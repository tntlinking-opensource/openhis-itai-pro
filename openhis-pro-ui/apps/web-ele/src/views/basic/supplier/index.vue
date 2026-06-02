<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { InitSupplierResponse, Supplier } from '#/api';
import type { PageParams } from '#/types/api-response';

import { computed, onActivated, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import {
  batchDisableSupplierApi,
  batchEnableSupplierApi,
  deleteSupplierApi,
  getSupplierListApi,
  initSupplierApi,
} from '#/api';

import EditDialog from './edit-dialog.vue';

const queryParams = ref<PageParams & { keyword?: string; typeEnum?: Array<number | string> }>({
  pageNo: 1,
  pageSize: 50,
});

const tableData = ref<Supplier[]>([]);
const loading = ref(false);
const total = ref(0);
const supplierTypeOptions = ref<Array<{ label: string; value: number }>>([]);
const organizationOptions = ref<Array<{ label: string; value: string }>>([]);

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
    label: '供应商编号',
    slot: 'busNo',
    width: 150,
  },
  {
    prop: 'name',
    label: '供应商名称',
  },
  {
    prop: 'typeEnum',
    label: '类型',
    slot: 'typeEnum',
  },
  {
    prop: 'address',
    label: '地址',
  },
  {
    prop: 'phone',
    label: '电话',
  },
  {
    prop: 'email',
    label: '邮箱',
  },
  {
    prop: 'activeFlag',
    label: '是否启用',
    slot: 'activeFlag',
    width: 100,
    align: 'center',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 150,
  },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '名称 | 编号 | 拼音 | 五笔',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'typeEnum',
    label: '类型',
    placeholder: '请选择类型',
    clearable: true,
    multiple: true,
    collapseTags: true,
    options: supplierTypeOptions.value.map((item) => ({
      label: item.label,
      value: item.value,
    })),
  },
]);

function getSupplierTypeLabel(typeEnum: unknown) {
  const normalizedValue =
    typeEnum === null || typeEnum === undefined ? undefined : Number(typeEnum as number | string);

  return supplierTypeOptions.value.find((item) => item.value === normalizedValue)?.label ?? '-';
}

const loadData = async () => {
  loading.value = true;
  try {
    const response = await getSupplierListApi(queryParams.value);
    tableData.value = response?.content || [];
    total.value = response?.totalCount || 0;
  } catch {
    ElMessage.error('查询失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = (params: Record<string, unknown>) => {
  // 搜索时回到第一页（TableLayout 会在 emit 时带 pageNo=1，但这里也同步到本地状态）
  queryParams.value.pageNo = 1;
  if (params.keyword !== undefined) {
    queryParams.value.keyword = typeof params.keyword === 'string' ? params.keyword : '';
  }
  if (params.typeEnum !== undefined) {
    queryParams.value.typeEnum = Array.isArray(params.typeEnum) ? params.typeEnum : [];
  }
  loadData();
};

const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 50,
  };
  loadData();
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  loadData();
};

const selectedRows = ref<Supplier[]>([]);

const handleSelectionChange = (selection: Supplier[]) => {
  selectedRows.value = selection;
};

const dialogVisible = ref(false);
const dialogFormData = ref<Partial<Supplier>>({});

const handleAdd = () => {
  dialogFormData.value = {};
  dialogVisible.value = true;
};

const handleEdit = (row: Supplier) => {
  dialogFormData.value = { ...row };
  dialogVisible.value = true;
};

const handleDialogSuccess = () => {
  loadData();
};

