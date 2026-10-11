import { createContext, useContext } from "react";

/**
 * 사이드바 컨텍스트의 값과 훅입니다.
 *
 * <h2>⚠️ 왜 Provider 파일과 분리했는가</h2>
 * Fast Refresh 는 한 파일이 <b>컴포넌트만</b> export 할 때 동작합니다.
 * {@code SidebarProvider} 와 {@code useSidebar} 를 같은 파일에 두면 훅 export
 * 때문에 핫 리로드가 전체 새로고침으로 떨어집니다.
 */

export type SidebarContextType = {
  isExpanded: boolean;
  isMobileOpen: boolean;
  isHovered: boolean;
  activeItem: string | null;
  openSubmenu: string | null;
  toggleSidebar: () => void;
  toggleMobileSidebar: () => void;
  setIsHovered: (isHovered: boolean) => void;
  setActiveItem: (item: string | null) => void;
  toggleSubmenu: (item: string) => void;
};

export const SidebarContext = createContext<SidebarContextType | undefined>(undefined);

/**
 * 사이드바 컨텍스트를 사용합니다.
 *
 * @returns 사이드바 상태와 동작
 * @throws SidebarProvider 밖에서 호출한 경우
 */
export const useSidebar = () => {
  const context = useContext(SidebarContext);
  if (!context) {
    throw new Error("useSidebar must be used within a SidebarProvider");
  }
  return context;
};
