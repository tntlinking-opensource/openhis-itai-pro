<script lang="ts" setup>
import type { MedicationCatalogInitResponse, MedicationCatalogItem } from '#/api';

import type { NationalCatalogItem } from '#/chs/api';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { ElAlert, ElButton, ElDialog, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import {
  EXTENDED_FORM_KEYBOARD_SELECTORS,
  FORM_KEYBOARD_GRID_NAV_KEYS,
  useFormKeyboardNavigation,
} from '#/hooks/use-form-keyboard-navigation';
import {
  createMedicationCatalogApi,
  getMedicationCatalogApi,
  medicationCatalogInitApi,
  updateMedicationCatalogApi,
} from '#/api';
import { uploadMedicationCatalog3301Api, revokeMedicationCatalog3302Api } from '#/chs/api';
import MedicationCatalogPickerDialog from '#/chs/components/medication-catalog-picker-dialog.vue';
import { catalogChrgitmLvToFormValue } from '#/chs/constants/catalog-chrgitm-lv';
import {
  formatCatalogListType,
  resolveFinTypeCodeByListType,
  resolveFinTypeLabelsByListType,
  resolveMedicationCategoryCodeByListType,
} from '#/chs/constants/catalog-list-type';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { getChargeTypeListApi } from '#/api/core/common';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';
import {
  applyCatalogEditabilityFromDetail,
  CATALOG_EDITABILITY_OK,
  isCatalogStructureLocked,
  isMedicationCatalogFieldLocked,
  MEDICATION_CATALOG_LOCKABLE_FORM_FIELDS,
} from '#/views/basic/catalog/catalog-editability';
import CatalogLotPriceDialog from '#/views/basic/catalog/catalog-lot-price-dialog.vue';
import {
  resolveCatalogRecordNavState,
  type CatalogRecordNavDirection,
} from '#/views/basic/catalog/catalog-record-nav';
import {
  HERBAL_MEDICATION_CATEGORY_CODE,
  resolveMedicationCatalogPurchasePriceDisplayDp,
} from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { matchSelectOptionKeyword } from '#/utils/select-option-keyword';

interface Props {
  modelValue: boolean;
  /** 'new' 或药品定义 id */
  medicationDefId: string;
  /** 列表页已拉取的字典选项，减少重复请求 */
  initOptionsSource: MedicationCatalogInitResponse;
  /** 当前列表页记录 id */
  recordIds?: string[];
  /** 列表当前页码（从 1 起），供跨页上下翻 */
  pageNo?: number;
  /** 列表每页条数 */
  pageSize?: number;
  /** 当前筛选条件下的总条数 */
  listTotal?: number;
  /** 列表正在为上下翻换页 */
  navigating?: boolean;
}

defineOptions({
  name: 'MedicationCatalogEditDialog',
});

const props = withDefaults(defineProps<Props>(), {
  recordIds: () => [],
  pageNo: 1,
  pageSize: 10,
  listTotal: 0,
  navigating: false,
});

const emit = defineEmits<{
  navigate: [direction: CatalogRecordNavDirection];
  success: [];
  'update:modelValue': [value: boolean];
}>();
const loading = ref(false);
const upload3301Loading = ref(false);
const revoke3302Loading = ref(false);
const pickerVisible = ref(false);
const lotPriceOpen = ref(false);
const defaultContractBusNo = ref<string>();
const formData = ref<Partial<MedicationCatalogItem>>({});
const initOptions = ref<MedicationCatalogInitResponse>({} as MedicationCatalogInitResponse);
const editabilityCode = ref(CATALOG_EDITABILITY_OK);
const editabilityMessage = ref('');

const catalogStructureLocked = computed(() => isCatalogStructureLocked(editabilityCode.value));

/** 采购参考价 InputNumber 小数位：中草药 3，其余 2（随药品分类联动）；售价固定 2 */
const catalogPurchasePriceInputDp = computed(() =>
  resolveMedicationCatalogPurchasePriceDisplayDp(formData.value?.categoryCode),
);

interface OptionItem {
  busNo?: string;
  children?: OptionItem[];
  info?: string;
  label?: string;
  name?: string;
  pyStr?: string;
  text?: string;
  value: number | string;
  wbStr?: string;
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

/** 厂家等可拼音过滤选项：保留 pyStr/wbStr/busNo */
const convertSearchableOptions = (
  options: null | OptionItem[] | undefined,
): Array<{
  busNo?: string;
  label: string;
  pyStr?: string;
  value: string;
  wbStr?: string;
}> => {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  return options.map((item) => ({
    label: item.label || item.info || item.text || item.name || String(item.value),
    value: String(item.value),
    busNo: item.busNo || undefined,
    pyStr: item.pyStr || undefined,
    wbStr: item.wbStr || undefined,
  }));
};

// 选项数据的 computed 属性
const createOptionsComputed = (key: string) =>
  computed(() => convertOptions((initOptions.value?.[key] as OptionItem[]) || null));

const statusFlagOptions = createOptionsComputed('statusFlagOptions');
const domainFlagOptions = createOptionsComputed('domainFlagOptions');
const medicationCategoryCodeOptions = createOptionsComputed('medicationCategoryCodeOptions');
/** 生产厂家全量选项（含拼音五笔；filterMethod 从此裁剪） */
const manufacturerOptionsAll = computed(() =>
  convertSearchableOptions((initOptions.value?.manufacturerOptions as OptionItem[]) || null),
);
/** 生产厂家下拉当前可见项 */
const manufacturerOptions = ref(manufacturerOptionsAll.value);
watch(
  manufacturerOptionsAll,
  (list) => {
    manufacturerOptions.value = list;
  },
  { immediate: true },
);

/**
 * 厂家下拉：按名称 / 编号 / 拼音 / 五笔过滤。
 *
 * @param query 输入框关键字
 */
function filterManufacturerOptions(query: string) {
  const all = manufacturerOptionsAll.value;
  if (!String(query ?? '').trim()) {
    manufacturerOptions.value = all;
    return;
  }
  manufacturerOptions.value = all.filter((item) => matchSelectOptionKeyword(item, query));
}

/**
 * 下拉收起时恢复全量，避免下次打开仍是上次过滤结果。
 *
 * @param visible 是否展开
 */
function onManufacturerSelectVisibleChange(visible: boolean) {
  if (!visible) {
    manufacturerOptions.value = manufacturerOptionsAll.value;
  }
}

const unitCodeOptions = createOptionsComputed('unitCodeOptions');
const doseUnitCodeOptions = createOptionsComputed('doseUnitCodeOptions');
const dddUnitCodeOptions = createOptionsComputed('dddUnitCodeOptions');

type UnitSelectOption = { children?: UnitSelectOption[]; label: string; value: string };
const chrgitmLvOptions = createOptionsComputed('chrgitmLvOptions');
const mrSettlementTypeCodeOptions = createOptionsComputed('mrSettlementTypeCodeOptions');
const doseFormCodeOptions = createOptionsComputed('doseFormCodeOptions');
const methodCodeOptions = createOptionsComputed('methodCodeOptions');
const rateCodeOptions = createOptionsComputed('rateCodeOptions');
const dosageInstructionOptions = createOptionsComputed('dosageInstructionOptions');
const finTypeCodeOptions = createOptionsComputed('finTypeCodeOptions');
const medicinePropertiesOptions = createOptionsComputed('medicinePropertiesOptions');
const medChrgitmTypeOptions = createOptionsComputed('medChrgitmTypeOptions');
const partAttributeEnumOptions = createOptionsComputed('partAttributeEnumOptions');
const tempOrderSplitPropertyEnumOptions = createOptionsComputed(
  'tempOrderSplitPropertyEnumOptions',
);

/** 库存段四类单位与对应拆零倍数字段 */
const INVENTORY_UNIT_SLOTS = [
  { unitField: 'purchaseUnitCode', ratioField: 'purchaseUnitRatio' },
  { unitField: 'stockUnitCode', ratioField: 'stockUnitRatio' },
  { unitField: 'packageUnitCode', ratioField: 'packageUnitRatio' },
] as const;

type InventoryUnitField = (typeof INVENTORY_UNIT_SLOTS)[number]['unitField'] | 'splitUnitCode';

/** 拆零倍数输入：支持小数，不固定小数位、不补尾零 */
const UNIT_RATIO_INPUT_PROPS = {
  min: 1,
  step: 0.001,
  controlsPosition: 'right' as const,
};

/** 剂量拆零倍数输入 */
const DOSE_UNIT_RATIO_INPUT_PROPS = {
  min: 0,
  step: 0.001,
  controlsPosition: 'right' as const,
};

/** 与拆零单位一致时倍数恒为 1 */
function isSplitUnitCode(unitCode: string, values: Record<string, unknown>): boolean {
  const code = String(unitCode ?? '').trim();
  const splitCode = String(values.splitUnitCode ?? '').trim();
  return !!code && !!splitCode && code === splitCode;
}

/** 从任意同单位编码槽位取有效倍数（可排除指定槽位、或优先指定值） */
function pickLinkedRatioForUnitCode(
  unitCode: string,
  values: Record<string, unknown>,
  options?: { excludeRatioField?: string; preferRatio?: number },
): number | undefined {
  const code = String(unitCode ?? '').trim();
  if (!code) {
    return undefined;
  }
  if (isSplitUnitCode(code, values)) {
    return 1;
  }
  const preferRatio = options?.preferRatio;
  if (preferRatio !== undefined && Number.isFinite(preferRatio) && preferRatio > 0) {
    return preferRatio;
  }
  for (const slot of INVENTORY_UNIT_SLOTS) {
    if (options?.excludeRatioField === slot.ratioField) {
      continue;
    }
    const slotCode = String(values[slot.unitField] ?? '').trim();
    if (slotCode === code) {
      const ratio = Number(values[slot.ratioField]);
      if (Number.isFinite(ratio) && ratio > 0) {
        return ratio;
      }
    }
  }
  if (options?.excludeRatioField !== 'doseUnitRatio') {
    const doseCode = String(values.doseUnitCode ?? '').trim();
    if (doseCode === code) {
      const ratio = Number(values.doseUnitRatio);
      if (Number.isFinite(ratio) && ratio > 0) {
        return ratio;
      }
    }
  }
  if (options?.excludeRatioField) {
    const ratio = Number(values[options.excludeRatioField]);
    if (Number.isFinite(ratio) && ratio > 0) {
      return ratio;
    }
  }
  return undefined;
}

/** 将同一单位编码关联的全部倍数统一为目标值 */
async function syncRatiosForUnitCode(
  unitCode: string,
  targetRatio: number,
  values: Record<string, unknown>,
) {
  const code = String(unitCode ?? '').trim();
  if (!code || !Number.isFinite(targetRatio) || targetRatio <= 0) {
    return;
  }
  const ratio = isSplitUnitCode(code, values) ? 1 : targetRatio;
  for (const slot of INVENTORY_UNIT_SLOTS) {
    const slotCode = String(values[slot.unitField] ?? '').trim();
    if (slotCode === code) {
      await formApi.setFieldValue(slot.ratioField, ratio);
    }
  }
  if (String(values.doseUnitCode ?? '').trim() === code) {
    await formApi.setFieldValue('doseUnitRatio', ratio);
  }
}

const formAreaRef = ref<HTMLElement | null>(null);

const { handleFormKeydown: handleMedicationFormKeydown } = useFormKeyboardNavigation(formAreaRef, {
  selectors: EXTENDED_FORM_KEYBOARD_SELECTORS,
  keys: FORM_KEYBOARD_GRID_NAV_KEYS,
  selectPolicy: true,
  navigationMode: 'grid',
});

/** 弹窗显隐，与 ElDialog v-model 同步 */
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => {
    emit('update:modelValue', v);
  },
});

