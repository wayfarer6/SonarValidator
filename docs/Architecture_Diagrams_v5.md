# SonarValidator 아키텍처 다이어그램 v5.0 — AI 정책 조언 (SONAR-43)

`SonarValidator` 3계층(Agent · Backend · Frontend)의 구조를 다이어그램으로 정리한
문서입니다. 모든 내용은 **실제 소스 코드와 로컬 실기동 실측**을 기준으로 작성했습니다.

> **v5.0 (2026-09-26)** — 망분리 위반을 탐지·조치하는 데서 멈추지 않고,
> **"어떻게 고칠 것인가" 를 AI 가 제안**하도록 닫았습니다. (SONAR-43)
>
> 이전 문서: v4.0 (서비스·컨트롤러 전략 패턴 확대)
>
> v4.0 대비 주요 변경은 아래와 같습니다.
>
> - 위반 **메시지 카드에 "AI 조언" 동선** 신설 (누르면 조언 모달)
> - 새 API 3개 — 조언 요청 / 이력 / 상세 (`/api/v1/policy/advice/**`)
> - 새 테이블 1개 — `policy_advice` (조언도 실패도 저장)
> - 새 프롬프트 계층 — **등급 규칙 · 서브넷 대장 · 선택지 비교** 요구
> - 🐛 **트랜잭션 오염 결함** 발견·수정 — "공급자 없음" 안내가 **500** 으로 나가던 문제
>   (기존 로그 분석 경로에도 같은 결함이 있었습니다)
> - 백엔드 테스트 **261 → 293**

---

## 1. 무엇을 추가했는가 — 한눈에

```mermaid
flowchart LR
    subgraph BEFORE["v4.0 — 탐지와 조치까지"]
        V["위반 탐지<br/>(BDD 엔진)"] --> M["메시지 카드<br/>'무엇이 잘못됐나'"]
        M --> Q["격리 / 강제 푸시<br/>(운영자 판단)"]
    end

    subgraph AFTER["v5.0 — 조언까지"]
        V2["위반 탐지"] --> M2["메시지 카드<br/>(누를 수 있음)"]
        M2 -->|클릭| AI["AI 정책 조언<br/>'어떻게 고치나'"]
        AI --> O["해결 선택지<br/>+ 보안 영향 + 운영 부담"]
        O --> Q2["운영자가 근거를 보고 결정"]
    end
```

**핵심 문제** — 위반 카드는 *무엇이* 잘못됐는지만 말합니다. 운영자는 *어떻게*
고칠지를 스스로 판단해야 하고, 그 판단이 틀리면 **보안을 약화시키는 방향**으로
갑니다. (예: "규칙 삭제" 하나만 떠올림)

**해결** — 등급 체계·서브넷 구성·위반 목록을 함께 주고, **선택지 여러 개와 각각의
트레이드오프**를 요구합니다.

| 영역 | v4.0 | v5.0 |
| --- | --- | --- |
| 위반 카드 | 읽기 전용 텍스트 | **클릭 가능 (AI 조언)** |
| 조언 근거 | — | 판정 리포트를 **서버가 다시 계산** |
| 프롬프트 | 로그 원문 중심 | **등급 규칙 + 서브넷 대장 + 위반 목록** |
| 출력 | 원인 + 권장 조치 | **선택지 + 보안 영향 + 운영 부담** |
| 저장 | — | `policy_advice` (실패도 저장) |
| 기존 결함 | — | 🐛 **트랜잭션 오염 1건** 수정 |

---

## 2. 계층별 책임

```mermaid
flowchart TB
    subgraph FE["Frontend (React 19)"]
        CARD["위반 메시지 카드<br/>PolicyManagement.tsx"]
        MODAL["PolicyAdviceModal<br/>이력 먼저 → 새 조언"]
        RESULT["PolicyAdviceCard<br/>선택지 비교 표시"]
    end

    subgraph BE["Backend (Spring Boot)"]
        CTRL["PolicyAdviceController<br/>REST 3개"]
        SVC["ViolationAdvisorService<br/>판정 재실행 + 오케스트레이션"]
        CTX["PolicyAdviceContext<br/>프롬프트용 맥락"]
        PB["PolicyAdvicePromptBuilder<br/>시스템 지침 + 본문"]
        PS["PolicyAdviceParser<br/>응답 3모양 흡수"]
        REPO["PolicyAdviceRepository"]
    end

    subgraph AI["AI 공급자 (외부)"]
        LLM["OpenAI 호환 API<br/>(OpenAI · vLLM · Ollama …)"]
    end

    CARD -->|POST| CTRL
    MODAL -->|GET 이력| CTRL
    CTRL --> SVC
    SVC --> CTX
    SVC --> PB
    SVC --> PS
    SVC --> REPO
    PB -->|messages| LLM
    LLM -->|JSON| PS
    CTRL --> MODAL
    MODAL --> RESULT
```

