package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.entity.Notification;
import org.sonar.sonarvalidator_backend.Repository.NotificationRepository;
import org.sonar.sonarvalidator_backend.Service.NotificationService;
import org.springframework.data.domain.Pageable;

import tools.jackson.databind.json.JsonMapper;

/**
 * 알림 기록/조회 계약을 저장소 없이 검증합니다.
 *
 * <h2>왜 저장소 스텁을 쓰는가</h2>
 * 알림 서비스의 위험한 로직은 <b>DB 종류와 무관한 부분</b>에 있습니다.
 * <ul>
 *   <li>중복 키가 같은 알림을 합치는가 (5분 창)</li>
 *   <li>분류/심각도의 잘못된 값이 목록을 비게 만들지 않는가</li>
 *   <li>검색어가 없을 때 LIKE 패턴이 null 로 나가는가</li>
 * </ul>
 * 그리고 <b>실제로 두 번 물렸던 버그</b>는 모두 여기서 잡힙니다.
 *
 * <h2>⚠️ 이 테스트가 잡는 실제 버그 2건</h2>
 * <ol>
 *   <li><b>읽음 필터 뒤집힘</b> — {@code unread_only=true} 를 서버가 그대로
 *       "읽음 여부" 로 해석해 <b>읽은 것만</b> 돌려주던 문제. 두 값이 모두
 *       boolean 이라 컴파일러가 잡지 못했고, 응답이 그럴듯해서 화면에서도
 *       눈치채기 어려웠습니다.</li>
 *   <li><b>PostgreSQL LOWER(bytea)</b> — 검색어가 없을 때 파라미터가 untyped
 *       null 로 바인딩되어 {@code function lower(bytea) does not exist} 로
 *       조회가 500 이 되던 문제. H2 에서는 통과하고 PostgreSQL 에서만
 *       터지므로 테스트 DB 로는 잡히지 않았습니다.</li>
 * </ol>
 */
class NotificationServiceTest {

