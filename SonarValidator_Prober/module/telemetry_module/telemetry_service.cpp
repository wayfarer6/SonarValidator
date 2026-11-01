#include "utils/log.hpp"
#include "module/telemetry_module/telemetry_service.hpp"

#include <chrono>
#include <utility>

#include <nlohmann/json.hpp>

#include "components/backend_communication/connect_with_timeout.hpp"
#include "components/backend_communication/timed_websocket_operation.hpp"
#include <iostream>

TelemetryService::TelemetryService()
    : host_(""), port_(0), target_(""), ioc_(), resolver_(ioc_), stream_(std::make_unique<Stream>(ioc_)), connected_(false)
{
}

TelemetryService::TelemetryService(std::string host, int port, std::string target)
    : host_(std::move(host)), port_(port), target_(std::move(target)), ioc_(), resolver_(ioc_), stream_(std::make_unique<Stream>(ioc_)), connected_(false)
{
}

void TelemetryService::initialize(std::string host, int port, std::string target)
{
    host_ = std::move(host);
    port_ = port;
    target_ = std::move(target);
}

bool TelemetryService::connect()
{
    try
    {
        if (host_.empty() || port_ <= 0)
        {
            return false;
        }

        reader_.Reset(ioc_, *stream_);
        stream_ = std::make_unique<Stream>(ioc_);
        auto const results = resolver_.resolve(host_, std::to_string(port_));

        const boost::system::error_code ec =
            sonar::net::ConnectWithTimeout(*stream_, results, kConnectTimeout, stop_token_);
        if (ec)
        {
            throw boost::system::system_error(ec);
        }

        sonar::net::HandshakeWithTimeout(ioc_, *stream_, host_, target_, kHandshakeTimeout, stop_token_);
        connected_ = true;
        return true;
    }
    catch (const std::exception& error)
    {
        SV_LOG_WARN << "[TELEMETRY] connection failed: " << host_ << ':' << port_
                  << target_ << " (" << error.what() << ")\n";
        reader_.Reset(ioc_, *stream_);
        connected_ = false;
        return false;
    }
}

// 연결된 WebSocket으로 텍스트를 전송합니다.
bool TelemetryService::sendText(const std::string &message)
{
    if (!connected_)
    {
        return false;
    }

    try
    {
        sonar::net::WriteWithTimeout(ioc_, *stream_, message, kConnectTimeout, stop_token_);
        return true;
    }
    catch (...)
    {
        connected_ = false;
        return false;
    }
}

std::string TelemetryService::receiveText()
{
    std::string message;
    while (connected_)
    {
        if (tryReceiveText(message, std::chrono::milliseconds(200))) return message;
    }
    return {};
}

TelemetryService::~TelemetryService()
{
    reader_.Reset(ioc_, *stream_);
}

// 필요하면 먼저 연결하고 요청을 전송합니다.
bool TelemetryService::sendRequest(const std::string &request, const std::string &target)
{
    (void)target;
    if (!connected_ && !connect())
    {
        return false;
    }

    return sendText(request);
}

// A polling timeout leaves the asynchronous read and partial frame intact.
bool TelemetryService::tryReceiveText(std::string& message, std::chrono::milliseconds timeout)
{
    if (!connected_) return false;
    boost::system::error_code ec;
    const bool received = reader_.ReadFor(ioc_, *stream_, message, timeout, ec);
    if (ec) connected_ = false;
    return received;
}
