import { useState, type FormEvent } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import Branch_Divider from "../components/common/Branch_Divider";
import OPNsenseConfigModal from "../components/opnsense/OPNsenseConfigModal";

/**
 * Agent 배포 화면입니다.
 *
 * <h2>OPNsense 카드의 특별 처리</h2>
 * 다른 장비는 이미지/배포 파일을 받아 설치하지만, <b>OPNsense 는 REST API 로
 * 접속</b>하므로 API Key 와 Secret 을 별도로 등록해야 합니다. 그래서 이
 * 카드만 클릭 시 <b>별도 설정 모달</b>을 엽니다.
 *
 * <p>모달에서 저장하면 서버가 즉시 연결을 확인하고 결과를 화면에 돌려줍니다.
 * 저장만 하고 끝내면 운영자가 "등록됐다" 고 믿고 넘어갔다가 정책 푸시
 * 단계에서야 처음 실패를 보게 됩니다.
 *
 * <h2>환경 구성 방식이 둘로 갈리는 이유</h2>
 * 프로버를 배포한 뒤 절차는 <b>서버에 닿는지</b>에 따라 완전히 달라집니다.
 * <ol>
 *   <li><b>온라인</b> — 좌측에 IP/Port 를 넣으면 30초 주기로 자동 전송</li>
 *   <li><b>오프라인</b> — 설정을 JSON 으로 남기고, 다음 단계에서 업로드</li>
 * </ol>
 * <p>이 갈림길을 표시하지 않으면 운영자는 두 절차를 동시에 하는 것으로
 * 오해하고, 연결되지 않는 장비에 IP 를 넣고 "왜 안 올라오지" 를 반복합니다.
 * 그래서 우측 카드에 {@link Branch_Divider} 로 분기 지점을 명시하고,
 * 각 갈래에 다음 행동을 적어 두었습니다.
 */
