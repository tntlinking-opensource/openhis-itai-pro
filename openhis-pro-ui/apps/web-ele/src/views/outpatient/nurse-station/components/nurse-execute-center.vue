<script setup lang="ts">
import type { NurseAdviceExecuteRow, NursePatient } from './types';

import type { PatientInfoBarPayload } from '#/components/outpatient/patient-card.types';

import { computed, nextTick, ref } from 'vue';

import { ElButton, ElTable, ElTableColumn, ElTag } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';
import PatientInfoBar from '#/components/outpatient/patient-info-bar.vue';
import {
  isNurseExecuteRowCompleted,
  resolveNurseExecuteStatusTagType,
} from '#/utils/nurse-station/nurse-execute-status';
import { SPECIMEN_BARCODE_SPLIT_URGENT_FROM_MERGE } from '#/utils/print/constants/specimen-barcode-merge.config';
import {
  clusterRowsBySpecimenMerge,
  isLabSpecimenExecuteRow,
  resolveSpecimenBarcodeMergeClusterKey,
  specimenMergeOperateRowSpan,
} from '#/utils/print/nurse-station-print-targets';

import { isNurseExecuteMedicationRow } from '../services/map-nurse-station-api';

defineOptions({
  name: 'NurseExecuteCenter',
});

const props = defineProps<Props>();

const emit = defineEmits<{
  cancelExecute: [row: NurseAdviceExecuteRow];
  execute: [];
  /** 输液药品行：单独打印本组瓶贴 */
  printInfusionLabel: [row: NurseAdviceExecuteRow];
  /** 医技列表：打印所选检验行（采血贴） */
  printLabRows: [rows: NurseAdviceExecuteRow[]];
  selectionChange: [keys: string[]];
}>();

interface Props {
  executeItems: NurseAdviceExecuteRow[];
  executeTableLoading: boolean;
  currentPatient: null | NursePatient;
  /** 与门诊患者信息条字段一致 */
  patient: PatientInfoBarPayload;
  doctorName: string;
  canExecuteSelected: boolean;
}

const executeTableRef = ref<InstanceType<typeof ElTable> | null>(null);

/** 院点拆急：合管内加急项单独出贴 */
const splitUrgentFromMerge = SPECIMEN_BARCODE_SPLIT_URGENT_FROM_MERGE;

/** 医技列表行的合管聚类键 merge:deviceId:groupNo（拆急院点：急项视为无合管） */
function labMergeId(row: NurseAdviceExecuteRow): string {
  return resolveSpecimenBarcodeMergeClusterKey(
    row.specimenTubeMergeDeviceId,
    row.specimenTubeMergeGroupNo,
    row.priorityEnum,
  );
}

/**
 * 上方「项目列表」待执行行：排除检验（类目 22）。
 * 检验仅出现在下方「医技列表」供采血贴，不可勾选执行。
 */
const pendingExecuteItems = computed(() =>
  props.executeItems.filter((row) => !isLabSpecimenExecuteRow(row)),
);

/** 医技列表：检验行按合管设备聚组，供操作列合并 */
const labPrintItems = computed(() =>
  clusterRowsBySpecimenMerge(
    props.executeItems.filter((row) => isLabSpecimenExecuteRow(row)),
    labMergeId,
  ),
);

/** 医技列表操作列：合管组只在首行留按钮 */
function labOperateSpanMethod(data: { column: { label?: string }; rowIndex: number }) {
  if (data.column.label !== '操作') {
    return { rowspan: 1, colspan: 1 };
  }
  const span = specimenMergeOperateRowSpan(labPrintItems.value, data.rowIndex, labMergeId);
  if (span <= 0) {
    return { rowspan: 0, colspan: 0 };
  }
  return { rowspan: span, colspan: 1 };
}

const getStatusTagType = (row: NurseAdviceExecuteRow) => {
  const code = row.statusEnum;
  if (code) {
    return resolveNurseExecuteStatusTagType(code);
  }
  return 'info';
};

const executeOrderDisplayedContent = (item: NurseAdviceExecuteRow) => {
  const c = item.content?.trim();
  return c && c.length > 0 ? item.content : item.name;
};

