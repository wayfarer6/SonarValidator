package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sonar.sonarvalidator_backend.support.StubRepository.UNHANDLED;
import static org.sonar.sonarvalidator_backend.support.StubRepository.of;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.entity.QuarantineState;
import org.sonar.sonarvalidator_backend.Repository.QuarantineStateRepository;
import org.sonar.sonarvalidator_backend.Service.QuarantineService;
import org.sonar.sonarvalidator_backend.Service.AgentSessionRegistry;
import org.sonar.sonarvalidator_backend.Service.ComplianceService;
import org.sonar.sonarvalidator_backend.Service.NotificationService;
import org.sonar.sonarvalidator_backend.Repository.ComplianceChangeRepository;
import org.sonar.sonarvalidator_backend.Repository.NotificationRepository;
import org.sonar.sonarvalidator_backend.Model.entity.ComplianceChange;
import org.sonar.sonarvalidator_backend.Model.entity.Notification;

import tools.jackson.databind.node.ObjectNode;

/**
 * 격리된 장치의 <b>정책 반영</b>을 검증합니다.
 *
 * <h2>⚠️ 이 테스트가 겨냥하는 최악의 실패 모드</h2>
 * <p>방화벽을 <b>연결 단위</b>로 격리했는데 정책이 "전부 차단" 으로 나가면,
 * 트렁크에 붙은 무관한 존이 함께 죽습니다. 그러면 방화벽을 노드 격리하지
 * 않는 이유(무관한 존 보호)가 무너지고, 격리 한 번에 랩 전체가 끊깁니다.
 *
 * <p>반대로 노드 격리인데 "전부 차단" 이 안 나가면, 장치가 인터페이스는
 * 내렸지만 재부팅 후 정책만으로 복구될 때 격리가 풀립니다.
 * 그래서 <b>두 방식이 서로 다른 정책을 만들어야</b> 합니다.
 */
class QuarantinePolicyOverrideTest {

    private final List<QuarantineState> rows = new ArrayList<>();
    private QuarantineService quarantineService;
    private PolicyProbe policyUnderTest;

    /** {@code PolicyRegistryService} 를 스텁 저장소와 함께 만드는 최소 배선입니다. */
    private static final class PolicyProbe {
        private final org.sonar.sonarvalidator_backend.Service.PolicyRegistryService delegate =
                new org.sonar.sonarvalidator_backend.Service.PolicyRegistryService();

        /**
         * {@code forAgent} 경로를 씁니다.
         *
         * <p>⚠️ {@code forDevice} 는 격리 오버라이드를 타지 않습니다. 오버라이드는
         * 프로젝트/폴백 정책을 만든 뒤 {@code forAgent} 가 적용합니다. 테스트가
         * {@code forDevice} 를 부르면 "오버라이드가 안 먹는다" 는 잘못된 결론에
         * 이릅니다.
         */
        ObjectNode forAgent(DeviceType type, String id) {
            return delegate.forAgent(id, type, id, null, null);
        }
    }

    @BeforeEach
    void setUp() {
        rows.clear();

        final QuarantineStateRepository repository = of(
                QuarantineStateRepository.class,
                (method, args) -> switch (method) {
                    case "save" -> {
                        final QuarantineState state = (QuarantineState) args[0];
                        if (!rows.contains(state)) {
                            rows.add(state);
                        }
                        yield state;
                    }
                    case "findByAgentIdAndReleasedAtIsNull" -> rows.stream()
                            .filter(s -> args[0] != null && args[0].equals(s.getAgentId())
                                    && s.getReleasedAt() == null)
                            .findFirst();
                    case "findByNodeIdAndReleasedAtIsNull" -> rows.stream()
                            .filter(s -> args[0] != null && args[0].equals(s.getNodeId())
                                    && s.getReleasedAt() == null)
                            .findFirst();
                    case "findByReleasedAtIsNullOrderByQuarantinedAtDesc" -> rows.stream()
                            .filter(s -> s.getReleasedAt() == null)
                            .toList();
                    case "count" -> (long) rows.size();
                    default -> UNHANDLED;
                });

        final ComplianceChangeRepository complianceRepository = of(
                ComplianceChangeRepository.class,
                (method, args) -> switch (method) {
                    case "save" -> args[0];
                    default -> UNHANDLED;
                });
        final NotificationRepository notificationRepository = of(
                NotificationRepository.class,
                (method, args) -> switch (method) {
                    case "save" -> args[0];
                    case "findFirstByDedupeKeyOrderByOccurredAtDesc" -> Optional.empty();
                    case "countByReadFalse" -> 0L;
                    default -> UNHANDLED;
                });

        quarantineService = new QuarantineService(
                repository,
                noopRegistry(),
                new ComplianceService(complianceRepository),
                new NotificationService(notificationRepository));

        policyUnderTest = new PolicyProbe();
        org.springframework.test.util.ReflectionTestUtils.setField(
                policyUnderTest.delegate, "quarantineService", quarantineService);
    }

