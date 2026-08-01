<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { RoleInfo } from '#/api/user/role';
import type { UserInfo, UserListParams } from '#/api/user/user';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { useAccess } from '@vben/access';

import { getRoleListApi } from '#/api/user/role';
import {
  deleteUserApi,
  disableUserApi,
  enableUserApi,
  getUserListApi,
  getUserRolesApi,
  unlockUserApi,
} from '#/api/user/user';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import ChangePasswordDialog from './components/change-password-dialog.vue';
import ImpersonateConfirmDialog from './components/impersonate-confirm-dialog.vue';
import UserFormDialog from './components/user-form-dialog.vue';

defineOptions({
  name: 'UserList',
});

const { hasAccessByCodes } = useAccess();
const canImpersonate = computed(() => hasAccessByCodes(['Identity.Users.Impersonate']));

/** 列表行：在用户基础信息上附带角色展示名 */
interface UserTableRow extends UserInfo {
  roleNames?: string;
}

interface RoleSelectOption {
  label: string;
  value: string;
}

const initQueryParams: UserListParams = {
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  isActive: undefined,
  isLockedOut: undefined,
  roleIds: [],
};

const queryParams = reactive<UserListParams>({ ...initQueryParams, roleIds: [] });

const tableData = ref<UserTableRow[]>([]);
const loading = ref(false);
const total = ref(0);
const roleOptions = ref<RoleSelectOption[]>([]);

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
    prop: 'userName',
    label: '用户名',
    width: 80,
  },
  {
    prop: 'name',
    label: '姓名',
    width: 80,
  },
  {
    prop: 'roleNames',
    label: '角色',
    slot: 'roleNames',
    minWidth: 140,
  },
  {
    prop: 'phoneNumber',
    label: '手机号',
    width: 180,
  },
  {
    prop: 'email',
    label: '邮箱',
    width: 180,
  },
  {
    prop: 'isActive',
    label: '状态',
    slot: 'isActive',
    align: 'center',
    width: 100,
  },
  {
    prop: 'isLockedOut',
    label: '锁定状态',
    slot: 'isLockedOut',
    align: 'center',
    width: 100,
  },
  {
    prop: 'accessFailedCount',
    label: '登录失败次数',
    align: 'center',
    width: 120,
  },
  {
    prop: 'lockoutEnd',
    label: '锁定结束时间',
    width: 180,
  },

  {
    prop: 'createdAt',
    label: '创建时间',
    width: 180,
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
    width: 280,
    showOverflowTooltip: false,
  },
];

/** 批量补全用户角色展示名 */
async function enrichUsersWithRoles(users: UserInfo[]): Promise<UserTableRow[]> {
  if (!users.length) {
    return [];
  }
  return Promise.all(
    users.map(async (user) => {
      try {
        const roles = await getUserRolesApi({
          value: preserveApiIdForRequest(user.id) ?? user.id,
        });
        const names = (Array.isArray(roles) ? roles : [])
          .map((role) => role.displayName || role.name)
          .filter(Boolean);
        return {
          ...user,
          roleNames: names.length ? names.join('、') : '-',
        };
      } catch {
        return {
          ...user,
          roleNames: '-',
        };
      }
    }),
  );
}

/** 从角色实体安全取出字符串主键，避免雪花 Long 精度丢失 */
function roleIdFromRole(role: Pick<RoleInfo, 'id'> | { id?: unknown }): string | undefined {
  return preserveApiIdForRequest(role.id);
}

/** 加载角色下拉选项（筛选用） */
async function loadRoleOptions() {
  try {
    const response = await getRoleListApi({
      pageNo: 1,
      pageSize: 1000,
    });
    const roles = response.content ?? [];
    // 仅保留能解析出主键的角色
    roleOptions.value = roles
      .map((role) => {
        const value = roleIdFromRole(role);
        if (!value) {
          return null;
        }
        return {
          label: role.displayName || role.name,
          value,
        };
      })
      .filter((item): item is RoleSelectOption => !!item);
  } catch {
    roleOptions.value = [];
  }
}

