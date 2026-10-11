package org.sonar.sonarvalidator_backend.Service.opnsense;

import java.util.Set;

/**
 * OPNsense 진단 대상 5개를 한 파일에 모았습니다.
 *
 * <h2>⚠️ 왜 중첩 클래스인가 — 파일 수 대 장점</h2>
 * <p>구현체가 전부 <b>3줄</b>입니다(이름 + API 경로). 각각 별도 파일로 두면
 * 5개 파일·5개 import 가 생기지만, 읽는 사람이 얻는 정보는 같습니다.
 *
 * <p>반대로 {@code ClientPolicyStrategy} 처럼 <b>본문이 긴</b> 전략은
 * 한 파일씩 둡니다. 기준은 "이름과 한 줄 호출이 전부인가" 입니다.
 * 인터페이스는 그대로라 나중에 본문이 자라면 파일로 빼면 됩니다.
 *
 * <h2>⚠️ {@code default} 전략이 마지막인 이유</h2>
 * <p>{@code OPNsenseProbeStrategies} 가 "못 찾으면 마지막" 으로 폴백합니다.
 * 그래서 접속 확인({@link ConnectionCheck})이 반드시 마지막이어야 합니다.
 * {@code matches()} 가 항상 {@code false} 인 것도 그래서입니다 —
 * 이름으로 선택되면 다른 대상이 가려집니다.
 */
final class ProbeStrategies {

    private ProbeStrategies() {
    }

    /** {@code /api/interfaces/overview/interfacesInfo} — 인터페이스·주소·상태. */
    static final class Interfaces implements OPNsenseProbeStrategy {

        @Override
        public boolean matches(String normalizedTarget) {
            return Set.of("interfaces", "ifaces", "interface").contains(normalizedTarget);
        }

        @Override
        public String name() {
            return "interfaces";
        }

        @Override
        public String description() {
            return "인터페이스 목록과 주소 — 어느 대역에 붙어 있는지 확인";
        }

        @Override
        public OPNsenseApiClient.Result probe(OPNsenseApiClient client, OPNsenseConnection connection) {
            return client.fetchInterfaces(connection);
        }
    }

    /** {@code /api/firewall/filter/search_rule} — 필터 규칙. */
    static final class FirewallRules implements OPNsenseProbeStrategy {

        @Override
        public boolean matches(String normalizedTarget) {
            return Set.of("rules", "filter", "firewall").contains(normalizedTarget);
        }

        @Override
        public String name() {
            return "rules";
        }

        @Override
        public String description() {
            return "방화벽 필터 규칙 — 우리가 넣은 규칙이 실제로 있는지 확인";
        }

        @Override
        public OPNsenseApiClient.Result probe(OPNsenseApiClient client, OPNsenseConnection connection) {
            return client.fetchFirewallRules(connection);
        }
    }

    /** {@code /api/firewall/filter/get} 의 {@code filter.snatrules} — NAT 규칙. */
    static final class NatRules implements OPNsenseProbeStrategy {

        @Override
        public boolean matches(String normalizedTarget) {
            return Set.of("nat", "natrules", "nat-rules").contains(normalizedTarget);
        }

        @Override
        public String name() {
            return "nat";
        }

        @Override
        public String description() {
            return "NAT 규칙 — 주소 변환이 격리 판정에 영향을 주는지 확인";
        }

        @Override
        public OPNsenseApiClient.Result probe(OPNsenseApiClient client, OPNsenseConnection connection) {
            return client.fetchNatRules(connection);
        }
    }

    /** {@code /api/firewall/alias/search_item} — 별칭(주소 그룹). */
    static final class Aliases implements OPNsenseProbeStrategy {

        @Override
        public boolean matches(String normalizedTarget) {
            return Set.of("aliases", "alias", "groups").contains(normalizedTarget);
        }

        @Override
        public String name() {
            return "aliases";
        }

        @Override
        public String description() {
            return "별칭(주소 그룹) — 규칙이 대역 대신 별칭을 쓰는지 확인";
        }

        @Override
        public OPNsenseApiClient.Result probe(OPNsenseApiClient client, OPNsenseConnection connection) {
            return client.fetchAliases(connection);
        }
    }

    /**
     * 기본 대상 — 접속과 버전만 확인합니다.
     *
     * <p>{@link #matches} 가 항상 {@code false} 인 이유: 이름으로 선택되면
     * 다른 대상이 가려집니다. 이름이 없거나 모를 때만 폴백으로 쓰입니다.
     */
    static final class ConnectionCheck implements OPNsenseProbeStrategy {

        @Override
        public boolean matches(String normalizedTarget) {
            return false;
        }

        @Override
        public String name() {
            return "firmware";
        }

        @Override
        public String description() {
            return "접속 확인과 펌웨어 버전 — API Key/Secret 이 유효한지 확인";
        }

        @Override
        public OPNsenseApiClient.Result probe(OPNsenseApiClient client, OPNsenseConnection connection) {
            return client.checkConnection(connection);
        }
    }
}