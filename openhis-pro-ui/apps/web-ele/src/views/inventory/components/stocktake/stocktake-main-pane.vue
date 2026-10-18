<!--
  盘库/盘点单右侧主区：与进货单/入库单同构（工作站 + VXE 明细 + 审批操作条）。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { ApiOptionItem } from '#/types/api-response';
import type { InboundProductUnitBand } from '#/utils/inventory/inbound-product-unit-bands';
import type { FormInstance } from 'element-plus';

import { computed, nextTick, reactive, ref, watch, onBeforeUnmount, onDeactivated } from 'vue';

import { Trash2 } from '@zxplat/icons';
import { useUserStore } from '@zxplat/stores';
import { formatDateTime } from '@zxplat/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocAmountSummaryBar from '@inventory/components/inv-doc-amount-summary-bar.vue';
import InvDocGridToolbar from '@inventory/components/inv-doc-grid-toolbar.vue';
import InvDocTraceScanBridge from '@inventory/components/inv-doc-trace-scan-bridge.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { useFinanceApprovalViewSummary } from '#/composables/use-finance-approval-view-summary';
import { bindInvDocCompactColumns } from '#/composables/use-inv-doc-compact-columns';
import { useInventoryDocGridEdit } from '#/composables/use-inventory-doc-grid-edit';
import { useInvDocParentSyncSession } from '#/composables/use-inv-doc-parent-sync-session';
import {
  clearInvDocDraftCacheAfterSave,
  INV_DOC_DRAFT_SCENE,
} from '#/utils/inventory/inv-doc-draft-cache';
import {
  agreePharmacyStocktakeApi,
  createPharmacyStocktakeApi,
  getPharmacyStocktakeDetailApi,
  pageProductsForPharmacyStocktakeApi,
  PHA_STOCKTAKE_CATEGORY_ENUM,
  rejectPharmacyStocktakeApi,
  removePharmacyStocktakeApi,
  submitPharmacyStocktakeApprovalApi,
  updatePharmacyStocktakeApi,
  withdrawPharmacyStocktakeApprovalApi,
  type PharmacyStocktakeLineDto,
  type PharmacyStocktakeLocationOption,
} from '#/api/inventory/pharmacy/stocktake';
import {
  agreeDeptStocktakeApi,
  createDeptStocktakeApi,
  getDeptStocktakeDetailApi,
  pageProductsForDeptStocktakeApi,
  DEP_STOCKTAKE_CATEGORY_ENUM,
  rejectDeptStocktakeApi,
  removeDeptStocktakeApi,
  submitDeptStocktakeApprovalApi,
  updateDeptStocktakeApi,
  withdrawDeptStocktakeApprovalApi,
  type DeptStocktakeLineDto,
  type DeptStocktakeLocationOption,
} from '#/api/inventory/department/stocktake';
import {
  agreeWarehouseStocktakeApi,
  createWarehouseStocktakeApi,
  getWarehouseStocktakeDetailApi,
  pageProductsForWarehouseStocktakeApi,
  WHS_STOCKTAKE_CATEGORY_ENUM,
  rejectWarehouseStocktakeApi,
  removeWarehouseStocktakeApi,
  submitWarehouseStocktakeApprovalApi,
  updateWarehouseStocktakeApi,
  withdrawWarehouseStocktakeApprovalApi,
  type StocktakeLineDto,
  type WarehouseStocktakeLocationOption,
} from '#/api/inventory/warehouse/stocktake';
import SectionTitle from '#/components/layouts/section-title.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { decimalAdd, decimalSub } from '#/utils/format/decimal-util';
import { formatInventoryLineTotal, formatPurchaseMoneyDisplay } from '#/utils/format/money-scale';
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
  applyWarehouseNonInboundBandKindToRow,
  canConvertDisplayQtyToSplitForBands,
  convertInboundQtyBetweenBandKinds,
  inboundUnitBandSelectOptions,
  normalizeInboundProductUnitBands,
  persistDisplayQtyAsSplitForBands,
  warehouseNonInboundDefaultBandKind,
  hydrateWarehouseDetailQtyFromPersisted,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  inventoryPickAvailableQtyAsStockDisplay,
  inventoryPickMedicineCell,
  inventoryPickNumberField,
  inventoryPickStringField,
  mapInventoryProductPickListRow,
} from '#/utils/inventory/inventory-product-pick-list';
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
  resolveInvDocApprovedByName,
  resolveInvDocPreparedByName,
  resolveInvDocPrintApplyTimeText,
  warnInventoryLocationRequiredByLabel,
} from '#/utils/inventory/inventory-workbench-list';
import { matchSelectOptionKeyword } from '#/utils/select-option-keyword';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import {
  applyInventoryRowCategoryCode,
  INV_DOC_HEAD_FORM_GRID_CLASS,
  INV_DOC_HEAD_SECTION_CLASS,
} from '#/utils/inventory/inventory-doc-grid-common';
import { refreshInventoryDocGridRow } from '#/utils/inventory/inventory-doc-grid-edit';
import { buildInvDocumentTitle, printInvDocument } from '#/utils/print/print-inv-document';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

defineOptions({ name: 'InventoryStocktakeMainPane' });

interface MedicinePickCell {
  id?: number | string;
  name?: string;
}

interface StocktakePreviewRow {
  id: string;
  medicinePick?: MedicinePickCell | Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  inventoryItemId?: number | string;
  /** 药品分类码；中药=4 时进价三位展示 */
  categoryCode?: string;
  spec: string;
  manufacturer: string;
  lotNumber: string;
  traceNo: string;
  bookQuantity: number;
  actualQuantity: number;
  profitLossQuantity: number;
  unitCode: string;
  unitBands?: InboundProductUnitBand[];
  selectedBandKind?: string;
  unitList?: { label?: string; value: string }[];
  __prevBandKind?: string;
  price: number;
  actualAmount: string;
  profitLossAmount: string;
  remark: string;
  _occurrenceTime?: string;
}

interface Props {
  /** 侧栏当前单据行（制单人/审批人与队列卡片同口径） */
  queueCardRow?: null | Record<string, unknown>;
  variant: 'department' | 'pharmacy' | 'warehouse';
  busNo?: string;
  isDraftOrder?: boolean;
  /** 侧栏草稿稳定 id（_clientDraftId），用于 session 缓存隔离 */
  clientDraftId?: string;
  resetKey?: number;
  sidebarPurposeLocationId?: number | string;
  locationOptions:
    | DeptStocktakeLocationOption[]
    | PharmacyStocktakeLocationOption[]
    | WarehouseStocktakeLocationOption[];
  itemTableTypeOptions?: ApiOptionItem[];
  practitionerOptions?: ApiOptionItem[];
  /** 单头区库房标签 */
  locationFieldLabel?: string;
  /** 单头区标题 */
  headSectionTitle?: string;
}

const props = withDefaults(defineProps<Props>(), {
  queueCardRow: null,
  busNo: '',
  isDraftOrder: false,
  clientDraftId: '',
  resetKey: 0,
  sidebarPurposeLocationId: undefined,
  locationOptions: () => [],
  itemTableTypeOptions: () => [],
  practitionerOptions: () => [],
  locationFieldLabel: '盘点库房',
  headSectionTitle: '盘点单信息',
});

