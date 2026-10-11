package org.sonar.sonarvalidator_backend.Service.cli.query;

import java.util.Set;

import org.sonar.sonarvalidator_backend.Service.cli.CliOutputParser;
import org.sonar.sonarvalidator_backend.Service.cli.CliVendor;

import tools.jackson.databind.node.ObjectNode;

/**
 * 이웃(ARP) 테이블을 파싱합니다. ({@code ip neigh}, {@code show arp})
 *
 * <p>이웃 정보가 중요한 이유: 주소만으로는 "같은 대역에 누가 있는가" 를
 * 알 수 없습니다. 이 테이블이 있어야 도달성 판정에서 실제 연결된 대상을
 * 확인할 수 있습니다.
 *
 * <p>리눅스({@code ip neigh})와 Cisco 계열({@code show arp})의 열 순서가
 * 달라 벤더를 넘겨야 합니다.
 */
public final class ArpQueryStrategy implements CliQueryStrategy {

    private static final Set<String> NAMES = Set.of("arp", "neigh");

    @Override
    public boolean matches(String normalizedTarget) {
        return NAMES.contains(normalizedTarget);
    }

    @Override
    public String primaryTarget() {
        return "arp";
    }

    @Override
    public String contractKey() {
        return "arp_table";
    }

    @Override
    public ObjectNode parse(CliOutputParser parser, CliVendor vendor, String raw) {
        return parser.parseArpTable(raw, vendor);
    }
}