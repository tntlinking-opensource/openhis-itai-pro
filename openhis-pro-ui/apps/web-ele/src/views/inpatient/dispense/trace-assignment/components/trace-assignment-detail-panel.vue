<script setup lang="ts">
import type {
  TraceAssignmentDisplayRow,
  TraceAssignmentGroupDraftWithPatient,
  TraceAssignmentTableRow,
} from '../services/map-inpatient-trace-assignment';
import type { TraceAssignmentWorkspaceMode } from './trace-assignment-queue-panel.vue';

import { ElButton, ElEmpty, ElInput, ElTable, ElTableColumn, ElTag } from 'element-plus';

import TraceCodeTooltip from '#/components/trace/trace-code-tooltip.vue';

import {
  formatTraceAssignmentQuantity,
  isTraceAssignmentSpacerRow,
} from '../services/map-inpatient-trace-assignment';

defineOptions({
  name: 'TraceAssignmentDetailPanel',
});

const props = withDefaults(
  defineProps<{
    /** 展平后的分组表行（含间隔行） */
    rows: TraceAssignmentTableRow[];
    loading?: boolean;
    /** 焦点就诊：高亮整组 */
    focusEncounterId?: string;
    emptySelected?: boolean;
    /** 待分配 / 已分配改码 */
    workspaceMode?: TraceAssignmentWorkspaceMode;
  }>(),
  {
    loading: false,
    focusEncounterId: '',
    emptySelected: false,
    workspaceMode: 'assign',
  },
);

const emit = defineEmits<{
  focus: [encounterId: string];
  updateTraceNo: [row: TraceAssignmentGroupDraftWithPatient, value: string];
  /** 行内发起更正（仅改码模式） */
  revise: [row: TraceAssignmentDisplayRow];
}>();

/** 床号 + 患者 两列合并（对齐门诊发药元列合并） */
const PATIENT_META_COLS = 2;
/** 当前表总列数（含操作列；改码模式多 1 列，span 按满列算无妨） */
const TABLE_TOTAL_COLS = 11;

/** 单表 rowspan：床号/患者按患者合并，间隔行整行合并 */
function tableSpanMethod({
  row,
  columnIndex,
}: {
  columnIndex: number;
  row: TraceAssignmentTableRow;
}): [number, number] | undefined {
  if (isTraceAssignmentSpacerRow(row)) {
    if (columnIndex === 0) {
      return [1, TABLE_TOTAL_COLS];
    }
    return [0, 0];
  }
  if (columnIndex < PATIENT_META_COLS) {
    if (row.isFirstInPatient) {
      return [row.patientRowSpan, 1];
    }
    return [0, 0];
  }
  return undefined;
}

/** 行样式：组块圆角外框、焦点底、间隔行压平 */
function resolveRowClassName({ row }: { row: TraceAssignmentTableRow }): string {
  if (isTraceAssignmentSpacerRow(row)) {
    return 'trace-detail-gap-row';
  }
  const classes = ['trace-detail-body'];
  if (row.isFirstInPatient) {
    classes.push('trace-detail-body--first');
  }
  if (row.isLastInPatient) {
    classes.push('trace-detail-body--last');
  }
  if (row.encounterId && row.encounterId === props.focusEncounterId) {
    classes.push('trace-detail-body--selected');
  }
  return classes.join(' ');
}

/** 点击数据行切换焦点患者 */
function handleRowClick(row: TraceAssignmentTableRow) {
  if (isTraceAssignmentSpacerRow(row)) {
    return;
  }
  if (row.encounterId) {
    emit('focus', row.encounterId);
  }
}

/** 分配状态标签类型 */
function resolveAssignmentTagType(status?: string): 'info' | 'success' | 'warning' {
  if (status === 'ASSIGNED') {
    return 'success';
  }
  if (status === 'PENDING') {
    return 'warning';
  }
  return 'info';
}

/** 当前模式下该行追溯码是否可直接改输入框 */
function isTraceNoEditable(row: TraceAssignmentDisplayRow): boolean {
  const status = String(row.group.assignmentStatus ?? '');
  if (props.workspaceMode === 'revise') {
    // 改码模式：已分配行经「更正」弹窗改，主表只读避免误触
    return false;
  }
  return status === 'PENDING' || !String(row.traceNo ?? '').trim();
}

/** 更新追溯码输入 */
function handleTraceNoUpdate(row: TraceAssignmentDisplayRow, value: string) {
  emit('updateTraceNo', row, value);
}

/** 行内更正 */
function handleReviseClick(row: TraceAssignmentDisplayRow) {
  emit('revise', row);
}
</script>

