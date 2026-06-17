<script lang="ts" setup>
import type { DiagnosisCatalogInitResponse, DiagnosisCatalogItem } from '#/api';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import {
  createDiagnosisCatalogApi,
  getDiagnosisCatalogApi,
  initDiagnosisCatalogApi,
  updateDiagnosisCatalogApi,
} from '#/api';

interface Props {
  visible?: boolean;
  formData?: Partial<DiagnosisCatalogItem>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<Partial<DiagnosisCatalogItem>>({});

const diagnosisCategoryList = ref<Array<{ label: string; value: number | string }>>([]);
const ybDiagTypes = ref<Array<{ label: string; value: number | string }>>([]);
const statusFlagOptions = ref<Array<{ label: string; value: number | string }>>([]);

const isEditMode = computed(() => {
  return !!props.formData?.id;
});

function normalizeDetailForForm(data: Record<string, unknown>) {
  const next: Record<string, unknown> = { ...data };
  if (next.ybFlag === 1 || next.ybFlag === '1') {
    next.ybFlag = true;
  } else if (next.ybFlag === 0 || next.ybFlag === '0') {
    next.ybFlag = false;
  }
  if (next.ybMatchFlag === 1 || next.ybMatchFlag === '1') {
    next.ybMatchFlag = true;
  } else if (next.ybMatchFlag === 0 || next.ybMatchFlag === '0') {
    next.ybMatchFlag = false;
  }
  if (next.statusEnum !== undefined && next.statusEnum !== null) {
    next.statusEnum = String(next.statusEnum);
  }
  return next;
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
    const submitData: Partial<DiagnosisCatalogItem> = {};
    Object.keys(mergedData).forEach((key) => {
      const value = mergedData[key];
      if (value !== null && value !== undefined) {
        submitData[key] = value;
      }
    });

    if (submitData.ybFlag !== undefined) {
      submitData.ybFlag = submitData.ybFlag ? 1 : 0;
    }
    if (submitData.ybMatchFlag !== undefined) {
      submitData.ybMatchFlag = submitData.ybMatchFlag ? 1 : 0;
    }

    if (isEditMode.value) {
      await updateDiagnosisCatalogApi(submitData);
      ElMessage.success('更新成功');
    } else {
      const { id: _id, ...createData } = submitData;
      await createDiagnosisCatalogApi(createData);
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
  if (id) {
    modalApi.setState({ loading: true });
    try {
      const response = await getDiagnosisCatalogApi(String(id));
      const data = response.data;
      if (data) {
        originalData.value = { ...data };
        const normalized = normalizeDetailForForm(data as Record<string, unknown>);
        formApi.setValues(normalized);
      } else {
        ElMessage.warning('未找到疾病目录信息');
        modalApi.close();
      }
    } catch (error) {
      console.error('获取疾病目录详情失败:', error);
      ElMessage.error('获取疾病目录详情失败，请稍后重试');
      modalApi.close();
      return;
    } finally {
      modalApi.setState({ loading: false });
    }
  } else {
    if (props.formData && Object.keys(props.formData).length > 0) {
      originalData.value = { ...props.formData };
      const normalized = normalizeDetailForForm(props.formData as Record<string, unknown>);
      formApi.setValues(normalized);
    } else {
      originalData.value = {};
      await formApi.resetForm();
    }
  }
}

async function loadInitData() {
  try {
    const res = await initDiagnosisCatalogApi();
    const data: DiagnosisCatalogInitResponse = res.data || ({} as DiagnosisCatalogInitResponse);
    diagnosisCategoryList.value =
      (data.diseaseCategoryList as Array<{ label: string; value: number | string }>) || [];
    ybDiagTypes.value =
      (data.ybDiagTypes as Array<{ label: string; value: number | string }>) || [];
    statusFlagOptions.value =
      (data.statusFlagOptions as Array<{ label: string; value: number | string }>) || [];
  } catch (error) {
    console.error('加载初始化数据失败:', error);
  }
}

onMounted(() => {
  loadInitData();
});

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

const fullWidthStyle = { width: '100%' };

const convertOptions = (
  options: Array<{ label: string; value: number | string }> | null | undefined,
) => {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  return options.map((item) => ({
    label: item.label,
    value: item.value,
  }));
};

const [Form, formApi] = useVbenForm(
  reactive({
    commonConfig: {
      labelWidth: 96,
      labelClass: 'justify-start text-left',
      componentProps: {
        style: fullWidthStyle,
      },
    },
    layout: 'horizontal',
    wrapperClass: 'diagnosis-form-dialog__grid grid grid-cols-2 gap-x-6',
    schema: computed(() => [
      {
        component: 'Input',
        fieldName: 'name',
        label: '名称',
        rules: 'required',
        componentProps: {
          placeholder: '请输入名称',
        },
      },
      {
        component: 'Select',
        fieldName: 'sourceEnum',
        label: '诊断目录',
        rules: 'required',
        componentProps: {
          options: convertOptions(diagnosisCategoryList.value),
          filterable: true,
          placeholder: '请选择疾病类型',
          clearable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'typeCode',
        label: '诊断类型',
        componentProps: {
          options: convertOptions(ybDiagTypes.value),
          filterable: true,
          placeholder: '请选择诊断类型',
          clearable: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'diseaseCode',
        label: '特慢病编码',
        componentProps: {
          placeholder: '请输入特慢病编码',
          clearable: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'ybNo',
        label: '医保编码',
        componentProps: {
          placeholder: '请输入医保编码',
          clearable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'statusEnum',
        label: '状态',
        componentProps: {
          options: convertOptions(statusFlagOptions.value),
          filterable: true,
          placeholder: '请选择状态',
          clearable: true,
        },
      },
      {
        component: 'Checkbox',
        fieldName: 'ybFlag',
        label: '医保标记',
      },
      {
        component: 'Checkbox',
        fieldName: 'ybMatchFlag',
        label: '医保对码',
      },
      {
        component: 'Input',
        fieldName: 'pyStr',
        label: '拼音助记',
        componentProps: {
          placeholder: '请输入拼音助记码',
          clearable: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'wbStr',
        label: '五笔助记',
        componentProps: {
          placeholder: '请输入五笔助记码',
          clearable: true,
        },
      },
      {
        component: 'InputArea',
        fieldName: 'description',
        label: '说明',
        formItemClass: 'col-span-2',
        componentProps: {
          placeholder: '请输入说明',
          rows: 3,
          style: fullWidthStyle,
        },
      },
    ]),
    showDefaultActions: false,
  }),
);

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
    class="diagnosis-form-dialog w-[720px]"
    :title="isEditMode ? '编辑疾病目录' : '新增疾病目录'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>

<style scoped>
.diagnosis-form-dialog :deep(.diagnosis-form-dialog__grid) {
  align-items: start;
}

.diagnosis-form-dialog :deep(.el-form-item) {
  margin-bottom: 12px;
}
</style>
