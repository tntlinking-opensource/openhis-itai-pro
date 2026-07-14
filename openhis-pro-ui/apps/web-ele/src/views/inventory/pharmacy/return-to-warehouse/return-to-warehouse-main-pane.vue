<!--
  药房退库单右侧主区：InvMainShell + InvDocActions + 单头 + 明细表格。
-->
<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';
import type { FormInstance } from 'element-plus';

import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type {
  PharmacyLocationOptionDto,
  ReturnToWarehouseDetailLineDto,
  ReturnToWarehouseLineDto,
  ReturnToWarehouseListRowDto,
  WarehouseLocationOptionDto,
} from '#/api/inventory/pharmacy/return-to-warehouse';

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
  agreeReturnToWarehouseApi,
  createReturnToWarehouseApi,
  getLinkablePharmacyStockInDetailApi,
  getReturnToWarehouseDetailApi,
  pageLinkableReturnSourceDocumentsApi,
  PHA_RTW_CATEGORY_STOCK,
  PHA_RTW_PURPOSE_TYPE_CABINET,
  PHA_RTW_SOURCE_TYPE_PHARMACY,
  rejectReturnToWarehouseApi,
  removeReturnToWarehouseApi,
  submitReturnToWarehouseApprovalApi,
  updateReturnToWarehouseApi,
  withdrawReturnToWarehouseApprovalApi,
} from '#/api/inventory/pharmacy/return-to-warehouse';
import SectionTitle from '#/components/layouts/section-title.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  preserveLocationIdForRequest,
  requireSpecificInventoryLocationId,
} from '#/utils/inventory/inventory-workbench-list';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import {
  INV_DOC_UPSTREAM_HINT,
  hasNonEmptyBusNo,
  mapReturnableLinkLineToGridRow,
  warnInventoryDocRestriction,
} from '#/utils/inventory/inventory-doc-restrictions';
import { withInvDocLinkPickerEnrichedColumns } from '#/utils/inventory/inv-doc-link-picker-display';
import {
  isStockOutDocumentEditableStatus,
  isStockOutDocumentRemovableStatus,
  buildStockOutDocActionFlags,
  stockOutStatusLabel,
  normalizeStockOutStatusSort,
} from '#/utils/inventory/stock-out-doc-actions';
import { resolveLoginPractitionerIdForRequest } from '#/utils/format/preserve-api-id';
import {
  applyHydratedInventoryDetailQtyToRow,
  applyInventoryRowQtyUnitList,
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

defineOptions({ name: 'InventoryPharmacyReturnToWarehouseMainPane' });

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
  unitList?: { label?: string; value: string; value_dictText?: string }[];
  __prevBandKind?: string;
  lotNumber: string;
  traceNo: string;
  reason: string;
  remake: string;
}

interface Props {
  busNo?: string;
  isDraftOrder?: boolean;
  resetKey?: number;
  pharmacyOptions: PharmacyLocationOptionDto[];
  warehouseOptions: WarehouseLocationOptionDto[];
  applyTime?: string;
}

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  isDraftOrder: false,
  resetKey: 0,
  pharmacyOptions: () => [],
  warehouseOptions: () => [],
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
    sourceLocationId: '药房',
    purposeLocationId: '退入药库',
  }),
);

const detailStatusEnum = ref<number | undefined>(undefined);
const headApplyTime = ref(formatDateTime(new Date()));
const saving = ref(false);

const linkSourceVisible = ref(false);
const linkSourceLoading = ref(false);
const linkSourceQueryParams = ref({ pageNo: 1, pageSize: 20, keyword: '' });
const linkSourceTotal = ref(0);
const linkSourceRecords = ref<ReturnToWarehouseListRowDto[]>([]);

