package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

/**
 * nftables 체인 하나를 파싱합니다. ({@code nft -a list chain ...})
 *
 * <h2>⚠️ ruleset 과 나눠 둔 이유</h2>
 * <p>체인만 조회하면 출력에 <b>테이블 선언이 없습니다.</b> 그래서 family/table
 * 을 원문에서 알 수 없고, 운영자가 대상 이름으로 지정해야 합니다. 전체
 * 조회({@code ruleset})와 진입 규칙이 달라 별도 전략으로 둡니다.
 *
 * <p>부분 조회가 필요한 상황: 규칙이 수천 개인 방화벽에서 한 체인만 보고
 * 싶을 때, 전체를 받아 파싱하면 응답이 매우 커집니다.
 *
 * @see FirewallRulesQueryStrategy
 */
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