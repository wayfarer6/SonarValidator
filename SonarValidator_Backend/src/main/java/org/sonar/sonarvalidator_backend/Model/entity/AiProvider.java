package org.sonar.sonarvalidator_backend.Model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * OpenAI 호환 AI 공급자 설정입니다. (별도 테이블)
 *
 * <h2>왜 별도 테이블인가</h2>
 * <p>AI 공급자 정보는 <b>자주 바뀌는 운영 설정</b>이고, 여러 화면(로그 분석,
 * 향후 정책 요약 등)이 같은 값을 공유합니다. 애플리케이션 프로퍼티로 두면
 * 값을 바꿀 때마다 재기동이 필요하고, 여러 공급자를 오가며 쓸 수 없습니다.
 * 그래서 테이블로 분리하고 REST 로 관리합니다.
 *
 * <h2>OpenAI 호환이란</h2>
 * <p>{@code POST {baseUrl}/chat/completions} 에 아래 형태로 요청하고
 * {@code choices[0].message.content} 를 받는 규격을 말합니다.
 *
 * <pre>
 *   {"model":"gpt-4o-mini","messages":[{"role":"user","content":"..."}]}
 * </pre>
 *
 * <p>이 규격은 OpenAI 뿐 아니라 Azure OpenAI, Ollama, vLLM, LocalAI,
 * Together, Groq, OpenRouter 등이 사실상 같은 형태로 제공합니다.
 * 그래서 공급자별 코드 분기 없이 baseUrl 만 바꾸면 됩니다.
 *
 * <h2>⚠️ API Key 를 어떻게 저장하는가</h2>
 * <p>OPNsense Secret 과 같은 방식으로 <b>AES-256-GCM 암호화</b>해 저장합니다
 * ({@link org.sonar.sonarvalidator_backend.Service.secret.SecretCipher}).
 * 조회 API 는 <b>마스킹된 값만</b> 돌려주고 원문은 절대 내보내지 않습니다.
 *
 * <p>평문 저장을 피하는 이유: DB 백업/덤프가 유출되면 그대로 키가 유출되고,
 * 그 키로 <b>과금되는 API</b> 를 호출당할 수 있습니다.
 */
@Entity
@Table(name = "ai_provider")
@Getter
@Setter
@NoArgsConstructor
public class AiProvider {

    /** 기본 키. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사람이 읽는 공급자 이름 (예: {@code Local Ollama}, {@code OpenAI}). */
    @Column(nullable = false, length = 120)
    private String name;

    /**
     * OpenAI 호환 엔드포인트의 기준 URL 입니다.
     *
     * <p>끝 슬래시와 {@code /v1} 유무를 모두 허용합니다. 저장 시에는
     * 끝 슬래시만 제거하고, 호출 경로를 붙일 때 {@code /v1} 중복을 피합니다.
     *
     * <pre>
     *   https://api.openai.com/v1
     *   http://192.168.122.58:11434/v1    (Ollama)
     *   https://my-resource.openai.azure.com/openai/deployments/gpt-4o
     * </pre>
     */
    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    /**
     * API Key 의 AES-GCM 암호문입니다. (Base64)
     *
     * <p>로컬 Ollama 처럼 키가 필요 없는 공급자도 있으므로 null 을 허용합니다.
     * 이 경우 Authorization 헤더를 아예 붙이지 않습니다.
     */
    @Column(name = "api_key_encrypted", length = 2000)
    private String apiKeyEncrypted;

    /** 사용할 모델 이름 (예: {@code gpt-4o-mini}, {@code llama3.1:8b}). */
    @Column(name = "model", nullable = false, length = 200)
    private String model;

    /**
     * Azure OpenAI 처럼 {@code api-key} 헤더를 요구하는 공급자를 위한 선택지입니다.
     *
     * <p>기본은 {@code Authorization: Bearer <key>} 입니다. Azure 는
     * {@code api-key: <key>} 를 쓰므로 이 값을 {@code azure} 로 두면 헤더가 바뀝니다.
     */
    @Column(name = "auth_style", length = 20)
    private String authStyle = "bearer";

    /** 추가 시스템 프롬프트. 공급자/조직별 분석 관점을 넣을 때 씁니다. */
    @Column(name = "system_prompt", length = 4000)
    private String systemPrompt;

    /** 요청 타임아웃(초). 로컬 소형 모델은 느릴 수 있어 넉넉히 둡니다. */
    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds = 120;

    /** 응답 최대 토큰 수. null 이면 요청에 넣지 않습니다(공급자 기본값). */
    @Column(name = "max_tokens")
    private Integer maxTokens;

    /** 창의성. 로그 분석은 사실 기반이라 낮게 유지하는 편이 좋습니다. */
    @Column(name = "temperature")
    private Double temperature = 0.2;

    /** TLS 인증서 검증 건너뛰기. (자체 서명 인증서를 쓰는 사내 서버용) */
    @Column(name = "allow_insecure_tls")
    private Boolean allowInsecureTls = false;

    /**
     * 기본 공급자 여부입니다.
     *
     * <p>여러 공급자를 등록해 두고 상황에 따라 고를 수 있습니다. 분석 요청에
     * 공급자를 지정하지 않으면 이 값이 true 인 것을 씁니다.
     *
     * <h3>⚠️ 초기값을 {@code null} 로 두는 이유</h3>
     * <p>{@code false} 로 초기화하면 <b>"값을 주지 않음" 과 "명시적으로 해제" 를
     * 구분할 수 없습니다.</b> 그러면 "첫 공급자는 자동으로 기본" 규칙을
     * 적용할 수 없어, 공급자를 하나만 등록해도 기본이 없어집니다.
     * (분석이 알파벳순으로 고르게 되어 엉뚱한 공급자로 요청이 갑니다)
     *
     * <p>{@code null} 은 "아직 정해지지 않음" 을 뜻하고, 조회 시
     * {@code Boolean.TRUE.equals(...)} 로 처리해 API 에서는 false 로 보입니다.
     */
    @Column(name = "is_default")
    private Boolean isDefault;

    /**
     * 사용 여부. 끄면 분석 대상에서 제외됩니다(삭제 대신 보존).
     *
     * <p>이 필드는 {@code true} 로 초기화해도 안전합니다. "값을 주지 않음" 과
     * "명시적으로 끔" 을 구분할 필요가 없기 때문입니다. (새 공급자는 사용 상태가
     * 자연스럽고, 끄는 것은 명시적 행위입니다)
     */
    @Column(name = "enabled")
    private Boolean enabled = true;

    /** 마지막 연결 확인 결과 ({@code ok} / {@code failed} / null). */
    @Column(name = "last_status", length = 40)
    private String lastStatus;

    /** 마지막 연결 확인 메시지 (실패 사유 등). */
    @Column(name = "last_message", length = 1000)
    private String lastMessage;

    /** 마지막 연결 확인 시각 (ISO-8601). */
    @Column(name = "last_checked_at", length = 40)
    private String lastCheckedAt;

    /** 생성 시각 (ISO-8601). */
    @Column(name = "created_at", length = 40)
    private String createdAt;

    /** 수정 시각 (ISO-8601). */
    @Column(name = "updated_at", length = 40)
    private String updatedAt;
}
