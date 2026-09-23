#ifndef SONAR_VALIDATOR_PROBER_POLICY_JSON_HPP_
#define SONAR_VALIDATOR_PROBER_POLICY_JSON_HPP_

#include <string>
#include <vector>

#include <nlohmann/json.hpp>

// 정책 JSON의 필드가 배열/스칼라 형태로 섞여 있어서
// (예: "vendor": ["Arista"], "vlan_id": "10")
// 두 형태를 모두 안전하게 읽을 수 있도록 돕는 헬퍼입니다.
namespace policy_json
{

// 키가 존재하지 않거나 null이면 fallback을 반환합니다.
// 배열이면 첫 번째 원소를 문자열로 반환합니다.
inline std::string AsString(const nlohmann::json& object,
                            const char* key,
                            const std::string& fallback = {})
{
    const auto it = object.find(key);
    if (it == object.end() || it->is_null())
    {
        return fallback;
    }

    if (it->is_array())
    {
        if (it->empty() || it->front().is_null())
        {
            return fallback;
        }
        const nlohmann::json& first = it->front();
        if (first.is_string())
        {
            return first.get<std::string>();
        }
        if (first.is_number_integer())
        {
            return std::to_string(first.get<long long>());
        }
        if (first.is_number_unsigned())
        {
            return std::to_string(first.get<unsigned long long>());
        }
        return first.dump();
    }

    if (it->is_string())
    {
        return it->get<std::string>();
    }
    if (it->is_number_integer())
    {
        return std::to_string(it->get<long long>());
    }
    if (it->is_number_unsigned())
    {
        return std::to_string(it->get<unsigned long long>());
    }
    if (it->is_boolean())
    {
        return it->get<bool>() ? "true" : "false";
    }
    return it->dump();
}

// 배열이면 모든 원소를 문자열 목록으로, 스칼라면 단일 원소 목록으로 반환합니다.
inline std::vector<std::string> AsStringList(const nlohmann::json& object,
                                             const char* key)
{
    const auto it = object.find(key);
    if (it == object.end() || it->is_null())
    {
        return {};
    }

    std::vector<std::string> result;
    if (it->is_array())
    {
        for (const auto& item : *it)
        {
            if (item.is_string())
            {
                result.push_back(item.get<std::string>());
            }
            else if (item.is_number())
            {
                result.push_back(item.dump());
            }
        }
        return result;
    }

    if (it->is_string())
    {
        return {it->get<std::string>()};
    }
    if (it->is_number())
    {
        return {it->dump()};
    }
    return {};
}

inline bool Has(const nlohmann::json& object, const char* key)
{
    const auto it = object.find(key);
    return it != object.end() && !it->is_null();
}

} // namespace policy_json

#endif // SONAR_VALIDATOR_PROBER_POLICY_JSON_HPP_
