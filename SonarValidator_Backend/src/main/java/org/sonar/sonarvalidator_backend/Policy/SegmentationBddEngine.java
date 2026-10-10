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
 *   1) 허용 집합 A = ∪ (출발지 대역 × 목적지 대역 × 프로토콜 × 포트) ← 규칙마다 OR
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

    /**
     * 관측(수집) 기반 인터넷 노출 위반의 규칙 식별자입니다.
     *
     * <p>명시적 규칙(Rule-0001 …)이 아니라 <b>장치가 실제로 인터넷 기본 경로를
     * 갖고 있다</b>는 수집 증거로 만들어지는 위반입니다. UI 에서 "어느 규칙 때문인지"
     * 를 구분할 수 있도록 고정 식별자를 씁니다.
     */
    public static final String INTERNET_EXPOSURE_RULE = "internet-exposure";

    /**
     * 반례에 쓰는 인터넷 대표 주소입니다.
     *
     * <p>인터넷 전체를 가리키는 실주소가 없으므로, 대표 시험점 하나를 반례로 씁니다.
     * 이 프로젝트의 기존 증거 모델({@code Internet-Probe-1 = 1.1.1.1/32})과 같은 값을
     * 써서 화면에서 해석이 엇갈리지 않게 합니다.
     */
    public static final String INTERNET_PROBE_IP = "1.1.1.1";

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
     * 규칙 집합을 검증합니다. (관측 증거 없이 규칙만 보는 경로)
     *
     * @param subnets 서브넷 목록 (식별자/CIDR/등급)
     * @param rules   연결 규칙 목록
     * @return 검증 보고서
     */
    public Report validate(List<PolicySubnet> subnets, List<PolicyRule> rules) {
        return validate(subnets, rules, Set.of());
    }

    /**
     * 규칙 집합을 검증합니다.
     *
     * @param subnets                서브넷 목록 (식별자/CIDR/등급)
     * @param rules                  연결 규칙 목록
     * @param internetExposedAgents  인터넷 기본 경로(0.0.0.0/0)가 수집된 장치 식별자
     * @return 검증 보고서
     */
    public Report validate(List<PolicySubnet> subnets, List<PolicyRule> rules,
                           Set<String> internetExposedAgents) {
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
            if (subnet.getZoneClass() == null || subnet.getCidr() == null || subnet.getCidr().isBlank()) {
                report.getViolations().add(new PolicyViolation(
                        "classification-required", subnet.getId(), null,
                        subnet.getZoneClass(), null, "-", "-", PacketVariables.ANY_PORT,
                        "VLAN/서브넷의 IP 대역과 CSO 등급을 지정해야 검증할 수 있습니다.",
                        PolicyViolation.Severity.MINOR));
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
        final List<PolicyRule> validRules = new ArrayList<>();
        for (final PolicyRule rule : safeRules) {
            if (rule.isEnabled()) {
                activeRules.add(rule);
            }
        }
        report.setRuleCount(activeRules.size());

        for (final PolicyRule rule : activeRules) {
            try {
                PacketVariables.protocolNumber(rule.getProtocol());
                if (rule.getPort() != PacketVariables.ANY_PORT
                        && (rule.getPort() < 1 || rule.getPort() > 65535)) {
                    throw new IllegalArgumentException("port out of range");
                }
                if ("icmp".equalsIgnoreCase(rule.getProtocol()) && rule.hasPort()) {
                    throw new IllegalArgumentException("ICMP does not use transport ports");
                }
            } catch (IllegalArgumentException ex) {
                report.getViolations().add(new PolicyViolation(
                        rule.getId(),
                        rule.getSource(),
                        rule.getDestination(),
                        null,
                        null,
                        "-",
                        "-",
                        PacketVariables.ANY_PORT,
                        "프로토콜 또는 포트가 유효하지 않습니다.",
                        PolicyViolation.Severity.MINOR,
                        rule.getProtocol()));
                continue;
            }

            validRules.add(rule);
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
                validRules, report);

        // --- 2) 포트 미지정 검사 ---------------------------------------
        if (flagMissingPort) {
            collectMissingPortViolations(manager, validRules, byId, byCidr, report);
        }

        // --- 3) 관측 기반 인터넷 노출 검사 ------------------------------
        // 규칙이 없어도 장치가 인터넷 기본 경로를 갖고 있으면 위반입니다.
        collectInternetExposureViolations(report, safeSubnets, internetExposedAgents);

        // --- 3.5) 허용 목록 밖 연결 검사 -------------------------------
        // 운영자가 서브넷에 "이것만 허용" 을 적어 넣은 경우, 그 밖으로 나가는
        // 연결을 찾아냅니다. (등급 규칙을 건너뛰지 않아도 위반입니다)
        collectRestrictedEgressViolations(report, safeSubnets, validRules, byId, byCidr);

        // --- 4) 보고서 마감 --------------------------------------------
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

        // 관측 기반 위반은 "규칙을 건너뛴 연결" 이 아니므로 따로 셉니다.
        // 합쳐서 세면 요약 문구가 실제 원인과 어긋납니다.
        final long internetExposure = report.getViolations().stream()
                .filter(v -> INTERNET_EXPOSURE_RULE.equals(v.ruleId()))
                .count();
        final long critical = report.getViolations().stream()
                .filter(v -> v.severity() == PolicyViolation.Severity.CRITICAL)
                .filter(v -> !INTERNET_EXPOSURE_RULE.equals(v.ruleId()))
                .count();

        if (report.isCompliant()) {
            report.getMessages().add(
                    "망분리 정책을 준수합니다. (규칙 " + safeRules.size() + "건, 서브넷 "
                            + safeSubnets.size() + "건)");
        } else {
            if (internetExposure > 0) {
                report.getMessages().add(
                        "오류: 인터넷 노출 — 기밀망/민감망 " + internetExposure
                                + "건이 수집된 기본 경로로 인터넷에 나갈 수 있습니다.");
            }
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
     * 수집된 증거로 인터넷 노출을 검사합니다.
     *
     * <h2>왜 규칙만으로는 부족한가</h2>
     * <p>BDD 는 <b>운영자가 적어 넣은 규칙</b>만 봅니다. 그래서 기밀망 서브넷을
     * Confidential 로 분류했는데 장치에 기본 경로가 있어 실제로는 인터넷이 열려 있어도,
     * 그 경로를 규칙으로 적어 두지 않으면 "준수" 로 보고됩니다. 화면은 초록인데
     * 장비에서는 인터넷이 되는, 가장 위험한 불일치입니다.
     *
     * <p>그래서 장치가 수집한 <b>기본 경로({@code 0.0.0.0/0})</b> 를 인터넷 연결로 보고
     * 검사합니다. 이것이 "관측 기반" 위반입니다.
     *
     * <ul>
     *   <li>{@code Confidential} + 인터넷 → CRITICAL (기밀망은 외부와 단절되어야 합니다)</li>
     *   <li>{@code Sensitive} + 인터넷 → MAJOR (업무망의 직접 인터넷 노출은 검토 대상입니다)</li>
     *   <li>{@code Open} + 인터넷 → 위반 아님 (공개망은 인터넷 연동이 정상입니다)</li>
     * </ul>
     *
     * @param report                 채울 보고서
     * @param subnets                검사할 서브넷
     * @param internetExposedAgents  기본 경로가 수집된 장치 식별자 (null 허용)
     */
    private void collectInternetExposureViolations(Report report, List<PolicySubnet> subnets,
                                                   Set<String> internetExposedAgents) {
        if (internetExposedAgents == null || internetExposedAgents.isEmpty()) {
            return;
        }
        for (final PolicySubnet subnet : subnets) {
            if (subnet == null || subnet.getAgentId() == null || subnet.getZoneClass() == null) {
                continue;
            }
            if (!internetExposedAgents.contains(subnet.getAgentId())) {
                continue;
            }
            final ZoneClass zone = subnet.getZoneClass();
            if (zone == ZoneClass.OPEN) {
                // 공개망은 인터넷과 붙는 것이 정상입니다.
                continue;
            }
            final boolean confidential = zone == ZoneClass.CONFIDENTIAL;
            final String label = confidential ? "기밀망(Confidential)" : "민감망(Sensitive)";
            final String cidr = subnet.getCidr() == null || subnet.getCidr().isBlank()
                    ? "(대역 미지정)"
                    : subnet.getCidr();
            final String reason = label + " " + cidr + " 을 가진 장치 " + subnet.getAgentId()
                    + " 에 인터넷 기본 경로(0.0.0.0/0)가 수집되었습니다. "
                    + (confidential
                            ? "기밀망이 인터넷으로 나갈 수 있으므로 망분리 위반입니다."
                            : "업무망에서 인터넷으로 직접 나갈 수 있는지 확인이 필요합니다.");
            report.getViolations().add(new PolicyViolation(
                    INTERNET_EXPOSURE_RULE,
                    subnet.getId(),
                    null,
                    zone,
                    ZoneClass.OPEN,
                    networkAddress(subnet.getCidr()),
                    INTERNET_PROBE_IP,
                    PacketVariables.ANY_PORT,
                    reason,
                    confidential ? PolicyViolation.Severity.CRITICAL : PolicyViolation.Severity.MAJOR,
                    "tcp"));
            report.getViolatedRuleIds().add(INTERNET_EXPOSURE_RULE);
        }
    }

    /**
     * 서브넷에 적어 넣은 <b>허용 목록</b>을 벗어나는 연결을 찾습니다.
     *
     * <h2>무엇을 검사하는가</h2>
     * <p>운영자가 서브넷에 "이 상대들만 연결할 수 있다" 를 적어 넣을 수 있습니다
     * ({@link PolicySubnet#getAllowedPeers()}). 예를 들어 기밀망에
     * {@code VLAN9} 만 적으면, 그 밖의 연결은 정책 배포 시 차단됩니다.
     *
     * <p>그런데 규칙 표에는 다른 상대도 남아 있을 수 있습니다(자동 수집된 규칙,
     * 또는 목록을 좁히기 전에 만든 규칙). 그런 규칙은 배포 후 <b>실제로 막힙니다.</b>
     * 검증이 침묵하면 운영자는 "허용했다" 고 믿는데 장비에서는 끊기는, 원인을
     * 찾기 어려운 장애가 됩니다. 그래서 배포 전에 알려 줍니다.
     *
     * <h2>⚠️ 등급 규칙을 완화할 수는 없다</h2>
     * <p>이 목록은 <b>조이기만</b> 합니다. 기밀망의 허용 목록에 Open 서브넷을
     * 넣어도 등급 건너뛰기 위반은 그대로 남습니다(위 검사가 별도로 잡습니다).
     * 목록에 넣는 것만으로 망분리가 풀리면 운영자의 실수 한 번이
     * 보안 경계를 없애기 때문입니다.
     *
     * @param report   채울 보고서
     * @param subnets  검사할 서브넷 (허용 목록 포함)
     * @param rules    유효한 활성 규칙
     * @param byId     식별자 색인
     * @param byCidr   CIDR 색인
     */
    private void collectRestrictedEgressViolations(Report report,
                                                   List<PolicySubnet> subnets,
                                                   List<PolicyRule> rules,
                                                   Map<String, PolicySubnet> byId,
                                                   Map<String, PolicySubnet> byCidr) {
        for (final PolicySubnet subnet : subnets) {
            if (subnet == null || !subnet.isRestricted()) {
                continue;
            }
            for (final PolicyRule rule : rules) {
                // 이 서브넷이 관여하는 규칙만 봅니다. (나가는 쪽/들어오는 쪽 모두)
                final boolean outgoing = matches(rule.getSource(), subnet, byId, byCidr);
                final boolean incoming = matches(rule.getDestination(), subnet, byId, byCidr);
                if (!outgoing && !incoming) {
                    continue;
                }

                // 허용 목록은 <b>나가는</b> 연결을 제한합니다. 들어오는 연결까지
                // 막으려면 상대의 목록에서 막아야 합니다 — 그러면 "이 서브넷은
                // 누구의 접근도 받지 않는다" 같은 다른 뜻이 되어 혼란스럽습니다.
                if (!outgoing) {
                    continue;
                }

                final String peerRef = rule.getDestination();
                final PolicySubnet peerSubnet = resolve(peerRef, byId, byCidr);
                // ⚠️ 상대의 모든 표기(식별자 + CIDR)를 봐야 합니다. 화면은
                //    식별자를 저장하고 규칙도 식별자를 쓸 수 있으므로, CIDR 만
                //    비교하면 허용한 상대를 위반으로 잘못 보고합니다.
                if (subnet.allowsAnyPeer(
                        peerRef,
                        peerSubnet == null ? null : peerSubnet.getCidr())) {
                    continue;
                }

                final String cidr = subnet.getCidr() == null || subnet.getCidr().isBlank()
                        ? "(대역 미지정)"
                        : subnet.getCidr();
                final String peer = peerRef == null ? "-" : peerRef;
                final boolean confidential = subnet.getZoneClass() == ZoneClass.CONFIDENTIAL;
                final String reason = "허용 목록 제한: " + cidr + " 의 허용 대상을 "
                        + String.join(", ", subnet.getAllowedPeers())
                        + " 로 지정했지만, 규칙 " + rule.getId() + " 이 " + peer
                        + " 로 나갑니다. 정책 배포 시 차단됩니다.";
                report.getViolations().add(new PolicyViolation(
                        rule.getId(),
                        subnet.getId(),
                        peer,
                        subnet.getZoneClass(),
                        peerZone(peerRef, byId, byCidr),
                        networkAddress(subnet.getCidr()),
                        networkAddress(resolveReference(peerRef, byId, byCidr)),
                        rule.getPort(),
                        reason,
                        confidential
                                ? PolicyViolation.Severity.CRITICAL
                                : PolicyViolation.Severity.MAJOR,
                        rule.getProtocol()));
                report.getViolatedRuleIds().add(rule.getId());
            }
        }
    }

    /**
     * 참조 문자열이 이 서브넷을 가리키는지 봅니다.
     *
     * @param reference 참조 (식별자 또는 CIDR)
     * @param subnet    대상 서브넷
     * @param byId      식별자 색인
     * @param byCidr    CIDR 색인
     * @return 이 서브넷이면 true
     */
    private boolean matches(String reference, PolicySubnet subnet,
                            Map<String, PolicySubnet> byId,
                            Map<String, PolicySubnet> byCidr) {
        return resolve(reference, byId, byCidr) == subnet;
    }

    /**
     * 참조를 색인에서 찾아 <b>대역 문자열</b>로 돌려줍니다.
     *
     * <p>허용 목록은 식별자와 CIDR 어느 쪽으로 적혀도 같은 서브넷으로 봐야 하므로,
     * 비교 전에 대역으로 통일합니다.
     *
     * @param reference 참조
     * @param byId      식별자 색인
     * @param byCidr    CIDR 색인
     * @return 대역 문자열 (찾지 못하면 참조 원문)
     */
    private String resolveReference(String reference,
                                    Map<String, PolicySubnet> byId,
                                    Map<String, PolicySubnet> byCidr) {
        final PolicySubnet found = resolve(reference, byId, byCidr);
        if (found != null && found.getCidr() != null && !found.getCidr().isBlank()) {
            return found.getCidr();
        }
        return reference;
    }

    /**
     * 참조가 가리키는 서브넷의 등급을 돌려줍니다.
     *
     * @param reference 참조
     * @param byId      식별자 색인
     * @param byCidr    CIDR 색인
     * @return 등급 (없으면 null)
     */
    private ZoneClass peerZone(String reference,
                               Map<String, PolicySubnet> byId,
                               Map<String, PolicySubnet> byCidr) {
        final PolicySubnet found = resolve(reference, byId, byCidr);
        return found == null ? null : found.getZoneClass();
    }

    /**
     * CIDR 에서 반례용 출발지 주소를 만듭니다.
     *
     * <p>BDD 위반은 네트워크 주소를 예시로 씁니다(예: {@code 10.0.9.0}).
     * 형식이 아니면 원문을 그대로 돌려줍니다.
     *
     * @param cidr 대역
     * @return 반례 주소
     */
    private static String networkAddress(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return "-";
        }
        final int slash = cidr.indexOf('/');
        return slash > 0 ? cidr.substring(0, slash) : cidr;
    }

    /**
     * 서브넷 하나 + 규칙 하나를 BDD 곱(출발지 × 목적지 × 프로토콜 × 포트)으로 만듭니다.
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
            final BddNode protocolSet = PacketVariables.protocol(manager, rule.getProtocol());
            return manager.and(
                    manager.and(manager.and(sourceSet, targetSet), portSet),
                    protocolSet);
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
            final String sampledProtocol = assignment != null
                    ? PacketVariables.protocolOf(assignment)
                    : rule.getProtocol();

            // An aggregate labelled Sensitive may contain a Confidential range.
            // The BDD intersection is authoritative; report the actual forbidden
            // pair containing the witness instead of filtering on aggregate labels.
            PolicySubnet witnessSource = source;
            PolicySubnet witnessTarget = target;
            witnessPair:
            for (final PolicySubnet candidateSource : byId.values()) {
                if (!containsAddress(candidateSource, sourceIp)) continue;
                for (final PolicySubnet candidateTarget : byId.values()) {
                    if (candidateSource.getZoneClass() != null && candidateTarget.getZoneClass() != null
                            && ZoneClass.forbidsDirectConnection(candidateSource.getZoneClass(), candidateTarget.getZoneClass())
                            && containsAddress(candidateTarget, targetIp)) {
                        witnessSource = candidateSource;
                        witnessTarget = candidateTarget;
                        break witnessPair;
                    }
                }
            }

            report.getViolations().add(new PolicyViolation(
                    rule.getId(),
                    witnessSource.getId(),
                    witnessTarget.getId(),
                    witnessSource.getZoneClass(),
                    witnessTarget.getZoneClass(),
                    sourceIp,
                    targetIp,
                    sampledPort,
                    // ⚠️ 사유는 "어떤 연결이 왜 막히는가" 를 그대로 문장으로 씁니다.
                    //   이전에는 "Confidential ↔ Open 직접 연결은 허용되지 않습니다."
                    //   처럼 등급만 말해서, 운영자가 어느 서브넷을 고쳐야 하는지
                    //   알 수 없었습니다. 위반은 <b>서브넷 사이의 연결</b> 문제이므로
                    //   출발/도착 서브넷을 이름으로 지목합니다.
                    describeForbidden(witnessSource, witnessTarget),
                    PolicyViolation.Severity.CRITICAL,
                    sampledProtocol));
            report.getViolatedRuleIds().add(rule.getId());
        }
    }

    private static boolean containsAddress(PolicySubnet subnet, String address) {
        try {
            final var cidr = PacketVariables.parseCidr(subnet.getCidr());
            final int mask = cidr.prefixLength() == 0 ? 0 : -1 << (32 - cidr.prefixLength());
            return (PacketVariables.parseIp(address) & mask) == (cidr.address() & mask);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * 금지된 연결을 사람이 읽는 한 문장으로 만듭니다.
     *
     * <p>형식: {@code "Subnet A (대역) -> Subnet B (대역) 연결은 허용되지 않습니다.
     * (기밀 ↔ 공개 — 등급 2단계 차이)"}
     *
     * <p>서브넷 표시 이름이 없으면 식별자를 씁니다. 랩에서는
     * {@code VLAN 131 ATICS} 처럼 운영자가 붙인 이름이 있어 그대로 읽힙니다.
     *
     * @param source 출발 서브넷
     * @param target 도착 서브넷
     * @return 위반 사유 문장
     */
    private static String describeForbidden(PolicySubnet source, PolicySubnet target) {
        final StringBuilder text = new StringBuilder();
        text.append(nameOf(source)).append(" -> ").append(nameOf(target))
                .append(" 연결은 허용되지 않습니다.");

        // 등급을 덧붙여 "왜" 를 남깁니다. 서브넷 이름만으로는 이유를 알 수 없습니다.
        if (source.getZoneClass() != null && target.getZoneClass() != null) {
            final int gap = Math.abs(source.getZoneClass().level() - target.getZoneClass().level());
            text.append(" (").append(source.getZoneClass().label())
                    .append(" ↔ ").append(target.getZoneClass().label())
                    .append(" — 등급 ").append(gap).append("단계 차이)");
        }
        return text.toString();
    }

    /**
     * 서브넷 표시 이름을 만듭니다.
     *
     * @param subnet 서브넷
     * @return 이름(없으면 식별자) + 대역
     */
    private static String nameOf(PolicySubnet subnet) {
        final String name = (subnet.getName() == null || subnet.getName().isBlank())
                ? subnet.getId()
                : subnet.getName();
        final String cidr = subnet.getCidr();
        return (cidr == null || cidr.isBlank()) ? name : name + " (" + cidr + ")";
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
            final String protocol = rule.getProtocol() == null
                    ? "tcp"
                    : rule.getProtocol().trim().toLowerCase(java.util.Locale.ROOT);
            if (!protocol.equals("tcp") && !protocol.equals("udp") && !protocol.equals("any")) {
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
                    PolicyViolation.Severity.MAJOR,
                    protocol));
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
