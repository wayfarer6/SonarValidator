package org.sonar.sonarvalidator_backend.Model;

//  어떤 벤더 및 OS 규격인지 판별하여 정의해 두는 열거형(Enum) 클래스
// 나머지 벤더는 나중에 지원
public enum ConfigurationFormat {
    CISCO_IOS,
    ARISTA_vEOS,
    OPNSense,
    AlpineFirewall,
    OpenvSwitch;

    public static ConfigurationFormat fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // 하이픈·공백·점을 밑줄로 통일해 비교합니다.
        final String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .replace('.', '_')
                .toUpperCase(java.util.Locale.ROOT);
        for (final ConfigurationFormat format : values()) {
            if (format.name().toUpperCase(java.util.Locale.ROOT).equals(normalized)) {
                return format;
            }
        }
        return switch (normalized) {
            case "CISCO", "IOS", "CISCO_IOS_XE", "IOS_XE" -> CISCO_IOS;
            case "ARISTA", "ARISTA_VEOS", "VEOS", "EOS" -> ARISTA_vEOS;
            case "OPNSENSE", "OPN_SENSE" -> OPNSense;
            case "ALPINE", "ALPINE_FIREWALL" -> AlpineFirewall;
            case "OVS", "OPEN_VSWITCH", "OPENVSWITCH" -> OpenvSwitch;
            default -> null;
        };
    }
}
