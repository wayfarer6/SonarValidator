#include <cassert>
#include <exception>
#include <filesystem>
#include <future>
#include <stdexcept>
#include <string>
#include <thread>
#include <memory>
#include "communication_service.hpp"

namespace fs = std::filesystem;

int main()
{
    CommunicationService service("localhost", 3000, "/api/test");
    service.initialize("localhost", 3000, "/api/test");
    http::request<http::string_body> req;
    
    // SET Header
    //req.set(http::field::host, "localhost");
    //req.set(http::field::content_type,"application/json");
    
    req.method(http::verb::get);
    req.target("/api/test");
    req.set(http::field::user_agent, "SonaValidator 1.0");
    req.set(http::field::accept, "application/json");
    

    boost::beast::error_code ec;

    // 데이터를 소켓 스트림으로 즉시 전송 (동기식)
    http::write(stream, req, ec);

    std::cout << req.body();
    std::cout << "Version: " << req.version() << '\n';

    std::cout << "Method: " << req.method_string() << '\n';
    std::cout << "Target: " << req.target() << '\n';
    std::cout << "Host: " << req[http::field::host] << '\n';
    std::cout << "Content-Type: " << req[http::field::content_type] << '\n';
    return 0;
}