    private RecordingRepository repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = new RecordingRepository();
        service = new NotificationService(repository);
    }

    // ------------------------------------------------------------------
    //  기록
    // ------------------------------------------------------------------

    @Test
    @DisplayName("알림을 기록하면 외부 식별자와 기본 상태가 채워진다")
    void notifyAssignsIdentityAndDefaults() {
        final Notification saved = service.notify(
                "POLICY", "critical", "제목", "본문",
                "PRJ-1", "AGT-1", "system", "/project/editor/PRJ-1", null);

        assertNotNull(saved.getNotificationId());
        assertTrue(saved.getNotificationId().startsWith("NTF-"));
        assertEquals("POLICY", saved.getCategory());
        assertEquals("critical", saved.getSeverity());
        assertFalse(saved.isRead());
        assertEquals(1, saved.getRepeatCount());
        assertEquals("PRJ-1", saved.getProjectKey());
        assertNotNull(saved.getOccurredAt());
    }

    @Test
    @DisplayName("알 수 없는 분류/심각도는 안전한 기본값으로 정규화된다")
    void notifyNormalizesUnknownValues() {
        // 자유 문자열을 그대로 저장하면 오타 분류가 생겨 화면 필터가 비어 보입니다.
        final Notification saved = service.notify(
                "오타분류", "치명적", "제목", "본문", null, null, null, null, null);

        assertEquals("SYSTEM", saved.getCategory());
        assertEquals("info", saved.getSeverity());
        // source 가 비면 "system" 으로 두어 "누가 만들었나" 가 비지 않게 합니다.
        assertEquals("system", saved.getSource());
    }

    @Test
    @DisplayName("같은 중복 키가 창 안에서 반복되면 새 행을 만들지 않고 횟수만 올린다")
    void notifyDedupesWithinWindow() {
        final String key = "policy-push-rejected:PRJ-1:1";
        final Notification first = service.notify(
                "POLICY", "critical", "푸시 거부", "위반 1건", "PRJ-1", null, "system", null, key);
        final Notification second = service.notify(
                "POLICY", "critical", "푸시 거부", "위반 1건", "PRJ-1", null, "system", null, key);

        // 같은 행이 갱신되어야 합니다. (행이 2개면 목록이 같은 문구로 도배됩니다)
        assertEquals(first.getNotificationId(), second.getNotificationId());
        assertEquals(2, second.getRepeatCount());
        assertEquals(1, repository.saved.size(), "새 행이 만들어지면 안 됩니다");
    }

    @Test
    @DisplayName("중복 키가 없으면 매번 새 알림을 만든다")
    void notifyWithoutDedupeKeyCreatesNewRows() {
        service.notify("PROJECT", "info", "생성", null, "PRJ-1", null, "system", null, null);
        service.notify("PROJECT", "info", "생성", null, "PRJ-1", null, "system", null, null);

        // 정책 수정/프로젝트 생성은 운영자의 의도된 행동이므로 합치면 이력을 잃습니다.
        assertEquals(2, repository.saved.size());
    }

    @Test
    @DisplayName("반복되면서 심각도가 올라가면 최신 심각도로 갱신된다")
    void notifyEscalatesSeverityOnRepeat() {
        final String key = "k";
        service.notify("POLICY", "info", "제목", null, null, null, "system", null, key);
        final Notification escalated = service.notify(
                "POLICY", "critical", "제목", null, null, null, "system", null, key);

        assertEquals("critical", escalated.getSeverity());
    }

    @Test
    @DisplayName("기록 실패는 예외를 삼켜 본 작업을 막지 않는다")
    void notifyQuietlySwallowsFailure() {
        repository.failOnSave = true;

        // 예외가 밖으로 나가면 정책 푸시 자체가 실패합니다. (훨씬 나쁩니다)
        service.notifyQuietly("POLICY", "info", "제목", null, null, null, "system", null, null);
    }

    // ------------------------------------------------------------------
    //  조회 — 검색 패턴 (PostgreSQL 회귀)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("검색어가 없으면 LIKE 패턴은 null 로 전달된다")
    void searchWithoutTermPassesNullPattern() {
        service.search(null, null, null, null, null, null, 50);

        // ⚠️ 패턴을 JPQL 안에서 만들면 파라미터가 untyped null 이 되어
        // PostgreSQL 이 lower(bytea) 로 해석하고 500 을 냅니다.
        assertNull(repository.lastPattern, "검색어가 없으면 패턴도 null 이어야 합니다");
    }

    @Test
    @DisplayName("검색어는 소문자 %패턴% 으로 변환되어 전달된다")
    void searchBuildsLowercasePattern() {
        service.search(null, null, null, null, null, "TIMEOUT", 50);

        // 대소문자 무시는 컬럼에만 LOWER 를 적용하고 패턴은 미리 소문자로 만듭니다.
        assertEquals("%timeout%", repository.lastPattern);
    }

    @Test
    @DisplayName("공백뿐인 검색어는 없는 것으로 본다")
    void blankSearchIsIgnored() {
        service.search(null, null, null, null, null, "   ", 50);

        assertNull(repository.lastPattern);
    }

    // ------------------------------------------------------------------
    //  조회 — 읽음 필터 (뒤집힘 회귀)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("읽음 상태는 3상태 문자열로 저장소에 전달된다")
    void readStateIsForwardedAsString() {
        service.search(null, null, "unread", null, null, null, 50);
        assertEquals("unread", repository.lastReadState);

        service.search(null, null, "read", null, null, null, 50);
        assertEquals("read", repository.lastReadState);

        service.search(null, null, null, null, null, null, 50);
        assertNull(repository.lastReadState);
    }

    @Test
    @DisplayName("잘못된 읽음 상태는 전체로 되돌린다 (결과가 비는 것을 막는다)")
    void unknownReadStateFallsBackToAll() {
        service.search(null, null, "안읽음", null, null, null, 50);

        assertNull(repository.lastReadState);
    }

    @Test
    @DisplayName("읽음 상태는 대소문자를 가리지 않는다")
    void readStateIsCaseInsensitive() {
        service.search(null, null, "UNREAD", null, null, null, 50);

        assertEquals("unread", repository.lastReadState);
    }

    // ------------------------------------------------------------------
    //  읽음 처리 / 삭제
    // ------------------------------------------------------------------

    @Test
    @DisplayName("읽음/안읽음 토글이 저장소에 반영된다")
    void markReadAndUnread() {
        final Notification saved = service.notify(
                "PROJECT", "info", "제목", null, null, null, "system", null, null);
        repository.byId = saved;

        assertTrue(service.markRead(saved.getNotificationId()).isRead());
        assertFalse(service.markUnread(saved.getNotificationId()).isRead());
    }

    @Test
    @DisplayName("없는 알림을 읽음 처리하면 404 로 매핑되는 예외가 난다")
    void markReadOnMissingNotificationThrows() {
        repository.byId = null;

        assertThrows(NotificationService.NotificationNotFoundException.class,
                () -> service.markRead("NTF-NOPE"));
    }

    @Test
    @DisplayName("읽음은 되돌릴 수 있다 (실수로 눌러도 복구 가능)")
    void unreadIsReversible() {
        final Notification saved = service.notify(
                "PROJECT", "info", "제목", null, null, null, "system", null, null);
        repository.byId = saved;

        service.markRead(saved.getNotificationId());
        final Notification restored = service.markUnread(saved.getNotificationId());

        // 되돌릴 수 없으면 운영자는 중요 알림을 놓칠까 봐 읽음 처리를 미루게 됩니다.
        assertFalse(restored.isRead());
    }

    // ------------------------------------------------------------------
    //  테스트용 저장소 스텁
    // ------------------------------------------------------------------

    /**
     * 실제 쿼리를 실행하지 않고 <b>전달된 인자만 기록</b>하는 스텁입니다.
     *
     * <p>이 테스트의 관심사는 "서비스가 저장소에 무엇을 넘기는가" 입니다.
     * DB 가 개입하면 PostgreSQL 전용 문제를 H2 로는 재현할 수 없습니다.
     */
    private static class RecordingRepository
            implements NotificationRepository {

        /** save 로 넘어온 알림들. */
        final List<Notification> saved = new java.util.ArrayList<>();

        /** findFirstByDedupeKey... 의 판정에 쓰이지 않습니다 (저장된 목록에서 찾습니다). */
        @SuppressWarnings("unused")
        Notification duplicate;

        /** findByNotificationId 가 돌려줄 대상. */
        Notification byId;

        /** true 면 save 가 실패합니다. */
        boolean failOnSave;

        /** 마지막 search 호출에 전달된 LIKE 패턴. */
        String lastPattern;

        /** 마지막 search 호출에 전달된 읽음 상태. */
        String lastReadState;

        @Override
        public List<Notification> search(String category, String severity, String readState,
                                         String projectKey, String agentId,
                                         String searchPattern, Pageable pageable) {
            this.lastReadState = readState;
            this.lastPattern = searchPattern;
            return List.of();
        }

        @Override
        public java.util.Optional<Notification> findFirstByDedupeKeyOrderByOccurredAtDesc(
                String dedupeKey) {
            if (dedupeKey == null) {
                return java.util.Optional.empty();
            }
            // 실제 DB 처럼 "이미 저장된 것 중 같은 키" 를 찾습니다.
            // (필드에 미리 넣어 두면 중복 합치기 흐름을 검증할 수 없습니다)
            return saved.stream()
                    .filter(n -> dedupeKey.equals(n.getDedupeKey()))
                    .findFirst();
        }

        @Override
        public java.util.Optional<Notification> findByNotificationId(String notificationId) {
            return java.util.Optional.ofNullable(byId);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S extends Notification> S save(S entity) {
            if (failOnSave) {
                throw new IllegalStateException("simulated save failure");
            }
            // 같은 행이 다시 저장되면 중복 추가하지 않습니다.
            // (중복 합치기 테스트에서 행 개수를 세기 때문)
            if (!saved.contains((Notification) entity)) {
                saved.add((Notification) entity);
            }
            return entity;
        }

        @Override
        public long countByReadFalse() {
            return 0;
        }

        @Override
        public List<Notification> findByReadFalseOrderByOccurredAtDesc(Pageable pageable) {
            return List.of();
        }

        @Override
        public List<Object[]> countByCategory() {
            return List.of();
        }

        @Override
        public List<Object[]> countBySeverity() {
            return List.of();
        }

        @Override
        public void delete(Notification entity) {
            saved.remove(entity);
        }

        @Override
        public void deleteAll() {
            saved.clear();
        }

        @Override
        public long count() {
            return saved.size();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S extends Notification> List<S> saveAll(Iterable<S> entities) {
            final List<S> result = new java.util.ArrayList<>();
            for (final S entity : entities) {
                result.add((S) save(entity));
            }
            return result;
        }

        // 아래 메서드들은 이 테스트에서 쓰이지 않으므로 최소 구현만 둡니다.
        @Override
        public List<Notification> findAll() {
            return List.copyOf(saved);
        }

        @Override
        public List<Notification> findAllById(Iterable<Long> ids) {
            return List.copyOf(saved);
        }

        @Override
        public java.util.Optional<Notification> findById(Long id) {
            return java.util.Optional.empty();
        }

        @Override
        public boolean existsById(Long id) {
            return false;
        }

        @Override
        public void deleteById(Long id) {
            // 사용하지 않습니다.
        }

        @Override
        public void deleteAllById(Iterable<? extends Long> ids) {
            // 사용하지 않습니다.
        }

        @Override
        public void deleteAll(Iterable<? extends Notification> entities) {
            // 사용하지 않습니다.
        }

        @Override
        public void flush() {
            // 사용하지 않습니다.
        }

        @Override
        public <S extends Notification> List<S> saveAllAndFlush(Iterable<S> entities) {
            return saveAll(entities);
        }

        @Override
        public void deleteAllInBatch(Iterable<Notification> entities) {
            // 사용하지 않습니다.
        }

        @Override
        public void deleteAllByIdInBatch(Iterable<Long> ids) {
            // 사용하지 않습니다.
        }

        @Override
        public void deleteAllInBatch() {
            // 사용하지 않습니다.
        }

        @Override
        public Notification getOne(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Notification getById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Notification getReferenceById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S extends Notification> java.util.Optional<S> findOne(
                org.springframework.data.domain.Example<S> example) {
            return java.util.Optional.empty();
        }

        @Override
        public <S extends Notification> List<S> findAll(
                org.springframework.data.domain.Example<S> example) {
            return List.of();
        }

        @Override
        public <S extends Notification> List<S> findAll(
                org.springframework.data.domain.Example<S> example,
                org.springframework.data.domain.Sort sort) {
            return List.of();
        }

        @Override
        public <S extends Notification> org.springframework.data.domain.Page<S> findAll(
                org.springframework.data.domain.Example<S> example, Pageable pageable) {
            return org.springframework.data.domain.Page.empty();
        }

        @Override
        public <S extends Notification> long count(
                org.springframework.data.domain.Example<S> example) {
            return 0;
        }

        @Override
        public <S extends Notification> boolean exists(
                org.springframework.data.domain.Example<S> example) {
            return false;
        }

        @Override
        public <S extends Notification, R> R findBy(
                org.springframework.data.domain.Example<S> example,
                java.util.function.Function<
                        org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S extends Notification> S saveAndFlush(S entity) {
            return save(entity);
        }

        @Override
        public List<Notification> findAll(org.springframework.data.domain.Sort sort) {
            return List.copyOf(saved);
        }

        @Override
        public org.springframework.data.domain.Page<Notification> findAll(Pageable pageable) {
            return org.springframework.data.domain.Page.empty();
        }
    }
}
