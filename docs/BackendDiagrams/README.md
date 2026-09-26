# Backend 다이어그램 문서

이 폴더는 **프론트엔드-백엔드 연결**과 **망분리 검증(BDD)** 구현을 설명하는
다이어그램 문서 모음입니다.

## 문서 목록

| 파일 | 내용 |
| --- | --- |
| `../Backend/Backend_Diagram.md` | **백엔드 전체 아키텍처 v1.0** — 계층, 격리 클래스/시퀀스, 테스트 현황 |
| `01-frontend-backend-connection.md` | 프론트엔드와 백엔드가 어떻게 연결되는가 (REST, CORS, 포트) |
| `02-project-editor-sequence.md` | 프로젝트 편집 → 저장 → 검증 시퀀스 다이어그램 |
| `03-project-editor-classes.md` | 프로젝트 편집 기능의 클래스 다이어그램 |
| `04-segmentation-bdd.md` | 망분리 검증 BDD 엔진 원리와 클래스 구조 |
| `05-policy-validation-sequence.md` | 정책 위반 조회/푸시 시퀀스 다이어그램 |
| `06-bdd-vs-batfish.md` | Batfish 와의 비교 및 설계 근거 |

> **v1.0 (2026-09-25)** — 격리(quarantine)가 추가되었습니다.
> Agent 측 다이어그램은 `../Agent/Agent_Architecture_Diagrams.md` §2.1/§3.1,
> 백엔드는 `../Backend/Backend_Diagram.md` §2/§6 을 보세요.

## 다이어그램 검증

문서의 Mermaid 블록이 실제로 파싱되는지 확인하는 스크립트가 있습니다.

```bash
cd docs
node check-mermaid.mjs \
  Backend/Backend_Diagram.md Agent/Agent_Architecture_Diagrams.md BackendDiagrams/*.md
```

> ⚠️ 순수 Node 에서는 `DOMPurify.sanitize is not a function` 이 뜹니다.
> mermaid 가 **문법 파싱을 마친 뒤** 라벨을 정화할 때 브라우저 DOM 을
> 요구하기 때문입니다. 스크립트는 이 메시지를 "문법은 통과" 로 처리하고,
> 진짜 오류(`Parse error on line N`)만 실패로 셉니다.

## 한 줄 요약

```
프론트엔드 (React)                 백엔드 (Spring Boot)              Agent (C++ Prober)
     |                                    |                                  |
     |--- REST /api/v1/projects --------->|                                  |
     |<-- 프로젝트/위반/토폴로지 ----------|                                  |
     |                                    |<--- WebSocket /api/v1/telemetry --|
     |                                    |--- policy-response -------------->|
     |                                    |--- command (격리) --------------->|
     |                                    |<-- ack {ok, affected, preserved} -|
     |--- POST /api/v1/quarantine ------->|                                  |
     |      망분리 위반 판정은 서버가 BDD 로 수행 (프론트는 표시만)             |
```

## 핵심 원칙

1. **위반 판정은 서버가 한다.** 프론트엔드 검증은 즉시 피드백용이며,
   최종 판정은 서버의 BDD 연산 결과입니다.
2. **프론트엔드는 벤더를 모른다.** 서버가 중립 구조(`NeutralDeviceConfig`)로
   변환한 뒤 내려줍니다.
3. **실패는 조용하지 않게 한다.** 형식이 잘못된 값은 추측하지 않고 비워 두고,
   검증에서 MINOR 로 보고합니다.
4. **판정은 자동, 조치는 수동.** 격리 버튼은 사람이 누릅니다. 오탐 한 번으로
   정상 장비를 끊으면 서비스가 마비됩니다.
5. **거짓 성공을 말하지 않는다.** 격리 응답의 `delivered`(보냈나)와
   `applied`(적용됐나)를 구분합니다. 둘이 다르면 화면에서 즉시 드러납니다.
