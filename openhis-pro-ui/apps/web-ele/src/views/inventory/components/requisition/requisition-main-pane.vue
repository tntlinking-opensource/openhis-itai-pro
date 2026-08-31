<!--
  请领单右侧主区：源药库 + 目的药房/科室药房，无供应商。
-->
<script setup lang="ts">
import type { VxeGridPropTypes } from '#/adapter/vxe-table';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocDetailGridShell from '@inventory/components/inv-doc-detail-grid-shell.vue';
import InvDocGridToolbar from '@inventory/components/inv-doc-grid-toolbar.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
import type { FormInstance } from 'element-plus';

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
  ElTag,
} from 'element-plus';

import { useInvDocDetailGrid } from '#/composables/use-inv-doc-detail-grid';
import { useInvDocGridRowOps } from '#/composables/use-inv-doc-grid-row-ops';
import { useInvDocParentSyncSession } from '#/composables/use-inv-doc-parent-sync-session';
import {
  agreeDepartmentRequisitionApi,
  createDepartmentRequisitionApi,
  DEPT_REQUISITION_CATEGORY_ENUM,
  DEPT_REQUISITION_TYPE_ENUM,
  getDepartmentRequisitionDetailApi,
  pageProductsForDepartmentRequisitionApi,
  rejectDepartmentRequisitionApi,
  removeDepartmentRequisitionApi,
  submitDepartmentRequisitionApprovalApi,
  updateDepartmentRequisitionApi,
  withdrawDepartmentRequisitionApprovalApi,
  type DepartmentRequisitionDetailLineDto,
  type DepartmentRequisitionLineDto,
} from '#/api/inventory/department/requisition';
import {
  agreePharmacyRequisitionApi,
  createPharmacyRequisitionApi,
  getPharmacyRequisitionDetailApi,
  pageProductsForPharmacyRequisitionApi,
  PHA_REQUISITION_CATEGORY_ENUM,
  PHA_REQUISITION_PURPOSE_TYPE_ENUM,
  PHA_REQUISITION_SOURCE_TYPE_ENUM,
  PHA_REQUISITION_TYPE_ENUM,
  rejectPharmacyRequisitionApi,
  removePharmacyRequisitionApi,
  submitPharmacyRequisitionApprovalApi,
  updatePharmacyRequisitionApi,
  withdrawPharmacyRequisitionApprovalApi,
  type PharmacyRequisitionDetailLineDto,
  type PharmacyRequisitionLineDto,
} from '#/api/inventory/pharmacy/requisition';
import SectionTitle from '#/components/layouts/section-title.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { resolveLoginPractitionerIdForRequest } from '#/utils/format/preserve-api-id';
import {
  applyHydratedInventoryDetailQtyToRow,
  applyInventoryRowQtyUnitList,
  applyWarehouseNonInboundProductPickUnitsToRow,
  createInventoryQtyUnitColumn,
  handleWarehouseNonInboundUnitBandChanged,
  hydrateInventoryDetailLineQtyForGrid,
  INV_DOC_HEAD_FORM_GRID_CLASS,
  INV_DOC_HEAD_SECTION_CLASS,
  isPresentItemId,
  scheduleInventoryProductPickFocus,
} from '#/utils/inventory/inventory-doc-grid-common';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import {
  buildInventoryDocPractitionerSelectOptions,
  INV_ITEM_TABLE_TYPE_DEVICE,
  INV_ITEM_TABLE_TYPE_MEDICATION,
  coercePractitionerIdForSelect,
  normalizeInventoryItemTableTypeOptions,
  requireSpecificInventoryLocationId,
  resolveDefaultInventoryItemTableType,
  resolveFirstRealLocationIdFromOptionItems,
  resolveSpecificInventoryLocationId,
  resolveDefaultPractitionerIdForSelect,
  resolveInvDocPreparedByName,
  resolveInvDocApprovedByName,
} from '#/utils/inventory/inventory-workbench-list';
import {
  inventoryPickAvailableQtyAsStockDisplay,
  inventoryPickMedicineCell,
  inventoryPickNumberField,
  inventoryPickStringField,
  mapInventoryProductPickListRow,
} from '#/utils/inventory/inventory-product-pick-list';
import { formatInventoryLineTotal, inventoryLineTotalNumber } from '#/utils/format/money-scale';
import {
  canConvertDisplayQtyToSplitForBands,
  convertInboundQtyBetweenBandKinds,
  persistDisplayQtyAsSplitForBands,
  resolveInboundLinePersistUnitCodeForBands,
} from '#/utils/inventory/inbound-product-unit-bands';
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

defineOptions({ name: 'InventoryRequisitionMainPane' });

type RequisitionVariant = 'department' | 'pharmacy';

