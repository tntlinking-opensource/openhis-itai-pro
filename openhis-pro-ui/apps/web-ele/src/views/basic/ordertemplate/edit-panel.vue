<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { DraftLine } from './order-template-lines';
import type { TcmTemplateDraftLine } from './order-template-tcm-lines';

import type {
  OrderTemplateHeaderItem,
  OrderTemplateInitResponse,
  SaveOrderTemplateHeaderParams,
} from '#/api/basic/order-template';
import type { MedicationCatalogInitResponse } from '#/api/core/system/catalog';
import type { AdviceProductOption } from '#/views/outpatient/doctor-station/advice';

import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElCheckbox,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElScrollbar,
  ElSelect,
} from 'element-plus';

import { pageFrequencyApi } from '#/api/basic/frequency';
import {
  changeOrderTemplateLifecycleApi,
  getOrderTemplateDetailApi,
  ORDER_TEMPLATE_KIND_TCM,
  ORDER_TEMPLATE_LIFECYCLE_PUBLISH,
  saveMaintainedOrderTemplateHeaderApi,
  saveMaintainedOrderTemplateLinesApi,
} from '#/api/basic/order-template';
import { medicationCatalogInitApi } from '#/api/core/system/catalog';
import TcmHerbAddCard from '#/components/doctor-station/tcm-advice/components/tcm-herb-add-card.vue';
import TcmHerbCard from '#/components/doctor-station/tcm-advice/components/tcm-herb-card.vue';
import TcmPrescriptionSummary from '#/components/doctor-station/tcm-advice/components/tcm-prescription-summary.vue';
import SelectTable from '#/components/select-table.vue';
import { decimalMul } from '#/utils/format/decimal-util';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { getProductSelectColumns, useOrderTemplateLines } from './order-template-lines';
import {
  applyTcmHerbProductToDraftLine,
  calcTcmTemplateSingleDoseAmount,
  mapCatalogSelectRowToAdviceProductOption,
  mapDraftLineToHerbCardItem,
  tcmTemplateProductColumns,
  updateTcmTemplateLineDose,
} from './order-template-tcm-lines';

interface Props {
  formData?: Partial<OrderTemplateHeaderItem>;
  templateKindEnum?: string;
  initOptions?: OrderTemplateInitResponse;
}

defineOptions({ name: 'BasicOrderTemplateEditPanel' });

const props = withDefaults(defineProps<Props>(), {
  formData: () => ({}),
  templateKindEnum: 'GENERAL',
  initOptions: () => ({}),
});

const emit = defineEmits<{
  success: [orderTemplateId?: string];
}>();

const formModel = reactive({
  orderTemplateId: '',
  bindingEnum: '',
  sceneEnum: '',
  templateKindEnum: '',
  name: '',
  defaultChineseHerbsDoseQuantity: undefined as number | undefined,
  defaultSufferingFlag: false,
  /** 用法 → 行 routeCode（tcm_method_code） */
  tcmRouteCode: '',
  /** 频次 → 行 frequencyId */
  tcmFrequencyId: '',
  /** 天数 → 行 defaultDays */
  tcmDefaultDays: 1 as number | undefined,
});

const statusEnum = ref('');
const detailLoading = ref(false);
const lineQueryParams = ref({ pageNo: 1, pageSize: 50 });
const medicationCatalogInit = ref<MedicationCatalogInitResponse>({});
const frequencyOptionsRef = ref<Array<{ label: string; value: string }>>([]);

function convertMedicationInitOptions(key: keyof MedicationCatalogInitResponse) {
  const raw = medicationCatalogInit.value?.[key];
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw.map((item) => {
    const row = item as { info?: string; label?: string; name?: string; value?: number | string };
    return {
      value: String(row.value ?? ''),
      label: String(row.label ?? row.name ?? row.info ?? row.value ?? ''),
    };
  });
}

const methodCodeOptionsRef = computed(() => convertMedicationInitOptions('methodCodeOptions'));
const unitCodeOptionsRef = computed(() => convertMedicationInitOptions('unitCodeOptions'));

const isEditMode = computed(() => !!formModel.orderTemplateId);
const isTcmKind = computed(() => formModel.templateKindEnum === ORDER_TEMPLATE_KIND_TCM);
const initOptionsRef = computed(() => props.initOptions ?? {});
const tcmMethodCodeOptionsRef = computed(() => {
  const raw = initOptionsRef.value.tcmMethodCodeOptions ?? [];
  return raw.map((item) => ({
    value: String(item.value ?? ''),
    label: String(item.label ?? item.value ?? ''),
  }));
});

/** 维护页内嵌：模板属性默认展开 */
const showHeaderForm = ref(true);

const headerFormRef = ref<FormInstance>();

const headerRules = computed<FormRules>(() => {
  const rules: FormRules = {
    name: [{ required: true, message: '请填写模板名称', trigger: 'blur' }],
    sceneEnum: [{ required: true, message: '请选择适用场景', trigger: 'change' }],
  };
  if (isTcmKind.value) {
    rules.defaultChineseHerbsDoseQuantity = [
      { required: true, message: '中药组套须填写默认付数', trigger: 'change' },
    ];
  }
  return rules;
});

/** 已停用组套只读 */
const isReadonly = computed(() => statusEnum.value === 'RETIRED');

/** 收起态展示的模板属性摘要 */
const headerSummary = computed(() => {
  const name = formModel.name.trim() || '未填写';
  const binding = initOptionsRef.value.bindingOptions?.find(
    (item) => item.value === formModel.bindingEnum,
  )?.label;
  const scene = initOptionsRef.value.sceneOptions?.find(
    (item) => item.value === formModel.sceneEnum,
  )?.label;
  const parts = [`名称：${name}`];
  if (binding) {
    parts.push(`绑定：${binding}`);
  }
  if (scene) {
    parts.push(`场景：${scene}`);
  }
  return parts.join(' · ');
});

const linesTableRef = ref<InstanceType<typeof TableLayout> | null>(null);
const editingLineKey = ref<string | undefined>(undefined);
const selectedLineKeys = ref<Set<string>>(new Set());
const autofocusProductLineKey = ref<null | string>(null);

