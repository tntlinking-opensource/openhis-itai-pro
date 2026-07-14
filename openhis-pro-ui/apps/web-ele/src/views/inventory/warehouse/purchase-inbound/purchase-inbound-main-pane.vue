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
import type { InboundProductUnitBand } from '#/utils/inventory/inbound-product-unit-bands';

import type { FormInstance } from 'element-plus';

import { computed, nextTick, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
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
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
} from 'element-plus';

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
import {
  INV_DOC_UPSTREAM_HINT,
  INV_DOC_UPSTREAM_WELCOME,
  hasNonEmptyBusNo,
  warnInventoryDocRestriction,
} from '#/utils/inventory/inventory-doc-restrictions';
import PurchaseInboundBatchAddDialog from '#/views/inventory/warehouse/purchase-inbound/purchase-inbound-batch-add-dialog.vue';
import {
  formatInventoryLineTotal,
  formatMoneyDisplay,
  inventoryLineTotalNumber,
} from '#/utils/format/money-scale';
import {
  canConvertDisplayQtyToSplitForBands,
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
  isPresentItemId,
  scheduleInventoryProductPickFocus,
} from '#/utils/inventory/inventory-doc-grid-common';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  INV_ALL_WAREHOUSES_LABEL,
  isSpecificInventoryLocationId,
  resolveFirstRealLocationIdFromInitOptions,
  resolveSpecificInventoryLocationId,
} from '#/utils/inventory/inventory-workbench-list';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import { withInvDocLinkPickerEnrichedColumns } from '#/utils/inventory/inv-doc-link-picker-display';

interface Props {
  /** 当前选中单据号（来自左侧列表） */
  busNo?: string;
  /** 当前为新增草稿（无后端单号） */
  isDraftOrder?: boolean;
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
  /** 未选中任何单据时展示欢迎空态（引导从订货单新建） */
  showWelcomeEmpty?: boolean;
  /** init 制单人/经手人选项（关联弹窗制单人列解析） */
  practitionerOptions?: ApiOptionItem[];
}

defineOptions({ name: 'PurchaseInboundMainPane' });

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  isDraftOrder: false,
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
  createFromUpstream: [row: LinkablePurchaseOrderListRowDto];
  documentChanged: [{ busNo?: string }];
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

interface HeadFormModel {
  busNo: string;
  originalBusNo: string;
  supplierId: string;
  applyTime: string;
  warehouseId: number | string;
  itemTableType: string;
}

function defaultHeadForm(): HeadFormModel {
  return {
    busNo: '',
    originalBusNo: '',
    supplierId: '',
    applyTime: '',
    warehouseId: '',
    itemTableType: '',
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
  const v = row.id ?? row.value;
  if (typeof v === 'number' || typeof v === 'string') return v;
  return String(v ?? '');
}

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

const headFormRules = computed(() =>
  buildInventoryLocationHeadFormRules({
    warehouseId: '库房',
  }),
);

const hasLinkedUpstream = computed(() => {
  if (hasNonEmptyBusNo(headForm.value.originalBusNo)) return true;
  const h = (props.inboundDetailRows?.[0] as Record<string, unknown> | undefined)?.originalBusNo;
  return hasNonEmptyBusNo(h as string | undefined);
});

const linesGridEditable = computed(() => props.documentEditable && hasLinkedUpstream.value);

const canEditOriginalBusNo = computed(() => props.documentEditable && props.isDraftOrder);

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
  const upstreamRequired = props.isDraftOrder && !hasLinkedUpstream.value;
  return {
    ...base,
    saveDisabled: base.saveDisabled || upstreamRequired,
    saveAndSubmitDisabled: base.saveAndSubmitDisabled || upstreamRequired,
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
  const k = opts.selectedBandKind.trim().toUpperCase();
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
  return {
    id: idStr,
    medicinePick: name
      ? {
          id: hasItemId ? (itemIdRaw as number | string) : idStr,
          name,
        }
      : undefined,
    itemTable: r.itemTable === undefined ? undefined : String(r.itemTable),
    itemId: hasItemId ? (itemIdRaw as number | string) : undefined,
    spec: String(r.specification ?? ''),
    manufacturer: String(r.manufacturer ?? ''),
    purchaseQty,
    purchaseUnit,
    unitBands: hasBands ? unitBands : undefined,
    selectedBandKind,
    unitList,
    __prevBandKind: selectedBandKind,
    unitPrice,
    totalAmount:
      total === undefined || total === null || total === ''
        ? ''
        : typeof total === 'number'
          ? formatMoneyDisplay(total)
          : String(total),
    batchNo: String(r.lotNumber ?? ''),
    traceNo: String(r.traceNo ?? '').trim(),
    prodExpiry: mapStartEndToProdExpiry(r.startTime, r.endTime),
    remark: String(r.remake ?? ''),
  };
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
  const bands = normalizeInboundProductUnitBands(pick.unitBands);
  row.unitBands = bands.length > 0 ? bands : undefined;
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
  spec: string;
  manufacturer: string;
  purchaseQty?: number;
  /** 提交用计量单位编码（与当前选中 band 一致） */
  purchaseUnit: string;
  /** 多计量带（选品/详情返回）；无则走 LEGACY 单行占位 */
  unitBands?: InboundProductUnitBand[];
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
    formatter: ({ row }: { row: InboundPreviewRow }) => resolveInboundRowProductName(row, 0),
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
    editRender: { name: 'InventoryTraceNoCell', props: { maxlength: 512, clearable: true } },
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
  '_qtyUnit',
  'batchNo',
  'traceNo',
  'prodExpiry',
  'remark',
  'unitPrice',
]);

