<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type { TemperatureSheetChartPoint } from './services/map-nurse-station-temperature-sheet';
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
import { ElLoading, ElMessage } from 'element-plus';

import {
  httpGetNurseStationTemperatureSheetData,
  httpLoadNurseStationTemperatureSheetInit,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-temperature-sheet.http';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  buildChartPointsFromGridRows,
  buildFooterDayCellsFromGridRows,
  buildTemperatureSheetPeekGridRowFromFillSlots,
  extendDerivedMetaForWindow,
  resolveMergedTemperatureSheetGridRows,
  resolveTemperatureSheetWindowStartDate,
  shiftTemperatureSheetRecordDateYmd,
  TEMPERATURE_SHEET_FOOTER_SUPPLEMENT_ROWS,
} from './services/map-nurse-station-temperature-sheet';
import { printTemperatureSheetPaper } from './services/temperature-sheet-browser-print';
import {
  buildChartTextMarkers,
  buildPhysicalCoolingChartPoints,
  buildSpecialEventLabelMap,
  buildSpecialEventStoreFromGridRows,
  createEmptySpecialEventStore,
  TEMPERATURE_SHEET_PHYSICAL_COOLING_DELAY_MINUTES,
} from './services/temperature-sheet-special-events';
import { resolveAdmissionDateYmd } from './services/temperature-sheet-time-format';
import {
  PAPER_SHEET_DESIGN_WIDTH,
  usePaperSheetViewportScale,
} from '../composables/use-paper-sheet-viewport-scale';
import { usePaperSheetFullscreen } from '../composables/use-paper-sheet-fullscreen';
import TemperatureSheetBatchFillDrawer from './components/temperature-sheet-batch-fill-drawer.vue';
import TemperatureSheetSingleFillDrawer from './components/temperature-sheet-single-fill-drawer.vue';
import './styles/temperature-sheet-fill-drawer.css';

defineOptions({
  name: 'InpatientNurseStationTemperatureSheetModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
  readonly: false,
});

