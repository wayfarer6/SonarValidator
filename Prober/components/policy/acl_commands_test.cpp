// 서버가 보낸 일괄 차단 ACL 을 장치 명령으로 옮기는 변환을 검증합니다.
//
// <h2>⚠️ 이 테스트가 막으려는 사고</h2>
//   IOS/FRR 의 확장 ACL 은 모든 줄을 훑고 매칭되는 줄이 없으면
//   <b>암묵적 deny any</b> 로 떨어집니다. 그런데 이 ACL 을 인터페이스에 걸면
//   (`ip access-group ... in`) 그 인터페이스로 들어오는 트래픽 전체가 이 ACL 의
//   판정을 받습니다.
//
//   그래서 차단 목록만 넣고 마지막 `permit ip any any` 를 빼면, 차단 대상이
//   아닌 트래픽까지 암묵적 deny 에 걸려 <b>인터페이스가 통째로 죽습니다.</b>
//   라우터에서는 곧 관리 접속·텔레메트리 두절이고, 프로버가 서버에 보고하지
//   못해 스스로 고립됩니다.
//
//   랩에서 한 번 겪으면 복구가 어려우므로(장치에 접속이 끊김) 여기서 고정합니다.

#include <iostream>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "components/policy/acl_commands.hpp"

namespace
{
    int failures = 0;

    void Expect(bool condition, const std::string& label)
    {
        if (condition)
        {
            std::cout << "  [ok]   " << label << '\n';
            return;
        }
        std::cerr << "  [FAIL] " << label << '\n';
        ++failures;
    }

    bool Contains(const std::vector<std::string>& lines, const std::string& text)
    {
        for (const auto& line : lines)
        {
            if (line == text)
            {
                return true;
            }
        }
        return false;
    }

    // 줄 목록에서 특정 접두사로 시작하는 항목의 개수를 셉니다.
    std::size_t CountPrefix(const std::vector<std::string>& lines, const std::string& prefix)
    {
        std::size_t count = 0;
        for (const auto& line : lines)
        {
            if (line.rfind(prefix, 0) == 0)
            {
                ++count;
            }
        }
        return count;
    }

    // 차단 한 건을 담은 서버 노드를 만듭니다. (라우터 형태 — match_criteria 중첩)
    nlohmann::json RouterPolicy(const std::string& source, const std::string& source_wild,
                                const std::string& destination, const std::string& destination_wild)
    {
        nlohmann::json policy;
        policy["command"] = nlohmann::json::array({"apply"});
        policy["rule_target"]["acl_name"] = nlohmann::json::array({"SONAR-CSO"});
        policy["rule_target"]["applied_interface"] = nlohmann::json::array({"GigabitEthernet1"});
        nlohmann::json entry;
        entry["match_criteria"]["ip_saddr"] = nlohmann::json::array({source});
        entry["match_criteria"]["ip_saddr_wildcard"] = nlohmann::json::array({source_wild});
        entry["match_criteria"]["ip_daddr"] = nlohmann::json::array({destination});
        entry["match_criteria"]["ip_daddr_wildcard"] = nlohmann::json::array({destination_wild});
        entry["match_criteria"]["protocol"] = nlohmann::json::array({"ip"});
        entry["action"] = nlohmann::json::array({"deny"});
        entry["reason"] = nlohmann::json::array({"허용 목록 밖"});
        policy["acl_rules"] = nlohmann::json::array({entry});
        return policy;
    }

    // ------------------------------------------------------------------
    //  서버 노드 해석
    // ------------------------------------------------------------------

    void TestReadEntriesNested()
    {
        const auto policy = RouterPolicy("192.168.122.254", "0.0.0.0", "0.0.0.0", "255.255.255.255");
        const auto entries = acl::ReadEntries(policy);
        Expect(entries.size() == 1, "중첩(match_criteria) 형태에서 한 건을 읽는다");
        Expect(!entries.empty() && entries[0].source == "192.168.122.254", "출발 주소를 읽는다");
        Expect(!entries.empty() && entries[0].source_wildcard == "0.0.0.0", "출발 반전 마스크를 읽는다");
        Expect(!entries.empty() && entries[0].destination == "0.0.0.0", "도착 주소를 읽는다");
        Expect(!entries.empty() && entries[0].destination_wildcard == "255.255.255.255",
               "기본 경로는 모든 주소와 매칭되는 반전 마스크를 쓴다");
        Expect(!entries.empty() && entries[0].reason == "허용 목록 밖", "사유를 읽는다");
    }

