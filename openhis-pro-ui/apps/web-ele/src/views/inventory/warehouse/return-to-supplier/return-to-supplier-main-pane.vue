<!--
  药库退货单右侧主区：InvMainShell + InvDocActions + 单头 + 明细表格。
-->
<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type {
  LinkableGoodsReceiptListRowDto,
  ReturnableInboundLineDto,
  ReturnToSupplierDetailLineDto,
  ReturnToSupplierLineDto,
  WarehouseOptionDto,
} from '#/api/inventory/warehouse/return-to-supplier';
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
import InvDocLinkPickerDialog from '@inventory/components/inv-doc-link-picker-dialog.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
import {
  ElAlert,
  ElButton,
  ElDatePicker,
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
  agreeReturnToSupplierApi,
  createReturnToSupplierApi,
  getLinkableGoodsReceiptDetailApi,
  getReturnToSupplierDetailApi,
  pageLinkableGoodsReceiptsApi,
  rejectReturnToSupplierApi,
  removeReturnToSupplierApi,
  submitReturnToSupplierApprovalApi,
  updateReturnToSupplierApi,
  withdrawReturnToSupplierApprovalApi,
  WHS_RT_CATEGORY_STOCK,
  WHS_RT_LOCATION_TYPE_CABINET,
} from '#/api/inventory/warehouse/return-to-supplier';
import SectionTitle from '#/components/layouts/section-title.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  INV_ALL_WAREHOUSES_LABEL,
  isSpecificInventoryLocationId,
  resolveSpecificInventoryLocationId,
  warnInventoryLocationRequired,
} from '#/utils/inventory/inventory-workbench-list';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import { withInvDocLinkPickerEnrichedColumns } from '#/utils/inventory/inv-doc-link-picker-display';
import {
  canConvertDisplayQtyToSplitForBands,
  persistDisplayQtyAsSplitForBands,
  resolveInboundLinePersistUnitCodeForBands,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  applyHydratedInventoryDetailQtyToRow,
  applyInventoryRowQtyUnitList,
  handleWarehouseNonInboundUnitBandChanged,
  hydrateInventoryDetailLineQtyForGrid,
} from '#/utils/inventory/inventory-doc-grid-common';
import {
  INV_DOC_UPSTREAM_HINT,
  hasNonEmptyBusNo,
  mapReturnableLinkLineToGridRow,
  warnInventoryDocRestriction,
} from '#/utils/inventory/inventory-doc-restrictions';
import {
  isStockOutDocumentEditableStatus,
  isStockOutDocumentRemovableStatus,
  buildStockOutDocActionFlags,
  stockOutStatusLabel,
  normalizeStockOutStatusSort,
} from '#/utils/inventory/stock-out-doc-actions';
import { resolveLoginPractitionerIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'InventoryWarehouseReturnToSupplierMainPane' });

interface MedicinePickCell {
  id?: number | string;
  name?: string;
}

interface ReturnPreviewRow {
  id: string;
  medicinePick?: MedicinePickCell | Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  productName: string;
  itemQuantity?: number;
  unitCode: string;
  unitCodeDictText?: string;
  unitBands?: InboundProductUnitBand[];
  selectedBandKind?: string;
  unitList?: Array<{ label: string; value: string }>;
  __prevBandKind?: string;
  /** 关联进货行落库时的 unitCode，保存须与进货行一致 */
  inboundPersistedUnitCode?: string;
  lotNumber: string;
  traceNo: string;
  reason: string;
  remake: string;
}

interface Props {
  busNo?: string;
  isDraftOrder?: boolean;
  resetKey?: number;
  warehouseOptions: WarehouseOptionDto[];
  supplierOptions?: ApiOptionItem[];
  applyTime?: string;
}

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  isDraftOrder: false,
  resetKey: 0,
  warehouseOptions: () => [],
  supplierOptions: () => [],
  applyTime: undefined,
});

