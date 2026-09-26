package org.sonar.sonarvalidator_backend.Service;

import java.util.List;
import java.util.Locale;

/**
 * Agent 번들에 담을 장치 유형별 <b>배포 지식</b>입니다.
 *
 * <h2>⚠️ 왜 전략 패턴인가</h2>
 * <p>{@code AgentBundleService} 안에 {@code nodeType} 문자열 {@code switch} 가
 * <b>두 곳</b> 있었습니다.
 * <pre>
 *   normalizeNodeType()  switch (nodeType)  → "Router"/"Switch"/"Firewall"/"VM"
 *   readme()             switch (nodeType)  → 장치별 준비 절차 문장
 * </pre>
 * 게다가 프로버가 기대하는 표기({@code Router})와 정책 서비스가 쓰는 표기
 * ({@code ROUTER})가 달라, 비교할 때마다 {@code equalsIgnoreCase} 가 필요했습니다.
 *
 * <p>이제 표기 변환은 {@link #matches} 한 곳에서 끝나고, "무엇을 준비해야
 * 하는가" 는 {@link #prepareNote} 가 소유합니다. 유형을 추가하려면 이
 * enum 에 상수 하나를 더합니다 — {@code switch} 를 찾아다닐 필요가 없습니다.
 *
 * <h2>⚠️ 미지의 유형을 VM 으로 두는 이유</h2>
 * <p>번들은 <b>운영자가 지금 무언가를 설치하려는</b> 상황에서 만들어집니다.
 * 유형을 모른다고 설치를 거부하면 운영자는 아무것도 못 합니다. VM 안내가
 * 가장 일반적이고(리눅스 컨테이너/가상머신), 그마저 틀렸다면 README 의
 * 다른 절(설정 배치·실행)은 여전히 유효합니다.
 */
public enum AgentNodeType {

    /** 라우터 (Alpine + FRR). */
    ROUTER("Router", "Router",
            "라우터(FRR) 는 vtysh 로 정책을 적용합니다."),

    /** 스위치 (Open vSwitch). */
    SWITCH("Switch", "Switch",
            """
            스위치(Open vSwitch) 는 L2 전용이라 관리망에만 붙습니다.
            관리 IP 가 없으면 서버에 도달하지 못합니다:

              ip link set eth11 up
              ip addr add 172.16.255.10N/24 dev eth11     # 스위치마다 다름
            """),

    /** 방화벽 (nftables). */
    FIREWALL("Firewall", "Firewall",
            """
            방화벽은 nftables 로 ACL 을 적용합니다.
            정책은 기본 `filter` 테이블을 건드리지 않고 `sonar` 테이블을 씁니다.

            ⚠️ 이 장치는 격리 대상이 아닙니다. eth1 트렁크로 여러 VLAN 을
               들고 있어 인터페이스를 내리면 무관한 존이 함께 끊깁니다.
            """),

    /** 가상머신 (Ubuntu). */
    VM("VM", "VM",
            """
            VM(Ubuntu) 은 netplan 으로 주소를 설정합니다.
            netplan 이 없으면 프로버가 `ip addr` 로 대체 적용합니다.
            """);

    /** 번들 인자로 받아들이는 이름들 (대소문자 구분 없음). */
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

    /** @return 프로버·번들이 기대하는 표기 (예: {@code Router}) */
    public String canonical() {
        return canonical;
    }

    /** @return 화면 표시 이름 */
    public String label() {
        return label;
    }

    /** @return README 에 들어갈 장치별 준비 절차 */
    public String prepareNote() {
        return prepareNote;
    }

    /**
     * 입력 문자열이 이 유형인지 봅니다.
     *
     * <h2>⚠️ 대소문자와 표기를 모두 흡수한다</h2>
     * <p>입력 경로가 여러 곳입니다.
     * <ul>
     *   <li>프론트엔드 선택 상자 → {@code "Router"}</li>
     *   <li>정책 서비스의 장치 유형 → {@code "ROUTER"}</li>
     *   <li>운영자 입력 → {@code "router"}, {@code " Router "}</li>
     * </ul>
     * 비교마다 {@code equalsIgnoreCase} 를 반복하지 않도록 여기서 정규화합니다.
     *
     * @param value 입력 (null 허용)
     * @return 이 유형이면 true
     */
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

    /**
     * 입력을 유형으로 해석합니다.
     *
     * <p>모르는 값·빈 값은 {@link #VM} 입니다. (클래스 주석의 이유 참고)
     *
     * @param value 입력 (null 허용)
     * @return 유형 (절대 null 이 아님)
     */
    public static AgentNodeType parse(String value) {
        for (final AgentNodeType type : values()) {
            if (type.matches(value)) {
                return type;
            }
        }
        return VM;
    }

    /**
     * 입력을 프로버가 기대하는 표기로 바꿉니다.
     *
     * @param value 입력 (null 허용)
     * @return {@code Router} / {@code Switch} / {@code VM} / {@code Firewall}
     */
    public static String canonicalOf(String value) {
        return parse(value).canonical();
    }

    /**
     * 번들 파일 이름에 쓸 접미사를 돌려줍니다.
     *
     * <p>파일 이름에 유형이 들어가야 운영자가 여러 번들을 내려받았을 때
     * 구분할 수 있습니다.
     *
     * @return 표기 (예: {@code ROUTER})
     */
    public String fileToken() {
        return name();
    }
}