**책임 분리가 중요한 지점 두 가지**

| 컴포넌트 | 책임 | 분리한 이유 |
| --- | --- | --- |
| `PolicyAdviceContext` | 등급·서브넷·위반을 **프롬프트용 표현**으로 | 판정 엔진의 센티널(`-1`)과 표시 문자열 규칙을 한 곳에 모음 |
| `PolicyAdviceParser` | 모델 응답 → 구조화 객체 | **순수 함수**라 네트워크·DB 없이 3가지 모양을 전부 테스트 가능 |

---

## 3. 클래스 다이어그램 — 조언 계층

```mermaid
classDiagram
    direction TB

    class PolicyAdviceController {
        -ViolationAdvisorService advisorService
        -NotificationService notificationService
        +advise(projectId, body, auth) Map
        +history(projectId, ruleId, limit) Map
        +detail(projectId, adviceId) Map
        -notifyIfNoteworthy(projectId, result) void
    }

    class ViolationAdvisorService {
        -ProjectService projectService
        -AiProviderService providerService
        -OpenAiCompatibleClient client
        -PolicyAdviceParser parser
        -PolicyAdviceRepository repository
        +advise(...) Map
        +history(...) List
        +detail(adviceId) Map
    }

    class PolicyAdviceContext {
        +int MAX_BRIEFS
        +int MAX_SUBNETS
        +of(project, report)$ PolicyAdviceContext
        +findBrief(ruleId, src, dst) ViolationBrief
        +briefs() List
        +subnetLines() List
        +truncated() boolean
        +bySeverity() Map
    }

    class ViolationBrief {
        +String ruleId
        +String severity
        +int classGap
        +boolean forbidden
        +Integer port
        +from(violation)$ ViolationBrief
        +describe() String
        +knownClassGap() Optional
    }

    class PolicyAdvicePromptBuilder {
        +build(provider, context, focus, prompt)$ List
        +buildUserPrompt(context, focus, prompt)$ String
    }

    class PolicyAdviceParser {
        +parse(text) PolicyAdviceAnswer
        +tryParseJson(text) JsonNode
    }

    class PolicyAdviceAnswer {
        +String riskLevel
        +List options
        +boolean structured
        +String raw
        +unstructured(raw)$ PolicyAdviceAnswer
        +normalizeRisk(value)$ String
        +toView() Map
    }

    class PolicyAdviceOption {
        +String title
        +String approach
        +String securityImpact
        +String operationalCost
        +boolean recommended
        +isUsable() boolean
    }

    class PolicyAdvice {
        +String adviceId
        +String projectKey
        +String ruleId
        +String scope
        +String optionsJson
        +boolean succeeded
        +boolean structured
    }

    PolicyAdviceController --> ViolationAdvisorService
    ViolationAdvisorService --> PolicyAdviceContext : 생성
    ViolationAdvisorService --> PolicyAdvicePromptBuilder : 위임
    ViolationAdvisorService --> PolicyAdviceParser : 위임
    ViolationAdvisorService --> PolicyAdviceRepository
    PolicyAdviceContext o-- ViolationBrief
    PolicyAdvicePromptBuilder ..> PolicyAdviceContext : 읽기
    PolicyAdviceParser ..> PolicyAdviceAnswer : 생성
    PolicyAdviceAnswer o-- PolicyAdviceOption
    ViolationAdvisorService ..> PolicyAdvice : 저장
```

### 3.1 ⚠️ 왜 판정 리포트를 **직접** 받는가 (응답 맵이 아니라)

v4.0 의 `PolicyManagement.violations()` 는 화면용 **맵**을 만듭니다. 조언 서비스가
그 맵을 되받아 파싱하면 **타입을 잃습니다.**

