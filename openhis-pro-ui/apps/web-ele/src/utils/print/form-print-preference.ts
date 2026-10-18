import { resolvePrinterPreferenceScope } from './printer-preference';

/** 库房/调价针式联单共用偏好键（纸张类型+规格+份数） */
export const INV_FORM_PRINT_PREF_KEY = 'inv-form-print';

export const MIN_FORM_PAPER_HEIGHT_MM = 50;
export const MAX_FORM_PAPER_HEIGHT_MM = 400;
/**
 * 三联单规格（241mm 连续纸按 11 英寸整张等分）：
 * 整张 11 英寸 ≈ 279mm；二等分 5.5 英寸 ≈ 140mm；三等分 ≈ 93mm。
 */
export const INV_FORM_TRIPLE_SPEC = {
  full: 'full',
  half: 'half',
  third: 'third',
} as const;

export type InvFormTripleSpec = (typeof INV_FORM_TRIPLE_SPEC)[keyof typeof INV_FORM_TRIPLE_SPEC];

export const INV_FORM_TRIPLE_SPEC_LABELS: Record<InvFormTripleSpec, string> = {
  [INV_FORM_TRIPLE_SPEC.full]: '整张',
  [INV_FORM_TRIPLE_SPEC.half]: '二等分',
  [INV_FORM_TRIPLE_SPEC.third]: '三等分',
};

export const INV_FORM_TRIPLE_SPEC_HEIGHT_MM: Record<InvFormTripleSpec, number> = {
  [INV_FORM_TRIPLE_SPEC.full]: 279,
  [INV_FORM_TRIPLE_SPEC.half]: 140,
  [INV_FORM_TRIPLE_SPEC.third]: 93,
};

export const DEFAULT_INV_FORM_TRIPLE_SPEC: InvFormTripleSpec = INV_FORM_TRIPLE_SPEC.third;

/** 三等分一折高度（兼容旧调用） */
export const DEFAULT_FORM_PAPER_HEIGHT_MM =
  INV_FORM_TRIPLE_SPEC_HEIGHT_MM[INV_FORM_TRIPLE_SPEC.third];
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

const STORAGE_PREFIX = 'zxplat:prt:form-print-prefs';

