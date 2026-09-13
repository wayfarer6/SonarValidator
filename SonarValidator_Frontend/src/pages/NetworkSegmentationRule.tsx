import { useState } from "react";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import NetworkTopologyMermaid from "./NetworkTopologyMermaid"; // 위에서 만든 컴포넌트

export default function NetworkSegmentationRule() {
  const [messageType] = useState<"error" | "success" | "warning">("error");

  return (
    <>
      <PageMeta
        title="Add Network Segmentation Rule | TailAdmin"
        description="Network Segmentation Rule Configuration with CSO (Confidential, Sensitive, Open)"
      />
      
      <PageBreadcrumb 
        pageTitle="Add Network Segmentation Rule" 
        parentName="Project" 
        parentPath="/project" 
      />

      <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6 space-y-6">
        
        {/* 상단 파트: 토폴로지 (Mermaid) 및 정책 설정 테이블 */}
        <div className="flex flex-col xl:flex-row gap-6">
          
          {/* 왼쪽: Mermaid 기반 CSO 토폴로지 영역 */}
          <div className="flex-1 rounded-xl border border-gray-200 bg-gray-50/50 p-5 dark:border-gray-700 dark:bg-gray-800/40">
            <div className="flex items-center justify-between mb-3">
              <h4 className="text-sm font-semibold text-gray-700 dark:text-gray-300">
                Network Topology (CSO Security Zones)
              </h4>
              <div className="flex gap-2 text-[11px]">
                <span className="px-2 py-0.5 rounded bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300">Open</span>
                <span className="px-2 py-0.5 rounded bg-orange-100 text-orange-800 dark:bg-orange-900/40 dark:text-orange-300">Sensitive</span>
                <span className="px-2 py-0.5 rounded bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-300">Confidential</span>
              </div>
            </div>

            {/* Mermaid 다이어그램 렌더링 컴포넌트 삽입 */}
            <NetworkTopologyMermaid />
          </div>

          {/* 오른쪽: 정책 설정 및 자동 생성 규칙 테이블 영역 */}
          <div className="w-full xl:w-[420px] space-y-5">
            
            {/* 정책 설정 테이블 */}
            <div className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800/50 shadow-theme-xs">
              <h5 className="mb-3 text-sm font-semibold text-gray-800 dark:text-white/90">
                정책 설정 (Rule Configuration)
              </h5>
              
              <div className="overflow-hidden border border-gray-200 rounded-lg dark:border-gray-700">
                <table className="w-full text-left text-xs">
                  <thead className="bg-gray-50 dark:bg-gray-700 text-gray-700 dark:text-gray-300">
                    <tr>
                      <th className="p-2 border-b dark:border-gray-600 font-medium">Rule ID</th>
                      <th className="p-2 border-b dark:border-gray-600 font-medium">SRC Subnet</th>
                      <th className="p-2 border-b dark:border-gray-600 font-medium">DST Subnet</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200 dark:divide-gray-700 text-gray-600 dark:text-gray-300">
                    <tr>
                      <td className="p-2 font-mono">Rule-XXXX</td>
                      <td className="p-2">SRC = Confidential</td>
                      <td className="p-2">DST = Sensitive</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            {/* Prober에서 생성한 자동 규칙 테이블 */}
            <div className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800/50 shadow-theme-xs">
              <h5 className="mb-3 text-sm font-semibold text-gray-800 dark:text-white/90">
                Prober에서 생성한 자동 규칙
              </h5>
              
              <div className="overflow-hidden border border-gray-200 rounded-lg dark:border-gray-700">
                <table className="w-full text-left text-xs">
                  <thead className="bg-gray-50 dark:bg-gray-700 text-gray-700 dark:text-gray-300">
                    <tr>
                      <th className="p-2 border-b dark:border-gray-600 font-medium">Rule ID</th>
                      <th className="p-2 border-b dark:border-gray-600 font-medium">SRC Subnet</th>
                      <th className="p-2 border-b dark:border-gray-600 font-medium">DST Subnet</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200 dark:divide-gray-700 text-gray-600 dark:text-gray-300">
                    <tr>
                      <td className="p-2 font-mono">Rule-XXXX</td>
                      <td className="p-2">SRC = Sensitive</td>
                      <td className="p-2">DST = Open</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

          </div>

        </div>

        {/* 하단 파트: 상태 메시지 아웃풋 영역 */}
        <div className="border-t border-gray-200 pt-5 dark:border-gray-700">
          <span className="text-xs font-bold text-gray-500 dark:text-gray-400 block mb-3">
            Message Type / Status Feedback
          </span>

          <div className="space-y-3">
            {messageType === "error" && (
              <div className="flex items-start gap-3 p-4 rounded-xl border border-red-200 bg-red-50/70 text-red-700 dark:bg-red-950/30 dark:border-red-900 dark:text-red-300 transition-all">
                <span className="text-xl">🚨</span>
                <div>
                  <h6 className="text-sm font-semibold">오류: 네트워크 연결 제한</h6>
                  <p className="text-xs mt-0.5">Confidential Zone과 Open Zone 간의 직접 연결은 논리적으로 허용되지 않습니다.</p>
                </div>
              </div>
            )}

            <div className="flex items-start gap-3 p-4 rounded-xl border border-emerald-200 bg-emerald-50/70 text-emerald-700 dark:bg-emerald-950/30 dark:border-emerald-900 dark:text-emerald-300 transition-all">
              <span className="text-xl">✅</span>
              <div>
                <h6 className="text-sm font-semibold">성공: 규칙 설정 완료</h6>
                <p className="text-xs mt-0.5">지정된 규칙이 설정 되었습니다. CSO 보안 정책을 준수합니다.</p>
              </div>
            </div>

            <div className="flex items-start gap-3 p-4 rounded-xl border border-amber-200 bg-amber-50/70 text-amber-700 dark:bg-amber-950/30 dark:border-amber-900 dark:text-amber-300 transition-all">
              <span className="text-xl">⚠️</span>
              <div>
                <h6 className="text-sm font-semibold">경고: 포트 설정 확인</h6>
                <p className="text-xs mt-0.5">지정된 규칙이 설정되었습니다. Subnet 간에 허용된 포트가 없습니다.</p>
              </div>
            </div>
          </div>
        </div>

      </div>
    </>
  );
}