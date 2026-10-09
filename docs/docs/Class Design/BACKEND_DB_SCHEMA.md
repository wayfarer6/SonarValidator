# Backend (SonarValidator_Backend) DB 스키마

`SonarValidator_Backend` (Spring Boot + JPA)의 **관계형 DB 스키마** 문서입니다.
모든 내용은 실제 소스(`Model/entity/*.java`, `Model/Configuration.java`, `application-*.yml`)를 기준으로 작성했습니다.

- **DBMS**
  - 운영/통합 테스트: **PostgreSQL** (`application-postgres.yml`, `ddl-auto=validate`)
  - 로컬 개발: **H2 파일 DB** (`application-local.yml`, `ddl-auto=update`)
- **스키마 생성 주체**: JPA/Hibernate 엔티티 (`@Entity`) — 별도 마이그레이션 도구(Flyway/Liquibase)와 `.sql` 파일은 저장소에 없습니다.
- **패키지**: `org.sonar.sonarvalidator_backend.Model.entity` (+ `Model.Configuration`)

> ⚠️ **운영 배포**: `SPRING_PROFILES_ACTIVE=postgres` 를 반드시 설정하세요. 빠뜨리면 H2 파일 DB를 사용합니다.
> `application.properties` 의 기본 프로필은 `local`(H2)입니다.

---

## 1. 테이블 목록 (17개 엔티티 → 17개 테이블)

| #  | 엔티티                      | 테이블                   | 성격                 |
| -- | --------------------------- | ------------------------ | -------------------- |
| 1  | `Configuration`           | `configuration`        | 수집 장치 노드(허브) |
| 2  | `Project`                 | `project`              | 프로젝트             |
| 3  | `ProjectSubnet`           | `project_subnet`       | 프로젝트 서브넷      |
| 4  | `ProjectRule`             | `project_rule`         | 프로젝트 연결 규칙   |
| 5  | `ExpectedAgent`           | `expected_agent`       | 등록 기대 에이전트   |
| 6  | `QuarantineState`         | `quarantine_state`     | 격리 이력            |
| 7  | `Notification`            | `notification`         | 알림 이력            |
| 8  | `DeviceLog`               | `device_log`           | 수집 장치 로그       |
| 9  | `LogAnalysis`             | `log_analysis`         | AI 로그 분석         |
| 10 | `PolicyAdvice`            | `policy_advice`        | AI 정책 어드바이스   |
| 11 | `ComplianceChange`        | `compliance_change`    | 컴플라이언스 변경    |
| 12 | `User`                    | `"USER"`               | 사용자               |
| 13 | `AiProvider`              | `ai_provider`          | AI 공급자            |
| 14 | `OPNsenseCredential`      | `opnsense_credential`  | OPNsense 자격 증명   |
| 15 | `OPNSenseFirewall`        | `opnsense_firewall`    | OPNsense 방화벽 노드 |
| 16 | `InterfaceEntity`         | `network_interface`    | 장치 인터페이스      |
| 17 | `RestAPIConnectionConfig` | `rest_api_node_config` | REST API 노드 설정   |

> `Configuration` 은 `configuration` 테이블(노드 허브)입니다. `device_log`/`network_interface`/`opnsense_firewall`/`opnsense_credential`/`rest_api_node_config`는 FK로 노드를 참조하지만, `quarantine_state.node_id`는 노드 삭제 후에도 감사 이력을 보존하기 위해 FK를 두지 않습니다.
> `User` 는 `"USER"` (PostgreSQL 예약어 회피를 위해 **큰따옴표**로 감싼 테이블명).

---

## 2. ER 다이어그램

