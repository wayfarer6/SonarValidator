import {page,base,api,save,shot,close} from './browser.mjs';
try {
 await page.goto(base+'/project',{waitUntil:'networkidle'});
 await page.getByRole('button',{name:'Add Agent',exact:true}).last().click();
 await page.getByRole('button',{name:'Linux VM',exact:false}).click();
 const results=[];
 for(const name of ['TOD-Cam','UAV','VDI-1','VDI-2','ATICS','KNCCS','AFCCS','Public-Web-Server','Management-Console']) {
   await page.getByPlaceholder('예: VDI-1-agent').fill(name);
   const response=page.waitForResponse(r=>r.url().includes('/agents/expected')&&r.request().method()==='POST');
   await page.getByRole('button',{name:'서버에 등록',exact:true}).click();
   const r=await response;results.push({name,status:r.status(),body:await r.json()});
   if(r.status()!==200)throw Error('register failed '+name);
 }
 save('vm-ui-registration',results);await shot('02-vm-registration');
 console.log(results.map(r=>[r.name,r.status]));
}catch(e){await shot('registration-error');throw e}finally{await close('registration')}
