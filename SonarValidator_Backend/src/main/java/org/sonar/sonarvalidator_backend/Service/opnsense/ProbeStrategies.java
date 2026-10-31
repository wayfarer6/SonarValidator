package org.sonar.sonarvalidator_backend.Service.opnsense;

import java.util.Set;

final class ProbeStrategies {

    private ProbeStrategies() {
    }

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
