<script setup lang="ts">
import type {
  PreviewStopClinicImpactResponse,
  ScheduleCalendarCellDto,
} from '#/api/booking/outpatient-schedule';
import type { BookingOptionDto } from '#/api/booking/booking-types';

import { computed, ref, watch } from 'vue';

import {
  previewStopClinicImpactApi,
  releaseStopClinicPreviewFreezeApi,
  transferAppointmentsOnStopClinicApi,
} from '#/api/booking/outpatient-schedule';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElCheckbox,
  ElDialog,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

const props = defineProps<{
  visible: boolean;
  organizationId?: number;
  schedule?: ScheduleCalendarCellDto | null;
  /** 部分停诊时的号源主键列表；空表示整日停诊。 */
  slotIds?: number[];
  practitioners?: BookingOptionDto[];
}>();

const emit = defineEmits<{
  'update:visible': [value: boolean];
  done: [];
}>();

const dispositionMode = ref<'TRANSFER' | 'CANCEL' | 'MANUAL'>('TRANSFER');
const targetPractitionerId = ref<number>();
const deferManualHandling = ref(false);
const stopReason = ref('停诊');
const preview = ref<PreviewStopClinicImpactResponse>();
const executed = ref(false);

const hasBlockingAppointments = computed(() => {
  const count = preview.value?.appointmentCount ?? 0;
  const registered = preview.value?.registeredCount ?? 0;
  const checkedIn = preview.value?.checkedInCount ?? 0;
  return count - registered - checkedIn > 0;
});

const isPartialStop = computed(() => (props.slotIds?.length ?? 0) > 0);

const dialogTitle = computed(() => (isPartialStop.value ? '部分号源停诊确认' : '停诊确认'));

const executeLabel = computed(() => (isPartialStop.value ? '执行并封锁号源' : '执行并停诊'));

const { run: runPreview, loading: previewLoading } = useRequest(
  async () => {
    if (!props.organizationId || !props.schedule?.scheduleId) return;
    return previewStopClinicImpactApi({
      organizationId: props.organizationId,
      scheduleId: props.schedule.scheduleId,
      slotIds: props.slotIds?.length ? props.slotIds : undefined,
      targetPractitionerId: targetPractitionerId.value,
      dispositionMode: dispositionMode.value,
    });
  },
  {
    manual: true,
    onSuccess: (res) => {
      preview.value = res;
    },
  },
);

const { run: runExecute, loading: executeLoading } = useRequest(
  async () => {
    if (!props.organizationId || !props.schedule?.scheduleId) return;
    const transferItems =
      dispositionMode.value === 'TRANSFER'
        ? (preview.value?.items ?? [])
            .filter((item) => item.dispositionStatus === 'CAN_TRANSFER' && item.targetSlotId)
            .map((item) => ({
              appointmentId: item.appointmentId!,
              targetSlotId: item.targetSlotId!,
            }))
        : undefined;
    return transferAppointmentsOnStopClinicApi({
      organizationId: props.organizationId,
      scheduleId: props.schedule.scheduleId,
      slotIds: props.slotIds?.length ? props.slotIds : undefined,
      dispositionMode: dispositionMode.value,
      targetPractitionerId: targetPractitionerId.value,
      transferItems,
      reason: stopReason.value,
      deferManualHandling: deferManualHandling.value,
    });
  },
  {
    manual: true,
    onSuccess: (res) => {
      const success = res?.successCount ?? 0;
      const failed = res?.failedCount ?? 0;
      if (res?.scheduleDeactivated) {
        ElMessage.success(`停诊完成：成功 ${success} 条，失败 ${failed} 条`);
      } else {
        const blocked = res?.blockedSlotCount ?? props.slotIds?.length ?? 0;
        ElMessage.success(
          `已封锁 ${blocked} 个号源：预约处置成功 ${success} 条，失败 ${failed} 条`,
        );
      }
      executed.value = true;
      emit('done');
      void handleVisibleChange(false);
    },
  },
);

watch(
  () => props.visible,
  (open) => {
    if (!open) return;
    executed.value = false;
    dispositionMode.value = 'TRANSFER';
    targetPractitionerId.value = undefined;
    deferManualHandling.value = false;
    stopReason.value = '停诊';
    preview.value = undefined;
    void runPreview();
  },
);

async function releasePreviewFreezeIfNeeded() {
  if (executed.value || !props.organizationId || !props.schedule?.scheduleId) {
    return;
  }
  try {
    await releaseStopClinicPreviewFreezeApi({
      organizationId: props.organizationId,
      scheduleId: props.schedule.scheduleId,
      slotIds: props.slotIds?.length ? props.slotIds : undefined,
    });
  } catch {
    // 取消时静默释放
  }
}