const emit = defineEmits<{
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

const userStore = useUserStore();

const pinnedPractitionerOptions = ref<ApiOptionItem[]>([]);

const effectivePractitionerOptions = computed(() =>
  buildInventoryDocPractitionerSelectOptions(props.practitionerOptions, {
    pinned: pinnedPractitionerOptions.value,
    userInfo: userStore.userInfo as null | Record<string, unknown>,
  }),
);

/** 经手人下拉过滤关键字（名称/拼音/五笔） */
const practitionerFilterKeyword = ref('');

/** 经手人下拉：按关键字过滤后的选项 */
const displayedPractitionerOptions = computed(() => {
  const kw = practitionerFilterKeyword.value;
  return effectivePractitionerOptions.value.filter((o) => matchSelectOptionKeyword(o, kw));
});

/** 经手人 ElSelect 输入过滤 */
function handlePractitionerFilter(query: string) {
  practitionerFilterKeyword.value = query;
}

/** 经手人下拉关闭时清空过滤，避免再次打开仍收窄 */
function handlePractitionerDropdownVisible(open: boolean) {
  if (!open) {
    practitionerFilterKeyword.value = '';
  }
}

/** 详情经手人不在下拉时临时 pin，保证回显 id 留在选项内（避免被登录人兜底覆盖）。 */
function pinPractitionerOption(id: unknown, label?: string) {
  const v = String(id ?? '').trim();
  if (v === '') return;
  let lbl = String(label ?? '').trim();
  // 无姓名时尝试从当前下拉取 label，仍没有则用 id 占位
  if (lbl === '' || lbl === v) {
    const hit = effectivePractitionerOptions.value.find((o) => o.value === v);
    if (hit?.label) lbl = hit.label;
  }
  if (effectivePractitionerOptions.value.some((o) => o.value === v)) return;
  pinnedPractitionerOptions.value = [{ label: lbl !== '' ? lbl : v, value: v }];
}

/** 选品门禁：优先单头「盘点药库」，与侧栏「全部」筛选解耦。 */
const productPickGateLocationId = computed(() =>
  resolveDocHeadProductPickLocationId(
    purposeLocationId.value,
    props.locationOptions as { locationId?: unknown; value?: unknown }[],
  ),
);

const categoryEnum = computed(() => {
  if (props.variant === 'pharmacy') return PHA_STOCKTAKE_CATEGORY_ENUM;
  if (props.variant === 'department') return DEP_STOCKTAKE_CATEGORY_ENUM;
  return WHS_STOCKTAKE_CATEGORY_ENUM;
});

const normalizedItemTableTypeOptions = computed(() =>
  normalizeInventoryItemTableTypeOptions(props.itemTableTypeOptions),
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

const headItemTableType = ref(firstItemTableTypeValue());
const practitionerId = ref<number | string | undefined>(undefined);
const saving = ref(false);

const isNewDraft = computed(() => !headerBusNo.value);
const { fromFinanceApproval, showFinanceApprovalSummary, financeApprovalSummaryItems } =
  useFinanceApprovalViewSummary();

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
    saveDisabled: base.saveDisabled || busy,
    saveAndSubmitDisabled: base.saveAndSubmitDisabled || busy,
    // 已落库单据才可打印
    printDisabled: busy || !hasPersistedBusNo,
  };
});

const paneFlashActive = ref(false);
let paneFlashTimer: ReturnType<typeof setTimeout> | undefined;

function triggerPaneFlash() {
  if (paneFlashTimer !== undefined) clearTimeout(paneFlashTimer);
  paneFlashActive.value = true;
  paneFlashTimer = setTimeout(() => {
    paneFlashActive.value = false;
    paneFlashTimer = undefined;
  }, 420);
}

/** 盘点数量仅允许整数。 */
function toStocktakeQty(value: unknown): number {
  const n = Number(value);
  if (!Number.isFinite(n)) return 0;
  return Math.round(n);
}

/** 切换盘点计量带：当前库存/实盘/盈亏数量与单价随带换算（默认 STOCK，保存转 SPLIT）。 */
function handleStocktakeUnitBandChanged(row: StocktakePreviewRow) {
  const newK = String(row.selectedBandKind ?? '')
    .trim()
    .toUpperCase();
  const oldK = String(row.__prevBandKind ?? '')
    .trim()
    .toUpperCase();
  const bands = row.unitBands ?? [];
  if (oldK && newK && oldK !== newK && oldK !== 'LEGACY' && newK !== 'LEGACY' && bands.length > 0) {
    const nActual = convertInboundQtyBetweenBandKinds(row.actualQuantity, oldK, newK, bands);
    if (nActual !== undefined) {
      row.actualQuantity = toStocktakeQty(nActual);
    }
    const nBook = convertInboundQtyBetweenBandKinds(row.bookQuantity, oldK, newK, bands);
    if (nBook !== undefined) {
      row.bookQuantity = toStocktakeQty(nBook);
    }
    const nPl = convertInboundQtyBetweenBandKinds(row.profitLossQuantity, oldK, newK, bands);
    if (nPl !== undefined) {
      row.profitLossQuantity = toStocktakeQty(nPl);
    }
  }
  applyWarehouseNonInboundBandKindToRow(row, newK);
  row.__prevBandKind = newK;
  recalcStocktakeLineFromActual(row);
}

/** 详情回显：持久化金额原样格式化，禁止 load 时用单价×数量重算覆盖后端 total_price。 */
function formatStocktakeDetailPersistedAmount(total: unknown, categoryCode?: unknown): string {
  if (total === undefined || total === null || total === '') return '';
  const n = Number(total);
  if (!Number.isFinite(n)) return String(total).trim();
  return formatPurchaseMoneyDisplay(n, categoryCode);
}

function refreshStocktakeLineAmounts(row: StocktakePreviewRow) {
  const actual = toStocktakeQty(row.actualQuantity);
  const pl = toStocktakeQty(row.profitLossQuantity);
  const price = Number(row.price);
  const actualOk = Number.isFinite(actual);
  const plOk = Number.isFinite(pl);
  const priceOk = Number.isFinite(price);
  row.actualAmount = actualOk && priceOk ? formatInventoryLineTotal(actual, price) : '';
  row.profitLossAmount = plOk && priceOk ? formatInventoryLineTotal(pl, price) : '';
}

/** 编辑实盘：盈亏 = 实盘 − 当前库存；实盘 < 0 时钳制为 0。返回是否发生钳制。 */
function recalcStocktakeLineFromActual(row: StocktakePreviewRow): boolean {
  const book = toStocktakeQty(row.bookQuantity);
  const rawActual = toStocktakeQty(row.actualQuantity);
  const bookOk = Number.isFinite(book);
  const actualOk = Number.isFinite(rawActual);
  let clamped = false;
  if (bookOk && actualOk) {
    const actual = rawActual < 0 ? 0 : rawActual;
    if (actual !== rawActual) {
      row.actualQuantity = actual;
      clamped = true;
    }
    row.profitLossQuantity = Number(decimalSub(actual, book, 0));
  } else {
    row.profitLossQuantity = 0;
  }
  refreshStocktakeLineAmounts(row);
  return clamped;
}

/** 编辑盈亏：实盘 = 当前库存 + 盈亏；盘亏超过当前库存时钳制盈亏为 −当前库存。返回是否发生钳制。 */
function recalcStocktakeLineFromProfitLoss(row: StocktakePreviewRow): boolean {
  const book = toStocktakeQty(row.bookQuantity);
  const rawPl = toStocktakeQty(row.profitLossQuantity);
  const bookOk = Number.isFinite(book);
  const plOk = Number.isFinite(rawPl);
  let clamped = false;
  if (bookOk && plOk) {
    const minPl = -book;
    const pl = rawPl < minPl ? minPl : rawPl;
    if (pl !== rawPl) {
      row.profitLossQuantity = pl;
      clamped = true;
    }
    row.actualQuantity = Number(decimalAdd(book, pl, 0));
  }
  refreshStocktakeLineAmounts(row);
  return clamped;
}

function stocktakeRowIndex(row: StocktakePreviewRow): number {
  const rows = collectGridRows();
  const idx = rows.findIndex((r) => r.id === row.id);
  return idx >= 0 ? idx : 0;
}

