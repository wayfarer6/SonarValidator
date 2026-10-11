import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require(
  "/home/osboxes/.local/share/sonar-e2e-2/browser/node_modules/playwright",
);

const root = path.resolve(".");
const runName = "final-ui-vlan-quarantine-20261010";
const screenshotDir = path.join(root, "Test/e2e/d-ai-pbl-poc", "screenshots", runName);
const evidenceDir = path.join(root, "Test/e2e/d-ai-pbl-poc", "evidence", runName);
fs.mkdirSync(screenshotDir, { recursive: true });
fs.mkdirSync(evidenceDir, { recursive: true });

function readEnv(file) {
  const result = {};
  for (const raw of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#") || !line.includes("=")) continue;
    const index = line.indexOf("=");
    const key = line.slice(0, index).trim();
    let value = line.slice(index + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    result[key] = value;
  }
  return result;
}

const env = readEnv(path.join(root, ".env"));
const base = "http://172.16.255.245:5173";
const projectId = "PRJ-AADE1BC3";
const targetCidrs = ["10.10.131.0/24", "10.10.132.0/24", "10.10.133.0/24"];
const browser = await chromium.launch({ headless: true, args: ["--disable-dev-shm-usage"] });
const context = await browser.newContext({
  viewport: { width: 1600, height: 1000 },
  locale: "ko-KR",
  timezoneId: "Asia/Seoul",
});
const page = await context.newPage();
const errors = [];
const evidence = {
  run_name: runName,
  frontend: base,
  project_id: projectId,
  target_vlans: [131, 132, 133],
  target_cidrs: targetCidrs,
  started_at: new Date().toISOString(),
  steps: [],
};

page.on("pageerror", error => errors.push({ type: "pageerror", message: error.message }));
page.on("response", response => {
  if (response.status() >= 400 && response.url().includes("/api/")) {
    errors.push({ type: "http", status: response.status(), url: response.url() });
  }
});

let terminalOutput = "";
page.on("websocket", socket => {
  socket.on("framereceived", event => {
    try {
      const message = JSON.parse(event.payload.toString());
      if (message.type === "terminal-output" && message.data_base64) {
        terminalOutput += Buffer.from(message.data_base64, "base64").toString();
      }
    } catch {
      // Terminal streams can contain non-JSON control frames.
    }
  });
});

async function shot(name, locator = null) {
  const target = locator ?? page;
  await target.screenshot({
    path: path.join(screenshotDir, `${name}.png`),
    ...(locator ? {} : { fullPage: true }),
  });
  fs.writeFileSync(path.join(evidenceDir, `${name}.txt`), await page.locator("body").innerText());
}

async function api(endpoint) {
  return page.evaluate(async endpointValue => {
    const response = await fetch(`http://${location.hostname}:3000/api/v1/${endpointValue}`, {
      credentials: "include",
    });
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text.slice(0, 500); }
    return { status: response.status, body };
  }, endpoint);
}

async function login() {
  await page.goto(`${base}/signin`, { waitUntil: "networkidle" });
  const username = page.getByPlaceholder("admin", { exact: true });
  if (await username.count()) {
    await username.fill("admin");
    await page.getByPlaceholder("비밀번호를 입력하세요").fill(env.SONAR_ADMIN_PASSWORD ?? "admin");
    await Promise.all([
      page.waitForURL(url => !url.pathname.includes("signin"), { timeout: 20_000 }),
      page.getByRole("button", { name: "Sign in" }).click(),
    ]);
  }
  const me = await api("auth/me");
  if (me.status !== 200 || me.body?.authenticated !== true) {
    throw new Error(`Login failed: ${JSON.stringify(me)}`);
  }
}

function subnetTable() {
  return page.getByRole("heading", { name: "서브넷 등급 설정" })
    .locator("xpath=ancestor::div[contains(@class,'rounded-xl')][1]");
}

async function setConfidential(vlan, subnetId) {
  const nameInput = subnetTable().getByLabel(`${subnetId} 이름`, { exact: true });
  if (await nameInput.count() !== 1) throw new Error(`Expected one VLAN ${vlan} name input (${subnetId})`);
  const row = nameInput.locator("xpath=ancestor::tr");
  await row.locator("select").first().selectOption("Confidential");
}

async function pollQuarantine(expectedActive, timeoutMs = 40_000) {
  const deadline = Date.now() + timeoutMs;
  let last;
  while (Date.now() < deadline) {
    last = await api(`quarantine?project_id=${projectId}`);
    const matching = (last.body?.quarantined ?? []).find(
      state => state.agent_id === "Firewall" && state.target_cidr === targetCidrs[0],
    );
    if (expectedActive && matching?.applied === true) return { response: last, matching };
    if (!expectedActive && !matching) return { response: last, matching: null };
    await page.waitForTimeout(1_000);
  }
  throw new Error(`Quarantine state did not become ${expectedActive ? "applied" : "released"}: ${JSON.stringify(last)}`);
}

