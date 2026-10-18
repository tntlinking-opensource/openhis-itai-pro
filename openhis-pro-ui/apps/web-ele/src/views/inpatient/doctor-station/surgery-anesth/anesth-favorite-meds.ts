import type { AdviceProductOption } from '#/views/inpatient/doctor-station/advice/services/advice.types';
import type { AnesthFavoriteKind } from '#/views/inpatient/doctor-station/surgery-anesth/anesth-favorite-defaults.config';

import { reactive } from 'vue';

import { useUserStore } from '@zxplat/stores';

import { useAuthStore } from '#/store';
import { ANESTH_FAVORITE_SEEDS } from '#/views/inpatient/doctor-station/surgery-anesth/anesth-favorite-defaults.config';

export type { AnesthFavoriteKind };

/** 常用药条数上限（本机缓存，不跟账号同步到别的电脑） */
export const ANESTH_FAVORITE_MED_LIMIT = 20;

/** 打开下拉时按编码回查库存的内存有效期 */
const LIVE_TTL_MS = 30_000;

/**
 * 本机常用麻醉药快照。
 * 只留选品和展示用的稳定字段；库存、单价、医保等级打开下拉时再向目录要。
 */
export interface AnesthFavoriteMedSnapshot {
  id: string;
  productDefinitionId: string;
  code: string;
  name: string;
  merchandiseName: string;
  spec: string;
  category: string;
  pinnedAt: number;
}

const buckets = reactive<Record<string, AnesthFavoriteMedSnapshot[]>>({});

const liveCache = new Map<string, { at: number; row: AdviceProductOption | 'missing' }>();

/** 当前登录用户 + 机构；主键保持字符串，避免长整型被抹平 */
export function resolveAnesthFavoriteOwner(): { organizationId: string; userId: string } {
  const userStore = useUserStore();
  const authStore = useAuthStore();
  const info = userStore.userInfo as null | { id?: unknown; userId?: unknown };
  const userId = String(info?.userId ?? info?.id ?? '').trim();
  const organizationId = String(authStore.getSelectedOrganization() ?? '').trim();
  return { userId, organizationId };
}

function storageKey(userId: string, organizationId: string, kind: AnesthFavoriteKind): string {
  return `zxplat.anesth.fav-meds.v2.${kind}.${userId}.${organizationId || 'none'}`;
}

function liveCacheKey(organizationId: string, id: string): string {
  return `${organizationId || 'none'}\n${id}`;
}

function isSnapshot(raw: unknown): raw is AnesthFavoriteMedSnapshot {
  if (!raw || typeof raw !== 'object') {
    return false;
  }
  const row = raw as Record<string, unknown>;
  const id = String(row.id ?? '').trim();
  const name = String(row.name ?? '').trim();
  if (!id || !name) {
    return false;
  }
  return true;
}

function normalizeSnapshot(raw: AnesthFavoriteMedSnapshot): AnesthFavoriteMedSnapshot {
  const pinnedAt = Number(raw.pinnedAt);
  return {
    id: String(raw.id).trim(),
    productDefinitionId: String(raw.productDefinitionId ?? '').trim(),
    code: String(raw.code ?? '').trim(),
    name: String(raw.name ?? '').trim(),
    merchandiseName: String(raw.merchandiseName ?? '').trim(),
    spec: String(raw.spec ?? '').trim(),
    category: String(raw.category ?? '').trim(),
    pinnedAt: Number.isFinite(pinnedAt) ? pinnedAt : 0,
  };
}

function seedSnapshots(kind: AnesthFavoriteKind): AnesthFavoriteMedSnapshot[] {
  const seeds = ANESTH_FAVORITE_SEEDS.filter((item) => item.kind === kind);
  return seeds.map((item, index) => ({
    id: `seed:${kind}:${item.code}`,
    productDefinitionId: '',
    code: item.code,
    name: item.code,
    merchandiseName: '',
    spec: '',
    category: kind === 'service' ? '麻醉' : '',
    pinnedAt: index + 1,
  }));
}

function seedMarkKey(userId: string, organizationId: string, kind: AnesthFavoriteKind): string {
  return `zxplat.anesth.fav-seed.v5.${kind}.${userId}.${organizationId || 'none'}`;
}

