#include "cli_output_parser.hpp"

#include <algorithm>
#include <cctype>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

// ANTLR 생성 코드 (빌드 시 parser/grammar/*.g4 로부터 생성됨)
#include "FrrRouterBaseVisitor.h"
#include "FrrRouterLexer.h"
#include "FrrRouterParser.h"
#include "IpAddrBaseVisitor.h"
#include "IpAddrLexer.h"
#include "IpAddrParser.h"
#include "NftablesRuleBaseVisitor.h"
#include "NftablesRuleLexer.h"
#include "NftablesRuleParser.h"
#include "OvsTopologyBaseVisitor.h"
#include "OvsTopologyLexer.h"
#include "OvsTopologyParser.h"
#include "SwitchTopologyBaseVisitor.h"
#include "SwitchTopologyLexer.h"
#include "SwitchTopologyParser.h"

#include <antlr4-runtime.h>

namespace cli_parser
{
namespace
{

using antlr4::ANTLRInputStream;
using antlr4::CommonTokenStream;
using antlr4::ParserRuleContext;
using Json = nlohmann::json;

// ============================================================================
// 공통 유틸
// ============================================================================

// CLI 출력은 버전에 따라 마지막 개행이 없을 수 있다. 문법이 NEWLINE 을 요구하므로 보정.
std::string NormalizeForGrammar(const std::string& raw)
{
    std::string text = raw;
    text.erase(std::remove(text.begin(), text.end(), '\r'), text.end());
    if (text.empty() || text.back() != '\n')
    {
        text.push_back('\n');
    }
    return text;
}

std::vector<std::string> SplitLines(const std::string& text)
{
    std::vector<std::string> lines;
    std::istringstream stream(text);
    std::string line;
    while (std::getline(stream, line))
    {
        lines.push_back(line);
    }
    return lines;
}

std::vector<std::string> SplitTokens(const std::string& line)
{
    std::vector<std::string> tokens;
    std::istringstream stream(line);
    std::string token;
    while (stream >> token)
    {
        tokens.push_back(token);
    }
    return tokens;
}

std::string Trim(const std::string& text)
{
    std::size_t start = 0;
    while (start < text.size() && std::isspace(static_cast<unsigned char>(text[start])) != 0)
    {
        ++start;
    }
    std::size_t end = text.size();
    while (end > start && std::isspace(static_cast<unsigned char>(text[end - 1])) != 0)
    {
        --end;
    }
    return text.substr(start, end - start);
}

// 끝의 구두점을 제거한다. (`eth0,` -> `eth0`, `connected,` -> `connected`)
//  주의: 따옴표(`"`)는 값 자체일 수 있으므로 제거하지 않는다.
//        (제거하면 `"eth0"` 이 `"eth0` 이 되어 따옴표 짝이 깨진다.)
std::string TrimPunct(const std::string& text)
{
    std::size_t end = text.size();
    while (end > 0)
    {
        const char ch = text[end - 1];
        if (ch == ',' || ch == ';' || ch == ')')
        {
            --end;
        }
        else
        {
            break;
        }
    }
    return text.substr(0, end);
}

std::string Unquote(const std::string& text)
{
    if (text.size() >= 2 && text.front() == '"' && text.back() == '"')
    {
        return text.substr(1, text.size() - 2);
    }
    return text;
}

bool IsNumber(const std::string& text)
{
    return !text.empty() &&
           std::all_of(text.begin(), text.end(), [](unsigned char ch) {
               return std::isdigit(ch) != 0;
           });
}

// `10.10.131.1/24` -> address="10.10.131.1", prefix_len=24 (슬래시 없으면 -1)
bool SplitCidr(const std::string& value, std::string& address, int& prefix_len)
{
    const std::size_t slash = value.find('/');
    if (slash == std::string::npos)
    {
        address = value;
        prefix_len = -1;
        return !value.empty();
    }
    address = value.substr(0, slash);
    const std::string prefix = value.substr(slash + 1);
    prefix_len = IsNumber(prefix) ? std::stoi(prefix) : -1;
    return !address.empty();
}

// `[141]` / `141,142` / `141` -> {141,142}
std::vector<int> ParseVlanNumbers(const std::string& text)
{
    std::string cleaned;
    for (const char ch : text)
    {
        if (std::isdigit(static_cast<unsigned char>(ch)) != 0 || ch == ',')
        {
            cleaned.push_back(ch);
        }
    }

    std::vector<int> values;
    std::istringstream stream(cleaned);
    std::string token;
    while (std::getline(stream, token, ','))
    {
        if (IsNumber(token))
        {
            values.push_back(std::stoi(token));
        }
    }
    return values;
}

Json VlanJson(const std::vector<int>& vlans)
{
    Json array = Json::array();
    for (const int vlan : vlans)
    {
        array.push_back(vlan);
    }
    return array;
}

bool Contains(const Json& array, const std::string& value)
{
    for (const auto& item : array)
    {
        if (item.is_string() && item.get<std::string>() == value)
        {
            return true;
        }
    }
    return false;
}

// MAC 주소(콜론 5개 이상)인지 판별
bool LooksLikeMac(const std::string& token)
{
    return std::count(token.begin(), token.end(), ':') >= 5;
}

// IP 주소(CIDR 포함) 형태인지 판별한다.
//  `10.10.131.1`, `10.10.131.1/24`, `fe80::1/64` -> true
//  `eth1.131` 처럼 점이 하나뿐인 이름 -> false (인터페이스명으로 취급해야 함)
bool LooksLikeIp(const std::string& token)
{
    if (token.find(':') != std::string::npos)
    {
        return true;  // IPv6 / MAC
    }

    std::string bare = token;
    const std::size_t slash = bare.find('/');
    if (slash != std::string::npos)
    {
        bare = bare.substr(0, slash);
    }

    std::size_t dots = 0;
    for (const char ch : bare)
    {
        if (ch == '.')
        {
            ++dots;
        }
        else if (std::isdigit(static_cast<unsigned char>(ch)) == 0)
        {
            return false;
        }
    }
    return dots == 3;  // 점 4개 옥텟이면 IPv4
}

// ============================================================================
// 파싱 세션
//
//  lexer/토큰스트림/parser/오류리스너의 수명을 한 곳에서 관리한다.
//  문법이 WS 를 skip 하므로 getText() 에는 공백이 사라진다.
//  따라서 "줄 종류 판정은 문법으로, 필드 값은 원문 라인으로" 처리한다.
//  (WS 를 skip 해도 토큰의 line/charPositionInLine 은 보존된다.)
// ============================================================================

class CollectingErrorListener : public antlr4::BaseErrorListener
{
public:
    void syntaxError(antlr4::Recognizer* /*recognizer*/,
                     antlr4::Token* /*offendingSymbol*/,
                     size_t line,
                     size_t charPositionInLine,
                     const std::string& msg,
                     std::exception_ptr /*e*/) override
    {
        ++error_count_;
        if (error_count_ <= kMaxReported)
        {
            if (!message_.empty())
            {
                message_ += "; ";
            }
            message_ += "line " + std::to_string(line) + ":" +
                        std::to_string(charPositionInLine) + " " + msg;
        }
    }

    bool HasError() const { return error_count_ > 0; }
    std::size_t ErrorCount() const { return error_count_; }
    const std::string& Message() const { return message_; }

private:
    static constexpr std::size_t kMaxReported = 5;
    std::size_t error_count_{0};
    std::string message_;
};

template <typename LexerT, typename ParserT>
class ParseSession
{
public:
    explicit ParseSession(const std::string& raw)
        : normalized_(NormalizeForGrammar(raw)),
          lines_(SplitLines("\n" + normalized_)),
          input_(normalized_),
          lexer_(&input_),
          tokens_(&lexer_),
          parser_(&tokens_)
    {
        lexer_.removeErrorListeners();
        parser_.removeErrorListeners();
        lexer_.addErrorListener(&listener_);
        parser_.addErrorListener(&listener_);
    }

    ParserT& Parser() { return parser_; }
    std::size_t ErrorCount() const { return listener_.ErrorCount(); }
    const std::string& ErrorMessage() const { return listener_.Message(); }

    // 컨텍스트가 시작하는 원문 라인을 그대로 돌려준다.
    std::string LineOf(const ParserRuleContext* ctx) const
    {
        if (ctx == nullptr || ctx->getStart() == nullptr)
        {
            return {};
        }
        const std::size_t line = ctx->getStart()->getLine();
        if (line == 0 || line >= lines_.size())
        {
            return {};
        }
        return lines_[line];
    }

    std::vector<std::string> TokensOf(const ParserRuleContext* ctx) const
    {
        return SplitTokens(LineOf(ctx));
    }

