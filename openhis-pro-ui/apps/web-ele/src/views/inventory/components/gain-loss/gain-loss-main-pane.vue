<!--
  损益单右侧主区：InvMainShell + InvDocActions + 单头 + 明细表格（含 inv_chg_type reason 下拉）。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { ChargeType } from '#/api/core/common';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { formatDateTime } from '@vben/utils';

import { Delete, Plus } from '@element-plus/icons-vue';
import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
import {
  ElButton,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { useInventoryDocGridEdit } from '#/composables/use-inventory-doc-grid-edit';
import { useFinanceApprovalAccess } from '#/composables/use-finance-approval-access';
import {
  agreeDeptGainLossApi,
  createDeptGainLossApi,
  DEP_GAIN_LOSS_CATEGORY_ENUM,
  getDeptGainLossDetailApi,
  rejectDeptGainLossApi,
  removeDeptGainLossApi,
  submitDeptGainLossApprovalApi,
  withdrawDeptGainLossApprovalApi,
  type DeptGainLossDetailLineDto,
  type DeptGainLossLineDto,
} from '#/api/inventory/department/gain-loss';
import {
  agreePharmacyGainLossApi,
  createPharmacyGainLossApi,
  getPharmacyGainLossDetailApi,
  PHA_GAIN_LOSS_CATEGORY_ENUM,
  rejectPharmacyGainLossApi,
  removePharmacyGainLossApi,
  submitPharmacyGainLossApprovalApi,
  withdrawPharmacyGainLossApprovalApi,
  type PharmacyGainLossDetailLineDto,
  type PharmacyGainLossLineDto,
} from '#/api/inventory/pharmacy/gain-loss';
import {
  agreeGainLossApi,
  createGainLossApi,
  getGainLossDetailApi,
  rejectGainLossApi,
  removeGainLossApi,
  submitGainLossApprovalApi,
  WHS_GAIN_LOSS_CATEGORY_ENUM,
  withdrawGainLossApprovalApi,
  type GainLossDetailLineDto,
  type GainLossLineDto,
} from '#/api/inventory/warehouse/gain-loss';
import { getChargeTypeListApi } from '#/api/core/common';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { GAIN_LOSS_INV_CHG_TYPE_OPTIONS } from '#/constants/inventory/inv-chg-type-options';
import SectionTitle from '#/components/layouts/section-title.vue';
import {
  inventoryPickMedicineCell,
  inventoryPickStringField,
} from '#/utils/inventory/inventory-product-pick-list';
import {
  buildStocktakeDocActionFlags,
  normalizeStocktakeStatusSort,
  stocktakeStatusLabel,
} from '#/utils/inventory/stocktake-doc-actions';
import { resolveLoginPractitionerIdForRequest } from '#/utils/format/preserve-api-id';
import { useUserStore } from '@vben/stores';

defineOptions({ name: 'InventoryGainLossMainPane' });

type GainLossVariant = 'department' | 'pharmacy' | 'warehouse';

interface MedicinePickCell {
  id?: number | string;
  name?: string;
}

interface GainLossPreviewRow {
  id: string;
  medicinePick?: MedicinePickCell | Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  productName: string;
  gainLossQuantity?: number;
  unitCode: string;
  lotNumber: string;
  reason: string;
  price?: number;
}

interface Props {
  variant: GainLossVariant;
  busNo?: string;
  isDraftOrder?: boolean;
  resetKey?: number;
  sidebarPurposeLocationId?: number | string;
  locationOptions: ApiOptionItem[];
  practitionerOptions?: ApiOptionItem[];
  itemTableTypeOptions?: ApiOptionItem[];
  locationFieldLabel?: string;
  headSectionTitle?: string;
}

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  isDraftOrder: false,
  resetKey: 0,
  sidebarPurposeLocationId: undefined,
  locationOptions: () => [],
  practitionerOptions: () => [],
  itemTableTypeOptions: () => [],
  locationFieldLabel: '损益库房',
  headSectionTitle: '损益单信息',
});

