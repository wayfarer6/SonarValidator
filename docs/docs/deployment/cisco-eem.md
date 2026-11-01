# Cisco EEM 감시 경로

Cisco 8000v는 IOS EEM이 60초마다 조회 결과를 기록하고 Guest Shell Prober가 이를 읽습니다. 현재 수집 명령은 `show ip interface brief`, `show ip route`, `show ip arp` 세 가지입니다.

```mermaid
flowchart LR
  IOS[IOS EEM 60초 타이머] --> F[bootflash guest-share 원문]
  F --> H[시작 스크립트 검증·동기화]
  H --> C[Guest Shell ios_cli 캐시]
  C --> P[Prober 파싱]
  P --> B[Backend 텔레메트리]
```

## 읽기 계약

- EEM 파일의 첫 줄은 메타데이터가 아닌 실제 표 헤더입니다. Interface·Protocol·Codes를 보존합니다.
- 세 명령의 유효한 스냅샷을 먼저 읽습니다. 파일이 없거나 유효하지 않을 때만 dohost 호환 경로를 사용합니다.
- 파일은 일반 파일, 0초 이상 300초 이하 경과, 최대 4 MiB, 완결된 마지막 줄과 명령에 맞는 헤더를 요구합니다. 빈 파일·오류 출력·미래 시각·심볼릭 링크·FIFO는 받지 않습니다.
- 설정 명령이나 여러 명령을 연결한 스크립트에는 조회 캐시를 사용하지 않습니다. 빈 직접 실행 출력도 성공으로 취급하지 않습니다.

## 파일 게시와 EEM

[현재 EEM 원본](/scripts/Lab1-Prober-EEM.txt), [시작 스크립트](/scripts/lab1-prober-start.sh)를 사용합니다. 타이머는 60초, maxrun은 55초이며 action 라벨은 고정 폭으로 정렬합니다. `terminal length 0`으로 페이지 대기를 막습니다.

스크립트는 동기화 잠금을 잡은 뒤 같은 디렉터리의 고유 임시 파일에 복사하고 원본 변경·헤더·오류·마지막 줄을 검사합니다. 검증된 파일만 rename으로 교체하고 원본 mtime을 보존합니다. 복사할 때 오래된 데이터가 새 데이터처럼 갱신되지 않습니다. 동기화 잠금은 Prober 실행 전에 닫으며 실행 중복 방지 잠금과 구분합니다.

세 조회는 순차 실행되고 파일별로 교체됩니다. 라우터 전체 상태의 동시·원자적 스냅샷을 보장하지는 않습니다. TTL은 허용 가능한 최대 나이이며 수집 주기 자체는 60초입니다.

수동 동기화 점검은 Guest Shell에서 다음 명령으로 실행합니다. Prober를 새로 띄우지 않습니다.

```bash
/bin/bash /home/guestshell/sonar/start.sh --sync-only
```

## 감시와 설정 적용의 구분

현재 설치한 SONAR-Lab1-Collect는 조회·시작 유지용입니다. ACL 요청 파일을 처리하는 적용용 EEM은 별도입니다. 요청 파일 작성 성공을 실제 장비 적용 성공으로 보고하지 않도록 했습니다. 적용용 EEM·실제 정책 적용 시험은 아직 완료하지 않았습니다.

## 검증

`./SonarValidator_Prober/build.sh`는 DB·PTY·IOS 출력·EEM 파일 읽기·동시 게시와 ManagementService 통합을 전체 CTest로 검사합니다. 특정 통합만 재검사할 때는 `ctest --test-dir <빌드 디렉터리> --output-on-failure -R ios_eem_service_test`를 사용합니다. dohost는 가짜 실행 파일로 대체하며 실제 장비에 정책을 보내지 않습니다.

실제 장비는 EEM 이벤트 이력, 원본과 캐시 mtime·헤더 일치, Backend의 인터페이스·라우트 수신을 함께 확인합니다. 이벤트 이력 success만으로 파일 내용까지 정상이라고 판단하지 않습니다.

명령 구성과 점검 기준은 [Cisco EEM 모범 사례](https://www.cisco.com/c/en/us/support/docs/ios-nx-os-software/ios-xe-16/216091-best-practices-and-useful-scripts-for-ee.html), [Cisco EEM 검증 안내](https://www.cisco.com/c/en/us/support/docs/ios-nx-os-software/ios-xe-16/217013-troubleshoot-and-test-eem-scripts.html)를 참고했습니다.
