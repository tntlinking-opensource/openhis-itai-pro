<script lang="ts" setup>
import type {
  CreateDeviceCatalogParams,
  DeviceCatalogInitResponse,
  DeviceCatalogItem,
  UpdateDeviceCatalogParams,
} from '#/api';

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
  createDeviceCatalogApi,
  getDeviceCatalogApi,
  initDeviceCatalogApi,
  updateDeviceCatalogApi,
} from '#/api';
import { getChargeTypeListApi } from '#/api/core/common';
import { revokeDeviceCatalog3302Api, uploadDeviceCatalog3301Api } from '#/chs/api';
import MedicationCatalogPickerDialog from '#/chs/components/medication-catalog-picker-dialog.vue';
import { catalogChrgitmLvToFormValue } from '#/chs/constants/catalog-chrgitm-lv';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';
import {
  applyCatalogEditabilityFromDetail,
  buildCatalogEditabilitySchemaPatches,
  CATALOG_EDITABILITY_OK,
  isCatalogStructureLocked,
  isDeviceCatalogFieldLocked,
} from '#/views/basic/catalog/catalog-editability';
import CatalogLotPriceDialog from '#/views/basic/catalog/catalog-lot-price-dialog.vue';
import {
  resolveCatalogRecordNavState,
  type CatalogRecordNavDirection,
} from '#/views/basic/catalog/catalog-record-nav';
import {
  parseCatalogPackCount,
  parseCatalogPriceUplmt,
  resolveManufacturerOptionValue,
  resolveOptionValueByLabel,
  resolveOptionValueByLabels,
  type CatalogLabeledOption,
} from '#/chs/utils/apply-national-catalog-fields';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { matchSelectOptionKeyword } from '#/utils/select-option-keyword';

