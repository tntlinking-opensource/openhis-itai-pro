<script lang="ts" setup>
import type {
  CreateDeviceCatalogParams,
  DeviceCatalogInitResponse,
  DeviceCatalogItem,
  UpdateDeviceCatalogParams,
} from '#/api';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { ElButton, ElDialog, ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import {
  createDeviceCatalogApi,
  getDeviceCatalogApi,
  initDeviceCatalogApi,
  updateDeviceCatalogApi,
} from '#/api';

interface Props {
  modelValue: boolean;
  /** 'new' 或耗材定义 id */
  catalogId: string;
  /** 列表页已拉取的字典选项，减少重复请求 */
  initOptionsSource: DeviceCatalogInitResponse;
}

defineOptions({
  name: 'DeviceCatalogEditDialog',
});

const props = defineProps<Props>();

const emit = defineEmits<{
  success: [];
  'update:modelValue': [value: boolean];
}>();

type OptionItem = {
  children?: OptionItem[];
  info?: string;
  label?: string;
  name?: string;
  text?: string;
  value: number | string;
};

const loading = ref(false);
const formData = ref<Partial<DeviceCatalogItem>>({});
const initOptions = ref<DeviceCatalogInitResponse>({} as DeviceCatalogInitResponse);
const originalData = ref<Partial<DeviceCatalogItem>>({});

const BOOLEAN_FLAG_FIELDS = ['allergenFlag', 'ybFlag', 'ybMatchFlag', 'hvcmFlag'] as const;

/** 业务信息内联复选组 */
const CHECKBOX_INLINE_GROUP_FIELDS = ['allergenFlag', 'ybFlag', 'hvcmFlag'] as const;

const convertOptions = (
  options: null | OptionItem[] | undefined,
): Array<{
  children?: Array<{ label: string; value: string }>;
  label: string;
  value: string;
}> => {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  return options.map((item) => ({
    label: item.label || item.info || item.text || item.name || String(item.value),
    value: String(item.value),
    children: item.children ? convertOptions(item.children) : undefined,
  }));
};

const createOptionsComputed = (key: string) =>
  computed(() => convertOptions((initOptions.value?.[key] as OptionItem[]) || null));

const deviceCategories = createOptionsComputed('deviceCategories');
const equipmentTypes = createOptionsComputed('equipmentTypes');
const exeOrganizations = createOptionsComputed('exeOrganizations');
const supplierListOptions = createOptionsComputed('supplierListOptions');
/** 五类计量单位统一候选（unit_code），与药品目录一致；旧 init 无 unitCodeOptions 时回落 packUnits */
const unitCodeOptions = computed(() => {
  const unified = convertOptions((initOptions.value?.unitCodeOptions as OptionItem[]) || null);
  if (unified.length > 0) {
    return unified;
  }
  return convertOptions((initOptions.value?.packUnits as OptionItem[]) || null);
});
const ybCategories = createOptionsComputed('ybCategories');
const financialTypes = createOptionsComputed('financialTypes');
const ybGrades = createOptionsComputed('ybGrades');
const locations = createOptionsComputed('locations');

/** 弹窗显隐，与 ElDialog v-model 同步 */
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => {
    emit('update:modelValue', v);
  },
});

const isEditMode = computed(() => {
  const id = String(props.catalogId || '');
  return !!id && id !== 'new';
});

/** 弹窗标题：新增 / 编辑（带出耗材名） */
const dialogTitle = computed(() => {
  if (!isEditMode.value) {
    return '新增耗材';
  }
  const name = formData.value?.name;
  return name ? `编辑耗材 — ${name}` : '编辑耗材';
});

/** 关闭弹窗（取消 / 点 X） */
function closeDialog() {
  emit('update:modelValue', false);
}

/** 保存成功后通知列表刷新并关闭 */
function finishSuccess() {
  emit('success');
  emit('update:modelValue', false);
}

