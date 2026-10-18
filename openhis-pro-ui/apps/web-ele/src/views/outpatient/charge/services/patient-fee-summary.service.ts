import type { PendingChargeItemRowDto } from '#/api/outpatient-charge/outpatient-charge.types';
import type { OutpatientRefundPaymentGroupDto } from '#/api/outpatient-refund/outpatient-refund.types';

import {
  formatMoneyDisplay,
  sumMoneyThenCalc,
  sumMoneyThenSettle,
} from '#/utils/format/money-scale';

/** 患者信息栏费用摘要：待收 + 已收（已配置有效折扣时另含折扣额） */
export interface OutpatientChargePatientFeeSummary {
  collectedAmount: number;
  /** 已结算行原价合计 − 已收；未配置有效折扣时为 0 */
  discountGapAmount: number;
  pendingCollectionAmount: number;
}

/** 患者信息栏费用摘要：待退 + 已退 */
export interface OutpatientRefundPatientFeeSummary {
  pendingRefundAmount: number;
  refundedAmount: number;
}

/** 收费费用摘要可选口径 */
export interface SummarizeOutpatientChargeFeeAmountsOptions {
  /** 产品已配置 ACTIVE 折扣内容时为 true；为 true 时待收按原价合计、已收按折后应收 */
  discountFeatureEnabled?: boolean;
}

/** 将金额格式化为两位小数字符串（展示用；权威应收以后端预结算为准） */
export function formatPatientFeeMoney(value: null | number | string | undefined): string {
  return formatMoneyDisplay(value);
}

/**
 * 把接口金额转成可加总的数字；空或非法按 0，避免把缺失当 NaN 污染合计。
 *
 * @param value 行上的金额字段
 */
function toChargeLineMoneyNumber(value: null | number | string | undefined): number {
  if (value === null || value === undefined || value === '') {
    return 0;
  }
  const parsed = Number(value);
  if (Number.isNaN(parsed)) {
    return 0;
  }
  return parsed;
}

/**
 * 行实收刻度：优先库内折后应收 discountAmount，否则原价。
 * 未打折行 discountAmount 通常等于 totalPrice 或为空。
 *
 * @param row 收费明细行
 */
function resolveChargeLineCollectedAmount(row: PendingChargeItemRowDto): number {
  if (row.discountAmount === null || row.discountAmount === undefined || row.discountAmount === '') {
    return toChargeLineMoneyNumber(row.totalPrice);
  }
  return toChargeLineMoneyNumber(row.discountAmount);
}

/**
 * 收费工作站：从待收明细汇总「待收」「已收」，与下方明细表同源。
 * 行金额按 6 位加总，摘要展示按 2 位 settle（非支付权威）。
 *
 * 未配置有效折扣：待收=未结原价、已收=已结原价。
 * 已配置有效折扣：待收=本屏全部行原价合计（收费后仍对照折前）；已收=已结行折后应收；折扣=已结原价−已收。
 */
export function summarizeOutpatientChargeFeeAmounts(
  items: PendingChargeItemRowDto[],
  isSettled: (row: PendingChargeItemRowDto) => boolean,
  options?: SummarizeOutpatientChargeFeeAmountsOptions,
): null | OutpatientChargePatientFeeSummary {
  if (items.length === 0) {
    return null;
  }
  const discountFeatureEnabled = Boolean(options?.discountFeatureEnabled);
  if (discountFeatureEnabled) {
    return summarizeChargeFeeAmountsWithDiscount(items, isSettled);
  }
  return summarizeChargeFeeAmountsByStatus(items, isSettled);
}

/**
 * 无折扣配置：按收费状态把原价拆成待收 / 已收，不展示折扣额。
 *
 * @param items 本屏明细
 * @param isSettled 是否已结算
 */
function summarizeChargeFeeAmountsByStatus(
  items: PendingChargeItemRowDto[],
  isSettled: (row: PendingChargeItemRowDto) => boolean,
): OutpatientChargePatientFeeSummary {
  const pendingAmounts: number[] = [];
  const collectedAmounts: number[] = [];
  for (const row of items) {
    const amount = toChargeLineMoneyNumber(row.totalPrice);
    if (isSettled(row)) {
      collectedAmounts.push(amount);
    } else {
      pendingAmounts.push(amount);
    }
  }
  return {
    pendingCollectionAmount: sumMoneyThenSettle(pendingAmounts),
    collectedAmount: sumMoneyThenSettle(collectedAmounts),
    discountGapAmount: 0,
  };
}

/**
 * 已配置折扣：待收始终是原价合计，已收只计已结算折后应收，折扣只计已落账优惠。
 *
 * @param items 本屏明细
 * @param isSettled 是否已结算
 */
function summarizeChargeFeeAmountsWithDiscount(
  items: PendingChargeItemRowDto[],
  isSettled: (row: PendingChargeItemRowDto) => boolean,
): OutpatientChargePatientFeeSummary {
  const originalAmounts: number[] = [];
  const collectedAmounts: number[] = [];
  const settledOriginalAmounts: number[] = [];
  for (const row of items) {
    const original = toChargeLineMoneyNumber(row.totalPrice);
    originalAmounts.push(original);
    if (isSettled(row)) {
      collectedAmounts.push(resolveChargeLineCollectedAmount(row));
      settledOriginalAmounts.push(original);
    }
  }
  const collectedAmount = sumMoneyThenSettle(collectedAmounts);
  const settledOriginal = sumMoneyThenSettle(settledOriginalAmounts);
  return {
    pendingCollectionAmount: sumMoneyThenSettle(originalAmounts),
    collectedAmount,
    discountGapAmount: sumMoneyThenSettle([settledOriginal, collectedAmount * -1]),
  };
}

/**
 * 把库内折后应收写到列表「折后价」列；已结算行不再走试算。
 *
 * @param items IF-OPC-03 明细
 */
export function seedPersistedDiscountedAmounts(
  items: PendingChargeItemRowDto[],
): PendingChargeItemRowDto[] {
  const seeded: PendingChargeItemRowDto[] = [];
  for (const row of items) {
    seeded.push(seedPersistedDiscountedAmountOnRow(row));
  }
  return seeded;
}

/**
 * 单行：有折后应收则同步 discountedAmount，供已结算列表展示折后价。
 *
 * @param row 收费明细行
 */
export function seedPersistedDiscountedAmountOnRow(
  row: PendingChargeItemRowDto,
): PendingChargeItemRowDto {
  if (row.discountAmount === null || row.discountAmount === undefined || row.discountAmount === '') {
    return row;
  }
  return { ...row, discountedAmount: row.discountAmount };
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