const {
  visibleLines,
  tableColumns,
  canGroupLines,
  isMedicationLineRow,
  isServiceLineRow,
  isDeviceLineRow,
  isGroupLeaderRow,
  getProductId,
  handleAddLine,
  handleRemoveLine,
  handleLineSelectionChange,
  handleAssignGroup,
  handleUngroup,
  syncGroupSharedFieldsFromLeader,
  searchProductPage,
  handleProductTableClose,
  handleProductIdChange,
  handleLineTotalUnitCodeChange,
  handleLineSingleDoseChange,
  handleLineDoseConvertRatioChange,
  handleLineFrequencyChange,
  getTotalUnitOptions,
  getPricingUnitOptions,
  validateAllLines,
  buildSubmitLines,
  setLinesFromApi,
  resetLinesForCreate,
  formatLineProductText,
  formatLineKindText,
  formatLineDoseText,
  formatLineRouteText,
  formatLineFrequencyText,
  formatLineDaysText,
  formatLineTotalText,
  formatLineServiceQuantityText,
  formatLineServiceExecutionCountText,
  formatLineDeviceQuantityText,
  formatLinePricingQuantityText,
  getProductGroupCellClass,
  lineKindOptions,
  handleLineKindChange,
} = useOrderTemplateLines({
  initOptions: () => props.initOptions ?? {},
  templateKindEnum: () => formModel.templateKindEnum || props.templateKindEnum,
  frequencyOptions: () => frequencyOptionsRef.value,
  methodCodeOptions: () => methodCodeOptionsRef.value,
  unitCodeOptions: () => unitCodeOptionsRef.value,
  editingLineKey: () => editingLineKey.value,
});

/** 中药组套可见药材行 */
const tcmVisibleLines = computed(() => visibleLines.value as TcmTemplateDraftLine[]);

/** 卡片展示模型 */
const tcmHerbCardItems = computed(() =>
  tcmVisibleLines.value.map((line) => mapDraftLineToHerbCardItem(line)),
);

const tcmHerbCount = computed(
  () =>
    tcmVisibleLines.value.filter((line) => String(line.medicationDefinitionId ?? '').trim()).length,
);

const tcmSingleDoseAmount = computed(() => calcTcmTemplateSingleDoseAmount(tcmVisibleLines.value));

const tcmTotalAmount = computed(() => {
  const doseQty = formModel.defaultChineseHerbsDoseQuantity || 1;
  return decimalMul(tcmSingleDoseAmount.value, doseQty);
});

/** 按 clientKey 查找草稿行 */
function findTcmDraftLine(clientLineKey: string): TcmTemplateDraftLine | undefined {
  return tcmVisibleLines.value.find((line) => line._clientKey === clientLineKey);
}

/** 从首行明细回填 TCM 用法/频次/天数（存于 cus_order_template_line） */
function loadTcmHeaderFieldsFromLines() {
  if (!isTcmKind.value) {
    return;
  }
  const first =
    tcmVisibleLines.value.find(
      (line) =>
        String(line.routeCode ?? '').trim() ||
        preserveApiIdForRequest(line.frequencyId) ||
        line.defaultDays !== undefined,
    ) ?? tcmVisibleLines.value[0];
  if (!first) {
    formModel.tcmRouteCode = '';
    formModel.tcmFrequencyId = '';
    formModel.tcmDefaultDays = 1;
    return;
  }
  formModel.tcmRouteCode = String(first.routeCode ?? '').trim();
  formModel.tcmFrequencyId = preserveApiIdForRequest(first.frequencyId) ?? '';
  formModel.tcmDefaultDays = first.defaultDays ?? 1;
}

/** 保存前将方头用法/频次/天数写入全部药材行 */
function syncTcmHeaderFieldsToLines() {
  if (!isTcmKind.value) {
    return;
  }
  const routeCode = String(formModel.tcmRouteCode ?? '').trim() || undefined;
  const frequencyId = preserveApiIdForRequest(formModel.tcmFrequencyId) || undefined;
  const frequencyText = frequencyOptionsRef.value.find((item) => item.value === frequencyId)?.label;
  const defaultDays = formModel.tcmDefaultDays;
  for (const line of tcmVisibleLines.value) {
    line.routeCode = routeCode;
    line.frequencyId = frequencyId;
    line.frequencyText = frequencyText;
    if (defaultDays !== undefined && defaultDays !== null) {
      line.defaultDays = defaultDays;
    }
  }
}

/** 分页检索药材目录（卡片选药用） */
async function queryTcmHerbProductOptions(params: {
  keyword?: string;
  pageNo: number;
  pageSize: number;
}) {
  const probeLine: DraftLine = {
    _clientKey: 'tcm-catalog-probe',
    lineKindEnum: 'MEDICATION',
  };
  const res = await searchProductPage(probeLine, params);
  return {
    content: res.content.map((row) => mapCatalogSelectRowToAdviceProductOption(row)),
    totalElements: res.totalCount,
  };
}

/** 新增药材卡片并进入编辑 */
function handleAddTcmHerbCard() {
  handleAddLine();
  void nextTick(() => {
    const lastLine = tcmVisibleLines.value[tcmVisibleLines.value.length - 1];
    if (!lastLine) {
      return;
    }
    lastLine.routeCode = String(formModel.tcmRouteCode ?? '').trim() || undefined;
    const frequencyId = preserveApiIdForRequest(formModel.tcmFrequencyId) || undefined;
    lastLine.frequencyId = frequencyId;
    lastLine.frequencyText = frequencyOptionsRef.value.find(
      (item) => item.value === frequencyId,
    )?.label;
    if (formModel.tcmDefaultDays !== undefined && formModel.tcmDefaultDays !== null) {
      lastLine.defaultDays = formModel.tcmDefaultDays;
    }
    editingLineKey.value = lastLine._clientKey;
    autofocusProductLineKey.value = lastLine._clientKey;
  });
}

function handleClearTcmHerbSelection() {
  selectedLineKeys.value = new Set();
}

function handleTcmHerbSelect(clientLineKey: string) {
  if (isReadonly.value || editingLineKey.value === clientLineKey) {
    return;
  }
  const next = new Set(selectedLineKeys.value);
  if (next.has(clientLineKey)) {
    next.delete(clientLineKey);
  } else {
    next.add(clientLineKey);
  }
  selectedLineKeys.value = next;
}

function isTcmHerbSelected(clientLineKey: string) {
  return selectedLineKeys.value.has(clientLineKey);
}

function handleTcmHerbEditStart(clientLineKey: string) {
  if (isReadonly.value) {
    return;
  }
  editingLineKey.value = clientLineKey;
}

function handleTcmHerbEditEnd(clientLineKey: string) {
  if (editingLineKey.value !== clientLineKey) {
    return;
  }
  editingLineKey.value = undefined;
  if (autofocusProductLineKey.value === clientLineKey) {
    autofocusProductLineKey.value = null;
  }
  const line = findTcmDraftLine(clientLineKey);
  if (line && !String(line.medicationDefinitionId ?? '').trim()) {
    handleRemoveLine(line);
  }
}

function handleTcmHerbAutofocusConsumed(clientLineKey: string) {
  if (autofocusProductLineKey.value === clientLineKey) {
    autofocusProductLineKey.value = null;
  }
}

