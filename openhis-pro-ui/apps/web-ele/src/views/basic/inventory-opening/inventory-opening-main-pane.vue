<!--
  库存期初右侧主区：单头 + 明细（批号/效期/进价/追溯）+ 审批与关闭/作废/复制。
-->
<script setup lang="ts">
import type { VxeGridPropTypes } from '#/adapter/vxe-table';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocDetailGridShell from '@inventory/components/inv-doc-detail-grid-shell.vue';
import InvDocGridToolbar from '@inventory/components/inv-doc-grid-toolbar.vue';
import InvDocTraceScanBridge from '@inventory/components/inv-doc-trace-scan-bridge.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
import type { FormInstance } from 'element-plus';

import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElProgress,
  ElSelect,
} from 'element-plus';

import { useInvDocDetailGrid } from '#/composables/use-inv-doc-detail-grid';
import { useInvDocGridRowOps } from '#/composables/use-inv-doc-grid-row-ops';
import { useInvDocParentSyncSession } from '#/composables/use-inv-doc-parent-sync-session';
import { useInventoryDocGridEdit } from '#/composables/use-inventory-doc-grid-edit';
import {
  applyInventoryRowQtyUnitList,
  createInventoryQtyUnitColumn,
  hydrateInventoryDetailLineQtyForGrid,
  INV_DOC_HEAD_FORM_GRID_CLASS,
  INV_DOC_HEAD_SECTION_CLASS,
} from '#/utils/inventory/inventory-doc-grid-common';
import {
  agreeInventoryOpeningApi,
  closeInventoryOpeningApi,
  copyInventoryOpeningFromPreviousApi,
  createInventoryOpeningApi,
  getInventoryOpeningDetailApi,
  rejectInventoryOpeningApi,
  removeInventoryOpeningApi,
  submitInventoryOpeningApprovalApi,
  updateInventoryOpeningApi,
  voidInventoryOpeningApi,
  withdrawInventoryOpeningApprovalApi,
  type InventoryOpeningDetailLineDto,
  type InventoryOpeningLineDto,
} from '#/api/basic/inventory-opening';
import SectionTitle from '#/components/layouts/section-title.vue';
import { inboundReceiptPickMedicineCell } from '#/utils/inventory/inbound-receipt-product-pick-list';
import {
  inventoryPickNumberField,
  inventoryPickStringField,
} from '#/utils/inventory/inventory-product-pick-list';
import {
  formatInventoryLineTotal,
  formatMoneyDisplay,
  inventoryLineTotalNumber,
  sumMoneyThenSettle,
} from '#/utils/format/money-scale';
import {
  canConvertDisplayQtyToSplitForBands,
  convertInboundUnitPriceBetweenBandKinds,
  findInboundUnitBandByKind,
  handleInventoryUnitBandChanged,
  inboundUnitBandSelectOptions,
  normalizeInboundProductUnitBands,
  persistDisplayQtyAsSplitForBands,
  resolveInboundLinePersistUnitCodeForBands,
  warehouseNonInboundDefaultBandKind,
  warehouseNonInboundResolveDisplayUnitPrice,
  type CatalogTraceUnitHint,
  type InboundProductUnitBand,
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
  INV_ITEM_TABLE_TYPE_MEDICATION,
  isSpecificInventoryLocationId,
  normalizeInventoryItemTableTypeOptions,
  resolveDefaultInventoryItemTableType,
  resolveDefaultPractitionerIdForSelect,
  resolveDocHeadProductPickLocationId,
  warnInventoryLocationRequiredByLabel,
} from '#/utils/inventory/inventory-workbench-list';
import { useUserStore } from '@vben/stores';
import { WAREHOUSE_FORM_ENUM } from '#/api/basic/warehouse';

defineOptions({ name: 'BasicInventoryOpeningMainPane' });

interface MedicinePickCell {
  id?: number | string;
  name?: string;
}

interface OpeningPreviewRow {
  id: string;
  medicinePick?: MedicinePickCell | Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  productName: string;
  spec: string;
  manufacturer: string;
  categoryCode?: string;
  itemQuantity?: number;
  unitCode: string;
  unitCodeDictText?: string;
  selectedBandKind?: string;
  __prevBandKind?: string;
  unitList?: { label?: string; value: string; value_dictText?: string }[];
  unitBands?: InboundProductUnitBand[];
  /** 选品/详情目录提示（含游离追溯单位，供扫码应扫数） */
  productCatalog?: CatalogTraceUnitHint & { unitBands?: InboundProductUnitBand[] };
  lotNumber: string;
  prodExpiry?: [string, string] | null;
  traceNo: string;
  price?: number;
  /** 供应商主键（裁决 1A 必填） */
  supplierId?: number | string;
  /** 行金额展示（2 位） */
  totalAmount: string;
  /** 行金额计算值（6 位，合计用；与侧栏 sumThenSettle 对齐） */
  lineTotalPrice?: number;
}

interface Props {
  busNo?: string;
  isDraftOrder?: boolean;
  resetKey?: number;
  locationOptions: Array<ApiOptionItem & { formEnum?: string; openingClosed?: boolean }>;
  practitionerOptions?: ApiOptionItem[];
  supplierOptions?: ApiOptionItem[];
  itemTableTypeOptions?: ApiOptionItem[];
  draftLocationId?: number | string;
}

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  isDraftOrder: false,
  resetKey: 0,
  locationOptions: () => [],
  practitionerOptions: () => [],
  supplierOptions: () => [],
  itemTableTypeOptions: () => [],
  draftLocationId: undefined,
});

const emit = defineEmits<{
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
  locationsChanged: [];
}>();

const userStore = useUserStore();

