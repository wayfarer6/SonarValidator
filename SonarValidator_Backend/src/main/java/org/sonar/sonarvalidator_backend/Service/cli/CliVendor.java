package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.Locale;

public enum CliVendor {

    OPEN_VSWITCH("OpenVSwitch"),

    FRR("FRR"),

    CISCO("Cisco"),

    ARISTA("Arista"),

    NFTABLES("nftables"),

    LINUX("Ubuntu"),

    UNKNOWN("Unknown");

    private final String displayName;

    CliVendor(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isLinuxHost() {
        return this == LINUX || this == FRR || this == NFTABLES || this == OPEN_VSWITCH;
    }

    public static CliVendor fromProductName(String productName) {
        final String name = productName == null ? "" : productName;
        if (name.contains("Cisco")) {
            return CISCO;
        }
        if (name.contains("Arista")) {
            return ARISTA;
        }
        if (name.contains("FRR")) {
            return FRR;
        }
        if (name.contains("OpenVSwitch") || name.contains("Open vSwitch")) {
            return OPEN_VSWITCH;
        }
        if (name.contains("nftables") || name.contains("nft")) {
            return NFTABLES;
        }
        if (name.contains("Ubuntu") || name.contains("Linux")) {
            return LINUX;
        }
        return UNKNOWN;
    }

    public static CliVendor parse(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        final String upper = value.trim().toUpperCase(Locale.ROOT);
        for (final CliVendor candidate : values()) {
            if (candidate.name().equals(upper)) {
                return candidate;
            }
        }
        // 이름이 아니면 제품명 규칙으로 한 번 더 시도합니다.
        // ("Open vSwitch 3.3" 처럼 이름+버전이 섞여 오는 경우)
        return fromProductName(value);
    }
}
