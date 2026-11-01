package org.sonar.sonarvalidator_backend.Controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Model.entity.User;
import org.sonar.sonarvalidator_backend.Service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public record PasswordChangeRequest(String currentPassword, String newPassword) {
    }

    public record CreateUserRequest(String username,
                                    String password,
                                    String displayName,
                                    String role) {
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        final User user = userService.findByUsername(authentication.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "사용자를 찾을 수 없습니다."));
        }
        return ResponseEntity.ok(UserService.toResponse(user));
    }

    @PostMapping("/me/password")
    public ResponseEntity<Map<String, Object>> changeMyPassword(
            Authentication authentication,
            @RequestBody(required = false) PasswordChangeRequest body) {
        if (body == null || body.newPassword() == null || body.newPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "새 비밀번호를 입력하세요."));
        }
        // 최소 길이 검증. 너무 짧으면 무차별 대입에 취약합니다.
        if (body.newPassword().length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "새 비밀번호는 8자 이상이어야 합니다."));
        }

        final String username = authentication.getName();
        // 현재 비밀번호를 한 번 더 확인합니다. (세션 탈취 대비)
        if (!userService.matches(username, body.currentPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "현재 비밀번호가 올바르지 않습니다."));
        }

        final boolean changed = userService.changePassword(username, body.newPassword());
        if (!changed) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "비밀번호 변경에 실패했습니다."));
        }
        log.debug("password changed via API: username={}", username);
        return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다."));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "관리자만 사용자 목록을 조회할 수 있습니다."));
        }
        final List<Map<String, Object>> users = new ArrayList<>();
        for (final User user : userService.listAll()) {
            users.add(UserService.toResponse(user));
        }
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", users.size());
        body.put("users", users);
        return ResponseEntity.ok(body);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(Authentication authentication,
                                                      @RequestBody(required = false) CreateUserRequest body) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "관리자만 사용자를 만들 수 있습니다."));
        }
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "요청 본문이 비어 있습니다."));
        }
        if (body.password() == null || body.password().length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "비밀번호는 8자 이상이어야 합니다."));
        }
        try {
            final User created = userService.create(
                    body.username(),
                    body.password(),
                    body.displayName(),
                    parseRole(body.role()));
            return ResponseEntity.ok(UserService.toResponse(created));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PutMapping("/{username}/enabled")
    public ResponseEntity<Map<String, Object>> setEnabled(
            Authentication authentication,
            @PathVariable String username,
            @org.springframework.web.bind.annotation.RequestParam("enabled") boolean enabled) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "관리자만 계정 상태를 바꿀 수 있습니다."));
        }
        // 자기 자신을 비활성하면 아무도 관리할 수 없게 될 수 있습니다.
        if (username.equalsIgnoreCase(authentication.getName()) && !enabled) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "자신의 계정은 비활성화할 수 없습니다."));
        }
        final boolean changed = userService.setEnabled(username, enabled);
        if (!changed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "사용자를 찾을 수 없습니다: " + username));
        }
        return ResponseEntity.ok(Map.of("username", username, "enabled", enabled));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/me")
    public ResponseEntity<Map<String, Object>> deleteMe(
            Authentication authentication,
            @RequestBody(required = false) PasswordChangeRequest body,
            jakarta.servlet.http.HttpServletRequest request) {

        final String username = authentication.getName();

        if (!userService.matches(username, body == null ? null : body.currentPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "비밀번호가 올바르지 않습니다."));
        }

        // 마지막 관리자 보호
        if (isAdmin(authentication) && userService.countAdmins() <= 1) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "마지막 관리자 계정은 삭제할 수 없습니다. "
                            + "다른 관리자를 먼저 만든 뒤 삭제하세요."));
        }

        final boolean deleted = userService.delete(username);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "사용자를 찾을 수 없습니다."));
        }

        // 삭제했으므로 세션도 정리합니다.
        final var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        log.warn("account deleted: username={}", username);
        return ResponseEntity.ok(Map.of("message", "계정이 삭제되었습니다."));
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private User.Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            return User.Role.OPERATOR;
        }
        try {
            return User.Role.valueOf(role.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return User.Role.OPERATOR;
        }
    }
}