interface RequisitionPreviewRow {
  id: string;
  medicinePick?: Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  inventoryItemId?: number | string;
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
  remark: string;
}

interface Props {
  /** 侧栏当前单据行（制单人/审批人与队列卡片同口径） */
  queueCardRow?: null | Record<string, unknown>;
  variant: RequisitionVariant;
  busNo?: string;
  isDraftOrder?: boolean;
  resetKey?: number;
  warehouseOptions: ApiOptionItem[];
  pharmacyOptions: ApiOptionItem[];
  practitionerOptions?: ApiOptionItem[];
  itemTableTypeOptions?: ApiOptionItem[];
  headSectionTitle?: string;
  purposeLocationLabel?: string;
}

const props = withDefaults(defineProps<Props>(), {
  queueCardRow: null,
  busNo: '',
  isDraftOrder: false,
  resetKey: 0,
  warehouseOptions: () => [],
  pharmacyOptions: () => [],
  practitionerOptions: () => [],
  itemTableTypeOptions: () => [],
  headSectionTitle: '请领单信息',
  purposeLocationLabel: '目的药房',
});

const emit = defineEmits<{
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

const userStore = useUserStore();

const isPharmacyVariant = computed(() => props.variant === 'pharmacy');

const listSource = computed(() =>
  isPharmacyVariant.value ? 'pharmacy-requisition' : 'department-requisition',
);

/** 选品门禁：init 首个真实源药库，与侧栏列表筛选解耦。 */
const productPickGateLocationId = computed(() =>
  resolveFirstRealLocationIdFromOptionItems(props.warehouseOptions),
);

const normalizedItemTableTypeOptions = computed(() =>
  normalizeInventoryItemTableTypeOptions(props.itemTableTypeOptions),
);

/** 科室请领默认耗材；药房请领默认药品。 */
function preferredItemTableType(): string {
  return isPharmacyVariant.value ? INV_ITEM_TABLE_TYPE_MEDICATION : INV_ITEM_TABLE_TYPE_DEVICE;
}

/** 取当前可选项目类型默认值。 */
function firstItemTableTypeValue(): string {
  return resolveDefaultInventoryItemTableType(
    normalizedItemTableTypeOptions.value,
    preferredItemTableType(),
  );
}

const pinnedPractitionerOptions = ref<ApiOptionItem[]>([]);

const effectivePractitionerOptions = computed(() =>
  buildInventoryDocPractitionerSelectOptions(props.practitionerOptions, {
    pinned: pinnedPractitionerOptions.value,
    userInfo: userStore.userInfo as null | Record<string, unknown>,
  }),
);

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
const sourceLocationId = ref<number | string | undefined>(undefined);
const purposeLocationId = ref<number | string | undefined>(undefined);
const headFormRef = ref<FormInstance>();

const headFormModel = computed(() => ({
  sourceLocationId: sourceLocationId.value ?? '',
  purposeLocationId: purposeLocationId.value ?? '',
}));

const headFormRules = computed(() =>
  buildInventoryLocationHeadFormRules({
    sourceLocationId: '源药库',
    purposeLocationId: props.purposeLocationLabel,
  }),
);

const practitionerId = ref<number | string | undefined>(undefined);
const headItemTableType = ref(firstItemTableTypeValue());
const detailStatusEnum = ref<number | string | undefined>(undefined);
const headApplyTime = ref(formatDateTime(new Date()));
const saving = ref(false);
/** 一键请领：分页拉取选品中 */
const oneClickLoading = ref(false);

/** 一键请领分页大小（仅前端循环拉全量，不改保存契约） */
const ONE_CLICK_PAGE_SIZE = 100;

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
  const busy = saving.value;
  return {
    ...base,
    saveDisabled: base.saveDisabled || busy,
    saveAndSubmitDisabled: base.saveAndSubmitDisabled || busy,
    printDisabled: busy || !hasPersistedBusNo,
  };
});

function nowIsoLocal(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function resolveApplyTimeForApi(): string {
  const t = String(headApplyTime.value ?? '').trim();
  if (t === '') return formatDateTime(new Date());
  return t.includes('T') ? t.replace('T', ' ') : t;
}

function resolveOccurrenceTimeForLine(): string {
  const t = resolveApplyTimeForApi();
  return t.includes('T') ? t : t.replace(' ', 'T');
}

function formatRequisitionExpirationDisplay(raw: unknown): string {
  const s = String(raw ?? '').trim();
  if (s === '') return '';
  if (s.includes(' ')) return s.split(' ')[0] ?? s;
  if (s.includes('T')) return s.split('T')[0] ?? s;
  return s;
}

/** 表格有效期 → 请求体 endTime（与进货单 normalizeInboundLineDateTimeForApi 同构） */
function normalizeRequisitionLineDateTimeForApi(dateStr: string): string | undefined {
  const raw = String(dateStr ?? '').trim();
  if (!raw) return undefined;
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    return `${raw}T00:00:00`;
  }
  return raw.includes('T') ? raw : raw.replace(' ', 'T');
}

