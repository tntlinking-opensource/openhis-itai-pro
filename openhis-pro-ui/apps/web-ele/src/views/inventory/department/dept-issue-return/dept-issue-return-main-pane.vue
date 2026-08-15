<!--
  科室退领单右侧主区：关联原领用单 originalBusNo + 明细。
-->
<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';
import type { VxeGridPropTypes } from '#/adapter/vxe-table';
import type { FormInstance } from 'element-plus';
import type {
  DeptIssueReturnDetailLineDto,
  DeptIssueReturnLineDto,
  LinkableIssueListRowDto,
  LinkableIssueReturnLineDto,
} from '#/api/inventory/department/dept-issue-return';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, ref, watch, onBeforeUnmount, onDeactivated } from 'vue';

import { Trash2 } from '@vben/icons';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocDetailGridShell from '@inventory/components/inv-doc-detail-grid-shell.vue';
import InvDocGridToolbar from '@inventory/components/inv-doc-grid-toolbar.vue';
import InvDocTraceScanBridge from '@inventory/components/inv-doc-trace-scan-bridge.vue';
import InvDocLinkPickerDialog from '@inventory/components/inv-doc-link-picker-dialog.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
import InvPrimaryBtn from '@inventory/components/inv-primary-btn.vue';
import {
  ElAlert,
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import { useInvDocDetailGrid } from '#/composables/use-inv-doc-detail-grid';
import { useInvDocGridRowOps } from '#/composables/use-inv-doc-grid-row-ops';
import { useInvDocParentSyncSession } from '#/composables/use-inv-doc-parent-sync-session';
import {
  clearInvDocDraftCacheAfterSave,
  INV_DOC_DRAFT_SCENE,
} from '#/utils/inventory/inv-doc-draft-cache';
import {
  agreeDeptIssueReturnApi,
  createDeptIssueReturnApi,
  getDeptIssueReturnDetailApi,
  getLinkableIssueDetailApi,
  pageLinkableIssueDocumentsApi,
  rejectDeptIssueReturnApi,
  removeDeptIssueReturnApi,
  submitDeptIssueReturnApprovalApi,
  updateDeptIssueReturnApi,
  withdrawDeptIssueReturnApprovalApi,
} from '#/api/inventory/department/dept-issue-return';
import SectionTitle from '#/components/layouts/section-title.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  INV_DOC_UPSTREAM_HINT,
  INV_DOC_UPSTREAM_WELCOME,
  hasNonEmptyBusNo,
  warnInventoryDocRestriction,
} from '#/utils/inventory/inventory-doc-restrictions';
import { withInvDocLinkPickerEnrichedColumns } from '#/utils/inventory/inv-doc-link-picker-display';
import { resolveLoginPractitionerIdForRequest } from '#/utils/format/preserve-api-id';
import { formatInventoryLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';
import {
  applyHydratedInventoryDetailQtyToRow,
  applyInventoryRowQtyUnitList,
  createInventoryQtyUnitColumn,
  handleWarehouseNonInboundUnitBandChanged,
  hydrateInventoryDetailLineQtyForGrid,
  isPresentItemId,
} from '#/utils/inventory/inventory-doc-grid-common';
import {
  canConvertDisplayQtyToSplitForBands,
  persistDisplayQtyAsSplitForBands,
  resolveInboundLinePersistUnitCodeForBands,
  type InboundProductUnitBand,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  INV_ALL_DEPT_PHARMACIES_LABEL,
  INV_ALL_LOCATIONS_FILTER_ID,
  isSpecificInventoryLocationId,
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
  isStockOutDocumentEditableStatus,
  isStockOutDocumentRemovableStatus,
  buildStockOutDocActionFlags,
  stockOutStatusLabel,
  normalizeStockOutStatusSort,
} from '#/utils/inventory/stock-out-doc-actions';
import {
  buildInvDocumentTitle,
  printInvDocument,
  resolveInvPrintProductBusNo,
} from '#/utils/print/print-inv-document';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

defineOptions({ name: 'InventoryDepartmentDeptIssueReturnMainPane' });

interface ReturnPreviewRow {
  id: string;
  itemTable?: string;
  itemId?: number | string;
  productName: string;
  spec: string;
  manufacturer: string;
  itemQuantity?: number;
  unitCode: string;
  unitCodeDictText?: string;
  unitBands?: InboundProductUnitBand[];
  selectedBandKind?: string;
  unitList?: { label?: string; value: string; value_dictText?: string }[];
  __prevBandKind?: string;
  price: number;
  totalPrice: string;
  lotNumber: string;
  expirationDate: string;
  traceNo: string;
  remark: string;
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
  deptPharmacyOptions?: ApiOptionItem[];
  practitionerOptions?: ApiOptionItem[];
}

const props = withDefaults(defineProps<Props>(), {
  queueCardRow: null,
  busNo: '',
  isDraftOrder: false,
  clientDraftId: '',
  resetKey: 0,
  showWelcomeEmpty: false,
  deptPharmacyOptions: () => [],
  practitionerOptions: () => [],
});

const emit = defineEmits<{
  createFromUpstream: [row: LinkableIssueListRowDto];
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

const headerBusNo = ref<string | undefined>(undefined);
const originalBusNo = ref<string | undefined>(undefined);
const purposeLocationId = ref<number | string | undefined>(undefined);
const practitionerId = ref<number | string | undefined>(undefined);
const headFormRef = ref<FormInstance>();

const headFormModel = computed(() => ({
  purposeLocationId: purposeLocationId.value ?? '',
}));

const headFormRules = computed(() =>
  buildInventoryLocationHeadFormRules({
    purposeLocationId: '科室药房',
  }),
);

const consumingOrganizationName = ref('');
const detailStatusEnum = ref<number | string | undefined>(undefined);
const saving = ref(false);

const linkIssueVisible = ref(false);
const linkIssueLoading = ref(false);
const linkIssueQueryParams = ref<{
  keyword: string;
  pageNo: number;
  pageSize: number;
  purposeLocationId: number | string;
}>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  purposeLocationId: INV_ALL_LOCATIONS_FILTER_ID,
});
const linkIssueTotal = ref(0);
const linkIssueRecords = ref<LinkableIssueListRowDto[]>([]);

