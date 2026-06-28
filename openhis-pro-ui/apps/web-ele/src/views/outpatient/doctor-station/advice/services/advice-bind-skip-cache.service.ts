import type {
  DoctorStationAdviceDraftBindItemDto,
  DoctorStationAdviceDraftLineRequest,
} from '#/api/doctor-station/advice.types';

import { useUserStore } from '@vben/stores';

import { MEDICATION_DEFINITION_TABLE } from './advice-tab.constants';

export const USAGE_BIND_TYPE = 'USAGE';
export const ACTIVITY_BIND_TYPE = 'ACTIVITY';

const STORAGE_KEY_PREFIX = 'openhis:doctor-station:advice-bind-skip:';

/** 绑定来源缓存键：USAGE|{methodCode} / ACTIVITY|{activityDefinitionId} */
export type AdviceBindSourceCacheKey = string;

export interface AdviceBindSkipCacheEntry {
  sourceCacheKey: AdviceBindSourceCacheKey;
  sourceLabel: string;
}

/** 绑定来源类型展示：用法 / 诊疗 */
export function resolveBindSourceKindLabel(
  bindType: string | undefined,
  sourceCacheKey?: string,
): string {
  const type = String(bindType ?? '').trim();
  if (type === USAGE_BIND_TYPE) {
    return '用法';
  }
  if (type === ACTIVITY_BIND_TYPE) {
    return '诊疗';
  }
  const key = String(sourceCacheKey ?? '').trim();
  if (key.startsWith(`${USAGE_BIND_TYPE}|`)) {
    return '用法';
  }
  if (key.startsWith(`${ACTIVITY_BIND_TYPE}|`)) {
    return '诊疗';
  }
  return '';
}

function storageKeyForUser(userId: string): string {
  return `${STORAGE_KEY_PREFIX}${userId}`;
}

/** 当前登录用户 id（Long 字符串） */
export function resolveAdviceBindSkipCacheUserId(): string {
  const userStore = useUserStore();
  const raw =
    preserveApiIdFromUser(userStore.userInfo?.userId) ??
    preserveApiIdFromUser(userStore.userInfo?.id);
  return raw ?? '';
}

function preserveApiIdFromUser(raw: unknown): string | undefined {
  if (raw === undefined || raw === null) {
    return undefined;
  }
  const s = String(raw).trim();
  return s || undefined;
}

function readSourceKeys(userId: string): Set<string> {
  if (!userId) {
    return new Set();
  }
  try {
    const raw = localStorage.getItem(storageKeyForUser(userId));
    if (!raw) {
      return new Set();
    }
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return new Set();
    }
    return new Set(parsed.map((item) => String(item ?? '').trim()).filter(Boolean));
  } catch {
    return new Set();
  }
}

function writeSourceKeys(userId: string, keys: Set<string>): void {
  if (!userId) {
    return;
  }
  if (keys.size === 0) {
    localStorage.removeItem(storageKeyForUser(userId));
    return;
  }
  localStorage.setItem(storageKeyForUser(userId), JSON.stringify([...keys]));
}

/** IF-DOR-23 绑定项 → 来源缓存键 */
export function resolveBindSourceCacheKey(
  item: DoctorStationAdviceDraftBindItemDto,
  lines: DoctorStationAdviceDraftLineRequest[],
): AdviceBindSourceCacheKey | undefined {
  const bindType = String(item.bindType ?? '').trim();
  const lineIndex = item.triggerLineIndex;
  if (lineIndex === undefined || lineIndex === null) {
    return undefined;
  }
  const line = lines[lineIndex];
  if (!line) {
    return undefined;
  }

  if (bindType === USAGE_BIND_TYPE) {
    const productTableName = String(line.productTableName ?? '').trim();
    if (productTableName !== MEDICATION_DEFINITION_TABLE) {
      return undefined;
    }
    const methodCode = String(line.medicationPayload?.methodCode ?? '').trim();
    return methodCode ? `${USAGE_BIND_TYPE}|${methodCode}` : undefined;
  }

  if (bindType === ACTIVITY_BIND_TYPE) {
    const activityDefinitionId = String(line.servicePayload?.activityDefinitionId ?? '').trim();
    return activityDefinitionId ? `${ACTIVITY_BIND_TYPE}|${activityDefinitionId}` : undefined;
  }

  return undefined;
}

/** 来源是否已设置「不再提示」 */
export function isAdviceBindSourceSkipped(
  sourceCacheKey: AdviceBindSourceCacheKey | undefined,
  userId?: string,
): boolean {
  const key = String(sourceCacheKey ?? '').trim();
  const uid = userId ?? resolveAdviceBindSkipCacheUserId();
  if (!key || !uid) {
    return false;
  }
  return readSourceKeys(uid).has(key);
}

/** 写入来源「不再提示」 */
export function addAdviceBindSkipSources(
  sourceCacheKeys: AdviceBindSourceCacheKey[],
  userId?: string,
): void {
  const uid = userId ?? resolveAdviceBindSkipCacheUserId();
  if (!uid) {
    return;
  }
  const keys = readSourceKeys(uid);
  for (const item of sourceCacheKeys) {
    const k = String(item ?? '').trim();
    if (k) {
      keys.add(k);
    }
  }
  writeSourceKeys(uid, keys);
}

/** 移除单个来源缓存 */
export function removeAdviceBindSkipSource(
  sourceCacheKey: AdviceBindSourceCacheKey,
  userId?: string,
): void {
  const uid = userId ?? resolveAdviceBindSkipCacheUserId();
  const k = String(sourceCacheKey ?? '').trim();
  if (!uid || !k) {
    return;
  }
  const keys = readSourceKeys(uid);
  keys.delete(k);
  writeSourceKeys(uid, keys);
}

/** 清空当前用户全部绑定来源缓存 */
export function clearAdviceBindSkipCache(userId?: string): void {
  const uid = userId ?? resolveAdviceBindSkipCacheUserId();
  if (!uid) {
    return;
  }
  localStorage.removeItem(storageKeyForUser(uid));
}

/** 列出当前用户已缓存来源（仅 key，展示名由调用方解析） */
export function listAdviceBindSkipSourceKeys(userId?: string): AdviceBindSourceCacheKey[] {
  const uid = userId ?? resolveAdviceBindSkipCacheUserId();
  if (!uid) {
    return [];
  }
  return [...readSourceKeys(uid)];
}
