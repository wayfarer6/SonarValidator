package org.sonar.sonarvalidator_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

/**
 * 로그인/인증 흐름 테스트입니다.
 *
 * <h2>무엇을 확인하는가</h2>
 * <ol>
 *   <li>인증 없이는 보호된 API 가 <b>401</b> 을 돌려주는지</li>
 *   <li>로그인에 성공하면 세션이 생기고 이후 요청이 통과하는지</li>
 *   <li>잘못된 비밀번호가 401 이고, 계정 존재 여부가 드러나지 않는지</li>
 *   <li>Agent WebSocket 경로가 인증 없이 접근 가능한지
 *       (막으면 랩 장치들이 전부 연결에 실패합니다)</li>
 * </ol>
 *
 * <h2>⚠️ RANDOM_PORT 로 띄우는 이유</h2>
 * <p>{@code WebSocketConfig} 의 {@code ServletServerContainerFactoryBean} 은
 * <b>실제 톰캣</b>이 필요합니다. {@code @SpringBootTest} 기본값(MOCK)에서는
 * {@code Attribute 'jakarta.websocket.server.ServerContainer' not found in
 * ServletContext} 로 컨텍스트 기동이 실패합니다.
 *
 * <h2>DB 는 H2 인메모리</h2>
 * <p>{@code test} 프로필이 H2 인메모리 + {@code create-drop} 을 씁니다. 덕분에
 * 외부 DB 서버 없이 인증/권한/잠금 로직을 실제 SQL 로 검증할 수 있습니다.
 * (매번 깨끗한 스키마에서 시작하므로 테스트 간 간섭이 없습니다)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.sonar.sonarvalidator_backend.Service.UserService userService;

    @Test
    @DisplayName("인증 없이 호출하면 401 을 돌려준다")
    void protectedApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Agent WebSocket 경로는 인증 없이 접근 가능하다")
    void agentWebSocketPathsArePublic() throws Exception {
        // WebSocket 핸드셰이크는 세션이 없습니다. 여기서 401 이 나오면
        // C++ Prober 가 전부 연결에 실패합니다.
        // (핸드셰이크가 아닌 일반 GET 이라 400/404 는 정상이며, 401 이 아니면 됩니다)
        final int management = mockMvc.perform(get("/api/v1/management"))
                .andReturn().getResponse().getStatus();
        final int telemetry = mockMvc.perform(get("/api/v1/telemetry"))
                .andReturn().getResponse().getStatus();

        assertThat(management).isNotEqualTo(401);
        assertThat(telemetry).isNotEqualTo(401);
    }

    @Test
    @DisplayName("로그인에 성공하면 세션으로 보호된 API 를 쓸 수 있다")
    void loginGrantsAccess() throws Exception {
        final String username = "test-login-user";
        final String password = "test-password-1234";
        createUserIfAbsent(username, password);

        final var session = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value(username))
                .andReturn()
                .getRequest()
                .getSession(false);

        assertThat(session).as("로그인 후 세션이 생겨야 합니다").isNotNull();

        // 같은 세션으로 보호된 API 호출
        mockMvc.perform(get("/api/v1/auth/me").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    @DisplayName("잘못된 비밀번호는 401 이고 계정 존재 여부를 드러내지 않는다")
    void wrongPasswordIsRejected() throws Exception {
        final String username = "test-wrong-pw-user";
        createUserIfAbsent(username, "correct-password-1234");

        // 존재하는 계정 + 틀린 비밀번호
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("username", username, "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));

        // 존재하지 않는 계정 — 같은 문구여야 합니다.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("username", "no-such-user-xyz", "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("빈 아이디/비밀번호는 400 을 돌려준다")
    void blankCredentialsAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("기본 관리자 계정이 만들어진다")
    void defaultAdminIsCreated() {
        // AdminAccountInitializer 가 기동 시 만든 계정입니다.
        final User admin = userService.findByUsername("admin");
        assertThat(admin).isNotNull();
        assertThat(admin.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        // 평문이 아니라 BCrypt 해시여야 합니다.
        assertThat(admin.getPasswordHash()).startsWith("$2");
    }

    @Test
    @DisplayName("비밀번호는 BCrypt 로 저장되고 검증된다")
    void passwordIsHashed() {
        final String username = "test-hash-user";
        final String password = "hash-check-password";
        createUserIfAbsent(username, password);

        final User user = userService.findByUsername(username);
        assertThat(user).isNotNull();
        // 평문이 그대로 저장되면 안 됩니다.
        assertThat(user.getPasswordHash()).isNotEqualTo(password);
        assertThat(user.getPasswordHash()).startsWith("$2");

        // matches 로 확인
        assertThat(userService.matches(username, password)).isTrue();
        assertThat(userService.matches(username, "wrong")).isFalse();
        // 존재하지 않는 사용자는 항상 false
        assertThat(userService.matches("no-such-user", "any")).isFalse();
    }

    @Test
    @DisplayName("연속 실패해도 계정은 잠기지 않는다")
    void repeatedFailuresDoNotLockAccount() {
        final String username = "test-lock-user";
        createUserIfAbsent(username, "lock-test-password");

        // 계정 잠금 정책은 제거되었습니다. (SONAR-19)
        // 실패 횟수를 세지 않으므로 몇 번을 실패해도 잠기지 않습니다.
        for (int i = 0; i < 5; i++) {
            userService.recordLoginFailure(username);
        }

        final User user = userService.findByUsername(username);
        assertThat(user).isNotNull();
        assertThat(user.isLocked()).as("잠금 정책이 제거되어 잠기지 않습니다").isFalse();

        // 성공하면 마지막 로그인 시각만 갱신됩니다.
        final Date before = user.getLastLoginAt();
        user.recordSuccess();
        assertThat(user.getLastLoginAt()).isNotNull();
        if (before != null) {
            assertThat(user.getLastLoginAt()).isAfterOrEqualTo(before);
        }
    }

    @Test
    @DisplayName("비밀번호 변경 시 현재 비밀번호를 확인한다")
    void passwordChangeRequiresCurrentPassword() throws Exception {
        final String username = "test-pwchange-user";
        final String password = "original-password-1";
        createUserIfAbsent(username, password);

        final var session = loginAndGetSession(username, password);

        // 틀린 현재 비밀번호 → 401
        mockMvc.perform(post("/api/v1/users/me/password")
                        .session((org.springframework.mock.web.MockHttpSession) session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "currentPassword", "wrong-current",
                                "newPassword", "brand-new-password-2"))))
                .andExpect(status().isUnauthorized());

        // 맞는 현재 비밀번호 → 200
        mockMvc.perform(post("/api/v1/users/me/password")
                        .session((org.springframework.mock.web.MockHttpSession) session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "currentPassword", password,
                                "newPassword", "brand-new-password-2"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("너무 짧은 새 비밀번호는 거부된다")
    void shortPasswordIsRejected() throws Exception {
        final String username = "test-shortpw-user";
        final String password = "original-password-3";
        createUserIfAbsent(username, password);

        final var session = loginAndGetSession(username, password);

        mockMvc.perform(post("/api/v1/users/me/password")
                        .session((org.springframework.mock.web.MockHttpSession) session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "currentPassword", password,
                                "newPassword", "short"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("일반 사용자는 사용자 목록을 조회할 수 없다")
    void nonAdminCannotListUsers() throws Exception {
        final String username = "test-nonadmin-user";
        final String password = "nonadmin-password-1";
        createUserIfAbsent(username, password);

        final var session = loginAndGetSession(username, password);

        mockMvc.perform(get("/api/v1/users")
                        .session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자는 사용자 목록을 조회할 수 있다")
    void adminCanListUsers() throws Exception {
        final var session = loginAndGetSession("admin", "admin");

        mockMvc.perform(get("/api/v1/users")
                        .session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.users").isArray());
    }

    @Test
    @DisplayName("아이디는 대소문자를 구분하지 않는다")
    void usernameIsCaseInsensitive() throws Exception {
        final String username = "test-case-user";
        final String password = "case-password-1234";
        createUserIfAbsent(username, password);

        // 대문자로 로그인 시도
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("username", "TEST-CASE-USER", "password", password))))
                .andExpect(status().isOk())
                // 서버는 정규화된(소문자) 아이디를 돌려줍니다.
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    @DisplayName("중복 아이디 생성은 거부된다")
    void duplicateUsernameIsRejected() {
        final String username = "test-dup-user";
        createUserIfAbsent(username, "dup-password-1234");

        assertThatThrownBy(() ->
                userService.create(username, "another-password", null, User.Role.VIEWER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("잘못된 입력으로 사용자 생성 시 예외를 던진다")
    void invalidUserCreationThrows() {
        assertThatThrownBy(() -> userService.create("", "password-1234", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userService.create("test-blank-pw", "", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                userService.create("test-short-pw-only", null, null, User.Role.VIEWER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 사용자가 없을 때만 만듭니다. (테스트 간 간섭 방지) */
    private void createUserIfAbsent(String username, String password) {
        if (userService.findByUsername(username) == null) {
            userService.create(username, password, null, User.Role.OPERATOR);
        }
    }

    /** 로그인해서 세션을 돌려줍니다. */
    private jakarta.servlet.http.HttpSession loginAndGetSession(String username, String password)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }
}
