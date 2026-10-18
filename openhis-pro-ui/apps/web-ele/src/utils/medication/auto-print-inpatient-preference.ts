import { useUserStore } from '@zxplat/stores';

import { useAuthStore } from '#/store';

const ENABLED_VALUE = '1';

/** 住院发药：核发成功后自动打印（汇总单 / 按患者发药单） */
const IP_DISPENSE_PREFIX = 'zxplat:pharmacy:ip-dispense-auto-print';
/** 住院退药：确认退药成功后自动打印退药单 */
const IP_RETURN_PREFIX = 'zxplat:pharmacy:ip-return-auto-print';

/** 当前登录上下文下的 localStorage 键 */
function buildStorageKey(prefix: string, tenantId: string, userId: string): string {
  return `${prefix}:${tenantId}:${userId}`;
}

/** 从 Pinia 解析租户与用户主键（未登录时返回空串） */
function resolvePreferenceScope(): { tenantId: string; userId: string } {
  const authStore = useAuthStore();
  const userStore = useUserStore();
  const tenantId = authStore.getSelectedTenant()?.trim() ?? '';
  const userId =
    userStore.userInfo?.id === null || userStore.userInfo?.id === undefined
      ? ''
      : String(userStore.userInfo.id);
  return { tenantId, userId };
}

/** 读取当前用户开关；无缓存或未登录时默认关闭 */
function readPreference(prefix: string): boolean {
  const { tenantId, userId } = resolvePreferenceScope();
  if (!tenantId || !userId) {
    return false;
  }
  return localStorage.getItem(buildStorageKey(prefix, tenantId, userId)) === ENABLED_VALUE;
}

/** 保存当前用户开关 */
function writePreference(prefix: string, enabled: boolean): void {
  const { tenantId, userId } = resolvePreferenceScope();
  if (!tenantId || !userId) {
    return;
  }
  localStorage.setItem(buildStorageKey(prefix, tenantId, userId), enabled ? ENABLED_VALUE : '0');
}

/** 读取「住院发药后自动打印」；无缓存时默认关闭 */
export function readInpatientDispenseAutoPrintPreference(): boolean {
  return readPreference(IP_DISPENSE_PREFIX);
}

/** 保存「住院发药后自动打印」 */
export function writeInpatientDispenseAutoPrintPreference(enabled: boolean): void {
  writePreference(IP_DISPENSE_PREFIX, enabled);
}

/** 读取「住院退药后自动打印」；无缓存时默认关闭 */
export function readInpatientReturnAutoPrintPreference(): boolean {
  return readPreference(IP_RETURN_PREFIX);
}

/** 保存「住院退药后自动打印」 */
export function writeInpatientReturnAutoPrintPreference(enabled: boolean): void {
  writePreference(IP_RETURN_PREFIX, enabled);
}
