<script lang="ts" setup>
import type { Menu } from '#/api/core/system/menu.types';
import type { VbenFormSchema } from '#/adapter/form';
import type { CreateRoleParams, RoleInfo, UpdateRoleParams } from '#/api/user/role';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElCheckbox, ElCheckboxGroup, ElMessage, ElTree } from 'element-plus';

import { useVbenForm, z } from '#/adapter/form';
import { getMenuGrantTreeApi } from '#/api/core/system/menu';
import { BUILTIN_ROLE_STAFF_SCOPES, STAFF_SCOPE_OPTIONS } from '#/api/basic/staff';
import { getPermissionsByRoleApi } from '#/api/user/permission';
import {
  assignPermissionsApi,
  assignStaffScopesApi,
  createRoleApi,
  getStaffScopesApi,
  updateRoleApi,
} from '#/api/user/role';
import { resolveGrantableMenuPermissionName } from '#/utils/menu-navigation-permission';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

interface Props {
  modelValue: boolean;
  role?: null | RoleInfo;
}

const props = withDefaults(defineProps<Props>(), {
  role: null,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'success'): void;
}>();

const isEdit = computed(() => !!props.role);

/** 系统管理员角色不可编辑菜单与声明 */
const isSystemAdminRole = computed(() => {
  if (!props.role) {
    return false;
  }
  return props.role.isStatic && (props.role.name === 'admin' || props.role.code === 'ROLE_ADMIN');
});

const originalData = ref<Record<string, unknown>>({});

const roleMenuTree = ref<Menu[]>([]);
const selectedMenuIds = ref<string[]>([]);
const menuTreeRef = ref<InstanceType<typeof ElTree>>();

const selectedStaffScopes = ref<string[]>([]);

/** 内置角色仅展示模板内管理能力；自定义角色可选全部（已去掉护士管理科室） */
const staffScopeOptionsForForm = computed(() => {
  if (isEdit.value && props.role?.isStatic && props.role.code) {
    const allowed = BUILTIN_ROLE_STAFF_SCOPES[props.role.code];
    if (allowed !== undefined) {
      return STAFF_SCOPE_OPTIONS.filter((item) => allowed.includes(item.value));
    }
  }
  return STAFF_SCOPE_OPTIONS;
});

/** 编辑回显时的初始权限名 / 声明，用于提交时跳过未变更的接口 */
const initialPermissionNames = ref<string[]>([]);
const initialStaffScopes = ref<string[]>([]);

/** 扁平化菜单树，便于按 id / permissionName 检索 */
function flattenMenus(menus: Menu[]): Menu[] {
  const result: Menu[] = [];
  const walk = (nodes: Menu[]) => {
    for (const node of nodes) {
      result.push(node);
      if (node.children?.length) {
        walk(node.children);
      }
    }
  };
  walk(menus);
  return result;
}

/** 目录、隐藏、停用菜单不参与 Navigation 授权 */
function isGrantableMenu(menu: Menu): boolean {
  if (menu.menuType === 'directory') {
    return false;
  }
  if (menu.isHidden || !menu.isEnabled) {
    return false;
  }
  const permissionName = resolveGrantableMenuPermissionName(menu);
  if (!permissionName) {
    return false;
  }
  if (permissionName.startsWith('Navigation.__folder__.')) {
    return false;
  }
  return true;
}

/** 根据勾选的菜单 id 收集 Navigation 权限名（菜单驱动授权） */
function collectPermissionNamesFromMenuIds(menus: Menu[], menuIds: string[]): string[] {
  const selectedIdSet = new Set(menuIds.map(String));
  const permissionNames = new Set<string>();
  for (const menu of flattenMenus(menus)) {
    if (!selectedIdSet.has(String(menu.id))) {
      continue;
    }
    if (!isGrantableMenu(menu)) {
      continue;
    }
    const permissionName = resolveGrantableMenuPermissionName(menu);
    if (permissionName) {
      permissionNames.add(permissionName);
    }
  }
  return [...permissionNames];
}

