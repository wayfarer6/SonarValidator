#include <iostream>
#include <coroutine>
#include <exception>
#include <print>

// C++23 스타일의 깔끔한 제너레이터 정의 (동작 원리 집중형)
struct Generator {
    struct promise_type {
        int current_value;

        // C++23에서는 대입 연산이나 반환 구조가 더 매끄럽게 처리됩니다.
        Generator get_return_object() {
            return Generator{std::coroutine_handle<promise_type>::from_promise(*this)};
        }
        std::suspend_always initial_suspend() noexcept { return {}; }
        std::suspend_always final_suspend() noexcept { return {}; }
        std::suspend_always yield_value(int v) noexcept {
            current_value = v;
            return {};
        }
        void return_void() noexcept {}
        void unhandled_exception() { std::terminate(); }
    };

    std::coroutine_handle<promise_type> h;

    ~Generator() { if (h) h.destroy(); }

    // C++23에서는 RAII 패턴과 소멸자 처리가 더욱 안전해졌습니다.
    bool next() {
        if (h && !h.done()) {
            h.resume();
            return !h.done();
        }
        return false;
    }
    int value() const { return h.promise().current_value; }
};

// 코루틴 함수 (C++23 문법 구조)
Generator make_sequence(int start, int end) {
    for (int i = start; i <= end; ++i) {
        co_yield i; // 값을 던지고 대기
    }
}

Generator test(int start,int end) {
    for(int i = start; i <= end; ++i)
        co_yield i;
}

int main() {
    // C++23의 깔끔한 출력 방식 (std::println 사용!)
    std::println("=== C++23 코루틴 시작 ===");
    std::println("이건 혁신이야!");
    std::cout << "왜 이렇게 " << "힘들게" << "화살표 2개씩 넣어가며 해야해" <<std::endl;
    auto gen = make_sequence(1, 3);
    while (gen.next()) {
        std::println("받은 값: {}", gen.value());
    }

    std::println("=== 코루틴 종료 ===");
}