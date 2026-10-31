
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include "components/parser/cli_output_parser.hpp"

namespace
{

std::string ReadAll(const std::string& path)
{
    std::ifstream input(path);
    if (!input)
    {
        return {};
    }
    std::ostringstream buffer;
    buffer << input.rdbuf();
    return buffer.str();
}

}  // namespace

int main(int argc, char** argv)
{
    if (argc < 3)
    {
        std::cerr << "usage: " << argv[0] << " <target> <file> [vendor]\n"
                  << "  target: nic|brief|route|interface|ovs|vlan|switchport|ruleset\n"
                  << "  vendor: ubuntu|frr|cisco|arista|ovs|nftables (기본: 자동 판별)\n";
        return 2;
    }

    const std::string target = argv[1];
    const std::string raw = ReadAll(argv[2]);
    if (raw.empty())
    {
        std::cerr << "empty input: " << argv[2] << '\n';
        return 2;
    }

    // 벤더를 강제하면 자동 판별을 건너뛴다(실장비 형식 검증용).
    cli_parser::Vendor forced = cli_parser::Vendor::kUnknown;
    if (argc > 3)
    {
        const std::string name = argv[3];
        if (name == "ubuntu")
        {
            forced = cli_parser::Vendor::kUbuntu;
        }
        else if (name == "frr")
        {
            forced = cli_parser::Vendor::kFrr;
        }
        else if (name == "cisco")
        {
            forced = cli_parser::Vendor::kCisco;
        }
        else if (name == "arista")
        {
            forced = cli_parser::Vendor::kArista;
        }
        else if (name == "ovs")
        {
            forced = cli_parser::Vendor::kOpenVSwitch;
        }
        else if (name == "nftables")
        {
            forced = cli_parser::Vendor::kNftables;
        }
    }

    nlohmann::json result;
    if (target == "nic")
    {
        result = cli_parser::ParseNicStatus(raw);
    }
    else if (target == "brief")
    {
        result = cli_parser::ParseNicBrief(raw);
    }
    else if (target == "route")
    {



        cli_parser::Vendor vendor = forced;
        if (vendor == cli_parser::Vendor::kUnknown)
        {
            const std::size_t first_line_end = raw.find('\n');
            const std::string first_line =
                raw.substr(0, first_line_end == std::string::npos ? raw.size() : first_line_end);
            if (first_line.rfind("default ", 0) == 0 ||
                first_line.rfind("blackhole", 0) == 0 ||
                first_line.find(" proto ") != std::string::npos)
            {
                vendor = cli_parser::Vendor::kUbuntu;
            }
            else if (first_line.find("Codes:") != std::string::npos ||
                     first_line.find("Gateway of last resort") != std::string::npos)
            {
                vendor = cli_parser::Vendor::kFrr;
            }
        }
        result = cli_parser::ParseRouteStatus(raw, vendor);
    }
    else if (target == "interface")
    {
        result = cli_parser::ParseInterfaceStatus(raw, cli_parser::Vendor::kUnknown);
    }
    else if (target == "ovs")
    {
        result = cli_parser::ParseOvsTopology(raw);
    }
    else if (target == "vlan")
    {
        result = cli_parser::ParseSwitchVlan(raw);
    }
    else if (target == "switchport")
    {
        result = cli_parser::ParseSwitchPorts(raw);
    }
    else if (target == "ruleset")
    {
        result = cli_parser::ParseFirewallRules(raw);
    }
    else if (target == "arp")
    {
        result = cli_parser::ParseArpTable(raw, cli_parser::Vendor::kUnknown);
    }
    else
    {
        std::cerr << "unknown target: " << target << '\n';
        return 2;
    }

    std::cout << result.dump(2) << '\n';

    const bool parsed = result.value("parsed", false);
    if (!parsed)
    {
        std::cerr << "[WARN] 파싱 경고/오류 있음: "
                  << result.value("parse_error", std::string{"(원인 미상)"}) << '\n';
        return 1;
    }
    return 0;
}