/** 根据角色已有权限名反推应勾选的菜单 id */
function collectMenuIdsFromPermissionNames(menus: Menu[], permissionNames: string[]): string[] {
  const grantedSet = new Set(permissionNames.map(String));
  const menuIds: string[] = [];
  for (const menu of flattenMenus(menus)) {
    const permissionName = resolveGrantableMenuPermissionName(menu);
    if (permissionName && grantedSet.has(permissionName)) {
      menuIds.push(String(menu.id));
    }
  }
  return menuIds;
}

/** 比较两组字符串是否相同（忽略顺序） */
function isSameStringSet(a: string[], b: string[]): boolean {
  if (a.length !== b.length) {
    return false;
  }
  const setB = new Set(b);
  return a.every((item) => setB.has(item));
}

/** 编辑模式下基础字段是否有变更 */
function hasBasicRoleFieldsChanged(mergedData: Record<string, unknown>): boolean {
  const displayName = String(mergedData.displayName || '');
  const description = String(mergedData.description || '');
  return (
    String(originalData.value.displayName || '') !== displayName ||
    String(originalData.value.description || '') !== description
  );
}

/** 解析 create 响应中的角色 id（禁止 Number 转换雪花 Long） */
function resolveRoleIdFromCreateResponse(response: unknown): string | undefined {
  const payload = response as RoleInfo | { data?: RoleInfo; id?: string | number };
  if (payload && typeof payload === 'object' && 'data' in payload && payload.data?.id != null) {
    return preserveApiIdForRequest(payload.data.id);
  }
  const direct = payload as RoleInfo;
  if (direct?.id != null) {
    return preserveApiIdForRequest(direct.id);
  }
  return undefined;
}

/** 解析字符串数组 API 响应 */
function unwrapStringList(response: unknown): string[] {
  const data =
    (response as { data?: string[] })?.data !== undefined
      ? (response as { data?: string[] }).data
      : response;
  return Array.isArray(data) ? data : [];
}

/** 非 admin 角色：仅在菜单或声明变更时调用对应接口 */
async function saveRoleMenusAndScopesIfChanged(roleId: string) {
  if (isSystemAdminRole.value) {
    return;
  }

  const permissionNames = collectPermissionNamesFromMenuIds(
    roleMenuTree.value,
    selectedMenuIds.value,
  );
  const menusChanged = !isSameStringSet(permissionNames, initialPermissionNames.value);
  const scopesChanged = !isSameStringSet(selectedStaffScopes.value, initialStaffScopes.value);

  if (menusChanged) {
    await assignPermissionsApi({
      id: roleId,
      request: { permissionNames },
    });
    initialPermissionNames.value = [...permissionNames];
  }

  if (scopesChanged) {
    await assignStaffScopesApi(roleId, selectedStaffScopes.value);
    initialStaffScopes.value = [...selectedStaffScopes.value];
  }
}