export interface FormPrintPreference {
  paperHeightMm: number;
  copies: number;
  paperKind: InvFormPaperKind;
  /** 三联单规格：整张 / 二等分 / 三等分；A4 时仍保留上次选择 */
  tripleSpec: InvFormTripleSpec;
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

/** 非法规格回落到三等分；仍认历史字面量 8/11 为规格码 */
export function normalizeInvFormTripleSpec(value: unknown): InvFormTripleSpec {
  if (value === INV_FORM_TRIPLE_SPEC.full) {
    return INV_FORM_TRIPLE_SPEC.full;
  }
  if (value === INV_FORM_TRIPLE_SPEC.half || value === '11') {
    return INV_FORM_TRIPLE_SPEC.half;
  }
  if (value === INV_FORM_TRIPLE_SPEC.third || value === '8') {
    return INV_FORM_TRIPLE_SPEC.third;
  }
  return DEFAULT_INV_FORM_TRIPLE_SPEC;
}

/** 是否针式三联单（非 A4） */
export function isInvFormTriplePaper(kind: InvFormPaperKind): boolean {
  return kind === INV_FORM_PAPER_KIND.triple;
}

/** 三联单规格对应的一折高度（mm） */
export function resolveInvFormTripleHeightMm(spec: InvFormTripleSpec): number {
  return INV_FORM_TRIPLE_SPEC_HEIGHT_MM[normalizeInvFormTripleSpec(spec)];
}

/**
 * 旧偏好只有纸高、没有规格时：≥210mm 整张，≥120mm 二等分，否则三等分。
 */
export function inferInvFormTripleSpecFromHeight(
  heightMm: null | number | undefined,
): InvFormTripleSpec {
  const n = typeof heightMm === 'number' && Number.isFinite(heightMm) ? heightMm : NaN;
  if (!Number.isFinite(n)) {
    return DEFAULT_INV_FORM_TRIPLE_SPEC;
  }
  if (n >= 210) {
    return INV_FORM_TRIPLE_SPEC.full;
  }
  if (n >= 120) {
    return INV_FORM_TRIPLE_SPEC.half;
  }
  return INV_FORM_TRIPLE_SPEC.third;
}

/** 设置页/弹窗展示：三联单带规格，A4 不带 */
export function resolveInvFormPaperDisplayLabel(
  paperKind: InvFormPaperKind,
  spec: InvFormTripleSpec,
): string {
  if (!isInvFormTriplePaper(paperKind)) {
    return INV_FORM_PAPER_KIND_LABELS[paperKind];
  }
  return `${INV_FORM_PAPER_KIND_LABELS.triple} · ${INV_FORM_TRIPLE_SPEC_LABELS[spec]}`;
}

/** 预览/打印时纸张与三联单规格一起变更 */
export interface InvFormPaperChangePayload {
  paperKind: InvFormPaperKind;
  tripleSpec: InvFormTripleSpec;
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

/** 从已存偏好解析三联单规格（tripleSpec，非法或缺失时按纸高推断） */
function resolveStoredTripleSpec(saved: FormPrintPreference | undefined): InvFormTripleSpec {
  if (!saved) {
    return DEFAULT_INV_FORM_TRIPLE_SPEC;
  }
  const rawSpec = saved.tripleSpec;
  if (rawSpec) {
    return normalizeInvFormTripleSpec(rawSpec);
  }
  return inferInvFormTripleSpecFromHeight(saved.paperHeightMm);
}

/** 读取联单纸张类型/规格/纸高/份数；未在设置里改过时纸张固定三联单 */
export function getFormPrintPreference(prefKey: string): FormPrintPreference {
  const code = prefKey.trim() || INV_FORM_PRINT_PREF_KEY;
  const { tenantId, userId } = resolvePrinterPreferenceScope();
  const saved = readStore(tenantId, userId)[code];
  const paperKindFromSettings = saved?.paperKindFromSettings === true;
  const tripleSpec = resolveStoredTripleSpec(saved);
  return {
    paperHeightMm: resolveInvFormTripleHeightMm(tripleSpec),
    copies: clampFormCopies(saved?.copies),
    paperKind: paperKindFromSettings
      ? normalizeInvFormPaperKind(saved?.paperKind)
      : DEFAULT_INV_FORM_PAPER_KIND,
    tripleSpec,
    paperKindFromSettings,
    updatedAt: saved?.updatedAt ?? '',
  };
}

/** 保存联单纸张类型/规格/纸高/份数；persistPaperKind 仅设置页传入 */
export function saveFormPrintPreference(
  prefKey: string,
  pref: {
    copies: number;
    paperKind?: InvFormPaperKind;
    persistPaperKind?: boolean;
    tripleSpec?: InvFormTripleSpec;
  },
): FormPrintPreference {
  const code = prefKey.trim() || INV_FORM_PRINT_PREF_KEY;
  const { tenantId, userId } = resolvePrinterPreferenceScope();
  const store = readStore(tenantId, userId);
  const persistPaperKind = pref.persistPaperKind === true;
  const paperKindFromSettings = persistPaperKind
    ? true
    : store[code]?.paperKindFromSettings === true;
  const tripleSpec = pref.tripleSpec
    ? normalizeInvFormTripleSpec(pref.tripleSpec)
    : resolveStoredTripleSpec(store[code]);
  const next: FormPrintPreference = {
    paperHeightMm: resolveInvFormTripleHeightMm(tripleSpec),
    copies: clampFormCopies(pref.copies),
    paperKind: persistPaperKind
      ? normalizeInvFormPaperKind(pref.paperKind)
      : normalizeInvFormPaperKind(store[code]?.paperKind),
    tripleSpec,
    paperKindFromSettings,
    updatedAt: new Date().toISOString(),
  };
  store[code] = next;
  writeStore(tenantId, userId, store);
  return next;
}
