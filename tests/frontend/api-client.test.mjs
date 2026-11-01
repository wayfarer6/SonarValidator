import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {createRequire} from "node:module";
const repo=new URL("../../",import.meta.url);
const require=createRequire(new URL("SonarValidator_Frontend/package.json",repo));
const ts=require("typescript");
const transpile = source => ts.transpileModule(source, {compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
const asModule = source => `data:text/javascript;base64,${Buffer.from(transpile(source)).toString("base64")}`;
const errorsUrl = asModule(fs.readFileSync(new URL("SonarValidator_Frontend/src/lib/errors.ts",repo),"utf8"));
const {errorLog,compactText,formatErrorDetails} = await import(errorsUrl);
const text=fs.readFileSync(new URL("SonarValidator_Frontend/src/lib/api/client.ts",repo),"utf8")
  .replace("import.meta.env.VITE_API_BASE_URL",'"http://test.invalid"').replace('"../errors"',JSON.stringify(errorsUrl));
const {apiRequest}=await import(asModule(text));
test.beforeEach(()=>errorLog.clear());
test("timeout covers a response body that never completes",async()=>{
  const original=globalThis.fetch;
  try {
    let signal;
    globalThis.fetch=async(_url,options)=>{signal=options.signal;return {ok:true,status:200,text:()=>new Promise((_resolve,reject)=>signal.addEventListener("abort",()=>reject(signal.reason),{once:true}))};};
    await assert.rejects(apiRequest("/body",{timeoutMs:25,retries:0}),error=>error.status===0&&error.message.includes("시간이 초과"));
    assert.equal(signal.aborted,true);
  } finally {globalThis.fetch=original;}
});
test("a failed POST is not automatically repeated",async()=>{
  const original=globalThis.fetch;let calls=0;
  try {globalThis.fetch=async()=>{calls++;throw new Error("offline");};await assert.rejects(apiRequest("/execute",{method:"POST",body:{},timeoutMs:100}));assert.equal(calls,1);}
  finally {globalThis.fetch=original;}
});
test("server conflict detail is shown to the operator",async()=>{
  const original=globalThis.fetch;
  try {globalThis.fetch=async()=>({ok:false,status:409,statusText:"Conflict",text:async()=>JSON.stringify({detail:"관측이 달라졌습니다"})});await assert.rejects(apiRequest("/execute",{method:"POST",body:{}}),error=>error.status===409&&error.message==="관측이 달라졌습니다");}
  finally {globalThis.fetch=original;}
});

test("long HTTP failures show a short summary and retain redacted details in the log",async()=>{
  const original=globalThis.fetch;
  try {
    const message="Database failure "+"long detail ".repeat(50)+"DETAIL_END";
    globalThis.fetch=async()=>({ok:false,status:500,statusText:"Internal Server Error",text:async()=>JSON.stringify({message,trace:"trace line",password:"hidden-password",access_token:"hidden-token"})});
    await assert.rejects(apiRequest("/save?token=query-secret",{method:"POST",body:{privateKey:"body-secret"}}),error=>{
      assert.equal(error.message,"서버에서 요청을 처리하지 못했습니다.");
      assert.match(error.details,/DETAIL_END/);return true;
    });
    const [entry]=errorLog.getSnapshot();assert.equal(entry.path,"/save");assert.equal(entry.status,500);assert.match(entry.details,/trace line/);
    for(const secret of ["hidden-password","hidden-token","query-secret","body-secret"])assert.equal(JSON.stringify(entry).includes(secret),false);
  } finally {globalThis.fetch=original;}
});
test("GET retries log one final failure and recovered retries leave no error",async()=>{
  const original=globalThis.fetch;let calls=0;
  try {
    globalThis.fetch=async()=>{calls++;return {ok:false,status:503,statusText:"Unavailable",text:async()=>"<html>technical details</html>"};};
    await assert.rejects(apiRequest("/retry",{retries:1}));assert.equal(calls,2);assert.equal(errorLog.getSnapshot().length,1);assert.equal(errorLog.getSnapshot()[0].count,1);
    errorLog.clear();calls=0;
    globalThis.fetch=async()=>{calls++;return {ok:calls>1,status:calls>1?200:503,statusText:"Unavailable",text:async()=>JSON.stringify({ready:true})};};
    assert.deepEqual(await apiRequest("/recovered",{retries:1}),{ready:true});assert.equal(errorLog.getSnapshot().length,0);
  } finally {globalThis.fetch=original;}
});
test("the error log is bounded, deduplicated and supports subscription cleanup",()=>{
  let events=0;const unsubscribe=errorLog.subscribe(()=>events++);
  const input={summary:"Failure",details:"same details",method:"GET",path:"/same",status:500};
  errorLog.record(input);errorLog.record(input);assert.equal(errorLog.getSnapshot().length,1);assert.equal(errorLog.getSnapshot()[0].count,2);
  unsubscribe();errorLog.record({...input,path:"/after-unsubscribe"});assert.equal(events,2);
  for(let i=0;i<110;i++)errorLog.record({...input,path:`/item/${i}`,details:"x".repeat(40_000)});
  assert.equal(errorLog.getSnapshot().length,100);assert.ok(errorLog.getSnapshot()[0].details.length<33_000);
  assert.equal(errorLog.getSnapshot(),errorLog.getSnapshot());
});
test("technical details redact text credentials and remain stable when formatted again",()=>{
  const raw='Authorization: Bearer hidden-bearer\nCookie: one=hidden-cookie; two=another-secret\npassword="hidden pass word"\napi_key=hidden-key&other=ok\nhttps://name:hidden-url@example.test/\n-----BEGIN OPENSSH PRIVATE KEY-----\nhidden-private-key\n-----END OPENSSH PRIVATE KEY-----';
  const cleaned=formatErrorDetails(raw);
  for(const secret of ["hidden-bearer","hidden-cookie","another-secret","hidden pass word","hidden-key","hidden-url","hidden-private-key"])assert.equal(cleaned.includes(secret),false,secret);
  assert.equal(formatErrorDetails(cleaned),cleaned);
  assert.equal(compactText(null,"없음"),"없음");assert.ok(compactText("line\n"+"x".repeat(200)).length<=100);
  const circular={detail:"safe",password:"hidden"};circular.self=circular;assert.match(formatErrorDetails(circular),/Circular/);
});
test("normal authentication probes are omitted and successful login clears session details",async()=>{
  const original=globalThis.fetch;
  try {
    globalThis.fetch=async()=>({ok:false,status:401,statusText:"Unauthorized",text:async()=>""});
    await assert.rejects(apiRequest("/api/v1/auth/me",{retries:0}));assert.equal(errorLog.getSnapshot().length,0);
    errorLog.record({summary:"old user",details:"old user data",method:"GET",path:"/old"});
    globalThis.fetch=async()=>({ok:true,status:200,text:async()=>JSON.stringify({authenticated:true})});
    await apiRequest("/api/v1/auth/login",{method:"POST",body:{username:"new"}});assert.equal(errorLog.getSnapshot().length,0);
  } finally {globalThis.fetch=original;}
});

test("very long unstructured responses are processed without regex backtracking stalls",()=>{
  const started=performance.now();const result=formatErrorDetails("x".repeat(200_000));
  assert.ok(result.length<33_000);assert.ok(performance.now()-started<1000);
});

test("redacted nested trace text preserves JSON formatting and the remaining trace",()=>{
  const result=formatErrorDetails({trace:'first line\nCookie: a=hidden-cookie; b=another-cookie\nTRACE_END',url:'https://example.test/?token=hidden-query&ok=1'});
  const parsed=JSON.parse(result);assert.match(parsed.trace,/TRACE_END/);assert.equal(result.includes('hidden-cookie'),false);assert.equal(result.includes('hidden-query'),false);
  assert.equal(formatErrorDetails(result),result);
});
