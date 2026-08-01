<!--
  药库出库单右侧主区：InvMainShell + InvDocActions + 单头表单 + useVbenVxeGrid；布局与进货主区同构。
-->
<script setup lang="ts">
import type { TableColumn, FormItemConfig } from '@vben-core/table-layout-ui';

import type { VxeGridPropTypes } from '#/adapter/vxe-table';
import type { PharmacyLocationOptionDto } from '#/api/inventory/pharmacy/requisition';
import type { WarehouseOptionDto } from '#/api/inventory/warehouse/stock-out';
import type { ApiOptionItem } from '#/types/api-response';
import type { InboundProductUnitBand } from '#/utils/inventory/inbound-product-unit-bands';

import type { FormInstance } from 'element-plus';

import { computed, nextTick, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocDetailGridShell from '@inventory/components/inv-doc-detail-grid-shell.vue';
import InvDocGridToolbar from '@inventory/components/inv-doc-grid-toolbar.vue';
import InvDocTraceScanBridge from '@inventory/components/inv-doc-trace-scan-bridge.vue';
import InvDocLinkPickerDialog from '@inventory/components/inv-doc-link-picker-dialog.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
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

import { useInvDocDetailGrid } from '#/composables/use-inv-doc-detail-grid';
import { useInvDocGridRowOps } from '#/composables/use-inv-doc-grid-row-ops';
import { useInvDocParentSyncSession } from '#/composables/use-inv-doc-parent-sync-session';
import {
  agreeStockOutApi,
  createStockOutApi,
  getLinkablePharmacyRequisitionDetailApi,
  getStockOutDetailApi,
  pageLinkablePharmacyRequisitionsApi,
  rejectStockOutApi,
  removeStockOutApi,
  submitStockOutApprovalApi,
  updateStockOutApi,
  withdrawStockOutApprovalApi,
  type StockOutDetailLineDto,
  type StockOutLineDto,
  type StockOutListRowDto,
  type StockOutProductCatalogDto,
} from '#/api/inventory/warehouse/stock-out';
import SectionTitle from '#/components/layouts/section-title.vue';
import StockOutBatchAddDialog from '#/views/inventory/warehouse/stock-out/stock-out-batch-add-dialog.vue';
import {
  formatInventoryLineTotal,
  formatMoneyDisplay,
  inventoryLineTotalNumber,
} from '#/utils/format/money-scale';
import { resolveLoginPractitionerIdForRequest } from '#/utils/format/preserve-api-id';
import {
  INV_ALL_LOCATIONS_FILTER_ID,
  INV_ALL_WAREHOUSES_LABEL,
  isSpecificInventoryLocationId,
  requireSpecificInventoryLocationId,
  resolveFirstRealLocationIdFromInitOptions,
  resolveLoginPractitionerLabelFromUserInfo,
  resolveSpecificInventoryLocationId,
  warnInventoryLocationRequired,
} from '#/utils/inventory/inventory-workbench-list';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import { withInvDocLinkPickerEnrichedColumns } from '#/utils/inventory/inv-doc-link-picker-display';
import { buildInvDocumentTitle, printInvDocument } from '#/utils/print/print-inv-document';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  convertInboundQtyBetweenBandKinds,
  inboundUnitBandSelectOptions,
  normalizeInboundProductUnitBands,
  canConvertDisplayQtyToSplitForBands,
  persistDisplayQtyAsSplitForBands,
  applyWarehouseNonInboundBandKindToRow,
  resolveInboundLinePersistUnitCodeForBands,
  warehouseNonInboundDefaultBandKind,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  isStockOutDocumentEditableStatus,
  isStockOutDocumentRemovableStatus,
  buildStockOutDocActionFlags,
  stockOutStatusLabel,
  normalizeStockOutStatusSort,
} from '#/utils/inventory/stock-out-doc-actions';
import {
  inventoryPickAvailableQtyAsStockDisplay,
  inventoryPickMedicineCell,
  inventoryPickNumberField,
  inventoryPickStringField,
} from '#/utils/inventory/inventory-product-pick-list';
import {
  applyInventoryRowCategoryCode,
  isPresentItemId,
  scheduleInventoryProductPickFocus,
  applyHydratedInventoryDetailQtyToRow,
  hydrateInventoryDetailLineQtyForGrid,
} from '#/utils/inventory/inventory-doc-grid-common';

defineOptions({ name: 'InventoryWarehouseStockOutMainPane' });

interface MedicinePickCell {
  id?: number | string;
  name?: string;
}

/** 出库明细编辑行：与 {@link StockOutLineDto} 字段对齐；仅数量、单位槽可编辑，单价/批号来自选品或详情。 */
interface StockOutboundPreviewRow {
  id: string;
  medicinePick?: MedicinePickCell | Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  /** 选品库存行 id（下拉行唯一键），不落库 */
  inventoryItemId?: number | string;
  /** 药品分类码；中药=4 时单价三位展示 */
  categoryCode?: string;
  spec: string;
  manufacturer: string;
  /** 与后端 {@code StockOutLineDto.itemQuantity} 一致 */
  itemQuantity?: number;
  /** 与后端 {@code StockOutLineDto.unitCode} 一致（当前计量带对应的单位编码） */
  unitCode: string;
  unitBands?: InboundProductUnitBand[];
  selectedBandKind?: string;
  unitList?: { label?: string; value: string; value_dictText?: string }[];
  __prevBandKind?: string;
  /** 与后端 {@code StockOutLineDto.price} 一致，只读 */
  price: number;
  /** 与后端 {@code StockOutLineDto.totalPrice} 一致；详情回显原样，编辑态由 {@link recalcStockOutboundLineTotal} 刷新 */
  totalPrice: string;
  /** 与后端 {@code StockOutLineDto.lotNumber} 一致，由选品/详情带入，只读 */
  lotNumber: string;
  /** 与后端 {@code StockOutLineDto.traceNo} 一致 */
  traceNo: string;
  remark: string;
  /** 详情接口带回，列表不单独开列 */
  _occurrenceTime?: string;
  _reason?: string;
}

