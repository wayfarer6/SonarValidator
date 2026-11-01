# DB 프로필과 영속성

기준: Backend src/main/resources의 application.properties, application-local.yml, application-postgres.yml.
현재 기본 프로필은 **local**입니다. postgres 파일의 기본 프로필이라는 과거 주석과 구분합니다.

| 프로필 | 저장 위치·동작 |
| --- | --- |
| local | H2 `jdbc:h2:file:./data/sonarvalidator;AUTO_SERVER=TRUE`, ddl-auto update |
| postgres | DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD로 접속, 기본 ddl-auto validate |

H2 경로는 Backend 실행 디렉터리를 기준으로 합니다. 다른 디렉터리에서 실행하면 다른 DB를 만들어 기존 프로젝트가 사라진 것처럼 보일 수 있습니다.
Lab 1은 SonarValidator_Backend/data/sonarvalidator.mv.db의 기존 PRJ-86F69C4D를 사용합니다.
DB 초기화·삭제나 프로젝트 재생성 없이 배포합니다.

SONAR_SECRET_KEY는 OPNsense 등 DB 자격증명의 암호화 키이며 재시작 때 동일하게 유지합니다. 32-byte 키를 Base64로 인코딩한 값을 설정해야 하며 누락·형식·길이 오류가 있으면 Backend는 시작하지 않습니다. 기존 DB가 있으면 기존 키를 복원하고 새 키로 대체하지 마세요.
스키마의 기준은 Model/entity와 배포 DB의 실제 구조입니다. 별도 버전의 수동 ERD를 함께 유지하지 않습니다.

## 네트워크 역할 스키마

`project_subnet.network_role`은 nullable VARCHAR(30)입니다. local의 ddl-auto update는 컬럼을 생성합니다. PostgreSQL validate 배포에서는 백업 후 `SonarValidator_Backend/src/main/resources/db/manual-migrations/network_role.sql`을 수동 실행한 뒤 서비스를 시작합니다. 자동 마이그레이션 도구가 실행하는 파일은 아닙니다.
