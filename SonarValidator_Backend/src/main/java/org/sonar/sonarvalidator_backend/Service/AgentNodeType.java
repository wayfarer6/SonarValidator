package org.sonar.sonarvalidator_backend.Service;

import java.util.List;
import java.util.Locale;

public enum AgentNodeType {

    ROUTER("Router", "Router",
            "라우터(FRR) 는 vtysh 로 정책을 적용합니다."),

    SWITCH("Switch", "Switch",
            """
            스위치(Open vSwitch) 는 L2 전용이라 관리망에만 붙습니다.
            관리 IP 가 없으면 서버에 도달하지 못합니다:

              ip link set eth11 up
              ip addr add 172.16.255.10N/24 dev eth11     # 스위치마다 다름
            """),

    FIREWALL("Firewall", "Firewall",
            """
            방화벽은 nftables 로 ACL 을 적용합니다.
            정책은 기본 `filter` 테이블을 건드리지 않고 `sonar` 테이블을 씁니다.

            ⚠️ 이 장치는 격리 대상이 아닙니다. eth1 트렁크로 여러 VLAN 을
               들고 있어 인터페이스를 내리면 무관한 존이 함께 끊깁니다.
            """),

    VM("VM", "VM",
            """
            VM(Ubuntu) 은 netplan 으로 주소를 설정합니다.
            netplan 이 없으면 프로버가 `ip addr` 로 대체 적용합니다.
            """);

    private static final List<String> ALIASES_EXTRA = List.of(
            "vm", "virtualmachine", "virtual-machine", "virtualmachine");

    private final String canonical;
    private final String label;
    private final String prepareNote;

    AgentNodeType(String canonical, String label, String prepareNote) {
        this.canonical = canonical;
        this.label = label;
        this.prepareNote = prepareNote;
    }

    public String canonical() {
        return canonical;
    }

    public String label() {
        return label;
    }

    public String prepareNote() {
        return prepareNote;
    }

    public boolean matches(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        final String lower = value.trim().toLowerCase(Locale.ROOT);
        if (canonical.toLowerCase(Locale.ROOT).equals(lower)) {
            return true;
        }
        // VM 은 이름이 여러 가지입니다. (VirtualMachine, Virtual Machine …)
        if (this == VM && ALIASES_EXTRA.contains(lower)) {
            return true;
        }
        return false;
    }

    public static AgentNodeType parse(String value) {
        for (final AgentNodeType type : values()) {
            if (type.matches(value)) {
                return type;
            }
        }
        return VM;
    }

    public static String canonicalOf(String value) {
        return parse(value).canonical();
    }

    public String fileToken() {
        return name();
    }
}