const emit = defineEmits<{
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

const { canFinanceApprove } = useFinanceApprovalAccess();

const userStore = useUserStore();

const categoryEnum = computed(() => {
  if (props.variant === 'pharmacy') return PHA_GAIN_LOSS_CATEGORY_ENUM;
  if (props.variant === 'department') return DEP_GAIN_LOSS_CATEGORY_ENUM;
  return WHS_GAIN_LOSS_CATEGORY_ENUM;
});

const listSource = computed(() => {
  if (props.variant === 'pharmacy') return 'pharmacy-gain-loss';
  if (props.variant === 'department') return 'dept-gain-loss';
  return 'warehouse-gain-loss';
});

function firstItemTableTypeValue(): string {
  const o = props.itemTableTypeOptions?.[0];
  return o?.value !== undefined && o?.value !== null && String(o.value) !== ''
    ? String(o.value)
    : 'med_medication_definition';
}

function normalizeLocationId(v?: number | string | null): number | string | undefined {
  if (v === undefined || v === null || v === '') return undefined;
  if (typeof v === 'number') return Number.isFinite(v) ? v : undefined;
  const s = String(v).trim();
  if (s === '') return undefined;
  if (!/^\d+$/.test(s)) return s;
  const n = Number(s);
  return Number.isSafeInteger(n) ? n : s;
}

function isPresentItemId(v: unknown): boolean {
  if (v === undefined || v === null) return false;
  return String(v).trim() !== '';
}

const headerBusNo = ref<string | undefined>(undefined);
const detailStatusEnum = ref<number | undefined>(undefined);
const purposeLocationId = ref<number | string | undefined>(undefined);
const practitionerId = ref<number | string | undefined>(undefined);
const headItemTableType = ref(firstItemTableTypeValue());
const headApplyTime = ref(formatDateTime(new Date()));
const saving = ref(false);

const insuranceContractBusNo = ref<string>();
const insuranceChargeTypeOptions = ref<ChargeType[]>([]);

const isNewDraft = computed(() => !headerBusNo.value);
const documentEditable = computed(() => isNewDraft.value && (props.isDraftOrder || !props.busNo));

const headerStatusText = computed(() => {
  if (isNewDraft.value) return '编辑中';
  return stocktakeStatusLabel(detailStatusEnum.value);
});

const docActionFlags = computed(() => {
  const base = buildStocktakeDocActionFlags({
    hasPersistedBusNo: Boolean(headerBusNo.value),
    isDraft: isNewDraft.value,
    status: detailStatusEnum.value,
  });
  const busy = saving.value;
  return {
    ...base,
    saveDisabled: base.saveDisabled || busy || !documentEditable.value,
    saveAndSubmitDisabled: base.saveAndSubmitDisabled || busy || !documentEditable.value,
  };
});

async function loadInsuranceContractBusNo() {
  try {
    const chargeTypes = await getChargeTypeListApi();
    const eligible = filterInsuranceChargeTypes(chargeTypes);
    insuranceChargeTypeOptions.value = eligible;
    insuranceContractBusNo.value = resolveDefaultInsuranceBusNo(
      eligible,
      insuranceContractBusNo.value,
    );
  } catch {
    insuranceContractBusNo.value = undefined;
    insuranceChargeTypeOptions.value = [];
  }
}

function resolveOccurrenceTimeForApi(): string {
  const t = String(headApplyTime.value ?? '').trim();
  if (t === '') return formatDateTime(new Date());
  return t.includes('T') ? t.replace('T', ' ') : t;
}

function syncRowFromProductPick(pick: Record<string, unknown>, row: GainLossPreviewRow) {
  row.medicinePick =
    inventoryPickMedicineCell(pick) ?? (pick as GainLossPreviewRow['medicinePick']);
  row.itemTable = inventoryPickStringField(pick, 'productTableName');
  const defId = pick.productDefinitionId;
  row.itemId =
    defId === undefined || defId === null || String(defId).trim() === ''
      ? undefined
      : (defId as number | string);
  row.productName =
    inventoryPickStringField(pick, 'merchandiseName') ||
    inventoryPickStringField(pick, 'productName');
  row.unitCode =
    inventoryPickStringField(pick, 'minUnitCode') || inventoryPickStringField(pick, 'unitCode');
  row.lotNumber = inventoryPickStringField(pick, 'lotNumber');
}

function createEmptyRow(): GainLossPreviewRow {
  return {
    id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    medicinePick: undefined,
    itemTable: undefined,
    itemId: undefined,
    productName: '',
    gainLossQuantity: undefined,
    unitCode: '',
    lotNumber: '',
    reason: '',
    price: undefined,
  };
}

function mapDetailLineToGrid(
  ln: DeptGainLossDetailLineDto | GainLossDetailLineDto | PharmacyGainLossDetailLineDto,
  rowIdx: number,
): GainLossPreviewRow {
  const itemIdRaw = ln.itemId;
  const idStr =
    ln.lineId !== undefined && ln.lineId !== null
      ? `ln-${String(ln.lineId)}`
      : itemIdRaw !== undefined && itemIdRaw !== null
        ? `ln-item-${String(itemIdRaw)}-${rowIdx}`
        : `ln-x-${rowIdx}`;
  const name = String(ln.productName ?? '').trim();
  return {
    id: idStr,
    medicinePick: name ? { id: itemIdRaw as number | string | undefined, name } : undefined,
    itemTable: ln.itemTable,
    itemId: itemIdRaw,
    productName: name,
    gainLossQuantity: ln.gainLossQuantity === undefined ? undefined : Number(ln.gainLossQuantity),
    unitCode: String(ln.unitCode ?? ''),
    lotNumber: String(ln.lotNumber ?? ''),
    reason: String(ln.reason ?? ''),
    price: ln.price === undefined ? undefined : Number(ln.price),
  };
}

function isGainLossRowComplete(row: GainLossPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.gainLossQuantity);
  if (!Number.isFinite(qty) || qty === 0) return false;
  if (!String(row.unitCode ?? '').trim()) return false;
  if (!String(row.lotNumber ?? '').trim()) return false;
  return !!String(row.reason ?? '').trim();
}

