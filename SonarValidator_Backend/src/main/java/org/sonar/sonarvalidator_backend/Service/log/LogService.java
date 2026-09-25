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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장비 로그의 수집·조회·필터를 담당합니다.
 *
 * <h2>수집 경로</h2>
 * <ol>
 *   <li>프로버(Agent)가 텔레메트리에 로그를 실어 보냄</li>
 *   <li>사용자가 파일로 업로드 (오프라인 장비용)</li>
 *   <li>화면에서 직접 입력 (테스트/소량)</li>
 * </ol>
 * <p>세 경로 모두 {@link #ingest} 로 들어와 <b>같은 정규화</b>를 거칩니다.
 * 경로별로 정규화가 다르면 같은 로그가 다르게 분류되어 필터가 어긋납니다.
 *
 * <h2>왜 반드시 상한이 필요한가</h2>
 * <p>로그는 다른 데이터보다 훨씬 빠르게 늘어납니다. 화면 조회에 상한을 두지
 * 않으면 수만 건을 한 번에 내려보내 브라우저가 멈춥니다. 그래서
 * {@link #MAX_PAGE_SIZE} 를 넘는 요청은 조용히 자릅니다.
 */
@Service
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    /** 한 번에 내려줄 수 있는 최대 로그 건수입니다. */
    public static final int MAX_PAGE_SIZE = 500;

    /** 기본 페이지 크기입니다. */
    public static final int DEFAULT_PAGE_SIZE = 100;

    private final DeviceLogRepository repository;
    private final LogNormalizer normalizer;

    /**
     * @param repository 로그 저장소
     * @param normalizer 로그 정규화기
     */
    public LogService(DeviceLogRepository repository, LogNormalizer normalizer) {
        this.repository = repository;
        this.normalizer = normalizer;
    }

    /**
     * 로그 여러 줄을 수집합니다.
     *
     * <p>각 줄을 정규화해 저장하고, <b>지문이 같은 기존 로그</b>가 있으면
     * 새 행을 만들지 않고 {@code repeatCount} 만 올립니다. 프로버가 주기적으로
     * 같은 로그를 다시 보내는 일이 흔한데, 그때마다 새 행을 만들면 같은 내용이
     * 수십 개 쌓여 화면과 AI 프롬프트가 모두 쓸모없어집니다.
     *
     * @param agentId    장비 식별자
     * @param product    제품명 (심각도 해석 힌트)
     * @param projectKey 프로젝트 키 (없으면 null)
     * @param source     출처 ({@code agent}/{@code upload}/{@code manual})
     * @param lines      로그 원문 목록
     * @return {@code {received, inserted, duplicated, skipped}}
     */
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

    /**
     * 필터 조합으로 로그를 조회합니다.
     *
     * @param agentId       장비 식별자 (null 이면 전체)
     * @param projectKey    프로젝트 키 (null 이면 전체)
     * @param from          기간 시작 ISO-8601 (null 이면 제한 없음)
     * @param to            기간 끝 ISO-8601 (null 이면 제한 없음)
     * @param severity      최소 심각도 이름 (예: {@code warning}). null 이면 전체
     * @param search        검색어 (null 이면 전체)
     * @param highlightedOnly 표시된 로그만
     * @param limit         페이지 크기 (null 이면 기본값)
     * @return {@code {total, returned, logs, filters}}
     */
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

        final List<DeviceLog> logs = repository.search(
                blankToNull(agentId),
                blankToNull(projectKey),
                fromDate,
                toDate,
                maxSeverity,
                blankToNull(search),
                highlightedOnly,
                PageRequest.of(0, pageSize));

        final long total = repository.countMatching(
                blankToNull(agentId),
                blankToNull(projectKey),
                fromDate,
                toDate,
                maxSeverity,
                blankToNull(search),
                highlightedOnly);

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

    /**
     * 분석에 쓸 로그 목록을 가져옵니다. (엔티티 그대로)
     *
     * <p>조회 API 와 분리한 이유: 분석은 시간순(오래된 것 먼저)으로 읽어야
     * 사건의 <b>인과 순서</b>를 AI 가 파악할 수 있습니다. 화면은 최신순이
     * 자연스럽지만 분석은 반대입니다.
     *
     * @param ids 지정한 로그 ID 목록 (비어 있으면 필터 조회)
     * @param agentId     장비 식별자
     * @param projectKey  프로젝트 키
     * @param from        기간 시작
     * @param to          기간 끝
     * @param severity    최소 심각도
     * @param maxCount    최대 건수
     * @return 시간순(오래된 먼저) 로그 목록
     */
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
        final List<DeviceLog> recent = repository.search(
                blankToNull(agentId),
                blankToNull(projectKey),
                org.sonar.sonarvalidator_backend.Util.Timestamps.parse(from),
                org.sonar.sonarvalidator_backend.Util.Timestamps.parse(to),
                maxSeverity,
                null,
                false,
                PageRequest.of(0, maxCount));

        final List<DeviceLog> ordered = new ArrayList<>(recent);
        java.util.Collections.reverse(ordered);
        return ordered;
    }

    /** 필터에 걸린 전체 건수를 셉니다. (분석 시 잘림 여부 판단용) */
    @Transactional(readOnly = true)
    public long countFor(String agentId, String projectKey, String from, String to, String severity) {
        return repository.countMatching(
                blankToNull(agentId),
                blankToNull(projectKey),
                org.sonar.sonarvalidator_backend.Util.Timestamps.parse(from),
                org.sonar.sonarvalidator_backend.Util.Timestamps.parse(to),
                normalizer.parseSeverity(severity),
                null,
                false);
    }

    /**
     * 로그 한 건의 사용자 메모/표시 여부를 바꿉니다.
     *
     * <p>"이 로그를 분석해 달라" 는 의도를 남기는 수단입니다. 표시해 둔 로그는
     * {@code highlighted_only} 필터로 모아 볼 수 있습니다.
     *
     * @param id          로그 키
     * @param highlighted 표시 여부 (null 이면 유지)
     * @param note        메모 (null 이면 유지)
     * @return 수정된 로그 (화면용)
     * @throws IllegalArgumentException 없을 때
     */
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

    /**
     * 필터 드롭다운에 쓸 선택지 목록을 만듭니다.
     *
     * @return {@code {agents, severities, total}}
     */
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

    /** 심각도별 건수입니다. (요약 카드용) */
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

    /** 로그 한 건을 화면용 맵으로 바꿉니다. */
    public Map<String, Object> toView(DeviceLog log) {
        final Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", log.getId());
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

    /**
     * 로그 지문을 만듭니다.
     *
     * <p>SHA-256 의 앞 32자를 씁니다. 전체 해시는 컬럼이 커지고(64자),
     * 32자(128비트)면 이 규모에서 충돌 가능성이 사실상 없습니다.
     */
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

    /** 빈 문자열을 null 로 바꿉니다. (쿼리의 조건 무시 판정용) */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
