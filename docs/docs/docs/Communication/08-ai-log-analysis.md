---
title: "08. AI 로그 분석 엔진"
sidebar_label: "08. AI 로그 분석"
---

# AI 로그 분석 엔진

장비 로그를 모아 **OpenAI 호환 API** 로 분석하는 기능입니다. 공급자(주소·키·모델)는
DB에 저장하고, 화면에서 로그를 골라 분석합니다.

```
  장비 로그 (warning 이상 / 사용자 지정)
       │  필터: 기간 · 프로젝트 · agent · 심각도
       ▼
  LogService.resolveForAnalysis()   ← 시간순 정렬 (인과 파악용)
       │
       ▼
  프롬프트 조립 (시스템 지침 + 로그 본문 + 추가 질문)
       │
       ▼
  OpenAiCompatibleClient.chat()     ← 공급자/모델은 DB 설정에서
       │
       ▼
  JSON 파싱 → summary / root_cause / recommendations / risk_level
       │
       ▼
  log_analysis 테이블에 저장 (실패도 기록)
```

---

## 1. 왜 "OpenAI 호환" 하나로 충분한가

`POST {baseUrl}/chat/completions` 규격은 사실상 표준이 되어, 아래가 **같은 코드**로
동작합니다. 공급자별 분기 코드가 필요 없습니다.

| 공급자 | baseUrl 예 |
| --- | --- |
| OpenAI | `https://api.openai.com/v1` |
| Ollama (로컬) | `http://localhost:11434/v1` |
| vLLM / LocalAI | `http://host:8000/v1` |
| Groq / Together / OpenRouter | 각 사의 `/v1` |
| Azure OpenAI | `https://<res>.openai.azure.com/openai/deployments/<model>` |

### ⚠️ baseUrl 정규화 (가장 자주 틀리는 부분)

사용자는 `https://api.openai.com`, `.../v1`, `.../v1/` 을 **모두** 입력합니다.
그대로 이어 붙이면 `/v1/v1/chat/completions` 나 `//chat/completions` 가 되어
**404** 가 납니다. 메시지가 불친절해 원인을 찾기 어렵습니다.

그래서 경로를 합칠 때 아래를 적용합니다.

1. 끝 슬래시 제거
2. 이미 `/chat/completions` 로 끝나면 그대로 사용
3. `/openai/deployments/` 가 있으면 Azure 로 보고 `api-version` 쿼리 추가
4. 그 외에는 `/chat/completions` 를 붙임

---

## 2. DB 스키마 (3개 테이블)

### 2.1 `ai_provider` — AI 공급자 설정

| 컬럼 | 설명 |
| --- | --- |
| `name` | 표시 이름 (중복 불가) |
| `base_url` | OpenAI 호환 엔드포인트 |
| `api_key_encrypted` | **AES-256-GCM 암호문** (평문 저장 안 함) |
| `model` | 모델 이름 |
| `auth_style` | `bearer` / `azure` (헤더 선택) |
| `system_prompt` | 조직별 추가 지침 |
| `timeout_seconds` | 요청 타임아웃 (로컬 모델은 300 권장) |
| `max_tokens`, `temperature` | 생성 파라미터 |
| `allow_insecure_tls` | 자체 서명 인증서 허용 |
| `is_default` | 기본 공급자 (하나만 유지) |
| `enabled` | 사용 여부 |
| `last_status` / `last_message` / `last_checked_at` | 연결 확인 결과 |

### 2.2 `device_log` — 수집한 로그

**인덱스가 조회 성능의 핵심**입니다. 로그는 다른 테이블보다 훨씬 빠르게 커집니다.

```sql
idx_device_log_agent_time (agent_id, logged_at)   -- 가장 빈번한 조회
idx_device_log_time       (logged_at)             -- 장비 미지정
idx_device_log_severity   (severity_num)          -- 심각도 필터
```

컬럼 순서가 중요합니다. `agent_id` 를 **앞에** 두어야 특정 장비 조회가 좁혀지고,
그 안에서 `logged_at` 범위가 정렬됩니다. 순서를 뒤집으면 시간 범위로 먼저
훑어 느려집니다.