    // 줄 선두 토큰의 컬럼 = 들여쓰기 깊이 (WS 를 skip 해도 위치는 보존된다)
    std::size_t IndentOf(const ParserRuleContext* ctx) const
    {
        if (ctx == nullptr || ctx->getStart() == nullptr)
        {
            return 0;
        }
        return ctx->getStart()->getCharPositionInLine();
    }

private:
    std::string normalized_;
    std::vector<std::string> lines_;
    ANTLRInputStream input_;
    LexerT lexer_;
    CommonTokenStream tokens_;
    ParserT parser_;
    CollectingErrorListener listener_;
};

// 파싱 결과에 품질 정보를 덧붙인다.
// 오류가 있어도 이미 뽑은 정보는 버리지 않는다(부분 결과도 서버에 유용하다).
template <typename SessionT>
Json AttachParseInfo(Json body, const SessionT& session)
{
    body["parsed"] = session.ErrorCount() == 0;
    if (session.ErrorCount() > 0)
    {
        body["parse_warnings"] = session.ErrorCount();
        body["parse_error"] = session.ErrorMessage();
    }
    return body;
}

Json MakeParseFailure(const std::string& raw, const std::string& error)
{
    Json failure;
    failure["parsed"] = false;
    failure["parse_error"] = error;
    failure["raw"] = raw;
    return failure;
}

// ============================================================================
// IpAddr — NIC / 주소 / 라우트
// ============================================================================

// `2: eth1.131@eth1: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP qlen 1000`
Json ParseIfaceHeaderTokens(const std::vector<std::string>& tokens)
{
    Json iface = Json::object();
    if (tokens.empty())
    {
        return iface;
    }

    // 첫 토큰은 `2:` 형태(INDEX + COLON)
    std::string index = tokens[0];
    const std::size_t colon = index.find(':');
    if (colon != std::string::npos)
    {
        index = index.substr(0, colon);
    }
    if (IsNumber(index))
    {
        iface["index"] = std::stoi(index);
    }

    if (tokens.size() > 1)
    {
        std::string name = tokens[1];
        if (!name.empty() && name.back() == ':')
        {
            name.pop_back();
        }
        // eth1.131@eth1 -> name=eth1.131, parent=eth1
        const std::size_t at = name.find('@');
        if (at != std::string::npos)
        {
            iface["parent"] = name.substr(at + 1);
            name = name.substr(0, at);
        }
        iface["name"] = name;
    }

    for (const auto& token : tokens)
    {
        if (token.size() >= 2 && token.front() == '<' && token.back() == '>')
        {
            Json flags = Json::array();
            std::stringstream stream(token.substr(1, token.size() - 2));
            std::string flag;
            while (std::getline(stream, flag, ','))
            {
                if (!flag.empty())
                {
                    flags.push_back(flag);
                }
            }
            iface["flags"] = flags;
            break;
        }
    }

    static const char* kKeys[] = {"mtu", "qdisc", "state", "qlen", "group", "link-netnsid"};
    for (std::size_t i = 0; i + 1 < tokens.size(); ++i)
    {
        for (const char* key : kKeys)
        {
            if (tokens[i] == key)
            {
                iface[key] = TrimPunct(tokens[i + 1]);
            }
        }
    }
    return iface;
}

// `link/ether 02:42:7c:24:78:01 brd ff:ff:ff:ff:ff:ff`
void ApplyLinkLine(const std::vector<std::string>& tokens, Json& iface)
{
    for (std::size_t i = 0; i + 1 < tokens.size(); ++i)
    {
        if (tokens[i].rfind("link/", 0) == 0)
        {
            iface["link_type"] = tokens[i].substr(5);
            iface["mac"] = TrimPunct(tokens[i + 1]);
        }
        else if (tokens[i] == "brd")
        {
            iface["broadcast"] = TrimPunct(tokens[i + 1]);
        }
        else if (tokens[i] == "promiscuity" || tokens[i] == "link-netnsid")
        {
            iface[tokens[i]] = TrimPunct(tokens[i + 1]);
        }
    }
}

// `inet 10.10.131.1/24 scope global eth1.131`
    // `inet6 ::1/128 scope host noprefixroute`   <- 인터페이스명 없음
    Json ParseAddressLine(const std::vector<std::string>& tokens)
    {
        Json address = Json::object();
        if (tokens.size() < 2)
        {
            return address;
        }

        address["family"] = tokens[0];  // inet / inet6
        std::string bare;
        int prefix_len = -1;
        if (SplitCidr(TrimPunct(tokens[1]), bare, prefix_len))
        {
            address["address"] = bare;
            if (prefix_len >= 0)
            {
                address["prefix_len"] = prefix_len;
            }
        }

        // scope 값 뒤에 오는 토큰이 인터페이스명이다.
        // scope 뒤에 바로 수명/속성 토큰이 오면 인터페이스명이 없는 것이다.
        static const char* kNonIface[] = {
            "global", "host", "link", "forever", "noprefixroute",
            "dynamic", "secondary", "temporary", "mngtmpaddr", "nodad",
            "metric", "brd", "proto", "deprecated"};

        for (std::size_t i = 2; i + 1 < tokens.size(); ++i)
        {
            if (tokens[i] == "scope")
            {
                address["scope"] = TrimPunct(tokens[i + 1]);
                // scope 값 다음 토큰이 인터페이스명일 수 있다.
                if (i + 2 < tokens.size())
                {
                    const std::string candidate = TrimPunct(tokens[i + 2]);
                    bool is_attr = false;
                    for (const char* skip : kNonIface)
                    {
                        if (candidate == skip)
                        {
                            is_attr = true;
                            break;
                        }
                    }
                    if (!is_attr && !candidate.empty() && !IsNumber(candidate) &&
                        !LooksLikeIp(candidate))
                    {
                        address["interface"] = candidate;
                    }
                }
            }
            else if (tokens[i] == "peer")
            {
                address["peer"] = TrimPunct(tokens[i + 1]);
            }
        }
        return address;
    }

// `valid_lft forever preferred_lft forever` -> 가장 최근 주소에 수명 정보를 채운다
void ApplyLifetimeLine(const std::vector<std::string>& tokens, Json& iface)
{
    if (!iface.contains("addresses") || !iface["addresses"].is_array() ||
        iface["addresses"].empty())
    {
        return;
    }
    Json& last = iface["addresses"].back();
    for (std::size_t i = 0; i + 1 < tokens.size(); ++i)
    {
        if (tokens[i] == "valid_lft")
        {
            last["valid_lft"] = TrimPunct(tokens[i + 1]);
        }
        else if (tokens[i] == "preferred_lft")
        {
            last["preferred_lft"] = TrimPunct(tokens[i + 1]);
        }
    }
}

class NicVisitor : public IpAddrBaseVisitor
{
public:
    ParseSession<IpAddrLexer, IpAddrParser>* session{nullptr};
    Json interfaces = Json::array();
    Json brief = Json::array();
    Json routes = Json::array();
    Json* current{nullptr};

    std::any visitItem(IpAddrParser::ItemContext* ctx) override
    {
        if (ctx->ifaceHeader() != nullptr)
        {
            Json iface = ParseIfaceHeaderTokens(session->TokensOf(ctx->ifaceHeader()));
            iface["addresses"] = Json::array();
            interfaces.push_back(std::move(iface));
            current = &interfaces.back();
            return {};
        }

        if (ctx->ifaceAttr() != nullptr)
        {
            if (current == nullptr)
            {
                return {};
            }
            const std::vector<std::string> tokens = session->TokensOf(ctx->ifaceAttr());
            if (tokens.empty())
            {
                return {};
            }
            if (tokens[0].rfind("link/", 0) == 0)
            {
                ApplyLinkLine(tokens, *current);
            }
            else if (tokens[0] == "inet" || tokens[0] == "inet6")
            {
                (*current)["addresses"].push_back(ParseAddressLine(tokens));
            }
            else if (tokens[0] == "valid_lft" || tokens[0] == "preferred_lft")
            {
                ApplyLifetimeLine(tokens, *current);
            }
            return {};
        }

        if (ctx->briefEntry() != nullptr)
        {
            const std::vector<std::string> tokens = session->TokensOf(ctx->briefEntry());
            Json entry = Json::object();
            if (!tokens.empty())
            {
                entry["name"] = tokens[0];
            }
            if (tokens.size() > 1)
            {
                entry["state"] = tokens[1];
            }

            Json addresses = Json::array();
            std::string mac;
            for (std::size_t i = 2; i < tokens.size(); ++i)
            {
                const std::string token = TrimPunct(tokens[i]);
                if (token.find('/') != std::string::npos)
                {
                    addresses.push_back(token);
                }
                else if (LooksLikeMac(token))
                {
                    mac = token;
                }
                else if (addresses.empty())
                {
                    addresses.push_back(token);
                }
            }
            if (!addresses.empty())
            {
                entry["addresses"] = addresses;
            }
            if (!mac.empty())
            {
                entry["mac"] = mac;
            }
            brief.push_back(std::move(entry));
            return {};
        }

        if (ctx->routeEntry() != nullptr)
        {
            const std::vector<std::string> tokens = session->TokensOf(ctx->routeEntry());
            if (tokens.empty())
            {
                return {};
            }

            Json route = Json::object();
            route["is_default"] = (tokens[0] == "default");
            route["destination"] = (tokens[0] == "default") ? "0.0.0.0/0" : tokens[0];

            for (std::size_t i = 1; i < tokens.size(); ++i)
            {
                const std::string token = tokens[i];
                if (token == "via" && i + 1 < tokens.size())
                {
                    route["via"] = TrimPunct(tokens[++i]);
                }
                else if (token == "dev" && i + 1 < tokens.size())
                {
                    route["interface_name"] = TrimPunct(tokens[++i]);
                }
                else if (token == "proto" && i + 1 < tokens.size())
                {
                    route["protocol"] = TrimPunct(tokens[++i]);
                }
                else if (token == "metric" && i + 1 < tokens.size())
                {
                    route["metric"] = TrimPunct(tokens[++i]);
                }
                else if (token == "src" && i + 1 < tokens.size())
                {
                    route["pref_src"] = TrimPunct(tokens[++i]);
                }
                else if (token == "scope" && i + 1 < tokens.size())
                {
                    route["scope"] = TrimPunct(tokens[++i]);
                }
                else if (token == "table" && i + 1 < tokens.size())
                {
                    route["table"] = TrimPunct(tokens[++i]);
                }
                else if (token == "linkdown")
                {
                    route["linkdown"] = true;
                }
                else
                {
                    if (!route.contains("extras"))
                    {
                        route["extras"] = Json::array();
                    }
                    route["extras"].push_back(TrimPunct(token));
                }
            }
            routes.push_back(std::move(route));
            return {};
        }

        return {};
    }
};

// ============================================================================
// FrrRouter — 라우팅 테이블 / 인터페이스
// ============================================================================

// FRR/Cisco 라우트 코드를 프로토콜 이름으로 정규화한다.
std::string NormalizeRouteCode(const std::string& code)
{
    if (code.empty())
    {
        return "unknown";
    }
    switch (code[0])
    {
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
    case 'D':
        return "eigrp";
    case 'N':
    case 'H':
    case 'G':
        return "nhrp";
    case 'A':
        return "babel";
    case 'L':
        return "local";
    case 'M':
        return "mobile";
    case 'P':
        return "periodic";
    case 'U':
        return "per-user";
    case 'T':
        return "table";
    default:
        return std::string(1, static_cast<char>(
                                  std::tolower(static_cast<unsigned char>(code[0]))));
    }
}

// 라우트 코드 토큰인지 (알파벳/*/</>/& 로만 구성)
bool IsRouteCodeToken(const std::string& token)
{
    if (token.empty())
    {
        return false;
    }
    return std::all_of(token.begin(), token.end(), [](unsigned char ch) {
        return std::isalpha(ch) != 0 || ch == '*' || ch == '<' || ch == '>' || ch == '&';
    });
}

// 목적지(CIDR)인지
bool IsDestinationToken(const std::string& token)
{
    return token.find('/') != std::string::npos &&
           token.find_first_of("0123456789") != std::string::npos;
}

class RouteVisitor : public FrrRouterBaseVisitor
{
public:
    ParseSession<FrrRouterLexer, FrrRouterParser>* session{nullptr};
    Json routes = Json::array();
    Json protocols_seen = Json::array();
    std::string gateway_of_last_resort;