/** 关联领用单弹窗：科室药房下拉（含「全部科室药房」） */
const linkIssuePharmacyOptions = computed(() => {
  const rows = (props.deptPharmacyOptions ?? [])
    .filter((row) => isSpecificInventoryLocationId(row.value))
    .map((row) => ({
      label: String(row.label ?? row.value ?? ''),
      value: row.value as number | string,
    }));
  return [{ label: INV_ALL_DEPT_PHARMACIES_LABEL, value: INV_ALL_LOCATIONS_FILTER_ID }, ...rows];
});

/** 弹窗筛库：具体药房原样传；否则传 '0'（全部）。 */
function resolveLinkIssueLocationIdForApi(): number | string {
  const id = resolveSpecificInventoryLocationId(linkIssueQueryParams.value.purposeLocationId);
  if (id) return id;
  return INV_ALL_LOCATIONS_FILTER_ID;
}

/** 归一弹窗药房筛选（空/数组 → 全部）。 */
function normalizeLinkIssuePharmacyId(raw: unknown): number | string {
  if (Array.isArray(raw)) {
    const first = raw[0];
    if (first === undefined || first === null) return INV_ALL_LOCATIONS_FILTER_ID;
    return first as number | string;
  }
  if (raw === undefined || raw === null || raw === '') return INV_ALL_LOCATIONS_FILTER_ID;
  return raw as number | string;
}

/** 弹窗药房下拉变更后立即重查。 */
function handleLinkIssuePharmacyChange(value: unknown) {
  linkIssueQueryParams.value.purposeLocationId = normalizeLinkIssuePharmacyId(value);
  linkIssueQueryParams.value.pageNo = 1;
  void fetchLinkableIssues();
}

const linkIssueFormItems = computed<FormItemConfig[]>(() => [
  {
    label: '科室药房',
    prop: 'purposeLocationId',
    type: 'select',
    placeholder: INV_ALL_DEPT_PHARMACIES_LABEL,
    filterable: true,
    clearable: false,
    multiple: false,
    options: linkIssuePharmacyOptions.value,
    onChange: handleLinkIssuePharmacyChange,
  },
  {
    label: '关键字',
    prop: 'keyword',
    type: 'input',
    placeholder: '领用单号/关键字',
  },
]);

const linkIssueTableColumns = computed<TableColumn[]>(() =>
  withInvDocLinkPickerEnrichedColumns(
    [
      { type: 'index', label: '序号', width: 58, align: 'center' },
      { prop: 'busNo', label: '领用单号', minWidth: 160, showOverflowTooltip: true },
      {
        prop: 'consumingOrganizationName',
        label: '领用科室',
        minWidth: 140,
        showOverflowTooltip: true,
      },
      { prop: 'approvalTime', label: '审批时间', minWidth: 160, slot: 'approvalTime' },
      { label: '操作', width: 88, align: 'center', fixed: 'right', slot: 'rowActions' },
    ],
    { warehouseLabel: '科室药房' },
  ),
);

const linkIssueLocationOptions = computed(() =>
  (props.deptPharmacyOptions ?? []).map((item) => ({
    value: item.value,
    label: item.label,
    locationId: item.value,
    displayName: item.label,
  })),
);

const isNewDraft = computed(() => !headerBusNo.value);
const documentEditable = computed(
  () => isNewDraft.value || isStockOutDocumentEditableStatus(detailStatusEnum.value),
);
const hasLinkedUpstream = computed(() => hasNonEmptyBusNo(originalBusNo.value));
const linesGridEditable = computed(() => documentEditable.value && hasLinkedUpstream.value);
/** 单头「从领用单选择」：非草稿可先选后建；草稿可编辑时可更换关联 */
const canEditOriginalBusNo = computed(() => !isNewDraft.value || documentEditable.value);
const linkIssueButtonLabel = computed(() =>
  hasLinkedUpstream.value && props.isDraftOrder ? '更换领用单' : '从领用单选择',
);

