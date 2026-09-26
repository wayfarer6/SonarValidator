package org.sonar.sonarvalidator_backend.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.sonar.sonarvalidator_backend.Model.DeviceType;
import org.sonar.sonarvalidator_backend.Model.entity.ExpectedAgent;
import org.sonar.sonarvalidator_backend.Repository.ExpectedAgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배포 예정 Agent 목록의 등록·조회·삭제와, <b>기대 대비 실제</b> 비교를 담당합니다.
 *
 * <h2>핵심 아이디어</h2>
 * <p>UI 에 보이는 목록은 다음 세 집합의 합집합입니다.
 * <pre>
 *   예정(ExpectedAgent)  ∪  연결됨(WebSocket 세션)  ∪  텔레메트리 도착(lastSeen)
 * </pre>
 * 세 집합을 각각 따로 보여주면 "왜 목록에 있는데 연결은 없지" 를 운영자가
 * 매번 머릿속으로 계산해야 합니다. 그래서 하나의 목록으로 합치고
 * {@code state} 한 글자로 구분합니다.
 *
 * <h2>대소문자 처리</h2>
 * <p>Agent 식별자는 사람이 적어 넣는 값이라 {@code VDI-1} / {@code vdi-1} 이
 * 섞입니다. 저장은 입력 그대로 하되(로그 대조가 쉬워야 함) <b>비교는 관대하게</b>
 * 합니다. 그래서 여기서 만든 색인은 소문자 키를 씁니다.
 */
@Service
public class ExpectedAgentService {

    private static final Logger log = LoggerFactory.getLogger(ExpectedAgentService.class);

    private final ExpectedAgentRepository repository;

    /**
     * @param repository 배포 예정 저장소
     */
    public ExpectedAgentService(ExpectedAgentRepository repository) {
        this.repository = repository;
    }

    /**
     * 배포 예정 Agent 를 등록하거나 갱신합니다.
     *
     * <p>같은 식별자가 이미 있으면 <b>덮어씁니다</b>. 현장에서는 같은 장치를
     * 여러 번 배포하므로, 두 번째 배포가 "이미 있습니다" 로 실패하면
     * 운영자는 다른 이름을 지어내게 됩니다. 그러면 목록이 거짓말을 시작합니다.
     *
     * @param agentId    Agent 식별자 (필수)
     * @param projectKey 프로젝트 키 (없으면 null)
     * @param deviceType 장치 유형 문자열 (없으면 ID 로 추론)
     * @param nodeType   배포 노드 유형 (없으면 VM)
     * @param expectedIp 관리 주소 (없으면 null)
     * @param note       메모 (없으면 null)
     * @return 저장된 배포 예정 정보
     */
    @Transactional
    public ExpectedAgent register(String agentId,
                                  String projectKey,
                                  String deviceType,
                                  String nodeType,
                                  String expectedIp,
                                  String note) {
        final String id = (agentId == null) ? "" : agentId.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("agent_id is required");
        }

        final Date now = new Date();
        final ExpectedAgent entity = repository.findByAgentId(id).orElseGet(ExpectedAgent::new);

