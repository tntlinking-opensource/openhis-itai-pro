<script setup lang="ts">
import type { MedicalTechExecutionLineDto } from '#/api/medical-tech/medical-tech-execution.types';
import type { SupplyItem } from '#/components/outpatient/supply-item-card.vue';
import type { TagProps } from 'element-plus';

import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { ElButton, ElEmpty, ElMessage, ElTable, ElTableColumn, ElTag } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import SupplyItemCard from '#/components/outpatient/supply-item-card.vue';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { PRINT_SCENARIO } from '#/utils/print/constants/print-scenario';
import { SERVICE_CATEGORY_PROOF } from '#/utils/print/constants/service-request-category-codes';
import {
  clusterRowsBySpecimenMerge,
  groupPrintPendingSpecimenBarcodeRows,
  resolveSpecimenBarcodeMergeDeviceId,
  specimenMergeOperateRowSpan,
} from '#/utils/print/nurse-station-print-targets';
import { printSpecimenBarcode } from '#/utils/print/print-nurse-station';

import { medicalTechWorkstationContextKey } from '../workbench-tab.config';

defineOptions({ name: 'MedicalTechExecutionPanel' });

interface Props {
  /** 注册清空勾选回调（壳层底栏「一键清空」） */
  registerClearTableSelection?: (fn: (() => void) | null) => void;
  /** 已勾选耗材行主键 */
  selectedSupplyIds?: string[];
  /** 诊疗执行行 */
  serviceLines?: MedicalTechExecutionLineDto[];
  sheetLoading?: boolean;
  /** 耗材卡片（仅门诊展示） */
  supplyItems?: SupplyItem[];
}

const props = withDefaults(defineProps<Props>(), {
  registerClearTableSelection: undefined,
  selectedSupplyIds: () => [],
  serviceLines: () => [],
  sheetLoading: false,
  supplyItems: () => [],
});

const emit = defineEmits<{
  cancelSupply: [id: string];
  selectionChange: [rows: MedicalTechExecutionLineDto[]];
  supplyChange: [id: string, checked: boolean];
}>();

const ctx = inject(medicalTechWorkstationContextKey);
if (!ctx) {
  throw new Error('MedicalTechExecutionPanel 须在 MedicalTechWorkstation 壳内使用');
}

const { selectedPatientSummary, encounterScope, selectedEncounterId } = ctx;

const serviceTableRef = ref<InstanceType<typeof ElTable> | null>(null);

/** 是否检验行（类目 22），只有检验行出采血贴 */
function isLabLine(row: MedicalTechExecutionLineDto): boolean {
  return String(row.serviceCategoryCode ?? '').trim() === SERVICE_CATEGORY_PROOF;
}

/** 检验行合管设备主键；非检验不参与合并；拆急院点急项视为无合管 */
function labMergeId(row: MedicalTechExecutionLineDto): string {
  if (!isLabLine(row)) {
    return '';
  }
  return resolveSpecimenBarcodeMergeDeviceId(row.specimenTubeMergeDeviceId, row.priorityEnum);
}

/** 诊疗表展示序：检验合管行收到首行后面，便于操作列合并 */
const displayLines = computed(() =>
  clusterRowsBySpecimenMerge(props.serviceLines ?? [], labMergeId),
);

/** 诊疗表操作列是否展示采血贴：仅检验，且合管只在组首行 */
function showLabPrintButton(row: MedicalTechExecutionLineDto, rowIndex: number): boolean {
  if (!isLabLine(row)) {
    return false;
  }
  return specimenMergeOperateRowSpan(displayLines.value, rowIndex, labMergeId) > 0;
}

/** 诊疗表操作列：合管组只在首行占位 */
function labOperateSpanMethod(data: { column: { label?: string }; rowIndex: number }) {
  if (data.column.label !== '操作') {
    return { rowspan: 1, colspan: 1 };
  }
  const span = specimenMergeOperateRowSpan(displayLines.value, data.rowIndex, labMergeId);
  if (span <= 0) {
    return { rowspan: 0, colspan: 0 };
  }
  return { rowspan: span, colspan: 1 };
}