interface Props {
  busNo?: string;
  isDraftOrder?: boolean;
  resetKey?: number;
  sidebarSourceLocationId?: number | string;
  sidebarPurposeLocationId?: number | string;
  warehouseOptions: WarehouseOptionDto[];
  pharmacyOptions: PharmacyLocationOptionDto[];
  /** 与进货单 init 的入库项目类型选项同构（选品 productTableName） */
  itemTableTypeOptions?: ApiOptionItem[];
  /** IF-WHS-SO-04/05 经手人 practitionerId（医务人员主键） */
  practitionerId?: number | string;
  /** 侧栏/单头制单时间（index tableForm.applyTime），写入明细 occurrenceTime、提交 applyClock */
  applyTime?: string;
}

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  isDraftOrder: false,
  resetKey: 0,
  sidebarPurposeLocationId: undefined,
  sidebarSourceLocationId: undefined,
  warehouseOptions: () => [],
  pharmacyOptions: () => [],
  itemTableTypeOptions: () => [],
  practitionerId: undefined,
  applyTime: undefined,
});

const emit = defineEmits<{
  createFromRequisition: [row: StockOutListRowDto];
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
  draftHeadSynced: [
    payload: {
      originalBusNo?: string;
      purposeLocationId?: number | string;
      sourceLocationId?: number | string;
    },
  ];
}>();

const userStore = useUserStore();

/** 选品门禁：init 首个真实源药库，与单头源药库解耦。 */
const productPickGateLocationId = computed(() =>
  resolveFirstRealLocationIdFromInitOptions(props.warehouseOptions),
);

function nowIsoLocal(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/** 单头制单时间 → 各明细 occurrenceTime（与后端 StockOutLineDto yyyy-MM-dd HH:mm:ss 对齐） */
const headApplyTime = ref(formatDateTime(new Date()));

function initHeadApplyTimeFromContext() {
  const p = String(props.applyTime ?? '').trim();
  headApplyTime.value = p
    ? p.includes('T')
      ? p.replace('T', ' ')
      : p
    : formatDateTime(new Date());
}

function resolveStockOutOccurrenceTimeForApi(): string {
  const fromHead = String(headApplyTime.value ?? '').trim();
  const fromParent = String(props.applyTime ?? '').trim();
  const t = fromHead || fromParent;
  if (t === '') return formatDateTime(new Date());
  return t.includes('T') ? t.replace('T', ' ') : t;
}

function resolveStockOutApplyClockForSubmit(): string {
  const raw = resolveStockOutOccurrenceTimeForApi();
  return raw.includes('T') ? raw : raw.replace(' ', 'T');
}

/** 库位 id：与后端 Long 字符串一致；仅对安全整数才收敛为 number，避免精度丢失。 */
function normalizeLocationId(v?: number | string | null): number | string | undefined {
  if (!isSpecificInventoryLocationId(v)) return undefined;
  if (typeof v === 'number') return Number.isFinite(v) ? v : undefined;
  const s = String(v).trim();
  if (s === '') return undefined;
  if (!/^\d+$/.test(s)) return s;
  const n = Number(s);
  if (Number.isSafeInteger(n)) return n;
  return s;
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

function firstItemTableTypeValue(): string {
  const o = props.itemTableTypeOptions?.[0];
  return o?.value !== undefined && o?.value !== null && String(o.value) !== ''
    ? String(o.value)
    : 'med_medication_definition';
}

const headerBusNo = ref<string | undefined>(undefined);
const originalBusNo = ref<string | undefined>(undefined);
const detailSourceLocationId = ref<number | string | undefined>(undefined);
const purposeLocationId = ref<number | string | undefined>(undefined);

const headFormRef = ref<FormInstance>();

const headFormModel = computed(() => ({
  detailSourceLocationId: detailSourceLocationId.value ?? '',
  purposeLocationId: purposeLocationId.value ?? '',
}));

const headFormRules = computed(() =>
  buildInventoryLocationHeadFormRules({
    detailSourceLocationId: '源药库',
    purposeLocationId: '目的药房',
  }),
);

const detailStatusEnum = ref<number | undefined>(undefined);

/** 出库选品 {@code productTableName}，与进货单「项目类型」一致 */
const headItemTableType = ref(firstItemTableTypeValue());

const saving = ref(false);

const linkQlVisible = ref(false);
const linkQlLoading = ref(false);
const linkQlQueryParams = ref<{
  keyword: string;
  pageNo: number;
  pageSize: number;
  sourceLocationId: number | string;
}>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  sourceLocationId: INV_ALL_LOCATIONS_FILTER_ID,
});
const linkQlTotal = ref(0);
const linkQlRecords = ref<StockOutListRowDto[]>([]);

const linkQlWarehouseOptions = computed(() => {
  const rows = (props.warehouseOptions ?? [])
    .filter((row) => isSpecificInventoryLocationId(row.locationId))
    .map((row) => ({
      label: String(row.displayName ?? row.locationId ?? ''),
      value: row.locationId as number | string,
    }));
  return [{ label: INV_ALL_WAREHOUSES_LABEL, value: INV_ALL_LOCATIONS_FILTER_ID }, ...rows];
});

function normalizeLinkQlSourceLocationId(raw: unknown): number | string {
  if (Array.isArray(raw)) {
    const first = raw[0];
    if (first === undefined || first === null) return INV_ALL_LOCATIONS_FILTER_ID;
    return first as number | string;
  }
  if (raw === undefined || raw === null || raw === '') return INV_ALL_LOCATIONS_FILTER_ID;
  return raw as number | string;
}

function resolveLinkQlSourceLocationIdForApi(): number | string {
  const wh = resolveSpecificInventoryLocationId(linkQlQueryParams.value.sourceLocationId);
  if (wh) return wh;
  return INV_ALL_LOCATIONS_FILTER_ID;
}

