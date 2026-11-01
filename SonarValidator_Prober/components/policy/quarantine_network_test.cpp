#include "components/policy/quarantine_handler.hpp"
#include "module/management_module/management_service.hpp"

#include <arpa/inet.h>
#include <poll.h>
#include <sched.h>
#include <signal.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#include <array>
#include <filesystem>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {
namespace fs = std::filesystem;
constexpr int kPort = 25432;

void Require(bool ok, const std::string& message) {
    if (!ok) throw std::runtime_error(message);
}

bool IsPrivateNetworkNamespace() {
    struct stat current{}, initial{};
    return stat("/proc/self/ns/net", &current) == 0 && stat("/proc/1/ns/net", &initial) == 0
        && current.st_ino != initial.st_ino;
}

class Peer {
public:
    Peer() {
        int ready[2], configured[2];
        Require(pipe(ready) == 0 && pipe(configured) == 0, "pipe creation failed");
        pid_ = fork();
        Require(pid_ >= 0, "peer fork failed");
        if (pid_ == 0) {
            close(ready[0]); close(configured[1]);
            if (unshare(CLONE_NEWNET) != 0) _exit(10);
            char signal = 'R';
            if (write(ready[1], &signal, 1) != 1 || read(configured[0], &signal, 1) != 1) _exit(11);
            ManagementService commands;
            if (!commands.RunCommand("/usr/sbin/ip link set lo up; /usr/sbin/ip link set sv-peer up; "
                    "/usr/sbin/ip addr add 10.254.10.2/24 dev sv-peer; "
                    "/usr/sbin/ip addr add 192.0.2.2/24 dev sv-peer")) _exit(12);
            int listener = socket(AF_INET, SOCK_STREAM, 0);
            int reuse = 1;
            setsockopt(listener, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse));
            sockaddr_in address{AF_INET, htons(kPort), {htonl(INADDR_ANY)}, {}};
            if (bind(listener, reinterpret_cast<sockaddr*>(&address), sizeof(address)) || listen(listener, 16)) _exit(13);
            if (write(ready[1], &signal, 1) != 1) _exit(14);
            std::array<pollfd, 32> descriptors{};
            for (auto& descriptor : descriptors) descriptor.fd = -1;
            descriptors[0] = {listener, POLLIN, 0};
            for (;;) {
                if (poll(descriptors.data(), descriptors.size(), -1) < 0) _exit(15);
                if (descriptors[0].revents & POLLIN) {
                    int client = accept(listener, nullptr, nullptr);
                    for (std::size_t i = 1; i < descriptors.size(); ++i) {
                        if (descriptors[i].fd >= 0) continue;
                        descriptors[i] = {client, POLLIN, 0}; client = -1; break;
                    }
                    if (client >= 0) close(client);
                }
                for (std::size_t i = 1; i < descriptors.size(); ++i) {
                    if (!descriptors[i].revents) continue;
                    char buffer[128];
                    const auto size = recv(descriptors[i].fd, buffer, sizeof(buffer), 0);
                    if (size <= 0) { close(descriptors[i].fd); descriptors[i].fd = -1; }
                    else send(descriptors[i].fd, buffer, size, MSG_NOSIGNAL);
                }
            }
        }
        close(ready[1]); close(configured[0]);
        char signal;
        Require(read(ready[0], &signal, 1) == 1, "peer namespace did not start");
        ManagementService commands;
        Require(commands.RunCommand("/usr/sbin/ip link add sv-host type veth peer name sv-peer && "
            "/usr/sbin/ip link set sv-peer netns " + std::to_string(pid_) + " && "
            "/usr/sbin/ip link set sv-host up && "
            "/usr/sbin/ip addr add 10.254.10.1/24 dev sv-host && "
            "/usr/sbin/ip addr add 192.0.2.1/24 dev sv-host"), "veth setup failed");
        Require(write(configured[1], &signal, 1) == 1 && read(ready[0], &signal, 1) == 1, "peer listener did not start");
        close(ready[0]); close(configured[1]);
    }
    ~Peer() {
        if (pid_ > 0) { kill(pid_, SIGTERM); waitpid(pid_, nullptr, 0); }
    }
    Peer(const Peer&) = delete;
    Peer& operator=(const Peer&) = delete;
private:
    pid_t pid_ = -1;
};

