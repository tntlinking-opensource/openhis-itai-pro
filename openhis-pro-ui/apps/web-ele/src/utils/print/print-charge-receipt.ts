import { ElMessage } from 'element-plus';

import { httpPrintChargeReceipt } from '#/api/outpatient-charge/outpatient-charge.http';

import { print, PRINT_SCENARIO, scheduleBackgroundPrint } from './runtime';

export interface PrintChargeReceiptOptions {
  encounterId?: null | number | string;
  paymentId?: null | number | string;
  jobName?: string;
  /** 伴随主流程打印：不弹打印机选择框（默认 false，补打等显式操作为 false） */
  background?: boolean;
}

/**
 * 拉取后端收费小票数据并静默打印（prepare → hiprint → confirm）。
 */
export async function printChargeReceipt(options: PrintChargeReceiptOptions) {
  const encounterId = String(options.encounterId ?? '').trim();
  const paymentId = String(options.paymentId ?? '').trim();
  if (!encounterId && !paymentId) {
    ElMessage.warning('缺少就诊或支付信息，无法打印收费小票');
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
    const message = error instanceof Error ? error.message : '获取收费小票数据失败';
    ElMessage.error(message);
    return { ok: false as const, errorMessage: message };
  }

  if (!slip) {
    ElMessage.warning('未获取到收费小票数据');
    return { ok: false as const };
  }

  return print({
    scenarioCode: PRINT_SCENARIO.chargeReceipt,
    source: slip,
    bizRef: {
      encounterId: slip.encounterId ?? (encounterId || undefined),
      paymentId: paymentId || undefined,
    },
    jobName: options.jobName ?? '收费小票',
    selectPrinter: options.background ? 'never' : 'auto',
    background: options.background === true,
  });
}

/**
 * 收费主流程成功后异步打印，不阻塞 UI 与后续业务。
 */
export function schedulePrintChargeReceipt(options: Omit<PrintChargeReceiptOptions, 'background'>) {
  scheduleBackgroundPrint(() =>
    printChargeReceipt({
      ...options,
      background: true,
    }),
  );
}
