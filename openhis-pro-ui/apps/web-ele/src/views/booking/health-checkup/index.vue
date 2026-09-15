<script setup lang="ts">
import type {
  HcDaySlotDto,
  HcDaySummaryDto,
  HcSlotTemplateDto,
} from '#/api/booking/health-checkup-slot-calendar';
import type { ContractTypeRowDto } from '#/api/peis/group-check';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import {
  addDaySlotApi,
  batchAddDaySlotsApi,
  copyDayPlanApi,
  deleteDaySlotApi,
  generateFutureDaysApi,
  getSlotTemplateApi,
  loadDayDetailApi,
  loadMonthOverviewApi,
  saveSlotTemplateApi,
  setDayContractTypesApi,
  setDayRestApi,
  setSlotOpenApi,
  updateSlotCapacityApi,
} from '#/api/booking/health-checkup-slot-calendar';
import { listContractTypesApi } from '#/api/peis/group-check';
import { listActiveCampusSitesApi } from '#/api/campus/campus-site';
import { useBookingOrganization } from '#/views/booking/composables/use-booking-organization';
import { useRequest } from '#/hooks/use-request';
import { ArrowLeft, ArrowRight, Refresh } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTimePicker,
} from 'element-plus';

defineOptions({ name: 'BookingHealthCheckup' });

function formatLocalDate(date: Date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return { yearMonth: `${y}-${m}`, date: `${y}-${m}-${d}` };
}

const todayLocal = formatLocalDate(new Date());

interface MonthCell {
  date: string;
  inMonth: boolean;
  summary?: HcDaySummaryDto;
}

interface SlotTableRow {
  timeKey: string;
  startTime?: string;
  endTime?: string;
  slotsByColumn: Record<string, HcDaySlotDto | undefined>;
}

const WEEKDAY_LABELS = ['一', '二', '三', '四', '五', '六', '日'];
const REST_WEEKDAY_OPTIONS = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' },
];

const monthListRef = ref<HTMLElement | null>(null);

const { organizationId } = useBookingOrganization({ fallbackToHospital: true });
const yearMonth = ref(todayLocal.yearMonth);
const monthCells = ref<MonthCell[]>([]);
const selectedDate = ref(todayLocal.date);
const dayRestFlag = ref(false);
const dayTypeCodes = ref<string[]>([]);
const daySlots = ref<HcDaySlotDto[]>([]);
const contractTypes = ref<ContractTypeRowDto[]>([]);
const typeNameMap = computed(() => {
  const map = new Map<string, string>();
  for (const row of contractTypes.value) {
    if (row.typeCode) map.set(row.typeCode, row.typeName ?? row.typeCode);
  }
  return map;
});

const templateVisible = ref(false);
const templateForm = ref<HcSlotTemplateDto>({
  slotMinutes: 30,
  amStart: '08:00:00',
  amEnd: '12:00:00',
  pmStart: '14:00:00',
  pmEnd: '17:00:00',
  capacityByType: {},
  restWeekdays: [],
  campusCodes: [],
});

const generateVisible = ref(false);
const generateDays = ref(30);
const generateFromDate = ref('');
const generateLoading = ref(false);

const copyVisible = ref(false);
const copyTargetDates = ref<string[]>([]);

const addingSlot = ref(false);
const addSlotSaving = ref(false);
const addSlotForm = ref({
  startTime: '08:00:00',
  endTime: '08:30:00',
  contractTypeCode: '',
  campusCode: '',
  capacity: 0,
  openFlag: true,
});

const batchAddingSlot = ref(false);
const batchAddSaving = ref(false);
const batchAddForm = ref({
  windowStart: '08:00:00',
  windowEnd: '12:00:00',
  slotMinutes: 30,
  contractTypeCodes: [] as string[],
  capacity: 0,
  openFlag: true,
});
/** 批量加档：各校区是否选用 + 独立容量 */
const batchCampusRows = ref<{ code: string; name: string; selected: boolean; capacity: number }[]>(
  [],
);
/** 校区编码 → 显示名 */
const campusNameMap = ref(new Map<string, string>());
/** 机构模板中的校区（用于空日也展出矩阵列） */
const orgCampusCodes = ref<string[]>([]);

const capacitySavingIds = ref(new Set<string>());

const campusOptions = computed(() => {
  const map = new Map(campusNameMap.value);
  for (const code of orgCampusCodes.value) {
    if (code && !map.has(code)) map.set(code, code);
  }
  for (const slot of daySlots.value) {
    if (slot.campusCode && !map.has(slot.campusCode)) {
      map.set(slot.campusCode, slot.campusCode);
    }
  }
  return [...map.entries()].map(([code, name]) => ({ code, name }));
});
const showCampusColumn = computed(
  () =>
    orgCampusCodes.value.length > 0 ||
    daySlots.value.some((row) => !!row.campusCode) ||
    campusOptions.value.length > 0,
);
const batchHasCampus = computed(() => batchCampusRows.value.length > 0);

/** 加档可选类型：优先当日开放类型，否则全部合同类型 */
const addSlotTypeOptions = computed(() => {
  const opened = new Set(dayTypeCodes.value);
  const preferred = contractTypes.value.filter(
    (row) => row.typeCode && (opened.size === 0 || opened.has(row.typeCode)),
  );
  return preferred.length > 0 ? preferred : contractTypes.value;
});

/** 当日参与分池的校区列表（模板优先，否则已有档位，再否则启用校区主数据） */
const dayCampusCodes = computed(() => {
  if (orgCampusCodes.value.length > 0) return [...orgCampusCodes.value];
  const fromSlots = [
    ...new Set(
      daySlots.value.map((row) => row.campusCode).filter((code): code is string => !!code),
    ),
  ];
  if (fromSlots.length > 0) return fromSlots;
  return campusOptions.value.map((row) => row.code);
});

const hasUnassignedCampusSlots = computed(() => daySlots.value.some((row) => !row.campusCode));

/** 是否按校区分组展示列 */
const useCampusGroupedColumns = computed(() => dayCampusCodes.value.length > 0);

/** 校区筛选：ALL = 全部校区 */
const campusFilter = ref<string>('ALL');

const displayCampusCodes = computed(() => {
  if (!useCampusGroupedColumns.value) return [] as string[];
  if (campusFilter.value === 'ALL') return dayCampusCodes.value;
  if (campusFilter.value === '__NONE__') return [] as string[];
  return dayCampusCodes.value.filter((code) => code === campusFilter.value);
});

/** 表头：校区 → 开放类型 */
const campusColumnGroups = computed(() => {
  const types =
    dayTypeCodes.value.length > 0
      ? dayTypeCodes.value
      : [
          ...new Set(
            daySlots.value
              .map((row) => row.contractTypeCode)
              .filter((code): code is string => !!code),
          ),
        ];
  const groups = displayCampusCodes.value.map((campusCode) => ({
    campusCode,
    campusLabel: campusNameMap.value.get(campusCode) ?? campusCode,
    typeColumns: types.map((typeCode) => ({
      typeCode,
      typeLabel: typeNameMap.value.get(typeCode) ?? typeCode,
      colKey: buildSlotColumnKey(typeCode, campusCode),
    })),
  }));
  // 历史未带校区的档位单独一组，避免看不见
  if (
    hasUnassignedCampusSlots.value &&
    (campusFilter.value === 'ALL' || campusFilter.value === '__NONE__')
  ) {
    groups.push({
      campusCode: '',
      campusLabel: '未分校区',
      typeColumns: types.map((typeCode) => ({
        typeCode,
        typeLabel: typeNameMap.value.get(typeCode) ?? typeCode,
        colKey: buildSlotColumnKey(typeCode, undefined),
      })),
    });
  }
  return groups;
});

