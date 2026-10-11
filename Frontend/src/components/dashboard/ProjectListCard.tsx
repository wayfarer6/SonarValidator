import { Link } from "react-router";
import { FolderIcon } from "../../icons";
import { useApi } from "../../hooks/useApi";
import { listProjects } from "../../lib/api/projects";

const STATUS_STYLE: Record<string, string> = {
  "In Progress":
    "bg-blue-50 text-blue-600 dark:bg-blue-500/10 dark:text-blue-400",
  Planning:
    "bg-orange-50 text-orange-600 dark:bg-orange-500/10 dark:text-orange-400",
  Completed:
    "bg-green-50 text-green-600 dark:bg-green-500/10 dark:text-green-400",
};

// 현재 프로젝트 리스트 카드 (백엔드 프로젝트 목록)
export default function ProjectListCard() {
  const { data, loading, error } = useApi(() => listProjects(), []);
  const projects = data?.projects ?? [];

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] md:p-6">
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-gray-100 dark:bg-gray-800">
            <FolderIcon className="size-5 text-gray-700 dark:text-white/90" />
          </div>
          <h4 className="text-base font-semibold text-gray-800 dark:text-white/90">
            Projects
          </h4>
        </div>
        <Link
          to="/project"
          className="text-sm font-medium text-brand-500 hover:text-brand-600"
        >
          View All
        </Link>
      </div>

      <div className="space-y-3">
        {projects.map((project) => (
          <div
            key={project.project_id}
            className="flex items-center justify-between rounded-xl border border-gray-100 p-3 transition hover:border-gray-200 hover:bg-gray-50/60 dark:border-gray-800 dark:hover:bg-gray-800/40"
          >
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-gray-800 dark:text-white/90">
                {project.name}
              </p>
              <p className="mt-0.5 truncate text-xs text-gray-500 dark:text-gray-400">
                {[project.category, project.description]
                  .filter(Boolean)
                  .join(" · ") || "—"}
              </p>
            </div>
            <span
              className={`ml-3 shrink-0 rounded-full px-2.5 py-1 text-xs font-medium ${
                STATUS_STYLE[project.status] ?? STATUS_STYLE.Planning
              }`}
            >
              {project.status}
            </span>
          </div>
        ))}

        {loading && (
          <p className="py-6 text-center text-xs text-gray-400">불러오는 중...</p>
        )}

        {!loading && error && (
          <p className="py-6 text-center text-xs text-red-500">{error}</p>
        )}

        {!loading && !error && projects.length === 0 && (
          <p className="py-6 text-center text-xs text-gray-400">
            프로젝트가 없습니다.
          </p>
        )}
      </div>
    </div>
  );
}
