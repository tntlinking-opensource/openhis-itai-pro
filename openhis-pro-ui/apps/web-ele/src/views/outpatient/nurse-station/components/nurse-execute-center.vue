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

const executeRowClassName = ({ row }: { row: NurseAdviceExecuteRow; rowIndex: number }) => {
  const parts: string[] = [];
  if (row.status === '已完成') {
    parts.push('is-nurse-execute-done-row');
  }
  if (isNurseExecuteMedicationRow(row)) {
    parts.push('is-nurse-execute-med-display-only-row');
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
          <template #default="{ row }">
            <div class="execute-order-content-wrap">
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
</style>
