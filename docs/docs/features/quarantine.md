# 격리와 관리 경로 보존

현재 API는 QuarantineController의 Agent별 격리·해제·상태와 by-node 격리·해제입니다.
Backend의 QuarantineService·전략 계층과 Prober의 quarantine_handler가 장비별 동작을 담당합니다.

공통 envelope는 node와 connection 범위를 구분합니다. connection 범위는 관련 연결 규칙으로 처리하며 무관한 인터페이스 전체를 내리지 않도록 보호합니다.
관리망 prefix와 실제 관리 인터페이스는 격리 전에 확인해야 합니다. 관리 경로가 끊기면 해제 명령도 도달하지 못합니다.

사용자 의도·서버 상태·Agent 응답·장비 실제 상태를 함께 확인합니다. 이번 Lab 1에서는 격리를 실행하지 않았습니다.
과거 실제 인터페이스 격리가 생략된 시험을 현재 성공으로 표시하지 않습니다. [시험 근거](../testing/evidence.md)를 참조합니다.
