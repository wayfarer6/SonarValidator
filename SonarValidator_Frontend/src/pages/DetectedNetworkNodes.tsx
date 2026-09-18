import { useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import Button from "../components/ui/button/Button";
import { useApi } from "../hooks/useApi";
import { getAllDiscoveredDevices } from "../lib/api";
import type { ApiDiscoveredDevice } from "../lib/api/types";

/**
 * 탐지된 네트워크 노드 화면입니다.
 *
 * <h2>더미 데이터에서 서버 연동으로</h2>
 * 이 화면은 이전에 {@code Record<"routers"|"switches"|...>} 로 하드코딩된
 * 8건을 보여줬습니다. 이제 {@code GET /api/v1/network/discovered} 를 호출해
 * <b>Agent 가 실제로 보고한 장치</b>를 보여줍니다.
 *
 * <h2>벤더가 아니라 계열로 묶는 이유</h2>
 * 기존 화면은 "Cisco Router / Arista Switch / OPNsense Firewall / Linux VM"
 * 4개 고정 카드였습니다. 하지만 랩에는 FRR 라우터, Alpine 방화벽, Open vSwitch
 * 도 있어서 고정 카드로는 담기지 않습니다. 그래서 <b>설정 형식(format)</b>
 * 기준으로 묶어 어떤 벤더가 늘어나도 자동으로 표시되게 했습니다.
 */
export default function DetectedNetworkNodes() {
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get("project_id");
  const navigate = useNavigate();

  const { data, loading, error, offline, reload } = useApi(
    () => getAllDiscoveredDevices(),
    [],
  );

  /** 장치를 설정 형식별로 묶습니다. */
  const grouped = useMemo(() => {
    const map = new Map<string, ApiDiscoveredDevice[]>();
    for (const device of data?.devices ?? []) {
      const key = device.format ?? "미분류";
      const bucket = map.get(key);
      if (bucket) bucket.push(device);
      else map.set(key, [device]);
    }
    return [...map.entries()].sort((a, b) => b[1].length - a[1].length);
  }, [data]);

  /** 카드 접기/펼치기 상태. */
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});

  const toggle = (format: string) =>
    setCollapsed((prev) => ({ ...prev, [format]: !prev[format] }));

  const handleContinue = () => {
    // 다음 단계(서브넷 등급 지정)로 이동합니다.
    navigate(`/project/create/subnet?project_id=${projectId ?? ""}`);
  };

  return (
    <>
      <PageMeta
        title="View Detected Network Nodes | SonarValidator"
        description="Agent 가 보고한 네트워크 장치 목록"
      />
      <PageBreadcrumb pageTitle="View Detected Network Nodes" />

      <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
        <div className="mb-6 flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 pb-4 dark:border-gray-800">
          <div className="flex items-center gap-2">
            <span className="inline-block rounded-lg bg-brand-50 px-3 py-1.5 text-sm font-semibold text-brand-600 dark:bg-brand-500/10 dark:text-brand-400">
              View Detected Network Nodes
            </span>
            {data && (
              <>
                <Badge size="sm" color="success">
                  연결 {data.connected_agents ?? 0}
                </Badge>
                <Badge size="sm" color="light">
                  수집 {data.parsed_devices ?? 0}
                </Badge>
              </>
            )}
          </div>
          <div className="flex gap-2">
            <Button size="sm" variant="outline" onClick={reload}>
              새로고침
            </Button>
            <Button size="sm" onClick={handleContinue}>
              Continue
            </Button>
          </div>
        </div>

        {loading && (
          <div className="py-12 text-center text-sm text-gray-500 dark:text-gray-400">
            장치 목록을 불러오는 중...
          </div>
        )}

        {error && (
          <div className="rounded-xl border border-error-200 bg-error-50 p-4 dark:border-error-500/30 dark:bg-error-500/10">
            <p className="text-sm font-medium text-gray-800 dark:text-white/90">
              {offline ? "백엔드에 연결할 수 없습니다" : "장치 목록을 불러오지 못했습니다"}
            </p>
            <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">{error}</p>
            <Button className="mt-3" size="sm" variant="outline" onClick={reload}>
              다시 시도
            </Button>
          </div>
        )}

        {!loading && !error && grouped.length === 0 && (
          <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-gray-200 py-12 text-center dark:border-gray-800">
            <p className="text-base font-medium text-gray-600 dark:text-gray-400">
              탐지된 장치가 없습니다
            </p>
            <p className="mt-1 max-w-md text-sm text-gray-400 dark:text-gray-500">
              Agent(Prober)를 배포하고 실행하면 수집된 장치가 여기에 표시됩니다.
              텔레메트리는 30초 주기로 수집되므로 실행 후 잠시 기다리세요.
            </p>
          </div>
        )}

        {/* 설정 형식별 카드 그리드 */}
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3 2xl:gap-6">
          {grouped.map(([format, devices]) => (
            <div
              key={format}
              className="rounded-xl border border-gray-200 bg-white shadow-theme-xs dark:border-gray-700 dark:bg-gray-800/50"
            >
              <button
                type="button"
                onClick={() => toggle(format)}
                className="flex w-full items-center justify-between border-b border-gray-100 px-4 py-3 text-left dark:border-gray-700"
              >
                <div className="flex items-center gap-2">
                  <h3 className="text-sm font-semibold text-gray-800 dark:text-white/90">
                    {format}
                  </h3>
                  <Badge size="sm" color="light">
                    {devices.length}
                  </Badge>
                </div>
                <span className="text-xs text-gray-400">
                  {collapsed[format] ? "▼" : "▲"}
                </span>
              </button>

              {!collapsed[format] && (
                <div className="custom-scrollbar max-h-[350px] overflow-y-auto p-4">
                  <div className="flex flex-col">
                    {devices.map((device) => {
                      // 주소가 있으면 대표 주소를 보여줍니다.
                      const primaryAddress =
                        device.interfaces
                          .flatMap((iface) => iface.addresses)
                          .map((address) => address.split("/")[0])[0] ?? null;
                      return (
                        <div
                          key={device.agent_id}
                          className="flex items-start justify-between border-b border-gray-100 py-3 last:border-b-0 dark:border-gray-700"
                        >
                          <div className="flex min-w-0 flex-col">
                            <span className="truncate font-medium text-black dark:text-white">
                              {primaryAddress ?? device.agent_id}
                            </span>
                            <span className="truncate text-xs text-gray-500 dark:text-gray-400">
                              {device.hostname ?? device.agent_id}
                            </span>
                            <span className="mt-0.5 text-[10px] text-gray-400">
                              인터페이스 {device.interfaces.length} · VLAN{" "}
                              {device.vlans.length}
                              {typeof device.route_count === "number" &&
                                ` · 경로 ${device.route_count}`}
                            </span>
                            {device.warnings && device.warnings.length > 0 && (
                              <span className="mt-0.5 text-[10px] text-warning-600 dark:text-orange-400">
                                경고 {device.warnings.length}건
                              </span>
                            )}
                          </div>
                          <span
                            className={`ml-2 h-3 w-3 shrink-0 rounded-full ${
                              device.discovered ? "bg-success-500" : "bg-gray-300"
                            }`}
                            title={device.discovered ? "수집 완료" : "텔레메트리 없음"}
                          />
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </>
  );
}
