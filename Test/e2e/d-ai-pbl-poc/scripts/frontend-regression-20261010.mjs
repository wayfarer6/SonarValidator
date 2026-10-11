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
const runName = "frontend-regression-20261010";
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
const pageErrors = [];
const consoleErrors = [];
const httpErrors = [];
const result = {
  frontend: base,
  project_id: projectId,
  started_at: new Date().toISOString(),
  routes: [],
  known_bugs: [],
};

page.on("pageerror", error => pageErrors.push({ url: page.url(), message: error.message }));
page.on("console", message => {
  if (message.type() === "error") consoleErrors.push({ url: page.url(), text: message.text() });
});
page.on("response", response => {
  if (response.status() >= 400 && response.url().includes("/api/")) {
    httpErrors.push({ status: response.status(), url: response.url(), page: page.url() });
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
  const me = await api("auth/me");
  if (me.status !== 200 || me.body?.authenticated !== true) throw new Error("login failed");
}

try {
  await login();

  const routes = [
    ["dashboard", "/"],
    ["projects", "/project"],
    ["agents", "/agent"],
    ["api-nodes", "/api-nodes"],
    ["profile", "/profile"],
    ["compliance", "/compliance"],
    ["compliance-export", "/compliance/export"],
    ["policy", "/policy"],
    ["policy-export", "/policy/export"],
    ["logs", "/log"],
    ["logs-export", "/log/export"],
    ["network", "/network"],
    ["notifications", "/notification"],
    ["notifications-export", "/notification/export"],
    ["project-editor", `/project/editor/${projectId}`],
  ];

  for (const [name, route] of routes) {
    const errorStart = pageErrors.length;
    const httpStart = httpErrors.length;
    const response = await page.goto(`${base}${route}`, { waitUntil: "networkidle", timeout: 30_000 });
    await page.screenshot({ path: path.join(shotDir, `route-${name}.png`), fullPage: false });
    const body = await page.locator("body").innerText();
    const item = {
      name,
      route,
      document_status: response?.status() ?? null,
      body_length: body.length,
      page_errors: pageErrors.slice(errorStart),
      http_errors: httpErrors.slice(httpStart),
      pass: response?.status() === 200 && body.length > 100 && pageErrors.length === errorStart &&
        !httpErrors.slice(httpStart).some(error => error.status >= 500),
    };
    result.routes.push(item);
    if (!item.pass) throw new Error(`route smoke failed: ${JSON.stringify(item)}`);
  }

  const before = await api(`projects/${projectId}/editing`);
  const vlan131 = before.body.subnets.find(subnet => subnet.vlan_id === 131);
  if (!vlan131) throw new Error("VLAN 131 missing");

  await page.goto(`${base}/project/editor/${projectId}`, { waitUntil: "networkidle" });
  const nameInput = page.getByLabel(`${vlan131.id} 이름`, { exact: true });
  const row = nameInput.locator("xpath=ancestor::tr");
  const cidrInput = row.locator("input").nth(1);
  await cidrInput.fill("10.10.131.999/24");
  await page.screenshot({ path: path.join(shotDir, "negative-invalid-cidr-draft.png"), fullPage: true });

  const [validationResponse] = await Promise.all([
    page.waitForResponse(response =>
      response.url().includes("/api/v1/projects/draft/validation") && response.request().method() === "POST",
    ),
    page.getByRole("button", { name: "검증 실행", exact: true }).click(),
  ]);
  let validationBody = null;
  try { validationBody = await validationResponse.json(); } catch { validationBody = null; }
  await page.waitForTimeout(500);
  await page.screenshot({ path: path.join(shotDir, "negative-invalid-cidr-rejected.png"), fullPage: true });

  const afterNegative = await api(`projects/${projectId}/editing`);
  const persisted131 = afterNegative.body.subnets.find(subnet => subnet.vlan_id === 131);
  result.invalid_cidr = {
    submitted: "10.10.131.999/24",
    response_status: validationResponse.status(),
    response_body: validationBody,
    persisted_cidr: persisted131?.cidr,
    validator_rejected: validationResponse.status() >= 400,
    persistence_preserved: persisted131?.cidr === vlan131.cidr,
  };
  result.invalid_cidr.pass = result.invalid_cidr.validator_rejected && result.invalid_cidr.persistence_preserved;
  if (!result.invalid_cidr.persistence_preserved) {
    throw new Error(`invalid CIDR changed persisted state: ${JSON.stringify(result.invalid_cidr)}`);
  }
  if (!result.invalid_cidr.validator_rejected) {
    result.known_bugs.push({
      id: "BUG-CIDR-VALIDATION-001",
      severity: "HIGH",
      title: "초안 검증이 유효하지 않은 IPv4 CIDR을 수락함",
      submitted: result.invalid_cidr.submitted,
      response_status: result.invalid_cidr.response_status,
      evidence: "negative-invalid-cidr-rejected.png",
    });
  }

  await page.reload({ waitUntil: "networkidle" });
  const reloadedNameInput = page.getByLabel(`${vlan131.id} 이름`, { exact: true });
  const reloadedRow = reloadedNameInput.locator("xpath=ancestor::tr");
  const restoredCidr = await reloadedRow.locator("input").nth(1).inputValue();
  result.reload_persistence = { expected: vlan131.cidr, actual: restoredCidr, pass: restoredCidr === vlan131.cidr };
  if (!result.reload_persistence.pass) throw new Error("reload persistence failed");

  await page.getByRole("button", { name: "검증 실행", exact: true }).click();
  await page.getByText(/망분리 검증 (통과|실패)/).waitFor({ timeout: 20_000 });
  const push = page.getByRole("button", { name: "정책 푸시", exact: true });
  result.policy_guard = {
    disabled: await push.isDisabled(),
    title: await push.getAttribute("title"),
  };
  result.policy_guard.pass = result.policy_guard.disabled && result.policy_guard.title?.includes("위반");
  await page.screenshot({ path: path.join(shotDir, "policy-guard-after-reload.png"), fullPage: true });
  if (!result.policy_guard.pass) throw new Error(`policy guard failed: ${JSON.stringify(result.policy_guard)}`);

  const anonymous = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const anonymousPage = await anonymous.newPage();
  await anonymousPage.goto(`${base}/agent`, { waitUntil: "networkidle" });
  result.auth_guard = {
    final_path: new URL(anonymousPage.url()).pathname,
    pass: new URL(anonymousPage.url()).pathname === "/signin",
  };
  await anonymousPage.screenshot({ path: path.join(shotDir, "auth-guard-signin.png"), fullPage: true });
  await anonymous.close();
  if (!result.auth_guard.pass) throw new Error(`auth guard failed: ${JSON.stringify(result.auth_guard)}`);

  result.page_errors = pageErrors;
  result.console_errors = consoleErrors;
  result.unexpected_console_errors = consoleErrors.filter(error =>
    !(new URL(error.url).pathname === "/signin" && error.text.includes("401")),
  );
  result.http_errors = httpErrors;
  result.unexpected_http_errors = httpErrors.filter(error =>
    error.status >= 500 || (error.status >= 400 && !error.url.includes("/auth/me") &&
      !error.url.includes("/projects/draft/validation")),
  );
  result.execution_completed = true;
  result.pass = pageErrors.length === 0 && result.unexpected_console_errors.length === 0 &&
    result.unexpected_http_errors.length === 0 && result.known_bugs.length === 0;
  result.completed_at = new Date().toISOString();
  if (pageErrors.length > 0 || result.unexpected_console_errors.length > 0 ||
      result.unexpected_http_errors.length > 0) {
    throw new Error("browser regression recorded unexpected runtime errors");
  }
} catch (error) {
  result.pass = false;
  result.failure = { message: error.message, stack: error.stack };
  await page.screenshot({ path: path.join(shotDir, "failure.png"), fullPage: true }).catch(() => {});
  throw error;
} finally {
  result.page_errors ??= pageErrors;
  result.console_errors ??= consoleErrors;
  result.http_errors ??= httpErrors;
  fs.writeFileSync(path.join(evidenceDir, "result.json"), JSON.stringify(result, null, 2));
  await browser.close();
}

console.log(JSON.stringify({
  pass: result.pass,
  routes: result.routes.length,
  invalid_cidr: result.invalid_cidr,
  policy_guard: result.policy_guard,
  auth_guard: result.auth_guard,
  known_bugs: result.known_bugs,
  page_errors: result.page_errors,
  console_errors: result.console_errors,
  unexpected_console_errors: result.unexpected_console_errors,
  unexpected_http_errors: result.unexpected_http_errors,
}, null, 2));