const linkSourceTableColumns = computed<TableColumn[]>(() =>
  withInvDocLinkPickerEnrichedColumns(
    [
      { type: 'index', label: '序号', width: 58, align: 'center' },
      { prop: 'busNo', label: '入库单号', minWidth: 160, showOverflowTooltip: true },
      { prop: 'applyTime', label: '制单时间', minWidth: 160, slot: 'applyTime' },
      { label: '操作', width: 88, align: 'center', fixed: 'right', slot: 'rowActions' },
    ],
    { warehouseLabel: '药房' },
  ),
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
    selectedBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: '单位' }],
    lotNumber: '',
    traceNo: '',
    reason: '',
    remake: '',
  };
}

function handleReturnToWarehouseUnitBandChanged(row: ReturnPreviewRow): void {
  handleWarehouseNonInboundUnitBandChanged(row, {
    getQty: () => Number(row.itemQuantity ?? 0),
    setQty: (qty) => {
      row.itemQuantity = qty;
    },
  });
}

function mapDetailLineToGrid(ln: ReturnToWarehouseDetailLineDto, rowIdx: number): ReturnPreviewRow {
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
    unitCodeDictText: String(ln.unitCodeDictText ?? ln.unitCode ?? ''),
    lotNumber: String(ln.lotNumber ?? ''),
    traceNo: String(ln.traceNo ?? ''),
    reason: String(ln.reason ?? ''),
    remake: String(ln.remake ?? ''),
  };
  const catalog = (ln as { productCatalog?: { unitBands?: unknown; unitBandsJson?: unknown } })
    .productCatalog;
  const hydrated = hydrateInventoryDetailLineQtyForGrid({
    itemQuantity: row.itemQuantity,
    unitCode: row.unitCode,
    unitCodeDictText: row.unitCodeDictText,
    productCatalog: catalog,
  });
  applyHydratedInventoryDetailQtyToRow(row, hydrated);
  applyInventoryRowQtyUnitList(row);
  return row;
}

/** 明细行必填项是否已齐（与 validateBeforeSave 保存校验对齐） */
function isReturnToWarehouseRowComplete(row: ReturnPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.itemQuantity);
  if (!Number.isFinite(qty) || qty <= 0) return false;
  return !!String(row.unitCode ?? '').trim();
}

