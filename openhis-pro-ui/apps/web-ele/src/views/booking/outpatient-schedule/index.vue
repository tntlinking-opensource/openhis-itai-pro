<script setup lang="ts">
import type { BookingEntityId } from '#/api/booking/booking-id';
import { bookingOptionValue, isSameBookingId, toBookingId } from '#/api/booking/booking-id';
import type { BookingOptionDto } from '#/api/booking/booking-types';
import type {
  LoadScheduleFormInitResponse,
  ScheduleCalendarCellDto,
  ScheduleCalendarDayDto,
  ScheduleCalendarMonthDayDto,
  ScheduleMonthSummaryDto,
} from '#/api/booking/outpatient-schedule';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@zxplat/common-ui';

import {
  addDaysIso,
  currentYearMonth,
  formatIsoWeekLabel,
  monthLabel,
  monthRangeIso,
  shiftYearMonth,
  todayLocalDateIso,
  twoWeekRangeAround,
  weekRangeAround,
  weekRangeLabel,
} from '#/api/booking/booking-constants';
import {
  copyScheduleToNextWeekApi,
  generateFromTemplateApi,
  loadScheduleFormInitApi,
  pageSchedulesByCalendarApi,
  pageSchedulesByMonthApi,
} from '#/api/booking/outpatient-schedule';
import {
  pageScheduleTemplatesApi,
  pageWeekScheduleTemplatesApi,
  listSlotProvisionTemplateOptionsApi,
} from '#/api/booking/schedule-template';
import type { ScheduleTemplateDto, WeekScheduleTemplateDto } from '#/api/booking/schedule-template';
import ScheduleActionDrawer from '#/views/booking/components/schedule-action-drawer.vue';
import ScheduleCalendarLegend from '#/views/booking/components/schedule-calendar-legend.vue';
import ScheduleMonthCalendar from '#/views/booking/components/schedule-month-calendar.vue';
import SchedulePageGuide from '#/views/booking/components/schedule-page-guide.vue';
import ScheduleWeekCalendar from '#/views/booking/components/schedule-week-calendar.vue';
import { useBookingOrganization } from '#/views/booking/composables/use-booking-organization';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElTooltip,
} from 'element-plus';

defineOptions({ name: 'BookingOutpatientSchedule' });

type ViewMode = 'week' | 'month';

const { organizationId: loginOrganizationId } = useBookingOrganization();
/** 排班科室：默认同登录科室，可在页内切换（须为门诊科室） */
const scheduleOrganizationId = ref<BookingEntityId | undefined>();
const initData = ref<LoadScheduleFormInitResponse>({});
const practitionerOptions = computed(() => initData.value.practitioners ?? []);
const practitionerEmptyHint = computed(() => {
  if (initLoading.value || practitionerOptions.value.length > 0) {
    return '';
  }
  if (!scheduleOrganizationId.value) {
    return '请先选择排班科室';
  }
  return '当前科室无激活医师岗，请切换科室或在基础资料维护岗位';
});
const calendarDays = ref<ScheduleCalendarDayDto[]>([]);
const monthDays = ref<ScheduleCalendarMonthDayDto[]>([]);
const monthSummary = ref<ScheduleMonthSummaryDto>();
const viewMode = ref<ViewMode>('week');
const anchorDate = ref(todayLocalDateIso());
const yearMonth = ref(currentYearMonth());

const filters = reactive({
  practitionerId: undefined as BookingEntityId | undefined,
  locationId: undefined as BookingEntityId | undefined,
  healthcareServiceId: undefined as BookingEntityId | undefined,
});

