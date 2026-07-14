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
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
  initOptions: () => ({}),
});

const emit = defineEmits<{
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

function openCatalogPicker() {
  pickerVisible.value = true;
}

function applyCatalogSelection(row: NationalCatalogItem) {
  formApi.setFieldValue('ybNo', row.hilistCode ?? '');
  const lv = catalogChrgitmLvToFormValue(row.chrgitmLv);
  if (lv !== undefined) {
    formApi.setFieldValue('chrgitmLv', lv);
  }
  formApi.setFieldValue('ybMatchFlag', 'NO');
  syncInsuranceFormData({
    ...insuranceFormData.value,
    ybNo: row.hilistCode,
    chrgitmLv: lv,
    ybMatchFlag: 'NO',
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
  const count = item.childrenNum && item.childrenNum > 0 ? item.childrenNum : 1;
  return formatMoneyDisplay(calcMoneyLineTotal(price, count));
}

/** 按子项单价与执行次数合计零售价（6 位加总后 2 位） */
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
    const count = item.childrenNum && item.childrenNum > 0 ? item.childrenNum : 1;
    lineTotals.push(calcMoneyLineTotal(unitPrice, count));
  }
  return sumMoneyThenSettle(lineTotals);
}

/** 存在已选子项时，将零售价同步为子项合计 */
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

/** 选中/清空子项时同步名称与单价 */
function handleChildSelect(item: ServiceCatalogChildRow, selected: ServiceCatalogItem[]) {
  const row = selected[0];
  if (!row) {
    item.childrenName = '';
    item.childrenRetailPrice = undefined;
    return;
  }
  item.childrenName = String(row.name ?? '');
  item.childrenRetailPrice = coerceRetailPrice(row.retailPrice);
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
}

/** 零售价禁用态：业务引用锁定优先，其次有子项时自动合计 */
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
        placeholder: hasSelectedChildren.value ? '由子项自动合计' : '请输入零售价',
      },
    },
  ]);
}

/** 病案结算类型下拉：保证 options 已挂到 schema（与药品目录 updateFormOptions 同序） */
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
        disabled: isServiceCatalogFieldLocked(editabilityCode.value, 'mrSettlementTypeCode'),
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

    // 过滤诊疗子项中的空行（名称和执行次数都为空的行不提交）
    if (Array.isArray(mergedData.children)) {
      mergedData.children = mergedData.children
        .filter(
          (item: {
            childrenId: null | string;
            childrenName: string;
            childrenNum: null | number;
          }) => {
            if (!item) return false;
            const hasName = !!item.childrenName;
            const hasNum =
              item.childrenNum !== null && item.childrenNum !== undefined && item.childrenNum !== 0;
            return hasName || hasNum;
          },
        )
        .map(({ childrenRetailPrice: _price, ...child }) => child);
      if (mergedData.children.length === 0) {
        delete mergedData.children;
      }
    }

    mapLegacyPackageUnitFields(mergedData);
    delete mergedData.salesUnitCode;
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
  // schema/options 刷新后再写一次，避免 ElSelectV2 丢回显
  await reapplyMrSettlementTypeCode(normalizedData);
}

async function loadFormData() {
  const id = props.formData?.id;
  if (id) {
    loading.value = true;
    try {
      const response = await getServiceCatalogApi({ id: String(id) });
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
              }))
            : [defaultChildrenRow()];
        childrenList.value = await hydrateChildrenRetailPrices(mappedChildren);
        syncRetailPriceFromChildren();
        // 子项加载会触发 schema 刷新，再补一次病案结算类型回显
        await nextTick();
        await reapplyMrSettlementTypeCode(normalizedData);
      } else {
        ElMessage.warning('未找到服务目录信息');
        modalApi.close();
      }
    } catch (error) {
      console.error('获取服务目录详情失败:', error);
      ElMessage.error('获取服务目录详情失败，请稍后重试');
      modalApi.close();
      return;
    } finally {
      loading.value = false;
    }
  } else {
    if (props.formData && Object.keys(props.formData).length > 0) {
      const normalizedData = normalizeFormSelectValues(props.formData as Record<string, unknown>);
      originalData.value = { ...normalizedData };
      await applyDetailFormValues(normalizedData);
      // 初始化子项列表
      const presetChildren =
        props.formData.children && Array.isArray(props.formData.children)
          ? [...props.formData.children]
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
      value: item.value,
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
          options: convertOptions(props.initOptions?.diagnosisCategoryOptions),
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
          options: convertOptions(props.initOptions?.typeEnumOptions),
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
        fieldName: 'purchasePrice',
        label: '成本价',
        componentProps: {
          precision: 2,
          min: 0,
          controlsPosition: 'right',
          placeholder: '请输入成本价',
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'retailPrice',
        label: '零售价',
        rules: 'required',
        componentProps: {
          precision: 2,
          min: 0,
          controlsPosition: 'right',
          placeholder: hasSelectedChildren.value ? '由子项自动合计' : '请输入零售价',
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

const addChild = () => {
  childrenList.value.push({
    childrenId: undefined,
    childrenName: '',
    childrenNum: undefined,
  });
};

const removeChild = (index: number) => {
  childrenList.value.splice(index, 1);
};

watch(
  () => props.formData?.id,
  async (id) => {
    if (!props.visible || id) {
      return;
    }
    await resetFormState();
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
      v-if="catalogStructureLocked"
      class="mb-3"
      type="warning"
      :closable="false"
      show-icon
      :title="editabilityMessage || '该诊疗项目已产生业务引用，分类/单位/价格等字段不可修改'"
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
                    :min="0"
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
      <div
        v-if="isEditMode"
        class="mt-3 flex flex-wrap gap-2 border-t border-[var(--el-border-color-lighter)] pt-3"
      >
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
