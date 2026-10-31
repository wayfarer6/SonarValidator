# Prober lifecycle regression tests

Run `./tests/prober/run.sh` in WSL/Linux with g++, SQLite development headers and nlohmann/json installed. Build artifacts use a temporary directory removed at exit. This runner leaves the existing CMake configuration unchanged.

- DB events: no worker thread, callback on the producing thread, 80 concurrent writes, close/reopen durability and rejected writes after close.
- Telemetry store: parsed NIC/routes/VLAN/trunk/ARP data and a deliberately rejected row that rolls back the batch.
- PTY lifecycle: input/output, resize, EOF, inherited descriptor isolation, 30 open/close cycles, background job termination, exited shell with a background PTY holder, input backpressure and no remaining descriptors/direct children.

These are local component tests. Run Robot S03 and device reboot checks separately for the deployed static binary.

Cisco/EEM 회귀에는 `ios-cli`, `ios-snapshot`, `ios-snapshot-publisher.sh`가 포함됩니다. 헤더·캐시 우선·TTL·오류·동시 게시와 원본 시각 보존을 확인합니다. 실제 ManagementService 통합은 `./tests/prober/run-cisco-eem-service.sh /tmp/sonar-lab1-static-build`로 실행하며 CMake 빌드의 컴파일·링크 인자를 재사용합니다. 테스트의 dohost는 임시 가짜 실행 파일이며 실제 라우터 설정은 변경하지 않습니다.
