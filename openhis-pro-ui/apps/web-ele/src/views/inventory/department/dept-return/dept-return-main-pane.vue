<!--
  科室退库单右侧主区。
-->
<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { FormInstance } from 'element-plus';
import type {
  DeptReturnDetailLineDto,
  DeptReturnLineDto,
  DeptReturnListRowDto,
  LocationOptionDto,
} from '#/api/inventory/department/dept-return';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, reactive, ref, watch, onBeforeUnmount, onDeactivated } from 'vue';

import { Trash2 } from '@vben/icons';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocGridToolbar from '@inventory/components/inv-doc-grid-toolbar.vue';
import InvDocTraceScanBridge from '@inventory/components/inv-doc-trace-scan-bridge.vue';
import InvDocLinkPickerDialog from '@inventory/components/inv-doc-link-picker-dialog.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
import InvPrimaryBtn from '@inventory/components/inv-primary-btn.vue';
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
  agreeDeptReturnApi,
  createDeptReturnApi,
  DEP_RT_CATEGORY_STOCK,
  DEP_RT_PURPOSE_TYPE_CABINET,
  DEP_RT_SOURCE_TYPE_DEPARTMENT,
  getDeptReturnDetailApi,
  getLinkableStockInDetailApi,
  pageLinkableReturnSourceDocumentsApi,
  rejectDeptReturnApi,
  removeDeptReturnApi,
  submitDeptReturnApprovalApi,
  updateDeptReturnApi,
  withdrawDeptReturnApprovalApi,
} from '#/api/inventory/department/dept-return';
import SectionTitle from '#/components/layouts/section-title.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  INV_ALL_DEPT_PHARMACIES_LABEL,
  INV_ALL_LOCATIONS_FILTER_ID,
  INV_ITEM_TABLE_TYPE_DEVICE,
  INV_ITEM_TABLE_TYPE_MEDICATION,
  INVENTORY_ITEM_TABLE_TYPE_LABELS,
  preserveLocationIdForRequest,
  isSpecificInventoryLocationId,
  resolveDocHeadProductPickLocationId,
  resolveSpecificInventoryLocationId,
  buildInventoryDocPractitionerSelectOptions,
  coercePractitionerIdForSelect,
  resolveDefaultPractitionerIdForSelect,
  resolveInvDocPreparedByName,
  resolveInvDocApprovedByName,
} from '#/utils/inventory/inventory-workbench-list';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import {
  INV_DOC_UPSTREAM_WELCOME,
  hasNonEmptyBusNo,
  mapReturnableLinkLineToGridRow,
} from '#/utils/inventory/inventory-doc-restrictions';
import { withInvDocLinkPickerEnrichedColumns } from '#/utils/inventory/inv-doc-link-picker-display';
import {
  isStockOutDocumentEditableStatus,
  isStockOutDocumentRemovableStatus,
  buildStockOutDocActionFlags,
  stockOutStatusLabel,
  normalizeStockOutStatusSort,
} from '#/utils/inventory/stock-out-doc-actions';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import {
  applyHydratedInventoryDetailQtyToRow,
  applyInventoryRowQtyUnitList,
  applyWarehouseNonInboundProductPickUnitsToRow,
  createInventoryQtyUnitColumn,
  handleWarehouseNonInboundUnitBandChanged,
  hydrateInventoryDetailLineQtyForGrid,
  scheduleInventoryProductPickFocus,
} from '#/utils/inventory/inventory-doc-grid-common';
import {
  inventoryPickAvailableQtyAsStockDisplay,
  inventoryPickMedicineCell,
  inventoryPickStringField,
} from '#/utils/inventory/inventory-product-pick-list';
import {
  canConvertDisplayQtyToSplitForBands,
  persistDisplayQtyAsSplitForBands,
  resolveInboundLinePersistUnitCodeForBands,
  type InboundProductUnitBand,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  buildInvDocumentTitle,
  printInvDocument,
  resolveInvPrintProductBusNo,
} from '#/utils/print/print-inv-document';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

defineOptions({ name: 'InventoryDepartmentDeptReturnMainPane' });

interface MedicinePickCell {
  id?: number | string;
  name?: string;
}

