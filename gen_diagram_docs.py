#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""docs/BackendDiagrams 문서 생성 (ASCII 전용 스크립트, 한글 손상 방지)."""
import io
import os
import re

OUT = os.path.join('docs', 'BackendDiagrams')
os.makedirs(OUT, exist_ok=True)

FILES = {}

# ---------------------------------------------------------------------------
# README (인덱스)
# ---------------------------------------------------------------------------
FILES['README.md'] = '''# Backend 다이어그램 문서

이 폴더는 **프론트엔드-백엔드 연결**과 **망분리 검증(BDD)** 구현을 설명하는
다이어그램 문서 모음입니다.

## 문서 목록

| 파일 | 내용 |
| --- | --- |
| `01-frontend-backend-connection.md` | 프론트엔드와 백엔드가 어떻게 연결되는가 (REST, CORS, 포트) |
| `02-project-editor-sequence.md` | 프로젝트 편집 → 저장 → 검증 시퀀스 다이어그램 |
| `03-project-editor-classes.md` | 프로젝트 편집 기능의 클래스 다이어그램 |
| `04-segmentation-bdd.md` | 망분리 검증 BDD 엔진 원리와 클래스 구조 |
| `05-policy-validation-sequence.md` | 정책 위반 조회/푸시 시퀀스 다이어그램 |
| `06-bdd-vs-batfish.md` | Batfish 와의 비교 및 설계 근거 |

## 한 줄 요약

```
프론트엔드 (React)                 백엔드 (Spring Boot)              Agent (C++ Prober)
     |                                    |                                  |
     |--- REST /api/v1/projects --------->|                                  |
     |<-- 프로젝트/위반/토폴로지 ----------|                                  |
     |                                    |<--- WebSocket /api/v1/telemetry --|
     |                                    |--- policy-response -------------->|
     |                                    |                                  |
     |      망분리 위반 판정은 서버가 BDD 로 수행 (프론트는 표시만)             |
```

## 핵심 원칙

1. **위반 판정은 서버가 한다.** 프론트엔드 검증은 즉시 피드백용이며,
   최종 판정은 서버의 BDD 연산 결과입니다.
2. **프론트엔드는 벤더를 모른다.** 서버가 중립 구조(`NeutralDeviceConfig`)로
   변환한 뒤 내려줍니다.
3. **실패는 조용하지 않게 한다.** 형식이 잘못된 값은 추측하지 않고 비워 두고,
   검증에서 MINOR 로 보고합니다.
'''

# ---------------------------------------------------------------------------
# 01 - 연결 구조
# ---------------------------------------------------------------------------
FILES['01-frontend-backend-connection.md'] = '''# 1. 프론트엔드 - 백엔드 연결

## 1.1 포트와 출처

| 구성 요소 | 포트 | 비고 |
| --- | --- | --- |
| 백엔드 (Spring Boot) | **3000** | Agent(C++ Prober)가 접속하는 포트와 동일해야 함 |
| 프론트엔드 (Vite dev) | **5173** | 개발 서버 |
| 프론트엔드 (Vite preview) | **4173** | 빌드 결과 미리보기 |

두 서버가 다른 포트이므로 브라우저 관점에서 **다른 출처(origin)** 입니다.
따라서 REST 호출에는 CORS 설정이 필요합니다.

> **주의**: WebSocket 은 CORS 의 영향을 받지 않습니다. 그래서 Agent 통신은
> 처음부터 잘 동작했고, REST 를 붙이는 시점에야 CORS 문제가 드러납니다.

## 1.2 CORS 설정 위치

`Config/WebMvcConfig.java`

```java
registry.addMapping("/api/**")
        .allowedOriginPatterns(allowedOriginPatterns)
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
```

허용 출처는 프로퍼티로 주입합니다.

```properties
# application.properties (기본값은 개발 편의용 localhost 계열)
sonar.cors.allowed-origins=http://localhost:5173,http://localhost:4173
```

`allowedOrigins("*")` + `allowCredentials(true)` 조합은 Spring 이 런타임에
거부하므로 `allowedOriginPatterns` 를 씁니다.

## 1.3 API base URL 결정 순서

`lib/api/client.ts`

1. `VITE_API_BASE_URL` 환경변수 (배포 시 주입)
2. 개발 기본값 `http://localhost:3000`

```bash
# 개발 시 백엔드를 다른 포트로 띄운 경우
VITE_API_BASE_URL=http://localhost:3100 npm run dev
```

## 1.4 연결 구조

```mermaid
flowchart LR
    subgraph Browser["브라우저"]
        UI["React 화면<br/>(ProjectEditor, PolicyManagement ...)"]
        APIMOD["lib/api<br/>(client.ts + 도메인 모듈)"]
        UI --> APIMOD
    end

    subgraph Backend["Spring Boot :3000"]
        CORS["WebMvcConfig<br/>(CORS)"]
        CTRL["Controller<br/>Project / Policy / Network"]
        SVC["Service<br/>Project / Compliance / DeviceConfig"]
        BDD["Policy<br/>SegmentationBddEngine"]
        WS["AgentWebSocketHandler"]
        REG["AgentSessionRegistry"]

        CORS --> CTRL
        CTRL --> SVC
        SVC --> BDD
        CTRL --> REG
        WS --> REG
    end

    subgraph Agent["C++ Prober"]
        PROBER["sonar_validator_prober"]
    end

    APIMOD -- "REST + JSON (CORS)" --> CORS
    PROBER -- "WebSocket<br/>/api/v1/telemetry" --> WS
    REG -- "command 푸시" --> PROBER
```

## 1.5 왜 API 계층을 따로 두는가

화면마다 `fetch` 를 직접 쓰면 세 가지 문제가 생깁니다.

1. 오류 응답 처리가 제각각이 됩니다.
2. base URL 이 여러 곳에 하드코딩됩니다.
3. 404 를 "데이터 없음" 으로 조용히 넘기는 실수가 생깁니다.

그래서 `lib/api/client.ts` 에서 규칙을 한 번만 정하고, 도메인 모듈
(`projects.ts`, `index.ts`)이 경로를 감춥니다. 화면 코드에는 URL 문자열이
나타나지 않습니다.

```ts
// 화면 코드
const { data, loading, error, offline } = useApi(() => listProjects(), []);

// 경로와 오류 처리는 lib/api 안에 있음
export function listProjects(): Promise<ApiProjectList> {
  return apiRequest<ApiProjectList>("/api/v1/projects");
}
```

## 1.6 연결 실패 시 화면 동작

백엔드가 꺼져 있으면 화면이 그냥 비어 보입니다. 그래서 `ApiError.isNetworkError`
로 "서버에 닿지 못함" 을 구분하고, 실행 방법까지 안내합니다.

```
백엔드에 연결할 수 없습니다
cd SonarValidator_Backend && ./mvnw spring-boot:run
[다시 시도]
```

## 1.7 환경별 확인 방법

```bash
# 1) CORS 사전 요청(Preflight) 확인
curl -s -D - -o /dev/null -X OPTIONS http://localhost:3000/api/v1/projects \\
  -H 'Origin: http://localhost:5173' \\
  -H 'Access-Control-Request-Method: GET' | grep -i access-control-allow-origin
# 기대: Access-Control-Allow-Origin: http://localhost:5173

# 2) API 응답 확인
curl -s http://localhost:3000/api/v1/projects
```
'''

