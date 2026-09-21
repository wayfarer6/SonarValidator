package org.sonar.sonarvalidator_backend.Model.entity;

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
 * 시스템이 발생시킨 알림 한 건입니다.
 *
 * <h2>왜 별도 테이블이 필요한가</h2>
 * <p>알림은 지금까지 <b>화면 안에서만</b> 존재했습니다. 헤더의 종 아이콘은
 * 하드코딩된 목록을 보여주고, 페이지를 새로 고치면 사라졌습니다. 그래서
 * 운영자가 자리를 비운 사이에 발생한 위반/푸시 실패를 <b>나중에 확인할
 * 방법이 없었습니다.</b> 감사(audit) 관점에서도 "무엇이 언제 알려졌나" 가
 * 남아야 하므로 DB 에 적재합니다.
 *
 * <h2>⚠️ 로그(`device_log`)와 섞지 않은 이유</h2>
 * <p>장비 로그와 알림은 발생 원천도, 보존 기간도, 읽음 상태도 다릅니다.
 * <ul>
 *   <li>{@code device_log} — 장비가 보낸 원문. 하루 수만 건. 읽음 개념 없음</li>
 *   <li>{@code notification} — <b>서버가 판단해서</b> 만든 요약. 하루 수십 건.
 *       읽음/안읽음 상태가 있음</li>
 * </ul>
 * <p>한 테이블에 넣으면 알림 조회가 로그 수만 건을 훑어야 하고, 로그 보존
 * 정책(오래된 것 삭제)이 알림까지 지워 버립니다.
 *
 * <h2>인덱스 설계</h2>
 * <p>화면은 항상 <b>최신순</b>으로 보고, 안읽음 개수를 배지로 표시합니다.
 * 그래서 두 가지 조회를 인덱스로 받칩니다.
 * <pre>
 *   ORDER BY occurred_at DESC              ← 목록 (전체/프로젝트별)
 *   WHERE is_read = false                  ← 안읽음 배지
 * </pre>
 * <p>{@code project_key} 를 함께 인덱싱하는 이유는 특정 프로젝트의 알림만
 * 보는 화면이 있기 때문입니다. (앞 컬럼이 같아야 범위가 좁혀집니다)
 */
@Entity
@Table(
        name = "notification",
        indexes = {
                // 최신순 목록 조회 (가장 빈번)
                @Index(name = "idx_notification_occurred", columnList = "occurred_at"),
                // 안읽음 배지 개수
                @Index(name = "idx_notification_read", columnList = "is_read"),
                // 프로젝트별 알림
                @Index(name = "idx_notification_project", columnList = "project_key, occurred_at"),
                // 장치별 알림
                @Index(name = "idx_notification_agent", columnList = "agent_id, occurred_at"),
        })
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    /** 기본 키. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 외부 노출용 식별자 (예: {@code NTF-3F9A21B4}).
     *
     * <p>DB 의 {@code id} 를 그대로 노출하면 전체 건수가 추측되고, 다른
     * 테이블의 id 와 헷갈립니다. 변경 이력({@code ComplianceChange})의
     * {@code CHG-...} 와 같은 방식으로 맞춥니다.
     */
    @Column(name = "notification_id", nullable = false, unique = true, length = 80)
    private String notificationId;

    /**
     * 알림 분류입니다. 화면의 필터 탭과 대응합니다.
     *
     * <pre>
     *   POLICY      — 망분리 정책 관련 (검증 실패, 푸시 성공/실패)
     *   AGENT       — 프로버 연결/해제, 텔레메트리 중단
     *   PROJECT     — 프로젝트 생성/수정/삭제
     *   SECURITY    — CVE, 인증 실패, 자격증명 만료
     *   SYSTEM      — 그 외 (디스크, 스케줄러 등)
     * </pre>
     *
     * <p>자유 문자열로 두면 화면에서 오타가 섞인 분류가 생겨 필터가
     * 비어 보입니다. 값 집합을 좁게 유지합니다.
     */
    @Column(nullable = false, length = 40)
    private String category = "SYSTEM";

    /**
     * 심각도입니다. syslog 명명을 따릅니다 ({@code critical}/{@code warning}/{@code info}).
     *
     * <p>장비 로그의 {@code severity} 와 같은 표기를 씁니다. 화면에서 두 목록을
     * 나란히 보여줄 때 색 기준이 달라지면 헷갈립니다.
     */
    @Column(nullable = false, length = 20)
    private String severity = "info";

    /** 한 줄 제목. (목록에서 굵게 표시) */
    @Column(nullable = false, length = 300)
    private String title;

    /** 본문 요약. 목록에서 2~3줄로 잘려 보입니다. */
    @Column(length = 2000)
    private String message;

    /** 관련 프로젝트 키. 프로젝트와 무관한 알림이면 null. */
    @Column(name = "project_key", length = 120)
    private String projectKey;

    /** 관련 장치 식별자. 장치와 무관한 알림이면 null. */
    @Column(name = "agent_id", length = 120)
    private String agentId;

    /**
     * 알림을 만든 주체입니다. ({@code system}, {@code scheduler}, 사용자 id …)
     *
     * <p>사용자 조작으로 생긴 알림과 자동 판정으로 생긴 알림을 구분합니다.
     */
    @Column(length = 200)
    private String source = "system";

    /**
     * 발생 시각 (ISO-8601, UTC).
     *
     * <p>문자열로 두는 이유는 다른 테이블({@code device_log},
     * {@code compliance_change})과 같습니다. ISO-8601 은 사전순 정렬이
     * 시간순 정렬과 일치하므로 DB 종류와 무관하게 동작합니다.
     */
    @Column(name = "occurred_at", nullable = false, length = 40)
    private String occurredAt;

    /**
     * 읽음 여부.
     *
     * <p>{@code boolean} 이 아니라 래퍼를 쓰지 않습니다. {@code nullable=false} 이고
     * 기본값이 {@code false} 이므로 원시형이 안전합니다. (null 이면 배지 개수가
     * 어긋납니다)
     */
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    /**
     * 화면에서 "자세히 보기" 로 이동할 경로 (예: {@code /project/editor/PRJ-1}).
     *
     * <p>알림을 보고 나서 <b>어디로 가야 하는지</b>를 서버가 알려 줍니다.
     * 프론트엔드가 분류별로 경로를 만드려면 분류가 늘 때마다 화면을 고쳐야
     * 하고, 서버가 만든 문맥(프로젝트 키 등)을 다시 조립해야 합니다.
     */
    @Column(length = 300)
    private String link;

    /**
     * 중복 방지 키입니다. (선택)
     *
     * <p>같은 원인이 짧은 시간에 반복되면(예: 30초 주기 검증이 계속 실패)
     * 알림이 수백 건 쌓여 목록이 무의미해집니다. 기록 지점에서 같은 키가
     * 최근에 있으면 <b>새로 만들지 않고</b> 시각만 갱신합니다.
     *
     * <p>null 이면 중복 검사를 하지 않습니다. (한 번만 일어나는 사건용)
     */
    @Column(name = "dedupe_key", length = 200)
    private String dedupeKey;

    /**
     * 같은 중복 키로 <b>몇 번</b> 발생했는지.
     *
     * <p>"위반 3건" 이 한 번이 아니라 열 번 반복됐다면 그 사실 자체가
     * 중요한 정보입니다. 알림을 합치면서 횟수를 잃으면 "한 번 있었던 일"
     * 처럼 보여 우선순위를 잘못 판단합니다.
     */
    @Column(name = "repeat_count", nullable = false)
    private int repeatCount = 1;
}
