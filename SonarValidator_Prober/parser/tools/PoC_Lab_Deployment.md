# PoC 랩(GNS3) 배포 가이드

`PoC용 네트워크 구성하기/` 의 토폴로지(D-AI-PBL-PoC-Network)에 프로버 에이전트를
배포하고, mock API / Spring 백엔드로 파싱 결과와 WebSocket 통신을 검증하는 절차입니다.
2026-09-18 기준으로 **18개 노드에서 실제 검증**했습니다.

---

## 1. 접속 경로

노드마다 접속 방법이 다릅니다. 이 차이가 배포 방법을 가릅니다.

| 노드 유형 | 대상 | 접속 방법 |
|---|---|---|
| QEMU 라우터 5대 | Gateway / DMZ / C4I / Survillance / VDI -Router | **SSH** `root@172.16.255.{1,3,4,5,6}` |
| 방화벽 1대 | Firewall | **SSH** `root@172.16.255.2` (컨테이너와 동일 노드) |
| 컨테이너 스위치 5대 | Switch-0 ~ Switch-4 | GNS3 호스트에서 **docker exec** (sshd 있음) |
| 컨테이너 VM 4대 | TOD-Cam / UAV / VDI-1 / VDI-2 | GNS3 호스트에서 **docker exec** (sshd 없음) |
| QEMU 서버 4대 | ATICS / KNCCS / AFCCS / Public-Web-Server | **콘솔**만 (sshd 없음) |

- **GNS3 호스트**: `ssh ssh1032007@172.18.136.244` (키 인증, docker 권한 보유)
- **콘솔 포트는 GNS3 호스트의 `127.0.0.1` 에만 열립니다.** 로컬에서는 접근 불가입니다.
  → 콘솔이 필요하면 GNS3 호스트에 SSH 로 들어가 헬퍼를 실행하세요.

### 콘솔 포트 매핑

| 노드 | 포트 | 노드 | 포트 |
|---|---|---|---|
| Gateway-Router | 5015 | Firewall | 5041 |
| DMZ-Router | 5040 | Switch-0 | 5042 |
| C4I-Network-Router | 5019 | Switch-1 | 5044 |
| Survillance-Network-Router | 5013 | Switch-2 | 5046 |
| VDI-Router | 5017 | Switch-3 | 5048 |
| ATICS | 5022 | Switch-4 | 5050 |
| KNCCS | 5024 | TOD-Cam | 5028 |
| AFCCS | 5026 | UAV / VDI-1 / VDI-2 | 5030 / 5032 / 5034 |
| Public-Web-Server | 5036 | Management-Console | 5901 (vnc) |

> 프로젝트 파일에서 자동 추출: `python3 dump_gns3_nodes.py`

---

## 2. 서버 주소가 두 개인 이유

| 접속 주소 | 어디서 쓰나 | 왜 |
|---|---|---|
| `192.168.122.58:3000` | 데이터망 노드(라우터/방화벽/VM) | 이 랩의 NAT 망에 있는 호스트 주소 |
| `172.16.255.245:3000` | **관리망만 있는 스위치** | 스위치는 L2 전용이라 관리망에만 붙음 |

- 호스트는 두 망에 동시에 붙어 있습니다 (`ens4` = 192.168.122.58, `ens3` = 172.16.255.245).
- **파일 전송용 HTTP 서버**도 같은 주소로 열어 둡니다. (기본 8099)

---

## 3. 스위치에 관리 IP 부여 (필수 선행 작업)

Open vSwitch 스위치는 L2 전용이라 `ip -4 a` 에 `lo` 만 보입니다.
관리망 포트(`eth11`)에 IP 를 주지 않으면 서버에 도달할 수 없습니다.

```sh
# 각 스위치 컨테이너에서 (GNS3 호스트)
ip link set eth11 up
ip addr add 172.16.255.10N/24 dev eth11      # Switch-0..4 → .101 ~ .105
```

> `eth11` 은 토폴로지상 Hub1 에 연결된 관리망 포트입니다.
> **Switch-2 는 Hub1 링크가 토폴로지에 없어** 관리망 라우팅이 되지 않습니다.
> 수집 자체는 정상이며, 서버 전송만 불가합니다. (GNS3 GUI 에서 링크 추가 필요)

---

## 4. 배포

### 4-1. 준비 (호스트)

