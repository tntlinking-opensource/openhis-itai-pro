<script lang="ts" setup>
import type { ProcedureCatalogInitResponse, ProcedureCatalogItem } from '#/api';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElButton, ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import {
  createProcedureCatalogApi,
  getProcedureCatalogApi,
  initProcedureCatalogApi,
  pageProcedureCatalogApi,
  updateProcedureCatalogApi,
} from '#/api';
import SelectTable from '#/components/select-table.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

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
/** 当前表单来源（驱动字段只读/选码） */
const currentSource = ref<string>('');
/** 国临选码展示名（general_op 侧） */
const clinicSelectedLabel = ref('');
/** 医保选码展示名（clinical_proc_v3 侧） */
const ybSelectedLabel = ref('');

const isEditMode = computed(() => !!props.formData?.id);
const isClinicalProcV3 = computed(() => currentSource.value === SOURCE_CLINICAL_PROC_V3);
const isGeneralOp = computed(() => currentSource.value === SOURCE_GENERAL_OP);

const crossCodeColumns = [
  { prop: 'procedureCode', label: '编码', width: 140 },
  { prop: 'name', label: '名称', minWidth: 220 },
  { prop: 'pyStr', label: '拼音', width: 100 },
];

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

async function searchCatalogBySource(sourceEnum: string, params: Record<string, unknown>) {
  const response = await pageProcedureCatalogApi({
    pageNo: Number(params.pageNo ?? 1) || 1,
    pageSize: Number(params.pageSize ?? 20) || 20,
    keyword: String(params.keyword ?? '').trim() || undefined,
    sourceEnum,
  });
  const parsed = parsePagedRowsFromApiData<ProcedureCatalogItem>(response.data);
  const content = (parsed.content ?? []).map((item) => ({
    ...item,
    value: String(item.procedureCode ?? ''),
    label: String(item.name ?? ''),
  }));
  return { content, totalCount: parsed.totalCount };
}

function remoteSearchClinicalProcV3(params: Record<string, unknown>) {
  return searchCatalogBySource(SOURCE_CLINICAL_PROC_V3, params);
}

function remoteSearchGeneralOp(params: Record<string, unknown>) {
  return searchCatalogBySource(SOURCE_GENERAL_OP, params);
}

function onClinicCodePicked(code: string | string[]) {
  const v = Array.isArray(code) ? (code[0] ?? '') : code;
  formApi.setFieldValue('clinicCode', v || undefined);
}

function onClinicBeforeClose(item: Record<string, unknown>) {
  clinicSelectedLabel.value = String(item.name ?? item.label ?? '');
  return true;
}

function onYbNoPicked(code: string | string[]) {
  const v = Array.isArray(code) ? (code[0] ?? '') : code;
  formApi.setFieldValue('ybNo', v || undefined);
}

function onYbBeforeClose(item: Record<string, unknown>) {
  ybSelectedLabel.value = String(item.name ?? item.label ?? '');
  formApi.setFieldValue('ybNo', String(item.procedureCode ?? item.value ?? '') || undefined);
  return true;
}

function clearClinicCode() {
  formApi.setFieldValue('clinicCode', undefined);
  clinicSelectedLabel.value = '';
}

