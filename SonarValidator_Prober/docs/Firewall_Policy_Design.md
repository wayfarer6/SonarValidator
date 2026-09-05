nftables 방화벽 규칙 제어 및 로그 수집을 위한 JSON 정책 디자인입니다. 테이블 생성, 체인 설정, 규칙 추가/삭제, 그리고 커널 로그 수집을 포함하도록 구성했습니다.

---

## nftables 방화벽 정책 디자인

* 테이블 및 체인 생성/활성화

```json
{
    "vendor": ["Linux"],
    "product": ["nftables"],
    "model": ["Linux Netfilter"],
    "command": ["create"],
    "table_family": ["inet"],
    "table_name": ["filter"],
    "chains": [
        {
            "chain_name": ["input"],
            "hook": ["input"],
            "priority": ["0"],
            "policy": ["drop"]
        },
        {
            "chain_name": ["forward"],
            "hook": ["forward"],
            "priority": ["0"],
            "policy": ["drop"]
        }
    ]
}

```

* 서브넷 간 통신 차단 및 허용 규칙 추가 (ACL 대체)

```json
{
    "vendor": ["Linux"],
    "product": ["nftables"],
    "model": ["Linux Netfilter"],
    "command": ["create"],
    "rule_target": {
        "table_family": ["inet"],
        "table_name": ["filter"],
        "chain_name": ["forward"]
    },
    "match_criteria": {
        "ip_saddr": ["192.168.1.0/24"],
        "ip_daddr": ["192.168.2.0/24"],
        "protocol": ["ip"]
    },
    "action": ["drop"]
}

```

* 방화벽 규칙 삭제

```json
{
    "vendor": ["Linux"],
    "product": ["nftables"],
    "model": ["Linux Netfilter"],
    "command": ["remove"],
    "rule_target": {
        "table_family": ["inet"],
        "table_name": ["filter"],
        "chain_name": ["forward"]
    },
    "match_criteria": {
        "ip_saddr": ["192.168.1.0/24"],
        "ip_daddr": ["192.168.2.0/24"]
    }
}

```

* 방화벽 상태 및 룰 조회 (라우팅/연결 상태 포함)

```json
{
    "vendor": ["Linux"],
    "product": ["nftables"],
    "model": ["Linux Netfilter"],
    "command": ["get"],
    "targets": [
        {
            "target_type": ["ruleset"],
            "command_syntax": ["nft list ruleset"]
        },
        {
            "target_type": ["active-connections"],
            "command_syntax": ["conntrack -L", "ss -t state established"]
        }
    ],
    "export_destination": {
        "protocol": ["syslog"],
        "collector_ip": ["10.0.0.254"]
    }
}

```

* 방화벽 거부/허용 로그 수집 설정 (ulog/syslog 연동)

```json
{
    "vendor": ["Linux"],
    "product": ["nftables"],
    "model": ["Linux Netfilter"],
    "command": ["create"],
    "log_feature": ["kernel-log"],
    "log_prefix": ["NFT_DROP: "],
    "severity_level": ["info"],
    "logging_server": ["10.0.0.254"]
}

```