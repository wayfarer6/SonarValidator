package org.sonar.sonarvalidator_backend.Model.entity;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.sonar.sonarvalidator_backend.Model.Configuration;

@Entity
@Table(name = "opnsense_credential")
@Getter
@Setter
@NoArgsConstructor
public class OPNsenseCredential {

    public enum Status {
        UNVERIFIED,
        OK,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "node_id", nullable = false, unique = true)
    private Configuration node;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    @Column(name = "api_key", length = 255)
    private String apiKey;

    @Column(name = "secret_encrypted", length = 1024)
    private String secret;

    @Column(name = "allow_insecure_tls", nullable = false)
    private boolean allowInsecureTls;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private Status status = Status.UNVERIFIED;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "last_checked_at")
    private Date lastCheckedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "detected_version", length = 64)
    private String detectedVersion;

    @Column(name = "project_key", length = 120)
    private String projectKey;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "created_at")
    private Date createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "updated_at")
    private Date updatedAt;

    public void markChecked(boolean ok, String version, String error) {
        this.status = ok ? Status.OK : Status.FAILED;
        this.lastCheckedAt = new Date();
        this.detectedVersion = ok ? version : this.detectedVersion;
        this.lastError = ok ? null : truncate(error);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        final String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500);
    }
}
