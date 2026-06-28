<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { CaseRecordStatusCode, QcDefectDto } from '#/api/mrms/types';

import { computed, onMounted, ref, watch } from 'vue';

import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElTag } from 'element-plus';

import { listDefectsApi, markRectifiedApi } from '#/api/mrms';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  QC_DEFECT_SEVERITY_LABEL,
  QC_DEFECT_SEVERITY_TAG,
  QC_DEFECT_STATUS_LABEL,
  QC_DEFECT_STATUS_TAG,
} from '../../../qc-workbench/qc.constants';

const props = defineProps<{
  caseRecordId: string;
  statusCode: CaseRecordStatusCode | string;
  isRecordRoom: boolean;
}>();

const defects = ref<QcDefectDto[]>([]);

const canDoctorRectify = computed(
  () => !props.isRecordRoom && (props.statusCode === 'returned' || props.statusCode === 'qcing'),
);

const { run: runList, loading } = useRequest(listDefectsApi, {
  manual: true,
  onSuccess: (data) => {
    defects.value = data ?? [];
  },
});

const { run: runRectify, loading: rectifying } = useRequest(markRectifiedApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('已标记整改');
    loadDefects();
  },
});

const tableColumns: TableColumn[] = [
  { prop: 'severityCode', label: '级别', width: 80, slot: 'severity' },
  { prop: 'statusCode', label: '状态', width: 90, slot: 'defectStatus' },
  { prop: 'ruleCode', label: '规则', width: 140, showOverflowTooltip: true },
  { prop: 'defectMessage', label: '描述', minWidth: 220, showOverflowTooltip: true },
  { prop: 'createdAt', label: '发现时间', width: 168, slot: 'createdAt' },
  { label: '操作', width: 100, slot: 'actions', align: 'center' },
];

function fmtTime(v?: string): string {
  if (!v) {
    return '';
  }
  return formatDateTime(v);
}

function loadDefects() {
  const id = preserveApiIdForRequest(props.caseRecordId);
  if (!id) {
    return;
  }
  runList({ caseRecordId: id });
}

async function onMarkRectified(row: QcDefectDto) {
  const defectId = preserveApiIdForRequest(row.defectId);
  if (!defectId) {
    return;
  }
  await runRectify({ defectId });
}

watch(
  () => props.caseRecordId,
  () => loadDefects(),
);

onMounted(() => {
  loadDefects();
});

defineExpose({ reload: loadDefects });
</script>

<template>
  <div class="homepage-qc-panel">
    <p
      v-if="canDoctorRectify"
      class="mb-12px text-13px"
      style="color: var(--el-text-color-secondary)"
    >
      请根据下列缺陷修改病案首页或相关文书，完成后点击「标记已改」；病案室复核通过后将关闭缺陷。
    </p>
    <p
      v-else-if="isRecordRoom"
      class="mb-12px text-13px"
      style="color: var(--el-text-color-secondary)"
    >
      完整质控操作请使用「质控工作台」；此处仅展示当前缺陷列表。
    </p>

    <TableLayout
      :table-data="defects"
      :loading="loading || rectifying"
      :total="defects.length"
      :table-columns="tableColumns"
      row-key="defectId"
      :show-top-query="false"
      :show-pagination="false"
    >
      <template #severity="{ row }">
        <ElTag :type="QC_DEFECT_SEVERITY_TAG[row.severityCode ?? ''] ?? 'info'" size="small">
          {{ QC_DEFECT_SEVERITY_LABEL[row.severityCode ?? ''] || row.severityCode }}
        </ElTag>
      </template>
      <template #defectStatus="{ row }">
        <ElTag :type="QC_DEFECT_STATUS_TAG[row.statusCode ?? ''] ?? 'info'" size="small">
          {{ QC_DEFECT_STATUS_LABEL[row.statusCode ?? ''] || row.statusCode }}
        </ElTag>
      </template>
      <template #createdAt="{ row }">
        <span>{{ fmtTime(row.createdAt) }}</span>
      </template>
      <template #actions="{ row }">
        <ElButton
          v-if="canDoctorRectify && row.statusCode === 'open'"
          link
          type="primary"
          @click="onMarkRectified(row)"
        >
          标记已改
        </ElButton>
      </template>
    </TableLayout>
  </div>
</template>
