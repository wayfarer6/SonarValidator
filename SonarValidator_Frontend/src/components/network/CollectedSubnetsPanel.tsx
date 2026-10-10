import { useMemo } from "react";
import { Link } from "react-router-dom";
import Badge from "../ui/badge/Badge";
import Button from "../ui/button/Button";
import { useApi } from "../../hooks/useApi";
import { getProjectDevices } from "../../lib/api/discovery";
import { getProject } from "../../lib/api/projects";
import { networkCidr } from "../../lib/discovery";
import type { ApiDiscoveredDevice, ApiProject, ApiTopology } from "../../lib/api/types";

interface CollectedSubnetsPanelProps {
  /** 대상 프로젝트 (없으면 안내만 보여줍니다). */
  projectId: string | null;
  /** 현재 토폴로지 (등록 여부를 대조하기 위해 씁니다). */
  topology: ApiTopology | null;
  /** 토폴로지 다시 읽기 — 수집 결과와 함께 갱신합니다. */
  onRefreshTopology: () => void;
}

/** 장치 한 대에서 수집한 대역 한 줄. */
interface CollectedRow {
  agentId: string;
  iface: string;
  cidr: string;
  vlan: number | null;
}

/**
 * 이 대역이 화면에서 어떤 상태인지입니다.
 *
 * <p>⚠️ {@code management} 를 따로 두는 이유: 관리 인터페이스는 수집은 되지만
 * <b>설계상 서브넷이 되지 않습니다.</b> "대기" 로 보이면 운영자가 반영을
 * 기다리며 프로버를 다시 시작하는 헛수고를 합니다.
 */
type RowState = "registered" | "pending" | "management";

/**
 * 수집된 IP 대역과 그 <b>등록 여부</b>를 보여줍니다.
 *
 * <h2>⚠️ 왜 토폴로지 옆에 필요한가</h2>
 * <p>토폴로지는 <b>등록된 서브넷</b>만 그립니다. 서브넷이 없으면 화면이
 * 비는데, 그때 "장치를 안 붙였나" 와 "붙였는데 반영이 안 됐나" 를 구분할
 * 방법이 화면에 없었습니다. 이 패널이 그 둘을 갈라 보여줍니다.
 *
 * <ul>
 *   <li>수집 0건 → 아직 장치가 붙지 않았거나 프로버가 실행 중이 아닙니다.</li>
 *   <li>수집 N건 · 등록 0건 → 반영 대기 중입니다. (텔레메트리 주기 최대 30초)</li>
 *   <li>수집 N건 · 등록 N건 → 정상입니다.</li>
 * </ul>
 *
 * <h2>관리망을 따로 표시하는 이유</h2>
 * <p>관리 인터페이스(예: {@code Management1})의 주소는 수집되지만 <b>정책
 * 대상이 아니므로</b> 서브넷으로 반영되지 않습니다. 표시하지 않으면
 * "왜 이 대역만 빠졌나" 로 보입니다.
 */