const isNewMedication = computed(() => props.medicationDefId === 'new');

const MEDICATION_CATALOG_PRICE_FIELD_NAMES = [
  'purchasePrice',
  'retailPrice',
  'maximumRetailPrice',
] as const;

/** 弹窗标题：新增 / 编辑（带出药品名） */
const dialogTitle = computed(() => {
  if (isNewMedication.value) {
    return '新增药品';
  }
  const name = formData.value?.name;
  return name ? `编辑药品 — ${name}` : '编辑药品';
});

/** 打开价格弹窗（仅编辑态） */
function handleViewLotPrices() {
  if (isNewMedication.value || !props.medicationDefId) {
    ElMessage.warning('请先保存药品后再查看价格');
    return;
  }
  lotPriceOpen.value = true;
}

/** 编辑态且列表有多条时展示上下翻（可跨页） */
const recordNavState = computed(() =>
  resolveCatalogRecordNavState(props.recordIds, props.medicationDefId, {
    pageNo: props.pageNo,
    pageSize: props.pageSize,
    listTotal: props.listTotal,
  }),
);

const showRecordNav = computed(() => {
  if (isNewMedication.value) {
    return false;
  }
  return recordNavState.value.listTotal > 1;
});

const canNavigatePrev = computed(() => recordNavState.value.canPrev);

const canNavigateNext = computed(() => recordNavState.value.canNext);

const recordNavBusy = computed(() => loading.value || props.navigating);

/** 切换到上一条药品（必要时由列表翻页） */
function handleNavigatePrev() {
  if (!canNavigatePrev.value || recordNavBusy.value) {
    return;
  }
  emit('navigate', -1);
}

