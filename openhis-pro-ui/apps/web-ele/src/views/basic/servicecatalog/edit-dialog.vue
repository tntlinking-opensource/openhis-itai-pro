<script lang="ts" setup>
import type { ServiceCatalogInitResponse, ServiceCatalogItem } from '#/api';

import type { NationalCatalogItem } from '#/chs/api';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Delete, Plus } from '@element-plus/icons-vue';
import { ElAlert, ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import {
  EXTENDED_FORM_KEYBOARD_SELECTORS,
  FORM_KEYBOARD_GRID_NAV_KEYS,
  useFormKeyboardNavigation,
} from '#/hooks/use-form-keyboard-navigation';
import { createServiceCatalogApi, getServiceCatalogApi, updateServiceCatalogApi } from '#/api';
import { getChargeTypeListApi } from '#/api/core/common';
import { revokeActivityCatalog3302Api, uploadActivityCatalog3301Api } from '#/chs/api';
import MedicationCatalogPickerDialog from '#/chs/components/medication-catalog-picker-dialog.vue';
import { catalogChrgitmLvToFormValue } from '#/chs/constants/catalog-chrgitm-lv';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';
import {
  parseCatalogPriceUplmt,
  resolveOptionValueByLabel,
  resolveOptionValueByLabels,
  type CatalogLabeledOption,
} from '#/chs/utils/apply-national-catalog-fields';
import ServiceCatalogSelect from '#/components/select-service-catalog.vue';
import {
  calcMoneyLineTotal,
  formatMoneyDisplay,
  sumMoneyThenSettle,
} from '#/utils/format/money-scale';
import {
  applyCatalogEditabilityFromDetail,
  buildCatalogEditabilitySchemaPatches,
  CATALOG_EDITABILITY_OK,
  isCatalogStructureLocked,
  isServiceCatalogFieldLocked,
} from '#/views/basic/catalog/catalog-editability';
import {
  BUILT_IN_ACTIVITY_EDIT_HINT,
  BUILT_IN_ACTIVITY_LOCKED_FORM_FIELDS,
  isBuiltInActivityName,
} from '#/constants/domain/built-in-activity-definition';
import {
  resolveCatalogRecordNavState,
  type CatalogRecordNavDirection,
} from '#/views/basic/catalog/catalog-record-nav';

interface ServiceCatalogChildRow {
  activityId?: number;
  childrenId?: string;
  childrenName?: string;
  childrenNum?: number;
  childrenRetailPrice?: number;
  id?: number;
}

defineOptions({
  name: 'ServiceCatalogEditDialog',
});

interface Props {
  visible?: boolean;
  formData?: Partial<ServiceCatalogItem>;
  initOptions?: ServiceCatalogInitResponse;
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

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
  initOptions: () => ({}),
  recordIds: () => [],
  pageNo: 1,
  pageSize: 10,
  listTotal: 0,
  navigating: false,
});

const emit = defineEmits<{
  navigate: [direction: CatalogRecordNavDirection];
  success: [];
  'update:visible': [value: boolean];
}>();

const loading = ref(false);
const upload3301Loading = ref(false);
const revoke3302Loading = ref(false);
const pickerVisible = ref(false);
const defaultContractBusNo = ref<string>();
const originalData = ref<Partial<ServiceCatalogItem>>({});
const insuranceFormData = ref<Partial<ServiceCatalogItem>>({});
const formAreaRef = ref<HTMLElement | null>(null);
const editabilityCode = ref(CATALOG_EDITABILITY_OK);
const editabilityMessage = ref('');

const catalogStructureLocked = computed(() => isCatalogStructureLocked(editabilityCode.value));

/** 当前编辑行是否为产品内置诊疗（按名称识别） */
const isBuiltInActivity = computed(() =>
  isBuiltInActivityName(String(originalData.value?.name ?? props.formData?.name ?? '')),
);

/** 顶部提示：内置优先，其次业务引用锁定 */
const structureLockAlertTitle = computed(() => {
  if (isBuiltInActivity.value) {
    return BUILT_IN_ACTIVITY_EDIT_HINT;
  }
  return editabilityMessage.value || '该诊疗项目已产生业务引用，名称/分类/单位/价格等字段不可修改';
});

/** 是否展示结构锁定提示条 */
const showStructureLockAlert = computed(
  () => isBuiltInActivity.value || catalogStructureLocked.value,
);

type OptionItem = {
  children?: OptionItem[];
  info?: string;
  label?: string;
  name?: string;
  text?: string;
  value: number | string;
};

/** 将 init 下拉规范为 Select options（value 统一 string） */
const convertOptions = (options: null | OptionItem[] | undefined | unknown) => {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  return options
    .filter(
      (item): item is OptionItem =>
        typeof item === 'object' &&
        item !== null &&
        'value' in item &&
        (typeof (item as OptionItem).value === 'string' ||
          typeof (item as OptionItem).value === 'number'),
    )
    .map((item) => ({
      label: item.label || item.info || item.text || item.name || String(item.value),
      value: String(item.value),
    }));
};

/**
 * 合并 init 下拉与「已知新码」及当前行孤儿值，避免 Select 只回显裸 code。
 *
 * <p>场景：SQL 已写入 `activity_category_code=53` / `ActivityType=7`，但进程字典缓存未刷或后端未发版时，
 * 仍保证编辑页能显示「诊查费」「诊查」。</p>
 *
 * @param options init 下拉（已规范化为 string value）
 * @param current 当前表单值（可空）
 * @param knownLabels 已知新码 → 展示名（如 53→诊查费）
 * @returns 合并后的下拉选项
 */
