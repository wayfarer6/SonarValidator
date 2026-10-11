import { createContext, useContext } from "react";

/**
 * 테마 컨텍스트의 값과 훅입니다.
 *
 * <h2>⚠️ 왜 Provider 파일과 분리했는가</h2>
 * Fast Refresh(개발 중 상태 유지)는 한 파일이 <b>컴포넌트만</b> export 할 때
 * 동작합니다. {@code ThemeProvider} 와 {@code useTheme} 를 같은 파일에 두면
 * 훅 export 때문에 핫 리로드가 전체 새로고침으로 떨어집니다. 그래서 컨텍스트
 * 객체와 훅은 이 모듈에, Provider 컴포넌트는 {@code ThemeContext.tsx} 에 둡니다.
 */

export type Theme = "light" | "dark";

export type ThemeContextType = {
  theme: Theme;
  toggleTheme: () => void;
};

export const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

/**
 * 테마 컨텍스트를 사용합니다.
 *
 * @returns 현재 테마와 토글 함수
 * @throws ThemeProvider 밖에서 호출한 경우
 */
export const useTheme = () => {
  const context = useContext(ThemeContext);
  if (context === undefined) {
    throw new Error("useTheme must be used within a ThemeProvider");
  }
  return context;
};