    std::any visitRouteItem(FrrRouterParser::RouteItemContext* ctx) override
    {
        // `10.0.0.0/8 is variably subnetted, 2 subnets, 2 masks` : 라우트가 아니라 요약 줄
        if (ctx->subnetSummary() != nullptr)
        {
            return {};
        }

        if (ctx->routeLine() != nullptr)
        {
            ParseRouteLine(session->TokensOf(ctx->routeLine()));
            return {};
        }

        // `Gateway of last resort is 192.168.122.1 to network 0.0.0.0`
        if (ctx->genericLine() != nullptr)
        {
            const std::vector<std::string> tokens = session->TokensOf(ctx->genericLine());
            for (std::size_t i = 0; i + 4 < tokens.size(); ++i)
            {
                if (tokens[i] == "Gateway" && tokens[i + 1] == "of" &&
                    tokens[i + 2] == "last" && tokens[i + 3] == "resort")
                {
                    for (std::size_t j = i + 4; j + 1 < tokens.size(); ++j)
                    {
                        if (tokens[j] == "is")
                        {
                            gateway_of_last_resort = TrimPunct(tokens[j + 1]);
                            break;
                        }
                    }
                    break;
                }
            }
        }
        return {};
    }

private:
    void ParseRouteLine(const std::vector<std::string>& tokens)
    {
        if (tokens.size() < 2)
        {
            return;
        }

        Json route = Json::object();

        // 1) 라우트 코드: `O>*`, `C`, `S*`, `O` 등 선두 기호 묶음
        std::size_t i = 0;
        std::string code;
        while (i < tokens.size() && IsRouteCodeToken(tokens[i]) &&
               !IsDestinationToken(tokens[i]))
        {
            // 목적지(CIDR)가 나오기 전까지를 코드로 본다.
            code += tokens[i];
            ++i;
            if (i < tokens.size() && IsDestinationToken(tokens[i]))
            {
                break;
            }
        }

        if (i >= tokens.size())
        {
            return;
        }

        route["protocol"] = NormalizeRouteCode(code);
        route["selected"] = code.find('*') != std::string::npos;
        route["fib"] = code.find('>') != std::string::npos;
        route["backup"] = code.find('&') != std::string::npos;
        route["raw_code"] = code;

        // 2) 목적지
        const std::string destination = TrimPunct(tokens[i]);
        route["prefix"] = destination;
        ++i;

        // 3) metric: `[110/200]`
        for (std::size_t j = i; j < tokens.size(); ++j)
        {
            if (!tokens[j].empty() && tokens[j].front() == '[')
            {
                std::string metric = tokens[j];
                if (!metric.empty() && metric.back() == ']')
                {
                    metric = metric.substr(1, metric.size() - 2);
                }
                else
                {
                    metric = metric.substr(1);
                }
                route["metric"] = metric;
                break;
            }
        }

        // 4) directly connected / via
        bool connected = false;
        for (std::size_t j = i; j + 2 < tokens.size(); ++j)
        {
            if (tokens[j] == "is" && tokens[j + 1] == "directly" &&
                TrimPunct(tokens[j + 2]).rfind("connected", 0) == 0)
            {
                connected = true;
                for (std::size_t k = j + 3; k < tokens.size(); ++k)
                {
                    const std::string token = TrimPunct(tokens[k]);
                    if (token.empty() || token == "weight" || IsNumber(token))
                    {
                        continue;
                    }
                    if (token.find(':') != std::string::npos)
                    {
                        route["uptime"] = token;
                        continue;
                    }
                    if (!route.contains("interface_name") &&
                        token.find('.') == std::string::npos)
                    {
                        route["interface_name"] = token;
                    }
                }
                break;
            }
        }
        route["connected"] = connected;

        if (connected)
        {
            route["next_hop"] = "directly connected";
        }
        else
        {
            for (std::size_t j = i; j + 1 < tokens.size(); ++j)
            {
                if (tokens[j] == "via")
                {
                    const std::string next_hop = TrimPunct(tokens[j + 1]);
                    route["next_hop"] = next_hop;
                    route["via"] = next_hop;
                    // via 다음은 `eth0,` 형태의 인터페이스
                    if (j + 2 < tokens.size())
                    {
                        const std::string iface = TrimPunct(tokens[j + 2]);
                        if (!iface.empty() && !IsNumber(iface) &&
                            iface.find('.') == std::string::npos &&
                            iface.find(':') == std::string::npos)
                        {
                            route["interface_name"] = iface;
                        }
                    }
                    break;
                }
            }
        }

        const std::string protocol = route["protocol"].get<std::string>();
        if (!Contains(protocols_seen, protocol))
        {
            protocols_seen.push_back(protocol);
        }

        routes.push_back(std::move(route));
    }
};

// `eth0  up  up` / `GigabitEthernet1  192.168.122.254  YES  NVRAM  up  up`
Json ParseBriefEntryTokens(const std::vector<std::string>& tokens)
{
    Json entry = Json::object();
    if (tokens.empty())
    {
        return entry;
    }

    entry["name"] = tokens[0];
    std::size_t i = 1;

    if (i < tokens.size())
    {
        const std::string value = TrimPunct(tokens[i]);
        if (value == "unassigned")
        {
            entry["ip_address"] = "";
            entry["unassigned"] = true;
            ++i;
        }
        else if (value.find('.') != std::string::npos ||
                 value.find(':') != std::string::npos)
        {
            std::string bare;
            int prefix_len = -1;
            if (SplitCidr(value, bare, prefix_len))
            {
                entry["ip_address"] = bare;
                if (prefix_len >= 0)
                {
                    entry["prefix_len"] = prefix_len;
                }
            }
            ++i;
        }
    }

    Json extras = Json::array();
    for (; i < tokens.size(); ++i)
    {
        const std::string token = TrimPunct(tokens[i]);
        if (token.empty())
        {
            continue;
        }

        if (token == "YES" || token == "NO" || token == "NVRAM" ||
            token == "unset" || token == "manual")
        {
            entry["method"] = token;
        }
        else if (token == "up" || token == "down" ||
                 token == "administratively" || token == "deleted")
        {
            if (!entry.contains("status"))
            {
                entry["status"] = token;
            }
            else if (!entry.contains("protocol"))
            {
                entry["protocol"] = token;
            }
            else
            {
                extras.push_back(token);
            }
        }
        else
        {
            extras.push_back(token);
        }
    }
    if (!extras.empty())
    {
        entry["extras"] = extras;
    }
    return entry;
}

class InterfaceVisitor : public FrrRouterBaseVisitor
{
public:
    ParseSession<FrrRouterLexer, FrrRouterParser>* session{nullptr};
    Json interfaces = Json::array();
    Json details = Json::object();
    bool in_detail{false};
    std::string detail_name;

    std::any visitIfaceItem(FrrRouterParser::IfaceItemContext* ctx) override
    {
        if (ctx->briefEntry() != nullptr)
        {
            const std::vector<std::string> tokens = session->TokensOf(ctx->briefEntry());
            // 컬럼 헤더/구분선을 인터페이스로 오인하지 않는다.
            //   `Interface   IP-Address   OK? Method Status Protocol`
            //   `            Address`            <- Arista 는 첫 줄이 비어 있다
            //   `--------- -------------------- ------------ ...`
            const Json entry = ParseBriefEntryTokens(tokens);
            const std::string name = entry.value("name", std::string{});
            if (name == "Interface" || name == "Address" || name == "Name" ||
                name.rfind("---", 0) == 0 || name == "unassigned")
            {
                return {};
            }
            interfaces.push_back(entry);
        }
        return {};
    }

    std::any visitDetailItem(FrrRouterParser::DetailItemContext* ctx) override
    {
        if (ctx->ifaceHeader() != nullptr)
        {
            // `eth0 is up, line protocol is up`
            const std::vector<std::string> tokens = session->TokensOf(ctx->ifaceHeader());
            if (tokens.size() > 1)
            {
                detail_name = TrimPunct(tokens[1]);
                in_detail = true;
                details[detail_name] = Json::object();
                for (std::size_t i = 0; i + 1 < tokens.size(); ++i)
                {
                    if (tokens[i] == "is")
                    {
                        details[detail_name]["state"] = TrimPunct(tokens[i + 1]);
                        break;
                    }
                }
            }
            return {};
        }

        if (ctx->detailAttr() != nullptr && in_detail)
        {
            const std::vector<std::string> tokens = session->TokensOf(ctx->detailAttr());
            if (tokens.size() < 2)
            {
                return {};
            }

            Json& attribute = details[detail_name];
            const std::string key = TrimPunct(tokens[0]);

            std::string value;
            for (std::size_t i = 1; i < tokens.size(); ++i)
            {
                if (!value.empty())
                {
                    value.push_back(' ');
                }
                value += tokens[i];
            }

            if (key == "HWaddr" || key == "Hardware")
            {
                attribute["mac"] = TrimPunct(tokens[1]);
            }
            else if (key == "inet" || key == "Internet")
            {
                std::string bare;
                int prefix_len = -1;
                if (SplitCidr(TrimPunct(tokens[1]), bare, prefix_len))
                {
                    attribute["ip_address"] = bare;
                    if (prefix_len >= 0)
                    {
                        attribute["prefix_len"] = prefix_len;
                    }
                }
            }
            else if (key == "MTU" || key == "mtu")
            {
                attribute["mtu"] = TrimPunct(tokens[1]);
            }
            else
            {
                attribute[key] = Trim(value);
            }
        }
        return {};
    }
};

// ============================================================================
// OvsTopology — ovs-vsctl show / list port / dump-flows
// ============================================================================

// `tag: 141` / `trunks: [141]` / `type: internal` / `ovs_version: "2.17.0"`
// attrLine 은 ATTRWORD COLON elem* 이므로 원문에서 key/value 를 직접 쪼갠다.
std::pair<std::string, std::string> SplitAttr(const std::string& line)
{
    const std::size_t colon = line.find(':');
    if (colon == std::string::npos)
    {
        return {Trim(line), {}};
    }
    const std::string key = Trim(line.substr(0, colon));
    const std::string value = Trim(line.substr(colon + 1));
    return {key, value};
}

// `"br0"` -> br0
std::string ParseQuotedName(const std::vector<std::string>& tokens, std::size_t index)
{
    if (index >= tokens.size())
    {
        return {};
    }
    std::string name = tokens[index];
    while (!name.empty() && (name.front() == '"' || name.front() == ':'))
    {
        name.erase(name.begin());
    }
    while (!name.empty() && (name.back() == '"' || name.back() == ':'))
    {
        name.pop_back();
    }
    return name;
}

class OvsVisitor : public OvsTopologyBaseVisitor
{
public:
    ParseSession<OvsTopologyLexer, OvsTopologyParser>* session{nullptr};