```mermaid
flowchart TB
    subgraph BAD["응답 맵을 되받으면"]
        A1["src_class = 'Confidential' (String)"] --> B1["ZoneClass.fromString() 재해석"]
        B1 -->|오타/표기 변경| C1["null → 등급 차이 -1"]
        C1 --> D1["⚠️ 조용히 '등급 미상' 으로 판정"]
    end

    subgraph GOOD["엔진 리포트를 직접 받으면"]
        A2["sourceZone = ZoneClass.CONFIDENTIAL"] --> B2["해석 단계 없음"]
        B2 --> C2["차이 2 · 금지 쌍 확정"]
    end
```

그래서 `PolicyAdviceContext.of(project, report)` 는 `SegmentationBddEngine.Report`
를 받습니다. **화면 계약과의 일치**는 `ViolationBrief.from()` 을 양쪽이 함께 쓰는
것으로 보장합니다 — 화면의 `sampled_packet` 과 프롬프트의 문장이 같은 규칙에서
나옵니다.

---

## 4. 프롬프트 설계 — 4가지 결정

```mermaid
flowchart TB
    ROOT["프롬프트 = 시스템 지침 + 판정 맥락 + 요청"]

    ROOT --> D1["1. 선택지를 여러 개 + 트레이드오프"]
    ROOT --> D2["2. 등급 체계를 규칙으로 명시"]
    ROOT --> D3["3. 없는 자산 가정 금지"]
    ROOT --> D4["4. 준수 상태도 평가 요청으로"]

    D1 --> R1["'규칙 삭제' 하나만 나오면<br/>운영자는 다른 선택이 있었음을 모른다"]
    D2 --> R2["등급 규칙 없이는 일반 보안론만 반복"]
    D3 --> R3["'방화벽에서 NAT' 같은<br/>토폴로지에 없는 장비를 전제"]
    D4 --> R4["위반 0건을 오류로 만들면<br/>'더 강화할 점' 질문이 막힌다"]
```

### 4.1 시스템 지침의 핵심 — 등급 규칙 **본문**

```
## 이 시스템이 적용하는 등급 규칙 (고정)
- 등급은 세 단계입니다.
  Confidential(레벨 3) · Sensitive(레벨 2) · Open(레벨 1)
- 두 서브넷의 등급 레벨 차이가 1 이하면 직접 연결을 허용합니다.
- 차이가 2 이상이면 한 단계를 건너뛰는 연결이므로 금지입니다.
  (예: Confidential(3) ↔ Open(1) 은 차이 2 → 금지)
- 즉 등급 간 이동은 반드시 인접 등급을 거쳐야 합니다.
```

⚠️ **등급 규칙을 사용자 메시지에도 넣지 않습니다.** 시스템 지침에 한 번만 둡니다.
두 곳에 같는 문단을 넣으면 **토큰만 두 배**로 쓰고 얻는 것이 없습니다. 대신
사용자 메시지에는 "그 규칙으로 판정했다" 는 사실만 확인시켜 줍니다.
(테스트 `systemPromptCarriesRules` 가 시스템 지침에 규칙이 있는지 검증합니다 —
처음에 규칙을 사용자 메시지에만 두어 이 테스트가 실패했고, 그때 바로잡았습니다)

### 4.2 출력 스키마 — 선택지가 곧 계약

```json
{
  "risk_level": "CRITICAL | HIGH | MEDIUM | LOW",
  "summary": "전체 위반 상황 한두 문장 요약",
  "root_cause": "근본 원인 판단. 근거가 약하면 '근거 부족'이라 명시",
  "policy_advice": "정책 관점의 핵심 조언 (한 줄)",
  "options": [
    {
      "title": "선택지 이름 (예: 등급 재분류)",
      "approach": "구체적으로 무엇을 어떻게 바꾸는지",
      "security_impact": "보안에 미치는 영향",
      "operational_cost": "운영 부담 (재작업·중단·비용)",
      "recommended": true
    }
  ],
  "evidence": ["판단 근거가 된 위반/서브넷 (최대 5개)"],
  "needs_more_data": false
}
```

`security_impact` 와 `operational_cost` 를 **스키마에 못 박은 이유**: 화면이 두
값을 **나란히** 보여줘야 운영자가 트레이드오프를 봅니다. 한쪽만 있으면 "쉬운 안"
만 고르게 됩니다.

