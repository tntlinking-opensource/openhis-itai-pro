<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  CalendarExceptionDto,
  CalendarExceptionMonthDayDto,
  CalendarExceptionType,
} from '#/api/booking/calendar-exception';
import type {
  ScheduleTemplateDto,
  SlotProvisionTemplateDto,
  SlotProvisionTemplateItemDto,
  WeekScheduleTemplateDto,
  WeekScheduleTemplateItemDto,
} from '#/api/booking/schedule-template';
import type { BookingEntityId } from '#/api/booking/booking-id';
import { bookingOptionValue, toBookingId } from '#/api/booking/booking-id';
import type { LoadScheduleFormInitResponse } from '#/api/booking/outpatient-schedule';
import type { ReleasePolicyDto } from '#/api/booking/booking-policy';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { pageReleasePoliciesApi } from '#/api/booking/booking-policy';
import { loadScheduleFormInitApi } from '#/api/booking/outpatient-schedule';
import {
  deleteScheduleTemplateApi,
  deleteSlotProvisionTemplateApi,
  deleteWeekScheduleTemplateApi,
  listSlotProvisionTemplateOptionsApi,
  loadScheduleTemplateApi,
  loadSlotProvisionTemplateApi,
  loadWeekScheduleTemplateApi,
  pageScheduleTemplatesApi,
  pageSlotProvisionTemplatesApi,
  pageWeekScheduleTemplatesApi,
  saveScheduleTemplateApi,
  saveSlotProvisionTemplateApi,
  saveWeekScheduleTemplateApi,
} from '#/api/booking/schedule-template';
import {
  calendarExceptionTypeLabel,
  calendarExceptionTypeOptions,
  deleteCalendarExceptionApi,
  listCalendarExceptionsByMonthApi,
  loadCalendarExceptionApi,
  pageCalendarExceptionsApi,
  saveCalendarExceptionApi,
} from '#/api/booking/calendar-exception';
import { useBookingOrganization } from '#/views/booking/composables/use-booking-organization';
import CalendarExceptionMonth from '#/views/booking/components/calendar-exception-month.vue';
import ReleasePolicyPanel from '#/views/booking/components/release-policy-panel.vue';
import ScheduleTemplateGuide from '#/views/booking/components/schedule-template-guide.vue';
import { useRequest } from '#/hooks/use-request';
import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTabPane,
  ElTabs,
} from 'element-plus';

defineOptions({ name: 'BookingScheduleTemplate' });

const router = useRouter();
const { organizationId } = useBookingOrganization();
const activeTab = ref('slot');

const t1List = ref<SlotProvisionTemplateDto[]>([]);
const t1Total = ref(0);
const t2List = ref<ScheduleTemplateDto[]>([]);
const t2Total = ref(0);
const twList = ref<WeekScheduleTemplateDto[]>([]);
const twTotal = ref(0);

const t1Query = reactive({ pageNo: 1, pageSize: 20, keyword: '' });
const t2Query = reactive({ pageNo: 1, pageSize: 20, keyword: '' });
const twQuery = reactive({ pageNo: 1, pageSize: 20, keyword: '' });
const t3Query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  exceptionType: '' as CalendarExceptionType | '',
});
const t3Month = ref(new Date().toISOString().slice(0, 7));
const t3MonthDays = ref<CalendarExceptionMonthDayDto[]>([]);
const t3List = ref<CalendarExceptionDto[]>([]);
const t3Total = ref(0);
const t3EditVisible = ref(false);
const t3Form = ref<Partial<CalendarExceptionDto>>({
  exceptionType: 'CLOSED',
  autoApplyOnGenerate: true,
});
const t2Options = ref<{ value: BookingEntityId; label: string }[]>([]);

const t1EditVisible = ref(false);
const t2EditVisible = ref(false);
const twEditVisible = ref(false);
const t1Form = ref<Partial<SlotProvisionTemplateDto>>({ enabled: true, slotTemplates: [] });
const t2Form = ref<Partial<ScheduleTemplateDto>>({
  enabled: true,
  scheduleMode: 'MODE-A',
  priority: 0,
});
const twForm = ref<Partial<WeekScheduleTemplateDto>>({
  enabled: true,
  fallbackBookable: false,
  isDefault: true,
  items: [],
});

const weekdayLabels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

