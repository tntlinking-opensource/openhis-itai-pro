import { ElMessage } from 'element-plus';

import { httpPrintRegisterTicket } from '#/api/outpatient-registration';

import { print, PRINT_SCENARIO, scheduleBackgroundPrint } from './runtime';

export interface PrintRegistrationReceiptOptions {
  encounterId?: null | number | string;
  paymentId?: null | number | string;
  jobName?: string;
  /** 伴随主流程打印：不弹打印机选择框（默认 false，补打等显式操作为 false） */
  background?: boolean;
}

/**
 * 拉取后端挂号小票数据并静默打印（prepare → hiprint → confirm）。
 */
export async function printRegistrationReceipt(options: PrintRegistrationReceiptOptions) {
  const encounterId = String(options.encounterId ?? '').trim();
  const paymentId = String(options.paymentId ?? '').trim();
  if (!encounterId && !paymentId) {
    ElMessage.warning('缺少就诊或支付信息，无法打印挂号小票');
    return { ok: false as const };
  }

  let slip;
  try {
    const result = await httpPrintRegisterTicket({
      encounterId: encounterId || undefined,
      paymentId: paymentId || undefined,
    });
    slip = result.slip;
  } catch (error) {
    const message = error instanceof Error ? error.message : '获取挂号小票数据失败';
    ElMessage.error(message);
    return { ok: false as const, errorMessage: message };
  }

  if (!slip) {
    ElMessage.warning('未获取到挂号小票数据');
    return { ok: false as const };
  }

  return print({
    scenarioCode: PRINT_SCENARIO.opRegPrint,
    source: slip,
    bizRef: {
      scenarioCode: PRINT_SCENARIO.opRegPrint,
      encounterId: slip.encounterId ?? (encounterId || undefined),
      paymentId: paymentId || undefined,
    },
    jobName: options.jobName ?? '挂号小票',
    selectPrinter: options.background ? 'never' : 'auto',
    background: options.background === true,
  });
}

/**
 * 挂号主流程成功后异步打印，不阻塞 UI 与后续业务（错误在 {@link printRegistrationReceipt} 内提示）。
 */
export function schedulePrintRegistrationReceipt(
  options: Omit<PrintRegistrationReceiptOptions, 'background'>,
) {
  scheduleBackgroundPrint(() =>
    printRegistrationReceipt({
      ...options,
      background: true,
    }),
  );
}