interface Props {
  modelValue: boolean;
  /** 'new' 或耗材定义 id */
  catalogId: string;
  /** 列表页已拉取的字典选项，减少重复请求 */
  initOptionsSource: DeviceCatalogInitResponse;
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
  name: 'DeviceCatalogEditDialog',
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

type OptionItem = {
  busNo?: string;
  children?: OptionItem[];
  info?: string;
  label?: string;
  name?: string;
  pyStr?: string;
  text?: string;
  value: number | string;
  wbStr?: string;
};

const loading = ref(false);
const upload3301Loading = ref(false);
const revoke3302Loading = ref(false);
const pickerVisible = ref(false);
const lotPriceOpen = ref(false);
const defaultContractBusNo = ref<string>();
const formAreaRef = ref<HTMLElement | null>(null);
const formData = ref<Partial<DeviceCatalogItem>>({});

const { handleFormKeydown: handleDeviceFormKeydown } = useFormKeyboardNavigation(formAreaRef, {
  selectors: EXTENDED_FORM_KEYBOARD_SELECTORS,
  keys: FORM_KEYBOARD_GRID_NAV_KEYS,
  selectPolicy: true,
  navigationMode: 'grid',
});
const initOptions = ref<DeviceCatalogInitResponse>({} as DeviceCatalogInitResponse);
const originalData = ref<Partial<DeviceCatalogItem>>({});
const editabilityCode = ref(CATALOG_EDITABILITY_OK);
const editabilityMessage = ref('');

const catalogStructureLocked = computed(() => isCatalogStructureLocked(editabilityCode.value));

const BOOLEAN_FLAG_FIELDS = [
  'allergenFlag',
  'ybFlag',
  'ybMatchFlag',
  'hvcmFlag',
  'chargeableFlag',
] as const;

/** 业务信息内联复选组 */
const CHECKBOX_INLINE_GROUP_FIELDS = [
  'allergenFlag',
  'ybFlag',
  'hvcmFlag',
  'chargeableFlag',
] as const;

/** 已从表单移除、提交时须剔除的字段 */
const REMOVED_FORM_FIELDS = [
  'modelNumber',
  'substanceText',
  'orgId',
  'defEncounterUnitCode',
  'defInhospitalUnitCode',
  'locationId',
  'jurisdiction',
] as const;

/** 拆零倍数输入：支持小数，不固定小数位、不补尾零 */
const UNIT_RATIO_INPUT_PROPS = {
  min: 1,
  step: 0.001,
  controlsPosition: 'right' as const,
};

/** 库存 4×4 栅格占位格（每行须满 4 格，否则下一行字段会顶到上一行第 4 列） */
function buildInvGridPadField(fieldName: string) {
  return {
    component: 'Input' as const,
    fieldName,
    hideLabel: true,
    formItemClass: 'inv-grid-pad !mb-0',
    componentProps: {
      disabled: true,
      class: 'pointer-events-none opacity-0',
      tabindex: -1,
    },
  };
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
function convertSearchableOptions(options: null | OptionItem[] | undefined): Array<{
  busNo?: string;
  label: string;
  pyStr?: string;
  value: string;
  wbStr?: string;
}> {
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
}

const createOptionsComputed = (key: string) =>
  computed(() => convertOptions((initOptions.value?.[key] as OptionItem[]) || null));

const deviceCategories = createOptionsComputed('deviceCategories');
const equipmentTypes = createOptionsComputed('equipmentTypes');
const supplierListOptions = createOptionsComputed('supplierListOptions');
/** 生产厂家全量：优先 manufacturerOptions；无则回落供应商列表（无拼音时仅按名称） */
const manufacturerOptionsAll = computed(() => {
  const dedicated = convertSearchableOptions(
    (initOptions.value?.manufacturerOptions as OptionItem[] | undefined) ?? null,
  );
  if (dedicated.length > 0) {
    return dedicated;
  }
  return convertSearchableOptions(
    (initOptions.value?.supplierListOptions as OptionItem[] | undefined) ?? null,
  );
});
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

/** 五类计量单位统一候选（unit_code），与药品目录一致 */
const unitCodeOptions = computed(() =>
  convertOptions((initOptions.value?.unitCodeOptions as OptionItem[]) || null),
);
const financialTypes = createOptionsComputed('financialTypes');
/** 医保类别（init 字段 ybCategories） */
const medChrgitmTypeOptions = computed(() =>
  convertOptions((initOptions.value?.ybCategories as OptionItem[] | undefined) ?? null),
);
/** 医保等级：优先 chrgitmLvOptions，兼容旧 init 字段 ybGrades */
const chrgitmLvOptions = computed(() =>
  convertOptions(
    (initOptions.value?.chrgitmLvOptions as OptionItem[] | undefined) ??
      (initOptions.value?.ybGrades as OptionItem[] | undefined) ??
      null,
  ),
);
const mrSettlementTypeCodeOptions = createOptionsComputed('mrSettlementTypeCodeOptions');

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

const DEVICE_CATALOG_PRICE_FIELD_NAMES = [
  'purchasePrice',
  'retailPrice',
  'maximumRetailPrice',
] as const;

const pickerContext = computed(() => ({
  name: String(formData.value?.name ?? ''),
  approvalNumber: String(formData.value?.approvalNumber ?? ''),
  totalVolume: String(formData.value?.totalVolume ?? ''),
  listType: '301',
}));

const canUpload3301 = computed(
  () => isEditMode.value && Boolean(String(formData.value?.ybNo ?? '').trim()),
);

const canRevoke3302 = computed(
  () =>
    isEditMode.value &&
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

/** 字段未锁定且目录有值时写入 */
function canApplyDeviceCatalogField(fieldName: string, catalogHasValue: boolean): boolean {
  if (!catalogHasValue) {
    return false;
  }
  return !isDeviceCatalogFieldLocked(editabilityCode.value, fieldName);
}

/**
 * 国家医保目录选码回填：医保码/等级必带；名称/规格/厂家/单位/限价/财务类型等尽量带入（不回填拼音）。
 */
async function applyCatalogSelection(row: NationalCatalogItem) {
  const patch: Record<string, unknown> = {
    ybNo: row.hilistCode ?? '',
    ybMatchFlag: false,
    ybFlag: true,
  };
  const lv = catalogChrgitmLvToFormValue(row.chrgitmLv);
  if (lv !== undefined) {
    patch.chrgitmLv = lv;
  }

  const hilistName = String(row.hilistName ?? '').trim();
  if (canApplyDeviceCatalogField('name', Boolean(hilistName))) {
    patch.name = hilistName;
  }
  if (canApplyDeviceCatalogField('merchandiseName', Boolean(hilistName))) {
    patch.merchandiseName = hilistName;
  }

  const aprvno = String(row.aprvno ?? '').trim();
  if (canApplyDeviceCatalogField('approvalNumber', Boolean(aprvno))) {
    patch.approvalNumber = aprvno;
  }

  const spec = String(row.spec ?? '').trim();
  if (canApplyDeviceCatalogField('totalVolume', Boolean(spec))) {
    patch.totalVolume = spec;
  }

  // 分类/财务：按「医用材料/耗材」等名称尽量命中院内下拉
  const categoryCode = resolveOptionValueByLabels(
    deviceCategories.value as CatalogLabeledOption[],
    ['医用材料', '医用耗材', '耗材', '卫生材料'],
  );
  if (canApplyDeviceCatalogField('categoryCode', Boolean(categoryCode))) {
    patch.categoryCode = categoryCode;
  }
  const itemTypeCode = resolveOptionValueByLabels(financialTypes.value as CatalogLabeledOption[], [
    '医用耗材',
    '卫生材料费',
    '卫生材料',
    '耗材',
  ]);
  if (canApplyDeviceCatalogField('itemTypeCode', Boolean(itemTypeCode))) {
    patch.itemTypeCode = itemTypeCode;
  }

  const prodentpName = String(row.prodentpName ?? '').trim();
  const manufacturerId = resolveManufacturerOptionValue(manufacturerOptionsAll.value, prodentpName);
  if (canApplyDeviceCatalogField('manufacturerId', Boolean(manufacturerId))) {
    manufacturerOptions.value = manufacturerOptionsAll.value;
    patch.manufacturerId = manufacturerId;
  } else if (prodentpName) {
    ElMessage.warning(`未匹配到生产厂家「${prodentpName}」，请手动选择或先维护厂家主数据`);
  }

  const unitOptions = unitCodeOptions.value as CatalogLabeledOption[];
  const pacuntCode = resolveOptionValueByLabel(unitOptions, row.minPacunt);
  if (canApplyDeviceCatalogField('purchaseUnitCode', Boolean(pacuntCode))) {
    patch.purchaseUnitCode = pacuntCode;
  }
  if (canApplyDeviceCatalogField('packageUnitCode', Boolean(pacuntCode))) {
    patch.packageUnitCode = pacuntCode;
  }
  if (canApplyDeviceCatalogField('stockUnitCode', Boolean(pacuntCode))) {
    patch.stockUnitCode = pacuntCode;
  }

  const prepuntCode =
    resolveOptionValueByLabel(unitOptions, row.minPrepunt) ??
    resolveOptionValueByLabel(unitOptions, row.minPrcunt);
  if (canApplyDeviceCatalogField('splitUnitCode', Boolean(prepuntCode))) {
    patch.splitUnitCode = prepuntCode;
  }
  if (canApplyDeviceCatalogField('doseUnitCode', Boolean(prepuntCode))) {
    patch.doseUnitCode = prepuntCode;
  }

  const pacCnt = parseCatalogPackCount(row.minPacCnt);
  if (canApplyDeviceCatalogField('purchaseUnitRatio', pacCnt !== undefined)) {
    patch.purchaseUnitRatio = pacCnt;
  }

  const maxPrice = parseCatalogPriceUplmt(row.priceUplmtAmt);
  if (canApplyDeviceCatalogField('maximumRetailPrice', maxPrice !== undefined)) {
    patch.maximumRetailPrice = maxPrice;
  }

  const dosformText = String(row.dosform ?? '').trim();
  if (dosformText && canApplyDeviceCatalogField('description', true)) {
    const currentDesc = String(formData.value?.description ?? '').trim();
    if (!currentDesc) {
      patch.description = `剂型/型号：${dosformText}`;
    }
  }

  for (const [field, value] of Object.entries(patch)) {
    await formApi.setFieldValue(field, value);
  }
  // 业务信息内联复选组需同步 ybFlag
  if (patch.ybFlag === true) {
    const group = ((await formApi.getValues()) as Record<string, unknown>)
      .__checkbox_inline_group as Record<string, unknown> | undefined;
    await formApi.setFieldValue('__checkbox_inline_group', {
      ...(group && typeof group === 'object' ? group : {}),
      ybFlag: true,
    });
  }

  formData.value = {
    ...formData.value,
    ...patch,
  } as Partial<DeviceCatalogItem>;

  ElMessage.success(`已选用医保编码 ${row.hilistCode}，保存耗材后可上传 3301`);
}

async function handleUpload3301() {
  const id = props.catalogId;
  if (id === 'new') {
    ElMessage.warning('请先保存耗材后再上传 3301');
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
    const result = await uploadDeviceCatalog3301Api({
      contractBusNo: defaultContractBusNo.value,
      deviceDefId: id,
      listType: '301',
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
  const id = props.catalogId;
  if (id === 'new') {
    ElMessage.warning('请先保存耗材后再撤销 3302');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '将撤销该耗材在医保平台的目录对照，本地「医保对码」将取消勾选。是否继续？',
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
    const result = await revokeDeviceCatalog3302Api({
      contractBusNo: defaultContractBusNo.value,
      deviceDefId: id,
      listType: '301',
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

/** 弹窗标题：新增 / 编辑（带出耗材名） */
const dialogTitle = computed(() => {
  if (!isEditMode.value) {
    return '新增耗材';
  }
  const name = formData.value?.name;
  return name ? `编辑耗材 — ${name}` : '编辑耗材';
});

/** 打开价格弹窗（仅编辑态） */
function handleViewLotPrices() {
  if (!isEditMode.value || !props.catalogId) {
    ElMessage.warning('请先保存耗材后再查看价格');
    return;
  }
  lotPriceOpen.value = true;
}

/** 编辑态且列表有多条时展示上下翻（可跨页） */
const recordNavState = computed(() =>
  resolveCatalogRecordNavState(props.recordIds, props.catalogId, {
    pageNo: props.pageNo,
    pageSize: props.pageSize,
    listTotal: props.listTotal,
  }),
);

const showRecordNav = computed(() => {
  if (!isEditMode.value) {
    return false;
  }
  return recordNavState.value.listTotal > 1;
});

const canNavigatePrev = computed(() => recordNavState.value.canPrev);

const canNavigateNext = computed(() => recordNavState.value.canNext);

const recordNavBusy = computed(() => loading.value || props.navigating);

/** 切换到上一条耗材（必要时由列表翻页） */
function handleNavigatePrev() {
  if (!canNavigatePrev.value || recordNavBusy.value) {
    return;
  }
  emit('navigate', -1);
}

/** 切换到下一条耗材（必要时由列表翻页） */
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
  originalData.value = {};
  editabilityCode.value = CATALOG_EDITABILITY_OK;
  editabilityMessage.value = '';
  await formApi.resetForm();
  // 新建默认：勾选「收费耗材」↔ chargeableFlag=true
  await formApi.setFieldValue('__checkbox_inline_group', { chargeableFlag: true });
}

/** 按 editabilityCode 同步表单字段 disabled */
function syncDeviceEditabilitySchema() {
  if (!formApi?.isMounted) {
    return;
  }
  formApi.updateSchema(buildCatalogEditabilitySchemaPatches('device', editabilityCode.value));
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

/** 销售单位历史字段名 salesUnit* 与后端 packageUnit* 对齐 */
function normalizeUnitFieldNames(values: Record<string, unknown>): Record<string, unknown> {
  const normalized = { ...values };
  mapLegacyPackageUnitFields(normalized);
  return normalized;
}

/** 下拉 value 统一为字符串，避免 number/string 不一致导致不回显 */
function normalizeFormSelectValues(data: Record<string, unknown>) {
  const selectFieldNames = [
    'categoryCode',
    'typeCode',
    'manufacturerId',
    'supplyId',
    'doseUnitCode',
    'purchaseUnitCode',
    'stockUnitCode',
    'packageUnitCode',
    'splitUnitCode',
    'itemTypeCode',
    'ybType',
    'chrgitmLv',
    'mrSettlementTypeCode',
    'traceUnitCode',
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

/** 避免快速上下翻时旧请求回写覆盖新详情 */
let deviceDetailLoadSeq = 0;

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

  const loadSeq = ++deviceDetailLoadSeq;
  loading.value = true;
  try {
    // 切换记录前先清空，避免上一张残留字段混入回显（setValues 不会删旧键）
    await resetFormState();
    if (loadSeq !== deviceDetailLoadSeq) {
      return;
    }
    const response = await getDeviceCatalogApi(id);
    if (loadSeq !== deviceDetailLoadSeq) {
      return;
    }
    const detail = response.data;
    if (!detail) {
      ElMessage.warning('未找到耗材信息');
      closeDialog();
      return;
    }

    originalData.value = { ...detail };
    const editability = applyCatalogEditabilityFromDetail(detail);
    editabilityCode.value = editability.code;
    editabilityMessage.value = editability.message;
    const normalizedData = normalizeFormSelectValues(detail as Record<string, unknown>);
    normalizeBooleanFields(normalizedData);
    formData.value = { ...normalizedData };
    await formApi.setValues(normalizedData);

    const inlineGroupValue: Record<string, boolean> = {};
    CHECKBOX_INLINE_GROUP_FIELDS.forEach((key) => {
      // 勾选与库字段同向：收费耗材 ↔ chargeableFlag=true
      inlineGroupValue[key] = coerceLegacyBool(normalizedData[key]);
    });
    await formApi.setFieldValue('__checkbox_inline_group', inlineGroupValue);
    // 单位/厂家/供应商 options 就绪后再写一次，避免 Select 只回显 code
    for (const field of [
      'manufacturerId',
      'supplyId',
      'doseUnitCode',
      'purchaseUnitCode',
      'splitUnitCode',
      'packageUnitCode',
      'stockUnitCode',
    ] as const) {
      const v = normalizedData[field];
      if (v !== undefined && v !== null && v !== '') {
        await formApi.setFieldValue(field, String(v));
      }
    }
    syncDeviceEditabilitySchema();
  } catch (error) {
    if (loadSeq !== deviceDetailLoadSeq) {
      return;
    }
    console.error('获取耗材详情失败:', error);
    ElMessage.error('获取耗材详情失败，请稍后重试');
    closeDialog();
  } finally {
    if (loadSeq === deviceDetailLoadSeq) {
      loading.value = false;
    }
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
      // false 也会进入（false !== ''）：取消勾选 → 写入 false；与 chargeableFlag 同向
      if (value !== undefined && value !== null && value !== '') {
        mergedData[key] = value === true || value === 1 || value === '1' || value === 'true';
      }
    });
  }

  normalizeBooleanFields(mergedData);
  mapLegacyPackageUnitFields(mergedData);
  delete mergedData.salesUnitCode;
  delete mergedData.salesUnitRatio;
  // manufacturerText 仅展示；写请求只提交 manufacturerId（雪花 string）
  delete mergedData.manufacturerText;
  const manufacturerId = preserveApiIdForRequest(mergedData.manufacturerId);
  if (manufacturerId) {
    mergedData.manufacturerId = manufacturerId;
  } else {
    delete mergedData.manufacturerId;
  }
  const supplyId = preserveApiIdForRequest(mergedData.supplyId);
  if (supplyId) {
    mergedData.supplyId = supplyId;
  } else {
    delete mergedData.supplyId;
  }
  REMOVED_FORM_FIELDS.forEach((field) => {
    delete mergedData[field];
  });
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
  DEVICE_CATALOG_PRICE_FIELD_NAMES.forEach((field) => {
    delete updateData[field];
  });
  updateData.packageUnitCode =
    updateData.packageUnitCode ?? updateData.salesUnitCode ?? updateData.packageUnitCode;
  updateData.packageUnitRatio =
    updateData.packageUnitRatio ?? updateData.salesUnitRatio ?? updateData.packageUnitRatio;
  delete updateData.salesUnitCode;
  delete updateData.salesUnitRatio;
  updateData.editabilityCode = editabilityCode.value;
  return updateData as UpdateDeviceCatalogParams;
}

function buildCreatePayload(submitData: Partial<DeviceCatalogItem>): CreateDeviceCatalogParams {
  const { id: _id, ...restData } = submitData;
  const restDataRecord = restData as Record<string, unknown>;
  const purchaseUnitCode = String(restDataRecord.purchaseUnitCode ?? '');
  const splitUnitCode = String(restDataRecord.splitUnitCode ?? '');
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

    // 耗材追溯：单位与倍数均可空，但任一侧有值则须成对且倍数 > 0（对齐后端 DeviceCatalogAppService）
    const traceUnitCode = String(submitData.traceUnitCode ?? '').trim();
    const traceRatioRaw = submitData.traceUnitRatio;
    const hasTraceUnit = traceUnitCode.length > 0;
    const hasTraceRatio =
      traceRatioRaw !== undefined && traceRatioRaw !== null && String(traceRatioRaw).trim() !== '';
    if (hasTraceUnit !== hasTraceRatio) {
      ElMessage.error('追溯码单位与倍数须同时填写或同时留空');
      loading.value = false;
      return;
    }
    if (hasTraceRatio) {
      const ratio = Number(traceRatioRaw);
      if (!Number.isFinite(ratio) || ratio <= 0) {
        ElMessage.error('追溯码/拆零倍数须大于 0');
        loading.value = false;
        return;
      }
      submitData.traceUnitCode = traceUnitCode;
      submitData.traceUnitRatio = ratio;
    } else {
      submitData.traceUnitCode = undefined;
      submitData.traceUnitRatio = undefined;
    }

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
      formFieldProps: {
        validateOnBlur: false,
        validateOnChange: false,
        validateOnInput: false,
        validateOnModelUpdate: false,
      },
      componentProps: {
        validateEvent: false,
        class: 'w-full',
      },
    },
    // lg 起固定四列，避免「库存与单位」栅格在 lg:3 列时第四格换行导致前三行只有三列（与药品目录一致）
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
        component: 'Select',
        fieldName: 'typeCode',
        label: '耗材种类',
        rules: 'required',
        componentProps: {
          options: equipmentTypes.value,
          filterable: true,
          placeholder: '请选择耗材种类（取药路由用）',
          clearable: false,
        },
      },
      {
        component: 'Select',
        fieldName: 'manufacturerId',
        label: '生产厂家',
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
        component: 'Input',
        fieldName: 'approvalNumber',
        label: '批准文号',
      },
      {
        component: 'Input',
        fieldName: 'totalVolume',
        label: '包装规格',
        rules: 'required',
      },
      {
        component: 'Input',
        fieldName: 'ybNo',
        label: '医保编码',
        componentProps: {
          onInput: (value: string) => {
            formData.value = { ...formData.value, ybNo: value };
          },
        },
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
          options: medChrgitmTypeOptions.value,
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
          options: chrgitmLvOptions.value,
          filterable: true,
          placeholder: '请选择医保等级',
          clearable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'mrSettlementTypeCode',
        label: '病案结算类型',
        componentProps: {
          options: mrSettlementTypeCodeOptions.value,
          filterable: true,
          placeholder: '请选择病案结算类型',
          clearable: true,
        },
      },
      {
        component: 'Checkbox',
        fieldName: 'ybMatchFlag',
        label: '医保对码',
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
        componentProps: { min: 1, step: 0.001, controlsPosition: 'right' },
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
      // 库存与单位：对齐药品目录四列栅格 —
      // 列1 单位、列2 倍数聚拢；列3 价格；列4 耗材无门诊/住院拆分，用占位；
      // 第4 行第3–4 列：警戒线；第5 行：追溯码单位+倍数（游离单位，仍靠左与其它单位对齐）
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
        componentProps: UNIT_RATIO_INPUT_PROPS,
      },
      {
        component: 'InputNumber',
        fieldName: 'purchasePrice',
        label: '采购参考价（包装单位）',
        rules: isEditMode.value ? undefined : 'required',
        componentProps: {
          min: 0,
          precision: 2,
          placeholder: '请输入采购参考价',
          disabled: isEditMode.value,
        },
      },
      buildInvGridPadField('__inv_grid_pad_r1c4'),
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
        componentProps: UNIT_RATIO_INPUT_PROPS,
      },
      {
        component: 'InputNumber',
        fieldName: 'retailPrice',
        label: '销售参考价（包装单位）',
        rules: isEditMode.value ? undefined : 'required',
        componentProps: {
          min: 0,
          precision: 2,
          placeholder: '请输入销售参考价',
          disabled: isEditMode.value,
        },
      },
      buildInvGridPadField('__inv_grid_pad_r2c4'),
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
        componentProps: UNIT_RATIO_INPUT_PROPS,
      },
      {
        component: 'InputNumber',
        fieldName: 'maximumRetailPrice',
        label: '最高零售价（包装单位）',
        componentProps: {
          min: 0,
          precision: 2,
          placeholder: '请输入最高零售价',
          disabled: isEditMode.value,
        },
      },
      buildInvGridPadField('__inv_grid_pad_r3c4'),
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
      buildInvGridPadField('__inv_grid_pad_r4c2'),
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
        componentProps: {
          options: unitCodeOptions.value,
          filterable: true,
          placeholder: '可选，追溯码贴码单位',
          clearable: true,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'traceUnitRatio',
        label: '追溯码/拆零倍数',
        componentProps: {
          ...UNIT_RATIO_INPUT_PROPS,
          placeholder: '1 追溯码单位 = 多少拆零',
        },
      },
      buildInvGridPadField('__inv_grid_pad_r5c3'),
      buildInvGridPadField('__inv_grid_pad_r5c4'),

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
        component: 'CheckboxInlineGroup',
        fieldName: '__checkbox_inline_group',
        formItemClass: 'col-span-full !mb-0',
        componentProps: {
          fields: [
            { fieldName: 'allergenFlag', label: '过敏标记' },
            { fieldName: 'ybFlag', label: '医保标记' },
            { fieldName: 'hvcmFlag', label: '高值耗材标志' },
            { fieldName: 'chargeableFlag', label: '收费耗材' },
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
    if (props.catalogId !== catalogId) {
      return;
    }
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
      <ElAlert
        v-if="catalogStructureLocked"
        class="mx-4 mb-3 sm:mx-5"
        type="warning"
        :closable="false"
        show-icon
        :title="
          editabilityMessage || '该耗材已产生业务引用，名称/分类/单位/拆零倍数/价格等字段不可修改'
        "
      />
      <div
        ref="formAreaRef"
        class="device-catalog-form-dialog__form-wrap box-border px-4 pb-3 pt-2 sm:px-5"
        @keydown.capture="handleDeviceFormKeydown"
      >
        <Form />
      </div>
    </div>
    <template #footer>
      <div class="flex flex-wrap items-center justify-between gap-2 px-4 sm:px-5">
        <div v-if="isEditMode" class="flex flex-wrap gap-2">
          <ElButton type="success" plain @click="openCatalogPicker">从国家目录选择</ElButton>
          <ElButton type="primary" plain @click="handleViewLotPrices">价格</ElButton>
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
      catalog-type="device"
      :item-id="catalogId"
      :item-name="String(formData?.name ?? '')"
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

.device-catalog-form-dialog {
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
.device-catalog-form-dialog .device-catalog-form-dialog__section-title {
  margin-top: 0;
  padding-bottom: 0 !important;
}

.device-catalog-form-dialog .device-catalog-form-dialog__section-title:not(:first-child) {
  margin-top: 8px;
}

.device-catalog-form-dialog .el-form-item {
  margin-bottom: 14px;
}

.device-catalog-form-dialog .el-form-item__content {
  min-width: 0;
}

.device-catalog-form-dialog .inv-grid-pad .el-form-item__content {
  display: flex;
  min-height: 40px;
  align-items: center;
}

.device-catalog-form-dialog .el-form-item__content .el-input,
.device-catalog-form-dialog .el-form-item__content .el-select {
  width: 100%;
  max-width: 100%;
}

.device-catalog-form-dialog .el-form-item__content .el-input-number {
  display: inline-flex;
  width: 100%;
  max-width: 100%;
}

.device-catalog-form-dialog .el-input-number .el-input {
  flex: 1;
  width: 100%;
}

.device-catalog-form-dialog .el-input-number .el-input__wrapper {
  width: 100%;
  box-sizing: border-box;
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