const actionVisible = ref(false);
const actionCreateMode = ref(false);
const selectedDate = ref('');
const selectedSchedule = ref<ScheduleCalendarCellDto | null>(null);
const t2TemplateVisible = ref(false);
const t2TemplateKind = ref<'DAY' | 'WEEK'>('DAY');
const t2TemplateId = ref<BookingEntityId>();
const twTemplateId = ref<BookingEntityId>();
const t2AutoPublish = ref(false);
const t2AutoProvision = ref(false);
/** 生成正式排班：用户每次显式选择的区间（与排班设置中的占位有效期无关） */
const t2GenerateRangeStart = ref('');
const t2GenerateRangeEnd = ref('');
const t2TemplateOptions = ref<ScheduleTemplateDto[]>([]);
const twTemplateOptions = ref<WeekScheduleTemplateDto[]>([]);
const t1TemplateOptions = ref<BookingOptionDto[]>([]);

const monthTitle = computed(() => monthLabel(yearMonth.value));

const weekTitle = computed(() => formatIsoWeekLabel(anchorDate.value));

/** 周历上方蓝框「选中周」的周一～周日（复制源区间，与日历今天无关） */
const focusWeekRange = computed(() => weekRangeAround(anchorDate.value));

const focusWeekRangeText = computed(() => {
  const { rangeStart } = focusWeekRange.value;
  return rangeStart ? weekRangeLabel(rangeStart) : '';
});

const { run: runInit, loading: initLoading } = useRequest(loadScheduleFormInitApi, {
  manual: true,
  onSuccess: (res) => {
    initData.value = res ?? {};
  },
});

const { run: runCalendar, loading: calendarLoading } = useRequest(pageSchedulesByCalendarApi, {
  manual: true,
  onSuccess: (res) => {
    calendarDays.value = stripTemplatePreviewDays(res?.days ?? []);
  },
});

const { run: runMonth, loading: monthLoading } = useRequest(pageSchedulesByMonthApi, {
  manual: true,
  onSuccess: (res) => {
    monthDays.value = stripTemplatePreviewDays(res?.days ?? []);
    monthSummary.value = res?.summary;
    if (res?.yearMonth) {
      yearMonth.value = res.yearMonth;
    }
  },
});

/** 周历暂不展示 TW 占位预览格，避免未选模板时出现虚线预览 */
function stripTemplatePreviewDays<T extends { schedules?: ScheduleCalendarCellDto[] }>(
  days: T[],
): T[] {
  return days.map((day) => ({
    ...day,
    schedules: (day.schedules ?? []).filter(
      (schedule) => !schedule.fromTemplate && schedule.statusEnum !== 'FROM_TEMPLATE',
    ),
  }));
}

function resolveGenerateRangeForKind(kind: 'DAY' | 'WEEK'): {
  rangeStart: string;
  rangeEnd: string;
} {
  if (kind === 'DAY') {
    const day = anchorDate.value || todayLocalDateIso();
    return { rangeStart: day, rangeEnd: day };
  }
  if (viewMode.value === 'month') {
    return monthRangeIso(yearMonth.value);
  }
  return focusWeekRange.value;
}

function applyGenerateRangeForKind(kind: 'DAY' | 'WEEK') {
  const range = resolveGenerateRangeForKind(kind);
  t2GenerateRangeStart.value = range.rangeStart;
  t2GenerateRangeEnd.value = range.rangeEnd;
}

function syncDayGenerateEnd() {
  if (t2TemplateKind.value !== 'DAY') {
    return;
  }
  t2GenerateRangeEnd.value = t2GenerateRangeStart.value;
}

function pickScheduleOrganizationId(
  departments: BookingOptionDto[] | undefined,
  preferred?: BookingEntityId,
): BookingEntityId | undefined {
  const pref = toBookingId(preferred);
  if (pref && departments?.some((item) => isSameBookingId(item.value, pref))) {
    return pref;
  }
  return toBookingId(departments?.[0]?.value) ?? pref;
}

async function loadT1TemplateOptions(orgId?: BookingEntityId) {
  if (!orgId) {
    t1TemplateOptions.value = [];
    return;
  }
  const res = await listSlotProvisionTemplateOptionsApi({ organizationId: orgId });
  t1TemplateOptions.value = res?.options ?? [];
}