```plantuml
@startuml Backend_DB_ER
title Backend DB ER

hide circle
skinparam linetype ortho

entity "USER" as app_user {
  * id : BIGINT <<PK IDENTITY>>
  --
  * username : VARCHAR(120) <<UNIQUE>>
  * password_hash : VARCHAR(120)
  display_name : VARCHAR(120)
  * role : VARCHAR(20)  ' ADMIN|OPERATOR|VIEWER
  * enabled : BOOLEAN
  last_login_at : TIMESTAMP
  created_at : TIMESTAMP
}

entity "project" as project {
  * id : BIGINT <<PK IDENTITY>>
  --
  * project_key : VARCHAR(120) <<UNIQUE>>
  * name : VARCHAR(200)
  category : VARCHAR(100)
  description : VARCHAR(1000)
  status : VARCHAR(50)
  user_id : BIGINT <<FK -> USER, NULLABLE>>
  management_prefix : VARCHAR(255)
  created_at : TIMESTAMP
  updated_at : TIMESTAMP
}

entity "project_subnet" as project_subnet {
  * id : BIGINT <<PK IDENTITY>>
  --
  project_id : BIGINT <<FK -> project>>
  ordinal : INTEGER  ' @OrderColumn
  * subnet_id : VARCHAR(80)
  * cidr : VARCHAR(80)
  zone_class : VARCHAR(30)  ' OPEN|SENSITIVE|CONFIDENTIAL
  name : VARCHAR(200)
  agent_id : VARCHAR(120)
  manually_edited : BOOLEAN
}

entity "project_rule" as project_rule {
  * id : BIGINT <<PK IDENTITY>>
  --
  project_id : BIGINT <<FK -> project>>
  * rule_id : VARCHAR(80)
  source_subnet_id : VARCHAR(80)
  destination_subnet_id : VARCHAR(80)
  port_number : INTEGER
  protocol : VARCHAR(20)
  origin : VARCHAR(30)  ' MANUAL|DISCOVERED
  * enabled : BOOLEAN
  note : VARCHAR(500)
}

entity "configuration" as configuration {
  * node_id : INTEGER <<PK IDENTITY>>
  --
  _hostname : VARCHAR(255)  ' 기본 네이밍
  agent_id : VARCHAR(120) <<UNIQUE>>
  _device_type : VARCHAR(255)  ' SWITCH|ROUTER|FIREWALL|VM
  _configuration_format : VARCHAR(255)  ' CISCO_IOS|ARISTA_vEOS|OPNSense|AlpineFirewall|OpenvSwitch
}

entity "expected_agent" as expected_agent {
  * id : BIGINT <<PK IDENTITY>>
  --
  * agent_id : VARCHAR(120) <<UNIQUE>>
  project_key : VARCHAR(120)
  device_type : VARCHAR(30)
  node_type : VARCHAR(30)
  expected_ip : VARCHAR(60)
  status : VARCHAR(20)
  note : VARCHAR(500)
  created_at : TIMESTAMP
  updated_at : TIMESTAMP
}

entity "quarantine_state" as quarantine_state {
  * id : BIGINT <<PK IDENTITY>>
  --
  agent_id : VARCHAR(120)
  node_id : INTEGER  ' 감사 이력 보존을 위해 FK 없음
  scope : VARCHAR(20)  ' NODE|CONNECTION
  target_cidr : VARCHAR(80)
  project_key : VARCHAR(120)
  requested_by : VARCHAR(120)
  reason : VARCHAR(500)
  * command_delivered : BOOLEAN
  * quarantined_at : TIMESTAMP
  released_at : TIMESTAMP
  released_by : VARCHAR(120)
}

entity "notification" as notification {
  * id : BIGINT <<PK IDENTITY>>
  --
  * notification_id : VARCHAR(80) <<UNIQUE>>
  * category : VARCHAR(40)
  * severity : VARCHAR(20)
  * title : VARCHAR(300)
  message : VARCHAR(2000)
  project_key : VARCHAR(120)
  project_id : BIGINT <<FK -> project>>
  agent_id : VARCHAR(120)
  node_id : INTEGER
  source : VARCHAR(200)
  * occurred_at : TIMESTAMP
  * is_read : BOOLEAN
  link : VARCHAR(300)
  dedupe_key : VARCHAR(200)
  * repeat_count : INTEGER
}

entity "device_log" as device_log {
  * id : BIGINT <<PK IDENTITY>>
  --
  node_id : INTEGER <<FK -> configuration>>
  * agent_id : VARCHAR(120)
  project_key : VARCHAR(120)
  product : VARCHAR(120)
  * logged_at : TIMESTAMP
  collected_at : TIMESTAMP
  * severity_num : INTEGER
  * severity : VARCHAR(20)
  facility : VARCHAR(60)
  message_id : VARCHAR(120)
  message : VARCHAR(8000)
  raw : VARCHAR(8000)
  source : VARCHAR(20)
  fingerprint : VARCHAR(64)
  repeat_count : INTEGER
  highlighted : BOOLEAN
  note : VARCHAR(1000)
}

entity "log_analysis" as log_analysis {
  * id : BIGINT <<PK IDENTITY>>
  --
  * analysis_id : VARCHAR(60) <<UNIQUE>>
  log_id : BIGINT
  project_key : VARCHAR(120)
  project_id : BIGINT <<FK -> project>>
  agent_id : VARCHAR(120)
  scope : VARCHAR(20)
  severity_filter : VARCHAR(20)
  period_from : TIMESTAMP
  period_to : TIMESTAMP
  filter_json : VARCHAR(4000)
  log_ids_json : VARCHAR(4000)
  included_log_count : INTEGER
  total_log_count : INTEGER
  provider_name : VARCHAR(120)
  model : VARCHAR(200)
  elapsed_ms : BIGINT
  risk_level : VARCHAR(20)
  summary : VARCHAR(4000)
  root_cause : VARCHAR(8000)
  recommendations_json : VARCHAR(8000)
  raw_response : VARCHAR(30000)
  requested_by : VARCHAR(200)
  error_message : VARCHAR(4000)
  succeeded : BOOLEAN
  * created_at : TIMESTAMP
}

entity "policy_advice" as policy_advice {
  * id : BIGINT <<PK IDENTITY>>
  --
  * advice_id : VARCHAR(60) <<UNIQUE>>
  * project_key : VARCHAR(120)
  project_name : VARCHAR(200)
  rule_id : VARCHAR(120)
  scope : VARCHAR(20)
  violation_count : INTEGER
  compliant : BOOLEAN
  included_violation_count : INTEGER
  truncated : BOOLEAN
  provider_name : VARCHAR(120)
  model : VARCHAR(200)
  elapsed_ms : BIGINT
  risk_level : VARCHAR(20)
  summary : VARCHAR(4000)
  root_cause : VARCHAR(8000)
  policy_advice : VARCHAR(4000)
  options_json : VARCHAR(8000)
  evidence_json : VARCHAR(4000)
  needs_more_data : BOOLEAN
  raw_response : VARCHAR(30000)
  structured : BOOLEAN
  requested_by : VARCHAR(200)
  error_message : VARCHAR(4000)
  succeeded : BOOLEAN
  * created_at : TIMESTAMP
}

entity "compliance_change" as compliance_change {
  * id : BIGINT <<PK IDENTITY>>
  --
  * change_id : VARCHAR(80) <<UNIQUE>>
  scope : VARCHAR(20)
  project_key : VARCHAR(120)
  agent_id : VARCHAR(120)
  change_type : VARCHAR(60)
  summary : VARCHAR(1000)
  changed_by : VARCHAR(200)
  timestamp : TIMESTAMP
  status : VARCHAR(20)
  detail : VARCHAR(20000)
}

entity "ai_provider" as ai_provider {
  * id : BIGINT <<PK IDENTITY>>
  --
  * name : VARCHAR(120)
  * base_url : VARCHAR(500)
  api_key_encrypted : VARCHAR(2000)
  * model : VARCHAR(200)
  auth_style : VARCHAR(20)
  system_prompt : VARCHAR(4000)
  timeout_seconds : INTEGER
  max_tokens : INTEGER
  temperature : DOUBLE
  allow_insecure_tls : BOOLEAN
  is_default : BOOLEAN
  enabled : BOOLEAN
  last_status : VARCHAR(40)
  last_message : VARCHAR(1000)
  last_checked_at : TIMESTAMP
  created_at : TIMESTAMP
  updated_at : TIMESTAMP
}

entity "opnsense_credential" as opnsense_credential {
  * id : BIGINT <<PK IDENTITY>>
  --
  * node_id : INTEGER <<FK -> configuration.node_id, UNIQUE>>
  display_name : VARCHAR(128)
  * base_url : VARCHAR(255)
  api_key : VARCHAR(255)
  secret_encrypted : VARCHAR(1024)
  * allow_insecure_tls : BOOLEAN
  status : VARCHAR(20)  ' UNVERIFIED|OK|FAILED
  last_checked_at : TIMESTAMP
  last_error : VARCHAR(500)
  detected_version : VARCHAR(64)
  created_at : TIMESTAMP
  updated_at : TIMESTAMP
}

entity "opnsense_firewall" as opnsense_firewall {
  * node_id : INTEGER <<PK/FK -> configuration>>
  --
  name : VARCHAR(128)
  management_ip : VARCHAR(64)
  version : VARCHAR(64)
}

entity "network_interface" as network_interface {
  * interface_id : BIGINT <<PK IDENTITY>>
  --
  node_id : INTEGER <<FK -> configuration>>
  * member_name : VARCHAR(50)
}

entity "rest_api_node_config" as rest_api_node_config {
  * id : BIGINT <<PK IDENTITY>>
  --
  node_id : INTEGER <<FK -> configuration, UNIQUE, NULLABLE>>
  apikey : VARCHAR(255)
  baseurl : VARCHAR(255)
}

app_user o|--o{ project : "optional owner(user_id), @ManyToOne"
project ||--o{ project_subnet : "project_id"
project ||--o{ project_rule : "project_id"
project ||--o{ notification : "project_id"
project ||--o{ log_analysis : "project_id"
configuration ||--o{ device_log : "node_id"
configuration ||--o{ network_interface : "node_id"
configuration ||--o| opnsense_firewall : "node_id"
configuration ||--o| opnsense_credential : "node_id"
configuration ||--o| rest_api_node_config : "node_id"
@enduml
```