interface ReturnPreviewRow {
  id: string;
  medicinePick?: MedicinePickCell | Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  inventoryItemId?: number | string;
  productName: string;
  specification?: string;
  manufacturer?: string;
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
  /** 侧栏当前单据行（制单人/审批人与队列卡片同口径） */
  queueCardRow?: null | Record<string, unknown>;
  busNo?: string;
  isDraftOrder?: boolean;
  /** 侧栏草稿稳定 id（_clientDraftId），用于 session 缓存隔离 */
  clientDraftId?: string;
  resetKey?: number;
  showWelcomeEmpty?: boolean;
  deptOptions: LocationOptionDto[];
  warehouseOptions: LocationOptionDto[];
  practitionerOptions?: ApiOptionItem[];
  applyTime?: string;
}

const props = withDefaults(defineProps<Props>(), {
  queueCardRow: null,
  busNo: '',
  isDraftOrder: false,
  clientDraftId: '',
  resetKey: 0,
  showWelcomeEmpty: false,
  deptOptions: () => [],
  warehouseOptions: () => [],
  practitionerOptions: () => [],
  applyTime: undefined,
});

const emit = defineEmits<{
  createBlank: [];
  createFromUpstream: [row: DeptReturnListRowDto];
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

/** 单头「项目类型」选项（选品 productTableName） */
const itemTableTypeOptions = [
  {
    value: INV_ITEM_TABLE_TYPE_MEDICATION,
    label: INVENTORY_ITEM_TABLE_TYPE_LABELS[INV_ITEM_TABLE_TYPE_MEDICATION],
  },
  {
    value: INV_ITEM_TABLE_TYPE_DEVICE,
    label: INVENTORY_ITEM_TABLE_TYPE_LABELS[INV_ITEM_TABLE_TYPE_DEVICE],
  },
];

const userStore = useUserStore();

const pinnedPractitionerOptions = ref<ApiOptionItem[]>([]);

const effectivePractitionerOptions = computed(() =>
  buildInventoryDocPractitionerSelectOptions(props.practitionerOptions, {
    pinned: pinnedPractitionerOptions.value,
    userInfo: userStore.userInfo as null | Record<string, unknown>,
  }),
);

/** 详情经手人不在下拉时临时 pin，避免 ElSelect 只显示裸 id。 */
function pinPractitionerOption(id: unknown, label?: string) {
  const v = String(id ?? '').trim();
  if (v === '') return;
  const lbl = String(label ?? '').trim();
  if (effectivePractitionerOptions.value.some((o) => o.value === v)) return;
  if (lbl !== '' && lbl !== v) {
    pinnedPractitionerOptions.value = [{ label: lbl, value: v }];
  }
}

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
/** 科室单据默认耗材（对偶领用/请领；耗材房选「药品」会查空） */
const headItemTableType = ref(INV_ITEM_TABLE_TYPE_DEVICE);
const practitionerId = ref<number | string | undefined>(undefined);
const headFormRef = ref<FormInstance>();

const headFormModel = computed(() => ({
  sourceLocationId: sourceLocationId.value ?? '',
  purposeLocationId: purposeLocationId.value ?? '',
}));

/** 选品门禁：跟单头「科室」，不用侧栏筛选库 */
const productPickGateLocationId = computed(() =>
  resolveDocHeadProductPickLocationId(sourceLocationId.value, props.deptOptions),
);

const headFormRules = computed(() =>
  buildInventoryLocationHeadFormRules({
    sourceLocationId: '科室',
    purposeLocationId: '退入药库',
  }),
);

const detailStatusEnum = ref<number | undefined>(undefined);
const headApplyTime = ref(formatDateTime(new Date()));
const saving = ref(false);

const linkSourceVisible = ref(false);
const linkSourceLoading = ref(false);
const linkSourceQueryParams = ref<{
  keyword: string;
  pageNo: number;
  pageSize: number;
  pharmacyId: number | string;
}>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  pharmacyId: INV_ALL_LOCATIONS_FILTER_ID,
});
const linkSourceTotal = ref(0);
const linkSourceRecords = ref<DeptReturnListRowDto[]>([]);