/** 打印本组采血贴：合管一组一张，名称拼接，张数取组内计划次数最大 */
async function onPrintLabRow(row: MedicalTechExecutionLineDto) {
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const mergeId = labMergeId(row);
  const groupRows = mergeId
    ? displayLines.value.filter((item) => labMergeId(item) === mergeId)
    : [row];
  const targets = groupPrintPendingSpecimenBarcodeRows(
    groupRows.map((item) => ({
      orderId: preserveApiIdForRequest(item.requestId) ?? '',
      title: item.itemName ?? '',
      // 传原始合管设备；拆急由 groupPrintPendingSpecimenBarcodeRows 按 priorityEnum 处理
      specimenTubeMergeDeviceId:
        preserveApiIdForRequest(item.specimenTubeMergeDeviceId) ?? undefined,
      dosageDisplay:
        item.plannedExecutionCount != null && item.plannedExecutionCount > 0
          ? String(item.plannedExecutionCount)
          : undefined,
      priorityEnum: String(item.priorityEnum ?? '').trim() || undefined,
    })),
  );
  if (targets.length === 0) {
    ElMessage.warning('没有可打印的采血/检验医嘱');
    return;
  }
  const patient = selectedPatientSummary.value;
  const scenarioCode =
    encounterScope.value === 'INPATIENT'
      ? PRINT_SCENARIO.ipSpecimenBarcodePrint
      : PRINT_SCENARIO.opSpecimenBarcodePrint;
  await printSpecimenBarcode({
    encounterId,
    scenarioCode,
    patientName: patient?.patientName,
    genderEnumText: patient?.genderEnumText,
    ageText: patient?.ageText,
    bedNo: patient?.bedName,
    items: targets.map((target) => ({
      serviceRequestId: target.serviceRequestId,
      itemName: target.itemName,
      labelCount: target.labelCount,
      priorityEnum: target.priorityEnum,
    })),
  });
}

/** 住院耗材走医技计费，执行页只在门诊把耗材放在诊疗表下方 */
const showSupplySection = computed(
  () => encounterScope.value !== 'INPATIENT' && Boolean(selectedPatientSummary.value),
);

/** 耗材勾选变更 */
function onSupplyChange(id: string, checked: boolean) {
  emit('supplyChange', id, checked);
}

/** 单张耗材卡取消执行 */
function onCancelSupply(id: string) {
  emit('cancelSupply', id);
}

/** 清空诊疗表勾选 */
function clearTableSelection() {
  serviceTableRef.value?.clearSelection();
}

/** 诊疗行是否可勾选 */
function serviceRowSelectable(row: MedicalTechExecutionLineDto) {
  return row.selectable !== false;
}

/**
 * 默认全选当前可执行诊疗行（门诊/住院同口径；护士可再取消个别行）。
 */
async function selectAllSelectableRows() {
  await nextTick();
  const table = serviceTableRef.value;
  if (!table) {
    return;
  }
  table.clearSelection();
  for (const row of props.serviceLines ?? []) {
    if (serviceRowSelectable(row)) {
      table.toggleRowSelection(row, true);
    }
  }
}

/** 诊疗行多选变更 */
function handleServiceSelectionChange(rows: MedicalTechExecutionLineDto[]) {
  emit('selectionChange', rows);
}

/** 收费状态 Tag 类型 */
function resolveChargeStatusTagType(row: MedicalTechExecutionLineDto): TagProps['type'] {
  const code = String(row.chargeStatusEnum ?? '')
    .trim()
    .toUpperCase();
  if (code === 'BILLED' || code === 'SETTLED' || code === 'PAID') {
    return 'success';
  }
  if (code === 'REFUNDING') {
    return 'warning';
  }
  if (code === 'REFUNDED' || code === 'CANCELLED') {
    return 'info';
  }
  return 'primary';
}

/** 净已执行 = 完成 − 取消（与后端 selectable 同口径） */
function resolveNetExecutedCount(row: MedicalTechExecutionLineDto): number {
  const completed = row.completedCount ?? 0;
  const cancelled = row.cancelledCount ?? 0;
  const net = completed - cancelled;
  if (net < 0) {
    return 0;
  }
  return net;
}

/** 执行次数展示：对齐护士站「净已执行/计划」，如 1/1、0/2 */
function formatExecutionCountPair(row: MedicalTechExecutionLineDto): string {
  const planned = row.plannedExecutionCount;
  const net = resolveNetExecutedCount(row);
  if (planned !== null && planned !== undefined && planned > 0) {
    return `${net}/${planned}`;
  }
  return `${net}`;
}