    Json bridges = Json::array();
    Json ports = Json::array();
    Json flows = Json::array();

    // 깊이 기반 현재 위치 (들여쓰기로 계층을 복원한다)
    Json* current_bridge{nullptr};
    Json* current_port{nullptr};
    Json* current_iface{nullptr};
    std::size_t bridge_indent{0};
    std::size_t port_indent{0};
    std::size_t iface_indent{0};

    std::any visitShowItem(OvsTopologyParser::ShowItemContext* ctx) override
    {
        const std::size_t indent = session->IndentOf(ctx);

        if (ctx->bridgeLine() != nullptr)
        {
            const std::vector<std::string> tokens = session->TokensOf(ctx->bridgeLine());
            Json bridge = Json::object();
            bridge["name"] = ParseQuotedName(tokens, 1);
            bridge["ports"] = Json::array();
            bridges.push_back(std::move(bridge));

            current_bridge = &bridges.back();
            current_port = nullptr;
            current_iface = nullptr;
            bridge_indent = indent;
            port_indent = 0;
            iface_indent = 0;
            return {};
        }

        if (ctx->portLine() != nullptr)
        {
            if (current_bridge == nullptr)
            {
                return {};
            }
            const std::vector<std::string> tokens = session->TokensOf(ctx->portLine());
            Json port = Json::object();
            port["name"] = ParseQuotedName(tokens, 1);
            port["trunks"] = Json::array();
            port["interfaces"] = Json::array();
            port["tag"] = Json::array();

            (*current_bridge)["ports"].push_back(std::move(port));
            current_port = &(*current_bridge)["ports"].back();
            current_iface = nullptr;
            port_indent = indent;
            iface_indent = 0;
            return {};
        }

        if (ctx->ifaceLine() != nullptr)
        {
            if (current_port == nullptr)
            {
                return {};
            }
            const std::vector<std::string> tokens = session->TokensOf(ctx->ifaceLine());
            Json iface = Json::object();
            iface["name"] = ParseQuotedName(tokens, 1);
            (*current_port)["interfaces"].push_back(std::move(iface));
            current_iface = &(*current_port)["interfaces"].back();
            iface_indent = indent;
            return {};
        }

        if (ctx->attrLine() != nullptr)
        {
            const std::string line = session->LineOf(ctx->attrLine());
            const auto [key, value] = SplitAttr(line);

            // Interface 속성(type: internal)이면 인터페이스에, 아니면 포트에 기록.
            Json* target = nullptr;
            if (current_iface != nullptr && indent >= iface_indent)
            {
                target = current_iface;
            }
            else if (current_port != nullptr && indent >= port_indent)
            {
                target = current_port;
            }
            else if (current_bridge != nullptr && indent >= bridge_indent)
            {
                target = current_bridge;
            }

            if (target == nullptr)
            {
                return {};
            }

            if (key == "tag")
            {
                // `tag: 141` 또는 `tag: []`
                const std::vector<int> vlans = ParseVlanNumbers(value);
                if (!vlans.empty())
                {
                    (*target)["tag"] = vlans.front();
                }
            }
            else if (key == "trunks")
            {
                (*target)["trunks"] = VlanJson(ParseVlanNumbers(value));
            }
            else if (key == "vlan_mode")
            {
                (*target)["vlan_mode"] = Unquote(value);
            }
            else if (key == "type")
            {
                (*target)["type"] = Unquote(value);
            }
            else if (key == "name")
            {
                (*target)["name"] = Unquote(value);
            }
            else if (key == "fail_mode")
            {
                (*target)["fail_mode"] = Unquote(value);
            }
            else if (key == "stp_enable")
            {
                (*target)["stp_enable"] = (Unquote(value) == "true");
            }
            else if (!key.empty() && !value.empty())
            {
                (*target)[key] = Unquote(value);
            }
        }
        return {};
    }

    // `ovs-vsctl list port` 레코드 -> 포트 객체 (레코드 경계 마다 새 객체)
    //
    //  실제 ovs-vsctl 은 두 가지 구분 방식을 쓴다.
    //    (A) 속성 레코드를 `--` 로 구분 (구버전/일부 옵션)
    //    (B) 레코드 사이를 **빈 줄** 로 구분 (배포판 기본, 실측 확인)
    //  (B) 를 처리하지 않으면 모든 레코드가 하나의 객체로 합쳐져
    //  포트가 1개만 수집된다. 두 방식 모두에서 새 레코드를 시작한다.
    std::any visitListItem(OvsTopologyParser::ListItemContext* ctx) override
    {
        // `--` 또는 빈 줄 = 레코드 구분자 → 다음 속성에서 새 객체를 시작한다.
        if (ctx->recordSep() != nullptr || ctx->blank() != nullptr)
        {
            current_port_list = nullptr;
            return {};
        }
        if (ctx->listRecord() == nullptr)
        {
            return {};
        }

        const std::string line = session->LineOf(ctx->listRecord());
        const auto [key, value] = SplitAttr(line);
        if (key.empty())
        {
            return {};
        }

        // 구분자가 없더라도 `_uuid` 가 다시 나오면 새 레코드다.
        // (마지막 레코드 뒤에 빈 줄이 없는 출력 형태 방어)
        if (key == "_uuid" && current_port_list != nullptr)
        {
            current_port_list = nullptr;
        }

        if (ports.empty() || (current_port_list == nullptr))
        {
            ports.push_back(Json::object());
            current_port_list = &ports.back();
        }

        if (key == "name")
        {
            (*current_port_list)["name"] = Unquote(value);
        }
        else if (key == "tag")
        {
            const std::vector<int> vlans = ParseVlanNumbers(value);
            if (!vlans.empty())
            {
                (*current_port_list)["tag"] = vlans.front();
            }
        }
        else if (key == "trunks")
        {
            (*current_port_list)["trunks"] = VlanJson(ParseVlanNumbers(value));
        }
        else if (key == "vlan_mode")
        {
            (*current_port_list)["vlan_mode"] = Unquote(value);
        }
        else if (key == "_uuid")
        {
            (*current_port_list)["uuid"] = Unquote(value);
        }
        else if (!value.empty())
        {
            (*current_port_list)[key] = Unquote(value);
        }
        return {};
    }

    // `ovs-vsctl list port` 는 `--` 로 레코드를 구분한다.
    // flowDocument 와 listDocument 를 함께 처리하기 위한 별도 진입.
    Json* current_port_list{nullptr};