let gainLossGridEdit!: ReturnType<typeof useInventoryDocGridEdit<GainLossPreviewRow>>;

function handleGainLossProductPick(row: GainLossPreviewRow) {
  void nextTick(async () => {
    await nextTick();
    await gainLossGridEdit.onProductSelected(row);
  });
}

const columns: VxeGridPropTypes.Column[] = [
  { type: 'checkbox', width: 48, fixed: 'left', align: 'center' },
  { type: 'seq', title: '序号', width: 52, fixed: 'left', align: 'center' },
  {
    field: 'medicinePick',
    title: '项目名称',
    minWidth: 200,
    fixed: 'left',
    editRender: {
      name: 'PurchaseOrderMedicineSelect',
      props: {
        listSource: listSource,
        query: async () => ({
          productTableName: headItemTableType.value,
          purposeLocationId: purposeLocationId.value ?? props.sidebarPurposeLocationId,
        }),
        onSelected: (product: Record<string, unknown>, row: GainLossPreviewRow) => {
          syncRowFromProductPick(product, row);
          handleGainLossProductPick(row);
        },
      },
    },
    showOverflow: true,
  },
  {
    field: 'gainLossQuantity',
    title: '损益数量',
    width: 120,
    align: 'center',
    editRender: { name: 'InventoryPersistElInputNumber', props: { controlsPosition: 'right' } },
  },
  {
    field: 'unitCode',
    title: '单位',
    width: 88,
    showOverflow: true,
  },
  {
    field: 'lotNumber',
    title: '批号',
    width: 120,
    editRender: { name: 'InventoryPersistElInput', props: { maxlength: 64, clearable: true } },
    showOverflow: true,
  },
  {
    field: 'reason',
    title: '变更原因',
    minWidth: 160,
    editRender: {
      name: 'InventoryPersistElSelect',
      options: GAIN_LOSS_INV_CHG_TYPE_OPTIONS.map((o) => ({
        label: o.label,
        value: o.value,
      })),
      props: { filterable: true, clearable: true },
    },
    showOverflow: true,
  },
  {
    slots: { default: 'action' },
    title: '操作',
    align: 'center',
    fixed: 'right',
    field: 'action',
    width: 88,
  },
];

const gridOptions: VxeGridProps<GainLossPreviewRow> = reactive({
  autoResize: true,
  height: '100%',
  border: true,
  stripe: true,
  showOverflow: true,
  size: 'medium',
  keepSource: true,
  columnConfig: { resizable: true },
  data: [],
  editConfig: {
    enabled: true,
    trigger: 'click',
    mode: 'row',
    autoClear: false,
    beforeEditMethod: () => documentEditable.value,
  },
  columns,
  pagerConfig: { enabled: false },
});

