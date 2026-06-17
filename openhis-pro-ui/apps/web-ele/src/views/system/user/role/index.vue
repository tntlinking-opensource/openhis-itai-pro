<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { RoleInfo, RoleListParams } from '#/api/user/role';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { deleteRoleApi, getRoleListApi } from '#/api/user/role';

import RoleFormDialog from './components/role-form-dialog.vue';

defineOptions({
  name: 'RoleList',
});

const queryParams = ref<RoleListParams>({
  pageNo: 1,
  pageSize: 50,
});

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  loadData();
};

const tableData = ref<RoleInfo[]>([]);
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
    prop: 'displayName',
    label: '角色名称',
    minWidth: 160,
  },
  {
    prop: 'isStatic',
    label: '角色类型',
    slot: 'roleType',
    align: 'center',
    width: 110,
  },
  {
    prop: 'description',
    label: '描述',
    minWidth: 200,
  },
  {
    prop: 'createdBy',
    label: '创建人',
    width: 120,
  },
  {
    prop: 'createdAt',
    label: '创建时间',
    width: 180,
  },
  {
    prop: 'updatedBy',
    label: '更新人',
    width: 120,
  },
  {
    prop: 'updatedAt',
    label: '更新时间',
    width: 180,
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 180,
  },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入角色名称',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'isStatic',
    label: '角色类型',
    placeholder: '请选择角色类型',
    clearable: true,
    multiple: false,
    options: [
      { label: '内置', value: 'true' },
      { label: '自定义', value: 'false' },
    ],
  },
]);

const loadData = async () => {
  loading.value = true;
  try {
    const response = await getRoleListApi({
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      keyword: queryParams.value.keyword,
      isStatic: queryParams.value.isStatic,
    });
    tableData.value = response.content;
    total.value = response.totalCount;
  } catch {
    ElMessage.error('加载数据失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = (params: { isStatic?: string; keyword?: string }) => {
  if (params.keyword !== undefined) {
    queryParams.value.keyword = params.keyword || '';
  }
  if (params.isStatic !== undefined) {
    queryParams.value.isStatic = params.isStatic;
  }
  queryParams.value.pageNo = 1;
  loadData();
};

const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 50,
    keyword: '',
    isStatic: undefined,
  };
  loadData();
};

const selectedRows = ref<RoleInfo[]>([]);

const handleSelectionChange = (selection: Record<string, unknown>[] | RoleInfo[]) => {
  selectedRows.value = selection as RoleInfo[];
};

const dialogVisible = ref(false);
const currentRole = ref<null | RoleInfo>(null);

const handleAdd = () => {
  currentRole.value = null;
  dialogVisible.value = true;
};

const handleEdit = (row: RoleInfo) => {
  currentRole.value = row;
  dialogVisible.value = true;
};

const handleDialogSuccess = () => {
  loadData();
};

const handleDelete = async (row: RoleInfo) => {
  if (row.isStatic) {
    ElMessage.warning('内置角色不能删除');
    return;
  }
  if (!row.id) {
    ElMessage.warning('缺少角色ID');
    return;
  }

  try {
    await ElMessageBox.confirm('确定删除该角色吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteRoleApi(row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error?.msg || error?.message || '删除失败，请稍后重试');
    }
  }
};

onMounted(() => {
  loadData();
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
      :show-pagination="true"
      :is-all-data="true"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>

      <template #roleType="{ row }">
        <ElTag :type="row.isStatic ? 'warning' : 'info'">
          {{ row.isStatic ? '内置' : '自定义' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <ElButton link type="primary" @click="handleEdit(row)">编辑</ElButton>
        <ElButton v-if="!row.isStatic" link type="danger" @click="handleDelete(row)">
          删除
        </ElButton>
      </template>
    </TableLayout>

    <RoleFormDialog v-model="dialogVisible" :role="currentRole" @success="handleDialogSuccess" />
  </Page>
</template>