async function loadFormInit(
  preferredOrganizationId?: BookingEntityId,
  practitionerId?: BookingEntityId,
) {
  const candidate =
    preferredOrganizationId ?? scheduleOrganizationId.value ?? loginOrganizationId.value;
  let response = await runInit({ organizationId: candidate, practitionerId });
  let resolved = pickScheduleOrganizationId(response?.departments, candidate);
  if (resolved !== undefined && resolved !== candidate) {
    response = await runInit({ organizationId: resolved, practitionerId });
  }
  scheduleOrganizationId.value = resolved;
  await loadT1TemplateOptions(resolved);
  return response;
}

async function loadWeekCalendar() {
  if (!scheduleOrganizationId.value) {
    ElMessage.warning('请先选择排班科室');
    return;
  }
  const { rangeStart, rangeEnd } = twoWeekRangeAround(anchorDate.value);
  await runCalendar({
    organizationId: scheduleOrganizationId.value,
    rangeStart,
    rangeEnd,
    practitionerId: filters.practitionerId,
    locationId: filters.locationId,
    healthcareServiceId: filters.healthcareServiceId,
  });
}

async function loadMonthCalendar() {
  if (!scheduleOrganizationId.value) {
    ElMessage.warning('请先选择排班科室');
    return;
  }
  await runMonth({
    organizationId: scheduleOrganizationId.value,
    yearMonth: yearMonth.value,
    practitionerId: filters.practitionerId,
    locationId: filters.locationId,
    healthcareServiceId: filters.healthcareServiceId,
  });
}

async function loadCalendar() {
  if (viewMode.value === 'month') {
    await loadMonthCalendar();
    return;
  }
  await loadWeekCalendar();
}

async function refreshAll() {
  await loadFormInit();
  await loadCalendar();
}

async function handleScheduleOrganizationChange() {
  filters.practitionerId = undefined;
  filters.locationId = undefined;
  filters.healthcareServiceId = undefined;
  await loadFormInit(scheduleOrganizationId.value);
  await loadCalendar();
}

async function handlePractitionerFilterChange() {
  filters.healthcareServiceId = undefined;
  await loadFormInit(scheduleOrganizationId.value, filters.practitionerId);
  await loadCalendar();
}

function shiftWeek(delta: number) {
  anchorDate.value = addDaysIso(anchorDate.value, delta * 7);
  yearMonth.value = currentYearMonth(anchorDate.value);
  void loadWeekCalendar();
}

function shiftMonth(delta: number) {
  yearMonth.value = shiftYearMonth(yearMonth.value, delta);
  anchorDate.value = `${yearMonth.value}-01`;
  void loadMonthCalendar();
}

function switchViewMode(mode: ViewMode) {
  viewMode.value = mode;
  if (mode === 'month') {
    yearMonth.value = currentYearMonth(anchorDate.value);
    void loadMonthCalendar();
    return;
  }
  void loadWeekCalendar();
}

function handleCellSelect(payload: { date: string; schedule: ScheduleCalendarCellDto }) {
  actionCreateMode.value = false;
  anchorDate.value = payload.date;
  selectedDate.value = payload.date;
  selectedSchedule.value = payload.schedule;
  actionVisible.value = true;
}

function handleMonthDaySelect(payload: { date: string; schedules: ScheduleCalendarCellDto[] }) {
  if (!payload.schedules.length) {
    selectedDate.value = payload.date;
    anchorDate.value = payload.date;
    viewMode.value = 'week';
    void loadWeekCalendar();
    return;
  }
  if (payload.schedules.length === 1) {
    handleCellSelect({ date: payload.date, schedule: payload.schedules[0]! });
    return;
  }
  anchorDate.value = payload.date;
  viewMode.value = 'week';
  void loadWeekCalendar();
}

function handleDayAnchor(payload: { date: string }) {
  anchorDate.value = payload.date;
  selectedDate.value = payload.date;
}

