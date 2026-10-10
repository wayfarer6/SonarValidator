import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const {chromium}=require('/home/osboxes/.local/share/sonar-e2e-2/browser/node_modules/playwright');
export const dir=path.resolve('astra-e2e-d-ai-pbl-2');
export const base='http://172.16.255.245';
const env=Object.fromEntries(fs.readFileSync('.env','utf8').split('\n').filter(l=>l.includes('=')&&!l.startsWith('#')).map(l=>{const i=l.indexOf('=');return [l.slice(0,i),l.slice(i+1).replace(/^['"]|['"]$/g,'')]}));
export const browser=await chromium.launch({headless:true,args:['--disable-dev-shm-usage']});
export const context=await browser.newContext({viewport:{width:1600,height:1000},locale:'ko-KR',timezoneId:'Asia/Seoul',acceptDownloads:true});
export const page=await context.newPage();
export const errors=[];
page.on('pageerror',e=>errors.push({type:'pageerror',message:e.message}));
page.on('response',r=>{if(r.status()>=400&&r.url().includes('/api/'))errors.push({type:'http',status:r.status(),url:r.url()})});
export const save=(name,data)=>fs.writeFileSync(path.join(dir,'evidence',name+'.json'),JSON.stringify(data,null,2));
export async function shot(name){await page.screenshot({path:path.join(dir,'screenshots',name+'.png'),fullPage:true,mask:[page.locator('input[type=password]')]});fs.writeFileSync(path.join(dir,'evidence',name+'.txt'),await page.locator('body').innerText());}
export async function api(endpoint,options={}) {return await page.evaluate(async ({endpoint,options})=>{const r=await fetch('/api/v1/'+endpoint,{...options,headers:{'Content-Type':'application/json',...options.headers}});const text=await r.text();let body=null;try{body=text?JSON.parse(text):null;}catch{return {status:r.status,body:null,raw:text.slice(0,400)};}return {status:r.status,body};},{endpoint,options})}
await page.goto(base,{waitUntil:'networkidle'});
// The SPA resolves /api/v1/auth/me before routing, so the sign-in form can appear
// a moment after networkidle. Wait for either the form or the app shell instead
// of testing the URL once, otherwise a fast check races the redirect and skips login.
const userField=page.getByPlaceholder('admin',{exact:true});
for(let i=0;i<20;i++){
  if(await userField.count()>0)break;
  if(!page.url().includes('signin')&&await page.locator('input[type=password]').count()===0)break;
  await page.waitForTimeout(300);
}
if(await userField.count()>0){
 await userField.fill('admin');
 await page.getByPlaceholder('비밀번호를 입력하세요').fill(env.SONAR_ADMIN_PASSWORD||'admin');
 await page.getByRole('button',{name:'Sign in'}).click();
 await page.waitForURL(u=>!u.pathname.includes('signin'),{timeout:20000});
}
// Fail fast and loudly if the session is still anonymous.
const me=await api('auth/me');
if(me.status!==200)throw new Error('login failed: /auth/me returned '+me.status+' '+JSON.stringify(me.body??me.raw));
export async function close(name){save(name+'-errors',errors);await browser.close();}
if(process.argv[1]?.endsWith('/browser.mjs')){
try{
 const out={};for(const ep of ['projects','agents/overview','agents','agents/configs'])out[ep]=await api(ep);save('initial-api',out);
 await page.goto(base+'/project',{waitUntil:'networkidle'});await shot('01-projects-before');
 console.log((await page.locator('body').innerText()).slice(0,12000));
}finally{await close('initial')}
}
