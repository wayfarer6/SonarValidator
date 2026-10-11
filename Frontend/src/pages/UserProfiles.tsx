import { useEffect, useRef } from "react";
import { useSearchParams } from "react-router";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import UserMetaCard from "../components/UserProfile/UserMetaCard";
import UserInfoCard from "../components/UserProfile/UserInfoCard";
import AccountManagementCard from "../components/UserProfile/AccountManagementCard";
import PageMeta from "../components/common/PageMeta";

export default function UserProfiles() {
  const [searchParams] = useSearchParams();
  const accountRef = useRef<HTMLDivElement>(null);

  // Account settings(드롭다운)에서 ?tab=account 로 진입한 경우 해당 섹션으로 스크롤
  useEffect(() => {
    if (searchParams.get("tab") === "account") {
      accountRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [searchParams]);

  return (
    <>
      <PageMeta
        title="React.js Profile Dashboard | TailAdmin - Next.js Admin Dashboard Template"
        description="This is React.js Profile Dashboard page for TailAdmin - React.js Tailwind CSS Admin Dashboard Template"
      />
      <PageBreadcrumb pageTitle="Profile" />
      <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
        <h3 className="mb-5 text-lg font-semibold text-gray-800 dark:text-white/90 lg:mb-7">
          Profile
        </h3>
        <div className="space-y-6">
          <UserMetaCard />
          <UserInfoCard />
          {/* <UserAddressCard /> */}
        </div>
      </div>
      <div ref={accountRef} className="mt-6 scroll-mt-24">
        <AccountManagementCard />
      </div>
    </>
  );
}
