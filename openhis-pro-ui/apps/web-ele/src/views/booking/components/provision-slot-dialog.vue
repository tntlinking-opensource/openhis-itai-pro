<script setup lang="ts">
import type { BookingEntityId } from '#/api/booking/booking-id';
import { bookingOptionValue, isSameBookingId, toBookingId } from '#/api/booking/booking-id';
import type { SlotTemplateRequest } from '#/api/booking/outpatient-schedule';
import type { SlotListItemDto } from '#/api/booking/slot-manage';
import type { BookingOptionDto } from '#/api/booking/booking-types';
import type { SlotProvisionTemplateItemDto } from '#/api/booking/schedule-template';

import { computed, ref, watch } from 'vue';

import {
  filterAgainstExistingWindows,
  formatTemplateTimeRange,
  mergePendingTemplates,
  slotWindowKey,
  slotWindowKeyFromListItem,
} from '#/api/booking/provision-slot-presets';
import { provisionSlotBatchApi } from '#/api/booking/outpatient-schedule';
import { loadSlotProvisionTemplateApi } from '#/api/booking/schedule-template';
import { deleteSlotsApi, pageSlotsByDateApi } from '#/api/booking/slot-manage';
import SlotTemplateRowsField from '#/views/booking/components/slot-template-rows-field.vue';
import {
  createDefaultSlotRow,
  slotRowsToTemplateRequests,
} from '#/views/booking/components/slot-template-rows.util';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

const props = defineProps<{
  visible: boolean;
  organizationId?: BookingEntityId;
  scheduleId?: BookingEntityId;
  /** 日程关联的 T1 放号时段模板（日/周排班生成时会带入） */
  slotProvisionTemplateId?: BookingEntityId;
  /** 手动新建日程时可选的放号时段列表 */
  slotProvisionTemplateOptions?: BookingOptionDto[];
  /** 周历选中日期，用于查询当日已放号源 */
  selectedDate?: string;
}>();

const emit = defineEmits<{
  'update:visible': [value: boolean];
  done: [];
}>();

const pendingTemplates = ref<SlotTemplateRequest[]>([]);
const t1FillLoading = ref(false);
const selectedT1ImportId = ref<BookingEntityId>();
const autoFilledFromT1 = ref(false);
/** 手动维护时段行，与「排班设置 → 放号时段」编辑方式一致 */
const manualRows = ref<SlotProvisionTemplateItemDto[]>([createDefaultSlotRow()]);

const hasLinkedT1 = computed(() => Boolean(toBookingId(props.slotProvisionTemplateId)));
const canImportT1 = computed(() => (props.slotProvisionTemplateOptions?.length ?? 0) > 0);

const orgDaySlots = ref<SlotListItemDto[]>([]);
const deletingSlotId = ref<BookingEntityId>();

/** 本日程已放号（表格展示） */
const scheduleExistingSlots = computed(() => {
  const scheduleId = toBookingId(props.scheduleId);
  if (!scheduleId) {
    return [];
  }
  return orgDaySlots.value.filter((slot) => isSameBookingId(slot.scheduleId, scheduleId));
});

/** 已停诊/作废日程下的号源不再占用机构时间窗 */
function isReleasedOrgSlot(slot: SlotListItemDto): boolean {
  return slot.displayStatus === '停诊';
}

/** 同科室当日已占用时间窗（库 uk_wkf_slot_org_time_window，含其他有效日程） */
function buildOccupiedOrgWindows(includePending = false): Set<string> {
  const windows = new Set<string>();
  for (const slot of orgDaySlots.value) {
    if (isReleasedOrgSlot(slot)) {
      continue;
    }
    const key = slotWindowKeyFromListItem(slot);
    if (key) {
      windows.add(key);
    }
  }
  if (includePending) {
    for (const template of pendingTemplates.value) {
      windows.add(slotWindowKey(template));
    }
  }
  return windows;
}

