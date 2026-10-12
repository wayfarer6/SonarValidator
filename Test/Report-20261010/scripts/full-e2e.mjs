/**
 * SonarValidator 전체 기능 검증 + 스크린샷 + 격리 실측 스크립트
 *
 * 전제: ./Test/dev.sh 로 Frontend(:5173) 와 Backend(:3000) 가 기동된 상태.
 * 산출물: Report-20261010/{evidence,screenshots}/
 *
 * 단계:
 *  A. 로그인/인증
 *  B. 전체 화면 회귀 + 스크린샷
 *  C. VLAN 등급 저장/새로고침 영속성
 *  D. 격리 정책 실측 (Firewall 터미널 ping → 격리 → ping → 해제 → ping)
 */
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require(
  "/home/osboxes/.local/share/sonar-e2e-2/browser/node_modules/playwright",
);

const root = path.resolve(".");
const reportDir = path.join(root, "Report-20261010");
const screenshotDir = path.join(reportDir, "screenshots");
const evidenceDir = path.join(reportDir, "evidence");
fs.mkdirSync(screenshotDir, { recursive: true });
fs.mkdirSync(evidenceDir, { recursive: true });

function readEnv(file) {
  const result = {};
  for (const raw of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#") || !line.includes("=")) continue;
    const i = line.indexOf("=");
    const key = line.slice(0, i).trim();
    let value = line.slice(i + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    result[key] = value;
  }
  return result;
}

const env = readEnv(path.join(root, ".env"));
// dev.sh 는 .env 의 SONAR_CORS_ALLOWED_ORIGINS 를 로드하지 않으므로 백엔드는 기본값
// (localhost:5173)만 허용한다. 따라서 브라우저는 localhost 로 접속해야 CORS 를 통과한다.
const HOST = process.env.SONAR_E2E_HOST || "localhost";
const base = `http://${HOST}:5173`;
const apiBase = `http://${HOST}:3000`;

const browser = await chromium.launch({ headless: true, args: ["--disable-dev-shm-usage"] });
const context = await browser.newContext({
  viewport: { width: 1600, height: 1000 },
  locale: "ko-KR",
  timezoneId: "Asia/Seoul",
});
const page = await context.newPage();

const errors = [];
const evidence = {
  run: "Report-20261010",
  frontend: base,
  backend: apiBase,
  started_at: new Date().toISOString(),
  steps: [],
  screenshots: [],
};
page.on("pageerror", e => errors.push({ type: "pageerror", message: e.message }));
page.on("response", r => {
  if (r.status() >= 400 && r.url().includes("/api/"))
    errors.push({ type: "http", status: r.status(), url: r.url() });
});

let terminalOutput = "";
page.on("websocket", ws => {
  ws.on("framereceived", ev => {
    try {
      const m = JSON.parse(ev.payload.toString());
      if (m.type === "terminal-output" && m.data_base64)
        terminalOutput += Buffer.from(m.data_base64, "base64").toString();
    } catch { /* non-JSON control frames */ }
  });
});

const shotList = [];
async function shot(name, locator = null) {
  const target = locator ?? page;
  const file = path.join(screenshotDir, `${name}.png`);
  await target.screenshot({ path: file, ...(locator ? {} : { fullPage: true }) });
  fs.writeFileSync(path.join(evidenceDir, `${name}.txt`), await page.locator("body").innerText());
  shotList.push(name);
  console.log("SHOT " + name);
}

async function api(endpoint, options = {}) {
  return page.evaluate(async ({ ep, opt }) => {
    const r = await fetch(`http://${location.hostname}:3000/api/v1/${ep}`, {
      credentials: "include",
      headers: { "Content-Type": "application/json", ...(opt.headers || {}) },
      ...opt,
    });
    const text = await r.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text.slice(0, 500); }
    return { status: r.status, body };
  }, { ep: endpoint, opt: options });
}

const stepResults = [];
async function step(name, fn) {
  const entry = { name, ok: true };
  try { entry.data = await fn(); }
  catch (e) { entry.ok = false; entry.error = e.message; await shot(`99-fail-${name}`).catch(() => {}); }
  stepResults.push(entry);
  console.log((entry.ok ? "PASS " : "FAIL ") + name + (entry.error ? " :: " + entry.error : ""));
  fs.writeFileSync(path.join(evidenceDir, "steps-progress.json"), JSON.stringify(stepResults, null, 2));
  return entry;
}

