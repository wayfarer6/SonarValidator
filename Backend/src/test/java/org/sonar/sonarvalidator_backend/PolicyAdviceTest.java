package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Policy.PacketVariables;
import org.sonar.sonarvalidator_backend.Policy.PolicyViolation;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;
import org.sonar.sonarvalidator_backend.Policy.advice.PolicyAdviceAnswer;
import org.sonar.sonarvalidator_backend.Policy.advice.PolicyAdviceContext;
import org.sonar.sonarvalidator_backend.Policy.advice.PolicyAdviceOption;
import org.sonar.sonarvalidator_backend.Policy.advice.PolicyAdviceParser;
import org.sonar.sonarvalidator_backend.Policy.advice.PolicyAdvicePromptBuilder;
import org.sonar.sonarvalidator_backend.Policy.advice.ViolationBrief;

/**
 * AI 정책 조언(SONAR-43)의 계약을 검증합니다.
 *
 * <h2>여기서 잡으려는 실패 모드</h2>
 * <ul>
 *   <li><b>센티널 -1 누출</b> — 판정 엔진의 {@code ANY_PORT(-1)} 이 프롬프트에
 *       그대로 들어가면 모델이 "포트 -1" 을 실제 포트로 읽고 없는 문제를
 *       지어냅니다. {@link ViolationBrief} 가 {@code null} 로 바꿔야 합니다.</li>
 *   <li><b>등급 미상과 차이 0 혼동</b> — 등급을 모를 때 차이를 0 으로 만들면
 *       "등급이 같다" 는 <b>거짓 사실</b>이 프롬프트에 들어갑니다.</li>
 *   <li><b>모델 응답 모양 3가지</b> — 순수 JSON / 코드블록 / 설명+JSON.
 *       하나만 처리하면 나머지는 "조언 실패" 로 보입니다.</li>
 *   <li><b>스키마 이탈</b> — {@code options} 를 문자열 배열로 보내는 경우.
 *       버리면 조언의 절반이 사라집니다.</li>
 * </ul>
 *
 * <p>네트워크·DB 를 쓰지 않습니다. 프롬프트 조립과 파싱은 <b>순수 함수</b>라
 * 전부 여기서 검증할 수 있습니다.
 */
class PolicyAdviceTest {

    private final PolicyAdviceParser parser = new PolicyAdviceParser();

    // -------------------------------------------------------------------------
    //  ViolationBrief — 판정 표현 → 프롬프트 표현
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ANY_PORT(-1) 센티널을 포트로 내보내지 않는다")
    void doesNotLeakAnyPortSentinel() {
        // ⚠️ -1 을 그대로 넣으면 모델이 "포트 -1" 을 실제 포트로 읽습니다.
        final PolicyViolation violation = new PolicyViolation(
                "Rule-9001", "Subnet-0131", "Subnet-0141",
                ZoneClass.CONFIDENTIAL, ZoneClass.OPEN,
                "10.10.131.5", "10.30.141.9",
                PacketVariables.ANY_PORT,
                "등급 2단계 차이", PolicyViolation.Severity.CRITICAL);

        final ViolationBrief brief = ViolationBrief.from(violation);

        assertNull(brief.port(), "ANY_PORT 는 null 로 내보내야 합니다");
        assertTrue(brief.describe().contains("포트 제한 없음"),
                "문장으로는 '포트 제한 없음' 이라고 알려야 합니다");
        assertFalse(brief.describe().contains("-1"));
    }

    @Test
    @DisplayName("실제 포트는 그대로 유지한다")
    void keepsRealPort() {
        final PolicyViolation violation = new PolicyViolation(
                "Rule-0003", "Subnet-0111", "Subnet-0112",
                ZoneClass.SENSITIVE, ZoneClass.SENSITIVE,
                "10.20.111.10", "10.20.112.20", 8080,
                "포트 지정", PolicyViolation.Severity.MAJOR);

        final ViolationBrief brief = ViolationBrief.from(violation);

        assertEquals(8080, brief.port());
        assertTrue(brief.describe().contains("포트 8080"));
    }

