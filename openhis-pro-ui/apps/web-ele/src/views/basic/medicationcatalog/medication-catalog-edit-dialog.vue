<script lang="ts" setup>
import type { MedicationCatalogInitResponse, MedicationCatalogItem } from '#/api';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { ElButton, ElDialog, ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import {
  createMedicationCatalogApi,
  getMedicationCatalogApi,
  medicationCatalogInitApi,
  updateMedicationCatalogApi,
} from '#/api';

interface Props {
  modelValue: boolean;
  /** 'new' 或药品定义 id */
  medicationDefId: string;
  /** 列表页已拉取的字典选项，减少重复请求 */
  initOptionsSource: MedicationCatalogInitResponse;
}

defineOptions({
  name: 'MedicationCatalogEditDialog',
});

const props = defineProps<Props>();

const emit = defineEmits<{
  success: [];
  'update:modelValue': [value: boolean];
}>();

const loading = ref(false);
const formData = ref<Partial<MedicationCatalogItem>>({});
const initOptions = ref<MedicationCatalogInitResponse>({} as MedicationCatalogInitResponse);

interface OptionItem {
  label?: string;
  value: number | string;
  info?: string;
  text?: string;
  name?: string;
  children?: OptionItem[];
}

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

// 选项数据的 computed 属性
const createOptionsComputed = (key: string) =>
  computed(() => convertOptions((initOptions.value?.[key] as OptionItem[]) || null));

const statusFlagOptions = createOptionsComputed('statusFlagOptions');
const domainFlagOptions = createOptionsComputed('domainFlagOptions');
const medicationCategoryCodeOptions = createOptionsComputed('medicationCategoryCodeOptions');
const unitCodeOptions = createOptionsComputed('unitCodeOptions');
const chrgitmLvOptions = createOptionsComputed('chrgitmLvOptions');
const doseFormCodeOptions = createOptionsComputed('doseFormCodeOptions');
const methodCodeOptions = createOptionsComputed('methodCodeOptions');
const rateCodeOptions = createOptionsComputed('rateCodeOptions');
const dosageInstructionOptions = createOptionsComputed('dosageInstructionOptions');
const finTypeCodeOptions = createOptionsComputed('finTypeCodeOptions');
const medicinePropertiesOptions = createOptionsComputed('medicinePropertiesOptions');
const medChrgitmTypeOptions = createOptionsComputed('medChrgitmTypeOptions');
const rxFlagOptions = createOptionsComputed('rxFlagOptions');
const partAttributeEnumOptions = createOptionsComputed('partAttributeEnumOptions');
const tempOrderSplitPropertyEnumOptions = createOptionsComputed(
  'tempOrderSplitPropertyEnumOptions',
);
const locationOptions = createOptionsComputed('locationOptions');

/** 弹窗显隐，与 ElDialog v-model 同步 */
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => {
    emit('update:modelValue', v);
  },
});

