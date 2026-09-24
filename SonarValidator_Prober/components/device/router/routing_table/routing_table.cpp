#include "components/device/router/routing_table/routing_table.hpp"

#include "components/parser/cli_output_parser.hpp"

#include <sstream>
#include <utility>

namespace {

std::string JsonText(const nlohmann::json& value)
{
  if (value.is_string()) {
    return value.get<std::string>();
  }
  if (value.is_null()) {
    return "";
  }
  return value.dump();
}

// ANTLR 파서가 돌려준 라우트 JSON 배열을 RouteEntry 목록으로 옮깁니다.
void AppendRoutes(const nlohmann::json& parsed, std::vector<RouteEntry>& routes)
{
  if (!parsed.contains("routes") || !parsed["routes"].is_array()) {
    return;
  }

  for (const auto& route_json : parsed["routes"]) {
    RouteEntry entry;
    entry.prefix = JsonText(route_json.value("prefix", nlohmann::json("")));
    entry.next_hop = route_json.contains("next_hop")
                         ? JsonText(route_json["next_hop"])
                         : std::string{"directly connected"};
    entry.protocol = JsonText(route_json.value("protocol", nlohmann::json("unknown")));
    entry.metric = route_json.contains("metric")
                       ? JsonText(route_json["metric"])
                       : std::string{"0"};
    entry.interface_name = JsonText(route_json.value("interface_name", nlohmann::json("")));
    routes.push_back(std::move(entry));
  }
}

}  // namespace

RoutingTable::RoutingTable() = default;

const std::string& RoutingTable::GetConnectionId() const { return connection_id_; }

void RoutingTable::SetConnectionId(std::string connection_id)
{
  connection_id_ = std::move(connection_id);
}

const std::string& RoutingTable::GetConnectedIp() const { return connected_ip_; }

void RoutingTable::SetConnectedIp(std::string connected_ip)
{
  connected_ip_ = std::move(connected_ip);
}

const std::string& RoutingTable::GetConnectedNodeId() const
{
  return connected_node_id_;
}

void RoutingTable::SetConnectedNodeId(std::string connected_node_id)
{
  connected_node_id_ = std::move(connected_node_id);
}

const std::string& RoutingTable::GetRoutingProtocol() const
{
  return routing_protocol_;
}

void RoutingTable::SetRoutingProtocolValue(std::string routing_protocol)
{
  routing_protocol_ = std::move(routing_protocol);
}

void RoutingTable::AddRoute(RouteEntry route)
{
  routes_.push_back(std::move(route));
}

void RoutingTable::AddRoute(std::string prefix,
                           std::string next_hop,
                           std::string protocol,
                           std::string metric,
                           std::string interface_name)
{
  routes_.push_back(RouteEntry{std::move(prefix),
                              std::move(next_hop),
                              std::move(protocol),
                              std::move(metric),
                              std::move(interface_name)});
}

const std::vector<RouteEntry>& RoutingTable::GetRoutes() const { return routes_; }

std::size_t RoutingTable::GetRouteCount() const { return routes_.size(); }

void RoutingTable::ClearRoutes() { routes_.clear(); }

const std::string& RoutingTable::ParsingRoutingTabe() const
{
  return parsed_routing_table_;
}

void RoutingTable::GetRoutingTabe(std::string router_id)
{
  std::ostringstream oss;
  oss << "Router ID: " << router_id << '\n';
  if (!connection_id_.empty()) {
    oss << "Connection ID: " << connection_id_ << '\n';
  }
  if (!connected_ip_.empty()) {
    oss << "Connected IP: " << connected_ip_ << '\n';
  }
  if (!connected_node_id_.empty()) {
    oss << "Connected Node ID: " << connected_node_id_ << '\n';
  }
  if (!routing_protocol_.empty()) {
    oss << "Routing Protocol: " << routing_protocol_ << '\n';
  }

  for (const auto& route : routes_) {
    oss << "Prefix: " << route.prefix << " -> " << route.next_hop
        << " (" << route.protocol << ", metric=" << route.metric
        << ", iface=" << route.interface_name << ")\n";
  }

  parsed_routing_table_ = oss.str();
}

