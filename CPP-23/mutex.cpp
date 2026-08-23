#include <iostream>
#include <thread>
#include <mutex>


struct MInt
{
    std::mutex mtx;
    int num = 0;
};


/* void plus1(MInt & mi)
{
    mi.mtx.lock();
    mi.num++;
    mi.mtx.unlock();
} */

// 생성될 때 무조건 락을 걸고(lock()), 소멸될 때 무조건 락을 풉니다(unlock()).
// 유연성: 없음. 중간에 수동으로 풀거나 다시 잠글 수 없습니다.
// 장점: 기능이 단순한 만큼 오버헤드가 적고 코드가 직관적입니다. 단순하게 스코프 안에서 뮤텍스를 보호할 때 가장 먼저 고려하는 표준적인 선택입니다.
void plus1(MInt & mi)
{
    std::lock_guard<std::mutex> lock(mi.mtx); // 자동으로 lock, 함수가 끝나면 자동 unlock
    mi.num++;
}

void plus2(MInt & mi)
{
    std::unique_lock<std::mutex> lock(mi.mtx);
    lock.lock();   // 필요할 때 직접 락 걸기
    mi.num++;
    lock.unlock(); // 필요할 때 직접 락 풀기

    // 락하는 코드 안너오도 됨 기본으로 해두는게 있어서
}

// 절대 두번 해제하거나 락하면 언디파인된 동작이라 위험!!!

// try 락하기 

void plus1_try_lock_version(MInt &mi)
{
    if(mi.mtx.try_lock()) {
       std::cout << "Lock이 걸림" << std::endl;
    }
    mi.num++;
    mi.mtx.unlock();
}

/*
td::unique_lock (무겁지만 강력하고 유연함)
특징: lock_guard가 가진 기본 기능에 다양한 제어 기능이 추가된 다목적 락입니다.

유연성: 매우 높음.

수동 제어: 원할 때 언제든 lock()과 unlock()을 직접 호출할 수 있습니다.

지연 잠금 (std::defer_lock): 생성할 때는 락을 안 걸고 대기하다가 나중에 필요할 때 걸 수 있습니다.

소유권 이전 (Move): 함수 간에 락의 소유권을 이동(Move)시킬 수 있습니다. (lock_guard는 소유권 이동이 불가능합니다.)

조건 변수 연동: std::condition_variable과 함께 쓰려면 반드시 unique_lock을 사용해야 합니다. (자세히 기다리려면 락을 풀었다가 다시 잠그는 과정이 필요하기 때문입니다.)

단점: lock_guard에 비해 약간의 메모리 오버헤드가 더 발생할 수 있습니다.

*/

int main()
{
    MInt mi;
    std::thread t1(plus1,std::ref(mi));
    std::thread t2(plus1,std::ref(mi));

    t1.join();
    t2.join();
    // mutex lock 없이 진행되었으면 아마 1로 나움? , 값자체가 예측이 불가능해짐
    std::cout << "num: " << mi.num << std::endl;
}