---

## 5. 시퀀스 — 위반 카드를 눌러 조언을 받기까지

```mermaid
sequenceDiagram
    autonumber
    actor OP as 운영자
    participant UI as PolicyManagement.tsx
    participant MD as PolicyAdviceModal
    participant CT as PolicyAdviceController
    participant SV as ViolationAdvisorService
    participant PS as ProjectService
    participant AI as OpenAiCompatibleClient
    participant LLM as AI 공급자

    OP->>UI: 위반 메시지 카드 클릭
    UI->>MD: adviceTarget 설정 (rule_id/src/dst)
    activate MD
    Note over MD: ⚠️ 열면 먼저 이력 — AI 재호출 방지

    MD->>CT: GET /policy/advice/{projectId}?rule_id=…
    CT->>SV: history(projectId, ruleId, limit)
    SV-->>CT: 기존 조언 목록
    CT-->>MD: { total, advices[] }
    MD-->>OP: 이전 조언 즉시 표시

    OP->>MD: "다시 물어보기" 클릭
    MD->>CT: POST /policy/advice/{projectId} { rule_id, prompt }
    CT->>SV: advise(projectKey, ruleId, src, dst, providerId, prompt, user)
    activate SV

    Note over SV,PS: 1) 판정은 서버가 다시 한다<br/>(프론트 텍스트를 신뢰하지 않음)
    SV->>PS: validateStored(projectKey)
    PS-->>SV: SegmentationBddEngine.Report

    Note over SV: 2) 프롬프트용 맥락으로 정리<br/>(심각도 순 정렬 + 잘림)
    SV->>SV: PolicyAdviceContext.of(project, report)
    SV->>SV: findBrief(ruleId, src, dst)

    Note over SV: 3) 공급자 선택 — 예외 없는 조회
    SV->>SV: providerService.resolveOrEmpty(providerId)
    alt 공급자 없음
        SV->>SV: record.succeeded = false
        SV->>SV: repository.save(record)
        SV-->>CT: { succeeded: false, error_message }
        CT-->>MD: 200 (실패 사유)
        MD-->>OP: 설정 안내 표시
    end

    Note over SV,LLM: 4) 프롬프트 조립 → 호출
    SV->>SV: PolicyAdvicePromptBuilder.build(...)
    SV->>AI: chat(connection, messages, jsonMode)
    AI->>LLM: POST /chat/completions
    LLM-->>AI: JSON (또는 코드블록 감싼 JSON)
    AI-->>SV: Result(ok, text, elapsedMs)

    Note over SV: 5) 응답 파싱 — 3가지 모양 흡수
    SV->>SV: PolicyAdviceParser.parse(text)
    SV->>SV: repository.save(record)
    deactivate SV
    SV-->>CT: toView(record)

    CT->>CT: notifyIfNoteworthy (CRITICAL/HIGH 만)
    CT-->>MD: 200 { advice_id, options[], structured, … }
    MD-->>OP: "방금 받은 조언" + 선택지 비교 카드
    deactivate MD
```

### 5.1 ⚠️ 왜 "열면 이력 먼저" 인가

```mermaid
flowchart LR
    A["카드 클릭"] --> B{"이력이 있는가"}
    B -->|있음| C["기존 조언 표시<br/>비용 0"]
    B -->|없음| D["'조언 요청' 안내"]
    C --> E{"운영자가 다시 묻는가"}
    E -->|예| F["POST → AI 호출<br/>비용 발생"]
    E -->|아니오| G["끝"]

    H["⚠️ 열자마자 호출하면"] -.->|매 클릭마다| I["같은 위반에 과금 반복"]
    I -.-> J["결론은 같은데 비용만 증가"]
```

같은 입력으로 AI 를 여러 번 부르면 **비용만 쓰고 결론은 같습니다.**
그래서 v4.0 의 `LogAnalysisEngine` 과 같은 순서를 지킵니다.

---

## 6. 시퀀스 — 🐛 트랜잭션 오염 결함 (발견 → 수정)

이번 작업에서 **실제 결함 1건**을 찾았습니다. 기존 로그 분석 경로에도 **같은
결함**이 있었습니다.

### 6.1 증상

