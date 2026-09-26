package org.sonar.sonarvalidator_backend.Policy.advice;

import java.util.ArrayList;
import java.util.List;

import org.sonar.sonarvalidator_backend.Model.entity.AiProvider;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient.Message;

/**
 * 망분리 위반 <b>정책 조언 프롬프트</b>를 조립합니다.
 *
 * <h2>로그 분석 프롬프트와 무엇이 다른가</h2>
 * <p>{@code LogAnalysisEngine} 은 "로그에서 무슨 일이 있었나" 를 묻습니다 —
 * 관측된 사실의 원인 규명입니다. 이 프롬프트는 <b>"이 위반을 어떻게
 * 해결해야 하나"</b> 를 묻습니다 — 정책 설계 판단입니다. 그래서 요구하는
 * 출력이 다릅니다.
 *
 * <table border="1">
 *   <caption>두 프롬프트의 차이</caption>
 *   <tr><th></th><th>로그 분석</th><th>정책 조언</th></tr>
 *   <tr><td>질문</td><td>무슨 일이 있었나</td><td>무엇을 어떻게 고치나</td></tr>
 *   <tr><td>1차 근거</td><td>로그 원문</td><td>등급 체계 + 서브넷 구성</td></tr>
 *   <tr><td>출력</td><td>원인 · 권장 조치</td><td><b>선택지 비교 + 리스크</b></td></tr>
 *   <tr><td>금지</td><td>로그에 없는 원인 추측</td><td>"그냥 규칙 삭제" 류의 무책임한 조언</td></tr>
 * </table>
 *
 * <h2>⚠️ 프롬프트 설계에서 정한 4가지</h2>
 *
 * <h3>1. 선택지를 여러 개 주고 트레이드오프를 쓰게 한다</h3>
 * <p>"규칙을 삭제하세요" 하나만 나오면 운영자는 <b>다른 선택이 있었다는 사실을
 * 모른 채</b> 보안을 약화시킵니다. 등급 재조정 · 중계 구간 신설 · 규칙 세분화
 * 같은 대안을 함께 제시하게 하고, 각각의 <b>비용과 위험</b>을 명시하게 합니다.
 *
 * <h3>2. 등급 체계를 규칙으로 명시한다</h3>
 * <p>모델은 "Confidential 과 Open 이 왜 직접 연결하면 안 되는지" 를 모릅니다.
 * 등급 레벨과 인접 규칙(차이 ≤ 1 허용)을 프롬프트에 넣어야 <b>근거 있는</b>
 * 대안을 냅니다. 넣지 않으면 일반 보안론만 반복합니다.
 *
 * <h3>3. 없는 자산을 가정하지 못하게 한다</h3>
 * <p>모델은 "방화벽에서 NAT 하세요" 처럼 <b>토폴로지에 없는 장비</b>를
 * 전제로 조언하기 쉽습니다. 서브넷 대장을 함께 주고 "목록에 없는 장비를
 * 가정하지 말라" 를 명시합니다.
 *
 * <h3>4. 준수 상태도 물어볼 수 있게 한다</h3>
 * <p>위반이 0건일 때도 "지금 구성이 안전한가 / 개선할 점이 있나" 를 물을 수
 * 있어야 합니다. 그래서 위반 목록이 비어 있어도 프롬프트가 성립합니다.
 */
public final class PolicyAdvicePromptBuilder {

