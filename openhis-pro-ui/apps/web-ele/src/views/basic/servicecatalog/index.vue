<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { ServiceCatalogInitResponse, ServiceCatalogItem } from '#/api';

import { computed, nextTick, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import {
  deleteServiceCatalogApi,
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
  categoryCode: '',
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
    width: 60,
    align: 'center',
  },
  {
    prop: 'busNo',
    label: '编码',
    minWidth: 140,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'name',
    label: '项目名',
    slot: 'name',
    minWidth: 220,
    showOverflowTooltip: true,
  },
  {
    prop: 'statusEnum',
    label: '状态',
    width: 100,
    align: 'center',
    headerAlign: 'center',
    slot: 'statusEnum',
  },
  {
    prop: 'categoryCode_dictText',
    label: '分类',
    minWidth: 120,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'retailPrice',
    label: '价格',
    minWidth: 100,
    align: 'right',
    headerAlign: 'right',
    slot: 'retailPrice',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 180,
    align: 'center',
    headerAlign: 'center',
  },
];

function formatRetailPrice(price: unknown): string {
  if (price === null || price === undefined || price === '') {
    return '';
  }
  const num = Number(price);
  return Number.isFinite(num) ? num.toFixed(2) : String(price);
}

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入诊疗名称或编码',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'categoryCode',
    label: '目录分类',
    placeholder: '请选择目录分类',
    clearable: true,
    filterable: true,
    options: categoryCodeOptions.value,
  },
]);

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
    categoryCode: '',
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

const doDeleteByIds = async (ids: string[]) => {
  if (ids.length === 0) {
    ElMessage.warning('请先选择要删除的数据');
    return;
  }
  await ElMessageBox.confirm(
    `确定要删除选中的 ${ids.length} 条诊疗项目吗？删除后无法恢复！`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    },
  );
  await deleteServiceCatalogApi(ids);
  ElMessage.success('删除成功');
  await handleQuery(queryParams.value);
  selectedRows.value = [];
  await nextTick();
  const gridApi = tableLayoutRef.value?.gridApi;
  if (gridApi) {
    const grid = gridApi.grid || gridApi;
    if (typeof grid.clearCheckboxRow === 'function') {
      grid.clearCheckboxRow();
    }
  }
};

const handleDelete = async () => {
  const ids = selectedRows.value
    .map((row) => row.id)
    .filter(Boolean)
    .map(String);
  try {
    await doDeleteByIds(ids);
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      ElMessage.error('删除失败，请稍后重试');
    }
  }
};

const handleRowDelete = async (row: ServiceCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await doDeleteByIds([String(row.id)]);
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      ElMessage.error('删除失败，请稍后重试');
    }
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

/** 将 init 接口分类选项转为下拉框格式 */
function convertCategoryOptions(options: unknown): Array<{ label: string; value: string }> {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  return options
    .filter(
      (item): item is { label: string; value: number | string } =>
        typeof item === 'object' &&
        item !== null &&
        'label' in item &&
        'value' in item &&
        (typeof item.value === 'string' || typeof item.value === 'number'),
    )
    .map((item) => ({
      label: item.label,
      value: String(item.value),
    }));
}

const categoryCodeOptions = computed(() =>
  convertCategoryOptions(initOptions.value?.diagnosisCategoryOptions),
);

const loadInitOptions = async () => {
  try {
    const response = await serviceCatalogInitApi();
    const data: ServiceCatalogInitResponse = response.data || ({} as ServiceCatalogInitResponse);
    if (data) {
      initOptions.value = data;
    }
  } catch (error) {
    console.error('获取初始化选项失败:', error);
    ElMessage.error('获取初始化选项失败，请稍后重试');
  }
};

const handleEditSuccess = () => {
  editFormData.value = {};
  handleQuery(queryParams.value);
};

const initPage = async () => {
  await loadInitOptions();
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
      :show-pagination="true"
      row-key="id"
      border
      stripe
      size="default"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
        <ElButton type="success" @click="handleEnable">批量启用</ElButton>
        <ElButton type="warning" @click="handleDisable">批量停用</ElButton>
        <ElButton type="danger" @click="handleDelete">删除</ElButton>
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

      <template #retailPrice="{ row }">
        {{ formatRetailPrice(row.retailPrice) }}
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-1">
          <ElButton type="primary" link @click="handleEdit(row)">编辑</ElButton>
          <ElButton v-if="row.statusEnum !== 2" type="success" link @click="handleStart(row)">
            启用
          </ElButton>
          <ElButton v-else-if="row.statusEnum === 2" type="warning" link @click="handleStop(row)">
            停用
          </ElButton>
          <ElButton type="danger" link @click="handleRowDelete(row)">删除</ElButton>
        </div>
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
