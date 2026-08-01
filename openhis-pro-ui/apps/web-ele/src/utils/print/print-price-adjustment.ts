import type { PrintResult } from './runtime/types';

import type { FinPriceAdjustmentPrintSource } from './mappers/fin-price-adjustment';

import { PRINT_SCENARIO } from './constants/print-scenario';
import { print } from './runtime';

export interface PrintPriceAdjustmentOptions {
  source: FinPriceAdjustmentPrintSource;
  bizRef?: Record<string, unknown>;
  jobName?: string;
}

/** 调价单打印（prepare → hiprint → confirm） */
export async function printPriceAdjustment(
  options: PrintPriceAdjustmentOptions,
): Promise<PrintResult> {
  return print({
    scenarioCode: PRINT_SCENARIO.finPriceAdjustmentPrint,
    source: options.source,
    bizRef: options.bizRef,
    jobName: options.jobName ?? '调价单',
  });
}

export type { FinPriceAdjustmentPrintSource } from './mappers/fin-price-adjustment';