/** 清空弹窗表单（setValues({}) 无法移除已填字段，须 resetForm） */
async function resetFormState() {
  formData.value = {};
  originalData.value = {};
  await formApi.resetForm();
  await formApi.setFieldValue('__checkbox_inline_group', {});
}

function handleDialogClosed() {
  void resetFormState();
  lastLoadedCatalogId.value = null;
}

/** 后端 Boolean；兼容历史 0/1 */
function coerceLegacyBool(value: unknown): boolean {
  return value === true || value === 1 || value === '1';
}

function normalizeBooleanFields(target: Record<string, unknown>) {
  BOOLEAN_FLAG_FIELDS.forEach((field) => {
    target[field] = coerceLegacyBool(target[field]);
  });
}

/** 历史 salesUnit* 与后端 packageUnit* 对齐（界面曾用 sales 命名） */
function mapLegacyPackageUnitFields(data: Record<string, unknown>) {
  if (
    data.salesUnitCode !== undefined &&
    data.salesUnitCode !== null &&
    data.salesUnitCode !== '' &&
    (data.packageUnitCode === undefined ||
      data.packageUnitCode === null ||
      data.packageUnitCode === '')
  ) {
    data.packageUnitCode = data.salesUnitCode;
  }
  if (
    data.salesUnitRatio !== undefined &&
    data.salesUnitRatio !== null &&
    data.salesUnitRatio !== '' &&
    (data.packageUnitRatio === undefined ||
      data.packageUnitRatio === null ||
      data.packageUnitRatio === '')
  ) {
    data.packageUnitRatio = data.salesUnitRatio;
  }
}

/** 历史 unitCode/minUnitCode 与 purchaseUnitCode/splitUnitCode 对齐 */
function normalizeUnitFieldNames(values: Record<string, unknown>): Record<string, unknown> {
  const normalized = { ...values };
  mapLegacyPackageUnitFields(normalized);
  if (
    (normalized.purchaseUnitCode === undefined || normalized.purchaseUnitCode === null) &&
    normalized.unitCode !== undefined &&
    normalized.unitCode !== null
  ) {
    normalized.purchaseUnitCode = normalized.unitCode;
  }
  if (
    (normalized.splitUnitCode === undefined || normalized.splitUnitCode === null) &&
    normalized.minUnitCode !== undefined &&
    normalized.minUnitCode !== null
  ) {
    normalized.splitUnitCode = normalized.minUnitCode;
  }
  return normalized;
}

/** 下拉 value 统一为字符串，避免 number/string 不一致导致不回显 */
function normalizeFormSelectValues(data: Record<string, unknown>) {
  const selectFieldNames = [
    'categoryCode',
    'typeCode',
    'supplyId',
    'doseUnitCode',
    'orgId',
    'locationId',
    'purchaseUnitCode',
    'stockUnitCode',
    'packageUnitCode',
    'splitUnitCode',
    'defEncounterUnitCode',
    'defInhospitalUnitCode',
    'itemTypeCode',
    'ybType',
    'chrgitmLv',
  ];
  const next = normalizeUnitFieldNames({ ...data });
  selectFieldNames.forEach((field) => {
    const v = next[field];
    if (v !== undefined && v !== null && v !== '') {
      next[field] = String(v);
    }
  });
  return next;
}

async function loadInitOptions() {
  try {
    const response = await initDeviceCatalogApi();
    initOptions.value = response.data || ({} as DeviceCatalogInitResponse);
  } catch (error) {
    console.error('获取初始化选项失败:', error);
    ElMessage.error('获取初始化选项失败，请稍后重试');
  }
}

