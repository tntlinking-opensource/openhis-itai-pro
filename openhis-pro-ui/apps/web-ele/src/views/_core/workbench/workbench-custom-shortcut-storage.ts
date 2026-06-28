import { useUserStore } from '@vben/stores';

import { useAuthStore } from '#/store';

const STORAGE_PREFIX = 'openhis:workbench:custom-shortcuts';

/** 自定义快捷入口最多保存条数 */
export const WORKBENCH_CUSTOM_SHORTCUT_MAX = 12;

/** 无自定义项时默认展示的空占位格数量 */
export const WORKBENCH_CUSTOM_EMPTY_SLOT_COUNT = 6;

/** 本地持久化的自定义快捷入口记录 */
export interface WorkbenchCustomShortcutRecord {
  routeName: string;
}

/** 当前登录上下文下的 localStorage 键 */
export function buildWorkbenchCustomShortcutStorageKey(tenantId: string, userId: string): string {
  return `${STORAGE_PREFIX}:${tenantId}:${userId}`;
}

/** 从 Pinia 解析租户与用户主键（未登录时返回空串） */
export function resolveWorkbenchCustomShortcutScope(): { tenantId: string; userId: string } {
  const authStore = useAuthStore();
  const userStore = useUserStore();
  const tenantId = authStore.getSelectedTenant()?.trim() ?? '';
  const userId =
    userStore.userInfo?.id === null || userStore.userInfo?.id === undefined
      ? ''
      : String(userStore.userInfo.id);
  return { tenantId, userId };
}

function normalizeRecords(raw: unknown): WorkbenchCustomShortcutRecord[] {
  if (!Array.isArray(raw)) {
    return [];
  }

  const seen = new Set<string>();
  const records: WorkbenchCustomShortcutRecord[] = [];

  for (const item of raw) {
    if (!item || typeof item !== 'object') {
      continue;
    }
    const routeName = String((item as WorkbenchCustomShortcutRecord).routeName ?? '').trim();
    if (!routeName || seen.has(routeName)) {
      continue;
    }
    seen.add(routeName);
    records.push({ routeName });
    if (records.length >= WORKBENCH_CUSTOM_SHORTCUT_MAX) {
      break;
    }
  }

  return records;
}

/** 读取当前用户已保存的自定义快捷入口 */
export function readWorkbenchCustomShortcuts(): WorkbenchCustomShortcutRecord[] {
  const { tenantId, userId } = resolveWorkbenchCustomShortcutScope();
  if (!tenantId || !userId) {
    return [];
  }

  const raw = localStorage.getItem(buildWorkbenchCustomShortcutStorageKey(tenantId, userId));
  if (!raw) {
    return [];
  }

  try {
    return normalizeRecords(JSON.parse(raw));
  } catch {
    return [];
  }
}

/** 覆盖保存当前用户的自定义快捷入口 */
export function writeWorkbenchCustomShortcuts(records: WorkbenchCustomShortcutRecord[]): void {
  const { tenantId, userId } = resolveWorkbenchCustomShortcutScope();
  if (!tenantId || !userId) {
    return;
  }

  const key = buildWorkbenchCustomShortcutStorageKey(tenantId, userId);
  const normalized = normalizeRecords(records);

  if (normalized.length === 0) {
    localStorage.removeItem(key);
    return;
  }

  localStorage.setItem(key, JSON.stringify(normalized));
}
