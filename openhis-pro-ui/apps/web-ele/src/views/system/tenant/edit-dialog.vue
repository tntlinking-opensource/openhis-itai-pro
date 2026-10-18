<script lang="ts" setup>
import type { CreateTenantParams, TenantInfo, UpdateTenantParams } from '#/api/core/system/tenant';

import { computed, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@zxplat/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm, z } from '#/adapter/form';
import { createTenantApi, updateTenantApi } from '#/api/core/system/tenant';

interface Props {
  visible?: boolean;
  formData?: Partial<TenantInfo>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<Partial<TenantInfo>>({});

const isEditMode = computed(() => {
  return !!props.formData?.id;
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
      const updateParams: UpdateTenantParams = {
        id: originalData.value.id,
        request: {
          name: mergedData.name || '',
          description: mergedData.description || '',
          extraProperties: mergedData.extraProperties
            ? typeof mergedData.extraProperties === 'string'
              ? mergedData.extraProperties
              : JSON.stringify(mergedData.extraProperties)
            : undefined,
          isActive: mergedData.isActive || 'true',
        },
      };
      await updateTenantApi(updateParams);
      ElMessage.success('更新成功');
    } else {
      const createParams: CreateTenantParams = {
        code: mergedData.code || '',
        name: mergedData.name || '',
        description: mergedData.description || '',
        extraProperties: mergedData.extraProperties
          ? typeof mergedData.extraProperties === 'string'
            ? mergedData.extraProperties
            : JSON.stringify(mergedData.extraProperties)
          : undefined,
        isActive: mergedData.isActive || 'true',
      };
      await createTenantApi(createParams);
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
      isActive: String(props.formData.isActive ?? true),
      extraProperties: props.formData.extraProperties
        ? typeof props.formData.extraProperties === 'string'
          ? props.formData.extraProperties
          : JSON.stringify(props.formData.extraProperties)
        : '',
    };
    formApi.setValues(formData);
  } else {
    originalData.value = {};
    await formApi.resetForm();
    await formApi.setValues({
      isActive: 'true',
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
        fieldName: 'code',
        label: '租户编码',
        required: true,
        rules: z.string().min(1, { message: '请输入租户编码' }),
      });
    }

    schema.push(
      {
        component: 'Input',
        fieldName: 'name',
        label: '租户名称',
        required: true,
        rules: z.string().min(1, { message: '请输入租户名称' }),
      },
      {
        component: 'Select',
        fieldName: 'isActive',
        label: '状态',
        componentProps: {
          options: [
            { label: '启用', value: 'true' },
            { label: '禁用', value: 'false' },
          ],
        },
        rules: z.string().min(1, { message: '请选择状态' }),
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
</script>

<template>
  <Modal
    class="w-[600px]"
    :title="isEditMode ? '编辑租户' : '新增租户'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
