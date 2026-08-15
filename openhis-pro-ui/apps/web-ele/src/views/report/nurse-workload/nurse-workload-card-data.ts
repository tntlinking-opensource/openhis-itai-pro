import type {
  NurseConsumableDetailRowDto,
  NurseConsumableSummaryRowDto,
  NurseExecutionDetailRowDto,
  NurseExecutionSummaryRowDto,
} from '#/api/report/nurse-workload';

import { decimalAdd } from '#/utils/format/decimal-util';
import { fmtReportNumber } from '#/views/report/shared/report-format';

export interface NurseConsumableItemChip {
  key: string;
  label: string;
  quantity: number;
}

export interface NurseExecutionItemChip {
  count: number;
  key: string;
  label: string;
}

/** 拼接耗材展示名（名称 + 规格）。 */
export function buildConsumableItemLabel(deviceName?: string, specText?: string) {
  const name = (deviceName ?? '').trim();
  const spec = (specText ?? '').trim();
  if (name && spec) {
    return `${name}(${spec})`;
  }
  return name || spec || '未知耗材';
}

/** 将耗材明细分组为卡片标签（按耗材定义 + 展示名聚合数量）。 */
export function aggregateConsumableDetailRows(
  rows: NurseConsumableDetailRowDto[],
): NurseConsumableItemChip[] {
  const bucket = new Map<string, NurseConsumableItemChip>();
  for (const row of rows) {
    const label = buildConsumableItemLabel(row.deviceName, row.specText);
    const key = `${row.deviceDefId ?? ''}::${label}`;
    const quantity = Number(row.quantity ?? 0);
    const existing = bucket.get(key);
    if (existing) {
      existing.quantity += quantity;
      continue;
    }
    bucket.set(key, { key, label, quantity });
  }
  return Array.from(bucket.values()).sort((left, right) => right.quantity - left.quantity);
}

/** 将诊疗执行汇总行转为卡片标签。 */
export function buildExecutionItemChips(
  rows: NurseExecutionSummaryRowDto[],
): NurseExecutionItemChip[] {
  return (rows ?? [])
    .filter((row) => Number(row.executionCount ?? 0) > 0)
    .map((row) => ({
      key: row.categoryEnum ?? row.categoryDisplay ?? '',
      label: row.categoryDisplay ?? '未分类',
      count: Number(row.executionCount ?? 0),
    }))
    .sort((left, right) => right.count - left.count);
}

/** 由诊疗明细按类目聚合为汇总行（与 IF-02 同口径，供卡片一次拉明细后分桶）。 */
export function buildExecutionSummaryFromDetails(
  rows: NurseExecutionDetailRowDto[],
): NurseExecutionSummaryRowDto[] {
  const bucket = new Map<string, NurseExecutionSummaryRowDto>();
  for (const row of rows) {
    const categoryEnum = row.categoryEnum ?? '';
    const existing = bucket.get(categoryEnum);
    if (existing) {
      existing.executionCount = Number(existing.executionCount ?? 0) + 1;
      continue;
    }
    bucket.set(categoryEnum, {
      categoryEnum: row.categoryEnum,
      categoryDisplay: row.categoryDisplay ?? '未分类',
      executionCount: 1,
    });
  }
  return Array.from(bucket.values()).sort(
    (left, right) => Number(right.executionCount ?? 0) - Number(left.executionCount ?? 0),
  );
}

/** 由耗材明细按高/低值聚合为汇总行（与 IF-04 同口径）。 */
export function buildConsumableSummaryFromDetails(
  rows: NurseConsumableDetailRowDto[],
): NurseConsumableSummaryRowDto[] {
  const bucket = new Map<string, NurseConsumableSummaryRowDto>();
  for (const row of rows) {
    let bucketKey = 'HVCM_NULL';
    if (row.hvcmFlag === true) {
      bucketKey = 'HVCM_TRUE';
    } else if (row.hvcmFlag === false) {
      bucketKey = 'HVCM_FALSE';
    }
    const quantity = Number(row.quantity ?? 0);
    const existing = bucket.get(bucketKey);
    if (existing) {
      existing.quantityTotal = decimalAdd(existing.quantityTotal ?? 0, quantity);
      continue;
    }
    bucket.set(bucketKey, {
      hvcmFlag: row.hvcmFlag,
      hvcmDisplay: row.hvcmDisplay,
      quantityTotal: quantity,
    });
  }
  return Array.from(bucket.values());
}

/** 明细行售价代数和（卡片合计；与页脚 totalSaleAmount 同展示口径）。 */
export function sumDetailSaleAmounts(rows: Array<{ saleAmount?: number | string }>) {
  let total: number | string = 0;
  for (const row of rows) {
    if (row?.saleAmount == null) {
      continue;
    }
    total = decimalAdd(total, row.saleAmount);
  }
  return total;
}

/** 明细行数量代数和。 */
export function sumDetailQuantities(rows: NurseConsumableDetailRowDto[]) {
  let total: number | string = 0;
  for (const row of rows) {
    total = decimalAdd(total, row.quantity ?? 0);
  }
  return total;
}

/** 标签数量展示：整数不带小数，否则保留两位。 */
export function formatChipQuantity(value: number) {
  if (Number.isInteger(value)) {
    return String(value);
  }
  return fmtReportNumber(value, 2);
}
