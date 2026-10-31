import type { ApiValidationReport } from "../../lib/api/types";

export default function PolicyAnalysisScope({ report }: { report: ApiValidationReport }) {
  return (
    <div className="mt-2 space-y-1 text-xs text-gray-600 dark:text-gray-300" data-testid="policy-analysis-scope">
      <p>정책 모델 검사 · 실제 패킷 도달성은 별도 확인이 필요합니다.</p>
      <p>경로·ACL 순서·NAT·VRF를 포함한 전달 경로는 분석하지 않습니다.</p>
      {report.model_valid === false && (
        <p className="font-medium text-error-600 dark:text-error-400">
          입력 오류로 검증을 완료할 수 없습니다. 입력을 수정해야 정책을 전송할 수 있습니다.
        </p>
      )}
    </div>
  );
}