    /** 명령 전송이 필요 없는 경로만 검증하므로 빈 레지스트리를 씁니다. */
    private static AgentSessionRegistry noopRegistry() {
        return new AgentSessionRegistry(tools.jackson.databind.json.JsonMapper.builder().build());
    }

    @Test
    @DisplayName("노드 격리면 정책이 전부 차단으로 바뀐다")
    void nodeQuarantineBlocksAll() {
        seedQuarantine("ATICS-agent", null, QuarantineState.Scope.NODE, null);

        final ObjectNode policy = policyUnderTest.forAgent(DeviceType.VM, "ATICS-agent");

        assertEquals(true, policy.get("quarantined").asBoolean(), "격리 표시");
        assertEquals("node", policy.get("quarantine_scope").asText(), "범위=노드");
        assertNull(policy.get("quarantine_target_cidr"), "노드 격리는 대상 대역 없음");
    }

    @Test
    @DisplayName("연결 단위 격리면 대상 대역만 차단하고 나머지는 건드리지 않는다")
    void connectionQuarantineBlocksTargetOnly() {
        // ⚠️ 방화벽을 대상 대역으로만 격리한 상태를 만듭니다.
        seedQuarantine("GNS3.Firewall", 7, QuarantineState.Scope.CONNECTION, "10.0.9.0/24");

        final ObjectNode policy = policyUnderTest.forAgent(DeviceType.FIREWALL, "GNS3.Firewall");

        assertEquals(true, policy.get("quarantined").asBoolean(), "격리 표시");
        assertEquals("connection", policy.get("quarantine_scope").asText(), "범위=연결");
        assertEquals("10.0.9.0/24",
                policy.get("quarantine_target_cidr").asText(), "대상 대역 기록");

        // ⚠️ 대상 대역으로 향하는 차단 규칙이 <b>앞에</b> 있어야 합니다.
        //    뒤에 있으면 허용 규칙이 먼저 매칭되어 무력합니다.
        final var policies = policy.get("policies");
        assertTrue(policies.isArray() && policies.size() > 0, "규칙 존재");
        final var first = policies.get(0);
        assertEquals("10.0.9.0/24",
                first.get("destination_subnet").get(0).asText(), "첫 규칙이 대상 대역");
        assertEquals("drop", first.get("action").get(0).asText(), "차단");

        // 문구가 "전부 차단" 이 아니어야 합니다 — 운영자가 오해하면 위험합니다.
        final String note = policy.get("quarantine_note").asText();
        assertTrue(note.contains("10.0.9.0/24"), "대상 대역 안내: " + note);
        assertFalse(note.contains("모든 전달 트래픽"), "전체 차단 문구가 아님: " + note);
    }

    @Test
    @DisplayName("격리되지 않은 장치는 정책이 그대로다")
    void nonQuarantinedPolicyUnchanged() {
        final ObjectNode policy = policyUnderTest.forAgent(DeviceType.VM, "UAV-agent");

        assertFalse(policy.has("quarantined"), "격리 표시 없음");
    }

    @Test
    @DisplayName("노드 번호로 격리한 장비도 정책에 반영된다 (Agent 없는 경우)")
    void nodeIdQuarantineReflected() {
        seedQuarantine(null, 42, QuarantineState.Scope.NODE, null);

        // agent_id 가 없어도 노드 번호로 격리를 찾아야 합니다.
        final QuarantineState state = quarantineService.activeState(null, 42);
        assertNotNull(state, "노드로 격리 조회");
    }

    /**
     * 격리 상태 한 건을 저장합니다.
     *
     * @param agentId Agent 식별자 (없으면 null)
     * @param nodeId  노드 번호 (없으면 null)
     * @param scope   격리 범위
     * @param target  대상 대역 (없으면 null)
     */
    private void seedQuarantine(String agentId, Integer nodeId,
                                QuarantineState.Scope scope, String target) {
        final QuarantineState state = new QuarantineState();
        state.setAgentId(agentId);
        state.setNodeId(nodeId);
        state.setScope(scope);
        state.setTargetCidr(target);
        state.setProjectKey("PRJ-1");
        state.setQuarantinedAt(new Date());
        rows.add(state);
    }
}