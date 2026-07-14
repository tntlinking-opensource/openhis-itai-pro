import type {
  NurseConsumableDetailRowDto,
  NurseExecutionSummaryRowDto,
} from '#/api/report/nurse-workload';

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

/** 标签数量展示：整数不带小数，否则保留两位。 */
export function formatChipQuantity(value: number) {
  if (Number.isInteger(value)) {
    return String(value);
  }
  return fmtReportNumber(value, 2);
}