const GAIN_LOSS_EDITABLE_COLUMN_FIELDS = new Set([
  'gainLossQuantity',
  'lotNumber',
  'medicinePick',
  'reason',
]);

const GAIN_LOSS_EDIT_FIELD_ORDER = [
  'medicinePick',
  'gainLossQuantity',
  'lotNumber',
  'reason',
] as const;

type GainLossEditField = (typeof GAIN_LOSS_EDIT_FIELD_ORDER)[number];

const GAIN_LOSS_VERTICAL_NAV_FIELDS = new Set<GainLossEditField>([
  'gainLossQuantity',
  'lotNumber',
  'reason',
]);

const gainLossGridKeyboardRef = ref<HTMLElement | null>(null);

const [Grid, gridApi] = useVbenVxeGrid<GainLossPreviewRow>({
  gridOptions,
  gridEvents: {
    cellClick: (params: { column?: { field?: string; type?: string }; row: GainLossPreviewRow }) =>
      gainLossGridEdit.onCellClick(params),
    editClosed(params: { row: GainLossPreviewRow }) {
      gainLossGridEdit.syncRowFormMode(params.row);
    },
  },
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
});

gainLossGridEdit = useInventoryDocGridEdit<GainLossPreviewRow>({
  getGridApi: () => gridApi ?? null,
  gridWrapRef: gainLossGridKeyboardRef,
  documentEditable: () => documentEditable.value,
  collectRows: collectGridRows,
  config: {
    rowKey: (row) => row.id,
    productField: 'medicinePick',
    editFieldOrder: GAIN_LOSS_EDIT_FIELD_ORDER,
    editableFields: GAIN_LOSS_EDITABLE_COLUMN_FIELDS,
    verticalNavFields: GAIN_LOSS_VERTICAL_NAV_FIELDS,
    firstFillFieldAfterPick: 'gainLossQuantity',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isGainLossRowComplete,
  },
});

function patchGainLossGridEditConfig() {
  const patched = gainLossGridEdit.patchGridOptions({
    ...(gridOptions as Record<string, unknown>),
    editConfig: {
      ...(gridOptions.editConfig as Record<string, unknown>),
      enabled: documentEditable.value,
    },
  });
  Object.assign(gridOptions, patched);
}

watch(
  () => documentEditable.value,
  (editable) => {
    patchGainLossGridEditConfig();
    void nextTick(() => {
      if (!editable) {
        gainLossGridEdit.resetFormMode();
        gainLossGridEdit.clearGridEdit();
      }
    });
  },
  { immediate: true },
);

function collectGridRows(): GainLossPreviewRow[] {
  const g = gridApi?.grid as null | { getFullData?: () => GainLossPreviewRow[] };
  return g?.getFullData?.() ?? [];
}

function resetDetailForCreate() {
  headerBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  const sid = normalizeLocationId(props.sidebarPurposeLocationId);
  purposeLocationId.value = sid ?? props.locationOptions[0]?.value;
  practitionerId.value =
    resolveLoginPractitionerIdForRequest(userStore.userInfo as null | Record<string, unknown>) ??
    props.practitionerOptions[0]?.value;
  headApplyTime.value = formatDateTime(new Date());
  gainLossGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchGainLossGridEditConfig();
  });
}

async function loadDetail(busNo: string) {
  gainLossGridEdit.resetFormMode();
  gainLossGridEdit.clearGridEdit();
  try {
    const res =
      props.variant === 'pharmacy'
        ? await getPharmacyGainLossDetailApi({ busNo })
        : props.variant === 'department'
          ? await getDeptGainLossDetailApi({ busNo })
          : await getGainLossDetailApi({ busNo });
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到损益单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    detailStatusEnum.value = normalizeStocktakeStatusSort(data.statusEnum);
    purposeLocationId.value = data.purposeLocationId ?? undefined;
    practitionerId.value = data.practitionerId ?? undefined;
    headApplyTime.value = data.applyTime
      ? String(data.applyTime).includes('T')
        ? String(data.applyTime).replace('T', ' ')
        : String(data.applyTime)
      : formatDateTime(new Date());

    const gridRows = (data.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchGainLossGridEditConfig();
    });
  } catch {
    void nextTick(() => {
      gridApi?.grid?.loadData([]);
      patchGainLossGridEditConfig();
    });
  }
}

