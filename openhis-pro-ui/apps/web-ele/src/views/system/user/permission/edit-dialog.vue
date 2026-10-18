<script lang="ts" setup>
import type {
  CreatePermissionParams,
  PermissionInfo,
  UpdatePermissionParams,
} from '#/api/user/permission';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@zxplat/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { createPermissionApi, updatePermissionApi } from '#/api/user/permission';

interface Props {
  visible?: boolean;
  formData?: Partial<PermissionInfo>;
  allPermissions?: PermissionInfo[];
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
  allPermissions: () => [],
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<Partial<PermissionInfo>>({});

const isEditMode = computed(() => {
  return !!props.formData?.id;
});

// 递归过滤树形数据，移除指定 ID 的节点及其子节点
function filterTreeOptions(
  items: PermissionInfo[],
  excludeId: number | undefined,
): PermissionInfo[] {
  if (!excludeId) {
    return items;
  }

  const result: PermissionInfo[] = [];

  for (const item of items) {
    // 如果当前节点是要排除的 ID，跳过
    if (item.id === excludeId) {
      continue;
    }

    // 递归处理子节点
    const filteredChildren =
      item.children && item.children.length > 0 ? filterTreeOptions(item.children, excludeId) : [];

    result.push({
      ...item,
      children: filteredChildren,
    });
  }

  return result;
}

// 将权限数据转换为选项格式
function convertPermissionsToOptions(permissions: PermissionInfo[]): Array<{
  children?: Array<{ label: string; value: number | string }>;
  label: string;
  value: number | string;
}> {
  return permissions.map((permission) => ({
    label: permission.displayName || permission.name,
    value: permission.name,
    children:
      permission.children && permission.children.length > 0
        ? convertPermissionsToOptions(permission.children)
        : undefined,
  }));
}

// 获取父权限选项（编辑模式下过滤掉当前权限及其子权限）
const parentPermissionOptions = computed(() => {
  const permissions = isEditMode.value
    ? filterTreeOptions(props.allPermissions, props.formData?.id || originalData.value?.id)
    : props.allPermissions;
  return convertPermissionsToOptions(permissions);
});

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

    if (isEditMode.value && originalData.value.id) {
      const updateParams: UpdatePermissionParams = {
        id: originalData.value.id,
        request: {
          parentName: mergedData.parentName || undefined,
          displayName: mergedData.displayName || '',
          description: mergedData.description || '',
        },
      };
      await updatePermissionApi(updateParams);
      ElMessage.success('更新成功');
    } else {
      const createParams: CreatePermissionParams = {
        parentName: mergedData.parentName || undefined,
        displayName: mergedData.displayName || '',
        name: mergedData.name || '',
        description: mergedData.description || '',
      };
      await createPermissionApi(createParams);
      ElMessage.success('创建成功');
    }
    emit('success');
    modalApi.close();
  } catch (error: any) {
    console.error(isEditMode.value ? '更新失败:' : '创建失败:', error);
    const errorMessage =
      error?.response?.data?.msg ||
      error?.response?.data?.message ||
      error?.message ||
      error?.msg ||
      (isEditMode.value ? '更新失败，请稍后重试' : '创建失败，请稍后重试');
    ElMessage.error(errorMessage);
  } finally {
    modalApi.unlock();
  }
}

async function loadFormData() {
  if (props.formData && Object.keys(props.formData).length > 0) {
    originalData.value = { ...props.formData };
    const formData = {
      ...props.formData,
      parentName: props.formData.parentName || undefined,
    };
    formApi.setValues(formData);
  } else {
    originalData.value = {};
    await formApi.resetForm();
    await formApi.setValues({
      parentName: undefined,
    });
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
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
  schema: computed(() => {
    const schema: any[] = [];

    if (!isEditMode.value) {
      schema.push({
        component: 'Input',
        fieldName: 'name',
        label: '权限名称',
        required: true,
      });
    }

    schema.push(
      {
        component: 'Input',
        fieldName: 'displayName',
        label: '显示名称',
        required: true,
      },
      {
        component: 'TreeSelect',
        fieldName: 'parentName',
        label: '父权限',
        componentProps: {
          data: parentPermissionOptions.value,
          checkStrictly: true,
          filterable: true,
          clearable: true,
          props: {
            value: 'value',
            label: 'label',
            children: 'children',
          },
          nodeKey: 'value',
        },
      },
      {
        component: 'InputArea',
        fieldName: 'description',
        label: '描述',
        componentProps: {
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
  () => props.visible,
  (visible) => {
    if (visible) {
      modalApi.open();
    } else {
      modalApi.close();
    }
  },
  { immediate: true },
);

// 监听父权限选项变化，确保在数据加载完成后正确回显
watch(
  () => parentPermissionOptions.value,
  async (newOptions) => {
    if (
      newOptions &&
      newOptions.length > 0 &&
      originalData.value &&
      originalData.value.parentName
    ) {
      await nextTick();
      formApi.setFieldValue('parentName', originalData.value.parentName);
    }
  },
  { immediate: true },
);
</script>

<template>
  <Modal
    class="w-[500px]"
    :title="isEditMode ? '编辑权限' : '新增权限'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