const productPickGateLocationId = computed(() =>
  resolveDocHeadProductPickLocationId(locationId.value, props.locationOptions),
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

function firstItemTableTypeValue(): string {
  return resolveDefaultInventoryItemTableType(
    normalizedItemTableTypeOptions.value,
    INV_ITEM_TABLE_TYPE_MEDICATION,
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
const locationId = ref<number | string | undefined>(undefined);
const headFormRef = ref<FormInstance>();

/** 用 reactive 单头 model，避免 computed 每次新对象导致 ElForm 校验读到空值、添加行静默失败 */
const headFormModel = reactive({
  purposeLocationId: '' as number | string,
});

const headFormRules = computed(() =>
  buildInventoryLocationHeadFormRules({
    purposeLocationId: '目标库房',
  }),
);

const practitionerId = ref<number | string | undefined>(undefined);
const headItemTableType = ref(firstItemTableTypeValue());
const headOccurrenceTime = ref(formatDateTime(new Date()));
const saving = ref(false);

const selectedLocationMeta = computed(() => {
  const key = String(locationId.value ?? '').trim();
  if (!key) return undefined;
  return props.locationOptions.find((o) => String(o.value) === key);
});

const locationOpeningClosed = computed(() => Boolean(selectedLocationMeta.value?.openingClosed));
const isWarehouseLocation = computed(
  () => selectedLocationMeta.value?.formEnum === WAREHOUSE_FORM_ENUM.WAREHOUSE,
);

const isNewDraft = computed(() => !headerBusNo.value);
const documentEditable = computed(
  () =>
    !locationOpeningClosed.value &&
    (isNewDraft.value || isStockOutDocumentEditableStatus(detailStatusEnum.value)),
);

const headerStatusText = computed(() => {
  if (isNewDraft.value) return '编辑中';
  return stocktakeStatusLabel(detailStatusEnum.value);
});

const isCurrentAgreed = computed(() => normalizeStocktakeStatusSort(detailStatusEnum.value) === 3);

const docActionFlags = computed(() => {
  const base = buildStocktakeDocActionFlags({
    hasPersistedBusNo: Boolean(headerBusNo.value),
    isDraft: isNewDraft.value,
    status: detailStatusEnum.value,
  });
  const busy = saving.value;
  const closed = locationOpeningClosed.value;
  return {
    ...base,
    saveDisabled: base.saveDisabled || busy || !documentEditable.value || closed,
    saveAndSubmitDisabled: base.saveAndSubmitDisabled || busy || !documentEditable.value || closed,
    submitApprovalDisabled: base.submitApprovalDisabled || busy || closed,
    withdrawDisabled: base.withdrawDisabled || busy || closed,
    approveDisabled: base.approveDisabled || busy || closed,
    rejectDisabled: base.rejectDisabled || busy || closed,
    removeDisabled: base.removeDisabled || busy,
  };
});

function resolveOccurrenceTimeForApi(): string {
  const t = String(headOccurrenceTime.value ?? '').trim();
  if (t === '') return formatDateTime(new Date());
  return t.includes('T') ? t.replace('T', ' ') : t;
}

function parseApiDateToPicker(value: unknown): string {
  const s = String(value ?? '').trim();
  if (!s) return '';
  return s.includes('T') ? s.slice(0, 10) : s.slice(0, 10);
}

function mapStartEndToProdExpiry(startTime: unknown, endTime: unknown): [string, string] | null {
  const start = parseApiDateToPicker(startTime);
  const end = parseApiDateToPicker(endTime);
  if (!start && !end) return null;
  return [start || end, end || start];
}

function normalizeLineDateTimeForApi(dateStr: string): string | undefined {
  const s = String(dateStr ?? '').trim();
  if (!s) return undefined;
  if (s.includes('T') || s.includes(' ')) return s.replace('T', ' ');
  return `${s} 00:00:00`;
}

function mapProdExpiryToStartEnd(prodExpiry: [string, string] | null | undefined): {
  endTime?: string;
  startTime?: string;
} {
  if (!Array.isArray(prodExpiry) || prodExpiry.length < 2) return {};
  const startTime = normalizeLineDateTimeForApi(prodExpiry[0] ?? '');
  const endTime = normalizeLineDateTimeForApi(prodExpiry[1] ?? '');
  return {
    ...(startTime ? { startTime } : {}),
    ...(endTime ? { endTime } : {}),
  };
}

/** 选品写入明细：对齐进货 syncRowFromInboundMedicinePick（单位槽/进价；追溯单位取目录）。 */
function syncRowFromProductPick(pick: Record<string, unknown>, row: OpeningPreviewRow) {
  row.medicinePick =
    inboundReceiptPickMedicineCell(pick) ?? (pick as OpeningPreviewRow['medicinePick']);
  row.itemTable =
    inventoryPickStringField(pick, 'productTableName') ||
    inventoryPickStringField(pick, 'itemTable');
  const defId = pick.productDefinitionId ?? pick.itemId;
  row.itemId =
    defId === undefined || defId === null || String(defId).trim() === ''
      ? undefined
      : (defId as number | string);
  row.productName =
    inventoryPickStringField(pick, 'merchandiseName') ||
    inventoryPickStringField(pick, 'productName') ||
    inventoryPickStringField(pick, 'itemName');
  row.spec =
    inventoryPickStringField(pick, 'specification') ||
    inventoryPickStringField(pick, 'totalVolume');
  row.manufacturer = inventoryPickStringField(pick, 'manufacturer');
  row.categoryCode = inventoryPickStringField(pick, 'categoryCode');
  const bands = normalizeInboundProductUnitBands(pick.unitBands);
  row.unitBands = bands.length > 0 ? bands : undefined;
  const pickTraceCode = String(pick.traceUnitCode ?? '').trim();
  const pickTraceRatio = Number(pick.traceUnitRatio);
  row.productCatalog = {
    unitBands: bands.length > 0 ? bands : undefined,
    traceUnitCode: pickTraceCode || undefined,
    traceUnitRatio:
      Number.isFinite(pickTraceRatio) && pickTraceRatio > 0 ? pickTraceRatio : undefined,
  };
  if (bands.length > 0) {
    const kind = warehouseNonInboundDefaultBandKind(bands);
    row.selectedBandKind = kind;
    row.unitList = inboundUnitBandSelectOptions(bands);
    row.__prevBandKind = kind;
    const band = findInboundUnitBandByKind(bands, kind);
    const code = String(band?.unitCode ?? '').trim();
    if (code !== '') {
      row.unitCode = code;
    }
    const resolved = warehouseNonInboundResolveDisplayUnitPrice({
      bands,
      displayBandKind: kind,
      legacyUnitPrice:
        inventoryPickNumberField(pick, 'purchasePrice') ||
        inventoryPickNumberField(pick, 'purchaseUnitPrice') ||
        inventoryPickNumberField(pick, 'referencePurchasePrice'),
    });
    row.price = resolved ?? undefined;
  } else {
    row.selectedBandKind = 'LEGACY';
    row.unitList = [{ value: 'LEGACY', label: '单位' }];
    row.__prevBandKind = 'LEGACY';
    const unit = inventoryPickStringField(pick, 'unitCode');
    if (unit) {
      row.unitCode = unit;
    }
    applyInventoryRowQtyUnitList(row);
    const refPrice =
      inventoryPickNumberField(pick, 'purchaseUnitPrice') ||
      inventoryPickNumberField(pick, 'purchasePrice') ||
      inventoryPickNumberField(pick, 'referencePurchasePrice');
    if (refPrice > 0) {
      row.price = refPrice;
    }
  }
  recalcLine(row);
}

/** 切换计量带：数量按 ratioToSplit 换算（对齐进货）。 */
function handleOpeningUnitBandChanged(row: OpeningPreviewRow) {
  handleInventoryUnitBandChanged(row, {
    getQty: () => Number(row.itemQuantity ?? 0),
    setQty: (qty) => {
      row.itemQuantity = qty;
    },
    onAfterChanged: () => recalcLine(row),
  });
}

function recalcLine(row: OpeningPreviewRow) {
  const qty = Number(row.itemQuantity);
  const price = Number(row.price);
  if (Number.isFinite(qty) && Number.isFinite(price) && qty > 0) {
    row.lineTotalPrice = inventoryLineTotalNumber(qty, price);
    row.totalAmount = formatInventoryLineTotal(qty, price);
  } else {
    row.lineTotalPrice = undefined;
    row.totalAmount = '';
  }
  schedulePurchaseAmountSummaryRefresh();
}

function createEmptyRow(): OpeningPreviewRow {
  return {
    id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    medicinePick: undefined,
    itemTable: undefined,
    itemId: undefined,
    productName: '',
    spec: '',
    manufacturer: '',
    itemQuantity: undefined,
    unitCode: '',
    selectedBandKind: 'LEGACY',
    __prevBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: '单位' }],
    lotNumber: '',
    prodExpiry: null,
    traceNo: '',
    productCatalog: undefined,
    price: undefined,
    supplierId: undefined,
    totalAmount: '',
    lineTotalPrice: undefined,
  };
}

function mapDetailLineToGrid(ln: InventoryOpeningDetailLineDto, rowIdx: number): OpeningPreviewRow {
  const itemIdRaw = ln.itemId;
  const idStr =
    itemIdRaw !== undefined && itemIdRaw !== null
      ? `ln-item-${String(itemIdRaw)}-${rowIdx}`
      : `ln-x-${rowIdx}`;
  const name = String(ln.itemName ?? '').trim();
  const unitCodeStr = String(ln.unitCode ?? '');
  const rawQty = ln.itemQuantity === undefined ? undefined : Number(ln.itemQuantity);
  const catalog = ln.productCatalog;
  const unitCodeDictText = String(ln.unitCodeDictText ?? ln.unitCode ?? '');
  const catalogTraceCode = String(catalog?.traceUnitCode ?? '').trim();
  const catalogTraceRatio = Number(catalog?.traceUnitRatio);
  const row: OpeningPreviewRow = {
    id: idStr,
    medicinePick: name ? { id: itemIdRaw as number | string | undefined, name } : undefined,
    itemTable: ln.itemTable,
    itemId: itemIdRaw,
    productName: name,
    spec: String(ln.specification ?? ''),
    manufacturer: String(ln.manufacturer ?? ''),
    itemQuantity: rawQty,
    unitCode: unitCodeStr,
    unitCodeDictText,
    lotNumber: String(ln.lotNumber ?? ''),
    prodExpiry: mapStartEndToProdExpiry(ln.startTime, ln.endTime),
    traceNo: String(ln.traceNo ?? '').trim(),
    productCatalog: {
      unitBands: undefined,
      traceUnitCode: catalogTraceCode || undefined,
      traceUnitRatio:
        Number.isFinite(catalogTraceRatio) && catalogTraceRatio > 0 ? catalogTraceRatio : undefined,
    },
    price: ln.price === undefined ? undefined : Number(ln.price),
    supplierId:
      ln.supplierId !== undefined && ln.supplierId !== null && String(ln.supplierId).trim() !== ''
        ? String(ln.supplierId)
        : undefined,
    totalAmount: formatMoneyDisplay(ln.totalPrice ?? ''),
    lineTotalPrice:
      ln.totalPrice !== undefined && ln.totalPrice !== null && String(ln.totalPrice).trim() !== ''
        ? Number(ln.totalPrice)
        : undefined,
  };

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
  row.itemQuantity = hydrated.displayQty;
  row.unitCode = hydrated.unitCode;
  if (hydrated.unitCodeDictText) {
    row.unitCodeDictText = hydrated.unitCodeDictText;
  }
  if (hydrated.unitBands?.length) {
    row.unitBands = hydrated.unitBands;
    row.unitList = hydrated.unitList;
    row.selectedBandKind = hydrated.bandKind;
    row.__prevBandKind = hydrated.bandKind;
    if (row.productCatalog) {
      row.productCatalog.unitBands = hydrated.unitBands;
    }
    // 后端 price 恒为包装价 → 换算为当前操作单位展示价
    const displayPrice = warehouseNonInboundResolveDisplayUnitPrice({
      bands: hydrated.unitBands,
      displayBandKind: hydrated.bandKind,
      legacyUnitPrice: ln.price === undefined ? undefined : Number(ln.price),
    });
    if (displayPrice !== undefined) {
      row.price = displayPrice;
    }
  } else {
    applyInventoryRowQtyUnitList(row);
  }
  // 落库 totalPrice 为权威行额；加载时勿用展示价×数量覆盖（目录包装比不准会放大合计）
  const persistedTotal = Number(ln.totalPrice);
  if (Number.isFinite(persistedTotal) && String(ln.totalPrice ?? '').trim() !== '') {
    row.lineTotalPrice = persistedTotal;
    row.totalAmount = formatMoneyDisplay(ln.totalPrice ?? '');
    schedulePurchaseAmountSummaryRefresh();
  } else {
    recalcLine(row);
  }
  return row;
}

function isOpeningRowComplete(row: OpeningPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.itemQuantity);
  if (!Number.isFinite(qty) || qty <= 0) return false;
  if (!String(row.unitCode ?? '').trim()) return false;
  if (!String(row.lotNumber ?? '').trim()) return false;
  const { endTime } = mapProdExpiryToStartEnd(row.prodExpiry);
  if (!endTime) return false;
  return true;
}