function handleTcmHerbProductSelect(clientLineKey: string, product: AdviceProductOption) {
  const line = findTcmDraftLine(clientLineKey);
  if (!line || isReadonly.value) {
    return;
  }
  applyTcmHerbProductToDraftLine(line, product);
}

function handleTcmHerbDoseChange(clientLineKey: string, dose: number) {
  const line = findTcmDraftLine(clientLineKey);
  if (!line || isReadonly.value) {
    return;
  }
  updateTcmTemplateLineDose(line, dose);
}

function handleRemoveTcmHerb(clientLineKey: string) {
  const line = findTcmDraftLine(clientLineKey);
  if (!line || isReadonly.value) {
    return;
  }
  handleRemoveLine(line);
  if (editingLineKey.value === clientLineKey) {
    editingLineKey.value = undefined;
  }
  const next = new Set(selectedLineKeys.value);
  next.delete(clientLineKey);
  selectedLineKeys.value = next;
}

const lineRowElMap = new Map<string, HTMLElement>();
let removeLineEditBlurListener: (() => void) | undefined;

/** 当前行是否处于行内编辑态 */
function isLineEditing(row: DraftLine): boolean {
  return editingLineKey.value === row._clientKey;
}

/** 绑定行外点击监听：失焦后切回文本展示 */
function bindLineEditBlurListener() {
  unbindLineEditBlurListener();
  const onMouseDown = (ev: MouseEvent) => handleLinesDocumentMouseDown(ev);
  document.addEventListener('mousedown', onMouseDown, true);
  removeLineEditBlurListener = () => document.removeEventListener('mousedown', onMouseDown, true);
}

/** 卸载行外点击监听 */
function unbindLineEditBlurListener() {
  removeLineEditBlurListener?.();
  removeLineEditBlurListener = undefined;
}

/** 退出当前行编辑态（保留已填数据） */
function clearLineEditing() {
  editingLineKey.value = undefined;
}

/** 进入指定行编辑态 */
function focusLineEditing(row: DraftLine | undefined) {
  editingLineKey.value = row?._clientKey;
}

/** 点击表格行进入编辑（跳过勾选列与操作列） */
function handleLineRowClick(
  row: Record<string, unknown>,
  column: undefined | { field?: string; type?: string },
  event: Event,
) {
  if (isReadonly.value) {
    return;
  }
  if (column?.type === 'checkbox') {
    return;
  }
  const target = event.target as HTMLElement | null;
  if (target?.closest('.el-button, .el-checkbox, a')) {
    return;
  }
  const draftRow = row as unknown as DraftLine;
  const tr = target?.closest?.('tr.vxe-body--row') as HTMLElement | null;
  if (tr) {
    lineRowElMap.set(draftRow._clientKey, tr);
  }
  focusLineEditing(draftRow);
}

/** 点击行外区域时退出编辑 */
function handleLinesDocumentMouseDown(ev: MouseEvent) {
  const key = editingLineKey.value;
  if (!key || isReadonly.value) {
    return;
  }
  // 中药组套：编辑态由 TcmHerbCard 自行 emit edit-end，父级不参与失焦
  if (isTcmKind.value) {
    return;
  }
  const target = ev.target as HTMLElement | null;
  if (
    target?.closest(
      [
        '.el-popper',
        '.el-select__popper',
        '.el-select-dropdown',
        '.el-picker-panel',
        '.el-dialog',
        '.el-overlay',
        '.el-message-box',
        '.vxe-pulldown--panel',
        '.select-table-dropdown-grid',
        '[data-popper-placement]',
        '.order-template-header-form',
      ].join(', '),
    )
  ) {
    return;
  }
  const rowEl = lineRowElMap.get(key);
  if (rowEl && target && rowEl.contains(target)) {
    return;
  }
  // 新增行尚未注册 tr 时，点击表格内仍视为当前行编辑
  if (!rowEl && target?.closest('.order-template-lines-table')) {
    return;
  }
  clearLineEditing();
}

/** 明细表行 class：高亮当前编辑行 */
function lineRowClassName({ row }: { row: Record<string, unknown>; rowIndex: number }) {
  return isLineEditing(row as unknown as DraftLine) ? 'order-template-line-row--editing' : '';
}

/** 新增行并自动进入编辑 */
function onAddLineClick() {
  handleAddLine();
  void nextTick(() => {
    const lastLine = visibleLines.value[visibleLines.value.length - 1];
    focusLineEditing(lastLine);
  });
}

/** 清空明细表勾选 */
function clearLinesTableSelection() {
  linesTableRef.value?.gridApi?.clearCheckboxRow?.();
}

/** 并组后清空表格勾选 */
function onAssignGroupClick() {
  if (handleAssignGroup()) {
    clearLinesTableSelection();
  }
}

/** 拆组后清空表格勾选 */
function onUngroupClick() {
  if (handleUngroup()) {
    clearLinesTableSelection();
  }
}

/** 成组首行修改用法/天数时同步同组行 */
function onGroupSharedFieldChange(row: DraftLine) {
  syncGroupSharedFieldsFromLeader(row);
}

const saving = ref(false);

/** 回填头表字段 */
function fillHeaderForm() {
  const row = props.formData ?? {};
  formModel.orderTemplateId = row.orderTemplateId ? String(row.orderTemplateId) : '';
  formModel.bindingEnum = row.bindingEnum ?? '';
  formModel.sceneEnum = row.sceneEnum ?? '';
  formModel.templateKindEnum = row.templateKindEnum ?? props.templateKindEnum;
  formModel.name = row.name ?? '';
  formModel.defaultChineseHerbsDoseQuantity = row.defaultChineseHerbsDoseQuantity;
  formModel.defaultSufferingFlag = row.defaultSufferingFlag ?? false;
  formModel.tcmRouteCode = '';
  formModel.tcmFrequencyId = '';
  formModel.tcmDefaultDays = 1;
  statusEnum.value = row.statusEnum ?? '';
}

/** 重置头表字段 */
function resetHeaderForm() {
  formModel.orderTemplateId = '';
  formModel.bindingEnum = '';
  formModel.sceneEnum = '';
  formModel.templateKindEnum = props.templateKindEnum;
  formModel.name = '';
  formModel.defaultChineseHerbsDoseQuantity = undefined;
  formModel.defaultSufferingFlag = false;
  formModel.tcmRouteCode = '';
  formModel.tcmFrequencyId = '';
  formModel.tcmDefaultDays = 1;
  statusEnum.value = '';
  void nextTick(() => headerFormRef.value?.clearValidate());
}