/** 打印本组采血贴：合管把同设备行一起送进原分组（一组一张、名称拼接） */
function onPrintLabRow(row: NurseAdviceExecuteRow) {
  const mergeId = labMergeId(row);
  const rows = mergeId ? labPrintItems.value.filter((item) => labMergeId(item) === mergeId) : [row];
  emit('printLabRows', rows);
}

const onSelectionChange = (selection: NurseAdviceExecuteRow[]) => {
  const keys = selection.map((row) => row.key).filter((k) => k.length > 0);
  emit('selectionChange', keys);
};

/** 药品/检验仅展示或不在本表；不参与勾选与执行 */
const executeRowSelectable = (row: NurseAdviceExecuteRow) =>
  !isLabSpecimenExecuteRow(row) &&
  !isNurseExecuteMedicationRow(row) &&
  !(row.statusEnum && isNurseExecuteRowCompleted(row.statusEnum));

/** 输液/注射药品行可打单组瓶贴 */
const canPrintInfusionLabelRow = (row: NurseAdviceExecuteRow) =>
  isNurseExecuteMedicationRow(row) && row.infusionFlag === true;

/** 同一申请单首行：用于组间分隔线（相对上方待执行表，已不含检验） */
const isApplyFormGroupStart = (row: NurseAdviceExecuteRow, rowIndex: number): boolean => {
  const formNo = row.applyFormNo?.trim();
  if (!formNo) {
    return false;
  }
  if (rowIndex <= 0) {
    return true;
  }
  const previous = pendingExecuteItems.value[rowIndex - 1];
  return previous?.applyFormNo?.trim() !== formNo;
};

/** 同一申请单末行：用于闭合左侧聚组竖线 */
const isApplyFormGroupEnd = (row: NurseAdviceExecuteRow, rowIndex: number): boolean => {
  const formNo = row.applyFormNo?.trim();
  if (!formNo) {
    return false;
  }
  const next = pendingExecuteItems.value[rowIndex + 1];
  return next?.applyFormNo?.trim() !== formNo;
};

/** 同一申请单续行（非首行）：用于去掉行间横线以衔接左侧竖线 */
const isApplyFormGroupContinue = (row: NurseAdviceExecuteRow, rowIndex: number): boolean => {
  const formNo = row.applyFormNo?.trim();
  if (!formNo || rowIndex <= 0) {
    return false;
  }
  return pendingExecuteItems.value[rowIndex - 1]?.applyFormNo?.trim() === formNo;
};

const executeRowClassName = ({
  row,
  rowIndex,
}: {
  row: NurseAdviceExecuteRow;
  rowIndex: number;
}) => {
  const parts: string[] = [];
  if (row.statusEnum && isNurseExecuteRowCompleted(row.statusEnum)) {
    parts.push('is-nurse-execute-done-row');
  }
  if (isNurseExecuteMedicationRow(row)) {
    parts.push('is-nurse-execute-med-display-only-row');
  }
  if (row.applyFormNo?.trim()) {
    parts.push('is-nurse-apply-form-member');
  }
  if (isApplyFormGroupStart(row, rowIndex)) {
    parts.push('is-nurse-apply-form-group-start');
  }
  if (isApplyFormGroupEnd(row, rowIndex)) {
    parts.push('is-nurse-apply-form-group-end');
  }
  if (isApplyFormGroupContinue(row, rowIndex)) {
    parts.push('is-nurse-apply-form-group-continue');
  }
  return parts.join(' ');
};

/** 申请单聚组：全表 cell-class-name（四边框住整组行） */
const APPLY_FORM_TABLE_EDGE_LEFT_INDEX = 0;
const APPLY_FORM_TABLE_EDGE_RIGHT_INDEX = 5;

const executeTableCellClassName = ({
  row,
  rowIndex,
  column,
  columnIndex,
}: {
  column?: { fixed?: boolean | string; property?: string };
  columnIndex: number;
  row: NurseAdviceExecuteRow;
  rowIndex: number;
}) => {
  if (!row?.applyFormNo?.trim()) {
    return '';
  }
  const parts = ['nurse-apply-form-cell'];
  if (isApplyFormGroupStart(row, rowIndex)) {
    parts.push('is-group-start');
  }
  if (isApplyFormGroupEnd(row, rowIndex)) {
    parts.push('is-group-end');
  }
  if (isApplyFormGroupContinue(row, rowIndex)) {
    parts.push('is-group-continue');
  }
  if (columnIndex === APPLY_FORM_TABLE_EDGE_LEFT_INDEX) {
    parts.push('is-group-edge-left');
  }
  const columnFixed = String(column?.fixed ?? '').trim();
  if (columnIndex === APPLY_FORM_TABLE_EDGE_RIGHT_INDEX || columnFixed === 'right') {
    parts.push('is-group-edge-right');
  }
  return parts.join(' ');
};

