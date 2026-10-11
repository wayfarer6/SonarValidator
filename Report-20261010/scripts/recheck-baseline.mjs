/**
 * 격리 전 기준 통신 재측정 + 최종 상태 재확인 + 스크린샷.
 * D-baseline 이 터미널 WebSocket 일시 거절로 실패했으므로 재시도 로직을 넣어 다시 측정한다.
 */
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require("/home/osboxes/.local/share/sonar-e2e-2/browser/node_modules/playwright");

const root = path.resolve(".");
const screenshotDir = path.join(root, "Report-20261010", "screenshots");
const evidenceDir = path.join(root, "Report-20261010", "evidence");

const env = Object.fromEntries(fs.readFileSync(path.join(root, ".env"), "utf8")
  .split(/\r?\n/).filter(l => l.includes("=") && !l.startsWith("#"))
  .map(l => { const i = l.indexOf("="); let v = l.slice(i + 1).trim();
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
    return [l.slice(0, i).trim(), v]; }));

const base = "http://localhost:5173";
const browser = await chromium.launch({ headless: true, args: ["--disable-dev-shm-usage"] });
const context = await browser.newContext({ viewport: { width: 1600, height: 1000 }, locale: "ko-KR", timezoneId: "Asia/Seoul" });
const page = await context.newPage();
let terminalOutput = "";
page.on("websocket", ws => ws.on("framereceived", ev => {
  try { const m = JSON.parse(ev.payload.toString());
    if (m.type === "terminal-output" && m.data_base64) terminalOutput += Buffer.from(m.data_base64, "base64").toString();
  } catch {}
}));