function warnStocktakeQtyClamped(rowIndex: number): void {
  ElMessage.warning(`第 ${rowIndex + 1} 行盘亏不能超过当前库存`);
}

/** 实盘数量变更（常驻表单无 editClosed，须即时钳制并刷新输入框）。 */
async function applyStocktakeActualQtyChange(row: StocktakePreviewRow): Promise<void> {
  const clamped = recalcStocktakeLineFromActual(row);
  if (clamped) {
    warnStocktakeQtyClamped(stocktakeRowIndex(row));
    await refreshInventoryDocGridRow(gridApi, row);
  }
}

/** 盈亏数量变更：盘亏超过当前库存时钳制为 −当前库存并刷新展示。 */
async function applyStocktakePlQtyChange(row: StocktakePreviewRow): Promise<void> {
  const clamped = recalcStocktakeLineFromProfitLoss(row);
  if (clamped) {
    warnStocktakeQtyClamped(stocktakeRowIndex(row));
    await refreshInventoryDocGridRow(gridApi, row);
  }
}

/** 选品回填当前库存：与下拉「库存数量」列同源（stockAvailableQty），避免 availableQuantity 缺失时赋 0。 */
function resolveStocktakeBookQtyFromPick(pick: Record<string, unknown>): number {
  if (
    Object.prototype.hasOwnProperty.call(pick, 'stockAvailableQty') &&
    pick.stockAvailableQty !== undefined &&
    pick.stockAvailableQty !== null
  ) {
    return toStocktakeQty(inventoryPickNumberField(pick, 'stockAvailableQty'));
  }
  return toStocktakeQty(inventoryPickAvailableQtyAsStockDisplay(pick));
}

function syncRowFromStocktakePick(pick: Record<string, unknown>, row: StocktakePreviewRow) {
  row.medicinePick =
    inventoryPickMedicineCell(pick) ?? (pick as StocktakePreviewRow['medicinePick']);
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
  row.spec = inventoryPickStringField(pick, 'specification');
  row.manufacturer = inventoryPickStringField(pick, 'manufacturer');
  row.lotNumber = inventoryPickStringField(pick, 'lotNumber');
  applyInventoryRowCategoryCode(row as unknown as Record<string, unknown>, pick);

  const bookQty = resolveStocktakeBookQtyFromPick(pick);
  row.bookQuantity = bookQty;
  row.actualQuantity = bookQty;

  const bands = normalizeInboundProductUnitBands(pick.unitBands);
  row.unitBands = bands.length > 0 ? bands : undefined;
  if (bands.length > 0) {
    const kind = warehouseNonInboundDefaultBandKind(bands);
    row.selectedBandKind = kind;
    row.unitList = inboundUnitBandSelectOptions(bands);
    row.__prevBandKind = kind;
    applyWarehouseNonInboundBandKindToRow(
      row,
      kind,
      inventoryPickNumberField(pick, 'purchaseUnitPrice'),
    );
  } else {
    row.selectedBandKind = 'LEGACY';
    row.unitList = [{ value: 'LEGACY', label: '单位' }];
    row.__prevBandKind = 'LEGACY';
    row.unitCode = '';
    row.price = inventoryPickNumberField(pick, 'purchaseUnitPrice');
  }
  recalcStocktakeLineFromActual(row);
}

function createEmptyStocktakeRow(): StocktakePreviewRow {
  return {
    id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    medicinePick: undefined,
    itemTable: undefined,
    itemId: undefined,
    inventoryItemId: undefined,
    spec: '',
    manufacturer: '',
    lotNumber: '',
    traceNo: '',
    bookQuantity: 0,
    actualQuantity: 0,
    profitLossQuantity: 0,
    unitCode: '',
    selectedBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: '单位' }],
    __prevBandKind: 'LEGACY',
    price: 0,
    actualAmount: '',
    profitLossAmount: '',
    remark: '',
  };
}

function mapDetailLineToGrid(
  ln: {
    itemTable?: string;
    itemId?: number | string;
    productName?: string;
    specification?: string;
    manufacturer?: string;
    lotNumber?: string;
    traceNo?: string;
    unitCode?: string;
    bookQuantity?: number;
    profitLossQuantity?: number;
    actualQuantity?: number;
    actualAmount?: number;
    profitLossAmount?: number;
    price?: number;
    remake?: string;
    occurrenceTime?: string;
    productCatalog?: unknown;
    lineId?: number | string;
  },
  rowIdx: number,
): StocktakePreviewRow {
  const itemIdRaw = ln.itemId;
  const idStr =
    ln.lineId !== undefined && ln.lineId !== null && String(ln.lineId) !== ''
      ? `ln-${ln.lineId}`
      : itemIdRaw !== undefined && itemIdRaw !== null
        ? `ln-item-${String(itemIdRaw)}-${rowIdx}`
        : `ln-x-${rowIdx}`;
  const name = String(ln.productName ?? '');
  const unitCodeStr = String(ln.unitCode ?? '');
  const catalog = ln.productCatalog as Record<string, unknown> | null | undefined;
  const bands = normalizeInboundProductUnitBands(catalog?.unitBands, catalog?.unitBandsJson);

  const base: StocktakePreviewRow = {
    id: idStr,
    medicinePick: name ? { id: itemIdRaw as number | string, name } : undefined,
    itemTable: ln.itemTable === undefined ? undefined : String(ln.itemTable),
    itemId:
      itemIdRaw === undefined || itemIdRaw === null ? undefined : (itemIdRaw as number | string),
    spec: String(ln.specification ?? ''),
    manufacturer: String(ln.manufacturer ?? ''),
    lotNumber: String(ln.lotNumber ?? '').trim(),
    traceNo: String(ln.traceNo ?? '').trim(),
    bookQuantity: toStocktakeQty(ln.bookQuantity),
    actualQuantity: toStocktakeQty(ln.actualQuantity),
    profitLossQuantity: toStocktakeQty(ln.profitLossQuantity),
    unitCode: unitCodeStr,
    selectedBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: unitCodeStr.trim() === '' ? '单位' : unitCodeStr }],
    price: ln.price === undefined || ln.price === null ? 0 : Number(ln.price),
    actualAmount: formatStocktakeDetailPersistedAmount(ln.actualAmount, catalog?.categoryCode),
    profitLossAmount: formatStocktakeDetailPersistedAmount(
      ln.profitLossAmount,
      catalog?.categoryCode,
    ),
    remark: String(ln.remake ?? ''),
    _occurrenceTime: ln.occurrenceTime,
  };

  if (bands.length > 0) {
    base.unitBands = bands;
    const hydrateQty = (splitQty: unknown) =>
      hydrateWarehouseDetailQtyFromPersisted({
        bands,
        persistedItemQuantity: Number(splitQty ?? 0),
        persistedUnitCode: unitCodeStr,
      });
    const actualLoaded = hydrateQty(ln.actualQuantity);
    base.actualQuantity = toStocktakeQty(actualLoaded.displayQty);
    base.selectedBandKind = actualLoaded.bandKind;
    base.unitCode = actualLoaded.unitCode;
    base.__prevBandKind = actualLoaded.bandKind;

    const bookLoaded = hydrateQty(ln.bookQuantity);
    base.bookQuantity = toStocktakeQty(bookLoaded.displayQty);

    const plLoaded = hydrateQty(ln.profitLossQuantity);
    base.profitLossQuantity = toStocktakeQty(plLoaded.displayQty);

    base.unitList = inboundUnitBandSelectOptions(bands);
    applyWarehouseNonInboundBandKindToRow(base, actualLoaded.bandKind, ln.price);
    const lnPrice = ln.price === undefined || ln.price === null ? undefined : Number(ln.price);
    if (lnPrice !== undefined && Number.isFinite(lnPrice)) {
      base.price = lnPrice;
    }
  }

  applyInventoryRowCategoryCode(base as unknown as Record<string, unknown>, {
    productCatalog: catalog ?? undefined,
    categoryCode: catalog?.categoryCode,
  });
  return base;
}

