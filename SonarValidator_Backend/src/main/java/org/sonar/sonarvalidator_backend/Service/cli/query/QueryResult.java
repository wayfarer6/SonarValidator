package org.sonar.sonarvalidator_backend.Service.cli.query;

import tools.jackson.databind.node.ObjectNode;

public record QueryResult(String query, String contractKey, ObjectNode body) {

    private static final String[] COUNT_KEYS = {
        "interface_count", "brief_count", "route_count", "entry_count", "vlan_count",
        "port_count", "bridge_count", "table_count",
    };

    private static final String[] ARRAY_KEYS = {
        "interfaces", "brief", "routes", "entries", "vlans", "ports",
        "bridges", "tables", "chains", "rules",
    };

    public boolean parsed() {
        return body != null && body.path("parsed").asBoolean(false);
    }

    public int itemCount() {
        if (body == null) {
            return 0;
        }
        for (final String key : COUNT_KEYS) {
            if (body.path(key).isNumber()) {
                return body.path(key).asInt(0);
            }
        }
        // 안전망: 개수 키가 없는 문법입니다.
        int total = 0;
        for (final String key : ARRAY_KEYS) {
            final tools.jackson.databind.JsonNode array = body.path(key);
            if (array.isArray()) {
                total += array.size();
            }
        }
        return total;
    }
}