---

## 3. 테이블 상세

### 3.1 `"USER"` — 사용자

| 컬럼              | 타입         | 제약                      | 설명                                                  |
| ----------------- | ------------ | ------------------------- | ----------------------------------------------------- |
| `id`            | BIGINT       | PK, IDENTITY              |                                                       |
| `username`      | VARCHAR(120) | NOT NULL,**UNIQUE** | 로그인 ID                                             |
| `password_hash` | VARCHAR(120) | NOT NULL                  | 해시된 비밀번호                                       |
| `display_name`  | VARCHAR(120) |                           | 표시 이름                                             |
| `role`          | VARCHAR(20)  | NOT NULL                  | `ADMIN` / `OPERATOR` / `VIEWER` (기본 OPERATOR) |
| `enabled`       | BOOLEAN      | NOT NULL                  | 활성 여부 (기본 true)                                 |
| `last_login_at` | TIMESTAMP    |                           | 마지막 로그인                                         |
| `created_at`    | TIMESTAMP    |                           | 생성 시각                                             |

> 테이블명이 `"USER"`(예약어 회피용 큰따옴표)입니다. `@Table(name = "\"USER\"")`.

### 3.2 `project` — 프로젝트

| 컬럼                            | 타입          | 제약                      | 설명                         |
| ------------------------------- | ------------- | ------------------------- | ---------------------------- |
| `id`                          | BIGINT        | PK, IDENTITY              |                              |
| `project_key`                 | VARCHAR(120)  | NOT NULL,**UNIQUE** | 프로젝트 식별 키             |
| `name`                        | VARCHAR(200)  | NOT NULL                  | 이름                         |
| `category`                    | VARCHAR(100)  |                           | 분류                         |
| `description`                 | VARCHAR(1000) |                           | 설명                         |
| `status`                      | VARCHAR(50)   |                           | 기본`"Planning"`           |
| `user_id`                     | BIGINT        | FK →`"USER".id`        | 소유자 (`@ManyToOne LAZY`) |
| `management_prefix`           | VARCHAR(255)  |                           | 관리망 대역                  |
| `created_at` / `updated_at` | TIMESTAMP     |                           |                              |

관계: `subnets`(`@OneToMany`, EAGER, `@OrderColumn(name="ordinal")`), `rules`(`@OneToMany`, cascade ALL, orphanRemoval).

### 3.3 `project_subnet` — 프로젝트 서브넷

