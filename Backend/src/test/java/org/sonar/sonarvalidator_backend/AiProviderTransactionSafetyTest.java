package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonar.sonarvalidator_backend.Model.entity.AiProvider;
import org.sonar.sonarvalidator_backend.Repository.AiProviderRepository;
import org.sonar.sonarvalidator_backend.Service.ai.AiProviderService;
import org.sonar.sonarvalidator_backend.Service.ai.OpenAiCompatibleClient;
import org.sonar.sonarvalidator_backend.Service.secret.SecretCipher;
import org.sonar.sonarvalidator_backend.support.StubRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 공급자 조회가 <b>트랜잭션을 오염시키지 않는지</b> 검증합니다.
 *
 * <h2>🐛 왜 이 테스트가 필요한가 (실측 E2E 에서 발견한 결함)</h2>
 * <p>로그 분석 / 정책 조언 서비스는 {@code @Transactional} 이고, 호출 실패를
 * <b>정상 응답으로 알려주는</b> 것이 계약입니다({@code succeeded=false} + 200).
 *
 * <p>그런데 그 안에서 {@code providerService.resolve()} 를 부르는데, 이것도
 * 같은 트랜잭션에 참여했습니다. 공급자가 하나도 없으면 {@code resolve()} 가
 * {@code IllegalStateException} 을 던지고, Spring 은 <b>그 트랜잭션을
 * {@code rollback-only} 로 마킹</b>합니다.
 *
 * <p>호출자가 예외를 잡아 실패를 기록해도 마킹은 되돌릴 수 없습니다. 결과:
 * <pre>
 *   POST /api/v1/logs/analyze        → 500 UnexpectedRollbackException
 *   POST /api/v1/policy/advice/{id}  → 500 UnexpectedRollbackException
 *
 *   기대: 200 { "succeeded": false,
 *              "error_message": "사용 가능한 AI 공급자가 없습니다…" }
 * </pre>
 *
 * <p>즉 <b>친절한 안내가 500 으로 나가고, 실패 기록도 롤백되어 사라집니다.</b>
 * AI 공급자를 아직 등록하지 않은 <b>모든 신규 설치</b>가 이 경로를 밟습니다.
 *
 * <h2>고친 방법</h2>
 * <p>조회는 트랜잭션에 <b>참여하지 않게</b> 했습니다
 * ({@code Propagation.NOT_SUPPORTED}). 그러면 마킹할 트랜잭션 자체가 없어
 * 문제가 사라집니다. 또한 호출자를 위해 <b>예외 없는 조회</b>
 * ({@link AiProviderService#resolveOrEmpty}) 를 새로 두었습니다 —
 * 예외를 아예 만들지 않는 편이 더 견고합니다.
 *
 * <h2>⚠️ 왜 트랜잭션 전파를 <b>리플렉션</b>으로 검증하는가</h2>
 * <p>이 결함은 <b>런타임 프록시 동작</b>이라 단위 테스트에서 재현하기
 * 어렵습니다(실제 트랜잭션 매니저와 프록시가 필요). 대신 <b>결함을 만든
 * 설정값</b>(전파 수준)을 직접 확인합니다. 전파가 {@code REQUIRED} 로
 * 되돌아가면 이 테스트가 즉시 실패하므로, 회귀가 조용히 들어오지 못합니다.
 */
class AiProviderTransactionSafetyTest {

    /**
     * 조회 메서드가 트랜잭션에 참여하지 않는지 확인합니다.
     *
     * <p>참여하면 {@link Propagation#REQUIRED} 이고, 그 경우 호출자의 트랜잭션이
     * 롤백 전용으로 마킹됩니다.
     */
    @Test
    @DisplayName("공급자 조회는 호출자 트랜잭션에 참여하지 않는다 (롤백 전용 전파 차단)")
    void lookupDoesNotJoinCallerTransaction() throws NoSuchMethodException {

        for (final String methodName : List.of("resolve", "resolveOrEmpty")) {
            final var method = AiProviderService.class.getMethod(methodName, Long.class);
            final Transactional annotation = method.getAnnotation(Transactional.class);

            assertNotNull(annotation, methodName + " 에 @Transactional 이 있어야 합니다");

            // ⚠️ REQUIRED(기본값) 면 호출자 트랜잭션에 참여해 rollback-only 마킹이
            //    전파됩니다. NOT_SUPPORTED 여야 안전합니다.
            assertEquals(Propagation.NOT_SUPPORTED, annotation.propagation(),
                    methodName + " 은 NOT_SUPPORTED 여야 합니다 — 참여하면 호출자가 "
                            + "예외를 잡아도 트랜잭션이 롤백 전용이 되어 500 이 나갑니다");
        }
    }

    @Test
    @DisplayName("공급자가 없으면 resolveOrEmpty 는 예외 없이 빈 값을 돌려준다")
    void resolveOrEmptyNeverThrows() {
        final var service = serviceWith(Optional.empty());

        final Optional<AiProvider> result = service.resolveOrEmpty(null);

        assertNotNull(result, "빈 Optional 이라도 null 을 돌려주면 안 됩니다");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("지정한 공급자가 없어도 resolveOrEmpty 는 예외를 던지지 않는다")
    void resolveOrEmptyWithMissingIdNeverThrows() {
        final var service = serviceWith(Optional.empty());

        // 관리자 경로(resolve)는 예외를 던지지만, 분석 경로는 던지면 안 됩니다.
        assertTrue(service.resolveOrEmpty(999L).isEmpty());
    }

    @Test
    @DisplayName("관리자 경로(resolve)는 사유가 담긴 예외를 그대로 던진다")
    void resolveStillThrowsWithReason() {
        final var service = serviceWith(Optional.empty());

        final IllegalStateException absent = assertThrows(
                IllegalStateException.class, () -> service.resolve(null));
        assertTrue(absent.getMessage().contains("사용 가능한 AI 공급자가 없습니다"));
        // 사유가 있어야 운영자가 무엇을 고칠지 압니다.
        assertTrue(absent.getMessage().contains("등록"),
                "무엇을 해야 하는지 알려야 합니다");

        final IllegalStateException missing = assertThrows(
                IllegalStateException.class, () -> service.resolve(999L));
        assertTrue(missing.getMessage().contains("999"));
    }

    @Test
    @DisplayName("공급자가 있으면 그대로 돌려준다")
    void returnsProviderWhenPresent() {
        final AiProvider provider = new AiProvider();
        provider.setName("테스트 공급자");
        provider.setModel("test-model");
        final var service = serviceWith(Optional.of(provider));

        // ⚠️ ID 를 명시합니다. ID 없이 부르면 자동 선택 경로(기본 → 사용 중 첫 개)를
        //    타는데, 스텁은 그 두 조회를 항상 빈 값으로 돌려주므로 "없음" 이 됩니다.
        final Optional<AiProvider> result = service.resolveOrEmpty(1L);

        assertFalse(result.isEmpty());
        assertEquals("테스트 공급자", result.get().getName());
    }

    /**
     * 지정한 조회 결과를 돌려주는 저장소를 가진 서비스를 만듭니다.
     *
     * <p>{@code JpaRepository} 를 직접 구현하면 120개 넘는 메서드를 채워야 하고
     * Spring Data 버전이 올라갈 때마다 관계없는 컴파일 실패가 납니다. 그래서
     * 동적 프록시 스텁({@link StubRepository})을 씁니다.
     *
     * @param byId 지정 공급자 조회 결과 (없으면 빈 값)
     * @return 서비스
     */
    private AiProviderService serviceWith(Optional<AiProvider> byId) {
        final AiProviderRepository repository = StubRepository.of(
                AiProviderRepository.class,
                (method, args) -> switch (method) {
                    // 지정 공급자 조회 — 테스트가 통제하는 유일한 축입니다.
                    case "findById" -> byId;
                    // 기본/사용 공급자 조회는 항상 비어 있어야 "공급자 없음" 경로가 됩니다.
                    case "findByIsDefaultTrue", "findByEnabledTrueOrderByNameAsc" -> List.of();
                    default -> StubRepository.UNHANDLED;
                });

        return new AiProviderService(repository, new SecretCipher(""),
                new OpenAiCompatibleClient());
    }
}