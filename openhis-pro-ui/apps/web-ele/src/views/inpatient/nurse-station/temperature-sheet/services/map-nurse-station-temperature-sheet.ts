import type {
  GetNurseStationTemperatureSheetDataResponse,
  SaveNurseStationTemperatureSheetAtRecordedTimeRequest,
  TemperatureSheetDetailItemDto,
  TemperatureSheetFillSlotDto,
  TemperatureSheetGridRowDto,
  TemperatureSheetGridSlotCellDto,
  TemperatureSheetDerivedMetaDto,
  TemperatureSheetTemperaturePointDto,
  TemperatureSheetTimeSlot,
  TemperatureSheetWindowRange,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.types';

import {
  applyDetailItemsToFillRow,
  buildDetailItemsFromFillRow,
  hasDetailItemsContent,
  readVitalValueFromDetailItems,
  resolveDayHeightWeightDetailItems,
  resolveSlot22DetailItems,
  TERM_SPECIAL_EVENT_CODE,
  VITAL_ITEM_CODE,
} from './temperature-sheet-detail-items';
import {
  parseDateTimeYmdHm,
  resolveAdmissionDateYmd as resolveAdmissionDateYmdFromAt,
  resolveTemperatureSheetChartTimeSlotFromHm,
} from './temperature-sheet-time-format';

export const TEMPERATURE_SHEET_TIME_SLOTS: TemperatureSheetTimeSlot[] = [
  '02',
  '06',
  '10',
  '14',
  '18',
  '22',
];

/** 单人主录入行 Vue key 序号（避免改时间后 key 冲突） */
let temperatureSheetFillRowUidSeq = 0;

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
  /** 实际测量时间 HH:mm（保存提交值） */
  recordTime: string;
  respiratory: string;
  /** 行唯一键（可改时间时保持稳定） */
  rowUid: string;
  specialEvents: string[];
  specialEventTimesByCode: Record<string, string>;
  systolic: string;
  temp: string;
  tempNotRisingEnabled: boolean;
  /** 图表三测槽（由 recordTime 推导，补充记录挂 22 槽） */
  timeSlot: TemperatureSheetTimeSlot;
  vitalSignId?: string;
}

