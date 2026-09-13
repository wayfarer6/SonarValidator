import { useState } from "react";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";
import { useModal } from "../hooks/useModal"; 
import { Modal } from "../components/ui/modal";
import Button from "../components/ui/button/Button";
import Input from "../components/form/input/InputField";
import Label from "../components/form/Label";
import { Link, useNavigate } from "react-router";

export default function Project() {
  // axios로 불러오도록 나중에 수정

  // 예시 프로젝트 데이터 상태 (더미 데이터 상태)
  const [projects, setProjects] = useState([
    {
      id: 1,
      name: "Sonar Bank Network",
      category: "Finance",
      description: "Network Topology of Sonar Bank.",
      status: "In Progress",
    },
    {
      id: 2,
      name: "A Nation Defense Force Network",
      category: "government",
      description: "Network Topology of A Nation Defense Force.",
      status: "Planning",
    },
  ]);

  const { isOpen, openModal, closeModal } = useModal();

  const navigate = useNavigate();
  const handleCreate = () => {
    // 저장 로직 처리
    console.log("Creating project...");
    closeModal();
    const project_id = Math.random().toString(36).slice(2) + "_" + projectName;
    navigate(`/project/create/?project_id=${project_id}`);
  };

  const [projectName, setProjectName] = useState("");
  const [categoryName,setCategoryName] = useState("");
  const [projectDescription,setProjectDescription] = useState("");
  return (
    <>
      <PageMeta
        title="React.js Profile Dashboard | TailAdmin - Next.js Admin Dashboard Template"
        description="This is React.js Profile Dashboard page for TailAdmin - React.js Tailwind CSS Admin Dashboard Template"
      />
      <PageBreadcrumb pageTitle="Project" />
      
      <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
        {/* 헤더 부분: 제목과 Create Project 버튼 */}
        <div className="mb-5 flex items-center justify-between lg:mb-7">
          <h3 className="text-lg font-semibold text-gray-800 dark:text-white/90">
            Project
          </h3>
          <button 
            onClick={openModal}
            className="inline-flex items-center justify-center rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600"
          >
            Create Project
          </button>
        </div>

        {/* 프로젝트 목록 영역 (조건문 활용) */}
        <div className="space-y-6">
          {projects.length > 0 ? (
            // 데이터가 있을 경우 map으로 카드 리스트 렌더링
            projects.map((project) => (
              <div
                key={project.id}
                className="p-5 border border-gray-200 rounded-2xl dark:border-gray-800 lg:p-6 bg-white dark:bg-gray-900 shadow-theme-xs"
              >
                <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="space-y-1">
                    <span className="inline-block px-2.5 py-0.5 text-xs font-medium text-brand-500 bg-brand-50 dark:bg-brand-500/10 rounded-full mb-1">
                      {project.category}
                    </span>
                    <h4 className="text-lg font-semibold text-gray-800 dark:text-white/90">
                      {project.name}
                    </h4>
                    <p className="text-sm text-gray-500 dark:text-gray-400">
                      {project.description}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="text-xs font-medium px-3 py-1 bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 rounded-full">
                      {project.status}
                    </span>
                    <button className="px-3.5 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700">
                      Manage
                    </button>
                  </div>
                </div>
              </div>
            ))
          ) : (
            // 데이터가 없을 경우 No project 표시
            <div className="flex flex-col items-center justify-center py-12 text-center border border-dashed border-gray-200 dark:border-gray-800 rounded-2xl">
              <p className="text-base font-medium text-gray-600 dark:text-gray-400">
                No project
              </p>
              <p className="text-sm text-gray-400 dark:text-gray-500 mt-1">
                Get started by creating a new project.
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Create / Edit Modal */}
      <Modal isOpen={isOpen} onClose={closeModal} className="max-w-[700px] m-4">
        <div className="no-scrollbar relative w-full max-w-[700px] overflow-y-auto rounded-3xl bg-white p-4 dark:bg-gray-900 lg:p-11">
          <div className="px-2 pr-14">
            <h4 className="mb-2 text-2xl font-semibold text-gray-800 dark:text-white/90">
              Create Project
            </h4>
            <p className="mb-6 text-sm text-gray-500 dark:text-gray-400 lg:mb-7">
              Fill in the details to create a new project.
            </p>
          </div>
          <form className="flex flex-col">
            <div className="custom-scrollbar h-[350px] overflow-y-auto px-2 pb-3 space-y-5">
              <div>
                <Label>Project Name</Label>
                <Input
                 type="text" placeholder="Enter project name"
                 value={projectName}
                 onChange={(e)=> setProjectName(e.target.value)}
                 />
              </div>
              <div>
                <Label>Category</Label>
                <Input 
                type="text" 
                placeholder="e.g. Web Application"
                value={categoryName}
                onChange={(e)=>setCategoryName(e.target.value)}
                />
              </div>
              <div>
                <Label>Description</Label>
                <Input type="text"
                placeholder="Enter brief description"
                value={projectDescription}
                onChange={(e)=>setProjectDescription(e.target.value)}
                 />
              </div>
            </div>
            <div className="flex items-center gap-3 px-2 mt-6 lg:justify-end">
              <Button size="sm" variant="outline" onClick={closeModal}>
                Close
              </Button>
              <Button size="sm" onClick={handleCreate}>
                Create Project
              </Button>
            </div>
          </form>
        </div>
      </Modal>
    </>
  );
}