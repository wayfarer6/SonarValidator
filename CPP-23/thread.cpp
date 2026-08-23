#include <iostream>
#include <vector>
#include <iomanip>
#include <chrono>
#include <print>
#include <thread>
void fn(std::vector<double>& nums, std::size_t beginIdx, std::size_t ednIdx)
{
      for(std::size_t idx=0; idx < ednIdx; ++ idx)
    {
        nums[idx]  *= 2.0;
        nums[idx]  += 1.0;
        nums[idx] *= static_cast<double>(idx);
    } 

}

int main()
{
    // constexpr 반드시 컴파일 타임에 값이 확정되는 진짜 상수를 의미
    constexpr std::size_t count = 100000000;
    std::vector<double> nums (count,1.0);

    auto start = std::chrono::steady_clock::now();
    //std::thread t1(fn,std::ref(nums),0,count/2);
    //std::thread t2 (fn,std::ref(nums),count/2,count);
    
    //t1.join();
    //t2.join();


    // std::jthread는 스코프를 벗어날 때(즉, main 함수가 끝날 때) 
    //join을 합니다. 하지만 finish 시간을 찍는 시점에는 스레드들이 
    // 백그라운드에서 열심히 일하고 있는 도중입니다.

    // 스코프를 달아야 jthread 객체가 소멸한 시점에 시간이 나옴
    {
        std::jthread j1(fn,std::ref(nums),0,count/2);
        std::jthread j2(fn,std::ref(nums),count/2,count);
    }

    auto finish = std::chrono::steady_clock::now();
    std::chrono::duration<double> duration = finish - start;
    // std::cout << "Time in seconds: " << duration.count() << std::endl;
    std::println("Time in seconds: {}",duration.count());

    std::cout << nums[count-1] << std::endl;
}