function handleDayOpen(payload: { date: string; schedules: ScheduleCalendarCellDto[] }) {
  anchorDate.value = payload.date;
  selectedDate.value = payload.date;
  if (payload.schedules.length === 1) {
    handleCellSelect({ date: payload.date, schedule: payload.schedules[0]! });
    return;
  }
  if (payload.schedules.length > 1) {
    handleCellSelect({ date: payload.date, schedule: payload.schedules[0]! });
    return;
  }
  openCreateDrawer(payload.date);
}

function openCreateDrawer(date?: string) {
  actionCreateMode.value = true;
  selectedSchedule.value = null;
  if (date) {
    anchorDate.value = date;
    selectedDate.value = date;
  } else {
    selectedDate.value = anchorDate.value;
  }
  actionVisible.value = true;
}

function targetWeekHasRealSchedules(rangeStart: string, rangeEnd: string) {
  return calendarDays.value.some((day) => {
    if (!day.date || day.date < rangeStart || day.date > rangeEnd) {
      return false;
    }
    return (day.schedules ?? []).some((schedule) => schedule.scheduleId != null);
  });
}

async function handleCopyWeek() {
  if (!scheduleOrganizationId.value) {
    ElMessage.warning('请先选择排班科室');
    return;
  }
  if (viewMode.value !== 'week') {
    ElMessage.warning('请切换到周视图后再复制');
    return;
  }
  const { rangeStart, rangeEnd } = focusWeekRange.value;
  const targetStart = addDaysIso(rangeStart, 7);
  const targetEnd = addDaysIso(rangeEnd, 7);
  if (targetWeekHasRealSchedules(targetStart, targetEnd)) {
    ElMessage.warning(`次周（${targetStart} ~ ${targetEnd}）已有排班，无法复制`);
    return;
  }
  try {
    await ElMessageBox.confirm(
      `将选中周（${rangeStart} ~ ${rangeEnd}）的排班复制到下一周（${targetStart} ~ ${targetEnd}）？\n\n选中周即周历上方蓝框标注的那一周，不一定是今天所在周。`,
      '复制到下一周',
    );
    const res = await copyScheduleToNextWeekApi({
      organizationId: scheduleOrganizationId.value,
      sourceRangeStart: rangeStart,
      sourceRangeEnd: rangeEnd,
      targetRangeStart: targetStart,
      targetRangeEnd: targetEnd,
      copySlotProvision: true,
    });
    const copied = res?.copiedScheduleCount ?? 0;
    const provisioned = res?.provisionedSlotCount ?? 0;
    if (copied === 0) {
      ElMessage.warning('选中周无可复制的正式日程（占位预览格无法复制；请先新建并发布日程）');
    } else {
      const slotHint = provisioned > 0 ? `，放号 ${provisioned} 个` : '';
      ElMessage.success(
        `已复制 ${copied} 条日程到次周（${targetStart} ~ ${targetEnd}），请查看下方「次周」行${slotHint}`,
      );
    }
    await loadCalendar();
  } catch (error) {
    if (error === 'cancel') {
      return;
    }
  }
}

async function openT2TemplateDialog() {
  if (!scheduleOrganizationId.value) {
    ElMessage.warning('请先选择排班科室');
    return;
  }
  const [t2Res, twRes] = await Promise.all([
    pageScheduleTemplatesApi({
      organizationId: scheduleOrganizationId.value,
      pageNo: 1,
      pageSize: 200,
    }),
    pageWeekScheduleTemplatesApi({
      organizationId: scheduleOrganizationId.value,
      pageNo: 1,
      pageSize: 200,
    }),
  ]);
  t2TemplateOptions.value = (t2Res?.content ?? []).filter((item) => item.enabled !== false);
  twTemplateOptions.value = (twRes?.content ?? []).filter((item) => item.enabled !== false);
  t2TemplateKind.value = 'DAY';
  t2TemplateId.value = toBookingId(t2TemplateOptions.value[0]?.id);
  twTemplateId.value = toBookingId(twTemplateOptions.value[0]?.id);
  t2AutoPublish.value = true;
  t2AutoProvision.value = false;
  applyGenerateRangeForKind(t2TemplateKind.value);
  t2TemplateVisible.value = true;
}