async function handleVisibleChange(visible: boolean) {
  if (visible) {
    emit('update:visible', true);
    return;
  }
  await releasePreviewFreezeIfNeeded();
  emit('update:visible', false);
}

watch([dispositionMode, targetPractitionerId], () => {
  if (props.visible) {
    void runPreview();
  }
});

function formatTime(value?: string) {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 16);
}

function rowClassName({ row }: { row: { dispositionStatus?: string } }) {
  if (row.dispositionStatus === 'SKIP_REGISTERED' || row.dispositionStatus === 'SKIP_CHECKED_IN') {
    return 'is-muted';
  }
  return '';
}

async function handleExecute() {
  try {
    await runExecute();
  } catch (error) {
    ElMessage.error((error as Error).message || '执行失败');
  }
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    :title="dialogTitle"
    width="760px"
    @update:model-value="handleVisibleChange"
  >
    <div v-loading="previewLoading" class="stop-clinic-wizard">
      <p class="stop-clinic-wizard__summary">
        将影响：Schedule {{ preview?.scheduleCount ?? 1 }} 条 · Slot
        {{ preview?.slotCount ?? 0 }} 个 · 有效预约 {{ preview?.appointmentCount ?? 0 }} · 已挂号
        {{ preview?.registeredCount ?? 0 }} · 已报到/叫号 {{ preview?.checkedInCount ?? 0 }} · 渠道
        {{ preview?.channelCount ?? 0 }}
      </p>
      <p v-if="preview?.sourceSlotsFrozen" class="stop-clinic-wizard__freeze-hint">
        已冻结 {{ preview.frozenSlotCount ?? 0 }} 个源号源至
        {{ formatTime(preview.freezeExpiresAt) }}，期间禁止新约（TC-FRZ-01）
      </p>

      <div class="stop-clinic-wizard__form">
        <span class="stop-clinic-wizard__label">处置方式</span>
        <ElRadioGroup v-model="dispositionMode">
          <ElRadio value="TRANSFER">转交医师</ElRadio>
          <ElRadio value="CANCEL">取消预约</ElRadio>
          <ElRadio value="MANUAL">稍后人工</ElRadio>
        </ElRadioGroup>
      </div>

      <div v-if="dispositionMode === 'TRANSFER'" class="stop-clinic-wizard__form">
        <span class="stop-clinic-wizard__label">目标医师</span>
        <ElSelect
          v-model="targetPractitionerId"
          clearable
          filterable
          placeholder="选择接替医师"
          style="width: 220px"
        >
          <ElOption
            v-for="item in practitioners || []"
            :key="item.value"
            :label="item.label"
            :value="Number(item.value)"
          />
        </ElSelect>
        <span v-if="preview?.transferMatchRule" class="stop-clinic-wizard__hint">
          匹配规则：{{ preview.transferMatchRule }}
        </span>
      </div>

      <ElTable
        :data="preview?.items || []"
        max-height="280"
        size="small"
        :row-class-name="rowClassName"
      >
        <ElTableColumn prop="busNo" label="预约单号" width="120" />
        <ElTableColumn prop="patientName" label="患者" width="90" />
        <ElTableColumn label="原时段" min-width="140">
          <template #default="{ row }">{{ formatTime(row.sourceStartTime) }}</template>
        </ElTableColumn>
        <ElTableColumn label="目标时段" min-width="140">
          <template #default="{ row }">{{ formatTime(row.targetStartTime) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="dispositionMessage" label="状态" min-width="120" />
      </ElTable>

      <ElCheckbox
        v-if="dispositionMode === 'MANUAL' && hasBlockingAppointments"
        v-model="deferManualHandling"
      >
        确认稍后人工处理，仍执行停诊
      </ElCheckbox>
    </div>

    <template #footer>
      <ElButton @click="handleVisibleChange(false)">取消</ElButton>
      <ElButton type="primary" :loading="executeLoading" @click="handleExecute">
        {{ executeLabel }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.stop-clinic-wizard {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stop-clinic-wizard__summary {
  margin: 0;
  color: var(--el-text-color-regular);
}

.stop-clinic-wizard__freeze-hint {
  margin: 0;
  font-size: 13px;
  color: var(--el-color-warning);
}

.stop-clinic-wizard__form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.stop-clinic-wizard__label {
  min-width: 72px;
  color: var(--el-text-color-secondary);
}

.stop-clinic-wizard__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

:deep(.is-muted) {
  color: var(--el-text-color-disabled);
}
</style>
