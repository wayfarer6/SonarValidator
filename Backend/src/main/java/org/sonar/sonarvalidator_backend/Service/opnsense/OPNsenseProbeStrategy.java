package org.sonar.sonarvalidator_backend.Service.opnsense;

/**
 * OPNsense 진단 조회 <b>대상 하나</b>를 API 호출로 옮깁니다.
 *
 * <h2>⚠️ 왜 전략 패턴인가</h2>
 * <p>컨트롤러의 {@code probe} 가 대상 이름을 {@code switch} 로 분기해
 * 다섯 개 API 중 하나를 골랐습니다. 그래서
 * <ul>
 *   <li>새 조회 대상({@code gateway}, {@code dhcp} 등)을 추가하려면 컨트롤러를 고쳐야 했고,</li>
 *   <li>컨트롤러가 API 클라이언트의 모든 메서드를 알게 되어 HTTP 계층이
 *       도메인 지식을 갖게 됐습니다.</li>
 * </ul>
 *
 * <p>지금은 대상 하나가 구현체 하나입니다. 컨트롤러는 <b>이름을 넘기고 결과를
 * 응답으로 감싸는 일</b>만 합니다.
 *
 * <h2>⚠️ 거부가 아니라 폴백이다</h2>
 * <p>모르는 대상은 예외가 아니라 {@link OPNsenseApiClient#checkConnection}
 * (접속 확인) 으로 폴백합니다. 진단 API 이므로 "그 대상은 없다" 보다
 * "일단 접속이 되는지 보여주자" 가 더 유용합니다 — 접속부터 실패하는 것이
 * 가장 흔한 원인이기 때문입니다.
 */
public interface OPNsenseProbeStrategy {

    /**
     * 정규화된(소문자·trim) 대상 이름이 이 전략의 담당인지 판단합니다.
     *
     * @param normalizedTarget 정규화된 대상 이름
     * @return 담당이면 true
     */
    boolean matches(String normalizedTarget);

    /** @return 이 전략의 대표 이름 (로그·화면 표기용) */
    String name();

    /**
     * 이 대상이 <b>무엇을 확인하는지</b> 사람이 읽는 설명입니다.
     *
     * <p>진단 응답에 실어 보내면 운영자가 "règles 조회가 성공했다" 를
     * 숫자만 보고 해석할 필요가 없습니다.
     *
     * @return 한 줄 설명
     */
    String description();

    /**
     * 실제 API 를 호출합니다.
     *
     * @param client     API 클라이언트
     * @param connection 접속 정보 (호출 전에 {@code isUsable()} 확인 완료)
     * @return 호출 결과 (null 아님)
     */
    OPNsenseApiClient.Result probe(OPNsenseApiClient client, OPNsenseConnection connection);
}