<script setup lang="ts">
import type {
  LoadScheduleFormInitResponse,
  ScheduleActorDto,
  ScheduleCalendarCellDto,
  ScheduleCalendarDayDto,
  ScheduleCalendarMonthDayDto,
  ScheduleMonthSummaryDto,
} from '#/api/booking/outpatient-schedule';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  addDaysIso,
  currentYearMonth,
  monthLabel,
  monthRangeIso,
  shiftYearMonth,
  weekRangeAround,
} from '#/api/booking/booking-constants';
import {
  copyScheduleToNextWeekApi,
  generateDepartmentFromPractitionerRolesApi,
  generateFromPractitionerTemplateApi,
  generateFromTemplateApi,
  loadScheduleFormInitApi,
  pageSchedulesByCalendarApi,
  pageSchedulesByMonthApi,
} from '#/api/booking/outpatient-schedule';
import {
  pageScheduleTemplatesApi,
  pageWeekScheduleTemplatesApi,
} from '#/api/booking/schedule-template';
import type { ScheduleTemplateDto, WeekScheduleTemplateDto } from '#/api/booking/schedule-template';
import ScheduleActionDrawer from '#/views/booking/components/schedule-action-drawer.vue';
import ScheduleFormDrawer from '#/views/booking/components/schedule-form-drawer.vue';
import ScheduleMonthCalendar from '#/views/booking/components/schedule-month-calendar.vue';
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
} from 'element-plus';

defineOptions({ name: 'BookingOutpatientSchedule' });

type ViewMode = 'week' | 'month';

const { organizationId } = useBookingOrganization();
const initData = ref<LoadScheduleFormInitResponse>({});
const calendarDays = ref<ScheduleCalendarDayDto[]>([]);
const monthDays = ref<ScheduleCalendarMonthDayDto[]>([]);
const monthSummary = ref<ScheduleMonthSummaryDto>();
const viewMode = ref<ViewMode>('week');
const anchorDate = ref(new Date().toISOString().slice(0, 10));
const yearMonth = ref(currentYearMonth());

const filters = reactive({
  practitionerId: undefined as number | undefined,
  locationId: undefined as number | undefined,
  healthcareServiceId: undefined as number | undefined,
});

const createVisible = ref(false);
const actionVisible = ref(false);
const editScheduleId = ref<number>();
const editInitialActors = ref<ScheduleActorDto[]>();
const selectedDate = ref('');
const selectedSchedule = ref<ScheduleCalendarCellDto | null>(null);
const templateVisible = ref(false);
const templatePractitionerId = ref<number>();
const t2TemplateVisible = ref(false);
const t2TemplateKind = ref<'DAY' | 'WEEK'>('DAY');
const t2TemplateId = ref<number>();
const twTemplateId = ref<number>();
const t2AutoPublish = ref(false);
const t2AutoProvision = ref(false);
const t2TemplateOptions = ref<ScheduleTemplateDto[]>([]);
const twTemplateOptions = ref<WeekScheduleTemplateDto[]>([]);

const monthTitle = computed(() => monthLabel(yearMonth.value));

const { run: runInit, loading: initLoading } = useRequest(loadScheduleFormInitApi, {
  manual: true,
  onSuccess: (res) => {
    initData.value = res ?? {};
  },
});

const { run: runCalendar, loading: calendarLoading } = useRequest(pageSchedulesByCalendarApi, {
  manual: true,
  onSuccess: (res) => {
    calendarDays.value = res?.days ?? [];
  },
});

const { run: runMonth, loading: monthLoading } = useRequest(pageSchedulesByMonthApi, {
  manual: true,
  onSuccess: (res) => {
    monthDays.value = res?.days ?? [];
    monthSummary.value = res?.summary;
    if (res?.yearMonth) {
      yearMonth.value = res.yearMonth;
    }
  },
});

