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

@Entity
@Table(name = "\"USER\"")
@Getter
@Setter
@NoArgsConstructor
public class User {

    public enum Role {
        ADMIN,
        OPERATOR,
        VIEWER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.OPERATOR;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_login_at")
    private Date lastLoginAt;

    @Column(name = "created_at")
    private Date createdAt;

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

    public void recordSuccess() {
        lastLoginAt = new Date();
    }

    @Deprecated
    public boolean isLocked() {
        return false;
    }

    public Instant lastLoginInstant() {
        return lastLoginAt == null ? null : lastLoginAt.toInstant();
    }
}
