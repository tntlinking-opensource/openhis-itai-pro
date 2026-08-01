import { useUserStore } from '@vben/stores';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 二次接诊跳转用的开药页签（开立医嘱=西药；中医处方=中药） */
export type DoctorOrderTabPreference = '中医处方' | '开立医嘱';

const STORAGE_KEY_PREFIX = 'openhis:doctor-station:order-tab-pref:';
const DEFAULT_PREF: DoctorOrderTabPreference = '开立医嘱';
const VALID_PREFS = new Set<string>(['开立医嘱', '中医处方']);

/** 当前登录用户 id（Long 字符串，禁用 Number） */
function resolvePreferenceUserId(): string {
  const userStore = useUserStore();
  return (
    preserveApiIdForRequest(userStore.userInfo?.userId) ??
    preserveApiIdForRequest(userStore.userInfo?.id) ??
    ''
  );
}

/** 读取本机记住的开药页签；无记录时默认开立医嘱 */
export function readDoctorOrderTabPreference(): DoctorOrderTabPreference {
  const userId = resolvePreferenceUserId();
  if (!userId) {
    return DEFAULT_PREF;
  }
  try {
    const raw = localStorage.getItem(`${STORAGE_KEY_PREFIX}${userId}`);
    const value = String(raw ?? '').trim();
    if (VALID_PREFS.has(value)) {
      return value as DoctorOrderTabPreference;
    }
  } catch {
    // 隐私模式等读写失败时回落默认
  }
  return DEFAULT_PREF;
}

/** 手动切到开立医嘱/中医处方时写入本机偏好，供下次二次接诊跳转 */
export function writeDoctorOrderTabPreference(tab: string): void {
  if (!VALID_PREFS.has(tab)) {
    return;
  }
  const userId = resolvePreferenceUserId();
  if (!userId) {
    return;
  }
  try {
    localStorage.setItem(`${STORAGE_KEY_PREFIX}${userId}`, tab);
  } catch {
    // ignore
  }
}
