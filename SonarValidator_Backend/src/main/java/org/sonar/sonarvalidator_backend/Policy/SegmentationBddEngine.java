package org.sonar.sonarvalidator_backend.Policy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 망분리 규칙을 <b>BDD 로 컴파일해</b> 위반 여부를 판정하는 엔진입니다.
 * (Batfish 의 심볼릭 도달성 분석을 이 프로젝트 규모에 맞게 축소한 구현)
 *
 * <h2>왜 규칙을 하나씩 비교하지 않는가</h2>
 * <p>규칙 수가 늘면 "규칙 N개 × 서브넷 M개" 를 모두 대조해야 하고, 규칙이
 * 겹치거나 부분적으로 중첩되면 판정이 틀리기 쉽습니다. 대신 <b>허용 집합 전체</b> 를
 * BDD 하나로 합쳐 두면 판정이 집합 연산 한 번으로 끝납니다.
 *
 * <pre>
 *   1) 허용 집합 A = ∪ (출발지 대역 × 목적지 대역 × 포트)       ← 규칙마다 OR
 *   2) 금지 집합 F = (Confidential 와 Open 의 모든 교차 곱)      ← 정책에서 생성
 *   3) 위반 집합 V = A ∩ F                                      ← 교집합 한 번
 *   4) V 가 공집합이 아니면, V 에서 할당을 하나 뽑아 반례 패킷으로 제시
 * </pre>
 *
 * <p>BDD 는 동일 부분 함수를 공유하므로 위 계산이 규칙 수에 <b>거의 선형</b>
 * 으로 유지됩니다. 이 방식이 "batfish 처럼 java 에서 하는" 검증의 핵심입니다.
 *
 * <h2>검사하는 두 가지</h2>
 * <ol>
 *   <li><b>금지 연결</b>: 등급 차이가 2 이상인 서브넷 쌍이 직접 연결됨 (CRITICAL)</li>
 *   <li><b>포트 미지정</b>: 허용 포트가 없어 사실상 전체 포트가 열린 규칙 (MAJOR)</li>
 * </ol>
 * <p>두 번째는 "금지 대역을 건드리지는 않지만 정책을 약화시킨다" 는 성격이라
 * 별도 집합으로 계산합니다.
 *
 * <p>이 클래스는 상태를 갖지 않습니다. 요청마다 호출해도 안전합니다.
 */
public class SegmentationBddEngine {

    /** 검증 결과 전체를 담는 보고서. */
    public static class Report {
        private boolean compliant;
        private int ruleCount;
        private int subnetCount;
        private int violationCount;
        private final List<PolicyViolation> violations = new ArrayList<>();
        private final Set<String> violatedRuleIds = new LinkedHashSet<>();
        private final List<String> messages = new ArrayList<>();
        private final Map<String, Object> metrics = new LinkedHashMap<>();

        /** @return 위반이 하나도 없으면 {@code true} */
        public boolean isCompliant() {
            return compliant;
        }

        /**
         * @param compliant 위반 없음 여부
         */
        public void setCompliant(boolean compliant) {
            this.compliant = compliant;
        }

        /** @return 검사한 규칙 수 */
        public int getRuleCount() {
            return ruleCount;
        }

        /**
         * @param ruleCount 검사한 규칙 수
         */
        public void setRuleCount(int ruleCount) {
            this.ruleCount = ruleCount;
        }

        /** @return 검사한 서브넷 수 */
        public int getSubnetCount() {
            return subnetCount;
        }

        /**
         * @param subnetCount 검사한 서브넷 수
         */
        public void setSubnetCount(int subnetCount) {
            this.subnetCount = subnetCount;
        }

        /** @return 위반 건수 */
        public int getViolationCount() {
            return violationCount;
        }

        /**
         * @param violationCount 위반 건수
         */
        public void setViolationCount(int violationCount) {
            this.violationCount = violationCount;
        }

