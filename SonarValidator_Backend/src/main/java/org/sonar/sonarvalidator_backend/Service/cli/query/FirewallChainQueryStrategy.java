package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

public final class FirewallChainQueryStrategy implements CliQueryStrategy {

    private static final Set<String> NAMES = Set.of("chain");

    @Override
    public boolean matches(String normalizedTarget) {
        return NAMES.contains(normalizedTarget);
    }

    @Override
    public String primaryTarget() {
        return "chain";
    }

    @Override
    public String contractKey() {
        // 체인도 규칙 목록을 만들므로 같은 키를 씁니다.
        return "firewall_rules";
    }

    @Override
    public ObjectNode parse(CliOutputParser parser, CliVendor vendor, String raw) {
        return parser.parseFirewallChain(raw);
    }
}
