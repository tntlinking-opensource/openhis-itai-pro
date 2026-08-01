<!--
  损益单右侧主区：InvMainShell + InvDocActions + 单头 + 明细表格（变更原因手写）。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { formatDateTime } from '@vben/utils';

import { QuestionFilled } from '@element-plus/icons-vue';
import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocGridToolbar from '@inventory/components/inv-doc-grid-toolbar.vue';
import InvDocTraceScanBridge from '@inventory/components/inv-doc-trace-scan-bridge.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
import type { FormInstance } from 'element-plus';

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
  ElTooltip,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { useFromFinanceApproval } from '#/composables/use-from-finance-approval';
import { bindInvDocCompactColumns } from '#/composables/use-inv-doc-compact-columns';
import {
  applyInventoryRowQtyUnitList,
  applyWarehouseNonInboundProductPickUnitsToRow,
  createInventoryQtyUnitColumn,
  hydrateInventoryDetailLineQtyForGrid,
  INV_DOC_HEAD_FORM_GRID_CLASS,
  INV_DOC_HEAD_SECTION_CLASS,
} from '#/utils/inventory/inventory-doc-grid-common';
import { useInventoryDocGridEdit } from '#/composables/use-inventory-doc-grid-edit';
import { useInvDocParentSyncSession } from '#/composables/use-inv-doc-parent-sync-session';
import {
  agreeDeptGainLossApi,
  createDeptGainLossApi,
  DEP_GAIN_LOSS_CATEGORY_ENUM,
  getDeptGainLossDetailApi,
  rejectDeptGainLossApi,
  removeDeptGainLossApi,
  submitDeptGainLossApprovalApi,
  updateDeptGainLossApi,
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
  updatePharmacyGainLossApi,
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
  updateGainLossApi,
  WHS_GAIN_LOSS_CATEGORY_ENUM,
  withdrawGainLossApprovalApi,
  type GainLossDetailLineDto,
  type GainLossLineDto,
} from '#/api/inventory/warehouse/gain-loss';
import SectionTitle from '#/components/layouts/section-title.vue';
import {
  inventoryPickMedicineCell,
  inventoryPickNumberField,
  inventoryPickStringField,
} from '#/utils/inventory/inventory-product-pick-list';
import {
  formatInventoryLineTotal,
  formatMoneyDisplay,
  inventoryLineTotalNumber,
} from '#/utils/format/money-scale';
import {
  applyWarehouseNonInboundBandKindToRow,
  canConvertDisplayQtyToSplitForBands,
  convertInboundQtyBetweenBandKinds,
  persistDisplayQtyAsSplitForBands,
  resolveInboundLinePersistUnitCodeForBands,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  buildStocktakeDocActionFlags,
  normalizeStocktakeStatusSort,
  stocktakeStatusLabel,
} from '#/utils/inventory/stocktake-doc-actions';
import {
  isStockOutDocumentEditableStatus,
  isStockOutDocumentRemovableStatus,
} from '#/utils/inventory/stock-out-doc-actions';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import {
  buildInventoryDocPractitionerSelectOptions,
  coercePractitionerIdForSelect,
  INV_ITEM_TABLE_TYPE_DEVICE,
  INV_ITEM_TABLE_TYPE_MEDICATION,
  isSpecificInventoryLocationId,
  normalizeInventoryItemTableTypeOptions,
  resolveDefaultInventoryItemTableType,
  resolveDefaultPractitionerIdForSelect,
  resolveDocHeadProductPickLocationId,
  warnInventoryLocationRequiredByLabel,
} from '#/utils/inventory/inventory-workbench-list';
import { buildInvDocumentTitle, printInvDocument } from '#/utils/print/print-inv-document';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';
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
  inventoryItemId?: number | string;
  productName: string;
  spec: string;
  manufacturer: string;
  gainLossQuantity?: number;
  unitCode: string;
  unitCodeDictText?: string;
  selectedBandKind?: string;
  __prevBandKind?: string;
  unitList?: { label?: string; value: string; value_dictText?: string }[];
  unitBands?: import('#/utils/inventory/inbound-product-unit-bands').InboundProductUnitBand[];
  lotNumber: string;
  traceNo: string;
  reason: string;
  /** 药品分类码；中药=4 时单价三位展示 */
  categoryCode?: string;
  price?: number;
  /** 损益金额 = 损益数量 × 单价（编辑态计算，详情回显以后端为准） */
  gainLossAmount: string;
}

