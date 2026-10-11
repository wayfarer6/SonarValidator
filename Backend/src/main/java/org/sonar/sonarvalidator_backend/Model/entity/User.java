package org.sonar.sonarvalidator_backend.Model.entity;

import java.time.Instant;
import java.util.Date;

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
 * <h2>계정 잠금 정책</h2>
 * <p>한때 {@code failed_attempts} / {@code locked_until} 로 무차별 대입을
 * 늦췄으나, 요구사항 변경으로 <b>잠금 정책을 두지 않습니다</b>.
 * 잠금 관련 컬럼과 로직은 제거했고, 코드 정리(사용자 관리 화면의 잠금
 * 표시 제거)는 후속 작업에서 진행합니다.
 *
 * <h2>테이블 이름이 {@code USER} 인 이유</h2>
 * <p>설계 ERD 와 동일하게 맞추기 위해 테이블 이름은 {@code USER} 입니다.
 * 다만 {@code USER} 는 H2 와 PostgreSQL 에서 <b>예약어(reserved word)</b>
 * 이므로 따옴표 없이 쓰면 {@code CREATE TABLE user (...)} 가 문법 오류로
 * 실패합니다. 그래서 {@code name} 값에 이스케이프한 따옴표를 포함해
 * <b>항상</b> {@code "USER"} 로 인용되게 합니다.
 */
@Entity
@Table(name = "\"USER\"")
@Getter
@Setter
@NoArgsConstructor
public class User {

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

    /** 마지막 로그인 시각. */
    @Column(name = "last_login_at")
    private Date lastLoginAt;

    /** 생성 시각. */
    @Column(name = "created_at")
    private Date createdAt;

    /**
     * 계정 생성 헬퍼.
     *
     * @param username      로그인 아이디
     * @param passwordHash  BCrypt 해시
     * @param displayName   표시 이름
     * @param role          권한
     * @return 저장 전 엔티티
     */
    public static User of(String username, String passwordHash, String displayName, Role role) {
        final User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setDisplayName(displayName);
        user.setRole(role == null ? Role.OPERATOR : role);
        user.setEnabled(true);
        user.setCreatedAt(new Date());
        return user;
    }

    /**
     * 로그인 성공을 기록합니다.
     *
     * <p>마지막 로그인 시각만 갱신합니다. (실패 카운터/잠금은 제거됨)
     */
    public void recordSuccess() {
        lastLoginAt = new Date();
    }

    /**
     * 지금 잠긴 상태인지 알려줍니다.
     *
     * <p>잠금 정책이 제거되어 <b>항상 {@code false}</b> 입니다. 인증
     * 구성과 사용자 관리 응답이 아직 이 메서드를 호출하므로, 호출부를
     * 전부 고치는 후속 작업까지 <b>컴파일 호환용</b> 으로 남겨 둡니다.
     *
     * @return 항상 {@code false}
     * @deprecated 잠금 정책 제거에 따라 후속 작업에서 삭제 예정입니다.
     */
    @Deprecated
    public boolean isLocked() {
        return false;
    }

    /**
     * 마지막 로그인 시각을 돌려줍니다. (편의용, {@link Date} 그대로)
     *
     * @return 마지막 로그인 시각 (없으면 null)
     */
    public Instant lastLoginInstant() {
        return lastLoginAt == null ? null : lastLoginAt.toInstant();
    }
}