class Connection {
public:
    explicit Connection(const char* host) {
        fd_ = socket(AF_INET, SOCK_STREAM, 0);
        timeval timeout{1, 0};
        setsockopt(fd_, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        setsockopt(fd_, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
        sockaddr_in address{AF_INET, htons(kPort), {}, {}};
        inet_pton(AF_INET, host, &address.sin_addr);
        connected_ = connect(fd_, reinterpret_cast<sockaddr*>(&address), sizeof(address)) == 0;
    }
    ~Connection() { close(fd_); }
    bool Echo() {
        if (!connected_) return false;
        char value = 'Q', reply = 0;
        return send(fd_, &value, 1, MSG_NOSIGNAL) == 1 && recv(fd_, &reply, 1, 0) == 1 && reply == value;
    }
    bool PendingReplyArrives() {
        char reply = 0;
        for (int attempt = 0; attempt < 12; ++attempt)
            if (recv(fd_, &reply, 1, 0) == 1) return reply == 'Q';
        return false;
    }
private:
    int fd_;
    bool connected_ = false;
};

void Exercise(const std::string& backend) {
    ManagementService commands;
    const auto directory = fs::temp_directory_path() / ("sonar-quarantine-net-" + std::to_string(getpid()));
    fs::create_directories(directory);
    fs::create_symlink("/usr/sbin/ip", directory / "ip");
    for (const auto& binary : backend == "nft" ? std::vector<std::string>{"nft"}
                                              : std::vector<std::string>{"iptables", "iptables-restore"})
        fs::create_symlink("/usr/sbin/" + binary, directory / binary);
    const std::string previousPath = getenv("PATH");
    setenv("PATH", directory.c_str(), 1);
    ProberConfig config("network-test", "network-test", "Linux", "test", DeviceType::kVirtualMachine,
                        "Ubuntu", 0, "192.0.2.2", 3000);
    config.SetManagementPrefixes("192.0.2.0/24");
    const auto routeBefore = commands.RunCommandOutput("ip -j route show");
    const auto rulesBefore = commands.RunCommandOutput("/usr/sbin/iptables -S; /usr/sbin/iptables -t nat -S");
    auto plan = nlohmann::json{{"revision", "network-regression"}, {"targets", nlohmann::json::array()},
        {"node", {{"action", "quarantine"}, {"request_id", "network-regression"}}}};
    Connection establishedData("10.254.10.2"), management("192.0.2.2");
    Require(establishedData.Echo() && management.Echo(), "baseline TCP failed");
    Require(quarantine::ReconcilePlan(config, commands, plan), backend + " isolation apply failed");
    Require(quarantine::ReconcilePlan(config, commands, plan), backend + " repeated apply failed");
    Require(!establishedData.Echo(), backend + " established data connection bypassed quarantine");
    Require(!Connection("10.254.10.2").Echo(), backend + " new data connection bypassed quarantine");
    Require(management.Echo(), backend + " management connection was interrupted");
    Require(commands.RunCommandOutput("ip -j route show") == routeBefore, backend + " changed routes");
    plan["node"]["action"] = "release";
    Require(quarantine::ReconcilePlan(config, commands, plan), backend + " release failed");
    Require(quarantine::ReconcilePlan(config, commands, plan), backend + " repeated release failed");
    Require(establishedData.PendingReplyArrives() && establishedData.Echo(),
            backend + " existing TCP connection did not recover after release");
    Require(Connection("10.254.10.2").Echo() && management.Echo(), backend + " recovery failed");
    Require(commands.RunCommandOutput("ip -j route show") == routeBefore, backend + " failed route restoration");
    Require(commands.RunCommandOutput("/usr/sbin/iptables -S; /usr/sbin/iptables -t nat -S") == rulesBefore,
            backend + " changed original firewall/NAT rules");
    setenv("PATH", previousPath.c_str(), 1);
    fs::remove_all(directory);
    std::cout << backend << ": existing/new TCP blocked and recovered, management preserved, exact rules/routes restored\n";
}
}

int main() {
    if (geteuid() != 0 || !getenv("SONAR_TEST_NETWORK_NAMESPACE") || !IsPrivateNetworkNamespace()) {
        std::cout << "Run as root: unshare -n env SONAR_TEST_NETWORK_NAMESPACE=1 <this-test>\n";
        return 77;
    }
    try {
        ManagementService commands;
        Require(commands.RunCommand("/usr/sbin/ip link set lo up; "
            "/usr/sbin/iptables -A OUTPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT; "
            "/usr/sbin/iptables -t nat -A POSTROUTING -s 10.254.10.0/24 -o unused0 -j MASQUERADE"),
            "baseline firewall setup failed");
        Peer peer;
        Exercise("nft");
        Exercise("iptables");
        return 0;
    } catch (const std::exception& error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
