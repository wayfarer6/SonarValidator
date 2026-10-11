package org.sonar.sonarvalidator_backend.Policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 프로젝트 편집기에서 다루는 <b>연결 규칙</b>입니다.
 *
 * <p>프론트엔드 {@code NetworkSegmentationRule.tsx} 의 {@code RuleRow} 와 같은
 * 정보를 담되, 검증에 필요한 등급 판정을 서버가 다시 하도록 만든 것입니다.
 * (프론트엔드 검증은 즉시 피드백용이고, 서버 검증이 최종 판정입니다.)
 *
 * <h2>protocol 필드가 있는 이유</h2>
 * <p>망분리 위반은 대개 TCP/UDP 포트에서 생기지만, ICMP 나 전체 프로토콜
 * 허용({@code any})은 포트로 표현할 수 없습니다. 그래서 프로토콜을 규칙의
 * 속성으로 두고, 포트가 의미 있는 경우에만 값이 채워집니다.
 */
public class PolicyRule {

    /**
     * 규칙이 어디서 왔는지 나타냅니다.
     *
     * <p>편집기에서 "자동 수집된 규칙"과 "운영자가 만든 규칙"을 구분해 보여주기
     * 위한 값입니다. 검증 로직은 출처를 보지 않습니다.
     */
    public enum Origin {
        /** 운영자가 프로젝트 편집기에서 직접 만든 규칙. */
        MANUAL,
        /** Agent 가 보고한 설정(방화벽 규칙/트렁크 등)에서 유도한 규칙. */
        DISCOVERED
    }

    /** 규칙 식별자 (예: {@code Rule-0001}). */
    private String id;

    /** 출발 서브넷 식별자. */
    private String source;

    /** 도착 서브넷 식별자. */
    private String destination;

    /** 허용 포트. 미지정이면 {@link PacketVariables#ANY_PORT}. */
    private int port = PacketVariables.ANY_PORT;

    /** 프로토콜 ({@code tcp}, {@code udp}, {@code icmp}, {@code any}). */
    private String protocol = "tcp";

    /** 규칙 출처. */
    private Origin origin = Origin.MANUAL;

    /**
     * 검증에 포함할지 여부입니다.
     *
     * <p>삭제 대신 <b>비활성</b> 으로 두는 것을 권장합니다. 자동 수집 규칙은
     * 장비를 다시 수집하면 되살아나므로, "무시 목록" 으로 관리해야 의도가
     * 유지됩니다. ({@code enabled=false} 규칙은 검증에서 제외됩니다.)
     */
    private boolean enabled = true;

    /** 규칙을 비활성한 사유. 운영자가 남기는 메모입니다. */
    private String note;

    /** 기본 생성자. */
    public PolicyRule() {
    }

    /**
     * 최소 필드로 만듭니다.
     *
     * @param id          식별자
     * @param source      출발 서브넷 식별자
     * @param destination 도착 서브넷 식별자
     * @param port        허용 포트
     */
    public PolicyRule(String id, String source, String destination, int port) {
        this.id = id;
        this.source = source;
        this.destination = destination;
        this.port = port;
    }

    /** @return 규칙 식별자 */
    public String getId() {
        return id;
    }

    /**
     * @param id 규칙 식별자
     */
    public void setId(String id) {
        this.id = id;
    }

    /** @return 출발 서브넷 식별자 */
    public String getSource() {
        return source;
    }

    /**
     * @param source 출발 서브넷 식별자
     */
    public void setSource(String source) {
        this.source = source;
    }

    /** @return 도착 서브넷 식별자 */
    public String getDestination() {
        return destination;
    }

    /**
     * @param destination 도착 서브넷 식별자
     */
    public void setDestination(String destination) {
        this.destination = destination;
    }

    /** @return 허용 포트 (미지정이면 -1) */
    public int getPort() {
        return port;
    }

    /**
     * @param port 허용 포트
     */
    public void setPort(int port) {
        this.port = port;
    }

    /** @return 프로토콜 */
    public String getProtocol() {
        return protocol;
    }

    /**
     * @param protocol 프로토콜
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /** @return 규칙 출처 */
    public Origin getOrigin() {
        return origin;
    }

    /**
     * @param origin 규칙 출처
     */
    public void setOrigin(Origin origin) {
        this.origin = origin;
    }

    /** @return 수동 규칙이면 {@code true} */
    public boolean isManual() {
        return origin == Origin.MANUAL;
    }

    /**
     * @param manual 수동 규칙 여부
     */
    public void setManual(boolean manual) {
        this.origin = manual ? Origin.MANUAL : Origin.DISCOVERED;
    }

    /** @return 검증에 포함되면 {@code true} */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled 검증 포함 여부
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 비활성 사유 메모 */
    public String getNote() {
        return note;
    }

    /**
     * @param note 비활성 사유 메모
     */
    public void setNote(String note) {
        this.note = note;
    }

    /** @return 포트가 지정되었으면 {@code true} */
    public boolean hasPort() {
        return port >= 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PolicyRule rule && Objects.equals(id, rule.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return id + ": " + source + " -> " + destination
                + (hasPort() ? (":" + port) : " (all ports)");
    }

    /**
     * 목록을 방어적으로 복사합니다.
     *
     * @param rules 원본 (null 허용)
     * @return 복사본 (null 아님)
     */
    public static List<PolicyRule> copyOf(List<PolicyRule> rules) {
        return rules == null ? new ArrayList<>() : new ArrayList<>(rules);
    }
}