let openingGridEdit!: ReturnType<typeof useInventoryDocGridEdit<OpeningPreviewRow>>;

function handleOpeningProductPick(row: OpeningPreviewRow) {
  void nextTick(async () => {
    await nextTick();
    await openingGridEdit.onProductSelected(row);
  });
}

/** 供应商下拉实时源（供 InventoryPersistElSelect.getOptions 读取） */
const supplierSelectOptions = ref<ApiOptionItem[]>([]);

watch(
  () => props.supplierOptions,
  (opts) => {
    supplierSelectOptions.value = (opts ?? []).map((o) => ({
      label: String(o.label ?? ''),
      value: String(o.value ?? ''),
    }));
  },
  { immediate: true, deep: true },
);

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
        listSource: 'inventory-opening',
        query: async () => ({
          productTableName: headItemTableType.value,
          locationId: productPickGateLocationId.value,
          purposeLocationId: productPickGateLocationId.value,
        }),
        onSelected: (product: Record<string, unknown>, row: OpeningPreviewRow) => {
          syncRowFromProductPick(product, row);
          handleOpeningProductPick(row);
        },
      },
    },
    showOverflow: true,
  },
  { field: 'spec', title: '规格', width: 110, showOverflow: true },
  { field: 'manufacturer', title: '厂家', minWidth: 100, showOverflow: true },
  createInventoryQtyUnitColumn({
    qtyField: 'itemQuantity',
    title: '数量',
    allowNegative: false,
    onUnitChange: (row) => {
      handleOpeningUnitBandChanged(row as OpeningPreviewRow);
    },
  }),
  {
    field: 'price',
    title: '进价',
    width: 128,
    align: 'right',
    headerAlign: 'center',
    editRender: {
      name: 'InventoryUnitPriceCell',
      // 期初单特例：进价展示/编辑 6 位；其它库存单据默认 2 位
      props: { priceField: 'price', unitField: 'selectedBandKind', precision: 6, displayDp: 6 },
    },
  },
  {
    field: 'supplierId',
    title: '供应商',
    minWidth: 140,
    showOverflow: true,
    editRender: {
      name: 'InventoryPersistElSelect',
      props: {
        clearable: false,
        filterable: true,
        placeholder: '必选',
        // 经 getOptions 读实时列表，避免 cloneDeep 列快照导致「有时有下拉、有时无数据」
        getOptions: () => supplierSelectOptions.value,
      },
    },
  },
  {
    field: 'totalAmount',
    title: '金额',
    width: 112,
    align: 'right',
    headerAlign: 'center',
    cellRender: {
      name: 'InventoryTotalAmountCell',
      props: { amountField: 'totalAmount' },
    },
  },
  {
    field: 'lotNumber',
    title: '批号',
    width: 120,
    showOverflow: true,
    editRender: {
      name: 'InventoryPersistElInput',
      props: { maxlength: 64, clearable: true },
    },
  },
  {
    field: 'prodExpiry',
    title: '生产日期~有效期至',
    minWidth: 240,
    showOverflow: true,
    editRender: {
      name: 'ElDatePicker',
      props: {
        type: 'daterange',
        valueFormat: 'YYYY-MM-DD',
        format: 'YYYY-MM-DD',
        rangeSeparator: '至',
        startPlaceholder: '生产日期',
        endPlaceholder: '有效期至',
        unlinkPanels: true,
        clearable: true,
        class: '!w-full min-w-[220px]',
      },
    },
  },
  {
    field: 'traceNo',
    title: '追溯码',
    minWidth: 140,
    editRender: { name: 'InventoryTraceNoCell', props: { maxlength: 512, clearable: true } },
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

const OPENING_EDITABLE_COLUMN_FIELDS = new Set([
  '_qtyUnit',
  'medicinePick',
  'price',
  'supplierId',
  'lotNumber',
  'prodExpiry',
  'traceNo',
]);

const OPENING_EDIT_FIELD_ORDER = [
  'medicinePick',
  '_qtyUnit',
  'lotNumber',
  'prodExpiry',
  'price',
  'supplierId',
  'traceNo',
] as const;

type OpeningEditField = (typeof OPENING_EDIT_FIELD_ORDER)[number];

const OPENING_VERTICAL_NAV_FIELDS = new Set<OpeningEditField>([
  '_qtyUnit',
  'lotNumber',
  'price',
  'traceNo',
]);

const {
  Grid,
  gridApi,
  bindGridWrap,
  gridEdit: openingGridEditFromHook,
  collectGridRows,
  patchGridEditConfig: patchOpeningGridEditConfig,
} = useInvDocDetailGrid<OpeningPreviewRow>({
  columns,
  documentEditable: () => documentEditable.value,
  beforeEditMethod: () => documentEditable.value,
  onEditClosed: (row) => recalcLine(row),
  config: {
    rowKey: (row) => row.id,
    productField: 'medicinePick',
    editFieldOrder: OPENING_EDIT_FIELD_ORDER,
    editableFields: OPENING_EDITABLE_COLUMN_FIELDS,
    verticalNavFields: OPENING_VERTICAL_NAV_FIELDS,
    firstFillFieldAfterPick: '_qtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isOpeningRowComplete,
  },
});

openingGridEdit = openingGridEditFromHook;

const detailLineCount = ref(0);
const purchaseTotalText = ref('');

function refreshPurchaseAmountSummary() {
  const rows = collectGridRows();
  const amounts: Array<number | string> = [];
  let lineCount = 0;
  for (const r of rows) {
    if (!isPresentItemId(r.itemId)) continue;
    lineCount++;
    if (r.lineTotalPrice !== undefined && Number.isFinite(r.lineTotalPrice)) {
      amounts.push(r.lineTotalPrice);
      continue;
    }
    const raw = String(r.totalAmount ?? '').trim();
    if (raw) amounts.push(raw);
  }
  detailLineCount.value = lineCount;
  // 与侧栏一致：先按行额（6 位）加总，再 settle 到 2 位；禁止「每行先 2 位再相加」
  purchaseTotalText.value =
    lineCount === 0 || amounts.length === 0 ? '' : formatMoneyDisplay(sumMoneyThenSettle(amounts));
}

let purchaseSummaryTimer: ReturnType<typeof setTimeout> | undefined;
function schedulePurchaseAmountSummaryRefresh() {
  if (purchaseSummaryTimer !== undefined) clearTimeout(purchaseSummaryTimer);
  purchaseSummaryTimer = setTimeout(() => {
    purchaseSummaryTimer = undefined;
    refreshPurchaseAmountSummary();
  }, 0);
}

const { addItem: addGridItem, deleteItem: deleteGridItem } = useInvDocGridRowOps({
  documentEditable: () => documentEditable.value,
  getGridApi: () => gridApi ?? null,
  gridEdit: openingGridEdit,
  createEmptyRow,
  hasProduct: (row) => isPresentItemId(row.itemId),
});

const traceScanBridgeRef = ref<InstanceType<typeof InvDocTraceScanBridge> | null>(null);

function handleOpenTraceScan(): void {
  traceScanBridgeRef.value?.open(collectGridRows());
}

async function addItem() {
  if (!documentEditable.value) return;
  if (!isSpecificInventoryLocationId(locationId.value)) {
    warnInventoryLocationRequiredByLabel('目标库房');
    void validateInventoryHeadFormFields(headFormRef.value, 'purposeLocationId');
    return;
  }
  const headOk = await validateInventoryHeadFormFields(headFormRef.value, 'purposeLocationId');
  if (!headOk) return;
  const before = collectGridRows().length;
  try {
    await addGridItem();
  } catch (e) {
    console.error(e);
    ElMessage.error('添加行失败');
    return;
  }
  if (collectGridRows().length <= before) {
    ElMessage.warning('添加行失败，请刷新页面后重试');
  }
}

async function deleteItem(target: 'select' | OpeningPreviewRow) {
  if (!documentEditable.value) return;
  if (target === 'select') {
    const checked =
      (
        gridApi?.grid as null | { getCheckboxRecords?: () => OpeningPreviewRow[] }
      )?.getCheckboxRecords?.() ?? [];
    if (checked.length === 0) {
      ElMessage.warning('请先勾选要删除的行');
      return;
    }
  }
  await deleteGridItem(target);
}

function resetDetailForCreate() {
  headerBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  pinnedPractitionerOptions.value = [];
  // 禁止把侧栏「全部库房」的 0 带进单头
  const draftLoc = props.draftLocationId;
  locationId.value = isSpecificInventoryLocationId(draftLoc) ? String(draftLoc) : undefined;
  headItemTableType.value = firstItemTableTypeValue();
  practitionerId.value = resolveDefaultPractitionerIdForSelect(
    effectivePractitionerOptions.value,
    userStore.userInfo as null | Record<string, unknown>,
  );
  headOccurrenceTime.value = formatDateTime(new Date());
  clearInventoryHeadFormValidation(headFormRef.value);
  openingGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchOpeningGridEditConfig();
    schedulePurchaseAmountSummaryRefresh();
  });
}

