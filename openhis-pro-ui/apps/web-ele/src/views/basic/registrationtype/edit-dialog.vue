<script lang="ts" setup>
import type { AddRegistrationTypeParams, RegistrationType } from '#/api/core/system/registration';

import { computed, reactive, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm, z } from '#/adapter/form';
import {
  addRegistrationTypeApi,
  REGISTRATION_TYPE_ACTIVE_FLAG,
  updateRegistrationTypeApi,
} from '#/api/core/system/registration';

interface Props {
  visible?: boolean;
  formData?: null | Partial<RegistrationType>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: null,
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const isEditMode = computed(() => !!props.formData?.id);

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  modalApi.lock(true);
  try {
    const values = (await formApi.getValues()) as AddRegistrationTypeParams;
    if (isEditMode.value) {
      if (!props.formData?.id) {
        ElMessage.error('缺少挂号类型ID');
        return;
      }
      const params: RegistrationType & { id: string } = {
        id: props.formData.id,
        name: values.name || '',
        ybNo: values.ybNo || '',
        busNo: values.busNo || '',
        amount: Number(values.amount) || 0,
      };
      await updateRegistrationTypeApi(params);
      ElMessage.success('编辑挂号类型成功');
    } else {
      const params: AddRegistrationTypeParams = {
        name: values.name || '',
        ybNo: values.ybNo || '',
        busNo: values.busNo || '',
        amount: Number(values.amount) || 0,
        activeFlag: values.activeFlag ?? REGISTRATION_TYPE_ACTIVE_FLAG.ACTIVE,
      };
      await addRegistrationTypeApi(params);
      ElMessage.success('新增挂号类型成功');
    }
    emit('success');
    modalApi.close();
  } catch (error: any) {
    console.error('操作失败:', error);
    const errorMessage = error.msg || error.message || '操作失败，请稍后重试';
    ElMessage.error(errorMessage);
  } finally {
    modalApi.unlock();
  }
}

async function resetForm() {
  await formApi.resetForm();

  if (props.formData && Object.keys(props.formData).length > 0) {
    const form = props.formData as Partial<RegistrationType>;
    await formApi.setValues({
      name: form.name || '',
      ybNo: form.ybNo || '',
      busNo: form.busNo || '',
      amount: form.amount || 0,
    });
  } else {
    await formApi.setValues({
      name: '',
      ybNo: '',
      busNo: '',
      amount: 0,
      activeFlag: REGISTRATION_TYPE_ACTIVE_FLAG.ACTIVE,
    });
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
    await resetForm();
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
      },
      rules: z.string().min(1, { message: '请输入名称' }),
    },
    {
      component: 'Input',
      fieldName: 'ybNo',
      label: '医保编码',
      required: true,
      componentProps: {
        placeholder: '请输入医保编码',
      },
      rules: z.string().min(1, { message: '请输入医保编码' }),
    },
    {
      component: 'Input',
      fieldName: 'busNo',
      label: '业务编码',
      required: true,
      componentProps: {
        placeholder: '请输入业务编码',
        disabled: isEditMode.value,
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'amount',
      label: '金额',
      required: true,
      componentProps: {
        placeholder: '请输入金额',
        min: 0,
        precision: 2,
        style: { width: '220px' },
      },
      rules: z.number().min(0, { message: '金额必须大于等于0' }),
    },
    {
      component: 'Select',
      fieldName: 'activeFlag',
      label: '状态',
      required: true,
      defaultValue: REGISTRATION_TYPE_ACTIVE_FLAG.ACTIVE,
      componentProps: {
        placeholder: '请选择状态',
        options: [
          { label: '启用', value: REGISTRATION_TYPE_ACTIVE_FLAG.ACTIVE },
          { label: '停用', value: REGISTRATION_TYPE_ACTIVE_FLAG.INACTIVE },
        ],
      },
      rules: z.number({ message: '请选择状态' }),
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
    class="w-[600px]"
    :title="isEditMode ? '编辑挂号类型' : '新增挂号类型'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