/** 无校区时的扁平列 */
const flatTypeColumnKeys = computed(() => {
  const keys = new Set<string>();
  for (const slot of daySlots.value) {
    if (!slot.campusCode) {
      keys.add(buildSlotColumnKey(slot.contractTypeCode, undefined));
    }
  }
  for (const typeCode of dayTypeCodes.value) {
    keys.add(buildSlotColumnKey(typeCode, undefined));
  }
  return [...keys].sort((a, b) => a.localeCompare(b, 'zh-CN'));
});

/** 各校区余量汇总（筛选条展示） */
const campusRemainingStats = computed(() => {
  const map = new Map<string, number>();
  for (const code of dayCampusCodes.value) {
    map.set(code, 0);
  }
  map.set('', 0);
  for (const slot of daySlots.value) {
    const key = slot.campusCode || '';
    const remaining = slot.remaining ?? Math.max(0, (slot.capacity ?? 0) - (slot.bookedCount ?? 0));
    map.set(key, (map.get(key) ?? 0) + remaining);
  }
  return map;
});

const slotTableRows = computed((): SlotTableRow[] => {
  const groups = new Map<string, HcDaySlotDto[]>();
  for (const slot of daySlots.value) {
    const timeKey = `${slot.startTime ?? ''}-${slot.endTime ?? ''}`;
    const bucket = groups.get(timeKey) ?? [];
    bucket.push(slot);
    groups.set(timeKey, bucket);
  }
  return [...groups.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([timeKey, slots]) => {
      const slotsByColumn: Record<string, HcDaySlotDto | undefined> = {};
      for (const slot of slots) {
        slotsByColumn[buildSlotColumnKey(slot.contractTypeCode, slot.campusCode)] = slot;
      }
      return {
        timeKey,
        startTime: slots[0]?.startTime,
        endTime: slots[0]?.endTime,
        slotsByColumn,
      };
    });
});

/** 当日号源汇总 */
const daySlotStats = computed(() => {
  let capacity = 0;
  let booked = 0;
  let openCount = 0;
  for (const slot of daySlots.value) {
    capacity += slot.capacity ?? 0;
    booked += slot.bookedCount ?? 0;
    if (slot.openFlag) openCount += 1;
  }
  return {
    slotCount: daySlots.value.length,
    capacity,
    booked,
    remaining: Math.max(0, capacity - booked),
    openCount,
  };
});

/** 批量加档预估新建档位数（不含已存在跳过） */
const batchPreviewCount = computed(() => {
  if (!batchAddingSlot.value) return 0;
  const start = normalizeTimeInput(batchAddForm.value.windowStart);
  const end = normalizeTimeInput(batchAddForm.value.windowEnd);
  const minutes = batchAddForm.value.slotMinutes || 0;
  const slices = countTimeSlices(start, end, minutes);
  const typeCount = batchAddForm.value.contractTypeCodes.length;
  const campusCount = batchHasCampus.value
    ? batchCampusRows.value.filter((row) => row.selected).length
    : 1;
  return slices * typeCount * Math.max(campusCount, 0);
});

const isTodaySelected = computed(() => selectedDate.value === todayLocal.date);

const { run: runMonth, loading: monthLoading } = useRequest(loadMonthOverviewApi, {
  manual: true,
  onSuccess: (res) => {
    monthCells.value = buildMonthCells(yearMonth.value, res?.days ?? []);
  },
});

const { run: runDayDetail, loading: dayLoading } = useRequest(loadDayDetailApi, {
  manual: true,
  onSuccess: (res) => {
    dayRestFlag.value = !!res?.plan?.restFlag;
    dayTypeCodes.value = (res?.dayTypes ?? [])
      .map((row) => row.contractTypeCode)
      .filter((code): code is string => !!code);
    daySlots.value = res?.slots ?? [];
  },
});

/** 组合列键（类型 + 校区）。 */
function buildSlotColumnKey(contractTypeCode?: string, campusCode?: string) {
  return `${contractTypeCode ?? ''}|${campusCode ?? ''}`;
}

/** 列头文案（扁平列）。 */
function slotColumnLabel(columnKey: string) {
  const [typeCode, campusCode] = columnKey.split('|');
  const typeLabel = typeNameMap.value.get(typeCode) ?? typeCode;
  if (!campusCode) {
    return useCampusGroupedColumns.value ? `${typeLabel} · 未分校区` : typeLabel;
  }
  const campusLabel = campusNameMap.value.get(campusCode) ?? campusCode;
  return `${typeLabel} · ${campusLabel}`;
}

function campusFilterLabel(code: string) {
  return campusNameMap.value.get(code) ?? code;
}

/** 格式化时刻展示 HH:mm。 */
function formatTimeLabel(value?: string) {
  if (!value) return '';
  return value.length >= 5 ? value.slice(0, 5) : value;
}

/** 月历日状态文案。 */
function dayStatusLabel(summary?: HcDaySummaryDto) {
  if (!summary || summary.status === 'EMPTY') return '无计划';
  if (summary.restFlag || summary.status === 'REST') return '休息';
  if (summary.status === 'FULL') return '约满';
  return `余 ${summary.remaining ?? 0}`;
}

/** 月历日状态 Tag 类型。 */
function dayStatusTagType(summary?: HcDaySummaryDto): 'info' | 'warning' | 'danger' | 'success' {
  if (!summary || summary.status === 'EMPTY') return 'info';
  if (summary.restFlag || summary.status === 'REST') return 'warning';
  if (summary.status === 'FULL') return 'danger';
  return 'success';
}

/** 月历日 CSS 状态类。 */
function dayStatusClass(summary?: HcDaySummaryDto) {
  if (!summary || summary.status === 'EMPTY') return 'is-empty';
  if (summary.restFlag || summary.status === 'REST') return 'is-rest';
  if (summary.status === 'FULL') return 'is-full';
  return 'is-open';
}

/** 构建当月日列表（单列，不含前后月占位）。 */
function buildMonthCells(month: string, days: HcDaySummaryDto[]): MonthCell[] {
  const [year, monthNum] = month.split('-').map(Number);
  if (!year || !monthNum) return [];
  const dayMap = new Map<string, HcDaySummaryDto>();
  for (const row of days) {
    if (row.planDate) dayMap.set(row.planDate, row);
  }
  const lastDate = new Date(year, monthNum, 0).getDate();
  const cells: MonthCell[] = [];
  for (let d = 1; d <= lastDate; d += 1) {
    const iso = `${month}-${String(d).padStart(2, '0')}`;
    cells.push({ date: iso, inMonth: true, summary: dayMap.get(iso) });
  }
  return cells;
}

/** 日期对应星期文案（周一=一）。 */
function weekdayLabel(dateIso: string) {
  const [y, m, d] = dateIso.split('-').map(Number);
  if (!y || !m || !d) return '';
  const isoWeekday = new Date(y, m - 1, d).getDay();
  const index = isoWeekday === 0 ? 6 : isoWeekday - 1;
  return WEEKDAY_LABELS[index] ?? '';
}

function scrollSelectedDayIntoView() {
  void nextTick(() => {
    const root = monthListRef.value;
    if (!root) return;
    const active = root.querySelector('.hc-calendar-page__day.is-selected') as HTMLElement | null;
    active?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  });
}

