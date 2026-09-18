# 5. 정책 위반 조회 / 푸시 시퀀스

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
