package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

public final class NicBriefQueryStrategy implements CliQueryStrategy {

    private static final Set<String> NAMES = Set.of("brief", "nic-brief");

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

    private static boolean looksLikeIosInterfaceStatus(String raw) {
        return raw != null && raw.contains(IOS_HEADER) && raw.contains(IOS_HEADER_HINT);
    }
}
