<script setup lang="ts">
import type { BookingEntityId } from '#/api/booking/booking-id';
import type { SlotTemplateRequest } from '#/api/booking/outpatient-schedule';
import type { SlotListItemDto } from '#/api/booking/slot-manage';

import { computed, ref, watch } from 'vue';

import {
  buildSlotTemplatesFromRange,
  countSlotsInRange,
  filterAgainstExistingStarts,
  formatTemplateTimeRange,
  mergePendingTemplates,
} from '#/api/booking/provision-slot-presets';
import { provisionSlotBatchApi } from '#/api/booking/outpatient-schedule';
import { loadSlotProvisionTemplateApi } from '#/api/booking/schedule-template';
import { deleteSlotsApi, pageSlotsByDateApi } from '#/api/booking/slot-manage';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTimePicker,
} from 'element-plus';

const props = defineProps<{
  visible: boolean;
  organizationId?: BookingEntityId;
  scheduleId?: BookingEntityId;
  /** 日程关联的 T1 放号时段模板 */
  slotProvisionTemplateId?: BookingEntityId;
  /** 周历选中日期，用于查询当日已放号源 */
  selectedDate?: string;
}>();

const emit = defineEmits<{
  'update:visible': [value: boolean];
  done: [];
}>();

const capacity = ref(10);
const rangeStart = ref('');
const rangeEnd = ref('');
const slotMinutes = ref(30);
const pendingTemplates = ref<SlotTemplateRequest[]>([]);
const t1FillLoading = ref(false);

const existingSlots = ref<SlotListItemDto[]>([]);
const deletingSlotId = ref<BookingEntityId>();

const existingStartTimes = computed(
  () => new Set(existingSlots.value.map((slot) => slot.startTime).filter(Boolean) as string[]),
);

const previewSlotCount = computed(() => {
  if (!rangeStart.value || !rangeEnd.value) return 0;
  return countSlotsInRange(resolveDate(), rangeStart.value, rangeEnd.value, slotMinutes.value);
});

const previewHint = computed(() => {
  if (!rangeStart.value || !rangeEnd.value) {
    return '填写起止时间后，将按间隔切分并加入待放号列表';
  }
  if (previewSlotCount.value <= 0) {
    return '结束时间须晚于开始时间，且间隔须能切出至少 1 段';
  }
  if (slotMinutes.value <= 0) {
    return `整段 ${formatTimeLabel(rangeStart.value)}–${formatTimeLabel(rangeEnd.value)} 作为 1 个号源`;
  }
  return `预计切分 ${previewSlotCount.value} 段，每段 ${slotMinutes.value} 分钟，容量 ${capacity.value}`;
});

const { run: runLoadSlots, loading: slotsLoading } = useRequest(
  async () => {
    if (!props.organizationId || !props.scheduleId || !props.selectedDate) {
      existingSlots.value = [];
      return [];
    }
    const res = await pageSlotsByDateApi({
      organizationId: props.organizationId,
      date: props.selectedDate,
      scheduleId: props.scheduleId,
      pageNo: 1,
      pageSize: 200,
    });
    existingSlots.value = res?.content ?? [];
    return existingSlots.value;
  },
  { manual: true },
);

watch(
  () => props.visible,
  (open) => {
    if (!open) return;
    capacity.value = 10;
    rangeStart.value = '';
    rangeEnd.value = '';
    slotMinutes.value = 30;
    pendingTemplates.value = [];
    void runLoadSlots();
  },
);

const { run: runProvision, loading: provisionLoading } = useRequest(
  async (slotTemplates: SlotTemplateRequest[]) => {
    if (!props.organizationId || !props.scheduleId) {
      throw new Error('缺少机构或日程');
    }
    if (slotTemplates.length === 0) {
      throw new Error('请先添加待放号时段');
    }
    return provisionSlotBatchApi({
      scheduleId: props.scheduleId,
      organizationId: props.organizationId,
      slotTemplates,
    });
  },
  {
    manual: true,
    onSuccess: async (res) => {
      ElMessage.success(`已生成 ${res?.generatedCount ?? 0} 个号源`);
      pendingTemplates.value = [];
      rangeStart.value = '';
      rangeEnd.value = '';
      await runLoadSlots();
      emit('done');
    },
  },
);

async function handleSubmit() {
  try {
    await runProvision(pendingTemplates.value);
  } catch (error) {
    ElMessage.error((error as Error).message || '放号失败');
  }
}

function resolveDate() {
  return props.selectedDate ?? new Date().toISOString().slice(0, 10);
}

