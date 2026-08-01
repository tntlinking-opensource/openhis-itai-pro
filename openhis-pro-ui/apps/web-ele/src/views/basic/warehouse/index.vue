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

import { BASIC_TABLE_MAX_HEIGHT } from '../basic-table-max-height';
import EditDialog from './edit-dialog.vue';

defineOptions({
  name: 'WarehouseList',
});

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
    placeholder: '请输入名称、拼音、五笔、类型或状态',
    clearable: true,
  },
]);

const tableColumns = ref<TableColumn[]>([
  {
    label: '编号',
    prop: 'busNo',
    minWidth: 140,
    showOverflowTooltip: true,
    align: 'center',
    headerAlign: 'center',
  },
  {
    label: '名称',
    prop: 'name',
    showOverflowTooltip: true,
  },
  {
    label: '类型',
    prop: 'formEnumText',
    align: 'center',
    headerAlign: 'center',
  },
  {
    label: '上级科室',
    prop: 'organizationName',
    showOverflowTooltip: true,
    slot: 'organizationName',
  },
  {
    label: '状态',
    prop: 'statusEnumText',
    align: 'center',
    headerAlign: 'center',
    slot: 'status',
  },
  {
    label: '操作',
    prop: 'action',
    fixed: 'right',
    slot: 'action',
    align: 'center',
    headerAlign: 'center',
  },
]);

/** 判断库房/药房是否处于启用态 */
function isWarehouseActive(row: Warehouse) {
  return row.statusEnum === WAREHOUSE_STATUS_ENUM.ACTIVE;
}

/** 按当前查询条件拉取列表 */
async function loadData() {
  loading.value = true;
  try {
    const response = await getWarehouseListApi({
      keyword: queryParams.keyword?.trim() || undefined,
    });
    tableData.value = Array.isArray(response) ? response : [];
  } catch (error) {
    console.error('加载数据失败:', error);
    ElMessage.error('加载数据失败，请稍后重试');
    tableData.value = [];
  } finally {
    loading.value = false;
  }
}

/** 触发服务端检索 */
function handleQuery() {
  loadData();
}

/** 重置查询条件并刷新列表 */
function handleReset() {
  Object.assign(queryParams, initQueryParams);
  loadData();
}

/** 打开新增弹窗 */
function handleAdd() {
  dialogFormData.value = {};
  dialogVisible.value = true;
}

/** 保存成功后刷新列表 */
function handleDialogSuccess() {
  loadData();
}

/** 编辑前拉取详情 */
async function handleEdit(row: Warehouse) {
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
}

/** 废止库房/药房 */
async function handleDelete(row: Warehouse) {
  if (!row.id) {
    ElMessage.warning('缺少库房ID');
    return;
  }
  try {
    await ElMessageBox.confirm(`确定要删除该${row.formEnumText || '库房'}吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    loading.value = true;
    try {
      await deleteWarehouseApi({ id: row.id });
      ElMessage.success('删除成功');
      await loadData();
    } catch (error) {
      console.error('删除失败:', error);
      ElMessage.error('删除失败，请稍后重试');
    } finally {
      loading.value = false;
    }
  } catch {}
}

/** 启用或停用库房/药房 */
async function handleToggleStatus(row: Warehouse) {
  if (!row.id) {
    ElMessage.warning('缺少库房ID');
    return;
  }

  const willEnable = !isWarehouseActive(row);
  const actionText = willEnable ? '启用' : '停用';

  try {
    await ElMessageBox.confirm(`确定要${actionText}「${row.name || ''}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return;
  }

  loading.value = true;
  try {
    if (willEnable) {
      await enableWarehouseApi({ id: row.id });
    } else {
      await deactivateWarehouseApi({ id: row.id });
    }
    ElMessage.success(`${actionText}成功`);
    await loadData();
  } catch (error) {
    console.error(`${actionText}失败:`, error);
    ElMessage.error(`${actionText}失败，请稍后重试`);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadData();
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
      :max-height="BASIC_TABLE_MAX_HEIGHT"
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
          :type="isWarehouseActive(row) ? 'warning' : 'success'"
          link
          @click.stop="handleToggleStatus(row)"
        >
          {{ isWarehouseActive(row) ? '停用' : '启用' }}
        </ElButton>
        <ElButton type="danger" link @click.stop="handleDelete(row)"> 删除 </ElButton>
      </template>
      <template #status="{ row }">
        <ElTag :type="isWarehouseActive(row) ? 'success' : 'info'">
          {{ row.statusEnumText }}
        </ElTag>
      </template>
      <template #organizationName="{ row }">
        {{ row.organizationName || '—' }}
      </template>
    </TableLayout>
    <EditDialog
      v-model:visible="dialogVisible"
      :form-data="dialogFormData"
      @success="handleDialogSuccess"
    />
  </Page>
</template>
