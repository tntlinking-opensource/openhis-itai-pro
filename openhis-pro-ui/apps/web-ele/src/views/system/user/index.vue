<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { ResponsibilityDept } from '#/api/core/common';
import type { RoleInfo } from '#/api/user/role';
import type { UserInfo, UserListParams } from '#/api/user/user';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { useAccess } from '@vben/access';

import { getTreeForResponsibilityDeptApi } from '#/api/core/common';
import { getRoleListApi } from '#/api/user/role';
import {
  deleteUserApi,
  disableUserApi,
  enableUserApi,
  getUserListApi,
  getUserRolesApi,
  unlockUserApi,
} from '#/api/user/user';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
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
  organizationId: undefined,
};

const queryParams = reactive<UserListParams>({ ...initQueryParams, roleIds: [] });

const tableData = ref<UserTableRow[]>([]);
const loading = ref(false);
const total = ref(0);
const roleOptions = ref<RoleSelectOption[]>([]);
const departmentOptions = ref<RoleSelectOption[]>([]);

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

/**
 * 判断节点是否为科室层级（排除医院根）。
 * 兼容数字 2、code=DEPARTMENT。
 */
function isDepartmentTypeEnum(typeEnum: unknown): boolean {
  if (typeEnum === 2 || typeEnum === '2') {
    // 科室层级 sortOrder
    return true;
  }
  if (typeof typeEnum === 'string' && typeEnum.toUpperCase() === 'DEPARTMENT') {
    // 科室 code
    return true;
  }
  // 医院根或其它非科室节点不进筛选
  return false;
}

/** 将责任科室树拍平为可搜索下拉（仅真实科室节点，主键保持 string） */
function flattenDepartmentOptions(nodes: ResponsibilityDept[] | undefined): RoleSelectOption[] {
  if (!nodes?.length) {
    // 空树：无科室可选
    return [];
  }
  const options: RoleSelectOption[] = [];
  // 深度优先：树里的每个科室节点都可作为出诊科室筛选值
  for (const node of nodes) {
    const id = preserveApiIdForRequest(node.id);
    if (id && isDepartmentTypeEnum(node.typeEnum) && node.name) {
      // 仅科室节点进入筛选项，医院根不作为出诊科室
      options.push({ label: node.name, value: id });
    }
    if (node.children?.length) {
      // 子树继续拍平，保证下级科室也能筛到出诊医生
      options.push(...flattenDepartmentOptions(node.children));
    }
  }
  return options;
}

/** 加载出诊科室下拉（与用户编辑「出诊科室」同源树） */
async function loadDepartmentOptions() {
  try {
    const data = unwrapApiList<ResponsibilityDept>(await getTreeForResponsibilityDeptApi());
    departmentOptions.value = flattenDepartmentOptions(data);
  } catch {
    // 科室树失败不阻断用户列表
    departmentOptions.value = [];
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
    prop: 'organizationId',
    label: '科室',
    placeholder: '请选择出诊科室',
    clearable: true,
    filterable: true,
    multiple: false,
    options: departmentOptions.value,
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

/** 按当前筛选条件分页加载用户列表 */
const loadData = async () => {
  loading.value = true;
  try {
    const roleIds = normalizeQueryRoleIds(queryParams.roleIds);
    let roleIdsForQuery: string[] | undefined;
    if (roleIds.length > 0) {
      // 未选角色时不传该字段，避免后端按空列表误筛
      roleIdsForQuery = roleIds;
    }
    const organizationId = preserveApiIdForRequest(queryParams.organizationId);
    const response = await getUserListApi({
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
      keyword: queryParams.keyword,
      isActive: queryParams.isActive,
      isLockedOut: queryParams.isLockedOut,
      roleIds: roleIdsForQuery,
      organizationId,
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

/** 点搜索：回到第一页并带上关键词/角色/出诊科室等条件 */
const handleQuery = (params: {
  isActive?: string;
  isLockedOut?: string;
  keyword?: string;
  organizationId?: string;
  roleIds?: string[];
}) => {
  queryParams.pageNo = 1;
  if (params.keyword !== undefined) {
    // 关键词允许空串，表示不按姓名/用户名过滤
    queryParams.keyword = params.keyword || '';
  }
  if (params.isActive !== undefined) {
    // 启用/停用；未选则不改写
    queryParams.isActive = params.isActive;
  }
  if (params.isLockedOut !== undefined) {
    // 锁定状态；未选则不改写
    queryParams.isLockedOut = params.isLockedOut;
  }
  if (params.roleIds !== undefined) {
    // 角色多选规范化为 string 主键，避免雪花精度丢失
    queryParams.roleIds = normalizeQueryRoleIds(params.roleIds);
  }
  if (params.organizationId !== undefined) {
    // 清空科室时 preserve 得到 undefined，即不按出诊岗收窄
    queryParams.organizationId = preserveApiIdForRequest(params.organizationId);
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
  void loadDepartmentOptions();
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
