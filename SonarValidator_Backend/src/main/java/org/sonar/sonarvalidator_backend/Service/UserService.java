package org.sonar.sonarvalidator_backend.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.sonar.sonarvalidator_backend.Model.entity.User;
import org.sonar.sonarvalidator_backend.Repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return repository.findByUsername(normalize(username)).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<User> listAll() {
        return repository.findAllByOrderByIdAsc();
    }

    @Transactional
    public User create(String username, String rawPassword, String displayName, User.Role role) {
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
        final User user = User.of(
                normalized,
                passwordEncoder.encode(rawPassword),
                displayName == null || displayName.isBlank() ? normalized : displayName,
                role);
        final User saved = repository.save(user);
        log.info("사용자 생성: 사용자={} 역할={}", saved.getUsername(), saved.getRole());
        return saved;
    }

    @Transactional(readOnly = true)
    public boolean matches(String username, String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return false;
        }
        final User user = findByUsername(username);
        if (user == null || user.getPasswordHash() == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    @Transactional
    public boolean changePassword(String username, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return false;
        }
        final User user = findByUsername(username);
        if (user == null) {
            return false;
        }
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        repository.save(user);
        log.info("비밀번호 변경: 사용자={}", user.getUsername());
        return true;
    }

    @Transactional
    public boolean setEnabled(String username, boolean enabled) {
        final User user = findByUsername(username);
        if (user == null) {
            return false;
        }
        user.setEnabled(enabled);
        repository.save(user);
        log.info("계정 상태 변경: 사용자={} 사용={}", user.getUsername(), enabled);
        return true;
    }

    @Transactional
    public boolean delete(String username) {
        final User user = findByUsername(username);
        if (user == null) {
            return false;
        }
        repository.delete(user);
        log.info("사용자 삭제: 사용자={}", user.getUsername());
        return true;
    }

    @Transactional(readOnly = true)
    public long countAdmins() {
        return repository.findAll().stream()
                .filter(user -> user.getRole() == User.Role.ADMIN && user.isEnabled())
                .count();
    }

    @Transactional
    public void recordLoginSuccess(User user) {
        if (user == null) {
            return;
        }
        user.recordSuccess();
        repository.save(user);
    }

    @Transactional
    public void recordLoginFailure(String username) {
        final User user = findByUsername(username);
        log.info("로그인 실패: 사용자={}", user == null ? "(unknown user)" : user.getUsername());
    }

    @Transactional
    public boolean ensureDefaultAdmin(String defaultPassword) {
        if (repository.count() > 0) {
            return false;
        }
        create("admin", defaultPassword, "Default Administrator", User.Role.ADMIN);
        log.warn("기본 관리자 생성: 사용자=admin, 공유 환경에서는 비밀번호를 변경하세요");
        return true;
    }

    public static java.util.Map<String, Object> toResponse(User user) {
        final java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("username", user.getUsername());
        body.put("display_name", user.getDisplayName());
        body.put("role", user.getRole() == null ? null : user.getRole().name());
        body.put("enabled", user.isEnabled());
        // 잠금 표시는 후속 작업에서 화면과 함께 걷어냅니다.
        // 그때까지 응답 키를 남겨 두어 프론트엔드가 깨지지 않게 합니다.
        body.put("locked", false);
        body.put("locked_until", null);
        body.put("last_login_at", org.sonar.sonarvalidator_backend.Util.Timestamps.iso(user.getLastLoginAt()));
        body.put("created_at", org.sonar.sonarvalidator_backend.Util.Timestamps.iso(user.getCreatedAt()));
        return body;
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public static String nowIso() {
        return Instant.now().toString();
    }
}
