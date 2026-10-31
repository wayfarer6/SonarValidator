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

public final class StubRepository {

    public static final Object UNHANDLED = new Object();

    private StubRepository() {
    }

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
