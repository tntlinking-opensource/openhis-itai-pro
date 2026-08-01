<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  FrontSoftwareConfigViewDto,
  PagePushTasksParams,
  PushLogSummaryDto,
  PushTaskSummaryDto,
} from '#/api/crb';

import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElAlert,
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  getFrontSoftwareConfigViewApi,
  listPushLogsByTaskApi,
  pagePushTasksApi,
  resolveCrbTenantId,
  retryPushTaskApi,
} from '#/api/crb';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'CrbPushMonitor' });

const TASK_STATUS_OPTIONS = [
  { label: '待处理(PENDING)', value: 'PENDING' },
  { label: '已发送(SENT)', value: 'SENT' },
  { label: '失败(DEAD)', value: 'DEAD' },
];

const tableData = ref<PushTaskSummaryDto[]>([]);
const total = ref(0);
const loading = ref(false);
const configView = ref<FrontSoftwareConfigViewDto | null>(null);
const logList = ref<PushLogSummaryDto[]>([]);
const activeTaskId = ref('');

const queryParams = reactive<PagePushTasksParams>({
  pageNo: 1,
  pageSize: 20,
  status: undefined,
});

const formItems: FormItemConfig[] = [
  {
    label: '任务状态',
    prop: 'status',
    type: 'select',
    clearable: true,
    placeholder: '全部',
    options: TASK_STATUS_OPTIONS,
  },
];

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 56, align: 'center' },
  { prop: 'taskId', label: '任务ID', width: 180, showOverflowTooltip: true },
  { prop: 'resource', label: '资源', width: 120, showOverflowTooltip: true },
  { prop: 'businessKey', label: '业务键', minWidth: 160, showOverflowTooltip: true },
  { prop: 'status', label: '状态', width: 100, align: 'center', slot: 'status' },
  { prop: 'attemptCount', label: '重试次数', width: 90, align: 'center' },
  { prop: 'dueAt', label: 'SLA截止', width: 168, slot: 'dueAt' },
  { prop: 'lastError', label: '最近错误', minWidth: 160, showOverflowTooltip: true },
  { label: '操作', width: 160, fixed: 'right', slot: 'actions', align: 'center' },
];

const [Drawer, drawerApi] = useVbenDrawer();

const { run: runConfig } = useRequest(getFrontSoftwareConfigViewApi, {
  manual: true,
  onSuccess: (data) => {
    configView.value = data ?? null;
  },
});

const { run: runPage, loading: pageLoading } = useRequest(pagePushTasksApi, {
  manual: true,
  onSuccess: (data) => {
    const parsed = parsePagedRowsFromApiData<PushTaskSummaryDto>(data);
    tableData.value = parsed.content;
    total.value = parsed.totalCount;
  },
});

const { run: runLogs, loading: logsLoading } = useRequest(listPushLogsByTaskApi, {
  manual: true,
  onSuccess: (data) => {
    logList.value = data ?? [];
  },
});

const { run: runRetry, loading: retryLoading } = useRequest(retryPushTaskApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('已提交重试');
    getList();
  },
});

watch([pageLoading, retryLoading], ([page, retry]) => {
  loading.value = (page ?? false) || (retry ?? false);
});

/** 前置软件配置提示文案。 */
const configBannerType = computed(() => (configView.value?.configured ? 'success' : 'warning'));

/** 前置软件配置摘要。 */
const configBannerText = computed(() => {
  const cfg = configView.value;
  if (!cfg) {
    return '正在加载前置软件配置…';
  }
  if (!cfg.configured) {
    return '前置软件未完整配置（baseUrl / orgCode / license），推送可能失败';
  }
  const parts = [
    `机构代码: ${cfg.orgCode ?? '—'}`,
    `BaseUrl: ${cfg.baseUrlMasked ?? '—'}`,
    `License: ${cfg.licenseMasked ?? '—'}`,
    cfg.mockEnabled ? 'Mock 已开启' : 'Mock 已关闭',
  ];
  return parts.join(' · ');
});

