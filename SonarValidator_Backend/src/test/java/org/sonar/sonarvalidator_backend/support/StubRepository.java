package org.sonar.sonarvalidator_backend.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Spring Data 저장소 인터페이스를 <b>동적 프록시</b>로 스텁 처리합니다.
 *
 * <h2>⚠️ 왜 직접 {@code implements} 하지 않는가</h2>
 * <p>{@code JpaRepository} 를 직접 구현하면 <b>120개가 넘는 메서드</b>를
 * 모두 채워야 합니다. 그중 실제로 쓰는 것은 2~3개입니다. 그러면
 *
 * <ul>
 *   <li>테스트 파일이 스텁 보일러플레이트로 뒤덮여 <b>검증하려는 계약이</b>
 *       눈에 띄지 않습니다.</li>
 *   <li>Spring Data 버전이 올라가 기본 메서드가 추가될 때마다
 *       <b>관계없는 테스트가 컴파일 실패</b>합니다.</li>
 * </ul>
 *
 * <p>프록시를 쓰면 <b>관심 있는 메서드만</b> 응답하고 나머지는 반환 타입에
 * 맞는 빈 값으로 자동 처리됩니다. 테스트가 검증 대상에만 집중합니다.
 *
 * <h2>사용 예</h2>
 * <pre>{@code
 * List<Notification> saved = new ArrayList<>();
 * NotificationRepository repo = StubRepository.of(NotificationRepository.class,
 *         (method, args) -> switch (method) {
 *             case "save" -> { Notification n = (Notification) args[0]; saved.add(n); yield n; }
 *             default -> StubRepository.UNHANDLED;
 *         });
 * }</pre>
 */
public final class StubRepository {

    /**
     * "이 메서드는 처리하지 않음" 을 뜻하는 표식입니다.
     *
     * <p>{@link #UNHANDLED} 를 돌려주면 반환 타입에 맞는 기본값
     * ({@code Optional.empty()}, 빈 {@code List}, {@code 0}, {@code false} …)이
     * 자동으로 나갑니다. {@code null} 을 돌려주는 것과 구분되어야 하므로
     * 별도 표식을 씁니다. ({@code null} 은 "정말 null" 을 뜻할 수 있습니다)
     */
    public static final Object UNHANDLED = new Object();

    private StubRepository() {
    }

    /**
     * 저장소 스텁을 만듭니다.
     *
     * @param repositoryInterface 저장소 인터페이스 (예: {@code NotificationRepository.class})
     * @param responder           메서드 이름과 인자를 받아 결과를 돌려주는 함수.
     *                            처리하지 않을 메서드는 {@link #UNHANDLED} 를 돌려줍니다.
     * @param <T>                 저장소 타입
     * @return 프록시 스텁
     */
    public static <T> T of(Class<T> repositoryInterface,
                           BiFunction<String, Object[], Object> responder) {
        final InvocationHandler handler = (proxy, method, args) -> {
            // equals/hashCode/toString 은 스텁이 아니라 프록시 자체의 계약입니다.
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "StubRepository(" + repositoryInterface.getSimpleName() + ")";
                };
            }

            final Object[] safeArgs = args == null ? new Object[0] : args;
            final Object result = responder.apply(method.getName(), safeArgs);
            if (result != UNHANDLED) {
                return result;
            }
            return defaultValue(method);
        };

        final Object proxy = Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class<?>[]{repositoryInterface},
                handler);
        return repositoryInterface.cast(proxy);
    }

    /**
     * 반환 타입에 맞는 "빈 값" 을 만듭니다.
     *
     * <p>프록시는 {@code null} 을 그냥 돌려주지만, 서비스 코드가
     * {@code repository.search(...)} 결과를 바로 스트림으로 돌리면
     * {@code null} 에서 NPE 가 납니다. 그래서 컬렉션/원시 타입은 빈 값으로
     * 맞춰 줍니다.
     *
     * @param method 스텁 처리할 메서드
     * @return 반환 타입에 맞는 빈 값
     */
    private static Object defaultValue(Method method) {
        final Class<?> type = method.getReturnType();

        if (type == Optional.class) {
            return Optional.empty();
        }
        if (type == List.class || type == Iterable.class || type == java.util.Collection.class) {
            return new ArrayList<>();
        }
        if (type == Page.class) {
            return Page.empty();
        }
        if (type == long.class || type == Long.class) {
            return 0L;
        }
        if (type == int.class || type == Integer.class) {
            return 0;
        }
        if (type == boolean.class || type == Boolean.class) {
            return false;
        }
        // 엔티티 단건 조회 등은 null 이 자연스럽습니다.
        return null;
    }
}