<!--
  药库退货入库单右侧主区。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type {
  LocationOptionDto,
  ReturnStockInDetailLineDto,
  ReturnStockInLineDto,
  WarehouseDocumentDetailRowDto,
} from '#/api/inventory/warehouse/return-stock-in';

import { computed, nextTick, reactive, ref, watch, onBeforeUnmount, onDeactivated } from 'vue';

import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocGridToolbar from '@inventory/components/inv-doc-grid-toolbar.vue';
import InvDocTraceScanBridge from '@inventory/components/inv-doc-trace-scan-bridge.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
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
  ElOptionGroup,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { useInventoryDocGridEdit } from '#/composables/use-inventory-doc-grid-edit';
import { bindInvDocCompactColumns } from '#/composables/use-inv-doc-compact-columns';
import { useInvDocParentSyncSession } from '#/composables/use-inv-doc-parent-sync-session';
import {
  clearInvDocDraftCacheAfterSave,
  INV_DOC_DRAFT_SCENE,
} from '#/utils/inventory/inv-doc-draft-cache';
import {
  agreeReturnStockInApi,
  createReturnStockInApi,
  getReturnStockInDetailApi,
  pageLinkableDeliveriesApi,
  rejectReturnStockInApi,
  removeReturnStockInApi,
  submitReturnStockInApprovalApi,
  updateReturnStockInApi,
  withdrawReturnStockInApprovalApi,
  WHS_RSI_CATEGORY_STOCK,
  WHS_RSI_LOCATION_TYPE_CABINET,
  WHS_RSI_LOCATION_TYPE_DEPARTMENT,
  WHS_RSI_LOCATION_TYPE_PHARMACY,
} from '#/api/inventory/warehouse/return-stock-in';

import type { FormInstance } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  applyWarehouseNonInboundProductPickUnitsToRow,
  applyHydratedInventoryDetailQtyToRow,
  createInventoryQtyUnitColumn,
  handleWarehouseNonInboundUnitBandChanged,
  hydrateInventoryDetailLineQtyForGrid,
} from '#/utils/inventory/inventory-doc-grid-common';
import {
  canConvertDisplayQtyToSplitForBands,
  persistDisplayQtyAsSplitForBands,
  resolveInboundLinePersistUnitCodeForBands,
  type InboundProductUnitBand,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  buildStockOutDocActionFlags,
  isStockOutDocumentRemovableStatus,
  normalizeStockOutStatusSort,
  stockOutStatusLabel,
} from '#/utils/inventory/stock-out-doc-actions';
import { resolveLoginPractitionerIdForRequest } from '#/utils/format/preserve-api-id';
import {
  resolveInvDocApprovedByName,
  resolveInvDocPreparedByName,
} from '#/utils/inventory/inventory-workbench-list';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import { INV_DOC_READONLY_SHELL_HINT } from '#/utils/inventory/inventory-doc-restrictions';
import {
  buildInvDocumentTitle,
  printInvDocument,
  resolveInvPrintProductBusNo,
} from '#/utils/print/print-inv-document';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

defineOptions({ name: 'InventoryWarehouseReturnStockInMainPane' });

interface ReturnPreviewRow {
  id: string;
  medicinePick?: Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  productName: string;
  itemQuantity?: number;
  unitCode: string;
  unitCodeDictText?: string;
  unitBands?: InboundProductUnitBand[];
  selectedBandKind?: string;
  unitList?: { label?: string; value: string }[];
  __prevBandKind?: string;
  lotNumber: string;
  traceNo: string;
  reason: string;
  remake: string;
}

interface Props {
  /** 侧栏当前单据行（制单人/审批人与队列卡片同口径） */
  queueCardRow?: null | Record<string, unknown>;
  busNo?: string;
  isDraftOrder?: boolean;
  /** 侧栏草稿稳定 id（_clientDraftId），用于 session 缓存隔离 */
  clientDraftId?: string;
  resetKey?: number;
  sidebarPurposeLocationId?: number | string;
  warehouseOptions: LocationOptionDto[];
  departmentOptions: LocationOptionDto[];
  pharmacyOptions: LocationOptionDto[];
  applyTime?: string;
}

