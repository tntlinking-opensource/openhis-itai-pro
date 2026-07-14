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
import { bookingOptionValue, isSameBookingId, toBookingId } from '#/api/booking/booking-id';
import type { LoadScheduleFormInitResponse } from '#/api/booking/outpatient-schedule';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

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
import ScheduleTemplateGuide from '#/views/booking/components/schedule-template-guide.vue';
import SlotTemplateRowsField from '#/views/booking/components/slot-template-rows-field.vue';
import {
  isDepartmentScheduleMode,
  normalizeScheduleModeForForm,
  scheduleModeLabel,
  SCHEDULE_MODE_OPTIONS,
} from '#/views/booking/components/schedule-template-shared';
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
/** 日排班弹窗内按所选科室加载的下拉（号源类型 / 医师 / 放号时段） */
const t2DialogInitData = ref<LoadScheduleFormInitResponse>({});
const t2DialogT1Options = ref<{ value: string; label: string }[]>([]);

/** 周排班弹窗：先选科室与排班方式，再过滤日排班 */
type TwScheduleScope = 'DEPARTMENT' | 'PRACTITIONER';
const twDialogOrganizationId = ref<BookingEntityId>();
const twScheduleScope = ref<TwScheduleScope>('DEPARTMENT');
const twDialogInitData = ref<LoadScheduleFormInitResponse>({});
const twOrgT2List = ref<ScheduleTemplateDto[]>([]);

const twScheduleScopeOptions = [
  { value: 'DEPARTMENT' as const, label: '按科室' },
  { value: 'PRACTITIONER' as const, label: '按医师' },
];

const isTwDepartmentScope = computed(() => twScheduleScope.value === 'DEPARTMENT');

const scheduleModeOptions = SCHEDULE_MODE_OPTIONS;

/** 主数据 label 映射，供周排班日排班下拉展示 */
function buildOptionLabelMap(options?: { label?: string; value?: unknown }[]): Map<string, string> {
  const map = new Map<string, string>();
  for (const option of options ?? []) {
    const key = bookingOptionValue(option.value);
    if (key) {
      map.set(key, String(option.label ?? key));
    }
  }
  return map;
}

/** 拼接展示片段，跳过与已有片段重复的文案 */
function appendDistinctLabel(parts: string[], segment?: string): void {
  const text = segment?.trim();
  if (!text) {
    return;
  }
  if (parts.some((part) => part === text)) {
    return;
  }
  parts.push(text);
}

/** 周排班日排班下拉展示文案 */
function buildTwT2OptionLabel(
  template: ScheduleTemplateDto,
  practitionerLabelMap: Map<string, string>,
  healthcareLabelMap: Map<string, string>,
): string {
  const name = template.templateName?.trim() || String(template.id ?? '');
  const serviceLabel = healthcareLabelMap.get(
    bookingOptionValue(template.healthcareServiceId) ?? '',
  );
  if (isDepartmentScheduleMode(template.scheduleMode)) {
    const parts: string[] = ['[科室号]'];
    appendDistinctLabel(parts, name);
    appendDistinctLabel(parts, serviceLabel);
    return parts.join(' · ');
  }
  const doctorLabel =
    practitionerLabelMap.get(bookingOptionValue(template.practitionerId) ?? '') ?? '未指定医师';
  const parts: string[] = ['[医师号]'];
  appendDistinctLabel(parts, doctorLabel);
  appendDistinctLabel(parts, name);
  appendDistinctLabel(parts, serviceLabel);
  return parts.join(' · ');
}

/** 按科室加载全部日排班（供周排班弹窗过滤） */
async function loadTwDialogContext(orgId?: BookingEntityId) {
  const resolvedOrgId = toBookingId(orgId);
  if (!resolvedOrgId) {
    twOrgT2List.value = [];
    twDialogInitData.value = {};
    return;
  }
  const [init, page] = await Promise.all([
    loadScheduleFormInitApi({ organizationId: resolvedOrgId }),
    pageScheduleTemplatesApi({
      organizationId: resolvedOrgId,
      pageNo: 1,
      pageSize: 500,
    }),
  ]);
  twDialogInitData.value = init ?? {};
  twOrgT2List.value = (page?.content ?? []).filter((row) => row.enabled !== false);
}