function recalcRequisitionLineTotal(row: RequisitionPreviewRow) {
  const qty = Number(row.itemQuantity);
  const unit = Number(row.price);
  row.totalPrice =
    Number.isFinite(qty) && Number.isFinite(unit) ? formatInventoryLineTotal(qty, unit) : '';
}

function createEmptyRow(): RequisitionPreviewRow {
  return {
    id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    medicinePick: undefined,
    itemTable: undefined,
    itemId: undefined,
    inventoryItemId: undefined,
    productName: '',
    spec: '',
    manufacturer: '',
    itemQuantity: undefined,
    unitCode: '',
    unitCodeDictText: '',
    selectedBandKind: 'LEGACY',
    __prevBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: '单位' }],
    price: 0,
    totalPrice: '',
    lotNumber: '',
    expirationDate: '',
    remark: '',
  };
}

function syncRowFromProductPick(pick: Record<string, unknown>, row: RequisitionPreviewRow) {
  row.medicinePick =
    inventoryPickMedicineCell(pick) ?? (pick as RequisitionPreviewRow['medicinePick']);
  row.itemTable = inventoryPickStringField(pick, 'productTableName');
  const defId = pick.productDefinitionId;
  row.itemId =
    defId === undefined || defId === null || String(defId).trim() === ''
      ? undefined
      : String(defId).trim();
  const invId = pick.inventoryItemId;
  row.inventoryItemId =
    invId === undefined || invId === null || String(invId).trim() === ''
      ? undefined
      : String(invId).trim();
  row.productName =
    inventoryPickStringField(pick, 'merchandiseName') ||
    inventoryPickStringField(pick, 'productName');
  row.spec = inventoryPickStringField(pick, 'specification');
  row.manufacturer = inventoryPickStringField(pick, 'manufacturer');
  row.lotNumber = inventoryPickStringField(pick, 'lotNumber');
  row.expirationDate = formatRequisitionExpirationDisplay(pick.expirationDate);
  applyWarehouseNonInboundProductPickUnitsToRow(pick, row, {
    legacyUnitPrice: inventoryPickNumberField(pick, 'purchaseUnitPrice'),
  });
  recalcRequisitionLineTotal(row);
}

/**
 * 一键请领数量：按当前行计量带把 SPLIT 剩余量换成展示数；
 * 无计量带时与选品「库存数量」列同源。
 */
function resolveRequisitionQtyFromPick(
  pick: Record<string, unknown>,
  row: RequisitionPreviewRow,
): number {
  const bands = row.unitBands ?? [];
  const kind = String(row.selectedBandKind ?? '')
    .trim()
    .toUpperCase();
  const splitAvail = inventoryPickNumberField(pick, 'availableQuantity');
  if (bands.length > 0 && kind && kind !== 'LEGACY') {
    const converted = convertInboundQtyBetweenBandKinds(splitAvail, 'SPLIT', kind, bands);
    if (converted !== undefined && Number.isFinite(converted)) {
      return converted;
    }
  }
  if (
    Object.prototype.hasOwnProperty.call(pick, 'stockAvailableQty') &&
    pick.stockAvailableQty !== undefined &&
    pick.stockAvailableQty !== null
  ) {
    return inventoryPickNumberField(pick, 'stockAvailableQty');
  }
  return inventoryPickAvailableQtyAsStockDisplay(pick);
}

/** 选品行 → 请领明细（数量默认=剩余库存） */
function mapPickToRequisitionRow(pick: Record<string, unknown>): RequisitionPreviewRow {
  const mapped = mapInventoryProductPickListRow(pick);
  const row = createEmptyRow();
  syncRowFromProductPick(mapped, row);
  const qty = resolveRequisitionQtyFromPick(mapped, row);
  // 仅带入有剩余量的库存行
  row.itemQuantity = qty > 0 ? qty : undefined;
  recalcRequisitionLineTotal(row);
  return row;
}

function handleRequisitionUnitBandChanged(row: RequisitionPreviewRow) {
  handleWarehouseNonInboundUnitBandChanged(row, {
    getQty: () => Number(row.itemQuantity),
    setQty: (qty) => {
      row.itemQuantity = qty;
    },
    onAfterChanged: () => recalcRequisitionLineTotal(row),
  });
}