async function handleGenerateFromT2Template() {
  if (!scheduleOrganizationId.value) {
    ElMessage.warning('请先选择排班科室');
    return;
  }
  if (t2TemplateKind.value === 'DAY' && !t2TemplateId.value) {
    ElMessage.warning('请选择日排班');
    return;
  }
  if (t2TemplateKind.value === 'WEEK' && !twTemplateId.value) {
    ElMessage.warning('请选择周排班');
    return;
  }
  const rangeStart = t2GenerateRangeStart.value?.trim();
  let rangeEnd = t2GenerateRangeEnd.value?.trim();
  if (t2TemplateKind.value === 'DAY') {
    rangeEnd = rangeStart;
  }
  if (!rangeStart || !rangeEnd) {
    ElMessage.warning(t2TemplateKind.value === 'DAY' ? '请选择出诊日期' : '请选择生成日期区间');
    return;
  }
  if (rangeEnd < rangeStart) {
    ElMessage.warning('结束日期不能早于开始日期');
    return;
  }
  const res = await generateFromTemplateApi({
    organizationId: scheduleOrganizationId.value,
    templateKind: t2TemplateKind.value,
    scheduleTemplateId: t2TemplateKind.value === 'DAY' ? t2TemplateId.value : undefined,
    weekScheduleTemplateId: t2TemplateKind.value === 'WEEK' ? twTemplateId.value : undefined,
    rangeStart,
    rangeEnd,
    autoPublish: t2AutoPublish.value,
    autoProvision: t2AutoProvision.value,
  });
  const generated = res?.generatedCount ?? 0;
  let msg =
    `已生成 ${generated} 条日程` +
    (res?.skippedCount ? `，跳过 ${res.skippedCount} 天` : '') +
    (res?.provisionedSlotCount ? `，放号 ${res.provisionedSlotCount} 个` : '');
  t2TemplateVisible.value = false;
  // 生成后清筛选，避免刚生成的草稿被医师/诊室/号源类型过滤掉
  filters.practitionerId = undefined;
  filters.locationId = undefined;
  filters.healthcareServiceId = undefined;
  anchorDate.value = rangeStart;
  viewMode.value = 'week';
  await loadCalendar();
  if (generated > 0 && !t2AutoPublish.value) {
    msg += '；请点击周历灰色草稿格 →「发布」';
  } else if (generated > 0 && t2AutoPublish.value && !t2AutoProvision.value) {
    msg += '；请点已发布格批量放号';
  }
  ElMessage.success(msg);
}

watch(t2TemplateKind, (kind) => {
  if (!t2TemplateVisible.value) {
    return;
  }
  applyGenerateRangeForKind(kind);
});

watch(actionVisible, (open) => {
  if (!open) {
    return;
  }
  void loadT1TemplateOptions(scheduleOrganizationId.value);
});

watch(loginOrganizationId, (organizationId) => {
  const id = toBookingId(organizationId);
  if (!id) {
    return;
  }
  const departments = initData.value.departments;
  if (departments?.length && !departments.some((item) => isSameBookingId(item.value, id))) {
    return;
  }
  if (isSameBookingId(scheduleOrganizationId.value, id)) {
    return;
  }
  scheduleOrganizationId.value = id;
  void handleScheduleOrganizationChange();
});

async function handleDrawerRefreshed(payload?: { planningDate?: string }) {
  if (payload?.planningDate) {
    anchorDate.value = payload.planningDate;
    yearMonth.value = currentYearMonth(payload.planningDate);
  }
  await refreshAll();
}

onMounted(refreshAll);
</script>