const emit = defineEmits<{
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

const userStore = useUserStore();

function nowIsoLocal(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
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
const originalBusNo = ref<string | undefined>(undefined);
const sourceLocationId = ref<number | string | undefined>(undefined);
const purposeLocationId = ref<number | string | undefined>(undefined);

const headFormRef = ref<FormInstance>();

const headFormModel = computed(() => ({
  sourceLocationId: sourceLocationId.value ?? '',
}));

const headFormRules = computed(() =>
  buildInventoryLocationHeadFormRules({
    sourceLocationId: '药库',
  }),
);

const supplierId = ref<number | string | undefined>(undefined);
const detailStatusEnum = ref<number | undefined>(undefined);
const headApplyTime = ref(formatDateTime(new Date()));
const saving = ref(false);

const linkInboundVisible = ref(false);
const linkInboundLoading = ref(false);
const linkInboundQueryParams = ref<{
  keyword: string;
  pageNo: number;
  pageSize: number;
  warehouseId: number | string;
}>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  warehouseId: INV_ALL_LOCATIONS_FILTER_ID,
});
const linkInboundTotal = ref(0);
const linkInboundRecords = ref<LinkableGoodsReceiptListRowDto[]>([]);

/** 关联进货单弹窗：药库下拉（含「全部库房」） */
const linkInboundWarehouseOptions = computed(() => {
  const rows = (props.warehouseOptions ?? [])
    .filter((row) => isSpecificInventoryLocationId(row.locationId))
    .map((row) => ({
      label: String(row.displayName ?? row.locationId ?? ''),
      value: row.locationId as number | string,
    }));
  return [{ label: INV_ALL_WAREHOUSES_LABEL, value: INV_ALL_LOCATIONS_FILTER_ID }, ...rows];
});

/** 弹窗查询：取具体药库，否则「全部库房」= '0' */
function resolveLinkInboundSourceLocationId(): number | string {
  const wh = resolveSpecificInventoryLocationId(linkInboundQueryParams.value.warehouseId);
  if (wh) return wh;
  return INV_ALL_LOCATIONS_FILTER_ID;
}

/** 归一弹窗库房筛选（兼容 select 多选数组回传） */
function normalizeLinkInboundWarehouseId(raw: unknown): number | string {
  if (Array.isArray(raw)) {
    const first = raw[0];
    if (first === undefined || first === null) return INV_ALL_LOCATIONS_FILTER_ID;
    return first as number | string;
  }
  if (raw === undefined || raw === null || raw === '') return INV_ALL_LOCATIONS_FILTER_ID;
  return raw as number | string;
}

/** 弹窗内切换药库后立即重查 */
function handleLinkInboundWarehouseChange(value: unknown) {
  linkInboundQueryParams.value.warehouseId = normalizeLinkInboundWarehouseId(value);
  linkInboundQueryParams.value.pageNo = 1;
  void fetchLinkableGoodsReceipts();
}

const linkInboundFormItems = computed<FormItemConfig[]>(() => [
  {
    label: '药库',
    prop: 'warehouseId',
    type: 'select',
    placeholder: '全部库房',
    filterable: true,
    clearable: false,
    multiple: false,
    options: linkInboundWarehouseOptions.value,
    onChange: handleLinkInboundWarehouseChange,
  },
  {
    label: '关键字',
    prop: 'keyword',
    type: 'input',
    placeholder: '进货单号/关键字',
  },
]);

const linkInboundTableColumns = computed<TableColumn[]>(() =>
  withInvDocLinkPickerEnrichedColumns([
    { type: 'index', label: '序号', width: 58, align: 'center' },
    { prop: 'busNo', label: '进货单号', minWidth: 160, showOverflowTooltip: true },
    { prop: 'supplierName', label: '供应商', minWidth: 160, showOverflowTooltip: true },
    { prop: 'applyTime', label: '制单时间', minWidth: 160, slot: 'applyTime' },
    { label: '操作', width: 88, align: 'center', fixed: 'right', slot: 'rowActions' },
  ]),
);

const isNewDraft = computed(() => !headerBusNo.value);
const documentEditable = computed(
  () => isNewDraft.value || isStockOutDocumentEditableStatus(detailStatusEnum.value),
);
const hasLinkedUpstream = computed(() => hasNonEmptyBusNo(originalBusNo.value));
const linesGridEditable = computed(() => documentEditable.value && hasLinkedUpstream.value);

const headerStatusText = computed(() => {
  if (isNewDraft.value) return '编辑中';
  return stockOutStatusLabel(detailStatusEnum.value);
});

const docActionFlags = computed(() => {
  const base = buildStockOutDocActionFlags({
    hasPersistedBusNo: Boolean(headerBusNo.value),
    isDraft: isNewDraft.value,
    status: detailStatusEnum.value,
  });
  const busy = saving.value;
  const upstreamRequired = isNewDraft.value && !hasLinkedUpstream.value;
  return {
    ...base,
    saveDisabled: base.saveDisabled || busy || upstreamRequired,
    saveAndSubmitDisabled: base.saveAndSubmitDisabled || busy || upstreamRequired,
  };
});