/** 按科室 + 排班方式过滤后的日排班选项 */
const twFilteredT2Options = computed(() => {
  const orgId = twDialogOrganizationId.value;
  if (!orgId) {
    return [];
  }
  const practitionerLabelMap = buildOptionLabelMap(twDialogInitData.value.practitioners);
  const healthcareLabelMap = buildOptionLabelMap(twDialogInitData.value.healthcareServices);
  return twOrgT2List.value
    .filter((template) => {
      if (!isSameBookingId(template.organizationId, orgId)) {
        return false;
      }
      if (isTwDepartmentScope.value) {
        return isDepartmentScheduleMode(template.scheduleMode);
      }
      return !isDepartmentScheduleMode(template.scheduleMode);
    })
    .map((template) => {
      const value = toBookingId(template.id);
      if (!value) {
        return null;
      }
      return {
        value,
        label: buildTwT2OptionLabel(template, practitionerLabelMap, healthcareLabelMap),
      };
    })
    .filter((item): item is { value: string; label: string } => item !== null);
});

/** 周排班弹窗可选门诊科室 */
const twDepartmentOptions = computed(
  () => twDialogInitData.value.departments ?? initData.value.departments ?? [],
);

/** 周明细是否可编辑（须先选出诊科室） */
const twWeekGridDisabled = computed(() => !twDialogOrganizationId.value);

/** 清除周明细里已不在当前过滤结果中的日排班 */
function sanitizeTwWeekSelections() {
  const allowed = new Set(twFilteredT2Options.value.map((item) => item.value));
  for (const row of twForm.value.items ?? []) {
    const templateId = toBookingId(row.dayScheduleTemplateId);
    if (templateId && !allowed.has(templateId)) {
      row.dayScheduleTemplateId = undefined;
    }
  }
}

/** 编辑回显：从已绑定的日排班推断排班方式 */
function inferTwDialogScopeFromItems(items?: WeekScheduleTemplateItemDto[]): TwScheduleScope {
  const templates: ScheduleTemplateDto[] = [];
  for (const row of items ?? []) {
    const templateId = toBookingId(row.dayScheduleTemplateId);
    if (!templateId) {
      continue;
    }
    const template = twOrgT2List.value.find((item) => isSameBookingId(item.id, templateId));
    if (template) {
      templates.push(template);
    }
  }
  if (!templates.length) {
    return 'DEPARTMENT';
  }
  if (templates.every((item) => isDepartmentScheduleMode(item.scheduleMode))) {
    return 'DEPARTMENT';
  }
  return 'PRACTITIONER';
}

/** 重置周排班弹窗筛选条件（新建） */
function resetTwDialogFilters(orgId?: BookingEntityId) {
  twDialogOrganizationId.value = pickDefaultT2OrganizationId(orgId);
  twScheduleScope.value = 'DEPARTMENT';
}

/** 日排班是否为「医师号」模式（须选出诊医师） */
const isPractitionerSchedule = computed(() => t2Form.value.scheduleMode !== 'MODE-B');

/** 科室号可选的门诊科室（来自 formInit） */
const departmentOptions = computed(() => initData.value.departments ?? []);

/** 放号时段 id → 名称，供日排班列表回显 */
const t1OptionLabelMap = computed(() => {
  const map = new Map<string, string>();
  for (const option of t1Options.value) {
    map.set(option.value, option.label);
  }
  return map;
});

const t1FormItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '名称' },
];

const t2FormItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '名称' },
];

