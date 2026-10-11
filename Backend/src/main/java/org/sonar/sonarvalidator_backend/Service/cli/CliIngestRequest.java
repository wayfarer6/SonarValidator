package org.sonar.sonarvalidator_backend.Service.cli;

/**
 * 원문 CLI 문자열 한 건을 <b>어느 대상으로</b> 해석할지 지정하는 요청입니다.
 *
 * <h2>왜 DTO 를 따로 두는가</h2>
 * <p>엔드포인트가 받는 본문은 사람이 만든 값입니다(운영자 붙여넣기, 스크립트).
 * 그래서 필드가 비어 있거나 모르는 이름이 와도 <b>예외를 던지지 않고</b>
 * 기본값으로 해석해야 합니다. 그 해석 규칙을 컨트롤러가 아니라 여기 한 곳에
 * 모아 두면 서비스 계층과 컨트롤러가 같은 규칙을 씁니다.
 *
 * <p>{@code vendor}/{@code target} 은 선택 항목이고 {@code raw} 는 필수입니다.
 * {@code raw} 가 비면 파싱할 것이 없으므로 호출자가 명시적으로 실패를
 * 돌려줍니다.
 *
 * @param product 제품명(벤더 판별용). 예: {@code Ubuntu}, {@code FRR}, {@code Cisco}
 * @param target  조회 대상. 예: {@code route}, {@code nic}, {@code arp}, {@code vlan}
 * @param raw     원문 CLI 출력 (필수)
 */
public record CliIngestRequest(String product, String target, String raw) {

    /**
     * 본문이 비었을 때 쓸 빈 요청입니다.
     *
     * @return 세 필드가 모두 {@code null} 인 요청
     */
    public static CliIngestRequest empty() {
        return new CliIngestRequest(null, null, null);
    }

    /**
     * 원문이 실제로 들어 있는지 봅니다.
     *
     * <p>공백만 있는 문자열은 "붙여넣기 실패" 로 보는 편이 낫습니다. 그대로
     * 파싱하면 문법이 빈 입력을 통과시켜 {@code parsed:true, 0건} 이 되고,
     * 화면에는 "조회했지만 아무것도 없음" 으로 보입니다.
     *
     * @return 원문이 있고 공백만으로 이루어지지 않았으면 {@code true}
     */
    public boolean hasRaw() {
        return raw != null && !raw.isBlank();
    }
}