import React from "react";

export default function DetectedNetworkNodes() {
  // 테스트를 위한 임시(Dummy) 데이터
  const detectedNodes = {
    routers: [
      { id: 1, ip: "192.168.10.1", name: "RTR-Auto-001", status: "online" },
      { id: 2, ip: "192.168.10.2", name: "RTR-Auto-002", status: "offline" },
    ],
    switches: [
      { id: 3, ip: "192.168.20.10", name: "SW-Auto-001", status: "online" },
      { id: 4, ip: "192.168.20.11", name: "SW-Auto-002", status: "online" },
      { id: 5, ip: "192.168.20.12", name: "SW-Auto-003", status: "offline" },
    ],
    firewalls: [
      { id: 6, ip: "10.0.0.254", name: "FW-Auto-001", status: "online" },
    ],
    vms: [
      { id: 7, ip: "172.16.0.100", name: "WIN-VM-001", status: "online" },
      { id: 8, ip: "172.16.0.101", name: "LINUX-VM-002", status: "online" },
    ],
  };

  // 노드 리스트를 렌더링하는 공통 함수 (TailAdmin 리스트 스타일 적용)
  const renderNodeList = (nodes) => {
    if (nodes.length === 0) {
      return (
        <div className="flex h-32 items-center justify-center text-sm text-gray-500 dark:text-gray-400">
          No nodes detected
        </div>
      );
    }

    return (
      <div className="flex flex-col">
        {nodes.map((node) => (
          <div
            key={node.id}
            className="flex items-center justify-between border-b border-stroke py-3 last:border-b-0 dark:border-strokedark"
          >
            <div className="flex flex-col">
              <span className="font-medium text-black dark:text-white">
                {node.ip}
              </span>
              <span className="text-sm text-gray-500 dark:text-gray-400">
                {node.name}
              </span>
            </div>
            {/* 상태 표시 인디케이터 (TailAdmin의 meta 색상 또는 표준 색상 활용) */}
            <div className="flex items-center justify-center">
              <span
                className={`h-3 w-3 rounded-full ${
                  node.status === "online" ? "bg-meta-3 bg-green-500" : "bg-meta-1 bg-red-500"
                }`}
                title={node.status === "online" ? "Online" : "Offline"}
              ></span>
            </div>
          </div>
        ))}
      </div>
    );
  };

  return (
    <div className="mt-6">
      <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-title-md2 font-semibold text-black dark:text-white">
          View Detected Network Nodes
        </h2>
      </div>

      {/* 4분할 그리드 레이아웃 */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 md:gap-6 xl:grid-cols-4 2xl:gap-7.5">
        
        {/* 1. Cisco Router 카드 */}
        <div className="rounded-sm border border-stroke bg-white shadow-default dark:border-strokedark dark:bg-boxdark">
          <div className="border-b border-stroke py-4 px-6.5 dark:border-strokedark">
            <h3 className="font-medium text-black dark:text-white">
              Cisco Router
            </h3>
          </div>
          <div className="p-6.5 max-h-[350px] overflow-y-auto custom-scrollbar">
            {renderNodeList(detectedNodes.routers)}
          </div>
        </div>

        {/* 2. Arista Switch 카드 */}
        <div className="rounded-sm border border-stroke bg-white shadow-default dark:border-strokedark dark:bg-boxdark">
          <div className="border-b border-stroke py-4 px-6.5 dark:border-strokedark">
            <h3 className="font-medium text-black dark:text-white">
              Arista Switch
            </h3>
          </div>
          <div className="p-6.5 max-h-[350px] overflow-y-auto custom-scrollbar">
            {renderNodeList(detectedNodes.switches)}
          </div>
        </div>

        {/* 3. Fortigate Firewall 카드 */}
        <div className="rounded-sm border border-stroke bg-white shadow-default dark:border-strokedark dark:bg-boxdark">
          <div className="border-b border-stroke py-4 px-6.5 dark:border-strokedark">
            <h3 className="font-medium text-black dark:text-white">
              Fortigate Firewall
            </h3>
          </div>
          <div className="p-6.5 max-h-[350px] overflow-y-auto custom-scrollbar">
            {renderNodeList(detectedNodes.firewalls)}
          </div>
        </div>

        {/* 4. Windows / Linux VM 카드 */}
        <div className="rounded-sm border border-stroke bg-white shadow-default dark:border-strokedark dark:bg-boxdark">
          <div className="border-b border-stroke py-4 px-6.5 dark:border-strokedark">
            <h3 className="font-medium text-black dark:text-white">
              Windows / Linux VM
            </h3>
          </div>
          <div className="p-6.5 max-h-[350px] overflow-y-auto custom-scrollbar">
            {renderNodeList(detectedNodes.vms)}
          </div>
        </div>

      </div>
    </div>
  );
}