const t1Columns: TableColumn[] = [
  { prop: 'templateName', label: '名称', minWidth: 140 },
  { prop: 'slotSummary', label: '时段', minWidth: 200, slot: 'slotSummary' },
  { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
];

const t2Columns: TableColumn[] = [
  { prop: 'templateName', label: '名称', minWidth: 140 },
  { prop: 'scheduleMode', label: '号别类型', width: 90, slot: 'scheduleMode' },
  { prop: 'slotProvisionTemplateId', label: '放号时段', minWidth: 120, slot: 'slotTemplate' },
  { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
];

const twColumns: TableColumn[] = [
  { prop: 'templateName', label: '名称', minWidth: 160 },
  { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
];

const twFormItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '名称' },
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
    scheduleMode: normalizeScheduleModeForForm(dto.scheduleMode),
    organizationId: toBookingId(dto.organizationId),
    healthcareServiceId: toBookingId(dto.healthcareServiceId),
    practitionerId: toBookingId(dto.practitionerId),
    slotProvisionTemplateId: toBookingId(dto.slotProvisionTemplateId),
  };
}

/** 日排班弹窗默认科室：优先登录科室，否则取可选列表首项 */
function pickDefaultT2OrganizationId(preferred?: BookingEntityId): BookingEntityId | undefined {
  const departments = departmentOptions.value;
  const pref = toBookingId(preferred) ?? organizationId.value;
  if (pref && departments.some((item) => isSameBookingId(item.value, pref))) {
    return pref;
  }
  return toBookingId(departments[0]?.value) ?? pref;
}

/** 按科室刷新日排班弹窗内的号源类型、医师、放号时段下拉；医师号时传 practitionerId 收窄号源 */
async function loadT2DialogContext(orgId?: BookingEntityId, practitionerId?: BookingEntityId) {
  const resolvedOrgId = toBookingId(orgId);
  if (!resolvedOrgId) {
    t2DialogInitData.value = {};
    t2DialogT1Options.value = [];
    return;
  }
  const resolvedPractitionerId = toBookingId(practitionerId);
  const [init, t1Opts] = await Promise.all([
    loadScheduleFormInitApi({
      organizationId: resolvedOrgId,
      practitionerId: resolvedPractitionerId,
    }),
    listSlotProvisionTemplateOptionsApi({ organizationId: resolvedOrgId }),
  ]);
  t2DialogInitData.value = init ?? {};
  t2DialogT1Options.value = (t1Opts?.options ?? []).map((option) => ({
    value: bookingOptionValue(option.value),
    label: String(option.label ?? option.value),
  }));
}

/** 医师号模式下是否已选出诊医师（号源类型须在其后选择） */
const t2PractitionerSelected = computed(
  () => isPractitionerSchedule.value && Boolean(toBookingId(t2Form.value.practitionerId)),
);

/** 科室号保存时使用的科室主键 */
function resolveT2SaveOrganizationId(
  form: Partial<ScheduleTemplateDto>,
): BookingEntityId | undefined {
  if (form.scheduleMode === 'MODE-B') {
    return toBookingId(form.organizationId);
  }
  return organizationId.value;
}

/** 放号时段列表展示：各段起止与容量 */
function formatSlotSummary(row: SlotProvisionTemplateDto): string {
  const items = row.slotTemplates ?? [];
  if (!items.length) {
    return '-';
  }
  return items
    .map((item) => `${item.startTime ?? ''}-${item.endTime ?? ''}（${item.capacity ?? 1}号）`)
    .join('；');
}

/** 日排班列表展示：绑定的放号时段名称 */
function resolveSlotTemplateLabel(slotProvisionTemplateId?: BookingEntityId): string {
  if (!slotProvisionTemplateId) {
    return '-';
  }
  const key = toBookingId(slotProvisionTemplateId);
  if (!key) {
    return '-';
  }
  return t1OptionLabelMap.value.get(key) ?? '-';
}

/** 排班模板按登录科室隔离，未选科室时阻断保存类操作 */
function ensureOrganizationId(): boolean {
  if (!organizationId.value) {
    ElMessage.warning('请先在系统顶部选择登录科室');
    return false;
  }
  return true;
}

