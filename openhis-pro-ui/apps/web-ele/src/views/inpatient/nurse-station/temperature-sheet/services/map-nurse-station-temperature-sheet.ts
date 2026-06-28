import type {
  GetNurseStationTemperatureSheetDataResponse,
  SaveNurseStationTemperatureSheetAtRecordedTimeRequest,
  TemperatureSheetDetailItemDto,
  TemperatureSheetFillSlotDto,
  TemperatureSheetGridRowDto,
  TemperatureSheetGridSlotCellDto,
  TemperatureSheetDerivedMetaDto,
  TemperatureSheetTimeSlot,
  TemperatureSheetWindowRange,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.types';

import {
  applyDetailItemsToFillRow,
  buildDetailItemsFromFillRow,
  hasDetailItemsContent,
  readVitalValueFromDetailItems,
  resolveSlot22DetailItems,
  VITAL_ITEM_CODE,
} from './temperature-sheet-detail-items';

export const TEMPERATURE_SHEET_TIME_SLOTS: TemperatureSheetTimeSlot[] = [
  '02',
  '06',
  '10',
  '14',
  '18',
  '22',
];

export interface TemperatureSheetChartPoint {
  date: string;
  fullDate: string;
  heartRate?: number;
  pulse?: number;
  respiratory?: string;
  temp?: number;
  timeSlot: TemperatureSheetTimeSlot;
}

export interface TemperatureSheetFillRowDraft {
  cooledTemperature: string;
  diastolic: string;
  dischargeEnabled: boolean;
  heartRate: string;
  medicationCooledTemperature: string;
  medicationCoolingEnabled: boolean;
  physicalCoolingEnabled: boolean;
  pulse: string;
  respiratory: string;
  specialEvents: string[];
  specialEventTimesByCode: Record<string, string>;
  systolic: string;
  temp: string;
  tempNotRisingEnabled: boolean;
  timeSlot: TemperatureSheetTimeSlot;
  vitalSignId?: string;
}

export interface TemperatureSheetExtraRecordDraft {
  abdominalCircumference: string;
  height: string;
  intake: string;
  other: string;
  skinTestPositive: string;
  stoolCount: string;
  urine: string;
  urineCount: string;
  weight: string;
}

/** 底部网格：按日合并的汇总展示（对应 dailySupplement） */
export interface TemperatureSheetFooterDayCell {
  bloodPressureFirst: string;
  bloodPressureSecond: string;
  height: string;
  intake: string;
  other: string;
  skinTestPositive: string;
  stoolCount: string;
  urine: string;
  urineCount: string;
  weight: string;
}

/** 前一日统计归属的补充项：录入日 D 的 22:00 槽保存，底部展示在 D-1 列 */
export const TEMPERATURE_SHEET_PREVIOUS_DAY_SUPPLEMENT_FIELDS = [
  'stoolCount',
  'urineCount',
  'intake',
  'other',
  'urine',
] as const satisfies ReadonlyArray<
  keyof Pick<
    TemperatureSheetExtraRecordDraft,
    'intake' | 'other' | 'stoolCount' | 'urine' | 'urineCount'
  >
>;

/** 底部补充指标行（除呼吸、血压外），顺序与纸质体温单一致 */
export const TEMPERATURE_SHEET_FOOTER_SUPPLEMENT_ROWS = [
  { field: 'stoolCount', label: '大便次数' },
  { field: 'urineCount', label: '小便次数' },
  { field: 'intake', label: '总入量(ml)' },
  { field: 'other', label: '其他(ml)' },
  { field: 'urine', label: '尿量(ml)' },
  { field: 'weight', label: '体重(kg)' },
  { field: 'height', label: '身高(cm)' },
  { field: 'skinTestPositive', label: '皮试阳性' },
] as const satisfies ReadonlyArray<{
  field: keyof Omit<TemperatureSheetFooterDayCell, 'bloodPressureFirst' | 'bloodPressureSecond'>;
  label: string;
}>;

/** 录入日偏移自然日（YYYY-MM-DD） */
export function shiftTemperatureSheetRecordDateYmd(dateYmd: string, days: number): string {
  return addDaysToYmd(dateYmd, days);
}

/** 将日期格式化为表头 MM-DD */
export function formatTemperatureSheetDisplayDate(recordDate: string): string {
  const parts = recordDate.split('-');
  if (parts.length !== 3) {
    return recordDate;
  }
  return `${parts[1]}-${parts[2]}`;
}

/** 将 Date 格式化为 YYYY-MM-DD */
function formatDateYmd(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** 在 YYYY-MM-DD 基础上偏移自然日 */
function addDaysToYmd(dateYmd: string, days: number): string {
  const parts = dateYmd.split('-').map((part) => Number(part));
  if (parts.length !== 3 || parts.some((part) => !Number.isFinite(part))) {
    return dateYmd;
  }
  const [year, month, day] = parts;
  const date = new Date(year, month - 1, day);
  date.setDate(date.getDate() + days);
  return formatDateYmd(date);
}

/** 解析当前窗口起始日期 */
export function resolveTemperatureSheetWindowStartDate(
  response: GetNurseStationTemperatureSheetDataResponse,
): string {
  const fromPagination = (response.pagination?.dateFrom ?? '').trim();
  if (fromPagination) {
    return fromPagination;
  }
  const firstRowDate = (response.gridRows?.[0]?.recordDate ?? '').trim();
  if (firstRowDate) {
    return firstRowDate;
  }
  return formatDateYmd(new Date());
}

/** 构建窗口内日×6 槽骨架行 */
export function buildTemperatureSheetSkeletonGridRows(
  startDateYmd: string,
  dayCount: number,
): TemperatureSheetGridRowDto[] {
  const rows: TemperatureSheetGridRowDto[] = [];
  for (let dayIndex = 0; dayIndex < dayCount; dayIndex += 1) {
    const recordDate = addDaysToYmd(startDateYmd, dayIndex);
    rows.push({
      recordDate,
      slotCells: TEMPERATURE_SHEET_TIME_SLOTS.map((timeSlot) => ({ timeSlot })),
    });
  }
  return rows;
}

/** 将接口网格合并到固定窗口骨架，保证始终展示完整天数 */
export function mergeTemperatureSheetGridRowsWithSkeleton(
  apiRows: TemperatureSheetGridRowDto[],
  startDateYmd: string,
  dayCount: number,
): TemperatureSheetGridRowDto[] {
  const skeletonRows = buildTemperatureSheetSkeletonGridRows(startDateYmd, dayCount);
  const apiRowMap = new Map<string, TemperatureSheetGridRowDto>();
  for (const row of apiRows) {
    const recordDate = (row.recordDate ?? '').trim();
    if (!recordDate) {
      continue;
    }
    apiRowMap.set(recordDate, row);
  }

  return skeletonRows.map((skeletonRow) => {
    const recordDate = (skeletonRow.recordDate ?? '').trim();
    const apiRow = apiRowMap.get(recordDate);
    if (!apiRow) {
      return skeletonRow;
    }

    const slotMap = new Map<string, TemperatureSheetGridSlotCellDto>();
    for (const cell of apiRow.slotCells ?? []) {
      const timeSlot = (cell.timeSlot ?? '').trim();
      if (!timeSlot) {
        continue;
      }
      slotMap.set(timeSlot, cell);
    }

    return {
      recordDate,
      slotCells: (skeletonRow.slotCells ?? []).map((skeletonCell) => {
        const timeSlot = (skeletonCell.timeSlot ?? '').trim();
        return slotMap.get(timeSlot) ?? skeletonCell;
      }),
    };
  });
}

/** 计算两个 YYYY-MM-DD 之间的自然日差 */
function diffDaysBetweenYmd(fromYmd: string, toYmd: string): number {
  const fromParts = fromYmd.split('-').map((part) => Number(part));
  const toParts = toYmd.split('-').map((part) => Number(part));
  if (fromParts.length !== 3 || toParts.length !== 3) {
    return 0;
  }
  const fromDate = new Date(fromParts[0], fromParts[1] - 1, fromParts[2]);
  const toDate = new Date(toParts[0], toParts[1] - 1, toParts[2]);
  return Math.round((toDate.getTime() - fromDate.getTime()) / 86_400_000);
}

/** 从派生 meta 中解析入科日 */
function resolveAdmissionDateYmd(
  derivedMeta: TemperatureSheetDerivedMetaDto | null | undefined,
  windowStartDate: string,
): string {
  for (const [date, dayNo] of Object.entries(derivedMeta?.hospitalizationDaysByDate ?? {})) {
    if (dayNo === 1) {
      return date;
    }
  }
  return windowStartDate;
}

/** 为窗口骨架日补齐住院/周次派生 meta */
export function extendDerivedMetaForWindow(
  derivedMeta: TemperatureSheetDerivedMetaDto | null | undefined,
  startDateYmd: string,
  dayCount: number,
): {
  hospitalizationDaysByDate: Record<string, number>;
  postoperativeDaysByDate: Record<string, number>;
  weekNoByDate: Record<string, number>;
} {
  const admissionDate = resolveAdmissionDateYmd(derivedMeta, startDateYmd);
  const hospitalizationDaysByDate = {
    ...(derivedMeta?.hospitalizationDaysByDate ?? {}),
  };
  const postoperativeDaysByDate = {
    ...(derivedMeta?.postoperativeDaysByDate ?? {}),
  };
  const weekNoByDate = {
    ...(derivedMeta?.weekNoByDate ?? {}),
  };

  // 为窗口内每一天补齐住院日与周次
  for (let dayIndex = 0; dayIndex < dayCount; dayIndex += 1) {
    const fullDate = addDaysToYmd(startDateYmd, dayIndex);
    if (hospitalizationDaysByDate[fullDate] == null) {
      const hospitalDay = diffDaysBetweenYmd(admissionDate, fullDate) + 1;
      hospitalizationDaysByDate[fullDate] = Math.max(1, hospitalDay);
    }
    if (weekNoByDate[fullDate] == null) {
      const hospitalDay = hospitalizationDaysByDate[fullDate] ?? 1;
      weekNoByDate[fullDate] = Math.floor((hospitalDay - 1) / 7) + 1;
    }
  }

  return {
    hospitalizationDaysByDate,
    postoperativeDaysByDate,
    weekNoByDate,
  };
}

/** 字符串体征值转图表数字 */
function parseChartNumber(value?: null | string): number | undefined {
  const text = (value ?? '').trim();
  if (!text) {
    return undefined;
  }
  const num = Number(text);
  return Number.isFinite(num) ? num : undefined;
}

/** 由网格行构建曲线展示点序列 */
export function buildChartPointsFromGridRows(
  gridRows: TemperatureSheetGridRowDto[],
): TemperatureSheetChartPoint[] {
  const points: TemperatureSheetChartPoint[] = [];

  // 按日×6 槽展开为图表横轴点
  for (const gridRow of gridRows) {
    const fullDate = (gridRow.recordDate ?? '').trim();
    if (!fullDate) {
      continue;
    }
    const displayDate = formatTemperatureSheetDisplayDate(fullDate);
    const slotMap = new Map(
      (gridRow.slotCells ?? []).map((slot) => [(slot.timeSlot ?? '').trim(), slot]),
    );
    // 固定 02→22 时次顺序，与表头/底部呼吸列对齐
    for (const timeSlot of TEMPERATURE_SHEET_TIME_SLOTS) {
      const slot = slotMap.get(timeSlot);
      points.push({
        fullDate,
        date: displayDate,
        timeSlot,
        temp: parseChartNumber(
          readVitalValueFromDetailItems(slot?.detailItems, VITAL_ITEM_CODE.TEMP),
        ),
        pulse: parseChartNumber(
          readVitalValueFromDetailItems(slot?.detailItems, VITAL_ITEM_CODE.PULSE),
        ),
        heartRate: parseChartNumber(
          readVitalValueFromDetailItems(slot?.detailItems, VITAL_ITEM_CODE.HR),
        ),
        respiratory:
          readVitalValueFromDetailItems(slot?.detailItems, VITAL_ITEM_CODE.RESP) || undefined,
      });
    }
  }

  return points;
}

/** 由接口响应构建图表点，优先网格，兜底曲线点 */
export function buildChartPointsFromSheetData(
  response: GetNurseStationTemperatureSheetDataResponse,
  expectedDayCount?: number,
): TemperatureSheetChartPoint[] {
  let gridRows = response.gridRows ?? [];
  if (expectedDayCount != null && expectedDayCount > 0) {
    const startDate = resolveTemperatureSheetWindowStartDate(response);
    gridRows = mergeTemperatureSheetGridRowsWithSkeleton(gridRows, startDate, expectedDayCount);
  }
  if (gridRows.length > 0) {
    return buildChartPointsFromGridRows(gridRows);
  }

  const points: TemperatureSheetChartPoint[] = [];
  for (const item of response.temperaturePoints ?? []) {
    const recordedAt = (item.recordedAt ?? '').trim();
    if (!recordedAt) {
      continue;
    }
    const [fullDate, timeText] = recordedAt.split(' ');
    const hour = (timeText ?? '').slice(0, 2) as TemperatureSheetTimeSlot;
    if (!fullDate || !TEMPERATURE_SHEET_TIME_SLOTS.includes(hour)) {
      continue;
    }
    points.push({
      fullDate,
      date: formatTemperatureSheetDisplayDate(fullDate),
      timeSlot: hour,
      temp: parseChartNumber(item.temperature),
    });
  }
  if (expectedDayCount != null && expectedDayCount > 0) {
    const startDate = resolveTemperatureSheetWindowStartDate(response);
    const skeletonRows = buildTemperatureSheetSkeletonGridRows(startDate, expectedDayCount);
    return buildChartPointsFromGridRows(skeletonRows).map((skeletonPoint) => {
      const matched = points.find(
        (point) =>
          point.fullDate === skeletonPoint.fullDate && point.timeSlot === skeletonPoint.timeSlot,
      );
      return matched ?? skeletonPoint;
    });
  }
  return points;
}

/** 合并接口网格与窗口骨架，供图表与底部网格共用 */
export function resolveMergedTemperatureSheetGridRows(
  response: GetNurseStationTemperatureSheetDataResponse,
  expectedDayCount?: number,
): TemperatureSheetGridRowDto[] {
  let gridRows = response.gridRows ?? [];
  if (expectedDayCount != null && expectedDayCount > 0) {
    const startDate = resolveTemperatureSheetWindowStartDate(response);
    gridRows = mergeTemperatureSheetGridRowsWithSkeleton(gridRows, startDate, expectedDayCount);
  }
  return gridRows;
}

/** 格式化底部血压展示 */
function formatFooterBloodPressureDisplay(
  systolic?: null | string,
  diastolic?: null | string,
): string {
  const sbp = (systolic ?? '').trim();
  const dbp = (diastolic ?? '').trim();
  if (sbp && dbp) {
    return `${sbp}/${dbp}`;
  }
  return sbp || dbp;
}

/** 判断该时次是否已填写血压（收缩压或舒张压任一有值） */
export function isBloodPressureSlotFilled(row: TemperatureSheetFillRowDraft): boolean {
  return Boolean(row.systolic.trim() || row.diastolic.trim());
}

/** 当日已填血压时次达 2 个后，其余空时次禁止再录入 */
export function isBloodPressureInputDisabled(
  row: TemperatureSheetFillRowDraft,
  rows: TemperatureSheetFillRowDraft[],
): boolean {
  if (isBloodPressureSlotFilled(row)) {
    return false;
  }
  const filledCount = rows.filter(isBloodPressureSlotFilled).length;
  return filledCount >= 2;
}

/** 由网格行构建底部按日汇总数据 */
export function buildFooterDayCellsFromGridRows(
  gridRows: TemperatureSheetGridRowDto[],
): TemperatureSheetFooterDayCell[] {
  const gridRowByDate = new Map<string, TemperatureSheetGridRowDto>();
  for (const gridRow of gridRows) {
    const recordDate = (gridRow.recordDate ?? '').trim();
    if (recordDate) {
      gridRowByDate.set(recordDate, gridRow);
    }
  }

  return gridRows.map((gridRow) => {
    const recordDate = (gridRow.recordDate ?? '').trim();
    const slotMap = new Map(
      (gridRow.slotCells ?? []).map((slot) => [(slot.timeSlot ?? '').trim(), slot]),
    );
    const detailItems22 = resolveSlot22DetailItems(gridRow.slotCells);
    // 前一日统计项：展示列 S 读录入日 S+1 的 22:00 槽
    const previousDayEntryRow = recordDate
      ? gridRowByDate.get(addDaysToYmd(recordDate, 1))
      : undefined;
    const previousDayDetailItems22 = resolveSlot22DetailItems(previousDayEntryRow?.slotCells);
    const bpSlots = TEMPERATURE_SHEET_TIME_SLOTS.map((timeSlot) => slotMap.get(timeSlot)).filter(
      (slot) => {
        const sbp = readVitalValueFromDetailItems(slot?.detailItems, VITAL_ITEM_CODE.SBP);
        const dbp = readVitalValueFromDetailItems(slot?.detailItems, VITAL_ITEM_CODE.DBP);
        return Boolean(sbp || dbp);
      },
    );
    const firstBpSlot = bpSlots[0];
    const secondBpSlot = bpSlots[1];
    return {
      bloodPressureFirst: formatFooterBloodPressureDisplay(
        readVitalValueFromDetailItems(firstBpSlot?.detailItems, VITAL_ITEM_CODE.SBP),
        readVitalValueFromDetailItems(firstBpSlot?.detailItems, VITAL_ITEM_CODE.DBP),
      ),
      bloodPressureSecond: formatFooterBloodPressureDisplay(
        readVitalValueFromDetailItems(secondBpSlot?.detailItems, VITAL_ITEM_CODE.SBP),
        readVitalValueFromDetailItems(secondBpSlot?.detailItems, VITAL_ITEM_CODE.DBP),
      ),
      stoolCount: readVitalValueFromDetailItems(previousDayDetailItems22, VITAL_ITEM_CODE.STOOL),
      urineCount: readVitalValueFromDetailItems(
        previousDayDetailItems22,
        VITAL_ITEM_CODE.URINE_FREQ,
      ),
      intake: readVitalValueFromDetailItems(previousDayDetailItems22, VITAL_ITEM_CODE.INTAKE),
      other: readVitalValueFromDetailItems(previousDayDetailItems22, VITAL_ITEM_CODE.OTHER),
      urine: readVitalValueFromDetailItems(previousDayDetailItems22, VITAL_ITEM_CODE.URINE_VOL),
      weight: readVitalValueFromDetailItems(detailItems22, VITAL_ITEM_CODE.WEIGHT),
      height: readVitalValueFromDetailItems(detailItems22, VITAL_ITEM_CODE.HEIGHT),
      skinTestPositive: readVitalValueFromDetailItems(
        detailItems22,
        VITAL_ITEM_CODE.SKIN_TEST_POSITIVE,
      ),
    };
  });
}

/** 创建空白录入行 */
export function createEmptyFillRows(): TemperatureSheetFillRowDraft[] {
  return TEMPERATURE_SHEET_TIME_SLOTS.map((timeSlot) => ({
    timeSlot,
    temp: '',
    pulse: '',
    respiratory: '',
    heartRate: '',
    systolic: '',
    diastolic: '',
    specialEvents: [],
    specialEventTimesByCode: {},
    dischargeEnabled: false,
    physicalCoolingEnabled: false,
    medicationCoolingEnabled: false,
    tempNotRisingEnabled: false,
    cooledTemperature: '',
    medicationCooledTemperature: '',
  }));
}

/** 创建空白补充记录 */
export function createEmptyExtraRecord(): TemperatureSheetExtraRecordDraft {
  return {
    weight: '',
    height: '',
    intake: '',
    urine: '',
    stoolCount: '',
    abdominalCircumference: '',
    urineCount: '',
    skinTestPositive: '',
    other: '',
  };
}

/** 将接口回显槽位写入录入草稿 */
export function applyFillSlotsToDraft(fillSlots: TemperatureSheetFillSlotDto[]): {
  extraRecord: TemperatureSheetExtraRecordDraft;
  rows: TemperatureSheetFillRowDraft[];
} {
  const rows = createEmptyFillRows();
  const extraRecord = createEmptyExtraRecord();
  const rowMap = new Map(rows.map((row) => [row.timeSlot, row]));

  // 按槽位回显主录入与 22 点补充项
  for (const slot of fillSlots) {
    const timeSlot = (slot.timeSlot ?? '').trim() as TemperatureSheetTimeSlot;
    const target = rowMap.get(timeSlot);
    if (!target) {
      continue;
    }
    target.vitalSignId = slot.vitalSignId?.trim() || undefined;
    applyDetailItemsToFillRow(target, extraRecord, slot.detailItems);
  }

  return { rows, extraRecord };
}

/** 判断单槽是否有待保存明细 */
export function hasFillRowSaveContent(
  row: TemperatureSheetFillRowDraft,
  extraRecord: TemperatureSheetExtraRecordDraft,
): boolean {
  return hasDetailItemsContent(buildDetailItemsFromFillRow(row, extraRecord));
}

/** 录入行转单槽保存请求 */
export function mapFillRowToSaveRequest(
  row: TemperatureSheetFillRowDraft,
  extraRecord: TemperatureSheetExtraRecordDraft,
  recordDate: string,
  encounterId: string,
  patientId: string,
): SaveNurseStationTemperatureSheetAtRecordedTimeRequest | null {
  const detailItems = buildDetailItemsFromFillRow(row, extraRecord);
  if (!hasDetailItemsContent(detailItems)) {
    return null;
  }

  return {
    encounterId,
    patientId,
    recordDate,
    timeSlot: row.timeSlot,
    vitalSignId: row.vitalSignId,
    detailItems,
  };
}

/** 收集当日全部待保存槽位请求 */
export function collectTemperatureSheetSaveRequests(
  rows: TemperatureSheetFillRowDraft[],
  extraRecord: TemperatureSheetExtraRecordDraft,
  recordDate: string,
  encounterId: string,
  patientId: string,
): SaveNurseStationTemperatureSheetAtRecordedTimeRequest[] {
  return rows
    .map((row) => mapFillRowToSaveRequest(row, extraRecord, recordDate, encounterId, patientId))
    .filter((item): item is SaveNurseStationTemperatureSheetAtRecordedTimeRequest => item !== null);
}

/** 批量保存后回写各槽 vitalSignId */
export function applySavedVitalSignIdsToFillRows(
  rows: TemperatureSheetFillRowDraft[],
  savedResults: Array<{ timeSlot: string; vitalSignId?: string }>,
): TemperatureSheetFillRowDraft[] {
  const savedMap = new Map(
    savedResults.map((item) => [item.timeSlot, item.vitalSignId?.trim() || undefined]),
  );
  return rows.map((row) => {
    const vitalSignId = savedMap.get(row.timeSlot);
    if (!vitalSignId) {
      return row;
    }
    return {
      ...row,
      vitalSignId,
    };
  });
}

/** 解析默认窗口范围 */
export function resolveTemperatureSheetWindowRange(
  code?: null | string,
): TemperatureSheetWindowRange {
  if (code === '3d' || code === '14d' || code === '7d') {
    return code;
  }
  return '7d';
}

/** 时次索引 */
export function resolveTimeSlotIndex(timeSlot: string): number {
  return TEMPERATURE_SHEET_TIME_SLOTS.findIndex((slot) => slot === timeSlot);
}
