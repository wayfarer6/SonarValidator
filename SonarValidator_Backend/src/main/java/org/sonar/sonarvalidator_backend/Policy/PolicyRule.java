package org.sonar.sonarvalidator_backend.Policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PolicyRule {

    public enum Origin {
        MANUAL,
        DISCOVERED
    }

    private String id;

    private String source;

    private String destination;

    private int port = PacketVariables.ANY_PORT;

    private String protocol = "tcp";

    private Origin origin = Origin.MANUAL;

    private boolean enabled = true;

    private String note;

    public PolicyRule() {
    }

    public PolicyRule(String id, String source, String destination, int port) {
        this.id = id;
        this.source = source;
        this.destination = destination;
        this.port = port;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Origin getOrigin() {
        return origin;
    }

    public void setOrigin(Origin origin) {
        this.origin = origin;
    }

    public boolean isManual() {
        return origin == Origin.MANUAL;
    }

    public void setManual(boolean manual) {
        this.origin = manual ? Origin.MANUAL : Origin.DISCOVERED;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean hasPort() {
        return port >= 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PolicyRule rule && Objects.equals(id, rule.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return id + ": " + source + " -> " + destination
                + (hasPort() ? (":" + port) : " (all ports)");
    }

    public static List<PolicyRule> copyOf(List<PolicyRule> rules) {
        return rules == null ? new ArrayList<>() : new ArrayList<>(rules);
    }
}