function handleLinkQlSourceLocationChange(value: unknown) {
  linkQlQueryParams.value.sourceLocationId = normalizeLinkQlSourceLocationId(value);
  linkQlQueryParams.value.pageNo = 1;
  void fetchLinkablePharmacyRequisitions();
}

const linkQlFormItems = computed<FormItemConfig[]>(() => [
  {
    label: '源药库',
    prop: 'sourceLocationId',
    type: 'select',
    placeholder: '全部库房',
    filterable: true,
    clearable: false,
    multiple: false,
    options: linkQlWarehouseOptions.value,
    onChange: handleLinkQlSourceLocationChange,
  },
  {
    label: '关键字',
    prop: 'keyword',
    type: 'input',
    placeholder: '请领单号/关键字',
  },
]);

const linkQlTableColumns = computed<TableColumn[]>(() =>
  withInvDocLinkPickerEnrichedColumns(
    [
      { type: 'index', label: '序号', width: 58, align: 'center' },
      { prop: 'busNo', label: '请领单号', minWidth: 160, showOverflowTooltip: true },
      { prop: 'statusEnum', label: '状态', width: 88, slot: 'statusEnum' },
      { prop: 'applyTime', label: '申请时间', minWidth: 160, slot: 'applyTime' },
      { label: '操作', width: 88, align: 'center', fixed: 'right', slot: 'rowActions' },
    ],
    { warehouseLabel: '库位' },
  ),
);

const linkQlLocationOptions = computed(() => [
  ...(props.warehouseOptions as Record<string, unknown>[]),
  ...(props.pharmacyOptions as Record<string, unknown>[]),
]);

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
  return {
    ...buildStockOutDocActionFlags({
      hasPersistedBusNo,
      isDraft: isNewDraft.value,
      status: detailStatusEnum.value,
      busy: saving.value,
    }),
    // 已落库单据才可打印
    printDisabled: saving.value || !hasPersistedBusNo,
  };
});

const canEditOriginalBusNo = computed(() => documentEditable.value && isNewDraft.value);

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

/** 编辑态：按数量 × 单价刷新合计（详情回显不调用，总价以后端为准） */
function recalcStockOutboundLineTotal(row: StockOutboundPreviewRow) {
  const qty = Number(row.itemQuantity);
  const unit = Number(row.price);
  row.totalPrice =
    Number.isFinite(qty) && Number.isFinite(unit) ? formatInventoryLineTotal(qty, unit) : '';
}

/** 切换出库计量带：单位码 + 当前带单价（默认 STOCK） */
function applyStockOutboundBandKindToRow(row: StockOutboundPreviewRow, kind: string) {
  applyWarehouseNonInboundBandKindToRow(row, kind);
}

function handleStockOutboundUnitBandChanged(row: StockOutboundPreviewRow) {
  const newK = String(row.selectedBandKind ?? '')
    .trim()
    .toUpperCase();
  const oldK = String(row.__prevBandKind ?? '')
    .trim()
    .toUpperCase();
  const bands = row.unitBands ?? [];
  if (oldK && newK && oldK !== newK && oldK !== 'LEGACY' && newK !== 'LEGACY' && bands.length > 0) {
    const nq = convertInboundQtyBetweenBandKinds(row.itemQuantity, oldK, newK, bands);
    if (nq !== undefined) {
      row.itemQuantity = nq;
    }
  }
  applyStockOutboundBandKindToRow(row, newK);
  row.__prevBandKind = newK;
  recalcStockOutboundLineTotal(row);
}

/** 与进货单 `syncRowFromInboundMedicinePick` 同逻辑 */
function syncRowFromStockOutboundMedicinePick(
  pick: Record<string, unknown>,
  row: StockOutboundPreviewRow,
) {
  row.medicinePick =
    inventoryPickMedicineCell(pick) ?? (pick as StockOutboundPreviewRow['medicinePick']);
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
  applyInventoryRowCategoryCode(row as unknown as Record<string, unknown>, pick);
  const bands = normalizeInboundProductUnitBands(pick.unitBands);
  row.unitBands = bands.length > 0 ? bands : undefined;
  if (bands.length > 0) {
    const kind = warehouseNonInboundDefaultBandKind(bands);
    row.selectedBandKind = kind;
    row.unitList = inboundUnitBandSelectOptions(bands);
    row.__prevBandKind = kind;
    applyStockOutboundBandKindToRow(row, kind);
  } else {
    row.selectedBandKind = 'LEGACY';
    row.unitList = [{ value: 'LEGACY', label: '单位' }];
    row.__prevBandKind = 'LEGACY';
    row.unitCode = '';
    row.price = inventoryPickNumberField(pick, 'purchaseUnitPrice');
  }
  row.lotNumber = inventoryPickStringField(pick, 'lotNumber');
  const availQty = inventoryPickAvailableQtyAsStockDisplay(pick);
  row.itemQuantity = Number.isFinite(availQty) ? availQty : undefined;
  recalcStockOutboundLineTotal(row);
}

function createEmptyStockOutboundRow(): StockOutboundPreviewRow {
  return {
    id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    medicinePick: undefined,
    itemTable: undefined,
    itemId: undefined,
    inventoryItemId: undefined,
    spec: '',
    manufacturer: '',
    itemQuantity: undefined,
    unitCode: '',
    selectedBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: '单位' }],
    __prevBandKind: 'LEGACY',
    price: 0,
    totalPrice: '',
    lotNumber: '',
    traceNo: '',
    remark: '',
  };
}

/** 详情行展示名：行上 productName 为空时取 catalog.merchandiseName */
function stockOutDetailLineProductName(
  ln: StockOutDetailLineDto,
  catalog?: StockOutProductCatalogDto | null,
): string {
  const top = String(ln.productName ?? '').trim();
  if (top) return top;
  return String(catalog?.merchandiseName ?? '').trim();
}

