<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type {
  NurseStationDispenseSummaryMedicineCategoryTab,
  NurseStationDispenseSummaryOrderRowDto,
  NurseStationDispenseSummarySlipHeaderDto,
  NurseStationDispenseSummarySlipLineDto,
  NurseStationDispenseSummaryTherapyFilter,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-dispense-summary.types';
import type { DispenseSummarySelectableSlot } from './services/map-nurse-station-dispense-summary';

import { computed, ref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  httpCancelMedicationSummary,
  httpCreateMedicationSummary,
  httpGetMedicationSummarySlipDetail,
  httpPageMedicationSummarySlipHeaders,
  httpPagePendingDispenseSummaryLines,
  httpSubmitMedicationSummary,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-dispense-summary.http';
import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';
import { useRequest } from '#/hooks/use-request';

import NurseStationPatientOrderCollapseTitle from '../components/nurse-station-patient-order-collapse-title.vue';
import DispenseSummaryDetailDialog from './components/dispense-summary-detail-dialog.vue';
import {
  buildDispenseSummaryVisibleCards,
  collectSelectableSlotsFromOrderRows,
  formatPlannedDispenseTimeLabel,
  formatQuantityWithUnit,
  formatSummarySlipDateTime,
  groupTimePointsForDisplay,
  indexPatientGroupsByEncounterId,
  isPendingSummarySlip,
  mapSelectedSlotsToCreateItems,
  resolveOrderRowKey,
  resolveSlipStatusText,
} from './services/map-nurse-station-dispense-summary';

type DispenseDisplayMode = 'detail' | 'summary';

interface Props {
  patients?: NurseStationModulePatient[];
}

defineOptions({
  name: 'InpatientNurseStationDispenseSummaryModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

const WRITE_BATCH_LIMIT = 500;
const SUBMIT_BATCH_LIMIT = 50;
const PENDING_PAGE_SIZE = 500;
const SUMMARY_PAGE_SIZE = 50;

const drugCategoryTabs: Array<{
  label: string;
  value: NurseStationDispenseSummaryMedicineCategoryTab;
}> = [
  { label: '西药', value: 'west' },
  { label: '中药', value: 'tcm' },
];

const displayModeTabs: Array<{ label: string; value: DispenseDisplayMode }> = [
  { label: '医嘱', value: 'detail' },
  { label: '汇总单', value: 'summary' },
];

const activeDrugCategoryTab = ref<NurseStationDispenseSummaryMedicineCategoryTab>('west');
const activeDisplayMode = ref<DispenseDisplayMode>('detail');
const activeOrderType = ref<NurseStationDispenseSummaryTherapyFilter>('all');
const selectedAll = ref(false);
const selectedSlotKeys = ref<Set<string>>(new Set());
const selectedSummarySlips = ref<NurseStationDispenseSummarySlipHeaderDto[]>([]);

/** 汇总领药查询截止时间 */
const queryDeadline = ref(dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss'));

const ordersByEncounterId = ref(new Map<string, NurseStationDispenseSummaryOrderRowDto[]>());
const summarySlipRows = ref<NurseStationDispenseSummarySlipHeaderDto[]>([]);
const summaryTotalCount = ref(0);

const detailDialogVisible = ref(false);
const detailDialogLoading = ref(false);
const detailDialogHeader = ref<NurseStationDispenseSummarySlipHeaderDto | null>(null);
const detailDialogLines = ref<NurseStationDispenseSummarySlipLineDto[]>([]);

const pendingLinesRequest = useRequest(httpPagePendingDispenseSummaryLines, { manual: true });
const summaryHeadersRequest = useRequest(httpPageMedicationSummarySlipHeaders, { manual: true });
const createSummaryRequest = useRequest(httpCreateMedicationSummary, { manual: true });
const submitSummaryRequest = useRequest(httpSubmitMedicationSummary, { manual: true });
const cancelSummaryRequest = useRequest(httpCancelMedicationSummary, { manual: true });

const encounterIds = computed(() =>
  props.patients.map((p) => String(p.id).trim()).filter((id) => id.length > 0),
);

const visibleCards = computed(() =>
  buildDispenseSummaryVisibleCards(props.patients, ordersByEncounterId.value),
);

const cardCountText = computed(() => `${visibleCards.value.length}位患者`);
const expandedPatientIds = ref<string[]>([]);
const summaryCountText = computed(() => `${summaryTotalCount.value}条记录`);

/** 明细 Tab 全部可勾选槽位 */
const allVisiblePlanSlots = computed(() =>
  collectSelectableSlotsFromOrderRows(ordersByEncounterId.value),
);

const detailActionsEnabled = computed(() => activeDisplayMode.value === 'detail');

/** 汇总 Tab 已选且待发送的单号 */
const selectedSubmittableBusNos = computed(() =>
  selectedSummarySlips.value
    .filter(isPendingSummarySlip)
    .map((row) => String(row.busNo ?? '').trim())
    .filter(Boolean),
);

/** 汇总 Tab 已选且可撤销的单号 */
const selectedCancellableBusNos = computed(() => selectedSubmittableBusNos.value);

/** 槽位勾选变化时回写全选状态 */
watch(
  selectedSlotKeys,
  (keys) => {
    const total = allVisiblePlanSlots.value.length;
    selectedAll.value = total > 0 && keys.size === total;
  },
  { deep: true },
);

watch(
  visibleCards,
  (cards) => {
    expandedPatientIds.value = cards.map((item) => item.patient.id);
  },
  { immediate: true },
);

watch(
  [encounterIds, activeDrugCategoryTab, activeOrderType, queryDeadline, activeDisplayMode],
  () => {
    if (activeDisplayMode.value === 'detail') {
      void loadPendingLines();
    } else {
      void loadSummaryHeaders();
    }
  },
  { immediate: true },
);

/** 医嘱名称摘要（medicationName + specificationText） */
function buildOrderContentTitle(row: NurseStationDispenseSummaryOrderRowDto): string {
  const name = String(row.medicationName ?? '').trim();
  const spec = String(row.specificationText ?? '').trim();
  if (name && spec) return `${name}【${spec}】`;
  return name || spec || '—';
}

/** 用法、频次扩展行（methodCodeText / frequencyCodeText） */
function buildOrderExtensionLine(row: NurseStationDispenseSummaryOrderRowDto): string {
  const method = String(row.methodCodeText ?? '').trim();
  const freq = String(row.frequencyCodeText ?? '').trim();
  if (method && freq) return `${method}，${freq}`;
  return method || freq || '';
}

/** 领药数量展示：优先首条 timePoint，否则 dosageInstruction */
function resolveOrderDosageText(row: NurseStationDispenseSummaryOrderRowDto): string {
  const firstPoint = row.timePoints?.[0];
  if (firstPoint) {
    return formatQuantityWithUnit(firstPoint.quantity, firstPoint.unitCodeText);
  }
  return String(row.dosageInstruction ?? '').trim() || '—';
}

/** 类型列：therapyEnumText */
function resolveTherapyTypeLabel(row: NurseStationDispenseSummaryOrderRowDto): string {
  const therapy = String(row.therapyEnumText ?? '').trim();
  if (therapy) return `${therapy}/药品`;
  return '药品';
}

/** 判断槽位是否已勾选 */
function isSlotSelected(slot: DispenseSummarySelectableSlot): boolean {
  return selectedSlotKeys.value.has(slot.slotKey);
}

/** 切换槽位勾选 */
function toggleSlotSelection(slot: DispenseSummarySelectableSlot, checked: boolean) {
  const next = new Set(selectedSlotKeys.value);
  if (checked) {
    next.add(slot.slotKey);
  } else {
    next.delete(slot.slotKey);
  }
  selectedSlotKeys.value = next;
}

/** 勾选当前列表全部可操作时点 */
function selectAllVisiblePlanSlots() {
  if (!detailActionsEnabled.value) {
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

/** 加载待汇总明细列表 */
async function loadPendingLines() {
  selectedSlotKeys.value = new Set();
  selectedAll.value = false;
  ordersByEncounterId.value = new Map();

  if (encounterIds.value.length === 0) {
    return;
  }

  const deadline = String(queryDeadline.value ?? '').trim();
  if (!deadline) {
    ElMessage.warning('请选择截止时间');
    return;
  }

  const res = await pendingLinesRequest.run({
    encounterIds: encounterIds.value,
    medicineCategoryTab: activeDrugCategoryTab.value,
    therapyFilter: activeOrderType.value,
    queryDeadline: deadline,
    pageNo: 1,
    pageSize: PENDING_PAGE_SIZE,
  });
  ordersByEncounterId.value = indexPatientGroupsByEncounterId(res.patientGroups);
  const total = res.totalCount ?? res.total ?? 0;
  if (total > PENDING_PAGE_SIZE) {
    ElMessage.warning(`待汇总明细共 ${total} 条，当前仅展示前 ${PENDING_PAGE_SIZE} 条`);
  }
  selectAllVisiblePlanSlots();
}

/** 加载已生成汇总单头列表 */
async function loadSummaryHeaders() {
  selectedSummarySlips.value = [];
  summarySlipRows.value = [];
  summaryTotalCount.value = 0;

  const res = await summaryHeadersRequest.run({
    pageNo: 1,
    pageSize: SUMMARY_PAGE_SIZE,
  });
  summarySlipRows.value = res.rows ?? [];
  summaryTotalCount.value = res.totalCount ?? res.total ?? summarySlipRows.value.length;
}

/** 工具栏「查询」：按当前 Tab 刷新列表 */
function handleRunFilterQuery() {
  if (activeDisplayMode.value === 'detail') {
    void loadPendingLines();
  } else {
    void loadSummaryHeaders();
  }
}

/** 校验批量槽位数并提示 */
function assertBatchWithinLimit(count: number): boolean {
  if (count <= 0) {
    ElMessage.warning('请先勾选待发药时间点');
    return false;
  }
  if (count > WRITE_BATCH_LIMIT) {
    ElMessage.warning(`单次最多汇总 ${WRITE_BATCH_LIMIT} 条明细`);
    return false;
  }
  return true;
}

/** 汇总保存成功后刷新明细与汇总 Tab */
async function afterCreateSummarySuccess(message: string) {
  ElMessage.success(message);
  selectedSlotKeys.value = new Set();
  selectedAll.value = false;
  await loadPendingLines();
  if (activeDisplayMode.value === 'summary') {
    await loadSummaryHeaders();
  }
}

/** 批量汇总保存 */
async function handleCreateSummaryPickup() {
  if (!detailActionsEnabled.value) return;
  const items = mapSelectedSlotsToCreateItems(selectedSlotKeys.value);
  if (!assertBatchWithinLimit(items.length)) return;

  try {
    await ElMessageBox.confirm(`确认汇总已选 ${items.length} 条待发药明细？`, '汇总领药', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  const res = await createSummaryRequest.run({ dispenseItems: items });
  const msg =
    res.message?.trim() ||
    (res.summaryNos?.length
      ? `已生成 ${res.summaryNos.length} 张汇总单，请在汇总单页发送`
      : '汇总保存成功');
  await afterCreateSummarySuccess(msg);
}

/** 汇总 Tab 表格多选变化 */
function handleSummarySelectionChange(rows: NurseStationDispenseSummarySlipHeaderDto[]) {
  selectedSummarySlips.value = rows;
}

/** 打开汇总单详情弹窗 */
async function handleViewSummarySheet(row: NurseStationDispenseSummarySlipHeaderDto) {
  const summaryNo = String(row.busNo ?? '').trim();
  if (!summaryNo) return;
  detailDialogVisible.value = true;
  detailDialogHeader.value = row;
  detailDialogLines.value = [];
  detailDialogLoading.value = true;
  try {
    const res = await httpGetMedicationSummarySlipDetail({ summaryNo });
    detailDialogLines.value = res.lines ?? [];
    if (res.header) {
      detailDialogHeader.value = res.header;
    }
  } finally {
    detailDialogLoading.value = false;
  }
}

/** 发送汇总单至药房 */
async function submitSummaryNos(busNos: string[], confirmMessage: string) {
  if (busNos.length === 0) {
    return;
  }
  if (busNos.length > SUBMIT_BATCH_LIMIT) {
    ElMessage.warning(`单次最多发送 ${SUBMIT_BATCH_LIMIT} 张汇总单`);
    return;
  }

  try {
    await ElMessageBox.confirm(confirmMessage, '发送汇总单', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  const res = await submitSummaryRequest.run({ summaryNos: busNos });
  ElMessage.success(res.message?.trim() || `已成功发送 ${busNos.length} 张汇总单`);
  await loadSummaryHeaders();
}

/** 发送已选待发送汇总单 */
async function handleSubmitSummarySheets() {
  const busNos = selectedSubmittableBusNos.value;
  if (busNos.length === 0) {
    ElMessage.warning('请勾选待发送状态的汇总单');
    return;
  }
  await submitSummaryNos(busNos, `确认发送已选 ${busNos.length} 张汇总单至药房？`);
}

/** 发送单行待发送汇总单 */
async function handleSubmitSingleSummarySheet(row: NurseStationDispenseSummarySlipHeaderDto) {
  if (!isPendingSummarySlip(row)) {
    ElMessage.warning('仅待发送状态的汇总单可发送');
    return;
  }
  const summaryNo = String(row.busNo ?? '').trim();
  if (!summaryNo) {
    return;
  }
  await submitSummaryNos([summaryNo], `确认发送汇总单 ${summaryNo} 至药房？`);
}

/** 撤销已选待发送汇总单 */
async function handleCancelSummarySheets() {
  const busNos = selectedCancellableBusNos.value;
  if (busNos.length === 0) {
    ElMessage.warning('请勾选待发送状态的汇总单');
    return;
  }
  if (busNos.length > SUBMIT_BATCH_LIMIT) {
    ElMessage.warning(`单次最多撤销 ${SUBMIT_BATCH_LIMIT} 张汇总单`);
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确认撤销已选 ${busNos.length} 张汇总单？撤销后对应明细将恢复为待汇总。`,
      '撤销汇总单',
      {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      },
    );
  } catch {
    return;
  }

  const res = await cancelSummaryRequest.run({ summaryNos: busNos });
  ElMessage.success(res.message?.trim() || `已成功撤销 ${busNos.length} 张汇总单`);
  await loadSummaryHeaders();
  if (encounterIds.value.length > 0) {
    await loadPendingLines();
  }
}

/** 领药时点勾选标签：时刻 + 数量（与医嘱执行槽位标签同构） */
function formatDispenseSlotLabel(slot: DispenseSummarySelectableSlot): string {
  const time = formatPlannedDispenseTimeLabel(slot.point.plannedDispenseTime);
  const qty = formatQuantityWithUnit(slot.point.quantity, slot.point.unitCodeText);
  if (time !== '—' && qty !== '—') return `${time} ${qty}`;
  return time !== '—' ? time : qty;
}
</script>

<template>
  <div
    v-loading="
      pendingLinesRequest.loading.value ||
      summaryHeadersRequest.loading.value ||
      createSummaryRequest.loading.value ||
      submitSummaryRequest.loading.value ||
      cancelSummaryRequest.loading.value
    "
    class="dispense-module flex h-full min-h-0 w-full flex-1 flex-col overflow-hidden rounded-md bg-[#eef9fd]"
  >
    <section
      class="flex w-full flex-wrap items-center justify-between gap-2 border-b border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-bg-color)] px-2 py-1.5"
    >
      <div class="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-2">
        <ElRadioGroup v-model="activeDrugCategoryTab" class="dispense-view-radio shrink-0">
          <ElRadioButton v-for="tab in drugCategoryTabs" :key="tab.value" :label="tab.value">
            {{ tab.label }}
          </ElRadioButton>
        </ElRadioGroup>
        <ElRadioGroup
          v-model="activeDisplayMode"
          class="dispense-display-radio inline-flex shrink-0 flex-wrap items-center"
        >
          <ElRadioButton v-for="tab in displayModeTabs" :key="tab.value" :label="tab.value">
            {{ tab.label }}
          </ElRadioButton>
        </ElRadioGroup>
        <ElRadioGroup
          v-if="detailActionsEnabled"
          v-model="activeOrderType"
          class="dispense-time-type-radio inline-flex shrink-0 flex-wrap items-center gap-3"
        >
          <ElRadio label="all">全部</ElRadio>
          <ElRadio label="long">长期</ElRadio>
          <ElRadio label="temp">临时</ElRadio>
        </ElRadioGroup>
        <template v-if="detailActionsEnabled">
          <span class="shrink-0 text-sm text-[var(--el-text-color-regular)]">截止时间</span>
          <ElDatePicker
            v-model="queryDeadline"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm:ss"
            placeholder="请选择截止时间"
            class="dispense-datetime"
          />
        </template>
        <ElButton plain @click="handleRunFilterQuery">查询</ElButton>
      </div>
      <div class="flex flex-wrap items-center gap-1">
        <span class="mr-1 shrink-0 text-xs text-[var(--el-text-color-secondary)]">{{
          activeDisplayMode === 'summary' ? summaryCountText : cardCountText
        }}</span>
        <template v-if="detailActionsEnabled">
          <label
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
          <ElButton type="primary" @click="handleCreateSummaryPickup">汇总领药</ElButton>
        </template>
        <template v-else>
          <ElButton
            type="primary"
            :disabled="selectedSubmittableBusNos.length === 0"
            @click="handleSubmitSummarySheets"
          >
            发送
          </ElButton>
          <ElButton
            type="danger"
            plain
            :disabled="selectedCancellableBusNos.length === 0"
            @click="handleCancelSummarySheets"
          >
            撤销
          </ElButton>
        </template>
      </div>
    </section>

    <section
      v-if="activeDisplayMode === 'detail' && visibleCards.length > 0"
      class="min-h-0 flex-1 overflow-auto p-2.5"
    >
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
              v-if="card.orderRows.length > 0"
              :data="card.orderRows"
              class="order-table order-table--horizontal-only"
              stripe
              :row-key="(row) => resolveOrderRowKey(row, card.encounterId)"
            >
              <ElTableColumn align="center" label="类型" min-width="108">
                <template #default="{ row }">
                  {{ resolveTherapyTypeLabel(row) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="医嘱内容" min-width="360">
                <template #default="{ row }">
                  <AdviceOrderDisplayCell
                    :row-key="resolveOrderRowKey(row, card.encounterId)"
                    :content="buildOrderContentTitle(row)"
                    :dosage-text="resolveOrderDosageText(row)"
                    :extension="buildOrderExtensionLine(row) || undefined"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn align="center" prop="locationName" label="取药药房" min-width="110" />
              <ElTableColumn label="领药时间" min-width="220">
                <template #default="{ row }">
                  <div class="execute-schedule-list">
                    <div
                      v-for="dayGroup in groupTimePointsForDisplay(
                        card.encounterId,
                        row.timePoints,
                      )"
                      :key="`${resolveOrderRowKey(row, card.encounterId)}-${dayGroup.dateLabel}`"
                      class="execute-schedule-row"
                    >
                      <span class="execute-schedule-date">{{ dayGroup.dateLabel }}</span>
                      <div class="execute-schedule-slots">
                        <ElCheckbox
                          v-for="slot in dayGroup.slots"
                          :key="slot.slotKey"
                          :model-value="isSlotSelected(slot)"
                          border
                          size="small"
                          @update:model-value="(v: boolean) => toggleSlotSelection(slot, v)"
                        >
                          <span>{{ formatDispenseSlotLabel(slot) }}</span>
                        </ElCheckbox>
                      </div>
                    </div>
                  </div>
                </template>
              </ElTableColumn>
            </ElTable>
            <div v-else class="py-6 text-center text-sm text-[var(--el-text-color-secondary)]">
              当前筛选下暂无待汇总明细
            </div>
          </div>
        </ElCollapseItem>
      </ElCollapse>
    </section>
    <section v-else-if="activeDisplayMode === 'summary'" class="min-h-0 flex-1 overflow-auto p-2.5">
      <div
        v-if="summarySlipRows.length > 0"
        class="patient-order-card overflow-hidden rounded-lg border border-solid border-[var(--el-border-color-lighter)] bg-[var(--el-bg-color)]"
      >
        <div class="card-table-wrap">
          <ElTable
            :data="summarySlipRows"
            class="order-table order-table--horizontal-only w-full"
            stripe
            row-key="busNo"
            @selection-change="handleSummarySelectionChange"
          >
            <ElTableColumn
              type="selection"
              width="48"
              :selectable="(row) => isPendingSummarySlip(row)"
            />
            <ElTableColumn prop="busNo" label="单据号" min-width="170" show-overflow-tooltip />
            <ElTableColumn
              prop="purposeLocationName"
              label="汇总药房"
              min-width="120"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.purposeLocationName?.trim() || '—' }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="applicantName" label="申请人" min-width="100">
              <template #default="{ row }">
                {{ row.applicantName || '—' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="状态" min-width="100">
              <template #default="{ row }">
                {{ resolveSlipStatusText(row) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="申请时间" min-width="165">
              <template #default="{ row }">
                {{ formatSummarySlipDateTime(row.applyTime) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="发药时间" min-width="165">
              <template #default="{ row }">
                {{ formatSummarySlipDateTime(row.dispenseTime) }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="receiverName" label="领药人" min-width="100">
              <template #default="{ row }">
                {{ row.receiverName || '—' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="160" align="center">
              <template #default="{ row }">
                <ElButton link type="primary" @click="handleViewSummarySheet(row)">详情</ElButton>
                <ElButton
                  link
                  type="primary"
                  :disabled="!isPendingSummarySlip(row)"
                  @click="handleSubmitSingleSummarySheet(row)"
                >
                  发送
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </div>
      <div v-else class="flex min-h-[200px] items-center justify-center">
        <ElEmpty description="暂无汇总单" />
      </div>
    </section>
    <section v-else class="flex min-h-0 min-w-0 flex-1 items-center justify-center bg-[#eef9fd]">
      <ElEmpty description="请先选择患者" />
    </section>

    <DispenseSummaryDetailDialog
      v-model:visible="detailDialogVisible"
      :header="detailDialogHeader"
      :lines="detailDialogLines"
      :loading="detailDialogLoading"
    />
  </div>
</template>

<style scoped lang="scss">
@use '../components/nurse-station-patient-order-list.scss';

.dispense-datetime {
  width: 220px;
  max-width: 100%;
}

.dispense-time-type-radio :deep(.el-radio) {
  margin-right: 0;
  height: auto;
  align-items: center;
}
</style>
