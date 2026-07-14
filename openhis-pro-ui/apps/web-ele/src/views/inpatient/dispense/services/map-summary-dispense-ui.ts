import type {
  InpatientPharmacySummaryDispenseItemDto,
  InpatientPharmacySummarySlipLineDto,
} from '#/api/inpatient-pharmacy-dispense/inpatient-pharmacy-summary-dispense.types';
import type { DispensePrescriptionItemRow } from '#/views/outpatient/dispense/components/types';

import { formatDateTime } from '@vben/utils';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

/** 汇总合并行展示模型：批号由底层发放行聚合（BR-IPS-012 合并 DTO 不含 lotNumber） */
export interface SummaryMergedLineDisplayRow extends InpatientPharmacySummarySlipLineDto {
  lotNumberText: string;
}

/** 按患者折叠分组（发放明细区） */
export interface SummaryDispensePatientGroup {
  groupKey: string;
  bedDisplay: string;
  patientName: string;
  items: InpatientPharmacySummaryDispenseItemDto[];
}

/** 数量 + 单位展示 */
export function formatSummaryQuantityWithUnit(
  quantity?: number | string,
  unitCodeText?: string,
): string {
  const q = quantity == null || quantity === '' ? '' : String(quantity);
  const unit = String(unitCodeText ?? '').trim();
  if (q && unit) return `${q}${unit}`;
  return q || unit || '—';
}

/** 汇总合并行数量展示（优先后端 displayQuantityText） */
export function formatSummaryMergedLineQuantity(row: {
  displayQuantityText?: string;
  itemQuantity?: number | string;
  unitCodeText?: string;
}): string {
  const displayText = String(row.displayQuantityText ?? '').trim();
  if (displayText) return displayText;
  return formatSummaryQuantityWithUnit(row.itemQuantity, row.unitCodeText);
}

/** 按药品 id 聚合底层发放行批号（去重、顿号连接） */
function aggregateLotNumbersByMedicationId(
  dispenseItems: InpatientPharmacySummaryDispenseItemDto[],
): Map<string, string> {
  const lotsByMedicationId = new Map<string, Set<string>>();
  for (const item of dispenseItems) {
    const medicationId = preserveApiIdForRequest(item.medicationId) ?? '';
    const lotNumber = String(item.lotNumber ?? '').trim();
    if (!medicationId || !lotNumber) {
      continue;
    }
    const bucket = lotsByMedicationId.get(medicationId) ?? new Set<string>();
    bucket.add(lotNumber);
    lotsByMedicationId.set(medicationId, bucket);
  }
  const result = new Map<string, string>();
  for (const [medicationId, lots] of lotsByMedicationId) {
    if (lots.size === 0) {
      continue;
    }
    result.set(medicationId, [...lots].join('、'));
  }
  return result;
}

/** 合并 Supply 行 + 发放行批号聚合 → 界面展示行 */
export function buildSummaryMergedLineDisplayRows(
  mergedLines: InpatientPharmacySummarySlipLineDto[] | undefined,
  dispenseItems: InpatientPharmacySummaryDispenseItemDto[] | undefined,
): SummaryMergedLineDisplayRow[] {
  const lotTextByMedicationId = aggregateLotNumbersByMedicationId(dispenseItems ?? []);
  const rows: SummaryMergedLineDisplayRow[] = [];
  for (const line of mergedLines ?? []) {
    const itemId = preserveApiIdForRequest(line.itemId) ?? '';
    const lotNumberText = (itemId && lotTextByMedicationId.get(itemId)) || '—';
    rows.push({
      ...line,
      lotNumberText,
    });
  }
  return rows;
}

/** 患者展开区单行文案：药名 + 数量 + 批号 */
export function formatSummaryPatientDispenseLineText(
  item: InpatientPharmacySummaryDispenseItemDto,
): string {
  const name = String(item.medicationName ?? '').trim() || '—';
  const quantity = formatSummaryQuantityWithUnit(item.quantity, item.unitCodeText);
  const lotNumber = String(item.lotNumber ?? '').trim();
  if (lotNumber) {
    return `${name} ${quantity}（批号 ${lotNumber}）`;
  }
  return `${name} ${quantity}`;
}

/** 合并 Supply 行 → 追溯码弹窗扁平行（汇总路径不关联患者发放行） */
export function mapSummaryMergedLinesToTraceLines(
  lines: SummaryMergedLineDisplayRow[],
): DispensePrescriptionItemRow[] {
  return lines.map((line, index) => {
    const id = preserveApiIdForRequest(line.supplyRequestId) ?? `summary-merged-${index}`;
    const name = String(line.medicationName ?? '').trim();
    const spec = String(line.specificationText ?? '').trim();
    const itemName = name && spec ? `${name}【${spec}】` : name || spec || '—';
    return {
      id,
      itemName,
      quantity: formatSummaryMergedLineQuantity(line),
      spec,
      traceCode: String(line.traceNo ?? '').trim(),
      batchNo: String(line.lotNumberText ?? '').trim() || '—',
      amount:
        line.totalPrice != null && line.totalPrice !== ''
          ? formatMoneyDisplay(line.totalPrice)
          : '—',
      pharmacy: String(line.purposeLocationName ?? '').trim() || '—',
      factory: String(line.manufacturerName ?? '').trim() || '—',
      diagnosis: '—',
      freq: '—',
      rxId: id,
      rxNo: id,
      rxType: 'western',
      deptName: '—',
      doctorName: '—',
      issuedAt: '—',
      status: 'pending',
      rxGroupIndex: 0,
      rxRowSpan: 1,
      isFirstInRx: true,
      isLastInRx: true,
    };
  });
}