const withOrphanSelectOption = (
  options: Array<{ label: string; value: string }>,
  current: unknown,
  knownLabels: Record<string, string> = {},
) => {
  const merged = [...options];
  // 已出现过的 value，避免重复 push
  const seen = new Set(merged.map((item) => item.value));
  // 先把已知新码常驻进下拉（不依赖当前行是否已选）
  for (const [code, label] of Object.entries(knownLabels)) {
    if (!seen.has(code)) {
      merged.push({ value: code, label });
      seen.add(code);
    }
  }
  // 无当前值：只返回 init+已知新码
  if (current === undefined || current === null || current === '') {
    return merged;
  }
  const value = String(current);
  // 已在选项中：无需再补孤儿行
  if (seen.has(value)) {
    return merged;
  }
  // 未知当前值：补一条（优先用 knownLabels，否则回落裸 code）
  return [...merged, { value, label: knownLabels[value] || value }];
};

/** 目录分类：53=诊查费（activity_category_code） */
const CATEGORY_ORPHAN_LABELS: Record<string, string> = { '53': '诊查费' };
/** 业务类型：7=诊查（ActivityType.CONSULTATION） */
const TYPE_ORPHAN_LABELS: Record<string, string> = { '7': '诊查' };

/** 病案结算类型候选项（依赖列表页 init） */
const mrSettlementTypeCodeOptions = computed(() =>
  convertOptions(props.initOptions?.mrSettlementTypeCodeOptions as OptionItem[] | undefined),
);

const { handleFormKeydown: handleServiceFormKeydown } = useFormKeyboardNavigation(formAreaRef, {
  selectors: EXTENDED_FORM_KEYBOARD_SELECTORS,
  keys: FORM_KEYBOARD_GRID_NAV_KEYS,
  selectPolicy: true,
  navigationMode: 'grid',
});

const isEditMode = computed(() => {
  return !!props.formData?.id;
});