/** 新增角色：保存菜单与声明 */
async function saveRoleMenusAndScopesForCreate(roleId: string) {
  if (isSystemAdminRole.value) {
    return;
  }

  const permissionNames = collectPermissionNamesFromMenuIds(
    roleMenuTree.value,
    selectedMenuIds.value,
  );
  if (permissionNames.length > 0) {
    await assignPermissionsApi({
      id: roleId,
      request: { permissionNames },
    });
  }
  if (selectedStaffScopes.value.length > 0) {
    await assignStaffScopesApi(roleId, selectedStaffScopes.value);
  }
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  modalApi.lock(true);
  try {
    const formValues = await formApi.getValues();
    const mergedData = {
      ...originalData.value,
      ...formValues,
    };

    let roleId: string | undefined;

    if (isEdit.value && props.role) {
      roleId = preserveApiIdForRequest(props.role.id);
      if (!roleId) {
        throw new Error('缺少角色ID');
      }

      if (hasBasicRoleFieldsChanged(mergedData)) {
        const updateParams: UpdateRoleParams = {
          id: roleId,
          displayName: String(mergedData.displayName || ''),
          description: String(mergedData.description || ''),
        };
        await updateRoleApi(updateParams);
        originalData.value = {
          ...originalData.value,
          displayName: updateParams.displayName,
          description: updateParams.description,
        };
      }

      await saveRoleMenusAndScopesIfChanged(roleId);
      ElMessage.success('编辑角色成功');
    } else {
      const createData: CreateRoleParams = {
        isDefault: false,
        displayName: String(mergedData.displayName || ''),
        description: String(mergedData.description || ''),
      };
      const createResponse = await createRoleApi(createData);
      roleId = resolveRoleIdFromCreateResponse(createResponse);

      if (roleId) {
        await saveRoleMenusAndScopesForCreate(roleId);
      }

      ElMessage.success('新增角色成功');
    }
    emit('success');
    modalApi.close();
  } catch (error: unknown) {
    const err = error as {
      message?: string;
      msg?: string;
      response?: { data?: { message?: string; msg?: string } };
    };
    const errorMessage =
      err?.response?.data?.msg ||
      err?.response?.data?.message ||
      err?.message ||
      err?.msg ||
      (isEdit.value ? '编辑角色失败，请稍后重试' : '新增角色失败，请稍后重试');
    ElMessage.error(errorMessage);
  } finally {
    modalApi.unlock();
  }
}

/** 打开弹窗时加载表单与菜单授权回显 */
async function loadFormData() {
  if (props.role?.id) {
    modalApi.setState({ loading: true });
    try {
      const roleData = props.role;
      originalData.value = { ...roleData };
      const formData: Record<string, unknown> = {
        displayName: roleData.displayName || '',
        description: roleData.description || '',
      };

      try {
        roleMenuTree.value = await getMenuGrantTreeApi();
      } catch (error) {
        console.error('获取菜单树失败:', error);
        roleMenuTree.value = [];
      }

      try {
        const permissionsResponse = await getPermissionsByRoleApi({
          value: preserveApiIdForRequest(props.role.id) ?? String(props.role.id),
        });
        const permissionsData =
          (permissionsResponse as unknown as { data?: string[] }).data ||
          (permissionsResponse as string[]);
        const permissionNames = Array.isArray(permissionsData) ? permissionsData : [];
        initialPermissionNames.value = [...permissionNames];
        selectedMenuIds.value = collectMenuIdsFromPermissionNames(
          roleMenuTree.value,
          permissionNames,
        );
      } catch (error) {
        console.error('获取角色权限失败:', error);
        initialPermissionNames.value = [];
        selectedMenuIds.value = [];
      }

      try {
        const roleId = preserveApiIdForRequest(props.role.id);
        if (roleId) {
          const scopesResponse = await getStaffScopesApi(roleId);
          const scopes = unwrapStringList(scopesResponse);
          initialStaffScopes.value = [...scopes];
          selectedStaffScopes.value = scopes;
        }
      } catch (error) {
        console.error('获取角色声明失败:', error);
        initialStaffScopes.value = [];
        selectedStaffScopes.value = [];
      }

      await nextTick();
      await formApi.setValues(formData);
    } catch (error) {
      console.error('加载角色表单失败:', error);
      ElMessage.error('加载角色表单失败，请稍后重试');
      modalApi.close();
      return;
    } finally {
      modalApi.setState({ loading: false });
    }
  } else {
    originalData.value = {};
    initialPermissionNames.value = [];
    initialStaffScopes.value = [];
    selectedMenuIds.value = [];
    selectedStaffScopes.value = [];
    try {
      roleMenuTree.value = await getMenuGrantTreeApi();
    } catch {
      roleMenuTree.value = [];
    }
    await formApi.resetForm();
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:modelValue', isOpen);
  },
  onOpened: async () => {
    await nextTick();
    await loadFormData();
  },
  onConfirm: handleSubmit,
  showCancelButton: true,
  showConfirmButton: true,
  confirmText: '确定',
  cancelText: '取消',
});

