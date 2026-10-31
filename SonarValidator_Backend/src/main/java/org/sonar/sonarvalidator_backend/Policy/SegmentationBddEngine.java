package org.sonar.sonarvalidator_backend.Policy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SegmentationBddEngine {

    public static class Report {
        private boolean compliant;
        private final List<String> inputErrors = new ArrayList<>();

        public boolean isModelValid() { return inputErrors.isEmpty(); }
        public List<String> getInputErrors() { return inputErrors; }
        public String getAnalysisScope() { return "POLICY_HEADER_SPACE"; }
        public boolean isDataPlaneAnalyzed() { return false; }
        private int ruleCount;
        private int subnetCount;
        private int violationCount;
        private final List<PolicyViolation> violations = new ArrayList<>();
        private final Set<String> violatedRuleIds = new LinkedHashSet<>();
        private final List<String> messages = new ArrayList<>();
        private final Map<String, Object> metrics = new LinkedHashMap<>();

        public boolean isCompliant() {
            return compliant;
        }

        public void setCompliant(boolean compliant) {
            this.compliant = compliant;
        }

        public int getRuleCount() {
            return ruleCount;
        }

        public void setRuleCount(int ruleCount) {
            this.ruleCount = ruleCount;
        }

        public int getSubnetCount() {
            return subnetCount;
        }

        public void setSubnetCount(int subnetCount) {
            this.subnetCount = subnetCount;
        }

        public int getViolationCount() {
            return violationCount;
        }

        public void setViolationCount(int violationCount) {
            this.violationCount = violationCount;
        }

        public List<PolicyViolation> getViolations() {
            return violations;
        }

        public Set<String> getViolatedRuleIds() {
            return violatedRuleIds;
        }

        public List<String> getMessages() {
            return messages;
        }

        public Map<String, Object> getMetrics() {
            return metrics;
        }
    }

    private final boolean flagMissingPort;

    public static final String INTERNET_EXPOSURE_RULE = "internet-exposure";


    public SegmentationBddEngine() {
        this(true);
    }

    public SegmentationBddEngine(boolean flagMissingPort) {
        this.flagMissingPort = flagMissingPort;
    }

    public Report validate(List<PolicySubnet> subnets, List<PolicyRule> rules) {
        return validate(subnets, rules, Set.of());
    }

    public Report validate(List<PolicySubnet> subnets, List<PolicyRule> rules,
                           Set<String> internetExposedAgents) {
        final Report report = new Report();
        final List<PolicySubnet> inputSubnets = PolicySubnet.copyOf(subnets);
        final List<PolicySubnet> safeSubnets = new ArrayList<>();
        final List<PolicyRule> safeRules = PolicyRule.copyOf(rules);
        report.setSubnetCount(inputSubnets.size());

        final Map<String, PolicySubnet> byId = new LinkedHashMap<>();
        final Map<String, PolicySubnet> byCidr = new LinkedHashMap<>();
        final Set<String> seenIds = new HashSet<>();
        final Set<String> seenCidrs = new HashSet<>();
        for (final PolicySubnet subnet : inputSubnets) {
            if (subnet == null) {
                inputError(report, "invalid-subnet", null, null, "빈 서브넷 항목은 검증할 수 없습니다.");
                continue;
            }
            if (subnet.getId() == null || subnet.getId().isBlank() || !seenIds.add(subnet.getId().trim())) {
                inputError(report, "invalid-subnet-id", subnet.getId(), null, "서브넷 ID는 비어 있지 않고 고유해야 합니다.");
                continue;
            }
            if (subnet.getZoneClass() == null) {
                inputError(report, "classification-required", subnet.getId(), null, "서브넷의 CSO 등급을 지정해야 검증할 수 있습니다.");
                continue;
            }
            try {
                PacketVariables.parseCidr(subnet.getCidr());
            } catch (IllegalArgumentException ex) {
                inputError(report, "invalid-cidr", subnet.getId(), null, "유효한 IPv4 CIDR이 필요합니다: " + subnet.getCidr());
                continue;
            }
            safeSubnets.add(subnet);
            byId.put(subnet.getId().trim(), subnet);
            final String canonical = PolicySubnet.normalizeCidr(subnet.getCidr());
            if (seenCidrs.add(canonical)) byCidr.put(canonical, subnet);
            else byCidr.put(canonical, null);
        }

        final BddManager manager = PacketVariables.newManager();
        final List<BddNode> allowedParts = new ArrayList<>();

        // 비활성 규칙은 검증 대상에서 제외합니다.
        // (자동 수집된 규칙을 지우지 않고 "무시" 로 관리할 수 있게 하기 위함)
        final List<PolicyRule> activeRules = new ArrayList<>();
        final List<PolicyRule> validRules = new ArrayList<>();
        final Set<String> ruleIds = new HashSet<>();
        for (final PolicyRule rule : safeRules) {
            if (rule == null) {
                inputError(report, "invalid-rule", null, null, "빈 규칙 항목은 검증할 수 없습니다.");
            } else if (rule.isEnabled()) {
                if (rule.getId() == null || rule.getId().isBlank() || !ruleIds.add(rule.getId().trim())) {
                    inputError(report, "invalid-rule-id", rule.getSource(), rule.getDestination(), "활성 규칙 ID는 비어 있지 않고 고유해야 합니다.");
                } else {
                    activeRules.add(rule);
                }
            }
        }
        report.setRuleCount(activeRules.size());

        for (final PolicyRule rule : activeRules) {
            try {
                final int protocol = PacketVariables.protocolNumber(rule.getProtocol());
                if (rule.getPort() != PacketVariables.ANY_PORT
                        && (rule.getPort() < 1 || rule.getPort() > 65535)) {
                    throw new IllegalArgumentException("port out of range");
                }
                if (protocol == 1 && rule.hasPort()) {
                    throw new IllegalArgumentException("ICMP does not use transport ports");
                }
            } catch (IllegalArgumentException ex) {
                inputError(report, rule.getId(), rule.getSource(), rule.getDestination(),
                        "프로토콜 또는 포트가 유효하지 않습니다.");
                continue;
            }

            final PolicySubnet source = resolve(rule.getSource(), byId, byCidr);
            final PolicySubnet target = resolve(rule.getDestination(), byId, byCidr);

            if (source == null || target == null) {
                inputError(report, rule.getId(), rule.getSource(), rule.getDestination(),
                        "규칙이 참조하는 서브넷이 없거나 CIDR 참조가 모호합니다. 고유한 서브넷 ID를 사용하세요.");
                continue;
            }

            final BddNode part = ruleToBdd(manager, source, target, rule);
            if (part != null) {
                allowedParts.add(part);
                validRules.add(rule);
            } else {
                inputError(report, rule.getId(), rule.getSource(), rule.getDestination(), "규칙을 BDD로 변환할 수 없습니다.");
            }
        }

        final BddNode allowed = manager.orAll(allowedParts);
        final BddNode forbidden = forbiddenSet(manager, safeSubnets);
        final BddNode violatingSet = manager.and(allowed, forbidden);

        collectForbiddenViolations(manager, violatingSet, allowed, forbidden, byId, byCidr,
                validRules, report);

        if (flagMissingPort) {
            collectMissingPortViolations(manager, validRules, byId, byCidr, report);
        }

        collectInternetExposureViolations(report, safeSubnets, internetExposedAgents);

        // 운영자가 서브넷에 "이것만 허용" 을 적어 넣은 경우, 그 밖으로 나가는
        // 연결을 찾아냅니다. (등급 규칙을 건너뛰지 않아도 위반입니다)
        collectRestrictedEgressViolations(report, safeSubnets, validRules, byId, byCidr);

        report.setViolationCount(report.getViolations().size());
        report.setCompliant(report.isModelValid() && report.getViolations().isEmpty());

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
                    "입력된 정책 모델이 CSO 제약을 준수합니다. (활성 규칙 " + activeRules.size() + "건, 서브넷 "
                            + safeSubnets.size() + "건)");
        } else {
            if (internetExposure > 0) {
                report.getMessages().add(
                        "인터넷 노출 검토 필요 — 기밀망/민감망 " + internetExposure
                                + "건의 장치에 기본 경로가 있습니다. 실제 도달성은 분석하지 않았습니다.");
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
        if (!report.isModelValid()) report.getMessages().add("입력 오류가 있어 정책 모델 검증을 완료할 수 없습니다. 강제 전송도 차단됩니다.");
        report.getMessages().add("검증 범위: 정책 헤더 공간. 실제 경로·ACL 순서·NAT·VRF 도달성은 분석하지 않았습니다.");
        return report;
    }

    private static void inputError(Report report, String ruleId, String source, String target, String reason) {
        report.getInputErrors().add(reason);
        report.getViolations().add(new PolicyViolation(ruleId, source, target, null, null,
                "-", "-", PacketVariables.ANY_PORT, reason, PolicyViolation.Severity.MINOR));
        report.getViolatedRuleIds().add(ruleId);
    }

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
                    + "잠재 노출 관측이며 ACL·NAT·VRF 및 종단 간 도달성을 확인해야 합니다.";
            report.getViolations().add(new PolicyViolation(
                    INTERNET_EXPOSURE_RULE,
                    subnet.getId(),
                    null,
                    zone,
                    ZoneClass.OPEN,
                    "-",
                    "-",
                    PacketVariables.ANY_PORT,
                    reason,
                    confidential ? PolicyViolation.Severity.CRITICAL : PolicyViolation.Severity.MAJOR,
                    "any"));
            report.getViolatedRuleIds().add(INTERNET_EXPOSURE_RULE);
        }
    }

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




                if (!outgoing) {
                    continue;
                }

                final String peerRef = rule.getDestination();
                final PolicySubnet peerSubnet = resolve(peerRef, byId, byCidr);



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

    private boolean matches(String reference, PolicySubnet subnet,
                            Map<String, PolicySubnet> byId,
                            Map<String, PolicySubnet> byCidr) {
        return resolve(reference, byId, byCidr) == subnet;
    }

    private String resolveReference(String reference,
                                    Map<String, PolicySubnet> byId,
                                    Map<String, PolicySubnet> byCidr) {
        final PolicySubnet found = resolve(reference, byId, byCidr);
        if (found != null && found.getCidr() != null && !found.getCidr().isBlank()) {
            return found.getCidr();
        }
        return reference;
    }

    private ZoneClass peerZone(String reference,
                               Map<String, PolicySubnet> byId,
                               Map<String, PolicySubnet> byCidr) {
        final PolicySubnet found = resolve(reference, byId, byCidr);
        return found == null ? null : found.getZoneClass();
    }

    private static String networkAddress(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return "-";
        }
        final int slash = cidr.indexOf('/');
        return slash > 0 ? cidr.substring(0, slash) : cidr;
    }

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

    private static String describeForbidden(PolicySubnet source, PolicySubnet target) {
        final StringBuilder text = new StringBuilder();
        text.append(nameOf(source)).append(" -> ").append(nameOf(target))
                .append(" 연결은 허용되지 않습니다.");


        if (source.getZoneClass() != null && target.getZoneClass() != null) {
            final int gap = Math.abs(source.getZoneClass().level() - target.getZoneClass().level());
            text.append(" (").append(source.getZoneClass().label())
                    .append(" ↔ ").append(target.getZoneClass().label())
                    .append(" — 등급 ").append(gap).append("단계 차이)");
        }
        return text.toString();
    }

    private static String nameOf(PolicySubnet subnet) {
        final String name = (subnet.getName() == null || subnet.getName().isBlank())
                ? subnet.getId()
                : subnet.getName();
        final String cidr = subnet.getCidr();
        return (cidr == null || cidr.isBlank()) ? name : name + " (" + cidr + ")";
    }

    private void collectMissingPortViolations(BddManager manager,
                                              List<PolicyRule> rules,
                                              Map<String, PolicySubnet> byId,
                                              Map<String, PolicySubnet> byCidr,
                                              Report report) {
        for (final PolicyRule rule : rules) {
            if (rule.hasPort()) {
                continue;
            }
            final String protocol = rule.getProtocol() == null || rule.getProtocol().isBlank()
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
