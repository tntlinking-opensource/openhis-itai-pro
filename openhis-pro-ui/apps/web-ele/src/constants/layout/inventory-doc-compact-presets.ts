/**
 * 库房单据工作站 compact 预设（列宽等）。
 * 先订货单试点；其它单据页批量铺开时复用/追加。
 */

/** 药库订货单明细列宽：default 与现网 1920 一致；compact 仅 <1680。 */
export const PURCHASE_ORDER_TABLE_COL_WIDTH = {
  default: {
    checkbox: 48,
    seq: 52,
    medicinePickMin: 200,
    spec: 120,
    manufacturerMin: 120,
    qtyUnit: 168,
    unitPrice: 120,
    totalAmount: 112,
    remarkMin: 120,
    action: 88,
  },
  compact: {
    checkbox: 44,
    seq: 48,
    medicinePickMin: 168,
    spec: 100,
    manufacturerMin: 100,
    qtyUnit: 148,
    unitPrice: 104,
    totalAmount: 96,
    remarkMin: 100,
    action: 72,
  },
} as const;

export type PurchaseOrderTableColWidthPreset = (typeof PURCHASE_ORDER_TABLE_COL_WIDTH)['default'];