/** 规范化查询用的角色 id 列表 */
function normalizeQueryRoleIds(roleIds: unknown): string[] {
  if (!Array.isArray(roleIds)) {
    return [];
  }
  const normalized: string[] = [];
  for (const id of roleIds) {
    const preserved = preserveApiIdForRequest(id);
    if (preserved) {
      normalized.push(preserved);
    }
  }
  return normalized;
}

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入用户名或姓名',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'roleIds',
    label: '角色',
    placeholder: '请选择角色',
    clearable: true,
    multiple: true,
    collapseTags: true,
    filterable: true,
    options: roleOptions.value,
  },
  {
    type: 'select',
    prop: 'isActive',
    label: '状态',
    placeholder: '请选择状态',
    clearable: true,
    multiple: false,
    options: [
      { label: '启用', value: 1 },
      { label: '禁用', value: 0 },
    ],
  },
  {
    type: 'select',
    prop: 'isLockedOut',
    label: '锁定状态',
    placeholder: '请选择锁定状态',
    clearable: true,
    multiple: false,
    options: [
      { label: '锁定', value: 1 },
      { label: '未锁定', value: 0 },
    ],
  },
]);

const loadData = async () => {
  loading.value = true;
  try {
    const roleIds = normalizeQueryRoleIds(queryParams.roleIds);
    const response = await getUserListApi({
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
      keyword: queryParams.keyword,
      isActive: queryParams.isActive,
      isLockedOut: queryParams.isLockedOut,
      roleIds: roleIds.length > 0 ? roleIds : undefined,
    });
    tableData.value = await enrichUsersWithRoles(response.content ?? []);
    total.value = response.totalCount ?? 0;
  } catch {
    ElMessage.error('加载数据失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = (params: {
  isActive?: string;
  isLockedOut?: string;
  keyword?: string;
  roleIds?: string[];
}) => {
  queryParams.pageNo = 1;
  if (params.keyword !== undefined) {
    queryParams.keyword = params.keyword || '';
  }
  if (params.isActive !== undefined) {
    queryParams.isActive = params.isActive;
  }
  if (params.isLockedOut !== undefined) {
    queryParams.isLockedOut = params.isLockedOut;
  }
  if (params.roleIds !== undefined) {
    queryParams.roleIds = normalizeQueryRoleIds(params.roleIds);
  }
  void loadData();
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.pageSize = pagination.limit;
  queryParams.pageNo = pagination.page;
  void loadData();
};

const handleReset = () => {
  Object.assign(queryParams, { ...initQueryParams, roleIds: [] });
  void loadData();
};

const selectedRows = ref<UserInfo[]>([]);

const handleSelectionChange = (selection: Record<string, unknown>[] | UserInfo[]) => {
  selectedRows.value = selection as UserInfo[];
};

const dialogVisible = ref(false);
const currentUser = ref<null | UserInfo>(null);

const changePasswordDialogVisible = ref(false);
const currentPasswordUser = ref<null | UserInfo>(null);

const impersonateDialogVisible = ref(false);
const impersonateTargetUser = ref<null | UserInfo>(null);

const handleAdd = () => {
  currentUser.value = null;
  dialogVisible.value = true;
};

const handleEdit = (row: UserInfo) => {
  currentUser.value = row;
  dialogVisible.value = true;
};

const handleChangePassword = (row: UserInfo) => {
  currentPasswordUser.value = row;
  changePasswordDialogVisible.value = true;
};

const handleImpersonate = (row: UserInfo) => {
  if (!row.isActive) {
    ElMessage.warning('该用户已禁用，无法代理登录');
    return;
  }
  impersonateTargetUser.value = row;
  impersonateDialogVisible.value = true;
};

const handleDialogSuccess = () => {
  loadData();
};

const handleChangePasswordSuccess = () => {
  loadData();
};

const isAdminUser = (user: UserInfo) => {
  return user.userName === 'admin' || user.userName?.toLowerCase() === 'admin';
};

const handleUnlock = async (row: UserInfo) => {
  try {
    await ElMessageBox.confirm('确定要解锁该用户吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await unlockUserApi({ id: row.id });
    ElMessage.success('解锁成功');
    loadData();
  } catch (error: any) {
    if (error !== 'cancel') {
      const errorMessage =
        error?.response?.data?.msg ||
        error?.response?.data?.message ||
        error?.message ||
        error?.msg ||
        '解锁失败，请稍后重试';
      ElMessage.error(errorMessage);
    }
  }
};

const handleEnable = async (row: UserInfo) => {
  try {
    await ElMessageBox.confirm('确定要启用该用户吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await enableUserApi({ id: row.id });
    ElMessage.success('启用成功');
    loadData();
  } catch (error: any) {
    if (error !== 'cancel') {
      const errorMessage =
        error?.response?.data?.msg ||
        error?.response?.data?.message ||
        error?.message ||
        error?.msg ||
        '启用失败，请稍后重试';
      ElMessage.error(errorMessage);
    }
  }
};

const handleDisable = async (row: UserInfo) => {
  try {
    await ElMessageBox.confirm('确定要停用该用户吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await disableUserApi({ id: row.id });
    ElMessage.success('停用成功');
    loadData();
  } catch (error: any) {
    if (error !== 'cancel') {
      const errorMessage =
        error?.response?.data?.msg ||
        error?.response?.data?.message ||
        error?.message ||
        error?.msg ||
        '停用失败，请稍后重试';
      ElMessage.error(errorMessage);
    }
  }
};

const handleDelete = async (row: UserInfo) => {
  if (isAdminUser(row)) {
    ElMessage.warning('admin用户不允许删除');
    return;
  }
  try {
    await ElMessageBox.confirm('确定要删除该用户吗？删除后无法恢复！', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteUserApi({ id: row.id });
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

onMounted(() => {
  void loadRoleOptions();
  void loadData();
});
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <TableLayout
      class="h-full min-h-0 flex-1"
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-side-query="false"
      :show-pagination="true"
      row-key="id"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>

      <template #isActive="{ row }">
        <ElTag :type="row.isActive ? 'success' : 'info'">
          {{ row.isActive ? '启用' : '禁用' }}
        </ElTag>
      </template>

      <template #isLockedOut="{ row }">
        <ElTag :type="row.isLockedOut ? 'danger' : 'success'">
          {{ row.isLockedOut ? '已锁定' : '未锁定' }}
        </ElTag>
      </template>

      <template #roleNames="{ row }">
        <span class="text-[var(--el-text-color-regular)]">{{ row.roleNames || '-' }}</span>
      </template>

      <template #action="{ row }">
        <div class="user-list__actions inline-flex flex-wrap items-center gap-x-2 gap-y-1">
          <ElButton link type="primary" @click="handleEdit(row)">编辑</ElButton>
          <ElButton
            v-if="canImpersonate && row.isActive"
            link
            type="warning"
            @click="handleImpersonate(row)"
          >
            代理登录
          </ElButton>
          <ElButton v-if="!isAdminUser(row)" link type="primary" @click="handleChangePassword(row)">
            改密
          </ElButton>
          <ElButton v-if="row.isLockedOut" link type="info" @click="handleUnlock(row)">
            解锁
          </ElButton>
          <ElButton
            v-if="!row.isActive && !isAdminUser(row)"
            link
            type="success"
            @click="handleEnable(row)"
          >
            启用
          </ElButton>
          <ElButton
            v-if="row.isActive && !isAdminUser(row)"
            link
            type="warning"
            @click="handleDisable(row)"
          >
            停用
          </ElButton>
          <ElButton v-if="!isAdminUser(row)" link type="danger" @click="handleDelete(row)">
            删除
          </ElButton>
        </div>
      </template>
    </TableLayout>

    <UserFormDialog v-model="dialogVisible" :user="currentUser" @success="handleDialogSuccess" />
    <ChangePasswordDialog
      v-model="changePasswordDialogVisible"
      :user="currentPasswordUser"
      @success="handleChangePasswordSuccess"
    />
    <ImpersonateConfirmDialog v-model="impersonateDialogVisible" :user="impersonateTargetUser" />
  </Page>
</template>

<style scoped>
.user-list__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
