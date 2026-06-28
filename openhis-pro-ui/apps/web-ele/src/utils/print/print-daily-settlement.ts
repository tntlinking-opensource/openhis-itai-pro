import type { OutpatientDailySettlementPrintSource } from './mappers/outpatient-daily-settlement';

import { ElMessage } from 'element-plus';

import { print, PRINT_SCENARIO } from './runtime';

export interface PrintDailySettlementOptions {
  source: OutpatientDailySettlementPrintSource;
  cashierId?: null | number | string;
  jobName?: string;
}

/** 门诊收费日结单打印（基于当前预览汇总） */
export async function printDailySettlement(options: PrintDailySettlementOptions) {
  if (!options.source.summary) {
    ElMessage.warning('请先查询日结汇总后再打印');
    return { ok: false as const };
  }

  const cashierId = String(options.cashierId ?? '').trim();
  const result = await print({
    scenarioCode: PRINT_SCENARIO.opDailySettlementPrint,
    source: options.source,
    bizRef: cashierId ? { cashierId } : undefined,
    jobName: options.jobName ?? '门诊收费日结单',
  });

  return result;
}