const props = withDefaults(defineProps<Props>(), {
  queueCardRow: null,
  busNo: '',
  isDraftOrder: false,
  clientDraftId: '',
  resetKey: 0,
  sidebarPurposeLocationId: undefined,
  warehouseOptions: () => [],
  departmentOptions: () => [],
  pharmacyOptions: () => [],
  applyTime: undefined,
});

const emit = defineEmits<{
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

const userStore = useUserStore();
const HEAD_ITEM_TABLE_TYPE = 'med_medication_definition';

function nowIsoLocal(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
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
  purposeLocationId: purposeLocationId.value ?? '',
}));

const headFormRules = computed(() =>
  buildInventoryLocationHeadFormRules({
    sourceLocationId: '退货来源',
    purposeLocationId: '接收药库',
  }),
);

const detailStatusEnum = ref<number | undefined>(undefined);
const headApplyTime = ref(formatDateTime(new Date()));
const saving = ref(false);

const linkDeliveryVisible = ref(false);
const linkDeliveryKeyword = ref('');
const linkDeliveryLoading = ref(false);
const linkDeliveryPage = ref({ pageNo: 1, pageSize: 10 });
const linkDeliveryTotal = ref(0);
const linkDeliveryRecords = ref<WarehouseDocumentDetailRowDto[]>([]);

const isNewDraft = computed(() => !headerBusNo.value);
/** 壳单只读：由药房/科室退库 agree 自动生成，禁止改单 */
const isReadOnlyShell = computed(() => true);
const documentEditable = computed(() => false);
const headerStatusText = computed(() => {
  if (isNewDraft.value) return '—';
  return stockOutStatusLabel(detailStatusEnum.value);
});
const docActionFlags = computed(() => {
  const hasPersistedBusNo = Boolean(headerBusNo.value);
  const base = buildStockOutDocActionFlags({
    hasPersistedBusNo,
    isDraft: isNewDraft.value,
    status: detailStatusEnum.value,
  });
  const busy = saving.value;
  // 只读壳：仅开放打印
  return {
    ...base,
    saveDisabled: true,
    saveAndSubmitDisabled: true,
    removeDisabled: true,
    submitApprovalDisabled: true,
    withdrawDisabled: true,
    approveDisabled: true,
    rejectDisabled: true,
    printDisabled: busy || !hasPersistedBusNo,
  };
});
const canEditOriginalBusNo = computed(() => false);

function resolveSourceTypeEnum(): number {
  const sid = sourceLocationId.value;
  if (sid === undefined || sid === null) return WHS_RSI_LOCATION_TYPE_DEPARTMENT;
  const idNum = Number(sid);
  if (props.pharmacyOptions.some((p) => p.locationId === idNum)) {
    return WHS_RSI_LOCATION_TYPE_PHARMACY;
  }
  return WHS_RSI_LOCATION_TYPE_DEPARTMENT;
}

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

function syncRowFromProductPick(pick: Record<string, unknown>, row: ReturnPreviewRow) {
  row.medicinePick = inventoryPickMedicineCell(pick) ?? (pick as ReturnPreviewRow['medicinePick']);
  row.itemTable = inventoryPickStringField(pick, 'productTableName');
  const defId = pick.productDefinitionId;
  row.itemId =
    defId === undefined || defId === null || String(defId).trim() === ''
      ? undefined
      : String(defId).trim();
  row.productName =
    inventoryPickStringField(pick, 'merchandiseName') ||
    inventoryPickStringField(pick, 'productName');
  row.lotNumber = inventoryPickStringField(pick, 'lotNumber');
  applyWarehouseNonInboundProductPickUnitsToRow(pick, row);
}

function handleReturnStockInUnitBandChanged(row: ReturnPreviewRow): void {
  handleWarehouseNonInboundUnitBandChanged(row, {
    getQty: () => Number(row.itemQuantity ?? 0),
    setQty: (qty) => {
      row.itemQuantity = qty;
    },
  });
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
    selectedBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: '单位' }],
    __prevBandKind: 'LEGACY',
    lotNumber: '',
    traceNo: '',
    reason: '',
    remake: '',
  };
}