    @Test
    @DisplayName("등급을 모르면 차이 0 이라 말하지 않는다")
    void unknownZoneIsNotZeroGap() {
        // ⚠️ 등급 미상을 차이 0 으로 만들면 "등급이 같다" 는 거짓 사실이 됩니다.
        final PolicyViolation violation = new PolicyViolation(
                "Rule-0001", "Subnet-0001", "Subnet-0002",
                null, null, "-", "-", PacketVariables.ANY_PORT,
                "규칙이 참조하는 서브넷을 찾을 수 없습니다.",
                PolicyViolation.Severity.MINOR);

        final ViolationBrief brief = ViolationBrief.from(violation);

        assertEquals(-1, brief.classGap());
        assertTrue(brief.knownClassGap().isEmpty());
        assertFalse(brief.forbidden(), "등급을 모르면 금지 쌍이라고 단정할 수 없습니다");
        assertTrue(brief.describe().contains("등급 미지정"));
    }

    @Test
    @DisplayName("Confidential ↔ Open 은 금지 쌍으로 표시한다")
    void marksForbiddenPair() {
        final PolicyViolation violation = new PolicyViolation(
                "Rule-9001", "Subnet-0131", "Subnet-0141",
                ZoneClass.CONFIDENTIAL, ZoneClass.OPEN,
                "10.10.131.5", "10.30.141.9", PacketVariables.ANY_PORT,
                "등급 건너뛰기", PolicyViolation.Severity.CRITICAL);

        final ViolationBrief brief = ViolationBrief.from(violation);

        assertEquals(2, brief.classGap());
        assertTrue(brief.forbidden());
        assertTrue(brief.describe().contains("금지 쌍"));
    }

    @Test
    @DisplayName("null 위반은 null 을 돌려준다")
    void nullViolationYieldsNull() {
        assertNull(ViolationBrief.from(null));
    }

    // -------------------------------------------------------------------------
    //  PolicyAdviceParser — 모델 응답 3가지 모양
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("순수 JSON 응답을 파싱한다")
    void parsesPlainJson() {
        final String response = """
                {
                  "risk_level": "HIGH",
                  "summary": "등급을 건너뛰는 연결이 1건 있습니다.",
                  "root_cause": "등급 분류가 실제 자산 성격과 어긋납니다.",
                  "policy_advice": "등급을 재검토하거나 중계 구간을 두세요.",
                  "options": [
                    {"title": "등급 재분류", "approach": "Public-Web-Server 를 Sensitive 로",
                     "security_impact": "등급 체계가 약해질 수 있음",
                     "operational_cost": "낮음", "recommended": true}
                  ],
                  "evidence": ["Rule-9001 Confidential -> Open"],
                  "needs_more_data": false
                }
                """;

        final PolicyAdviceAnswer answer = parser.parse(response);

        assertTrue(answer.structured());
        assertEquals("HIGH", answer.riskLevel());
        assertEquals(1, answer.options().size());
        assertEquals("등급 재분류", answer.options().get(0).title());
        assertTrue(answer.options().get(0).recommended());
        assertEquals(1, answer.evidence().size());
        assertFalse(answer.needsMoreData());
    }

    @Test
    @DisplayName("코드블록으로 감싼 JSON 을 파싱한다 (가장 흔한 모양)")
    void parsesCodeFencedJson() {
        final String response = """
                분석 결과입니다.

                ```json
                {
                  "risk_level": "CRITICAL",
                  "summary": "즉시 조치가 필요합니다.",
                  "root_cause": "중간 등급 없이 직접 연결했습니다.",
                  "policy_advice": "Sensitive 중계 구간을 두세요.",
                  "options": [
                    {"title": "중계 구간 신설", "approach": "Sensitive VM 추가",
                     "security_impact": "개선", "operational_cost": "장비 필요",
                     "recommended": true}
                  ],
                  "evidence": [],
                  "needs_more_data": false
                }
                ```

                위와 같이 판단했습니다.
                """;

        final PolicyAdviceAnswer answer = parser.parse(response);

        assertTrue(answer.structured(), "코드블록을 벗겨내야 합니다");
        assertEquals("CRITICAL", answer.riskLevel());
        assertEquals("중계 구간 신설", answer.options().get(0).title());
    }