/** 详情行规格：行上 specification 为空时取 catalog.totalVolume */
function stockOutDetailLineSpecification(
  ln: StockOutDetailLineDto,
  catalog?: StockOutProductCatalogDto | null,
): string {
  const top = String(ln.specification ?? '').trim();
  if (top) return top;
  return String(catalog?.totalVolume ?? '').trim();
}

/** 详情行厂家/产地：行上 manufacturer 为空时暂无可回落字段（由详情接口读模型补齐） */
function stockOutDetailLineManufacturer(ln: StockOutDetailLineDto): string {
  return String(ln.manufacturer ?? '').trim();
}

function formatStockOutDetailTotalAmount(total: unknown): string {
  if (total === undefined || total === null || total === '') return '';
  const n = Number(total);
  if (!Number.isFinite(n)) return String(total).trim();
  return formatMoneyDisplay(n);
}

function mapStockOutDetailLineToGrid(
  ln: StockOutDetailLineDto,
  rowIdx: number,
): StockOutboundPreviewRow {
  const itemIdRaw = ln.itemId;
  const catalog = ln.productCatalog ?? undefined;
  const idStr =
    ln.id !== undefined && ln.id !== null && String(ln.id) !== ''
      ? `ln-${String(ln.id)}`
      : itemIdRaw !== undefined && itemIdRaw !== null
        ? `ln-item-${String(itemIdRaw)}-${rowIdx}`
        : `ln-x-${rowIdx}`;
  const name = stockOutDetailLineProductName(ln, catalog);
  const qty = ln.itemQuantity;
  const unitCodeStr = String(ln.unitCode ?? '');
  const hasItemId =
    itemIdRaw !== undefined &&
    itemIdRaw !== null &&
    !(typeof itemIdRaw === 'string' && itemIdRaw.trim() === '');
  const rawQty = qty === undefined || qty === null ? 0 : Number(qty);
  const unitCodeDictText = String(ln.unitCodeDictText ?? ln.unitCode ?? '');

  const base: StockOutboundPreviewRow = {
    id: idStr,
    medicinePick: name
      ? {
          id: hasItemId ? (itemIdRaw as number | string) : idStr,
          name,
          busNo: catalog?.productBusNo,
        }
      : undefined,
    itemTable: ln.itemTable === undefined ? undefined : String(ln.itemTable),
    itemId: hasItemId ? (itemIdRaw as number | string) : undefined,
    spec: stockOutDetailLineSpecification(ln, catalog),
    manufacturer: stockOutDetailLineManufacturer(ln),
    itemQuantity: rawQty,
    unitCode: unitCodeStr,
    selectedBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: unitCodeStr.trim() === '' ? '单位' : unitCodeStr }],
    __prevBandKind: 'LEGACY',
    price: ln.price === undefined || ln.price === null ? 0 : Number(ln.price),
    totalPrice: formatStockOutDetailTotalAmount(ln.totalPrice),
    lotNumber: String(ln.lotNumber ?? '').trim(),
    traceNo: String(ln.traceNo ?? '').trim(),
    remark: String(ln.remake ?? '').trim(),
    _occurrenceTime: ln.occurrenceTime ?? undefined,
    _reason: ln.reason,
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
  applyHydratedInventoryDetailQtyToRow(base, hydrated);
  if (!base.unitBands?.length && ln.unitCodeDictText) {
    base.unitList = [
      {
        value: 'LEGACY',
        label: String(ln.unitCodeDictText).trim() || unitCodeStr || '单位',
      },
    ];
  }

  return base;
}

function mapDetailLinesToGridRows(lines: StockOutDetailLineDto[]): StockOutboundPreviewRow[] {
  return lines.map((ln, i) => mapStockOutDetailLineToGrid(ln, i));
}

/** 明细行必填项是否已齐（与 validateBeforeSave 保存校验对齐） */
function isStockOutRowComplete(row: StockOutboundPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.itemQuantity);
  if (!Number.isFinite(qty) || qty <= 0) return false;
  if (!String(row.unitCode ?? '').trim()) return false;
  return canConvertDisplayQtyToSplitForBands(row.unitBands ?? [], qty, row.selectedBandKind);
}

/** 选品后进入整行表单并聚焦数量 */
function handleStockOutProductPick(row: StockOutboundPreviewRow) {
  scheduleInventoryProductPickFocus(stockOutGridEdit, row);
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
        listSource: 'stock-out',
        query: async () => ({
          productTableName: headItemTableType.value || undefined,
          sourceLocationId: detailSourceLocationId.value ?? productPickGateLocationId.value,
        }),
        onSelected: (product: Record<string, unknown>, row: StockOutboundPreviewRow) => {
          syncRowFromStockOutboundMedicinePick(product, row);
          handleStockOutProductPick(row);
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
        qtyField: 'itemQuantity',
        unitField: 'selectedBandKind',
        onUnitChange: (row: StockOutboundPreviewRow) => {
          handleStockOutboundUnitBandChanged(row);
        },
      },
    },
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
    field: 'totalPrice',
    title: '总金额',
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
    field: 'traceNo',
    title: '追溯码',
    minWidth: 140,
    editRender: { name: 'InventoryTraceNoCell', props: { maxlength: 512, clearable: true } },
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

const STOCK_OUT_EDITABLE_COLUMN_FIELDS = new Set(['_qtyUnit', 'medicinePick', 'remark', 'traceNo']);

const STOCK_OUT_EDIT_FIELD_ORDER = ['medicinePick', '_qtyUnit', 'traceNo', 'remark'] as const;

type StockOutEditField = (typeof STOCK_OUT_EDIT_FIELD_ORDER)[number];

const STOCK_OUT_VERTICAL_NAV_FIELDS = new Set<StockOutEditField>(['_qtyUnit', 'remark', 'traceNo']);

