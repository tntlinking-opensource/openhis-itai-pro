<!--
  盘库/盘点单右侧主区：与进货单/入库单同构（工作站 + VXE 明细 + 审批操作条）。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { ApiOptionItem } from '#/types/api-response';
import type { InboundProductUnitBand } from '#/utils/inventory/inbound-product-unit-bands';
import type { FormInstance } from 'element-plus';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
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
  ElSelect,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { useInventoryDocGridEdit } from '#/composables/use-inventory-doc-grid-edit';
import { useInvDocParentSyncSession } from '#/composables/use-inv-doc-parent-sync-session';
import {
  agreePharmacyStocktakeApi,
  createPharmacyStocktakeApi,
  getPharmacyStocktakeDetailApi,
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
import { decimalAdd, decimalSub } from '#/utils/format/decimal-util';
import { formatInventoryLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';
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
} from '#/utils/inventory/inventory-product-pick-list';
import { resolveLoginPractitionerIdForRequest } from '#/utils/format/preserve-api-id';
import {
  INV_ITEM_TABLE_TYPE_DEVICE,
  INV_ITEM_TABLE_TYPE_MEDICATION,
  isSpecificInventoryLocationId,
  normalizeInventoryItemTableTypeOptions,
  resolveDefaultInventoryItemTableType,
  resolveDocHeadProductPickLocationId,
  warnInventoryLocationRequiredByLabel,
} from '#/utils/inventory/inventory-workbench-list';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import {
  INV_DOC_HEAD_FORM_GRID_CLASS,
  INV_DOC_HEAD_SECTION_CLASS,
} from '#/utils/inventory/inventory-doc-grid-common';
import { refreshInventoryDocGridRow } from '#/utils/inventory/inventory-doc-grid-edit';

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
  variant: 'department' | 'pharmacy' | 'warehouse';
  busNo?: string;
  isDraftOrder?: boolean;
  resetKey?: number;
  sidebarPurposeLocationId?: number | string;
  locationOptions:
    | DeptStocktakeLocationOption[]
    | PharmacyStocktakeLocationOption[]
    | WarehouseStocktakeLocationOption[];
  itemTableTypeOptions?: ApiOptionItem[];
  /** 单头区库房标签 */
  locationFieldLabel?: string;
  /** 单头区标题 */
  headSectionTitle?: string;
}

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  isDraftOrder: false,
  resetKey: 0,
  sidebarPurposeLocationId: undefined,
  locationOptions: () => [],
  itemTableTypeOptions: () => [],
  locationFieldLabel: '盘点库房',
  headSectionTitle: '盘点单信息',
});

const emit = defineEmits<{
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

const userStore = useUserStore();

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
const saving = ref(false);

const isNewDraft = computed(() => !headerBusNo.value);

const documentEditable = computed(
  () => isNewDraft.value || isStockOutDocumentEditableStatus(detailStatusEnum.value),
);

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
    saveDisabled: base.saveDisabled || busy,
    saveAndSubmitDisabled: base.saveAndSubmitDisabled || busy,
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
function formatStocktakeDetailPersistedAmount(total: unknown): string {
  if (total === undefined || total === null || total === '') return '';
  return formatMoneyDisplay(total);
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
    actualAmount: formatStocktakeDetailPersistedAmount(ln.actualAmount),
    profitLossAmount: formatStocktakeDetailPersistedAmount(ln.profitLossAmount),
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
  purposeLocationId.value = undefined;
  clearInventoryHeadFormValidation(headFormRef.value);
  stocktakeGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchStocktakeGridEditConfig();
  });
}

async function loadDetail(busNo: string, loadGen?: number) {
  stocktakeGridEdit.resetFormMode();
  stocktakeGridEdit.clearGridEdit();
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
      traceNo: String(r.traceNo ?? '').trim() || undefined,
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
  const rows = collectGridRows();
  if (!validateBeforeSave(rows)) return undefined;

  const practitionerId = resolveLoginPractitionerIdForRequest(
    userStore.userInfo as null | Record<string, unknown>,
  );
  if (!practitionerId) {
    ElMessage.warning('无法获取当前操作人员，请重新登录');
    return undefined;
  }

  saving.value = true;
  try {
    const lines = buildLinesPayload(rows);
    const common = {
      purposeLocationId: purposeLocationId.value as number | string,
      practitionerId,
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

async function handleSaveDoc() {
  await persistStocktake();
}

async function handleSaveAndSubmitDoc() {
  const no = await persistStocktake();
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
}

interface StocktakeDraftSnap {
  purposeLocationId?: number | string;
  rows: StocktakePreviewRow[];
}

const parentSync = useInvDocParentSyncSession<StocktakeDraftSnap>();

/** 侧栏切单：缓存/恢复本地草稿，避免已落库明细污染新建单 */
async function syncFromParentContext() {
  await parentSync.syncFromParentContext(
    { isDraftOrder: Boolean(props.isDraftOrder), parentBusNo: props.busNo },
    {
      getLocalPersistedBusNo: () => headerBusNo.value,
      captureDraft: () => ({
        purposeLocationId: purposeLocationId.value,
        rows: collectGridRows().map((r) => ({ ...r })),
      }),
      restoreDraft: async (snap) => {
        headerBusNo.value = undefined;
        detailStatusEnum.value = undefined;
        purposeLocationId.value = snap.purposeLocationId;
        stocktakeGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
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
                @click="deleteItem(row as StocktakePreviewRow)"
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
  <InvDocTraceScanBridge ref="traceScanBridgeRef" scene="stocktake" />
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
  min-height: 200px;
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
