<!--
  科室领用单右侧主区：科室药房 + 领用科室（手选行政科室）+ 明细。
-->
<script setup lang="ts">
import type { VxeGridPropTypes } from '#/adapter/vxe-table';
import type { FormInstance } from 'element-plus';
import type {
  DeptIssueDetailLineDto,
  DeptIssueLineDto,
  DeptPharmacyOptionDto,
} from '#/api/inventory/department/dept-issue';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocDetailGridShell from '@inventory/components/inv-doc-detail-grid-shell.vue';
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
  ElTag,
} from 'element-plus';

import { useInvDocDetailGrid } from '#/composables/use-inv-doc-detail-grid';
import { useInvDocGridRowOps } from '#/composables/use-inv-doc-grid-row-ops';
import { useInvDocParentSyncSession } from '#/composables/use-inv-doc-parent-sync-session';
import {
  agreeDeptIssueApi,
  createDeptIssueApi,
  getDeptIssueDetailApi,
  rejectDeptIssueApi,
  removeDeptIssueApi,
  submitDeptIssueApprovalApi,
  updateDeptIssueApi,
  withdrawDeptIssueApprovalApi,
} from '#/api/inventory/department/dept-issue';
import SectionTitle from '#/components/layouts/section-title.vue';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import {
  applyInventoryRowQtyUnitList,
  applyWarehouseNonInboundProductPickUnitsToRow,
  applyHydratedInventoryDetailQtyToRow,
  createInventoryQtyUnitColumn,
  handleWarehouseNonInboundUnitBandChanged,
  hydrateInventoryDetailLineQtyForGrid,
  isPresentItemId,
  scheduleInventoryProductPickFocus,
} from '#/utils/inventory/inventory-doc-grid-common';
import {
  canConvertDisplayQtyToSplitForBands,
  persistDisplayQtyAsSplitForBands,
  resolveInboundLinePersistUnitCodeForBands,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  inventoryPickMedicineCell,
  inventoryPickNumberField,
  inventoryPickStringField,
} from '#/utils/inventory/inventory-product-pick-list';
import { formatInventoryLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';
import {
  buildInventoryDocPractitionerSelectOptions,
  coercePractitionerIdForSelect,
  INV_ITEM_TABLE_TYPE_DEVICE,
  normalizeInventoryItemTableTypeOptions,
  resolveDefaultInventoryItemTableType,
  resolveDefaultPractitionerIdForSelect,
  resolveDocHeadProductPickLocationId,
} from '#/utils/inventory/inventory-workbench-list';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  inventoryRequiredSelectRule,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import {
  isStockOutDocumentEditableStatus,
  isStockOutDocumentRemovableStatus,
  buildStockOutDocActionFlags,
  stockOutStatusLabel,
  normalizeStockOutStatusSort,
} from '#/utils/inventory/stock-out-doc-actions';
import { buildInvDocumentTitle, printInvDocument } from '#/utils/print/print-inv-document';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

defineOptions({ name: 'InventoryDepartmentDeptIssueMainPane' });

interface DeptPharmacyHeadOption extends ApiOptionItem {
  organizationId?: string;
  organizationName?: string;
}

interface IssuePreviewRow {
  id: string;
  medicinePick?: Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  productName: string;
  spec: string;
  manufacturer: string;
  itemQuantity?: number;
  unitCode: string;
  unitCodeDictText?: string;
  selectedBandKind?: string;
  __prevBandKind?: string;
  unitList?: { label?: string; value: string; value_dictText?: string }[];
  unitBands?: import('#/utils/inventory/inbound-product-unit-bands').InboundProductUnitBand[];
  price: number;
  totalPrice: string;
  lotNumber: string;
  expirationDate: string;
  traceNo: string;
  remark: string;
}

interface Props {
  busNo?: string;
  isDraftOrder?: boolean;
  resetKey?: number;
  deptPharmacyOptions?: DeptPharmacyHeadOption[];
  organizationOptions?: ApiOptionItem[];
  practitionerOptions?: ApiOptionItem[];
  productTableTypeOptions?: ApiOptionItem[];
}

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  isDraftOrder: false,
  resetKey: 0,
  deptPharmacyOptions: () => [],
  organizationOptions: () => [],
  practitionerOptions: () => [],
  productTableTypeOptions: () => [],
});