const canEditOriginalBusNo = computed(() => documentEditable.value && isNewDraft.value);

function initHeadApplyTimeFromContext() {
  const p = String(props.applyTime ?? '').trim();
  headApplyTime.value = p
    ? p.includes('T')
      ? p.replace('T', ' ')
      : p
    : formatDateTime(new Date());
}

function resolveOccurrenceTimeForApi(): string {
  const fromHead = String(headApplyTime.value ?? '').trim();
  const fromParent = String(props.applyTime ?? '').trim();
  const t = fromHead || fromParent;
  if (t === '') return formatDateTime(new Date());
  return t.includes('T') ? t.replace('T', ' ') : t;
}

function resolveApplyClockForSubmit(): string {
  const raw = resolveOccurrenceTimeForApi();
  return raw.includes('T') ? raw : raw.replace(' ', 'T');
}

function createEmptyRow(): ReturnPreviewRow {
  return {
    id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    medicinePick: undefined,
    itemTable: undefined,
    itemId: undefined,
    productName: '',
    itemQuantity: undefined,
    unitCode: '',
    lotNumber: '',
    traceNo: '',
    reason: '',
    remake: '',
  };
}

function applyReturnRowUnitList(row: ReturnPreviewRow): void {
  applyInventoryRowQtyUnitList(row);
}

function handleReturnToSupplierUnitBandChanged(row: ReturnPreviewRow): void {
  handleWarehouseNonInboundUnitBandChanged(row, {
    getQty: () => Number(row.itemQuantity ?? 0),
    setQty: (qty) => {
      row.itemQuantity = qty;
    },
  });
}

function mapReturnableInboundLineToGrid(
  ln: ReturnableInboundLineDto,
  rowIdx: number,
): ReturnPreviewRow {
  const base = mapReturnableLinkLineToGridRow<ReturnPreviewRow>(ln, rowIdx);
  base.inboundPersistedUnitCode = String(ln.unitCode ?? '').trim();
  return base;
}

function mapDetailLineToGrid(ln: ReturnToSupplierDetailLineDto, rowIdx: number): ReturnPreviewRow {
  const itemIdRaw = ln.itemId;
  const idStr =
    ln.id !== undefined && ln.id !== null
      ? `ln-${String(ln.id)}`
      : itemIdRaw !== undefined && itemIdRaw !== null
        ? `ln-item-${String(itemIdRaw)}-${rowIdx}`
        : `ln-x-${rowIdx}`;
  const name = String(ln.productName ?? '').trim();
  const row: ReturnPreviewRow = {
    id: idStr,
    medicinePick: name ? { id: itemIdRaw as number | string | undefined, name } : undefined,
    itemTable: ln.itemTable,
    itemId: itemIdRaw,
    productName: name,
    itemQuantity: ln.itemQuantity === undefined ? undefined : Number(ln.itemQuantity),
    unitCode: String(ln.unitCode ?? ''),
    lotNumber: String(ln.lotNumber ?? ''),
    traceNo: String(ln.traceNo ?? ''),
    reason: String(ln.reason ?? ''),
    remake: String(ln.remake ?? ''),
  };
  const hydrated = hydrateInventoryDetailLineQtyForGrid({
    itemQuantity: row.itemQuantity,
    unitCode: row.unitCode,
    unitCodeDictText: String(ln.unitCodeDictText ?? ln.unitCode ?? ''),
    productCatalog: ln.productCatalog,
  });
  applyHydratedInventoryDetailQtyToRow(row, hydrated);
  applyReturnRowUnitList(row);
  return row;
}

function isReturnToSupplierRowComplete(row: ReturnPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.itemQuantity);
  if (!Number.isFinite(qty) || qty <= 0) return false;
  return !!String(row.unitCode ?? '').trim();
}

let returnToSupplierGridEdit!: ReturnType<typeof useInventoryDocGridEdit<ReturnPreviewRow>>;

