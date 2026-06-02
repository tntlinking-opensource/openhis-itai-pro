<script lang="ts" setup>
import type { FormItemConfig, TableColumn, TreeNodeData } from '@vben-core/table-layout-ui';

import type { DiagnosisCatalogInitResponse, DiagnosisCatalogItem } from '#/api';

import { nextTick, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage } from 'element-plus';

import {
  batchDisableDiagnosisCatalogApi,
  batchEnableDiagnosisCatalogApi,
  deleteDiagnosisCatalogApi,
  initDiagnosisCatalogApi,
  pageDiagnosisCatalogApi,
} from '#/api';
import StatusEnumTag from '#/components/status-enum-tag.vue';

import EditDialog from './edit-dialog.vue';

const queryParams = ref({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
  sourceEnum: undefined as string | undefined,
});

const tableData = ref<DiagnosisCatalogItem[]>([]);
const loading = ref(false);
const total = ref(0);
const selectedRows = ref<DiagnosisCatalogItem[]>([]);
const tableLayoutRef = ref<InstanceType<typeof TableLayout> | null>(null);
const editDialogVisible = ref(false);
const editFormData = ref<Partial<DiagnosisCatalogItem>>({});

// 初始化数据（分类、状态等）
const diagnosisCategoryList = ref<Array<{ label: string; value: number | string }>>([]);
const statusFlagOptions = ref<Array<{ label: string; value: number | string }>>([]);
const ybDiagTypes = ref<Array<{ label: string; value: number | string }>>([]);

const tableColumns: TableColumn[] = [
  {
    type: 'selection',
    fixed: 'left',
    width: 50,
    align: 'center',
  },
  {
    type: 'index',
    label: '序号',
    width: 60,
  },
  {
    prop: 'conditionCode',
    label: '编码',
    width: 150,
    showOverflowTooltip: true,
  },
  {
    prop: 'name',
    label: '诊断名称',
    showOverflowTooltip: true,
  },
  {
    prop: 'ybNo',
    label: '医保编码',
    width: 140,
    showOverflowTooltip: true,
  },
  {
    prop: 'statusEnum',
    label: '状态',
    width: 90,
    slot: 'statusEnum',
  },
  {
    prop: 'description',
    label: '说明',
    showOverflowTooltip: true,
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 150,
  },
];

const formItems: FormItemConfig[] = [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入诊断名称或编码',
    clearable: true,
  },
];

