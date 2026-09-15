/**
 * 库存单据工作站：未保存草稿 / 编辑中追溯码的 localStorage 缓存。
 *
 * 按 userId + scene + docKey 隔离，避免盘点码串到调拨、进货串到出库。
 * 切单 / 切菜单 / 刷新均保留；主清理时机：保存落库成功迁键、删除单据、丢弃新建草稿。
 */

import { useUserStore } from '@vben/stores';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const STORAGE_PREFIX = 'openhis:inv-doc-draft';
const ENTRY_VERSION = 1 as const;

/** 常见场景常量（也可传自定义字符串，只要同页一致、跨业务不撞名） */
export const INV_DOC_DRAFT_SCENE = {
  warehousePurchaseInbound: 'warehouse-purchase-inbound',
  warehouseStockOut: 'warehouse-stock-out',
  warehouseReturnToSupplier: 'warehouse-return-to-supplier',
  warehouseStocktake: 'warehouse-stocktake',
  warehouseGainLoss: 'warehouse-gain-loss',
  warehouseTransferOut: 'warehouse-transfer-out',
  warehouseTransferIn: 'warehouse-transfer-in',
  pharmacyStockIn: 'pharmacy-stock-in',
  pharmacyReturnToWarehouse: 'pharmacy-return-to-warehouse',
  pharmacyStocktake: 'pharmacy-stocktake',
  pharmacyGainLoss: 'pharmacy-gain-loss',
  pharmacyTransferOut: 'pharmacy-transfer-out',
  pharmacyTransferIn: 'pharmacy-transfer-in',
  deptStocktake: 'dept-stocktake',
  deptGainLoss: 'dept-gain-loss',
  deptTransferOut: 'dept-transfer-out',
  deptTransferIn: 'dept-transfer-in',
  deptIssue: 'dept-issue',
  deptReturn: 'dept-return',
  deptIssueReturn: 'dept-issue-return',
} as const;

export type InvDocDraftScene =
  | (typeof INV_DOC_DRAFT_SCENE)[keyof typeof INV_DOC_DRAFT_SCENE]
  | string;

export interface InvDocDraftCacheEntry<TSnap = unknown> {
  version: typeof ENTRY_VERSION;
  scene: string;
  docKey: string;
  userId: string;
  updatedAt: number;
  /** 侧栏草稿卡片展示用（不含明细） */
  cardMeta?: Record<string, unknown>;
  snap: TSnap;
}

/** 解析当前登录用户主键（未登录时回落 local，避免整段缓存静默不写） */
export function resolveInvDocDraftUserId(): string {
  const userStore = useUserStore();
  const fromId = preserveApiIdForRequest(userStore.userInfo?.id);
  if (fromId) return fromId;
  const fromPractitioner = preserveApiIdForRequest(userStore.userInfo?.practitionerId);
  if (fromPractitioner) return `prac:${fromPractitioner}`;
  return 'local';
}

/** 组装单条草稿缓存键 */
export function buildInvDocDraftStorageKey(userId: string, scene: string, docKey: string): string {
  return `${STORAGE_PREFIX}:v${ENTRY_VERSION}:${userId}:${scene}:${docKey}`;
}

/** 组装场景索引键（该用户该场景下的 docKey 列表） */
export function buildInvDocDraftIndexKey(userId: string, scene: string): string {
  return `${STORAGE_PREFIX}:idx:v${ENTRY_VERSION}:${userId}:${scene}`;
}

/** 规范化 docKey；空则不可写入 */
export function normalizeInvDocDraftDocKey(raw: unknown): string {
  return String(raw ?? '').trim();
}

/** 是否为未落库客户端草稿 key */
export function isInvDocClientDraftKey(docKey: string): boolean {
  return docKey.startsWith('draft:');
}

/** 读取场景下已登记的 docKey 列表 */
function readSceneIndex(userId: string, scene: string): string[] {
  if (!userId || !scene) return [];
  try {
    const raw = localStorage.getItem(buildInvDocDraftIndexKey(userId, scene));
    if (!raw) return [];
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.map((k) => normalizeInvDocDraftDocKey(k)).filter((k) => k !== '');
  } catch {
    return [];
  }
}

/** 写回场景索引 */
function writeSceneIndex(userId: string, scene: string, keys: string[]): void {
  if (!userId || !scene) return;
  const unique = [...new Set(keys.map((k) => normalizeInvDocDraftDocKey(k)).filter(Boolean))];
  const indexKey = buildInvDocDraftIndexKey(userId, scene);
  if (unique.length === 0) {
    localStorage.removeItem(indexKey);
    return;
  }
  localStorage.setItem(indexKey, JSON.stringify(unique));
}

