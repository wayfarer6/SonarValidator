import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import ApiConnectedNodesPanel from "../components/opnsense/ApiConnectedNodesPanel";

/**
 * API 연결 노드 목록 화면입니다.
 *
 * <h2>Agent 목록과 무엇이 다른가</h2>
 * <ul>
 *   <li><b>Agent List</b> — 프로버가 설치되어 WebSocket 으로 붙는 장비.
 *       연결은 장비가 서버로 들어옵니다.</li>
 *   <li><b>API 연결 노드</b> — 프로버를 올릴 수 없어 <b>서버가 REST API 로
 *       직접 접속</b>하는 장비(예: OPNsense). Agent 목록에는 절대 나타나지
 *       않습니다.</li>
 * </ul>
 *
 * <p>두 종류를 같은 목록에 섞으면 "연결됨" 의 의미가 달라져 혼란스럽습니다.
 * (WebSocket 세션 vs API 자격증명) 그래서 화면을 분리하고, 등록·편집·연결
 * 확인을 이 화면에서 모두 처리합니다.
 *
 * <h2>미등록 후보까지 보여주는 이유</h2>
 * 장비가 이미 수집되어 있어도 API 자격증명이 없으면 조회할 수 없습니다.
 * 그 사실을 목록이 알려주지 않으면 운영자는 "왜 방화벽 정보가 비어 있지"
 * 로만 보게 됩니다.
 */
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