<template>
  <div class="trace-detail-panel flex h-full min-h-0 flex-col">
    <template v-if="!emptySelected">
      <ElTable
        v-if="rows.length > 0 || loading"
        v-loading="loading"
        :data="rows"
        :span-method="tableSpanMethod"
        :row-class-name="resolveRowClassName"
        row-key="rowId"
        height="100%"
        class="trace-detail-grouped-table w-full min-w-0"
        @row-click="handleRowClick"
      >
        <ElTableColumn
          prop="bedName"
          label="床号"
          width="72"
          align="center"
          header-align="center"
        />
        <ElTableColumn
          prop="patientName"
          label="患者"
          min-width="88"
          align="center"
          header-align="center"
          show-overflow-tooltip
          class-name="trace-detail-patient-cell"
        />
        <ElTableColumn
          prop="group.summaryNo"
          label="汇总单号"
          min-width="120"
          align="center"
          header-align="center"
          show-overflow-tooltip
          class-name="trace-detail-first-detail-cell"
        />
        <ElTableColumn
          prop="group.medicationName"
          label="药品"
          min-width="140"
          align="center"
          header-align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="group.specification"
          label="规格"
          min-width="100"
          align="center"
          header-align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="group.lotNumber"
          label="批号"
          width="110"
          align="center"
          header-align="center"
          show-overflow-tooltip
        />
        <ElTableColumn label="发药量" width="100" align="center" header-align="center">
          <template #default="{ row }">
            <template v-if="!isTraceAssignmentSpacerRow(row)">
              {{ formatTraceAssignmentQuantity(row.group) }}
            </template>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="88" align="center" header-align="center">
          <template #default="{ row }">
            <template v-if="!isTraceAssignmentSpacerRow(row)">
              <ElTag
                :type="resolveAssignmentTagType(row.group.assignmentStatus)"
                size="small"
                effect="light"
              >
                {{ row.group.assignmentStatus_enumText ?? row.group.assignmentStatus ?? '—' }}
              </ElTag>
            </template>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="group.tracePoolPreview"
          label="码池摘要"
          min-width="120"
          align="center"
          header-align="center"
          show-overflow-tooltip
        />
        <ElTableColumn label="追溯码" min-width="220" align="center" header-align="center">
          <template #default="{ row }">
            <template v-if="!isTraceAssignmentSpacerRow(row)">
              <ElInput
                v-if="isTraceNoEditable(row)"
                :model-value="row.traceNo"
                clearable
                size="small"
                placeholder="扫码或手工录入（逗号分隔）"
                @click.stop
                @update:model-value="(v) => handleTraceNoUpdate(row, String(v ?? ''))"
              />
              <TraceCodeTooltip v-else :value="row.traceNo" />
            </template>
          </template>
        </ElTableColumn>
        <ElTableColumn
          v-if="workspaceMode === 'revise'"
          label="操作"
          width="88"
          align="center"
          header-align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <template v-if="!isTraceAssignmentSpacerRow(row)">
              <ElButton link type="warning" @click.stop="handleReviseClick(row)">更正</ElButton>
            </template>
          </template>
        </ElTableColumn>
      </ElTable>
      <div
        v-else
        class="flex flex-1 items-center justify-center py-12 text-sm text-[var(--el-text-color-secondary)]"
      >
        {{ workspaceMode === 'revise' ? '暂无已分配可更正的药品' : '暂无汇总核发后的待分配药品' }}
      </div>
    </template>
    <div v-else class="flex min-h-0 flex-1 items-center justify-center">
      <ElEmpty
        :description="
          workspaceMode === 'revise'
            ? '请在左侧勾选已分配患者进行改码'
            : '请在左侧勾选患者查看追溯码信息'
        "
      />
    </div>
  </div>
</template>

<style scoped>
/* 对齐门诊发药列表明细成组样式；外框用主题浅边框，避免黑色硬线 */
:deep(.trace-detail-grouped-table.el-table) {
  --trace-detail-group-outer-border: var(--el-border-color);
  --el-table-header-bg-color: hsl(var(--accent));
  --el-table-header-text-color: hsl(var(--accent-foreground));
  --el-table-text-color: hsl(var(--foreground));
  --el-table-bg-color: hsl(var(--background));
  --el-table-tr-bg-color: hsl(var(--background));
  --el-table-row-hover-bg-color: hsl(var(--accent-hover));
  --el-table-border-color: hsl(var(--border));
  --el-table-current-row-bg-color: hsl(var(--accent) / 35%);
  font-size: var(--el-font-size-base);
}

