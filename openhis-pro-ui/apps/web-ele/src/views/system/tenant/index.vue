<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { TenantInfo, TenantListParams } from '#/api/core/system/tenant';
import type { UserInfo } from '#/api/user/user';

import { computed, onMounted, ref } from 'vue';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import {
  assignUsersToTenantApi,
  deleteTenantApi,
  getTenantListApi,
  getTenantUsersApi,
  removeUsersApi,
} from '#/api/core/system/tenant';
import UserSelectDialog from '#/components/select-user-dialog.vue';

import EditDialog from './edit-dialog.vue';

defineOptions({
  name: 'TenantList',
});

const queryParams = ref<TenantListParams>({
  pageNo: 1,
  pageSize: 50,
  code: '',
  keyword: '',
  isActive: undefined,
});

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  loadData();
};

const tableData = ref<TenantInfo[]>([]);
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
    prop: 'code',
    label: '租户编码',
    width: 150,
  },
  {
    prop: 'name',
    label: '租户名称',
    width: 200,
  },
  {
    prop: 'description',
    label: '描述',
    minWidth: 200,
  },
  {
    prop: 'isActive',
    label: '状态',
    slot: 'isActive',
    align: 'center',
    width: 100,
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
    width: 150,
  },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'code',
    label: '租户编码',
    placeholder: '请输入租户编码',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入租户名称或描述',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'isActive',
    label: '状态',
    placeholder: '请选择状态',
    clearable: true,
    multiple: false,
    options: [
      { label: '启用', value: 'true' },
      { label: '禁用', value: 'false' },
    ],
  },
]);

const loadData = async () => {
  loading.value = true;
  try {
    const response = await getTenantListApi({
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      code: queryParams.value.code,
      keyword: queryParams.value.keyword,
      isActive: queryParams.value.isActive,
    });
    tableData.value = response.content || [];
    total.value = response.totalCount || 0;
    selectedTenant.value = tableData.value[0] || null;
    loadUserList();
  } catch {
    ElMessage.error('加载数据失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = (params: { code?: string; isActive?: string; keyword?: string }) => {
  if (params.code !== undefined) {
    queryParams.value.code = params.code || '';
  }
  if (params.keyword !== undefined) {
    queryParams.value.keyword = params.keyword || '';
  }
  if (params.isActive !== undefined) {
    queryParams.value.isActive = params.isActive;
  }
  queryParams.value.pageNo = 1;
  loadData();
};

const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 50,
    code: '',
    keyword: '',
    isActive: undefined,
  };
  loadData();
};

const dialogVisible = ref(false);
const dialogFormData = ref<Partial<TenantInfo>>({});

const handleAdd = () => {
  dialogFormData.value = {};
  dialogVisible.value = true;
};

const handleEdit = (row: TenantInfo) => {
  dialogFormData.value = { ...row };
  dialogVisible.value = true;
};

const handleDelete = async (row: TenantInfo) => {
  if (!row.id) {
    ElMessage.warning('缺少租户ID');
    return;
  }

  try {
    await ElMessageBox.confirm('确定要删除该租户吗？删除后无法恢复！', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteTenantApi(row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch (error: any) {
    if (error !== 'cancel') {
      const errorMessage =
        error?.response?.data?.msg ||
        error?.response?.data?.message ||
        error?.message ||
        error?.msg ||
        '删除失败，请稍后重试';
      ElMessage.error(errorMessage);
    }
  }
};

const handleDialogSuccess = () => {
  loadData();
};

const selectedTenant = ref<null | TenantInfo>(null);

const userList = ref<UserInfo[]>([]);
const userLoading = ref(false);
const selectedUserIds = ref<string[]>([]);

const selectedUserIdsForAdd = ref<string[]>([]);
const userSelectDialogRef = ref<InstanceType<typeof UserSelectDialog> | null>(null);

const loadUserList = async () => {
  if (!selectedTenant.value?.id) {
    userList.value = [];
    return;
  }

  userLoading.value = true;
  try {
    const response = await getTenantUsersApi({
      id: selectedTenant.value?.id,
    });
    userList.value = response;
    selectedUserIdsForAdd.value = userList.value.map((user) => user.id);
  } catch {
    ElMessage.error('加载用户列表失败');
    userList.value = [];
  } finally {
    userLoading.value = false;
  }
};

const getRowClassName = ({ row }: { row: Record<string, any>; rowIndex: number }) => {
  if (selectedTenant.value && row.id === selectedTenant.value.id) {
    return 'selected-tenant-row';
  }
  return '';
};

const handleSelectTenant = (row: Record<string, any>) => {
  selectedTenant.value = row as TenantInfo;
  selectedUserIds.value = [];
  loadUserList();
};

const handleAddUser = async () => {
  if (!selectedTenant.value?.id) {
    ElMessage.warning('请先选择租户');
    return;
  }
  if (userSelectDialogRef.value) {
    userSelectDialogRef.value.openDialog();
  } else {
    console.error('userSelectDialogRef 未找到');
  }
};

const handleAddUserChange = async (users: null | UserInfo | UserInfo[]) => {
  if (!users) {
    return;
  }

  const userList = Array.isArray(users) ? users : [users];
  const tenantId = selectedTenant.value?.id;

  if (!tenantId) {
    ElMessage.warning('请先选择租户');
    return;
  }

  try {
    await assignUsersToTenantApi({ tenantId, userIds: userList.map((user) => user.id.toString()) });
    ElMessage.success(`成功添加 ${userList.length} 个用户到租户`);
    selectedUserIdsForAdd.value = [];
    loadUserList();
  } catch (error: any) {
    const errorMessage = error?.msg || '添加用户失败，请稍后重试';
    ElMessage.error(errorMessage);
  }
};

const handleRemoveUser = async () => {
  if (!selectedTenant.value?.id || selectedUserIds.value.length === 0) {
    ElMessage.warning('请先选择要移除的用户');
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确定要移除选中的 ${selectedUserIds.value.length} 个用户吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      },
    );

    await removeUsersApi({ tenantId: selectedTenant.value?.id, userIds: selectedUserIds.value });

    ElMessage.success('移除用户成功');
    selectedUserIds.value = [];
    loadUserList();
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('移除用户失败');
    }
  }
};