/** 加载药品目录 init（给药途径/单位字典，对齐门诊医生站） */
async function loadMedicationCatalogInit() {
  try {
    const res = await medicationCatalogInitApi();
    medicationCatalogInit.value = res?.data ?? {};
  } catch {
    medicationCatalogInit.value = {};
  }
}

/** 加载启用频次下拉（维护药品行频次） */
async function loadFrequencyOptions() {
  try {
    const res = await pageFrequencyApi({ pageNo: 1, pageSize: 500 });
    const data = (
      res as {
        data?: {
          content?: Array<{
            activeFlag?: number;
            id?: number | string;
            name?: string;
            rateCode?: string;
          }>;
        };
      }
    )?.data;
    const list = data?.content ?? [];
    frequencyOptionsRef.value = list
      .filter((item) => Number(item.activeFlag) === 1)
      .map((item) => {
        const value = preserveApiIdForRequest(item.id) ?? '';
        const label = String(item.name ?? item.rateCode ?? '').trim();
        return { value, label };
      })
      .filter((item) => item.value && item.label);
  } catch {
    frequencyOptionsRef.value = [];
  }
}

/** 打开弹窗时加载头表与明细 */
async function loadFormData() {
  await Promise.all([loadMedicationCatalogInit(), loadFrequencyOptions()]);
  const orderTemplateId = preserveApiIdForRequest(props.formData?.orderTemplateId);
  if (orderTemplateId) {
    fillHeaderForm();
    showHeaderForm.value = true;
    detailLoading.value = true;
    try {
      const detail = await getOrderTemplateDetailApi({ orderTemplateId });
      if (detail.header) {
        formModel.orderTemplateId = detail.header.orderTemplateId
          ? String(detail.header.orderTemplateId)
          : orderTemplateId;
        formModel.bindingEnum = detail.header.bindingEnum ?? formModel.bindingEnum;
        formModel.sceneEnum = detail.header.sceneEnum ?? formModel.sceneEnum;
        formModel.templateKindEnum = detail.header.templateKindEnum ?? formModel.templateKindEnum;
        formModel.name = detail.header.name ?? formModel.name;
        formModel.defaultChineseHerbsDoseQuantity = detail.header.defaultChineseHerbsDoseQuantity;
        formModel.defaultSufferingFlag = detail.header.defaultSufferingFlag ?? false;
        statusEnum.value = detail.header.statusEnum ?? statusEnum.value;
      }
      await setLinesFromApi(detail.lines);
      loadTcmHeaderFieldsFromLines();
      clearLineEditing();
      selectedLineKeys.value = new Set();
      autofocusProductLineKey.value = null;
      void nextTick(() => headerFormRef.value?.clearValidate());
    } catch {
      ElMessage.error('加载组套详情失败，请稍后重试');
    } finally {
      detailLoading.value = false;
    }
    return;
  }
  resetHeaderForm();
  resetLinesForCreate();
  showHeaderForm.value = true;
  void nextTick(() => {
    if (isTcmKind.value) {
      const firstLine = tcmVisibleLines.value[0];
      if (firstLine) {
        editingLineKey.value = firstLine._clientKey;
        autofocusProductLineKey.value = firstLine._clientKey;
      }
      return;
    }
    focusLineEditing(visibleLines.value[0]);
  });
}

/** 校验头表（失败时展开模板属性区；控件 validate-event=false，仅此处主动 validate） */
async function validateHeader(): Promise<boolean> {
  showHeaderForm.value = true;
  await nextTick();
  if (!headerFormRef.value) {
    return true;
  }
  try {
    await headerFormRef.value.validate();
    return true;
  } catch {
    return false;
  }
}

/** 保存后自动发布（已发布/已停用不再重复发布） */
async function publishOrderTemplateAfterSave(orderTemplateId: string): Promise<boolean> {
  if (statusEnum.value === 'ACTIVE' || statusEnum.value === 'RETIRED') {
    return false;
  }
  await changeOrderTemplateLifecycleApi({
    orderTemplateId,
    lifecycleActionCode: ORDER_TEMPLATE_LIFECYCLE_PUBLISH,
  });
  statusEnum.value = 'ACTIVE';
  return true;
}

/** 一次保存：先头表，再明细行，最后自动发布；bindingEnum 由右上角存 XX 按钮传入 */
async function handleSubmit(bindingEnum?: string) {
  if (isReadonly.value) {
    ElMessage.warning('已停用的组套不可编辑');
    return;
  }
  const targetBinding = String(bindingEnum ?? formModel.bindingEnum ?? '').trim();
  if (!targetBinding) {
    ElMessage.warning('请选择保存类型');
    return;
  }
  const currentBinding = String(formModel.bindingEnum ?? '').trim();
  const currentId = preserveApiIdForRequest(formModel.orderTemplateId);
  // 绑定类型变更时按新建保存，避免误改归属
  if (currentId && currentBinding && currentBinding !== targetBinding) {
    formModel.orderTemplateId = '';
    statusEnum.value = '';
  }
  formModel.bindingEnum = targetBinding;

  if (!(await validateHeader()) || !validateAllLines()) {
    return;
  }
  syncTcmHeaderFieldsToLines();

  const headerPayload: SaveOrderTemplateHeaderParams = {
    orderTemplateId: preserveApiIdForRequest(formModel.orderTemplateId),
    bindingEnum: formModel.bindingEnum,
    sceneEnum: formModel.sceneEnum,
    templateKindEnum: formModel.templateKindEnum || props.templateKindEnum,
    name: formModel.name.trim(),
    sortWeight: 0,
    defaultChineseHerbsDoseQuantity: isTcmKind.value
      ? formModel.defaultChineseHerbsDoseQuantity
      : undefined,
    defaultSufferingFlag: isTcmKind.value ? formModel.defaultSufferingFlag : undefined,
  };

  saving.value = true;
  try {
    const headerRes = await saveMaintainedOrderTemplateHeaderApi(headerPayload);
    const orderTemplateId =
      preserveApiIdForRequest(headerRes?.orderTemplateId) ??
      preserveApiIdForRequest(formModel.orderTemplateId);
    if (!orderTemplateId) {
      ElMessage.error('保存失败：未返回组套 ID');
      return;
    }
    formModel.orderTemplateId = orderTemplateId;
    await saveMaintainedOrderTemplateLinesApi({
      orderTemplateId,
      lines: buildSubmitLines(),
    });
    const published = await publishOrderTemplateAfterSave(orderTemplateId);
    ElMessage.success(published ? '保存并发布成功' : '保存成功');
    emit('success', orderTemplateId);
  } catch {
    ElMessage.error('保存失败，请稍后重试');
  } finally {
    saving.value = false;
  }
}

/** 重置头表与明细，供父级「清空」调用 */
function resetPanelContent() {
  resetHeaderForm();
  resetLinesForCreate();
  clearLineEditing();
  selectedLineKeys.value = new Set();
  autofocusProductLineKey.value = null;
  showHeaderForm.value = true;
}