| 컬럼                | 타입         | 제약                | 설명                                        |
| ------------------- | ------------ | ------------------- | ------------------------------------------- |
| `id`              | BIGINT       | PK, IDENTITY        |                                             |
| `project_id`      | BIGINT       | FK →`project.id` |                                             |
| `ordinal`         | INTEGER      |                     | `@OrderColumn` 목록 순서                  |
| `subnet_id`       | VARCHAR(80)  | NOT NULL            | 서브넷 ID                                   |
| `cidr`            | VARCHAR(80)  | NOT NULL            | CIDR                                        |
| `zone_class`      | VARCHAR(30)  |                     | `OPEN` / `SENSITIVE` / `CONFIDENTIAL` |
| `name`            | VARCHAR(200) |                     | 이름                                        |
| `agent_id`        | VARCHAR(120) |                     | 보고한 에이전트                             |
| `manually_edited` | BOOLEAN      |                     | 수동 편집 여부                              |

### 3.4 `project_rule` — 프로젝트 연결 규칙

| 컬럼                      | 타입         | 제약                | 설명                                      |
| ------------------------- | ------------ | ------------------- | ----------------------------------------- |
| `id`                    | BIGINT       | PK, IDENTITY        |                                           |
| `project_id`            | BIGINT       | FK →`project.id` |                                           |
| `rule_id`               | VARCHAR(80)  | NOT NULL            | 규칙 ID                                   |
| `source_subnet_id`      | VARCHAR(80)  |                     | 출발 서브넷                               |
| `destination_subnet_id` | VARCHAR(80)  |                     | 도착 서브넷                               |
| `port_number`           | INTEGER      |                     | NULL이면 "모든 포트"                      |
| `protocol`              | VARCHAR(20)  |                     | 기본`"tcp"`                             |
| `origin`                | VARCHAR(30)  |                     | `MANUAL` / `DISCOVERED` (기본 MANUAL) |
| `enabled`               | BOOLEAN      | NOT NULL            | 사용 여부 (기본 true)                     |
| `note`                  | VARCHAR(500) |                     | 메모                                      |

### 3.5 `configuration` — 수집 장치 노드 (허브)

| 컬럼                      | 타입         | 제약             | 설명                                                                          |
| ------------------------- | ------------ | ---------------- | ----------------------------------------------------------------------------- |
| `node_id`               | INTEGER      | PK, IDENTITY     |                                                                               |
| `_hostname`             | VARCHAR(255) |                  | 호스트명 (`findBy_hostname`)                                                |
| `agent_id`              | VARCHAR(120) | **UNIQUE** | 에이전트 식별자                                                               |
| `_device_type`          | VARCHAR(255) |                  | `SWITCH`/`ROUTER`/`FIREWALL`/`VM`                                     |
| `_configuration_format` | VARCHAR(255) |                  | `CISCO_IOS`/`ARISTA_vEOS`/`OPNSense`/`AlpineFirewall`/`OpenvSwitch` |

> `_interfaces`, `_vrfs`, `_packetFilters`, `_vlans` 는 `@Transient` — **컬럼이 아닙니다**.

### 3.6 `expected_agent` — 등록 기대 에이전트

| 컬럼                            | 타입         | 제약                      | 설명 |
| ------------------------------- | ------------ | ------------------------- | ---- |
| `id`                          | BIGINT       | PK, IDENTITY              |      |
| `agent_id`                    | VARCHAR(120) | NOT NULL,**UNIQUE** |      |
| `project_key`                 | VARCHAR(120) |                           |      |
| `device_type`                 | VARCHAR(30)  |                           |      |
| `node_type`                   | VARCHAR(30)  |                           |      |
| `expected_ip`                 | VARCHAR(60)  |                           |      |
| `status`                      | VARCHAR(20)  |                           |      |
| `note`                        | VARCHAR(500) |                           |      |
| `created_at` / `updated_at` | TIMESTAMP    |                           |      |

### 3.7 `quarantine_state` — 격리 이력

| 컬럼                  | 타입         | 제약                           | 설명                            |
| --------------------- | ------------ | ------------------------------ | ------------------------------- |
| `id`                | BIGINT       | PK, IDENTITY                   |                                 |
| `agent_id`          | VARCHAR(120) |                                |                                 |
| `node_id`           | INTEGER      |                              | 노드 삭제 후에도 격리 이력 보존 (FK 없음) |
| `scope`             | VARCHAR(20)  |                                | `NODE`(기본) / `CONNECTION` |
| `target_cidr`       | VARCHAR(80)  |                                | 연결 단위 격리 대상 대역        |
| `project_key`       | VARCHAR(120) |                                |                                 |
| `requested_by`      | VARCHAR(120) |                                | 요청자                          |
| `reason`            | VARCHAR(500) |                                | 사유                            |
| `command_delivered` | BOOLEAN      | NOT NULL                       | 명령 전달 여부 (기본 false)     |
| `quarantined_at`    | TIMESTAMP    | NOT NULL                       | 격리 시각                       |
| `released_at`       | TIMESTAMP    |                                | 해제 시각 (NULL = 격리 중)      |
| `released_by`       | VARCHAR(120) |                                | 해제자                          |

인덱스:

| 인덱스                      | 컬럼                        |
| --------------------------- | --------------------------- |
| `idx_quarantine_agent`    | `(agent_id, released_at)` |
| `idx_quarantine_node`     | `(node_id, released_at)`  |
| `idx_quarantine_released` | `(released_at)`           |

### 3.8 `notification` — 알림 이력

