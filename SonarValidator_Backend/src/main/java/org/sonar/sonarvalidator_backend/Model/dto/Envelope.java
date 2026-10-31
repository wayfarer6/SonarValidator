package org.sonar.sonarvalidator_backend.Model.dto;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;

public class Envelope {

    private String type = Types.UNKNOWN;

    private String agent_id;

    private String device_type;

    private String correlation_id;

    private JsonNode payload;

    private String error;

    public static final class Types {
        public static final String HELLO = "hello";
        public static final String POLICY_REQUEST = "policy-request";
        public static final String POLICY_RESPONSE = "policy-response";
        public static final String TELEMETRY = "telemetry";
        public static final String COMMAND = "command";
        public static final String ACK = "ack";
        public static final String ERROR = "error";
        public static final String UNKNOWN = "unknown";

        private Types() {
        }
    }

    public Envelope() {
    }

    public static Envelope of(String type) {
        final Envelope envelope = new Envelope();
        envelope.setType(type);
        return envelope;
    }

    public static Envelope reply(String type, String correlationId, JsonNode payload) {
        final Envelope envelope = of(type);
        envelope.setCorrelation_id(correlationId);
        envelope.setPayload(payload);
        return envelope;
    }

    public static Envelope replyTo(String type, Envelope request, JsonNode payload) {
        final Envelope envelope = reply(type, request == null ? null : request.getCorrelation_id(), payload);
        if (request != null) {
            envelope.setAgent_id(request.getAgent_id());
            envelope.setDevice_type(request.getDevice_type());
        }
        return envelope;
    }

    public static Envelope error(String correlationId, String message) {
        final Envelope envelope = of(Types.ERROR);
        envelope.setCorrelation_id(correlationId);
        envelope.setError(message);
        return envelope;
    }

    public JsonNode payloadOrEmpty() {
        if (payload == null || payload.isNull()) {
            return tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        }
        return payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = (type == null || type.isBlank()) ? Types.UNKNOWN : type;
    }

    public String getAgent_id() {
        return agent_id;
    }

    public void setAgent_id(String agent_id) {
        this.agent_id = agent_id;
    }

    public String getDevice_type() {
        return device_type;
    }

    public void setDevice_type(String device_type) {
        this.device_type = device_type;
    }

    public String getCorrelation_id() {
        return correlation_id;
    }

    public void setCorrelation_id(String correlation_id) {
        this.correlation_id = correlation_id;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String summary() {
        final Map<String, Object> parts = new LinkedHashMap<>();
        parts.put("type", type);
        parts.put("agent_id", agent_id);
        parts.put("device_type", device_type);
        parts.put("correlation_id", correlation_id);
        parts.put("error", error);
        return parts.toString();
    }

    @Override
    public String toString() {
        return "Envelope" + summary();
    }
}
