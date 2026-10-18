<script setup lang="ts">
import type {
  MedicalTechExecutingOrgOptionDto,
  MedicalTechExecutionLineDto,
} from '#/api/medical-tech/medical-tech-execution.types';
import type { SupplyItem } from '#/components/outpatient/supply-item-card.vue';
import type { TagProps } from 'element-plus';

import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import {
  ElAlert,
  ElButton,
  ElEmpty,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import SupplyItemCard from '#/components/outpatient/supply-item-card.vue';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE,
  isEncounterClinicalOrderMutationBlocked,
} from '#/utils/inpatient/encounter-clinical-order-gate';
import { PRINT_SCENARIO } from '#/utils/print/constants/print-scenario';
import { SERVICE_CATEGORY_PROOF } from '#/utils/print/constants/service-request-category-codes';
import { isBloodCollectionFeePrintItem } from '#/utils/print/nurse-station-print-targets';
import {
  clusterRowsBySpecimenMerge,
  groupPrintPendingSpecimenBarcodeRows,
  resolveSpecimenBarcodeMergeClusterKey,
  specimenMergeOperateRowSpan,
} from '#/utils/print/nurse-station-print-targets';
import { printSpecimenBarcode } from '#/utils/print/print-nurse-station';
import { buildServiceApplyFormExtensionText } from '#/utils/service-apply-display';

import {
  collectDefaultSelectedServiceLines,
  isMedicalTechServiceRowSelectable,
  resolveMedicalTechNetExecutedCount,
} from '../medical-tech-execution-selection';
import { medicalTechWorkstationContextKey } from '../workbench-tab.config';

defineOptions({ name: 'MedicalTechExecutionPanel' });

interface Props {
  /** 注册清空勾选回调（壳层底栏「一键清空」） */
  registerClearTableSelection?: (fn: (() => void) | null) => void;
  /** 已勾选耗材行主键 */
  selectedSupplyIds?: string[];
  /** 诊疗执行行 */
  serviceLines?: MedicalTechExecutionLineDto[];
  /** 共看组项目类型筛选项（CT/DR 等，按执行科室区分；空则不展示） */
  sharedExecutingOrgFilterOptions?: MedicalTechExecutingOrgOptionDto[];
  sheetLoading?: boolean;
  /** 耗材卡片（门诊可勾选执行；住院只读展示开立带出/发放行） */
  supplyItems?: SupplyItem[];
}

const props = withDefaults(defineProps<Props>(), {
  registerClearTableSelection: undefined,
  selectedSupplyIds: () => [],
  serviceLines: () => [],
  sharedExecutingOrgFilterOptions: () => [],
  sheetLoading: false,
  supplyItems: () => [],
});

/** 项目类型筛（CT/DR，按执行科室区分）：空=全部 */
const projectTypeFilterOrgId = ref('');

/** 是否展示项目类型筛（共看组，如 CT/DR） */
const showProjectTypeFilter = computed(
  () => (props.sharedExecutingOrgFilterOptions?.length ?? 0) > 0,
);

/**
 * 共看组科室名 → 项目类型文案（CT室→CT，DR室→DR；其它去「室」后缀）。
 *
 * @param organizationName 执行科室名称（仅展示；筛匹配用 organizationId）
 * @param fallbackId 无名称时回落筛值
 */
function resolveProjectTypeLabel(organizationName: null | string | undefined, fallbackId: string): string {
  const name = String(organizationName ?? '').trim();
  // 无名称 → 回落主键字面量，避免下拉空白
  if (!name) {
    return fallbackId;
  }
  const ctDr = name.match(/^(CT|DR|MR|US|ECG)/i);
  // 影像缩写前缀优先（文案「项目类型」）
  if (ctDr?.[1]) {
    return ctDr[1].toUpperCase();
  }
  return name.replace(/室$/u, '').trim() || name;
}

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

/**
 * 住院待结算/已结算/已离院：黄条提示并配合底栏禁用（对齐护士站）。
 */
