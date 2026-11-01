import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import { createRequire } from "node:module";
const repo = new URL("../../", import.meta.url);
const require = createRequire(new URL("SonarValidator_Frontend/package.json", repo));
const { JSDOM } = require("jsdom");
const dom = new JSDOM("<html><body></body></html>", { url: "http://localhost" });
for (const name of ["window", "document", "HTMLElement", "HTMLInputElement", "Event", "MouseEvent"])
  globalThis[name] = dom.window[name];
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
const React = require("react");
const { createRoot } = require("react-dom/client");
const ts = require("typescript");
globalThis.__settingsReact = React;
globalThis.__settingsJsx = require("react/jsx-runtime");
const component = async (name, stubs, exportName = "default") => {
  let source = fs.readFileSync(new URL(`SonarValidator_Frontend/src/components/project/${name}.tsx`, repo), "utf8");
  source = source.replace(/^import[\s\S]*?from ["'][^"']+["'];?\s*/gm, "");
  source = `const {useState,useEffect,useId,useRef}=globalThis.__settingsReact;\n${stubs}\n${source}`;
  let js = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX,
  }}).outputText;
  js = js.replace(/^import.*from ["']react\/jsx-runtime["'];?$/gm,
    'const {jsx:_jsx,jsxs:_jsxs,Fragment:_Fragment}=globalThis.__settingsJsx;');
  return (await import(`data:text/javascript;base64,${Buffer.from(js + `\n//# sourceURL=${name}.tsx`).toString("base64")}`))[exportName];
};
const Monitor = await component("NodeMonitorSettings", `
const getMonitorSettings=(...a)=>globalThis.__settingsApi.getMonitorSettings(...a);
const updateMonitorSettings=(...a)=>globalThis.__settingsApi.updateMonitorSettings(...a);`);
globalThis.__settingsMonitor = Monitor;
const Settings = await component("NodeSettingsModal", `
const getProjectForEditing=(...a)=>globalThis.__settingsApi.getProjectForEditing(...a);
const updateProject=(...a)=>globalThis.__settingsApi.updateProject(...a);
const deleteExpectedAgent=()=>Promise.resolve();
const NETWORK_ROLES=[];
const NodeMonitorSettings=globalThis.__settingsMonitor;
const PolicyAdvicePanel=()=>null;
const PeerSelector=()=>null;
const Modal=({children})=>children;`);
const value = (seconds) => ({ monitor_interval_seconds: seconds, revision: 1,
  reported_interval_seconds: seconds, status: "applied", error: null });
const project = (agent) => ({subnets:[{id:agent,cidr:"10.2.0.0/24",name:agent,agent_id:agent}],rules:[]});
const callbacks = {onDirtyChange:()=>{},onSaved:()=>{},onClose:()=>{},onRemoved:()=>{}};
async function render(Component, props) {
  const host=document.createElement("div");document.body.append(host);
  const root=createRoot(host);
  await React.act(async()=>root.render(React.createElement(Component,props)));
  return {host,root,close:async()=>{await React.act(async()=>root.unmount());host.remove();}};
}
async function type(input, text) {
  await React.act(async()=>{
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,"value").set.call(input,text);
    input.dispatchEvent(new Event("input",{bubbles:true}));
  });
}
async function click(button) { await React.act(async()=>button.dispatchEvent(new MouseEvent("click",{bubbles:true}))); }
function button(host,text) {return [...host.querySelectorAll("button")].find(b=>b.textContent.includes(text));}

test("changing node identity discards the previous node's collection draft", async()=>{
  globalThis.__settingsApi={getMonitorSettings:async id=>value(id==="A"?30:120)};
  const view=await render(Monitor,{agentId:"A",projectId:"P",...callbacks});
  try {
    await type(view.host.querySelector("input"),"7");
    assert.equal(view.host.querySelector("input").value,"7");
    await React.act(async()=>view.root.render(React.createElement(Monitor,{agentId:"B",projectId:"P",...callbacks})));
    assert.equal(view.host.querySelector("input").value,"2");
    assert.equal(view.host.querySelector("select").value,"minutes");
  } finally {await view.close();}
});

test("collection inputs stay locked until the pending save finishes", async()=>{
  let finish; let calls=0;
  globalThis.__settingsApi={getMonitorSettings:async()=>value(30),updateMonitorSettings:()=>{
    calls++;return new Promise(resolve=>{finish=resolve;});}};
  const view=await render(Monitor,{agentId:"A",projectId:"P",...callbacks});
  try {
    await type(view.host.querySelector("input"),"7");
    await click(button(view.host,"수집 주기 저장"));
    assert.equal(calls,1);
    assert.equal(view.host.querySelector("input").disabled,true);
    assert.equal(view.host.querySelector("select").disabled,true);
    await React.act(async()=>finish({...value(7),revision:2}));
    assert.equal(view.host.querySelector("input").value,"7");
    assert.equal(view.host.querySelector("input").disabled,false);
  } finally {await view.close();}
});

test("connection saves lock all fields and prevent closing before completion", async()=>{
  let finish,closed=0,saved=0;
  globalThis.__settingsApi={getMonitorSettings:async()=>value(30),
    getProjectForEditing:async()=>project("A"),updateProject:()=>new Promise(resolve=>{finish=resolve;})};
  const view=await render(Settings,{agentId:"A",projectId:"P",connected:true,...callbacks,
    onClose:()=>closed++,onSaved:()=>saved++});
  try {
    await click(button(view.host,"연결 관리"));
    await type(view.host.querySelector('[aria-label="서브넷 이름"]'),"edited");
    await click(button(view.host,"변경 저장"));
    const fieldset=view.host.querySelector('[data-testid="node-connection-modal"]');
    assert.equal(fieldset.disabled,true);
    await click(button(view.host,"닫기"));assert.equal(closed,0);
    await React.act(async()=>finish(project("A")));
    assert.equal(fieldset.disabled,false);assert.equal(saved,1);
  } finally {await view.close();}
});

test("nested detail modals keep the outer modal open and restore focus and scroll", async()=>{
  const Modal=await component("../ui/modal/index","","Modal");
  const originalRects=HTMLElement.prototype.getClientRects;
  HTMLElement.prototype.getClientRects=function(){return [{width:1,height:1}];};
  const originalOverflow=document.body.style.overflow;document.body.style.overflow="auto";
  function Harness(){
    const [outer,setOuter]=React.useState(true);const [inner,setInner]=React.useState(false);
    return React.createElement(Modal,{isOpen:outer,onClose:()=>setOuter(false)},
      React.createElement("button",{onClick:()=>setInner(true)},"상세 열기"),
      React.createElement(Modal,{isOpen:inner,onClose:()=>setInner(false)},React.createElement("button",{},"내부 상세 내용")));
  }
  const view=await render(Harness,{});
  try{
    const trigger=button(view.host,"상세 열기");trigger.focus();await click(trigger);
    assert.ok(button(view.host,"내부 상세 내용"));assert.equal(document.body.style.overflow,"hidden");
    await React.act(async()=>document.activeElement.dispatchEvent(new dom.window.KeyboardEvent("keydown",{key:"Escape",bubbles:true})));
    assert.equal(button(view.host,"내부 상세 내용"),undefined);assert.ok(button(view.host,"상세 열기"));
    assert.equal(document.activeElement,trigger);assert.equal(document.body.style.overflow,"hidden");
    await React.act(async()=>document.dispatchEvent(new dom.window.KeyboardEvent("keydown",{key:"Escape",bubbles:true})));
    assert.equal(button(view.host,"상세 열기"),undefined);assert.equal(document.body.style.overflow,"auto");
  }finally{await view.close();HTMLElement.prototype.getClientRects=originalRects;document.body.style.overflow=originalOverflow;}
});
