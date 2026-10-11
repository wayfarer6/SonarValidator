/**
 * 이 파일은 삭제되었습니다 (mock 데이터 완전 제거).
 *
 * <h2>무슨 일이 있었나</h2>
 * 예전에는 화면을 먼저 만들고 서버를 나중에 붙이는 순서라, 프로젝트/에이전트/
 * 로그/변경이력을 이 파일의 상수로 채워 두었습니다. 화면은 그럴듯해 보였지만
 * 실제로는 아무것도 조회하지 않았습니다 — 프로젝트가 0개, 연결된 Agent 가
 * 0개여도 "3개", "30개" 가 표시되는 상태였습니다.
 *
 * 이제 모든 화면이 백엔드에서 실제 값을 받아옵니다.
 *
 * <h2>각 상수가 어디로 갔는지</h2>
 * <ul>
 *   <li>{@code MOCK_PROJECTS} → {@code GET /api/v1/projects}
 *       ({@code lib/api/projects.ts :: listProjects})</li>
 *   <li>{@code MOCK_AGENTS} → {@code GET /api/v1/agents} 와
 *       {@code GET /api/v1/network/discovered} 를 {@code lib/agentView.ts} 에서
 *       {@code agent_id} 로 결합</li>
 *   <li>{@code MOCK_LOGS} → {@code GET /api/v1/logs/summary} +
 *       {@code GET /api/v1/logs/list} ({@code lib/api/aiLogs.ts})</li>
 *   <li>{@code MOCK_COMPLIANCE_CHANGES} →
 *       {@code GET /api/v1/compliance/changes} ({@code listComplianceChanges})</li>
 *   <li>{@code buildTopologyChart} → {@code lib/topology/mermaid.ts ::
 *       topologyToMermaid} (서버가 준 실제 서브넷/규칙을 그림)</li>
 *   <li>{@code formatTimestamp} → {@code lib/agentView.ts}</li>
 * </ul>
 *
 * @deprecated 내용이 모두 제거되었습니다. import 하지 마세요.
 */
export {};