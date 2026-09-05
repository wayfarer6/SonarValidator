우분투(Ubuntu) 기반 VM의 네트워크 설정(IP 할당, 기본 게이트웨이, DNS, 인터페이스 활성화/비활성화)을 제어하기 위한 netplan/networkd 기반 JSON 정책 디자인입니다.

---

## Ubuntu VM 네트워크 설정 정책 디자인

* 네트워크 인터페이스 활성화(On) / 비활성화(Off)

```json
{
    "vendor": ["Canonical"],
    "product": ["Ubuntu Linux"],
    "model": ["Ubuntu VM"],
    "command": ["on"],
    "interface": ["ens33"]
}

```

```json
{
    "vendor": ["Canonical"],
    "product": ["Ubuntu Linux"],
    "model": ["Ubuntu VM"],
    "command": ["off"],
    "interface": ["ens33"]
}

```

* 정적 IP 주소 및 게이트웨이 설정 (Netplan 기반)

```json
{
    "vendor": ["Canonical"],
    "product": ["Ubuntu Linux"],
    "model": ["Ubuntu VM"],
    "command": ["create"],
    "config_backend": ["netplan"],
    "network_config": {
        "ethernets": {
            "ens33": {
                "dhcp4": ["false"],
                "addresses": ["192.168.1.100/24"],
                "routes": [
                    {
                        "to": ["0.0.0.0/0"],
                        "via": ["192.168.1.254"]
                    }
                ],
                "nameservers": {
                    "addresses": ["8.8.8.8", "1.1.1.1"]
                }
            }
        }
    }
}

```

* 네트워크 설정 삭제 (DHCP로 원복)

```json
{
    "vendor": ["Canonical"],
    "product": ["Ubuntu Linux"],
    "model": ["Ubuntu VM"],
    "command": ["remove"],
    "config_backend": ["netplan"],
    "interface": ["ens33"],
    "fallback_dhcp4": ["true"]
}

```

* VM 네트워크 상태, 라우팅 테이블 및 활성 커넥션 조회

```json
{
    "vendor": ["Canonical"],
    "product": ["Ubuntu Linux"],
    "model": ["Ubuntu VM"],
    "command": ["get"],
    "targets": [
        {
            "target_type": ["route-table"],
            "command_syntax": ["ip route show"],
            "include_connected": ["true"]
        },
        {
            "target_type": ["active-connections"],
            "command_syntax": ["ss -t -a", "ip addr show"],
            "status": ["established"]
        }
    ],
    "export_destination": {
        "protocol": ["syslog"],
        "collector_ip": ["10.0.0.254"]
    }
}

```

* VM 시스템 및 네트워크 로그 수집 설정 (rsyslog / systemd-journald)

```json
{
    "vendor": ["Canonical"],
    "product": ["Ubuntu Linux"],
    "model": ["Ubuntu VM"],
    "command": ["create"],
    "log_feature": ["journald-syslog"],
    "severity_level": ["info"],
    "logging_server": ["10.0.0.254"]
}

```