/** 关联入库单弹窗：科室药房下拉（含「全部科室药房」） */
const linkSourcePharmacyOptions = computed(() => {
  const rows = (props.deptOptions ?? [])
    .filter((row) => isSpecificInventoryLocationId(row.locationId))
    .map((row) => ({
      label: String(row.displayName ?? row.locationId ?? ''),
      value: row.locationId as number | string,
    }));
  return [{ label: INV_ALL_DEPT_PHARMACIES_LABEL, value: INV_ALL_LOCATIONS_FILTER_ID }, ...rows];
});

function resolveLinkSourceLocationIdForApi(): number | string {
  const id = resolveSpecificInventoryLocationId(linkSourceQueryParams.value.pharmacyId);
  if (id) return id;
  return INV_ALL_LOCATIONS_FILTER_ID;
}

function normalizeLinkSourcePharmacyId(raw: unknown): number | string {
  if (Array.isArray(raw)) {
    const first = raw[0];
    if (first === undefined || first === null) return INV_ALL_LOCATIONS_FILTER_ID;
    return first as number | string;
  }
  if (raw === undefined || raw === null || raw === '') return INV_ALL_LOCATIONS_FILTER_ID;
  return raw as number | string;
}

function handleLinkSourcePharmacyChange(value: unknown) {
  linkSourceQueryParams.value.pharmacyId = normalizeLinkSourcePharmacyId(value);
  linkSourceQueryParams.value.pageNo = 1;
  void fetchLinkableSourceDocuments();
}

const linkSourceFormItems = computed<FormItemConfig[]>(() => [
  {
    label: '科室药房',
    prop: 'pharmacyId',
    type: 'select',
    placeholder: INV_ALL_DEPT_PHARMACIES_LABEL,
    filterable: true,
    clearable: false,
    multiple: false,
    options: linkSourcePharmacyOptions.value,
    onChange: handleLinkSourcePharmacyChange,
  },
  {
    label: '关键字',
    prop: 'keyword',
    type: 'input',
    placeholder: '入库单号/关键字',
  },
]);

const linkSourceTableColumns = computed<TableColumn[]>(() =>
  withInvDocLinkPickerEnrichedColumns(
    [
      { type: 'index', label: '序号', width: 58, align: 'center' },
      { prop: 'busNo', label: '入库单号', minWidth: 160, showOverflowTooltip: true },
      { prop: 'applyTime', label: '制单时间', minWidth: 160, slot: 'applyTime' },
      { label: '操作', width: 88, align: 'center', fixed: 'right', slot: 'rowActions' },
    ],
    { warehouseLabel: '科室药房' },
  ),
);

const isNewDraft = computed(() => !headerBusNo.value);
const documentEditable = computed(
  () => isNewDraft.value || isStockOutDocumentEditableStatus(detailStatusEnum.value),
);
const hasLinkedUpstream = computed(() => hasNonEmptyBusNo(originalBusNo.value));
const linesGridEditable = computed(() => documentEditable.value);
const headerStatusText = computed(() => {
  if (isNewDraft.value) return '编辑中';
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
  return {
    ...base,
    saveDisabled: base.saveDisabled || busy,
    saveAndSubmitDisabled: base.saveAndSubmitDisabled || busy,
    printDisabled: busy || !hasPersistedBusNo,
  };
});
/** 单头「从入库单选择」：非草稿可先选后建；草稿可编辑时可更换关联 */
const canEditOriginalBusNo = computed(() => !isNewDraft.value || documentEditable.value);
const linkSourceButtonLabel = computed(() =>
  hasLinkedUpstream.value && props.isDraftOrder ? '更换入库单' : '从入库单选择',
);

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
    inventoryItemId: undefined,
    productName: '',
    specification: '',
    manufacturer: '',
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

function handleDeptReturnUnitBandChanged(row: ReturnPreviewRow): void {
  handleWarehouseNonInboundUnitBandChanged(row, {
    getQty: () => Number(row.itemQuantity ?? 0),
    setQty: (qty) => {
      row.itemQuantity = qty;
    },
  });
}

