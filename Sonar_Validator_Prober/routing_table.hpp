#ifndef SONAR_VALIDATOR_PROBER_ROUTING_TABLE_HPP_
#define SONAR_VALIDATOR_PROBER_ROUTING_TABLE_HPP_

#include <string>

#include <nlohmann/json.hpp>

using Json = nlohmann::json;

class RoutingTable {
 public:
  RoutingTable();

  bool RemoveConnection();
  bool AddConnection();
  Json ToJson() const;
  bool SetRoutingProtocol();

 private:
  std::string connection_id_;
  std::string connected_ip_;
  std::string connected_node_id_;
};

#endif  // SONAR_VALIDATOR_PROBER_ROUTING_TABLE_HPP_ (헤더가 여러번 include 되는 불상사 방지)
