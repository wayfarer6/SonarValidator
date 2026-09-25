package org.sonar.sonarvalidator_backend.Model.entity;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

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
 * OPNsense 방화벽 REST API 접속 정보입니다.
 *
 * <h2>배경</h2>
 * <p>OPNsense 는 자체 API 를 제공하지만 <b>API Key 와 Secret</b> 두 값을 함께
 * 보내야 인증됩니다. Basic 인증과 형태가 다르므로 별도 규칙이 필요합니다.
 *
 * <h2>왜 장치마다 따로 저장하는가</h2>
 * <p>기존 {@code OPNSenseClientConfig} 는 {@code opnsense.api-key} 프로퍼티
 * 하나만 읽어 <b>모든 장치가 같은 키를 쓴다</b> 고 가정했습니다. 실제로는
 * 방화벽마다 키가 다르므로, 장치(Agent) 단위로 저장해야 합니다.
 * 그래서 이 엔티티는 {@code agentId} 를 자연 키로 가집니다.
 *
 * <h2>⚠️ 자격증명 저장 방식</h2>
 * <p>{@link #secret} 은 <b>AES-GCM 으로 암호화해</b> 저장합니다
 * ({@code Service/secret/SecretCipher}). 평문 저장은 백업/덤프 유출 시
 * 방화벽 전체 제어권이 넘어가므로 피해야 합니다.
 *
 * <p>화면에는 <b>마스킹된 값만</b> 돌려줍니다. 저장된 비밀을 다시 읽어
 * 보여줄 이유가 없고, 보여주면 그 자체가 유출 경로가 됩니다.
 *
 * <h2>TLS 검증</h2>
 * <p>{@link #allowInsecureTls} 는 자체 서명 인증서를 쓰는 랩 환경을 위한
 * 스위치입니다. 기본값은 {@code false} 이고, 켜면 경고를 남깁니다.
 */
@Entity
@Table(name = "opnsense_credential")
@Getter
@Setter
@NoArgsConstructor
public class OPNsenseCredential {

    /** 연결 확인 상태. */
    public enum Status {
        /** 저장만 되고 아직 호출해 보지 않았습니다. */
        UNVERIFIED,
        /** 연결 확인 성공. */
        OK,
        /** 연결 확인 실패. {@link #lastError} 참고. */
        FAILED
    }

    /** 기본 키. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 대상 Agent 식별자.
     *
     * <p>Agent 1대당 자격증명 1개이므로 unique 입니다.
     */
    @Column(name = "agent_id", nullable = false, unique = true, length = 128)
    private String agentId;

    /** 표시용 이름 (예: {@code FW-DMZ-01}). */
    @Column(name = "display_name", length = 128)
    private String displayName;

    /**
     * 기준 URL (예: {@code https://10.99.143.2}).
     *
     * <p>OPNsense API 는 대부분 {@code /api/...} 하위이므로 경로는 붙이지
     * 않습니다. 포트가 기본값(443)이 아니면 여기에 포함해야 합니다.
     */
    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    /** API Key. (자격증명 자체가 비밀은 아니지만 함께 취급합니다) */
    @Column(name = "api_key", length = 255)
    private String apiKey;

    /** API Secret. AES-GCM 암호문이 들어갑니다. */
    @Column(name = "secret_encrypted", length = 1024)
    private String secret;

    /** 자체 서명 인증서 허용 여부. 랩 전용입니다. */
    @Column(name = "allow_insecure_tls", nullable = false)
    private boolean allowInsecureTls;

    /** 연결 확인 상태. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private Status status = Status.UNVERIFIED;

    /** 마지막 확인 시각. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "last_checked_at")
    private Date lastCheckedAt;

    /** 마지막 오류 메시지 (실패 시). */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /** 확인 시 읽어온 OPNsense 버전. */
    @Column(name = "detected_version", length = 64)
    private String detectedVersion;

    /** 생성 시각. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "created_at")
    private Date createdAt;

    /** 수정 시각. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    @Column(name = "updated_at")
    private Date updatedAt;

    /**
     * 연결 확인 결과를 반영합니다.
     *
     * @param ok      성공 여부
     * @param version 성공 시 감지한 버전
     * @param error   실패 시 오류 메시지
     */
    public void markChecked(boolean ok, String version, String error) {
        this.status = ok ? Status.OK : Status.FAILED;
        this.lastCheckedAt = new Date();
        this.detectedVersion = ok ? version : this.detectedVersion;
        this.lastError = ok ? null : truncate(error);
    }

    /** 오류 메시지를 컬럼 길이에 맞게 자릅니다. */
    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        final String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500);
    }
}
