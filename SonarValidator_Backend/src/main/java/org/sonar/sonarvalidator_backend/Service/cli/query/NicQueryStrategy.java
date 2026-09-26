package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

/**
 * 장치 주소 조회를 파싱합니다. ({@code ip a}, {@code ip addr show})
 *
 * <p>가장 기본이 되는 조회입니다. 벤더를 가리지 않고 대부분의 리눅스 계열
 * 장치가 같은 형식을 냅니다. 그래서 {@code UNKNOWN} / {@code LINUX} 의
 * 기본 조회이기도 합니다.
 *
 * @see org.sonar.sonarvalidator_backend.Service.cli.IpAddrVisitor
 */
public final class NicQueryStrategy implements CliQueryStrategy {

    /** 이 전략이 담당하는 이름들. 별칭이 여러 개인 이유는 장비마다 다르게 부르기 때문입니다. */
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