| 컬럼                | 타입          | 제약                      | 설명             |
| ------------------- | ------------- | ------------------------- | ---------------- |
| `id`              | BIGINT        | PK, IDENTITY              |                  |
| `notification_id` | VARCHAR(80)   | NOT NULL,**UNIQUE** |                  |
| `category`        | VARCHAR(40)   | NOT NULL                  | 기본`"SYSTEM"` |
| `severity`        | VARCHAR(20)   | NOT NULL                  | 기본`"info"`   |
| `title`           | VARCHAR(300)  | NOT NULL                  |                  |
| `message`         | VARCHAR(2000) |                           |                  |
| `project_key`     | VARCHAR(120)  |                           |                  |
| `project_id`      | BIGINT        | FK →`project.id`       |                  |
| `agent_id`        | VARCHAR(120)  |                           |                  |
| `node_id`         | INTEGER       |                           |                  |
| `source`          | VARCHAR(200)  |                           | 기본`"system"` |
| `occurred_at`     | TIMESTAMP     | NOT NULL                  |                  |
| `is_read`         | BOOLEAN       | NOT NULL                  | 기본 false       |
| `link`            | VARCHAR(300)  |                           |                  |
| `dedupe_key`      | VARCHAR(200)  |                           | 중복 제거 키     |
| `repeat_count`    | INTEGER       | NOT NULL                  | 기본 1           |

인덱스:

| 인덱스                        | 컬럼                           |
| ----------------------------- | ------------------------------ |
| `idx_notification_occurred` | `(occurred_at)`              |
| `idx_notification_read`     | `(is_read)`                  |
| `idx_notification_project`  | `(project_key, occurred_at)` |
| `idx_notification_agent`    | `(agent_id, occurred_at)`    |

### 3.9 `device_log` — 수집 장치 로그

| 컬럼             | 타입          | 제약                           | 설명                   |
| ---------------- | ------------- | ------------------------------ | ---------------------- |
| `id`           | BIGINT        | PK, IDENTITY                   |                        |
| `node_id`      | INTEGER       | FK →`configuration.node_id` |                        |
| `agent_id`     | VARCHAR(120)  | NOT NULL                       |                        |
| `project_key`  | VARCHAR(120)  |                                |                        |
| `product`      | VARCHAR(120)  |                                |                        |
| `logged_at`    | TIMESTAMP     | NOT NULL                       |                        |
| `collected_at` | TIMESTAMP     |                                |                        |
| `severity_num` | INTEGER       | NOT NULL                       | 기본 6 (syslog 심각도) |
| `severity`     | VARCHAR(20)   | NOT NULL                       | 기본`"info"`         |
| `facility`     | VARCHAR(60)   |                                |                        |
| `message_id`   | VARCHAR(120)  |                                |                        |
| `message`      | VARCHAR(8000) |                                |                        |
| `raw`          | VARCHAR(8000) |                                | 원문                   |
| `source`       | VARCHAR(20)   |                                | 기본`"agent"`        |
| `fingerprint`  | VARCHAR(64)   |                                | 중복 판별 지문         |
| `repeat_count` | INTEGER       |                                | 기본 1                 |
| `highlighted`  | BOOLEAN       |                                | 기본 false             |
| `note`         | VARCHAR(1000) |                                | 메모                   |

인덱스:

| 인덱스                        | 컬럼                      |
| ----------------------------- | ------------------------- |
| `idx_device_log_agent_time` | `(agent_id, logged_at)` |
| `idx_device_log_time`       | `(logged_at)`           |
| `idx_device_log_severity`   | `(severity_num)`        |
| `idx_device_log_node`       | `(node_id)`             |

### 3.10 `log_analysis` — AI 로그 분석

| 컬럼                            | 타입                                 | 설명                                     |
| ------------------------------- | ------------------------------------ | ---------------------------------------- |
| `id`                          | BIGINT, PK IDENTITY                  |                                          |
| `analysis_id`                 | VARCHAR(60) NOT NULL**UNIQUE** | 분석 ID                                  |
| `log_id`                      | BIGINT                               | 대상 로그                                |
| `project_key`                 | VARCHAR(120)                         |                                          |
| `project_id`                  | BIGINT FK →`project.id`           |                                          |
| `agent_id`                    | VARCHAR(120)                         | 여러 장비면 NULL                         |
| `scope`                       | VARCHAR(20)                          |                                          |
| `severity_filter`             | VARCHAR(20)                          |                                          |
| `period_from` / `period_to` | TIMESTAMP                            | 분석 기간                                |
| `filter_json`                 | VARCHAR(4000)                        | 필터 재현용 JSON                         |
| `log_ids_json`                | VARCHAR(4000)                        | 프롬프트에 넣은 로그 ID 목록             |
| `included_log_count`          | INTEGER                              | 실제 전달 줄 수 (기본 0)                 |
| `total_log_count`             | INTEGER                              | 필터 전체 로그 수 (기본 0)               |
| `provider_name` / `model`   | VARCHAR(120/200)                     | 사용한 AI                                |
| `elapsed_ms`                  | BIGINT                               | 호출 소요                                |
| `risk_level`                  | VARCHAR(20)                          | `CRITICAL`/`HIGH`/`MEDIUM`/`LOW` |
| `summary`                     | VARCHAR(4000)                        | 요약                                     |
| `root_cause`                  | VARCHAR(8000)                        | 근본 원인                                |
| `recommendations_json`        | VARCHAR(8000)                        | 권고(JSON)                               |
| `raw_response`                | VARCHAR(30000)                       | 응답 원문                                |
| `requested_by`                | VARCHAR(200)                         | 요청자                                   |
| `error_message`               | VARCHAR(4000)                        | 오류                                     |
| `succeeded`                   | BOOLEAN                              | 성공 여부 (기본 true)                    |
| `created_at`                  | TIMESTAMP NOT NULL                   |                                          |

제약/인덱스:

