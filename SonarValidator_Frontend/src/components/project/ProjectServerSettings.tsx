import { useState } from "react";
import { Modal } from "../ui/modal";
import { getProject, updateProject } from "../../lib/api/projects";

export default function ProjectServerSettings({ projectId }: { projectId: string }) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [ip, setIp] = useState("");
  const [port, setPort] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const startEditing = async () => {
    setBusy(true); setError(null); setSaved(false);
    try {
      const project = await getProject(projectId);
      setIp(project.management_server_ip ?? "");
      setPort(project.management_server_port?.toString() ?? "3000");
      setOpen(true);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "서버 설정을 불러오지 못했습니다."); }
    finally { setBusy(false); }
  };
  const save = async () => {
    const address = ip.trim();
    if (!/^(?=.{1,253}$)[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?$/.test(address) ||
        !/^\d+$/.test(port.trim()) || Number(port) < 1 || Number(port) > 65535) {
      setError("서버 IP 또는 호스트명과 1~65535 범위의 포트를 입력하세요."); return;
    }
    setBusy(true); setError(null);
    try {
      await updateProject(projectId, { management_server_ip: address, management_server_port: Number(port) });
      setSaved(true); setOpen(false);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "서버 설정을 저장하지 못했습니다."); }
    finally { setBusy(false); }
  };
  return <div id="management-server" className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 pt-4 dark:border-gray-800">
    <div className="text-xs text-gray-500">
      <p>Prober가 접속할 관리 서버 주소를 이 프로젝트에서 관리합니다.</p>
      <p className="mt-1">변경 후 새 번들을 배포해야 기존 Prober에 반영됩니다.</p>
      {saved && <p role="status" className="mt-2 text-success-600">관리 서버 설정을 저장했습니다.</p>}
      {!open && error && <p role="alert" className="mt-2 text-error-600">{error}</p>}
    </div>
    <button type="button" disabled={busy} onClick={() => void startEditing()} className="rounded-lg border border-gray-300 px-3 py-2 text-sm dark:border-gray-600 dark:text-gray-200 disabled:opacity-50">관리 서버 설정</button>
    <Modal ariaLabelledBy="project-server-title" isOpen={open} onClose={() => { if (!busy) setOpen(false); }} className="m-4 max-w-lg">
      <section data-testid="project-server-modal" className="rounded-2xl bg-white p-6 dark:bg-gray-900">
        <h3 id="project-server-title" className="pr-10 text-lg font-semibold dark:text-white">프로젝트 관리 서버</h3>
        <p className="mt-2 text-xs text-gray-500">새로 다운로드할 모든 Prober에 동일하게 적용됩니다.</p>
        <form onSubmit={event => { event.preventDefault(); void save(); }} className="mt-5 space-y-4">
          <label className="block text-sm dark:text-gray-200">서버 IP 또는 호스트명<input aria-label="Management Server IP" value={ip} onChange={event => { setIp(event.target.value); setError(null); }} disabled={busy} className="mt-1 w-full rounded-lg border border-gray-300 bg-transparent px-3 py-2 dark:border-gray-600" /></label>
          <label className="block text-sm dark:text-gray-200">포트<input aria-label="Management Server Port" inputMode="numeric" value={port} onChange={event => { setPort(event.target.value); setError(null); }} disabled={busy} className="mt-1 w-full rounded-lg border border-gray-300 bg-transparent px-3 py-2 dark:border-gray-600" /></label>
          {error && <p role="alert" className="text-sm text-error-600">{error}</p>}
          <div className="flex justify-end gap-2"><button type="button" onClick={() => setOpen(false)} disabled={busy} className="rounded-lg border px-4 py-2 text-sm dark:text-gray-200">취소</button><button type="submit" disabled={busy} className="rounded-lg bg-brand-500 px-4 py-2 text-sm text-white disabled:opacity-50">{busy ? "저장 중..." : "서버 설정 저장"}</button></div>
        </form>
      </section>
    </Modal>
  </div>;
}
