import type {
  NurseStationOrderExecuteBindItemDto,
  NurseStationOrderExecuteOrderType,
  NurseStationOrderExecuteRowDto,
  NurseStationOrderExecuteSlotDto,
  NurseStationOrderExecuteTab,
  NurseStationOrderExecuteWriteSlotDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.types';

import { formatDateTime } from '@vben/utils';
import dayjs from 'dayjs';

import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

/** 表格行：医嘱展示 + 当前 Tab 对应槽位分组 */
export interface NurseStationOrderExecuteTableRow {
  id: string;
  orderId: string;
  orderType: NurseStationOrderExecuteOrderType;
  encounterId: string;
  /** 长临 + 药/诊，如「长期/药品」 */
  typeLabel: string;
  content: string;
  dosage: string;
  extension?: string;
  startStopText: string;
  planSchedules: NurseStationOrderExecutePlanSchedule[];
  bindItems: NurseStationOrderExecuteBindItemDto[];
}

/** 按日期分组的槽位（用于「预计执行时点」列） */
export interface NurseStationOrderExecutePlanSchedule {
  date: string;
  slots: NurseStationOrderExecutePlanSlot[];
}

/** 可勾选的计划槽位 */
export interface NurseStationOrderExecutePlanSlot {
  slotKey: string;
  planTime: string;
  timeLabel: string;
  checked: boolean;
}

const SLOT_KEY_SEP = '\u0001';

function orderTypeToShortLabel(orderType: string): string {
  const t = orderType.trim().toUpperCase();
  if (t === 'MED_REQUEST') return '药品';
  if (t === 'SERVICE_REQUEST') return '诊疗';
  return orderType || '医嘱';
}

/** 长临 code → 短文案 */
function therapyEnumToLabel(code: string): string {
  const c = code.trim().toUpperCase();
  if (c === 'LONG_TERM') return '长期';
  if (c === 'TEMPORARY') return '临时';
  if (c === 'DISCHARGE_MEDICATION') return '出院带药';
  return '';
}

/** 列表「长临」列：优先后端 therapyEnumText，否则按 therapyEnum 推导 */
export function resolveExecuteTherapyLabel(dto: NurseStationOrderExecuteRowDto): string {
  const text = String(dto.therapyEnumText ?? '').trim();
  if (text) {
    if (text.includes('长期')) return '长期';
    if (text.includes('临时')) return '临时';
    if (text.includes('出院')) return '出院带药';
    return text;
  }
  return therapyEnumToLabel(String(dto.therapyEnum ?? '')) || '—';
}

/** 组装列表「类型」列：长临 + 药/诊 */
function buildExecuteTypeLabel(dto: NurseStationOrderExecuteRowDto): string {
  const therapy = resolveExecuteTherapyLabel(dto);
  const kind = orderTypeToShortLabel(String(dto.orderType ?? ''));
  if (therapy && therapy !== '—' && kind) return `${therapy}/${kind}`;
  return therapy !== '—' ? therapy : kind || '—';
}

/** 医嘱名称摘要：名称 + 规格 */
function buildExecuteContentTitle(dto: NurseStationOrderExecuteRowDto): string {
  const name = String(dto.adviceDisplayName ?? '').trim();
  const spec = String(dto.specificationText ?? '').trim();
  if (name && spec) return `${name}【${spec}】`;
  return name || spec || '—';
}

/** 用法、频次拼成扩展行 */
function buildExecuteExtensionLine(dto: NurseStationOrderExecuteRowDto): string {
  const usage = String(dto.usageDisplay ?? '').trim();
  const freq = String(dto.frequencyDisplay ?? '').trim();
  if (usage && freq) return `${usage}，${freq}`;
  return usage || freq || '';
}

/** 开始/终止时间展示 */
function buildStartStopText(dto: NurseStationOrderExecuteRowDto): string {
  const startRaw = String(dto.authoredTime ?? '').trim();
  const stopRaw = String(dto.stopTime ?? '').trim();
  const start = startRaw ? formatDateTime(startRaw) : '';
  const stop = stopRaw ? formatDateTime(stopRaw) : '';
  if (start && stop) return `${start}\n${stop}`;
  return start || stop || '—';
}

/** 按 executeTab 取当前应展示的槽位列表 */
function resolveSlotsForTab(
  dto: NurseStationOrderExecuteRowDto,
  executeTab: NurseStationOrderExecuteTab,
): NurseStationOrderExecuteSlotDto[] {
  if (executeTab === 'pending') return dto.pendingSlots ?? [];
  if (executeTab === 'executed') return dto.executedSlots ?? [];
  if (executeTab === 'skipped') return dto.skippedSlots ?? [];
  if (executeTab === 'cancelled') return dto.cancelledSlots ?? [];
  return [
    ...(dto.pendingSlots ?? []),
    ...(dto.executedSlots ?? []),
    ...(dto.skippedSlots ?? []),
    ...(dto.cancelledSlots ?? []),
  ];
}

/** 生成槽位稳定键（orderId + orderType + planTime） */
export function buildExecuteSlotKey(orderId: string, orderType: string, planTime: string): string {
  return `${orderId}${SLOT_KEY_SEP}${orderType}${SLOT_KEY_SEP}${planTime}`;
}

/** 槽位列表按日期分组为表格展示结构 */
export function groupSlotsByDate(
  orderId: string,
  orderType: string,
  slots: NurseStationOrderExecuteSlotDto[],
): NurseStationOrderExecutePlanSchedule[] {
  const byDate = new Map<string, NurseStationOrderExecutePlanSlot[]>();
  for (const slot of slots) {
    const planTime = String(slot.planTime ?? '').trim();
    if (!planTime) continue;
    const parsed = dayjs(planTime);
    if (!parsed.isValid()) continue;
    const dateKey = parsed.format('MM-DD');
    const timeLabel = parsed.format('HH:mm');
    const slotKey = buildExecuteSlotKey(orderId, orderType, planTime);
    const bucket = byDate.get(dateKey) ?? [];
    bucket.push({
      slotKey,
      planTime,
      timeLabel,
      checked: false,
    });
    byDate.set(dateKey, bucket);
  }
  const schedules: NurseStationOrderExecutePlanSchedule[] = [];
  for (const [date, planSlots] of byDate) {
    planSlots.sort((a, b) => a.timeLabel.localeCompare(b.timeLabel));
    schedules.push({ date, slots: planSlots });
  }
  schedules.sort((a, b) => a.date.localeCompare(b.date));
  return schedules;
}

/** 列表行 DTO → 表格行 */
export function mapExecuteRowDtoToTableRow(
  dto: NurseStationOrderExecuteRowDto,
  executeTab: NurseStationOrderExecuteTab,
): NurseStationOrderExecuteTableRow {
  const orderId = preserveApiIdForRequest(dto.orderId) ?? '';
  const orderTypeRaw = String(dto.orderType ?? '')
    .trim()
    .toUpperCase();
  const orderType =
    orderTypeRaw === 'MED_REQUEST' || orderTypeRaw === 'SERVICE_REQUEST'
      ? orderTypeRaw
      : 'MED_REQUEST';
  const listKey = String(dto.listRowKey ?? '').trim();
  const id =
    listKey ||
    (orderId && orderType
      ? `${orderId}${SLOT_KEY_SEP}${orderType}`
      : `execute-${orderId || Date.now()}`);
  const tabSlots = resolveSlotsForTab(dto, executeTab);

  return {
    id,
    orderId,
    orderType,
    encounterId: preserveApiIdForRequest(dto.encounterId) ?? '',
    typeLabel: buildExecuteTypeLabel(dto),
    content: buildExecuteContentTitle(dto),
    dosage: String(dto.dosageDisplay ?? '').trim() || '—',
    extension: buildExecuteExtensionLine(dto) || undefined,
    startStopText: buildStartStopText(dto),
    planSchedules: groupSlotsByDate(orderId, orderType, tabSlots),
    bindItems: dto.bindItems ?? [],
  };
}

/** 接口分组 → encounterId → 表格行 */
export function indexExecuteGroupsByEncounterId(
  groups: { encounterId?: string; orders?: NurseStationOrderExecuteRowDto[] }[] | undefined,
  executeTab: NurseStationOrderExecuteTab,
): Map<string, NurseStationOrderExecuteTableRow[]> {
  const map = new Map<string, NurseStationOrderExecuteTableRow[]>();
  for (const group of groups ?? []) {
    const encounterId = preserveApiIdForRequest(group.encounterId);
    if (!encounterId) continue;
    const rows = (group.orders ?? []).map((dto) => mapExecuteRowDtoToTableRow(dto, executeTab));
    map.set(encounterId, rows);
  }
  return map;
}

/** 收集已勾选槽位 → 写请求 items（执行需 actualTime） */
export function mapSelectedSlotsToWriteItems(
  rows: NurseStationOrderExecuteTableRow[],
  selectedSlotKeys: Set<string>,
  actualTime: string,
  includeBindItems: boolean,
  accountIdByEncounterId?: Map<string, string>,
  requireAccountId = false,
): NurseStationOrderExecuteWriteSlotDto[] {
  const items: NurseStationOrderExecuteWriteSlotDto[] = [];
  for (const row of rows) {
    const orderId = preserveApiIdForRequest(row.orderId);
    if (!orderId) continue;
    const encounterId = String(row.encounterId ?? '').trim();
    const accountId = encounterId ? accountIdByEncounterId?.get(encounterId) : undefined;
    if (requireAccountId && !accountId) continue;
    for (const schedule of row.planSchedules) {
      for (const slot of schedule.slots) {
        if (!selectedSlotKeys.has(slot.slotKey)) continue;
        const item: NurseStationOrderExecuteWriteSlotDto = {
          orderId,
          orderType: row.orderType,
          planTime: slot.planTime,
        };
        if (accountId) {
          item.accountId = accountId;
        }
        if (actualTime) {
          item.actualTime = actualTime;
        }
        if (includeBindItems && row.bindItems.length > 0) {
          item.bindItems = row.bindItems.map((bind) => ({
            activityId: preserveApiIdForRequest(bind.activityId),
            deviceId: preserveApiIdForRequest(bind.deviceId),
            bindType: bind.bindType,
            bindDisplayName: bind.bindDisplayName,
          }));
        }
        items.push(item);
      }
    }
  }
  return items;
}

/** 不执行写请求（不含 actualTime / bindItems） */
export function mapSelectedSlotsToSkipItems(
  rows: NurseStationOrderExecuteTableRow[],
  selectedSlotKeys: Set<string>,
): NurseStationOrderExecuteWriteSlotDto[] {
  return mapSelectedSlotsToWriteItems(rows, selectedSlotKeys, '', false).map(
    ({ orderId, orderType, planTime }) => ({
      orderId,
      orderType,
      planTime,
    }),
  );
}
