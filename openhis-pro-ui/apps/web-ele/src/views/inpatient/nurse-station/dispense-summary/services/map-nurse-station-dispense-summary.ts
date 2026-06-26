import type {
  NurseStationDispenseSummaryOrderRowDto,
  NurseStationDispenseSummaryPatientGroupDto,
  NurseStationDispenseSummarySlipHeaderDto,
  NurseStationDispenseSummaryTimePointDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-dispense-summary.types';
import type { NurseStationModulePatient } from '../../components/nurse-station-module-patient.types';

import { formatDateTime } from '@vben/utils';
import dayjs from 'dayjs';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 勾选槽位（仅 UI 状态键，字段来自后端 timePoints） */
export interface DispenseSummarySelectableSlot {
  slotKey: string;
  dispenseId: string;
  encounterId: string;
  point: NurseStationDispenseSummaryTimePointDto;
}

/** 按日期分组的可勾选时间点（展示用） */
export interface DispenseSummaryTimePointDayGroup {
  dateLabel: string;
  slots: DispenseSummarySelectableSlot[];
}

const SLOT_KEY_SEP = '\u0001';
const PENDING_SUPPLY_STATUS = 'PENDING_APPROVAL';

/** 待发送汇总单（SupplyRequest.status_enum = PENDING_APPROVAL） */
export function isPendingSummarySlip(row: NurseStationDispenseSummarySlipHeaderDto): boolean {
  return String(row.supplyStatusEnum ?? '').trim() === PENDING_SUPPLY_STATUS;
}

/** 时间点 slotKey：dispenseId + encounterId */
export function buildDispenseSummarySlotKey(dispenseId: string, encounterId: string): string {
  return `${dispenseId}${SLOT_KEY_SEP}${encounterId}`;
}

/** 解析 slotKey 为写请求项 */
export function parseDispenseSummarySlotKey(
  slotKey: string,
): { dispenseId: string; encounterId: string } | null {
  const parts = slotKey.split(SLOT_KEY_SEP);
  if (parts.length !== 2) return null;
  const dispenseId = preserveApiIdForRequest(parts[0]);
  const encounterId = preserveApiIdForRequest(parts[1]);
  if (!dispenseId || !encounterId) return null;
  return { dispenseId, encounterId };
}

/** 计划发药时刻按日期分组 */
export function groupTimePointsForDisplay(
  encounterId: string,
  timePoints: NurseStationDispenseSummaryTimePointDto[] | undefined,
): DispenseSummaryTimePointDayGroup[] {
  const byDate = new Map<string, DispenseSummarySelectableSlot[]>();
  for (const point of timePoints ?? []) {
    const dispenseId = preserveApiIdForRequest(point.dispenseId);
    if (!dispenseId) continue;
    const planTimeRaw = String(point.plannedDispenseTime ?? '').trim();
    const planTime = planTimeRaw ? formatDateTime(planTimeRaw) : '';
    const dateLabel = planTime ? dayjs(planTime).format('MM-DD') : '—';
    const slot: DispenseSummarySelectableSlot = {
      slotKey: buildDispenseSummarySlotKey(dispenseId, encounterId),
      dispenseId,
      encounterId,
      point,
    };
    const bucket = byDate.get(dateLabel) ?? [];
    bucket.push(slot);
    byDate.set(dateLabel, bucket);
  }
  return [...byDate.entries()].map(([dateLabel, slots]) => ({
    dateLabel,
    slots: slots.sort((a, b) => {
      const ta = String(a.point.plannedDispenseTime ?? '');
      const tb = String(b.point.plannedDispenseTime ?? '');
      return ta.localeCompare(tb);
    }),
  }));
}

/** 接口就诊分组 → encounterId → orderRows */
export function indexPatientGroupsByEncounterId(
  groups: NurseStationDispenseSummaryPatientGroupDto[] | undefined,
): Map<string, NurseStationDispenseSummaryOrderRowDto[]> {
  const map = new Map<string, NurseStationDispenseSummaryOrderRowDto[]>();
  for (const group of groups ?? []) {
    const encounterId = preserveApiIdForRequest(group.encounterId);
    if (!encounterId) continue;
    map.set(encounterId, group.orderRows ?? []);
  }
  return map;
}

/** 收集全部可勾选槽位 */
export function collectSelectableSlotsFromOrderRows(
  ordersByEncounterId: Map<string, NurseStationDispenseSummaryOrderRowDto[]>,
): DispenseSummarySelectableSlot[] {
  const slots: DispenseSummarySelectableSlot[] = [];
  for (const [encounterId, orderRows] of ordersByEncounterId.entries()) {
    for (const orderRow of orderRows) {
      for (const dayGroup of groupTimePointsForDisplay(encounterId, orderRow.timePoints)) {
        slots.push(...dayGroup.slots);
      }
    }
  }
  return slots;
}

/** 已选 slotKey → 写请求 dispenseItems */
export function mapSelectedSlotsToCreateItems(
  selectedSlotKeys: Set<string>,
): { dispenseId: string; encounterId: string }[] {
  const items: { dispenseId: string; encounterId: string }[] = [];
  const seen = new Set<string>();
  for (const slotKey of selectedSlotKeys) {
    const parsed = parseDispenseSummarySlotKey(slotKey);
    if (!parsed) continue;
    const dedupeKey = `${parsed.dispenseId}${SLOT_KEY_SEP}${parsed.encounterId}`;
    if (seen.has(dedupeKey)) continue;
    seen.add(dedupeKey);
    items.push(parsed);
  }
  return items;
}

/** 左侧患者 + 接口 orderRows 对齐 */
export function buildDispenseSummaryVisibleCards(
  patients: NurseStationModulePatient[],
  ordersByEncounterId: Map<string, NurseStationDispenseSummaryOrderRowDto[]>,
): Array<{
  patient: NurseStationModulePatient;
  encounterId: string;
  orderRows: NurseStationDispenseSummaryOrderRowDto[];
}> {
  return patients.map((patient) => {
    const encounterId = String(patient.id).trim();
    return {
      patient,
      encounterId,
      orderRows: ordersByEncounterId.get(encounterId) ?? [],
    };
  });
}

/** 医嘱行主键（表格 row-key） */
export function resolveOrderRowKey(
  row: NurseStationDispenseSummaryOrderRowDto,
  encounterId: string,
): string {
  const medReqId = preserveApiIdForRequest(row.medReqId);
  if (medReqId) return medReqId;
  const medicationId = preserveApiIdForRequest(row.medicationId);
  return medicationId ? `${encounterId}-${medicationId}` : encounterId;
}

/** 计划发药时刻 HH:mm 展示 */
export function formatPlannedDispenseTimeLabel(plannedDispenseTime?: string): string {
  const raw = String(plannedDispenseTime ?? '').trim();
  if (!raw) return '—';
  const formatted = formatDateTime(raw);
  return formatted ? dayjs(formatted).format('HH:mm') : '—';
}

/** 数量 + 单位展示（后端 quantity / unitCodeText） */
export function formatQuantityWithUnit(quantity?: number | string, unitCodeText?: string): string {
  const q = quantity == null || quantity === '' ? '' : String(quantity);
  const unit = String(unitCodeText ?? '').trim();
  if (q && unit) return `${q}${unit}`;
  return q || unit || '—';
}

/** 汇总单明细行数量展示（优先后端 displayQuantityText，如 1盒1片） */
export function formatSummarySlipLineQuantity(row: {
  displayQuantityText?: string;
  itemQuantity?: number | string;
  unitCodeText?: string;
}): string {
  const displayText = String(row.displayQuantityText ?? '').trim();
  if (displayText) return displayText;
  return formatQuantityWithUnit(row.itemQuantity, row.unitCodeText);
}

/** 汇总单头发药/审批态展示（优先后端 deliveryStatusEnumText） */
export function resolveSlipStatusText(row: NurseStationDispenseSummarySlipHeaderDto): string {
  const deliveryText = String(row.deliveryStatusEnumText ?? '').trim();
  if (deliveryText) return deliveryText;
  return String(row.supplyStatusEnumText ?? '').trim() || '—';
}

/** 汇总单日期时间展示 */
export function formatSummarySlipDateTime(value?: string): string {
  const raw = String(value ?? '').trim();
  if (!raw) return '—';
  return formatDateTime(raw) || raw;
}

/** 汇总单状态 Tag 类型（按文案粗匹配） */
export function resolveSlipStatusTagType(
  statusText: string,
): 'danger' | 'info' | 'success' | 'warning' {
  const text = statusText.trim();
  if (/已发|完成|已核对|已配送/.test(text)) return 'success';
  if (/待|待发|审批|待核对/.test(text)) return 'warning';
  if (/撤销|驳回|作废|取消/.test(text)) return 'danger';
  return 'info';
}
