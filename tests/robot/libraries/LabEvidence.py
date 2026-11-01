import errno
from datetime import datetime, timezone
import json
from pathlib import Path
import shlex
import socket
import sys
import time

from robot.api.deco import keyword

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "labs"))
from labctl import LabControl, LABS


class LabEvidence:
    ROBOT_LIBRARY_SCOPE = "SUITE"

    def __init__(self, lab):
        self.lab = lab
        self.site = LABS[lab]
        self.control = LabControl()

    @keyword
    def lab_inventory(self):
        return self.site

    @keyword
    def presentation_scenario(self):
        scene = dict(self.site["presentation"])
        scene.update(next(case for case in self.site["isolation_cases"] if case["id"] == scene["case_id"]))
        return scene

    def _save(self, path, value):
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2))
        return value

    @keyword
    def verify_live_collection(self, output_file):
        deadline = time.monotonic() + 120
        expected = [a["id"] for a in self.site["agents"]]
        latest = {}
        node_states = {n["node_id"]: n["status"] for n in self.control.gns("/nodes")}
        stopped_agents = [a["id"] for name, site in LABS.items() if name != self.lab
            and all(node_states[n] == "stopped" for n in site["nodes"]) for a in site["agents"]]
        while time.monotonic() < deadline:
            states = {v["agent_id"]: v for v in self.control.api("/agents/expected")["agents"]}
            latest = self.control.telemetry(self.lab)
            def fresh(value):
                timestamp = value.get("collected_at")
                if not timestamp:
                    return False
                age = (datetime.now(timezone.utc) - datetime.fromisoformat(timestamp.replace("Z", "+00:00"))).total_seconds()
                return 0 <= age <= max(45, 2 * value.get("monitor_interval_seconds", 30) + 10)
            offline_ok = all(not states.get(a, {}).get("connected") for a in stopped_agents)
            def complete(agent):
                value = latest[agent["id"]]
                layer = value.get("vlan_status") if agent["profile"] == "ovs" else value.get("route_status")
                return states.get(agent["id"], {}).get("connected") and fresh(value) and value.get("nic_status") and layer
            ready = offline_ok and all(complete(agent) for agent in self.site["agents"])
            if ready:
                break
            time.sleep(2)
        assert ready, "Missing live collection: " + str({a: list(latest.get(a, {})) for a in expected})
        project = self.control.api("/projects/" + self.site["project_id"])
        cidrs = {s["cidr"] for s in project["subnets"]}
        assert set(self.site["cidrs"]).issubset(cidrs), "Expected data prefixes missing: " + str(cidrs)
        summary = {}
        for agent in self.site["agents"]:
            value = latest[agent["id"]]
            vlans = [v.get("vlan_id") for v in value.get("vlan_status", {}).get("vlans", [])]
            summary[agent["id"]] = {
                "product": value.get("product"), "device_type": value.get("device_type"),
                "collected_at": value.get("collected_at"),
                "monitor_interval_seconds": value.get("monitor_interval_seconds"),
                "interfaces": len(value.get("nic_status", {}).get("interfaces", [])),
                "routes": len(value.get("route_status", {}).get("routes", [])),
                "vlans": vlans, "connected": True}
            if agent["profile"] == "eos":
                assert 20 in vlans, "Arista VLAN20 missing"
            if agent["profile"] == "ovs":
                assert 40 in vlans, "OVS VLAN40 missing"
                assert "10.40.40." not in json.dumps(value.get("nic_status")), "L2 switch has a fabricated service address"
        return self._save(output_file, {"lab": self.lab, "project_id": self.site["project_id"],
            "agents": summary, "project_cidrs": sorted(cidrs), "powered_off_agents_not_online": stopped_agents})

    def _run(self, agent, command, timeout=30, required=True):
        result = self.control.ssh(agent, command, timeout=timeout, check=required)
        return result.returncode, result.stdout.decode(errors="replace").strip()

    @keyword
    def verify_network_path(self, output_file):
        results = {}
        for agent in self.site["agents"]:
            if agent["profile"] in ["ubuntu", "ios"]:
                code, text = self._run(agent, "curl -sS -o /dev/null -w '%{http_code}' --max-time 15 https://example.com", timeout=25)
                assert code == 0 and text.endswith("200"), agent["id"] + " Internet HTTPS failed: " + text[-100:]
                results[agent["id"] + "_internet_https"] = 200
        if self.lab == "lab2":
            switch = next(a for a in self.site["agents"] if a["profile"] == "eos")
            _, vlan = self._run(switch, "FastCli -p 15 -M -c 'show vlan brief'")
            assert "LAB2_DATA" in vlan and "Et1" in vlan and "Et2" in vlan
            results["arista_vlan20_ports"] = vlan
        if self.lab in ["lab2", "lab4"]:
            host = next(a for a in self.site["agents"] if a["profile"] == "ubuntu")
            code, text = self._run(host, "curl -sS --interface eth0 --connect-timeout 2 --max-time 3 http://" +
                self.site["server_ip"] + ":3000/actuator/health", timeout=8, required=False)
            assert code != 0, "Data NIC unexpectedly reached management server"
            results["data_nic_management_connection"] = "blocked"
        if self.lab == "lab3":
            for index, agent in enumerate(self.site["agents"]):
                peer = self.site["agents"][1 - index]["host"]
                gateway = "10.30." + ("10" if index == 0 else "20") + ".1"
                self._run(agent, "ping -c 1 -W 2 " + gateway)
                code, _ = self._run(agent, "ping -c 1 -W 2 " + peer, required=False)
                assert code != 0, "Cross-subnet ICMP unexpectedly allowed"
                code, _ = self._run(agent, "ping -c 1 -W 2 " + self.site["server_ip"], required=False)
                assert code != 0, "Management ICMP unexpectedly allowed"
                _, health = self._run(agent, "curl -sS --max-time 5 http://" +
                    self.site["server_ip"] + ":3000/actuator/health")
                assert json.loads(health)["status"] == "UP"
                with socket.socket() as connection:
                    connection.settimeout(2)
                    connection.bind((self.site["server_ip"], 0))
                    try:
                        connection.connect((agent["host"], 22))
                        raise AssertionError("Direct management-to-container SSH unexpectedly allowed")
                    except OSError as error:
                        assert error.errno != errno.ENETUNREACH, "Return route is missing"
                results[agent["id"]] = {
                    "gateway_icmp": "allowed", "peer_icmp": "blocked", "management_icmp": "blocked",
                    "backend_tcp3000": "allowed", "direct_ssh": "blocked", "ssh_via_firewall": "allowed"}
        if self.lab == "lab4":
            switch = next(a for a in self.site["agents"] if a["profile"] == "ovs")
            _, first = self._run(switch, "ovs-vsctl get Port eth0 tag")
            _, second = self._run(switch, "ovs-vsctl get Port eth2 tag")
            assert first == second == "40"
            code, _ = self._run(switch, "ovs-vsctl port-to-br eth1", required=False)
            assert code != 0, "Management interface must remain outside data bridge"
            _, flows = self._run(switch, "ovs-ofctl dump-flows br-data")
            assert "NORMAL" in flows
            router = next(a for a in self.site["agents"] if a["profile"] == "frr")
            _, routes = self._run(router, "vtysh -c 'show ip route'")
            assert "10.40.40.0/24" in routes
            results["ovs_vlan40"] = {"eth0": 40, "eth2": 40, "management_in_bridge": False, "flows": flows}
            results["frr_routes"] = routes
        firewall = self.site.get("firewall")
        if firewall:
            probes = {}
            for target in ["firmware", "interfaces", "rules", "nat", "aliases"]:
                response = self.control.api("/opnsense/credentials/" + firewall["id"] + "/probe?target=" + target, {}, "POST")
                assert response["ok"] and response["status_code"] == 200, "OPNsense " + target + ": " + str(response.get("error"))
                probes[target] = {k: response.get(k) for k in ["ok", "status_code", "summary"]}
            results["opnsense_probes"] = probes
        return self._save(output_file, {"lab": self.lab, "checks": results})

    @keyword
    def verify_prober_processes(self, output_file):
        result = {}
        for agent in self.site["agents"]:
            _, text = self._run(agent, "pids=$(pidof sonar_validator_prober || pgrep -x sonar_validator || true); set -- $pids; test \"$#\" -eq 1; p=$1; "
                "awk '/Threads:/ { print $2 }' /proc/$p/status; "
                "find /proc/$p/task -mindepth 1 -maxdepth 1 -type d | wc -l; "
                "pgrep -af '^sonar-terminal-job' || true")
            lines = text.splitlines()
            assert lines[:2] == ["4", "4"], agent["id"] + ": unexpected thread count"
            assert len(lines) == 2, agent["id"] + ": terminal job remains"
            result[agent["id"]] = {"threads": 4, "remaining_terminal_jobs": 0}
        return self._save(output_file, result)

    @keyword
    def prepare_isolation_check(self, agent_id, target_cidr, probe_agent_id, destination, interface_down, output_dir):
        agents = {agent["id"]: agent for agent in self.site["agents"]}
        assert agent_id in agents and probe_agent_id in agents, "Isolation checks are limited to the selected lab inventory"
        active = self.control.api("/quarantine?project_id=" + self.site["project_id"])
        assert not active["quarantined"] and not active.get("pending_releases"), "Existing operator isolation must be preserved"
        self.isolation = {"agent": agents[agent_id], "cidr": target_cidr,
            "probe": agents[probe_agent_id], "destination": destination,
            "interface_down": str(interface_down).lower() == "true", "output": Path(output_dir), "events": [], "requested": False}
        self.isolation["baseline"] = self.control.api("/network/topology/" + self.site["project_id"])
        topology = self.isolation["baseline"]
        targets = [n for n in topology["nodes"] if n.get("cidr") == target_cidr] if target_cidr else [
            n for n in topology["nodes"] if n.get("agent_id") == agent_id]
        assert targets and all(n.get("observed_up") is True and not n.get("config_stale") for n in targets), "Fresh baseline observations are required"
        ids = {n["id"] for n in targets}
        assert any(e.get("observed") and (e["source"] in ids or e["target"] in ids) for e in topology["edges"]), "No baseline observed edge to exercise"
        self.isolation["config_before"] = self.control.api("/agents/" + agent_id + "/config")
        if agents[agent_id]["profile"] == "frr":
            code, rules = self._run(agents[agent_id], "export PATH=/usr/sbin:/usr/bin:/sbin:/bin; iptables -S; iptables -t nat -S")
            assert code == 0
            self.isolation["firewall_before"] = rules
        self._assert_probe(True)
        self._isolation_event("baseline", topology)

    def _assert_probe(self, reachable):
        ctx = self.isolation
        destination = ctx["destination"]
        https = destination.startswith("https://")
        command = ("curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 2 --max-time 5 " if https
            else "ping -n -c 1 -W 2 ") + shlex.quote(destination)
        code, output = self._run(ctx["probe"], "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; "
            + command, timeout=10, required=False)
        observed = code == 0 and (not https or output.endswith("200"))
        assert observed == reachable, "Data path expected reachable=" + str(reachable) + ": " + output[-300:]
        code, _ = self._run(ctx["agent"], "printf management-ssh-ok", timeout=10, required=False)
        assert code == 0, "Management SSH must remain available throughout isolation"

    def _isolation_event(self, phase, value):
        ctx = self.isolation
        ctx["events"].append({"phase": phase, "at": datetime.now(timezone.utc).isoformat(), "value": value})
        self._save(ctx["output"] / "isolation.json", {"lab": self.lab, "agent": ctx["agent"]["id"],
            "target_cidr": ctx["cidr"], "events": ctx["events"]})

    @keyword
    def reboot_isolated_device_and_verify_journal(self):
        ctx = self.isolation
        agent = ctx["agent"]
        assert self.lab == "lab1" and agent["profile"] == "ios", "Reboot regression is limited to Lab1 Cisco inventory"
        self._assert_isolation_confirmation(True)
        journal = agent["root"] + "/data/quarantine_interfaces.json"
        before = json.loads(self.control.ssh(agent, "cat " + shlex.quote(journal)).stdout)
        assert isinstance(before, dict) and before, "An active interface journal is required"
        self._isolation_event("before-reboot-journal", before)
        node = agent["gns3_node"]
        self.control.gns("/nodes/" + node + "/stop", {}, "POST")
        self.control.gns("/nodes/" + node + "/start", {}, "POST")
        self.control.wait_ready(self.lab, timeout=600)
        after = json.loads(self.control.ssh(agent, "cat " + shlex.quote(journal)).stdout)
        assert after == before, "Original admin states did not persist across GNS3 restart"
        self._isolation_event("after-reboot-journal", after)

    @keyword
    def request_live_isolation(self):
        ctx = self.isolation
        payload = {"project_id": self.site["project_id"], "reason": "Robot live topology isolation regression",
            "requested_by": "robot-network-test"}
        if ctx["cidr"]:
            payload["target_cidr"] = ctx["cidr"]
        response = self.control.api("/quarantine/" + ctx["agent"]["id"], payload)
        assert not response.get("rejected"), response.get("reason")
        ctx["request_id"] = response["request_id"]
        ctx["requested"] = True
        self._isolation_event("isolation-request", response)

    @keyword
    def live_isolation_should_be_confirmed(self):
        self._assert_isolation_confirmation(True)

    def _assert_isolation_confirmation(self, isolated):
        ctx = self.isolation
        states = self.control.api("/quarantine/" + ctx["agent"]["id"])["history"]
        state = next((s for s in states if s.get("request_id") == ctx.get("request_id")), None)
        assert state and state.get("status") == "APPLIED", "Current operation not confirmed: " + str(state)
        assert state["active"] == isolated, "ACK belongs to the wrong isolation operation"
        self._assert_probe(not isolated)
        topology = self.control.api("/network/topology/" + self.site["project_id"])
        targets = [n for n in topology["nodes"] if n.get("cidr") == ctx["cidr"]] if ctx["cidr"] else [
            n for n in topology["nodes"] if n.get("agent_id") == ctx["agent"]["id"]]
        assert targets, "Topology must contain the affected observed subnet"
        assert all(n.get("quarantined") == isolated for n in targets), "Topology does not reflect confirmed enforcement"
        if ctx["interface_down"]:
            assert all(n.get("observed_up") == (not isolated) for n in targets), "Collected interface change not reflected in topology"
            ids = {n["id"] for n in targets}
            observed = [e for e in topology["edges"] if e.get("observed") and e.get("agent_id") == ctx["agent"]["id"] and (e["source"] in ids or e["target"] in ids)]
            baseline = [e for e in ctx["baseline"]["edges"] if e.get("observed") and e.get("agent_id") == ctx["agent"]["id"] and (e["source"] in ids or e["target"] in ids)]
            assert (not observed) if isolated else (len(observed) >= len(baseline)), "Observed routing edges failed to change or recover"
        elif isolated:
            assert any(e.get("blocked") for e in topology["edges"] if e.get("observed")), "ACL enforcement must annotate an observed path"
        config = self.control.api("/agents/" + ctx["agent"]["id"] + "/config")
        if not isolated or not ctx["interface_down"]:
            assert self._routing_signature(config) == self._routing_signature(ctx["config_before"]), "Original interface or routing state was not preserved/restored"
        if not isolated and "firewall_before" in ctx:
            _, rules = self._run(ctx["agent"], "export PATH=/usr/sbin:/usr/bin:/sbin:/bin; iptables -S; iptables -t nat -S")
            assert rules == ctx["firewall_before"], "Original firewall or NAT rule order was changed"
        self._isolation_event("isolated-confirmed" if isolated else "released-confirmed", {"state": state, "topology": topology,
            "data_probe": "blocked" if isolated else "reachable", "management_ssh": "reachable"})
        return topology

    @staticmethod
    def _routing_signature(config):
        interfaces = {name: {key: value.get(key) for key in ["adminState", "operState", "addresses", "accessVlan", "trunkVlans"]}
            for name, value in config.get("interfaces", {}).items()}
        for value in interfaces.values():
            for key in ["addresses", "trunkVlans"]:
                value[key] = sorted(value.get(key) or [])
        routes = [{key: route.get(key) for key in ["prefix", "nextHop", "nextHopInterface", "protocol", "metric", "defaultRoute"]}
            for route in config.get("routes", [])]
        return {"interfaces": interfaces, "routes": sorted(routes, key=lambda route: json.dumps(route, sort_keys=True))}

    @keyword
    def request_live_release(self):
        ctx = self.isolation
        import urllib.parse
        query = "?" + urllib.parse.urlencode({"target_cidr": ctx["cidr"]}) if ctx["cidr"] else ""
        response = self.control.api("/quarantine/" + ctx["agent"]["id"] + query, method="DELETE")
        if response.get("released"):
            ctx["request_id"] = response["request_id"]
            self._isolation_event("release-request", response)

    @keyword
    def live_release_should_be_confirmed(self):
        return self._assert_isolation_confirmation(False)

    @keyword
    def restore_live_isolation(self):
        if not getattr(self, "isolation", None):
            return
        if not self.isolation.get("requested"):
            self.isolation = None
            return
        if not self.isolation.get("request_id"):
            states = self.control.api("/quarantine/" + self.isolation["agent"]["id"])["history"]
            current = next((state for state in states if state["active"] and
                (state.get("target_cidr") or "") == self.isolation["cidr"]), None)
            if not current:
                self._assert_probe(True)
                self.isolation = None
                return
            self.isolation["request_id"] = current["request_id"]
        self.request_live_release()
        deadline = time.monotonic() + 150
        while time.monotonic() < deadline:
            try:
                self.live_release_should_be_confirmed()
                self.isolation = None
                return
            except AssertionError:
                time.sleep(3)
        raise AssertionError("Isolation cleanup did not restore the original data path; keep the lab running for diagnosis")

    @keyword
    def adopt_ui_isolation_request(self):
        ctx = self.isolation
        states = self.control.api("/quarantine/" + ctx["agent"]["id"])["history"]
        state = next((state for state in states if state["active"] and (state.get("target_cidr") or "") == ctx["cidr"]), None)
        assert state, "UI isolation request was not persisted"
        ctx["request_id"] = state["request_id"]
        ctx["requested"] = True
        self._isolation_event("ui-isolation-request", state)

    @keyword
    def arm_isolation_cleanup(self):
        self.isolation["requested"] = True

    @keyword
    def adopt_ui_release_request(self):
        ctx = self.isolation
        states = self.control.api("/quarantine/" + ctx["agent"]["id"])["history"]
        previous = next(state for state in reversed(ctx["events"]) if state["phase"] == "isolated-confirmed")["value"]["state"]
        state = next((state for state in states if not state["active"]
            and (state.get("target_cidr") or "") == ctx["cidr"]
            and state.get("quarantined_at") == previous["quarantined_at"]
            and state.get("request_id") != ctx["request_id"]), None)
        assert state, "UI release request for this isolation was not persisted"
        ctx["request_id"] = state["request_id"]
        self._isolation_event("ui-release-request", state)