/** 库存选品回填退库明细行（与药房退库同构） */
function syncRowFromDeptReturnMedicinePick(pick: Record<string, unknown>, row: ReturnPreviewRow) {
  row.medicinePick = inventoryPickMedicineCell(pick) ?? (pick as ReturnPreviewRow['medicinePick']);
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
  row.productName = inventoryPickStringField(pick, 'productName');
  row.specification = inventoryPickStringField(pick, 'specification');
  row.manufacturer = inventoryPickStringField(pick, 'manufacturer');
  applyWarehouseNonInboundProductPickUnitsToRow(pick, row);
  row.lotNumber = inventoryPickStringField(pick, 'lotNumber');
  const availQty = inventoryPickAvailableQtyAsStockDisplay(pick);
  row.itemQuantity = Number.isFinite(availQty) ? availQty : undefined;
}

function mapDetailLineToGrid(ln: DeptReturnDetailLineDto, rowIdx: number): ReturnPreviewRow {
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
    medicinePick: name
      ? {
          id: itemIdRaw as number | string | undefined,
          name,
          ...(String(
            (ln.productCatalog as { productBusNo?: string } | null | undefined)?.productBusNo ?? '',
          ).trim()
            ? {
                busNo: String(
                  (ln.productCatalog as { productBusNo?: string }).productBusNo ?? '',
                ).trim(),
              }
            : {}),
        }
      : undefined,
    itemTable: ln.itemTable,
    itemId: itemIdRaw,
    productName: name,
    specification: String(
      (ln as { specification?: string }).specification ??
        (ln as { productCatalog?: { totalVolume?: string } }).productCatalog?.totalVolume ??
        '',
    ),
    manufacturer: String((ln as { manufacturer?: string }).manufacturer ?? ''),
    itemQuantity: ln.itemQuantity === undefined ? undefined : Number(ln.itemQuantity),
    unitCode: String(ln.unitCode ?? ''),
    // 仅回填真实字典文案；裸 unitCode 交给 hydrate/计量带解析，避免数量列显示「100 9」
    unitCodeDictText: String(ln.unitCodeDictText ?? '').trim() || undefined,
    lotNumber: String(ln.lotNumber ?? ''),
    traceNo: String(ln.traceNo ?? ''),
    reason: String(ln.reason ?? ''),
    remake: String(ln.remake ?? ''),
  };
  const hydrated = hydrateInventoryDetailLineQtyForGrid({
    itemQuantity: row.itemQuantity,
    unitCode: row.unitCode,
    unitCodeDictText: row.unitCodeDictText,
    productCatalog: ln.productCatalog,
  });
  applyHydratedInventoryDetailQtyToRow(row, hydrated);
  applyInventoryRowQtyUnitList(row);
  return row;
}

function isDeptReturnRowComplete(row: ReturnPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.itemQuantity);
  if (!Number.isFinite(qty) || qty <= 0) return false;
  if (!String(row.unitCode ?? '').trim()) return false;
  return canConvertDisplayQtyToSplitForBands(row.unitBands ?? [], qty, row.selectedBandKind);
}

let deptReturnGridEdit!: ReturnType<typeof useInventoryDocGridEdit<ReturnPreviewRow>>;

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
        listSource: 'dept-return',
        placeholder: '请输入关键字检索',
        query: async () => ({
          productTableName: headItemTableType.value || undefined,
          sourceLocationId: sourceLocationId.value ?? productPickGateLocationId.value,
        }),
        onSelected: (product: Record<string, unknown>, row: ReturnPreviewRow) => {
          syncRowFromDeptReturnMedicinePick(product, row);
          handleDeptReturnProductPick(row);
        },
      },
    },
    showOverflow: true,
  },
  {
    field: 'specification',
    title: '规格',
    width: 120,
    showOverflow: true,
  },
  {
    field: 'manufacturer',
    title: '厂家/产地',
    minWidth: 120,
    showOverflow: true,
  },
  createInventoryQtyUnitColumn({
    onUnitChange: (row) => handleDeptReturnUnitBandChanged(row as ReturnPreviewRow),
  }),
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

const DEPT_RETURN_EDITABLE_COLUMN_FIELDS = new Set([
  'medicinePick',
  '_qtyUnit',
  'reason',
  'remake',
  'traceNo',
]);

const DEPT_RETURN_EDIT_FIELD_ORDER = [
  'medicinePick',
  '_qtyUnit',
  'traceNo',
  'reason',
  'remake',
] as const;

type DeptReturnEditField = (typeof DEPT_RETURN_EDIT_FIELD_ORDER)[number];

