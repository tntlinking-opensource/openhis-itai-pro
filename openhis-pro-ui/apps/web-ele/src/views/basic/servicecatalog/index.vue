<script lang="ts" setup>
import type { FormItemConfig, TableColumn, TreeNodeData } from '@vben-core/table-layout-ui';

import type { ServiceCatalogInitResponse, ServiceCatalogItem } from '#/api';

import { nextTick, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage } from 'element-plus';

import {
  pageServiceCatalogApi,
  serviceCatalogInitApi,
  startServiceCatalogApi,
  stopServiceCatalogApi,
} from '#/api';
import StatusEnumTag from '#/components/status-enum-tag.vue';

import EditDialog from './edit-dialog.vue';

const queryParams = ref({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
  name: '',
  categoryCode: undefined as string | undefined,
});

const tableData = ref<ServiceCatalogItem[]>([]);
const loading = ref(false);
const total = ref(0);

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
  },
  {
    prop: 'busNo',
    label: '业务编号',
  },
  {
    prop: 'name',
    label: '诊疗名称',
    slot: 'name',
  },
  {
    prop: 'categoryCode_dictText',
    label: '目录分类',
  },
  {
    prop: 'statusEnum',
    label: '状态',
    width: 80,
    align: 'center',
    slot: 'statusEnum',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 220,
  },
];

const formItems: FormItemConfig[] = [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入诊疗名称或编码',
    clearable: true,
  },
];

const handleQuery = async (params: Record<string, unknown>) => {
  loading.value = true;
  try {
    const response = await pageServiceCatalogApi({
      pageNo: typeof params.pageNo === 'number' ? params.pageNo : queryParams.value.pageNo,
      pageSize: typeof params.pageSize === 'number' ? params.pageSize : queryParams.value.pageSize,
      keyword:
        typeof params.keyword === 'string' && params.keyword
          ? params.keyword
          : queryParams.value.keyword || undefined,
      name:
        typeof params.name === 'string' && params.name
          ? params.name
          : queryParams.value.name || undefined,
      categoryCode:
        typeof params.categoryCode === 'string' && params.categoryCode
          ? params.categoryCode
          : queryParams.value.categoryCode || undefined,
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
    name: '',
    categoryCode: undefined,
  };
  handleQuery(queryParams.value);
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  handleQuery(queryParams.value);
};

const selectedRows = ref<ServiceCatalogItem[]>([]);
const tableLayoutRef = ref<InstanceType<typeof TableLayout> | null>(null);

const handleSelectionChange = (selection: ServiceCatalogItem[]) => {
  selectedRows.value = selection;
};

const handleSortChange = (_sortInfo: { column: unknown; order: string; prop: string }) => {};

const handleAdd = () => {
  editFormData.value = {};
  editDialogVisible.value = true;
};

const handleEnable = async () => {
  if (selectedRows.value.every((row) => row.statusEnum === 2)) {
    ElMessage.warning('请先选择要启用的数据');
    return;
  }

  try {
    const ids = selectedRows.value.map((row) => row.id).filter(Boolean);
    if (ids.length === 0) {
      ElMessage.warning('所选数据中没有有效的ID');
      return;
    }

    await startServiceCatalogApi(ids as string[]);
    ElMessage.success('启用成功');
    selectedRows.value = [];
    await handleQuery(queryParams.value);
    await nextTick();
    const gridApi = tableLayoutRef.value?.gridApi;
    if (gridApi) {
      const grid = gridApi.grid || gridApi;
      grid.clearCheckboxRow();
    }
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleDisable = async () => {
  if (selectedRows.value.every((row) => row.statusEnum === 3)) {
    ElMessage.warning('请先选择要禁用的数据');
    return;
  }

  try {
    const ids = selectedRows.value.map((row) => row.id).filter(Boolean);
    if (ids.length === 0) {
      ElMessage.warning('所选数据中没有有效的ID');
      return;
    }

    await stopServiceCatalogApi(ids as string[]);
    ElMessage.success('禁用成功');
    selectedRows.value = [];
    await handleQuery(queryParams.value);
    await nextTick();
    const gridApi = tableLayoutRef.value?.gridApi;
    if (gridApi) {
      const grid = gridApi.grid || gridApi;
      grid.clearCheckboxRow();
    }
    nextTick(() => {
      const gridApi = tableLayoutRef.value?.gridApi;
      if (gridApi && typeof gridApi.clearCheckboxRow === 'function') {
        gridApi.clearCheckboxRow();
      } else if (gridApi && typeof gridApi.clearCheckboxReserve === 'function') {
        gridApi.clearCheckboxReserve();
      }
    });
  } catch (error) {
    console.error('禁用失败:', error);
    ElMessage.error('禁用失败，请稍后重试');
  }
};

const handleStart = async (row: ServiceCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await startServiceCatalogApi([String(row.id)]);
    ElMessage.success('启用成功');
    handleQuery(queryParams.value);
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleStop = async (row: ServiceCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await stopServiceCatalogApi([String(row.id)]);
    ElMessage.success('停用成功');
    handleQuery(queryParams.value);
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const handleEdit = (row: ServiceCatalogItem) => {
  editFormData.value = { ...row };
  editDialogVisible.value = true;
};

const editDialogVisible = ref(false);
const editFormData = ref<Partial<ServiceCatalogItem>>({});

// 初始化选项数据
const initOptions = ref<ServiceCatalogInitResponse>({} as ServiceCatalogInitResponse);
const treeData = ref<Array<{ id: number | string; label: string; value: number | string }>>([]);

const convertOptionsToTree = (
  options: Array<{ children?: unknown[]; label: string; value: number | string }>,
): Array<{ id: number | string; label: string; value: number | string }> => {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  return options.map((item) => ({
    id: item.value,
    label: item.label,
    value: item.value,
    children: null,
  }));
};

const loadInitOptions = async () => {
  try {
    const response = await serviceCatalogInitApi();
    const data: ServiceCatalogInitResponse = response.data || ({} as ServiceCatalogInitResponse);
    if (data) {
      initOptions.value = data;
      treeData.value = convertOptionsToTree(
        (data.diagnosisCategoryOptions as Array<{ label: string; value: number | string }>) || [],
      );
    }
  } catch (error) {
    console.error('获取初始化选项失败:', error);
    ElMessage.error('获取初始化选项失败，请稍后重试');
  }
};

const handleSideQuery = (node: TreeNodeData) => {
  if (node) {
    const nodeId = node.id as string | undefined;
    const nodeValue = node.value as number | string | undefined;
    queryParams.value.categoryCode =
      nodeId === '__ALL__' ? undefined : nodeValue ? String(nodeValue) : undefined;
    queryParams.value.pageNo = 1;
    handleQuery(queryParams.value);
  }
};

const handleEditSuccess = () => {
  editFormData.value = {};
  handleQuery(queryParams.value);
};

const initPage = async () => {
  await loadInitOptions();
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
      @sort-change="handleSortChange"
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
        <ElButton v-else-if="row.statusEnum === 2" type="warning" link @click="handleStop(row)">
          停用
        </ElButton>
        <ElButton type="danger" link @click="() => ElMessage.warning(`删除 ${row.name || row.id}`)">
          删除
        </ElButton>
      </template>
    </TableLayout>

    <EditDialog
      v-model:visible="editDialogVisible"
      :form-data="editFormData"
      :init-options="initOptions"
      @success="handleEditSuccess"
    />
  </Page>
</template>