async function loadDetailData() {
  const id = String(props.catalogId || '');
  if (!id) {
    ElMessage.warning('缺少耗材ID');
    closeDialog();
    return;
  }

  if (id === 'new') {
    await resetFormState();
    return;
  }

  loading.value = true;
  try {
    const response = await getDeviceCatalogApi(id);
    const detail = response.data;
    if (!detail) {
      ElMessage.warning('未找到耗材信息');
      closeDialog();
      return;
    }

    originalData.value = { ...detail };
    const normalizedData = normalizeFormSelectValues(detail as Record<string, unknown>);
    normalizeBooleanFields(normalizedData);
    formData.value = { ...normalizedData };
    formApi.setValues(normalizedData);

    const inlineGroupValue: Record<string, boolean> = {};
    CHECKBOX_INLINE_GROUP_FIELDS.forEach((key) => {
      inlineGroupValue[key] = coerceLegacyBool(normalizedData[key]);
    });
    formApi.setFieldValue('__checkbox_inline_group', inlineGroupValue);
  } catch (error) {
    console.error('获取耗材详情失败:', error);
    ElMessage.error('获取耗材详情失败，请稍后重试');
    closeDialog();
  } finally {
    loading.value = false;
  }
}

function buildSubmitData(formValues: Record<string, unknown>): Partial<DeviceCatalogItem> {
  const { __checkbox_inline_group, ...restFormValues } = formValues;
  const mergedData = {
    ...originalData.value,
    ...normalizeUnitFieldNames(restFormValues),
  } as Record<string, unknown>;

  if (__checkbox_inline_group && typeof __checkbox_inline_group === 'object') {
    const group = __checkbox_inline_group as Record<string, unknown>;
    CHECKBOX_INLINE_GROUP_FIELDS.forEach((key) => {
      const value = group[key];
      if (value !== undefined && value !== null && value !== '') {
        mergedData[key] = value === true || value === 1 || value === '1' || value === 'true';
      }
    });
  }

  normalizeBooleanFields(mergedData);
  mapLegacyPackageUnitFields(mergedData);
  delete mergedData.salesUnitCode;
  delete mergedData.salesUnitRatio;
  const submitData: Partial<DeviceCatalogItem> = {};
  Object.keys(mergedData).forEach((key) => {
    if (key.startsWith('__')) {
      return;
    }
    const value = mergedData[key];
    if (value !== null && value !== undefined && value !== '') {
      submitData[key] = value;
    }
  });
  return submitData;
}

function buildUpdatePayload(submitData: Partial<DeviceCatalogItem>): UpdateDeviceCatalogParams {
  const updateData = { ...submitData } as Record<string, unknown>;
  updateData.purchaseUnitCode = updateData.purchaseUnitCode ?? updateData.unitCode;
  updateData.splitUnitCode = updateData.splitUnitCode ?? updateData.minUnitCode;
  updateData.packageUnitCode =
    updateData.packageUnitCode ?? updateData.salesUnitCode ?? updateData.packageUnitCode;
  updateData.packageUnitRatio =
    updateData.packageUnitRatio ?? updateData.salesUnitRatio ?? updateData.packageUnitRatio;
  delete updateData.salesUnitCode;
  delete updateData.salesUnitRatio;
  delete updateData.unitCode;
  delete updateData.minUnitCode;
  return updateData as UpdateDeviceCatalogParams;
}

function buildCreatePayload(submitData: Partial<DeviceCatalogItem>): CreateDeviceCatalogParams {
  const { id: _id, unitCode: _unitCode, minUnitCode: _minUnitCode, ...restData } = submitData;
  const restDataRecord = restData as Record<string, unknown>;
  const purchaseUnitCode = String(restDataRecord.purchaseUnitCode ?? restData.unitCode ?? '');
  const splitUnitCode = String(restDataRecord.splitUnitCode ?? restData.minUnitCode ?? '');
  return {
    ...restData,
    purchaseUnitCode,
    splitUnitCode,
    packageUnitCode: (restDataRecord.packageUnitCode ??
      restDataRecord.salesUnitCode ??
      '') as string,
    packageUnitRatio: (restDataRecord.packageUnitRatio ?? restDataRecord.salesUnitRatio) as
      | number
      | undefined,
  } as CreateDeviceCatalogParams;
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  loading.value = true;
  try {
    const formValues = (await formApi.getValues()) as Record<string, unknown>;
    const submitData = buildSubmitData(formValues);

    if (isEditMode.value) {
      const updateData = buildUpdatePayload(submitData);
      await updateDeviceCatalogApi(updateData);
      ElMessage.success('更新成功');
      formData.value = { ...submitData };
    } else {
      const createData = buildCreatePayload(submitData);
      await createDeviceCatalogApi(createData);
      ElMessage.success('创建成功');
    }

    finishSuccess();
  } catch (error) {
    console.error(isEditMode.value ? '更新失败:' : '创建失败:', error);
    ElMessage.error(isEditMode.value ? '更新失败，请稍后重试' : '创建失败，请稍后重试');
  } finally {
    loading.value = false;
  }
}

