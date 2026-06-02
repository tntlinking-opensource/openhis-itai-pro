/**
 * 盘库/盘点单底部操作条：与药库出库、药房入库同一套 SupplyStatus 门控。
 */

import type { InboundReceiptDocActionFlags } from '#/utils/inventory/inbound-receipt-status';

import {
  buildStockOutDocActionFlags,
  normalizeStockOutStatusSort,
  stockOutStatusLabel,
} from '#/utils/inventory/stock-out-doc-actions';

export { stockOutStatusLabel as stocktakeStatusLabel };
export { normalizeStockOutStatusSort as normalizeStocktakeStatusSort };

export function buildStocktakeDocActionFlags(p: {
  hasPersistedBusNo: boolean;
  isDraft: boolean;
  status?: null | number | string;
}): InboundReceiptDocActionFlags {
  return buildStockOutDocActionFlags(p);
}
