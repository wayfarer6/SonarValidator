/**
 * 2차 검증: .env 로더 수정 확인 + 터미널 + 격리 + 등급(Confidential/Sensitive/Open) 및 연결 규칙 검증.
 *
 * 전제: ./Test/dev.sh 로 dev 스택 기동(수정된 .env 로더 적용).
 * 브라우저는 172.16.255.245:5173 로 접속한다 → .env 의 CORS 허용목록이 실제로 적용됐는지 확인하는 의미.
 *
 * 단계:
 *   A 로그인(수정된 CORS 경로)
 *   B 터미널(방화벽+라우터+스위치)
 *   C 격리 왕복(격리 전 ping → VLAN131 격리 → ping 차단 → 해제 → 복구 ping)
 *   D 등급 매트릭스: 131=Confidential, 132=Open, 133=Sensitive 로 설정
 *   E 규칙 131→132(금지쌍) 추가 → 검증 실패 확인
 *   F 해소: 금지 규칙 제거 + 131→133, 133→132(Sensitive 경유) 추가 → 검증 통과 확인
 *   G 원복: 등급/규칙 원상복구 + 최종 상태
 */
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require("/home/osboxes/.local/share/sonar-e2e-2/browser/node_modules/playwright");

const root = path.resolve(".");
const dir = path.join(root, "Report-20261010");
const shotDir = path.join(dir, "screenshots", "round2");
const evDir = path.join(dir, "evidence", "round2");
fs.mkdirSync(shotDir, { recursive: true });
fs.mkdirSync(evDir, { recursive: true });

const env = Object.fromEntries(
  fs.readFileSync(path.join(root, ".env"), "utf8").split(/\r?\n/)
    .filter(l => l.includes("=") && !l.startsWith("#"))
    .map(l => { const i = l.indexOf("="); let v = l.slice(i + 1).trim();
      if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
      return [l.slice(0, i).trim(), v]; }));

const BASE = process.env.R2_BASE || "http://172.16.255.245:5173";
const API_HOST = new URL(BASE).hostname;

const browser = await chromium.launch({ headless: true, args: ["--disable-dev-shm-usage"] });
const context = await browser.newContext({ viewport: { width: 1600, height: 1000 }, locale: "ko-KR", timezoneId: "Asia/Seoul" });
const page = await context.newPage();

const errors = [];
const result = { base: BASE, started_at: new Date().toISOString(), steps: [], screenshots: [] };
page.on("pageerror", e => errors.push({ type: "pageerror", message: e.message }));
page.on("response", r => { if (r.status() >= 400 && r.url().includes("/api/"))
  errors.push({ type: "http", status: r.status(), url: r.url() }); });

let termBuf = "";
page.on("websocket", ws => ws.on("framereceived", ev => {
  try { const m = JSON.parse(ev.payload.toString());
    if (m.type === "terminal-output" && m.data_base64) termBuf += Buffer.from(m.data_base64, "base64").toString();
  } catch {}
}));

