import { Modal } from "../ui/modal";
import Button from "../ui/button/Button";
import type { ApiTopologyNode } from "../../lib/api/types";

type Props = {
  error: string | null;
  agent: { agentId: string; deviceType?: string | null } | null;
  subnets: ApiTopologyNode[];
  selectedCidr: string | null;
  loading: boolean;
  submitting: boolean;
  onSelect: (cidr: string | null) => void;
  onClose: () => void;
  onSubmit: () => void | Promise<void>;
};

export default function IsolationModal({ error, agent, subnets, selectedCidr, loading, submitting, onSelect, onClose, onSubmit }: Props) {
  return (
      <Modal
        isOpen={agent !== null}
        ariaLabel="격리 설정"
        closeDisabled={submitting}
        onClose={onClose}
        className="max-w-lg p-6"
      >
        <div className="space-y-5">
          <div className="pr-12">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">격리 설정</h2>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">{agent?.agentId} · 관리 접속을 유지하며 선택한 범위의 IPv4 데이터 통신을 차단합니다.</p>
          </div>
          <label className="block text-sm text-gray-700 dark:text-gray-200">
            격리 범위
            <select aria-label="격리 범위" value={selectedCidr ?? "__unselected"}
              onChange={(event) => onSelect(event.target.value === "__unselected" ? null : event.target.value)}
              disabled={loading || submitting}
              className="mt-2 w-full rounded-lg border border-gray-300 bg-white p-2 dark:border-gray-600 dark:bg-gray-800">
              <option value="__unselected" disabled>격리 범위를 선택하세요</option>
              {agent?.deviceType?.toUpperCase() !== "FIREWALL" && <option value="">장치 IPv4 통신 · 관리 유지</option>}
              {subnets.map((subnet) => <option key={subnet.id} value={subnet.cidr}>{subnet.label} · {subnet.cidr}</option>)}
            </select>
          </label>
          <p className="text-xs text-gray-500">장비의 적용 확인 후 격리 상태로 표시됩니다. 해제하면 이번 격리에서 변경한 포트·규칙만 복원합니다.</p>
          {error && <p role="alert" className="text-sm text-error-600 dark:text-error-400">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button size="sm" variant="outline" onClick={onClose} disabled={submitting}>취소</Button>
            <Button
              size="sm"
              onClick={() => void onSubmit()}
              disabled={selectedCidr === null || loading || submitting}
            >
              {submitting ? "요청 중..." : "격리 요청"}
            </Button>
          </div>
        </div>
      </Modal>
  );
}
