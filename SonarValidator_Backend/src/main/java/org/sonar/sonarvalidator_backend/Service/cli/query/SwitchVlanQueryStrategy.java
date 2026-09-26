package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

/**
 * VLAN 목록을 파싱합니다. ({@code show vlan brief})
 *
 * <p>스위치의 VLAN 대역이 곧 구역 경계입니다. 어느 포트가 어느 VLAN 에
 * 속하는지 알아야 "이 스위치가 어떤 존 사이에 걸려 있는가" 를 판정할 수 있고,
 * 그래야 존 간 차단 규칙을 어디에 걸지 정할 수 있습니다.
 *
 * <p>{@code ARISTA} 벤더의 기본 조회입니다.
 */
public final class SwitchVlanQueryStrategy implements CliQueryStrategy {

    private static final Set<String> NAMES = Set.of("vlan", "vlan-brief");

    @Override
    public boolean matches(String normalizedTarget) {
        return NAMES.contains(normalizedTarget);
    }

    @Override
    public String primaryTarget() {
        return "vlan";
    }

    @Override
    public String contractKey() {
        return "vlan_status";
    }

    @Override
    public ObjectNode parse(CliOutputParser parser, CliVendor vendor, String raw) {
        return parser.parseSwitchVlan(raw);
    }
}