    void TestReadEntriesFlat()
    {
        // OVS 형태: 조건부가 곧 항목입니다.
        nlohmann::json policy;
        nlohmann::json entry;
        entry["ip_saddr"] = nlohmann::json::array({"10.0.8.0/24"});
        entry["ip_daddr"] = nlohmann::json::array({"10.0.9.0/24"});
        policy["acl_rules"] = nlohmann::json::array({entry});

        const auto entries = acl::ReadEntries(policy);
        Expect(entries.size() == 1, "평평한 형태에서도 한 건을 읽는다");
        Expect(!entries.empty() && entries[0].source == "10.0.8.0/24", "CIDR 을 그대로 읽는다");
    }

    void TestReadEntriesDropsEmptyAddress()
    {
        // ⚠️ 주소가 빈 줄을 그대로 문법에 넣으면 `deny ip   ` 가 되어 장치가
        //    전체를 거부하거나 "any any" 로 해석되어 트래픽이 끊깁니다.
        nlohmann::json policy;
        nlohmann::json broken;
        broken["match_criteria"]["ip_saddr"] = nlohmann::json::array({""});
        broken["match_criteria"]["ip_daddr"] = nlohmann::json::array({"10.0.9.0/24"});
        nlohmann::json good;
        good["match_criteria"]["ip_saddr"] = nlohmann::json::array({"10.0.8.0/24"});
        good["match_criteria"]["ip_daddr"] = nlohmann::json::array({"10.0.9.0/24"});
        policy["acl_rules"] = nlohmann::json::array({broken, good});

        const auto entries = acl::ReadEntries(policy);
        Expect(entries.size() == 1, "주소가 빈 줄은 버리고 나머지를 남긴다");
    }

    void TestReadTarget()
    {
        const auto policy = RouterPolicy("10.0.8.0", "0.0.0.255", "10.0.9.0", "0.0.0.255");
        Expect(acl::ReadName(policy) == "SONAR-CSO", "ACL 이름을 읽는다");
        Expect(acl::ReadApplyTarget(policy) == "GigabitEthernet1", "적용 인터페이스를 읽는다");

        // ⚠️ OVS 는 ovs-ofctl 의 대상이 <b>브리지</b>입니다. 업링크 포트를
        //    넣으면 "no bridge named eth0" 로 실패합니다.
        nlohmann::json ovs;
        ovs["rule_target"]["bridge_name"] = nlohmann::json::array({"br0"});
        ovs["rule_target"]["applied_interface"] = nlohmann::json::array({"eth0"});
        Expect(acl::ReadApplyTarget(ovs) == "br0", "브리지를 인터페이스보다 먼저 본다");
    }

    // ------------------------------------------------------------------
    //  IOS 명령 생성
    // ------------------------------------------------------------------

    void TestIosAlwaysPermitsTheRest()
    {
        const auto commands = acl::BuildIosAclCommands(
            "SONAR-CSO", "GigabitEthernet1",
            {acl::Entry{"192.168.122.254", "0.0.0.0", "0.0.0.0", "255.255.255.255", "허용 목록 밖"}});

        // ⚠️ 이 줄이 빠지면 인터페이스가 통째로 죽습니다. (암묵적 deny any)
        Expect(Contains(commands, "permit ip any any"),
               "차단 목록 뒤에 permit ip any any 를 넣는다");
        Expect(Contains(commands, "deny ip 192.168.122.254 0.0.0.0 0.0.0.0 255.255.255.255"),
               "차단 줄을 반전 마스크로 만든다");
        Expect(Contains(commands, "ip access-list extended SONAR-CSO"), "이름 있는 ACL 을 만든다");
        Expect(Contains(commands, "no ip access-list extended SONAR-CSO"),
               "이전 ACL 을 지우고 다시 쓴다");
        Expect(Contains(commands, "ip access-group SONAR-CSO in"), "인터페이스에 건다");
        Expect(Contains(commands, "no ip access-group SONAR-CSO in"), "이전 바인딩을 먼저 푼다");
        // 설정 모드에 갇히면 뒤따르는 조회가 거부됩니다.
        Expect(Contains(commands, "end"), "설정 모드에서 빠져나온다");
    }

