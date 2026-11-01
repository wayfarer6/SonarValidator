import test from "node:test";
import assert from "node:assert/strict";
import {bundleFileName, bundleDownloadError} from "../src/lib/api/download.ts";
const fallback="sonar-agent-Lab1-R1.tar.gz";
test("Spring UTF-8 filename wins over MIME encoded fallback",()=>{
  assert.equal(bundleFileName('attachment; filename="=?UTF-8?Q?sonar-agent-Lab1-R1.tar.gz?="; filename*=UTF-8\'\'sonar-agent-Lab1-R1.tar.gz',fallback),fallback);
});
test("UTF-8 filename and quoted ASCII are decoded",()=>{
  assert.equal(bundleFileName("attachment; filename*=UTF-8''lab%20test.tar.gz",fallback),"lab test.tar.gz");
  assert.equal(bundleFileName('attachment; filename="another.tar.gz"',fallback),"another.tar.gz");
});
test("invalid percent encoding and path names use safe fallback",()=>{
  assert.equal(bundleFileName("attachment; filename*=UTF-8''bad%ZZ",fallback),fallback);
  assert.equal(bundleFileName('attachment; filename="../secret"',fallback),fallback);
  assert.equal(bundleFileName('',fallback),fallback);
});

test("download errors show the server's action message",()=>{
  assert.equal(bundleDownloadError(409,JSON.stringify({message:"먼저 프로젝트에 Agent를 등록하세요.",trace:"internal stack"})),"먼저 프로젝트에 Agent를 등록하세요.");
});
test("legacy stack traces and HTML errors do not appear in the UI",()=>{
  assert.equal(bundleDownloadError(500,JSON.stringify({trace:"internal stack"})),"Agent 파일 요청을 완료하지 못했습니다 (HTTP 500).");
  assert.equal(bundleDownloadError(502,"<html>proxy stack</html>"),"Agent 파일 요청을 완료하지 못했습니다 (HTTP 502).");
});
