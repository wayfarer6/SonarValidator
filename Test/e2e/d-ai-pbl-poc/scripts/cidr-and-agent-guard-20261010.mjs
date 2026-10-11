import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require(
  "/home/osboxes/.local/share/sonar-e2e-2/browser/node_modules/playwright",
);

const root = path.resolve(".");
const base = "http://172.16.255.245:5173";
const projectId = "PRJ-AADE1BC3";
const runName = "cidr-and-agent-guard-20261010";
const shotDir = path.join(root, "Test/e2e/d-ai-pbl-poc", "screenshots", runName);
const evidenceDir = path.join(root, "Test/e2e/d-ai-pbl-poc", "evidence", runName);
fs.mkdirSync(shotDir, { recursive: true });
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
        (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    result[key] = value;
  }
  return result;
}

const env = readEnv(path.join(root, ".env"));
const browser = await chromium.launch({ headless: true, args: ["--disable-dev-shm-usage"] });
const context = await browser.newContext({
  viewport: { width: 1600, height: 1000 },
  locale: "ko-KR",
  timezoneId: "Asia/Seoul",
});
const page = await context.newPage();
const runtimeErrors = [];
const result = {
  run_name: runName,
  frontend: base,
  project_id: projectId,
  started_at: new Date().toISOString(),
  invalid_cidr_matrix: [],
};

page.on("pageerror", error => runtimeErrors.push({ type: "page", message: error.message, url: page.url() }));
page.on("response", response => {
  if (response.status() >= 500) {
    runtimeErrors.push({ type: "http", status: response.status(), url: response.url() });
  }
});

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
  await page.getByPlaceholder("admin", { exact: true }).fill("admin");
  await page.getByPlaceholder("비밀번호를 입력하세요").fill(env.SONAR_ADMIN_PASSWORD ?? "admin");
  await Promise.all([
    page.waitForURL(url => !url.pathname.includes("signin"), { timeout: 20_000 }),
    page.getByRole("button", { name: "Sign in" }).click(),
  ]);
}