void RoutingTable::ParseFrrTable(const std::string& raw_output)
{
  routes_.clear();
  routing_protocol_.clear();
  parsed_routing_table_ = raw_output;

  // 수작업 라인 스캔 대신 공용 ANTLR 문법(FrrRouter.g4)에 위임한다.
  AppendRoutes(cli_parser::ParseRouteStatus(raw_output, cli_parser::Vendor::kFrr), routes_);

  // FRR CLI 코드 머리말 기준으로 대표 프로토콜을 고른다(ospf 우선).
  std::string protocol;
  for (const auto& route : routes_) {
    if (protocol.empty()) {
      if (route.protocol != "unknown") {
        protocol = route.protocol;
      }
      continue;
    }
    if (route.protocol == "ospf") {
      protocol = route.protocol;
    }
  }

  routing_protocol_ = protocol.empty() ? "static" : protocol;
}

void RoutingTable::ParseCiscoTable(const std::string& raw_output)
{
  routes_.clear();
  routing_protocol_.clear();
  parsed_routing_table_ = raw_output;

  // 수작업 라인 스캔 대신 공용 ANTLR 문법(FrrRouter.g4)에 위임한다.
  // Cisco `show ip route` 도 라우트 코드(C/O/S...)를 붙여 출력하므로
  // FRR 과 동일한 문법으로 파싱한다.
  AppendRoutes(cli_parser::ParseRouteStatus(raw_output, cli_parser::Vendor::kCisco), routes_);

  std::string protocol;
  for (const auto& route : routes_) {
    if (protocol.empty() || route.protocol == "ospf" ||
        (protocol == "connected" && route.protocol != "connected")) {
      protocol = route.protocol;
    }
  }

  routing_protocol_ = protocol.empty() ? "static" : protocol;
}

bool RoutingTable::RemoveConnection()
{
  if (connection_id_.empty() && connected_ip_.empty() && connected_node_id_.empty()) {
    return false;
  }

  connection_id_.clear();
  connected_ip_.clear();
  connected_node_id_.clear();
  routing_protocol_.clear();
  parsed_routing_table_.clear();
  routes_.clear();
  return true;
}

bool RoutingTable::AddConnection()
{
  return !connection_id_.empty() || !connected_ip_.empty() || !connected_node_id_.empty();
}

Json RoutingTable::ToJson() const
{
  Json json;
  json["connection_id"] = connection_id_;
  json["connected_ip"] = connected_ip_;
  json["connected_node_id"] = connected_node_id_;
  json["routing_protocol"] = routing_protocol_;

  json["routes"] = Json::array();
  for (const auto& route : routes_) {
    json["routes"].push_back({
        {"prefix", route.prefix},
        {"next_hop", route.next_hop},
        {"protocol", route.protocol},
        {"metric", route.metric},
        {"interface_name", route.interface_name},
    });
  }

  return json;
}

bool RoutingTable::SetRoutingProtocol()
{
  if (routing_protocol_.empty()) {
    routing_protocol_ = "static";
  }
  return !routing_protocol_.empty();
}

std::string FrrRoutingTableView::Render(const RoutingTable& table) const
{
  std::ostringstream oss;
  oss << "Codes: K - kernel, C - connected, S - static, R - RIP, O - OSPF\n";
  oss << "Gateway of last resort is not set\n";

  for (const auto& route : table.GetRoutes()) {
    oss << route.prefix << " via " << route.next_hop << " dev " << route.interface_name
        << " proto " << route.protocol << " metric " << route.metric << '\n';
  }

  if (table.GetRoutes().empty()) {
    oss << table.GetConnectedIp() << " via " << table.GetConnectedNodeId() << " dev "
        << table.GetConnectionId() << '\n';
  }

  oss << "Protocol: " << table.GetRoutingProtocol() << '\n';
  return oss.str();
}

std::string CiscoRoutingTableView::Render(const RoutingTable& table) const
{
  std::ostringstream oss;
  oss << "show ip route\n";
  oss << "Codes: C - connected, S - static, R - RIP, O - OSPF, B - BGP\n";

  for (const auto& route : table.GetRoutes()) {
    oss << route.prefix << " [" << route.metric << "] via " << route.next_hop
        << ", " << route.interface_name << ", " << route.protocol << '\n';
  }

  if (table.GetRoutes().empty()) {
    oss << table.GetConnectedIp() << " is directly connected, " << table.GetConnectionId()
        << '\n';
  }

  oss << table.GetConnectedNodeId() << " " << table.GetRoutingProtocol() << '\n';
  return oss.str();
}

