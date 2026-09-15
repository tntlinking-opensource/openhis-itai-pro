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

/** 零元号判定容差（元） */
const ZERO_YUAN_EPS = 0.001;

export interface SkipAutoPrintRegistrationReceiptInput {
  /**
   * 号别原价回退值。医保预结后接口 `totalAmount` 会被改成现金应付，不能单独当原价。
   */
  originalFeeAmount?: unknown;
  /** 预结算收费明细（金额为号别原价） */
  chargeItems?: Array<{ amount?: unknown }> | null;
  insurancePreSettled?: boolean | null;
  ybFundPaySumamt?: unknown;
  ybAcctPay?: unknown;
  ybMedfeeSumamt?: unknown;
}

/** 把未知金额转成有限数字，非法值当 0。 */
function toMoneyAmount(value: unknown): number {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

/** 累加收费明细原价；明细为空时返回 0。 */
function sumChargeItemOriginalAmounts(
  chargeItems: Array<{ amount?: unknown }> | null | undefined,
): number {
  if (!Array.isArray(chargeItems) || chargeItems.length === 0) {
    return 0;
  }
  let sum = 0;
  for (const item of chargeItems) {
    // 只加明细原价，不用报销后现金应付
    sum += toMoneyAmount(item.amount);
  }
  return sum;
}

/**
 * 挂号成功后是否跳过自动打小票。
 * 仅真正零元号（号别原价为 0 且未走医保）跳过；有价号别医保全额报销后现金为 0 仍要打。
 */
export function shouldSkipAutoPrintRegistrationReceipt(
  input: SkipAutoPrintRegistrationReceiptInput,
): boolean {
  const fromItems = sumChargeItemOriginalAmounts(input.chargeItems);
  const originalFee =
    fromItems > ZERO_YUAN_EPS ? fromItems : toMoneyAmount(input.originalFeeAmount);
  const hasInsurance =
    Boolean(input.insurancePreSettled) ||
    toMoneyAmount(input.ybFundPaySumamt) > ZERO_YUAN_EPS ||
    toMoneyAmount(input.ybAcctPay) > ZERO_YUAN_EPS ||
    toMoneyAmount(input.ybMedfeeSumamt) > ZERO_YUAN_EPS;
  if (hasInsurance) {
    return false;
  }
  return originalFee <= ZERO_YUAN_EPS;
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