| 컬럼 | 설명 |
| --- | --- |
| `logged_at` | 로그 발생 시각 (정렬·범위 필터 기준) |
| `severity_num` | **정규화된 등급 0~7** (낮을수록 심각) |
| `severity` | 표준 이름 (`critical`/`warning`/…) |
| `facility` / `message_id` | syslog facility / 벤더 메시지 코드 |
| `raw` | **원문 한 줄** (AI 분석의 근거) |
| `fingerprint` | SHA-256 앞 32자 — 중복 수집 방지 |
| `repeat_count` | 같은 로그 반복 횟수 |
| `highlighted` / `note` | 사용자 표시 / 메모 |

### 2.3 `log_analysis` — 분석 결과 (실패 포함)

| 컬럼 | 설명 |
| --- | --- |
| `analysis_id` | 외부 노출용 ID (`AIA-1A2B3C4D`) |
| `scope` | `selected` / `filter` / `single` |
| `filter_json` | 분석 시점의 필터 (재현용) |
| `log_ids_json` | 실제 프롬프트에 넣은 로그 ID 목록 |
| `included_log_count` / `total_log_count` | 사용한 수 / 전체 수 → **잘림 판단** |
| `risk_level` | `CRITICAL`/`HIGH`/`MEDIUM`/`LOW` |
| `summary` / `root_cause` / `recommendations_json` | 구조화 파싱 결과 |
| `raw_response` | 모델 응답 원문 (파싱 실패 시에도 남음) |
| `succeeded` / `error_message` | **실패도 저장** |
| `elapsed_ms` | 소요 시간 |

---

## 3. 로그 정규화 (벤더별 표기 흡수)

벤더마다 심각도 표기가 다릅니다. 정규화하지 않으면 "warning 이상" 필터를
벤더별로 따로 구현해야 합니다.

| 장비 | 예시 | 심각도 위치 |
| --- | --- | --- |
| Cisco IOS-XE | `%SYS-5-CONFIG_I: Configured from console` | `SYS-`**`5`** |
| FRR | `%DAEMON-3-BGP_PEER_DOWN: peer down` | `DAEMON-`**`3`** |
| Linux syslog | `kernel: eth1: link is down` | 키워드 |

### syslog 등급 (숫자가 낮을수록 심각)

```
0 emergency  1 alert  2 critical  3 error
4 warning    5 notice 6 info      7 debug
```

**"warning 이상" = `severity_num <= 4`** 입니다. 부등호를 반대로 쓰면 정반대
결과가 되는데, 결과가 그럴듯해 보여 알아채기 어렵습니다.

### ⚠️ 함정 3가지 (테스트로 발견)

#### ① `replaceAll("Z?$", "Z")` 는 Z 를 덧붙인다

ISO 타임스탬프 정규화에서 이 패턴을 쓰면 끝의 **0폭 위치**에서도 매치되어
`…33Z` 가 `…33ZZ` 가 됩니다. `Instant.parse` 가 실패하고 **항상 폴백(수집 시각)** 을
쓰게 됩니다. 예외도 로그도 없어 정렬만 조용히 시간순이 아니게 됩니다.

#### ② `contains("down")` 은 `shutdown` 도 걸린다

정상적인 관리 조작인 "interface shutdown" 이 오류로 분류됩니다.
→ `\bdown\b` 처럼 **단어 경계**를 씁니다.

#### ③ `\bwarn\b` 는 `warning` 을 놓친다

영어는 어미가 붙으므로 양쪽 경계를 요구하면 안 됩니다.
→ 앞 경계만 요구하는 **접두 매칭**(`\bwarn`)을 씁니다.

### 판단 근거가 없을 때의 기본값 = `notice(5)`

- `info(6)` 로 두면 어떤 필터에도 안 걸려 **조용히 묻힙니다**
- `warning(4)` 로 두면 모든 로그가 경고가 되어 **알림이 무의미**해집니다

---

## 4. 프롬프트 설계

### 4.1 입력 크기 상한 (둘 다 필요)

| 상한 | 값 | 이유 |
| --- | --- | --- |
| 줄 수 | 300 | 로그 한 줄 평균 150자면 약 12,000 토큰 |
| 글자 수 | 400,000 | **줄 수만 제한하면 한 줄이 긴 경우를 막지 못함** |
| 한 줄 | 2,000자 | 설정 덤프가 한 줄로 들어오면 전체 예산을 다 씀 |

### 4.2 잘렸다는 사실을 양쪽에 알린다

일부만 보고 "문제 없음" 이라고 답하면 위험합니다.

- **모델에게**: `(컨텍스트 한도로 일부만 제공됨 — 전체를 보지 못했다는 점을 감안하세요)`
- **사용자에게**: 응답에 `included_log_count` / `total_log_count` / `truncated` 를 담아 화면이 배지로 표시

