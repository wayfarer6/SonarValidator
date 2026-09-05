#ifndef SONAR_VALIDATOR_PROBER_ROUTING_TABLE_HPP_
#define SONAR_VALIDATOR_PROBER_ROUTING_TABLE_HPP_

#include <string>
#include <vector>

#include <nlohmann/json.hpp>

using Json = nlohmann::json;

struct RouteEntry {
  std::string prefix;
  std::string next_hop;
  std::string protocol;
  std::string metric;
  std::string interface_name;
};


class RoutingTable {
 public:
  RoutingTable();

  const std::string& GetConnectionId() const;
  void SetConnectionId(std::string connection_id);

  const std::string& GetConnectedIp() const;
  void SetConnectedIp(std::string connected_ip);

  const std::string& GetConnectedNodeId() const;
  void SetConnectedNodeId(std::string connected_node_id);

  const std::string& GetRoutingProtocol() const;
  void SetRoutingProtocolValue(std::string routing_protocol);

  void AddRoute(RouteEntry route);
  void AddRoute(std::string prefix,
                std::string next_hop,
                std::string protocol,
                std::string metric,
                std::string interface_name);
  const std::vector<RouteEntry>& GetRoutes() const;
  std::size_t GetRouteCount() const;
  void ClearRoutes();

  const std::string& ParsingRoutingTabe() const;
  void GetRoutingTabe(std::string router_id);
  void ParseFrrTable(const std::string& raw_output);
  void ParseCiscoTable(const std::string& raw_output);

  bool RemoveConnection();
  bool AddConnection();
  Json ToJson() const;
  bool SetRoutingProtocol();

 private:
  std::string connection_id_;
  std::string connected_ip_;
  std::string connected_node_id_;
  std::string routing_protocol_;
  std::string parsed_routing_table_;
  std::vector<RouteEntry> routes_;
};

class RoutingTableView {
 public:
  virtual ~RoutingTableView() = default;
  virtual std::string Render(const RoutingTable& table) const = 0;
};

class FrrRoutingTableView : public RoutingTableView {
 public:
  std::string Render(const RoutingTable& table) const override;
};

class CiscoRoutingTableView : public RoutingTableView {
 public:
  std::string Render(const RoutingTable& table) const override;
};


#endif  // SONAR_VALIDATOR_PROBER_ROUTING_TABLE_HPP_ (헤더가 여러번 include 되는 불상사 방지)
