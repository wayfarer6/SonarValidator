package org.sonar.sonarvalidator_backend.Policy.advice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record PolicyAdviceAnswer(String riskLevel,
                                 String summary,
                                 String rootCause,
                                 String policyAdvice,
                                 List<PolicyAdviceOption> options,
                                 List<String> evidence,
                                 boolean needsMoreData,
                                 boolean structured,
                                 String raw) {

    private static final int MAX_STORED_RAW = 30_000;

    public static PolicyAdviceAnswer unstructured(String raw) {
        return new PolicyAdviceAnswer(null, null, null, null,
                List.of(), List.of(), false, false, raw);
    }

    public Map<String, Object> toView() {
        final Map<String, Object> view = new LinkedHashMap<>();
        view.put("risk_level", riskLevel);
        view.put("summary", summary);
        view.put("root_cause", rootCause);
        view.put("policy_advice", policyAdvice);

        final List<Map<String, Object>> optionViews = new ArrayList<>();
        for (final PolicyAdviceOption option : options) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("title", option.title());
            entry.put("approach", option.approach());
            entry.put("security_impact", option.securityImpact());
            entry.put("operational_cost", option.operationalCost());
            entry.put("recommended", option.recommended());
            optionViews.add(entry);
        }
        view.put("options", optionViews);
        view.put("evidence", evidence);
        view.put("needs_more_data", needsMoreData);
        view.put("structured", structured);
        view.put("raw_response", raw);
        return view;
    }

    public String truncatedRaw() {
        if (raw == null) {
            return null;
        }
        return raw.length() <= MAX_STORED_RAW ? raw : raw.substring(0, MAX_STORED_RAW);
    }

    public static String normalizeRisk(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String upper = value.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "CRITICAL", "SEVERE" -> "CRITICAL";
            case "HIGH", "MAJOR" -> "HIGH";
            case "MEDIUM", "MODERATE", "WARN", "WARNING" -> "MEDIUM";
            case "LOW", "INFO", "OK", "NONE" -> "LOW";
            default -> null;
        };
    }
}
