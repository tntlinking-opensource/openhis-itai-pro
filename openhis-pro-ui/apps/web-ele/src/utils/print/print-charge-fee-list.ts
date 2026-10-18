import { ElMessage } from 'element-plus';

import { httpPrintChargeReceipt } from '#/api/outpatient-charge/outpatient-charge.http';

import { print, PRINT_SCENARIO } from './runtime';

export interface PrintChargeFeeListOptions {
  encounterId?: null | number | string;
  paymentId?: null | number | string;
  jobName?: string;
}

/**
 * 拉取已结算收费数据，按门诊患者费用清单模板打印（A4 横向；慢病切换标题）。
 */
export async function printChargeFeeList(options: PrintChargeFeeListOptions) {
  const encounterId = String(options.encounterId ?? '').trim();
  const paymentId = String(options.paymentId ?? '').trim();
  if (!encounterId && !paymentId) {
    ElMessage.warning('缺少就诊或支付信息，无法打印费用清单');
    return { ok: false as const };
  }

  let slip;
  try {
    const result = await httpPrintChargeReceipt({
      encounterId: encounterId || undefined,
      paymentId: paymentId || undefined,
    });
    slip = result.slip;
  } catch (error) {
    const message = error instanceof Error ? error.message : '获取费用清单数据失败';
    ElMessage.error(message);
    return { ok: false as const, errorMessage: message };
  }

  if (!slip) {
    ElMessage.warning('未获取到费用清单数据');
    return { ok: false as const };
  }

  const rows = Array.isArray(slip.feeListRows) ? slip.feeListRows : [];
  if (rows.length === 0) {
    ElMessage.warning('没有可打印的费用明细');
    return { ok: false as const };
  }

  const documentTitle = String(slip.documentTitle ?? '').trim() || '门诊患者费用清单';

  return print({
    scenarioCode: PRINT_SCENARIO.opFeeListPrint,
    source: slip,
    bizRef: {
      scenarioCode: PRINT_SCENARIO.opFeeListPrint,
      encounterId: slip.encounterId ?? (encounterId || undefined),
      paymentId: paymentId || undefined,
    },
    jobName: options.jobName ?? documentTitle,
    selectPrinter: 'auto',
  });
}