    @Test
    @DisplayName("설명이 앞뒤에 붙은 JSON 을 파싱한다")
    void parsesJsonSurroundedByProse() {
        final String response = """
                제가 판단하기에는 위험도가 MEDIUM 입니다. 자세한 내용은 아래와 같습니다:
                {"risk_level": "MEDIUM", "summary": "포트 미지정 규칙이 있습니다.",
                 "root_cause": "포트가 지정되지 않았습니다.",
                 "policy_advice": "포트를 좁히세요.",
                 "options": [], "evidence": [], "needs_more_data": true}
                이상입니다.
                """;

        final PolicyAdviceAnswer answer = parser.parse(response);

        assertTrue(answer.structured());
        assertEquals("MEDIUM", answer.riskLevel());
        assertTrue(answer.needsMoreData());
    }

    @Test
    @DisplayName("options 를 문자열 배열로 보내도 제목만 있는 선택지로 살린다")
    void promotesStringOptions() {
        // ⚠️ 스키마 이탈. 버리면 조언의 절반이 사라집니다.
        final String response = """
                {"risk_level": "HIGH",
                 "options": ["등급 재분류", "중계 구간 신설"],
                 "evidence": "단일 문자열 근거"}
                """;

        final PolicyAdviceAnswer answer = parser.parse(response);

        assertTrue(answer.structured());
        assertEquals(2, answer.options().size(), "문자열도 선택지로 승격해야 합니다");
        assertEquals("등급 재분류", answer.options().get(0).title());
        // evidence 도 문자열 하나로 왔을 때 배열로 승격합니다.
        assertEquals(1, answer.evidence().size());
        assertEquals("단일 문자열 근거", answer.evidence().get(0));
    }

    @Test
    @DisplayName("recommendations 키로 답해도 읽는다 (키 이름 이탈)")
    void acceptsAlternativeOptionKey() {
        final String response = """
                {"risk_level": "LOW",
                 "recommendations": [
                   {"title": "유지", "approach": "현재 구성 유지", "recommended": true}
                 ]}
                """;

        final PolicyAdviceAnswer answer = parser.parse(response);

        assertEquals(1, answer.options().size());
        assertEquals("유지", answer.options().get(0).title());
    }

    @Test
    @DisplayName("제목이 빈 선택지 카드는 만들지 않는다")
    void dropsEmptyOptionCards() {
        // ⚠️ 빈 카드를 화면에 띄우면 운영자는 "내용 없는 조언" 을 봅니다.
        final String response = """
                {"risk_level": "LOW",
                 "options": [
                   {"title": "", "approach": ""},
                   {"title": "정상", "approach": "실행 방법"}
                 ]}
                """;

        final PolicyAdviceAnswer answer = parser.parse(response);

        assertEquals(1, answer.options().size());
        assertEquals("정상", answer.options().get(0).title());
    }

    @Test
    @DisplayName("파싱 실패는 오류가 아니라 원문 보존이다")
    void keepsRawWhenNotJson() {
        final String response = "죄송합니다. JSON 으로 답하지 못했습니다.";

        final PolicyAdviceAnswer answer = parser.parse(response);

        assertFalse(answer.structured());
        assertEquals(response, answer.raw());
        assertNull(answer.riskLevel());
        assertTrue(answer.options().isEmpty());
    }

    @Test
    @DisplayName("빈 응답도 원문 보존으로 처리한다")
    void handlesBlankResponse() {
        final PolicyAdviceAnswer answer = parser.parse("   ");

        assertFalse(answer.structured());
        assertNotNull(answer.options());
        assertTrue(answer.options().isEmpty());
    }

    @Test
    @DisplayName("위험도 이표기를 표준 4단계로 접는다")
    void normalizesRisk() {
        assertEquals("CRITICAL", PolicyAdviceAnswer.normalizeRisk("SEVERE"));
        assertEquals("HIGH", PolicyAdviceAnswer.normalizeRisk("major"));
        assertEquals("MEDIUM", PolicyAdviceAnswer.normalizeRisk("moderate"));
        assertEquals("LOW", PolicyAdviceAnswer.normalizeRisk("ok"));
        assertNull(PolicyAdviceAnswer.normalizeRisk("뭔가이상함"));
        assertNull(PolicyAdviceAnswer.normalizeRisk(null));
    }

    @Test
    @DisplayName("policy_advice 대신 advice/note 로 답해도 읽는다")
    void acceptsAlternativeAdviceKey() {
        assertEquals("핵심 조언",
                parser.parse("{\"advice\": \"핵심 조언\"}").policyAdvice());
        assertEquals("핵심 조언",
                parser.parse("{\"note\": \"핵심 조언\"}").policyAdvice());
    }

