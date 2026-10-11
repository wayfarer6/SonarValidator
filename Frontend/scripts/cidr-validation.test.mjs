import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { Script } from "node:vm";
import ts from "typescript";

// 화면 CIDR 검증이 서버(PolicySubnet.isValidCidr)와 같은 값을 거부/수락하는지
// 고정한다. 두 규칙이 어긋나면 "화면은 통과인데 서버가 400" 이 된다.
const source = readFileSync(
  new URL("../src/lib/policy/cidr.ts", import.meta.url),
  "utf8",
);
const exports = {};
new Script(
  ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText,
).runInNewContext({ exports });
const { validateIpv4Cidr, collectCidrErrors } = exports;

test("잘못된 CIDR 4종과 다중 슬래시·템플릿 표기를 거부한다", () => {
  assert.ok(validateIpv4Cidr("10.10.131.999/24"));
  assert.ok(validateIpv4Cidr("10.10.131.0/33"));
  assert.ok(validateIpv4Cidr("10.10.131/24"));
  assert.ok(validateIpv4Cidr("not-an-ip/24"));
  assert.ok(validateIpv4Cidr("10.0.0.1/24/extra"));
  assert.ok(validateIpv4Cidr("192.168.0.x/24"));
});

test("정상 경계값과 빈 값은 통과한다", () => {
  assert.equal(validateIpv4Cidr("0.0.0.0/0"), null);
  assert.equal(validateIpv4Cidr("10.10.131.7/32"), null);
  assert.equal(validateIpv4Cidr("10.10.131.0/24"), null);
  // 대역 미수집 행(빈 값)은 오류가 아니다.
  assert.equal(validateIpv4Cidr(""), null);
  assert.equal(validateIpv4Cidr(null), null);
});

test("collectCidrErrors 는 오류가 있는 행만 모은다", () => {
  const errors = collectCidrErrors([
    { id: "S1", cidr: "10.10.131.0/24" },
    { id: "S2", cidr: "10.10.131.0/33" },
    { id: "S3", cidr: "" },
  ]);
  assert.deepEqual(Object.keys(errors), ["S2"]);
});