# ---------------------------------------------------------------------------
# 02 - 프로젝트 편집 시퀀스
# ---------------------------------------------------------------------------
FILES['02-project-editor-sequence.md'] = '''# 2. 프로젝트 편집 시퀀스 다이어그램

## 2.1 프로젝트 생성

```mermaid
sequenceDiagram
    autonumber
    actor User as 운영자
    participant UI as Project.tsx
    participant API as lib/api/projects.ts
    participant PC as ProjectController
    participant PS as ProjectService
    participant Repo as ProjectRepository
    participant DB as (H2 / PostgreSQL)

    User->>UI: Create Project 클릭 + 이름 입력
    UI->>API: createProject({name, category, description})
    API->>PC: POST /api/v1/projects
    PC->>PS: create(request)
    PS->>PS: resolveKey(projectId)<br/>중복이면 PRJ-XXXXXXXX 발급
    PS->>Repo: save(project)
    Repo->>DB: INSERT project
    DB-->>Repo: id
    Repo-->>PS: Project
    PS-->>PC: Project
    PC-->>API: 200 {project_id, name, ...}
    API-->>UI: ApiProject
    UI->>UI: navigate(/project/editor/{project_id})
```

**설계 의도**: 키 발급을 서버가 담당합니다. 프론트엔드가 만든 키는 신뢰할 수
없고(중복 가능), URL 에 그대로 노출되므로 서버가 확정합니다.

## 2.2 정책 편집 → 저장 → 검증 (핵심 흐름)

```mermaid
sequenceDiagram
    autonumber
    actor User as 운영자
    participant UI as ProjectEditor.tsx
    participant SE as SubnetEditor / RuleEditor
    participant Hook as useApiAction
    participant API as lib/api/projects.ts
    participant PC as ProjectController
    participant PS as ProjectService
    participant Eng as SegmentationBddEngine
    participant CS as ComplianceService
    participant Repo as ProjectRepository

    Note over User,SE: 1) 편집 단계 (로컬 상태)
    User->>SE: 서브넷 CIDR / 등급 변경
    SE->>UI: onChange(subnetId, patch)
    UI->>UI: setSubnets(...) + setDirty(true)

    User->>SE: 규칙 SRC/DST/Port 입력
    SE->>SE: isForbiddenPair(src, dst) 로컬 판정
    SE-->>User: 행 강조 + "저장 시 검증에서 오류" 안내

    Note over User,Hook: 2) 저장 단계
    User->>UI: Save & Validate 클릭
    UI->>Hook: run()
    Hook->>API: updateProject(projectId, {subnets, rules})
    API->>PC: PUT /api/v1/projects/{id}
    PC->>PS: update(projectId, request)
    PS->>PS: mapSubnets / mapRules<br/>(등급 null 이면 OPEN)
    PS->>Repo: save(project)<br/>(orphanRemoval 로 기존 목록 교체)
    PS->>CS: recordQuietly("Policy Update", ...)
    CS-->>PS: (실패해도 흡수)
    Repo-->>PS: 저장된 Project
    PS-->>PC: Project
    PC-->>API: 200 {subnet_count, rule_count, ...}
    API-->>Hook: ApiProject

    Note over Hook,Eng: 3) 검증 단계 (저장 성공 후에만)
    Hook->>API: validateProject(projectId)
    API->>PC: GET /api/v1/projects/{id}/validation
    PC->>PS: validateStored(projectId)
    PS->>Eng: validate(subnets, rules)
    Eng->>Eng: 허용집합 = OR(규칙별 대역x대역x포트)
    Eng->>Eng: 금지집합 = OR(등급차 2 이상 쌍)
    Eng->>Eng: 위반집합 = 허용 ∩ 금지
    Eng->>Eng: anySat(위반) → 반례 패킷 추출
    Eng-->>PS: Report (violations + metrics)
    PS-->>PC: Report
    PC-->>API: 200 {compliant, violations, metrics}
    API-->>Hook: ApiValidationReport
    Hook-->>UI: report
    UI->>UI: ViolationSummary 갱신<br/>규칙 행 강조 + 반례 패킷 표시
```

## 2.3 왜 저장 후에 검증하는가

검증은 **서버에 저장된 상태**를 기준으로 돌아갑니다. 이유는 두 가지입니다.

1. 자동 수집된 실제 설정(방화벽 규칙)을 반영해야 합니다.
   로컬 상태만 보면 수집 결과를 놓칩니다.
2. "화면에 보이는 상태 = 검증된 상태" 를 보장합니다.
   저장 전 검증을 허용하면, 사용자가 보고 있는 화면과 검증 대상이
   어긋날 수 있습니다.

저장 없이 미리보기만 필요하면 `POST /api/v1/projects/draft/validation` 을
쓰면 됩니다.

## 2.4 편집 상태 관리 방식

`ProjectEditor` 는 서버 응답을 로컬 `draft` 로 복사해 편집합니다.

```
서버 응답 (data) --useEffect--> 편집 상태 (subnets, rules, name ...)
                                      |
                                      | 사용자 편집
                                      v
                                  dirty = true
                                      |
                                      | 저장
                                      v
                              PUT /api/v1/projects/{id}
```

**목록 전체 교체 방식**을 쓴 이유: 편집기가 화면 상태를 통째로 보내므로
부분 수정 API 가 필요 없고, 계약이 단순해집니다. 서버는
`orphanRemoval = true` 로 기존 행을 정리하고 새로 넣습니다.

## 2.5 자동 수집 초안 흐름

프로젝트가 비어 있으면 서버가 수집 결과로 초안을 만듭니다.

```mermaid
sequenceDiagram
    autonumber
    participant UI as ProjectEditor
    participant PC as ProjectController
    participant PS as ProjectService
    participant PV as ProjectView
    participant AR as AgentMessageRouterService

    UI->>PC: GET /api/v1/projects/{id}
    PC->>PS: getByKey(id)
    PS->>PS: subnets 비어 있음 확인
    PS->>PV: forEditing(project, router)
    PV->>AR: allConfigs()
    AR-->>PV: Agent 별 NeutralDeviceConfig
    PV->>PV: subnetsFromDevice()<br/>인터페이스 주소 -> CIDR 정규화
    PV->>PV: discoveredRules()<br/>방화벽 규칙 -> 비활성 초안
    PV-->>PS: draft=true + draft_note
    PS-->>PC: 편집 뷰
    PC-->>UI: {draft:true, subnets, rules}
```

**중요**: 자동 수집 초안의 등급은 모두 `Open` 이고
`manually_edited=false` 입니다. 또한 수집된 방화벽 규칙은 `enabled=false` 입니다.

주소 대역만 보고 등급을 추측하면 잘못된 경보가 쏟아지고, 그러면 운영자가
경보를 무시하게 됩니다. 등급 결정과 규칙 활성화는 항상 사람이 합니다.

## 2.6 상태별 화면 동작

| 상태 | 화면 표시 |
| --- | --- |
| 로딩 중 | "프로젝트를 불러오는 중..." |
| 연결 실패 | 오류 배너 + 실행 명령 + 다시 시도 |
| 편집 중 | "저장되지 않은 변경" 배지 |
| 자동 수집 초안 | "자동 수집 초안" 배지 + 안내 문구 |
| 검증 통과 | 초록 패널 + "정책 푸시" 활성 |
| 검증 실패 | 빨강 패널 + 심각도 집계 + 반례 패킷 표 + 푸시 비활성 |
'''

