package org.sonar.sonarvalidator_backend.Model.Config;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * 벤더별 CLI 조회 결과를 <b>중립적인 장비 설정</b>으로 옮기는 계약입니다.
 *
 * <h2>배경 (Batfish 참고)</h2>
 * <p>Batfish 는 벤더 파서가 각자 {@code Configuration} 이라는 공통 구조
 * (인터페이스/VRF/ACL/라우팅/VLAN)를 채우고, 이후 분석은 벤더를 모른 채 그
 * 공통 구조만 본다는 설계를 씁니다. 이 프로젝트도 같은 방식을 따릅니다.
 *
 * <pre>
 *   Agent 텔레메트리(벤더 고유 JSON)
 *        │
 *        ▼
 *   DeviceConfigParser 구현체   ← CiscoRouter / AlpineFirewall / OpenVSwitch / LinuxVM / AristaSwitch
 *        │
 *        ▼
 *   NeutralDeviceConfig         ← 이후 분석·비교·저장은 전부 이 구조만 사용
 * </pre>
 *
 * <p>구현체는 <b>조회 결과만</b> 읽습니다. 설정을 바꾸거나 명령을 실행하지 않습니다.
 */
public interface DeviceConfigParser {

    /**
     * 이 파서가 처리할 수 있는 벤더인지 알려줍니다.
     *
     * @param   productName Agent 가 보고한 제품명 (예: {@code "FRR"}, {@code "OpenVSwitch"})
     * @param   deviceType  Agent 가 보고한 장치 유형
     * @return  처리 가능하면 {@code true}
     */
    boolean supports(String productName, String deviceType);

    /**
     * 텔레메트리 payload 를 중립 설정으로 변환합니다.
     *
     * <p>payload 는 Agent 의 {@code telemetry} 봉투 본문이며
     * {@code nic_status}, {@code route_status}, {@code vlan_status},
     * {@code trunk_status}, {@code arp_table}, {@code firewall_rules} 등의 키를
     * 선택적으로 담습니다. 없는 항목은 비워 둡니다.
     *
     * @param  hostname 표시용 장치 이름 (Agent 식별자)
     * @param  product  Agent 가 보고한 제품명
     * @param  payload  텔레메트리 payload (null 이면 빈 설정)
     * @return 중립 설정 (null 이 아님)
     */
    NeutralDeviceConfig parse(String hostname, String product, JsonNode payload);

    /**
     * 이 파서가 담당하는 설정 형식입니다. (저장/직렬화 시 벤더 식별자로 사용)
     *
     * @return 설정 형식
     */
    String format();

    /**
     * 파서가 중립 설정에서 다루는 항목 이름 목록입니다. (진단/문서용)
     *
     * @return 항목 이름 목록
     */
    default List<String> capabilities() {
        return List.of("interfaces", "routes", "vlans", "trunks");
    }
}