const formConfig = reactive({
  commonConfig: {
    componentProps: {
      style: { width: '220px' },
    },
  },
  layout: 'horizontal' as const,
  schema: computed((): VbenFormSchema[] => {
    const schema: VbenFormSchema[] = [
      {
        component: 'Input',
        fieldName: 'displayName',
        label: '角色名称',
        componentProps: {
          placeholder: '请输入角色名称',
        },
        rules: z.string().min(1, { message: '请输入角色名称' }),
      },
      {
        component: 'InputArea',
        fieldName: 'description',
        label: '描述',
        componentProps: {
          placeholder: '请输入描述',
          rows: 3,
        },
      },
    ];

    if (isEdit.value && props.role?.isStatic) {
      schema.unshift({
        component: 'Input',
        fieldName: 'roleTypeHint',
        label: '角色类型',
        componentProps: {
          disabled: true,
          placeholder: '内置角色',
        },
        defaultValue: '内置角色',
      });
    }

    return schema;
  }),
  showDefaultActions: false,
});

const [Form, formApi] = useVbenForm(formConfig);

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      modalApi.open();
    } else {
      modalApi.close();
    }
  },
  { immediate: true },
);

/** 菜单树勾选变更：仅维护 menuId，提交时再映射为 Navigation 权限 */
const handleMenuTreeCheck = (_data: Menu, checkedInfo: { checkedKeys?: string[] }) => {
  if (isSystemAdminRole.value) {
    return;
  }
  selectedMenuIds.value = (checkedInfo.checkedKeys ?? []).map(String);
};

watch(
  [selectedMenuIds, roleMenuTree],
  ([menuIds, menus]) => {
    if (menuTreeRef.value && menus.length > 0) {
      nextTick(() => {
        menuTreeRef.value?.setCheckedKeys(menuIds);
      });
    }
  },
  { immediate: true },
);
</script>

<template>
  <Modal
    class="w-[900px]"
    :title="isEdit ? '编辑角色' : '新增角色'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <div class="space-y-4">
      <p v-if="!isEdit" class="text-muted-foreground text-xs">
        新建为自定义角色，仅配置登录菜单与声明；参与岗位业务的内置角色由系统提供，不可新建。
      </p>
      <Form />

      <div class="border-t pt-4">
        <div class="mb-2 text-sm font-medium">菜单权限</div>
        <p class="text-muted-foreground mb-2 text-xs">
          勾选可访问的页面；仅显示启用且未隐藏的菜单，保存时自动同步 Navigation 入口权限。
        </p>
        <div v-if="isSystemAdminRole" class="mb-2 text-xs text-gray-500">
          系统管理员角色拥有全部菜单
        </div>
        <div
          class="mb-4 max-h-64 overflow-y-auto rounded-md border p-4"
          :class="{ 'pointer-events-none opacity-50': isSystemAdminRole }"
        >
          <ElTree
            ref="menuTreeRef"
            :data="roleMenuTree"
            :props="{ children: 'children', label: 'displayName' }"
            show-checkbox
            node-key="id"
            @check="handleMenuTreeCheck"
          />
        </div>
      </div>

      <div class="border-t pt-4">
        <div class="mb-2 text-sm font-medium">角色管理能力</div>
        <p class="text-muted-foreground mb-2 text-xs">
          拥有该角色的用户，在人员维护页可编辑的岗位分配区块。内置业务角色的能力由系统固定；自定义角色可按需勾选。
        </p>
        <ElCheckboxGroup
          v-model="selectedStaffScopes"
          :disabled="isSystemAdminRole"
          class="flex flex-wrap gap-x-4 gap-y-2"
        >
          <ElCheckbox v-for="opt in staffScopeOptionsForForm" :key="opt.value" :label="opt.value">
            {{ opt.label }}
          </ElCheckbox>
        </ElCheckboxGroup>
      </div>
    </div>
  </Modal>
</template>