async function loadDetail(busNo: string, loadGen?: number) {
  openingGridEdit.resetFormMode();
  openingGridEdit.clearGridEdit();
  try {
    const res = await getInventoryOpeningDetailApi({ busNo });
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到期初单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    detailStatusEnum.value = normalizeStocktakeStatusSort(data.statusEnum);
    locationId.value = isSpecificInventoryLocationId(data.locationId)
      ? String(data.locationId)
      : undefined;
    pinnedPractitionerOptions.value = [];
    pinPractitionerOption(data.practitionerId);
    practitionerId.value = coercePractitionerIdForSelect(
      data.practitionerId,
      effectivePractitionerOptions.value,
    );
    const occ = data.occurrenceTime ?? data.applyTime;
    headOccurrenceTime.value = occ
      ? String(occ).includes('T')
        ? String(occ).replace('T', ' ')
        : String(occ)
      : formatDateTime(new Date());

    const gridRows = (data.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData(gridRows);
      patchOpeningGridEditConfig();
      schedulePurchaseAmountSummaryRefresh();
    });
  } catch {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchOpeningGridEditConfig();
      schedulePurchaseAmountSummaryRefresh();
    });
  }
}

function buildLinesPayload(rows: OpeningPreviewRow[]): InventoryOpeningLineDto[] {
  return rows.map((r) => {
    const qty = Number(r.itemQuantity);
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
    const { endTime, startTime } = mapProdExpiryToStartEnd(r.prodExpiry);
    // 界面可按操作单位展示单价；落库 price 恒为包装单位价（与 unitCode 无关）
    const packagePrice = resolveOpeningPersistPackageUnitPrice(r);
    const displayPrice = Number(r.price);
    const amountRaw = String(r.totalAmount ?? '').trim();
    const amountNum = Number(amountRaw);
    const totalPrice =
      r.lineTotalPrice !== undefined && Number.isFinite(r.lineTotalPrice)
        ? r.lineTotalPrice
        : Number.isFinite(amountNum) && amountRaw !== ''
          ? amountNum
          : Number.isFinite(qty) && Number.isFinite(displayPrice)
            ? inventoryLineTotalNumber(qty, displayPrice)
            : undefined;
    return {
      itemTable: r.itemTable,
      itemId: r.itemId,
      lotNumber: String(r.lotNumber ?? '').trim() || undefined,
      startTime,
      endTime,
      itemQuantity: persisted.itemQuantity,
      unitCode: unitCode || undefined,
      price: packagePrice,
      totalPrice,
      traceNo: String(r.traceNo ?? '').trim() || undefined,
      supplierId:
        r.supplierId !== undefined && r.supplierId !== null && String(r.supplierId).trim() !== ''
          ? r.supplierId
          : undefined,
    };
  });
}