try {
  await login();
  const initial = await api(`projects/${projectId}/editing`);
  if (initial.status !== 200) throw new Error(`project load failed: ${initial.status}`);
  const vlan131 = initial.body.subnets.find(subnet => subnet.vlan_id === 131);
  if (!vlan131) throw new Error("VLAN 131 missing");

  const cases = [
    ["octet-overflow", "10.10.131.999/24"],
    ["prefix-overflow", "10.10.131.0/33"],
    ["missing-octet", "10.10.131/24"],
    ["non-numeric", "not-an-ip/24"],
  ];

  for (const [name, value] of cases) {
    await page.goto(`${base}/project/editor/${projectId}`, { waitUntil: "networkidle" });
    const nameInput = page.getByLabel(`${vlan131.id} 이름`, { exact: true });
    const row = nameInput.locator("xpath=ancestor::tr");
    const cidrInput = row.locator("input").nth(1);
    await cidrInput.fill(value);
    const [response] = await Promise.all([
      page.waitForResponse(candidate =>
        candidate.url().includes("/api/v1/projects/draft/validation") &&
        candidate.request().method() === "POST",
      ),
      page.getByRole("button", { name: "검증 실행", exact: true }).click(),
    ]);
    let body = null;
    try { body = await response.json(); } catch { body = null; }
    await page.waitForTimeout(300);
    const screen = `invalid-${name}.png`;
    await page.screenshot({ path: path.join(shotDir, screen), fullPage: true });
    const serialized = JSON.stringify(body ?? {});
    const invalidFlagged = response.status() >= 400 ||
      /invalid|유효하지|형식|범위|CIDR.*지정/i.test(serialized);
    result.invalid_cidr_matrix.push({
      name,
      submitted: value,
      response_status: response.status(),
      invalid_flagged: invalidFlagged,
      compliant: body?.compliant ?? null,
      violation_count: body?.violation_count ?? null,
      echoed_in_response: serialized.includes(value),
      screenshot: screen,
      pass: invalidFlagged,
    });
  }

  // 초안 입력은 저장하지 않았으므로 새로고침 뒤 서버 상태가 그대로인지 확인합니다.
  const persisted = await api(`projects/${projectId}/editing`);
  const persisted131 = persisted.body.subnets.find(subnet => subnet.vlan_id === 131);
  result.persistence = {
    expected: vlan131.cidr,
    actual: persisted131?.cidr ?? null,
    pass: persisted131?.cidr === vlan131.cidr,
  };

  await page.goto(`${base}/agent`, { waitUntil: "networkidle" });
  const vmCell = page.getByText("test-vm", { exact: true }).first();
  const vmRow = vmCell.locator("xpath=ancestor::tr");
  const vmText = await vmRow.innerText();
  const vmIsolationButtons = vmRow.getByRole("button", { name: "서브넷 격리", exact: true });

  const unassignedRouter = page.getByText("Survillance-Network-Router", { exact: true }).first();
  const unassignedRow = unassignedRouter.locator("xpath=ancestor::tr");
  const unassignedButton = unassignedRow.getByRole("button", { name: "서브넷 격리", exact: true });

  result.agent_guards = {
    offline_vm: {
      unsupported_label: vmText.includes("VLAN 격리 미지원 (VM)"),
      isolation_button_count: await vmIsolationButtons.count(),
    },
    unassigned_router: {
      isolation_button_present: await unassignedButton.count() === 1,
      isolation_button_disabled: await unassignedButton.isDisabled(),
      title: await unassignedButton.getAttribute("title"),
    },
  };
  result.agent_guards.offline_vm.pass = result.agent_guards.offline_vm.unsupported_label &&
    result.agent_guards.offline_vm.isolation_button_count === 0;
  result.agent_guards.unassigned_router.pass =
    result.agent_guards.unassigned_router.isolation_button_present &&
    result.agent_guards.unassigned_router.isolation_button_disabled &&
    result.agent_guards.unassigned_router.title?.includes("프로젝트에 연결된");
  await page.screenshot({ path: path.join(shotDir, "agent-isolation-guards.png"), fullPage: true });

  const [finalProject, finalQuarantine, finalOverview] = await Promise.all([
    api(`projects/${projectId}/editing`),
    api(`quarantine?project_id=${projectId}`),
    api(`agents/overview?project_id=${projectId}`),
  ]);
  result.final_state = {
    project_status: finalProject.status,
    vlan_131_132_133: finalProject.body?.subnets
      ?.filter(subnet => [131, 132, 133].includes(subnet.vlan_id))
      .map(subnet => ({ vlan_id: subnet.vlan_id, cidr: subnet.cidr, subnet_class: subnet.subnet_class })),
    quarantine_status: finalQuarantine.status,
    active_quarantine_total: finalQuarantine.body?.total ?? null,
    overview_status: finalOverview.status,
    connected_agents: finalOverview.body?.connected ?? null,
    total_agents: finalOverview.body?.total ?? null,
    silent_agents: finalOverview.body?.silent ?? null,
  };
  result.final_state.pass = result.final_state.project_status === 200 &&
    result.final_state.quarantine_status === 200 &&
    result.final_state.overview_status === 200 &&
    result.final_state.active_quarantine_total === 0 &&
    result.final_state.vlan_131_132_133?.length === 3 &&
    result.final_state.vlan_131_132_133.every(subnet => subnet.subnet_class === "Confidential");

  result.runtime_errors = runtimeErrors;
  result.execution_completed = true;
  result.pass = result.invalid_cidr_matrix.every(item => item.pass) &&
    result.persistence.pass &&
    result.agent_guards.offline_vm.pass &&
    result.agent_guards.unassigned_router.pass &&
    result.final_state.pass &&
    runtimeErrors.length === 0;
  result.completed_at = new Date().toISOString();
} catch (error) {
  result.pass = false;
  result.failure = { message: error.message, stack: error.stack };
  await page.screenshot({ path: path.join(shotDir, "failure.png"), fullPage: true }).catch(() => {});
  throw error;
} finally {
  result.runtime_errors ??= runtimeErrors;
  fs.writeFileSync(path.join(evidenceDir, "result.json"), JSON.stringify(result, null, 2));
  await browser.close();
}

console.log(JSON.stringify(result, null, 2));
