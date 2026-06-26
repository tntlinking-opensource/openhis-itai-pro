import type {
  NurseStationCostDetailLineDto,
  NurseStationCostDetailPresentationMode,
  NurseStationCostSummaryLineDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-cost-detail.types';

import { decimalToFixed } from '#/utils/format/decimal-util';

/** 明细行表格 row-key */
export function buildCostDetailLineRowKey(row: NurseStationCostDetailLineDto): string {
  const chargeItemId = row.chargeItemId;
  if (chargeItemId != null && `${chargeItemId}`.trim()) {
    return String(chargeItemId);
  }
  return `${row.productTableName ?? ''}-${row.productId ?? ''}-${row.businessDateTime ?? ''}`;
}

/** 汇总行表格 row-key */
export function buildCostSummaryLineRowKey(row: NurseStationCostSummaryLineDto): string {
  return `${row.productTableName ?? ''}-${row.productId ?? ''}-${row.expenseBlockCode ?? ''}`;
}

/** 明细数量展示：账单数量 + 单位展示名 */
export function formatCostDetailQuantity(row: NurseStationCostDetailLineDto): string {
  const value = row.quantityValue;
  if (value == null || value === '') {
    return '—';
  }
  const unit = row.quantityUnitDisplay ?? row.quantityUnit ?? '';
  return unit ? `${value} ${unit}` : String(value);
}

/** 汇总数量展示：满包装整数 + 不足一包小单位 */
export function formatCostSummaryQuantity(row: NurseStationCostSummaryLineDto): string {
  const parts: string[] = [];
  const packageQuantity = row.packageQuantity;
  if (packageQuantity != null && packageQuantity > 0) {
    const unit = row.packageUnitDisplay ?? row.packageUnitCode ?? '';
    parts.push(`${packageQuantity}${unit}`);
  }
  const splitQuantity = row.splitUnitQuantity;
  if (splitQuantity != null && Number(splitQuantity) > 0) {
    const unit = row.splitUnitDisplay ?? row.splitUnitCode ?? '';
    parts.push(`${splitQuantity}${unit}`);
  }
  return parts.length > 0 ? parts.join(' + ') : '—';
}

/** 金额格式化为两位小数文案 */
export function formatCostDetailMoney(value: number | string | undefined, dp = 2): string {
  if (value == null || value === '') {
    return '—';
  }
  return `￥${decimalToFixed(value, dp).toFixed(dp)}`;
}

/** 单价格式化为四位小数 */
export function formatCostDetailUnitPrice(value: number | string | undefined): string {
  if (value == null || value === '') {
    return '—';
  }
  return decimalToFixed(value, 4).toFixed(4);
}

/** 校验展示模式是否合法 */
export function isCostDetailPresentationMode(
  value: string | undefined,
): value is NurseStationCostDetailPresentationMode {
  return value === 'DETAIL' || value === 'SUMMARY';
}