const { run: runLoadSlots, loading: slotsLoading } = useRequest(
  async () => {
    if (!props.organizationId || !props.selectedDate) {
      orgDaySlots.value = [];
      return [];
    }
    const res = await pageSlotsByDateApi({
      organizationId: props.organizationId,
      date: props.selectedDate,
      pageNo: 1,
      pageSize: 500,
    });
    orgDaySlots.value = res?.content ?? [];
    return orgDaySlots.value;
  },
  { manual: true },
);

watch(
  () => props.visible,
  async (open) => {
    if (!open) {
      return;
    }
    pendingTemplates.value = [];
    selectedT1ImportId.value = undefined;
    autoFilledFromT1.value = false;
    manualRows.value = [createDefaultSlotRow()];
    await runLoadSlots();
    if (hasLinkedT1.value) {
      const filled = await fillPendingFromT1(props.slotProvisionTemplateId, { silent: true });
      autoFilledFromT1.value = filled;
    }
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
      manualRows.value = [createDefaultSlotRow()];
      await runLoadSlots();
      emit('done');
    },
  },
);

async function handleSubmit() {
  const { accepted, skippedExisting } = filterAgainstExistingWindows(
    pendingTemplates.value,
    buildOccupiedOrgWindows(false),
  );
  if (skippedExisting > 0 && accepted.length === 0) {
    ElMessage.warning('待放号时段与同科室其他日程已占用的时间窗冲突，请调整时段或先删除冲突号源');
    return;
  }
  if (skippedExisting > 0) {
    pendingTemplates.value = accepted;
    ElMessage.warning(
      `已跳过 ${skippedExisting} 个与同科室冲突的时段，将放号其余 ${accepted.length} 段`,
    );
  }
  try {
    await runProvision(accepted.length ? accepted : pendingTemplates.value);
  } catch (error) {
    const message = (error as Error).message || '放号失败';
    if (message.includes('uk_wkf_slot_org_time_window') || message.includes('重复键')) {
      ElMessage.error(
        '放号失败：该时段与同科室其他日程已占用相同时间窗（08:00-12:00 等不能重复）。请调整时段或删除冲突号源后重试。',
      );
      return;
    }
    ElMessage.error(message);
  }
}

function resolveDate() {
  return props.selectedDate ?? new Date().toISOString().slice(0, 10);
}

/** 将时段行转为待放号并合并进列表 */
function mergeRowsIntoPending(
  rows: SlotProvisionTemplateItemDto[],
  options?: { silent?: boolean; replacePending?: boolean },
): boolean {
  const generated = slotRowsToTemplateRequests(rows, resolveDate());
  if (generated.length === 0) {
    if (!options?.silent) {
      ElMessage.warning('请至少填写一行有效的起止时间与容量');
    }
    return false;
  }
  const { accepted, skippedExisting } = filterAgainstExistingWindows(
    generated,
    buildOccupiedOrgWindows(true),
  );
  const beforeCount = options?.replacePending ? 0 : pendingTemplates.value.length;
  const base = options?.replacePending ? [] : pendingTemplates.value;
  pendingTemplates.value = mergePendingTemplates(base, accepted);
  const added = pendingTemplates.value.length - beforeCount;
  if (added <= 0) {
    if (!options?.silent) {
      ElMessage.warning(
        skippedExisting > 0
          ? '时段与同科室其他日程已占用的时间窗冲突，或已在待放号列表中'
          : '时段均已存在或已在待放号列表中',
      );
    }
    return pendingTemplates.value.length > 0;
  }
  if (!options?.silent) {
    ElMessage.success(
      `已添加 ${added} 个时段${skippedExisting > 0 ? `，跳过 ${skippedExisting} 个与同科室冲突` : ''}`,
    );
  }
  return true;
}