/** 单价展示；无值显示 —（后端无账单时已填目录现价） */
function formatUnitPrice(row: MedicalTechExecutionLineDto): string {
  if (row.unitPrice === null || row.unitPrice === undefined || row.unitPrice === '') {
    return '—';
  }
  return formatMoneyDisplay(row.unitPrice);
}

/** 总价展示（账单合计或目录预览）；无值显示 — */
function formatChargeAmount(row: MedicalTechExecutionLineDto): string {
  if (row.chargeAmount === null || row.chargeAmount === undefined || row.chargeAmount === '') {
    return '—';
  }
  return formatMoneyDisplay(row.chargeAmount);
}

onMounted(() => {
  props.registerClearTableSelection?.(clearTableSelection);
  void selectAllSelectableRows();
});

onBeforeUnmount(() => {
  props.registerClearTableSelection?.(null);
});

// 执行单行刷新后默认全选可执行行（患者切换 / 执行后重载同路径）
watch(
  () => props.serviceLines,
  () => {
    void selectAllSelectableRows();
  },
);
</script>

<template>
  <div class="flex h-full min-h-0 min-w-0 flex-col">
    <div class="bg-background flex shrink-0 flex-col gap-2 px-3 pb-1.5 pt-2">
      <SectionTitle title="诊疗项目" />
    </div>
    <div
      v-loading="sheetLoading"
      class="execute-el-table-wrap bg-background flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden px-3 pb-2 pt-0"
    >
      <ElTable
        ref="serviceTableRef"
        :data="displayLines"
        row-key="requestId"
        border
        stripe
        height="100%"
        class="advice-table"
        :span-method="labOperateSpanMethod"
        @selection-change="handleServiceSelectionChange"
      >
        <ElTableColumn
          type="selection"
          width="42"
          align="center"
          :selectable="serviceRowSelectable"
        />
        <ElTableColumn label="项目" width="200" prop="itemName" show-overflow-tooltip />
        <ElTableColumn align="center" label="数量" prop="quantity" min-width="88" />
        <ElTableColumn
          align="center"
          label="单位"
          prop="unitName"
          min-width="88"
          show-overflow-tooltip
        />
        <ElTableColumn align="right" label="单价" min-width="100">
          <template #default="{ row }">
            {{ formatUnitPrice(row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn align="right" label="总价" min-width="100">
          <template #default="{ row }">
            {{ formatChargeAmount(row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn align="center" label="已执行/计划" min-width="120">
          <template #default="{ row }">
            {{ formatExecutionCountPair(row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn align="center" label="收费状态" min-width="120">
          <template #default="{ row }">
            <ElTag
              v-if="row.chargeStatusEnumText"
              size="small"
              effect="light"
              :type="resolveChargeStatusTagType(row)"
            >
              {{ row.chargeStatusEnumText }}
            </ElTag>
            <span v-else class="text-text-sub">—</span>
          </template>
        </ElTableColumn>
        <ElTableColumn align="center" label="操作" width="88">
          <template #default="{ row, $index }">
            <ElButton
              v-if="showLabPrintButton(row, $index)"
              type="primary"
              link
              size="small"
              @click="onPrintLabRow(row)"
            >
              采血贴
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
    <div
      v-if="showSupplySection"
      v-loading="sheetLoading"
      class="bg-background flex max-h-[42%] min-h-0 shrink-0 flex-col overflow-hidden border-t border-[var(--el-border-color-lighter)] px-3 py-2"
    >
      <SectionTitle title="耗材明细" />
      <p class="mt-0.5 text-[11px] leading-snug text-[var(--el-text-color-regular)]">
        勾选「待发放」随批执行；已发放可点「取消执行」回退。
      </p>
      <div v-if="supplyItems.length === 0" class="flex min-h-[88px] items-center justify-center">
        <ElEmpty description="暂无耗材明细" :image-size="64" />
      </div>
      <div v-else class="mt-supply-list mt-2 min-h-0 overflow-y-auto pr-0.5">
        <SupplyItemCard
          v-for="item in supplyItems"
          :key="item.id"
          :item="item"
          selectable
          :selected="selectedSupplyIds.includes(item.id)"
          @change="onSupplyChange"
          @cancel="onCancelSupply"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.execute-el-table-wrap :deep(.el-table__header-wrapper th.el-table__cell) {
  background: var(--el-fill-color) !important;
}

.execute-el-table-wrap :deep(.el-table__header-wrapper th.el-table__cell .cell) {
  white-space: nowrap;
}
</style>
