import { useUserStore } from '@vben/stores';

import { useAuthStore } from '#/store';

const STORAGE_PREFIX = 'openhis:prt:printer-prefs';

export interface PrinterPreference {
  printerName: string;
  skipPrompt: boolean;
  updatedAt: string;
}

type PrinterPreferenceStore = Record<string, PrinterPreference>;

/** 当前登录上下文下的 localStorage 键 */
export function buildPrinterPreferenceStorageKey(tenantId: string, userId: string): string {
  return `${STORAGE_PREFIX}:${tenantId}:${userId}`;
}

/** 从 Pinia 解析租户与用户主键（未登录时返回空串） */
export function resolvePrinterPreferenceScope(): { tenantId: string; userId: string } {
  const authStore = useAuthStore();
  const userStore = useUserStore();
  const tenantId = authStore.getSelectedTenant()?.trim() ?? '';
  const userId =
    userStore.userInfo?.id === null || userStore.userInfo?.id === undefined
      ? ''
      : String(userStore.userInfo.id);
  return { tenantId, userId };
}

function readStore(tenantId: string, userId: string): PrinterPreferenceStore {
  if (!tenantId || !userId) {
    return {};
  }
  const raw = localStorage.getItem(buildPrinterPreferenceStorageKey(tenantId, userId));
  if (!raw) {
    return {};
  }
  try {
    const parsed = JSON.parse(raw) as PrinterPreferenceStore;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function writeStore(tenantId: string, userId: string, store: PrinterPreferenceStore): void {
  if (!tenantId || !userId) {
    return;
  }
  const key = buildPrinterPreferenceStorageKey(tenantId, userId);
  if (Object.keys(store).length === 0) {
    localStorage.removeItem(key);
    return;
  }
  localStorage.setItem(key, JSON.stringify(store));
}

/** 读取某业务场景的打印机偏好 */
export function getPrinterPreference(bindingCode: string): null | PrinterPreference {
  const code = bindingCode.trim();
  if (!code) {
    return null;
  }
  const { tenantId, userId } = resolvePrinterPreferenceScope();
  const pref = readStore(tenantId, userId)[code];
  if (!pref?.printerName?.trim()) {
    return null;
  }
  return {
    printerName: pref.printerName.trim(),
    skipPrompt: pref.skipPrompt !== false,
    updatedAt: pref.updatedAt ?? '',
  };
}

/** 保存或更新某业务场景的打印机偏好 */
export function savePrinterPreference(bindingCode: string, pref: PrinterPreference): void {
  const code = bindingCode.trim();
  const printerName = pref.printerName?.trim();
  if (!code || !printerName) {
    return;
  }
  const { tenantId, userId } = resolvePrinterPreferenceScope();
  const store = readStore(tenantId, userId);
  store[code] = {
    printerName,
    skipPrompt: pref.skipPrompt === true,
    updatedAt: pref.updatedAt || new Date().toISOString(),
  };
  writeStore(tenantId, userId, store);
}

/** 恢复每次询问：保留上次打印机供预填，但不再静默直打 */
export function setPrinterPromptEnabled(bindingCode: string, enabled: boolean): void {
  const code = bindingCode.trim();
  if (!code) {
    return;
  }
  const { tenantId, userId } = resolvePrinterPreferenceScope();
  const store = readStore(tenantId, userId);
  const existing = store[code];
  if (!existing?.printerName?.trim()) {
    return;
  }
  store[code] = {
    ...existing,
    skipPrompt: !enabled,
    updatedAt: new Date().toISOString(),
  };
  writeStore(tenantId, userId, store);
}

/** 清除某场景的默认打印机 */
export function clearPrinterPreference(bindingCode: string): void {
  const code = bindingCode.trim();
  if (!code) {
    return;
  }
  const { tenantId, userId } = resolvePrinterPreferenceScope();
  const store = readStore(tenantId, userId);
  if (!(code in store)) {
    return;
  }
  const { [code]: _removed, ...rest } = store;
  writeStore(tenantId, userId, rest);
}

/** 读取当前用户全部场景的打印机偏好 */
export function listPrinterPreferences(): PrinterPreferenceStore {
  const { tenantId, userId } = resolvePrinterPreferenceScope();
  return readStore(tenantId, userId);
}