const shots = [];
async function shot(name, locator = null) {
  await (locator ?? page).screenshot({ path: path.join(shotDir, `${name}.png`), ...(locator ? {} : { fullPage: true }) });
  fs.writeFileSync(path.join(evDir, `${name}.txt`), await page.locator("body").innerText());
  shots.push(name); console.log("SHOT " + name);
}
async function api(ep, options = {}) {
  return page.evaluate(async ({ ep, opt }) => {
    const r = await fetch(`http://${location.hostname}:3000/api/v1/${ep}`, {
      credentials: "include", headers: { "Content-Type": "application/json", ...(opt.headers || {}) }, ...opt });
    const t = await r.text(); let b = null;
    try { b = t ? JSON.parse(t) : null; } catch { b = t.slice(0, 400); }
    return { status: r.status, body: b };
  }, { ep, opt: options });
}
const plain = v => v.replace(/\x1b\[[0-9;?]*[ -/]*[@-~]/g, "").replace(/\r/g, "");

const steps = [];
async function step(name, fn) {
  const e = { name, ok: true };
  try { e.data = await fn(); } catch (err) { e.ok = false; e.error = err.message; await shot(`99-fail-${name}`).catch(() => {}); }
  steps.push(e);
  console.log((e.ok ? "PASS " : "FAIL ") + name + (e.error ? " :: " + e.error : ""));
  fs.writeFileSync(path.join(evDir, "progress.json"), JSON.stringify(steps, null, 2));
  return e;
}

async function login() {
  await page.goto(`${BASE}/signin`, { waitUntil: "networkidle" });
  const user = page.getByPlaceholder("admin", { exact: true });
  for (let i = 0; i < 20 && (await user.count()) === 0; i++) await page.waitForTimeout(300);
  if (await user.count()) {
    await user.fill("admin");
    await page.getByPlaceholder("비밀번호를 입력하세요").fill(env.SONAR_ADMIN_PASSWORD ?? "admin");
    await Promise.all([ page.waitForURL(u => !u.pathname.includes("signin"), { timeout: 25_000 }),
      page.getByRole("button", { name: "Sign in" }).click() ]);
  }
  const me = await api("auth/me");
  if (me.status !== 200 || me.body?.authenticated !== true)
    throw new Error("login failed " + me.status + " " + JSON.stringify(me.body));
  return me.body;
}

async function runTerminal(label, agentName, command, marker, timeoutMs = 30_000, attempts = 4) {
  const isRouter = /Router/i.test(agentName);
  let lastErr = "";
  for (let a = 1; a <= attempts; a++) {
    termBuf = "";
    try {
      await page.goto(`${BASE}/project`, { waitUntil: "networkidle" });
      const agent = page.locator("li").filter({ has: page.getByText(agentName, { exact: true }) });
      if ((await agent.count()) !== 1) throw new Error(`${agentName} card count=${await agent.count()}`);
      await agent.getByRole("button", { name: "터미널 열기", exact: true }).click({ timeout: 10_000 });
      await agent.getByText("연결됨", { exact: true }).waitFor({ timeout: 15_000 });
      const input = agent.locator(".xterm-helper-textarea");
      await input.focus();

      // 라우터(FRR)는 로그인 프로필에서 vtysh 를 less pager 로 띄운다. pager 를 걷어내고
      // paging 을 끈 뒤 shell 로 빠져나와야 명령이 실행된다.
      if (isRouter) {
        for (let i = 0; i < 8 && !termBuf.includes("frr#"); i++) { await page.keyboard.press("Enter"); await page.waitForTimeout(400); }
        if (!termBuf.includes("frr#")) { await page.keyboard.press("q"); await page.waitForTimeout(400); for (let i = 0; i < 4 && !termBuf.includes("frr#"); i++) { await page.keyboard.press("Enter"); await page.waitForTimeout(400); } }
        await page.keyboard.type("terminal length 0"); await page.keyboard.press("Enter"); await page.waitForTimeout(700);
        await page.keyboard.type("show ip route"); await page.keyboard.press("Enter"); await page.waitForTimeout(1800);
        await page.keyboard.type("exit"); await page.keyboard.press("Enter"); await page.waitForTimeout(1000);
        // vtysh 는 입력을 에코하므로, 산술 결과(8888888)가 보이는지로 shell 여부 판정
        let shellReady = false;
        for (let at = 0; at < 5 && !shellReady; at++) {
          await page.keyboard.type("echo $((1234567+7654321))"); await page.keyboard.press("Enter");
          for (let i = 0; i < 8; i++) { await page.waitForTimeout(300); if (termBuf.includes("8888888")) break; }
          shellReady = termBuf.includes("8888888");
          if (!shellReady) { await page.keyboard.press("Enter"); await page.waitForTimeout(200); await page.keyboard.type("exit"); await page.keyboard.press("Enter"); await page.waitForTimeout(900); }
        }
        if (!shellReady) throw new Error(`${agentName} shell not reached`);
      }

      await page.keyboard.type(command);
      await page.keyboard.press("Enter");
      const deadline = Date.now() + timeoutMs;
      while (!termBuf.includes(marker) && Date.now() < deadline) await page.waitForTimeout(400);
      await shot(`terminal-${label}`, agent);
      fs.writeFileSync(path.join(evDir, `terminal-${label}.txt`), termBuf);
      await agent.getByRole("button", { name: "터미널 닫기", exact: true }).first().click().catch(() => {});
      if (!termBuf.includes(marker)) throw new Error(`marker ${marker} not seen (attempt ${a})`);
      return plain(termBuf);
    } catch (err) { lastErr = err.message; await page.waitForTimeout(3000); }
  }
  throw new Error(`terminal ${agentName}: ${lastErr}`);
}

async function pollQuarantine(projectId, expectActive, timeoutMs = 40_000) {
  const deadline = Date.now() + timeoutMs; let last;
  while (Date.now() < deadline) {
    last = await api(`quarantine?project_id=${projectId}`);
    const m = (last.body?.quarantined ?? []).find(s => s.agent_id === "Firewall" && s.target_cidr === "10.10.131.0/24");
    if (expectActive && m?.applied === true) return m;
    if (!expectActive && !m) return null;
    await page.waitForTimeout(1_000);
  }
  throw new Error(`quarantine active=${expectActive} not reached: ${JSON.stringify(last?.body)}`);
}

function subnetTable() {
  return page.getByRole("heading", { name: "서브넷 등급 설정" })
    .locator("xpath=ancestor::div[contains(@class,'rounded-xl')][1]");
}
async function setGrade(subnetId, grade) {
  const nameInput = subnetTable().getByLabel(`${subnetId} 이름`, { exact: true });
  const row = nameInput.locator("xpath=ancestor::tr");
  await row.locator("select").first().selectOption(grade);
}
function ruleTable() {
  return page.locator("table").filter({ has: page.getByRole("columnheader", { name: "Rule ID" }) });
}
function ruleRow(ruleId) {
  return ruleTable().locator("tr").filter({ has: page.locator(`select[aria-label="${ruleId} 프로토콜"]`) });
}
async function addRule(ruleId, srcId, dstId, port) {
  await page.locator("button", { hasText: "Add Rule" }).first().click();
  const row = ruleRow(ruleId);
  await row.waitFor({ timeout: 10_000 });
  await row.locator("select").nth(0).selectOption(srcId);
  await row.locator("select").nth(1).selectOption(dstId);
  await row.locator('input[type="number"]').fill(String(port));
}
async function removeRule(ruleId) {
  const row = ruleRow(ruleId);
  await row.locator('button[title="규칙 삭제"]').click();
}
async function saveAndValidate() {
  const respP = page.waitForResponse(r => r.url().includes("/api/v1/projects/") && r.request().method() === "PUT");
  await page.getByRole("button", { name: "저장 및 검증" }).first().click();
  const resp = await respP;
  await page.getByText(/망분리 검증 (통과|실패)/).waitFor({ timeout: 25_000 });
  await page.waitForTimeout(800);
  return resp.status();
}

// ────────────────────────────────────────────────────────────────
let projectId = null;
const VLAN = {}; // vlan id -> subnet id
try {
  await step("A-login", async () => { const me = await login(); await shot("A-01-dashboard"); return me; });

  await step("B-project-select", async () => {
    const ps = await api("projects");
    for (const id of (ps.body.projects ?? []).map(p => p.project_id)) {
      const ed = await api(`projects/${id}/editing`);
      if ((ed.body?.subnets ?? []).some(s => s.vlan_id === 131)) {
        projectId = id;
        for (const s of ed.body.subnets) if ([131, 132, 133].includes(s.vlan_id)) VLAN[s.vlan_id] = s.id;
        return { projectId, vlans: Object.keys(VLAN) };
      }
    }
    throw new Error("VLAN131 프로젝트 없음");
  });

  // ── B. 터미널 ────────────────────────────────────────────────
  await step("B1-terminal-firewall", async () => {
    const out = await runTerminal("firewall", "Firewall",
      "id; hostname; printf 'TERM_FW_%s\\n' $((111+111))", "TERM_FW_222");
    if (!out.includes("uid=0(root)")) throw new Error("not root: " + out.slice(-200));
    return "root ok";
  });
  await step("B2-terminal-router", async () => {
    const out = await runTerminal("router", "Gateway-Router",
      "printf 'TERM_RT_%s\\n' $((222+222))", "TERM_RT_444");
    return "root-seen=" + out.includes("uid=0(root)");
  });
  await step("B3-terminal-switch", async () => {
    const out = await runTerminal("switch", "Switch-3",
      "printf 'TERM_SW_%s\\n' $((333+333))", "TERM_SW_666");
    return "root-seen=" + out.includes("uid=0(root)");
  });

  // ── C. 격리 왕복 ─────────────────────────────────────────────
  await step("C1-baseline-ping", async () => {
    const out = await runTerminal("iso-baseline", "Firewall",
      "nft list table inet sonar_quarantine >/dev/null 2>&1 && echo T_PRESENT || echo T_ABSENT; ping -c2 -W1 10.10.131.10 >/dev/null; r131=$?; ping -c2 -W1 10.10.132.10 >/dev/null; r132=$?; echo BASE_131=$r131 BASE_132=$r132; printf 'READY_%s\\n' $((500+5))",
      "READY_505");
    if (!/BASE_131=0 BASE_132=0/.test(out)) throw new Error("baseline unreachable: " + out.slice(-300));
    return out.match(/BASE_131=0 BASE_132=0/)[0];
  });

  await step("C2-quarantine", async () => {
    await page.goto(`${BASE}/agent`, { waitUntil: "networkidle" });
    const row = page.locator("tbody tr").filter({ hasText: "Firewall" }).first();
    await row.scrollIntoViewIfNeeded(); await shot("C-01-agent-before");
    await row.getByRole("button", { name: "서브넷 격리", exact: true }).click();
    await page.getByRole("heading", { name: "격리할 VLAN 서브넷 선택" }).waitFor();
    await page.getByLabel(/VLAN131/).check(); await shot("C-02-modal");
    const rp = page.waitForResponse(r => r.url().includes("/api/v1/quarantine/Firewall") && r.request().method() === "POST");
    await page.getByRole("button", { name: "선택한 서브넷 격리", exact: true }).click();
    const resp = await rp;
    const applied = await pollQuarantine(projectId, true);
    await page.getByRole("button", { name: "새로고침", exact: true }).click();
    await page.waitForTimeout(800);
    await page.getByText(/10\.10\.131\.0\/24 · 적용 확인/).waitFor({ timeout: 10_000 });
    await shot("C-03-quarantine-applied");
    return { status: resp.status(), applied: applied.applied, detail: applied.applied_detail };
  });

  await step("C3-isolation-proof", async () => {
    const out = await runTerminal("iso-applied", "Firewall",
      "nft list table inet sonar_quarantine >/dev/null 2>&1 && echo TABLE_PRESENT || echo TABLE_ABSENT; ping -c2 -W1 10.10.131.10 >/dev/null; r131=$?; ping -c2 -W1 10.10.132.10 >/dev/null; r132=$?; echo ISO_131=$r131 UNAFF_132=$r132; printf 'DONE_%s\\n' $((600+6))",
      "DONE_606");
    if (!/ISO_131=[1-9]/.test(out) || !/UNAFF_132=0/.test(out))
      throw new Error("isolation failed: " + out.slice(-300));
    return out.match(/ISO_131=\d+ UNAFF_132=\d+/)[0];
  });

  await step("C4-release", async () => {
    await page.goto(`${BASE}/agent`, { waitUntil: "networkidle" });
    const row = page.locator("tbody tr").filter({ hasText: "Firewall" }).first();
    await row.getByText(/10\.10\.131\.0\/24 · 적용 확인/).waitFor({ timeout: 10_000 });
    const rp = page.waitForResponse(r => r.url().includes("/api/v1/quarantine/Firewall") && r.request().method() === "DELETE");
    await row.getByRole("button", { name: "서브넷 해제", exact: true }).click();
    const resp = await rp;
    await pollQuarantine(projectId, false);
    await page.getByRole("button", { name: "새로고침", exact: true }).click();
    await page.waitForTimeout(800); await shot("C-04-released");
    return { status: resp.status() };
  });

  await step("C5-recovery-proof", async () => {
    const out = await runTerminal("iso-recovered", "Firewall",
      "nft list table inet sonar_quarantine >/dev/null 2>&1 && echo TABLE_PRESENT || echo TABLE_ABSENT; ping -c2 -W1 10.10.131.10 >/dev/null; r131=$?; ping -c2 -W1 10.10.132.10 >/dev/null; r132=$?; echo REC_131=$r131 REC_132=$r132; printf 'DONE_%s\\n' $((700+7))",
      "DONE_707");
    if (!/REC_131=0 REC_132=0/.test(out)) throw new Error("recovery failed: " + out.slice(-300));
    return out.match(/REC_131=0 REC_132=0/)[0];
  });

  // ── D. 등급 매트릭스 ─────────────────────────────────────────
  // 먼저 남아 있는 규칙을 모두 정리해 E 단계가 Rule-0001 을 새로 만들 수 있게 한다.
  await step("D0-cleanup-rules", async () => {
    await page.goto(`${BASE}/project/editor/${projectId}`, { waitUntil: "networkidle" });
    await page.getByText(projectId, { exact: true }).waitFor();
    let removed = 0;
    for (let guard = 0; guard < 30; guard++) {
      const del = ruleTable().locator('button[title="규칙 삭제"]');
      if ((await del.count()) === 0) break;
      await del.first().click(); await page.waitForTimeout(200); removed++;
    }
    if (removed > 0) await saveAndValidate();
    const ed = (await api(`projects/${projectId}/editing`)).body;
    return { removed, remainingRules: (ed.rules ?? []).map(r => r.id) };
  });

  await step("D-grades-set", async () => {
    await page.goto(`${BASE}/project/editor/${projectId}`, { waitUntil: "networkidle" });
    await page.getByText(projectId, { exact: true }).waitFor();
    await setGrade(VLAN[131], "Confidential");
    await setGrade(VLAN[132], "Open");
    await setGrade(VLAN[133], "Sensitive");
    await shot("D-01-grades-draft");
    const status = await saveAndValidate();
    await shot("D-02-grades-saved");
    const ed = (await api(`projects/${projectId}/editing`)).body;
    const got = ed.subnets.filter(s => [131, 132, 133].includes(s.vlan_id))
      .map(s => `${s.vlan_id}:${s.subnet_class}`);
    const expected = ["131:Confidential", "132:Open", "133:Sensitive"];
    if (JSON.stringify(got) !== JSON.stringify(expected)) throw new Error("grade persist: " + JSON.stringify(got));
    return { saveStatus: status, grades: got };
  });

  // ── E. 금지쌍 규칙 추가 → 위반 ───────────────────────────────
  await step("E-forbidden-rule", async () => {
    await addRule("Rule-0001", VLAN[131], VLAN[132], 443);   // Confidential → Open (금지)
    await shot("E-01-forbidden-rule-draft");
    const status = await saveAndValidate();
    await shot("E-02-forbidden-rule-result");
    const rep = (await api(`projects/${projectId}/validation`)).body;
    const vCount = rep?.violation_count ?? rep?.violations?.length ?? 0;
    const ruleViolations = (rep?.violations ?? []).filter(v => ["Rule-0001"].includes(v.rule_id));
    if (!(vCount > 0 && ruleViolations.length > 0))
      throw new Error(`expected rule-based violation for Rule-0001: ${JSON.stringify(rep).slice(0, 300)}`);
    return { saveStatus: status, violationCount: vCount,
      ruleViolations: ruleViolations.map(v => `${v.severity}:${v.reason}`) };
  });

  // ── F. 해소: Sensitive 경유 규칙으로 교체 ────────────────────
  // 주의: addRule 은 '가장 작은 미사용 번호'를 다시 쓴다. 규칙을 지우면 그 번호가
  // 재사용되므로, F 에서는 기존 Rule-0001 을 131→133 으로 바꾸고 133→132 를 새로 추가한다.
  // 판정: 등급을 건너뛰는(규칙 기반) 위반이 사라져야 한다. 인터넷 노출·포트 미지정 같은
  //       장치/설정 고유 위반은 이 규칙과 무관하므로 별도로 기록한다.
  await step("F-resolve-via-sensitive", async () => {
    const eRuleViolations = steps.find(s => s.name === "E-forbidden-rule")?.data?.ruleViolations?.length ?? 0;
    const row1 = ruleRow("Rule-0001");
    await row1.waitFor({ timeout: 10_000 });
    await row1.locator("select").nth(1).selectOption(VLAN[133]);   // dst: Open → Sensitive
    await addRule("Rule-0002", VLAN[133], VLAN[132], 443);        // Sensitive → Open
    await shot("F-01-via-sensitive-draft");
    const status = await saveAndValidate();
    await shot("F-02-via-sensitive-result");
    const rep = (await api(`projects/${projectId}/validation`)).body;
    const all = rep?.violations ?? [];
    // 우리 규칙(Rule-0001/Rule-0002)을 참조하는 규칙 기반 위반은 0 이어야 한다.
    const ruleBased = all.filter(v => ["Rule-0001", "Rule-0002"].includes(v.rule_id));
    // 등급 건너뛰기(직접 연결) 위반 메시지도 없어야 한다.
    const gradeJump = all.filter(v => /직접 연결|등급/.test(v.reason ?? ""));
    const unrelated = all.filter(v => /인터넷 노출|기본 경로/.test(v.reason ?? ""))
      .map(v => `${v.severity}:${v.reason}`);
    if (ruleBased.length !== 0) throw new Error("rule-based violation remains: " + JSON.stringify(ruleBased));
    if (gradeJump.length !== 0) throw new Error("grade-jump violation remains: " + JSON.stringify(gradeJump));
    // 등급 규칙 자체는 그대로 살아있음을 확인 (해소는 규칙 설계로 이뤄졌지 정책 약화가 아님)
    const fp = (await api(`projects/${projectId}/forbidden-pairs`)).body;
    return { saveStatus: status, violationCount: rep?.violation_count ?? all.length,
      eRuleViolations, fRuleViolations: ruleBased.length, unrelatedViolations: unrelated,
      forbiddenPairsTotal: fp?.total ?? null,
      forbiddenPairs: (fp?.pairs ?? []).map(p => `${p.src}↔${p.dst}`) };
  });

  // ── G. 원복 ─────────────────────────────────────────────────
  await step("G-restore", async () => {
    await page.goto(`${BASE}/project/editor/${projectId}`, { waitUntil: "networkidle" });
    await page.getByText(projectId, { exact: true }).waitFor();
    // 남아 있는 규칙을 모두 제거 (id 재사용 때문에 개수를 미리 가정하지 않는다)
    for (let guard = 0; guard < 20; guard++) {
      const del = ruleTable().locator('button[title="규칙 삭제"]');
      if ((await del.count()) === 0) break;
      await del.first().click();
      await page.waitForTimeout(200);
    }
    for (const v of [131, 132, 133]) await setGrade(VLAN[v], "Confidential");
    await shot("G-01-restore-draft");
    await saveAndValidate();
    await shot("G-02-restore-result");
    const ed = (await api(`projects/${projectId}/editing`)).body;
    const grades = ed.subnets.filter(s => [131, 132, 133].includes(s.vlan_id)).map(s => `${s.vlan_id}:${s.subnet_class}`);
    const remainingRules = (ed.rules ?? []).map(r => r.id);
    if (grades.some(g => !g.endsWith("Confidential"))) throw new Error("restore grades failed: " + JSON.stringify(grades));
    return { grades, rules: remainingRules };
  });

  await step("H-final", async () => {
    const quar = (await api(`quarantine?project_id=${projectId}`)).body;
    const ov = (await api(`agents/overview?project_id=${projectId}`)).body;
    await page.goto(`${BASE}/project/editor/${projectId}`, { waitUntil: "networkidle" });
    await page.waitForTimeout(1500); await shot("H-01-final");
    return { activeQuarantine: quar?.total ?? null, agentsConnected: ov?.connected, agentsTotal: (ov?.agents ?? []).length };
  });

  result.steps = steps; result.screenshots = shots; result.project_id = projectId;
  result.vlan_ids = VLAN; result.browser_errors = errors;
  result.pass = steps.every(s => s.ok); result.completed_at = new Date().toISOString();
  fs.writeFileSync(path.join(evDir, "result.json"), JSON.stringify(result, null, 2));
  console.log("=== SUMMARY ===");
  console.log(JSON.stringify({ pass: result.pass, screenshots: shots.length,
    steps: steps.map(s => ({ name: s.name, ok: s.ok, error: s.error })) }, null, 2));
} catch (err) {
  result.steps = steps; result.screenshots = shots; result.browser_errors = errors;
  result.pass = false; result.failure = err.message; result.completed_at = new Date().toISOString();
  fs.writeFileSync(path.join(evDir, "result.json"), JSON.stringify(result, null, 2));
  await shot("99-fatal").catch(() => {});
  console.error("FATAL: " + err.message);
  process.exitCode = 1;
} finally { await browser.close(); }
