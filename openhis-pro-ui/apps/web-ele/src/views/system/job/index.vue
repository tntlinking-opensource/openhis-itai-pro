<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  ScheduledJobInfo,
  ScheduledJobListParams,
  ScheduledJobTenantOption,
} from '#/api/core/system/scheduled-job';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTabPane, ElTabs, ElTag } from 'element-plus';

import {
  changeScheduledJobStatusApi,
  deleteScheduledJobApi,
  getScheduledJobListApi,
  listScheduledJobTenantOptionsApi,
  runScheduledJobNowApi,
} from '#/api/core/system/scheduled-job';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import EditDialog from './edit-dialog.vue';
import ExecutionLogPanel from './execution-log-panel.vue';

defineOptions({
  name: 'ScheduledJobList',
});

const activeTab = ref('jobs');
const executionLogPanelRef = ref<InstanceType<typeof ExecutionLogPanel> | null>(null);
const tenantOptions = ref<ScheduledJobTenantOption[]>([]);

const queryParams = ref<ScheduledJobListParams>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  jobGroup: '',
  status: '',
  tenantId: '',
});

const tableData = ref<ScheduledJobTableRow[]>([]);
const loading = ref(false);
const total = ref(0);

const dialogVisible = ref(false);
const dialogFormData = ref<Partial<ScheduledJobInfo>>({});

type ScheduledJobTableRow = ScheduledJobInfo & { _jobRowKey: string };

/** 列表行唯一键：Quartz JobKey（须为字符串字段，TableLayout/vxe 不支持 function rowKey） */
function buildJobRowKey(row: ScheduledJobInfo, index: number) {
  const name = row.jobName?.trim();
  const group = row.jobGroup?.trim() || 'DEFAULT';
  if (!name) {
    return `row-${index}`;
  }
  return `${group}/${name}`;
}

/** 仅运行中任务展示下次执行；暂停时 Quartz 缓存值易误导（仍显示未来时刻） */
function formatNextFireTime(row: ScheduledJobInfo) {
  if (row.status !== 'NORMAL') {
    return '-';
  }
  return row.nextFireTime ? formatDateTime(row.nextFireTime) : '-';
}

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 60 },
  {
    prop: 'displayName',
    label: '任务名称',
    minWidth: 140,
    formatter: (row: ScheduledJobInfo) => row.displayName || row.jobName,
  },
  { prop: 'jobName', label: '任务标识', minWidth: 120, showOverflowTooltip: true },
  { prop: 'jobGroup', label: '分组', width: 90 },
  { prop: 'tenantName', label: '租户', width: 110, showOverflowTooltip: true },
  { prop: 'jobBeanName', label: '处理器', minWidth: 120, showOverflowTooltip: true },
  {
    prop: 'cronDescription',
    label: '执行计划',
    minWidth: 160,
    showOverflowTooltip: true,
    formatter: (row: ScheduledJobInfo) => row.cronDescription || row.cronExpression || '-',
  },
  { prop: 'status', label: '状态', slot: 'status', width: 80, align: 'center' },
  {
    prop: 'nextFireTime',
    label: '下次执行',
    width: 165,
    formatter: (row: ScheduledJobInfo) => formatNextFireTime(row),
  },
  { prop: 'action', label: '操作', fixed: 'right', slot: 'action', width: 300 },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '任务名/显示名/处理器',
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
    type: 'input',
    prop: 'jobGroup',
    label: '任务组',
    placeholder: '如 DEFAULT',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'status',
    label: '状态',
    placeholder: '全部',
    clearable: true,
    multiple: false,
    options: [
      { label: '运行中', value: 'NORMAL' },
      { label: '已暂停', value: 'PAUSE' },
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

/** 加载定时任务分页列表 */
const loadData = async () => {
  loading.value = true;
  try {
    const tenantIdRaw = queryParams.value.tenantId;
    let tenantId: number | string | undefined;
    if (tenantIdRaw) {
      tenantId = preserveApiIdForRequest(tenantIdRaw);
    }
    const response = await getScheduledJobListApi({
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      keyword: queryParams.value.keyword || undefined,
      jobGroup: queryParams.value.jobGroup || undefined,
      status: queryParams.value.status || undefined,
      tenantId: tenantId as string | undefined,
    });
    const { content, totalCount } = parsePagedRowsFromApiData<ScheduledJobInfo>(response);
    tableData.value = content.map((row, index) => ({
      ...row,
      _jobRowKey: buildJobRowKey(row, index),
    }));
    total.value = totalCount;
  } catch {
    ElMessage.error('加载定时任务失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  loadData();
};

const handleQuery = (params: {
  jobGroup?: string;
  keyword?: string;
  status?: string;
  tenantId?: string;
}) => {
  if (params.keyword !== undefined) {
    queryParams.value.keyword = params.keyword || '';
  }
  if (params.tenantId !== undefined) {
    queryParams.value.tenantId = params.tenantId || '';
  }
  if (params.jobGroup !== undefined) {
    queryParams.value.jobGroup = params.jobGroup || '';
  }
  if (params.status !== undefined) {
    queryParams.value.status = params.status || '';
  }
  queryParams.value.pageNo = 1;
  loadData();
};

const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    jobGroup: '',
    status: '',
    tenantId: '',
  };
  loadData();
};

const handleAdd = () => {
  dialogFormData.value = {};
  dialogVisible.value = true;
};

const handleEdit = (row: ScheduledJobInfo) => {
  dialogFormData.value = { ...row, parameters: row.parameters ? [...row.parameters] : [] };
  dialogVisible.value = true;
};

/** 跳转到执行记录并带入 JobKey */
const handleViewLogs = (row: ScheduledJobInfo) => {
  activeTab.value = 'logs';
  executionLogPanelRef.value?.applyPresetFilter(row.jobName, row.jobGroup);
};

/** 删除 Quartz 任务 */
const handleDelete = async (row: ScheduledJobInfo) => {
  try {
    await ElMessageBox.confirm(
      `确定删除任务「${row.displayName || row.jobName}」吗？删除后无法恢复。`,
      '提示',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    );
    await deleteScheduledJobApi({ jobName: row.jobName, jobGroup: row.jobGroup });
    ElMessage.success('删除成功');
    loadData();
  } catch (error: unknown) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败，请稍后重试');
    }
  }
};