<template>
  <Page auto-content-height>
    <div class="booking-schedule-page">
      <header class="booking-schedule-page__toolbar">
        <ElSelect
          v-model="scheduleOrganizationId"
          filterable
          placeholder="排班科室"
          style="width: 180px"
          @change="handleScheduleOrganizationChange"
        >
          <ElOption
            v-for="item in initData.departments || []"
            :key="item.value"
            :label="item.label"
            :value="bookingOptionValue(item.value)"
          />
        </ElSelect>
        <ElSelect
          v-model="filters.practitionerId"
          clearable
          filterable
          :placeholder="practitionerEmptyHint || '医师'"
          style="width: 180px"
          @change="handlePractitionerFilterChange"
        >
          <ElOption
            v-for="item in practitionerOptions"
            :key="item.value"
            :label="item.label"
            :value="bookingOptionValue(item.value)"
          />
        </ElSelect>
        <ElSelect
          v-model="filters.locationId"
          clearable
          filterable
          placeholder="诊室"
          style="width: 180px"
          @change="loadCalendar"
        >
          <ElOption
            v-for="item in initData.clinicLocations || []"
            :key="item.value"
            :label="item.label"
            :value="bookingOptionValue(item.value)"
          />
        </ElSelect>
        <ElSelect
          v-model="filters.healthcareServiceId"
          clearable
          filterable
          placeholder="号源类型"
          style="width: 180px"
          @change="loadCalendar"
        >
          <ElOption
            v-for="item in initData.healthcareServices || []"
            :key="item.value"
            :label="item.label"
            :value="bookingOptionValue(item.value)"
          />
        </ElSelect>

        <ElRadioGroup v-model="viewMode" @change="switchViewMode">
          <ElRadioButton value="week">周视图</ElRadioButton>
          <ElRadioButton value="month">月视图</ElRadioButton>
        </ElRadioGroup>

        <template v-if="viewMode === 'week'">
          <span class="booking-schedule-page__week-title">
            选中周 {{ weekTitle }}
            <span v-if="focusWeekRangeText" class="booking-schedule-page__week-subtitle">
              {{ focusWeekRangeText }}
            </span>
          </span>
          <ElButton @click="shiftWeek(-1)">上一周</ElButton>
          <ElButton @click="shiftWeek(1)">下一周</ElButton>
          <ElTooltip placement="top" :show-after="200">
            <template #content>
              将周历上方「选中周」（蓝框）的排班复制到其下一周（次周），与今天是否在该周无关
            </template>
            <ElButton @click="handleCopyWeek">复制到下一周</ElButton>
          </ElTooltip>
        </template>
        <template v-else>
          <span class="booking-schedule-page__month-title">{{ monthTitle }}</span>
          <ElButton @click="shiftMonth(-1)">上一月</ElButton>
          <ElButton @click="shiftMonth(1)">下一月</ElButton>
        </template>

        <ElButton @click="openT2TemplateDialog">生成正式排班</ElButton>
        <ElButton :loading="calendarLoading || monthLoading || initLoading" @click="refreshAll">
          刷新
        </ElButton>
        <ElButton type="primary" @click="() => openCreateDrawer()">新建日程</ElButton>
      </header>

      <SchedulePageGuide />

      <ScheduleCalendarLegend />

      <div v-if="viewMode === 'week'" class="booking-schedule-page__calendar">
        <ScheduleWeekCalendar
          :days="calendarDays"
          :loading="calendarLoading"
          :anchor-date="anchorDate"
          :organization-id="scheduleOrganizationId"
          :practitioners="initData.practitioners"
          :departments="initData.departments"
          @select="handleCellSelect"
          @anchor="handleDayAnchor"
          @open-day="handleDayOpen"
        />
      </div>

      <ScheduleMonthCalendar
        v-else
        :days="monthDays"
        :summary="monthSummary"
        :loading="monthLoading"
        @select-day="handleMonthDaySelect"
      />

      <ScheduleActionDrawer
        v-model:visible="actionVisible"
        :create-mode="actionCreateMode"
        :organization-id="scheduleOrganizationId"
        :selected-date="selectedDate"
        :schedule="selectedSchedule"
        :practitioners="initData.practitioners"
        :departments="initData.departments"
        :healthcare-services="initData.healthcareServices"
        :slot-provision-template-options="t1TemplateOptions"
        @refreshed="handleDrawerRefreshed"
      />

      <ElDialog v-model="t2TemplateVisible" title="生成正式排班" width="520px">
        <p class="booking-schedule-page__hint booking-schedule-page__hint--block">
          将「排班设置」中的模板生成为正式日程。
          <strong>日排班</strong>仅生成选定单日；<strong>周排班</strong>在区间内按 weekday
          逐日映射。
          默认自动发布（绿格）；关闭后为草稿（灰格）。还须<strong>放号</strong>后患者才可约。
        </p>
        <ElForm label-width="108px">
          <ElFormItem label="排班类型" required>
            <ElRadioGroup v-model="t2TemplateKind">
              <ElRadioButton value="DAY">日排班</ElRadioButton>
              <ElRadioButton value="WEEK">周排班</ElRadioButton>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem v-if="t2TemplateKind === 'DAY'" label="日排班" required>
            <ElSelect
              v-model="t2TemplateId"
              filterable
              placeholder="选择日排班"
              style="width: 100%"
            >
              <ElOption
                v-for="item in t2TemplateOptions"
                :key="item.id"
                :label="item.templateName"
                :value="toBookingId(item.id)!"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-else label="周排班" required>
            <ElSelect
              v-model="twTemplateId"
              filterable
              placeholder="选择周排班"
              style="width: 100%"
            >
              <ElOption
                v-for="item in twTemplateOptions"
                :key="item.id"
                :label="item.templateName"
                :value="toBookingId(item.id)!"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="t2TemplateKind === 'DAY'" label="出诊日期" required>
            <ElDatePicker
              v-model="t2GenerateRangeStart"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 100%"
              @change="syncDayGenerateEnd"
            />
            <p class="booking-schedule-page__hint">仅生成选定单日；要排整周请切换为「周排班」</p>
          </ElFormItem>
          <ElFormItem v-else label="生成区间" required>
            <ElDatePicker
              v-model="t2GenerateRangeStart"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="开始日期"
              style="width: 140px"
            />
            <span style="margin: 0 8px">—</span>
            <ElDatePicker
              v-model="t2GenerateRangeEnd"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="结束日期"
              style="width: 140px"
            />
            <p class="booking-schedule-page__hint">
              按周排班模板映射 weekday；已存在排班或 T3 停诊日会自动跳过
            </p>
          </ElFormItem>
          <ElFormItem label="自动发布">
            <ElSwitch v-model="t2AutoPublish" />
            <p v-if="!t2AutoPublish" class="booking-schedule-page__hint">
              关闭后周历显示<strong>灰色草稿格</strong>，点格子 → 抽屉里点「发布」
            </p>
          </ElFormItem>
          <ElFormItem label="自动放号">
            <ElSwitch v-model="t2AutoProvision" :disabled="!t2AutoPublish" />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="t2TemplateVisible = false">取消</ElButton>
          <ElButton type="primary" @click="handleGenerateFromT2Template">生成正式排班</ElButton>
        </template>
      </ElDialog>
    </div>
  </Page>
</template>

<style scoped>
.booking-schedule-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.booking-schedule-page__calendar {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.booking-schedule-page__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.booking-schedule-page__week-title {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
  min-width: 120px;
  font-size: 14px;
  font-weight: 600;
}

.booking-schedule-page__week-subtitle {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.booking-schedule-page__month-title {
  min-width: 96px;
  font-size: 14px;
  font-weight: 600;
}

.booking-schedule-page__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.booking-schedule-page__hint--block {
  margin: 0 0 12px;
  line-height: 1.5;
}
</style>
