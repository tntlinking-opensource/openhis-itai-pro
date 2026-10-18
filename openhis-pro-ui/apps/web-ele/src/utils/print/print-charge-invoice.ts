import { ElMessage } from 'element-plus';

import { httpPrintChargeReceipt } from '#/api/outpatient-charge/outpatient-charge.http';

import { print, PRINT_SCENARIO } from './runtime';

export interface PrintChargeInvoiceOptions {
  encounterId?: null | number | string;
  paymentId?: null | number | string;
  jobName?: string;
}

/**
 * 拉取已结算收费数据，按吉林通用机打发票套打模板打印（240×150mm 横向）。
 */
export async function printChargeInvoice(options: PrintChargeInvoiceOptions) {
  const encounterId = String(options.encounterId ?? '').trim();
  const paymentId = String(options.paymentId ?? '').trim();
  if (!encounterId && !paymentId) {
    ElMessage.warning('缺少就诊或支付信息，无法打印发票');
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
    const message = error instanceof Error ? error.message : '获取发票数据失败';
    ElMessage.error(message);
    return { ok: false as const, errorMessage: message };
  }

  if (!slip) {
    ElMessage.warning('未获取到发票数据');
    return { ok: false as const };
  }

  return print({
    scenarioCode: PRINT_SCENARIO.chargeInvoice,
    source: slip,
    bizRef: {
      scenarioCode: PRINT_SCENARIO.chargeInvoice,
      encounterId: slip.encounterId ?? (encounterId || undefined),
      paymentId: paymentId || undefined,
    },
    jobName: options.jobName ?? '门诊收费发票',
    selectPrinter: 'auto',
  });
}
