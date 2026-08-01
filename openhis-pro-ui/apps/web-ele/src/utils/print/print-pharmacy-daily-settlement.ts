import type { PharmacyDailySettlementPrintSource } from './mappers/pharmacy-daily-settlement';

import { ElMessage } from 'element-plus';

import { print, PRINT_SCENARIO } from './runtime';

export interface PrintPharmacyDailySettlementOptions {
  source: PharmacyDailySettlementPrintSource;
  /** 打印作业业务引用（可选） */
  bizRef?: Record<string, number | string>;
  jobName?: string;
}

/** 药房日结单打印（门诊/住院共用模板 pharmacy-dailySettlement-print） */
export async function printPharmacyDailySettlement(options: PrintPharmacyDailySettlementOptions) {
  const hasCategory = (options.source.categoryRows?.length ?? 0) > 0;
  const hasProduct = (options.source.productRows?.length ?? 0) > 0;
  if (!hasCategory && !hasProduct) {
    ElMessage.warning('请先查询日结汇总后再打印');
    return { ok: false as const };
  }

  return print({
    scenarioCode: PRINT_SCENARIO.pharmacyDailySettlementPrint,
    source: options.source,
    bizRef: options.bizRef,
    jobName: options.jobName ?? '药房日结单',
  });
}