const hasEncounterClinicalOrderBlocked = computed(() => {
  if (encounterScope.value !== 'INPATIENT') {
    return false;
  }
  return isEncounterClinicalOrderMutationBlocked(selectedPatientSummary.value?.statusEnum);
});

/** 是否可打采血贴的检验行（类目 22，排除合管派生采血费） */
function isLabLine(row: MedicalTechExecutionLineDto): boolean {
  if (String(row.serviceCategoryCode ?? '').trim() !== SERVICE_CATEGORY_PROOF) {
    return false;
  }
  // 采血费只计费，操作列不展示采血贴
  if (isBloodCollectionFeePrintItem(row.itemName)) {
    return false;
  }
  return true;
}

/** 检验行合管聚类键；非检验不参与合并；拆急院点急项视为无合管 */
function labMergeId(row: MedicalTechExecutionLineDto): string {
  if (!isLabLine(row)) {
    return '';
  }
  return resolveSpecimenBarcodeMergeClusterKey(
    row.specimenTubeMergeDeviceId,
    row.specimenTubeMergeGroupNo,
    row.priorityEnum,
  );
}

/**
 * 按项目类型（执行科室 orgId）筛诊疗行后再合管聚类。
 * 空筛=全部；只筛项目列表，不筛患者队列。
 */
const filteredServiceLines = computed(() => {
  const rows = props.serviceLines ?? [];
  const filterId = projectTypeFilterOrgId.value.trim();
  // 未选项目类型 → 全量
  if (!filterId) {
    return rows;
  }
  return rows.filter((row) => preserveApiIdForRequest(row.executingOrgId) === filterId);
});

/** 诊疗表展示序：检验合管行收到首行后面，便于操作列合并 */
const displayLines = computed(() =>
  clusterRowsBySpecimenMerge(filteredServiceLines.value, labMergeId),
);

/** 共看筛选项变化时：若当前筛值已不在选项中则回全部 */
watch(
  () => props.sharedExecutingOrgFilterOptions,
  (options) => {
    const current = projectTypeFilterOrgId.value.trim();
    if (!current) {
      return;
    }
    const stillValid = (options ?? []).some(
      (item) => preserveApiIdForRequest(item.organizationId) === current,
    );
    if (!stillValid) {
      projectTypeFilterOrgId.value = '';
    }
  },
);

/** 项目副行：部位/样本 + 备注（与医生站/护士站申请单 extension 同口径） */
function resolveServiceLineExtension(row: MedicalTechExecutionLineDto): string {
  return buildServiceApplyFormExtensionText({
    remark: row.remark,
    serviceRequestDetails: row.serviceRequestDetails,
  });
}

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
      busNo: String(item.busNo ?? '').trim() || undefined,
      title: item.itemName ?? '',
      // 传原始合管设备；拆急由 groupPrintPendingSpecimenBarcodeRows 按 priorityEnum 处理
      specimenTubeMergeDeviceId:
        preserveApiIdForRequest(item.specimenTubeMergeDeviceId) ?? undefined,
      specimenTubeMergeGroupNo:
        item.specimenTubeMergeGroupNo === null || item.specimenTubeMergeGroupNo === undefined
          ? undefined
          : item.specimenTubeMergeGroupNo,
      encounterId,
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
      barcode: target.barcode,
      itemName: target.itemName,
      labelCount: target.labelCount,
      priorityEnum: target.priorityEnum,
      memberServiceRequestIds: target.memberServiceRequestIds,
    })),
  });
}

/** 已选患者即展示耗材区：门诊可勾选执行；住院只读（开立带出 + 已有发放行） */
const showSupplySection = computed(() => Boolean(selectedPatientSummary.value));

/** 住院耗材只读，不参与勾选/取消执行 */
const supplySelectable = computed(() => encounterScope.value !== 'INPATIENT');

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