    /**
     * 시스템 지침입니다.
     *
     * <p>⚠️ 응답을 <b>JSON 으로 고정</b>합니다. 자유 서술이면 화면이 선택지를
     * 카드로 분리해 보여줄 수 없고, 운영자는 긴 문단에서 조치를 골라내야 합니다.
     */
    private static final String SYSTEM_INSTRUCTION = """
            당신은 망분리(CSO, Cross Domain Security) 정책을 설계·검토하는
            보안 아키텍트입니다. 군사·공공·금융의 망분리 규정과 등급 기반
            세그멘테이션 실무를 다룹니다.

            아래 프로젝트의 망분리 정책 위반을 분석하고, 운영자가 실제로
            실행할 수 있는 조언을 하세요.

            ## 이 시스템이 적용하는 등급 규칙 (고정)
            - 등급은 세 단계입니다.
              Confidential(레벨 3) · Sensitive(레벨 2) · Open(레벨 1)
            - 두 서브넷의 등급 레벨 차이가 1 이하면 직접 연결을 허용합니다.
            - 차이가 2 이상이면 한 단계를 건너뛰는 연결이므로 금지입니다.
              (예: Confidential(3) ↔ Open(1) 은 차이 2 → 금지)
            - 즉 등급 간 이동은 반드시 인접 등급을 거쳐야 합니다.
            - 위반은 이 규칙을 어기는 활성 규칙이 있을 때 발생합니다.
              (등급 오분류 / 규칙이 등급 규칙 위반 / 중간 등급 없는 직결 /
               포트 미지정으로 인한 정책 약화)

            반드시 지킬 규칙:

            1. 주어진 서브넷 목록에 없는 장비·대역·서비스를 가정하지 마세요.
               목록에 없는 것을 해결책에 넣어야 한다면 "새로 도입이 필요하다"고
               명시하세요.
            2. 위반의 근본 원인을 위 네 가지 중 어디에 해당하는지 먼저 판단하세요.
            3. 해결책은 최소 2개 이상 제시하고, 각각의 보안 영향과 운영 부담을
               함께 쓰세요. "규칙을 삭제" 같은 단일 제안만 하지 마세요.
            4. 보안을 약화시키는 조언(등급 강등, 금지 규칙 추가 없이 허용)은
               그 결과를 명시하고 대안을 함께 제시하세요.
            5. 근거가 부족하면 추측하지 말고, 무엇을 더 확인해야 하는지 쓰세요.
            6. 반드시 아래 JSON 형식으로만 답하세요. 설명 문장이나 코드블록을
               덧붙이지 마세요.

            {
              "risk_level": "CRITICAL | HIGH | MEDIUM | LOW",
              "summary": "전체 위반 상황 한두 문장 요약 (한국어)",
              "root_cause": "근본 원인 판단. 근거가 약하면 '근거 부족'이라 명시",
              "policy_advice": "정책 관점의 핵심 조언 (한 줄)",
              "options": [
                {
                  "title": "선택지 이름 (예: 등급 재분류)",
                  "approach": "구체적으로 무엇을 어떻게 바꾸는지",
                  "security_impact": "보안에 미치는 영향",
                  "operational_cost": "운영 부담 (재작업·중단·비용)",
                  "recommended": true
                }
              ],
              "evidence": ["판단 근거가 된 위반/서브넷 (최대 5개, 원문 인용)"],
              "needs_more_data": false
            }
            """;

    private PolicyAdvicePromptBuilder() {
        // 유틸리티 클래스는 인스턴스화하지 않습니다.
    }

    /**
     * 시스템 + 사용자 메시지를 만듭니다.
     *
     * @param provider AI 공급자 (추가 시스템 프롬프트가 있으면 반영)
     * @param context  판정 맥락
     * @param focus    사용자가 짚은 위반 (없으면 전체)
     * @param userPrompt 사용자 추가 질문 (선택)
     * @return 메시지 목록 (system → user)
     */
    public static List<Message> build(AiProvider provider,
                                      PolicyAdviceContext context,
                                      ViolationBrief focus,
                                      String userPrompt) {

        final List<Message> messages = new ArrayList<>();

        final StringBuilder system = new StringBuilder(SYSTEM_INSTRUCTION);
        if (provider != null
                && provider.getSystemPrompt() != null
                && !provider.getSystemPrompt().isBlank()) {
            system.append("\n추가 지침 (운영자가 설정):\n")
                    .append(provider.getSystemPrompt().trim())
                    .append('\n');
        }
        messages.add(Message.system(system.toString()));

        messages.add(Message.user(buildUserPrompt(context, focus, userPrompt)));
        return messages;
    }