    std::any visitFlowItem(OvsTopologyParser::FlowItemContext* ctx) override
    {
        if (ctx->flowLine() == nullptr)
        {
            return {};
        }

        const std::string line = Trim(session->LineOf(ctx->flowLine()));
        Json flow = Json::object();
        flow["raw"] = line;

        // `cookie=0x0, duration=1.2s, table=0, n_packets=0, actions=drop`
        std::stringstream stream(line);
        std::string piece;
        while (std::getline(stream, piece, ','))
        {
            piece = Trim(piece);
            const std::size_t equals = piece.find('=');
            if (equals == std::string::npos)
            {
                if (!piece.empty())
                {
                    flow["match"] = piece;
                }
                continue;
            }

            const std::string key = piece.substr(0, equals);
            const std::string value = piece.substr(equals + 1);
            if (key == "actions")
            {
                flow["actions"] = value;
            }
            else if (key == "n_packets")
            {
                flow["packets"] = value;
            }
            else if (key == "n_bytes")
            {
                flow["bytes"] = value;
            }
            else if (key == "table")
            {
                flow["table"] = value;
            }
            else if (key == "priority")
            {
                flow["priority"] = value;
            }
            else
            {
                if (!flow.contains("match_pairs"))
                {
                    flow["match_pairs"] = Json::object();
                }
                flow["match_pairs"][key] = value;
            }
        }

        // 목적지 주소 기반 매칭을 꺼내기 쉽게 정리
        if (flow.contains("match_pairs"))
        {
            for (const char* key : {"nw_src", "nw_dst", "ip", "in_port", "dl_type"})
            {
                if (flow["match_pairs"].contains(key))
                {
                    flow[key] = flow["match_pairs"][key];
                }
            }
        }

        flows.push_back(std::move(flow));
        return {};
    }
};

// ============================================================================
// NftablesRule — nft list ruleset
// ============================================================================

// 체인 속성 줄:
//   `type filter hook forward priority filter; policy accept;`
//   `type nat hook postrouting priority srcnat; policy accept;`
//   `type filter hook input priority 0; policy drop;`
// 한 줄에 여러 속성이 세미콜론으로 이어지고, 각 속성이 `key value` 쌍이므로
// 토큰을 훑으며 키워드를 만날 때마다 다음 토큰을 값으로 취한다.
void ApplyChainAttr(const std::string& line, Json& chain)
{
    const std::vector<std::string> tokens = SplitTokens(line);
    for (std::size_t i = 0; i + 1 < tokens.size(); ++i)
    {
        const std::string key = TrimPunct(tokens[i]);
        const std::string value = Unquote(TrimPunct(tokens[i + 1]));

        if (key == "type")
        {
            chain["type"] = value;
        }
        else if (key == "hook")
        {
            chain["hook"] = value;
        }
        else if (key == "priority")
        {
            chain["priority"] = value;
        }
        else if (key == "policy")
        {
            chain["policy"] = value;
        }
        else if (key == "device")
        {
            chain["device"] = value;
        }
        else if (key == "comment")
        {
            chain["comment"] = value;
        }
    }
}

// 규칙 조각 목록에서 (match_pairs, action, modifiers) 를 뽑는다.
//   `ip saddr 1.2.3.0/24 ip daddr 5.6.7.0/24 counter log prefix "NFT_DROP: " drop`
//   `oifname "eth0" masquerade`
//
// nft 표기는 `ip saddr <값>` 처럼 두 토큰이 하나의 키를 이룬다.
//   접두어(ip/ip6/tcp/udp/ct/meta/icmp) + 하위키(saddr/daddr/dport/...) 조합을
//   "ip saddr" 한 키로 합쳐야 JSON 에서 소스/목적지를 바로 꺼낼 수 있다.
void ApplyRulePieces(const std::vector<std::string>& pieces, Json& rule)
{
    // 두 토큰이 하나의 키를 이루는 접두어
    static const char* kPrefixes[] = {
        "ip", "ip6", "tcp", "udp", "ct", "meta", "icmp", "icmpv6"};

    // 접두어 뒤에 붙는 하위 키
    static const char* kSubKeys[] = {
        "saddr", "daddr", "state", "dport", "sport", "protocol",
        "type", "code", "l4proto", "mark", "iifname", "oifname", "iif", "oif"};

    // 단독으로 키가 되는 토큰
    static const char* kPlainKeys[] = {
        "iifname", "oifname", "iif", "oif", "saddr", "daddr", "dport",
        "sport", "protocol", "mark", "handle", "comment"};

    static const char* kActions[] = {
        "accept", "drop", "reject", "return", "jump", "goto",
        "masquerade", "redirect", "dnat", "snat", "set"};

    auto Matches = [](const std::string& token, const char* const* table, std::size_t count) {
        for (std::size_t i = 0; i < count; ++i)
        {
            if (token == table[i])
            {
                return true;
            }
        }
        return false;
    };

    auto AddMatch = [&rule](const std::string& key, const std::string& value) {
        if (!rule.contains("match_pairs"))
        {
            rule["match_pairs"] = Json::object();
        }
        rule["match_pairs"][key] = value;

        std::string text = rule.value("match", std::string{});
        if (!text.empty())
        {
            text.push_back(' ');
        }
        text += key + " " + value;
        rule["match"] = text;
    };

    Json modifiers = Json::array();
    std::string action;
    bool counter_seen = false;

    for (std::size_t i = 0; i < pieces.size(); ++i)
    {
        const std::string token = pieces[i];

        // 1) action
        if (Matches(token, kActions, std::size(kActions)))
        {
            action = token;
            // jump/goto 는 대상 체인명을 함께 기록
            if ((token == "jump" || token == "goto") && i + 1 < pieces.size())
            {
                rule["jump_target"] = Unquote(pieces[++i]);
            }
            continue;
        }

        // 2) 수식어
        if (token == "counter")
        {
            counter_seen = true;
            modifiers.push_back(token);
            continue;
        }
        if (token == "log")
        {
            modifiers.push_back(token);
            if (i + 2 < pieces.size() && pieces[i + 1] == "prefix")
            {
                // `prefix "NFT_DROP: "` -> 조각이 공백에서 쪼개졌을 수 있으므로
                // 닫는 따옴표가 나올 때까지 값을 이어 붙인다.
                std::string prefix_value = pieces[i + 2];
                std::size_t consumed = i + 2;
                while (!prefix_value.empty() && prefix_value.front() == '"' &&
                       prefix_value.back() != '"' && consumed + 1 < pieces.size())
                {
                    prefix_value += " " + pieces[++consumed];
                }
                rule["log_prefix"] = Unquote(prefix_value);
                i = consumed;
            }
            continue;
        }
        if ((token == "packets" || token == "bytes") && i + 1 < pieces.size() &&
            IsNumber(pieces[i + 1]))
        {
            rule[token] = std::stoll(pieces[i + 1]);
            ++i;
            continue;
        }

        // 3) 접두어 + 하위키 (ip saddr / tcp dport / ct state ...)
        if (Matches(token, kPrefixes, std::size(kPrefixes)) && i + 1 < pieces.size() &&
            Matches(pieces[i + 1], kSubKeys, std::size(kSubKeys)) &&
            i + 2 < pieces.size())
        {
            AddMatch(token + " " + pieces[i + 1], Unquote(pieces[i + 2]));
            i += 2;
            continue;
        }

        // 4) 단독 키
        if (Matches(token, kPlainKeys, std::size(kPlainKeys)) && i + 1 < pieces.size())
        {
            AddMatch(token, Unquote(pieces[i + 1]));
            ++i;
            continue;
        }

        // 5) 해석하지 못한 조각은 버리지 않고 남긴다(정보 보존).
        modifiers.push_back(token);
    }

    if (!action.empty())
    {
        rule["action"] = action;
    }
    if (!modifiers.empty())
    {
        rule["modifiers"] = modifiers;
    }
    rule["counter"] = counter_seen;
}

class NftablesVisitor : public NftablesRuleBaseVisitor
{
public:
    ParseSession<NftablesRuleLexer, NftablesRuleParser>* session{nullptr};
    Json tables = Json::array();
    Json* current_table{nullptr};
    Json* current_chain{nullptr};

    std::any visitTableBlock(NftablesRuleParser::TableBlockContext* ctx) override
    {
        Json table = Json::object();
        table["family"] = ctx->familyBlock() == nullptr
                              ? std::string{}
                              : ctx->familyBlock()->getText();
        table["name"] = ctx->tableName() == nullptr
                            ? std::string{}
                            : ctx->tableName()->getText();
        table["chains"] = Json::array();
        tables.push_back(std::move(table));

        current_table = &tables.back();
        current_chain = nullptr;

        for (auto* body : ctx->bodyBlock())
        {
            if (body->chainBlock() != nullptr)
            {
                visitChainBlock(body->chainBlock());
            }
        }

        current_table = nullptr;
        current_chain = nullptr;
        return {};
    }

    std::any visitChainDocument(NftablesRuleParser::ChainDocumentContext* ctx) override
    {
        // `nft -a list chain <family> <table> <chain>` 는 테이블 블록 없이 온다.
        Json table = Json::object();
        table["family"] = "unknown";
        table["name"] = "unknown";
        table["chains"] = Json::array();
        tables.push_back(std::move(table));
        current_table = &tables.back();
        current_chain = nullptr;

        for (auto* item : ctx->chainItem())
        {
            if (item->chainAttr() != nullptr && current_chain != nullptr)
            {
                ApplyChainAttr(session->LineOf(item->chainAttr()), *current_chain);
            }
            else if (item->ruleLine() != nullptr && current_chain != nullptr)
            {
                ApplyRuleLine(item->ruleLine(), *current_chain);
            }
        }
        return {};
    }

    std::any visitChainBlock(NftablesRuleParser::ChainBlockContext* ctx) override
    {
        if (current_table == nullptr)
        {
            return {};
        }

        Json chain = Json::object();
        chain["name"] = ctx->chainName() == nullptr
                            ? std::string{}
                            : ctx->chainName()->getText();
        chain["rules"] = Json::array();
        (*current_table)["chains"].push_back(std::move(chain));
        current_chain = &(*current_table)["chains"].back();

        for (auto* body : ctx->chainBody())
        {
            if (body->chainAttr() != nullptr)
            {
                ApplyChainAttr(session->LineOf(body->chainAttr()), *current_chain);
            }
            else if (body->ruleLine() != nullptr)
            {
                ApplyRuleLine(body->ruleLine(), *current_chain);
            }
        }
        return {};
    }

private:
    void ApplyRuleLine(NftablesRuleParser::RuleLineContext* ctx, Json& chain)
    {
        Json rule = Json::object();
        // 원문 라인을 그대로 보존한다(핸들/주석 포함).
        const std::string raw = Trim(session->LineOf(ctx));
        rule["raw"] = raw;

        // `# handle 5` 같은 주석에서 handle 을 뽑는다(있으면 삭제에 필요).
        const std::size_t handle_pos = raw.rfind("# handle ");
        if (handle_pos != std::string::npos)
        {
            const std::string handle = Trim(raw.substr(handle_pos + 9));
            if (IsNumber(handle))
            {
                rule["handle"] = std::stoll(handle);
            }
        }

        std::vector<std::string> pieces;
        for (const auto& token : SplitTokens(raw))
        {
            // `{` `}` `#` 이후는 규칙 본문이 아니다.
            if (token == "{" || token == "}" || token == "#")
            {
                break;
            }
            pieces.push_back(TrimPunct(token));
        }

        ApplyRulePieces(pieces, rule);
        chain["rules"].push_back(std::move(rule));
    }
};

// ============================================================================
// SwitchTopology — show vlan brief / show ip interface brief / switchport
// ============================================================================

class SwitchVisitor : public SwitchTopologyBaseVisitor
{
public:
    ParseSession<SwitchTopologyLexer, SwitchTopologyParser>* session{nullptr};
    Json vlans = Json::array();
    Json interfaces = Json::array();
    Json ports = Json::array();
    Json* current_port{nullptr};

    std::any visitVlanItem(SwitchTopologyParser::VlanItemContext* ctx) override
    {
        if (ctx->vlanEntry() != nullptr)
        {
            const std::vector<std::string> tokens = session->TokensOf(ctx->vlanEntry());
            Json vlan = Json::object();
            if (tokens.size() > 0 && IsNumber(tokens[0]))
            {
                vlan["vlan_id"] = std::stoi(tokens[0]);
            }
            if (tokens.size() > 1)
            {
                vlan["name"] = tokens[1];
            }
            if (tokens.size() > 2)
            {
                vlan["status"] = TrimPunct(tokens[2]);
            }

            Json port_list = Json::array();
            for (std::size_t i = 3; i < tokens.size(); ++i)
            {
                const std::string port = TrimPunct(tokens[i]);
                if (!port.empty())
                {
                    port_list.push_back(port);
                }
            }
            if (!port_list.empty())
            {
                vlan["ports"] = port_list;
            }
            vlans.push_back(std::move(vlan));
            return {};
        }

        // `show vlan brief` 는 포트 목록이 다음 줄로 이어질 수 있다.
        // 문법상 genericLine 이지만, 직전 VLAN 레코드에 이어 붙인다.
        if (ctx->genericLine() != nullptr && !vlans.empty())
        {
            const std::vector<std::string> tokens = session->TokensOf(ctx->genericLine());
            if (tokens.empty())
            {
                return {};
            }
            // 첫 토큰이 포트명 형태(영문+숫자)일 때만 continuation 으로 본다.
            const std::string& first = tokens[0];
            const bool looks_like_port =
                !first.empty() &&
                std::isalpha(static_cast<unsigned char>(first[0])) != 0 &&
                first.find_first_of("0123456789") != std::string::npos;

            if (!looks_like_port)
            {
                return {};
            }

            Json& vlan = vlans.back();
            if (!vlan.contains("ports"))
            {
                vlan["ports"] = Json::array();
            }
            for (const auto& token : tokens)
            {
                const std::string port = TrimPunct(token);
                if (!port.empty())
                {
                    vlan["ports"].push_back(port);
                }
            }
        }
        return {};
    }