defineExpose({
  submit: handleSubmit,
  clear: resetPanelContent,
});

watch(
  () => [
    preserveApiIdForRequest(props.formData?.orderTemplateId),
    props.templateKindEnum,
    props.formData?.bindingEnum,
  ],
  () => {
    void loadFormData();
  },
);

onMounted(() => {
  bindLineEditBlurListener();
  void loadFormData();
});

onUnmounted(() => {
  unbindLineEditBlurListener();
  clearLineEditing();
  selectedLineKeys.value = new Set();
  autofocusProductLineKey.value = null;
  lineRowElMap.clear();
});
</script>

<template>
  <div class="order-template-edit-panel flex min-h-0 flex-1 flex-col gap-3 overflow-hidden">
    <div class="order-template-edit-panel__body flex min-h-0 flex-1 flex-col gap-3 overflow-hidden">
      <section
        class="shrink-0"
        :class="
          isEditMode
            ? 'order-2 border-t border-[var(--el-border-color-lighter)] pt-3'
            : 'order-1 border-b border-[var(--el-border-color-lighter)] pb-3'
        "
      >
        <div class="mb-2 flex items-center justify-between gap-2">
          <span
            class="text-sm"
            :class="
              isEditMode
                ? 'text-[var(--el-text-color-secondary)]'
                : 'font-medium text-[var(--el-text-color-primary)]'
            "
          >
            模板属性
          </span>
          <ElButton
            v-if="isEditMode"
            link
            type="primary"
            :disabled="isReadonly"
            @click="showHeaderForm = !showHeaderForm"
          >
            {{ showHeaderForm ? '收起' : '展开编辑' }}
          </ElButton>
        </div>
        <p
          v-if="isEditMode && !showHeaderForm"
          class="m-0 text-sm text-[var(--el-text-color-regular)]"
        >
          {{ headerSummary }}
        </p>
        <ElForm
          v-if="!isEditMode || showHeaderForm"
          ref="headerFormRef"
          :model="formModel"
          :rules="headerRules"
          :validate-on-rule-change="false"
          label-width="88px"
          :disabled="isReadonly"
          class="order-template-header-form"
          :class="{ 'order-template-header-form--tcm': isTcmKind }"
        >
          <div class="order-template-header-form__row">
            <ElFormItem label="名称" prop="name">
              <ElInput
                v-model="formModel.name"
                :validate-event="false"
                placeholder="保存时必填"
                maxlength="128"
              />
            </ElFormItem>
            <ElFormItem label="适用场景" prop="sceneEnum">
              <ElSelect
                v-model="formModel.sceneEnum"
                :validate-event="false"
                placeholder="请选择"
                class="w-full"
              >
                <ElOption
                  v-for="item in initOptionsRef.sceneOptions ?? []"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem v-if="isTcmKind" label="默认付数" prop="defaultChineseHerbsDoseQuantity">
              <ElInputNumber
                v-model="formModel.defaultChineseHerbsDoseQuantity"
                :validate-event="false"
                class="!w-full"
                :min="1"
                :max="99"
                :controls="false"
              />
            </ElFormItem>
          </div>
          <div v-if="isTcmKind" class="order-template-header-form__row">
            <ElFormItem label="默认代煎" class="order-template-header-form__checkbox">
              <ElCheckbox v-model="formModel.defaultSufferingFlag">代煎</ElCheckbox>
            </ElFormItem>
            <ElFormItem label="用法">
              <ElSelect
                v-model="formModel.tcmRouteCode"
                filterable
                clearable
                placeholder="请选择用法"
                class="w-full"
              >
                <ElOption
                  v-for="item in tcmMethodCodeOptionsRef"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="频次">
              <ElSelect
                v-model="formModel.tcmFrequencyId"
                filterable
                clearable
                placeholder="请选择频次"
                class="w-full"
              >
                <ElOption
                  v-for="item in frequencyOptionsRef"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="天数">
              <ElInputNumber
                v-model="formModel.tcmDefaultDays"
                class="!w-full"
                :min="1"
                :max="99"
                :controls="false"
              />
            </ElFormItem>
          </div>
        </ElForm>
      </section>

      <section class="flex min-h-0 flex-col" :class="isEditMode ? 'order-1 flex-1' : 'order-2'">
        <template v-if="isTcmKind">
          <p v-if="isReadonly" class="m-0 mb-2 text-sm text-[var(--el-text-color-regular)]">
            已停用的组套不可编辑。
          </p>

          <div
            v-loading="detailLoading || saving"
            class="tcm-template-content border-border-base bg-background-card flex min-h-[360px] flex-1 flex-col overflow-hidden rounded border"
          >
            <ElScrollbar class="tcm-herb-panel__scroll min-h-0 flex-1">
              <div class="tcm-herb-grid p-3" @click.self="handleClearTcmHerbSelection">
                <TcmHerbCard
                  v-for="cardItem in tcmHerbCardItems"
                  :key="cardItem.clientLineKey"
                  :item="cardItem"
                  :selected="isTcmHerbSelected(cardItem.clientLineKey)"
                  :editing="editingLineKey === cardItem.clientLineKey"
                  :autofocus-product="autofocusProductLineKey === cardItem.clientLineKey"
                  :blur-ignore-selectors="['.order-template-header-form']"
                  :product-columns="tcmTemplateProductColumns"
                  :query-product-options="queryTcmHerbProductOptions"
                  :readonly="isReadonly"
                  @select="handleTcmHerbSelect(cardItem.clientLineKey)"
                  @edit-start="handleTcmHerbEditStart(cardItem.clientLineKey)"
                  @edit-end="handleTcmHerbEditEnd(cardItem.clientLineKey)"
                  @autofocus-product-consumed="
                    handleTcmHerbAutofocusConsumed(cardItem.clientLineKey)
                  "
                  @remove="handleRemoveTcmHerb(cardItem.clientLineKey)"
                  @product-select="handleTcmHerbProductSelect(cardItem.clientLineKey, $event)"
                  @update-dose="handleTcmHerbDoseChange(cardItem.clientLineKey, $event)"
                />
                <TcmHerbAddCard :disabled="isReadonly" @add="handleAddTcmHerbCard" />
              </div>
            </ElScrollbar>

            <TcmPrescriptionSummary
              :herb-count="tcmHerbCount"
              :single-dose-amount="tcmSingleDoseAmount"
              :chinese-herbs-dose-quantity="formModel.defaultChineseHerbsDoseQuantity || 1"
              :total-amount="tcmTotalAmount"
            />
          </div>
        </template>

        <template v-else>
          <div class="mb-2 flex items-center gap-2">
            <ElButton type="primary" :disabled="isReadonly" @click="onAddLineClick">
              新增行
            </ElButton>
            <template v-if="canGroupLines">
              <ElButton plain :disabled="isReadonly" @click="onAssignGroupClick">并组</ElButton>
              <ElButton plain :disabled="isReadonly" @click="onUngroupClick">拆组</ElButton>
            </template>
          </div>
          <p v-if="isReadonly" class="m-0 mb-2 text-sm text-[var(--el-text-color-regular)]">
            已停用的组套不可编辑。
          </p>

          <TableLayout
            ref="linesTableRef"
            class="order-template-lines-table min-h-[360px] flex-1"
            :table-data="visibleLines"
            :loading="detailLoading || saving"
            :total="visibleLines.length"
            :query-params="lineQueryParams"
            :form-items="[]"
            :table-columns="tableColumns"
            :show-top-query="false"
            :show-pagination="false"
            :row-class-name="lineRowClassName"
            row-key="_clientKey"
            @selection-change="handleLineSelectionChange"
            @row-click="handleLineRowClick"
          >
            <template #toolbar-actions></template>

            <template #lineKind="{ row }">
              <div v-if="isLineEditing(row as DraftLine)" class="line-edit-cell">
                <ElSelect
                  v-model="row.lineKindEnum"
                  :disabled="isReadonly"
                  placeholder="类型"
                  class="w-full"
                  @change="handleLineKindChange(row as DraftLine)"
                >
                  <ElOption
                    v-for="item in lineKindOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
              </div>
              <span v-else class="order-template-line-display-text">
                {{ formatLineKindText(row as DraftLine) || '—' }}
              </span>
            </template>

            <template #product="{ row }">
              <div
                class="order-template-product-cell"
                :class="getProductGroupCellClass(row as DraftLine)"
              >
                <template v-if="isLineEditing(row as DraftLine)">
                  <div class="line-edit-cell">
                    <SelectTable
                      :model-value="getProductId(row as DraftLine)"
                      :selected-label="row._productLabel || row.productName || ''"
                      :disabled="isReadonly || !row.lineKindEnum"
                      :remote-method="(params) => searchProductPage(row as DraftLine, params)"
                      :columns="getProductSelectColumns(row.lineKindEnum)"
                      value-key="value"
                      label-key="label"
                      placeholder="搜索项目"
                      :panel-width="720"
                      :before-close="
                        (item, val) => handleProductTableClose(row as DraftLine, item, String(val))
                      "
                      @update:model-value="(val) => handleProductIdChange(row as DraftLine, val)"
                    />
                  </div>
                </template>
                <span v-else class="order-template-line-display-text">
                  {{ formatLineProductText(row as DraftLine) || '—' }}
                </span>
              </div>
            </template>

            <template #dose="{ row }">
              <template v-if="isMedicationLineRow(row as DraftLine)">
                <div v-if="isLineEditing(row as DraftLine)" class="line-edit-cell">
                  <div class="dose-convert-wrap">
                    <div class="dose-num-with-unit">
                      <ElInputNumber
                        class="dose-qty-input"
                        :class="
                          getProductId(row as DraftLine) ? 'order-template-single-dose-primary' : ''
                        "
                        :model-value="row._singleDose ?? undefined"
                        :disabled="isReadonly"
                        :min="0"
                        :precision="2"
                        :controls="false"
                        @update:model-value="
                          (val) => handleLineSingleDoseChange(row as DraftLine, val)
                        "
                      />
                      <span class="dose-unit-tail" :title="row._doseMainUnit || ''">{{
                        row._doseMainUnit || '—'
                      }}</span>
                    </div>
                    <span class="equals-sign">=</span>
                    <div class="dose-num-with-unit">
                      <ElInputNumber
                        class="dose-qty-input"
                        :model-value="row._doseConvertRatio ?? undefined"
                        :disabled="isReadonly"
                        :min="0"
                        :precision="2"
                        :controls="false"
                        @update:model-value="
                          (val) => handleLineDoseConvertRatioChange(row as DraftLine, val)
                        "
                      />
                      <span class="dose-unit-tail" :title="row._doseSubUnit || ''">{{
                        row._doseSubUnit || '—'
                      }}</span>
                    </div>
                  </div>
                </div>
                <span v-else class="order-template-line-display-text">
                  {{ formatLineDoseText(row as DraftLine) }}
                </span>
              </template>
              <template v-else-if="isServiceLineRow(row as DraftLine)">
                <div v-if="isLineEditing(row as DraftLine)" class="line-edit-cell">
                  <div class="dose-num-with-unit">
                    <ElInputNumber
                      v-model="row.defaultQuantity"
                      class="dose-qty-input order-template-single-dose-primary"
                      :disabled="isReadonly"
                      :min="0"
                      :precision="2"
                      :controls="false"
                    />
                    <span
                      class="dose-unit-tail"
                      :title="
                        getPricingUnitOptions(row as DraftLine)[0]?.label ||
                        row.defaultUnitName ||
                        row.defaultUnitCode ||
                        ''
                      "
                    >
                      {{
                        getPricingUnitOptions(row as DraftLine)[0]?.label ||
                        row.defaultUnitName ||
                        row.defaultUnitCode ||
                        '—'
                      }}
                    </span>
                  </div>
                </div>
                <span v-else class="order-template-line-display-text">
                  {{ formatLineServiceQuantityText(row as DraftLine) }}
                </span>
              </template>
              <template v-else-if="isDeviceLineRow(row as DraftLine)">
                <div v-if="isLineEditing(row as DraftLine)" class="line-edit-cell">
                  <div class="total-amount-wrap device-qty-wrap">
                    <ElInputNumber
                      v-model="row.defaultQuantity"
                      class="dose-qty-input"
                      :disabled="isReadonly"
                      :min="0"
                      :precision="2"
                      :controls="false"
                    />
                    <ElSelect
                      v-if="getPricingUnitOptions(row as DraftLine).length > 0"
                      :model-value="row.defaultUnitCode"
                      class="total-unit-select"
                      disabled
                      placeholder="单位"
                    >
                      <ElOption
                        v-for="item in getPricingUnitOptions(row as DraftLine)"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      />
                    </ElSelect>
                    <span v-else class="dose-unit-tail">{{
                      row.defaultUnitName || row.defaultUnitCode || '—'
                    }}</span>
                  </div>
                </div>
                <span v-else class="order-template-line-display-text">
                  {{ formatLineDeviceQuantityText(row as DraftLine) }}
                </span>
              </template>
              <span v-else class="text-[var(--el-text-color-placeholder)]">—</span>
            </template>

            <template #routeCode="{ row }">
              <template v-if="isMedicationLineRow(row as DraftLine)">
                <div
                  v-if="isLineEditing(row as DraftLine) && isGroupLeaderRow(row as DraftLine)"
                  class="line-edit-cell"
                >
                  <ElSelect
                    v-model="row.routeCode"
                    :disabled="isReadonly"
                    filterable
                    clearable
                    placeholder="用法"
                    class="w-full"
                    @change="onGroupSharedFieldChange(row as DraftLine)"
                  >
                    <ElOption
                      v-for="item in methodCodeOptionsRef"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </ElSelect>
                </div>
                <span v-else class="order-template-line-display-text">
                  {{ formatLineRouteText(row as DraftLine) }}
                </span>
              </template>
              <template v-else-if="isServiceLineRow(row as DraftLine)">
                <div v-if="isLineEditing(row as DraftLine)" class="line-edit-cell">
                  <ElInputNumber
                    v-model="row.defaultDays"
                    :disabled="isReadonly"
                    :min="1"
                    :precision="0"
                    :controls="false"
                    class="!w-full"
                  />
                </div>
                <span v-else class="order-template-line-display-text">
                  {{ formatLineServiceExecutionCountText(row as DraftLine) }}
                </span>
              </template>
              <span v-else class="text-[var(--el-text-color-placeholder)]">—</span>
            </template>

            <template #frequencyId="{ row }">
              <template v-if="isMedicationLineRow(row as DraftLine)">
                <div
                  v-if="isLineEditing(row as DraftLine) && isGroupLeaderRow(row as DraftLine)"
                  class="line-edit-cell"
                >
                  <ElSelect
                    :model-value="preserveApiIdForRequest(row.frequencyId) || ''"
                    :disabled="isReadonly"
                    filterable
                    clearable
                    placeholder="频次"
                    class="w-full"
                    @update:model-value="
                      (val) => handleLineFrequencyChange(row as DraftLine, String(val || ''))
                    "
                  >
                    <ElOption
                      v-for="item in frequencyOptionsRef"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </ElSelect>
                </div>
                <span v-else class="order-template-line-display-text">
                  {{ formatLineFrequencyText(row as DraftLine) }}
                </span>
              </template>
              <span v-else class="text-[var(--el-text-color-placeholder)]">—</span>
            </template>

            <template #defaultDays="{ row }">
              <template v-if="isMedicationLineRow(row as DraftLine)">
                <div
                  v-if="isLineEditing(row as DraftLine) && isGroupLeaderRow(row as DraftLine)"
                  class="line-edit-cell"
                >
                  <ElInputNumber
                    v-model="row.defaultDays"
                    :disabled="isReadonly"
                    :min="1"
                    :controls="false"
                    class="!w-full"
                    @change="onGroupSharedFieldChange(row as DraftLine)"
                  />
                </div>
                <span v-else class="order-template-line-display-text">
                  {{ formatLineDaysText(row as DraftLine) }}
                </span>
              </template>
              <span v-else class="text-[var(--el-text-color-placeholder)]">—</span>
            </template>

            <template #totalAmount="{ row }">
              <template v-if="isMedicationLineRow(row as DraftLine)">
                <div v-if="isLineEditing(row as DraftLine)" class="line-edit-cell">
                  <div class="total-amount-wrap">
                    <ElInputNumber
                      v-model="row.defaultQuantity"
                      class="dose-qty-input"
                      :disabled="isReadonly"
                      :min="0"
                      :precision="2"
                      :controls="false"
                    />
                    <ElSelect
                      :model-value="row.defaultUnitCode"
                      class="total-unit-select"
                      :disabled="isReadonly || getTotalUnitOptions(row as DraftLine).length === 0"
                      placeholder="单位"
                      @update:model-value="
                        (val) => handleLineTotalUnitCodeChange(row as DraftLine, val)
                      "
                    >
                      <ElOption
                        v-for="item in getTotalUnitOptions(row as DraftLine)"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      />
                    </ElSelect>
                  </div>
                </div>
                <span v-else class="order-template-line-display-text">
                  {{ formatLineTotalText(row as DraftLine) }}
                </span>
              </template>
              <span v-else class="text-[var(--el-text-color-placeholder)]">—</span>
            </template>

            <template #serviceQuantity="{ row }">
              <template v-if="isServiceLineRow(row as DraftLine)">
                <div v-if="isLineEditing(row as DraftLine)" class="line-edit-cell">
                  <div class="dose-num-with-unit">
                    <ElInputNumber
                      v-model="row.defaultQuantity"
                      class="dose-qty-input order-template-single-dose-primary"
                      :disabled="isReadonly"
                      :min="0"
                      :precision="2"
                      :controls="false"
                    />
                    <span
                      class="dose-unit-tail"
                      :title="
                        getPricingUnitOptions(row as DraftLine)[0]?.label ||
                        row.defaultUnitName ||
                        row.defaultUnitCode ||
                        ''
                      "
                    >
                      {{
                        getPricingUnitOptions(row as DraftLine)[0]?.label ||
                        row.defaultUnitName ||
                        row.defaultUnitCode ||
                        '—'
                      }}
                    </span>
                  </div>
                </div>
                <span v-else class="order-template-line-display-text">
                  {{ formatLineServiceQuantityText(row as DraftLine) }}
                </span>
              </template>
              <span v-else class="text-[var(--el-text-color-placeholder)]">—</span>
            </template>

            <template #serviceExecutionCount="{ row }">
              <template v-if="isServiceLineRow(row as DraftLine)">
                <div v-if="isLineEditing(row as DraftLine)" class="line-edit-cell">
                  <ElInputNumber
                    v-model="row.defaultDays"
                    :disabled="isReadonly"
                    :min="1"
                    :precision="0"
                    :controls="false"
                    class="!w-full"
                  />
                </div>
                <span v-else class="order-template-line-display-text">
                  {{ formatLineServiceExecutionCountText(row as DraftLine) }}
                </span>
              </template>
              <span v-else class="text-[var(--el-text-color-placeholder)]">—</span>
            </template>

            <template #deviceQuantity="{ row }">
              <template v-if="isDeviceLineRow(row as DraftLine)">
                <div v-if="isLineEditing(row as DraftLine)" class="line-edit-cell">
                  <div class="total-amount-wrap device-qty-wrap">
                    <ElInputNumber
                      v-model="row.defaultQuantity"
                      class="dose-qty-input"
                      :disabled="isReadonly"
                      :min="0"
                      :precision="2"
                      :controls="false"
                    />
                    <ElSelect
                      v-if="getPricingUnitOptions(row as DraftLine).length > 0"
                      :model-value="row.defaultUnitCode"
                      class="total-unit-select"
                      disabled
                      placeholder="单位"
                    >
                      <ElOption
                        v-for="item in getPricingUnitOptions(row as DraftLine)"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      />
                    </ElSelect>
                    <span v-else class="dose-unit-tail">{{
                      row.defaultUnitName || row.defaultUnitCode || '—'
                    }}</span>
                  </div>
                </div>
                <span v-else class="order-template-line-display-text">
                  {{ formatLineDeviceQuantityText(row as DraftLine) }}
                </span>
              </template>
              <span v-else class="text-[var(--el-text-color-placeholder)]">—</span>
            </template>

            <template #quantityWithUnit="{ row }">
              <div v-if="isLineEditing(row as DraftLine)" class="line-edit-cell">
                <div class="total-amount-wrap">
                  <ElInputNumber
                    v-model="row.defaultQuantity"
                    class="dose-qty-input"
                    :disabled="isReadonly"
                    :min="0"
                    :precision="2"
                    :controls="false"
                  />
                  <ElSelect
                    :model-value="row.defaultUnitCode"
                    class="total-unit-select"
                    disabled
                    placeholder="单位"
                  >
                    <ElOption
                      v-for="item in getPricingUnitOptions(row as DraftLine)"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </ElSelect>
                </div>
              </div>
              <span v-else class="order-template-line-display-text">
                {{ formatLinePricingQuantityText(row as DraftLine) }}
              </span>
            </template>

            <template #actions="{ row }">
              <ElButton
                link
                type="danger"
                :disabled="isReadonly"
                @click="handleRemoveLine(row as DraftLine)"
              >
                删除
              </ElButton>
            </template>
          </TableLayout>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.order-template-header-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order-template-header-form__row {
  display: flex;
  flex-wrap: nowrap;
  align-items: flex-start;
  gap: 12px 16px;
}

