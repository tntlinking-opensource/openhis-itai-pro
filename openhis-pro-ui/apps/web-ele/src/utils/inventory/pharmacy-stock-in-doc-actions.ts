/**
 * 药房入库单底部操作条：与药库出库单同一套状态门控（待审核可改、审核中可审等）。
 */

import type { InboundReceiptDocActionFlags } from '#/utils/inventory/inbound-receipt-status';

import {
  buildStockOutDocActionFlags,
  normalizeStockOutStatusSort,
  stockOutStatusLabel,
} from '#/utils/inventory/stock-out-doc-actions';

export { stockOutStatusLabel as pharmacyStockInStatusLabel };

/** @deprecated 请用 {@link normalizeStockOutStatusSort} */
export const normalizePharmacyStockInStatusSort = normalizeStockOutStatusSort;

export function buildPharmacyStockInDocActionFlags(p: {
  hasPersistedBusNo: boolean;
  isDraft: boolean;
  status?: null | number | string;
}): InboundReceiptDocActionFlags {
  return buildStockOutDocActionFlags(p);
}
