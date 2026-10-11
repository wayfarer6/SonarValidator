import fs from 'node:fs';
import path from 'node:path';
import {page,base,api,save,shot,close,dir,errors} from './browser.mjs';

// 실 프로젝트(장비 배정됨)와 격리 fixture(저장 테스트용)
const REAL='PRJ-D488B7A4';
const FIXTURE='PRJ-4B6C7538';
const results={steps:[],assertions:{}};
const step=async(name,fn)=>{
  const entry={name,ok:true};
  try{entry.data=await fn();}catch(e){entry.ok=false;entry.error=e.message;}
  results.steps.push(entry);save('ui-e2e-progress',results);
  console.log((entry.ok?'PASS ':'FAIL ')+name+(entry.error?' :: '+entry.error:''));
  return entry;
};

// 01 대시보드
await step('dashboard',async()=>{await page.goto(base+'/',{waitUntil:'networkidle'});await page.waitForTimeout(1500);await shot('ui-01-dashboard');});

// 02 프로젝트 목록 + API
await step('project-list',async()=>{
  await page.goto(base+'/project',{waitUntil:'networkidle'});
  await page.getByRole('button',{name:'Add Agent',exact:true}).last().waitFor({timeout:15000});
  await shot('ui-02-project-list');
  const p=await api('projects');
  const ids=(p.body.projects||[]).map(x=>x.project_id);
  results.assertions.projects=ids;
  if(!ids.includes(REAL))throw Error('real project missing');
  if(!ids.includes(FIXTURE))throw Error('fixture project missing');
  return ids;
});

// 03 Agent 화면 (연결 노드 목록)
await step('agent-screen',async()=>{
  const ov=await api('agents/overview?project_id='+REAL);
  results.assertions.connectedAgents=ov.body.connected;
  results.assertions.totalAgents=(ov.body.agents||[]).length;
  if(ov.body.connected<15)throw Error('expected many connected agents, got '+ov.body.connected);
  await page.goto(base+'/agent',{waitUntil:'networkidle'});
  await page.waitForTimeout(2000);
  await shot('ui-03-agents');
  return {connected:ov.body.connected};
});

// 04 Manage (프로젝트 에디터)
await step('project-editor',async()=>{
  await page.goto(base+'/project/editor/'+REAL,{waitUntil:'networkidle'});
  await page.waitForTimeout(3000);
  await shot('ui-04-project-editor');
});

// 05 Subnet Advance Configuration (실 프로젝트 수집 VLAN 표시)
await step('subnet-advance-real',async()=>{
  await page.goto(base+'/project/create/subnet?project_id='+REAL,{waitUntil:'networkidle'});
  const sel=page.locator('select[aria-label="VLAN/서브넷 선택"]');
  await sel.waitFor({timeout:20000});
  await page.waitForTimeout(1500);
  const opts=await sel.locator('option').allTextContents();
  results.assertions.realSubnetOptions=opts.length;
  const vlanOpts=opts.filter(o=>/VLAN\s*\d/.test(o));
  results.assertions.realVlanOptions=vlanOpts.length;
  await shot('ui-05-subnet-advance');
  const vlanTable=page.locator('[aria-label="수집된 VLAN"]');
  results.assertions.discoveredVlanTable=await vlanTable.count()===1;
  if(opts.length<10)throw Error('too few subnet rows: '+opts.length);
  if(vlanOpts.length<1)throw Error('no VLAN rows rendered');
  return {subnets:opts.length,vlanRows:vlanOpts.length,first:vlanOpts.slice(0,3)};
});

// 06 VLAN/CSO 편집 저장 → 새로고침 → 유지 검증 (격리 fixture)
await step('subnet-edit-persist',async()=>{
  const before=await api('projects/'+FIXTURE+'/editing');
  const target=before.body.subnets.find(s=>s.id==='Subnet-0001')||before.body.subnets[0];
  const orig={name:target.name,cls:target.subnet_class,cidr:target.cidr};
  await page.goto(base+'/project/create/subnet?project_id='+FIXTURE,{waitUntil:'networkidle'});
  const sel=page.locator('select[aria-label="VLAN/서브넷 선택"]');
  await sel.waitFor({timeout:20000});
  await sel.selectOption(target.id);
  const nameInput=page.locator('input[aria-label="VLAN/서브넷 이름"]');
  const cso=page.locator('select[aria-label="CSO 등급"]');
  await nameInput.fill(before.body.draft?'Astra VLAN131 업무망':'Astra E2E 업무망');
  await cso.selectOption('Sensitive');
  await page.getByRole('button',{name:'VLAN/서브넷 저장'}).click();
  await page.waitForTimeout(2500);
  await shot('ui-06-subnet-edit-saved');
  await page.reload({waitUntil:'networkidle'});
  await sel.waitFor({timeout:20000});
  await sel.selectOption(target.id);
  await page.waitForTimeout(800);
  const persistedName=await nameInput.inputValue();
  const persistedClass=await cso.inputValue();
  const persisted=persistedClass==='Sensitive'&&persistedName.length>0;
  results.assertions.editPersist={subnet:target.id,savedName:persistedName,savedClass:persistedClass,persisted};
  // 원상 복구
  await nameInput.fill(orig.name||'');
  await cso.selectOption(orig.cls||'');
  await page.getByRole('button',{name:'VLAN/서브넷 저장'}).click();
  await page.waitForTimeout(2000);
  await page.reload({waitUntil:'networkidle'});
  await sel.waitFor({timeout:20000});
  await sel.selectOption(target.id);
  await page.waitForTimeout(800);
  results.assertions.editRestored=(await cso.inputValue())===(orig.cls||'');
  await shot('ui-07-subnet-restored');
  if(!persisted)throw Error('edit did not persist after reload');
  return results.assertions.editPersist;
});

// 07 로그 화면 + API
await step('log-page',async()=>{
  const logs=await api('logs');
  results.assertions.logsStatus=logs.status;
  await page.goto(base+'/log',{waitUntil:'networkidle'});
  await page.waitForTimeout(2500);
  await shot('ui-08-logs');
  if(logs.status!==200)throw Error('logs API '+logs.status);
  return {status:logs.status};
});

// 08 컴플라이언스 / 정책 화면
await step('compliance-policy',async()=>{
  await page.goto(base+'/compliance',{waitUntil:'networkidle'});await page.waitForTimeout(2000);await shot('ui-09-compliance');
  await page.goto(base+'/policy',{waitUntil:'networkidle'});await page.waitForTimeout(2000);await shot('ui-10-policy');
});

results.errors=errors;
results.assertions.pageErrorCount=errors.filter(e=>e.type==='pageerror').length;
results.assertions.httpErrorCount=errors.filter(e=>e.type==='http').length;
save('ui-e2e-results',results);
console.log('=== SUMMARY ===');
console.log(JSON.stringify(results.assertions,null,2));
await close('ui-e2e');