const headerStatusText = computed(() => {
  if (isNewDraft.value) return '编辑中';
  return stockOutStatusLabel(detailStatusEnum.value);
});

const docActionFlags = computed(() => {
  const hasPersistedBusNo = Boolean(headerBusNo.value);
  const base = buildStockOutDocActionFlags({
    hasPersistedBusNo,
    isDraft: isNewDraft.value,
    status: normalizeStockOutStatusSort(detailStatusEnum.value),
  });
  const upstreamRequired = isNewDraft.value && !hasLinkedUpstream.value;
  return {
    ...base,
    saveDisabled: base.saveDisabled || saving.value || upstreamRequired,
    saveAndSubmitDisabled: base.saveAndSubmitDisabled || saving.value || upstreamRequired,
    printDisabled: saving.value || !hasPersistedBusNo,
  };
});

/** 有效期展示：取日期部分。 */
function formatReturnExpirationDisplay(raw: unknown): string {
  const s = String(raw ?? '').trim();
  if (s === '') return '';
  if (s.includes(' ')) return s.split(' ')[0] ?? s;
  if (s.includes('T')) return s.split('T')[0] ?? s;
  return s;
}

/** 表格有效期 → 请求体 endTime（空格分隔，对齐后端 Flexible 反序列化）。 */
function normalizeReturnLineDateTimeForApi(dateStr: string): string | undefined {
  const raw = String(dateStr ?? '').trim();
  if (!raw) return undefined;
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    return `${raw} 00:00:00`;
  }
  if (raw.includes('T')) {
    return raw.replace('T', ' ').slice(0, 19);
  }
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/.test(raw)) {
    return raw.slice(0, 19);
  }
  return raw;
}

/** 编辑态：按数量 × 单价刷新金额。 */
function recalcReturnLineTotal(row: ReturnPreviewRow) {
  const qty = Number(row.itemQuantity);
  const unit = Number(row.price);
  row.totalPrice =
    Number.isFinite(qty) && Number.isFinite(unit) ? formatInventoryLineTotal(qty, unit) : '';
}

function createEmptyRow(): ReturnPreviewRow {
  return {
    id: `new-${Date.now()}`,
    productName: '',
    spec: '',
    manufacturer: '',
    itemQuantity: undefined,
    unitCode: '',
    selectedBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: '单位' }],
    price: 0,
    totalPrice: '',
    lotNumber: '',
    expirationDate: '',
    traceNo: '',
    remark: '',
  };
}

function handleDeptIssueReturnUnitBandChanged(row: ReturnPreviewRow): void {
  handleWarehouseNonInboundUnitBandChanged(row, {
    getQty: () => Number(row.itemQuantity ?? 0),
    setQty: (qty) => {
      row.itemQuantity = qty;
    },
    onAfterChanged: () => recalcReturnLineTotal(row),
  });
}

/** 可退领用行 → 网格（规格/厂家/金额/效期/备注带入）。 */
function mapLinkableLineToGrid(ln: LinkableIssueReturnLineDto, idx: number): ReturnPreviewRow {
  const name = String(ln.productName ?? '').trim();
  const unitCode = String(ln.unitCode ?? '');
  const unitLabel = String(ln.unitCodeDictText ?? '').trim() || unitCode;
  const totalRaw = ln.totalPrice;
  const row: ReturnPreviewRow = {
    id: `ln-${idx}`,
    itemTable: ln.itemTable,
    itemId: ln.itemId,
    productName: name,
    spec: String(ln.specification ?? '').trim(),
    manufacturer: String(ln.manufacturer ?? '').trim(),
    itemQuantity: ln.returnableQuantity === undefined ? undefined : Number(ln.returnableQuantity),
    unitCode,
    unitCodeDictText: unitLabel,
    price: ln.price === undefined || ln.price === null ? 0 : Number(ln.price),
    totalPrice:
      totalRaw === undefined || totalRaw === null || totalRaw === ''
        ? ''
        : formatMoneyDisplay(totalRaw),
    lotNumber: String(ln.lotNumber ?? ''),
    expirationDate: formatReturnExpirationDisplay(ln.endTime),
    traceNo: String(ln.traceNo ?? ''),
    remark: String(ln.remake ?? ''),
  };
  const catalog = ln.productCatalog;
  const hydrated = hydrateInventoryDetailLineQtyForGrid({
    itemQuantity: row.itemQuantity,
    unitCode,
    unitCodeDictText: unitLabel,
    productCatalog: catalog,
    unitBands: ln.unitBands,
    unitBandsJson: ln.unitBandsJson,
  });
  applyHydratedInventoryDetailQtyToRow(row, hydrated);
  applyInventoryRowQtyUnitList(row);
  // 带入后按可退数量重算金额，避免仅有单价时空白
  if (!String(row.totalPrice ?? '').trim()) {
    recalcReturnLineTotal(row);
  }
  return row;
}

