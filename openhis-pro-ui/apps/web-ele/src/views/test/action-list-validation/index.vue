<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import { computed, reactive, ref } from 'vue';

import { Plus, Refresh } from '@element-plus/icons-vue';
import { Page } from '@vben/common-ui';
import { TableLayout } from '@vben-core/table-layout-ui';
import {
  ElButton,
  ElDatePicker,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTag,
} from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';

defineOptions({
  name: 'ActionListValidationPage',
});

type BizStatus = 'done' | 'draft' | 'executing' | 'revoked';

interface ActionListRow {
  bizNo: string;
  createdAt: string;
  id: string;
  patientName: string;
  status: BizStatus;
}

interface QueryParams {
  dateRange: [] | [string, string];
  keyword: string;
  pageNo: number;
  pageSize: number;
  status: '' | BizStatus;
}

const statusOptions: Array<{ label: string; value: QueryParams['status'] }> = [
  { label: '全部', value: '' },
  { label: '草稿', value: 'draft' },
  { label: '执行中', value: 'executing' },
  { label: '已完成', value: 'done' },
  { label: '已撤回', value: 'revoked' },
];

const tableColumns: TableColumn[] = [
  { type: 'selection', width: 50, align: 'center', headerAlign: 'center' },
  { type: 'index', label: '序号', width: 60, align: 'center', headerAlign: 'center' },
  { prop: 'bizNo', label: '业务号', minWidth: 170, align: 'center', headerAlign: 'center' },
  { prop: 'patientName', label: '患者姓名', minWidth: 120, align: 'center', headerAlign: 'center' },
  {
    prop: 'status',
    label: '状态',
    width: 110,
    slot: 'status',
    align: 'center',
    headerAlign: 'center',
  },
  { prop: 'createdAt', label: '创建时间', minWidth: 170, align: 'center', headerAlign: 'center' },
  {
    prop: 'actions',
    label: '操作',
    minWidth: 240,
    slot: 'actions',
    fixed: 'right',
    headerAlign: 'center',
  },
];

const sourceData = reactive<ActionListRow[]>(
  Array.from({ length: 24 }).map((_, index) => {
    const statusPool: BizStatus[] = ['draft', 'executing', 'done', 'revoked'];
    const status = statusPool[index % statusPool.length]!;
    return {
      id: `${index + 1}`,
      bizNo: `ALV-20260430-${String(index + 1).padStart(3, '0')}`,
      patientName: `测试患者${index + 1}`,
      status,
      createdAt: `2026-04-30 ${String(8 + Math.floor(index / 2)).padStart(2, '0')}:${String(
        (index * 7) % 60,
      ).padStart(2, '0')}:00`,
    };
  }),
);

// 核心业务/视图状态：查询条件、加载态、列表数据、选中项（单一来源）。
const queryParams = reactive<QueryParams>({
  keyword: '',
  status: '',
  dateRange: [],
  pageNo: 1,
  pageSize: 10,
});
const loading = ref(false);
const tableData = ref<ActionListRow[]>([]);
const total = ref(0);
const selection = ref<ActionListRow[]>([]);
const errorMode = ref(false);

const subtitleText = computed(() => {
  return errorMode.value
    ? '当前为错误态模拟：所有操作将返回失败提示。'
    : '用于验证带操作列表页面的按钮层级、闭环反馈与样式基线。';
});

// 统一延迟：用于模拟执行中 loading 反馈。
function delay(ms: number) {
  return new Promise<void>((resolve) => {
    setTimeout(resolve, ms);
  });
}

// 状态标签映射：保证状态视觉层级与列表语义一致。
function getStatusTagType(status: BizStatus): 'danger' | 'info' | 'success' | 'warning' {
  if (status === 'executing') return 'warning';
  if (status === 'done') return 'success';
  if (status === 'revoked') return 'danger';
  return 'info';
}

// 根据筛选条件构建结果集，作为分页前唯一数据来源。
function buildFilteredRows(): ActionListRow[] {
  const keyword = queryParams.keyword.trim();
  return sourceData.filter((item) => {
    const matchKeyword =
      keyword.length === 0 || item.bizNo.includes(keyword) || item.patientName.includes(keyword);
    const matchStatus = queryParams.status === '' || item.status === queryParams.status;
    return matchKeyword && matchStatus;
  });
}