const plain = v => v.replace(/\x1b\[[0-9;?]*[ -/]*[@-~]/g, "").replace(/\r/g, "");
async function shot(name, locator = null) {
  await (locator ?? page).screenshot({ path: path.join(screenshotDir, `${name}.png`), ...(locator ? {} : { fullPage: true }) });
  fs.writeFileSync(path.join(evidenceDir, `${name}.txt`), await page.locator("body").innerText());
  console.log("SHOT " + name);
}
async function api(ep) {
  return page.evaluate(async e => { const r = await fetch(`http://${location.hostname}:3000/api/v1/${e}`, { credentials: "include" });
    const t = await r.text(); let b = null; try { b = t ? JSON.parse(t) : null; } catch { b = t.slice(0, 400); } return { status: r.status, body: b }; }, ep);
}

const result = { started_at: new Date().toISOString(), attempts: [] };
try {
  // 로그인
  await page.goto(`${base}/signin`, { waitUntil: "networkidle" });
  const user = page.getByPlaceholder("admin", { exact: true });
  for (let i = 0; i < 20 && (await user.count()) === 0; i++) await page.waitForTimeout(300);
  if (await user.count()) {
    await user.fill("admin");
    await page.getByPlaceholder("비밀번호를 입력하세요").fill(env.SONAR_ADMIN_PASSWORD ?? "admin");
    await Promise.all([page.waitForURL(u => !u.pathname.includes("signin"), { timeout: 20_000 }),
      page.getByRole("button", { name: "Sign in" }).click()]);
  }
  const me = await api("auth/me");
  if (me.status !== 200) throw new Error("login failed " + me.status);
  console.log("PASS login");

  // 터미널 재시도 루프
  const command = "nft list table inet sonar_quarantine >/dev/null 2>&1 && echo TABLE_PRESENT || echo TABLE_ABSENT; ping -c 2 -W 1 10.10.131.10 >/dev/null; r131=$?; ping -c 2 -W 1 10.10.132.10 >/dev/null; r132=$?; echo BASELINE_131=$r131 BASELINE_132=$r132; printf 'BASE_DONE_%s\\n' $((600+6))";
  let ok = false;
  for (let attempt = 1; attempt <= 4 && !ok; attempt++) {
    terminalOutput = "";
    const rec = { attempt };
    try {
      await page.goto(`${base}/project`, { waitUntil: "networkidle" });
      const agent = page.locator("li").filter({ has: page.getByText("Firewall", { exact: true }) });
      await agent.getByRole("button", { name: "터미널 열기", exact: true }).click({ timeout: 10_000 });
      await agent.getByText("연결됨", { exact: true }).waitFor({ timeout: 15_000 });
      const input = agent.locator(".xterm-helper-textarea");
      await input.focus();
      await page.keyboard.type(command);
      await page.keyboard.press("Enter");
      const deadline = Date.now() + 25_000;
      while (!terminalOutput.includes("BASE_DONE_606") && Date.now() < deadline) await page.waitForTimeout(400);
      rec.output = plain(terminalOutput);
      if (terminalOutput.includes("BASE_DONE_606")) {
        await shot(`terminal-baseline-recheck`, agent);
        await agent.getByRole("button", { name: "터미널 닫기", exact: true }).first().click();
        ok = true;
      } else {
        await agent.getByRole("button", { name: "터미널 닫기", exact: true }).first().click().catch(() => {});
      }
    } catch (e) { rec.error = e.message; }
    result.attempts.push(rec);
    console.log(`attempt ${attempt}: ${ok ? "OK" : (rec.error || "no marker")}`);
    if (!ok) await page.waitForTimeout(3000);
  }
  if (!ok) throw new Error("baseline terminal recheck failed after retries");

  const out = result.attempts.find(a => a.output)?.output ?? "";
  // 터미널이 입력한 명령을 그대로 에코하므로, echo 인자에 포함된 문자열이 아니라
  // 독립된 출력 줄(^TABLE_...)만 격리 테이블 존재 여부로 판정한다.
  result.baseline_table = /^TABLE_PRESENT$/m.test(out) ? "PRESENT"
    : /^TABLE_ABSENT$/m.test(out) ? "ABSENT" : "unknown";
  result.baseline_131 = /BASELINE_131=(\d+)/.exec(out)?.[1];
  result.baseline_132 = /BASELINE_132=(\d+)/.exec(out)?.[1];
  fs.writeFileSync(path.join(evidenceDir, "terminal-baseline-recheck.txt"), out);

  // 최종 상태 API 재확인
  const projects = await api("projects");
  const ids = (projects.body.projects ?? []).map(p => p.project_id);
  let projectId = null;
  for (const id of ids) { const ed = await api(`projects/${id}/editing`);
    if ((ed.body?.subnets ?? []).some(s => s.vlan_id === 131)) { projectId = id; break; } }
  const editing = (await api(`projects/${projectId}/editing`)).body;
  const quar = (await api(`quarantine?project_id=${projectId}`)).body;
  const ov = (await api(`agents/overview?project_id=${projectId}`)).body;
  result.project_id = projectId;
  result.vlans = editing.subnets.filter(s => [131, 132, 133].includes(s.vlan_id)).map(s => `${s.vlan_id}:${s.subnet_class}`);
  result.active_quarantine = quar?.total ?? null;
  result.agents = { connected: ov?.connected, total: (ov?.agents ?? []).length };

  // 관리망/백엔드 접속 확인 (Node 컨텍스트에서; 페이지 fetch 는 CORS 로 막힘)
  const health = (await fetch("http://localhost:3000/actuator/health")).status;
  result.backend_health_status = health;

  await page.goto(`${base}/project/editor/${projectId}`, { waitUntil: "networkidle" });
  await page.waitForTimeout(1500);
  await shot("24-final-state-recheck");

  result.pass = result.baseline_131 === "0" && result.baseline_132 === "0" && result.active_quarantine === 0 && health === 200;
  result.completed_at = new Date().toISOString();
  fs.writeFileSync(path.join(evidenceDir, "baseline-recheck.json"), JSON.stringify(result, null, 2));
  console.log(JSON.stringify({ pass: result.pass, table: result.baseline_table, b131: result.baseline_131, b132: result.baseline_132, active: result.active_quarantine, vlans: result.vlans, agents: result.agents }, null, 2));
} catch (e) {
  result.failure = e.message; result.pass = false;
  fs.writeFileSync(path.join(evidenceDir, "baseline-recheck.json"), JSON.stringify(result, null, 2));
  console.error("FAIL: " + e.message);
  process.exitCode = 1;
} finally { await browser.close(); }
