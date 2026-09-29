<!--
  药库进货单右侧主区：单头/明细 + useVbenVxeGrid；单据操作吸底。
-->
<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';
import type { VxeGridPropTypes } from '#/adapter/vxe-table';
import type {
  CreateInboundReceiptResponseDto,
  InboundReceiptLineDto,
  LinkablePurchaseOrderListRowDto,
} from '#/api/inventory/warehouse/inbound-receipt';
import type { ApiOptionItem } from '#/types/api-response';
import type {
  CatalogTraceUnitHint,
  InboundProductUnitBand,
} from '#/utils/inventory/inbound-product-unit-bands';

import type { FormInstance } from 'element-plus';

import { computed, nextTick, onBeforeUnmount, onDeactivated, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocAmountSummaryBar from '@inventory/components/inv-doc-amount-summary-bar.vue';
import InvDocDetailGridShell from '@inventory/components/inv-doc-detail-grid-shell.vue';
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

import { useFinanceApprovalViewSummary } from '#/composables/use-finance-approval-view-summary';
import { useInvDocDetailGrid } from '#/composables/use-inv-doc-detail-grid';
import { useInvDocGridRowOps } from '#/composables/use-inv-doc-grid-row-ops';
import { useInvDocParentSyncSession } from '#/composables/use-inv-doc-parent-sync-session';
import {
  agreeInboundReceiptApi,
  createInboundReceiptApi,
  getLinkablePurchaseOrderDetailApi,
  pageLinkablePurchaseOrdersApi,
  rejectInboundReceiptApi,
  removeInboundReceiptApi,
  submitInboundReceiptApprovalApi,
  updateInboundReceiptApi,
  withdrawInboundReceiptApprovalApi,
} from '#/api/inventory/warehouse/inbound-receipt';
import SectionTitle from '#/components/layouts/section-title.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { hasNonEmptyBusNo } from '#/utils/inventory/inventory-doc-restrictions';
import PurchaseInboundBatchAddDialog from '#/views/inventory/warehouse/purchase-inbound/purchase-inbound-batch-add-dialog.vue';
import {
  formatInventoryLineTotal,
  formatPurchaseMoneyDisplay,
  inventoryLineTotalNumber,
} from '#/utils/format/money-scale';
import {
  canConvertDisplayQtyToSplitForBands,
  defaultInboundUnitBandKind,
  findInboundUnitBandByKind,
  hydrateInboundDetailQtyFromPersisted,
  inboundUnitBandSelectOptions,
  normalizeInboundProductUnitBands,
  persistDisplayQtyAsSplitForBands,
  resolveInboundLinePersistUnitCodeForBands,
  handleInventoryUnitBandChanged,
  warehouseNonInboundDefaultBandKind,
  warehouseNonInboundResolveDisplayUnitPrice,
} from '#/utils/inventory/inbound-product-unit-bands';
import { inboundReceiptPickMedicineCell } from '#/utils/inventory/inbound-receipt-product-pick-list';
import {
  buildInboundReceiptDocActionFlags,
  inboundReceiptStatusLabel,
} from '#/utils/inventory/inbound-receipt-status';
import {
  inventoryPickNumberField,
  inventoryPickStringField,
} from '#/utils/inventory/inventory-product-pick-list';
import {
  applyInventoryRowCategoryCode,
  isPresentItemId,
  scheduleInventoryProductPickFocus,
} from '#/utils/inventory/inventory-doc-grid-common';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  INV_ALL_WAREHOUSES_LABEL,
  INV_ITEM_TABLE_TYPE_DEVICE,
  isSpecificInventoryLocationId,
  resolveFirstRealLocationIdFromInitOptions,
  resolveInvDocApprovedByName,
  resolveInvDocPreparedByName,
  resolveInvDocPrintApplyTimeText,
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
  clearInvDocDraftCache,
  clearInvDocDraftCacheAfterSave,
  INV_DOC_DRAFT_SCENE,
  mergeInvDocTraceIntoRows,
} from '#/utils/inventory/inv-doc-draft-cache';
import {
  applyMsfxUpstreamToInboundApi,
  getMsfxUpstreamImportByHisBusNoApi,
} from '#/api/msfx/inbound-upstream';
import { getMsfxSupplierMapBySupplierIdApi, pageMsfxSupplierMapApi } from '#/api/msfx/supplier-map';
import { uploadAndExpandInboundSmallCodesApi } from '#/api/msfx/upload';
import { MSFX_HIS_SCENE } from '#/msfx/constants';
import { isMsfxUiEnabled } from '#/msfx/is-msfx-ui-enabled';
import { assignMsfxTraceCodesToRows } from '#/msfx/utils/assign-trace-codes';
import MsfxUploadStatusBar from '#/msfx/components/msfx-upload-status-bar.vue';
import MsfxUpstreamPullDialog from '#/msfx/components/msfx-upstream-pull-dialog.vue';
import MsfxUpstreamGenerateConfirmDialog from '#/msfx/components/msfx-upstream-generate-confirm-dialog.vue';
import type { MsfxGenerateConfirmLine } from '#/msfx/utils/generate-confirm';
import {
  countMsfxWriteableTraceCodes,
  isMsfxQtyCodeMismatch,
  resolveMsfxExpectedTraceCount,
  resolveMsfxWriteableTraceCodes,
} from '#/msfx/utils/generate-confirm';
import type { MsfxUpstreamBillRowDto, MsfxUpstreamTraceCodeItem } from '#/api/msfx/types';
import {
  buildInvDocumentTitle,
  printInvDocument,
  resolveInvPrintProductBusNo,
} from '#/utils/print/print-inv-document';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

interface Props {
  /** 侧栏当前单据行（制单人/审批人与队列卡片同口径） */
  queueCardRow?: null | Record<string, unknown>;
  /** 当前选中单据号（来自左侧列表） */
  busNo?: string;
  /** 当前为新增草稿（无后端单号） */
  isDraftOrder?: boolean;
  /** 侧栏草稿稳定 id（_clientDraftId），用于 localStorage 缓存隔离 */
  clientDraftId?: string;
  /** 详情拉取后明细行（后端字段，与 index tableData 同源） */
  inboundDetailRows?: Record<string, unknown>[];
  /** IF-WHS-IN-01 入库项目类型选项 */
  itemTableTypeOptions?: ApiOptionItem[];
  /** 父级递增时：整页空态 + 刷新动效（如新增草稿） */
  resetKey?: number;
  /** 侧栏/单头制单时间（index tableForm.applyTime），写入明细 occurrenceTime */
  applyTime?: string;
  /** init 供应商选项 */
  supplierOptions?: ApiOptionItem[];
  /** init 药库位置（与侧栏 cabinet 同构 id/name） */
  warehouseOptions?: Record<string, unknown>[];
  /** 是否允许改单头/明细（待审核、驳回、草稿为 true） */
  documentEditable?: boolean;
  /** IF-WHS-IN-04/05 经手人 practitionerId（医务人员主键），与 CreateInboundReceiptRequest 一致 */
  practitionerId?: number | string;
  /** 未选中任何单据时展示欢迎空态（引导空白新增或从订货单拉取） */
  showWelcomeEmpty?: boolean;
  /** init 制单人/经手人选项（关联弹窗制单人列解析） */
  practitionerOptions?: ApiOptionItem[];
}

defineOptions({ name: 'PurchaseInboundMainPane' });

const props = withDefaults(defineProps<Props>(), {
  queueCardRow: null,
  busNo: '',
  isDraftOrder: false,
  clientDraftId: '',
  inboundDetailRows: () => [],
  itemTableTypeOptions: () => [],
  resetKey: 0,
  applyTime: '',
  supplierOptions: () => [],
  warehouseOptions: () => [],
  documentEditable: true,
  practitionerId: undefined,
  showWelcomeEmpty: false,
  practitionerOptions: () => [],
});

