import Badge from "../ui/badge/Badge";
import { adviceRiskColor } from "../../lib/api/policyAdvice";
import type { ApiPolicyAdvice } from "../../lib/api/policyAdvice";

export default function PolicyAdviceCard({
  advice,
}: {
  advice: ApiPolicyAdvice;
}) {
  if (!advice.succeeded) {
    return (
      <div className="rounded-xl border border-error-200 bg-error-50 p-4 dark:border-error-500/30 dark:bg-error-500/10">
        <div className="flex flex-wrap items-center gap-2">
          <Badge size="sm" color="error">
            조언 실패
          </Badge>
          <span className="font-mono text-[11px] text-gray-500">
            {advice.advice_id}
          </span>
          {advice.provider_name && (
            <span className="text-[11px] text-gray-500">
              {advice.provider_name}
              {advice.model ? ` · ${advice.model}` : ""}
            </span>
          )}
        </div>
        <p className="mt-2 text-xs text-gray-700 dark:text-gray-300">
          {advice.error_message}
        </p>
        <p className="mt-2 text-[11px] text-gray-500 dark:text-gray-400">
          AI 공급자 설정(모델명·API Key·서버 기동)을 확인하세요.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-gray-200 p-4 dark:border-gray-700">
      {/* 헤더 */}
      <div className="flex flex-wrap items-center gap-2">
        <Badge size="sm" color={adviceRiskColor(advice.risk_level)}>
          위험도 {advice.risk_level ?? "미상"}
        </Badge>
        <span className="font-mono text-[11px] text-gray-500">
          {advice.advice_id}
        </span>
        {advice.provider_name && (
          <span className="text-[11px] text-gray-500">
            {advice.provider_name}
            {advice.model ? ` · ${advice.model}` : ""}
          </span>
        )}
        {advice.elapsed_ms != null && (
          <span className="text-[11px] text-gray-400">
            {(advice.elapsed_ms / 1000).toFixed(1)}초
          </span>
        )}
        <span className="text-[11px] text-gray-400">
          위반 {advice.included_violation_count ?? 0}건 근거
        </span>
        {/* ⚠️ 잘렸으면 반드시 알립니다. 일부만 보고 내린 조언을 과신하면 위험합니다. */}
        {advice.truncated && (
          <Badge size="sm" color="warning">
            전체 {advice.violation_count}건 중 일부만 사용
          </Badge>
        )}
      </div>

      {/* 정책 관점 핵심 조언 — 한 줄로 먼저 결론 */}
      {advice.policy_advice && (
        <div className="mt-3 rounded-lg bg-brand-50 p-3 dark:bg-brand-500/10">
          <h5 className="text-xs font-semibold text-brand-600 dark:text-brand-400">
            정책 조언
          </h5>
          <p className="mt-1 whitespace-pre-wrap text-sm text-gray-800 dark:text-gray-200">
            {advice.policy_advice}
          </p>
        </div>
      )}

      {advice.summary && (
        <div className="mt-3">
          <h5 className="text-xs font-semibold text-gray-700 dark:text-gray-300">
            요약
          </h5>
          <p className="mt-1 whitespace-pre-wrap text-sm text-gray-700 dark:text-gray-300">
            {advice.summary}
          </p>
        </div>
      )}

      {advice.root_cause && (
        <div className="mt-3">
          <h5 className="text-xs font-semibold text-gray-700 dark:text-gray-300">
            근본 원인
          </h5>
          <p className="mt-1 whitespace-pre-wrap text-sm text-gray-700 dark:text-gray-300">
            {advice.root_cause}
          </p>
        </div>
      )}

      {/* 해결 선택지 — 이 카드의 핵심 */}
      {advice.options.length > 0 && (
        <div className="mt-4">
          <h5 className="text-xs font-semibold text-gray-700 dark:text-gray-300">
            해결 선택지 {advice.options.length}가지
          </h5>
          <div className="mt-2 space-y-2">
            {advice.options.map((option, index) => (
              <div
                key={`${index}-${option.title}`}
                className={`rounded-lg border p-3 ${
                  option.recommended
                    ? "border-brand-300 bg-brand-50/50 dark:border-brand-500/40 dark:bg-brand-500/10"
                    : "border-gray-200 dark:border-gray-700"
                }`}
              >
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-semibold text-gray-800 dark:text-white/90">
                    {option.title}
                  </span>
                  {option.recommended && (
                    <Badge size="sm" color="primary">
                      권장
                    </Badge>
                  )}
                </div>

                {option.approach && (
                  <p className="mt-1.5 whitespace-pre-wrap text-xs text-gray-700 dark:text-gray-300">
                    {option.approach}
                  </p>
                )}

                {/* ⚠️ 보안 영향과 운영 부담을 나란히 둡니다.
                    한쪽만 보이면 운영자는 "쉬운 안" 만 고릅니다. */}
                {(option.security_impact || option.operational_cost) && (
                  <dl className="mt-2 grid grid-cols-1 gap-2 text-[11px] sm:grid-cols-2">
                    {option.security_impact && (
                      <div className="rounded bg-gray-50 p-2 dark:bg-white/[0.03]">
                        <dt className="font-medium text-gray-600 dark:text-gray-400">
                          보안 영향
                        </dt>
                        <dd className="mt-0.5 text-gray-700 dark:text-gray-300">
                          {option.security_impact}
                        </dd>
                      </div>
                    )}
                    {option.operational_cost && (
                      <div className="rounded bg-gray-50 p-2 dark:bg-white/[0.03]">
                        <dt className="font-medium text-gray-600 dark:text-gray-400">
                          운영 부담
                        </dt>
                        <dd className="mt-0.5 text-gray-700 dark:text-gray-300">
                          {option.operational_cost}
                        </dd>
                      </div>
                    )}
                  </dl>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {advice.evidence.length > 0 && (
        <div className="mt-4">
          <h5 className="text-xs font-semibold text-gray-700 dark:text-gray-300">
            판단 근거
          </h5>
          <ul className="mt-1 space-y-1 text-[11px] text-gray-600 dark:text-gray-400">
            {advice.evidence.map((item, index) => (
              <li key={`${index}-${item}`} className="flex items-start gap-1.5">
                <span className="mt-1 size-1 shrink-0 rounded-full bg-current opacity-50" />
                <span className="whitespace-pre-wrap">{item}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* 구조화 파싱 실패 시 원문을 그대로 보여줍니다. */}
      {!advice.structured && advice.raw_response && (
        <div className="mt-3">
          <h5 className="text-xs font-semibold text-gray-700 dark:text-gray-300">
            모델 응답 (구조화 파싱 실패 — 원문)
          </h5>
          <pre className="custom-scrollbar mt-1 max-h-[300px] overflow-auto whitespace-pre-wrap rounded-lg bg-gray-50 p-3 text-[11px] text-gray-700 dark:bg-gray-900/50 dark:text-gray-300">
            {advice.raw_response}
          </pre>
        </div>
      )}

      {advice.needs_more_data && (
        <p className="mt-3 rounded-lg bg-warning-50 p-2.5 text-[11px] text-warning-700 dark:bg-warning-500/10 dark:text-warning-400">
          ⚠️ 모델이 판단에 추가 자료가 필요하다고 보고했습니다. 위의 근거를
          확인하고 더 구체적인 질문을 덧붙여 다시 물어보세요.
        </p>
      )}
    </div>
  );
}
