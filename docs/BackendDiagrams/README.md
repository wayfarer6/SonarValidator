# Backend 다이어그램 문서

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
