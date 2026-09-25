import { SidebarProvider, useSidebar } from "../context/SidebarContext";
import { Outlet } from "react-router";
import AppHeader from "./AppHeader";
import Backdrop from "./Backdrop";
import AppSidebar from "./AppSidebar";
import { ProjectWizardProvider } from "../context/ProjectWizardContext";

const LayoutContent: React.FC = () => {
  const { isExpanded, isHovered, isMobileOpen } = useSidebar();

  return (
    <div className="min-h-screen xl:flex">
      <div>
        <AppSidebar />
        <Backdrop />
      </div>
      <div
        className={`flex-1 transition-all duration-300 ease-in-out ${
          isExpanded || isHovered ? "lg:ml-[290px]" : "lg:ml-[90px]"
        } ${isMobileOpen ? "ml-0" : ""}`}
      >
        <AppHeader />
        <div className="p-4 mx-auto max-w-(--breakpoint-2xl) md:p-6">
          {/* 마법사(/project/create/*) 화면들이 공유하는 서브넷/규칙 상태입니다.
              Router 안쪽에 있어야 ?project_id= 를 읽어 백엔드에서 가져올 수
              있습니다. (main.tsx 는 Router 바깥이었습니다)
              레이아웃이 다시 마운트되지 않으므로 화면 이동 중에도 상태가 유지됩니다. */}
          <ProjectWizardProvider>
            <Outlet />
          </ProjectWizardProvider>
        </div>
      </div>
    </div>
  );
};

const AppLayout: React.FC = () => {
  return (
    <SidebarProvider>
      <LayoutContent />
    </SidebarProvider>
  );
};

export default AppLayout;