/** 格式化日期时间展示。 */
function fmtTime(v?: string): string {
  if (!v) {
    return '';
  }
  return formatDateTime(v);
}

/** 拉取任务分页列表。 */
async function getList() {
  await runPage(resolveCrbTenantId(), {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    status: queryParams.status?.trim() || undefined,
  });
}

/** 加载前置软件配置视图。 */
async function loadConfigView() {
  await runConfig();
}

/** 重置筛选条件。 */
function handleReset() {
  queryParams.pageNo = 1;
  queryParams.pageSize = 20;
  queryParams.status = undefined;
  getList();
}

/** 分页变更。 */
function handlePagination(payload: { pageNo: number; pageSize: number }) {
  queryParams.pageNo = payload.pageNo;
  queryParams.pageSize = payload.pageSize;
  getList();
}

/** 打开任务日志抽屉。 */
async function handleViewLogs(row: PushTaskSummaryDto) {
  const taskId = preserveApiIdForRequest(row.taskId);
  if (!taskId) {
    ElMessage.warning('任务主键缺失');
    return;
  }
  activeTaskId.value = taskId;
  drawerApi.open();
  await runLogs(resolveCrbTenantId(), taskId);
}

/** 人工重试推送任务。 */
async function handleRetry(row: PushTaskSummaryDto) {
  const taskId = preserveApiIdForRequest(row.taskId);
  if (!taskId) {
    ElMessage.warning('任务主键缺失');
    return;
  }
  try {
    await ElMessageBox.confirm('确定重试该推送任务吗？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  await runRetry(resolveCrbTenantId(), taskId);
}

/** 任务状态标签类型。 */
function statusTagType(status?: string): 'danger' | 'info' | 'success' | 'warning' {
  if (status === 'SENT') {
    return 'success';
  }
  if (status === 'DEAD') {
    return 'danger';
  }
  if (status === 'PENDING') {
    return 'warning';
  }
  return 'info';
}

onMounted(() => {
  loadConfigView();
  getList();
});

onActivated(() => {
  loadConfigView();
  getList();
});
</script>

<template>
  <Page auto-content-height>
    <ElAlert
      class="mb-3"
      :title="configBannerText"
      :type="configBannerType"
      :closable="false"
      show-icon
    />

    <TableLayout
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams as Record<string, unknown>"
      :form-items="formItems"
      :table-columns="tableColumns"
      row-key="taskId"
      :show-top-query="true"
      :show-pagination="true"
      @query="getList"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #status="{ row }">
        <ElTag :type="statusTagType(row.status)" size="small">{{ row.status || '—' }}</ElTag>
      </template>
      <template #dueAt="{ row }">
        <span>{{ fmtTime(row.dueAt) }}</span>
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="handleViewLogs(row)">日志</ElButton>
        <ElButton link type="warning" @click="handleRetry(row)">重试</ElButton>
      </template>
    </TableLayout>

    <Drawer :title="`推送日志 · ${activeTaskId}`" :loading="logsLoading" :footer="false">
      <ElTable :data="logList" border stripe size="small">
        <ElTableColumn type="index" label="序号" width="56" align="center" />
        <ElTableColumn prop="success" label="成功" width="70" align="center">
          <template #default="{ row }">
            <ElTag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? '是' : '否' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="errorCode" label="错误码" width="100" show-overflow-tooltip />
        <ElTableColumn prop="errorName" label="错误名" min-width="120" show-overflow-tooltip />
        <ElTableColumn prop="durationMs" label="耗时(ms)" width="90" align="center" />
        <ElTableColumn label="时间" width="168">
          <template #default="{ row }">
            {{ fmtTime(row.createdAt) }}
          </template>
        </ElTableColumn>
      </ElTable>
    </Drawer>
  </Page>
</template>

<style scoped>
.mb-3 {
  margin-bottom: 12px;
}
</style>
