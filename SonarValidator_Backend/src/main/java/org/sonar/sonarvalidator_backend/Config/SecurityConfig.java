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

/**
 * Spring Security 설정입니다. (아이디/비밀번호 기반 로그인)
 *
 * <h2>인증 방식 선택: 세션 vs JWT</h2>
 * <p><b>세션 기반</b>을 씁니다. 이유는 세 가지입니다.
 * <ol>
 *   <li>기존 프론트엔드가 쿠키 기반 흐름을 이미 전제로 하고 있습니다.</li>
 *   <li>로그아웃이 <b>서버에서 즉시</b> 유효합니다. JWT 는 만료까지 살아 있고,
 *       무효화하려면 별도 저장소가 필요합니다.</li>
 *   <li>토큰 저장 위치(로컬스토리지 vs 쿠키) 문제를 고민하지 않아도 됩니다.</li>
 * </ol>
 * <p>무상태 확장이 필요해지면 그때 JWT 로 옮기면 됩니다. 지금 규모에서 JWT 는
 * 얻는 것보다 관리할 것이 많습니다. (갱신/폐기/탈취 대응)
 *
 * <h2>⚠️ 인증 예외 경로가 중요한 이유</h2>
 * <p>Agent(C++ Prober)는 <b>세션도 쿠키도 없습니다.</b> WebSocket 핸드셰이크
 * 요청에 인증을 요구하면 랩의 장치 17대가 전부 연결에 실패합니다. 그래서
 * {@code /api/v1/management}, {@code /api/v1/telemetry} 를 예외로 둡니다.
 *
 * <p>이 경로는 현재 <b>네트워크 신뢰</b>에 의존합니다. 운영에서는 관리망
 * 접근 제어(방화벽/터널)나 Agent 별 공유 시크릿으로 보강해야 합니다.
 *
 * <h2>H2 콘솔</h2>
 * <p>{@code /h2-console} 은 {@code local} 프로필의 개발 편의용입니다.
 * 프레임을 쓰므로 {@code frameOptions} 를 허용해야 화면이 뜹니다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 인증 없이 접근할 수 있는 경로. */
    private static final String[] PUBLIC_PATHS = {
            // 로그인/로그아웃/현재 사용자
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/auth/me",
            // Agent 가 접속하는 WebSocket (세션 없음 — 위 주석 참고)
            "/api/v1/management",
            "/api/v1/telemetry",
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

    /**
     * 비밀번호 인코더입니다. (BCrypt)
     *
     * <p>BCrypt 를 쓰는 이유: 의도적으로 느리고(무차별 대입 비용 증가),
     * salt 를 해시에 포함하므로 같은 비밀번호도 다른 값이 됩니다.
     * MD5/SHA-256 같은 범용 해시는 GPU 로 초당 수십억 회 시도가 가능해
     * 비밀번호 저장에 부적합합니다.
     *
     * @return BCrypt 인코더 (기본 strength 10)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 사용자 조회를 담당합니다.
     *
     * <p>{@link UserService} 를 거치지 않고 저장소를 직접 쓰는 이유는
     * 인증 시점에 트랜잭션이 끼어들지 않게 하기 위함입니다.
     *
     * @param repository 사용자 저장소
     * @return UserDetailsService
     */
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

    /**
     * DAO 인증 제공자입니다.
     *
     * <p>{@code hideUserNotFoundExceptions} 를 켜 두면 "아이디 없음" 과
     * "비밀번호 틀림" 이 같은 예외로 나옵니다. 계정 존재 여부를 외부에
     * 알려주지 않기 위한 기본 동작입니다.
     *
     * @param userDetailsService 사용자 조회
     * @param passwordEncoder    비밀번호 인코더
     * @return 인증 제공자
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        final DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    /**
     * 인증 관리자입니다. (컨트롤러에서 직접 인증할 때 사용)
     *
     * @param configuration 인증 설정
     * @return 인증 관리자
     * @throws Exception 설정을 읽지 못한 경우
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * HTTP 보안 규칙을 정의합니다.
     *
     * <h2>⚠️ CorsConfigurationSource 를 주입받지 않는 이유</h2>
     * <p>{@code WebMvcConfig} 가 이미 {@code addCorsMappings} 로 CORS 를
     * 설정하고 있고, 스프링은 그것을 {@code CorsConfigurationSource} 로도
     * 노출합니다({@code mvcHandlerMappingIntrospector}). 여기에 같은 타입
     * 빈을 하나 더 만들면 <b>주입 대상이 둘이 되어 기동이 실패</b>합니다.
     *
     * <pre>
     *   No qualifying bean of type 'CorsConfigurationSource'
     *   available: expected single matching bean but found 2:
     *   corsConfigurationSource, mvcHandlerMappingIntrospector
     * </pre>
     *
     * <p>그래서 {@code corsConfigurationSource} 빈을 없애고, 스프링이 MVC
     * 설정에서 만들어 준 것을 {@code cors(Customizer.withDefaults())} 로
     * 사용합니다. 즉 CORS 정의는 {@code WebMvcConfig} 한 곳에만 둡니다.
     *
     * @param http        보안 빌더
     * @param userService 로그인 성공/실패 기록용
     * @return 필터 체인
     * @throws Exception 설정 오류
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   UserService userService) throws Exception {
        http
                // CORS 는 WebMvcConfig 의 addCorsMappings 설정을 그대로 씁니다.
                .cors(org.springframework.security.config.Customizer.withDefaults())
                // CSRF: 세션 쿠키를 쓰면 필요하지만, REST + JSON API 이고
                // 프론트가 별도 CSRF 토큰 흐름을 아직 갖추지 않았습니다.
                // SameSite=Lax 쿠키(스프링 기본)로 크로스 사이트 폼 전송은
                // 막히므로, 지금 단계에서는 끄고 아래 쿠키 설정으로 보완합니다.
                // 운영에서 CSRF 토큰을 붙일 때 이 줄을 제거하세요.
                .csrf(csrf -> csrf.disable())
                // H2 콘솔은 iframe 을 쓰므로 같은 출처에 한해 허용합니다.
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        // CORS 사전 요청은 인증 없이 통과해야 합니다.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // 나머지 API 는 인증 필요
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

    /**
     * 오류 응답을 JSON 으로 씁니다.
     *
     * <p>스프링 기본 동작은 HTML 오류 페이지를 반환합니다. 프론트가
     * {@code response.json()} 을 호출하면 파싱 오류가 나므로 맞춰 줍니다.
     *
     * @param response 응답
     * @param status   상태 코드
     * @param message  메시지
     * @throws IOException 쓰기 실패
     */
    private void writeJson(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(java.util.Map.of("message", message)));
    }
}