function plain(v) { return v.replace(/\x1b\[[0-9;?]*[ -/]*[@-~]/g, "").replace(/\r/g, ""); }

async function login() {
  await page.goto(`${base}/signin`, { waitUntil: "networkidle" });
  const username = page.getByPlaceholder("admin", { exact: true });
  for (let i = 0; i < 20 && (await username.count()) === 0; i++) await page.waitForTimeout(300);
  if (await username.count()) {
    await username.fill("admin");
    await page.getByPlaceholder("비밀번호를 입력하세요").fill(env.SONAR_ADMIN_PASSWORD ?? "admin");
    await Promise.all([
      page.waitForURL(u => !u.pathname.includes("signin"), { timeout: 20_000 }),
      page.getByRole("button", { name: "Sign in" }).click(),
    ]);
  }
  const me = await api("auth/me");
  if (me.status !== 200 || me.body?.authenticated !== true)
    throw new Error(`login failed: ${JSON.stringify(me)}`);
  return me.body;
}

async function runTerminal(label, agentName, command, marker, timeoutMs = 30_000) {
  terminalOutput = "";
  await page.goto(`${base}/project`, { waitUntil: "networkidle" });
  const agent = page.locator("li").filter({ has: page.getByText(agentName, { exact: true }) });
  if ((await agent.count()) !== 1) throw new Error(`${agentName} project card count=${await agent.count()}`);
  await agent.getByRole("button", { name: "터미널 열기", exact: true }).click();
  await agent.getByText("연결됨", { exact: true }).waitFor({ timeout: 20_000 });
  const input = agent.locator(".xterm-helper-textarea");
  await input.focus();
  await page.keyboard.type(command);
  await page.keyboard.press("Enter");
  const deadline = Date.now() + timeoutMs;
  while (!terminalOutput.includes(marker) && Date.now() < deadline) await page.waitForTimeout(400);
  await shot(`terminal-${label}`, agent);
  fs.writeFileSync(path.join(evidenceDir, `terminal-${label}.txt`), terminalOutput);
  await agent.getByRole("button", { name: "터미널 닫기", exact: true }).first().click();
  if (!terminalOutput.includes(marker)) throw new Error(`marker ${marker} not seen for ${label}`);
  return terminalOutput;
}

async function pollQuarantine(projectId, expectActive, timeoutMs = 40_000) {
  const deadline = Date.now() + timeoutMs;
  let last;
  while (Date.now() < deadline) {
    last = await api(`quarantine?project_id=${projectId}`);
    const match = (last.body?.quarantined ?? []).find(
      s => s.agent_id === "Firewall" && s.target_cidr === "10.10.131.0/24");
    if (expectActive && match?.applied === true) return match;
    if (!expectActive && !match) return null;
    await page.waitForTimeout(1_000);
  }
  throw new Error(`quarantine active=${expectActive} not reached: ${JSON.stringify(last?.body)}`);
}

// ─────────────────────────────────────────────────────────────────────────
try {
  // A. 로그인
  const me = await step("A-login", async () => { const r = await login(); return r; });
  await shot("00-after-login");
  if (!me.ok) throw new Error("login failed, aborting");

  // 대상 프로젝트 선정 (VLAN 131 을 가진 프로젝트)
  const projects = await api("projects");
  const ids = (projects.body.projects ?? []).map(p => p.project_id);
  let projectId = null;
  for (const id of ids) {
    const ed = await api(`projects/${id}/editing`);
    if ((ed.body?.subnets ?? []).some(s => s.vlan_id === 131)) { projectId = id; break; }
  }
  if (!projectId) throw new Error("VLAN 131 을 가진 프로젝트를 찾지 못했습니다: " + JSON.stringify(ids));
  evidence.project_id = projectId;
  console.log("PROJECT " + projectId);

  // B. 전체 화면 회귀 + 스크린샷
  const routes = [
    ["01-dashboard", "/"],
    ["02-project-list", "/project"],
    ["03-agent", "/agent"],
    ["04-api-nodes", "/api-nodes"],
    ["05-project-editor", `/project/editor/${projectId}`],
    ["06-subnet-advance", `/project/create/subnet?project_id=${projectId}`],
    ["07-segmentation", `/project/create/segmentation?project_id=${projectId}`],
    ["08-preview", `/project/create/preview?project_id=${projectId}`],
    ["09-view-nodes", `/project/create/ViewNodes?project_id=${projectId}`],
    ["10-compliance", "/compliance"],
    ["11-policy", "/policy"],
    ["12-log", "/log"],
    ["13-network", "/network"],
    ["14-notification", "/notification"],
    ["15-profile", "/profile"],
  ];
  await step("B-screen-regression", async () => {
    const seen = [];
    for (const [name, route] of routes) {
      await page.goto(base + route, { waitUntil: "networkidle" });
      await page.waitForTimeout(2000);
      await shot(name);
      seen.push({ name, route, title: await page.title() });
    }
    return seen;
  });

  // C. VLAN 등급 저장/영속성 (131/132/133 = Confidential 로 저장 → 새로고침 유지)
  await step("C-vlan-persist", async () => {
    const before = (await api(`projects/${projectId}/editing`)).body;
    const targets = before.subnets.filter(s => [131, 132, 133].includes(s.vlan_id));
    if (targets.length !== 3) throw new Error("VLAN 131/132/133 없음: " + targets.length);
    await page.goto(`${base}/project/editor/${projectId}`, { waitUntil: "networkidle" });
    await page.getByText(projectId, { exact: true }).waitFor();
    const table = page.getByRole("heading", { name: "서브넷 등급 설정" })
      .locator("xpath=ancestor::div[contains(@class,'rounded-xl')][1]");
    for (const s of targets) {
      const nameInput = table.getByLabel(`${s.id} 이름`, { exact: true });
      const row = nameInput.locator("xpath=ancestor::tr");
      await row.locator("select").first().selectOption("Confidential");
    }
    await shot("16-three-vlans-confidential-draft");
    const savePromise = page.waitForResponse(r =>
      r.url().includes(`/api/v1/projects/${projectId}`) && r.request().method() === "PUT");
    await page.getByRole("button", { name: "저장 및 검증", exact: true }).first().click();
    const saveRes = await savePromise;
    await page.getByText(/망분리 검증 (통과|실패)/).waitFor({ timeout: 20_000 });
    await shot("17-confidential-saved-validation");
    const after = (await api(`projects/${projectId}/editing`)).body;
    const persisted = after.subnets.filter(s => [131, 132, 133].includes(s.vlan_id))
      .map(s => ({ id: s.id, vlan: s.vlan_id, cls: s.subnet_class }));
    if (persisted.length !== 3 || persisted.some(s => s.cls !== "Confidential"))
      throw new Error("등급 영속 실패: " + JSON.stringify(persisted));
    return { saveStatus: saveRes.status(), persisted };
  });

  // 정책 푸시 안전장치
  await step("C2-policy-push-guard", async () => {
    const push = page.getByRole("button", { name: "정책 푸시", exact: true });
    if ((await push.count()) === 0) return { status: "no-button" };
    const disabled = await push.isDisabled();
    await shot("18-policy-push");
    return { disabled, title: await push.getAttribute("title") };
  });

  // D. 격리 정책 실측
  await step("D-baseline-reachability", async () => {
    const out = plain(await runTerminal(
      "baseline",
      "Firewall",
      "ping -c 2 -W 1 10.10.131.10 >/dev/null; r131=$?; ping -c 2 -W 1 10.10.132.10 >/dev/null; r132=$?; echo BASELINE_131=$r131 BASELINE_132=$r132; printf 'BASELINE_DONE_%s\\n' $((700+7))",
      "BASELINE_DONE_707",
    ));
    if (!/BASELINE_131=0 BASELINE_132=0/.test(out))
      throw new Error("격리 전 통신 실패: " + JSON.stringify(out.slice(-400)));
    return out.match(/BASELINE_131=0 BASELINE_132=0/)[0];
  });

  await step("D2-quarantine-131", async () => {
    await page.goto(`${base}/agent`, { waitUntil: "networkidle" });
    const row = page.locator("tbody tr").filter({ hasText: "Firewall" }).first();
    await row.scrollIntoViewIfNeeded();
    await shot("19-agent-before-quarantine");
    await row.getByRole("button", { name: "서브넷 격리", exact: true }).click();
    await page.getByRole("heading", { name: "격리할 VLAN 서브넷 선택" }).waitFor();
    await page.getByLabel(/VLAN131/).check();
    await shot("20-quarantine-modal-selected");
    const respPromise = page.waitForResponse(r =>
      r.url().includes("/api/v1/quarantine/Firewall") && r.request().method() === "POST");
    await page.getByRole("button", { name: "선택한 서브넷 격리", exact: true }).click();
    const resp = await respPromise;
    const applied = await pollQuarantine(projectId, true);
    await page.getByRole("button", { name: "새로고침", exact: true }).click();
    await page.waitForTimeout(800);
    await page.getByText(/10\.10\.131\.0\/24 · 적용 확인/).waitFor({ timeout: 10_000 });
    await shot("21-quarantine-applied");
    return { requestStatus: resp.status(), applied };
  });

  await step("D3-isolation-proof", async () => {
    const out = plain(await runTerminal(
      "isolation-proof",
      "Firewall",
      "nft list table inet sonar_quarantine >/dev/null 2>&1 && echo TABLE_PRESENT || echo TABLE_ABSENT; ping -c 2 -W 1 10.10.131.10 >/dev/null; r131=$?; ping -c 2 -W 1 10.10.132.10 >/dev/null; r132=$?; echo ISOLATED_131=$r131 UNAFFECTED_132=$r132; printf 'ISO_DONE_%s\\n' $((800+8))",
      "ISO_DONE_808",
    ));
    if (!/ISOLATED_131=[1-9]/.test(out) || !/UNAFFECTED_132=0/.test(out))
      throw new Error("격리 실측 실패(131 차단/132 유지 아님): " + JSON.stringify(out.slice(-400)));
    return out.match(/ISOLATED_131=\d+ UNAFFECTED_132=\d+/)[0];
  });

  await step("D4-release", async () => {
    await page.goto(`${base}/agent`, { waitUntil: "networkidle" });
    const row = page.locator("tbody tr").filter({ hasText: "Firewall" }).first();
    await row.getByText(/10\.10\.131\.0\/24 · 적용 확인/).waitFor({ timeout: 10_000 });
    const respPromise = page.waitForResponse(r =>
      r.url().includes("/api/v1/quarantine/Firewall") && r.request().method() === "DELETE");
    await row.getByRole("button", { name: "서브넷 해제", exact: true }).click();
    const resp = await respPromise;
    await pollQuarantine(projectId, false);
    await page.getByRole("button", { name: "새로고침", exact: true }).click();
    await page.waitForTimeout(800);
    await shot("22-quarantine-released");
    return { requestStatus: resp.status() };
  });

  await step("D5-recovery-proof", async () => {
    const out = plain(await runTerminal(
      "recovery-proof",
      "Firewall",
      "nft list table inet sonar_quarantine >/dev/null 2>&1 && echo TABLE_PRESENT || echo TABLE_REMOVED; ping -c 2 -W 1 10.10.131.10 >/dev/null; r131=$?; ping -c 2 -W 1 10.10.132.10 >/dev/null; r132=$?; echo RECOVERED_131=$r131 RECOVERED_132=$r132; printf 'REC_DONE_%s\\n' $((900+9))",
      "REC_DONE_909",
    ));
    if (!out.includes("TABLE_REMOVED") || !/RECOVERED_131=0 RECOVERED_132=0/.test(out))
      throw new Error("복구 실패: " + JSON.stringify(out.slice(-400)));
    return { table: "REMOVED", reachability: out.match(/RECOVERED_131=0 RECOVERED_132=0/)[0] };
  });

  // E. 최종 상태
  await step("E-final-state", async () => {
    await page.goto(`${base}/project/editor/${projectId}`, { waitUntil: "networkidle" });
    await page.getByText(projectId, { exact: true }).waitFor();
    await page.waitForTimeout(1500);
    await shot("23-final-subnet-state");
    const editing = (await api(`projects/${projectId}/editing`)).body;
    const quar = (await api(`quarantine?project_id=${projectId}`)).body;
    const ov = (await api(`agents/overview?project_id=${projectId}`)).body;
    return {
      vlans: editing.subnets.filter(s => [131, 132, 133].includes(s.vlan_id))
        .map(s => `${s.vlan_id}:${s.subnet_class}`),
      activeQuarantine: quar?.total ?? null,
      agentsConnected: ov?.connected ?? null,
      agentsTotal: (ov?.agents ?? []).length,
    };
  });

  evidence.steps = stepResults;
  evidence.screenshots = shotList;
  evidence.browser_errors = errors;
  evidence.completed_at = new Date().toISOString();
  evidence.pass = stepResults.every(s => s.ok);
  fs.writeFileSync(path.join(evidenceDir, "result.json"), JSON.stringify(evidence, null, 2));
  console.log("=== SUMMARY ===");
  console.log(JSON.stringify({ pass: evidence.pass, screenshots: shotList.length,
    steps: stepResults.map(s => ({ name: s.name, ok: s.ok, error: s.error })) }, null, 2));
} catch (err) {
  evidence.failure = { message: err.message };
  evidence.steps = stepResults;
  evidence.screenshots = shotList;
  evidence.browser_errors = errors;
  evidence.pass = false;
  fs.writeFileSync(path.join(evidenceDir, "result.json"), JSON.stringify(evidence, null, 2));
  await shot("99-fatal").catch(() => {});
  console.error("FATAL: " + err.message);
  process.exitCode = 1;
} finally {
  await browser.close();
}
