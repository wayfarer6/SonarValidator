package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

/**
 * nftables 전체 규칙셋을 파싱합니다. ({@code nft list ruleset})
 *
 * <p>방화벽의 실제 차단 상태를 보는 유일한 조회입니다. 우리가 넣은
 * {@code sonar} 테이블이 있는지, 금지 연결 규칙이 실제로 걸렸는지를
 * 확인하는 근거가 됩니다.
 *
 * <p>{@code NFTABLES} 벤더의 기본 조회입니다.
 */
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