async function handleFillFromT1() {
  if (!props.slotProvisionTemplateId) {
    ElMessage.warning('该日程未关联放号时段模板');
    return;
  }
  t1FillLoading.value = true;
  try {
    const detail = await loadSlotProvisionTemplateApi({
      id: props.slotProvisionTemplateId,
    });
    const date = resolveDate();
    const generated: SlotTemplateRequest[] = (detail?.slotTemplates ?? [])
      .filter((item) => item.startTime && item.endTime)
      .map((item) => {
        const start = item.startTime!.length <= 5 ? `${item.startTime}:00` : item.startTime!;
        const end = item.endTime!.length <= 5 ? `${item.endTime}:00` : item.endTime!;
        return {
          startTime: `${date}T${start}`,
          endTime: `${date}T${end}`,
          capacity: item.capacity ?? 1,
        };
      });
    if (generated.length === 0) {
      ElMessage.warning('放号时段模板无有效时段行');
      return;
    }
    const { accepted, skippedExisting } = filterAgainstExistingStarts(
      generated,
      existingStartTimes.value,
    );
    const beforeCount = pendingTemplates.value.length;
    pendingTemplates.value = mergePendingTemplates(pendingTemplates.value, accepted);
    const added = pendingTemplates.value.length - beforeCount;
    if (added <= 0) {
      ElMessage.warning('放号时段均已存在或已在待放号列表中');
      return;
    }
    ElMessage.success(
      `已按放号时段模板添加 ${added} 个时段${skippedExisting > 0 ? `，跳过 ${skippedExisting} 个已放号` : ''}`,
    );
  } catch (error) {
    ElMessage.error((error as Error).message || '加载放号时段失败');
  } finally {
    t1FillLoading.value = false;
  }
}

function formatTimeLabel(time: string) {
  return time.slice(0, 5);
}

function addRangeToPending() {
  const date = resolveDate();
  if (!rangeStart.value || !rangeEnd.value) {
    ElMessage.warning('请填写开始与结束时间');
    return;
  }
  const startMs = new Date(`${date}T${rangeStart.value}`).getTime();
  const endMs = new Date(`${date}T${rangeEnd.value}`).getTime();
  if (!Number.isFinite(startMs) || !Number.isFinite(endMs) || endMs <= startMs) {
    ElMessage.warning('结束时间须晚于开始时间');
    return;
  }
  if (slotMinutes.value < 0) {
    ElMessage.warning('每段时长不能为负数');
    return;
  }

  const generated = buildSlotTemplatesFromRange(
    date,
    rangeStart.value,
    rangeEnd.value,
    slotMinutes.value,
    capacity.value,
  );
  if (generated.length === 0) {
    ElMessage.warning('无法切分出有效时段，请检查间隔是否过大');
    return;
  }

  const { accepted, skippedExisting } = filterAgainstExistingStarts(
    generated,
    existingStartTimes.value,
  );
  const beforeCount = pendingTemplates.value.length;
  pendingTemplates.value = mergePendingTemplates(pendingTemplates.value, accepted);
  const added = pendingTemplates.value.length - beforeCount;
  const skippedPending = accepted.length - added;

  if (added <= 0) {
    ElMessage.warning('所选时段均已存在或已在待放号列表中');
    return;
  }
  const parts = [`已添加 ${added} 个时段`];
  if (skippedExisting > 0) {
    parts.push(`跳过 ${skippedExisting} 个已放号`);
  }
  if (skippedPending > 0) {
    parts.push(`跳过 ${skippedPending} 个重复待放`);
  }
  ElMessage.success(parts.join('，'));
}

function removePending(index: number) {
  pendingTemplates.value = pendingTemplates.value.filter((_, i) => i !== index);
}

function clearPending() {
  pendingTemplates.value = [];
}

function formatTimeRange(slot: SlotListItemDto) {
  const start = slot.startTime?.slice(11, 16) ?? '-';
  const end = slot.endTime?.slice(11, 16) ?? '-';
  return `${start} - ${end}`;
}

function formatStatus(slot: SlotListItemDto) {
  return slot.displayStatus || slot.statusCode || '-';
}

function canDeleteSlot(slot: SlotListItemDto) {
  return (slot.bookedCount ?? 0) <= 0;
}

