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

class QuarantinePolicyOverrideTest {

    private final List<QuarantineState> rows = new ArrayList<>();
    private QuarantineService quarantineService;
    private PolicyProbe policyUnderTest;

    private static final class PolicyProbe {
        private final org.sonar.sonarvalidator_backend.Service.PolicyRegistryService delegate =
                new org.sonar.sonarvalidator_backend.Service.PolicyRegistryService();

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
                    case "findAllByAgentIdAndReleasedAtIsNull" -> rows.stream()
                            .filter(s -> args[0] != null && args[0].equals(s.getAgentId())
                                    && s.getReleasedAt() == null)
                            .toList();
                    case "findAllByNodeIdAndReleasedAtIsNull" -> rows.stream()
                            .filter(s -> args[0] != null && args[0].equals(s.getNodeId())
                                    && s.getReleasedAt() == null)
                            .toList();
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

        final var targets = policy.get("subnet_quarantine");
        assertEquals(1, targets.size());
        assertEquals("10.0.9.0/24", targets.get(0).get("cidr").asText());
        assertEquals(0, policy.get("policies").size(), "No nft command sent to an unrelated platform");

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

    @Test
    void independentTargetsSurvivePartialReleaseAndFinalReleaseIsExplicit() {
        policyUnderTest.delegate.setAutomaticEnforcementEnabled(false);
        seedQuarantine("switch", 7, QuarantineState.Scope.CONNECTION, "10.0.9.0/24");
        seedQuarantine("switch", 7, QuarantineState.Scope.CONNECTION, "10.0.10.0/24");
        assertEquals(2, policyUnderTest.forAgent(DeviceType.SWITCH, "switch").path("subnet_quarantine").size());
        rows.get(0).setReleasedAt(new Date());
        final var remaining = policyUnderTest.forAgent(DeviceType.SWITCH, "switch");
        assertEquals(1, remaining.path("subnet_quarantine").size());
        assertEquals("10.0.10.0/24", remaining.path("subnet_quarantine").get(0).path("cidr").asText());
        assertEquals(0, remaining.path("policies").size());
        rows.get(1).setReleasedAt(new Date());
        final var released = policyUnderTest.forAgent(DeviceType.SWITCH, "switch");
        assertTrue(released.path("subnet_quarantine").isArray());
        assertEquals(0, released.path("subnet_quarantine").size());
    }

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
