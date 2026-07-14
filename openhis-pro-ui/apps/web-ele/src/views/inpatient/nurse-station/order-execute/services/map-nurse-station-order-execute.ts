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

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { formatMoneyYuanWithSymbol } from '#/utils/format/money-scale';

/** 表格行：医嘱展示 + 当前 Tab 对应槽位分组 */
export interface NurseStationOrderExecuteTableRow {
  id: string;
  orderId: string;
  orderType: NurseStationOrderExecuteOrderType;
  encounterId: string;
  /** 诊疗类目 code（采血识别） */
  categoryEnum?: string;
  /** 输液组号 */
  groupNo?: number;
  /** 长临 + 药/诊，如「长期/药品」 */
  typeLabel: string;
  /** 药/诊短文案 */
  orderKindLabel: string;
  /** 长临 code（LONG_TERM / TEMPORARY） */
  therapyTypeCode?: string;
  /** 长临展示文案 */
  therapyLabel?: string;
  content: string;
  dosage: string;
  extension?: string;
  startStopText: string;
  /** 请求状态 code */
  statusEnum?: string;
  /** 请求状态展示 */
  statusDisplay: string;
  /** 是否已停嘱 */
  halted: boolean;
  planSchedules: NurseStationOrderExecutePlanSchedule[];
  bindItems: NurseStationOrderExecuteBindItemDto[];
  tcmFlag?: boolean;
  prescriptionNo?: string;
  lineRequestIds?: string[];
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
  /** 已执行 Tab 取消执行写路径主键（净剩 COMPLETED procedureId） */
  procedureId?: string;
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

/** 列表长临 code */
function resolveExecuteTherapyTypeCode(dto: NurseStationOrderExecuteRowDto): string {
  return String(dto.therapyEnum ?? '')
    .trim()
    .toUpperCase();
}

/** 列表「长临」列：按 therapyEnum 推导 */
export function resolveExecuteTherapyLabel(dto: NurseStationOrderExecuteRowDto): string {
  return therapyEnumToLabel(String(dto.therapyEnum ?? '')) || '—';
}

/** 组装列表「类型」列：长临 + 药/诊 */
function buildExecuteTypeLabel(dto: NurseStationOrderExecuteRowDto): string {
  const therapy = resolveExecuteTherapyLabel(dto);
  const kind = orderTypeToShortLabel(String(dto.orderType ?? ''));
  if (therapy && therapy !== '—' && kind) return `${therapy}/${kind}`;
  return therapy !== '—' ? therapy : kind || '—';
}

/** 医嘱名称摘要：名称 + 规格；中药方折叠行仅用方级展示名 */
function buildExecuteContentTitle(dto: NurseStationOrderExecuteRowDto): string {
  if (dto.tcmFlag === true) {
    const collapsedName = String(dto.adviceDisplayName ?? '').trim();
    if (collapsedName) return collapsedName;
  }
  const name = String(dto.adviceDisplayName ?? '').trim();
  const spec = String(dto.specificationText ?? '').trim();
  if (name && spec) return `${name}【${spec}】`;
  return name || spec || '—';
}

/** 剂量/数量：中药方展示各味单次量 + 付数 */
function buildExecuteDosageDisplay(dto: NurseStationOrderExecuteRowDto): string {
  if (dto.tcmFlag === true) {
    const singleDose = String(dto.dosageDisplay ?? '').trim();
    const fuCount = dto.chineseHerbsDoseQuantity;
    const fuText = fuCount != null && String(fuCount).trim() ? `${String(fuCount).trim()}付` : '';
    if (singleDose && fuText) {
      return `${singleDose} ${fuText}`;
    }
    return singleDose || fuText || '—';
  }
  return String(dto.dosageDisplay ?? '').trim() || '—';
}

function mapLineRequestIds(
  ids: NurseStationOrderExecuteRowDto['lineRequestIds'],
): string[] | undefined {
  if (!ids?.length) return undefined;
  const mapped = ids
    .map((id) => preserveApiIdForRequest(id) ?? String(id ?? '').trim())
    .filter((id) => id.length > 0);
  return mapped.length > 0 ? mapped : undefined;
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
      procedureId: preserveApiIdForRequest(slot.procedureId) ?? undefined,
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

/** 绑定项展示名 */
export function resolveExecuteBindItemDisplayName(
  bind: NurseStationOrderExecuteBindItemDto,
): string {
  const name = String(bind.displayName ?? bind.bindDisplayName ?? '').trim();
  return name || '—';
}

/** 绑定项展示：名称【规格】 */
export function resolveExecuteBindItemDisplayText(
  bind: NurseStationOrderExecuteBindItemDto,
): string {
  const name = resolveExecuteBindItemDisplayName(bind);
  const spec = String(bind.specificationText ?? '').trim();
  if (name === '—') return spec || '—';
  if (spec) return `${name}【${spec}】`;
  return name;
}

/** 绑定项单位文案（仅单位，不含数量） */
export function resolveExecuteBindItemUnitLabel(bind: NurseStationOrderExecuteBindItemDto): string {
  return String(bind.unitCode_dictText ?? bind.unitCode ?? '').trim();
}

/** 绑定项数量输入值（模板未配置时默认 1） */
export function resolveExecuteBindQuantityValue(bind: NurseStationOrderExecuteBindItemDto): number {
  const numerator = bind.quantityNumerator;
  if (numerator == null || Number.isNaN(Number(numerator))) {
    return 1;
  }
  return Number(numerator);
}

/** 绑定项数量 + 单位展示 */
export function formatExecuteBindItemQuantity(bind: NurseStationOrderExecuteBindItemDto): string {
  const unit = String(bind.unitCode_dictText ?? bind.unitCode ?? '').trim();
  const numerator = bind.quantityNumerator;
  if (numerator == null) {
    return unit || '—';
  }
  const denominator = bind.quantityDenominator;
  let qtyText = String(numerator);
  if (denominator != null && denominator !== 1) {
    qtyText = `${numerator}/${denominator}`;
  }
  return unit ? `${qtyText}${unit}` : qtyText;
}

/** 绑定项单价展示 */
export function formatExecuteBindItemUnitPrice(unitPrice?: number): string {
  if (unitPrice == null || Number.isNaN(unitPrice)) {
    return '—';
  }
  return formatMoneyYuanWithSymbol(unitPrice);
}

/** 绑定项稳定 key（展开列表渲染） */
export function resolveExecuteBindItemKey(
  bind: NurseStationOrderExecuteBindItemDto,
  index: number,
): string {
  const bindingId = preserveApiIdForRequest(bind.bindingId) ?? '';
  const targetId =
    preserveApiIdForRequest(bind.deviceId) ?? preserveApiIdForRequest(bind.activityId) ?? '';
  return `${bindingId}-${targetId}-${index}`;
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
  const statusEnum = String(dto.statusEnum ?? '')
    .trim()
    .toUpperCase();
  const statusDisplay = String(dto.statusEnumText ?? '').trim() || '—';

  return {
    id,
    orderId,
    orderType,
    encounterId: preserveApiIdForRequest(dto.encounterId) ?? '',
    categoryEnum: String(dto.categoryEnum ?? '').trim() || undefined,
    groupNo: dto.groupNo ?? undefined,
    typeLabel: buildExecuteTypeLabel(dto),
    orderKindLabel: orderTypeToShortLabel(String(dto.orderType ?? '')) || '—',
    therapyTypeCode: resolveExecuteTherapyTypeCode(dto) || undefined,
    therapyLabel: (() => {
      const label = resolveExecuteTherapyLabel(dto);
      return label === '—' ? undefined : label;
    })(),
    content: buildExecuteContentTitle(dto),
    dosage: buildExecuteDosageDisplay(dto),
    extension: buildExecuteExtensionLine(dto) || undefined,
    startStopText: buildStartStopText(dto),
    statusEnum: statusEnum || undefined,
    statusDisplay,
    halted: statusEnum === 'STOPPED',
    planSchedules: groupSlotsByDate(orderId, orderType, tabSlots),
    bindItems: dto.bindItems ?? [],
    tcmFlag: dto.tcmFlag === true ? true : undefined,
    prescriptionNo: String(dto.prescriptionNo ?? '').trim() || undefined,
    lineRequestIds: mapLineRequestIds(dto.lineRequestIds),
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
            bindType: bind.bindType,
            bindingId: preserveApiIdForRequest(bind.bindingId),
            activityId: preserveApiIdForRequest(bind.activityId),
            deviceId: preserveApiIdForRequest(bind.deviceId),
            displayName: bind.displayName ?? bind.bindDisplayName,
            busNo: bind.busNo,
            specificationText: bind.specificationText,
            unitPrice: bind.unitPrice,
            chargeItemDefinitionId: preserveApiIdForRequest(bind.chargeItemDefinitionId),
            quantityNumerator: bind.quantityNumerator,
            quantityDenominator: bind.quantityDenominator,
            unitCode: bind.unitCode,
            unitCode_dictText: bind.unitCode_dictText,
            priority: bind.priority,
          }));
        }
        if (row.lineRequestIds?.length) {
          item.lineRequestIds = [...row.lineRequestIds];
        }
        if (row.prescriptionNo) {
          item.prescriptionNo = row.prescriptionNo;
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

/** 已执行 Tab 勾选槽位 → 取消执行 procedureId 列表（去重、保序） */
export function mapSelectedSlotsToCancelProcedureIds(
  rows: NurseStationOrderExecuteTableRow[],
  selectedSlotKeys: Set<string>,
): { missingCount: number; procedureIds: string[] } {
  const procedureIds: string[] = [];
  const seen = new Set<string>();
  let missingCount = 0;

  for (const row of rows) {
    for (const schedule of row.planSchedules) {
      for (const slot of schedule.slots) {
        if (!selectedSlotKeys.has(slot.slotKey)) continue;
        const procedureId = String(slot.procedureId ?? '').trim();
        if (!procedureId) {
          missingCount += 1;
          continue;
        }
        if (seen.has(procedureId)) continue;
        seen.add(procedureId);
        procedureIds.push(procedureId);
      }
    }
  }

  return { procedureIds, missingCount };
}
