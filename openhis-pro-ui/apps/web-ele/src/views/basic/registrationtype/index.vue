<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { RegistrationType } from '#/api/core/system/registration';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import {
  activateRegistrationTypeApi,
  deactivateRegistrationTypeApi,
  deleteRegistrationTypeApi,
  getRegistrationTypeListApi,
  isRegistrationTypeActive,
} from '#/api/core/system/registration';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

import { BASIC_TABLE_MAX_HEIGHT } from '../basic-table-max-height';
import EditDialog from './edit-dialog.vue';

defineOptions({
  name: 'RegistrationTypeList',
});

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
    placeholder: '名称、诊查费或业务编码',
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
    minWidth: 160,
    showOverflowTooltip: true,
  },
  {
    prop: 'activityNames',
    label: '诊查费',
    minWidth: 220,
    showOverflowTooltip: true,
  },
  {
    prop: 'busNo',
    label: '业务编码',
    width: 140,
  },
  {
    prop: 'amount',
    label: '金额',
    width: 100,
    align: 'right',
    formatter: (_row, _column, cellValue) => {
      if (cellValue === null || cellValue === undefined || cellValue === '') {
        return formatMoneyDisplay(0);
      }
      return formatMoneyDisplay(cellValue);
    },
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
    width: 220,
    align: 'center',
  },
];

/** 按关键词过滤本地列表（名称 / 诊查费摘要 / 业务编码） */
const applyFilters = () => {
  let filtered = [...allData.value];

  const keyword = queryParams.keyword?.toLowerCase().trim() || '';
  if (keyword) {
    // 名称、诊查费绑定摘要、业务编码模糊匹配
    filtered = filtered.filter((item) => {
      return (
        item.name?.toLowerCase().includes(keyword) ||
        item.activityNames?.toLowerCase().includes(keyword) ||
        item.busNo?.toLowerCase().includes(keyword)
      );
    });
  }

  tableData.value = filtered;
  total.value = filtered.length;
};

/** 拉取挂号类型全量列表并应用筛选 */
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

/** 查询区触发筛选 */
const handleQuery = (params: { activeFlag?: number; keyword?: string }) => {
  if (params.keyword !== undefined) {
    queryParams.keyword = params.keyword || '';
  }
  if (params.activeFlag !== undefined) {
    queryParams.activeFlag = params.activeFlag;
  }
  applyFilters();
};

/** 重置查询条件 */
const handleReset = () => {
  queryParams.keyword = '';
  queryParams.activeFlag = undefined;
  applyFilters();
};

/** 打开新增弹窗 */
const handleAdd = () => {
  editFormData.value = null;
  editDialogVisible.value = true;
};

/** 打开编辑弹窗 */
const handleEdit = (row: RegistrationType) => {
  editFormData.value = { ...row };
  editDialogVisible.value = true;
};

/** 新增/编辑成功后刷新 */
const handleEditSuccess = () => {
  loadAllData();
};

/** 启用或停用挂号类型 */
const handleToggleStatus = async (row: RegistrationType) => {
  if (!row.id) {
    ElMessage.error('缺少挂号类型ID');
    return;
  }

  const willEnable = !isRegistrationTypeActive(row);
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
      await activateRegistrationTypeApi(row.id);
    } else {
      await deactivateRegistrationTypeApi(row.id);
    }
    ElMessage.success(`${actionText}成功`);
    await loadAllData();
  } catch (error: unknown) {
    console.error(`${actionText}失败:`, error);
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err.msg || err.message || `${actionText}失败，请稍后重试`);
  } finally {
    loading.value = false;
  }
};

/** 删除挂号类型（后端启用态会先停用再作废） */
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
  } catch (error: unknown) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      const err = error as { message?: string; msg?: string };
      ElMessage.error(err.msg || err.message || '删除失败，请稍后重试');
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
      :max-height="BASIC_TABLE_MAX_HEIGHT"
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
        <ElTag :type="isRegistrationTypeActive(row) ? 'success' : 'warning'">
          {{ isRegistrationTypeActive(row) ? '启用' : '停用' }}
        </ElTag>
      </template>
      <template #action="{ row }">
        <ElButton type="primary" link @click="handleEdit(row)">编辑</ElButton>
        <ElButton
          :type="isRegistrationTypeActive(row) ? 'warning' : 'success'"
          link
          @click="handleToggleStatus(row)"
        >
          {{ isRegistrationTypeActive(row) ? '停用' : '启用' }}
        </ElButton>
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
