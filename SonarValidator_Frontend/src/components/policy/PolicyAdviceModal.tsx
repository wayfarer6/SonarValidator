import { useEffect, useRef, useState } from "react";
import { Modal } from "../ui/modal";
import { useAuth } from "../../context/AuthContext";
import { listEnabledAiProviders, type ApiAiProvider } from "../../lib/api/aiLogs";
import { listPolicyAdvices, requestPolicyAdvice, type ApiPolicyAdvice } from "../../lib/api/policyAdvice";
import PolicyAdviceCard from "./PolicyAdviceCard";

interface AdviceProps {projectId:string;ruleId?:string|null;agentId?:string|null;srcSubnet?:string|null;dstSubnet?:string|null;violationLabel?:string}
export function PolicyAdvicePanel({projectId,ruleId,agentId,srcSubnet,dstSubnet,violationLabel}:AdviceProps) {
  const {user}=useAuth();const canAsk=user?.role==="ADMIN"||user?.role==="OPERATOR";
  const [providers,setProviders]=useState<ApiAiProvider[]>([]);const [providerId,setProviderId]=useState<number|"">("");
  const generation=useRef(0);
  const [prompt,setPrompt]=useState("");const [history,setHistory]=useState<ApiPolicyAdvice[]>([]);
  const [loading,setLoading]=useState(true);const [asking,setAsking]=useState(false);const [error,setError]=useState<string|null>(null);
  useEffect(()=>{let active=true;++generation.current;setAsking(false);setLoading(true);setHistory([]);setError(null);
    Promise.all([listEnabledAiProviders(),listPolicyAdvices(projectId,{ruleId,agentId,limit:10})]).then(([p,h])=>{
      if(!active)return;setProviders(p.providers);setProviderId((p.providers.find(v=>v.is_default)??p.providers[0])?.id??"");setHistory(h.advices);
    }).catch(cause=>{if(active)setError(cause instanceof Error?cause.message:String(cause));}).finally(()=>{if(active)setLoading(false);});
    return()=>{active=false;};
  },[projectId,ruleId,agentId]);
  const ask=async()=>{if(asking||providerId==="")return;const started=generation.current;setAsking(true);setError(null);
    try{const advice=await requestPolicyAdvice(projectId,{rule_id:ruleId,agent_id:agentId,src_subnet:srcSubnet,dst_subnet:dstSubnet,provider_id:providerId,prompt:prompt||null});if(started!==generation.current)return;setHistory(rows=>[advice,...rows]);}
    catch(cause){if(started===generation.current)setError(cause instanceof Error?cause.message:String(cause));}finally{if(started===generation.current)setAsking(false);}
  };
  return <section className="space-y-4" data-testid="ai-advice-panel">
    <header><h3 className="text-lg font-semibold dark:text-white">AI 진단·명령 제안</h3><p className="mt-1 text-xs text-gray-500">{violationLabel??(agentId?`${agentId} 설정 오류·수집 경고 분석`:ruleId?`규칙 ${ruleId}`:"프로젝트 정책 분석")}</p><p className="mt-2 text-xs text-gray-500">서버가 다시 계산한 정책 위반과 수집된 설정을 근거로 조언합니다. 실제 패킷 도달성 판정을 대신하지 않습니다.</p></header>
    {loading&&<p className="text-sm text-gray-500">AI 설정과 이력을 불러오는 중...</p>}
    {!loading&&!providers.length&&<p className="rounded-lg bg-warning-50 p-3 text-sm text-warning-700">활성 AI 공급자가 없습니다. 로그 관리의 AI 공급자 설정에서 연결을 등록하세요.</p>}
    {canAsk&&providers.length>0&&<div className="space-y-3 rounded-xl border p-4 dark:border-gray-700">
      <label className="block text-xs dark:text-gray-200">AI 공급자<select aria-label="AI 공급자" disabled={asking} className="mt-2 w-full rounded-lg border bg-white p-2 text-sm dark:bg-gray-900" value={providerId} onChange={e=>setProviderId(Number(e.target.value))}>{providers.map(v=><option key={v.id} value={v.id}>{v.name} · {v.model}</option>)}</select></label>
      <label className="block text-xs dark:text-gray-200">오류 상황·추가 질문<textarea aria-label="AI 추가 질문" rows={2} maxLength={2000} disabled={asking} value={prompt} onChange={e=>setPrompt(e.target.value)} placeholder="예: VLAN이 누락돼 통신이 안 됩니다. 확인 명령과 수정 방법을 제안해 주세요." className="mt-2 w-full rounded-lg border bg-white p-2 text-sm dark:bg-gray-900"/></label>
      <button type="button" disabled={asking||providerId===""} onClick={()=>void ask()} className="rounded-lg bg-brand-500 px-4 py-2 text-sm text-white disabled:opacity-40">{asking?"AI 진단 중...":"AI 진단·명령 요청"}</button>
      <p className="text-xs text-gray-500">선택한 공급자로 프로젝트 대역과 장비 설정 요약을 전송합니다. 요청할 때마다 AI 호출이 발생합니다.</p>
    </div>}
    {error&&<p role="alert" className="text-sm text-error-600">{error}</p>}
    {!loading&&!history.length&&<p className="py-4 text-sm text-gray-500">저장된 AI 조언이 없습니다.</p>}
    {history.map(advice=><div key={advice.advice_id}><p className="mb-2 text-xs text-gray-500">{new Date(advice.created_at).toLocaleString("ko-KR")} · {advice.requested_by}</p><PolicyAdviceCard advice={advice}/></div>)}
  </section>;
}
export default function PolicyAdviceModal(props:AdviceProps&{isOpen:boolean;onClose:()=>void}) {
  return <Modal isOpen={props.isOpen} onClose={props.onClose} className="mx-4 max-h-[90vh] w-full max-w-4xl overflow-y-auto p-6">{props.isOpen&&<PolicyAdvicePanel {...props}/>}</Modal>;
}