function mapDetailLineToGrid(
  ln: DepartmentRequisitionDetailLineDto | PharmacyRequisitionDetailLineDto,
  rowIdx: number,
): RequisitionPreviewRow {
  const itemIdRaw = ln.itemId;
  const idStr = ln.id !== undefined && ln.id !== null ? `ln-${String(ln.id)}` : `ln-x-${rowIdx}`;
  const name = String(ln.productName ?? '').trim();
  const price = ln.price === undefined || ln.price === null ? 0 : Number(ln.price);
  const totalRaw = ln.totalPrice;
  const unitCodeStr = String(ln.unitCode ?? '');
  const rawQty = ln.itemQuantity === undefined ? undefined : Number(ln.itemQuantity);
  const catalog = (ln as { productCatalog?: { unitBands?: unknown; productBusNo?: string } })
    .productCatalog;
  const unitCodeDictText = String(
    ('unitCodeDictText' in ln ? ln.unitCodeDictText : undefined) ?? ln.unitCode ?? '',
  );
  const productBusNo = String(catalog?.productBusNo ?? '').trim();
  const row: RequisitionPreviewRow = {
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
    inventoryItemId: ln.inventoryItemId,
    productName: name,
    spec: String(ln.specification ?? ''),
    manufacturer: String(ln.manufacturer ?? ''),
    itemQuantity: rawQty,
    unitCode: unitCodeStr,
    unitCodeDictText,
    price: Number.isFinite(price) ? price : 0,
    totalPrice: totalRaw === undefined || totalRaw === null ? '' : String(totalRaw),
    lotNumber: String(ln.lotNumber ?? ''),
    expirationDate: formatRequisitionExpirationDisplay(ln.endTime),
    remark: String(ln.remake ?? ''),
  };

  const hydrated = hydrateInventoryDetailLineQtyForGrid(
    {
      itemQuantity: rawQty,
      unitCode: unitCodeStr,
      unitCodeDictText,
      productCatalog: catalog,
    },
    { useSupplyLineHydrate: true },
  );
  applyHydratedInventoryDetailQtyToRow(row, hydrated);
  if (!row.unitBands?.length) {
    applyInventoryRowQtyUnitList(row);
  }
  return row;
}

function isRequisitionRowComplete(row: RequisitionPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.itemQuantity);
  if (!Number.isFinite(qty) || qty <= 0) return false;
  return !!String(row.unitCode ?? '').trim();
}

function handleRequisitionProductPick(row: RequisitionPreviewRow) {
  scheduleInventoryProductPickFocus(requisitionGridEdit, row);
}

/** 选品门禁：单头源药库优先，其次 init 首个真实源药库。 */
function resolveRequisitionProductPickSourceLocationId(): string | undefined {
  return resolveSpecificInventoryLocationId(
    sourceLocationId.value,
    productPickGateLocationId.value,
  );
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
        listSource: listSource,
        query: async () => ({
          productTableName: headItemTableType.value,
          sourceLocationId: resolveRequisitionProductPickSourceLocationId(),
        }),
        onSelected: (product: Record<string, unknown>, row: RequisitionPreviewRow) => {
          syncRowFromProductPick(product, row);
          handleRequisitionProductPick(row);
        },
      },
    },
    showOverflow: true,
  },
  {
    field: 'spec',
    title: '规格',
    width: 120,
    showOverflow: true,
  },
  {
    field: 'manufacturer',
    title: '厂家',
    minWidth: 120,
    showOverflow: true,
  },
  createInventoryQtyUnitColumn({
    onUnitChange: (row) => handleRequisitionUnitBandChanged(row as RequisitionPreviewRow),
  }),
  {
    field: 'totalPrice',
    title: '金额',
    width: 112,
    align: 'right',
    headerAlign: 'center',
    cellRender: {
      name: 'InventoryTotalAmountCell',
      props: {
        amountField: 'totalPrice',
      },
    },
  },
  {
    field: 'lotNumber',
    title: '批号',
    width: 120,
    showOverflow: true,
  },
  {
    field: 'expirationDate',
    title: '有效期',
    width: 120,
    showOverflow: true,
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

const REQUISITION_EDITABLE_FIELDS = new Set(['_qtyUnit', 'medicinePick', 'remark']);
const REQUISITION_EDIT_ORDER = ['medicinePick', '_qtyUnit', 'remark'] as const;
const REQUISITION_VERTICAL_NAV = new Set(['_qtyUnit', 'remark']);

const {
  Grid,
  gridApi,
  bindGridWrap,
  gridEdit: requisitionGridEdit,
  collectGridRows,
  patchGridEditConfig: patchRequisitionGridEditConfig,
} = useInvDocDetailGrid<RequisitionPreviewRow>({
  columns,
  documentEditable: () => documentEditable.value,
  beforeEditMethod: () => documentEditable.value,
  onEditClosed: (row) => recalcRequisitionLineTotal(row),
  config: {
    rowKey: (row) => row.id,
    productField: 'medicinePick',
    editFieldOrder: REQUISITION_EDIT_ORDER,
    editableFields: REQUISITION_EDITABLE_FIELDS,
    verticalNavFields: REQUISITION_VERTICAL_NAV,
    firstFillFieldAfterPick: '_qtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isRequisitionRowComplete,
  },
});

const { addItem: addRequisitionRow, deleteItem } = useInvDocGridRowOps({
  documentEditable: () => documentEditable.value,
  getGridApi: () => gridApi ?? null,
  gridEdit: requisitionGridEdit,
  createEmptyRow,
  hasProduct: (row) => isPresentItemId(row.itemId),
});

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
  await addRequisitionRow();
}

