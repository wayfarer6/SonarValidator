package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

/**
 * 인터페이스 상태 출력을 파싱합니다. ({@code show ip interface brief})
 *
 * <h2>⚠️ {@code brief} 와 겹치지 않게 한다</h2>
 * <p>이 대상은 이름 {@code interface} 또는 {@code interface-brief} 로
 * 명시적으로 요청합니다. {@code brief} 로 요청된 IOS 출력은
 * {@link NicBriefQueryStrategy} 가 먼저 담당하므로, 두 전략이 같은 원문을
 * 두 번 처리하지 않습니다.
 *
 * <p>이름을 나눠 둔 덕분에 {@code interface} 라고 적으면 원문 모양과 무관하게
 * 이 문법을 쓴다는 의사를 운영자가 명시할 수 있습니다.
 */
public final class InterfaceStatusQueryStrategy implements CliQueryStrategy {

    private static final Set<String> NAMES = Set.of("interface", "interface-brief");

    @Override
    public boolean matches(String normalizedTarget) {
        return NAMES.contains(normalizedTarget);
    }

    @Override
    public String primaryTarget() {
        return "interface";
    }

    @Override
    public String contractKey() {
        return "nic_status";
    }

    @Override
    public ObjectNode parse(CliOutputParser parser, CliVendor vendor, String raw) {
        return parser.parseInterfaceStatus(raw, vendor);
    }
}