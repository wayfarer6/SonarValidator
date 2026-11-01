import { useEffect, useState } from "react";
import { getMonitorSettings, updateMonitorSettings } from "../../lib/api";
import type { ApiMonitorSettings } from "../../lib/api/types";

type Unit = "seconds" | "minutes" | "hours";
interface Draft { amount: string; unit: Unit; revision: number; baseline: number }
const factors: Record<Unit, number> = { seconds: 1, minutes: 60, hours: 3600 };
function draftFor(settings: ApiMonitorSettings): Draft {
  const seconds = settings.monitor_interval_seconds;
  const unit: Unit = seconds % 3600 === 0 ? "hours" : seconds % 60 === 0 ? "minutes" : "seconds";
  return { amount: String(seconds / factors[unit]), unit, revision: settings.revision, baseline: seconds };
}
function duration(seconds: number): string {
  if (seconds % 3600 === 0) return `${seconds / 3600}시간`;
  if (seconds % 60 === 0) return `${seconds / 60}분`;
  return `${seconds}초`;
}

type MonitorProps = {
  agentId: string; projectId: string; onDirtyChange: (dirty: boolean) => void; onSaved: () => void;
};

export default function NodeMonitorSettings(props: MonitorProps) {
  return <MonitorSettingsEditor key={`${props.projectId}:${props.agentId}`} {...props} />;
}

function MonitorSettingsEditor({ agentId, projectId, onDirtyChange, onSaved }: MonitorProps) {
  const [settings, setSettings] = useState<ApiMonitorSettings | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  useEffect(() => {
    let active = true;
    let polling = false;
    const load = async () => {
      if (polling) return;
      polling = true;
      try {
        const value = await getMonitorSettings(agentId, projectId);
        if (!active) return;
        setSettings(current => current && current.revision > value.revision ? current : value);
        setDraft(current => current ?? draftFor(value));
        setError(null);
      } catch (cause) { if (active) setError(cause instanceof Error ? cause.message : String(cause)); }
      finally { polling = false; }
    };
    void load();
    const timer = window.setInterval(() => void load(), 2000);
    return () => { active = false; window.clearInterval(timer); };
  }, [agentId, projectId]);
  const seconds = draft ? Number(draft.amount) * factors[draft.unit] : NaN;
  const valid = Boolean(draft?.amount.trim()) && Number.isInteger(seconds) && seconds >= 1 && seconds <= 86400;
  const dirty = Boolean(draft && (!valid || seconds !== draft.baseline));
  const changedElsewhere = Boolean(settings && draft && settings.revision !== draft.revision);
  useEffect(() => { onDirtyChange(dirty); }, [dirty, onDirtyChange]);
  const reload = async () => {
    try {
      const value = await getMonitorSettings(agentId, projectId);
      setSettings(value); setDraft(draftFor(value)); setError(null); setSaved(false);
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
  };
  const save = async () => {
    if (!valid || !draft || changedElsewhere) return;
    setSaving(true); setError(null); setSaved(false);
    try {
      const value = await updateMonitorSettings(agentId, projectId, seconds, draft.revision);
      setSettings(value); setDraft(draftFor(value));
      setSaved(true); onSaved();
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setSaving(false); }
  };
  const input = "w-full rounded-lg border border-gray-300 bg-white p-3 text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-900 dark:text-white";
  return <section data-testid="node-monitor-settings" className="space-y-5">
    <div><h3 className="text-lg font-semibold dark:text-white">수집 주기</h3>
      <p className="mt-1 text-sm text-gray-500">이 노드의 Prober가 데이터를 수집하는 간격입니다. 다른 노드의 주기는 바뀌지 않습니다.</p></div>
    {error && <p role="alert" className="text-sm text-error-600">{error}</p>}
    {!settings && !error && <p>수집 설정을 불러오는 중...</p>}
    {settings && <>
      <dl className="grid gap-4 rounded-xl bg-gray-50 p-4 text-sm sm:grid-cols-2 dark:bg-gray-800 dark:text-white">
        <div><dt className="text-gray-500">저장한 주기</dt><dd className="mt-1 font-medium" data-testid="monitor-desired">{duration(settings.monitor_interval_seconds)}</dd></div>
        <div><dt className="text-gray-500">Prober가 보고한 주기</dt><dd className="mt-1 font-medium" data-testid="monitor-reported">{settings.reported_interval_seconds === null ? "아직 확인되지 않음" : duration(settings.reported_interval_seconds)}</dd></div>
      </dl>
      <p role="status" data-testid="monitor-apply-status" className={`text-sm ${settings.status === "error" ? "text-error-600" : "text-gray-600 dark:text-gray-300"}`}>
        {settings.status === "applied" ? "Prober 적용 확인됨" : settings.status === "offline" ? "Prober 수집 연결 대기 · 저장한 값은 재연결 시 전달됩니다." : settings.status === "error" ? settings.error : "저장한 설정의 Prober 적용을 기다리는 중입니다."}
      </p>
    </>}
    {draft && <div className="grid max-w-lg gap-4 sm:grid-cols-2">
      <label className="text-sm dark:text-white">주기 값<input aria-label="수집 주기 값" type="number" disabled={saving} min={1 / factors[draft.unit]} max={86400 / factors[draft.unit]} step="any" className={`${input} mt-2`} value={draft.amount} onChange={event => { setDraft({ ...draft, amount: event.target.value }); setSaved(false); }} /></label>
      <label className="text-sm dark:text-white">단위<select disabled={saving} aria-label="수집 주기 단위" className={`${input} mt-2`} value={draft.unit} onChange={event => { setDraft({ ...draft, unit: event.target.value as Unit }); setSaved(false); }}><option value="seconds">초</option><option value="minutes">분</option><option value="hours">시간</option></select></label>
    </div>}
    <p className="text-xs text-gray-500">1초~24시간, 기본 30초입니다. 수집 소요 시간과 장비 자체의 갱신 주기에 따라 실제 데이터 시각은 달라질 수 있습니다.</p>
    {draft && !valid && <p role="alert" className="text-sm text-error-600">1초~24시간 범위로 입력하세요. 1초보다 작은 단위는 지원하지 않습니다.</p>}
    {changedElsewhere && <p role="alert" className="text-sm text-warning-600">다른 화면에서 설정이 변경됐습니다. 다시 불러온 뒤 수정하세요.</p>}
    <footer className="flex flex-wrap items-center justify-between gap-3 border-t pt-4">
      <button type="button" onClick={() => void reload()} disabled={saving} className="rounded-lg border px-4 py-2 text-sm dark:text-white">설정 다시 불러오기</button>
      <div className="flex items-center gap-3">{saved && <span className="text-sm text-success-600">저장됨</span>}
        <button type="button" onClick={() => void save()} disabled={(!dirty && settings?.status !== "error" && settings?.status !== "pending") || !valid || saving || changedElsewhere} className="rounded-lg bg-brand-500 px-5 py-2.5 text-sm text-white disabled:opacity-40">{saving ? "저장 중..." : !dirty && (settings?.status === "error" || settings?.status === "pending") ? "수집 주기 다시 전달" : "수집 주기 저장"}</button>
      </div>
    </footer>
  </section>;
}