    // -------------------------------------------------------------------------
    //  PolicyAdviceOption
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("제목만 있고 실행 방법이 없으면 사용 불가로 본다")
    void optionNeedsApproach() {
        assertFalse(new PolicyAdviceOption("제목", "", "", "", false).isUsable());
        assertFalse(new PolicyAdviceOption("", "방법", "", "", false).isUsable());
        assertTrue(new PolicyAdviceOption("제목", "방법", "", "", false).isUsable());
    }

    // -------------------------------------------------------------------------
    //  PolicyAdvicePromptBuilder — 프롬프트 계약
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("시스템 지침에 등급 규칙과 JSON 스키마가 들어간다")
    void systemPromptCarriesRules() {
        final String system = PolicyAdvicePromptBuilder
                .build(null, emptyContext(), null, null)
                .get(0)
                .content();

        // 등급 규칙이 없으면 모델은 "왜 금지인지" 를 모른 채 일반론을 냅니다.
        assertTrue(system.contains("Confidential"), "등급 이름이 있어야 합니다");
        assertTrue(system.contains("레벨"), "등급 레벨 설명이 있어야 합니다");
        assertTrue(system.contains("차이"), "인접 규칙이 있어야 합니다");
        // 출력 형식이 없으면 화면이 카드로 분리할 수 없습니다.
        assertTrue(system.contains("\"options\""), "선택지 스키마가 있어야 합니다");
        assertTrue(system.contains("security_impact"), "보안 영향 필드가 있어야 합니다");
        assertTrue(system.contains("operational_cost"), "운영 부담 필드가 있어야 합니다");
        // 없는 자산을 가정하지 못하게 하는 지침
        assertTrue(system.contains("가정하지 마세요"));
    }

    @Test
    @DisplayName("사용자 지침의 추가 프롬프트가 시스템에 반영된다")
    void providerSystemPromptIsAppended() {
        final var provider = new org.sonar.sonarvalidator_backend.Model.entity.AiProvider();
        provider.setSystemPrompt("우리 조직은 군사 규정을 따릅니다.");

        final String system = PolicyAdvicePromptBuilder
                .build(provider, emptyContext(), null, null)
                .get(0)
                .content();

        assertTrue(system.contains("우리 조직은 군사 규정을 따릅니다."));
    }

    @Test
    @DisplayName("사용자 프롬프트에 서브넷 대장과 위반 목록이 들어간다")
    void userPromptCarriesContext() {
        final ViolationBrief focus = new ViolationBrief(
                "Rule-9001", "CRITICAL", "VLAN 131 ATICS", "VLAN 141 Web",
                "Confidential", "Open", 2, true,
                "10.10.131.5 -> 10.30.141.9 (all ports)", null,
                "등급 2단계 차이");

        final String user = PolicyAdvicePromptBuilder.buildUserPrompt(
                contextWith(List.of(focus), false, 1), focus, "군사 규정 관점에서");

        assertTrue(user.contains("VLAN 131 ATICS"), "서브넷 이름이 있어야 합니다");
        assertTrue(user.contains("10.10.131.0/24"), "대역이 있어야 합니다");
        assertTrue(user.contains("등급 차이 2"), "등급 차이가 있어야 합니다");
        assertTrue(user.contains("Rule-9001"), "규칙 ID 가 있어야 합니다");
        assertTrue(user.contains("질문 대상 위반"), "초점 위반이 별도로 표시되어야 합니다");
        assertTrue(user.contains("군사 규정 관점에서"), "추가 질문이 반영되어야 합니다");
    }

    @Test
    @DisplayName("위반이 잘렸으면 그 사실을 프롬프트에 알린다")
    void userPromptWarnsWhenTruncated() {
        // ⚠️ 일부만 보고 "전체가 이렇다" 고 단정하면 위험합니다.
        final ViolationBrief brief = new ViolationBrief(
                "Rule-1", "CRITICAL", "A", "B", "Confidential", "Open", 2, true,
                "10.0.0.1 -> 10.0.0.2", null, "사유");

        final String user = PolicyAdvicePromptBuilder.buildUserPrompt(
                contextTruncated(List.of(brief), 100), null, null);

        assertTrue(user.contains("전체 위반 100건 중"), "잘림 사실을 알려야 합니다");
        assertTrue(user.contains("남은 위반도 있다고 전제하세요"));
    }

