package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Service.ExpectedAgentService;

/**
 * 배포 예정/관측 통합 현황의 계약을 검증합니다.
 *
 * <h2>⚠️ 이 테스트가 잡는 실제 버그 2건</h2>
 * <p>PoC 랩에 라우터 5대를 실제로 배포한 뒤 화면에서 발견했습니다.
 * <ol>
 *   <li><b>이름이 소문자로 바뀜</b> — 예정에 없는 Agent 의 {@code agent_id} 가
 *       내부 매칭 키(소문자)로 덮어써져, 배포 스크립트에 적은
 *       {@code Gateway-Router} 가 {@code gateway-router} 로 보였습니다.
 *       그러면 프로젝트 서브넷의 {@code agent_id} 와 매칭되지 않아
 *       <b>정책이 기본값으로 떨어집니다.</b></li>
 *   <li><b>장치 유형이 null</b> — 예정에 없으면 {@code device_type} 이 비어
 *       화면에 유형을 알 수 없었고, 유형별 정책 분기도 불가능했습니다.</li>
 * </ol>
 *
 * <p>두 번째는 {@link DeviceType#inferFromDeviceId} 가 이름의 <b>끝</b>을
 * 보지 않던 문제와 겹칩니다. 랩 장치는 이름이 역할로 끝납니다
 * ({@code Gateway-Router}, {@code Survillance-Network-Router}).
 */
class ExpectedAgentOverviewTest {

    // ------------------------------------------------------------------
    //  이름 대소문자 보존
    // ------------------------------------------------------------------

    @Test
    @DisplayName("예정에 없는 Agent 도 원본 대소문자를 유지한다")
    void unregisteredAgentKeepsOriginalCasing() {
        // 랩 실제 이름입니다. (배포 스크립트가 넣은 값)
        final Set<String> connected = Set.of("Gateway-Router", "C4I-Network-Router");

        final Map<String, Map<String, Object>> overview =
                ExpectedAgentService.overview(List.of(), connected, Set.of());

        final List<Object> ids = overview.values().stream()
                .map(entry -> entry.get("agent_id"))
                .toList();

        assertTrue(ids.contains("Gateway-Router"),
                "원본 대소문자가 유지되어야 합니다 (gateway-router 가 아니라)");
        assertTrue(ids.contains("C4I-Network-Router"), "하이픈 포함 이름도 그대로");
        assertFalse(ids.contains("gateway-router"), "소문자로 덮어쓰면 안 됩니다");
    }

    @Test
    @DisplayName("대소문자만 다른 중복은 한 줄로 합쳐진다")
    void caseVariantsMergeIntoOneRow() {
        // Agent 는 hello 에서 'VDI-1-agent', 텔레메트리에서 'vdi-1-agent' 를
        // 보낼 수 있습니다. 두 줄로 보이면 운영자가 같은 장비를 두 번 격리합니다.
        final Map<String, Map<String, Object>> overview = ExpectedAgentService.overview(
                List.of(), Set.of("VDI-1-agent"), Set.of("vdi-1-agent"));

        assertEquals(1, overview.size(), "같은 장치는 한 줄");
        final Map<String, Object> entry = overview.values().iterator().next();
        assertEquals("VDI-1-agent", entry.get("agent_id"),
                "먼저 본 표기(연결 목록 우선)를 씁니다");
        assertEquals(Boolean.TRUE, entry.get("connected"));
        assertEquals(Boolean.TRUE, entry.get("telemetry_seen"));
    }

    @Test
    @DisplayName("예정에 등록된 Agent 는 예정 목록의 표기를 쓴다")
    void expectedAgentUsesRegisteredCasing() {
        final ExpectedAgent expected = new ExpectedAgent();
        expected.setAgentId("ATICS-agent");
        expected.setProjectKey("poc-dai-pbl");
        expected.setDeviceType("VM");

        final Map<String, Map<String, Object>> overview = ExpectedAgentService.overview(
                List.of(expected), Set.of("atics-agent"), Set.of());

        assertEquals(1, overview.size());
        final Map<String, Object> entry = overview.values().iterator().next();
        assertEquals("ATICS-agent", entry.get("agent_id"), "등록된 표기 유지");
        assertEquals(Boolean.TRUE, entry.get("connected"), "소문자 연결도 매칭됨");
        assertEquals(Boolean.TRUE, entry.get("expected"));
    }

    // ------------------------------------------------------------------
    //  장치 유형
    // ------------------------------------------------------------------