interface Props {
  variant: GainLossVariant;
  busNo?: string;
  isDraftOrder?: boolean;
  resetKey?: number;
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

/** 选品门禁：优先单头库位，与侧栏「全部」筛选解耦。 */
const productPickGateLocationId = computed(() =>
  resolveDocHeadProductPickLocationId(purposeLocationId.value, props.locationOptions),
);

const normalizedItemTableTypeOptions = computed(() =>
  normalizeInventoryItemTableTypeOptions(props.itemTableTypeOptions),
);

const pinnedPractitionerOptions = ref<ApiOptionItem[]>([]);

const effectivePractitionerOptions = computed(() =>
  buildInventoryDocPractitionerSelectOptions(props.practitionerOptions, {
    pinned: pinnedPractitionerOptions.value,
    userInfo: userStore.userInfo as null | Record<string, unknown>,
  }),
);

function preferredItemTableType(): string {
  return props.variant === 'department'
    ? INV_ITEM_TABLE_TYPE_DEVICE
    : INV_ITEM_TABLE_TYPE_MEDICATION;
}

function firstItemTableTypeValue(): string {
  return resolveDefaultInventoryItemTableType(
    normalizedItemTableTypeOptions.value,
    preferredItemTableType(),
  );
}

function pinPractitionerOption(id: unknown, label?: string) {
  const v = String(id ?? '').trim();
  if (v === '') return;
  const lbl = String(label ?? '').trim();
  if (effectivePractitionerOptions.value.some((o) => o.value === v)) return;
  if (lbl !== '' && lbl !== v) {
    pinnedPractitionerOptions.value = [{ label: lbl, value: v }];
  }
}

function isPresentItemId(v: unknown): boolean {
  if (v === undefined || v === null) return false;
  return String(v).trim() !== '';
}

const headerBusNo = ref<string | undefined>(undefined);
const detailStatusEnum = ref<number | undefined>(undefined);
const purposeLocationId = ref<number | string | undefined>(undefined);
const headFormRef = ref<FormInstance>();

const headFormModel = computed(() => ({
  purposeLocationId: purposeLocationId.value ?? '',
}));

const headFormRules = computed(() =>
  buildInventoryLocationHeadFormRules({
    purposeLocationId: props.locationFieldLabel,
  }),
);

const practitionerId = ref<number | string | undefined>(undefined);
const headItemTableType = ref(firstItemTableTypeValue());
const headApplyTime = ref(formatDateTime(new Date()));
const saving = ref(false);

const isNewDraft = computed(() => !headerBusNo.value);
const { fromFinanceApproval } = useFromFinanceApproval();
/** 新建草稿或待审核/驳回/已撤回可改；财务审批深链强制只读 */
const documentEditable = computed(
  () =>
    !fromFinanceApproval.value &&
    (isNewDraft.value || isStockOutDocumentEditableStatus(detailStatusEnum.value)),
);

const headerStatusText = computed(() => {
  if (isNewDraft.value) return '编辑中';
  return stocktakeStatusLabel(detailStatusEnum.value);
});

const docActionFlags = computed(() => {
  const hasPersistedBusNo = Boolean(headerBusNo.value);
  const base = buildStocktakeDocActionFlags({
    hasPersistedBusNo,
    isDraft: isNewDraft.value,
    status: detailStatusEnum.value,
  });
  const busy = saving.value;
  return {
    ...base,
    saveDisabled: base.saveDisabled || busy || !documentEditable.value,
    saveAndSubmitDisabled: base.saveAndSubmitDisabled || busy || !documentEditable.value,
    printDisabled: busy || !hasPersistedBusNo,
  };
});

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
  const invId = pick.inventoryItemId;
  row.inventoryItemId =
    invId === undefined || invId === null || String(invId).trim() === ''
      ? undefined
      : (invId as number | string);
  row.productName =
    inventoryPickStringField(pick, 'merchandiseName') ||
    inventoryPickStringField(pick, 'productName');
  row.spec = inventoryPickStringField(pick, 'specification');
  row.manufacturer = inventoryPickStringField(pick, 'manufacturer');
  row.lotNumber = inventoryPickStringField(pick, 'lotNumber');
  applyWarehouseNonInboundProductPickUnitsToRow(pick, row, {
    legacyUnitPrice: inventoryPickNumberField(pick, 'purchaseUnitPrice'),
  });
  recalcGainLossLine(row);
}

