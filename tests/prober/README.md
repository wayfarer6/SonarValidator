# Prober 회귀 검사

저장소 루트의 빌드 진입점 하나로 configure/build/전체 CTest를 실행합니다.

```sh
./SonarValidator_Prober/build.sh
```

이미 빌드한 디렉터리에서 특정 회귀만 실행할 수도 있습니다.

```sh
ctest --test-dir SonarValidator_Prober/build --output-on-failure -R 'database_service|telemetry_store|terminal_session|ios_'
```

DB 이벤트·트랜잭션, PTY 입출력·종료·자식 정리, IOS CLI·스냅샷·동시 게시, ManagementService/EEM 통합은 같은 CMake 테스트 목록에서 관리합니다. `ios-snapshot-publisher.sh`는 실제 Cisco 게시 스크립트의 원본 시각 보존·유효성·동시 게시를 검사하는 CTest 테스트입니다. dohost는 임시 가짜 명령으로 대체합니다.

실제 장비의 부팅·SSH·수집 검증은 Robot 시나리오를 사용합니다. 별도의 C++ 컴파일 래퍼는 두지 않습니다.