/** 详情行 → 网格回显。 */
function mapDetailLineToGrid(ln: DeptIssueReturnDetailLineDto, idx: number): ReturnPreviewRow {
  const name = String(ln.productName ?? ln.itemName ?? '').trim();
  const totalRaw = ln.totalPrice;
  const row: ReturnPreviewRow = {
    id: `ln-${ln.id ?? idx}`,
    itemTable: ln.itemTable,
    itemId: ln.itemId,
    productName: name,
    spec: String(ln.spec ?? ln.specification ?? '').trim(),
    manufacturer: String(ln.manufacturer ?? '').trim(),
    itemQuantity: ln.itemQuantity === undefined ? undefined : Number(ln.itemQuantity),
    unitCode: String(ln.unitCode ?? ''),
    unitCodeDictText: String(ln.unitCodeDictText ?? ln.unitName ?? ln.unitCode ?? ''),
    price: ln.price === undefined || ln.price === null ? 0 : Number(ln.price),
    totalPrice:
      totalRaw === undefined || totalRaw === null || totalRaw === ''
        ? ''
        : formatMoneyDisplay(totalRaw),
    lotNumber: String(ln.lotNumber ?? ''),
    expirationDate: formatReturnExpirationDisplay(ln.endTime),
    traceNo: String(ln.traceNo ?? ''),
    remark: String(ln.remake ?? ''),
  };
  const catalog = ln.productCatalog;
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

function isReturnRowComplete(row: ReturnPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.itemQuantity);
  return Number.isFinite(qty) && qty > 0 && !!String(row.unitCode ?? '').trim();
}

const columns: VxeGridPropTypes.Column[] = [
  { type: 'checkbox', width: 48, fixed: 'left', align: 'center' },
  { type: 'seq', title: '序号', width: 52, fixed: 'left', align: 'center' },
  { field: 'productName', title: '项目名称', minWidth: 200, showOverflow: true },
  { field: 'spec', title: '规格', width: 120, showOverflow: true },
  { field: 'manufacturer', title: '厂家', minWidth: 120, showOverflow: true },
  createInventoryQtyUnitColumn({
    title: '退领数量',
    onUnitChange: (row) => handleDeptIssueReturnUnitBandChanged(row as ReturnPreviewRow),
  }),
  {
    field: 'totalPrice',
    title: '金额',
    width: 112,
    align: 'right',
    headerAlign: 'center',
    cellRender: {
      name: 'InventoryTotalAmountCell',
      props: { amountField: 'totalPrice' },
    },
  },
  { field: 'lotNumber', title: '批号', width: 120, showOverflow: true },
  { field: 'expirationDate', title: '有效期', width: 120, showOverflow: true },
  {
    field: 'traceNo',
    title: '追溯码',
    minWidth: 120,
    editRender: { name: 'InventoryTraceNoCell', props: { maxlength: 64, clearable: true } },
  },
  {
    field: 'remark',
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

const {
  Grid,
  gridApi,
  bindGridWrap,
  gridEdit: returnGridEdit,
  collectGridRows,
  patchGridEditConfig,
} = useInvDocDetailGrid<ReturnPreviewRow>({
  columns,
  documentEditable: () => linesGridEditable.value,
  beforeEditMethod: () => linesGridEditable.value,
  onEditClosed: (row) => recalcReturnLineTotal(row),
  config: {
    rowKey: (row) => row.id,
    productField: 'productName',
    editFieldOrder: ['_qtyUnit', 'traceNo', 'remark'],
    editableFields: new Set(['_qtyUnit', 'traceNo', 'remark']),
    verticalNavFields: new Set(['_qtyUnit', 'traceNo', 'remark']),
    firstFillFieldAfterPick: '_qtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isReturnRowComplete,
  },
});

const { deleteItem } = useInvDocGridRowOps({
  documentEditable: () => linesGridEditable.value,
  getGridApi: () => gridApi ?? null,
  gridEdit: returnGridEdit,
  createEmptyRow,
  hasProduct: (row) => isPresentItemId(row.itemId),
});

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
  purposeLocationId.value = undefined;
  consumingOrganizationName.value = '';
  practitionerId.value = resolveDefaultPractitionerIdForSelect(
    effectivePractitionerOptions.value,
    userStore.userInfo as null | Record<string, unknown>,
  );
  clearInventoryHeadFormValidation(headFormRef.value);
  returnGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchGridEditConfig();
  });
}

