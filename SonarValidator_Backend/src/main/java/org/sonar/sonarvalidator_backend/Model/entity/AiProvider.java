package org.sonar.sonarvalidator_backend.Model.entity;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_provider")
@Getter
@Setter
@NoArgsConstructor
public class AiProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "api_key_encrypted", length = 2000)
    private String apiKeyEncrypted;

    @Column(name = "model", nullable = false, length = 200)
    private String model;

    @Column(name = "auth_style", length = 20)
    private String authStyle = "bearer";

    @Column(name = "system_prompt", length = 4000)
    private String systemPrompt;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds = 120;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "temperature")
    private Double temperature = 0.2;

    @Column(name = "allow_insecure_tls")
    private Boolean allowInsecureTls = false;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "last_status", length = 40)
    private String lastStatus;

    @Column(name = "last_message", length = 1000)
    private String lastMessage;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "last_checked_at")
    private Date lastCheckedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "created_at")
    private Date createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "updated_at")
    private Date updatedAt;
}