    std::any visitBriefItem(SwitchTopologyParser::BriefItemContext* ctx) override
    {
        if (ctx->briefEntry() != nullptr)
        {
            const std::vector<std::string> tokens = session->TokensOf(ctx->briefEntry());
            // 컬럼 헤더(`Interface IP Address Status Protocol MTU Owner`)를
            // 인터페이스로 오인하지 않는다.
            const Json entry = ParseBriefEntryTokens(tokens);
            const std::string name = entry.value("name", std::string{});
            if (name == "Interface" || name == "Address" || name == "Name")
            {
                return {};
            }
            interfaces.push_back(entry);
        }
        return {};
    }

    std::any visitPortItem(SwitchTopologyParser::PortItemContext* ctx) override
    {
        if (ctx->portEntry() == nullptr)
        {
            return {};
        }

        const std::vector<std::string> tokens = session->TokensOf(ctx->portEntry());

        // ---------------------------------------------------------------
        // 1) `interface Ethernet1` 형태 (IOS 스타일) -> 새 포트 레코드
        // ---------------------------------------------------------------
        if (ctx->portEntry()->INTERFACE() != nullptr)
        {
            Json port = Json::object();
            if (tokens.size() > 1)
            {
                port["name"] = TrimPunct(tokens[1]);
            }
            port["trunk_vlans"] = Json::array();
            ports.push_back(std::move(port));
            current_port = &ports.back();
            return {};
        }

        const std::string line = session->LineOf(ctx->portEntry());
        const auto [key, value] = SplitAttr(line);
        if (key.empty())
        {
            return {};
        }

        // ---------------------------------------------------------------
        // 2) Arista 는 `Name: Et2` 로 포트 블록을 시작한다.
        //    (실제 장비 출력 기준) 이 줄에서 새 포트 레코드를 만든다.
        // ---------------------------------------------------------------
        if (key == "Name")
        {
            Json port = Json::object();
            port["name"] = Unquote(value);
            port["trunk_vlans"] = Json::array();
            ports.push_back(std::move(port));
            current_port = &ports.back();
            return {};
        }

        // 포트 블록 이전의 전역 설정 줄(Default switchport mode 등)은 버린다.
        if (current_port == nullptr)
        {
            return {};
        }

        // 키 표기 차이 흡수:
        //   IOS    : `Access Mode VLAN: 99`  / `Trunking VLANs Enabled: 111,112`
        //   Arista : `Access Mode VLAN: 8 (VLAN8)`
        //            `Trunking VLANs Enabled: ALL`
        //            `Administrative Mode: static access`
        //            `Operational Mode: static access`
        if (key == "Switchport")
        {
            (*current_port)["admin_enabled"] = (Unquote(value) == "Enabled");
        }
        else if (key.find("Administrative Mode") != std::string::npos &&
                 key.find("Native") == std::string::npos)
        {
            const std::string mode = Unquote(value);
            if (mode.find("trunk") != std::string::npos)
            {
                (*current_port)["mode"] = "trunk";
            }
            else if (mode.find("access") != std::string::npos)
            {
                (*current_port)["mode"] = "access";
            }
            else
            {
                (*current_port)["mode"] = mode;
            }
        }
        else if (key == "Operational Mode")
        {
            (*current_port)["operational_mode"] = Unquote(value);
        }
        else if (key.find("Access Mode VLAN") != std::string::npos)
        {
            // `8 (VLAN8)` 에서 괄호 앞 숫자만 VLAN ID 다.
            // (ParseVlanNumbers 를 그대로 쓰면 괄호 안 이름의 숫자까지 이어붙어
            //  `8 (VLAN8)` -> 88 이 되는 버그가 생긴다.)
            const std::size_t paren = value.find('(');
            const std::string id_text = Trim(paren == std::string::npos
                                                 ? value
                                                 : value.substr(0, paren));
            if (IsNumber(id_text))
            {
                (*current_port)["access_vlan"] = std::stoi(id_text);
            }

            // 괄호 안 이름도 보존한다(`8 (VLAN8)` -> access_vlan_name=VLAN8)
            const std::size_t close = value.find(')', paren);
            if (paren != std::string::npos && close != std::string::npos &&
                close > paren + 1)
            {
                (*current_port)["access_vlan_name"] =
                    value.substr(paren + 1, close - paren - 1);
            }
        }
        else if (key.find("Trunking VLANs Enabled") != std::string::npos ||
                 key.find("Trunking VLANs Active") != std::string::npos)
        {
            // Arista 는 `ALL` 로 표기하므로 그대로 문자열로도 남긴다.
            const std::string trunk_text = Unquote(value);
            if (trunk_text == "ALL" || trunk_text.empty())
            {
                (*current_port)["trunk_vlans_all"] = (trunk_text == "ALL");
                (*current_port)["trunk_vlans"] = Json::array();
            }
            else
            {
                (*current_port)["trunk_vlans"] = VlanJson(ParseVlanNumbers(trunk_text));
            }
        }
        else if (key.find("Administrative Trunking Encapsulation") != std::string::npos)
        {
            (*current_port)["encapsulation"] = Unquote(value);
        }
        else if (!value.empty())
        {
            (*current_port)[key] = Unquote(value);
        }
        return {};
    }
};

}  // namespace

// ============================================================================
// ARP / 이웃 테이블 — `ip neigh show`, `show arp`, `show ip arp`
//
//  세 벤더의 출력 형식이 모두 다르다.
//    Linux  : 10.0.9.1 dev ens3 lladdr 0c:2d:07:65:99:f3 REACHABLE
//    Arista : 10.0.9.100  1:35:06  0cae.dcfd.0000  Vlan9, Ethernet3
//    Cisco  : Internet  10.20.0.4  -  0c2d.0765.99f3  ARPA  GigabitEthernet4
//  공통점은 "선두에 IP 주소"라는 것뿐이므로, 첫 토큰을 주소로 잡고
//  나머지는 형태를 보고 해석한다.
// ============================================================================

namespace
{

// Arista/Cisco 형식의 하드웨어 주소(`0cae.21dd.0001`)를 콜론 표기로 바꾼다.
//  0cae.21dd.0001 -> 0c:ae:21:dd:00:01
std::string NormalizeHardwareAddress(const std::string& token)
{
    const std::string value = TrimPunct(token);

    // 이미 콜론 표기(MAC)면 그대로 둔다.
    if (std::count(value.begin(), value.end(), ':') >= 5)
    {
        return value;
    }

    // 점 2개로 3덩어리(4자리씩)면 Cisco/Arista 표기다.
    if (std::count(value.begin(), value.end(), '.') != 2)
    {
        return value;
    }

    // 16진수만 모아 12자리면 2자리씩 콜론으로 구분한다.
    std::string digits;
    for (const char ch : value)
    {
        if (std::isxdigit(static_cast<unsigned char>(ch)) != 0)
        {
            digits.push_back(static_cast<char>(
                std::tolower(static_cast<unsigned char>(ch))));
        }
    }
    if (digits.size() != 12)
    {
        return value;
    }

    std::string normalized;
    for (std::size_t i = 0; i < digits.size(); ++i)
    {
        if (i != 0 && i % 2 == 0)
        {
            normalized.push_back(':');
        }
        normalized.push_back(digits[i]);
    }
    return normalized;
}

// ARP 테이블의 컬럼 헤더/구분선인지 판별한다.
//  Arista : `Address  Age (sec)  Hardware Addr  Interface`
//  Cisco  : `Protocol  Address  Age (min)  Hardware Addr  Type  Interface`
//  Linux  : 헤더 없음
bool IsArpHeaderLine(const std::vector<std::string>& tokens)
{
    static const char* kHeaderWords[] = {
        "Address", "Age", "Hardware", "Interface", "Protocol", "Type",
        "Addr", "Vlan", "MAC", "HWaddr", "(sec)", "(min)"};

    for (const auto& token : tokens)
    {
        const std::string value = TrimPunct(token);
        for (const char* word : kHeaderWords)
        {
            if (value == word)
            {
                return true;
            }
        }
        // `(sec)` `(min)` 처럼 괄호로 시작하는 단위 표기도 헤더의 일부다.
        if (!value.empty() && value.front() == '(')
        {
            return true;
        }
    }
    return false;
}

// `2:31:51`(시:분:초) 형태의 age 인지 판별한다.
bool LooksLikeAge(const std::string& token)
{
    if (std::count(token.begin(), token.end(), ':') != 2)
    {
        return false;
    }
    return std::all_of(token.begin(), token.end(), [](unsigned char ch) {
        return std::isdigit(ch) != 0 || ch == ':';
    });
}

// MAC 주소 형태(콜론 5개 이상 또는 Cisco 점 표기)인지 판별한다.
bool LooksLikeHardwareAddress(const std::string& token)
{
    const std::string value = TrimPunct(token);
    if (std::count(value.begin(), value.end(), ':') >= 5)
    {
        return true;
    }
    return value.size() == 14 &&
           std::count(value.begin(), value.end(), '.') == 2 &&
           std::all_of(value.begin(), value.end(), [](unsigned char ch) {
               return std::isxdigit(static_cast<unsigned char>(ch)) != 0 || ch == '.';
           });
}

// Linux `ip neigh` 의 상태 토큰
bool IsNeighborState(const std::string& token)
{
    static const char* kStates[] = {
        "REACHABLE", "STALE", "DELAY", "PROBE", "FAILED", "INCOMPLETE",
        "NOARP", "PERMANENT", "NONE"};
    for (const char* state : kStates)
    {
        if (token == state)
        {
            return true;
        }
    }
    return false;
}

// Cisco `show ip arp` 의 Type 컬럼
bool IsArpType(const std::string& token)
{
    return token == "ARPA" || token == "SNAP" || token == "PROBE" ||
           token == "STATIC" || token == "Dynamic" || token == "dynamic";
}

class ArpVisitor : public IpAddrBaseVisitor
{
public:
    ParseSession<IpAddrLexer, IpAddrParser>* session{nullptr};
    Json entries = Json::array();