/** 编辑态：按损益数量 × 单价刷新金额（支持负数数量）。 */
function recalcGainLossLine(row: GainLossPreviewRow) {
  const qty = Number(row.gainLossQuantity);
  const price = Number(row.price);
  row.gainLossAmount =
    Number.isFinite(qty) && Number.isFinite(price) ? formatInventoryLineTotal(qty, price) : '';
}

function handleGainLossUnitBandChanged(row: GainLossPreviewRow) {
  const newK = String(row.selectedBandKind ?? '')
    .trim()
    .toUpperCase();
  const oldK = String(row.__prevBandKind ?? '')
    .trim()
    .toUpperCase();
  const bands = row.unitBands ?? [];
  if (oldK && newK && oldK !== newK && oldK !== 'LEGACY' && newK !== 'LEGACY' && bands.length > 0) {
    const qty = Number(row.gainLossQuantity);
    if (Number.isFinite(qty) && qty !== 0) {
      const sign = qty < 0 ? -1 : 1;
      const nq = convertInboundQtyBetweenBandKinds(Math.abs(qty), oldK, newK, bands);
      if (nq !== undefined) {
        row.gainLossQuantity = nq * sign;
      }
    }
  }
  applyWarehouseNonInboundBandKindToRow(row, newK, row.price);
  row.__prevBandKind = newK;
  recalcGainLossLine(row);
}

function createEmptyRow(): GainLossPreviewRow {
  return {
    id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    medicinePick: undefined,
    itemTable: undefined,
    itemId: undefined,
    productName: '',
    spec: '',
    manufacturer: '',
    gainLossQuantity: undefined,
    unitCode: '',
    selectedBandKind: 'LEGACY',
    __prevBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: '单位' }],
    lotNumber: '',
    traceNo: '',
    reason: '',
    price: undefined,
    gainLossAmount: '',
  };
}

function formatGainLossDetailAmount(value: unknown): string {
  if (value === undefined || value === null || value === '') return '';
  const n = Number(value);
  if (!Number.isFinite(n)) return String(value).trim();
  return formatMoneyDisplay(n);
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
  const unitCodeStr = String(ln.unitCode ?? '');
  const rawQty = ln.gainLossQuantity === undefined ? undefined : Number(ln.gainLossQuantity);
  const catalog = (ln as { productCatalog?: { unitBands?: unknown } }).productCatalog;
  const unitCodeDictText = String(
    ('unitCodeDictText' in ln ? ln.unitCodeDictText : undefined) ?? ln.unitCode ?? '',
  );
  const row: GainLossPreviewRow = {
    id: idStr,
    medicinePick: name ? { id: itemIdRaw as number | string | undefined, name } : undefined,
    itemTable: ln.itemTable,
    itemId: itemIdRaw,
    inventoryItemId:
      'inventoryItemId' in ln && ln.inventoryItemId !== undefined && ln.inventoryItemId !== null
        ? ln.inventoryItemId
        : undefined,
    productName: name,
    spec: String(ln.specification ?? ''),
    manufacturer: String(ln.manufacturer ?? ''),
    gainLossQuantity: rawQty,
    unitCode: unitCodeStr,
    unitCodeDictText,
    lotNumber: String(ln.lotNumber ?? ''),
    traceNo: String(ln.traceNo ?? '').trim(),
    reason: String(ln.reason ?? ''),
    price: ln.price === undefined ? undefined : Number(ln.price),
    gainLossAmount: formatGainLossDetailAmount(
      'gainLossAmount' in ln ? ln.gainLossAmount : undefined,
    ),
  };

  const sign = rawQty !== undefined && Number.isFinite(rawQty) && rawQty < 0 ? -1 : 1;
  const absPersisted = rawQty === undefined || !Number.isFinite(rawQty) ? 0 : Math.abs(rawQty);
  const hydrated = hydrateInventoryDetailLineQtyForGrid(
    {
      itemQuantity: absPersisted,
      unitCode: unitCodeStr,
      unitCodeDictText,
      productCatalog: catalog,
    },
    { useSupplyLineHydrate: true },
  );
  row.gainLossQuantity = hydrated.displayQty * sign;
  row.unitCode = hydrated.unitCode;
  if (hydrated.unitCodeDictText) {
    row.unitCodeDictText = hydrated.unitCodeDictText;
  }
  if (hydrated.unitBands?.length) {
    row.unitBands = hydrated.unitBands;
    row.unitList = hydrated.unitList;
    row.selectedBandKind = hydrated.bandKind;
    row.__prevBandKind = hydrated.bandKind;
  } else {
    applyInventoryRowQtyUnitList(row);
  }
  if (hydrated.categoryCode) {
    row.categoryCode = hydrated.categoryCode;
  }
  return row;
}

