import { useUserStore } from '@vben/stores';

import { useAuthStore } from '#/store';

const STORAGE_PREFIX = 'openhis:pharmacy:op-dispense-auto-review';
const ENABLED_VALUE = '1';

/** 当前登录上下文下的 localStorage 键 */
export function buildAutoReviewPreferenceStorageKey(tenantId: string, userId: string): string {
  return `${STORAGE_PREFIX}:${tenantId}:${userId}`;
}

/** 从 Pinia 解析租户与用户主键（未登录时返回空串） */
export function resolveAutoReviewPreferenceScope(): { tenantId: string; userId: string } {
  const authStore = useAuthStore();
  const userStore = useUserStore();
  const tenantId = authStore.getSelectedTenant()?.trim() ?? '';
  const userId =
    userStore.userInfo?.id === null || userStore.userInfo?.id === undefined
      ? ''
      : String(userStore.userInfo.id);
  return { tenantId, userId };
}

/** 读取当前用户「发药完成自动点评」开关；无缓存时默认关闭 */
export function readAutoReviewPreference(): boolean {
  const { tenantId, userId } = resolveAutoReviewPreferenceScope();
  if (!tenantId || !userId) {
    return false;
  }
  const raw = localStorage.getItem(buildAutoReviewPreferenceStorageKey(tenantId, userId));
  return raw === ENABLED_VALUE;
}

/** 保存当前用户「发药完成自动点评」开关 */
export function writeAutoReviewPreference(enabled: boolean): void {
  const { tenantId, userId } = resolveAutoReviewPreferenceScope();
  if (!tenantId || !userId) {
    return;
  }
  const key = buildAutoReviewPreferenceStorageKey(tenantId, userId);
  localStorage.setItem(key, enabled ? ENABLED_VALUE : '0');
}
