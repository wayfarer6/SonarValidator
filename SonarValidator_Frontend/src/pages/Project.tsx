import { useState } from "react";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import { useModal } from "../hooks/useModal";
import { Modal } from "../components/ui/modal";
import Button from "../components/ui/button/Button";
import Input from "../components/form/input/InputField";
import Label from "../components/form/Label";
import Badge from "../components/ui/badge/Badge";
import { Link, useNavigate } from "react-router";
import { useApi } from "../hooks/useApi";
import { useApiAction } from "../hooks/useApiAction";
import { createProject, listProjects } from "../lib/api/projects";
import AgentDeployCard from "../components/project/AgentDeployCard";

/**
 * 프로젝트 목록 화면입니다.
 *
 * <h2>더미 데이터에서 서버 연동으로</h2>
 * 이 화면은 이전에 {@code useState([...])} 로 하드코딩된 두 건을 보여줬습니다.
 * 이제 {@code GET /api/v1/projects} 를 호출하고, 생성도
 * {@code POST /api/v1/projects} 로 서버에 저장합니다.
 *
 * <h2>생성 직후 편집 화면으로 보내는 이유</h2>
 * 기존에는 4단계 마법사({@code /project/create/*})로 보냈습니다. 이제는
 * 편집 화면 하나에서 서브넷/규칙/검증을 모두 다루므로, 단계를 나누면
 * 같은 정보를 두 곳에서 관리하게 됩니다. 마법사 경로는 그대로 남겨 두어
 * 기존 링크가 깨지지 않게 했습니다.
 *
 * <h2>오프라인 안내를 넣은 이유</h2>
 * 백엔드가 꺼져 있으면 화면이 그냥 비어 보입니다. "왜 프로젝트가 안 보이지"
 * 로 오해하지 않도록 연결 실패를 구분해 실행 방법까지 안내합니다.
 *
 * <h2>Manage 와 Add Agent 를 나눈 이유</h2>
 * 두 동작은 목적이 다릅니다.
 * <ul>
 *   <li><b>Manage</b> — 이미 있는 서브넷/규칙을 보고 검증합니다. 편집 화면으로
 *       이동합니다.</li>
 *   <li><b>Add Agent</b> — 이 프로젝트에 장비를 붙입니다. 화면을 옮기지 않고
 *       목록에서 <b>그 행 아래에</b> 배포 카드를 펼칩니다. 여러 프로젝트를
 *       오가며 배포할 때 목록으로 돌아오는 왕복이 없어집니다.</li>
 * </ul>
 */
