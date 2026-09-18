# 6. Batfish 와의 비교 및 설계 근거

## 6.1 Batfish 란

Batfish 는 네트워크 설정을 정적으로 분석하는 도구입니다. 핵심 아이디어는
**벤더별 설정을 공통 구조로 변환한 뒤, 분석은 벤더를 모른 채 수행**하는
것입니다.

```
Cisco IOS  ---+
Arista EOS ---+--> [파서] --> Configuration --> [분석 엔진]
Junos      ---+                (공통 구조)         |
                                                    v
                                          BDD 기반 도달성 분석
```

이 프로젝트는 같은 설계를 따릅니다.

| Batfish | 이 프로젝트 | 역할 |
| --- | --- | --- |
| `Configuration` | `NeutralDeviceConfig` | 벤더 중립 장비 설정 |
| 벤더별 파서 | `CiscoRouterConfigParser`, `FrrRouterConfigParser` 등 6종 | CLI 출력 -> 중립 구조 |
| 심볼릭 패킷 집합 | `BddManager` + `PacketVariables` | 패킷 집합의 BDD 표현 |
| 도달성 분석 | `SegmentationBddEngine` | 망분리 위반 판정 |
| 반례 경로 | `PolicyViolation.sampledPacket()` | 재현 가능한 예시 패킷 |

## 6.2 규모에 맞게 축소한 부분

Batfish 는 범용 도구라 다음을 다룹니다. 이 프로젝트는 필요한 만큼만
구현했습니다.

| 항목 | Batfish | 이 프로젝트 | 이유 |
| --- | --- | --- | --- |
| 프로토콜 | IPv4/IPv6, TCP/UDP/ICMP, ARP, BGP/OSPF | IPv4 + 포트 | 망분리 검증에 필요한 최소 집합 |
| 변수 수 | 가변 (패킷 필드 전체) | 80 비트 고정 | 예측 가능하고 캐시 효율이 높음 |
| 정책 표현 | ACL, 라우팅 정책, NAT 등 | 등급 기반 연결 규칙 | 도메인 요구사항이 단순함 |
| 데이터 평면 | 시뮬레이션 가능 | 정적 집합 연산만 | 실시간 검증이 목적이 아님 |
| 대칭성 | 양방향 자동 처리 | 금지 쌍을 양방향 생성 | 코드가 단순해짐 |

## 6.3 변수 배치 설계

```
+---------------+---------------+----------+
| 출발지 IP 32  | 목적지 IP 32  | 포트 16  |
+---------------+---------------+----------+
 0            31 32          63 64      79
```

**MSB 먼저** 배치한 이유: CIDR 접두사가 "앞쪽 N개 비트 고정" 으로 자연스럽게
표현됩니다. LSB 먼저면 접두사 길이마다 비트 위치 계산이 달라져 실수가
생깁니다.

```java
public static BddNode cidr(BddManager manager, String cidr, int offset) {
    ParsedCidr parsed = parseCidr(cidr);
    BddNode result = manager.one();
    for (int bit = 0; bit < parsed.prefixLength(); bit++) {
        int variable = offset + bit;
        boolean value = ((parsed.address() >>> (IP_BITS - 1 - bit)) & 1) != 0;
        result = manager.and(result, value
                ? manager.variable(variable)
                : manager.not(manager.variable(variable)));
    }
    return result;   // 하위 비트는 자유 -> 자동으로 대역 전체 포함
}
```

`0.0.0.0/0` 은 반복이 0회라 `manager.one()` 이 됩니다. 즉 "전체 집합" 을
별도로 특수 처리할 필요가 없습니다.

## 6.4 Java 에서 BDD 를 직접 구현한 이유

외부 BDD 라이브러리(예: JavaBDD/JDD)를 쓰지 않고 직접 구현했습니다.

| 이유 | 설명 |
| --- | --- |
| 의존성 최소화 | Spring Boot + ANTLR 만으로 구성. 네이티브 라이브러리 불필요 |
| 변수 수 고정 | 80 변수 고정이라 범용 라이브러리의 유연성이 필요 없음 |
| 디버깅 용이성 | 노드 수/구조를 직접 노출해 `metrics` 로 보여줄 수 있음 |
| 학습/설명 목적 | 구현이 문서와 1:1 로 대응되어 설명이 명확함 |
| 스레드 안전성 제어 | 매니저를 요청 단위로 새로 만들어 동기화 문제 제거 |