```mermaid
sequenceDiagram
    autonumber
    participant C as Controller
    participant Svc as ViolationAdvisorService<br/>@Transactional
    participant PS as AiProviderService.resolve<br/>@Transactional(readOnly)
    participant DB as Repository

    C->>Svc: advise(...)
    activate Svc
    Note over Svc: 트랜잭션 T 시작
    Svc->>PS: resolve(providerId)
    activate PS
    Note over PS: T 에 <b>참여</b>
    PS-->>Svc: throw IllegalStateException
    deactivate PS
    Note over PS: ⚠️ T 를 <b>rollback-only 로 마킹</b>
    Svc->>Svc: catch → record.succeeded=false
    Svc->>DB: save(record) — 실패 기록 시도
    Note over Svc: 200 으로 돌려주려 함
    deactivate Svc
    Note over Svc: 커밋 시도
    Svc--xC: UnexpectedRollbackException
    Note over C: ⚠️ 500 Internal Server Error<br/>실패 기록도 롤백되어 사라짐
```

### 6.2 핵심 — "예외를 잡아도 마킹은 되돌릴 수 없다"

```mermaid
flowchart TB
    A["내부 메서드가 RuntimeException"] --> B["Spring: 이 트랜잭션은 rollback-only"]
    B --> C["호출자가 catch 로 예외를 삼킴"]
    C --> D["그러나 마킹은 남아 있음"]
    D --> E["커밋 시점 → UnexpectedRollbackException"]
    E --> F["500 + 실패 기록 소실"]

    G["⚠️ 예외를 잡는 것으로는 막을 수 없다"] -.-> D
```

**영향 범위** — AI 공급자를 아직 등록하지 않은 **모든 신규 설치**가 이 경로를
밟습니다. "설정에서 공급자를 등록하세요" 라는 **친절한 안내가 500 으로** 나갑니다.

### 6.3 수정

| 변경 | 내용 |
| --- | --- |
| 조회 전파 | `resolve` / `resolveOrEmpty` → **`Propagation.NOT_SUPPORTED`** |
| 예외 없는 조회 | `resolveOrEmpty` **신설** (`Optional` 반환, 예외를 만들지 않음) |
| 호출자 2곳 | `LogAnalysisEngine` · `ViolationAdvisorService` → `resolveOrEmpty` 사용 |

```mermaid
flowchart LR
    subgraph BEFORE["이전"]
        B1["resolve() throws"] --> B2["T rollback-only"] --> B3["500"]
    end

    subgraph AFTER["이후"]
        A1["resolveOrEmpty()<br/>NOT_SUPPORTED"] --> A2["마킹할 T 가 없음"]
        A2 --> A3["실패 기록 저장"]
        A3 --> A4["200 + 사유"]
    end
```

**실측 확인**

```text
이전: POST /api/v1/policy/advice/{id}  → 500 UnexpectedRollbackException
이후: HTTP=200
      {"succeeded": false,
       "error_message": "사용 가능한 AI 공급자가 없습니다.
                         설정에서 AI 공급자를 등록하고 '사용' 을 켜세요.",
       "advice_id": "PADV-DA42B6F5", "elapsed_ms": 30}
```

**회귀 방지** — `AiProviderTransactionSafetyTest` 가 **전파 수준을 리플렉션으로**
검증합니다. 런타임 프록시 동작이라 단위 테스트로 재현하기 어렵기 때문에, 대신
**결함을 만든 설정값**을 직접 확인합니다. `REQUIRED` 로 되돌아가면 즉시 실패합니다.

---

## 7. 클래스 다이어그램 — 값 객체와 파싱

### 7.1 ⚠️ 센티널 누출을 막는다

판정 엔진의 `sampledPort` 는 `ANY_PORT` = **-1** 로 "전체 포트" 를 나타냅니다.
이 값을 그대로 프롬프트에 넣으면 모델이 **"포트 -1" 을 실제 포트로 읽고**
없는 문제를 지어냅니다.

```mermaid
flowchart LR
    A["PolicyViolation<br/>sampledPort = -1"] --> B["ViolationBrief.from()"]
    B --> C{"port == ANY_PORT"}
    C -->|예| D["port = null<br/>문장: '포트 제한 없음'"]
    C -->|아니오| E["port = 8080<br/>문장: '포트 8080'"]

    F["⚠️ -1 을 그대로 넣으면"] -.-> G["모델이 '포트 -1' 을<br/>실제 포트로 해석"]
```