/** 国基曾预置过的麻醉药品编码；v4 起不再默认，迁移时从本机常用里拿掉 */
const RETIRED_MEDICATION_SEED_CODES = ['962', '980', '963', '964', '965'];

function readStored(key: string): AnesthFavoriteMedSnapshot[] {
  const raw = localStorage.getItem(key);
  if (!raw) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .filter(isSnapshot)
      .map(normalizeSnapshot)
      .sort((a, b) => a.pinnedAt - b.pinnedAt)
      .slice(0, ANESTH_FAVORITE_MED_LIMIT);
  } catch {
    return [];
  }
}

function persistBucket(key: string): void {
  const list = buckets[key] ?? [];
  localStorage.setItem(key, JSON.stringify(list));
}

/**
 * 当前用户某一类常用（药品 / 麻醉项目）。
 * 本机还没做过预置时，把院点默认编码插到最前面；未登录不落缓存。
 */
export function anesthFavoriteBucket(
  userId: string,
  organizationId: string,
  kind: AnesthFavoriteKind,
): AnesthFavoriteMedSnapshot[] {
  if (!userId) {
    return [];
  }
  const key = storageKey(userId, organizationId, kind);
  const existing = buckets[key];
  if (existing) {
    return existing;
  }
  buckets[key] = applyDefaultSeeds(key, userId, organizationId, kind, readStored(key));
  return buckets[key];
}

function applyDefaultSeeds(
  key: string,
  userId: string,
  organizationId: string,
  kind: AnesthFavoriteKind,
  items: AnesthFavoriteMedSnapshot[],
): AnesthFavoriteMedSnapshot[] {
  const mark = seedMarkKey(userId, organizationId, kind);
  if (localStorage.getItem(mark) === '1') {
    return items;
  }
  const merged = seedSnapshots(kind);
  for (const item of items) {
    const exists = merged.some((seed) => sameCode(item.code, seed.code));
    if (exists) {
      continue;
    }
    if (
      kind === 'medication' &&
      RETIRED_MEDICATION_SEED_CODES.some((code) => sameCode(item.code, code))
    ) {
      continue;
    }
    merged.push(item);
  }
  rewriteFavoriteOrder(merged);
  localStorage.setItem(key, JSON.stringify(merged));
  localStorage.setItem(mark, '1');
  return merged;
}

function sameCode(left: string, right: string): boolean {
  const a = left.trim().toLowerCase();
  const b = right.trim().toLowerCase();
  if (a !== '' && a === b) {
    return true;
  }
  if (!/^\d+$/.test(b)) {
    return false;
  }
  const digits = a.replace(/\D/g, '').replace(/^0+/, '');
  const wanted = b.replace(/^0+/, '');
  return digits !== '' && digits === wanted;
}

function sameFavorite(item: AnesthFavoriteMedSnapshot, row: AdviceProductOption): boolean {
  if (!item.id.startsWith('seed:') && item.id === String(row.id)) {
    return true;
  }
  const defId = String(row.productDefinitionId ?? '').trim();
  if (defId !== '' && defId === item.productDefinitionId) {
    return true;
  }
  return sameCode(String(row.code ?? ''), item.code);
}

/** 选品行是否就是这条常用（含预置编码，纯数字编码忽略 MD 前缀和前导零） */
export function anesthFavoriteRowMatches(
  item: AnesthFavoriteMedSnapshot,
  row: AdviceProductOption,
): boolean {
  return sameFavorite(item, row);
}

export function isAnesthFavoriteMed(
  userId: string,
  organizationId: string,
  kind: AnesthFavoriteKind,
  row: AdviceProductOption,
): boolean {
  return anesthFavoriteBucket(userId, organizationId, kind).some((item) => sameFavorite(item, row));
}

function snapshotFromOption(row: AdviceProductOption): AnesthFavoriteMedSnapshot {
  return {
    id: String(row.id),
    productDefinitionId: String(row.productDefinitionId ?? '').trim(),
    code: String(row.code ?? '').trim(),
    name: String(row.name ?? '').trim(),
    merchandiseName: String(row.merchandiseName ?? '').trim(),
    spec: String(row.spec ?? '').trim(),
    category: String(row.category ?? '').trim(),
    pinnedAt: Date.now(),
  };
}

/**
 * 标星或取消。已在列表中则取消；超过上限返回 limit，不写入。
 */
