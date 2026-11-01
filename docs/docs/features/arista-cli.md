# Arista CLI 수집과 제어

## 실행 계약

Arista는 로컬 EOS CLI 엔진을 `FastCli -p 15 -M -c <명령 묶음>`으로 실행합니다. `-p 15`는 시작 권한, `-M`은 페이징 비활성화, `-c`는 비대화형 명령 실행입니다. AAA를 끄는 `-A` 옵션은 사용하지 않습니다. SSH 접속 직후의 EOS 권한은 별개이므로 운영자가 Bash로 들어갈 때는 먼저 enable이 필요할 수 있습니다.

`arista_cli::BuildCommand`가 명령 배열을 줄 단위로 조립하고 shell quote합니다. 항목에 줄바꿈/NUL이 들어오면 거부하며, 정책의 인터페이스·ACL 이름과 VLAN 1~4094를 검사합니다. EOS 자체의 명령 오류도 종료 코드로 확인합니다.

`ManagementService.QueryAristaCli`는 명령이 완료되고 exit code가 0일 때만 출력 전체를 반환합니다. 오류·출력 한도 초과·타임아웃·중지 요청은 빈 결과로 처리합니다. 제한 시간은 20초, 출력 한도는 4MiB입니다. 각 호출의 프로세스와 자식은 공통 command runner가 정리합니다.

제어는 configure terminal → 대상 명령 → end를 같은 배치에 넣고 실제 실행 성공 여부를 반환합니다. 이전의 고정 300ms 출력 읽기, 비어 있지 않으면 성공으로 간주하기, 명령 간 설정 모드 공유는 제거했습니다. 사용자 웹 터미널의 PTY는 별도 기능으로 유지합니다.

## 수집 항목

| 명령 | 자료 |
| --- | --- |
| show vlan brief | VLAN과 소속 포트 |
| show ip interface brief | IP 인터페이스·주소·상태 |
| show ip route | 연결·정적·동적 경로 |
| show interfaces switchport | 포트 모드·access/native/trunk VLAN |
| show arp | ARP와 인터페이스 |

L2 switch의 IP 인터페이스가 Management1뿐인 것은 정상일 수 있습니다. access 포트는 switchport 결과에서 수집합니다. `Gateway of last resort is not set`의 not을 주소로 만들지 않도록 IP 주소를 검사합니다. 미설정 게이트웨이는 생략하고 실제 연결 경로는 유지합니다.

## 이벤트 처리 도입 기준

Arista EOS의 자동 실행 기능은 event-handler입니다. onboot나 인터페이스 변경 등에서 Bash 동작을 실행할 수 있습니다. Cisco IOS EEM 명령을 그대로 넣지 않습니다. [Arista 공식 CLI/event-handler 문서](https://www.arista.com/en/um-eos/eos-command-line-interface-cli)를 기준으로 합니다.

직접 CLI가 반복 실행·긴 출력·취소·오류 반환 검사에서 안정적이면 캐시 발행 데몬을 추가하지 않습니다. 직접 접근이 EOS 세션/권한 문제로 반복 실패하고 장비 내부 작업은 성공하는 경우에만, event-handler 또는 schedule 기반 수집·원자적 파일 게시·TTL 검사를 검토합니다. 이 경우에도 캐시 실패를 이전 값으로 숨기지 않아야 합니다. 이벤트 처리기를 넣는 것만으로 소비자 측 300ms 읽기나 성공 판정 오류가 해결되지는 않습니다.

## 검증

`arista_cli_test`는 300ms보다 느린 응답의 마지막 부분, nonzero exit의 부분 출력 거부, 인용부호/셸 치환 차단, 실행 중 stop, 제어 실패 전달과 잘못된 필드 거부를 검사합니다. 실제 EOS 검사는 `tests/robot/suites/arista_cli.robot`의 직접 CLI·반복 수집·긴 출력 검사를 사용합니다. 프로그램을 상시 등록하지 않고 오프라인 스냅샷만 검사할 수 있습니다.

Lab2 장비 이름과 실제 이미지 버전이 다를 수 있습니다. 검증 보고서는 이름보다 show version의 실제 버전과 바이너리 해시를 기준으로 기록합니다. 테스트용 파일과 임시 VLAN은 검사 뒤 정리합니다.
