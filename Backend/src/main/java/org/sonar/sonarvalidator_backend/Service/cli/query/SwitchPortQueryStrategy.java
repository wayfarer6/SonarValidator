package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

/**
 * 스위치 포트 모드/트렁크를 파싱합니다. ({@code show interfaces switchport})
 *
 * <h2>⚠️ 트렁크인지 알아야 차단이 먹는다</h2>
 * <p>access 포트에 ACL 을 걸면 그 VLAN 안에서만 매칭되어 <b>존 간 이동을
 * 보지 못합니다.</b> 존을 건너뛰는 패킷은 트렁크(업링크)를 지나므로, 차단
 * 규칙도 트렁크에 걸어야 합니다. 이 조회가 그 포트를 알려줍니다.
 */
public final class SwitchPortQueryStrategy implements CliQueryStrategy {

    private static final Set<String> NAMES = Set.of("switchport", "port");

    @Override
    public boolean matches(String normalizedTarget) {
        return NAMES.contains(normalizedTarget);
    }

    @Override
    public String primaryTarget() {
        return "switchport";
    }

    @Override
    public String contractKey() {
        return "trunk_status";
    }

    @Override
    public ObjectNode parse(CliOutputParser parser, CliVendor vendor, String raw) {
        return parser.parseSwitchPorts(raw);
    }
}