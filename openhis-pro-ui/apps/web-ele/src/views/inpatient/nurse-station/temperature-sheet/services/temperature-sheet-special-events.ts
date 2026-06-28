import type {
  TemperatureSheetGridRowDto,
  TemperatureSheetTimeSlot,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.types';

import { TEMPERATURE_SHEET_TIME_SLOTS } from './map-nurse-station-temperature-sheet';
import type {
  TemperatureSheetChartPoint,
  TemperatureSheetFillRowDraft,
} from './map-nurse-station-temperature-sheet';
import {
  isSpecialEventChartMarkerCode,
  isSpecialEventShortcutCode,
  isSpecialEventTimeRequired,
  parseSpecialEventSlotFromDetailItems,
  SPECIAL_EVENT_ITEM_CODE,
} from './temperature-sheet-detail-items';
import {
  formatHmToChineseTime,
  formatTemperatureSheetTimeSlotChinese,
  parseDateTimeYmdHm,
  resolveDefaultEventTimeHm,
  resolveTemperatureSheetTimeSlotFromHm,
} from './temperature-sheet-time-format';

export { formatTemperatureSheetTimeSlotChinese } from './temperature-sheet-time-format';

/** 特殊事件 code（与后端 special_event_code 字典 item_code 一致） */
export type TemperatureSheetSpecialEventCode = string;

/** 下拉选项结构 */
export interface TemperatureSheetSpecialEventOption {
  label: string;
  value: string;
}

/** 单槽特殊事件存储 */
export interface TemperatureSheetSlotSpecialFlag {
  cooledTemperature?: string;
  eventTimeByCode?: Record<string, string>;
  eventTypes: string[];
  originalTemperature?: string;
  physicalCoolingEnabled?: boolean;
}

/** 就诊维度特殊事件（由接口网格组装，供图表展示） */
export interface TemperatureSheetSpecialEventStore {
  slotFlags: Record<string, TemperatureSheetSlotSpecialFlag>;
}

/** 图表竖排文字标记 */
export interface TemperatureSheetChartTextMarker {
  displayText: string;
  key: string;
  slotIndex: number;
  source: 'manual';
  stackOrder: number;
}

/** 物理降温图表数据点（原体温 + 30 分钟后降温后体温） */
export interface TemperatureSheetPhysicalCoolingChartPoint {
  cooledChartX: number;
  cooledTemp: number;
  originalChartX: number;
  originalTemp: number;
  /** 前一时次在折线上的坐标（用于红色虚线起点） */
  prevChartX?: number;
  prevSlotIndex?: number;
  /** 前一时次有效体温（用于红色虚线起点） */
  prevTemp?: number;
  slotIndex: number;
  timeSlot: TemperatureSheetTimeSlot;
}

/** 体温单相邻时次间隔（分钟） */
export const TEMPERATURE_SHEET_SLOT_INTERVAL_MINUTES = 240;

/** 物理降温后复测时间偏移（分钟） */
export const TEMPERATURE_SHEET_PHYSICAL_COOLING_DELAY_MINUTES = 30;

/** 由 init 接口 specialEventOptions 构建多选下拉（排除快捷项与脉搏短绌） */
export function buildSpecialEventSelectOptions(
  options: TemperatureSheetSpecialEventOption[] | undefined,
): TemperatureSheetSpecialEventOption[] {
  return (options ?? []).filter((option) => {
    const code = option.value.trim();
    return code.length > 0 && !isSpecialEventShortcutCode(code);
  });
}

/** 由 init 接口选项构建 code → 展示名映射 */
export function buildSpecialEventLabelMap(
  options: TemperatureSheetSpecialEventOption[] | undefined,
): Record<string, string> {
  const labelMap: Record<string, string> = {};
  for (const option of options ?? []) {
    const code = option.value.trim();
    const label = option.label.trim();
    if (!code) {
      continue;
    }
    labelMap[code] = label || code;
  }
  labelMap[SPECIAL_EVENT_ITEM_CODE.PHYSICAL_COOLING] = '物理降温';
  labelMap[SPECIAL_EVENT_ITEM_CODE.MEDICATION_COOLING] = '药物降温';
  labelMap[SPECIAL_EVENT_ITEM_CODE.TEMP_NOT_RISING] = '体温不升';
  return labelMap;
}

/** 解析特殊事件展示名 */
export function resolveSpecialEventLabel(
  eventCode: string,
  labelMap?: Record<string, string>,
): string {
  const code = eventCode.trim();
  return labelMap?.[code] ?? code;
}

/** 创建空白本地存储 */
export function createEmptySpecialEventStore(): TemperatureSheetSpecialEventStore {
  return {
    slotFlags: {},
  };
}

/** 生成槽位存储键 */
export function resolveSpecialEventSlotKey(
  recordDate: string,
  timeSlot: TemperatureSheetTimeSlot,
): string {
  return `${recordDate}|${timeSlot}`;
}

/** 解析当前行已选且须填时间的特殊事件 */
export function resolveTimeRequiredSpecialEvents(row: TemperatureSheetFillRowDraft): string[] {
  return row.specialEvents.filter((code) => isSpecialEventTimeRequired(code));
}

/** 特殊事件多选变更后同步时间字段 */
export function syncSpecialEventTimesOnSelectionChange(row: TemperatureSheetFillRowDraft): void {
  const selectedSet = new Set(row.specialEvents);
  for (const code of Object.keys(row.specialEventTimesByCode)) {
    if (code === SPECIAL_EVENT_ITEM_CODE.DISCHARGE) {
      continue;
    }
    if (!selectedSet.has(code)) {
      delete row.specialEventTimesByCode[code];
    }
  }
  for (const code of row.specialEvents) {
    if (!isSpecialEventTimeRequired(code)) {
      continue;
    }
    if (!row.specialEventTimesByCode[code]?.trim()) {
      row.specialEventTimesByCode[code] = resolveDefaultEventTimeHm(row.timeSlot);
    }
  }
  if (!row.dischargeEnabled) {
    delete row.specialEventTimesByCode[SPECIAL_EVENT_ITEM_CODE.DISCHARGE];
  }
}

/** 快捷勾选变更后同步关联字段 */
export function syncSpecialEventShortcutFields(row: TemperatureSheetFillRowDraft): void {
  if (row.dischargeEnabled) {
    if (!row.specialEventTimesByCode[SPECIAL_EVENT_ITEM_CODE.DISCHARGE]?.trim()) {
      row.specialEventTimesByCode[SPECIAL_EVENT_ITEM_CODE.DISCHARGE] = resolveDefaultEventTimeHm(
        row.timeSlot,
      );
    }
  } else {
    delete row.specialEventTimesByCode[SPECIAL_EVENT_ITEM_CODE.DISCHARGE];
  }
  if (!row.medicationCoolingEnabled) {
    row.medicationCooledTemperature = '';
  }
}

/** 链式单选：已选数量 + 1 个空槽 */
export function resolveSpecialEventPickerSlots(row: TemperatureSheetFillRowDraft): number[] {
  return Array.from({ length: row.specialEvents.length + 1 }, (_item, index) => index);
}

/** 链式单选：读取某槽已选值 */
export function resolveSpecialEventPickerValue(
  row: TemperatureSheetFillRowDraft,
  slotIndex: number,
): string {
  return row.specialEvents[slotIndex] ?? '';
}

/** 链式单选：排除其它槽已选项 */
export function resolveSpecialEventPickerOptions(
  row: TemperatureSheetFillRowDraft,
  slotIndex: number,
  options: TemperatureSheetSpecialEventOption[],
): TemperatureSheetSpecialEventOption[] {
  const usedCodes = new Set(row.specialEvents.filter((_code, index) => index !== slotIndex));
  return options.filter((option) => !usedCodes.has(option.value));
}

/** 链式单选：选中/清空后更新 specialEvents 并同步时间 */
export function handleSpecialEventPickerChange(
  row: TemperatureSheetFillRowDraft,
  slotIndex: number,
  rawValue: null | string | undefined,
): void {
  const code = (rawValue ?? '').trim();
  row.specialEvents = code
    ? [...row.specialEvents.slice(0, slotIndex), code]
    : row.specialEvents.slice(0, slotIndex);
  syncSpecialEventTimesOnSelectionChange(row);
}

/** 组装竖排事件文案（须选时间的事件带「事件|时间」） */
export function formatSpecialEventDisplayText(
  eventType: string,
  timeSlot: TemperatureSheetTimeSlot,
  itemValue?: string,
  labelMap?: Record<string, string>,
): string {
  const code = eventType.trim();
  if (!isSpecialEventTimeRequired(code)) {
    return resolveSpecialEventLabel(eventType, labelMap);
  }
  const hm = (itemValue ?? '').trim();
  const timeText = hm.includes(':')
    ? formatHmToChineseTime(hm)
    : hm || formatTemperatureSheetTimeSlotChinese(timeSlot);
  if (code === SPECIAL_EVENT_ITEM_CODE.ADMISSION) {
    return `入院|时间${timeText}`;
  }
  const label = resolveSpecialEventLabel(eventType, labelMap);
  return `${label}|${timeText}`;
}

/** 由接口网格行组装特殊事件存储（图表展示用） */
export function buildSpecialEventStoreFromGridRows(
  gridRows: TemperatureSheetGridRowDto[],
): TemperatureSheetSpecialEventStore {
  const slotFlags: Record<string, TemperatureSheetSlotSpecialFlag> = {};

  // 遍历窗口内日×6 槽，提取 special_event_code 明细投影
  for (const gridRow of gridRows) {
    const recordDate = (gridRow.recordDate ?? '').trim();
    if (!recordDate) {
      continue;
    }
    for (const cell of gridRow.slotCells ?? []) {
      const timeSlot = (cell.timeSlot ?? '').trim() as TemperatureSheetTimeSlot;
      if (!TEMPERATURE_SHEET_TIME_SLOTS.includes(timeSlot)) {
        continue;
      }
      const parsed = parseSpecialEventSlotFromDetailItems(cell.detailItems);
      if (!parsed) {
        continue;
      }
      slotFlags[resolveSpecialEventSlotKey(recordDate, timeSlot)] = {
        cooledTemperature: parsed.cooledTemperature,
        eventTimeByCode: parsed.eventTimeByCode,
        eventTypes: parsed.eventTypes,
        originalTemperature: parsed.originalTemperature,
        physicalCoolingEnabled: parsed.physicalCoolingEnabled,
      };
    }
  }

  return { slotFlags };
}

/** 从录入行提取当日槽位标记 */
export function extractSlotFlagsFromFillRows(
  rows: TemperatureSheetFillRowDraft[],
  recordDate: string,
): Record<string, TemperatureSheetSlotSpecialFlag> {
  const result: Record<string, TemperatureSheetSlotSpecialFlag> = {};
  for (const row of rows) {
    const hasShortcuts =
      row.dischargeEnabled ||
      row.physicalCoolingEnabled ||
      row.medicationCoolingEnabled ||
      row.tempNotRisingEnabled;
    const hasSpecialEvents = row.specialEvents.length > 0;
    if (!hasSpecialEvents && !hasShortcuts) {
      continue;
    }
    const eventTypes = [...row.specialEvents];
    if (row.dischargeEnabled) {
      eventTypes.push(SPECIAL_EVENT_ITEM_CODE.DISCHARGE);
    }
    if (row.medicationCoolingEnabled) {
      eventTypes.push(SPECIAL_EVENT_ITEM_CODE.MEDICATION_COOLING);
    }
    if (row.tempNotRisingEnabled) {
      eventTypes.push(SPECIAL_EVENT_ITEM_CODE.TEMP_NOT_RISING);
    }
    const eventTimeByCode: Record<string, string> = {};
    for (const code of row.specialEvents) {
      if (isSpecialEventTimeRequired(code)) {
        const hm = row.specialEventTimesByCode[code]?.trim();
        if (hm) {
          eventTimeByCode[code] = formatHmToChineseTime(hm);
        }
      }
    }
    if (row.dischargeEnabled) {
      const dischargeHm = row.specialEventTimesByCode[SPECIAL_EVENT_ITEM_CODE.DISCHARGE]?.trim();
      if (dischargeHm) {
        eventTimeByCode[SPECIAL_EVENT_ITEM_CODE.DISCHARGE] = formatHmToChineseTime(dischargeHm);
      }
    }
    result[resolveSpecialEventSlotKey(recordDate, row.timeSlot)] = {
      cooledTemperature: row.physicalCoolingEnabled
        ? row.cooledTemperature.trim() || undefined
        : undefined,
      eventTimeByCode,
      eventTypes,
      originalTemperature: row.temp.trim() || undefined,
      physicalCoolingEnabled: row.physicalCoolingEnabled,
    };
  }
  return result;
}

/** 合并当日槽位标记到存储 */
export function mergeSlotFlagsForDate(
  store: TemperatureSheetSpecialEventStore,
  recordDate: string,
  slotFlags: Record<string, TemperatureSheetSlotSpecialFlag>,
): TemperatureSheetSpecialEventStore {
  const nextSlotFlags = { ...store.slotFlags };
  // 先清掉当日旧标记，再写入新标记
  for (const key of Object.keys(nextSlotFlags)) {
    if (key.startsWith(`${recordDate}|`)) {
      delete nextSlotFlags[key];
    }
  }
  Object.assign(nextSlotFlags, slotFlags);
  return {
    slotFlags: nextSlotFlags,
  };
}

/** 统计存储中某类事件出现次数 */
export function countSpecialEventInStore(
  store: TemperatureSheetSpecialEventStore,
  eventType: string,
): number {
  let count = 0;
  for (const flag of Object.values(store.slotFlags)) {
    if (flag.eventTypes.includes(eventType)) {
      count += 1;
    }
  }
  return count;
}

/** 存储中是否已有入院事件（手录或历史数据） */
function hasAdmissionEventInStore(store: TemperatureSheetSpecialEventStore): boolean {
  for (const flag of Object.values(store.slotFlags)) {
    if (flag.eventTypes.includes(SPECIAL_EVENT_ITEM_CODE.ADMISSION)) {
      return true;
    }
  }
  return false;
}

/** 由患者入院时间生成图表竖排「入院|几时几分」标记 */
function buildPatientAdmissionChartMarker(
  admissionAt: string | undefined,
  visiblePoints: TemperatureSheetChartPoint[],
  labelMap?: Record<string, string>,
): TemperatureSheetChartTextMarker | null {
  const parsed = parseDateTimeYmdHm(admissionAt);
  if (!parsed) {
    return null;
  }
  const timeSlot = resolveTemperatureSheetTimeSlotFromHm(parsed.hm);
  const slotIndex = visiblePoints.findIndex(
    (point) => point.fullDate === parsed.dateYmd && point.timeSlot === timeSlot,
  );
  if (slotIndex < 0) {
    return null;
  }
  return {
    key: `auto-admission-${parsed.dateYmd}|${timeSlot}`,
    slotIndex,
    displayText: formatSpecialEventDisplayText(
      SPECIAL_EVENT_ITEM_CODE.ADMISSION,
      timeSlot,
      parsed.hm,
      labelMap,
    ),
    source: 'manual',
    stackOrder: 0,
  };
}

/** 合并手录竖排文字标记，同槽位纵向堆叠；无手录入院时由患者入院时间自动带入 */
export function buildChartTextMarkers(
  visiblePoints: TemperatureSheetChartPoint[],
  store: TemperatureSheetSpecialEventStore,
  labelMap?: Record<string, string>,
  patientAdmissionAt?: string,
): TemperatureSheetChartTextMarker[] {
  const rawMarkers: TemperatureSheetChartTextMarker[] = [];

  if (!hasAdmissionEventInStore(store)) {
    const autoAdmission = buildPatientAdmissionChartMarker(
      patientAdmissionAt,
      visiblePoints,
      labelMap,
    );
    if (autoAdmission) {
      rawMarkers.push(autoAdmission);
    }
  }

  for (const [slotKey, flag] of Object.entries(store.slotFlags)) {
    const [recordDate, timeSlot] = slotKey.split('|') as [string, TemperatureSheetTimeSlot];
    const slotIndex = visiblePoints.findIndex(
      (point) => point.fullDate === recordDate && point.timeSlot === timeSlot,
    );
    if (slotIndex < 0) {
      continue;
    }
    for (const eventType of flag.eventTypes) {
      if (!isSpecialEventChartMarkerCode(eventType)) {
        continue;
      }
      rawMarkers.push({
        key: `${slotKey}-${eventType}`,
        slotIndex,
        displayText: formatSpecialEventDisplayText(
          eventType,
          timeSlot,
          flag.eventTimeByCode?.[eventType],
          labelMap,
        ),
        source: 'manual',
        stackOrder: 0,
      });
    }
  }

  const stackCounter = new Map<number, number>();
  return rawMarkers.map((marker) => {
    const order = stackCounter.get(marker.slotIndex) ?? 0;
    stackCounter.set(marker.slotIndex, order + 1);
    return {
      ...marker,
      stackOrder: order,
    };
  });
}

/** 解析字符串为图表数字 */
function parseChartNumber(value?: string): number | undefined {
  const text = (value ?? '').trim();
  if (!text) {
    return undefined;
  }
  const num = Number(text);
  return Number.isFinite(num) ? num : undefined;
}

/** 解析物理降温后复测点在图表横轴的位置 */
export function resolvePhysicalCoolingCooledChartX(slotIndex: number): number {
  return (
    slotIndex +
    0.5 +
    TEMPERATURE_SHEET_PHYSICAL_COOLING_DELAY_MINUTES / TEMPERATURE_SHEET_SLOT_INTERVAL_MINUTES
  );
}

/** 解析槽位在体温折线上的有效点位（降温槽取降温前体温） */
function resolveSlotLineTempPoint(
  visiblePoints: TemperatureSheetChartPoint[],
  slotIndex: number,
  coolingBySlot: Map<number, TemperatureSheetPhysicalCoolingChartPoint>,
): { chartX: number; temp: number } | null {
  if (slotIndex < 0) {
    return null;
  }
  const cooling = coolingBySlot.get(slotIndex);
  if (cooling) {
    return { chartX: cooling.originalChartX, temp: cooling.originalTemp };
  }
  const point = visiblePoints[slotIndex];
  if (point?.temp === undefined) {
    return null;
  }
  return { chartX: slotIndex + 0.5, temp: point.temp };
}

/** 构建物理降温图表数据（原体温 + 30 分钟后降温后体温） */
export function buildPhysicalCoolingChartPoints(
  visiblePoints: TemperatureSheetChartPoint[],
  store: TemperatureSheetSpecialEventStore,
): TemperatureSheetPhysicalCoolingChartPoint[] {
  const result: TemperatureSheetPhysicalCoolingChartPoint[] = [];
  const coolingBySlot = new Map<number, TemperatureSheetPhysicalCoolingChartPoint>();
  visiblePoints.forEach((point, slotIndex) => {
    const flag = store.slotFlags[resolveSpecialEventSlotKey(point.fullDate, point.timeSlot)];
    if (!flag?.physicalCoolingEnabled) {
      return;
    }
    const originalTemp = point.temp ?? parseChartNumber(flag.originalTemperature);
    const cooledTemp = parseChartNumber(flag.cooledTemperature);
    if (originalTemp === undefined || cooledTemp === undefined) {
      return;
    }
    const prevSlotIndex = slotIndex - 1;
    const prevPoint = resolveSlotLineTempPoint(visiblePoints, prevSlotIndex, coolingBySlot);
    const entry: TemperatureSheetPhysicalCoolingChartPoint = {
      slotIndex,
      timeSlot: point.timeSlot,
      originalTemp,
      cooledTemp,
      originalChartX: slotIndex + 0.5,
      cooledChartX: resolvePhysicalCoolingCooledChartX(slotIndex),
    };
    // 虚线起点仅衔接同日上一时次；跨天则用本槽降温前体温，避免红线横跨日界
    const prevChartPoint = visiblePoints[prevSlotIndex];
    if (prevPoint && prevChartPoint && prevChartPoint.fullDate === point.fullDate) {
      entry.prevSlotIndex = prevSlotIndex;
      entry.prevChartX = prevPoint.chartX;
      entry.prevTemp = prevPoint.temp;
    }
    coolingBySlot.set(slotIndex, entry);
    result.push(entry);
  });
  return result;
}
/** 校验录入行特殊事件 */
export function validateFillRowsSpecialEvents(
  rows: TemperatureSheetFillRowDraft[],
  labelMap?: Record<string, string>,
): string | null {
  for (const row of rows) {
    for (const code of row.specialEvents) {
      if (!isSpecialEventTimeRequired(code)) {
        continue;
      }
      if (!(row.specialEventTimesByCode[code] ?? '').trim()) {
        const label = resolveSpecialEventLabel(code, labelMap);
        return `${row.timeSlot}:00 「${label}」须选择事件时间`;
      }
    }
    if (
      row.dischargeEnabled &&
      !(row.specialEventTimesByCode[SPECIAL_EVENT_ITEM_CODE.DISCHARGE] ?? '').trim()
    ) {
      return `${row.timeSlot}:00 「出院」须选择事件时间`;
    }
    if (row.physicalCoolingEnabled) {
      if (parseChartNumber(row.temp) === undefined) {
        return `${row.timeSlot}:00 勾选物理降温时须先填写体温`;
      }
      if (parseChartNumber(row.cooledTemperature) === undefined) {
        return `${row.timeSlot}:00 勾选物理降温时须填写降温后体温`;
      }
    }
    if (row.medicationCoolingEnabled) {
      if (parseChartNumber(row.temp) === undefined) {
        return `${row.timeSlot}:00 勾选药物降温时须先填写体温`;
      }
      if (parseChartNumber(row.medicationCooledTemperature) === undefined) {
        return `${row.timeSlot}:00 勾选药物降温时须填写降温后体温`;
      }
    }
  }
  return null;
}

/** 校验存储中死亡/出院唯一性 */
export function validateSpecialEventStore(store: TemperatureSheetSpecialEventStore): string | null {
  if (countSpecialEventInStore(store, SPECIAL_EVENT_ITEM_CODE.DEATH) > 1) {
    return '「死亡」事件只能登记一次';
  }
  if (countSpecialEventInStore(store, SPECIAL_EVENT_ITEM_CODE.DISCHARGE) > 1) {
    return '「出院」事件只能登记一次';
  }
  return null;
}

/** 判断录入行是否有特殊事件内容 */
export function hasFillRowSpecialEventContent(row: TemperatureSheetFillRowDraft): boolean {
  return (
    row.specialEvents.length > 0 ||
    row.dischargeEnabled ||
    row.physicalCoolingEnabled ||
    row.medicationCoolingEnabled ||
    row.tempNotRisingEnabled ||
    row.cooledTemperature.trim().length > 0 ||
    row.medicationCooledTemperature.trim().length > 0
  );
}

/** 录入行特殊事件摘要（表格单元格展示） */
export function buildFillRowSpecialEventSummary(
  row: TemperatureSheetFillRowDraft,
  labelMap?: Record<string, string>,
): string {
  const parts: string[] = [];
  if (row.dischargeEnabled) {
    const hm = row.specialEventTimesByCode[SPECIAL_EVENT_ITEM_CODE.DISCHARGE]?.trim();
    parts.push(hm ? `出院 ${hm}` : '出院');
  }
  if (row.physicalCoolingEnabled) {
    const cooled = row.cooledTemperature.trim();
    parts.push(cooled ? `物理降温 ${cooled}℃` : '物理降温');
  }
  if (row.medicationCoolingEnabled) {
    const cooled = row.medicationCooledTemperature.trim();
    parts.push(cooled ? `药物降温 ${cooled}℃` : '药物降温');
  }
  if (row.tempNotRisingEnabled) {
    parts.push('体温不升');
  }
  for (const code of row.specialEvents) {
    const label = resolveSpecialEventLabel(code, labelMap);
    if (isSpecialEventTimeRequired(code)) {
      const hm = row.specialEventTimesByCode[code]?.trim();
      parts.push(hm ? `${label} ${hm}` : label);
      continue;
    }
    parts.push(label);
  }
  return parts.length > 0 ? parts.join(' · ') : '无';
}
