#ifndef SONAR_VALIDATOR_PROBER_CONNECT_WITH_TIMEOUT_HPP_
#define SONAR_VALIDATOR_PROBER_CONNECT_WITH_TIMEOUT_HPP_

#include <chrono>

#include <boost/asio.hpp>
#include <boost/beast/core.hpp>
#include <boost/beast/websocket.hpp>

namespace sonar::net
{

namespace beast = boost::beast;
namespace net = boost::asio;
namespace websocket = beast::websocket;
using tcp = boost::asio::ip::tcp;

/**
 * 제한 시간이 있는 동기 TCP 연결입니다.
 *
 * <h2>왜 직접 구현하는가</h2>
 *
 * <p>표준 동기 {@code socket.connect()} 에는 타임아웃이 없습니다. 응답이
 * 없는 주소로 연결하면 OS 의 TCP 재전송 한계(리눅스 기본 약 2분)까지
 * 블록됩니다. 그동안 워커 스레드가 멈춰 있어 <b>SIGTERM 을 받아도
 * 프로세스가 종료되지 않습니다</b> — main 은 join 에서 대기하고,
 * 소켓은 SYN-SENT 로 남습니다.
 *
 * <h2>시도했다가 실패한 방법들</h2>
 *
 * <p>1) {@code beast::tcp_stream::expires_after()} — 문서상
 * <b>"asynchronous read/write/connect functions"</b> 에만 적용됩니다.
 * 동기 connect/handshake 에는 <b>아무 효과가 없습니다.</b>
 * (실측: 적용 후에도 회귀 테스트가 hang)
 *
 * <p>2) {@code std::async} + {@code wait_for} — 제한 시간이 지나 반환해도
 * <b>future 의 소멸자가 스레드 완료를 기다려</b> 결국 블록됩니다.
 * (실측: 같은 hang)
 *
 * <h2>그래서 non-blocking + poll</h2>
 *
 * <p>소켓을 non-blocking 으로 열고 connect 를 걸면 즉시 EINPROGRESS 로
 * 돌아옵니다. 그 뒤 {@code poll()} 로 제한 시간만 기다리고,
 * {@code SO_ERROR} 로 실제 결과를 확인한 뒤 blocking 으로 되돌립니다.
 * 그리고 동기 read/write 에도 제한을 걸기 위해
 * {@code SO_RCVTIMEO}/{@code SO_SNDTIMEO} 를 설정합니다
 * (핸드셰이크가 응답 없이 매달리는 것을 막습니다).
 *
 * <p>DNS 결과가 여러 개여도 <b>첫 번째만</b> 씁니다. 랩 장비는 단일
 * 주소라 round-robin 이 필요 없고, 실패하면 다음 정책 요청에서 재시도합니다.
 *
 * @param stream  대상 스트림 (소켓을 직접 열고 설정합니다)
 * @param results DNS 해석 결과
 * @param timeout connect 와 이후 동기 I/O 의 제한 시간
 * @return 성공하면 빈 error_code, 실패하면 사유
 */
inline boost::system::error_code ConnectWithTimeout(
    websocket::stream<beast::tcp_stream>& stream,
    const tcp::resolver::results_type& results,
    std::chrono::seconds timeout)
{
    auto& socket = beast::get_lowest_layer(stream).socket();
    boost::system::error_code ec;

    if (results.empty())
    {
        return net::error::make_error_code(net::error::host_not_found);
    }

    const auto endpoint = results.begin()->endpoint();

    socket.open(endpoint.protocol(), ec);
    if (ec)
    {
        return ec;
    }

    socket.non_blocking(true, ec);
    if (ec)
    {
        return ec;
    }

    socket.connect(endpoint, ec);

    // 정상 경로는 EINPROGRESS(would_block) 로 돌아옵니다.
    if (ec && ec != net::error::in_progress && ec != net::error::would_block)
    {
        return ec;  // 즉시 실패 (연결 거부 등)
    }

    if (ec)  // 진행 중 → poll 로 제한 시간만 대기
    {
        ::pollfd pfd{};
        pfd.fd = socket.native_handle();
        pfd.events = POLLOUT;

        const int timeout_ms = static_cast<int>(
            std::chrono::duration_cast<std::chrono::milliseconds>(timeout).count());
        const int ready = ::poll(&pfd, 1, timeout_ms);

        if (ready == 0)
        {
            return net::error::make_error_code(net::error::timed_out);
        }
        if (ready < 0)
        {
            return boost::system::error_code(errno, boost::system::system_category());
        }

        // poll 이 준비돼도 연결 실패일 수 있으므로 SO_ERROR 를 확인합니다.
        int so_error = 0;
        ::socklen_t len = sizeof(so_error);
        if (::getsockopt(socket.native_handle(), SOL_SOCKET, SO_ERROR,
                         &so_error, &len) != 0)
        {
            return boost::system::error_code(errno, boost::system::system_category());
        }
        if (so_error != 0)
        {
            return boost::system::error_code(so_error, boost::system::system_category());
        }
    }

    // 핸드셰이크를 위해 blocking 으로 되돌리고, 동기 I/O 에 제한 시간을 겁니다.
    socket.non_blocking(false, ec);
    if (ec)
    {
        return ec;
    }

    ::timeval tv{};
    tv.tv_sec = static_cast<decltype(tv.tv_sec)>(timeout.count());
    tv.tv_usec = 0;
    (void)::setsockopt(socket.native_handle(), SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    (void)::setsockopt(socket.native_handle(), SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));

    return {};
}

}  // namespace sonar::net

#endif  // SONAR_VALIDATOR_PROBER_CONNECT_WITH_TIMEOUT_HPP_