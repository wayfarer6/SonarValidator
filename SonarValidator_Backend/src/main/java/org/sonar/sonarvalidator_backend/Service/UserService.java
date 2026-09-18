package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.sonar.sonarvalidator_backend.Model.entity.AppUser;
import org.sonar.sonarvalidator_backend.Repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 조회/생성과 로그인 실패 기록을 담당합니다.
 *
 * <h2>인증 로직과 분리한 이유</h2>
 * <p>실제 인증 판정은 {@code SecurityConfig} 의
 * {@code DaoAuthenticationProvider}(+ {@code UserDetailsService}) 가 합니다.
 * 이 서비스는 <b>데이터와 잠금 상태</b> 만 다룹니다. 두 관심사를 나눠 두면
 * 잠금 정책을 바꿔도 인증 흐름이 흔들리지 않습니다.
 *
 * <h2>기본 관리자 계정</h2>
 * <p>사용자가 하나도 없으면 기동 시 기본 관리자를 만듭니다. 이렇게 하지 않으면
 * 첫 로그인이 불가능해 아무도 들어갈 수 없습니다.
 * 기본 비밀번호는 <b>환경변수로 반드시 바꾸도록 경고</b>를 남깁니다.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** 잠금을 시작할 실패 횟수. */
    private static final int MAX_ATTEMPTS_BEFORE_LOCK = 5;

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    /**
     * @param repository      사용자 저장소
     * @param passwordEncoder BCrypt 인코더 (SecurityConfig 에서 빈으로 등록)
     */
    public UserService(AppUserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 아이디로 사용자를 찾습니다.
     *
     * @param username 로그인 아이디
     * @return 사용자 (없으면 {@code null})
     */
    @Transactional(readOnly = true)
    public AppUser findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return repository.findByUsername(normalize(username)).orElse(null);
    }

    /**
     * 전체 사용자를 조회합니다. (비밀번호 해시는 응답에 넣지 않습니다)
     *
     * @return 사용자 목록
     */
    @Transactional(readOnly = true)
    public List<AppUser> listAll() {
        return repository.findAllByOrderByIdAsc();
    }

    /**
     * 사용자를 만듭니다.
     *
     * @param username    로그인 아이디
     * @param rawPassword 평문 비밀번호 (여기서 해시합니다)
     * @param displayName 표시 이름
     * @param role        권한
     * @return 저장된 사용자
     * @throws IllegalArgumentException 아이디가 비었거나 이미 존재하는 경우
     */
    @Transactional
    public AppUser create(String username, String rawPassword, String displayName, AppUser.Role role) {
        final String normalized = normalize(username);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        if (repository.existsByUsername(normalized)) {
            throw new IllegalArgumentException("username already exists: " + normalized);
        }
        final AppUser user = AppUser.of(
                normalized,
                passwordEncoder.encode(rawPassword),
                displayName == null || displayName.isBlank() ? normalized : displayName,
                role);
        final AppUser saved = repository.save(user);
        log.info("user created: username={} role={}", saved.getUsername(), saved.getRole());
        return saved;
    }

    /**
     * 비밀번호가 일치하는지 확인합니다.
     *
     * <p>비밀번호 <b>변경</b> 시 현재 비밀번호를 한 번 더 확인하는 용도입니다.
     * 세션만 있으면 바꿀 수 있게 두면 세션 탈취 시 계정 전체를 빼앗깁니다.
     *
     * @param username      대상 아이디
     * @param rawPassword   확인할 평문 비밀번호
     * @return 일치하면 {@code true}
     */
    @Transactional(readOnly = true)
    public boolean matches(String username, String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return false;
        }
        final AppUser user = findByUsername(username);
        if (user == null || user.getPasswordHash() == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    /**
     * 비밀번호를 바꿉니다. (본인 변경 / 관리자 초기화 공용)
     *
     * @param username    대상 아이디
     * @param rawPassword 새 평문 비밀번호
     * @return 성공 여부
     */
    @Transactional
    public boolean changePassword(String username, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return false;
        }
        final AppUser user = findByUsername(username);
        if (user == null) {
            return false;
        }
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        // 비밀번호를 바꾸면 잠금과 실패 카운터를 함께 푸는 것이 자연스럽습니다.
        user.recordSuccess();
        repository.save(user);
        log.info("password changed for username={}", user.getUsername());
        return true;
    }

    /**
     * 계정 활성 여부를 바꿉니다.
     *
     * @param username 대상 아이디
     * @param enabled  사용 여부
     * @return 성공 여부
     */
    @Transactional
    public boolean setEnabled(String username, boolean enabled) {
        final AppUser user = findByUsername(username);
        if (user == null) {
            return false;
        }
        user.setEnabled(enabled);
        repository.save(user);
        log.info("user {} enabled={}", user.getUsername(), enabled);
        return true;
    }

    /**
     * 계정을 삭제합니다.
     *
     * @param username 대상 아이디
     * @return 삭제했으면 {@code true}
     */
    @Transactional
    public boolean delete(String username) {
        final AppUser user = findByUsername(username);
        if (user == null) {
            return false;
        }
        repository.delete(user);
        log.info("user deleted: username={}", user.getUsername());
        return true;
    }

    /**
     * 관리자 계정 수를 셉니다.
     *
     * <p>"마지막 관리자는 삭제할 수 없다" 규칙에 씁니다. 관리자가 하나뿐인데
     * 지워지면 아무도 시스템에 들어갈 수 없습니다.
     *
     * @return ADMIN 역할이면서 활성인 계정 수
     */
    @Transactional(readOnly = true)
    public long countAdmins() {
        return repository.findAll().stream()
                .filter(user -> user.getRole() == AppUser.Role.ADMIN && user.isEnabled())
                .count();
    }

    /**
     * 로그인 성공을 기록합니다.
     *
     * @param user 사용자
     */
    @Transactional
    public void recordLoginSuccess(AppUser user) {
        if (user == null) {
            return;
        }
        user.recordSuccess();
        repository.save(user);
    }

    /**
     * 로그인 실패를 기록하고 필요하면 계정을 잠급니다.
     *
     * <p>존재하지 않는 아이디로도 호출될 수 있으므로 사용자가 없으면 조용히
     * 넘어갑니다. (아이디 존재 여부를 응답으로 알려주지 않기 위함)
     *
     * @param username 시도된 아이디
     */
    @Transactional
    public void recordLoginFailure(String username) {
        final AppUser user = findByUsername(username);
        if (user == null) {
            return;
        }
        user.recordFailure(MAX_ATTEMPTS_BEFORE_LOCK);
        repository.save(user);
        if (user.isLocked()) {
            log.warn("user {} locked until {} after {} failed attempts",
                    user.getUsername(), user.getLockedUntil(), user.getFailedAttempts());
        } else {
            log.info("login failed for {} (attempt {})", user.getUsername(), user.getFailedAttempts());
        }
    }

    /**
     * 사용자가 하나도 없으면 기본 관리자를 만듭니다.
     *
     * @param defaultPassword 기본 비밀번호 (운영에서는 환경변수로 주입)
     * @return 생성했으면 {@code true}
     */
    @Transactional
    public boolean ensureDefaultAdmin(String defaultPassword) {
        if (repository.count() > 0) {
            return false;
        }
        create("admin", defaultPassword, "Default Administrator", AppUser.Role.ADMIN);
        log.warn("=================================================================");
        log.warn(" Default admin account created (username=admin).");
        log.warn(" CHANGE THIS PASSWORD IMMEDIATELY in any shared environment.");
        log.warn(" Override with SONAR_ADMIN_PASSWORD environment variable.");
        log.warn("=================================================================");
        return true;
    }

    /**
     * 마지막 로그인 시각을 포함한 요약을 만듭니다. (응답용, 비밀번호 제외)
     *
     * @param user 사용자
     * @return 요약 맵
     */
    public static java.util.Map<String, Object> toResponse(AppUser user) {
        final java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("username", user.getUsername());
        body.put("display_name", user.getDisplayName());
        body.put("role", user.getRole() == null ? null : user.getRole().name());
        body.put("enabled", user.isEnabled());
        body.put("locked", user.isLocked());
        body.put("locked_until", user.getLockedUntil() == null ? null : user.getLockedUntil().toString());
        body.put("last_login_at", user.getLastLoginAt() == null ? null : user.getLastLoginAt().toString());
        body.put("created_at", user.getCreatedAt());
        return body;
    }

    /**
     * 아이디를 정규화합니다.
     *
     * <p>대소문자와 앞뒤 공백을 없앱니다. {@code Admin} 과 {@code admin} 이
     * 다른 계정이 되면 운영자가 혼동합니다.
     *
     * @param username 원본
     * @return 정규화된 아이디
     */
    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 기본 관리자 생성 시각을 남깁니다. (진단용)
     *
     * @return 현재 시각 문자열
     */
    public static String nowIso() {
        return Instant.now().toString();
    }
}
