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

        // ⚠️ 접두사뿐 아니라 <b>이름의 끝</b>도 봅니다.
        //   랩의 장치는 이름이 역할로 끝납니다 — Gateway-Router,
        //   Survillance-Network-Router, DMZ-Router, VDI-Router, GNS3.Firewall.
        //   접두사만 보면 이것들은 전부 VM 으로 추론되어, 라우터 화면에
        //   VM 이 표시되고 유형별 정책 분기도 어긋납니다.
        //   (실측: 배포한 라우터 5대가 전부 VM 으로 나왔습니다)
        //
        //   구분자는 하이픈만이 아닙니다. 컨테이너 이름은 점을 씁니다
        //   (GNS3.Firewall). 그래서 마지막 토큰을 뽑아 비교합니다.
        final int cut = Math.max(
                Math.max(upper.lastIndexOf('-'), upper.lastIndexOf('.')),
                Math.max(upper.lastIndexOf('_'), upper.lastIndexOf('/')));
        final String last = (cut < 0) ? "" : upper.substring(cut + 1);

        if ("ROUTER".equals(last)) {
            return ROUTER;
        }
        if ("SWITCH".equals(last)) {
            return SWITCH;
        }
        if ("FIREWALL".equals(last)) {
            return FIREWALL;
        }

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
