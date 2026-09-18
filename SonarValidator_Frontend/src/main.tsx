import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import "swiper/swiper-bundle.css";
import "flatpickr/dist/flatpickr.css";
import App from "./App.tsx";
import { AppWrapper } from "./components/common/PageMeta.tsx";
import { ThemeProvider } from "./context/ThemeContext.tsx";
import { ProjectWizardProvider } from "./context/ProjectWizardContext.tsx";
// AuthProvider 가 있어야 App 의 세션 확인(useAuth)이 동작합니다.
import { AuthProvider } from "./context/AuthContext.tsx";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ThemeProvider>
      <AppWrapper>
        <AuthProvider>
          <ProjectWizardProvider>
            <App />
          </ProjectWizardProvider>
        </AuthProvider>
      </AppWrapper>
    </ThemeProvider>
  </StrictMode>,
);