:deep(.trace-detail-grouped-table tr.trace-detail-gap-row) {
  height: 6px !important;
  max-height: 6px !important;
  line-height: 0;
}

:deep(.trace-detail-grouped-table tr.trace-detail-gap-row td.el-table__cell) {
  height: 6px !important;
  min-height: 0 !important;
  max-height: 6px !important;
  padding: 0 !important;
  border: none !important;
  background: transparent !important;
  line-height: 0;
  vertical-align: middle;
}

:deep(.trace-detail-grouped-table tr.trace-detail-gap-row td.el-table__cell .cell) {
  padding: 0 !important;
  min-height: 0 !important;
  max-height: 0 !important;
  line-height: 0 !important;
  font-size: 0 !important;
  overflow: hidden !important;
}

:deep(.trace-detail-grouped-table tr.trace-detail-body > td.el-table__cell) {
  background-color: hsl(var(--background));
  color: hsl(var(--foreground));
}

/* 表体列间短竖线：不含左侧合并两列（床号、患者） */
:deep(
  .trace-detail-grouped-table
    .el-table__body-wrapper
    .el-table__body
    tr.trace-detail-body--first
    > td.el-table__cell:nth-child(n + 3):not(:last-child)
) {
  position: relative;
}

:deep(
  .trace-detail-grouped-table
    .el-table__body-wrapper
    .el-table__body
    tr.trace-detail-body--first
    > td.el-table__cell:nth-child(n + 3):not(:last-child)::after
) {
  content: '';
  position: absolute;
  top: 50%;
  right: 0;
  z-index: 1;
  width: 1px;
  height: 16px;
  background-color: var(--el-border-color-lighter);
  transform: translateY(-50%);
  pointer-events: none;
}

:deep(
  .trace-detail-grouped-table
    .el-table__body-wrapper
    .el-table__body
    tr.trace-detail-body:not(.trace-detail-body--first)
    > td.el-table__cell:not(:last-child)
) {
  position: relative;
}

:deep(
  .trace-detail-grouped-table
    .el-table__body-wrapper
    .el-table__body
    tr.trace-detail-body:not(.trace-detail-body--first)
    > td.el-table__cell:not(:last-child)::after
) {
  content: '';
  position: absolute;
  top: 50%;
  right: 0;
  z-index: 1;
  width: 1px;
  height: 16px;
  background-color: var(--el-border-color-lighter);
  transform: translateY(-50%);
  pointer-events: none;
}

:deep(.trace-detail-grouped-table tr.trace-detail-body--first > td.el-table__cell) {
  border-top: 1px solid var(--trace-detail-group-outer-border);
}

:deep(.trace-detail-grouped-table tr.trace-detail-body--last > td.el-table__cell) {
  border-bottom: 1px solid var(--trace-detail-group-outer-border);
}

:deep(.trace-detail-grouped-table tr.trace-detail-body--first > td.el-table__cell:first-child) {
  border-left: 1px solid var(--trace-detail-group-outer-border);
  border-top-left-radius: 6px;
  border-bottom-left-radius: 6px;
}

:deep(.trace-detail-grouped-table tr.trace-detail-body--first > td.el-table__cell:nth-child(1)),
:deep(.trace-detail-grouped-table tr.trace-detail-body--first > td.el-table__cell:nth-child(2)) {
  border-bottom: 1px solid var(--trace-detail-group-outer-border);
}

:deep(.trace-detail-grouped-table td.trace-detail-first-detail-cell) {
  border-left: 1px solid hsl(var(--border));
}

:deep(.trace-detail-grouped-table tr.trace-detail-body > td.el-table__cell:last-child) {
  border-right: 1px solid var(--trace-detail-group-outer-border);
}

:deep(.trace-detail-grouped-table tr.trace-detail-body--first > td.el-table__cell:last-child) {
  border-top-right-radius: 6px;
}

:deep(.trace-detail-grouped-table tr.trace-detail-body--last > td.el-table__cell:last-child) {
  border-bottom-right-radius: 6px;
}

:deep(.trace-detail-grouped-table tr.trace-detail-body--selected > td.el-table__cell) {
  background-color: hsl(var(--accent) / 35%) !important;
}

:deep(.trace-detail-grouped-table .el-table__header th.el-table__cell) {
  color: hsl(var(--accent-foreground));
  background-color: hsl(var(--accent));
}

:deep(.trace-detail-grouped-table .el-table__header .cell) {
  white-space: nowrap;
}

:deep(.trace-detail-grouped-table .el-table__inner-wrapper::before) {
  display: none;
}

.trace-detail-grouped-table :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-border-color-lighter) inset;
}
</style>