const {
  Grid,
  gridApi,
  bindGridWrap,
  gridEdit: stockOutGridEdit,
  collectGridRows,
  patchGridEditConfig: patchStockOutGridEditConfig,
} = useInvDocDetailGrid<StockOutboundPreviewRow>({
  columns,
  documentEditable: () => documentEditable.value,
  beforeEditMethod: () => documentEditable.value,
  onEditClosed: (row) => recalcStockOutboundLineTotal(row),
  config: {
    rowKey: (row) => row.id,
    productField: 'medicinePick',
    editFieldOrder: STOCK_OUT_EDIT_FIELD_ORDER,
    editableFields: STOCK_OUT_EDITABLE_COLUMN_FIELDS,
    verticalNavFields: STOCK_OUT_VERTICAL_NAV_FIELDS,
    firstFillFieldAfterPick: '_qtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isStockOutRowComplete,
  },
});

const {
  addItem: addStockOutRow,
  deleteItem,
  removeEmptyRows: removeEmptyStockOutRows,
} = useInvDocGridRowOps({
  documentEditable: () => documentEditable.value,
  getGridApi: () => gridApi ?? null,
  gridEdit: stockOutGridEdit,
  createEmptyRow: createEmptyStockOutboundRow,
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
    if (opts?.length && !String(headItemTableType.value ?? '').trim()) {
      headItemTableType.value = String(opts[0]?.value ?? '');
    }
  },
  { deep: true },
);

watch(
  () => props.resetKey,
  () => {
    parentSync.discardDraftSession();
    stockOutGridEdit.resetFormMode();
    triggerPaneFlash();
  },
);

/** 清空右侧编辑区，准备新建草稿。 */
function resetDetailForCreate() {
  headerBusNo.value = undefined;
  originalBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  detailSourceLocationId.value = undefined;
  purposeLocationId.value = undefined;
  initHeadApplyTimeFromContext();
  clearInventoryHeadFormValidation(headFormRef.value);
  stockOutGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchStockOutGridEditConfig();
  });
}

/** 按单号加载出库单详情并填充表单与明细。 */
async function loadDetail(busNo: string, loadGen?: number) {
  stockOutGridEdit.resetFormMode();
  stockOutGridEdit.clearGridEdit();
  try {
    const res = await getStockOutDetailApi({ busNo });
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到出库单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    originalBusNo.value = data.originalBusNo ?? undefined;
    detailStatusEnum.value = normalizeStockOutStatusSort(data.statusEnum);
    detailSourceLocationId.value = data.sourceLocationId ?? undefined;
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

    const gridRows = mapDetailLinesToGridRows(data.lines ?? []);
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData(gridRows);
      patchStockOutGridEditConfig();
    });
  } catch {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchStockOutGridEditConfig();
    });
  }
}

function composeStockOutRemake(r: StockOutboundPreviewRow): string | undefined {
  const rem = String(r.remark ?? '').trim();
  return rem || undefined;
}

function buildLinesPayload(rows: StockOutboundPreviewRow[]): StockOutLineDto[] {
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
    return {
      itemTable: r.itemTable,
      itemId: r.itemId,
      itemQuantity: persisted.itemQuantity,
      unitCode: unitCode || undefined,
      lotNumber: String(r.lotNumber ?? '').trim() || undefined,
      traceNo: String(r.traceNo ?? '').trim() || undefined,
      inventoryItemId:
        r.inventoryItemId === undefined || r.inventoryItemId === null
          ? undefined
          : (r.inventoryItemId as number | string),
      price: Number.isFinite(unit) ? unit : undefined,
      totalPrice,
      occurrenceTime: resolveStockOutOccurrenceTimeForApi(),
      reason: r._reason?.trim() || undefined,
      remake: composeStockOutRemake(r),
    };
  });
}

function validateBeforeSave(rows: StockOutboundPreviewRow[]): boolean {
  if (!isSpecificInventoryLocationId(detailSourceLocationId.value)) {
    warnInventoryLocationRequired('sourceWarehouse');
    return false;
  }
  if (!isSpecificInventoryLocationId(purposeLocationId.value)) {
    warnInventoryLocationRequired('purposePharmacy');
    return false;
  }
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一行出库明细');
    return false;
  }
  for (const [i, r] of rows.entries()) {
    recalcStockOutboundLineTotal(r);
    const itemTable = String(r.itemTable ?? '').trim();
    if (!itemTable || !isPresentItemId(r.itemId)) {
      ElMessage.warning(`第 ${i + 1} 行请选择项目`);
      return false;
    }
    const qty = Number(r.itemQuantity);
    if (!Number.isFinite(qty) || qty <= 0) {
      ElMessage.warning(`第 ${i + 1} 行数量须大于 0`);
      return false;
    }
    const unitCode = String(r.unitCode ?? '').trim();
    if (!unitCode) {
      ElMessage.warning(`第 ${i + 1} 行请选择单位`);
      return false;
    }
    const bands = r.unitBands ?? [];
    if (!canConvertDisplayQtyToSplitForBands(bands, qty, r.selectedBandKind)) {
      ElMessage.warning(`第 ${i + 1} 行无法将数量换算为拆零单位，请检查计量带`);
      return false;
    }
  }
  return true;
}

