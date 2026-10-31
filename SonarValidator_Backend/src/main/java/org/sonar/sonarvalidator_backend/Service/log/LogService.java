package org.sonar.sonarvalidator_backend.Service.log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.sonarvalidator_backend.Model.entity.DeviceLog;
import org.sonar.sonarvalidator_backend.Repository.DeviceLogRepository;
import org.sonar.sonarvalidator_backend.Service.NodeRegistryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    public static final int MAX_PAGE_SIZE = 500;

    public static final int DEFAULT_PAGE_SIZE = 100;

    private final DeviceLogRepository repository;
    private final LogNormalizer normalizer;

    private NodeRegistryService nodeRegistry;

    public LogService(DeviceLogRepository repository, LogNormalizer normalizer) {
        this.repository = repository;
        this.normalizer = normalizer;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setNodeRegistry(NodeRegistryService nodeRegistry) {
        this.nodeRegistry = nodeRegistry;
    }

    @Transactional
    public Map<String, Object> ingest(String agentId,
                                      String product,
                                      String projectKey,
                                      String source,
                                      List<String> lines) {

        final Map<String, Object> result = new LinkedHashMap<>();
        int inserted = 0;
        int duplicated = 0;
        int skipped = 0;

        if (lines == null || lines.isEmpty()) {
            result.put("received", 0);
            result.put("inserted", 0);
            result.put("duplicated", 0);
            result.put("skipped", 0);
            result.put("messages", List.of("수집할 로그 줄이 없습니다."));
            return result;
        }

        final String collectedAt = Instant.now().toString();
        final List<DeviceLog> toSave = new ArrayList<>();

        final org.sonar.sonarvalidator_backend.Model.Configuration node =
                (nodeRegistry == null)
                        ? null
                        : nodeRegistry.resolveOrCreate(agentId,
                                (org.sonar.sonarvalidator_backend.Model.Config.NeutralDeviceConfig) null);

        for (final String line : lines) {
            if (line == null || line.isBlank()) {
                skipped++;
                continue;
            }

            final LogNormalizer.NormalizedLog normalized =
                    normalizer.normalize(line, agentId, product, projectKey, collectedAt);

            // 지문은 원문 + 장비로 만듭니다. 장비가 다르면 같은 문구라도 별개입니다.
            final String fingerprint = fingerprintOf(agentId, normalized.raw);

            final var existing = repository.findByFingerprint(fingerprint);
            if (existing.isPresent()) {
                // 같은 로그가 이미 있습니다. 새 행 대신 반복 횟수만 올립니다.
                final DeviceLog log = existing.get();
                log.setRepeatCount((log.getRepeatCount() == null ? 1 : log.getRepeatCount()) + 1);
                // 반복이 늘면 최근 발생 시각도 갱신합니다. (정렬 상단으로)
                log.setLoggedAt(org.sonar.sonarvalidator_backend.Util.Timestamps.parse(normalized.loggedAt));
                toSave.add(log);
                duplicated++;
                continue;
            }

            final DeviceLog entity = new DeviceLog();
            entity.setNode(node);
            entity.setAgentId(agentId);
            entity.setProjectKey(projectKey);
            entity.setProduct(product);
            entity.setLoggedAt(org.sonar.sonarvalidator_backend.Util.Timestamps.parse(normalized.loggedAt));
            entity.setCollectedAt(org.sonar.sonarvalidator_backend.Util.Timestamps.parse(normalized.collectedAt));
            entity.setSeverityNum(normalized.severityNum);
            entity.setSeverity(normalized.severity);
            entity.setFacility(normalized.facility);
            entity.setMessageId(normalized.messageId);
            entity.setMessage(normalized.message);
            entity.setRaw(normalized.raw);
            entity.setSource(source == null || source.isBlank() ? "agent" : source);
            entity.setFingerprint(fingerprint);
            entity.setRepeatCount(1);
            // 사용자가 표시하지 않은 상태로 시작합니다.
            entity.setHighlighted(false);
            toSave.add(entity);
            inserted++;
        }

        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
        }

        log.info("logs ingested: agent={} received={} inserted={} duplicated={} skipped={}",
                agentId, lines.size(), inserted, duplicated, skipped);

        result.put("received", lines.size());
        result.put("inserted", inserted);
        result.put("duplicated", duplicated);
        result.put("skipped", skipped);
        result.put("messages", List.of());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> query(String agentId,
                                     String projectKey,
                                     String from,
                                     String to,
                                     String severity,
                                     String search,
                                     boolean highlightedOnly,
                                     Integer limit) {

        // 심각도 이름을 숫자로 바꿉니다. 이름이 이상하면 필터를 적용하지 않습니다.
        // (오타로 필터가 조용히 무시되는 것보다, 전체를 보여주고 안내하는 편이 낫습니다)
        final Integer maxSeverity = normalizer.parseSeverity(severity);

        final int pageSize = limit == null || limit <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(MAX_PAGE_SIZE, limit);

        // 저장소 파라미터는 날짜 타입입니다. API 경계는 문자열로 유지하고
        // 여기서 한 번만 변환합니다. (형식이 깨진 값은 null = 제한 없음)
        final Date fromDate = org.sonar.sonarvalidator_backend.Util.Timestamps.parse(from);
        final Date toDate = org.sonar.sonarvalidator_backend.Util.Timestamps.parse(to);
        final String normalizedAgentId = blankToNull(agentId);
        final String normalizedProjectKey = blankToNull(projectKey);
        final String searchTerm = blankToNull(search);
        Specification<DeviceLog> specification = (root, query, builder) ->
            builder.conjunction();
        if (normalizedAgentId != null) {
            specification = specification.and((root, query, builder) ->
                builder.equal(root.get("agentId"), normalizedAgentId));
        }
        if (normalizedProjectKey != null) {
            specification = specification.and((root, query, builder) ->
                builder.equal(root.get("projectKey"), normalizedProjectKey));
        }
        if (fromDate != null) {
            specification = specification.and((root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("loggedAt"), fromDate));
        }
        if (toDate != null) {
            specification = specification.and((root, query, builder) ->
                builder.lessThanOrEqualTo(root.get("loggedAt"), toDate));
        }
        if (maxSeverity != null) {
            specification = specification.and((root, query, builder) ->
                builder.lessThanOrEqualTo(root.get("severityNum"), maxSeverity));
        }
        if (searchTerm != null) {
            final String searchPattern = "%" + searchTerm.toLowerCase(java.util.Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                builder.like(builder.lower(root.get("message")), searchPattern),
                builder.like(builder.lower(root.get("raw")), searchPattern)));
        }
        if (highlightedOnly) {
            specification = specification.and((root, query, builder) ->
                builder.isTrue(root.get("highlighted")));
        }

        final var sort = Sort.by(Sort.Order.desc("loggedAt"), Sort.Order.desc("id"));
        final List<DeviceLog> logs = repository.findAll(
            specification, PageRequest.of(0, pageSize, sort)).getContent();
        final long total = repository.count(specification);

        final Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("agent_id", blankToNull(agentId));
        filters.put("project_id", blankToNull(projectKey));
        filters.put("from", blankToNull(from));
        filters.put("to", blankToNull(to));
        filters.put("severity", severity);
        filters.put("max_severity_num", maxSeverity);
        filters.put("search", blankToNull(search));
        filters.put("highlighted_only", highlightedOnly);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", total);
        body.put("returned", logs.size());
        // 상한에 걸려 잘렸는지 알려 줍니다. 조용히 자르면 사용자가
        // "다 보인다" 고 오해하고 로그를 놓칩니다.
        body.put("truncated", total > logs.size());
        body.put("limit", pageSize);
        body.put("filters", filters);
        body.put("logs", logs.stream().map(this::toView).toList());
        return body;
    }

    @Transactional(readOnly = true)
    public List<DeviceLog> resolveForAnalysis(List<Long> ids,
                                              String agentId,
                                              String projectKey,
                                              String from,
                                              String to,
                                              String severity,
                                              int maxCount) {

        // 사용자가 특정 로그를 골랐으면 그것만 씁니다.
        if (ids != null && !ids.isEmpty()) {
            final List<DeviceLog> selected = repository.findAllById(ids);
            selected.sort(Comparator.comparing(
                    DeviceLog::getLoggedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())));
            return selected.size() > maxCount ? selected.subList(0, maxCount) : selected;
        }

        final Integer maxSeverity = normalizer.parseSeverity(severity);

        // 최신순으로 넉넉히 가져온 뒤 뒤집어 시간순으로 만듭니다.
        // (저장소 쿼리가 최신순이므로, 오래된 것부터 보려면 한 번 뒤집어야 합니다)
        final String normalizedAgentId = blankToNull(agentId);
        final String normalizedProjectKey = blankToNull(projectKey);
        final Date fromDate = org.sonar.sonarvalidator_backend.Util.Timestamps.parse(from);
        final Date toDate = org.sonar.sonarvalidator_backend.Util.Timestamps.parse(to);
        Specification<DeviceLog> specification = (root, query, builder) ->
            builder.conjunction();
        if (normalizedAgentId != null) {
            specification = specification.and((root, query, builder) ->
                builder.equal(root.get("agentId"), normalizedAgentId));
        }
        if (normalizedProjectKey != null) {
            specification = specification.and((root, query, builder) ->
                builder.equal(root.get("projectKey"), normalizedProjectKey));
        }
        if (fromDate != null) {
            specification = specification.and((root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("loggedAt"), fromDate));
        }
        if (toDate != null) {
            specification = specification.and((root, query, builder) ->
                builder.lessThanOrEqualTo(root.get("loggedAt"), toDate));
        }
        if (maxSeverity != null) {
            specification = specification.and((root, query, builder) ->
                builder.lessThanOrEqualTo(root.get("severityNum"), maxSeverity));
        }
        final List<DeviceLog> recent = repository.findAll(
            specification,
            PageRequest.of(0, maxCount, Sort.by(Sort.Order.desc("loggedAt"), Sort.Order.desc("id"))))
            .getContent();

        final List<DeviceLog> ordered = new ArrayList<>(recent);
        java.util.Collections.reverse(ordered);
        return ordered;
    }

    @Transactional(readOnly = true)
    public long countFor(String agentId, String projectKey, String from, String to, String severity) {
        final String normalizedAgentId = blankToNull(agentId);
        final String normalizedProjectKey = blankToNull(projectKey);
        final Date fromDate = org.sonar.sonarvalidator_backend.Util.Timestamps.parse(from);
        final Date toDate = org.sonar.sonarvalidator_backend.Util.Timestamps.parse(to);
        final Integer maxSeverity = normalizer.parseSeverity(severity);
        Specification<DeviceLog> specification = (root, query, builder) ->
            builder.conjunction();
        if (normalizedAgentId != null) {
            specification = specification.and((root, query, builder) ->
                builder.equal(root.get("agentId"), normalizedAgentId));
        }
        if (normalizedProjectKey != null) {
            specification = specification.and((root, query, builder) ->
                builder.equal(root.get("projectKey"), normalizedProjectKey));
        }
        if (fromDate != null) {
            specification = specification.and((root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("loggedAt"), fromDate));
        }
        if (toDate != null) {
            specification = specification.and((root, query, builder) ->
                builder.lessThanOrEqualTo(root.get("loggedAt"), toDate));
        }
        if (maxSeverity != null) {
            specification = specification.and((root, query, builder) ->
                builder.lessThanOrEqualTo(root.get("severityNum"), maxSeverity));
        }
        return repository.count(specification);
    }

    @Transactional
    public Map<String, Object> updateFlags(Long id, Boolean highlighted, String note) {
        final DeviceLog log = repository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("로그를 찾을 수 없습니다: " + id));

        if (highlighted != null) {
            log.setHighlighted(highlighted);
        }
        if (note != null) {
            log.setNote(note);
        }

        return toView(repository.save(log));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> filterOptions() {
        final List<Map<String, Object>> severities = new ArrayList<>();
        // 화면에는 심각한 것부터 보여 줍니다. (사용자가 보통 그것을 찾습니다)
        final String[] names = {"critical", "error", "warning", "notice", "info", "debug"};
        final int[] numbers = {2, 3, 4, 5, 6, 7};
        for (int i = 0; i < names.length; i++) {
            final Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", names[i]);
            item.put("num", numbers[i]);
            severities.add(item);
        }

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("agents", repository.distinctAgentIds());
        body.put("severities", severities);
        body.put("total", repository.count());
        return body;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> severitySummary() {
        final List<Map<String, Object>> summary = new ArrayList<>();
        for (final Object[] row : repository.countBySeverity()) {
            final int num = row[0] == null ? 6 : ((Number) row[0]).intValue();
            final Map<String, Object> item = new LinkedHashMap<>();
            item.put("severity", LogNormalizer.nameOf(num));
            item.put("num", num);
            item.put("count", row[1] == null ? 0 : ((Number) row[1]).longValue());
            summary.add(item);
        }
        return summary;
    }

    public Map<String, Object> toView(DeviceLog log) {
        final Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", log.getId());
        view.put("node_id", log.nodeId());
        view.put("agent_id", log.getAgentId());
        view.put("project_id", log.getProjectKey());
        view.put("product", log.getProduct());
        view.put("logged_at", org.sonar.sonarvalidator_backend.Util.Timestamps.iso(log.getLoggedAt()));
        view.put("collected_at", org.sonar.sonarvalidator_backend.Util.Timestamps.iso(log.getCollectedAt()));
        view.put("severity", log.getSeverity());
        view.put("severity_num", log.getSeverityNum());
        view.put("facility", log.getFacility());
        view.put("message_id", log.getMessageId());
        view.put("message", log.getMessage());
        view.put("raw", log.getRaw());
        view.put("source", log.getSource());
        view.put("repeat_count", log.getRepeatCount());
        view.put("highlighted", Boolean.TRUE.equals(log.getHighlighted()));
        view.put("note", log.getNote());
        return view;
    }

    private String fingerprintOf(String agentId, String raw) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((agentId == null ? "" : agentId).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest()).substring(0, 32);
        } catch (Exception ex) {
            // 해시를 못 만들면 지문을 비웁니다. 그러면 중복 제거가 안 될 뿐,
            // 저장 자체는 계속됩니다. (로그를 잃는 것보다 낫습니다)
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