async function loadDetail(busNo: string, loadGen?: number) {
  returnGridEdit.resetFormMode();
  returnGridEdit.clearGridEdit();
  try {
    const res = await getDeptIssueReturnDetailApi({ busNo });
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到退领单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    originalBusNo.value = data.originalBusNo ?? undefined;
    detailStatusEnum.value = normalizeStockOutStatusSort(data.statusEnum);
    purposeLocationId.value = data.purposeLocationId ?? undefined;
    consumingOrganizationName.value = String(data.consumingOrganizationName ?? '').trim();
    pinnedPractitionerOptions.value = [];
    const detailPractitionerId = data.practitionerId ?? data.applicantId;
    const detailPractitionerLabel = data.applicantName ? String(data.applicantName) : undefined;
    pinPractitionerOption(detailPractitionerId, detailPractitionerLabel);
    practitionerId.value = coercePractitionerIdForSelect(
      detailPractitionerId,
      effectivePractitionerOptions.value,
    );
    const gridRows = (data.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData(gridRows);
      patchGridEditConfig();
    });
  } catch {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchGridEditConfig();
    });
  }
}

function buildLinesPayload(rows: ReturnPreviewRow[]): DeptIssueReturnLineDto[] {
  return rows.map((r) => {
    recalcReturnLineTotal(r);
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
    const unit = Number(r.price);
    return {
      itemTable: r.itemTable,
      itemId: r.itemId,
      itemQuantity: persisted.itemQuantity,
      unitCode: unitCode || undefined,
      lotNumber: String(r.lotNumber ?? '').trim() || undefined,
      traceNo: String(r.traceNo ?? '').trim(),
      price: Number.isFinite(unit) ? unit : undefined,
      endTime: normalizeReturnLineDateTimeForApi(String(r.expirationDate ?? '')),
      remake: String(r.remark ?? '').trim() || undefined,
    };
  });
}

async function persistReturn(): Promise<string | undefined> {
  if (!documentEditable.value) return undefined;
  const ob = originalBusNo.value?.trim();
  if (!ob) {
    warnInventoryDocRestriction(INV_DOC_UPSTREAM_HINT.deptIssueReturn);
    return undefined;
  }
  if (!(await validateInventoryHeadFormFields(headFormRef.value, 'purposeLocationId'))) {
    return undefined;
  }
  const handlerId = practitionerId.value;
  if (!handlerId) {
    ElMessage.warning('请选择经手人');
    return undefined;
  }
  const rows = collectGridRows();
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一行退领明细');
    return undefined;
  }
  for (const [i, r] of rows.entries()) {
    if (!isReturnRowComplete(r)) {
      ElMessage.warning(`第 ${i + 1} 行请补全项目、数量与单位`);
      return undefined;
    }
    const qty = Number(r.itemQuantity);
    if (!canConvertDisplayQtyToSplitForBands(r.unitBands ?? [], qty, r.selectedBandKind)) {
      ElMessage.warning(`第 ${i + 1} 行数量与单位无法换算，请检查产品单位维护`);
      return undefined;
    }
  }
  saving.value = true;
  try {
    const lines = buildLinesPayload(rows);
    if (headerBusNo.value) {
      await updateDeptIssueReturnApi({
        busNo: headerBusNo.value,
        purposeLocationId: purposeLocationId.value as number | string,
        practitionerId: handlerId,
        lines,
      });
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      clearDraftPersistAfterSave(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }
    const res = await createDeptIssueReturnApi({
      originalBusNo: ob,
      purposeLocationId: purposeLocationId.value as number | string,
      practitionerId: handlerId,
      lines,
    });
    const no = res.data?.busNo;
    ElMessage.success('新建成功');
    if (no) await loadDetail(no);
    clearDraftPersistAfterSave(no);
    emit('documentSaved', { busNo: no });
    return no;
  } catch {
    return undefined;
  } finally {
    saving.value = false;
  }
}

/** 关联领用单弹窗：查询。 */
async function handleLinkIssueQuery(params: Record<string, unknown>) {
  Object.assign(linkIssueQueryParams.value, params);
  linkIssueQueryParams.value.purposeLocationId = normalizeLinkIssuePharmacyId(
    linkIssueQueryParams.value.purposeLocationId,
  );
  linkIssueQueryParams.value.pageNo = 1;
  await fetchLinkableIssues();
}

/** 关联领用单弹窗：重置为全部科室药房。 */
function handleLinkIssueReset() {
  linkIssueQueryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    purposeLocationId: INV_ALL_LOCATIONS_FILTER_ID,
  };
  void fetchLinkableIssues();
}

/** 关联领用单弹窗：分页。 */
function handleLinkIssuePagination(pagination: { limit: number; page: number }) {
  linkIssueQueryParams.value.pageNo = pagination.page;
  linkIssueQueryParams.value.pageSize = pagination.limit;
  void fetchLinkableIssues();
}

/** 打开可关联领用单弹窗（默认全部科室药房；不校验单头库房）。 */
function openUpstreamPicker() {
  linkIssueQueryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    purposeLocationId: INV_ALL_LOCATIONS_FILTER_ID,
  };
  linkIssueRecords.value = [];
  linkIssueTotal.value = 0;
  linkIssueVisible.value = true;
}

