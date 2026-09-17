---
sidebar_position: 2
---

# 데이터베이스 설정 (JPA)

## 1. 도입한 의존성

`pom.xml` 에 네 개를 추가했습니다.

| 의존성 | 스코프 | 역할 |
| --- | --- | --- |
| `spring-boot-starter-data-jpa` | compile | Spring Data JPA + Hibernate. `@Entity`/`JpaRepository` 를 씁니다. |
| `org.postgresql:postgresql` | runtime | PostgreSQL JDBC 드라이버. (BOM 이 버전 관리) |
| `com.h2database:h2` | runtime | 개발/테스트용 임베디드 DB. |
| `org.springframework.boot:spring-boot-h2console` | runtime | H2 웹 콘솔 (`/h2-console`). |
| `spring-boot-starter-data-jpa-test` | test | `@DataJpaTest` 등 JPA 테스트 슬라이스. |

:::warning Spring Boot 4 에서 달라진 점
Boot 3 까지는 `spring-boot-starter-data-jpa` 와 `h2` 만 있으면 H2 콘솔과
`@DataJpaTest` 가 바로 동작했습니다. **Boot 4 에서는 둘 다 별도 모듈로 분리** 되었습니다.

| 증상 | 필요한 모듈 |
| --- | --- |
| `/h2-console` 이 404 | `org.springframework.boot:spring-boot-h2console` |
| `package ...test.autoconfigure.orm.jpa does not exist` | `spring-boot-starter-data-jpa-test` |

또한 `@DataJpaTest` 의 패키지가 이동했습니다.

```java
// Boot 3
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

// Boot 4
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
```

드라이버 의존성이 없으면 기동 시
`Failed to determine a suitable driver class` 로 실패합니다.
:::

## 2. 프로필 구조

설정을 프로필로 분리해, DB 서버 유무에 따라 바로 전환할 수 있게 했습니다.

| 파일 | 프로필 | 용도 |
| --- | --- | --- |
| `application.properties` | - | 공통 설정 + 기본 프로필 지정 |
| `application-local.yml` | `local` | **기본값.** H2 파일 DB. 외부 DB 서버 불필요 |
| `application-postgres.yml` | `postgres` | PostgreSQL. 운영/통합 |
| `src/test/resources/application-test.yml` | `test` | H2 인메모리. 테스트 전용 |

`application.properties` 의 기본값:

```properties
spring.profiles.active=local
```

### 실행 방법

```bash
# H2 (기본) - 바로 기동됩니다
./mvnw spring-boot:run

# PostgreSQL
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres

# 환경 변수로 지정 (운영 배포)
SPRING_PROFILES_ACTIVE=postgres java -jar app.jar
```

:::danger 운영 배포 시 주의
기본 프로필이 `local`(H2) 이므로, 운영에서는 **반드시**
`SPRING_PROFILES_ACTIVE=postgres` 를 설정해야 합니다.
빠뜨리면 파일 기반 H2 를 사용하게 되어 데이터가 컨테이너에만 남습니다.
:::

## 3. PostgreSQL 설정 예제

`src/main/resources/application-postgres.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:sonarvalidator}
    driver-class-name: org.postgresql.Driver
    username: ${DB_USER:sonarvalidator}
    password: ${DB_PASSWORD:sonarvalidator}

    hikari:
      pool-name: sonarvalidator-pool
      maximum-pool-size: ${DB_POOL_MAX:10}
      minimum-idle: ${DB_POOL_MIN_IDLE:2}
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  jpa:
    hibernate:
      ddl-auto: ${JPA_DDL_AUTO:validate}
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
```

### 환경 변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DB_HOST` | `localhost` | DB 호스트 |
| `DB_PORT` | `5432` | DB 포트 |
| `DB_NAME` | `sonarvalidator` | 데이터베이스 이름 |
| `DB_USER` | `sonarvalidator` | 사용자 |
| `DB_PASSWORD` | `sonarvalidator` | 비밀번호 (**운영에서는 반드시 주입**) |
| `DB_POOL_MAX` | `10` | 최대 커넥션 수 |
| `DB_POOL_MIN_IDLE` | `2` | 최소 유휴 커넥션 |
| `JPA_DDL_AUTO` | `validate` | 스키마 처리 방식 |