function toIsoDate(date: Date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function yearMonthOf(dateIso: string) {
  return dateIso.slice(0, 7);
}

function shiftIsoDate(dateIso: string, deltaDays: number) {
  const [y, m, d] = dateIso.split('-').map(Number);
  if (!y || !m || !d) return dateIso;
  const next = new Date(y, m - 1, d + deltaDays);
  return toIsoDate(next);
}

async function loadContractTypes() {
  contractTypes.value = (await listContractTypesApi()) ?? [];
}

async function loadCampusSites() {
  try {
    const res = await listActiveCampusSitesApi({});
    const map = new Map<string, string>();
    for (const row of res?.records ?? []) {
      if (!row.campusCode) continue;
      map.set(row.campusCode, row.campusName || row.shortName || row.campusCode);
    }
    campusNameMap.value = map;
  } catch {
    campusNameMap.value = new Map();
  }
}

async function loadOrgTemplateMeta() {
  if (!organizationId.value) {
    orgCampusCodes.value = [];
    return;
  }
  try {
    const tpl = await getSlotTemplateApi({ organizationId: organizationId.value });
    orgCampusCodes.value = [...(tpl?.campusCodes ?? [])];
  } catch {
    orgCampusCodes.value = [];
  }
}

async function refreshAll() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录机构');
    return;
  }
  if (!yearMonth.value) {
    yearMonth.value = yearMonthOf(selectedDate.value) || toIsoDate(new Date()).slice(0, 7);
  }
  await Promise.all([
    runMonth({ organizationId: organizationId.value, yearMonth: yearMonth.value }),
    runDayDetail({ organizationId: organizationId.value, planDate: selectedDate.value }),
    loadOrgTemplateMeta(),
  ]);
}

function goToday() {
  const today = formatLocalDate(new Date());
  if (yearMonth.value !== today.yearMonth) {
    yearMonth.value = today.yearMonth;
  }
  selectDay(today.date);
}

async function loadDayDetailOnly() {
  if (!organizationId.value || !selectedDate.value) return;
  await runDayDetail({ organizationId: organizationId.value, planDate: selectedDate.value });
}

function selectDay(date: string) {
  selectedDate.value = date;
  addingSlot.value = false;
  batchAddingSlot.value = false;
  const nextMonth = yearMonthOf(date);
  if (nextMonth && nextMonth !== yearMonth.value) {
    yearMonth.value = nextMonth;
  }
  void loadDayDetailOnly();
  scrollSelectedDayIntoView();
}

/** 日明细切换前一天 / 下一天；跨月时同步月历。 */
function shiftSelectedDay(deltaDays: number) {
  if (!selectedDate.value) return;
  const nextDate = shiftIsoDate(selectedDate.value, deltaDays);
  selectDay(nextDate);
}

/** 打开模板设置并回填机构模板。 */
async function openTemplateDialog() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录机构');
    return;
  }
  const tpl = await getSlotTemplateApi({ organizationId: organizationId.value });
  const capacityByType: Record<string, number> = {};
  for (const row of contractTypes.value) {
    if (!row.typeCode) continue;
    capacityByType[row.typeCode] = tpl?.capacityByType?.[row.typeCode] ?? 0;
  }
  let campusCodes = [...(tpl?.campusCodes ?? [])];
  if (campusCodes.length === 0 && campusOptions.value.length > 0) {
    campusCodes = campusOptions.value.map((row) => row.code);
  }
  templateForm.value = {
    slotMinutes: tpl?.slotMinutes ?? 30,
    amStart: tpl?.amStart ?? '08:00:00',
    amEnd: tpl?.amEnd ?? '12:00:00',
    pmStart: tpl?.pmStart ?? '14:00:00',
    pmEnd: tpl?.pmEnd ?? '17:00:00',
    capacityByType,
    restWeekdays: [...(tpl?.restWeekdays ?? [])],
    campusCodes,
  };
  templateVisible.value = true;
}

async function handleSaveTemplate() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录机构');
    return;
  }
  if (!templateForm.value.slotMinutes || templateForm.value.slotMinutes <= 0) {
    ElMessage.warning('请填写时段间隔');
    return;
  }
  const campusCodes = [...(templateForm.value.campusCodes ?? [])];
  await saveSlotTemplateApi({
    organizationId: organizationId.value,
    slotMinutes: templateForm.value.slotMinutes,
    amStart: templateForm.value.amStart,
    amEnd: templateForm.value.amEnd,
    pmStart: templateForm.value.pmStart,
    pmEnd: templateForm.value.pmEnd,
    capacityByType: templateForm.value.capacityByType,
    restWeekdays: templateForm.value.restWeekdays,
    campusCodes,
  });
  ElMessage.success('模板已保存');
  templateVisible.value = false;
  await loadOrgTemplateMeta();
}

async function handleGenerateFutureDays() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录机构');
    return;
  }
  if (!generateDays.value || generateDays.value < 1) {
    ElMessage.warning('请填写生成天数');
    return;
  }
  if (generateLoading.value) {
    return;
  }
  generateLoading.value = true;
  try {
    const tpl = await getSlotTemplateApi({ organizationId: organizationId.value });
    if (!tpl?.slotMinutes || tpl.slotMinutes <= 0) {
      ElMessage.warning('请先在「模板设置」中保存号源模板，再生成日计划');
      return;
    }
    if (!tpl.amStart && !tpl.pmStart) {
      ElMessage.warning('模板未配置上午/下午开放时段，请先完善模板设置');
      return;
    }
    const res = await generateFutureDaysApi({
      organizationId: organizationId.value,
      days: generateDays.value,
      fromDate: generateFromDate.value || undefined,
    });
    ElMessage.success(`已生成 ${res?.generatedDayCount ?? 0} 天计划`);
    generateVisible.value = false;
    await refreshAll();
  } finally {
    generateLoading.value = false;
  }
}

async function handleCopyDayPlan() {
  if (!organizationId.value || !selectedDate.value) return;
  if (copyTargetDates.value.length === 0) {
    ElMessage.warning('请选择目标日期');
    return;
  }
  await copyDayPlanApi({
    organizationId: organizationId.value,
    sourceDate: selectedDate.value,
    targetDates: copyTargetDates.value,
  });
  ElMessage.success('日计划已复制');
  copyVisible.value = false;
  copyTargetDates.value = [];
  await refreshAll();
}

async function handleDayRestChange(restFlag: boolean) {
  if (!organizationId.value || !selectedDate.value) return;
  await setDayRestApi({
    organizationId: organizationId.value,
    planDate: selectedDate.value,
    restFlag,
  });
  await refreshAll();
}

async function handleDayTypesChange(codes: string[]) {
  if (!organizationId.value || !selectedDate.value) return;
  await setDayContractTypesApi({
    organizationId: organizationId.value,
    planDate: selectedDate.value,
    contractTypeCodes: codes,
  });
  await loadDayDetailOnly();
}

async function handleSlotOpenChange(slot: HcDaySlotDto, openFlag: boolean) {
  if (!slot.id) return;
  await setSlotOpenApi({ daySlotId: slot.id, openFlag });
  slot.openFlag = openFlag;
}