async function loadWeekCalendar() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录科室');
    return;
  }
  const { rangeStart, rangeEnd } = weekRangeAround(anchorDate.value);
  await runCalendar({
    organizationId: organizationId.value,
    rangeStart,
    rangeEnd,
    practitionerId: filters.practitionerId,
    locationId: filters.locationId,
    healthcareServiceId: filters.healthcareServiceId,
  });
}

async function loadMonthCalendar() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录科室');
    return;
  }
  await runMonth({
    organizationId: organizationId.value,
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
  if (!organizationId.value) return;
  await runInit({ organizationId: organizationId.value });
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

function openCreateDrawer() {
  editScheduleId.value = undefined;
  editInitialActors.value = undefined;
  createVisible.value = true;
}

function resolveActorsForEdit(scheduleId: number): ScheduleActorDto[] | undefined {
  if (selectedSchedule.value?.scheduleId === scheduleId && selectedSchedule.value.actors?.length) {
    return selectedSchedule.value.actors;
  }
  for (const day of calendarDays.value) {
    const cell = day.schedules?.find((item) => item.scheduleId === scheduleId);
    if (cell?.actors?.length) return cell.actors;
  }
  for (const day of monthDays.value) {
    for (const cell of day.schedules ?? []) {
      if (cell.scheduleId === scheduleId && cell.actors?.length) return cell.actors;
    }
  }
  return undefined;
}

function handleEdit(scheduleId: number) {
  editScheduleId.value = scheduleId;
  editInitialActors.value = resolveActorsForEdit(scheduleId);
  actionVisible.value = false;
  createVisible.value = true;
}

async function handleCopyWeek() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录科室');
    return;
  }
  const { rangeStart, rangeEnd } = weekRangeAround(anchorDate.value);
  const targetStart = addDaysIso(rangeStart, 7);
  const targetEnd = addDaysIso(rangeEnd, 7);
  try {
    await ElMessageBox.confirm(
      `将 ${rangeStart} ~ ${rangeEnd} 的排班复制到 ${targetStart} ~ ${targetEnd}？`,
      '复制到下一周',
    );
    const res = await copyScheduleToNextWeekApi({
      organizationId: organizationId.value,
      sourceRangeStart: rangeStart,
      sourceRangeEnd: rangeEnd,
      targetRangeStart: targetStart,
      targetRangeEnd: targetEnd,
      copySlotProvision: true,
    });
    ElMessage.success(
      `复制完成：日程 ${res?.copiedScheduleCount ?? 0} 条，放号 ${res?.provisionedSlotCount ?? 0} 个`,
    );
    anchorDate.value = targetStart;
    yearMonth.value = currentYearMonth(targetStart);
    await loadCalendar();
  } catch {
    // cancel or error
  }
}

function openTemplateDialog() {
  templatePractitionerId.value = filters.practitionerId;
  templateVisible.value = true;
}

async function handleGenerateFromTemplate() {
  if (!organizationId.value || !templatePractitionerId.value) {
    ElMessage.warning('请选择医师');
    return;
  }
  const res = await generateFromPractitionerTemplateApi({
    organizationId: organizationId.value,
    practitionerId: templatePractitionerId.value,
  });
  ElMessage.success(`已从岗位模板生成 ${res?.scheduleIds?.length ?? 0} 条日程草稿`);
  templateVisible.value = false;
  await loadCalendar();
}

async function handleDepartmentBatchGenerate() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录科室');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '将为科室全部在岗医师批量生成岗位模板草稿，是否继续？',
      '科室批量生成',
    );
    const res = await generateDepartmentFromPractitionerRolesApi({
      organizationId: organizationId.value,
      practitionerIds: filters.practitionerId ? [filters.practitionerId] : undefined,
    });
    const ok = res?.successItems?.length ?? 0;
    const fail = res?.failedItems?.length ?? 0;
    ElMessage.success(`成功 ${ok} 人，失败 ${fail} 人，共 ${res?.totalScheduleCount ?? 0} 条草稿`);
    await loadCalendar();
  } catch {
    // cancel
  }
}

