import PageMeta from "../../components/common/PageMeta";
import AuthLayout from "./AuthPageLayout";
import SignInForm from "../../components/auth/SignInForm";

export default function SignIn() {
  return (
    <>
      <PageMeta
        title="로그인 | SonarValidator"
        description="SonarValidator 관리 계정 로그인"
      />
      <AuthLayout>
        <SignInForm />
      </AuthLayout>
    </>
  );
}
