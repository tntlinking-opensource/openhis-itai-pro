import { useUserStore } from '@vben/stores';

import { useAuthStore } from '#/store';

const STORAGE_PREFIX = 'openhis:pharmacy:preparer-preference';

/** 当前登录上下文下的 localStorage 键 */
export function buildPreparerPreferenceStorageKey(tenantId: string, userId: string): string {
  return `${STORAGE_PREFIX}:${tenantId}:${userId}`;
}

/** 从 Pinia 解析租户与用户主键（未登录时返回空串） */
export function resolvePreparerPreferenceScope(): { tenantId: string; userId: string } {
  const authStore = useAuthStore();
  const userStore = useUserStore();
  const tenantId = authStore.getSelectedTenant()?.trim() ?? '';
  const userId =
    userStore.userInfo?.id === null || userStore.userInfo?.id === undefined
      ? ''
      : String(userStore.userInfo.id);
  return { tenantId, userId };
}

/** 读取当前用户上次选择的调配药师 practitionerId */
export function readPreparerPreference(): string | undefined {
  const { tenantId, userId } = resolvePreparerPreferenceScope();
  if (!tenantId || !userId) {
    return undefined;
  }
  const raw = localStorage.getItem(buildPreparerPreferenceStorageKey(tenantId, userId));
  const id = raw?.trim();
  return id || undefined;
}

/** 保存或清空当前用户的调配药师偏好（清空时传空） */
export function writePreparerPreference(practitionerId: string | undefined): void {
  const { tenantId, userId } = resolvePreparerPreferenceScope();
  if (!tenantId || !userId) {
    return;
  }
  const key = buildPreparerPreferenceStorageKey(tenantId, userId);
  const id = practitionerId?.trim();
  if (!id) {
    localStorage.removeItem(key);
    return;
  }
  localStorage.setItem(key, id);
}

/**
 * 在候选列表中恢复缓存的调配药师；缓存无效时回落到首项。
 * @returns 最终应选中的 practitionerId，无候选时为 undefined
 */
export function resolvePreparerSelection(
  options: ReadonlyArray<{ value: string }>,
): string | undefined {
  if (options.length === 0) {
    return undefined;
  }
  const cached = readPreparerPreference();
  // 仅当缓存仍在当前候选中时回填，避免失效 ID 卡死下拉
  if (cached && options.some((opt) => opt.value === cached)) {
    return cached;
  }
  return options[0]?.value;
}