/** 切换到下一条药品（必要时由列表翻页） */
function handleNavigateNext() {
  if (!canNavigateNext.value || recordNavBusy.value) {
    return;
  }
  emit('navigate', 1);
}

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
  editabilityCode.value = CATALOG_EDITABILITY_OK;
  editabilityMessage.value = '';
  await formApi.resetForm();
  await formApi.setFieldValue('__checkbox_inline_group', {});
}

/**
 * 按 editabilityCode 同步字段 disabled。
 * 不可直接 updateSchema({ componentProps: { disabled } })：
 * merge 会把函数型 componentProps 整段替换成仅含 disabled 的对象，单位下拉 options 被清空。
 */
function syncMedicationEditabilitySchema() {
  if (!formApi?.isMounted) {
    return;
  }
  const locked = (fieldName: string) =>
    isMedicationCatalogFieldLocked(editabilityCode.value, fieldName);
  const currentSchema = formApi.getState()?.schema ?? [];
  const patches = MEDICATION_CATALOG_LOCKABLE_FORM_FIELDS.map((fieldName) => {
    const existing = currentSchema.find((item) => item.fieldName === fieldName);
    const prevProps = existing?.componentProps;
    const disabled = locked(fieldName);
    if (typeof prevProps === 'function') {
      return {
        fieldName,
        componentProps: (...args: unknown[]) => ({
          ...(prevProps as (...a: unknown[]) => Record<string, unknown>)(...args),
          disabled,
        }),
      };
    }
    return {
      fieldName,
      componentProps: {
        ...((prevProps && typeof prevProps === 'object' ? prevProps : {}) as Record<
          string,
          unknown
        >),
        disabled,
      },
    };
  });
  formApi.updateSchema(patches);
}

function handleDialogClosed() {
  void resetFormState();
  lastLoadedDefId.value = null;
}

