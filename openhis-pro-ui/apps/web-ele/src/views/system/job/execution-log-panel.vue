<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  ScheduledJobExecutionLog,
  ScheduledJobExecutionLogListParams,
  ScheduledJobTenantOption,
} from '#/api/core/system/scheduled-job';

import { computed, onMounted, ref } from 'vue';

import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElMessage, ElTag } from 'element-plus';

import {
  getScheduledJobExecutionLogListApi,
  listScheduledJobTenantOptionsApi,
} from '#/api/core/system/scheduled-job';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({
  name: 'ScheduledJobExecutionLogPanel',
});

interface Props {
  /** 从任务列表带入的任务标识筛选 */
  presetJobName?: string;
  presetJobGroup?: string;
}

const props = withDefaults(defineProps<Props>(), {
  presetJobName: '',
  presetJobGroup: '',
});

const tenantOptions = ref<ScheduledJobTenantOption[]>([]);

const queryParams = ref<ScheduledJobExecutionLogListParams>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  jobName: '',
  jobGroup: '',
  tenantId: '',
  success: undefined,
});

type ScheduledJobExecutionLogRow = ScheduledJobExecutionLog & { _logRowKey: string };

const tableData = ref<ScheduledJobExecutionLogRow[]>([]);
const loading = ref(false);
const total = ref(0);

/**
 * 列表行唯一键。Snowflake Long 若被当成 Number，末位会抹平，多条记录 id 撞车后
 * TableLayout/vxe 的 keyField 只会渲染去重后的行（表现为 total=4 却只见 2 行）。
 */
function buildLogRowKey(row: ScheduledJobExecutionLog, index: number) {
  const id = row.id != null && String(row.id) !== '' ? String(row.id) : '';
  return id ? `${id}#${index}` : `row-${index}`;
}

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 60 },
  {
    prop: 'finishedAt',
    label: '执行时间',
    width: 170,
    formatter: (row: ScheduledJobExecutionLog) =>
      row.finishedAt ? formatDateTime(row.finishedAt) : '-',
  },
  {
    prop: 'displayName',
    label: '任务',
    minWidth: 140,
    formatter: (row: ScheduledJobExecutionLog) => row.displayName || row.jobName,
  },
  { prop: 'jobGroup', label: '分组', width: 90 },
  { prop: 'tenantName', label: '租户', width: 120, showOverflowTooltip: true },
  { prop: 'executionTypeLabel', label: '触发方式', width: 100 },
  { prop: 'success', label: '结果', slot: 'success', width: 80, align: 'center' },
  { prop: 'durationMs', label: '耗时(ms)', width: 90, align: 'right' },
  { prop: 'errorMessage', label: '错误信息', minWidth: 200, showOverflowTooltip: true },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '任务名/显示名',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'jobName',
    label: '任务标识',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'jobGroup',
    label: '任务组',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'tenantId',
    label: '租户',
    placeholder: '全部',
    clearable: true,
    multiple: false,
    options: tenantOptions.value.map((item) => ({
      label: `${item.name} (${item.code})`,
      value: item.id,
    })),
  },
  {
    type: 'select',
    prop: 'success',
    label: '结果',
    placeholder: '全部',
    clearable: true,
    multiple: false,
    options: [
      { label: '成功', value: 'true' },
      { label: '失败', value: 'false' },
    ],
  },
]);

/** 加载租户下拉 */
async function loadTenantOptions() {
  try {
    tenantOptions.value = (await listScheduledJobTenantOptionsApi()) || [];
  } catch {
    tenantOptions.value = [];
  }
}

/** 加载执行历史分页 */
async function loadData() {
  loading.value = true;
  try {
    const tenantIdRaw = queryParams.value.tenantId;
    let tenantId: string | undefined;
    if (tenantIdRaw) {
      tenantId = preserveApiIdForRequest(tenantIdRaw);
    }
    let success: boolean | undefined;
    const successRaw = queryParams.value.success as boolean | string | undefined;
    if (successRaw === true || successRaw === 'true') {
      success = true;
    } else if (successRaw === false || successRaw === 'false') {
      success = false;
    }
    const response = await getScheduledJobExecutionLogListApi({
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      keyword: queryParams.value.keyword || undefined,
      jobName: queryParams.value.jobName || undefined,
      jobGroup: queryParams.value.jobGroup || undefined,
      tenantId,
      success,
    });
    const content = response.content || [];
    tableData.value = content.map((row, index) => ({
      ...row,
      _logRowKey: buildLogRowKey(row, index),
    }));
    total.value = response.totalCount || 0;
  } catch {
    ElMessage.error('加载执行记录失败');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  loadData();
};

const handleQuery = (params: Record<string, unknown>) => {
  if (params.keyword !== undefined) {
    queryParams.value.keyword = String(params.keyword || '');
  }
  if (params.jobName !== undefined) {
    queryParams.value.jobName = String(params.jobName || '');
  }
  if (params.jobGroup !== undefined) {
    queryParams.value.jobGroup = String(params.jobGroup || '');
  }
  if (params.tenantId !== undefined) {
    queryParams.value.tenantId = String(params.tenantId || '');
  }
  if (params.success !== undefined) {
    queryParams.value.success = params.success as boolean | undefined;
  }
  queryParams.value.pageNo = 1;
  loadData();
};

const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    jobName: props.presetJobName || '',
    jobGroup: props.presetJobGroup || '',
    tenantId: '',
    success: undefined,
  };
  loadData();
};

/** 从任务列表跳转时带入 JobKey 筛选 */
function applyPresetFilter(jobName: string, jobGroup: string) {
  queryParams.value.jobName = jobName;
  queryParams.value.jobGroup = jobGroup;
  queryParams.value.pageNo = 1;
  loadData();
}

defineExpose({ applyPresetFilter, loadData });

onMounted(async () => {
  if (props.presetJobName) {
    queryParams.value.jobName = props.presetJobName;
  }
  if (props.presetJobGroup) {
    queryParams.value.jobGroup = props.presetJobGroup;
  }
  await loadTenantOptions();
  await loadData();
});
</script>

<template>
  <TableLayout
    :table-data="tableData"
    :loading="loading"
    :total="total"
    :query-params="queryParams"
    :form-items="formItems"
    :table-columns="tableColumns"
    :show-top-query="true"
    :show-pagination="true"
    row-key="_logRowKey"
    @query="handleQuery"
    @reset="handleReset"
    @pagination="handlePagination"
  >
    <template #success="{ row }">
      <ElTag :type="row.success ? 'success' : 'danger'">
        {{ row.successLabel || (row.success ? '成功' : '失败') }}
      </ElTag>
    </template>
  </TableLayout>
</template>