async function handleDeleteSlot(slot: SlotListItemDto) {
  if (!slot.slotId || !canDeleteSlot(slot)) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认删除号源 ${formatTimeRange(slot)}？删除后不可恢复。`,
      '删除号源',
      { type: 'warning' },
    );
    deletingSlotId.value = slot.slotId;
    await deleteSlotsApi({ slotIds: [slot.slotId] });
    ElMessage.success('号源已删除');
    await runLoadSlots();
    emit('done');
  } catch {
    // cancel or error
  } finally {
    deletingSlotId.value = undefined;
  }
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    title="批量放号"
    width="720px"
    @update:model-value="emit('update:visible', $event)"
  >
    <section class="provision-slot-dialog__section">
      <header class="provision-slot-dialog__section-title">
        已放号源
        <span v-if="selectedDate" class="provision-slot-dialog__date-hint"
          >（{{ selectedDate }}）</span
        >
      </header>
      <ElTable
        v-loading="slotsLoading"
        :data="existingSlots"
        size="small"
        max-height="180"
        empty-text="暂无号源，可在下方填写时段后添加"
      >
        <ElTableColumn label="时段" min-width="108">
          <template #default="{ row }">{{ formatTimeRange(row) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="capacity" label="容量" width="64" align="center" />
        <ElTableColumn prop="bookedCount" label="已约" width="64" align="center" />
        <ElTableColumn prop="remainingCount" label="剩余" width="64" align="center" />
        <ElTableColumn label="状态" min-width="72">
          <template #default="{ row }">{{ formatStatus(row) }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="72" align="center" fixed="right">
          <template #default="{ row }">
            <ElButton
              link
              type="danger"
              :disabled="!canDeleteSlot(row)"
              :loading="deletingSlotId === row.slotId"
              @click="handleDeleteSlot(row)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </section>

    <section class="provision-slot-dialog__section">
      <header class="provision-slot-dialog__section-title">添加时段</header>
      <div v-if="slotProvisionTemplateId" class="provision-slot-dialog__t1-row">
        <ElButton :loading="t1FillLoading" type="primary" plain @click="handleFillFromT1">
          按放号时段模板填充
        </ElButton>
        <span class="provision-slot-dialog__hint">将排班设置中的 T1 时段加入待放号列表</span>
      </div>
      <p class="provision-slot-dialog__hint">{{ previewHint }}</p>
      <ElForm inline label-width="72px" class="provision-slot-dialog__range-form">
        <ElFormItem label="开始">
          <ElTimePicker
            v-model="rangeStart"
            value-format="HH:mm:ss"
            placeholder="开始"
            style="width: 120px"
          />
        </ElFormItem>
        <ElFormItem label="结束">
          <ElTimePicker
            v-model="rangeEnd"
            value-format="HH:mm:ss"
            placeholder="结束"
            style="width: 120px"
          />
        </ElFormItem>
        <ElFormItem label="每段时长">
          <ElInputNumber
            v-model="slotMinutes"
            :min="0"
            :max="480"
            :step="5"
            size="small"
            style="width: 120px"
          />
          <span class="provision-slot-dialog__unit">分钟</span>
        </ElFormItem>
        <ElFormItem label="容量">
          <ElInputNumber v-model="capacity" :min="1" :max="999" size="small" style="width: 100px" />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" :disabled="previewSlotCount <= 0" @click="addRangeToPending">
            添加到待放号
            <template v-if="previewSlotCount > 0">（{{ previewSlotCount }} 段）</template>
          </ElButton>
        </ElFormItem>
      </ElForm>
      <p class="provision-slot-dialog__hint provision-slot-dialog__hint--secondary">
        每段时长填 0 表示整段仅 1 个号源；大于 0 时按间隔自动切分（如 08:00–12:00、30 分钟 → 8
        段）。
      </p>
    </section>

    <section class="provision-slot-dialog__section">
      <header class="provision-slot-dialog__section-title">
        待放号
        <ElTag
          v-if="pendingTemplates.length"
          size="small"
          type="info"
          class="provision-slot-dialog__count"
        >
          {{ pendingTemplates.length }} 段
        </ElTag>
        <ElButton
          v-if="pendingTemplates.length"
          link
          type="primary"
          size="small"
          class="provision-slot-dialog__clear"
          @click="clearPending"
        >
          清空
        </ElButton>
      </header>
      <ElTable
        :data="pendingTemplates"
        size="small"
        max-height="160"
        empty-text="填写上方时段并添加后，在此确认放号"
      >
        <ElTableColumn label="时段" min-width="108">
          <template #default="{ row }">{{ formatTemplateTimeRange(row) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="capacity" label="容量" width="64" align="center" />
        <ElTableColumn label="操作" width="72" align="center">
          <template #default="{ $index }">
            <ElButton link type="danger" @click="removePending($index)">移除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </section>

    <template #footer>
      <ElButton @click="emit('update:visible', false)">关闭</ElButton>
      <ElButton
        type="primary"
        :loading="provisionLoading"
        :disabled="pendingTemplates.length === 0"
        @click="handleSubmit"
      >
        确认放号{{ pendingTemplates.length ? `（${pendingTemplates.length} 段）` : '' }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.provision-slot-dialog__section + .provision-slot-dialog__section {
  margin-top: 16px;
}

.provision-slot-dialog__section-title {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
}

.provision-slot-dialog__date-hint {
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.provision-slot-dialog__hint {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.provision-slot-dialog__t1-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.provision-slot-dialog__hint--secondary {
  margin-top: 4px;
  margin-bottom: 0;
}

.provision-slot-dialog__range-form {
  margin-bottom: 0;
}

.provision-slot-dialog__unit {
  margin-left: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.provision-slot-dialog__count {
  margin-left: 4px;
}

.provision-slot-dialog__clear {
  margin-left: auto;
}
</style>
