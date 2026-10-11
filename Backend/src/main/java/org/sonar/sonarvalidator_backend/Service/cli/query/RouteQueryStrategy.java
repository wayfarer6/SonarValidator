package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

/**
 * 라우팅 테이블을 파싱합니다. ({@code ip route show}, {@code show ip route})
 *
 * <h2>⚠️ 벤더가 아니라 <b>명령</b>이 문법을 가른다</h2>
 * <p>같은 FRR 장비라도 출력이 다릅니다.
 * <pre>
 *   FRR 호스트 셸   ip route show    → 라우트 코드 없음  → IpAddr 문법
 *   FRR vtysh       show ip route    → {@code O&gt;*} 코드 있음   → FrrRouter 문법
 * </pre>
 * 그래서 벤더를 그대로 넘겨 파서가 표식을 보고 고르게 합니다. 전략이
 * 미리 정하면 한쪽이 깨집니다.
 *
 * <p>{@code FRR} / {@code CISCO} 의 기본 조회입니다.
 */
public final class RouteQueryStrategy implements CliQueryStrategy {

    private static final Set<String> NAMES = Set.of("route", "route-table");

    @Override
    public boolean matches(String normalizedTarget) {
        return NAMES.contains(normalizedTarget);
    }

    @Override
    public String primaryTarget() {
        return "route";
    }

    @Override
    public String contractKey() {
        return "route_status";
    }

    @Override
    public ObjectNode parse(CliOutputParser parser, CliVendor vendor, String raw) {
        return parser.parseRouteStatus(raw, vendor);
    }
}