export interface TemperatureSheetExtraRecordDraft {
  abdominalCircumference: string;
  height: string;
  intake: string;
  other: string;
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

/** 录入日是否为入院日（入院日不填前一日入出量/大便等统计项） */
export function isTemperatureSheetAdmissionRecordDate(
  recordDateYmd: string,
  admissionAt?: string,
): boolean {
  const recordDate = (recordDateYmd ?? '').trim();
  if (!recordDate) {
    return false;
  }
  const admissionDate = resolveAdmissionDateYmdFromAt(admissionAt);
  return Boolean(admissionDate && recordDate === admissionDate);
}

/** 清空前一日统计补充项（入院日录入用） */
export function clearTemperatureSheetPreviousDaySupplementFields(
  extraRecord: TemperatureSheetExtraRecordDraft,
): TemperatureSheetExtraRecordDraft {
  const next: TemperatureSheetExtraRecordDraft = { ...extraRecord };
  for (const field of TEMPERATURE_SHEET_PREVIOUS_DAY_SUPPLEMENT_FIELDS) {
    next[field] = '';
  }
  return next;
}

/** 底部补充指标行（除呼吸、血压外），顺序与纸质体温单一致 */
export const TEMPERATURE_SHEET_FOOTER_SUPPLEMENT_ROWS = [
  { field: 'stoolCount', label: '大便次数' },
  { field: 'urineCount', label: '小便次数' },
  { field: 'intake', label: '总入量(ml)' },
  { field: 'other', label: '其他(ml)' },
  { field: 'urine', label: '尿量(ml)' },
  { field: 'weight', label: '体重(kg)' },
  { field: 'height', label: '身高(cm)' },
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

/** 曲线描点依赖的体征项：同槽身份优先取贡献了这些项的主表 */
const TEMPERATURE_SHEET_CURVE_ITEM_CODES = new Set<string>([
  VITAL_ITEM_CODE.TEMP,
  VITAL_ITEM_CODE.PULSE,
  VITAL_ITEM_CODE.HR,
]);

/** 同槽合并过程中的时分与主表身份（曲线来源优先于最晚一条） */
interface TemperatureSheetSlotMergeState {
  curveRecordTime: string;
  curveVitalSignId?: string;
  detailItems: TemperatureSheetDetailItemDto[];
  latestRecordTime: string;
  latestVitalSignId?: string;
}

/**
 * 明细项是否参与同槽合并。
 * 有测量值或备注即有效；标记类特殊事件 itemValue 可空，存在即有效。
 */
function temperatureSheetDetailItemHasMergeValue(item: TemperatureSheetDetailItemDto): boolean {
  const itemCode = (item.itemCode ?? '').trim();
  if (!itemCode) {
    return false;
  }
  if ((item.itemValue ?? '').trim()) {
    return true;
  }
  if ((item.remark ?? '').trim()) {
    return true;
  }
  return (item.termCode ?? '').trim() === TERM_SPECIAL_EVENT_CODE;
}

/** 本批明细是否写了体温、脉搏或心率（用于格子 recordTime / vitalSignId） */
function temperatureSheetItemsContributeCurve(
  items: TemperatureSheetDetailItemDto[] | null | undefined,
): boolean {
  for (const item of items ?? []) {
    const itemCode = (item.itemCode ?? '').trim();
    if (!TEMPERATURE_SHEET_CURVE_ITEM_CODES.has(itemCode)) {
      continue;
    }
    if ((item.itemValue ?? '').trim()) {
      return true;
    }
  }
  return false;
}

/**
 * 按 item_code 合并两批明细：同码以后写且有值的为准，不同码并集。
 * 调用方须按记录时分升序传入（earlier 早于 later）。
 */
function mergeTemperatureSheetDetailItemsByCode(
  earlier: TemperatureSheetDetailItemDto[] | null | undefined,
  later: TemperatureSheetDetailItemDto[] | null | undefined,
): TemperatureSheetDetailItemDto[] {
  const merged = new Map<string, TemperatureSheetDetailItemDto>();
  for (const item of earlier ?? []) {
    const itemCode = (item.itemCode ?? '').trim();
    if (!temperatureSheetDetailItemHasMergeValue(item)) {
      continue;
    }
    merged.set(itemCode, item);
  }
  for (const item of later ?? []) {
    const itemCode = (item.itemCode ?? '').trim();
    if (!temperatureSheetDetailItemHasMergeValue(item)) {
      continue;
    }
    merged.set(itemCode, item);
  }
  return [...merged.values()];
}

/** 把一条已落格记录并入槽状态：同码后写覆盖，曲线身份优先于最晚时分 */
function absorbTemperatureSheetSlotSource(
  state: TemperatureSheetSlotMergeState,
  detailItems: TemperatureSheetDetailItemDto[] | null | undefined,
  recordTime: string,
  vitalSignId?: string,
): void {
  const items = detailItems ?? [];
  const incomingIsLater =
    !state.latestRecordTime || recordTime.localeCompare(state.latestRecordTime) >= 0;
  if (recordTime && incomingIsLater) {
    state.detailItems = mergeTemperatureSheetDetailItemsByCode(state.detailItems, items);
    state.latestRecordTime = recordTime;
    state.latestVitalSignId = vitalSignId;
    if (temperatureSheetItemsContributeCurve(items)) {
      state.curveRecordTime = recordTime;
      state.curveVitalSignId = vitalSignId;
    }
    return;
  }
  // 无时分或更早的记录只补尚未出现的项，避免冲掉已按时间合并的值
  state.detailItems = mergeTemperatureSheetDetailItemsByCode(items, state.detailItems);
  if (!state.curveRecordTime && temperatureSheetItemsContributeCurve(items) && recordTime) {
    state.curveRecordTime = recordTime;
    state.curveVitalSignId = vitalSignId;
  }
}

/** 同槽展示身份：优先体温/脉搏/心率来源，否则最晚一条 */
function resolveTemperatureSheetMergedSlotIdentity(state: TemperatureSheetSlotMergeState): {
  recordTime?: string;
  vitalSignId?: string;
} {
  if (state.curveRecordTime || state.curveVitalSignId) {
    return {
      recordTime: state.curveRecordTime || undefined,
      vitalSignId: state.curveVitalSignId,
    };
  }
  return {
    recordTime: state.latestRecordTime || undefined,
    vitalSignId: state.latestVitalSignId,
  };
}

/** 六个图表槽的空合并状态 */
function createTemperatureSheetSlotMergeStates(): Map<
  TemperatureSheetTimeSlot,
  TemperatureSheetSlotMergeState
> {
  return new Map(
    TEMPERATURE_SHEET_TIME_SLOTS.map((timeSlot) => [
      timeSlot,
      {
        detailItems: [],
        latestRecordTime: '',
        curveRecordTime: '',
      },
    ]),
  );
}

/**
 * 按实际测量时分就近重排日×6 槽（09:40 → 10 时栏）。
 * 格子无 recordTime 时保持接口原槽，兼容未升级后端。
 * 同槽多格按 item_code 合并，禁止整格只留较晚时分。
 */
export function rebucketTemperatureSheetGridRowsByNearestSlot(
  gridRows: TemperatureSheetGridRowDto[],
): TemperatureSheetGridRowDto[] {
  return gridRows.map((gridRow) => rebucketTemperatureSheetGridRowByNearestSlot(gridRow));
}

/** 将单日格子按 recordTime 就近落到三测时次，同槽按体征项合并 */
function rebucketTemperatureSheetGridRowByNearestSlot(
  gridRow: TemperatureSheetGridRowDto,
): TemperatureSheetGridRowDto {
  const sourceCells = gridRow.slotCells ?? [];
  const hasRecordTime = sourceCells.some((cell) => (cell.recordTime ?? '').trim().length > 0);
  if (!hasRecordTime) {
    return gridRow;
  }

  const states = createTemperatureSheetSlotMergeStates();
  const orderedCells = [...sourceCells].sort((left, right) =>
    (left.recordTime ?? '').trim().localeCompare((right.recordTime ?? '').trim()),
  );

  // 有真实时分的格子按就近时次落格；同槽按 item_code 合并
  for (const cell of orderedCells) {
    const recordTime = (cell.recordTime ?? '').trim();
    const chartSlot = recordTime
      ? resolveTemperatureSheetChartTimeSlotFromHm(recordTime)
      : ((cell.timeSlot ?? '').trim() as TemperatureSheetTimeSlot);
    if (!TEMPERATURE_SHEET_TIME_SLOTS.includes(chartSlot)) {
      continue;
    }
    const state = states.get(chartSlot);
    if (!state) {
      continue;
    }
    absorbTemperatureSheetSlotSource(state, cell.detailItems, recordTime, cell.vitalSignId);
  }

  const slotCells: TemperatureSheetGridSlotCellDto[] = TEMPERATURE_SHEET_TIME_SLOTS.map(
    (timeSlot) => {
      const state = states.get(timeSlot);
      const identity = state
        ? resolveTemperatureSheetMergedSlotIdentity(state)
        : { recordTime: undefined, vitalSignId: undefined };
      return {
        timeSlot,
        detailItems: state?.detailItems ?? [],
        recordTime: identity.recordTime,
        vitalSignId: identity.vitalSignId,
      };
    },
  );

  return {
    recordDate: gridRow.recordDate,
    slotCells,
  };
}

/**
 * 用曲线点 recordedAt 补格子测量时分（vitalSignId 对齐）。
 * 旧后端格子没有 recordTime 时，仍能按真实时分就近描点。
 */
function attachRecordTimeFromTemperaturePoints(
  gridRows: TemperatureSheetGridRowDto[],
  temperaturePoints: TemperatureSheetTemperaturePointDto[] | null | undefined,
): TemperatureSheetGridRowDto[] {
  const hmByVitalSignId = new Map<string, string>();
  for (const point of temperaturePoints ?? []) {
    const vitalSignId = String(point.vitalSignId ?? '').trim();
    const parsed = parseDateTimeYmdHm(point.recordedAt ?? undefined);
    if (!vitalSignId || !parsed) {
      continue;
    }
    hmByVitalSignId.set(vitalSignId, parsed.hm);
  }
  if (hmByVitalSignId.size === 0) {
    return gridRows;
  }

  return gridRows.map((gridRow) => ({
    ...gridRow,
    slotCells: (gridRow.slotCells ?? []).map((cell) => {
      if ((cell.recordTime ?? '').trim()) {
        return cell;
      }
      const vitalSignId = String(cell.vitalSignId ?? '').trim();
      const recordTime = hmByVitalSignId.get(vitalSignId);
      if (!recordTime) {
        return cell;
      }
      return { ...cell, recordTime };
    }),
  }));
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

/** 由网格行构建曲线展示点序列（描点按测量时分就近落格） */
export function buildChartPointsFromGridRows(
  gridRows: TemperatureSheetGridRowDto[],
): TemperatureSheetChartPoint[] {
  const points: TemperatureSheetChartPoint[] = [];
  const chartRows = rebucketTemperatureSheetGridRowsByNearestSlot(gridRows);

  // 按日×6 槽展开为图表横轴点
  for (const gridRow of chartRows) {
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
        // 曲线脉搏：优先 PULSE，缺省回退 HR（与录入合并口径一致）
        pulse: parseChartNumber(
          readVitalValueFromDetailItems(slot?.detailItems, VITAL_ITEM_CODE.PULSE) ||
            readVitalValueFromDetailItems(slot?.detailItems, VITAL_ITEM_CODE.HR),
        ),
        heartRate: parseChartNumber(
          readVitalValueFromDetailItems(slot?.detailItems, VITAL_ITEM_CODE.HR) ||
            readVitalValueFromDetailItems(slot?.detailItems, VITAL_ITEM_CODE.PULSE),
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
    gridRows = attachRecordTimeFromTemperaturePoints(gridRows, response.temperaturePoints);
    return buildChartPointsFromGridRows(gridRows);
  }

  const points: TemperatureSheetChartPoint[] = [];
  for (const item of response.temperaturePoints ?? []) {
    const recordedAt = (item.recordedAt ?? '').trim();
    if (!recordedAt) {
      continue;
    }
    const [fullDate, timeText] = recordedAt.split(' ');
    const timeHm = (timeText ?? '').slice(0, 5);
    if (!fullDate || !/^\d{2}:\d{2}$/.test(timeHm)) {
      continue;
    }
    const timeSlot = resolveTemperatureSheetChartTimeSlotFromHm(timeHm);
    points.push({
      fullDate,
      date: formatTemperatureSheetDisplayDate(fullDate),
      timeSlot,
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
  gridRows = attachRecordTimeFromTemperaturePoints(gridRows, response.temperaturePoints);
  return rebucketTemperatureSheetGridRowsByNearestSlot(gridRows);
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

/**
 * 将指定日 fillSlots 压成单日网格行（仅供底部「前一日统计」跨窗 peek，不参与表头列）。
 * 同图表槽按 item_code 合并，与网格读侧一致；不改录入抽屉逐条回显。
 */
export function buildTemperatureSheetPeekGridRowFromFillSlots(
  recordDate: string,
  fillSlots: TemperatureSheetFillSlotDto[] | null | undefined,
): TemperatureSheetGridRowDto {
  const states = createTemperatureSheetSlotMergeStates();
  const orderedSlots = [...(fillSlots ?? [])].sort((left, right) =>
    normalizeTemperatureSheetRecordTime(left.timeSlot).localeCompare(
      normalizeTemperatureSheetRecordTime(right.timeSlot),
    ),
  );

  // 按实际时分就近归入图表槽；同槽按 item_code 合并，较晚有值项覆盖同码
  for (const slot of orderedSlots) {
    const recordTime = normalizeTemperatureSheetRecordTime(slot.timeSlot);
    const chartSlot = resolveTemperatureSheetChartTimeSlotFromHm(recordTime);
    const state = states.get(chartSlot);
    if (!state) {
      continue;
    }
    absorbTemperatureSheetSlotSource(
      state,
      slot.detailItems,
      recordTime,
      slot.vitalSignId?.trim() || undefined,
    );
  }

  const slotCells: TemperatureSheetGridSlotCellDto[] = TEMPERATURE_SHEET_TIME_SLOTS.map(
    (timeSlot) => {
      const state = states.get(timeSlot);
      const identity = state
        ? resolveTemperatureSheetMergedSlotIdentity(state)
        : { recordTime: undefined, vitalSignId: undefined };
      return {
        timeSlot,
        detailItems: state?.detailItems ?? [],
        vitalSignId: identity.vitalSignId,
      };
    },
  );

  return {
    recordDate: recordDate.trim(),
    slotCells,
  };
}

/** 由网格行构建底部按日汇总数据；peekRows 用于窗口末日的 S+1（跨页录入日）补数 */
export function buildFooterDayCellsFromGridRows(
  gridRows: TemperatureSheetGridRowDto[],
  peekRows?: TemperatureSheetGridRowDto[] | null,
): TemperatureSheetFooterDayCell[] {
  const gridRowByDate = new Map<string, TemperatureSheetGridRowDto>();
  // 展示列 + 跨窗 peek（仅参与 S+1 查找，不生成额外底部列）
  for (const gridRow of [...gridRows, ...(peekRows ?? [])]) {
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
    // 身高/体重：优先 22:00，兼容入院体征写在入科时点的数据
    const heightWeightItems = resolveDayHeightWeightDetailItems(gridRow.slotCells);
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
      weight: readVitalValueFromDetailItems(heightWeightItems, VITAL_ITEM_CODE.WEIGHT),
      height: readVitalValueFromDetailItems(heightWeightItems, VITAL_ITEM_CODE.HEIGHT),
    };
  });
}

/** 生成录入行唯一键 */
function createTemperatureSheetFillRowUid(): string {
  temperatureSheetFillRowUidSeq += 1;
  return `ts-fill-${temperatureSheetFillRowUidSeq}`;
}

/** 创建单条空白录入行（默认当前时分，并推导图表槽） */
export function createEmptyFillRow(
  timeSlot?: TemperatureSheetTimeSlot,
): TemperatureSheetFillRowDraft {
  const now = new Date();
  const recordTime = timeSlot
    ? formatTemperatureSheetTimeSlotHm(timeSlot)
    : `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  const resolvedSlot = timeSlot ?? resolveTemperatureSheetChartTimeSlotFromHm(recordTime);
  return {
    rowUid: createTemperatureSheetFillRowUid(),
    recordTime,
    timeSlot: resolvedSlot,
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
  };
}

/** 创建固定 6 槽空白录入行（批量/图表骨架） */
export function createEmptyFillRows(): TemperatureSheetFillRowDraft[] {
  return TEMPERATURE_SHEET_TIME_SLOTS.map((timeSlot) => createEmptyFillRow(timeSlot));
}

/** 单人主录入默认：仅 1 条待填写 */
export function createDefaultEditableFillRows(): TemperatureSheetFillRowDraft[] {
  return [createEmptyFillRow()];
}

/** 时次转 HH:mm（录入时间展示） */
export function formatTemperatureSheetTimeSlotHm(timeSlot: TemperatureSheetTimeSlot): string {
  return `${timeSlot}:00`;
}

/** 将接口 timeSlot（槽 code 或 HH:mm）规范为 HH:mm */
export function normalizeTemperatureSheetRecordTime(raw: null | string | undefined): string {
  const text = (raw ?? '').trim();
  if (!text) {
    return dayjsNowHm();
  }
  if (TEMPERATURE_SHEET_TIME_SLOTS.includes(text as TemperatureSheetTimeSlot)) {
    return formatTemperatureSheetTimeSlotHm(text as TemperatureSheetTimeSlot);
  }
  const hmMatch = /^(\d{1,2}):(\d{2})(?::\d{2})?$/.exec(text);
  if (hmMatch) {
    const hour = Number(hmMatch[1]);
    const minute = Number(hmMatch[2]);
    if (
      Number.isFinite(hour) &&
      Number.isFinite(minute) &&
      hour >= 0 &&
      hour <= 23 &&
      minute >= 0 &&
      minute <= 59
    ) {
      return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
    }
  }
  return dayjsNowHm();
}

/** 当前时分 HH:mm */
function dayjsNowHm(): string {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
}

/** 按 recordTime 同步图表三测槽（30 分界就近落格） */
export function syncFillRowChartTimeSlot(row: TemperatureSheetFillRowDraft): void {
  row.timeSlot = resolveTemperatureSheetChartTimeSlotFromHm(row.recordTime);
}

/** 列出尚未占用的三测时次 */
export function listUnusedTemperatureSheetTimeSlots(
  rows: TemperatureSheetFillRowDraft[],
): TemperatureSheetTimeSlot[] {
  const used = new Set(rows.map((row) => row.timeSlot));
  return TEMPERATURE_SHEET_TIME_SLOTS.filter((slot) => !used.has(slot));
}

/** 判断录入行是否已有体征/特殊事件内容（不含补充记录） */
export function hasEditableFillRowVitalContent(row: TemperatureSheetFillRowDraft): boolean {
  if (row.vitalSignId?.trim()) {
    return true;
  }
  return hasFillRowSaveContent(row, createEmptyExtraRecord());
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
    other: '',
  };
}

/** 将接口回显槽位写入录入草稿（固定 6 槽，供批量回显） */
export function applyFillSlotsToDraft(fillSlots: TemperatureSheetFillSlotDto[]): {
  extraRecord: TemperatureSheetExtraRecordDraft;
  rows: TemperatureSheetFillRowDraft[];
} {
  const rows = createEmptyFillRows();
  const extraRecord = createEmptyExtraRecord();
  const rowMap = new Map(rows.map((row) => [row.timeSlot, row]));

  // 按实际时分就近归到图表槽回显；同槽多条取较晚的时分覆盖
  for (const slot of fillSlots) {
    const recordTime = normalizeTemperatureSheetRecordTime(slot.timeSlot);
    const chartSlot = resolveTemperatureSheetChartTimeSlotFromHm(recordTime);
    const target = rowMap.get(chartSlot);
    if (!target) {
      continue;
    }
    target.recordTime = recordTime;
    target.timeSlot = chartSlot;
    target.vitalSignId = slot.vitalSignId?.trim() || undefined;
    applyDetailItemsToFillRow(target, extraRecord, slot.detailItems);
  }

  return { rows, extraRecord };
}

/** 将接口回显转为单人可编辑行：按实际时分逐条；无数据时默认 1 空行 */
export function applyFillSlotsToEditableDraft(fillSlots: TemperatureSheetFillSlotDto[]): {
  extraRecord: TemperatureSheetExtraRecordDraft;
  rows: TemperatureSheetFillRowDraft[];
} {
  const extraRecord = createEmptyExtraRecord();
  const rows: TemperatureSheetFillRowDraft[] = [];

  // 每条回显对应一行，保留用户自选时分
  for (const slot of fillSlots) {
    const recordTime = normalizeTemperatureSheetRecordTime(slot.timeSlot);
    const row = createEmptyFillRow();
    row.recordTime = recordTime;
    syncFillRowChartTimeSlot(row);
    row.vitalSignId = slot.vitalSignId?.trim() || undefined;
    applyDetailItemsToFillRow(row, extraRecord, slot.detailItems);
    if (hasEditableFillRowVitalContent(row)) {
      rows.push(row);
    }
  }

  rows.sort((a, b) => a.recordTime.localeCompare(b.recordTime));
  if (rows.length === 0) {
    return {
      rows: createDefaultEditableFillRows(),
      extraRecord,
    };
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
    timeSlot: row.recordTime,
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
  const requests = rows
    .map((row) => mapFillRowToSaveRequest(row, extraRecord, recordDate, encounterId, patientId))
    .filter((item): item is SaveNurseStationTemperatureSheetAtRecordedTimeRequest => item !== null);

  // 补充记录仅挂图表 22 槽：有补充内容但请求中尚无 22 槽时补提 22:00
  const hasExtraContent = Object.values(extraRecord).some((value) => value.trim().length > 0);
  const hasChartSlot22Request = requests.some((request) => {
    const chartSlot = resolveTemperatureSheetChartTimeSlotFromHm(
      normalizeTemperatureSheetRecordTime(request.timeSlot),
    );
    return chartSlot === '22';
  });
  if (hasExtraContent && !hasChartSlot22Request) {
    const slot22Row = rows.find((row) => row.timeSlot === '22') ?? createEmptyFillRow('22');
    const extraRequest = mapFillRowToSaveRequest(
      slot22Row,
      extraRecord,
      recordDate,
      encounterId,
      patientId,
    );
    if (extraRequest) {
      requests.push(extraRequest);
    }
  }

  return requests;
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
    const vitalSignId = savedMap.get(row.recordTime) ?? savedMap.get(row.timeSlot) ?? undefined;
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