const columns: VxeGridPropTypes.Column[] = [
  { type: 'checkbox', width: 48, fixed: 'left', align: 'center' },
  { type: 'seq', title: '序号', width: 52, fixed: 'left', align: 'center' },
  {
    field: 'productName',
    title: '项目名称',
    minWidth: 200,
    fixed: 'left',
    showOverflow: true,
  },
  {
    field: '_qtyUnit',
    title: '数量',
    width: 168,
    align: 'center',
    headerAlign: 'center',
    editRender: {
      name: 'InventoryQtyUnitCell',
      props: {
        qtyField: 'itemQuantity',
        unitField: 'selectedBandKind',
        onUnitChange: (row: ReturnPreviewRow) => {
          handleReturnToSupplierUnitBandChanged(row);
        },
      },
    },
  },
  {
    field: 'lotNumber',
    title: '批号',
    width: 120,
    editRender: { name: 'InventoryPersistElInput', props: { maxlength: 64, clearable: true } },
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
    title: '退货原因',
    minWidth: 120,
    editRender: { name: 'InventoryPersistElInput', props: { maxlength: 200, clearable: true } },
    showOverflow: true,
  },
  {
    field: 'remake',
    title: '备注',
    minWidth: 120,
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

const gridOptions: VxeGridProps<ReturnPreviewRow> = reactive({
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
    beforeEditMethod: () => linesGridEditable.value,
  },
  columns,
  pagerConfig: { enabled: false },
});

const RETURN_TO_SUPPLIER_EDITABLE_COLUMN_FIELDS = new Set([
  '_qtyUnit',
  'lotNumber',
  'reason',
  'remake',
  'traceNo',
]);

const RETURN_TO_SUPPLIER_EDIT_FIELD_ORDER = [
  '_qtyUnit',
  'lotNumber',
  'traceNo',
  'reason',
  'remake',
] as const;

type ReturnToSupplierEditField = (typeof RETURN_TO_SUPPLIER_EDIT_FIELD_ORDER)[number];

const RETURN_TO_SUPPLIER_VERTICAL_NAV_FIELDS = new Set<ReturnToSupplierEditField>([
  '_qtyUnit',
  'lotNumber',
  'reason',
  'remake',
  'traceNo',
]);

const returnToSupplierGridKeyboardRef = ref<HTMLElement | null>(null);

const [Grid, gridApi] = useVbenVxeGrid<ReturnPreviewRow>({
  gridOptions,
  gridEvents: {
    cellClick: (params: { column?: { field?: string; type?: string }; row: ReturnPreviewRow }) =>
      returnToSupplierGridEdit.onCellClick(params),
    editClosed(params: { row: ReturnPreviewRow }) {
      returnToSupplierGridEdit.syncRowFormMode(params.row);
    },
  },
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
});

returnToSupplierGridEdit = useInventoryDocGridEdit<ReturnPreviewRow>({
  getGridApi: () => gridApi ?? null,
  gridWrapRef: returnToSupplierGridKeyboardRef,
  documentEditable: () => linesGridEditable.value,
  collectRows: collectGridRows,
  config: {
    rowKey: (row) => row.id,
    productField: 'productName',
    editFieldOrder: RETURN_TO_SUPPLIER_EDIT_FIELD_ORDER,
    editableFields: RETURN_TO_SUPPLIER_EDITABLE_COLUMN_FIELDS,
    verticalNavFields: RETURN_TO_SUPPLIER_VERTICAL_NAV_FIELDS,
    firstFillFieldAfterPick: '_qtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isReturnToSupplierRowComplete,
  },
});

function patchReturnToSupplierGridEditConfig() {
  const patched = returnToSupplierGridEdit.patchGridOptions({
    ...(gridOptions as Record<string, unknown>),
    editConfig: {
      ...(gridOptions.editConfig as Record<string, unknown>),
      enabled: linesGridEditable.value,
      beforeEditMethod: () => linesGridEditable.value,
    },
  });
  Object.assign(gridOptions, patched);
}

watch(
  () => linesGridEditable.value,
  (editable) => {
    patchReturnToSupplierGridEditConfig();
    void nextTick(() => {
      if (!editable) {
        returnToSupplierGridEdit.resetFormMode();
        if (!documentEditable.value) {
          returnToSupplierGridEdit.clearGridEdit();
        }
      }
    });
  },
  { immediate: true },
);

function collectGridRows(): ReturnPreviewRow[] {
  const g = gridApi?.grid as null | { getFullData?: () => ReturnPreviewRow[] };
  return g?.getFullData?.() ?? [];
}

const traceScanBridgeRef = ref<InstanceType<typeof InvDocTraceScanBridge> | null>(null);

/** 打开批量追溯码扫码弹窗 */
function handleOpenTraceScan(): void {
  traceScanBridgeRef.value?.open(collectGridRows());
}

function resetDetailForCreate() {
  headerBusNo.value = undefined;
  originalBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  supplierId.value = undefined;
  sourceLocationId.value = undefined;
  purposeLocationId.value = undefined;
  initHeadApplyTimeFromContext();
  clearInventoryHeadFormValidation(headFormRef.value);
  returnToSupplierGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchReturnToSupplierGridEditConfig();
  });
}