const initData = ref<LoadScheduleFormInitResponse>({});
const t1Options = ref<{ value: string; label: string }[]>([]);
const releasePolicies = ref<ReleasePolicyDto[]>([]);

const scheduleModeOptions = [
  { value: 'MODE-A', label: '医师门诊 (MODE-A)' },
  { value: 'MODE-B', label: '科室号 (MODE-B)' },
  { value: 'MODE-C', label: '诊区号 (MODE-C)' },
];

const t1FormItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '编码/名称' },
];

const t2FormItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '编码/名称' },
];

const t1Columns: TableColumn[] = [
  { prop: 'templateName', label: '名称', minWidth: 160 },
  { prop: 'enabled', label: '启用', width: 80, slot: 'enabled' },
  { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
];

const t2Columns: TableColumn[] = [
  { prop: 'templateName', label: '名称', minWidth: 140 },
  { prop: 'scheduleMode', label: '模式', width: 100 },
  { prop: 'enabled', label: '启用', width: 80, slot: 'enabled' },
  { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
];

const twColumns: TableColumn[] = [
  { prop: 'templateName', label: '名称', minWidth: 160 },
  { prop: 'isDefault', label: '默认', width: 70, slot: 'isDefault' },
  { prop: 'fallbackBookable', label: '可约', width: 70, slot: 'fallbackBookable' },
  { prop: 'enabled', label: '启用', width: 70, slot: 'enabled' },
  { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
];

const twFormItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '编码/名称' },
];

const t3FormItems: FormItemConfig[] = [
  { prop: 'exceptionType', label: '类型', type: 'select', options: calendarExceptionTypeOptions },
  { prop: 'keyword', label: '说明', type: 'input', placeholder: '国庆、调休等' },
];

const t3Columns: TableColumn[] = [
  { prop: 'exceptionDate', label: '起始日', width: 110 },
  { prop: 'rangeEnd', label: '结束日', width: 110 },
  { prop: 'exceptionType', label: '类型', width: 120, slot: 'exceptionType' },
  { prop: 'reason', label: '说明', minWidth: 140 },
  { prop: 'autoApplyOnGenerate', label: '参与生成', width: 90, slot: 'autoApply' },
  { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
];

const { run: runT1Page, loading: t1Loading } = useRequest(pageSlotProvisionTemplatesApi, {
  manual: true,
  onSuccess: (res) => {
    t1List.value = res?.content ?? [];
    t1Total.value = Number(res?.totalCount ?? 0);
  },
});

const { run: runT2Page, loading: t2Loading } = useRequest(pageScheduleTemplatesApi, {
  manual: true,
  onSuccess: (res) => {
    t2List.value = res?.content ?? [];
    t2Total.value = Number(res?.totalCount ?? 0);
  },
});

const { run: runTwPage, loading: twLoading } = useRequest(pageWeekScheduleTemplatesApi, {
  manual: true,
  onSuccess: (res) => {
    twList.value = res?.content ?? [];
    twTotal.value = Number(res?.totalCount ?? 0);
  },
});

const { run: runT3Page, loading: t3Loading } = useRequest(pageCalendarExceptionsApi, {
  manual: true,
  onSuccess: (res) => {
    t3List.value = res?.content ?? [];
    t3Total.value = Number(res?.totalCount ?? 0);
  },
});

const { run: runT3Month, loading: t3MonthLoading } = useRequest(listCalendarExceptionsByMonthApi, {
  manual: true,
  onSuccess: (res) => {
    t3MonthDays.value = res?.days ?? [];
  },
});

async function loadT1List() {
  await runT1Page({
    organizationId: organizationId.value,
    keyword: String(t1Query.keyword || '').trim() || undefined,
    pageNo: t1Query.pageNo,
    pageSize: t1Query.pageSize,
  });
}

async function loadT2List() {
  if (!organizationId.value) return;
  await runT2Page({
    organizationId: organizationId.value,
    keyword: String(t2Query.keyword || '').trim() || undefined,
    pageNo: t2Query.pageNo,
    pageSize: t2Query.pageSize,
  });
}

async function loadTwList() {
  if (!organizationId.value) return;
  await runTwPage({
    organizationId: organizationId.value,
    keyword: String(twQuery.keyword || '').trim() || undefined,
    pageNo: twQuery.pageNo,
    pageSize: twQuery.pageSize,
  });
}

async function loadT3List() {
  await runT3Page({
    organizationId: organizationId.value,
    exceptionType: t3Query.exceptionType || undefined,
    keyword: String(t3Query.keyword || '').trim() || undefined,
    pageNo: t3Query.pageNo,
    pageSize: t3Query.pageSize,
  });
}

async function loadT3Month() {
  await runT3Month({
    organizationId: organizationId.value,
    month: t3Month.value,
  });
}

async function loadT3Data() {
  await Promise.all([loadT3List(), loadT3Month()]);
}

function normalizeScheduleTemplateForm(dto: ScheduleTemplateDto): Partial<ScheduleTemplateDto> {
  return {
    ...dto,
    healthcareServiceId: toBookingId(dto.healthcareServiceId),
    practitionerId: toBookingId(dto.practitionerId),
    locationId: toBookingId(dto.locationId),
    slotProvisionTemplateId: toBookingId(dto.slotProvisionTemplateId),
    defaultReleasePolicyId: toBookingId(dto.defaultReleasePolicyId),
  };
}

/** 排班模板按登录科室隔离，未选科室时阻断保存类操作 */
function ensureOrganizationId(): boolean {
  if (!organizationId.value) {
    ElMessage.warning('请先在系统顶部选择登录科室');
    return false;
  }
  return true;
}

/** 周明细里的日排班主键与下拉选项统一为 string，避免 Long 精度丢失 */
function normalizeTwFormItems(
  items?: WeekScheduleTemplateItemDto[],
): WeekScheduleTemplateItemDto[] {
  if (!items?.length) {
    return [];
  }
  return items.map((row) => ({
    ...row,
    dayScheduleTemplateId: toBookingId(row.dayScheduleTemplateId),
  }));
}

async function loadAuxData() {
  if (!organizationId.value) return;
  const [init, t1Opts, policies] = await Promise.all([
    loadScheduleFormInitApi({ organizationId: organizationId.value }),
    listSlotProvisionTemplateOptionsApi({ organizationId: organizationId.value }),
    pageReleasePoliciesApi({
      organizationId: organizationId.value,
      pageNo: 1,
      pageSize: 100,
    }),
  ]);
  initData.value = init ?? {};
  t1Options.value = (t1Opts?.options ?? []).map((o) => ({
    value: bookingOptionValue(o.value),
    label: String(o.label ?? o.value),
  }));
  releasePolicies.value = policies?.content ?? [];
  t2Options.value =
    (
      await pageScheduleTemplatesApi({
        organizationId: organizationId.value,
        pageNo: 1,
        pageSize: 200,
      })
    )?.content?.map((item) => ({
      value: toBookingId(item.id)!,
      label: item.templateName ?? String(item.id),
    })) ?? [];
}

function openT1Create() {
  t1Form.value = {
    enabled: true,
    organizationId: organizationId.value,
    slotTemplates: [{ startTime: '08:00', endTime: '12:00', capacity: 10 }],
  };
  t1EditVisible.value = true;
}

async function openT1Edit(row: SlotProvisionTemplateDto) {
  if (!row.id) return;
  t1Form.value = await loadSlotProvisionTemplateApi({ id: row.id });
  t1EditVisible.value = true;
}

function addSlotRow() {
  const items = t1Form.value.slotTemplates ?? [];
  items.push({ startTime: '14:00', endTime: '17:00', capacity: 10 });
  t1Form.value.slotTemplates = items;
}

function removeSlotRow(index: number) {
  t1Form.value.slotTemplates?.splice(index, 1);
}

async function handleSaveT1() {
  const form = t1Form.value;
  if (!form.templateName?.trim()) {
    ElMessage.warning('请填写名称');
    return;
  }
  if (!form.slotTemplates?.length) {
    ElMessage.warning('请至少添加一个时段');
    return;
  }
  await saveSlotProvisionTemplateApi({
    id: form.id,
    organizationId: organizationId.value,
    templateName: form.templateName.trim(),
    slotTemplates: form.slotTemplates as SlotProvisionTemplateItemDto[],
    enabled: form.enabled !== false,
  });
  ElMessage.success('放号时段已保存');
  t1EditVisible.value = false;
  await loadT1List();
  await loadAuxData();
}

async function handleDeleteT1(row: SlotProvisionTemplateDto) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确认删除「${row.templateName}」？`, '删除');
    await deleteSlotProvisionTemplateApi({ id: row.id });
    ElMessage.success('已删除');
    await loadT1List();
  } catch {
    // cancel
  }
}

async function openT2Create() {
  if (!ensureOrganizationId()) {
    return;
  }
  await loadAuxData();
  t2Form.value = {
    enabled: true,
    organizationId: organizationId.value,
    scheduleMode: 'MODE-A',
    priority: 0,
  };
  t2EditVisible.value = true;
}

async function openT2Edit(row: ScheduleTemplateDto) {
  if (!row.id) return;
  await loadAuxData();
  const loaded = await loadScheduleTemplateApi({ id: row.id });
  t2Form.value = normalizeScheduleTemplateForm(loaded);
  t2EditVisible.value = true;
}

async function handleSaveT2() {
  const form = t2Form.value;
  if (!ensureOrganizationId()) {
    return;
  }
  if (!form.templateName?.trim() || !form.healthcareServiceId) {
    ElMessage.warning('请填写名称并选择号源类型');
    return;
  }
  await saveScheduleTemplateApi({
    id: form.id,
    organizationId: organizationId.value,
    templateName: form.templateName.trim(),
    scheduleMode: form.scheduleMode ?? 'MODE-A',
    practitionerId: form.practitionerId,
    locationId: form.locationId,
    healthcareServiceId: form.healthcareServiceId,
    slotProvisionTemplateId: form.slotProvisionTemplateId,
    defaultReleasePolicyId: form.defaultReleasePolicyId,
    effectiveStart: form.effectiveStart,
    effectiveEnd: form.effectiveEnd,
    priority: Number(form.priority ?? 0),
    enabled: form.enabled !== false,
  });
  ElMessage.success('日排班已保存');
  t2EditVisible.value = false;
  await loadT2List();
}

async function handleDeleteT2(row: ScheduleTemplateDto) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确认删除「${row.templateName}」？`, '删除');
    await deleteScheduleTemplateApi({ id: row.id });
    ElMessage.success('已删除');
    await loadT2List();
  } catch {
    // cancel
  }
}