.order-template-header-form__row :deep(.el-form-item) {
  flex: 1 1 0;
  min-width: 0;
  margin-bottom: 0;
}

.order-template-header-form--tcm .order-template-header-form__row :deep(.el-form-item) {
  flex-basis: calc(25% - 12px);
  max-width: calc(25% - 12px);
}

.order-template-header-form__checkbox :deep(.el-form-item__content) {
  min-height: 32px;
  display: flex;
  align-items: center;
}

.order-template-edit-panel__body {
  min-height: 0;
}

.order-template-lines-table {
  min-height: 0;
}

.order-template-lines-table :deep(.vxe-grid),
.order-template-lines-table :deep(.vxe-table) {
  width: 100%;
}

.order-template-lines-table :deep(.el-table .cell) {
  overflow: hidden;
}

.line-edit-cell {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
}

.line-edit-cell :deep(.el-select:not(.total-unit-select)),
.line-edit-cell :deep(.el-input:not(.unit-input)),
.line-edit-cell :deep(.el-input-number:not(.dose-qty-input)),
.line-edit-cell :deep(.vxe-pulldown) {
  width: 100%;
  max-width: 100%;
}

.device-qty-wrap {
  grid-template-columns: minmax(72px, 1fr) 72px;
  max-width: 220px;
}

