package org.sonar.sonarvalidator_backend.Service.cli;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

public final class CliJson {

    public static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private CliJson() {
    }

    public static ObjectNode object() {
        return JSON.objectNode();
    }

    public static ArrayNode array() {
        return JSON.arrayNode();
    }

    public static ObjectNode attachParseInfo(ObjectNode body, ParseSession<?, ?> session) {
        body.put("parsed", session.errorCount() == 0);
        if (session.errorCount() > 0) {
            body.put("parse_warnings", session.errorCount());
            body.put("parse_error", session.errorMessage());
        }
        return body;
    }

    public static ObjectNode failure(String raw, String error) {
        final ObjectNode failure = object();
        failure.put("parsed", false);
        failure.put("parse_error", error == null ? "unknown error" : error);
        failure.put("raw", raw == null ? "" : raw);
        return failure;
    }
}