        entity.setAgentId(id);
        entity.setProjectKey(projectKey);
        entity.setDeviceType(resolveDeviceType(deviceType, id));
        entity.setNodeType((nodeType == null || nodeType.isBlank()) ? "VM" : nodeType);
        entity.setExpectedIp(expectedIp);
        if (note != null) {
            entity.setNote(note);
        }
        entity.setStatus("Deployed");
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);

        final ExpectedAgent saved = repository.save(entity);
        log.info("expected agent registered: id={} project={} type={}", id, projectKey, saved.getDeviceType());
        return saved;
    }

    /**
     * 배포 예정 목록을 최신순으로 조회합니다.
     *
     * @param projectKey 프로젝트 키 (null/빈 값이면 전체)
     * @return 배포 예정 목록
     */
    @Transactional(readOnly = true)
    public List<ExpectedAgent> list(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            return repository.findAllByOrderByCreatedAtDesc();
        }
        return repository.findByProjectKeyOrderByCreatedAtDesc(projectKey);
    }

    /**
     * 배포 예정 항목을 삭제합니다.
     *
     * @param agentId Agent 식별자
     */
    @Transactional
    public void delete(String agentId) {
        repository.findByAgentId(agentId).ifPresent(entity -> {
            repository.delete(entity);
            log.info("expected agent removed: id={}", agentId);
        });
    }

    /**
     * 프로젝트에 속한 배포 예정 항목을 모두 지웁니다. (프로젝트 삭제 경로)
     *
     * @param projectKey 프로젝트 키
     */
    @Transactional
    public void deleteByProject(String projectKey) {
        repository.deleteByProjectKey(projectKey);
    }

    /**
     * 배포 예정 + 실제 연결 + 텔레메트리를 하나의 목록으로 합칩니다.
     *
     * @param expected       배포 예정 목록
     * @param connectedIds   현재 WebSocket 으로 연결된 식별자
     * @param telemetryIds   텔레메트리를 보낸 적 있는 식별자
     * @return {@code agent_id} → 상태 항목 맵 (입력 순서 유지)
     */
    public static Map<String, Map<String, Object>> overview(Collection<ExpectedAgent> expected,
                                                            Collection<String> connectedIds,
                                                            Collection<String> telemetryIds) {
        final Set<String> connected = lowerCaseSet(connectedIds);
        final Set<String> seen = lowerCaseSet(telemetryIds);

        final Map<String, Map<String, Object>> result = new java.util.LinkedHashMap<>();

        for (final ExpectedAgent item : expected) {
            final Map<String, Object> entry = new java.util.LinkedHashMap<>();
            final String key = lower(item.getAgentId());
            entry.put("agent_id", item.getAgentId());
            entry.put("project_id", item.getProjectKey());
            entry.put("device_type", item.getDeviceType());
            entry.put("node_type", item.getNodeType());
            entry.put("expected_ip", item.getExpectedIp());
            entry.put("registered_at", item.getCreatedAt());
            entry.put("connected", connected.contains(key));
            entry.put("telemetry_seen", seen.contains(key));
            entry.put("expected", true);
            entry.put("state", stateOf(connected.contains(key), seen.contains(key), true));
            result.put(key, entry);
        }

        // 예정에 없는데 붙어 온 Agent 도 숨기지 않습니다.
        // (수동 실행/오프라인 반입 경로로 들어온 Agent 가 여기에 해당합니다)
        //
        // ⚠️ 원본 대소문자를 보존합니다.
        //   이 분기에서만 키(소문자)를 agent_id 로 내보내면, 예정에 없는 장치는
        //   화면에서 이름이 소문자로 바뀝니다. 그러면 운영자가 배포 스크립트에
        //   적은 이름(Gateway-Router)과 대조할 수 없고, 프로젝트 서브넷의
        //   agent_id 와도 매칭되지 않아 정책이 기본값으로 떨어집니다.
        //   (실측: 라우터 5대가 전부 소문자로 보였습니다)
        final Map<String, String> canonical = new LinkedHashMap<>();
        if (connectedIds != null) {
            for (final String value : connectedIds) {
                if (value != null && !value.isBlank()) {
                    canonical.putIfAbsent(lower(value), value);
                }
            }
        }
        if (telemetryIds != null) {
            for (final String value : telemetryIds) {
                if (value != null && !value.isBlank()) {
                    canonical.putIfAbsent(lower(value), value);
                }
            }
        }

        final Set<String> unexpected = new LinkedHashSet<>(connected);
        unexpected.addAll(seen);
        unexpected.removeAll(result.keySet());
        for (final String key : unexpected) {
            final String agentId = canonical.getOrDefault(key, key);
            final Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("agent_id", agentId);
            entry.put("project_id", null);
            // 유형을 비워 두면 화면에 "—" 로만 보여 운영자가 판단할 수 없습니다.
            // 식별자에서 추론합니다. (inferFromDeviceId 가 이름의 끝도 봅니다)
            entry.put("device_type", resolveDeviceType(null, agentId));
            entry.put("node_type", null);
            entry.put("expected_ip", null);
            entry.put("registered_at", null);
            entry.put("connected", connected.contains(key));
            entry.put("telemetry_seen", seen.contains(key));
            entry.put("expected", false);
            entry.put("state", stateOf(connected.contains(key), seen.contains(key), false));
            result.put(key, entry);
        }

        return result;
    }

    /**
     * 목록에서 쓸 대표 상태 문자열을 만듭니다.
     *
     * <p>{@code connected} 를 최우선으로 봅니다. 세션이 살아 있으면 텔레메트리가
     * 한 주기 늦게 도착했을 뿐이므로 연결된 것으로 판단하는 편이 정확합니다.
     *
     * @param connected 연결 여부
     * @param seen      텔레메트리 수신 여부
     * @param expected  배포 예정 목록에 있었는지 여부
     * @return {@code connected} / {@code telemetry-only} / {@code silent} / {@code unregistered}
     */
    public static String stateOf(boolean connected, boolean seen, boolean expected) {
        if (connected) {
            return "connected";
        }
        if (seen) {
            return "telemetry-only";
        }
        return expected ? "silent" : "unregistered";
    }

    /**
     * 장치 유형을 해석합니다. 명시 값이 없거나 모르는 값이면 식별자로 추론합니다.
     *
     * @param deviceType 명시 유형 (null 허용)
     * @param agentId    Agent 식별자
     * @return 유형 이름
     */
    private static String resolveDeviceType(String deviceType, String agentId) {
        final DeviceType parsed = DeviceType.fromString(deviceType);
        if (parsed != null) {
            return parsed.name();
        }
        final DeviceType inferred = DeviceType.inferFromDeviceId(agentId);
        return (inferred == null) ? DeviceType.VM.name() : inferred.name();
    }

    /** @param values 식별자들 @return 소문자 집합 */
    private static Set<String> lowerCaseSet(Collection<String> values) {
        final Set<String> set = new LinkedHashSet<>();
        if (values != null) {
            for (final String value : values) {
                if (value != null && !value.isBlank()) {
                    set.add(lower(value));
                }
            }
        }
        return set;
    }

    /** @param value 입력 @return 소문자/공백제거 키 (null 이면 빈 문자열) */
    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 배포 예정 목록을 DTO 맵으로 바꿉니다. (컨트롤러 응답용)
     *
     * @param expected 배포 예정 목록
     * @param connectedIds 연결된 식별자
     * @param telemetryIds 텔레메트리 식별자
     * @return 응답 본문
     */
    public static Map<String, Object> toResponse(List<ExpectedAgent> expected,
                                                 Collection<String> connectedIds,
                                                 Collection<String> telemetryIds) {
        final Map<String, Map<String, Object>> overview = overview(expected, connectedIds, telemetryIds);
        int connected = 0;
        int silent = 0;
        for (final Map<String, Object> entry : overview.values()) {
            if (Boolean.TRUE.equals(entry.get("connected"))) {
                connected++;
            } else if ("silent".equals(entry.get("state"))) {
                silent++;
            }
        }

        final Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("total", overview.size());
        body.put("expected_total", expected.size());
        body.put("connected", connected);
        body.put("silent", silent);
        body.put("agents", new ArrayList<>(overview.values()));
        return body;
    }
}