export function toggleAnesthFavoriteMed(
  userId: string,
  organizationId: string,
  kind: AnesthFavoriteKind,
  row: AdviceProductOption,
): 'added' | 'limit' | 'removed' {
  const list = anesthFavoriteBucket(userId, organizationId, kind);
  const index = list.findIndex((item) => sameFavorite(item, row));
  if (index >= 0) {
    list.splice(index, 1);
    rewriteFavoriteOrder(list);
    persistBucket(storageKey(userId, organizationId, kind));
    return 'removed';
  }
  if (list.length >= ANESTH_FAVORITE_MED_LIMIT) {
    return 'limit';
  }
  const next = snapshotFromOption(row);
  const last = list[list.length - 1];
  next.pinnedAt = last ? last.pinnedAt + 1 : 1;
  list.push(next);
  persistBucket(storageKey(userId, organizationId, kind));
  return 'added';
}

/** 从常用块取消，不需要整行目录数据 */
export function removeAnesthFavoriteMed(
  userId: string,
  organizationId: string,
  kind: AnesthFavoriteKind,
  id: string,
): void {
  const list = anesthFavoriteBucket(userId, organizationId, kind);
  const index = list.findIndex((item) => item.id === id);
  if (index < 0) {
    return;
  }
  list.splice(index, 1);
  rewriteFavoriteOrder(list);
  persistBucket(storageKey(userId, organizationId, kind));
}

/**
 * 上移或下移一条常用。delta=-1 上移，1 下移。
 * 已在两端时不改。
 */
export function shiftAnesthFavorite(
  userId: string,
  organizationId: string,
  kind: AnesthFavoriteKind,
  id: string,
  delta: -1 | 1,
): void {
  const list = anesthFavoriteBucket(userId, organizationId, kind);
  const from = list.findIndex((item) => item.id === id);
  if (from < 0) {
    return;
  }
  const to = from + delta;
  if (to < 0 || to >= list.length) {
    return;
  }
  const [moved] = list.splice(from, 1);
  if (!moved) {
    return;
  }
  list.splice(to, 0, moved);
  rewriteFavoriteOrder(list);
  persistBucket(storageKey(userId, organizationId, kind));
}

/**
 * 预置项第一次对上目录后，换成真实主键和名称，排序不变。
 *
 * @returns 写入后的 id；对不上时仍是原 id
 */
export function adoptAnesthFavoriteLive(
  userId: string,
  organizationId: string,
  kind: AnesthFavoriteKind,
  itemId: string,
  row: AdviceProductOption,
): string {
  const list = anesthFavoriteBucket(userId, organizationId, kind);
  const index = list.findIndex((item) => item.id === itemId);
  if (index < 0) {
    return itemId;
  }
  const prev = list[index];
  if (!prev) {
    return itemId;
  }
  if (!prev.id.startsWith('seed:') && prev.id === String(row.id)) {
    return prev.id;
  }
  const next = snapshotFromOption(row);
  next.pinnedAt = prev.pinnedAt;
  list.splice(index, 1, next);
  persistBucket(storageKey(userId, organizationId, kind));
  return next.id;
}

function rewriteFavoriteOrder(list: AnesthFavoriteMedSnapshot[]): void {
  list.forEach((item, index) => {
    item.pinnedAt = index + 1;
  });
}

/** 名称、编码、商品名、规格包含关键字则留在常用块 */
export function favoriteMatchesKeyword(item: AnesthFavoriteMedSnapshot, keyword: string): boolean {
  const query = keyword.trim().toLowerCase();
  if (!query) {
    return true;
  }
  const haystack = [item.name, item.code, item.merchandiseName, item.spec].join('\n').toLowerCase();
  return haystack.includes(query);
}

export function readAnesthFavoriteLive(
  organizationId: string,
  id: string,
): AdviceProductOption | 'missing' | undefined {
  const hit = liveCache.get(liveCacheKey(organizationId, id));
  if (!hit) {
    return undefined;
  }
  if (Date.now() - hit.at > LIVE_TTL_MS) {
    liveCache.delete(liveCacheKey(organizationId, id));
    return undefined;
  }
  return hit.row;
}

export function rememberAnesthFavoriteLive(
  organizationId: string,
  id: string,
  row: AdviceProductOption | 'missing',
): void {
  liveCache.set(liveCacheKey(organizationId, id), { at: Date.now(), row });
}