function isGainLossRowComplete(row: GainLossPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.gainLossQuantity);
  if (!Number.isFinite(qty) || qty === 0) return false;
  if (!String(row.unitCode ?? '').trim()) return false;
  if (!String(row.lotNumber ?? '').trim()) return false;
  return true;
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
          purposeLocationId: productPickGateLocationId.value,
        }),
        onSelected: (product: Record<string, unknown>, row: GainLossPreviewRow) => {
          syncRowFromProductPick(product, row);
          handleGainLossProductPick(row);
        },
      },
    },
    showOverflow: true,
  },
  { field: 'spec', title: '规格', width: 110, showOverflow: true },
  { field: 'manufacturer', title: '厂家', minWidth: 100, showOverflow: true },
  createInventoryQtyUnitColumn({
    qtyField: 'gainLossQuantity',
    title: '损益数量',
    allowNegative: true,
    headerSlot: 'gainLossQtyHeader',
    onUnitChange: (row) => handleGainLossUnitBandChanged(row as GainLossPreviewRow),
  }),
  {
    field: 'price',
    title: '单价',
    width: 120,
    align: 'right',
    headerAlign: 'center',
    cellRender: {
      name: 'InventoryUnitPriceCell',
      props: {
        priceField: 'price',
        unitField: 'selectedBandKind',
      },
    },
  },
  {
    field: 'gainLossAmount',
    title: '金额',
    width: 112,
    align: 'right',
    headerAlign: 'center',
    cellRender: {
      name: 'InventoryTotalAmountCell',
      props: {
        amountField: 'gainLossAmount',
      },
    },
  },
  {
    field: 'lotNumber',
    title: '批号',
    width: 120,
    showOverflow: true,
  },
  {
    field: 'traceNo',
    title: '追溯码',
    minWidth: 140,
    editRender: { name: 'InventoryTraceNoCell', props: { maxlength: 512, clearable: true } },
  },
  {
    field: 'reason',
    title: '变更原因',
    minWidth: 160,
    editRender: { name: 'InventoryPersistElInput', props: { maxlength: 200, clearable: true } },
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

const GAIN_LOSS_EDITABLE_COLUMN_FIELDS = new Set(['_qtyUnit', 'medicinePick', 'reason', 'traceNo']);

const GAIN_LOSS_EDIT_FIELD_ORDER = ['medicinePick', '_qtyUnit', 'traceNo', 'reason'] as const;

type GainLossEditField = (typeof GAIN_LOSS_EDIT_FIELD_ORDER)[number];

const GAIN_LOSS_VERTICAL_NAV_FIELDS = new Set<GainLossEditField>(['_qtyUnit', 'reason', 'traceNo']);

const gainLossGridKeyboardRef = ref<HTMLElement | null>(null);

const [Grid, gridApi] = useVbenVxeGrid<GainLossPreviewRow>({
  gridOptions,
  gridEvents: {
    cellClick: (params: { column?: { field?: string; type?: string }; row: GainLossPreviewRow }) =>
      gainLossGridEdit.onCellClick(params),
    editClosed(params: { row: GainLossPreviewRow }) {
      recalcGainLossLine(params.row);
      gainLossGridEdit.syncRowFormMode(params.row);
    },
  },
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
});
bindInvDocCompactColumns({
  baseColumns: columns,
  gridOptions,
  getGrid: () =>
    gridApi?.grid as { loadColumn?: (cols: VxeGridPropTypes.Column[]) => void } | null | undefined,
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
    firstFillFieldAfterPick: '_qtyUnit',
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
      showIcon: documentEditable.value,
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

const traceScanBridgeRef = ref<InstanceType<typeof InvDocTraceScanBridge> | null>(null);

/** 打开批量追溯码扫码弹窗 */
function handleOpenTraceScan(): void {
  traceScanBridgeRef.value?.open(collectGridRows());
}

function resetDetailForCreate() {
  headerBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  pinnedPractitionerOptions.value = [];
  purposeLocationId.value = undefined;
  headItemTableType.value = firstItemTableTypeValue();
  practitionerId.value = resolveDefaultPractitionerIdForSelect(
    effectivePractitionerOptions.value,
    userStore.userInfo as null | Record<string, unknown>,
  );
  headApplyTime.value = formatDateTime(new Date());
  clearInventoryHeadFormValidation(headFormRef.value);
  gainLossGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchGainLossGridEditConfig();
  });
}

async function loadDetail(busNo: string, loadGen?: number) {
  gainLossGridEdit.resetFormMode();
  gainLossGridEdit.clearGridEdit();
  try {
    const res =
      props.variant === 'pharmacy'
        ? await getPharmacyGainLossDetailApi({ busNo })
        : props.variant === 'department'
          ? await getDeptGainLossDetailApi({ busNo })
          : await getGainLossDetailApi({ busNo });
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到损益单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    detailStatusEnum.value = normalizeStocktakeStatusSort(data.statusEnum);
    purposeLocationId.value = data.purposeLocationId ?? undefined;
    pinnedPractitionerOptions.value = [];
    const detailPractitionerId = data.practitionerId ?? data.applicantId;
    const detailPractitionerLabel =
      'applicantName' in data && data.applicantName ? String(data.applicantName) : undefined;
    pinPractitionerOption(detailPractitionerId, detailPractitionerLabel);
    practitionerId.value = coercePractitionerIdForSelect(
      detailPractitionerId,
      effectivePractitionerOptions.value,
    );
    headApplyTime.value = data.applyTime
      ? String(data.applyTime).includes('T')
        ? String(data.applyTime).replace('T', ' ')
        : String(data.applyTime)
      : formatDateTime(new Date());

    const gridRows = (data.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData(gridRows);
      patchGainLossGridEditConfig();
    });
  } catch {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchGainLossGridEditConfig();
    });
  }
}

