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

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
    }

    public record LoginRequest(String username, String password) {
    }

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
            log.info("로그인 완료: 사용자={}", username);

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
