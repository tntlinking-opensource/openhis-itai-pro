<script lang="ts" setup>
import type { CreateWarehouseParams, Warehouse } from '#/api/basic/warehouse';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import {
  createWarehouseApi,
  editWarehouseApi,
  getWarehouseApi,
  WAREHOUSE_FORM_ENUM,
} from '#/api/basic/warehouse';

interface Props {
  visible?: boolean;
  formData?: Partial<Warehouse>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const type = ref<(typeof WAREHOUSE_FORM_ENUM)[keyof typeof WAREHOUSE_FORM_ENUM]>(
  WAREHOUSE_FORM_ENUM.WAREHOUSE,
);

const isEditMode = computed(() => !!props.formData?.id);

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) return;

  modalApi.lock(true);
  try {
    const formValues = await formApi.getValues();
    const currentType = (formValues.type ||
      type.value) as (typeof WAREHOUSE_FORM_ENUM)[keyof typeof WAREHOUSE_FORM_ENUM];

    if (isEditMode.value) {
      const params: CreateWarehouseParams & { id: string } = {
        formEnum: currentType,
        name: formValues.name || '',
        id: props.formData!.id as string,
      };
      await editWarehouseApi(params);
      ElMessage.success('更新成功');
    } else {
      const params: CreateWarehouseParams = {
        formEnum: currentType,
        name: formValues.name || '',
      };
      await createWarehouseApi(params);
      ElMessage.success('创建库房成功');
    }

    emit('success');
    modalApi.close();
  } catch (error: unknown) {
    console.error(isEditMode.value ? '更新失败:' : '创建失败:', error);
    const errorMessage =
      (error as { response?: { data?: { message?: string; msg?: string } } })?.response?.data
        ?.msg ||
      (error as { response?: { data?: { message?: string; msg?: string } } })?.response?.data
        ?.message ||
      (error as { message?: string })?.message ||
      (error as { msg?: string })?.msg ||
      (isEditMode.value ? '更新失败，请稍后重试' : '创建失败，请稍后重试');
    ElMessage.error(errorMessage);
  } finally {
    modalApi.unlock();
  }
}

async function loadFormData() {
  const id = props.formData?.id;

  if (id && typeof id === 'string') {
    modalApi.setState({ loading: true });
    try {
      const response = await getWarehouseApi({ id });
      if (response) {
        const backendType = String(response.formEnum);
        const normalizedType =
          backendType === WAREHOUSE_FORM_ENUM.PHARMACY
            ? WAREHOUSE_FORM_ENUM.PHARMACY
            : WAREHOUSE_FORM_ENUM.WAREHOUSE;
        type.value = normalizedType;
        await formApi.setValues({
          type: normalizedType,
          name: response.name || '',
        });
      } else {
        ElMessage.warning('未找到库房信息');
        modalApi.close();
      }
    } catch (error) {
      console.error('获取库房详情失败:', error);
      ElMessage.error('获取库房详情失败，请稍后重试');
      modalApi.close();
      return;
    } finally {
      modalApi.setState({ loading: false });
    }
  } else {
    await formApi.resetForm();
    type.value = WAREHOUSE_FORM_ENUM.WAREHOUSE;
    await formApi.setValues({
      type: type.value,
      name: '',
    });
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
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
  schema: [
    {
      component: 'RadioGroup',
      fieldName: 'type',
      label: '类型',
      required: true,
      componentProps: {
        options: [
          { label: '仓库', value: WAREHOUSE_FORM_ENUM.WAREHOUSE },
          { label: '药房', value: WAREHOUSE_FORM_ENUM.PHARMACY },
        ],
        onChange: (value: (typeof WAREHOUSE_FORM_ENUM)[keyof typeof WAREHOUSE_FORM_ENUM]) => {
          type.value = value;
        },
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
  ],
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
    :title="isEditMode ? '编辑' : '新增'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
