import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import ApiConnectedNodesPanel from "../components/opnsense/ApiConnectedNodesPanel";

export default function ApiConnectedNodes() {
  return (
    <>
      <PageMeta
        title="API 연결 노드 | SonarValidator"
        description="REST API 로 직접 연결되는 네트워크 장비 목록"
      />
      <PageBreadcrumb pageTitle="API 연결 노드" />

      <div className="space-y-6">
        <ApiConnectedNodesPanel
          showSummary
          showCandidates
          title="API 연결 노드"
          description="프로버 없이 REST API 로 직접 연결되는 장비입니다. 여기서 등록·편집·연결 확인을 모두 처리하며, Agent 목록과는 별개입니다."
        />
      </div>
    </>
  );
}
