<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { Warehouse } from '#/api/basic/warehouse';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import {
  deactivateWarehouseApi,
  deleteWarehouseApi,
  enableWarehouseApi,
  getWarehouseApi,
  getWarehouseListApi,
  WAREHOUSE_STATUS_ENUM,
} from '#/api/basic/warehouse';

import EditDialog from './edit-dialog.vue';

defineOptions({
  name: 'WarehouseList',
});

const allData = ref<Warehouse[]>([]);
const tableData = ref<Warehouse[]>([]);
const loading = ref(false);
const initQueryParams = {
  keyword: '',
};
const queryParams = reactive({ ...initQueryParams });

const dialogVisible = ref(false);
const dialogFormData = ref<Partial<Warehouse>>({});

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
    label: '名称',
    prop: 'name',
    width: 200,
  },
  {
    label: '业务编号',
    prop: 'busNo',
    width: 200,
  },
  {
    label: '类型',
    prop: 'formEnumText',
    width: 120,
  },
  {
    label: '状态',
    prop: 'statusEnumText',
    width: 120,
    align: 'center',
    slot: 'status',
  },
  {
    label: '操作',
    prop: 'action',
    fixed: 'right',
    slot: 'action',
    minWidth: 200,
  },
]);

const filterData = () => {
  const keyword = queryParams.keyword?.toLowerCase().trim() || '';

  tableData.value = keyword
    ? allData.value.filter((item) => {
        const name = item.name?.toLowerCase() || '';
        const busNo = item.busNo?.toLowerCase() || '';
        const status = item.statusEnumText?.toLowerCase() || '';
        const type = item.formEnumText?.toLowerCase() || '';
        return (
          name.includes(keyword) ||
          busNo.includes(keyword) ||
          status.includes(keyword) ||
          type.includes(keyword)
        );
      })
    : allData.value;
};

const loadAllData = async () => {
  loading.value = true;
  try {
    const response = await getWarehouseListApi();
    const data = response;
    allData.value = Array.isArray(data) ? data : [];
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

const handleAdd = () => {
  dialogFormData.value = {};
  dialogVisible.value = true;
};

const handleDialogSuccess = () => {
  loadAllData();
};

const handleEdit = async (row: Warehouse) => {
  if (!row.id) {
    ElMessage.warning('缺少库房ID');
    return;
  }
  try {
    const response = await getWarehouseApi({ id: row.id });
    dialogFormData.value = response || row;
    dialogVisible.value = true;
  } catch (error) {
    console.error('获取库房详情失败:', error);
    ElMessage.error('获取库房详情失败，请稍后重试');
  }
};

const handleDelete = async (row: Warehouse) => {
  if (!row.id) {
    ElMessage.warning('缺少库房ID');
    return;
  }
  try {
    await ElMessageBox.confirm(`确定要删除该${row.formEnumText}吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    loading.value = true;
    try {
      await deleteWarehouseApi({ id: row.id });
      ElMessage.success('删除成功');
      loadAllData();
    } catch (error) {
      console.error('删除失败:', error);
      ElMessage.error('删除失败，请稍后重试');
    } finally {
      loading.value = false;
    }
  } catch {}
};

const handleToggleStatus = async (row: Warehouse) => {
  if (!row.id) {
    ElMessage.warning('缺少库房ID');
    return;
  }

  const isEnabled = String(row.statusEnum) === WAREHOUSE_STATUS_ENUM.ACTIVE;

  if (isEnabled) {
    loading.value = true;
    try {
      await deactivateWarehouseApi({ id: row.id });
      ElMessage.success('停用成功');
      await loadAllData();
    } catch (error) {
      console.error('停用失败:', error);
      ElMessage.error('停用失败，请稍后重试');
    } finally {
      loading.value = false;
    }
    return;
  }

  loading.value = true;
  try {
    await enableWarehouseApi({ id: row.id });
    ElMessage.success('启用成功');
    await loadAllData();
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  } finally {
    loading.value = false;
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
        <ElButton
          :type="String(row.statusEnum) === WAREHOUSE_STATUS_ENUM.ACTIVE ? 'warning' : 'success'"
          link
          @click.stop="handleToggleStatus(row)"
        >
          {{ String(row.statusEnum) === WAREHOUSE_STATUS_ENUM.ACTIVE ? '停用' : '启用' }}
        </ElButton>
        <ElButton type="danger" link @click.stop="handleDelete(row)"> 删除 </ElButton>
      </template>
      <template #status="{ row }">
        <ElTag :type="String(row.statusEnum) === WAREHOUSE_STATUS_ENUM.ACTIVE ? 'success' : 'info'">
          {{ row.statusEnumText }}
        </ElTag>
      </template>
    </TableLayout>
    <EditDialog
      v-model:visible="dialogVisible"
      :form-data="dialogFormData"
      @success="handleDialogSuccess"
    />
  </Page>
</template>
