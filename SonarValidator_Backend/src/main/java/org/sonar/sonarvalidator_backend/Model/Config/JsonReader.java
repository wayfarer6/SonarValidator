package org.sonar.sonarvalidator_backend.Model.Config;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * Agent 텔레메트리 JSON 을 안전하게 읽는 헬퍼입니다.
 *
 * <h2>왜 직접 파싱하는가</h2>
 * <p>Jackson 3({@code tools.jackson})에서는 스칼라 접근자 이름이 바뀌었고
 * ({@code asText} → {@code asString}, {@code booleanValue} 등), Agent 가 보내는
 * 값은 숫자가 문자열로 오는 경우가 흔합니다(예: {@code "mtu":"1500"}). 여기서는
 * 모든 스칼라를 문자열로 정규화한 뒤 필요한 타입으로 변환해, 어떤 표현이 와도
 * 예외 없이 값을 얻도록 합니다.
 *
 * <p>수집 경로에서는 예외가 곧 텔레메트리 유실이므로 <b>절대 예외를 던지지 않고</b>
 * 값이 없으면 {@code null} 을 돌려줍니다.
 */
public final class JsonReader {

    private JsonReader() {
    }

    /**
     * 객체에서 필드를 꺼냅니다. 없거나 null 이면 null.
     *
     * @param parent 객체 노드 (null 허용)
     * @param field  필드 이름
     * @return 필드 노드 또는 null
     */
    public static JsonNode at(JsonNode parent, String field) {
        if (parent == null || !parent.isObject()) {
            return null;
        }
        final JsonNode value = parent.get(field);
        return (value == null || value.isNull()) ? null : value;
    }

    /**
     * 스칼라를 문자열로 읽습니다. 숫자/불리언도 문자열로 변환합니다.
     * (배열/객체는 대상이 아니며 그 경우 null)
     *
     * @param node 값 노드
     * @return 문자열 또는 null
     */
    public static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asString();
        }
        if (node.isArray() || node.isObject()) {
            return null;
        }
        return node.toString();
    }

    /**
     * 객체의 필드를 문자열로 읽습니다.
     *
     * @param parent 객체 노드
     * @param field  필드 이름
     * @return 문자열 또는 null
     */
    public static String text(JsonNode parent, String field) {
        return text(at(parent, field));
    }

    /**
     * 객체의 필드를 읽고, 없으면 {@code fallback} 을 돌려줍니다.
     *
     * @param parent   객체 노드
     * @param field    필드 이름
     * @param fallback 대체 값
     * @return 값 또는 fallback
     */
    public static String textOr(JsonNode parent, String field, String fallback) {
        final String value = text(parent, field);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * 정수를 읽습니다. 문자열 숫자도 허용합니다.
     *
     * @param parent 객체 노드
     * @param field  필드 이름
     * @return 정수 또는 null
     */
    public static Integer integer(JsonNode parent, String field) {
        final String raw = text(parent, field);
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 불리언을 읽습니다. {@code true/1} 을 참으로 봅니다.
     *
     * @param parent 객체 노드
     * @param field  필드 이름
     * @return 불리언 또는 null (값이 없을 때)
     */
    public static Boolean bool(JsonNode parent, String field) {
        final String raw = text(parent, field);
        if (raw == null) {
            return null;
        }
        final String value = raw.trim().toLowerCase();
        if ("true".equals(value) || "1".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value) || "0".equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * 배열을 문자열 목록으로 읽습니다. 배열이 아니면 빈 목록입니다.
     *
     * @param parent 객체 노드
     * @param field  필드 이름
     * @return 문자열 목록 (null 이 아님)
     */
    public static List<String> textList(JsonNode parent, String field) {
        final List<String> items = new ArrayList<>();
        final JsonNode array = at(parent, field);
        if (array == null || !array.isArray()) {
            return items;
        }
        for (final JsonNode item : array) {
            final String value = text(item);
            if (value != null) {
                items.add(value);
            }
        }
        return items;
    }

    /**
     * 배열을 정수 목록으로 읽습니다. (예: trunk_vlans: [111,112])
     *
     * @param parent 객체 노드
     * @param field  필드 이름
     * @return 정수 목록 (null 이 아님)
     */
    public static List<Integer> intList(JsonNode parent, String field) {
        final List<Integer> items = new ArrayList<>();
        final JsonNode array = at(parent, field);
        if (array == null || !array.isArray()) {
            return items;
        }
        for (final JsonNode item : array) {
            final String raw = text(item);
            if (raw == null) {
                continue;
            }
            try {
                items.add(Integer.valueOf(raw.trim()));
            } catch (NumberFormatException ignored) {
                // 숫자가 아닌 항목은 건너뜁니다.
            }
        }
        return items;
    }

    /**
     * 배열 필드를 순회합니다. 배열이 아니면 빈 목록입니다.
     *
     * @param parent 객체 노드
     * @param field  필드 이름
     * @return 자식 노드 목록 (null 이 아님)
     */
    public static List<JsonNode> objects(JsonNode parent, String field) {
        final List<JsonNode> items = new ArrayList<>();
        final JsonNode array = at(parent, field);
        if (array == null || !array.isArray()) {
            return items;
        }
        for (final JsonNode item : array) {
            if (item != null && item.isObject()) {
                items.add(item);
            }
        }
        return items;
    }
}
