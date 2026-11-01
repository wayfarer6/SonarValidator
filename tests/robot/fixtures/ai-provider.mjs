import http from "node:http";
const host="127.0.0.1",port=Number(process.env.SONAR_MOCK_AI_PORT??3049);
const server=http.createServer(async(req,res)=>{
  if(req.method==="GET"&&req.url==="/v1/models"){res.setHeader("Content-Type","application/json");res.end(JSON.stringify({data:[{id:"mock-advice-v1"}]}));return;}
  if(req.method!=="POST"||req.url!=="/v1/chat/completions"){res.writeHead(404);res.end();return;}
  let raw="";for await(const chunk of req){raw+=chunk;if(raw.length>200000){res.writeHead(413);res.end();return;}}
  try {
    const request=JSON.parse(raw);const text=request.messages.map(m=>m.content).join("\n");
    const agent=request.messages.at(-1).content.match(/"agent_id":"([^"\n]+)"/)?.[1]??"unknown-node";
    const manual=text.includes("MOCK_MANUAL"),config=text.includes("MOCK_CONFIG");
    const vlan=text.includes("MOCK_VLANS");
    const operation=manual?"manual":config?"set_interface_description":vlan?"inspect_vlans":"inspect_routes";
    const commands=manual?["reload"]:config?["configure terminal","interface GigabitEthernet2","description AI_PREVIEW_ONLY","end"]:vlan?["show vlan"]:["show ip route"];
    const answer={risk_level:"MEDIUM",summary:"[모의 공급자] 정책 위반과 설정 경고를 검토한 시험용 응답입니다.",root_cause:"실제 원인 판정이 아닌 기능 검증용 결과입니다. 수집 경고와 대역 분류를 확인하세요.",policy_advice:"진단 조회로 근거를 확인한 뒤 수정안을 검토하세요.",options:[{title:"설정과 경로 확인",approach:"관측된 장비에서 진단 명령을 실행하세요.",security_impact:"조회는 설정을 바꾸지 않습니다.",operational_cost:"낮음",recommended:true}],command_proposals:[{agent_id:agent,title:manual?"수동 검토만 가능한 명령":config?"인터페이스 설명 수정 미리보기":"경로 진단",reason:"[모의] 명령 제안·검토·실행 결과 연결을 확인합니다.",operation,arguments:config?{interface:"GigabitEthernet2",description:"AI_PREVIEW_ONLY"}:{},commands}],evidence:["모의 HTTP 응답 · 실제 AI 모델 호출 아님"],needs_more_data:false};
    res.setHeader("Content-Type","application/json");res.end(JSON.stringify({model:"mock-advice-v1",choices:[{message:{role:"assistant",content:JSON.stringify(answer)}}]}));
  }catch{res.writeHead(400);res.end();}
});
server.listen(port,host,()=>process.stdout.write(`MOCK_AI_READY ${host}:${port}\n`));
for(const signal of ["SIGTERM","SIGINT"])process.on(signal,()=>server.close(()=>process.exit(0)));
