import type { PendingChargeItemRowDto } from '#/api/outpatient-charge/outpatient-charge.types';
import type { OutpatientRefundPaymentGroupDto } from '#/api/outpatient-refund/outpatient-refund.types';

import {
  formatMoneyDisplay,
  sumMoneyThenCalc,
  sumMoneyThenSettle,
} from '#/utils/format/money-scale';

/** 患者信息栏费用摘要：待收 + 已收 */
export interface OutpatientChargePatientFeeSummary {
  collectedAmount: number;
  pendingCollectionAmount: number;
}

/** 患者信息栏费用摘要：待退 + 已退 */
export interface OutpatientRefundPatientFeeSummary {
  pendingRefundAmount: number;
  refundedAmount: number;
}

/** 将金额格式化为两位小数字符串（展示用；权威应收以后端预结算为准） */
export function formatPatientFeeMoney(value: null | number | string | undefined): string {
  return formatMoneyDisplay(value);
}

/**
 * 收费工作站：从待收明细汇总「待收」「已收」，与下方明细表同源。
 * 行金额按 6 位加总，摘要展示按 2 位 settle（非支付权威）。
 */
export function summarizeOutpatientChargeFeeAmounts(
  items: PendingChargeItemRowDto[],
  isSettled: (row: PendingChargeItemRowDto) => boolean,
): null | OutpatientChargePatientFeeSummary {
  if (items.length === 0) {
    return null;
  }
  const pendingAmounts: number[] = [];
  const collectedAmounts: number[] = [];
  for (const row of items) {
    const amount = row.totalPrice ?? 0;
    if (isSettled(row)) {
      collectedAmounts.push(amount);
    } else {
      pendingAmounts.push(amount);
    }
  }
  return {
    pendingCollectionAmount: sumMoneyThenSettle(pendingAmounts),
    collectedAmount: sumMoneyThenSettle(collectedAmounts),
  };
}

/**
 * 退费工作站：从支付分组明细行按状态汇总「待退」「已退」。
 * REFUNDING 计入待退，REFUNDED 计入已退，与明细表状态列一致。
 */
export function summarizeOutpatientRefundFeeAmounts(
  groups: OutpatientRefundPaymentGroupDto[],
): null | OutpatientRefundPatientFeeSummary {
  if (groups.length === 0) {
    return null;
  }
  const pendingAmounts: number[] = [];
  const refundedAmounts: number[] = [];
  for (const group of groups) {
    for (const line of group.refundingLines ?? []) {
      const status = (line.chargeItemStatusEnum ?? '').trim().toUpperCase();
      const amount = line.lineTotalAmount ?? 0;
      if (status === 'REFUNDING') {
        pendingAmounts.push(amount);
      } else if (status === 'REFUNDED') {
        refundedAmounts.push(amount);
      }
    }
  }
  return {
    pendingRefundAmount: sumMoneyThenSettle(pendingAmounts),
    refundedAmount: sumMoneyThenSettle(refundedAmounts),
  };
}

/** 块内行金额 6 位加总（非支付场景） */
export function sumChargeLineAmountsCalc(values: number[]): number {
  return sumMoneyThenCalc(values);
}
