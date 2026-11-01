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
  projectId: string | null;
  topology: ApiTopology | null;
  onRefreshTopology: () => void;
}

interface CollectedRow {
  agentId: string;
  iface: string;
  cidr: string;
  vlan: number | null;
}

type RowState = "registered" | "pending" | "management";

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

  const registered = useMemo(
    () => new Set((topology?.nodes ?? []).map((node) => node.cidr)),
    [topology],
  );

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
        장치에서 수집한 대역은 <b>자동으로 프로젝트 서브넷에 반영</b>됩니다(등급 미분류).
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

function vlanOf(name: string): number | null {
  const match = /^vlan(\d+)$/i.exec(name ?? "");
  return match ? Number(match[1]) : null;
}
