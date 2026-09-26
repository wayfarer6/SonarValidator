package org.sonar.sonarvalidator_backend.Service.cli.query;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

/**
 * 조회 대상 이름({@code target}) 하나를 <b>실제 파싱 절차</b>로 옮깁니다.
 *
 * <h2>⚠️ 왜 전략 패턴인가 — 대상이 14개다</h2>
 * <p>이전에는 {@link CliOutputParser#parseQueryOutput} 안에 이름
 * {@code switch} 가 있었고, 대상이 늘 때마다 그 메서드를 고쳐야 했습니다.
 * 게다가 대상 → 이름 → 계약 키 매핑이 <b>두 곳</b>에 따로 정의되어
 * 있었습니다 ({@code CliOutputParser} 의 switch 와
 * {@code CliIngestService.contractKeyOf}). 새 대상 {@code interface-brief}
 * 를 한쪽에만 추가하면 <b>파싱은 되는데 화면은 빈</b> 상태가 됩니다.
 *
 * <p>지금은 대상 하나가 <b>구현체 하나</b>입니다. {@link #matches} 에 이름을
 * 적고 {@link #parse} 에 절차를 쓰면, 파싱과 계약 키가 같은 객체에 있으므로
 * 어긋날 수 없습니다.
 *
 * <h2>⚠️ 선택기는 이름을 <b>정규화하지 않는다</b></h2>
 * <p>대소문자·공백 처리는 선택기가 한 번만 합니다({@code toLowerCase}).
 * 구현체는 정규화된 이름만 받습니다. 각 구현체가 자기 방식으로 정규화하면
 * {@code "Route "} 같은 입력이 어떤 구현체에도 안 걸려 조용히 폴백됩니다.
 *
 * <h2>⚠️ 예외를 던지지 않는다</h2>
 * <p>구현체는 {@link CliOutputParser} 의 {@code guard} 를 통과한 결과만
 * 돌려주므로, 문법 오류는 {@code parsed:false} 노드가 됩니다.
 * 텔레메트리 수집 경로에서 예외는 곧 데이터 유실입니다.
 */
public interface CliQueryStrategy {

    /**
     * 정규화된(소문자·trim) 대상 이름이 이 전략의 담당인지 판단합니다.
     *
     * @param normalizedTarget 정규화된 대상 이름 (null/빈 문자열 아님)
     * @return 담당이면 true
     */
    boolean matches(String normalizedTarget);

    /**
     * 이 전략이 담당하는 <b>기본</b> 대상 이름입니다.
     *
     * <p>대상이 지정되지 않았을 때 이 이름으로 자기를 지목합니다. 그래서
     * "벤더 기본 조회" 도 별도 {@code switch} 없이 이름 하나로 표현됩니다.
     *
     * <pre>
     *   벤더 FRR + 대상 없음
     *     → FrrRouterQueryStrategy.primaryTarget() == "route"
     *     → 그 이름으로 다시 자기 자신을 선택
     * </pre>
     *
     * @return 기본 대상 이름
     */
    String primaryTarget();

    /**
     * 이 대상의 파싱 결과를 소비자가 읽을 payload 계약 키입니다.
     *
     * <p>{@code nic} / {@code addr} / {@code brief} 처럼 <b>여러 이름이 같은
     * 키</b>를 쓰는 경우를 한 곳에서 처리합니다.
     *
     * @return 계약 키 (예: {@code route_status})
     */
    String contractKey();

    /**
     * 원문을 파싱합니다.
     *
     * <h2>⚠️ 원문 모양이 문법을 가른다면 <b>여기서</b> 고른다</h2>
     * <p>{@code brief} 하나가 두 출력({@code ip -br addr show},
     * {@code show ip interface brief})을 받습니다. 이런 경우를
     * <b>전략을 둘로 나누지 마세요.</b>
     *
     * <p>나눠 보니 두 결함이 있었습니다.
     * <ul>
     *   <li>선택기가 "이름 매칭" 으로 첫 후보를 잡으므로, <b>등록 순서가
     *       곧 정확성</b>이 됩니다. 새 전략을 끼워 넣는 사람이 그 사실을
     *       모르면 조용히 깨집니다.</li>
     *   <li>같은 계약 키를 두 전략이 따로 선언하므로, 나중에 한쪽만
     *       고치면 <b>파싱은 되는데 화면은 빈</b> 상태가 됩니다.</li>
     * </ul>
     *
     * <p>그래서 <b>이름 하나 = 전략 하나</b>를 규칙으로 두고, 두 문법은
     * 이 메서드 안에서 {@code raw} 를 보고 고릅니다.
     * ({@link NicBriefQueryStrategy} 가 그 예입니다)
     *
     * @param parser 사용할 파서 (공유 상태 없음)
     * @param vendor 판별된 벤더 (출력 모양이 벤더에 따라 다를 때 사용)
     * @param raw    원문 CLI 출력
     * @return 파싱 본문 (실패 시에도 {@code parsed:false} 노드 — null 아님)
     */
    tools.jackson.databind.node.ObjectNode parse(CliOutputParser parser, CliVendor vendor, String raw);
}