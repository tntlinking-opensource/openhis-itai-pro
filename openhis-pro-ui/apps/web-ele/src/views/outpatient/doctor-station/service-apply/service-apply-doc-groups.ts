import type { AdviceRowItem } from '../advice/services/advice.types';
import type { ServiceApplyKind } from './service-apply.types';

import { formatUnitPriceYuan } from '../advice/services/advice-format';
import { decimalAdd } from '#/utils/format/decimal-util';
import {
  SERVICE_APPLY_BODY_STRUCTURE_TABLE,
  SERVICE_APPLY_SPECIMEN_TABLE,
} from '#/utils/service-apply-display';
import { SERVICE_APPLY_CATEGORY_BY_KIND } from './service-apply.constants';

/** 同一申请单号下的医嘱行（仅 UI 聚组，不扩展后端字段） */
export interface ServiceApplyDocumentGroup {
  applyFormNo: string;
  lines: AdviceRowItem[];
}

function trimText(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 从列表行推断检验/检查/治疗类型（优先 categoryEnum，明细表仅兜底） */
export function inferServiceApplyKindFromAdviceRow(row: AdviceRowItem): ServiceApplyKind | null {
  const code = trimText(row.catalogCategoryCode).toUpperCase();
  if (code === SERVICE_APPLY_CATEGORY_BY_KIND.lab || code === '22') {
    return 'lab';
  }
  if (code === SERVICE_APPLY_CATEGORY_BY_KIND.exam || code === '23') {
    return 'exam';
  }
  if (code === SERVICE_APPLY_CATEGORY_BY_KIND.treatment || code === '21') {
    return 'treatment';
  }
  const table = trimText(row.serviceRequestDetails?.[0]?.targetTable);
  if (table === SERVICE_APPLY_SPECIMEN_TABLE) {
    return 'lab';
  }
  if (table === SERVICE_APPLY_BODY_STRUCTURE_TABLE) {
    return 'exam';
  }
  return null;
}

/** 解析列表行已格式化的金额文案为数字 */
function parseLineAmountYuan(text: string): number {
  const normalized = trimText(text).replace(/[^\d.-]/g, '');
  const n = Number(normalized);
  return Number.isFinite(n) ? n : 0;
}

/** 汇总同一申请单各明细行金额 */
export function sumServiceApplyDocumentTotalAmount(lines: readonly AdviceRowItem[]): string {
  let sum = 0;
  for (const line of lines) {
    sum = decimalAdd(sum, parseLineAmountYuan(line.lineTotalAmount));
  }
  return formatUnitPriceYuan(sum);
}

/** 按申请单号聚组，并按开立时间降序排列 */
export function groupServiceApplyRowsByFormNo(
  rows: readonly AdviceRowItem[],
): ServiceApplyDocumentGroup[] {
  const bucketMap = new Map<string, AdviceRowItem[]>();
  for (const row of rows) {
    const formNo = trimText(row.applyFormNo);
    if (!formNo) {
      continue;
    }
    const bucket = bucketMap.get(formNo);
    if (bucket) {
      bucket.push(row);
    } else {
      bucketMap.set(formNo, [row]);
    }
  }

  const groups: ServiceApplyDocumentGroup[] = [];
  for (const [applyFormNo, lines] of bucketMap) {
    const sortedLines = [...lines].sort(
      (left, right) => (left.sortNumber ?? 0) - (right.sortNumber ?? 0),
    );
    groups.push({ applyFormNo, lines: sortedLines });
  }

  groups.sort((left, right) => {
    const leftTime = trimText(left.lines[0]?.startTime);
    const rightTime = trimText(right.lines[0]?.startTime);
    if (leftTime !== rightTime) {
      return rightTime.localeCompare(leftTime);
    }
    return right.applyFormNo.localeCompare(left.applyFormNo);
  });
  return groups;
}
