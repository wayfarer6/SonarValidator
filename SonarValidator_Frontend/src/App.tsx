import { BrowserRouter as Router, Routes, Route } from "react-router";
import SignIn from "./pages/AuthPages/SignIn";
import SignUp from "./pages/AuthPages/SignUp";
import NotFound from "./pages/OtherPage/NotFound";
import UserProfiles from "./pages/UserProfiles";
import AppLayout from "./layout/AppLayout";
import { ScrollToTop } from "./components/common/ScrollToTop";
import Home from "./pages/Dashboard/Home";
import { Navigate } from 'react-router-dom';
import { useAuth } from "./context/AuthContext";
import Project from "./pages/Project";
import ProjectCreation from "./pages/ProjectCreation";
import ProjectEditor from "./pages/ProjectEditor";
import Agent from "./pages/Agent";
import ApiConnectedNodes from "./pages/ApiConnectedNodes";
import Compliance from "./pages/Compliance";
import DetectedNetworkNodes from "./pages/DetectedNetworkNodes";
import SubnetAdvanceConfiguration from "./pages/SubnetAdvanceConfiguration";
import NetworkSegmentationRule from "./pages/NetworkSegmentationRule";
import TopologyRulePreview from "./pages/TopologyRulePreview";
import ComplianceExporter from "./pages/ComplianceExporter";
import PolicyManagement from "./pages/PolicyManagement";
import PolicyExporter from "./pages/PolicyExporter";
import NetworkManagement from "./pages/NetworkManagement";
import LogManagement from "./pages/LogManagement";
import Notification from "./pages/Notification";

export default function App() {

  const { user, initializing } = useAuth();
  const signedIn = user !== null;

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
        <Route
          path="/signin"
          element={signedIn ? <Navigate to="/" replace /> : <SignIn />}
        />
        <Route
          path="/signup"
          element={signedIn ? <Navigate to="/" replace /> : <SignUp />}
        />
        {signedIn ? (
          <Route element={<AppLayout />}>
            <Route index path="/" element={<Home />} />
            <Route path="/profile" element={<UserProfiles />} />
            <Route path="/project" element={<Project />} />
            <Route path="/project/create" element={<ProjectCreation />} />
            <Route path="/project/editor/:projectId" element={<ProjectEditor />} />
            <Route path="/project/editor" element={<ProjectEditor />} />
            <Route path="/project/create/ViewNodes" element={<DetectedNetworkNodes/>}/>
            <Route path="/project/create/subnet" element={<SubnetAdvanceConfiguration/>}/>
            <Route path="/project/create/segmentation" element={<NetworkSegmentationRule/>}/>
            <Route path="/project/create/preview" element={<TopologyRulePreview/>}/>
            <Route path="/agent" element={<Agent/>}/>
            <Route path="/api-nodes" element={<ApiConnectedNodes/>}/>
            <Route path="/compliance" element={<Compliance/>}/>
            <Route path="/compliance/export" element={<ComplianceExporter/>}/>
            <Route path="/policy" element={<PolicyManagement/>}/>
            <Route path="/policy/export" element={<PolicyExporter/>}/>
            <Route path="/log" element={<LogManagement/>}/>
            <Route path="/log/export" element={<LogManagement/>}/>
            <Route path="/*/log" element={<LogManagement/>}/>
            <Route path="/network" element={<NetworkManagement/>}/>
            <Route path="/notification" element={<Notification/>}/>
            <Route path="/notification/export" element={<Notification/>}/>

          </Route>
        ) : (
          /* 3. 로그인 안 된 상태에서 대시보드('/') 등 보호된 페이지로 가려고 하면 /signin으로 튕김 */
          <Route path="*" element={<Navigate to="/signin" replace />} />
        )}
        {signedIn && <Route path="*" element={<NotFound />} />}
      </Routes>
    </Router>
  );
}
