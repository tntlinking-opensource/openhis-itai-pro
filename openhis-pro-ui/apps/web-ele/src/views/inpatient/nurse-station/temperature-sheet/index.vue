<script setup lang="ts">
import type { EchartsUIType } from '@vben/plugins/echarts';

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { useFormKeyboardNavigation } from '#/hooks/use-form-keyboard-navigation';

type WindowRange = '3d' | '7d' | '14d';

defineOptions({
  name: 'InpatientNurseStationTemperatureSheetModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

interface TemperatureSheetPatient {
  id: string;
  bedNo: string;
  name: string;
  gender?: string;
  age: number;
  nurseLevel: string;
  feeType?: string;
  diagnosis?: string;
  wardName: string;
  riskTags: string[];
}

interface TemperaturePoint {
  date: string;
  heartRate?: number;
  pulse?: number;
  temp?: number;
  timeSlot: '02' | '06' | '10' | '14' | '18' | '22';
}

interface TooltipParamLike {
  dataIndex?: number;
  marker?: string;
  seriesName?: string;
}

interface Props {
  patients?: TemperatureSheetPatient[];
}

const A4_WIDTH = 794;
const A4_HEIGHT = 1123;

const activeWindow = ref<WindowRange>('7d');
const timeSlots: TemperaturePoint['timeSlot'][] = ['02', '06', '10', '14', '18', '22'];

/** 当前分页页码，从 0 开始，每页展示 activeWindow 对应天数。 */
const currentPage = ref(0);

/** 每页天数。 */
const pageSize = computed(() =>
  activeWindow.value === '3d' ? 3 : activeWindow.value === '14d' ? 14 : 7,
);

/** 总页数（暂以 mock 数据日期范围计算）。 */
const totalPages = computed(() => {
  const uniqueDates = [...new Set(mockPoints.value.map((p) => p.date))];
  return Math.max(1, Math.ceil(uniqueDates.length / pageSize.value));
});

function goToPrevPage() {
  if (currentPage.value > 0) currentPage.value -= 1;
}

function goToNextPage() {
  if (currentPage.value < totalPages.value - 1) currentPage.value += 1;
}
const fillDrawerVisible = ref(false);
const recordDate = ref('2026-04-24');
const fillFormAreaRef = ref<HTMLElement | null>(null);

interface TemperatureRecordRow {
  diastolic?: number;
  heartRate?: number;
  pulse?: number;
  remark?: string;
  respiratory?: number;
  systolic?: number;
  temp?: number;
  timeSlot: TemperaturePoint['timeSlot'];
}

const tempRecordRows = ref<TemperatureRecordRow[]>(
  timeSlots.map((slot) => ({
    timeSlot: slot,
  })),
);

const extraRecord = ref({
  abdominalCircumference: undefined as number | undefined,
  height: undefined as number | undefined,
  intake: undefined as number | undefined,
  other: undefined as number | undefined,
  skinTestPositive: undefined as number | undefined,
  stoolCount: undefined as number | undefined,
  urine: undefined as number | undefined,
  urineCount: undefined as number | undefined,
  weight: undefined as number | undefined,
});

const { handleFormKeydown: handleFillFormKeydown } = useFormKeyboardNavigation(fillFormAreaRef, {
  keys: ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Enter'],
  resolveFocusIndex: ({ currentIndex, direction, event, focusable }) => {
    const currentTarget = focusable[currentIndex];
    const currentCell = currentTarget?.closest<HTMLElement>('.nav-cell');
    // 上下键优先按“同列”在生命体征主表内跳转。
    if ((event.key === 'ArrowUp' || event.key === 'ArrowDown') && currentCell) {
      const { navCol, navGroup, navRow } = currentCell.dataset;
      if (navGroup === 'vital' && navCol !== undefined && navRow !== undefined) {
        const nextRow = Number(navRow) + (event.key === 'ArrowUp' ? -1 : 1);
        const nextCell = fillFormAreaRef.value?.querySelector<HTMLElement>(
          `.nav-cell[data-nav-group="vital"][data-nav-col="${navCol}"][data-nav-row="${nextRow}"]`,
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

/** 打开录入抽屉后，自动聚焦到首个录入框，减少鼠标点击。 */
async function openFillDrawer() {
  fillDrawerVisible.value = true;
  await nextTick();
  const firstInput = fillFormAreaRef.value?.querySelector<HTMLInputElement>(
    '.fill-row .el-input-number input',
  );
  firstInput?.focus();
}

function copyPrevRow(index: number) {
  if (index <= 0) return;
  const prev = tempRecordRows.value[index - 1];
  const current = tempRecordRows.value[index];
  if (!prev || !current) return;
  current.temp = prev.temp;
  current.pulse = prev.pulse;
  current.respiratory = prev.respiratory;
  current.heartRate = prev.heartRate;
  current.systolic = prev.systolic;
  current.diastolic = prev.diastolic;
}

function fillSameTemperature(value?: number) {
  if (value === undefined || Number.isNaN(value)) return;
  tempRecordRows.value.forEach((row) => {
    row.temp = value;
  });
}

/** 将当前时次常见字段快速同步到所有时次，减少重复录入。 */
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
const tempMin = 34;
const tempMax = 42;
const tempMinorStep = 0.2;
const majorTempTicks = [34, 35, 36, 37, 38, 39, 40, 41, 42] as const;
const tempGridLineCount = Math.round((tempMax - tempMin) / tempMinorStep) + 1;
const tempGridLineColors = Array.from({ length: tempGridLineCount }, (_item, index) =>
  index !== 0 && index !== tempGridLineCount - 1 && index % 5 === 0 ? '#1d4ed8' : '#9ca3af',
);
const chartRef = ref<EchartsUIType>();
const plotAreaRef = ref<HTMLElement>();
const sheetViewportRef = ref<HTMLElement>();
const sheetScale = ref(1);
const { renderEcharts } = useEcharts(chartRef);
let plotResizeObserver: null | ResizeObserver = null;

const mockPoints = ref<TemperaturePoint[]>([
  { date: '04-21', timeSlot: '02', temp: 37.1, pulse: 86, heartRate: 88 },
  { date: '04-21', timeSlot: '06', temp: 37.2, pulse: 88, heartRate: 90 },
  { date: '04-21', timeSlot: '10', temp: 37.9, pulse: 95, heartRate: 98 },
  { date: '04-21', timeSlot: '14', temp: 38.2, pulse: 100, heartRate: 102 },
  { date: '04-21', timeSlot: '18', temp: 37.8, pulse: 94, heartRate: 96 },
  { date: '04-21', timeSlot: '22', temp: 37.4, pulse: 90, heartRate: 92 },
  { date: '04-22', timeSlot: '02', temp: 37.2, pulse: 86, heartRate: 88 },
  { date: '04-22', timeSlot: '06', temp: 37.1, pulse: 84, heartRate: 86 },
  { date: '04-22', timeSlot: '10', temp: 37.5, pulse: 92, heartRate: 95 },
  { date: '04-22', timeSlot: '14', temp: 37.8, pulse: 98, heartRate: 100 },
  { date: '04-22', timeSlot: '18', temp: 37.6, pulse: 94, heartRate: 96 },
  { date: '04-22', timeSlot: '22', temp: 37.3, pulse: 88, heartRate: 90 },
  { date: '04-23', timeSlot: '02', temp: 37.2, pulse: 85, heartRate: 87 },
  { date: '04-23', timeSlot: '06', temp: 37.1, pulse: 83, heartRate: 85 },
  { date: '04-23', timeSlot: '10', temp: 37.4, pulse: 90, heartRate: 93 },
  { date: '04-23', timeSlot: '14', temp: 37.8, pulse: 96, heartRate: 98 },
  { date: '04-23', timeSlot: '18', temp: 37.5, pulse: 92, heartRate: 94 },
  { date: '04-23', timeSlot: '22', temp: 37.2, pulse: 86, heartRate: 88 },
]);

const selectedPatient = computed(() => props.patients[0]);

const visiblePoints = computed(() => {
  const dayCount = activeWindow.value === '3d' ? 3 : activeWindow.value === '14d' ? 14 : 7;
  const firstDate = mockPoints.value[0]?.date || '2026-04-21';
  const [yearText, monthText, dayText] =
    firstDate.includes('-') && firstDate.length > 8
      ? firstDate.split('-')
      : ['2026', '04', firstDate.slice(-2)];
  const startDate = new Date(Number(yearText), Number(monthText) - 1, Number(dayText));
  const pointMap = new Map(mockPoints.value.map((item) => [`${item.date}-${item.timeSlot}`, item]));
  const points: TemperaturePoint[] = [];
  for (let dayOffset = 0; dayOffset < dayCount; dayOffset += 1) {
    const currentDate = new Date(startDate);
    currentDate.setDate(startDate.getDate() + dayOffset);
    const mm = `${currentDate.getMonth() + 1}`.padStart(2, '0');
    const dd = `${currentDate.getDate()}`.padStart(2, '0');
    const dateText = `${mm}-${dd}`;
    for (const timeSlot of timeSlots) {
      const key = `${dateText}-${timeSlot}`;
      const matched = pointMap.get(key);
      points.push({
        date: dateText,
        timeSlot,
        temp: matched?.temp,
        pulse: matched?.pulse,
        heartRate: matched?.heartRate,
      });
    }
  }
  return points;
});

const chartColumns = computed(() => visiblePoints.value.length);
const dayDividerColumns = computed(() =>
  visiblePoints.value
    .map((_item, index) => (isDayDividerColumn(index) ? index : null))
    .filter((item): item is number => item !== null && item < visiblePoints.value.length - 1),
);
const admissionMarkerIndex = computed(() =>
  visiblePoints.value.findIndex((point) => point.timeSlot === '18'),
);
const tempScaleDisplay = computed(() =>
  majorTempTicks.toReversed().filter((temp) => temp < 42 && temp > 34),
);
const pulseScaleDisplay = [160, 140, 120, 100, 80, 60, 40] as const;
const tempTickStyles = computed(() =>
  tempScaleDisplay.value.map((temp) => {
    const top = tempToTopPercent(temp);
    return {
      temp,
      style: {
        top: `${top ?? 0}%`,
      },
    };
  }),
);
const pulseTickStyles = computed(() =>
  pulseScaleDisplay.map((pulse) => {
    const top = pulseToTopPercent(pulse);
    return {
      pulse,
      style: {
        top: `${top ?? 0}%`,
      },
    };
  }),
);

const dayHeaders = computed(() => {
  const headers: Array<{ date: string; span: number; startIndex: number }> = [];
  let currentDate = '';
  let startIndex = 0;
  let span = 0;
  visiblePoints.value.forEach((item, index) => {
    if (item.date !== currentDate) {
      if (currentDate) headers.push({ date: currentDate, span, startIndex });
      currentDate = item.date;
      startIndex = index;
      span = 1;
      return;
    }
    span += 1;
  });
  if (currentDate) headers.push({ date: currentDate, span, startIndex });
  return headers;
});

/** 每天 6 个时次，最后一列作为红色日分割线。 */
function isDayDividerColumn(index: number): boolean {
  return (index + 1) % 6 === 0;
}

/** 时间行仅保留中间日分隔线，最右外边框不标红。 */
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

function heartRateToTopPercent(value?: number): null | number {
  if (value === undefined) return null;
  const normalized = tempMin + ((value - 20) / 160) * (tempMax - tempMin);
  return tempToTopPercent(normalized);
}

/** 体温单 tooltip 展示原始业务值，避免显示共轴换算后的温度值。 */
function buildTooltipText(params: TooltipParamLike | TooltipParamLike[]): string {
  if (!Array.isArray(params)) {
    const point = visiblePoints.value[params.dataIndex ?? -1];
    if (!point) return '';
    if (params.seriesName === '体温') {
      return `${point.date} ${point.timeSlot}:00<br/>${params.marker ?? ''} 体温：${point.temp} ℃`;
    }
    if (params.seriesName === '脉搏') {
      return `${point.date} ${point.timeSlot}:00<br/>${params.marker ?? ''} 脉搏：${point.pulse} 次/分`;
    }
    if (params.seriesName === '心率') {
      return `${point.date} ${point.timeSlot}:00<br/>${params.marker ?? ''} 心率：${point.heartRate} 次/分`;
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
      if (item.seriesName === '体温') {
        if (point.temp === undefined) return '';
        return `${item.marker ?? ''} 体温：${point.temp} ℃`;
      }
      if (item.seriesName === '脉搏') {
        if (point.pulse === undefined) return '';
        return `${item.marker ?? ''} 脉搏：${point.pulse} 次/分`;
      }
      if (item.seriesName === '心率') {
        if (point.heartRate === undefined) return '';
        return `${item.marker ?? ''} 心率：${point.heartRate} 次/分`;
      }
      return '';
    })
    .filter(Boolean);
  if (lines.length === 0) return '';
  return [header, ...lines].join('<br/>');
}

/** 将 0-100% 的相对位置映射为体温轴绝对值。 */
function percentToTempValue(percent: number): number {
  return tempMax - ((tempMax - tempMin) * percent) / 100;
}

/** 构建 ECharts 配置：主网格、蓝色主横线、红色日分割线与三组点线。 */
const chartOption = computed(() => {
  const dividerPositionSet = new Set(dayDividerColumns.value.map((index) => index + 1));
  const xGridLineColors = Array.from({ length: chartColumns.value + 1 }, (_item, index) =>
    dividerPositionSet.has(index) ? 'transparent' : '#9ca3af',
  );
  const tempSeriesData = visiblePoints.value.map((item, index) =>
    item.temp === undefined ? null : [index + 0.5, Number(item.temp.toFixed(2))],
  );
  const pulseSeriesData = visiblePoints.value.map((item, index) => {
    const top = pulseToTopPercent(item.pulse);
    if (top === null) return null;
    return [index + 0.5, Number(percentToTempValue(top).toFixed(2))];
  });
  const heartSeriesData = visiblePoints.value.map((item, index) => {
    const top = heartRateToTopPercent(item.heartRate);
    return top === null ? null : [index + 0.5, Number(percentToTempValue(top).toFixed(2))];
  });
  return {
    animation: false,
    grid: {
      bottom: 0,
      containLabel: false,
      left: 0,
      right: 0,
      top: 0,
    },
    xAxis: {
      axisLabel: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      interval: 1,
      max: chartColumns.value,
      min: 0,
      splitLine: {
        lineStyle: {
          color: xGridLineColors,
          width: 1,
        },
        show: true,
      },
      type: 'value' as const,
    },
    yAxis: {
      axisLabel: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      interval: tempMinorStep,
      max: tempMax,
      min: tempMin,
      splitLine: {
        lineStyle: {
          color: tempGridLineColors,
          width: 1,
        },
        show: true,
      },
      type: 'value' as const,
    },
    series: [
      {
        connectNulls: false,
        data: tempSeriesData,
        itemStyle: { color: '#d9001b' },
        lineStyle: { color: '#d9001b', width: 1 },
        name: '体温',
        showSymbol: true,
        symbol: 'circle',
        symbolSize: 6,
        type: 'line' as const,
      },
      {
        connectNulls: false,
        data: pulseSeriesData,
        itemStyle: { color: '#111827' },
        lineStyle: { color: '#111827', width: 1 },
        name: '脉搏',
        showSymbol: true,
        symbol: 'path://M-4,-4 L4,4 M4,-4 L-4,4',
        symbolSize: 8,
        type: 'line' as const,
      },
      {
        connectNulls: false,
        data: heartSeriesData,
        itemStyle: {
          borderColor: '#1d4ed8',
          borderWidth: 1.2,
          color: '#ffffff',
        },
        lineStyle: { color: '#1d4ed8', width: 1 },
        name: '心率',
        showSymbol: true,
        symbol: 'circle',
        symbolSize: 7,
        type: 'line' as const,
      },
    ],
    tooltip: {
      formatter: (params: unknown) =>
        buildTooltipText(params as TooltipParamLike | TooltipParamLike[]),
      trigger: 'item' as const,
    },
  };
});

/** 初始化并在数据窗口变化时刷新体温单主图。 */
async function renderTemperatureChart() {
  await nextTick();
  await renderEcharts(chartOption.value);
}

/** 根据可用空间计算 A4 画布缩放比例，保证仅等比缩小。 */
function updateSheetScale() {
  const viewport = sheetViewportRef.value;
  if (!viewport) return;
  const widthScale = viewport.clientWidth / A4_WIDTH;
  const nextScale = Math.min(widthScale, 1);
  sheetScale.value = Number.isFinite(nextScale) && nextScale > 0 ? nextScale : 1;
}

/** 调用浏览器打印，使用系统打印预览完成打印参数设置。 */
function handlePrint() {
  window.print();
}

onMounted(() => {
  updateSheetScale();
  window.addEventListener('resize', updateSheetScale);
  plotResizeObserver = new ResizeObserver(() => {
    void renderTemperatureChart();
  });
  if (plotAreaRef.value) {
    plotResizeObserver.observe(plotAreaRef.value);
  }
  void renderTemperatureChart();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateSheetScale);
  plotResizeObserver?.disconnect();
  plotResizeObserver = null;
});

watch([visiblePoints, activeWindow], () => {
  void renderTemperatureChart();
});

watch(sheetViewportRef, () => {
  updateSheetScale();
});
</script>

<template>
  <div class="temperature-sheet">
    <div ref="sheetViewportRef" class="sheet-viewport">
      <div class="sheet-stage">
        <div
          class="sheet-paper-shell"
          :style="{
            height: `${Math.ceil(A4_HEIGHT * sheetScale)}px`,
            width: `${Math.ceil(A4_WIDTH * sheetScale)}px`,
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
              <span>病历号：{{ selectedPatient?.id || '' }}</span>
              <span>入院日期：2026-01-13</span>
              <span>出院日期：</span>
            </div>

            <div class="sheet-head-grid">
              <div class="head-label">日期</div>
              <div
                class="head-content day-content"
                :style="{ gridTemplateColumns: `repeat(${chartColumns}, 1fr)` }"
              >
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
              <div
                class="head-content day-content"
                :style="{ gridTemplateColumns: `repeat(${chartColumns}, 1fr)` }"
              >
                <span
                  v-for="(header, index) in dayHeaders"
                  :key="`in-day-${header.date}-${header.startIndex}`"
                  class="head-day-cell"
                  :style="{ gridColumn: `${header.startIndex + 1} / span ${header.span}` }"
                >
                  第 {{ 99 + index }} 日
                </span>
              </div>
              <div class="head-label">术/化疗后日数</div>
              <div
                class="head-content day-content"
                :style="{ gridTemplateColumns: `repeat(${chartColumns}, 1fr)` }"
              >
                <span
                  v-for="header in dayHeaders"
                  :key="`post-day-${header.date}-${header.startIndex}`"
                  class="head-day-cell"
                  :style="{ gridColumn: `${header.startIndex + 1} / span ${header.span}` }"
                >
                  第 日
                </span>
              </div>
              <div class="head-label">时间</div>
              <div
                class="head-content time-content"
                :style="{ gridTemplateColumns: `repeat(${chartColumns}, 1fr)` }"
              >
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
                <div
                  class="day-divider-overlay"
                  :style="{ gridTemplateColumns: `repeat(${chartColumns}, 1fr)` }"
                >
                  <span
                    v-for="index in dayDividerColumns"
                    :key="`divider-${index}`"
                    class="day-divider-marker"
                    :style="{ gridColumn: `${index + 1}` }"
                  ></span>
                </div>
                <div
                  v-if="admissionMarkerIndex >= 0"
                  class="event-text-marker"
                  :style="{ left: `${((admissionMarkerIndex + 0.5) / chartColumns) * 100}%` }"
                >
                  入院|时间十时十分
                </div>
                <div ref="plotAreaRef" class="plot-area">
                  <EchartsUI ref="chartRef" class="plot-chart" height="100%" width="100%" />
                </div>
              </main>
            </div>
            <div class="sheet-footer">
              <div class="footer-row"><span>呼吸(次/分)</span><span></span></div>
              <div class="footer-row"><span>血压(mmHg)</span><span></span></div>
              <div class="footer-row"><span>入量及类型</span><span></span></div>
              <div class="footer-row"><span>出量及类型</span><span></span></div>
              <div class="footer-row"><span>大便(次)</span><span></span></div>
              <div class="footer-row"><span>体重(kg)</span><span></span></div>
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
          <ElButton size="small" type="primary" @click="handlePrint">打印当前页</ElButton>
          <ElButton size="small" type="primary" @click="handlePrint">打印全部页</ElButton>
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
      size="50%"
    >
      <div ref="fillFormAreaRef" class="fill-drawer" @keydown="handleFillFormKeydown">
        <div class="fill-toolbar">
          <div class="fill-toolbar-left">
            <ElDatePicker
              v-model="recordDate"
              format="YYYY-MM-DD"
              placeholder="录入日期"
              type="date"
              value-format="YYYY-MM-DD"
            />
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
            <header class="fill-card-title">生命体征（主录入）</header>
            <div class="fill-grid-head">
              <span>时次</span>
              <span>体温(℃)</span>
              <span>脉搏</span>
              <span>呼吸</span>
              <span>心率</span>
              <span>收缩压</span>
              <span>舒张压</span>
              <span>快捷</span>
            </div>
            <div class="fill-grid-body">
              <div v-for="(row, index) in tempRecordRows" :key="row.timeSlot" class="fill-row">
                <span class="time-slot">{{ row.timeSlot }}:00</span>
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
                  <ElInput v-model="row.systolic" placeholder="收缩压">
                    <template #suffix>mmHg</template>
                  </ElInput>
                </div>
                <div
                  class="nav-cell"
                  :data-nav-col="5"
                  data-nav-group="vital"
                  :data-nav-row="index"
                >
                  <ElInput v-model="row.diastolic" placeholder="舒张压">
                    <template #suffix>mmHg</template>
                  </ElInput>
                </div>
                <ElButton link type="primary" @click="copyPrevRow(index)">复制上一时次</ElButton>
              </div>
            </div>
          </section>

          <section class="fill-card fill-side-card">
            <header class="fill-card-title">补充记录</header>
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
          <ElButton plain>暂存</ElButton>
          <ElButton type="primary">保存并下一时次</ElButton>
        </div>
      </template>
    </ElDrawer>
  </div>
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
.sheet-viewport {
  display: block;
  flex: 1;
  width: 100%;
  min-height: 0;
  overflow: auto;
  padding: 8px 0;
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
.fill-drawer-main {
  display: grid;
  grid-template-columns: minmax(0, 2.2fr) minmax(280px, 1fr);
  gap: 4px;
  min-height: 0;
  flex: 1;
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
  grid-template-columns: 80px repeat(6, minmax(90px, 1fr)) 96px;
  gap: 8px;
  align-items: center;
}
.fill-grid-head {
  padding: 2px 4px;
  background: #eff6ff;
  font-size: 12px;
  color: #334155;
  text-align: center;
}
.fill-grid-body {
  flex: 1;
  overflow: auto;
  padding: 2px 4px;
}
.fill-row {
  padding: 2px 0;
}
.fill-row + .fill-row {
  border-top: 1px dashed #e5e7eb;
}
.time-slot {
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  font-weight: 700;
  font-size: 13px;
  color: #1f2937;
}
.fill-row :deep(.el-input),
.extra-grid :deep(.el-input) {
  width: 100%;
}
.fill-row :deep(.el-input__suffix),
.extra-grid :deep(.el-input__suffix) {
  color: #6b7280;
  font-size: 11px;
}
.fill-side-card {
  padding-bottom: 2px;
}
.extra-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  padding: 4px;
}
.extra-item {
  display: grid;
  grid-template-columns: 72px minmax(0, 156px);
  align-items: center;
  gap: 4px;
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
}
.patient-info-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 14px;
  min-height: 24px;
  padding: 2px 4px;
  font-size: 12px;
}

.sheet-head-grid {
  display: grid;
  grid-template-columns: var(--axis-total-width) 1fr;
  border: 1px solid #222;
  border-bottom: 1px solid #222;
  font-size: 13px;
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
  top: 4px;
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
.sheet-footer {
  border: 1px solid #222;
  border-top: 0;
}
.footer-row {
  min-height: 22px;
  display: flex;
  border-top: 1px solid #222;
}
.footer-row:first-child {
  border-top: 0;
}
.footer-row > span:first-child {
  width: 110px;
  border-right: 1px solid #222;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.footer-row > span:last-child {
  flex: 1;
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