const handleStart = async (row: Supplier) => {
  if (!row.id) {
    ElMessage.warning('缺少供应商ID');
    return;
  }
  try {
    await batchEnableSupplierApi([row.id]);
    ElMessage.success('启用成功');
    loadData();
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleStop = async (row: Supplier) => {
  if (!row.id) {
    ElMessage.warning('缺少供应商ID');
    return;
  }
  try {
    await batchDisableSupplierApi([row.id]);
    ElMessage.success('停用成功');
    loadData();
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const handleBatchStart = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要启用的供应商');
    return;
  }

  const ids = selectedRows.value
    .filter((row) => row.activeFlag !== 1 && row.id)
    .map((row) => row.id);

  if (ids.length === 0) {
    ElMessage.warning('所选供应商中已全部启用，无需重复操作');
    return;
  }

  try {
    await batchEnableSupplierApi(ids);
    ElMessage.success(`成功启用 ${ids.length} 个供应商`);
    loadData();
    selectedRows.value = [];
  } catch (error) {
    console.error('批量启用失败:', error);
    ElMessage.error('批量启用失败，请稍后重试');
  }
};

const handleBatchStop = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要停用的供应商');
    return;
  }

  const ids = selectedRows.value
    .filter((row) => row.activeFlag === 1 && row.id)
    .map((row) => row.id);

  if (ids.length === 0) {
    ElMessage.warning('所选供应商中已全部停用，无需重复操作');
    return;
  }

  try {
    await batchDisableSupplierApi(ids);
    ElMessage.success(`成功停用 ${ids.length} 个供应商`);
    loadData();
    selectedRows.value = [];
  } catch (error) {
    console.error('批量停用失败:', error);
    ElMessage.error('批量停用失败，请稍后重试');
  }
};

const handleDelete = async (row: Supplier) => {
  try {
    await ElMessageBox.confirm('确定要删除该供应商吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });

    loading.value = true;
    try {
      await deleteSupplierApi(row.id);
      ElMessage.success('删除成功');
      loadData();
    } catch (error) {
      console.error('删除失败:', error);
      ElMessage.error('删除失败，请稍后重试');
    } finally {
      loading.value = false;
    }
  } catch {
    // ignore
  }
};

async function loadInitOptions() {
  try {
    const response = await initSupplierApi();
    const data: InitSupplierResponse = response || ({} as InitSupplierResponse);

    if (data.supplierTypeOptions) {
      supplierTypeOptions.value = data.supplierTypeOptions.map((item) => ({
        label: String(item.label ?? ''),
        value: Number(item.value),
      }));
    }

    if (data.organizationOptions) {
      organizationOptions.value = data.organizationOptions;
    }
  } catch (error) {
    console.error('加载供应商类型选项失败:', error);
  }
}

const initPage = async () => {
  await loadInitOptions();
  await loadData();
};

onActivated(() => {
  const needRefresh = sessionStorage.getItem('supplierRefresh');
  if (needRefresh === 'true') {
    sessionStorage.removeItem('supplierRefresh');
    loadData();
  }
});

initPage();
</script>

<template>
  <Page>
    <TableLayout
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-side-query="false"
      :show-pagination="true"
      :is-all-data="false"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
        <ElButton type="success" @click="handleBatchStart">批量启用</ElButton>
        <ElButton type="warning" @click="handleBatchStop">批量停用</ElButton>
      </template>

      <template #busNo="{ row }">
        <a
          href="javascript:void(0)"
          class="text-primary cursor-pointer hover:underline"
          @click.stop="handleEdit(row)"
        >
          {{ row.busNo }}
        </a>
      </template>

      <template #typeEnum="{ row }">
        {{ getSupplierTypeLabel(row.typeEnum) }}
      </template>

      <template #activeFlag="{ row }">
        <ElTag :type="row.activeFlag === 1 ? 'success' : 'info'">
          {{ row.activeFlag === 1 ? '是' : '否' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <ElButton type="primary" link @click.stop="handleEdit(row)"> 编辑 </ElButton>
        <ElButton v-if="row.activeFlag === 1" type="warning" link @click.stop="handleStop(row)">
          停用
        </ElButton>
        <ElButton
          v-else-if="row.activeFlag !== 1"
          type="success"
          link
          @click.stop="handleStart(row)"
        >
          启用
        </ElButton>
        <ElButton type="danger" link @click.stop="handleDelete(row)"> 删除 </ElButton>
      </template>
    </TableLayout>

    <EditDialog
      v-model:visible="dialogVisible"
      :form-data="dialogFormData"
      :supplier-type-options="supplierTypeOptions"
      :organization-options="organizationOptions"
      @success="handleDialogSuccess"
    />
  </Page>
</template>