const handleQuery = async (params: Record<string, unknown>) => {
  loading.value = true;
  try {
    const response = await pageDiagnosisCatalogApi({
      pageNo: typeof params.pageNo === 'number' ? params.pageNo : queryParams.value.pageNo,
      pageSize: typeof params.pageSize === 'number' ? params.pageSize : queryParams.value.pageSize,
      keyword:
        typeof params.keyword === 'string' && params.keyword
          ? params.keyword
          : queryParams.value.keyword || undefined,
      sourceEnum:
        typeof params.sourceEnum === 'string' && params.sourceEnum
          ? params.sourceEnum
          : queryParams.value.sourceEnum || undefined,
    });

    const responseData = response.data;
    if (responseData) {
      tableData.value = responseData.content || [];
      total.value = responseData.totalElements || 0;
    } else {
      tableData.value = [];
      total.value = 0;
    }
  } catch (error) {
    console.error('查询失败:', error);
    ElMessage.error('查询失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 50,
    keyword: '',
    sourceEnum: undefined,
  };
  handleQuery(queryParams.value);
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  handleQuery(queryParams.value);
};

// 侧边树数据：疾病分类
const treeData = ref<Array<{ id: number | string; label: string; value: number | string }>>([]);

const handleSideQuery = (node: TreeNodeData) => {
  const nodeValue = node.value as number | string | undefined;
  queryParams.value.sourceEnum =
    nodeValue !== undefined && nodeValue !== null ? String(nodeValue) : undefined;
  queryParams.value.pageNo = 1;
  handleQuery(queryParams.value);
};

const handleSelectionChange = (selection: DiagnosisCatalogItem[]) => {
  selectedRows.value = selection;
};

const handleAdd = () => {
  editFormData.value = {};
  editDialogVisible.value = true;
};

const handleEdit = (row: DiagnosisCatalogItem) => {
  editFormData.value = { ...row };
  editDialogVisible.value = true;
};

const handleDelete = async (row?: DiagnosisCatalogItem) => {
  const ids = row && row.id ? [row.id] : selectedRows.value.map((item) => item.id).filter(Boolean);

  if (ids.length === 0) {
    ElMessage.warning('请先选择要删除的数据');
    return;
  }

  try {
    await Promise.all(ids.map((id) => deleteDiagnosisCatalogApi(String(id))));
    ElMessage.success('删除成功');
    selectedRows.value = [];
    await handleQuery(queryParams.value);
    await nextTick();
    const gridApi = tableLayoutRef.value?.gridApi;
    if (gridApi) {
      const grid = gridApi.grid || gridApi;
      if (typeof grid.clearCheckboxRow === 'function') {
        grid.clearCheckboxRow();
      }
    }
  } catch (error) {
    console.error('删除失败:', error);
    ElMessage.error('删除失败，请稍后重试');
  }
};

const handleEnable = async () => {
  const ids = selectedRows.value.map((item) => item.id).filter(Boolean);
  if (ids.length === 0) {
    ElMessage.warning('请先选择要启用的数据');
    return;
  }
  try {
    await batchEnableDiagnosisCatalogApi(ids.map(String));
    ElMessage.success('启用成功');
    selectedRows.value = [];
    await handleQuery(queryParams.value);
    await nextTick();
    const gridApi = tableLayoutRef.value?.gridApi;
    if (gridApi) {
      const grid = gridApi.grid || gridApi;
      if (typeof grid.clearCheckboxRow === 'function') {
        grid.clearCheckboxRow();
      }
    }
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleDisable = async () => {
  const ids = selectedRows.value.map((item) => item.id).filter(Boolean);
  if (ids.length === 0) {
    ElMessage.warning('请先选择要停用的数据');
    return;
  }
  try {
    await batchDisableDiagnosisCatalogApi(ids.map(String));
    ElMessage.success('停用成功');
    selectedRows.value = [];
    await handleQuery(queryParams.value);
    await nextTick();
    const gridApi = tableLayoutRef.value?.gridApi;
    if (gridApi) {
      const grid = gridApi.grid || gridApi;
      if (typeof grid.clearCheckboxRow === 'function') {
        grid.clearCheckboxRow();
      }
    }
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const handleStart = async (row: DiagnosisCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await batchEnableDiagnosisCatalogApi([String(row.id)]);
    ElMessage.success('启用成功');
    await handleQuery(queryParams.value);
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleStop = async (row: DiagnosisCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await batchDisableDiagnosisCatalogApi([String(row.id)]);
    ElMessage.success('停用成功');
    await handleQuery(queryParams.value);
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const handleEditSuccess = () => {
  handleQuery(queryParams.value);
};

// 初始化：加载分类等信息 + 首次查询
const initPage = async () => {
  try {
    const res = await initDiagnosisCatalogApi();
    const data: DiagnosisCatalogInitResponse = res.data || ({} as DiagnosisCatalogInitResponse);
    diagnosisCategoryList.value =
      (data.diseaseCategoryList as Array<{ label: string; value: number | string }>) || [];
    statusFlagOptions.value =
      (data.statusFlagOptions as Array<{ label: string; value: number | string }>) || [];
    ybDiagTypes.value =
      (data.ybDiagTypes as Array<{ label: string; value: number | string }>) || [];
    treeData.value = diagnosisCategoryList.value.map((item) => ({
      label: item.label,
      value: item.value,
      id: String(item.value),
    }));
  } catch (error) {
    console.error('初始化失败:', error);
  }
  await handleQuery(queryParams.value);
};

initPage();
</script>

<template>
  <Page>
    <TableLayout
      ref="tableLayoutRef"
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-top-query="true"
      :show-side-query="true"
      :show-pagination="true"
      :sider-data="treeData"
      row-key="id"
      border
      stripe
      size="default"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
      @side-query="handleSideQuery"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
        <ElButton type="success" @click="handleEnable">批量启用</ElButton>
        <ElButton type="warning" @click="handleDisable">批量停用</ElButton>
      </template>

      <template #name="{ row }">
        <a
          href="javascript:void(0)"
          class="text-primary cursor-pointer hover:underline"
          @click="handleEdit(row)"
        >
          {{ row.name }}
        </a>
      </template>

      <template #statusEnum="{ row }">
        <StatusEnumTag :status-enum="row.statusEnum" :status-text="row.statusEnum_enumText" />
      </template>

      <template #action="{ row }">
        <ElButton type="primary" link @click="handleEdit(row)"> 编辑 </ElButton>
        <ElButton v-if="row.statusEnum !== 2" type="success" link @click="handleStart(row)">
          启用
        </ElButton>
        <ElButton v-else type="warning" link @click="handleStop(row)"> 停用 </ElButton>
        <ElButton type="danger" link @click="handleDelete(row)"> 删除 </ElButton>
      </template>
    </TableLayout>

    <EditDialog
      v-model:visible="editDialogVisible"
      :form-data="editFormData"
      @success="handleEditSuccess"
    />
  </Page>
</template>
