import fs from 'node:fs';
import path from 'node:path';
import {page,base,api,save,shot,close,dir} from './browser.mjs';
const selected=process.argv.slice(2);
const results=[];
let output='';
page.on('websocket',ws=>ws.on('framereceived',event=>{
 try { const m=JSON.parse(event.payload.toString());if(m.type==='terminal-output'&&m.data_base64)output+=Buffer.from(m.data_base64,'base64').toString(); } catch {}
}));
try {
 await page.goto(base+'/project',{waitUntil:'networkidle'});
 const overview=await api('agents/overview?project_id=PRJ-D488B7A4');
 for(const a of overview.body.agents.filter(a=>selected.length?selected.includes(a.agent_id):a.connected)) {
  const name=a.agent_id;output='';const result={name,connected:a.connected};
  try {
   const row=page.locator('li').filter({has:page.getByText(name,{exact:true})});
   await row.getByRole('button',{name:'터미널 열기',exact:true}).click({timeout:10000});
   await row.getByText('연결됨',{exact:true}).waitFor({timeout:20000});
   const input=row.locator('.xterm-helper-textarea');await input.focus();
   // FRR routers open vtysh inside `less` (banner + pager). Dismiss the pager
   // until the frr# prompt is visible, disable paging, run a route query, then
   // leave vtysh for the real root shell.
   if(a.device_type==='ROUTER'){
    for(let i=0;i<8&&!output.includes('frr#');i++){await page.keyboard.press('Enter');await page.waitForTimeout(400);}
    if(!output.includes('frr#')){await page.keyboard.press('q');await page.waitForTimeout(400);for(let i=0;i<4&&!output.includes('frr#');i++){await page.keyboard.press('Enter');await page.waitForTimeout(400);}}
    await page.keyboard.type('terminal length 0');await page.keyboard.press('Enter');await page.waitForTimeout(700);
    await page.keyboard.type('show ip route');await page.keyboard.press('Enter');await page.waitForTimeout(1800);
    await page.keyboard.type('exit');await page.keyboard.press('Enter');await page.waitForTimeout(1000);
   }
   // vtysh echoes whatever is typed, so a literal marker would always match.
   // Probe with an arithmetic expansion: only a real shell prints the result.
   let shellReady=false;
   for(let attempt=0;attempt<5&&!shellReady;attempt++){
    await page.keyboard.type('echo $((1234567+7654321))');await page.keyboard.press('Enter');
    for(let i=0;i<8;i++){await page.waitForTimeout(300);if(output.includes('8888888'))break;}
    shellReady=output.includes('8888888');
    if(!shellReady){await page.keyboard.press('Enter');await page.waitForTimeout(200);await page.keyboard.type('exit');await page.keyboard.press('Enter');await page.waitForTimeout(900);}
   }
   result.shell=shellReady;
   const command=`id; hostname; ip -br -4 addr; ip route; printf 'E2E_%s_OK\\n' '${name}'`;
   await page.keyboard.type(command);await page.keyboard.press('Enter');
   for(let i=0;i<20&&!output.includes(`E2E_${name}_OK`);i++)await page.waitForTimeout(500);
   result.root=output.includes('uid=0(root)');result.marker=output.includes(`E2E_${name}_OK`);
   result.pass=result.root&&result.marker;
   fs.writeFileSync(path.join(dir,'evidence','terminal-'+name+'.txt'),output);
   await row.screenshot({path:path.join(dir,'screenshots','terminal-'+name+'.png')});
   await row.getByRole('button',{name:'터미널 닫기',exact:true}).first().click();
  }catch(e){result.pass=false;result.error=e.message;await shot('terminal-error-'+name).catch(()=>{});await page.goto(base+'/project',{waitUntil:'networkidle'});}
  results.push(result);save('terminals-'+(selected.join('_')||'all'),results);console.log(JSON.stringify(result));
 }
}finally{await close('terminals-'+(selected.join('_')||'all'))}
