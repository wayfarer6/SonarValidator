import { useEffect, useRef, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import { executeAdviceCommand, listAdviceExecutions, type ApiPolicyAdvice, type ApiAdviceExecution } from "../../lib/api/policyAdvice";

const pending = (state: string) => ["PENDING", "DISPATCHED", "RUNNING"].includes(state);
const labels: Record<string,string> = {PENDING:"실행 준비",DISPATCHED:"Prober로 전달됨",RUNNING:"Prober 실행 중",SUCCEEDED:"실행·결과 확인됨",FAILED:"실행 실패",REJECTED:"실행 거부",UNCONFIRMED:"적용 여부 확인 불가"};
export default function AdviceCommandList({advice}:{advice:ApiPolicyAdvice}) {
  const {user}=useAuth();
  const canOperate=user?.role==="ADMIN"||user?.role==="OPERATOR";
  const [executions,setExecutions]=useState<ApiAdviceExecution[]>([]);
  const [review,setReview]=useState<number|null>(null);
  const [confirmed,setConfirmed]=useState(false);
  const [sending,setSending]=useState(false);
  const [error,setError]=useState<string|null>(null);
  const [copied,setCopied]=useState<number|null>(null);
  const requestIds=useRef(new Map<number,string>());
  const hasPending=executions.some(e=>pending(e.status));
  useEffect(()=>{
    if(!canOperate)return;
    let active=true;
    const load=async()=>{
      try {const rows=await listAdviceExecutions(advice.project_id,advice.advice_id);if(active)setExecutions(rows);}
      catch(cause){if(active)setError(cause instanceof Error?cause.message:String(cause));}
    };
    void load();
    const timer=hasPending?window.setInterval(()=>void load(),2000):undefined;
    return()=>{active=false;if(timer)window.clearInterval(timer);};
  },[advice.project_id,advice.advice_id,canOperate,hasPending]);
  const execute=async(index:number)=>{
    if(!confirmed||sending)return;
    setSending(true);setError(null);
    const id=requestIds.current.get(index)??crypto.randomUUID();requestIds.current.set(index,id);
    try {
      const row=await executeAdviceCommand(advice.project_id,advice.advice_id,index,id);
      setExecutions(rows=>[row,...rows.filter(e=>e.execution_id!==row.execution_id)]);
      setReview(null);setConfirmed(false);
      if(!pending(row.status))requestIds.current.delete(index);
    }catch(cause){
      setError(cause instanceof Error?cause.message:String(cause));
      try{setExecutions(await listAdviceExecutions(advice.project_id,advice.advice_id));}catch{ /* Keep the original request error. */ }
    }
    finally{setSending(false);}
  };
  if(!advice.command_proposals?.length)return null;
  return <section className="mt-5 space-y-3" data-testid="ai-command-proposals">
    <h4 className="font-semibold text-gray-800 dark:text-white">명령 제안과 Prober 실행</h4>
    <p className="text-xs text-gray-500">AI 설명과 명령을 검토한 뒤 실행하세요. 지원 유형은 서버와 Prober가 각각 검증하며, 전달과 실제 결과 확인을 구분합니다.</p>
    {error&&<p role="alert" className="text-sm text-error-600">{error}</p>}
    {advice.command_proposals.map(command=><article key={command.index} data-testid={`ai-command-${command.index}`} className="space-y-3 rounded-xl border p-4 dark:border-gray-700">
      <div className="flex flex-wrap items-center justify-between gap-2"><h5 className="text-sm font-semibold dark:text-white">{command.title||command.operation}</h5><span className="font-mono text-xs dark:text-gray-300">{command.agent_id}</span></div>
      <p className="whitespace-pre-wrap text-xs text-gray-600 dark:text-gray-300">{command.reason}</p>
      {command.execution_commands.length>0&&<div><p className="mb-1 text-xs font-medium dark:text-white">검증된 실행 명령</p><pre className="max-h-56 overflow-auto whitespace-pre-wrap rounded-lg bg-gray-900 p-3 text-xs text-gray-100">{command.execution_commands.join("\n")}</pre></div>}
      {command.suggested_commands.length>0&&<details open={!command.execution_commands.length}><summary className="cursor-pointer text-xs text-gray-500">AI가 제안한 명령 원문</summary><pre className="mt-2 max-h-56 overflow-auto whitespace-pre-wrap rounded-lg bg-gray-50 p-3 text-xs dark:bg-gray-800 dark:text-gray-200">{command.suggested_commands.join("\n")}</pre></details>}
      {command.verification_commands.length>0&&<p className="font-mono text-xs text-gray-500">결과 확인: {command.verification_commands.join("; ")}</p>}
      {!command.executable&&<div className="rounded-lg bg-warning-50 p-3 text-xs text-warning-700 dark:bg-warning-500/10 dark:text-warning-400"><p className="font-medium">Prober 실행 불가 · 수동 검토</p>{command.blocked_reasons.map((reason,i)=><p key={i}>{reason}</p>)}</div>}
      {command.changes_configuration&&<p className="text-xs text-warning-700 dark:text-warning-400">실행 중인 장비 설정을 변경합니다. 부팅 설정으로 저장하는 작업은 별도입니다.</p>}
      <div className="flex flex-wrap gap-2">
        <button type="button" className="rounded-lg border px-3 py-2 text-xs dark:text-white" onClick={async()=>{try{await navigator.clipboard.writeText((command.execution_commands.length?command.execution_commands:command.suggested_commands).join("\n"));setCopied(command.index);}catch{setError("클립보드에 접근하지 못했습니다. 명령을 직접 선택해 복사하세요.");}}}>{copied===command.index?"복사됨":"명령 복사"}</button>
        {canOperate&&<button type="button" disabled={!command.executable||hasPending||sending} className="rounded-lg bg-brand-500 px-3 py-2 text-xs text-white disabled:opacity-40" onClick={()=>{setReview(command.index);setConfirmed(false);requestIds.current.delete(command.index);}}>Prober 실행 검토</button>}
      </div>
      {review===command.index&&<div className="space-y-3 rounded-lg border border-brand-300 p-3" data-testid="ai-execution-review">
        <p className="text-sm font-medium dark:text-white">{command.agent_id}에서 {command.changes_configuration?"설정 변경":"진단 명령"}을 실행합니다.</p>
        <label className="flex items-start gap-2 text-xs dark:text-gray-200"><input type="checkbox" checked={confirmed} disabled={sending} onChange={e=>setConfirmed(e.target.checked)}/>대상 노드, 실행 명령과 영향을 확인했습니다.</label>
        <div className="flex gap-2"><button type="button" disabled={!confirmed||sending} onClick={()=>void execute(command.index)} className="rounded-lg bg-brand-500 px-3 py-2 text-xs text-white disabled:opacity-40">{sending?"요청 중...":"확인 후 Prober 실행"}</button><button type="button" disabled={sending} onClick={()=>setReview(null)} className="rounded-lg border px-3 py-2 text-xs dark:text-white">취소</button></div>
      </div>}
      {executions.filter(e=>e.proposal_index===command.index).map(execution=><div key={execution.execution_id} data-testid="ai-execution-result" className="rounded-lg bg-gray-50 p-3 text-xs dark:bg-gray-800 dark:text-gray-200">
        <p role="status" className="font-medium">{labels[execution.status]??execution.status}</p><p className="mt-1">{execution.detail}</p>
        <p className="mt-1 text-gray-500">{new Date(execution.created_at).toLocaleString("ko-KR")} · {execution.requested_by}</p>
        {execution.result?.output&&<pre className="mt-2 max-h-64 overflow-auto whitespace-pre-wrap font-mono">{execution.result.output}</pre>}
      </div>)}
    </article>)}
    {!canOperate&&<p className="text-xs text-gray-500">명령 실행은 관리자 또는 운영자에게 허용됩니다.</p>}
  </section>;
}
