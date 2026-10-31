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

class AiProviderTransactionSafetyTest {

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
