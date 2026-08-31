import type { PrintResult } from './runtime/types';

import type { FinPriceAdjustmentPrintSource } from './mappers/fin-price-adjustment';

import { applyInvFormPaperLayout } from './apply-inv-form-paper-layout';
import { PRINT_SCENARIO } from './constants/print-scenario';
import {
  DEFAULT_FORM_COPIES,
  getFormPrintPreference,
  INV_FORM_PAPER_HEIGHT_MM,
  INV_FORM_PRINT_PREF_KEY,
} from './form-print-preference';
import { print } from './runtime';

export interface PrintPriceAdjustmentOptions {
  source: FinPriceAdjustmentPrintSource;
  bizRef?: Record<string, unknown>;
  jobName?: string;
  /** true 时打开屏幕预览，不静默打印 */
  preview?: boolean;
  copies?: number;
  paperHeightMm?: number;
}

/** 调价单打印或预览 */
export async function printPriceAdjustment(
  options: PrintPriceAdjustmentOptions,
): Promise<PrintResult> {
  const pref = getFormPrintPreference(INV_FORM_PRINT_PREF_KEY);
  const copies = options.copies ?? (options.preview ? pref.copies : DEFAULT_FORM_COPIES);
  const paperHeightMm = options.paperHeightMm ?? INV_FORM_PAPER_HEIGHT_MM;
  return print({
    scenarioCode: PRINT_SCENARIO.finPriceAdjustmentPrint,
    source: options.source,
    bizRef: {
      scenarioCode: PRINT_SCENARIO.finPriceAdjustmentPrint,
      ...(options.bizRef ?? {}),
    },
    jobName: options.jobName ?? '调价单',
    preview: options.preview,
    copies,
    repeatPrintData: true,
    transformPrintJson: (printJson) => applyInvFormPaperLayout(printJson, paperHeightMm),
  });
}

export type { FinPriceAdjustmentPrintSource } from './mappers/fin-price-adjustment';