/** 保存：新建走 create，已存在走 update；返回当前单号。 */
async function persistStockOut(): Promise<string | undefined> {
  // 重入护栏：须在首行置 busy，避免双击在 await 前并发两次 create
  if (saving.value) return undefined;
  saving.value = true;
  try {
    if (!documentEditable.value) {
      ElMessage.warning('当前状态不允许修改');
      return undefined;
    }
    stockOutGridEdit.clearGridEdit();
    const rows = collectGridRows();
    if (!validateBeforeSave(rows)) return undefined;
    const lines = buildLinesPayload(rows);
    let practitionerId: number | string;
    try {
      practitionerId = coercePractitionerId(props.practitionerId, '经手人');
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '经手人不能为空');
      return undefined;
    }
    const common = {
      sourceLocationId: detailSourceLocationId.value as number | string,
      purposeLocationId: purposeLocationId.value as number | string,
      practitionerId,
      lines,
    };

    if (headerBusNo.value) {
      await updateStockOutApi({
        busNo: headerBusNo.value,
        ...common,
      });
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }
    const ob = originalBusNo.value?.trim();
    const res = await createStockOutApi({
      ...(ob ? { originalBusNo: ob } : {}),
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
  await persistStockOut();
}

async function handleSaveAndSubmitDoc() {
  // 在提交审批阶段继续置灰，避免保存结束后瞬间二次点击再跑一遍 save+submit
  if (saving.value) return;
  const no = await persistStockOut();
  if (!no) return;
  saving.value = true;
  try {
    await handleSubmitApproval();
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
  if (!no) return;
  if (!isStockOutDocumentRemovableStatus(detailStatusEnum.value)) {
    ElMessage.warning('仅待审核、驳回、已撤回单据可删除');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除整张出库单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await removeStockOutApi({ busNo: no });
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
    const res = await submitStockOutApprovalApi({
      busNo: no,
      applyClock: resolveStockOutApplyClockForSubmit(),
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
    const res = await withdrawStockOutApprovalApi({ busNo: no });
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
    await agreeStockOutApi({
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
    await rejectStockOutApi({
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

/** 解析下拉选项展示名 */
function resolvePrintOptionLabel(options: ApiOptionItem[] | undefined, value: unknown): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = options?.find((item) => String(item.value ?? '') === key);
  return String(hit?.label ?? '').trim();
}

/** 解析源药库展示名 */
function resolvePrintWarehouseName(value: unknown): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = (props.warehouseOptions ?? []).find((row) => String(row.locationId ?? '') === key);
  return String(hit?.displayName ?? hit?.locationId ?? '').trim();
}

/** 解析目的药房展示名 */
function resolvePrintPharmacyName(value: unknown): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = (props.pharmacyOptions ?? []).find((row) => String(row.locationId ?? '') === key);
  return String(hit?.displayName ?? hit?.locationId ?? '').trim();
}

/** 明细行项目名称（选品单元格） */
function resolveStockOutPrintProductName(row: StockOutboundPreviewRow): string {
  const pick = row.medicinePick;
  if (pick && typeof pick === 'object' && 'name' in pick) {
    return String((pick as { name?: string }).name ?? '').trim();
  }
  return '';
}

/** 明细行当前计量单位展示文案 */
function resolveStockOutPrintUnitLabel(row: StockOutboundPreviewRow): string {
  const kind = String(row.selectedBandKind ?? '').trim();
  const byKind = row.unitList?.find((item) => String(item.value ?? '') === kind);
  if (byKind) {
    return String(byKind.label ?? byKind.value_dictText ?? '').trim();
  }
  const code = String(row.unitCode ?? '').trim();
  const byCode = row.unitList?.find((item) => String(item.value ?? '') === code);
  return String(byCode?.label ?? byCode?.value_dictText ?? code).trim();
}

/** 打印出库单（计价流转模版） */
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

  const sourceWarehouseName = resolvePrintWarehouseName(detailSourceLocationId.value);
  const purposePharmacyName = resolvePrintPharmacyName(purposeLocationId.value);
  const itemTypeText = resolvePrintOptionLabel(props.itemTableTypeOptions, headItemTableType.value);
  const practitionerName = resolveLoginPractitionerLabelFromUserInfo(
    userStore.userInfo as null | Record<string, unknown>,
  );
  const documentTitle = buildInvDocumentTitle(sourceWarehouseName, '出库单');
  const applyTimeText = String(headApplyTime.value ?? props.applyTime ?? '')
    .trim()
    .replace('T', ' ');

  // 按当前网格展示值组装 PrintData（效期字段出库行无值则留空）
  const lineRows = productRows.map((row) => ({
    productName: resolveStockOutPrintProductName(row),
    specText: String(row.spec ?? '').trim(),
    manufacturerText: String(row.manufacturer ?? '').trim(),
    qty: row.itemQuantity,
    unitLabel: resolveStockOutPrintUnitLabel(row),
    unitPrice: row.price,
    amount: row.totalPrice,
    lotNumber: String(row.lotNumber ?? '').trim(),
    expiryText: '',
  }));

  await printInvDocument({
    family: 'movement',
    source: {
      hospitalName: resolvePrintHospitalName(),
      documentTitle,
      docNo: busNo,
      applyTimeText,
      itemTypeText,
      sourceLocationText: sourceWarehouseName,
      supplierText: '',
      linkDocText: String(originalBusNo.value ?? '').trim(),
      destLocationText: purposePharmacyName,
      invoiceNoText: '',
      practitionerName,
      lineRows,
      preparedByName: practitionerName,
      approvedByName: '',
    },
    bizRef: { busNo, docKind: 'stock-out' },
    jobName: documentTitle,
  });
}

const batchDialogVisible = ref(false);

/** 打开批量选品弹窗（与单行选品共用 sourceLocationId / productTableName） */
function openBatchDialog() {
  if (!documentEditable.value) return;
  if (!requireSpecificInventoryLocationId(detailSourceLocationId.value, 'sourceWarehouse')) {
    return;
  }
  const productTableName = String(headItemTableType.value ?? '').trim();
  if (!productTableName) {
    ElMessage.warning('请先选择项目类型');
    return;
  }
  batchDialogVisible.value = true;
}

/** 批量选品确认：逐条新建行并写入选品信息（等同多次单行选择） */
async function handleBatchConfirm(products: Record<string, unknown>[]) {
  if (!products.length) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  stockOutGridEdit.clearGridEdit();
  await removeEmptyStockOutRows();
  const insertedRows: StockOutboundPreviewRow[] = [];
  for (const product of products) {
    const record = createEmptyStockOutboundRow();
    syncRowFromStockOutboundMedicinePick(product, record);
    const { row } = await $grid.insertAt(record, null);
    if (row) {
      insertedRows.push(row as StockOutboundPreviewRow);
      stockOutGridEdit.markRowFormMode(row as StockOutboundPreviewRow);
    }
  }
  await $grid.$nextTick();
  await nextTick();
  const firstInserted = insertedRows[0];
  if (firstInserted && isPresentItemId(firstInserted.itemId)) {
    await $grid.scrollToRow(firstInserted);
    await stockOutGridEdit.enterRowFormMode(firstInserted, '_qtyUnit');
  }
  ElMessage.success(`已添加 ${products.length} 条药品，请补充数量等信息`);
}

/** 打开可关联请领单弹窗（无需已在草稿；未在草稿时选用会 emit createFromRequisition） */
function openUpstreamPicker() {
  linkQlQueryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    sourceLocationId: INV_ALL_LOCATIONS_FILTER_ID,
  };
  linkQlRecords.value = [];
  linkQlTotal.value = 0;
  linkQlVisible.value = true;
}

/** 单头「从请领单选择/更换」：仅新建草稿可改关联 */
function openLinkQlDialog() {
  if (!canEditOriginalBusNo.value) {
    ElMessage.warning('仅新建单据时可关联请领单');
    return;
  }
  openUpstreamPicker();
}

/** 关联请领单弹窗：查询 */
function handleLinkQlQuery(params: Record<string, unknown>) {
  Object.assign(linkQlQueryParams.value, params);
  linkQlQueryParams.value.sourceLocationId = normalizeLinkQlSourceLocationId(
    linkQlQueryParams.value.sourceLocationId,
  );
  linkQlQueryParams.value.pageNo = 1;
  void fetchLinkablePharmacyRequisitions();
}

/** 关联请领单弹窗：重置 */
function handleLinkQlReset() {
  linkQlQueryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    sourceLocationId: INV_ALL_LOCATIONS_FILTER_ID,
  };
  void fetchLinkablePharmacyRequisitions();
}

/** 关联请领单弹窗：分页 */
function handleLinkQlPagination(pagination: { limit: number; page: number }) {
  linkQlQueryParams.value.pageNo = pagination.page;
  linkQlQueryParams.value.pageSize = pagination.limit;
  void fetchLinkablePharmacyRequisitions();
}

async function fetchLinkablePharmacyRequisitions() {
  linkQlQueryParams.value.sourceLocationId = normalizeLinkQlSourceLocationId(
    linkQlQueryParams.value.sourceLocationId,
  );
  linkQlLoading.value = true;
  try {
    const res = await pageLinkablePharmacyRequisitionsApi({
      sourceLocationId: resolveLinkQlSourceLocationIdForApi(),
      keyword: String(linkQlQueryParams.value.keyword ?? '').trim() || undefined,
      pageNo: linkQlQueryParams.value.pageNo,
      pageSize: linkQlQueryParams.value.pageSize,
    });
    const { content, totalCount } = parsePagedRowsFromApiData<StockOutListRowDto>(res.data);
    linkQlRecords.value = content;
    linkQlTotal.value = totalCount;
  } catch {
    linkQlRecords.value = [];
    linkQlTotal.value = 0;
  } finally {
    linkQlLoading.value = false;
  }
}

async function applyLinkablePharmacyRequisition(row: StockOutListRowDto) {
  if (!props.isDraftOrder) {
    emit('createFromRequisition', row);
    return;
  }
  await applyLinkablePharmacyRequisitionToDraft(row);
}

async function applyLinkablePharmacyRequisitionToDraft(row: StockOutListRowDto) {
  const qlNo = row.busNo?.trim();
  if (!qlNo) return;
  try {
    const res = await getLinkablePharmacyRequisitionDetailApi({
      busNo: qlNo,
      excludeStockOutBusNo: headerBusNo.value?.trim() || undefined,
    });
    const data = res.data;
    if (!data?.lines?.length) {
      ElMessage.warning('未获取到请领明细');
      return;
    }
    originalBusNo.value = data.originalBusNo ?? data.requisitionBusNo ?? qlNo;
    if (data.sourceLocationId !== undefined && data.sourceLocationId !== null) {
      detailSourceLocationId.value = data.sourceLocationId;
    }
    if (data.purposeLocationId !== undefined && data.purposeLocationId !== null) {
      purposeLocationId.value = data.purposeLocationId;
    }
    const gridRows = (data.lines ?? []).map((ln, i) =>
      mapStockOutDetailLineToGrid(ln as StockOutDetailLineDto, i),
    );
    stockOutGridEdit.resetFormMode();
    stockOutGridEdit.clearGridEdit();
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchStockOutGridEditConfig();
    });
    linkQlVisible.value = false;
    emit('draftHeadSynced', {
      sourceLocationId: detailSourceLocationId.value,
      purposeLocationId: purposeLocationId.value,
      originalBusNo: originalBusNo.value,
    });
    ElMessage.success('已带入请领明细，请核对数量后保存');
  } catch {
    /* handled */
  }
}

defineExpose({
  applyLinkablePharmacyRequisitionToDraft,
  openUpstreamPicker,
});

async function addItem() {
  if (!documentEditable.value) return;
  if (
    !(await validateInventoryHeadFormFields(headFormRef.value, [
      'detailSourceLocationId',
      'purposeLocationId',
    ]))
  ) {
    return;
  }
  await addStockOutRow();
}

interface StockOutDraftSnap {
  detailSourceLocationId?: number | string;
  purposeLocationId?: number | string;
  originalBusNo?: string;
  headApplyTime: string;
  headItemTableType: string;
  rows: StockOutboundPreviewRow[];
}

const parentSync = useInvDocParentSyncSession<StockOutDraftSnap>();

/** 侧栏切单：缓存/恢复本地草稿，避免已落库明细污染新建单 */
async function syncFromParentContext() {
  await parentSync.syncFromParentContext(
    { isDraftOrder: Boolean(props.isDraftOrder), parentBusNo: props.busNo },
    {
      getLocalPersistedBusNo: () => headerBusNo.value,
      captureDraft: () => ({
        detailSourceLocationId: detailSourceLocationId.value,
        purposeLocationId: purposeLocationId.value,
        originalBusNo: originalBusNo.value,
        headApplyTime: headApplyTime.value,
        headItemTableType: headItemTableType.value,
        rows: collectGridRows().map((r) => ({ ...r })),
      }),
      restoreDraft: async (snap) => {
        headerBusNo.value = undefined;
        detailStatusEnum.value = undefined;
        detailSourceLocationId.value = snap.detailSourceLocationId;
        purposeLocationId.value = snap.purposeLocationId;
        originalBusNo.value = snap.originalBusNo;
        headApplyTime.value = snap.headApplyTime;
        headItemTableType.value = snap.headItemTableType;
        stockOutGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
        patchStockOutGridEditConfig();
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
  <div
    class="stock-out-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
    :class="{ 'stock-out-main-pane--flash': paneFlashActive }"
  >
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
        @mousedown="stockOutGridEdit.onSurfaceMouseDown"
      >
        <div class="shrink-0">
          <SectionTitle title="出库单信息" />
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
            label-width="88px"
            class="compact-head-form stock-out-head-form mt-1"
          >
            <div
              class="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5"
            >
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem label="源药库" prop="detailSourceLocationId">
                <ElSelect
                  v-model="detailSourceLocationId"
                  placeholder="出库源库房"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="w in warehouseOptions"
                    :key="String(w.locationId)"
                    :label="w.displayName ?? String(w.locationId)"
                    :value="w.locationId"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="目的药房" prop="purposeLocationId">
                <ElSelect
                  v-model="purposeLocationId"
                  placeholder="调入药房"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="p in pharmacyOptions"
                    :key="String(p.locationId)"
                    :label="p.displayName ?? String(p.locationId)"
                    :value="p.locationId"
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
              <ElFormItem label="项目类型">
                <ElSelect
                  v-model="headItemTableType"
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
              <ElFormItem
                label="关联请领"
                class="stock-out-head-form__ql sm:col-span-2 lg:col-span-2 xl:col-span-2"
              >
                <div class="stock-out-head-form__ql-inner flex min-w-0 items-center gap-2">
                  <ElInput
                    v-model="originalBusNo"
                    class="min-w-0 flex-1"
                    clearable
                    maxlength="64"
                    placeholder="可选，已同意的请领单号"
                    :disabled="!canEditOriginalBusNo"
                  />
                  <ElButton
                    class="shrink-0"
                    :disabled="!canEditOriginalBusNo"
                    @click="openLinkQlDialog"
                  >
                    从请领单选择
                  </ElButton>
                </div>
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <InvDocDetailGridShell
          :bind-grid-wrap="bindGridWrap"
          :on-grid-keydown="stockOutGridEdit.onGridKeydown"
        >
          <Grid class="min-h-0 flex-1">
            <template #toolbar-actions>
              <InvDocGridToolbar
                show-batch-add
                show-trace-scan
                :disabled="!documentEditable"
                @add="addItem"
                @batch-add="openBatchDialog"
                @delete-selected="deleteItem('select')"
                @trace-scan="handleOpenTraceScan"
              />
            </template>
            <template #action="{ row }">
              <ElButton
                type="danger"
                text
                :disabled="!documentEditable"
                @click="deleteItem(row as StockOutboundPreviewRow)"
              >
                <Trash2 class="size-4" />
                删除
              </ElButton>
            </template>
          </Grid>
        </InvDocDetailGridShell>
      </div>
    </InvMainShell>

    <StockOutBatchAddDialog
      v-model:visible="batchDialogVisible"
      :source-location-id="detailSourceLocationId"
      :product-table-name="headItemTableType"
      @confirm="handleBatchConfirm"
    />

    <InvDocLinkPickerDialog
      v-model="linkQlVisible"
      title="选择已同意的药房请领单"
      :table-data="linkQlRecords"
      :loading="linkQlLoading"
      :total="linkQlTotal"
      :query-params="linkQlQueryParams"
      :form-items="linkQlFormItems"
      :table-columns="linkQlTableColumns"
      :location-options="linkQlLocationOptions"
      @opened="fetchLinkablePharmacyRequisitions"
      @query="handleLinkQlQuery"
      @reset="handleLinkQlReset"
      @pagination="handleLinkQlPagination"
      @select="(row) => applyLinkablePharmacyRequisition(row as StockOutListRowDto)"
    >
      <template #statusEnum="{ row }">
        {{ stockOutStatusLabel((row as StockOutListRowDto).statusEnum) }}
      </template>
      <template #applyTime="{ row }">
        {{
          (row as StockOutListRowDto).applyTime
            ? formatDateTime((row as StockOutListRowDto).applyTime as string)
            : ''
        }}
      </template>
      <template #rowActions="{ row }">
        <ElButton
          link
          type="primary"
          @click.stop="applyLinkablePharmacyRequisition(row as StockOutListRowDto)"
        >
          选用
        </ElButton>
      </template>
    </InvDocLinkPickerDialog>
  </div>
  <InvDocTraceScanBridge
    ref="traceScanBridgeRef"
    scene="inventoryOut"
    :default-location-id="detailSourceLocationId"
  />
</template>

<style scoped lang="scss">
.stock-out-head-form__ql {
  margin-bottom: 0;
}

.stock-out-head-form__ql-inner {
  width: 100%;
  max-width: 22rem;
}

.compact-head-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.compact-head-form :deep(.el-form-item__label) {
  padding-right: 8px;
}

@keyframes stock-out-pane-flash {
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

.stock-out-main-pane--flash {
  animation: stock-out-pane-flash 0.42s ease-out;
}
</style>