    void TestIosPermitComesAfterDenies()
    {
        const auto commands = acl::BuildIosAclCommands(
            "SONAR-CSO", "GigabitEthernet1",
            {acl::Entry{"10.0.8.0", "0.0.0.255", "10.0.9.0", "0.0.0.255", "등급 건너뜀"}});

        std::size_t lastDeny = 0;
        std::size_t permit = 0;
        for (std::size_t i = 0; i < commands.size(); ++i)
        {
            if (commands[i].rfind("deny ip ", 0) == 0)
            {
                lastDeny = i;
            }
            if (commands[i] == "permit ip any any")
            {
                permit = i;
            }
        }
        // ⚠️ 순서가 뒤바뀌면 차단이 하나도 적용되지 않습니다 — ACL 은 첫 매칭에서
        //    결정되므로 permit 이 앞에 있으면 모든 트래픽이 통과합니다.
        Expect(permit > lastDeny, "permit 이 모든 deny 뒤에 온다");
    }

    void TestIosEmptyListClearsAcl()
    {
        const auto commands = acl::BuildIosAclCommands("SONAR-CSO", "GigabitEthernet1", {});

        // 운영자가 마지막 금지 연결을 지운 경우입니다. 차단이 <b>풀려야</b> 합니다.
        Expect(Contains(commands, "no ip access-list extended SONAR-CSO"), "ACL 을 지운다");
        Expect(CountPrefix(commands, "ip access-list extended") == 0, "빈 목록으로 ACL 을 만들지 않는다");
        Expect(CountPrefix(commands, "ip access-group") == 0, "빈 목록을 인터페이스에 걸지 않는다");
        Expect(!Contains(commands, "permit ip any any"), "빈 목록에는 permit 이 없다");
    }

    void TestIosWithoutInterfaceStillCreatesAcl()
    {
        // 장치 인터페이스를 알아내지 못한 경우에도 규칙 자체는 만들되,
        // 엉뚱한 인터페이스를 막지 않도록 바인딩하지 않습니다.
        const auto commands = acl::BuildIosAclCommands(
            "SONAR-CSO", "",
            {acl::Entry{"10.0.8.0", "0.0.0.255", "10.0.9.0", "0.0.0.255", "등급 건너뜀"}});

        Expect(CountPrefix(commands, "ip access-group") == 0, "인터페이스를 모르면 걸지 않는다");
        Expect(Contains(commands, "permit ip any any"), "그래도 permit 은 넣는다 (나중에 걸 때 대비)");
    }

    // ------------------------------------------------------------------
    //  FRR 명령 생성
    // ------------------------------------------------------------------

    void TestFrrAlwaysPermitsTheRest()
    {
        const auto lines = acl::BuildFrrAclCommands(
            "SONAR-CSO", "eth0",
            {acl::Entry{"10.0.8.0", "0.0.0.255", "10.0.9.0", "0.0.0.255", "등급 건너뜀"}});

        // IOS 와 같은 이유로 반드시 필요합니다. (암묵적 deny any)
        Expect(Contains(lines, "access-list SONAR-CSO permit ip any any"),
               "차단 목록 뒤에 permit ip any any 를 넣는다");
        Expect(Contains(lines, "access-list SONAR-CSO deny ip 10.0.8.0 0.0.0.255 10.0.9.0 0.0.0.255"),
               "차단 줄을 만든다");
        Expect(Contains(lines, "no access-list SONAR-CSO"), "이전 목록을 지운다");
        Expect(Contains(lines, "ip access-group SONAR-CSO in"), "인터페이스에 건다");
    }

    void TestFrrEmptyListClearsAcl()
    {
        const auto lines = acl::BuildFrrAclCommands("SONAR-CSO", "eth0", {});
        Expect(Contains(lines, "no access-list SONAR-CSO"), "ACL 을 지운다");
        Expect(CountPrefix(lines, "access-list SONAR-CSO deny") == 0, "빈 목록에는 deny 가 없다");
        Expect(!Contains(lines, "access-list SONAR-CSO permit ip any any"),
               "빈 목록에는 permit 도 없다");
    }
}

int main()
{
    std::cout << "acl_commands_test\n";

    TestReadEntriesNested();
    TestReadEntriesFlat();
    TestReadEntriesDropsEmptyAddress();
    TestReadTarget();

    TestIosAlwaysPermitsTheRest();
    TestIosPermitComesAfterDenies();
    TestIosEmptyListClearsAcl();
    TestIosWithoutInterfaceStillCreatesAcl();

    TestFrrAlwaysPermitsTheRest();
    TestFrrEmptyListClearsAcl();

    if (failures == 0)
    {
        std::cout << "all acl_commands tests passed\n";
        return 0;
    }
    std::cerr << failures << " acl_commands test(s) failed\n";
    return 1;
}