async function clearTableSelection() {
  await nextTick();
  executeTableRef.value?.clearSelection();
}

/** 与 `executeRowSelectable` 一致：仅勾选上方待执行表中可执行的非药品、非检验行 */
async function setTableSelectionForKeys(keys: string[]) {
  await nextTick();
  const table = executeTableRef.value;
  if (!table) {
    return;
  }
  table.clearSelection();
  const keySet = new Set(keys);
  for (const row of pendingExecuteItems.value) {
    if (keySet.has(row.key) && executeRowSelectable(row)) {
      table.toggleRowSelection(row, true);
    }
  }
}

defineExpose({
  clearTableSelection,
  setTableSelectionForKeys,
});
</script>

<template>
  <div class="flex h-full min-h-0 min-w-0 flex-1 flex-col">
    <div class="bg-background flex flex-col gap-2 px-2 pb-1.5 pt-2">
      <SectionTitle title="项目列表" />
      <div class="flex min-w-0 flex-wrap items-center justify-between gap-2">
        <div class="min-w-0 flex-1">
          <PatientInfoBar v-if="currentPatient" :patient="patient" :doctor-name="doctorName" />
          <div v-else class="text-text-sub py-2 text-sm">请选择患者</div>
        </div>
        <ElButton
          type="primary"
          size="default"
          class="execute-primary-btn shrink-0"
          :disabled="!canExecuteSelected"
          @click="emit('execute')"
        >
          执行并记录
        </ElButton>
      </div>
    </div>

    <div
      class="execute-el-table-wrap nurse-execute-advice-table bg-background flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden px-2 pb-1.5 pt-0 sm:px-2.5 sm:pb-2 sm:pt-0.5"
    >
      <ElTable
        ref="executeTableRef"
        v-loading="executeTableLoading"
        :data="pendingExecuteItems"
        row-key="key"
        :row-class-name="executeRowClassName"
        :cell-class-name="executeTableCellClassName"
        border
        stripe
        height="100%"
        class="advice-table"
        @selection-change="onSelectionChange"
      >
        <ElTableColumn
          type="selection"
          width="42"
          align="center"
          :selectable="executeRowSelectable"
        />
        <ElTableColumn prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <template v-if="!isNurseExecuteMedicationRow(row)">
              <ElTag size="small" effect="light" :type="getStatusTagType(row)">
                {{ row.status }}
              </ElTag>
            </template>
            <template v-else>—</template>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="content" label="医嘱内容" min-width="420">
          <template #default="{ row, $index }">
            <div
              class="execute-order-content-wrap"
              :class="{ 'is-apply-form-block': Boolean(row.applyFormNo?.trim()) }"
            >
              <div v-if="isApplyFormGroupStart(row, $index)" class="nurse-apply-form-group-label">
                <ElTag size="small" effect="plain" type="primary">
                  申请单 {{ row.applyFormNo }}
                </ElTag>
              </div>
              <AdviceOrderDisplayCell
                :row-key="row.key"
                :group-items="row.groupItems"
                :content="executeOrderDisplayedContent(row)"
                :dosage-text="row.dosageText"
                :extension="row.extension"
              />
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="已执行次数" width="100" align="center">
          <template #default="{ row }">
            <template v-if="!isNurseExecuteMedicationRow(row)">
              {{ row.executedCountDisplay ?? '—' }}
            </template>
            <template v-else>—</template>
          </template>
        </ElTableColumn>
        <ElTableColumn label="执行人" width="130" align="center">
          <template #default="{ row }">
            {{ row.performerDisplay ?? '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <div class="flex flex-wrap items-center justify-center gap-1">
              <ElButton
                v-if="canPrintInfusionLabelRow(row)"
                type="primary"
                link
                size="small"
                @click="emit('printInfusionLabel', row)"
              >
                打印
              </ElButton>
              <ElButton
                v-if="row.canCancelExecute"
                type="danger"
                link
                size="small"
                @click="emit('cancelExecute', row)"
              >
                取消执行
              </ElButton>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>

    <div
      class="bg-background flex max-h-[40%] min-h-0 shrink-0 flex-col overflow-hidden border-t border-[var(--el-border-color-lighter)] px-2 py-2"
    >
      <div class="mb-1">
        <SectionTitle :title="`医技列表（${labPrintItems.length}）`" />
      </div>
      <p class="mb-1.5 text-xs leading-snug text-[var(--el-text-color-regular)]">
        仅检验项目。合管一组一张，项目名拼在一起，张数取组内最大。{{
          splitUrgentFromMerge ? '加急项单独出贴。' : ''
        }}
      </p>
      <div
        v-if="labPrintItems.length === 0"
        class="text-text-sub flex min-h-[72px] items-center justify-center text-sm"
      >
        暂无检验项目
      </div>
      <ElTable
        v-else
        :data="labPrintItems"
        border
        stripe
        max-height="220"
        class="nurse-lab-print-table min-h-0"
        row-key="key"
        :span-method="labOperateSpanMethod"
      >
        <ElTableColumn label="检验项目" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ executeOrderDisplayedContent(row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="申请单号" min-width="140" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.applyFormNo || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90" align="center">
          <template #default="{ row }">
            <ElTag size="small" effect="light" :type="getStatusTagType(row)">
              {{ row.status }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="88" align="center">
          <template #default="{ row, $index }">
            <ElButton
              v-if="specimenMergeOperateRowSpan(labPrintItems, $index, labMergeId) > 0"
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
  </div>
</template>

<style scoped>
/* 与医生站医嘱 `advice/index.vue` 中 `.advice-table` 表头一致（Element Plus） */
.nurse-execute-advice-table :deep(.el-table__header-wrapper th.el-table__cell),
.nurse-lab-print-table :deep(.el-table__header-wrapper th.el-table__cell) {
  background: #eaf4ff !important;
}

/* 药品行不展示勾选框（仅参考展示） */
.nurse-execute-advice-table
  :deep(
    .el-table__body
      tr.is-nurse-execute-med-display-only-row
      td.el-table-column--selection
      .el-checkbox
  ) {
  display: none !important;
}

/* 同一申请单多行：浅底 + 四边框成一组；组间顶部分隔；组内无横线 */
.nurse-execute-advice-table :deep(td.nurse-apply-form-cell) {
  background-color: var(--el-color-primary-light-9) !important;
}

.nurse-execute-advice-table :deep(td.nurse-apply-form-cell.is-group-edge-left) {
  border-left: 2px solid var(--el-color-primary-light-5) !important;
}

.nurse-execute-advice-table :deep(td.nurse-apply-form-cell.is-group-edge-right) {
  border-right: 2px solid var(--el-color-primary-light-5) !important;
}

.nurse-execute-advice-table :deep(td.nurse-apply-form-cell.is-group-start) {
  border-top: 2px solid var(--el-color-primary-light-5) !important;
}

.nurse-execute-advice-table :deep(td.nurse-apply-form-cell.is-group-end) {
  border-bottom: 2px solid var(--el-color-primary-light-5) !important;
}

/* 组内续行：去掉行间横线，避免组内虚线/断框 */
.nurse-execute-advice-table :deep(td.nurse-apply-form-cell.is-group-continue) {
  border-top: none !important;
}

.nurse-execute-advice-table :deep(td.nurse-apply-form-cell:not(.is-group-end)) {
  border-bottom: none !important;
}

/* 组框圆角（仅四角单元格） */
.nurse-execute-advice-table :deep(td.nurse-apply-form-cell.is-group-start.is-group-edge-left) {
  border-top-left-radius: 4px;
}

.nurse-execute-advice-table :deep(td.nurse-apply-form-cell.is-group-start.is-group-edge-right) {
  border-top-right-radius: 4px;
}

.nurse-execute-advice-table :deep(td.nurse-apply-form-cell.is-group-end.is-group-edge-left) {
  border-bottom-left-radius: 4px;
}

.nurse-execute-advice-table :deep(td.nurse-apply-form-cell.is-group-end.is-group-edge-right) {
  border-bottom-right-radius: 4px;
}

.execute-order-content-wrap.is-apply-form-block {
  padding-left: 8px;
}

.nurse-apply-form-group-label {
  margin-bottom: 4px;
}
</style>
