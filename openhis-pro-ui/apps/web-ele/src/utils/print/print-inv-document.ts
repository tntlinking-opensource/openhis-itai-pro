import type { PrintResult } from './runtime/types';

import type { InvMovementPrintSource } from './mappers/inv-movement';
import type { InvReturnPrintSource } from './mappers/inv-return';
import type { InvStocktakePrintSource } from './mappers/inv-stocktake';

import { ElMessage } from 'element-plus';

import { PRINT_SCENARIO } from './constants/print-scenario';
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
}

/** 按模版族打印库存单据（prepare → hiprint → confirm） */
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
  return print({
    scenarioCode,
    source: options.source,
    bizRef: options.bizRef,
    jobName: options.jobName ?? FAMILY_DEFAULT_JOB[family],
  });
}

export { buildInvDocumentTitle } from './mappers/inv-common';
export type { InvMovementPrintSource } from './mappers/inv-movement';
export type { InvReturnPrintSource } from './mappers/inv-return';
export type { InvStocktakePrintSource } from './mappers/inv-stocktake';
