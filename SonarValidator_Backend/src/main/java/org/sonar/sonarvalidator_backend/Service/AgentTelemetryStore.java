package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

/**
 * Agent 별 <b>최근 관측값</b>을 메모리에 보관합니다.
 *
 * <h2>왜 별도 컴포넌트인가</h2>
 * <p>{@link AgentMessageRouterService} 가 수신({@code hello},
 * {@code policy-request}, {@code telemetry}, {@code ack})을 분기하면서
 * <b>저장까지</b> 겸하고 있었습니다. 그 결과 조회 API 여섯 곳이 "라우터" 를
 * 통해 값만 꺼내러 들어갔고, 라우터를 고치면 화면이 깨질 수 있었습니다.
 *
 * <p>지금은 <b>수신은 라우터</b>, <b>보관/조회는 이 클래스</b>가 맡습니다.
 * 조회하는 쪽은 라우터가 어떤 봉투를 처리하는지 알 필요가 없습니다.
 *
 * <h2>⚠️ 값은 메모리에만 있다</h2>
 * <p>DB 도입 전까지의 임시 저장소입니다. 재시작하면 사라지므로, 저장이
 * 필요한 경로({@link OfflineSnapshotService}, 로그 적재)는 각자 DB 를 씁니다.
 * 그래서 여기서는 <b>동시성</b>만 보장하고 영속성은 보장하지 않습니다.
 *
 * <h2>⚠️ 출처(온라인/오프라인)를 구분한다</h2>
 * <p>파일 업로드로 들어온 설정은 세션이 없으므로
 * {@link AgentSessionRegistry#connectedAgentIds()} 에 나타나지 않습니다.
 * 그런데 화면에는 "연결은 안 됐지만 설정은 확보된 장비" 로 보여야 합니다.
 * 출처를 구분하지 않으면 그 장치가 통째로 사라집니다.
 */
@Service
public class AgentTelemetryStore {

    /** Agent 별 최근 텔레메트리 (Agent 가 보낸 원본). */
    private final Map<String, JsonNode> lastTelemetry = new ConcurrentHashMap<>();

    /** Agent 별 최근 중립 설정 (벤더 파서를 거친 구조). */
    private final Map<String, NeutralDeviceConfig> lastConfig = new ConcurrentHashMap<>();

    /** Agent 별 마지막 수신 시각. (헬스 체크) */
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    /** 설정이 파일 업로드로 들어온 Agent 집합. */
    private final Set<String> offlineOrigins = ConcurrentHashMap.newKeySet();

    // ------------------------------------------------------------------
    //  기록
    // ------------------------------------------------------------------

    /**
     * 수신 시각을 갱신합니다.
     *
     * @param agentId Agent 식별자 (null/blank 이면 무시)
     */
    public void touch(String agentId) {
        if (agentId != null && !agentId.isBlank()) {
            lastSeen.put(agentId, Instant.now());
        }
    }

    /**
     * 원본 텔레메트리를 저장합니다.
     *
     * <p>파싱에 실패해도 <b>원본은 남깁니다.</b> 그래야 배포 확인 화면이
     * "서버가 이 장치를 봤는가" 를 답할 수 있습니다. (파서가 모르는 벤더가
     * 붙으면 설정 목록에서 통째로 사라지는 문제를 막습니다)
     *
     * @param agentId Agent 식별자
     * @param payload 원본 payload
     */
    public void putTelemetry(String agentId, JsonNode payload) {
        if (agentId == null) {
            return;
        }
        lastTelemetry.put(agentId, payload);
    }

    /**
     * 중립 설정을 저장합니다. (온라인 경로)
     *
     * <p>오프라인 출처 표시가 남아 있으면 지웁니다. 장치가 다시 붙어 실제
     * 세션으로 값을 보내는 순간, 그 장치는 더 이상 "오프라인 설정" 이
     * 아니기 때문입니다. 지우지 않으면 연결된 장치가 계속 파일 업로드로
     * 표시됩니다.
     *
     * @param agentId Agent 식별자
     * @param config  중립 설정
     */
    public void putConfig(String agentId, NeutralDeviceConfig config) {
        if (agentId == null) {
            return;
        }
        lastConfig.put(agentId, config);
        offlineOrigins.remove(agentId);
    }

    /**
     * 중립 설정을 저장하고 <b>오프라인 출처로 표시</b>합니다.
     *
     * <p>세션이 없는 경로(파일 업로드)에서만 씁니다.
     *
     * @param agentId Agent 식별자
     * @param config  중립 설정
     */
    public void putOfflineConfig(String agentId, NeutralDeviceConfig config) {
        if (agentId == null) {
            return;
        }
        lastConfig.put(agentId, config);
        offlineOrigins.add(agentId);
    }

    // ------------------------------------------------------------------
    //  조회
    // ------------------------------------------------------------------

    /** @param agentId Agent 식별자 @return 최근 원본 텔레메트리 (없으면 null) */
    public JsonNode telemetryOf(String agentId) {
        return lastTelemetry.get(agentId);
    }

    /** @param agentId Agent 식별자 @return 최근 중립 설정 (없으면 null) */
    public NeutralDeviceConfig configOf(String agentId) {
        return lastConfig.get(agentId);
    }

    /** @return 설정 식별자 → 중립 설정 (불변 사본) */
    public Map<String, NeutralDeviceConfig> allConfigs() {
        return Map.copyOf(lastConfig);
    }

    /**
     * 원본 텔레메트리라도 보낸 적 있는 모든 Agent 식별자입니다.
     *
     * @return 식별자 집합 (불변 사본)
     */
    public Set<String> allTelemetryAgentIds() {
        return Set.copyOf(lastTelemetry.keySet());
    }

    /** @param agentId Agent 식별자 @return 마지막 수신 시각 (없으면 null) */
    public Instant lastSeenOf(String agentId) {
        return lastSeen.get(agentId);
    }

    /** @param agentId Agent 식별자 @return 파일 업로드로 들어온 설정이면 true */
    public boolean isOfflineOrigin(String agentId) {
        return offlineOrigins.contains(agentId);
    }

    /**
     * 진단용 요약입니다. (Agent 수를 로그 한 줄로 봅니다)
     *
     * @return {@code "telemetry=N config=M offline=K"}
     */
    public String summary() {
        return "telemetry=" + lastTelemetry.size()
                + " config=" + lastConfig.size()
                + " offline=" + offlineOrigins.size();
    }
}