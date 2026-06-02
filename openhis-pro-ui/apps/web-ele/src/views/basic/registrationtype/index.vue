<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { RegistrationType } from '#/api/core/system/registration';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import {
  deleteRegistrationTypeApi,
  getRegistrationTypeListApi,
  REGISTRATION_TYPE_ACTIVE_FLAG,
} from '#/api/core/system/registration';

import EditDialog from './edit-dialog.vue';

const allData = ref<RegistrationType[]>([]);
const tableData = ref<RegistrationType[]>([]);
const loading = ref(false);
const total = ref(0);
const editDialogVisible = ref(false);
const editFormData = ref<null | Partial<RegistrationType>>(null);

const queryParams = reactive<{
  activeFlag?: number | undefined;
  keyword?: string;
}>({
  keyword: '',
  activeFlag: undefined,
});

const formItems = ref<FormItemConfig[]>([
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入名称、医保编码或业务编码',
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
    minWidth: 200,
    showOverflowTooltip: true,
  },
  {
    prop: 'ybNo',
    label: '医保编码',
    width: 150,
  },
  {
    prop: 'busNo',
    label: '业务编码',
    width: 150,
  },
  {
    prop: 'amount',
    label: '金额',
    width: 100,
  },
  {
    prop: 'activeFlag',
    label: '状态',
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
    align: 'center',
  },
];

const applyFilters = () => {
  let filtered = [...allData.value];

  const keyword = queryParams.keyword?.toLowerCase().trim() || '';
  if (keyword) {
    filtered = filtered.filter((item) => {
      return (
        item.name?.toLowerCase().includes(keyword) ||
        item.ybNo?.toLowerCase().includes(keyword) ||
        item.busNo?.toLowerCase().includes(keyword)
      );
    });
  }

  tableData.value = filtered;
  total.value = filtered.length;
};

const loadAllData = async () => {
  loading.value = true;
  try {
    const response = await getRegistrationTypeListApi();
    allData.value = response || [];
    applyFilters();
  } catch {
    ElMessage.error('加载挂号类型失败，请稍后重试');
    allData.value = [];
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = (params: { activeFlag?: number; keyword?: string }) => {
  if (params.keyword !== undefined) {
    queryParams.keyword = params.keyword || '';
  }
  if (params.activeFlag !== undefined) {
    queryParams.activeFlag = params.activeFlag;
  }
  applyFilters();
};

const handleReset = () => {
  queryParams.keyword = '';
  queryParams.activeFlag = undefined;
  applyFilters();
};

const handleAdd = () => {
  editFormData.value = null;
  editDialogVisible.value = true;
};

const handleEdit = (row: RegistrationType) => {
  editFormData.value = { ...row };
  editDialogVisible.value = true;
};

const handleEditSuccess = () => {
  loadAllData();
};

const handleDelete = async (row: RegistrationType) => {
  if (!row.id) {
    ElMessage.error('缺少挂号类型ID');
    return;
  }
  try {
    await ElMessageBox.confirm('确定要删除该挂号类型吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteRegistrationTypeApi(row.id);
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
        <ElTag :type="row.activeFlag === REGISTRATION_TYPE_ACTIVE_FLAG.ACTIVE ? 'success' : 'info'">
          {{ row.activeFlag === REGISTRATION_TYPE_ACTIVE_FLAG.ACTIVE ? '启用' : '停用' }}
        </ElTag>
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
