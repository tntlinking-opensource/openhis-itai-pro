/**
 * 医技费用清单：状态/汇总本地装配（对齐护士站费用明细交互，无日期筛）。
 *
 * <p>收费状态（ChargeItem.status）与确费（fee_confirmer_id）是两个字段，禁止混在同一筛选项。</p>
 */
import type { MedicalTechFeeItemRowDto } from '#/api/medical-tech/medical-tech-fee-list.types';

import { decimalAdd } from '#/utils/format/decimal-util';
import { formatMoneyDisplay, formatMoneyYuanWithSymbol } from '#/utils/format/money-scale';

/** 明细 / 汇总 */
export type MedicalTechFeePresentationMode = 'DETAIL' | 'SUMMARY';

/** 收费状态筛（adm_charge_item.status_enum） */
export type MedicalTechFeeChargeStatusFilter = 'ALL' | 'PENDING_SETTLEMENT' | 'SETTLED';

/** 确费状态筛（fee_confirmer_id 有无） */
export type MedicalTechFeeConfirmFilter = 'ALL' | 'CONFIRMED' | 'UNCONFIRMED';

export interface MedicalTechFeeFilterOption<T extends string> {
  code: T;
  display: string;
}

export interface MedicalTechFeeSummaryLine {
  itemCode: string;
  itemName: string;
  lineCount: number;
  quantity: number;
  rowKey: string;
  totalPrice: number;
  unitPrice: null | number | string;
}

export const MT_FEE_PRESENTATION_OPTIONS = [
  { code: 'DETAIL' as const, display: '明细' },
  { code: 'SUMMARY' as const, display: '汇总' },
];

export const MT_FEE_CHARGE_STATUS_OPTIONS: MedicalTechFeeFilterOption<MedicalTechFeeChargeStatusFilter>[] =
  [
    { code: 'ALL', display: '全部' },
    { code: 'PENDING_SETTLEMENT', display: '待结算' },
    { code: 'SETTLED', display: '已结算' },
  ];

export const MT_FEE_CONFIRM_OPTIONS: MedicalTechFeeFilterOption<MedicalTechFeeConfirmFilter>[] = [
  { code: 'ALL', display: '全部' },
  { code: 'CONFIRMED', display: '已确费' },
  { code: 'UNCONFIRMED', display: '未确费' },
];

/** 规范化收费状态码 */
function normalizeChargeStatus(status?: null | string): string {
  return String(status ?? '')
    .trim()
    .toUpperCase();
}

/** 行是否已确费 */
export function isMedicalTechFeeConfirmed(row: MedicalTechFeeItemRowDto): boolean {
  return Boolean(String(row.feeConfirmerId ?? '').trim());
}

/** 行是否命中收费状态筛 */
export function matchMedicalTechFeeChargeStatus(
  row: MedicalTechFeeItemRowDto,
  statusFilter: MedicalTechFeeChargeStatusFilter,
): boolean {
  if (statusFilter === 'ALL') {
    return true;
  }
  const status = normalizeChargeStatus(row.chargeStatusEnum);
  if (statusFilter === 'PENDING_SETTLEMENT') {
    return status === 'PENDING_SETTLEMENT';
  }
  return status === 'SETTLED' || status.includes('SETTLED') || status === 'BILLED';
}

/** 行是否命中确费筛 */
export function matchMedicalTechFeeConfirm(
  row: MedicalTechFeeItemRowDto,
  confirmFilter: MedicalTechFeeConfirmFilter,
): boolean {
  if (confirmFilter === 'ALL') {
    return true;
  }
  const confirmed = isMedicalTechFeeConfirmed(row);
  if (confirmFilter === 'CONFIRMED') {
    return confirmed;
  }
  return !confirmed;
}

/** 行是否命中项目关键字（名称/编码） */
export function matchMedicalTechFeeKeyword(
  row: MedicalTechFeeItemRowDto,
  keyword: string,
): boolean {
  const key = String(keyword ?? '')
    .trim()
    .toLowerCase();
  if (!key) {
    return true;
  }
  const name = String(row.itemName ?? '')
    .trim()
    .toLowerCase();
  const code = String(row.itemCode ?? '')
    .trim()
    .toLowerCase();
  return name.includes(key) || code.includes(key);
}

/** 按项目+编码+单价汇总；净数量为 0 的冲销轧平行不展示 */
export function buildMedicalTechFeeSummaryLines(
  rows: MedicalTechFeeItemRowDto[],
): MedicalTechFeeSummaryLine[] {
  const bucket = new Map<string, MedicalTechFeeSummaryLine>();
  for (const row of rows) {
    const itemCode = String(row.itemCode ?? '').trim();
    const itemName = String(row.itemName ?? '').trim() || '—';
    const unitPrice = row.unitPrice ?? null;
    const key = `${itemCode}|${itemName}|${String(unitPrice ?? '')}`;
    const existing = bucket.get(key);
    const qty = Number(row.quantity);
    const amt = Number(row.totalPrice);
    const safeQty = Number.isFinite(qty) ? qty : 0;
    const safeAmt = Number.isFinite(amt) ? amt : 0;
    if (existing) {
      existing.quantity = Number(decimalAdd(existing.quantity, safeQty));
      existing.totalPrice = Number(decimalAdd(existing.totalPrice, safeAmt));
      existing.lineCount += 1;
      continue;
    }
    bucket.set(key, {
      rowKey: key,
      itemCode,
      itemName,
      unitPrice,
      quantity: safeQty,
      totalPrice: safeAmt,
      lineCount: 1,
    });
  }
  // 冲销轧平后净数量为 0：汇总无意义，直接剔除
  return [...bucket.values()].filter((line) => {
    const qty = Number(line.quantity);
    return Number.isFinite(qty) && qty !== 0;
  });
}

/** 合计金额展示 */
export function formatMedicalTechFeeSum(amount: null | number | string | undefined): string {
  if (amount === null || amount === undefined || amount === '') {
    return '—';
  }
  return formatMoneyYuanWithSymbol(amount);
}

/** 单价/金额列展示 */
export function formatMedicalTechFeeMoney(value: null | number | string | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  return formatMoneyDisplay(value);
}

/** 确费列文案 */
export function formatMedicalTechFeeConfirmLabel(row: MedicalTechFeeItemRowDto): string {
  return isMedicalTechFeeConfirmed(row) ? '已确费' : '未确费';
}
