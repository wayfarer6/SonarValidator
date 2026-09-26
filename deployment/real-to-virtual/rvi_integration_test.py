#!/usr/bin/env python3
"""RVI(Real-to-Virtual-Infrastructure) 통합 테스트 — S1~S10 자동 실행.

## 무엇을 하는가

계획 문서(`RVI 통합 테스트 계획 v1.0`)의 S1~S10 을 **API 로 재현**하고
판정·증거를 모읍니다. 사람이 화면을 보지 않아도 결과를 남길 수 있게 하는 것이
목적입니다. (브라우저 확인이 필요한 항목은 `UI` 로 표시해 수동 확인으로 남깁니다)

## 왜 스크립트인가

이전 회차는 손으로 호출하고 결과를 옮겨 적었습니다. 그러면
  - 같은 테스트를 다시 돌릴 수 없고
  - "무엇을 확인했는지" 가 사람 기억에 의존합니다.
그래서 호출·판정·증거 캡처를 한 곳에 모읍니다.

## 사용

    export SONAR_ADMIN_PW='...'          # 기본 admin
    python3 rvi_integration_test.py --base http://localhost:3000

결과는 표준출력(사람용)과 JSON 파일(기계용)로 남습니다.
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

# 판정 결과를 담습니다. (순서 유지)
RESULTS = []


class Api:
    """세션 쿠키를 유지하는 최소 HTTP 클라이언트."""

    def __init__(self, base):
        self.base = base.rstrip("/")
        self.cookies = {}

    def call(self, method, path, body=None, timeout=30):
        url = self.base + path
        data = None
        headers = {"Accept": "application/json"}
        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        if self.cookies:
            headers["Cookie"] = "; ".join(f"{k}={v}" for k, v in self.cookies.items())

        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read().decode("utf-8", "replace")
                for header in resp.headers.get_all("Set-Cookie") or []:
                    if "=" in header:
                        k, v = header.split(";")[0].split("=", 1)
                        self.cookies[k.strip()] = v.strip()
                return resp.status, _parse(raw)
        except urllib.error.HTTPError as ex:
            raw = ex.read().decode("utf-8", "replace")
            return ex.code, _parse(raw)
        except Exception as ex:                      # noqa: BLE001
            return 0, {"error": f"{type(ex).__name__}: {ex}"}

    def get(self, path, **kw):
        return self.call("GET", path, **kw)

    def post(self, path, body=None, **kw):
        return self.call("POST", path, body, **kw)

    def put(self, path, body=None, **kw):
        return self.call("PUT", path, body, **kw)

    def delete(self, path, **kw):
        return self.call("DELETE", path, **kw)


def _parse(raw):
    if not raw:
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return {"_raw": raw[:800]}


def record(scenario, item, ok, evidence, note="", manual=False):
    """판정 한 건을 기록합니다."""
    tag = "MANUAL" if manual else ("PASS" if ok else "FAIL")
    RESULTS.append({
        "scenario": scenario, "item": item, "result": tag,
        "evidence": evidence, "note": note,
    })
    mark = "○" if manual else ("✅" if ok else "❌")
    print(f"  {mark} [{scenario}] {item}")
    if evidence:
        print(f"       {str(evidence)[:200]}")
    if note:
        print(f"       ⚠️ {note}")
    return ok


def count_rows(body):
    """목록 응답에서 행 수를 뽑습니다. (키 이름이 제각각이라 후보를 훑습니다)"""
    if not isinstance(body, dict):
        return 0
    for key in ("total", "rowCount", "count"):
        if isinstance(body.get(key), int):
            return body[key]
    for key in ("projects", "agents", "changes", "logs", "notifications", "rows", "items"):
        value = body.get(key)
        if isinstance(value, list):
            return len(value)
    return 0


# ---------------------------------------------------------------------------
# S1 — Dashboard
# ---------------------------------------------------------------------------

def s1_dashboard(api):
    print("\n════ S1. Dashboard 이상 유무 ════")
    status, health = api.get("/actuator/health")
    record("S1", "Backend health", status == 200,
           f"HTTP {status} status={((health or {}).get('status'))}")

    checks = [
        ("/api/v1/agents/overview", "Agent 현황"),
        ("/api/v1/projects", "프로젝트"),
        ("/api/v1/logs/summary", "로그 요약"),
        ("/api/v1/notifications/summary", "알림 요약"),
        ("/api/v1/network/discovered", "발견 노드"),
    ]
    bad = []
    for path, label in checks:
        status, body = api.get(path)
        rows = count_rows(body)
        record("S1", f"{label} 조회", status == 200, f"HTTP {status} rows={rows}")
        if status >= 400 or status == 0:
            bad.append(path)

    record("S1", "5개 블록 4xx/5xx 없음", not bad,
           f"실패 {len(bad)}건 {bad}" if bad else "전부 200")


# ---------------------------------------------------------------------------
# S2 — 프로젝트 생성
# ---------------------------------------------------------------------------

PROJECT_NAME = "RVI-INTEGRATION-2026-09-26"


def s2_project(api):
    print("\n════ S2. 프로젝트 생성 ════")
    status, body = api.get("/api/v1/projects")
    existing = None
    for p in (body or {}).get("projects", []):
        if p.get("name") == PROJECT_NAME:
            existing = p
            break

    if existing:
        record("S2", "기존 프로젝트 재사용", True,
               f"project_id={existing.get('project_id')}", "계획: 삭제하지 않음")
        return existing.get("project_id")

    status, created = api.post("/api/v1/projects", {
        "name": PROJECT_NAME,
        "category": "Defense",
        "description": "Real-to-Virtual-Infrastructure 통합 테스트 (RVI 기준 재실행)",
    })
    ok = status == 200 and isinstance(created, dict) and created.get("project_id")
    record("S2", "프로젝트 생성", ok, f"HTTP {status} project_id={(created or {}).get('project_id')}")
    return (created or {}).get("project_id")


# ---------------------------------------------------------------------------
# S3 — 편집 · 검증 · 오프라인
# ---------------------------------------------------------------------------

def s3_validation(api, project_id):
    print("\n════ S3. 편집·검증·오프라인 ════")
    if not project_id:
        record("S3", "프로젝트 없음 — 건너뜀", False, "S2 실패", manual=True)
        return

    # 등급이 다른 두 서브넷 → 등급 불일치(CRITICAL) 기대
    # ⚠️ 키가 `classification` 이 아니라 `subnet_class` 입니다.
    status, body = api.put(f"/api/v1/projects/{project_id}", {
        "subnets": [
            {"cidr": "10.0.8.0/24", "name": "Subnet-A", "subnet_class": "Confidential"},
            {"cidr": "10.0.9.0/24", "name": "Subnet-B", "subnet_class": "Open"},
        ],
    })
    record("S3", "서브넷 2개 저장 (등급 불일치)", status == 200, f"HTTP {status}")

    # 검증은 저장하지 않는 전용 경로로도 돌려 봅니다.
    status, body = api.post(f"/api/v1/projects/{project_id}/validation", {
        "subnets": [
            {"cidr": "10.0.8.0/24", "name": "Subnet-A", "subnet_class": "Confidential"},
            {"cidr": "10.0.9.0/24", "name": "Subnet-B", "subnet_class": "Open"},
        ],
        "rules": [{"src": "10.0.8.0/24", "dst": "10.0.9.0/24",
                   "port": 443, "protocol": "tcp", "enabled": True}],
    })
    findings = (body or {}).get("findings") or (body or {}).get("issues") or []
    severities = [str(f.get("severity", "")).upper() for f in findings if isinstance(f, dict)]
    critical = [s for s in severities if s == "CRITICAL"]
    record("S3", "검증(무저장) 실행", status == 200,
           f"HTTP {status} findings={len(findings)} severities={sorted(set(severities))}")

    # ⚠️ 응답 구조는 {compliant, violation_count, violations[], messages[]} 입니다.
    #    CRITICAL 문자열이 아니라 **등급 위반 건수**로 판정해야 합니다.
    status, validation = api.get(f"/api/v1/projects/{project_id}/validation")
    violations = (validation or {}).get("violations") or []
    vcount = (validation or {}).get("violation_count")
    compliant = (validation or {}).get("compliant")
    classes = [(v.get("src_class"), v.get("dst_class")) for v in violations
               if isinstance(v, dict)]
    record("S3", "등급 위반 탐지 (CRITICAL 상당)",
           status == 200 and (vcount or 0) >= 1,
           f"HTTP {status} compliant={compliant} violation_count={vcount} "
           f"classes={classes}")
    if violations:
        first = violations[0]
        record("S3", "위반 사유에 등급 단계 명시",
               "등급" in str(first.get("reason", "")),
               f"src={first.get('src_class')} dst={first.get('dst_class')} "
               f"reason={str(first.get('reason'))[:90]}")

    status, schema = api.get("/api/v1/offline/schema")
    record("S3", "오프라인 스키마 조회", status == 200, f"HTTP {status}")

    status, imported = api.get("/api/v1/offline/imported")
    record("S3", "오프라인 스냅샷 목록", status == 200,
           f"HTTP {status} rows={count_rows(imported)}")


# ---------------------------------------------------------------------------
# S4 — Node 추가 · 배포 예정 · 번들
# ---------------------------------------------------------------------------

EXPECTED_NODES = [
    ("CiscoCatalyst8000V-Router", "CISCO", "Router", "10.20.0.1"),
    ("AristaEOS-Switch-agent", "ARISTA", "Switch", "10.20.0.4"),
    ("Ubuntu-24-VM-agent", "UBUNTU", "VM", "10.0.8.100"),
    ("Ubuntu-24-VM1-agent", "UBUNTU", "VM", "10.0.9.100"),
]


def s4_expected_bundle(api, project_id):
    print("\n════ S4. Node 추가·배포 예정·번들 ════")
    for agent_id, device, node, ip in EXPECTED_NODES:
        status, body = api.post("/api/v1/agents/expected", {
            "agent_id": agent_id, "project_id": project_id,
            "device_type": device, "node_type": node, "expected_ip": ip,
        })
        ok = status == 200 and (body or {}).get("registered")
        record("S4", f"배포 예정 등록 {agent_id}", ok, f"HTTP {status}")

    status, listing = api.get("/api/v1/agents/expected")
    ids = [a.get("agent_id") for a in (listing or {}).get("agents", [])]
    record("S4", "배포 예정 목록 반영", all(n[0] in ids for n in EXPECTED_NODES),
           f"HTTP {status} 등록 {len(ids)}건")

    # ⚠️ bundle/info 는 `agent_id` 가 필수 쿼리 파라미터입니다.
    status, info = api.get("/api/v1/agents/bundle/info?agent_id=CiscoCatalyst8000V-Router&node_type=Router")
    record("S4", "번들 정보 조회", status == 200, f"HTTP {status}")

    status, bundle = api.get("/api/v1/agents/bundle/CiscoCatalyst8000V-Router")
    record("S4", "번들 생성 (Cisco)", status == 200, f"HTTP {status}")


# ---------------------------------------------------------------------------
# S5 — Agent 연결 · 텔레메트리
# ---------------------------------------------------------------------------

EXPECTED_AGENTS = [
    "AristaEOS-Switch-agent",
    "CiscoCatalyst8000V-Router",
    "Ubuntu-24-VM-agent",
    "Ubuntu-24-VM1-agent",
]


def s5_agents(api):
    print("\n════ S5. Agent 연결·텔레메트리 ════")
    status, listing = api.get("/api/v1/agents")
    connected = {a.get("agent_id"): a for a in (listing or {}).get("agents", [])}
    record("S5", "Agent 목록 조회", status == 200,
           f"HTTP {status} connected={(listing or {}).get('connected')}")

    for agent_id in EXPECTED_AGENTS:
        if agent_id in connected:
            record("S5", f"연결 {agent_id}", True,
                   f"last_seen={connected[agent_id].get('last_seen')}")
        else:
            record("S5", f"연결 {agent_id}", False, "목록에 없음")

    for agent_id in EXPECTED_AGENTS:
        if agent_id not in connected:
            continue
        status, telem = api.get(f"/api/v1/agents/{agent_id}/telemetry")
        if status != 200 or not isinstance(telem, dict):
            record("S5", f"텔레메트리 {agent_id}", False, f"HTTP {status}")
            continue
        product = telem.get("product") or telem.get("vendor")
        record("S5", f"텔레메트리 {agent_id}", bool(product),
               f"product={product}")

        # ⚠️ nic_status 유무·내용이 노드마다 다릅니다. (설계 확인 항목)
        #    - Arista: 키 자체가 없음(null)
        #    - Cisco : guestshell(LXC) 이라 "Unexpected" 만
        #    합격 기준(계획 §5.1)은 "NIC 파싱 성공, 0건 아님" 입니다.
        nic = telem.get("nic_status")
        ifaces = (nic or {}).get("interfaces") if isinstance(nic, dict) else None
        real = [i for i in (ifaces or [])
                if isinstance(i, dict) and i.get("name") and i.get("name") != "Unexpected"]
        if nic is None:
            record("S5", f"인터페이스 수집 {agent_id}", False,
                   "nic_status 키 없음 (null)",
                   "계획 합격기준 '0건 아님' 위반")
        elif not real:
            record("S5", f"인터페이스 수집 {agent_id}", False,
                   f"names={[i.get('name') for i in (ifaces or [])]}",
                   "호스트 인터페이스를 못 읽음")
        else:
            record("S5", f"인터페이스 수집 {agent_id}", True, f"실인터페이스 {len(real)}건")


# ---------------------------------------------------------------------------
# S6 — Policy
# ---------------------------------------------------------------------------

def _s6_rule():
    """차단 규칙 페이로드. (필드명은 src/dst/port/protocol)

    :return: 규칙 dict
    """
    return {"src": "10.0.8.0/24", "dst": "10.0.9.0/24",
            "port": 443, "protocol": "tcp", "enabled": True,
            "note": "RVI-BLOCK-10.0.9"}


def s6_policy(api, project_id):
    print("\n════ S6. Policy 위반·금지쌍·푸시 ════")
    if not project_id:
        record("S6", "프로젝트 없음 — 건너뜀", False, "S2 실패", manual=True)
        return

    status, _ = api.put(f"/api/v1/projects/{project_id}",
                        {"rules": [_s6_rule()]})
    record("S6", "차단 규칙 저장", status == 200, f"HTTP {status}")

    status, violates = api.get(f"/api/v1/policy/violations/{project_id}")
    record("S6", "위반 조회", status == 200, f"HTTP {status} rows={count_rows(violates)}")

    status, pairs = api.get(f"/api/v1/policy/forbidden-pairs/{project_id}")
    record("S6", "금지쌍 조회", status == 200, f"HTTP {status} rows={count_rows(pairs)}")

    status, pushed = api.post(f"/api/v1/policy/push/{project_id}")
    record("S6", "정책 푸시", status == 200, f"HTTP {status}")

    status, summary = api.get("/api/v1/routes/summary")
    record("S6", "라우팅 요약 (푸시 반영)", status == 200,
           f"HTTP {status} rows={count_rows(summary)}")


# ---------------------------------------------------------------------------
# S7 — Compliance
# ---------------------------------------------------------------------------

def s7_compliance(api, project_id):
    print("\n════ S7. Compliance 이력 ════")
    status, changes = api.get("/api/v1/compliance/changes")
    record("S7", "변경 이력 전체", status == 200, f"HTTP {status} rows={count_rows(changes)}")

    if project_id:
        status, per = api.get(f"/api/v1/compliance/changes/project/{project_id}")
        record("S7", "프로젝트별 이력", status == 200, f"HTTP {status} rows={count_rows(per)}")
        record("S7", "PDF 내보내기", False, "API 미확인 — UI 수동 확인", manual=True)


# ---------------------------------------------------------------------------
# S8 — Log
# ---------------------------------------------------------------------------

LOG_SAMPLE = """Sep 26 11:20:31 OPNsense filterlog: 5,,,1000000103,vtnet1,match,block,in,4,0x0,,64,0,0,DF,6,tcp,60,172.18.10.5,10.20.0.3,45678,3000,0,S,1234567890,,64240,,mss
Sep 26 11:20:32 OPNsense filterlog: 5,,,1000000103,vtnet1,match,block,in,4,0x0,,64,0,0,DF,6,tcp,60,172.18.10.5,10.20.0.3,45679,3000,0,S,1234567891,,64240,,mss
"""


def s8_logs(api):
    print("\n════ S8. Log 적재·필터·요약 ════")
    status, filters = api.get("/api/v1/logs/filters")
    record("S8", "필터 목록", status == 200, f"HTTP {status}")

    # ⚠️ 로그 본문 키는 `lines`(배열) 또는 `text`(문자열) 입니다. `content` 는 인식되지 않습니다.
    status, ingested = api.post("/api/v1/logs/ingest", {
        "agent_id": "OPNsense-Firewall",
        "source": "OPNsense-Firewall",
        "text": LOG_SAMPLE,
    })
    record("S8", "로그 적재", status == 200,
           f"HTTP {status} {str(ingested)[:120]}")

    # ⚠️ 응답 구조는 {"summary":[{severity,num,count}]} 입니다.
    #    total 이 아니라 severity 별 집계이므로 합계로 판정합니다.
    status, summary = api.get("/api/v1/logs/summary")
    buckets = (summary or {}).get("summary") or []
    total = sum(int(b.get("count", 0)) for b in buckets if isinstance(b, dict))
    record("S8", "로그 요약 (심각도 집계)", status == 200 and total > 0,
           f"HTTP {status} 총 {total}건 "
           f"{[(b.get('severity'), b.get('count')) for b in buckets]}")


# ---------------------------------------------------------------------------
# S9 — Notification
# ---------------------------------------------------------------------------

def s9_notifications(api):
    print("\n════ S9. Notification ════")
    status, summary = api.get("/api/v1/notifications/summary")
    record("S9", "알림 요약", status == 200, f"HTTP {status} rows={count_rows(summary)}")

    status, unread = api.get("/api/v1/notifications/unread")
    record("S9", "읽지 않은 알림", status == 200, f"HTTP {status} rows={count_rows(unread)}")

    status, _ = api.post("/api/v1/notifications/read-all")
    record("S9", "전체 읽음 처리", status == 200, f"HTTP {status}")


# ---------------------------------------------------------------------------
# S10 — Network · 격리 · OPNsense
# ---------------------------------------------------------------------------

def s10_network(api, project_id):
    print("\n════ S10. Network·격리·OPNsense ════")

    if project_id:
        status, topo = api.get(f"/api/v1/network/topology/{project_id}")
        record("S10", "토폴로지 조회", status == 200, f"HTTP {status} rows={count_rows(topo)}")

    status, discovered = api.get("/api/v1/network/discovered")
    record("S10", "발견 노드 조회", status == 200, f"HTTP {status} rows={count_rows(discovered)}")

    # OPNsense — 실장비 자격증명 검증 + 진단 5개 (RVI 신규 필수 항목)
    TARGET = "OPNsense-Firewall"
    status, creds = api.get("/api/v1/opnsense/credentials")
    has_cred = any(c.get("agent_id") == TARGET
                   for c in (creds or {}).get("credentials", []))
    record("S10", "OPNsense 자격증명 등록됨", has_cred, f"HTTP {status}")

    if has_cred:
        status, verify = api.post(f"/api/v1/opnsense/credentials/{TARGET}/verify")
        ok = status == 200 and (verify or {}).get("status") == "OK"
        record("S10", "OPNsense 연결 검증", ok,
               f"HTTP {status} status={(verify or {}).get('status')} "
               f"ver={(verify or {}).get('detected_version')}")

        for target in ("firmware", "interfaces", "rules", "nat", "aliases"):
            status, probe = api.post(
                f"/api/v1/opnsense/credentials/{TARGET}/probe?target={target}")
            ok = status == 200 and (probe or {}).get("ok") is True
            record("S10", f"OPNsense 진단 {target}", ok,
                   f"HTTP {status} ok={(probe or {}).get('ok')}")
    else:
        record("S10", "OPNsense 검증", False, "자격증명 미등록")

    # 격리 — 대상은 실제 연결된 Agent 여야 합니다.
    status, listing = api.get("/api/v1/agents")
    connected = [a.get("agent_id") for a in (listing or {}).get("agents", [])]
    target = next((a for a in connected if a.startswith("Ubuntu")), None) or \
        (connected[0] if connected else None)
    if not target:
        record("S10", "격리 대상 없음", False, "연결된 Agent 0대", manual=True)
        return

    status, quaran = api.post(f"/api/v1/quarantine/{target}")
    record("S10", f"격리 요청 {target}", status in (200, 202),
           f"HTTP {status} {str(quaran)[:150]}")

    status, state = api.get(f"/api/v1/quarantine/{target}")
    record("S10", f"격리 상태 조회 {target}", status == 200,
           f"HTTP {status} {str(state)[:150]}")

    status, released = api.delete(f"/api/v1/quarantine/{target}")
    record("S10", f"격리 해제 {target}", status in (200, 202),
           f"HTTP {status} {str(released)[:150]}")

    # Firewall 격리 거부 — 방화벽 노드는 Agent 가 아니므로 거부되어야 합니다.
    # ⚠️ 거부는 HTTP 4xx 가 아니라 **200 + rejected:true** 로 옵니다.
    #    (Agent 에 명령을 보내지 않고 서버가 판단해 거절한 경우)
    status, denied = api.post("/api/v1/quarantine/OPNsense-Firewall")
    rejected = isinstance(denied, dict) and (denied.get("rejected") is True
                                             or denied.get("quarantined") is False)
    record("S10", "Firewall 격리 거부", rejected,
           f"HTTP {status} rejected={(denied or {}).get('rejected')} "
           f"delivered={(denied or {}).get('delivered')}",
           "200 + rejected:true 는 정상 거부입니다")


# ---------------------------------------------------------------------------
# 회귀 (Phase 4)
# ---------------------------------------------------------------------------

def regression(api):
    print("\n════ 회귀 (Phase 4) ════")
    status, overview = api.get("/api/v1/agents/overview")
    record("회귀", "Agent overview", status == 200, f"HTTP {status}")

    status, _ = api.get("/h2-console")
    record("회귀", "H2 콘솔 접근", status in (200, 302), f"HTTP {status}", manual=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:3000")
    parser.add_argument("--user", default="admin")
    parser.add_argument("--password",
                        default=os.environ.get("SONAR_ADMIN_PW", "admin"))
    parser.add_argument("--out", default="/tmp/rvi_integration_result.json")
    args = parser.parse_args()

    api = Api(args.base)

    print("════ 로그인 ════")
    status, body = api.post("/api/v1/auth/login",
                            {"username": args.user, "password": args.password})
    if status != 200:
        print(f"❌ 로그인 실패 HTTP {status} {body}")
        return 2
    print(f"  ✅ 로그인 OK ({body.get('username')})")

    s1_dashboard(api)
    project_id = s2_project(api)
    s3_validation(api, project_id)
    s4_expected_bundle(api, project_id)
    time.sleep(2)                      # Agent 가 붙을 여유
    s5_agents(api)
    s6_policy(api, project_id)
    s7_compliance(api, project_id)
    s8_logs(api)
    s9_notifications(api)
    s10_network(api, project_id)
    regression(api)

    # 요약
    passed = sum(1 for r in RESULTS if r["result"] == "PASS")
    failed = sum(1 for r in RESULTS if r["result"] == "FAIL")
    manual = sum(1 for r in RESULTS if r["result"] == "MANUAL")
    print("\n════════════════════════════════════")
    print(f"  PASS={passed}  FAIL={failed}  MANUAL(수동확인)={manual}  총={len(RESULTS)}")
    print("════════════════════════════════════")
    if failed:
        print("\n실패 항목:")
        for r in RESULTS:
            if r["result"] == "FAIL":
                print(f"  ❌ [{r['scenario']}] {r['item']} — {r['evidence']}")

    payload = {
        "base": args.base, "project_id": project_id,
        "passed": passed, "failed": failed, "manual": manual,
        "results": RESULTS,
    }
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=2)
    print(f"\n결과 JSON: {args.out}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())