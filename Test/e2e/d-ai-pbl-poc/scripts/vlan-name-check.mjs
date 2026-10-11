// 수집된 VLAN 이름 편집 UI 검증 스크립트 (dev 스택: frontend 5173 / backend 3000)
//
// 왜 별도 스크립트인가
//   Vite dev 서버에는 /api 프록시가 없어 상대경로 fetch 가 HTML 을 받습니다.
//   그래서 API 호출은 절대 URL(http://localhost:3000)로 보냅니다.
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const {chromium}=require('/home/osboxes/.local/share/sonar-e2e-2/browser/node_modules/playwright');

const dir=path.resolve('Test/e2e/d-ai-pbl-poc');
const FE='http://localhost:5173';
const BE='http://localhost:3000';
const PROJECT=process.argv[2]||'PRJ-AADE1BC3';
const AGENT=process.argv[3]||'Switch-1';
const VLAN=Number(process.argv[4]||111);
const NAME=process.argv[5]||'감시장비망';

const browser=await chromium.launch({headless:true,args:['--disable-dev-shm-usage']});
const context=await browser.newContext({viewport:{width:1600,height:1000},locale:'ko-KR',timezoneId:'Asia/Seoul'});
const page=await context.newPage();
const errors=[];
page.on('pageerror',e=>errors.push({type:'pageerror',message:e.message}));

try{
  await page.goto(FE,{waitUntil:'networkidle'});
  const user=page.getByPlaceholder('admin',{exact:true});
  for(let i=0;i<20;i++){ if(await user.count()>0) break; await page.waitForTimeout(300); }
  if(await user.count()>0){
    await user.fill('admin');
    await page.getByPlaceholder('비밀번호를 입력하세요').fill('admin');
    await page.getByRole('button',{name:'Sign in'}).click();
    await page.waitForURL(u=>!u.pathname.includes('signin'),{timeout:20000});
  }

  await page.goto(`${FE}/project/create/subnet?project_id=${PROJECT}`,{waitUntil:'networkidle'});
  const table=page.locator('[aria-label="수집된 VLAN"]');
  await table.waitFor({timeout:20000});
  await page.waitForTimeout(2500);

  const input=page.locator(`input[aria-label="${AGENT} VLAN${VLAN} 이름"]`);
  await input.waitFor({timeout:15000});
  const before=await input.inputValue();
  await input.fill(NAME);
  await page.locator(`tr:has(input[aria-label="${AGENT} VLAN${VLAN} 이름"]) button`).click();
  await page.waitForTimeout(2500);
  const status=await table.locator('[role="status"]').innerText().catch(()=>null);
  await table.screenshot({path:path.join(dir,'screenshots','vlan-name-edit.png')});

  await page.reload({waitUntil:'networkidle'});
  await table.waitFor({timeout:20000});
  await page.waitForTimeout(3000);
  const afterReload=await page.locator(`input[aria-label="${AGENT} VLAN${VLAN} 이름"]`).inputValue();
  await table.screenshot({path:path.join(dir,'screenshots','vlan-name-persisted.png')});
  await page.screenshot({path:path.join(dir,'screenshots','vlan-subnet-page.png'),fullPage:true});

  // API 로도 저장 확인
  const api=await page.evaluate(async({be,project})=>{
    const r=await fetch(`${be}/api/v1/projects/${project}/vlans`,{credentials:'include'});
    return await r.json();
  },{be:BE,project:PROJECT});

  const result={agent:AGENT,vlan:VLAN,before,status,afterReload,persisted:afterReload===NAME,apiVlans:api.vlans,errors};
  fs.writeFileSync(path.join(dir,'evidence','vlan-name-ui.json'),JSON.stringify(result,null,2));
  console.log(JSON.stringify(result,null,2));
}finally{
  await browser.close();
}