/** 按 T1 模板填充待放号或手动编辑区 */
async function fillPendingFromT1(
  templateId?: BookingEntityId,
  options?: { silent?: boolean; toManualRows?: boolean; merge?: boolean },
): Promise<boolean> {
  const resolvedId = toBookingId(templateId);
  if (!resolvedId) {
    if (!options?.silent) {
      ElMessage.warning('请选择放号时段');
    }
    return false;
  }
  t1FillLoading.value = true;
  try {
    const detail = await loadSlotProvisionTemplateApi({ id: resolvedId });
    const rows = (detail?.slotTemplates ?? []).filter((item) => item.startTime && item.endTime);
    if (rows.length === 0) {
      if (!options?.silent) {
        ElMessage.warning('放号时段模板无有效时段行');
      }
      return false;
    }
    if (options?.toManualRows) {
      manualRows.value = rows.map((item) => ({
        startTime: item.startTime,
        endTime: item.endTime,
        capacity: item.capacity ?? 1,
      }));
      if (!options?.silent) {
        ElMessage.success('已导入到下方时段行，可编辑后加入待放号');
      }
      return true;
    }
    return mergeRowsIntoPending(rows, {
      silent: options?.silent,
      replacePending: options?.merge === false,
    });
  } catch (error) {
    if (!options?.silent) {
      ElMessage.error((error as Error).message || '加载放号时段失败');
    }
    return false;
  } finally {
    t1FillLoading.value = false;
  }
}

/** 从下拉选择的 T1 导入到手动编辑区 */
async function handleImportSelectedT1() {
  await fillPendingFromT1(selectedT1ImportId.value, { toManualRows: true });
}

/** 将手动编辑区时段加入待放号 */
function handleAddManualRowsToPending() {
  mergeRowsIntoPending(manualRows.value);
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
        :data="scheduleExistingSlots"
        size="small"
        max-height="180"
        empty-text="暂无号源"
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
      <header class="provision-slot-dialog__section-title">维护时段</header>
      <p
        v-if="hasLinkedT1 && autoFilledFromT1 && pendingTemplates.length"
        class="provision-slot-dialog__hint provision-slot-dialog__hint--primary"
      >
        已从关联的<strong>放号时段</strong>自动带入待放号列表；如需调整可手动增删行后重新加入。
      </p>
      <p v-else class="provision-slot-dialog__hint">
        可导入放号时段模板，或按行填写起止时间与容量（与「排班设置 → 放号时段」一致）。
        同一科室同一天，相同起止时间只能放号一次（其他<strong>有效</strong>日程已占用的时段会自动跳过；
        已停诊日程的号源不占用时间窗，放号时将自动回收）。
      </p>
      <ElForm label-width="88px" class="provision-slot-dialog__manual-form">
        <div v-if="canImportT1" class="provision-slot-dialog__t1-row">
          <ElSelect
            v-model="selectedT1ImportId"
            filterable
            clearable
            placeholder="选择放号时段导入"
            style="width: 240px"
          >
            <ElOption
              v-for="item in slotProvisionTemplateOptions"
              :key="item.value"
              :label="item.label"
              :value="bookingOptionValue(item.value)"
            />
          </ElSelect>
          <ElButton
            :loading="t1FillLoading"
            type="primary"
            plain
            :disabled="!selectedT1ImportId"
            @click="handleImportSelectedT1"
          >
            导入放号时段
          </ElButton>
        </div>
        <SlotTemplateRowsField v-model="manualRows" label="时段" />
        <div class="provision-slot-dialog__actions">
          <ElButton type="primary" plain @click="handleAddManualRowsToPending">
            加入待放号
          </ElButton>
        </div>
      </ElForm>
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
        empty-text="导入或手动维护时段后，点「加入待放号」"
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

.provision-slot-dialog__hint--primary {
  margin-bottom: 8px;
  color: var(--el-color-primary);
}

.provision-slot-dialog__manual-form {
  margin-bottom: 0;
}

.provision-slot-dialog__actions {
  margin-top: 4px;
}

.provision-slot-dialog__count {
  margin-left: 4px;
}

.provision-slot-dialog__clear {
  margin-left: auto;
}
</style>
