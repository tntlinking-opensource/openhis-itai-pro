<script setup lang="ts">
import type { NurseAdviceExecuteRow, NursePatient } from './types';

import type { PatientInfoBarPayload } from '#/components/outpatient/patient-card.types';

import { nextTick, ref } from 'vue';

import { ElButton, ElTable, ElTableColumn, ElTag } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';
import PatientInfoBar from '#/components/outpatient/patient-info-bar.vue';

import { isNurseExecuteMedicationRow } from '../services/map-nurse-station-api';

defineOptions({
  name: 'NurseExecuteCenter',
});

const props = defineProps<Props>();

const emit = defineEmits<{
  cancelExecute: [row: NurseAdviceExecuteRow];
  execute: [];
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

const getStatusTagType = (status: NurseAdviceExecuteRow['status']) => {
  if (status === '待签发') {
    return 'warning';
  }
  if (status === '已执行') {
    return 'success';
  }
  if (status === '已收费') {
    return 'info';
  }
  if (status === '已退费') {
    return 'danger';
  }
  if (status === '已完成' || status === '已发放') {
    return 'success';
  }
  if (status === '执行中') {
    return 'warning';
  }
  if (status === '待执行') {
    return 'primary';
  }
  return 'info';
};

const executeOrderDisplayedContent = (item: NurseAdviceExecuteRow) => {
  const c = item.content?.trim();
  return c && c.length > 0 ? item.content : item.name;
};

const onSelectionChange = (selection: NurseAdviceExecuteRow[]) => {
  const keys = selection.map((row) => row.key).filter((k) => k.length > 0);
  emit('selectionChange', keys);
};

/** 药品行仅展示，不参与勾选与执行 */
const executeRowSelectable = (row: NurseAdviceExecuteRow) =>
  !isNurseExecuteMedicationRow(row) && row.status !== '已完成';

/** 同一申请单首行：用于组间分隔线 */
const isApplyFormGroupStart = (row: NurseAdviceExecuteRow, rowIndex: number): boolean => {
  const formNo = row.applyFormNo?.trim();
  if (!formNo) {
    return false;
  }
  if (rowIndex <= 0) {
    return true;
  }
  const previous = props.executeItems[rowIndex - 1];
  return previous?.applyFormNo?.trim() !== formNo;
};

/** 同一申请单末行：用于闭合左侧聚组竖线 */
const isApplyFormGroupEnd = (row: NurseAdviceExecuteRow, rowIndex: number): boolean => {
  const formNo = row.applyFormNo?.trim();
  if (!formNo) {
    return false;
  }
  const next = props.executeItems[rowIndex + 1];
  return next?.applyFormNo?.trim() !== formNo;
};

/** 同一申请单续行（非首行）：用于去掉行间横线以衔接左侧竖线 */
const isApplyFormGroupContinue = (row: NurseAdviceExecuteRow, rowIndex: number): boolean => {
  const formNo = row.applyFormNo?.trim();
  if (!formNo || rowIndex <= 0) {
    return false;
  }
  return props.executeItems[rowIndex - 1]?.applyFormNo?.trim() === formNo;
};

const executeRowClassName = ({
  row,
  rowIndex,
}: {
  row: NurseAdviceExecuteRow;
  rowIndex: number;
}) => {
  const parts: string[] = [];
  if (row.status === '已完成') {
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

/** 与 `executeRowSelectable` 一致：仅勾选可执行的非药品行 */
async function setTableSelectionForKeys(keys: string[]) {
  await nextTick();
  const table = executeTableRef.value;
  if (!table) {
    return;
  }
  table.clearSelection();
  const keySet = new Set(keys);
  for (const row of props.executeItems) {
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
        :data="executeItems"
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
              <ElTag size="small" effect="light" :type="getStatusTagType(row.status)">
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
        <ElTableColumn label="操作" width="96" align="center" fixed="right">
          <template #default="{ row }">
            <ElButton
              v-if="row.canCancelExecute"
              type="danger"
              link
              size="small"
              @click="emit('cancelExecute', row)"
            >
              取消执行
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
  </div>
</template>

<style scoped>
/* 与医生站医嘱 `advice/index.vue` 中 `.advice-table` 表头一致（Element Plus） */
.nurse-execute-advice-table :deep(.el-table__header-wrapper th.el-table__cell) {
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
