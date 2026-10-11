#include "components/device/router/router.hpp"

#include <utility>

Router::Router(std::string name) : name_(std::move(name)) {}

const std::string& Router::getName() const { return name_; }

void Router::setName(std::string name) { name_ = std::move(name); }

RoutingTable& Router::getRoutingTable() { return routing_table_; }

const RoutingTable& Router::getRoutingTable() const { return routing_table_; }
