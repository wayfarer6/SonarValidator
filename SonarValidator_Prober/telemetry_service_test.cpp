#include <cassert>
#include <iostream>
#include <string>

#include "telemetry_service.hpp"

int main()
{
    TelemetryService service("localhost", 3000, "/");

    if (!service.connect())
    {
        std::cerr << "connect failed\n";
        return 1;
    }

    const std::string heartbeat = "hello";
    if (!service.sendText(heartbeat))
    {
        std::cerr << "send failed\n";
        return 2;
    }

    const std::string response = service.receiveText();
    std::cout << "received: " << response << '\n';

    if (response != heartbeat)
    {
        std::cerr << "unexpected heartbeat response\n";
        return 3;
    }

    std::cout << "heartbeat test passed\n";
    return 0;
}