package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

public final class FirewallRulesQueryStrategy implements CliQueryStrategy {

    private static final Set<String> NAMES = Set.of("ruleset", "nft", "firewall");

    @Override
    public boolean matches(String normalizedTarget) {
        return NAMES.contains(normalizedTarget);
    }

    @Override
    public String primaryTarget() {
        return "ruleset";
    }

    @Override
    public String contractKey() {
        return "firewall_rules";
    }

    @Override
    public ObjectNode parse(CliOutputParser parser, CliVendor vendor, String raw) {
        return parser.parseFirewallRules(raw);
    }
}
