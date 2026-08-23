

// -O는 최적화 수준임

```bash
g++ thread.cpp -O3 --std=c++23 -o thread
```

- thread race condition 감지

```bash
g++ data_race.cpp -fsanitize=thread -O2 -g -std=c++23 -o data_race
./data_race
```