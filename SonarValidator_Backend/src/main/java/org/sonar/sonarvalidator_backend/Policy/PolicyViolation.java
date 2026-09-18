package org.sonar.sonarvalidator_backend.Policy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 망분리 위반 한 건의 <b>반례 증거</b>입니다.
 *
 * <p>BDD 는 "위반 집합이 비어 있지 않다"까지만 알려줍니다. 운영자에게는
 * <b>어떤 패킷이 실제로 위반인지</b>가 필요하므로, 위반 집합에서 만족 할당을
 * 하나 뽑아 주소/포트로 복원해 담습니다. 이것이 BDD 를 쓰는 실질적 이점입니다 —
 * 단순히 "위반 있음"이 아니라 재현 가능한 예시를 줍니다.
 *
 * @param ruleId          위반한 규칙 식별자
 * @param sourceSubnetId  출발 서브넷 식별자
 * @param targetSubnetId  도착 서브넷 식별자
 * @param sourceZone      출발 존 등급
 * @param targetZone      도착 존 등급
 * @param sampledSourceIp 반례로 추출한 출발지 IP
 * @param sampledTargetIp 반례로 추출한 목적지 IP
 * @param sampledPort     반례로 추출한 포트 ({@link PacketVariables#ANY_PORT} 면 전체 허용)
 * @param reason          사람이 읽는 사유
 * @param severity        심각도
 */
public record PolicyViolation(
        String ruleId,
        String sourceSubnetId,
        String targetSubnetId,
        ZoneClass sourceZone,
        ZoneClass targetZone,
        String sampledSourceIp,
        String sampledTargetIp,
        int sampledPort,
        String reason,
        Severity severity) {

    /** 위반 심각도. */
    public enum Severity {
        /** 한 단계 건너뛰는 직접 연결. 즉시 조치 대상입니다. */
        CRITICAL,
        /** 포트 미지정 등 정책을 약화시키는 설정. 검토 대상입니다. */
        MAJOR,
        /** 참고 수준 (예: 규칙이 서브넷을 찾지 못함). */
        MINOR
    }

    /** @return 반례 패킷을 사람이 읽는 한 줄로 표현 */
    public String sampledPacket() {
        return sampledSourceIp + " -> " + sampledTargetIp
                + (sampledPort == PacketVariables.ANY_PORT ? " (all ports)" : (":" + sampledPort));
    }

    /** @return 프로토콜까지 포함한 반례 표기 */
    public String sampledPacket(String protocol) {
        final String proto = protocol == null || protocol.isBlank() ? "tcp" : protocol.toLowerCase();
        return proto + " " + sampledSourceIp + " -> " + sampledTargetIp
                + (sampledPort == PacketVariables.ANY_PORT ? " (all ports)" : (":" + sampledPort));
    }

    /**
     * 위반 목록을 규칙 식별자 기준으로 묶습니다. (UI 표 표시용)
     *
     * @param violations 위반 목록
     * @return 규칙 식별자 → 위반 목록
     */
    public static Map<String, List<PolicyViolation>> groupByRule(List<PolicyViolation> violations) {
        final Map<String, List<PolicyViolation>> grouped = new LinkedHashMap<>();
        if (violations == null) {
            return grouped;
        }
        for (final PolicyViolation violation : violations) {
            grouped.computeIfAbsent(violation.ruleId(), key -> new ArrayList<>()).add(violation);
        }
        return grouped;
    }
}