/** 将 docKey 登记到场景索引 */
function ensureIndexed(userId: string, scene: string, docKey: string): void {
  const keys = readSceneIndex(userId, scene);
  if (keys.includes(docKey)) return;
  writeSceneIndex(userId, scene, [...keys, docKey]);
}

/** 从场景索引移除 docKey */
function removeFromIndex(userId: string, scene: string, docKey: string): void {
  const keys = readSceneIndex(userId, scene).filter((k) => k !== docKey);
  writeSceneIndex(userId, scene, keys);
}

/** 行上取批号（兼容 batchNo / lotNumber） */
function resolveRowLot(row: Record<string, unknown>): string {
  return String(row.batchNo ?? row.lotNumber ?? '').trim();
}

/** 行上取追溯码 */
function resolveRowTrace(row: Record<string, unknown>): string {
  return String(row.traceNo ?? '').trim();
}

/**
 * 把缓存快照里的追溯码补进目标行（仅填空，不覆盖已有码）。
 * 匹配顺序：行 id → itemId|批号 → 仅 itemId（双方各仅一行时）。
 */
export function mergeInvDocTraceIntoRows<T extends Record<string, unknown>>(
  targetRows: T[],
  cachedRows: Record<string, unknown>[] | null | undefined,
): T[] {
  if (!cachedRows?.length || !targetRows.length) return targetRows;

  const byId = new Map<string, string>();
  const byItemLot = new Map<string, string>();
  const byItemId = new Map<string, string[]>();
  for (const row of cachedRows) {
    const trace = resolveRowTrace(row);
    if (!trace) continue;
    const id = String(row.id ?? '').trim();
    if (id) byId.set(id, trace);
    const itemId = String(row.itemId ?? '').trim();
    const lot = resolveRowLot(row);
    if (itemId && lot) byItemLot.set(`${itemId}|${lot}`, trace);
    if (itemId) {
      const list = byItemId.get(itemId) ?? [];
      list.push(trace);
      byItemId.set(itemId, list);
    }
  }
  if (byId.size === 0 && byItemLot.size === 0) return targetRows;

  const targetItemCounts = new Map<string, number>();
  for (const row of targetRows) {
    const itemId = String(row.itemId ?? '').trim();
    if (!itemId) continue;
    targetItemCounts.set(itemId, (targetItemCounts.get(itemId) ?? 0) + 1);
  }

  for (const row of targetRows) {
    if (resolveRowTrace(row)) continue;
    const byRowId = byId.get(String(row.id ?? '').trim());
    const itemId = String(row.itemId ?? '').trim();
    const lot = resolveRowLot(row);
    const byLot = itemId && lot ? byItemLot.get(`${itemId}|${lot}`) : undefined;
    let next = byRowId || byLot;
    if (!next && itemId && targetItemCounts.get(itemId) === 1) {
      const cached = byItemId.get(itemId);
      if (cached?.length === 1) next = cached[0];
    }
    if (!next) continue;
    (row as Record<string, unknown>).traceNo = next;
  }
  return targetRows;
}

/**
 * 写入（或覆盖）单据草稿缓存。
 * userId/scene/docKey 任一缺失时静默跳过。
 * 快照按调用方原样落盘（含空追溯码），以便用户清空后能覆盖旧缓存。
 */
export function writeInvDocDraftCache<TSnap>(params: {
  scene: InvDocDraftScene;
  docKey: string;
  snap: TSnap;
  cardMeta?: Record<string, unknown>;
  userId?: string;
}): void {
  const userId = params.userId?.trim() || resolveInvDocDraftUserId();
  const scene = String(params.scene ?? '').trim();
  const docKey = normalizeInvDocDraftDocKey(params.docKey);
  if (!userId || !scene || !docKey) return;

  const entry: InvDocDraftCacheEntry<TSnap> = {
    version: ENTRY_VERSION,
    scene,
    docKey,
    userId,
    updatedAt: Date.now(),
    cardMeta: params.cardMeta,
    snap: params.snap,
  };
  try {
    localStorage.setItem(buildInvDocDraftStorageKey(userId, scene, docKey), JSON.stringify(entry));
    ensureIndexed(userId, scene, docKey);
  } catch {
    // localStorage 满或不可用时不阻断业务
  }
}

/** 读取单条草稿缓存；不存在或解析失败返回 null */
export function readInvDocDraftCache<TSnap>(
  scene: InvDocDraftScene,
  docKey: string,
  userId?: string,
): InvDocDraftCacheEntry<TSnap> | null {
  const uid = userId?.trim() || resolveInvDocDraftUserId();
  const sc = String(scene ?? '').trim();
  const key = normalizeInvDocDraftDocKey(docKey);
  if (!uid || !sc || !key) return null;
  try {
    const raw = localStorage.getItem(buildInvDocDraftStorageKey(uid, sc, key));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as InvDocDraftCacheEntry<TSnap>;
    if (!parsed || parsed.version !== ENTRY_VERSION) return null;
    if (parsed.scene !== sc || parsed.docKey !== key) return null;
    return parsed;
  } catch {
    return null;
  }
}