export default function CollectedSubnetsPanel({
  projectId,
  topology,
  onRefreshTopology,
}: CollectedSubnetsPanelProps) {
  const devices = useApi(
    () =>
      projectId
        ? getProjectDevices(projectId)
        : Promise.resolve([] as ApiDiscoveredDevice[]),
    [projectId],
  );

  // 관리망 판단에 프로젝트 설정(관리 서버 주소·프리픽스)이 필요합니다.
  const project = useApi(
    () =>
      projectId
        ? getProject(projectId)
        : Promise.resolve(null as ApiProject | null),
    [projectId],
  );

  /** 수집된 대역 목록입니다. (SVI 우선, 같은 대역은 한 번만) */
  const collected = useMemo(() => {
    const rows: CollectedRow[] = [];
    const seen = new Set<string>();
    for (const device of devices.data ?? []) {
      for (const iface of device.interfaces ?? []) {
        for (const address of iface.addresses ?? []) {
          const cidr = networkCidr(address);
          if (!cidr) continue;
          const key = `${device.agent_id}|${cidr}`;
          if (seen.has(key)) continue;
          seen.add(key);
          rows.push({
            agentId: device.agent_id,
            iface: iface.name,
            cidr,
            vlan: vlanOf(iface.name),
          });
        }
      }
    }
    return rows;
  }, [devices.data]);

  /** 등록된 서브넷 대역입니다. */
  const registered = useMemo(
    () => new Set((topology?.nodes ?? []).map((node) => node.cidr)),
    [topology],
  );

  /**
   * 정책에서 제외되는 관리망 대역입니다.
   *
   * <p>서버({@code ProjectSubnetAutoSyncService})와 <b>같은 규칙</b>으로
   * 계산합니다 — 다르면 화면의 "대기" 개수와 실제 반영 결과가 어긋납니다.
   */
  const management = useMemo(() => {
    const networks = new Set<string>();
    const prefix = project.data?.management_prefix;
    if (prefix) {
      const declared = networkCidr(prefix);
      if (declared) networks.add(declared);
    }
    const serverIp = project.data?.management_server_ip;
    if (serverIp) {
      // 관리 서버와 같은 /24 를 관리망으로 봅니다. (서버와 같은 규칙)
      const derived = networkCidr(`${serverIp.trim()}/24`);
      if (derived) networks.add(derived);
    }
    return networks;
  }, [project.data]);

  const stateOf = (cidr: string): RowState => {
    if (management.has(cidr)) return "management";
    return registered.has(cidr) ? "registered" : "pending";
  };

  const registeredCount = collected.filter((row) => stateOf(row.cidr) === "registered").length;
  const pendingCount = collected.filter((row) => stateOf(row.cidr) === "pending").length;
  const managementCount = collected.length - registeredCount - pendingCount;

  const refresh = () => {
    devices.reload();
    project.reload();
    onRefreshTopology();
  };

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <h4 className="text-sm font-semibold text-gray-800 dark:text-white/90">
            수집된 IP 대역
          </h4>
          {!devices.loading && (
            <Badge size="sm" color={pendingCount > 0 ? "warning" : "light"}>
              {collected.length}건 수집 · {registeredCount}건 반영
              {managementCount > 0 ? ` · ${managementCount}건 관리망` : ""}
            </Badge>
          )}
        </div>
        <div className="flex items-center gap-2">
          {projectId && (
            <Link
              to={`/project/editor/${encodeURIComponent(projectId)}`}
              className="rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-white/[0.03]"
            >
              이름·등급 편집
            </Link>
          )}
          <Button size="sm" variant="outline" onClick={refresh} disabled={devices.loading}>
            새로고침
          </Button>
        </div>
      </div>

      <p className="mb-3 text-xs text-gray-500 dark:text-gray-400">
        장치에서 수집한 대역은 <b>자동으로 프로젝트 서브넷에 반영</b>됩니다(등급 Open).
        관리망과 주소 없는 VLAN 은 제외되며, 등급은 편집 화면에서 올리세요.
      </p>

      {!projectId && (
        <p className="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
          프로젝트를 선택하면 수집 결과가 표시됩니다.
        </p>
      )}

      {projectId && devices.loading && (
        <p className="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
          수집 결과를 불러오는 중...
        </p>
      )}

      {projectId && devices.offline && (
        <p className="rounded-lg bg-warning-50 px-3 py-2 text-xs text-warning-700 dark:bg-warning-500/15 dark:text-orange-300">
          서버에 연결하지 못했습니다. ({devices.error})
        </p>
      )}

      {projectId && !devices.loading && !devices.error && collected.length === 0 && (
        <div className="rounded-lg border border-dashed border-gray-300 px-4 py-6 text-center dark:border-gray-700">
          <p className="text-sm text-gray-600 dark:text-gray-300">
            이 프로젝트에서 수집된 대역이 없습니다.
          </p>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            장치에 프로버를 설치해 연결하면 VLAN·SVI 대역이 자동으로 채워집니다.
            <br />
            실행 중인지 확인: <span className="font-mono">systemctl status sonar_validator_prober</span>
            {" "}(systemd 가 없는 장비는 직접 실행)
          </p>
        </div>
      )}

      {pendingCount > 0 && (
        <p className="mb-3 rounded-lg bg-warning-50 px-3 py-2 text-xs text-warning-700 dark:bg-warning-500/15 dark:text-orange-300">
          {pendingCount}건이 아직 반영되지 않았습니다. 텔레메트리 수신 주기(최대 30초)가
          지나면 자동 반영됩니다. 계속 그대로면 프로버가 실행 중인지 확인하세요.
        </p>
      )}

      {collected.length > 0 && (
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead>
              <tr className="border-b border-gray-100 text-xs uppercase tracking-wide text-gray-400 dark:border-gray-800">
                <th className="py-2 pr-4 font-medium">장치</th>
                <th className="py-2 pr-4 font-medium">인터페이스</th>
                <th className="py-2 pr-4 font-medium">VLAN</th>
                <th className="py-2 pr-4 font-medium">대역</th>
                <th className="py-2 font-medium">반영</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {collected.map((row) => (
                <tr key={`${row.agentId}:${row.iface}:${row.cidr}`}>
                  <td className="py-2 pr-4 font-mono text-xs text-gray-700 dark:text-gray-200">
                    {row.agentId}
                  </td>
                  <td className="py-2 pr-4 font-mono text-xs text-gray-600 dark:text-gray-300">
                    {row.iface}
                  </td>
                  <td className="py-2 pr-4 text-xs text-gray-600 dark:text-gray-300">
                    {row.vlan ?? "—"}
                  </td>
                  <td className="py-2 pr-4 font-mono text-xs text-gray-700 dark:text-gray-200">
                    {row.cidr}
                  </td>
                  <td className="py-2">
                    {stateOf(row.cidr) === "registered" && (
                      <Badge size="sm" color="success">
                        반영됨
                      </Badge>
                    )}
                    {stateOf(row.cidr) === "pending" && (
                      <Badge size="sm" color="warning">
                        대기
                      </Badge>
                    )}
                    {stateOf(row.cidr) === "management" && (
                      <Badge size="sm" color="light">
                        관리망 (제외)
                      </Badge>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/**
 * 인터페이스 이름에서 VLAN 번호를 얻습니다. (예: {@code Vlan8} → 8)
 *
 * @param name 인터페이스 이름
 * @returns VLAN 번호, SVI 형태가 아니면 {@code null}
 */
function vlanOf(name: string): number | null {
  const match = /^vlan(\d+)$/i.exec(name ?? "");
  return match ? Number(match[1]) : null;
}
