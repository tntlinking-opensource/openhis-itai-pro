import type { PendingChargeItemRowDto } from '#/api/outpatient-charge/outpatient-charge.types';
import type { OutpatientRefundPaymentGroupDto } from '#/api/outpatient-refund/outpatient-refund.types';

import { decimalAdd } from '#/utils/format/decimal-util';

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

/** 将金额格式化为两位小数字符串（展示用） */
export function formatPatientFeeMoney(value: null | number | string | undefined): string {
  const n = Number(value ?? 0);
  if (Number.isNaN(n)) {
    return '0.00';
  }
  return n.toFixed(2);
}

/**
 * 收费工作站：从待收明细汇总「待收」「已收」，与下方明细表同源。
 */
export function summarizeOutpatientChargeFeeAmounts(
  items: PendingChargeItemRowDto[],
  isSettled: (row: PendingChargeItemRowDto) => boolean,
): null | OutpatientChargePatientFeeSummary {
  if (items.length === 0) {
    return null;
  }
  let pendingCollectionAmount = 0;
  let collectedAmount = 0;
  for (const row of items) {
    const amount = row.totalPrice ?? 0;
    if (isSettled(row)) {
      collectedAmount = decimalAdd(collectedAmount, amount, 4);
    } else {
      pendingCollectionAmount = decimalAdd(pendingCollectionAmount, amount, 4);
    }
  }
  return { pendingCollectionAmount, collectedAmount };
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
  let pendingRefundAmount = 0;
  let refundedAmount = 0;
  for (const group of groups) {
    for (const line of group.refundingLines ?? []) {
      const status = (line.chargeItemStatusEnum ?? '').trim().toUpperCase();
      const amount = line.lineTotalAmount ?? 0;
      if (status === 'REFUNDING') {
        pendingRefundAmount = decimalAdd(pendingRefundAmount, amount, 4);
      } else if (status === 'REFUNDED') {
        refundedAmount = decimalAdd(refundedAmount, amount, 4);
      }
    }
  }
  return { pendingRefundAmount, refundedAmount };
}
