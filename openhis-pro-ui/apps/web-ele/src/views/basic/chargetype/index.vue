<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { ChargeType } from '#/api/core/system/charge-type';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import {
  CHARGE_TYPE_ACTIVE_FLAG,
  deleteChargeTypeApi,
  getChargeTypeListApi,
} from '#/api/core/system/charge-type';

import EditDialog from './edit-dialog.vue';

const allData = ref<ChargeType[]>([]);
const tableData = ref<ChargeType[]>([]);
const loading = ref(false);
const total = ref(0);
const editDialogVisible = ref(false);
const editFormData = ref<null | Partial<ChargeType>>(null);

const queryParams = reactive<{
  keyword?: string;
}>({
  keyword: '',
});

const formItems = ref<FormItemConfig[]>([
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入名称或编码',
    clearable: true,
  },
]);

const tableColumns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
    width: 60,
  },
  {
    prop: 'name',
    label: '名称',
    width: 200,
    showOverflowTooltip: true,
  },
  {
    prop: 'busNo',
    label: '编码',
    width: 200,
    showOverflowTooltip: true,
  },
  {
    prop: 'activeFlag',
    label: '状态',
    width: 100,
    slot: 'activeFlag',
    align: 'center',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
  },
];

const applyFilters = () => {
  let filtered = [...allData.value];

  const keyword = queryParams.keyword?.toLowerCase().trim() || '';
  if (keyword) {
    filtered = filtered.filter((item) => {
      return (
        item.name?.toLowerCase().includes(keyword) || item.code?.toLowerCase().includes(keyword)
      );
    });
  }

  tableData.value = filtered;
  total.value = filtered.length;
};

const loadAllData = async () => {
  loading.value = true;
  try {
    const response = await getChargeTypeListApi();
    allData.value = response || [];
    applyFilters();
  } catch {
    ElMessage.error('加载收费类型失败，请稍后重试');
    allData.value = [];
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = (params: { keyword?: string }) => {
  if (params.keyword !== undefined) {
    queryParams.keyword = params.keyword || '';
  }
  applyFilters();
};

const handleReset = () => {
  queryParams.keyword = '';
  applyFilters();
};

const handleAdd = () => {
  editFormData.value = null;
  editDialogVisible.value = true;
};

const handleEdit = (row: ChargeType) => {
  editFormData.value = { ...row };
  editDialogVisible.value = true;
};

const handleDelete = async (row: ChargeType) => {
  if (!row.id) {
    ElMessage.error('缺少收费类型ID');
    return;
  }
  try {
    await ElMessageBox.confirm('确定要删除该收费类型吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteChargeTypeApi(row.id);
    ElMessage.success('删除成功');
    loadAllData();
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      const errorMessage = error.msg || error.message || '删除失败，请稍后重试';
      ElMessage.error(errorMessage);
    }
  }
};

const handleEditSuccess = () => {
  loadAllData();
};

onMounted(() => {
  loadAllData();
});
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
      :show-top-query="true"
      :show-pagination="false"
      row-key="id"
      @query="handleQuery"
      @reset="handleReset"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>
      <template #activeFlag="{ row }">
        <ElTag
          v-if="row.activeFlag !== undefined"
          :type="row.activeFlag === CHARGE_TYPE_ACTIVE_FLAG.ACTIVE ? 'success' : 'info'"
        >
          {{ row.activeFlag === CHARGE_TYPE_ACTIVE_FLAG.ACTIVE ? '启用' : '停用' }}
        </ElTag>
        <span v-else>-</span>
      </template>
      <template #action="{ row }">
        <ElButton type="primary" link @click="handleEdit(row)">编辑</ElButton>
        <ElButton type="danger" link @click="handleDelete(row)">删除</ElButton>
      </template>
    </TableLayout>
    <EditDialog
      v-model:visible="editDialogVisible"
      :form-data="editFormData"
      @success="handleEditSuccess"
    />
  </Page>
</template>