# ---------------------------------------------------------------------------
# 03 - 클래스 다이어그램
# ---------------------------------------------------------------------------
FILES['03-project-editor-classes.md'] = '''# 3. 프로젝트 편집 기능 클래스 다이어그램

## 3.1 백엔드 전체 구조

```mermaid
classDiagram
    direction TB

    class ProjectController {
        -ProjectService projectService
        +list() Map
        +get(projectId) Map
        +create(body) Map
        +update(projectId, body) Map
        +delete(projectId) Map
        +validateStored(projectId) Map
        +validate(projectId, body) Map
        +validateDraft(body) Map
        +forbiddenPairs(projectId) Map
    }

    class ProjectService {
        -ProjectRepository repository
        -SegmentationBddEngine engine
        -ComplianceService complianceService
        +listAll() List~Project~
        +getByKey(key) Project
        +create(request) Project
        +update(key, request) Project
        +delete(key) void
        +validateStored(key) Report
        +validate(request) Report
        +forbiddenPairs(subnets) List
        -resolveKey(candidate) String
        -mapSubnets(payloads) List~PolicySubnet~
        -mapRules(payloads) List~PolicyRule~
    }

    class ProjectRepository {
        <<interface>>
        +findByProjectKey(key) Optional~Project~
        +findAllByOrderByCreatedAtDesc() List~Project~
        +existsByProjectKey(key) boolean
    }

    class Project {
        -Long id
        -String projectKey
        -String name
        -String category
        -String description
        -String status
        -String createdAt
        -String updatedAt
        -List~ProjectSubnet~ subnets
        -List~ProjectRule~ rules
        +toPolicySubnets() List~PolicySubnet~
        +toPolicyRules() List~PolicyRule~
        +replacePolicy(subnets, rules) void
    }

    class ProjectSubnet {
        -Long id
        -String subnetId
        -String cidr
        -ZoneClass zoneClass
        -String name
        -String agentId
        -boolean manuallyEdited
        +toPolicySubnet() PolicySubnet
        +from(subnet) ProjectSubnet
    }

    class ProjectRule {
        -Long id
        -String ruleId
        -String source
        -String destination
        -Integer port
        -String protocol
        -Origin origin
        -boolean enabled
        -String note
        +toPolicyRule() PolicyRule
        +from(rule) ProjectRule
    }

    ProjectController --> ProjectService
    ProjectService --> ProjectRepository
    ProjectService --> SegmentationBddEngine
    ProjectService ..> ComplianceService
    ProjectRepository ..> Project
    Project "1" *-- "0..*" ProjectSubnet
    Project "1" *-- "0..*" ProjectRule
```

## 3.2 도메인 vs 영속 표현 (분리 이유)

같은 정보를 두 계층으로 나눠 표현합니다.

| 계층 | 클래스 | 역할 |
| --- | --- | --- |
| 도메인 | `PolicySubnet`, `PolicyRule` | 검증 엔진 입력. JPA 를 전혀 모름 |
| 영속 | `ProjectSubnet`, `ProjectRule` | DB 저장. 등급/출처를 컬럼으로 매핑 |
| 변환 | `Project.toPolicyXxx()` / `Xxx.from()` | 두 표현 사이 변환 |

```mermaid
classDiagram
    direction LR
    class PolicySubnet {
        <<도메인>>
        -String id
        -String cidr
        -ZoneClass zoneClass
        -boolean manuallyEdited
        +normalizeCidr(cidr)$ String
        +cidrIndex(subnets)$ Map
    }
    class ProjectSubnet {
        <<영속>>
        -String subnetId
        -String cidr
        -ZoneClass zoneClass
        -boolean manuallyEdited
    }
    class PolicyRule {
        <<도메인>>
        -String id
        -String source
        -String destination
        -int port
        -Origin origin
        -boolean enabled
        +hasPort() boolean
    }
    class ProjectRule {
        <<영속>>
        -String ruleId
        -String source
        -String destination
        -Integer port
        -Origin origin
        -boolean enabled
    }

    PolicySubnet <--> ProjectSubnet : toPolicySubnet / from
    PolicyRule <--> ProjectRule : toPolicyRule / from
```

이렇게 나누면:
- 검증 로직이 JPA 에 묶이지 않습니다 (`SegmentationBddEngine` 단위 테스트 가능).
- 스키마 변경이 엔진에 영향을 주지 않습니다.
- 포트의 "미지정" 표현 차이를 흡수할 수 있습니다
  (도메인은 `-1`, 영속은 `null`).

## 3.3 DTO 와 매퍼

```mermaid
classDiagram
    direction TB

    class ProjectDto {
        <<final class>>
    }
    class SubnetPayload {
        <<record>>
        +String id
        +String cidr
        +String subnetClass
        +String name
        +String agentId
        +Boolean manuallyEdited
        +toPolicySubnet() PolicySubnet
    }
    class RulePayload {
        <<record>>
        +String id
        +String src
        +String dst
        +Integer port
        +String protocol
        +String origin
        +Boolean enabled
        +String note
        +toPolicyRule() PolicyRule
    }
    class CreateRequest {
        <<record>>
        +String projectId
        +String name
        +String category
        +String description
        +String status
    }
    class UpdateRequest {
        <<record>>
        +String name
        +List~SubnetPayload~ subnets
        +List~RulePayload~ rules
    }
    class ValidateRequest {
        <<record>>
        +List~SubnetPayload~ subnets
        +List~RulePayload~ rules
    }
    class ProjectMapper {
        <<final class>>
        +toResponse(project)$ Map
        +toSummary(project)$ Map
        +toValidationResponse(report)$ Map
    }

    ProjectDto *-- SubnetPayload
    ProjectDto *-- RulePayload
    ProjectDto *-- CreateRequest
    ProjectDto *-- UpdateRequest
    ProjectDto *-- ValidateRequest
```

### 필드 이름을 명시한 이유

`ProjectDto` 는 `record` 라서 **컴포넌트 이름이 곧 JSON 키**입니다.
`subnetClass` 라고 선언하면 프론트엔드가 보내는 `subnet_class` 를 읽지
못합니다.

```java
public record SubnetPayload(
        String id,
        String cidr,
        @JsonProperty("subnet_class") String subnetClass,   // 명시 필요
        String name,
        @JsonProperty("agent_id") String agentId,           // 명시 필요
        @JsonProperty("manually_edited") Boolean manuallyEdited) {
```

**실패가 조용하다는 점이 위험합니다.** 값이 `null` 로 들어오면 서비스가
기본 등급 `Open` 을 채우고, 검증은 "위반 없음" 이라고 잘못 보고합니다.
예외도 경고 로그도 없습니다. 그래서
`ProjectDtoSerializationTest` 가 snake_case 본문을 실제로 파싱해 이
계약을 왕복 검증합니다.

## 3.4 프론트엔드 구조

```mermaid
classDiagram
    direction TB

    class ProjectEditor {
        <<page>>
        -String projectId
        -ApiSubnet[] subnets
        -ApiRule[] rules
        -ApiValidationReport report
        -boolean dirty
        +handleSave() void
        +handleValidate() void
        +handlePush() void
        +updateSubnetClass(id, class) void
        +updateSubnetCidr(id, cidr) void
        +addRule() void
        +removeSubnet(id) void
    }
    class SubnetEditor {
        <<component>>
        +ApiSubnet[] subnets
        +onChange(id, class)
        +onCidrChange(id, cidr)
        +onRemove(id)
        +onAdd()
        +Set violatingSubnetIds
    }
    class RuleEditor {
        <<component>>
        +ApiRule[] rules
        +ApiSubnet[] subnets
        +ApiViolation[] violations
        +onChange(id, patch)
        +onRemove(id)
        +onAdd()
    }
    class ViolationSummary {
        <<component>>
        +ApiValidationReport report
        +onValidate()
        +onSave()
        +onPush()
    }
    class useApi~T~ {
        <<hook>>
        +T data
        +boolean loading
        +String error
        +boolean offline
        +reload() void
    }
    class useApiAction~TArgs,TResult~ {
        <<hook>>
        +run(...args) Promise
        +boolean submitting
        +String error
        +TResult result
    }
    class apiClient {
        <<module>>
        +API_BASE_URL String
        +apiRequest(path, options) Promise
        +ApiError
    }
    class projectsApi {
        <<module>>
        +listProjects()
        +getProject(id)
        +createProject(input)
        +updateProject(id, input)
        +validateProject(id)
        +validateDraft(input)
        +getForbiddenPairs(id)
    }
    class zonesLib {
        <<module>>
        +ZONE_CLASSES
        +zoneInfo(value)
        +isForbiddenPair(src, dst) boolean
        +forbiddenReason(src, dst) String
    }

    ProjectEditor --> SubnetEditor
    ProjectEditor --> RuleEditor
    ProjectEditor --> ViolationSummary
    ProjectEditor --> useApi
    ProjectEditor --> useApiAction
    ProjectEditor --> projectsApi
    SubnetEditor ..> zonesLib
    RuleEditor ..> zonesLib
    useApi ..> apiClient
    useApiAction ..> apiClient
    projectsApi ..> apiClient
```

## 3.5 useApiAction 의 함정 (반드시 지킬 것)

`useApiAction` 은 `action` 을 **ref 에 담아** 호출합니다.

```ts
const actionRef = useRef(action);
actionRef.current = action;      // 매 렌더 최신값 유지
const submittingRef = useRef(false);

const run = useCallback(async (...args) => {
  if (submittingRef.current) return null;
  submittingRef.current = true;
  ...
  const value = await actionRef.current(...args);   // ref 를 통해 호출
  ...
}, []);                          // 의존성 비어 있음
```

만약 `useCallback([submitting])` 로 메모이즈하고 `action(...)` 을 직접
호출하면, 콜백이 **처음 렌더의 action 을 계속 붙잡습니다.** 호출자는 보통
`() => save(subnets, rules)` 처럼 화면 상태를 캡처한 화살표 함수를 넘기므로,
그 상태가 바뀌어도 **오래된 값이 전송**됩니다.

증상이 조용해서 위험합니다. 저장은 성공(200)하고 `updated_at` 도 갱신되지만
**내용은 항상 최초 값** 입니다. 서브넷을 추가해도 서버에는 빈 배열이 저장되어
검증이 "위반 0건" 이라고 잘못 보고합니다. (E2E 검증에서 실제로 발견)

## 3.6 라우팅

| 경로 | 컴포넌트 | 설명 |
| --- | --- | --- |
| `/project` | `Project.tsx` | 목록 + 생성 모달 |
| `/project/editor/:projectId` | `ProjectEditor.tsx` | 편집 + 검증 (신규) |
| `/project/editor?project_id=...` | `ProjectEditor.tsx` | 쿼리스트링 호환 |
| `/project/create/*` | 마법사 4단계 | 기존 경로 유지 |
| `/policy` | `PolicyManagement.tsx` | 위반 현황 + 푸시 |
| `/network` | `NetworkManagement.tsx` | 토폴로지 + 라우팅 |
| `/agent` | `Agent.tsx` | Agent 연결/수집 상태 |
'''

