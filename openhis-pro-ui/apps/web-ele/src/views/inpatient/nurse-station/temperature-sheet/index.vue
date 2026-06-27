<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type {
  TemperatureSheetChartPoint,
  TemperatureSheetExtraRecordDraft,
  TemperatureSheetFillRowDraft,
} from './services/map-nurse-station-temperature-sheet';
import type {
  TemperatureSheetChartTextMarker,
  TemperatureSheetPhysicalCoolingChartPoint,
  TemperatureSheetSpecialEventOption,
  TemperatureSheetSpecialEventStore,
} from './services/temperature-sheet-special-events';

import type { TemperatureSheetGridRowDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.types';
import type { OptionDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station.types';

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import dayjs from 'dayjs';
import { ElMessage } from 'element-plus';

import {
  httpGetNurseStationTemperatureSheetData,
  httpLoadNurseStationTemperatureSheetInit,
  httpSaveNurseStationTemperatureSheetAtRecordedTime,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.http';
import { useFormKeyboardNavigation } from '#/hooks/use-form-keyboard-navigation';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  applyFillSlotsToDraft,
  applySavedVitalSignIdsToFillRows,
  buildChartPointsFromGridRows,
  buildFooterDayCellsFromGridRows,
  collectTemperatureSheetSaveRequests,
  createEmptyExtraRecord,
  createEmptyFillRows,
  extendDerivedMetaForWindow,
  formatTemperatureSheetDisplayDate,
  isBloodPressureInputDisabled,
  resolveMergedTemperatureSheetGridRows,
  resolveTemperatureSheetWindowStartDate,
  shiftTemperatureSheetRecordDateYmd,
  TEMPERATURE_SHEET_FOOTER_SUPPLEMENT_ROWS,
} from './services/map-nurse-station-temperature-sheet';
import {
  isSpecialEventTimeRequired,
  SPECIAL_EVENT_ITEM_CODE,
} from './services/temperature-sheet-detail-items';
import {
  buildChartTextMarkers,
  buildPhysicalCoolingChartPoints,
  buildSpecialEventLabelMap,
  buildSpecialEventSelectOptions,
  buildSpecialEventStoreFromGridRows,
  createEmptySpecialEventStore,
  extractSlotFlagsFromFillRows,
  handleSpecialEventPickerChange,
  mergeSlotFlagsForDate,
  resolveSpecialEventPickerOptions,
  resolveSpecialEventPickerSlots,
  resolveSpecialEventPickerValue,
  syncSpecialEventShortcutFields,
  syncSpecialEventTimesOnSelectionChange,
  TEMPERATURE_SHEET_PHYSICAL_COOLING_DELAY_MINUTES,
  validateFillRowsSpecialEvents,
  validateSpecialEventStore,
} from './services/temperature-sheet-special-events';
import { formatTemperatureSheetTimeSlotRange } from './services/temperature-sheet-time-format';
import {
  PAPER_SHEET_DESIGN_WIDTH,
  usePaperSheetViewportScale,
} from '../composables/use-paper-sheet-viewport-scale';
import { usePaperSheetFullscreen } from '../composables/use-paper-sheet-fullscreen';

defineOptions({
  name: 'InpatientNurseStationTemperatureSheetModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

interface Props {
  patients?: NurseStationModulePatient[];
}

interface TooltipParamLike {
  data?: [number, number] | null;
  dataIndex?: number;
  marker?: string;
  seriesName?: string;
}

const A4_WIDTH = PAPER_SHEET_DESIGN_WIDTH;
const A4_HEIGHT = 1123;
/** 屏幕常态固定 1.25 倍；全屏时按视口等比放大 */
const SHEET_NORMAL_SCALE = 1.25;
/** 右侧翻页/操作栏占位（宽 90px + 左间距 16px） */
const SHEET_ACTION_PANEL_WIDTH = 106;
const WINDOW_DAY_COUNT = 7;
const WINDOW_RANGE = '7d' as const;

const dataRequest = useRequest(httpGetNurseStationTemperatureSheetData, { manual: true });
const initRequest = useRequest(httpLoadNurseStationTemperatureSheetInit, { manual: true });
const currentPage = ref(0);
const chartPoints = ref<TemperatureSheetChartPoint[]>([]);
const sheetGridRows = ref<TemperatureSheetGridRowDto[]>([]);
const derivedMeta = ref<{
  hospitalizationDaysByDate: Record<string, number>;
  postoperativeDaysByDate: Record<string, number>;
  weekNoByDate: Record<string, number>;
}>({
  hospitalizationDaysByDate: {},
  postoperativeDaysByDate: {},
  weekNoByDate: {},
});
const totalPages = ref(1);
const fillDrawerVisible = ref(false);
const recordDate = ref('');
const activeFillSlotIndex = ref(0);
const savingAll = ref(false);
const fillFormAreaRef = ref<HTMLElement | null>(null);
const tempRecordRows = ref<TemperatureSheetFillRowDraft[]>(createEmptyFillRows());
const extraRecord = ref<TemperatureSheetExtraRecordDraft>(createEmptyExtraRecord());
const specialEventStore = ref<TemperatureSheetSpecialEventStore>(createEmptySpecialEventStore());
const specialEventOptions = ref<TemperatureSheetSpecialEventOption[]>([]);
const specialEventSelectOptions = computed(() =>
  buildSpecialEventSelectOptions(specialEventOptions.value),
);
const specialEventLabelMap = computed(() => buildSpecialEventLabelMap(specialEventOptions.value));

const tempMin = 34;
const tempMax = 42;
const tempMinorStep = 0.2;
const majorTempTicks = [34, 35, 36, 37, 38, 39, 40, 41, 42] as const;
const tempGridLineCount = Math.round((tempMax - tempMin) / tempMinorStep) + 1;
const tempGridLineColors = Array.from({ length: tempGridLineCount }, (_item, index) =>
  index !== 0 && index !== tempGridLineCount - 1 && index % 5 === 0 ? '#1d4ed8' : '#9ca3af',
);
const chartColorTemp = '#1d4ed8';
const chartColorPulse = '#d9001b';
/** ECharts path 叉号无法描边，改用 SVG image 保证体温蓝叉可见 */
const chartTempCrossSymbol = `image://data:image/svg+xml,${encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="-6 -6 12 12"><path d="M-4.5,-4.5 L4.5,4.5 M4.5,-4.5 L-4.5,4.5" stroke="#1d4ed8" stroke-width="1.6" fill="none" stroke-linecap="round"/></svg>',
)}`;
/** ECharts emptyCircle 描边不稳定，改用 SVG 保证降温后红空心圆可见 */
const chartCooledEmptyCircleSymbol = `image://data:image/svg+xml,${encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="-5 -5 10 10"><circle cx="0" cy="0" r="4" stroke="#d9001b" stroke-width="1.8" fill="none"/></svg>',
)}`;
const chartRef = ref<EchartsUIType>();
const plotAreaRef = ref<HTMLElement>();
const sheetViewportRef = ref<HTMLElement>();
const { isFullscreen, toggleFullscreen } = usePaperSheetFullscreen();
const {
  scale: sheetScale,
  shellWidth: sheetShellWidth,
  shellHeight: sheetShellHeight,
  recomputeScale,
  remeasureContent,
} = usePaperSheetViewportScale(sheetViewportRef, {
  designWidth: A4_WIDTH,
  designHeight: A4_HEIGHT,
  enabled: isFullscreen,
  fallbackScale: SHEET_NORMAL_SCALE,
  fitMode: 'width',
  reservedWidth: SHEET_ACTION_PANEL_WIDTH,
  padding: 0,
});
const { renderEcharts } = useEcharts(chartRef);
let plotResizeObserver: null | ResizeObserver = null;

/** 体温单仅支持左侧单选患者 */
const canOperateSheet = computed(() => props.patients.length === 1);

/** 当前选中就诊上下文 */
const selectedPatientContext = computed(() => {
  if (!canOperateSheet.value) {
    return null;
  }
  const patient = props.patients[0];
  if (!patient) {
    return null;
  }
  const encounterId = preserveApiIdForRequest(patient.id);
  const patientId = preserveApiIdForRequest(patient.patientId ?? patient.id);
  if (!encounterId || !patientId) {
    return null;
  }
  return { encounterId, patientId, patient };
});

const selectedPatient = computed(() => selectedPatientContext.value?.patient);

/** 补充记录中前一日统计项的归属日期（录入日 - 1） */
const supplementStatisticalDateLabel = computed(() => {
  const entryDate = recordDate.value.trim();
  if (!entryDate) {
    return '';
  }
  return formatTemperatureSheetDisplayDate(shiftTemperatureSheetRecordDateYmd(entryDate, -1));
});

/** 体温单抬头日期展示（取 YYYY-MM-DD） */
function formatSheetPatientDate(value?: string): string {
  const text = (value ?? '').trim();
  if (!text) {
    return '';
  }
  const parsed = dayjs(text);
  if (parsed.isValid()) {
    return parsed.format('YYYY-MM-DD');
  }
  return text.length >= 10 ? text.slice(0, 10) : text;
}

const visiblePoints = computed(() => chartPoints.value);

const footerDayCells = computed(() => buildFooterDayCellsFromGridRows(sheetGridRows.value));

/** 按业务日期索引底部日汇总，与表头日期列对齐 */
const footerDayCellByDate = computed(() => {
  const map = new Map<string, (typeof footerDayCells.value)[number]>();
  sheetGridRows.value.forEach((row, index) => {
    const fullDate = (row.recordDate ?? '').trim();
    const cell = footerDayCells.value[index];
    if (fullDate && cell) {
      map.set(fullDate, cell);
    }
  });
  return map;
});

/** 翻到上一页并重新加载数据 */
function goToPrevPage() {
  if (currentPage.value > 0) {
    currentPage.value -= 1;
    void loadTemperatureSheetData();
  }
}

/** 翻到下一页并重新加载数据 */
function goToNextPage() {
  if (currentPage.value < totalPages.value - 1) {
    currentPage.value += 1;
    void loadTemperatureSheetData();
  }
}

const { handleFormKeydown: handleFillFormKeydown } = useFormKeyboardNavigation(fillFormAreaRef, {
  keys: ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Enter'],
  resolveFocusIndex: ({ currentIndex, direction, event, focusable }) => {
    const currentTarget = focusable[currentIndex];
    const currentCell = currentTarget?.closest<HTMLElement>('.nav-cell');
    if ((event.key === 'ArrowUp' || event.key === 'ArrowDown') && currentCell) {
      const { navCol, navGroup, navRow } = currentCell.dataset;
      if (navGroup === 'vital' && navCol !== undefined && navRow !== undefined) {
        const nextRow = Number(navRow) + (event.key === 'ArrowUp' ? -1 : 1);
        const nextCell = fillFormAreaRef.value?.querySelector<HTMLElement>(
          `.nav-cell[data-nav-group="${navGroup}"][data-nav-col="${navCol}"][data-nav-row="${nextRow}"]`,
        );
        if (nextCell) {
          const nextFocusableIndex = focusable.findIndex(
            (el) => el === nextCell || nextCell.contains(el),
          );
          if (nextFocusableIndex !== -1) return nextFocusableIndex;
        }
      }
    }
    return currentIndex + direction;
  },
});

/** 加载体温单展示数据 */
async function loadTemperatureSheetData(focusRecordDate?: string) {
  const context = selectedPatientContext.value;
  if (!context) {
    chartPoints.value = [];
    sheetGridRows.value = [];
    totalPages.value = 1;
    return;
  }

  const response = await dataRequest.run({
    encounterId: context.encounterId,
    patientId: context.patientId,
    windowRange: WINDOW_RANGE,
    pageIndex: currentPage.value,
    focusRecordDate,
  });

  const windowDayCount = WINDOW_DAY_COUNT;
  const windowStartDate = resolveTemperatureSheetWindowStartDate(response);
  sheetGridRows.value = resolveMergedTemperatureSheetGridRows(response, windowDayCount);
  chartPoints.value = buildChartPointsFromGridRows(sheetGridRows.value);
  totalPages.value = Math.max(1, response.pagination?.totalPages ?? 1);
  derivedMeta.value = extendDerivedMetaForWindow(
    response.derivedMeta,
    windowStartDate,
    windowDayCount,
  );
  specialEventStore.value = buildSpecialEventStoreFromGridRows(sheetGridRows.value);

  if (focusRecordDate && response.fillSlots) {
    const draft = applyFillSlotsToDraft(response.fillSlots);
    draft.rows.forEach((row) => {
      syncSpecialEventTimesOnSelectionChange(row);
      syncSpecialEventShortcutFields(row);
    });
    tempRecordRows.value = draft.rows;
    extraRecord.value = draft.extraRecord;
  }
}

/** 加载录入抽屉指定日期的回显数据 */
async function loadFillDrawerSlots() {
  if (!recordDate.value) {
    tempRecordRows.value = createEmptyFillRows();
    extraRecord.value = createEmptyExtraRecord();
    return;
  }
  await loadTemperatureSheetData(recordDate.value);
}

/** 打开录入抽屉并加载当日回显 */
async function openFillDrawer() {
  if (!selectedPatientContext.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (!recordDate.value) {
    recordDate.value = dayjs().format('YYYY-MM-DD');
  }
  activeFillSlotIndex.value = 0;
  fillDrawerVisible.value = true;
  await loadFillDrawerSlots();
  await nextTick();
  const firstInput = fillFormAreaRef.value?.querySelector<HTMLInputElement>(
    '.fill-row .el-input input',
  );
  firstInput?.focus();
}

/** 录入日期加减天数（工具栏快捷切换，不写入日期面板） */
function shiftRecordDateByDays(offset: number): void {
  const base = recordDate.value?.trim() ? dayjs(recordDate.value) : dayjs();
  if (!base.isValid()) {
    return;
  }
  recordDate.value = base.add(offset, 'day').format('YYYY-MM-DD');
}

/** 复制上一时次录入值 */
function copyPrevRow(index: number) {
  if (index <= 0) return;
  const prev = tempRecordRows.value[index - 1];
  const current = tempRecordRows.value[index];
  if (!prev || !current) return;
  current.temp = prev.temp;
  current.pulse = prev.pulse;
  current.respiratory = prev.respiratory;
  current.heartRate = prev.heartRate;
}

/** 保存前校验特殊事件（合并当日编辑到窗口存储） */
function validateSpecialEventsBeforeSave(): string | null {
  if (!recordDate.value) {
    return null;
  }
  const specialError = validateFillRowsSpecialEvents(
    tempRecordRows.value,
    specialEventLabelMap.value,
  );
  if (specialError) {
    return specialError;
  }
  const slotFlags = extractSlotFlagsFromFillRows(tempRecordRows.value, recordDate.value);
  const mergedStore = mergeSlotFlagsForDate(specialEventStore.value, recordDate.value, slotFlags);
  return validateSpecialEventStore(mergedStore);
}

/** 底部血压半日列 span */
function resolveFooterBpHalfSpan(daySpan: number): number {
  return Math.max(1, Math.floor(daySpan / 2));
}

/** 呼吸值是否显示在单元格上方（奇数时次低、偶数时次高，每日从高位重新开始） */
function isRespiratoryHighSlot(index: number): boolean {
  return index % 2 === 0;
}

/** 将首行体温同步到全部时次 */
function fillSameTemperature(value?: string) {
  const text = (value ?? '').trim();
  if (!text) return;
  tempRecordRows.value.forEach((row) => {
    row.temp = text;
  });
}

/** 将首行生命体征同步到其余时次 */
function fillSameFromFirstRow() {
  const firstRow = tempRecordRows.value[0];
  if (!firstRow) return;
  tempRecordRows.value.forEach((row, index) => {
    if (index === 0) return;
    row.temp = firstRow.temp;
    row.pulse = firstRow.pulse;
    row.respiratory = firstRow.respiratory;
    row.heartRate = firstRow.heartRate;
  });
}

/** 批量保存当日全部已填写时次 */
async function handleSaveAllSlots() {
  const context = selectedPatientContext.value;
  if (!context || !recordDate.value) {
    ElMessage.warning('请先选择患者与录入日期');
    return;
  }

  const saveRequests = collectTemperatureSheetSaveRequests(
    tempRecordRows.value,
    extraRecord.value,
    recordDate.value,
    context.encounterId,
    context.patientId,
  );
  if (saveRequests.length === 0) {
    ElMessage.warning('请至少填写一项生命体征或特殊事件');
    return;
  }

  const specialEventError = validateSpecialEventsBeforeSave();
  if (specialEventError) {
    ElMessage.warning(specialEventError);
    return;
  }

  savingAll.value = true;
  try {
    const savedResults: Array<{ timeSlot: string; vitalSignId?: string }> = [];
    // 后端单槽保存，按有时次内容逐条提交（含特殊事件）
    for (const request of saveRequests) {
      const response = await httpSaveNurseStationTemperatureSheetAtRecordedTime(request);
      savedResults.push({
        timeSlot: request.timeSlot,
        vitalSignId: response.vitalSignId ?? undefined,
      });
    }

    tempRecordRows.value = applySavedVitalSignIdsToFillRows(tempRecordRows.value, savedResults);
    await loadTemperatureSheetData(recordDate.value);
    ElMessage.success(`已保存 ${saveRequests.length} 条体温单记录`);
  } finally {
    savingAll.value = false;
  }
}

const chartColumns = computed(() => visiblePoints.value.length);
const slotGridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${chartColumns.value}, minmax(0, 1fr))`,
}));
const dayDividerColumns = computed(() =>
  visiblePoints.value
    .map((_item, index) => (isDayDividerColumn(index) ? index : null))
    .filter((item): item is number => item !== null && item < visiblePoints.value.length - 1),
);
const chartTextMarkers = computed(() =>
  buildChartTextMarkers(
    visiblePoints.value,
    specialEventStore.value,
    specialEventLabelMap.value,
    selectedPatient.value?.admissionAt,
  ),
);
const physicalCoolingChartPoints = computed(() =>
  buildPhysicalCoolingChartPoints(visiblePoints.value, specialEventStore.value),
);

/** 计算竖排事件标记定位 */
function resolveChartTextMarkerStyle(marker: TemperatureSheetChartTextMarker) {
  const columns = Math.max(chartColumns.value, 1);
  return {
    left: `${((marker.slotIndex + 0.5) / columns) * 100}%`,
    top: `${4 + marker.stackOrder * 52}px`,
  };
}
const tempScaleDisplay = computed(() =>
  majorTempTicks.toReversed().filter((temp) => temp < 42 && temp > 34),
);
const pulseScaleDisplay = [160, 140, 120, 100, 80, 60, 40] as const;
const tempTickStyles = computed(() =>
  tempScaleDisplay.value.map((temp) => {
    const top = tempToTopPercent(temp);
    return { temp, style: { top: `${top ?? 0}%` } };
  }),
);
const pulseTickStyles = computed(() =>
  pulseScaleDisplay.map((pulse) => {
    const top = pulseToTopPercent(pulse);
    return { pulse, style: { top: `${top ?? 0}%` } };
  }),
);

const dayHeaders = computed(() => {
  const headers: Array<{ date: string; fullDate: string; span: number; startIndex: number }> = [];
  let currentDate = '';
  let currentFullDate = '';
  let startIndex = 0;
  let span = 0;
  visiblePoints.value.forEach((item, index) => {
    if (item.date !== currentDate) {
      if (currentDate)
        headers.push({ date: currentDate, fullDate: currentFullDate, span, startIndex });
      currentDate = item.date;
      currentFullDate = item.fullDate;
      startIndex = index;
      span = 1;
      return;
    }
    span += 1;
  });
  if (currentDate) headers.push({ date: currentDate, fullDate: currentFullDate, span, startIndex });
  return headers;
});

/** 解析某日住院天数展示文案 */
function resolveHospitalizationDayLabel(fullDate: string, fallbackIndex: number): string {
  const dayNo = derivedMeta.value.hospitalizationDaysByDate[fullDate];
  if (dayNo !== null) {
    return `第 ${dayNo} 日`;
  }
  return `第 ${fallbackIndex + 1} 日`;
}

function isDayDividerColumn(index: number): boolean {
  return (index + 1) % 6 === 0;
}

function isInnerDayDividerColumn(index: number): boolean {
  return isDayDividerColumn(index) && index < chartColumns.value - 1;
}

function tempToTopPercent(value?: number): null | number {
  if (value === undefined) return null;
  const clamped = Math.max(tempMin, Math.min(tempMax, value));
  return ((tempMax - clamped) / (tempMax - tempMin)) * 100;
}

function pulseToTopPercent(value?: number): null | number {
  if (value === undefined) return null;
  const normalized = tempMin + ((value - 20) / 160) * (tempMax - tempMin);
  return tempToTopPercent(normalized);
}

function resolveCooledTimeText(timeSlot: string): string {
  const baseHour = Number(timeSlot);
  const cooledHour = baseHour + Math.floor(TEMPERATURE_SHEET_PHYSICAL_COOLING_DELAY_MINUTES / 60);
  const cooledMin = TEMPERATURE_SHEET_PHYSICAL_COOLING_DELAY_MINUTES % 60;
  return `${String(cooledHour).padStart(2, '0')}:${String(cooledMin).padStart(2, '0')}`;
}

function buildTooltipText(params: TooltipParamLike | TooltipParamLike[]): string {
  if (!Array.isArray(params)) {
    if (params.seriesName === '物理降温') {
      const coolingPoint = physicalCoolingChartPoints.value[params.dataIndex ?? -1];
      if (!coolingPoint) {
        return '';
      }
      const timeText = resolveCooledTimeText(coolingPoint.timeSlot);
      return `${coolingPoint.timeSlot}:00 物理降温后 ${timeText}<br/>${params.marker ?? ''} 降温后体温：${coolingPoint.cooledTemp} ℃`;
    }
    const point = visiblePoints.value[params.dataIndex ?? -1];
    if (!point) return '';
    const coolingPoint = physicalCoolingChartPoints.value.find(
      (item) => item.slotIndex === params.dataIndex,
    );
    if (params.seriesName === '体温') {
      if (coolingPoint) {
        const timeText = resolveCooledTimeText(coolingPoint.timeSlot);
        return `${point.date} ${point.timeSlot}:00<br/>${params.marker ?? ''} 体温：${coolingPoint.originalTemp} ℃<br/>${timeText} 降温后：${coolingPoint.cooledTemp} ℃`;
      }
      return `${point.date} ${point.timeSlot}:00<br/>${params.marker ?? ''} 体温：${point.temp} ℃`;
    }
    if (params.seriesName === '脉搏') {
      return `${point.date} ${point.timeSlot}:00<br/>${params.marker ?? ''} 脉搏：${point.pulse} 次/分`;
    }
    return '';
  }

  const items = params;
  const firstDataIndex = items[0]?.dataIndex;
  if (firstDataIndex === undefined) return '';
  const point = visiblePoints.value[firstDataIndex];
  if (!point) return '';
  const header = `${point.date} ${point.timeSlot}:00`;
  const lines = items
    .map((item) => {
      if (item.seriesName === '体温' && point.temp !== undefined) {
        return `${item.marker ?? ''} 体温：${point.temp} ℃`;
      }
      if (item.seriesName === '脉搏' && point.pulse !== undefined) {
        return `${item.marker ?? ''} 脉搏：${point.pulse} 次/分`;
      }
      return '';
    })
    .filter(Boolean);
  if (lines.length === 0) return '';
  return [header, ...lines].join('<br/>');
}

function percentToTempValue(percent: number): number {
  return tempMax - ((tempMax - tempMin) * percent) / 100;
}

const chartOption = computed(() => {
  const dividerPositionSet = new Set(dayDividerColumns.value.map((index) => index + 1));
  const xGridLineColors = Array.from({ length: chartColumns.value + 1 }, (_item, index) =>
    dividerPositionSet.has(index) ? 'transparent' : '#9ca3af',
  );
  const coolingPointMap = new Map<number, TemperatureSheetPhysicalCoolingChartPoint>(
    physicalCoolingChartPoints.value.map((item) => [item.slotIndex, item]),
  );
  const tempSeriesData = visiblePoints.value.map((item, index) => {
    const coolingPoint = coolingPointMap.get(index);
    if (coolingPoint) {
      return [index + 0.5, Number(coolingPoint.originalTemp.toFixed(2))];
    }
    if (item.temp === undefined) {
      return null;
    }
    return [index + 0.5, Number(item.temp.toFixed(2))];
  });
  const coolingConnectorData = physicalCoolingChartPoints.value.flatMap((coolingPoint) => {
    const startX = coolingPoint.prevChartX ?? coolingPoint.originalChartX;
    const startY = coolingPoint.prevTemp ?? coolingPoint.originalTemp;
    return [
      [startX, Number(startY.toFixed(2))],
      [coolingPoint.cooledChartX, Number(coolingPoint.cooledTemp.toFixed(2))],
      null,
    ];
  });
  const cooledSeriesData = physicalCoolingChartPoints.value.flatMap((coolingPoint, index, list) => {
    const point: [number, number] = [
      coolingPoint.cooledChartX,
      Number(coolingPoint.cooledTemp.toFixed(2)),
    ];
    if (index < list.length - 1) {
      return [point, null];
    }
    return [point];
  });
  const pulseSeriesData = visiblePoints.value.map((item, index) => {
    const top = pulseToTopPercent(item.pulse);
    if (top === null) return null;
    return [index + 0.5, Number(percentToTempValue(top).toFixed(2))];
  });
  return {
    animation: false,
    grid: { bottom: 0, containLabel: false, left: 0, right: 0, top: 0 },
    xAxis: {
      axisLabel: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      interval: 1,
      max: chartColumns.value,
      min: 0,
      splitLine: { lineStyle: { color: xGridLineColors, width: 1 }, show: true },
      type: 'value' as const,
    },
    yAxis: {
      axisLabel: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      interval: tempMinorStep,
      max: tempMax,
      min: tempMin,
      splitLine: { lineStyle: { color: tempGridLineColors, width: 1 }, show: true },
      type: 'value' as const,
    },
    series: [
      {
        connectNulls: false,
        data: pulseSeriesData,
        itemStyle: { color: chartColorPulse },
        lineStyle: { color: chartColorPulse, width: 1 },
        name: '脉搏',
        showSymbol: true,
        symbol: 'circle',
        symbolSize: 7,
        type: 'line' as const,
        z: 2,
      },
      {
        connectNulls: false,
        data: tempSeriesData,
        itemStyle: { color: chartColorTemp },
        lineStyle: { color: chartColorTemp, width: 1 },
        name: '体温',
        showSymbol: true,
        symbol: chartTempCrossSymbol,
        symbolSize: 12,
        type: 'line' as const,
        z: 3,
      },
      {
        connectNulls: false,
        data: coolingConnectorData,
        lineStyle: { color: chartColorPulse, type: 'dashed' as const, width: 1.5 },
        name: '物理降温连线',
        showSymbol: false,
        silent: true,
        type: 'line' as const,
        z: 4,
      },
      {
        connectNulls: false,
        data: cooledSeriesData,
        itemStyle: { color: chartColorPulse },
        lineStyle: { opacity: 0, width: 0 },
        name: '物理降温',
        showSymbol: true,
        symbol: chartCooledEmptyCircleSymbol,
        symbolSize: 10,
        type: 'line' as const,
        z: 5,
      },
    ],
    tooltip: {
      formatter: (params: unknown) =>
        buildTooltipText(params as TooltipParamLike | TooltipParamLike[]),
      trigger: 'item' as const,
    },
  };
});

async function renderTemperatureChart() {
  await nextTick();
  await renderEcharts(chartOption.value);
}

/** 将 init 接口 OptionDto 转为特殊事件下拉项 */
function mapSpecialEventOptionsFromInit(options: OptionDto[] | null | undefined) {
  return (options ?? [])
    .map((option) => ({
      label: (option.label ?? '').trim(),
      value: String(option.value ?? '').trim(),
    }))
    .filter((option) => option.value.length > 0);
}

/** 加载体温单页初始化（特殊事件字典等） */
async function loadTemperatureSheetInit() {
  const initRes = await initRequest.run();
  specialEventOptions.value = mapSpecialEventOptionsFromInit(initRes.specialEventOptions);
}

onMounted(() => {
  plotResizeObserver = new ResizeObserver(() => {
    void renderTemperatureChart();
  });
  if (plotAreaRef.value) {
    plotResizeObserver.observe(plotAreaRef.value);
  }
  void loadTemperatureSheetInit();
  void loadTemperatureSheetData();
  void renderTemperatureChart();
});

onBeforeUnmount(() => {
  plotResizeObserver?.disconnect();
  plotResizeObserver = null;
});

watch(visiblePoints, () => {
  void renderTemperatureChart();
});

watch(
  specialEventStore,
  () => {
    void renderTemperatureChart();
  },
  { deep: true },
);

watch(selectedPatientContext, () => {
  currentPage.value = 0;
  specialEventStore.value = createEmptySpecialEventStore();
  void loadTemperatureSheetData();
});

watch(recordDate, () => {
  if (!fillDrawerVisible.value) {
    return;
  }
  void loadFillDrawerSlots();
});

watch(isFullscreen, () => {
  void nextTick(() => {
    remeasureContent();
    recomputeScale();
    requestAnimationFrame(() => {
      remeasureContent();
      recomputeScale();
      void renderTemperatureChart();
    });
  });
});
</script>

<template>
  <Teleport to="body" :disabled="!isFullscreen">
    <div class="temperature-sheet" :class="{ 'is-fullscreen': isFullscreen }">
      <ElEmpty v-if="!canOperateSheet" description="请先从左侧选择一位患者" />

      <div
        v-else
        ref="sheetViewportRef"
        v-loading="dataRequest.loading.value"
        class="sheet-viewport"
      >
        <div class="sheet-stage">
          <div
            class="sheet-paper-shell"
            :style="{
              height: `${sheetShellHeight}px`,
              width: `${sheetShellWidth}px`,
            }"
          >
            <section class="sheet-paper" :style="{ transform: `scale(${sheetScale})` }">
              <header class="sheet-title">体 温 单</header>

              <div class="patient-info-line">
                <span>姓名：{{ selectedPatient?.name || '' }}</span>
                <span>性别：{{ selectedPatient?.gender || '' }}</span>
                <span>年龄：{{ selectedPatient?.age || '' }}岁</span>
                <span>病室：{{ selectedPatient?.wardName || '' }}</span>
                <span>床号：{{ selectedPatient?.bedNo || '' }}</span>
                <span>病历号：{{ selectedPatient?.busNo || '' }}</span>
                <span>入院日期：{{ formatSheetPatientDate(selectedPatient?.admissionAt) }}</span>
              </div>

              <div class="sheet-head-grid">
                <div class="head-label">日期</div>
                <div class="head-content day-content" :style="slotGridStyle">
                  <span
                    v-for="header in dayHeaders"
                    :key="`date-${header.date}-${header.startIndex}`"
                    class="head-day-cell"
                    :style="{ gridColumn: `${header.startIndex + 1} / span ${header.span}` }"
                  >
                    {{ header.date }}日
                  </span>
                </div>
                <div class="head-label">住院日数</div>
                <div class="head-content day-content" :style="slotGridStyle">
                  <span
                    v-for="(header, index) in dayHeaders"
                    :key="`in-day-${header.date}-${header.startIndex}`"
                    class="head-day-cell"
                    :style="{ gridColumn: `${header.startIndex + 1} / span ${header.span}` }"
                  >
                    {{ resolveHospitalizationDayLabel(header.fullDate, index) }}
                  </span>
                </div>
                <div class="head-label">时间</div>
                <div class="head-content time-content" :style="slotGridStyle">
                  <span
                    v-for="(point, index) in visiblePoints"
                    :key="`head-time-${point.date}-${point.timeSlot}-${index}`"
                    class="head-time-cell"
                    :class="{ 'is-day-divider': isInnerDayDividerColumn(index) }"
                  >
                    {{ point.timeSlot }}
                  </span>
                </div>
              </div>
              <div class="chart-wrapper">
                <aside class="axis axis-left">
                  <div class="axis-title">脉搏(次/分)</div>
                  <div class="axis-values">
                    <span
                      v-for="item in pulseTickStyles"
                      :key="`pulse-${item.pulse}`"
                      class="axis-value axis-value-pulse"
                      :style="item.style"
                      >{{ item.pulse }}</span
                    >
                  </div>
                </aside>
                <aside class="axis axis-mid">
                  <div class="axis-title">体温(℃)</div>
                  <div class="axis-values">
                    <span
                      v-for="item in tempTickStyles"
                      :key="`temp-${item.temp}`"
                      class="axis-value axis-value-temp"
                      :style="item.style"
                      >{{ item.temp }}</span
                    >
                  </div>
                </aside>
                <main class="chart-main">
                  <div class="day-divider-overlay" :style="slotGridStyle">
                    <span
                      v-for="index in dayDividerColumns"
                      :key="`divider-${index}`"
                      class="day-divider-marker"
                      :style="{ gridColumn: `${index + 1}` }"
                    ></span>
                  </div>
                  <div
                    v-for="marker in chartTextMarkers"
                    :key="marker.key"
                    class="event-text-marker"
                    :style="resolveChartTextMarkerStyle(marker)"
                  >
                    {{ marker.displayText }}
                  </div>
                  <div ref="plotAreaRef" class="plot-area">
                    <EchartsUI ref="chartRef" class="plot-chart" height="100%" width="100%" />
                  </div>
                </main>
              </div>
              <div class="sheet-footer-grid">
                <div class="footer-label footer-resp-label">呼吸(次/分)</div>
                <div class="footer-content time-content resp-content" :style="slotGridStyle">
                  <span
                    v-for="(point, index) in visiblePoints"
                    :key="`footer-resp-${point.fullDate}-${point.timeSlot}-${index}`"
                    class="head-time-cell footer-resp-cell"
                    :class="{
                      'is-day-divider': isInnerDayDividerColumn(index),
                      'is-resp-high': isRespiratoryHighSlot(index),
                      'is-resp-low': !isRespiratoryHighSlot(index),
                    }"
                  >
                    {{ point.respiratory }}
                  </span>
                </div>
                <div class="footer-label">血压(mmHg)</div>
                <div class="footer-content day-content" :style="slotGridStyle">
                  <template v-for="header in dayHeaders" :key="`footer-bp-${header.fullDate}`">
                    <span
                      class="footer-day-cell footer-bp-cell"
                      :style="{
                        gridColumn: `${header.startIndex + 1} / span ${resolveFooterBpHalfSpan(header.span)}`,
                      }"
                    >
                      {{ footerDayCellByDate.get(header.fullDate)?.bloodPressureFirst }}
                    </span>
                    <span
                      class="footer-day-cell footer-bp-cell is-day-end"
                      :style="{
                        gridColumn: `${header.startIndex + 1 + resolveFooterBpHalfSpan(header.span)} / span ${resolveFooterBpHalfSpan(header.span)}`,
                      }"
                    >
                      {{ footerDayCellByDate.get(header.fullDate)?.bloodPressureSecond }}
                    </span>
                  </template>
                </div>
                <template
                  v-for="row in TEMPERATURE_SHEET_FOOTER_SUPPLEMENT_ROWS"
                  :key="`footer-supplement-${row.field}`"
                >
                  <div class="footer-label">{{ row.label }}</div>
                  <div class="footer-content day-content" :style="slotGridStyle">
                    <span
                      v-for="header in dayHeaders"
                      :key="`footer-${row.field}-${header.fullDate}`"
                      class="footer-day-cell"
                      :style="{ gridColumn: `${header.startIndex + 1} / span ${header.span}` }"
                    >
                      {{ footerDayCellByDate.get(header.fullDate)?.[row.field] }}
                    </span>
                  </div>
                </template>
              </div>
            </section>
          </div>
          <div class="sheet-action-panel">
            <ElButton :disabled="currentPage === 0" size="small" @click="goToPrevPage">
              上一页
            </ElButton>
            <span class="page-indicator">{{ currentPage + 1 }} / {{ totalPages }}</span>
            <ElButton :disabled="currentPage >= totalPages - 1" size="small" @click="goToNextPage">
              下一页
            </ElButton>
            <ElDivider />
            <ElButton size="small" @click="toggleFullscreen">
              {{ isFullscreen ? '退出全屏' : '全屏' }}
            </ElButton>
            <ElButton size="small" type="success" @click="openFillDrawer">填写体温单</ElButton>
          </div>
        </div>
      </div>
      <ElDrawer
        v-model="fillDrawerVisible"
        :append-to-body="true"
        :with-header="false"
        class="temp-fill-drawer"
        direction="btt"
        size="72%"
      >
        <div ref="fillFormAreaRef" class="fill-drawer" @keydown="handleFillFormKeydown">
          <div class="fill-toolbar">
            <div class="fill-toolbar-left">
              <div class="fill-record-date-nav">
                <ElDatePicker
                  v-model="recordDate"
                  format="YYYY-MM-DD"
                  placeholder="录入日期"
                  type="date"
                  value-format="YYYY-MM-DD"
                />
                <ElButton plain size="small" @click="shiftRecordDateByDays(-1)">上一天</ElButton>
                <ElButton plain size="small" @click="shiftRecordDateByDays(1)">下一天</ElButton>
              </div>
              <ElTag type="info">键盘快捷：←/→ 切换，↑/↓ 同列切换，Enter 下一项</ElTag>
            </div>
            <div class="fill-toolbar-right">
              <ElButton plain @click="fillSameTemperature(tempRecordRows[0]?.temp)">
                同步体温到全部时次
              </ElButton>
              <ElButton plain @click="fillSameFromFirstRow">同步首行生命体征</ElButton>
            </div>
          </div>

          <div class="fill-drawer-main">
            <section class="fill-card">
              <header class="fill-card-title">
                生命体征（主录入）
                <ElTag size="small" type="info"
                  >快捷项：出院/物理降温/药物降温/体温不升；图表竖排仅展示入院/转科/手术/分娩/出院/死亡；转科/死亡/分娩/出院须选时间；入院由患者入院时间自动带入</ElTag
                >
              </header>
              <div class="fill-grid-head">
                <span>时次</span>
                <span>体温(℃)</span>
                <span>脉搏</span>
                <span>呼吸</span>
                <span>心率</span>
                <span>收缩压</span>
                <span>舒张压</span>
                <span>特殊</span>
                <span>操作</span>
              </div>
              <div class="fill-grid-body">
                <div
                  v-for="(row, index) in tempRecordRows"
                  :key="row.timeSlot"
                  class="fill-row"
                  :class="{ 'is-active-slot': index === activeFillSlotIndex }"
                  @click="activeFillSlotIndex = index"
                >
                  <div class="time-slot-cell">
                    <span class="time-slot">{{ row.timeSlot }}:00</span>
                    <span class="time-slot-range">{{
                      formatTemperatureSheetTimeSlotRange(row.timeSlot)
                    }}</span>
                  </div>
                  <div
                    class="nav-cell"
                    :data-nav-col="0"
                    data-nav-group="vital"
                    :data-nav-row="index"
                  >
                    <ElInput v-model="row.temp" placeholder="体温">
                      <template #suffix>℃</template>
                    </ElInput>
                  </div>
                  <div
                    class="nav-cell"
                    :data-nav-col="1"
                    data-nav-group="vital"
                    :data-nav-row="index"
                  >
                    <ElInput v-model="row.pulse" placeholder="脉搏">
                      <template #suffix>次/分</template>
                    </ElInput>
                  </div>
                  <div
                    class="nav-cell"
                    :data-nav-col="2"
                    data-nav-group="vital"
                    :data-nav-row="index"
                  >
                    <ElInput v-model="row.respiratory" placeholder="呼吸">
                      <template #suffix>次/分</template>
                    </ElInput>
                  </div>
                  <div
                    class="nav-cell"
                    :data-nav-col="3"
                    data-nav-group="vital"
                    :data-nav-row="index"
                  >
                    <ElInput v-model="row.heartRate" placeholder="心率">
                      <template #suffix>bpm</template>
                    </ElInput>
                  </div>
                  <div
                    class="nav-cell"
                    :data-nav-col="4"
                    data-nav-group="vital"
                    :data-nav-row="index"
                  >
                    <ElInput
                      v-model="row.systolic"
                      :disabled="isBloodPressureInputDisabled(row, tempRecordRows)"
                      placeholder="收缩压"
                    >
                      <template #suffix>mmHg</template>
                    </ElInput>
                  </div>
                  <div
                    class="nav-cell"
                    :data-nav-col="5"
                    data-nav-group="vital"
                    :data-nav-row="index"
                  >
                    <ElInput
                      v-model="row.diastolic"
                      :disabled="isBloodPressureInputDisabled(row, tempRecordRows)"
                      placeholder="舒张压"
                    >
                      <template #suffix>mmHg</template>
                    </ElInput>
                  </div>
                  <div class="fill-row-special" @click.stop>
                    <div class="special-event-main-row">
                      <ElCheckbox
                        v-model="row.dischargeEnabled"
                        class="special-cooling-checkbox"
                        label="出院"
                        @change="syncSpecialEventShortcutFields(row)"
                      />
                      <ElTimePicker
                        v-if="row.dischargeEnabled"
                        v-model="row.specialEventTimesByCode[SPECIAL_EVENT_ITEM_CODE.DISCHARGE]"
                        class="special-event-time-picker"
                        format="HH:mm"
                        placeholder="时间"
                        style="width: 110px; --el-date-editor-width: 110px"
                        value-format="HH:mm"
                        :clearable="false"
                      />
                      <ElCheckbox
                        v-model="row.physicalCoolingEnabled"
                        class="special-cooling-checkbox"
                        label="物理降温"
                      />
                      <ElInput
                        v-if="row.physicalCoolingEnabled"
                        v-model="row.cooledTemperature"
                        class="cooled-temp-input"
                        placeholder="降温后"
                      >
                        <template #suffix>℃</template>
                      </ElInput>
                      <ElCheckbox
                        v-model="row.medicationCoolingEnabled"
                        class="special-cooling-checkbox"
                        label="药物降温"
                        @change="syncSpecialEventShortcutFields(row)"
                      />
                      <ElInput
                        v-if="row.medicationCoolingEnabled"
                        v-model="row.medicationCooledTemperature"
                        class="cooled-temp-input"
                        placeholder="降温后"
                      >
                        <template #suffix>℃</template>
                      </ElInput>
                      <ElCheckbox
                        v-model="row.tempNotRisingEnabled"
                        class="special-cooling-checkbox"
                        label="体温不升"
                      />
                      <template
                        v-for="slotIndex in resolveSpecialEventPickerSlots(row)"
                        :key="`${row.timeSlot}-special-${slotIndex}`"
                      >
                        <ElSelect
                          class="special-event-select"
                          clearable
                          :model-value="resolveSpecialEventPickerValue(row, slotIndex)"
                          placeholder="特殊事件"
                          @update:model-value="
                            (value: string | undefined) =>
                              handleSpecialEventPickerChange(row, slotIndex, value)
                          "
                        >
                          <ElOption
                            v-for="option in resolveSpecialEventPickerOptions(
                              row,
                              slotIndex,
                              specialEventSelectOptions,
                            )"
                            :key="`${row.timeSlot}-${slotIndex}-${option.value}`"
                            :label="option.label"
                            :value="option.value"
                          />
                        </ElSelect>
                        <ElTimePicker
                          v-if="
                            isSpecialEventTimeRequired(
                              resolveSpecialEventPickerValue(row, slotIndex),
                            )
                          "
                          v-model="
                            row.specialEventTimesByCode[
                              resolveSpecialEventPickerValue(row, slotIndex)
                            ]
                          "
                          class="special-event-time-picker"
                          format="HH:mm"
                          placeholder="时间"
                          style="width: 110px; --el-date-editor-width: 110px"
                          value-format="HH:mm"
                          :clearable="false"
                        />
                      </template>
                    </div>
                  </div>
                  <div class="fill-row-action">
                    <ElButton link type="primary" @click.stop="copyPrevRow(index)"
                      >复制上条</ElButton
                    >
                  </div>
                </div>
              </div>
            </section>

            <section class="fill-card fill-extra-card">
              <header class="fill-card-title">
                补充记录
                <ElTag size="small" type="info">
                  大便次数、小便次数、总入量、其他、尿量为前一日（{{
                    supplementStatisticalDateLabel || '—'
                  }}）统计，保存后显示在前一日列
                </ElTag>
              </header>
              <div class="extra-grid">
                <div class="extra-item">
                  <span class="extra-label">体重(kg)</span>
                  <ElInput v-model="extraRecord.weight" placeholder="请输入体重">
                    <template #suffix>kg</template>
                  </ElInput>
                </div>
                <div class="extra-item">
                  <span class="extra-label">小便次数(次)</span>
                  <ElInput v-model="extraRecord.urineCount" placeholder="请输入小便次数">
                    <template #suffix>次</template>
                  </ElInput>
                </div>
                <div class="extra-item">
                  <span class="extra-label">腹围(cm)</span>
                  <ElInput v-model="extraRecord.abdominalCircumference" placeholder="请输入腹围">
                    <template #suffix>cm</template>
                  </ElInput>
                </div>
                <div class="extra-item">
                  <span class="extra-label">大便次数(次)</span>
                  <ElInput v-model="extraRecord.stoolCount" placeholder="请输入大便次数">
                    <template #suffix>次</template>
                  </ElInput>
                </div>
                <div class="extra-item">
                  <span class="extra-label">身高(cm)</span>
                  <ElInput v-model="extraRecord.height" placeholder="请输入身高">
                    <template #suffix>cm</template>
                  </ElInput>
                </div>
                <div class="extra-item">
                  <span class="extra-label">入量(ml)</span>
                  <ElInput v-model="extraRecord.intake" placeholder="请输入入量">
                    <template #suffix>ml</template>
                  </ElInput>
                </div>
                <div class="extra-item">
                  <span class="extra-label">皮试阳性</span>
                  <ElInput v-model="extraRecord.skinTestPositive" placeholder="请输入皮试阳性">
                    <template #suffix>次</template>
                  </ElInput>
                </div>
                <div class="extra-item">
                  <span class="extra-label">尿量(ml)</span>
                  <ElInput v-model="extraRecord.urine" placeholder="请输入尿量">
                    <template #suffix>ml</template>
                  </ElInput>
                </div>
                <div class="extra-item">
                  <span class="extra-label">其他(ml)</span>
                  <ElInput v-model="extraRecord.other" placeholder="请输入其他">
                    <template #suffix>ml</template>
                  </ElInput>
                </div>
              </div>
            </section>
          </div>
        </div>
        <template #footer>
          <div class="fill-footer">
            <ElButton @click="fillDrawerVisible = false">取消</ElButton>
            <ElButton :loading="savingAll" type="primary" @click="handleSaveAllSlots"
              >保存</ElButton
            >
          </div>
        </template>
      </ElDrawer>
    </div>
  </Teleport>
</template>

<style scoped>
.temperature-sheet {
  --axis-column-width: 38px;
  --axis-border-width: 1px;
  --axis-total-width: calc(var(--axis-column-width) * 2);
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-height: 0;
  height: 100%;
  padding: 8px;
  background: #f4f5f7;
  overflow: auto;
}

.temperature-sheet.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  width: 100vw;
  height: 100vh;
  max-width: 100vw;
  max-height: 100vh;
  align-items: stretch;
  gap: 0;
  padding: 0;
  overflow: hidden;
  background: #eef0f3;
}

.sheet-viewport {
  display: block;
  flex: 1;
  width: 100%;
  min-height: 0;
  overflow: auto;
  padding: 8px 0;
}

.temperature-sheet.is-fullscreen .sheet-viewport {
  display: flex;
  flex: 1;
  align-items: flex-start;
  justify-content: center;
  padding: 24px;
  overflow: auto;
  background: #eef0f3;
}

.sheet-stage {
  display: flex;
  align-items: flex-start;
  width: fit-content;
  margin: 0 auto;
}
.sheet-paper-shell {
  flex: 0 0 auto;
  position: relative;
  overflow: hidden;
}
.sheet-paper {
  box-sizing: border-box;
  position: absolute;
  top: 0;
  left: 0;
  flex: 0 0 auto;
  width: 794px;
  height: 1123px;
  border: 1px solid #222;
  background: #fff;
  padding: 8px;
  display: flex;
  flex-direction: column;
  transform-origin: top left;
}
.sheet-action-panel {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  margin-left: 16px;
  padding-top: 8px;
  flex-shrink: 0;
  width: 90px;
}
.sheet-action-panel :deep(.el-button) {
  width: 100%;
  margin: 0;
}
.page-indicator {
  text-align: center;
  font-size: 12px;
  color: #6b7280;
}
.fill-drawer {
  display: flex;
  flex-direction: column;
  gap: 4px;
  height: 100%;
}
.fill-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 2px;
}
.fill-toolbar-left,
.fill-toolbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
}
.fill-record-date-nav {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.fill-drawer-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  flex: 1;
  overflow: auto;
}
.fill-card {
  display: flex;
  flex-direction: column;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}
.fill-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 32px;
  padding: 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  background: #f8fafc;
  border-bottom: 1px solid #e5e7eb;
}
.fill-grid-head,
.fill-row {
  display: grid;
  grid-template-columns: 88px repeat(6, 160px) minmax(0, 1fr) 72px;
  gap: 8px;
  align-items: start;
}
.fill-grid-head {
  padding: 6px 8px;
  background: #eff6ff;
  font-size: 12px;
  color: #334155;
  text-align: center;
  align-items: center;
}
.fill-grid-body {
  flex: 1;
  overflow: auto;
  padding: 4px 8px 8px;
}
.fill-row {
  padding: 8px 0;
  border-bottom: 1px dashed #e5e7eb;
}
.fill-row:last-child {
  border-bottom: none;
}
.fill-row.is-active-slot {
  margin: 0 -4px;
  padding: 8px 4px;
  background: #eff6ff;
  border-radius: 6px;
  outline: 1px solid #93c5fd;
}
.fill-row-action {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 4px;
}
.fill-row-special {
  min-width: 0;
}
.special-event-main-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  min-width: 0;
}
.time-slot-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.time-slot {
  font-weight: 700;
  font-size: 13px;
  color: #1f2937;
}
.time-slot-range {
  font-size: 11px;
  font-weight: 400;
  line-height: 1.2;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
.fill-row .nav-cell :deep(.el-input),
.extra-grid :deep(.el-input) {
  width: 100%;
}
.fill-row .nav-cell :deep(.el-input__suffix),
.extra-grid :deep(.el-input__suffix) {
  color: #6b7280;
  font-size: 11px;
}
.fill-extra-card {
  flex-shrink: 0;
}
.special-cooling-checkbox {
  flex-shrink: 0;
  margin-right: 0;
}
.special-cooling-checkbox :deep(.el-checkbox__label) {
  padding-left: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.cooled-temp-input {
  width: 110px;
  flex-shrink: 0;
}
.cooled-temp-input :deep(.el-input__suffix) {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.special-event-select {
  width: 110px;
  flex-shrink: 0;
}
.special-event-time-picker {
  flex-shrink: 0;
}
.extra-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 12px;
  padding: 8px;
}
.extra-item {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}
.extra-label {
  text-align: left;
  font-size: 12px;
  color: #4b5563;
}
.extra-item :deep(.el-input__wrapper) {
  min-height: 26px;
}
.fill-footer {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}

:global(.temp-fill-drawer .el-drawer__body) {
  padding: 10px 0 0;
}

:global(.temp-fill-drawer .el-drawer__header) {
  margin: 0;
  padding: 4px 10px;
  min-height: 34px;
}

:global(.temp-fill-drawer .el-drawer__footer) {
  padding: 4px 10px;
}
.sheet-title {
  text-align: center;
  font-size: 28px;
  letter-spacing: 12px;
  font-weight: 700;
  line-height: 1.2;
  margin-bottom: 6px;
  color: #020817;
}
.patient-info-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 14px;
  min-height: 24px;
  padding: 2px 4px;
  font-size: 12px;
  color: #020817;
}

.sheet-head-grid {
  display: grid;
  grid-template-columns: var(--axis-total-width) minmax(0, 1fr);
  border: 1px solid #222;
  border-bottom: 1px solid #222;
  font-size: 13px;
  color: #020817;
}
.head-label {
  box-sizing: border-box;
  min-height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #222;
  border-bottom: 1px solid #222;
}

.head-content {
  box-sizing: border-box;
  min-width: 0;
  width: 100%;
  border-bottom: 1px solid #222;
}

.day-content {
  display: grid;
}

.head-day-cell {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #222;
}

.time-content {
  display: grid;
}

.head-time-cell {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #9ca3af;
  color: #111827;
}

.head-time-cell.is-day-divider {
  border-right: 1px solid #b91c1c;
}
.chart-wrapper {
  display: flex;
  flex: 1;
  min-height: 0;
  border: 1px solid #222;
  border-top: 0;
}
.axis {
  box-sizing: border-box;
  width: var(--axis-column-width);
  border-right: var(--axis-border-width) solid #222;
  position: relative;
  display: flex;
  align-items: stretch;
  font-size: 10px;
  padding: 0;
}
.axis-left {
  color: #dc2626;
}
.axis-mid {
  color: #1d4ed8;
}
.axis-title {
  position: absolute;
  top: 4px;
  left: 50%;
  transform: translateX(-50%);
  writing-mode: vertical-rl;
  font-size: 10px;
  letter-spacing: 1px;
  line-height: 1;
  z-index: 1;
  pointer-events: none;
}

.axis-values {
  flex: 1;
  width: 100%;
  position: relative;
}

.axis-value {
  display: flex;
  align-items: center;
  justify-content: center;
}
.axis-value-temp {
  position: absolute;
  left: 0;
  width: 100%;
  transform: translateY(-50%);
}
.axis-value-pulse {
  position: absolute;
  left: 0;
  width: 100%;
  transform: translateY(-50%);
}
.chart-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
}
.event-text-marker {
  position: absolute;
  transform: translateX(-50%);
  z-index: 4;
  writing-mode: vertical-rl;
  text-orientation: upright;
  color: #b91c1c;
  font-size: 12px;
  line-height: 1.1;
  letter-spacing: 1px;
  pointer-events: none;
}
.day-divider-overlay {
  position: absolute;
  inset: 0;
  display: grid;
  pointer-events: none;
  z-index: 3;
}
.day-divider-marker {
  justify-self: end;
  width: 0;
  height: 100%;
  border-right: 1px solid #b91c1c;
}
.day-row,
.time-row {
  display: grid;
  min-height: 20px;
  border-bottom: 1px solid #222;
}
.day-cell,
.time-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #9ca3af;
  font-size: 10px;
}
.time-cell {
  color: #b91c1c;
}
.time-cell.is-day-divider {
  border-right: 2px solid #b91c1c;
}
.plot-area {
  position: relative;
  flex: 1;
  min-height: 0;
}
.plot-chart {
  width: 100%;
  height: 100%;
}
.sheet-footer-grid {
  display: grid;
  grid-template-columns: var(--axis-total-width) minmax(0, 1fr);
  border: 1px solid #222;
  border-top: 0;
  font-size: 10px;
  color: #111827;
}
.footer-label {
  box-sizing: border-box;
  min-height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #222;
  border-bottom: 1px solid #222;
  font-size: 12px;
}
.footer-content {
  box-sizing: border-box;
  min-width: 0;
  width: 100%;
  border-bottom: 1px solid #222;
}
.footer-content.day-content,
.footer-content.time-content {
  display: grid;
}
.footer-content.time-content .head-time-cell {
  min-height: 22px;
  font-size: 10px;
}
.footer-resp-label {
  min-height: 40px;
}
.resp-content .footer-resp-cell {
  min-height: 40px;
  font-size: 10px;
}
.footer-resp-cell.is-resp-high {
  align-items: flex-start;
  padding-top: 4px;
}
.footer-resp-cell.is-resp-low {
  align-items: flex-end;
  padding-bottom: 4px;
}
.footer-bp-cell {
  border-right: 1px solid #9ca3af;
}
.footer-bp-cell.is-day-end {
  border-right: 1px solid #b91c1c;
}
.footer-day-cell {
  box-sizing: border-box;
  min-height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #222;
  padding: 0 4px;
  line-height: 1.2;
  word-break: break-all;
}

@media print {
  @page {
    size: A4 portrait;
    margin: 0;
  }

  :global(body *) {
    visibility: hidden;
  }

  :global(body .temperature-sheet),
  :global(body .temperature-sheet *) {
    visibility: visible;
  }

  :global(body),
  :global(html),
  :global(#app) {
    margin: 0 !important;
    padding: 0 !important;
    height: auto !important;
    overflow: visible !important;
    transform: none !important;
    position: static !important;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  .temperature-sheet {
    position: fixed !important;
    top: 4mm !important;
    left: 4mm !important;
    z-index: 99999 !important;
    display: block;
    width: 794px !important;
    height: 1123px !important;
    margin: 0 !important;
    padding: 0;
    background: #fff;
    overflow: visible;
    transform: scale(0.96);
    transform-origin: top left;
  }

  .sheet-viewport {
    display: block;
    width: 794px !important;
    height: 1123px !important;
    margin: 0 !important;
    overflow: visible;
    padding: 0;
  }

  .sheet-stage {
    display: block;
    width: 794px !important;
    height: 1123px !important;
    margin: 0;
  }

  .sheet-paper {
    position: static;
    transform: none !important;
  }

  .sheet-paper-shell {
    width: 794px !important;
    height: 1123px !important;
    margin: 0 !important;
    overflow: visible;
  }

  .sheet-action-panel {
    display: none !important;
  }
}
</style>