        /** @return 위반 목록 */
        public List<PolicyViolation> getViolations() {
            return violations;
        }

        /** @return 위반이 발생한 규칙 식별자 (UI 에서 규칙 행 강조용) */
        public Set<String> getViolatedRuleIds() {
            return violatedRuleIds;
        }

        /** @return 사람이 읽는 요약 메시지 */
        public List<String> getMessages() {
            return messages;
        }

        /** @return BDD 크기/허용 공간 등 진단 지표 */
        public Map<String, Object> getMetrics() {
            return metrics;
        }
    }

    /** 허용 포트가 없는 규칙을 MAJOR 로 볼지 여부. 기본은 참. */
    private final boolean flagMissingPort;

    /** 기본 설정으로 만듭니다. (포트 미지정을 MAJOR 로 표시) */
    public SegmentationBddEngine() {
        this(true);
    }

    /**
     * @param flagMissingPort 허용 포트가 없는 규칙을 위반으로 볼지 여부
     */
    public SegmentationBddEngine(boolean flagMissingPort) {
        this.flagMissingPort = flagMissingPort;
    }

    /**
     * 규칙 집합을 검증합니다.
     *
     * @param subnets 서브넷 목록 (식별자/CIDR/등급)
     * @param rules   연결 규칙 목록
     * @return 검증 보고서
     */
    public Report validate(List<PolicySubnet> subnets, List<PolicyRule> rules) {
        final Report report = new Report();
        final List<PolicySubnet> safeSubnets = PolicySubnet.copyOf(subnets);
        final List<PolicyRule> safeRules = PolicyRule.copyOf(rules);
        report.setSubnetCount(safeSubnets.size());
        report.setRuleCount(safeRules.size());

        // 서브넷을 식별자와 CIDR 양쪽으로 조회할 수 있게 색인합니다.
        final Map<String, PolicySubnet> byId = new LinkedHashMap<>();
        final Map<String, PolicySubnet> byCidr = new LinkedHashMap<>();
        for (final PolicySubnet subnet : safeSubnets) {
            if (subnet == null) {
                continue;
            }
            if (subnet.getId() != null) {
                byId.put(subnet.getId(), subnet);
            }
            if (subnet.getCidr() != null && !subnet.getCidr().isBlank()) {
                byCidr.putIfAbsent(PolicySubnet.normalizeCidr(subnet.getCidr()), subnet);
            }
        }

        // --- 1) 금지 연결 검사 (BDD) ------------------------------------
        final BddManager manager = PacketVariables.newManager();
        final List<BddNode> allowedParts = new ArrayList<>();

        // 비활성 규칙은 검증 대상에서 제외합니다.
        // (자동 수집된 규칙을 지우지 않고 "무시" 로 관리할 수 있게 하기 위함)
        final List<PolicyRule> activeRules = new ArrayList<>();
        for (final PolicyRule rule : safeRules) {
            if (rule.isEnabled()) {
                activeRules.add(rule);
            }
        }
        report.setRuleCount(activeRules.size());

        for (final PolicyRule rule : activeRules) {
            final PolicySubnet source = resolve(rule.getSource(), byId, byCidr);
            final PolicySubnet target = resolve(rule.getDestination(), byId, byCidr);

            if (source == null || target == null) {
                report.getViolations().add(new PolicyViolation(
                        rule.getId(),
                        rule.getSource(),
                        rule.getDestination(),
                        null,
                        null,
                        "-",
                        "-",
                        PacketVariables.ANY_PORT,
                        "규칙이 참조하는 서브넷을 찾을 수 없습니다.",
                        PolicyViolation.Severity.MINOR));
                continue;
            }

            final BddNode part = ruleToBdd(manager, source, target, rule);
            if (part != null) {
                allowedParts.add(part);
            }
        }

        final BddNode allowed = manager.orAll(allowedParts);
        final BddNode forbidden = forbiddenSet(manager, safeSubnets);
        final BddNode violatingSet = manager.and(allowed, forbidden);

        collectForbiddenViolations(manager, violatingSet, allowed, forbidden, byId, byCidr,
                activeRules, report);

        // --- 2) 포트 미지정 검사 ---------------------------------------
        if (flagMissingPort) {
            collectMissingPortViolations(manager, activeRules, byId, byCidr, report);
        }

        // --- 3) 보고서 마감 --------------------------------------------
        report.setViolationCount(report.getViolations().size());
        report.setCompliant(report.getViolations().isEmpty());

        report.getMetrics().put("bdd_variables", manager.variableCount());
        report.getMetrics().put("bdd_nodes_total", manager.nodeCount());
        report.getMetrics().put("bdd_nodes_allowed", manager.nodeCount(allowed));
        report.getMetrics().put("bdd_nodes_forbidden", manager.nodeCount(forbidden));
        report.getMetrics().put("bdd_nodes_violating", manager.nodeCount(violatingSet));
        report.getMetrics().put("allowed_combinations", manager.satCount(allowed));
        report.getMetrics().put("forbidden_combinations", manager.satCount(forbidden));
        report.getMetrics().put("violating_combinations", manager.satCount(violatingSet));
        report.getMetrics().put("address_space", BigInteger.TWO.pow(PacketVariables.TOTAL_BITS));

        if (report.isCompliant()) {
            report.getMessages().add(
                    "망분리 정책을 준수합니다. (규칙 " + safeRules.size() + "건, 서브넷 "
                            + safeSubnets.size() + "건)");
        } else {
            long critical = report.getViolations().stream()
                    .filter(v -> v.severity() == PolicyViolation.Severity.CRITICAL)
                    .count();
            if (critical > 0) {
                report.getMessages().add(
                        "오류: 네트워크 연결 제한 — 등급을 건너뛰는 직접 연결 " + critical + "건이 있습니다.");
            }
            long major = report.getViolations().stream()
                    .filter(v -> v.severity() == PolicyViolation.Severity.MAJOR)
                    .count();
            if (major > 0) {
                report.getMessages().add(
                        "경고: 포트 설정 확인 — 허용 포트가 없는 규칙 " + major + "건이 있습니다.");
            }
        }
        return report;
    }

