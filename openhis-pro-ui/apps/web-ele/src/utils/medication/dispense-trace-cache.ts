/**
 * 发药工作站：追溯码（及门诊库房选择）localStorage 缓存。
 * 按 userId + scene + encounterId 隔离，防误切菜单白扫；发药成功后按行清理。
 * 与库存单据 inv-doc-draft 缓存前缀隔离，互不影响。
 */

import { useUserStore } from '@zxplat/stores';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const STORAGE_PREFIX = 'zxplat:dispense-trace';
const ENTRY_VERSION = 1 as const;

export const DISPENSE_TRACE_CACHE_SCENE = {
  outpatient: 'outpatient',
  inpatient: 'inpatient',
} as const;

export type DispenseTraceCacheScene =
  | (typeof DISPENSE_TRACE_CACHE_SCENE)[keyof typeof DISPENSE_TRACE_CACHE_SCENE]
  | string;

export interface DispenseTraceCacheSnap {
  traceCodeByLineId: Record<string, string>;
  /** 门诊发药：库房×批号选择；住院可缺省 */
  inventoryPickByLineId?: Record<string, string>;
}

export interface DispenseTraceCacheEntry {
  version: typeof ENTRY_VERSION;
  scene: string;
  encounterId: string;
  userId: string;
  updatedAt: number;
  snap: DispenseTraceCacheSnap;
}

/** 解析当前登录用户主键（未登录回落 local） */
export function resolveDispenseTraceCacheUserId(): string {
  const userStore = useUserStore();
  const fromId = preserveApiIdForRequest(userStore.userInfo?.id);
  if (fromId) return fromId;
  const fromPractitioner = preserveApiIdForRequest(userStore.userInfo?.practitionerId);
  if (fromPractitioner) return `prac:${fromPractitioner}`;
  return 'local';
}

/** 组装单条发药扫码缓存键 */
export function buildDispenseTraceStorageKey(
  userId: string,
  scene: string,
  encounterId: string,
): string {
  return `${STORAGE_PREFIX}:v${ENTRY_VERSION}:${userId}:${scene}:${encounterId}`;
}

/** 规范化 encounterId */
export function normalizeDispenseEncounterId(raw: unknown): string {
  return String(raw ?? '').trim();
}

/** 是否有需要落盘的内容 */
function hasDispenseTraceSnapContent(snap: DispenseTraceCacheSnap): boolean {
  if (Object.keys(snap.traceCodeByLineId).length > 0) return true;
  if (snap.inventoryPickByLineId && Object.keys(snap.inventoryPickByLineId).length > 0) {
    return true;
  }
  return false;
}

/**
 * 写入（或覆盖）某就诊的发药扫码缓存。
 * snap 为空时改为删除该 key。
 */
export function writeDispenseTraceCache(params: {
  scene: DispenseTraceCacheScene;
  encounterId: string;
  snap: DispenseTraceCacheSnap;
  userId?: string;
}): void {
  const userId = params.userId?.trim() || resolveDispenseTraceCacheUserId();
  const scene = String(params.scene ?? '').trim();
  const encounterId = normalizeDispenseEncounterId(params.encounterId);
  if (!userId || !scene || !encounterId) return;

  if (!hasDispenseTraceSnapContent(params.snap)) {
    clearDispenseTraceCache(scene, encounterId, userId);
    return;
  }

  const entry: DispenseTraceCacheEntry = {
    version: ENTRY_VERSION,
    scene,
    encounterId,
    userId,
    updatedAt: Date.now(),
    snap: {
      traceCodeByLineId: { ...params.snap.traceCodeByLineId },
      inventoryPickByLineId: params.snap.inventoryPickByLineId
        ? { ...params.snap.inventoryPickByLineId }
        : undefined,
    },
  };
  try {
    localStorage.setItem(
      buildDispenseTraceStorageKey(userId, scene, encounterId),
      JSON.stringify(entry),
    );
  } catch {
    // localStorage 不可用时不阻断发药
  }
}

/** 读取某就诊的发药扫码缓存 */
export function readDispenseTraceCache(
  scene: DispenseTraceCacheScene,
  encounterId: string,
  userId?: string,
): DispenseTraceCacheSnap | null {
  const uid = userId?.trim() || resolveDispenseTraceCacheUserId();
  const sc = String(scene ?? '').trim();
  const enc = normalizeDispenseEncounterId(encounterId);
  if (!uid || !sc || !enc) return null;
  try {
    const raw = localStorage.getItem(buildDispenseTraceStorageKey(uid, sc, enc));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as DispenseTraceCacheEntry;
    if (!parsed || parsed.version !== ENTRY_VERSION) return null;
    if (parsed.scene !== sc || parsed.encounterId !== enc) return null;
    return {
      traceCodeByLineId: { ...(parsed.snap?.traceCodeByLineId ?? {}) },
      inventoryPickByLineId: parsed.snap?.inventoryPickByLineId
        ? { ...parsed.snap.inventoryPickByLineId }
        : undefined,
    };
  } catch {
    return null;
  }
}

/** 清除某就诊的发药扫码缓存 */
export function clearDispenseTraceCache(
  scene: DispenseTraceCacheScene,
  encounterId: string,
  userId?: string,
): void {
  const uid = userId?.trim() || resolveDispenseTraceCacheUserId();
  const sc = String(scene ?? '').trim();
  const enc = normalizeDispenseEncounterId(encounterId);
  if (!uid || !sc || !enc) return;
  localStorage.removeItem(buildDispenseTraceStorageKey(uid, sc, enc));
}

/**
 * 发药成功后：从缓存中剔除已核发行；若已空则删 key。
 */
export function removeDispenseTraceCacheLines(params: {
  scene: DispenseTraceCacheScene;
  encounterId: string;
  lineIds: string[];
  userId?: string;
}): void {
  const enc = normalizeDispenseEncounterId(params.encounterId);
  if (!enc || params.lineIds.length === 0) return;
  const existing = readDispenseTraceCache(params.scene, enc, params.userId);
  if (!existing) return;
  const idSet = new Set(params.lineIds.map((id) => String(id)));
  const nextTrace: Record<string, string> = {};
  for (const [id, code] of Object.entries(existing.traceCodeByLineId)) {
    if (!idSet.has(id)) nextTrace[id] = code;
  }
  const nextPick: Record<string, string> = {};
  for (const [id, pick] of Object.entries(existing.inventoryPickByLineId ?? {})) {
    if (!idSet.has(id)) nextPick[id] = pick;
  }
  writeDispenseTraceCache({
    scene: params.scene,
    encounterId: enc,
    snap: {
      traceCodeByLineId: nextTrace,
      inventoryPickByLineId: Object.keys(nextPick).length > 0 ? nextPick : undefined,
    },
    userId: params.userId,
  });
}
