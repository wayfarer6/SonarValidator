# SonarValidator

네트워크 장비의 실제 설정을 수집하고 의도한 망분리 정책과 비교하는 도구입니다.

| 구성 | 역할 | 기술 |
| --- | --- | --- |
| Prober | 장비 명령 수집·구조화·전송 | C++23, ANTLR4, SQLite |
| Backend | 프로젝트·정책·검증·API | Java 26, Spring Boot, H2/PostgreSQL |
| Frontend | 배포·서브넷·연결·검증 UI | React, TypeScript, Vite |
| Docs | 현재 구조와 운영 안내 | Docusaurus |

[문서 안내](docs/docs/intro.md)에서 시작합니다. 날짜별 중복 설계·시험 보고서는 기능별 안내와 검증 근거로 통합했습니다.

- [아키텍처](docs/docs/architecture/overview.md)
- [Lab 1 SSH 배포와 접근](docs/docs/deployment/lab1.md)
- [정책과 허용 연결](docs/docs/policy/overview.md)
- [UI 검토](docs/docs/frontend/lab1-review.md)
- [빌드](docs/docs/development/prober-build-debug.md), [CI](docs/docs/development/ci.md)
- [시험 근거](docs/docs/testing/evidence.md)

## 실행과 검증

Backend는 SonarValidator_Backend에서 Maven Wrapper의 spring-boot:run을 사용합니다. 기본 local 프로필은 실행 디렉터리의 H2 파일을 사용합니다.
Frontend는 SonarValidator_Frontend에서 npm run dev로 실행합니다. 기본 포트는 Backend 3000, Frontend 5173입니다.
문서 서버는 docs에서 npm start -- --port 3001로 실행해 포트 충돌을 피합니다.

기존 데이터·환경변수·포트부터 확인합니다. Lab 1의 실행 경로와 환경은 배포 안내를 따릅니다.
dev.sh는 Prober 빌드·스테이징도 수행하므로 대상 OS용 정적 바이너리를 준비한 경우 스테이징 덮어쓰기를 주의합니다.

Prober는 재사용 core 라이브러리와 CTest로 빌드·검증합니다. `SONAR_BUILD_TESTS`는 기본 ON이며 CI도 같은 테스트를 실행합니다. `installer_bundle`은 검증한 바이너리를 Installer에 복사합니다. 바이너리는 Git 대신 릴리스 자산으로 배포합니다.
[장비 확장 UML](docs/docs/architecture/device-extensions.md)과 [단일 JSON 출력](docs/docs/features/offline-export.md)을 기준으로 사용합니다.

## 라이선스

[LICENSE](LICENSE)