같은 원칙으로 **등급 미상**을 차이 `0` 으로 만들지 않습니다.

```mermaid
flowchart TB
    A["등급을 모름"] --> B{"어떻게 표현할까"}
    B -->|틀린 방법| C["gap = 0"]
    C --> D["⚠️ '등급이 같다' 는<br/><b>거짓 사실</b>이 프롬프트에 들어감"]
    B -->|맞는 방법| E["gap = -1"]
    E --> F["'등급 미지정' 으로 서술<br/>forbidden = false"]
```

### 7.2 파싱 — 모델 응답 3가지 모양

```mermaid
flowchart TB
    RAW["모델 응답"] --> A{"시작이 ``` 인가"}
    A -->|예| B["코드블록 벗기기"]
    A -->|아니오| C{"{ 로 시작하는가"}
    B --> C
    C -->|아니오| D["첫 { 부터 마지막 } 추출"]
    C -->|예| E["그대로"]
    D --> E
    E --> F{"JSON 파싱 성공?"}
    F -->|아니오| G["unstructured<br/>raw 만 보존"]
    F -->|예| H["PolicyAdviceAnswer"]
```

**스키마 이탈도 살립니다.**

| 이탈 | 처리 |
| --- | --- |
| `options` 대신 `recommendations` 사용 | 키 목록으로 흡수 |
| `options` 를 문자열 배열로 | 제목만 있는 선택지로 **승격** |
| `evidence` 를 문자열 하나로 | 1개짜리 배열로 승격 |
| `policy_advice` 대신 `advice`/`note` | 키 목록으로 흡수 |
| 제목이 빈 선택지 | **버림** (내용 없는 카드 방지) |

```mermaid
flowchart LR
    A["options: ['등급 재분류', '중계 신설']"] --> B["문자열 배열"]
    B -->|버리면| C["⚠️ 조언의 절반이 사라짐"]
    B -->|승격| D["PolicyAdviceOption(title=문자열)"]
    D --> E["'무엇을 고려해야 하는지' 라도 전달"]