    @Test
    @DisplayName("준수 상태면 평가 요청으로 바뀐다")
    void compliantContextAsksForAssessment() {
        // 위반 0건을 오류로 만들지 않고 "더 강화할 점이 있나" 로 전환합니다.
        final String user = PolicyAdvicePromptBuilder.buildUserPrompt(
                emptyContext(), null, null);

        assertTrue(user.contains("추가로 강화할 수 있는"));
        assertTrue(user.contains("위반이 없습니다") || user.contains("(탐지된 위반이 없습니다)"));
    }

    @Test
    @DisplayName("준수인데 위반 상세가 없으면 단정하지 말라고 알린다")
    void nonCompliantWithoutDetailsWarns() {
        // ⚠️ 이 상태에서 "위반 0건" 이라고 답하면 운영자는 위반을 놓칩니다.
        final String user = PolicyAdvicePromptBuilder.buildUserPrompt(
                contextWith(List.of(), false, 5), null, null);

        assertTrue(user.contains("위반이 없다고 단정하지 말고"),
                "목록이 비었지만 미준수인 경우를 구분해야 합니다");
    }

    // -------------------------------------------------------------------------
    //  PolicyAdviceContext — 잘림/정렬/매칭
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("심각도 순으로 정렬해 자른다 (MINOR 가 먼저 잘린다)")
    void sortsBySeverityBeforeTruncating() {
        final List<ViolationBrief> briefs = new java.util.ArrayList<>();
        // MINOR 부터 넣어도 정렬되어야 합니다.
        for (int index = 0; index < 50; index++) {
            briefs.add(new ViolationBrief("Minor-" + index, "MINOR", "A", "B",
                    "Open", "Open", 0, false, "p", null, "r"));
        }
        briefs.add(new ViolationBrief("Critical-1", "CRITICAL", "A", "B",
                "Confidential", "Open", 2, true, "p", null, "r"));

        final PolicyAdviceContext context = contextWith(briefs, false, briefs.size());

        // ⚠️ CRITICAL 이 잘려나가면 조언의 초점이 사라집니다.
        assertEquals(PolicyAdviceContext.MAX_BRIEFS, context.briefs().size());
        assertEquals("CRITICAL", context.briefs().get(0).severity(),
                "가장 심각한 것이 맨 앞에 남아야 합니다");
        assertTrue(context.truncated());
    }

    @Test
    @DisplayName("규칙+출발+도착으로 위반을 정확히 찾는다")
    void findsBriefByTriple() {
        // 같은 규칙이 여러 쌍을 위반하면 규칙만으로는 엉뚱한 건이 나옵니다.
        final ViolationBrief first = new ViolationBrief("Rule-1", "CRITICAL",
                "Subnet-A", "Subnet-B", "Confidential", "Open", 2, true, "p", null, "r");
        final ViolationBrief second = new ViolationBrief("Rule-1", "CRITICAL",
                "Subnet-A", "Subnet-C", "Confidential", "Open", 2, true, "p", null, "r");

        final PolicyAdviceContext context = contextWith(List.of(first, second), false, 2);

        assertEquals("Subnet-C",
                context.findBrief("Rule-1", "Subnet-A", "Subnet-C").dstLabel());
    }

    @Test
    @DisplayName("src/dst 없이 규칙만으로도 찾는다 (프론트가 안 보낸 경우)")
    void findsBriefByRuleOnly() {
        final ViolationBrief brief = new ViolationBrief("Rule-9", "MAJOR",
                "Subnet-A", "Subnet-B", "Sensitive", "Sensitive", 0, false, "p", null, "r");

        final PolicyAdviceContext context = contextWith(List.of(brief), false, 1);

        assertNotNull(context.findBrief("Rule-9", null, null));
        assertEquals("Subnet-B", context.findBrief("Rule-9", null, null).dstLabel());
    }

    @Test
    @DisplayName("없는 규칙을 물으면 null 을 돌려준다 (조용히 아무거나 고르지 않는다)")
    void unknownRuleYieldsNull() {
        final PolicyAdviceContext context = emptyContext();

        assertNull(context.findBrief("Rule-없음", null, null));
        assertNull(context.findBrief(null, null, null));
    }

