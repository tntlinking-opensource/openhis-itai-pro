import type {
  NurseStationOrderExecuteBindItemDto,
  NurseStationOrderExecuteOrderType,
  NurseStationOrderExecuteRowDto,
  NurseStationOrderExecuteSlotDto,
  NurseStationOrderExecuteTab,
  NurseStationOrderExecuteWriteSlotDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.types';
import type {
  AdvicePackageChildItemDto,
  ServiceRequestDetailSummaryDto,
} from '#/api/doctor-station/advice.types';
import type { DrugGroupDisplayItem, DrugGroupKey } from '#/utils/doctor-station/drug-group-table';

import { formatDateTime } from '@vben/utils';

import dayjs from 'dayjs';

import { mergeConsecutiveRowsByDrugGroup } from '#/utils/doctor-station/drug-group-table';
import { formatLongTermMedicationDoseLine } from '#/utils/doctor-station/medication-dose-display';
import {
  buildMedicationContentTitleWithPackageSplit,
  mergePackageSplitIntoSpec,
} from '#/utils/doctor-station/package-split-ratio-display';
import { formatMoneyYuanWithSymbol } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  SERVICE_CATEGORY_DISCHARGE,
  SERVICE_CATEGORY_ENTRUST,
  SERVICE_CATEGORY_NURSING,
  SERVICE_CATEGORY_PROOF,
  SERVICE_CATEGORY_TEST,
  SERVICE_CATEGORY_TRANSFER,
  SERVICE_CATEGORY_TREATMENT,
} from '#/utils/print/constants/service-request-category-codes';

/** 表格行：医嘱展示 + 当前 Tab 对应槽位分组 */
export interface NurseStationOrderExecuteTableRow {
  id: string;
  orderId: string;
  orderType: NurseStationOrderExecuteOrderType;
  encounterId: string;
  /** 诊疗类目 code（采血识别） */
  categoryEnum?: string;
  /** 药品成组号（与医生站 group_id 同源） */
  groupId?: string;
  /** 输液组号 int 形态 */
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
  /** 开立时间+开立人（BR-IP-ORD-031） */
  startTimeDisplay: string;
  /** 停嘱时间+停嘱人；无停嘱时为空 */
  stopTimeDisplay?: string;
  /** 开立/停嘱合并文案（兼容检索/导出） */
  startStopText: string;
  /** 请求状态 code */
  statusEnum?: string;
  /** 请求状态展示 */
  statusDisplay: string;
  /** 校对科室（执行科室）展示 */
  executeDeptDisplay: string;
  /** 是否已停嘱 */
  halted: boolean;
  /** 开立时间原始值（执行 actual 边界） */
  reqOpenedTime?: string;
  /** 开嘱医生展示名（检查/检验申请单打印） */
  requesterName?: string;
  /** 医嘱备注（申请单明细备注列） */
  remark?: string;
  /** 停嘱时间原始值（执行 actual 边界） */
  stopTime?: string;
  /** 给药途径 code（打印贴/筛选用，禁止用 usageDisplay） */
  methodCode?: string;
  /** 是否输液相关（与 methodCode 一并用于贴签判定） */
  infusionFlag?: boolean;
  /** 输液滴速编码（打印/回填；与 DTO speedCode 同源） */
  speedCode?: string;
  /** 输液滴速展示名（列表扩展行与瓶签打印用） */
  speedDisplay?: string;
  planSchedules: NurseStationOrderExecutePlanSchedule[];
  bindItems: NurseStationOrderExecuteBindItemDto[];
  /**
   * 套餐子项（打印用；列表不展示）
   */
  packageChildren?: AdvicePackageChildItemDto[];
  /**
   * 部位/标本明细（打印用；列表不展示）
   */
  serviceRequestDetails?: ServiceRequestDetailSummaryDto[];
  tcmFlag?: boolean;
  prescriptionNo?: string;
  lineRequestIds?: string[];
  /** 成组括线展示项（同 groupId 合并后） */
  groupItems?: DrugGroupDisplayItem[];
  /** 成组合并前的成员行（取消执行等需展开槽位） */
  mergedMedicationMembers?: NurseStationOrderExecuteTableRow[];
}

/** 按日期分组的槽位（用于「预计执行时点」列） */
export interface NurseStationOrderExecutePlanSchedule {
  date: string;
  slots: NurseStationOrderExecutePlanSlot[];
}

/** 可勾选的计划槽位 */
export interface NurseStationOrderExecutePlanSlot {
  slotKey: string;
  /** 计划时点（occurrence；写路径与勾选键始终用此值） */
  planTime: string;
  /**
   * 待执行建议实际时分（后端 defaultActualTime；首次槽=开立+2分钟、停嘱日末槽=停嘱）。
   * 写路径不改时分时优先于 planTime。
   */
  defaultActualTime?: string;
  /** 列表展示用时点文案（待执行=计划；已执行等终态 Tab 优先实际；终态可拼操作人） */
  timeLabel: string;
  /** 实际/登记时刻（recorded_time）；pending 通常为空 */
  recordedTime?: string;
  /** 操作人展示名（终态 Tab；与汇总领药「时刻+数量」同构拼接） */
  operatorName?: string;
  /** 已执行 Tab 取消执行写路径主键（净剩 COMPLETED procedureId） */
  procedureId?: string;
  checked: boolean;
}