async function runFirewallTerminal(label, command, marker) {
  terminalOutput = "";
  await page.goto(`${base}/project`, { waitUntil: "networkidle" });
  const agent = page.locator("li").filter({ has: page.getByText("Firewall", { exact: true }) });
  if (await agent.count() !== 1) throw new Error(`Firewall project agent card count=${await agent.count()}`);
  await agent.getByRole("button", { name: "터미널 열기", exact: true }).click();
  await agent.getByText("연결됨", { exact: true }).waitFor({ timeout: 20_000 });
  const input = agent.locator(".xterm-helper-textarea");
  await input.focus();
  await page.keyboard.type(command);
  await page.keyboard.press("Enter");
  const deadline = Date.now() + 25_000;
  while (!terminalOutput.includes(marker) && Date.now() < deadline) {
    await page.waitForTimeout(400);
  }
  if (!terminalOutput.includes(marker)) {
    throw new Error(`Terminal marker not observed for ${label}`);
  }
  fs.writeFileSync(path.join(evidenceDir, `terminal-${label}-raw.txt`), terminalOutput);
  await shot(`terminal-${label}`, agent);
  await agent.getByRole("button", { name: "터미널 닫기", exact: true }).first().click();
  return terminalOutput;
}

function plainTerminal(value) {
  return value
    .replace(/\x1b\[[0-9;?]*[ -/]*[@-~]/g, "")
    .replace(/\r/g, "");
}

try {
  await login();
  evidence.before = (await api(`projects/${projectId}/editing`)).body;
  evidence.steps.push({ name: "login", pass: true });

  await page.goto(`${base}/project/editor/${projectId}`, { waitUntil: "networkidle" });
  await page.getByText(projectId, { exact: true }).waitFor();
  await shot("01-project-editor-before");

  for (const vlan of [131, 132, 133]) {
    const subnet = evidence.before.subnets.find(candidate => candidate.vlan_id === vlan);
    if (!subnet) throw new Error(`VLAN ${vlan} is missing from the project`);
    await setConfidential(vlan, subnet.id);
  }
  await shot("02-three-vlans-confidential-draft");

  const saveResponsePromise = page.waitForResponse(response =>
    response.url().includes(`/api/v1/projects/${projectId}`) && response.request().method() === "PUT",
  );
  await page.getByRole("button", { name: "저장 및 검증", exact: true }).first().click();
  const saveResponse = await saveResponsePromise;
  evidence.save = { status: saveResponse.status(), body: await saveResponse.json() };
  await page.getByText(/망분리 검증 (통과|실패)/).waitFor({ timeout: 20_000 });
  await shot("03-confidential-saved-validation");

  evidence.after_confidential = (await api(`projects/${projectId}/editing`)).body;
  const selected = evidence.after_confidential.subnets.filter(subnet => [131, 132, 133].includes(subnet.vlan_id));
  if (selected.length !== 3 || selected.some(subnet => subnet.subnet_class !== "Confidential")) {
    throw new Error(`Confidential persistence check failed: ${JSON.stringify(selected)}`);
  }
  evidence.steps.push({ name: "three_vlans_confidential", pass: true, selected });

  const pushButton = page.getByRole("button", { name: "정책 푸시", exact: true });
  if (await pushButton.count()) {
    if (await pushButton.isDisabled()) {
      evidence.policy_push = {
        status: "blocked_by_validation",
        reason: await pushButton.getAttribute("title"),
      };
    } else {
      const [pushResponse] = await Promise.all([
        page.waitForResponse(response =>
          response.url().includes(`/api/v1/policy/push/${projectId}`) && response.request().method() === "POST",
        ),
        pushButton.click(),
      ]);
      let pushBody;
      try { pushBody = await pushResponse.json(); } catch { pushBody = null; }
      evidence.policy_push = { status: pushResponse.status(), body: pushBody };
      await page.waitForTimeout(1_000);
    }
    await shot("04-policy-push-result");
  }

  const baselineTerminal = await runFirewallTerminal(
    "05-baseline",
    "ping -c 2 -W 1 10.10.131.10 >/dev/null; r131=$?; ping -c 2 -W 1 10.10.132.10 >/dev/null; r132=$?; echo BASELINE_131=$r131 BASELINE_132=$r132; printf 'BASELINE_COMPLETE_%s\\n' $((700+7))",
    "BASELINE_COMPLETE_707",
  );
  evidence.baseline_terminal = baselineTerminal;
  if (!/BASELINE_131=0 BASELINE_132=0/.test(plainTerminal(baselineTerminal))) {
    throw new Error(`Baseline VLAN reachability failed: ${JSON.stringify(plainTerminal(baselineTerminal))}`);
  }

  await page.goto(`${base}/agent`, { waitUntil: "networkidle" });
  const firewallRow = page.locator("tbody tr").filter({ hasText: "Firewall" }).first();
  await firewallRow.scrollIntoViewIfNeeded();
  await shot("06-agent-before-quarantine");
  await firewallRow.getByRole("button", { name: "서브넷 격리", exact: true }).click();
  await page.getByRole("heading", { name: "격리할 VLAN 서브넷 선택" }).waitFor();
  await page.getByLabel(/VLAN131/).check();
  await shot("07-quarantine-modal-selected");

  const quarantineResponsePromise = page.waitForResponse(response =>
    response.url().includes("/api/v1/quarantine/Firewall") && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "선택한 서브넷 격리", exact: true }).click();
  const quarantineResponse = await quarantineResponsePromise;
  evidence.quarantine_request = { status: quarantineResponse.status(), body: await quarantineResponse.json() };
  evidence.quarantine_applied = await pollQuarantine(true);

  await page.getByRole("button", { name: "새로고침", exact: true }).click();
  await page.waitForTimeout(800);
  await page.getByText(/10\.10\.131\.0\/24 · 적용 확인/).waitFor({ timeout: 10_000 });
  await shot("08-quarantine-applied");
  evidence.steps.push({ name: "vlan131_quarantine", pass: true, state: evidence.quarantine_applied.matching });

  const isolatedTerminal = await runFirewallTerminal(
    "09-quarantine-proof",
    "nft list table inet sonar_quarantine; ping -c 2 -W 1 10.10.131.10 >/dev/null; r131=$?; ping -c 2 -W 1 10.10.132.10 >/dev/null; r132=$?; echo ISOLATED_131=$r131 UNAFFECTED_132=$r132; printf 'ISOLATION_COMPLETE_%s\\n' $((800+8))",
    "ISOLATION_COMPLETE_808",
  );
  evidence.isolated_terminal = isolatedTerminal;
  if (!/ISOLATED_131=[1-9][0-9]* UNAFFECTED_132=0/.test(plainTerminal(isolatedTerminal)) ||
      !plainTerminal(isolatedTerminal).includes("table inet sonar_quarantine")) {
    throw new Error(`VLAN isolation proof failed: ${JSON.stringify(plainTerminal(isolatedTerminal))}`);
  }

  await page.goto(`${base}/agent`, { waitUntil: "networkidle" });
  const releaseRow = page.locator("tbody tr").filter({ hasText: "Firewall" }).first();
  await releaseRow.getByText(/10\.10\.131\.0\/24 · 적용 확인/).waitFor({ timeout: 10_000 });
  const releaseResponsePromise = page.waitForResponse(response =>
    response.url().includes("/api/v1/quarantine/Firewall") && response.request().method() === "DELETE",
  );
  await releaseRow.getByRole("button", { name: "서브넷 해제", exact: true }).click();
  const releaseResponse = await releaseResponsePromise;
  evidence.release_request = { status: releaseResponse.status(), body: await releaseResponse.json() };
  evidence.quarantine_released = await pollQuarantine(false);
  await page.getByRole("button", { name: "새로고침", exact: true }).click();
  await page.waitForTimeout(800);
  await shot("10-quarantine-released");

  const releasedTerminal = await runFirewallTerminal(
    "11-release-proof",
    "if nft list table inet sonar_quarantine >/dev/null 2>&1; then echo QUARANTINE_TABLE_PRESENT; else echo QUARANTINE_TABLE_REMOVED; fi; ping -c 2 -W 1 10.10.131.10 >/dev/null; r131=$?; ping -c 2 -W 1 10.10.132.10 >/dev/null; r132=$?; echo RELEASED_131=$r131 UNAFFECTED_132=$r132; printf 'RELEASE_COMPLETE_%s\\n' $((900+9))",
    "RELEASE_COMPLETE_909",
  );
  evidence.released_terminal = releasedTerminal;
  if (!plainTerminal(releasedTerminal).includes("QUARANTINE_TABLE_REMOVED") ||
      !/RELEASED_131=0 UNAFFECTED_132=0/.test(plainTerminal(releasedTerminal))) {
    throw new Error(`VLAN release proof failed: ${JSON.stringify(plainTerminal(releasedTerminal))}`);
  }
  evidence.steps.push({ name: "vlan131_release", pass: true });

  await page.goto(`${base}/project/editor/${projectId}`, { waitUntil: "networkidle" });
  await page.getByText(projectId, { exact: true }).waitFor();
  await shot("12-final-confidential-state");
  evidence.final = {
    project: (await api(`projects/${projectId}/editing`)).body,
    quarantine: (await api(`quarantine?project_id=${projectId}`)).body,
    overview: (await api(`agents/overview?project_id=${projectId}`)).body,
  };
  evidence.completed_at = new Date().toISOString();
  evidence.pass = evidence.final.quarantine.total === 0;
} catch (error) {
  evidence.pass = false;
  evidence.failure = { message: error.message, stack: error.stack };
  await shot("99-failure").catch(() => {});
  throw error;
} finally {
  evidence.browser_errors = errors;
  fs.writeFileSync(path.join(evidenceDir, "result.json"), JSON.stringify(evidence, null, 2));
  await browser.close();
}

console.log(JSON.stringify({
  pass: evidence.pass,
  screenshots: screenshotDir,
  evidence: evidenceDir,
  steps: evidence.steps,
  browser_errors: errors,
}, null, 2));