const emit = defineEmits<{
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

const userStore = useUserStore();

const normalizedProductTableTypeOptions = computed(() =>
  normalizeInventoryItemTableTypeOptions(props.productTableTypeOptions),
);

const pinnedPractitionerOptions = ref<ApiOptionItem[]>([]);
const pinnedOrganizationOptions = ref<ApiOptionItem[]>([]);

const effectivePractitionerOptions = computed(() =>
  buildInventoryDocPractitionerSelectOptions(props.practitionerOptions, {
    pinned: pinnedPractitionerOptions.value,
    userInfo: userStore.userInfo as null | Record<string, unknown>,
  }),
);

/** 领用科室下拉：init 全科室 + 详情 pin。 */
const effectiveOrganizationOptions = computed(() => {
  const seen = new Set<string>();
  const out: ApiOptionItem[] = [];
  // 先 pin 再 init，保证历史科室名优先展示
  for (const o of [...pinnedOrganizationOptions.value, ...(props.organizationOptions ?? [])]) {
    const v = String(o.value ?? '').trim();
    if (v === '' || seen.has(v)) continue;
    seen.add(v);
    out.push({ label: String(o.label ?? v), value: v });
  }
  return out;
});

/** 详情领用科室不在下拉时临时 pin，避免 ElSelect 只显示裸 id。 */
function pinOrganizationOption(id: unknown, label?: string) {
  const v = String(id ?? '').trim();
  if (v === '') return;
  const lbl = String(label ?? '').trim();
  if (effectiveOrganizationOptions.value.some((o) => String(o.value) === v)) return;
  if (lbl !== '' && lbl !== v) {
    pinnedOrganizationOptions.value = [{ label: lbl, value: v }];
  }
}

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

const headItemTableType = ref(INV_ITEM_TABLE_TYPE_DEVICE);
const practitionerId = ref<number | string | undefined>(undefined);

/** 选品门禁：跟单头科室药房；未选时用 init 首个真实库位。 */
const productPickGateLocationId = computed(() =>
  resolveDocHeadProductPickLocationId(purposeLocationId.value, props.deptPharmacyOptions),
);

const headerBusNo = ref<string | undefined>(undefined);
const purposeLocationId = ref<number | string | undefined>(undefined);
const consumingOrganizationId = ref<number | string | undefined>(undefined);
const headFormRef = ref<FormInstance>();

const headFormModel = computed(() => ({
  purposeLocationId: purposeLocationId.value ?? '',
  consumingOrganizationId: consumingOrganizationId.value ?? '',
}));

const headFormRules = computed(() => ({
  ...buildInventoryLocationHeadFormRules({
    purposeLocationId: '科室药房',
  }),
  consumingOrganizationId: [inventoryRequiredSelectRule('领用科室')],
}));

const headReason = ref('');
const detailStatusEnum = ref<number | string | undefined>(undefined);
const saving = ref(false);

/** 打印/展示用领用科室名（跟下拉当前选中）。 */
const consumingOrganizationName = computed(() => {
  const id = String(consumingOrganizationId.value ?? '').trim();
  if (id === '') return '';
  const hit = effectiveOrganizationOptions.value.find((o) => String(o.value) === id);
  return String(hit?.label ?? '').trim();
});

const isNewDraft = computed(() => !headerBusNo.value);
const documentEditable = computed(
  () => isNewDraft.value || isStockOutDocumentEditableStatus(detailStatusEnum.value),
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
  return {
    ...base,
    saveDisabled: base.saveDisabled || saving.value,
    printDisabled: saving.value || !hasPersistedBusNo,
  };
});

/** 有效期展示：取日期部分（与请领 formatRequisitionExpirationDisplay 同构）。 */
function formatIssueExpirationDisplay(raw: unknown): string {
  const s = String(raw ?? '').trim();
  if (s === '') return '';
  if (s.includes(' ')) return s.split(' ')[0] ?? s;
  if (s.includes('T')) return s.split('T')[0] ?? s;
  return s;
}