    @Test
    @DisplayName("심각도별 건수를 센다")
    void countsBySeverity() {
        final PolicyAdviceContext context = contextWith(List.of(
                new ViolationBrief("A", "CRITICAL", "A", "B", "Confidential", "Open", 2, true, "p", null, "r"),
                new ViolationBrief("B", "CRITICAL", "A", "C", "Confidential", "Open", 2, true, "p", null, "r"),
                new ViolationBrief("C", "MAJOR", "A", "D", "Open", "Open", 0, false, "p", null, "r")),
                false, 3);

        assertEquals(2, context.bySeverity().get("CRITICAL"));
        assertEquals(1, context.bySeverity().get("MAJOR"));
        assertEquals(0, context.bySeverity().get("MINOR"));
    }

    // -------------------------------------------------------------------------
    //  헬퍼
    // -------------------------------------------------------------------------

    /** 위반이 없는 준수 맥락을 만듭니다. (Project 없이 최소 정보) */
    private PolicyAdviceContext emptyContext() {
        return contextWith(List.of(), true, 0);
    }

    /** 지정한 위반 목록으로 맥락을 만듭니다. */
    private PolicyAdviceContext contextWith(List<ViolationBrief> briefs,
                                            boolean compliant,
                                            int totalCount) {
        return contextFromBriefs(briefs, compliant, totalCount);
    }

    /** 잘림 시나리오용 맥락을 만듭니다. */
    private PolicyAdviceContext contextTruncated(List<ViolationBrief> briefs, int totalCount) {
        return contextFromBriefs(briefs, false, totalCount);
    }

    /**
     * 맥락을 직접 만듭니다.
     *
     * <p>{@link PolicyAdviceContext#of} 는 {@code Project}/{@code Report} 를
     * 요구하므로, 프롬프트 계약만 검증할 때는 리플렉션 없이 같은 상태를
     * 만들 수 없습니다. 그래서 <b>실제 생성 경로</b>를 쓰지 않고 여기서는
     * {@code of} 를 쓰지 않습니다 — 대신 검증 대상이 프롬프트라면
     * 위반 목록만 있으면 충분합니다. 이 헬퍼는 {@code of} 와 같은 규칙으로
     * 목록을 정렬/절단합니다.
     */
    private PolicyAdviceContext contextFromBriefs(List<ViolationBrief> briefs,
                                                  boolean compliant,
                                                  int totalCount) {
        // PolicyAdviceContext 의 생성자는 private 이므로 of() 를 통해야 합니다.
        // Project 없이 위반만 넣기 위해 실제로는 빈 프로젝트로 만듭니다.
        final org.sonar.sonarvalidator_backend.Model.entity.Project project =
                new org.sonar.sonarvalidator_backend.Model.entity.Project();
        project.setProjectKey("PRJ-TEST");
        project.setName("테스트 프로젝트");
        project.setCategory("Defense");
        project.setSubnets(new java.util.ArrayList<>());

        final var subnet = new org.sonar.sonarvalidator_backend.Model.entity.ProjectSubnet();
        subnet.setSubnetId("Subnet-A");
        subnet.setCidr("10.10.131.0/24");
        subnet.setName("VLAN 131 ATICS");
        subnet.setZoneClass(ZoneClass.CONFIDENTIAL);
        project.getSubnets().add(subnet);

        final var report = new org.sonar.sonarvalidator_backend.Policy.SegmentationBddEngine.Report();
        report.setRuleCount(9);
        report.setSubnetCount(8);
        report.setCompliant(compliant);
        report.setViolationCount(totalCount);

        // ⚠️ Report 의 violations 는 final 리스트라 setter 가 없습니다.
        //    getViolations().add(...) 로 채웁니다.
        for (final ViolationBrief brief : briefs) {
            report.getViolations().add(toViolation(brief));
        }

        return PolicyAdviceContext.of(project, report);
    }

    /** 테스트용 {@link ViolationBrief} 를 판정 엔진의 위반으로 되돌립니다. */
    private PolicyViolation toViolation(ViolationBrief brief) {
        final ZoneClass source = ZoneClass.fromString(brief.srcClass());
        final ZoneClass target = ZoneClass.fromString(brief.dstClass());
        return new PolicyViolation(
                brief.ruleId(), brief.srcLabel(), brief.dstLabel(),
                source, target,
                "10.0.0.1", "10.0.0.2",
                brief.port() == null ? PacketVariables.ANY_PORT : brief.port(),
                brief.reason(),
                PolicyViolation.Severity.valueOf(brief.severity()));
    }
}