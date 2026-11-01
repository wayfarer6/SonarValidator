# CI

Backend는 Java 26과 Maven Wrapper 테스트, Frontend는 TypeScript·ESLint·Vite 빌드를 검증합니다. Prober는 저장소에 포함된 ANTLR 4.13.2 C++ 소스와 생성 파서를 함께 빌드합니다.

`.github/workflows/prober.yml`은 일반/전체 정적 링크의 두 구성을 검사합니다. ANTLR 다운로드·외부 설치·Homebrew 캐시 단계는 없습니다. 일반 빌드 중 `brew`나 `java`를 호출하면 도구 guard가 검사를 실패시킵니다. 라이선스 사본도 원본과 비교합니다. 전체 CTest 결과는 구성별 JUnit 아티팩트로 남깁니다.

문법 재생성 도구 다운로드는 명시적 `fetch_antlr_tool` 타깃에서만 수행합니다. 현재 작업에서는 workflow 파일을 수정했으며 GitHub의 실제 실행·푸시는 수행하지 않습니다.