| 이름                              | 종류   | 컬럼                          |
| --------------------------------- | ------ | ----------------------------- |
| `uk_log_analysis_agent_project` | UNIQUE | `(agent_id, project_id)`    |
| `idx_log_analysis_project`      | INDEX  | `(project_key, created_at)` |
| `idx_log_analysis_agent`        | INDEX  | `(agent_id, created_at)`    |

> 복합 UNIQUE `(agent_id, project_id)` 는 "같은 장비·같은 프로젝트" 중복 분석을 막습니다.

### 3.11 `policy_advice` — AI 정책 어드바이스

| 컬럼                          | 타입                                 | 설명                               |
| ----------------------------- | ------------------------------------ | ---------------------------------- |
| `id`                        | BIGINT, PK IDENTITY                  |                                    |
| `advice_id`                 | VARCHAR(60) NOT NULL**UNIQUE** |                                    |
| `project_key`               | VARCHAR(120) NOT NULL                |                                    |
| `project_name`              | VARCHAR(200)                         |                                    |
| `rule_id`                   | VARCHAR(120)                         |                                    |
| `scope`                     | VARCHAR(20)                          | 기본`"project"`                  |
| `violation_count`           | INTEGER                              | 위반 건수 스냅샷 (기본 0)          |
| `compliant`                 | BOOLEAN                              | 준수 여부 스냅샷 (기본 false)      |
| `included_violation_count`  | INTEGER                              | 실제 전달 위반 수 (기본 0)         |
| `truncated`                 | BOOLEAN                              | 위반 잘림 여부 (기본 false)        |
| `provider_name` / `model` | VARCHAR(120/200)                     |                                    |
| `elapsed_ms`                | BIGINT                               |                                    |
| `risk_level`                | VARCHAR(20)                          |                                    |
| `summary`                   | VARCHAR(4000)                        |                                    |
| `root_cause`                | VARCHAR(8000)                        |                                    |
| `policy_advice`             | VARCHAR(4000)                        | 핵심 조언 한 줄                    |
| `options_json`              | VARCHAR(8000)                        | 해결 선택지(JSON)                  |
| `evidence_json`             | VARCHAR(4000)                        | 근거 인용(JSON)                    |
| `needs_more_data`           | BOOLEAN                              | 추가 자료 요청 (기본 false)        |
| `raw_response`              | VARCHAR(30000)                       | 응답 원문                          |
| `structured`                | BOOLEAN                              | 구조화 파싱 성공 여부 (기본 false) |
| `requested_by`              | VARCHAR(200)                         |                                    |
| `error_message`             | VARCHAR(4000)                        |                                    |
| `succeeded`                 | BOOLEAN                              | 성공 여부 (기본 true)              |
| `created_at`                | TIMESTAMP NOT NULL                   |                                    |

인덱스:

| 인덱스                        | 컬럼                          |
| ----------------------------- | ----------------------------- |
| `idx_policy_advice_project` | `(project_key, created_at)` |
| `idx_policy_advice_rule`    | `(rule_id, created_at)`     |

### 3.12 `compliance_change` — 컴플라이언스 변경

| 컬럼            | 타입           | 제약                      | 설명            |
| --------------- | -------------- | ------------------------- | --------------- |
| `id`          | BIGINT         | PK, IDENTITY              |                 |
| `change_id`   | VARCHAR(80)    | NOT NULL,**UNIQUE** |                 |
| `scope`       | VARCHAR(20)    |                           |                 |
| `project_key` | VARCHAR(120)   |                           |                 |
| `agent_id`    | VARCHAR(120)   |                           |                 |
| `change_type` | VARCHAR(60)    |                           | `type` 컬럼명 |
| `summary`     | VARCHAR(1000)  |                           |                 |
| `changed_by`  | VARCHAR(200)   |                           |                 |
| `timestamp`   | TIMESTAMP      |                           |                 |
| `status`      | VARCHAR(20)    |                           |                 |
| `detail`      | VARCHAR(20000) |                           |                 |

### 3.13 `ai_provider` — AI 공급자

| 컬럼                               | 타입             | 제약         | 설명                         |
| ---------------------------------- | ---------------- | ------------ | ---------------------------- |
| `id`                             | BIGINT           | PK, IDENTITY |                              |
| `name`                           | VARCHAR(120)     | NOT NULL     |                              |
| `base_url`                       | VARCHAR(500)     | NOT NULL     |                              |
| `api_key_encrypted`              | VARCHAR(2000)    |              | 암호화된 키                  |
| `model`                          | VARCHAR(200)     | NOT NULL     |                              |
| `auth_style`                     | VARCHAR(20)      |              | 인증 방식 (기본`"bearer"`) |
| `system_prompt`                  | VARCHAR(4000)    |              |                              |
| `timeout_seconds`                | INTEGER          |              | 기본 120                     |
| `max_tokens`                     | INTEGER          |              |                              |
| `temperature`                    | DOUBLE           |              | 기본 0.2                     |
| `allow_insecure_tls`             | BOOLEAN          |              | 기본 false                   |
| `is_default`                     | BOOLEAN          |              | 기본 공급자 (기본 false)     |
| `enabled`                        | BOOLEAN          |              | 기본 true                    |
| `last_status` / `last_message` | VARCHAR(40/1000) |              | 연결 확인 결과               |
| `last_checked_at`                | TIMESTAMP        |              |                              |
| `created_at` / `updated_at`    | TIMESTAMP        |              |                              |

### 3.14 `opnsense_credential` — OPNsense 자격 증명