/** 清除单条草稿缓存 */
export function clearInvDocDraftCache(
  scene: InvDocDraftScene,
  docKey: string,
  userId?: string,
): void {
  const uid = userId?.trim() || resolveInvDocDraftUserId();
  const sc = String(scene ?? '').trim();
  const key = normalizeInvDocDraftDocKey(docKey);
  if (!uid || !sc || !key) return;
  localStorage.removeItem(buildInvDocDraftStorageKey(uid, sc, key));
  removeFromIndex(uid, sc, key);
}

/** 清除该场景下所有「客户端草稿」缓存（draft:*），保留已落库 busNo 编辑缓存 */
export function clearInvDocClientDraftCachesForScene(
  scene: InvDocDraftScene,
  userId?: string,
): void {
  const uid = userId?.trim() || resolveInvDocDraftUserId();
  const sc = String(scene ?? '').trim();
  if (!uid || !sc) return;
  const keys = readSceneIndex(uid, sc);
  for (const docKey of keys) {
    if (isInvDocClientDraftKey(docKey)) {
      clearInvDocDraftCache(sc, docKey, uid);
    }
  }
}

/** 清除该场景全部缓存（草稿 + 已落库未保存编辑） */
export function clearAllInvDocDraftCachesForScene(scene: InvDocDraftScene, userId?: string): void {
  const uid = userId?.trim() || resolveInvDocDraftUserId();
  const sc = String(scene ?? '').trim();
  if (!uid || !sc) return;
  for (const docKey of readSceneIndex(uid, sc)) {
    clearInvDocDraftCache(sc, docKey, uid);
  }
}

/**
 * 保存成功时清理：客户端草稿 key + 落库 busNo key（若有）。
 */
export function clearInvDocDraftCacheAfterSave(params: {
  scene: InvDocDraftScene;
  clientDraftId?: string | null;
  busNo?: string | null;
  userId?: string;
}): void {
  const sc = String(params.scene ?? '').trim();
  if (!sc) return;
  const draftId = normalizeInvDocDraftDocKey(params.clientDraftId);
  const busNo = normalizeInvDocDraftDocKey(params.busNo);
  if (draftId) clearInvDocDraftCache(sc, draftId, params.userId);
  if (busNo) clearInvDocDraftCache(sc, busNo, params.userId);
}

/** 列出该场景下可用于侧栏恢复的客户端草稿（按更新时间倒序） */
export function listInvDocClientDraftCaches(
  scene: InvDocDraftScene,
  userId?: string,
): InvDocDraftCacheEntry[] {
  const uid = userId?.trim() || resolveInvDocDraftUserId();
  const sc = String(scene ?? '').trim();
  if (!uid || !sc) return [];
  const entries: InvDocDraftCacheEntry[] = [];
  for (const docKey of readSceneIndex(uid, sc)) {
    if (!isInvDocClientDraftKey(docKey)) continue;
    const entry = readInvDocDraftCache(sc, docKey, uid);
    if (entry) entries.push(entry);
  }
  entries.sort((a, b) => b.updatedAt - a.updatedAt);
  return entries;
}

/**
 * 将 localStorage 中的客户端草稿卡片合并进侧栏队列（不覆盖已有同 _clientDraftId）。
 * @returns 本次新插入的草稿行（可用于自动选中）
 */
export function mergeInvDocDraftCardsIntoQueue(params: {
  scene: InvDocDraftScene;
  orderList: Record<string, unknown>[];
  buildDraftRow: (
    clientDraftId: string,
    cardMeta?: Record<string, unknown>,
  ) => Record<string, unknown>;
}): { orderList: Record<string, unknown>[]; inserted: Record<string, unknown>[] } {
  const existingIds = new Set(
    params.orderList
      .filter((r) => r._draft)
      .map((r) => normalizeInvDocDraftDocKey(r._clientDraftId))
      .filter(Boolean),
  );
  const inserted: Record<string, unknown>[] = [];
  for (const entry of listInvDocClientDraftCaches(params.scene)) {
    if (existingIds.has(entry.docKey)) continue;
    const row = params.buildDraftRow(entry.docKey, entry.cardMeta);
    inserted.push(row);
    existingIds.add(entry.docKey);
  }
  if (inserted.length === 0) {
    return { orderList: params.orderList, inserted };
  }
  return {
    orderList: [...inserted, ...params.orderList],
    inserted,
  };
}