### 4.3 근거 없는 추측 금지

모델은 로그에 없는 원인을 그럴듯하게 지어내는 경향이 있습니다. 그래서
시스템 프롬프트에 규칙을 명시하고, 응답 스키마에 `evidence`(근거 로그 줄)를
요구합니다.

```
1. 로그에 실제로 나타난 내용만 근거로 판단하세요.
   근거가 부족하면 추측하지 말고, 무엇을 더 확인해야 하는지 말하세요.
2. 로그에 없는 장비/설정/토폴로지를 가정하지 마세요.
3. 반복되는 로그(repeat)는 문제의 심각도 판단에 반영하세요.
4. 타임스탬프 순서로 사건의 인과관계를 파악하세요.
5. 반드시 아래 JSON 형식으로만 답하세요.
```

### 4.4 분석은 시간순(오래된 것 먼저)으로 전달

화면은 최신순이 자연스럽지만 분석은 반대입니다. **인과 순서**를 봐야
"무엇이 먼저 깨졌는지" 를 판단할 수 있습니다.

### 4.5 응답 파싱 실패를 오류로 처리하지 않는다

모델이 코드블록으로 감싸거나 앞뒤에 설명을 붙이는 일이 흔합니다. 그때 분석
자체를 버리면 사용자는 "분석 실패" 만 보게 되는데, **원문에는 쓸 만한 내용이
있습니다.** 그래서 원문은 항상 남기고, 파싱 성공 시에만 구조화 필드를 채웁니다.

파싱 순서:
1. 앞뒤 공백 제거
2. ```` ```json … ``` ```` 코드블록 벗기기
3. 첫 `{` 부터 마지막 `}` 까지 시도

---

## 5. API 키 취급 원칙

| 원칙 | 구현 |
| --- | --- |
| 저장은 항상 암호화 | `SecretCipher` (AES-256-GCM) |
| 조회는 마스킹만 | `has_api_key` + `api_key_masked: "********"` |
| 수정 시 키를 비우면 **기존 유지** | 빈 문자열이면 암호문을 건드리지 않음 |
| 화면은 키를 불러오지 않음 | 수정 폼을 항상 빈 값으로 시작 |

> **키를 비우면 유지** 규칙이 없으면, 수정할 때마다 키를 다시 입력해야 하고,
> 사용자가 "지워질까 봐" 또는 "지워지겠지" 라고 오해해 설정이 조용히 깨집니다.

---

## 6. REST API

### 6.1 AI 공급자

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/v1/ai/providers` | 목록 (키 마스킹) |
| `GET` | `/api/v1/ai/providers/enabled` | 사용 중인 것만 |
| `POST` | `/api/v1/ai/providers` | 생성/수정 (`id` 없으면 생성) |
| `DELETE` | `/api/v1/ai/providers/{id}` | 삭제 |
| `POST` | `/api/v1/ai/providers/{id}/check` | 연결 확인 |
| `POST` | `/api/v1/ai/providers/{id}/enabled` | 사용 토글 |
| `POST` | `/api/v1/ai/providers/{id}/default` | 기본 지정 |

### 6.2 로그

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/v1/logs` | 조회 (**모든 필터가 선택 파라미터**) |
| `GET` | `/api/v1/logs/filters` | 필터 선택지 (장비 목록 등) |
| `GET` | `/api/v1/logs/summary` | 심각도별 집계 |
| `POST` | `/api/v1/logs/ingest` | 적재 (JSON) |
| `POST` | `/api/v1/logs/upload` | 로그 파일 업로드 |
| `POST` | `/api/v1/logs/{id}/flags` | 표시/메모 |
| `POST` | `/api/v1/logs/analyze` | **AI 분석 실행** |
| `GET` | `/api/v1/logs/analyses` | 분석 이력 |
| `GET` | `/api/v1/logs/analyses/{analysisId}` | 분석 상세 |

### 6.3 필터를 한 엔드포인트로 통합한 이유

요구사항이 "날짜별 / 프로젝트별 / 특정 agent" 입니다. 세 축을 각각 별도
엔드포인트로 만들면 **조합**(프로젝트 + 기간 + 장비)을 표현할 수 없습니다.
그래서 모두 선택 파라미터로 받고, 안 준 축은 제한하지 않습니다.

