<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { PermissionInfo } from '#/api/user/permission';
import type { CreateRoleParams, RoleInfo, UpdateRoleParams } from '#/api/user/role';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage, ElTree } from 'element-plus';

import { useVbenForm, z } from '#/adapter/form';
import { getPermissionListApi, getPermissionsByRoleApi } from '#/api/user/permission';
import {
  assignPermissionsApi,
  createRoleApi,
  getRoleByIdApi,
  updateRoleApi,
} from '#/api/user/role';

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

// 判断是否为系统管理员角色
const isSystemAdminRole = computed(() => {
  if (!props.role) return false;
  return props.role.name === 'admin' || props.role.code === 'ROLE_ADMIN';
});

const originalData = ref<Record<string, any>>({});

// 权限树数据
const allPermissions = ref<PermissionInfo[]>([]);

// 选中的权限值（权限名称数组）
const selectedPermissions = ref<string[]>([]);

// 监听权限树数据变化，设置默认展开
watch(
  allPermissions,
  (newPermissions) => {
    if (newPermissions.length > 0) {
      // 默认展开前两级
      const keys: string[] = [];
      const collectKeys = (nodes: PermissionInfo[], level: number) => {
        if (level <= 2) {
          nodes.forEach((node) => {
            keys.push(node.name);
            if (node.children && node.children.length > 0) {
              collectKeys(node.children, level + 1);
            }
          });
        }
      };
      collectKeys(newPermissions, 1);
    }
  },
  { immediate: true },
);

const loadPermissions = async () => {
  try {
    const response = await getPermissionListApi();
    const data =
      (response as unknown as { data?: PermissionInfo[] }).data || (response as PermissionInfo[]);
    allPermissions.value = Array.isArray(data) ? data : [];
  } catch (error) {
    console.error('加载权限树失败:', error);
    allPermissions.value = [];
  }
};

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

    let roleId: number;

    if (isEdit.value && props.role) {
      // 编辑角色
      roleId = props.role.id;
      const updateParams: UpdateRoleParams = {
        id: roleId,
        isDefault: mergedData.isDefault || 'false',
        displayName: mergedData.displayName || '',
        description: mergedData.description || '',
      };
      await updateRoleApi(updateParams);

      // 绑定权限（系统管理员角色不能编辑权限）
      if (!isSystemAdminRole.value) {
        await assignPermissionsApi({
          id: roleId,
          request: {
            permissionNames: selectedPermissions.value,
          },
        });
      }

      ElMessage.success('编辑角色成功');
    } else {
      // 新增角色
      const createData: CreateRoleParams = {
        isDefault: mergedData.isDefault || 'false',
        isStatic: mergedData.isStatic || 'false',
        code: mergedData.code || '',
        displayName: mergedData.displayName || '',
        name: mergedData.name || '',
        description: mergedData.description || '',
      };
      const createResponse = await createRoleApi(createData);

      // 从响应中获取角色ID
      const response = createResponse as unknown as { data?: { id: number }; id?: number };
      roleId = response.data?.id || response.id || 0;

      // 绑定权限
      if (selectedPermissions.value.length > 0 && roleId) {
        await assignPermissionsApi({
          id: roleId,
          request: {
            permissionNames: selectedPermissions.value,
          },
        });
      }

      ElMessage.success('新增角色成功');
    }
    emit('success');
    modalApi.close();
  } catch (error: any) {
    const errorMessage =
      error?.response?.data?.msg ||
      error?.response?.data?.message ||
      error?.message ||
      error?.msg ||
      (isEdit.value ? '编辑角色失败，请稍后重试' : '新增角色失败，请稍后重试');
    ElMessage.error(errorMessage);
  } finally {
    modalApi.unlock();
  }
}

async function loadFormData() {
  if (props.role && props.role.id) {
    // 编辑模式：调用接口获取角色详情
    modalApi.setState({ loading: true });
    try {
      const response = await getRoleByIdApi(props.role.id);
      const roleData = (response as unknown as { data?: RoleInfo }).data || (response as RoleInfo);

      originalData.value = { ...roleData };
      const formData: Record<string, any> = {
        displayName: roleData.displayName || '',
        description: roleData.description || '',
        // 将布尔值或数字转换为字符串 'true' 或 'false'，确保与 Select 选项匹配
        isDefault: normalizeBooleanString(roleData.isDefault),
        isStatic: normalizeBooleanString(roleData.isStatic),
      };

      // 调用接口获取角色的权限
      try {
        const permissionsResponse = await getPermissionsByRoleApi({ value: props.role.id });
        const permissionsData =
          (permissionsResponse as unknown as { data?: string[] }).data ||
          (permissionsResponse as string[]);
        selectedPermissions.value = Array.isArray(permissionsData) ? permissionsData : [];
      } catch (error) {
        console.error('获取角色权限失败:', error);
        // 如果获取权限失败，尝试从角色详情中获取
        if (roleData.permissionNames && Array.isArray(roleData.permissionNames)) {
          selectedPermissions.value = roleData.permissionNames;
        } else if (roleData.permissions && Array.isArray(roleData.permissions)) {
          selectedPermissions.value = roleData.permissions;
        } else {
          selectedPermissions.value = [];
        }
      }

      // 等待表单 schema 更新后再设置值
      await nextTick();
      await formApi.setValues(formData);
    } catch (error) {
      console.error('获取角色详情失败:', error);
      ElMessage.error('获取角色详情失败，请稍后重试');
      modalApi.close();
      return;
    } finally {
      modalApi.setState({ loading: false });
    }
  } else {
    // 新增模式
    originalData.value = {};
    selectedPermissions.value = [];
    await formApi.resetForm();
    await nextTick();
    await formApi.setValues({
      isDefault: 'false',
      isStatic: 'false',
    });
  }
}