function isPresentItemId(v: unknown): boolean {
  if (v === undefined || v === null) return false;
  if (typeof v === 'string') return v.trim() !== '';
  if (typeof v === 'number') return Number.isFinite(v);
  if (typeof v === 'bigint') return true;
  return String(v).trim() !== '';
}

/** 明细行必填项是否已齐（与 validateBeforeSave 保存校验对齐） */
function isStocktakeRowComplete(row: StocktakePreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  if (!String(row.unitCode ?? '').trim()) return false;
  const actual = Number(row.actualQuantity);
  const pl = Number(row.profitLossQuantity);
  if (!Number.isFinite(actual) || !Number.isFinite(pl)) return false;
  const bands = row.unitBands ?? [];
  if (!canConvertDisplayQtyToSplitForBands(bands, actual, row.selectedBandKind)) return false;
  return canConvertDisplayQtyToSplitForBands(bands, pl, row.selectedBandKind);
}

let stocktakeGridEdit!: ReturnType<typeof useInventoryDocGridEdit<StocktakePreviewRow>>;

function handleStocktakeProductPick(row: StocktakePreviewRow) {
  void nextTick(async () => {
    await nextTick();
    await stocktakeGridEdit.onProductSelected(row);
    await refreshInventoryDocGridRow(gridApi, row);
  });
}