/** @deprecated 汇总追溯码不按患者发放行采集，请用 {@link mapSummaryMergedLinesToTraceLines} */
export function mapSummaryDispenseItemsToTraceLines(
  items: InpatientPharmacySummaryDispenseItemDto[],
): DispensePrescriptionItemRow[] {
  return items.map((item, index) => {
    const id = preserveApiIdForRequest(item.dispenseId) ?? `summary-line-${index}`;
    const name = String(item.medicationName ?? '').trim();
    const spec = String(item.specificationText ?? '').trim();
    const itemName = name && spec ? `${name}【${spec}】` : name || spec || '—';
    return {
      id,
      itemName,
      quantity: formatSummaryQuantityWithUnit(item.quantity, item.unitCodeText),
      spec,
      traceCode: String(item.traceNo ?? '').trim(),
      batchNo: String(item.lotNumber ?? '').trim() || '—',
      amount: '—',
      pharmacy: String(item.locationName ?? '').trim() || '—',
      factory: '—',
      diagnosis: '—',
      freq: '—',
      rxId: id,
      rxNo: id,
      rxType: 'western',
      deptName: '—',
      doctorName: '—',
      issuedAt: '—',
      status: 'pending',
      rxGroupIndex: 0,
      rxRowSpan: 1,
      isFirstInRx: true,
      isLastInRx: true,
    };
  });
}

/** 底层发放行按就诊/患者分组 */
export function groupSummaryDispenseItemsByPatient(
  items: InpatientPharmacySummaryDispenseItemDto[] | undefined,
): SummaryDispensePatientGroup[] {
  const map = new Map<string, SummaryDispensePatientGroup>();
  for (const item of items ?? []) {
    const encounterId = preserveApiIdForRequest(item.encounterId) ?? '';
    const patientId = preserveApiIdForRequest(item.patientId) ?? '';
    const groupKey = encounterId || patientId || String(item.dispenseId ?? '');
    const bucket = map.get(groupKey) ?? {
      groupKey,
      bedDisplay: String(item.bedDisplay ?? '').trim() || '—',
      patientName: String(item.patientName ?? '').trim() || '—',
      items: [],
    };
    bucket.items.push(item);
    map.set(groupKey, bucket);
  }
  return [...map.values()];
}

/** 汇总单头申请时间展示 */
export function formatSummaryApplyTime(value?: string): string {
  const raw = String(value ?? '').trim();
  if (!raw) return '—';
  return formatDateTime(raw) || raw;
}

/** IF-IPS-01 列表查询：日期范围 → startDate/endDate */
export function buildSummarySlipListDateRange(range: [string, string] | null | undefined): {
  endDate?: string;
  startDate?: string;
} {
  if (!range?.[0] || !range?.[1]) {
    return {};
  }
  return {
    startDate: `${range[0]} 00:00:00`,
    endDate: `${range[1]} 23:59:59`,
  };
}

/** 规范化发放行 dispenseId（后端 Long → string） */
export function normalizeSummaryDispenseItems(
  items: InpatientPharmacySummaryDispenseItemDto[] | undefined,
): InpatientPharmacySummaryDispenseItemDto[] {
  const list: InpatientPharmacySummaryDispenseItemDto[] = [];
  for (const item of items ?? []) {
    const dispenseId = preserveApiIdForRequest(item.dispenseId);
    if (!dispenseId) continue;
    list.push({
      ...item,
      dispenseId,
      encounterId: preserveApiIdForRequest(item.encounterId) ?? item.encounterId,
      patientId: preserveApiIdForRequest(item.patientId) ?? item.patientId,
      medicationId: preserveApiIdForRequest(item.medicationId) ?? item.medicationId,
      locationId: preserveApiIdForRequest(item.locationId) ?? item.locationId,
    });
  }
  return list;
}

/** IF-IPS-03：追溯码提交行（锚定供应行 / SupplyDelivery，非 dispenseId） */
export function buildSummaryDispenseTraceLines(
  traceByLineId: Record<string, string>,
): Array<{ supplyRequestId: string; traceNo: string }> {
  const lines: Array<{ supplyRequestId: string; traceNo: string }> = [];
  for (const [supplyRequestIdRaw, traceNoRaw] of Object.entries(traceByLineId)) {
    const supplyRequestId = preserveApiIdForRequest(supplyRequestIdRaw);
    const traceNo = String(traceNoRaw ?? '').trim();
    if (!supplyRequestId || !traceNo) continue;
    lines.push({ supplyRequestId, traceNo });
  }
  return lines;
}
