import type {
  NurseStationCostDetailLineDto,
  NurseStationCostDetailPresentationMode,
  NurseStationCostDetailStatusFilter,
  NurseStationCostSummaryActivityLineDto,
  NurseStationCostSummaryLineDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-cost-detail.types';

import { decimalToFixed } from '#/utils/format/decimal-util';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

/** 明细行表格 row-key */
export function buildCostDetailLineRowKey(row: NurseStationCostDetailLineDto): string {
  const chargeItemId = row.chargeItemId;
  if (chargeItemId != null && `${chargeItemId}`.trim()) {
    return String(chargeItemId);
  }
  return `${row.productTableName ?? ''}-${row.productId ?? ''}-${row.businessDateTime ?? ''}`;
}

/**
 * 费用明细「业务时间」列展示值：优先执行/划价事实时刻 displayDateTime，回落逻辑 occurrence。
 */
export function resolveCostDetailDisplayDateTime(row: {
  displayDateTime?: string;
  businessDateTime?: string;
  activityDateTime?: string;
}): string {
  const display = String(row.displayDateTime ?? '').trim();
  if (display) return display;
  const business = String(row.businessDateTime ?? '').trim();
  if (business) return business;
  return String(row.activityDateTime ?? '').trim();
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
  const unit = String(row.quantityUnitDisplay || row.quantityUnit || '').trim();
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

/** 汇总展开明细数量展示 */
export function formatCostSummaryActivityQuantity(
  row: NurseStationCostSummaryActivityLineDto,
): string {
  const value = row.quantityValue;
  if (value == null || value === '') {
    return '—';
  }
  const unit = String(row.quantityUnitDisplay || row.quantityUnit || '').trim();
  return unit ? `${value} ${unit}` : String(value);
}

/** 汇总展开明细行 row-key */
export function buildCostSummaryActivityLineRowKey(
  row: NurseStationCostSummaryActivityLineDto,
  index: number,
): string {
  const chargeItemId = row.chargeItemId;
  if (chargeItemId != null && `${chargeItemId}`.trim()) {
    return String(chargeItemId);
  }
  return `${row.activityDateTime ?? ''}-${row.activityTypeEnum ?? ''}-${index}`;
}

/** 金额格式化为两位小数文案 */
export function formatCostDetailMoney(value: number | string | undefined, dp = 2): string {
  if (value == null || value === '') {
    return '—';
  }
  return `￥${decimalToFixed(value, dp).toFixed(dp)}`;
}

/** 单价格式化为两位小数（与 BR-MON-001 展示层一致） */
export function formatCostDetailUnitPrice(value: number | string | undefined): string {
  if (value == null || value === '') {
    return '—';
  }
  return formatMoneyDisplay(value);
}

/** 校验展示模式是否合法 */
export function isCostDetailPresentationMode(
  value: string | undefined,
): value is NurseStationCostDetailPresentationMode {
  return value === 'DETAIL' || value === 'SUMMARY';
}

/** 校验费用状态筛选是否合法 */
export function isCostDetailStatusFilter(
  value: string | undefined,
): value is NurseStationCostDetailStatusFilter {
  return value === 'PENDING_SETTLEMENT' || value === 'SETTLED';
}