async function loadDetail(busNo: string, loadGen?: number) {
  returnToSupplierGridEdit.resetFormMode();
  returnToSupplierGridEdit.clearGridEdit();
  try {
    const res = await getReturnToSupplierDetailApi({ busNo });
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到退货单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    originalBusNo.value = data.originalBusNo ?? undefined;
    detailStatusEnum.value = normalizeStockOutStatusSort(data.statusEnum);
    sourceLocationId.value = data.sourceLocationId ?? undefined;
    purposeLocationId.value = data.purposeLocationId ?? data.sourceLocationId ?? undefined;
    supplierId.value = data.supplierId ?? undefined;

    let headOt = '';
    for (const ln of data.lines ?? []) {
      const o = ln.occurrenceTime;
      if (o !== undefined && o !== null && String(o).trim() !== '') {
        headOt = String(o).trim();
        break;
      }
    }
    headApplyTime.value = headOt
      ? headOt.includes('T')
        ? headOt.replace('T', ' ')
        : headOt
      : formatDateTime(new Date());

    const gridRows = (data.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData(gridRows);
      patchReturnToSupplierGridEditConfig();
    });
  } catch {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchReturnToSupplierGridEditConfig();
    });
  }
}

function buildLinesPayload(rows: ReturnPreviewRow[]): ReturnToSupplierLineDto[] {
  return rows.map((r) => {
    const persisted = persistDisplayQtyAsSplitForBands({
      bands: r.unitBands ?? [],
      displayQty: r.itemQuantity,
      legacyUnitCode: r.unitCode,
      selectedBandKind: r.selectedBandKind,
    });
    const unitCode =
      String(r.inboundPersistedUnitCode ?? '').trim() ||
      resolveInboundLinePersistUnitCodeForBands({
        bands: r.unitBands ?? [],
        legacyUnitCode: r.unitCode,
        selectedBandKind: r.selectedBandKind,
      }) ||
      String(r.unitCode ?? '').trim();
    const itemIdRaw = r.itemId;
    const itemId =
      itemIdRaw === undefined || itemIdRaw === null || String(itemIdRaw).trim() === ''
        ? undefined
        : (itemIdRaw as number | string);
    return {
      itemTable: r.itemTable,
      itemId,
      itemQuantity: persisted.itemQuantity,
      unitCode: unitCode || undefined,
      lotNumber: String(r.lotNumber ?? '').trim() || undefined,
      traceNo: String(r.traceNo ?? '').trim() || undefined,
      occurrenceTime: resolveOccurrenceTimeForApi(),
      reason: String(r.reason ?? '').trim() || undefined,
      remake: String(r.remake ?? '').trim() || undefined,
    };
  });
}

function validateBeforeSave(rows: ReturnPreviewRow[]): boolean {
  if (!isSpecificInventoryLocationId(sourceLocationId.value)) {
    warnInventoryLocationRequired('warehouse');
    return false;
  }
  if (!hasLinkedUpstream.value) {
    warnInventoryDocRestriction(INV_DOC_UPSTREAM_HINT.returnToSupplier);
    return false;
  }
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一行退货明细');
    return false;
  }
  for (const [i, r] of rows.entries()) {
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
    if (!canConvertDisplayQtyToSplitForBands(r.unitBands ?? [], qty, r.selectedBandKind)) {
      ElMessage.warning(`第 ${i + 1} 行数量与单位无法换算，请检查产品单位维护`);
      return false;
    }
  }
  return true;
}

function resolvePurposeLocationIdForApi(): number | string {
  return (purposeLocationId.value ?? sourceLocationId.value) as number | string;
}

