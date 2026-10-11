package org.sonar.sonarvalidator_backend.Model.entity;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 배포하기로 <b>예정된</b> Agent 한 건입니다.
 *
 * <h2>왜 "예정" 을 따로 저장하는가</h2>
 * <p>배포 화면에서 "이 장치에 Agent 를 심겠다" 고 결정한 순간과, 실제 Agent 가
 * {@code hello} 를 보내 연결된 순간은 다릅니다. 그 사이(빌드·전송·기동)에는
 * 아무도 이 장치의 존재를 모릅니다.
 *
 * <p>그래서 배포 시점에 기대 목록을 남겨 둡니다. 그러면
 * <ul>
 *   <li>UI 가 "배포 예정 / 연결됨 / 미연결" 을 한 표에서 구분해 보여줄 수 있고,</li>
 *   <li>서버가 "예정인데 30분째 안 붙는다" 를 <b>알림으로</b> 알려줄 수 있습니다.</li>
 * </ul>
 * 목록이 없으면 "Agent 가 0대" 라는 사실만 보이고, 그 0대가 <b>정상</b> 인지
 * <b>배포 실패</b> 인지 구분할 수 없습니다.
 *
 * <h2>연결 상태를 여기에 저장하지 않는 이유</h2>
 * <p>{@code status} 는 <b>연결 여부</b> 가 아니라 <b>배포 진행 단계</b>
 * ({@code Pending} / {@code Deployed}) 입니다. 실제 연결 여부는 WebSocket 세션
 * ({@code AgentSessionRegistry}) 이 유일한 진실입니다. 두 곳에 쓰면 반드시
 * 어긋나고, 어긋난 쪽을 믿게 됩니다.
 */
@Entity
@Table(name = "expected_agent")
@Getter
@Setter
@NoArgsConstructor
public class ExpectedAgent {

    /** 기본 키. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Agent 식별자.
     *
     * <p>Agent 가 {@code hello} 로 보고하는 값과 <b>정확히 같아야</b> 합니다.
     * 그래서 대소문자를 보존해 저장하고, 조회할 때만 관대하게 비교합니다.
     */
    @Column(name = "agent_id", nullable = false, unique = true, length = 120)
    private String agentId;

    /** 대상 프로젝트 키. */
    @Column(name = "project_key", length = 120)
    private String projectKey;

    /** 장치 유형 ({@code SWITCH}/{@code ROUTER}/{@code FIREWALL}/{@code VM}). */
    @Column(name = "device_type", length = 30)
    private String deviceType;

    /** 배포 노드 유형 ({@code VM}/{@code Container} 등 — 설치 스크립트가 쓰는 값). */
    @Column(name = "node_type", length = 30)
    private String nodeType;

    /** 이 장치의 관리 주소 (선택). 배포 점검에 씁니다. */
    @Column(name = "expected_ip", length = 60)
    private String expectedIp;

    /** 배포 진행 단계 ({@code Pending} / {@code Deployed}). */
    @Column(length = 20)
    private String status = "Pending";

    /** 메모 (선택). */
    @Column(length = 500)
    private String note;

    /** 등록 시각. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "created_at")
    private Date createdAt;

    /** 최근 갱신 시각. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "updated_at")
    private Date updatedAt;
}