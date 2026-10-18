<script lang="ts" setup>
import type { ConceptParams } from '#/api/basic/code';

import { computed, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@zxplat/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { createConceptApi, updateConceptApi } from '#/api/basic/code';

interface Props {
  visible?: boolean;
  formData?: Partial<{
    [key: string]: any;
    code?: string;
    definition?: string;
    id?: string;
    name?: string;
    sortOrder?: number;
  }>;
  codeSystemId?: string;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
  codeSystemId: '',
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<
  Partial<{ code?: string; definition?: string; id?: string; name?: string; sortOrder?: number }>
>({});

const isEditMode = computed(() => !!originalData.value.id);

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  if (!props.codeSystemId) {
    ElMessage.warning('请先选择代码系统');
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
      await updateConceptApi({
        id: String(mergedData.id),
        display: mergedData.name || '',
        definition: mergedData.definition || '',
        codeSystemId: props.codeSystemId,
        code: mergedData.code || '',
        sortOrder: mergedData.sortOrder ?? undefined,
      });
      ElMessage.success('编辑成功');
    } else {
      const createParams: ConceptParams = {
        display: mergedData.name || '',
        definition: mergedData.definition || '',
        codeSystemId: props.codeSystemId,
        code: mergedData.code || '',
        sortOrder: mergedData.sortOrder ?? undefined,
      };
      await createConceptApi(createParams);
      ElMessage.success('创建成功');
    }
    emit('success');
    modalApi.close();
  } catch (error: any) {
    console.error('操作失败:', error);
    const errorMessage =
      error?.response?.data?.msg ||
      error?.response?.data?.message ||
      error?.message ||
      error?.msg ||
      '操作失败，请稍后重试';
    ElMessage.error(errorMessage);
  } finally {
    modalApi.unlock();
  }
}

async function loadFormData() {
  if (props.formData && Object.keys(props.formData).length > 0 && props.formData.id) {
    originalData.value = {
      id: props.formData.id,
      name: props.formData.display,
      code: props.formData.code || '',
      definition: props.formData.definition || props.formData.description || '',
      sortOrder: props.formData.sortOrder,
    };
    await formApi.setValues({
      name: props.formData.display || props.formData.name || '',
      code: props.formData.code || '',
      definition: props.formData.definition || props.formData.description || '',
      sortOrder: props.formData.sortOrder ?? undefined,
    });
  } else {
    originalData.value = {};
    await formApi.resetForm();
    await formApi.setValues({
      name: '',
      code: '',
      definition: '',
      sortOrder: undefined,
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
      fieldName: 'code',
      label: '编码',
      required: true,
      componentProps: {
        placeholder: '请输入编码',
        disabled: isEditMode.value,
      },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '名称',
      required: true,
      componentProps: {
        placeholder: '请输入名称',
      },
    },
    {
      component: 'InputArea',
      fieldName: 'definition',
      label: '描述',
      required: true,
      componentProps: {
        placeholder: '请输入描述',
        rows: 4,
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'sortOrder',
      label: '排序',
      required: false,
      componentProps: {
        placeholder: '请输入排序号（越小越靠前）',
        min: 0,
        step: 1,
        controlsPosition: 'right',
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

watch(
  () => props.formData,
  () => {
    if (props.visible) {
      loadFormData();
    }
  },
  { deep: true },
);
</script>

<template>
  <Modal
    class="w-[500px]"
    :title="isEditMode ? '编辑字典编码' : '新增字典编码'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
