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
  initWarehouseApi,
  isDepartmentPharmacyFormEnum,
  isPharmacyFormEnum,
  WAREHOUSE_FORM_ENUM,
} from '#/api/basic/warehouse';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

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

const organizationOptions = ref<Array<{ label: string; value: string }>>([]);
const formEnumOptions = ref<Array<{ label: string; value: string }>>([]);
const initLoading = ref(false);

const isEditMode = computed(() => !!props.formData?.id);

/** 将后端形态码规范为前端下拉值 */
function normalizeFormEnum(formEnum?: null | string) {
  if (isPharmacyFormEnum(formEnum)) {
    return WAREHOUSE_FORM_ENUM.PHARMACY;
  }
  if (isDepartmentPharmacyFormEnum(formEnum)) {
    return WAREHOUSE_FORM_ENUM.DEPARTMENT_PHARMACY;
  }
  return WAREHOUSE_FORM_ENUM.WAREHOUSE;
}

/** 加载维护页初始化下拉（形态、上级科室） */
async function loadInitOptions() {
  initLoading.value = true;
  try {
    const response = await initWarehouseApi();
    formEnumOptions.value = (response?.formEnumOptions ?? []).map((item) => ({
      label: item.label,
      value: item.value,
    }));
    organizationOptions.value = (response?.organizationOptions ?? []).map((item) => ({
      label: item.label,
      value: preserveApiIdForRequest(item.value) ?? String(item.value),
    }));
    await formApi.updateSchema([
      {
        fieldName: 'type',
        componentProps: {
          options: formEnumOptions.value,
        },
      },
    ]);
  } catch (error) {
    console.error('加载库房初始化选项失败:', error);
    ElMessage.error('加载科室选项失败，请稍后重试');
  } finally {
    initLoading.value = false;
  }
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) return;

  modalApi.lock(true);
  try {
    const formValues = await formApi.getValues();
    const currentType =
      formValues.type as (typeof WAREHOUSE_FORM_ENUM)[keyof typeof WAREHOUSE_FORM_ENUM];

    const params: CreateWarehouseParams = {
      formEnum: currentType,
      name: formValues.name || '',
    };
    if (isDepartmentPharmacyFormEnum(currentType)) {
      params.organizationId = preserveApiIdForRequest(formValues.organizationId);
    }

    if (isEditMode.value) {
      await editWarehouseApi({
        ...params,
        id: props.formData!.id as string,
      });
      ElMessage.success('更新成功');
    } else {
      await createWarehouseApi(params);
      ElMessage.success('创建成功');
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
  await loadInitOptions();
  const id = props.formData?.id;

  if (id && typeof id === 'string') {
    modalApi.setState({ loading: true });
    try {
      const response = await getWarehouseApi({ id });
      if (response) {
        const normalizedType = normalizeFormEnum(response.formEnum);
        await formApi.setValues({
          type: normalizedType,
          name: response.name || '',
          organizationId: preserveApiIdForRequest(response.organizationId) ?? undefined,
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
    await formApi.setValues({
      type: WAREHOUSE_FORM_ENUM.WAREHOUSE,
      name: '',
      organizationId: undefined,
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
  labelWidth: 96,
  schema: [
    {
      component: 'RadioGroup',
      fieldName: 'type',
      label: '类型',
      rules: 'required',
      componentProps: {
        options: [],
        class: 'warehouse-form-type-radio',
      },
    },
    {
      component: 'Select',
      fieldName: 'organizationId',
      label: '上级科室',
      dependencies: {
        triggerFields: ['type'],
        show: (values: Record<string, unknown>) =>
          isDepartmentPharmacyFormEnum(values.type as string),
        rules: (values: Record<string, unknown>) =>
          isDepartmentPharmacyFormEnum(values.type as string) ? 'required' : '',
        componentProps: () => ({
          options: organizationOptions.value,
          filterable: true,
          clearable: true,
          loading: initLoading.value,
          placeholder: '请选择上级科室',
        }),
      },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '名称',
      rules: 'required',
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
    class="w-[600px]"
    :title="isEditMode ? '编辑' : '新增'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>

<style scoped>
:deep(.warehouse-form-type-radio) {
  display: inline-flex;
  flex-wrap: nowrap;
  width: auto !important;
  max-width: none;
}

:deep(.warehouse-form-type-radio .el-radio) {
  margin-right: 20px;
}
</style>