/** 槽位列展示数据源：待执行看计划；终态 Tab 看实际（无则回落计划） */
export type ExecuteSlotDisplaySource = 'plan' | 'recorded';

/** 按 executeTab 决定时间列展示用计划还是实际 */
export function resolveExecuteSlotDisplaySource(
  executeTab: NurseStationOrderExecuteTab,
): ExecuteSlotDisplaySource {
  if (executeTab === 'pending') {
    return 'plan';
  }
  // 已执行：展示执行时间；不执行/取消：展示登记事实时间（无则回落计划）
  if (executeTab === 'executed') {
    return 'recorded';
  }
  // 不执行 / 取消执行：以「哪一次计划」为主（计划时点），与操作确认语义一致
  return 'plan';
}

/** 时间列标题：随 Tab 切换，避免「预计执行」歧义 */
export function resolveExecuteScheduleColumnLabel(executeTab: NurseStationOrderExecuteTab): string {
  if (executeTab === 'executed') {
    return '执行时间';
  }
  return '计划时间';
}

/**
 * 解析槽位「时间列」展示时刻（逻辑键 / 勾选 / 写路径仍锚 planTime）。
 * 待执行：优先 defaultActualTime（开嘱日首次=开立+2分钟、停嘱日末=停嘱）；
 * 已执行：优先 recordedTime。
 */
function resolveSlotDisplayInstant(
  slot: NurseStationOrderExecuteSlotDto,
  displaySource: ExecuteSlotDisplaySource,
): string {
  const planTime = String(slot.planTime ?? '').trim();
  if (displaySource === 'recorded') {
    const recorded = String(slot.recordedTime ?? '').trim();
    if (recorded) {
      return recorded;
    }
    return planTime;
  }
  // 首次槽：展示开立时刻；频次点（如 18:00）仅作逻辑锚，不进时间列文案
  const defaultActual = String(slot.defaultActualTime ?? '').trim();
  if (defaultActual) {
    return defaultActual;
  }
  return planTime;
}

const SLOT_KEY_SEP = '\u0001';

function orderTypeToShortLabel(orderType: string): string {
  const t = orderType.trim().toUpperCase();
  if (t === 'MED_REQUEST') return '药品';
  if (t === 'SERVICE_REQUEST') return '诊疗';
  return orderType || '医嘱';
}

