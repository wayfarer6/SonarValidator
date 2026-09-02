#include "routing_table.hpp"

#include <cassert>
#include <string>

int main()
{
  RoutingTable table;
  table.SetConnectionId("conn-01");
  table.SetConnectedIp("10.0.0.2");
  table.SetConnectedNodeId("R1");
  table.SetRoutingProtocolValue("ospf");

  table.AddRoute("10.0.0.0/24", "10.0.0.1", "connected", "0", "eth0");
  table.AddRoute("192.168.10.0/24", "10.0.0.1", "ospf", "10", "eth0");

  assert(table.GetConnectionId() == "conn-01");
  assert(table.GetConnectedIp() == "10.0.0.2");
  assert(table.GetConnectedNodeId() == "R1");
  assert(table.GetRoutingProtocol() == "ospf");
  assert(table.GetRouteCount() == 2);

  Json json = table.ToJson();
  assert(json["connection_id"] == "conn-01");
  assert(json["routes"][0]["prefix"] == "10.0.0.0/24");

  const FrrRoutingTableView frr_view;
  const std::string frr_output = frr_view.Render(table);
  assert(frr_output.find("Codes:") != std::string::npos);
  assert(frr_output.find("10.0.0.0/24") != std::string::npos);
  assert(frr_output.find("ospf") != std::string::npos);

  const CiscoRoutingTableView cisco_view;
  const std::string cisco_output = cisco_view.Render(table);
  assert(cisco_output.find("show ip route") != std::string::npos);
  assert(cisco_output.find("192.168.10.0/24") != std::string::npos);

  table.SetRoutingProtocol();
  assert(table.GetRoutingProtocol() == "ospf");

  /* 실전 Test 1 FRR */
  const std::string frr_table = R"(
Codes: K - kernel route, C - connected, S - static, R - RIP,
       O - OSPF, I - IS-IS, B - BGP, E - EIGRP, N - NHRP,
       T - Table, v - VNC, V - VNC-Direct, A - Babel, F - PBR,
       f - OpenFabric,
       > - selected route, * - FIB route, q - queued, r - rejected, b - backup
       t - trapped, o - offload failure

K>* 0.0.0.0/0 [0/1] via 172.16.254.254, eth0, 00:08:34
O>* 10.10.0.0/16 [110/200] via 172.16.255.11, eth1, weight 1, 00:07:26
O>* 10.20.0.0/24 [110/200] via 172.16.255.12, eth1, weight 1, 00:07:26
O>* 10.30.0.0/24 [110/200] via 172.16.255.13, eth1, weight 1, 00:07:26
C>* 172.16.254.0/24 is directly connected, eth0, 00:08:34
O   172.16.255.0/24 [110/100] is directly connected, eth1, weight 1, 00:07:26
C>* 172.16.255.0/24 is directly connected, eth1, 00:08:34
)";

  RoutingTable parsed_table;
  parsed_table.ParseFrrTable(frr_table);
  assert(parsed_table.GetRouteCount() == 7);
  assert(parsed_table.GetRoutingProtocol() == "ospf");
  assert(parsed_table.GetRoutes()[0].prefix == "0.0.0.0/0");
  assert(parsed_table.GetRoutes()[0].next_hop == "172.16.254.254");
  assert(parsed_table.GetRoutes()[0].protocol == "kernel");
  assert(parsed_table.GetRoutes()[1].prefix == "10.10.0.0/16");
  assert(parsed_table.GetRoutes()[1].protocol == "ospf");
  assert(parsed_table.GetRoutes()[4].prefix == "172.16.254.0/24");
  assert(parsed_table.GetRoutes()[4].interface_name == "eth0");

  const std::string cisco_table = R"(
show ip route
Codes: C - connected, S - static, R - RIP, O - OSPF, B - BGP

C        192.168.1.0/24 is directly connected, GigabitEthernet0/0
O        10.10.0.0/16 [110/200] via 172.16.255.11, 00:07:26, GigabitEthernet0/1
O        10.20.0.0/24 [110/200] via 172.16.255.12, 00:07:26, GigabitEthernet0/1
)";

  RoutingTable parsed_cisco;
  parsed_cisco.ParseCiscoTable(cisco_table);
  assert(parsed_cisco.GetRouteCount() == 3);
  assert(parsed_cisco.GetRoutingProtocol() == "ospf");
  assert(parsed_cisco.GetRoutes()[0].prefix == "192.168.1.0/24");
  assert(parsed_cisco.GetRoutes()[0].interface_name == "GigabitEthernet0/0");
  assert(parsed_cisco.GetRoutes()[1].next_hop == "172.16.255.11");
  assert(parsed_cisco.GetRoutes()[1].interface_name == "GigabitEthernet0/1");

  return 0;
}