    @Test
    @DisplayName("예정에 없는 Agent 도 식별자에서 유형을 추론한다")
    void unregisteredAgentGetsInferredDeviceType() {
        final Map<String, Map<String, Object>> overview = ExpectedAgentService.overview(
                List.of(),
                Set.of("Gateway-Router", "Switch-1", "Firewall", "TOD-Cam"),
                Set.of());

        assertEquals("ROUTER", typeOf(overview, "Gateway-Router"));
        assertEquals("SWITCH", typeOf(overview, "Switch-1"));
        assertEquals("FIREWALL", typeOf(overview, "Firewall"));
        // 이름에 역할이 없으면 VM 으로 둡니다. (모르는 것을 지어내지 않음)
        assertEquals("VM", typeOf(overview, "TOD-Cam"));
    }

    @Test
    @DisplayName("inferFromDeviceId 는 이름의 끝도 본다")
    void inferFromDeviceIdChecksSuffix() {
        // 랩 라우터의 실제 이름들입니다. 접두사만 보면 전부 VM 이 됩니다.
        assertEquals(DeviceType.ROUTER, DeviceType.inferFromDeviceId("Gateway-Router"));
        assertEquals(DeviceType.ROUTER, DeviceType.inferFromDeviceId("DMZ-Router"));
        assertEquals(DeviceType.ROUTER, DeviceType.inferFromDeviceId("VDI-Router"));
        assertEquals(DeviceType.ROUTER,
                DeviceType.inferFromDeviceId("Survillance-Network-Router"));
        assertEquals(DeviceType.ROUTER, DeviceType.inferFromDeviceId("C4I-Network-Router"));

        assertEquals(DeviceType.SWITCH, DeviceType.inferFromDeviceId("Switch-0"));
        assertEquals(DeviceType.FIREWALL, DeviceType.inferFromDeviceId("GNS3.Firewall"));
        assertEquals(DeviceType.SWITCH, DeviceType.inferFromDeviceId("br0-switch"));

        // 접두사 규칙도 그대로 동작해야 합니다. (기존 동작 보존)
        assertEquals(DeviceType.VM, DeviceType.inferFromDeviceId("vm-01"));
        assertEquals(DeviceType.SWITCH, DeviceType.inferFromDeviceId("sw-01"));
        assertEquals(DeviceType.ROUTER, DeviceType.inferFromDeviceId("rt-01"));
        assertEquals(DeviceType.FIREWALL, DeviceType.inferFromDeviceId("fw-01"));

        // 모르면 VM, null/빈 값에도 안전
        assertEquals(DeviceType.VM, DeviceType.inferFromDeviceId("TOD-Cam"));
        assertEquals(DeviceType.VM, DeviceType.inferFromDeviceId(null));
        assertEquals(DeviceType.VM, DeviceType.inferFromDeviceId("  "));
    }

    // ------------------------------------------------------------------
    //  집계 일관성
    // ------------------------------------------------------------------

    @Test
    @DisplayName("connected/silent 집계가 행 상태와 일치한다")
    void countsMatchRowStates() {
        final ExpectedAgent silent = new ExpectedAgent();
        silent.setAgentId("KNCCS-agent");
        silent.setDeviceType("VM");

        final Map<String, Object> body = ExpectedAgentService.toResponse(
                List.of(silent),
                Set.of("Gateway-Router"),
                Set.of("Gateway-Router"));

        assertEquals(2, body.get("total"), "예정 1 + 관측 1");
        assertEquals(1, body.get("expected_total"));
        assertEquals(1, body.get("connected"), "Gateway-Router 만 연결");
        assertEquals(1, body.get("silent"), "KNCCS 는 무응답");

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> agents =
                (List<Map<String, Object>>) body.get("agents");
        assertEquals(2, agents.size());
        for (final Map<String, Object> entry : agents) {
            assertNotNull(entry.get("agent_id"));
            assertNotNull(entry.get("device_type"), "모든 행에 유형이 있어야 합니다");
        }
    }

    @Test
    @DisplayName("빈 입력에도 안전하다")
    void emptyInputIsSafe() {
        final Map<String, Map<String, Object>> overview = ExpectedAgentService.overview(
                List.of(), Set.of(), Set.of());
        assertTrue(overview.isEmpty());

        final Map<String, Object> body = ExpectedAgentService.toResponse(
                List.of(), Set.of(), Set.of());
        assertEquals(0, body.get("total"));
        assertEquals(0, body.get("connected"));
        assertEquals(0, body.get("silent"));
    }

    /** @return 해당 식별자의 device_type 값 */
    private static Object typeOf(Map<String, Map<String, Object>> overview, String agentId) {
        return overview.values().stream()
                .filter(entry -> agentId.equals(entry.get("agent_id")))
                .map(entry -> entry.get("device_type"))
                .findFirst()
                .orElse(null);
    }
}