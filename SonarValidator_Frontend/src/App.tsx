import { BrowserRouter as Router, Routes, Route } from "react-router";
import SignIn from "./pages/AuthPages/SignIn";
import SignUp from "./pages/AuthPages/SignUp";
import NotFound from "./pages/OtherPage/NotFound";
import UserProfiles from "./pages/UserProfiles";
import Videos from "./pages/UiElements/Videos";
import Images from "./pages/UiElements/Images";
import Alerts from "./pages/UiElements/Alerts";
import Badges from "./pages/UiElements/Badges";
import Avatars from "./pages/UiElements/Avatars";
import Buttons from "./pages/UiElements/Buttons";
import LineChart from "./pages/Charts/LineChart";
import BarChart from "./pages/Charts/BarChart";
import Calendar from "./pages/Calendar";
import BasicTables from "./pages/Tables/BasicTables";
import FormElements from "./pages/Forms/FormElements";
import Blank from "./pages/Blank";
import AppLayout from "./layout/AppLayout";
import { ScrollToTop } from "./components/common/ScrollToTop";
import Home from "./pages/Dashboard/Home";
import { Navigate } from 'react-router-dom';
import { useAuth } from "./context/AuthContext";
import Project from "./pages/Project";
import ProjectCreation from "./pages/ProjectCreation";
import ProjectEditor from "./pages/ProjectEditor";
import Agent from "./pages/Agent";
import Compliance from "./pages/Compliance";
import DetectedNetworkNodes from "./pages/DetectedNetworkNodes";
import SubnetAdvanceConfiguration from "./pages/SubnetAdvanceConfiguration";
import NetworkSegmentationRule from "./pages/NetworkSegmentationRule";
import TopologyRulePreview from "./pages/TopologyRulePreview";
import ComplianceExporter from "./pages/ComplianceExporter";
import PolicyManagement from "./pages/ PolicyManagement";
import PolicyExporter from "./pages/PolicyExporter";
import NetwworkManagement from "./pages/NetworkManagement";

export default function App() {
  // 인증 상태는 서버 세션을 따릅니다. (기존 쿠키 방식은 검증 없이 통과했습니다)
  const { user, initializing } = useAuth();
  const signedIn = user !== null;

  // 세션 확인이 끝나기 전에 라우팅하면 로그인 화면이 잠깐 보였다가 바뀝니다.
  if (initializing) {
    return (
      <div className="flex h-screen items-center justify-center bg-white dark:bg-gray-900">
        <div className="flex flex-col items-center gap-3">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-brand-500 border-t-transparent" />
          <span className="text-sm text-gray-500 dark:text-gray-400">
            로그인 상태를 확인하는 중...
          </span>
        </div>
      </div>
    );
  }

  return (
    <Router> 
      <ScrollToTop />
      <Routes>
        {/* 1. 로그인/회원가입 페이지는 항상 열어두되, 이미 로그인된 사람이 접근하면 메인으로 보냄 */}
        <Route 
          path="/signin" 
          element={signedIn ? <Navigate to="/" replace /> : <SignIn />} 
        />
        <Route 
          path="/signup" 
          element={signedIn ? <Navigate to="/" replace /> : <SignUp />} 
        />

        {/* 2. 로그인된 사용자만 접근할 수 있는 대시보드 레이아웃 */}
        {signedIn ? (
          <Route element={<AppLayout />}>
            <Route index path="/" element={<Home />} />
            <Route path="/profile" element={<UserProfiles />} />
            <Route path="/project" element={<Project />} />
            <Route path="/project/create" element={<ProjectCreation />} />
            {/* 프로젝트 편집: 서브넷 등급 + 연결 규칙 + 망분리 검증을 한 화면에서 다룹니다.
                쿼리스트링(?project_id=...) 도 받아 기존 마법사 링크와 호환됩니다. */}
            <Route path="/project/editor/:projectId" element={<ProjectEditor />} />
            <Route path="/project/editor" element={<ProjectEditor />} />
            <Route path="/project/create/ViewNodes" element={<DetectedNetworkNodes/>}/>
            <Route path="/project/create/subnet" element={<SubnetAdvanceConfiguration/>}/>
            <Route path="/project/create/segmentation" element={<NetworkSegmentationRule/>}/>
            <Route path="/project/create/preview" element={<TopologyRulePreview/>}/>
            <Route path="/agent" element={<Agent/>}/>
            <Route path="/compliance" element={<Compliance/>}/>
            <Route path="/compliance/export" element={<ComplianceExporter/>}/>
            <Route path="/policy" element={<PolicyManagement/>}/>
            <Route path="/policy/export" element={<PolicyExporter/>}/>
            <Route path="/network" element={<NetwworkManagement/>}/>
            
            
            <Route path="/calendar" element={<Calendar />} />
            <Route path="/blank" element={<Blank />} />
            <Route path="/form-elements" element={<FormElements />} />
            <Route path="/basic-tables" element={<BasicTables />} />
            <Route path="/alerts" element={<Alerts />} />
            <Route path="/avatars" element={<Avatars />} />
            <Route path="/badge" element={<Badges />} />
            <Route path="/buttons" element={<Buttons />} />
            <Route path="/images" element={<Images />} />
            <Route path="/videos" element={<Videos />} />
            <Route path="/line-chart" element={<LineChart />} />
            <Route path="/bar-chart" element={<BarChart />} />
          </Route>
        ) : (
          /* 3. 로그인 안 된 상태에서 대시보드('/') 등 보호된 페이지로 가려고 하면 /signin으로 튕김 */
          <Route path="*" element={<Navigate to="/signin" replace />} />
        )}

        {/* 4. 로그인된 상태에서 이상한 주소로 가면 404(NotFound) 처리 */}
        {signedIn && <Route path="*" element={<NotFound />} />}
      </Routes>
    </Router>
  );
}

  // return (
  //   <>
      
  //     <Router>
  //       <ScrollToTop />
  //       <Routes>
  //         {/* Dashboard Layout */}
  //         <Route element={<AppLayout />}>
  //           <Route index path="/" element={<Home />} />

  //           {/* Others Page */}
  //           <Route path="/profile" element={<UserProfiles />} />
  //           <Route path="/calendar" element={<Calendar />} />
  //           <Route path="/blank" element={<Blank />} />

  //           {/* Forms */}
  //           <Route path="/form-elements" element={<FormElements />} />

  //           {/* Tables */}
  //           <Route path="/basic-tables" element={<BasicTables />} />

  //           {/* Ui Elements */}
  //           <Route path="/alerts" element={<Alerts />} />
  //           <Route path="/avatars" element={<Avatars />} />
  //           <Route path="/badge" element={<Badges />} />
  //           <Route path="/buttons" element={<Buttons />} />
  //           <Route path="/images" element={<Images />} />
  //           <Route path="/videos" element={<Videos />} />

  //           {/* Charts */}
  //           <Route path="/line-chart" element={<LineChart />} />
  //           <Route path="/bar-chart" element={<BarChart />} />
  //         </Route>

  //         {/* Auth Layout */}
  //         <Route path="/signin" element={<SignIn />} />
  //         <Route path="/signup" element={<SignUp />} />

  //         {/* Fallback Route */}
  //         <Route path="*" element={<NotFound />} />
  //       </Routes>
  //     </Router>
  //   </>
  // );