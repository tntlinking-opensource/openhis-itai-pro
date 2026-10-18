import { PRINT_SCENARIO } from './constants/print-scenario';
import { resolvePrinterPreferenceScope } from './printer-preference';

/** 门诊申请单纸张：A4 竖版 / A5 竖版 */
export const SERVICE_APPLY_PAPER_SIZE = {
  a4: 'A4',
  a5: 'A5',
} as const;

export type ServiceApplyPaperSize =
  (typeof SERVICE_APPLY_PAPER_SIZE)[keyof typeof SERVICE_APPLY_PAPER_SIZE];

export const SERVICE_APPLY_PAPER_SIZE_LABELS: Record<ServiceApplyPaperSize, string> = {
  [SERVICE_APPLY_PAPER_SIZE.a4]: 'A4纸',
  [SERVICE_APPLY_PAPER_SIZE.a5]: 'A5纸',
};

export const DEFAULT_SERVICE_APPLY_PAPER_SIZE: ServiceApplyPaperSize = SERVICE_APPLY_PAPER_SIZE.a4;

const STORAGE_PREFIX = 'zxplat:prt:service-apply-paper';
const PREF_KEY = 'service-apply';

interface ServiceApplyPaperPreference {
  paperSize: ServiceApplyPaperSize;
  updatedAt: string;
}

type PreferenceStore = Record<string, ServiceApplyPaperPreference>;

function buildStorageKey(tenantId: string, userId: string): string {
  return `${STORAGE_PREFIX}:${tenantId}:${userId}`;
}

function readStore(tenantId: string, userId: string): PreferenceStore {
  if (!tenantId || !userId) {
    return {};
  }
  const raw = localStorage.getItem(buildStorageKey(tenantId, userId));
  if (!raw) {
    return {};
  }
  try {
    const parsed = JSON.parse(raw) as PreferenceStore;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function writeStore(tenantId: string, userId: string, store: PreferenceStore): void {
  if (!tenantId || !userId) {
    return;
  }
  localStorage.setItem(buildStorageKey(tenantId, userId), JSON.stringify(store));
}

/** 非法值回落 A4 */
export function normalizeServiceApplyPaperSize(value: unknown): ServiceApplyPaperSize {
  if (value === SERVICE_APPLY_PAPER_SIZE.a5) {
    return SERVICE_APPLY_PAPER_SIZE.a5;
  }
  return SERVICE_APPLY_PAPER_SIZE.a4;
}

/** 读取上次选择的申请单纸张 */
export function getServiceApplyPaperSizePreference(): ServiceApplyPaperSize {
  const { tenantId, userId } = resolvePrinterPreferenceScope();
  const saved = readStore(tenantId, userId)[PREF_KEY];
  return normalizeServiceApplyPaperSize(saved?.paperSize);
}

/** 记住申请单纸张选择 */
export function saveServiceApplyPaperSizePreference(paperSize: ServiceApplyPaperSize): void {
  const { tenantId, userId } = resolvePrinterPreferenceScope();
  if (!tenantId || !userId) {
    return;
  }
  const store = readStore(tenantId, userId);
  store[PREF_KEY] = {
    paperSize: normalizeServiceApplyPaperSize(paperSize),
    updatedAt: new Date().toISOString(),
  };
  writeStore(tenantId, userId, store);
}

/** 由场景码反推申请单纸张（A5 场景 → A5，其余 A4） */
export function inferServiceApplyPaperSizeFromScenario(scenarioCode: string): ServiceApplyPaperSize {
  const code = scenarioCode.trim();
  if (code === PRINT_SCENARIO.examinationPrintA5 || code === PRINT_SCENARIO.testPrintA5) {
    return SERVICE_APPLY_PAPER_SIZE.a5;
  }
  return SERVICE_APPLY_PAPER_SIZE.a4;
}

/**
 * 按纸张在检查/检验 A4↔A5 场景间切换；非申请单场景原样返回。
 */
export function remapServiceApplyScenarioByPaper(
  scenarioCode: string,
  paperSize: ServiceApplyPaperSize,
): string {
  const code = scenarioCode.trim();
  const useA5 = normalizeServiceApplyPaperSize(paperSize) === SERVICE_APPLY_PAPER_SIZE.a5;
  if (code === PRINT_SCENARIO.examinationPrint || code === PRINT_SCENARIO.examinationPrintA5) {
    return useA5 ? PRINT_SCENARIO.examinationPrintA5 : PRINT_SCENARIO.examinationPrint;
  }
  if (code === PRINT_SCENARIO.testPrint || code === PRINT_SCENARIO.testPrintA5) {
    return useA5 ? PRINT_SCENARIO.testPrintA5 : PRINT_SCENARIO.testPrint;
  }
  return code;
}
