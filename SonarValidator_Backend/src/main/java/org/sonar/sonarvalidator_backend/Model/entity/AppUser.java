package org.sonar.sonarvalidator_backend.Model.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 로그인 사용자입니다.
 *
 * <h2>왜 별도 테이블인가</h2>
 * 프론트엔드의 로그인은 지금까지 쿠키에 이메일만 넣고 통과시키는
 * <b>자리표시자</b> 였습니다. 서버가 인증을 하려면 사용자 정보가 DB 에 있어야
 * 합니다.
 *
 * <h2>비밀번호 저장 원칙</h2>
 * <p>{@link #passwordHash} 에는 <b>BCrypt 해시</b>만 넣습니다. 평문이나
 * 복호화 가능한 값을 저장하면 DB 유출 시 곧바로 계정 탈취로 이어집니다.
 * BCrypt 는 의도적으로 느리고, 같은 비밀번호라도 매번 다른 해시가 나오도록
 * salt 를 포함합니다.
 *
 * <h2>계정 잠금 필드를 두는 이유</h2>
 * <p>{@link #failedAttempts} 와 {@link #lockedUntil} 로 무차별 대입을 늦춥니다.
 * 실제로 방어 효과가 크면서 구현 비용이 낮은 축에 속합니다.
 * (스프링 시큐리티의 {@code DaoAuthenticationProvider} 와 별개로,
 * 우리가 직접 세는 값입니다.)
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    /** 사용자 권한. */
    public enum Role {
        /** 모든 기능을 사용할 수 있습니다. */
        ADMIN,
        /** 조회와 편집은 가능하지만 사용자 관리는 못 합니다. */
        OPERATOR,
        /** 조회만 가능합니다. */
        VIEWER
    }

    /** 기본 키. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 로그인 아이디.
     *
     * <p>화면은 "Email" 로 보이지만 실제로는 아이디입니다. 이메일 형식을
     * 강제하지 않는 이유는 사내 계정(예: {@code admin})을 그대로 쓰는 경우가
     * 많기 때문입니다.
     */
    @Column(nullable = false, unique = true, length = 120)
    private String username;

    /** BCrypt 해시. 평문을 절대 넣지 마세요. */
    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    /** 화면에 보여줄 이름. */
    @Column(name = "display_name", length = 120)
    private String displayName;

    /**
     * 권한.
     *
     * <p>{@link EnumType#STRING} 으로 저장합니다. ORDINAL 은 나중에 상수 순서를
     * 바꾸면 기존 데이터의 의미가 조용히 바뀝니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.OPERATOR;

    /** 계정 사용 여부. 비활성 계정은 로그인할 수 없습니다. */
    @Column(nullable = false)
    private boolean enabled = true;

    /** 연속 로그인 실패 횟수. */
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    /** 잠금 해제 시각. null 이면 잠기지 않은 상태입니다. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** 마지막 로그인 시각. */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** 생성 시각. */
    @Column(name = "created_at", length = 40)
    private String createdAt;

    /**
     * 계정 생성 헬퍼.
     *
     * @param username      로그인 아이디
     * @param passwordHash  BCrypt 해시
     * @param displayName   표시 이름
     * @param role          권한
     * @return 저장 전 엔티티
     */
    public static AppUser of(String username, String passwordHash, String displayName, Role role) {
        final AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setDisplayName(displayName);
        user.setRole(role == null ? Role.OPERATOR : role);
        user.setEnabled(true);
        user.setCreatedAt(Instant.now().toString());
        return user;
    }

    /**
     * 지금 잠긴 상태인지 알려줍니다.
     *
     * @return 잠겨 있으면 {@code true}
     */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /**
     * 로그인 실패를 기록하고 필요하면 잠급니다.
     *
     * <p>5회 실패마다 잠금 시간을 늘립니다(1분, 2분, 4분 ... 최대 30분).
     * 고정 시간으로 두면 공격자가 그 주기만 기다리면 되므로 지수적으로 늘립니다.
     *
     * @param maxAttemptsBeforeLock 잠금을 시작할 실패 횟수
     */
    public void recordFailure(int maxAttemptsBeforeLock) {
        failedAttempts++;
        if (failedAttempts >= maxAttemptsBeforeLock) {
            final int over = failedAttempts - maxAttemptsBeforeLock;
            final long minutes = Math.min(30L, 1L << Math.min(over, 5));
            lockedUntil = Instant.now().plusSeconds(minutes * 60L);
        }
    }

    /** 로그인 성공을 기록하고 실패 카운터를 초기화합니다. */
    public void recordSuccess() {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = Instant.now();
    }
}
