import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import "swiper/swiper-bundle.css";
import "flatpickr/dist/flatpickr.css";
import App from "./App.tsx";
import { AppWrapper } from "./components/common/PageMeta.tsx";
import { ThemeProvider } from "./context/ThemeContext.tsx";
import { CookiesProvider } from 'react-cookie'; // 👈 여기서 감싸기

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ThemeProvider>
      <AppWrapper>
         <CookiesProvider> 
        <App />
        </CookiesProvider>
      </AppWrapper>
    </ThemeProvider>
  </StrictMode>,
);