/** 表格有效期 → 请求体 endTime（空格分隔，对齐后端 DATE_TIME_PATTERN / Flexible 反序列化）。 */
function normalizeIssueLineDateTimeForApi(dateStr: string): string | undefined {
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

/** 从选品行解析有效期（兼容 expirationDate / endTime）。 */
function resolvePickExpirationRaw(pick: Record<string, unknown>): unknown {
  if (
    pick.expirationDate !== undefined &&
    pick.expirationDate !== null &&
    pick.expirationDate !== ''
  ) {
    return pick.expirationDate;
  }
  if (pick.endTime !== undefined && pick.endTime !== null && pick.endTime !== '') {
    return pick.endTime;
  }
  return undefined;
}

/** 编辑态：按数量 × 单价刷新金额（详情 load 用落库 totalPrice）。 */
function recalcIssueLineTotal(row: IssuePreviewRow) {
  const qty = Number(row.itemQuantity);
  const unit = Number(row.price);
  row.totalPrice =
    Number.isFinite(qty) && Number.isFinite(unit) ? formatInventoryLineTotal(qty, unit) : '';
}

function createEmptyRow(): IssuePreviewRow {
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
    price: 0,
    totalPrice: '',
    lotNumber: '',
    expirationDate: '',
    traceNo: '',
    remark: '',
  };
}

/** 选品回填：规格/厂家/批号/效期/单价，并重算金额。 */
function syncRowFromProductPick(pick: Record<string, unknown>, row: IssuePreviewRow) {
  row.medicinePick = inventoryPickMedicineCell(pick) ?? (pick as IssuePreviewRow['medicinePick']);
  row.itemTable = inventoryPickStringField(pick, 'productTableName');
  const defId = pick.productDefinitionId;
  row.itemId =
    defId === undefined || defId === null || String(defId).trim() === ''
      ? undefined
      : String(defId).trim();
  row.productName =
    inventoryPickStringField(pick, 'merchandiseName') ||
    inventoryPickStringField(pick, 'productName');
  row.spec = inventoryPickStringField(pick, 'specification');
  row.manufacturer = inventoryPickStringField(pick, 'manufacturer');
  row.lotNumber = inventoryPickStringField(pick, 'lotNumber');
  row.expirationDate = formatIssueExpirationDisplay(resolvePickExpirationRaw(pick));
  row.traceNo = inventoryPickStringField(pick, 'traceNo');
  applyWarehouseNonInboundProductPickUnitsToRow(pick, row, {
    legacyUnitPrice: inventoryPickNumberField(pick, 'purchaseUnitPrice'),
  });
  recalcIssueLineTotal(row);
  // 选品后强制刷新只读列（规格/厂家/有效期等），避免 keepSource 下单元格不重绘
  void nextTick(() => {
    const grid = gridApi?.grid as { reloadRow?: (r: IssuePreviewRow) => void } | undefined;
    grid?.reloadRow?.(row);
  });
}

function handleDeptIssueUnitBandChanged(row: IssuePreviewRow) {
  handleWarehouseNonInboundUnitBandChanged(row, {
    getQty: () => Number(row.itemQuantity),
    setQty: (qty) => {
      row.itemQuantity = qty;
    },
    onAfterChanged: () => recalcIssueLineTotal(row),
  });
}

