package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

/**
 * {@code brief} 계열 조회를 파싱합니다. <b>원문 모양에 따라 문법이 둘</b>입니다.
 *
 * <h2>⚠️ 왜 한 전략이 두 문법을 갖는가 — 시행착오 기록</h2>
 * <p>처음에는 {@code brief} 를 <b>두 개의 전략</b>으로 나누고 선택기가 앞에서
 * 부터 첫 매칭을 쓰게 했습니다. 그 설계에는 두 가지 결함이 있었습니다.
 *
 * <p><b>(1) {@code accepts()} 를 "거부" 로만 쓸 수 있었다.</b>
 * 선택기가 {@code strategy.matches(name) && strategy.accepts(raw)} 로
 * 조건을 합치므로, {@code accepts} 가 참이면 <b>그 전략이 확정</b>됩니다.
 * 그래서 두 번째 전략(주소 요약)은 첫 번째가 거부했을 때만 기회를 얻습니다.
 * <pre>
 *   IOS 헤더가 있는데 첫 전략이 &lt;b&gt;주소 요약&lt;/b&gt;이면?
 *     accepts(IOS 원문) → "헤더가 있으니 내 것이 아니다" → false
 *     두 번째 전략(IOS 문법) → 선택됨 ✔
 *   그런데 이름이 다른 경로로 들어오면 순서가 뒤집혀 조용히 틀린 문법을 쓴다
 * </pre>
 *
 * <p><b>(2) 등록 순서가 곧 정확성이 됐다.</b> 두 전략이 같은 이름을
 * 담당하므로, 목록에 끼워 넣는 위치 하나로 결과가 바뀝니다. 새 전략을
 * 추가하는 사람이 그 사실을 모르면 <b>조용히 깨집니다.</b>
 *
 * <p>그래서 <b>한 전략이 두 문법을 모두 알고 원문으로 고르게</b> 했습니다.
 * 이제 {@code brief} 는 담당이 하나뿐이고, 등록 순서에 의존하지 않습니다.
 * 계약 키도 한 곳에서 하나로 정해집니다.
 *
 * <h2>⚠️ IOS 헤더 판정은 두 표식을 함께 본다</h2>
 * <p>{@code show ip interface brief} 의 헤더는 {@code Interface} 와
 * {@code OK?} 를 함께 씁니다. 하나만 보면 {@code ip -br addr} 출력의
 * {@code Interface} 열과 구분되지 않아 <b>주소 출력을 IOS 문법으로
 * 파싱</b>합니다. 그러면 ANTLR 의 catch-all 규칙이 통과시켜
 * {@code parsed:true, 항목 0건} 이 되고, 호출자는 "조회했는데 없다" 로
 * 해석합니다. (예외가 나지 않는 것이 이 버그의 무서운 점입니다)
 */
public final class NicBriefQueryStrategy implements CliQueryStrategy {

    /** 이 전략이 담당하는 이름들. */
    private static final Set<String> NAMES = Set.of("brief", "nic-brief");

    /** IOS 인터페이스 상태 출력의 표식 — 두 개를 함께 봐야 오판하지 않습니다. */
    private static final String IOS_HEADER = "Interface";
    private static final String IOS_HEADER_HINT = "OK?";

    @Override
    public boolean matches(String normalizedTarget) {
        return NAMES.contains(normalizedTarget);
    }

    @Override
    public String primaryTarget() {
        return "brief";
    }

    @Override
    public String contractKey() {
        // 두 문법 모두 인터페이스 정보를 만들므로 같은 키를 씁니다.
        // 소비자는 어느 문법이었는지 구분할 필요가 없습니다.
        return "nic_status";
    }

    @Override
    public ObjectNode parse(CliOutputParser parser, CliVendor vendor, String raw) {
        // ⚠️ 문법 선택을 여기서 합니다. 선택기는 이름만 보므로
        //    "이 이름이 두 문법을 가진다" 는 사실을 이 클래스가 소유합니다.
        if (looksLikeIosInterfaceStatus(raw)) {
            return parser.parseInterfaceStatus(raw, vendor);
        }
        return parser.parseNicBrief(raw);
    }

    /**
     * 원문이 IOS 스타일 인터페이스 상태 출력인지 봅니다.
     *
     * @param raw 원문 (null 허용)
     * @return IOS 헤더가 보이면 true
     */
    private static boolean looksLikeIosInterfaceStatus(String raw) {
        return raw != null && raw.contains(IOS_HEADER) && raw.contains(IOS_HEADER_HINT);
    }
}