# ---------------------------------------------------------------------------
# 04 - BDD 엔진
# ---------------------------------------------------------------------------
FILES['04-segmentation-bdd.md'] = '''# 4. 망분리 검증 BDD 엔진

## 4.1 문제 정의

망분리 정책은 이렇게 표현됩니다.

> **Confidential 등급 서브넷과 Open 등급 서브넷은 직접 연결할 수 없다.**
> Sensitive 등급을 경유해야 한다.

여기에 연결 규칙 N개가 있습니다. 규칙이 그 정책을 위반하는지 어떻게 판정할까요?

### 순진한 방법의 문제

규칙과 서브넷 쌍을 하나씩 대조하는 방식은 세 가지 문제가 있습니다.

1. **규칙 수 x 서브넷 쌍 수** 로 계산량이 늘어납니다.
2. 규칙이 **부분적으로 겹치면** 판정이 틀리기 쉽습니다.
   (예: `10.10.0.0/16` 규칙이 `10.10.131.0/24` 금지 대역을 포함하는 경우)
3. "왜 위반인지" 를 설명할 **구체적 예시**를 만들기 어렵습니다.

## 4.2 BDD 접근

패킷을 **비트 벡터**로 보고, 규칙을 **집합**으로 표현합니다.

```
변수 배치 (총 80 비트)
+---------------+---------------+----------+
| 출발지 IP 32  | 목적지 IP 32  | 포트 16  |
+---------------+---------------+----------+
 0            31 32          63 64      79
```

비트 순서는 **MSB 먼저**입니다. 이렇게 두면 접두사 조건이 자연스럽게
"앞쪽 비트가 주어진 값과 같다" 로 표현됩니다.

### 집합 연산으로 판정

```
1) 허용 집합 A = OR (출발지 대역 x 목적지 대역 x 포트)     <- 규칙마다
2) 금지 집합 F = OR (Confidential 와 Open 의 모든 교차 곱)  <- 정책에서 생성
3) 위반 집합 V = A ∩ F                                    <- 교집합 한 번
4) V ≠ 공집합 이면 anySat(V) 로 반례 패킷 추출
```

BDD 는 동일 부분 함수를 **공유**하므로 이 계산이 규칙 수에 거의 선형으로
유지됩니다. 주소 공간이 2^80 이어도 실제 노드 수는 규칙 수준입니다.

## 4.3 구체적 예시

서브넷 3개, 규칙 2개인 경우:

| 서브넷 | 대역 | 등급 |
| --- | --- | --- |
| Subnet-0004 | 10.10.131.0/24 | Confidential (3) |
| Subnet-0002 | 10.20.111.0/24 | Sensitive (2) |
| Subnet-0001 | 192.168.0.0/24 | Open (1) |

| 규칙 | 출발 | 도착 | 포트 |
| --- | --- | --- | --- |
| Rule-0001 | Subnet-0001 | Subnet-0002 | 443 |
| Rule-0002 | Subnet-0004 | Subnet-0001 | 443 |

**금지 집합**: 등급 차이가 2 이상인 쌍은
(0004,0001), (0001,0004) 두 개뿐입니다. 따라서

```
F = (10.10.131.0/24 x 192.168.0.0/24)  U  (192.168.0.0/24 x 10.10.131.0/24)
```

**허용 집합**: Rule-0001 은 인접 등급이라 위반 후보가 아니고,
Rule-0002 가 금지 집합과 겹칩니다.

```
A ∩ F  ⊇  (10.10.131.0/24 x 192.168.0.0/24 x {443})
```

**반례 추출**: `anySat` 이 변수 할당을 하나 뽑으면
`10.10.131.0 -> 192.168.0.0:443` 같은 재현 가능한 패킷이 나옵니다.

이것이 BDD 를 쓰는 실질적 이점입니다. "위반 있음" 이 아니라
**"이 패킷이 위반입니다"** 를 알려줍니다.

## 4.4 클래스 구조

```mermaid
classDiagram
    direction TB

    class BddNode {
        <<final>>
        +BddNode FALSE$
        +BddNode TRUE$
        -int id
        -int variable
        -BddNode low
        -BddNode high
        +isTerminal() boolean
        +isTrue() boolean
        +isFalse() boolean
    }

    class BddManager {
        <<final>>
        -List~BddNode~ nodes
        -Map~UniqueKey,BddNode~ uniqueTable
        -Map~Integer,BddNode~ notCache
        -Map~ApplyKey,BddNode~ applyCache
        -int variableCount
        +zero() BddNode
        +one() BddNode
        +variable(index) BddNode
        +not(a) BddNode
        +and(a, b) BddNode
        +or(a, b) BddNode
        +xor(a, b) BddNode
        +orAll(parts) BddNode
        +exists(a, index) BddNode
        +forAll(a, index) BddNode
        +anySat(a) int[]
        +satCount(a) BigInteger
        +nodeCount(a) int
        -mk(index, low, high) BddNode
        -apply(op, a, b) BddNode
    }

    class PacketVariables {
        <<final>>
        +int IP_BITS = 32$
        +int PORT_BITS = 16$
        +int SRC_IP_OFFSET = 0$
        +int DST_IP_OFFSET = 32$
        +int PORT_OFFSET = 64$
        +int TOTAL_BITS = 80$
        +int ANY_PORT = -1$
        +newManager() BddManager$
        +cidr(mgr, cidr, offset) BddNode$
        +port(mgr, port) BddNode$
        +parseCidr(cidr) ParsedCidr$
        +parseIp(ip) int$
        +srcIpOf(assign) String$
        +dstIpOf(assign) String$
        +portOf(assign) int$
    }

    class ZoneClass {
        <<enum>>
        OPEN(1)
        SENSITIVE(2)
        CONFIDENTIAL(3)
        +label() String
        +level() int
        +allowsDirectConnection(s,t)$ boolean
        +forbidsDirectConnection(s,t)$ boolean
    }

    class SegmentationBddEngine {
        -boolean flagMissingPort
        +validate(subnets, rules) Report
        +forbiddenPairs(subnets) List
        -ruleToBdd(mgr, src, dst, rule) BddNode
        -forbiddenSet(mgr, subnets) BddNode
        -collectForbiddenViolations(...) void
        -collectMissingPortViolations(...) void
        -resolve(ref, byId, byCidr) PolicySubnet
    }

    class Report {
        -boolean compliant
        -int ruleCount
        -int subnetCount
        -int violationCount
        -List~PolicyViolation~ violations
        -Set~String~ violatedRuleIds
        -List~String~ messages
        -Map~String,Object~ metrics
    }

    class PolicyViolation {
        <<record>>
        +String ruleId
        +String sourceSubnetId
        +String targetSubnetId
        +ZoneClass sourceZone
        +ZoneClass targetZone
        +String sampledSourceIp
        +String sampledTargetIp
        +int sampledPort
        +String reason
        +Severity severity
        +sampledPacket() String
        +groupByRule(violations)$ Map
    }

    BddManager *-- BddNode
    SegmentationBddEngine --> BddManager
    SegmentationBddEngine --> PacketVariables
    SegmentationBddEngine --> Report
    SegmentationBddEngine ..> ZoneClass
    Report *-- PolicyViolation
    PacketVariables ..> BddManager
```

## 4.5 연산 알고리즘

### apply (이항 연산)

섀넌 전개를 씁니다.

```
f = (x AND f|x=1) OR (NOT x AND f|x=0)
```

두 피연산자의 최상위 변수 `x` 를 기준으로 자식을 나눠 재귀합니다.

```java
private BddNode apply(Op op, BddNode a, BddNode b) {
    if (a.isTerminal() && b.isTerminal()) { ... }   // 즉시 계산
    // 교환 법칙으로 캐시 적중률을 높임 (단말 id 0/1 이 앞으로)
    if (a.id() > b.id()) { swap a, b }
    if (cache has (op, a.id, b.id)) return cached;
    int index = min(topVariable(a), topVariable(b));
    result = mk(index,
                apply(op, cofactor(a, index, false), cofactor(b, index, false)),
                apply(op, cofactor(a, index, true),  cofactor(b, index, true)));
    cache.put(key, result);
    return result;
}
```

### 축약 규칙 (mk)

```java
private BddNode mk(int index, BddNode low, BddNode high) {
    if (low == high) return low;          // 변수에 무관 -> 자식 그대로
    key = (index, low.id, high.id);
    if (uniqueTable has key) return existing;   // 노드 공유
    ...
}
```

이 두 가지(축약 + 유일 테이블)가 BDD 를 압축하는 핵심입니다.

### anySat (반례 추출)

low(0) 를 먼저 시도해 0 을 선호합니다. 만족하는 경로를 따라가며 변수 값을
채우고, 방문하지 않은 변수는 -1(무관)로 남깁니다.

## 4.6 검사하는 두 가지

| 검사 | 방법 | 심각도 |
| --- | --- | --- |
| 등급을 건너뛰는 직접 연결 | `A ∩ F ≠ 공집합` | CRITICAL |
| 허용 포트 미지정 | 규칙 순회 (`port == ANY_PORT`) | MAJOR |
| 존재하지 않는 서브넷 참조 | 규칙 순회 | MINOR |

두 번째 검사를 별도로 두는 이유: 금지 대역을 건드리지는 않지만 정책을
약화시키기 때문입니다. `port` 가 비어 있으면 전체 포트가 열린 것으로
해석되므로 `MAJOR` 로 보고합니다.

## 4.7 성능 특성

실측 지표 (응답의 `metrics` 필드):

| 지표 | 의미 |
| --- | --- |
| `bdd_variables` | 변수 개수 (항상 80) |
| `bdd_nodes_allowed` | 허용 집합의 노드 수 |
| `bdd_nodes_forbidden` | 금지 집합의 노드 수 |
| `bdd_nodes_violating` | 위반 집합의 노드 수 |
| `allowed_combinations` | 허용 집합의 원소 수 (BigInteger) |
| `address_space` | 2^80 |

노드 수가 규칙 수에 비해 작으면 중복 함수가 잘 공유되었다는 뜻입니다.
운영자는 이 지표로 "검증이 실제로 집합 연산으로 돌았다" 를 확인할 수 있습니다.

> **주의**: `satCount` 는 매니저가 **아는 변수 개수** 를 기준으로 셉니다.
> 그래서 `PacketVariables.newManager()` 가 변수 80개를 미리 등록합니다.
> 등록하지 않으면 방문한 변수만 세어 "허용 조합 수" 비교가 어긋납니다.

## 4.8 테스트 전략

| 테스트 | 검증 내용 |
| --- | --- |
| `BddManagerTest` | BDD 자체의 수학적 정확성 |
| `SegmentationBddEngineTest` | 정책 판정 정확성 (랩 실제 대역 사용) |

`BddManagerTest` 가 확인하는 법칙:

- 드모르간: `NOT(a AND b) == NOT a OR NOT b`
- 분배: `a AND (b OR c) == (a AND b) OR (a AND c)`
- 존재 양화: `EXISTS x. (x AND y) == y`
- 노드 공유: 같은 함수는 같은 객체(`isSameAs`)
- 접두사 크기: `/24` 는 2^8 개 주소를 포함
- 접두사 밖 비트는 제약하지 않음

`SegmentationBddEngineTest` 가 확인하는 시나리오:

- 인접 등급은 허용
- Confidential -> Open 은 CRITICAL
- 반례 패킷이 위반 대역 안에서 추출됨
- 등급 차이 2 이상 쌍이 `forbiddenPairs` 에 나옴
- 포트 미지정은 MAJOR
- 비활성 규칙은 검증 제외
- CIDR 문자열 직접 참조도 해석
- BDD 노드 수가 규칙 수준에 머묾
'''

