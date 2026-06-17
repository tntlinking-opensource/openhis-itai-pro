<!--
  盘库/盘点单右侧主区：与进货单/入库单同构（工作站 + VXE 明细 + 审批操作条）。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { ApiOptionItem } from '#/types/api-response';
import type { InboundProductUnitBand } from '#/utils/inventory/inbound-product-unit-bands';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import { Delete, Plus } from '@element-plus/icons-vue';
import InvDocActions from '@inventory/components/inv-doc-actions.vue';
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
import { getChargeTypeListApi } from '#/api/core/common';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
  type ChargeType,
} from '#/chs/constants/insurance-contract';
import SectionTitle from '#/components/layouts/section-title.vue';
import { decimalMul, decimalSub } from '#/utils/decimal-util';
import {
  buildStocktakeDocActionFlags,
  normalizeStocktakeStatusSort,
  stocktakeStatusLabel,
} from '#/utils/inventory/stocktake-doc-actions';
import {
  applyWarehouseNonInboundBandKindToRow,
  canConvertDisplayQtyToSplitForBands,
  convertInboundQtyBetweenBandKinds,
  inboundUnitBandSelectOptions,
  normalizeInboundProductUnitBands,
  persistDisplayQtyAsSplitForBands,
  warehouseNonInboundDefaultBandKind,
  warehouseNonInboundLoadPersistedSplitAsStockDisplay,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  inventoryPickAvailableQtyAsStockDisplay,
  inventoryPickMedicineCell,
  inventoryPickNumberField,
  inventoryPickStringField,
} from '#/utils/inventory/inventory-product-pick-list';
import { resolveLoginPractitionerIdForRequest } from '#/utils/preserve-api-id';

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

const categoryEnum = computed(() => {
  if (props.variant === 'pharmacy') return PHA_STOCKTAKE_CATEGORY_ENUM;
  if (props.variant === 'department') return DEP_STOCKTAKE_CATEGORY_ENUM;
  return WHS_STOCKTAKE_CATEGORY_ENUM;
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
  if (Number.isSafeInteger(n)) return n;
  return s;
}

const headerBusNo = ref<string | undefined>(undefined);
const detailStatusEnum = ref<number | undefined>(undefined);
const purposeLocationId = ref<number | string | undefined>(undefined);
const headItemTableType = ref(firstItemTableTypeValue());
const saving = ref(false);

const isNewDraft = computed(() => !headerBusNo.value);

const documentEditable = computed(() => isNewDraft.value || detailStatusEnum.value === 1);

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

const insuranceContractBusNo = ref<string>();
const insuranceChargeTypeOptions = ref<ChargeType[]>([]);

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

function formatStocktakeQtyCell({ cellValue }: { cellValue?: unknown }) {
  const n = Number(cellValue);
  return Number.isFinite(n) ? String(toStocktakeQty(n)) : '';
}

/** 按账面/实盘重算盈亏数量与金额。 */
/** 切换盘点计量带：数量与单价随带换算（默认 STOCK，保存转 SPLIT）。 */
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
  }
  applyWarehouseNonInboundBandKindToRow(row, newK);
  row.__prevBandKind = newK;
  recalcStocktakeLine(row);
}