const [Form, formApi] = useVbenForm(
  reactive({
    layout: 'horizontal',
    commonConfig: {
      labelClass: 'justify-start text-left',
    },
    wrapperClass: 'grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4',
    schema: computed(() => [
      {
        component: 'SectionTitle',
        fieldName: '__section_basic',
        label: '基础信息',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0 !pb-0 device-catalog-form-dialog__section-title',
        componentProps: {
          title: '基础信息',
        },
      },
      {
        component: 'Input',
        fieldName: 'busNo',
        label: '耗材编号',
        hide: true,
        componentProps: {
          disabled: props.catalogId !== 'new',
        },
      },
      {
        component: 'Input',
        fieldName: 'name',
        label: '耗材名称',
        rules: 'required',
      },
      {
        component: 'Input',
        fieldName: 'merchandiseName',
        label: '商品名称',
      },
      {
        component: 'Select',
        fieldName: 'categoryCode',
        label: '耗材分类',
        rules: 'required',
        componentProps: {
          options: deviceCategories.value,
          filterable: true,
          placeholder: '请选择耗材分类',
          clearable: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'manufacturerText',
        label: '生产厂家',
        componentProps: {
          maxlength: 200,
          showWordLimit: true,
          clearable: true,
          placeholder: '请输入生产厂家',
        },
      },
      {
        component: 'Select',
        fieldName: 'supplyId',
        label: '供应商',
        componentProps: {
          options: supplierListOptions.value,
          filterable: true,
          placeholder: '请选择供应商',
          clearable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'typeCode',
        label: '耗材种类',
        componentProps: {
          options: equipmentTypes.value,
          filterable: true,
          placeholder: '请选择耗材种类',
          clearable: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'modelNumber',
        label: '产品型号',
      },
      {
        component: 'Input',
        fieldName: 'approvalNumber',
        label: '批准文号',
      },
      {
        component: 'Input',
        fieldName: 'substanceText',
        label: '主要成分',
      },
      {
        component: 'Input',
        fieldName: 'ybNo',
        label: '医保编码',
      },
      {
        component: 'Input',
        fieldName: 'ybOrgNo',
        label: '医药机构目录编码',
      },
      {
        component: 'Select',
        fieldName: 'itemTypeCode',
        label: '财务类型',
        rules: 'required',
        componentProps: {
          options: financialTypes.value,
          filterable: true,
          placeholder: '请选择财务类型',
          clearable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'ybType',
        label: '医保类别',
        componentProps: {
          options: ybCategories.value,
          filterable: true,
          placeholder: '请选择医保类别',
          clearable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'chrgitmLv',
        label: '医保等级',
        componentProps: {
          options: ybGrades.value,
          filterable: true,
          placeholder: '请选择医保等级',
          clearable: true,
        },
      },
      {
        component: 'Checkbox',
        fieldName: 'ybMatchFlag',
        label: '医保对码',
      },

      {
        component: 'SectionTitle',
        fieldName: '__section_clinical',
        label: '临床与规格信息',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0 !pb-0 device-catalog-form-dialog__section-title',
        componentProps: {
          title: '临床与规格信息',
        },
      },
      {
        component: 'Select',
        fieldName: 'doseUnitCode',
        label: '剂量单位',
        hide: true,
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
          placeholder: '请选择剂量单位',
          clearable: true,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'doseUnitRatio',
        label: '剂量/拆零倍数',
        hide: true,
        componentProps: { min: 1, precision: 6 },
      },
      {
        component: 'Input',
        fieldName: 'totalVolume',
        label: '包装规格',
        rules: 'required',
      },

      {
        component: 'SectionTitle',
        fieldName: '__section_inventory',
        label: '库存与单位信息',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0 !pb-0 device-catalog-form-dialog__section-title',
        componentProps: {
          title: '库存与单位信息',
        },
      },
      {
        component: 'ApiTreeSelect',
        fieldName: 'orgId',
        label: '提供部门',
        componentProps: {
          data: exeOrganizations.value,
          checkStrictly: true,
          placeholder: '请选择提供部门',
          clearable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'purchaseUnitCode',
        label: '采购单位',
        rules: 'required',
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
          placeholder: '请选择采购单位',
          clearable: true,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'purchaseUnitRatio',
        label: '采购/拆零倍数',
        rules: 'required',
        componentProps: { min: 1, precision: 6 },
      },
      {
        component: 'InputNumber',
        fieldName: 'purchasePrice',
        label: '购入价（包装单位）',
        rules: 'required',
        componentProps: {
          min: 0,
          precision: 2,
          placeholder: '请输入购入价',
        },
      },
      {
        component: 'Select',
        fieldName: 'defEncounterUnitCode',
        label: '默认门诊就诊单位',
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
          placeholder: '请选择默认门诊/就诊单位',
          clearable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'stockUnitCode',
        label: '库存单位',
        rules: 'required',
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
          placeholder: '请选择库存单位',
          clearable: true,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'stockUnitRatio',
        label: '库存/拆零倍数',
        rules: 'required',
        componentProps: { min: 1, precision: 6 },
      },
      {
        component: 'InputNumber',
        fieldName: 'retailPrice',
        label: '零售价（包装单位）',
        rules: 'required',
        componentProps: {
          min: 0,
          precision: 2,
          placeholder: '请输入零售价',
        },
      },
      {
        component: 'Select',
        fieldName: 'defInhospitalUnitCode',
        label: '默认住院单位',
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
          placeholder: '请选择默认住院单位',
          clearable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'packageUnitCode',
        label: '包装单位',
        rules: 'required',
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
          placeholder: '请选择包装单位',
          clearable: true,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'packageUnitRatio',
        label: '包装/拆零倍数',
        rules: 'required',
        componentProps: { min: 1, precision: 6 },
      },
      {
        component: 'InputNumber',
        fieldName: 'maximumRetailPrice',
        label: '最高零售价（包装单位）',
        componentProps: {
          min: 0,
          precision: 2,
          placeholder: '请输入最高零售价',
        },
      },
      {
        component: 'TreeSelect',
        fieldName: 'locationId',
        label: '默认采购入库库房',
        componentProps: {
          data: locations.value,
          checkStrictly: true,
          filterable: true,
          placeholder: '请选择仓库',
          clearable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'splitUnitCode',
        label: '拆零单位',
        rules: 'required',
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
          placeholder: '请选择拆零单位',
          clearable: true,
        },
      },
      {
        component: 'Input',
        fieldName: '__inv_grid_pad_r4c2',
        hideLabel: true,
        formItemClass: 'inv-grid-pad !mb-0',
        componentProps: {
          disabled: true,
          class: 'pointer-events-none opacity-0',
          tabindex: -1,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'itemMaxQuantity',
        label: '警戒线最高值(库存单位)',
        componentProps: {
          min: 0,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'itemMinQuantity',
        label: '警戒线最低值(库存单位)',
        componentProps: {
          min: 0,
        },
      },

      {
        component: 'SectionTitle',
        fieldName: '__section_business',
        label: '业务信息',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0 !pb-0 device-catalog-form-dialog__section-title',
        componentProps: {
          title: '业务信息',
        },
      },
      {
        component: 'Input',
        fieldName: 'pyStr',
        label: '拼音码',
      },
      {
        component: 'Input',
        fieldName: 'wbStr',
        label: '五笔码',
      },
      {
        component: 'Input',
        fieldName: 'jurisdiction',
        label: '适用范围',
      },
      {
        component: 'CheckboxInlineGroup',
        fieldName: '__checkbox_inline_group',
        formItemClass: 'col-span-full !mb-0',
        componentProps: {
          fields: [
            { fieldName: 'allergenFlag', label: '过敏标记' },
            { fieldName: 'ybFlag', label: '医保标记' },
            { fieldName: 'hvcmFlag', label: '高值耗材标志' },
          ],
        },
      },
      {
        component: 'InputArea',
        fieldName: 'description',
        label: '说明',
        formItemClass: 'col-span-full',
      },
    ]),
    showDefaultActions: false,
  }),
);

const keydownDisabled = ref(false);
/** 避免同 id 重复拉详情；关弹窗后清空 */
const lastLoadedCatalogId = ref<null | string>(null);

watch(
  () => props.modelValue,
  (open) => {
    keydownDisabled.value = !open;
  },
);

watch(
  () => [props.modelValue, props.catalogId, props.initOptionsSource] as const,
  async ([open, catalogId]) => {
    if (!open || !catalogId) {
      return;
    }

    await nextTick();

    if (
      props.initOptionsSource &&
      typeof props.initOptionsSource === 'object' &&
      Object.keys(props.initOptionsSource).length > 0
    ) {
      initOptions.value = props.initOptionsSource;
    }

    if (!initOptions.value || Object.keys(initOptions.value).length === 0) {
      await loadInitOptions();
    }

    if (lastLoadedCatalogId.value === catalogId && catalogId !== 'new') {
      return;
    }

    await loadDetailData();
    lastLoadedCatalogId.value = catalogId;
  },
  { flush: 'post' },
);
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    class="device-catalog-form-dialog"
    :title="dialogTitle"
    width="1400px"
    align-center
    append-to-body
    :close-on-click-modal="false"
    :data-ep-keydown-disabled="keydownDisabled"
    @closed="handleDialogClosed"
  >
    <div v-loading="loading" class="min-h-0">
      <div class="device-catalog-form-dialog__form-wrap box-border px-4 pb-3 pt-2 sm:px-5">
        <Form />
      </div>
    </div>
    <template #footer>
      <div class="flex justify-end gap-2 px-4 sm:px-5">
        <ElButton type="primary" :loading="loading" @click="handleSubmit">保存</ElButton>
        <ElButton @click="closeDialog">取消</ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
[data-ep-keydown-disabled='true'] * {
  pointer-events: none;
}

[data-ep-keydown-disabled='true'] {
  pointer-events: auto;
  background-color: inherit;
}

.device-catalog-form-dialog {
  max-width: 98vw;
}

.inv-grid-pad :deep(.el-form-item__content) {
  display: flex;
  min-height: 40px;
  align-items: center;
}
</style>

<style>
.device-catalog-form-dialog .device-catalog-form-dialog__section-title {
  margin-top: 0;
  padding-bottom: 0 !important;
}

.device-catalog-form-dialog.el-dialog {
  display: flex;
  max-height: min(96vh, 1040px);
  height: min(93vh, 1000px);
  flex-direction: column;
  margin-top: 3vh !important;
  margin-bottom: 3vh !important;
}

.device-catalog-form-dialog .el-dialog__header {
  flex-shrink: 0;
}

.device-catalog-form-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
}

.device-catalog-form-dialog .el-dialog__body > div {
  min-height: 0;
}

.device-catalog-form-dialog .el-dialog__footer {
  flex-shrink: 0;
}
</style>
