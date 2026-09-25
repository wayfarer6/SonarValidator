import { useEffect, useRef, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import OfflineImportCard from "../components/offline/OfflineImportCard";
import { useProjectWizard, type SubnetClass } from "../context/ProjectWizardContext";

const SUBNET_CLASSES: SubnetClass[] = ["Confidential", "Sensitive", "Open"];

export default function SubnetAdvanceConfiguration() {
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get("project_id");
  const navigate = useNavigate();
  // 서브넷 목록은 서버(수집 결과/저장된 정책)에서 옵니다.
  const {
    subnets,
    setSubnetClass: applySubnetClass,
    loading,
    error,
    offline,
    reload,
    saving,
    draft,
    draftNote,
  } = useProjectWizard();

  const [selectedSubnet, setSelectedSubnet] = useState("");
  const [ipRange, setIpRange] = useState("");
  const [subnetClass, setSubnetClass] = useState<SubnetClass>("Open");

  // 서브넷이 서버에서 늦게 도착하므로(비동기), 목록이 준비된 뒤 선택을 맞춥니다.
  // 렌더 중 초기값으로 잡으면 목록이 비어 있어 선택이 사라집니다.
  useEffect(() => {
    if (subnets.length === 0) return;
    setSelectedSubnet((prev) => {
      const stillValid = prev !== "" && subnets.some((subnet) => subnet.id === prev);
      return stillValid ? prev : subnets[0].id;
    });
  }, [subnets]);

  // 선택한 서브넷이 바뀌면 CSO 드롭다운을 그 서브넷의 현재 등급으로 맞춥니다.
  useEffect(() => {
    const current = subnets.find((subnet) => subnet.id === selectedSubnet);
    if (current) setSubnetClass(current.subnetClass);
  }, [selectedSubnet, subnets]);
  const [vxlanIp, setVxlanIp] = useState("");
  const [vxlanPort, setVxlanPort] = useState("");

  const vxlanCardRef = useRef<HTMLDivElement>(null);

  const handleContinue = () => {
    console.log("Proceeding to next step...");
    navigate(`/project/create/segmentation?project_id=${projectId ?? ""}`);
  };

  // 좌측 박스의 "VXLAN 설정" 버튼 → 우측 VXLAN 카드로 스크롤/포커스 이동
  const handleFocusVxlan = () => {
    vxlanCardRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
    vxlanCardRef.current?.focus();
  };

  const handleSetSubnetClass = () => {
    applySubnetClass(selectedSubnet, subnetClass);
    console.log(
      `Set Subnet Class - subnet: ${selectedSubnet}, range: ${ipRange || "(auto)"}, class: ${subnetClass}`,
    );
  };

  const handleApplyVxlan = () => {
    console.log(`Apply VXLAN - subnet: ${selectedSubnet}, ip: ${vxlanIp}, port: ${vxlanPort}`);
  };

  return (
    <>
      <PageMeta
        title="Subnet Advance Configuration | TailAdmin - React.js Admin Dashboard Template"
        description="This is Subnet Advance Configuration page for TailAdmin"
      />
      <PageBreadcrumb pageTitle="Subnet Advance Configuration" />

      {/* 전체 메인 컨테이너 박스 */}
      <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
        {/* 상단 헤더 영역 (직전 화면과 동일한 뱃지 + 우측 Continue 버튼) */}
        <div className="mb-6 flex items-center justify-between border-b border-gray-100 pb-4 dark:border-gray-800">
          <span className="inline-block rounded-lg bg-brand-50 px-3 py-1.5 text-sm font-semibold text-brand-600 dark:bg-brand-500/10 dark:text-brand-400">
            Subnet Advance Configuration
          </span>

          {/* 우측 상단 Continue 버튼 */}
          <button
            type="button"
            onClick={handleContinue}
            className="rounded-xl border border-gray-300 bg-white px-6 py-2.5 text-sm font-semibold text-gray-800 shadow-sm transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-white dark:hover:bg-gray-700"
          >
            Continue
          </button>
        </div>

        {/* 서버 상태 안내. 서브넷은 서버 수집 결과/저장된 정책에서 오므로
            조회 중이거나 실패했을 때 화면이 비어 보이지 않게 알려 줍니다. */}
        {loading && (
          <div className="mb-4 rounded-xl border border-gray-200 bg-gray-50 p-3 text-sm text-gray-600 dark:border-gray-700 dark:bg-gray-800/50 dark:text-gray-300">
            서브넷 정보를 불러오는 중...
          </div>
        )}

        {!loading && error && (
          <div className="mb-4 rounded-xl border border-error-200 bg-error-50 p-3 dark:border-error-500/30 dark:bg-error-500/10">
            <p className="text-sm font-medium text-gray-800 dark:text-white/90">
              {offline ? "백엔드에 연결할 수 없습니다" : "서브넷 정보를 불러오지 못했습니다"}
            </p>
            <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">{error}</p>
            <button
              type="button"
              onClick={reload}
              className="mt-2 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-gray-800 transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-white/90 dark:hover:bg-gray-700"
            >
              다시 시도
            </button>
          </div>
        )}

        {/* 서버 수집 결과로 만든 초안임을 알립니다. 등급은 확인 전까지 Open 입니다. */}
        {!loading && draft && (
          <div className="mb-4 rounded-xl border border-brand-200 bg-brand-50 p-3 dark:border-brand-500/30 dark:bg-brand-500/10">
            <p className="text-sm font-medium text-brand-700 dark:text-brand-300">
              수집된 설정에서 만든 초안입니다
            </p>
            <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">
              {draftNote ??
                "등급은 확인 전까지 Open 입니다. 등급을 지정하고 Set Subnet Class 를 누르면 저장됩니다."}
            </p>
          </div>
        )}

        {!loading && !error && subnets.length === 0 && (
          <div className="mb-4 flex flex-col items-center rounded-2xl border border-dashed border-gray-200 py-10 text-center dark:border-gray-800">
            <p className="text-base font-medium text-gray-600 dark:text-gray-400">
              지정할 서브넷이 없습니다
            </p>
            <p className="mt-1 max-w-md text-sm text-gray-400 dark:text-gray-500">
              Agent(Prober)를 배포하고 실행하면 수집된 인터페이스 주소가 서브넷으로
              나타납니다. 또는 아래 Import Offline Prober Data 로 저장한 결과를 가져오세요.
            </p>
          </div>
        )}

        {/* 와이어프레임 기반 좌우 2분할 레이아웃 */}
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
          {/* 좌측: Edit Subnet Manually 박스 */}
          <div className="rounded-xl border border-gray-200 bg-gray-50/50 p-5 dark:border-gray-800 dark:bg-transparent">
            <div className="mb-5 rounded-lg border border-gray-200 bg-white py-2.5 text-center font-medium text-gray-800 shadow-sm dark:border-gray-700 dark:bg-gray-800 dark:text-white/90">
              Edit Subnet Manually
            </div>

            {/* 서브넷 선택 드롭다운 */}
            <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800/50">
              <select
                value={selectedSubnet}
                onChange={(e) => {
                  const nextId = e.target.value;
                  setSelectedSubnet(nextId);
                  // 선택한 서브넷의 현재 등급을 CSO 드롭다운에 동기화
                  const current = subnets.find((subnet) => subnet.id === nextId);
                  if (current) setSubnetClass(current.subnetClass);
                }}
                className="w-full rounded-lg border border-gray-300 bg-transparent px-3.5 py-2.5 text-sm text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white"
              >
                {subnets.length === 0 && <option value="">서브넷 없음</option>}
                {subnets.map((subnet) => (
                  <option key={subnet.id} value={subnet.id}>
                    {subnet.id} {subnet.cidr} ({subnet.subnetClass})
                    {subnet.name ? ` · ${subnet.name}` : ""}
                    {subnet.manuallyEdited ? "" : " · 확인 필요"}
                  </option>
                ))}
              </select>
            </div>

            {/* 세부 설정 박스 (IP Range / VXLAN 설정 / Set Subnet Class / CSO) */}
            <div className="mt-4 space-y-4 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800/50">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <input
                  type="text"
                  value={ipRange}
                  onChange={(e) => setIpRange(e.target.value)}
                  placeholder="IP Range (e.g. 192.168.0.1 ~ .50)"
                  className="w-full rounded-lg border border-gray-300 bg-transparent px-3.5 py-2.5 text-sm text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white"
                />
                <button
                  type="button"
                  onClick={handleFocusVxlan}
                  className="w-full rounded-lg border border-gray-300 bg-white px-3.5 py-2.5 text-sm font-medium text-gray-800 shadow-sm transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-white/90 dark:hover:bg-gray-700"
                >
                  VXLAN 설정
                </button>
              </div>

              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <button
                  type="button"
                  onClick={handleSetSubnetClass}
                  disabled={selectedSubnet === "" || saving}
                  className="w-full rounded-lg border border-gray-300 bg-white px-3.5 py-2.5 text-sm font-medium text-gray-800 shadow-sm transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-white/90 dark:hover:bg-gray-700"
                >
                  {saving ? "저장 중..." : "Set Subnet Class"}
                </button>

                {/* CSO 클래스 드롭다운 */}
                <select
                  value={subnetClass}
                  onChange={(e) => setSubnetClass(e.target.value as SubnetClass)}
                  className="w-full rounded-lg border border-gray-300 bg-transparent px-3.5 py-2.5 text-sm text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white"
                >
                  {SUBNET_CLASSES.map((cls) => (
                    <option key={cls} value={cls}>
                      {cls}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          {/* 우측: VXLAN 설정 + Import Offline Prober Data */}
          <div className="flex flex-col gap-6">
            {/* VXLAN 설정 카드 */}
            <div
              ref={vxlanCardRef}
              tabIndex={-1}
              className="rounded-xl border border-gray-200 bg-gray-50/50 p-5 focus:outline-none focus:ring-2 focus:ring-brand-500 dark:border-gray-800 dark:bg-transparent"
            >
              <div className="mb-5 rounded-lg border border-gray-200 bg-white py-2.5 text-center font-medium text-gray-800 shadow-sm dark:border-gray-700 dark:bg-gray-800 dark:text-white/90">
                VXLAN 설정
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                    Subnet IP
                  </label>
                  <input
                    type="text"
                    value={vxlanIp}
                    onChange={(e) => setVxlanIp(e.target.value)}
                    placeholder="192.168.x.x"
                    className="w-full rounded-lg border border-gray-300 bg-transparent px-3.5 py-2.5 text-sm text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white"
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                    Port NUM
                  </label>
                  <input
                    type="text"
                    value={vxlanPort}
                    onChange={(e) => setVxlanPort(e.target.value)}
                    placeholder="TCP xx"
                    className="w-full rounded-lg border border-gray-300 bg-transparent px-3.5 py-2.5 text-sm text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white"
                  />
                </div>
              </div>

              <button
                type="button"
                onClick={handleApplyVxlan}
                className="mt-4 w-full rounded-xl border border-gray-200 bg-white p-3 text-center text-sm font-medium text-gray-800 shadow-sm transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-white/90 dark:hover:bg-gray-700"
              >
                Apply VXLAN
              </button>
            </div>

            {/* Import Offline Prober Data 카드 (드래그앤드롭 → 서버 업로드)
                이전에는 파일 이름만 화면에 쌓고 서버로 보내지 않아서,
                "올렸는데 반영이 안 된다" 는 상태였습니다.
                이제 선택/드롭 → 사전 검사 → 업로드 → 결과 표시까지 한 카드에서
                처리하고, 반영된 장치는 아래 목록에 즉시 나타납니다. */}
            <OfflineImportCard />
          </div>
        </div>
      </div>
    </>
  );
}
