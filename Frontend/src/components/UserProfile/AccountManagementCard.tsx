import { useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router";
import { useModal } from "../../hooks/useModal";
import { Modal } from "../ui/modal";
import Button from "../ui/button/Button";
import Input from "../form/input/InputField";
import Label from "../form/Label";
import { useUserEmail } from "../../lib/userInfo";
import { useAuth } from "../../context/AuthContext";
import { apiRequest, ApiError } from "../../lib/api/client";
import { changePassword } from "../../lib/api/auth";

type Feedback = { type: "success" | "error"; text: string } | null;

/**
 * 계정 관리 카드입니다.
 *
 * <h2>자리표시자에서 실제 API 로</h2>
 * 이전 구현은 서버를 부르지 않고 화면 메시지만 바꿨습니다.
 * (<code>// TODO: 백엔드 비밀번호 변경 API 호출</code>)
 * 즉 "변경되었습니다" 가 표시돼도 <b>실제로는 아무것도 바뀌지 않았습니다.</b>
 * 이제 서버 API 와 연결했습니다.
 *
 * <h2>이메일 변경 기능을 뺀 이유</h2>
 * 현재 백엔드는 아이디(username)가 로그인 키이고, 사용자 이름 변경 API 가
 * 없습니다. 화면만 남겨 두면 "바꿨는데 로그인이 안 된다" 는 혼란을 만듭니다.
 * 그래서 <b>아이디는 읽기 전용</b>으로 표시하고, 변경이 필요하면 관리자가
 * 새 계정을 만드는 흐름으로 안내합니다.
 *
 * <h2>계정 삭제에 비밀번호를 요구하는 이유</h2>
 * 삭제는 되돌릴 수 없습니다. 세션만 탈취한 공격자가 계정을 지워버리는 것을
 * 막으려면 비밀번호 확인이 필요합니다. 서버도 같은 검증을 합니다.
 */
export default function AccountManagementCard() {
  const navigate = useNavigate();
  const currentEmail = useUserEmail();
  const { logout } = useAuth();
  const { isOpen, openModal, closeModal } = useModal();

  // 비밀번호 변경 상태
  const [currentPw, setCurrentPw] = useState("");
  const [newPw, setNewPw] = useState("");
  const [confirmPw, setConfirmPw] = useState("");
  const [pwFeedback, setPwFeedback] = useState<Feedback>(null);
  const [pwSubmitting, setPwSubmitting] = useState(false);

  // 계정 삭제 상태
  const [deletePw, setDeletePw] = useState("");
  const [deleteFeedback, setDeleteFeedback] = useState<Feedback>(null);
  const [deleteSubmitting, setDeleteSubmitting] = useState(false);

  /** 비밀번호를 변경합니다. */
  const handleChangePassword = async (e: FormEvent) => {
    e.preventDefault();
    setPwFeedback(null);

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

    setPwSubmitting(true);
    try {
      await changePassword(currentPw, newPw);
      setPwFeedback({ type: "success", text: "비밀번호가 변경되었습니다." });
      setCurrentPw("");
      setNewPw("");
      setConfirmPw("");
    } catch (cause) {
      setPwFeedback({
        type: "error",
        text: cause instanceof ApiError ? cause.message : "비밀번호 변경에 실패했습니다.",
      });
    } finally {
      setPwSubmitting(false);
    }
  };

  /** 계정을 삭제합니다. */
  const handleDeleteAccount = async () => {
    setDeleteFeedback(null);
    if (!deletePw) {
      setDeleteFeedback({ type: "error", text: "비밀번호를 입력하세요." });
      return;
    }

    setDeleteSubmitting(true);
    try {
      await apiRequest<{ message: string }>("/api/v1/users/me", {
        method: "DELETE",
        body: { currentPassword: deletePw },
      });
      closeModal();
      // 서버가 세션을 무효화했으므로 프론트 상태도 초기화합니다.
      await logout();
      navigate("/signin");
    } catch (cause) {
      setDeleteFeedback({
        type: "error",
        text: cause instanceof ApiError ? cause.message : "계정 삭제에 실패했습니다.",
      });
    } finally {
      setDeleteSubmitting(false);
    }
  };

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
      <h3 className="mb-5 text-lg font-semibold text-gray-800 dark:text-white/90 lg:mb-7">
        Account Management
      </h3>

      <div className="space-y-7">
        {/* 아이디 (읽기 전용) */}
        <div className="flex flex-col gap-3">
          <div>
            <h4 className="text-sm font-semibold text-gray-800 dark:text-white/90">
              아이디
            </h4>
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
              아이디는 변경할 수 없습니다. 변경이 필요하면 관리자에게 새 계정을 요청하세요.
            </p>
          </div>
          <Input type="text" value={currentEmail} disabled />
        </div>

        {/* 비밀번호 변경 */}
        <form
          onSubmit={handleChangePassword}
          className="flex flex-col gap-4 border-t border-gray-200 pt-6 dark:border-gray-800"
        >
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
            <Button size="sm" type="submit" disabled={pwSubmitting}>
              {pwSubmitting ? "변경 중..." : "비밀번호 변경"}
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
                계정을 삭제하면 되돌릴 수 없습니다. 마지막 관리자 계정은 삭제할 수
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

      {/* 계정 삭제 확인 모달 — 기존 Modal 컴포넌트를 재사용합니다. */}
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

          <div className="mb-4">
            <Label>
              비밀번호 확인 <span className="text-error-500">*</span>
            </Label>
            <Input
              type="password"
              value={deletePw}
              onChange={(e) => setDeletePw(e.target.value)}
              placeholder="현재 비밀번호를 입력하세요"
            />
          </div>

          <FeedbackText feedback={deleteFeedback} />

          <div className="mt-6 flex items-center justify-end gap-3">
            <Button size="sm" variant="outline" onClick={closeModal}>
              취소
            </Button>
            <Button
              size="sm"
              onClick={handleDeleteAccount}
              disabled={deleteSubmitting}
              className="bg-error-600 hover:bg-error-500"
            >
              {deleteSubmitting ? "삭제 중..." : "계정 삭제"}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}

/** 피드백 메시지를 렌더링합니다. */
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