/** 编辑态且列表有多条时展示上下翻（可跨页） */
const recordNavState = computed(() =>
  resolveCatalogRecordNavState(props.recordIds, props.formData?.id, {
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

/** 切换到上一条诊疗（必要时由列表翻页） */
function handleNavigatePrev() {
  if (!canNavigatePrev.value || recordNavBusy.value) {
    return;
  }
  emit('navigate', -1);
}

/** 切换到下一条诊疗（必要时由列表翻页） */
function handleNavigateNext() {
  if (!canNavigateNext.value || recordNavBusy.value) {
    return;
  }
  emit('navigate', 1);
}

const activityDefId = computed(() => originalData.value?.id ?? props.formData?.id);

const pickerContext = computed(() => ({
  name: String(insuranceFormData.value?.name ?? ''),
  listType: '201',
}));

const canUpload3301 = computed(
  () => isEditMode.value && Boolean(String(insuranceFormData.value?.ybNo ?? '').trim()),
);

const canRevoke3302 = computed(
  () =>
    isEditMode.value &&
    Boolean(String(insuranceFormData.value?.ybNo ?? '').trim()) &&
    isYbMatched(insuranceFormData.value?.ybMatchFlag),
);

function isYbMatched(value: unknown): boolean {
  return value === true || value === 1 || value === '1' || value === 'YES';
}

function syncInsuranceFormData(data: Record<string, unknown>) {
  insuranceFormData.value = {
    name: data.name as string | undefined,
    ybNo: data.ybNo as string | undefined,
    ybMatchFlag: data.ybMatchFlag as ServiceCatalogItem['ybMatchFlag'],
    chrgitmLv: data.chrgitmLv as ServiceCatalogItem['chrgitmLv'],
  };
}

async function loadDefaultContractBusNo() {
  try {
    const list = await getChargeTypeListApi();
    const eligible = filterInsuranceChargeTypes(list ?? []);
    defaultContractBusNo.value = resolveDefaultInsuranceBusNo(eligible, defaultContractBusNo.value);
  } catch {
    defaultContractBusNo.value = undefined;
  }
}

async function openCatalogPicker() {
  try {
    const values = (await formApi.getValues()) as Record<string, unknown>;
    syncInsuranceFormData({
      ...insuranceFormData.value,
      name: values.name as string | undefined,
      ybNo: values.ybNo as string | undefined,
      chrgitmLv: values.chrgitmLv as ServiceCatalogItem['chrgitmLv'],
      ybMatchFlag: values.ybMatchFlag as ServiceCatalogItem['ybMatchFlag'],
    });
  } catch {
    // 表单未就绪时仍打开选码弹窗
  }
  pickerVisible.value = true;
}

function parsePriceUplmt(value: string | undefined | null): number | undefined {
  return parseCatalogPriceUplmt(value);
}

/** 字段未锁定且目录有值时写入 */
function canApplyServiceCatalogField(fieldName: string, catalogHasValue: boolean): boolean {
  if (!catalogHasValue) {
    return false;
  }
  // 内置项禁止选码回填改写名称/类目/类型
  if (
    isBuiltInActivity.value &&
    (BUILT_IN_ACTIVITY_LOCKED_FORM_FIELDS as readonly string[]).includes(fieldName)
  ) {
    return false;
  }
  return !isServiceCatalogFieldLocked(editabilityCode.value, fieldName);
}

/**
 * 国家医保目录选码回填：医保码/等级必带；名称/单位/限价/分类/财务类型/说明等尽量带入（不回填拼音）。
 */
async function applyCatalogSelection(row: NationalCatalogItem) {
  const patch: Record<string, unknown> = {
    ybNo: row.hilistCode ?? '',
    ybFlag: 'YES',
    ybMatchFlag: 'NO',
  };
  const lv = catalogChrgitmLvToFormValue(row.chrgitmLv);
  if (lv !== undefined) {
    patch.chrgitmLv = lv;
  }

  const hilistName = String(row.hilistName ?? '').trim();
  if (canApplyServiceCatalogField('name', Boolean(hilistName))) {
    patch.name = hilistName;
  }

  const categoryOptions = convertOptions(
    props.initOptions?.diagnosisCategoryOptions,
  ) as CatalogLabeledOption[];
  const categoryCode = resolveOptionValueByLabels(categoryOptions, [
    '医疗服务',
    '医疗服务项目',
    '诊疗',
    '检查',
    '化验',
    '治疗',
  ]);
  if (canApplyServiceCatalogField('categoryCode', Boolean(categoryCode))) {
    patch.categoryCode = categoryCode;
  }

  const finOptions = convertOptions(
    props.initOptions?.finTypeCodeOptions,
  ) as CatalogLabeledOption[];
  // 财务类型：优先目录名称关键词，再兜底「一般诊疗费」
  const itemTypeFromName = hilistName
    ? resolveOptionValueByLabels(
        finOptions,
        [
          hilistName.includes('化验') || hilistName.includes('检验') ? '化验费' : '',
          hilistName.includes('检查') ? '检查费' : '',
          hilistName.includes('治疗') ? '治疗费' : '',
          hilistName.includes('手术') ? '手术费' : '',
        ].filter(Boolean),
      )
    : undefined;
  const itemTypeCode =
    itemTypeFromName ??
    resolveOptionValueByLabels(finOptions, ['一般诊疗费', '诊疗费', '医疗服务']);
  if (canApplyServiceCatalogField('itemTypeCode', Boolean(itemTypeCode))) {
    patch.itemTypeCode = itemTypeCode;
  }

  const unitOptions = convertTreeOptions(
    props.initOptions?.unitCodeOptions,
  ) as CatalogLabeledOption[];
  const packageUnitCode =
    resolveOptionValueByLabel(unitOptions, row.minPrcunt) ??
    resolveOptionValueByLabel(unitOptions, row.minPrepunt) ??
    resolveOptionValueByLabel(unitOptions, row.minPacunt);
  if (canApplyServiceCatalogField('packageUnitCode', Boolean(packageUnitCode))) {
    patch.packageUnitCode = packageUnitCode;
  }

  const maxPrice = parsePriceUplmt(row.priceUplmtAmt);
  if (canApplyServiceCatalogField('maximumRetailPrice', maxPrice !== undefined)) {
    patch.maximumRetailPrice = maxPrice;
  }

  // 说明：目录规格/剂型拼进 descriptionText（空才写，避免冲掉已有说明）
  const spec = String(row.spec ?? '').trim();
  const dosform = String(row.dosform ?? '').trim();
  const descBits = [spec && `规格：${spec}`, dosform && `剂型：${dosform}`].filter(Boolean);
  if (descBits.length > 0 && canApplyServiceCatalogField('descriptionText', true)) {
    const currentDesc = String(
      ((await formApi.getValues()) as Record<string, unknown>).descriptionText ?? '',
    ).trim();
    if (!currentDesc) {
      patch.descriptionText = descBits.join('；');
    }
  }

  for (const [field, value] of Object.entries(patch)) {
    await formApi.setFieldValue(field, value);
  }

  syncInsuranceFormData({
    ...insuranceFormData.value,
    name: (patch.name as string | undefined) ?? insuranceFormData.value.name,
    ybNo: row.hilistCode,
    chrgitmLv: lv,
    ybMatchFlag: 'NO',
    ybFlag: 'YES',
  });
  ElMessage.success(`已选用医保编码 ${row.hilistCode}，保存诊疗后可上传 3301`);
}

async function handleUpload3301() {
  const id = activityDefId.value;
  if (!id) {
    ElMessage.warning('请先保存诊疗后再上传 3301');
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
    const result = await uploadActivityCatalog3301Api({
      contractBusNo: defaultContractBusNo.value,
      activityDefId: id,
      listType: '201',
    });
    if (result.allSuccess) {
      ElMessage.success(result.summary ?? '3301 上传成功');
      formApi.setFieldValue('ybMatchFlag', 'YES');
      syncInsuranceFormData({ ...insuranceFormData.value, ybMatchFlag: 'YES' });
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
  const id = activityDefId.value;
  if (!id) {
    ElMessage.warning('请先保存诊疗后再撤销 3302');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '将撤销该诊疗项目在医保平台的目录对照，本地「医保对码」将取消勾选。是否继续？',
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
    const result = await revokeActivityCatalog3302Api({
      contractBusNo: defaultContractBusNo.value,
      activityDefId: id,
      listType: '201',
    });
    if (result.allSuccess) {
      ElMessage.success(result.summary ?? '3302 撤销成功');
      formApi.setFieldValue('ybMatchFlag', 'NO');
      syncInsuranceFormData({ ...insuranceFormData.value, ybMatchFlag: 'NO' });
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

const childrenList = ref<ServiceCatalogChildRow[]>([]);

const hasSelectedChildren = computed(() =>
  childrenList.value.some((row) => Boolean(String(row.childrenId ?? '').trim())),
);

const defaultChildrenRow = (): ServiceCatalogChildRow => ({
  childrenId: undefined,
  childrenName: '',
  childrenNum: undefined,
  childrenRetailPrice: undefined,
});

/** 子项执行次数：空或非法时回落为 1 */
function normalizeChildrenNum(value: unknown): number {
  const num = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(num) && num > 0 ? Math.trunc(num) : 1;
}

/** 将接口金额字段转为数字，非法值返回 undefined */
function coerceRetailPrice(value: unknown): number | undefined {
  if (value === null || value === undefined || value === '') {
    return undefined;
  }
  const num = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(num) ? num : undefined;
}

/** 子项单价展示文案 */
function formatChildUnitPrice(price: number | undefined): string {
  if (price === undefined || price === null || !Number.isFinite(price)) {
    return '-';
  }
  return formatMoneyDisplay(price);
}

/** 子项行小计（单价 × 执行次数） */
function formatChildLineTotal(item: ServiceCatalogChildRow): string {
  const price = item.childrenRetailPrice;
  if (price === undefined || price === null || !Number.isFinite(price)) {
    return '-';
  }
  const count = normalizeChildrenNum(item.childrenNum);
  return formatMoneyDisplay(calcMoneyLineTotal(price, count));
}

/** 按子项单价与执行次数合计售价（6 位加总后 2 位） */
function calcChildrenRetailTotal(list: ServiceCatalogChildRow[]): number {
  const lineTotals: number[] = [];
  for (const item of list) {
    const childId = String(item.childrenId ?? '').trim();
    if (!childId) {
      continue;
    }
    const unitPrice = item.childrenRetailPrice;
    if (unitPrice === undefined || unitPrice === null) {
      continue;
    }
    const count = normalizeChildrenNum(item.childrenNum);
    lineTotals.push(calcMoneyLineTotal(unitPrice, count));
  }
  return sumMoneyThenSettle(lineTotals);
}

/** 存在已选子项时，将售价同步为子项合计 */
function syncRetailPriceFromChildren() {
  if (!hasSelectedChildren.value) {
    return;
  }
  formApi.setFieldValue('retailPrice', calcChildrenRetailTotal(childrenList.value));
}

/** 编辑回显时按子项 id 补齐单价 */
async function hydrateChildrenRetailPrices(
  children: ServiceCatalogChildRow[],
): Promise<ServiceCatalogChildRow[]> {
  return Promise.all(
    children.map(async (child) => {
      if (!child.childrenId || child.childrenRetailPrice !== undefined) {
        return child;
      }
      try {
        const response = await getServiceCatalogApi({ id: String(child.childrenId) });
        return {
          ...child,
          childrenRetailPrice: coerceRetailPrice(response.data?.retailPrice),
        };
      } catch {
        return child;
      }
    }),
  );
}

/** 选中/清空子项时同步名称、单价；选中后执行次数默认 1 */
function handleChildSelect(item: ServiceCatalogChildRow, selected: ServiceCatalogItem[]) {
  const row = selected[0];
  if (!row) {
    item.childrenName = '';
    item.childrenNum = undefined;
    item.childrenRetailPrice = undefined;
    return;
  }
  item.childrenName = String(row.name ?? '');
  item.childrenRetailPrice = coerceRetailPrice(row.retailPrice);
  if (item.childrenNum === null || item.childrenNum === undefined) {
    item.childrenNum = 1;
  }
}

/** 清空弹窗表单与子项列表 */
async function resetFormState() {
  originalData.value = {};
  insuranceFormData.value = {};
  editabilityCode.value = CATALOG_EDITABILITY_OK;
  editabilityMessage.value = '';
  await formApi.resetForm();
  childrenList.value = [defaultChildrenRow()];
  syncFormFieldAvailability();
}

/** 按 editabilityCode 同步表单字段 disabled */
function syncServiceEditabilitySchema() {
  if (!formApi?.isMounted) {
    return;
  }
  formApi.updateSchema(buildCatalogEditabilitySchemaPatches('service', editabilityCode.value));
  // 产品内置：强制锁定名称/类目/类型（叠在业务引用锁之上）
  if (isBuiltInActivity.value) {
    formApi.updateSchema(
      BUILT_IN_ACTIVITY_LOCKED_FORM_FIELDS.map((fieldName) => ({
        fieldName,
        componentProps: { disabled: true },
      })),
    );
  }
}

/** 售价禁用态：业务引用锁定优先，其次有子项时自动合计 */
function syncRetailPriceFieldSchema() {
  if (!formApi?.isMounted) {
    return;
  }
  const lockedByEditability = isServiceCatalogFieldLocked(editabilityCode.value, 'retailPrice');
  formApi.updateSchema([
    {
      fieldName: 'retailPrice',
      componentProps: {
        disabled: lockedByEditability || hasSelectedChildren.value,
        placeholder: hasSelectedChildren.value ? '由子项自动合计' : '请输入售价',
      },
    },
  ]);
}

/**
 * 病案结算类型下拉：保证 options 已挂到 schema（与药品目录 updateFormOptions 同序）。
 * 锁定态仍可改（BR-CAT-008），禁止再绑 editability disabled。
 */
function syncMrSettlementTypeOptions() {
  if (!formApi?.isMounted) {
    return;
  }
  formApi.updateSchema([
    {
      fieldName: 'mrSettlementTypeCode',
      componentProps: {
        options: mrSettlementTypeCodeOptions.value,
        filterable: true,
        placeholder: '请选择病案结算类型',
        clearable: true,
        disabled: false,
      },
    },
  ]);
}

/** ElSelectV2：options 晚到或 schema 刷新后需重写 modelValue，否则有值也不显示文案 */
async function reapplyMrSettlementTypeCode(source: Record<string, unknown> = originalData.value) {
  if (!formApi?.isMounted) {
    return;
  }
  const code = source.mrSettlementTypeCode;
  if (code === undefined || code === null || code === '') {
    return;
  }
  await formApi.setFieldValue('mrSettlementTypeCode', String(code));
}

/** 重置/切换模式后统一恢复字段可编辑态 */
function syncFormFieldAvailability() {
  syncServiceEditabilitySchema();
  syncRetailPriceFieldSchema();
  // editability 仅补丁 disabled 后重新挂上 options，避免 Select 无候选项而不回显
  syncMrSettlementTypeOptions();
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
    } as Record<string, unknown>;

    // 未选诊疗项目的空子项行不提交；已选行补齐执行次数默认 1
    if (Array.isArray(mergedData.children)) {
      mergedData.children = mergedData.children
        .filter((item: { childrenId?: null | string } | null | undefined) => {
          if (!item) return false;
          return Boolean(String(item.childrenId ?? '').trim());
        })
        .map(({ childrenRetailPrice: _price, ...child }) => ({
          ...child,
          childrenNum: normalizeChildrenNum(child.childrenNum),
        }));
      if (mergedData.children.length === 0) {
        delete mergedData.children;
      }
    }

    mapLegacyPackageUnitFields(mergedData);
    delete mergedData.salesUnitCode;
    // 诊疗目录无进价（采购参考价）；不提交，避免回写 PURCHASE 定价行
    delete mergedData.purchasePrice;
    const normalizedYbFlag = normalizeWhetherFlagForSubmit(mergedData.ybFlag);
    if (normalizedYbFlag !== undefined) {
      mergedData.ybFlag = normalizedYbFlag;
    }
    const normalizedYbMatchFlag = normalizeWhetherFlagForSubmit(mergedData.ybMatchFlag);
    if (normalizedYbMatchFlag !== undefined) {
      mergedData.ybMatchFlag = normalizedYbMatchFlag;
    }

    const submitData: Partial<ServiceCatalogItem> = {};
    Object.keys(mergedData).forEach((key) => {
      const value = mergedData[key];
      if (value !== null && value !== undefined && value !== '') {
        submitData[key] = value;
      }
    });

    if (isEditMode.value) {
      submitData.editabilityCode = editabilityCode.value;
      await updateServiceCatalogApi(submitData);
      ElMessage.success('更新成功');
    } else {
      const { id: _id, ...createData } = submitData;
      await createServiceCatalogApi(createData);
      ElMessage.success('创建成功');
    }
    emit('success');
    await resetFormState();
    modalApi.close();
  } catch (error) {
    console.error(isEditMode.value ? '更新失败:' : '创建失败:', error);
    ElMessage.error(isEditMode.value ? '更新失败，请稍后重试' : '创建失败，请稍后重试');
  } finally {
    modalApi.unlock();
  }
}

/** 详情回写：先挂 options 再 setValues，最后单独回写病案结算类型 */
async function applyDetailFormValues(normalizedData: Record<string, unknown>) {
  syncMrSettlementTypeOptions();
  await nextTick();
  await formApi.setValues(normalizedData);
  syncInsuranceFormData(normalizedData);
  syncFormFieldAvailability();
  await nextTick();
  // schema/options 刷新后再写一次，避免 ElSelectV2 / TreeSelect 丢回显
  await reapplyMrSettlementTypeCode(normalizedData);
  const packageUnit = normalizedData.packageUnitCode;
  if (packageUnit !== undefined && packageUnit !== null && packageUnit !== '') {
    await formApi.setFieldValue('packageUnitCode', String(packageUnit));
  }
}

/** 避免快速上下翻时旧请求回写覆盖新详情 */
let serviceDetailLoadSeq = 0;

async function loadFormData() {
  const id = props.formData?.id;
  if (id) {
    const loadSeq = ++serviceDetailLoadSeq;
    loading.value = true;
    try {
      // 切换记录前先清空，避免上一张残留字段/子项混入回显
      await resetFormState();
      if (loadSeq !== serviceDetailLoadSeq) {
        return;
      }
      const response = await getServiceCatalogApi({ id: String(id) });
      if (loadSeq !== serviceDetailLoadSeq) {
        return;
      }
      const data = response.data;
      if (data) {
        const editability = applyCatalogEditabilityFromDetail(data);
        editabilityCode.value = editability.code;
        editabilityMessage.value = editability.message;
        const normalizedData = normalizeFormSelectValues(data as Record<string, unknown>);
        originalData.value = { ...normalizedData };
        await applyDetailFormValues(normalizedData);
        const mappedChildren =
          Array.isArray(data.children) && data.children.length > 0
            ? data.children.map((child) => ({
                ...child,
                childrenId:
                  child.childrenId !== null && child.childrenId !== undefined
                    ? String(child.childrenId)
                    : undefined,
                childrenNum: normalizeChildrenNum(child.childrenNum),
              }))
            : [defaultChildrenRow()];
        childrenList.value = await hydrateChildrenRetailPrices(mappedChildren);
        if (loadSeq !== serviceDetailLoadSeq) {
          return;
        }
        syncRetailPriceFromChildren();
        // 子项加载会触发 schema 刷新，再补一次病案结算类型回显
        await nextTick();
        await reapplyMrSettlementTypeCode(normalizedData);
      } else {
        ElMessage.warning('未找到服务目录信息');
        modalApi.close();
      }
    } catch (error) {
      if (loadSeq !== serviceDetailLoadSeq) {
        return;
      }
      console.error('获取服务目录详情失败:', error);
      ElMessage.error('获取服务目录详情失败，请稍后重试');
      modalApi.close();
      return;
    } finally {
      if (loadSeq === serviceDetailLoadSeq) {
        loading.value = false;
      }
    }
  } else {
    if (props.formData && Object.keys(props.formData).length > 0) {
      const normalizedData = normalizeFormSelectValues(props.formData as Record<string, unknown>);
      originalData.value = { ...normalizedData };
      await applyDetailFormValues(normalizedData);
      // 初始化子项列表
      const presetChildren =
        props.formData.children && Array.isArray(props.formData.children)
          ? props.formData.children.map((child) => ({
              ...child,
              childrenNum: normalizeChildrenNum(child.childrenNum),
            }))
          : [];
      childrenList.value =
        presetChildren.length > 0
          ? await hydrateChildrenRetailPrices(presetChildren)
          : [defaultChildrenRow()];
      syncRetailPriceFromChildren();
    } else {
      originalData.value = {};
      await resetFormState();
    }
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
    await loadFormData();
  },
  onClosed: async () => {
    await resetFormState();
  },
  onConfirm: handleSubmit,
  showCancelButton: true,
  showConfirmButton: true,
  confirmText: '确定',
  cancelText: '取消',
  contentClass: 'service-catalog-form-dialog__content',
});

/** 历史表单 salesUnitCode 与后端 packageUnitCode 对齐 */
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
}

function coerceWhetherOptionValue(value: unknown): string | undefined {
  if (value === true || value === 1 || value === '1') {
    return 'YES';
  }
  if (value === false || value === 0 || value === '0') {
    return 'NO';
  }
  if (value === 'YES' || value === 'NO') {
    return value;
  }
  return undefined;
}

function normalizeWhetherFlagForSubmit(value: unknown): number | undefined {
  if (value === true || value === 1 || value === '1' || value === 'YES') {
    return 1;
  }
  if (value === false || value === 0 || value === '0' || value === 'NO') {
    return 0;
  }
  return undefined;
}

function normalizeFormSelectValues(data: Record<string, unknown>) {
  const next = { ...data };
  mapLegacyPackageUnitFields(next);
  // 这些字段在表单里是 Select，需要和 options.value 类型保持一致
  [
    'typeEnum',
    'chrgitmLv',
    'mrSettlementTypeCode',
    'packageUnitCode',
    'categoryCode',
    'itemTypeCode',
    'ybType',
  ].forEach((field) => {
    const v = next[field];
    if (v !== undefined && v !== null && v !== '') {
      next[field] = String(v);
    }
  });
  const ybFlag = coerceWhetherOptionValue(next.ybFlag);
  if (ybFlag !== undefined) {
    next.ybFlag = ybFlag;
  }
  const ybMatchFlag = coerceWhetherOptionValue(next.ybMatchFlag);
  if (ybMatchFlag !== undefined) {
    next.ybMatchFlag = ybMatchFlag;
  }
  return next;
}

// 转换树形选项格式
interface TreeOption {
  label: string;
  value: number | string;
  children?: TreeOption[];
}

const convertTreeOptions = (options: unknown): TreeOption[] => {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  return options
    .filter(
      (item): item is { children?: unknown[]; label: string; value: number | string } =>
        typeof item === 'object' &&
        item !== null &&
        'label' in item &&
        'value' in item &&
        (typeof item.value === 'string' || typeof item.value === 'number'),
    )
    .map((item) => ({
      label: item.label,
      value: String(item.value),
      children: item.children ? convertTreeOptions(item.children) : undefined,
    }));
};

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
    wrapperClass: 'grid grid-cols-4 gap-x-4',
    schema: computed(() => [
      {
        component: 'SectionTitle',
        fieldName: '__section_basic',
        label: '基础信息',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0 !pb-0 service-catalog-form-dialog__section-title',
        componentProps: {
          title: '基础信息',
        },
      },
      {
        component: 'Input',
        fieldName: 'name',
        label: '诊疗名称',
        rules: 'required',
      },
      {
        component: 'Select',
        fieldName: 'categoryCode',
        label: '目录分类',
        rules: 'required',
        componentProps: {
          options: withOrphanSelectOption(
            convertOptions(props.initOptions?.diagnosisCategoryOptions),
            props.formData?.categoryCode,
            CATEGORY_ORPHAN_LABELS,
          ),
          filterable: true,
          placeholder: '请选择目录分类',
          clearable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'typeEnum',
        label: '业务类型',
        rules: 'required',
        componentProps: {
          options: withOrphanSelectOption(
            convertOptions(props.initOptions?.typeEnumOptions),
            props.formData?.typeEnum,
            TYPE_ORPHAN_LABELS,
          ),
          placeholder: '请选择业务类型',
          clearable: true,
        },
      },
      {
        component: 'ApiTreeSelect',
        fieldName: 'packageUnitCode',
        label: '销售/计价单位',
        rules: 'required',
        componentProps: {
          data: convertTreeOptions(props.initOptions?.unitCodeOptions),
          filterable: true,
          placeholder: '请选择销售/计价单位',
          checkStrictly: true,
          clearable: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'pyStr',
        label: '拼音',
      },
      {
        component: 'Input',
        fieldName: 'wbStr',
        label: '五笔',
      },
      {
        component: 'Select',
        fieldName: 'itemTypeCode',
        label: '财务类型',
        componentProps: {
          options: convertOptions(props.initOptions?.finTypeCodeOptions),
          filterable: true,
          placeholder: '请选择财务类型',
          clearable: true,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'retailPrice',
        label: '售价',
        rules: 'required',
        componentProps: {
          precision: 2,
          min: 0,
          controlsPosition: 'right',
          placeholder: hasSelectedChildren.value ? '由子项自动合计' : '请输入售价',
          disabled: hasSelectedChildren.value,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'maximumRetailPrice',
        label: '最高零售价',
        componentProps: {
          precision: 2,
          min: 0,
          controlsPosition: 'right',
          placeholder: '请输入最高零售价',
        },
      },
      {
        component: 'InputArea',
        fieldName: 'descriptionText',
        label: '描述',
        formItemClass: 'col-span-full',
        componentProps: {
          rows: 3,
          class: 'w-full',
        },
      },
      {
        component: 'SectionTitle',
        fieldName: '__section_yb',
        label: '医保信息',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0 !pb-0 service-catalog-form-dialog__section-title',
        componentProps: {
          title: '医保信息',
        },
      },
      {
        component: 'RadioGroup',
        fieldName: 'ybFlag',
        label: '医保标志',
        componentProps: {
          options: convertOptions(props.initOptions?.statusWeatherOption),
        },
      },
      {
        component: 'Input',
        fieldName: 'ybNo',
        label: '医保编号',
        componentProps: {
          onInput: (value: string) => {
            syncInsuranceFormData({ ...insuranceFormData.value, ybNo: value });
          },
        },
      },
      {
        component: 'Select',
        fieldName: 'ybType',
        label: '医保类别',
        componentProps: {
          options: convertOptions(props.initOptions?.medChrgitmTypeOptions),
          filterable: true,
          placeholder: '请选择医保类别',
          clearable: true,
        },
      },
      {
        component: 'RadioGroup',
        fieldName: 'ybMatchFlag',
        label: '医保对码标记',
        componentProps: {
          options: convertOptions(props.initOptions?.statusWeatherOption),
        },
      },
      {
        component: 'Select',
        fieldName: 'chrgitmLv',
        label: '医保等级',
        componentProps: {
          options: convertOptions(props.initOptions?.chrgitmLvOptions),
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
        component: 'SectionTitle',
        fieldName: '__section_children',
        label: '子项',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0 !pb-0 service-catalog-form-dialog__section-title',
        componentProps: {
          title: '子项',
        },
      },
      {
        component: 'custom',
        fieldName: 'children',
        label: '子项',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0',
        slot: 'children',
      },
    ]),
    showDefaultActions: false,
  }),
);

/** 追加一行空子项（未选项目时不预填执行次数） */
const addChild = () => {
  childrenList.value.push(defaultChildrenRow());
};

const removeChild = (index: number) => {
  childrenList.value.splice(index, 1);
};

watch(
  () => props.formData?.id,
  async (id, prevId) => {
    if (!props.visible) {
      return;
    }
    // 弹窗已打开时切换上下一条：重新拉详情；id 清空则回新增态
    if (id === prevId) {
      return;
    }
    if (!id) {
      await resetFormState();
      return;
    }
    if (prevId === undefined) {
      return;
    }
    await loadFormData();
  },
);

watch(
  childrenList,
  (newList) => {
    formApi.setFieldValue('children', newList);
    syncRetailPriceFromChildren();
    syncRetailPriceFieldSchema();
  },
  { deep: true },
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

/** init 就绪或弹窗打开时补挂 options，并重写已加载详情中的字段值 */
watch(
  () => [props.visible, mrSettlementTypeCodeOptions.value.length] as const,
  async ([visible, optionsLen]) => {
    if (!visible || optionsLen <= 0) {
      return;
    }
    syncMrSettlementTypeOptions();
    await nextTick();
    await reapplyMrSettlementTypeCode();
  },
);
</script>

<template>
  <Modal
    class="service-catalog-form-dialog w-[1100px] max-w-[98vw]"
    :title="isEditMode ? '编辑诊疗' : '新增诊疗'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <ElAlert
      v-if="showStructureLockAlert"
      class="mb-3"
      type="warning"
      :closable="false"
      show-icon
      :title="structureLockAlertTitle"
    />
    <div ref="formAreaRef" @keydown.capture="handleServiceFormKeydown">
      <Form>
        <template #children>
          <div class="service-catalog-form-dialog__children-wrap w-full">
            <div class="service-catalog-form-dialog__children-toolbar">
              <el-button type="primary" size="small" :icon="Plus" @click="addChild">
                添加子项
              </el-button>
            </div>
            <div class="service-catalog-form-dialog__children-panel w-full">
              <div class="service-catalog-form-dialog__child-header">
                <span>诊疗项目</span>
                <span class="text-right">单价</span>
                <span>执行次数</span>
                <span class="text-right">小计</span>
              </div>
              <div class="service-catalog-form-dialog__children-list w-full">
                <div
                  v-for="(item, index) in childrenList"
                  :key="index"
                  class="service-catalog-form-dialog__child-row"
                >
                  <div class="service-catalog-form-dialog__child-select min-w-0">
                    <ServiceCatalogSelect
                      v-model="item.childrenId"
                      :exclude-id="isEditMode ? originalData.id : null"
                      :selected-label="item.childrenName"
                      placeholder="请选择诊疗项目"
                      :multiple="false"
                      @change="(items) => handleChildSelect(item, items)"
                    />
                  </div>
                  <div class="service-catalog-form-dialog__child-price text-right">
                    {{ formatChildUnitPrice(item.childrenRetailPrice) }}
                  </div>
                  <el-input-number
                    v-model="item.childrenNum"
                    size="small"
                    :min="1"
                    :precision="0"
                    placeholder="次数"
                    class="service-catalog-form-dialog__child-num w-full min-w-0"
                    controls-position="right"
                  />
                  <div class="service-catalog-form-dialog__child-subtotal">
                    <span class="service-catalog-form-dialog__child-line-total">
                      {{ formatChildLineTotal(item) }}
                    </span>
                    <el-button
                      type="danger"
                      :icon="Delete"
                      link
                      class="!p-0"
                      @click="removeChild(index)"
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </Form>
      <div class="mt-3 flex flex-wrap gap-2 border-t border-[var(--el-border-color-lighter)] pt-3">
        <ElButton type="success" plain @click="openCatalogPicker">从国家目录选择</ElButton>
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
    </div>
    <MedicationCatalogPickerDialog
      v-model="pickerVisible"
      :context="pickerContext"
      @confirm="applyCatalogSelection"
    />
    <template #prepend-footer>
      <div v-if="showRecordNav" class="mr-auto flex gap-2">
        <ElButton :disabled="!canNavigatePrev || recordNavBusy" @click="handleNavigatePrev">
          上一个
        </ElButton>
        <ElButton :disabled="!canNavigateNext || recordNavBusy" @click="handleNavigateNext">
          下一个
        </ElButton>
      </div>
    </template>
  </Modal>
</template>

<style scoped>
.service-catalog-form-dialog__children-wrap {
  margin-top: 2px;
}

.service-catalog-form-dialog__children-toolbar {
  margin-bottom: 8px;
}

.service-catalog-form-dialog__children-panel {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background-color: var(--el-bg-color);
}

.service-catalog-form-dialog__child-header,
.service-catalog-form-dialog__child-row {
  display: grid;
  grid-template-columns: minmax(0, 2.2fr) 80px 108px 108px;
  gap: 8px;
  align-items: center;
  padding: 0 12px;
}

.service-catalog-form-dialog__child-header {
  min-height: 34px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background-color: var(--el-fill-color-light);
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.service-catalog-form-dialog__children-list {
  max-height: 280px;
  overflow-y: auto;
}

.service-catalog-form-dialog__child-row {
  min-height: 36px;
  padding-top: 4px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.service-catalog-form-dialog__child-row:last-child {
  border-bottom: none;
}

.service-catalog-form-dialog__child-price,
.service-catalog-form-dialog__child-line-total {
  font-size: 13px;
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}

.service-catalog-form-dialog__child-subtotal {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  min-width: 0;
}

.service-catalog-form-dialog__children-list :deep(.vxe-input) {
  height: 28px;
  line-height: 28px;
}

.service-catalog-form-dialog__children-list :deep(.vxe-input--inner) {
  height: 28px;
  min-height: 28px;
  padding-top: 0;
  padding-bottom: 0;
  font-size: 13px;
  line-height: 28px;
}

.service-catalog-form-dialog__children-list
  :deep(.service-catalog-form-dialog__child-num.el-input-number) {
  width: 100%;
  line-height: 28px;
}

.service-catalog-form-dialog__children-list
  :deep(.service-catalog-form-dialog__child-num .el-input__wrapper) {
  min-height: 28px;
  padding-top: 0;
  padding-bottom: 0;
}
</style>

<style>
.service-catalog-form-dialog.el-dialog {
  display: flex;
  max-height: min(96vh, 1040px);
  height: min(94vh, 1000px);
  flex-direction: column;
  margin-top: 2vh !important;
  margin-bottom: 2vh !important;
}

.service-catalog-form-dialog .el-dialog__header {
  flex-shrink: 0;
}

.service-catalog-form-dialog .el-dialog__body {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
}

.service-catalog-form-dialog .el-dialog__body > div {
  min-height: 0;
}

.service-catalog-form-dialog .el-dialog__footer {
  flex-shrink: 0;
}

.service-catalog-form-dialog .service-catalog-form-dialog__section-title {
  margin-top: 0;
  padding-bottom: 0 !important;
}

.service-catalog-form-dialog .service-catalog-form-dialog__section-title:not(:first-child) {
  margin-top: 8px;
}

.service-catalog-form-dialog :deep(.el-form-item) {
  margin-bottom: 14px;
}

.service-catalog-form-dialog :deep(.el-input-number) {
  width: 100%;
  max-width: 100%;
}

.service-catalog-form-dialog :deep(.el-input-number .el-input__wrapper) {
  width: 100%;
}
</style>