```
GET /api/v1/logs?project_id=PRJ-1&agent_id=c8000v-1
                &from=2026-09-19T00:00:00Z&to=2026-09-19T23:59:59Z
                &severity=warning&search=BGP&highlighted_only=false&limit=200
```

### 6.4 ⚠️ 분석은 실패해도 200 을 돌려준다

```json
{
  "analysis_id": "AIA-82C302C5",
  "succeeded": false,
  "error_message": "연결 거부: http://localhost:9999/v1 (주소/포트 확인, 로컬 모델이면 실행 여부 확인)"
}
```

실패를 5xx 로 만들면 화면이 사유를 보여주기 어렵고, **이력에도 남기기 어렵습니다.**
실패를 남기지 않으면 화면에 아무 기록이 없어 사용자가 같은 실수를 반복합니다.

**프론트는 HTTP 상태가 아니라 `succeeded` 를 확인해야 합니다.**

### 6.5 로그 조회는 반드시 상한을 둔다

로그는 빠르게 늘어납니다. 상한 없는 조회는 브라우저를 멈춥니다.
`MAX_PAGE_SIZE = 500` 으로 자르고, 응답에 `truncated` 를 표시합니다.

---

## 7. 프론트엔드

### 7.1 화면 구성

```
┌─ 필터 바 ────────────────────────────────────────────┐
│ 프로젝트 · 장비 · 기간(from~to) · 심각도 · 검색어      │
├─ 잘림 경고 (truncated 일 때만) ───────────────────────┤
├─ 로그 목록 ────────────────────────────────────────┤
│ [선택] 시각 | agent | 심각도 | 메시지 | ★              │
├─ AI 분석 ─────────────────────────────────────────┤
│ 공급자 선택 · 추가 지시 · [선택 N건 분석] [전체 분석]  │
│ → 결과: 위험도 · 요약 · 추정 원인 · 권장 조치          │
├─ 분석 이력 ────────────────────────────────────────┤
└────────────────────────────────────────────────────┘
```

### 7.2 ⚠️ 사용자가 반드시 알아야 하는 3가지

1. **잘림(truncated)** — 로그는 상한이 있어 전부 보이지 않을 수 있습니다.
   "일부만 분석했다" 를 표시하지 않으면 사용자가 결론을 과신합니다.
2. **분석 실패도 200** — `succeeded` 를 확인해야 합니다.
3. **공급자 미등록** — 분석을 누르기 전에 미리 안내합니다.

### 7.3 `datetime-local` → ISO 변환 함정

`datetime-local` 은 타임존이 없는 문자열(`2026-09-19T08:00`)이라 브라우저가
**로컬 시간으로 해석**합니다. 그대로 보내면 서버(UTC 저장)와 어긋나 조회 결과가
밀립니다.

또한 **종료 시각은 그 날의 끝(23:59:59.999)으로 올려야** 합니다. 사용자가 날짜만
고르면 시각이 00:00 이 되어 하루 전체를 포함하지 못합니다.

### 7.4 프리셋 버튼

주소 형식을 틀려 404 가 나는 실수를 줄이기 위해 자주 쓰는 조합을 한 번에
채웁니다. 로컬 모델(Ollama/vLLM)을 고르면 타임아웃을 300초로 올려 줍니다.

---

## 8. 검증 결과 (2026-09-19, 실측)

### 8.1 단위/통합 테스트

| 대상 | 결과 |
| --- | --- |
| 백엔드 `./mvnw clean test` | **106/106 통과** |
| 신규 `LogNormalizerTest` | 15건 (심각도·타임스탬프·키워드 경계) |
| 신규 `OpenAiCompatibleClientTest` | 14건 (URL 조립·설정 검증) |
| 신규 `AiProviderServiceTest` | 15건 (저장 규칙·키 암호화·기본 플래그) |
| 프론트 `tsc -b` / `npm run build` | 오류 0 / 성공 |

### 8.2 실서버 E2E (포트 3300, H2 분리, mock OpenAI 공급자)

mock 공급자를 로컬(8899)에 띄워 **실제 HTTP 왕복**을 검증했습니다.

