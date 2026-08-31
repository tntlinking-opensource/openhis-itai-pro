import type { AdviceRowItem } from '../advice/services/advice.types';
import type { ServiceApplyKind } from './service-apply.types';

import { formatUnitPriceYuan } from '../advice/services/advice-format';
import { decimalAdd } from '#/utils/format/decimal-util';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  SERVICE_APPLY_BODY_STRUCTURE_TABLE,
  SERVICE_APPLY_SPECIMEN_TABLE,
} from '#/utils/service-apply-display';
import { SERVICE_APPLY_CATEGORY_BY_KIND } from './service-apply.constants';

import { isAdviceRowBoundConsumableFromService } from '../advice/services/advice-row';

/** 同一申请单号下的医嘱行（仅 UI 聚组，不扩展后端字段） */
export interface ServiceApplyDocumentGroup {
  applyFormNo: string;
  lines: AdviceRowItem[];
  /** 诊疗 requestId → 绑定耗材行（派生 DeviceRequest，无 AR 号） */
  boundConsumablesByRequestId: Map<string, AdviceRowItem[]>;
}

function trimText(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 医嘱 requestId 归一化（避免 Long 精度导致父子行无法关联） */
function normalizeAdviceRequestId(value: unknown): string {
  const preserved = preserveApiIdForRequest(value);
  if (preserved) {
    return preserved;
  }
  return trimText(value);
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

/** 是否为诊疗绑定带出的耗材行 */
export function isServiceApplyBoundConsumableRow(row: AdviceRowItem): boolean {
  return isAdviceRowBoundConsumableFromService(row);
}

/** 申请单诊疗行 requestId → 绑定耗材子行 */
export function buildServiceApplyBoundConsumablesByRequestId(
  serviceLines: readonly AdviceRowItem[],
  allRows: readonly AdviceRowItem[],
): Map<string, AdviceRowItem[]> {
  const serviceRequestIds = new Set(
    serviceLines.map((row) => normalizeAdviceRequestId(row.requestId)).filter(Boolean),
  );
  const map = new Map<string, AdviceRowItem[]>();
  for (const row of allRows) {
    if (!isServiceApplyBoundConsumableRow(row)) {
      continue;
    }
    const anchorId = normalizeAdviceRequestId(row.basedOnServiceRequestId);
    if (!anchorId || !serviceRequestIds.has(anchorId)) {
      continue;
    }
    const bucket = map.get(anchorId);
    if (bucket) {
      bucket.push(row);
    } else {
      map.set(anchorId, [row]);
    }
  }
  for (const [anchorId, boundRows] of map) {
    map.set(
      anchorId,
      [...boundRows].sort((left, right) => (left.sortNumber ?? 0) - (right.sortNumber ?? 0)),
    );
  }
  return map;
}

/** 汇总同一申请单各明细行及绑定耗材金额 */
export function sumServiceApplyDocumentTotalAmount(
  lines: readonly AdviceRowItem[],
  boundConsumablesByRequestId?: ReadonlyMap<string, AdviceRowItem[]>,
): string {
  let sum = 0;
  for (const line of lines) {
    sum = decimalAdd(sum, parseLineAmountYuan(line.lineTotalAmount));
    const bound = boundConsumablesByRequestId?.get(normalizeAdviceRequestId(line.requestId)) ?? [];
    for (const deviceRow of bound) {
      sum = decimalAdd(sum, parseLineAmountYuan(deviceRow.lineTotalAmount));
    }
  }
  return formatUnitPriceYuan(sum);
}

/** 按申请单号聚组，并按开立时间降序排列 */
export function groupServiceApplyRowsByFormNo(
  rows: readonly AdviceRowItem[],
  allRowsForBind?: readonly AdviceRowItem[],
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
    const boundConsumablesByRequestId = buildServiceApplyBoundConsumablesByRequestId(
      sortedLines,
      allRowsForBind ?? rows,
    );
    groups.push({ applyFormNo, lines: sortedLines, boundConsumablesByRequestId });
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
