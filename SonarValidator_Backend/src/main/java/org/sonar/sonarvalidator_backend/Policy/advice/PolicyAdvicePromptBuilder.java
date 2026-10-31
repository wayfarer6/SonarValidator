package org.sonar.sonarvalidator_backend.Policy.advice;

import java.util.ArrayList;
import java.util.List;

import org.sonar.sonarvalidator_backend.Model.entity.AiProvider;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient.Message;

public final class PolicyAdvicePromptBuilder {

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

    public static String buildUserPrompt(PolicyAdviceContext context,
                                         ViolationBrief focus,
                                         String userPrompt) {

        final StringBuilder user = new StringBuilder();

        user.append("## 대상 프로젝트\n");
        user.append("- 이름: ").append(context.projectName()).append('\n');
        user.append("- 성격: ").append(context.category()).append('\n');
        user.append("- 규칙 ").append(context.ruleCount())
                .append("건, 서브넷 ").append(context.subnetCount()).append("건\n");
        user.append("- 준수 여부: ")
                .append(context.compliant() ? "준수 (위반 0건)" : "미준수")
                .append('\n');

        user.append("\n## 판정 기준\n");
        user.append("- 시스템 지침의 등급 규칙(레벨 차이 2 이상 금지)으로 서버가 판정한 결과입니다.\n");
        user.append("- 아래 위반의 '등급 차이' 값이 그 판정 근거입니다.\n");

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

        if (focus != null) {
            user.append("\n## 질문 대상 위반 (이 건을 중심으로 답하세요)\n");
            user.append(focus.describe()).append('\n');
        }

        user.append("\n## 탐지된 위반 (심각도 순)\n");
        if (context.briefs().isEmpty()) {
            user.append("(탐지된 위반이 없습니다)\n");
            if (!context.compliant()) {



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