/** 类型列：优先后端 orderKindDisplay，回落类目/orderType（嘱托禁止显示「诊疗」） */
function resolveExecuteOrderKindLabel(dto: NurseStationOrderExecuteRowDto): string {
  const fromApi = String(dto.orderKindDisplay ?? '').trim();
  if (fromApi) return fromApi;
  const category = String(dto.categoryEnum ?? '').trim();
  if (category === '34') return '出院';
  if (category === '32') return '转科';
  if (category === '26') return '护理';
  if (category === '52') return '嘱托';
  return orderTypeToShortLabel(String(dto.orderType ?? ''));
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

/** 组装列表「类型」列：长临 + 药/诊/嘱托等 */
function buildExecuteTypeLabel(dto: NurseStationOrderExecuteRowDto): string {
  const therapy = resolveExecuteTherapyLabel(dto);
  const kind = resolveExecuteOrderKindLabel(dto);
  if (therapy && therapy !== '—' && kind) return `${therapy}/${kind}`;
  return therapy === '—' ? kind || '—' : therapy;
}

/** 医嘱名称摘要：名称【5mg1片x100】；中药方折叠行仅用方级展示名 */
function buildExecuteContentTitle(dto: NurseStationOrderExecuteRowDto): string {
  if (dto.tcmFlag === true) {
    const collapsedName = String(dto.adviceDisplayName ?? '').trim();
    if (collapsedName) return collapsedName;
  }
  const name = String(dto.adviceDisplayName ?? '').trim();
  const spec = String(dto.specificationText ?? '').trim();
  const title = buildMedicationContentTitleWithPackageSplit(name, spec, dto.packageSplitRatioText);
  return title || '—';
}

/** 剂量/数量：长期「一次一片，每日三片」；中药方展示各味单次量 + 付数 */
function buildExecuteDosageDisplay(dto: NurseStationOrderExecuteRowDto): string {
  if (dto.tcmFlag === true) {
    const singleDose = String(dto.dosageDisplay ?? '').trim();
    const fuCount = dto.chineseHerbsDoseQuantity;
    const fuText =
      fuCount !== null && fuCount !== undefined && String(fuCount).trim()
        ? `${String(fuCount).trim()}付`
        : '';
    if (singleDose && fuText) {
      return `${singleDose} ${fuText}`;
    }
    return singleDose || fuText || '—';
  }
  // 长期药：单次量 + 日总量（频次在用法行）
  const therapy = String(dto.therapyEnum ?? '')
    .trim()
    .toUpperCase();
  if (therapy === 'LONG_TERM') {
    const longTermLine = formatLongTermMedicationDoseLine({
      dosageDisplay: dto.dosageDisplay,
      dailyDoseDisplay: dto.dailyDoseDisplay,
    });
    if (longTermLine) return longTermLine;
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

/** 用法、频次、滴速、备注拼成扩展行 */
function buildExecuteExtensionLine(dto: NurseStationOrderExecuteRowDto): string {
  const parts: string[] = [];
  const usage = String(dto.usageDisplay ?? '').trim();
  const freq = String(dto.frequencyDisplay ?? '').trim();
  const speed = String(dto.speedDisplay ?? '').trim();
  if (usage && freq) {
    parts.push(`${usage}，${freq}`);
  } else if (usage || freq) {
    parts.push(usage || freq);
  }
  if (speed) {
    parts.push(`滴速：${speed}`);
  }
  const remark = String(dto.remark ?? '').trim();
  if (remark) {
    parts.push(`备注：${remark}`);
  }
  // 口服一盒一点剩余（BR-IP-EXE-038）
  if (dto.oralPackageBatchInventoryApplied === true) {
    const unit = String(dto.oralPackageSplitUnitDisplay ?? '').trim() || '片';
    const eaten = String(dto.oralPackageConsumedSplitQuantity ?? '').trim();
    const remain = String(dto.oralPackageRemainSplitQuantity ?? '').trim();
    const next = String(dto.oralPackageNextClaimDate ?? '').trim();
    if (eaten !== '' || remain !== '') {
      parts.push(`已吃${eaten || '0'}${unit}，剩余${remain || '0'}${unit}`);
    }
    if (next) {
      parts.push(`预计下次领盒 ${next}`);
    }
  }
  return parts.join('；');
}

/** 开立/停嘱时间分行文案（时间后跟人名，BR-IP-ORD-031） */
function buildStartStopParts(dto: NurseStationOrderExecuteRowDto): {
  start: string;
  stop: string;
} {
  const startRaw = String(dto.reqOpenedTime ?? '').trim();
  const stopRaw = String(dto.stopTime ?? '').trim();
  const requester = String(dto.requesterName ?? '').trim();
  const stopper = String(dto.stopperName ?? '').trim();
  let start = startRaw ? formatDateTime(startRaw) : '';
  let stop = stopRaw ? formatDateTime(stopRaw) : '';
  if (start && requester) {
    start = `${start} ${requester}`;
  }
  if (stop && stopper) {
    stop = `${stop} ${stopper}`;
  }
  return { start: start || '—', stop };
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
  displaySource: ExecuteSlotDisplaySource = 'plan',
): NurseStationOrderExecutePlanSchedule[] {
  const byDate = new Map<string, NurseStationOrderExecutePlanSlot[]>();
  for (const slot of slots) {
    const planTime = String(slot.planTime ?? '').trim();
    if (!planTime) continue;
    // 日期分组必须锚定 planTime（哪一天的哪一顿）；已执行仅「时刻文案」改读 recorded
    const planParsed = dayjs(planTime);
    if (!planParsed.isValid()) continue;
    const dateKey = planParsed.format('MM-DD');
    const displayInstant = resolveSlotDisplayInstant(slot, displaySource);
    const displayParsed = dayjs(displayInstant);
    const timeSource = displayParsed.isValid() ? displayParsed : planParsed;
    // 终态 Tab：时刻后拼接操作人（同汇总领药「HH:mm 数量单位」）
    const timePart = timeSource.format('HH:mm');
    const operatorName = String(slot.operatorName ?? '').trim();
    const timeLabel = operatorName.length > 0 ? `${timePart} ${operatorName}` : timePart;
    const slotKey = buildExecuteSlotKey(orderId, orderType, planTime);
    const recordedTime = String(slot.recordedTime ?? '').trim() || undefined;
    const defaultActualTime = String(slot.defaultActualTime ?? '').trim() || undefined;
    const bucket = byDate.get(dateKey) ?? [];
    bucket.push({
      slotKey,
      planTime,
      defaultActualTime,
      timeLabel,
      recordedTime,
      operatorName: operatorName || undefined,
      procedureId: preserveApiIdForRequest(slot.procedureId) ?? undefined,
      checked: false,
    });
    byDate.set(dateKey, bucket);
  }
  const schedules: NurseStationOrderExecutePlanSchedule[] = [];
  for (const [date, planSlots] of byDate) {
    // 同日按计划点排序，避免已执行 Tab 按 recorded 文案打乱频次顺序
    planSlots.sort((a, b) => a.planTime.localeCompare(b.planTime));
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

/** 绑定项数量输入值（模板未配置时默认 1；显式 0 表示本趟不带出） */
export function resolveExecuteBindQuantityValue(bind: NurseStationOrderExecuteBindItemDto): number {
  const numerator = bind.quantityNumerator;
  if (numerator === null || numerator === undefined || Number.isNaN(Number(numerator))) {
    return 1;
  }
  return Number(numerator);
}

/** 长期医嘱带出数量本地记忆（按医嘱+目标项记住上次手改量） */
const LONG_TERM_BIND_QTY_MEMORY_KEY = 'openhis.ip.ns.order-execute.long-bind-qty';

/** 长期带出数量记忆键：orderId + 诊疗/耗材目标 */
export function buildLongTermBindQtyMemoryKey(
  orderId: string,
  bind: NurseStationOrderExecuteBindItemDto,
): null | string {
  const oid = preserveApiIdForRequest(orderId);
  if (!oid) {
    return null;
  }
  const activityId = preserveApiIdForRequest(bind.activityId);
  if (activityId) {
    return `${oid}|A|${activityId}`;
  }
  const deviceId = preserveApiIdForRequest(bind.deviceId);
  if (deviceId) {
    return `${oid}|D|${deviceId}`;
  }
  return null;
}

/** 读取长期带出数量记忆（localStorage；失败回空对象） */
export function readLongTermBindQtyMemory(): Record<string, number> {
  try {
    const raw = localStorage.getItem(LONG_TERM_BIND_QTY_MEMORY_KEY);
    if (!raw) {
      return {};
    }
    const parsed = JSON.parse(raw) as Record<string, number>;
    if (!parsed || typeof parsed !== 'object') {
      return {};
    }
    return parsed;
  } catch {
    return {};
  }
}

/** 写入长期带出数量记忆（护士手改后下次列表沿用） */
export function writeLongTermBindQtyMemory(
  orderId: string,
  bind: NurseStationOrderExecuteBindItemDto,
  qty: number,
) {
  const key = buildLongTermBindQtyMemoryKey(orderId, bind);
  if (!key) {
    return;
  }
  const nextQty = Number.isFinite(qty) ? Math.max(0, Math.floor(qty)) : 0;
  const memory = readLongTermBindQtyMemory();
  memory[key] = nextQty;
  try {
    localStorage.setItem(LONG_TERM_BIND_QTY_MEMORY_KEY, JSON.stringify(memory));
  } catch {
    // 忽略配额/隐私模式失败，不影响当次编辑
  }
}

/**
 * 长期医嘱行：用本地记忆覆盖 bindItems 数量（优先于模板；与后端上次执行回显互补）。
 * 仅 therapyTypeCode=LONG_TERM 生效。
 */
export function applyLongTermBindQtyMemoryToRows(rows: NurseStationOrderExecuteTableRow[]): void {
  const memory = readLongTermBindQtyMemory();
  if (Object.keys(memory).length === 0) {
    return;
  }
  for (const row of rows) {
    if (
      String(row.therapyTypeCode ?? '')
        .trim()
        .toUpperCase() !== 'LONG_TERM'
    ) {
      continue;
    }
    const bindItems = row.bindItems;
    if (!bindItems?.length) {
      continue;
    }
    for (let i = 0; i < bindItems.length; i += 1) {
      const bind = bindItems[i];
      if (!bind) {
        continue;
      }
      const key = buildLongTermBindQtyMemoryKey(row.orderId, bind);
      if (!key) {
        continue;
      }
      const remembered = memory[key];
      if (remembered === null || remembered === undefined || Number.isNaN(Number(remembered))) {
        continue;
      }
      bindItems[i] = {
        ...bind,
        quantityNumerator: Math.max(0, Math.floor(Number(remembered))),
        quantityDenominator: 1,
      };
    }
  }
}

/** 对 encounter 索引内全部行应用长期带出数量记忆 */
export function applyLongTermBindQtyMemoryToIndex(
  index: Map<string, NurseStationOrderExecuteTableRow[]>,
): Map<string, NurseStationOrderExecuteTableRow[]> {
  const next = new Map<string, NurseStationOrderExecuteTableRow[]>();
  for (const [encounterId, rows] of index) {
    const cloned = rows.map((row) => ({
      ...row,
      bindItems: row.bindItems?.map((b) => ({ ...b })) ?? [],
    }));
    applyLongTermBindQtyMemoryToRows(cloned);
    next.set(encounterId, cloned);
  }
  return next;
}

/** 绑定项数量 + 单位展示 */
export function formatExecuteBindItemQuantity(bind: NurseStationOrderExecuteBindItemDto): string {
  const unit = String(bind.unitCode_dictText ?? bind.unitCode ?? '').trim();
  const numerator = bind.quantityNumerator;
  if (numerator === null || numerator === undefined) {
    return unit || '—';
  }
  const denominator = bind.quantityDenominator;
  let qtyText = String(numerator);
  if (denominator !== null && denominator !== undefined && denominator !== 1) {
    qtyText = `${numerator}/${denominator}`;
  }
  return unit ? `${qtyText}${unit}` : qtyText;
}

/** 绑定项单价展示 */
export function formatExecuteBindItemUnitPrice(unitPrice?: number): string {
  if (unitPrice === null || unitPrice === undefined || Number.isNaN(unitPrice)) {
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
  const groupId = String(dto.groupId ?? dto.groupNo ?? '').trim() || undefined;
  // 开立/停嘱分行，供列表醒目展示停嘱行
  const startStopParts = buildStartStopParts(dto);

  return {
    id,
    orderId,
    orderType,
    encounterId: preserveApiIdForRequest(dto.encounterId) ?? '',
    categoryEnum: String(dto.categoryEnum ?? '').trim() || undefined,
    groupId,
    groupNo: dto.groupNo ?? undefined,
    typeLabel: buildExecuteTypeLabel(dto),
    // 与校对同源：嘱托类目 52 / orderKindDisplay 展示「嘱托」，禁止仅按 orderType 回落「诊疗」
    orderKindLabel: resolveExecuteOrderKindLabel(dto) || '—',
    therapyTypeCode: resolveExecuteTherapyTypeCode(dto) || undefined,
    therapyLabel: (() => {
      const label = resolveExecuteTherapyLabel(dto);
      return label === '—' ? undefined : label;
    })(),
    content: buildExecuteContentTitle(dto),
    dosage: buildExecuteDosageDisplay(dto),
    extension: buildExecuteExtensionLine(dto) || undefined,
    startTimeDisplay: startStopParts.start,
    stopTimeDisplay: startStopParts.stop || undefined,
    startStopText: startStopParts.stop
      ? `${startStopParts.start}\n${startStopParts.stop}`
      : startStopParts.start,
    statusEnum: statusEnum || undefined,
    statusDisplay,
    executeDeptDisplay: String(dto.performerCheckOrgDisplay ?? '').trim() || '—',
    halted: statusEnum === 'STOPPED',
    reqOpenedTime: String(dto.reqOpenedTime ?? '').trim() || undefined,
    requesterName: String(dto.requesterName ?? '').trim() || undefined,
    remark: String(dto.remark ?? '').trim() || undefined,
    stopTime: String(dto.stopTime ?? '').trim() || undefined,
    methodCode: String(dto.methodCode ?? '').trim() || undefined,
    infusionFlag: dto.infusionFlag === true ? true : dto.infusionFlag === false ? false : undefined,
    speedCode: String(dto.speedCode ?? '').trim() || undefined,
    speedDisplay: String(dto.speedDisplay ?? '').trim() || undefined,
    planSchedules: groupSlotsByDate(
      orderId,
      orderType,
      tabSlots,
      resolveExecuteSlotDisplaySource(executeTab),
    ),
    bindItems: dto.bindItems ?? [],
    // 打印用；页面表格不展示
    packageChildren: dto.packageChildren ?? undefined,
    serviceRequestDetails: dto.serviceRequestDetails ?? undefined,
    tcmFlag: dto.tcmFlag === true ? true : undefined,
    prescriptionNo: String(dto.prescriptionNo ?? '').trim() || undefined,
    lineRequestIds: mapLineRequestIds(dto.lineRequestIds),
  };
}

/** 成组括线展示项：对齐门诊护士站 */
function executeDtoToDrugGroupItem(dto: NurseStationOrderExecuteRowDto): DrugGroupDisplayItem {
  const therapy = String(dto.therapyEnum ?? '')
    .trim()
    .toUpperCase();
  // 长期：单次+日总量；瓶贴打印与列表剂量列同源
  const longTermDoseLine =
    therapy === 'LONG_TERM'
      ? formatLongTermMedicationDoseLine({
          dosageDisplay: dto.dosageDisplay,
          dailyDoseDisplay: dto.dailyDoseDisplay,
        })
      : '';
  const doseText = longTermDoseLine || String(dto.dosageDisplay ?? '').trim();
  return {
    selectedDrugName: String(dto.adviceDisplayName ?? '').trim(),
    selectedDrugSpec: mergePackageSplitIntoSpec(
      String(dto.specificationText ?? '').trim(),
      dto.packageSplitRatioText,
    ),
    pharmacy: '',
    singleDose: doseText,
    doseSubUnit: '',
    totalAmount: '',
    totalAmountUnit: '',
    usage: String(dto.usageDisplay ?? '').trim(),
    frequency: String(dto.frequencyDisplay ?? '').trim(),
    medicationDays: '',
    dosageDisplay: doseText || undefined,
    speedDisplay:
      String(dto.speedDisplay ?? '').trim() || String(dto.speedCode ?? '').trim() || undefined,
  };
}

/** 西药成组合并键：仅药嘱且非中药方折叠行 */
function executeMedicationGroupMergeKey(dto: NurseStationOrderExecuteRowDto): DrugGroupKey {
  const orderTypeRaw = String(dto.orderType ?? '')
    .trim()
    .toUpperCase();
  if (orderTypeRaw !== 'MED_REQUEST') {
    return '';
  }
  if (dto.tcmFlag === true) {
    return '';
  }
  const gid = String(dto.groupId ?? dto.groupNo ?? '').trim();
  return gid === '' ? '' : gid;
}

/** 成组同滴速：取组内首条非空 speedDisplay/speedCode（溶媒首行常为空） */
function pickGroupSpeedFromExecuteSlice(slice: readonly NurseStationOrderExecuteRowDto[]): {
  speedCode?: string;
  speedDisplay?: string;
} {
  for (const dto of slice) {
    const speedDisplay = String(dto.speedDisplay ?? '').trim();
    const speedCode = String(dto.speedCode ?? '').trim();
    if (speedDisplay || speedCode) {
      return {
        speedCode: speedCode || undefined,
        speedDisplay: speedDisplay || undefined,
      };
    }
  }
  return {};
}

/**
 * 相同 groupId 且连续的西药行合并为一行，挂 groupItems；槽位取代表行（组员同频次）。
 * 写路径回传 lineRequestIds，后端按成组/方级扩行。
 */
function mergeExecuteMedicationRowsForDisplay(
  rows: NurseStationOrderExecuteRowDto[],
  executeTab: NurseStationOrderExecuteTab,
): NurseStationOrderExecuteTableRow[] {
  return mergeConsecutiveRowsByDrugGroup(rows, {
    getGroupKey: executeMedicationGroupMergeKey,
    toGroupItem: executeDtoToDrugGroupItem,
    buildMergedRow: ({ slice, groupItems }) => {
      const members = slice.map((dto) => mapExecuteRowDtoToTableRow(dto, executeTab));
      const firstMember = members[0];
      const headDto = slice[0];
      if (firstMember === undefined || headDto === undefined) {
        throw new Error('mergeExecuteMedicationRowsForDisplay: empty slice');
      }
      if (members.length === 1) {
        return { ...firstMember };
      }
      const lineRequestIds = members.map((m) => m.orderId).filter((id) => id.length > 0);
      const gid = String(headDto.groupId ?? headDto.groupNo ?? '').trim();
      const idPart = lineRequestIds.join('&');
      const groupSpeed = pickGroupSpeedFromExecuteSlice(slice);
      const extensionDto: NurseStationOrderExecuteRowDto = {
        ...headDto,
        speedCode: groupSpeed.speedCode ?? headDto.speedCode,
        speedDisplay: groupSpeed.speedDisplay ?? headDto.speedDisplay,
      };
      return {
        ...firstMember,
        id: gid ? `merged-med-${gid}-${idPart}` : `merged-med-${idPart}`,
        groupItems,
        mergedMedicationMembers: members,
        lineRequestIds,
        speedCode: groupSpeed.speedCode ?? firstMember.speedCode,
        speedDisplay: groupSpeed.speedDisplay ?? firstMember.speedDisplay,
        extension: buildExecuteExtensionLine(extensionDto) || undefined,
      };
    },
  });
}

/** 当前 Tab 下该行是否仍有可展示时点（待执行 Tab 即是否还有待执行点） */
export function hasExecuteRowPlanSlots(row: NurseStationOrderExecuteTableRow): boolean {
  for (const schedule of row.planSchedules ?? []) {
    if ((schedule.slots?.length ?? 0) > 0) {
      return true;
    }
  }
  return false;
}

/**
 * 每人医嘱列表：有当前 Tab 时点的置顶，无时点（如待执行 Tab 下已全部执行完）沉底。
 * 稳定排序，同组相对顺序保持后端开立时间序。
 */
export function sortExecuteRowsByHasPlanSlots(
  rows: NurseStationOrderExecuteTableRow[],
): NurseStationOrderExecuteTableRow[] {
  return [...rows].sort((a, b) => {
    const aRank = hasExecuteRowPlanSlots(a) ? 0 : 1;
    const bRank = hasExecuteRowPlanSlots(b) ? 0 : 1;
    return aRank - bRank;
  });
}

/** 接口分组 → encounterId → 表格行（含西药成组合并） */
export function indexExecuteGroupsByEncounterId(
  groups: undefined | { encounterId?: string; orders?: NurseStationOrderExecuteRowDto[] }[],
  executeTab: NurseStationOrderExecuteTab,
): Map<string, NurseStationOrderExecuteTableRow[]> {
  const map = new Map<string, NurseStationOrderExecuteTableRow[]>();
  for (const group of groups ?? []) {
    const encounterId = preserveApiIdForRequest(group.encounterId);
    if (!encounterId) continue;
    const rawRows = (group.orders ?? []).filter(
      (dto): dto is NonNullable<typeof dto> => dto !== null && dto !== undefined,
    );
    map.set(
      encounterId,
      sortExecuteRowsByHasPlanSlots(mergeExecuteMedicationRowsForDisplay(rawRows, executeTab)),
    );
  }
  return map;
}

/**
 * 医嘱执行「项目类型」快捷筛选（UI code；与后端 medicineCategoryTab 不同）。
 * 检验=22、检查=23、治疗=21、嘱托=52。
 * 空选=查全部（仅排除护理=26/转科=32/出院=34）；勾选才按快捷类型收窄。
 * 会诊等未挂快捷项的医嘱不归类、也不因白名单被抹掉。
 */
export type NurseStationOrderExecuteProjectType =
  | 'entrust'
  | 'exam'
  | 'lab'
  | 'tcm'
  | 'treatment'
  | 'west';

/** 快捷筛选可选项（仅用于勾选收窄；空选不依赖本列表） */
export const NURSE_STATION_ORDER_EXECUTE_PROJECT_TYPES: NurseStationOrderExecuteProjectType[] = [
  'west',
  'tcm',
  'lab',
  'exam',
  'treatment',
  'entrust',
];

/** 执行列表永不展示的诊疗类目（护理级别 / 转科 / 出院） */
const EXECUTE_LIST_EXCLUDED_SERVICE_CATEGORIES = new Set([
  SERVICE_CATEGORY_NURSING,
  SERVICE_CATEGORY_TRANSFER,
  SERVICE_CATEGORY_DISCHARGE,
]);

/**
 * 行是否命中某快捷筛选项（用 orderType / tcmFlag / categoryEnum）。
 * 未挂快捷项的诊疗（如会诊）返回 null，空选时仍展示。
 */
export function resolveExecuteRowProjectType(
  row: Pick<NurseStationOrderExecuteTableRow, 'categoryEnum' | 'orderType' | 'tcmFlag'>,
): NurseStationOrderExecuteProjectType | null {
  if (row.tcmFlag === true) {
    return 'tcm';
  }
  if (row.orderType === 'MED_REQUEST') {
    return 'west';
  }
  const category = String(row.categoryEnum ?? '').trim();
  if (EXECUTE_LIST_EXCLUDED_SERVICE_CATEGORIES.has(category)) {
    return null;
  }
  if (category === SERVICE_CATEGORY_PROOF) {
    return 'lab';
  }
  if (category === SERVICE_CATEGORY_TEST) {
    return 'exam';
  }
  if (category === SERVICE_CATEGORY_TREATMENT) {
    return 'treatment';
  }
  if (category === SERVICE_CATEGORY_ENTRUST) {
    return 'entrust';
  }
  return null;
}

/**
 * 项目类型快捷过滤：空选仅排除护理/转科/出院；有勾选再按快捷类型收窄。
 */
export function filterExecuteRowsByProjectTypes(
  rows: NurseStationOrderExecuteTableRow[],
  selected: readonly NurseStationOrderExecuteProjectType[],
): NurseStationOrderExecuteTableRow[] {
  const quickFilterOn = selected.length > 0;
  const allowed = quickFilterOn ? new Set(selected) : null;
  return rows.filter((row) => {
    const category = String(row.categoryEnum ?? '').trim();
    // 排除法：护理/转科/出院永不展示
    if (EXECUTE_LIST_EXCLUDED_SERVICE_CATEGORIES.has(category)) {
      return false;
    }
    // 空选：其余医嘱全部保留（含会诊等未挂快捷项）
    if (!allowed) {
      return true;
    }
    // 快捷勾选：仅保留命中项；未挂快捷项的行不进入勾选结果
    const projectType = resolveExecuteRowProjectType(row);
    return projectType !== null && allowed.has(projectType);
  });
}

/** 合并多次 list 的就诊分组（按 encounterId 拼订单，行按 listRowKey/orderId+orderType 去重） */
export function mergeExecutePatientGroups(
  groupLists: Array<
    undefined | { encounterId?: string; orders?: NurseStationOrderExecuteRowDto[] }[]
  >,
): { encounterId?: string; orders?: NurseStationOrderExecuteRowDto[] }[] {
  const ordersByEncounter = new Map<string, NurseStationOrderExecuteRowDto[]>();
  const seenRowKeyByEncounter = new Map<string, Set<string>>();

  for (const groups of groupLists) {
    for (const group of groups ?? []) {
      const encounterId = preserveApiIdForRequest(group.encounterId);
      if (!encounterId) continue;
      let orders = ordersByEncounter.get(encounterId);
      if (!orders) {
        orders = [];
        ordersByEncounter.set(encounterId, orders);
        seenRowKeyByEncounter.set(encounterId, new Set());
      }
      const seen = seenRowKeyByEncounter.get(encounterId)!;
      for (const dto of group.orders ?? []) {
        if (!dto) continue;
        const orderId = preserveApiIdForRequest(dto.orderId) ?? '';
        const orderType = String(dto.orderType ?? '')
          .trim()
          .toUpperCase();
        const listKey = String(dto.listRowKey ?? '').trim();
        const dedupeKey = listKey || `${orderId}${SLOT_KEY_SEP}${orderType}`;
        if (!dedupeKey || seen.has(dedupeKey)) continue;
        seen.add(dedupeKey);
        orders.push(dto);
      }
    }
  }

  const merged: { encounterId?: string; orders?: NurseStationOrderExecuteRowDto[] }[] = [];
  for (const [encounterId, orders] of ordersByEncounter) {
    merged.push({ encounterId, orders });
  }
  return merged;
}

/** 索引后按项目类型过滤各就诊下的行 */
export function filterExecuteIndexByProjectTypes(
  index: Map<string, NurseStationOrderExecuteTableRow[]>,
  selected: readonly NurseStationOrderExecuteProjectType[],
): Map<string, NurseStationOrderExecuteTableRow[]> {
  const next = new Map<string, NurseStationOrderExecuteTableRow[]>();
  for (const [encounterId, rows] of index) {
    next.set(encounterId, filterExecuteRowsByProjectTypes(rows, selected));
  }
  return next;
}

/**
 * 组装单槽位 actualTime：不改时分则优先 defaultActualTime（首次=开立+2分钟、停嘱日末=停嘱），否则 planTime；
 * 改则日期钉死计划日、仅替换 HH:mm。
 *
 * @param planTime 计划时点（逻辑锚点，原样回传）
 * @param adjustHHmm 该槽右键「修改时间」的时分（如 08:20）；空则按默认/计划
 * @param defaultActualTime 后端建议实际时分（首次=开立+2分钟，停嘱日末=停嘱）
 */
export function resolveExecuteActualTime(
  planTime: string,
  adjustHHmm?: null | string,
  defaultActualTime?: null | string,
): string {
  const plan = dayjs(planTime);
  if (!plan.isValid()) {
    return String(planTime ?? '').trim();
  }
  const trimmed = String(adjustHHmm ?? '').trim();
  if (!trimmed) {
    // 有 defaultActualTime 则用建议值（开立+2分钟/停嘱），否则回落 planTime
    const fallback = String(defaultActualTime ?? '').trim();
    if (fallback) {
      const suggested = dayjs(fallback);
      if (suggested.isValid()) {
        return suggested.format('YYYY-MM-DD HH:mm:ss');
      }
    }
    return plan.format('YYYY-MM-DD HH:mm:ss');
  }
  const parts = trimmed.split(':');
  const hour = Number(parts[0]);
  const minute = Number(parts[1] ?? 0);
  if (Number.isNaN(hour) || Number.isNaN(minute)) {
    return plan.format('YYYY-MM-DD HH:mm:ss');
  }
  return plan.hour(hour).minute(minute).second(0).format('YYYY-MM-DD HH:mm:ss');
}

/**
 * 收集已勾选槽位 → 写请求 items。
 * actualTime：有右键改时时分则同日替换 HH:mm；否则优先 defaultActualTime / planTime。
 */
export function mapSelectedSlotsToWriteItems(
  rows: NurseStationOrderExecuteTableRow[],
  selectedSlotKeys: Set<string>,
  adjustHHmmBySlotKey: null | Readonly<Record<string, string>> | undefined,
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
        // 每槽独立：右键改时优先；否则 defaultActualTime（首次=开立+2分钟）/ planTime
        const slotAdjustHHmm = String(adjustHHmmBySlotKey?.[slot.slotKey] ?? '').trim() || null;
        const actualTime = resolveExecuteActualTime(
          slot.planTime,
          slotAdjustHHmm,
          slot.defaultActualTime,
        );
        // 临嘱：预计=执行，改不改时分都同值回传（后端亦强制对齐）
        const planTime =
          String(row.therapyTypeCode ?? '')
            .trim()
            .toUpperCase() === 'TEMPORARY'
            ? actualTime
            : slot.planTime;
        const item: NurseStationOrderExecuteWriteSlotDto = {
          orderId,
          orderType: row.orderType,
          planTime,
          actualTime,
        };
        if (accountId) {
          item.accountId = accountId;
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
            // 未配置时显式回传 1，避免后端按 null 默认而前端展示已改量不同步
            quantityNumerator: resolveExecuteBindQuantityValue(bind),
            quantityDenominator:
              bind.quantityDenominator === null || bind.quantityDenominator === undefined
                ? 1
                : bind.quantityDenominator,
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
  return mapSelectedSlotsToWriteItems(rows, selectedSlotKeys, null, false).map(
    ({ orderId, orderType, planTime }) => ({
      orderId,
      orderType,
      planTime,
    }),
  );
}

/** 已执行 Tab 勾选槽位 → 取消执行 procedureId 列表（去重、保序；成组展开各成员同 planTime） */
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
        // 成组：收集全部成员在同 planTime 的 procedureId；非成组仅本行
        const memberRows = row.mergedMedicationMembers?.length
          ? row.mergedMedicationMembers
          : [row];
        let foundAny = false;
        for (const member of memberRows) {
          for (const memberSchedule of member.planSchedules) {
            for (const memberSlot of memberSchedule.slots) {
              if (memberSlot.planTime !== slot.planTime) continue;
              const procedureId = String(memberSlot.procedureId ?? '').trim();
              if (!procedureId) continue;
              foundAny = true;
              if (seen.has(procedureId)) continue;
              seen.add(procedureId);
              procedureIds.push(procedureId);
            }
          }
        }
        if (!foundAny) {
          missingCount += 1;
        }
      }
    }
  }

  return { procedureIds, missingCount };
}
