import { Link, useNavigate, useSearchParams } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import AgentDeployCard from "../components/project/AgentDeployCard";

export default function ProjectCreation() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const projectId = params.get("project_id");
  return <>
    <PageMeta title="Prober Deployment | SonarValidator" description="프로젝트 설정으로 Prober 다운로드" />
    <PageBreadcrumb pageTitle="Prober Deployment" parentName="Project" parentPath="/project" />
    {!projectId ? <div className="rounded-xl border border-gray-200 p-6 text-sm dark:border-gray-700">
      <p>프로젝트를 선택한 뒤 Prober를 다운로드하세요.</p>
      <Link to="/project" className="mt-3 inline-block text-brand-600 hover:underline">프로젝트 목록</Link>
    </div> : <>
      <p className="mb-4 text-sm text-gray-500">서버 주소와 포트는 프로젝트 편집에서 설정합니다. 장비 유형을 선택하고 Agent를 등록한 뒤 번들을 다운로드하세요.</p>
      <AgentDeployCard projectId={projectId} onClose={() => navigate(`/project/editor/${encodeURIComponent(projectId)}`)} />
      <Link to={`/project/create/ViewNodes?project_id=${encodeURIComponent(projectId)}`} className="mt-5 inline-block rounded-lg bg-brand-500 px-4 py-2 text-sm text-white">다음: 노드 확인</Link>
    </>}
  </>;
}