/** 按页请求源药库选品（仅库存>0），与单行选品同源接口 */
async function fetchRequisitionPickPage(pageNo: number, pageSize: number) {
  const sourceLocationIdForPick = resolveRequisitionProductPickSourceLocationId();
  const productTableName = String(headItemTableType.value ?? '').trim();
  const body = {
    sourceLocationId: sourceLocationIdForPick as number | string,
    productTableName,
    pageNo,
    pageSize,
    positiveQuantityOnly: true,
  };
  if (isPharmacyVariant.value) {
    return pageProductsForPharmacyRequisitionApi(body);
  }
  return pageProductsForDepartmentRequisitionApi(body);
}

/**
 * 一键请领：分页拉齐源药库有库存选品，数量=剩余量写入明细；
 * 不改保存/提交契约，仅相当于批量选品填表。
 */
async function startOneClickRequisition() {
  if (!documentEditable.value || oneClickLoading.value) return;
  if (
    !(await validateInventoryHeadFormFields(headFormRef.value, [
      'sourceLocationId',
      'purposeLocationId',
    ]))
  ) {
    return;
  }
  const productTableName = String(headItemTableType.value ?? '').trim();
  if (!productTableName) {
    ElMessage.warning('请先选择项目类型');
    return;
  }
  if (!resolveRequisitionProductPickSourceLocationId()) {
    ElMessage.warning('请先选择源药库');
    return;
  }

  const existing = collectGridRows().filter((r) => isPresentItemId(r.itemId));
  if (existing.length > 0) {
    try {
      await ElMessageBox.confirm(
        '一键请领将按当前源药库与项目类型加载全部有库存明细，现有明细将被替换，是否继续？',
        '一键请领',
        { type: 'warning', confirmButtonText: '继续', cancelButtonText: '取消' },
      );
    } catch {
      return;
    }
  }

  requisitionGridEdit.clearGridEdit();
  requisitionGridEdit.resetFormMode();
  oneClickLoading.value = true;
  try {
    const allRows: RequisitionPreviewRow[] = [];
    let pageNo = 1;
    let totalCount = 0;
    // 分页拼齐选品列表，过滤无剩余量行
    while (true) {
      const res = await fetchRequisitionPickPage(pageNo, ONE_CLICK_PAGE_SIZE);
      const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res.data);
      totalCount = parsed.totalCount;
      for (const raw of parsed.content) {
        const row = mapPickToRequisitionRow(raw);
        if (!isPresentItemId(row.itemId)) continue;
        if (row.itemQuantity === undefined || Number(row.itemQuantity) <= 0) continue;
        allRows.push(row);
      }
      if (parsed.content.length === 0) break;
      if (pageNo * ONE_CLICK_PAGE_SIZE >= totalCount) break;
      pageNo += 1;
    }

    gridApi?.grid?.loadData(allRows);
    if (allRows.length === 0) {
      ElMessage.warning('当前源药库暂无有库存可请领项目');
      return;
    }
    ElMessage.success(`已载入 ${allRows.length} 条请领明细（数量=剩余库存）`);
  } catch {
    gridApi?.grid?.loadData([]);
    ElMessage.error('一键请领加载失败');
  } finally {
    oneClickLoading.value = false;
  }
}

function resetDetailForCreate() {
  headerBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  pinnedPractitionerOptions.value = [];
  sourceLocationId.value = undefined;
  purposeLocationId.value = undefined;
  headItemTableType.value = firstItemTableTypeValue();
  practitionerId.value = resolveDefaultPractitionerIdForSelect(
    effectivePractitionerOptions.value,
    userStore.userInfo as null | Record<string, unknown>,
  );
  headApplyTime.value = formatDateTime(new Date());
  clearInventoryHeadFormValidation(headFormRef.value);
  requisitionGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchRequisitionGridEditConfig();
  });
}