.dose-num-with-unit {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  width: 100%;
}

.dose-num-with-unit :deep(.dose-qty-input) {
  flex: 1 1 0;
  min-width: 72px;
  width: auto;
}

/* 单次剂量换算：拆零 = 剂量（对齐门诊医生站） */
.dose-convert-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  width: 100%;
}

.dose-convert-wrap .dose-num-with-unit {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 1 1 0;
  min-width: 0;
}

.dose-convert-wrap :deep(.dose-qty-input) {
  flex: 1 1 0;
  width: auto;
  min-width: 52px;
  max-width: none;
}

.dose-convert-wrap :deep(.dose-qty-input .el-input__wrapper) {
  width: 100%;
}

.dose-unit-tail {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: var(--el-component-size, 32px);
  user-select: none;
  white-space: nowrap;
  max-width: 3.5em;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}

.dose-convert-wrap .equals-sign {
  display: inline-flex;
  align-items: center;
  color: var(--el-text-color-secondary);
  font-weight: 700;
  flex-shrink: 0;
}

/* 总量：数字框占满剩余单元格，单位固定窄列 */
.total-amount-wrap {
  display: grid;
  grid-template-columns: minmax(72px, 1fr) 58px;
  align-items: center;
  gap: 6px;
  width: 100%;
}