function buildLinesPayload(
  rows: GainLossPreviewRow[],
): DeptGainLossLineDto[] | GainLossLineDto[] | PharmacyGainLossLineDto[] {
  return rows.map((r) => ({
    itemTable: r.itemTable,
    itemId:
      typeof r.itemId === 'string' && /^\d+$/.test(r.itemId)
        ? Number(r.itemId)
        : (r.itemId as number | undefined),
    gainLossQuantity: r.gainLossQuantity,
    unitCode: String(r.unitCode ?? '').trim() || undefined,
    lotNumber: String(r.lotNumber ?? '').trim() || undefined,
    reason: String(r.reason ?? '').trim() || undefined,
    price: r.price,
    occurrenceTime: resolveOccurrenceTimeForApi(),
  }));
}

function validateBeforeSave(rows: GainLossPreviewRow[]): boolean {
  const loc = purposeLocationId.value;
  if (loc === undefined || loc === null || String(loc).trim() === '') {
    ElMessage.warning(`请选择${props.locationFieldLabel}`);
    return false;
  }
  const pid = practitionerId.value;
  if (pid === undefined || pid === null || String(pid).trim() === '') {
    ElMessage.warning('请选择经手人');
    return false;
  }
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一行损益明细');
    return false;
  }
  for (const [i, r] of rows.entries()) {
    if (!String(r.itemTable ?? '').trim() || !isPresentItemId(r.itemId)) {
      ElMessage.warning(`第 ${i + 1} 行请选择项目`);
      return false;
    }
    const qty = Number(r.gainLossQuantity);
    if (!Number.isFinite(qty) || qty === 0) {
      ElMessage.warning(`第 ${i + 1} 行损益数量不能为 0`);
      return false;
    }
    if (!String(r.unitCode ?? '').trim()) {
      ElMessage.warning(`第 ${i + 1} 行单位不能为空`);
      return false;
    }
    if (!String(r.lotNumber ?? '').trim()) {
      ElMessage.warning(`第 ${i + 1} 行批号不能为空`);
      return false;
    }
    if (!String(r.reason ?? '').trim()) {
      ElMessage.warning(`第 ${i + 1} 行请选择变更原因`);
      return false;
    }
  }
  return true;
}

async function persistGainLoss(): Promise<string | undefined> {
  if (!documentEditable.value) {
    ElMessage.warning('当前状态不允许修改');
    return undefined;
  }
  const rows = collectGridRows();
  if (!validateBeforeSave(rows)) return undefined;

  saving.value = true;
  try {
    const lines = buildLinesPayload(rows);
    const common = {
      purposeLocationId: purposeLocationId.value as number | string,
      practitionerId: practitionerId.value as number | string,
      categoryEnum: categoryEnum.value,
      lines,
    };

    const res =
      props.variant === 'pharmacy'
        ? await createPharmacyGainLossApi(common)
        : props.variant === 'department'
          ? await createDeptGainLossApi(common)
          : await createGainLossApi(common);

    const no = res.data?.busNo;
    ElMessage.success('新建成功');
    if (no) {
      headerBusNo.value = no;
      detailStatusEnum.value = normalizeStocktakeStatusSort(res.data?.statusEnum) ?? 1;
      await loadDetail(no);
    }
    emit('documentSaved', { busNo: no });
    return no;
  } catch {
    return undefined;
  } finally {
    saving.value = false;
  }
}

async function handleSaveDoc() {
  await persistGainLoss();
}

async function handleSaveAndSubmitDoc() {
  const no = await persistGainLoss();
  if (!no) return;
  await handleSubmitApproval();
}

async function handleRemoveDoc() {
  if (isNewDraft.value) {
    try {
      await ElMessageBox.confirm('确认放弃该草稿？', '提示', { type: 'warning' });
    } catch {
      return;
    }
    resetDetailForCreate();
    emit('documentRemoved');
    return;
  }
  const no = headerBusNo.value;
  if (!no) return;
  if (detailStatusEnum.value !== 1) {
    ElMessage.warning('仅待审核单据可删除');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除整张损益单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    if (props.variant === 'pharmacy') {
      await removePharmacyGainLossApi({ busNo: no });
    } else if (props.variant === 'department') {
      await removeDeptGainLossApi({ busNo: no });
    } else {
      await removeGainLossApi({ busNo: no });
    }
    ElMessage.success('已删除');
    resetDetailForCreate();
    emit('documentRemoved');
  } catch {
    /* handled */
  }
}