function recalcStocktakeLine(row: StocktakePreviewRow) {
  const book = toStocktakeQty(row.bookQuantity);
  const actual = toStocktakeQty(row.actualQuantity);
  const price = Number(row.price);
  const bookOk = Number.isFinite(book);
  const actualOk = Number.isFinite(actual);
  const priceOk = Number.isFinite(price);

  if (bookOk && actualOk) {
    row.profitLossQuantity = Number(decimalSub(actual, book, 0));
  } else {
    row.profitLossQuantity = 0;
  }

  row.actualAmount = actualOk && priceOk ? String(decimalMul(actual, price, 2)) : '';
  row.profitLossAmount =
    bookOk && actualOk && priceOk ? String(decimalMul(row.profitLossQuantity, price, 2)) : '';
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

  row.bookQuantity = toStocktakeQty(inventoryPickAvailableQtyAsStockDisplay(pick));
  row.actualQuantity = row.bookQuantity;

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
  recalcStocktakeLine(row);
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
    bookQuantity: toStocktakeQty(ln.bookQuantity),
    actualQuantity: toStocktakeQty(ln.actualQuantity),
    profitLossQuantity: toStocktakeQty(ln.profitLossQuantity),
    unitCode: unitCodeStr,
    selectedBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: unitCodeStr.trim() === '' ? '单位' : unitCodeStr }],
    price: ln.price === undefined || ln.price === null ? 0 : Number(ln.price),
    actualAmount: ln.actualAmount === undefined ? '' : String(ln.actualAmount),
    profitLossAmount: ln.profitLossAmount === undefined ? '' : String(ln.profitLossAmount),
    remark: String(ln.remake ?? ''),
    _occurrenceTime: ln.occurrenceTime,
  };

  if (bands.length > 0) {
    base.unitBands = bands;
    const actualLoaded = warehouseNonInboundLoadPersistedSplitAsStockDisplay({
      bands,
      persistedItemQuantity: ln.actualQuantity ?? 0,
      persistedUnitCode: unitCodeStr,
    });
    base.actualQuantity = toStocktakeQty(actualLoaded.displayQty);
    base.selectedBandKind = actualLoaded.bandKind;
    base.unitCode = actualLoaded.unitCode;
    base.__prevBandKind = actualLoaded.bandKind;

    const bookLoaded = warehouseNonInboundLoadPersistedSplitAsStockDisplay({
      bands,
      persistedItemQuantity: ln.bookQuantity ?? 0,
      persistedUnitCode: unitCodeStr,
    });
    base.bookQuantity = toStocktakeQty(bookLoaded.displayQty);

    base.unitList = inboundUnitBandSelectOptions(bands);
    applyWarehouseNonInboundBandKindToRow(base, actualLoaded.bandKind, ln.price);
  }

  return base;
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
          productTableName: headItemTableType.value || undefined,
          purposeLocationId: purposeLocationId.value ?? props.sidebarPurposeLocationId,
        }),
        onSelected: (product: Record<string, unknown>, row: StocktakePreviewRow) => {
          syncRowFromStocktakePick(product, row);
        },
      },
    },
    showOverflow: true,
  },
  { field: 'spec', title: '规格', width: 110, showOverflow: true },
  { field: 'manufacturer', title: '厂家', minWidth: 100, showOverflow: true },
  { field: 'lotNumber', title: '批号', width: 100, showOverflow: true },
  {
    field: 'bookQuantity',
    title: '账面数量',
    width: 96,
    align: 'right',
    headerAlign: 'center',
    formatter: formatStocktakeQtyCell,
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
      },
    },
  },
  {
    field: 'profitLossQuantity',
    title: '盈亏数量',
    width: 108,
    align: 'right',
    headerAlign: 'center',
    formatter: formatStocktakeQtyCell,
  },
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
    field: 'actualAmount',
    title: '实盘金额',
    width: 112,
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
    width: 112,
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
      name: 'ElInput',
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
  columnConfig: { resizable: true },
  data: [],
  editConfig: {
    enabled: true,
    trigger: 'click',
    mode: 'row',
    beforeEditMethod: () => documentEditable.value,
  },
  columns,
  pagerConfig: { enabled: false },
});

const [Grid, gridApi] = useVbenVxeGrid<StocktakePreviewRow>({
  gridOptions,
  gridEvents: {
    editClosed(params: { row: StocktakePreviewRow; column?: { field?: string } }) {
      const f = params.column?.field;
      if (f === 'actualQuantity' || f === '_actualQtyUnit') {
        recalcStocktakeLine(params.row);
      }
    },
  },
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
});

function patchGridEditConfig() {
  gridOptions.editConfig = {
    ...gridOptions.editConfig,
    enabled: documentEditable.value,
    trigger: 'click',
    mode: 'row',
    beforeEditMethod: () => documentEditable.value,
  };
}

watch(
  () => documentEditable.value,
  () => {
    patchGridEditConfig();
    void nextTick(() => {
      const g = gridApi?.grid as undefined | { clearEdit?: () => void };
      if (!documentEditable.value) g?.clearEdit?.();
    });
  },
  { immediate: true },
);

watch(
  () => props.itemTableTypeOptions,
  (opts) => {
    if (opts?.length && !String(headItemTableType.value ?? '').trim()) {
      headItemTableType.value = String(opts[0]?.value ?? '');
    }
  },
  { deep: true },
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

function resetDetailForCreate() {
  headerBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  const pid = normalizeLocationId(props.sidebarPurposeLocationId);
  purposeLocationId.value = pid ?? props.locationOptions[0]?.locationId;
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchGridEditConfig();
  });
}