const columns: VxeGridPropTypes.Column[] = [
  { type: 'checkbox', width: 48, fixed: 'left', align: 'center' },
  { type: 'seq', title: '序号', width: 52, fixed: 'left', align: 'center' },
  {
    field: 'medicinePick',
    title: '项目名称',
    minWidth: 180,
    fixed: 'left',
    editRender: {
      name: 'PurchaseOrderMedicineSelect',
      props: {
        listSource:
          props.variant === 'pharmacy'
            ? 'pharmacy-stocktake'
            : props.variant === 'department'
              ? 'dept-stocktake'
              : 'warehouse-stocktake',
        query: async () => ({
          productTableName: headItemTableType.value || firstItemTableTypeValue(),
          purposeLocationId: productPickGateLocationId.value,
        }),
        onSelected: (product: Record<string, unknown>, row: StocktakePreviewRow) => {
          const mergedPick =
            product?.stockAvailableQty !== undefined ||
            product?.availableQuantity !== undefined ||
            product?.unitBands
              ? product
              : ({
                  ...(row.medicinePick as Record<string, unknown> | undefined),
                  ...product,
                } as Record<string, unknown>);
          syncRowFromStocktakePick(mergedPick, row);
          handleStocktakeProductPick(row);
        },
      },
    },
    showOverflow: true,
  },
  { field: 'spec', title: '规格', width: 110, showOverflow: true },
  { field: 'manufacturer', title: '厂家', minWidth: 100, showOverflow: true },
  { field: 'lotNumber', title: '批号', width: 100, showOverflow: true },
  {
    field: 'traceNo',
    title: '追溯码',
    minWidth: 140,
    editRender: { name: 'InventoryTraceNoCell', props: { maxlength: 512, clearable: true } },
  },
  {
    field: '_bookQtyUnit',
    title: '当前库存',
    width: 158,
    align: 'center',
    headerAlign: 'center',
    cellRender: {
      name: 'InventoryQtyUnitReadonlyCell',
      props: {
        qtyField: 'bookQuantity',
        unitField: 'selectedBandKind',
      },
    },
  },
  {
    field: '_actualQtyUnit',
    title: '实盘数量',
    width: 168,
    align: 'center',
    headerAlign: 'center',
    editRender: {
      name: 'InventoryQtyUnitCell',
      props: {
        qtyField: 'actualQuantity',
        unitField: 'selectedBandKind',
        onUnitChange: (row: StocktakePreviewRow) => {
          handleStocktakeUnitBandChanged(row);
        },
        onQtyChange: (row: StocktakePreviewRow) => {
          void applyStocktakeActualQtyChange(row);
        },
      },
    },
  },
  {
    field: '_plQtyUnit',
    title: '盈亏数量',
    width: 168,
    align: 'center',
    headerAlign: 'center',
    editRender: {
      name: 'InventoryQtyUnitCell',
      props: {
        qtyField: 'profitLossQuantity',
        unitField: 'selectedBandKind',
        allowNegative: true,
        onUnitChange: (row: StocktakePreviewRow) => {
          handleStocktakeUnitBandChanged(row);
        },
        onQtyChange: (row: StocktakePreviewRow) => {
          void applyStocktakePlQtyChange(row);
        },
      },
    },
  },
  {
    field: 'price',
    title: '单价',
    width: 110,
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
    field: 'actualAmount',
    title: '实盘金额',
    width: 102,
    align: 'right',
    headerAlign: 'center',
    cellRender: {
      name: 'InventoryTotalAmountCell',
      props: {
        amountField: 'actualAmount',
      },
    },
  },
  {
    field: 'profitLossAmount',
    title: '盈亏金额',
    width: 102,
    align: 'right',
    headerAlign: 'center',
    cellRender: {
      name: 'InventoryTotalAmountCell',
      props: {
        amountField: 'profitLossAmount',
      },
    },
  },
  {
    field: 'remark',
    title: '备注',
    minWidth: 100,
    showOverflow: true,
    editRender: {
      name: 'InventoryPersistElInput',
      props: { maxlength: 200, clearable: true },
    },
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

const gridOptions: VxeGridProps<StocktakePreviewRow> = reactive({
  autoResize: true,
  height: '100%',
  border: true,
  stripe: true,
  showOverflow: true,
  showFooter: false,
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

const STOCKTAKE_EDITABLE_COLUMN_FIELDS = new Set([
  '_actualQtyUnit',
  '_plQtyUnit',
  'medicinePick',
  'remark',
  'traceNo',
]);

const STOCKTAKE_EDIT_FIELD_ORDER = [
  'medicinePick',
  '_actualQtyUnit',
  '_plQtyUnit',
  'traceNo',
  'remark',
] as const;

type StocktakeEditField = (typeof STOCKTAKE_EDIT_FIELD_ORDER)[number];

const STOCKTAKE_VERTICAL_NAV_FIELDS = new Set<StocktakeEditField>([
  '_actualQtyUnit',
  '_plQtyUnit',
  'remark',
  'traceNo',
]);

const stocktakeGridKeyboardRef = ref<HTMLElement | null>(null);

const [Grid, gridApi] = useVbenVxeGrid<StocktakePreviewRow>({
  gridOptions,
  gridEvents: {
    cellClick: (params: { column?: { field?: string; type?: string }; row: StocktakePreviewRow }) =>
      stocktakeGridEdit.onCellClick(params),
    editClosed(params: { row: StocktakePreviewRow; column?: { field?: string } }) {
      const f = params.column?.field;
      if (f === 'actualQuantity' || f === '_actualQtyUnit') {
        void applyStocktakeActualQtyChange(params.row);
      } else if (f === 'profitLossQuantity' || f === '_plQtyUnit') {
        void applyStocktakePlQtyChange(params.row);
      }
      stocktakeGridEdit.syncRowFormMode(params.row);
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

stocktakeGridEdit = useInventoryDocGridEdit<StocktakePreviewRow>({
  getGridApi: () => gridApi ?? null,
  gridWrapRef: stocktakeGridKeyboardRef,
  documentEditable: () => documentEditable.value,
  collectRows: collectGridRows,
  config: {
    rowKey: (row) => row.id,
    productField: 'medicinePick',
    editFieldOrder: STOCKTAKE_EDIT_FIELD_ORDER,
    editableFields: STOCKTAKE_EDITABLE_COLUMN_FIELDS,
    verticalNavFields: STOCKTAKE_VERTICAL_NAV_FIELDS,
    firstFillFieldAfterPick: '_actualQtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isStocktakeRowComplete,
  },
});

function patchStocktakeGridEditConfig() {
  const patched = stocktakeGridEdit.patchGridOptions({
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
    patchStocktakeGridEditConfig();
    void nextTick(() => {
      if (!editable) {
        stocktakeGridEdit.resetFormMode();
        stocktakeGridEdit.clearGridEdit();
      }
    });
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
  () => props.resetKey,
  () => {
    triggerPaneFlash();
  },
);

function collectGridRows(): StocktakePreviewRow[] {
  const g = gridApi?.grid as null | { getFullData?: () => StocktakePreviewRow[] };
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
  practitionerId.value = resolveDefaultPractitionerIdForSelect(
    effectivePractitionerOptions.value,
    userStore.userInfo as null | Record<string, unknown>,
  );
  clearInventoryHeadFormValidation(headFormRef.value);
  stocktakeGridEdit.resetFormMode();
  exitBatchMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchStocktakeGridEditConfig();
  });
}

async function loadDetail(busNo: string, loadGen?: number) {
  stocktakeGridEdit.resetFormMode();
  stocktakeGridEdit.clearGridEdit();
  exitBatchMode();
  try {
    const res =
      props.variant === 'pharmacy'
        ? await getPharmacyStocktakeDetailApi({ busNo })
        : props.variant === 'department'
          ? await getDeptStocktakeDetailApi({ busNo })
          : await getWarehouseStocktakeDetailApi({ busNo });
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到盘点单详情');
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

    const gridRows = (data.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    // 详情回显：单价/实盘金额/盈亏金额以后端 SupplyRequest 落库为准，不在 load 时重算
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData(gridRows);
      patchStocktakeGridEditConfig();
    });
  } catch {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchStocktakeGridEditConfig();
    });
  }
}

function buildLinesPayload(
  rows: StocktakePreviewRow[],
): StocktakeLineDto[] | PharmacyStocktakeLineDto[] | DeptStocktakeLineDto[] {
  const now = formatDateTime(new Date());
  return rows.map((r) => {
    recalcStocktakeLineFromActual(r);
    const displayActual = toStocktakeQty(r.actualQuantity);
    const displayPl = toStocktakeQty(r.profitLossQuantity);
    const persistedActual = persistDisplayQtyAsSplitForBands({
      bands: r.unitBands ?? [],
      displayQty: displayActual,
      legacyUnitCode: r.unitCode,
      selectedBandKind: r.selectedBandKind,
    });
    const persistedPl = persistDisplayQtyAsSplitForBands({
      bands: r.unitBands ?? [],
      displayQty: displayPl,
      legacyUnitCode: r.unitCode,
      selectedBandKind: r.selectedBandKind,
    });
    const actual = persistedActual.itemQuantity;
    const pl = persistedPl.itemQuantity;
    const actualAmt = Number(r.actualAmount);
    const plAmt = Number(r.profitLossAmount);
    return {
      itemTable: r.itemTable,
      itemId: r.itemId,
      profitLossQuantity: Number.isFinite(pl) ? pl : 0,
      actualQuantity: Number.isFinite(actual) ? actual : undefined,
      actualAmount: Number.isFinite(actualAmt) ? actualAmt : undefined,
      profitLossAmount: Number.isFinite(plAmt) ? plAmt : undefined,
      unitCode: String(persistedActual.unitCode ?? r.unitCode ?? '').trim() || undefined,
      lotNumber: String(r.lotNumber ?? '').trim() || undefined,
      traceNo: String(r.traceNo ?? '').trim(),
      inventoryItemId: r.inventoryItemId,
      price: Number.isFinite(Number(r.price)) ? Number(r.price) : undefined,
      remake: String(r.remark ?? '').trim() || undefined,
      occurrenceTime: r._occurrenceTime?.trim() || now,
    };
  });
}

function validateBeforeSave(rows: StocktakePreviewRow[]): boolean {
  if (!isSpecificInventoryLocationId(purposeLocationId.value)) {
    warnInventoryLocationRequiredByLabel(props.locationFieldLabel);
    return false;
  }
  if (!practitionerId.value) {
    ElMessage.warning('请选择经手人');
    return false;
  }
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一行盘点明细');
    return false;
  }
  for (const [i, r] of rows.entries()) {
    recalcStocktakeLineFromActual(r);
    const itemTable = String(r.itemTable ?? '').trim();
    if (
      !itemTable ||
      r.itemId === undefined ||
      r.itemId === null ||
      String(r.itemId).trim() === ''
    ) {
      ElMessage.warning(`第 ${i + 1} 行请选择项目`);
      return false;
    }
    const unitCode = String(r.unitCode ?? '').trim();
    if (!unitCode) {
      ElMessage.warning(`第 ${i + 1} 行缺少计量单位`);
      return false;
    }
    const actual = Number(r.actualQuantity);
    const pl = Number(r.profitLossQuantity);
    if (!Number.isFinite(actual)) {
      ElMessage.warning(`第 ${i + 1} 行实盘数量无效`);
      return false;
    }
    if (!Number.isFinite(pl)) {
      ElMessage.warning(`第 ${i + 1} 行盈亏数量无效`);
      return false;
    }
    const bands = r.unitBands ?? [];
    if (!canConvertDisplayQtyToSplitForBands(bands, actual, r.selectedBandKind)) {
      ElMessage.warning(`第 ${i + 1} 行实盘数量无法换算为拆零单位，请检查计量带`);
      return false;
    }
    if (!canConvertDisplayQtyToSplitForBands(bands, pl, r.selectedBandKind)) {
      ElMessage.warning(`第 ${i + 1} 行盈亏数量无法换算为拆零单位，请检查计量带`);
      return false;
    }
  }
  return true;
}

async function persistStocktake(): Promise<string | undefined> {
  if (!documentEditable.value) {
    ElMessage.warning('当前状态不允许修改');
    return undefined;
  }
  // 批量分页模式需汇总全部页（含缓存与未访问页默认值）
  const rows = batchMode.value ? await collectAllBatchModeRows() : collectGridRows();
  if (!validateBeforeSave(rows)) return undefined;

  const handlerId = practitionerId.value;
  if (!handlerId) {
    ElMessage.warning('请选择经手人');
    return undefined;
  }

  saving.value = true;
  try {
    const lines = buildLinesPayload(rows);
    const common = {
      purposeLocationId: purposeLocationId.value as number | string,
      practitionerId: String(handlerId),
      categoryEnum: categoryEnum.value,
      lines,
    };

    if (headerBusNo.value) {
      if (props.variant === 'pharmacy') {
        await updatePharmacyStocktakeApi({ busNo: headerBusNo.value, ...common });
      } else if (props.variant === 'department') {
        await updateDeptStocktakeApi({ busNo: headerBusNo.value, ...common });
      } else {
        await updateWarehouseStocktakeApi({ busNo: headerBusNo.value, ...common });
      }
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }

    const res =
      props.variant === 'pharmacy'
        ? await createPharmacyStocktakeApi(common)
        : props.variant === 'department'
          ? await createDeptStocktakeApi(common)
          : await createWarehouseStocktakeApi(common);
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

/** 落库成功后清 session 草稿缓存 */
function clearDraftPersistAfterSave(busNo?: string) {
  clearInvDocDraftCacheAfterSave({
    scene:
      props.variant === 'pharmacy'
        ? INV_DOC_DRAFT_SCENE.pharmacyStocktake
        : props.variant === 'department'
          ? INV_DOC_DRAFT_SCENE.deptStocktake
          : INV_DOC_DRAFT_SCENE.warehouseStocktake,
    clientDraftId: props.clientDraftId,
    busNo,
  });
  parentSync.clearDraftSnap();
}

async function handleSaveDoc() {
  const no = await persistStocktake();
  if (!no) return;
  clearDraftPersistAfterSave(no);
}

async function handleSaveAndSubmitDoc() {
  const no = await persistStocktake();
  if (!no) return;
  clearDraftPersistAfterSave(no);
  await handleSubmitApproval();
}

async function handleRemoveDoc() {
  if (isNewDraft.value) {
    try {
      await ElMessageBox.confirm('确认放弃该草稿？', '提示', { type: 'warning' });
    } catch {
      return;
    }
    clearInvDocDraftCacheAfterSave({
      scene:
        props.variant === 'pharmacy'
          ? INV_DOC_DRAFT_SCENE.pharmacyStocktake
          : props.variant === 'department'
            ? INV_DOC_DRAFT_SCENE.deptStocktake
            : INV_DOC_DRAFT_SCENE.warehouseStocktake,
      clientDraftId: props.clientDraftId,
    });
    parentSync.discardDraftSession();
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
    await ElMessageBox.confirm('确认删除整张盘点单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    if (props.variant === 'pharmacy') {
      await removePharmacyStocktakeApi({ busNo: no });
    } else if (props.variant === 'department') {
      await removeDeptStocktakeApi({ busNo: no });
    } else {
      await removeWarehouseStocktakeApi({ busNo: no });
    }
    clearDraftPersistAfterSave(no);
    ElMessage.success('已删除');
    resetDetailForCreate();
    emit('documentRemoved');
  } catch {
    /* 拦截器已提示 */
  }
}

async function handleSubmitApproval() {
  const no = headerBusNo.value;
  if (!no) return;
  try {
    const res =
      props.variant === 'pharmacy'
        ? await submitPharmacyStocktakeApprovalApi({ busNo: no })
        : props.variant === 'department'
          ? await submitDeptStocktakeApprovalApi({ busNo: no })
          : await submitWarehouseStocktakeApprovalApi({ busNo: no });
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
        ? await withdrawPharmacyStocktakeApprovalApi({ busNo: no })
        : props.variant === 'department'
          ? await withdrawDeptStocktakeApprovalApi({ busNo: no })
          : await withdrawWarehouseStocktakeApprovalApi({ busNo: no });
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
    if (props.variant === 'pharmacy') {
      await agreePharmacyStocktakeApi({ busNo: no });
    } else if (props.variant === 'department') {
      await agreeDeptStocktakeApi({ busNo: no });
    } else {
      await agreeWarehouseStocktakeApi({ busNo: no });
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
    if (props.variant === 'pharmacy') {
      await rejectPharmacyStocktakeApi({ busNo: no, changeReason: value });
    } else if (props.variant === 'department') {
      await rejectDeptStocktakeApi({ busNo: no, changeReason: value });
    } else {
      await rejectWarehouseStocktakeApi({ busNo: no, changeReason: value });
    }
    ElMessage.success('已驳回');
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch (e) {
    if (e === 'cancel') return;
  }
}

/** 解析下拉选项展示名 */
function resolvePrintOptionLabel(options: ApiOptionItem[] | undefined, value: unknown): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = options?.find((item) => String(item.value ?? '') === key);
  return String(hit?.label ?? '').trim();
}

/** 解析盘点库房展示名 */
function resolvePrintLocationName(value: unknown): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = (props.locationOptions ?? []).find((row) => String(row.locationId ?? '') === key);
  return String(hit?.displayName ?? hit?.locationId ?? '').trim();
}

/** 明细行项目名称（选品单元格） */
function resolveStocktakePrintProductName(row: StocktakePreviewRow): string {
  const pick = row.medicinePick;
  if (pick && typeof pick === 'object' && 'name' in pick) {
    return String((pick as { name?: string }).name ?? '').trim();
  }
  return '';
}

/** 明细行当前计量单位展示文案 */
function resolveStocktakePrintUnitLabel(row: StocktakePreviewRow): string {
  const kind = String(row.selectedBandKind ?? '').trim();
  const byKind = row.unitList?.find((item) => String(item.value ?? '') === kind);
  if (byKind) {
    return String(byKind.label ?? '').trim();
  }
  return String(row.unitCode ?? '').trim();
}

/** 打印或预览盘点单 */
async function handlePrint(payload?: { preview?: boolean }) {
  const busNo = String(headerBusNo.value ?? '').trim();
  if (!busNo) {
    ElMessage.warning('请先保存单据后再打印');
    return;
  }
  const gridRows = batchMode.value ? await collectAllBatchModeRows() : collectGridRows();
  const productRows = gridRows.filter((row) => isPresentItemId(row.itemId));
  if (productRows.length === 0) {
    ElMessage.warning('没有可打印的明细');
    return;
  }

  const locationName = resolvePrintLocationName(purposeLocationId.value);
  const itemTypeText = resolvePrintOptionLabel(
    normalizedItemTableTypeOptions.value,
    headItemTableType.value,
  );
  const documentTitle = buildInvDocumentTitle(locationName, '盘点单');
  // 打印制单时间取侧栏单据实际审批时间（盘点详情无单头 applyTime 时勿用打印时刻冒充）
  const applyTimeText = resolveInvDocPrintApplyTimeText({
    queueCardRow: props.queueCardRow as null | Record<string, unknown>,
  });

  const lineRows = productRows.map((row) => ({
    productName: resolveStocktakePrintProductName(row),
    specText: String(row.spec ?? '').trim(),
    manufacturerText: String(row.manufacturer ?? '').trim(),
    lotNumber: String(row.lotNumber ?? '').trim(),
    bookQty: row.bookQuantity,
    actualQty: row.actualQuantity,
    diffQty: row.profitLossQuantity,
    unitLabel: resolveStocktakePrintUnitLabel(row),
    unitPrice: row.price,
    actualAmount: row.actualAmount,
    diffAmount: row.profitLossAmount,
    reasonText: String(row.remark ?? '').trim(),
  }));

  const preparedByName = resolveInvDocPreparedByName(
    props.queueCardRow as null | Record<string, unknown>,
  );
  const approvedByName = resolveInvDocApprovedByName(
    props.queueCardRow as null | Record<string, unknown>,
  );
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
      lineRows,
      preparedByName,
      approvedByName,
    },
    bizRef: { busNo, docKind: 'stocktake', variant: props.variant },
    jobName: documentTitle,
    preview: payload?.preview === true,
  });
}

async function addItem() {
  if (!documentEditable.value) return;
  if (!(await validateInventoryHeadFormFields(headFormRef.value, 'purposeLocationId'))) {
    return;
  }
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const record = createEmptyStocktakeRow();
  const { row } = await $grid.insertAt(record, null);
  await $grid.$nextTick();
  if (row) {
    await $grid.scrollToRow(row);
    await stocktakeGridEdit.enterRowFormMode(row as StocktakePreviewRow, 'medicinePick');
  }
}

async function deleteItem(row: 'select' | StocktakePreviewRow) {
  if (!documentEditable.value) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const checked = $grid.getCheckboxRecords() as StocktakePreviewRow[];
  if (row === 'select' && checked.length === 0) return;
  await $grid.remove(row === 'select' ? checked : row);
  // 批量模式下同步当前页缓存，避免删行后切页又恢复
  if (batchMode.value) {
    flushCurrentBatchPageToCache();
  }
}

const BATCH_PAGE_SIZE = 10;

/** 批量盘点：分页加载库存行到明细表，并缓存各页已编辑数据 */
const batchMode = ref(false);
const batchPageNo = ref(1);
const batchTotal = ref(0);
const batchLoading = ref(false);
/** 各页快照（含用户改过的实盘/备注等），切页时回写/恢复 */
const batchPageCache = ref(new Map<number, StocktakePreviewRow[]>());

/** 深拷贝当前页明细，避免缓存与网格互相污染 */
function cloneStocktakeRows(rows: StocktakePreviewRow[]): StocktakePreviewRow[] {
  return rows.map((r) => ({
    ...r,
    medicinePick: r.medicinePick ? { ...(r.medicinePick as object) } : undefined,
    unitBands: r.unitBands?.map((b) => ({ ...b })),
    unitList: r.unitList?.map((u) => ({ ...u })),
  }));
}

/** 退出批量盘点分页模式，恢复整表编辑 */
function exitBatchMode() {
  batchMode.value = false;
  batchPageNo.value = 1;
  batchTotal.value = 0;
  batchPageCache.value = new Map();
  batchLoading.value = false;
}

/** 把当前页网格数据写入页缓存 */
function flushCurrentBatchPageToCache() {
  if (!batchMode.value) return;
  const pageNo = batchPageNo.value;
  batchPageCache.value.set(pageNo, cloneStocktakeRows(collectGridRows()));
}

/** 选品接口按 variant 分流 */
async function fetchStocktakePickPage(pageNo: number, pageSize: number) {
  const purposeLocationId = productPickGateLocationId.value;
  const productTableName = String(headItemTableType.value ?? '').trim();
  const body = {
    purposeLocationId: purposeLocationId as number | string,
    productTableName,
    pageNo,
    pageSize,
    // 批量盘点只拉账面量>0 的库存行，零库存不得入单；单行选品仍可含零库存
    positiveQuantityOnly: true,
  };
  if (props.variant === 'pharmacy') {
    return pageProductsForPharmacyStocktakeApi(body);
  }
  if (props.variant === 'department') {
    return pageProductsForDeptStocktakeApi(body);
  }
  return pageProductsForWarehouseStocktakeApi(body);
}

/** 选品行映射为盘点明细（实盘默认等于账面） */
function mapPickToStocktakeRow(pick: Record<string, unknown>): StocktakePreviewRow {
  const row = createEmptyStocktakeRow();
  syncRowFromStocktakePick(mapInventoryProductPickListRow(pick), row);
  return row;
}

/** 加载指定页：优先用缓存，否则请求选品接口 */
async function loadBatchPage(pageNo: number) {
  const cached = batchPageCache.value.get(pageNo);
  if (cached) {
    batchPageNo.value = pageNo;
    gridApi?.grid?.loadData(cloneStocktakeRows(cached));
    await enterBatchPageEditMode();
    return;
  }
  batchLoading.value = true;
  try {
    const res = await fetchStocktakePickPage(pageNo, BATCH_PAGE_SIZE);
    const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res.data);
    batchTotal.value = parsed.totalCount;
    // 映射为本页明细行，并写入缓存
    const rows = parsed.content.map((r) => mapPickToStocktakeRow(r));
    batchPageCache.value.set(pageNo, cloneStocktakeRows(rows));
    batchPageNo.value = pageNo;
    gridApi?.grid?.loadData(rows);
    await enterBatchPageEditMode();
  } catch {
    gridApi?.grid?.loadData([]);
  } finally {
    batchLoading.value = false;
  }
}

/** 本页明细进入表单编辑态，并聚焦首行实盘数量 */
async function enterBatchPageEditMode() {
  await nextTick();
  const rows = collectGridRows().filter((r) => isPresentItemId(r.itemId));
  if (rows.length === 0) return;
  // 全部标记为表单态，便于直接改实盘
  for (const row of rows) {
    stocktakeGridEdit.markRowFormMode(row);
  }
  const first = rows[0];
  if (!first) return;
  await gridApi?.grid?.scrollToRow?.(first);
  await stocktakeGridEdit.enterRowFormMode(first, '_actualQtyUnit');
}

/** 底部分页切换：先缓存当前页，再加载目标页 */
async function handleBatchPageChange(currentPage: number) {
  if (!batchMode.value || batchLoading.value) return;
  if (currentPage === batchPageNo.value) return;
  // 先落缓存再清编辑态，避免未提交单元格改动丢失
  flushCurrentBatchPageToCache();
  stocktakeGridEdit.clearGridEdit();
  await loadBatchPage(currentPage);
}

/** 汇总批量模式下全部页明细（保存前用；未访问页现场拉取默认实盘=账面） */
async function collectAllBatchModeRows(): Promise<StocktakePreviewRow[]> {
  flushCurrentBatchPageToCache();
  const total = batchTotal.value;
  if (total <= 0) return [];
  const pageCount = Math.max(1, Math.ceil(total / BATCH_PAGE_SIZE));
  const allRows: StocktakePreviewRow[] = [];
  batchLoading.value = true;
  try {
    // 按页拼齐：已访问页用缓存，未访问页拉接口默认值
    for (let pageNo = 1; pageNo <= pageCount; pageNo++) {
      const cached = batchPageCache.value.get(pageNo);
      if (cached) {
        allRows.push(...cloneStocktakeRows(cached));
        continue;
      }
      const res = await fetchStocktakePickPage(pageNo, BATCH_PAGE_SIZE);
      const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res.data);
      const rows = parsed.content.map((r) => mapPickToStocktakeRow(r));
      batchPageCache.value.set(pageNo, cloneStocktakeRows(rows));
      allRows.push(...rows);
    }
  } finally {
    batchLoading.value = false;
  }
  return allRows;
}

/** 开始批量盘点：按选品列表分页载入操作明细 */
async function startBatchStocktake() {
  if (!documentEditable.value) return;
  if (!(await validateInventoryHeadFormFields(headFormRef.value, 'purposeLocationId'))) {
    return;
  }
  const productTableName = String(headItemTableType.value ?? '').trim();
  if (!productTableName) {
    ElMessage.warning('请先选择项目类型');
    return;
  }
  if (!productPickGateLocationId.value) {
    warnInventoryLocationRequiredByLabel(props.locationFieldLabel);
    return;
  }

  const existing = collectGridRows().filter((r) => isPresentItemId(r.itemId));
  if (existing.length > 0 && !batchMode.value) {
    try {
      await ElMessageBox.confirm(
        '批量盘点将按当前库房与项目类型重新加载库存行到明细表，现有明细将被替换，是否继续？',
        '批量盘点',
        { type: 'warning', confirmButtonText: '继续', cancelButtonText: '取消' },
      );
    } catch {
      return;
    }
  }

  stocktakeGridEdit.clearGridEdit();
  stocktakeGridEdit.resetFormMode();
  batchPageCache.value = new Map();
  batchMode.value = true;
  batchPageNo.value = 1;
  batchTotal.value = 0;
  gridApi?.grid?.loadData([]);
  await loadBatchPage(1);
  if (batchTotal.value === 0) {
    ElMessage.warning('当前条件下暂无可盘点库存');
    exitBatchMode();
    return;
  }
  ElMessage.success(`已加载盘点明细，共 ${batchTotal.value} 条，每页 ${BATCH_PAGE_SIZE} 条`);
}

interface StocktakeDraftSnap {
  purposeLocationId?: number | string;
  practitionerId?: number | string;
  rows: StocktakePreviewRow[];
  batchMode?: boolean;
  batchPageNo?: number;
  batchTotal?: number;
  batchPages?: [number, StocktakePreviewRow[]][];
}

const parentSync = useInvDocParentSyncSession<StocktakeDraftSnap>({
  scene: () =>
    props.variant === 'pharmacy'
      ? INV_DOC_DRAFT_SCENE.pharmacyStocktake
      : props.variant === 'department'
        ? INV_DOC_DRAFT_SCENE.deptStocktake
        : INV_DOC_DRAFT_SCENE.warehouseStocktake,
  getDocKey: () => {
    const draftId = String(props.clientDraftId ?? '').trim();
    if (props.isDraftOrder && draftId) return draftId;
    return String(props.busNo ?? headerBusNo.value ?? '').trim() || undefined;
  },
  getCardMeta: () => ({
    statusEnum_enumText: '编辑中',
    applyTime: '',
  }),
});

/** 拍摄当前主区草稿快照（侧栏切单 / 落盘共用） */
function captureStocktakeDraftSnap(): StocktakeDraftSnap {
  if (batchMode.value) {
    flushCurrentBatchPageToCache();
  }
  return {
    purposeLocationId: purposeLocationId.value,
    practitionerId: practitionerId.value,
    rows: collectGridRows().map((r) => ({ ...r })),
    batchMode: batchMode.value,
    batchPageNo: batchPageNo.value,
    batchTotal: batchTotal.value,
    batchPages: batchMode.value
      ? Array.from(batchPageCache.value.entries()).map(
          ([pageNo, rows]) => [pageNo, cloneStocktakeRows(rows)] as [number, StocktakePreviewRow[]],
        )
      : undefined,
  };
}

/** 侧栏切单：缓存/恢复本地草稿，避免已落库明细污染新建单 */
async function syncFromParentContext() {
  await parentSync.syncFromParentContext(
    { isDraftOrder: Boolean(props.isDraftOrder), parentBusNo: props.busNo },
    {
      getLocalPersistedBusNo: () => headerBusNo.value,
      captureDraft: captureStocktakeDraftSnap,
      restoreDraft: async (snap) => {
        headerBusNo.value = undefined;
        detailStatusEnum.value = undefined;
        purposeLocationId.value = snap.purposeLocationId;
        practitionerId.value = snap.practitionerId;
        stocktakeGridEdit.resetFormMode();
        await nextTick();
        if (snap.batchMode) {
          batchMode.value = true;
          batchPageNo.value = snap.batchPageNo ?? 1;
          batchTotal.value = snap.batchTotal ?? 0;
          batchPageCache.value = new Map(
            (snap.batchPages ?? []).map(
              ([pageNo, rows]) =>
                [pageNo, cloneStocktakeRows(rows)] as [number, StocktakePreviewRow[]],
            ),
          );
          const cached = batchPageCache.value.get(batchPageNo.value);
          gridApi?.grid?.loadData(cached ? cloneStocktakeRows(cached) : snap.rows);
        } else {
          exitBatchMode();
          gridApi?.grid?.loadData(snap.rows);
        }
        patchStocktakeGridEditConfig();
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
/** 切菜单 / KeepAlive 停用前把未保存草稿写入 sessionStorage */
function flushStocktakeDraftPersist(): void {
  parentSync.flushPersist({
    getLocalPersistedBusNo: () => headerBusNo.value,
    captureDraft: captureStocktakeDraftSnap,
  });
}

onBeforeUnmount(() => {
  flushStocktakeDraftPersist();
});

onDeactivated(() => {
  flushStocktakeDraftPersist();
});

watch(
  () => props.resetKey,
  () => {
    parentSync.discardDraftSession();
  },
);

watch(
  () => [props.busNo, props.isDraftOrder, props.resetKey, props.clientDraftId] as const,
  () => {
    void syncFromParentContext();
  },
  { immediate: true },
);

watch(
  effectivePractitionerOptions,
  (opts) => {
    if (opts.length === 0) return;
    // 已落库单据：经手人只认详情回显，禁止用登录人覆盖
    if (!props.isDraftOrder && String(props.busNo ?? '').trim() !== '') {
      return;
    }
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
  <div
    class="stocktake-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
    :class="{ 'stocktake-main-pane--flash': paneFlashActive }"
  >
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
        @mousedown="stocktakeGridEdit.onSurfaceMouseDown"
      >
        <div :class="INV_DOC_HEAD_SECTION_CLASS">
          <SectionTitle :title="headSectionTitle" />
          <div class="mt-1 flex flex-wrap items-center gap-2">
            <span class="text-xs" style="color: var(--el-text-color-regular)">状态</span>
            <span class="text-sm" style="color: var(--el-text-color-primary)">{{
              headerStatusText
            }}</span>
            <span v-if="headerBusNo" class="text-xs" style="color: var(--el-text-color-secondary)">
              单号 {{ headerBusNo }}
            </span>
          </div>
          <ElForm
            ref="headFormRef"
            :model="headFormModel"
            :rules="headFormRules"
            label-width="96px"
            class="compact-head-form mt-1"
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
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="loc in locationOptions"
                    :key="String(loc.locationId)"
                    :label="loc.displayName ?? String(loc.locationId)"
                    :value="loc.locationId"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="经手人">
                <ElSelect
                  v-model="practitionerId"
                  class="w-full"
                  filterable
                  clearable
                  placeholder="请选择经手人（支持拼音）"
                  :disabled="!documentEditable"
                  :filter-method="handlePractitionerFilter"
                  @visible-change="handlePractitionerDropdownVisible"
                >
                  <ElOption
                    v-for="p in displayedPractitionerOptions"
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
            </div>
          </ElForm>
        </div>

        <div
          ref="stocktakeGridKeyboardRef"
          class="stocktake-main-pane__grid-wrap mt-2 flex min-h-0 min-w-0 flex-1 flex-col"
          @keydown.capture="stocktakeGridEdit.onGridKeydown"
        >
          <Grid class="min-h-0 flex-1">
            <template #toolbar-actions>
              <InvDocGridToolbar
                v-if="documentEditable"
                show-batch-add
                batch-add-label="批量盘点"
                show-trace-scan
                @add="addItem"
                @batch-add="startBatchStocktake"
                @delete-selected="deleteItem('select')"
                @trace-scan="handleOpenTraceScan"
              />
            </template>
            <template #action="{ row }">
              <ElButton
                v-if="documentEditable"
                type="danger"
                text
                @click="deleteItem(row as StocktakePreviewRow)"
              >
                <Trash2 class="size-4" />
                删除
              </ElButton>
            </template>
          </Grid>
          <div
            v-if="batchMode && batchTotal > 0"
            class="stocktake-main-pane__pager flex shrink-0 items-center justify-end py-2"
          >
            <ElPagination
              background
              layout="total, prev, pager, next"
              :current-page="batchPageNo"
              :page-size="BATCH_PAGE_SIZE"
              :total="batchTotal"
              :disabled="batchLoading || !documentEditable"
              @current-change="handleBatchPageChange"
            />
          </div>
        </div>
      </div>
      <InvDocAmountSummaryBar
        v-if="showFinanceApprovalSummary"
        :items="financeApprovalSummaryItems"
      />
    </InvMainShell>
  </div>
  <InvDocTraceScanBridge
    ref="traceScanBridgeRef"
    scene="stocktake"
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

.stocktake-main-pane__grid-wrap :deep(.vxe-grid) {
  flex: 1;
  min-height: 0;
}

.stocktake-main-pane__pager {
  border-top: 1px solid var(--el-border-color-lighter);
}

@keyframes stocktake-pane-flash {
  0% {
    box-shadow: inset 0 0 0 2px var(--el-color-primary-light-5);
  }
  100% {
    box-shadow: inset 0 0 0 0 transparent;
  }
}

.stocktake-main-pane--flash {
  animation: stocktake-pane-flash 0.42s ease-out;
}
</style>