async function handleSubmitApproval() {
  const no = headerBusNo.value;
  if (!no) return;
  try {
    const res =
      props.variant === 'pharmacy'
        ? await submitPharmacyGainLossApprovalApi({ busNo: no })
        : props.variant === 'department'
          ? await submitDeptGainLossApprovalApi({ busNo: no })
          : await submitGainLossApprovalApi({ busNo: no });
    if (res.data?.statusEnum !== undefined && res.data.statusEnum !== null) {
      detailStatusEnum.value = normalizeStocktakeStatusSort(res.data.statusEnum);
    }
    ElMessage.success('已提交审批');
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch {
    /* handled */
  }
}

async function handleWithdraw() {
  const no = headerBusNo.value;
  if (!no) return;
  try {
    const res =
      props.variant === 'pharmacy'
        ? await withdrawPharmacyGainLossApprovalApi({ busNo: no })
        : props.variant === 'department'
          ? await withdrawDeptGainLossApprovalApi({ busNo: no })
          : await withdrawGainLossApprovalApi({ busNo: no });
    if (res.data?.statusEnum !== undefined && res.data.statusEnum !== null) {
      detailStatusEnum.value = normalizeStocktakeStatusSort(res.data.statusEnum);
    }
    ElMessage.success('已撤回');
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch {
    /* handled */
  }
}

async function handleApprove() {
  const no = headerBusNo.value;
  if (!no) return;
  if (!insuranceContractBusNo.value?.trim()) {
    ElMessage.warning('请选择医保契约');
    return;
  }
  try {
    const body = { busNo: no, contractBusNo: insuranceContractBusNo.value };
    if (props.variant === 'pharmacy') {
      await agreePharmacyGainLossApi(body);
    } else if (props.variant === 'department') {
      await agreeDeptGainLossApi(body);
    } else {
      await agreeGainLossApi(body);
    }
    ElMessage.success('审批同意');
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch {
    /* handled */
  }
}

async function handleReject() {
  const no = headerBusNo.value;
  if (!no) return;
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '原因不能为空',
    });
    const body = { busNo: no, rejectReason: value };
    if (props.variant === 'pharmacy') {
      await rejectPharmacyGainLossApi(body);
    } else if (props.variant === 'department') {
      await rejectDeptGainLossApi(body);
    } else {
      await rejectGainLossApi(body);
    }
    ElMessage.success('已驳回');
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch (e) {
    if (e === 'cancel') return;
  }
}

async function addItem() {
  if (!documentEditable.value) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const record = createEmptyRow();
  const { row } = await $grid.insertAt(record, null);
  await $grid.$nextTick();
  if (row) {
    await $grid.scrollToRow(row);
    await gainLossGridEdit.enterRowFormMode(row as GainLossPreviewRow, 'medicinePick');
  }
}

async function deleteItem(row: 'select' | GainLossPreviewRow) {
  if (!documentEditable.value) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const checked = $grid.getCheckboxRecords() as GainLossPreviewRow[];
  if (row === 'select' && checked.length === 0) return;
  await $grid.remove(row === 'select' ? checked : row);
}

async function syncFromParentContext() {
  if (props.isDraftOrder) {
    resetDetailForCreate();
    return;
  }
  const bn = String(props.busNo ?? '').trim();
  if (bn) {
    await loadDetail(bn);
  } else {
    resetDetailForCreate();
  }
}

watch(
  () => [props.busNo, props.isDraftOrder, props.resetKey] as const,
  () => {
    void syncFromParentContext();
  },
  { immediate: true },
);

watch(
  () => props.sidebarPurposeLocationId,
  () => {
    if (props.isDraftOrder || !headerBusNo.value) {
      const sid = normalizeLocationId(props.sidebarPurposeLocationId);
      if (sid !== undefined) {
        purposeLocationId.value = sid;
      }
    }
  },
);

watch(
  () => props.locationOptions,
  (list) => {
    if (list.length === 0) return;
    if (!purposeLocationId.value && (isNewDraft.value || !headerBusNo.value)) {
      const sid = normalizeLocationId(props.sidebarPurposeLocationId);
      purposeLocationId.value = sid ?? list[0]?.value;
    }
  },
  { deep: true },
);

onMounted(() => {
  void loadInsuranceContractBusNo();
});
</script>

<template>
  <div class="gain-loss-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
    <InvMainShell>
      <template #actionBar>
        <div class="flex w-full flex-col gap-2">
          <div
            v-if="insuranceChargeTypeOptions.length > 0"
            class="flex flex-wrap items-center gap-2"
          >
            <span class="text-xs text-[var(--el-text-color-secondary)]">医保契约</span>
            <ElSelect
              v-model="insuranceContractBusNo"
              class="!w-72"
              placeholder="请选择医保契约"
              filterable
              clearable
              :disabled="docActionFlags.approveDisabled"
            >
              <ElOption
                v-for="item in insuranceChargeTypeOptions"
                :key="item.busNo"
                :label="item.busNo ? `${item.label}（${item.busNo}）` : item.label"
                :value="item.busNo"
              />
            </ElSelect>
          </div>
          <div class="flex w-full flex-wrap items-center justify-end gap-2">
            <InvDocActions
              v-bind="docActionFlags"
              :show-approval-actions="canFinanceApprove"
              @approve="handleApprove"
              @reject="handleReject"
              @remove-doc="handleRemoveDoc"
              @save="handleSaveDoc"
              @save-and-submit="handleSaveAndSubmitDoc"
              @submit-approval="handleSubmitApproval"
              @withdraw="handleWithdraw"
            />
          </div>
        </div>
      </template>

      <div
        class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-2 pt-2"
        @mousedown="gainLossGridEdit.onSurfaceMouseDown"
      >
        <div class="gain-loss-head-panel shrink-0">
          <div
            class="gain-loss-head-panel__bar flex flex-wrap items-center justify-between gap-x-4 gap-y-2"
          >
            <SectionTitle :title="headSectionTitle" />
            <div class="flex flex-wrap items-center gap-2">
              <span class="text-xs text-[var(--el-text-color-secondary)]">状态</span>
              <ElTag size="small" type="info" effect="plain">{{ headerStatusText || '—' }}</ElTag>
            </div>
          </div>
          <ElForm label-width="96px" class="compact-head-form gain-loss-head-form">
            <div
              class="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
            >
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem :label="locationFieldLabel">
                <ElSelect
                  v-model="purposeLocationId"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="loc in locationOptions"
                    :key="String(loc.value)"
                    :label="loc.label"
                    :value="loc.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="经手人">
                <ElSelect
                  v-model="practitionerId"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="p in practitionerOptions"
                    :key="String(p.value)"
                    :label="p.label"
                    :value="p.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="制单时间">
                <ElDatePicker
                  v-model="headApplyTime"
                  class="w-full"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  format="YYYY-MM-DD HH:mm:ss"
                  placeholder="选择时间"
                  :disabled="!documentEditable"
                />
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <div
          ref="gainLossGridKeyboardRef"
          class="gain-loss-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col"
          @keydown.capture="gainLossGridEdit.onGridKeydown"
        >
          <Grid class="min-h-0 flex-1">
            <template #toolbar-actions>
              <ElButton
                class="mr-2"
                type="primary"
                :icon="Plus"
                :disabled="!documentEditable"
                @click="addItem"
              >
                添加行
              </ElButton>
              <ElButton
                type="danger"
                :icon="Delete"
                :disabled="!documentEditable"
                @click="deleteItem('select')"
              >
                删除行
              </ElButton>
            </template>
            <template #action="{ row }">
              <ElButton
                type="danger"
                text
                :disabled="!documentEditable"
                @click="deleteItem(row as GainLossPreviewRow)"
              >
                <Trash2 class="size-4" />
                删除
              </ElButton>
            </template>
          </Grid>
        </div>
      </div>
    </InvMainShell>
  </div>
</template>

<style scoped lang="scss">
.gain-loss-head-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 12px 4px;
  background: var(--el-bg-color);
}

.gain-loss-main-pane__grid-wrap :deep(.vxe-grid) {
  min-height: 200px;
}
</style>