const INBOUND_EDIT_FIELD_ORDER = [
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

const parentSync = useInvDocParentSyncSession<InboundDraftSnap>();

/** 将父级已拉详情写入主区（含 loadGen 防异步串扰） */
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
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchInboundGridEditConfig();
    });
    return;
  }
  syncHeadFromDetailRows(rows);
  const gridRows = rows.map((r) => mapDetailRowToGrid(r));
  inboundGridEdit.resetFormMode();
  inboundGridEdit.clearGridEdit();
  void nextTick(() => {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    gridApi?.grid?.loadData(gridRows);
    patchInboundGridEditConfig();
  });
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
      captureDraft: () => ({
        head: { ...headForm.value, busNo: '' },
        rows: collectGridRows().map((r) => ({ ...r })),
      }),
      restoreDraft: async (snap) => {
        headForm.value = { ...snap.head, busNo: '' };
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
        applyPersistedDetailFromParent(gen);
      },
    },
  );
}

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
  () => [props.busNo, props.inboundDetailRows, props.isDraftOrder] as const,
  () => {
    void syncFromParentContext();
  },
  { deep: true, immediate: true },
);

function pickApiErrMsg(e: unknown): string {
  if (e && typeof e === 'object' && 'response' in e) {
    const data = (e as { response?: { data?: { message?: string; msg?: string } } }).response?.data;
    if (data?.msg) return String(data.msg);
    if (data?.message) return String(data.message);
  }
  if (e instanceof Error) return e.message;
  return '保存失败';
}

watch(
  () => headForm.value.itemTableType,
  (newVal, oldVal) => {
    const next = String(newVal ?? '').trim();
    const prev = String(oldVal ?? '').trim();
    if (!prev || next === prev) return;
    const productRows = collectGridRows().filter((r) => isPresentItemId(r.itemId));
    if (productRows.length === 0) return;
    const lockedTable = String(productRows[0]?.itemTable ?? '').trim();
    if (lockedTable && lockedTable !== next) {
      ElMessage.warning('已有明细行，不能切换项目类型；请先删除明细后再切换');
      headForm.value.itemTableType = prev;
    }
  },
);

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
    lines.push({
      itemTable,
      itemId: itemId as number | string,
      itemQuantity: persisted.itemQuantity,
      unitCode,
      lotNumber,
      traceNo: String(r.traceNo ?? '').trim() || undefined,
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
  if (props.isDraftOrder && !hasLinkedUpstream.value) {
    warnInventoryDocRestriction(INV_DOC_UPSTREAM_HINT.purchaseInbound);
    return false;
  }
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
  ElMessage.success('保存成功');
  emit('documentSaved', { busNo });
}

async function handleSaveAndSubmitDoc() {
  const busNo = await persistInbound(true);
  if (!busNo) return;
  ElMessage.success('保存并提交审批成功');
  emit('documentSaved', { busNo });
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
      approvalClock: nowIsoLocal(),
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
      approvalClock: nowIsoLocal(),
    });
    ElMessage.success('已驳回');
    emit('documentChanged', { busNo });
  } catch {
    /* 拦截器已提示 */
  }
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
    prodExpiry: null,
    remark: '',
  };
}

const batchDialogVisible = ref(false);

async function handleAddItem() {
  if (!props.documentEditable) return;
  if (!hasLinkedUpstream.value) {
    warnInventoryDocRestriction(INV_DOC_UPSTREAM_HINT.purchaseInbound);
    return;
  }
  if (!(await validateInventoryHeadFormFields(headFormRef.value, 'warehouseId'))) {
    return;
  }
  await addGridItem();
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
    ElMessage.warning('仅新建单据时可关联订货单');
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
  if (!linesGridEditable.value) {
    warnInventoryDocRestriction(INV_DOC_UPSTREAM_HINT.purchaseInbound);
    return;
  }
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
        v-if="showWelcomeEmpty"
        class="flex min-h-0 flex-1 flex-col items-center justify-center px-4 py-12"
      >
        <el-empty :image-size="96" :description="INV_DOC_UPSTREAM_WELCOME.purchaseInbound">
          <InvPrimaryBtn label="从订货单新建" @click="openUpstreamPicker" />
        </el-empty>
      </div>

      <div
        v-else
        class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-2 pt-2"
        @mousedown="inboundGridEdit.onSurfaceMouseDown"
      >
        <div class="shrink-0">
          <SectionTitle title="进货单信息" />
          <div class="mt-1 flex flex-wrap items-center gap-2">
            <span class="text-xs" style="color: var(--el-text-color-regular)">状态</span>
            <span class="text-sm" style="color: var(--el-text-color-primary)">{{
              inboundHeaderStatusText
            }}</span>
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
                    v-for="w in warehouseOptions"
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
                    placeholder="请选择订货单"
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
            </div>
          </ElForm>
        </div>

        <ElAlert
          v-if="documentEditable && !hasLinkedUpstream"
          type="warning"
          :closable="false"
          show-icon
          class="shrink-0"
          :title="INV_DOC_UPSTREAM_HINT.purchaseInbound"
        />

        <InvDocDetailGridShell
          :bind-grid-wrap="bindGridWrap"
          :on-grid-keydown="inboundGridEdit.onGridKeydown"
        >
          <Grid>
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
                @click="deleteItem(row as InboundPreviewRow)"
              >
                <Trash2 class="size-4" />
                删除
              </ElButton>
            </template>
          </Grid>
        </InvDocDetailGridShell>
      </div>
    </InvMainShell>

    <PurchaseInboundBatchAddDialog
      v-model:visible="batchDialogVisible"
      :pick-gate-location-id="resolveInboundReceiptPickGateLocationId()"
      :product-table-name="headForm.itemTableType"
      @confirm="handleBatchConfirm"
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