/** 单头「从领用单选择/更换」：非草稿先选后建；草稿可编辑时可更换关联。 */
function openLinkIssueDialog() {
  if (!canEditOriginalBusNo.value) {
    ElMessage.warning('当前单据不可关联领用单');
    return;
  }
  openUpstreamPicker();
}

/** 关联领用单弹窗：分页查询可退领用单。 */
async function fetchLinkableIssues() {
  linkIssueQueryParams.value.purposeLocationId = normalizeLinkIssuePharmacyId(
    linkIssueQueryParams.value.purposeLocationId,
  );
  linkIssueLoading.value = true;
  try {
    const res = await pageLinkableIssueDocumentsApi({
      purposeLocationId: resolveLinkIssueLocationIdForApi(),
      keyword: String(linkIssueQueryParams.value.keyword ?? '').trim() || undefined,
      pageNo: linkIssueQueryParams.value.pageNo,
      pageSize: linkIssueQueryParams.value.pageSize,
    });
    const { content, totalCount } = parsePagedRowsFromApiData<LinkableIssueListRowDto>(res.data);
    linkIssueRecords.value = content;
    linkIssueTotal.value = totalCount;
  } catch {
    linkIssueRecords.value = [];
    linkIssueTotal.value = 0;
  } finally {
    linkIssueLoading.value = false;
  }
}

/** 选用领用单：非草稿时交给父级建草稿，草稿内直接带入。 */
async function applyLinkableIssue(row: LinkableIssueListRowDto) {
  if (!props.isDraftOrder) {
    emit('createFromUpstream', row);
    return;
  }
  await applyLinkableIssueToDraft(row);
}

/** 将可退领用单明细写入当前草稿（单头药房由源单回填）。 */
async function applyLinkableIssueToDraft(row: LinkableIssueListRowDto) {
  const issueNo = row.busNo?.trim();
  if (!issueNo) return;
  const pid =
    resolveSpecificInventoryLocationId(row.purposeLocationId) ??
    resolveSpecificInventoryLocationId(linkIssueQueryParams.value.purposeLocationId);
  if (!pid) {
    ElMessage.warning('领用单缺少科室药房，无法带入');
    return;
  }
  try {
    const res = await getLinkableIssueDetailApi({
      originalBusNo: issueNo,
      purposeLocationId: pid,
    });
    const data = res.data;
    if (!data?.originalBusNo) {
      ElMessage.warning('未获取到可退领明细');
      return;
    }
    originalBusNo.value = issueNo;
    consumingOrganizationName.value = String(data.consumingOrganizationName ?? '').trim();
    if (data.purposeLocationId) {
      purposeLocationId.value = data.purposeLocationId;
    } else {
      purposeLocationId.value = pid;
    }
    const gridRows = (data.lines ?? []).map((ln, i) => mapLinkableLineToGrid(ln, i));
    returnGridEdit.resetFormMode();
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchGridEditConfig();
    });
    linkIssueVisible.value = false;
    ElMessage.success('已带入可退领明细，请核对数量后保存');
  } catch {
    /* handled */
  }
}

defineExpose({
  applyLinkableIssueToDraft,
  openUpstreamPicker,
});

/** 落库成功后清 session 草稿缓存 */
function clearDraftPersistAfterSave(busNo?: string) {
  clearInvDocDraftCacheAfterSave({
    scene: INV_DOC_DRAFT_SCENE.deptIssueReturn,
    clientDraftId: props.clientDraftId,
    busNo,
  });
  parentSync.clearDraftSnap();
}

async function handleRemoveDoc() {
  if (isNewDraft.value) {
    try {
      await ElMessageBox.confirm('确认放弃该草稿？', '提示', { type: 'warning' });
    } catch {
      return;
    }
    clearInvDocDraftCacheAfterSave({
      scene: INV_DOC_DRAFT_SCENE.deptIssueReturn,
      clientDraftId: props.clientDraftId,
    });
    parentSync.discardDraftSession();
    resetDetailForCreate();
    emit('documentRemoved');
    return;
  }
  const no = headerBusNo.value;
  if (!no || !isStockOutDocumentRemovableStatus(detailStatusEnum.value)) return;
  try {
    await ElMessageBox.confirm('确认删除整张退领单？', '提示', { type: 'warning' });
    await removeDeptIssueReturnApi({ busNo: no });
    clearInvDocDraftCacheAfterSave({
      scene: INV_DOC_DRAFT_SCENE.deptIssueReturn,
      busNo: no,
    });
    parentSync.clearDraftSnap();
    ElMessage.success('已删除');
    resetDetailForCreate();
    emit('documentRemoved');
  } catch {
    /* handled */
  }
}

interface DeptIssueReturnDraftSnap {
  originalBusNo?: string;
  purposeLocationId?: number | string;
  practitionerId?: number | string;
  consumingOrganizationName: string;
  rows: ReturnPreviewRow[];
}