function mapDetailLineToGrid(ln: ReturnStockInDetailLineDto, rowIdx: number): ReturnPreviewRow {
  const itemIdRaw = ln.itemId;
  const idStr =
    ln.id !== undefined && ln.id !== null
      ? `ln-${String(ln.id)}`
      : itemIdRaw !== undefined && itemIdRaw !== null
        ? `ln-item-${String(itemIdRaw)}-${rowIdx}`
        : `ln-x-${rowIdx}`;
  const name = String(ln.productName ?? '').trim();
  const productBusNo = String(
    (ln.productCatalog as { productBusNo?: string } | null | undefined)?.productBusNo ?? '',
  ).trim();
  const row: ReturnPreviewRow = {
    id: idStr,
    medicinePick: name
      ? {
          id: itemIdRaw as number | string | undefined,
          name,
          ...(productBusNo ? { busNo: productBusNo } : {}),
        }
      : undefined,
    itemTable: ln.itemTable,
    itemId: itemIdRaw,
    productName: name,
    itemQuantity: ln.itemQuantity === undefined ? undefined : Number(ln.itemQuantity),
    unitCode: String(ln.unitCode ?? ''),
    unitCodeDictText: String(ln.unitCodeDictText ?? '').trim() || undefined,
    lotNumber: String(ln.lotNumber ?? ''),
    traceNo: String(ln.traceNo ?? ''),
    reason: String(ln.reason ?? ''),
    remake: String(ln.remake ?? ''),
  };
  const catalog = ln.productCatalog;
  const hydrated = hydrateInventoryDetailLineQtyForGrid({
    itemQuantity: row.itemQuantity,
    unitCode: row.unitCode,
    unitCodeDictText: row.unitCodeDictText,
    productCatalog: catalog,
  });
  applyHydratedInventoryDetailQtyToRow(row, hydrated);
  return row;
}

function isReturnStockInRowComplete(row: ReturnPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.itemQuantity);
  if (!Number.isFinite(qty) || qty <= 0) return false;
  return !!String(row.unitCode ?? '').trim();
}

let returnStockInGridEdit!: ReturnType<typeof useInventoryDocGridEdit<ReturnPreviewRow>>;

function handleReturnStockInProductPick(row: ReturnPreviewRow) {
  void nextTick(async () => {
    await nextTick();
    await returnStockInGridEdit.onProductSelected(row);
  });
}

const columns: VxeGridPropTypes.Column[] = [
  { type: 'seq', title: '序号', width: 52, fixed: 'left', align: 'center' },
  {
    field: 'medicinePick',
    title: '项目名称',
    minWidth: 200,
    fixed: 'left',
    editRender: {
      name: 'PurchaseOrderMedicineSelect',
      props: {
        listSource: 'return-stock-in',
        query: async () => ({
          productTableName: HEAD_ITEM_TABLE_TYPE,
          sourceLocationId: sourceLocationId.value,
        }),
        onSelected: (product: Record<string, unknown>, row: ReturnPreviewRow) => {
          syncRowFromProductPick(product, row);
          handleReturnStockInProductPick(row);
        },
      },
    },
    showOverflow: true,
  },
  createInventoryQtyUnitColumn({
    onUnitChange: (row) => handleReturnStockInUnitBandChanged(row as ReturnPreviewRow),
  }),
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
    enabled: false,
    trigger: 'click',
    mode: 'row',
    autoClear: false,
    beforeEditMethod: () => documentEditable.value,
  },
  columns,
  pagerConfig: { enabled: false },
});

const RETURN_STOCK_IN_EDITABLE_COLUMN_FIELDS = new Set([
  '_qtyUnit',
  'lotNumber',
  'medicinePick',
  'reason',
  'remake',
  'traceNo',
]);

const RETURN_STOCK_IN_EDIT_FIELD_ORDER = [
  'medicinePick',
  '_qtyUnit',
  'lotNumber',
  'traceNo',
  'reason',
  'remake',
] as const;

type ReturnStockInEditField = (typeof RETURN_STOCK_IN_EDIT_FIELD_ORDER)[number];

