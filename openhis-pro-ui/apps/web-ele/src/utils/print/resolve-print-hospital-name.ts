import { useUserStore } from '@vben/stores';

/** 从登录用户上下文解析机构抬头（C） */
export function resolvePrintHospitalName(): string {
  const userStore = useUserStore();
  const userInfo = userStore.userInfo as null | Record<string, unknown> | undefined;
  if (!userInfo) {
    return '';
  }
  const candidates = [userInfo.hospitalName, userInfo.tenantName, userInfo.orgName];
  for (const item of candidates) {
    const text = String(item ?? '').trim();
    if (text) {
      return text;
    }
  }
  return '';
}
