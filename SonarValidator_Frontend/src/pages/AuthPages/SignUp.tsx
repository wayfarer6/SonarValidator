import PageMeta from "../../components/common/PageMeta";
import AuthLayout from "./AuthPageLayout";
import SignUpForm from "../../components/auth/SignUpForm";

export default function SignUp() {
  return (
    <>
      <PageMeta
        title="계정 안내 | SonarValidator"
        description="SonarValidator 계정 생성 안내"
      />
      <AuthLayout>
        <SignUpForm />
      </AuthLayout>
    </>
  );
}