/** 切换暂停/运行 */
const handleToggleStatus = async (row: ScheduledJobInfo) => {
  const nextStatus = row.status === 'NORMAL' ? 'PAUSE' : 'NORMAL';
  const actionLabel = nextStatus === 'PAUSE' ? '暂停' : '启用';
  try {
    const updated = await changeScheduledJobStatusApi({
      jobName: row.jobName,
      jobGroup: row.jobGroup,
      status: nextStatus,
    });
    ElMessage.success(`${actionLabel}成功`);
    if (updated?.jobName && updated?.jobGroup) {
      const index = tableData.value.findIndex(
        (item) => item.jobName === updated.jobName && item.jobGroup === updated.jobGroup,
      );
      if (index >= 0) {
        tableData.value[index] = { ...tableData.value[index], ...updated };
      }
    }
    await loadData();
  } catch {
    ElMessage.error(`${actionLabel}失败，请稍后重试`);
  }
};

/** 立即触发一次 */
const handleRunNow = async (row: ScheduledJobInfo) => {
  try {
    await ElMessageBox.confirm(
      `确定立即执行任务「${row.displayName || row.jobName}」吗？`,
      '立即执行',
      { type: 'info', confirmButtonText: '执行', cancelButtonText: '取消' },
    );
    await runScheduledJobNowApi({ jobName: row.jobName, jobGroup: row.jobGroup });
    ElMessage.success('已触发执行，可在执行记录查看结果');
  } catch (error: unknown) {
    if (error !== 'cancel') {
      ElMessage.error('触发失败，请稍后重试');
    }
  }
};

const handleDialogSuccess = () => {
  loadData();
};

onMounted(async () => {
  await loadTenantOptions();
  await loadData();
});
</script>

<template>
  <Page>
    <ElTabs v-model="activeTab" class="job-tabs">
      <ElTabPane label="任务配置" name="jobs">
        <TableLayout
          :table-data="tableData"
          :loading="loading"
          :total="total"
          :query-params="queryParams"
          :form-items="formItems"
          :table-columns="tableColumns"
          :show-side-query="false"
          :show-top-query="true"
          :show-pagination="true"
          :max-height="560"
          row-key="_jobRowKey"
          @query="handleQuery"
          @reset="handleReset"
          @pagination="handlePagination"
        >
          <template #toolbar-actions>
            <ElButton type="primary" @click="handleAdd">新增任务</ElButton>
          </template>

          <template #status="{ row }">
            <ElTag :type="row.status === 'NORMAL' ? 'success' : 'info'">
              {{ row.statusLabel || (row.status === 'NORMAL' ? '运行中' : '已暂停') }}
            </ElTag>
          </template>

          <template #action="{ row }">
            <ElButton type="primary" link @click.stop="handleEdit(row)">编辑</ElButton>
            <ElButton type="primary" link @click.stop="handleViewLogs(row)">执行记录</ElButton>
            <ElButton
              :type="row.status === 'NORMAL' ? 'warning' : 'primary'"
              link
              @click.stop="handleToggleStatus(row)"
            >
              {{ row.status === 'NORMAL' ? '暂停' : '启用' }}
            </ElButton>
            <ElButton type="primary" link @click.stop="handleRunNow(row)">立即执行</ElButton>
            <ElButton type="danger" link @click.stop="handleDelete(row)">删除</ElButton>
          </template>
        </TableLayout>
      </ElTabPane>

      <ElTabPane label="执行记录" name="logs">
        <ExecutionLogPanel ref="executionLogPanelRef" />
      </ElTabPane>
    </ElTabs>

    <EditDialog
      v-model:visible="dialogVisible"
      :form-data="dialogFormData"
      @success="handleDialogSuccess"
    />
  </Page>
</template>

<style scoped>
.job-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
</style>