/** 业务信息内联复选组（不含临床段的皮试/注射） */
const CHECKBOX_INLINE_GROUP_FIELDS = [
  'basicFlag',
  'nationalMonitorFlag',
  'centralPurchaseFlag',
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

/** 后端 Boolean；兼容历史 0/1、字符串 true/false */
function coerceLegacyBool(value: unknown): boolean {
  return value === true || value === 1 || value === '1' || value === 'true' || value === 'TRUE';
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
  // 列表 DTO 历史字段 PurchasePrice（大写 P）→ 表单 purchasePrice
  if (
    data.PurchasePrice !== undefined &&
    data.PurchasePrice !== null &&
    data.PurchasePrice !== '' &&
    (data.purchasePrice === undefined || data.purchasePrice === null || data.purchasePrice === '')
  ) {
    data.purchasePrice = data.PurchasePrice;
  }
  // 兼容历史前端字段 finTypeCode
  if (
    data.finTypeCode !== undefined &&
    data.finTypeCode !== null &&
    data.finTypeCode !== '' &&
    (data.typeCode === undefined || data.typeCode === null || data.typeCode === '')
  ) {
    data.typeCode = data.finTypeCode;
  }
  // 下拉/树下拉 value 通常是“code”。为了避免 number/string 不一致导致不回显，
  // 统一把这些字段都转成字符串参与回显匹配。
  const selectFieldNames = [
    'chrgitmLv',
    'mrSettlementTypeCode',
    'categoryCode',
    'manufacturerId',
    'purchaseUnitCode',
    'splitUnitCode',
    'packageUnitCode',
    'stockUnitCode',
    'doseUnitCode',
    'doseFormCode',
    'methodCode',
    'rateCode',
    'dosageInstruction',
    'typeCode',
    'pharmacologyCategoryCode',
    'ybType',
    'partAttributeEnum',
    'thoPartAttributeEnum',
    'statusEnum',
    'domainEnum',
    'traceUnitCode',
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

/** 金额/数值字段：保证 InputNumber 能正确回显 */
function normalizeNumericFormValues(data: Record<string, unknown>) {
  const priceFields = [
    'purchasePrice',
    'retailPrice',
    'maximumRetailPrice',
    'traceUnitRatio',
  ] as const;
  priceFields.forEach((field) => {
    const v = data[field];
    if (v === undefined || v === null || v === '') {
      return;
    }
    const n = typeof v === 'number' ? v : Number(v);
    if (!Number.isNaN(n)) {
      data[field] = n;
    }
  });
}

async function applyDetailFormValues(raw: Record<string, unknown>) {
  const editability = applyCatalogEditabilityFromDetail(raw);
  editabilityCode.value = editability.code;
  editabilityMessage.value = editability.message;

  const normalizedData = normalizeFormSelectValues({ ...raw });
  normalizeNumericFormValues(normalizedData);
  // 独立 Checkbox 与业务组一样做布尔归一，避免 true/1/"true" 回显不勾选
  normalizedData.ybMatchFlag = coerceLegacyBool(normalizedData.ybMatchFlag);
  normalizedData.skinTestFlag = coerceLegacyBool(normalizedData.skinTestFlag);
  normalizedData.injectFlag = coerceLegacyBool(normalizedData.injectFlag);
  formData.value = { ...normalizedData };
  await formApi.setValues(normalizedData);

  const inlineGroupValue: Record<string, boolean> = {};
  CHECKBOX_INLINE_GROUP_FIELDS.forEach((key) => {
    inlineGroupValue[key] = coerceLegacyBool(normalizedData[key]);
  });
  formApi.setFieldValue('__checkbox_inline_group', inlineGroupValue);

  // 皮试/输液：options 就绪后仍可能丢勾选，再写一次
  await formApi.setFieldValue('skinTestFlag', coerceLegacyBool(normalizedData.skinTestFlag));
  await formApi.setFieldValue('injectFlag', coerceLegacyBool(normalizedData.injectFlag));

  // 财务类型、金额：下拉/数字组件在 options 未就绪时 setValues 可能丢失，单独再设一次
  const typeCode = normalizedData.typeCode;
  if (typeCode !== undefined && typeCode !== null && typeCode !== '') {
    await formApi.setFieldValue('typeCode', String(typeCode));
  }
  for (const field of ['purchasePrice', 'retailPrice', 'maximumRetailPrice'] as const) {
    const v = normalizedData[field];
    if (v !== undefined && v !== null && v !== '') {
      await formApi.setFieldValue(field, v);
    }
  }
  // 单位/厂家下拉依赖 options；options 就绪后再写一次，避免只回显 code
  for (const field of [
    'manufacturerId',
    'doseUnitCode',
    'purchaseUnitCode',
    'splitUnitCode',
    'packageUnitCode',
    'stockUnitCode',
    'dddUnitCode',
    'traceUnitCode',
  ] as const) {
    const v = normalizedData[field];
    if (v !== undefined && v !== null && v !== '') {
      await formApi.setFieldValue(field, String(v));
    }
  }
  syncMedicationEditabilitySchema();
}

/** 避免快速上下翻时旧请求回写覆盖新详情 */
let medicationDetailLoadSeq = 0;

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

  const loadSeq = ++medicationDetailLoadSeq;
  loading.value = true;
  try {
    // 切换记录前先清空，避免上一张残留字段混入回显（setValues 不会删旧键）
    await resetFormState();
    if (loadSeq !== medicationDetailLoadSeq) {
      return;
    }
    const response = await getMedicationCatalogApi(id);
    if (loadSeq !== medicationDetailLoadSeq) {
      return;
    }
    const data = response.data;
    if (data) {
      await applyDetailFormValues(data as Record<string, unknown>);
    } else {
      ElMessage.warning('未找到药品信息');
      closeDialog();
    }
  } catch (error) {
    if (loadSeq !== medicationDetailLoadSeq) {
      return;
    }
    console.error('获取药品详情失败:', error);
    ElMessage.error('获取药品详情失败，请稍后重试');
    closeDialog();
  } finally {
    if (loadSeq === medicationDetailLoadSeq) {
      loading.value = false;
    }
  }
}

const pickerContext = computed(() => {
  const values = (formApi?.getValues?.() ?? formData.value) as Record<string, unknown>;
  const categoryCode = String(values.categoryCode ?? formData.value?.categoryCode ?? '').trim();
  // 中草药 → 中药饮片(102)；其余默认西药/中成药(101)
  const listType = categoryCode === HERBAL_MEDICATION_CATEGORY_CODE ? '102' : '101';
  return {
    name: String(values.name ?? ''),
    approvalNumber: String(values.approvalNumber ?? ''),
    nationalDrugCode: String(values.nationalDrugCode ?? ''),
    totalVolume: String(values.totalVolume ?? ''),
    listType,
  };
});

/** 3301/3302 上传用 list_type：随药品分类切换 */
function resolveMedicationYbListType(): string {
  const categoryCode = String(formData.value?.categoryCode ?? '').trim();
  return categoryCode === HERBAL_MEDICATION_CATEGORY_CODE ? '102' : '101';
}

const canUpload3301 = computed(
  () => props.medicationDefId !== 'new' && Boolean(String(formData.value?.ybNo ?? '').trim()),
);

const canRevoke3302 = computed(
  () =>
    props.medicationDefId !== 'new' &&
    Boolean(String(formData.value?.ybNo ?? '').trim()) &&
    coerceLegacyBool(formData.value?.ybMatchFlag),
);

async function loadDefaultContractBusNo() {
  try {
    const list = await getChargeTypeListApi();
    const eligible = filterInsuranceChargeTypes(list ?? []);
    defaultContractBusNo.value = resolveDefaultInsuranceBusNo(eligible, defaultContractBusNo.value);
  } catch {
    defaultContractBusNo.value = undefined;
  }
}

function openCatalogPicker() {
  pickerVisible.value = true;
}

/** 解析目录限价上限为表单数字 */
function parseCatalogPriceUplmt(value: string | undefined | null): number | undefined {
  if (value === null || value === undefined || value === '') {
    return undefined;
  }
  const num = Number(String(value).trim());
  return Number.isFinite(num) && num >= 0 ? num : undefined;
}

type LabeledOption = { children?: LabeledOption[]; label: string; value: string };

/** 展平树形下拉（药品分类 ApiTreeSelect） */
function flattenLabeledOptions(options: LabeledOption[]): Array<{ label: string; value: string }> {
  const result: Array<{ label: string; value: string }> = [];
  const walk = (nodes: LabeledOption[]) => {
    for (const node of nodes) {
      result.push({ label: node.label, value: node.value });
      if (node.children?.length) {
        walk(node.children);
      }
    }
  };
  walk(options);
  return result;
}

/**
 * 按下拉 label 匹配选项 value：精确优先，其次互为包含；多命中取最短 label。
 */
function resolveOptionValueByLabel(
  options: LabeledOption[],
  rawLabel: string | undefined | null,
): string | undefined {
  const text = String(rawLabel ?? '').trim();
  const flat = flattenLabeledOptions(options);
  if (!text || flat.length === 0) {
    return undefined;
  }
  const normalized = text.toLowerCase();
  const exact = flat.find((item) => item.label.trim().toLowerCase() === normalized);
  if (exact) {
    return exact.value;
  }
  // 目录「注射剂」与院内「注射液」等近似文案：互为包含
  const fuzzy = flat.filter((item) => {
    const label = item.label.trim().toLowerCase();
    return label.includes(normalized) || normalized.includes(label);
  });
  if (fuzzy.length === 0) {
    return undefined;
  }
  if (fuzzy.length === 1) {
    return fuzzy[0]!.value;
  }
  fuzzy.sort((a, b) => a.label.length - b.label.length);
  return fuzzy[0]!.value;
}

/** 厂家名归一化：去空白/括号段/公司后缀，便于目录名与院内主数据对齐 */
function normalizeManufacturerName(raw: string): string {
  return String(raw ?? '')
    .trim()
    .toLowerCase()
    .replaceAll(/[（(][^）)]*[）)]/g, '')
    .replaceAll(/有限责任公司|股份有限公司|有限公司|集团公司|集团|制药厂|药业|制药|公司/g, '')
    .replaceAll(/[\s·・.。，,、]/g, '');
}

/**
 * 按目录生产企业匹配院内厂家下拉：精确 → 归一化精确 → 包含/核心名；多命中取最短 label。
 */
function resolveManufacturerOptionValue(
  options: Array<{ label: string; value: string }>,
  rawName: string | undefined | null,
): string | undefined {
  const text = String(rawName ?? '').trim();
  if (!text || options.length === 0) {
    return undefined;
  }
  const lower = text.toLowerCase();
  const exact = options.find((item) => item.label.trim().toLowerCase() === lower);
  if (exact) {
    return exact.value;
  }

  const normalizedCatalog = normalizeManufacturerName(text);
  if (normalizedCatalog) {
    const normExact = options.filter(
      (item) => normalizeManufacturerName(item.label) === normalizedCatalog,
    );
    if (normExact.length === 1) {
      return normExact[0]!.value;
    }
    if (normExact.length > 1) {
      normExact.sort((a, b) => a.label.length - b.label.length);
      return normExact[0]!.value;
    }
  }

  // 「津药和平(天津)制药有限公司」→ 核心「津药和平」再包含匹配
  const core = text.split(/[（(]/)[0]?.trim() ?? text;
  const coreNorm = normalizeManufacturerName(core);
  const candidates = options.filter((item) => {
    const label = item.label.trim().toLowerCase();
    const labelNorm = normalizeManufacturerName(item.label);
    if (label.includes(lower) || lower.includes(label)) {
      return true;
    }
    if (normalizedCatalog && labelNorm) {
      if (labelNorm.includes(normalizedCatalog) || normalizedCatalog.includes(labelNorm)) {
        return true;
      }
    }
    if (coreNorm && labelNorm && (labelNorm.includes(coreNorm) || coreNorm.includes(labelNorm))) {
      return true;
    }
    return false;
  });
  if (candidates.length === 0) {
    return undefined;
  }
  if (candidates.length === 1) {
    return candidates[0]!.value;
  }
  candidates.sort((a, b) => a.label.length - b.label.length);
  return candidates[0]!.value;
}

/** 选项中是否存在指定 value */
function hasOptionValue(options: LabeledOption[], value: string | undefined): boolean {
  if (!value) {
    return false;
  }
  return flattenLabeledOptions(options).some((item) => item.value === value);
}

/**
 * 按医保 list_type 解析财务类型：优先平台标准码，否则按「西药费/中成药费」等 label 匹配。
 */
function resolveFinTypeCodeFromListType(
  listType: string | undefined | null,
  options: LabeledOption[],
): string | undefined {
  const preferredCode = resolveFinTypeCodeByListType(listType);
  if (hasOptionValue(options, preferredCode)) {
    return preferredCode;
  }
  // 院点 fin_type 字典码可能与平台标准码不一致，改按名称兜底
  for (const label of resolveFinTypeLabelsByListType(listType)) {
    const matched = resolveOptionValueByLabel(options, label);
    if (matched) {
      return matched;
    }
  }
  return undefined;
}

/**
 * 按医保 list_type 解析药品分类：优先标准短码（西药 2 / 中草药 4），否则按「西药中成药/中药饮片/中草药」名称匹配。
 */
function resolveCategoryCodeFromListType(
  listType: string | undefined | null,
  options: LabeledOption[],
): string | undefined {
  const preferredCode = resolveMedicationCategoryCodeByListType(listType);
  if (hasOptionValue(options, preferredCode)) {
    return preferredCode;
  }
  const listTypeLabel = formatCatalogListType(String(listType ?? '').trim());
  if (listTypeLabel && listTypeLabel !== '-') {
    const byListLabel = resolveOptionValueByLabel(options, listTypeLabel);
    if (byListLabel) {
      return byListLabel;
    }
  }
  // 饮片对院内「中草药」
  if (String(listType ?? '').trim() === '102') {
    return resolveOptionValueByLabel(options, '中草药');
  }
  return undefined;
}

/** 字段未锁定且目录有值时写入（可编辑锁定态跳过） */
function canApplyCatalogField(fieldName: string, catalogHasValue: boolean): boolean {
  if (!catalogHasValue) {
    return false;
  }
  return !isMedicationCatalogFieldLocked(editabilityCode.value, fieldName);
}

/**
 * 国家医保目录选码回填：医保码/等级必带；分类/财务类型/名称/规格/厂家/剂型/单位等尽量带入（不回填拼音）。
 */
async function applyCatalogSelection(row: NationalCatalogItem) {
  const patch: Record<string, unknown> = {
    ybNo: row.hilistCode ?? '',
    ybMatchFlag: false,
  };
  const lv = catalogChrgitmLvToFormValue(row.chrgitmLv);
  if (lv !== undefined) {
    patch.chrgitmLv = lv;
  }

  const hilistName = String(row.hilistName ?? '').trim();
  if (canApplyCatalogField('name', Boolean(hilistName))) {
    patch.name = hilistName;
  }
  if (canApplyCatalogField('merchandiseName', Boolean(hilistName))) {
    patch.merchandiseName = hilistName;
  }

  // 西药中成药(101)→默认西药；中药饮片(102)→中草药
  const categoryCode = resolveCategoryCodeFromListType(
    row.listType,
    medicationCategoryCodeOptions.value as LabeledOption[],
  );
  if (canApplyCatalogField('categoryCode', Boolean(categoryCode))) {
    patch.categoryCode = categoryCode;
  }
  const typeCode = resolveFinTypeCodeFromListType(
    row.listType,
    finTypeCodeOptions.value as LabeledOption[],
  );
  if (canApplyCatalogField('typeCode', Boolean(typeCode))) {
    patch.typeCode = typeCode;
  }

  const aprvno = String(row.aprvno ?? '').trim();
  if (canApplyCatalogField('approvalNumber', Boolean(aprvno))) {
    patch.approvalNumber = aprvno;
  }

  const spec = String(row.spec ?? '').trim();
  if (canApplyCatalogField('totalVolume', Boolean(spec))) {
    patch.totalVolume = spec;
  }

  // 厂家：目录生产企业 → 院内 cus_supplier；未命中不瞎填，仅提示
  const prodentpName = String(row.prodentpName ?? '').trim();
  const manufacturerId = resolveManufacturerOptionValue(manufacturerOptionsAll.value, prodentpName);
  if (canApplyCatalogField('manufacturerId', Boolean(manufacturerId))) {
    manufacturerOptions.value = manufacturerOptionsAll.value;
    patch.manufacturerId = manufacturerId;
  } else if (prodentpName) {
    ElMessage.warning(`未匹配到生产厂家「${prodentpName}」，请手动选择或先维护厂家主数据`);
  }

  const doseFormCode = resolveOptionValueByLabel(doseFormCodeOptions.value, row.dosform);
  if (canApplyCatalogField('doseFormCode', Boolean(doseFormCode))) {
    patch.doseFormCode = doseFormCode;
  }

  const pacuntCode = resolveOptionValueByLabel(unitCodeOptions.value, row.minPacunt);
  if (canApplyCatalogField('purchaseUnitCode', Boolean(pacuntCode))) {
    patch.purchaseUnitCode = pacuntCode;
  }
  if (canApplyCatalogField('packageUnitCode', Boolean(pacuntCode))) {
    patch.packageUnitCode = pacuntCode;
  }
  if (canApplyCatalogField('stockUnitCode', Boolean(pacuntCode))) {
    patch.stockUnitCode = pacuntCode;
  }

  const prepuntInvCode = resolveOptionValueByLabel(unitCodeOptions.value, row.minPrepunt);
  if (canApplyCatalogField('splitUnitCode', Boolean(prepuntInvCode))) {
    patch.splitUnitCode = prepuntInvCode;
  }

  // 剂量单位优先制剂单位，其次计价单位
  const doseUnitCode =
    resolveOptionValueByLabel(doseUnitCodeOptions.value, row.minPrepunt) ??
    resolveOptionValueByLabel(doseUnitCodeOptions.value, row.minPrcunt) ??
    resolveOptionValueByLabel(doseUnitCodeOptions.value, row.minPacunt);
  if (canApplyCatalogField('doseUnitCode', Boolean(doseUnitCode))) {
    patch.doseUnitCode = doseUnitCode;
  }

  const pacCntRaw = String(row.minPacCnt ?? '').trim();
  const pacCnt = Number(pacCntRaw);
  if (
    canApplyCatalogField('purchaseUnitRatio', Boolean(pacCntRaw)) &&
    Number.isFinite(pacCnt) &&
    pacCnt > 0
  ) {
    patch.purchaseUnitRatio = pacCnt;
  }

  const maxPrice = parseCatalogPriceUplmt(row.priceUplmtAmt);
  if (canApplyCatalogField('maximumRetailPrice', maxPrice !== undefined)) {
    patch.maximumRetailPrice = maxPrice;
  }

  // 剂型含「注射」时勾选注射标志（未锁定才写）
  const dosformText = String(row.dosform ?? '');
  if (canApplyCatalogField('injectFlag', dosformText.includes('注射'))) {
    patch.injectFlag = true;
  }

  for (const [field, value] of Object.entries(patch)) {
    await formApi.setFieldValue(field, value);
  }

  formData.value = {
    ...formData.value,
    ...patch,
  } as Partial<MedicationCatalogItem>;

  ElMessage.success(`已选用医保编码 ${row.hilistCode}，保存药品后可上传 3301`);
}

async function handleUpload3301() {
  const medId = props.medicationDefId;
  if (medId === 'new') {
    ElMessage.warning('请先保存药品后再上传 3301');
    return;
  }
  await loadDefaultContractBusNo();
  if (!defaultContractBusNo.value) {
    ElMessage.warning('未配置医保契约');
    return;
  }
  const signedIn = await ensureInsuranceSignIn(defaultContractBusNo.value);
  if (!signedIn) return;
  upload3301Loading.value = true;
  try {
    const result = await uploadMedicationCatalog3301Api({
      contractBusNo: defaultContractBusNo.value,
      medicationDefId: medId,
      listType: resolveMedicationYbListType(),
    });
    if (result.allSuccess) {
      ElMessage.success(result.summary ?? '3301 上传成功');
      formApi.setFieldValue('ybMatchFlag', true);
      formData.value = { ...formData.value, ybMatchFlag: true };
    } else {
      ElMessage.error(result.summary ?? '3301 上传失败');
    }
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '3301 上传失败';
    ElMessage.error(msg);
  } finally {
    upload3301Loading.value = false;
  }
}

async function handleRevoke3302() {
  const medId = props.medicationDefId;
  if (medId === 'new') {
    ElMessage.warning('请先保存药品后再撤销 3302');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '将撤销该药品在医保平台的目录对照，本地「医保对码」将取消勾选。是否继续？',
      '撤销医保对照(3302)',
      { type: 'warning', confirmButtonText: '撤销', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  await loadDefaultContractBusNo();
  if (!defaultContractBusNo.value) {
    ElMessage.warning('未配置医保契约');
    return;
  }
  const signedIn = await ensureInsuranceSignIn(defaultContractBusNo.value);
  if (!signedIn) return;
  revoke3302Loading.value = true;
  try {
    const result = await revokeMedicationCatalog3302Api({
      contractBusNo: defaultContractBusNo.value,
      medicationDefId: medId,
      listType: resolveMedicationYbListType(),
    });
    if (result.allSuccess) {
      ElMessage.success(result.summary ?? '3302 撤销成功');
      formApi.setFieldValue('ybMatchFlag', false);
      formData.value = { ...formData.value, ybMatchFlag: false };
    } else {
      ElMessage.error(result.summary ?? '3302 撤销失败');
    }
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '3302 撤销失败';
    ElMessage.error(msg);
  } finally {
    revoke3302Loading.value = false;
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
          // 与 schema 同源：切换分类刷新进价小数位
          onChange: (value: unknown) => {
            const code = value === undefined || value === null ? '' : String(value);
            formData.value = { ...formData.value, categoryCode: code };
          },
        },
      },
      {
        fieldName: 'manufacturerId',
        componentProps: () => ({
          options: manufacturerOptions.value,
          filterable: true,
          clearable: true,
          placeholder: '请选择生产厂家',
          filterMethod: filterManufacturerOptions,
          onVisibleChange: onManufacturerSelectVisibleChange,
        }),
      },
      {
        fieldName: 'purchaseUnitCode',
        componentProps: () => buildInventoryUnitSelectProps('purchaseUnitCode'),
      },
      {
        fieldName: 'splitUnitCode',
        componentProps: () => buildInventoryUnitSelectProps('splitUnitCode'),
      },
      {
        fieldName: 'stockUnitCode',
        componentProps: () => buildInventoryUnitSelectProps('stockUnitCode'),
      },
      {
        fieldName: 'packageUnitCode',
        componentProps: () => buildInventoryUnitSelectProps('packageUnitCode'),
      },
      {
        fieldName: 'doseUnitCode',
        componentProps: () => ({
          options: doseUnitCodeOptions.value,
          filterable: true,
          disabled: isMedicationCatalogFieldLocked(editabilityCode.value, 'doseUnitCode'),
        }),
      },
      {
        fieldName: 'dddUnitCode',
        componentProps: () => ({
          options: dddUnitCodeOptions.value,
          placeholder: '请选择DDD单位',
          allowClear: true,
          filterable: true,
        }),
      },
      {
        fieldName: 'chrgitmLv',
        componentProps: {
          options: chrgitmLvOptions.value,
        },
      },
      {
        fieldName: 'mrSettlementTypeCode',
        componentProps: {
          options: mrSettlementTypeCodeOptions.value,
          filterable: true,
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
        fieldName: 'typeCode',
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
    syncMedicationEditabilitySchema();
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

    // 药品目录：追溯码单位与倍数必填（游离于五类业务单位）
    const traceUnitCode = String(mergedData.traceUnitCode ?? '').trim();
    const traceUnitRatio = Number(mergedData.traceUnitRatio);
    if (!traceUnitCode) {
      ElMessage.error('请选择追溯码单位');
      loading.value = false;
      return;
    }
    if (!Number.isFinite(traceUnitRatio) || traceUnitRatio <= 0) {
      ElMessage.error('请填写有效的追溯码/拆零倍数');
      loading.value = false;
      return;
    }
    mergedData.traceUnitCode = traceUnitCode;
    mergedData.traceUnitRatio = traceUnitRatio;

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
    delete mergedData.finTypeCode;
    // 药品目录不再维护供应商（详情回显里的历史 supplyId 也不提交）
    delete mergedData.supplyId;
    // manufacturerText 仅展示；持久化只提交 manufacturerId（雪花 string）
    delete mergedData.manufacturerText;
    const manufacturerId = preserveApiIdForRequest(mergedData.manufacturerId);
    if (manufacturerId) {
      mergedData.manufacturerId = manufacturerId;
    } else {
      delete mergedData.manufacturerId;
    }

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
      MEDICATION_CATALOG_PRICE_FIELD_NAMES.forEach((field) => {
        delete submitData[field];
      });
      submitData.id = String(
        submitData.id ?? submitData.medicationDefId ?? props.medicationDefId ?? '',
      );
      submitData.editabilityCode = editabilityCode.value;
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
    /** 标签与默认右对齐区分：本弹窗全部左对齐；仅保存时校验，失焦/变更不触发 */
    commonConfig: {
      labelClass: 'justify-start text-left',
      formFieldProps: {
        validateOnBlur: false,
        validateOnChange: false,
        validateOnInput: false,
        validateOnModelUpdate: false,
      },
      componentProps: {
        validateEvent: false,
      },
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
          // 切换分类后刷新进价小数位（中草药进价 3 / 其余 2；售价恒 2）
          onChange: (value: unknown) => {
            const code = value === undefined || value === null ? '' : String(value);
            formData.value = { ...formData.value, categoryCode: code };
          },
        },
      },
      {
        component: 'Select',
        fieldName: 'manufacturerId',
        label: '生产厂家',
        rules: 'required',
        componentProps: () => ({
          options: manufacturerOptions.value,
          filterable: true,
          clearable: true,
          placeholder: '请选择生产厂家',
          filterMethod: filterManufacturerOptions,
          onVisibleChange: onManufacturerSelectVisibleChange,
        }),
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
        fieldName: 'typeCode',
        label: '财务类型',
        rules: 'required',
        componentProps: {
          options: finTypeCodeOptions.value,
          filterable: true,
        },
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
        component: 'Select',
        fieldName: 'mrSettlementTypeCode',
        label: '病案结算类型',
        componentProps: {
          options: mrSettlementTypeCodeOptions.value,
          filterable: true,
          clearable: true,
          placeholder: '请选择病案结算类型',
        },
      },
      {
        component: 'Checkbox',
        fieldName: 'ybMatchFlag',
        label: '医保对码',
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
        componentProps: () => ({
          options: doseUnitCodeOptions.value,
          filterable: true,
          disabled: isMedicationCatalogFieldLocked(editabilityCode.value, 'doseUnitCode'),
        }),
      },
      {
        component: 'InputNumber',
        fieldName: 'doseUnitRatio',
        label: '拆零/剂量倍数',
        rules: 'required',
        componentProps: DOSE_UNIT_RATIO_INPUT_PROPS,
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
        label: '输液标记',
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
        label: '默认单次剂量',
        // 四列网格下强制换行，使默认/最大/最小剂量同一行
        formItemClass: 'col-start-1',
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
        component: 'Input',
        fieldName: 'minUnit',
        label: '单次最小剂量',
      },
      {
        component: 'Select',
        fieldName: 'dosageInstruction',
        label: '中药特殊煎法',
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
        component: 'Select',
        fieldName: 'dddUnitCode',
        label: 'DDD单位',
        componentProps: () => ({
          options: dddUnitCodeOptions.value,
          placeholder: '请选择DDD单位',
          allowClear: true,
          filterable: true,
        }),
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
      // 库存与单位：四列 — 列1 单位、列2 倍数聚拢；列3 价格；列4 门诊/住院拆分；
      // 第4 行第3–4 列：警戒线；第5 行：追溯码单位+倍数（游离单位，仍靠左与其它单位对齐）
      {
        component: 'Select',
        fieldName: 'purchaseUnitCode',
        label: '采购单位',
        rules: 'required',
        componentProps: () => buildInventoryUnitSelectProps('purchaseUnitCode'),
      },
      {
        component: 'InputNumber',
        fieldName: 'purchaseUnitRatio',
        label: '采购/拆零倍数',
        rules: 'required',
        componentProps: buildUnitRatioInputProps('purchaseUnitRatio'),
      },
      {
        component: 'InputNumber',
        fieldName: 'purchasePrice',
        label: '采购参考价（包装单位）',
        rules: isNewMedication.value ? 'required' : undefined,
        componentProps: {
          // 中草药进价允许录入三位小数；西药/中成药仍两位
          precision: catalogPurchasePriceInputDp.value,
          min: 0,
          disabled: !isNewMedication.value,
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
        componentProps: () => buildInventoryUnitSelectProps('stockUnitCode'),
      },
      {
        component: 'InputNumber',
        fieldName: 'stockUnitRatio',
        label: '库存/拆零倍数',
        rules: 'required',
        componentProps: buildUnitRatioInputProps('stockUnitRatio'),
      },
      {
        component: 'InputNumber',
        fieldName: 'retailPrice',
        label: '销售参考价（包装单位）',
        rules: isNewMedication.value ? 'required' : undefined,
        componentProps: {
          // 售价固定两位（与收费/批号售价展示一致；中药也不例外）
          precision: 2,
          min: 0,
          disabled: !isNewMedication.value,
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
        componentProps: () => buildInventoryUnitSelectProps('packageUnitCode'),
      },
      {
        component: 'InputNumber',
        fieldName: 'packageUnitRatio',
        label: '包装/拆零倍数',
        rules: 'required',
        componentProps: buildUnitRatioInputProps('packageUnitRatio'),
      },
      {
        component: 'InputNumber',
        fieldName: 'maximumRetailPrice',
        label: '最高零售价（包装单位）',
        componentProps: {
          precision: 2,
          min: 0,
          disabled: !isNewMedication.value,
        },
      },
      {
        component: 'Input',
        fieldName: '__inv_grid_pad_r3c4',
        hideLabel: true,
        formItemClass: 'inv-grid-pad !mb-0',
        componentProps: {
          disabled: true,
          class: 'pointer-events-none opacity-0',
          tabindex: -1,
        },
      },
      {
        component: 'Select',
        fieldName: 'splitUnitCode',
        label: '拆零单位',
        rules: 'required',
        componentProps: () => buildInventoryUnitSelectProps('splitUnitCode'),
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
        component: 'Select',
        fieldName: 'traceUnitCode',
        label: '追溯码单位',
        rules: 'required',
        componentProps: () => ({
          options: unitCodeOptions.value as UnitSelectOption[],
          filterable: true,
          placeholder: '请选择追溯码单位',
          clearable: true,
        }),
      },
      {
        component: 'InputNumber',
        fieldName: 'traceUnitRatio',
        label: '追溯码/拆零倍数',
        rules: 'required',
        componentProps: () => ({
          ...UNIT_RATIO_INPUT_PROPS,
          placeholder: '1 追溯码单位 = 多少拆零',
        }),
      },
      {
        component: 'Input',
        fieldName: '__inv_grid_pad_r5c3',
        hideLabel: true,
        formItemClass: 'inv-grid-pad !mb-0',
        componentProps: {
          disabled: true,
          class: 'pointer-events-none opacity-0',
          tabindex: -1,
        },
      },
      {
        component: 'Input',
        fieldName: '__inv_grid_pad_r5c4',
        hideLabel: true,
        formItemClass: 'inv-grid-pad !mb-0',
        componentProps: {
          disabled: true,
          class: 'pointer-events-none opacity-0',
          tabindex: -1,
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
            { fieldName: 'nationalMonitorFlag', label: '国家重点监控药品' },
            { fieldName: 'centralPurchaseFlag', label: '集采药品' },
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

/** 单位下拉变更后，联动同单位编码槽位的拆零倍数 */
async function handleInventoryUnitChange(
  unitField: InventoryUnitField,
  unitCode: unknown,
  valuesOverride?: Record<string, unknown>,
) {
  const values = valuesOverride ?? ((await formApi.getValues()) as Record<string, unknown>);
  const code = String(unitCode ?? '').trim();
  const nextValues = { ...values, [unitField]: code };

  if (unitField === 'splitUnitCode') {
    if (!code) {
      return;
    }
    await syncRatiosForUnitCode(code, 1, nextValues);
    return;
  }

  const slot = INVENTORY_UNIT_SLOTS.find((item) => item.unitField === unitField);
  if (!slot || !code) {
    return;
  }
  const ratio = pickLinkedRatioForUnitCode(code, nextValues, {
    excludeRatioField: slot.ratioField,
  });
  if (ratio === undefined) {
    return;
  }
  await syncRatiosForUnitCode(code, ratio, nextValues);
}

type InventoryRatioField = (typeof INVENTORY_UNIT_SLOTS)[number]['ratioField'];

/** 拆零倍数变更后，联动同单位编码的全部倍数（不以采购为优先） */
async function handleInventoryRatioChange(ratioField: InventoryRatioField, ratio: unknown) {
  const numRatio = Number(ratio);
  if (!Number.isFinite(numRatio) || numRatio <= 0) {
    return;
  }
  const values = (await formApi.getValues()) as Record<string, unknown>;
  const nextValues = { ...values, [ratioField]: numRatio };

  const changedSlot = INVENTORY_UNIT_SLOTS.find((item) => item.ratioField === ratioField);
  if (!changedSlot) {
    return;
  }
  const unitCode = String(nextValues[changedSlot.unitField] ?? '').trim();
  if (!unitCode) {
    return;
  }
  await syncRatiosForUnitCode(unitCode, numRatio, nextValues);
}

/** 构造带 onChange 的拆零倍数输入 props */
function buildUnitRatioInputProps(ratioField: InventoryRatioField) {
  return {
    ...UNIT_RATIO_INPUT_PROPS,
    onChange: (value: unknown) => {
      void handleInventoryRatioChange(ratioField, value);
    },
  };
}

/** 构造带 onChange 的单位下拉 props（采购/拆零/包装/库存统一 unit_code） */
function buildInventoryUnitSelectProps(unitField: InventoryUnitField) {
  return {
    options: unitCodeOptions.value as UnitSelectOption[],
    filterable: true,
    disabled: isMedicationCatalogFieldLocked(editabilityCode.value, unitField),
    onChange: (value: unknown) => {
      void handleInventoryUnitChange(unitField, value);
    },
  };
}

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
  () =>
    [
      props.modelValue,
      doseUnitCodeOptions.value.length,
      unitCodeOptions.value.length,
      dddUnitCodeOptions.value.length,
    ] as const,
  ([open]) => {
    if (open && formApi?.isMounted) {
      updateFormOptions();
    }
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

    if (!initOptions.value || Object.keys(initOptions.value).length === 0) {
      const fromSession = tryLoadInitOptionsFromSession();
      if (!fromSession) {
        await loadInitOptions();
      }
    }

    if (lastLoadedDefId.value === medId && medId !== 'new') {
      updateFormOptions();
      if (formData.value && Object.keys(formData.value).length > 0) {
        await applyDetailFormValues(formData.value as Record<string, unknown>);
      }
    } else {
      await loadDetailData();
      // 快速翻页时以当前 props 为准，丢弃已过期的回写
      if (props.medicationDefId !== medId) {
        return;
      }
      updateFormOptions();
      if (formData.value && Object.keys(formData.value).length > 0) {
        await applyDetailFormValues(formData.value as Record<string, unknown>);
      }
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
      <ElAlert
        v-if="catalogStructureLocked"
        class="mx-4 mb-3 sm:mx-5"
        type="warning"
        :closable="false"
        show-icon
        :title="
          editabilityMessage || '该药品已产生业务引用，名称/分类/单位/拆零倍数/价格等字段不可修改'
        "
      />
      <div
        ref="formAreaRef"
        class="medication-catalog-form-dialog__form-wrap box-border px-4 pb-3 pt-2 sm:px-5"
        @keydown.capture="handleMedicationFormKeydown"
      >
        <Form />
      </div>
    </div>
    <template #footer>
      <div class="flex flex-wrap items-center justify-between gap-2 px-4 sm:px-5">
        <div class="flex flex-wrap gap-2">
          <ElButton type="success" plain @click="openCatalogPicker">从国家目录选择</ElButton>
          <ElButton v-if="!isNewMedication" type="primary" plain @click="handleViewLotPrices">
            价格
          </ElButton>
          <ElButton
            v-if="canUpload3301"
            type="warning"
            plain
            :loading="upload3301Loading"
            @click="handleUpload3301"
          >
            上传医保对照(3301)
          </ElButton>
          <ElButton
            v-if="canRevoke3302"
            type="danger"
            plain
            :loading="revoke3302Loading"
            @click="handleRevoke3302"
          >
            撤销医保对照(3302)
          </ElButton>
        </div>
        <div class="flex flex-wrap gap-2">
          <template v-if="showRecordNav">
            <ElButton :disabled="!canNavigatePrev || recordNavBusy" @click="handleNavigatePrev">
              上一个
            </ElButton>
            <ElButton :disabled="!canNavigateNext || recordNavBusy" @click="handleNavigateNext">
              下一个
            </ElButton>
          </template>
          <ElButton type="primary" :loading="loading" @click="handleSubmit">保存</ElButton>
          <ElButton @click="closeDialog">取消</ElButton>
        </div>
      </div>
    </template>
    <MedicationCatalogPickerDialog
      v-model="pickerVisible"
      :context="pickerContext"
      @confirm="applyCatalogSelection"
    />
    <CatalogLotPriceDialog
      v-model="lotPriceOpen"
      catalog-type="medication"
      :item-id="medicationDefId"
      :item-name="String(formData?.name ?? '')"
      :category-code="String(formData?.categoryCode ?? '')"
    />
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
