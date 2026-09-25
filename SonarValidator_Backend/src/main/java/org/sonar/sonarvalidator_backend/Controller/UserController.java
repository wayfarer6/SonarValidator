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

/**
 * 사용자 관리 API 입니다.
 *
 * <h2>왜 인증 컨트롤러와 분리했는가</h2>
 * <p>{@code AuthController} 는 "로그인 자체" 를 다루고, 이 컨트롤러는
 * <b>로그인한 뒤의 계정 관리</b>를 다룹니다. 관심사가 달라 파일을 나눴습니다.
 *
 * <h2>비밀번호 변경 시 현재 비밀번호를 요구하는 이유</h2>
 * <p>세션만 있으면 비밀번호를 바꿀 수 있게 하면, 세션 탈취 시 곧바로 계정
 * 전체를 빼앗깁니다. 현재 비밀번호를 한 번 더 확인하면 그 위험이 줄어듭니다.
 *
 * <h2>권한 확인</h2>
 * <p>사용자 목록 조회와 계정 생성은 {@code ADMIN} 만 가능합니다. 권한 확인을
 * 애노테이션이 아니라 <b>코드로</b> 하는 이유는, 지금 규모에서 설정이 한눈에
 * 보이는 편이 실수를 줄이기 때문입니다.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    /**
     * @param userService 사용자 서비스
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 비밀번호 변경 요청 본문입니다.
     *
     * @param currentPassword 현재 비밀번호
     * @param newPassword     새 비밀번호
     */
    public record PasswordChangeRequest(String currentPassword, String newPassword) {
    }

    /**
     * 계정 생성 요청 본문입니다.
     *
     * @param username    아이디
     * @param password    평문 비밀번호
     * @param displayName 표시 이름
     * @param role        권한 (ADMIN / OPERATOR / VIEWER)
     */
    public record CreateUserRequest(String username,
                                    String password,
                                    String displayName,
                                    String role) {
    }

    /**
     * 현재 로그인한 사용자의 정보를 반환합니다.
     *
     * @param authentication 현재 인증
     * @return 사용자 정보
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        final User user = userService.findByUsername(authentication.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "사용자를 찾을 수 없습니다."));
        }
        return ResponseEntity.ok(UserService.toResponse(user));
    }

    /**
     * 본인 비밀번호를 변경합니다.
     *
     * @param authentication 현재 인증
     * @param body           변경 요청
     * @return 결과 메시지
     */
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
        log.info("password changed via API: username={}", username);
        return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다."));
    }

    /**
     * 전체 사용자 목록을 반환합니다. (ADMIN 전용)
     *
     * @param authentication 현재 인증
     * @return {@code {"total": n, "users": [...]}}
     */
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

    /**
     * 사용자를 생성합니다. (ADMIN 전용)
     *
     * @param authentication 현재 인증
     * @param body           생성 요청
     * @return 생성된 사용자
     */
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

    /**
     * 계정 활성 여부를 바꿉니다. (ADMIN 전용)
     *
     * @param authentication 현재 인증
     * @param username       대상 아이디
     * @param enabled        사용 여부
     * @return 결과
     */
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

    /**
     * 본인 계정을 삭제합니다.
     *
     * <h2>왜 비밀번호를 다시 요구하는가</h2>
     * <p>삭제는 되돌릴 수 없습니다. 세션만 탈취한 공격자가 계정을 지워버리는
     * 것을 막으려면, 비밀번호 확인이 필요합니다. (비밀번호 변경과 같은 이유)
     *
     * <h2>마지막 관리자는 지울 수 없습니다</h2>
     * <p>관리자가 하나뿐인데 지우면 아무도 시스템에 들어갈 수 없습니다.
     * 그래서 마지막 ADMIN 은 삭제를 거부합니다.
     *
     * @param authentication 현재 인증
     * @param body           비밀번호 확인 본문
     * @param request        HTTP 요청 (세션 무효화용)
     * @return 결과 메시지
     */
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

    /**
     * 현재 사용자가 ADMIN 인지 확인합니다.
     *
     * @param authentication 현재 인증
     * @return 관리자면 {@code true}
     */
    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    /**
     * 역할 문자열을 enum 으로 바꿉니다.
     *
     * @param role 역할 문자열 (null 허용)
     * @return 역할 (알 수 없으면 OPERATOR)
     */
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
