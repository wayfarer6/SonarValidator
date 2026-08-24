#include <boost/asio.hpp>
#include <boost/asio/ssl.hpp>
#include <iostream>
#include <string>
#include <memory>
#include <mutex>

using tcp = boost::asio::ip::tcp;

class client {
    
}

class Connection {
    private:
        std::string hostname;
        int port_num;

    Connection() {std::make_shared<client>(io_context, context, "127.0.0.1", "4433");
        c->start("4433");}

};

class ConnectionPool {

    std::vector<std::shared_ptr<Connection>> mutex_v;
    std::vector<

};

int main() {
    // 예시 자료
    std::cout << "===========Dummy data=========\n";
    const std::string mac = "90:e8:68:4d:b2:b9";
    const std::string ipv4Addr = "192.168.0.1";
    const int subnet_mask = 24;
    const std::string ipv4_range = "192.168.0.0";
    const std::string default_gateway = "192.168.0.1";

    std::cout << "mac: "<< mac << '\n';
    std::cout << "mac: "<< ipv4Addr << '\n';
    std::cout << "subnet_mask: "<< subnet_mask << '\n';
    std::cout << "ipv4_range: "<< ipv4_range << '\n';
    std::cout << "default_gateway: "<< default_gateway << '\n';
    

    
    
    return 0;
}