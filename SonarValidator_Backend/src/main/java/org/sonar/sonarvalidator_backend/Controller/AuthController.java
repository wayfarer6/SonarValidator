package org.sonar.sonarvalidator_backend.Controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.sonar.sonarvalidator_backend.Model.entity.User;
import org.sonar.sonarvalidator_backend.Service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 로그인/로그아웃/현재 사용자 조회 API 입니다.
 *
 * <h2>왜 직접 인증을 호출하는가</h2>
 * <p>{@code formLogin()} 을 쓰면 스프링이 {@code application/x-www-form-urlencoded}
 * 와 302 리다이렉트를 강제합니다. 프론트엔드는 <b>JSON</b> 을 보내고 JSON
 * 응답을 받아야 하므로, {@link AuthenticationManager} 를 직접 호출해
 * 성공/실패를 모두 JSON 으로 돌려줍니다.
 *
 * <h2>세션에 SecurityContext 를 저장하는 이유</h2>
 * <p>인증에 성공하면 {@link SecurityContext} 를 <b>명시적으로 세션에 넣어야</b>
 * 다음 요청에서 인증된 사용자로 인식됩니다. 이 단계를 빠뜨리면 로그인은
 * 성공(200)하는데 이후 API 가 전부 401 이 되는 혼란스러운 증상이 생깁니다.
 *
 * <h2>실패 사유를 구분해 주는 이유</h2>
 * <ul>
 *   <li>{@link BadCredentialsException} -> 401 "아이디 또는 비밀번호가 올바르지 않습니다"</li>
 *   <li>{@link DisabledException} -> 403 "비활성 계정입니다"</li>
 * </ul>
 * <p>아이디 존재 여부는 드러내지 않습니다. (아이디/비밀번호 오류를 한 문구로 통일)
 *
 * <p><b>계정 잠금(423)은 제거되었습니다.</b> 서버가 잠금 상태를 두지 않으므로
 * {@code LockedException} 분기도 없습니다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    /**
     * @param authenticationManager 인증 관리자
     * @param userService           사용자 서비스
     */
    public AuthController(AuthenticationManager authenticationManager, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
    }

    /**
     * 로그인 요청 본문입니다.
     *
     * @param username 아이디 (화면에서는 Email 로 표시)
     * @param password 평문 비밀번호
     */
    public record LoginRequest(String username, String password) {
    }

    /**
     * 로그인합니다.
     *
     * @param body    로그인 요청
     * @param request HTTP 요청 (세션 생성을 위함)
     * @return 성공 시 사용자 정보, 실패 시 오류 메시지
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) LoginRequest body,
                                                     HttpServletRequest request) {
        final String username = body == null ? null : body.username();
        final String password = body == null ? null : body.password();

        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "아이디와 비밀번호를 모두 입력하세요."));
        }

        try {
            final Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username.trim(), password));

            // 인증 성공: SecurityContext 를 세션에 저장해야 다음 요청에서 유지됩니다.
            final SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            // 세션 고정 공격 방지를 위해 기존 세션을 버리고 새로 만듭니다.
            final HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            final HttpSession session = request.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            userService.recordLoginSuccess(userService.findByUsername(username));
            log.info("login success: username={} session={}", username, session.getId());

            final Map<String, Object> body2 = new LinkedHashMap<>();
            body2.put("authenticated", true);
            body2.put("username", authentication.getName());
            final User user = userService.findByUsername(username);
            if (user != null) {
                body2.put("display_name", user.getDisplayName());
                body2.put("role", user.getRole() == null ? null : user.getRole().name());
            }
            return ResponseEntity.ok(body2);

        } catch (DisabledException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "비활성화된 계정입니다. 관리자에게 문의하세요."));

        } catch (BadCredentialsException ex) {
            userService.recordLoginFailure(username);
            // 아이디 존재 여부를 드러내지 않도록 문구를 통일합니다.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "아이디 또는 비밀번호가 올바르지 않습니다."));
        }
    }

    /**
     * 현재 로그인한 사용자를 반환합니다.
     *
     * <p>프론트엔드가 새로고침 후 로그인 상태를 복원할 때 씁니다.
     * 세션 쿠키가 유효하면 사용자 정보를, 아니면 401 을 돌려줍니다.
     *
     * @param authentication 현재 인증 (없으면 null)
     * @return 사용자 정보 또는 401
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authenticated", false));
        }
        final User user = userService.findByUsername(authentication.getName());
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", true);
        body.put("username", authentication.getName());
        if (user != null) {
            body.put("display_name", user.getDisplayName());
            body.put("role", user.getRole() == null ? null : user.getRole().name());
            body.put("last_login_at",
                    org.sonar.sonarvalidator_backend.Util.Timestamps.iso(user.getLastLoginAt()));
        }
        return ResponseEntity.ok(body);
    }

    /**
     * 로그아웃합니다.
     *
     * <p>{@code SecurityConfig} 의 {@code logout()} 설정이 실제 무효화를
     * 담당하며, 이 메서드는 스프링 시큐리티 필터가 처리하지 못한 경우를 위한
     * 안전망입니다.
     *
     * @param request HTTP 요청
     * @return 결과 메시지
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "로그아웃되었습니다."));
    }
}
