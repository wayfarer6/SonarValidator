#include "routing_table.hpp"

#include <algorithm>
#include <cctype>
#include <sstream>
#include <utility>

namespace {

std::string Trim(const std::string& input)
{
  std::size_t start = 0;
  while (start < input.size() && std::isspace(static_cast<unsigned char>(input[start])) != 0) {
    ++start;
  }

  std::size_t end = input.size();
  while (end > start && std::isspace(static_cast<unsigned char>(input[end - 1])) != 0) {
    --end;
  }

  return input.substr(start, end - start);
}

std::string ToLower(std::string value)
{
  std::transform(value.begin(), value.end(), value.begin(), [](unsigned char ch) {
    return static_cast<char>(std::tolower(ch));
  });
  return value;
}

std::string NormalizeProtocolCode(const std::string& code)
{
  if (code.empty()) {
    return "unknown";
  }

  switch (code[0]) {
    case 'K':
      return "kernel";
    case 'C':
      return "connected";
    case 'S':
      return "static";
    case 'R':
      return "rip";
    case 'O':
      return "ospf";
    case 'I':
      return "isis";
    case 'B':
      return "bgp";
    case 'E':
      return "eigrp";
    case 'N':
      return "nhrp";
    case 'A':
      return "babel";
    default:
      return ToLower(code);
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
  parsed_routing_table_ = raw_output;
  routing_protocol_.clear();

  std::istringstream stream(raw_output);
  std::string line;
  while (std::getline(stream, line)) {
    line = Trim(line);
    if (line.empty() || line.rfind("Codes:", 0) == 0 ||
        line.rfind("Gateway of last resort", 0) == 0) {
      continue;
    }

    const std::size_t first_space = line.find(' ');
    if (first_space == std::string::npos) {
      continue;
    }

    const std::string route_code = line.substr(0, first_space);
    std::string remainder = Trim(line.substr(first_space + 1));
    if (remainder.empty() ||
        (remainder.find('/') == std::string::npos &&
         remainder.find(" via ") == std::string::npos &&
         remainder.find(" is directly connected") == std::string::npos)) {
      continue;
    }

    std::string prefix = remainder;
    std::string next_hop = "directly connected";
    std::string metric = "0";
    std::string interface_name;
    const std::string protocol = NormalizeProtocolCode(
        route_code.empty() ? "" : std::string(1, route_code[0]));

    const std::size_t first_token_end = remainder.find(' ');
    if (first_token_end != std::string::npos) {
      prefix = remainder.substr(0, first_token_end);
      remainder = Trim(remainder.substr(first_token_end));
    } else {
      remainder.clear();
    }

    if (prefix.find('/') == std::string::npos &&
        prefix.find('.') == std::string::npos) {
      continue;
    }

    const std::size_t metric_start = remainder.find('[');
    const std::size_t metric_end = remainder.find(']', metric_start);
    if (metric_start != std::string::npos && metric_end != std::string::npos &&
        metric_end > metric_start) {
      metric = remainder.substr(metric_start + 1, metric_end - metric_start - 1);
    }

    const std::size_t direct_marker = remainder.find("is directly connected");
    if (direct_marker != std::string::npos) {
      std::string tail = remainder.substr(direct_marker + std::string("is directly connected").size());
      if (!tail.empty() && tail[0] == ',') {
        tail = Trim(tail.substr(1));
      }
      const std::size_t tail_comma = tail.find(',');
      if (tail_comma != std::string::npos) {
        interface_name = Trim(tail.substr(0, tail_comma));
      } else {
        interface_name = Trim(tail);
      }
      if (routing_protocol_.empty() && protocol != "unknown") {
        routing_protocol_ = protocol;
      }
      routes_.push_back(RouteEntry{prefix, next_hop, protocol, metric, interface_name});
      continue;
    }

    const std::size_t via_pos = remainder.find(" via ");
    if (via_pos != std::string::npos) {
      const std::size_t next_hop_start = via_pos + 5;
      const std::size_t next_hop_end = remainder.find(", ", next_hop_start);
      if (next_hop_end != std::string::npos) {
        next_hop = remainder.substr(next_hop_start, next_hop_end - next_hop_start);
      } else {
        const std::size_t end_of_hop = remainder.find(' ', next_hop_start);
        if (end_of_hop != std::string::npos) {
          next_hop = remainder.substr(next_hop_start, end_of_hop - next_hop_start);
        } else {
          next_hop = remainder.substr(next_hop_start);
        }
      }
      next_hop = Trim(next_hop);

      const std::size_t iface_start = remainder.rfind(", ");
      if (iface_start != std::string::npos) {
        std::string tail = remainder.substr(iface_start + 2);
        const std::size_t tail_comma = tail.find(',');
        if (tail_comma != std::string::npos) {
          interface_name = Trim(tail.substr(0, tail_comma));
        } else {
          interface_name = Trim(tail);
        }
      }
    }

    if (protocol == "ospf" || routing_protocol_.empty() ||
        (routing_protocol_ == "kernel" && protocol != "unknown")) {
      if (protocol == "ospf" || routing_protocol_.empty()) {
        routing_protocol_ = protocol;
      }
    }

    routes_.push_back(RouteEntry{prefix, next_hop, protocol, metric, interface_name});
  }

  if (routing_protocol_.empty()) {
    routing_protocol_ = "static";
  }
}

void RoutingTable::ParseCiscoTable(const std::string& raw_output)
{
  routes_.clear();
  parsed_routing_table_ = raw_output;
  routing_protocol_.clear();

  std::istringstream stream(raw_output);
  std::string line;

  while (std::getline(stream, line)) {
    line = Trim(line);
    if (line.empty() || line.rfind("show ip route", 0) == 0 ||
        line.rfind("Codes:", 0) == 0 || line == "") {
      continue;
    }

    const std::size_t first_space = line.find(' ');
    if (first_space == std::string::npos) {
      continue;
    }

    std::string route_code = Trim(line.substr(0, first_space));
    std::string remainder = Trim(line.substr(first_space + 1));
    if (remainder.empty()) {
      continue;
    }

    std::string prefix = remainder;
    std::string protocol = NormalizeProtocolCode(route_code);
    std::string next_hop = "directly connected";
    std::string metric = "0";
    std::string interface_name;

    const std::size_t direct_pos = remainder.find("is directly connected");
    if (direct_pos != std::string::npos) {
      prefix = Trim(remainder.substr(0, direct_pos));
      std::string tail = Trim(remainder.substr(direct_pos + std::string("is directly connected").size()));
      if (!tail.empty() && tail[0] == ',') {
        tail = Trim(tail.substr(1));
      }
      if (!tail.empty()) {
        interface_name = Trim(tail);
      }
      if (protocol == "unknown") {
        protocol = "connected";
      }
      if (routing_protocol_.empty() || protocol == "ospf" ||
          (routing_protocol_ == "connected" && protocol != "connected")) {
        routing_protocol_ = protocol;
      }
      routes_.push_back(RouteEntry{prefix, next_hop, protocol, metric, interface_name});
      continue;
    }

    const std::size_t metric_open = remainder.find('[');
    const std::size_t metric_close = remainder.find(']', metric_open);
    if (metric_open != std::string::npos && metric_close != std::string::npos && metric_close > metric_open) {
      metric = remainder.substr(metric_open + 1, metric_close - metric_open - 1);
    }

    const std::size_t via_pos = remainder.find(" via ");
    if (via_pos != std::string::npos) {
      prefix = Trim(remainder.substr(0, via_pos));
      std::string after_via = Trim(remainder.substr(via_pos + 5));
      const std::size_t next_hop_end = after_via.find(',');
      if (next_hop_end != std::string::npos) {
        next_hop = Trim(after_via.substr(0, next_hop_end));
        std::string tail = Trim(after_via.substr(next_hop_end + 1));
        const std::size_t iface_pos = tail.rfind(',');
        if (iface_pos != std::string::npos) {
          interface_name = Trim(tail.substr(iface_pos + 1));
        }
      } else {
        next_hop = Trim(after_via);
      }
    } else {
      prefix = Trim(remainder);
    }

    if (protocol == "unknown") {
      protocol = "static";
    }

    if (routing_protocol_.empty() || protocol == "ospf" ||
        (routing_protocol_ == "connected" && protocol != "connected")) {
      routing_protocol_ = protocol;
    }

    routes_.push_back(RouteEntry{prefix, next_hop, protocol, metric, interface_name});
  }

  if (routing_protocol_.empty()) {
    routing_protocol_ = "static";
  }
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

