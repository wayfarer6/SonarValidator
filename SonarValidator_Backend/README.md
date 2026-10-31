# SonarValidator Backend

Java 26·Spring Boot 서버입니다. REST Controller, Service, 영속 엔티티·Repository, Policy 검증·장비 전략, WebSocket 처리로 구성됩니다.

- [현재 구조](../docs/docs/architecture/overview.md)
- [DB 프로필·영속성](../docs/docs/deployment/database.md)
- [통신 계약](../docs/docs/protocol/overview.md)
- [정책 모델](../docs/docs/policy/overview.md), [BDD](../docs/docs/policy/bdd.md)
- [OPNsense](../docs/docs/deployment/opnsense-api.md)
- [실제 Lab 1 실행 환경](../docs/docs/deployment/lab1.md)

기본 프로필은 local입니다. 이 디렉터리에서 Maven Wrapper의 spring-boot:run을 실행해야 기존 data/sonarvalidator H2 파일을 사용합니다.
검증은 Maven Wrapper의 clean test를 기준으로 합니다. 이번 배포에서 전체 회귀 테스트를 다시 실행한 것으로 간주하지 않습니다.