const parentSync = useInvDocParentSyncSession<DeptIssueReturnDraftSnap>({
  scene: INV_DOC_DRAFT_SCENE.deptIssueReturn,
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
function captureDeptIssueReturnDraftSnap(): DeptIssueReturnDraftSnap {
  return {
    originalBusNo: originalBusNo.value,
    purposeLocationId: purposeLocationId.value,
    practitionerId: practitionerId.value,
    consumingOrganizationName: consumingOrganizationName.value,
    rows: collectGridRows().map((r) => ({ ...r })),
  };
}

/** 侧栏切单：缓存/恢复本地草稿，避免已落库明细污染新建单 */
async function syncFromParentContext() {
  await parentSync.syncFromParentContext(
    { isDraftOrder: Boolean(props.isDraftOrder), parentBusNo: props.busNo },
    {
      getLocalPersistedBusNo: () => headerBusNo.value,
      captureDraft: captureDeptIssueReturnDraftSnap,
      restoreDraft: async (snap) => {
        headerBusNo.value = undefined;
        detailStatusEnum.value = undefined;
        originalBusNo.value = snap.originalBusNo;
        purposeLocationId.value = snap.purposeLocationId;
        practitionerId.value = snap.practitionerId;
        consumingOrganizationName.value = snap.consumingOrganizationName;
        returnGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
        patchGridEditConfig();
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
function flushDeptIssueReturnDraftPersist(): void {
  parentSync.flushPersist({
    getLocalPersistedBusNo: () => headerBusNo.value,
    captureDraft: captureDeptIssueReturnDraftSnap,
  });
}

onBeforeUnmount(() => {
  flushDeptIssueReturnDraftPersist();
});

onDeactivated(() => {
  flushDeptIssueReturnDraftPersist();
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

/** 明细行当前计量单位展示文案 */
function resolveIssueReturnPrintUnitLabel(row: ReturnPreviewRow): string {
  const kind = String(row.selectedBandKind ?? '').trim();
  const byKind = row.unitList?.find((item) => String(item.value ?? '') === kind);
  if (byKind) {
    return String(byKind.label ?? byKind.value_dictText ?? '').trim();
  }
  return String(row.unitCodeDictText ?? row.unitCode ?? '').trim();
}

/** 打印发放退回单 */
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

  const pharmacyName = resolvePrintOptionLabel(props.deptPharmacyOptions, purposeLocationId.value);
  const documentTitle = buildInvDocumentTitle(pharmacyName, '发放退回单');

  // 按当前网格展示值组装退货明细
  const lineRows = productRows.map((row) => ({
    productBusNo: resolveInvPrintProductBusNo(row as unknown as Record<string, unknown>),
    productName: String(row.productName ?? '').trim(),
    qty: row.itemQuantity,
    unitLabel: resolveIssueReturnPrintUnitLabel(row),
    lotNumber: String(row.lotNumber ?? '').trim(),
    reasonText: '',
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
      applyTimeText: '',
      itemTypeText: '',
      sourceLocationText: String(consumingOrganizationName.value ?? '').trim(),
      supplierText: '',
      linkDocText: String(originalBusNo.value ?? '').trim(),
      destLocationText: pharmacyName,
      invoiceNoText: '',
      lineRows,
      preparedByName,
      approvedByName,
    },
    bizRef: { busNo, docKind: 'dept-issue-return' },
    jobName: documentTitle,
  });
}
</script>

<template>
  <div class="dept-issue-return-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
    <InvMainShell>
      <template v-if="!showWelcomeEmpty" #actionBar>
        <div class="flex w-full flex-wrap items-center justify-end gap-2">
          <InvDocActions
            v-bind="docActionFlags"
            @print="handlePrint"
            @approve="
              async () => {
                const no = headerBusNo;
                if (!no) return;
                await agreeDeptIssueReturnApi({
                  busNo: no,
                  approverId: resolveLoginPractitionerIdForRequest(
                    userStore.userInfo as null | Record<string, unknown>,
                  ),
                  approvalClock: formatDateTime(new Date()).replace(' ', 'T'),
                });
                ElMessage.success('审批同意');
                await loadDetail(no);
                emit('documentSaved', { busNo: no });
              }
            "
            @reject="
              async () => {
                const no = headerBusNo;
                if (!no) return;
                try {
                  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', {
                    inputPattern: /\S+/,
                    inputErrorMessage: '原因不能为空',
                  });
                  await rejectDeptIssueReturnApi({
                    busNo: no,
                    rejectReason: value,
                    approverId: resolveLoginPractitionerIdForRequest(
                      userStore.userInfo as null | Record<string, unknown>,
                    ),
                    approvalClock: formatDateTime(new Date()).replace(' ', 'T'),
                  });
                  ElMessage.success('已驳回');
                  await loadDetail(no);
                  emit('documentSaved', { busNo: no });
                } catch (e) {
                  if (e === 'cancel') return;
                }
              }
            "
            @remove-doc="handleRemoveDoc"
            @save="persistReturn"
            @save-and-submit="
              async () => {
                const no = await persistReturn();
                if (no) {
                  await submitDeptIssueReturnApprovalApi({ busNo: no });
                  ElMessage.success('已提交审批');
                  await loadDetail(no);
                  emit('documentSaved', { busNo: no });
                }
              }
            "
            @submit-approval="
              async () => {
                const no = headerBusNo;
                if (!no) return;
                await submitDeptIssueReturnApprovalApi({ busNo: no });
                ElMessage.success('已提交审批');
                await loadDetail(no);
                emit('documentSaved', { busNo: no });
              }
            "
            @withdraw="
              async () => {
                const no = headerBusNo;
                if (!no) return;
                await withdrawDeptIssueReturnApprovalApi({ busNo: no });
                ElMessage.success('已撤回');
                await loadDetail(no);
                emit('documentSaved', { busNo: no });
              }
            "
          />
        </div>
      </template>

      <div
        v-if="showWelcomeEmpty"
        class="flex min-h-0 flex-1 flex-col items-center justify-center px-4 py-12"
      >
        <el-empty :image-size="96" :description="INV_DOC_UPSTREAM_WELCOME.deptIssueReturn">
          <InvPrimaryBtn label="从领用单新建" @click="openUpstreamPicker" />
        </el-empty>
      </div>

      <div
        v-else
        class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-2 pt-2"
        @mousedown="returnGridEdit.onSurfaceMouseDown"
      >
        <div class="shrink-0">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <SectionTitle title="科室退领单" />
            <ElTag size="small" type="info" effect="plain">{{ headerStatusText || '—' }}</ElTag>
          </div>
          <ElForm
            ref="headFormRef"
            :model="headFormModel"
            :rules="headFormRules"
            label-width="96px"
            class="compact-head-form mt-1"
          >
            <div class="grid grid-cols-1 gap-x-2 sm:grid-cols-2 lg:grid-cols-4">
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem label="科室药房" prop="purposeLocationId">
                <ElSelect v-model="purposeLocationId" class="w-full" filterable clearable disabled>
                  <ElOption
                    v-for="p in deptPharmacyOptions"
                    :key="String(p.value)"
                    :label="p.label"
                    :value="p.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="领用科室">
                <ElInput
                  :model-value="consumingOrganizationName"
                  disabled
                  placeholder="自原领用单带出"
                />
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
              <ElFormItem label="关联领用">
                <div class="flex w-full min-w-0 items-center gap-2">
                  <ElInput
                    :model-value="originalBusNo ?? ''"
                    class="min-w-0 flex-1"
                    readonly
                    disabled
                    placeholder="请通过「从领用单选择」关联"
                  />
                  <ElButton
                    class="shrink-0"
                    :disabled="!canEditOriginalBusNo"
                    @click="openLinkIssueDialog"
                  >
                    {{ linkIssueButtonLabel }}
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
          :title="INV_DOC_UPSTREAM_HINT.deptIssueReturn"
        />

        <InvDocDetailGridShell
          :bind-grid-wrap="bindGridWrap"
          :on-grid-keydown="returnGridEdit.onGridKeydown"
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
        </InvDocDetailGridShell>
      </div>
    </InvMainShell>

    <InvDocLinkPickerDialog
      v-model="linkIssueVisible"
      title="选择可退领的领用单"
      :table-data="linkIssueRecords"
      :loading="linkIssueLoading"
      :total="linkIssueTotal"
      :query-params="linkIssueQueryParams"
      :form-items="linkIssueFormItems"
      :table-columns="linkIssueTableColumns"
      :location-options="linkIssueLocationOptions"
      keyword-placeholder="领用单号/关键字"
      @opened="fetchLinkableIssues"
      @query="handleLinkIssueQuery"
      @reset="handleLinkIssueReset"
      @pagination="handleLinkIssuePagination"
      @select="(row) => applyLinkableIssue(row as LinkableIssueListRowDto)"
    >
      <template #approvalTime="{ row }">
        {{
          (row as LinkableIssueListRowDto).approvalTime
            ? formatDateTime((row as LinkableIssueListRowDto).approvalTime as string)
            : ''
        }}
      </template>
      <template #rowActions="{ row }">
        <ElButton
          link
          type="primary"
          @click.stop="applyLinkableIssue(row as LinkableIssueListRowDto)"
        >
          选用
        </ElButton>
      </template>
    </InvDocLinkPickerDialog>
  </div>
  <InvDocTraceScanBridge
    ref="traceScanBridgeRef"
    scene="inventoryOut"
    :default-location-id="purposeLocationId"
  />
</template>

<style scoped lang="scss">
.compact-head-form :deep(.el-form-item) {
  margin-bottom: 0;
}
</style>
