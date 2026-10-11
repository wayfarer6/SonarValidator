package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

/**
 * Open vSwitch 토폴로지를 파싱합니다. ({@code ovs-vsctl show}, {@code ovs-vsctl list port})
 *
 * <h2>⚠️ L2 전용 스위치에는 IP 주소가 없다</h2>
 * <p>그래서 {@link NicQueryStrategy} 로는 스위치를 볼 수 없습니다. 브리지와
 * 포트 구성이 곧 "무엇이 어디에 붙어 있는가" 이므로, 격리 판정에서 스위치에
 * 어떤 VLAN 이 걸려 있는지 확인하는 유일한 근거입니다.
 *
 * <p>{@code OPEN_VSWITCH} 벤더의 기본 조회입니다.
 *
 * <h2>⚠️ 두 출력을 내용으로 가른다</h2>
 * <p>같은 명령 계열이지만 진입 규칙이 다릅니다. {@code --} 구분자나
 * {@code _uuid} 가 보이면 {@code list port} 로 봅니다. 이 판단은
 * {@code CliOutputParser.parseOvsTopology} 안에서 합니다 — 전략이 미리
 * 정하면 한쪽 출력이 빈 결과가 됩니다.
 */
public final class OvsTopologyQueryStrategy implements CliQueryStrategy {

    private static final Set<String> NAMES = Set.of("topology", "ovs");

    @Override
    public boolean matches(String normalizedTarget) {
        return NAMES.contains(normalizedTarget);
    }

    @Override
    public String primaryTarget() {
        return "topology";
    }

    @Override
    public String contractKey() {
        return "ovs_topology";
    }

    @Override
    public ObjectNode parse(CliOutputParser parser, CliVendor vendor, String raw) {
        return parser.parseOvsTopology(raw);
    }
}