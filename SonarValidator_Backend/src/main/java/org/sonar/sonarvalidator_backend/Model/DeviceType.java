package org.sonar.sonarvalidator_backend.Model;

/**
 * Agent(장치) 유형.
 *
 * C++ Prober 의 {@code SonarValidator_Prober/device_type.hpp}
 * ({@code enum class DeviceType { kSwitch, kVirtualMachine, kFirewall, kRouter }}) 와
 * 1:1 로 대응합니다. JSON 에서는 소문자 이름 대신 문서에서 합의한 약어
 * ("SWITCH", "ROUTER", "FIREWALL", "VM") 를 사용합니다.
 */
public enum DeviceType {
    SWITCH,
    ROUTER,
    FIREWALL,
    VM;

    /**
     * 대소문자를 구분하지 않고 문자열을 파싱합니다.
     * 문서 합의안의 "VM" 과 C++ 쪽 이름 "VirtualMachine" 을 모두 허용합니다.
     *
     * @param value 파싱할 값 (null 이면 null 반환)
     * @return 대응하는 {@link DeviceType}, 알 수 없으면 null
     */
    public static DeviceType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase()) {
            case "SWITCH" -> SWITCH;
            case "ROUTER" -> ROUTER;
            case "FIREWALL" -> FIREWALL;
            case "VM", "VIRTUALMACHINE", "VIRTUAL_MACHINE" -> VM;
            default -> null;
        };
    }

    /**
     * 장치 식별자 접두사에서 유형을 추론합니다.
     *
     * <p>C++ 하네스와 모크 브로커는 {@code {"device_id":"vm-01"}} 처럼 유형이
     * 드러나는 id 를 보냅니다. {@link #fromString(String)}  이 값은
     * {@code "VM-01"} 이라 어떤 상수와도 일치하지 않으므로 접두사 기반 추론이
     * 필요합니다.
     *
     * @param deviceId 장치 식별자 (예: {@code vm-01}, {@code switch-3})
     * @return 추론된 유형, 알 수 없으면 {@link #VM}
     */
    public static DeviceType inferFromDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return VM;
        }
        final String upper = deviceId.trim().toUpperCase();
        if (upper.startsWith("VIRTUAL") || upper.startsWith("VM")) {
            return VM;
        }
        if (upper.startsWith("SWITCH") || upper.startsWith("SW")) {
            return SWITCH;
        }
        if (upper.startsWith("ROUTER") || upper.startsWith("RT")) {
            return ROUTER;
        }
        if (upper.startsWith("FIREWALL") || upper.startsWith("FW")) {
            return FIREWALL;
        }
        return VM;
    }
}
