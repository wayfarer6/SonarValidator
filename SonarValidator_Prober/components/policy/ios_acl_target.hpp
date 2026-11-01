#pragma once
#include <cctype>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

namespace ios_acl_target {
inline bool ParseIpv4(const std::string& text, unsigned int& value)
{
    unsigned int address = 0;
    int octets = 0;
    int shift = 24;
    std::size_t index = 0;

    while (index <= text.size())
    {
        const std::size_t dot = text.find('.', index);
        const std::string part = text.substr(
            index, dot == std::string::npos ? std::string::npos : dot - index);
        if (part.empty() || part.size() > 3)
        {
            return false;
        }
        int octet = 0;
        for (const char ch : part)
        {
            if (std::isdigit(static_cast<unsigned char>(ch)) == 0)
            {
                return false;
            }
            octet = (octet * 10) + (ch - '0');
        }
        if (octet > 255 || shift < 0)
        {
            return false;
        }
        address |= static_cast<unsigned int>(octet) << shift;
        ++octets;
        shift -= 8;

        if (dot == std::string::npos)
        {
            break;
        }
        index = dot + 1;
    }

    if (octets != 4)
    {
        return false;
    }
    value = address;
    return true;
}

inline bool IpInCidr(const std::string& address, const std::string& cidr)
{
    const std::size_t slash = cidr.find('/');
    if (slash == std::string::npos)
    {
        return false;
    }

    unsigned int host = 0;
    unsigned int base = 0;
    if (!ParseIpv4(address, host) || !ParseIpv4(cidr.substr(0, slash), base))
    {
        return false;
    }

    int prefix = 0;
    const std::string prefix_text = cidr.substr(slash + 1);
    if (prefix_text.empty() || prefix_text.size() > 2)
    {
        return false;
    }
    for (const char ch : prefix_text)
    {
        if (std::isdigit(static_cast<unsigned char>(ch)) == 0)
        {
            return false;
        }
        prefix = (prefix * 10) + (ch - '0');
    }
    if (prefix > 32)
    {
        return false;
    }

    const unsigned int mask = (prefix == 0) ? 0u : (0xffffffffu << (32 - prefix));
    return (host & mask) == (base & mask);
}

inline std::vector<std::pair<std::string, std::string>> ReadIosInterfaces(const std::string& snapshot)
{
    std::vector<std::pair<std::string, std::string>> interfaces;

    if (snapshot.empty())
    {
        return interfaces;
    }

    std::istringstream stream(snapshot);
    std::string line;
    bool header_seen = false;
    while (std::getline(stream, line))
    {
        if (!header_seen)
        {
            header_seen = line.rfind("Interface", 0) == 0;
            continue;
        }
        std::istringstream fields(line);
        std::string name;
        std::string address;
        if (!(fields >> name >> address))
        {
            continue;
        }
        unsigned int ignored = 0;
        if (!ParseIpv4(address, ignored))
        {
            continue;
        }
        interfaces.emplace_back(name, address);
    }
    return interfaces;
}

inline std::string ResolveIosBindInterface(const std::string& brief, const std::string& hint,
                                    const std::string& subnet_cidr)
{
    const auto interfaces = ReadIosInterfaces(brief);
    std::vector<std::string> candidates;
    for (const auto& [name, address] : interfaces) {
        if (name.starts_with("VirtualPortGroup") || name.starts_with("Management")) continue;
        if (!subnet_cidr.empty() ? IpInCidr(address, subnet_cidr) : name == hint)
            candidates.push_back(name);
    }
    if (candidates.size() == 1) return candidates.front();
    return {};
}

}
