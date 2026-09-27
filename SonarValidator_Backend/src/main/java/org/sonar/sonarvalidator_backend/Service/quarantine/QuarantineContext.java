package org.sonar.sonarvalidator_backend.Service.quarantine;

import java.util.List;

import org.sonar.sonarvalidator_backend.Model.DeviceType;

/**
 * 격리 전략이 판단에 쓰는 <b>읽기 전용 입력</b>입니다.
 *
 * <h2>⚠️ 파라미터를 묶는 이유</h2>
 * <p>{@code QuarantineService} 가 전략에 넘길 값이 늘어났습니다
 * (노드 번호·Agent 식별자·서브넷 CIDR·프로젝트). 각 메서드에 흩어 넘기면
 * 순서 실수가 생기므로 한 객체로 묶습니다.
 *
 * <h2>agentId 가 null 일 수 있다</h2>
 * <p>OPNsense 처럼 REST API 전용 장비는 Agent 가 없습니다. 전략은
 * {@link #hasAgent()} 로 분기해야 하며, {@code agentId} 를 무조건
 * 문자열 결합에 쓰면 안 됩니다.
 *
 * @param nodeId      노드 번호 ({@code configuration.node_id}, 없으면 null)
 * @param agentId     Agent 식별자 (REST 전용 장비는 null)
 * @param deviceType  장치 유형 (null 이면 미상)
 * @param projectKey  프로젝트 키 (없으면 null)
 * @param subnetCidr  격리하려는 서브넷 CIDR (연결 단위 격리 시)
 * @param managementPrefix 관리(제어평면) 대역 CIDR (예: {@code 172.16.255.0/24})
 */
public record QuarantineContext(
        Integer nodeId,
        String agentId,
        DeviceType deviceType,
        String projectKey,
        String subnetCidr,
        String managementPrefix) {

    /**
     * Agent 가 있는 장치인지 확인합니다.
     *
     * @return Agent 식별자가 있으면 true
     */
    public boolean hasAgent() {
        return agentId != null && !agentId.isBlank();
    }

    /**
     * 연결 단위 격리 대상이 있는지 확인합니다.
     *
     * @return 서브넷 CIDR 이 있으면 true
     */
    public boolean hasSubnet() {
        return subnetCidr != null && !subnetCidr.isBlank();
    }

    /**
     * 제어평면 대역이 지정되어 있는지 확인합니다.
     *
     * @return 관리 대역 CIDR 이 있으면 true
     */
    public boolean hasManagementPrefix() {
        return managementPrefix != null && !managementPrefix.isBlank();
    }

    /**
     * 장치 유형을 사람이 읽는 이름으로 돌려줍니다.
     *
     * @return 유형 이름 (미상이면 {@code "UNKNOWN"})
     */
    public String deviceTypeName() {
        return deviceType == null ? "UNKNOWN" : deviceType.name();
    }

    /**
     * 경고 문장에 쓸 대상 이름을 만듭니다.
     *
     * <p>Agent 가 있으면 그것을, 없으면 노드 번호를 씁니다. OPNsense 처럼
     * Agent 가 없는 장비에서 {@code "null"} 이 노출되지 않게 하기 위함입니다.
     *
     * @return 표시용 이름
     */
    public String displayName() {
        if (hasAgent()) {
            return agentId;
        }
        return nodeId == null ? "unknown" : ("node-" + nodeId);
    }

    /**
     * 경고 문장 목록을 만들 때 쓰는 헬퍼입니다.
     *
     * @param items 문장 목록
     * @return 그대로 돌려줍니다 (null 이면 빈 목록)
     */
    public static List<String> nonNull(List<String> items) {
        return items == null ? List.of() : items;
    }
}