function openT3Create(date?: string) {
  t3Form.value = {
    organizationId: organizationId.value,
    exceptionDate: date ?? new Date().toISOString().slice(0, 10),
    exceptionType: 'CLOSED',
    autoApplyOnGenerate: true,
  };
  t3EditVisible.value = true;
}

async function openT3Edit(row: CalendarExceptionDto) {
  if (!row.id) return;
  t3Form.value = await loadCalendarExceptionApi({ id: row.id });
  t3EditVisible.value = true;
}

function handleT3MonthSelect(date: string) {
  openT3Create(date);
}

const t3NeedsOverrideTemplate = computed(() => {
  const type = t3Form.value.exceptionType;
  return type === 'REDUCED' || type === 'OVERRIDE' || type === 'EXTRA';
});

async function handleSaveT3() {
  const form = t3Form.value;
  if (!form.exceptionDate || !form.exceptionType) {
    ElMessage.warning('请填写日期与类型');
    return;
  }
  if (t3NeedsOverrideTemplate.value && !form.overrideScheduleTemplateId) {
    ElMessage.warning('REDUCED/OVERRIDE/EXTRA 须选择替换日排班');
    return;
  }
  await saveCalendarExceptionApi({
    id: form.id,
    organizationId: organizationId.value,
    exceptionDate: form.exceptionDate,
    rangeEnd: form.rangeEnd,
    exceptionType: form.exceptionType,
    overrideScheduleTemplateId: form.overrideScheduleTemplateId,
    reason: form.reason,
    autoApplyOnGenerate: form.autoApplyOnGenerate !== false,
  });
  ElMessage.success('日历例外已保存');
  t3EditVisible.value = false;
  await loadT3Data();
}

