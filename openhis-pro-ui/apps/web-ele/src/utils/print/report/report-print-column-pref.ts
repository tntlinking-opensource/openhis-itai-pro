import { useUserStore } from '@zxplat/stores';

import { useAuthStore } from '#/store';

const STORAGE_PREFIX = 'zxplat:rpt:print-cols';

/** 报表打印纸张方向 */
export type ReportPrintOrientation = 'landscape' | 'portrait';

/** 报表打印范围：跨页全部 / 仅本页 */
export type ReportPrintScope = 'all' | 'currentPage';

export interface ReportPrintColumnPreference {
  /** 有序字段名，即打印列顺序 */
  fields: string[];
  /** 纸张方向；缺省按横向 */
  orientation: ReportPrintOrientation;
  /** 打印范围；缺省跨页打全部 */
  printScope: ReportPrintScope;
  updatedAt: string;
}

type ReportPrintColumnPreferenceStore = Record<string, ReportPrintColumnPreference>;

/** 规范化纸张方向 */
export function normalizeReportPrintOrientation(value: unknown): ReportPrintOrientation {
  return value === 'portrait' ? 'portrait' : 'landscape';
}

/** 规范化打印范围；未存过时默认跨页全部 */
export function normalizeReportPrintScope(value: unknown): ReportPrintScope {
  return value === 'currentPage' ? 'currentPage' : 'all';
}

/** 当前登录上下文下的 localStorage 键 */
export function buildReportPrintColumnPrefStorageKey(tenantId: string, userId: string): string {
  return `${STORAGE_PREFIX}:${tenantId}:${userId}`;
}

/** 从 Pinia 解析租户与用户主键（未登录时返回空串） */
export function resolveReportPrintColumnPrefScope(): {
  tenantId: string;
  userId: string;
} {
  const authStore = useAuthStore();
  const userStore = useUserStore();
  const tenantId = authStore.getSelectedTenant()?.trim() ?? '';
  const userId =
    userStore.userInfo?.id === null || userStore.userInfo?.id === undefined
      ? ''
      : String(userStore.userInfo.id);
  return { tenantId, userId };
}

function readStore(tenantId: string, userId: string): ReportPrintColumnPreferenceStore {
  if (!tenantId || !userId) {
    return {};
  }
  const raw = localStorage.getItem(buildReportPrintColumnPrefStorageKey(tenantId, userId));
  if (!raw) {
    return {};
  }
  try {
    const parsed = JSON.parse(raw) as ReportPrintColumnPreferenceStore;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function writeStore(
  tenantId: string,
  userId: string,
  store: ReportPrintColumnPreferenceStore,
): void {
  if (!tenantId || !userId) {
    return;
  }
  const key = buildReportPrintColumnPrefStorageKey(tenantId, userId);
  if (Object.keys(store).length === 0) {
    localStorage.removeItem(key);
    return;
  }
  localStorage.setItem(key, JSON.stringify(store));
}

/** 去空白、去重并保序 */
function normalizeFieldList(rawList: string[]): string[] {
  const seen = new Set<string>();
  const normalized: string[] = [];
  for (const raw of rawList) {
    const field = String(raw ?? '').trim();
    if (!field || seen.has(field)) {
      continue;
    }
    seen.add(field);
    normalized.push(field);
  }
  return normalized;
}

/** 读取某报表的打印字段/方向偏好 */
export function getReportPrintColumnPreference(
  reportCode: string,
): null | ReportPrintColumnPreference {
  const code = reportCode.trim();
  if (!code) {
    return null;
  }
  const { tenantId, userId } = resolveReportPrintColumnPrefScope();
  const pref = readStore(tenantId, userId)[code];
  if (!pref || !Array.isArray(pref.fields) || pref.fields.length === 0) {
    return null;
  }
  const fields = normalizeFieldList(pref.fields.map((f) => String(f ?? '')));
  if (fields.length === 0) {
    return null;
  }
  return {
    fields,
    orientation: normalizeReportPrintOrientation(pref.orientation),
    printScope: normalizeReportPrintScope(pref.printScope),
    updatedAt: pref.updatedAt ?? '',
  };
}

export interface SaveReportPrintColumnPreferenceInput {
  fields: string[];
  orientation?: ReportPrintOrientation;
  printScope?: ReportPrintScope;
}

/** 保存某报表的打印字段与纸张方向 */
export function saveReportPrintColumnPreference(
  reportCode: string,
  input: SaveReportPrintColumnPreferenceInput | string[],
): void {
  const code = reportCode.trim();
  if (!code) {
    return;
  }
  const payload = Array.isArray(input) ? { fields: input } : input;
  const normalized = normalizeFieldList(payload.fields);
  if (normalized.length === 0) {
    return;
  }
  const { tenantId, userId } = resolveReportPrintColumnPrefScope();
  const store = readStore(tenantId, userId);
  const prev = store[code];
  store[code] = {
    fields: normalized,
    orientation: normalizeReportPrintOrientation(
      payload.orientation ?? prev?.orientation ?? 'landscape',
    ),
    printScope: normalizeReportPrintScope(payload.printScope ?? prev?.printScope),
    updatedAt: new Date().toISOString(),
  };
  writeStore(tenantId, userId, store);
}