    std::any visitArpEntry(IpAddrParser::ArpEntryContext* ctx) override
    {
        // ArpEntryContext 자체가 한 줄이므로 LineOf(ctx) 를 그대로 쓴다.
        const std::vector<std::string> tokens = session->TokensOf(ctx);
        if (tokens.size() < 2)
        {
            return {};
        }

        // 컬럼 헤더/구분선은 항목이 아니다.
        if (IsArpHeaderLine(tokens))
        {
            return {};
        }

        // 첫 토큰이 IP 가 아니면 (Cisco 의 `Internet` 같은 프로토콜 컬럼)
        // 그 다음 토큰을 주소로 본다.
        std::size_t address_index = 0;
        if (!LooksLikeIp(TrimPunct(tokens[0])))
        {
            static const char* kProtocols[] = {
                "Internet", "I", "ip", "ipv6", "IPv6", "Incomplete"};
            bool is_protocol = false;
            for (const char* protocol : kProtocols)
            {
                if (TrimPunct(tokens[0]) == protocol)
                {
                    is_protocol = true;
                    break;
                }
            }
            if (!is_protocol)
            {
                return {};
            }
            address_index = 1;
            if (tokens.size() < 3)
            {
                return {};
            }
        }

        Json entry = Json::object();
        entry["address"] = TrimPunct(tokens[address_index]);
        entry["family"] =
            (tokens[address_index].find(':') != std::string::npos) ? "inet6" : "inet";

        Json interfaces = Json::array();
        std::string mac;
        std::string state;
        std::string age;
        std::string type;
        bool dev_seen = false;

        for (std::size_t i = address_index + 1; i < tokens.size(); ++i)
        {
            std::string token = TrimPunct(tokens[i]);
            if (token.empty())
            {
                continue;
            }

            // Linux: `dev <iface>` / `lladdr <mac>`
            if (token == "dev")
            {
                dev_seen = true;
                if (i + 1 < tokens.size())
                {
                    interfaces.push_back(TrimPunct(tokens[++i]));
                }
                continue;
            }
            if (token == "lladdr")
            {
                if (i + 1 < tokens.size())
                {
                    mac = NormalizeHardwareAddress(tokens[++i]);
                }
                continue;
            }

            // Arista: `Vlan9,` `Ethernet3` 처럼 인터페이스가 쉼표로 이어진다.
            if (token.back() == ',')
            {
                token.pop_back();
                if (!token.empty())
                {
                    interfaces.push_back(token);
                }
                continue;
            }

            // MAC (Cisco/Arista 점 표기 포함)
            if (mac.empty() && LooksLikeHardwareAddress(token))
            {
                mac = NormalizeHardwareAddress(token);
                continue;
            }

            // age (`2:31:51` Arista, `-` 또는 숫자 Cisco)
            if (age.empty() && (LooksLikeAge(token) || IsNumber(token) || token == "-"))
            {
                age = token;
                continue;
            }

            // 상태 (Linux)
            if (state.empty() && IsNeighborState(token))
            {
                state = token;
                continue;
            }

            // Type 컬럼 (Cisco)
            if (type.empty() && IsArpType(token))
            {
                type = token;
                continue;
            }

            // 남은 영문 토큰은 인터페이스명 후보로 본다.
            // (`Protocol`/`Internet` 같은 헤더 토큰은 걸러낸다)
            if (token != "Protocol" && token != "Internet" && token != "Address" &&
                token != "Age" && token != "Hardware" && token != "Type" &&
                token != "Interface" && token != "Addr" && token != "(min)" &&
                token != "Vlan" && token != "vlan")
            {
                if (!dev_seen || interfaces.empty())
                {
                    interfaces.push_back(token);
                }
            }
        }

        if (!mac.empty())
        {
            entry["mac"] = mac;
        }
        if (!state.empty())
        {
            entry["state"] = state;
        }
        if (!age.empty())
        {
            entry["age"] = age;
        }
        if (!type.empty())
        {
            entry["type"] = type;
        }
        if (!interfaces.empty())
        {
            entry["interfaces"] = interfaces;
            entry["interface"] = interfaces.front();
        }

        // 주소도 MAC 도 없으면 헤더/잡음 줄이다.
        if (!entry.contains("address") || entry["address"].get<std::string>().empty())
        {
            return {};
        }
        if (mac.empty() && interfaces.empty())
        {
            return {};
        }

        entries.push_back(std::move(entry));
        return {};
    }
};

}  // namespace

nlohmann::json ParseArpTable(const std::string& raw_output, Vendor /*vendor*/)
{
    ParseSession<IpAddrLexer, IpAddrParser> session(raw_output);
    IpAddrParser& parser = session.Parser();

    try
    {
        auto* tree = parser.arpDocument();
        ArpVisitor visitor;
        visitor.session = &session;
        for (auto* item : tree->arpEntry())
        {
            visitor.visitArpEntry(item);
        }

        Json body = Json::object();
        body["entries"] = visitor.entries;
        body["entry_count"] = visitor.entries.size();
        return AttachParseInfo(std::move(body), session);
    }
    catch (const std::exception& ex)
    {
        return MakeParseFailure(raw_output, ex.what());
    }
}

// ============================================================================
// 공개 API — NIC / 라우팅 / 인터페이스 / 스위치 / 방화벽
// ============================================================================

nlohmann::json ParseNicStatus(const std::string& raw_output)
{
    ParseSession<IpAddrLexer, IpAddrParser> session(raw_output);
    IpAddrParser& parser = session.Parser();

    try
    {
        auto* tree = parser.document();
        NicVisitor visitor;
        visitor.session = &session;
        for (auto* item : tree->item())
        {
            visitor.visitItem(item);
        }

        Json body = Json::object();
        body["interfaces"] = visitor.interfaces;
        return AttachParseInfo(std::move(body), session);
    }
    catch (const std::exception& ex)
    {
        return MakeParseFailure(raw_output, ex.what());
    }
}

nlohmann::json ParseNicBrief(const std::string& raw_output)
{
    ParseSession<IpAddrLexer, IpAddrParser> session(raw_output);
    IpAddrParser& parser = session.Parser();

    try
    {
        auto* tree = parser.briefDocument();
        NicVisitor visitor;
        visitor.session = &session;
        for (auto* item : tree->item())
        {
            visitor.visitItem(item);
        }

        Json body = Json::object();
        body["brief"] = visitor.brief;
        return AttachParseInfo(std::move(body), session);
    }
    catch (const std::exception& ex)
    {
        return MakeParseFailure(raw_output, ex.what());
    }
}

// FRR 라우터는 라우팅 정보를 두 가지 형식으로 내보낼 수 있다.
//
//   (A) 호스트 커널 — `ip route show` (Alpine/Debian 셸)
//       default via 192.168.122.1 dev eth0 metric 1
//       10.99.10.0/24 dev eth1 proto kernel scope link src 10.99.10.1
//       10.10.128.0/21 nhid 30 via 10.99.10.4 dev eth1 proto ospf metric 20
//       → 라우트 코드가 없고 IpAddr 문법으로 파싱해야 한다.
//
//   (B) vtysh — `show ip route` (FRR CLI)
//       Codes: K - kernel route, C - connected, S - static, ...
//       O>* 0.0.0.0/0 [110/1] via 10.99.10.1, eth0, weight 1, 00:17:20
//       O   10.99.10.0/24 [110/100] is directly connected, eth0, ...
//       → 라우트 코드가 있고 FrrRouter 문법으로 파싱해야 한다.
//
// 같은 Vendor::kFrr 라도 문법이 달라야 하므로 내용으로 판별한다.
bool LooksLikeVtyshRouteTable(const std::string& raw_output)
{
    // 1) FRR CLI 의 코드 설명 머리말은 가장 확실한 신호다.
    if (raw_output.find("Codes:") != std::string::npos)
    {
        return true;
    }

    // 2) 줄 첫 토큰이 라우트 코드(영문 대문자 1~4자 + 선택적 `*`/`>` 마커)인지 본다.
    //    커널 형식의 첫 토큰은 `default` 나 CIDR(`10.99.10.0/24`) 이므로
    //    전부 대문자로만 이루어진 짧은 토큰과 겹치지 않는다.
    std::istringstream stream(raw_output);
    std::string line;
    while (std::getline(stream, line))
    {
        std::istringstream line_stream(line);
        std::string token;
        if (!(line_stream >> token))
        {
            continue;
        }

        std::size_t index = 0;
        while (index < token.size() &&
               std::isupper(static_cast<unsigned char>(token[index])) != 0)
        {
            ++index;
        }

        const std::size_t letter_count = index;
        if (letter_count == 0 || letter_count > 4)
        {
            continue;
        }

        while (index < token.size() && (token[index] == '*' || token[index] == '>'))
        {
            ++index;
        }

        if (index == token.size())
        {
            // 코드 뒤에 목적지(주소 또는 `is directly connected`)가 이어지면 확정.
            std::string second;
            if ((line_stream >> second) && !second.empty())
            {
                return true;
            }
        }
    }

    return false;
}

nlohmann::json ParseRouteStatus(const std::string& raw_output, Vendor vendor)
{
    // Linux `ip route show` 는 라우트 코드(O>*, C 등)가 없고
    // `default via ... dev ... proto ...` 형태다.
    // FRR/Cisco 문법으로는 코드가 없어 파싱되지 않으므로 IpAddr 문법을 쓴다.
    //
    // FRR 라우터(Vendor::kFrr)는 호스트 OS 가 리눅스이므로 커널 테이블을
    // 그대로 내보낼 수도 있고(A), vtysh 로 코드가 붙은 표를 낼 수도 있다(B).
    // 그래서 kFrr 은 출력 내용을 보고 문법을 고른다.
    //
    // nftables 방화벽(kNftables)·OpenVSwitch(kOpenVSwitch) 도 호스트는
    // 리눅스이므로 커널 `ip route show` 형식을 그대로 낸다.
    // 어느 쪽이든 라우트 코드가 없으면 IpAddr 문법으로 파싱한다.
    const bool linux_host_vendor = vendor == Vendor::kUbuntu || vendor == Vendor::kFrr ||
                                   vendor == Vendor::kNftables ||
                                   vendor == Vendor::kOpenVSwitch;

    const bool linux_kernel_style =
        linux_host_vendor && !LooksLikeVtyshRouteTable(raw_output);

    if (linux_kernel_style)
    {
        // IpAddr 문법의 routeDocument 를 사용한다.
        //  NicVisitor 가 routeEntry 를 처리하므로 그 결과를 재사용한다.
        ParseSession<IpAddrLexer, IpAddrParser> session(raw_output);
        IpAddrParser& parser = session.Parser();
        try
        {
            auto* tree = parser.routeDocument();
            NicVisitor visitor;
            visitor.session = &session;
            for (auto* item : tree->item())
            {
                visitor.visitItem(item);
            }

            Json body = Json::object();
            body["routes"] = visitor.routes;
            body["route_count"] = visitor.routes.size();

            Json protocols = Json::array();
            for (const auto& route : visitor.routes)
            {
                if (route.contains("protocol") && route["protocol"].is_string())
                {
                    const std::string protocol = route["protocol"].get<std::string>();
                    bool seen = false;
                    for (const auto& existing : protocols)
                    {
                        if (existing.get<std::string>() == protocol)
                        {
                            seen = true;
                            break;
                        }
                    }
                    if (!seen)
                    {
                        protocols.push_back(protocol);
                    }
                }
            }
            body["protocols"] = protocols;
            return AttachParseInfo(std::move(body), session);
        }
        catch (const std::exception& ex)
        {
            return MakeParseFailure(raw_output, ex.what());
        }
    }

    ParseSession<FrrRouterLexer, FrrRouterParser> session(raw_output);
    FrrRouterParser& parser = session.Parser();

    try
    {
        auto* tree = parser.routeDocument();
        RouteVisitor visitor;
        visitor.session = &session;
        for (auto* item : tree->routeItem())
        {
            visitor.visitRouteItem(item);
        }

        Json body = Json::object();
        body["routes"] = visitor.routes;
        body["protocols"] = visitor.protocols_seen;
        body["route_count"] = visitor.routes.size();
        if (!visitor.gateway_of_last_resort.empty())
        {
            body["gateway_of_last_resort"] = visitor.gateway_of_last_resort;
        }
        return AttachParseInfo(std::move(body), session);
    }
    catch (const std::exception& ex)
    {
        return MakeParseFailure(raw_output, ex.what());
    }
}

nlohmann::json ParseInterfaceStatus(const std::string& raw_output, Vendor /*vendor*/)
{
    ParseSession<FrrRouterLexer, FrrRouterParser> session(raw_output);
    FrrRouterParser& parser = session.Parser();

    try
    {
        auto* tree = parser.ifaceDocument();
        InterfaceVisitor visitor;
        visitor.session = &session;
        for (auto* item : tree->ifaceItem())
        {
            visitor.visitIfaceItem(item);
        }

        Json body = Json::object();
        body["interfaces"] = visitor.interfaces;
        body["interface_count"] = visitor.interfaces.size();
        return AttachParseInfo(std::move(body), session);
    }
    catch (const std::exception& ex)
    {
        return MakeParseFailure(raw_output, ex.what());
    }
}

nlohmann::json ParseOvsTopology(const std::string& raw_output)
{
    // 입력이 `list port` 출력이면 속성 레코드가 `--` 로 나뉜다.
    const bool is_list = raw_output.find("\n--\n") != std::string::npos ||
                         raw_output.rfind("--", 0) == 0 ||
                         raw_output.find("_uuid") != std::string::npos;

    if (is_list)
    {
        ParseSession<OvsTopologyLexer, OvsTopologyParser> session(raw_output);
        OvsTopologyParser& parser = session.Parser();
        try
        {
            auto* tree = parser.listDocument();
            OvsVisitor visitor;
            visitor.session = &session;
            for (auto* item : tree->listItem())
            {
                visitor.visitListItem(item);
            }
            Json body = Json::object();
            body["ports"] = visitor.ports;
            body["port_count"] = visitor.ports.size();
            return AttachParseInfo(std::move(body), session);
        }
        catch (const std::exception& ex)
        {
            return MakeParseFailure(raw_output, ex.what());
        }
    }

    ParseSession<OvsTopologyLexer, OvsTopologyParser> session(raw_output);
    OvsTopologyParser& parser = session.Parser();
    try
    {
        auto* tree = parser.showDocument();
        OvsVisitor visitor;
        visitor.session = &session;
        for (auto* item : tree->showItem())
        {
            visitor.visitShowItem(item);
        }
        Json body = Json::object();
        body["bridges"] = visitor.bridges;
        body["bridge_count"] = visitor.bridges.size();
        return AttachParseInfo(std::move(body), session);
    }
    catch (const std::exception& ex)
    {
        return MakeParseFailure(raw_output, ex.what());
    }
}

nlohmann::json ParseSwitchVlan(const std::string& raw_output)
{
    ParseSession<SwitchTopologyLexer, SwitchTopologyParser> session(raw_output);
    SwitchTopologyParser& parser = session.Parser();

    try
    {
        auto* tree = parser.vlanDocument();
        SwitchVisitor visitor;
        visitor.session = &session;
        for (auto* item : tree->vlanItem())
        {
            visitor.visitVlanItem(item);
        }

        Json body = Json::object();
        body["vlans"] = visitor.vlans;
        body["vlan_count"] = visitor.vlans.size();
        return AttachParseInfo(std::move(body), session);
    }
    catch (const std::exception& ex)
    {
        return MakeParseFailure(raw_output, ex.what());
    }
}

nlohmann::json ParseSwitchPorts(const std::string& raw_output)
{
    ParseSession<SwitchTopologyLexer, SwitchTopologyParser> session(raw_output);
    SwitchTopologyParser& parser = session.Parser();

    try
    {
        auto* tree = parser.portDocument();
        SwitchVisitor visitor;
        visitor.session = &session;
        for (auto* item : tree->portItem())
        {
            visitor.visitPortItem(item);
        }

        Json body = Json::object();
        body["ports"] = visitor.ports;
        body["port_count"] = visitor.ports.size();
        return AttachParseInfo(std::move(body), session);
    }
    catch (const std::exception& ex)
    {
        return MakeParseFailure(raw_output, ex.what());
    }
}

nlohmann::json ParseFirewallRules(const std::string& raw_output)
{
    ParseSession<NftablesRuleLexer, NftablesRuleParser> session(raw_output);
    NftablesRuleParser& parser = session.Parser();

    try
    {
        auto* tree = parser.rulesetDocument();
        NftablesVisitor visitor;
        visitor.session = &session;
        for (auto* item : tree->rulesetItem())
        {
            if (item->tableBlock() != nullptr)
            {
                visitor.visitTableBlock(item->tableBlock());
            }
        }

        Json body = Json::object();
        body["tables"] = visitor.tables;
        body["table_count"] = visitor.tables.size();

        std::size_t chain_count = 0;
        std::size_t rule_count = 0;
        for (const auto& table : visitor.tables)
        {
            if (!table.contains("chains"))
            {
                continue;
            }
            chain_count += table["chains"].size();
            for (const auto& chain : table["chains"])
            {
                if (chain.contains("rules"))
                {
                    rule_count += chain["rules"].size();
                }
            }
        }
        body["chain_count"] = chain_count;
        body["rule_count"] = rule_count;
        return AttachParseInfo(std::move(body), session);
    }
    catch (const std::exception& ex)
    {
        return MakeParseFailure(raw_output, ex.what());
    }
}

nlohmann::json ParseQueryOutput(Vendor vendor,
                                const std::string& target,
                                const std::string& raw_output)
{
    if (target == "nic" || target == "addr")
    {
        return ParseNicStatus(raw_output);
    }
    if (target == "brief" || target == "nic-brief")
    {
        // `show ip interface brief` 와 `ip -br addr show` 를 모두 지원한다.
        if (raw_output.find("Interface") != std::string::npos &&
            raw_output.find("OK?") != std::string::npos)
        {
            return ParseInterfaceStatus(raw_output, vendor);
        }
        return ParseNicBrief(raw_output);
    }
    if (target == "route" || target == "route-table")
    {
        return ParseRouteStatus(raw_output, vendor);
    }
    if (target == "interface" || target == "interface-brief")
    {
        return ParseInterfaceStatus(raw_output, vendor);
    }
    if (target == "topology" || target == "ovs")
    {
        return ParseOvsTopology(raw_output);
    }
    if (target == "vlan")
    {
        return ParseSwitchVlan(raw_output);
    }
    if (target == "switchport" || target == "port")
    {
        return ParseSwitchPorts(raw_output);
    }
    if (target == "ruleset" || target == "nft")
    {
        return ParseFirewallRules(raw_output);
    }
    if (target == "arp" || target == "neigh")
    {
        return ParseArpTable(raw_output, vendor);
    }

    // target 미지정: 벤더 기본 조회로 폴백
    switch (vendor)
    {
    case Vendor::kOpenVSwitch:
        return ParseOvsTopology(raw_output);
    case Vendor::kFrr:
    case Vendor::kCisco:
        return ParseRouteStatus(raw_output, vendor);
    case Vendor::kArista:
        return ParseSwitchVlan(raw_output);
    case Vendor::kNftables:
        return ParseFirewallRules(raw_output);
    case Vendor::kUbuntu:
    case Vendor::kUnknown:
        break;
    }
    return ParseNicStatus(raw_output);
}

// ============================================================================
// 벤더 이름 <-> Vendor 열거형
//
// 수집기(collector)는 ProberConfig::GetProductName() 만 알고 있으므로
// 제품명 문자열과 Vendor 사이의 변환이 필요하다.
// ProberConfig::DetectProductName() 이 만드는 문자열을 그대로 받는다.
// ============================================================================

Vendor VendorFromProductName(const std::string& product_name)
{
    if (product_name.find("Cisco") != std::string::npos)
    {
        return Vendor::kCisco;
    }
    if (product_name.find("Arista") != std::string::npos)
    {
        return Vendor::kArista;
    }
    if (product_name.find("FRR") != std::string::npos)
    {
        return Vendor::kFrr;
    }
    if (product_name.find("OpenVSwitch") != std::string::npos ||
        product_name.find("Open vSwitch") != std::string::npos)
    {
        return Vendor::kOpenVSwitch;
    }
    if (product_name.find("nftables") != std::string::npos ||
        product_name.find("nft") != std::string::npos)
    {
        return Vendor::kNftables;
    }
    if (product_name.find("Ubuntu") != std::string::npos ||
        product_name.find("Linux") != std::string::npos)
    {
        return Vendor::kUbuntu;
    }
    return Vendor::kUnknown;
}

std::string VendorName(Vendor vendor)
{
    switch (vendor)
    {
    case Vendor::kOpenVSwitch:
        return "OpenVSwitch";
    case Vendor::kFrr:
        return "FRR";
    case Vendor::kCisco:
        return "Cisco";
    case Vendor::kArista:
        return "Arista";
    case Vendor::kNftables:
        return "nftables";
    case Vendor::kUbuntu:
        return "Ubuntu";
    case Vendor::kUnknown:
        break;
    }
    return "Unknown";
}

}  // namespace cli_parser