async function handleDeleteT3(row: CalendarExceptionDto) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确认删除 ${row.exceptionDate} 的例外？`, '删除');
    await deleteCalendarExceptionApi({ id: row.id });
    ElMessage.success('已删除');
    await loadT3Data();
  } catch {
    // cancel
  }
}

/** 打开周排班编辑前刷新日排班下拉，并校验前置数据 */
async function prepareTwDialog(): Promise<boolean> {
  if (!ensureOrganizationId()) {
    return false;
  }
  await loadAuxData();
  if (t2Options.value.length === 0) {
    ElMessage.warning('请先在「② 日排班」中新建至少一条日排班');
    activeTab.value = 'schedule';
    return false;
  }
  return true;
}

async function openTwCreate() {
  if (!(await prepareTwDialog())) {
    return;
  }
  twForm.value = {
    enabled: true,
    fallbackBookable: false,
    isDefault: true,
    organizationId: organizationId.value,
    items: [{ weekday: 1, enabled: true, sortNo: 0 }],
  };
  twEditVisible.value = true;
}

async function openTwEdit(row: WeekScheduleTemplateDto) {
  if (!row.id) return;
  if (!(await prepareTwDialog())) {
    return;
  }
  const loaded = await loadWeekScheduleTemplateApi({ id: row.id });
  twForm.value = {
    ...loaded,
    items: normalizeTwFormItems(loaded.items),
  };
  twEditVisible.value = true;
}

function addTwItemRow() {
  const items = twForm.value.items ?? [];
  items.push({ weekday: 1, enabled: true, sortNo: items.length });
  twForm.value.items = items;
}

function removeTwItemRow(index: number) {
  twForm.value.items?.splice(index, 1);
}

/** 保存后引导去门诊排班落地 */
async function promptGoOutpatientSchedule() {
  try {
    await ElMessageBox.confirm(
      '模板已保存，患者尚不可约。请前往「门诊排班」→「生成正式排班」选择日期，再逐条发布并放号。',
      '下一步：门诊排班',
      { confirmButtonText: '前往门诊排班', cancelButtonText: '稍后', type: 'info' },
    );
    await router.push('/booking/outpatient-schedule');
  } catch {
    // 用户选择稍后
  }
}

async function handleSaveTw() {
  const form = twForm.value;
  if (!ensureOrganizationId()) {
    return;
  }
  if (!form.templateName?.trim()) {
    ElMessage.warning('请填写名称');
    return;
  }
  const items = form.items ?? [];
  if (items.length === 0) {
    ElMessage.warning('请至少添加一行周明细');
    return;
  }
  for (const row of items) {
    if (!row.weekday || !row.dayScheduleTemplateId) {
      ElMessage.warning('周明细每一行都须选择「星期几」和「日排班」');
      return;
    }
  }
  await saveWeekScheduleTemplateApi({
    id: form.id,
    organizationId: organizationId.value,
    templateName: form.templateName.trim(),
    fallbackBookable: false,
    isDefault: form.isDefault !== false,
    enabled: form.enabled !== false,
    items: items.map((row: WeekScheduleTemplateItemDto) => ({
      weekday: row.weekday!,
      dayScheduleTemplateId: toBookingId(row.dayScheduleTemplateId)!,
      sortNo: row.sortNo,
      enabled: row.enabled !== false,
    })),
  });
  ElMessage.success('周排班已保存');
  twEditVisible.value = false;
  await loadTwList();
  await promptGoOutpatientSchedule();
}

async function handleDeleteTw(row: WeekScheduleTemplateDto) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确认删除周排班「${row.templateName}」？`, '删除');
    await deleteWeekScheduleTemplateApi({ id: row.id });
    ElMessage.success('已删除');
    await loadTwList();
  } catch {
    // cancel
  }
}

