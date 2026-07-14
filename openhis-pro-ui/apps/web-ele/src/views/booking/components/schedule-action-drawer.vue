<script setup lang="ts">
import type { BookingEntityId } from '#/api/booking/booking-id';
import { bookingOptionValue, isSameBookingId, toBookingId } from '#/api/booking/booking-id';
import type { ScheduleCalendarCellDto } from '#/api/booking/outpatient-schedule';
import type { BookingOptionDto } from '#/api/booking/booking-types';

import { computed, reactive, ref, watch } from 'vue';

import { formatScheduleStatus, BOOKING_ACTOR_TYPE } from '#/api/booking/booking-constants';
import {
  createScheduleApi,
  deactivateScheduleApi,
  generateFromTemplateApi,
  loadScheduleFormInitApi,
  previewPublishScheduleConflictsApi,
  previewStopClinicImpactApi,
  publishScheduleApi,
  reactivateScheduleApi,
  retireScheduleApi,
  substitutePractitionerApi,
  updateScheduleDraftApi,
} from '#/api/booking/outpatient-schedule';
import type { SlotListItemDto } from '#/api/booking/slot-manage';
import { pageSlotsByDateApi } from '#/api/booking/slot-manage';
import { listSlotProvisionTemplateOptionsApi } from '#/api/booking/schedule-template';
import ProvisionSlotDialog from '#/views/booking/components/provision-slot-dialog.vue';
import ScheduleDraftForm from '#/views/booking/components/schedule-draft-form.vue';
import {
  assertDraftFormValid,
  applyHorizonToForm,
  buildDraftActors,
  createEmptyDraftForm,
  extractPlanningDate,
  fillDraftFormFromActors,
  resetDraftFormForDate,
  resolveDraftPlanningHorizon,
} from '#/views/booking/components/schedule-draft-form.utils';
import StopClinicWizard from '#/views/booking/components/stop-clinic-wizard.vue';
import {
  scheduleDepartmentLabel,
  schedulePractitionerLabel,
  scheduleServiceTypeLabel,
} from '#/views/booking/components/schedule-actor-labels';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElCheckbox,
  ElCheckboxGroup,
} from 'element-plus';

const props = defineProps<{
  visible: boolean;
  /** 新建模式：与 schedule 互斥，使用同一套草稿表单 */
  createMode?: boolean;
  organizationId?: BookingEntityId;
  selectedDate?: string;
  schedule?: ScheduleCalendarCellDto | null;
  practitioners?: BookingOptionDto[];
  departments?: BookingOptionDto[];
  healthcareServices?: BookingOptionDto[];
  slotProvisionTemplateOptions?: BookingOptionDto[];
}>();

const emit = defineEmits<{
  'update:visible': [value: boolean];
  refreshed: [payload?: { planningDate?: string }];
}>();

const draftForm = reactive(createEmptyDraftForm());

/** 与排班设置日排班弹窗同源的下拉数据 */
const draftFormInitData = ref<{
  practitioners?: BookingOptionDto[];
  healthcareServices?: BookingOptionDto[];
}>({});
const draftT1Options = ref<BookingOptionDto[]>([]);

