package org.sonar.sonarvalidator_backend.Policy.advice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.sonar.sonarvalidator_backend.Model.entity.Project;
import org.sonar.sonarvalidator_backend.Policy.PolicySubnet;
import org.sonar.sonarvalidator_backend.Policy.PolicyViolation;
import org.sonar.sonarvalidator_backend.Policy.SegmentationBddEngine;

public final class PolicyAdviceContext {

    public static final int MAX_BRIEFS = 40;

    public static final int MAX_SUBNETS = 24;

    private final String projectKey;
    private final String projectName;
    private final String category;
    private final int ruleCount;
    private final int subnetCount;
    private final boolean compliant;
    private final List<ViolationBrief> briefs;
    private final List<String> subnetLines;
    private final int totalViolationCount;

    private PolicyAdviceContext(String projectKey,
                                String projectName,
                                String category,
                                int ruleCount,
                                int subnetCount,
                                boolean compliant,
                                List<ViolationBrief> briefs,
                                List<String> subnetLines,
                                int totalViolationCount) {
        this.projectKey = projectKey;
        this.projectName = projectName;
        this.category = category;
        this.ruleCount = ruleCount;
        this.subnetCount = subnetCount;
        this.compliant = compliant;
        this.briefs = List.copyOf(briefs);
        this.subnetLines = List.copyOf(subnetLines);
        this.totalViolationCount = totalViolationCount;
    }

    public static PolicyAdviceContext of(Project project, SegmentationBddEngine.Report report) {

        final List<ViolationBrief> briefs = new ArrayList<>();
        if (report != null && report.getViolations() != null) {
            for (final PolicyViolation violation : report.getViolations()) {
                final ViolationBrief brief = ViolationBrief.from(violation);
                if (brief != null) {
                    briefs.add(brief);
                }
            }
        }

        // ⚠️ 심각도 순 정렬: CRITICAL → MAJOR → MINOR.
        //    자를 때 덜 중요한 것이 먼저 잘려야 합니다.
        briefs.sort((left, right) ->
                Integer.compare(severityRank(left.severity()), severityRank(right.severity())));

        final List<ViolationBrief> limited = briefs.size() > MAX_BRIEFS
                ? new ArrayList<>(briefs.subList(0, MAX_BRIEFS))
                : briefs;

        final List<String> subnetLines = new ArrayList<>();
        if (project != null) {
            final List<PolicySubnet> subnets = project.toPolicySubnets();
            int count = 0;
            for (final PolicySubnet subnet : subnets) {
                if (count++ >= MAX_SUBNETS) {
                    break;
                }
                subnetLines.add(describeSubnet(subnet));
            }
        }

        return new PolicyAdviceContext(
                project == null ? null : project.getProjectKey(),
                project == null ? "(알 수 없음)" : project.getName(),
                project == null || project.getCategory() == null
                        ? "미분류"
                        : String.valueOf(project.getCategory()),
                report == null ? 0 : report.getRuleCount(),
                report == null ? 0 : report.getSubnetCount(),
                report != null && report.isCompliant(),
                limited,
                subnetLines,



                totalViolationCount(report, briefs.size()));
    }

    public ViolationBrief findBrief(String ruleId, String srcSubnet, String dstSubnet) {
        if (ruleId == null || ruleId.isBlank()) {
            return null;
        }
        final String wanted = ruleId.trim();

        ViolationBrief fallback = null;
        for (final ViolationBrief brief : briefs) {
            if (!wanted.equalsIgnoreCase(brief.ruleId())) {
                continue;
            }
            if (fallback == null) {
                fallback = brief;
            }
            if (matches(brief.srcLabel(), srcSubnet) && matches(brief.dstLabel(), dstSubnet)) {
                return brief;
            }
        }
        // 정확히 일치하는 쌍이 없으면 규칙만 맞는 첫 건을 돌려줍니다.
        // (프론트가 src/dst 를 안 보냈을 때도 동작해야 합니다)
        return fallback;
    }

    private static boolean matches(String label, String wanted) {
        return wanted == null || wanted.isBlank() || wanted.trim().equalsIgnoreCase(label);
    }

    private static int totalViolationCount(SegmentationBddEngine.Report report, int parsedCount) {
        if (report == null) {
            return parsedCount;
        }
        return Math.max(report.getViolationCount(), parsedCount);
    }

    private static String describeSubnet(PolicySubnet subnet) {
        if (subnet == null) {
            return "-";
        }
        final String name = subnet.getName() == null || subnet.getName().isBlank()
                ? subnet.getId()
                : subnet.getName();
        final String zone = subnet.getZoneClass() == null
                ? "미지정"
                : subnet.getZoneClass().label();
        final String cidr = subnet.getCidr() == null ? "대역 미지정" : subnet.getCidr();
        final String agent = subnet.getAgentId() == null || subnet.getAgentId().isBlank()
                ? ""
                : ", 장치 " + subnet.getAgentId();
        return "- " + name + " (" + subnet.getId() + ") : " + cidr + ", 등급 " + zone + agent;
    }

    private static int severityRank(String severity) {
        return switch (severity == null ? "" : severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 0;
            case "MAJOR" -> 1;
            default -> 2;
        };
    }

    // 프롬프트 조립용 접근자

    public String projectKey() {
        return projectKey;
    }

    public String projectName() {
        return projectName;
    }

    public String category() {
        return category;
    }

    public int ruleCount() {
        return ruleCount;
    }

    public int subnetCount() {
        return subnetCount;
    }

    public boolean compliant() {
        return compliant;
    }

    public List<ViolationBrief> briefs() {
        return briefs;
    }

    public List<String> subnetLines() {
        return subnetLines;
    }

    public int totalViolationCount() {
        return totalViolationCount;
    }

    public boolean truncated() {
        return totalViolationCount > briefs.size();
    }

    public Map<String, Integer> bySeverity() {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("CRITICAL", 0);
        counts.put("MAJOR", 0);
        counts.put("MINOR", 0);
        for (final ViolationBrief brief : briefs) {
            counts.merge(brief.severity(), 1, Integer::sum);
        }
        return counts;
    }

    public String briefsAsText() {
        return briefs.stream().map(ViolationBrief::describe).collect(Collectors.joining("\n"));
    }
}