export default function ProjectCreation() {
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get("project_id");
  const navigate = useNavigate();

  const [managementServerIPAddr, setManagementServerIPAddr] = useState("");
  const [managementServerPort, setManagementServerPort] = useState("");
  // 카드(모달 역할)의 노출 여부를 제어하는 상태
  const [showDeployCard, setShowDeployCard] = useState(false);

  // OPNsense 설정 모달의 열림 여부와 대상 Agent
  const [opnsenseOpen, setOpnsenseOpen] = useState(false);
  const [opnsenseAgentId, setOpnsenseAgentId] = useState("");
  const [opnsenseSavedCount, setOpnsenseSavedCount] = useState(0);


  //project 이름없으면 지정해주는거 필요

  const handleCreateProber = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    console.log(`Setting Management Server - IP: ${managementServerIPAddr}, Port: ${managementServerPort}`);
    setShowDeployCard(true); 

  };

  const handleContinue = () => {
    console.log("Proceeding to next step...");
    // 다음 페이지로 이동하는 로직 (예: navigate(`/project/status/${projectId}`))
    navigate(`/project/create/ViewNodes?project_id=${projectId ?? ""}`);
  };

  return (
    <>
      <PageMeta
        title="Create Project | TailAdmin - React.js Admin Dashboard Template"
        description="This is Create Project page for TailAdmin"
      />
      <PageBreadcrumb pageTitle="Create Project" />

      {/* 전체 메인 컨테이너 박스 */}
      <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
        
        {/* 상단 헤더 영역 (Prober Deployment 뱃지 및 우측 Continue 버튼) */}
        <div className="mb-6 flex items-center justify-between border-b border-gray-100 pb-4 dark:border-gray-800">
          <span className="inline-block rounded-lg bg-brand-50 px-3 py-1.5 text-sm font-semibold text-brand-600 dark:bg-brand-500/10 dark:text-brand-400">
            Prober Deployment
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
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          
          {/* 좌측: Setup Management Server 박스 */}
          <div className="rounded-xl border border-gray-200 bg-gray-50/50 p-5 dark:border-gray-800 dark:bg-transparent">
            
            <div className="mb-5 rounded-lg border border-gray-200 bg-white py-2.5 text-center font-medium text-gray-800 shadow-sm dark:border-gray-700 dark:bg-gray-800 dark:text-white/90">
              Setup Management Server 
            </div>

            <form onSubmit={handleCreateProber} className="space-y-4">
              
              {/* Management Server IP 입력 영역 */}
              <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800/50">
                <input
                  type="text"
                  value={managementServerIPAddr}
                  onChange={(e) => setManagementServerIPAddr(e.target.value)}
                  placeholder="Set Management Server IP"
                  className="w-full rounded-lg border border-gray-300 bg-transparent px-3.5 py-2.5 text-sm text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white"
                />
              </div>

              {/* Management Server Port 입력 영역 */}
              <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800/50">
                <input
                  type="text"
                  value={managementServerPort}
                  onChange={(e) => setManagementServerPort(e.target.value)}
                  placeholder="Set Management Server Port"
                  className="w-full rounded-lg border border-gray-300 bg-transparent px-3.5 py-2.5 text-sm text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white"
                />
              </div>

              {/* Create Prober 버튼 (클릭 시 하단 카드 활성화) */}
              <button
                type="submit"
                className="w-full rounded-xl border border-gray-200 bg-white p-4 text-center font-medium text-gray-800 shadow-sm transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-white/90 dark:hover:bg-gray-700"
              >
                Create Prober
              </button>
            </form>
          </div>

          {/* 우측: 환경 구성 방식 카드
              프로버를 배포한 뒤 갈래가 둘로 나뉩니다.
                (A) 서버에 연결 가능 → 텔레메트리를 서버로 직접 전송
                (B) 서버에 연결 불가 → 설정을 JSON 으로 남겨 나중에 업로드
              이 갈림길을 그냥 여백으로 두면 운영자가 두 절차를 동시에 하는 것으로
              오해하고, 연결이 안 되는 장비에 IP/Port 를 넣고 "왜 안 올라오지" 를
              반복합니다. 그래서 분기 지점을 Branch_Divider 로 명시합니다. */}
          <div className="flex flex-col rounded-xl border border-gray-200 bg-gray-50/50 p-5 dark:border-gray-800 dark:bg-transparent">
            <div className="flex h-full flex-col rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-800">
              <h3 className="mb-4 text-center text-base font-semibold text-gray-800 dark:text-white/90">
                환경 구성 방식
              </h3>

              {/* 공통 선행 단계 */}
              <div className="rounded-lg border border-gray-200 bg-gray-50/60 p-3 dark:border-gray-700 dark:bg-gray-900/40">
                <p className="flex items-start gap-2 text-sm text-gray-600 dark:text-gray-300">
                  <span className="font-semibold">1.</span>
                  <span>네트워크 장비 종류에 맞게 Prober를 다운로드하세요.</span>
                </p>
              </div>

              {/* ─────── 분기 지점 ─────── */}
              <div className="my-4">
                <Branch_Divider
                  orientation="horizontal"
                  label="OR"
                  hint="서버 연결 가능 여부로 갈립니다"
                />
              </div>

              {/* 갈래 A: 온라인 */}
              <div className="rounded-lg border border-gray-200 p-3 dark:border-gray-700">
                <div className="mb-1.5 flex items-center gap-2">
                  <span className="font-semibold text-gray-800 dark:text-white/90">
                    2. 서버에 연결 가능
                  </span>
                  <Badge size="sm" color="success">
                    온라인
                  </Badge>
                </div>
                <p className="text-xs text-gray-500 dark:text-gray-400">
                  좌측에 Management Server IP 와 Port 를 입력하고{" "}
                  <span className="font-medium">Create Prober</span> 를 누르세요.
                  프로버가 30초 주기로 텔레메트리를 자동 전송합니다.
                </p>
              </div>

              {/* 갈래 B: 오프라인 */}
              <div className="mt-3 rounded-lg border border-gray-200 p-3 dark:border-gray-700">
                <div className="mb-1.5 flex items-center gap-2">
                  <span className="font-semibold text-gray-800 dark:text-white/90">
                    또는 연결이 불가능한 경우
                  </span>
                  <Badge size="sm" color="info">
                    오프라인
                  </Badge>
                </div>
                <p className="text-xs text-gray-500 dark:text-gray-400">
                  프로버가 설정을 JSON 파일로 남깁니다. 그 파일을 다음 단계의{" "}
                  <span className="font-medium">Import Offline Prober Data</span>{" "}
                  카드에 끌어다 놓으면 서버가 같은 파서로 변환해 장치 목록에
                  반영합니다.
                </p>
                <code className="mt-2 block rounded bg-gray-100 px-2 py-1.5 font-mono text-[10px] text-gray-700 dark:bg-gray-900 dark:text-gray-300">
                  ./sonar_validator_prober --export-once
                </code>
              </div>

              {/* 오프라인 갈래로 바로 이동 — 파일을 만든 운영자가 다음에
                  어디로 가야 하는지 화면 안에서 알 수 있게 합니다. */}
              <button
                type="button"
                onClick={() =>
                  navigate(`/project/create/subnet?project_id=${projectId ?? ""}`)
                }
                className="mt-3 w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-xs font-medium text-gray-700 transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-white/[0.03]"
              >
                오프라인 데이터 가져오기 화면으로 이동
              </button>
            </div>
          </div>

        </div>
        {showDeployCard && (
          <div className="mt-6 rounded-2xl border border-gray-200 bg-gray-50 p-5 dark:border-gray-700 dark:bg-gray-800/50 lg:p-6">
            <div className="mb-4 flex items-center justify-between border-b border-gray-200 pb-3 dark:border-gray-700">
              <h4 className="font-semibold text-gray-800 dark:text-white/90">
                Deploy & Download
              </h4>
              <button
                onClick={() => setShowDeployCard(false)}
                className="text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
              >
                닫기 ✕
              </button>
            </div>

            {/* 안내 문구 */}
            <div className="mb-4 text-sm text-gray-600 dark:text-gray-300">
              <p>네트워크 장비 유형을 선택하세요</p>
            </div>

            {/* 장비 카드들을 가로로 묶어주는 Flex 컨테이너 */}
            <div className="flex flex-wrap items-center gap-4">

              {/* Cisco Router */}
              <div className="flex flex-col items-center justify-center p-4 w-[150px] h-[170px] border border-gray-200 rounded-xl bg-white dark:bg-gray-800 dark:border-gray-700 hover:border-brand-500 dark:hover:border-brand-500 cursor-pointer transition-all shadow-theme-xs group">
                <div className="w-[100px] h-[100px] flex items-center justify-center mb-2">
                  <img
                    src="https://companieslogo.com/img/orig/CSCO-187e9f61.png?t=1728111511"
                    alt="Cisco Router"
                    className="max-w-full max-h-full object-contain"
                  />
                </div>
                <span className="text-xs font-semibold text-gray-800 dark:text-white/90 text-center">
                  Cisco Router
                </span>
              </div>

              {/* Arista Switch */}
              <div className="flex flex-col items-center justify-center p-4 w-[150px] h-[170px] border border-gray-200 rounded-xl bg-white dark:bg-gray-800 dark:border-gray-700 hover:border-brand-500 dark:hover:border-brand-500 cursor-pointer transition-all shadow-theme-xs group">
                <div className="w-[100px] h-[100px] flex items-center justify-center mb-2">
                  <img
                    src="https://companieslogo.com/img/orig/ANET_BIG-150f82cc.png?t=1720244490"
                    alt="Arista Switch"
                    className="max-w-full max-h-full object-contain"
                  />
                </div>
                <span className="text-xs font-semibold text-gray-800 dark:text-white/90 text-center">
                  Arista Switch
                </span>
              </div>

              {/* OPNsense Firewall — 다른 카드와 달리 API 자격증명이 필요하므로
                  클릭 시 별도 설정 모달을 엽니다. */}
              <div
                onClick={() => {
                  setOpnsenseAgentId(managementServerIPAddr.trim() || "opnsense-1");
                  setOpnsenseOpen(true);
                }}
                title="클릭하면 API Key / Secret 을 등록하는 화면이 열립니다"
                className="flex flex-col items-center justify-center p-4 w-[150px] h-[170px] border border-gray-200 rounded-xl bg-white dark:bg-gray-800 dark:border-gray-700 hover:border-brand-500 dark:hover:border-brand-500 cursor-pointer transition-all shadow-theme-xs group relative"
              >
                {opnsenseSavedCount > 0 && (
                  <span className="absolute right-2 top-2">
                    <Badge size="sm" color="success">
                      {opnsenseSavedCount}
                    </Badge>
                  </span>
                )}
                <div className="w-[100px] h-[100px] flex items-center justify-center mb-2">
                  <img
                    src="https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/png/opnsense.png"
                    alt="OPNsense Firewall"
                    className="max-w-full max-h-full object-contain"
                  />
                </div>
                <span className="text-xs font-semibold text-gray-800 dark:text-white/90 text-center">
                  OPNsense Firewall
                </span>
                <span className="mt-1 text-[10px] text-brand-500">API 설정 필요</span>
              </div>

              {/* Linux VM */}
              <div className="flex flex-col items-center justify-center p-4 w-[150px] h-[170px] border border-gray-200 rounded-xl bg-white dark:bg-gray-800 dark:border-gray-700 hover:border-brand-500 dark:hover:border-brand-500 cursor-pointer transition-all shadow-theme-xs group">
                <div className="w-[100px] h-[100px] flex items-center justify-center mb-2">
                  <img
                    src="https://img.icons8.com/color/150/linux.png"
                    alt="Linux VM"
                    className="max-w-full max-h-full object-contain"
                  />
                </div>
                <span className="text-xs font-semibold text-gray-800 dark:text-white/90 text-center">
                  Linux VM
                </span>
              </div>

              {/* Poc OpenvSwitch */}
              <div className="flex flex-col items-center justify-center p-4 w-[150px] h-[170px] border border-gray-200 rounded-xl bg-white dark:bg-gray-800 dark:border-gray-700 hover:border-brand-500 dark:hover:border-brand-500 cursor-pointer transition-all shadow-theme-xs group">
                <div className="w-[100px] h-[100px] flex items-center justify-center mb-2">
                  <img
                    src="https://images.seeklogo.com/logo-png/27/1/open-vswitch-logo-png_seeklogo-271617.png"
                    alt="OpenvSwitch"
                    className="max-w-full max-h-full object-contain"
                  />
                </div>
                <span className="text-xs font-semibold text-gray-800 dark:text-white/90 text-center">
                  Open vSwitch(for poc)
                </span>
              </div>


              {/* Alpine Based Firewall */}
              <div className="flex flex-col items-center justify-center p-4 w-[150px] h-[170px] border border-gray-200 rounded-xl bg-white dark:bg-gray-800 dark:border-gray-700 hover:border-brand-500 dark:hover:border-brand-500 cursor-pointer transition-all shadow-theme-xs group">
                <div className="w-[100px] h-[100px] flex items-center justify-center mb-2">
                  <img
                    src="https://cdn-icons-png.flaticon.com/512/6071/6071236.png"
                    alt="Alpine Based Firewall"
                    className="max-w-full max-h-full object-contain"
                  />
                </div>
                <span className="text-xs font-semibold text-gray-800 dark:text-white/90 text-center">
                  Alpine Based Firewall (for poc)
                </span>
              </div>

              {/* FRRouting */}
              <div className="flex flex-col items-center justify-center p-4 w-[150px] h-[170px] border border-gray-200 rounded-xl bg-white dark:bg-gray-800 dark:border-gray-700 hover:border-brand-500 dark:hover:border-brand-500 cursor-pointer transition-all shadow-theme-xs group">
                <div className="w-[100px] h-[100px] flex items-center justify-center mb-2">
                  <img
                    src="https://docs.frrouting.org/en/stable-8.5/_static/frr-icon.svg"
                    alt="FRRouting (for poc)"
                    className="max-w-full max-h-full object-contain"
                  />
                </div>
                <span className="text-xs font-semibold text-gray-800 dark:text-white/90 text-center">
                  FRRouting (for poc)
                </span>
              </div>

            </div>
          </div>
        )}

      </div>

      {/* OPNsense 설정 모달 — 기존 Modal 컴포넌트를 재사용합니다. */}
      <OPNsenseConfigModal
        isOpen={opnsenseOpen}
        onClose={() => setOpnsenseOpen(false)}
        agentId={opnsenseAgentId}
        deviceLabel={managementServerIPAddr || null}
        onSaved={() => setOpnsenseSavedCount((count) => count + 1)}
      />
    </>
  );
}