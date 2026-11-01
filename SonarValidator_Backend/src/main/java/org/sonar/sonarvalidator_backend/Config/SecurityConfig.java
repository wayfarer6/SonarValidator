package org.sonar.sonarvalidator_backend.Config;

import java.io.IOException;
import java.util.List;

import org.sonar.sonarvalidator_backend.Model.entity.User;
import org.sonar.sonarvalidator_backend.Repository.UserRepository;
import org.sonar.sonarvalidator_backend.Service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            // 로그인/로그아웃/현재 사용자
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/auth/me",
            // Agent 가 접속하는 WebSocket (세션 없음 — 위 주석 참고)
            "/api/v1/management",
            "/api/v1/telemetry",
            // Agent terminal channel. It also requires the configured shared secret.
            "/api/v1/terminal/agent",
            // Browser terminal authenticates and authorizes in its handshake interceptor.
            "/api/v1/terminal/browser",
            // 헬스 체크
            "/actuator/health",
            // H2 콘솔 (local 프로필 전용)
            "/h2-console/**",
            // 프론트엔드 정적 자원 (같은 서버에서 서빙하는 배포 형태 대비)
            "/",
            "/index.html",
            "/assets/**",
            "/images/**",
            "/favicon.ico",
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository repository) {
        return username -> {
            final String normalized = username == null
                    ? ""
                    : username.trim().toLowerCase(java.util.Locale.ROOT);
            final User user = repository.findByUsername(normalized)
                    .orElseThrow(() -> new UsernameNotFoundException("user not found"));

            // 아직은 비활성 계정만 스프링 시큐리티에 알려 줍니다.
            // (계정 잠금 정책은 제거되었습니다 - disabled 만 남습니다)
            final List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + (user.getRole() == null
                            ? User.Role.VIEWER.name()
                            : user.getRole().name())));

            // 엔티티의 User 와 이름이 같아 스프링 시큐리티의 User 는
            // 정규화 이름으로 씁니다. (import 하면 서로 가립니다)
            return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                    .password(user.getPasswordHash())
                    .authorities(authorities)
                    .disabled(!user.isEnabled())
                    .build();
        };
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        final DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   UserService userService) throws Exception {
        http
                // CORS 는 WebMvcConfig 의 addCorsMappings 설정을 그대로 씁니다.
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                // H2 콘솔은 iframe 을 쓰므로 같은 출처에 한해 허용합니다.
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        // CORS 사전 요청은 인증 없이 통과해야 합니다.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/agents/download/**", "/api/v1/agents/bundle/**")
                            .hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers("/api/v1/users/me/**", "/api/v1/notifications/**").authenticated()
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/projects/*/validation", "/api/v1/cli/ingest").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .sessionManagement(session -> session
                        // 세션 고정 공격 방지: 로그인 시 세션 ID 를 바꿉니다.
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(5))
                .exceptionHandling(ex -> ex
                        // 인증되지 않은 API 호출은 302 리다이렉트 대신 401 JSON.
                        // (프론트가 401 을 보고 로그인 화면으로 보낼 수 있게)
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.pathPattern("/api/**"))
                        .accessDeniedHandler((request, response, denied) ->
                                writeJson(response, HttpStatus.FORBIDDEN, "권한이 없습니다.")))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, auth) -> {
                            writeJson(response, HttpStatus.OK, "로그아웃되었습니다.");
                        })
                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true));

        return http.build();
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(java.util.Map.of("message", message)));
    }
}