| 컬럼                            | 타입          | 제약                      | 설명                                 |
| ------------------------------- | ------------- | ------------------------- | ------------------------------------ |
| `id`                          | BIGINT        | PK, IDENTITY              |                                      |
| `node_id`                     | INTEGER       | NOT NULL, **UNIQUE FK → `configuration.node_id`** | OPNsense 장치 정본 |
| `display_name`                | VARCHAR(128)  |                           |                                      |
| `base_url`                    | VARCHAR(255)  | NOT NULL                  |                                      |
| `api_key`                     | VARCHAR(255)  |                           |                                      |
| `secret_encrypted`            | VARCHAR(1024) |                           | 필드명`secret`                     |
| `allow_insecure_tls`          | BOOLEAN       | NOT NULL                  |                                      |
| `status`                      | VARCHAR(20)   |                           | `UNVERIFIED` / `OK` / `FAILED` |
| `last_checked_at`             | TIMESTAMP     |                           |                                      |
| `last_error`                  | VARCHAR(500)  |                           |                                      |
| `detected_version`            | VARCHAR(64)   |                           |                                      |
| `created_at` / `updated_at` | TIMESTAMP     |                           |                                      |

### 3.15 `opnsense_firewall` — OPNsense 방화벽 노드

| 컬럼              | 타입         | 제약                                                             | 설명 |
| ----------------- | ------------ | ---------------------------------------------------------------- | ---- |
| `node_id`       | INTEGER      | **PK = FK → `configuration.node_id`** (`@MapsId` 1:1) |      |
| `name`          | VARCHAR(128) |                                                                  |      |
| `management_ip` | VARCHAR(64)  |                                                                  |      |
| `version`       | VARCHAR(64)  |                                                                  |      |

### 3.16 `network_interface` — 장치 인터페이스

| 컬럼             | 타입        | 제약                           | 설명                    |
| ---------------- | ----------- | ------------------------------ | ----------------------- |
| `interface_id` | BIGINT      | PK, IDENTITY                   |                         |
| `node_id`      | INTEGER     | FK →`configuration.node_id` |                         |
| `member_name`  | VARCHAR(50) | NOT NULL                       | 필드명`interfaceName` |

### 3.17 `rest_api_node_config` — REST API 노드 설정

> Legacy JPA 엔티티입니다. 현재 OPNsense 서비스 경로에는 사용처가 없으며, OPNsense 자격 증명의 정본은 `opnsense_credential`입니다. 신규 접속 정보는 이 테이블에 저장하지 않습니다.

| 컬럼        | 타입                                          | 설명                 |
| ----------- | --------------------------------------------- | -------------------- |
| `id`      | BIGINT, PK IDENTITY                           |                      |
| `node_id` | INTEGER, UNIQUE FK →`configuration.node_id` (선택적 1:1) | 노드별 설정 |
| `apikey`  | VARCHAR(255)                                  |                      |
| `baseurl` | VARCHAR(255)                                  | `http://host:port` |

---

## 4. 열거형(Enum) 요약

| 열거형                        | 사용 컬럼                               | 값                                                                                |
| ----------------------------- | --------------------------------------- | --------------------------------------------------------------------------------- |
| `User.Role`                 | `"USER".role`                         | `ADMIN`, `OPERATOR`, `VIEWER`                                               |
| `QuarantineState.Scope`     | `quarantine_state.scope`              | `NODE`, `CONNECTION`                                                          |
| `OPNsenseCredential.Status` | `opnsense_credential.status`          | `UNVERIFIED`, `OK`, `FAILED`                                                |
| `PolicyRule.Origin`         | `project_rule.origin`                 | `MANUAL`, `DISCOVERED`                                                        |
| `ZoneClass`                 | `project_subnet.zone_class`           | `OPEN`(1), `SENSITIVE`(2), `CONFIDENTIAL`(3)                                |
| `DeviceType`                | `configuration._device_type`          | `SWITCH`, `ROUTER`, `FIREWALL`, `VM`                                      |
| `ConfigurationFormat`       | `configuration._configuration_format` | `CISCO_IOS`, `ARISTA_vEOS`, `OPNSense`, `AlpineFirewall`, `OpenvSwitch` |

> `ZoneClass` 는 등급 레벨 차이 `<= 1` 만 직접 연결을 허용합니다(망분리 규칙의 핵심).

---

## 5. 인덱스/제약 요약

| 이름                              | 종류   | 테이블               | 컬럼                           |
| --------------------------------- | ------ | -------------------- | ------------------------------ |
| `uk_log_analysis_agent_project` | UNIQUE | `log_analysis`     | `(agent_id, project_id)`     |
| `idx_log_analysis_project`      | INDEX  | `log_analysis`     | `(project_key, created_at)`  |
| `idx_log_analysis_agent`        | INDEX  | `log_analysis`     | `(agent_id, created_at)`     |
| `idx_policy_advice_project`     | INDEX  | `policy_advice`    | `(project_key, created_at)`  |
| `idx_policy_advice_rule`        | INDEX  | `policy_advice`    | `(rule_id, created_at)`      |
| `idx_device_log_agent_time`     | INDEX  | `device_log`       | `(agent_id, logged_at)`      |
| `idx_device_log_time`           | INDEX  | `device_log`       | `(logged_at)`                |
| `idx_device_log_severity`       | INDEX  | `device_log`       | `(severity_num)`             |
| `idx_device_log_node`           | INDEX  | `device_log`       | `(node_id)`                  |
| `idx_notification_occurred`     | INDEX  | `notification`     | `(occurred_at)`              |
| `idx_notification_read`         | INDEX  | `notification`     | `(is_read)`                  |
| `idx_notification_project`      | INDEX  | `notification`     | `(project_key, occurred_at)` |
| `idx_notification_agent`        | INDEX  | `notification`     | `(agent_id, occurred_at)`    |
| `idx_quarantine_agent`          | INDEX  | `quarantine_state` | `(agent_id, released_at)`    |
| `idx_quarantine_node`           | INDEX  | `quarantine_state` | `(node_id, released_at)`     |
| `idx_quarantine_released`       | INDEX  | `quarantine_state` | `(released_at)`              |
| `uk_opnsense_credential_node` | UNIQUE | `opnsense_credential` | `(node_id)` |