| 단계 | 실측 결과 |
| --- | --- |
| 공급자 등록 | `has_api_key=true`, `api_key_masked="********"` |
| 키 평문 노출 검사 | 응답에 평문 없음 ✅ |
| 키 암호화 | DB 값에 평문 미포함 ✅ |
| 연결 확인 | `ok=true`, 사용 가능한 모델 2개 (115ms) |
| 로그 적재 (8줄) | `inserted=8` |
| 심각도 정규화 | `critical 2`(OSPF) / `error 3`×3 / `warning 4` / `notice 5`×2 |
| 심각도 필터 | `warning`→5건, `error`→4건, `critical`→1건 (**방향 정확**) |
| 중복 수집 | `inserted=0, duplicated=1`, `repeat_count=2` |
| 표시/메모 | `highlighted_only=true` → 1건 |
| 날짜 필터 | 오늘 9건 / 2020년 0건 |
| **AI 분석 (필터 전체)** | `succeeded=true`, `HIGH`, 로그 5줄 사용, 17ms |
| **AI 분석 (선택)** | `scope=selected`, 로그 1줄 |
| 프롬프트 검증 | mock 로그에 `log_lines=5`, `auth=Bearer sk-test-…` |
| 실패: 빈 필터 | `succeeded=false`, "분석할 로그가 없습니다…" |
| 실패: 미도달 공급자 | `succeeded=false`, "연결 거부: …" |
| **실패도 이력 저장** | 이력에 `[FAIL]` 2건 표시 ✅ |
| **키 유지** | 빈 키로 수정 후에도 `has_api_key=true`, 연결 성공 ✅ |

### 8.3 Agent → 서버 로그 경로

프로버 없이 raw WebSocket 으로 텔레메트리 봉투를 보내 검증했습니다.

```
[mock-llm] model=mock-model log_lines=5 auth=Bearer sk-test-secre
```

- `log_status.entries[].raw` 형태 3줄 → `error/warning/notice` 로 정규화 ✅

### 8.4 브라우저 검증 (실조작)

| 요소 | 실측 |
| --- | --- |
| 화면 섹션 | 필터·목록·AI 분석·이력 모두 렌더링 |
| 로그 목록 | 7행, 심각도 배지·메시지 ID·반복 배지 표시 |
| 선택 → 분석 | `7건 선택` → `선택한 7건 분석` |
| 분석 결과 | `위험도 HIGH`, 요약/추정 원인/권장 조치, `AIA-0D3F7EBD`, `로그 7줄 사용`, `0.2초` |
| 공급자 드롭다운 | 기본 공급자 자동 선택 |
| 공급자 설정 | `기본`/`사용 중`/`Key 등록됨` 배지, 연결 확인 결과 표시 |
| 키 노출 | 화면에 평문 키 없음 ✅ |

---

## 9. 구현 중 발견한 버그 (재발 주의)

### 9.1 ⚠️ 부분 수정이 기본 공급자 플래그를 지운다

**증상**: 분석이 엉뚱한 공급자로 요청을 보냅니다.
(E2E 에서 `Bad Provider` 로 가 "연결 거부" 발생)

**원인**: 수정 화면이 일부 필드만 보냈을 때 서비스가 플래그를 `false` 로
덮어써 **기본 공급자가 하나도 없게** 됩니다. 그러면 `resolve()` 가
알파벳순 첫 공급자를 골랐습니다.

**수정**: `is_default` 를 **명시적으로 보냈을 때만** 변경합니다.
`null` 이면 기존 값을 유지합니다.

**추가 조치**: 엔티티 초기값을 `false` → `null` 로 바꿨습니다.
`false` 로 두면 **"값을 주지 않음" 과 "명시적 해제" 를 구분할 수 없어**
"첫 공급자는 자동으로 기본" 규칙을 적용할 수 없습니다.

### 9.2 ⚠️ `@DataJpaTest` 에서 `new SecretCipher("")` 는 실패한다

`SecretCipher` 는 `@PostConstruct` 에서 키를 준비합니다. 테스트에서 직접
생성하면 초기화가 실행되지 않아 `InvalidKeyException: No installed provider
supports this key: (null)` 이 납니다.

→ `@Import({SecretCipher.class, …})` 로 Spring 이 빈을 만들게 합니다.
실제 운영과 같은 경로를 검증하게 되는 부수 효과도 있습니다.

### 9.3 `new Date(datetimeLocalValue)` 의 타임존

프론트에서 `datetime-local` 값을 그대로 `new Date()` 하면 로컬 시간으로
해석됩니다. 서버는 UTC 로 저장하므로 그대로 보내면 조회가 밀립니다.
→ `toISOString()` 으로 변환하고, 종료 시각은 그 날의 끝으로 올립니다.

---

## 10. 운영 절차

### 10.1 로컬 Ollama 로 시작하기

