#include "management_service.hpp"

#include <utility>

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

bool ManagementService::sendText(const std::string &message)
{
    if (!connected_)
    {
        return false;
    }

    try
    {
        stream_.write(net::buffer(message));
        return true;
    }
    catch (...)
    {
        connected_ = false;
        return false;
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

bool ManagementService::fetchPolicy(const std::string& policy_name, std::string& payload)
{
    (void)policy_name;
    if (!connected_ && !connect())
    {
        payload.clear();
        return false;
    }

    payload = receiveText();
    if (payload.empty())
    {
        return false;
    }

    return true;
}
