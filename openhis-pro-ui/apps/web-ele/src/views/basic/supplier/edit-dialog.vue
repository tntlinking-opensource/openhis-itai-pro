<script lang="ts" setup>
import type { Supplier } from '#/api';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { createSupplierApi, getSupplierApi, updateSupplierApi } from '#/api';

interface Props {
  visible?: boolean;
  formData?: Partial<Supplier>;
  supplierTypeOptions?: Array<{ label: string; value: number }>;
  organizationOptions?: Array<{ label: string; value: string }>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
  supplierTypeOptions: () => [],
  organizationOptions: () => [],
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<Record<string, unknown>>({});

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
    const submitData: Record<string, unknown> = {};
    Object.keys(mergedData).forEach((key) => {
      const value = mergedData[key];
      if (value !== null && value !== undefined && value !== '') {
        submitData[key] = value;
      }
    });

    if (isEditMode.value) {
      await updateSupplierApi(submitData);
      ElMessage.success('更新成功');
    } else {
      const { id: _id, ...createData } = submitData;
      await createSupplierApi(createData);
      ElMessage.success('创建成功');
    }
    emit('success');
    modalApi.close();
  } catch (error) {
    console.error(isEditMode.value ? '更新失败:' : '创建失败:', error);
    ElMessage.error(isEditMode.value ? '更新失败，请稍后重试' : '创建失败，请稍后重试');
  } finally {
    modalApi.unlock();
  }
}

async function loadFormData() {
  const id = props.formData?.id;
  if (id !== undefined && id !== null && String(id).trim() !== '') {
    modalApi.setState({ loading: true });
    try {
      const response = await getSupplierApi(String(id));
      const data = response;
      if (data) {
        originalData.value = { ...data };
        const formData = {
          ...data,
          orgId: data.orgId ? String(data.orgId) : undefined,
        };
        await formApi.setValues(formData);
      } else {
        ElMessage.warning('未找到供应商信息');
        modalApi.close();
      }
    } catch (error) {
      console.error('获取供应商详情失败:', error);
      ElMessage.error('获取供应商详情失败，请稍后重试');
      modalApi.close();
      return;
    } finally {
      modalApi.setState({ loading: false });
    }
  } else {
    if (props.formData && Object.keys(props.formData).length > 0) {
      originalData.value = { ...props.formData };
      const formData = {
        ...props.formData,
        orgId: props.formData.orgId ? String(props.formData.orgId) : undefined,
      };
      formApi.setValues(formData);
    } else {
      originalData.value = {};
      await formApi.resetForm();
      formApi.setValues({});
    }
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
    updateFormSchema();
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
      component: 'Input',
      fieldName: 'busNo',
      label: '供应商编号',
      componentProps: {
        placeholder: '可输入；留空则系统自动生成',
      },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '供应商名称',
      rules: 'required',
    },
    {
      component: 'TreeSelect',
      fieldName: 'orgId',
      label: '部门',
      componentProps: {
        data: props.organizationOptions,
        checkStrictly: true,
        filterable: true,
        props: {
          value: 'value',
          label: 'label',
          children: 'children',
        },
        nodeKey: 'value',
      },
    },
    {
      component: 'Select',
      fieldName: 'typeEnum',
      label: '类型',
      componentProps: {
        options: props.supplierTypeOptions,
      },
    },
    {
      component: 'Input',
      fieldName: 'address',
      label: '地址',
    },
    {
      component: 'Input',
      fieldName: 'phone',
      label: '电话',
    },
    {
      component: 'Input',
      fieldName: 'email',
      label: '邮箱',
    },
    {
      component: 'Input',
      fieldName: 'pyStr',
      label: '拼音',
    },
    {
      component: 'Input',
      fieldName: 'wbStr',
      label: '五笔',
    },
    // {
    //   component: 'RadioGroup',
    //   fieldName: 'activeFlag',
    //   label: '是否启用',
    //   componentProps: {
    //     options: [
    //       { label: '是', value: 1 },
    //       { label: '否', value: 0 },
    //     ],
    //   },
    // },
  ],
  showDefaultActions: false,
});

interface SchemaItemWithProps {
  fieldName?: string;
  componentProps?: Record<string, unknown>;
}

const [Form, formApi] = useVbenForm(formConfig);

function updateFormSchema() {
  const typeField = formConfig.schema.find((item) => item.fieldName === 'typeEnum') as
    | SchemaItemWithProps
    | undefined;
  if (typeField?.componentProps) {
    typeField.componentProps.options = props.supplierTypeOptions;
  }

  const orgField = formConfig.schema.find((item) => item.fieldName === 'orgId') as
    | SchemaItemWithProps
    | undefined;
  if (orgField?.componentProps) {
    orgField.componentProps.data = props.organizationOptions;
  }
}

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
  () => props.organizationOptions,
  async (newOptions) => {
    if (newOptions && newOptions.length > 0 && originalData.value?.orgId) {
      await nextTick();
      const orgId = String(originalData.value.orgId);
      formApi.setFieldValue('orgId', orgId);
    }
  },
  { immediate: true },
);
</script>

<template>
  <Modal
    class="w-[500px]"
    :title="isEditMode ? '编辑供应商' : '新增供应商'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