function buildLinesPayload(
  rows: GainLossPreviewRow[],
): DeptGainLossLineDto[] | GainLossLineDto[] | PharmacyGainLossLineDto[] {
  const occurrenceTime = resolveOccurrenceTimeForApi();
  return rows.map((r) => {
    recalcGainLossLine(r);
    const qty = Number(r.gainLossQuantity);
    const sign = Number.isFinite(qty) && qty < 0 ? -1 : 1;
    const absQty = Number.isFinite(qty) ? Math.abs(qty) : 0;
    const persisted = persistDisplayQtyAsSplitForBands({
      bands: r.unitBands ?? [],
      displayQty: absQty,
      legacyUnitCode: r.unitCode,
      selectedBandKind: r.selectedBandKind,
    });
    const unitCode =
      resolveInboundLinePersistUnitCodeForBands({
        bands: r.unitBands ?? [],
        legacyUnitCode: r.unitCode,
        selectedBandKind: r.selectedBandKind,
      }) || String(r.unitCode ?? '').trim();
    const splitQty = persisted.itemQuantity * sign;
    const price = Number(r.price);
    const amountRaw = String(r.gainLossAmount ?? '').trim();
    const amountNum = Number(amountRaw);
    const gainLossAmount =
      Number.isFinite(amountNum) && amountRaw !== ''
        ? amountNum
        : Number.isFinite(qty) && Number.isFinite(price)
          ? inventoryLineTotalNumber(qty, price)
          : undefined;
    return {
      itemTable: r.itemTable,
      itemId: r.itemId,
      inventoryItemId:
        r.inventoryItemId === undefined || r.inventoryItemId === null
          ? undefined
          : r.inventoryItemId,
      gainLossQuantity: splitQty,
      unitCode: unitCode || undefined,
      lotNumber: String(r.lotNumber ?? '').trim() || undefined,
      traceNo: String(r.traceNo ?? '').trim() || undefined,
      reason: String(r.reason ?? '').trim() || undefined,
      price: Number.isFinite(price) && price > 0 ? price : undefined,
      gainLossAmount,
      occurrenceTime,
    };
  });
}