**UNIQUE 컬럼/키**: `"USER".username`, `project.project_key`, `expected_agent.agent_id`, `notification.notification_id`, `log_analysis.analysis_id`, `policy_advice.advice_id`, `compliance_change.change_id`, `configuration.agent_id`, `opnsense_credential.node_id`, `rest_api_node_config.node_id` (OneToOne).

---

## 6. 운영 설정 / 주의점

### 프로필별 DDL 정책

| 프로필              | DB         | `ddl-auto` | 파일                         |
| ------------------- | ---------- | ------------ | ---------------------------- |
| `local` (기본)    | H2 파일    | `update`   | `application-local.yml`    |
| `postgres` (운영) | PostgreSQL | `validate` | `application-postgres.yml` |

- `postgres` 프로필은 `validate` 이므로 **엔티티와 실제 테이블이 다르면 기동 실패**합니다. 스키마 변경은 마이그레이션 도구로 관리하세요.
- `postgres` 는 `PostgreSQLDialect` + `batch_size=50`, `order_inserts/updates=true`.

### 접속 (환경변수로 덮어쓰기 가능)

```
DB_HOST=localhost  DB_PORT=5432  DB_NAME=sonarvalidator
DB_USER=sonarvalidator  DB_PASSWORD=***  DB_POOL_MAX=10  DB_POOL_MIN_IDLE=2
JPA_DDL_AUTO=validate
```

- H2 로컬: `jdbc:h2:file:./data/sonarvalidator;AUTO_SERVER=TRUE`, 콘솔 `/h2-console`.
- **운영 배포 시 `SPRING_PROFILES_ACTIVE=postgres` 필수** (빠뜨리면 H2 사용).

### 주요 주의점

- **컬럼 개수 ≠ 필드 개수**: `Configuration` 의 `_interfaces`/`_vrfs`/`_packetFilters`/`_vlans` 는 `@Transient` 입니다.
- **암호화 컬럼**: `ai_provider.api_key_encrypted`, `opnsense_credential.secret_encrypted` 는 `SecretCipher`(AES-256-GCM)로 암호화됩니다. `sonar.secret.key` 를 비우면 재시작 시 복호화가 불가능합니다.
- **시각 타입**: 대부분 `java.util.Date` (TIMESTAMP). `@JsonFormat` 로 UTC ISO-8601 직렬화합니다.
- **마이그레이션 부재**: 저장소에 Flyway/Liquibase/`.sql` 이 없습니다. 스키마 이력이 필요하면 마이그레이션 도구 도입이 필요합니다.
- **`opnsense_firewall`**: `@MapsId` 로 PK가 곧 FK(`node_id`)인 1:1 관계입니다.
- **`opnsense_credential`**: `node_id`가 `configuration.node_id`를 참조하는 필수 1:1 관계입니다. credential 자연 키가 Agent 식별자가 되는 것이 아니라 노드 정본에 귀속됩니다. 기존 UI의 Agent-ID 경로는 API 호환 별칭으로 Configuration을 먼저 조회하고, 응답에는 `node_id`를 포함합니다.
- **`quarantine_state.node_id`**: `configuration.node_id`의 값이지만 FK는 없습니다. 노드 삭제 뒤에도 격리 감사 이력을 보존합니다.
- **NamingStrategy**: Spring Boot 기본(CamelCaseToUnderscores)이 적용됩니다. 명시적 `@Column(name=...)` 이 있는 컬럼은 그 이름을 우선합니다.

### 기존 PostgreSQL 데이터의 OPNsense credential 이전

`opnsense_credential`에 Agent-ID 문자열만 저장하던 DB는 새 엔티티 검증 전에 한 번 이전해야 합니다. 먼저 모든 credential의 `agent_id`가 `configuration.agent_id`와 대응하는지 확인하고, 대응되지 않는 행은 노드 정본을 등록/수정한 뒤 아래 작업을 실행합니다. 이 스크립트는 기존 테이블이 구버전(`agent_id` 보유, `node_id` 미보유)일 때 사용하는 단회 이전입니다.

```sql
BEGIN;

ALTER TABLE opnsense_credential ADD COLUMN node_id INTEGER;

UPDATE opnsense_credential AS credential
SET node_id = configuration.node_id
FROM configuration
WHERE configuration.agent_id = credential.agent_id;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM opnsense_credential WHERE node_id IS NULL) THEN
    RAISE EXCEPTION 'OPNsense credential has no matching configuration node; resolve agent_id before migration';
  END IF;
END $$;

ALTER TABLE opnsense_credential ALTER COLUMN node_id SET NOT NULL;
ALTER TABLE opnsense_credential
  ADD CONSTRAINT uk_opnsense_credential_node UNIQUE (node_id);
ALTER TABLE opnsense_credential
  ADD CONSTRAINT fk_opnsense_credential_node
  FOREIGN KEY (node_id) REFERENCES configuration(node_id);
ALTER TABLE opnsense_credential DROP COLUMN agent_id;

COMMIT;
```

운영 설정은 `ddl-auto=validate`이므로 애플리케이션 기동 전에 이전을 완료해야 합니다. 이 저장소에는 마이그레이션 실행기가 없으므로 운영 절차에서 백업 후 DB 관리자가 적용합니다.
