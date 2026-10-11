import PageMeta from "../../components/common/PageMeta";
import AgentList from "../../components/project/agent-element/AgentList";
import ProjectListCard from "../../components/dashboard/ProjectListCard";
import LatestTopologyCard from "../../components/dashboard/LatestTopologyCard";
import LogStatusCard from "../../components/dashboard/LogStatusCard";
import AgentTableCard from "../../components/dashboard/AgentTableCard";

export default function Home() {
  return (
    <>
      <PageMeta
        title="SonarValidator Dashboard"
        description="SonarValidator home dashboard: projects, latest topology, logs, agents"
      />
      <div className="space-y-6">
        {/* 상단 요약 메트릭 (Projects / Connected Agents) */}
        <AgentList />

        <div className="grid grid-cols-12 gap-6">
          {/* 현재 프로젝트 리스트 */}
          <div className="col-span-12 xl:col-span-4">
            <ProjectListCard />
          </div>

          {/* 가장 최근 프로젝트 토폴로지 */}
          <div className="col-span-12 xl:col-span-8">
            <LatestTopologyCard />
          </div>

          {/* 로그 현황 */}
          <div className="col-span-12">
            <LogStatusCard />
          </div>

          {/* Agent 리스트 (id, 이름, IP, 대역대, 장비 타입) */}
          <div className="col-span-12">
            <AgentTableCard />
          </div>
        </div>
      </div>
    </>
  );
}
