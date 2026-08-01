/**
 * 财务审批列表金额列：按筛选单据类型动态表头（AS-FIN-APR-001 动态金额列）。
 */
import type { FinanceApprovalDocumentRowDto } from '#/api/finance/approval';

import { formatMoneyDisplay } from '#/utils/format/money-scale';

/** 列表类型码：全部 */
export const FINANCE_APPROVAL_TYPE_ALL = 'ALL';
/** 列表类型码：调价 */
export const FINANCE_APPROVAL_TYPE_PRICE = 'PRICE_ADJUSTMENT';

/** 损益三码 */
const GAIN_LOSS_TYPE_CODES = new Set(['CABINET_GAIN_LOSS', 'PHARMACY_GAIN_LOSS', 'DEPT_GAIN_LOSS']);

/** 盘点三码 */
const STOCKTAKE_TYPE_CODES = new Set(['CABINET_STOCKTAKE', 'PHARMACY_STOCKTAKE', 'DEPT_STOCKTAKE']);

/** 金额列键 */
export type FinanceApprovalAmountColumnKey =
  | 'supplyAmount'
  | 'stocktakeActual'
  | 'profitLoss'
  | 'purchaseImpact'
  | 'sellingImpact';

/** 金额列定义 */
export interface FinanceApprovalAmountColumnDef {
  key: FinanceApprovalAmountColumnKey;
  label: string;
}

/**
 * 按筛选单据类型解析金额列表头。
 *
 * @param typeEnum 当前筛选类型；空/ALL=字段并集列（行上有值才填）
 */
export function resolveFinanceApprovalAmountColumns(
  typeEnum?: string,
): FinanceApprovalAmountColumnDef[] {
  const type = String(typeEnum ?? '').trim();
  // 空或全部：供应金额 / 盘点实盘+盈亏 / 调价进价·售价分列并存，禁止混填
  if (!type || type === FINANCE_APPROVAL_TYPE_ALL) {
    return [
      { key: 'supplyAmount', label: '金额' },
      { key: 'stocktakeActual', label: '实盘金额' },
      { key: 'profitLoss', label: '盈亏金额' },
      { key: 'purchaseImpact', label: '进价盈亏' },
      { key: 'sellingImpact', label: '售价盈亏' },
    ];
  }
  // 调价：仅进价/售价盈亏，不展示供应「金额」列
  if (type === FINANCE_APPROVAL_TYPE_PRICE) {
    return [
      { key: 'purchaseImpact', label: '进价盈亏' },
      { key: 'sellingImpact', label: '售价盈亏' },
    ];
  }
  if (type === 'CABINET_INBOUND_RECEIPT') {
    return [{ key: 'supplyAmount', label: '进货金额' }];
  }
  if (type === 'CABINET_RETURN_TO_SUPPLIER') {
    return [{ key: 'supplyAmount', label: '退货金额' }];
  }
  if (GAIN_LOSS_TYPE_CODES.has(type)) {
    return [{ key: 'profitLoss', label: '盈亏金额' }];
  }
  if (STOCKTAKE_TYPE_CODES.has(type)) {
    return [
      { key: 'stocktakeActual', label: '实盘金额' },
      { key: 'profitLoss', label: '盈亏金额' },
    ];
  }
  return [{ key: 'supplyAmount', label: '金额' }];
}

/**
 * 调价筛选时库房/供应商无业务含义，应隐藏列。
 *
 * @param typeEnum 当前筛选类型
 */
export function shouldShowFinanceApprovalLocationSupplier(typeEnum?: string): boolean {
  return String(typeEnum ?? '').trim() !== FINANCE_APPROVAL_TYPE_PRICE;
}

/**
 * 金额单元格展示（按列键取字段；调价分列禁止加总；供应金额不吞盘点/损益）。
 *
 * @param row 列表行
 * @param key 金额列键
 */
export function resolveFinanceApprovalAmountCell(
  row: FinanceApprovalDocumentRowDto,
  key: FinanceApprovalAmountColumnKey,
): string {
  if (key === 'purchaseImpact') {
    const amount = row.priceAdjustmentPurchaseImpact;
    if (amount === undefined || amount === null || amount === '') return '';
    return formatMoneyDisplay(amount);
  }
  if (key === 'sellingImpact') {
    const amount = row.priceAdjustmentSellingImpact;
    if (amount === undefined || amount === null || amount === '') return '';
    return formatMoneyDisplay(amount);
  }
  if (key === 'stocktakeActual') {
    const amount = row.stocktakeActualAmount;
    if (amount === undefined || amount === null || amount === '') return '';
    return formatMoneyDisplay(amount);
  }
  if (key === 'profitLoss') {
    const amount = row.profitLossAmount;
    if (amount === undefined || amount === null || amount === '') return '';
    return formatMoneyDisplay(amount);
  }
  // 供应「金额」：仅进货/退货，禁止回落盘点实盘或盈亏（避免全部视图丢列）
  const candidates = [row.inboundAmount, row.returnAmount];
  for (const amount of candidates) {
    if (amount === undefined || amount === null || amount === '') continue;
    return formatMoneyDisplay(amount);
  }
  return '';
}