```sh
cd SonarValidator_Prober
cmake --build build_static -j"$(nproc)"        # musl(Alpine) 대응 정적 바이너리
mkdir -p /tmp/sonar_stage
cp build_static/sonar_validator_prober /tmp/sonar_stage/
cp Installer/default_template.sqlite  /tmp/sonar_stage/

# 파일 전송용 HTTP 서버
cd /tmp/sonar_stage && python3 -m http.server 8099 --bind 0.0.0.0 &
```

> **정적 빌드가 필요한 이유**: Alpine 은 musl libc 라 glibc 동적 바이너리를 실행하지 못합니다.
> 스위치/방화벽 컨테이너가 Alpine 이므로 정적 바이너리를 씁니다.
> 자세한 빌드 옵션은 `docs/Agent/Session_Notes.md` 를 참고하세요.

### 4-2. 라우터 / 방화벽 (SSH)

```sh
sh parser/tools/deploy_poc_lab.sh http://172.16.255.245:8099
```

### 4-3. 컨테이너 (스위치 / VM)

```sh
# GNS3 호스트로 파일 전송
scp /tmp/sonar_stage/{deploy_containers.sh,restart_in_container.sh,supported binary} \
    ssh1032007@172.18.136.244:/tmp/

# GNS3 호스트에서
bash /tmp/deploy_containers.sh /tmp/sonar_validator_prober /tmp/default_template.sqlite
```

### 4-4. QEMU 서버 (콘솔)

VM 안에 컴파일러가 없으므로 HTTP 로 바이너리를 받습니다.

```sh
# GNS3 호스트에서 (콘솔 로그인: ubuntu / ubuntu)
python3 vmrun.py 5022 ubuntu ubuntu \
  "curl -s -o /tmp/d.sh http://192.168.122.58:8099/deploy_vm.sh; echo ubuntu | sudo -S sh /tmp/d.sh http://192.168.122.58:8099 VM" \
  "echo ubuntu | sudo -S sh /opt/sonar_validator/run.sh"
```

> VM 에서는 **`172.16.255.245` 가 아니라 `192.168.122.58`** 을 써야 합니다.
> 관리망 주소는 방화벽 정책상 VM 에서 도달하지 않습니다.

---

## 5. ⚠️ 재시작은 반드시 SIGTERM 으로

`kill -9` 로 프로버를 죽이면 SQLite 에 **hot journal** 이 남아
다음 기동이 `Runtime initialization failed` 로 실패합니다.

```sh
# 좋은 예: SIGTERM → 유예 → (필요 시) SIGKILL + DB 복구
sh parser/tools/restart_in_container.sh <SERVER_IP> <NODE_TYPE>   # 컨테이너
sh parser/tools/deploy_poc_lab.sh <HTTP_BASE>                     # 라우터 (배포+재시작)
```

두 스크립트 모두 다음을 보장합니다.

1. `SIGTERM` 을 먼저 보내고 최대 8초 기다립니다.
2. 그래도 살아 있으면 `SIGKILL` 후 **hot journal 제거 + DB 헤더 검증**.
3. 헤더가 `SQLite format 3` 가 아니면 DB 를 재생성합니다.

> DB 를 잃어도 괜찮습니다. 텔레메트리는 주기마다 다시 전송되고,
> 권위 있는 사본은 서버(백엔드)에 있습니다.

### 프로세스를 찾는 올바른 방법

```sh
# 잘못된 예: 자기 자신의 명령줄과 매칭되어 SSH 세션이 죽습니다
pkill -f sonar_validator_prober

# 올바른 예: 실제 실행 파일 기준으로 찾습니다
for p in /proc/[0-9]*; do
    e=$(readlink "$p/exe" 2>/dev/null) || continue
    case "$e" in */sonar_validator_prober) kill -TERM "${p#/proc/}" ;; esac
done
```

> `ssh root@host 'rm -f /opt/.../sonar_validator_prober'` 같은 명령줄에도
> 프로버 이름이 들어갑니다. `pkill -f` 는 그 셸까지 매칭해 **원격 세션을 끊습니다.**

---

## 6. 검증

### 6-1. mock API 로 통신 확인

```sh
cd Agent_Test/mock_API
MOCK_PUSH_INTERVAL_MS=20000 node envelope_server.js &
```

정상이면 로그에 `hello` → `policy-request` → `telemetry` 가 모두 보이고
`[error]` 는 0건입니다.

### 6-2. Spring 백엔드로 확인

```sh
cd SonarValidator_Backend
export JAVA_HOME=/home/osboxes/.sdkman/candidates/java/26.0.2-oracle
./mvnw spring-boot:run          # 포트 3000 (mock 과 동시 실행 불가)
```

