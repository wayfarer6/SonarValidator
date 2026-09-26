package org.sonar.sonarvalidator_backend.Policy.advice;

import java.util.Locale;
import java.util.Optional;

import org.sonar.sonarvalidator_backend.Policy.PacketVariables;
import org.sonar.sonarvalidator_backend.Policy.PolicyViolation;
import org.sonar.sonarvalidator_backend.Policy.ZoneClass;

/**
 * 위반 한 건을 <b>AI 프롬프트에 넣을 형태</b>로 정리한 값 객체입니다.
 *
 * <h2>왜 {@link PolicyViolation} 을 그대로 쓰지 않는가</h2>
 * <p>판정 엔진의 위반 레코드는 BDD 가 만든 <b>내부 표현</b>입니다. 예를 들어
 * {@code sampledPort} 는 {@link PacketVariables#ANY_PORT} 라는 <b>센티널 -1</b>
 * 로 "전체 포트" 를 나타냅니다. 이 값을 그대로 프롬프트에 넣으면 모델이
 * "포트 -1" 을 실제 포트로 읽어 <b>없는 문제를 지어냅니다.</b>
 *
 * <p>또한 프롬프트는 <b>사람이 읽는 서술</b>이므로, 표시 문자열을 만드는 규칙을
 * 한 곳에 모아 둡니다. 화면({@code sampled_packet})과 프롬프트가 서로 다른
 * 문장을 쓰면, 운영자가 AI 에게 "화면에 있는 그 위반" 을 물을 때 어긋납니다.
 *
 * @param ruleId       위반한 규칙 식별자
 * @param severity     심각도 이름 ({@code CRITICAL}/{@code MAJOR}/{@code MINOR})
 * @param srcLabel     출발 서브넷 표시 (예: {@code VLAN 131 ATICS})
 * @param dstLabel     도착 서브넷 표시
 * @param srcClass     출발 등급 (없으면 {@code 미지정})
 * @param dstClass     도착 등급
 * @param classGap     등급 레벨 차이 (2 이상이면 금지, 알 수 없으면 -1)
 * @param forbidden    등급 규칙상 애초에 금지되는 쌍인지
 * @param packet       반례 패킷 문장 (사람이 읽는 형식)
 * @param port         반례 포트 ({@code null} 이면 "전체 포트")
 * @param reason       판정 엔진이 만든 사유
 */
public record ViolationBrief(String ruleId,
                             String severity,
                             String srcLabel,
                             String dstLabel,
                             String srcClass,
                             String dstClass,
                             int classGap,
                             boolean forbidden,
                             String packet,
                             Integer port,
                             String reason) {

    /** 등급을 알 수 없을 때 쓰는 표시입니다. */
    private static final String UNKNOWN = "미지정";

    /**
     * 판정 엔진의 위반을 프롬프트용 표현으로 바꿉니다.
     *
     * @param violation 위반 (null 이면 null 반환)
     * @return 프롬프트용 표현
     */
    public static ViolationBrief from(PolicyViolation violation) {
        if (violation == null) {
            return null;
        }

        final ZoneClass source = violation.sourceZone();
        final ZoneClass target = violation.targetZone();

        // ⚠️ 등급 차이는 "알 수 없음"(-1)과 "차이 0" 을 구분해야 합니다.
        //    -1 을 그대로 두면 "등급 차이 -1" 이라는 문장이 프롬프트에 들어가
        //    모델이 없는 사실을 추론합니다.
        final int gap = (source == null || target == null)
                ? -1
                : Math.abs(source.level() - target.level());

        final boolean forbidden = source != null && target != null
                && ZoneClass.forbidsDirectConnection(source, target);

        // 반례 포트: ANY_PORT(-1) 는 "포트 제한 없음" 이라는 의미이므로
        // 숫자 대신 null 로 내보내고, 문장은 sampledPacket() 이 만듭니다.
        final Integer port = violation.sampledPort() == PacketVariables.ANY_PORT
                ? null
                : violation.sampledPort();

        return new ViolationBrief(
                text(violation.ruleId(), "-"),
                violation.severity() == null
                        ? "MINOR"
                        : violation.severity().name().toUpperCase(Locale.ROOT),
                text(violation.sourceSubnetId(), UNKNOWN),
                text(violation.targetSubnetId(), UNKNOWN),
                source == null ? UNKNOWN : source.label(),
                target == null ? UNKNOWN : target.label(),
                gap,
                forbidden,
                text(violation.sampledPacket(), "-"),
                port,
                text(violation.reason(), "사유 없음"));
    }

    /**
     * 프롬프트에 넣을 한 줄 설명을 만듭니다.
     *
     * <p>형식: {@code [CRITICAL] Rule-9001 — A(Confidential) → B(Open), 차이 2, 금지쌍}
     *
     * @return 한 줄 설명
     */
    public String describe() {
        final StringBuilder line = new StringBuilder();
        line.append('[').append(severity).append("] ").append(ruleId);
        line.append(" — ").append(srcLabel).append('(').append(srcClass).append(')');
        line.append(" → ").append(dstLabel).append('(').append(dstClass).append(')');

        if (classGap >= 0) {
            line.append(", 등급 차이 ").append(classGap);
        } else {
            line.append(", 등급 미지정");
        }
        if (forbidden) {
            line.append(", 금지 쌍");
        }
        line.append('\n');
        line.append("    반례 패킷: ").append(packet);
        if (port != null) {
            line.append(" (포트 ").append(port).append(')');
        } else {
            line.append(" (포트 제한 없음)");
        }
        line.append('\n');
        line.append("    사유: ").append(reason);
        return line.toString();
    }

    /** @return 등급 차이를 알 수 있으면 그 값 (프롬프트에서 조건부 사용) */
    public Optional<Integer> knownClassGap() {
        return classGap >= 0 ? Optional.of(classGap) : Optional.empty();
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}