const RETURN_STOCK_IN_VERTICAL_NAV_FIELDS = new Set<ReturnStockInEditField>([
  '_qtyUnit',
  'lotNumber',
  'reason',
  'remake',
  'traceNo',
]);

const returnStockInGridKeyboardRef = ref<HTMLElement | null>(null);

const [Grid, gridApi] = useVbenVxeGrid<ReturnPreviewRow>({
  gridOptions,
  gridEvents: {
    cellClick: (params: { column?: { field?: string; type?: string }; row: ReturnPreviewRow }) =>
      returnStockInGridEdit.onCellClick(params),
    editClosed(params: { row: ReturnPreviewRow }) {
      returnStockInGridEdit.syncRowFormMode(params.row);
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

returnStockInGridEdit = useInventoryDocGridEdit<ReturnPreviewRow>({
  getGridApi: () => gridApi ?? null,
  gridWrapRef: returnStockInGridKeyboardRef,
  documentEditable: () => documentEditable.value,
  collectRows: collectGridRows,
  config: {
    rowKey: (row) => row.id,
    productField: 'medicinePick',
    editFieldOrder: RETURN_STOCK_IN_EDIT_FIELD_ORDER,
    editableFields: RETURN_STOCK_IN_EDITABLE_COLUMN_FIELDS,
    verticalNavFields: RETURN_STOCK_IN_VERTICAL_NAV_FIELDS,
    firstFillFieldAfterPick: '_qtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isReturnStockInRowComplete,
  },
});

function patchReturnStockInGridEditConfig() {
  const patched = returnStockInGridEdit.patchGridOptions({
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
    patchReturnStockInGridEditConfig();
    void nextTick(() => {
      if (!editable) {
        returnStockInGridEdit.resetFormMode();
        returnStockInGridEdit.clearGridEdit();
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
  sourceLocationId.value = undefined;
  purposeLocationId.value = undefined;
  initHeadApplyTimeFromContext();
  clearInventoryHeadFormValidation(headFormRef.value);
  returnStockInGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchReturnStockInGridEditConfig();
  });
}

async function loadDetail(busNo: string, loadGen?: number) {
  returnStockInGridEdit.resetFormMode();
  returnStockInGridEdit.clearGridEdit();
  try {
    const res = await getReturnStockInDetailApi({ busNo });
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到退货入库单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    originalBusNo.value = data.originalBusNo ?? undefined;
    detailStatusEnum.value = normalizeStockOutStatusSort(data.statusEnum);
    sourceLocationId.value = data.sourceLocationId ?? undefined;
    purposeLocationId.value = data.purposeLocationId ?? undefined;

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
      patchReturnStockInGridEditConfig();
    });
  } catch {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchReturnStockInGridEditConfig();
    });
  }
}

function buildLinesPayload(rows: ReturnPreviewRow[]): ReturnStockInLineDto[] {
  return rows.map((r) => {
    const persisted = persistDisplayQtyAsSplitForBands({
      bands: r.unitBands ?? [],
      displayQty: r.itemQuantity,
      legacyUnitCode: r.unitCode,
      selectedBandKind: r.selectedBandKind,
    });
    const unitCode =
      resolveInboundLinePersistUnitCodeForBands({
        bands: r.unitBands ?? [],
        legacyUnitCode: r.unitCode,
        selectedBandKind: r.selectedBandKind,
      }) ?? String(r.unitCode ?? '').trim();
    return {
      itemTable: r.itemTable,
      itemId: isPresentItemId(r.itemId) ? String(r.itemId) : undefined,
      itemQuantity: persisted.itemQuantity,
      unitCode: unitCode || undefined,
      lotNumber: String(r.lotNumber ?? '').trim() || undefined,
      traceNo: String(r.traceNo ?? '').trim(),
      occurrenceTime: resolveOccurrenceTimeForApi(),
      reason: String(r.reason ?? '').trim() || undefined,
      remake: String(r.remake ?? '').trim() || undefined,
    };
  });
}

function validateBeforeSave(rows: ReturnPreviewRow[]): boolean {
  const src = sourceLocationId.value;
  if (src === undefined || src === null || String(src).trim() === '') {
    ElMessage.warning('请选择退货来源（科室/药房）');
    return false;
  }
  const purpose = purposeLocationId.value;
  if (purpose === undefined || purpose === null || String(purpose).trim() === '') {
    ElMessage.warning('请选择接收药库');
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
      ElMessage.warning(`第 ${i + 1} 行无法将数量换算为拆零单位，请检查计量带`);
      return false;
    }
  }
  return true;
}

async function persistDocument(): Promise<string | undefined> {
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
      sourceLocationId: Number(sourceLocationId.value),
      purposeLocationId: Number(purposeLocationId.value),
      sourceTypeEnum: resolveSourceTypeEnum(),
      purposeTypeEnum: WHS_RSI_LOCATION_TYPE_CABINET,
      categoryEnum: WHS_RSI_CATEGORY_STOCK,
      lines,
    };
    const original = originalBusNo.value?.trim();

    if (headerBusNo.value) {
      await updateReturnStockInApi({ busNo: headerBusNo.value, ...common });
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }

    const res = await createReturnStockInApi({
      ...(original ? { originalBusNo: original } : {}),
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

/** 落库成功后清 session 草稿缓存 */
function clearDraftPersistAfterSave(busNo?: string) {
  clearInvDocDraftCacheAfterSave({
    scene: INV_DOC_DRAFT_SCENE.warehouseReturnStockIn,
    clientDraftId: props.clientDraftId,
    busNo,
  });
  parentSync.clearDraftSnap();
}

async function handleSaveDoc() {
  const no = await persistDocument();
  if (!no) return;
  clearDraftPersistAfterSave(no);
}

async function handleSaveAndSubmitDoc() {
  const no = await persistDocument();
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
      scene: INV_DOC_DRAFT_SCENE.warehouseReturnStockIn,
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
    await ElMessageBox.confirm('确认删除整张退货入库单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await removeReturnStockInApi({ busNo: no });
    clearInvDocDraftCacheAfterSave({
      scene: INV_DOC_DRAFT_SCENE.warehouseReturnStockIn,
      busNo: no,
    });
    parentSync.clearDraftSnap();
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
    const res = await submitReturnStockInApprovalApi({
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
    const res = await withdrawReturnStockInApprovalApi({ busNo: no });
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

async function handleApprove() {
  const no = headerBusNo.value;
  if (!no) return;
  const approverId = resolveLoginPractitionerIdForRequest(
    userStore.userInfo as null | Record<string, unknown>,
  );
  try {
    await agreeReturnStockInApi({
      busNo: no,
      approverId,
      approvalClock: nowIsoLocal(),
    });
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
    await rejectReturnStockInApi({
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
  if (
    !(await validateInventoryHeadFormFields(headFormRef.value, [
      'sourceLocationId',
      'purposeLocationId',
    ]))
  ) {
    return;
  }
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const record = createEmptyRow();
  const { row } = await $grid.insertAt(record, null);
  await $grid.$nextTick();
  if (row) {
    await $grid.scrollToRow(row);
    await returnStockInGridEdit.enterRowFormMode(row as ReturnPreviewRow, 'medicinePick');
  }
}

async function deleteItem(row: 'select' | ReturnPreviewRow) {
  if (!documentEditable.value) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const checked = $grid.getCheckboxRecords() as ReturnPreviewRow[];
  if (row === 'select' && checked.length === 0) return;
  await $grid.remove(row === 'select' ? checked : row);
}

async function openLinkDeliveryDialog() {
  if (!canEditOriginalBusNo.value) {
    ElMessage.warning('仅新建单据时可关联退库单');
    return;
  }
  const src = sourceLocationId.value;
  if (src === undefined || src === null) {
    ElMessage.warning('请先选择退货来源');
    return;
  }
  linkDeliveryVisible.value = true;
  linkDeliveryKeyword.value = '';
  linkDeliveryPage.value.pageNo = 1;
  await fetchLinkableDeliveries();
}

async function fetchLinkableDeliveries() {
  const src = sourceLocationId.value;
  if (src === undefined || src === null) {
    linkDeliveryRecords.value = [];
    linkDeliveryTotal.value = 0;
    return;
  }
  linkDeliveryLoading.value = true;
  try {
    const res = await pageLinkableDeliveriesApi({
      sourceLocationId: Number(src),
      keyword: linkDeliveryKeyword.value.trim() || undefined,
      pageNo: linkDeliveryPage.value.pageNo,
      pageSize: linkDeliveryPage.value.pageSize,
    });
    const { content, totalCount } = parsePagedRowsFromApiData<WarehouseDocumentDetailRowDto>(
      res.data,
    );
    linkDeliveryRecords.value = content;
    linkDeliveryTotal.value = totalCount;
  } catch {
    linkDeliveryRecords.value = [];
    linkDeliveryTotal.value = 0;
  } finally {
    linkDeliveryLoading.value = false;
  }
}

function handleSelectLinkableDelivery(row: WarehouseDocumentDetailRowDto) {
  const no = String(row.busNo ?? '').trim();
  if (!no) return;
  originalBusNo.value = no;
  linkDeliveryVisible.value = false;
  ElMessage.success(`已关联退库单 ${no}`);
}

interface ReturnStockInDraftSnap {
  originalBusNo?: string;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  headApplyTime: string;
  rows: ReturnPreviewRow[];
}

const parentSync = useInvDocParentSyncSession<ReturnStockInDraftSnap>({
  scene: INV_DOC_DRAFT_SCENE.warehouseReturnStockIn,
  getDocKey: () => {
    const draftId = String(props.clientDraftId ?? '').trim();
    if (props.isDraftOrder && draftId) return draftId;
    return String(props.busNo ?? headerBusNo.value ?? '').trim() || undefined;
  },
  getCardMeta: () => ({
    statusEnum_enumText: '编辑中',
    applyTime: headApplyTime.value,
  }),
});

/** 拍摄当前主区草稿快照（侧栏切单 / 落盘共用） */
function captureReturnStockInDraftSnap(): ReturnStockInDraftSnap {
  return {
    originalBusNo: originalBusNo.value,
    sourceLocationId: sourceLocationId.value,
    purposeLocationId: purposeLocationId.value,
    headApplyTime: headApplyTime.value,
    rows: collectGridRows().map((r) => ({ ...r })),
  };
}

/** 侧栏切单：缓存/恢复本地草稿，避免已落库明细污染新建单 */
async function syncFromParentContext() {
  await parentSync.syncFromParentContext(
    { isDraftOrder: Boolean(props.isDraftOrder), parentBusNo: props.busNo },
    {
      getLocalPersistedBusNo: () => headerBusNo.value,
      captureDraft: captureReturnStockInDraftSnap,
      restoreDraft: async (snap) => {
        headerBusNo.value = undefined;
        detailStatusEnum.value = undefined;
        originalBusNo.value = snap.originalBusNo;
        sourceLocationId.value = snap.sourceLocationId;
        purposeLocationId.value = snap.purposeLocationId;
        headApplyTime.value = snap.headApplyTime;
        returnStockInGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
        patchReturnStockInGridEditConfig();
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
function flushReturnStockInDraftPersist(): void {
  parentSync.flushPersist({
    getLocalPersistedBusNo: () => headerBusNo.value,
    captureDraft: captureReturnStockInDraftSnap,
  });
}

onBeforeUnmount(() => {
  flushReturnStockInDraftPersist();
});

onDeactivated(() => {
  flushReturnStockInDraftPersist();
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
  () => props.applyTime,
  (v) => {
    if (!props.isDraftOrder && headerBusNo.value) return;
    const s = String(v ?? '').trim();
    if (!s) return;
    headApplyTime.value = s.includes('T') ? s.replace('T', ' ') : s;
  },
);

/** 解析库位展示名 */
function resolvePrintLocationName(
  options: { locationId?: number | string; displayName?: string }[] | undefined,
  value: unknown,
): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = (options ?? []).find((row) => String(row.locationId ?? '') === key);
  return String(hit?.displayName ?? hit?.locationId ?? '').trim();
}

/** 明细行当前计量单位展示文案 */
function resolveReturnStockInPrintUnitLabel(row: ReturnPreviewRow): string {
  const kind = String(row.selectedBandKind ?? '').trim();
  const byKind = row.unitList?.find((item) => String(item.value ?? '') === kind);
  if (byKind) {
    return String(byKind.label ?? '').trim();
  }
  return String(row.unitCodeDictText ?? row.unitCode ?? '').trim();
}

/** 打印退库入库单 */
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

  const sourceName = resolvePrintLocationName(
    [...(props.pharmacyOptions ?? []), ...(props.warehouseOptions ?? [])],
    sourceLocationId.value,
  );
  const warehouseName = resolvePrintLocationName(props.warehouseOptions, purposeLocationId.value);
  const documentTitle = buildInvDocumentTitle(warehouseName || sourceName, '退货入库单');
  const applyTimeText = String(headApplyTime.value ?? props.applyTime ?? '')
    .trim()
    .replace('T', ' ');

  // 按当前网格展示值组装退货明细
  const lineRows = productRows.map((row) => ({
    productBusNo: resolveInvPrintProductBusNo(row as unknown as Record<string, unknown>),
    productName: String(row.productName ?? '').trim(),
    qty: row.itemQuantity,
    unitLabel: resolveReturnStockInPrintUnitLabel(row),
    lotNumber: String(row.lotNumber ?? '').trim(),
    reasonText: String(row.reason ?? '').trim(),
  }));

  const preparedByName = resolveInvDocPreparedByName(
    props.queueCardRow as null | Record<string, unknown>,
  );
  const approvedByName = resolveInvDocApprovedByName(
    props.queueCardRow as null | Record<string, unknown>,
  );
  await printInvDocument({
    family: 'return',
    source: {
      hospitalName: resolvePrintHospitalName(),
      documentTitle,
      docNo: busNo,
      applyTimeText,
      itemTypeText: '',
      sourceLocationText: sourceName,
      supplierText: '',
      linkDocText: String(originalBusNo.value ?? '').trim(),
      destLocationText: warehouseName,
      invoiceNoText: '',
      lineRows,
      preparedByName,
      approvedByName,
    },
    bizRef: { busNo, docKind: 'return-stock-in' },
    jobName: documentTitle,
  });
}
</script>

<template>
  <div class="return-stock-in-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
    <InvMainShell>
      <template #actionBar>
        <div class="flex w-full flex-wrap items-center justify-end gap-2">
          <InvDocActions
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
        @mousedown="returnStockInGridEdit.onSurfaceMouseDown"
      >
        <div class="return-stock-in-head-panel shrink-0">
          <div
            class="return-stock-in-head-panel__bar flex flex-wrap items-center justify-between gap-x-4 gap-y-2"
          >
            <div>
              <SectionTitle title="退货入库单信息" />
              <p
                v-if="isReadOnlyShell"
                class="mt-1 text-xs"
                style="color: var(--el-text-color-secondary)"
              >
                {{ INV_DOC_READONLY_SHELL_HINT.returnStockIn }}
              </p>
            </div>
            <div class="flex flex-wrap items-center gap-2">
              <span class="text-xs text-[var(--el-text-color-secondary)]">状态</span>
              <ElTag size="small" type="info" effect="plain">{{ headerStatusText || '—' }}</ElTag>
              <span
                v-if="headerBusNo"
                class="text-xs"
                style="color: var(--el-text-color-secondary)"
              >
                单号 {{ headerBusNo }}
              </span>
            </div>
          </div>
          <ElForm
            ref="headFormRef"
            :model="headFormModel"
            :rules="headFormRules"
            label-width="96px"
            class="compact-head-form"
          >
            <div
              class="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
            >
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem label="退货来源" prop="sourceLocationId">
                <ElSelect
                  v-model="sourceLocationId"
                  placeholder="科室或药房"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOptionGroup v-if="departmentOptions.length > 0" label="科室">
                    <ElOption
                      v-for="d in departmentOptions"
                      :key="`dept-${d.locationId}`"
                      :label="d.displayName ?? String(d.locationId)"
                      :value="d.locationId!"
                    />
                  </ElOptionGroup>
                  <ElOptionGroup v-if="pharmacyOptions.length > 0" label="药房">
                    <ElOption
                      v-for="p in pharmacyOptions"
                      :key="`pha-${p.locationId}`"
                      :label="p.displayName ?? String(p.locationId)"
                      :value="p.locationId!"
                    />
                  </ElOptionGroup>
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="接收药库" prop="purposeLocationId">
                <ElSelect
                  v-model="purposeLocationId"
                  placeholder="退货目的药库"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="w in warehouseOptions"
                    :key="String(w.locationId)"
                    :label="w.displayName ?? String(w.locationId)"
                    :value="w.locationId!"
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
              <ElFormItem label="关联退库单" class="sm:col-span-2 lg:col-span-2">
                <div class="flex min-w-0 items-center gap-2">
                  <ElInput
                    v-model="originalBusNo"
                    class="min-w-0 flex-1"
                    clearable
                    maxlength="64"
                    placeholder="上游退库单号"
                    :disabled="!canEditOriginalBusNo"
                  />
                  <ElButton
                    v-if="!isReadOnlyShell"
                    class="shrink-0"
                    :disabled="!canEditOriginalBusNo"
                    @click="openLinkDeliveryDialog"
                  >
                    从退库单选择
                  </ElButton>
                </div>
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <div
          ref="returnStockInGridKeyboardRef"
          class="return-stock-in-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col"
          @keydown.capture="returnStockInGridEdit.onGridKeydown"
        >
          <Grid class="min-h-0 flex-1">
            <template v-if="!isReadOnlyShell" #toolbar-actions>
              <InvDocGridToolbar
                :disabled="!documentEditable"
                show-trace-scan
                @add="addItem"
                @delete-selected="deleteItem('select')"
                @trace-scan="handleOpenTraceScan"
              />
            </template>
          </Grid>
        </div>
      </div>
    </InvMainShell>

    <ElDialog
      v-if="!isReadOnlyShell"
      v-model="linkDeliveryVisible"
      destroy-on-close
      title="选择可关联的退库单"
      width="720px"
      @opened="fetchLinkableDeliveries"
    >
      <div class="mb-2 flex gap-2">
        <ElInput
          v-model="linkDeliveryKeyword"
          clearable
          placeholder="退库单号/关键字"
          size="small"
          style="max-width: 260px"
          @keyup.enter="fetchLinkableDeliveries"
        />
        <ElButton size="small" type="primary" @click="fetchLinkableDeliveries">检索</ElButton>
      </div>
      <ElTable
        v-loading="linkDeliveryLoading"
        :data="linkDeliveryRecords"
        size="small"
        highlight-current-row
        @row-dblclick="handleSelectLinkableDelivery"
      >
        <ElTableColumn prop="busNo" label="退库单号" min-width="140" show-overflow-tooltip />
        <ElTableColumn prop="applyTime" label="制单时间" min-width="140" show-overflow-tooltip />
        <ElTableColumn label="操作" width="88" align="center">
          <template #default="{ row }">
            <ElButton link type="primary" size="small" @click="handleSelectLinkableDelivery(row)">
              选择
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <div class="mt-3 flex justify-end">
        <ElPagination
          v-model:current-page="linkDeliveryPage.pageNo"
          v-model:page-size="linkDeliveryPage.pageSize"
          small
          layout="total, prev, pager, next"
          :total="linkDeliveryTotal"
          @current-change="fetchLinkableDeliveries"
          @size-change="fetchLinkableDeliveries"
        />
      </div>
    </ElDialog>
  </div>
  <InvDocTraceScanBridge
    v-if="!isReadOnlyShell"
    ref="traceScanBridgeRef"
    scene="inventoryOut"
    :default-location-id="sourceLocationId"
  />
</template>

<style scoped lang="scss">
.return-stock-in-head-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 12px 4px;
  background: var(--el-bg-color);
}

.return-stock-in-main-pane__grid-wrap :deep(.vxe-grid) {
  min-height: 200px;
}
</style>
