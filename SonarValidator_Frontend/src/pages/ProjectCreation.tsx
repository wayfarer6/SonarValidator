import { useEffect, useState, type FormEvent } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import Branch_Divider from "../components/common/Branch_Divider";
import AgentDeployCard from "../components/project/AgentDeployCard";
import { getProject, updateProject } from "../lib/api/projects";


























export default function ProjectCreation() {
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get("project_id");
  const navigate = useNavigate();
  /** project_id 없이 들어온 경우 안내를 띄우기 위한 상태. */
  const [missingProject, setMissingProject] = useState(false);
  const [deployValidationMessage, setDeployValidationMessage] = useState<string | null>(null);

  const [managementServerIPAddr, setManagementServerIPAddr] = useState("");
  const [managementServerPort, setManagementServerPort] = useState("");
  const [serverSettingsError, setServerSettingsError] = useState<string | null>(null);
  const normalizedPort = managementServerPort.trim();
  const isManagementPortValid =
    /^\d+$/.test(normalizedPort) &&
    Number(normalizedPort) >= 1 &&
    Number(normalizedPort) <= 65535;
  // 장비 카드 목록(Deploy & Download)의 노출 여부
  const [showDeployCard, setShowDeployCard] = useState(false);

  useEffect(() => {
    if (!projectId) return;

    let cancelled = false;
    getProject(projectId)
      .then((project) => {
        if (cancelled) return;
        setManagementServerIPAddr(project.management_server_ip ?? "");
        setManagementServerPort(project.management_server_port?.toString() ?? "");
      })
      .catch((cause: unknown) => {
        if (cancelled) return;
        setServerSettingsError(
          cause instanceof Error
            ? cause.message
            : "프로젝트의 Management Server 설정을 불러오지 못했습니다.",
        );
      });

    return () => {
      cancelled = true;
    };
  }, [projectId]);

  //project 이름없으면 지정해주는거 필요

  const handleCreateProber = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const serverIp = managementServerIPAddr.trim();
    const serverPort = managementServerPort.trim();
    if (!serverIp || !serverPort) {
      setDeployValidationMessage("관리 서버 IP와 포트를 모두 입력하세요.");
      setShowDeployCard(false);
      return;
    }
    if (
      !/^\d+$/.test(serverPort) ||
      Number(serverPort) < 1 ||
      Number(serverPort) > 65535
    ) {
      setDeployValidationMessage("관리 서버 포트는 1에서 65535 사이의 숫자로 입력하세요.");
      setShowDeployCard(false);
      return;
    }

    if (projectId) {
      try {
        await updateProject(projectId, {
          management_server_ip: serverIp,
          management_server_port: Number(serverPort),
        });
      } catch (cause) {
        setDeployValidationMessage(
          cause instanceof Error
            ? `Management Server 설정을 저장하지 못했습니다: ${cause.message}`
            : "Management Server 설정을 저장하지 못했습니다.",
        );
        setShowDeployCard(false);
        return;
      }
    }

    setDeployValidationMessage(null);
    setShowDeployCard(true);

  };

  const handleContinue = () => {
    // ⚠️ project_id 가 없으면 다음 단계가 전부 빈 값이 되고, 마지막 미리보기에서
    //    "프로젝트가 지정되지 않았습니다" 로 막힙니다. 그 전에 이유를 알려 줍니다.
    if (!projectId) {
      setMissingProject(true);
      return;
    }
    navigate(`/project/create/ViewNodes?project_id=${projectId}`);
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

        {/* ⚠️ project_id 없이 들어온 경우 — 다음 단계가 막히므로 이유와 해결을 알려 줍니다. */}
        {missingProject && (
          <div className="mb-5 rounded-xl border border-warning-200 bg-warning-50 p-4 dark:border-warning-500/30 dark:bg-warning-500/10">
            <p className="text-sm font-medium text-gray-800 dark:text-white/90">
              프로젝트가 지정되지 않았습니다
            </p>
            <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">
              이 마법사는 <span className="font-mono">?project_id=</span> 가 있어야 진행할 수
              있습니다. 프로젝트 목록에서 <b>Create Project</b> 로 시작하거나,
              기존 프로젝트의 <b>Agent 추가</b> 에서 오프라인 데이터를 가져오세요.
            </p>
            <button
              type="button"
              onClick={() => navigate("/project")}
              className="mt-3 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
            >
              프로젝트 목록으로
            </button>
          </div>
        )}

        {/* 와이어프레임 기반 좌우 2분할 레이아웃 */}
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">

          {/* 좌측: Setup Management Server 박스 */}
          <div className="rounded-xl border border-gray-200 bg-gray-50/50 p-5 dark:border-gray-800 dark:bg-transparent">

            <div className="mb-5 rounded-lg border border-gray-200 bg-white py-2.5 text-center font-medium text-gray-800 shadow-sm dark:border-gray-700 dark:bg-gray-800 dark:text-white/90">
              Setup Management Server
            </div>

            <form onSubmit={handleCreateProber} className="space-y-4">
              {serverSettingsError && (
                <p className="text-xs text-error-600 dark:text-error-400">
                  저장된 서버 설정을 불러오지 못했습니다: {serverSettingsError}
                </p>
              )}

              {/* Management Server IP 입력 영역 */}
              <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800/50">
                <input
                  type="text"
                  value={managementServerIPAddr}
                  onChange={(e) => {
                    setManagementServerIPAddr(e.target.value);
                    setDeployValidationMessage(null);
                  }}
                  placeholder="Set Management Server IP"
                  aria-label="Management Server IP"
                  aria-required="true"
                  aria-invalid={deployValidationMessage !== null && !managementServerIPAddr.trim()}
                  className="w-full rounded-lg border border-gray-300 bg-transparent px-3.5 py-2.5 text-sm text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white"
                />
              </div>

              {/* Management Server Port 입력 영역 */}
              <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-800/50">
                <input
                  type="text"
                  value={managementServerPort}
                  onChange={(e) => {
                    setManagementServerPort(e.target.value);
                    setDeployValidationMessage(null);
                  }}
                  placeholder="Set Management Server Port"
                  aria-label="Management Server Port"
                  aria-required="true"
                  aria-invalid={deployValidationMessage !== null && !isManagementPortValid}
                  inputMode="numeric"
                  className="w-full rounded-lg border border-gray-300 bg-transparent px-3.5 py-2.5 text-sm text-gray-800 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 dark:border-gray-700 dark:text-white"
                />
              </div>

              {deployValidationMessage && (
                <div
                  role="alert"
                  className="rounded-xl border border-error-200 bg-error-50 p-4 text-sm text-error-700 dark:border-error-500/30 dark:bg-error-500/10 dark:text-error-400"
                >
                  {deployValidationMessage}
                </div>
              )}

              {/* Create Prober 버튼 (클릭 시 하단 카드 활성화) */}
              <button
                type="submit"
                className="w-full rounded-xl border border-gray-200 bg-white p-4 text-center font-medium text-gray-800 shadow-sm transition hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-white/90 dark:hover:bg-gray-700"
              >
                Create Prober
              </button>
            </form>
          </div>

          {





}
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
                  ./sonar_validator_prober --export snapshot.json
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
          <AgentDeployCard
            projectId={projectId ?? "(프로젝트 미지정)"}
            initialServerIp={managementServerIPAddr}
            initialServerPort={managementServerPort}
            onClose={() => setShowDeployCard(false)}
            onImportOffline={() =>
              navigate(`/project/create/subnet?project_id=${projectId ?? ""}`)
            }
          />
        )}

      </div>
    </>
  );
}
