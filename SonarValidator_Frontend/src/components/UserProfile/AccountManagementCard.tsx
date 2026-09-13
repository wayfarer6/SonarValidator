import { useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router";
import { useCookies } from "react-cookie";
import { useModal } from "../../hooks/useModal";
import { Modal } from "../ui/modal";
import Button from "../ui/button/Button";
import Input from "../form/input/InputField";
import Label from "../form/Label";
import { notifyUserInfoChanged, useUserEmail } from "../../lib/userInfo";

type Feedback = { type: "success" | "error"; text: string } | null;

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function FeedbackText({ feedback }: { feedback: Feedback }) {
  if (!feedback) return null;
  return (
    <p
      className={`col-span-2 text-sm font-medium ${
        feedback.type === "error"
          ? "text-error-600 dark:text-error-500"
          : "text-success-600 dark:text-success-500"
      }`}
    >
      {feedback.text}
    </p>
  );
}

export default function AccountManagementCard() {
  const [, setCookie, removeCookie] = useCookies(["username"]);
  const navigate = useNavigate();
  const currentEmail = useUserEmail();
  const { isOpen, openModal, closeModal } = useModal();

  // 비밀번호 변경 상태
  const [currentPw, setCurrentPw] = useState("");
  const [newPw, setNewPw] = useState("");
  const [confirmPw, setConfirmPw] = useState("");
  const [pwFeedback, setPwFeedback] = useState<Feedback>(null);

  // 이메일 변경 상태
  const [newEmail, setNewEmail] = useState("");
  const [confirmEmail, setConfirmEmail] = useState("");
  const [emailFeedback, setEmailFeedback] = useState<Feedback>(null);

  const handleChangePassword = (e: FormEvent) => {
    e.preventDefault();
    if (!currentPw) {
      setPwFeedback({ type: "error", text: "현재 비밀번호를 입력해주세요." });
      return;
    }
    if (newPw.length < 8) {
      setPwFeedback({ type: "error", text: "새 비밀번호는 8자 이상이어야 합니다." });
      return;
    }
    if (newPw !== confirmPw) {
      setPwFeedback({ type: "error", text: "새 비밀번호 확인이 일치하지 않습니다." });
      return;
    }
    // TODO: 백엔드 비밀번호 변경 API 호출 (POST /api/v1/users/me/password)
    setPwFeedback({ type: "success", text: "비밀번호가 변경되었습니다." });
    setCurrentPw("");
    setNewPw("");
    setConfirmPw("");
  };

  const handleChangeEmail = (e: FormEvent) => {
    e.preventDefault();
    if (!EMAIL_REGEX.test(newEmail)) {
      setEmailFeedback({ type: "error", text: "올바른 이메일 형식이 아닙니다." });
      return;
    }
    if (newEmail === currentEmail) {
      setEmailFeedback({ type: "error", text: "현재 사용 중인 이메일입니다." });
      return;
    }
    if (newEmail !== confirmEmail) {
      setEmailFeedback({ type: "error", text: "이메일 확인이 일치하지 않습니다." });
      return;
    }
    // TODO: 백엔드 이메일 변경 API 호출 (인증 메일 발송 등)
    setCookie("username", newEmail, { path: "/" });
    notifyUserInfoChanged();
    setEmailFeedback({ type: "success", text: "이메일이 변경되었습니다." });
    setNewEmail("");
    setConfirmEmail("");
  };

  const handleDeleteAccount = () => {
    // TODO: 백엔드 계정 삭제 API 호출 (DELETE /api/v1/users/me)
    closeModal();
    removeCookie("username", { path: "/" });
    navigate("/signin");
  };

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
      <h3 className="mb-5 text-lg font-semibold text-gray-800 dark:text-white/90 lg:mb-7">
        Account Management
      </h3>

      <div className="space-y-7">
        {/* 비밀번호 변경 */}
        <form onSubmit={handleChangePassword} className="flex flex-col gap-4">
          <div>
            <h4 className="text-sm font-semibold text-gray-800 dark:text-white/90">
              비밀번호 변경
            </h4>
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
              계정 보호를 위해 주기적으로 비밀번호를 변경하는 것을 권장합니다.
            </p>
          </div>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
            <div>
              <Label>현재 비밀번호</Label>
              <Input
                type="password"
                value={currentPw}
                onChange={(e) => setCurrentPw(e.target.value)}
                placeholder="현재 비밀번호"
              />
            </div>
            <div>
              <Label>새 비밀번호</Label>
              <Input
                type="password"
                value={newPw}
                onChange={(e) => setNewPw(e.target.value)}
                placeholder="8자 이상"
              />
            </div>
            <div>
              <Label>새 비밀번호 확인</Label>
              <Input
                type="password"
                value={confirmPw}
                onChange={(e) => setConfirmPw(e.target.value)}
                placeholder="새 비밀번호 재입력"
              />
            </div>
            <FeedbackText feedback={pwFeedback} />
          </div>
          <div className="flex justify-end">
            <Button size="sm" type="submit">
              비밀번호 변경
            </Button>
          </div>
        </form>

        {/* 이메일 변경 */}
        <form
          onSubmit={handleChangeEmail}
          className="flex flex-col gap-4 border-t border-gray-200 pt-6 dark:border-gray-800"
        >
          <div>
            <h4 className="text-sm font-semibold text-gray-800 dark:text-white/90">
              이메일 변경
            </h4>
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
              로그인에 사용하는 이메일 주소를 변경합니다.
            </p>
          </div>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
            <div>
              <Label>현재 이메일</Label>
              <Input type="email" value={currentEmail} disabled />
            </div>
            <div>
              <Label>새 이메일</Label>
              <Input
                type="email"
                value={newEmail}
                onChange={(e) => setNewEmail(e.target.value)}
                placeholder="new@email.com"
              />
            </div>
            <div>
              <Label>새 이메일 확인</Label>
              <Input
                type="email"
                value={confirmEmail}
                onChange={(e) => setConfirmEmail(e.target.value)}
                placeholder="새 이메일 재입력"
              />
            </div>
            <FeedbackText feedback={emailFeedback} />
          </div>
          <div className="flex justify-end">
            <Button size="sm" type="submit">
              이메일 변경
            </Button>
          </div>
        </form>

        {/* 계정 삭제 */}
        <div className="border-t border-gray-200 pt-6 dark:border-gray-800">
          <div className="flex flex-col gap-4 rounded-xl border border-error-200 bg-error-50 p-4 dark:border-error-500/30 dark:bg-error-500/10 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <h4 className="text-sm font-semibold text-error-600 dark:text-error-500">
                계정 삭제
              </h4>
              <p className="mt-1 text-xs text-gray-600 dark:text-gray-400">
                계정을 삭제하면 모든 프로젝트·로그 데이터가 함께 삭제되며 복구할 수
                없습니다.
              </p>
            </div>
            <button
              onClick={openModal}
              className="inline-flex shrink-0 items-center justify-center rounded-lg bg-error-600 px-4 py-3 text-sm font-medium text-white shadow-theme-xs transition hover:bg-error-500"
            >
              계정 삭제
            </button>
          </div>
        </div>
      </div>

      {/* 계정 삭제 확인 모달 */}
      <Modal isOpen={isOpen} onClose={closeModal} className="max-w-[480px] m-4">
        <div className="relative w-full rounded-3xl bg-white p-6 dark:bg-gray-900 lg:p-8">
          <h4 className="mb-2 text-xl font-semibold text-gray-800 dark:text-white/90">
            계정을 삭제하시겠습니까?
          </h4>
          <p className="mb-6 text-sm text-gray-500 dark:text-gray-400">
            <span className="font-medium text-gray-700 dark:text-gray-300">
              {currentEmail}
            </span>{" "}
            계정이 영구적으로 삭제됩니다. 이 작업은 되돌릴 수 없습니다.
          </p>
          <div className="flex items-center justify-end gap-3">
            <Button size="sm" variant="outline" onClick={closeModal}>
              취소
            </Button>
            <button
              onClick={handleDeleteAccount}
              className="inline-flex items-center justify-center rounded-lg bg-error-600 px-4 py-3 text-sm font-medium text-white shadow-theme-xs transition hover:bg-error-500"
            >
              영구 삭제
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