const emit = defineEmits<{
  createBlank: [];
  createFromUpstream: [row: LinkablePurchaseOrderListRowDto];
  documentChanged: [{ busNo?: string }];
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

/** 财务审批查看：页脚合计与列表金额列同口径 */
const { showFinanceApprovalSummary, financeApprovalSummaryItems } = useFinanceApprovalViewSummary();

interface HeadFormModel {
  busNo: string;
  originalBusNo: string;
  supplierId: string;
  applyTime: string;
  warehouseId: number | string;
  itemTableType: string;
  /** 采购发票号（单头填写，保存时写入同单每一行） */
  invoiceNo: string;
}

function defaultHeadForm(): HeadFormModel {
  return {
    busNo: '',
    originalBusNo: '',
    supplierId: '',
    applyTime: '',
    warehouseId: '',
    itemTableType: '',
    invoiceNo: '',
  };
}

function firstItemTableTypeValue(): string {
  const o = props.itemTableTypeOptions?.[0];
  return o?.value !== undefined && o?.value !== null ? String(o.value) : '';
}

function nowIsoLocal(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/** 单头制单时间 → 明细 occurrenceTime / 提交 applyClock（与 index applyTime 同源） */
function resolveInboundApplyOccurrenceTime(): string {
  const fromHead = String(headForm.value.applyTime ?? '').trim();
  const fromParent = String(props.applyTime ?? '').trim();
  const fromDetail = String(
    (props.inboundDetailRows?.[0] as Record<string, unknown> | undefined)?.applyTime ?? '',
  ).trim();
  const t = fromHead || fromParent || fromDetail;
  if (t === '') return nowIsoLocal();
  return t.includes('T') ? t : t.replace(' ', 'T');
}

function currentPersistedBusNo(): string {
  return String(props.busNo ?? headForm.value.busNo ?? '').trim();
}

function warehouseOptionValue(row: Record<string, unknown>): number | string {
  const v = row.id ?? row.value ?? row.locationId;
  if (typeof v === 'number' || typeof v === 'string') return v;
  return String(v ?? '');
}

/** 单头库房下拉：排除侧栏「全部库房」占位（id=0），进货必须落到具体药库。 */
const headWarehouseOptions = computed(() =>
  (props.warehouseOptions ?? []).filter((row) =>
    isSpecificInventoryLocationId(row.id ?? row.value),
  ),
);

/** 选品接口药库门禁：与单头库房无关，取 init 返回的首个真实药库位满足后端 purposeLocationId 必填。 */
function resolveInboundReceiptPickGateLocationId(): number | string | undefined {
  return resolveFirstRealLocationIdFromInitOptions(props.warehouseOptions ?? []);
}

function resolveInboundItemTableTypeLabel(tableName?: string): string {
  const key = String(tableName ?? '').trim();
  if (!key) return '项目';
  const hit = props.itemTableTypeOptions?.find((o) => String(o.value ?? '') === key);
  return String(hit?.label ?? key).trim() || '项目';
}

/** 选品前：单头项目类型必填，且与已有明细、所选行 itemTable 一致（一张单仅药品或仅耗材）。 */
function validateInboundPickItemTable(pick: Record<string, unknown>): boolean {
  const docType = String(headForm.value.itemTableType ?? '').trim();
  if (!docType) {
    ElMessage.warning('请先在单头选择项目类型');
    return false;
  }
  const pickTable = inventoryPickStringField(pick, 'productTableName');
  if (pickTable && pickTable !== docType) {
    ElMessage.warning(
      `所选项目属于「${resolveInboundItemTableTypeLabel(pickTable)}」，与单头「${resolveInboundItemTableTypeLabel(docType)}」不一致`,
    );
    return false;
  }
  const existingRows = collectGridRows().filter((r) => isPresentItemId(r.itemId));
  if (existingRows.length > 0) {
    const lockedTable = String(existingRows[0]?.itemTable ?? '').trim();
    if (lockedTable && pickTable && lockedTable !== pickTable) {
      ElMessage.warning('一张进货单只能包含同一项目类型（药品或耗材）');
      return false;
    }
  }
  return true;
}

/** 保存前：明细行 itemTable 须与单头一致，且全单只有一种类型。 */
function validateDocumentItemTableConsistency(rows: InboundPreviewRow[]): boolean {
  const docType = String(headForm.value.itemTableType ?? '').trim();
  if (!docType) {
    ElMessage.warning('请选择项目类型');
    return false;
  }
  const productRows = rows.filter((r) => isPresentItemId(r.itemId));
  const itemTables = new Set(
    productRows.map((r) => String(r.itemTable ?? '').trim()).filter((t) => t !== ''),
  );
  if (itemTables.size > 1) {
    ElMessage.error('一张进货单只能包含同一项目类型（药品或耗材）');
    return false;
  }
  if (itemTables.size === 1 && !itemTables.has(docType)) {
    ElMessage.error('明细项目类型与单头项目类型不一致');
    return false;
  }
  return true;
}

const headForm = ref<HeadFormModel>(defaultHeadForm());
const headFormRef = ref<FormInstance>();

const headFormRules = computed(() => ({
  ...buildInventoryLocationHeadFormRules({
    warehouseId: '库房',
  }),
  // 发票号非必填；空则后端/进销存上报占位 0（09 §4.5e）
}));

const hasLinkedUpstream = computed(() => {
  if (hasNonEmptyBusNo(headForm.value.originalBusNo)) return true;
  const h = (props.inboundDetailRows?.[0] as Record<string, unknown> | undefined)?.originalBusNo;
  return hasNonEmptyBusNo(h as string | undefined);
});

/** 明细可编辑：可手工选品，不强制关联订货单 */
const linesGridEditable = computed(() => props.documentEditable);

/** 单头「从订货单选择」：非草稿可先选后建；草稿可编辑时可更换关联 */
const canEditOriginalBusNo = computed(() => !props.isDraftOrder || props.documentEditable);

/** 保存/保存并提交进行中：置灰防双击并发建单或软删重插出重复明细 */
const saving = ref(false);

const docActionFlags = computed(() => {
  const base = buildInboundReceiptDocActionFlags({
    documentEditable: props.documentEditable,
    hasPersistedBusNo: Boolean(String(props.busNo ?? headForm.value.busNo ?? '').trim()),
    isDraft: props.isDraftOrder,
    statusRaw: (props.inboundDetailRows?.[0] as Record<string, unknown> | undefined)?.statusEnum,
    busy: saving.value,
  });
  const hasPersistedBusNo = Boolean(String(props.busNo ?? headForm.value.busNo ?? '').trim());
  return {
    ...base,
    // 已落库单据才可打印
    printDisabled: saving.value || !hasPersistedBusNo,
  };
});

const linkPoVisible = ref(false);
const linkPoLoading = ref(false);
const linkPoQueryParams = ref<{
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
const linkPoTotal = ref(0);
const linkPoRecords = ref<LinkablePurchaseOrderListRowDto[]>([]);

const linkPoWarehouseOptions = computed(() => {
  const rows = (props.warehouseOptions ?? [])
    .filter((row) => isSpecificInventoryLocationId(row.id ?? row.value))
    .map((row) => ({
      label: String(row.name ?? row.label ?? ''),
      value: warehouseOptionValue(row),
    }));
  return [{ label: INV_ALL_WAREHOUSES_LABEL, value: INV_ALL_LOCATIONS_FILTER_ID }, ...rows];
});

function resolveLinkPoPurposeLocationId(): number | string {
  const wh = resolveSpecificInventoryLocationId(linkPoQueryParams.value.warehouseId);
  if (wh) return wh;
  return INV_ALL_LOCATIONS_FILTER_ID;
}

function normalizeLinkPoWarehouseId(raw: unknown): number | string {
  if (Array.isArray(raw)) {
    const first = raw[0];
    if (first === undefined || first === null) return INV_ALL_LOCATIONS_FILTER_ID;
    return first as number | string;
  }
  if (raw === undefined || raw === null || raw === '') return INV_ALL_LOCATIONS_FILTER_ID;
  return raw as number | string;
}

function handleLinkPoWarehouseChange(value: unknown) {
  linkPoQueryParams.value.warehouseId = normalizeLinkPoWarehouseId(value);
  linkPoQueryParams.value.pageNo = 1;
  void fetchLinkablePurchaseOrders();
}

const linkPoFormItems = computed<FormItemConfig[]>(() => [
  {
    label: '库房',
    prop: 'warehouseId',
    type: 'select',
    placeholder: '全部库房',
    filterable: true,
    clearable: false,
    multiple: false,
    options: linkPoWarehouseOptions.value,
    onChange: handleLinkPoWarehouseChange,
  },
  {
    label: '关键字',
    prop: 'keyword',
    type: 'input',
    placeholder: '订货单号/关键字',
  },
]);

function resolveLinkPoSupplierLabel(row: LinkablePurchaseOrderListRowDto): string {
  const name = String(row.supplierName ?? '').trim();
  if (name) return name;
  const supplierId = String(row.supplierId ?? '').trim();
  if (!supplierId) return '';
  const hit = props.supplierOptions.find((item) => String(item.value ?? '') === supplierId);
  return String(hit?.label ?? '').trim();
}

const linkPoTableColumns = computed<TableColumn[]>(() =>
  withInvDocLinkPickerEnrichedColumns([
    { type: 'index', label: '序号', width: 58, align: 'center' },
    { prop: 'busNo', label: '订货单号', minWidth: 160, showOverflowTooltip: true },
    {
      prop: 'supplierName',
      label: '供应商',
      width: 160,
      showOverflowTooltip: true,
      slot: 'supplierName',
    },
    { prop: 'applyTime', label: '制单时间', minWidth: 160, slot: 'applyTime' },
    { label: '操作', width: 88, align: 'center', fixed: 'right', slot: 'rowActions' },
  ]),
);

const inboundHeaderStatusText = computed(() => {
  if (props.isDraftOrder) return '编辑中';
  return inboundReceiptStatusLabel(
    (props.inboundDetailRows?.[0] as Record<string, unknown> | undefined)?.statusEnum,
  );
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

function applyEmptyState() {
  headForm.value = defaultHeadForm();
  headForm.value.busNo = props.busNo ?? '';
  headForm.value.applyTime = formatDateTime(new Date());
  headForm.value.itemTableType = firstItemTableTypeValue();
  clearInventoryHeadFormValidation(headFormRef.value);
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchInboundGridEditConfig();
  });
}

function syncHeadFromDetailRows(rows: Record<string, unknown>[]) {
  const h = rows[0];
  if (!h) return;
  headForm.value.busNo = String(h.busNo ?? props.busNo ?? '');
  headForm.value.originalBusNo = String(h.originalBusNo ?? '').trim();
  headForm.value.supplierId =
    h.supplierId !== undefined && h.supplierId !== null && String(h.supplierId) !== ''
      ? String(h.supplierId)
      : '';
  headForm.value.applyTime =
    h.applyTime !== undefined && h.applyTime !== null && String(h.applyTime) !== ''
      ? String(h.applyTime)
      : formatDateTime(new Date());
  const pl = h.purposeLocationId ?? h.locationId;
  headForm.value.warehouseId =
    pl !== undefined && pl !== null && pl !== '' ? (pl as number | string) : '';
  const firstItemTable = rows
    .map((r) => r.itemTable)
    .find((t) => t !== undefined && t !== null && t !== '');
  headForm.value.itemTableType =
    firstItemTable !== undefined && firstItemTable !== null && String(firstItemTable) !== ''
      ? String(firstItemTable)
      : firstItemTableTypeValue();
  // 发票号与 busNo 同口径：同号多行共享，取首行回显
  headForm.value.invoiceNo =
    h.invoiceNo !== undefined && h.invoiceNo !== null && String(h.invoiceNo).trim() !== ''
      ? String(h.invoiceNo).trim()
      : '';
}

/** 接口 LocalDateTime → 日期选择器 YYYY-MM-DD */
function parseInboundApiDateToPickerDate(v: unknown): string {
  const raw = String(v ?? '').trim();
  if (!raw) return '';
  const m = raw.match(/^(\d{4}-\d{2}-\d{2})/);
  return m?.[1] ?? raw.slice(0, 10);
}

/** 详情行 startTime/endTime → 表格 prodExpiry 区间 */
function mapStartEndToProdExpiry(startTime: unknown, endTime: unknown): [string, string] | null {
  const start = parseInboundApiDateToPickerDate(startTime);
  const end = parseInboundApiDateToPickerDate(endTime);
  if (!start && !end) return null;
  if (start && end) return [start, end];
  if (start) return [start, start];
  return [end, end];
}

/** 日期选择器值 → 提交用 LocalDateTime（与 occurrenceTime 一致带 T） */
function normalizeInboundLineDateTimeForApi(dateStr: string): string | undefined {
  const raw = String(dateStr ?? '').trim();
  if (!raw) return undefined;
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    return `${raw}T00:00:00`;
  }
  return raw.includes('T') ? raw : raw.replace(' ', 'T');
}

/** 表格 prodExpiry → 请求体 startTime/endTime */
function mapProdExpiryToStartEnd(prodExpiry: [string, string] | null | undefined): {
  endTime?: string;
  startTime?: string;
} {
  if (!Array.isArray(prodExpiry) || prodExpiry.length < 2) {
    return {};
  }
  const startTime = normalizeInboundLineDateTimeForApi(prodExpiry[0] ?? '');
  const endTime = normalizeInboundLineDateTimeForApi(prodExpiry[1] ?? '');
  return {
    ...(endTime ? { endTime } : {}),
    ...(startTime ? { startTime } : {}),
  };
}

/** 详情行单位展示名（IF-WHS-IN-03 unitCodeDictText） */
function resolveInboundDetailUnitCodeDictText(r: Record<string, unknown>): string {
  return String(r.unitCodeDictText ?? r.unitCode_dictText ?? '').trim();
}

/** 无多计量带或带内缺 unitName 时，用 unitCodeDictText 补全 unitList 展示文案 */
function applyInboundDetailUnitListDictText(
  unitList: { label: string; value: string }[],
  opts: {
    hasBands: boolean;
    selectedBandKind: string;
    unitBands: InboundProductUnitBand[];
    unitCodeDictText: string;
    unitCodeStr: string;
  },
): { label: string; value: string }[] {
  const dictText = opts.unitCodeDictText;
  if (!dictText) return unitList;
  if (!opts.hasBands) {
    return [{ value: 'LEGACY', label: dictText || opts.unitCodeStr || '单位' }];
  }
  const k = String(opts.selectedBandKind ?? '')
    .trim()
    .toUpperCase();
  const band = findInboundUnitBandByKind(opts.unitBands, k);
  const code = String(band?.unitCode ?? opts.unitCodeStr).trim();
  return unitList.map((o) => {
    if (o.value !== k) return o;
    const lbl = String(o.label ?? '').trim();
    if (!lbl || lbl === k || lbl === code) {
      return { ...o, label: dictText };
    }
    return o;
  });
}

function mapDetailRowToGrid(r: Record<string, unknown>): InboundPreviewRow {
  const itemIdRaw = r.itemId;
  const idStr =
    r.id !== undefined && r.id !== null && String(r.id) !== ''
      ? String(r.id)
      : itemIdRaw !== undefined && itemIdRaw !== null
        ? `item-${String(itemIdRaw)}`
        : `row-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
  const name = String(r.productName ?? r.itemName ?? '');
  const qty = r.itemQuantity;
  const price = r.price;
  const total = r.totalPrice;
  const hasItemId =
    itemIdRaw !== undefined &&
    itemIdRaw !== null &&
    !(typeof itemIdRaw === 'string' && itemIdRaw.trim() === '');
  const catalog = r.productCatalog as Record<string, unknown> | undefined;
  const unitBands = normalizeInboundProductUnitBands(
    r.unitBands ?? catalog?.unitBands,
    r.unitBandsJson ?? catalog?.unitBandsJson,
  );
  const unitCodeStr = String(r.unitCode ?? '');
  const unitCodeDictText = resolveInboundDetailUnitCodeDictText(r);
  const hasBands = unitBands.length > 0;
  const rawQty = qty === undefined || qty === null ? 0 : Number(qty);
  let purchaseQty = rawQty;
  let purchaseUnit = unitCodeStr;
  let selectedBandKind: string = 'LEGACY';
  // 回显：itemQuantity 为 SPLIT；unitCode 为保存时单位；仅数量做 SPLIT→录入单位换算
  if (hasBands) {
    const hydrated = hydrateInboundDetailQtyFromPersisted({
      bands: unitBands,
      persistedItemQuantity: rawQty,
      persistedUnitCode: unitCodeStr,
    });
    purchaseQty = hydrated.displayQty;
    purchaseUnit = hydrated.unitCode;
    selectedBandKind = hydrated.bandKind;
  }
  const unitList = applyInboundDetailUnitListDictText(
    hasBands
      ? inboundUnitBandSelectOptions(unitBands)
      : [{ value: 'LEGACY', label: unitCodeStr.trim() === '' ? '单位' : unitCodeStr }],
    {
      hasBands,
      selectedBandKind,
      unitBands,
      unitCodeDictText,
      unitCodeStr,
    },
  );
  const unitPrice = price === undefined || price === null ? 0 : Number(price);
  // 目录游离追溯单位：详情 productCatalog / 选品顶栏字段；保留 categoryCode 供中药进价三位展示
  const productBusNo = String(catalog?.productBusNo ?? '').trim();
  const productCatalog: CatalogTraceUnitHint & {
    unitBands?: InboundProductUnitBand[];
    categoryCode?: string;
    productBusNo?: string;
  } = {
    unitBands: hasBands ? unitBands : undefined,
    categoryCode: String(catalog?.categoryCode ?? r.categoryCode ?? '').trim() || undefined,
    productBusNo: productBusNo || undefined,
    traceUnitCode: String(catalog?.traceUnitCode ?? '').trim() || undefined,
    traceUnitRatio: (() => {
      const n = Number(catalog?.traceUnitRatio ?? r.traceUnitRatio);
      return Number.isFinite(n) && n > 0 ? n : undefined;
    })(),
  };
  const mapped: InboundPreviewRow = {
    id: idStr,
    medicinePick: name
      ? {
          id: hasItemId ? (itemIdRaw as number | string) : idStr,
          name,
          ...(productBusNo ? { busNo: productBusNo } : {}),
        }
      : undefined,
    itemTable: r.itemTable === undefined ? undefined : String(r.itemTable),
    itemId: hasItemId ? (itemIdRaw as number | string) : undefined,
    spec: String(r.specification ?? ''),
    manufacturer: String(r.manufacturer ?? ''),
    purchaseQty,
    purchaseUnit,
    unitBands: hasBands ? unitBands : undefined,
    productCatalog,
    selectedBandKind,
    unitList,
    __prevBandKind: selectedBandKind,
    unitPrice,
    totalAmount:
      total === undefined || total === null || total === ''
        ? ''
        : typeof total === 'number'
          ? formatPurchaseMoneyDisplay(total, productCatalog.categoryCode)
          : String(total),
    batchNo: String(r.lotNumber ?? ''),
    traceNo: String(r.traceNo ?? '').trim(),
    prodExpiry: mapStartEndToProdExpiry(r.startTime, r.endTime),
    remark: String(r.remake ?? ''),
  };
  applyInventoryRowCategoryCode(mapped as unknown as Record<string, unknown>, {
    productCatalog,
    categoryCode: productCatalog.categoryCode,
  });
  return mapped;
}

/** 编辑态：按数量 × 单价刷新合计（详情回显不调用，总价以后端为准） */
function recalcInboundLineTotal(row: InboundPreviewRow) {
  const qty = Number(row.purchaseQty);
  const unit = Number(row.unitPrice);
  row.totalAmount =
    Number.isFinite(qty) && Number.isFinite(unit) ? formatInventoryLineTotal(qty, unit) : '';
}

/** 切换采购单位带：数量按 ratioToSplit 换算到目标带 */
function handleInboundUnitBandChanged(row: InboundPreviewRow) {
  handleInventoryUnitBandChanged(row, {
    getQty: () => Number(row.purchaseQty ?? 0),
    setQty: (qty) => {
      row.purchaseQty = qty;
    },
    onAfterChanged: () => recalcInboundLineTotal(row),
  });
}

/** 选品行写入明细：itemId / 选品 id 与接口同源，不做 Number() */
function syncRowFromInboundMedicinePick(pick: Record<string, unknown>, row: InboundPreviewRow) {
  row.medicinePick =
    inboundReceiptPickMedicineCell(pick) ?? (pick as InboundPreviewRow['medicinePick']);
  row.itemTable = inventoryPickStringField(pick, 'productTableName');
  const defId = pick.productDefinitionId;
  row.itemId =
    defId === undefined || defId === null || String(defId).trim() === ''
      ? undefined
      : (defId as number | string);
  row.spec = inventoryPickStringField(pick, 'totalVolume');
  row.manufacturer = inventoryPickStringField(pick, 'manufacturer');
  applyInventoryRowCategoryCode(row as unknown as Record<string, unknown>, pick);
  const bands = normalizeInboundProductUnitBands(pick.unitBands);
  row.unitBands = bands.length > 0 ? bands : undefined;
  // 目录追溯单位：选品顶栏 / productCatalog（单据不再采集）
  const pickTraceCode = String(pick.traceUnitCode ?? '').trim();
  const pickTraceRatio = Number(pick.traceUnitRatio);
  const pickCategoryCode = String(pick.categoryCode ?? '').trim() || undefined;
  const pickProductBusNo = inventoryPickStringField(pick, 'productBusNo');
  row.productCatalog = {
    unitBands: bands.length > 0 ? bands : undefined,
    categoryCode: pickCategoryCode,
    productBusNo: pickProductBusNo || undefined,
    traceUnitCode: pickTraceCode || undefined,
    traceUnitRatio:
      Number.isFinite(pickTraceRatio) && pickTraceRatio > 0 ? pickTraceRatio : undefined,
  };
  if (bands.length > 0) {
    const kind = warehouseNonInboundDefaultBandKind(bands);
    row.selectedBandKind = kind;
    row.unitList = inboundUnitBandSelectOptions(bands);
    row.__prevBandKind = kind;
    const band = findInboundUnitBandByKind(bands, kind);
    const code = String(band?.unitCode ?? '').trim();
    if (code !== '') {
      row.purchaseUnit = code;
    }
    const resolved = warehouseNonInboundResolveDisplayUnitPrice({
      bands,
      displayBandKind: kind,
      legacyUnitPrice: inventoryPickNumberField(pick, 'purchasePrice'),
    });
    row.unitPrice = resolved ?? 0;
  } else {
    row.selectedBandKind = 'LEGACY';
    row.unitList = [{ value: 'LEGACY', label: '单位' }];
    row.__prevBandKind = 'LEGACY';
    row.purchaseUnit = inventoryPickStringField(pick, 'unitCode');
    row.unitPrice = inventoryPickNumberField(pick, 'purchasePrice');
  }
  recalcInboundLineTotal(row);
}

interface MedicinePickCell {
  id?: number | string;
  name?: string;
}

interface InboundPreviewRow {
  id: string;
  medicinePick?: MedicinePickCell | Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  /** 药品分类码；中药=4 时进价三位展示 */
  categoryCode?: string;
  spec: string;
  manufacturer: string;
  purchaseQty?: number;
  /** 提交用计量单位编码（与当前选中 band 一致） */
  purchaseUnit: string;
  /** 多计量带（选品/详情返回）；无则走 LEGACY 单行占位 */
  unitBands?: InboundProductUnitBand[];
  /** 选品/详情目录提示（含游离追溯单位，供扫码应扫数） */
  productCatalog?: CatalogTraceUnitHint & {
    unitBands?: InboundProductUnitBand[];
    categoryCode?: string;
  };
  /** 当前选中的 bandKind，默认 PURCHASE；无带时为 LEGACY */
  selectedBandKind?: string;
  /** UnitSelect 用，与 `vxe-table` 全局 UnitSelect 约定一致 */
  unitList?: { label?: string; value: string; value_dictText?: string }[];
  /** 上一次计量带，用于切换时换算数量 */
  __prevBandKind?: string;
  unitPrice: number;
  totalAmount: string;
  batchNo: string;
  /** 与后端 {@code InboundReceiptLineDto.traceNo} 一致 */
  traceNo: string;
  prodExpiry?: [string, string] | null;
  remark: string;
}

/** 明细行必填项是否已齐（与 buildLinesFromGrid 保存校验对齐） */
function isInboundRowComplete(row: InboundPreviewRow): boolean {
  if (!isPresentItemId(row.itemId)) return false;
  const qty = Number(row.purchaseQty);
  if (!Number.isFinite(qty) || qty <= 0) return false;
  if (!canConvertDisplayQtyToSplitForBands(row.unitBands ?? [], qty, row.selectedBandKind)) {
    return false;
  }
  const unitCode =
    resolveInboundLinePersistUnitCodeForBands({
      bands: row.unitBands ?? [],
      legacyUnitCode: row.purchaseUnit,
      selectedBandKind: row.selectedBandKind,
    }) ?? '';
  if (!unitCode) return false;
  if (!String(row.batchNo ?? '').trim()) return false;
  const { endTime, startTime } = mapProdExpiryToStartEnd(row.prodExpiry);
  return !!(startTime && endTime);
}

/** 选品后进入整行表单并聚焦数量 */
function handleInboundProductPick(row: InboundPreviewRow) {
  scheduleInventoryProductPickFocus(inboundGridEdit, row);
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
        listSource: 'inbound-receipt',
        placeholder: '请先选择项目类型后检索',
        query: async () => ({
          productTableName: headForm.value.itemTableType || undefined,
          purposeLocationId: resolveInboundReceiptPickGateLocationId(),
        }),
        onSelected: (product: Record<string, unknown>, row: InboundPreviewRow) => {
          if (!validateInboundPickItemTable(product)) return;
          syncRowFromInboundMedicinePick(product, row);
          handleInboundProductPick(row);
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
    title: '厂家/产地',
    minWidth: 120,
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
        qtyField: 'purchaseQty',
        unitField: 'selectedBandKind',
        onUnitChange: (row: InboundPreviewRow) => {
          handleInboundUnitBandChanged(row);
        },
      },
    },
  },
  {
    field: 'unitPrice',
    title: '单价',
    width: 120,
    align: 'right',
    headerAlign: 'center',
    editRender: {
      name: 'InventoryUnitPriceCell',
      props: {
        priceField: 'unitPrice',
        unitField: 'selectedBandKind',
      },
    },
  },
  {
    field: 'totalAmount',
    title: '总金额',
    width: 112,
    align: 'right',
    headerAlign: 'center',
    cellRender: {
      name: 'InventoryTotalAmountCell',
      props: {
        amountField: 'totalAmount',
      },
    },
  },
  {
    field: 'batchNo',
    title: '批号',
    width: 120,
    showOverflow: true,
    editRender: {
      name: 'InventoryPersistElInput',
      props: { maxlength: 64, clearable: true },
    },
  },
  {
    field: 'traceNo',
    title: '追溯码',
    minWidth: 140,
    // 64 盒小码逗号串约 1.3k+；512 会截断。与后端 TRACE_NO_WIDE_MAX_LENGTH 对齐
    editRender: { name: 'InventoryTraceNoCell', props: { maxlength: 50000, clearable: true } },
  },
  {
    field: 'prodExpiry',
    title: '生产日期~有效期至',
    minWidth: 240,
    showOverflow: true,
    editRender: {
      name: 'ElDatePicker',
      props: {
        type: 'daterange',
        valueFormat: 'YYYY-MM-DD',
        format: 'YYYY-MM-DD',
        rangeSeparator: '至',
        startPlaceholder: '生产日期',
        endPlaceholder: '有效期至',
        unlinkPanels: true,
        clearable: true,
        class: '!w-full min-w-[220px]',
      },
    },
  },
  {
    field: 'remark',
    title: '备注',
    minWidth: 120,
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

const INBOUND_EDITABLE_COLUMN_FIELDS = new Set([
  'medicinePick',
  '_qtyUnit',
  'batchNo',
  'traceNo',
  'prodExpiry',
  'remark',
  'unitPrice',
]);

const INBOUND_EDIT_FIELD_ORDER = [
  'medicinePick',
  '_qtyUnit',
  'unitPrice',
  'batchNo',
  'traceNo',
  'prodExpiry',
  'remark',
] as const;

type InboundEditField = (typeof INBOUND_EDIT_FIELD_ORDER)[number];

/** 支持方向键上下换行的列（不含项目名称，避免误聚焦选品下拉） */
const INBOUND_VERTICAL_NAV_FIELDS = new Set<InboundEditField>([
  '_qtyUnit',
  'batchNo',
  'traceNo',
  'prodExpiry',
  'remark',
  'unitPrice',
]);

const {
  Grid,
  gridApi,
  bindGridWrap,
  gridEdit: inboundGridEdit,
  collectGridRows,
  patchGridEditConfig: patchInboundGridEditConfig,
} = useInvDocDetailGrid<InboundPreviewRow>({
  columns,
  documentEditable: () => linesGridEditable.value,
  beforeEditMethod: () => linesGridEditable.value,
  onEditClosed: (row) => recalcInboundLineTotal(row),
  config: {
    rowKey: (row) => row.id,
    productField: 'medicinePick',
    editFieldOrder: INBOUND_EDIT_FIELD_ORDER,
    editableFields: INBOUND_EDITABLE_COLUMN_FIELDS,
    verticalNavFields: INBOUND_VERTICAL_NAV_FIELDS,
    firstFillFieldAfterPick: '_qtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isInboundRowComplete,
  },
});

const {
  addItem: addGridItem,
  deleteItem,
  removeEmptyRows: removeEmptyInboundRows,
} = useInvDocGridRowOps({
  documentEditable: () => linesGridEditable.value,
  getGridApi: () => gridApi ?? null,
  gridEdit: inboundGridEdit,
  createEmptyRow: createEmptyInboundRow,
  hasProduct: (row) => isPresentItemId(row.itemId),
});

const traceScanBridgeRef = ref<InstanceType<typeof InvDocTraceScanBridge> | null>(null);

/** 打开批量追溯码扫码弹窗 */
function handleOpenTraceScan(): void {
  traceScanBridgeRef.value?.open(collectGridRows());
}

watch(
  () => props.itemTableTypeOptions,
  (opts) => {
    if (opts?.length && !headForm.value.itemTableType) {
      headForm.value.itemTableType = String(opts[0]?.value ?? '');
    }
  },
  { deep: true },
);

interface InboundDraftSnap {
  head: HeadFormModel;
  rows: InboundPreviewRow[];
}

const parentSync = useInvDocParentSyncSession<InboundDraftSnap>({
  scene: INV_DOC_DRAFT_SCENE.warehousePurchaseInbound,
  getDocKey: () => {
    // 未落库草稿用客户端 id；已落库编辑用 busNo
    const draftId = String(props.clientDraftId ?? '').trim();
    if (props.isDraftOrder && draftId) return draftId;
    return String(props.busNo ?? headForm.value.busNo ?? '').trim() || undefined;
  },
  getCardMeta: () => ({
    statusEnum_enumText: '编辑中',
    applyTime: headForm.value.applyTime,
    supplierId_dictText:
      props.supplierOptions.find((o) => String(o.value) === String(headForm.value.supplierId))
        ?.label ?? '',
  }),
});

/** 拍摄当前主区草稿快照（侧栏切单 / 落盘共用） */
function captureInboundDraftSnap(): InboundDraftSnap {
  return {
    head: {
      ...headForm.value,
      busNo: props.isDraftOrder ? '' : String(headForm.value.busNo ?? ''),
    },
    rows: collectGridRows().map((r) => ({ ...r })),
  };
}

/** 侧栏切单：缓存/恢复本地草稿，避免已落库明细污染新建单 */
async function syncFromParentContext() {
  await parentSync.syncFromParentContext(
    { isDraftOrder: Boolean(props.isDraftOrder), parentBusNo: props.busNo },
    {
      getLocalPersistedBusNo: () => {
        const headNo = String(headForm.value.busNo ?? '').trim();
        if (props.isDraftOrder) return headNo || undefined;
        return String(props.busNo ?? headNo).trim() || undefined;
      },
      captureDraft: captureInboundDraftSnap,
      restoreDraft: async (snap) => {
        headForm.value = {
          ...snap.head,
          busNo: props.isDraftOrder ? '' : String(snap.head.busNo ?? ''),
        };
        inboundGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
        patchInboundGridEditConfig();
      },
      resetForCreate: () => {
        inboundGridEdit.resetFormMode();
        applyEmptyState();
      },
      loadByBusNo: async (_busNo, gen) => {
        // 追溯码必须在 loadData 前合并进行；勿在 nextTick 后再补，否则会被空码详情冲掉
        applyPersistedDetailFromParent(gen);
      },
    },
  );
}

/** 切菜单 / KeepAlive 停用 / 扫码后把当前单写入 localStorage */
function flushInboundDraftPersist(): void {
  parentSync.flushPersist({
    captureDraft: captureInboundDraftSnap,
  });
}

/** 扫码弹窗写回明细后立即落盘 */
function handleTraceBindingsChanged(): void {
  // bridge 已就地改写行上的 traceNo；再 load 一次保证网格/collect 一致后再落盘
  const rows = collectGridRows();
  if (rows.length > 0) {
    gridApi?.grid?.loadData(rows);
  }
  flushInboundDraftPersist();
}

onBeforeUnmount(() => {
  flushInboundDraftPersist();
});

onDeactivated(() => {
  flushInboundDraftPersist();
});

watch(
  () => props.resetKey,
  () => {
    parentSync.discardDraftSession();
    inboundGridEdit.resetFormMode();
    applyEmptyState();
    triggerPaneFlash();
  },
);

watch(
  () => [props.busNo, props.inboundDetailRows, props.isDraftOrder, props.clientDraftId] as const,
  () => {
    void syncFromParentContext();
  },
  { deep: true, immediate: true },
);

/** 将父级已拉详情写入主区（含 loadGen 防异步串扰）；落盘缓存的追溯码在 loadData 前同步补回 */
function applyPersistedDetailFromParent(loadGen?: number) {
  if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
  if (!props.busNo) {
    inboundGridEdit.resetFormMode();
    applyEmptyState();
    return;
  }
  const rows = props.inboundDetailRows ?? [];
  if (rows.length === 0) {
    headForm.value.busNo = props.busNo;
    headForm.value.applyTime = formatDateTime(new Date());
    headForm.value.supplierId = '';
    headForm.value.warehouseId = '';
    headForm.value.itemTableType = firstItemTableTypeValue();
    headForm.value.invoiceNo = '';
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchInboundGridEditConfig();
    });
    return;
  }
  syncHeadFromDetailRows(rows);
  const busNo = String(props.busNo ?? '').trim();
  const cachedSnap = busNo ? (parentSync.readPersistByKey(busNo) as InboundDraftSnap | null) : null;
  const gridRows = mergeInvDocTraceIntoRows(
    rows.map((r) => mapDetailRowToGrid(r)),
    cachedSnap?.rows,
  );
  inboundGridEdit.resetFormMode();
  inboundGridEdit.clearGridEdit();
  void nextTick(() => {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    gridApi?.grid?.loadData(gridRows);
    patchInboundGridEditConfig();
  });
}

function pickApiErrMsg(e: unknown): string {
  if (e && typeof e === 'object' && 'response' in e) {
    const data = (e as { response?: { data?: { message?: string; msg?: string } } }).response?.data;
    if (data?.msg) return String(data.msg);
    if (data?.message) return String(data.message);
  }
  if (e instanceof Error) return e.message;
  return '保存失败';
}

/**
 * 仅响应用户改「项目类型」：详情重载/单头回填也会改 itemTableType，
 * 若用 watch 会在提交/撤回后误报「已有明细不能切换」。
 */
function handleItemTableTypeChange(newVal: string) {
  const next = String(newVal ?? '').trim();
  const productRows = collectGridRows().filter((r) => isPresentItemId(r.itemId));
  if (productRows.length === 0) return;
  const lockedTable = String(productRows[0]?.itemTable ?? '').trim();
  if (lockedTable && lockedTable !== next) {
    ElMessage.warning('已有明细行，不能切换项目类型；请先删除明细后再切换');
    headForm.value.itemTableType = lockedTable;
  }
}

function parseLineTotalPrice(row: InboundPreviewRow): number | undefined {
  const s = String(row.totalAmount ?? '').trim();
  if (s === '') {
    const qty = Number(row.purchaseQty);
    const up = Number(row.unitPrice);
    if (Number.isFinite(qty) && Number.isFinite(up)) return qty * up;
    return undefined;
  }
  const n = Number.parseFloat(s);
  return Number.isFinite(n) ? n : undefined;
}

/** 同一进货单内：itemTable + itemId + 批号 唯一。 */
function inboundItemLotDuplicateKey(
  itemTable: string,
  itemId: number | string,
  lotNumber: string,
): string {
  return `${itemTable}\u0001${String(itemId)}\u0001${lotNumber}`;
}

function resolveInboundRowProductName(row: InboundPreviewRow, rowIndex: number): string {
  const pick = row.medicinePick;
  const name =
    pick && typeof pick === 'object' && 'name' in pick
      ? String((pick as { name?: string }).name ?? '').trim()
      : '';
  return name || `第 ${rowIndex + 1} 行`;
}

function buildLinesFromGrid(): InboundReceiptLineDto[] | null {
  const rows = collectGridRows();
  for (const r of rows) {
    recalcInboundLineTotal(r);
  }
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一行明细');
    return null;
  }
  if (!validateDocumentItemTableConsistency(rows)) {
    return null;
  }
  const docType = String(headForm.value.itemTableType ?? '').trim();
  const lines: InboundReceiptLineDto[] = [];
  const occurrenceTime = resolveInboundApplyOccurrenceTime();
  const itemLotSeen = new Map<string, number>();
  for (const [i, row] of rows.entries()) {
    const r = row;
    const itemTable = String(r.itemTable ?? '').trim();
    const itemId = r.itemId;
    if (!itemTable || !isPresentItemId(itemId)) {
      ElMessage.error(`第 ${i + 1} 行请选择项目`);
      return null;
    }
    if (itemTable !== docType) {
      ElMessage.error(`第 ${i + 1} 行项目类型与单头不一致`);
      return null;
    }
    const qty = Number(r.purchaseQty);
    if (!Number.isFinite(qty) || qty <= 0) {
      ElMessage.error(`第 ${i + 1} 行数量须大于 0`);
      return null;
    }
    if (!canConvertDisplayQtyToSplitForBands(r.unitBands ?? [], qty, r.selectedBandKind)) {
      ElMessage.error(`第 ${i + 1} 行无法将数量换算为拆零单位，请检查计量带`);
      return null;
    }
    const persisted = persistDisplayQtyAsSplitForBands({
      bands: r.unitBands ?? [],
      displayQty: qty,
      legacyUnitCode: r.purchaseUnit,
      selectedBandKind: r.selectedBandKind,
    });
    const unitCode =
      resolveInboundLinePersistUnitCodeForBands({
        bands: r.unitBands ?? [],
        legacyUnitCode: r.purchaseUnit,
        selectedBandKind: r.selectedBandKind,
      }) ?? '';
    if (!unitCode) {
      ElMessage.error(`第 ${i + 1} 行请选择单位`);
      return null;
    }
    const lotNumber = String(r.batchNo ?? '').trim();
    if (!lotNumber) {
      ElMessage.error(`第 ${i + 1} 行批号必填`);
      return null;
    }
    const dupKey = inboundItemLotDuplicateKey(itemTable, itemId as number | string, lotNumber);
    const dupAt = itemLotSeen.get(dupKey);
    if (dupAt !== undefined) {
      const name = resolveInboundRowProductName(r, i);
      ElMessage.error(
        `「${name}」批号「${lotNumber}」与第 ${dupAt + 1} 行重复，同一项目不允许入库两条相同批号`,
      );
      return null;
    }
    itemLotSeen.set(dupKey, i);
    const price = Number(r.unitPrice);
    const { endTime, startTime } = mapProdExpiryToStartEnd(r.prodExpiry);
    // 空串也要显式传给后端，不能用 || undefined（否则改空会被当成「未传」落不掉）
    const traceNo = String(r.traceNo ?? '').trim();
    lines.push({
      itemTable,
      itemId: itemId as number | string,
      itemQuantity: persisted.itemQuantity,
      unitCode,
      lotNumber,
      traceNo,
      price: Number.isFinite(price) ? price : undefined,
      totalPrice: parseLineTotalPrice(r),
      occurrenceTime,
      endTime,
      startTime,
      remake: String(r.remark ?? '').trim() || undefined,
    });
  }
  return lines;
}

function coerceLocationId(v: unknown, label: string): number | string {
  if (v === undefined || v === null || v === '') {
    throw new Error(`${label}不能为空`);
  }
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  const s = String(v).trim();
  if (!s) throw new Error(`${label}不能为空`);
  if (/^-?\d+$/.test(s)) {
    const n = Number(s);
    if (Number.isSafeInteger(n)) return n;
    return s;
  }
  return s;
}

function optionalSupplierId(s: string): number | string | undefined {
  const t = String(s ?? '').trim();
  if (!t) return undefined;
  const n = Number(t);
  if (Number.isFinite(n) && Number.isSafeInteger(n)) return n;
  if (/^\d+$/.test(t)) return t;
  return undefined;
}

function validateHeadBeforeSave(): boolean {
  if (!String(headForm.value.supplierId ?? '').trim()) {
    ElMessage.warning('请选择供应商');
    return false;
  }
  if (!String(headForm.value.itemTableType ?? '').trim()) {
    ElMessage.warning('请选择项目类型');
    return false;
  }
  if (!isSpecificInventoryLocationId(headForm.value.warehouseId)) {
    warnInventoryLocationRequired('warehouse');
    return false;
  }
  // 发票号可选：未填由后端落库/上报占位 0，不在此拦截
  return true;
}

function coercePractitionerId(v: unknown, label: string): number | string {
  if (v === undefined || v === null || v === '') {
    throw new Error(`${label}不能为空`);
  }
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  const s = String(v).trim();
  if (!s) throw new Error(`${label}不能为空`);
  if (/^-?\d+$/.test(s)) {
    const n = Number(s);
    if (Number.isSafeInteger(n)) return n;
    return s;
  }
  return s;
}

function buildSaveCommonPayload(lines: InboundReceiptLineDto[]) {
  const warehouseId = coerceLocationId(headForm.value.warehouseId, '入库库房');
  const head = (props.inboundDetailRows ?? [])[0] as Record<string, unknown> | undefined;
  const sourceTypeEnum = String(head?.sourceTypeEnum ?? 'CABINET').trim() || 'CABINET';
  const purposeTypeEnum = String(head?.purposeTypeEnum ?? 'CABINET').trim() || 'CABINET';
  const categoryEnum = String(head?.categoryEnum ?? 'STOCK_SUPPLY').trim() || 'STOCK_SUPPLY';
  const originalRaw = headForm.value.originalBusNo || head?.originalBusNo;
  const originalBusNo =
    originalRaw !== undefined && originalRaw !== null && String(originalRaw).trim() !== ''
      ? String(originalRaw).trim()
      : undefined;
  return {
    originalBusNo,
    sourceLocationId: warehouseId,
    purposeLocationId: warehouseId,
    sourceTypeEnum,
    purposeTypeEnum,
    categoryEnum,
    supplierId: optionalSupplierId(headForm.value.supplierId),
    practitionerId: coercePractitionerId(props.practitionerId, '经手人'),
    // 单头发票号：后端写入同单每一行 invoice_no（与 busNo 共享机制）
    invoiceNo: String(headForm.value.invoiceNo ?? '').trim() || undefined,
    lines,
  };
}

async function persistInbound(submitApproval: boolean): Promise<string | undefined> {
  // 重入护栏：双击「保存/保存并提交」会并发 create 或软删重插，导致同单明细翻倍
  if (saving.value) return undefined;
  if (!props.isDraftOrder && !props.documentEditable) {
    ElMessage.warning('当前状态不允许修改');
    return undefined;
  }
  inboundGridEdit.clearGridEdit();
  if (!validateHeadBeforeSave()) {
    return undefined;
  }
  const lines = buildLinesFromGrid();
  if (!lines) return undefined;
  let common: ReturnType<typeof buildSaveCommonPayload>;
  try {
    common = buildSaveCommonPayload(lines);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '参数不完整');
    return undefined;
  }
  saving.value = true;
  try {
    let busNo: string | undefined;
    // 已有单号一律走修订（软删后重插），避免草稿标记未清时重复 create
    const existingBusNo = String(props.busNo ?? headForm.value.busNo ?? '').trim();
    if (existingBusNo) {
      const head = (props.inboundDetailRows ?? [])[0] as Record<string, unknown> | undefined;
      const rv = head?.rowVersion;
      await updateInboundReceiptApi({
        busNo: existingBusNo,
        ...common,
        rowVersion: rv !== undefined && rv !== null && String(rv) !== '' ? Number(rv) : undefined,
      });
      busNo = existingBusNo;
    } else if (props.isDraftOrder) {
      const res = (await createInboundReceiptApi(common)) as CreateInboundReceiptResponseDto & {
        data?: CreateInboundReceiptResponseDto;
      };
      busNo = (res.busNo ?? res.data?.busNo)?.trim();
      // 新建成功立即写入单头，后续「保存并提交」走 update 而非二次 create
      if (busNo) {
        headForm.value.busNo = busNo;
      }
    } else {
      ElMessage.error('缺少单据号');
      return undefined;
    }
    if (!busNo) {
      ElMessage.warning('保存成功但未返回单号');
      return undefined;
    }
    if (submitApproval) {
      await submitInboundReceiptApprovalApi({
        busNo,
        applyClock: headForm.value.applyTime?.trim() || undefined,
      });
    }
    return busNo;
  } catch (error) {
    ElMessage.error(pickApiErrMsg(error));
    return undefined;
  } finally {
    saving.value = false;
  }
}

async function handleSaveDoc() {
  const busNo = await persistInbound(false);
  if (!busNo) return;
  // 落库成功：草稿 key 清掉；整单快照迁到 busNo，切单/刷新可从 localStorage 补码
  const draftId = String(props.clientDraftId ?? '').trim();
  if (draftId) {
    clearInvDocDraftCache(INV_DOC_DRAFT_SCENE.warehousePurchaseInbound, draftId);
  }
  parentSync.bindActivePersistDocKey(busNo);
  parentSync.writePersistToKey(busNo, captureInboundDraftSnap());
  parentSync.clearDraftSnap({ clearActivePersist: false });
  ElMessage.success('保存成功');
  emit('documentSaved', { busNo });
  await flushPendingMsfxImportBind(busNo);
}

/** 生成行后若当时未落库，保存成功后补绑定上游导入账（支持多选） */
async function flushPendingMsfxImportBind(busNo: string) {
  const binds = pendingMsfxBinds.value;
  if (!binds.length) return;
  const no = String(busNo ?? '').trim();
  if (!no) return;
  try {
    await bindMsfxImportsToHisBusNo(binds, no);
    pendingMsfxBinds.value = [];
    msfxUploadBarRef.value?.reload();
    ElMessage.success(
      binds.length > 1 ? `已绑定 ${binds.length} 张上游出库单` : '已绑定上游出库单',
    );
  } catch (error) {
    ElMessage.warning(`进货单已保存，但上游单绑定失败：${pickApiErrMsg(error)}`);
  }
}

function toMsfxImportBinds(
  importIds: Array<number | string>,
  billCodeJoined?: string,
): Array<{ importId: string; billCode?: string }> {
  const ids = importIds.map((id) => String(id ?? '').trim()).filter(Boolean);
  const bills = String(billCodeJoined ?? '')
    .split('、')
    .map((s) => s.trim())
    .filter(Boolean);
  return ids.map((importId, i) => ({
    importId,
    billCode: bills.length === ids.length ? bills[i] : bills[0],
  }));
}

async function bindMsfxImportsToHisBusNo(
  binds: Array<{ importId: string; billCode?: string }>,
  hisBusNo: string,
) {
  for (const bind of binds) {
    await applyMsfxUpstreamToInboundApi({
      importId: bind.importId,
      hisBusNo,
      upstreamBillCode: bind.billCode,
    });
  }
}

async function handleSaveAndSubmitDoc() {
  const busNo = await persistInbound(true);
  if (!busNo) return;
  const draftId = String(props.clientDraftId ?? '').trim();
  if (draftId) {
    clearInvDocDraftCache(INV_DOC_DRAFT_SCENE.warehousePurchaseInbound, draftId);
  }
  parentSync.bindActivePersistDocKey(busNo);
  parentSync.writePersistToKey(busNo, captureInboundDraftSnap());
  parentSync.clearDraftSnap({ clearActivePersist: false });
  ElMessage.success('保存并提交审批成功');
  emit('documentSaved', { busNo });
  await flushPendingMsfxImportBind(busNo);
}

function applyClockForSubmit(): string | undefined {
  return resolveInboundApplyOccurrenceTime();
}

async function handleSubmitApproval() {
  if (props.isDraftOrder) {
    ElMessage.warning('请先保存单据');
    return;
  }
  const busNo = currentPersistedBusNo();
  if (!busNo) {
    ElMessage.warning('请先选择或保存单据');
    return;
  }
  try {
    await submitInboundReceiptApprovalApi({
      busNo,
      applyClock: applyClockForSubmit(),
    });
    ElMessage.success('已提交审批');
    emit('documentChanged', { busNo });
  } catch {
    /* 拦截器已提示 */
  }
}

async function handleWithdraw() {
  if (props.isDraftOrder) return;
  const busNo = currentPersistedBusNo();
  if (!busNo) return;
  try {
    await withdrawInboundReceiptApprovalApi({ busNo });
    ElMessage.success('已撤回');
    emit('documentChanged', { busNo });
  } catch {
    /* 拦截器已提示 */
  }
}

async function handleRemoveDoc() {
  const busNo = currentPersistedBusNo();
  if (props.isDraftOrder) {
    try {
      await ElMessageBox.confirm('确认放弃当前进货草稿？', '提示', { type: 'warning' });
    } catch {
      return;
    }
    clearInvDocDraftCacheAfterSave({
      scene: INV_DOC_DRAFT_SCENE.warehousePurchaseInbound,
      clientDraftId: props.clientDraftId,
    });
    parentSync.discardDraftSession();
    emit('documentRemoved');
    return;
  }
  if (!props.documentEditable) {
    ElMessage.warning('当前状态不允许删除');
    return;
  }
  if (!busNo) return;
  try {
    await ElMessageBox.confirm('确认删除整张进货单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await removeInboundReceiptApi({ busNo });
    clearInvDocDraftCacheAfterSave({
      scene: INV_DOC_DRAFT_SCENE.warehousePurchaseInbound,
      busNo,
    });
    parentSync.clearDraftSnap();
    ElMessage.success('已删除');
    emit('documentRemoved');
  } catch {
    /* 拦截器已提示 */
  }
}

async function handleApprove() {
  if (props.isDraftOrder) return;
  const busNo = currentPersistedBusNo();
  if (!busNo) return;
  try {
    await agreeInboundReceiptApi({
      busNo,
    });
    ElMessage.success('审批同意');
    emit('documentChanged', { busNo });
  } catch {
    /* 拦截器已提示 */
  }
}

async function handleReject() {
  if (props.isDraftOrder) return;
  const busNo = currentPersistedBusNo();
  if (!busNo) return;
  let rejectReason: string;
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '原因不能为空',
    });
    rejectReason = value;
  } catch (error) {
    if (error === 'cancel') return;
    return;
  }
  try {
    await rejectInboundReceiptApi({
      busNo,
      rejectReason,
    });
    ElMessage.success('已驳回');
    emit('documentChanged', { busNo });
  } catch {
    /* 拦截器已提示 */
  }
}

/** 解析下拉选项展示名 */
function resolvePrintOptionLabel(options: ApiOptionItem[] | undefined, value: unknown): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = options?.find((item) => String(item.value ?? '') === key);
  return String(hit?.label ?? '').trim();
}

/** 解析库房位置展示名 */
function resolvePrintWarehouseName(value: unknown): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = (props.warehouseOptions ?? []).find(
    (row) => String(warehouseOptionValue(row)) === key,
  );
  return String(hit?.name ?? hit?.label ?? '').trim();
}

/** 明细行当前计量单位展示文案（与网格 UnitSelect / selectedBandKind 一致） */
function resolveInboundPrintUnitLabel(row: InboundPreviewRow): string {
  const kind = String(row.selectedBandKind ?? '').trim();
  const byKind = row.unitList?.find((item) => String(item.value ?? '') === kind);
  if (byKind) {
    return String(byKind.label ?? byKind.value_dictText ?? '').trim();
  }
  const code = String(row.purchaseUnit ?? '').trim();
  const byCode = row.unitList?.find((item) => String(item.value ?? '') === code);
  return String(byCode?.label ?? byCode?.value_dictText ?? code).trim();
}

/** 打印或预览进货单（计价流转模版） */
async function handlePrint(payload?: { preview?: boolean }) {
  const busNo = currentPersistedBusNo();
  if (!busNo) {
    ElMessage.warning('请先保存单据后再打印');
    return;
  }
  const productRows = collectGridRows().filter((row) => isPresentItemId(row.itemId));
  if (productRows.length === 0) {
    ElMessage.warning('没有可打印的明细');
    return;
  }

  const warehouseName = resolvePrintWarehouseName(headForm.value.warehouseId);
  const supplierText = resolvePrintOptionLabel(props.supplierOptions, headForm.value.supplierId);
  const itemTypeText = resolvePrintOptionLabel(
    props.itemTableTypeOptions,
    headForm.value.itemTableType,
  );
  const isDeviceInbound =
    String(headForm.value.itemTableType ?? '').trim() === INV_ITEM_TABLE_TYPE_DEVICE;
  const documentTitle = buildInvDocumentTitle(warehouseName, '进货单');
  // 打印制单时间取侧栏单据实际审批时间，禁止用单头缺省「当前时刻」冒充
  const applyTimeText = resolveInvDocPrintApplyTimeText({
    queueCardRow: props.queueCardRow as null | Record<string, unknown>,
  });

  // 按当前网格展示值组装 PrintData（非 SPLIT 落库量；效期仅打有效期至）
  const lineRows = productRows.map((row) => {
    const { endTime } = mapProdExpiryToStartEnd(row.prodExpiry);
    return {
      productBusNo: resolveInvPrintProductBusNo(row as unknown as Record<string, unknown>),
      productName: resolveInboundRowProductName(row, 0),
      specText: String(row.spec ?? '').trim(),
      manufacturerText: String(row.manufacturer ?? '').trim(),
      qty: row.purchaseQty,
      unitLabel: resolveInboundPrintUnitLabel(row),
      unitPrice: row.unitPrice,
      amount: parseLineTotalPrice(row),
      lotNumber: String(row.batchNo ?? '').trim(),
      expiryDate: endTime,
    };
  });

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
      sourceLocationText: warehouseName,
      supplierText,
      linkDocText: String(headForm.value.originalBusNo ?? '').trim(),
      destLocationText: '',
      invoiceNoText: String(headForm.value.invoiceNo ?? '').trim(),
      lineRows,
      preparedByName,
      approvedByName,
      // 仅耗材进货：底部印负责人/验收入库/审核；药品进货仍为采购人/库管/验收人
      deviceInboundSignOff: isDeviceInbound,
    },
    bizRef: { busNo, docKind: 'purchase-inbound' },
    jobName: documentTitle,
    preview: payload?.preview === true,
  });
}

function createEmptyInboundRow(): InboundPreviewRow {
  return {
    id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    medicinePick: undefined,
    itemTable: undefined,
    itemId: undefined,
    spec: '',
    manufacturer: '',
    purchaseQty: undefined,
    purchaseUnit: '',
    selectedBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: '单位' }],
    __prevBandKind: 'LEGACY',
    unitPrice: 0,
    totalAmount: '',
    batchNo: '',
    traceNo: '',
    productCatalog: undefined,
    prodExpiry: null,
    remark: '',
  };
}

const batchDialogVisible = ref(false);

/** 码上放心：供应商企业 ent_id（102 上传必填；可由对照表 / 上游拉单带出） */
const msfxSupplierEntId = ref('');
const msfxSupplierEntName = ref('');
/** 院点 L0：国基等关闭码上放心页内入口 */
const msfxUiEnabled = isMsfxUiEnabled();
/** 打开拉单时：有明细才硬锁 preferred 上游企业 */
const msfxLockPreferredSupplier = ref(true);
const msfxUpstreamVisible = ref(false);
const msfxGenerateConfirmVisible = ref(false);
const msfxGenerateDrugRows = ref<MsfxUpstreamBillRowDto[]>([]);
const msfxGenerateConfirmRef = ref<InstanceType<typeof MsfxUpstreamGenerateConfirmDialog> | null>(
  null,
);
const msfxUploadBarRef = ref<InstanceType<typeof MsfxUploadStatusBar> | null>(null);

function onMsfxInboundCodesExpanded() {
  const busNo = currentPersistedBusNo();
  if (!busNo) return;
  emit('documentSaved', { busNo });
}

/** 确认窗尚未落库时，用上游导入账做 102 单号（不要求先选供应商/保存进货单）。 */
function resolvePullSmallCodesBusNo(): string {
  const existing = currentPersistedBusNo();
  if (existing) return existing;
  const importId = String(pendingMsfxApplyPayload.value?.importId ?? '').trim();
  if (importId) return `MSFX-IN-${importId}`;
  const bill = String(pendingMsfxApplyPayload.value?.billCode ?? '')
    .trim()
    .replace(/[^A-Za-z0-9_-]/g, '');
  if (bill) return `MSFX-IN-${bill.slice(-24)}`;
  return `MSFX-IN-${Date.now()}`;
}

/** 按拉单上游企业回填 HIS 供应商，方便稍后保存；失败不拦截拉取小码。 */
async function ensureHisSupplierFromUpstreamPull() {
  if (String(headForm.value.supplierId ?? '').trim()) return;
  const entId = String(pendingMsfxApplyPayload.value?.fromEntId ?? '').trim();
  const entName = String(pendingMsfxApplyPayload.value?.fromEntName ?? '').trim();
  if (entId) {
    try {
      const res = await pageMsfxSupplierMapApi({ pageNo: 1, pageSize: 200 });
      const hit = (res.data?.content ?? []).find((m) => String(m.msfxEntId ?? '').trim() === entId);
      if (
        hit?.supplierId !== undefined &&
        hit.supplierId !== null &&
        String(hit.supplierId).trim()
      ) {
        headForm.value.supplierId = String(hit.supplierId);
        msfxSupplierEntId.value = entId;
        const mappedName = String(hit.msfxEntName ?? '').trim();
        if (mappedName) msfxSupplierEntName.value = mappedName;
        return;
      }
    } catch {
      /* 对照查不到时再按下文名称匹配 */
    }
  }
  if (entName) {
    const opts = props.supplierOptions ?? [];
    const exact = opts.find((o) => String(o.label ?? '').trim() === entName);
    const fuzzy =
      exact ||
      opts.find((o) => {
        const lab = String(o.label ?? '').trim();
        return Boolean(lab && (lab.includes(entName) || entName.includes(lab)));
      });
    if (fuzzy?.value !== undefined && fuzzy.value !== null && String(fuzzy.value).trim()) {
      headForm.value.supplierId = String(fuzzy.value);
    }
  }
  if (entId) msfxSupplierEntId.value = entId;
  if (entName && !String(msfxSupplierEntName.value ?? '').trim()) {
    msfxSupplierEntName.value = entName;
  }
}

function resolveMsfxUploadSupplierName(): string {
  return (
    String(pendingMsfxApplyPayload.value?.fromEntName ?? '').trim() ||
    String(pendingMsfxApplyPayload.value?.drugRows?.[0]?.fromEntName ?? '').trim() ||
    String(msfxSupplierEntName.value ?? '').trim()
  );
}

function resolveMsfxUploadSupplierRef(): string {
  return String(pendingMsfxApplyPayload.value?.drugRows?.[0]?.fromRefUserId ?? '').trim();
}

async function handlePullSmallCodes(payload: {
  key: string;
  packCodes: string[];
  oneLevelOnly?: boolean;
  rank?: 'LARGE' | 'MEDIUM' | 'SMALL';
}) {
  const packCodes = (payload.packCodes ?? []).map((c) => String(c ?? '').trim()).filter(Boolean);
  if (packCodes.length === 0) {
    ElMessage.warning('本行没有可展开的中大码');
    return;
  }
  // 必须展成小码入库：先整行 102，再按批展开（每批 10，对齐平台 relation 上限）
  const MSFX_EXPAND_CHUNK_SIZE = 10;
  const batches = Math.ceil(packCodes.length / MSFX_EXPAND_CHUNK_SIZE);
  if (packCodes.length > 50) {
    try {
      await ElMessageBox.confirm(
        `本行有 ${packCodes.length} 个中大码，将上报采购入库(102)后分 ${batches} 批展开为小码，可能需要数分钟，请勿关闭页面。`,
        '分批展开小码',
        {
          confirmButtonText: '开始展开小码',
          cancelButtonText: '取消',
          type: 'info',
        },
      );
    } catch {
      return;
    }
  }
  await ensureHisSupplierFromUpstreamPull();
  const busNo = resolvePullSmallCodesBusNo();
  let supplierEntId =
    String(pendingMsfxApplyPayload.value?.fromEntId ?? '').trim() ||
    String(msfxSupplierEntId.value ?? '').trim();
  if (!supplierEntId) {
    try {
      const { value } = await ElMessageBox.prompt(
        '上报采购入库须填写码上放心上游企业 ID（须与出库单发货企业一致，不要填成其他企业）',
        '填写上游企业 ID',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          inputPlaceholder: 'ent_id',
        },
      );
      supplierEntId = String(value ?? '').trim();
      if (supplierEntId) {
        msfxSupplierEntId.value = supplierEntId;
      }
    } catch {
      return;
    }
  }
  if (!supplierEntId) {
    ElMessage.warning('未填写上游企业 ID，无法上报入库');
    return;
  }
  const oneLevelOnly = Boolean(payload.oneLevelOnly);
  const expandLedgerKey = String(payload.key ?? '').trim() || `expand-${Date.now()}`;
  msfxGenerateConfirmRef.value?.setRowPulling(payload.key, true, payload.rank);

  const chunks: string[][] = [];
  for (let i = 0; i < packCodes.length; i += MSFX_EXPAND_CHUNK_SIZE) {
    chunks.push(packCodes.slice(i, i + MSFX_EXPAND_CHUNK_SIZE));
  }
  const loading = ElMessage({
    message:
      chunks.length > 1
        ? `正在展开小码：0/${chunks.length} 批完成，请勿关闭…`
        : oneLevelOnly
          ? '正在上报入库并逐级展开，请勿关闭…'
          : '正在上报入库并拉取小码，请勿关闭…',
    type: 'info',
    duration: 0,
  });

  const mergedCodeItems: MsfxUpstreamTraceCodeItem[] = [];
  const mergedSmall: string[] = [];
  /** 已得到明确结果的源码（展开成功 / 已是最小）；未决批不纳入，便于保留原中码再拉 */
  const resolvedSourceCodes: string[] = [];
  let lastHint = '';
  let anyExpanded = false;
  let doneBatches = 0;

  const isAlreadySmallestItem = (item: MsfxUpstreamTraceCodeItem) => {
    const s = String(item.isSmallest ?? '')
      .trim()
      .toLowerCase();
    if (s === 'y' || s === '1' || s === 'true') return true;
    return String(item.codePackLevel ?? '').trim() === '1';
  };

  try {
    for (let i = 0; i < chunks.length; i++) {
      const chunk = chunks[i]!;
      try {
        const res = await uploadAndExpandInboundSmallCodesApi({
          busNo,
          hisSceneEnum: MSFX_HIS_SCENE.WAREHOUSE_PURCHASE_IN,
          supplierEntId,
          supplierEntName: resolveMsfxUploadSupplierName() || undefined,
          supplierRefEntId: resolveMsfxUploadSupplierRef() || undefined,
          billTime: String(headForm.value.applyTime ?? '').trim() || undefined,
          expandLedgerKey,
          // 首批带全量码做 102；后续批只展开本批，复用同一账本
          uploadTraceCodes: i === 0 ? packCodes : undefined,
          traceCodes: chunk,
          oneLevelOnly: oneLevelOnly || undefined,
        });
        const data = res.data ?? {};
        lastHint = String(data.hint ?? '').trim();
        const codeItems = data.codeItems ?? [];
        const smallCodes = (data.smallCodes ?? [])
          .map((c) => String(c ?? '').trim())
          .filter(Boolean);
        const incomingCodes = codeItems
          .map((item) => String(item.code ?? '').trim())
          .filter(Boolean);
        const expanded =
          incomingCodes.length > 0 &&
          (incomingCodes.length !== chunk.length || incomingCodes.some((c) => !chunk.includes(c)));
        // 本批全是最小码（误标中码）：后端回填 codeItems，须并入小码，不可当「未展开」丢弃
        const alreadySmallest =
          incomingCodes.length > 0 &&
          (lastHint.includes('已是最小包装') ||
            (incomingCodes.length === chunk.length &&
              incomingCodes.every((c) => chunk.includes(c)) &&
              codeItems.every((item) => isAlreadySmallestItem(item))));
        if (expanded || alreadySmallest) {
          anyExpanded = true;
          mergedCodeItems.push(...codeItems);
          resolvedSourceCodes.push(...chunk);
        } else if (smallCodes.length > chunk.length) {
          anyExpanded = true;
          mergedSmall.push(...smallCodes);
          resolvedSourceCodes.push(...chunk);
        } else {
          ElMessage.warning(
            `第 ${i + 1}/${chunks.length} 批尚未返回小码：${lastHint || '请稍后重试本批'}`,
          );
          // 未决中大码不计入小码，也不标 resolved，保留原码可再拉
        }
        doneBatches += 1;
      } catch (error) {
        const raw =
          error && typeof error === 'object' && 'message' in error
            ? String((error as { message?: string }).message ?? '')
            : '';
        if (/timeout/i.test(raw) || pickApiErrMsg(error).toLowerCase().includes('timeout')) {
          ElMessage.error(
            `第 ${i + 1}/${chunks.length} 批展开超时。已完成 ${doneBatches} 批，请再次点击「拉取」继续剩余码`,
          );
        } else {
          ElMessage.error(
            `第 ${i + 1}/${chunks.length} 批失败：${pickApiErrMsg(error) || '拉取小码失败'}`,
          );
        }
        break;
      }
    }

    msfxUploadBarRef.value?.reload();
    if (anyExpanded && mergedCodeItems.length > 0) {
      msfxGenerateConfirmRef.value?.applyExpandedCodes(
        payload.key,
        resolvedSourceCodes.length > 0 ? resolvedSourceCodes : packCodes,
        mergedCodeItems,
        oneLevelOnly ? 'MEDIUM' : 'SMALL',
      );
      ElMessage.success(
        lastHint ||
          `已展开 ${mergedCodeItems.length} 个码（完成 ${doneBatches}/${chunks.length} 批）`,
      );
      return;
    }
    if (anyExpanded && mergedSmall.length > 0) {
      msfxGenerateConfirmRef.value?.applySmallCodes(payload.key, mergedSmall);
      ElMessage.success(
        lastHint ||
          `已展开为 ${mergedSmall.length} 个小码（完成 ${doneBatches}/${chunks.length} 批）`,
      );
      return;
    }
    ElMessage.warning(lastHint || '平台尚未返回小码，请确认 102 成功后再次点击「拉取」');
  } finally {
    loading.close();
    msfxGenerateConfirmRef.value?.setRowPulling(payload.key, false);
  }
}
/** 已生成行但尚未落库绑定时，保存成功后补 markApplied（支持多选） */
const pendingMsfxBinds = ref<Array<{ importId: string; billCode?: string }>>([]);
/** 确认生成弹窗打开时暂存拉单套用上下文 */
const pendingMsfxApplyPayload = ref<{
  importId: number | string;
  importIds?: Array<number | string>;
  fromEntId?: string;
  fromEntName?: string;
  billCode?: string;
  drugRows?: MsfxUpstreamBillRowDto[];
  traceCodes: string[];
} | null>(null);

/** 选择 HIS 供应商时按对照表回填 ent_id */
watch(
  () => headForm.value.supplierId,
  (supplierId) => {
    void hydrateMsfxSupplierEntFromMap(supplierId);
  },
);

/** 已落库单号变化时尝试回填上游导入账的 fromEntId */
watch(
  () => currentPersistedBusNo(),
  (busNo) => {
    void hydrateMsfxSupplierEntFromImport(busNo);
  },
  { immediate: true },
);

async function hydrateMsfxSupplierEntFromMap(supplierId: string | number | undefined | null) {
  const id = String(supplierId ?? '').trim();
  if (!id) {
    msfxSupplierEntId.value = '';
    msfxSupplierEntName.value = '';
    return;
  }
  // 误把 HIS 供应商 id 写进 ent_id 时清掉
  if (String(msfxSupplierEntId.value ?? '').trim() === id) {
    msfxSupplierEntId.value = '';
    msfxSupplierEntName.value = '';
  }
  try {
    const res = await getMsfxSupplierMapBySupplierIdApi({ supplierId: id });
    const ent = String(res.data?.msfxEntId ?? '').trim();
    if (ent && ent !== id) {
      msfxSupplierEntId.value = ent;
      const name = String(res.data?.msfxEntName ?? '').trim();
      msfxSupplierEntName.value =
        name && name !== ent ? name : String(res.data?.supplierName ?? '').trim();
    } else {
      // 新供应商无对照：清空旧 ent，避免拉单仍锁定上一家
      msfxSupplierEntId.value = '';
      msfxSupplierEntName.value = '';
    }
  } catch {
    // 模块未启用：忽略；有旧值时保留以免打断已拉单上下文
  }
}

async function hydrateMsfxSupplierEntFromImport(busNo: string) {
  const no = String(busNo ?? '').trim();
  if (!no || String(msfxSupplierEntId.value ?? '').trim()) return;
  try {
    const res = await getMsfxUpstreamImportByHisBusNoApi({ hisBusNo: no });
    const ent = String(res.data?.fromEntId ?? '').trim();
    const supplierId = String(headForm.value.supplierId ?? '').trim();
    if (ent && ent !== supplierId) {
      msfxSupplierEntId.value = ent;
      const name = String(res.data?.fromEntName ?? '').trim();
      if (name && name !== ent) msfxSupplierEntName.value = name;
    }
  } catch {
    // 模块未启用或无导入账：忽略
  }
}
function openMsfxUpstreamPull() {
  if (!msfxUiEnabled) {
    return;
  }
  if (!props.documentEditable && !props.isDraftOrder) {
    ElMessage.warning('当前单据不可编辑，无法套用上游码');
    return;
  }
  if (!String(headForm.value.itemTableType ?? '').trim()) {
    ElMessage.warning('请先在单头选择项目类型');
    return;
  }
  if (!isSpecificInventoryLocationId(headForm.value.warehouseId)) {
    ElMessage.warning('请先选择库房，再拉单生成明细');
    return;
  }
  const hasLines = collectGridRows().some((r) => isPresentItemId(r.itemId));
  msfxLockPreferredSupplier.value = hasLines;
  // 明细已空：按单头供应商对照重填，解除上次拉单企业硬锁，便于换供应商
  if (!hasLines) {
    void hydrateMsfxSupplierEntFromMap(headForm.value.supplierId).finally(() => {
      msfxUpstreamVisible.value = true;
    });
    return;
  }
  msfxUpstreamVisible.value = true;
}

/**
 * 上游拉单套用：有药品行则打开确认生成（可追加到已有明细，同供应商可多次拉）；
 * 仅有追溯码时套到本单空码行。
 */
async function handleMsfxUpstreamApply(payload: {
  importId: number | string;
  importIds?: Array<number | string>;
  fromEntId?: string;
  fromEntName?: string;
  billCode?: string;
  drugRows?: MsfxUpstreamBillRowDto[];
  traceCodes: string[];
}) {
  if (!String(headForm.value.itemTableType ?? '').trim()) {
    ElMessage.warning('请先在单头选择项目类型');
    return;
  }
  if (!(await validateInventoryHeadFormFields(headFormRef.value, ['warehouseId']))) {
    return;
  }

  const incomingEnt = String(payload.fromEntId ?? '').trim();
  const hasProductLines = collectGridRows().some((row) => isPresentItemId(row.itemId));
  // 已有明细才校验同供应商；明细空时允许换上游企业（并在套用后改写对照）
  if (hasProductLines) {
    let boundEnt = String(msfxSupplierEntId.value ?? '').trim();
    if (!boundEnt) {
      const sid = String(headForm.value.supplierId ?? '').trim();
      if (sid) {
        try {
          const res = await getMsfxSupplierMapBySupplierIdApi({ supplierId: sid });
          boundEnt = String(res.data?.msfxEntId ?? '').trim();
        } catch {
          // 无对照时不在此拦截，由拉单弹窗约束同企业勾选
        }
      }
    }
    if (incomingEnt && boundEnt && incomingEnt !== boundEnt) {
      ElMessage.warning(
        '拉单上游企业与本单供应商不一致，不能混入其他供应商的出库单。请先清空明细后更换单头供应商，或另建进货单',
      );
      return;
    }
  }

  const drugRows = payload.drugRows ?? [];
  // 有上游药品：确认生成/追加明细（同供应商可多次拉）
  if (drugRows.length > 0) {
    pendingMsfxApplyPayload.value = payload;
    msfxGenerateDrugRows.value = drugRows;
    msfxGenerateConfirmVisible.value = true;
    return;
  }

  const existingProductRows = collectGridRows().filter((row) => isPresentItemId(row.itemId));
  if (existingProductRows.length === 0) {
    ElMessage.warning('上游明细无药品行，无法生成');
    return;
  }
  if (!payload.traceCodes?.length) {
    ElMessage.warning('上游无可套用的药品或追溯码');
    return;
  }
  const assignedResult = assignMsfxTraceCodesToRows(existingProductRows, payload.traceCodes);
  if (assignedResult.unassignedCount > 0) {
    ElMessage.warning(
      `有 ${assignedResult.unassignedCount} 个追溯码未分配到空码行（不再自动追加到末行，请增行后重套或手工粘贴）`,
    );
  }
  const emptyTail = collectGridRows().filter((row) => !isPresentItemId(row.itemId));
  gridApi?.grid?.loadData([...assignedResult.rows, ...emptyTail]);
  await finishMsfxUpstreamApplyAfterGridChange(payload, {
    generatedCount: 0,
    unmatchedNames: [],
  });
}

/**
 * 确认写入前不再同步调平台展开。
 * 大批量（如 1500 盒）relation 必超时；中大码能盖住入库量时按 PACK 落库即可。
 * 应扫数按盒/箱对齐（勿用拆零片数），码数已够则保持当前层级。
 */
async function expandMsfxLineCodesToSmallest(line: MsfxGenerateConfirmLine): Promise<void> {
  if (String(line.drug.inboundCodeRank ?? '').toUpperCase() === 'PACK') {
    return;
  }
  const writeable = resolveMsfxWriteableTraceCodes(line.drug);
  const qty = Number(line.purchaseQty);
  if (writeable.length === 0 || !Number.isFinite(qty) || qty <= 0) {
    return;
  }
  const bands = normalizeInboundProductUnitBands(line.product?.unitBands);
  const mismatchOpts = {
    selectedBandKind: line.selectedBandKind,
    bands,
    product: line.product,
  };
  const packProbe = { ...line.drug, inboundCodeRank: 'PACK' as const };
  if (!isMsfxQtyCodeMismatch(qty, packProbe, mismatchOpts)) {
    line.drug.inboundCodeRank = 'PACK';
    if ((line.drug.packTraceCodes ?? []).length === 0) {
      line.drug.packTraceCodes = [...writeable];
    }
    return;
  }
  const expected =
    resolveMsfxExpectedTraceCount({
      purchaseQty: qty,
      selectedBandKind: line.selectedBandKind,
      bands,
      product: line.product,
      drug: line.drug,
    }) ?? qty;
  // 码数已够应扫（盒码对盒数），保持当前层级
  if (writeable.length + 0.0001 >= expected) {
    return;
  }
  // 码数盖不住：仍按中大码落库，避免同步展开超时；用户可事后在确认页分批拉取
  line.drug.inboundCodeRank = 'PACK';
  if ((line.drug.packTraceCodes ?? []).length === 0) {
    line.drug.packTraceCodes = [...writeable];
  }
}

/** 确认面板：写入选中的进货行后走落盘/绑定 */
async function handleMsfxGenerateConfirm(lines: MsfxGenerateConfirmLine[]) {
  const payload = pendingMsfxApplyPayload.value;
  if (!payload) {
    ElMessage.warning('拉单上下文已失效，请重新拉单');
    return;
  }
  const loading = ElMessage({
    message: '正在写入进货明细…',
    type: 'info',
    duration: 0,
  });
  try {
    for (const line of lines) {
      await expandMsfxLineCodesToSmallest(line);
    }
    const mismatched = lines.filter((line) => {
      const bands = normalizeInboundProductUnitBands(line.product?.unitBands);
      return isMsfxQtyCodeMismatch(line.purchaseQty, line.drug, {
        selectedBandKind: line.selectedBandKind,
        bands,
        product: line.product,
      });
    });
    if (mismatched.length > 0) {
      const sample = mismatched
        .slice(0, 3)
        .map((line) => {
          const bands = normalizeInboundProductUnitBands(line.product?.unitBands);
          const name =
            String(
              line.product?.productName ?? line.drug?.drugName ?? line.drug?.physicName ?? '',
            ).trim() || '药品';
          const codeN = countMsfxWriteableTraceCodes(line.drug);
          const expected =
            line.purchaseQty !== undefined && line.purchaseQty !== null
              ? resolveMsfxExpectedTraceCount({
                  purchaseQty: Number(line.purchaseQty),
                  selectedBandKind: line.selectedBandKind,
                  bands,
                  product: line.product,
                  drug: line.drug,
                })
              : undefined;
          return `${name}(应扫${expected ?? '—'}≠码${codeN})`;
        })
        .join('、');
      ElMessage.warning(`追溯码数量与入库数量不一致，不允许带入：${sample}`);
      return;
    }
    const inserted = await insertConfirmedMsfxInboundLines(lines);
    if (inserted <= 0) {
      ElMessage.warning('未写入任何明细行');
      return;
    }
    await finishMsfxUpstreamApplyAfterGridChange(payload, {
      generatedCount: inserted,
      unmatchedNames: [],
    });
    pendingMsfxApplyPayload.value = null;
    msfxGenerateDrugRows.value = [];
  } catch (error) {
    const raw =
      error && typeof error === 'object' && 'message' in error
        ? String((error as { message?: string }).message ?? '')
        : '';
    if (/timeout/i.test(raw)) {
      ElMessage.error('展开追溯码超时。请在确认页选择「中大码」入库，或缩小单次拉单数量后重试');
    } else {
      ElMessage.error(pickApiErrMsg(error) || '写入进货明细失败');
    }
  } finally {
    loading.close();
  }
}

async function finishMsfxUpstreamApplyAfterGridChange(
  payload: {
    importId: number | string;
    importIds?: Array<number | string>;
    fromEntId?: string;
    fromEntName?: string;
    billCode?: string;
    traceCodes: string[];
  },
  meta: { generatedCount: number; unmatchedNames: string[] },
) {
  const { generatedCount, unmatchedNames } = meta;
  flushInboundDraftPersist();

  if (payload.fromEntId) {
    msfxSupplierEntId.value = payload.fromEntId;
  }
  if (payload.fromEntName) {
    msfxSupplierEntName.value = payload.fromEntName;
  }

  const importIds =
    payload.importIds && payload.importIds.length > 0 ? payload.importIds : [payload.importId];
  const binds = toMsfxImportBinds(importIds, payload.billCode);

  let busNo = currentPersistedBusNo();
  if (!busNo) {
    busNo = (await persistInbound(false)) ?? '';
    if (!busNo) {
      if (generatedCount > 0) {
        pendingMsfxBinds.value = binds;
        ElMessage.warning(
          `已追加 ${generatedCount} 行，请补全批号/数量后点保存；保存成功后将自动绑定上游单`,
        );
      } else {
        ElMessage.warning('追溯码已写入明细，但保存失败；请手工保存后再绑定上游单');
      }
      return;
    }
    const draftId = String(props.clientDraftId ?? '').trim();
    if (draftId) {
      clearInvDocDraftCache(INV_DOC_DRAFT_SCENE.warehousePurchaseInbound, draftId);
    }
    parentSync.bindActivePersistDocKey(busNo);
    if (!inboundRowsNeedMsfxSmallCodeExpand()) {
      emit('documentSaved', { busNo });
    }
  } else if (props.documentEditable) {
    const saved = await persistInbound(false);
    if (!saved) {
      if (generatedCount > 0) {
        pendingMsfxBinds.value = binds;
        ElMessage.warning(
          `已追加 ${generatedCount} 行，请补全批号/数量后点保存；保存成功后将自动绑定上游单`,
        );
      } else {
        ElMessage.warning('追溯码已写入网格，但落盘失败；请手工保存后再绑定');
      }
      return;
    }
  }

  pendingMsfxBinds.value = [];
  try {
    await bindMsfxImportsToHisBusNo(binds, busNo);
    const billHint = payload.billCode ? ` ${payload.billCode}` : '';
    const billCountHint = importIds.length > 1 ? `（共 ${importIds.length} 张）` : '';
    if (generatedCount > 0) {
      const miss =
        unmatchedNames.length > 0
          ? `；未匹配 ${unmatchedNames.length} 种：${unmatchedNames.slice(0, 3).join('、')}${unmatchedNames.length > 3 ? '…' : ''}`
          : '';
      ElMessage.success(
        `已追加 ${generatedCount} 行进货明细（上游单${billHint}${billCountHint}）${miss}`,
      );
    } else {
      ElMessage.success(
        `已套用上游单${billHint}${billCountHint}，共 ${payload.traceCodes.length} 码`,
      );
    }
    msfxUploadBarRef.value?.reload();
  } catch (error) {
    ElMessage.warning(`进货明细已写入，上游单绑定未完成：${pickApiErrMsg(error)}`);
  }

  await tryMsfxUploadAndExpandAfterPull(busNo);
}

/** 拆零入库仍是中大码（码数 < 入库支数）时，用本进货单号正式 102 再展开支码 */
function inboundRowsNeedMsfxSmallCodeExpand(): boolean {
  for (const row of collectGridRows()) {
    const codes = String(row.traceNo ?? '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    const qty = Number(row.purchaseQty);
    if (codes.length > 0 && Number.isFinite(qty) && qty > 0 && codes.length + 0.0001 < qty) {
      return true;
    }
  }
  return false;
}

async function tryMsfxUploadAndExpandAfterPull(busNo: string): Promise<void> {
  if (!msfxUiEnabled) {
    return;
  }
  const no = String(busNo ?? '').trim();
  if (!no || !inboundRowsNeedMsfxSmallCodeExpand()) return;

  // 统计待展开码量：超过阈值跳过自动展开，避免保存后整单超时
  let pendingCodes = 0;
  for (const row of collectGridRows()) {
    const codes = String(row.traceNo ?? '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    const qty = Number(row.purchaseQty);
    if (codes.length > 0 && Number.isFinite(qty) && qty > 0 && codes.length + 0.0001 < qty) {
      pendingCodes += codes.length;
    }
  }
  if (pendingCodes > 50) {
    ElMessage.info(
      `本单有 ${pendingCodes} 个中大码，自动展开易超时，已跳过。可按中大码完成进货；若必须小码请在确认页分批「拉取」`,
    );
    return;
  }

  const loading = ElMessage({
    message: '正在用本进货单上报码上放心（102）并展开支码，请勿关闭页面…',
    type: 'info',
    duration: 0,
  });
  try {
    const res = await uploadAndExpandInboundSmallCodesApi({
      busNo: no,
      hisSceneEnum: MSFX_HIS_SCENE.WAREHOUSE_PURCHASE_IN,
      supplierEntId: String(msfxSupplierEntId.value ?? '').trim() || undefined,
      supplierEntName: resolveMsfxUploadSupplierName() || undefined,
      supplierRefEntId: resolveMsfxUploadSupplierRef() || undefined,
      billTime: String(headForm.value.applyTime ?? '').trim() || undefined,
    });
    const data = res.data ?? {};
    msfxUploadBarRef.value?.reload();
    if (data.wroteToLines) {
      ElMessage.success(data.hint || `已展开为 ${data.afterCodeCount ?? 0} 个支码`);
      emit('documentSaved', { busNo: no });
      return;
    }
    const hint = String(data.hint ?? '').trim();
    ElMessage.warning(hint || '进货已保存。支码尚未展开，请在单头点「上报并展开支码」');
    emit('documentSaved', { busNo: no });
  } catch (error) {
    const raw =
      error && typeof error === 'object' && 'message' in error
        ? String((error as { message?: string }).message ?? '')
        : '';
    if (/timeout/i.test(raw)) {
      ElMessage.warning('进货已保存。展开支码超时，可按中大码继续审批，或稍后分批上报展开');
    } else {
      ElMessage.warning('进货已保存。码上放心展开支码未完成，请在单头点「上报并展开支码」');
    }
    emit('documentSaved', { busNo: no });
  } finally {
    loading.close();
  }
}

/** 将确认面板选中的行写入进货网格 */
async function insertConfirmedMsfxInboundLines(lines: MsfxGenerateConfirmLine[]): Promise<number> {
  const docType = String(headForm.value.itemTableType ?? '').trim();
  const $grid = gridApi?.grid;
  if (!$grid || lines.length === 0) return 0;

  inboundGridEdit.clearGridEdit();
  await removeEmptyInboundRows();
  const insertedRows: InboundPreviewRow[] = [];
  for (const line of lines) {
    const pickTable = inventoryPickStringField(line.product, 'productTableName');
    if (pickTable && docType && pickTable !== docType) {
      continue;
    }
    const record = createEmptyInboundRow();
    syncRowFromInboundMedicinePick(line.product, record);
    const bands = record.unitBands ?? [];
    const kind =
      String(line.selectedBandKind ?? '').trim() ||
      (bands.length ? defaultInboundUnitBandKind(bands) : 'LEGACY');
    record.selectedBandKind = kind;
    record.__prevBandKind = kind;
    record.unitList =
      bands.length > 0 ? inboundUnitBandSelectOptions(bands) : [{ value: 'LEGACY', label: '单位' }];
    const band = findInboundUnitBandByKind(bands, kind);
    const unitCode = String(band?.unitCode ?? record.purchaseUnit ?? '').trim();
    if (unitCode) {
      record.purchaseUnit = unitCode;
    }
    const bandPrice = Number(band?.unitPrice);
    if (Number.isFinite(bandPrice) && bandPrice >= 0) {
      record.unitPrice = bandPrice;
    } else {
      const resolved = warehouseNonInboundResolveDisplayUnitPrice({
        bands,
        displayBandKind: kind,
        legacyUnitPrice: record.unitPrice,
      });
      if (resolved !== undefined && resolved !== null) {
        record.unitPrice = resolved;
      }
    }
    if (line.purchaseQty !== undefined && line.purchaseQty !== null) {
      const qty = Number(line.purchaseQty);
      if (Number.isFinite(qty) && qty > 0) {
        record.purchaseQty = qty;
      }
    }
    recalcInboundLineTotal(record);
    record.batchNo = String(line.drug.produceBatchNo ?? '').trim();
    // 优先已展开小码；确认前会尽量把中大码 relation 成最小码
    const codeParts = resolveMsfxWriteableTraceCodes(line.drug);
    if (codeParts.length) {
      record.traceNo = codeParts.join(',');
    }
    record.prodExpiry = mapStartEndToProdExpiry(line.drug.produceDate, line.drug.validEndDate);
    const { row } = await $grid.insertAt(record, null);
    if (row) {
      insertedRows.push(row as InboundPreviewRow);
      inboundGridEdit.markRowFormMode(row as InboundPreviewRow);
    }
  }
  await $grid.$nextTick();
  await nextTick();
  const firstInserted = insertedRows[0];
  if (firstInserted && isPresentItemId(firstInserted.itemId)) {
    await $grid.scrollToRow(firstInserted);
    await inboundGridEdit.enterRowFormMode(firstInserted, '_qtyUnit');
  }
  return insertedRows.length;
}

/** 添加入库明细空行（同药不同批号可多行，保存时按批号去重） */
async function handleAddItem() {
  if (!props.documentEditable) return;
  if (!String(headForm.value.itemTableType ?? '').trim()) {
    ElMessage.warning('请先在单头选择项目类型');
    return;
  }
  if (!(await validateInventoryHeadFormFields(headFormRef.value, ['warehouseId']))) {
    return;
  }
  await addGridItem();
}

/** 欢迎空态：新建空白进货草稿 */
function handleCreateBlank() {
  emit('createBlank');
}

function openUpstreamPicker() {
  linkPoQueryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    warehouseId: INV_ALL_LOCATIONS_FILTER_ID,
  };
  linkPoRecords.value = [];
  linkPoTotal.value = 0;
  linkPoVisible.value = true;
}

async function openLinkPoDialog() {
  if (!canEditOriginalBusNo.value) {
    ElMessage.warning('当前单据不可关联订货单');
    return;
  }
  await openUpstreamPicker();
}

function handleLinkPoQuery(params: Record<string, unknown>) {
  Object.assign(linkPoQueryParams.value, params);
  linkPoQueryParams.value.warehouseId = normalizeLinkPoWarehouseId(
    linkPoQueryParams.value.warehouseId,
  );
  linkPoQueryParams.value.pageNo = 1;
  void fetchLinkablePurchaseOrders();
}

function handleLinkPoReset() {
  linkPoQueryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    warehouseId: INV_ALL_LOCATIONS_FILTER_ID,
  };
  void fetchLinkablePurchaseOrders();
}

function handleLinkPoPagination(pagination: { limit: number; page: number }) {
  linkPoQueryParams.value.pageNo = pagination.page;
  linkPoQueryParams.value.pageSize = pagination.limit;
  void fetchLinkablePurchaseOrders();
}

async function fetchLinkablePurchaseOrders() {
  linkPoQueryParams.value.warehouseId = normalizeLinkPoWarehouseId(
    linkPoQueryParams.value.warehouseId,
  );
  linkPoLoading.value = true;
  try {
    const res = await pageLinkablePurchaseOrdersApi({
      purposeLocationId: resolveLinkPoPurposeLocationId(),
      keyword: String(linkPoQueryParams.value.keyword ?? '').trim() || undefined,
      pageNo: linkPoQueryParams.value.pageNo,
      pageSize: linkPoQueryParams.value.pageSize,
    });
    const { content, totalCount } = parsePagedRowsFromApiData<LinkablePurchaseOrderListRowDto>(
      res.data,
    );
    linkPoRecords.value = content;
    linkPoTotal.value = totalCount;
  } catch {
    linkPoRecords.value = [];
    linkPoTotal.value = 0;
  } finally {
    linkPoLoading.value = false;
  }
}

async function applyLinkablePurchaseOrder(row: LinkablePurchaseOrderListRowDto) {
  if (!props.isDraftOrder) {
    emit('createFromUpstream', row);
    return;
  }
  await applyLinkablePurchaseOrderToDraft(row);
}

async function applyLinkablePurchaseOrderToDraft(row: LinkablePurchaseOrderListRowDto) {
  const poNo = String(row.busNo ?? row.originalBusNo ?? '').trim();
  if (!poNo) return;
  try {
    const res = await getLinkablePurchaseOrderDetailApi({
      busNo: poNo,
    });
    const data = res.data;
    if (!data?.lines?.length) {
      ElMessage.warning('未获取到订货明细');
      return;
    }
    headForm.value.originalBusNo = data.originalBusNo ?? data.purchaseOrderBusNo ?? poNo;
    if (data.supplierId !== undefined && data.supplierId !== null) {
      headForm.value.supplierId = String(data.supplierId);
    }
    if (data.purposeLocationId) {
      headForm.value.warehouseId = data.purposeLocationId as number | string;
    }
    const firstTable = data.lines[0]?.itemTable;
    if (firstTable) {
      headForm.value.itemTableType = String(firstTable);
    }
    const gridRows = (data.lines ?? []).map((ln) =>
      mapDetailRowToGrid(ln as unknown as Record<string, unknown>),
    );
    inboundGridEdit.resetFormMode();
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchInboundGridEditConfig();
    });
    linkPoVisible.value = false;
    ElMessage.success('已带入订货明细，请核对数量/批号后保存');
  } catch {
    /* handled */
  }
}

const linkPoButtonLabel = computed(() =>
  hasLinkedUpstream.value && props.isDraftOrder ? '更换订货单' : '从订货单选择',
);

defineExpose({
  applyLinkablePurchaseOrderToDraft,
  openUpstreamPicker,
});

watch(
  () => linesGridEditable.value,
  () => {
    patchInboundGridEditConfig();
  },
);

/** 打开批量选品弹窗（与单行选品共用单头项目类型） */
function openBatchDialog() {
  if (!linesGridEditable.value) return;
  if (!String(headForm.value.itemTableType ?? '').trim()) {
    ElMessage.warning('请先在单头选择项目类型');
    return;
  }
  batchDialogVisible.value = true;
}

/** 批量选品确认：逐条新建行并写入选品信息（等同多次单行选择） */
async function handleBatchConfirm(products: Record<string, unknown>[]) {
  if (products.length === 0) return;
  const docType = String(headForm.value.itemTableType ?? '').trim();
  if (!docType) {
    ElMessage.warning('请先在单头选择项目类型');
    return;
  }
  for (const product of products) {
    const pickTable = inventoryPickStringField(product, 'productTableName');
    if (pickTable && pickTable !== docType) {
      ElMessage.warning('批量添加的项目须与单头项目类型一致');
      return;
    }
  }
  const existingRows = collectGridRows().filter((r) => isPresentItemId(r.itemId));
  if (existingRows.length > 0) {
    const lockedTable = String(existingRows[0]?.itemTable ?? '').trim();
    const firstPickTable = inventoryPickStringField(products[0]!, 'productTableName');
    if (lockedTable && firstPickTable && lockedTable !== firstPickTable) {
      ElMessage.warning('一张进货单只能包含同一项目类型（药品或耗材）');
      return;
    }
  }
  const $grid = gridApi?.grid;
  if (!$grid) return;
  inboundGridEdit.clearGridEdit();
  await removeEmptyInboundRows();
  const insertedRows: InboundPreviewRow[] = [];
  for (const product of products) {
    const record = createEmptyInboundRow();
    syncRowFromInboundMedicinePick(product, record);
    const { row } = await $grid.insertAt(record, null);
    if (row) {
      insertedRows.push(row as InboundPreviewRow);
      inboundGridEdit.markRowFormMode(row as InboundPreviewRow);
    }
  }
  await $grid.$nextTick();
  await nextTick();
  const firstInserted = insertedRows[0];
  if (firstInserted && isPresentItemId(firstInserted.itemId)) {
    await $grid.scrollToRow(firstInserted);
    await inboundGridEdit.enterRowFormMode(firstInserted, '_qtyUnit');
  }
  ElMessage.success(`已添加 ${products.length} 条项目，请补充数量、批号等信息`);
}
</script>

<template>
  <div
    class="purchase-inbound-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
    :class="{ 'purchase-inbound-main-pane--flash': paneFlashActive }"
  >
    <InvMainShell>
      <template v-if="!showWelcomeEmpty" #actionBar>
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
        v-if="showWelcomeEmpty"
        class="flex min-h-0 flex-1 flex-col items-center justify-center px-4 py-12"
      >
        <el-empty :image-size="96" description="可手工新建进货单选品录入，或从已同意订货单拉取明细">
          <div class="flex flex-wrap items-center justify-center gap-2">
            <InvPrimaryBtn label="新增进货单" @click="handleCreateBlank" />
            <ElButton @click="openUpstreamPicker">从订货单新建</ElButton>
          </div>
        </el-empty>
      </div>

      <div
        v-else
        class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-2 pt-2"
        @mousedown="inboundGridEdit.onSurfaceMouseDown"
      >
        <div class="shrink-0">
          <SectionTitle title="进货单信息" />
          <div class="mt-1 flex flex-wrap items-start gap-x-4 gap-y-2">
            <div class="flex items-center gap-2 pt-1.5">
              <span class="text-xs" style="color: var(--el-text-color-regular)">状态</span>
              <span class="text-sm" style="color: var(--el-text-color-primary)">{{
                inboundHeaderStatusText
              }}</span>
            </div>
            <MsfxUploadStatusBar
              ref="msfxUploadBarRef"
              :bus-no="currentPersistedBusNo()"
              :his-scene-enum="MSFX_HIS_SCENE.WAREHOUSE_PURCHASE_IN"
              v-model:supplier-ent-id="msfxSupplierEntId"
              :supplier-ent-name="msfxSupplierEntName"
              :bill-time="headForm.applyTime"
              @inbound-codes-expanded="onMsfxInboundCodesExpanded"
            />
          </div>
          <ElForm
            ref="headFormRef"
            :model="headForm"
            :rules="headFormRules"
            label-width="80px"
            class="compact-head-form mt-1"
          >
            <div
              class="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5"
            >
              <ElFormItem label="单据号">
                <ElInput v-model="headForm.busNo" disabled placeholder="未选择" />
              </ElFormItem>
              <ElFormItem label="供应商" required>
                <ElSelect
                  v-model="headForm.supplierId"
                  placeholder="请选择"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="s in supplierOptions"
                    :key="String(s.value)"
                    :label="s.label"
                    :value="String(s.value)"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="制单时间">
                <ElDatePicker
                  v-model="headForm.applyTime"
                  class="w-full"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  format="YYYY-MM-DD HH:mm:ss"
                  placeholder="选择时间"
                  :disabled="!documentEditable"
                />
              </ElFormItem>
              <ElFormItem label="库房" prop="warehouseId">
                <ElSelect
                  v-model="headForm.warehouseId"
                  placeholder="请选择"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="w in headWarehouseOptions"
                    :key="String(w.id ?? w.value)"
                    :label="String(w.name ?? w.label ?? '')"
                    :value="warehouseOptionValue(w)"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="项目类型">
                <ElSelect
                  v-model="headForm.itemTableType"
                  placeholder="请选择"
                  class="w-full"
                  filterable
                  :disabled="!documentEditable"
                  @change="handleItemTableTypeChange"
                >
                  <ElOption
                    v-for="t in itemTableTypeOptions"
                    :key="String(t.value)"
                    :label="t.label"
                    :value="String(t.value)"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="关联订货" class="sm:col-span-2 lg:col-span-2 xl:col-span-2">
                <div class="flex min-w-0 items-center gap-2">
                  <ElInput
                    :model-value="headForm.originalBusNo"
                    class="min-w-0 flex-1"
                    readonly
                    disabled
                    placeholder="可选；须为已同意的订货单"
                  />
                  <ElButton
                    class="shrink-0"
                    :disabled="!canEditOriginalBusNo"
                    @click="openLinkPoDialog"
                  >
                    {{ linkPoButtonLabel }}
                  </ElButton>
                </div>
              </ElFormItem>
              <ElFormItem label="发票号" prop="invoiceNo">
                <ElInput
                  v-model="headForm.invoiceNo"
                  class="w-full"
                  clearable
                  maxlength="255"
                  placeholder="选填；未填上报占位 0"
                  :disabled="!documentEditable"
                />
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <InvDocDetailGridShell
          :bind-grid-wrap="bindGridWrap"
          :on-grid-keydown="inboundGridEdit.onGridKeydown"
        >
          <Grid>
            <template #toolbar-actions>
              <InvDocGridToolbar
                v-if="linesGridEditable"
                show-batch-add
                show-trace-scan
                @add="handleAddItem"
                @batch-add="openBatchDialog"
                @delete-selected="deleteItem('select')"
                @trace-scan="handleOpenTraceScan"
              />
              <ElButton
                v-if="linesGridEditable && msfxUiEnabled"
                class="mr-2"
                @click="openMsfxUpstreamPull"
              >
                码上放心拉单
              </ElButton>
            </template>
            <template #action="{ row }">
              <ElButton
                v-if="linesGridEditable"
                type="danger"
                text
                @click="deleteItem(row as InboundPreviewRow)"
              >
                <Trash2 class="size-4" />
                删除
              </ElButton>
            </template>
          </Grid>
        </InvDocDetailGridShell>
      </div>
      <InvDocAmountSummaryBar
        v-if="showFinanceApprovalSummary"
        :items="financeApprovalSummaryItems"
      />
    </InvMainShell>

    <PurchaseInboundBatchAddDialog
      v-model:visible="batchDialogVisible"
      :pick-gate-location-id="resolveInboundReceiptPickGateLocationId()"
      :product-table-name="headForm.itemTableType"
      @confirm="handleBatchConfirm"
    />

    <MsfxUpstreamPullDialog
      v-model="msfxUpstreamVisible"
      :his-supplier-id="headForm.supplierId"
      :his-supplier-name="
        resolvePrintOptionLabel(props.supplierOptions, headForm.supplierId) || undefined
      "
      :preferred-from-ent-id="msfxSupplierEntId"
      :preferred-from-ent-name="msfxSupplierEntName"
      :lock-preferred-supplier="msfxLockPreferredSupplier"
      @apply="handleMsfxUpstreamApply"
    />

    <MsfxUpstreamGenerateConfirmDialog
      ref="msfxGenerateConfirmRef"
      v-model="msfxGenerateConfirmVisible"
      :drug-rows="msfxGenerateDrugRows"
      :product-table-name="headForm.itemTableType"
      :purpose-location-id="resolveInboundReceiptPickGateLocationId()"
      @confirm="handleMsfxGenerateConfirm"
      @pull-small-codes="handlePullSmallCodes"
    />

    <InvDocLinkPickerDialog
      v-model="linkPoVisible"
      title="选择已同意的订货单"
      :table-data="linkPoRecords"
      :loading="linkPoLoading"
      :total="linkPoTotal"
      :query-params="linkPoQueryParams"
      :form-items="linkPoFormItems"
      :table-columns="linkPoTableColumns"
      :location-options="warehouseOptions"
      :practitioner-options="practitionerOptions"
      @query="handleLinkPoQuery"
      @reset="handleLinkPoReset"
      @pagination="handleLinkPoPagination"
      @opened="fetchLinkablePurchaseOrders"
      @select="(row) => applyLinkablePurchaseOrder(row as LinkablePurchaseOrderListRowDto)"
    >
      <template #supplierName="{ row }">
        {{ resolveLinkPoSupplierLabel(row as LinkablePurchaseOrderListRowDto) }}
      </template>
      <template #applyTime="{ row }">
        {{
          (row as LinkablePurchaseOrderListRowDto).applyTime
            ? formatDateTime((row as LinkablePurchaseOrderListRowDto).applyTime as string)
            : ''
        }}
      </template>
      <template #rowActions="{ row }">
        <ElButton
          link
          type="primary"
          @click.stop="applyLinkablePurchaseOrder(row as LinkablePurchaseOrderListRowDto)"
        >
          选用
        </ElButton>
      </template>
    </InvDocLinkPickerDialog>
  </div>
  <InvDocTraceScanBridge
    ref="traceScanBridgeRef"
    scene="inbound"
    lot-field="batchNo"
    :default-location-id="headForm.warehouseId"
    @bindings-changed="handleTraceBindingsChanged"
  />
</template>

<style scoped lang="scss">
.compact-head-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.compact-head-form :deep(.el-form-item__label) {
  padding-right: 8px;
}

@keyframes purchase-inbound-pane-flash {
  0% {
    opacity: 1;
  }

  35% {
    opacity: 0.72;
  }

  100% {
    opacity: 1;
  }
}

.purchase-inbound-main-pane--flash {
  animation: purchase-inbound-pane-flash 0.42s ease-out;
}
</style>
