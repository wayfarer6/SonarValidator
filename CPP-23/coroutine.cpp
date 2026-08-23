#include <iostream>
#include <generator> // C++23
#include <vector>
#include <print>

// 첫 번째 코루틴 (예: 짝수를 순서대로 뱉음)
std::generator<int> function1() {
    int multiple = 1; // 코루틴이 처음 실행될 때 "딱 한 번만" 1로 초기화됨!
    
    while (true) {
        co_yield 2 * multiple; // 현재 multiple 값을 써서 뱉고 일시정지
        multiple++;            // 다음 깨어났을 때 1 증가!
    }
}

// 두 번째 코루틴 (예: 홀수를 순서대로 뱉음)
std::generator<int> function2() {
    int multiple2 = 1;

    while(true) {
        co_yield 1* multiple2 ;
        multiple2++;
    }
}

int main() {
    std::vector<int> result;

    // 각각의 제너레이터 객체 생성 (독립된 상태 유지)
    auto gen1 = function1();
    auto gen2 = function2();

    // 이터레이터를 이용해 번갈아가며 순차적으로 배열에 집어넣기
    // (실제로는 필요에 따라 반복문이나 알고리즘으로 조합 가능)
    auto it1 = gen1.begin();
    auto it2 = gen2.begin();

    // 예시로 둘 다 끝날 때까지 번갈아 가며 가져오기
    // while (it1 != gen1.end() || it2 != gen2.end()) {
    //     if (it1 != gen1.end()) {
    //         result.push_back(*it1);
    //         ++it1;
    //     }
    //     if (it2 != gen2.end()) {
    //         result.push_back(*it2);
    //         ++it2;
    //     }    
    //}

    for(int i = 1; i < 11; i++)
    {
        if(it1 != gen1.end())
        {
            result.push_back(*it1);
            ++it1;
        };
        if(it2 != gen2.end())
        {
            result.push_back(*it2);
            ++it2;
        };

    }

    // 결과 출력: 2, 1, 4, 3, 6, 5 순서로 들어가게 됨
    for (int val : result) {
        std::print("{} ", val);
    }
    std::println();
}

/*
코루틴 왜씀 그냥 번갈아 대입하는건데 

근데 함수 1과 2의 리턴 값이 굉장히 큰것들이라면 문제가 시작됨 

데이터 양이 적고 단순할 때: 코루틴은 닭 잡는 소입니다. 그냥 평범한 while문이나 반복문이 훨씬 단순하고 빠릅니다.

데이터가 거대하거나, 무한 스트림이거나, I/O 대기가 길어 연산을 쪼개야 할 때: 그때 비로소 코루틴의 진가가 발휘됩니다.

std::vector<int> function1() { return {2, 4, 6, ..., 1000000}; } // 대량 메모리 소모!
std::vector<int> function2() { return {1, 3, 5, ..., 1000000}; } // 대량 메모리 소모!

*/