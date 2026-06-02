<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { deleteBodyApi, getBodyListApi } from '#/api/basic/body';

import EditDialog from './edit-dialog.vue';

defineOptions({
  name: 'BodyList',
});

interface BodyListResponse {
  id?: string;
  name?: string;
  busNo?: string;
  parentId?: string;
  description?: string;
  [key: string]: unknown;
}

const allData = ref<BodyListResponse[]>([]);
const tableData = ref<BodyListResponse[]>([]);
const loading = ref(false);
const initQueryParams = {
  keyword: '',
};
const queryParams = reactive({ ...initQueryParams });

const formItems = ref([
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '请输入关键词',
    clearable: true,
  },
]);

const tableColumns = ref<TableColumn[]>([
  {
    type: 'index',
    label: '序号',
    width: 60,
  },
  {
    label: '业务编号',
    prop: 'busNo',
    width: 220,
  },
  {
    label: '名称',
    prop: 'name',
    width: 200,
  },
  {
    label: '操作',
    prop: 'action',
    fixed: 'right',
    slot: 'action',
    width: 200,
  },
]);

// 前端过滤数据
const filterData = () => {
  const keyword = queryParams.keyword?.toLowerCase().trim() || '';

  tableData.value = keyword
    ? allData.value.filter((item) => {
        return (
          item.name?.toLowerCase().includes(keyword) ||
          item.busNo?.toLowerCase().includes(keyword) ||
          item.description?.toLowerCase().includes(keyword)
        );
      })
    : allData.value;
};

const loadAllData = async () => {
  loading.value = true;
  try {
    const response = await getBodyListApi();
    allData.value = response;
    filterData();
  } catch (error) {
    console.error('加载数据失败:', error);
    ElMessage.error('加载数据失败，请稍后重试');
    allData.value = [];
    tableData.value = [];
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  filterData();
};

const handleReset = () => {
  Object.assign(queryParams, initQueryParams);
  filterData();
};

const dialogVisible = ref(false);
const dialogFormData = ref<Partial<BodyListResponse>>({});

const handleAdd = () => {
  dialogFormData.value = {};
  dialogVisible.value = true;
};

const handleDialogSuccess = () => {
  loadAllData();
};

const handleEdit = (row: BodyListResponse) => {
  dialogFormData.value = { ...row };
  dialogVisible.value = true;
};

const handleDelete = async (row: BodyListResponse) => {
  if (!row.id) {
    ElMessage.warning('缺少身体部位ID');
    return;
  }

  try {
    await ElMessageBox.confirm('确定要删除该身体部位吗？删除后无法恢复！', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteBodyApi({ value: row.id });
    ElMessage.success('删除成功');
    loadAllData();
  } catch (error: unknown) {
    if (error !== 'cancel') {
      const errorMessage =
        (error as { response?: { data?: { message?: string; msg?: string } } })?.response?.data
          ?.msg ||
        (error as { response?: { data?: { message?: string; msg?: string } } })?.response?.data
          ?.message ||
        (error as { message?: string })?.message ||
        (error as { msg?: string })?.msg ||
        '删除失败，请稍后重试';
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
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-top-query="true"
      :show-pagination="false"
      row-key="id"
      @query="handleQuery"
      @reset="handleReset"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>

      <template #action="{ row }">
        <ElButton type="primary" link @click.stop="handleEdit(row)"> 编辑 </ElButton>
        <ElButton type="danger" link @click.stop="handleDelete(row)"> 删除 </ElButton>
      </template>
    </TableLayout>

    <EditDialog
      v-model:visible="dialogVisible"
      :form-data="dialogFormData"
      @success="handleDialogSuccess"
    />
  </Page>
</template>