/** 将当前计量带展示单价换算为包装单位价落库；无带时原样。 */
function resolveOpeningPersistPackageUnitPrice(row: OpeningPreviewRow): number | undefined {
  const price = Number(row.price);
  if (!Number.isFinite(price) || price <= 0) return undefined;
  const bands = row.unitBands ?? [];
  const kind = String(row.selectedBandKind ?? '')
    .trim()
    .toUpperCase();
  if (!kind || kind === 'LEGACY' || kind === 'PACKAGE' || bands.length === 0) {
    return price;
  }
  const pkg = convertInboundUnitPriceBetweenBandKinds(price, kind, 'PACKAGE', bands);
  return pkg !== undefined && Number.isFinite(pkg) && pkg > 0 ? pkg : price;
}

function validateBeforeSave(rows: OpeningPreviewRow[], requireLines: boolean): boolean {
  if (locationOpeningClosed.value) {
    ElMessage.warning('该库房期初已关闭，不可再改写');
    return false;
  }
  if (!isSpecificInventoryLocationId(locationId.value)) {
    warnInventoryLocationRequiredByLabel('目标库房');
    return false;
  }
  if (requireLines && rows.length === 0) {
    ElMessage.warning('请至少添加一行明细后再保存');
    return false;
  }
  for (const [i, r] of rows.entries()) {
    recalcLine(r);
    if (!String(r.itemTable ?? '').trim() || !isPresentItemId(r.itemId)) {
      ElMessage.warning(`第 ${i + 1} 行请选择项目`);
      return false;
    }
    const qty = Number(r.itemQuantity);
    if (!Number.isFinite(qty) || qty <= 0) {
      ElMessage.warning(`第 ${i + 1} 行数量须大于 0`);
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
    const { endTime } = mapProdExpiryToStartEnd(r.prodExpiry);
    if (!endTime) {
      ElMessage.warning(`第 ${i + 1} 行有效期不能为空`);
      return false;
    }
    if (isWarehouseLocation.value) {
      const price = Number(r.price);
      if (!Number.isFinite(price) || price <= 0) {
        ElMessage.warning(`第 ${i + 1} 行药库期初进价必填`);
        return false;
      }
    }
    if (r.supplierId === undefined || r.supplierId === null || String(r.supplierId).trim() === '') {
      ElMessage.warning(`第 ${i + 1} 行供应商必填`);
      return false;
    }
    if (
      !canConvertDisplayQtyToSplitForBands(r.unitBands ?? [], Math.abs(qty), r.selectedBandKind)
    ) {
      ElMessage.warning(`第 ${i + 1} 行无法将数量换算为拆零单位，请检查计量带`);
      return false;
    }
  }
  // 单内唯一 (itemTable, itemId, lotNumber)：同品不同批号允许多行
  const seen = new Map<string, number>();
  for (const [i, r] of rows.entries()) {
    const key = `${String(r.itemTable)}\u0001${String(r.itemId)}\u0001${String(r.lotNumber).trim()}`;
    const at = seen.get(key);
    if (at !== undefined) {
      ElMessage.warning(
        `「${r.productName || '项目'}」批号「${r.lotNumber}」与第 ${at + 1} 行重复`,
      );
      return false;
    }
    seen.set(key, i);
  }
  return true;
}

async function persistOpening(requireLines = false): Promise<string | undefined> {
  if (saving.value) return undefined;
  saving.value = true;
  try {
    if (!documentEditable.value) {
      ElMessage.warning(locationOpeningClosed.value ? '该库房期初已关闭' : '当前状态不允许修改');
      return undefined;
    }
    const okHead = await validateInventoryHeadFormFields(headFormRef.value, 'purposeLocationId');
    if (!okHead) return undefined;
    const rows = collectGridRows().filter(
      (r) => isPresentItemId(r.itemId) || String(r.productName ?? '').trim() !== '',
    );
    if (!validateBeforeSave(rows, requireLines)) return undefined;

    const lines = buildLinesPayload(rows);
    const common = {
      locationId: locationId.value as number | string,
      practitionerId:
        practitionerId.value === undefined ||
        practitionerId.value === null ||
        String(practitionerId.value).trim() === ''
          ? undefined
          : practitionerId.value,
      occurrenceTime: resolveOccurrenceTimeForApi(),
      lines,
    };

    const existingBusNo = String(headerBusNo.value ?? '').trim();
    if (existingBusNo) {
      await updateInventoryOpeningApi({ busNo: existingBusNo, ...common });
      ElMessage.success('保存成功');
      await loadDetail(existingBusNo);
      emit('documentSaved', { busNo: existingBusNo });
      return existingBusNo;
    }

    const res = await createInventoryOpeningApi(common);
    const no = res.data?.busNo;
    ElMessage.success('新建成功');
    if (no) {
      headerBusNo.value = no;
      detailStatusEnum.value = 1;
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
  // 裁决 2B：落库保存须至少一行（本地空草稿可不调接口）
  await persistOpening(true);
}

async function handleSaveAndSubmitDoc() {
  if (saving.value) return;
  const no = await persistOpening(true);
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
    await ElMessageBox.confirm('确认删除整张期初单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await removeInventoryOpeningApi({ busNo: no });
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
  if (locationOpeningClosed.value) {
    ElMessage.warning('该库房期初已关闭，不可提交');
    return;
  }
  try {
    const res = await submitInventoryOpeningApprovalApi({ busNo: no });
    if (res.data?.statusEnum !== undefined && res.data.statusEnum !== null) {
      detailStatusEnum.value = normalizeStocktakeStatusSort(res.data.statusEnum);
    } else {
      detailStatusEnum.value = 2;
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
    await withdrawInventoryOpeningApprovalApi({ busNo: no });
    ElMessage.success('已撤回');
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch {
    /* handled */
  }
}

const agreeProgressVisible = ref(false);
const agreeProgressPercent = ref(0);
const agreeProgressStatus = ref<'exception' | 'success' | undefined>();
const agreeProgressHint = ref('');
let agreeProgressTimer: ReturnType<typeof setInterval> | undefined;

function clearAgreeProgressTimer() {
  if (agreeProgressTimer !== undefined) {
    clearInterval(agreeProgressTimer);
    agreeProgressTimer = undefined;
  }
}

function startAgreeProgress(lineCount: number) {
  clearAgreeProgressTimer();
  agreeProgressPercent.value = 2;
  agreeProgressStatus.value = undefined;
  agreeProgressHint.value =
    lineCount > 0
      ? `正在入账 ${lineCount} 行明细（库存 / 批号价 / 追溯），请勿关闭页面…`
      : '正在入账，请勿关闭页面…';
  agreeProgressVisible.value = true;
  // 无服务端进度时，缓步逼近 90%，完成后跳到 100%
  agreeProgressTimer = setInterval(() => {
    const cur = agreeProgressPercent.value;
    if (cur >= 90) return;
    const step = Math.max(1, Math.ceil((90 - cur) / 12));
    agreeProgressPercent.value = Math.min(90, cur + step);
  }, 500);
}

function finishAgreeProgress(ok: boolean) {
  clearAgreeProgressTimer();
  agreeProgressPercent.value = 100;
  agreeProgressStatus.value = ok ? 'success' : 'exception';
  agreeProgressHint.value = ok ? '入账完成' : '入账失败，请查看错误提示后重试';
  window.setTimeout(
    () => {
      agreeProgressVisible.value = false;
    },
    ok ? 600 : 1600,
  );
}

async function handleApprove() {
  const no = headerBusNo.value;
  if (!no) return;
  if (locationOpeningClosed.value) {
    ElMessage.warning('该库房期初已关闭，不可同意');
    return;
  }
  if (saving.value || agreeProgressVisible.value) return;
  try {
    await ElMessageBox.confirm(
      '同意后将以本单覆盖该库当前有效期初结果（若有前单将先作废回滚）。确认同意？',
      '审批同意',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  saving.value = true;
  startAgreeProgress(detailLineCount.value);
  try {
    await agreeInventoryOpeningApi({ busNo: no });
    finishAgreeProgress(true);
    ElMessage.success('已同意入账');
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch {
    finishAgreeProgress(false);
    /* handled */
  } finally {
    saving.value = false;
  }
}

async function handleReject() {
  const no = headerBusNo.value;
  if (!no) return;
  let reason = '';
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因（可选）', '驳回', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputPlaceholder: '驳回原因',
    });
    reason = String(value ?? '').trim();
  } catch {
    return;
  }
  try {
    await rejectInventoryOpeningApi({ busNo: no, reason: reason || undefined });
    ElMessage.success('已驳回');
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch {
    /* handled */
  }
}

async function handleCopyFromPrevious() {
  if (locationOpeningClosed.value) {
    ElMessage.warning('该库房期初已关闭，不可复制');
    return;
  }
  if (!isSpecificInventoryLocationId(locationId.value)) {
    warnInventoryLocationRequiredByLabel('目标库房');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '将复制该库上一张有效/指定源单明细为新草稿。源单不变。确认继续？',
      '从上一张复制',
      { type: 'info' },
    );
  } catch {
    return;
  }
  saving.value = true;
  try {
    const sourceBusNo =
      isCurrentAgreed.value || normalizeStocktakeStatusSort(detailStatusEnum.value) === 11
        ? headerBusNo.value
        : undefined;
    const res = await copyInventoryOpeningFromPreviousApi({
      locationId: locationId.value as number | string,
      sourceBusNo: sourceBusNo || undefined,
    });
    const no = res.data?.busNo;
    if (!no) {
      ElMessage.warning('复制失败：未返回单号');
      return;
    }
    ElMessage.success('已复制为新草稿');
    emit('documentSaved', { busNo: no });
    await loadDetail(no);
  } catch {
    /* handled */
  } finally {
    saving.value = false;
  }
}

async function handleCloseOpening() {
  if (!isSpecificInventoryLocationId(locationId.value)) {
    warnInventoryLocationRequiredByLabel('目标库房');
    return;
  }
  if (locationOpeningClosed.value) {
    ElMessage.info('该库房期初已关闭');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '关闭后该库不可再新建/提交/同意/复制期初。关前须无审核中单据。确认关闭？',
      '关闭期初',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  try {
    await closeInventoryOpeningApi({ locationId: locationId.value as number | string });
    ElMessage.success('已关闭期初');
    emit('locationsChanged');
  } catch {
    /* handled */
  }
}

async function handleVoidOpening() {
  const no = headerBusNo.value;
  if (!no) {
    ElMessage.warning('请先选择当前有效期初单');
    return;
  }
  if (!isCurrentAgreed.value) {
    ElMessage.warning('仅可对手工作废当前已同意的有效单');
    return;
  }
  if (locationOpeningClosed.value) {
    ElMessage.warning('该库房期初已关闭，不可作废');
    return;
  }
  let reason = '';
  try {
    const { value } = await ElMessageBox.prompt(
      '作废将回滚该单库存/追溯（批号价保留）。请输入原因（可选）',
      '作废当前有效单',
      {
        confirmButtonText: '作废',
        cancelButtonText: '取消',
        type: 'warning',
        inputPlaceholder: '作废原因',
      },
    );
    reason = String(value ?? '').trim();
  } catch {
    return;
  }
  try {
    await voidInventoryOpeningApi({ busNo: no, reason: reason || undefined });
    ElMessage.success('已作废');
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch {
    /* handled */
  }
}

interface OpeningDraftSnap {
  locationId?: number | string;
  practitionerId?: number | string;
  headItemTableType: string;
  headOccurrenceTime: string;
  rows: OpeningPreviewRow[];
}

const parentSync = useInvDocParentSyncSession<OpeningDraftSnap>();

/** 侧栏切单：缓存/恢复本地草稿 */
async function syncFromParentContext() {
  await parentSync.syncFromParentContext(
    { isDraftOrder: Boolean(props.isDraftOrder), parentBusNo: props.busNo },
    {
      getLocalPersistedBusNo: () => headerBusNo.value,
      captureDraft: () => ({
        locationId: locationId.value,
        practitionerId: practitionerId.value,
        headItemTableType: headItemTableType.value,
        headOccurrenceTime: headOccurrenceTime.value,
        rows: collectGridRows().map((r) => ({ ...r })),
      }),
      restoreDraft: async (snap) => {
        headerBusNo.value = undefined;
        detailStatusEnum.value = undefined;
        locationId.value = isSpecificInventoryLocationId(snap.locationId)
          ? String(snap.locationId)
          : undefined;
        practitionerId.value = snap.practitionerId;
        headItemTableType.value = snap.headItemTableType;
        headOccurrenceTime.value = snap.headOccurrenceTime;
        openingGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
        patchOpeningGridEditConfig();
        schedulePurchaseAmountSummaryRefresh();
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
  locationId,
  (v) => {
    // 侧栏「全部」占位不得残留在单头
    if (v !== undefined && v !== null && !isSpecificInventoryLocationId(v)) {
      locationId.value = undefined;
      headFormModel.purposeLocationId = '';
      return;
    }
    headFormModel.purposeLocationId = v === undefined || v === null ? '' : (v as number | string);
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
        INV_ITEM_TABLE_TYPE_MEDICATION,
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
</script>

<template>
  <div class="inventory-opening-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
    <InvMainShell>
      <template #actionBar>
        <div class="flex w-full flex-wrap items-center justify-end gap-2">
          <ElButton :disabled="saving || locationOpeningClosed" @click="handleCopyFromPrevious">
            从上一张复制
          </ElButton>
          <ElButton :disabled="saving || locationOpeningClosed" @click="handleCloseOpening">
            关闭期初
          </ElButton>
          <ElButton
            type="danger"
            plain
            :disabled="saving || !isCurrentAgreed || locationOpeningClosed"
            @click="handleVoidOpening"
          >
            作废当前有效
          </ElButton>
          <InvDocActions
            v-bind="docActionFlags"
            @approve="handleApprove"
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
        @mousedown="openingGridEdit.onSurfaceMouseDown"
      >
        <div :class="INV_DOC_HEAD_SECTION_CLASS">
          <SectionTitle title="库存期初单" />
          <div class="mt-1 flex flex-wrap items-center gap-2">
            <span class="text-xs" style="color: var(--el-text-color-regular)">状态</span>
            <span class="text-sm" style="color: var(--el-text-color-primary)">{{
              headerStatusText || '—'
            }}</span>
            <span
              v-if="detailLineCount > 0"
              class="text-xs"
              style="color: var(--el-text-color-regular)"
            >
              明细 {{ detailLineCount }} 行
            </span>
            <span
              v-if="purchaseTotalText"
              class="text-sm font-medium"
              style="color: var(--el-text-color-primary)"
            >
              进价合计 {{ purchaseTotalText }} 元
            </span>
            <span
              v-if="locationOpeningClosed"
              class="text-xs"
              style="color: var(--el-color-danger)"
            >
              该库房期初已关闭（只读）
            </span>
          </div>
          <ElForm
            ref="headFormRef"
            :model="headFormModel"
            :rules="headFormRules"
            label-width="96px"
            class="compact-head-form inventory-opening-head-form mt-1"
          >
            <div :class="INV_DOC_HEAD_FORM_GRID_CLASS">
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem label="目标库房" prop="purposeLocationId">
                <ElSelect
                  v-model="locationId"
                  class="w-full"
                  filterable
                  clearable
                  placeholder="请选择药库/药房/耗材房"
                  :disabled="!documentEditable"
                  @clear="locationId = undefined"
                >
                  <ElOption
                    v-for="loc in locationOptions"
                    :key="String(loc.value)"
                    :label="loc.label"
                    :value="String(loc.value)"
                    :disabled="Boolean(loc.openingClosed) && isNewDraft"
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
              <ElFormItem label="业务时点">
                <ElDatePicker
                  v-model="headOccurrenceTime"
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

        <InvDocDetailGridShell
          :bind-grid-wrap="bindGridWrap"
          :on-grid-keydown="openingGridEdit.onGridKeydown"
        >
          <Grid>
            <template #toolbar-actions>
              <InvDocGridToolbar
                :disabled="!documentEditable"
                show-trace-scan
                @add="addItem"
                @delete-selected="deleteItem('select')"
                @trace-scan="handleOpenTraceScan"
              />
            </template>
            <template #action="{ row }">
              <ElButton
                type="danger"
                text
                :disabled="!documentEditable"
                @click="deleteItem(row as OpeningPreviewRow)"
              >
                <Trash2 class="size-4" />
                删除
              </ElButton>
            </template>
          </Grid>
        </InvDocDetailGridShell>
      </div>
    </InvMainShell>
  </div>
  <!-- 反查走 inbound（可建新码）；码数硬 Gate 已取消，弹窗应扫数仅提示 -->
  <InvDocTraceScanBridge
    ref="traceScanBridgeRef"
    scene="inbound"
    :hard-scan-count-gate="false"
    lot-field="lotNumber"
    :default-location-id="locationId"
  />
  <ElDialog
    v-model="agreeProgressVisible"
    title="审批入账中"
    width="420px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="agreeProgressStatus === 'exception'"
    append-to-body
  >
    <div class="flex flex-col gap-3 py-1">
      <ElProgress
        :percentage="agreeProgressPercent"
        :status="agreeProgressStatus"
        :stroke-width="16"
        striped
        striped-flow
      />
      <p class="text-sm" style="color: var(--el-text-color-regular)">{{ agreeProgressHint }}</p>
    </div>
  </ElDialog>
</template>

<style scoped lang="scss">
.compact-head-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.compact-head-form :deep(.el-form-item__label) {
  padding-right: 8px;
}
</style>
