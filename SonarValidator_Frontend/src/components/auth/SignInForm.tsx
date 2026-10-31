import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { AlertIcon, ChevronLeftIcon, EyeCloseIcon, EyeIcon } from "../../icons";
import Label from "../form/Label";
import Input from "../form/input/InputField";
import Checkbox from "../form/input/Checkbox";
import Button from "../ui/button/Button";
import { useAuth } from "../../context/AuthContext";

export default function SignInForm() {
  const navigate = useNavigate();
  const { login, submitting, error, clearError } = useAuth();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isChecked, setIsChecked] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  const handleLogin = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setValidationError(null);

    // 서버 호출 전 기본 검증으로 불필요한 왕복을 줄입니다.
    if (!username.trim()) {
      setValidationError("아이디를 입력하세요.");
      return;
    }
    if (!password) {
      setValidationError("비밀번호를 입력하세요.");
      return;
    }

    const ok = await login(username.trim(), password);
    if (ok) {
      navigate("/");
    }
    // 실패 시 오류는 컨텍스트의 error 로 표시됩니다.
  };

  const displayError = validationError ?? error;

  return (
    <div className="flex flex-col flex-1">
      <div className="w-full max-w-md pt-10 mx-auto">
        <Link
          to="/"
          className="inline-flex items-center text-sm text-gray-500 transition-colors hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300"
        >
          <ChevronLeftIcon className="size-5" />
          Back to dashboard
        </Link>
      </div>
      <div className="flex flex-col justify-center flex-1 w-full max-w-md mx-auto">
        <div>
          <div className="mb-5 sm:mb-8">
            <h1 className="mb-2 font-semibold text-gray-800 text-title-sm dark:text-white/90 sm:text-title-md">
              Sign In
            </h1>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              계속하려면 아이디와 비밀번호로 로그인 하십시오.
            </p>
          </div>
          <div>
            <form onSubmit={handleLogin}>
              <div className="space-y-6">
                <div>
                  <Label>
                    아이디 <span className="text-error-500">*</span>{" "}
                  </Label>
                  <Input
                    type="text"
                    placeholder="admin"
                    value={username}
                    onChange={(e) => {
                      setUsername(e.target.value);
                      if (displayError) clearError();
                    }}
                  />
                </div>
                <div>
                  <Label>
                    비밀번호 <span className="text-error-500">*</span>{" "}
                  </Label>
                  <div className="relative">
                    <Input
                      type={showPassword ? "text" : "password"}
                      placeholder="비밀번호를 입력하세요"
                      value={password}
                      onChange={(e) => {
                        setPassword(e.target.value);
                        if (displayError) clearError();
                      }}
                    />
                    <span
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute z-30 -translate-y-1/2 cursor-pointer right-4 top-1/2"
                    >
                      {showPassword ? (
                        <EyeIcon className="fill-gray-500 dark:fill-gray-400 size-5" />
                      ) : (
                        <EyeCloseIcon className="fill-gray-500 dark:fill-gray-400 size-5" />
                      )}
                    </span>
                  </div>
                </div>

                {/* 인증 오류 안내 */}
                {displayError && (
                  <div className="flex items-start gap-2 rounded-lg bg-error-50 px-3 py-2.5 text-xs text-error-700 dark:bg-error-500/15 dark:text-error-300">
                    <AlertIcon className="mt-0.5 size-4 shrink-0" />
                    <span>{displayError}</span>
                  </div>
                )}

                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <Checkbox checked={isChecked} onChange={setIsChecked} />
                    <span className="block font-normal text-gray-700 text-theme-sm dark:text-gray-400">
                      로그인 상태 유지하기
                    </span>
                  </div>
                </div>
                <div>
                  <Button className="w-full" size="sm" type="submit" disabled={submitting}>
                    {submitting ? "로그인 중..." : "Sign in"}
                  </Button>
                </div>
              </div>
            </form>

            <div className="mt-5">
              <p className="text-center text-xs text-gray-400 dark:text-gray-500">
                초기 계정은 <span className="font-mono">admin</span> / 환경변수
                <span className="font-mono"> SONAR_ADMIN_PASSWORD</span> 로 설정한 비밀번호입니다.
                기본값을 쓰고 있다면 즉시 변경하세요.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
