package org.sonar.sonarvalidator_backend.Policy.advice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 가 돌려준 <b>구조화된 정책 조언</b>입니다.
 *
 * <h2>⚠️ 파싱 실패를 오류로 처리하지 않는다</h2>
 * <p>모델은 코드블록({@code ```json … ```})으로 감싸거나 앞뒤에 설명을
 * 붙이는 일이 흔합니다. 그때 조언 자체를 버리면 운영자는 "조언 실패" 만
 * 보게 되는데, <b>원문에는 쓸 만한 내용이 들어 있습니다.</b> 그래서
 * 원문은 항상 남기고({@code raw}), 파싱에 성공한 경우에만 구조화 필드를
 * 채웁니다({@code structured=true}).
 *
 * <p>이 규칙은 {@code LogAnalysisEngine.parseStructured} 와 같습니다 —
 * 두 경로가 다르게 동작하면 사용자가 화면마다 다른 신뢰도를 갖게 됩니다.
 *
 * @param riskLevel     위험도 ({@code CRITICAL}/{@code HIGH}/{@code MEDIUM}/{@code LOW}, 미상이면 null)
 * @param summary       한두 문장 요약
 * @param rootCause     근본 원인 판단
 * @param policyAdvice  정책 관점 핵심 조언 (한 줄)
 * @param options       해결 선택지 목록
 * @param evidence      판단 근거가 된 위반/서브넷 인용
 * @param needsMoreData 모델이 추가 자료를 요청했는지
 * @param structured    구조화 파싱에 성공했는지
 * @param raw           모델 응답 원문 (파싱 실패 시에도 남음)
 */
public record PolicyAdviceAnswer(String riskLevel,
                                 String summary,
                                 String rootCause,
                                 String policyAdvice,
                                 List<PolicyAdviceOption> options,
                                 List<String> evidence,
                                 boolean needsMoreData,
                                 boolean structured,
                                 String raw) {

    /** 저장·응답에 남길 원문 최대 길이입니다. (컬럼 크기와 맞춤) */
    private static final int MAX_STORED_RAW = 30_000;

    /**
     * 파싱 실패(또는 빈 응답)를 나타내는 답을 만듭니다.
     *
     * @param raw 모델 원문
     * @return 구조화되지 않은 답
     */
    public static PolicyAdviceAnswer unstructured(String raw) {
        return new PolicyAdviceAnswer(null, null, null, null,
                List.of(), List.of(), false, false, raw);
    }

    /** @return 화면/저장에 쓸 형태로 바꾼 맵 */
    public Map<String, Object> toView() {
        final Map<String, Object> view = new LinkedHashMap<>();
        view.put("risk_level", riskLevel);
        view.put("summary", summary);
        view.put("root_cause", rootCause);
        view.put("policy_advice", policyAdvice);

        final List<Map<String, Object>> optionViews = new ArrayList<>();
        for (final PolicyAdviceOption option : options) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("title", option.title());
            entry.put("approach", option.approach());
            entry.put("security_impact", option.securityImpact());
            entry.put("operational_cost", option.operationalCost());
            entry.put("recommended", option.recommended());
            optionViews.add(entry);
        }
        view.put("options", optionViews);
        view.put("evidence", evidence);
        view.put("needs_more_data", needsMoreData);
        view.put("structured", structured);
        view.put("raw_response", raw);
        return view;
    }

    /** @return 원문을 저장 상한에 맞게 자른 값 */
    public String truncatedRaw() {
        if (raw == null) {
            return null;
        }
        return raw.length() <= MAX_STORED_RAW ? raw : raw.substring(0, MAX_STORED_RAW);
    }

    /**
     * 위험도를 표준 값으로 정규화합니다.
     *
     * <p>모델은 {@code SEVERE}, {@code MODERATE}, {@code WARN} 같은 표현을
     * 섞어 씁니다. 표준 4단계로 접어 화면 배지가 색을 정할 수 있게 합니다.
     * {@code LogAnalysisEngine} 과 <b>같은 규칙</b>을 씁니다.
     *
     * @param value 모델이 준 위험도 문자열
     * @return 표준 위험도, 알 수 없으면 null
     */
    public static String normalizeRisk(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String upper = value.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "CRITICAL", "SEVERE" -> "CRITICAL";
            case "HIGH", "MAJOR" -> "HIGH";
            case "MEDIUM", "MODERATE", "WARN", "WARNING" -> "MEDIUM";
            case "LOW", "INFO", "OK", "NONE" -> "LOW";
            default -> null;
        };
    }
}