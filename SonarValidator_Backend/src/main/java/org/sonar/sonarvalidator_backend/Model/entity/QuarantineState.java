package org.sonar.sonarvalidator_backend.Model.entity;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 격리된(quarantined) Agent 의 현재 상태 한 건입니다.
 *
 * <h2>격리가 무엇인가</h2>
 * <p>망분리 위반이 감지됐을 때 운영자가 <b>직접</b> 그 장치를 네트워크에서
 * 떼어내는 조치입니다. 서버가 자동으로 격리하지는 않습니다. 자동 격리는
 * 오탐 한 번으로 정상 장비를 끊어 서비스를 마비시킬 수 있기 때문입니다.
 *
 * <h2>격리는 세 가지를 동시에 한다</h2>
 * <ol>
 *   <li><b>정책 차단(A)</b> — 위반 서브넷으로 내려가는 정책을 {@code drop} 으로
 *       덮어써 장치가 스스로 트래픽을 막게 합니다.</li>
 *   <li><b>인터페이스 차단(B)</b> — Agent 가 위반 인터페이스 자체를 내립니다.
 *       ({@code ip link set down} / OVS 포트 down)</li>
 *   <li><b>관리 상태 반영(C)</b> — 이 테이블의 행이 남고, 이후 정책 푸시에서
 *       대상에서 빠지며, 토폴로지에 <b>빨간색</b>으로 표시됩니다.</li>
 * </ol>
 *
 * <h2>⚠️ 관리 인터페이스는 절대 내리지 않는다</h2>
 * <p>격리 명령은 Agent 로 가는 WebSocket(관리 경로)을 통해 전달됩니다.
 * 관리 인터페이스까지 내리면 <b>해제(release) 명령을 보낼 길이 사라집니다.</b>
 * 그래서 Agent 는 관리 대역(예: {@code 172.16.255.0/24}) 인터페이스를
 * 제외 대상에서 뺍니다.
 *
 * <h2>왜 삭제하지 않고 {@link #releasedAt} 을 두는가</h2>
 * <p>격리는 보안 사건입니다. "언제 격리했고 언제 풀었는지" 가 감사 기록으로
 * 남아야 합니다. 행을 지우면 그 이력이 사라집니다. 그래서
 * {@code released_at IS NULL} 이면 <b>현재 격리 중</b>으로 봅니다.
 *
 * <h2>인덱스 설계</h2>
 * <p>조회는 항상 두 가지입니다.
 * <pre>
 *   WHERE agent_id = ? AND released_at IS NULL   ← 특정 장치가 격리 중인가
 *   WHERE released_at IS NULL                    ← 격리 중인 장치 전체 (토폴로지/푸시 제외)
 * </pre>
 * <p>그래서 {@code agent_id} 와 {@code released_at} 을 함께 인덱싱합니다.
 */
@Entity
@Table(
        name = "quarantine_state",
        indexes = {
                // 장치별 현재 격리 여부 (가장 빈번)
                @Index(name = "idx_quarantine_agent", columnList = "agent_id, released_at"),
                // 격리 중인 장치 전체 조회 (토폴로지/푸시 대상 제외)
                @Index(name = "idx_quarantine_released", columnList = "released_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class QuarantineState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 격리 대상 Agent 식별자입니다.
     *
     * <p>{@code @ManyToOne} 이 아니라 문자열인 이유는, 격리는 <b>연결이
     * 끊긴 장치</b>에도 남아야 하기 때문입니다. Agent 는 재부팅/재배포되면
     * 세션이 사라지지만 격리 상태는 서버 DB 에 남아야 다음 접속 때 다시
     * 차단 정책을 받습니다.
     */
    @Column(name = "agent_id", length = 120, nullable = false)
    private String agentId;

    /** 격리 대상이 속한 프로젝트 키입니다. (없으면 null — 프로젝트 미배정 장치) */
    @Column(name = "project_key", length = 120)
    private String projectKey;

    /** 격리를 요청한 주체입니다. ({@code "operator"} 또는 사용자명) */
    @Column(name = "requested_by", length = 120)
    private String requestedBy;

    /** 격리 사유입니다. (운영자 메모) */
    @Column(name = "reason", length = 500)
    private String reason;

    /**
     * {@code true} 면 서버가 Agent 에 명령을 <b>전달했다는 뜻</b>입니다.
     *
     * <p>⚠️ 이 값은 "장치가 실제로 막혔다" 를 보장하지 않습니다. Agent 가
     * 연결이 끊겨 있으면 명령이 큐에 남지 않고 실패하기 때문입니다.
     * 그래서 전달 여부와 격리 상태를 분리해 남깁니다.
     */
    @Column(name = "command_delivered", nullable = false)
    private boolean commandDelivered = false;

    /** 격리가 적용된 시각입니다. */
    @Column(name = "quarantined_at", nullable = false)
    private Date quarantinedAt;

    /**
     * 격리가 해제된 시각입니다.
     *
     * <p>{@code null} 이면 <b>현재 격리 중</b>입니다. 이것이 삭제 플래그 역할을
     * 하므로 별도 {@code active} 컬럼을 두지 않습니다. (두 개면 서로
     * 어긋날 수 있습니다)
     */
    @Column(name = "released_at")
    private Date releasedAt;

    /** 해제를 요청한 주체입니다. */
    @Column(name = "released_by", length = 120)
    private String releasedBy;

    /**
     * 현재 격리 중인지 여부입니다.
     *
     * @return 해제 시각이 없으면 {@code true}
     */
    public boolean isActive() {
        return releasedAt == null;
    }
}