/** 弹窗标题：新增 / 编辑（带出药品名） */
const dialogTitle = computed(() => {
  if (props.medicationDefId === 'new') {
    return '新增药品';
  }
  const name = formData.value?.name;
  return name ? `编辑药品 — ${name}` : '编辑药品';
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
  await formApi.resetForm();
  await formApi.setFieldValue('__checkbox_inline_group', {});
}

function handleDialogClosed() {
  void resetFormState();
  lastLoadedDefId.value = null;
}

const supplierOptions = computed(() => {
  const list = (initOptions.value?.supplierListOptions ?? []) as OptionItem[];
  if (!Array.isArray(list) || list.length === 0) {
    return [];
  }
  return list
    .map((item) => {
      const label = String(item?.label ?? item?.info ?? item?.text ?? item?.name ?? '');
      const value = item?.value;
      return label && value !== undefined && value !== null
        ? { label, value: String(value) }
        : null;
    })
    .filter((v): v is { label: string; value: string } => !!v);
});

/** 业务信息内联复选组（不含临床段的皮试/注射） */
const CHECKBOX_INLINE_GROUP_FIELDS = [
  'basicFlag',
  'childrenFlag',
  'selfFlag',
  'activeFlag',
  'restrictedFlag',
  'antibioticFlag',
  'rxFlag',
] as const;

const BUSINESS_FLAG_FIELDS = [
  ...CHECKBOX_INLINE_GROUP_FIELDS,
  'skinTestFlag',
  'injectFlag',
] as const;

/** 后端 Boolean；兼容历史 0/1 */
function coerceLegacyBool(value: unknown): boolean {
  return value === true || value === 1 || value === '1';
}

/** 历史表单字段 salesUnit* 与后端 packageUnit* 对齐（耗材页曾用 sales 命名） */
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

function normalizeFormSelectValues(data: Record<string, unknown>) {
  mapLegacyPackageUnitFields(data);
  // 下拉/树下拉 value 通常是“code”。为了避免 number/string 不一致导致不回显，
  // 统一把这些字段都转成字符串参与回显匹配。
  const selectFieldNames = [
    'supplyId',
    'chrgitmLv',
    'categoryCode',
    'purchaseUnitCode',
    'splitUnitCode',
    'packageUnitCode',
    'stockUnitCode',
    'doseUnitCode',
    'doseFormCode',
    'methodCode',
    'rateCode',
    'dosageInstruction',
    'finTypeCode',
    'pharmacologyCategoryCode',
    'ybType',
    'rxFlag',
    'partAttributeEnum',
    'thoPartAttributeEnum',
    'locationId',
    'statusEnum',
    'domainEnum',
  ];

  const next = { ...data };
  selectFieldNames.forEach((field) => {
    const v = next[field];
    if (v !== undefined && v !== null && v !== '') {
      next[field] = String(v);
    }
  });
  return next;
}

async function loadDetailData() {
  const id = props.medicationDefId;
  if (!id) {
    ElMessage.warning('缺少药品ID');
    closeDialog();
    return;
  }

  // 新增模式，跳过数据加载
  if (id === 'new') {
    await resetFormState();
    return;
  }

  loading.value = true;
  try {
    const response = await getMedicationCatalogApi(id);
    const data = response.data;
    if (data) {
      const normalizedData = normalizeFormSelectValues(data as Record<string, unknown>);
      (normalizedData as Record<string, unknown>).ybMatchFlag = coerceLegacyBool(
        (normalizedData as Record<string, unknown>).ybMatchFlag,
      );
      formData.value = { ...normalizedData };
      formApi.setValues(normalizedData);

      const inlineGroupValue: Record<string, boolean> = {};
      CHECKBOX_INLINE_GROUP_FIELDS.forEach((key) => {
        const value = (normalizedData as Record<string, unknown>)[key];
        inlineGroupValue[key] = coerceLegacyBool(value);
      });
      formApi.setFieldValue('__checkbox_inline_group', inlineGroupValue);
    } else {
      ElMessage.warning('未找到药品信息');
      closeDialog();
    }
  } catch (error) {
    console.error('获取药品详情失败:', error);
    ElMessage.error('获取药品详情失败，请稍后重试');
    closeDialog();
  } finally {
    loading.value = false;
  }
}

async function loadInitOptions() {
  try {
    const response = await medicationCatalogInitApi();
    const data: MedicationCatalogInitResponse =
      response.data || ({} as MedicationCatalogInitResponse);
    if (data) {
      initOptions.value = data;
    }
  } catch (error) {
    console.error('获取初始化选项失败:', error);
    ElMessage.error('获取初始化选项失败，请稍后重试');
  }
}

function tryLoadInitOptionsFromSession() {
  const raw = sessionStorage.getItem('medicineCatalogInitOptions');
  if (!raw) {
    return false;
  }

  try {
    const parsed = JSON.parse(raw) as MedicationCatalogInitResponse;
    if (parsed && typeof parsed === 'object') {
      initOptions.value = parsed;
      return true;
    }
    return false;
  } catch (error) {
    console.error('从 sessionStorage 解析初始化选项失败:', error);
    return false;
  } finally {
    // 避免后续误用旧的选项（新增/编辑都由列表页写入该 key）
    sessionStorage.removeItem('medicineCatalogInitOptions');
  }
}

const updateFormOptions = () => {
  if (formApi?.isMounted) {
    formApi.updateSchema([
      {
        fieldName: 'categoryCode',
        componentProps: {
          data: medicationCategoryCodeOptions.value,
        },
      },
      {
        fieldName: 'purchaseUnitCode',
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
        },
      },
      {
        fieldName: 'splitUnitCode',
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
        },
      },
      {
        fieldName: 'stockUnitCode',
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
        },
      },
      {
        fieldName: 'packageUnitCode',
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
        },
      },
      {
        fieldName: 'doseUnitCode',
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
        },
      },
      {
        fieldName: 'chrgitmLv',
        componentProps: {
          options: chrgitmLvOptions.value,
        },
      },
      {
        fieldName: 'pharmacologyCategoryCode',
        componentProps: {
          options: medicinePropertiesOptions.value,
        },
      },
      {
        fieldName: 'doseFormCode',
        componentProps: {
          options: doseFormCodeOptions.value,
          filterable: true,
        },
      },
      {
        fieldName: 'methodCode',
        componentProps: {
          options: methodCodeOptions.value,
          filterable: true,
        },
      },
      {
        fieldName: 'rateCode',
        componentProps: {
          options: rateCodeOptions.value,
          filterable: true,
        },
      },
      {
        fieldName: 'dosageInstruction',
        componentProps: {
          options: dosageInstructionOptions.value,
          filterable: true,
        },
      },
      {
        fieldName: 'finTypeCode',
        componentProps: {
          options: finTypeCodeOptions.value,
          filterable: true,
        },
      },
      {
        fieldName: 'ybType',
        componentProps: {
          options: medChrgitmTypeOptions.value,
          filterable: true,
        },
      },
      {
        fieldName: 'rxFlag',
        componentProps: {
          options: rxFlagOptions.value,
        },
      },
      {
        fieldName: 'partAttributeEnum',
        componentProps: {
          options: partAttributeEnumOptions.value,
        },
      },
      {
        fieldName: 'thoPartAttributeEnum',
        componentProps: {
          options: tempOrderSplitPropertyEnumOptions.value,
        },
      },
      {
        fieldName: 'locationId',
        componentProps: {
          data: locationOptions.value,
        },
      },
      {
        fieldName: 'supplyId',
        componentProps: {
          options: supplierOptions.value,
          filterable: true,
          clearable: true,
          placeholder: '请选择供应商',
        },
      },
      {
        fieldName: 'statusEnum',
        componentProps: {
          options: statusFlagOptions.value,
        },
      },
      {
        fieldName: 'domainEnum',
        componentProps: {
          options: domainFlagOptions.value,
        },
      },
    ]);
  }
};

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  const id = props.medicationDefId;
  const isNew = id === 'new';

  loading.value = true;
  try {
    const formValues = await formApi.getValues();
    const { __checkbox_inline_group, ...restFormValues } = formValues as Record<string, unknown>;
    const mergedData = {
      ...formData.value,
      ...restFormValues,
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

    BUSINESS_FLAG_FIELDS.forEach((key) => {
      const value = mergedData[key];
      if (value === undefined || value === null || value === '') {
        mergedData[key] = false;
      }
    });

    mapLegacyPackageUnitFields(mergedData);
    delete mergedData.salesUnitCode;
    delete mergedData.salesUnitRatio;

    const submitData: Partial<MedicationCatalogItem> = {};
    Object.keys(mergedData).forEach((key) => {
      if (key.startsWith('__')) {
        return;
      }
      const value = mergedData[key];
      if (value !== null && value !== undefined && value !== '') {
        submitData[key] = value;
      }
    });

    if (isNew) {
      await createMedicationCatalogApi(submitData);
      ElMessage.success('创建成功');
      finishSuccess();
    } else {
      await updateMedicationCatalogApi(submitData);
      ElMessage.success('更新成功');
      formData.value = { ...submitData };
      finishSuccess();
    }
  } catch (error) {
    console.error(isNew ? '创建失败' : '更新失败:', error);
    ElMessage.error(isNew ? '创建失败，请稍后重试' : '更新失败，请稍后重试');
  } finally {
    loading.value = false;
  }
}