async function loadDetail(busNo: string, loadGen?: number) {
  requisitionGridEdit.resetFormMode();
  requisitionGridEdit.clearGridEdit();
  try {
    const res = isPharmacyVariant.value
      ? await getPharmacyRequisitionDetailApi({ busNo })
      : await getDepartmentRequisitionDetailApi({ busNo });
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到请领单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    detailStatusEnum.value = normalizeStockOutStatusSort(data.statusEnum);
    sourceLocationId.value = data.sourceLocationId ?? undefined;
    purposeLocationId.value = data.purposeLocationId ?? undefined;
    pinnedPractitionerOptions.value = [];
    const detailPractitionerId =
      'practitionerId' in data && data.practitionerId !== undefined
        ? data.practitionerId
        : 'applicantId' in data
          ? (data as { applicantId?: unknown }).applicantId
          : undefined;
    pinPractitionerOption(detailPractitionerId);
    practitionerId.value = coercePractitionerIdForSelect(
      detailPractitionerId,
      effectivePractitionerOptions.value,
    );
    const firstLineTable = String(data.lines?.[0]?.itemTable ?? '').trim();
    headItemTableType.value = firstLineTable !== '' ? firstLineTable : firstItemTableTypeValue();
    const firstOcc = data.lines?.find((ln) => ln.occurrenceTime)?.occurrenceTime;
    headApplyTime.value = firstOcc ? formatDateTime(String(firstOcc)) : formatDateTime(new Date());
    const gridRows = (data.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData(gridRows);
      patchRequisitionGridEditConfig();
    });
  } catch {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchRequisitionGridEditConfig();
    });
  }
}

function buildLinesPayload(
  rows: RequisitionPreviewRow[],
): DepartmentRequisitionLineDto[] | PharmacyRequisitionLineDto[] {
  const occurrenceTime = resolveOccurrenceTimeForLine();
  return rows.map((r) => {
    const qty = Number(r.itemQuantity);
    const unit = Number(r.price);
    const tpRaw = String(r.totalPrice ?? '').trim();
    const tpNum = Number(tpRaw);
    const totalPrice =
      Number.isFinite(tpNum) && tpRaw !== ''
        ? tpNum
        : Number.isFinite(qty) && Number.isFinite(unit)
          ? inventoryLineTotalNumber(qty, unit)
          : undefined;
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
      }) || String(r.unitCode ?? '').trim();
    const endTime = normalizeRequisitionLineDateTimeForApi(String(r.expirationDate ?? ''));
    return {
      itemTable: r.itemTable,
      itemId: r.itemId,
      inventoryItemId:
        r.inventoryItemId === undefined || r.inventoryItemId === null
          ? undefined
          : r.inventoryItemId,
      itemQuantity: persisted.itemQuantity,
      unitCode: unitCode || undefined,
      lotNumber: String(r.lotNumber ?? '').trim() || undefined,
      price: Number.isFinite(unit) && unit > 0 ? unit : undefined,
      totalPrice,
      occurrenceTime,
      ...(endTime ? { endTime } : {}),
      remake: String(r.remark ?? '').trim() || undefined,
    };
  });
}