```bash
# 1) Ollama 실행 및 모델 내려받기
ollama serve
ollama pull llama3.1:8b

# 2) 화면에서 공급자 추가
#    빠른 선택에서 "Ollama(로컬)" 을 누르면 주소/타임아웃이 채워집니다.
#      Base URL : http://localhost:11434/v1
#      Model    : llama3.1:8b
#      API Key  : (비워 둠)
```

> ⚠️ 서버가 도커 컨테이너에서 돌면 `localhost` 는 컨테이너 자신입니다.
> 호스트 주소(`http://host.docker.internal:11434/v1` 또는 실제 IP)를 쓰세요.

### 10.2 API 키 암호화 키 (운영 필수)

```bash
openssl rand -base64 32
# 출력값을 SONAR_SECRET_KEY 로 주입
```

키를 주입하지 않으면 **임시 키**를 쓰므로, 서버를 재시작하면 저장한 API Key 를
복호화하지 못합니다. (401 이 나고 원인을 찾기 어렵습니다)

### 10.3 로그 수집 경로 선택

| 상황 | 방법 |
| --- | --- |
| 프로버가 정상 연결 | `telemetry` payload 에 `log_status` 포함 → 자동 적재 |
| 장비에 접속 불가 | 로그 파일을 `POST /api/v1/logs/upload` 로 업로드 |
| 소량/테스트 | 화면의 "로그 직접 입력" 에 붙여넣기 |

> ⚠️ 텔레메트리 경로는 **한 번에 500줄**까지만 적재합니다.
> (30초 주기로 2000줄씩 받으면 하루 576만 건이 되어 DB 가 가득 참)
> 초과분은 버리되 **경고 로그**를 남깁니다.

### 10.4 로그 폭증 방지

로그는 계속 쌓입니다. 필요하면 오래된 로그를 정리하세요.

```sql
-- 90일 이전 로그 삭제 (Repository.findByLoggedAtBefore 활용)
```

---

## 11. 다음 단계 (미구현)

| 항목 | 설명 |
| --- | --- |
| 로그 자동 정리 스케줄러 | `findByLoggedAtBefore` 는 구현됨. 주기 실행 미연결 |
| syslog 수신 서버 | 현재는 프로버 경유 / 파일 업로드 / 직접 입력 |
| 분석 결과 내보내기 | PDF/CSV 로 보고서화 |
| 프롬프트 템플릿 관리 | 화면에서 시스템 프롬프트를 여러 개 두고 선택 |
| 스트리밍 응답 | 로컬 대형 모델은 수십 초가 걸려 진행 표시가 필요 |
| 비용 집계 | `usage` 토큰 수를 DB 에 남기고 공급자별 집계 |

---

## 12. 파일 인벤토리

### Backend

```
src/main/java/.../
├── Model/entity/AiProvider.java          # 신규 (AI 공급자)
├── Model/entity/DeviceLog.java           # 신규 (로그)
├── Model/entity/LogAnalysis.java         # 신규 (분석 결과)
├── Repository/AiProviderRepository.java  # 신규
├── Repository/DeviceLogRepository.java   # 신규 (필터 조합 쿼리)
├── Repository/LogAnalysisRepository.java # 신규
├── Service/ai/OpenAiCompatibleClient.java # 신규 (URL 조립·인증·오류 해석)
├── Service/ai/AiProviderService.java      # 신규 (설정 저장·키 암호화)
├── Service/ai/LogAnalysisEngine.java      # 신규 (프롬프트·파싱·저장)
├── Service/log/LogNormalizer.java         # 신규 (벤더별 정규화)
├── Service/log/LogService.java            # 신규 (수집·조회·필터)
├── Controller/AiProviderController.java   # 신규
├── Controller/LogController.java          # 신규
└── Service/AgentMessageRouterService.java # 변경 (텔레메트리 로그 적재)

src/test/java/.../
├── LogNormalizerTest.java                 # 신규 (15건)
├── OpenAiCompatibleClientTest.java        # 신규 (14건)
└── AiProviderServiceTest.java             # 신규 (15건)
```

### Frontend

```
src/
├── lib/api/aiLogs.ts                       # 신규 (API + 표시 유틸)
├── components/ai/AiProviderSettings.tsx    # 신규 (공급자 설정)
├── pages/LogManagement.tsx                 # 신규 (로그 + 필터 + 분석)
├── App.tsx                                 # 변경 (/log 라우트)
└── layout/AppSidebar.tsx                   # 변경 (AI Provider 메뉴)
```