async function openT2TemplateDialog() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录科室');
    return;
  }
  const [t2Res, twRes] = await Promise.all([
    pageScheduleTemplatesApi({
      organizationId: organizationId.value,
      pageNo: 1,
      pageSize: 200,
    }),
    pageWeekScheduleTemplatesApi({
      organizationId: organizationId.value,
      pageNo: 1,
      pageSize: 200,
    }),
  ]);
  t2TemplateOptions.value = (t2Res?.content ?? []).filter((item) => item.enabled !== false);
  twTemplateOptions.value = (twRes?.content ?? []).filter((item) => item.enabled !== false);
  t2TemplateKind.value = 'DAY';
  t2TemplateId.value = t2TemplateOptions.value[0]?.id;
  twTemplateId.value = twTemplateOptions.value[0]?.id;
  t2AutoPublish.value = false;
  t2AutoProvision.value = false;
  t2TemplateVisible.value = true;
}

async function handleGenerateFromT2Template() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录科室');
    return;
  }
  if (t2TemplateKind.value === 'DAY' && !t2TemplateId.value) {
    ElMessage.warning('请选择日程模板');
    return;
  }
  if (t2TemplateKind.value === 'WEEK' && !twTemplateId.value) {
    ElMessage.warning('请选择周模板');
    return;
  }
  const range =
    viewMode.value === 'month' ? monthRangeIso(yearMonth.value) : weekRangeAround(anchorDate.value);
  const { rangeStart, rangeEnd } = range;
  const res = await generateFromTemplateApi({
    organizationId: organizationId.value,
    templateKind: t2TemplateKind.value,
    scheduleTemplateId: t2TemplateKind.value === 'DAY' ? t2TemplateId.value : undefined,
    weekScheduleTemplateId: t2TemplateKind.value === 'WEEK' ? twTemplateId.value : undefined,
    rangeStart,
    rangeEnd,
    autoPublish: t2AutoPublish.value,
    autoProvision: t2AutoProvision.value,
  });
  const msg =
    `已生成 ${res?.generatedCount ?? 0} 条日程` +
    (res?.skippedCount ? `，跳过 ${res.skippedCount} 天` : '') +
    (res?.provisionedSlotCount ? `，放号 ${res.provisionedSlotCount} 个` : '');
  ElMessage.success(msg);
  t2TemplateVisible.value = false;
  await loadCalendar();
}

onMounted(refreshAll);
</script>

