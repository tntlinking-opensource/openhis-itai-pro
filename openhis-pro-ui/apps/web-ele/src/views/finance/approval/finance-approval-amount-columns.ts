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
 * 按列键取列表行原始金额（空则 undefined；供应列仅进货/退货）。
 *
 * @param row 列表行
 * @param key 金额列键
 */
export function resolveFinanceApprovalAmountRaw(
  row: FinanceApprovalDocumentRowDto,
  key: FinanceApprovalAmountColumnKey,
): number | string | undefined {
  if (key === 'purchaseImpact') {
    const amount = row.priceAdjustmentPurchaseImpact;
    if (amount === undefined || amount === null || amount === '') return undefined;
    return amount;
  }
  if (key === 'sellingImpact') {
    const amount = row.priceAdjustmentSellingImpact;
    if (amount === undefined || amount === null || amount === '') return undefined;
    return amount;
  }
  if (key === 'stocktakeActual') {
    const amount = row.stocktakeActualAmount;
    if (amount === undefined || amount === null || amount === '') return undefined;
    return amount;
  }
  if (key === 'profitLoss') {
    const amount = row.profitLossAmount;
    if (amount === undefined || amount === null || amount === '') return undefined;
    return amount;
  }
  // 供应「金额」：仅进货/退货，禁止回落盘点实盘或盈亏
  const candidates = [row.inboundAmount, row.returnAmount];
  for (const amount of candidates) {
    if (amount === undefined || amount === null || amount === '') continue;
    return amount;
  }
  return undefined;
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
  const raw = resolveFinanceApprovalAmountRaw(row, key);
  if (raw === undefined) return '';
  return formatMoneyDisplay(raw);
}

/** 财务审批「查看」深链金额 query 前缀 */
export const FINANCE_APPROVAL_VIEW_AMOUNT_QUERY_PREFIX = 'faAmt_';

/** 财务审批「查看」深链类型 query 键（驱动金额列标签） */
export const FINANCE_APPROVAL_VIEW_TYPE_QUERY_KEY = 'faType';

/** 详情页脚合计项（对齐列表金额列） */
export interface FinanceApprovalViewSummaryItem {
  label: string;
  value: string;
}

/**
 * 组装财务审批「查看」深链 query 中的金额字段（与列表列口径一致）。
 *
 * @param row 列表行
 * @param typeCode 路由用单据类型码
 */
export function buildFinanceApprovalViewAmountQuery(
  row: FinanceApprovalDocumentRowDto,
  typeCode: string,
): Record<string, string> {
  const query: Record<string, string> = {
    [FINANCE_APPROVAL_VIEW_TYPE_QUERY_KEY]: typeCode,
  };
  // 按该类型金额列写入非空原始值，详情页脚原样展示
  for (const col of resolveFinanceApprovalAmountColumns(typeCode)) {
    const raw = resolveFinanceApprovalAmountRaw(row, col.key);
    if (raw === undefined) continue;
    query[`${FINANCE_APPROVAL_VIEW_AMOUNT_QUERY_PREFIX}${col.key}`] = String(raw);
  }
  return query;
}

/**
 * 从路由 query 解析财务审批查看页脚合计项。
 *
 * @param query 路由 query
 */
export function resolveFinanceApprovalViewSummaryItems(
  query: Record<string, unknown>,
): FinanceApprovalViewSummaryItem[] {
  const typeRaw = query[FINANCE_APPROVAL_VIEW_TYPE_QUERY_KEY];
  const typeEnum = Array.isArray(typeRaw)
    ? String(typeRaw[0] ?? '').trim()
    : String(typeRaw ?? '').trim();
  const items: FinanceApprovalViewSummaryItem[] = [];
  // 仅展示该类型列表列；无值则跳过（与列表空单元格一致）
  for (const col of resolveFinanceApprovalAmountColumns(typeEnum || undefined)) {
    const key = `${FINANCE_APPROVAL_VIEW_AMOUNT_QUERY_PREFIX}${col.key}`;
    const raw = query[key];
    const text = Array.isArray(raw) ? String(raw[0] ?? '').trim() : String(raw ?? '').trim();
    if (!text) continue;
    items.push({
      label: `${col.label}合计`,
      value: formatMoneyDisplay(text),
    });
  }
  return items;
}
