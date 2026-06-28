import type {
  TemperatureSheetDetailItemDto,
  TemperatureSheetTimeSlot,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.types';

import type {
  TemperatureSheetExtraRecordDraft,
  TemperatureSheetFillRowDraft,
} from './map-nurse-station-temperature-sheet';
import {
  formatHmToChineseTime,
  formatTemperatureSheetTimeSlotChinese,
  parseChineseTimeToHm,
} from './temperature-sheet-time-format';

/** 术语体系：生命体征 */
export const TERM_VITAL_SIGNS_CODE = 'vital_signs_code';

/** 术语体系：特殊事件 */
export const TERM_SPECIAL_EVENT_CODE = 'special_event_code';

/** 生命体征 item_code */
export const VITAL_ITEM_CODE = {
  DBP: 'DBP',
  HEIGHT: 'HEIGHT',
  HR: 'HR',
  INTAKE: 'INTAKE',
  OTHER: 'OTHER',
  PULSE: 'PULSE',
  RESP: 'RESP',
  SBP: 'SBP',
  SKIN_TEST_POSITIVE: 'SKIN_TEST_POSITIVE',
  STOOL: 'STOOL',
  TEMP: 'TEMP',
  URINE_FREQ: 'URINE_FREQ',
  URINE_VOL: 'URINE_VOL',
  WAIST: 'WAIST',
  WEIGHT: 'WEIGHT',
} as const;

/** 特殊事件 item_code */
export const SPECIAL_EVENT_ITEM_CODE = {
  ADMISSION: 'ADMISSION',
  DEATH: 'DEATH',
  DELIVERY: 'DELIVERY',
  DISCHARGE: 'DISCHARGE',
  MEDICATION_COOLING: 'MEDICATION_COOLING',
  OUT_FOR_EXAM: 'OUT_FOR_EXAM',
  PHYSICAL_COOLING: 'PHYSICAL_COOLING',
  PULSE_DEFICIT: 'PULSE_DEFICIT',
  REFUSE_TEMP: 'REFUSE_TEMP',
  SURGERY: 'SURGERY',
  TEMP_NOT_RISING: 'TEMP_NOT_RISING',
  TRANSFER: 'TRANSFER',
} as const;

/** 须选手动事件时间的特殊事件 */
export const SPECIAL_EVENT_TIME_REQUIRED_CODES = new Set<string>([
  SPECIAL_EVENT_ITEM_CODE.ADMISSION,
  SPECIAL_EVENT_ITEM_CODE.TRANSFER,
  SPECIAL_EVENT_ITEM_CODE.DEATH,
  SPECIAL_EVENT_ITEM_CODE.DISCHARGE,
  SPECIAL_EVENT_ITEM_CODE.DELIVERY,
]);

/** 在体温单图表对应时次下竖排展示的特殊事件 */
export const SPECIAL_EVENT_CHART_MARKER_CODES = new Set<string>([
  SPECIAL_EVENT_ITEM_CODE.ADMISSION,
  SPECIAL_EVENT_ITEM_CODE.TRANSFER,
  SPECIAL_EVENT_ITEM_CODE.SURGERY,
  SPECIAL_EVENT_ITEM_CODE.DELIVERY,
  SPECIAL_EVENT_ITEM_CODE.DISCHARGE,
  SPECIAL_EVENT_ITEM_CODE.DEATH,
]);

/** 不参与图表竖排标记的特殊事件（降温、脉搏短绌等） */
export const SPECIAL_EVENT_NON_CHART_MARKER_CODES = new Set<string>([
  SPECIAL_EVENT_ITEM_CODE.PHYSICAL_COOLING,
  SPECIAL_EVENT_ITEM_CODE.MEDICATION_COOLING,
  SPECIAL_EVENT_ITEM_CODE.PULSE_DEFICIT,
]);

/** 须填写降温后体温的特殊事件（下拉项；药物降温已改为快捷勾选） */
export const SPECIAL_EVENT_COOLED_TEMPERATURE_REQUIRED_CODES = new Set<string>([]);

/** 快捷勾选特殊事件（固定顺序，不出现在下拉） */
export const SPECIAL_EVENT_SHORTCUT_CODES = [
  SPECIAL_EVENT_ITEM_CODE.DISCHARGE,
  SPECIAL_EVENT_ITEM_CODE.PHYSICAL_COOLING,
  SPECIAL_EVENT_ITEM_CODE.MEDICATION_COOLING,
  SPECIAL_EVENT_ITEM_CODE.TEMP_NOT_RISING,
] as const;

/** 下拉中排除的特殊事件（含快捷项、脉搏短绌、入院自动带入） */
export const SPECIAL_EVENT_SHORTCUT_EXCLUDED_FROM_SELECT = new Set<string>([
  ...SPECIAL_EVENT_SHORTCUT_CODES,
  SPECIAL_EVENT_ITEM_CODE.PULSE_DEFICIT,
  SPECIAL_EVENT_ITEM_CODE.ADMISSION,
]);

/** 是否须填写事件时间 */
export function isSpecialEventTimeRequired(code: string): boolean {
  return SPECIAL_EVENT_TIME_REQUIRED_CODES.has(code.trim());
}

/** 是否须填写降温后体温 */
export function isSpecialEventCooledTemperatureRequired(code: string): boolean {
  return SPECIAL_EVENT_COOLED_TEMPERATURE_REQUIRED_CODES.has(code.trim());
}

/** 是否为快捷勾选特殊事件（不出现在下拉） */
export function isSpecialEventShortcutCode(code: string): boolean {
  return SPECIAL_EVENT_SHORTCUT_EXCLUDED_FROM_SELECT.has(code.trim());
}

/** 是否在体温单图表竖排展示 */
export function isSpecialEventChartMarkerCode(code: string): boolean {
  return SPECIAL_EVENT_CHART_MARKER_CODES.has(code.trim());
}
/** 读取明细项值 */
export function readDetailItemValue(
  detailItems: TemperatureSheetDetailItemDto[] | null | undefined,
  termCode: string,
  itemCode: string,
): string {
  for (const item of detailItems ?? []) {
    if ((item.termCode ?? '').trim() === termCode && (item.itemCode ?? '').trim() === itemCode) {
      return (item.itemValue ?? '').trim();
    }
  }
  return '';
}

/** 判断明细列表是否有内容 */
export function hasDetailItemsContent(
  detailItems: TemperatureSheetDetailItemDto[] | null | undefined,
): boolean {
  for (const item of detailItems ?? []) {
    if ((item.itemCode ?? '').trim()) {
      return true;
    }
  }
  return false;
}

/** 追加非空值明细项 */
function pushDetailItemIfPresent(
  items: TemperatureSheetDetailItemDto[],
  termCode: string,
  itemCode: string,
  itemValue?: string,
): void {
  const value = (itemValue ?? '').trim();
  if (!value) {
    return;
  }
  items.push({
    termCode,
    itemCode,
    itemValue: value,
  });
}

/** 录入行 + 补充项 → 保存用明细项 */
export function buildDetailItemsFromFillRow(
  row: TemperatureSheetFillRowDraft,
  extraRecord: TemperatureSheetExtraRecordDraft,
): TemperatureSheetDetailItemDto[] {
  const items: TemperatureSheetDetailItemDto[] = [];

  pushDetailItemIfPresent(items, TERM_VITAL_SIGNS_CODE, VITAL_ITEM_CODE.TEMP, row.temp);
  pushDetailItemIfPresent(items, TERM_VITAL_SIGNS_CODE, VITAL_ITEM_CODE.PULSE, row.pulse);
  pushDetailItemIfPresent(items, TERM_VITAL_SIGNS_CODE, VITAL_ITEM_CODE.RESP, row.respiratory);
  pushDetailItemIfPresent(items, TERM_VITAL_SIGNS_CODE, VITAL_ITEM_CODE.HR, row.heartRate);
  pushDetailItemIfPresent(items, TERM_VITAL_SIGNS_CODE, VITAL_ITEM_CODE.SBP, row.systolic);
  pushDetailItemIfPresent(items, TERM_VITAL_SIGNS_CODE, VITAL_ITEM_CODE.DBP, row.diastolic);

  for (const rawCode of row.specialEvents) {
    const itemCode = rawCode.trim();
    if (!itemCode || isSpecialEventShortcutCode(itemCode)) {
      continue;
    }
    let itemValue: string | undefined;
    if (isSpecialEventTimeRequired(itemCode)) {
      const hm = (row.specialEventTimesByCode[itemCode] ?? '').trim();
      itemValue = hm ? formatHmToChineseTime(hm) : undefined;
    }
    items.push({
      termCode: TERM_SPECIAL_EVENT_CODE,
      itemCode,
      itemValue,
    });
  }
  if (row.dischargeEnabled) {
    const dischargeHm = (
      row.specialEventTimesByCode[SPECIAL_EVENT_ITEM_CODE.DISCHARGE] ?? ''
    ).trim();
    items.push({
      termCode: TERM_SPECIAL_EVENT_CODE,
      itemCode: SPECIAL_EVENT_ITEM_CODE.DISCHARGE,
      itemValue: dischargeHm ? formatHmToChineseTime(dischargeHm) : undefined,
    });
  }
  if (row.physicalCoolingEnabled) {
    items.push({
      termCode: TERM_SPECIAL_EVENT_CODE,
      itemCode: SPECIAL_EVENT_ITEM_CODE.PHYSICAL_COOLING,
      itemValue: row.cooledTemperature.trim() || undefined,
    });
  }
  if (row.medicationCoolingEnabled) {
    items.push({
      termCode: TERM_SPECIAL_EVENT_CODE,
      itemCode: SPECIAL_EVENT_ITEM_CODE.MEDICATION_COOLING,
      itemValue: row.medicationCooledTemperature.trim() || undefined,
    });
  }
  if (row.tempNotRisingEnabled) {
    items.push({
      termCode: TERM_SPECIAL_EVENT_CODE,
      itemCode: SPECIAL_EVENT_ITEM_CODE.TEMP_NOT_RISING,
    });
  }

  if (row.timeSlot === '22') {
    pushDetailItemIfPresent(
      items,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.WEIGHT,
      extraRecord.weight,
    );
    pushDetailItemIfPresent(
      items,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.HEIGHT,
      extraRecord.height,
    );
    pushDetailItemIfPresent(
      items,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.INTAKE,
      extraRecord.intake,
    );
    pushDetailItemIfPresent(
      items,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.URINE_VOL,
      extraRecord.urine,
    );
    pushDetailItemIfPresent(
      items,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.STOOL,
      extraRecord.stoolCount,
    );
    pushDetailItemIfPresent(
      items,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.WAIST,
      extraRecord.abdominalCircumference,
    );
    pushDetailItemIfPresent(
      items,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.URINE_FREQ,
      extraRecord.urineCount,
    );
    pushDetailItemIfPresent(
      items,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.SKIN_TEST_POSITIVE,
      extraRecord.skinTestPositive,
    );
    pushDetailItemIfPresent(items, TERM_VITAL_SIGNS_CODE, VITAL_ITEM_CODE.OTHER, extraRecord.other);
  }

  return items;
}

/** 明细项 → 录入行 + 补充项 */
export function applyDetailItemsToFillRow(
  row: TemperatureSheetFillRowDraft,
  extraRecord: TemperatureSheetExtraRecordDraft,
  detailItems: TemperatureSheetDetailItemDto[] | null | undefined,
): void {
  row.temp = readDetailItemValue(detailItems, TERM_VITAL_SIGNS_CODE, VITAL_ITEM_CODE.TEMP);
  row.pulse = readDetailItemValue(detailItems, TERM_VITAL_SIGNS_CODE, VITAL_ITEM_CODE.PULSE);
  row.respiratory = readDetailItemValue(detailItems, TERM_VITAL_SIGNS_CODE, VITAL_ITEM_CODE.RESP);
  row.heartRate = readDetailItemValue(detailItems, TERM_VITAL_SIGNS_CODE, VITAL_ITEM_CODE.HR);
  row.systolic = readDetailItemValue(detailItems, TERM_VITAL_SIGNS_CODE, VITAL_ITEM_CODE.SBP);
  row.diastolic = readDetailItemValue(detailItems, TERM_VITAL_SIGNS_CODE, VITAL_ITEM_CODE.DBP);

  row.specialEvents = [];
  row.specialEventTimesByCode = {};
  row.dischargeEnabled = false;
  row.physicalCoolingEnabled = false;
  row.medicationCoolingEnabled = false;
  row.tempNotRisingEnabled = false;
  row.cooledTemperature = '';
  row.medicationCooledTemperature = '';

  for (const item of detailItems ?? []) {
    const termCode = (item.termCode ?? '').trim();
    const itemCode = (item.itemCode ?? '').trim();
    if (!itemCode) {
      continue;
    }
    if (termCode === TERM_SPECIAL_EVENT_CODE) {
      if (itemCode === SPECIAL_EVENT_ITEM_CODE.PHYSICAL_COOLING) {
        row.physicalCoolingEnabled = true;
        row.cooledTemperature = (item.itemValue ?? '').trim();
        continue;
      }
      if (itemCode === SPECIAL_EVENT_ITEM_CODE.PULSE_DEFICIT) {
        continue;
      }
      if (itemCode === SPECIAL_EVENT_ITEM_CODE.DISCHARGE) {
        row.dischargeEnabled = true;
        const itemValue = (item.itemValue ?? '').trim();
        if (itemValue) {
          row.specialEventTimesByCode[itemCode] = parseChineseTimeToHm(itemValue, row.timeSlot);
        }
        continue;
      }
      if (itemCode === SPECIAL_EVENT_ITEM_CODE.MEDICATION_COOLING) {
        row.medicationCoolingEnabled = true;
        row.medicationCooledTemperature = (item.itemValue ?? '').trim();
        continue;
      }
      if (itemCode === SPECIAL_EVENT_ITEM_CODE.TEMP_NOT_RISING) {
        row.tempNotRisingEnabled = true;
        continue;
      }
      if (!row.specialEvents.includes(itemCode)) {
        row.specialEvents.push(itemCode);
      }
      const itemValue = (item.itemValue ?? '').trim();
      if (isSpecialEventTimeRequired(itemCode) && itemValue) {
        row.specialEventTimesByCode[itemCode] = parseChineseTimeToHm(itemValue, row.timeSlot);
      }
    }
  }

  if (row.timeSlot === '22') {
    extraRecord.weight = readDetailItemValue(
      detailItems,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.WEIGHT,
    );
    extraRecord.height = readDetailItemValue(
      detailItems,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.HEIGHT,
    );
    extraRecord.intake = readDetailItemValue(
      detailItems,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.INTAKE,
    );
    extraRecord.urine = readDetailItemValue(
      detailItems,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.URINE_VOL,
    );
    extraRecord.stoolCount = readDetailItemValue(
      detailItems,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.STOOL,
    );
    extraRecord.abdominalCircumference = readDetailItemValue(
      detailItems,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.WAIST,
    );
    extraRecord.urineCount = readDetailItemValue(
      detailItems,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.URINE_FREQ,
    );
    extraRecord.skinTestPositive = readDetailItemValue(
      detailItems,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.SKIN_TEST_POSITIVE,
    );
    extraRecord.other = readDetailItemValue(
      detailItems,
      TERM_VITAL_SIGNS_CODE,
      VITAL_ITEM_CODE.OTHER,
    );
  }
}

/** 从网格槽明细读取体征值 */
export function readVitalValueFromDetailItems(
  detailItems: TemperatureSheetDetailItemDto[] | null | undefined,
  itemCode: string,
): string {
  return readDetailItemValue(detailItems, TERM_VITAL_SIGNS_CODE, itemCode);
}

/** 从槽位明细解析特殊事件标记（图表展示用） */
export function parseSpecialEventSlotFromDetailItems(
  detailItems: TemperatureSheetDetailItemDto[] | null | undefined,
): {
  cooledTemperature?: string;
  eventTimeByCode: Record<string, string>;
  eventTypes: string[];
  originalTemperature?: string;
  physicalCoolingEnabled: boolean;
} | null {
  const eventTypes: string[] = [];
  const eventTimeByCode: Record<string, string> = {};
  let physicalCoolingEnabled = false;
  let cooledTemperature: string | undefined;

  for (const item of detailItems ?? []) {
    if ((item.termCode ?? '').trim() !== TERM_SPECIAL_EVENT_CODE) {
      continue;
    }
    const itemCode = (item.itemCode ?? '').trim();
    if (!itemCode) {
      continue;
    }
    if (itemCode === SPECIAL_EVENT_ITEM_CODE.PHYSICAL_COOLING) {
      physicalCoolingEnabled = true;
      cooledTemperature = (item.itemValue ?? '').trim() || undefined;
      continue;
    }
    if (itemCode === SPECIAL_EVENT_ITEM_CODE.PULSE_DEFICIT) {
      continue;
    }
    if (!eventTypes.includes(itemCode)) {
      eventTypes.push(itemCode);
    }
    const itemValue = (item.itemValue ?? '').trim();
    if (itemValue) {
      eventTimeByCode[itemCode] = itemValue;
    }
  }

  const originalTemperature =
    readVitalValueFromDetailItems(detailItems, VITAL_ITEM_CODE.TEMP) || undefined;

  if (eventTypes.length === 0 && !physicalCoolingEnabled) {
    return null;
  }

  return {
    cooledTemperature,
    eventTimeByCode,
    eventTypes,
    originalTemperature,
    physicalCoolingEnabled,
  };
}

/** 获取 22 点槽明细（底部汇总用） */
export function resolveSlot22DetailItems(
  slotCells:
    | Array<{ detailItems?: TemperatureSheetDetailItemDto[] | null; timeSlot?: null | string }>
    | null
    | undefined,
): TemperatureSheetDetailItemDto[] {
  const slot22 = (slotCells ?? []).find((cell) => (cell.timeSlot ?? '').trim() === '22');
  return slot22?.detailItems ?? [];
}
