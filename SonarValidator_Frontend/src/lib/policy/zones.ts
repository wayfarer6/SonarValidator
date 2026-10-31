import type { SubnetClass } from "../../lib/api/types";

export const ZONE_CLASSES: {
  value: SubnetClass;
  label: string;
  level: number;
  badgeClass: string;
  rowClass: string;
  description: string;
}[] = [
  {
    value: "Confidential",
    label: "Confidential",
    level: 3,
    badgeClass: "bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-300",
    rowClass: "bg-red-50/40 dark:bg-red-900/10",
    description: "기밀망. 외부(Open)와 직접 연결 금지",
  },
  {
    value: "Sensitive",
    label: "Sensitive",
    level: 2,
    badgeClass: "bg-orange-100 text-orange-800 dark:bg-orange-900/40 dark:text-orange-300",
    rowClass: "bg-orange-50/40 dark:bg-orange-900/10",
    description: "내부 업무망. 양쪽 등급과 연결 가능",
  },
  {
    value: "Open",
    label: "Open",
    level: 1,
    badgeClass: "bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300",
    rowClass: "bg-blue-50/40 dark:bg-blue-900/10",
    description: "공개망. 기밀망과 직접 연결 금지",
  },
];

export function zoneInfo(value: SubnetClass | null) {
  if (!value) return null;
  return ZONE_CLASSES.find((zone) => zone.value === value) ?? null;
}

export function isForbiddenPair(
  source: SubnetClass | null,
  target: SubnetClass | null,
): boolean {
  const sourceZone = zoneInfo(source);
  const targetZone = zoneInfo(target);
  // 등급을 모르면 판정하지 않습니다. (오탐 방지 — 서버와 동일한 원칙)
  if (!sourceZone || !targetZone) return false;
  return Math.abs(sourceZone.level - targetZone.level) >= 2;
}

export function forbiddenReason(
  source: SubnetClass | null,
  target: SubnetClass | null,
): string {
  return `${source ?? "?"} ↔ ${target ?? "?"} 직접 연결은 허용되지 않습니다.`;
}