async function persistReturnToSupplier(): Promise<string | undefined> {
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
      sourceLocationId: sourceLocationId.value as number | string,
      purposeLocationId: resolvePurposeLocationIdForApi(),
      purposeTypeEnum: WHS_RT_LOCATION_TYPE_CABINET,
      categoryEnum: WHS_RT_CATEGORY_STOCK,
      supplierId:
        supplierId.value === undefined ||
        supplierId.value === null ||
        String(supplierId.value) === ''
          ? undefined
          : (supplierId.value as number | string),
      lines,
    };

    if (headerBusNo.value) {
      await updateReturnToSupplierApi({
        busNo: headerBusNo.value,
        ...common,
      });
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }

    const res = await createReturnToSupplierApi({
      originalBusNo: originalBusNo.value!.trim(),
      ...common,
    });
    const no = res.data?.busNo;
    ElMessage.success('新建成功');
    if (no) {
      headerBusNo.value = no;
      detailStatusEnum.value = normalizeStockOutStatusSort(res.data?.statusEnum) ?? 1;
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
  await persistReturnToSupplier();
}

async function handleSaveAndSubmitDoc() {
  const no = await persistReturnToSupplier();
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
    await ElMessageBox.confirm('确认删除整张退货单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await removeReturnToSupplierApi({ busNo: no });
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
    const res = await submitReturnToSupplierApprovalApi({
      busNo: no,
      applyClock: resolveApplyClockForSubmit(),
    });
    if (res.data?.statusEnum !== undefined && res.data.statusEnum !== null) {
      detailStatusEnum.value = normalizeStockOutStatusSort(res.data.statusEnum);
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
    const res = await withdrawReturnToSupplierApprovalApi({ busNo: no });
    if (res.data?.statusEnum !== undefined && res.data.statusEnum !== null) {
      detailStatusEnum.value = normalizeStockOutStatusSort(res.data.statusEnum);
    }
    ElMessage.success('已撤回');
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch {
    /* handled */
  }
}

function resolveAgreeMessage(res: unknown, fallback: string): string {
  const msg = (res as { message?: string } | undefined)?.message?.trim();
  return msg || fallback;
}

async function handleApprove() {
  const no = headerBusNo.value;
  if (!no) return;
  const approverId = resolveLoginPractitionerIdForRequest(
    userStore.userInfo as null | Record<string, unknown>,
  );
  try {
    const res = await agreeReturnToSupplierApi({
      busNo: no,
      approverId,
      approvalClock: nowIsoLocal(),
    });
    const message = resolveAgreeMessage(res, '审批同意');
    if (message.includes('EX-OPP-207')) {
      ElMessage.warning(message);
    } else {
      ElMessage.success(message);
    }
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch (error: unknown) {
    const msg = String((error as { message?: string })?.message ?? '');
    if (msg.includes('EX-OPP-207')) {
      ElMessage.warning(msg);
      await loadDetail(no);
      emit('documentSaved', { busNo: no });
    }
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
    await rejectReturnToSupplierApi({
      busNo: no,
      rejectReason: value,
      approverId: resolveLoginPractitionerIdForRequest(
        userStore.userInfo as null | Record<string, unknown>,
      ),
      approvalClock: nowIsoLocal(),
    });
    ElMessage.success('已驳回');
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch (e) {
    if (e === 'cancel') return;
  }
}

async function addItem() {
  if (!documentEditable.value) return;
  if (!(await validateInventoryHeadFormFields(headFormRef.value, 'sourceLocationId'))) {
    return;
  }
  if (!hasLinkedUpstream.value) {
    warnInventoryDocRestriction(INV_DOC_UPSTREAM_HINT.returnToSupplier);
    return;
  }
  ElMessage.warning('请通过「从进货单选择」调整明细，不支持手工添加行');
}

async function deleteItem(row: 'select' | ReturnPreviewRow) {
  if (!linesGridEditable.value) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const checked = $grid.getCheckboxRecords() as ReturnPreviewRow[];
  if (row === 'select' && checked.length === 0) return;
  await $grid.remove(row === 'select' ? checked : row);
}

async function openLinkInboundDialog() {
  if (!canEditOriginalBusNo.value) {
    ElMessage.warning('仅新建单据时可关联进货单，或请先点「新增」');
    return;
  }
  // 弹窗内筛选药库，不在单头强制先选（对齐进货单「从订货单选择」）
  linkInboundQueryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    warehouseId: INV_ALL_LOCATIONS_FILTER_ID,
  };
  linkInboundVisible.value = true;
}

/** 关联进货单弹窗：查询 */
function handleLinkInboundQuery(params: Record<string, unknown>) {
  Object.assign(linkInboundQueryParams.value, params);
  linkInboundQueryParams.value.warehouseId = normalizeLinkInboundWarehouseId(
    linkInboundQueryParams.value.warehouseId,
  );
  linkInboundQueryParams.value.pageNo = 1;
  void fetchLinkableGoodsReceipts();
}

/** 关联进货单弹窗：重置 */
function handleLinkInboundReset() {
  linkInboundQueryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    warehouseId: INV_ALL_LOCATIONS_FILTER_ID,
  };
  void fetchLinkableGoodsReceipts();
}

/** 关联进货单弹窗：分页 */
function handleLinkInboundPagination(pagination: { limit: number; page: number }) {
  linkInboundQueryParams.value.pageNo = pagination.page;
  linkInboundQueryParams.value.pageSize = pagination.limit;
  void fetchLinkableGoodsReceipts();
}

async function fetchLinkableGoodsReceipts() {
  linkInboundQueryParams.value.warehouseId = normalizeLinkInboundWarehouseId(
    linkInboundQueryParams.value.warehouseId,
  );
  linkInboundLoading.value = true;
  try {
    const res = await pageLinkableGoodsReceiptsApi({
      sourceLocationId: resolveLinkInboundSourceLocationId(),
      keyword: String(linkInboundQueryParams.value.keyword ?? '').trim() || undefined,
      pageNo: linkInboundQueryParams.value.pageNo,
      pageSize: linkInboundQueryParams.value.pageSize,
    });
    const { content, totalCount } = parsePagedRowsFromApiData<LinkableGoodsReceiptListRowDto>(
      res.data,
    );
    linkInboundRecords.value = content;
    linkInboundTotal.value = totalCount;
  } catch {
    linkInboundRecords.value = [];
    linkInboundTotal.value = 0;
  } finally {
    linkInboundLoading.value = false;
  }
}

async function applyLinkableInbound(row: LinkableGoodsReceiptListRowDto) {
  const busNo = String(row.busNo ?? '').trim();
  if (!busNo) return;
  try {
    const res = await getLinkableGoodsReceiptDetailApi({
      busNo,
      excludeReturnBusNo: headerBusNo.value,
    });
    const data = res.data;
    if (!data?.lines?.length && !data?.originalBusNo && !data?.goodsReceiptBusNo) {
      ElMessage.warning('未获取到可退明细');
      return;
    }
    originalBusNo.value = data.originalBusNo ?? data.goodsReceiptBusNo ?? busNo;
    if (data.sourceLocationId) {
      sourceLocationId.value = data.sourceLocationId;
      purposeLocationId.value = data.purposeLocationId ?? data.sourceLocationId;
    }
    if (data.supplierId !== undefined && data.supplierId !== null) {
      supplierId.value = data.supplierId;
    }
    const gridRows = (data.lines ?? []).map((ln, i) => mapReturnableInboundLineToGrid(ln, i));
    returnToSupplierGridEdit.resetFormMode();
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchReturnToSupplierGridEditConfig();
    });
    linkInboundVisible.value = false;
    ElMessage.success('已带入可退明细，请核对数量后保存');
  } catch {
    /* handled */
  }
}

