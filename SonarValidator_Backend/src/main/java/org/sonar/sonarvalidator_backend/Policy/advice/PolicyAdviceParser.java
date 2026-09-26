package org.sonar.sonarvalidator_backend.Policy.advice;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 응답을 {@link PolicyAdviceAnswer} 로 바꿉니다.
 *
 * <h2>⚠️ 왜 고쳐야 할 모양이 3가지인가 (실측)</h2>
 * <p>모델은 같은 프롬프트에도 세 가지 모양으로 답합니다. 파서가 하나만
 * 처리하면 나머지 둘은 "조언 실패" 로 보입니다.
 *
 * <ol>
 *   <li><b>순수 JSON</b> — 요청대로 답한 경우</li>
 *   <li><b>코드블록 감싼 JSON</b> — {@code ```json … ```} (가장 흔함)</li>
 *   <li><b>설명 + JSON 혼합</b> — "분석 결과입니다:" 뒤에 JSON</li>
 * </ol>
 *
 * <p>또한 <b>스키마 이탈</b>도 흔합니다.
 * <ul>
 *   <li>{@code options} 대신 {@code recommendations} 를 씀</li>
 *   <li>{@code options} 를 문자열 배열로 씀 (객체 배열이 아니라)</li>
 *   <li>{@code evidence} 를 문자열 하나로 씀 (배열이 아니라)</li>
 * </ul>
 * <p>이때 <b>버리지 않고 살릴 수 있는 만큼 살립니다.</b> 문자열 배열
 * {@code options} 는 제목만 있는 선택지로 승격시킵니다 — 운영자에게
 * "무엇을 고려해야 하는지" 라도 전달됩니다.
 *
 * <h2>분리한 이유</h2>
 * <p>이 로직은 <b>순수 함수</b>입니다. 네트워크·DB 가 없으므로 단위 테스트로
 * 위 세 모양과 스키마 이탈을 전부 검증할 수 있습니다.
 * ({@code PolicyAdviceParserTest}) 서비스에 섞여 있으면 그 검증이 불가능합니다.
 */
@Component
public class PolicyAdviceParser {

    /** 배열이 아니어도 배열로 승격시켜 읽을 키 이름들입니다. */
    private static final List<String> OPTION_KEYS = List.of("options", "recommendations", "solutions");

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 모델 응답을 구조화된 조언으로 바꿉니다.
     *
     * @param text 모델 응답 (null 허용)
     * @return 구조화된 조언, 파싱 실패 시 원문만 담은 결과
     */
    public PolicyAdviceAnswer parse(String text) {
        if (text == null || text.isBlank()) {
            return PolicyAdviceAnswer.unstructured(text);
        }

        final JsonNode root = tryParseJson(text);
        if (root == null || !root.isObject()) {
            // 파싱 실패: 원문만 남깁니다. 화면은 raw_response 를 그대로 보여줍니다.
            return PolicyAdviceAnswer.unstructured(text);
        }

        return new PolicyAdviceAnswer(
                PolicyAdviceAnswer.normalizeRisk(root.path("risk_level").asString("")),
                textOrNull(root, "summary"),
                textOrNull(root, "root_cause"),
                // 모델이 policy_advice 외에 advice/note 로 답하는 경우를 흡수합니다.
                firstText(root, "policy_advice", "advice", "note"),
                readOptions(root),
                readStringList(root.path("evidence")),
                root.path("needs_more_data").asBoolean(false),
                true,
                text);
    }

    /**
     * 응답에서 JSON 을 꺼냅니다.
     *
     * <p>모델이 코드블록으로 감싸는 경우가 흔하므로 그것을 먼저 벗겨냅니다.
     * 그다음 첫 {@code &#123;} 부터 마지막 {@code &#125;} 까지를 시도합니다.
     *
     * @param text 모델 응답
     * @return 파싱된 JSON (실패 시 null)
     */
    JsonNode tryParseJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String candidate = text.trim();

        // ```json … ``` 또는 ``` … ``` 를 벗깁니다.
        if (candidate.startsWith("```")) {
            final int firstNewline = candidate.indexOf('\n');
            if (firstNewline > 0) {
                candidate = candidate.substring(firstNewline + 1);
            }
            final int closing = candidate.lastIndexOf("```");
            if (closing >= 0) {
                candidate = candidate.substring(0, closing);
            }
            candidate = candidate.trim();
        }

        // 앞뒤에 설명이 붙은 경우: 첫 { 부터 마지막 } 까지를 시도합니다.
        if (!candidate.startsWith("{")) {
            final int start = candidate.indexOf('{');
            final int end = candidate.lastIndexOf('}');
            if (start >= 0 && end > start) {
                candidate = candidate.substring(start, end + 1);
            }
        }

        try {
            return mapper.readTree(candidate);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 선택지 목록을 읽습니다.
     *
     * <p>⚠️ 두 모양을 모두 받습니다.
     * <ul>
     *   <li>객체 배열 — 정상 (title/approach/… 를 채움)</li>
     *   <li>문자열 배열 — 스키마 이탈. 제목만 있는 선택지로 <b>승격</b>시킵니다.
     *       버리면 조언의 절반이 사라집니다.</li>
     * </ul>
     *
     * @param root 응답 JSON
     * @return 사용 가능한 선택지 목록 (빈 값 항목은 제외)
     */
    private List<PolicyAdviceOption> readOptions(JsonNode root) {
        final List<PolicyAdviceOption> options = new ArrayList<>();

        for (final String key : OPTION_KEYS) {
            final JsonNode node = root.path(key);
            if (!node.isArray()) {
                continue;
            }
            for (final JsonNode item : node) {
                if (item.isObject()) {
                    final PolicyAdviceOption option = new PolicyAdviceOption(
                            item.path("title").asString(""),
                            item.path("approach").asString(""),
                            item.path("security_impact").asString(""),
                            item.path("operational_cost").asString(""),
                            item.path("recommended").asBoolean(false));
                    // ⚠️ 제목/실행방법이 빈 카드는 화면에서 "내용 없는 조언" 이 됩니다.
                    if (option.isUsable()) {
                        options.add(option);
                    }
                } else if (item.isTextual()) {
                    // 스키마 이탈: 문자열을 제목으로 승격합니다.
                    final String title = item.asString("").trim();
                    if (!title.isEmpty()) {
                        options.add(new PolicyAdviceOption(title, title, "", "", false));
                    }
                }
            }
            // 첫 번째로 발견한 키만 씁니다. 여러 키가 있으면 중복이 생깁니다.
            if (!options.isEmpty()) {
                break;
            }
        }
        return options;
    }

    /**
     * 문자열 배열을 읽습니다. 문자열 하나로 온 경우도 배열로 승격합니다.
     *
     * @param node 배열 또는 문자열 노드
     * @return 문자열 목록
     */
    private List<String> readStringList(JsonNode node) {
        final List<String> values = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return values;
        }

        if (node.isArray()) {
            for (final JsonNode item : node) {
                final String value = item.asString("");
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
            return values;
        }

        // 스키마 이탈: 문자열 하나로 온 경우
        if (node.isTextual()) {
            final String value = node.asString("").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    /** 키의 문자열 값을 돌려줍니다. 비어 있으면 null. */
    private String textOrNull(JsonNode root, String key) {
        final String value = root.path(key).asString("");
        return value.isBlank() ? null : value;
    }

    /** 여러 키 중 처음으로 값이 있는 것을 돌려줍니다. */
    private String firstText(JsonNode root, String... keys) {
        for (final String key : keys) {
            final String value = textOrNull(root, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}