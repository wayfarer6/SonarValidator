/** Frontend 실행 상태와 CIDR 검증 결함을 화면 증적으로 남긴다. */
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require(
  "/home/osboxes/.local/share/sonar-e2e-2/browser/node_modules/playwright",
);

const root = path.resolve(".");
const base = process.env.SCREENSHOT_BASE ?? "http://172.16.255.245:5173";
const outputDir = path.join(root, "Report-20261010", "screenshots", "round3", "errors");
const evidenceDir = path.join(root, "Report-20261010", "evidence", "round3");
fs.mkdirSync(outputDir, { recursive: true });
fs.mkdirSync(evidenceDir, { recursive: true });

function readEnv(file) {
  const values = {};
  for (const raw of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#") || !line.includes("=")) continue;
    const index = line.indexOf("=");
    let value = line.slice(index + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    values[line.slice(0, index).trim()] = value;
  }
  return values;
}

const env = readEnv(path.join(root, ".env"));
const result = { base, started_at: new Date().toISOString(), screenshots: [], errors: [] };
const browser = await chromium.launch({ headless: true, args: ["--disable-dev-shm-usage"] });
const context = await browser.newContext({
  viewport: { width: 1600, height: 1000 },
  locale: "ko-KR",
  timezoneId: "Asia/Seoul",
});
const page = await context.newPage();
page.on("pageerror", error => result.errors.push({ type: "pageerror", message: error.message }));

async function screenshot(name, locator = page) {
  const file = `${name}.png`;
  await locator.screenshot({
    path: path.join(outputDir, file),
    ...(locator === page ? { fullPage: true } : {}),
  });
  result.screenshots.push(file);
}

try {
  await page.goto(`${base}/signin`, { waitUntil: "networkidle" });
  await page.getByPlaceholder("admin", { exact: true }).fill("admin");
  await page.getByPlaceholder("비밀번호를 입력하세요")
    .fill(env.SONAR_ADMIN_PASSWORD ?? "admin");
  await Promise.all([
    page.waitForURL(url => !url.pathname.includes("signin"), { timeout: 20_000 }),
    page.getByRole("button", { name: "Sign in" }).click(),
  ]);

  await page.goto(`${base}/`, { waitUntil: "networkidle" });
  await screenshot("01-frontend-running-dashboard");

  const projectId = "PRJ-AADE1BC3";
  await page.goto(`${base}/project/editor/${projectId}`, { waitUntil: "networkidle" });
  const targetName = page.getByLabel(/VLAN131-.* 이름/, { exact: true }).first();
  await targetName.waitFor({ timeout: 15_000 });
  const targetRow = targetName.locator("xpath=ancestor::tr");
  // 이름 input 다음이 CIDR input이다. 공통 Input 컴포넌트의 type 직렬화 방식에
  // 의존하지 않도록 행 내부 순서로 찾는다.
  const cidrInput = targetRow.locator("input").nth(1);
  await cidrInput.fill("10.10.131.999/24");
  // 표의 기본 열 폭은 좁아 전체 값이 잘리므로 증적에서는 입력값 전체가 보이게 한다.
  await cidrInput.evaluate(element => {
    element.style.width = "190px";
    element.style.minWidth = "190px";
  });
  await screenshot("02-invalid-cidr-input", targetRow);

  const [response] = await Promise.all([
    page.waitForResponse(candidate =>
      candidate.url().includes("/api/v1/projects/draft/validation") &&
      candidate.request().method() === "POST"),
    page.getByRole("button", { name: "검증 실행", exact: true }).click(),
  ]);
  const responseBody = await response.json();
  await page.getByRole("heading", { name: /망분리 검증 (통과|실패)/ })
    .waitFor({ timeout: 15_000 });
  const summary = page.getByRole("heading", { name: /망분리 검증 (통과|실패)/ })
    .locator('xpath=ancestor::div[contains(@class,"rounded-xl")][1]');
  await screenshot("03-invalid-cidr-validation-response", summary);

  const detailHeading = page.getByRole("heading", { name: /위반 상세/ });
  await detailHeading.scrollIntoViewIfNeeded();
  const details = detailHeading.locator('xpath=ancestor::div[contains(@class,"rounded-2xl")][1]');
  await screenshot("04-invalid-cidr-response-detail", details);

  result.response_status = response.status();
  result.response_contains_invalid_input = JSON.stringify(responseBody).includes("10.10.131.999/24");
  result.input_error_visible = await page.getByText(/유효하지 않은 CIDR|CIDR 형식|옥텟 범위/).count() > 0;
  result.completed = true;
} catch (error) {
  result.completed = false;
  result.failure = error.message;
  await screenshot("99-capture-failure").catch(() => {});
} finally {
  result.completed_at = new Date().toISOString();
  fs.writeFileSync(
    path.join(evidenceDir, "error-screenshots.json"),
    JSON.stringify(result, null, 2),
  );
  await browser.close();
}

console.log(JSON.stringify(result, null, 2));