    /**
     * 사용자 메시지 본문을 만듭니다.
     *
     * <p>public 인 이유: 이 메서드가 <b>프롬프트 계약</b> 그 자체입니다.
     * "등급 규칙이 들어가는가", "잘림을 알리는가" 는 조언 품질을 좌우하므로
     * 단위 테스트가 직접 검증해야 합니다. {@code build} 로 간접 검증하면
     * 시스템 지침과 섞여 어느 쪽이 빠졌는지 알기 어렵습니다.
     *
     * @param context 판정 맥락
     * @param focus   사용자가 짚은 위반 (없으면 전체 요약)
     * @param userPrompt 사용자 추가 질문
     * @return 본문
     */
    public static String buildUserPrompt(PolicyAdviceContext context,
                                         ViolationBrief focus,
                                         String userPrompt) {

        final StringBuilder user = new StringBuilder();

        // --- 1) 프로젝트 개요 ---------------------------------------------
        user.append("## 대상 프로젝트\n");
        user.append("- 이름: ").append(context.projectName()).append('\n');
        user.append("- 성격: ").append(context.category()).append('\n');
        user.append("- 규칙 ").append(context.ruleCount())
                .append("건, 서브넷 ").append(context.subnetCount()).append("건\n");
        user.append("- 준수 여부: ")
                .append(context.compliant() ? "준수 (위반 0건)" : "미준수")
                .append('\n');

        // --- 2) 등급 체계 --------------------------------------------------
        // ⚠️ 등급 규칙 <b>본문</b>은 시스템 지침에 있습니다. 사용자 메시지에
        //    같은 문단을 또 넣으면 토큰만 두 배로 쓰고 얻는 것이 없습니다.
        //    대신 "그 규칙으로 판정했다" 는 사실만 확인시켜 줍니다.
        user.append("\n## 판정 기준\n");
        user.append("- 시스템 지침의 등급 규칙(레벨 차이 2 이상 금지)으로 서버가 판정한 결과입니다.\n");
        user.append("- 아래 위반의 '등급 차이' 값이 그 판정 근거입니다.\n");

        // --- 3) 서브넷 대장 ------------------------------------------------
        user.append("\n## 서브넷 구성\n");
        if (context.subnetLines().isEmpty()) {
            user.append("(서브넷 정보 없음)\n");
        } else {
            for (final String line : context.subnetLines()) {
                user.append(line).append('\n');
            }
            if (context.subnetCount() > context.subnetLines().size()) {
                user.append("… (서브넷 ").append(context.subnetCount())
                        .append("건 중 ").append(context.subnetLines().size())
                        .append("건만 표시)\n");
            }
        }

        // --- 4) 사용자가 짚은 위반 (있으면 맨 앞에) -------------------------
        if (focus != null) {
            user.append("\n## 질문 대상 위반 (이 건을 중심으로 답하세요)\n");
            user.append(focus.describe()).append('\n');
        }

        // --- 5) 위반 목록 --------------------------------------------------
        user.append("\n## 탐지된 위반 (심각도 순)\n");
        if (context.briefs().isEmpty()) {
            user.append("(탐지된 위반이 없습니다)\n");
            if (!context.compliant()) {
                // ⚠️ 준수가 아닌데 목록이 비는 경우는 두 가지입니다.
                //    (a) 응답에 violations 가 실리지 않음 (b) 집계만 있고 목록 없음.
                //    이 사실을 모델에게 알려 "위반이 0건" 이라고 단정하지 않게 합니다.
                user.append("⚠️ 준수 상태가 아니라고 보고되었으나 위반 상세가 전달되지 "
                        + "않았습니다. 위반이 없다고 단정하지 말고, 상세 위반 내역이 "
                        + "필요하다고 답하세요.\n");
            }
        } else {
            user.append(context.briefsAsText()).append('\n');

            // ⚠️ 잘렸다는 사실을 반드시 알립니다.
            if (context.truncated()) {
                user.append("\n⚠️ 전체 위반 ").append(context.totalViolationCount())
                        .append("건 중 심각한 ").append(context.briefs().size())
                        .append("건만 제공했습니다. 전체를 보지 못했다는 점을 감안하고, "
                                + "남은 위반도 있다고 전제하세요.\n");
            }
        }

        // --- 6) 무엇을 답할지 ----------------------------------------------
        user.append("\n## 요청\n");
        if (context.compliant() && context.briefs().isEmpty() && focus == null) {
            user.append("현재 구성은 위반이 없습니다. 이 구성에서 추가로 강화할 수 있는 "
                    + "점과, 잠재적 위험(예: 포트 미지정, 과도한 허용)이 있는지 "
                    + "평가해 주세요.\n");
        } else if (focus != null) {
            user.append("위 '질문 대상 위반' 한 건에 대해, 왜 금지인지와 "
                    + "어떻게 해결할지 선택지 형태로 답해 주세요.\n");
        } else {
            user.append("위 위반들을 묶어 근본 원인을 판단하고, "
                    + "우선순위가 높은 것부터 해결 선택지를 제시해 주세요.\n");
        }

        if (userPrompt != null && !userPrompt.isBlank()) {
            user.append("\n추가로 답해 주세요: ").append(userPrompt.trim()).append('\n');
        }

        return user.toString();
    }
}