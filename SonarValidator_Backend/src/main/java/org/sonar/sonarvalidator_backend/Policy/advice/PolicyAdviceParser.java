package org.sonar.sonarvalidator_backend.Policy.advice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class PolicyAdviceParser {

    private static final List<String> OPTION_KEYS = List.of("options", "recommendations", "solutions");

    private final ObjectMapper mapper = new ObjectMapper();

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
                text, readCommands(root.path("command_proposals")));
    }

    public List<AdviceCommandProposal> readCommands(JsonNode node) {
        List<AdviceCommandProposal> commands = new ArrayList<>();
        if (!node.isArray()) return commands;
        for (JsonNode item : node) {
            if (commands.size() >= 12) break;
            if (!item.isObject()) continue;
            String agent = item.path("agent_id").asString("");
            if (agent.isBlank() || agent.length() > 120) continue;
            Map<String, Object> args = new java.util.TreeMap<>();
            JsonNode values = item.path("arguments");
            if (values.isObject() && values.size() <= 6) values.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (value.isString() && value.asString().length() <= 128) args.put(entry.getKey(), value.asString());
                else if (value.isIntegralNumber() && value.canConvertToInt()) args.put(entry.getKey(), value.asInt());
                else args.put("_invalid_argument", "Unsupported argument value");
            });
            if (!values.isObject() || values.size() > 6) args.put("_invalid_argument", "Arguments must be a bounded object");
            List<String> raw = readStringList(item.has("commands") ? item.path("commands") : item.path("suggested_commands")).stream().limit(12).map(v -> v.substring(0, Math.min(240, v.length()))).toList();
            commands.add(new AdviceCommandProposal(agent, bounded(item, "title", 160), bounded(item, "reason", 1000),
                    bounded(item, "operation", 80), Map.copyOf(args), raw));
        }
        return List.copyOf(commands);
    }
    private static String bounded(JsonNode node, String key, int max) {
        String value = node.path(key).asString("");
        return value.substring(0, Math.min(max, value.length()));
    }

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

    private String textOrNull(JsonNode root, String key) {
        final String value = root.path(key).asString("");
        return value.isBlank() ? null : value;
    }

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
