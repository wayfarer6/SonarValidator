#ifndef SONAR_VALIDATOR_PROBER_ROUTER_ROUTER_HPP_
#define SONAR_VALIDATOR_PROBER_ROUTER_ROUTER_HPP_

#include <string>

#include "components/device/router/routing_table/routing_table.hpp"

class Router
{
public:
    explicit Router(std::string name = {});

    const std::string& getName() const;
    void setName(std::string name);

    RoutingTable& getRoutingTable();
    const RoutingTable& getRoutingTable() const;

private:
    std::string name_;
    RoutingTable routing_table_;
};

#endif // SONAR_VALIDATOR_PROBER_ROUTER_ROUTER_HPP_
