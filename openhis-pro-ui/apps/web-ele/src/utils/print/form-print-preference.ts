import { resolvePrinterPreferenceScope } from './printer-preference';

/** 库房/调价针式联单共用偏好键（纸张类型+纸高+份数） */
export const INV_FORM_PRINT_PREF_KEY = 'inv-form-print';

export const MIN_FORM_PAPER_HEIGHT_MM = 50;
export const MAX_FORM_PAPER_HEIGHT_MM = 400;
/**
 * 针式联单一折高度（mm）。
 * 与 9.5×4 英寸连续纸齿孔间距一致，全院统一，不再让用户填。
 */
export const DEFAULT_FORM_PAPER_HEIGHT_MM = 100;
export const INV_FORM_PAPER_HEIGHT_MM = DEFAULT_FORM_PAPER_HEIGHT_MM;

/** A4 纵向版心（mm），与库房模板设计稿一致 */
export const A4_PAPER_WIDTH_MM = 210;
export const A4_PAPER_HEIGHT_MM = 297;

export const MIN_FORM_COPIES = 1;
export const MAX_FORM_COPIES = 9;
/** 复写联单一次走纸即多联，默认只打 1 份 */
export const DEFAULT_FORM_COPIES = 1;

/** 库房单据纸张：三联单（针式连续纸） / A4 */
export const INV_FORM_PAPER_KIND = {
  triple: 'triple',
  a4: 'a4',
} as const;

export type InvFormPaperKind = (typeof INV_FORM_PAPER_KIND)[keyof typeof INV_FORM_PAPER_KIND];

export const INV_FORM_PAPER_KIND_LABELS: Record<InvFormPaperKind, string> = {
  triple: '三联单',
  a4: 'A4纸',
};

export const DEFAULT_INV_FORM_PAPER_KIND: InvFormPaperKind = INV_FORM_PAPER_KIND.triple;

const STORAGE_PREFIX = 'openhis:prt:form-print-prefs';

export interface FormPrintPreference {
  paperHeightMm: number;
  copies: number;
  paperKind: InvFormPaperKind;
  updatedAt: string;
  /**
   * 设置页或勾选「下次不再提示」后为 true。
   * 无此标记时纸张一律按三联单，避免打印里随手点过 A4 把默认改掉。
   */
  paperKindFromSettings?: boolean;
}

type FormPrintPreferenceStore = Record<string, FormPrintPreference>;

/** 当前登录上下文下的 localStorage 键 */
function buildStorageKey(tenantId: string, userId: string): string {
  return `${STORAGE_PREFIX}:${tenantId}:${userId}`;
}

/** 纸高限制在可打印范围 */
export function clampFormPaperHeightMm(value: null | number | undefined): number {
  const n =
    typeof value === 'number' && Number.isFinite(value) ? value : DEFAULT_FORM_PAPER_HEIGHT_MM;
  return Math.min(MAX_FORM_PAPER_HEIGHT_MM, Math.max(MIN_FORM_PAPER_HEIGHT_MM, Math.round(n)));
}

/** 份数限制在 1～9 */
export function clampFormCopies(value: null | number | undefined): number {
  const n = typeof value === 'number' && Number.isFinite(value) ? value : DEFAULT_FORM_COPIES;
  return Math.min(MAX_FORM_COPIES, Math.max(MIN_FORM_COPIES, Math.round(n)));
}

/** 非法值回落到三联单 */
export function normalizeInvFormPaperKind(value: unknown): InvFormPaperKind {
  return value === INV_FORM_PAPER_KIND.a4 ? INV_FORM_PAPER_KIND.a4 : INV_FORM_PAPER_KIND.triple;
}

/** 是否针式三联单（非 A4） */
export function isInvFormTriplePaper(kind: InvFormPaperKind): boolean {
  return kind === INV_FORM_PAPER_KIND.triple;
}

function readStore(tenantId: string, userId: string): FormPrintPreferenceStore {
  if (!tenantId || !userId) {
    return {};
  }
  const raw = localStorage.getItem(buildStorageKey(tenantId, userId));
  if (!raw) {
    return {};
  }
  try {
    const parsed = JSON.parse(raw) as FormPrintPreferenceStore;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function writeStore(tenantId: string, userId: string, store: FormPrintPreferenceStore): void {
  if (!tenantId || !userId) {
    return;
  }
  const key = buildStorageKey(tenantId, userId);
  if (Object.keys(store).length === 0) {
    localStorage.removeItem(key);
    return;
  }
  localStorage.setItem(key, JSON.stringify(store));
}

/** 读取联单纸张类型/纸高/份数；未在设置里改过时纸张固定三联单 */
export function getFormPrintPreference(prefKey: string): FormPrintPreference {
  const code = prefKey.trim() || INV_FORM_PRINT_PREF_KEY;
  const { tenantId, userId } = resolvePrinterPreferenceScope();
  const saved = readStore(tenantId, userId)[code];
  const paperKindFromSettings = saved?.paperKindFromSettings === true;
  return {
    paperHeightMm: clampFormPaperHeightMm(saved?.paperHeightMm),
    copies: clampFormCopies(saved?.copies),
    paperKind: paperKindFromSettings
      ? normalizeInvFormPaperKind(saved?.paperKind)
      : DEFAULT_INV_FORM_PAPER_KIND,
    paperKindFromSettings,
    updatedAt: saved?.updatedAt ?? '',
  };
}

/** 保存联单纸张类型/纸高/份数；persistPaperKind 仅设置页传入 */
export function saveFormPrintPreference(
  prefKey: string,
  pref: {
    copies: number;
    paperHeightMm: number;
    paperKind?: InvFormPaperKind;
    persistPaperKind?: boolean;
  },
): FormPrintPreference {
  const code = prefKey.trim() || INV_FORM_PRINT_PREF_KEY;
  const { tenantId, userId } = resolvePrinterPreferenceScope();
  const store = readStore(tenantId, userId);
  const persistPaperKind = pref.persistPaperKind === true;
  const paperKindFromSettings = persistPaperKind
    ? true
    : store[code]?.paperKindFromSettings === true;
  const next: FormPrintPreference = {
    paperHeightMm: clampFormPaperHeightMm(pref.paperHeightMm),
    copies: clampFormCopies(pref.copies),
    paperKind: persistPaperKind
      ? normalizeInvFormPaperKind(pref.paperKind)
      : normalizeInvFormPaperKind(store[code]?.paperKind),
    paperKindFromSettings,
    updatedAt: new Date().toISOString(),
  };
  store[code] = next;
  writeStore(tenantId, userId, store);
  return next;
}