interface Props {
  patients?: NurseStationModulePatient[];
  /** 只读：隐藏填写/批量录入，医生站查看用 */
  readonly?: boolean;
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
/** 窗口末日次日网格（跨页时供底部前一日统计读 S+1） */
const footerPeekGridRows = ref<TemperatureSheetGridRowDto[]>([]);
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
const fillDrawerRef = ref<InstanceType<typeof TemperatureSheetSingleFillDrawer> | null>(null);
const batchFillDrawerVisible = ref(false);
const batchFillDrawerRef = ref<InstanceType<typeof TemperatureSheetBatchFillDrawer> | null>(null);
const specialEventStore = ref<TemperatureSheetSpecialEventStore>(createEmptySpecialEventStore());
const specialEventOptions = ref<TemperatureSheetSpecialEventOption[]>([]);
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
const sheetPaperRef = ref<HTMLElement>();
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

const footerDayCells = computed(() =>
  buildFooterDayCellsFromGridRows(sheetGridRows.value, footerPeekGridRows.value),
);

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

/** 解析当前页窗口末日的次日（用于底部前一日统计跨页 peek） */
function resolveFooterPeekFocusDate(admissionAt?: string, paginationDateTo?: string): string {
  const admissionDate = resolveAdmissionDateYmd(admissionAt);
  if (admissionDate) {
    const windowDateFrom = shiftTemperatureSheetRecordDateYmd(
      admissionDate,
      currentPage.value * WINDOW_DAY_COUNT,
    );
    const windowDateTo = shiftTemperatureSheetRecordDateYmd(windowDateFrom, WINDOW_DAY_COUNT - 1);
    return shiftTemperatureSheetRecordDateYmd(windowDateTo, 1);
  }
  const dateTo = (paginationDateTo ?? '').trim();
  if (!dateTo) {
    return '';
  }
  return shiftTemperatureSheetRecordDateYmd(dateTo, 1);
}

/** 加载体温单展示数据 */
async function loadTemperatureSheetData() {
  const context = selectedPatientContext.value;
  if (!context) {
    chartPoints.value = [];
    sheetGridRows.value = [];
    footerPeekGridRows.value = [];
    totalPages.value = 1;
    return;
  }

  // 预计算窗口末日次日：同屏无 S+1 时用 fillSlots 补底部前一日统计
  const peekFocusDate = resolveFooterPeekFocusDate(context.patient.admissionAt);
  const response = await dataRequest.run({
    encounterId: context.encounterId,
    patientId: context.patientId,
    windowRange: WINDOW_RANGE,
    pageIndex: currentPage.value,
    focusRecordDate: peekFocusDate || undefined,
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

  // 窗口内已有次日列则不必 peek；否则用 focus 日 fillSlots 合成次日网格
  const resolvedPeekDate =
    peekFocusDate ||
    resolveFooterPeekFocusDate(context.patient.admissionAt, response.pagination?.dateTo);
  const peekAlreadyInWindow = sheetGridRows.value.some(
    (row) => (row.recordDate ?? '').trim() === resolvedPeekDate,
  );
  if (resolvedPeekDate && !peekAlreadyInWindow && response.fillSlots?.length) {
    footerPeekGridRows.value = [
      buildTemperatureSheetPeekGridRowFromFillSlots(resolvedPeekDate, response.fillSlots),
    ];
  } else if (resolvedPeekDate && !peekAlreadyInWindow && !peekFocusDate) {
    // 无入院日时首包未带 focus，补一次次日回显
    const peekResponse = await dataRequest.run({
      encounterId: context.encounterId,
      patientId: context.patientId,
      windowRange: WINDOW_RANGE,
      pageIndex: currentPage.value,
      focusRecordDate: resolvedPeekDate,
    });
    footerPeekGridRows.value = peekResponse.fillSlots?.length
      ? [buildTemperatureSheetPeekGridRowFromFillSlots(resolvedPeekDate, peekResponse.fillSlots)]
      : [];
  } else {
    footerPeekGridRows.value = [];
  }
}

/** 打开按患者录入抽屉（先加载回显再打开，避免抽屉上弹） */
async function openFillDrawer(): Promise<void> {
  if (props.readonly) {
    return;
  }
  const context = selectedPatientContext.value;
  if (!context) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const patient = context.patient;
  await fillDrawerRef.value?.open({
    patient: {
      encounterId: context.encounterId,
      patientId: context.patientId,
      bedNo: patient.bedNo?.trim() || '-',
      name: patient.name?.trim() || '-',
      gender: patient.gender?.trim(),
      admissionAt: patient.admissionAt,
    },
  });
}

/** 单人录入保存后刷新体温单图表 */
function handleSingleFillSaved(): void {
  void loadTemperatureSheetData();
}

/** 打开按时点批量录入抽屉 */
async function openBatchFillDrawer(): Promise<void> {
  if (props.readonly) {
    return;
  }
  const drawer = batchFillDrawerRef.value;
  if (!drawer) {
    return;
  }
  const loading = ElLoading.service({
    lock: true,
    text: '加载患者体温回显…',
    background: 'rgba(255, 255, 255, 0.6)',
  });
  try {
    await drawer.prepareOpen();
    batchFillDrawerVisible.value = true;
    await drawer.focusFirstInput();
  } finally {
    loading.close();
  }
}

/** 批量保存后刷新当前选中患者体温单 */
function handleBatchFillSaved(): void {
  if (selectedPatientContext.value) {
    void loadTemperatureSheetData();
  }
}

/** 浏览器打印当前体温单纸面（同文档克隆宿主，避免空白/缩角） */
async function handlePrint(): Promise<void> {
  if (!selectedPatientContext.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const paper = sheetPaperRef.value;
  if (!paper) {
    ElMessage.warning('体温单纸面未就绪');
    return;
  }
  const loading = ElLoading.service({
    lock: true,
    text: '正在准备打印…',
    background: 'rgba(255, 255, 255, 0.6)',
  });
  try {
    await nextTick();
    await renderTemperatureChart();
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
    });
    // 必须在调起系统打印前关闭遮罩，否则可能进入打印 DOM
    loading.close();
    await printTemperatureSheetPaper(paper);
  } catch (error) {
    const message = error instanceof Error ? error.message : '打印失败';
    ElMessage.error(message);
  } finally {
    loading.close();
  }
}

/** 底部血压半日列 span */
function resolveFooterBpHalfSpan(daySpan: number): number {
  return Math.max(1, Math.floor(daySpan / 2));
}

/** 呼吸值是否显示在单元格上方（奇数时次低、偶数时次高，每日从高位重新开始） */
function isRespiratoryHighSlot(index: number): boolean {
  return index % 2 === 0;
}

const chartColumns = computed(() => visiblePoints.value.length);
const slotGridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${chartColumns.value}, minmax(0, 1fr))`,
}));
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
      // 纵向框线改由 CSS 与表头/表尾同构绘制，避免 ECharts splitLine 相对 border-right 偏右 1px
      splitLine: { show: false },
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
        // 时间点不连续时也要跨空槽连线（如仅 16 日/17 日各两点）
        connectNulls: true,
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
        // 时间点不连续时也要跨空槽连线（如仅 16 日/17 日各两点）
        connectNulls: true,
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
      <ElEmpty v-if="!canOperateSheet" description="请先从左侧选择一位患者查看体温单">
        <ElButton v-if="!readonly" type="primary" plain @click="openBatchFillDrawer">
          按时点批量录入
        </ElButton>
      </ElEmpty>

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
            <section
              ref="sheetPaperRef"
              class="sheet-paper"
              :style="{ transform: `scale(${sheetScale})` }"
            >
              <header class="sheet-title">体 温 单</header>

              <div class="patient-info-line">
                <span>姓名：{{ selectedPatient?.name || '' }}</span>
                <span>性别：{{ selectedPatient?.gender || '' }}</span>
                <span>年龄：{{ selectedPatient?.age || '' }}岁</span>
                <span>病室：{{ selectedPatient?.wardName || '' }}</span>
                <span>床号：{{ selectedPatient?.bedNo || '' }}</span>
                <span>病历号：{{ selectedPatient?.encounterBusNo || '' }}</span>
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
                <div class="chart-axis-rail">
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
                </div>
                <main class="chart-main">
                  <!-- 与表头时间格 / 表尾呼吸格同构：每槽 border-right，保证纵向框线对齐 -->
                  <div class="slot-vline-overlay" :style="slotGridStyle">
                    <span
                      v-for="(point, index) in visiblePoints"
                      :key="`slot-vline-${point.fullDate}-${point.timeSlot}-${index}`"
                      class="slot-vline"
                      :class="{ 'is-day-divider': isInnerDayDividerColumn(index) }"
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
            <ElButton size="small" type="primary" @click="handlePrint">打印当前页</ElButton>
            <ElButton
              v-if="!readonly"
              size="small"
              plain
              type="primary"
              @click="openBatchFillDrawer"
            >
              按时点批量录入
            </ElButton>
            <ElButton v-if="!readonly" size="small" type="success" @click="openFillDrawer">
              填写体温单
            </ElButton>
          </div>
        </div>
      </div>
    </div>
  </Teleport>

  <template v-if="!readonly">
    <TemperatureSheetSingleFillDrawer ref="fillDrawerRef" @saved="handleSingleFillSaved" />

    <TemperatureSheetBatchFillDrawer
      ref="batchFillDrawerRef"
      v-model:visible="batchFillDrawerVisible"
      @saved="handleBatchFillSaved"
    />
  </template>
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
  /* 与 sheet-head-grid / sheet-footer-grid 同列模板，保证内容区左缘与竖线对齐 */
  display: grid;
  grid-template-columns: var(--axis-total-width) minmax(0, 1fr);
  flex: 1;
  min-height: 0;
  border: 1px solid #222;
  border-top: 0;
}
.chart-axis-rail {
  box-sizing: border-box;
  display: flex;
  min-width: 0;
  height: 100%;
  border-right: 1px solid #222;
}
.axis {
  box-sizing: border-box;
  flex: 1 1 0;
  width: auto;
  min-width: 0;
  position: relative;
  display: flex;
  align-items: stretch;
  font-size: 10px;
  padding: 0;
}
.axis-left {
  border-right: var(--axis-border-width) solid #222;
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
.slot-vline-overlay {
  position: absolute;
  inset: 0;
  display: grid;
  pointer-events: none;
  z-index: 3;
}
.slot-vline {
  box-sizing: border-box;
  border-right: 1px solid #9ca3af;
}
.slot-vline.is-day-divider {
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
</style>