// 刷新列表：筛选 + 分页统一入口，保证所有操作后都走同一闭环。
function refreshTable() {
  loading.value = true;
  if (errorMode.value) {
    loading.value = false;
    tableData.value = [];
    total.value = 0;
    ElMessage.error('模拟错误态：列表加载失败');
    return;
  }
  const filtered = buildFilteredRows();
  total.value = filtered.length;
  const start = (queryParams.pageNo - 1) * queryParams.pageSize;
  const end = start + queryParams.pageSize;
  tableData.value = filtered.slice(start, end);
  loading.value = false;
}

// 查询动作：回到第一页并刷新，确保筛选行为稳定可复现。
function handleQuery() {
  queryParams.pageNo = 1;
  refreshTable();
}

// 重置动作：清空查询条件并刷新，覆盖空态/默认态切换验证。
function handleReset() {
  queryParams.keyword = '';
  queryParams.status = '';
  queryParams.dateRange = [];
  queryParams.pageNo = 1;
  selection.value = [];
  refreshTable();
}

// 分页动作：写回页码与页长后刷新列表，不依赖隐式 watch。
function handlePagination(pagination: { page: number; limit: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  refreshTable();
}

// 选中动作：维护批量操作目标集合，作为顶部批量前置校验依据。
function handleSelectionChange(rows: ActionListRow[]) {
  selection.value = rows;
}

// 新增动作：模拟新增成功并刷新列表。
async function handleAdd() {
  loading.value = true;
  await delay(300);
  loading.value = false;
  if (errorMode.value) {
    ElMessage.error('新增失败（模拟）');
    return;
  }
  ElMessage.success('新增成功（模拟）');
  refreshTable();
}

// 刷新动作：中性操作入口，直接触发统一刷新逻辑。
async function handleRefresh() {
  loading.value = true;
  await delay(200);
  loading.value = false;
  if (errorMode.value) {
    ElMessage.error('刷新失败（模拟）');
    return;
  }
  ElMessage.success('刷新成功');
  refreshTable();
}

// 批量执行：仅草稿可执行，执行后刷新列表。
async function handleBatchExecute() {
  if (selection.value.length === 0) {
    ElMessage.warning('请先选择至少一条记录');
    return;
  }
  const invalid = selection.value.some((item) => item.status !== 'draft');
  if (invalid) {
    ElMessage.error('仅草稿状态允许批量执行');
    return;
  }
  loading.value = true;
  await delay(400);
  loading.value = false;
  if (errorMode.value) {
    ElMessage.error('批量执行失败（模拟）');
    return;
  }
  for (const item of selection.value) {
    const target = sourceData.find((row) => row.id === item.id);
    if (target) target.status = 'executing';
  }
  ElMessage.success(`批量执行成功：${selection.value.length} 条`);
  refreshTable();
}

// 批量撤回：仅执行中可撤回，撤回后刷新列表。
async function handleBatchRevoke() {
  if (selection.value.length === 0) {
    ElMessage.warning('请先选择至少一条记录');
    return;
  }
  const invalid = selection.value.some((item) => item.status !== 'executing');
  if (invalid) {
    ElMessage.error('仅执行中状态允许批量撤回');
    return;
  }
  loading.value = true;
  await delay(400);
  loading.value = false;
  if (errorMode.value) {
    ElMessage.error('批量撤回失败（模拟）');
    return;
  }
  for (const item of selection.value) {
    const target = sourceData.find((row) => row.id === item.id);
    if (target) target.status = 'revoked';
  }
  ElMessage.success(`批量撤回成功：${selection.value.length} 条`);
  refreshTable();
}

// 查看动作：提供行内操作反馈，不修改状态但保持统一反馈体验。
function handleView(row: ActionListRow) {
  ElMessage.success(`查看：${row.bizNo}`);
}

// 执行动作：仅草稿允许执行，执行后刷新。
async function handleExecute(row: ActionListRow) {
  if (row.status !== 'draft') {
    ElMessage.error('仅草稿状态允许执行');
    return;
  }
  loading.value = true;
  await delay(300);
  loading.value = false;
  if (errorMode.value) {
    ElMessage.error('执行失败（模拟）');
    return;
  }
  row.status = 'executing';
  ElMessage.success(`执行成功：${row.bizNo}`);
  refreshTable();
}

// 撤回动作：仅执行中允许撤回，撤回后刷新。
async function handleRevoke(row: ActionListRow) {
  if (row.status !== 'executing') {
    ElMessage.error('仅执行中状态允许撤回');
    return;
  }
  loading.value = true;
  await delay(300);
  loading.value = false;
  if (errorMode.value) {
    ElMessage.error('撤回失败（模拟）');
    return;
  }
  row.status = 'revoked';
  ElMessage.success(`撤回成功：${row.bizNo}`);
  refreshTable();
}

// 删除动作：仅草稿允许删除，确认后删除并刷新列表。
async function handleDelete(row: ActionListRow) {
  if (row.status !== 'draft') {
    ElMessage.error('仅草稿状态允许删除');
    return;
  }
  try {
    await ElMessageBox.confirm(`确认删除 ${row.bizNo} 吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    });
    loading.value = true;
    await delay(250);
    loading.value = false;
    if (errorMode.value) {
      ElMessage.error('删除失败（模拟）');
      return;
    }
    const index = sourceData.findIndex((item) => item.id === row.id);
    if (index >= 0) {
      sourceData.splice(index, 1);
    }
    ElMessage.success(`删除成功：${row.bizNo}`);
    refreshTable();
  } catch {
    ElMessage.info('已取消删除');
  }
}

refreshTable();
</script>

<template>
  <Page auto-content-height class="action-list-validation-page" content-class="h-full">
    <div class="page-head">
      <SectionTitle title="带操作列表测试页" />
      <p class="subtitle">{{ subtitleText }}</p>
    </div>

    <section class="section-card">
      <div class="query-row">
        <ElInput
          v-model="queryParams.keyword"
          clearable
          class="query-item"
          placeholder="关键字（业务号/患者姓名）"
        />
        <ElSelect
          v-model="queryParams.status"
          clearable
          class="query-item"
          placeholder="请选择状态"
        >
          <ElOption
            v-for="item in statusOptions"
            :key="`${item.value}`"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
        <ElDatePicker
          v-model="queryParams.dateRange"
          clearable
          class="query-item date-range"
          type="daterange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
        />
        <div class="flex items-center gap-2">
          <ElButton type="primary" @click="handleQuery">查询</ElButton>
          <ElButton @click="handleReset">重置</ElButton>
        </div>
      </div>
    </section>

    <section class="section-card mt-3">
      <TableLayout
        :table-data="tableData"
        :loading="loading"
        :total="total"
        :query-params="queryParams"
        :table-columns="tableColumns"
        :show-top-query="false"
        :show-pagination="true"
        border
        stripe
        @pagination="handlePagination"
        @selection-change="handleSelectionChange"
      >
        <template #toolbar-actions>
          <div class="mb-2 flex w-full items-center gap-1">
            <ElButton :icon="Plus" type="primary" @click="handleAdd">新增</ElButton>
            <ElButton :icon="Refresh" plain @click="handleRefresh">刷新</ElButton>
            <ElButton type="primary" @click="handleBatchExecute">批量执行</ElButton>
            <ElButton type="warning" plain @click="handleBatchRevoke">批量撤回</ElButton>
          </div>
        </template>
        <template #toolbar-tools>
          <ElSwitch
            v-model="errorMode"
            active-text="错误态"
            inactive-text="正常态"
            @change="handleRefresh"
          />
        </template>
        <template #status="{ row }">
          <ElTag :type="getStatusTagType(row.status)" effect="light">{{ row.status }}</ElTag>
        </template>
        <template #actions="{ row }">
          <div class="flex items-center justify-center gap-2">
            <ElButton link type="primary" @click="handleView(row)">查看</ElButton>
            <ElButton link type="primary" @click="handleExecute(row)">执行</ElButton>
            <ElButton link type="warning" @click="handleRevoke(row)">撤回</ElButton>
            <ElButton link type="danger" @click="handleDelete(row)">删除</ElButton>
          </div>
        </template>
      </TableLayout>
    </section>
  </Page>
</template>

<style scoped>
.action-list-validation-page {
  min-height: 100%;
  background-color: var(--el-fill-color-lighter);
}

.page-head {
  margin-bottom: 12px;
}

.subtitle {
  margin: 6px 0 0 10px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.section-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background-color: var(--el-bg-color);
  padding: 12px;
}

.query-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.query-item {
  width: 220px;
}

.date-range {
  width: 280px;
}
</style>