async function handleInlineCapacityChange(slot: HcDaySlotDto, value: number | undefined) {
  if (!slot.id) return;
  const slotId = String(slot.id);
  const booked = slot.bookedCount ?? 0;
  const next = value ?? 0;
  if (next < booked) {
    ElMessage.warning(`容量不得小于已约人数（${booked}）`);
    return;
  }
  if (next === (slot.capacity ?? 0)) {
    return;
  }
  if (capacitySavingIds.value.has(slotId)) {
    return;
  }
  capacitySavingIds.value = new Set(capacitySavingIds.value).add(slotId);
  try {
    await updateSlotCapacityApi({ daySlotId: slot.id, newCapacity: next });
    slot.capacity = next;
    slot.remaining = Math.max(0, next - booked);
    if (organizationId.value) {
      await runMonth({ organizationId: organizationId.value, yearMonth: yearMonth.value });
    }
  } finally {
    const nextSet = new Set(capacitySavingIds.value);
    nextSet.delete(slotId);
    capacitySavingIds.value = nextSet;
  }
}

async function handleDeleteSlot(slot: HcDaySlotDto) {
  if (!slot.id) return;
  try {
    await ElMessageBox.confirm('确认删除该档位？', '删除档位');
    await deleteDaySlotApi({ daySlotId: slot.id });
    ElMessage.success('已删除');
    await refreshAll();
  } catch {
    // cancel
  }
}

function normalizeTimeInput(value: string) {
  const trimmed = (value || '').trim();
  if (/^\d{1,2}:\d{2}$/.test(trimmed)) {
    const parts = trimmed.split(':');
    const h = parts[0] ?? '0';
    const m = parts[1] ?? '00';
    return `${h.padStart(2, '0')}:${m.padStart(2, '0')}:00`;
  }
  if (/^\d{1,2}:\d{2}:\d{2}$/.test(trimmed)) {
    const parts = trimmed.split(':');
    const h = parts[0] ?? '0';
    const m = parts[1] ?? '00';
    const s = parts[2] ?? '00';
    return `${h.padStart(2, '0')}:${m.padStart(2, '0')}:${s.padStart(2, '0')}`;
  }
  return trimmed;
}

/** 按间隔切分开放窗，返回档位数。 */
function countTimeSlices(startTime: string, endTime: string, slotMinutes: number) {
  if (!startTime || !endTime || slotMinutes <= 0) return 0;
  const toMinutes = (value: string) => {
    const parts = value.split(':').map(Number);
    const h = parts[0] ?? 0;
    const m = parts[1] ?? 0;
    return h * 60 + m;
  };
  const start = toMinutes(startTime);
  const end = toMinutes(endTime);
  if (end <= start) return 0;
  let count = 0;
  for (let cursor = start; cursor + slotMinutes <= end; cursor += slotMinutes) {
    count += 1;
  }
  return count;
}

function defaultAddSlotTypeCode() {
  return (
    dayTypeCodes.value[0] ||
    addSlotTypeOptions.value[0]?.typeCode ||
    contractTypes.value[0]?.typeCode ||
    ''
  );
}

function startAddSlot(prefill?: {
  startTime?: string;
  endTime?: string;
  contractTypeCode?: string;
  campusCode?: string;
}) {
  batchAddingSlot.value = false;
  addSlotForm.value = {
    startTime: prefill?.startTime || '08:00:00',
    endTime: prefill?.endTime || '08:30:00',
    contractTypeCode: prefill?.contractTypeCode || defaultAddSlotTypeCode(),
    campusCode: prefill?.campusCode || dayCampusCodes.value[0] || '',
    capacity: 0,
    openFlag: true,
  };
  addingSlot.value = true;
}

function startAddSlotFromCell(row: SlotTableRow, columnKey: string) {
  const [typeCode, campusCode] = columnKey.split('|');
  startAddSlot({
    startTime: row.startTime,
    endTime: row.endTime,
    contractTypeCode: typeCode,
    campusCode: campusCode || '',
  });
}

function cancelAddSlot() {
  addingSlot.value = false;
}

async function handleAddSlot() {
  if (!organizationId.value || !selectedDate.value) return;
  if (!addSlotForm.value.contractTypeCode) {
    ElMessage.warning('请选择合同类型');
    return;
  }
  const startTime = normalizeTimeInput(addSlotForm.value.startTime);
  const endTime = normalizeTimeInput(addSlotForm.value.endTime);
  if (!startTime || !endTime) {
    ElMessage.warning('请填写开始/结束时间');
    return;
  }
  addSlotSaving.value = true;
  try {
    await addDaySlotApi({
      organizationId: organizationId.value,
      planDate: selectedDate.value,
      startTime,
      endTime,
      contractTypeCode: addSlotForm.value.contractTypeCode,
      campusCode: addSlotForm.value.campusCode || undefined,
      capacity: addSlotForm.value.capacity,
      openFlag: addSlotForm.value.openFlag,
    });
    ElMessage.success('档位已添加');
    addingSlot.value = false;
    await refreshAll();
  } finally {
    addSlotSaving.value = false;
  }
}

async function startBatchAddSlot() {
  addingSlot.value = false;
  let windowStart = '08:00:00';
  let windowEnd = '12:00:00';
  let slotMinutes = 30;
  let templateCampusCodes: string[] = [];
  if (organizationId.value) {
    try {
      const tpl = await getSlotTemplateApi({ organizationId: organizationId.value });
      if (tpl?.amStart) windowStart = tpl.amStart;
      if (tpl?.amEnd) windowEnd = tpl.amEnd;
      if (tpl?.slotMinutes && tpl.slotMinutes > 0) slotMinutes = tpl.slotMinutes;
      templateCampusCodes = tpl?.campusCodes ?? [];
    } catch {
      // 无模板时用默认值
    }
  }
  const typeCodes =
    dayTypeCodes.value.length > 0
      ? [...dayTypeCodes.value]
      : addSlotTypeOptions.value.map((row) => row.typeCode!).filter(Boolean);
  batchAddForm.value = {
    windowStart,
    windowEnd,
    slotMinutes,
    contractTypeCodes: typeCodes,
    capacity: 0,
    openFlag: true,
  };
  batchCampusRows.value = buildBatchCampusRows(templateCampusCodes);
  batchAddingSlot.value = true;
}

/** 组装批量加档校区行：模板校区优先，否则用当日/启用校区。 */
function buildBatchCampusRows(templateCampusCodes: string[]) {
  const codes =
    templateCampusCodes.length > 0
      ? templateCampusCodes
      : dayCampusCodes.value.length > 0
        ? dayCampusCodes.value
        : campusOptions.value.map((row) => row.code);
  return codes
    .map((code) => code.trim())
    .filter(Boolean)
    .map((code) => ({
      code,
      name: campusNameMap.value.get(code) ?? code,
      selected: true,
      capacity: 0,
    }));
}

function cancelBatchAddSlot() {
  batchAddingSlot.value = false;
}

async function handleBatchAddSlot() {
  if (!organizationId.value || !selectedDate.value) return;
  if (!batchAddForm.value.contractTypeCodes.length) {
    ElMessage.warning('请至少选择一种合同类型');
    return;
  }
  const windowStart = normalizeTimeInput(batchAddForm.value.windowStart);
  const windowEnd = normalizeTimeInput(batchAddForm.value.windowEnd);
  if (!windowStart || !windowEnd) {
    ElMessage.warning('请填写开放窗开始/结束时间');
    return;
  }
  if (!batchAddForm.value.slotMinutes || batchAddForm.value.slotMinutes <= 0) {
    ElMessage.warning('请填写有效的时段间隔');
    return;
  }
  const selectedCampuses = batchCampusRows.value.filter((row) => row.selected);
  if (batchHasCampus.value && selectedCampuses.length === 0) {
    ElMessage.warning('请至少选择一个校区');
    return;
  }
  const campusCodes = selectedCampuses.map((row) => row.code);
  const capacityByCampus: Record<string, number> = {};
  for (const row of selectedCampuses) {
    capacityByCampus[row.code] = row.capacity ?? 0;
  }
  batchAddSaving.value = true;
  try {
    const res = await batchAddDaySlotsApi({
      organizationId: organizationId.value,
      planDate: selectedDate.value,
      windowStart,
      windowEnd,
      slotMinutes: batchAddForm.value.slotMinutes,
      contractTypeCodes: batchAddForm.value.contractTypeCodes,
      campusCodes: campusCodes.length > 0 ? campusCodes : undefined,
      capacityByCampus: campusCodes.length > 0 ? capacityByCampus : undefined,
      capacity: batchAddForm.value.capacity,
      openFlag: batchAddForm.value.openFlag,
    });
    const created = res?.createdCount ?? 0;
    const skipped = res?.skippedCount ?? 0;
    ElMessage.success(`批量加档完成：新建 ${created}，跳过已存在 ${skipped}`);
    batchAddingSlot.value = false;
    await refreshAll();
  } finally {
    batchAddSaving.value = false;
  }
}