/**
 * 将各种类型的布尔值转换为字符串 'true' 或 'false'
 * 支持：boolean、string ('true'/'false'/'1'/'0'/'Y'/'N')、number (1/0)
 */
function normalizeBooleanString(value: any): string {
  if (value === true || value === 'true' || value === '1' || value === 'Y') {
    return 'true';
  }
  if (value === false || value === 'false' || value === '0' || value === 'N') {
    return 'false';
  }
  // 默认返回 'false'
  return 'false';
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
    const schema: VbenFormSchema[] = [];

    if (!isEdit.value) {
      schema.push(
        {
          component: 'Input',
          fieldName: 'code',
          label: '角色编码',
          componentProps: {
            placeholder: '请输入角色编码',
          },
          rules: z.string().min(1, { message: '请输入角色编码' }),
        },
        {
          component: 'Input',
          fieldName: 'name',
          label: '角色名称',
          componentProps: {
            placeholder: '请输入角色名称',
          },
          rules: z.string().min(1, { message: '请输入角色名称' }),
        },
      );
    }

    schema.push({
      component: 'Input',
      fieldName: 'displayName',
      label: '显示名称',
      componentProps: {
        placeholder: '请输入显示名称',
      },
      rules: z.string().min(1, { message: '请输入显示名称' }),
    });

    if (!isEdit.value) {
      schema.push({
        component: 'Select',
        fieldName: 'isStatic',
        label: '是否内置',
        componentProps: {
          placeholder: '请选择是否内置',
          options: [
            { label: '是', value: 'true' },
            { label: '否', value: 'false' },
          ],
        },
        rules: z.string().min(1, { message: '请选择是否内置' }),
      });
    }

    schema.push(
      {
        component: 'Select',
        fieldName: 'isDefault',
        label: '是否默认',
        componentProps: {
          placeholder: '请选择是否默认',
          options: [
            { label: '是', value: 'true' },
            { label: '否', value: 'false' },
          ],
        },
        rules: z.string().min(1, { message: '请选择是否默认' }),
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
    );

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

// Tree 组件引用
const treeRef = ref<InstanceType<typeof ElTree>>();

// 处理树节点选中事件
const handleTreeCheck = (_data: PermissionInfo, checkedInfo: any) => {
  if (isSystemAdminRole.value) {
    return;
  }
  const checkedKeys = checkedInfo.checkedKeys || [];
  selectedPermissions.value = checkedKeys.map(String);
};

// 监听选中的权限变化，更新树组件的选中状态
watch(
  [selectedPermissions, allPermissions],
  ([newPermissions, permissions]) => {
    if (treeRef.value && newPermissions.length > 0 && permissions.length > 0) {
      nextTick(() => {
        treeRef.value?.setCheckedKeys(newPermissions);
      });
    }
  },
  { immediate: true },
);

onMounted(() => {
  loadPermissions();
});
</script>

<template>
  <Modal
    class="w-[900px]"
    :title="isEdit ? '编辑角色' : '新增角色'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <div class="space-y-4">
      <Form />

      <div class="border-t pt-4">
        <div class="mb-2 text-sm font-medium">权限选择</div>
        <div v-if="isSystemAdminRole" class="mb-2 text-xs text-gray-500">
          系统管理员角色拥有所有权限
        </div>
        <div
          class="overflow-y-auto rounded-md border p-4"
          :class="{ 'pointer-events-none opacity-50': isSystemAdminRole }"
        >
          <ElTree
            ref="treeRef"
            :data="allPermissions"
            :props="{
              children: 'children',
              label: 'displayName',
            }"
            show-checkbox
            node-key="name"
            @check="handleTreeCheck"
          />
        </div>
      </div>
    </div>
  </Modal>
</template>