| 엔드포인트 | 용도 |
|---|---|
| `GET /api/v1/agents` | 연결된 에이전트 목록/요약 |
| `GET /api/v1/agents/{id}/telemetry` | 최근 텔레메트리 원본(벤더 고유) |
| `GET /api/v1/agents/{id}/config` | **중립 설정**(벤더 무관 구조) |
| `GET /api/v1/agents/configs` | 전체 중립 설정 요약 |
| `POST /api/v1/agents/{id}/push` | 서버 → 에이전트 지시 |

### 6-3. 파싱 결과를 DB 에서 직접 확인

```sh
python3 - <<'PY'
import sqlite3
con = sqlite3.connect("/opt/sonar_validator/data/prober_db.sqlite")
for t in ("nic_info","nic_address","route_table","vlan_status","trunk_status","arp_table"):
    print(t, con.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0])
PY
```

> **수집 주기는 30초**입니다. 프로버를 30초 미만으로 실행하면 DB 가 비어 보입니다.
> 검증할 때는 최소 35초, 안전하게 60~70초 이상 실행하세요.

---

## 7. 장비별 수집 명령 (조회 전용)

프로버는 **조회만** 합니다. 설정을 바꾸지 않습니다.

| 제품 | 실행 명령 | 수집 항목 |
|---|---|---|
| Ubuntu / Linux VM | `ip a`, `ip -br addr show`, `ip route show`, `ip neigh show` | NIC, 라우팅, ARP |
| FRR 라우터 (Alpine) | 위와 동일 | NIC, 라우팅(OSPF), ARP |
| nftables 방화벽 (Alpine) | 위 + `nft list ruleset` | + 필터 규칙 |
| Open vSwitch 스위치 | `ovs-vsctl show`, `ovs-vsctl list port`, `ip a` | L2 포트, VLAN, 트렁크 |
| Cisco IOS-XE | `show ip interface brief`, `show ip route`, `show ip arp` | NIC, 라우팅, ARP |
| Arista vEOS | `show vlan brief`, `show ip interface brief`, `show interfaces switchport`, `show arp` | NIC, VLAN, 트렁크, ARP |

### 제품 감지 순서 (`DetectProductName`)

`dohost`(Cisco) → `ovs-vsctl`(OVS) → `FastCli`(Arista) → `vtysh`(FRR)
→ 배포판 이름(Ubuntu) → `nft`(nftables)

> 이 순서 때문에 **방화벽은 `nft` 가 아니라 `vtysh` 유무에 따라 갈릴 수 있습니다.**
> 방화벽에는 vtysh 가 없으므로 `nft` 로 판정됩니다.

---

## 8. 검증된 결과 (2026-09-18)

```
connected agents : 18
policy requests  : 7000+
vendor formats   : AlpineFirewall, ARISTA_vEOS, CISCO_IOS, FRRRouter, LinuxVM, OpenvSwitch
parsed devices   : 15
backend errors   : 0
```

| 노드 | 형식 | ifaces | routes | vlans | arp | rules |
|---|---|---|---|---|---|---|
| Firewall | AlpineFirewall | 11 | 6 | 3 | 6 | 5 |
| Gateway-Router | FRRRouter | 10 | 14 | 0 | 15 | 0 |
| Switch-0..4 | OpenvSwitch | 15 | 0 | 1~3 | 0 | 0 |
| ATICS / KNCCS / AFCCS / PWS | LinuxVM | 2 | 2 | 0 | 1 | 0 |

- 방화벽의 `eth1.131/132/133` 이 **VLAN 131/132/133 + parent eth1** 로 분해됨
- Switch-1 의 업링크 `eth0` 이 **trunk, 허용 VLAN [111, 112]** 로 파악됨
- 서버 푸시(`POST /push`)가 실제 에이전트에 전달되고 간격이 변경됨

---

## 9. 알려진 제약

| 항목 | 상태 |
|---|---|
| Switch-2 서버 전송 | 토폴로지에 Hub1 링크 없음 → GNS3 GUI 에서 추가 필요 (수집은 정상) |
| Cisco / Arista 실장비 | 이 랩에는 없음. 파서는 단위 테스트로만 검증됨 |
| Firewall VLAN 런타임 | `/etc/network/interfaces` 에는 있으나 부팅 시 생성되지 않음 → 수동 생성 필요 |
| 라우터 초기 SSH | 키가 없으면 콘솔에서 `authorized_keys` 에 추가해야 함 (`frr_shell.py` 참고) |