function openCopyDialog() {
  copyTargetDates.value = [];
  copyVisible.value = true;
}

function openGenerateDialog() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录机构');
    return;
  }
  generateFromDate.value = '';
  generateDays.value = 30;
  generateVisible.value = true;
}

watch(yearMonth, (value) => {
  if (!organizationId.value || !value) return;
  void runMonth({ organizationId: organizationId.value, yearMonth: value }).then(() => {
    scrollSelectedDayIntoView();
  });
});

watch(organizationId, () => {
  void refreshAll();
});

onMounted(async () => {
  await Promise.all([loadContractTypes(), loadCampusSites()]);
  await refreshAll();
  scrollSelectedDayIntoView();
});
</script>

<template>
  <Page auto-content-height>
    <div class="hc-calendar-page">
      <div class="hc-calendar-page__toolbar">
        <ElDatePicker
          v-model="yearMonth"
          type="month"
          value-format="YYYY-MM"
          placeholder="选择月份"
          :clearable="false"
          style="width: 140px"
        />
        <ElButton :disabled="isTodaySelected" @click="goToday">今天</ElButton>
        <div class="hc-calendar-page__toolbar-spacer" />
        <ElButton @click="openTemplateDialog">模板设置</ElButton>
        <ElButton type="primary" plain @click="openGenerateDialog">生成未来N天</ElButton>
        <ElButton :disabled="!selectedDate" @click="openCopyDialog">复制日计划</ElButton>
        <ElButton :icon="Refresh" :loading="monthLoading || dayLoading" @click="refreshAll">
          刷新
        </ElButton>
      </div>

      <div class="hc-calendar-page__body">
        <aside v-loading="monthLoading" ref="monthListRef" class="hc-calendar-page__month">
          <div class="hc-calendar-page__month-title">{{ yearMonth }} 日列表</div>
          <div class="hc-calendar-page__day-list">
            <button
              v-for="cell in monthCells"
              :key="cell.date"
              type="button"
              class="hc-calendar-page__day"
              :class="[
                dayStatusClass(cell.summary),
                {
                  'is-selected': cell.date === selectedDate,
                  'is-today': cell.date === todayLocal.date,
                },
              ]"
              @click="selectDay(cell.date)"
            >
              <span class="hc-calendar-page__day-main">
                <span class="hc-calendar-page__day-date">{{ cell.date.slice(8) }}</span>
                <span class="hc-calendar-page__day-week">周{{ weekdayLabel(cell.date) }}</span>
                <span v-if="cell.date === todayLocal.date" class="hc-calendar-page__day-today"
                  >今天</span
                >
              </span>
              <ElTag effect="plain" size="small" :type="dayStatusTagType(cell.summary)">
                {{ dayStatusLabel(cell.summary) }}
              </ElTag>
            </button>
          </div>
        </aside>

        <section class="hc-calendar-page__detail">
          <div class="hc-calendar-page__detail-head">
            <div class="hc-calendar-page__detail-nav">
              <ElButton :icon="ArrowLeft" circle size="small" @click="shiftSelectedDay(-1)" />
              <div class="hc-calendar-page__detail-title-wrap">
                <div class="hc-calendar-page__detail-title">
                  {{ selectedDate }}
                  <ElTag v-if="isTodaySelected" size="small" type="primary" effect="plain"
                    >今天</ElTag
                  >
                </div>
                <div class="hc-calendar-page__detail-sub">
                  周{{ weekdayLabel(selectedDate) }}
                  <template v-if="daySlotStats.slotCount > 0">
                    · {{ daySlotStats.slotCount }} 档 · 余 {{ daySlotStats.remaining }} （已约
                    {{ daySlotStats.booked }}）
                  </template>
                  <template v-else> · 暂无时段</template>
                </div>
              </div>
              <ElButton :icon="ArrowRight" circle size="small" @click="shiftSelectedDay(1)" />
            </div>

            <div class="hc-calendar-page__detail-meta">
              <div class="hc-calendar-page__meta-row">
                <span class="hc-calendar-page__meta-label">开放类型</span>
                <ElCheckboxGroup
                  v-model="dayTypeCodes"
                  class="hc-calendar-page__types"
                  @change="handleDayTypesChange"
                >
                  <ElCheckbox
                    v-for="item in contractTypes"
                    :key="item.typeCode"
                    :value="item.typeCode"
                  >
                    {{ item.typeName }}
                  </ElCheckbox>
                </ElCheckboxGroup>
              </div>
              <div v-if="useCampusGroupedColumns" class="hc-calendar-page__meta-row">
                <span class="hc-calendar-page__meta-label">校区</span>
                <div class="hc-calendar-page__campus-filter">
                  <button
                    type="button"
                    class="hc-calendar-page__campus-chip"
                    :class="{ 'is-active': campusFilter === 'ALL' }"
                    @click="campusFilter = 'ALL'"
                  >
                    全部校区
                  </button>
                  <button
                    v-for="code in dayCampusCodes"
                    :key="code"
                    type="button"
                    class="hc-calendar-page__campus-chip"
                    :class="{ 'is-active': campusFilter === code }"
                    @click="campusFilter = code"
                  >
                    {{ campusFilterLabel(code) }}
                    <span class="hc-calendar-page__campus-chip-rem">
                      余{{ campusRemainingStats.get(code) ?? 0 }}
                    </span>
                  </button>
                  <button
                    v-if="hasUnassignedCampusSlots"
                    type="button"
                    class="hc-calendar-page__campus-chip"
                    :class="{ 'is-active': campusFilter === '__NONE__' }"
                    @click="campusFilter = '__NONE__'"
                  >
                    未分校区
                    <span class="hc-calendar-page__campus-chip-rem">
                      余{{ campusRemainingStats.get('') ?? 0 }}
                    </span>
                  </button>
                </div>
              </div>
            </div>

            <div class="hc-calendar-page__detail-actions">
              <ElButton
                type="primary"
                size="small"
                :disabled="addingSlot || batchAddingSlot"
                @click="startAddSlot()"
              >
                加档
              </ElButton>
              <ElButton
                size="small"
                :disabled="addingSlot || batchAddingSlot"
                @click="startBatchAddSlot"
              >
                批量加档
              </ElButton>
              <ElSwitch
                v-model="dayRestFlag"
                active-text="整日休息"
                inactive-text="开放"
                @change="handleDayRestChange"
              />
            </div>
          </div>

          <div v-loading="dayLoading" class="hc-calendar-page__detail-body">
            <div v-if="dayRestFlag" class="hc-calendar-page__rest-banner">
              当日为整日休息，窗口与小程序不可约；仍可维护类型与时段配置。
            </div>

            <div class="hc-calendar-page__section hc-calendar-page__section--slots">
              <div class="hc-calendar-page__slots-head">
                <div class="hc-calendar-page__section-title">时段配额</div>
                <div class="hc-calendar-page__slots-hint">
                  <template v-if="useCampusGroupedColumns">
                    表头按校区分组；筛选见日期右侧，空格「+」可补该校区档位。
                  </template>
                  <template v-else>
                    行=时段，列=类型；空格点「+」可快速补档，容量可直接改。
                  </template>
                </div>
              </div>

              <div
                v-if="hasUnassignedCampusSlots && useCampusGroupedColumns"
                class="hc-calendar-page__campus-tip"
              >
                当前有部分档位未标注校区。请用「批量加档」勾选净月/自由等校区并分别设容量，或在对应校区列点「+」补档。
              </div>

              <div
                v-if="slotTableRows.length === 0 && !addingSlot && !batchAddingSlot"
                class="hc-calendar-page__empty"
              >
                <p>当日还没有时段配额。</p>
                <div class="hc-calendar-page__empty-actions">
                  <ElButton type="primary" size="small" @click="startBatchAddSlot"
                    >批量加档</ElButton
                  >
                  <ElButton size="small" @click="startAddSlot()">单档添加</ElButton>
                  <ElButton size="small" @click="openGenerateDialog">生成未来N天</ElButton>
                </div>
              </div>

              <ElTable
                v-else
                :data="slotTableRows"
                border
                stripe
                size="small"
                max-height="520"
                empty-text="当日暂无时段"
                class="hc-calendar-page__slot-table"
              >
                <ElTableColumn label="时段" width="118" fixed>
                  <template #default="{ row }">
                    <span class="hc-calendar-page__time-label">
                      {{ formatTimeLabel(row.startTime) }}-{{ formatTimeLabel(row.endTime) }}
                    </span>
                  </template>
                </ElTableColumn>

                <template v-if="useCampusGroupedColumns">
                  <ElTableColumn
                    v-for="group in campusColumnGroups"
                    :key="group.campusCode || '__none__'"
                    :label="group.campusLabel"
                    align="center"
                  >
                    <ElTableColumn
                      v-for="col in group.typeColumns"
                      :key="col.colKey"
                      :label="col.typeLabel"
                      min-width="220"
                    >
                      <template #default="{ row }">
                        <template v-if="row.slotsByColumn[col.colKey]">
                          <div class="hc-calendar-page__slot-cell">
                            <ElInputNumber
                              :model-value="row.slotsByColumn[col.colKey]?.capacity ?? 0"
                              :min="row.slotsByColumn[col.colKey]?.bookedCount ?? 0"
                              :disabled="
                                !!row.slotsByColumn[col.colKey]?.id &&
                                capacitySavingIds.has(String(row.slotsByColumn[col.colKey]!.id))
                              "
                              size="small"
                              controls-position="right"
                              class="hc-calendar-page__slot-capacity"
                              @change="
                                (val: number | undefined) =>
                                  row.slotsByColumn[col.colKey] &&
                                  handleInlineCapacityChange(row.slotsByColumn[col.colKey]!, val)
                              "
                            />
                            <span class="hc-calendar-page__slot-booked">
                              已约{{ row.slotsByColumn[col.colKey]?.bookedCount ?? 0 }}/余{{
                                row.slotsByColumn[col.colKey]?.remaining ?? 0
                              }}
                            </span>
                            <ElSwitch
                              :model-value="!!row.slotsByColumn[col.colKey]?.openFlag"
                              size="small"
                              inline-prompt
                              active-text="开"
                              inactive-text="关"
                              class="hc-calendar-page__slot-switch"
                              @change="
                                (val: boolean) =>
                                  row.slotsByColumn[col.colKey] &&
                                  handleSlotOpenChange(row.slotsByColumn[col.colKey]!, val)
                              "
                            />
                            <ElButton
                              type="danger"
                              link
                              size="small"
                              @click="handleDeleteSlot(row.slotsByColumn[col.colKey]!)"
                            >
                              删
                            </ElButton>
                          </div>
                        </template>
                        <ElButton
                          v-else
                          type="primary"
                          link
                          size="small"
                          class="hc-calendar-page__slot-empty-add"
                          @click="startAddSlotFromCell(row, col.colKey)"
                        >
                          +
                        </ElButton>
                      </template>
                    </ElTableColumn>
                  </ElTableColumn>
                </template>

                <template v-else>
                  <ElTableColumn
                    v-for="colKey in flatTypeColumnKeys"
                    :key="colKey"
                    :label="slotColumnLabel(colKey)"
                    min-width="240"
                  >
                    <template #default="{ row }">
                      <template v-if="row.slotsByColumn[colKey]">
                        <div class="hc-calendar-page__slot-cell">
                          <ElInputNumber
                            :model-value="row.slotsByColumn[colKey]?.capacity ?? 0"
                            :min="row.slotsByColumn[colKey]?.bookedCount ?? 0"
                            :disabled="
                              !!row.slotsByColumn[colKey]?.id &&
                              capacitySavingIds.has(String(row.slotsByColumn[colKey]!.id))
                            "
                            size="small"
                            controls-position="right"
                            class="hc-calendar-page__slot-capacity"
                            @change="
                              (val: number | undefined) =>
                                row.slotsByColumn[colKey] &&
                                handleInlineCapacityChange(row.slotsByColumn[colKey]!, val)
                            "
                          />
                          <span class="hc-calendar-page__slot-booked">
                            已约{{ row.slotsByColumn[colKey]?.bookedCount ?? 0 }}/余{{
                              row.slotsByColumn[colKey]?.remaining ?? 0
                            }}
                          </span>
                          <ElSwitch
                            :model-value="!!row.slotsByColumn[colKey]?.openFlag"
                            size="small"
                            inline-prompt
                            active-text="开"
                            inactive-text="关"
                            class="hc-calendar-page__slot-switch"
                            @change="
                              (val: boolean) =>
                                row.slotsByColumn[colKey] &&
                                handleSlotOpenChange(row.slotsByColumn[colKey]!, val)
                            "
                          />
                          <ElButton
                            type="danger"
                            link
                            size="small"
                            @click="handleDeleteSlot(row.slotsByColumn[colKey]!)"
                          >
                            删
                          </ElButton>
                        </div>
                      </template>
                      <ElButton
                        v-else
                        type="primary"
                        link
                        size="small"
                        class="hc-calendar-page__slot-empty-add"
                        @click="startAddSlotFromCell(row, colKey)"
                      >
                        +
                      </ElButton>
                    </template>
                  </ElTableColumn>
                </template>
              </ElTable>

              <div v-if="addingSlot" class="hc-calendar-page__add-row">
                <ElTimePicker
                  v-model="addSlotForm.startTime"
                  size="small"
                  value-format="HH:mm:ss"
                  format="HH:mm"
                  placeholder="开始"
                  class="hc-calendar-page__add-time-picker"
                />
                <span class="hc-calendar-page__add-sep">-</span>
                <ElTimePicker
                  v-model="addSlotForm.endTime"
                  size="small"
                  value-format="HH:mm:ss"
                  format="HH:mm"
                  placeholder="结束"
                  class="hc-calendar-page__add-time-picker"
                />
                <ElSelect
                  v-model="addSlotForm.contractTypeCode"
                  size="small"
                  placeholder="类型"
                  class="hc-calendar-page__add-type"
                >
                  <ElOption
                    v-for="item in addSlotTypeOptions"
                    :key="item.typeCode"
                    :label="item.typeName"
                    :value="item.typeCode!"
                  />
                </ElSelect>
                <ElSelect
                  v-if="showCampusColumn"
                  v-model="addSlotForm.campusCode"
                  size="small"
                  clearable
                  filterable
                  placeholder="校区"
                  class="hc-calendar-page__add-campus-select"
                >
                  <ElOption
                    v-for="item in campusOptions"
                    :key="item.code"
                    :label="item.name"
                    :value="item.code"
                  />
                </ElSelect>
                <ElInputNumber
                  v-model="addSlotForm.capacity"
                  size="small"
                  :min="0"
                  controls-position="right"
                  class="hc-calendar-page__slot-capacity"
                />
                <ElSwitch
                  v-model="addSlotForm.openFlag"
                  size="small"
                  inline-prompt
                  active-text="开"
                  inactive-text="关"
                />
                <ElButton
                  type="primary"
                  size="small"
                  :loading="addSlotSaving"
                  @click="handleAddSlot"
                >
                  确定
                </ElButton>
                <ElButton size="small" :disabled="addSlotSaving" @click="cancelAddSlot">
                  取消
                </ElButton>
              </div>

              <div v-if="batchAddingSlot" class="hc-calendar-page__batch-panel">
                <div class="hc-calendar-page__add-row">
                  <span class="hc-calendar-page__add-label">开放窗</span>
                  <ElTimePicker
                    v-model="batchAddForm.windowStart"
                    size="small"
                    value-format="HH:mm:ss"
                    format="HH:mm"
                    placeholder="开始"
                    class="hc-calendar-page__add-time-picker"
                  />
                  <span class="hc-calendar-page__add-sep">-</span>
                  <ElTimePicker
                    v-model="batchAddForm.windowEnd"
                    size="small"
                    value-format="HH:mm:ss"
                    format="HH:mm"
                    placeholder="结束"
                    class="hc-calendar-page__add-time-picker"
                  />
                  <span class="hc-calendar-page__add-label">间隔</span>
                  <ElInputNumber
                    v-model="batchAddForm.slotMinutes"
                    size="small"
                    :min="5"
                    :max="120"
                    controls-position="right"
                    class="hc-calendar-page__batch-minutes"
                  />
                  <span class="hc-calendar-page__add-label">分</span>
                  <ElSelect
                    v-model="batchAddForm.contractTypeCodes"
                    size="small"
                    multiple
                    collapse-tags
                    collapse-tags-tooltip
                    placeholder="合同类型"
                    class="hc-calendar-page__batch-types"
                  >
                    <ElOption
                      v-for="item in addSlotTypeOptions"
                      :key="item.typeCode"
                      :label="item.typeName"
                      :value="item.typeCode!"
                    />
                  </ElSelect>
                  <template v-if="!batchHasCampus">
                    <span class="hc-calendar-page__add-label">容量</span>
                    <ElInputNumber
                      v-model="batchAddForm.capacity"
                      size="small"
                      :min="0"
                      controls-position="right"
                      class="hc-calendar-page__slot-capacity"
                    />
                  </template>
                  <ElSwitch
                    v-model="batchAddForm.openFlag"
                    size="small"
                    inline-prompt
                    active-text="开"
                    inactive-text="关"
                  />
                  <span v-if="batchPreviewCount > 0" class="hc-calendar-page__batch-preview">
                    约 {{ batchPreviewCount }} 档
                  </span>
                  <ElButton
                    type="primary"
                    size="small"
                    :loading="batchAddSaving"
                    :disabled="batchPreviewCount <= 0"
                    @click="handleBatchAddSlot"
                  >
                    确定
                  </ElButton>
                  <ElButton size="small" :disabled="batchAddSaving" @click="cancelBatchAddSlot">
                    取消
                  </ElButton>
                </div>
                <div v-if="batchHasCampus" class="hc-calendar-page__campus-caps">
                  <span class="hc-calendar-page__add-label">校区容量</span>
                  <div
                    v-for="row in batchCampusRows"
                    :key="row.code"
                    class="hc-calendar-page__campus-cap-item"
                  >
                    <ElCheckbox v-model="row.selected">{{ row.name }}</ElCheckbox>
                    <ElInputNumber
                      v-model="row.capacity"
                      size="small"
                      :min="0"
                      :disabled="!row.selected"
                      controls-position="right"
                      class="hc-calendar-page__slot-capacity"
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>

      <ElDialog v-model="templateVisible" title="号源模板设置" width="560px">
        <ElForm label-width="108px">
          <ElFormItem label="时段间隔(分)">
            <ElInputNumber v-model="templateForm.slotMinutes" :min="5" :max="120" />
          </ElFormItem>
          <ElFormItem label="上午">
            <div class="hc-calendar-page__time-range">
              <ElInput v-model="templateForm.amStart" placeholder="08:00:00" />
              <span>至</span>
              <ElInput v-model="templateForm.amEnd" placeholder="12:00:00" />
            </div>
          </ElFormItem>
          <ElFormItem label="下午">
            <div class="hc-calendar-page__time-range">
              <ElInput v-model="templateForm.pmStart" placeholder="14:00:00" />
              <span>至</span>
              <ElInput v-model="templateForm.pmEnd" placeholder="17:00:00" />
            </div>
          </ElFormItem>
          <ElFormItem label="默认容量">
            <div class="hc-calendar-page__capacity-grid">
              <div
                v-for="item in contractTypes"
                :key="item.typeCode"
                class="hc-calendar-page__capacity-item"
              >
                <span>{{ item.typeName }}</span>
                <ElInputNumber
                  :model-value="templateForm.capacityByType?.[item.typeCode!] ?? 0"
                  :min="0"
                  controls-position="right"
                  @update:model-value="
                    (val: number | undefined) => {
                      if (!templateForm.capacityByType) templateForm.capacityByType = {};
                      templateForm.capacityByType[item.typeCode!] = val ?? 0;
                    }
                  "
                />
              </div>
            </div>
          </ElFormItem>
          <ElFormItem label="模板休息日">
            <ElCheckboxGroup v-model="templateForm.restWeekdays">
              <ElCheckbox
                v-for="item in REST_WEEKDAY_OPTIONS"
                :key="item.value"
                :label="item.value"
              >
                {{ item.label }}
              </ElCheckbox>
            </ElCheckboxGroup>
          </ElFormItem>
          <ElFormItem label="校区">
            <ElSelect
              v-model="templateForm.campusCodes"
              multiple
              collapse-tags
              collapse-tags-tooltip
              filterable
              placeholder="选择参与号源分池的校区"
              style="width: 100%"
            >
              <ElOption
                v-for="item in campusOptions"
                :key="item.code"
                :label="item.name"
                :value="item.code"
              />
            </ElSelect>
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="templateVisible = false">取消</ElButton>
          <ElButton type="primary" @click="handleSaveTemplate">保存</ElButton>
        </template>
      </ElDialog>

      <ElDialog v-model="generateVisible" title="生成未来N天" width="420px">
        <ElForm label-width="96px">
          <ElFormItem label="生成天数" required>
            <ElInputNumber v-model="generateDays" :min="1" :max="365" />
          </ElFormItem>
          <ElFormItem label="起始日期">
            <ElDatePicker
              v-model="generateFromDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="默认从明天"
              style="width: 100%"
            />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton :disabled="generateLoading" @click="generateVisible = false">取消</ElButton>
          <ElButton type="primary" :loading="generateLoading" @click="handleGenerateFutureDays">
            生成
          </ElButton>
        </template>
      </ElDialog>

      <ElDialog v-model="copyVisible" title="复制日计划" width="480px">
        <p class="hc-calendar-page__copy-hint">源日期：{{ selectedDate }}</p>
        <ElForm label-width="88px">
          <ElFormItem label="目标日期" required>
            <ElDatePicker
              v-model="copyTargetDates"
              type="dates"
              value-format="YYYY-MM-DD"
              placeholder="可多选"
              style="width: 100%"
            />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="copyVisible = false">取消</ElButton>
          <ElButton type="primary" @click="handleCopyDayPlan">复制</ElButton>
        </template>
      </ElDialog>
    </div>
  </Page>
