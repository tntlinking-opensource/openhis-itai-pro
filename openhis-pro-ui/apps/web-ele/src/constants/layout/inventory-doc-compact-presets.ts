import type { VxeGridPropTypes } from '#/adapter/vxe-table';

/**
 * 库房单据工作站 compact 预设（列宽等）。
 * 订货单保留专表；其它单据用 {@link withInvDocDetailColWidths} 按字段名覆盖。
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

/** 窄屏时按 field / type 覆盖的绝对宽度（default 保留各页原稿）。 */
const INV_DOC_COMPACT_FIELD_WIDTH: Record<string, { width?: number; minWidth?: number }> = {
  medicinePick: { minWidth: 168 },
  itemName: { minWidth: 168 },
  productName: { minWidth: 168 },
  spec: { width: 100 },
  manufacturer: { minWidth: 100 },
  _qtyUnit: { width: 148 },
  _sourceStockQtyUnit: { width: 108 },
  _actualQtyUnit: { width: 148 },
  _plQtyUnit: { width: 148 },
  _bookQtyUnit: { width: 148 },
  unitPrice: { width: 104 },
  price: { width: 100 },
  totalAmount: { width: 96 },
  totalPrice: { width: 96 },
  totalPriceText: { width: 96 },
  actualAmount: { width: 96 },
  profitLossAmount: { width: 96 },
  gainLossAmount: { width: 96 },
  remark: { minWidth: 100 },
  action: { width: 72 },
  lotNumber: { width: 100 },
  batchNo: { width: 100 },
  expirationDate: { width: 100 },
  expiryDate: { width: 100 },
  prodExpiry: { width: 100 },
  productionDate: { width: 100 },
  locationName: { minWidth: 100 },
  warehouseName: { minWidth: 100 },
  supplierName: { minWidth: 120 },
  traceCode: { minWidth: 120 },
  traceNo: { minWidth: 120 },
  approvalNumber: { minWidth: 100 },
  specification: { width: 100 },
  reason: { minWidth: 100 },
  remake: { minWidth: 100 },
};

/**
 * 按视口缩放库房明细列宽：非 compact 原样拷贝；compact 仅覆盖已知 type/field。
 */
export function withInvDocDetailColWidths(
  columns: VxeGridPropTypes.Column[],
  isCompact: boolean,
): VxeGridPropTypes.Column[] {
  if (!isCompact) {
    return columns.map((col) => ({ ...col }));
  }

  return columns.map((col) => {
    const next: VxeGridPropTypes.Column = { ...col };

    if (col.type === 'checkbox') {
      next.width = PURCHASE_ORDER_TABLE_COL_WIDTH.compact.checkbox;
      return next;
    }
    if (col.type === 'seq') {
      next.width = PURCHASE_ORDER_TABLE_COL_WIDTH.compact.seq;
      return next;
    }

    const field = col.field == null ? '' : String(col.field);
    const override = INV_DOC_COMPACT_FIELD_WIDTH[field];
    if (!override) {
      return next;
    }
    if (override.width != null) {
      next.width = override.width;
    }
    if (override.minWidth != null) {
      next.minWidth = override.minWidth;
    }
    return next;
  });
}
