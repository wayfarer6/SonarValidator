#include <iostream>
#include <thread>
#include <print>

int num = 0;

void plus100000()
{
    for(int i=0; i < 10000; i++)
    {
        num++;
    }
}

int main()
{
    std::thread t1(plus100000); // data race condititon
    std::thread t2(plus100000); // data race condtition
    
    plus100000();
    plus100000();
    t1.join();
    t2.join();
    std::println("num: {}",num);
}