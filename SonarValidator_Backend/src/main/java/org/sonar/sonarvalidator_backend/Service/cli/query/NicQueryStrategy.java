package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

public final class NicQueryStrategy implements CliQueryStrategy {

    private static final Set<String> NAMES = Set.of("nic", "addr");

    @Override
    public boolean matches(String normalizedTarget) {
        return NAMES.contains(normalizedTarget);
    }

    @Override
    public String primaryTarget() {
        return "nic";
    }

    @Override
    public String contractKey() {
        // 소비자(AbstractDeviceConfigParser)가 읽는 키입니다.
        // 이름은 여러 개여도 키는 하나여야 화면이 같은 자리에 그립니다.
        return "nic_status";
    }

    @Override
    public ObjectNode parse(CliOutputParser parser, CliVendor vendor, String raw) {
        return parser.parseNicStatus(raw);
    }
}