<template>
  <Page auto-content-height>
    <div class="booking-schedule-page">
      <header class="booking-schedule-page__toolbar">
        <ElSelect
          v-model="filters.practitionerId"
          clearable
          filterable
          placeholder="医师"
          style="width: 180px"
          @change="loadCalendar"
        >
          <ElOption
            v-for="item in initData.practitioners || []"
            :key="item.value"
            :label="item.label"
            :value="Number(item.value)"
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
            :value="Number(item.value)"
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
            :value="Number(item.value)"
          />
        </ElSelect>

        <ElRadioGroup v-model="viewMode" @change="switchViewMode">
          <ElRadioButton value="week">周视图</ElRadioButton>
          <ElRadioButton value="month">月视图</ElRadioButton>
        </ElRadioGroup>

        <template v-if="viewMode === 'week'">
          <ElButton @click="shiftWeek(-1)">上一周</ElButton>
          <ElButton @click="shiftWeek(1)">下一周</ElButton>
        </template>
        <template v-else>
          <span class="booking-schedule-page__month-title">{{ monthTitle }}</span>
          <ElButton @click="shiftMonth(-1)">上一月</ElButton>
          <ElButton @click="shiftMonth(1)">下一月</ElButton>
        </template>

        <ElButton @click="handleCopyWeek">复制到下一周</ElButton>
        <ElButton @click="openTemplateDialog">从岗位模板生成</ElButton>
        <ElButton @click="handleDepartmentBatchGenerate">科室岗位批量</ElButton>
        <ElButton @click="openT2TemplateDialog">从模板生成</ElButton>
        <ElButton :loading="calendarLoading || monthLoading || initLoading" @click="refreshAll">
          刷新
        </ElButton>
        <ElButton type="primary" @click="openCreateDrawer">新建日程</ElButton>
      </header>

      <ScheduleWeekCalendar
        v-if="viewMode === 'week'"
        :days="calendarDays"
        :loading="calendarLoading"
        @select="handleCellSelect"
      />

      <ScheduleMonthCalendar
        v-else
        :days="monthDays"
        :summary="monthSummary"
        :loading="monthLoading"
        @select-day="handleMonthDaySelect"
      />

      <ScheduleFormDrawer
        v-model:visible="createVisible"
        :organization-id="organizationId"
        :practitioners="initData.practitioners"
        :clinic-locations="initData.clinicLocations"
        :healthcare-services="initData.healthcareServices"
        :schedule-id="editScheduleId"
        :initial-actors="editInitialActors"
        @saved="refreshAll"
      />

      <ScheduleActionDrawer
        v-model:visible="actionVisible"
        :organization-id="organizationId"
        :selected-date="selectedDate"
        :schedule="selectedSchedule"
        :practitioners="initData.practitioners"
        @refreshed="refreshAll"
        @edit="handleEdit"
      />

      <ElDialog v-model="templateVisible" title="从岗位模板生成" width="420px">
        <ElForm label-width="80px">
          <ElFormItem label="医师" required>
            <ElSelect
              v-model="templatePractitionerId"
              filterable
              placeholder="选择医师"
              style="width: 100%"
            >
              <ElOption
                v-for="item in initData.practitioners || []"
                :key="item.value"
                :label="item.label"
                :value="Number(item.value)"
              />
            </ElSelect>
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="templateVisible = false">取消</ElButton>
          <ElButton type="primary" @click="handleGenerateFromTemplate">生成草稿</ElButton>
        </template>
      </ElDialog>

      <ElDialog v-model="t2TemplateVisible" title="从模板生成排班" width="480px">
        <ElForm label-width="108px">
          <ElFormItem label="模板类型" required>
            <ElRadioGroup v-model="t2TemplateKind">
              <ElRadioButton value="DAY">日模板 (T2)</ElRadioButton>
              <ElRadioButton value="WEEK">周模板 (TW)</ElRadioButton>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem v-if="t2TemplateKind === 'DAY'" label="日程模板" required>
            <ElSelect
              v-model="t2TemplateId"
              filterable
              placeholder="选择 T2 模板"
              style="width: 100%"
            >
              <ElOption
                v-for="item in t2TemplateOptions"
                :key="item.id"
                :label="`${item.templateName} (${item.templateCode})`"
                :value="item.id!"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-else label="周模板" required>
            <ElSelect
              v-model="twTemplateId"
              filterable
              placeholder="选择 TW 模板"
              style="width: 100%"
            >
              <ElOption
                v-for="item in twTemplateOptions"
                :key="item.id"
                :label="`${item.templateName} (${item.templateCode})`"
                :value="item.id!"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="日期范围">
            <span class="booking-schedule-page__hint">
              {{ viewMode === 'month' ? `当前月 ${yearMonth}` : '当前周历可见区间' }}
            </span>
          </ElFormItem>
          <ElFormItem label="自动发布">
            <ElSwitch v-model="t2AutoPublish" />
          </ElFormItem>
          <ElFormItem label="自动放号">
            <ElSwitch v-model="t2AutoProvision" :disabled="!t2AutoPublish" />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="t2TemplateVisible = false">取消</ElButton>
          <ElButton type="primary" @click="handleGenerateFromT2Template">生成</ElButton>
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
}

.booking-schedule-page__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
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
</style>