.total-amount-wrap :deep(.dose-qty-input) {
  width: 100%;
  min-width: 0;
}

.total-amount-wrap :deep(.dose-qty-input .el-input__wrapper) {
  width: 100%;
}

.total-amount-wrap :deep(.total-unit-select),
.total-amount-wrap :deep(.unit-input) {
  width: 100%;
  min-width: 0;
}

.total-amount-wrap :deep(.total-unit-select .el-select__wrapper) {
  padding-left: 6px;
  padding-right: 18px;
}

.order-template-line-display-text {
  display: block;
  width: 100%;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-regular);
  font-size: var(--el-font-size-base);
  line-height: 32px;
}

.order-template-lines-table :deep(.order-template-line-row--editing) {
  background-color: var(--el-color-primary-light-9);
}

.order-template-lines-table :deep(.vxe-body--row:not(.order-template-line-row--editing)) {
  cursor: pointer;
}

.order-template-product-cell {
  position: relative;
  width: 100%;
  min-width: 0;
}

.order-template-product-cell--group-single,
.order-template-product-cell--group-first,
.order-template-product-cell--group-middle,
.order-template-product-cell--group-last {
  padding-left: 14px;
}

.order-template-product-cell--group-single::before,
.order-template-product-cell--group-first::before,
.order-template-product-cell--group-middle::before,
.order-template-product-cell--group-last::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  border-left: 2px dashed var(--el-text-color-secondary);
}

.order-template-product-cell--group-single::before {
  top: 20%;
  bottom: 20%;
}

.order-template-product-cell--group-single::after,
.order-template-product-cell--group-first::after {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 12px;
  border-top: 2px dashed var(--el-text-color-secondary);
}

.order-template-product-cell--group-last::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: 50%;
  width: 12px;
  border-bottom: 2px dashed var(--el-text-color-secondary);
}

.order-template-product-cell--group-first::before {
  top: 50%;
  bottom: 0;
}

.order-template-product-cell--group-last::before {
  top: 0;
  bottom: 50%;
}

.tcm-template-content {
  display: flex;
  flex-direction: column;
}

.tcm-herb-panel__scroll {
  flex: 1;
  min-height: 0;
  background: var(--el-fill-color-lighter);
}

.tcm-herb-panel__scroll :deep(.el-scrollbar__view) {
  background: var(--el-fill-color-lighter);
}

.tcm-herb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
  align-content: start;
}
</style>