```

---

## 8. 데이터 모델 — `policy_advice`

```mermaid
erDiagram
    PROJECT ||--o{ POLICY_ADVICE : "조언 대상"
    POLICY_ADVICE {
        bigint id PK
        string advice_id UK "PADV-XXXXXXXX"
        string project_key "조회 축"
        string project_name "삭제·개명 대비"
        string rule_id "초점 위반 (선택)"
        string scope "violation | project"
        int violation_count "스냅샷"
        boolean compliant "스냅샷"
        int included_violation_count
        boolean truncated "잘림 여부"
        string provider_name
        string model
        bigint elapsed_ms
        string risk_level
        string summary
        string root_cause
        string policy_advice
        string options_json "핵심 — 선택지 배열"
        string evidence_json
        boolean needs_more_data
        string raw_response "파싱 실패 대비"
        boolean structured
        string requested_by
        string error_message
        boolean succeeded "실패도 저장"
        timestamp created_at
    }
```

### 8.1 왜 로그 분석 테이블에 합치지 않았나

```mermaid
flowchart TB
    subgraph SPLIT["별도 테이블 (선택)"]
        A["log_analysis<br/>기간 · 장비 축"]
        B["policy_advice<br/>프로젝트 · 규칙 축"]
    end

    subgraph MERGED["한 테이블 (합쳤다면)"]
        C["⚠️ 한쪽만 쓰는 컬럼이 절반"]
        D["⚠️ 인덱스·정렬 기준이 다름"]
        E["⚠️ 조회마다 '이 행은 어느 종류인가' 판정"]
    end
```

| | `log_analysis` | `policy_advice` |
| --- | --- | --- |
| 대상 | 로그 줄 | 위반 / 프로젝트 |
| 조회 축 | 기간 · 장비 | 프로젝트 · 규칙 |
| 핵심 필드 | `summary`, `root_cause` | `policy_advice`, **`options_json`** |
| 선택지 비교 | 없음 (조치 나열) | **있음 (트레이드오프)** |

### 8.2 ⚠️ 스냅샷을 함께 저장하는 이유

`violation_count` · `compliant` 를 조언 시점 값으로 남깁니다. 나중에 프로젝트가
바뀌어도 **"그때 몇 건을 보고 이 조언을 했나"** 를 되짚을 수 있어야 합니다.
조언을 근거로 정책을 바꾸는 결정을 **감사(audit)** 할 때 필요합니다.

---

## 9. API 계약

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/v1/policy/advice/{projectId}` | 조언 요청 |
| GET | `/api/v1/policy/advice/{projectId}` | 이력 (`?rule_id=`, `?limit=`) |
| GET | `/api/v1/policy/advice/{projectId}/{adviceId}` | 상세 |

```json
// POST 요청
{ "rule_id": "Rule-0001",        // 없으면 프로젝트 전체
  "src_subnet": "Subnet-0001",   // 같은 규칙의 다른 쌍 구분
  "dst_subnet": "Subnet-0002",
  "provider_id": 3,              // 없으면 기본 공급자
  "prompt": "군사 규정 관점에서 봐줘" }
```

### 9.1 ⚠️ POST 이지 PUT 이 아닌 이유

```mermaid
flowchart LR
    A["PUT = 여러 번 보내도 같은 결과"] --> B["이 동작에는 거짓"]
    B --> C["같은 요청 2번 → AI 호출 2번<br/>비용 2배"]
    D["POST + GET 이력"] --> E["재사용은 이력으로<br/>새 호출은 명시적 행위"]
```

### 9.2 ⚠️ 실패도 200 을 돌려준다

AI 호출 실패(키 만료·모델명 오타·서버 미기동)는 **사유가 담긴 정상 응답**입니다.
(`succeeded=false`, `error_message`) 5xx 로 만들면 화면이 사유를 보여주기 어렵고
이력에도 남기기 어렵습니다. — `LogController.analyze` 와 같은 규칙입니다.

### 9.3 알림 연동

```mermaid
flowchart TB
    A["조언 성공"] --> B{"risk_level"}
    B -->|CRITICAL| C["POLICY / critical 알림"]
    B -->|HIGH| D["POLICY / warning 알림"]
    B -->|MEDIUM · LOW| E["알림 없음 (이력만)"]
    F["조언 실패"] --> G["알림 없음 (이력만)"]

    C --> H["dedupeKey =<br/>policy-advice:project:rule:risk"]
    H --> I["반복 조회 시 합쳐짐"]
```

⚠️ **알림 실패가 조언 응답을 막지 않습니다.** 조언은 이미 만들어졌고 이력에도
남았으므로, 알림 실패가 500 을 만들면 "조언은 성공했는데 화면은 오류" 라는
모순이 생깁니다.

---

## 10. 프론트엔드 — 조언 표시

```mermaid
flowchart TB
    subgraph PAGE["PolicyManagement.tsx"]
        MSG["메시지 카드<br/>(프로젝트 전체)"]
        ROW["위반 행 'AI 조언' 버튼<br/>(이 한 건)"]
    end

    MSG --> MODAL["PolicyAdviceModal"]
    ROW --> MODAL

    MODAL --> HIST["이력 먼저 표시"]
    MODAL --> ASK["다시 물어보기"]
    ASK --> CARD["PolicyAdviceCard"]

    CARD --> R1["위험도 배지"]
    CARD --> R2["정책 조언 (핵심 한 줄)"]
    CARD --> R3["요약 · 근본 원인"]
    CARD --> R4["해결 선택지<br/>보안 영향 | 운영 부담"]
    CARD --> R5["판단 근거"]
```

### 10.1 ⚠️ 실패와 빈 응답을 구분한다

```mermaid
flowchart TB
    A["succeeded = false"] --> B["호출 자체 실패"]
    B --> C["화면: '조언 실패'<br/>+ 공급자 설정 확인 안내"]

    D["structured = false"] --> E["호출은 성공, 형식 이탈"]
    E --> F["화면: 원문 그대로 표시"]

    G["⚠️ 둘을 같이 다루면"] -.-> H["운영자가 고칠 대상<br/>(설정 vs 프롬프트)을 모른다"]
```

### 10.2 트레이드오프를 나란히 보여준다

```mermaid
flowchart LR
    A["선택지 카드"] --> B["보안 영향"]
    A --> C["운영 부담"]
    B --> D["⚠️ 한쪽만 보이면<br/>'쉬운 안' 만 고른다"]
    C --> D
```

`recommended=true` 인 선택지는 브랜드 색으로 강조하되, **다른 선택지를 숨기지
않습니다** — 운영자가 상황에 따라 덜 권장되는 안을 골라야 할 수 있습니다.

---

## 11. 실측 검증 결과 (v5.0)

### 11.1 테스트

| 항목 | v4.0 | v5.0 | 비고 |
| --- | --- | --- | --- |
| Backend | 261 | **293** | 신규 32건 |
| Prober | 13 | 13 | 변경 없음 |
| Frontend tsc | clean | **clean** | 신규 파일 포함 |
| Frontend eslint | 0 errors | **0 errors** | 신규 파일 포함 |

신규 테스트 파일 2개:

- `PolicyAdviceTest` (27건) — 센티널 누출, 등급 미상, 응답 3모양, 스키마 이탈,
  프롬프트 계약, 심각도 정렬/잘림, 규칙+쌍 매칭
- `AiProviderTransactionSafetyTest` (5건) — 트랜잭션 전파, 예외 없는 조회,
  관리자 경로 계약 유지

### 11.2 실기동 E2E (HTTP, 포트 3000 + 브라우저 클릭)

| # | 검증 | 결과 |
| --- | --- | --- |
| 1 | 공급자 없이 조언 요청 | ✅ **200** `succeeded=false` + 설정 안내 (이전: 500) |
| 2 | 테이블 자동 생성 | ✅ `policy_advice` 26 컬럼 |
| 3 | 위반 1건 집중 조언 | ✅ `scope=violation`, 선택지 **2개**, 근거 1건 |
| 4 | 프로젝트 전체 조언 | ✅ `scope=project`, 선택지 2개 |
| 5 | 이력 (rule_id 필터) | ✅ 2건 반환 (그 규칙만) |
| 6 | 이력 (전체) | ✅ 4건, 실패 건도 포함 |
| 7 | 알림 연동 | ✅ `POLICY / critical` 2건 (CRITICAL 조언) |
| 8 | 브라우저 — 카드 클릭 | ✅ 모달 열림, 이력 3건 즉시 표시 |
| 9 | 브라우저 — 다시 물어보기 | ✅ "방금 받은 조언" + 이력 4건 |
| 10 | 콘솔 오류 | ✅ **0건** |
| 11 | 선택지 트레이드오프 표시 | ✅ 보안 영향 · 운영 부담 나란히 |
| 12 | 코드블록 감싼 응답 파싱 | ✅ 실제 모델 이탈 재현 → 정상 파싱 |

### 11.3 🐛 발견·수정한 결함

| 결함 | 증상 | 위험도 |
| --- | --- | --- |
| 공급자 조회가 호출자 트랜잭션을 오염 | "공급자 없음" 안내가 **500** 으로 나가고 실패 기록도 소실 | **높음** (신규 설치 전부 해당) |

```mermaid
flowchart LR
    A["실기동 E2E"] --> B["공급자 없이 조언 요청"]
    B --> C["⚠️ 500 UnexpectedRollbackException"]
    C --> D["원인 추적"]
    D --> E["내부 readOnly 조회가<br/>호출자 T 를 rollback-only 로 마킹"]
    E --> F["NOT_SUPPORTED + resolveOrEmpty"]
    F --> G["200 + 사유"]
    G --> H["기존 로그 분석 경로에도<br/>같은 결함 → 함께 수정"]
```

---

## 12. v4.0 → v5.0 변경 요약

| 항목 | v4.0 | v5.0 |
| --- | --- | --- |
| 위반 카드 | 읽기 전용 | **클릭 → AI 조언** |
| 조언 API | — | **3개** (요청/이력/상세) |
| 조언 테이블 | — | `policy_advice` |
| 프롬프트 | 로그 원문 | **등급 규칙 + 서브넷 대장 + 위반 목록** |
| 출력 스키마 | 원인 + 조치 | **선택지 + 보안 영향 + 운영 부담** |
| 조언 재사용 | — | **이력 우선** (AI 재호출 방지) |
| 트랜잭션 안전 | — | **`NOT_SUPPORTED`** (결함 수정) |
| 백엔드 테스트 | 261 | **293** |
| 발견한 결함 | — | **1건** (기존 경로 포함) |