onMounted(async () => {
  await loadT1List();
  await loadT2List();
  await loadTwList();
  await loadAuxData();
  await loadT3Data();
});

watch(organizationId, () => {
  void loadT1List();
  void loadT2List();
  void loadTwList();
  void loadAuxData();
});

watch(t3Month, () => {
  void loadT3Month();
});

const twDaySchedulePlaceholder = computed(() => {
  if (!t2Options.value.length) {
    return '请先在「② 日排班」新建日排班';
  }
  return '选择日排班';
});
</script>

<template>
  <Page auto-content-height>
    <div class="schedule-template-page">
      <ScheduleTemplateGuide />
      <ElAlert
        v-if="!organizationId"
        type="warning"
        :closable="false"
        show-icon
        title="请先在系统顶部选择登录科室，否则无法保存排班模板"
        class="schedule-template-page__org-alert"
      />
      <ElTabs v-model="activeTab">
        <ElTabPane label="① 放号时段" name="slot">
          <TableLayout
            v-model:query-params="t1Query"
            :form-items="t1FormItems"
            :table-columns="t1Columns"
            :table-data="t1List"
            :total="t1Total"
            :loading="t1Loading"
            @search="
              () => {
                t1Query.pageNo = 1;
                loadT1List();
              }
            "
            @pagination="
              (p) => {
                t1Query.pageNo = p.page;
                t1Query.pageSize = p.limit;
                loadT1List();
              }
            "
          >
            <template #toolbar-actions>
              <ElButton type="primary" @click="openT1Create">新建放号时段</ElButton>
            </template>
            <template #enabled="{ row }">{{ row.enabled ? '是' : '否' }}</template>
            <template #actions="{ row }">
              <ElButton link type="primary" @click="openT1Edit(row)">编辑</ElButton>
              <ElButton link type="danger" @click="handleDeleteT1(row)">删除</ElButton>
            </template>
          </TableLayout>
        </ElTabPane>

        <ElTabPane label="② 日排班" name="schedule">
          <TableLayout
            v-model:query-params="t2Query"
            :form-items="t2FormItems"
            :table-columns="t2Columns"
            :table-data="t2List"
            :total="t2Total"
            :loading="t2Loading"
            @search="
              () => {
                t2Query.pageNo = 1;
                loadT2List();
              }
            "
            @pagination="
              (p) => {
                t2Query.pageNo = p.page;
                t2Query.pageSize = p.limit;
                loadT2List();
              }
            "
          >
            <template #toolbar-actions>
              <ElButton type="primary" @click="openT2Create">新建日排班</ElButton>
            </template>
            <template #enabled="{ row }">{{ row.enabled ? '是' : '否' }}</template>
            <template #actions="{ row }">
              <ElButton link type="primary" @click="openT2Edit(row)">编辑</ElButton>
              <ElButton link type="danger" @click="handleDeleteT2(row)">删除</ElButton>
            </template>
          </TableLayout>
        </ElTabPane>

        <ElTabPane label="③ 周排班" name="week">
          <TableLayout
            v-model:query-params="twQuery"
            :form-items="twFormItems"
            :table-columns="twColumns"
            :table-data="twList"
            :total="twTotal"
            :loading="twLoading"
            @search="
              () => {
                twQuery.pageNo = 1;
                loadTwList();
              }
            "
            @pagination="
              (p) => {
                twQuery.pageNo = p.page;
                twQuery.pageSize = p.limit;
                loadTwList();
              }
            "
          >
            <template #toolbar-actions>
              <ElButton type="primary" @click="openTwCreate">新建周排班</ElButton>
            </template>
            <template #isDefault="{ row }">{{ row.isDefault ? '是' : '否' }}</template>
            <template #fallbackBookable="{ row }">{{
              row.fallbackBookable ? '是' : '否'
            }}</template>
            <template #enabled="{ row }">{{ row.enabled ? '是' : '否' }}</template>
            <template #actions="{ row }">
              <ElButton link type="primary" @click="openTwEdit(row)">编辑</ElButton>
              <ElButton link type="danger" @click="handleDeleteTw(row)">删除</ElButton>
            </template>
          </TableLayout>
        </ElTabPane>

        <ElTabPane label="放号策略（可选）" name="releasePolicy">
          <ReleasePolicyPanel @changed="loadAuxData" />
        </ElTabPane>

        <ElTabPane label="节假日例外（可选）" name="exception">
          <div class="t3-layout">
            <aside class="t3-layout__calendar">
              <div class="t3-layout__calendar-toolbar">
                <ElDatePicker
                  v-model="t3Month"
                  type="month"
                  value-format="YYYY-MM"
                  placeholder="选择月份"
                />
              </div>
              <CalendarExceptionMonth
                :days="t3MonthDays"
                :loading="t3MonthLoading"
                @select-day="handleT3MonthSelect"
              />
            </aside>
            <section class="t3-layout__list">
              <TableLayout
                v-model:query-params="t3Query"
                :form-items="t3FormItems"
                :table-columns="t3Columns"
                :table-data="t3List"
                :total="t3Total"
                :loading="t3Loading"
                @search="
                  () => {
                    t3Query.pageNo = 1;
                    loadT3List();
                  }
                "
                @pagination="
                  (p) => {
                    t3Query.pageNo = p.page;
                    t3Query.pageSize = p.limit;
                    loadT3List();
                  }
                "
              >
                <template #toolbar-actions>
                  <ElButton type="primary" @click="openT3Create()">新建例外</ElButton>
                </template>
                <template #exceptionType="{ row }">
                  {{ calendarExceptionTypeLabel(row.exceptionType) }}
                </template>
                <template #autoApply="{ row }">{{
                  row.autoApplyOnGenerate ? '是' : '否'
                }}</template>
                <template #actions="{ row }">
                  <ElButton link type="primary" @click="openT3Edit(row)">编辑</ElButton>
                  <ElButton link type="danger" @click="handleDeleteT3(row)">删除</ElButton>
                </template>
              </TableLayout>
            </section>
          </div>
        </ElTabPane>
      </ElTabs>

      <ElDialog
        v-model="t1EditVisible"
        :title="t1Form.id ? '编辑放号时段' : '新建放号时段'"
        width="560px"
      >
        <ElForm label-width="88px">
          <ElFormItem label="名称" required>
            <ElInput v-model="t1Form.templateName" placeholder="如：上午20号" />
          </ElFormItem>
          <ElFormItem label="启用">
            <ElSwitch v-model="t1Form.enabled" />
          </ElFormItem>
          <ElFormItem label="时段">
            <div class="slot-rows">
              <div v-for="(row, idx) in t1Form.slotTemplates" :key="idx" class="slot-row">
                <ElInput v-model="row.startTime" placeholder="08:00" style="width: 88px" />
                <span>—</span>
                <ElInput v-model="row.endTime" placeholder="12:00" style="width: 88px" />
                <ElInputNumber v-model="row.capacity" :min="1" placeholder="容量" />
                <ElButton link type="danger" @click="removeSlotRow(idx)">删</ElButton>
              </div>
              <ElButton link type="primary" @click="addSlotRow">+ 添加时段</ElButton>
            </div>
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="t1EditVisible = false">取消</ElButton>
          <ElButton type="primary" @click="handleSaveT1">保存</ElButton>
        </template>
      </ElDialog>

      <ElDialog
        v-model="t2EditVisible"
        :title="t2Form.id ? '编辑日排班' : '新建日排班'"
        width="560px"
      >
        <ElForm label-width="100px">
          <ElFormItem label="名称" required>
            <ElInput v-model="t2Form.templateName" placeholder="如：张医生专家号" />
          </ElFormItem>
          <ElFormItem label="模式" required>
            <ElSelect v-model="t2Form.scheduleMode" style="width: 100%">
              <ElOption
                v-for="item in scheduleModeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="号源类型" required>
            <ElSelect v-model="t2Form.healthcareServiceId" filterable style="width: 100%">
              <ElOption
                v-for="item in initData.healthcareServices || []"
                :key="item.value"
                :label="item.label"
                :value="bookingOptionValue(item.value)"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="医师">
            <ElSelect v-model="t2Form.practitionerId" clearable filterable style="width: 100%">
              <ElOption
                v-for="item in initData.practitioners || []"
                :key="item.value"
                :label="item.label"
                :value="bookingOptionValue(item.value)"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="诊室">
            <ElSelect v-model="t2Form.locationId" clearable filterable style="width: 100%">
              <ElOption
                v-for="item in initData.clinicLocations || []"
                :key="item.value"
                :label="item.label"
                :value="bookingOptionValue(item.value)"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="放号时段">
            <ElSelect
              v-model="t2Form.slotProvisionTemplateId"
              clearable
              filterable
              :placeholder="t1Options.length ? '请选择' : '请先在「放号时段」页新建并启用'"
              style="width: 100%"
            >
              <ElOption
                v-for="item in t1Options"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="放号策略">
            <ElSelect
              v-model="t2Form.defaultReleasePolicyId"
              clearable
              filterable
              :placeholder="
                releasePolicies.length
                  ? '可选：未选放号时段时才需要'
                  : '请先在「放号策略」Tab 新建（通常可不选）'
              "
              style="width: 100%"
            >
              <ElOption
                v-for="item in releasePolicies"
                :key="item.id"
                :label="item.policyName"
                :value="toBookingId(item.id)!"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="启用">
            <ElSwitch v-model="t2Form.enabled" />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="t2EditVisible = false">取消</ElButton>
          <ElButton type="primary" @click="handleSaveT2">保存</ElButton>
        </template>
      </ElDialog>

      <ElDialog
        v-model="twEditVisible"
        :title="twForm.id ? '编辑周排班' : '新建周排班'"
        width="680px"
      >
        <ElForm label-width="120px">
          <ElFormItem label="名称" required>
            <ElInput v-model="twForm.templateName" placeholder="内科默认周排班" />
          </ElFormItem>
          <ElFormItem label="科室默认">
            <ElSwitch v-model="twForm.isDefault" />
            <p class="schedule-template-page__hint">
              建议开启：门诊排班周历上才会显示<strong>预览格</strong>（尚未生成正式排班时的惯例提示）
            </p>
          </ElFormItem>
          <ElFormItem label="启用">
            <ElSwitch v-model="twForm.enabled" />
          </ElFormItem>
          <ElFormItem label="周明细" required>
            <div class="slot-rows">
              <p
                v-if="!t2Options.length"
                class="schedule-template-page__hint schedule-template-page__hint--warn"
              >
                暂无日排班可选，请先到「② 日排班」Tab 新建。
              </p>
              <div v-for="(row, idx) in twForm.items" :key="idx" class="slot-row tw-item-row">
                <ElSelect v-model="row.weekday" style="width: 88px">
                  <ElOption
                    v-for="(label, wIdx) in weekdayLabels"
                    :key="wIdx + 1"
                    :label="label"
                    :value="wIdx + 1"
                  />
                </ElSelect>
                <ElSelect
                  v-model="row.dayScheduleTemplateId"
                  filterable
                  :placeholder="twDaySchedulePlaceholder"
                  :disabled="!t2Options.length"
                  style="width: 280px"
                >
                  <ElOption
                    v-for="item in t2Options"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
                <ElButton link type="danger" @click="removeTwItemRow(idx)">删</ElButton>
              </div>
              <ElButton link type="primary" @click="addTwItemRow"
                >+ 添加一行（星期几 + 日排班）</ElButton
              >
            </div>
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="twEditVisible = false">取消</ElButton>
          <ElButton type="primary" @click="handleSaveTw">保存</ElButton>
        </template>
      </ElDialog>

      <ElDialog
        v-model="t3EditVisible"
        :title="t3Form.id ? '编辑日历例外' : '新建日历例外'"
        width="560px"
      >
        <ElForm label-width="108px">
          <ElFormItem label="类型" required>
            <ElSelect v-model="t3Form.exceptionType" style="width: 100%">
              <ElOption
                v-for="item in calendarExceptionTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="起始日" required>
            <ElDatePicker
              v-model="t3Form.exceptionDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </ElFormItem>
          <ElFormItem label="结束日">
            <ElDatePicker
              v-model="t3Form.rangeEnd"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="空=单日"
              style="width: 100%"
            />
          </ElFormItem>
          <ElFormItem v-if="t3NeedsOverrideTemplate" label="替换日排班" required>
            <ElSelect v-model="t3Form.overrideScheduleTemplateId" filterable style="width: 100%">
              <ElOption
                v-for="item in t2Options"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="说明">
            <ElInput v-model="t3Form.reason" placeholder="国庆、调休等" />
          </ElFormItem>
          <ElFormItem label="参与生成">
            <ElSwitch v-model="t3Form.autoApplyOnGenerate" />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="t3EditVisible = false">取消</ElButton>
          <ElButton type="primary" @click="handleSaveT3">保存</ElButton>
        </template>
      </ElDialog>
    </div>
  </Page>
</template>

<style scoped>
.schedule-template-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.schedule-template-page__org-alert {
  margin-bottom: 12px;
}

.schedule-template-page__hint {
  width: 100%;
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

.schedule-template-page__hint--warn {
  color: var(--el-color-warning);
}

.slot-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.slot-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.t3-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  min-height: 520px;
}

.t3-layout__calendar {
  padding: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.t3-layout__calendar-toolbar {
  margin-bottom: 12px;
}

.t3-layout__list {
  min-width: 0;
}
</style>
