## Cisco Router 제품군 Policy Design

Cisco 라우터에서 라우팅 프로토콜(OSPF) 활성화, 인터페이스 제어, 연결 및 라우팅 테이블 조회/삭제, 그리고 로그 수집을 제어하기 위한 개별 JSON 정책 구조입니다.

* 인터페이스 활성화(On) / 비활성화(Off)

```json
{
    "vendor": ["Cisco"],
    "product": ["IOS XE"],
    "model": ["Cisco ISR"],
    "command": ["on"],
    "interface": ["GigabitEthernet0/0/1"]
}

```

```json
{
    "vendor": ["Cisco"],
    "product": ["IOS XE"],
    "model": ["Cisco ISR"],
    "command": ["off"],
    "interface": ["GigabitEthernet0/0/1"]
}

```

* OSPF 라우팅 프로토콜 활성화

```json
{
    "vendor": ["Cisco"],
    "product": ["IOS XE"],
    "model": ["Cisco ISR"],
    "command": ["create"],
    "protocol": ["ospf"],
    "process_id": ["1"],
    "router_id": ["1.1.1.1"],
    "networks": [
        {"network": "192.168.1.0", "wildcard_mask": "0.0.0.255", "area": "0"}
    ]
}

```

* 라우팅 테이블 및 연결 상태 조회

```json
{
    "vendor": ["Cisco"],
    "product": ["IOS XE"],
    "model": ["Cisco ISR"],
    "command": ["get"],
    "target": ["route-table"]
}

```

* 정적 라우팅 또는 연결(Route) 삭제

```json
{
    "vendor": ["Cisco"],
    "product": ["IOS XE"],
    "model": ["Cisco ISR"],
    "command": ["remove"],
    "destination_prefix": ["10.200.0.0"],
    "subnet_mask": ["255.255.0.0"],
    "next_hop": ["192.168.1.254"]
}

```

* 로그 수집(Syslog) 설정

```json
{
    "vendor": ["Cisco"],
    "product": ["IOS XE"],
    "model": ["Cisco ISR"],
    "command": ["create"],
    "log_feature": ["syslog"],
    "severity_level": ["info"],
    "logging_server": ["10.0.0.254"]
}

```

---

## FRR (Free Range Routing) Router 정책 디자인

FRR 라우터 환경(Quagga 계열 명령어 기반)에서 OSPF, 인터페이스, 라우팅 테이블 및 로그 수집을 제어하기 위한 JSON 정책 구조입니다.

* 인터페이스 활성화(On) / 비활성화(Off)

```json
{
    "vendor": ["FRR"],
    "product": ["FRRouting"],
    "model": ["Linux FRR"],
    "command": ["on"],
    "interface": ["eth0"]
}

```

```json
{
    "vendor": ["FRR"],
    "product": ["FRRouting"],
    "model": ["Linux FRR"],
    "command": ["off"],
    "interface": ["eth0"]
}

```

* OSPF 활성화 (zebra/ospfd 연동)

```json
{
    "vendor": ["FRR"],
    "product": ["FRRouting"],
    "model": ["Linux FRR"],
    "command": ["create"],
    "protocol": ["ospf"],
    "router_id": ["2.2.2.2"],
    "networks": [
        {"network": "192.168.2.0/24", "area": "0"}
    ]
}

```

* 라우팅 테이블 조회

```json
{
    "vendor": ["FRR"],
    "product": ["FRRouting"],
    "model": ["Linux FRR"],
    "command": ["get"],
    "target": ["route-table"]
}

```

* 라우팅 경로 삭제

```json
{
    "vendor": ["FRR"],
    "product": ["FRRouting"],
    "model": ["Linux FRR"],
    "command": ["remove"],
    "destination_prefix": ["10.200.0.0/16"],
    "next_hop": ["192.168.2.254"]
}

```

* 로그 수집(Syslog/Log File) 설정

```json
{
    "vendor": ["FRR"],
    "product": ["FRRouting"],
    "model": ["Linux FRR"],
    "command": ["create"],
    "log_feature": ["syslog"],
    "log_level": ["notifications"],
    "log_target": ["file", "/var/log/frr/frr.log"]
}

```

Cisco Router와 FRR Router의 라우팅 테이블 및 활성 커넥션(세션) 조회 결과를 전송/수집하기 위한 정책 디자인입니다. 기존 `get` 명령어를 확장하여, `show ip route` 및 활성 커넥션 정보까지 포함하도록 구성했습니다.

---

## Cisco Router 제품군 Policy Design (라우팅/커넥션 조회 추가)

* 라우팅 테이블(`show ip route`) 및 활성 커넥션(`show ip ssh`, `show tcp brief` 등) 조회 정책

```json
{
    "vendor": ["Cisco"],
    "product": ["IOS XE"],
    "model": ["Cisco ISR"],
    "command": ["get"],
    "targets": [
        {
            "target_type": ["route-table"],
            "command_syntax": ["show ip route"],
            "include_connected": ["true"]
        },
        {
            "target_type": ["active-connections"],
            "command_syntax": ["show tcp brief", "show ip ssh"],
            "status": ["established"]
        }
    ],
    "export_destination": {
        "protocol": ["syslog"],
        "collector_ip": ["10.0.0.254"]
    }
}

```

---

## FRR (Free Range Routing) Router 정책 디자인 (라우팅/커넥션 조회 추가)

* 라우팅 테이블(`show ip route`) 및 활성 커넥션/네트워크 상태 조회 정책

```json
{
    "vendor": ["FRR"],
    "product": ["FRRouting"],
    "model": ["Linux FRR"],
    "command": ["get"],
    "targets": [
        {
            "target_type": ["route-table"],
            "command_syntax": ["show ip route"],
            "include_connected": ["true"]
        },
        {
            "target_type": ["active-connections"],
            "command_syntax": ["ss -t state established", "netstat -an"],
            "protocol": ["tcp"]
        }
    ],
    "export_destination": {
        "protocol": ["syslog"],
        "collector_ip": ["10.0.0.254"]
    }
}

```