/** 详情行 → 网格行（规格/厂家/金额/效期/备注回显）。 */
function mapDetailLineToGrid(ln: DeptIssueDetailLineDto, rowIdx: number): IssuePreviewRow {
  const name = String(ln.productName ?? ln.itemName ?? '').trim();
  const totalRaw = ln.totalPrice;
  const row: IssuePreviewRow = {
    id: `ln-${ln.id ?? rowIdx}`,
    medicinePick: name ? { id: ln.itemId, name } : undefined,
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
    expirationDate: formatIssueExpirationDisplay(ln.endTime),
    traceNo: String(ln.traceNo ?? ''),
    remark: String(ln.remake ?? ''),
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

function isIssueRowComplete(row: IssuePreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.itemQuantity);
  if (!Number.isFinite(qty) || qty <= 0) return false;
  return !!String(row.unitCode ?? '').trim();
}

function handleIssueProductPick(row: IssuePreviewRow) {
  scheduleInventoryProductPickFocus(issueGridEdit, row);
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
        listSource: 'dept-issue',
        query: async () => ({
          productTableName: headItemTableType.value,
          purposeLocationId: productPickGateLocationId.value,
        }),
        onSelected: (product: Record<string, unknown>, row: IssuePreviewRow) => {
          syncRowFromProductPick(product, row);
          handleIssueProductPick(row);
        },
      },
    },
    showOverflow: true,
  },
  { field: 'spec', title: '规格', width: 120, showOverflow: true },
  { field: 'manufacturer', title: '厂家', minWidth: 120, showOverflow: true },
  createInventoryQtyUnitColumn({
    onUnitChange: (row) => handleDeptIssueUnitBandChanged(row as IssuePreviewRow),
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
  gridEdit: issueGridEdit,
  collectGridRows,
  patchGridEditConfig,
} = useInvDocDetailGrid<IssuePreviewRow>({
  columns,
  documentEditable: () => documentEditable.value,
  beforeEditMethod: () => documentEditable.value,
  onEditClosed: (row) => recalcIssueLineTotal(row),
  config: {
    rowKey: (row) => row.id,
    productField: 'medicinePick',
    editFieldOrder: ['medicinePick', '_qtyUnit', 'traceNo', 'remark'],
    editableFields: new Set(['_qtyUnit', 'medicinePick', 'traceNo', 'remark']),
    verticalNavFields: new Set(['_qtyUnit', 'traceNo', 'remark']),
    firstFillFieldAfterPick: '_qtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isIssueRowComplete,
  },
});

const { addItem: addIssueRow, deleteItem } = useInvDocGridRowOps({
  documentEditable: () => documentEditable.value,
  getGridApi: () => gridApi ?? null,
  gridEdit: issueGridEdit,
  createEmptyRow,
  hasProduct: (row) => isPresentItemId(row.itemId),
});

const traceScanBridgeRef = ref<InstanceType<typeof InvDocTraceScanBridge> | null>(null);

/** 打开批量追溯码扫码弹窗 */
function handleOpenTraceScan(): void {
  traceScanBridgeRef.value?.open(collectGridRows());
}

async function addItem() {
  if (!documentEditable.value) return;
  if (
    !(await validateInventoryHeadFormFields(headFormRef.value, [
      'purposeLocationId',
      'consumingOrganizationId',
    ]))
  ) {
    return;
  }
  await addIssueRow();
}

function resetDetailForCreate() {
  headerBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  pinnedPractitionerOptions.value = [];
  pinnedOrganizationOptions.value = [];
  purposeLocationId.value = undefined;
  consumingOrganizationId.value = undefined;
  headReason.value = '';
  practitionerId.value = resolveDefaultPractitionerIdForSelect(
    effectivePractitionerOptions.value,
    userStore.userInfo as null | Record<string, unknown>,
  );
  clearInventoryHeadFormValidation(headFormRef.value);
  issueGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchGridEditConfig();
  });
}