function clearYbNo() {
  formApi.setFieldValue('ybNo', undefined);
  ybSelectedLabel.value = '';
}

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
    if (submitData.sourceEnum === SOURCE_CLINICAL_PROC_V3) {
      submitData.clinicCode = String(submitData.procedureCode ?? '').trim() || undefined;
      const yb = String(submitData.ybNo ?? '').trim();
      submitData.ybNo = yb || undefined;
      submitData.ybFlag = yb ? 1 : 0;
      submitData.ybMatchFlag = yb ? 1 : 0;
    } else if (submitData.sourceEnum === SOURCE_GENERAL_OP) {
      const yb =
        String(submitData.ybNo ?? '').trim() || String(submitData.procedureCode ?? '').trim();
      submitData.ybNo = yb || undefined;
      submitData.ybFlag = 1;
      const clinic = String(submitData.clinicCode ?? '').trim();
      submitData.ybMatchFlag = clinic ? 1 : 0;
    }

    if (isEditMode.value) {
      await updateProcedureCatalogApi(submitData);
      ElMessage.success('更新成功（对照已同步到对侧目录）');
    } else {
      const { id: _id, ...createData } = submitData;
      await createProcedureCatalogApi(createData);
      ElMessage.success('创建成功（对照已同步到对侧目录）');
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
  clinicSelectedLabel.value = '';
  ybSelectedLabel.value = '';
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
        clinicSelectedLabel.value = String(data.clinicCode ?? '');
        ybSelectedLabel.value = String(data.ybNo ?? '');
        if (data.sourceEnum === SOURCE_GENERAL_OP && data.clinicCode) {
          await resolveLabel(SOURCE_CLINICAL_PROC_V3, String(data.clinicCode), (n) => {
            clinicSelectedLabel.value = n;
          });
        }
        if (data.sourceEnum === SOURCE_CLINICAL_PROC_V3 && data.ybNo) {
          await resolveLabel(SOURCE_GENERAL_OP, String(data.ybNo), (n) => {
            ybSelectedLabel.value = n;
          });
        }
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

async function resolveLabel(sourceEnum: string, code: string, apply: (name: string) => void) {
  try {
    const res = await pageProcedureCatalogApi({
      pageNo: 1,
      pageSize: 5,
      keyword: code,
      sourceEnum,
    });
    const parsed = parsePagedRowsFromApiData<ProcedureCatalogItem>(res.data);
    const hit = (parsed.content ?? []).find((r) => String(r.procedureCode) === code);
    if (hit?.name) {
      apply(`${hit.procedureCode} ${hit.name}`);
    }
  } catch {
    // 展示编码即可
  }
}

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

/** 对照选码区：按来源只留一边 SelectTable */
const crossCodeSchema = computed(() => {
  if (isClinicalProcV3.value) {
    return [
      {
        component: 'Input' as const,
        fieldName: 'ybNo',
        label: '医保编码',
        formItemClass: 'col-span-2',
      },
    ];
  }
  if (isGeneralOp.value) {
    return [
      {
        component: 'Input' as const,
        fieldName: 'clinicCode',
        label: '国临码',
        formItemClass: 'col-span-2',
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
            clinicSelectedLabel.value = '';
            ybSelectedLabel.value = '';
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
          onChange: async (v: string) => {
            if (isClinicalProcV3.value) {
              formApi.setFieldValue('clinicCode', v || undefined);
            }
            if (isGeneralOp.value) {
              formApi.setFieldValue('ybNo', v || undefined);
            }
          },
        },
      },
      {
        component: 'Input',
        fieldName: 'typeCode',
        label: '类别',
        componentProps: { placeholder: '请输入手术类别', clearable: true },
      },
      ...crossCodeSchema.value,
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
    <p class="procedure-form-dialog__hint">对照请从对侧目录选择，保存后自动同步。</p>
    <Form>
      <!-- 插槽名必须等于 fieldName，Form 按 fieldName 分发 -->
      <template #ybNo="slotProps">
        <div v-if="isClinicalProcV3" class="procedure-form-dialog__picker">
          <SelectTable
            :model-value="slotProps?.value ?? undefined"
            :selected-label="ybSelectedLabel"
            placeholder="从「手术操作目录」选择医保编码"
            :remote-method="remoteSearchGeneralOp"
            :columns="crossCodeColumns"
            value-key="procedureCode"
            label-key="name"
            :panel-width="640"
            :before-close="onYbBeforeClose"
            @update:model-value="onYbNoPicked"
          />
          <ElButton link type="danger" @click="clearYbNo">清除</ElButton>
        </div>
      </template>
      <template #clinicCode="slotProps">
        <div v-if="isGeneralOp" class="procedure-form-dialog__picker">
          <SelectTable
            :model-value="slotProps?.value ?? undefined"
            :selected-label="clinicSelectedLabel"
            placeholder="从「国家临床版手术3.0」选择国临码"
            :remote-method="remoteSearchClinicalProcV3"
            :columns="crossCodeColumns"
            value-key="procedureCode"
            label-key="name"
            :panel-width="640"
            :before-close="onClinicBeforeClose"
            @update:model-value="onClinicCodePicked"
          />
          <ElButton link type="danger" @click="clearClinicCode">清除</ElButton>
        </div>
      </template>
    </Form>
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

.procedure-form-dialog__picker {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.procedure-form-dialog__picker :deep(.vxe-pulldown),
.procedure-form-dialog__picker :deep(.vxe-input) {
  flex: 1;
  width: 100%;
}
</style>
