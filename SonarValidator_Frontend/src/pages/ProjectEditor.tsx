import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import Badge from "../components/ui/badge/Badge";
import Button from "../components/ui/button/Button";
import Input from "../components/form/input/InputField";
import Label from "../components/form/Label";
import ProjectServerSettings from "../components/project/ProjectServerSettings";
import DiscoveredVlans from "../components/project/DiscoveredVlans";
import { getProjectForEditing } from "../lib/api/discovery";
import SubnetEditor from "../components/project/SubnetEditor";
import RuleEditor from "../components/project/RuleEditor";
import ViolationSummary from "../components/project/ViolationSummary";
import { useApi } from "../hooks/useApi";
import { useApiAction } from "../hooks/useApiAction";
import {
  updateProject,
  validateDraft,
  getForbiddenPairs,
} from "../lib/api/projects";
import { listAgentOverview } from "../lib/api";
import { pushPolicy } from "../lib/api";
import type {
  ApiRule,
  ApiSubnet,
  ApiValidationReport,
  SubnetClass,
} from "../lib/api/types";
import { ZONE_CLASSES } from "../lib/policy/zones";

export default function ProjectEditor() {
  const { projectId: routeProjectId } = useParams<{ projectId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  // 경로 파라미터 또는 쿼리스트링 어느 쪽으로도 프로젝트를 받습니다.
  // (기존 마법사가 ?project_id=... 를 쓰고 있어 호환을 유지합니다)
  const projectId = routeProjectId ?? searchParams.get("project_id") ?? "";

  // 서버 상태 로드
  const { data, loading, error, offline, reload } = useApi(
    () => getProjectForEditing(projectId),
    [projectId],
  );

  const forbiddenPairs = useApi(() => getForbiddenPairs(projectId), [projectId]);
  const agentOverview = useApi(() => listAgentOverview(projectId), [projectId]);

  // 편집 상태 (서버 응답을 로컬 draft 로 복사해 편집)
  const [name, setName] = useState("");
  const [category, setCategory] = useState("");
  const [description, setDescription] = useState("");
  const [status, setStatus] = useState("");
  const [subnets, setSubnets] = useState<ApiSubnet[]>([]);
  const [rules, setRules] = useState<ApiRule[]>([]);
  const [report, setReport] = useState<ApiValidationReport | null>(null);
  const [dirty, setDirty] = useState(false);

  // 서버 응답이 도착하면 편집 상태를 초기화합니다.
  useEffect(() => {
    if (!data) return;
    setName(data.name ?? "");
    setCategory(data.category ?? "");
    setDescription(data.description ?? "");
    setStatus(data.status ?? "");
    setSubnets(data.subnets ?? []);
    setRules(data.rules ?? []);
    setDirty(false);
    setReport(null);
  }, [data]);

  // 액션
  const requestBody = useCallback(() => ({
    subnets: subnets.map((subnet) => ({
      id: subnet.id,
      cidr: subnet.cidr,
      vlan_id: subnet.vlan_id,
      network_role: subnet.network_role,
      subnet_class: subnet.subnet_class,
      name: subnet.name,
      agent_id: subnet.agent_id,
      // 사람이 등급을 지정했으므로 확인됨으로 표시합니다.
      manually_edited: subnet.manually_edited,
      // ⚠️ 허용 목록을 반드시 실어 보냅니다. 빠뜨리면 서버가 "제한 없음" 으로
      //    저장해, 운영자가 지정한 차단이 저장 한 번에 사라집니다.
      allowed_peers: subnet.allowed_peers ?? [],
    })),
    rules: rules.map((rule) => ({
      id: rule.id,
      src: rule.src,
      dst: rule.dst,
      port: rule.port,
      protocol: rule.protocol,
      origin: rule.origin,
      enabled: rule.enabled,
      note: rule.note,
    })),
  }), [subnets, rules]);

  const saveAction = useApiAction(() =>
    updateProject(projectId, {
      name,
      category,
      description,
      status,
      ...requestBody(),
    }),
  );

  // ⚠️ 저장된 상태가 아니라 현재 초안을 검증합니다.
  //    그래야 등급을 바꾸자마자 위반(예: 기밀망의 인터넷 노출)이 바로 뜹니다.
  const validateAction = useApiAction(() => validateDraft(requestBody()));
  const pushAction = useApiAction(() => pushPolicy(projectId, false));

  const handleSave = useCallback(async () => {
    const saved = await saveAction.run();
    if (!saved) return;
    setDirty(false);
    // 저장이 성공했으므로 이제 서버 상태 기준으로 검증할 수 있습니다.
    const validation = await validateAction.run();
    if (!validation) return;
    setReport(validation);
    // 위반이 0건일 때만 목록으로 리다이렉트합니다.
    if (validation.compliant) {
      navigate("/project");
    }
  }, [saveAction, validateAction, navigate]);

  const handleValidate = useCallback(async () => {
    const validation = await validateAction.run();
    if (validation) setReport(validation);
  }, [validateAction]);

  const handlePush = useCallback(async () => {
    const result = await pushAction.run();
    if (result) {
      // 푸시 결과를 알리기 위해 검증 상태를 다시 읽습니다.
      reload();
    }
  }, [pushAction, reload]);

  // 편집 핸들러
  const updateSubnetClass = (subnetId: string, subnetClass: SubnetClass | null) => {
    setSubnets((prev) =>
      prev.map((subnet) =>
        subnet.id === subnetId
          ? { ...subnet, subnet_class: subnetClass, manually_edited: true }
          : subnet,
      ),
    );
    setDirty(true);
  };

  const updateSubnetCidr = (subnetId: string, cidr: string) => {
    setSubnets((prev) =>
      prev.map((subnet) =>
        subnet.id === subnetId
          ? { ...subnet, cidr, manually_edited: true }
          : subnet,
      ),
    );
    setDirty(true);
  };

  const updateSubnetAgent = (subnetId: string, agentId: string | null) => {
    setSubnets((prev) =>
      prev.map((subnet) =>
        subnet.id === subnetId
          ? { ...subnet, agent_id: agentId, manually_edited: true }
          : subnet,
      ),
    );
    setDirty(true);
  };

  const updateSubnetAllowedPeers = (subnetId: string, peers: string[]) => {
    setSubnets((prev) =>
      prev.map((subnet) =>
        subnet.id === subnetId
          ? { ...subnet, allowed_peers: peers, manually_edited: true }
          : subnet,
      ),
    );
    setDirty(true);
  };

  const removeSubnet = (subnetId: string) => {
    setSubnets((prev) => prev.filter((subnet) => subnet.id !== subnetId));
    // 삭제된 서브넷을 참조하는 규칙도 함께 정리합니다.
    // (고아 규칙을 남기면 검증에서 MINOR 위반으로 잡힙니다)
    setRules((prev) =>
      prev.filter((rule) => rule.src !== subnetId && rule.dst !== subnetId),
    );
    setDirty(true);
  };

  const addSubnet = () => {
    setSubnets((prev) => {



      const usedCidrs = new Set(prev.map((subnet) => subnet.cidr.split("/")[0]));
      const usedIds = new Set(prev.map((subnet) => subnet.id));

      let thirdOctet = 0;
      while (usedCidrs.has(`10.0.${thirdOctet}.0`) && thirdOctet < 255) {
        thirdOctet++;
      }
      let sequence = 1;
      while (usedIds.has(`Subnet-${String(sequence).padStart(4, "0")}`)) {
        sequence++;
      }

      return [
        ...prev,
        {
          id: `Subnet-${String(sequence).padStart(4, "0")}`,
          cidr: `10.0.${thirdOctet}.0/24`,
          subnet_class: null,
          name: null,
          agent_id: null,
          manually_edited: true,
        },
      ];
    });
    setDirty(true);
  };

  const updateRule = (ruleId: string, patch: Partial<ApiRule>) => {
    setRules((prev) =>
      prev.map((rule) => (rule.id === ruleId ? { ...rule, ...patch } : rule)),
    );
    setDirty(true);
  };

  const removeRule = (ruleId: string) => {
    setRules((prev) => prev.filter((rule) => rule.id !== ruleId));
    setDirty(true);
  };




  const addRule = () => {
    setRules((prev) => {
      const sorted = [...subnets].sort(
        (a, b) =>
          (ZONE_CLASSES.find((z) => z.value === b.subnet_class)?.level ?? 0) -
          (ZONE_CLASSES.find((z) => z.value === a.subnet_class)?.level ?? 0),
      );
      const usedIds = new Set(prev.map((rule) => rule.id));
      let sequence = 1;
      while (usedIds.has(`Rule-${String(sequence).padStart(4, "0")}`)) {
        sequence++;
      }
      return [
        ...prev,
        {
          id: `Rule-${String(sequence).padStart(4, "0")}`,
          src: sorted[0]?.id ?? "",
          dst: sorted[1]?.id ?? sorted[0]?.id ?? "",
          port: null,
          protocol: "tcp",
          origin: "MANUAL" as const,
          enabled: true,
          note: null,
        },
      ];
    });
    setDirty(true);
  };

  // 위반이 있는 서브넷/규칙 식별자 집합 (표 강조용)
  const violatingSubnetIds = useMemo(() => {
    const ids = new Set<string>();
    if (!report) return ids;
    for (const violation of report.violations) {
      if (violation.src_subnet) ids.add(violation.src_subnet);
      if (violation.dst_subnet) ids.add(violation.dst_subnet);
    }
    return ids;
  }, [report]);

  // 렌더
  if (!projectId) {
    return (
      <>
        <PageBreadcrumb pageTitle="Project Editor" parentName="Project" parentPath="/project" />
        <div className="rounded-2xl border border-warning-200 bg-warning-50 p-6 dark:border-warning-500/30 dark:bg-warning-500/10">
          <p className="text-sm text-gray-700 dark:text-gray-200">
            프로젝트를 지정하지 않았습니다. 프로젝트 목록에서 편집할 프로젝트를
            선택하세요.
          </p>
          <Button className="mt-4" size="sm" onClick={() => navigate("/project")}>
            프로젝트 목록으로
          </Button>
        </div>
      </>
    );
  }

  return (
    <>
      <PageMeta
        title="Project Editor | SonarValidator"
        description="프로젝트 네트워크 서브넷 등급과 연결 규칙 편집, 망분리 검증"
      />
      <PageBreadcrumb pageTitle="Project Editor" parentName="Project" parentPath="/project" />

      <div className="space-y-6">
        {/* 로딩 / 오류 상태 */}
        {loading && (
          <div className="rounded-2xl border border-gray-200 bg-white p-6 text-sm text-gray-500 dark:border-gray-800 dark:bg-white/[0.03] dark:text-gray-400">
            프로젝트를 불러오는 중...
          </div>
        )}

        {error && (
          <div className="rounded-2xl border border-error-200 bg-error-50 p-6 dark:border-error-500/30 dark:bg-error-500/10">
            <p className="text-sm font-medium text-gray-800 dark:text-white/90">
              {offline ? "백엔드에 연결할 수 없습니다" : "프로젝트를 불러오지 못했습니다"}
            </p>
            <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">{error}</p>
            {offline && (
              <p className="mt-2 rounded bg-white/60 p-2 font-mono text-[11px] text-gray-700 dark:bg-black/20 dark:text-gray-200">
                cd SonarValidator_Backend && ./mvnw spring-boot:run
              </p>
            )}
            <Button className="mt-4" size="sm" variant="outline" onClick={reload}>
              다시 시도
            </Button>
          </div>
        )}

        {data && (
          <>
            {/* 프로젝트 메타데이터 */}
            <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
              <div className="mb-5 flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 pb-4 dark:border-gray-800">
                <div className="flex items-center gap-2">
                  <span className="inline-block rounded-lg bg-brand-50 px-3 py-1.5 text-sm font-semibold text-brand-600 dark:bg-brand-500/10 dark:text-brand-400">
                    Project Editor
                  </span>
                  <Badge size="sm" color="light">
                    {data.project_id}
                  </Badge>
                  {dirty && (
                    <Badge size="sm" color="warning">
                      저장되지 않은 변경
                    </Badge>
                  )}
                  {data.draft && (
                    <Badge size="sm" color="info">
                      자동 수집 초안
                    </Badge>
                  )}
                </div>
                <div className="flex gap-2">
                  <Button size="sm" variant="outline" onClick={() => navigate("/project")}>
                    목록
                  </Button>
                  <Button size="sm" onClick={handleSave} disabled={saveAction.submitting}>
                    {saveAction.submitting ? "저장 중..." : "저장 및 검증"}
                  </Button>
                </div>
              </div>

              {data.draft_note && (
                <p className="mb-4 rounded-lg bg-blue-light-50 px-3 py-2 text-xs text-blue-light-700 dark:bg-blue-light-500/10 dark:text-blue-light-300">
                  {data.draft_note}
                </p>
              )}

              <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                <div>
                  <Label>Project Name</Label>
                  <Input
                    type="text"
                    value={name}
                    placeholder="프로젝트 이름"
                    onChange={(e) => {
                      setName(e.target.value);
                      setDirty(true);
                    }}
                  />
                </div>
                <div>
                  <Label>Category</Label>
                  <Input
                    type="text"
                    value={category}
                    placeholder="예: Finance"
                    onChange={(e) => {
                      setCategory(e.target.value);
                      setDirty(true);
                    }}
                  />
                </div>
                <div>
                  <Label>Description</Label>
                  <Input
                    type="text"
                    value={description}
                    placeholder="간단한 설명"
                    onChange={(e) => {
                      setDescription(e.target.value);
                      setDirty(true);
                    }}
                  />
                </div>
                <div>
                  <Label>Status</Label>
                  <select
                    value={status}
                    onChange={(e) => {
                      setStatus(e.target.value);
                      setDirty(true);
                    }}
                    className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 focus:border-brand-500 focus:outline-none dark:border-gray-700 dark:text-white"
                  >
                    <option value="Planning">Planning</option>
                    <option value="In Progress">In Progress</option>
                    <option value="Completed">Completed</option>
                  </select>
                </div>
              </div>
              <ProjectServerSettings projectId={projectId} />
            </div>

            <DiscoveredVlans projectId={projectId} />

            {/* 검증 결과 */}
            <ViolationSummary
              report={report}
              validating={validateAction.submitting}
              onValidate={handleValidate}
              onSave={handleSave}
              saving={saveAction.submitting}
              onPush={handlePush}
              pushing={pushAction.submitting}
            />

            {/* 액션 오류 안내 */}
            {(saveAction.error || validateAction.error || pushAction.error) && (
              <div className="rounded-xl border border-error-200 bg-error-50 p-3 text-xs text-error-700 dark:border-error-500/30 dark:bg-error-500/10 dark:text-error-300">
                {saveAction.error ?? validateAction.error ?? pushAction.error}
              </div>
            )}

            {/* 등급을 건너뛰는 조합 안내 (정적 정보) */}
            {forbiddenPairs.data && forbiddenPairs.data.total > 0 && (
              <div className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800/50">
                <h5 className="mb-2 text-sm font-semibold text-gray-800 dark:text-white/90">
                  금지된 연결 조합 ({forbiddenPairs.data.total}쌍)
                </h5>
                <p className="mb-3 text-xs text-gray-500 dark:text-gray-400">
                  {forbiddenPairs.data.rule ??
                    "등급 차이가 2 이상인 서브넷끼리는 직접 연결할 수 없습니다."}
                </p>
                <div className="flex flex-wrap gap-2">
                  {forbiddenPairs.data.pairs.map((pair) => (
                    <span
                      key={`${pair.src}-${pair.dst}`}
                      className="rounded bg-error-50 px-2 py-1 font-mono text-[11px] text-error-700 dark:bg-error-500/15 dark:text-error-300"
                    >
                      {pair.src} → {pair.dst}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* 편집 표: 서브넷 + 규칙 */}
            <div className="grid grid-cols-1 gap-6">
              <SubnetEditor
                subnets={subnets}
                onRoleChange={(id, role) => { setSubnets(items => items.map(item => item.id === id ? { ...item, network_role: role, manually_edited: true } : item)); setDirty(true); }}
          onChange={updateSubnetClass}
                onNameChange={(id, name) => { setSubnets(prev => prev.map(s => s.id === id ? {...s, name, manually_edited: true} : s)); setDirty(true); }}
              onCidrChange={updateSubnetCidr}
                onAgentChange={updateSubnetAgent}
                onAllowedPeersChange={updateSubnetAllowedPeers}
                agents={(agentOverview.data?.agents ?? []).map((agent) => ({
                  agent_id: agent.agent_id,
                  label: agent.agent_id,
                }))}
                onRemove={removeSubnet}
                onAdd={addSubnet}
                violatingSubnetIds={violatingSubnetIds}
              />
              <RuleEditor
                rules={rules}
                subnets={subnets}
                onChange={updateRule}
                onRemove={removeRule}
                onAdd={addRule}
                violations={report?.violations ?? []}
              />
            </div>

            {/* 위반 상세 표: BDD 반례까지 함께 */}
            {report && report.violations.length > 0 && (
              <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
                <h5 className="mb-3 text-sm font-semibold text-gray-800 dark:text-white/90">
                  위반 상세 ({report.violations.length}건)
                </h5>
                <div className="overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-700">
                  <table className="min-w-full text-left text-xs">
                    <thead className="bg-gray-50 text-gray-700 dark:bg-gray-700 dark:text-gray-300">
                      <tr>
                        <th className="border-b p-2 font-medium dark:border-gray-600">심각도</th>
                        <th className="border-b p-2 font-medium dark:border-gray-600">Rule</th>
                        <th className="border-b p-2 font-medium dark:border-gray-600">경로</th>
                        <th className="border-b p-2 font-medium dark:border-gray-600">사유</th>
                        <th className="border-b p-2 font-medium dark:border-gray-600">
                          반례 패킷
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200 text-gray-600 dark:divide-gray-700 dark:text-gray-300">
                      {report.violations.map((violation, index) => (
                        <tr key={`${violation.rule_id}-${index}`}>
                          <td className="p-2">
                            <Badge
                              size="sm"
                              color={
                                violation.severity === "CRITICAL"
                                  ? "error"
                                  : violation.severity === "MAJOR"
                                    ? "warning"
                                    : "info"
                              }
                            >
                              {violation.severity}
                            </Badge>
                          </td>
                          <td className="p-2 font-mono">{violation.rule_id}</td>
                          <td className="p-2 font-mono">
                            {violation.src_class ?? "?"} → {violation.dst_class ?? "?"}
                          </td>
                          <td className="p-2">{violation.reason}</td>
                          <td className="p-2 font-mono text-[11px]">
                            {violation.sampled_packet && violation.sampled_packet !== "- -> -"
                              ? violation.sampled_packet
                              : "—"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <p className="mt-2 text-[11px] text-gray-400">
                  반례 패킷은 BDD 위반 집합에서 추출한 실제 예시입니다. 이 주소·포트
                  조합이 허용 규칙에 포함되어 있으므로 정책 위반입니다.
                </p>
              </div>
            )}

            {/* 푸시 결과 */}
            {pushAction.result && (
              <div className="rounded-xl border border-gray-200 bg-white p-4 text-xs dark:border-gray-700 dark:bg-gray-800/50">
                <h5 className="mb-2 text-sm font-semibold text-gray-800 dark:text-white/90">
                  정책 푸시 결과
                </h5>
                <pre className="overflow-x-auto rounded bg-gray-50 p-3 font-mono text-[11px] text-gray-700 dark:bg-gray-900 dark:text-gray-200">
                  {JSON.stringify(pushAction.result, null, 2)}
                </pre>
              </div>
            )}
          </>
        )}
      </div>
    </>
  );
}