:::warning 비밀번호를 파일에 저장하지 마세요
`application-postgres.yml` 의 `${DB_PASSWORD:sonarvalidator}` 는 **기본값** 입니다.
운영에서는 이 값을 쓰지 말고 환경 변수나 시크릿 관리 도구로 주입하세요.
기본값을 남겨두면 설정 누락 시 알려진 비밀번호로 접속을 시도하게 됩니다.
:::

### PostgreSQL 준비 (예시)

```sql
CREATE DATABASE sonarvalidator;
CREATE USER sonarvalidator WITH ENCRYPTED PASSWORD '변경할_비밀번호';
GRANT ALL PRIVILEGES ON DATABASE sonarvalidator TO sonarvalidator;
-- 스키마 생성 권한 (ddl-auto=update 를 쓸 때만 필요)
\c sonarvalidator
GRANT ALL ON SCHEMA public TO sonarvalidator;
```

## 4. H2 설정 예제 (개발용)

`src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/sonarvalidator;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false

  h2:
    console:
      enabled: true
      path: /h2-console
```

### H2 웹 콘솔 사용법

1. 애플리케이션을 실행합니다.
2. 브라우저에서 <http://localhost:3000/h2-console> 을 엽니다.
3. 로그인 화면에서 값을 채웁니다.

| 항목 | 값 |
| --- | --- |
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:file:./data/sonarvalidator;AUTO_SERVER=TRUE` |
| User Name | `sa` |
| Password | (비워둠) |

:::note AUTO_SERVER=TRUE 를 쓰는 이유
이 옵션이 없으면 H2 파일 DB 는 **한 프로세스만** 열 수 있습니다.
애플리케이션이 떠 있는 상태에서 콘솔이나 외부 도구로 같은 파일에 접속하려면
`AUTO_SERVER=TRUE` 가 필요합니다.
:::

### H2 모드 비교

| 모드 | URL | 특징 |
| --- | --- | --- |
| 파일 | `jdbc:h2:file:./data/sonarvalidator` | 재시작해도 데이터 유지. `local` 프로필 기본값 |
| 인메모리 | `jdbc:h2:mem:name` | 프로세스 종료 시 사라짐. 테스트에 적합 |

## 5. 스키마 관리 방식

| `ddl-auto` | 동작 | 사용 시점 |
| --- | --- | --- |
| `update` | 엔티티 변경을 테이블에 자동 반영 | 개발 (`local`) |
| `validate` | 엔티티와 실제 스키마 일치만 검증, 불일치 시 기동 실패 | 운영 (`postgres` 기본값) |
| `create-drop` | 기동 시 생성, 종료 시 삭제 | 테스트 (`test`) |
| `none` | 아무것도 하지 않음 | Flyway 등 외부 도구 사용 시 |

:::warning update 는 운영에서 쓰지 마세요
`update` 는 컬럼을 **추가** 만 하고 삭제/타입 변경은 하지 않습니다.
또한 변경 이력이 남지 않아, 운영 DB 와 엔티티가 조용히 어긋날 수 있습니다.
운영에서는 `validate` 로 두고 스키마 변경은 마이그레이션 도구로 관리하세요.
:::

## 6. 엔티티 작성 규칙

이 프로젝트에서 사용하는 형태입니다.

```java
@Entity
@Table(name = "opnsense_firewall")
@Getter
@Setter
@NoArgsConstructor          // JPA 스펙이 요구하는 기본 생성자
public class OPNSenseFirewall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false, length = 128)
    private String agentId;     // Java 는 camelCase, DB 는 snake_case
}
```

| 규칙 | 이유 |
| --- | --- |
| `@NoArgsConstructor` 필수 | JPA 가 리플렉션으로 인스턴스를 만듭니다. |
| `@Column(name = "...")` 명시 | Java 는 camelCase, DB 는 snake_case 관례를 명시적으로 연결합니다. |
| `nullable = false` 지정 | 스키마 제약이 생겨 잘못된 데이터가 조기에 드러납니다. |
| `length` 지정 | 기본값(255)이 의도와 다를 수 있습니다. |

## 7. 리포지토리 사용법

`JpaRepository` 를 상속하면 저장/조회/삭제가 자동 제공되고,
메서드 이름 규칙만으로 쿼리가 생성됩니다.

```java
@Repository
public interface OPNSenseFirewallRepository extends JpaRepository<OPNSenseFirewall, Long> {

