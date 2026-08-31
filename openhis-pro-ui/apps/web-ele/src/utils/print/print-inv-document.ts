import type { InvFormPaperKind } from './form-print-preference';
import type { InvMovementPrintSource } from './mappers/inv-movement';
import type { InvReturnPrintSource } from './mappers/inv-return';
import type { InvStocktakePrintSource } from './mappers/inv-stocktake';
import type { PrintResult } from './runtime/types';

import { ElMessage } from 'element-plus';

import { applyInvDocumentPaperByKind } from './apply-inv-form-paper-layout';
import { PRINT_SCENARIO } from './constants/print-scenario';
import {
  DEFAULT_FORM_COPIES,
  getFormPrintPreference,
  INV_FORM_PAPER_HEIGHT_MM,
  INV_FORM_PRINT_PREF_KEY,
  isInvFormTriplePaper,
  normalizeInvFormPaperKind,
} from './form-print-preference';
import { normalizeInvReturnPrintJson } from './normalize-inv-return-print-json';
import { print } from './runtime';
import { PRINT_ERROR_CODE } from './runtime/types';

/** 库存打印模版族 */
export type InvPrintFamily = 'movement' | 'return' | 'stocktake';

const FAMILY_SCENARIO = {
  movement: PRINT_SCENARIO.invMovementPrint,
  stocktake: PRINT_SCENARIO.invStocktakePrint,
  return: PRINT_SCENARIO.invReturnPrint,
} as const;

const FAMILY_DEFAULT_JOB = {
  movement: '库存流转单',
  stocktake: '库存盘点单',
  return: '库存退货单',
} as const;

export interface PrintInvDocumentOptions {
  family: InvPrintFamily;
  source: InvMovementPrintSource | InvReturnPrintSource | InvStocktakePrintSource;
  bizRef?: Record<string, unknown>;
  jobName?: string;
  /** true 时打开屏幕预览，不静默打印 */
  preview?: boolean;
  copies?: number;
  paperHeightMm?: number;
  /** 指定则跳过打印前纸张弹窗 */
  paperKind?: InvFormPaperKind;
}

/** 按模版族打印或预览库存单据 */
export async function printInvDocument(options: PrintInvDocumentOptions): Promise<PrintResult> {
  const family = options.family;
  const scenarioCode = FAMILY_SCENARIO[family];
  if (!scenarioCode) {
    ElMessage.error('未知的库存打印模版族');
    return {
      ok: false,
      errorCode: PRINT_ERROR_CODE.INVALID_OPTIONS,
      errorMessage: '未知的库存打印模版族',
    };
  }
  const pref = getFormPrintPreference(INV_FORM_PRINT_PREF_KEY);
  const copies = options.copies ?? (options.preview ? pref.copies : DEFAULT_FORM_COPIES);
  const paperHeightMm = options.paperHeightMm ?? INV_FORM_PAPER_HEIGHT_MM;
  const paperKind = normalizeInvFormPaperKind(options.paperKind ?? pref.paperKind);
  const isPreview = options.preview === true;
  const isTriple = isInvFormTriplePaper(paperKind);

  return print({
    scenarioCode,
    source: options.source,
    bizRef: {
      scenarioCode,
      ...(options.bizRef ?? {}),
    },
    jobName: options.jobName ?? FAMILY_DEFAULT_JOB[family],
    preview: isPreview,
    copies,
    repeatPrintData: isTriple,
    formPaperKind: paperKind,
    showFormPaperKind: isPreview,
    // 直接打印：纸张与打印机同一弹窗；已记住则按 auto 跳过
    showPaperKind: !isPreview && options.paperKind === undefined,
    selectPrinter: 'auto',
    transformPrintJson: (printJson, ctx) => {
      const kind = normalizeInvFormPaperKind(ctx?.formPaperKind ?? paperKind);
      // 退货种子模板是迷你版，先对齐流转单版式再套纸张
      const normalized = family === 'return' ? normalizeInvReturnPrintJson(printJson) : printJson;
      return applyInvDocumentPaperByKind(normalized, kind, paperHeightMm);
    },
  });
}

export { buildInvDocumentTitle, resolveInvPrintProductBusNo } from './mappers/inv-common';
export type { InvMovementPrintSource } from './mappers/inv-movement';
export type { InvReturnPrintSource } from './mappers/inv-return';
export type { InvStocktakePrintSource } from './mappers/inv-stocktake';
