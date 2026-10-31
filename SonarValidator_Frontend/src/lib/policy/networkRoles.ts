import type { NetworkRole } from "../api/types";

export const NETWORK_ROLES: { value: NetworkRole; label: string }[] = [
  { value: "MANAGEMENT", label: "관리망" },
  { value: "TRANSIT", label: "라우터 간 전송망" },
  { value: "SERVICE", label: "서비스·업무망" },
  { value: "GUEST", label: "Guest Shell·게스트망" },
  { value: "INTERNET", label: "인터넷 업링크" },
];
