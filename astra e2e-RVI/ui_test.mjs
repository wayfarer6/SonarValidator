import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || '/tmp/sonar-rvi-browser/node_modules/playwright');
const dir = path.dirname(fileURLToPath(import.meta.url));
const context = await chromium.launchPersistentContext('/tmp/sonar-rvi-browser-profile', {
  headless: true, viewport: {width: 1600, height: 1000}, locale: 'ko-KR', timezoneId: 'Asia/Seoul',
});
const page = context.pages()[0] || await context.newPage();
const errors = [];
page.on('pageerror', error => errors.push({type:'pageerror', message:error.message}));
page.on('response', response => {
  if (response.status() >= 400 && response.url().includes(':3000'))
    errors.push({type:'http', status:response.status(), url:response.url()});
});
const screenshot = async name => {
  await page.screenshot({path:path.join(dir,'screenshots',name+'.png'), fullPage:true,
    mask: [page.locator('input[type="password"]')]});
};
try {
  await page.goto('http://10.20.0.3:5173/', {waitUntil:'networkidle'});
  if (page.url().includes('/signin')) {
    await page.getByPlaceholder('admin', {exact:true}).fill(process.env.SONAR_UI_USER || 'admin');
    await page.getByPlaceholder('비밀번호를 입력하세요').fill(process.env.SONAR_UI_PASSWORD || 'admin');
    await page.getByRole('button',{name:'Sign in',exact:false}).click();
    await page.waitForURL(url => !url.pathname.includes('signin'), {timeout:15000});
  }
  const action = process.argv[2] || 'initial';
  if (action === 'initial') {
    await page.waitForTimeout(1200);
    await screenshot('01-dashboard');
    const overview = await page.evaluate(async () => {
      const result={};
      for (const endpoint of ['projects','agents/overview','opnsense/credentials']) {
        const r=await fetch('http://10.20.0.3:3000/api/v1/'+endpoint,{credentials:'include'});
        result[endpoint]={status:r.status,body:await r.json()};
      }
      return result;
    });
    fs.writeFileSync(path.join(dir,'ui-initial-state.json'),JSON.stringify(overview,null,2));
    console.log(JSON.stringify(overview,null,2));
    await page.goto('http://10.20.0.3:5173/project',{waitUntil:'networkidle'});
    await screenshot('02-projects');
    console.log((await page.locator('body').innerText()).slice(0,9000));
  } else {
    // Additional scenario scripts share the authenticated browser context.
    const run = await import(path.join(dir, action+'.mjs'));
    await run.default({page,context,dir,screenshot,fs});
  }
} catch(error) {
  await screenshot('error-'+Date.now()).catch(()=>{});
  console.error(error);
  process.exitCode=1;
} finally {
  fs.writeFileSync(path.join(dir,'browser-errors-'+(process.argv[2]||'initial')+'.json'),JSON.stringify(errors,null,2));
  await context.close();
}