interface ReturnToSupplierDraftSnap {
  originalBusNo?: string;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  supplierId?: number | string;
  headApplyTime: string;
  rows: ReturnPreviewRow[];
}

const parentSync = useInvDocParentSyncSession<ReturnToSupplierDraftSnap>();

/** 侧栏切单：缓存/恢复本地草稿，避免已落库明细污染新建单 */
async function syncFromParentContext() {
  await parentSync.syncFromParentContext(
    { isDraftOrder: Boolean(props.isDraftOrder), parentBusNo: props.busNo },
    {
      getLocalPersistedBusNo: () => headerBusNo.value,
      captureDraft: () => ({
        originalBusNo: originalBusNo.value,
        sourceLocationId: sourceLocationId.value,
        purposeLocationId: purposeLocationId.value,
        supplierId: supplierId.value,
        headApplyTime: headApplyTime.value,
        rows: collectGridRows().map((r) => ({ ...r })),
      }),
      restoreDraft: async (snap) => {
        headerBusNo.value = undefined;
        detailStatusEnum.value = undefined;
        originalBusNo.value = snap.originalBusNo;
        sourceLocationId.value = snap.sourceLocationId;
        purposeLocationId.value = snap.purposeLocationId;
        supplierId.value = snap.supplierId;
        headApplyTime.value = snap.headApplyTime;
        returnToSupplierGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
        patchReturnToSupplierGridEditConfig();
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
  () => props.applyTime,
  (v) => {
    if (!props.isDraftOrder && headerBusNo.value) return;
    const s = String(v ?? '').trim();
    if (!s) return;
    headApplyTime.value = s.includes('T') ? s.replace('T', ' ') : s;
  },
);
</script>

<template>
  <div class="return-to-supplier-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
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
        @mousedown="returnToSupplierGridEdit.onSurfaceMouseDown"
      >
        <div class="shrink-0">
          <SectionTitle title="退货单信息" />
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
            class="compact-head-form return-to-supplier-head-form mt-1"
          >
            <div
              class="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
            >
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem label="药库" prop="sourceLocationId">
                <ElSelect
                  v-model="sourceLocationId"
                  placeholder="退货源药库"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                  @change="purposeLocationId = sourceLocationId"
                >
                  <ElOption
                    v-for="w in warehouseOptions"
                    :key="String(w.locationId)"
                    :label="w.displayName ?? String(w.locationId)"
                    :value="w.locationId"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="供应商">
                <ElSelect
                  v-model="supplierId"
                  placeholder="请选择供应商"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="s in supplierOptions"
                    :key="String(s.value)"
                    :label="s.label"
                    :value="s.value"
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
              <ElFormItem
                label="原进货单"
                class="return-to-supplier-head-form__inbound sm:col-span-2 lg:col-span-2"
              >
                <div class="flex min-w-0 items-center gap-2">
                  <ElInput
                    :model-value="originalBusNo ?? ''"
                    class="min-w-0 flex-1"
                    readonly
                    disabled
                    placeholder="请通过「从进货单选择」关联"
                  />
                  <ElButton
                    class="shrink-0"
                    :disabled="!canEditOriginalBusNo"
                    @click="openLinkInboundDialog"
                  >
                    从进货单选择
                  </ElButton>
                </div>
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <ElAlert
          v-if="documentEditable && !hasLinkedUpstream"
          type="warning"
          :closable="false"
          show-icon
          class="shrink-0"
          :title="INV_DOC_UPSTREAM_HINT.returnToSupplier"
        />

        <div
          ref="returnToSupplierGridKeyboardRef"
          class="return-to-supplier-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col"
          @keydown.capture="returnToSupplierGridEdit.onGridKeydown"
        >
          <Grid class="min-h-0 flex-1">
            <template #toolbar-actions>
              <InvDocGridToolbar
                :show-add="false"
                :disabled="!linesGridEditable"
                show-trace-scan
                @delete-selected="deleteItem('select')"
                @trace-scan="handleOpenTraceScan"
              />
            </template>
            <template #action="{ row }">
              <ElButton
                type="danger"
                text
                :disabled="!linesGridEditable"
                @click="deleteItem(row as ReturnPreviewRow)"
              >
                <Trash2 class="size-4" />
                删除
              </ElButton>
            </template>
          </Grid>
        </div>
      </div>
    </InvMainShell>

    <InvDocLinkPickerDialog
      v-model="linkInboundVisible"
      title="选择已同意的药库进货单"
      :table-data="linkInboundRecords"
      :loading="linkInboundLoading"
      :total="linkInboundTotal"
      :query-params="linkInboundQueryParams"
      :form-items="linkInboundFormItems"
      :table-columns="linkInboundTableColumns"
      :location-options="warehouseOptions"
      keyword-placeholder="进货单号/关键字"
      @opened="fetchLinkableGoodsReceipts"
      @query="handleLinkInboundQuery"
      @reset="handleLinkInboundReset"
      @pagination="handleLinkInboundPagination"
      @select="(row) => applyLinkableInbound(row as LinkableGoodsReceiptListRowDto)"
    >
      <template #applyTime="{ row }">
        {{
          (() => {
            const r = row as LinkableGoodsReceiptListRowDto;
            const t = r.applyTime ?? r.approvalTime;
            return t ? formatDateTime(t as string) : '';
          })()
        }}
      </template>
      <template #rowActions="{ row }">
        <ElButton
          link
          type="primary"
          @click.stop="applyLinkableInbound(row as LinkableGoodsReceiptListRowDto)"
        >
          选择
        </ElButton>
      </template>
    </InvDocLinkPickerDialog>
  </div>
  <InvDocTraceScanBridge ref="traceScanBridgeRef" scene="inventoryOut" />
</template>

<style scoped lang="scss">
.compact-head-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.compact-head-form :deep(.el-form-item__label) {
  padding-right: 8px;
}

.return-to-supplier-main-pane__grid-wrap :deep(.vxe-grid) {
  min-height: 200px;
}
</style>