    Optional<OPNSenseFirewall> findByAgentId(String agentId);

    boolean existsByAgentId(String agentId);

    List<OPNSenseFirewall> findByNameContainingIgnoreCase(String keyword);
}
```

이름 규칙 예시:

| 메서드 이름 | 생성되는 조건 |
| --- | --- |
| `findByAgentId` | `where agent_id = ?` |
| `findByAgentIdAndManagementIp` | `where agent_id = ? and management_ip = ?` |
| `findByNameContaining` | `where name like '%?%'` |
| `findByNameContainingIgnoreCase` | `where upper(name) like upper('%?%')` |
| `existsByAgentId` | 존재 여부 (`count` 기반) |
| `countByVersion` | 개수 |

## 8. 테스트

JPA 계층은 `@DataJpaTest` 로 검증합니다. 각 테스트가 트랜잭션으로 감싸져
자동 롤백되므로 테스트 간 간섭이 없습니다.

```java
@DataJpaTest
@ActiveProfiles("test")     // H2 인메모리
class OPNSenseFirewallRepositoryTest {

    @Autowired
    private OPNSenseFirewallRepository repository;

    @Test
    void savesAndFindsById() {
        OPNSenseFirewall saved = repository.save(new OPNSenseFirewall("fw-01", "DMZ"));
        assertNotNull(saved.getId());           // IDENTITY 로 id 생성
        assertTrue(repository.findById(saved.getId()).isPresent());
    }
}
```

`@DataJpaTest` 는 기본적으로 임베디드 DB 를 찾습니다. PostgreSQL 을 상대로
테스트하려면 `@AutoConfigureTestDatabase(replace = Replace.NONE)` 을 추가합니다.

## 9. 기동 검증 결과

| 확인 항목 | 결과 |
| --- | --- |
| `./mvnw clean test` | **12개 통과** (라우터 8, 리포지토리 3, 컨텍스트 1) |
| 기본 프로필 기동 (`local`) | H2 파일 DB, `/h2-console` 200 |
| `postgres` 프로필 | PostgreSQL 드라이버 42.7.13 로 접속 시도 확인 |
| JPA 테이블 생성 | `OPNSENSE_FIREWALL` (id, agent_id, name, management_ip, version) |
| WebSocket 경로 회귀 | 없음 (에이전트 통합 테스트 통과) |

## 10. 문제 해결

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| `Failed to determine a suitable driver class` | JDBC 드라이버 없음 | `postgresql` 또는 `h2` 의존성 추가 |
| `/h2-console` 404 | Boot 4 에서 콘솔 모듈 분리 | `spring-boot-h2console` 추가 |
| `package ...autoconfigure.orm.jpa does not exist` | `@DataJpaTest` 패키지 이동 | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `Connection to localhost:5432 refused` | PostgreSQL 서버 미실행 | 서버를 띄우거나 `local` 프로필 사용 |
| `Database is already in use` (H2) | 다른 프로세스가 파일 점유 | URL 에 `AUTO_SERVER=TRUE` 추가 |
| `Schema-validation: missing table` | `ddl-auto=validate` 인데 테이블 없음 | `JPA_DDL_AUTO=update` 로 한 번 생성 |
| 한글 데이터가 깨짐 | DB 인코딩 | PostgreSQL 은 기본 UTF-8. H2 는 기본 UTF-8 |
