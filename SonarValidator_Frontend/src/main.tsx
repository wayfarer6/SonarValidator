import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import "swiper/swiper-bundle.css";
import "flatpickr/dist/flatpickr.css";
import App from "./App.tsx";
import { AppWrapper } from "./components/common/PageMeta.tsx";
import { ThemeProvider } from "./context/ThemeContext.tsx";
// AuthProvider 가 있어야 App 의 세션 확인(useAuth)이 동작합니다.
import { AuthProvider } from "./context/AuthContext.tsx";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ThemeProvider>
      <AppWrapper>
        <AuthProvider>
          {/* ProjectWizardProvider 는 여기(main.tsx)에 두지 않습니다.
              이 위치는 <Router> 바깥이고, Provider 가 ?project_id= 를 읽어
              백엔드에서 서브넷을 가져와야 하기 때문입니다.
              (Router 는 App.tsx 안에 있습니다)
              → layout/AppLayout.tsx 에서 <Outlet/> 을 감쌉니다. */}
          <App />
        </AuthProvider>
      </AppWrapper>
    </ThemeProvider>
  </StrictMode>,
);
