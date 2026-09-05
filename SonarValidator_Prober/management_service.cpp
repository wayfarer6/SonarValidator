#include "management_service.hpp"
#include <nlohmann/json.hpp>
#include <utility>
#include <sstream>
#include <iostream>
#include <cstdio>
struct TerminalHandler
{
    void operator()(FILE* pipe) const
    {
        if(pipe == nullptr)
        {
            pclose(pipe);
        }
    }
};

using TerminalFile = std::unique_ptr<FILE, TerminalHandler>;

ManagementService::ManagementService()
    : host_(), port_(0), target_(), ioc_(), resolver_(ioc_), stream_(ioc_), connected_(false)
{
}

ManagementService::ManagementService(std::string host, int port, std::string target)
    : host_(std::move(host)), port_(port), target_(std::move(target)), ioc_(), resolver_(ioc_), stream_(ioc_), connected_(false)
{
}

ManagementService::~ManagementService()
{
    if (connected_)
    {
        try
        {
            stream_.close(websocket::close_code::normal);
        }
        catch (...)
        {
        }
    }
}

bool ManagementService::connect()
{
    try
    {
        if (host_.empty() || port_ <= 0)
        {
            return false;
        }

        auto const results = resolver_.resolve(host_, std::to_string(port_));
        beast::get_lowest_layer(stream_).connect(results);
        stream_.handshake(host_, target_);
        connected_ = true;
        return true;
    }
    catch (...)
    {
        connected_ = false;
        return false;
    }
}

Json ManagementService::sendText(const std::string &message)
{
    if (!connected_)
    {
        return {};
    }

    try
    {
        stream_.write(net::buffer(message));
        beast::flat_buffer buffer; // 동적 버퍼 boost library가 제공
        stream_.read(buffer);
        Json result = Json::parse(buffer.data());
        return result;
    }
    catch (...)
    {
        connected_ = false;
        return {};
    }
}

std::string ManagementService::receiveText()
{
    if (!connected_)
    {
        return {};
    }

    try
    {
        beast::flat_buffer buffer;
        std::string message;
        stream_.read(buffer);
        message.assign(static_cast<const char *>(buffer.data().data()), buffer.size());
        return message;
    }
    catch (...)
    {
        connected_ = false;
        return {};
    }
}

bool ManagementService::applyPolicy(const std::string& policy_name, const std::string& payload)
{
    (void)policy_name;
    if (!connected_ && !connect())
    {
        return false;
    }

    return sendText(payload);
}

Json ManagementService::fetchPolicy(const DeviceType device_type,const std::string& device_id)
{
    if(!connected_ && !connect())
    {
        return false;
    }
    Json payload_json = Json();
    payload_json["device_type"] = device_type;
    payload_json["device_id"] = device_id;

    const std::string payload = to_string(payload_json);

    try {
        sendText(payload);
        // 보낸 다음에 답을 받을려면 

    } catch(...) {
        connected_ = false;
        return {};
    }
}

bool ManagementService::processOpenVSwitchPolicy(const Json& policy_payload)
{
   


    

    return true;
}



bool ManagementService::processAristaSwitchPolicy(const Json& policy_payload)
{
     // OpenVSwitch Policy 처리 로직
    std::string command = "";
    
    // 예시
    //command = "sudo ovs-vsctl set bridge br0 other-config:policy=" + policy_payload["policy"] ;
    
    
    commandAristaSwitch(command);
}

Json ManagementService::commandAristaSwitch(const std::string& comm )
{
        // AristaVEos Policy 처리 로직
    std::string command = " FastCli -c";
    //command =+ "\"show version\"";
    command += comm;

    // 임시 버퍼 (128바이트씩 쪼개서 안전하게 읽음)
    std::array<char, 128> buffer;
    std::string result;


    TerminalFile terminal_file (popen(command.c_str(), "r"));

    if (!terminal_file) throw std::runtime_error("Can't open Terminal!");

    // 버퍼 단위로 읽어서 하나의 string에 병합

    while (fgets(buffer.data(), buffer.size(), terminal_file.get()) != nullptr) {
        result += buffer.data();
    }

    // string을 입력 스트림(stringstream)으로 변환
    std::stringstream stream(result);
    
    // 이제 일반적인 std::istream처럼 사용 가능
    std::string line;
    while (std::getline(stream, line)) {
        std::cout << line << std::endl;
    }

}

void ManagementService::commandAristaSwitch_no_return(const std::string& comm )
{
        // AristaVEos Policy 처리 로직
    std::string command = " FastCli -c";
    command =+ "\"show version\"";


    // 임시 버퍼 (128바이트씩 쪼개서 안전하게 읽음)
    std::array<char, 128> buffer;
    std::string result;


    TerminalFile terminal_file (popen(command.c_str(), "r"));

    if (!terminal_file) throw std::runtime_error("Can't open Terminal!");

}