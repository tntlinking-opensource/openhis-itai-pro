import { useUserStore } from '@zxplat/stores';

import { useAuthStore } from '#/store';

const STORAGE_PREFIX = 'zxplat:pharmacy:op-dispense-auto-print-prescription';
const ENABLED_VALUE = '1';

/** 当前登录上下文下的 localStorage 键 */
export function buildAutoPrintPrescriptionPreferenceStorageKey(
  tenantId: string,
  userId: string,
): string {
  return `${STORAGE_PREFIX}:${tenantId}:${userId}`;
}

/** 从 Pinia 解析租户与用户主键（未登录时返回空串） */
export function resolveAutoPrintPrescriptionPreferenceScope(): {
  tenantId: string;
  userId: string;
} {
  const authStore = useAuthStore();
  const userStore = useUserStore();
  const tenantId = authStore.getSelectedTenant()?.trim() ?? '';
  const userId =
    userStore.userInfo?.id === null || userStore.userInfo?.id === undefined
      ? ''
      : String(userStore.userInfo.id);
  return { tenantId, userId };
}

/** 读取当前用户「发药后自动打印处方」开关；无缓存时默认关闭 */
export function readAutoPrintPrescriptionPreference(): boolean {
  const { tenantId, userId } = resolveAutoPrintPrescriptionPreferenceScope();
  if (!tenantId || !userId) {
    return false;
  }
  const raw = localStorage.getItem(
    buildAutoPrintPrescriptionPreferenceStorageKey(tenantId, userId),
  );
  return raw === ENABLED_VALUE;
}

/** 保存当前用户「发药后自动打印处方」开关 */
export function writeAutoPrintPrescriptionPreference(enabled: boolean): void {
  const { tenantId, userId } = resolveAutoPrintPrescriptionPreferenceScope();
  if (!tenantId || !userId) {
    return;
  }
  const key = buildAutoPrintPrescriptionPreferenceStorageKey(tenantId, userId);
  localStorage.setItem(key, enabled ? ENABLED_VALUE : '0');
}