function validateBeforeSave(rows: RequisitionPreviewRow[]): boolean {
  const purposeKind = isPharmacyVariant.value ? 'pharmacy' : 'deptPharmacy';
  if (
    !requireSpecificInventoryLocationId(sourceLocationId.value, 'warehouse') ||
    !requireSpecificInventoryLocationId(purposeLocationId.value, purposeKind)
  ) {
    return false;
  }
  if (!practitionerId.value) {
    ElMessage.warning('请选择经手人');
    return false;
  }
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一行明细');
    return false;
  }
  for (const [i, r] of rows.entries()) {
    if (!r.itemTable || !r.itemId) {
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

async function persistRequisition(): Promise<string | undefined> {
  if (!documentEditable.value) {
    ElMessage.warning('当前状态不允许修改');
    return undefined;
  }
  const rows = collectGridRows();
  if (!validateBeforeSave(rows)) return undefined;

  const handlerId = practitionerId.value;
  if (!handlerId) return undefined;

  saving.value = true;
  try {
    const lines = buildLinesPayload(rows);
    if (isPharmacyVariant.value) {
      const common = {
        sourceLocationId: sourceLocationId.value as number | string,
        purposeLocationId: purposeLocationId.value as number | string,
        sourceTypeEnum: PHA_REQUISITION_SOURCE_TYPE_ENUM,
        purposeTypeEnum: PHA_REQUISITION_PURPOSE_TYPE_ENUM,
        categoryEnum: PHA_REQUISITION_CATEGORY_ENUM,
        typeEnum: PHA_REQUISITION_TYPE_ENUM,
        practitionerId: handlerId,
        lines: lines as PharmacyRequisitionLineDto[],
      };
      if (headerBusNo.value) {
        await updatePharmacyRequisitionApi({ busNo: headerBusNo.value, ...common });
        ElMessage.success('保存成功');
        await loadDetail(headerBusNo.value);
        emit('documentSaved', { busNo: headerBusNo.value });
        return headerBusNo.value;
      }
      const res = await createPharmacyRequisitionApi(common);
      const no = res.data?.busNo;
      ElMessage.success('新建成功');
      if (no) {
        headerBusNo.value = no;
        detailStatusEnum.value = normalizeStockOutStatusSort(res.data?.statusEnum) ?? 1;
        await loadDetail(no);
      }
      emit('documentSaved', { busNo: no });
      return no;
    }

    const deptCommon = {
      sourceLocationId: sourceLocationId.value as number | string,
      purposeLocationId: purposeLocationId.value as number | string,
      sourceTypeEnum: 'CABINET',
      purposeTypeEnum: 'PHARMACY',
      categoryEnum: DEPT_REQUISITION_CATEGORY_ENUM,
      practitionerId: handlerId,
      lines: lines as DepartmentRequisitionLineDto[],
    };
    if (headerBusNo.value) {
      await updateDepartmentRequisitionApi({ busNo: headerBusNo.value, ...deptCommon });
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }
    const res = await createDepartmentRequisitionApi(deptCommon);
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
  await persistRequisition();
}

async function handleSaveAndSubmitDoc() {
  const no = await persistRequisition();
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
  if (!no || !isStockOutDocumentRemovableStatus(detailStatusEnum.value)) {
    ElMessage.warning('仅待审核、驳回、已撤回单据可删除');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除整张请领单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    if (isPharmacyVariant.value) {
      await removePharmacyRequisitionApi({ busNo: no });
    } else {
      await removeDepartmentRequisitionApi({ busNo: no });
    }
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
  try {
    const res = isPharmacyVariant.value
      ? await submitPharmacyRequisitionApprovalApi({ busNo: no, applyClock: nowIsoLocal() })
      : await submitDepartmentRequisitionApprovalApi({ busNo: no, applyClock: nowIsoLocal() });
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
    const res = isPharmacyVariant.value
      ? await withdrawPharmacyRequisitionApprovalApi({ busNo: no })
      : await withdrawDepartmentRequisitionApprovalApi({ busNo: no });
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
  const operatorId = resolveLoginPractitionerIdForRequest(
    userStore.userInfo as null | Record<string, unknown>,
  );
  try {
    if (isPharmacyVariant.value) {
      await agreePharmacyRequisitionApi({
        busNo: no,
        operatorId,
        approvalClock: nowIsoLocal(),
      });
    } else {
      await agreeDepartmentRequisitionApi({
        busNo: no,
        operatorId,
        approvalClock: nowIsoLocal(),
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
    const operatorId = resolveLoginPractitionerIdForRequest(
      userStore.userInfo as null | Record<string, unknown>,
    );
    if (isPharmacyVariant.value) {
      await rejectPharmacyRequisitionApi({
        busNo: no,
        rejectReason: value,
        operatorId,
        approvalClock: nowIsoLocal(),
      });
    } else {
      await rejectDepartmentRequisitionApi({
        busNo: no,
        rejectReason: value,
        operatorId,
        approvalClock: nowIsoLocal(),
      });
    }
    ElMessage.success('已驳回');
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch (e) {
    if (e === 'cancel') return;
  }
}

interface RequisitionDraftSnap {
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  practitionerId?: number | string;
  headItemTableType: string;
  headApplyTime: string;
  rows: RequisitionPreviewRow[];
}

const parentSync = useInvDocParentSyncSession<RequisitionDraftSnap>();

/** 侧栏切单：缓存/恢复本地草稿，避免已落库明细污染新建单 */
async function syncFromParentContext() {
  await parentSync.syncFromParentContext(
    { isDraftOrder: Boolean(props.isDraftOrder), parentBusNo: props.busNo },
    {
      getLocalPersistedBusNo: () => headerBusNo.value,
      captureDraft: () => ({
        sourceLocationId: sourceLocationId.value,
        purposeLocationId: purposeLocationId.value,
        practitionerId: practitionerId.value,
        headItemTableType: headItemTableType.value,
        headApplyTime: headApplyTime.value,
        rows: collectGridRows().map((r) => ({ ...r })),
      }),
      restoreDraft: async (snap) => {
        headerBusNo.value = undefined;
        detailStatusEnum.value = undefined;
        sourceLocationId.value = snap.sourceLocationId;
        purposeLocationId.value = snap.purposeLocationId;
        practitionerId.value = snap.practitionerId;
        headItemTableType.value = snap.headItemTableType;
        headApplyTime.value = snap.headApplyTime;
        requisitionGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
        patchRequisitionGridEditConfig();
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

watch(documentEditable, (editable) => {
  patchRequisitionGridEditConfig();
  void nextTick(() => {
    if (!editable) {
      requisitionGridEdit.resetFormMode();
      requisitionGridEdit.clearGridEdit();
    }
  });
});

/** 解析下拉选项展示名 */
function resolvePrintOptionLabel(options: ApiOptionItem[] | undefined, value: unknown): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = options?.find((item) => String(item.value ?? '') === key);
  return String(hit?.label ?? '').trim();
}

/** 明细行当前计量单位展示文案 */
function resolveRequisitionPrintUnitLabel(row: RequisitionPreviewRow): string {
  const kind = String(row.selectedBandKind ?? '').trim();
  const byKind = row.unitList?.find((item) => String(item.value ?? '') === kind);
  if (byKind) {
    return String(byKind.label ?? byKind.value_dictText ?? '').trim();
  }
  return String(row.unitCodeDictText ?? row.unitCode ?? '').trim();
}

/** 打印或预览请领单（计价流转模版） */
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

  const sourceName = resolvePrintOptionLabel(props.warehouseOptions, sourceLocationId.value);
  const destName = resolvePrintOptionLabel(props.pharmacyOptions, purposeLocationId.value);
  const itemTypeText = resolvePrintOptionLabel(
    normalizedItemTableTypeOptions.value,
    headItemTableType.value,
  );
  const documentTitle = buildInvDocumentTitle(destName || sourceName, '请领单');
  const applyTimeText = String(headApplyTime.value ?? '')
    .trim()
    .replace('T', ' ');

  const lineRows = productRows.map((row) => ({
    productBusNo: resolveInvPrintProductBusNo(row as unknown as Record<string, unknown>),
    productName: String(row.productName ?? '').trim(),
    specText: String(row.spec ?? '').trim(),
    manufacturerText: String(row.manufacturer ?? '').trim(),
    qty: row.itemQuantity,
    unitLabel: resolveRequisitionPrintUnitLabel(row),
    unitPrice: row.price,
    amount: row.totalPrice,
    lotNumber: String(row.lotNumber ?? '').trim(),
    expiryText: String(row.expirationDate ?? '').trim(),
  }));

  const preparedByName = resolveInvDocPreparedByName(
    props.queueCardRow as null | Record<string, unknown>,
  );
  const approvedByName = resolveInvDocApprovedByName(
    props.queueCardRow as null | Record<string, unknown>,
  );
  await printInvDocument({
    family: 'movement',
    source: {
      hospitalName: resolvePrintHospitalName(),
      documentTitle,
      docNo: busNo,
      applyTimeText,
      itemTypeText,
      sourceLocationText: sourceName,
      supplierText: '',
      linkDocText: '',
      destLocationText: destName,
      invoiceNoText: '',
      lineRows,
      preparedByName,
      approvedByName,
    },
    bizRef: { busNo, docKind: 'requisition', variant: props.variant },
    jobName: documentTitle,
    preview: payload?.preview === true,
  });
}
</script>

<template>
  <div class="requisition-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
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
        @mousedown="requisitionGridEdit.onSurfaceMouseDown"
      >
        <div :class="INV_DOC_HEAD_SECTION_CLASS">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <SectionTitle :title="headSectionTitle" />
            <div class="flex items-center gap-2">
              <span class="text-xs" style="color: var(--el-text-color-regular)">状态</span>
              <ElTag size="small" type="info" effect="plain">{{ headerStatusText || '—' }}</ElTag>
            </div>
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
              <ElFormItem label="源药库" prop="sourceLocationId">
                <ElSelect
                  v-model="sourceLocationId"
                  class="w-full"
                  filterable
                  clearable
                  placeholder="请选择"
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="w in warehouseOptions"
                    :key="String(w.value)"
                    :label="w.label"
                    :value="w.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem :label="purposeLocationLabel" prop="purposeLocationId">
                <ElSelect
                  v-model="purposeLocationId"
                  class="w-full"
                  filterable
                  clearable
                  placeholder="请选择"
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="p in pharmacyOptions"
                    :key="String(p.value)"
                    :label="p.label"
                    :value="p.value"
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
              <ElFormItem label="制单时间">
                <ElDatePicker
                  v-model="headApplyTime"
                  class="w-full"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  format="YYYY-MM-DD HH:mm:ss"
                  :disabled="!documentEditable"
                />
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <InvDocDetailGridShell
          :bind-grid-wrap="bindGridWrap"
          :on-grid-keydown="requisitionGridEdit.onGridKeydown"
        >
          <Grid class="min-h-0 flex-1">
            <template #toolbar-actions>
              <InvDocGridToolbar
                :disabled="!documentEditable || oneClickLoading"
                show-batch-add
                batch-add-label="一键请领"
                @add="addItem"
                @batch-add="startOneClickRequisition"
                @delete-selected="deleteItem('select')"
              />
            </template>
            <template #action="{ row }">
              <ElButton
                type="danger"
                text
                :disabled="!documentEditable"
                @click="deleteItem(row as RequisitionPreviewRow)"
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
</style>