async function loadDetail(busNo: string) {
  try {
    const res =
      props.variant === 'pharmacy'
        ? await getPharmacyStocktakeDetailApi({ busNo })
        : props.variant === 'department'
          ? await getDeptStocktakeDetailApi({ busNo })
          : await getWarehouseStocktakeDetailApi({ busNo });
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到盘点单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    detailStatusEnum.value = normalizeStocktakeStatusSort(data.statusEnum);
    purposeLocationId.value = data.purposeLocationId ?? undefined;

    const gridRows = (data.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchGridEditConfig();
      for (const r of gridRows) {
        recalcStocktakeLine(r);
      }
    });
  } catch {
    void nextTick(() => {
      gridApi?.grid?.loadData([]);
      patchGridEditConfig();
    });
  }
}

function buildLinesPayload(
  rows: StocktakePreviewRow[],
): StocktakeLineDto[] | PharmacyStocktakeLineDto[] | DeptStocktakeLineDto[] {
  const now = formatDateTime(new Date());
  return rows.map((r) => {
    recalcStocktakeLine(r);
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
      inventoryItemId: r.inventoryItemId,
      price: Number.isFinite(Number(r.price)) ? Number(r.price) : undefined,
      remake: String(r.remark ?? '').trim() || undefined,
      occurrenceTime: r._occurrenceTime?.trim() || now,
    };
  });
}

function validateBeforeSave(rows: StocktakePreviewRow[]): boolean {
  const loc = purposeLocationId.value;
  if (loc === undefined || loc === null || loc === '') {
    ElMessage.warning(`请选择${props.locationFieldLabel}`);
    return false;
  }
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一行盘点明细');
    return false;
  }
  for (const [i, r] of rows.entries()) {
    recalcStocktakeLine(r);
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
    if (!Number.isFinite(actual) || actual < 0) {
      ElMessage.warning(`第 ${i + 1} 行实盘数量须 ≥ 0`);
      return false;
    }
    const bands = r.unitBands ?? [];
    if (!canConvertDisplayQtyToSplitForBands(bands, actual, r.selectedBandKind)) {
      ElMessage.warning(`第 ${i + 1} 行无法将数量换算为拆零单位，请检查计量带`);
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
  if (detailStatusEnum.value !== 1) {
    ElMessage.warning('仅待审核单据可删除');
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
  if (!insuranceContractBusNo.value?.trim()) {
    ElMessage.warning('请先选择医保契约');
    return;
  }
  try {
    if (props.variant === 'pharmacy') {
      await agreePharmacyStocktakeApi({
        busNo: no,
        contractBusNo: insuranceContractBusNo.value,
      });
    } else if (props.variant === 'department') {
      await agreeDeptStocktakeApi({
        busNo: no,
        contractBusNo: insuranceContractBusNo.value,
      });
    } else {
      await agreeWarehouseStocktakeApi({
        busNo: no,
        contractBusNo: insuranceContractBusNo.value,
      });
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
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const record = createEmptyStocktakeRow();
  const { row } = await $grid.insertAt(record, null);
  await $grid.$nextTick();
  if (row) {
    await $grid.scrollToRow(row);
    await $grid.setEditCell(row, 'medicinePick');
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
      const pid = normalizeLocationId(props.sidebarPurposeLocationId);
      if (pid !== undefined) {
        purposeLocationId.value = pid;
      }
    }
  },
);

watch(
  () => props.locationOptions,
  (list) => {
    if (list.length === 0) return;
    if (!purposeLocationId.value && (isNewDraft.value || !headerBusNo.value)) {
      const pid = normalizeLocationId(props.sidebarPurposeLocationId);
      purposeLocationId.value = pid ?? list[0]?.locationId;
    }
  },
  { deep: true },
);

onMounted(() => {
  void loadInsuranceContractBusNo();
});
</script>

<template>
  <div
    class="stocktake-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
    :class="{ 'stocktake-main-pane--flash': paneFlashActive }"
  >
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

      <div class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-2 pt-2">
        <div class="shrink-0">
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
          <ElForm label-width="80px" class="compact-head-form mt-1">
            <div
              class="grid grid-cols-1 gap-x-2 gap-y-0 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4"
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
                    v-for="t in itemTableTypeOptions"
                    :key="String(t.value)"
                    :label="t.label"
                    :value="String(t.value)"
                  />
                </ElSelect>
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <div class="stocktake-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col">
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
</template>

<style scoped>
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