async function loadDetail(busNo: string, loadGen?: number) {
  issueGridEdit.resetFormMode();
  issueGridEdit.clearGridEdit();
  try {
    const res = await getDeptIssueDetailApi({ busNo });
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到领用单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    detailStatusEnum.value = normalizeStockOutStatusSort(data.statusEnum);
    purposeLocationId.value = data.purposeLocationId ?? undefined;
    pinnedOrganizationOptions.value = [];
    pinOrganizationOption(data.consumingOrganizationId, data.consumingOrganizationName);
    consumingOrganizationId.value =
      data.consumingOrganizationId !== undefined && data.consumingOrganizationId !== null
        ? String(data.consumingOrganizationId)
        : undefined;
    headReason.value = String(data.reason ?? '');
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

function buildLinesPayload(rows: IssuePreviewRow[]): DeptIssueLineDto[] {
  return rows.map((r) => {
    recalcIssueLineTotal(r);
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
      traceNo: String(r.traceNo ?? '').trim() || undefined,
      price: Number.isFinite(unit) ? unit : undefined,
      endTime: normalizeIssueLineDateTimeForApi(String(r.expirationDate ?? '')),
      remake: String(r.remark ?? '').trim() || undefined,
    };
  });
}

async function persistIssue(): Promise<string | undefined> {
  if (!documentEditable.value) return undefined;
  if (
    !(await validateInventoryHeadFormFields(headFormRef.value, [
      'purposeLocationId',
      'consumingOrganizationId',
    ]))
  ) {
    return undefined;
  }
  const rows = collectGridRows();
  const handlerId = practitionerId.value;
  if (!handlerId) {
    ElMessage.warning('请选择经手人');
    return undefined;
  }
  const orgId = preserveApiIdForRequest(consumingOrganizationId.value);
  if (!orgId) {
    ElMessage.warning('请选择领用科室');
    return undefined;
  }
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一行明细');
    return undefined;
  }
  for (const [i, r] of rows.entries()) {
    if (!isIssueRowComplete(r)) {
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
    const common = {
      purposeLocationId: purposeLocationId.value as number | string,
      consumingOrganizationId: orgId,
      practitionerId: handlerId,
      reason: headReason.value.trim() || undefined,
      lines: buildLinesPayload(rows),
    };
    if (headerBusNo.value) {
      await updateDeptIssueApi({ busNo: headerBusNo.value, ...common });
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }
    const res = await createDeptIssueApi(common);
    const no = res.data?.busNo;
    ElMessage.success('新建成功');
    if (no) {
      headerBusNo.value = no;
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
  if (!no || !isStockOutDocumentRemovableStatus(detailStatusEnum.value)) return;
  try {
    await ElMessageBox.confirm('确认删除整张领用单？', '提示', { type: 'warning' });
    await removeDeptIssueApi({ busNo: no });
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
  await submitDeptIssueApprovalApi({ busNo: no });
  ElMessage.success('已提交审批');
  await loadDetail(no);
  emit('documentSaved', { busNo: no });
}

async function handleWithdraw() {
  const no = headerBusNo.value;
  if (!no) return;
  await withdrawDeptIssueApprovalApi({ busNo: no });
  ElMessage.success('已撤回');
  await loadDetail(no);
  emit('documentSaved', { busNo: no });
}

async function handleApprove() {
  const no = headerBusNo.value;
  if (!no) return;
  await agreeDeptIssueApi({
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
    await rejectDeptIssueApi({
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

watch(
  normalizedProductTableTypeOptions,
  (opts) => {
    if (opts.length === 0) return;
    const current = String(headItemTableType.value ?? '').trim();
    if (!current || !opts.some((o) => o.value === current)) {
      headItemTableType.value = resolveDefaultInventoryItemTableType(
        opts,
        INV_ITEM_TABLE_TYPE_DEVICE,
      );
    }
  },
  { immediate: true },
);

interface DeptIssueDraftSnap {
  purposeLocationId?: number | string;
  consumingOrganizationId?: number | string;
  practitionerId?: number | string;
  headReason: string;
  headItemTableType: string;
  rows: IssuePreviewRow[];
}

const parentSync = useInvDocParentSyncSession<DeptIssueDraftSnap>();

/** 侧栏切单：缓存/恢复本地草稿，避免已落库明细污染新建单 */
async function syncFromParentContext() {
  await parentSync.syncFromParentContext(
    { isDraftOrder: Boolean(props.isDraftOrder), parentBusNo: props.busNo },
    {
      getLocalPersistedBusNo: () => headerBusNo.value,
      captureDraft: () => ({
        purposeLocationId: purposeLocationId.value,
        consumingOrganizationId: consumingOrganizationId.value,
        practitionerId: practitionerId.value,
        headReason: headReason.value,
        headItemTableType: headItemTableType.value,
        rows: collectGridRows().map((r) => ({ ...r })),
      }),
      restoreDraft: async (snap) => {
        headerBusNo.value = undefined;
        detailStatusEnum.value = undefined;
        purposeLocationId.value = snap.purposeLocationId;
        consumingOrganizationId.value = snap.consumingOrganizationId;
        practitionerId.value = snap.practitionerId;
        headReason.value = snap.headReason;
        headItemTableType.value = snap.headItemTableType;
        issueGridEdit.resetFormMode();
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
function resolveIssuePrintUnitLabel(row: IssuePreviewRow): string {
  const kind = String(row.selectedBandKind ?? '').trim();
  const byKind = row.unitList?.find((item) => String(item.value ?? '') === kind);
  if (byKind) {
    return String(byKind.label ?? byKind.value_dictText ?? '').trim();
  }
  return String(row.unitCodeDictText ?? row.unitCode ?? '').trim();
}

/** 打印发放单（计价流转模版） */
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
  const itemTypeText = resolvePrintOptionLabel(
    normalizedProductTableTypeOptions.value,
    headItemTableType.value,
  );
  const practitionerName = resolvePrintOptionLabel(
    effectivePractitionerOptions.value,
    practitionerId.value,
  );
  const documentTitle = buildInvDocumentTitle(pharmacyName, '发放单');

  // 按当前网格展示值组装 PrintData（含规格/厂家/效期）
  const lineRows = productRows.map((row) => ({
    productName: String(row.productName ?? '').trim(),
    specText: String(row.spec ?? '').trim(),
    manufacturerText: String(row.manufacturer ?? '').trim(),
    qty: row.itemQuantity,
    unitLabel: resolveIssuePrintUnitLabel(row),
    lotNumber: String(row.lotNumber ?? '').trim(),
    expiryText: String(row.expirationDate ?? '').trim(),
    amount: row.totalPrice,
  }));

  await printInvDocument({
    family: 'movement',
    source: {
      hospitalName: resolvePrintHospitalName(),
      documentTitle,
      docNo: busNo,
      applyTimeText: '',
      itemTypeText,
      sourceLocationText: pharmacyName,
      supplierText: '',
      linkDocText: '',
      destLocationText: String(consumingOrganizationName.value ?? '').trim(),
      invoiceNoText: '',
      practitionerName,
      lineRows,
      preparedByName: practitionerName,
      approvedByName: '',
    },
    bizRef: { busNo, docKind: 'dept-issue' },
    jobName: documentTitle,
  });
}
</script>

<template>
  <div class="dept-issue-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
    <InvMainShell>
      <template #actionBar>
        <div class="flex w-full flex-wrap items-center justify-end gap-2">
          <InvDocActions
            v-bind="docActionFlags"
            @approve="handleApprove"
            @print="handlePrint"
            @reject="handleReject"
            @remove-doc="handleRemoveDoc"
            @save="persistIssue"
            @save-and-submit="
              async () => {
                const no = await persistIssue();
                if (no) await handleSubmitApproval();
              }
            "
            @submit-approval="handleSubmitApproval"
            @withdraw="handleWithdraw"
          />
        </div>
      </template>

      <div
        class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-2 pt-2"
        @mousedown="issueGridEdit.onSurfaceMouseDown"
      >
        <div class="shrink-0">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <SectionTitle title="科室领用单" />
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
                <ElSelect
                  v-model="purposeLocationId"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="p in deptPharmacyOptions"
                    :key="String(p.value)"
                    :label="p.label"
                    :value="p.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="领用科室" prop="consumingOrganizationId">
                <ElSelect
                  v-model="consumingOrganizationId"
                  class="w-full"
                  filterable
                  clearable
                  placeholder="请选择领用科室"
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="o in effectiveOrganizationOptions"
                    :key="String(o.value)"
                    :label="o.label"
                    :value="o.value"
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
                    v-for="t in normalizedProductTableTypeOptions"
                    :key="String(t.value)"
                    :label="t.label"
                    :value="String(t.value)"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="领用事由">
                <ElInput
                  v-model="headReason"
                  maxlength="200"
                  clearable
                  :disabled="!documentEditable"
                />
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <InvDocDetailGridShell
          :bind-grid-wrap="bindGridWrap"
          :on-grid-keydown="issueGridEdit.onGridKeydown"
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
                @click="deleteItem(row as IssuePreviewRow)"
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
