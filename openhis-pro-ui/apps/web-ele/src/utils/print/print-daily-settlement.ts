import type { OutpatientDailySettlementPrintSource } from './mappers/outpatient-daily-settlement';

import { ElMessage } from 'element-plus';

import { resolvePrintHospitalName } from './resolve-print-hospital-name';
import { print, PRINT_SCENARIO } from './runtime';

export interface PrintDailySettlementOptions {
  source: OutpatientDailySettlementPrintSource;
  /** 打印作业业务引用（交账单 settlementId，或预览窗 orgId+日期+收款人） */
  bizRef?: Record<string, unknown>;
  jobName?: string;
}

/** 门诊/住院收费日结单打印（基于当前预览汇总；字段与页面动态同构） */
export async function printDailySettlement(options: PrintDailySettlementOptions) {
  if (!options.source.summary) {
    ElMessage.warning('请先查询日结汇总后再打印');
    return { ok: false as const };
  }

  const result = await print({
    scenarioCode: PRINT_SCENARIO.opDailySettlementPrint,
    source: {
      hospitalName: options.source.hospitalName?.trim() || resolvePrintHospitalName(),
      documentTitle: options.source.documentTitle?.trim() || '门诊收费日结单',
      cashierName: options.source.cashierName,
      settlementPeriodText: options.source.settlementPeriodText,
      rows: options.source.rows,
      summary: options.source.summary,
    },
    bizRef: {
      scenarioCode: PRINT_SCENARIO.opDailySettlementPrint,
      ...(options.bizRef ?? {}),
    },
    jobName: options.jobName ?? '门诊收费日结单',
  });

  return result;
}
