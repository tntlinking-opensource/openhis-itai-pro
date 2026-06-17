<script setup lang="ts">
import type { ScheduleCalendarCellDto } from '#/api/booking/outpatient-schedule';
import type { BookingOptionDto } from '#/api/booking/booking-types';

import { computed, reactive, ref, watch } from 'vue';

import {
  formatScheduleStatus,
  toDateTimeEnd,
  toDateTimeStart,
  BOOKING_ACTOR_TYPE,
} from '#/api/booking/booking-constants';
import {
  deactivateScheduleApi,
  generateFromTemplateApi,
  previewPublishScheduleConflictsApi,
  previewStopClinicImpactApi,
  publishScheduleApi,
  reactivateScheduleApi,
  substitutePractitionerApi,
} from '#/api/booking/outpatient-schedule';
import type { SlotListItemDto } from '#/api/booking/slot-manage';
import { pageSlotsByDateApi } from '#/api/booking/slot-manage';
import ProvisionSlotDialog from '#/views/booking/components/provision-slot-dialog.vue';
import StopClinicWizard from '#/views/booking/components/stop-clinic-wizard.vue';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDatePicker,
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
  organizationId?: number;
  selectedDate?: string;
  schedule?: ScheduleCalendarCellDto | null;
  practitioners?: BookingOptionDto[];
}>();

const emit = defineEmits<{
  'update:visible': [value: boolean];
  refreshed: [];
  edit: [scheduleId: number];
}>();

const publishForm = reactive({
  planningHorizonStart: '',
  planningHorizonEnd: '',
});

const provisionVisible = ref(false);
const stopWizardVisible = ref(false);
const materializeLoading = ref(false);
const substitutePractitionerId = ref<number>();
const substituteReason = ref('');
const scheduleSlots = ref<SlotListItemDto[]>([]);
const partialSlotIds = ref<number[]>([]);
const partialStopWizardVisible = ref(false);
const slotsLoading = ref(false);

const isTemplateCell = computed(
  () => props.schedule?.statusEnum === 'FROM_TEMPLATE' || props.schedule?.fromTemplate === true,
);

const originalPractitionerId = computed(() => {
  const actors = props.schedule?.actors ?? [];
  const practitioner = actors.find((item) => item.actorType === BOOKING_ACTOR_TYPE.PRACTITIONER);
  return practitioner?.actorId;
});

async function handlePublish() {
  if (!props.schedule?.scheduleId) return;
  try {
    const preview = await previewPublishScheduleConflictsApi({
      scheduleId: props.schedule.scheduleId,
      planningHorizonStart: publishForm.planningHorizonStart,
      planningHorizonEnd: publishForm.planningHorizonEnd,
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
        scheduleId: props.schedule.scheduleId,
        planningHorizonStart: publishForm.planningHorizonStart,
        planningHorizonEnd: publishForm.planningHorizonEnd,
        acknowledgeConflictWarnings: true,
      });
    } else {
      await publishScheduleApi({
        scheduleId: props.schedule.scheduleId,
        planningHorizonStart: publishForm.planningHorizonStart,
        planningHorizonEnd: publishForm.planningHorizonEnd,
      });
    }
    ElMessage.success('日程已发布');
    emit('refreshed');
    emit('update:visible', false);
  } catch {
    // cancel or error
  }
}

async function handleMaterializeFromTemplate() {
  if (!props.organizationId || !props.selectedDate || !props.schedule?.dayScheduleTemplateId) {
    ElMessage.warning('模板信息不完整');
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
    ElMessage.success(`已从模板物化 ${res?.generatedCount ?? 0} 条日程`);
    emit('refreshed');
    emit('update:visible', false);
  } finally {
    materializeLoading.value = false;
  }
}

watch(
  () => props.visible,
  (open) => {
    if (!open || !props.selectedDate) return;
    publishForm.planningHorizonStart = toDateTimeStart(props.selectedDate);
    publishForm.planningHorizonEnd = toDateTimeEnd(props.selectedDate);
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
    title="日程详情"
    size="460px"
    @update:model-value="emit('update:visible', $event)"
  >
    <ElDescriptions v-if="schedule" :column="1" border>
      <ElDescriptionsItem label="名称">{{ schedule.name || '-' }}</ElDescriptionsItem>
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

    <div v-if="isTemplateCell" class="schedule-action__section">
      <p class="schedule-action__hint">
        该格来自周模板 fallback，{{
          schedule?.bookable ? '可直接预约（lazy 物化）' : '仅展示不可约'
        }}。
      </p>
      <ElButton
        v-if="schedule?.dayScheduleTemplateId"
        type="primary"
        :loading="materializeLoading"
        @click="handleMaterializeFromTemplate"
      >
        从模板物化当日排班
      </ElButton>
    </div>

    <div v-else-if="schedule?.statusEnum === 'draft'" class="schedule-action__section">
      <h4>发布日程</h4>
      <ElForm label-width="88px">
        <ElFormItem label="规划起">
          <ElDatePicker
            v-model="publishForm.planningHorizonStart"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="规划止">
          <ElDatePicker
            v-model="publishForm.planningHorizonEnd"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </ElFormItem>
      </ElForm>
      <ElButton type="primary" @click="handlePublish">发布</ElButton>
      <ElButton @click="schedule?.scheduleId && emit('edit', schedule.scheduleId)"
        >编辑参与者</ElButton
      >
    </div>

    <div v-if="schedule?.statusEnum === 'active'" class="schedule-action__section">
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
              :value="Number(item.value)"
              :disabled="Number(item.value) === originalPractitionerId"
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
    </div>

    <ProvisionSlotDialog
      v-model:visible="provisionVisible"
      :organization-id="organizationId"
      :schedule-id="schedule?.scheduleId"
      @done="emit('refreshed')"
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