const DEPT_RETURN_VERTICAL_NAV_FIELDS = new Set<DeptReturnEditField>([
  '_qtyUnit',
  'reason',
  'remake',
  'traceNo',
]);

const deptReturnGridKeyboardRef = ref<HTMLElement | null>(null);

const [Grid, gridApi] = useVbenVxeGrid<ReturnPreviewRow>({
  gridOptions,
  gridEvents: {
    cellClick: (params: { column?: { field?: string; type?: string }; row: ReturnPreviewRow }) =>
      deptReturnGridEdit.onCellClick(params),
    editClosed(params: { row: ReturnPreviewRow }) {
      deptReturnGridEdit.syncRowFormMode(params.row);
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

deptReturnGridEdit = useInventoryDocGridEdit<ReturnPreviewRow>({
  getGridApi: () => gridApi ?? null,
  gridWrapRef: deptReturnGridKeyboardRef,
  documentEditable: () => linesGridEditable.value,
  collectRows: collectGridRows,
  config: {
    rowKey: (row) => row.id,
    productField: 'medicinePick',
    editFieldOrder: DEPT_RETURN_EDIT_FIELD_ORDER,
    editableFields: DEPT_RETURN_EDITABLE_COLUMN_FIELDS,
    verticalNavFields: DEPT_RETURN_VERTICAL_NAV_FIELDS,
    firstFillFieldAfterPick: '_qtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isDeptReturnRowComplete,
  },
});

/** 选品后进入整行表单并聚焦数量 */
function handleDeptReturnProductPick(row: ReturnPreviewRow) {
  scheduleInventoryProductPickFocus(deptReturnGridEdit, row);
}

function patchDeptReturnGridEditConfig() {
  const patched = deptReturnGridEdit.patchGridOptions({
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
    patchDeptReturnGridEditConfig();
    void nextTick(() => {
      if (!editable) {
        deptReturnGridEdit.resetFormMode();
        if (!documentEditable.value) {
          deptReturnGridEdit.clearGridEdit();
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
  pinnedPractitionerOptions.value = [];
  sourceLocationId.value = undefined;
  purposeLocationId.value = undefined;
  headItemTableType.value = INV_ITEM_TABLE_TYPE_DEVICE;
  practitionerId.value = resolveDefaultPractitionerIdForSelect(
    effectivePractitionerOptions.value,
    userStore.userInfo as null | Record<string, unknown>,
  );
  initHeadApplyTimeFromContext();
  clearInventoryHeadFormValidation(headFormRef.value);
  deptReturnGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchDeptReturnGridEditConfig();
  });
}

async function loadDetail(busNo: string, loadGen?: number) {
  deptReturnGridEdit.resetFormMode();
  deptReturnGridEdit.clearGridEdit();
  try {
    const res = await getDeptReturnDetailApi({ busNo });
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
    pinnedPractitionerOptions.value = [];
    const detailPractitionerId = data.practitionerId ?? data.applicantId;
    pinPractitionerOption(detailPractitionerId, data.applicantName);
    practitionerId.value = coercePractitionerIdForSelect(
      detailPractitionerId,
      effectivePractitionerOptions.value,
    );

    // 回显首行物品表作为项目类型，便于继续自由选品
    const firstLineTable = String(data.lines?.[0]?.itemTable ?? '').trim();
    if (firstLineTable) {
      headItemTableType.value = firstLineTable;
    }

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
      patchDeptReturnGridEditConfig();
    });
  } catch {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchDeptReturnGridEditConfig();
    });
  }
}

function buildLinesPayload(rows: ReturnPreviewRow[]): DeptReturnLineDto[] {
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
      // Snowflake itemId 必须以 string 提交，禁止 Number() 抹精度
      itemId: isPresentItemId(r.itemId)
        ? (preserveApiIdForRequest(r.itemId) ?? String(r.itemId))
        : undefined,
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
  const handlerId = practitionerId.value;
  if (!handlerId) {
    ElMessage.warning('请选择经手人');
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
      practitionerId: handlerId,
      sourceTypeEnum: DEP_RT_SOURCE_TYPE_DEPARTMENT,
      purposeTypeEnum: DEP_RT_PURPOSE_TYPE_CABINET,
      categoryEnum: DEP_RT_CATEGORY_STOCK,
      lines,
    };

    if (headerBusNo.value) {
      await updateDeptReturnApi({ busNo: headerBusNo.value, ...common });
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }

    const linkedOriginal = String(originalBusNo.value ?? '').trim();
    const res = await createDeptReturnApi({
      ...(linkedOriginal ? { originalBusNo: linkedOriginal } : {}),
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
    scene: INV_DOC_DRAFT_SCENE.deptReturn,
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
      scene: INV_DOC_DRAFT_SCENE.deptReturn,
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
    await ElMessageBox.confirm('确认删除整张退库单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await removeDeptReturnApi({ busNo: no });
    clearInvDocDraftCacheAfterSave({
      scene: INV_DOC_DRAFT_SCENE.deptReturn,
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
    const res = await submitDeptReturnApprovalApi({
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
    const res = await withdrawDeptReturnApprovalApi({ busNo: no });
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
    await agreeDeptReturnApi({
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
    await rejectDeptReturnApi({
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
    await deptReturnGridEdit.enterRowFormMode(row as ReturnPreviewRow, 'medicinePick');
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

function openUpstreamPicker() {
  linkSourceQueryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    pharmacyId: INV_ALL_LOCATIONS_FILTER_ID,
  };
  linkSourceRecords.value = [];
  linkSourceTotal.value = 0;
  linkSourceVisible.value = true;
}

async function openLinkSourceDialog() {
  if (!canEditOriginalBusNo.value) {
    ElMessage.warning('当前单据不可关联入库单');
    return;
  }
  openUpstreamPicker();
}

/** 关联入库单弹窗：查询 */
function handleLinkSourceQuery(params: Record<string, unknown>) {
  Object.assign(linkSourceQueryParams.value, params);
  linkSourceQueryParams.value.pharmacyId = normalizeLinkSourcePharmacyId(
    linkSourceQueryParams.value.pharmacyId,
  );
  linkSourceQueryParams.value.pageNo = 1;
  void fetchLinkableSourceDocuments();
}

/** 关联入库单弹窗：重置 */
function handleLinkSourceReset() {
  linkSourceQueryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    pharmacyId: INV_ALL_LOCATIONS_FILTER_ID,
  };
  void fetchLinkableSourceDocuments();
}

/** 关联入库单弹窗：分页 */
function handleLinkSourcePagination(pagination: { limit: number; page: number }) {
  linkSourceQueryParams.value.pageNo = pagination.page;
  linkSourceQueryParams.value.pageSize = pagination.limit;
  void fetchLinkableSourceDocuments();
}

async function fetchLinkableSourceDocuments() {
  linkSourceQueryParams.value.pharmacyId = normalizeLinkSourcePharmacyId(
    linkSourceQueryParams.value.pharmacyId,
  );
  linkSourceLoading.value = true;
  try {
    const res = await pageLinkableReturnSourceDocumentsApi({
      sourceLocationId: resolveLinkSourceLocationIdForApi(),
      keyword: String(linkSourceQueryParams.value.keyword ?? '').trim() || undefined,
      pageNo: linkSourceQueryParams.value.pageNo,
      pageSize: linkSourceQueryParams.value.pageSize,
    });
    const { content, totalCount } = parsePagedRowsFromApiData<DeptReturnListRowDto>(res.data);
    linkSourceRecords.value = content;
    linkSourceTotal.value = totalCount;
  } catch {
    linkSourceRecords.value = [];
    linkSourceTotal.value = 0;
  } finally {
    linkSourceLoading.value = false;
  }
}

async function applyLinkableSource(row: DeptReturnListRowDto) {
  if (!props.isDraftOrder) {
    emit('createFromUpstream', row);
    return;
  }
  await applyLinkableSourceToDraft(row);
}

async function applyLinkableSourceToDraft(row: DeptReturnListRowDto) {
  const busNo = String(row.busNo ?? '').trim();
  if (!busNo) return;
  try {
    const res = await getLinkableStockInDetailApi({
      busNo,
      excludeReturnBusNo: headerBusNo.value,
    });
    const data = res.data;
    if (!data?.lines?.length && !data?.originalBusNo && !data?.stockInBusNo) {
      ElMessage.warning('未获取到可退明细');
      return;
    }
    originalBusNo.value = data.originalBusNo ?? data.stockInBusNo ?? busNo;
    // 库位 id 保持 string，避免 Snowflake 精度丢失导致「退入药库」下拉只显示裸 id
    if (data.sourceLocationId) {
      sourceLocationId.value =
        preserveLocationIdForRequest(data.sourceLocationId) ?? String(data.sourceLocationId);
    }
    if (data.purposeLocationId) {
      purposeLocationId.value =
        preserveLocationIdForRequest(data.purposeLocationId) ?? String(data.purposeLocationId);
    }
    const gridRows = (data.lines ?? []).map((ln, i) =>
      mapReturnableLinkLineToGridRow<ReturnPreviewRow>(ln, i),
    );
    deptReturnGridEdit.resetFormMode();
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchDeptReturnGridEditConfig();
    });
    linkSourceVisible.value = false;
    ElMessage.success('已带入可退明细，请核对数量后保存');
  } catch {
    /* handled */
  }
}

defineExpose({
  applyLinkableSourceToDraft,
  openUpstreamPicker,
});

interface DeptReturnDraftSnap {
  originalBusNo?: string;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  practitionerId?: number | string;
  headItemTableType: string;
  headApplyTime: string;
  rows: ReturnPreviewRow[];
}

const parentSync = useInvDocParentSyncSession<DeptReturnDraftSnap>({
  scene: INV_DOC_DRAFT_SCENE.deptReturn,
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
function captureDeptReturnDraftSnap(): DeptReturnDraftSnap {
  return {
    originalBusNo: originalBusNo.value,
    sourceLocationId: sourceLocationId.value,
    purposeLocationId: purposeLocationId.value,
    practitionerId: practitionerId.value,
    headItemTableType: headItemTableType.value,
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
      captureDraft: captureDeptReturnDraftSnap,
      restoreDraft: async (snap) => {
        headerBusNo.value = undefined;
        detailStatusEnum.value = undefined;
        originalBusNo.value = snap.originalBusNo;
        sourceLocationId.value = snap.sourceLocationId;
        purposeLocationId.value = snap.purposeLocationId;
        practitionerId.value = snap.practitionerId;
        headItemTableType.value = snap.headItemTableType || INV_ITEM_TABLE_TYPE_DEVICE;
        headApplyTime.value = snap.headApplyTime;
        deptReturnGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
        patchDeptReturnGridEditConfig();
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
function flushDeptReturnDraftPersist(): void {
  parentSync.flushPersist({
    getLocalPersistedBusNo: () => headerBusNo.value,
    captureDraft: captureDeptReturnDraftSnap,
  });
}

onBeforeUnmount(() => {
  flushDeptReturnDraftPersist();
});

onDeactivated(() => {
  flushDeptReturnDraftPersist();
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

/** 解析下拉选项展示名 */
function resolvePrintOptionLabel(options: ApiOptionItem[] | undefined, value: unknown): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = options?.find((item) => String(item.value ?? '') === key);
  return String(hit?.label ?? '').trim();
}

/** 明细行当前计量单位展示文案 */
function resolveDeptReturnPrintUnitLabel(row: ReturnPreviewRow): string {
  const kind = String(row.selectedBandKind ?? '').trim();
  const byKind = row.unitList?.find((item) => String(item.value ?? '') === kind);
  if (byKind) {
    return String(byKind.label ?? byKind.value_dictText ?? '').trim();
  }
  return String(row.unitCodeDictText ?? row.unitCode ?? '').trim();
}

/** 打印或预览科室退库单 */
async function handlePrint(payload?: { preview?: boolean }) {
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

  const sourceName = resolvePrintLocationName(props.deptOptions, sourceLocationId.value);
  const warehouseName = resolvePrintLocationName(props.warehouseOptions, purposeLocationId.value);
  const documentTitle = buildInvDocumentTitle(sourceName, '科室退库单');
  const applyTimeText = String(headApplyTime.value ?? props.applyTime ?? '')
    .trim()
    .replace('T', ' ');

  // 按当前网格展示值组装退货明细
  const lineRows = productRows.map((row) => ({
    productBusNo: resolveInvPrintProductBusNo(row as unknown as Record<string, unknown>),
    productName: String(row.productName ?? '').trim(),
    qty: row.itemQuantity,
    unitLabel: resolveDeptReturnPrintUnitLabel(row),
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
    bizRef: { busNo, docKind: 'dept-return' },
    jobName: documentTitle,
    preview: payload?.preview === true,
  });
}
</script>

<template>
  <div class="dept-return-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
    <InvMainShell>
      <template v-if="!showWelcomeEmpty" #actionBar>
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
        v-if="showWelcomeEmpty"
        class="flex min-h-0 flex-1 flex-col items-center justify-center px-4 py-12"
      >
        <el-empty :image-size="96" :description="INV_DOC_UPSTREAM_WELCOME.deptReturn">
          <div class="flex flex-wrap items-center justify-center gap-2">
            <InvPrimaryBtn label="新建空白退库单" @click="emit('createBlank')" />
            <ElButton @click="openUpstreamPicker">从入库单新建</ElButton>
          </div>
        </el-empty>
      </div>

      <div
        v-else
        class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-2 pt-2"
        @mousedown="deptReturnGridEdit.onSurfaceMouseDown"
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
            class="compact-head-form mt-1"
          >
            <div
              class="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
            >
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem label="科室" prop="sourceLocationId">
                <ElSelect
                  v-model="sourceLocationId"
                  placeholder="退库源科室"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="d in deptOptions"
                    :key="String(d.locationId)"
                    :label="d.displayName ?? String(d.locationId)"
                    :value="String(d.locationId ?? '')"
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
                    :value="String(w.locationId ?? '')"
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
                  placeholder="药品/耗材"
                  class="w-full"
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="t in itemTableTypeOptions"
                    :key="t.value"
                    :label="t.label"
                    :value="t.value"
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
              <ElFormItem label="原入库单" class="sm:col-span-2 lg:col-span-2">
                <div class="flex min-w-0 items-center gap-2">
                  <ElInput
                    :model-value="originalBusNo ?? ''"
                    class="min-w-0 flex-1"
                    readonly
                    disabled
                    placeholder="可选：从入库单带入"
                  />
                  <ElButton
                    class="shrink-0"
                    :disabled="!canEditOriginalBusNo"
                    @click="openLinkSourceDialog"
                  >
                    {{ linkSourceButtonLabel }}
                  </ElButton>
                </div>
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <div
          ref="deptReturnGridKeyboardRef"
          class="dept-return-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col"
          @keydown.capture="deptReturnGridEdit.onGridKeydown"
        >
          <Grid class="min-h-0 flex-1">
            <template #toolbar-actions>
              <InvDocGridToolbar
                :show-add="true"
                :disabled="!linesGridEditable"
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
      title="选择已同意的科室入库单"
      :table-data="linkSourceRecords"
      :loading="linkSourceLoading"
      :total="linkSourceTotal"
      :query-params="linkSourceQueryParams"
      :form-items="linkSourceFormItems"
      :table-columns="linkSourceTableColumns"
      :location-options="deptOptions"
      keyword-placeholder="入库单号/关键字"
      @opened="fetchLinkableSourceDocuments"
      @query="handleLinkSourceQuery"
      @reset="handleLinkSourceReset"
      @pagination="handleLinkSourcePagination"
      @select="(row) => applyLinkableSource(row as DeptReturnListRowDto)"
    >
      <template #applyTime="{ row }">
        {{
          (() => {
            const r = row as DeptReturnListRowDto & { approvalTime?: string };
            const t = r.applyTime ?? r.approvalTime;
            return t ? formatDateTime(t as string) : '';
          })()
        }}
      </template>
      <template #rowActions="{ row }">
        <ElButton
          link
          type="primary"
          @click.stop="applyLinkableSource(row as DeptReturnListRowDto)"
        >
          选择
        </ElButton>
      </template>
    </InvDocLinkPickerDialog>
  </div>
  <InvDocTraceScanBridge
    ref="traceScanBridgeRef"
    scene="inventoryOut"
    :default-location-id="sourceLocationId"
  />
</template>

<style scoped lang="scss">
.compact-head-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.compact-head-form :deep(.el-form-item__label) {
  padding-right: 8px;
}

.dept-return-main-pane__grid-wrap :deep(.vxe-grid) {
  min-height: 200px;
}
</style>