const [Form, formApi] = useVbenForm(
  reactive({
    layout: 'horizontal',
    /** 标签与默认右对齐区分：本弹窗全部左对齐 */
    commonConfig: {
      labelClass: 'justify-start text-left',
    },
    // lg 起固定四列，避免「库存与单位」4×4 在 lg:3 列时第四格换行导致前三行只有三列
    wrapperClass: 'grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4',
    schema: computed(() => [
      {
        component: 'SectionTitle',
        fieldName: '__section_basic',
        label: '基础信息',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0 !pb-0 medication-catalog-form-dialog__section-title',
        componentProps: {
          title: '基础信息',
        },
      },
      {
        component: 'Input',
        fieldName: 'busNo',
        label: '药品编号',
        hide: true,
        componentProps: {
          disabled: props.medicationDefId !== 'new',
        },
      },
      {
        component: 'Input',
        fieldName: 'name',
        label: '通用名称',
        rules: 'required',
      },
      {
        component: 'Input',
        fieldName: 'merchandiseName',
        label: '商品名称',
        rules: 'required',
      },
      {
        component: 'ApiTreeSelect',
        fieldName: 'categoryCode',
        label: '药品分类',
        rules: 'required',
        componentProps: {
          data: medicationCategoryCodeOptions.value,
        },
      },
      {
        component: 'Input',
        fieldName: 'manufacturerText',
        label: '生产厂家',
        rules: 'required',
        componentProps: {
          maxlength: 200,
          showWordLimit: true,
          clearable: true,
          placeholder: '请输入生产厂家及产地（纯文本）',
        },
      },
      {
        component: 'Select',
        fieldName: 'supplyId',
        label: '供应商',
        rules: 'required',
        componentProps: {
          options: supplierOptions.value,
          filterable: true,
          clearable: true,
          placeholder: '请选择供应商',
        },
      },
      {
        component: 'Input',
        fieldName: 'approvalNumber',
        label: '批准文号',
      },
      {
        component: 'Input',
        fieldName: 'nationalDrugCode',
        label: '贯标国家编码',
      },
      {
        component: 'Input',
        fieldName: 'ybNo',
        label: '医保编码',
      },
      {
        component: 'Select',
        fieldName: 'chrgitmLv',
        label: '医保等级',
        componentProps: {
          options: chrgitmLvOptions.value,
        },
      },
      {
        component: 'Checkbox',
        fieldName: 'ybMatchFlag',
        label: '医保对码',
      },
      {
        component: 'Select',
        fieldName: 'finTypeCode',
        label: '财务类型',
        rules: 'required',
        componentProps: {
          options: finTypeCodeOptions.value,
          filterable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'ybType',
        label: '医保类别',
        componentProps: {
          options: medChrgitmTypeOptions.value,
          filterable: true,
        },
      },

      // ========== 临床信息 ==========
      {
        component: 'SectionTitle',
        fieldName: '__section_clinical',
        label: '临床与规格信息',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0 !pb-0 medication-catalog-form-dialog__section-title',
        componentProps: {
          title: '临床与规格信息',
        },
      },
      {
        component: 'Select',
        fieldName: 'pharmacologyCategoryCode',
        label: '药品性质',
        rules: 'required',
        componentProps: {
          options: medicinePropertiesOptions.value,
        },
      },
      {
        component: 'Input',
        fieldName: 'totalVolume',
        label: '规格',
        rules: 'required',
      },
      {
        component: 'Select',
        fieldName: 'doseUnitCode',
        label: '剂量单位',
        rules: 'required',
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'doseUnitRatio',
        label: '拆零/剂量倍数',
        rules: 'required',
        componentProps: {
          min: 0,
          precision: 6,
        },
      },
      {
        component: 'Select',
        fieldName: 'doseFormCode',
        label: '剂型',
        rules: 'required',
        componentProps: {
          options: doseFormCodeOptions.value,
          filterable: true,
        },
      },
      {
        component: 'Checkbox',
        fieldName: 'skinTestFlag',
        label: '皮试判别',
      },
      {
        component: 'Checkbox',
        fieldName: 'injectFlag',
        label: '注射药品',
      },
      {
        component: 'Input',
        fieldName: 'usageLimit',
        label: '用量限定',
      },
      {
        component: 'Select',
        fieldName: 'methodCode',
        label: '默认用法',
        componentProps: {
          options: methodCodeOptions.value,
          filterable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'rateCode',
        label: '默认用药频次',
        componentProps: {
          options: rateCodeOptions.value,
          filterable: true,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'dose',
        label: '单次剂量',
        componentProps: {
          precision: 2,
          min: 0,
        },
      },
      {
        component: 'Input',
        fieldName: 'maxUnit',
        label: '单次最大剂量',
      },
      {
        component: 'Select',
        fieldName: 'dosageInstruction',
        label: '用法说明',
        componentProps: {
          options: dosageInstructionOptions.value,
          filterable: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'restrictedScope',
        label: '限制使用范围',
      },
      {
        component: 'Input',
        fieldName: 'definition',
        label: '药品说明',
      },
      {
        component: 'Input',
        fieldName: 'dddCode',
        label: 'DDD值',
      },
      {
        component: 'Input',
        fieldName: 'dddUnitCode',
        label: 'DDD单位',
      },
      {
        component: 'Input',
        fieldName: 'minRateCode',
        label: '最小用药频次',
      },
      {
        component: 'Input',
        fieldName: 'maxRateCode',
        label: '最大用药频次',
      },
      // ========== 库存信息 ==========
      {
        component: 'SectionTitle',
        fieldName: '__section_inventory',
        label: '库存与单位信息',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0 !pb-0 medication-catalog-form-dialog__section-title',
        componentProps: {
          title: '库存与单位信息',
        },
      },
      // 库存与单位：四列四行 — 列4 第1–2 行：门诊/住院拆分；第3 行：默认入库库房；第4 行第3–4 列：警戒线高/低
      {
        component: 'Select',
        fieldName: 'purchaseUnitCode',
        label: '采购单位',
        rules: 'required',
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'purchaseUnitRatio',
        label: '采购/拆零倍数',
        rules: 'required',
        componentProps: {
          min: 1,
          precision: 6,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'purchasePrice',
        label: '购入价（包装单位）',
        rules: 'required',
        componentProps: {
          precision: 2,
          min: 0,
        },
      },
      {
        component: 'Select',
        fieldName: 'partAttributeEnum',
        label: '门诊拆分属性',
        rules: 'required',
        componentProps: {
          options: partAttributeEnumOptions.value,
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
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'stockUnitRatio',
        label: '库存/拆零倍数',
        rules: 'required',
        componentProps: {
          min: 1,
          precision: 6,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'retailPrice',
        label: '零售价（包装单位）',
        rules: 'required',
        componentProps: {
          precision: 2,
          min: 0,
        },
      },
      {
        component: 'Select',
        fieldName: 'thoPartAttributeEnum',
        label: '住院临时医嘱拆分属性',
        rules: 'required',
        componentProps: {
          options: tempOrderSplitPropertyEnumOptions.value,
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
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'packageUnitRatio',
        label: '包装/拆零倍数',
        rules: 'required',
        componentProps: {
          min: 1,
          precision: 6,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'maximumRetailPrice',
        label: '最高零售价（包装单位）',
        componentProps: {
          precision: 2,
          min: 0,
        },
      },
      {
        component: 'TreeSelect',
        fieldName: 'locationId',
        label: '默认采购入库库房',
        componentProps: {
          data: locationOptions.value,
          checkStrictly: true,
          filterable: true,
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
        formItemClass: 'col-span-full !mb-0 !pb-0 medication-catalog-form-dialog__section-title',
        componentProps: {
          title: '业务信息',
        },
      },
      {
        component: 'Input',
        fieldName: 'pyStr',
        label: '药品拼音码',
      },
      {
        component: 'Input',
        fieldName: 'wbStr',
        label: '药品五笔码',
      },
      {
        component: 'Input',
        fieldName: 'merchandisePyStr',
        label: '商品拼音码',
      },
      {
        component: 'Input',
        fieldName: 'merchandiseWbStr',
        label: '商品五笔码',
      },
      {
        component: 'Input',
        fieldName: 'drug69Code',
        label: '69码',
      },
      {
        component: 'Select',
        fieldName: 'statusEnum',
        label: '药品状态',
        componentProps: {
          options: statusFlagOptions.value,
        },
      },
      {
        component: 'Select',
        fieldName: 'domainEnum',
        label: '领域',
        componentProps: {
          options: domainFlagOptions.value,
        },
      },
      {
        component: 'CheckboxInlineGroup',
        fieldName: '__checkbox_inline_group',
        formItemClass: 'col-span-full !mb-0',
        componentProps: {
          fields: [
            { fieldName: 'basicFlag', label: '基药标识' },
            { fieldName: 'childrenFlag', label: '儿童用药标志' },
            { fieldName: 'selfFlag', label: '自制药标识' },
            { fieldName: 'activeFlag', label: '是否活性' },
            { fieldName: 'restrictedFlag', label: '限制使用' },
            { fieldName: 'antibioticFlag', label: '抗生素' },
            { fieldName: 'rxFlag', label: '处方标志' },
          ],
        },
      },
    ]),
    showDefaultActions: false,
  }),
);

const keydownDisabled = ref(false);
/** 避免同 id 重复拉详情；关弹窗后清空 */
const lastLoadedDefId = ref<null | string>(null);

watch(
  () => props.modelValue,
  (open) => {
    keydownDisabled.value = !open;
  },
);

watch(
  () => [props.modelValue, props.medicationDefId, props.initOptionsSource] as const,
  async ([open, medId]) => {
    if (!open || !medId) {
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

    const needRefreshSupplierOptions =
      sessionStorage.getItem('medicineCatalogRefreshSupplierOptions') === 'true';
    if (needRefreshSupplierOptions) {
      sessionStorage.removeItem('medicineCatalogRefreshSupplierOptions');
      await loadInitOptions();
      updateFormOptions();
    }

    if (!initOptions.value || Object.keys(initOptions.value).length === 0) {
      const fromSession = tryLoadInitOptionsFromSession();
      if (!fromSession) {
        await loadInitOptions();
      }
    }

    if (lastLoadedDefId.value === medId && medId !== 'new') {
      updateFormOptions();
    } else {
      await loadDetailData();
      updateFormOptions();
      lastLoadedDefId.value = medId;
    }
  },
  { flush: 'post' },
);
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    class="medication-catalog-form-dialog"
    :title="dialogTitle"
    width="1400px"
    align-center
    append-to-body
    :close-on-click-modal="false"
    :data-ep-keydown-disabled="keydownDisabled"
    @closed="handleDialogClosed"
  >
    <div v-loading="loading" class="min-h-0">
      <div class="medication-catalog-form-dialog__form-wrap box-border px-4 pb-3 pt-2 sm:px-5">
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

.medication-catalog-form-dialog {
  max-width: 98vw;
}

/* 库存 4×4 占位格：占满栅格列高，避免第四列视觉塌陷 */
.inv-grid-pad :deep(.el-form-item__content) {
  display: flex;
  min-height: 40px;
  align-items: center;
}
</style>

<style>
/* 无 scoped：append-to-body 后命中 el-dialog 内部；双类名仅本弹窗 */
.medication-catalog-form-dialog .medication-catalog-form-dialog__section-title {
  margin-top: 0;
  padding-bottom: 0 !important;
}

/* 固定弹窗高度：头/尾固定，中间表单区域滚动 */
.medication-catalog-form-dialog.el-dialog {
  display: flex;
  max-height: min(96vh, 1040px);
  height: min(93vh, 1000px);
  flex-direction: column;
  margin-top: 3vh !important;
  margin-bottom: 3vh !important;
}

.medication-catalog-form-dialog .el-dialog__header {
  flex-shrink: 0;
}

.medication-catalog-form-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
}

.medication-catalog-form-dialog .el-dialog__body > div {
  min-height: 0;
}

.medication-catalog-form-dialog .el-dialog__footer {
  flex-shrink: 0;
}
</style>