# ---------------------------------------------------------------------------
# 05 - 정책 검증 시퀀스
# ---------------------------------------------------------------------------
FILES['05-policy-validation-sequence.md'] = '''# 5. 정책 위반 조회 / 푸시 시퀀스

## 5.1 위반 현황 조회

```mermaid
sequenceDiagram
    autonumber
    actor User as 운영자
    participant UI as PolicyManagement.tsx
    participant API as lib/api/index.ts
    participant PC as PolicyManagement
    participant PS as ProjectService
    participant Eng as SegmentationBddEngine
    participant Repo as ProjectRepository

    User->>UI: /policy 접속
    UI->>API: listProjects()
    API-->>UI: 프로젝트 목록
    UI->>UI: 첫 프로젝트 기본 선택

    UI->>API: getPolicyViolations(projectId)
    API->>PC: GET /api/v1/policy/violations/{projectId}
    PC->>PS: getByKey(projectId)
    PS->>Repo: findByProjectKey
    Repo-->>PS: Project
    PC->>PS: validateStored(projectId)
    PS->>Eng: validate(subnets, rules)
    Eng-->>PS: Report

    PC->>PC: 심각도별 집계 (CRITICAL/MAJOR/MINOR)
    PC->>PC: 규칙별 묶음 (by_rule)
    PC-->>API: {compliant, by_severity, violations, by_rule, metrics}
    API-->>UI: ApiPolicyViolations
    UI->>User: 심각도 카드 + 위반 표 + 반례 패킷

    Note over UI: compliant=false 이면<br/>"정책 푸시" 버튼 자동 비활성
```

## 5.2 정책 푸시 (검증 통과 시에만)

```mermaid
sequenceDiagram
    autonumber
    actor User as 운영자
    participant UI as PolicyManagement.tsx
    participant API as lib/api/index.ts
    participant PC as PolicyManagement
    participant PS as ProjectService
    participant Eng as SegmentationBddEngine
    participant Reg as AgentSessionRegistry
    participant Ag as C++ Prober

    User->>UI: 정책 푸시 클릭
    UI->>API: pushPolicy(projectId, force=false)
    API->>PC: POST /api/v1/policy/push/{projectId}?force=false

    PC->>PS: validateStored(projectId)
    PS->>Eng: validate(subnets, rules)
    Eng-->>PS: Report

    alt 위반이 있고 force=false
        PC-->>API: {pushed:false, reason, violated_rule_ids}
        API-->>UI: 결과
        UI->>User: 전송 거부 + 위반 규칙 안내
    else 검증 통과 또는 force=true
        loop 서브넷마다 (agent_id 가 있는 것)
            PC->>PC: payload 조립<br/>(project_id, subnet_id, cidr, rules[])
            PC->>Reg: sendTo(agentId, command 봉투)
            Reg->>Ag: WebSocket text frame
            Ag-->>Reg: (선택) ack 봉투
            Reg-->>PC: delivered
        end
        PC-->>API: {pushed:true, delivered, targets, deliveries}
        API-->>UI: 결과
        UI->>User: 전송 결과 표시
    end
```

### 왜 위반 시 푸시를 거부하는가

위반 정책을 장치에 밀어 넣으면 **실제로 망분리가 깨집니다.** 그래서
기본 동작이 거부이고, `force=true` 로만 우회할 수 있습니다. 우회 시에도
`WARN` 로그가 남아 감사 추적이 가능합니다.

## 5.3 정책 봉투 구조

푸시되는 `command` 봉투의 payload:

```json
{
  "project_id": "PRJ-6067C1F4",
  "subnet_id": "Subnet-0004",
  "cidr": "10.10.131.0/24",
  "subnet_class": "Confidential",
  "rules": [
    {
      "rule_id": "Rule-0003",
      "destination": "Subnet-0002",
      "protocol": "tcp",
      "port": 443
    }
  ]
}
```

- `enabled=false` 규칙은 포함되지 않습니다.
- `port` 가 미지정이면 키 자체를 넣지 않습니다 (전체 포트 허용 의미).
- `agent_id` 가 없는 서브넷은 건너뜁니다 (푸시할 대상이 없음).

## 5.4 변경 이력 기록

정책을 저장할 때마다 이력이 남습니다.

```mermaid
sequenceDiagram
    autonumber
    participant PS as ProjectService
    participant CS as ComplianceService
    participant Repo as ComplianceChangeRepository
    participant UI as Compliance 화면

    PS->>CS: recordQuietly("Project", key, null, "Policy Update", summary, "system", null)
    CS->>CS: changeId 발급 (CHG-XXXXXXXX)
    CS->>Repo: save(change)
    alt 저장 실패
        CS-->>PS: 예외 흡수 + WARN 로그
    else 성공
        Repo-->>CS: ComplianceChange
        CS-->>PS: (void)
    end

    UI->>Repo: GET /api/v1/compliance/changes?project_id=...
    Repo-->>UI: 최신순 이력
```

**기록 실패가 본 작업을 막지 않습니다.** 감사 로그 때문에 정책 적용이
실패하는 것이 더 나쁘기 때문입니다. 그래서 `recordQuietly` 는 예외를
흡수하고 경고만 남깁니다.

## 5.5 토폴로지 조회

```mermaid
sequenceDiagram
    autonumber
    participant UI as NetworkManagement.tsx
    participant NC as NetworkTopologyController
    participant PS as ProjectService
    participant Zone as ZoneClass

    UI->>NC: GET /api/v1/network/topology/{projectId}
    NC->>PS: getByKey(projectId)
    PS-->>NC: Project

    loop 서브넷마다
        NC->>NC: 노드 생성 (id, label, cidr, class, level)
    end
    loop 활성 규칙마다
        NC->>Zone: forbidsDirectConnection(srcClass, dstClass)
        Zone-->>NC: boolean
        NC->>NC: 간선 생성 (forbidden, severity)
    end

    NC-->>UI: {nodes, edges, legend}
    UI->>UI: buildMermaidChart()
    Note over UI: 등급별 subgraph +<br/>금지 간선은 굵은 빨간 화살표
```

### 노드 모양으로 등급을 구분하는 이유

색만 다르면 흑백 인쇄나 색각 이상에서 구분이 어렵습니다. 등급별로 모양도
다르게 해서 색 없이도 읽히게 했습니다.

| 등급 | 노드 모양 |
| --- | --- |
| Confidential | `{{"..."}}` 육각형 |
| Sensitive | `(["..."])` 스타디움 |
| Open | `["..."]` 사각형 |
'''

