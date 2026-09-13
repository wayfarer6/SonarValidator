import { useRef, useState, type ChangeEvent, type DragEvent } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import { useProjectWizard, type SubnetClass } from "../context/ProjectWizardContext";

const SUBNET_CLASSES: SubnetClass[] = ["Confidential", "Sensitive", "Open"];

export default function SubnetAdvanceConfiguration() {
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get("project_id");
  const navigate = useNavigate();
  const { subnets, setSubnetClass: applySubnetClass } = useProjectWizard();

  const [selectedSubnet, setSelectedSubnet] = useState(subnets[0]?.id ?? "");
  const [ipRange, setIpRange] = useState("");
  const [subnetClass, setSubnetClass] = useState<SubnetClass>(
    subnets[0]?.subnetClass ?? "Open",
  );
  const [vxlanIp, setVxlanIp] = useState("");
  const [vxlanPort, setVxlanPort] = useState("");
  const [importedFiles, setImportedFiles] = useState<string[]>([]);
  const [isDragOver, setIsDragOver] = useState(false);

  const vxlanCardRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

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

  const addFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const names = Array.from(files).map((file) => file.name);
    setImportedFiles((prev) => [...prev, ...names]);
    console.log("Import offline prober data:", names);
  };

  const handleDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragOver(false);
    addFiles(e.dataTransfer.files);
  };

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    addFiles(e.target.files);
    e.target.value = "";
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
                {subnets.map((subnet) => (
                  <option key={subnet.id} value={subnet.id}>
                    {subnet.id} {subnet.cidr} ({subnet.subnetClass})
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
                  className="w-full rounded-lg border border-gray-300 bg-white px-3.5 py-2.5 text-sm font-medium text-gray-800 shadow-sm transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-white/90 dark:hover:bg-gray-700"
                >
                  Set Subnet Class
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

            {/* Import Offline Prober Data 카드 (Drop Zone) */}
            <div className="rounded-xl border border-gray-200 bg-gray-50/50 p-5 dark:border-gray-800 dark:bg-transparent">
              <div className="mb-5 rounded-lg border border-gray-200 bg-white py-2.5 text-center font-medium text-gray-800 shadow-sm dark:border-gray-700 dark:bg-gray-800 dark:text-white/90">
                Import Offline Prober Data
              </div>

              <div
                onDragOver={(e) => {
                  e.preventDefault();
                  setIsDragOver(true);
                }}
                onDragLeave={() => setIsDragOver(false)}
                onDrop={handleDrop}
                onClick={() => fileInputRef.current?.click()}
                className={`flex min-h-[140px] cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed p-4 text-sm transition ${
                  isDragOver
                    ? "border-brand-500 bg-brand-50/60 text-brand-600 dark:bg-brand-500/10 dark:text-brand-400"
                    : "border-gray-300 bg-white text-gray-500 hover:border-brand-500 hover:text-brand-600 dark:border-gray-700 dark:bg-gray-800/50 dark:text-gray-400 dark:hover:border-brand-500 dark:hover:text-brand-400"
                }`}
              >
                <span className="text-2xl leading-none">⬇</span>
                <span className="font-medium">Drop Here</span>
                <span className="text-xs text-gray-400 dark:text-gray-500">
                  클릭해서 파일을 선택할 수도 있습니다
                </span>
              </div>
              <input
                ref={fileInputRef}
                type="file"
                multiple
                hidden
                onChange={handleFileChange}
              />

              {importedFiles.length > 0 && (
                <ul className="mt-3 space-y-1 text-xs text-gray-600 dark:text-gray-300">
                  {importedFiles.map((name, idx) => (
                    <li
                      key={`${name}-${idx}`}
                      className="truncate rounded-lg border border-gray-200 bg-white px-3 py-1.5 dark:border-gray-700 dark:bg-gray-800"
                    >
                      📄 {name}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
