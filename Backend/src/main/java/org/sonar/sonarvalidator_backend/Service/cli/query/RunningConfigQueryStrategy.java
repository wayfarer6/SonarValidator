package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

/**
 * 실행 중 설정을 파싱합니다. ({@code show running-config})
 *
 * <p>포트별 설정이 한 문서에 몰려 있어, 개별 조회를 여러 번 하는 것보다
 * 한 번에 전체 구성을 얻을 수 있습니다. 오프라인 스냅샷으로 받은 파일이
 * 대부분 이 형식입니다.
 */
public final class RunningConfigQueryStrategy implements CliQueryStrategy {

    private static final Set<String> NAMES = Set.of("running", "running-config");

    @Override
    public boolean matches(String normalizedTarget) {
        return NAMES.contains(normalizedTarget);
    }

    @Override
    public String primaryTarget() {
        return "running";
    }

    @Override
    public String contractKey() {
        // 포트 목록을 만들므로 트렁크 조회와 같은 키를 씁니다.
        // 소비자는 두 경로를 구분할 필요가 없습니다.
        return "trunk_status";
    }

    @Override
    public ObjectNode parse(CliOutputParser parser, CliVendor vendor, String raw) {
        return parser.parseRunningConfig(raw);
    }
}