구현 분량은 약 500줄입니다 (`BddNode` + `BddManager` + `PacketVariables`).

## 6.5 등급 기반 정책 표현의 이점

정책을 "금지 목록" 이 아니라 **"등급 + 인접 규칙"** 으로 표현합니다.

```java
public static boolean allowsDirectConnection(ZoneClass source, ZoneClass target) {
    if (source == null || target == null) {
        return true;      // 등급을 모르면 판정하지 않음
    }
    return Math.abs(source.level - target.level) <= 1;
}
```

이 표현의 이점:

1. **새 등급 추가가 쉽습니다.** `ZoneClass` 에 상수를 넣기만 하면 되고,
   위반 판정 코드를 고칠 필요가 없습니다.
2. **정책 변경이 한 곳입니다.** 등급 레벨만 바꾸면 전체 판정이 바뀝니다.
3. **설명이 자연스럽습니다.** "한 단계를 건너뛰면 위반" 이 사람이 읽는
   규칙과 코드가 일치합니다.

`forbidsDirectConnection(null, ...)` 이 `false` 를 돌려주는 것도 의도입니다.
등급을 모르는 상태에서 위반이라고 단정하면 **오탐**이 쏟아지고, 그러면
운영자가 경보를 무시하게 됩니다.

## 6.6 반례 추출이 주는 실무 가치

일반적인 검증 도구는 "위반 N건" 만 알려줍니다. 그러면 운영자는 어느 규칙을
어떻게 고쳐야 할지 알기 어렵습니다.

BDD 는 위반 집합에서 **만족 할당**을 뽑을 수 있으므로 구체적 패킷을
제시합니다.

```
[CRITICAL] Confidential <-> Open 직접 연결은 허용되지 않습니다.
위반 예시 패킷: 10.10.131.0 -> 192.168.0.0:443
```

이 패킷은 "이 주소에서 이 주소로 이 포트로 가는 트래픽이 허용 규칙에
포함되어 있다" 는 사실을 그대로 보여줍니다. 검증 결과에 대한 신뢰가
높아지고, 조치가 명확해집니다.

## 6.7 적합성 판단: BDD 가 과한가

BDD 는 만능이 아닙니다. 이 프로젝트에 적합한 이유를 정리하면:

| 조건 | 이 프로젝트 |
| --- | --- |
| 조건이 비트 벡터로 표현되는가 | 예 (IPv4 주소 + 포트) |
| 집합 연산(합/교/여집합)이 필요한가 | 예 (허용 규칙 합집합 vs 금지 집합) |
| 반례가 필요한가 | 예 (운영자가 조치하려면 필요) |
| 조건 수가 많은가 | 예 (규칙 수 x 서브넷 수) |

반대로 이런 경우에는 BDD 가 과합니다.

- 조건이 단순 비교 몇 개뿐일 때 (단순 `if` 로 충분)
- 순서가 중요할 때 (ACL 은 첫 일치 규칙이 적용되므로 BDD 만으로는 부족함)

### ACL 순서 문제

실제 방화벽 ACL 은 **첫 일치 규칙**이 적용됩니다. 이 프로젝트의 연결
규칙은 "허용 목록" 의미이므로 합집합으로 충분하지만, ACL 을 그대로
검증하려면 순서를 반영해야 합니다.

현재 구현은 ACL 순서를 다루지 않습니다. 수집된 방화벽 규칙은
`enabled=false` 초안으로만 제시하고, 검증 대상은 사람이 확인한 연결
규칙입니다. 이 경계를 명확히 둔 것이 오탐을 막는 핵심입니다.

## 6.8 확장 지점

| 확장 | 방법 |
| --- | --- |
| IPv6 지원 | `PacketVariables` 에 128비트 주소 변수 추가 (변수 96개 증가) |
| 프로토콜 구분 | 변수 8개 추가 (tcp/udp/icmp/...) 후 `port()` 에 조건 추가 |
| ACL 순서 반영 | 규칙에 우선순위를 두고, 앞선 규칙의 집합을 이후 규칙에서 차감 |
| 경로 기반 도달성 | `NeutralDeviceConfig.getRoutes()` 를 홉 단위로 연결해 전이 폐쇄 계산 |
