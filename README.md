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
dev.sh는 기존 스테이징 바이너리를 사용하고, 없을 때 Prober 정적 바이너리를 빌드합니다. GNS3용 이미지는 `./dev.sh --build-images`로 빌드합니다. [태그와 다른 PC로 이동하는 절차](docs/docs/deployment/gns3-images.md)를 참고하세요.

Prober는 재사용 core 라이브러리와 CTest로 빌드·검증합니다. `SONAR_BUILD_TESTS`는 기본 ON이며 CI도 같은 테스트를 실행합니다. `installer_bundle`은 검증한 바이너리를 Installer에 복사합니다. 바이너리는 Git 대신 릴리스 자산으로 배포합니다.
[장비 확장 UML](docs/docs/architecture/device-extensions.md)과 [단일 JSON 출력](docs/docs/features/offline-export.md)을 기준으로 사용합니다.

## 저장소 구조와 실행 진입점

```text
SonarValidator_Backend/   API와 정책 검증
SonarValidator_Frontend/  관리 UI
SonarValidator_Prober/    C++ 수집기와 Installer
  third_party/           버전이 고정된 외부 소스
  .devcontainer/         C++ 개발 환경
docker/
  gns3/                  Ubuntu·OVS·방화벽 Dockerfile과 공통 부팅 지원
  base/ backend/ frontend/  앱 스택 Dockerfile
tests/                   Robot·Lab 제어·Prober 계약 검사·발표 자료 도구
docs/                    기능별 문서 사이트
```

- 로컬 개발: `./dev.sh`
- GNS3 이미지: `./dev.sh --build-images`
- 앱 스택 이미지: `./dev.sh --build-images=stack`
- Prober 빌드·CTest: `./SonarValidator_Prober/build.sh`
- Lab 제어·이전 이미지 패키지 캐시: `python3 tests/labs/labctl.py --help`
- UI 시나리오와 녹화: `./tests/robot/run.sh`

`test-results`는 로컬 녹화·스크린샷·검증 산출물이며 소스 관리에서 제외합니다. Backend `target`, Frontend `dist`, CMake 빌드 디렉터리도 생성 결과입니다. 노드 부팅·배포에 사용하는 스크립트와 실제 테스트는 각 기능 폴더에 유지합니다.

## 라이선스

[LICENSE](LICENSE)
