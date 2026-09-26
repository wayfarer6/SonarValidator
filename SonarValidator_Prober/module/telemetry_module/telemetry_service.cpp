#include "module/telemetry_module/telemetry_service.hpp"

#include <chrono>
#include <thread>
#include <utility>

#include <nlohmann/json.hpp>

#include "components/backend_communication/connect_with_timeout.hpp"

TelemetryService::TelemetryService()
    : host_(""), port_(0), target_(""), ioc_(), resolver_(ioc_), stream_(ioc_), connected_(false)
{
}

TelemetryService::TelemetryService(std::string host, int port, std::string target)
    : host_(std::move(host)), port_(port), target_(std::move(target)), ioc_(), resolver_(ioc_), stream_(ioc_), connected_(false)
{
}

void TelemetryService::initialize(std::string host, int port, std::string target)
{
    host_ = std::move(host);
    port_ = port;
    target_ = std::move(target);
}

// 서버에 TCP 연결 후 WebSocket 핸드셰이크를 수행합니다. 각 서비스 마다 독자적으로 연결을 수립, 즉 한 agent 마다 일단 2개의 연결 세션을 갖고 있음.
bool TelemetryService::connect()
{
    try
    {
        if (host_.empty() || port_ <= 0)
        {
            return false;
        }

        auto const results = resolver_.resolve(host_, std::to_string(port_));

        // 제한 시간이 있는 연결입니다. 자세한 이유는
        // components/backend_communication/connect_with_timeout.hpp 참고
        // (표준 동기 connect 에는 타임아웃이 없고,
        //  beast::tcp_stream::expires_after() 는 비동기 전용이며,
        //  std::async + wait_for 는 future 소멸자에서 다시 블록된다)
        const boost::system::error_code ec =
            sonar::net::ConnectWithTimeout(stream_, results, kConnectTimeout);
        if (ec)
        {
            connected_ = false;
            return false;
        }

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

// 연결된 WebSocket으로 텍스트를 전송합니다.
bool TelemetryService::sendText(const std::string &message)
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

// WebSocket으로부터 텍스트를 수신합니다. (블로킹)
std::string TelemetryService::receiveText()
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

TelemetryService::~TelemetryService()
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

// 논블로킹으로 짧은 시간 동안 수신을 시도합니다.
// 소켓을 잠시 논블로킹으로 전환해 read_some으로 폴링하고,
// 완전한 JSON이 도착하면 반환한 뒤 다시 블로킹 모드로 복원합니다.  // 왜 논블록킹으로 전환하지 
bool TelemetryService::tryReceiveText(std::string& message, std::chrono::milliseconds timeout)
{
    if (!connected_)
    {
        return false;
    }

    boost::system::error_code ec;
    auto& socket = beast::get_lowest_layer(stream_).socket();
    socket.native_non_blocking(true, ec);
    // 지금 버퍼를 여기서 논블로킹으로 전환하는데
    if (ec)
    {
        return false;
    }

    beast::flat_buffer buffer;
    const auto deadline = std::chrono::steady_clock::now() + timeout;
    bool received = false;

    while (std::chrono::steady_clock::now() < deadline)
    {
        const std::size_t previous_size = buffer.size();
        stream_.read_some(buffer, 65536, ec);

        if (ec == websocket::error::closed)
        {
            connected_ = false;
            break; // 연결을 지속하기 위해 while문이 사용됨. 연결 재시도
        }

        if (!ec && buffer.size() > previous_size)
        { // 버퍼가 안커졌다는건 수신 받은 게 없다는 걸 의미 할테니.
            const std::string raw = beast::buffers_to_string(buffer.data()); // beast 내에 문자열로 역직렬화하는 함수가.
            try
            {
                // 완전한 JSON 메시지가 도착했는지 검증합니다.
                const nlohmann::json validation = nlohmann::json::parse(raw);
                (void)validation;
                message = raw;
                received = true;
                break;
            }
            catch (...)
            {
                // 부분 프레임이면 계속 읽습니다.
            }
        }

        if (ec == boost::asio::error::would_block || ec == boost::asio::error::try_again)
        {
            ec.clear();
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
            continue;
        }

        if (ec)
        {
            break;
        }
    }

    socket.native_non_blocking(false, ec);
    return received;
}