/** 诊疗行是否可勾选（尚可执行或净剩可取消；待结算轨整表不可勾） */
function serviceRowSelectable(row: MedicalTechExecutionLineDto) {
  if (hasEncounterClinicalOrderBlocked.value) {
    return false;
  }
  return isMedicalTechServiceRowSelectable(row);
}

/**
 * 按执行态默认勾选：优先待执行；无待执行时勾净剩可取消行（满额执行后仍可取消）。
 */
async function selectDefaultRowsByStatus() {
  await nextTick();
  const table = serviceTableRef.value;
  if (!table) {
    return;
  }
  table.clearSelection();
  // 默认勾选仅针对当前筛后可见行（共看按项目类型筛时）
  const defaults = collectDefaultSelectedServiceLines(filteredServiceLines.value);
  for (const row of defaults) {
    table.toggleRowSelection(row, true);
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

/** 执行次数展示：对齐护士站「净已执行/计划」，如 1/1、0/2 */
function formatExecutionCountPair(row: MedicalTechExecutionLineDto): string {
  const planned = row.plannedExecutionCount;
  const net = resolveMedicalTechNetExecutedCount(row);
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
  void selectDefaultRowsByStatus();
});

onBeforeUnmount(() => {
  props.registerClearTableSelection?.(null);
});

// 执行单行刷新或项目类型筛变更后按状态默认勾选
watch(
  [() => props.serviceLines, projectTypeFilterOrgId],
  () => {
    void selectDefaultRowsByStatus();
  },
);
</script>

<template>
  <div class="flex h-full min-h-0 min-w-0 flex-col">
    <ElAlert
      v-if="hasEncounterClinicalOrderBlocked"
      class="mx-3 mt-2 shrink-0"
      type="warning"
      :closable="false"
      show-icon
      :title="`${ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE}（不可执行或取消执行）`"
    />
    <div
      class="bg-background flex shrink-0 flex-nowrap items-center justify-between gap-3 px-3 pb-1.5 pt-2"
    >
      <div class="shrink-0 whitespace-nowrap">
        <SectionTitle title="诊疗项目" />
      </div>
      <div v-if="showProjectTypeFilter" class="w-[120px] shrink-0">
        <ElSelect
          v-model="projectTypeFilterOrgId"
          clearable
          placeholder="项目类型"
          class="w-full"
        >
          <ElOption label="全部" value="" />
          <ElOption
            v-for="opt in sharedExecutingOrgFilterOptions"
            :key="opt.organizationId"
            :label="
              resolveProjectTypeLabel(
                opt.organizationName,
                preserveApiIdForRequest(opt.organizationId) || '',
              )
            "
            :value="preserveApiIdForRequest(opt.organizationId) || ''"
          />
        </ElSelect>
      </div>
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
        <ElTableColumn label="项目" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="leading-5">
              <div>{{ row.itemName?.trim() || '—' }}</div>
              <div
                v-if="resolveServiceLineExtension(row)"
                class="text-text-sub mt-0.5 text-xs"
              >
                {{ resolveServiceLineExtension(row) }}
              </div>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn
          align="center"
          label="开方医生"
          prop="requesterName"
          min-width="96"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.requesterName?.trim() || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          align="center"
          label="开方科室"
          prop="requestingOrgName"
          min-width="110"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.requestingOrgName?.trim() || '—' }}
          </template>
        </ElTableColumn>
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
      class="bg-background flex max-h-[48%] min-h-0 shrink-0 flex-col overflow-hidden border-t border-[var(--el-border-color-lighter)] px-3 py-2"
    >
      <SectionTitle title="耗材明细" />
      <div v-if="supplyItems.length === 0" class="flex min-h-[88px] items-center justify-center">
        <ElEmpty description="暂无耗材明细" :image-size="64" />
      </div>
      <div v-else class="mt-supply-list mt-2 min-h-0 overflow-y-auto pr-0.5">
        <SupplyItemCard
          v-for="item in supplyItems"
          :key="item.id"
          :item="item"
          :selectable="supplySelectable"
          :selected="supplySelectable && selectedSupplyIds.includes(item.id)"
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
