#include "module/management_module/management_service.hpp"
#include "components/backend_communication/test_websocket_server.hpp"
#include <boost/beast/core.hpp>
#include <iostream>
#include <stdexcept>

int main() {
    TestWebSocketServer server([](auto& socket) {
        auto read = [&] {
            boost::beast::flat_buffer buffer; socket.read(buffer);
            return nlohmann::json::parse(boost::beast::buffers_to_string(buffer.data()));
        };
        auto hello = read();
        auto ack = envelope::Make(envelope::kAck, "node", "ROUTER", envelope::CorrelationId(hello), nlohmann::json::object());
        socket.write(boost::asio::buffer(ack.dump()));
        auto request = read();
        if (!envelope::IsType(request, envelope::kPolicyRequest)) throw std::runtime_error("policy request not received");
        auto reply = envelope::Make(envelope::kPolicyResponse, "node", "ROUTER", envelope::CorrelationId(request),
                {{"policy_id", "loopback"}, {"policies", nlohmann::json::array()}});
        socket.write(boost::asio::buffer(reply.dump()));
        auto applied = read();
        if (!envelope::IsType(applied, envelope::kAck) || !envelope::Payload(applied).value("applied", false))
            throw std::runtime_error("policy result not reported");
    });
    ManagementService service("127.0.0.1", server.Port(), "/api/v1/management");
    service.SetAgentId("node");
    auto policy = service.fetchPolicy(DeviceType::kRouter, "node");
    if (policy.value("policy_id", "") != "loopback") throw std::runtime_error("policy response lost");
    if (!service.ReportPolicyApplied(DeviceType::kRouter, "node", "loopback", true))
        throw std::runtime_error("policy result send failed");
    server.Wait();
    std::cout << "Management loopback integration PASS\n";
}