# ---------------------------------------------------------------------------
# 06 - Batfish 비교
# ---------------------------------------------------------------------------
FILES['06-bdd-vs-batfish.md'] = '''# 6. Batfish 와의 비교 및 설계 근거

## 6.1 Batfish 란

Batfish 는 네트워크 설정을 정적으로 분석하는 도구입니다. 핵심 아이디어는
**벤더별 설정을 공통 구조로 변환한 뒤, 분석은 벤더를 모른 채 수행**하는
것입니다.

```
Cisco IOS  ---+
Arista EOS ---+--> [파서] --> Configuration --> [분석 엔진]
Junos      ---+                (공통 구조)         |
                                                    v
                                          BDD 기반 도달성 분석
```

이 프로젝트는 같은 설계를 따릅니다.

| Batfish | 이 프로젝트 | 역할 |
| --- | --- | --- |
| `Configuration` | `NeutralDeviceConfig` | 벤더 중립 장비 설정 |
| 벤더별 파서 | `CiscoRouterConfigParser`, `FrrRouterConfigParser` 등 6종 | CLI 출력 -> 중립 구조 |
| 심볼릭 패킷 집합 | `BddManager` + `PacketVariables` | 패킷 집합의 BDD 표현 |
| 도달성 분석 | `SegmentationBddEngine` | 망분리 위반 판정 |
| 반례 경로 | `PolicyViolation.sampledPacket()` | 재현 가능한 예시 패킷 |

## 6.2 규모에 맞게 축소한 부분

Batfish 는 범용 도구라 다음을 다룹니다. 이 프로젝트는 필요한 만큼만
구현했습니다.

| 항목 | Batfish | 이 프로젝트 | 이유 |
| --- | --- | --- | --- |
| 프로토콜 | IPv4/IPv6, TCP/UDP/ICMP, ARP, BGP/OSPF | IPv4 + 포트 | 망분리 검증에 필요한 최소 집합 |
| 변수 수 | 가변 (패킷 필드 전체) | 80 비트 고정 | 예측 가능하고 캐시 효율이 높음 |
| 정책 표현 | ACL, 라우팅 정책, NAT 등 | 등급 기반 연결 규칙 | 도메인 요구사항이 단순함 |
| 데이터 평면 | 시뮬레이션 가능 | 정적 집합 연산만 | 실시간 검증이 목적이 아님 |
| 대칭성 | 양방향 자동 처리 | 금지 쌍을 양방향 생성 | 코드가 단순해짐 |

## 6.3 변수 배치 설계

```
+---------------+---------------+----------+
| 출발지 IP 32  | 목적지 IP 32  | 포트 16  |
+---------------+---------------+----------+
 0            31 32          63 64      79
```

**MSB 먼저** 배치한 이유: CIDR 접두사가 "앞쪽 N개 비트 고정" 으로 자연스럽게
표현됩니다. LSB 먼저면 접두사 길이마다 비트 위치 계산이 달라져 실수가
생깁니다.

```java
public static BddNode cidr(BddManager manager, String cidr, int offset) {
    ParsedCidr parsed = parseCidr(cidr);
    BddNode result = manager.one();
    for (int bit = 0; bit < parsed.prefixLength(); bit++) {
        int variable = offset + bit;
        boolean value = ((parsed.address() >>> (IP_BITS - 1 - bit)) & 1) != 0;
        result = manager.and(result, value
                ? manager.variable(variable)
                : manager.not(manager.variable(variable)));
    }
    return result;   // 하위 비트는 자유 -> 자동으로 대역 전체 포함
}
```

`0.0.0.0/0` 은 반복이 0회라 `manager.one()` 이 됩니다. 즉 "전체 집합" 을
별도로 특수 처리할 필요가 없습니다.

## 6.4 Java 에서 BDD 를 직접 구현한 이유

외부 BDD 라이브러리(예: JavaBDD/JDD)를 쓰지 않고 직접 구현했습니다.

| 이유 | 설명 |
| --- | --- |
| 의존성 최소화 | Spring Boot + ANTLR 만으로 구성. 네이티브 라이브러리 불필요 |
| 변수 수 고정 | 80 변수 고정이라 범용 라이브러리의 유연성이 필요 없음 |
| 디버깅 용이성 | 노드 수/구조를 직접 노출해 `metrics` 로 보여줄 수 있음 |
| 학습/설명 목적 | 구현이 문서와 1:1 로 대응되어 설명이 명확함 |
| 스레드 안전성 제어 | 매니저를 요청 단위로 새로 만들어 동기화 문제 제거 |

구현 분량은 약 500줄입니다 (`BddNode` + `BddManager` + `PacketVariables`).

## 6.5 등급 기반 정책 표현의 이점

정책을 "금지 목록" 이 아니라 **"등급 + 인접 규칙"** 으로 표현합니다.

```java
public static boolean allowsDirectConnection(ZoneClass source, ZoneClass target) {
    if (source == null || target == null) {
        return true;      // 등급을 모르면 판정하지 않음
    }
    return Math.abs(source.level - target.level) <= 1;
}
```

이 표현의 이점:

1. **새 등급 추가가 쉽습니다.** `ZoneClass` 에 상수를 넣기만 하면 되고,
   위반 판정 코드를 고칠 필요가 없습니다.
2. **정책 변경이 한 곳입니다.** 등급 레벨만 바꾸면 전체 판정이 바뀝니다.
3. **설명이 자연스럽습니다.** "한 단계를 건너뛰면 위반" 이 사람이 읽는
   규칙과 코드가 일치합니다.

`forbidsDirectConnection(null, ...)` 이 `false` 를 돌려주는 것도 의도입니다.
등급을 모르는 상태에서 위반이라고 단정하면 **오탐**이 쏟아지고, 그러면
운영자가 경보를 무시하게 됩니다.

## 6.6 반례 추출이 주는 실무 가치

일반적인 검증 도구는 "위반 N건" 만 알려줍니다. 그러면 운영자는 어느 규칙을
어떻게 고쳐야 할지 알기 어렵습니다.

BDD 는 위반 집합에서 **만족 할당**을 뽑을 수 있으므로 구체적 패킷을
제시합니다.

```
[CRITICAL] Confidential <-> Open 직접 연결은 허용되지 않습니다.
위반 예시 패킷: 10.10.131.0 -> 192.168.0.0:443
```

이 패킷은 "이 주소에서 이 주소로 이 포트로 가는 트래픽이 허용 규칙에
포함되어 있다" 는 사실을 그대로 보여줍니다. 검증 결과에 대한 신뢰가
높아지고, 조치가 명확해집니다.

## 6.7 적합성 판단: BDD 가 과한가

BDD 는 만능이 아닙니다. 이 프로젝트에 적합한 이유를 정리하면:

| 조건 | 이 프로젝트 |
| --- | --- |
| 조건이 비트 벡터로 표현되는가 | 예 (IPv4 주소 + 포트) |
| 집합 연산(합/교/여집합)이 필요한가 | 예 (허용 규칙 합집합 vs 금지 집합) |
| 반례가 필요한가 | 예 (운영자가 조치하려면 필요) |
| 조건 수가 많은가 | 예 (규칙 수 x 서브넷 수) |

반대로 이런 경우에는 BDD 가 과합니다.

- 조건이 단순 비교 몇 개뿐일 때 (단순 `if` 로 충분)
- 순서가 중요할 때 (ACL 은 첫 일치 규칙이 적용되므로 BDD 만으로는 부족함)

### ACL 순서 문제

실제 방화벽 ACL 은 **첫 일치 규칙**이 적용됩니다. 이 프로젝트의 연결
규칙은 "허용 목록" 의미이므로 합집합으로 충분하지만, ACL 을 그대로
검증하려면 순서를 반영해야 합니다.

현재 구현은 ACL 순서를 다루지 않습니다. 수집된 방화벽 규칙은
`enabled=false` 초안으로만 제시하고, 검증 대상은 사람이 확인한 연결
규칙입니다. 이 경계를 명확히 둔 것이 오탐을 막는 핵심입니다.

## 6.8 확장 지점

| 확장 | 방법 |
| --- | --- |
| IPv6 지원 | `PacketVariables` 에 128비트 주소 변수 추가 (변수 96개 증가) |
| 프로토콜 구분 | 변수 8개 추가 (tcp/udp/icmp/...) 후 `port()` 에 조건 추가 |
| ACL 순서 반영 | 규칙에 우선순위를 두고, 앞선 규칙의 집합을 이후 규칙에서 차감 |
| 경로 기반 도달성 | `NeutralDeviceConfig.getRoutes()` 를 홉 단위로 연결해 전이 폐쇄 계산 |
'''

# ---------------------------------------------------------------------------
# 쓰기 + 검증
# ---------------------------------------------------------------------------
for name, text in FILES.items():
    path = os.path.join(OUT, name)
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(text)
    # 손상 검사
    bad = '\ufffd' in text
    spaced = re.findall(r'[\uac00-\ud7a3][ \t]{2,}[\uac00-\ud7a3]', text)
    print('WROTE %-45s %6d bytes  U+FFFD=%s  spaced=%d' % (
        name, len(text.encode('utf-8')), bad, len(spaced)))
    if bad or spaced:
        print('   !! CORRUPTION DETECTED')

# mermaid 블록 개수 요약
total_mermaid = 0
for name, text in FILES.items():
    count = text.count('```mermaid')
    total_mermaid += count
    print('mermaid blocks in %-45s %d' % (name, count))
print('TOTAL mermaid blocks:', total_mermaid)