/** 周明细固定 7 行：周一～周日，未选日排班表示该日不出诊 */
function buildEmptyTwWeekItems(): WeekScheduleTemplateItemDto[] {
  return weekdayLabels.map((_, index) => ({
    weekday: index + 1,
    enabled: true,
    sortNo: 0,
  }));
}

/** 编辑回显：将已保存明细合并进固定周网格（同 weekday 多条时取首条） */
function mergeTwItemsToWeekGrid(
  items?: WeekScheduleTemplateItemDto[],
): WeekScheduleTemplateItemDto[] {
  const grid = buildEmptyTwWeekItems();
  if (!items?.length) {
    return grid;
  }
  for (const row of items) {
    const weekday = row.weekday;
    if (!weekday || weekday < 1 || weekday > 7) {
      continue;
    }
    const slot = grid[weekday - 1];
    if (slot && !slot.dayScheduleTemplateId) {
      grid[weekday - 1] = {
        ...row,
        weekday,
        dayScheduleTemplateId: toBookingId(row.dayScheduleTemplateId),
        sortNo: row.sortNo ?? 0,
        enabled: row.enabled !== false,
      };
    }
  }
  return grid;
}

async function loadAuxData() {
  if (!organizationId.value) return;
  const [init, t1Opts] = await Promise.all([
    loadScheduleFormInitApi({ organizationId: organizationId.value }),
    listSlotProvisionTemplateOptionsApi({ organizationId: organizationId.value }),
  ]);
  initData.value = init ?? {};
  t1Options.value = (t1Opts?.options ?? []).map((o) => ({
    value: bookingOptionValue(o.value),
    label: String(o.label ?? o.value),
  }));
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
    enabled: true,
  });
  ElMessage.success('放号时段已保存');
  t1EditVisible.value = false;
  await loadT1List();
  await loadAuxData();
}

/** 打开日排班编辑前校验是否已维护放号时段 */
async function prepareT2Dialog(preferredOrgId?: BookingEntityId): Promise<boolean> {
  if (!ensureOrganizationId()) {
    return false;
  }
  await loadAuxData();
  const dialogOrgId = pickDefaultT2OrganizationId(preferredOrgId);
  await loadT2DialogContext(dialogOrgId);
  if (t2DialogT1Options.value.length === 0) {
    ElMessage.warning('请先在「① 放号时段」中新建至少一套放号时段');
    activeTab.value = 'slot';
    return false;
  }
  return true;
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
  if (!(await prepareT2Dialog())) {
    return;
  }
  const defaultOrgId = pickDefaultT2OrganizationId();
  t2Form.value = {
    enabled: true,
    organizationId: defaultOrgId,
    scheduleMode: 'MODE-A',
    priority: 0,
    slotProvisionTemplateId: t2DialogT1Options.value[0]?.value,
  };
  t2EditVisible.value = true;
}

async function openT2Edit(row: ScheduleTemplateDto) {
  if (!row.id) return;
  const loaded = await loadScheduleTemplateApi({ id: row.id });
  const normalized = normalizeScheduleTemplateForm(loaded);
  const mode = normalized.scheduleMode ?? 'MODE-A';
  const dialogOrgId =
    mode === 'MODE-B'
      ? pickDefaultT2OrganizationId(toBookingId(normalized.organizationId))
      : organizationId.value;
  if (!(await prepareT2Dialog(dialogOrgId))) {
    return;
  }
  t2Form.value = {
    ...normalized,
    organizationId: mode === 'MODE-B' ? dialogOrgId : organizationId.value,
  };
  await loadT2DialogContext(
    mode === 'MODE-B' ? dialogOrgId : organizationId.value,
    mode === 'MODE-A' ? toBookingId(normalized.practitionerId) : undefined,
  );
  t2EditVisible.value = true;
}