const handleRemoveSingleUser = async (user: UserInfo) => {
  if (!selectedTenant.value?.id) {
    ElMessage.warning('请先选择租户');
    return;
  }

  try {
    await ElMessageBox.confirm(`确定要移除用户 "${user.name || user.userName}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });

    await removeUsersApi({ tenantId: selectedTenant.value.id, userIds: [user.id] });

    ElMessage.success('移除用户成功');
    loadUserList();
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('移除用户失败');
    }
  }
};

// 用户表格列配置
const userTableColumns: TableColumn[] = [
  {
    type: 'selection',
    fixed: 'left',
    width: 50,
    align: 'center',
  },
  {
    prop: 'tenant',
    label: '当前租户',
    width: 120,
    formatter: () => {
      return selectedTenant.value?.name || '';
    },
  },
  {
    prop: 'userName',
    label: '用户名',
    width: 120,
  },
  {
    prop: 'name',
    label: '姓名',
    width: 120,
  },
  {
    prop: 'action',
    label: '操作',
    width: 80,
    fixed: 'right',
    align: 'center',
    slot: 'action',
  },
];

// 监听选中用户变化
const handleUserSelectionChange = (selection: UserInfo[]) => {
  selectedUserIds.value = selection.map((user) => user.id);
};

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="flex flex-row p-2">
    <div class="bg-background-card w-[66.66%] p-2">
      <TableLayout
        :table-data="tableData"
        :loading="loading"
        :total="total"
        :query-params="queryParams"
        :form-items="formItems"
        :table-columns="tableColumns"
        :show-side-query="false"
        :show-top-query="true"
        :show-pagination="true"
        :highlight-current-row="true"
        row-key="id"
        :row-class-name="getRowClassName"
        @query="handleQuery"
        @reset="handleReset"
        @pagination="handlePagination"
        @row-click="handleSelectTenant"
      >
        <template #toolbar-actions>
          <ElButton type="primary" @click="handleAdd">新增</ElButton>
        </template>

        <template #isActive="{ row }">
          <ElTag :type="row.isActive ? 'success' : 'warning'">
            {{ row.isActive ? '启用' : '禁用' }}
          </ElTag>
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
    </div>
    <div class="bg-background-card ml-2 w-[33.33%] p-4">
      <TableLayout
        :table-data="userList"
        :loading="userLoading"
        :total="userList.length"
        :query-params="{ pageNo: 1, pageSize: 20 }"
        :table-columns="userTableColumns"
        :show-top-query="false"
        :show-side-query="false"
        :show-pagination="false"
        row-key="id"
        @selection-change="handleUserSelectionChange as (selection: Record<string, any>[]) => any"
      >
        <template #toolbar-actions>
          <ElButton type="success" @click="handleAddUser"> + 添加用户 </ElButton>
          <ElButton type="danger" @click="handleRemoveUser"> 移除用户 </ElButton>
        </template>
        <template #action="{ row }">
          <ElButton type="danger" link @click.stop="handleRemoveSingleUser(row)"> 移除 </ElButton>
        </template>
      </TableLayout>
      <UserSelectDialog
        ref="userSelectDialogRef"
        v-model="selectedUserIdsForAdd"
        :multiple="true"
        :show-input="false"
        placeholder="请选择要添加的用户"
        dialog-title="选择用户"
        @change="handleAddUserChange"
      />
    </div>
  </div>
</template>

<style scoped>
:deep(.selected-tenant-row) {
  background-color: hsl(var(--accent)) !important;
}

:deep(.selected-tenant-row:hover) {
  background-color: hsl(var(--accent-hover)) !important;
}
</style>
