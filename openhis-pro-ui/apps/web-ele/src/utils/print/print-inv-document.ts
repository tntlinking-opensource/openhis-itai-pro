import type { InvFormPaperKind, InvFormTripleSpec } from './form-print-preference';
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
  INV_FORM_PRINT_PREF_KEY,
  isInvFormTriplePaper,
  normalizeInvFormPaperKind,
  normalizeInvFormTripleSpec,
  resolveInvFormTripleHeightMm,
} from './form-print-preference';
import { normalizeInvGainLossPrintJson } from './normalize-inv-gain-loss-print-json';
import { applyInvMovementDeviceSignOffFormatters } from './normalize-inv-movement-sign-off';
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
  /** 指定则按该规格套三联单纸高 */
  tripleSpec?: InvFormTripleSpec;
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
  const tripleSpec = normalizeInvFormTripleSpec(options.tripleSpec ?? pref.tripleSpec);
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
    formTripleSpec: tripleSpec,
    showFormPaperKind: isPreview,
    // 直接打印：纸张与打印机同一弹窗；已记住则按 auto 跳过
    showPaperKind: !isPreview && options.paperKind === undefined,
    selectPrinter: 'auto',
    transformPrintJson: (printJson, ctx) => {
      const kind = normalizeInvFormPaperKind(ctx?.formPaperKind ?? paperKind);
      const spec = normalizeInvFormTripleSpec(ctx?.formTripleSpec ?? tripleSpec);
      const heightMm = options.paperHeightMm ?? resolveInvFormTripleHeightMm(spec);
      // 退货种子模板是迷你版，先对齐流转单版式再套纸张
      const afterReturn = family === 'return' ? normalizeInvReturnPrintJson(printJson) : printJson;
      // 耗材进货/采购申请签批与药品分流（库模板未迁时仍按 PrintData 开关改写）
      let normalized =
        family === 'movement' ? applyInvMovementDeviceSignOffFormatters(afterReturn) : afterReturn;
      // 损益单复用盘点模版：去掉账面/实盘列，避免把损益量印成实盘
      if (family === 'stocktake' && options.bizRef?.docKind === 'gain-loss') {
        normalized = normalizeInvGainLossPrintJson(normalized);
      }
      return applyInvDocumentPaperByKind(normalized, kind, heightMm);
    },
  });
}

export { buildInvDocumentTitle, resolveInvPrintProductBusNo } from './mappers/inv-common';
export type { InvMovementPrintSource } from './mappers/inv-movement';
export type { InvReturnPrintSource } from './mappers/inv-return';
export type { InvStocktakePrintSource } from './mappers/inv-stocktake';