    /**
     * 서브넷 하나 + 규칙 하나를 BDD 곱(출발지 × 목적지 × 포트)으로 만듭니다.
     *
     * @param manager 변수 매니저
     * @param source  출발 서브넷
     * @param target  도착 서브넷
     * @param rule    규칙
     * @return 규칙이 나타내는 패킷 집합, 대역 파싱 실패 시 {@code null}
     */
    private BddNode ruleToBdd(BddManager manager, PolicySubnet source, PolicySubnet target, PolicyRule rule) {
        try {
            final BddNode sourceSet = PacketVariables.cidr(manager, source.getCidr(), PacketVariables.SRC_IP_OFFSET);
            final BddNode targetSet = PacketVariables.cidr(manager, target.getCidr(), PacketVariables.DST_IP_OFFSET);
            final BddNode portSet = PacketVariables.port(manager, rule.getPort());
            return manager.and(manager.and(sourceSet, targetSet), portSet);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 금지 집합(등급 차이 2 이상인 서브넷 쌍의 모든 교차 곱)을 BDD 로 만듭니다.
     *
     * <p>정책이 "금지 목록"이 아니라 "등급 규칙"으로 표현되므로, 새 등급을
     * 추가해도 이 메서드는 그대로 동작합니다.
     *
     * @param manager 변수 매니저
     * @param subnets 서브넷 목록
     * @return 금지 패킷 집합
     */
    private BddNode forbiddenSet(BddManager manager, List<PolicySubnet> subnets) {
        final List<BddNode> parts = new ArrayList<>();
        for (final PolicySubnet source : subnets) {
            if (source == null || source.getZoneClass() == null || source.getCidr() == null) {
                continue;
            }
            for (final PolicySubnet target : subnets) {
                if (target == null || target.getZoneClass() == null || target.getCidr() == null) {
                    continue;
                }
                if (!ZoneClass.forbidsDirectConnection(source.getZoneClass(), target.getZoneClass())) {
                    continue;
                }
                try {
                    final BddNode sourceSet = PacketVariables.cidr(
                            manager, source.getCidr(), PacketVariables.SRC_IP_OFFSET);
                    final BddNode targetSet = PacketVariables.cidr(
                            manager, target.getCidr(), PacketVariables.DST_IP_OFFSET);
                    // 방향은 양쪽 다 금지입니다.
                    parts.add(manager.and(sourceSet, targetSet));
                } catch (IllegalArgumentException ex) {
                    // 대역이 잘못된 서브넷은 금지 집합에서 제외합니다. (위반 목록에서 별도로 다룸)
                }
            }
        }
        return manager.orAll(parts);
    }

    /**
     * 위반 집합에서 규칙별 반례를 뽑아 보고서에 담습니다.
     *
     * <p>규칙마다 자기 패킷 집합과 금지 집합의 교집합을 따로 계산해
     * <b>어느 규칙이 문제인지</b> 를 특정합니다. 이 부분은 규칙 수만큼 반복되지만
     * BDD 노드 공유 덕분에 실제 비용은 작습니다.
     *
     * @param manager       변수 매니저
     * @param violatingSet  위반 집합 (진단용, 비어 있으면 빠르게 종료)
     * @param allowed       전체 허용 집합
     * @param forbidden     금지 집합
     * @param byId          식별자 색인
     * @param byCidr        CIDR 색인
     * @param rules         규칙 목록
     * @param report        채울 보고서
     */
    private void collectForbiddenViolations(BddManager manager,
                                            BddNode violatingSet,
                                            BddNode allowed,
                                            BddNode forbidden,
                                            Map<String, PolicySubnet> byId,
                                            Map<String, PolicySubnet> byCidr,
                                            List<PolicyRule> rules,
                                            Report report) {
        if (violatingSet.isFalse()) {
            return;
        }
        // 위반 집합 전체의 대표 반례 (규칙 특정이 어려울 때의 폴백)
        final int[] globalSample = manager.anySat(violatingSet);

        for (final PolicyRule rule : rules) {
            final PolicySubnet source = resolve(rule.getSource(), byId, byCidr);
            final PolicySubnet target = resolve(rule.getDestination(), byId, byCidr);
            if (source == null || target == null || source.getZoneClass() == null || target.getZoneClass() == null) {
                continue;
            }
            if (!ZoneClass.forbidsDirectConnection(source.getZoneClass(), target.getZoneClass())) {
                continue;
            }
            final BddNode ruleSet = ruleToBdd(manager, source, target, rule);
            if (ruleSet == null) {
                continue;
            }
            final BddNode overlap = manager.and(ruleSet, forbidden);
            if (overlap.isFalse()) {
                continue;
            }
            final int[] assignment = manager.anySat(overlap);
            final String sourceIp = assignment != null
                    ? PacketVariables.srcIpOf(assignment)
                    : (globalSample != null ? PacketVariables.srcIpOf(globalSample) : "-");
            final String targetIp = assignment != null
                    ? PacketVariables.dstIpOf(assignment)
                    : (globalSample != null ? PacketVariables.dstIpOf(globalSample) : "-");
            final int sampledPort = assignment != null
                    ? PacketVariables.portOf(assignment)
                    : PacketVariables.ANY_PORT;

            report.getViolations().add(new PolicyViolation(
                    rule.getId(),
                    source.getId(),
                    target.getId(),
                    source.getZoneClass(),
                    target.getZoneClass(),
                    sourceIp,
                    targetIp,
                    sampledPort,
                    source.getZoneClass().label() + " ↔ " + target.getZoneClass().label()
                            + " 직접 연결은 허용되지 않습니다.",
                    PolicyViolation.Severity.CRITICAL));
            report.getViolatedRuleIds().add(rule.getId());
        }
    }

    /**
     * 허용 포트가 지정되지 않은 규칙을 찾습니다.
     *
     * @param manager 변수 매니저
     * @param rules   규칙 목록
     * @param byId    식별자 색인
     * @param byCidr  CIDR 색인
     * @param report  채울 보고서
     */
    private void collectMissingPortViolations(BddManager manager,
                                              List<PolicyRule> rules,
                                              Map<String, PolicySubnet> byId,
                                              Map<String, PolicySubnet> byCidr,
                                              Report report) {
        for (final PolicyRule rule : rules) {
            if (rule.hasPort()) {
                continue;
            }
            final PolicySubnet source = resolve(rule.getSource(), byId, byCidr);
            final PolicySubnet target = resolve(rule.getDestination(), byId, byCidr);
            if (source == null || target == null) {
                continue;
            }
            // 금지 쌍이면 CRITICAL 로 이미 잡히므로 중복 보고하지 않습니다.
            if (source.getZoneClass() != null && target.getZoneClass() != null
                    && ZoneClass.forbidsDirectConnection(source.getZoneClass(), target.getZoneClass())) {
                continue;
            }
            report.getViolations().add(new PolicyViolation(
                    rule.getId(),
                    source.getId(),
                    target.getId(),
                    source.getZoneClass(),
                    target.getZoneClass(),
                    "-",
                    "-",
                    PacketVariables.ANY_PORT,
                    "허용 포트가 지정되지 않았습니다. 모든 포트가 열린 것으로 해석됩니다.",
                    PolicyViolation.Severity.MAJOR));
            report.getViolatedRuleIds().add(rule.getId());
        }
    }

    /**
     * 서브넷 참조를 해석합니다. 식별자를 먼저 보고, 없으면 CIDR 로 찾습니다.
     *
     * <p>프론트엔드는 식별자(예: {@code Subnet-0004})를 보내지만, 자동 수집
     * 결과를 다룰 때는 CIDR 이 직접 들어올 수 있어 양쪽을 모두 받습니다.
     *
     * @param reference 참조 문자열
     * @param byId      식별자 색인
     * @param byCidr    CIDR 색인
     * @return 찾은 서브넷, 없으면 {@code null}
     */
    private PolicySubnet resolve(String reference,
                                 Map<String, PolicySubnet> byId,
                                 Map<String, PolicySubnet> byCidr) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        final String trimmed = reference.trim();
        final PolicySubnet direct = byId.get(trimmed);
        if (direct != null) {
            return direct;
        }
        return byCidr.get(PolicySubnet.normalizeCidr(trimmed));
    }

    /**
     * 등급을 건너뛰는 서브넷 쌍 목록을 만듭니다. (UI 안내/문서용)
     *
     * @param subnets 서브넷 목록
     * @return 금지 쌍 목록 (출발 식별자, 도착 식별자)
     */
    public List<String[]> forbiddenPairs(List<PolicySubnet> subnets) {
        final List<String[]> pairs = new ArrayList<>();
        final List<PolicySubnet> safe = PolicySubnet.copyOf(subnets);
        final Set<String> seen = new HashSet<>();
        for (final PolicySubnet source : safe) {
            for (final PolicySubnet target : safe) {
                if (source == null || target == null
                        || source.getZoneClass() == null || target.getZoneClass() == null) {
                    continue;
                }
                if (source.getId() == null || target.getId() == null) {
                    continue;
                }
                if (!ZoneClass.forbidsDirectConnection(source.getZoneClass(), target.getZoneClass())) {
                    continue;
                }
                final String key = source.getId() + "|" + target.getId();
                if (seen.add(key)) {
                    pairs.add(new String[] {source.getId(), target.getId()});
                }
            }
        }
        return pairs;
    }
}