let returnToWarehouseGridEdit!: ReturnType<typeof useInventoryDocGridEdit<ReturnPreviewRow>>;

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
  createInventoryQtyUnitColumn({
    onUnitChange: (row) => handleReturnToWarehouseUnitBandChanged(row as ReturnPreviewRow),
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
    title: '退库原因',
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

const RETURN_TO_WAREHOUSE_EDITABLE_COLUMN_FIELDS = new Set([
  '_qtyUnit',
  'lotNumber',
  'reason',
  'remake',
  'traceNo',
]);

const RETURN_TO_WAREHOUSE_EDIT_FIELD_ORDER = [
  '_qtyUnit',
  'lotNumber',
  'traceNo',
  'reason',
  'remake',
] as const;

type ReturnToWarehouseEditField = (typeof RETURN_TO_WAREHOUSE_EDIT_FIELD_ORDER)[number];

const RETURN_TO_WAREHOUSE_VERTICAL_NAV_FIELDS = new Set<ReturnToWarehouseEditField>([
  '_qtyUnit',
  'lotNumber',
  'reason',
  'remake',
  'traceNo',
]);

const returnToWarehouseGridKeyboardRef = ref<HTMLElement | null>(null);

const [Grid, gridApi] = useVbenVxeGrid<ReturnPreviewRow>({
  gridOptions,
  gridEvents: {
    cellClick: (params: { column?: { field?: string; type?: string }; row: ReturnPreviewRow }) =>
      returnToWarehouseGridEdit.onCellClick(params),
    editClosed(params: { row: ReturnPreviewRow }) {
      returnToWarehouseGridEdit.syncRowFormMode(params.row);
    },
  },
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
});

returnToWarehouseGridEdit = useInventoryDocGridEdit<ReturnPreviewRow>({
  getGridApi: () => gridApi ?? null,
  gridWrapRef: returnToWarehouseGridKeyboardRef,
  documentEditable: () => linesGridEditable.value,
  collectRows: collectGridRows,
  config: {
    rowKey: (row) => row.id,
    productField: 'productName',
    editFieldOrder: RETURN_TO_WAREHOUSE_EDIT_FIELD_ORDER,
    editableFields: RETURN_TO_WAREHOUSE_EDITABLE_COLUMN_FIELDS,
    verticalNavFields: RETURN_TO_WAREHOUSE_VERTICAL_NAV_FIELDS,
    firstFillFieldAfterPick: '_qtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isReturnToWarehouseRowComplete,
  },
});

function patchReturnToWarehouseGridEditConfig() {
  const patched = returnToWarehouseGridEdit.patchGridOptions({
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
    patchReturnToWarehouseGridEditConfig();
    void nextTick(() => {
      if (!editable) {
        returnToWarehouseGridEdit.resetFormMode();
        if (!documentEditable.value) {
          returnToWarehouseGridEdit.clearGridEdit();
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
  sourceLocationId.value = undefined;
  purposeLocationId.value = undefined;
  initHeadApplyTimeFromContext();
  clearInventoryHeadFormValidation(headFormRef.value);
  returnToWarehouseGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchReturnToWarehouseGridEditConfig();
  });
}

async function loadDetail(busNo: string, loadGen?: number) {
  returnToWarehouseGridEdit.resetFormMode();
  returnToWarehouseGridEdit.clearGridEdit();
  try {
    const res = await getReturnToWarehouseDetailApi({ busNo });
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到退库单详情');
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
      patchReturnToWarehouseGridEditConfig();
    });
  } catch {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchReturnToWarehouseGridEditConfig();
    });
  }
}

function buildLinesPayload(rows: ReturnPreviewRow[]): ReturnToWarehouseLineDto[] {
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
      itemId:
        typeof r.itemId === 'string' && /^\d+$/.test(r.itemId)
          ? Number(r.itemId)
          : (r.itemId as number | undefined),
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
  if (!hasLinkedUpstream.value) {
    warnInventoryDocRestriction(INV_DOC_UPSTREAM_HINT.returnToWarehouse);
    return false;
  }
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一行退库明细');
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

async function persistDocument(): Promise<string | undefined> {
  if (!documentEditable.value) {
    ElMessage.warning('当前状态不允许修改');
    return undefined;
  }
  if (
    !(await validateInventoryHeadFormFields(headFormRef.value, [
      'sourceLocationId',
      'purposeLocationId',
    ]))
  ) {
    return undefined;
  }
  const rows = collectGridRows();
  if (!validateBeforeSave(rows)) return undefined;

  saving.value = true;
  try {
    const lines = buildLinesPayload(rows);
    const common = {
      sourceLocationId:
        preserveLocationIdForRequest(sourceLocationId.value) ?? sourceLocationId.value,
      purposeLocationId:
        preserveLocationIdForRequest(purposeLocationId.value) ?? purposeLocationId.value,
      sourceTypeEnum: PHA_RTW_SOURCE_TYPE_PHARMACY,
      purposeTypeEnum: PHA_RTW_PURPOSE_TYPE_CABINET,
      categoryEnum: PHA_RTW_CATEGORY_STOCK,
      lines,
    };

    if (headerBusNo.value) {
      await updateReturnToWarehouseApi({ busNo: headerBusNo.value, ...common });
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }

    const res = await createReturnToWarehouseApi({
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
  await persistDocument();
}

async function handleSaveAndSubmitDoc() {
  const no = await persistDocument();
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
    await ElMessageBox.confirm('确认删除整张退库单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await removeReturnToWarehouseApi({ busNo: no });
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
    const res = await submitReturnToWarehouseApprovalApi({
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
    const res = await withdrawReturnToWarehouseApprovalApi({ busNo: no });
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
    await agreeReturnToWarehouseApi({
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
    await rejectReturnToWarehouseApi({
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
  if (!hasLinkedUpstream.value) {
    warnInventoryDocRestriction(INV_DOC_UPSTREAM_HINT.returnToWarehouse);
    return;
  }
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
    await returnToWarehouseGridEdit.enterRowFormMode(row as ReturnPreviewRow, '_qtyUnit');
  }
}

async function deleteItem(row: 'select' | ReturnPreviewRow) {
  if (!linesGridEditable.value) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const checked = $grid.getCheckboxRecords() as ReturnPreviewRow[];
  if (row === 'select' && checked.length === 0) return;
  await $grid.remove(row === 'select' ? checked : row);
}

async function openLinkSourceDialog() {
  if (!canEditOriginalBusNo.value) {
    ElMessage.warning('仅新建单据时可关联入库单，或请先点「新增」');
    return;
  }
  if (!requireSpecificInventoryLocationId(sourceLocationId.value, 'pharmacy')) {
    return;
  }
  linkSourceQueryParams.value = { pageNo: 1, pageSize: 20, keyword: '' };
  linkSourceVisible.value = true;
}

/** 关联入库单弹窗：查询 */
function handleLinkSourceQuery(params: Record<string, unknown>) {
  Object.assign(linkSourceQueryParams.value, params);
  linkSourceQueryParams.value.pageNo = 1;
  void fetchLinkableSourceDocuments();
}

/** 关联入库单弹窗：重置 */
function handleLinkSourceReset() {
  linkSourceQueryParams.value = { pageNo: 1, pageSize: 20, keyword: '' };
  void fetchLinkableSourceDocuments();
}

/** 关联入库单弹窗：分页 */
function handleLinkSourcePagination(pagination: { limit: number; page: number }) {
  linkSourceQueryParams.value.pageNo = pagination.page;
  linkSourceQueryParams.value.pageSize = pagination.limit;
  void fetchLinkableSourceDocuments();
}

async function fetchLinkableSourceDocuments() {
  const src = requireSpecificInventoryLocationId(sourceLocationId.value, 'pharmacy');
  if (!src) {
    linkSourceRecords.value = [];
    linkSourceTotal.value = 0;
    return;
  }
  linkSourceLoading.value = true;
  try {
    const res = await pageLinkableReturnSourceDocumentsApi({
      sourceLocationId: src,
      keyword: String(linkSourceQueryParams.value.keyword ?? '').trim() || undefined,
      pageNo: linkSourceQueryParams.value.pageNo,
      pageSize: linkSourceQueryParams.value.pageSize,
    });
    const { content, totalCount } = parsePagedRowsFromApiData<ReturnToWarehouseListRowDto>(
      res.data,
    );
    linkSourceRecords.value = content;
    linkSourceTotal.value = totalCount;
  } catch {
    linkSourceRecords.value = [];
    linkSourceTotal.value = 0;
  } finally {
    linkSourceLoading.value = false;
  }
}

async function applyLinkableSource(row: ReturnToWarehouseListRowDto) {
  const busNo = String(row.busNo ?? '').trim();
  if (!busNo) return;
  try {
    const res = await getLinkablePharmacyStockInDetailApi({
      busNo,
      excludeReturnBusNo: headerBusNo.value,
    });
    const data = res.data;
    if (!data?.lines?.length && !data?.originalBusNo && !data?.stockInBusNo) {
      ElMessage.warning('未获取到可退明细');
      return;
    }
    originalBusNo.value = data.originalBusNo ?? data.stockInBusNo ?? busNo;
    if (data.sourceLocationId) {
      sourceLocationId.value = data.sourceLocationId;
    }
    if (data.purposeLocationId) {
      purposeLocationId.value = data.purposeLocationId;
    }
    const gridRows = (data.lines ?? []).map((ln, i) =>
      mapReturnableLinkLineToGridRow<ReturnPreviewRow>(ln, i),
    );
    returnToWarehouseGridEdit.resetFormMode();
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchReturnToWarehouseGridEditConfig();
    });
    linkSourceVisible.value = false;
    ElMessage.success('已带入可退明细，请核对数量后保存');
  } catch {
    /* handled */
  }
}

interface ReturnToWarehouseDraftSnap {
  originalBusNo?: string;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  headApplyTime: string;
  rows: ReturnPreviewRow[];
}

const parentSync = useInvDocParentSyncSession<ReturnToWarehouseDraftSnap>();

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
        headApplyTime: headApplyTime.value,
        rows: collectGridRows().map((r) => ({ ...r })),
      }),
      restoreDraft: async (snap) => {
        headerBusNo.value = undefined;
        detailStatusEnum.value = undefined;
        originalBusNo.value = snap.originalBusNo;
        sourceLocationId.value = snap.sourceLocationId;
        purposeLocationId.value = snap.purposeLocationId;
        headApplyTime.value = snap.headApplyTime;
        returnToWarehouseGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
        patchReturnToWarehouseGridEditConfig();
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
  <div class="return-to-warehouse-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
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
        @mousedown="returnToWarehouseGridEdit.onSurfaceMouseDown"
      >
        <div class="shrink-0">
          <SectionTitle title="退库单信息" />
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
            class="compact-head-form return-to-warehouse-head-form mt-1"
          >
            <div
              class="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
            >
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem label="药房" prop="sourceLocationId">
                <ElSelect
                  v-model="sourceLocationId"
                  placeholder="退库源药房"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="w in pharmacyOptions"
                    :key="String(w.locationId)"
                    :label="w.displayName ?? String(w.locationId)"
                    :value="w.locationId!"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="退入药库" prop="purposeLocationId">
                <ElSelect
                  v-model="purposeLocationId"
                  placeholder="退库目的药库"
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
              <ElFormItem
                label="原入库单"
                class="return-to-warehouse-head-form__source sm:col-span-2 lg:col-span-2"
              >
                <div class="flex min-w-0 items-center gap-2">
                  <ElInput
                    :model-value="originalBusNo ?? ''"
                    class="min-w-0 flex-1"
                    readonly
                    disabled
                    placeholder="请通过「从入库单选择」关联"
                  />
                  <ElButton
                    class="shrink-0"
                    :disabled="!canEditOriginalBusNo"
                    @click="openLinkSourceDialog"
                  >
                    从入库单选择
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
          :title="INV_DOC_UPSTREAM_HINT.returnToWarehouse"
        />

        <div
          ref="returnToWarehouseGridKeyboardRef"
          class="return-to-warehouse-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col"
          @keydown.capture="returnToWarehouseGridEdit.onGridKeydown"
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
      v-model="linkSourceVisible"
      title="选择已同意的药房入库单"
      :table-data="linkSourceRecords"
      :loading="linkSourceLoading"
      :total="linkSourceTotal"
      :query-params="linkSourceQueryParams"
      :table-columns="linkSourceTableColumns"
      :location-options="pharmacyOptions"
      keyword-placeholder="入库单号/关键字"
      @opened="fetchLinkableSourceDocuments"
      @query="handleLinkSourceQuery"
      @reset="handleLinkSourceReset"
      @pagination="handleLinkSourcePagination"
      @select="(row) => applyLinkableSource(row as ReturnToWarehouseListRowDto)"
    >
      <template #applyTime="{ row }">
        {{
          (row as ReturnToWarehouseListRowDto).applyTime
            ? formatDateTime((row as ReturnToWarehouseListRowDto).applyTime as string)
            : ''
        }}
      </template>
      <template #rowActions="{ row }">
        <ElButton
          link
          type="primary"
          @click.stop="applyLinkableSource(row as ReturnToWarehouseListRowDto)"
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

.return-to-warehouse-main-pane__grid-wrap :deep(.vxe-grid) {
  min-height: 200px;
}
</style>