function validateBeforeSave(rows: GainLossPreviewRow[]): boolean {
  if (!isSpecificInventoryLocationId(purposeLocationId.value)) {
    warnInventoryLocationRequiredByLabel(props.locationFieldLabel);
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
    recalcGainLossLine(r);
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
    if (
      !canConvertDisplayQtyToSplitForBands(r.unitBands ?? [], Math.abs(qty), r.selectedBandKind)
    ) {
      ElMessage.warning(`第 ${i + 1} 行无法将数量换算为拆零单位，请检查计量带`);
      return false;
    }
  }
  return true;
}

/** 保存：新建走 create，已存在走 update；返回当前单号。 */
async function persistGainLoss(): Promise<string | undefined> {
  // 重入护栏：须在首行置 busy，避免双击在 await 前并发两次 create
  if (saving.value) return undefined;
  saving.value = true;
  try {
    if (!documentEditable.value) {
      ElMessage.warning('当前状态不允许修改');
      return undefined;
    }
    const rows = collectGridRows();
    if (!validateBeforeSave(rows)) return undefined;

    const lines = buildLinesPayload(rows);
    const common = {
      purposeLocationId: purposeLocationId.value as number | string,
      practitionerId: practitionerId.value as number | string,
      categoryEnum: categoryEnum.value,
      lines,
    };

    const existingBusNo = String(headerBusNo.value ?? '').trim();
    if (existingBusNo) {
      if (props.variant === 'pharmacy') {
        await updatePharmacyGainLossApi({ busNo: existingBusNo, ...common });
      } else if (props.variant === 'department') {
        await updateDeptGainLossApi({ busNo: existingBusNo, ...common });
      } else {
        await updateGainLossApi({ busNo: existingBusNo, ...common });
      }
      ElMessage.success('保存成功');
      await loadDetail(existingBusNo);
      emit('documentSaved', { busNo: existingBusNo });
      return existingBusNo;
    }

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
  // 在提交审批阶段继续置灰，避免保存结束后瞬间二次点击再跑一遍 save+submit
  if (saving.value) return;
  const no = await persistGainLoss();
  if (!no) return;
  saving.value = true;
  try {
    await handleSubmitApproval();
  } finally {
    saving.value = false;
  }
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
  if (!isStockOutDocumentRemovableStatus(detailStatusEnum.value)) {
    ElMessage.warning('仅待审核、驳回、已撤回单据可删除');
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
  try {
    const body = { busNo: no };
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
    // 前后端统一字段名 rejectReason（对齐订货单等库存单据）
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
  if (!(await validateInventoryHeadFormFields(headFormRef.value, 'purposeLocationId'))) {
    return;
  }
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

interface GainLossDraftSnap {
  purposeLocationId?: number | string;
  practitionerId?: number | string;
  headItemTableType: string;
  headApplyTime: string;
  rows: GainLossPreviewRow[];
}

const parentSync = useInvDocParentSyncSession<GainLossDraftSnap>();

/** 侧栏切单：缓存/恢复本地草稿，避免已落库明细污染新建单 */
async function syncFromParentContext() {
  await parentSync.syncFromParentContext(
    { isDraftOrder: Boolean(props.isDraftOrder), parentBusNo: props.busNo },
    {
      getLocalPersistedBusNo: () => headerBusNo.value,
      captureDraft: () => ({
        purposeLocationId: purposeLocationId.value,
        practitionerId: practitionerId.value,
        headItemTableType: headItemTableType.value,
        headApplyTime: headApplyTime.value,
        rows: collectGridRows().map((r) => ({ ...r })),
      }),
      restoreDraft: async (snap) => {
        headerBusNo.value = undefined;
        detailStatusEnum.value = undefined;
        purposeLocationId.value = snap.purposeLocationId;
        practitionerId.value = snap.practitionerId;
        headItemTableType.value = snap.headItemTableType;
        headApplyTime.value = snap.headApplyTime;
        gainLossGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
        patchGainLossGridEditConfig();
      },
      resetForCreate: () => {
        resetDetailForCreate();
      },
      loadByBusNo: async (busNo, gen) => {
        await loadDetail(busNo, gen);
      },
    },
  );
}

watch(
  () => props.resetKey,
  () => {
    parentSync.discardDraftSession();
  },
);

watch(
  () => [props.busNo, props.isDraftOrder, props.resetKey] as const,
  () => {
    void syncFromParentContext();
  },
  { immediate: true },
);

watch(
  normalizedItemTableTypeOptions,
  (opts) => {
    if (opts.length === 0) return;
    const current = String(headItemTableType.value ?? '').trim();
    if (!current || !opts.some((o) => o.value === current)) {
      headItemTableType.value = resolveDefaultInventoryItemTableType(
        opts,
        preferredItemTableType(),
      );
    }
  },
  { immediate: true },
);

watch(
  effectivePractitionerOptions,
  (opts) => {
    if (opts.length === 0) return;
    const current = String(practitionerId.value ?? '').trim();
    if (!current || !opts.some((o) => o.value === current)) {
      practitionerId.value = resolveDefaultPractitionerIdForSelect(
        opts,
        userStore.userInfo as null | Record<string, unknown>,
      );
    }
  },
  { deep: true },
);

/** 解析下拉选项展示名 */
function resolvePrintOptionLabel(options: ApiOptionItem[] | undefined, value: unknown): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = options?.find((item) => String(item.value ?? '') === key);
  return String(hit?.label ?? '').trim();
}

/** 解析损益库房展示名 */
function resolvePrintLocationName(value: unknown): string {
  return resolvePrintOptionLabel(props.locationOptions, value);
}

/** 明细行当前计量单位展示文案 */
function resolveGainLossPrintUnitLabel(row: GainLossPreviewRow): string {
  const kind = String(row.selectedBandKind ?? '').trim();
  const byKind = row.unitList?.find((item) => String(item.value ?? '') === kind);
  if (byKind) {
    return String(byKind.label ?? byKind.value_dictText ?? '').trim();
  }
  return String(row.unitCodeDictText ?? row.unitCode ?? '').trim();
}

/** 打印损益单（盘点盈亏模版；损益量写入 actualQtyText） */
async function handlePrint() {
  const busNo = String(headerBusNo.value ?? '').trim();
  if (!busNo) {
    ElMessage.warning('请先保存单据后再打印');
    return;
  }
  const productRows = collectGridRows().filter((row) => isPresentItemId(row.itemId));
  if (productRows.length === 0) {
    ElMessage.warning('没有可打印的明细');
    return;
  }

  const locationName = resolvePrintLocationName(purposeLocationId.value);
  const itemTypeText = resolvePrintOptionLabel(
    normalizedItemTableTypeOptions.value,
    headItemTableType.value,
  );
  const practitionerName = resolvePrintOptionLabel(
    effectivePractitionerOptions.value,
    practitionerId.value,
  );
  const documentTitle = buildInvDocumentTitle(locationName, '损益单');
  const applyTimeText = String(headApplyTime.value ?? '')
    .trim()
    .replace('T', ' ');

  // 按当前网格展示值组装盈亏明细：损益量 → diffQty，原因 → reasonText
  const lineRows = productRows.map((row) => ({
    productName: String(row.productName ?? '').trim(),
    specText: String(row.spec ?? '').trim(),
    manufacturerText: String(row.manufacturer ?? '').trim(),
    lotNumber: String(row.lotNumber ?? '').trim(),
    diffQty: row.gainLossQuantity,
    unitLabel: resolveGainLossPrintUnitLabel(row),
    unitPrice: row.price,
    diffAmount: row.gainLossAmount,
    reasonText: String(row.reason ?? '').trim(),
  }));

  await printInvDocument({
    family: 'stocktake',
    source: {
      hospitalName: resolvePrintHospitalName(),
      documentTitle,
      docNo: busNo,
      applyTimeText,
      itemTypeText,
      sourceLocationText: locationName,
      destLocationText: '',
      practitionerName,
      lineRows,
      preparedByName: practitionerName,
      approvedByName: '',
    },
    bizRef: { busNo, docKind: 'gain-loss', variant: props.variant },
    jobName: documentTitle,
  });
}
</script>

<template>
  <div class="gain-loss-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
    <InvMainShell>
      <template #actionBar>
        <div class="flex w-full flex-wrap items-center justify-end gap-2">
          <InvDocActions
            require-finance-approval-role
            v-bind="docActionFlags"
            @approve="handleApprove"
            @print="handlePrint"
            @reject="handleReject"
            @remove-doc="handleRemoveDoc"
            @save="handleSaveDoc"
            @save-and-submit="handleSaveAndSubmitDoc"
            @submit-approval="handleSubmitApproval"
            @withdraw="handleWithdraw"
          />
        </div>
      </template>

      <div
        class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-2 pt-2"
        @mousedown="gainLossGridEdit.onSurfaceMouseDown"
      >
        <div :class="INV_DOC_HEAD_SECTION_CLASS">
          <SectionTitle :title="headSectionTitle" />
          <div class="mt-1 flex flex-wrap items-center gap-2">
            <span class="text-xs" style="color: var(--el-text-color-regular)">状态</span>
            <span class="text-sm" style="color: var(--el-text-color-primary)">{{
              headerStatusText || '—'
            }}</span>
          </div>
          <ElForm
            ref="headFormRef"
            :model="headFormModel"
            :rules="headFormRules"
            label-width="96px"
            class="compact-head-form gain-loss-head-form mt-1"
          >
            <div :class="INV_DOC_HEAD_FORM_GRID_CLASS">
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem :label="locationFieldLabel" prop="purposeLocationId">
                <ElSelect
                  v-model="purposeLocationId"
                  class="w-full"
                  filterable
                  clearable
                  placeholder="请选择"
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
                  placeholder="请选择经手人"
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="p in effectivePractitionerOptions"
                    :key="String(p.value)"
                    :label="p.label"
                    :value="p.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="项目类型">
                <ElSelect
                  v-model="headItemTableType"
                  class="w-full"
                  filterable
                  placeholder="请选择"
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="t in normalizedItemTableTypeOptions"
                    :key="String(t.value)"
                    :label="t.label"
                    :value="String(t.value)"
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
          class="gain-loss-main-pane__grid-wrap mt-2 flex min-h-0 min-w-0 flex-1 flex-col"
          @keydown.capture="gainLossGridEdit.onGridKeydown"
        >
          <Grid class="min-h-0 flex-1">
            <template #toolbar-actions>
              <InvDocGridToolbar
                v-if="documentEditable"
                show-trace-scan
                @add="addItem"
                @delete-selected="deleteItem('select')"
                @trace-scan="handleOpenTraceScan"
              />
            </template>
            <template #gainLossQtyHeader>
              <span class="inline-flex items-center justify-center gap-1">
                损益数量
                <ElTooltip content="可填负数：负数表示报损，正数表示报溢" placement="top">
                  <QuestionFilled
                    class="size-3.5 cursor-help text-[var(--el-text-color-secondary)]"
                  />
                </ElTooltip>
              </span>
            </template>
            <template #action="{ row }">
              <ElButton
                v-if="documentEditable"
                type="danger"
                text
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
  <InvDocTraceScanBridge
    ref="traceScanBridgeRef"
    scene="gainLoss"
    :default-location-id="purposeLocationId"
  />
</template>

<style scoped lang="scss">
.compact-head-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.compact-head-form :deep(.el-form-item__label) {
  padding-right: 8px;
}

.inv-doc-head-section {
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.gain-loss-main-pane__grid-wrap :deep(.vxe-grid) {
  min-height: 200px;
}
</style>
