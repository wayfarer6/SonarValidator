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
 * 네트워크 장비에서 수집한 로그 한 줄입니다.
 *
 * <h2>로그를 왜 DB 에 남기는가</h2>
 * <p>장비의 로그는 시간이 지나면 <b>장비 자신의 버퍼에서 밀려나 사라집니다.</b>
 * (Cisco 는 기본적으로 최근 수백 줄만 보관) 문제가 생긴 뒤에 장비에 접속해
 * 보면 이미 사라진 뒤인 경우가 많습니다. 그래서 수집 시점에 중앙에 남깁니다.
 *
 * <h2>⚠️ 인덱스 설계 (조회 성능의 핵심)</h2>
 * <p>로그는 다른 테이블보다 <b>훨씬 빠르게 커집니다.</b> 랩 규모에서도 하루에
 * 수만 줄이 쌓일 수 있습니다. 화면은 항상 아래 조건으로 조회하므로 복합
 * 인덱스를 그 순서대로 만듭니다.
 *
 * <pre>
 *   WHERE agent_id = ? AND logged_at BETWEEN ? AND ?   ← 가장 자주 쓰는 형태
 *   ORDER BY logged_at DESC
 * </pre>
 *
 * <p>인덱스 컬럼 순서가 중요합니다. {@code agent_id} 를 앞에 두어야 특정 장비
 * 조회가 좁혀지고, 그 안에서 {@code logged_at} 범위가 정렬됩니다.
 * (순서를 뒤집으면 시간 범위로 먼저 훑어 느려집니다.)
 *
 * <h2>심각도(severity)를 정규화하는 이유</h2>
 * <p>벤더마다 표기가 다릅니다.
 * <ul>
 *   <li>Cisco: {@code %SYS-5-CONFIG_I} 처럼 숫자(0~7)가 들어 있습니다</li>
 *   <li>Linux/syslog: {@code emerg/alert/crit/err/warning/notice/info/debug}</li>
 *   <li>FRR: {@code %DAEMON-3-...} 형식</li>
 * </ul>
 * <p>원문을 그대로 두면 "warning 이상만" 같은 필터를 벤더별로 따로 구현해야
 * 합니다. 그래서 수집 시점에 <b>숫자 등급(0~7, 낮을수록 심각)</b> 으로
 * 정규화해 저장하고, 화면과 AI 는 이 값만 봅니다.
 */
@Entity
@Table(
        name = "device_log",
        indexes = {
                // 장비별 + 시간 범위 조회 (가장 빈번)
                @Index(name = "idx_device_log_agent_time",
                        columnList = "agent_id, logged_at"),
                // 전체 시간순 조회 (장비 미지정)
                @Index(name = "idx_device_log_time", columnList = "logged_at"),
                // 심각도 필터
                @Index(name = "idx_device_log_severity", columnList = "severity_num"),
        })
@Getter
@Setter
@NoArgsConstructor
public class DeviceLog {

    /** 기본 키. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그를 보낸 장비 식별자. */
    @Column(name = "agent_id", nullable = false, length = 120)
    private String agentId;

    /** 장비가 속한 프로젝트 키. (프로젝트별 필터용, 없으면 null) */
    @Column(name = "project_key", length = 120)
    private String projectKey;

    /** 장비 제품명 (예: {@code Cisco 8000v}, {@code FRR}). */
    @Column(name = "product", length = 120)
    private String product;

    /** 로그 발생 시각 (ISO-8601, UTC). 정렬·범위 필터의 기준입니다. */
    @Column(name = "logged_at", nullable = false, length = 40)
    private String loggedAt;

    /** 수집 시각 (ISO-8601, UTC). 장비 시계가 틀렸을 때를 구분하기 위함입니다. */
    @Column(name = "collected_at", length = 40)
    private String collectedAt;

    /**
     * 정규화된 심각도 등급입니다. syslog 표준을 따릅니다.
     *
     * <pre>
     *   0 emergency · 1 alert · 2 critical · 3 error
     *   4 warning   · 5 notice · 6 info    · 7 debug
     * </pre>
     * <p>숫자가 <b>낮을수록 심각</b>합니다. "warning 이상" 은 {@code <= 4} 입니다.
     */
    @Column(name = "severity_num", nullable = false)
    private Integer severityNum = 6;

    /** 정규화된 심각도 이름 ({@code critical}/{@code warning}/{@code info} …). */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "info";

    /** syslog facility (예: {@code SYS}, {@code LINEPROTO}, {@code kernel}). */
    @Column(name = "facility", length = 60)
    private String facility;

    /** 벤더가 붙인 메시지 코드 (예: {@code %SYS-5-CONFIG_I}). */
    @Column(name = "message_id", length = 120)
    private String messageId;

    /** 로그 본문 (메시지 코드 이후의 실제 내용). */
    @Column(name = "message", length = 8000)
    private String message;

    /** 로그 원문 한 줄 그대로. (AI 분석의 근거 자료) */
    @Column(name = "raw", length = 8000)
    private String raw;

    /**
     * 로그 출처입니다.
     *
     * <ul>
     *   <li>{@code agent} — 프로버가 장비에서 직접 수집</li>
     *   <li>{@code syslog} — 서버가 syslog 수신</li>
     *   <li>{@code upload} — 사용자가 파일로 업로드</li>
     *   <li>{@code manual} — 화면에서 직접 입력</li>
     * </ul>
     */
    @Column(name = "source", length = 20)
    private String source = "agent";

    /**
     * 로그 본문의 지문입니다. 중복 수집을 막습니다.
     *
     * <p>프로버가 주기적으로 같은 로그를 다시 보내는 일이 흔합니다.
     * (장비 버퍼 전체를 매번 읽는 경우) 지문이 같으면 새 행을 만들지 않고
     * {@code repeatCount} 만 올립니다.
     */
    @Column(name = "fingerprint", length = 64)
    private String fingerprint;

    /** 같은 로그가 몇 번 반복됐는지. */
    @Column(name = "repeat_count")
    private Integer repeatCount = 1;

    /** 사용자가 지정한 관심 로그인지 여부. (수동 태그) */
    @Column(name = "highlighted")
    private Boolean highlighted = false;

    /** 사용자가 남긴 메모. (분석 근거로 함께 전달됨) */
    @Column(name = "note", length = 1000)
    private String note;
}
