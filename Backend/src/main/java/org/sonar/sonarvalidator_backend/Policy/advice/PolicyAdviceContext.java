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

/**
 * AI 정책 조언에 넘길 <b>판정 맥락 전체</b>입니다.
 *
 * <h2>왜 위반 목록만으로는 부족한가</h2>
 * <p>모델이 "왜 이 연결이 금지인가" 를 판단하려면 <b>등급 체계와 서브넷 구성</b>을
 * 알아야 합니다. 위반 목록만 주면 규칙 이름만 나열하는 일반론이 나옵니다.
 * 그래서 세 가지를 함께 담습니다.
 *
 * <ol>
 *   <li><b>프로젝트</b> — 이름/성격 (군사·의료·금융이면 조언 수위가 다름)</li>
 *   <li><b>서브넷 대장</b> — 어느 대역이 어느 등급인지 (모델이 대안 경로를 계산하는 근거)</li>
 *   <li><b>위반 목록</b> — 이미 정리된 {@link ViolationBrief}</li>
 * </ol>
 *
 * <h2>⚠️ 프롬프트 상한</h2>
 * <p>위반이 수백 건이면 프롬프트가 터집니다. 조언은 <b>심각한 것부터</b>
 * 보는 것이 맞으므로 심각도 순으로 정렬한 뒤 자릅니다. 자른 사실은
 * {@link #truncated()} 로 남겨 서버가 사용자에게 알립니다 — 일부만 보고
 * "전체가 이렇다" 고 단정하면 위험합니다.
 *
 * <h2>⚠️ 응답 맵이 아니라 엔진 리포트를 받는 이유</h2>
 * <p>화면용 {@code violations} 응답 맵을 되받아 파싱하면 <b>타입을 잃습니다</b>
 * (문자열을 다시 등급으로 해석하는 코드가 필요하고, 그 해석이 틀리면 조용히
 * 등급 차이가 -1 이 됩니다). 판정 엔진의 리포트를 직접 받으면 등급이 이미
 * {@code ZoneClass} 이므로 <b>해석 단계가 사라집니다.</b>
 * 화면 계약과의 일치는 {@code PolicyManagement.toViolationMap} 이 같은
 * {@link ViolationBrief#from} 규칙을 쓰는 것으로 보장합니다.
 */
public final class PolicyAdviceContext {

    /**
     * 프롬프트에 넣는 최대 위반 건수입니다.
     *
     * <p>한 건이 약 6줄이라 40건 ≈ 240줄 ≈ 3,000 토큰입니다. 조언의 질은
     * <b>대표 사례</b>로 결정되므로, 전부 넣어 컨텍스트를 태우는 것보다
     * 심각한 순서로 자르는 편이 낫습니다.
     */
    public static final int MAX_BRIEFS = 40;

    /** 프롬프트에 넣는 최대 서브넷 수입니다. */
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

    /**
     * 프로젝트와 판정 리포트로 맥락을 만듭니다.
     *
     * @param project 프로젝트 (null 이면 최소 정보만)
     * @param report  판정 리포트 (null 이면 위반 없음으로 취급)
     * @return 맥락
     */
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
                // ⚠️ 잘림 판정의 기준은 <b>리포트가 보고한 전체 위반 건수</b>입니다.
                //    briefs.size() 를 쓰면 자른 뒤에도 같은 값이라 truncated() 가
                //    영원히 false 가 되고, 서버가 "일부만 봤다" 를 알리지 못합니다.
                totalViolationCount(report, briefs.size()));
    }

    /**
     * 위반 하나를 맥락에서 찾습니다. (카드를 눌러 물은 경우)
     *
     * <p>매칭은 <b>규칙 + 출발 + 도착</b> 세 값으로 합니다. 규칙만으로 찾으면
     * 같은 규칙이 여러 쌍을 위반할 때 엉뚱한 건의 조언이 나옵니다.
     *
     * @param ruleId    규칙 식별자 (필수)
     * @param srcSubnet 출발 서브넷 (없으면 규칙만으로 매칭)
     * @param dstSubnet 도착 서브넷 (없으면 규칙만으로 매칭)
     * @return 찾은 위반, 없으면 null
     */
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

    /**
     * 전체 위반 건수를 정합니다.
     *
     * <p>⚠️ 두 값을 구분해야 합니다.
     * <ul>
     *   <li>{@code report.getViolationCount()} — 판정이 센 <b>전체</b> 위반 건수</li>
     *   <li>{@code parsedCount} — 리포트의 {@code violations} 목록에서
     *       <b>실제로 읽은</b> 건수</li>
     * </ul>
     * <p>정상 경로에서는 둘이 같지만, {@code violation_count} 만 채우고 목록을
     * 비워 보내는 경우가 있을 수 있습니다. 그때 작은 값을 기준으로 삼으면
     * {@code truncated()} 가 거짓이 되어 <b>"일부만 봤다" 는 사실이 사라집니다.</b>
     * 그래서 <b>큰 쪽</b>을 전체로 봅니다 — 잘렸다고 알리는 것이 놓치는 것보다
     * 안전합니다.
     *
     * @param report      판정 리포트 (null 허용)
     * @param parsedCount 파싱한 위반 건수
     * @return 전체 위반 건수
     */
    private static int totalViolationCount(SegmentationBddEngine.Report report, int parsedCount) {
        if (report == null) {
            return parsedCount;
        }
        return Math.max(report.getViolationCount(), parsedCount);
    }

    /** 서브넷 한 줄 설명을 만듭니다. */
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

    /** 심각도 정렬 순위입니다. (낮을수록 먼저) */
    private static int severityRank(String severity) {
        return switch (severity == null ? "" : severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 0;
            case "MAJOR" -> 1;
            default -> 2;
        };
    }

    // -------------------------------------------------------------------------
    // 프롬프트 조립용 접근자
    // -------------------------------------------------------------------------

    /** @return 프로젝트 키 */
    public String projectKey() {
        return projectKey;
    }

    /** @return 프로젝트 이름 */
    public String projectName() {
        return projectName;
    }

    /** @return 프로젝트 성격 (조언 수위 결정에 쓰임) */
    public String category() {
        return category;
    }

    /** @return 규칙 수 */
    public int ruleCount() {
        return ruleCount;
    }

    /** @return 서브넷 수 */
    public int subnetCount() {
        return subnetCount;
    }

    /** @return 위반이 없는지 */
    public boolean compliant() {
        return compliant;
    }

    /** @return 프롬프트에 넣을 위반 목록 (심각도 순, 잘림 적용) */
    public List<ViolationBrief> briefs() {
        return briefs;
    }

    /** @return 프롬프트에 넣을 서브넷 대장 */
    public List<String> subnetLines() {
        return subnetLines;
    }

    /** @return 잘리기 전 전체 위반 건수 */
    public int totalViolationCount() {
        return totalViolationCount;
    }

    /**
     * 위반이 잘렸는지 알려줍니다.
     *
     * <p>⚠️ 잘렸다는 사실을 <b>모델과 사용자 모두에게</b> 알려야 합니다.
     * 일부만 보고 "전체가 이렇다" 고 단정하면 위험합니다.
     *
     * @return 프롬프트가 일부만 담았으면 true
     */
    public boolean truncated() {
        return totalViolationCount > briefs.size();
    }

    /** @return 심각도별 건수 (요약 문장용) */
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

    /** @return 프롬프트에 넣은 위반 줄들을 이어 붙인 본문 */
    public String briefsAsText() {
        return briefs.stream().map(ViolationBrief::describe).collect(Collectors.joining("\n"));
    }
}