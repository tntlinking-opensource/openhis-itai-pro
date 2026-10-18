import { useUserStore } from '@zxplat/stores';

import { useAuthStore } from '#/store';

const STORAGE_PREFIX = 'zxplat:prt:printer-prefs';
/** 本机共用打印机偏好（不跟登录用户走；护士站多账号轮班同一台电脑） */
const DEVICE_SCOPE = 'device';

export interface PrinterPreference {
  printerName: string;
  skipPrompt: boolean;
  updatedAt: string;
}

type PrinterPreferenceStore = Record<string, PrinterPreference>;

/** 本机打印机偏好 localStorage 键（按租户隔离，全员共用） */
export function buildPrinterPreferenceStorageKey(tenantId: string): string {
  return `${STORAGE_PREFIX}:${tenantId}:${DEVICE_SCOPE}`;
}

/** 从 Pinia 解析租户与用户主键（纸张等仍按用户；打印机已改为本机键） */
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

function parseStore(raw: null | string): PrinterPreferenceStore {
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

function prefUpdatedAtMs(pref: PrinterPreference | undefined): number {
  const raw = pref?.updatedAt ?? '';
  if (!raw) {
    return 0;
  }
  const ms = Date.parse(raw);
  return Number.isFinite(ms) ? ms : 0;
}

/** 把本机上旧的「按用户」缓存合并进本机键（同一场景取较新的） */
function migrateLegacyUserStores(tenantId: string): PrinterPreferenceStore {
  const deviceKey = buildPrinterPreferenceStorageKey(tenantId);
  const prefix = `${STORAGE_PREFIX}:${tenantId}:`;
  const merged: PrinterPreferenceStore = {};
  for (let i = 0; i < localStorage.length; i += 1) {
    const key = localStorage.key(i);
    if (!key || key === deviceKey || !key.startsWith(prefix)) {
      continue;
    }
    const userId = key.slice(prefix.length);
    if (!userId || userId === DEVICE_SCOPE) {
      continue;
    }
    const legacy = parseStore(localStorage.getItem(key));
    for (const [code, pref] of Object.entries(legacy)) {
      if (!pref?.printerName?.trim()) {
        continue;
      }
      const current = merged[code];
      if (!current || prefUpdatedAtMs(pref) >= prefUpdatedAtMs(current)) {
        merged[code] = {
          printerName: pref.printerName.trim(),
          skipPrompt: pref.skipPrompt !== false,
          updatedAt: pref.updatedAt ?? '',
        };
      }
    }
  }
  return merged;
}

function readStore(tenantId: string): PrinterPreferenceStore {
  if (!tenantId) {
    return {};
  }
  const key = buildPrinterPreferenceStorageKey(tenantId);
  const raw = localStorage.getItem(key);
  if (raw !== null) {
    return parseStore(raw);
  }
  const migrated = migrateLegacyUserStores(tenantId);
  // 写出本机键（含空对象），避免清打印机后又被旧用户键冲回来
  localStorage.setItem(key, JSON.stringify(migrated));
  return migrated;
}

function writeStore(tenantId: string, store: PrinterPreferenceStore): void {
  if (!tenantId) {
    return;
  }
  const key = buildPrinterPreferenceStorageKey(tenantId);
  localStorage.setItem(key, JSON.stringify(store));
}

/** 读取某业务场景的打印机偏好（本机共用） */
export function getPrinterPreference(bindingCode: string): null | PrinterPreference {
  const code = bindingCode.trim();
  if (!code) {
    return null;
  }
  const { tenantId } = resolvePrinterPreferenceScope();
  const pref = readStore(tenantId)[code];
  if (!pref?.printerName?.trim()) {
    return null;
  }
  return {
    printerName: pref.printerName.trim(),
    skipPrompt: pref.skipPrompt !== false,
    updatedAt: pref.updatedAt ?? '',
  };
}

/** 保存或更新某业务场景的打印机偏好（本机共用） */
export function savePrinterPreference(bindingCode: string, pref: PrinterPreference): void {
  const code = bindingCode.trim();
  const printerName = pref.printerName?.trim();
  if (!code || !printerName) {
    return;
  }
  const { tenantId } = resolvePrinterPreferenceScope();
  const store = readStore(tenantId);
  store[code] = {
    printerName,
    skipPrompt: pref.skipPrompt === true,
    updatedAt: pref.updatedAt || new Date().toISOString(),
  };
  writeStore(tenantId, store);
}

/** 恢复每次询问：保留上次打印机供预填，但不再静默直打 */
export function setPrinterPromptEnabled(bindingCode: string, enabled: boolean): void {
  const code = bindingCode.trim();
  if (!code) {
    return;
  }
  const { tenantId } = resolvePrinterPreferenceScope();
  const store = readStore(tenantId);
  const existing = store[code];
  if (!existing?.printerName?.trim()) {
    return;
  }
  store[code] = {
    ...existing,
    skipPrompt: !enabled,
    updatedAt: new Date().toISOString(),
  };
  writeStore(tenantId, store);
}

/** 清除某场景的默认打印机 */
export function clearPrinterPreference(bindingCode: string): void {
  const code = bindingCode.trim();
  if (!code) {
    return;
  }
  const { tenantId } = resolvePrinterPreferenceScope();
  const store = readStore(tenantId);
  if (!(code in store)) {
    return;
  }
  const { [code]: _removed, ...rest } = store;
  writeStore(tenantId, rest);
}

/** 读取本机全部场景的打印机偏好 */
export function listPrinterPreferences(): PrinterPreferenceStore {
  const { tenantId } = resolvePrinterPreferenceScope();
  return readStore(tenantId);
}
