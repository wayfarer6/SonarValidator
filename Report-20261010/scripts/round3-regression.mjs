/**
 * 3차 보완 검증: 인증 가드, 관리 콘솔 CORS, CIDR 부정 입력, 최종 안전 상태.
 *
 * CIDR 입력은 초안 검증 API에만 전달하며 프로젝트를 저장하지 않는다.
 * 제품 결함이 재현되어도 실행 자체는 완료되므로 결과는 JSON의 pass/known_issues로 판정한다.
 */
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require(
  "/home/osboxes/.local/share/sonar-e2e-2/browser/node_modules/playwright",
);

const root = path.resolve(".");
const base = process.env.R3_BASE ?? "http://172.16.255.245:5173";
const apiBase = process.env.R3_API_BASE ?? "http://172.16.255.245:3000";
const reportDir = path.join(root, "Report-20261010");
const evidenceDir = path.join(reportDir, "evidence", "round3");
const screenshotDir = path.join(reportDir, "screenshots", "round3");
fs.mkdirSync(evidenceDir, { recursive: true });
fs.mkdirSync(screenshotDir, { recursive: true });

function readEnv(file) {
  const values = {};
  for (const raw of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#") || !line.includes("=")) continue;
    const index = line.indexOf("=");
    const key = line.slice(0, index).trim();
    let value = line.slice(index + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    values[key] = value;
  }
  return values;
}

const env = readEnv(path.join(root, ".env"));
const result = {
  run_name: "round3-regression",
  started_at: new Date().toISOString(),
  frontend: base,
  backend: apiBase,
  checks: {},
  invalid_cidr_matrix: [],
  known_issues: [],
  runtime_errors: [],
};

const browser = await chromium.launch({
  headless: true,
  args: ["--disable-dev-shm-usage"],
});
const context = await browser.newContext({
  viewport: { width: 1600, height: 1000 },
  locale: "ko-KR",
  timezoneId: "Asia/Seoul",
});
const page = await context.newPage();
page.on("pageerror", error => result.runtime_errors.push({
  type: "pageerror",
  message: error.message,
  url: page.url(),
}));
page.on("response", response => {
  if (response.status() >= 500) {
    result.runtime_errors.push({
      type: "http",
      status: response.status(),
      url: response.url(),
    });
  }
});

async function api(endpoint, options = {}) {
  return page.evaluate(async ({ apiBaseValue, endpointValue, optionsValue }) => {
    const response = await fetch(`${apiBaseValue}/api/v1/${endpointValue}`, {
      credentials: "include",
      headers: { "Content-Type": "application/json", ...(optionsValue.headers ?? {}) },
      ...optionsValue,
    });
    const text = await response.text();
    let body = null;
    try { body = text ? JSON.parse(text) : null; } catch { body = text.slice(0, 500); }
    return { status: response.status, body };
  }, { apiBaseValue: apiBase, endpointValue: endpoint, optionsValue: options });
}

try {
  const health = await fetch(`${apiBase}/actuator/health`);
  const healthBody = await health.json();
  result.checks.health = {
    status: health.status,
    body_status: healthBody.status,
    pass: health.status === 200 && healthBody.status === "UP",
  };

  const cors = await fetch(`${apiBase}/api/v1/auth/me`, {
    method: "OPTIONS",
    headers: {
      Origin: base,
      "Access-Control-Request-Method": "GET",
    },
  });
  result.checks.cors = {
    status: cors.status,
    allow_origin: cors.headers.get("access-control-allow-origin"),
    pass: cors.ok && cors.headers.get("access-control-allow-origin") === base,
  };

  await page.goto(`${base}/agent`, { waitUntil: "networkidle" });
  result.checks.auth_guard = {
    final_path: new URL(page.url()).pathname,
    pass: new URL(page.url()).pathname === "/signin",
  };

  await page.getByPlaceholder("admin", { exact: true }).fill("admin");
  await page.getByPlaceholder("비밀번호를 입력하세요")
    .fill(env.SONAR_ADMIN_PASSWORD ?? "admin");
  await Promise.all([
    page.waitForURL(url => !url.pathname.includes("signin"), { timeout: 20_000 }),
    page.getByRole("button", { name: "Sign in" }).click(),
  ]);
  const me = await api("auth/me");
  result.checks.login = {
    status: me.status,
    authenticated: me.body?.authenticated ?? false,
    role: me.body?.role ?? null,
    pass: me.status === 200 && me.body?.authenticated === true,
  };

  const projects = await api("projects");
  const candidates = projects.body?.projects ?? [];
  let selected = null;
  for (const candidate of candidates) {
    const id = candidate.project_id;
    if (!id) continue;
    const detail = await api(`projects/${id}/editing`);
    if (detail.status === 200 && detail.body?.subnets?.some(subnet => subnet.cidr)) {
      selected = detail.body;
      break;
    }
  }
  if (!selected) throw new Error("CIDR 초안 검증에 사용할 프로젝트를 찾지 못했습니다.");
  const projectId = selected.project_id;
  const target = selected.subnets.find(subnet => subnet.vlan_id === 131)
    ?? selected.subnets.find(subnet => subnet.cidr);
  if (!target) throw new Error("CIDR 대상 서브넷을 찾지 못했습니다.");
  result.project_id = projectId;
  result.target_subnet = {
    id: target.id,
    vlan_id: target.vlan_id ?? null,
    persisted_cidr_before: target.cidr,
  };

  const invalidCases = [
    ["octet-overflow", "10.10.131.999/24"],
    ["prefix-overflow", "10.10.131.0/33"],
    ["missing-octet", "10.10.131/24"],
    ["non-numeric", "not-an-ip/24"],
  ];
  for (const [name, value] of invalidCases) {
    const subnets = selected.subnets.map(subnet =>
      subnet.id === target.id ? { ...subnet, cidr: value } : subnet);
    const response = await api("projects/draft/validation", {
      method: "POST",
      body: JSON.stringify({ subnets, rules: selected.rules ?? [] }),
    });
    const serialized = JSON.stringify(response.body ?? {});
    const invalidFlagged = response.status >= 400 ||
      /invalid|유효하지|형식|범위|CIDR.*지정/i.test(serialized);
    result.invalid_cidr_matrix.push({
      name,
      submitted: value,
      response_status: response.status,
      invalid_flagged: invalidFlagged,
      echoed_in_response: serialized.includes(value),
      violation_count: response.body?.violation_count ?? null,
      pass: invalidFlagged,
    });
  }

  await page.goto(`${base}/project/editor/${projectId}`, { waitUntil: "networkidle" });
  const nameInput = page.getByLabel(`${target.id} 이름`, { exact: true });
  const row = nameInput.locator("xpath=ancestor::tr");
  await row.locator("input").nth(1).fill(invalidCases[0][1]);
  await page.screenshot({
    path: path.join(screenshotDir, "invalid-cidr-draft.png"),
    fullPage: true,
  });

  const after = await api(`projects/${projectId}/editing`);
  const persistedAfter = after.body?.subnets?.find(subnet => subnet.id === target.id)?.cidr ?? null;
  result.checks.persistence_preserved = {
    expected: target.cidr,
    actual: persistedAfter,
    pass: persistedAfter === target.cidr,
  };

  const quarantine = await api(`quarantine?project_id=${projectId}`);
  result.checks.final_safety = {
    quarantine_status: quarantine.status,
    active_quarantine_total: quarantine.body?.total ?? null,
    pass: quarantine.status === 200 && quarantine.body?.total === 0,
  };

  if (result.invalid_cidr_matrix.some(item => !item.pass)) {
    result.known_issues.push({
      id: "BUG-CIDR-VALIDATION-001",
      severity: "HIGH",
      title: "초안 검증이 유효하지 않은 IPv4 CIDR을 명시적으로 거부하지 않음",
      reproduced_cases: result.invalid_cidr_matrix.filter(item => !item.pass).length,
    });
  }

  result.execution_completed = true;
  result.pass = Object.values(result.checks).every(check => check.pass) &&
    result.invalid_cidr_matrix.every(item => item.pass) &&
    result.runtime_errors.length === 0;
} catch (error) {
  result.execution_completed = false;
  result.pass = false;
  result.failure = { message: error.message, stack: error.stack };
  await page.screenshot({
    path: path.join(screenshotDir, "failure.png"),
    fullPage: true,
  }).catch(() => {});
} finally {
  result.completed_at = new Date().toISOString();
  fs.writeFileSync(
    path.join(evidenceDir, "result.json"),
    JSON.stringify(result, null, 2),
  );
  await browser.close();
}

console.log(JSON.stringify(result, null, 2));