export default function Project() {
  const navigate = useNavigate();
  const { data, loading, error, offline, reload } = useApi(() => listProjects(), []);
  const createAction = useApiAction(createProject);

  const { isOpen, openModal, closeModal } = useModal();
  const [projectName, setProjectName] = useState("");
  const [categoryName, setCategoryName] = useState("");
  const [projectDescription, setProjectDescription] = useState("");

  /**
   * 배포 카드가 펼쳐진 프로젝트 키입니다. 한 번에 하나만 엽니다.
   *
   * <p>여러 개를 동시에 열어 두면 어느 카드가 어느 프로젝트의 설정인지
   * 헷갈립니다. Management Server IP 를 프로젝트마다 다르게 넣는 경우가
   * 특히 그렇습니다. ("지금 고친 값이 어디에 들어가나")
   */
  const [deployingProjectId, setDeployingProjectId] = useState<string | null>(null);

  /** 이름 검증 실패 문구. `alert()` 대신 인라인으로 보여 줍니다. */
  const [nameError, setNameError] = useState<string | null>(null);

  /**
   * 프로젝트를 생성하고 **생성 마법사**로 이동합니다.
   *
   * <h2>⚠️ 편집 화면이 아니라 마법사로 가는 이유</h2>
   * <p>새 프로젝트는 서브넷·분할 규칙이 아직 없습니다. 편집 화면은 <b>이미
   * 정책이 있는</b> 프로젝트를 고치는 곳이므로, 빈 프로젝트를 거기로 보내면
   * 사용자가 "이제 뭘 해야 하나" 를 알 수 없습니다.
   *
   * <p>예전에는 이 자리가 비어 있어 `project_id` 없이 마법사에 들어가면
   * 전 단계가 빈 값이 되고 마지막 미리보기에서 막혔습니다.
   * 여기서 `project_id` 를 붙여 주면 흐름이 끊기지 않습니다.
   */
  const handleCreate = async () => {
    if (!projectName.trim()) {
      // ⚠️ alert() 는 화면을 막고 스타일도 맞지 않습니다.
      //    다른 입력 검증과 같이 인라인으로 보여 줍니다.
      setNameError("프로젝트 이름을 입력하세요.");
      return;
    }
    setNameError(null);

    const created = await createAction.run({
      name: projectName.trim(),
      category: categoryName.trim(),
      description: projectDescription.trim(),
      status: "Planning",
    });

    if (!created) return;

    setProjectName("");
    setCategoryName("");
    setProjectDescription("");
    closeModal();
    reload();
    navigate(`/project/create?project_id=${encodeURIComponent(created.project_id)}`);
  };

  const projects = data?.projects ?? [];

  return (
    <>
      <PageMeta
        title="Project List | SonarValidator"
        description="프로젝트 목록과 망분리 검증 상태"
      />
      <PageBreadcrumb pageTitle="Project" />

      <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3 lg:mb-7">
          <div className="flex items-center gap-2">
            <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">Project</h3>
            {data && (
              <Badge size="sm" color="light">
                {data.total}개
              </Badge>
            )}
          </div>
          <button
            onClick={openModal}
            className="inline-flex items-center justify-center rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600"
          >
            Create Project
          </button>
        </div>

        {loading && (
          <div className="py-12 text-center text-sm text-gray-500 dark:text-gray-400">
            프로젝트를 불러오는 중...
          </div>
        )}

        {error && (
          <div className="mb-5 rounded-xl border border-error-200 bg-error-50 p-4 dark:border-error-500/30 dark:bg-error-500/10">
            <p className="text-sm font-medium text-gray-800 dark:text-white/90">
              {offline ? "백엔드에 연결할 수 없습니다" : "프로젝트를 불러오지 못했습니다"}
            </p>
            <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">{error}</p>
            {offline && (
              <p className="mt-2 rounded bg-white/60 p-2 font-mono text-[11px] text-gray-700 dark:bg-black/20 dark:text-gray-200">
                cd SonarValidator_Backend && ./mvnw spring-boot:run
              </p>
            )}
            <Button className="mt-3" size="sm" variant="outline" onClick={reload}>
              다시 시도
            </Button>
          </div>
        )}

        <div className="space-y-6">
          {!loading && !error && projects.length === 0 && (
            <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-gray-200 py-12 text-center dark:border-gray-800">
              <p className="text-base font-medium text-gray-600 dark:text-gray-400">
                No project
              </p>
              <p className="mt-1 text-sm text-gray-400 dark:text-gray-500">
                Get started by creating a new project.
              </p>
            </div>
          )}

          {projects.map((project) => (
            <div
              key={project.project_id}
              className="rounded-2xl border border-gray-200 bg-white p-5 shadow-theme-xs dark:border-gray-800 dark:bg-gray-900 lg:p-6"
            >
              <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="space-y-1">
                  <div className="mb-1 flex flex-wrap items-center gap-2">
                    {project.category && (
                      <span className="inline-block rounded-full bg-brand-50 px-2.5 py-0.5 text-xs font-medium text-brand-500 dark:bg-brand-500/10">
                        {project.category}
                      </span>
                    )}
                    <span className="font-mono text-[11px] text-gray-400">
                      {project.project_id}
                    </span>
                  </div>
                  <h4 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                    {project.name}
                  </h4>
                  <p className="text-sm text-gray-500 dark:text-gray-400">
                    {project.description || "설명 없음"}
                  </p>
                  <p className="text-xs text-gray-400 dark:text-gray-500">
                    서브넷 {project.subnet_count}건 · 규칙 {project.rule_count}건
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <span className="rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-700 dark:bg-gray-800 dark:text-gray-300">
                    {project.status}
                  </span>
                  {/* Add Agent — 배포 카드를 이 행 아래에 펼칩니다. */}
                  <button
                    type="button"
                    onClick={() =>
                      setDeployingProjectId((current) =>
                        current === project.project_id ? null : project.project_id,
                      )
                    }
                    title="이 프로젝트에 Agent(Prober)를 추가합니다"
                    className={
                      deployingProjectId === project.project_id
                        ? "rounded-lg bg-brand-500 px-3.5 py-2 text-sm font-medium text-white hover:bg-brand-600"
                        : "rounded-lg border border-brand-500 bg-brand-50 px-3.5 py-2 text-sm font-medium text-brand-600 hover:bg-brand-100 dark:bg-brand-500/10 dark:text-brand-400 dark:hover:bg-brand-500/20"
                    }
                  >
                    {deployingProjectId === project.project_id ? "Close" : "Add Agent"}
                  </button>
                  <Link
                    to={`/project/editor/${encodeURIComponent(project.project_id)}`}
                    className="rounded-lg border border-gray-300 bg-white px-3.5 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
                  >
                    Manage
                  </Link>
                </div>
              </div>

              {/* 배포 카드 — Manage 옆 버튼으로 펼칩니다. 배포 화면
                  (ProjectCreation)의 Deploy & Download 와 같은 컴포넌트를 씁니다. */}
              {deployingProjectId === project.project_id && (
                <AgentDeployCard
                  projectId={project.project_id}
                  onClose={() => setDeployingProjectId(null)}
                  onImportOffline={() =>
                    navigate(`/project/create/subnet?project_id=${project.project_id}`)
                  }
                />
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Create Modal */}
      <Modal isOpen={isOpen} onClose={closeModal} className="max-w-[700px] m-4">
        <div className="no-scrollbar relative w-full max-w-[700px] overflow-y-auto rounded-3xl bg-white p-4 dark:bg-gray-900 lg:p-11">
          <div className="px-2 pr-14">
            <h4 className="mb-2 text-2xl font-semibold text-gray-800 dark:text-white/90">
              Create Project
            </h4>
            <p className="mb-6 text-sm text-gray-500 dark:text-gray-400 lg:mb-7">
              Fill in the details to create a new project.
            </p>
          </div>
          <form
            className="flex flex-col"
            onSubmit={(e) => {
              e.preventDefault();
              handleCreate();
            }}
          >
            <div className="custom-scrollbar h-[350px] space-y-5 overflow-y-auto px-2 pb-3">
              <div>
                <Label>Project Name</Label>
                <Input
                  type="text"
                  placeholder="Enter project name"
                  value={projectName}
                  onChange={(e) => {
                    setProjectName(e.target.value);
                    if (nameError) setNameError(null);
                  }}
                />
                {nameError && (
                  <p className="mt-1 text-xs text-error-500">{nameError}</p>
                )}
              </div>
              <div>
                <Label>Category</Label>
                <Input
                  type="text"
                  placeholder="e.g. Finance"
                  value={categoryName}
                  onChange={(e) => setCategoryName(e.target.value)}
                />
              </div>
              <div>
                <Label>Description</Label>
                <Input
                  type="text"
                  placeholder="Enter brief description"
                  value={projectDescription}
                  onChange={(e) => setProjectDescription(e.target.value)}
                />
              </div>
              {createAction.error && (
                <p className="text-xs text-error-500">{createAction.error}</p>
              )}
            </div>
            <div className="mt-6 flex items-center gap-3 px-2 lg:justify-end">
              <Button size="sm" variant="outline" onClick={closeModal}>
                Close
              </Button>
              <Button size="sm" type="submit" disabled={createAction.submitting}>
                {createAction.submitting ? "Creating..." : "Create Project"}
              </Button>
            </div>
          </form>
        </div>
      </Modal>
    </>
  );
}