/** 按科室刷新医师、号源、放号时段（医师号时传 practitionerId 收窄号源） */
async function loadDraftFormContext(orgId?: BookingEntityId, practitionerId?: BookingEntityId) {
  const resolvedOrgId = toBookingId(orgId);
  if (!resolvedOrgId) {
    draftFormInitData.value = {};
    draftT1Options.value = [];
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
  draftFormInitData.value = {
    practitioners: init?.practitioners ?? [],
    healthcareServices: init?.healthcareServices ?? [],
  };
  draftT1Options.value = t1Opts?.options ?? [];
}

const provisionVisible = ref(false);
const stopWizardVisible = ref(false);
const materializeLoading = ref(false);
const substitutePractitionerId = ref<number>();
const substituteReason = ref('');
const scheduleSlots = ref<SlotListItemDto[]>([]);
const partialSlotIds = ref<BookingEntityId[]>([]);
const partialStopWizardVisible = ref(false);
const slotsLoading = ref(false);
const publishLoading = ref(false);
const retireLoading = ref(false);
/** 模板生成的草稿默认只读确认；需改字段时再切编辑 */
const draftEditMode = ref(false);

const isCreateMode = computed(() => props.createMode === true);

const isExistingDraft = computed(
  () =>
    !isCreateMode.value && props.schedule?.statusEnum === 'draft' && !!props.schedule?.scheduleId,
);

const canRetireSchedule = computed(
  () =>
    !!props.schedule?.scheduleId &&
    (props.schedule?.statusEnum === 'draft' || props.schedule?.statusEnum === 'inactive'),
);

const isTemplateCell = computed(
  () => props.schedule?.statusEnum === 'FROM_TEMPLATE' || props.schedule?.fromTemplate === true,
);

const isDraft = computed(() => isCreateMode.value || props.schedule?.statusEnum === 'draft');

/** 模板/生成带入的草稿：只读摘要 + 发布，不要求重填表单 */
const showDraftPublishPanel = computed(() => isExistingDraft.value && !draftEditMode.value);

const showDraftForm = computed(
  () => isCreateMode.value || (isExistingDraft.value && draftEditMode.value),
);

const drawerTitle = computed(() => {
  if (isCreateMode.value) {
    return '新建排班';
  }
  if (showDraftPublishPanel.value) {
    return '草稿排班 — 待发布';
  }
  if (isDraft.value) {
    return '编辑草稿排班';
  }
  return '日程详情';
});

const planningHorizonDisplay = computed(() => {
  const start = props.schedule?.planningHorizonStart;
  const end = props.schedule?.planningHorizonEnd;
  if (!start || !end) {
    return '—';
  }
  const startText = start.replace('T', ' ').slice(0, 16);
  const endText = end.replace('T', ' ').slice(11, 16);
  return `${startText} ~ ${endText}`;
});

const originalPractitionerId = computed(() => {
  const actors = props.schedule?.actors ?? [];
  const practitioner = actors.find((item) => item.actorType === BOOKING_ACTOR_TYPE.PRACTITIONER);
  return practitioner?.actorId ?? props.schedule?.originalPractitionerId;
});

/** 是否绑定了出诊医师（科室号无医师，不可替诊） */
const hasPractitionerActor = computed(() => Boolean(toBookingId(originalPractitionerId.value)));

const canSubstitutePractitioner = computed(() => hasPractitionerActor.value);

const practitionerDisplay = computed(() => {
  const label = schedulePractitionerLabel(props.schedule, props.practitioners);
  return label || '未指定';
});

const departmentDisplay = computed(
  () => scheduleDepartmentLabel(props.organizationId, props.departments) || '—',
);

const serviceTypeDisplay = computed(
  () => scheduleServiceTypeLabel(props.schedule, props.healthcareServices) || '—',
);

const missingPractitionerHint = computed(() => {
  if (!props.schedule || showDraftForm.value || hasPractitionerActor.value) {
    return false;
  }
  // 科室号发布后可无医师；仅草稿仍提示补医师后发布
  return props.schedule.statusEnum === 'draft';
});

async function resetCreateForm() {
  Object.assign(draftForm, createEmptyDraftForm());
  const date = props.selectedDate ?? new Date().toISOString().slice(0, 10);
  resetDraftFormForDate(draftForm, date);
  await loadDraftFormContext(props.organizationId);
}

async function fillDraftFormFromSchedule() {
  draftForm.name = props.schedule?.name ?? '';
  fillDraftFormFromActors(draftForm, props.schedule?.actors);
  if (!draftForm.healthcareServiceId) {
    draftForm.healthcareServiceId = toBookingId(props.schedule?.healthcareServiceId);
  }
  draftForm.slotProvisionTemplateId = toBookingId(props.schedule?.slotProvisionTemplateId);
  applyHorizonToForm(
    draftForm,
    props.schedule?.planningHorizonStart,
    props.schedule?.planningHorizonEnd,
    props.selectedDate,
  );
  await loadDraftFormContext(
    props.organizationId,
    draftForm.scheduleMode === 'MODE-A' ? draftForm.practitionerId : undefined,
  );
}

async function resolvePublishHorizon() {
  if (showDraftForm.value) {
    return resolveDraftPlanningHorizon(draftForm);
  }
  const start = props.schedule?.planningHorizonStart;
  const end = props.schedule?.planningHorizonEnd;
  if (start && end) {
    return { planningHorizonStart: start, planningHorizonEnd: end };
  }
  throw new Error('日程规划时间不完整');
}

function assertPublishReady() {
  if (!draftForm.healthcareServiceId) {
    draftForm.healthcareServiceId = toBookingId(props.schedule?.healthcareServiceId);
  }
  assertDraftFormValid(draftForm);
}

function emitRefreshed() {
  emit('refreshed', { planningDate: extractPlanningDate(draftForm) });
}

async function createDraftSchedule() {
  if (!props.organizationId) {
    throw new Error('请先选择排班科室');
  }
  const horizon = await resolveDraftPlanningHorizon(draftForm);
  const res = await createScheduleApi({
    organizationId: props.organizationId,
    name: draftForm.name.trim() || undefined,
    actors: buildDraftActors(draftForm),
    planningHorizonStart: horizon.planningHorizonStart,
    planningHorizonEnd: horizon.planningHorizonEnd,
    slotProvisionTemplateId: draftForm.slotProvisionTemplateId,
  });
  if (!res?.scheduleId) {
    throw new Error('创建日程失败');
  }
  return res.scheduleId;
}

async function saveDraftSchedule() {
  if (isCreateMode.value) {
    await createDraftSchedule();
    return;
  }
  if (!props.schedule?.scheduleId) {
    return;
  }
  const horizon = await resolveDraftPlanningHorizon(draftForm);
  await updateScheduleDraftApi({
    scheduleId: props.schedule.scheduleId,
    name: draftForm.name.trim() || undefined,
    actors: buildDraftActors(draftForm),
    planningHorizonStart: horizon.planningHorizonStart,
    planningHorizonEnd: horizon.planningHorizonEnd,
    slotProvisionTemplateId: draftForm.slotProvisionTemplateId,
  });
}

const { run: runSaveDraft, loading: saveDraftLoading } = useRequest(saveDraftSchedule, {
  manual: true,
  onSuccess: () => {
    ElMessage.success(isCreateMode.value ? '草稿日程已创建' : '草稿已保存');
    emitRefreshed();
    emit('update:visible', false);
  },
});

async function handleSaveDraft() {
  try {
    await runSaveDraft();
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败');
  }
}

async function publishScheduleById(
  scheduleId: BookingEntityId,
  horizon?: { planningHorizonStart: string; planningHorizonEnd: string },
) {
  const resolved = horizon ?? (await resolvePublishHorizon());
  const preview = await previewPublishScheduleConflictsApi({
    scheduleId,
    planningHorizonStart: resolved.planningHorizonStart,
    planningHorizonEnd: resolved.planningHorizonEnd,
  });
  if (preview?.hasConflict) {
    const lines = (preview.conflicts ?? [])
      .slice(0, 5)
      .map((item) => item.message || item.conflictScheduleName || '冲突')
      .join('\n');
    await ElMessageBox.confirm(
      `检测到 ${preview.conflicts?.length ?? 0} 条冲突：\n${lines}\n\n确认仍要发布？`,
      '发布冲突警告',
      { type: 'warning' },
    );
    await publishScheduleApi({
      scheduleId,
      planningHorizonStart: resolved.planningHorizonStart,
      planningHorizonEnd: resolved.planningHorizonEnd,
      acknowledgeConflictWarnings: true,
    });
    return;
  }
  await publishScheduleApi({
    scheduleId,
    planningHorizonStart: resolved.planningHorizonStart,
    planningHorizonEnd: resolved.planningHorizonEnd,
  });
}

async function handlePublish() {
  try {
    assertPublishReady();
    let scheduleId = toBookingId(props.schedule?.scheduleId);
    if (isCreateMode.value) {
      scheduleId = toBookingId(await createDraftSchedule());
    }
    if (!scheduleId) {
      return;
    }
    await publishScheduleById(scheduleId);
    ElMessage.success('日程已发布');
    emitRefreshed();
    emit('update:visible', false);
  } catch {
    // cancel or error
  }
}

async function handlePublishWithLoading() {
  publishLoading.value = true;
  try {
    await handlePublish();
  } finally {
    publishLoading.value = false;
  }
}

async function handlePublishGeneratedDraft() {
  const scheduleId = toBookingId(props.schedule?.scheduleId);
  const start = props.schedule?.planningHorizonStart;
  const end = props.schedule?.planningHorizonEnd;
  if (!scheduleId || !start || !end) {
    ElMessage.warning('草稿信息不完整，请点「修改后再发布」检查');
    return;
  }
  publishLoading.value = true;
  try {
    await publishScheduleById(scheduleId, {
      planningHorizonStart: start,
      planningHorizonEnd: end,
    });
    ElMessage.success('日程已发布');
    emitRefreshed();
    emit('update:visible', false);
  } catch {
    // cancel or error
  } finally {
    publishLoading.value = false;
  }
}

function enterDraftEditMode() {
  draftEditMode.value = true;
  void fillDraftFormFromSchedule();
}

async function handleRetireSchedule() {
  if (!props.schedule?.scheduleId) {
    return;
  }
  const isDraft = props.schedule.statusEnum === 'draft';
  try {
    await ElMessageBox.confirm(
      isDraft
        ? '删除后该草稿将从周历移除，且不可恢复。确认删除？'
        : '作废后该日程将从周历移除。确认作废？',
      isDraft ? '删除草稿' : '作废日程',
      { type: 'warning' },
    );
    retireLoading.value = true;
    await retireScheduleApi({
      scheduleId: props.schedule.scheduleId,
      reason: isDraft ? '误操作删除草稿' : '作废日程',
    });
    ElMessage.success(isDraft ? '草稿已删除' : '日程已作废');
    emitRefreshed();
    emit('update:visible', false);
  } catch {
    // cancel or error
  } finally {
    retireLoading.value = false;
  }
}

async function handleMaterializeFromTemplate() {
  if (!props.organizationId || !props.selectedDate || !props.schedule?.dayScheduleTemplateId) {
    ElMessage.warning('占位预览信息不完整');
    return;
  }
  materializeLoading.value = true;
  try {
    const res = await generateFromTemplateApi({
      organizationId: props.organizationId,
      scheduleTemplateId: props.schedule.dayScheduleTemplateId,
      templateKind: 'DAY',
      rangeStart: props.selectedDate,
      rangeEnd: props.selectedDate,
      autoPublish: true,
      autoProvision: true,
    });
    ElMessage.success(`已生成 ${res?.generatedCount ?? 0} 条正式排班`);
    emit('refreshed', { planningDate: props.selectedDate });
    emit('update:visible', false);
  } finally {
    materializeLoading.value = false;
  }
}

async function initDrawerForm() {
  draftEditMode.value = false;
  if (isCreateMode.value) {
    await resetCreateForm();
    return;
  }
  if (isExistingDraft.value) {
    return;
  }
  if (props.schedule?.statusEnum === 'draft') {
    await fillDraftFormFromSchedule();
  }
}

watch(
  () => props.visible,
  (open) => {
    if (!open) return;
    void initDrawerForm();
    substitutePractitionerId.value = props.schedule?.substitutePractitionerId;
    substituteReason.value = '';
    partialSlotIds.value = [];
    if (open && props.schedule?.statusEnum === 'active') {
      void loadScheduleSlots();
    } else {
      scheduleSlots.value = [];
    }
  },
);

watch(
  () => [
    props.createMode,
    props.schedule?.scheduleId,
    props.schedule?.statusEnum,
    props.selectedDate,
  ],
  () => {
    if (props.visible) {
      void initDrawerForm();
    }
  },
);

/** 表单内切换号别/医师时重载下拉 */
async function handleDraftFormReloadContext(payload: { practitionerId?: BookingEntityId }) {
  await loadDraftFormContext(props.organizationId, payload.practitionerId);
}

async function loadScheduleSlots() {
  if (!props.organizationId || !props.selectedDate || !props.schedule?.scheduleId) return;
  slotsLoading.value = true;
  try {
    const res = await pageSlotsByDateApi({
      organizationId: props.organizationId,
      date: props.selectedDate,
      scheduleId: props.schedule.scheduleId,
      pageNo: 1,
      pageSize: 200,
    });
    scheduleSlots.value = res?.content ?? [];
  } finally {
    slotsLoading.value = false;
  }
}

function formatSlotLabel(slot: SlotListItemDto) {
  const start = slot.startTime?.slice(11, 16) ?? '';
  const end = slot.endTime?.slice(11, 16) ?? '';
  return `${start}-${end} · 已约${slot.bookedCount ?? 0}/${slot.capacity ?? 0} · ${slot.displayStatus || slot.statusCode || ''}`;
}

async function handleProvisionDone() {
  emit('refreshed');
  if (props.schedule?.statusEnum === 'active') {
    await loadScheduleSlots();
  }
}

async function handleReactivate() {
  if (!props.schedule?.scheduleId) return;
  await ElMessageBox.confirm(
    '恢复开诊后日程变为 active，但不会自动恢复号源，须手动批量放号。确认恢复？',
    '恢复开诊',
    { type: 'warning' },
  );
  await reactivateScheduleApi({ scheduleId: props.schedule.scheduleId });
  ElMessage.success('日程已恢复开诊，请重新放号');
  emit('refreshed');
  emit('update:visible', false);
}

async function handlePartialStop() {
  if (!props.organizationId || !props.schedule?.scheduleId) return;
  if (!partialSlotIds.value.length) {
    ElMessage.warning('请至少选择一个号源时段');
    return;
  }
  try {
    const preview = await previewStopClinicImpactApi({
      organizationId: props.organizationId,
      scheduleId: props.schedule.scheduleId,
      slotIds: partialSlotIds.value,
    });
    const needWizard =
      (preview?.appointmentCount ?? 0) > 0 && preview?.blockDeactivateIfUnhandled !== false;
    if (needWizard) {
      partialStopWizardVisible.value = true;
      return;
    }
    partialStopWizardVisible.value = true;
  } catch {
    // cancel
  }
}

async function handleSubstitute() {
  if (!props.schedule?.scheduleId) return;
  if (!substitutePractitionerId.value) {
    ElMessage.warning('请选择替诊医师');
    return;
  }
  await substitutePractitionerApi({
    scheduleId: props.schedule.scheduleId,
    substitutePractitionerId: substitutePractitionerId.value,
    reason: substituteReason.value || undefined,
  });
  ElMessage.success('替诊已设置');
  emit('refreshed');
  emit('update:visible', false);
}

async function handleClearSubstitute() {
  if (!props.schedule?.scheduleId) return;
  await substitutePractitionerApi({
    scheduleId: props.schedule.scheduleId,
  });
  ElMessage.success('已撤销替诊');
  emit('refreshed');
  emit('update:visible', false);
}

const { run: runDeactivate, loading: deactivateLoading } = useRequest(
  async (reason: string) => {
    if (!props.schedule?.scheduleId) return;
    await deactivateScheduleApi({
      scheduleId: props.schedule.scheduleId,
      reason,
    });
  },
  {
    manual: true,
    onSuccess: () => {
      ElMessage.success('日程已停诊');
      emit('refreshed');
      emit('update:visible', false);
    },
  },
);

async function handleDeactivate() {
  if (!props.organizationId || !props.schedule?.scheduleId) return;
  try {
    const preview = await previewStopClinicImpactApi({
      organizationId: props.organizationId,
      scheduleId: props.schedule.scheduleId,
    });
    const needWizard =
      (preview?.appointmentCount ?? 0) > 0 && preview?.blockDeactivateIfUnhandled !== false;
    if (needWizard) {
      stopWizardVisible.value = true;
      return;
    }
    const { value } = await ElMessageBox.prompt('请输入停诊原因', '停诊确认', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPlaceholder: '停诊',
    });
    await runDeactivate(value || '停诊');
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <ElDrawer
    :model-value="visible"
    :title="drawerTitle"
    size="460px"
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-if="showDraftPublishPanel" class="schedule-action__section">
      <p class="schedule-action__hint">
        信息已由<strong>排班模板</strong>带入，无需重填。确认无误后点「发布」；发布后在同一格「批量放号」。
      </p>
      <ElDescriptions :column="1" border>
        <ElDescriptionsItem label="名称">{{ schedule?.name || '—' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="出诊医师">{{ practitionerDisplay || '—' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="科室">{{ departmentDisplay }}</ElDescriptionsItem>
        <ElDescriptionsItem label="号源类型">{{ serviceTypeDisplay || '—' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="出诊时间">{{ planningHorizonDisplay }}</ElDescriptionsItem>
      </ElDescriptions>
      <div class="schedule-action__row">
        <ElButton type="primary" :loading="publishLoading" @click="handlePublishGeneratedDraft">
          发布
        </ElButton>
        <ElButton link type="primary" @click="enterDraftEditMode">修改后再发布</ElButton>
        <ElButton link type="danger" :loading="retireLoading" @click="handleRetireSchedule">
          删除草稿
        </ElButton>
      </div>
    </div>

    <div v-else-if="showDraftForm" class="schedule-action__section">
      <ScheduleDraftForm
        v-model="draftForm"
        :organization-id="organizationId"
        :practitioners="draftFormInitData.practitioners"
        :healthcare-services="draftFormInitData.healthcareServices"
        :slot-provision-template-options="draftT1Options"
        @reload-context="handleDraftFormReloadContext"
      />
      <div class="schedule-action__row">
        <ElButton :loading="saveDraftLoading" @click="handleSaveDraft">保存草稿</ElButton>
        <ElButton type="primary" :loading="publishLoading" @click="handlePublishWithLoading">
          发布
        </ElButton>
        <ElButton
          v-if="canRetireSchedule && !isCreateMode"
          type="danger"
          plain
          :loading="retireLoading"
          @click="handleRetireSchedule"
        >
          {{ schedule?.statusEnum === 'draft' ? '删除草稿' : '作废日程' }}
        </ElButton>
      </div>
    </div>

    <ElDescriptions v-else-if="schedule" :column="1" border>
      <ElDescriptionsItem label="名称">{{ schedule.name || '-' }}</ElDescriptionsItem>
      <ElDescriptionsItem label="出诊医师">{{ practitionerDisplay }}</ElDescriptionsItem>
      <ElDescriptionsItem label="科室">{{ departmentDisplay }}</ElDescriptionsItem>
      <ElDescriptionsItem label="号源类型">{{ serviceTypeDisplay }}</ElDescriptionsItem>
      <ElDescriptionsItem label="状态">{{
        formatScheduleStatus(schedule.statusEnum)
      }}</ElDescriptionsItem>
      <ElDescriptionsItem label="规划起">{{
        schedule.planningHorizonStart || '-'
      }}</ElDescriptionsItem>
      <ElDescriptionsItem label="规划止">{{
        schedule.planningHorizonEnd || '-'
      }}</ElDescriptionsItem>
      <ElDescriptionsItem v-if="schedule.substituteDisplay" label="替诊">
        {{ schedule.substituteDisplay }}
      </ElDescriptionsItem>
    </ElDescriptions>

    <p v-if="schedule && missingPractitionerHint && !showDraftForm" class="schedule-action__hint">
      该日程未绑定出诊医师。请在「排班设置 →
      日排班」中维护出诊医师后重新生成，或在草稿状态下编辑并发布。
    </p>

    <div v-if="isTemplateCell" class="schedule-action__section">
      <p class="schedule-action__hint">
        此为<strong>占位预览</strong>（当天尚无正式排班），{{
          schedule?.bookable ? '可预约时会自动生成正式排班' : '仅预览、不可约'
        }}。预览何时出现由「排班设置」中的占位有效期控制。
      </p>
      <ElButton
        v-if="schedule?.dayScheduleTemplateId"
        type="primary"
        :loading="materializeLoading"
        @click="handleMaterializeFromTemplate"
      >
        生成本日正式排班
      </ElButton>
    </div>

    <div v-if="schedule?.statusEnum === 'active'" class="schedule-action__section">
      <template v-if="canSubstitutePractitioner">
        <h4>替诊</h4>
        <p class="schedule-action__hint">
          预约仍挂原医师号源，队列/大屏显示「B替A」；不触发停诊转交流程。
        </p>
        <ElForm label-width="88px">
          <ElFormItem label="替诊医师">
            <ElSelect
              v-model="substitutePractitionerId"
              clearable
              filterable
              placeholder="选择顶班医师"
              style="width: 100%"
            >
              <ElOption
                v-for="item in practitioners || []"
                :key="item.value"
                :label="item.label"
                :value="bookingOptionValue(item.value)"
                :disabled="isSameBookingId(item.value, originalPractitionerId)"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="原因">
            <ElInput v-model="substituteReason" placeholder="可选" />
          </ElFormItem>
        </ElForm>
        <div class="schedule-action__row">
          <ElButton type="primary" @click="handleSubstitute">确认替诊</ElButton>
          <ElButton v-if="schedule.substitutePractitionerId" @click="handleClearSubstitute">
            撤销替诊
          </ElButton>
        </div>
      </template>
      <ElButton type="primary" @click="provisionVisible = true">批量放号</ElButton>
      <div v-loading="slotsLoading" class="schedule-action__partial">
        <h4>部分停诊</h4>
        <p class="schedule-action__hint">选择需停诊的号源时段；有预约时将进入停诊向导。</p>
        <ElCheckboxGroup v-if="scheduleSlots.length" v-model="partialSlotIds">
          <ElCheckbox v-for="slot in scheduleSlots" :key="slot.slotId" :value="slot.slotId!">
            {{ formatSlotLabel(slot) }}
          </ElCheckbox>
        </ElCheckboxGroup>
        <p v-else class="schedule-action__hint">当日暂无号源，请先放号。</p>
        <ElButton type="warning" :disabled="!partialSlotIds.length" @click="handlePartialStop">
          部分停诊
        </ElButton>
      </div>
      <ElButton type="danger" :loading="deactivateLoading" @click="handleDeactivate"
        >整日停诊</ElButton
      >
    </div>

    <div v-else-if="schedule?.statusEnum === 'inactive'" class="schedule-action__section">
      <h4>恢复开诊</h4>
      <p class="schedule-action__hint">
        将日程从 inactive 恢复为 active；不会自动恢复已封锁/释放的号源，须手动批量放号。
      </p>
      <ElButton type="primary" @click="handleReactivate">恢复开诊</ElButton>

      <h4>作废日程</h4>
      <p class="schedule-action__hint">
        从周历永久移除该日程；不会自动恢复号源，删除后该日可重新新建或生成排班。若号源仍有人预约，须先通过停诊向导处理预约。
      </p>
      <ElButton
        v-if="canRetireSchedule"
        type="danger"
        plain
        :loading="retireLoading"
        @click="handleRetireSchedule"
      >
        作废日程
      </ElButton>
    </div>

    <ProvisionSlotDialog
      v-model:visible="provisionVisible"
      :organization-id="organizationId"
      :schedule-id="schedule?.scheduleId"
      :slot-provision-template-id="schedule?.slotProvisionTemplateId"
      :slot-provision-template-options="slotProvisionTemplateOptions"
      :selected-date="selectedDate"
      @done="handleProvisionDone"
    />

    <StopClinicWizard
      v-model:visible="stopWizardVisible"
      :organization-id="organizationId"
      :schedule="schedule"
      :practitioners="practitioners"
      @done="emit('refreshed')"
    />

    <StopClinicWizard
      v-model:visible="partialStopWizardVisible"
      :organization-id="organizationId"
      :schedule="schedule"
      :slot-ids="partialSlotIds"
      :practitioners="practitioners"
      @done="emit('refreshed')"
    />
  </ElDrawer>
</template>

<style scoped>
.schedule-action__section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 16px;
}

.schedule-action__section:first-child {
  margin-top: 0;
}

.schedule-action__section h4 {
  margin: 0;
  font-size: 14px;
}

.schedule-action__hint {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.schedule-action__row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.schedule-action__partial {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
}

.schedule-action__partial :deep(.el-checkbox) {
  display: flex;
  margin-right: 0;
}
</style>
