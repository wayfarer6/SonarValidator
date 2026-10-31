package org.sonar.sonarvalidator_backend.Policy;

import java.util.Locale;

public enum ZoneClass {

    OPEN("Open", 1),

    SENSITIVE("Sensitive", 2),

    CONFIDENTIAL("Confidential", 3);

    private final String label;
    private final int level;

    ZoneClass(String label, int level) {
        this.label = label;
        this.level = level;
    }

    public String label() {
        return label;
    }

    public int level() {
        return level;
    }

    public static ZoneClass fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (final ZoneClass zone : values()) {
            if (zone.name().equals(normalized) || zone.label.toUpperCase(Locale.ROOT).equals(normalized)) {
                return zone;
            }
        }
        return null;
    }

    public static boolean allowsDirectConnection(ZoneClass source, ZoneClass target) {
        if (source == null || target == null) {
            return true;
        }
        return Math.abs(source.level - target.level) <= 1;
    }

    public static boolean forbidsDirectConnection(ZoneClass source, ZoneClass target) {
        return !allowsDirectConnection(source, target);
    }
}