async function handleSaveT2() {
  const form = t2Form.value;
  if (!ensureOrganizationId()) {
    return;
  }
  if (!form.templateName?.trim()) {
    ElMessage.warning('请填写名称');
    return;
  }
  const scheduleMode = form.scheduleMode ?? 'MODE-A';
  const saveOrganizationId = resolveT2SaveOrganizationId(form);
  if (scheduleMode === 'MODE-A' && !form.practitionerId) {
    ElMessage.warning('医师号须先选择出诊医师');
    return;
  }
  if (scheduleMode === 'MODE-B' && !saveOrganizationId) {
    ElMessage.warning('科室号须选择出诊科室');
    return;
  }
  if (!form.healthcareServiceId) {
    ElMessage.warning(
      scheduleMode === 'MODE-A' ? '请选择该医师可接诊的号源类型' : '请选择号源类型',
    );
    return;
  }
  if (!form.slotProvisionTemplateId) {
    ElMessage.warning('请选择放号时段');
    return;
  }
  await saveScheduleTemplateApi({
    id: form.id,
    organizationId: saveOrganizationId!,
    templateName: form.templateName.trim(),
    scheduleMode,
    practitionerId: scheduleMode === 'MODE-A' ? form.practitionerId : undefined,
    healthcareServiceId: form.healthcareServiceId,
    slotProvisionTemplateId: form.slotProvisionTemplateId,
    effectiveStart: form.effectiveStart,
    effectiveEnd: form.effectiveEnd,
    priority: Number(form.priority ?? 0),
    enabled: true,
  });
  ElMessage.success('日排班已保存');
  t2EditVisible.value = false;
  await loadT2List();
  await loadAuxData();
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

/** 打开周排班弹窗前校验登录科室并刷新主数据 */
async function prepareTwDialog(): Promise<boolean> {
  if (!ensureOrganizationId()) {
    return false;
  }
  await loadAuxData();
  return true;
}

/** 打开周排班弹窗后加载科室上下文，并校验是否存在日排班 */
async function ensureTwDialogContext(): Promise<boolean> {
  await loadTwDialogContext(twDialogOrganizationId.value);
  if (twOrgT2List.value.length === 0) {
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
  resetTwDialogFilters();
  if (!(await ensureTwDialogContext())) {
    return;
  }
  twForm.value = {
    enabled: true,
    fallbackBookable: false,
    isDefault: true,
    organizationId: twDialogOrganizationId.value,
    items: buildEmptyTwWeekItems(),
  };
  twEditVisible.value = true;
}

async function openTwEdit(row: WeekScheduleTemplateDto) {
  if (!row.id) return;
  if (!(await prepareTwDialog())) {
    return;
  }
  const loaded = await loadWeekScheduleTemplateApi({ id: row.id });
  resetTwDialogFilters(toBookingId(loaded.organizationId));
  if (!(await ensureTwDialogContext())) {
    return;
  }
  const inferred = inferTwDialogScopeFromItems(loaded.items);
  twScheduleScope.value = inferred;
  twForm.value = {
    ...loaded,
    organizationId: twDialogOrganizationId.value,
    items: mergeTwItemsToWeekGrid(loaded.items),
  };
  sanitizeTwWeekSelections();
  twEditVisible.value = true;
}

async function handleSaveTw() {
  const form = twForm.value;
  const saveOrgId = toBookingId(twDialogOrganizationId.value);
  if (!saveOrgId) {
    ElMessage.warning('请选择出诊科室');
    return;
  }
  if (!form.templateName?.trim()) {
    ElMessage.warning('请填写名称');
    return;
  }
  const items = form.items ?? [];
  const filledItems = items.filter((row) => row.weekday && toBookingId(row.dayScheduleTemplateId));
  if (filledItems.length === 0) {
    ElMessage.warning('请至少为一个工作日选择日排班');
    return;
  }
  const allowed = new Set(twFilteredT2Options.value.map((item) => item.value));
  for (const row of filledItems) {
    const templateId = toBookingId(row.dayScheduleTemplateId);
    if (!templateId || !allowed.has(templateId)) {
      ElMessage.warning('所选日排班与当前科室、排班方式不匹配，请重新选择');
      return;
    }
  }
  await saveWeekScheduleTemplateApi({
    id: form.id,
    organizationId: saveOrgId,
    templateName: form.templateName.trim(),
    fallbackBookable: false,
    isDefault: true,
    enabled: true,
    items: filledItems.map((row: WeekScheduleTemplateItemDto) => ({
      weekday: row.weekday!,
      dayScheduleTemplateId: toBookingId(row.dayScheduleTemplateId)!,
      sortNo: row.sortNo,
      enabled: row.enabled !== false,
    })),
  });
  ElMessage.success('周排班已保存');
  twEditVisible.value = false;
  await loadTwList();
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

/** 号别类型切换：科室号须选科室并刷新下拉；医师号沿用登录科室 */
watch(
  () => t2Form.value.scheduleMode,
  async (mode) => {
    if (!t2EditVisible.value) {
      return;
    }
    if (mode === 'MODE-B') {
      t2Form.value.practitionerId = undefined;
      t2Form.value.organizationId = pickDefaultT2OrganizationId(t2Form.value.organizationId);
      await loadT2DialogContext(toBookingId(t2Form.value.organizationId));
      if (
        t2Form.value.slotProvisionTemplateId &&
        !t2DialogT1Options.value.some((item) =>
          isSameBookingId(item.value, t2Form.value.slotProvisionTemplateId),
        )
      ) {
        t2Form.value.slotProvisionTemplateId = t2DialogT1Options.value[0]?.value;
      }
      return;
    }
    t2Form.value.organizationId = organizationId.value;
    t2Form.value.healthcareServiceId = undefined;
    await loadT2DialogContext(organizationId.value);
  },
);

/** 医师号切换出诊医师时，按用户管理中的可接诊号源收窄号源类型 */
watch(
  () => t2Form.value.practitionerId,
  async (practitionerId, prevId) => {
    if (!t2EditVisible.value || t2Form.value.scheduleMode !== 'MODE-A') {
      return;
    }
    if (isSameBookingId(practitionerId, prevId)) {
      return;
    }
    await loadT2DialogContext(organizationId.value, toBookingId(practitionerId));
    if (
      t2Form.value.healthcareServiceId &&
      !t2DialogInitData.value.healthcareServices?.some((item) =>
        isSameBookingId(item.value, t2Form.value.healthcareServiceId),
      )
    ) {
      t2Form.value.healthcareServiceId = undefined;
    }
  },
);

/** 科室号切换出诊科室时，按新科室刷新号源类型与放号时段 */
watch(
  () => t2Form.value.organizationId,
  async (orgId, prevOrgId) => {
    if (!t2EditVisible.value || t2Form.value.scheduleMode !== 'MODE-B') {
      return;
    }
    if (isSameBookingId(orgId, prevOrgId)) {
      return;
    }
    await loadT2DialogContext(toBookingId(orgId));
    t2Form.value.healthcareServiceId = undefined;
    if (
      t2Form.value.slotProvisionTemplateId &&
      !t2DialogT1Options.value.some((item) =>
        isSameBookingId(item.value, t2Form.value.slotProvisionTemplateId),
      )
    ) {
      t2Form.value.slotProvisionTemplateId = t2DialogT1Options.value[0]?.value;
    }
  },
);

/** 周排班切换出诊科室：重载日排班并清除无效明细 */
watch(
  () => twDialogOrganizationId.value,
  async (orgId, prevOrgId) => {
    if (!twEditVisible.value || isSameBookingId(orgId, prevOrgId)) {
      return;
    }
    await loadTwDialogContext(orgId);
    sanitizeTwWeekSelections();
  },
);

/** 周排班切换排班方式：刷新可选日排班并清除不匹配明细 */
watch(twScheduleScope, () => {
  if (!twEditVisible.value) {
    return;
  }
  sanitizeTwWeekSelections();
});

const twDaySchedulePlaceholder = computed(() => {
  if (!twDialogOrganizationId.value) {
    return '请先选择出诊科室';
  }
  if (!twFilteredT2Options.value.length) {
    return isTwDepartmentScope.value ? '该科室暂无科室号日排班' : '该科室暂无医师号日排班';
  }
  return '选择日排班（留空表示不出诊）';
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
            <template #slotSummary="{ row }">{{ formatSlotSummary(row) }}</template>
            <template #actions="{ row }">
              <ElButton link type="primary" @click="openT1Edit(row)">编辑</ElButton>
              <ElButton link type="danger" @click="handleDeleteT1(row)">删除</ElButton>
            </template>
          </TableLayout>
        </ElTabPane>

        <ElTabPane label="② 日排班" name="schedule">
          <ElAlert
            v-if="t1Total === 0"
            type="warning"
            :closable="false"
            show-icon
            title="请先在「① 放号时段」新建至少一套时段，再配置日排班"
            class="schedule-template-page__tab-alert"
          />
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
            <template #scheduleMode="{ row }">{{ scheduleModeLabel(row.scheduleMode) }}</template>
            <template #slotTemplate="{ row }">
              {{ resolveSlotTemplateLabel(row.slotProvisionTemplateId) }}
            </template>
            <template #actions="{ row }">
              <ElButton link type="primary" @click="openT2Edit(row)">编辑</ElButton>
              <ElButton link type="danger" @click="handleDeleteT2(row)">删除</ElButton>
            </template>
          </TableLayout>
        </ElTabPane>

        <ElTabPane label="③ 周排班" name="week">
          <ElAlert
            v-if="t2Total === 0"
            type="warning"
            :closable="false"
            show-icon
            title="请先在「② 日排班」新建至少一条日排班，再配置周排班"
            class="schedule-template-page__tab-alert"
          />
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
            <template #actions="{ row }">
              <ElButton link type="primary" @click="openTwEdit(row)">编辑</ElButton>
              <ElButton link type="danger" @click="handleDeleteTw(row)">删除</ElButton>
            </template>
          </TableLayout>
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
          <SlotTemplateRowsField
            :model-value="t1Form.slotTemplates || []"
            label="时段"
            @update:model-value="t1Form.slotTemplates = $event"
          />
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
          <ElFormItem label="号别类型" required>
            <ElSelect v-model="t2Form.scheduleMode" style="width: 100%">
              <ElOption
                v-for="item in scheduleModeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
            <p class="schedule-template-page__hint">
              医师号：指名到医生；科室号：只排到科室，到院再分诊
            </p>
          </ElFormItem>
          <ElFormItem v-if="!isPractitionerSchedule" label="出诊科室" required>
            <ElSelect v-model="t2Form.organizationId" filterable style="width: 100%">
              <ElOption
                v-for="item in departmentOptions"
                :key="item.value"
                :label="item.label"
                :value="bookingOptionValue(item.value)"
              />
            </ElSelect>
            <p
              v-if="!departmentOptions.length"
              class="schedule-template-page__hint schedule-template-page__hint--warn"
            >
              暂无可选门诊科室，请先在基础资料维护科室或在顶部切换登录科室
            </p>
            <p v-else class="schedule-template-page__hint">
              患者将预约到所选科室；列表按顶部登录科室展示，查看其它科室模板请先切换登录科室
            </p>
          </ElFormItem>
          <ElFormItem v-if="isPractitionerSchedule" label="出诊医师" required>
            <ElSelect v-model="t2Form.practitionerId" filterable style="width: 100%">
              <ElOption
                v-for="item in t2DialogInitData.practitioners || []"
                :key="item.value"
                :label="item.label"
                :value="bookingOptionValue(item.value)"
              />
            </ElSelect>
            <p class="schedule-template-page__hint">
              先选医师，号源类型将根据该医师在用户管理中配置的可接诊号源显示
            </p>
          </ElFormItem>
          <ElFormItem label="号源类型" required>
            <ElSelect
              v-model="t2Form.healthcareServiceId"
              filterable
              :disabled="isPractitionerSchedule && !t2PractitionerSelected"
              :placeholder="
                isPractitionerSchedule && !t2PractitionerSelected ? '请先选择出诊医师' : '请选择'
              "
              style="width: 100%"
            >
              <ElOption
                v-for="item in t2DialogInitData.healthcareServices || []"
                :key="item.value"
                :label="item.label"
                :value="bookingOptionValue(item.value)"
              />
            </ElSelect>
            <p
              v-if="
                isPractitionerSchedule &&
                t2PractitionerSelected &&
                !(t2DialogInitData.healthcareServices || []).length
              "
              class="schedule-template-page__hint schedule-template-page__hint--warn"
            >
              该医师未配置可接诊号源，请先在用户管理中维护
            </p>
          </ElFormItem>
          <ElFormItem label="放号时段" required>
            <ElSelect
              v-model="t2Form.slotProvisionTemplateId"
              filterable
              placeholder="请选择放号时段"
              style="width: 100%"
            >
              <ElOption
                v-for="item in t2DialogT1Options"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
            <p
              v-if="!t2DialogT1Options.length"
              class="schedule-template-page__hint schedule-template-page__hint--warn"
            >
              该科室暂无放号时段，请先到「① 放号时段」Tab 为对应科室新建
            </p>
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
          <ElFormItem label="出诊科室" required>
            <ElSelect v-model="twDialogOrganizationId" filterable style="width: 100%">
              <ElOption
                v-for="item in twDepartmentOptions"
                :key="item.value"
                :label="item.label"
                :value="bookingOptionValue(item.value)"
              />
            </ElSelect>
            <p
              v-if="!twDepartmentOptions.length"
              class="schedule-template-page__hint schedule-template-page__hint--warn"
            >
              暂无可选门诊科室，请先在基础资料维护或在顶部切换登录科室
            </p>
          </ElFormItem>
          <ElFormItem label="排班方式" required>
            <ElSelect v-model="twScheduleScope" style="width: 100%">
              <ElOption
                v-for="item in twScheduleScopeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
            <p class="schedule-template-page__hint">
              按科室：列出该科室全部<strong>科室号</strong>日排班；按医师：列出该科室全部<strong>医师号</strong>日排班（一周可排多位医师）
            </p>
          </ElFormItem>
          <ElFormItem label="周明细" required>
            <div class="slot-rows">
              <p
                v-if="twWeekGridDisabled"
                class="schedule-template-page__hint schedule-template-page__hint--warn"
              >
                请先选择「出诊科室」
              </p>
              <p
                v-else-if="!twFilteredT2Options.length"
                class="schedule-template-page__hint schedule-template-page__hint--warn"
              >
                {{
                  isTwDepartmentScope ? '该科室暂无科室号日排班' : '该科室暂无医师号日排班'
                }}，请先到「② 日排班」新建
              </p>
              <p v-else class="schedule-template-page__hint">
                周一～周日固定展示，各天可任选一条日排班；留空表示该日不出诊
              </p>
              <div v-for="row in twForm.items" :key="row.weekday" class="slot-row tw-item-row">
                <span class="tw-weekday-label">{{ weekdayLabels[(row.weekday ?? 1) - 1] }}</span>
                <ElSelect
                  v-model="row.dayScheduleTemplateId"
                  clearable
                  filterable
                  :placeholder="twDaySchedulePlaceholder"
                  :disabled="twWeekGridDisabled || !twFilteredT2Options.length"
                  style="width: 320px"
                >
                  <ElOption
                    v-for="item in twFilteredT2Options"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
              </div>
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

.schedule-template-page__tab-alert {
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

.tw-weekday-label {
  flex-shrink: 0;
  width: 40px;
  font-size: 13px;
  color: var(--el-text-color-regular);
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
