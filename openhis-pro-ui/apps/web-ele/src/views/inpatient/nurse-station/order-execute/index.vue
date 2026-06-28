<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type {
  NurseStationOrderExecuteMedicineCategoryTab,
  NurseStationOrderExecuteTab,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.types';
import type {
  NurseStationOrderExecutePlanSlot,
  NurseStationOrderExecuteTableRow,
} from './services/map-nurse-station-order-execute';

import { computed, inject, ref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElMessage, ElMessageBox } from 'element-plus';

import { httpCancelNurseStationOrderExecutions } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-cancel-execute.http';
import {
  httpExecuteNurseStationOrders,
  httpListNurseStationOrderExecute,
  httpSkipNurseStationOrderSlots,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.http';
import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';
import {
  adviceTherapyTypeClass,
  getAdviceStatusTagType,
} from '#/views/inpatient/doctor-station/advice/services/advice-list';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import NurseStationPatientOrderCollapseTitle from '../components/nurse-station-patient-order-collapse-title.vue';
import { nurseStationPatientListPanelContextKey } from '../components/patient-list-panel.context';
import {
  indexExecuteGroupsByEncounterId,
  mapSelectedSlotsToCancelProcedureIds,
  mapSelectedSlotsToSkipItems,
  mapSelectedSlotsToWriteItems,
  resolveExecuteBindItemDisplayText,
  resolveExecuteBindItemKey,
  resolveExecuteBindItemUnitLabel,
  resolveExecuteBindQuantityValue,
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

const drugCategoryTabs: Array<{
  label: string;
  value: NurseStationOrderExecuteMedicineCategoryTab;
}> = [
  { label: '西药', value: 'west' },
  { label: '中药', value: 'tcm' },
];

const listPanelContext = inject(nurseStationPatientListPanelContextKey, null);

const statusTabs: Array<{ label: string; value: ExecuteTab }> = [
  { label: '待执行', value: 'pending' },
  { label: '已执行', value: 'executed' },
  { label: '不执行', value: 'skipped' },
  { label: '取消执行', value: 'cancelled' },
];

const activeStatus = ref<ExecuteTab>('pending');
const activeDrugCategoryTab = ref<NurseStationOrderExecuteMedicineCategoryTab>('west');
const activeTimeType = ref<TimeTypeFilter>('all');
/** 槽位勾选态（按 slotKey 存，避免每次勾选克隆 Set 触发整表重渲染） */
const slotSelectionByKey = ref<Record<string, boolean>>({});
const ordersByEncounterId = ref<Map<string, NurseStationOrderExecuteTableRow[]>>(new Map());
const executeDeadline = ref(dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss'));

const listRequest = useRequest(httpListNurseStationOrderExecute, { manual: true });
const executeRequest = useRequest(httpExecuteNurseStationOrders, {
  manual: true,
  refreshOnStale: () => loadExecuteList(),
});
const skipRequest = useRequest(httpSkipNurseStationOrderSlots, {
  manual: true,
  refreshOnStale: () => loadExecuteList(),
});
const cancelExecuteRequest = useRequest(httpCancelNurseStationOrderExecutions, {
  manual: true,
  refreshOnStale: () => loadExecuteList(),
});

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

/** 待执行 / 取消执行 Tab：批量正向执行 */
const executeActionsEnabled = computed(
  () => activeStatus.value === 'pending' || activeStatus.value === 'cancelled',
);

/** 待执行 Tab：批量不执行 */
const skipActionsEnabled = computed(() => activeStatus.value === 'pending');

/** 已执行 Tab：批量取消执行 */
const cancelActionsEnabled = computed(() => activeStatus.value === 'executed');

/** 待执行 / 已执行 / 取消执行 Tab 允许勾选槽位 */
const slotSelectionEnabled = computed(
  () => executeActionsEnabled.value || skipActionsEnabled.value || cancelActionsEnabled.value,
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

/** 已勾选槽位数（供全选态与批量写路径复用） */
const selectedSlotCount = computed(() => {
  let count = 0;
  const selection = slotSelectionByKey.value;
  for (const key in selection) {
    if (selection[key]) {
      count += 1;
    }
  }
  return count;
});

/** 全选开关：当前 Tab 下可操作槽位已全部勾选 */
const selectedAll = computed(() => {
  const total = allVisiblePlanSlots.value.length;
  return total > 0 && selectedSlotCount.value === total;
});

/** 写路径过滤用：已勾选 slotKey 集合 */
function buildSelectedSlotKeySet(): Set<string> {
  const keys = new Set<string>();
  const selection = slotSelectionByKey.value;
  for (const key in selection) {
    if (selection[key]) {
      keys.add(key);
    }
  }
  return keys;
}

/** 清空当前列表槽位勾选 */
function clearSlotSelection() {
  slotSelectionByKey.value = {};
}

/** 勾选当前 Tab 下全部可操作时点 */
function selectAllVisiblePlanSlots() {
  if (!slotSelectionEnabled.value) {
    clearSlotSelection();
    return;
  }
  const next: Record<string, boolean> = {};
  for (const slot of allVisiblePlanSlots.value) {
    next[slot.slotKey] = true;
  }
  slotSelectionByKey.value = next;
}

/** 全选开关：选中或清空当前列表全部可操作时点 */
function handleSelectedAllChange(on: boolean) {
  if (!on) {
    clearSlotSelection();
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
  [encounterIds, activeStatus, activeDrugCategoryTab, activeTimeType, executeDeadline],
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

/** 医嘱状态 Tag 颜色（已停嘱用红色） */
function getExecuteStatusTagType(row: NurseStationOrderExecuteTableRow) {
  if (row.halted) return 'danger';
  return getAdviceStatusTagType({
    statusEnum: row.statusEnum,
    status: row.statusDisplay,
  });
}

/** 是否药嘱行（绑定项下沉到用法频次下方） */
function isExecuteMedicineOrder(row: NurseStationOrderExecuteTableRow): boolean {
  return row.orderType === 'MED_REQUEST';
}

/** 待执行 Tab 允许护士手改绑定项数量 */
const bindQuantityEditable = computed(() => executeActionsEnabled.value);

/** 绑定项数量签名（供 v-memo 感知手改数量） */
function bindItemsQuantitySig(row: NurseStationOrderExecuteTableRow): string {
  return row.bindItems.map((bind) => String(bind.quantityNumerator ?? '')).join('|');
}

/** 回写绑定项数量到列表行（执行写路径会原样携带 bindItems） */
function updateBindItemQuantity(
  encounterId: string,
  row: NurseStationOrderExecuteTableRow,
  bindIndex: number,
  value: number | undefined,
) {
  const rows = ordersByEncounterId.value.get(encounterId);
  if (!rows) return;
  const rowIndex = rows.findIndex((item) => item.id === row.id);
  if (rowIndex < 0) return;
  const bindItems = rows[rowIndex]?.bindItems;
  if (!bindItems?.[bindIndex]) return;
  const nextValue = value == null || Number.isNaN(value) ? 1 : Math.max(1, Math.floor(value));
  bindItems[bindIndex].quantityNumerator = nextValue;
  ordersByEncounterId.value = new Map(ordersByEncounterId.value);
}

/** 切换槽位勾选（按 key 原地更新，仅刷新对应勾选框） */
function toggleSlotSelection(slot: NurseStationOrderExecutePlanSlot, checked: boolean) {
  if (checked) {
    slotSelectionByKey.value[slot.slotKey] = true;
    return;
  }
  delete slotSelectionByKey.value[slot.slotKey];
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
  clearSlotSelection();
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
    medicineCategoryTab: activeDrugCategoryTab.value,
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
  await loadExecuteList();
  await listPanelContext?.refreshPatientFilters();
}

/** 批量执行医嘱（实际执行时间取当前时刻） */
async function handleExecuteOrders() {
  if (!executeActionsEnabled.value) return;
  const actualTime = dayjs().format('YYYY-MM-DD HH:mm:ss');
  const selectedKeys = buildSelectedSlotKeySet();
  const selectedCount = selectedKeys.size;
  const items = mapSelectedSlotsToWriteItems(
    collectAllTableRows(),
    selectedKeys,
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
  if (!skipActionsEnabled.value) return;
  const items = mapSelectedSlotsToSkipItems(collectAllTableRows(), buildSelectedSlotKeySet());
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

/** 批量取消已执行计划点（需填写取消原因） */
async function handleCancelExecute() {
  if (!cancelActionsEnabled.value) return;
  const selectedKeys = buildSelectedSlotKeySet();
  const selectedCount = selectedKeys.size;
  const { procedureIds, missingCount } = mapSelectedSlotsToCancelProcedureIds(
    collectAllTableRows(),
    selectedKeys,
  );
  if (missingCount > 0) {
    ElMessage.warning('部分计划点缺少执行记录，请刷新列表后重试');
    return;
  }
  if (!assertBatchWithinLimit(procedureIds.length)) return;
  if (procedureIds.length < selectedCount) {
    ElMessage.warning('部分计划点不可取消，请刷新后重试');
    return;
  }

  let statusReasonText: string | undefined;
  try {
    const { value } = await ElMessageBox.prompt(
      `将取消已选 ${procedureIds.length} 个计划点；已发药将生成待退单。请填写取消原因：`,
      '取消执行',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '取消原因',
        inputValidator: (val) => (val?.trim() ? true : '请填写取消原因'),
      },
    );
    statusReasonText = value.trim();
  } catch {
    return;
  }

  const res = await cancelExecuteRequest.run({
    encounterIds: encounterIds.value,
    procedureIds,
    statusReasonText: statusReasonText ?? '',
  });
  const n = res.cancelledProcedureIds?.length ?? procedureIds.length;
  const summaryHint = (res.rebuiltSummaryNos?.length ?? 0) > 0 ? '，已自动重建汇总单' : '';
  await afterWriteSuccess(res.message?.trim() || `已成功取消 ${n} 个计划点${summaryHint}`);
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
        <ElRadioGroup v-model="activeDrugCategoryTab" class="execute-drug-category-radio shrink-0">
          <ElRadioButton v-for="tab in drugCategoryTabs" :key="tab.value" :label="tab.value">
            {{ tab.label }}
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
        <ElButton type="primary" :disabled="!executeActionsEnabled" @click="handleExecuteOrders">
          执行医嘱
        </ElButton>
        <ElButton type="warning" plain :disabled="!skipActionsEnabled" @click="handleSkipOrders">
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
              class="order-table order-table--horizontal-only order-execute-table"
              stripe
              row-key="id"
            >
              <ElTableColumn align="center" label="状态" width="96">
                <template #default="{ row }">
                  <div v-memo="[row.id, row.statusEnum, row.statusDisplay, row.halted]">
                    <ElTag
                      v-if="row.statusDisplay && row.statusDisplay !== '—'"
                      size="small"
                      effect="light"
                      :type="getExecuteStatusTagType(row)"
                    >
                      {{ row.statusDisplay }}
                    </ElTag>
                    <span v-else class="text-[var(--el-text-color-secondary)]">—</span>
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn align="center" label="类型" min-width="108">
                <template #default="{ row }">
                  <div v-memo="[row.id, row.orderKindLabel, row.therapyLabel, row.therapyTypeCode]">
                    <span>{{ row.orderKindLabel }}</span>
                    <template v-if="row.therapyLabel">
                      <span class="ip-advice-type-sep"> / </span>
                      <span
                        class="ip-advice-therapy-text"
                        :class="adviceTherapyTypeClass(row.therapyTypeCode)"
                      >
                        {{ row.therapyLabel }}
                      </span>
                    </template>
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn label="医嘱内容" min-width="360">
                <template #default="{ row }">
                  <div
                    v-memo="[
                      row.id,
                      row.content,
                      row.dosage,
                      row.extension,
                      bindQuantityEditable,
                      bindItemsQuantitySig(row),
                    ]"
                    class="nurse-order-content-cell"
                  >
                    <template v-if="isExecuteMedicineOrder(row)">
                      <AdviceOrderDisplayCell
                        :row-key="row.id"
                        :content="executeOrderDisplayedContent(row)"
                        :dosage-text="row.dosage"
                        :extension="row.extension"
                      />
                    </template>
                    <template v-else-if="row.bindItems?.length">
                      <div class="advice-order-line-row">
                        <span class="drug-main-text">
                          {{ executeOrderDisplayedContent(row) }}
                        </span>
                        <span v-if="row.dosage" class="group-drug-dose">{{ row.dosage }}</span>
                      </div>
                      <div v-if="row.extension" class="normal-drug-shared">{{ row.extension }}</div>
                    </template>
                    <AdviceOrderDisplayCell
                      v-else
                      :row-key="row.id"
                      :content="executeOrderDisplayedContent(row)"
                      :dosage-text="row.dosage"
                      :extension="row.extension"
                    />
                    <div
                      v-if="row.bindItems?.length"
                      class="order-execute-bind-follow"
                      :class="{ 'order-execute-bind-follow--after-extension': !!row.extension }"
                    >
                      <div
                        v-for="(bind, bindIndex) in row.bindItems"
                        :key="resolveExecuteBindItemKey(bind, bindIndex)"
                        class="order-execute-bind-attach-row"
                      >
                        <span class="order-execute-bind-tag">带</span>
                        <span
                          class="order-execute-bind-name"
                          :title="resolveExecuteBindItemDisplayText(bind)"
                        >
                          {{ resolveExecuteBindItemDisplayText(bind) }}
                        </span>
                        <div class="order-execute-bind-qty-group">
                          <ElInputNumber
                            v-if="bindQuantityEditable"
                            class="order-execute-bind-qty-input"
                            :model-value="resolveExecuteBindQuantityValue(bind)"
                            :min="1"
                            :step="1"
                            :precision="0"
                            :controls="false"
                            size="small"
                            @update:model-value="
                              (value) =>
                                updateBindItemQuantity(card.patient.id, row, bindIndex, value)
                            "
                          />
                          <span v-else class="order-execute-bind-qty-readonly">
                            {{ resolveExecuteBindQuantityValue(bind) }}
                          </span>
                          <span
                            v-if="resolveExecuteBindItemUnitLabel(bind)"
                            class="order-execute-bind-unit"
                          >
                            {{ resolveExecuteBindItemUnitLabel(bind) }}
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn align="center" label="开始/终止" min-width="110">
                <template #default="{ row }">
                  <span v-memo="[row.id, row.startStopText]" class="start-stop-cell">{{
                    row.startStopText
                  }}</span>
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
                            :model-value="!!slotSelectionByKey[slot.slotKey]"
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

.order-execute-bind-follow {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.order-execute-bind-follow:not(.order-execute-bind-follow--after-extension) {
  border-top: 1px dashed #cbd5e1;
  padding-top: 3px;
}

.order-execute-bind-attach-row {
  align-items: center;
  display: flex;
  font-size: 12px;
  gap: 6px;
  min-width: 0;
}

.order-execute-bind-tag {
  color: #2563eb;
  flex-shrink: 0;
}

.order-execute-bind-name {
  color: var(--el-text-color-primary);
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-execute-bind-qty-group {
  align-items: center;
  display: inline-flex;
  flex-shrink: 0;
  gap: 2px;
  margin-left: auto;
}

.order-execute-bind-qty-input {
  width: 48px;
}

.order-execute-bind-qty-input :deep(.el-input__wrapper) {
  background: var(--el-fill-color-blank);
  box-shadow: 0 0 0 1px var(--el-border-color-lighter) inset;
  padding: 0 4px;
}

.order-execute-bind-qty-input :deep(.el-input__inner) {
  color: var(--el-text-color-regular);
  font-size: 12px;
  height: 22px;
  text-align: center;
}

.order-execute-bind-qty-readonly,
.order-execute-bind-unit {
  color: var(--el-text-color-regular);
  white-space: nowrap;
}
</style>
