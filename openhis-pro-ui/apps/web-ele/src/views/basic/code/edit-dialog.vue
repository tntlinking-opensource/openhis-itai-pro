<script lang="ts" setup>
import type { CodeListResponse, CreateCodeSystemParams } from '#/api/basic/code';

import { computed, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@zxplat/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { createCodeSystemApi, updateCodeSystemApi } from '#/api/basic/code';

interface Props {
  visible?: boolean;
  formData?: Partial<CodeListResponse>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<Partial<CodeListResponse>>({});

const isEditMode = computed(() => !!originalData.value.id);

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

    if (isEditMode.value && mergedData.id) {
      // 编辑：不允许修改 name，只更新其他字段
      await updateCodeSystemApi({
        id: String(mergedData.id),
        description: mergedData.description || '',
        title: mergedData.title || '',
        version: mergedData.version || '',
      });
      ElMessage.success('编辑成功');
    } else {
      const createParams: CreateCodeSystemParams = {
        name: mergedData.name || '',
        description: mergedData.description || '',
        title: mergedData.title || '',
        version: mergedData.version || '',
      };
      await createCodeSystemApi(createParams);
      ElMessage.success('创建成功');
    }
    emit('success');
    modalApi.close();
  } catch (error: any) {
    console.error('创建失败:', error);
    const errorMessage =
      error?.response?.data?.msg ||
      error?.response?.data?.message ||
      error?.message ||
      error?.msg ||
      '创建失败，请稍后重试';
    ElMessage.error(errorMessage);
  } finally {
    modalApi.unlock();
  }
}

async function loadFormData() {
  if (props.formData && Object.keys(props.formData).length > 0) {
    originalData.value = { ...props.formData };
    await formApi.setValues(props.formData);
  } else {
    originalData.value = {};
    await formApi.resetForm();
    await formApi.setValues({
      name: '',
      description: '',
      title: '',
      version: '',
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
  schema: computed(() => [
    {
      component: 'Input',
      fieldName: 'name',
      label: '名称',
      required: true,
      componentProps: {
        placeholder: '请输入名称',
        // 编辑时名称不可修改
        disabled: isEditMode.value,
      },
    },
    {
      component: 'Input',
      fieldName: 'title',
      label: '标题',
      required: true,
      componentProps: {
        placeholder: '请输入标题',
      },
    },
    {
      component: 'Input',
      fieldName: 'version',
      label: '版本',
      required: true,
      componentProps: {
        placeholder: '请输入版本',
      },
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
  ]),
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
    class="w-[500px]"
    :title="isEditMode ? '编辑字典' : '新增字典'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