</template>

<style scoped>
.hc-calendar-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
  font-size: 15px;
}

.hc-calendar-page__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  font-size: 15px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.hc-calendar-page__toolbar-spacer {
  flex: 1;
  min-width: 8px;
}

.hc-calendar-page__body {
  display: grid;
  flex: 1;
  grid-template-columns: 268px minmax(0, 1fr);
  gap: 12px;
  min-height: 0;
}

.hc-calendar-page__month,
.hc-calendar-page__detail {
  min-height: 0;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.hc-calendar-page__month {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.hc-calendar-page__month-title {
  flex-shrink: 0;
  padding: 12px 12px 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.hc-calendar-page__day-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  padding: 8px;
  overflow: auto;
}

.hc-calendar-page__day {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 10px 12px;
  font-size: 15px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease;
}

.hc-calendar-page__day:hover {
  background: var(--el-fill-color-light);
}

.hc-calendar-page__day.is-selected {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.hc-calendar-page__day.is-today:not(.is-selected) {
  border-color: var(--el-color-primary-light-5);
}

.hc-calendar-page__day.is-rest:not(.is-selected) {
  background: var(--el-fill-color-lighter);
}

.hc-calendar-page__day.is-full:not(.is-selected) {
  background: var(--el-color-danger-light-9);
}

.hc-calendar-page__day-main {
  display: flex;
  gap: 8px;
  align-items: baseline;
  min-width: 0;
}

.hc-calendar-page__day-date {
  font-size: 20px;
  font-weight: 650;
  line-height: 1;
  color: var(--el-text-color-primary);
}

.hc-calendar-page__day-week {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.hc-calendar-page__day-today {
  padding: 0 6px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 999px;
}

.hc-calendar-page__detail {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.hc-calendar-page__detail-head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  align-items: flex-start;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.hc-calendar-page__detail-nav {
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
  align-items: center;
}

.hc-calendar-page__detail-title-wrap {
  min-width: 148px;
  text-align: center;
}

.hc-calendar-page__detail-title {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 650;
  line-height: 1.2;
  white-space: nowrap;
}

.hc-calendar-page__detail-sub {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.hc-calendar-page__detail-meta {
  display: flex;
  flex: 1 1 360px;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.hc-calendar-page__meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  align-items: center;
}

.hc-calendar-page__meta-label {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.hc-calendar-page__detail-actions {
  display: flex;
  flex: 0 1 auto;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-left: auto;
}

.hc-calendar-page__detail-body {
  flex: 1;
  padding: 14px 16px 18px;
  overflow: auto;
}

.hc-calendar-page__rest-banner {
  margin-bottom: 12px;
  padding: 10px 12px;
  font-size: 14px;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-7);
  border-radius: 6px;
}

.hc-calendar-page__section {
  margin-bottom: 16px;
}

.hc-calendar-page__section-title {
  margin-bottom: 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.hc-calendar-page__types {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  gap: 2px 10px;
  align-items: center;
  min-width: 0;
  font-size: 13px;
}

.hc-calendar-page__slots-head {
  margin-bottom: 8px;
}

.hc-calendar-page__slots-hint {
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

.hc-calendar-page__campus-filter {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.hc-calendar-page__campus-chip {
  display: inline-flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
  padding: 4px 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
  cursor: pointer;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 999px;
}

.hc-calendar-page__campus-chip:hover {
  border-color: var(--el-color-primary-light-5);
}

.hc-calendar-page__campus-chip.is-active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.hc-calendar-page__campus-chip-rem {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.hc-calendar-page__campus-chip.is-active .hc-calendar-page__campus-chip-rem {
  color: var(--el-color-primary);
}

.hc-calendar-page__campus-tip {
  margin-bottom: 8px;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-7);
  border-radius: 6px;
}

.hc-calendar-page__slots-head .hc-calendar-page__section-title {
  margin-bottom: 0;
}

.hc-calendar-page__empty {
  padding: 28px 16px;
  text-align: center;
  background: var(--el-fill-color-lighter);
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
}

.hc-calendar-page__empty p {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.hc-calendar-page__empty-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.hc-calendar-page__slot-table {
  width: 100%;
  font-size: 13px;
}

.hc-calendar-page__slot-table :deep(.el-table__cell) {
  padding: 4px 0;
}

.hc-calendar-page__time-label {
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.hc-calendar-page__slot-cell {
  display: flex;
  flex-wrap: nowrap;
  gap: 6px;
  align-items: center;
  min-height: 28px;
}

.hc-calendar-page__slot-capacity {
  width: 96px;
  flex-shrink: 0;
}

.hc-calendar-page__slot-booked {
  flex-shrink: 0;
  font-size: 12px;
  line-height: 1.2;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.hc-calendar-page__slot-switch {
  flex-shrink: 0;
}

.hc-calendar-page__slot-empty-add {
  padding: 0 4px;
  font-size: 14px;
  font-weight: 600;
}

.hc-calendar-page__add-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-top: 8px;
  padding: 8px 10px;
  background: var(--el-fill-color-lighter);
  border: 1px dashed var(--el-color-primary-light-5);
  border-radius: 6px;
}

.hc-calendar-page__add-time {
  width: 96px;
}

.hc-calendar-page__add-time-picker {
  width: 118px;
}

.hc-calendar-page__add-sep {
  color: var(--el-text-color-secondary);
}

.hc-calendar-page__add-type {
  width: 120px;
}

.hc-calendar-page__add-campus {
  width: 96px;
}

.hc-calendar-page__add-campus-select {
  width: 128px;
}

.hc-calendar-page__add-label {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.hc-calendar-page__batch-minutes {
  width: 88px;
}

.hc-calendar-page__batch-types {
  width: 180px;
}

.hc-calendar-page__batch-preview {
  font-size: 12px;
  color: var(--el-color-primary);
  white-space: nowrap;
}

.hc-calendar-page__batch-panel {
  margin-top: 8px;
  padding: 8px 10px;
  background: var(--el-fill-color-lighter);
  border: 1px dashed var(--el-color-primary-light-5);
  border-radius: 6px;
}

.hc-calendar-page__batch-panel .hc-calendar-page__add-row {
  margin-top: 0;
  padding: 0;
  background: transparent;
  border: none;
  border-radius: 0;
}

.hc-calendar-page__campus-caps {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  align-items: center;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.hc-calendar-page__campus-cap-item {
  display: flex;
  gap: 6px;
  align-items: center;
}

.hc-calendar-page__time-range {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.hc-calendar-page__capacity-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.hc-calendar-page__capacity-item {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
}

.hc-calendar-page__copy-hint {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 960px) {
  .hc-calendar-page__body {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(220px, 32%) minmax(0, 1fr);
  }
}
</style>
