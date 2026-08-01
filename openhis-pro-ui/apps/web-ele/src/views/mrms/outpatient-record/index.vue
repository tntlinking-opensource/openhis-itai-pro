<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { OutpatientRecordListItemDto, QueryOutpatientRecordParams } from '#/api/mrms/types';

import { onActivated, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElTag } from 'element-plus';

import { generateDocumentPdfApi } from '#/api/emr/document';
import { queryOutpatientRecordPageApi } from '#/api/mrms';
import EmrPdfPreviewDialog from '#/components/outpatient/emr-pdf-preview-dialog.vue';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'MrmsOutpatientRecordList' });

const tableData = ref<OutpatientRecordListItemDto[]>([]);
const total = ref(0);
const loading = ref(false);

const queryParams = reactive<QueryOutpatientRecordParams & { encounterRange?: string[] }>({
  pageNo: 1,
  pageSize: 20,
});

const pdfVisible = ref(false);
const pdfBase64 = ref('');
const pdfPageCount = ref<number | undefined>();
const pdfTitle = ref('门诊病历');
const pdfAutoPrint = ref(false);

const DOCUMENT_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  LOCKED: '已锁定',
  ARCHIVED: '已归档',
};

const DOCUMENT_STATUS_TAG: Record<string, 'info' | 'success' | 'warning' | 'danger'> = {
  DRAFT: 'info',
  SUBMITTED: 'success',
  LOCKED: 'warning',
  ARCHIVED: 'danger',
};

const DOCUMENT_STATUS_OPTIONS = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已提交', value: 'SUBMITTED' },
  { label: '已锁定', value: 'LOCKED' },
  { label: '已归档', value: 'ARCHIVED' },
];

const { run: runPage, loading: pageLoading } = useRequest(queryOutpatientRecordPageApi, {
  manual: true,
  onSuccess: (data) => {
    const parsed = parsePagedRowsFromApiData<OutpatientRecordListItemDto>(data);
    tableData.value = parsed.content;
    total.value = parsed.totalCount;
  },
});

watch(pageLoading, (v) => {
  loading.value = v ?? false;
});

const formItems: FormItemConfig[] = [
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '姓名/门诊号/病案号',
  },
  {
    label: '科室',
    prop: 'deptName',
    type: 'input',
    placeholder: '就诊科室',
  },
  {
    label: '状态',
    prop: 'statusCode',
    type: 'select',
    options: DOCUMENT_STATUS_OPTIONS,
    clearable: true,
  },
  {
    label: '就诊日期',
    prop: 'encounterRange',
    type: 'daterange',
  },
];

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 56, align: 'center' },
  { prop: 'patientName', label: '姓名', width: 90 },
  { prop: 'outpatientNo', label: '门诊号', width: 130, showOverflowTooltip: true },
  { prop: 'deptName', label: '科室', width: 120, showOverflowTooltip: true },
  { prop: 'encounterTime', label: '就诊时间', width: 168, slot: 'encounterTime' },
  { prop: 'title', label: '文书标题', minWidth: 160, showOverflowTooltip: true },
  { prop: 'statusCode', label: '状态', width: 100, slot: 'status' },
  { prop: 'updatedAt', label: '更新时间', width: 168, slot: 'updatedAt' },
  { label: '操作', width: 140, fixed: 'right', slot: 'actions', align: 'center' },
];

/** 格式化日期时间展示 */
function fmtTime(v?: string): string {
  if (!v) {
    return '';
  }
  return formatDateTime(v);
}

/** 组装分页查询参数 */
function buildQuery(): QueryOutpatientRecordParams {
  const range = queryParams.encounterRange;
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    keyword: queryParams.keyword?.trim() || undefined,
    deptName: queryParams.deptName?.trim() || undefined,
    statusCode: queryParams.statusCode?.trim() || undefined,
    encounterDateFrom: range?.[0],
    encounterDateTo: range?.[1],
  };
}

/** 拉取列表 */
async function getList() {
  await runPage(buildQuery());
}

/** 重置筛选 */
function handleReset() {
  queryParams.pageNo = 1;
  queryParams.pageSize = 20;
  queryParams.keyword = undefined;
  queryParams.deptName = undefined;
  queryParams.statusCode = undefined;
  queryParams.encounterRange = undefined;
  getList();
}

/** 分页变更 */
function handlePagination(payload: { pageNo: number; pageSize: number }) {
  queryParams.pageNo = payload.pageNo;
  queryParams.pageSize = payload.pageSize;
  getList();
}

function statusText(row: OutpatientRecordListItemDto): string {
  return DOCUMENT_STATUS_LABEL[row.statusCode ?? ''] || row.statusCode || '';
}

/**
 * 生成门诊病历 PDF 并打开预览/打印对话框。
 *
 * @param row 列表行
 * @param autoPrint 是否自动调起系统打印
 */
async function openPdf(row: OutpatientRecordListItemDto, autoPrint: boolean) {
  const documentId = preserveApiIdForRequest(row.documentId);
  if (!documentId) {
    ElMessage.warning('文书主键缺失');
    return;
  }
  try {
    loading.value = true;
    const result = await generateDocumentPdfApi({
      documentId,
      patientId: preserveApiIdForRequest(row.patientId),
      encounterId: preserveApiIdForRequest(row.encounterId),
      documentTitle: row.title || '门诊病历',
      documentTypeName: row.documentTypeName || '门诊病历',
      previewMode: true,
    });
    const base64 = result?.pdfBase64?.trim();
    if (!base64) {
      ElMessage.error('未生成 PDF 内容');
      return;
    }
    pdfBase64.value = base64;
    pdfPageCount.value = result.pageCount;
    pdfTitle.value = row.title || `${row.patientName || ''}门诊病历`;
    pdfAutoPrint.value = autoPrint;
    pdfVisible.value = true;
  } catch {
    ElMessage.error(autoPrint ? '打印失败' : '预览失败');
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  getList();
});

onActivated(() => {
  getList();
});
</script>

<template>
  <Page auto-content-height>
    <TableLayout
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams as Record<string, unknown>"
      :form-items="formItems"
      :table-columns="tableColumns"
      row-key="documentId"
      :show-top-query="true"
      :show-pagination="true"
      @query="getList"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #status="{ row }">
        <ElTag :type="DOCUMENT_STATUS_TAG[row.statusCode ?? ''] ?? 'info'" size="small">
          {{ statusText(row) }}
        </ElTag>
      </template>
      <template #encounterTime="{ row }">
        <span>{{ fmtTime(row.encounterTime) }}</span>
      </template>
      <template #updatedAt="{ row }">
        <span>{{ fmtTime(row.updatedAt) }}</span>
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openPdf(row, false)"> 查看 </ElButton>
        <ElButton link type="primary" @click="openPdf(row, true)"> 打印 </ElButton>
      </template>
    </TableLayout>

    <EmrPdfPreviewDialog
      v-model="pdfVisible"
      :pdf-base64="pdfBase64"
      :page-count="pdfPageCount"
      :title="pdfTitle"
      :auto-print="pdfAutoPrint"
    />
  </Page>
</template>
