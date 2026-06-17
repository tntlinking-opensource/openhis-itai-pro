<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { ReportTemplateListItemDto } from '#/api/report/report-design';

import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElTag } from 'element-plus';

import { listReportTemplatesApi } from '#/api/report/report-design';
import { useRequest } from '#/hooks/use-request';

import CopyTemplateDialog from './copy-template-dialog.vue';
import FormalPrintDialog from './formal-print-dialog.vue';
import PreviewDialog from './preview-dialog.vue';
import {
  formatReportLayoutMode,
  formatReportPipeline,
  formatReportSource,
  formatReportStatus,
  REPORT_PIPELINE_OPTIONS,
  REPORT_STATUS_OPTIONS,
} from './report-template.constants';

defineOptions({
  name: 'ReportPrintTemplate',
});

const router = useRouter();
const tableData = ref<ReportTemplateListItemDto[]>([]);
const previewDialogRef = ref<InstanceType<typeof PreviewDialog> | null>(null);
const formalPrintDialogRef = ref<InstanceType<typeof FormalPrintDialog> | null>(null);
const copyDialogRef = ref<InstanceType<typeof CopyTemplateDialog> | null>(null);

const queryParams = reactive<{
  keyword?: string;
  status?: string;
  pipeline?: string;
}>({});

const { run: runList, loading: listLoading } = useRequest(listReportTemplatesApi, {
  manual: true,
});

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '名称 / 编码',
  },
  {
    label: '状态',
    prop: 'status',
    type: 'select',
    options: [...REPORT_STATUS_OPTIONS],
    clearable: true,
  },
  {
    label: '管线',
    prop: 'pipeline',
    type: 'select',
    options: [...REPORT_PIPELINE_OPTIONS],
    clearable: true,
  },
]);

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, fixed: 'left' },
  {
    prop: 'name',
    label: '模板名称',
    minWidth: 200,
    fixed: 'left',
    showOverflowTooltip: true,
  },
  {
    prop: 'code',
    label: '模板编码',
    minWidth: 200,
    showOverflowTooltip: true,
  },
  {
    prop: 'pipeline',
    label: '管线',
    width: 110,
    slot: 'pipeline',
  },
  {
    prop: 'layoutMode',
    label: '排版模式',
    width: 120,
    slot: 'layoutMode',
  },
  {
    prop: 'pageSize',
    label: '纸型',
    width: 72,
  },
  {
    prop: 'datasetCode',
    label: '数据集',
    width: 160,
    showOverflowTooltip: true,
  },
  {
    prop: 'version',
    label: '版本',
    width: 64,
    align: 'center',
  },
  {
    prop: 'status',
    label: '状态',
    width: 88,
    slot: 'status',
  },
  {
    prop: 'source',
    label: '来源',
    width: 72,
    slot: 'source',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    width: 280,
    align: 'center',
    slot: 'action',
  },
];

async function getList() {
  try {
    const params = {
      keyword: queryParams.keyword?.trim() || undefined,
      status: queryParams.status?.trim() || undefined,
      pipeline: queryParams.pipeline?.trim() || undefined,
    };
    const rows = await runList(params);
    tableData.value = Array.isArray(rows) ? rows : [];
  } catch {
    tableData.value = [];
  }
}

function handleReset() {
  queryParams.keyword = undefined;
  queryParams.status = undefined;
  queryParams.pipeline = undefined;
  getList();
}

function handlePreview(row: ReportTemplateListItemDto) {
  if (!row.code?.trim()) {
    ElMessage.warning('模板编码为空');
    return;
  }
  previewDialogRef.value?.open(row);
}

function handleFormalPrint(row: ReportTemplateListItemDto) {
  if (!row.code?.trim()) {
    ElMessage.warning('模板编码为空');
    return;
  }
  formalPrintDialogRef.value?.open(row);
}

function handleCopyAsNew(row: ReportTemplateListItemDto) {
  if (!row.code?.trim()) {
    ElMessage.warning('模板编码为空');
    return;
  }
  copyDialogRef.value?.open({
    code: row.code,
    name: row.name,
    pipeline: row.pipeline,
    layoutMode: row.layoutMode,
  });
}

function onCopyAsNewSuccess(newCode: string) {
  ElMessage.success('新模板已创建');
  void getList();
  router.push({
    path: '/basic/report-template/designer',
    query: { code: newCode },
  });
}

function handleDesign(row: ReportTemplateListItemDto) {
  if (!row.code?.trim()) {
    ElMessage.warning('模板编码为空');
    return;
  }
  router.push({
    path: '/basic/report-template/designer',
    query: { code: row.code },
  });
}

function statusTagType(status?: string): 'info' | 'success' | 'warning' {
  if (status === 'published') {
    return 'success';
  }
  if (status === 'draft') {
    return 'warning';
  }
  return 'info';
}

getList();
</script>

<template>
  <Page auto-content-height>
    <TableLayout
      :form-items="formItems"
      :loading="listLoading"
      :query-params="queryParams"
      :show-pagination="false"
      :show-top-query="true"
      :table-columns="tableColumns"
      :table-data="tableData"
      @query="getList"
      @reset="handleReset"
    >
      <template #pipeline="{ row }">
        {{ formatReportPipeline(row.pipeline) }}
      </template>
      <template #layoutMode="{ row }">
        {{ formatReportLayoutMode(row.layoutMode) }}
      </template>
      <template #status="{ row }">
        <ElTag :type="statusTagType(row.status)" size="small">
          {{ formatReportStatus(row.status) }}
        </ElTag>
      </template>
      <template #source="{ row }">
        {{ formatReportSource(row.source) }}
      </template>
      <template #action="{ row }">
        <ElButton link type="primary" @click="handlePreview(row)">试打</ElButton>
        <ElButton link type="primary" @click="handleFormalPrint(row)">正式打印</ElButton>
        <ElButton link type="primary" @click="handleCopyAsNew(row)">复制</ElButton>
        <ElButton link type="primary" @click="handleDesign(row)">设计</ElButton>
      </template>
    </TableLayout>
    <PreviewDialog ref="previewDialogRef" />
    <FormalPrintDialog ref="formalPrintDialogRef" />
    <CopyTemplateDialog ref="copyDialogRef" @success="onCopyAsNewSuccess" />
  </Page>
</template>
