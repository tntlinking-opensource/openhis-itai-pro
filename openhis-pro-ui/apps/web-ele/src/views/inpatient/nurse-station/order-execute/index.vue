<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type { NurseStationOrderExecuteTab } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.types';
import type {
  NurseStationOrderExecutePlanSlot,
  NurseStationOrderExecuteTableRow,
} from './services/map-nurse-station-order-execute';

import { computed, inject, ref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  httpExecuteNurseStationOrders,
  httpListNurseStationOrderExecute,
  httpSkipNurseStationOrderSlots,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.http';
import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

import NurseStationPatientOrderCollapseTitle from '../components/nurse-station-patient-order-collapse-title.vue';
import { nurseStationPatientListPanelContextKey } from '../components/patient-list-panel.context';
import {
  indexExecuteGroupsByEncounterId,
  mapSelectedSlotsToSkipItems,
  mapSelectedSlotsToWriteItems,
} from './services/map-nurse-station-order-execute';

type ExecuteTab = NurseStationOrderExecuteTab;
type TimeTypeFilter = 'all' | 'long' | 'temp';

interface ExecutePatientCard {
  patient: NurseStationModulePatient;
  orders: NurseStationOrderExecuteTableRow[];
}

interface Props {
  patients?: NurseStationModulePatient[];
}

defineOptions({
  name: 'InpatientNurseStationOrderExecuteModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

const WRITE_BATCH_LIMIT = 50;

const listPanelContext = inject(nurseStationPatientListPanelContextKey, null);

const statusTabs: Array<{ label: string; value: ExecuteTab }> = [
  { label: '待执行', value: 'pending' },
  { label: '已执行', value: 'executed' },
  { label: '不执行', value: 'skipped' },
  { label: '取消执行', value: 'cancelled' },
];

const activeStatus = ref<ExecuteTab>('pending');
const activeTimeType = ref<TimeTypeFilter>('all');
const selectedAll = ref(false);
const selectedSlotKeys = ref<Set<string>>(new Set());
const ordersByEncounterId = ref<Map<string, NurseStationOrderExecuteTableRow[]>>(new Map());
const executeDeadline = ref(dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss'));

const listRequest = useRequest(httpListNurseStationOrderExecute, { manual: true });
const executeRequest = useRequest(httpExecuteNurseStationOrders, { manual: true });
const skipRequest = useRequest(httpSkipNurseStationOrderSlots, { manual: true });

const encounterIds = computed(() =>
  props.patients.map((p) => String(p.id).trim()).filter((id) => id.length > 0),
);

/** 就诊 id → 住院账户 id（来自左侧患者队列） */
const accountIdByEncounterId = computed(() => {
  const map = new Map<string, string>();
  for (const patient of props.patients) {
    const encounterId = String(patient.id).trim();
    const accountId = preserveApiIdForRequest(patient.accountId);
    if (encounterId && accountId) {
      map.set(encounterId, accountId);
    }
  }
  return map;
});

/** 待执行 Tab：批量执行 / 不执行 */
const writeActionsEnabled = computed(() => activeStatus.value === 'pending');

/** 已执行 Tab：批量取消执行 */
const cancelActionsEnabled = computed(() => activeStatus.value === 'executed');

/** 待执行 / 已执行 Tab 允许勾选槽位 */
const slotSelectionEnabled = computed(
  () => writeActionsEnabled.value || cancelActionsEnabled.value,
);

const visibleCards = computed<ExecutePatientCard[]>(() => {
  const index = ordersByEncounterId.value;
  return props.patients.map((patient) => {
    const encounterId = String(patient.id).trim();
    return {
      patient,
      orders: index.get(encounterId) ?? [],
    };
  });
});

const cardCountText = computed(() => `${visibleCards.value.length}位患者`);
const expandedPatientIds = ref<string[]>([]);

/** 当前 Tab 下全部可勾选槽位（用于全选） */
const allVisiblePlanSlots = computed(() => {
  const slots: NurseStationOrderExecutePlanSlot[] = [];
  for (const card of visibleCards.value) {
    for (const row of card.orders) {
      for (const schedule of row.planSchedules) {
        slots.push(...schedule.slots);
      }
    }
  }
  return slots;
});

/** 槽位勾选变化时回写全选状态 */
watch(
  selectedSlotKeys,
  (keys) => {
    const total = allVisiblePlanSlots.value.length;
    selectedAll.value = total > 0 && keys.size === total;
  },
  { deep: true },
);

/** 勾选当前 Tab 下全部可操作时点 */
function selectAllVisiblePlanSlots() {
  if (!slotSelectionEnabled.value) {
    selectedSlotKeys.value = new Set();
    selectedAll.value = false;
    return;
  }
  const keys = allVisiblePlanSlots.value.map((slot) => slot.slotKey);
  selectedSlotKeys.value = new Set(keys);
  selectedAll.value = keys.length > 0;
}

/** 全选开关：选中或清空当前列表全部可操作时点 */
function handleSelectedAllChange(on: boolean) {
  if (!on) {
    selectedSlotKeys.value = new Set();
    selectedAll.value = false;
    return;
  }
  selectAllVisiblePlanSlots();
}

watch(
  visibleCards,
  (cards) => {
    expandedPatientIds.value = cards.map((item) => item.patient.id);
  },
  { immediate: true },
);

watch(
  [encounterIds, activeStatus, activeTimeType, executeDeadline],
  () => {
    void loadExecuteList();
  },
  { immediate: true },
);

/** 主文案：优先医嘱名称摘要 */
function executeOrderDisplayedContent(item: NurseStationOrderExecuteTableRow): string {
  const c = item.content?.trim();
  if (c && c.length > 0) return item.content;
  return item.typeLabel;
}

/** 判断槽位是否已勾选 */
function isSlotSelected(slot: NurseStationOrderExecutePlanSlot): boolean {
  return selectedSlotKeys.value.has(slot.slotKey);
}

/** 切换槽位勾选 */
function toggleSlotSelection(slot: NurseStationOrderExecutePlanSlot, checked: boolean) {
  const next = new Set(selectedSlotKeys.value);
  if (checked) {
    next.add(slot.slotKey);
  } else {
    next.delete(slot.slotKey);
  }
  selectedSlotKeys.value = next;
}

/** 收集当前列表全部表格行 */
function collectAllTableRows(): NurseStationOrderExecuteTableRow[] {
  const rows: NurseStationOrderExecuteTableRow[] = [];
  for (const card of visibleCards.value) {
    rows.push(...card.orders);
  }
  return rows;
}

/** 按当前患者与筛选条件加载医嘱执行列表 */
async function loadExecuteList() {
  selectedSlotKeys.value = new Set();
  selectedAll.value = false;
  ordersByEncounterId.value = new Map();

  if (encounterIds.value.length === 0) {
    return;
  }

  const deadline = String(executeDeadline.value ?? '').trim();
  if (!deadline) {
    ElMessage.warning('请选择截止时间');
    return;
  }

  const res = await listRequest.run({
    encounterIds: encounterIds.value,
    executeTab: activeStatus.value,
    therapyType: activeTimeType.value,
    executionDeadline: deadline,
  });
  ordersByEncounterId.value = indexExecuteGroupsByEncounterId(res.groups, activeStatus.value);
  selectAllVisiblePlanSlots();
}

/** 工具栏「查询」：显式刷新列表 */
function handleRunFilterQuery() {
  void loadExecuteList();
}

/** 校验批量槽位数并提示 */
function assertBatchWithinLimit(count: number): boolean {
  if (count <= 0) {
    ElMessage.warning('请先勾选计划执行时点');
    return false;
  }
  if (count > WRITE_BATCH_LIMIT) {
    ElMessage.warning(`单次最多处理 ${WRITE_BATCH_LIMIT} 个计划点`);
    return false;
  }
  return true;
}

/** 写操作成功后刷新列表与左侧待执行计数 */
async function afterWriteSuccess(message: string) {
  ElMessage.success(message);
  selectedSlotKeys.value = new Set();
  selectedAll.value = false;
  await loadExecuteList();
  await listPanelContext?.refreshPatientFilters();
}

/** 批量执行医嘱（实际执行时间取当前时刻） */
async function handleExecuteOrders() {
  if (!writeActionsEnabled.value) return;
  const actualTime = dayjs().format('YYYY-MM-DD HH:mm:ss');
  const selectedCount = selectedSlotKeys.value.size;
  const items = mapSelectedSlotsToWriteItems(
    collectAllTableRows(),
    selectedSlotKeys.value,
    actualTime,
    true,
    accountIdByEncounterId.value,
    true,
  );
  if (items.length < selectedCount) {
    ElMessage.warning('部分患者缺少账户信息，请刷新患者列表后重试');
    return;
  }
  if (!assertBatchWithinLimit(items.length)) return;

  try {
    await ElMessageBox.confirm(`确认执行已选 ${items.length} 个计划点？`, '执行医嘱', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  const res = await executeRequest.run({ items });
  const n = res.processedCount ?? items.length;
  await afterWriteSuccess(`已成功执行 ${n} 个计划点`);
}

/** 批量登记不执行 */
async function handleSkipOrders() {
  if (!writeActionsEnabled.value) return;
  const items = mapSelectedSlotsToSkipItems(collectAllTableRows(), selectedSlotKeys.value);
  if (!assertBatchWithinLimit(items.length)) return;

  try {
    await ElMessageBox.confirm(`确认将已选 ${items.length} 个计划点登记为不执行？`, '不执行', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  const res = await skipRequest.run({ items });
  const n = res.processedCount ?? items.length;
  await afterWriteSuccess(`已登记不执行 ${n} 个计划点`);
}

/** 批量取消执行（写接口待汇总领药模块就绪后对接） */
async function handleCancelExecute() {
  if (!cancelActionsEnabled.value) return;
  const items = mapSelectedSlotsToSkipItems(collectAllTableRows(), selectedSlotKeys.value);
  if (!assertBatchWithinLimit(items.length)) return;

  try {
    await ElMessageBox.confirm(`确认取消已选 ${items.length} 个计划点的执行？`, '取消执行', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  ElMessage.info('取消执行接口暂未开放');
}
</script>

<template>
  <div
    v-loading="
      listRequest.loading.value || executeRequest.loading.value || skipRequest.loading.value
    "
    class="execute-module flex h-full min-h-0 w-full flex-1 flex-col overflow-hidden rounded-md bg-[#eef9fd]"
  >
    <section
      class="flex w-full flex-wrap items-center justify-between gap-2 border-b border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-bg-color)] px-2 py-1.5"
    >
      <div class="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-2">
        <ElRadioGroup v-model="activeStatus" class="execute-status-radio shrink-0">
          <ElRadioButton v-for="status in statusTabs" :key="status.value" :label="status.value">
            {{ status.label }}
          </ElRadioButton>
        </ElRadioGroup>
        <ElRadioGroup
          v-model="activeTimeType"
          class="execute-time-type-radio inline-flex shrink-0 flex-wrap items-center gap-3"
        >
          <ElRadio label="all">全部</ElRadio>
          <ElRadio label="long">长期</ElRadio>
          <ElRadio label="temp">临时</ElRadio>
        </ElRadioGroup>
        <span class="shrink-0 text-sm text-[var(--el-text-color-regular)]">截止时间</span>
        <ElDatePicker
          v-model="executeDeadline"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          format="YYYY-MM-DD HH:mm:ss"
          placeholder="请选择截止时间"
          class="execute-datetime"
        />
        <ElButton plain @click="handleRunFilterQuery">查询</ElButton>
      </div>
      <div class="flex flex-wrap items-center gap-1">
        <span class="mr-1 shrink-0 text-xs text-[var(--el-text-color-secondary)]">
          {{ cardCountText }}
        </span>
        <label
          v-if="slotSelectionEnabled"
          class="mr-1 flex shrink-0 items-center gap-1 text-xs text-[var(--el-text-color-regular)]"
        >
          全选
          <ElSwitch
            :model-value="selectedAll"
            :disabled="allVisiblePlanSlots.length === 0"
            inline-prompt
            active-text=" "
            inactive-text=" "
            @update:model-value="handleSelectedAllChange"
          />
        </label>
        <ElButton type="primary" :disabled="!writeActionsEnabled" @click="handleExecuteOrders">
          执行医嘱
        </ElButton>
        <ElButton type="warning" plain :disabled="!writeActionsEnabled" @click="handleSkipOrders">
          不执行
        </ElButton>
        <ElButton
          type="danger"
          plain
          :disabled="!cancelActionsEnabled"
          @click="handleCancelExecute"
        >
          取消执行
        </ElButton>
      </div>
    </section>

    <section v-if="visibleCards.length > 0" class="min-h-0 flex-1 overflow-auto p-2.5">
      <ElCollapse v-model="expandedPatientIds" class="patient-collapse">
        <ElCollapseItem
          v-for="card in visibleCards"
          :key="card.patient.id"
          :name="card.patient.id"
          class="patient-order-card mb-2.5 overflow-hidden rounded-lg border border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-bg-color)] last:mb-0"
        >
          <template #title>
            <NurseStationPatientOrderCollapseTitle :patient="card.patient" />
          </template>

          <div class="card-table-wrap">
            <ElTable
              v-if="card.orders.length > 0"
              :data="card.orders"
              class="order-table order-table--horizontal-only"
              stripe
              row-key="id"
            >
              <ElTableColumn prop="typeLabel" align="center" label="类型" min-width="108" />
              <ElTableColumn label="医嘱内容" min-width="360">
                <template #default="{ row }">
                  <AdviceOrderDisplayCell
                    :row-key="row.id"
                    :content="executeOrderDisplayedContent(row)"
                    :dosage-text="row.dosage"
                    :extension="row.extension"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn align="center" label="开始/终止" min-width="110">
                <template #default="{ row }">
                  <span class="start-stop-cell">{{ row.startStopText }}</span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="预计执行时点" min-width="220">
                <template #default="{ row }">
                  <div class="execute-schedule-list">
                    <div
                      v-for="schedule in row.planSchedules"
                      :key="`${row.id}-${schedule.date}`"
                      class="execute-schedule-row"
                    >
                      <span class="execute-schedule-date">{{ schedule.date }}</span>
                      <div class="execute-schedule-slots">
                        <template v-if="slotSelectionEnabled">
                          <ElCheckbox
                            v-for="slot in schedule.slots"
                            :key="slot.slotKey"
                            :model-value="isSlotSelected(slot)"
                            border
                            size="small"
                            @update:model-value="(v: boolean) => toggleSlotSelection(slot, v)"
                          >
                            <span>{{ slot.timeLabel }}</span>
                          </ElCheckbox>
                        </template>
                        <template v-else>
                          <span
                            v-for="slot in schedule.slots"
                            :key="slot.slotKey"
                            class="execute-slot-readonly"
                          >
                            {{ slot.timeLabel }}
                          </span>
                        </template>
                      </div>
                    </div>
                  </div>
                </template>
              </ElTableColumn>
            </ElTable>
            <div v-else class="py-6 text-center text-sm text-[var(--el-text-color-secondary)]">
              当前筛选下暂无医嘱
            </div>
          </div>
        </ElCollapseItem>
      </ElCollapse>
    </section>
    <section v-else class="flex min-h-0 min-w-0 flex-1 items-center justify-center bg-[#eef9fd]">
      <ElEmpty description="请先选择患者" />
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '../components/nurse-station-patient-order-list.scss';

.execute-datetime {
  width: 220px;
  max-width: 100%;
}

.execute-time-type-radio :deep(.el-radio) {
  margin-right: 0;
  height: auto;
  align-items: center;
}

.start-stop-cell {
  white-space: pre-line;
  font-size: 14px;
  line-height: 1.4;
}

.execute-schedule-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.execute-schedule-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.execute-schedule-date {
  width: 42px;
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.execute-schedule-slots {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
}

.execute-schedule-slots :deep(.el-checkbox) {
  margin-right: 0;
}

.execute-slot-readonly {
  display: inline-flex;
  align-items: center;
  padding: 0 6px;
  height: 24px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-small);
  font-size: 14px;
  color: var(--el-text-color-regular);
}
</style>
