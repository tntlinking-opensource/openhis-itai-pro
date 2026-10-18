<script lang="ts" setup>
import type { DiagnosisCatalogInitResponse, DiagnosisCatalogItem } from '#/api';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@zxplat/common-ui';

import { ElButton, ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import {
  createDiagnosisCatalogApi,
  getDiagnosisCatalogApi,
  initDiagnosisCatalogApi,
  pageDiagnosisCatalogApi,
  updateDiagnosisCatalogApi,
} from '#/api';
import SelectTable from '#/components/select-table.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

/** 国家临床版 2.0 */
const SOURCE_CLINICAL_V2 = 'clinical_v2';
/** 院内诊断（医院自维护） */
const SOURCE_HOSPITAL_DX = 'hospital_dx';
/** 疾病与诊断目录（医保 1307 主对照） */
const SOURCE_GENERAL_DX = 'general_dx';

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
const currentSource = ref<string>('');
const clinicSelectedLabel = ref('');
const ybSelectedLabel = ref('');

const isEditMode = computed(() => !!props.formData?.id);
const isClinicalV2 = computed(() => currentSource.value === SOURCE_CLINICAL_V2);
const isHospitalDx = computed(() => currentSource.value === SOURCE_HOSPITAL_DX);
/** 医保类诊断目录（非国临、非院内）：编码默认即医保码 */
const isYbDiagnosisCatalog = computed(
  () =>
    !!currentSource.value &&
    currentSource.value !== SOURCE_CLINICAL_V2 &&
    currentSource.value !== SOURCE_HOSPITAL_DX,
);

const crossCodeColumns = [
  { prop: 'conditionCode', label: '编码', width: 140 },
  { prop: 'name', label: '名称', minWidth: 220 },
  { prop: 'pyStr', label: '拼音', width: 100 },
  { prop: 'sourceEnum', label: '来源', width: 120 },
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

async function searchCatalogBySource(
  sourceEnum: string | undefined,
  params: Record<string, unknown>,
) {
  const response = await pageDiagnosisCatalogApi({
    pageNo: Number(params.pageNo ?? 1) || 1,
    pageSize: Number(params.pageSize ?? 20) || 20,
    keyword: String(params.keyword ?? '').trim() || undefined,
    sourceEnum,
  });
  const parsed = parsePagedRowsFromApiData<DiagnosisCatalogItem>(response.data);
  let rows = parsed.content ?? [];
  if (!sourceEnum) {
    rows = rows.filter((r) => String(r.sourceEnum) !== SOURCE_CLINICAL_V2);
  }
  const content = rows.map((item) => ({
    ...item,
    value: String(item.conditionCode ?? ''),
    label: String(item.name ?? ''),
  }));
  return { content, totalCount: parsed.totalCount };
}

function remoteSearchClinicalV2(params: Record<string, unknown>) {
  return searchCatalogBySource(SOURCE_CLINICAL_V2, params);
}

/** 国临目录选医保：优先疾病与诊断目录 */
function remoteSearchYbDx(params: Record<string, unknown>) {
  return searchCatalogBySource(SOURCE_GENERAL_DX, params);
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
  const code = String(item.ybNo ?? item.conditionCode ?? item.value ?? '').trim();
  formApi.setFieldValue('ybNo', code || undefined);
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

    if (submitData.sourceEnum === SOURCE_CLINICAL_V2) {
      submitData.clinicCode = String(submitData.conditionCode ?? '').trim() || undefined;
      const yb = String(submitData.ybNo ?? '').trim();
      submitData.ybNo = yb || undefined;
      submitData.ybFlag = yb ? 1 : 0;
      submitData.ybMatchFlag = yb ? 1 : 0;
    } else if (submitData.sourceEnum === SOURCE_HOSPITAL_DX) {
      const yb = String(submitData.ybNo ?? '').trim();
      const clinic = String(submitData.clinicCode ?? '').trim();
      submitData.ybNo = yb || undefined;
      submitData.clinicCode = clinic || undefined;
      submitData.ybFlag = yb ? 1 : 0;
      submitData.ybMatchFlag = yb && clinic ? 1 : 0;
    } else if (submitData.sourceEnum) {
      // 医保类目录：主码即医保码；不在此维护国临挂接（真源=国临绑医保）
      const yb =
        String(submitData.ybNo ?? '').trim() || String(submitData.conditionCode ?? '').trim();
      submitData.ybNo = yb || undefined;
      submitData.ybFlag = yb ? 1 : 0;
      // 保留库内已有 clinic_code（国临侧回写），避免编辑医保行时误清空
      const originalClinic = String(originalData.value.clinicCode ?? '').trim();
      submitData.clinicCode = originalClinic || undefined;
      submitData.ybMatchFlag = originalClinic ? 1 : 0;
    }

    if (isEditMode.value) {
      await updateDiagnosisCatalogApi(submitData);
      ElMessage.success('更新成功（对照已同步到对侧目录）');
    } else {
      const { id: _id, ...createData } = submitData;
      await createDiagnosisCatalogApi(createData);
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

async function resolveLabel(sourceEnum: string, code: string, apply: (name: string) => void) {
  try {
    const res = await pageDiagnosisCatalogApi({
      pageNo: 1,
      pageSize: 5,
      keyword: code,
      sourceEnum,
    });
    const parsed = parsePagedRowsFromApiData<DiagnosisCatalogItem>(res.data);
    const hit = (parsed.content ?? []).find(
      (r) => String(r.conditionCode) === code || String(r.ybNo) === code,
    );
    if (hit?.name) {
      apply(`${hit.conditionCode} ${hit.name}`);
    }
  } catch {
    // ignore
  }
}

async function loadFormData() {
  clinicSelectedLabel.value = '';
  ybSelectedLabel.value = '';
  const id = props.formData?.id;
  if (id) {
    modalApi.setState({ loading: true });
    try {
      const response = await getDiagnosisCatalogApi(String(id));
      const data = response.data;
      if (data) {
        originalData.value = { ...data };
        currentSource.value = String(data.sourceEnum ?? '');
        formApi.setValues(normalizeDetailForForm(data as Record<string, unknown>));
        clinicSelectedLabel.value = String(data.clinicCode ?? '');
        ybSelectedLabel.value = String(data.ybNo ?? '');
        // 仅院内诊断编辑页仍展示国临选码，需回填标签
        if (data.sourceEnum === SOURCE_HOSPITAL_DX && data.clinicCode) {
          await resolveLabel(SOURCE_CLINICAL_V2, String(data.clinicCode), (n) => {
            clinicSelectedLabel.value = n;
          });
        }
        if (
          (data.sourceEnum === SOURCE_CLINICAL_V2 || data.sourceEnum === SOURCE_HOSPITAL_DX) &&
          data.ybNo
        ) {
          await resolveLabel(SOURCE_GENERAL_DX, String(data.ybNo), (n) => {
            ybSelectedLabel.value = n;
          });
        }
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

/** 对照选码区：仅国临可绑医保；院内可同时挂国临/医保；医保目录不维护国临挂接 */
const crossCodeSchema = computed(() => {
  if (isClinicalV2.value) {
    return [
      {
        component: 'Input' as const,
        fieldName: 'ybNo',
        label: '医保编码',
        formItemClass: 'col-span-2',
      },
    ];
  }
  if (isHospitalDx.value) {
    return [
      {
        component: 'Input' as const,
        fieldName: 'clinicCode',
        label: '国临码',
      },
      {
        component: 'Input' as const,
        fieldName: 'ybNo',
        label: '医保编码',
      },
    ];
  }
  // 医保类目录：对照真源在国临侧，编辑页不出现「绑国临码」
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
          disabled: isEditMode.value,
          onChange: (v: string) => {
            currentSource.value = String(v ?? '');
            // 切换来源时清空对侧选码，避免脏数据
            formApi.setFieldValue('clinicCode', undefined);
            formApi.setFieldValue('ybNo', undefined);
            clinicSelectedLabel.value = '';
            ybSelectedLabel.value = '';
          },
        },
      },
      {
        component: 'Input',
        fieldName: 'conditionCode',
        label: isClinicalV2.value
          ? '国临编码'
          : isHospitalDx.value
            ? '院内编码'
            : isYbDiagnosisCatalog.value
              ? '诊断编码'
              : '编码',
        componentProps: {
          placeholder: isClinicalV2.value
            ? '请输入国家临床版诊断编码'
            : isHospitalDx.value
              ? '请输入院内诊断编码'
              : isYbDiagnosisCatalog.value
                ? '请输入诊断/医保编码'
                : '请先选择诊断目录',
          clearable: true,
          disabled: !currentSource.value,
          onChange: async (v: string) => {
            if (isClinicalV2.value) {
              formApi.setFieldValue('clinicCode', v || undefined);
            }
            if (isYbDiagnosisCatalog.value) {
              formApi.setFieldValue('ybNo', v || undefined);
            }
          },
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
    class="diagnosis-form-dialog w-[760px]"
    :title="isEditMode ? '编辑疾病目录' : '新增疾病目录'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <p class="diagnosis-form-dialog__hint">
      {{
        isHospitalDx
          ? '院内诊断以院内编码为主；国临码/医保编码为可选对照，不自动回写对侧目录。'
          : isClinicalV2
            ? '对照请从疾病与诊断目录选择医保编码，保存后自动同步。'
            : isYbDiagnosisCatalog
              ? '医保目录仅维护本目录编码/名称；国临对照请在「国家临床版2.0」中绑定医保码。'
              : '对照请从对侧目录选择，保存后自动同步。'
      }}
    </p>
    <Form>
      <!-- 插槽名必须等于 fieldName，Form 按 fieldName 分发 -->
      <template #ybNo="slotProps">
        <div v-if="isClinicalV2 || isHospitalDx" class="diagnosis-form-dialog__picker">
          <SelectTable
            :model-value="slotProps?.value ?? undefined"
            :selected-label="ybSelectedLabel"
            placeholder="从「疾病与诊断目录」选择医保编码"
            :remote-method="remoteSearchYbDx"
            :columns="crossCodeColumns"
            value-key="conditionCode"
            label-key="name"
            :panel-width="680"
            :before-close="onYbBeforeClose"
            @update:model-value="onYbNoPicked"
          />
          <ElButton link type="danger" @click="clearYbNo">清除</ElButton>
        </div>
      </template>
      <template #clinicCode="slotProps">
        <div v-if="isHospitalDx" class="diagnosis-form-dialog__picker">
          <SelectTable
            :model-value="slotProps?.value ?? undefined"
            :selected-label="clinicSelectedLabel"
            placeholder="从「国家临床版2.0」选择国临码"
            :remote-method="remoteSearchClinicalV2"
            :columns="crossCodeColumns"
            value-key="conditionCode"
            label-key="name"
            :panel-width="680"
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
.diagnosis-form-dialog :deep(.diagnosis-form-dialog__grid) {
  align-items: start;
}

.diagnosis-form-dialog :deep(.el-form-item) {
  margin-bottom: 12px;
}

.diagnosis-form-dialog__hint {
  margin: 0 0 12px;
  color: #666;
  font-size: 12px;
  line-height: 1.5;
}

.diagnosis-form-dialog__picker {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.diagnosis-form-dialog__picker :deep(.vxe-pulldown),
.diagnosis-form-dialog__picker :deep(.vxe-input) {
  flex: 1;
  width: 100%;
}
</style>
