import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import { createRequire } from "node:module";
const repo=new URL("../../",import.meta.url);
const require=createRequire(new URL("SonarValidator_Frontend/package.json",repo));
const {JSDOM}=require("jsdom");const dom=new JSDOM("<html><body></body></html>");
globalThis.window=dom.window;globalThis.document=dom.window.document;globalThis.IS_REACT_ACT_ENVIRONMENT=true;
const React=require("react"),{createRoot}=require("react-dom/client"),ts=require("typescript");
globalThis.__topologyReact=React;
let nextTimer=0;const timers=new Map();
globalThis.__topologyTimers={set:(fn)=>{timers.set(++nextTimer,fn);return nextTimer;},clear:id=>timers.delete(id)};
let source=fs.readFileSync(new URL("SonarValidator_Frontend/src/hooks/useApi.ts",repo),"utf8")
 .replace(/^import.*;\s*$/gm,"");
source=`const {useState,useEffect,useRef,useCallback}=globalThis.__topologyReact;
class ApiError extends Error {isNetworkError=true;}
const setTimeout=globalThis.__topologyTimers.set,clearTimeout=globalThis.__topologyTimers.clear;
${source}`;
const js=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
const {useApi}=await import(`data:text/javascript;base64,${Buffer.from(js).toString("base64")}`);
let state;
function View({id,fetcher}){state=useApi(fetcher,[id],20);return React.createElement("div",{},JSON.stringify(state));}
async function render(props){const host=document.createElement("div");document.body.append(host);const root=createRoot(host);
 const update=async p=>React.act(async()=>root.render(React.createElement(View,p)));
 await update(props);return {update,close:async()=>{await React.act(async()=>root.unmount());host.remove();}};}
async function tick(){const pair=timers.entries().next().value;assert.ok(pair,"refresh scheduled");timers.delete(pair[0]);await React.act(async()=>pair[1]());}

test("topology refresh is serialized, keeps the chart visible and recovers after an error",async()=>{
 let finish,calls=0;const fetcher=()=>{calls++;return new Promise(resolve=>{finish=resolve;});};
 const view=await render({id:"A",fetcher});
 try{assert.equal(calls,1);assert.equal(timers.size,0,"no overlapping request while pending");
  await React.act(async()=>finish({edges:["before"]}));assert.equal(state.loading,false);assert.equal(timers.size,1);
  await tick();assert.equal(calls,2);assert.equal(state.loading,false);assert.deepEqual(state.data.edges,["before"]);
  await React.act(async()=>finish({edges:[]}));assert.deepEqual(state.data.edges,[]);
  await view.update({id:"A",fetcher:()=>Promise.reject(new Error("unavailable"))});await tick();
  assert.equal(state.error,"unavailable");assert.deepEqual(state.data.edges,[],"last accepted graph retained with error status");
  await view.update({id:"A",fetcher:async()=>({edges:["restored"]})});await tick();
  assert.equal(state.error,null);assert.deepEqual(state.data.edges,["restored"]);assert.ok(state.updatedAt);
 }finally{await view.close();assert.equal(timers.size,0,"poller cleaned on unmount");}
});
test("late results from a previous project and unmounted requests never replace current topology",async()=>{
 let oldFinish,newFinish;const view=await render({id:"A",fetcher:()=>new Promise(r=>{oldFinish=r;})});
 try{await view.update({id:"B",fetcher:()=>new Promise(r=>{newFinish=r;})});assert.equal(state.data,null);
  await React.act(async()=>newFinish({project:"B"}));await React.act(async()=>oldFinish({project:"A"}));
  assert.equal(state.data.project,"B");assert.equal(timers.size,1);
 }finally{await view.close();}
 assert.equal(timers.size,0);
});

let mermaidSource=fs.readFileSync(new URL("SonarValidator_Frontend/src/lib/topology/mermaid.ts",repo),"utf8").replace(/^import.*;\s*$/gm,"");
const mermaidJs=ts.transpileModule(mermaidSource,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
const diagrams=await import(`data:text/javascript;base64,${Buffer.from(mermaidJs).toString("base64")}`);
test("all diagram views distinguish confirmed blocking from a requested or failed operation",()=>{
 const data={nodes:[{id:"a",label:"A",cidr:"10.0.1.0/24",level:1,quarantined:false,quarantine_status:"PENDING",quarantine_requested:true},
  {id:"b",label:"B",cidr:"10.0.2.0/24",level:2}],edges:[{source:"a",target:"b",observed:true,blocked:false,forbidden:false}]};
 for(const build of [diagrams.topologyToMermaid,diagrams.topologyToDetailedMermaid]){
  assert.match(build(data),/격리 확인 대기/);assert.doesNotMatch(build(data),/격리 적용 확인/);
  data.nodes[0].quarantine_status="FAILED";assert.match(build(data),/적용 실패/);
  data.nodes[0].quarantine_status="APPLIED";data.nodes[0].quarantined=true;data.edges[0].blocked=true;
  assert.match(build(data),/차단 확인/);assert.match(build(data),/격리 적용 확인/);
  data.nodes[0].quarantine_status="PENDING";data.nodes[0].quarantined=false;data.edges[0].blocked=false;
 }
 data.nodes[0].quarantined=true;data.edges[0].blocked=true;
 assert.match(diagrams.topologyToZoneMermaid(data),/차단 확인/);
 data.edges.push({...data.edges[0],blocked:false});assert.match(diagrams.topologyToZoneMermaid(data),/일부 차단/);
});

test("topology labels cannot add Mermaid syntax and distinct node IDs stay distinct",()=>{
 const ids=["node-1","node_1","end","1","노드"];
 assert.equal(new Set(ids.map(diagrams.sanitizeMermaidId)).size,ids.length);
 for(const id of ids) assert.match(diagrams.sanitizeMermaidId(id),/^n_[0-9a-f_]+$/);
 const data={nodes:[{id:"node-1",label:'A"] --> injected["B',level:null,cidr:null}],edges:[]};
 const chart=diagrams.topologyToMermaid(data);
 assert.ok(chart.includes("#quot;"));assert.ok(chart.includes("&gt;"));
 assert.ok(!chart.includes('A"] --> injected'));
 assert.match(chart,/classDef unclassified/);
});

test("manual refresh keeps same-project data mounted until the replacement arrives",async()=>{
 let finish;const view=await render({id:"A",fetcher:async()=>({agents:["terminal-owner"]})});
 try{
  await view.update({id:"A",fetcher:()=>new Promise(resolve=>{finish=resolve;})});
  await React.act(async()=>state.reload());
  assert.deepEqual(state.data,{agents:["terminal-owner"]},"refresh must not unmount an active terminal");
  assert.equal(state.loading,false);
  await React.act(async()=>finish({agents:["terminal-owner","new-agent"]}));
  assert.equal(state.data.agents.length,2);
 }finally{await view.close();}
});
