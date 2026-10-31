package org.sonar.sonarvalidator_backend.Model.Config;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

public final class JsonReader {

    private JsonReader() {
    }

    public static JsonNode at(JsonNode parent, String field) {
        if (parent == null || !parent.isObject()) {
            return null;
        }
        final JsonNode value = parent.get(field);
        return (value == null || value.isNull()) ? null : value;
    }

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

    public static String text(JsonNode parent, String field) {
        return text(at(parent, field));
    }

    public static String textOr(JsonNode parent, String field, String fallback) {
        final String value = text(parent, field);
        return (value == null || value.isBlank()) ? fallback : value;
    }

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
