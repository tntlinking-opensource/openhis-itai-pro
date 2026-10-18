<script lang="ts" setup>
import type { ProcedureCatalogInitResponse, ProcedureCatalogItem } from '#/api';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@zxplat/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import {
  createProcedureCatalogApi,
  getProcedureCatalogApi,
  initProcedureCatalogApi,
  updateProcedureCatalogApi,
} from '#/api';

/** 国家临床版手术 3.0 */
const SOURCE_CLINICAL_PROC_V3 = 'clinical_proc_v3';
/** 医保手术与操作 */
const SOURCE_GENERAL_OP = 'general_op';

interface Props {
  visible?: boolean;
  formData?: Partial<ProcedureCatalogItem>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<Partial<ProcedureCatalogItem>>({});
const procedureCategoryList = ref<Array<{ label: string; value: number | string }>>([]);
const statusFlagOptions = ref<Array<{ label: string; value: number | string }>>([]);
/** 当前表单来源（驱动编码标签） */
const currentSource = ref<string>('');

const isEditMode = computed(() => !!props.formData?.id);
const isClinicalProcV3 = computed(() => currentSource.value === SOURCE_CLINICAL_PROC_V3);
const isGeneralOp = computed(() => currentSource.value === SOURCE_GENERAL_OP);

/**
 * 详情字段规范化：标志位与状态码统一为表单可绑类型。
 *
 * @param data 接口详情
 */
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

/**
 * 提交保存：编码都写在本行，不再做对侧目录选码/挂靠。
 */
async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }
  modalApi.lock(true);
  try {
    const formValues = await formApi.getValues();
    const mergedData = { ...originalData.value, ...formValues };
    const submitData: Partial<ProcedureCatalogItem> = {};
    Object.keys(mergedData).forEach((key) => {
      const value = mergedData[key];
      if (value !== null && value !== undefined) {
        submitData[key] = value;
      }
    });
    // 本行编码自洽：国临行 procedureCode=clinicCode；医保行 procedureCode=ybNo
    if (submitData.sourceEnum === SOURCE_CLINICAL_PROC_V3) {
      const code = String(submitData.procedureCode ?? '').trim();
      submitData.procedureCode = code || undefined;
      submitData.clinicCode = code || undefined;
      const yb = String(submitData.ybNo ?? '').trim();
      submitData.ybNo = yb || undefined;
      submitData.ybFlag = yb ? 1 : 0;
      submitData.ybMatchFlag = yb ? 1 : 0;
    } else if (submitData.sourceEnum === SOURCE_GENERAL_OP) {
      const code = String(submitData.procedureCode ?? '').trim();
      submitData.procedureCode = code || undefined;
      submitData.ybNo = code || undefined;
      submitData.ybFlag = 1;
      const clinic = String(submitData.clinicCode ?? '').trim();
      submitData.clinicCode = clinic || undefined;
      submitData.ybMatchFlag = clinic ? 1 : 0;
    }
    // 开立不依赖挂靠 AD，维护页不再维护该字段
    delete submitData.activityDefinitionId;

    if (isEditMode.value) {
      await updateProcedureCatalogApi(submitData);
      ElMessage.success('更新成功');
    } else {
      const { id: _id, ...createData } = submitData;
      await createProcedureCatalogApi(createData);
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

/**
 * 打开弹窗时装载详情或新增空表。
 */
async function loadFormData() {
  const id = props.formData?.id;
  if (id) {
    modalApi.setState({ loading: true });
    try {
      const response = await getProcedureCatalogApi(String(id));
      const data = response.data;
      if (data) {
        originalData.value = { ...data };
        currentSource.value = String(data.sourceEnum ?? '');
        formApi.setValues(normalizeDetailForForm(data as Record<string, unknown>));
      } else {
        ElMessage.warning('未找到手术目录信息');
        modalApi.close();
      }
    } catch (error) {
      console.error('获取手术目录详情失败:', error);
      ElMessage.error('获取手术目录详情失败，请稍后重试');
      modalApi.close();
    } finally {
      modalApi.setState({ loading: false });
    }
  } else if (props.formData && Object.keys(props.formData).length > 0) {
    originalData.value = { ...props.formData };
    currentSource.value = String(props.formData.sourceEnum ?? '');
    formApi.setValues(normalizeDetailForForm(props.formData as Record<string, unknown>));
  } else {
    originalData.value = {};
    currentSource.value = '';
    await formApi.resetForm();
  }
}

/**
 * 加载目录分类与状态选项。
 */
async function loadInitData() {
  try {
    const res = await initProcedureCatalogApi();
    const data: ProcedureCatalogInitResponse = res.data || ({} as ProcedureCatalogInitResponse);
    procedureCategoryList.value =
      (data.procedureCategoryList as Array<{ label: string; value: number | string }>) || [];
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

/**
 * 下拉选项转 Form Select 结构。
 *
 * @param options 初始化选项
 */
function convertOptions(
  options: Array<{ label: string; value: number | string }> | null | undefined,
) {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  return options.map((item) => ({
    label: item.label,
    value: item.value,
  }));
}

/**
 * 本行辅码：国临行可填医保编码；医保行可填国临码——均为本表输入，不对侧选码。
 */
const sameLineExtraCodeSchema = computed(() => {
  if (isClinicalProcV3.value) {
    return [
      {
        component: 'Input' as const,
        fieldName: 'ybNo',
        label: '医保编码',
        componentProps: {
          placeholder: '可选，本行填写医保手术编码',
          clearable: true,
        },
      },
    ];
  }
  if (isGeneralOp.value) {
    return [
      {
        component: 'Input' as const,
        fieldName: 'clinicCode',
        label: '国临码',
        componentProps: {
          placeholder: '可选，本行填写国临手术编码',
          clearable: true,
        },
      },
    ];
  }
  return [];
});

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
    wrapperClass: 'procedure-form-dialog__grid grid grid-cols-2 gap-x-6',
    schema: computed(() => [
      {
        component: 'Input',
        fieldName: 'name',
        label: '名称',
        rules: 'required',
        componentProps: { placeholder: '请输入名称' },
      },
      {
        component: 'Select',
        fieldName: 'sourceEnum',
        label: '手术目录',
        rules: 'required',
        componentProps: {
          options: convertOptions(procedureCategoryList.value),
          filterable: true,
          placeholder: '请选择目录分类',
          clearable: true,
          disabled: isEditMode.value,
          onChange: (v: string) => {
            currentSource.value = String(v ?? '');
            formApi.setFieldValue('clinicCode', undefined);
            formApi.setFieldValue('ybNo', undefined);
          },
        },
      },
      {
        component: 'Input',
        fieldName: 'procedureCode',
        label: isClinicalProcV3.value ? '国临编码' : isGeneralOp.value ? '医保编码' : '编码',
        componentProps: {
          placeholder: isClinicalProcV3.value
            ? '请输入国家临床版手术编码'
            : isGeneralOp.value
              ? '请输入医保手术编码'
              : '请先选择手术目录',
          clearable: true,
          disabled: !currentSource.value,
        },
      },
      {
        component: 'Input',
        fieldName: 'typeCode',
        label: '类别',
        componentProps: { placeholder: '请输入手术类别', clearable: true },
      },
      ...sameLineExtraCodeSchema.value,
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
        component: 'Input',
        fieldName: 'pyStr',
        label: '拼音助记',
        componentProps: { placeholder: '请输入拼音助记码', clearable: true },
      },
      {
        component: 'Input',
        fieldName: 'wbStr',
        label: '五笔助记',
        componentProps: { placeholder: '请输入五笔助记码', clearable: true },
      },
      {
        component: 'InputArea',
        fieldName: 'description',
        label: '说明',
        formItemClass: 'col-span-2',
        componentProps: { placeholder: '请输入说明', rows: 3, style: fullWidthStyle },
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
    class="procedure-form-dialog w-[760px]"
    :title="isEditMode ? '编辑手术目录' : '新增手术目录'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <p class="procedure-form-dialog__hint">
      编码写在本行即可：主编码按目录类型填写；另一编码为可选文本，不再从对侧目录选码、也不挂靠诊疗项目。
    </p>
    <Form />
  </Modal>
</template>

<style scoped>
.procedure-form-dialog :deep(.procedure-form-dialog__grid) {
  align-items: start;
}

.procedure-form-dialog :deep(.el-form-item) {
  margin-bottom: 12px;
}

.procedure-form-dialog__hint {
  margin: 0 0 12px;
  color: #666;
  font-size: 12px;
  line-height: 1.5;
}
</style>
