package org.sonar.sonarvalidator_backend.Model;

//  어떤 벤더 및 OS 규격인지 판별하여 정의해 두는 열거형(Enum) 클래스
// 나머지 벤더는 나중에 지원
public enum ConfigurationFormat {
    CISCO_IOS,
    ARISTA_vEOS,
    OPNSense,
    AlpineFirewall,
    OpenvSwitch;

    /**
     * 문자열에서 설정 형식을 파싱합니다. (대소문자·구분자 관용)
     *
     * <p>파서가 남기는 형식 이름은 표기가 제각각입니다
     * ({@code cisco-ios}, {@code ARISTA_vEOS}, {@code arista-vEOS} …).
     * 비교를 호출부마다 반복하지 않도록 여기서 한 번만 흡수합니다.
     * 모르는 값은 {@code null} 로 돌려주어 호출자가 판단하게 합니다.
     *
     * @param value 형식 이름 (null/빈 문자열이면 null)
     * @return 대응하는 형식 (모르면 null)
     */
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
