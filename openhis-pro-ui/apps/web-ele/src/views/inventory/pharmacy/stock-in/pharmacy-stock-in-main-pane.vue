<!--
  药房入库单右侧主区：与药库出库单同构；契约对齐 AS-INV-PHA-001-SDD（枚举以领域 getCode() 字符串提交）。
-->
<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { WarehouseOptionDto } from '#/api/inventory/pharmacy/stock-in';
import type { ApiOptionItem } from '#/types/api-response';
import type { InboundProductUnitBand } from '#/utils/inventory/inbound-product-unit-bands';
import type { FormInstance } from 'element-plus';

import { computed, nextTick, reactive, ref, watch, onBeforeUnmount, onDeactivated } from 'vue';

import { Trash2 } from '@vben/icons';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocGridToolbar from '@inventory/components/inv-doc-grid-toolbar.vue';
import InvDocTraceScanBridge from '@inventory/components/inv-doc-trace-scan-bridge.vue';
import InvDocLinkPickerDialog from '@inventory/components/inv-doc-link-picker-dialog.vue';
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
  getLinkablePharmacyRequisitionDetailApi,
  pageLinkablePharmacyRequisitionsApi,
  type StockOutDetailLineDto,
  type StockOutListRowDto,
} from '#/api/inventory/warehouse/stock-out';
import {
  PHA_STOCK_IN_CATEGORY_ENUM,
  PHA_STOCK_IN_PURPOSE_TYPE_ENUM,
  PHA_STOCK_IN_SOURCE_TYPE_ENUM,
  agreePharmacyStockInApi,
  createPharmacyStockInApi,
  getPharmacyStockInDetailApi,
  rejectPharmacyStockInApi,
  removePharmacyStockInApi,
  submitPharmacyStockInApprovalApi,
  updatePharmacyStockInApi,
  withdrawPharmacyStockInApprovalApi,
  type PharmacyStockInDetailLineDto,
  type PharmacyStockInLineDto,
} from '#/api/inventory/pharmacy/stock-in';
import SectionTitle from '#/components/layouts/section-title.vue';
import {
  formatInventoryLineTotal,
  formatPurchaseMoneyDisplay,
  inventoryLineTotalNumber,
} from '#/utils/format/money-scale';
import { resolveLoginPractitionerIdForRequest } from '#/utils/format/preserve-api-id';
import {
  requireAnySpecificInventoryLocationId,
  resolveFirstRealLocationIdFromOptionItems,
  resolveSpecificInventoryLocationId,
  resolveInvDocPreparedByName,
  resolveInvDocApprovedByName,
} from '#/utils/inventory/inventory-workbench-list';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import { withInvDocLinkPickerEnrichedColumns } from '#/utils/inventory/inv-doc-link-picker-display';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { INV_DOC_READONLY_SHELL_HINT } from '#/utils/inventory/inventory-doc-restrictions';
import {
  applyInventoryRowCategoryCode,
  applyWarehouseNonInboundProductPickUnitsToRow,
  handleWarehouseNonInboundUnitBandChanged,
} from '#/utils/inventory/inventory-doc-grid-common';
import {
  canConvertDisplayQtyToSplitForBands,
  findInboundUnitBandByKind,
  hydrateWarehouseSupplyLineQtyForGrid,
  inboundUnitBandSelectOptions,
  normalizeInboundProductUnitBands,
  persistDisplayQtyAsSplitForBands,
  resolveInboundLinePersistUnitCodeForBands,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  inventoryPickMedicineCell,
  inventoryPickNumberField,
  inventoryPickStringField,
} from '#/utils/inventory/inventory-product-pick-list';
import {
  buildPharmacyStockInDocActionFlags,
  normalizePharmacyStockInStatusSort,
  pharmacyStockInStatusLabel,
} from '#/utils/inventory/pharmacy-stock-in-doc-actions';
import { isStockOutDocumentRemovableStatus } from '#/utils/inventory/stock-out-doc-actions';
import {
  buildInvDocumentTitle,
  printInvDocument,
  resolveInvPrintProductBusNo,
} from '#/utils/print/print-inv-document';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

defineOptions({ name: 'InventoryPharmacyStockInMainPane' });

interface MedicinePickCell {
  id?: number | string;
  name?: string;
}

/** 入库明细编辑行：与 {@link PharmacyStockInLineDto} / 详情行字段对齐；与出库页同构。 */
interface PharmacyStockInPreviewRow {
  id: string;
  medicinePick?: MedicinePickCell | Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  /** 选品库存行 id（下拉行唯一键），不落库 */
  inventoryItemId?: number | string;
  /** 药品分类码；中药=4 时进价三位展示 */
  categoryCode?: string;
  spec: string;
  manufacturer: string;
  /** 与后端 {@code PharmacyStockInLineDto.itemQuantity} 一致 */
  itemQuantity: number;
  /** 与后端 {@code PharmacyStockInLineDto.unitCode} 一致（当前计量带对应的单位编码） */
  unitCode: string;
  unitBands?: InboundProductUnitBand[];
  selectedBandKind?: string;
  unitList?: { label?: string; value: string; value_dictText?: string }[];
  __prevBandKind?: string;
  /** 与后端 {@code PharmacyStockInLineDto.price} 一致，只读 */
  price: number;
  /** 与后端 {@code PharmacyStockInLineDto.totalPrice} 对应的展示字符串（由数量×单价计算） */
  totalPrice: string;
  /** 与后端 {@code PharmacyStockInLineDto.lotNumber} 一致，由选品/详情带入，只读 */
  lotNumber: string;
  /** 与后端 {@code PharmacyStockInLineDto.traceNo} 一致 */
  traceNo: string;
  remark: string;
  /** 详情接口带回，列表不单独开列 */
  _occurrenceTime?: string;
  _reason?: string;
}

interface Props {
  /** 侧栏当前单据行（制单人/审批人与队列卡片同口径） */
  queueCardRow?: null | Record<string, unknown>;
  busNo?: string;
  isDraftOrder?: boolean;
  /** 侧栏草稿稳定 id（_clientDraftId），用于 session 缓存隔离 */
  clientDraftId?: string;
  resetKey?: number;
  sidebarSourceLocationId?: number | string;
  sidebarPurposeLocationId?: number | string;
  warehouseOptions: WarehouseOptionDto[];
  pharmacyOptions: WarehouseOptionDto[];
  /** 与进货单 init 的入库项目类型选项同构（选品 productTableName） */
  itemTableTypeOptions?: ApiOptionItem[];
}

const props = withDefaults(defineProps<Props>(), {
  queueCardRow: null,
  busNo: '',
  isDraftOrder: false,
  clientDraftId: '',
  resetKey: 0,
  sidebarPurposeLocationId: undefined,
  sidebarSourceLocationId: undefined,
  warehouseOptions: () => [],
  pharmacyOptions: () => [],
  itemTableTypeOptions: () => [],
});

const emit = defineEmits<{
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

const userStore = useUserStore();

/** 选品门禁：init 首个真实源药库，与侧栏列表筛选解耦。 */
const productPickGateLocationId = computed(() =>
  resolveFirstRealLocationIdFromOptionItems(
    props.warehouseOptions as { locationId?: unknown; value?: unknown }[],
  ),
);

function nowIsoLocal(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/** 库位 id：与后端 Long 字符串一致；仅对安全整数才收敛为 number，避免精度丢失。 */
function normalizeLocationId(v?: number | string | null): number | string | undefined {
  if (v === undefined || v === null || v === '') return undefined;
  if (typeof v === 'number') return Number.isFinite(v) ? v : undefined;
  const s = String(v).trim();
  if (s === '') return undefined;
  if (!/^\d+$/.test(s)) return s;
  const n = Number(s);
  if (Number.isSafeInteger(n)) return n;
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

/** 入库选品 {@code productTableName}，与进货单「项目类型」一致 */
const headItemTableType = ref(firstItemTableTypeValue());

const saving = ref(false);

const linkQlVisible = ref(false);
const linkQlLoading = ref(false);
const linkQlQueryParams = ref({ pageNo: 1, pageSize: 20, keyword: '' });
const linkQlTotal = ref(0);
const linkQlRecords = ref<StockOutListRowDto[]>([]);

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

/** 壳单只读：由药库出库 agree 自动生成，禁止改单 */
const isReadOnlyShell = computed(() => true);
const documentEditable = computed(() => false);

const headerStatusText = computed(() => {
  if (isNewDraft.value) return '编辑中';
  return pharmacyStockInStatusLabel(detailStatusEnum.value);
});

const docActionFlags = computed(() => {
  const hasPersistedBusNo = Boolean(headerBusNo.value);
  const base = buildPharmacyStockInDocActionFlags({
    hasPersistedBusNo,
    isDraft: isNewDraft.value,
    status: detailStatusEnum.value,
  });
  const busy = saving.value;
  // 只读壳（药库出库自动生成）：仅开放打印
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

/** 详情回显：持久化 total_price 原样格式化。 */
function formatPharmacyStockInDetailTotalPrice(total: unknown, categoryCode?: unknown): string {
  if (total === undefined || total === null || total === '') return '';
  const n = Number(total);
  if (!Number.isFinite(n)) return String(total).trim();
  return formatPurchaseMoneyDisplay(n, categoryCode);
}

/** 编辑态：按数量 × 单价刷新合计（详情 load 不调用）。 */
function recalcPharmacyStockInLineTotal(row: PharmacyStockInPreviewRow) {
  const qty = Number(row.itemQuantity);
  const unit = Number(row.price);
  row.totalPrice =
    Number.isFinite(qty) && Number.isFinite(unit) ? formatInventoryLineTotal(qty, unit) : '';
}

function applyPharmacyStockInBandKindToRow(row: PharmacyStockInPreviewRow, kind: string) {
  const k = kind.trim().toUpperCase();
  if (k === 'LEGACY' || !row.unitBands?.length) {
    return;
  }
  const b = findInboundUnitBandByKind(row.unitBands, k);
  if (!b) {
    return;
  }
  const code = String(b.unitCode ?? '').trim();
  if (code !== '') {
    row.unitCode = code;
  }
  const up = b.unitPrice;
  if (up !== undefined && up !== null && String(up) !== '') {
    const n = Number(up);
    if (Number.isFinite(n)) {
      row.price = n;
    }
  }
}

function handlePharmacyStockInUnitBandChanged(row: PharmacyStockInPreviewRow) {
  handleWarehouseNonInboundUnitBandChanged(row, {
    getQty: () => Number(row.itemQuantity),
    setQty: (qty) => {
      row.itemQuantity = qty;
    },
    onAfterChanged: () => recalcPharmacyStockInLineTotal(row),
  });
}

/** 选品回填：默认 STOCK 计量带（INV 改造清单 §1.1） */
function syncRowFromPharmacyStockInMedicinePick(
  pick: Record<string, unknown>,
  row: PharmacyStockInPreviewRow,
) {
  row.medicinePick =
    inventoryPickMedicineCell(pick) ?? (pick as PharmacyStockInPreviewRow['medicinePick']);
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
  applyWarehouseNonInboundProductPickUnitsToRow(pick, row, {
    legacyUnitPrice: inventoryPickNumberField(pick, 'purchaseUnitPrice'),
  });
  row.lotNumber = inventoryPickStringField(pick, 'lotNumber');
  row.traceNo = inventoryPickStringField(pick, 'traceNo');
  recalcPharmacyStockInLineTotal(row);
}

function createEmptyPharmacyStockInRow(): PharmacyStockInPreviewRow {
  return {
    id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    medicinePick: undefined,
    itemTable: undefined,
    itemId: undefined,
    inventoryItemId: undefined,
    spec: '',
    manufacturer: '',
    itemQuantity: 0,
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

function mapPharmacyStockInDetailLineToGrid(
  ln: PharmacyStockInDetailLineDto,
  rowIdx: number,
): PharmacyStockInPreviewRow {
  const itemIdRaw = ln.itemId;
  const idStr =
    ln.id !== undefined && ln.id !== null && String(ln.id) !== ''
      ? `ln-${ln.id}`
      : itemIdRaw !== undefined && itemIdRaw !== null
        ? `ln-item-${String(itemIdRaw)}-${rowIdx}`
        : `ln-x-${rowIdx}`;
  const name = String(ln.productName ?? '');
  const qty = ln.itemQuantity;
  const unitCodeStr = String(ln.unitCode ?? '');
  const hasItemId =
    itemIdRaw !== undefined &&
    itemIdRaw !== null &&
    !(typeof itemIdRaw === 'string' && itemIdRaw.trim() === '');
  const catalog = ln.productCatalog as Record<string, unknown> | null | undefined;
  const bands = normalizeInboundProductUnitBands(catalog?.unitBands, catalog?.unitBandsJson);

  const base: PharmacyStockInPreviewRow = {
    id: idStr,
    medicinePick: name
      ? {
          id: hasItemId ? (itemIdRaw as number | string) : idStr,
          name,
          ...(String(catalog?.productBusNo ?? '').trim()
            ? { busNo: String(catalog?.productBusNo ?? '').trim() }
            : {}),
        }
      : undefined,
    itemTable: ln.itemTable === undefined ? undefined : String(ln.itemTable),
    itemId: hasItemId ? (itemIdRaw as number | string) : undefined,
    spec: String(ln.specification ?? ''),
    manufacturer: String(ln.manufacturer ?? ''),
    itemQuantity: qty === undefined || qty === null ? 0 : Number(qty),
    unitCode: unitCodeStr,
    selectedBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: unitCodeStr.trim() === '' ? '单位' : unitCodeStr }],
    __prevBandKind: 'LEGACY',
    price: ln.price === undefined || ln.price === null ? 0 : Number(ln.price),
    totalPrice: formatPharmacyStockInDetailTotalPrice(ln.totalPrice, catalog?.categoryCode),
    lotNumber: String(ln.lotNumber ?? '').trim(),
    traceNo: String(ln.traceNo ?? '').trim(),
    remark: String(ln.remake ?? ''),
    _occurrenceTime: ln.occurrenceTime,
    _reason: ln.reason,
  };

  if (bands.length > 0) {
    base.unitBands = bands;
    const rawQty = qty === undefined || qty === null ? 0 : Number(qty);
    const hydrated = hydrateWarehouseSupplyLineQtyForGrid({
      bands,
      persistedItemQuantity: rawQty,
      persistedUnitCode: unitCodeStr,
    });
    base.itemQuantity = hydrated.displayQty;
    base.unitCode = hydrated.unitCode;
    base.selectedBandKind = hydrated.bandKind;
    base.unitList = inboundUnitBandSelectOptions(bands);
    base.__prevBandKind = hydrated.bandKind;
    applyPharmacyStockInBandKindToRow(base, hydrated.bandKind);
    const lnPrice = ln.price === undefined || ln.price === null ? undefined : Number(ln.price);
    if (lnPrice !== undefined && Number.isFinite(lnPrice)) {
      base.price = lnPrice;
    }
  } else if (ln.unitCodeDictText) {
    base.unitList = [
      {
        value: 'LEGACY',
        label: String(ln.unitCodeDictText).trim() || unitCodeStr || '单位',
      },
    ];
  }

  applyInventoryRowCategoryCode(base as unknown as Record<string, unknown>, {
    productCatalog: catalog ?? undefined,
    categoryCode: catalog?.categoryCode,
  });
  return base;
}

function mapDetailLinesToGridRows(
  lines: PharmacyStockInDetailLineDto[],
): PharmacyStockInPreviewRow[] {
  return lines.map((ln, i) => mapPharmacyStockInDetailLineToGrid(ln, i));
}

/** 从已同意的药房请领单明细生成可编辑行（请领行无计量带时走 LEGACY）。 */
function mapLinkableRequisitionLineToPharmacyGrid(
  ln: StockOutDetailLineDto,
  rowIdx: number,
): PharmacyStockInPreviewRow {
  return mapPharmacyStockInDetailLineToGrid(ln as unknown as PharmacyStockInDetailLineDto, rowIdx);
}

/** 明细行必填项是否已齐（与 validateBeforeSave 保存校验对齐） */
function isPharmacyStockInRowComplete(row: PharmacyStockInPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.itemQuantity);
  if (!Number.isFinite(qty) || qty <= 0) return false;
  const unitCode = String(row.unitCode ?? '').trim();
  if (!unitCode) return false;
  const bands = row.unitBands ?? [];
  if (!canConvertDisplayQtyToSplitForBands(bands, qty, row.selectedBandKind)) return false;
  return true;
}

let pharmacyStockInGridEdit!: ReturnType<typeof useInventoryDocGridEdit<PharmacyStockInPreviewRow>>;

function handlePharmacyStockInProductPick(row: PharmacyStockInPreviewRow) {
  void nextTick(async () => {
    await nextTick();
    await pharmacyStockInGridEdit.onProductSelected(row);
  });
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
        listSource: 'pharmacy-stock-in',
        query: async () => ({
          productTableName: headItemTableType.value || undefined,
          sourceLocationId: detailSourceLocationId.value ?? productPickGateLocationId.value,
        }),
        onSelected: (product: Record<string, unknown>, row: PharmacyStockInPreviewRow) => {
          syncRowFromPharmacyStockInMedicinePick(product, row);
          handlePharmacyStockInProductPick(row);
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
        onUnitChange: (row: PharmacyStockInPreviewRow) => {
          handlePharmacyStockInUnitBandChanged(row);
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
    minWidth: 100,
    editRender: {
      name: 'InventoryTraceNoCell',
      props: { maxlength: 64, clearable: true },
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

const gridOptions: VxeGridProps<PharmacyStockInPreviewRow> = reactive({
  autoResize: true,
  height: '100%',
  border: true,
  stripe: true,
  showOverflow: true,
  showFooter: false,
  size: 'medium',
  keepSource: true,
  columnConfig: { resizable: true },
  data: [],
  editConfig: {
    enabled: true,
    trigger: 'click',
    mode: 'row',
    autoClear: false,
    beforeEditMethod: () => documentEditable.value,
  },
  columns,
  pagerConfig: { enabled: false },
});

const PHARMACY_STOCK_IN_EDITABLE_COLUMN_FIELDS = new Set([
  '_qtyUnit',
  'medicinePick',
  'remark',
  'traceNo',
]);

const PHARMACY_STOCK_IN_EDIT_FIELD_ORDER = [
  'medicinePick',
  '_qtyUnit',
  'traceNo',
  'remark',
] as const;

type PharmacyStockInEditField = (typeof PHARMACY_STOCK_IN_EDIT_FIELD_ORDER)[number];

const PHARMACY_STOCK_IN_VERTICAL_NAV_FIELDS = new Set<PharmacyStockInEditField>([
  '_qtyUnit',
  'remark',
  'traceNo',
]);

const pharmacyStockInGridKeyboardRef = ref<HTMLElement | null>(null);

const [Grid, gridApi] = useVbenVxeGrid<PharmacyStockInPreviewRow>({
  gridOptions,
  gridEvents: {
    cellClick: (params: {
      column?: { field?: string; type?: string };
      row: PharmacyStockInPreviewRow;
    }) => pharmacyStockInGridEdit.onCellClick(params),
    editClosed(params: { row: PharmacyStockInPreviewRow }) {
      recalcPharmacyStockInLineTotal(params.row);
      pharmacyStockInGridEdit.syncRowFormMode(params.row);
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

pharmacyStockInGridEdit = useInventoryDocGridEdit<PharmacyStockInPreviewRow>({
  getGridApi: () => gridApi ?? null,
  gridWrapRef: pharmacyStockInGridKeyboardRef,
  documentEditable: () => documentEditable.value,
  collectRows: collectGridRows,
  config: {
    rowKey: (row) => row.id,
    productField: 'medicinePick',
    editFieldOrder: PHARMACY_STOCK_IN_EDIT_FIELD_ORDER,
    editableFields: PHARMACY_STOCK_IN_EDITABLE_COLUMN_FIELDS,
    verticalNavFields: PHARMACY_STOCK_IN_VERTICAL_NAV_FIELDS,
    firstFillFieldAfterPick: '_qtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isPharmacyStockInRowComplete,
  },
});

function patchPharmacyStockInGridEditConfig() {
  const patched = pharmacyStockInGridEdit.patchGridOptions({
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
    patchPharmacyStockInGridEditConfig();
    void nextTick(() => {
      if (!editable) {
        pharmacyStockInGridEdit.resetFormMode();
        pharmacyStockInGridEdit.clearGridEdit();
      }
    });
  },
  { immediate: true },
);

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
    triggerPaneFlash();
  },
);

function isPresentItemId(v: unknown): boolean {
  if (v === undefined || v === null) return false;
  if (typeof v === 'string') return v.trim() !== '';
  if (typeof v === 'number') return Number.isFinite(v);
  if (typeof v === 'bigint') return true;
  return String(v).trim() !== '';
}

function collectGridRows(): PharmacyStockInPreviewRow[] {
  const g = gridApi?.grid as null | { getFullData?: () => PharmacyStockInPreviewRow[] };
  return g?.getFullData?.() ?? [];
}

const traceScanBridgeRef = ref<InstanceType<typeof InvDocTraceScanBridge> | null>(null);

/** 打开批量追溯码扫码弹窗 */
function handleOpenTraceScan(): void {
  traceScanBridgeRef.value?.open(collectGridRows());
}

/** 清空右侧编辑区，准备新建草稿。 */
function resetDetailForCreate() {
  headerBusNo.value = undefined;
  originalBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  detailSourceLocationId.value = undefined;
  purposeLocationId.value = undefined;
  clearInventoryHeadFormValidation(headFormRef.value);
  pharmacyStockInGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchPharmacyStockInGridEditConfig();
  });
}

/** 按单号加载入库单详情并填充表单与明细。 */
async function loadDetail(busNo: string, loadGen?: number) {
  pharmacyStockInGridEdit.resetFormMode();
  pharmacyStockInGridEdit.clearGridEdit();
  try {
    const res = await getPharmacyStockInDetailApi({ busNo });
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到入库单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    originalBusNo.value = data.originalBusNo ?? undefined;
    detailStatusEnum.value = normalizePharmacyStockInStatusSort(data.statusEnum);
    detailSourceLocationId.value = data.sourceLocationId ?? undefined;
    purposeLocationId.value = data.purposeLocationId ?? undefined;

    const detailLines = data.lines ?? [];
    const firstItemTable = detailLines.find(
      (ln) =>
        ln.itemTable !== undefined && ln.itemTable !== null && String(ln.itemTable).trim() !== '',
    )?.itemTable;
    if (firstItemTable !== undefined && firstItemTable !== null) {
      headItemTableType.value = String(firstItemTable);
    }

    const gridRows = mapDetailLinesToGridRows(detailLines);
    // 详情回显：单价/总价以后端 SupplyRequest 落库为准，不在 load 时重算
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData(gridRows);
      patchPharmacyStockInGridEditConfig();
    });
  } catch {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchPharmacyStockInGridEditConfig();
    });
  }
}

function composePharmacyStockInRemake(r: PharmacyStockInPreviewRow): string | undefined {
  const rem = String(r.remark ?? '').trim();
  return rem || undefined;
}

function buildLinesPayload(rows: PharmacyStockInPreviewRow[]): PharmacyStockInLineDto[] {
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
      displayQty: qty,
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
      itemId: r.itemId,
      itemQuantity: persisted.itemQuantity,
      unitCode: unitCode || undefined,
      lotNumber: String(r.lotNumber ?? '').trim() || undefined,
      traceNo: String(r.traceNo ?? '').trim(),
      price: Number.isFinite(unit) ? unit : undefined,
      totalPrice,
      occurrenceTime: r._occurrenceTime?.trim() || undefined,
      reason: r._reason?.trim() || undefined,
      remake: composePharmacyStockInRemake(r),
    };
  });
}

function validateBeforeSave(rows: PharmacyStockInPreviewRow[]): boolean {
  const src = detailSourceLocationId.value;
  const purpose = purposeLocationId.value;
  if (src === undefined || src === null || src === '') {
    ElMessage.warning('请选择源药库');
    return false;
  }
  if (purpose === undefined || purpose === null || purpose === '') {
    ElMessage.warning('请选择目的药房');
    return false;
  }
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一行入库明细');
    return false;
  }
  for (const [i, r] of rows.entries()) {
    recalcPharmacyStockInLineTotal(r);
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
      ElMessage.warning(`第 ${i + 1} 行请选择入库单位`);
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
async function persistPharmacyStockIn(): Promise<string | undefined> {
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
      sourceLocationId: detailSourceLocationId.value as number | string,
      purposeLocationId: purposeLocationId.value as number | string,
      sourceTypeEnum: PHA_STOCK_IN_SOURCE_TYPE_ENUM,
      purposeTypeEnum: PHA_STOCK_IN_PURPOSE_TYPE_ENUM,
      categoryEnum: PHA_STOCK_IN_CATEGORY_ENUM,
      lines,
    };

    if (headerBusNo.value) {
      await updatePharmacyStockInApi({
        busNo: headerBusNo.value,
        ...common,
      });
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }
    const ob = originalBusNo.value?.trim();
    const res = await createPharmacyStockInApi({
      ...(ob ? { originalBusNo: ob } : {}),
      ...common,
    });
    const no = res.data?.busNo;
    ElMessage.success('新建成功');
    if (no) {
      headerBusNo.value = no;
      detailStatusEnum.value = normalizePharmacyStockInStatusSort(res.data?.statusEnum) ?? 1;
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
    scene: INV_DOC_DRAFT_SCENE.pharmacyStockIn,
    clientDraftId: props.clientDraftId,
    busNo,
  });
  parentSync.clearDraftSnap();
}

async function handleSaveDoc() {
  const no = await persistPharmacyStockIn();
  if (!no) return;
  clearDraftPersistAfterSave(no);
}

async function handleSaveAndSubmitDoc() {
  const no = await persistPharmacyStockIn();
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
      scene: INV_DOC_DRAFT_SCENE.pharmacyStockIn,
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
    await ElMessageBox.confirm('确认删除整张入库单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await removePharmacyStockInApi({ busNo: no });
    clearInvDocDraftCacheAfterSave({
      scene: INV_DOC_DRAFT_SCENE.pharmacyStockIn,
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
    const res = await submitPharmacyStockInApprovalApi({
      busNo: no,
    });
    if (res.data?.statusEnum !== undefined && res.data.statusEnum !== null) {
      detailStatusEnum.value = normalizePharmacyStockInStatusSort(res.data.statusEnum);
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
    const res = await withdrawPharmacyStockInApprovalApi({ busNo: no });
    if (res.data?.statusEnum !== undefined && res.data.statusEnum !== null) {
      detailStatusEnum.value = normalizePharmacyStockInStatusSort(res.data.statusEnum);
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
    await agreePharmacyStockInApi({
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
    await rejectPharmacyStockInApi({
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
      'detailSourceLocationId',
      'purposeLocationId',
    ]))
  ) {
    return;
  }
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const record = createEmptyPharmacyStockInRow();
  const { row } = await $grid.insertAt(record, null);
  await $grid.$nextTick();
  if (row) {
    await $grid.scrollToRow(row);
    await pharmacyStockInGridEdit.enterRowFormMode(
      row as PharmacyStockInPreviewRow,
      'medicinePick',
    );
  }
}

async function deleteItem(row: 'select' | PharmacyStockInPreviewRow) {
  if (!documentEditable.value) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const checked = $grid.getCheckboxRecords() as PharmacyStockInPreviewRow[];
  if (row === 'select' && checked.length === 0) return;
  await $grid.remove(row === 'select' ? checked : row);
}

async function openLinkQlDialog() {
  if (!canEditOriginalBusNo.value) {
    ElMessage.warning('仅新建单据时可关联请领单，或请先点「新增」');
    return;
  }
  const src = requireAnySpecificInventoryLocationId(
    'sourceWarehouse',
    detailSourceLocationId.value,
    productPickGateLocationId.value,
  );
  if (!src) {
    return;
  }
  linkQlQueryParams.value = { pageNo: 1, pageSize: 20, keyword: '' };
  linkQlVisible.value = true;
}

/** 关联请领单弹窗：查询 */
function handleLinkQlQuery(params: Record<string, unknown>) {
  Object.assign(linkQlQueryParams.value, params);
  linkQlQueryParams.value.pageNo = 1;
  void fetchLinkablePharmacyRequisitions();
}

/** 关联请领单弹窗：重置 */
function handleLinkQlReset() {
  linkQlQueryParams.value = { pageNo: 1, pageSize: 20, keyword: '' };
  void fetchLinkablePharmacyRequisitions();
}

/** 关联请领单弹窗：分页 */
function handleLinkQlPagination(pagination: { limit: number; page: number }) {
  linkQlQueryParams.value.pageNo = pagination.page;
  linkQlQueryParams.value.pageSize = pagination.limit;
  void fetchLinkablePharmacyRequisitions();
}

async function fetchLinkablePharmacyRequisitions() {
  const src = resolveSpecificInventoryLocationId(
    detailSourceLocationId.value,
    productPickGateLocationId.value,
  );
  if (!src) {
    linkQlRecords.value = [];
    linkQlTotal.value = 0;
    return;
  }
  linkQlLoading.value = true;
  try {
    const res = await pageLinkablePharmacyRequisitionsApi({
      sourceLocationId: src,
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
      mapLinkableRequisitionLineToPharmacyGrid(ln, i),
    );
    pharmacyStockInGridEdit.resetFormMode();
    pharmacyStockInGridEdit.clearGridEdit();
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchPharmacyStockInGridEditConfig();
    });
    linkQlVisible.value = false;
    ElMessage.success('已带入请领明细，请核对数量后保存');
  } catch {
    /* handled */
  }
}

interface PharmacyStockInDraftSnap {
  originalBusNo?: string;
  detailSourceLocationId?: number | string;
  purposeLocationId?: number | string;
  headItemTableType: string;
  rows: PharmacyStockInPreviewRow[];
}

const parentSync = useInvDocParentSyncSession<PharmacyStockInDraftSnap>({
  scene: INV_DOC_DRAFT_SCENE.pharmacyStockIn,
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
function capturePharmacyStockInDraftSnap(): PharmacyStockInDraftSnap {
  return {
    originalBusNo: originalBusNo.value,
    detailSourceLocationId: detailSourceLocationId.value,
    purposeLocationId: purposeLocationId.value,
    headItemTableType: headItemTableType.value,
    rows: collectGridRows().map((r) => ({ ...r })),
  };
}

/** 侧栏切单：缓存/恢复本地草稿，避免已落库明细污染新建单 */
async function syncFromParentContext() {
  await parentSync.syncFromParentContext(
    { isDraftOrder: Boolean(props.isDraftOrder), parentBusNo: props.busNo },
    {
      getLocalPersistedBusNo: () => headerBusNo.value,
      captureDraft: capturePharmacyStockInDraftSnap,
      restoreDraft: async (snap) => {
        headerBusNo.value = undefined;
        detailStatusEnum.value = undefined;
        originalBusNo.value = snap.originalBusNo;
        detailSourceLocationId.value = snap.detailSourceLocationId;
        purposeLocationId.value = snap.purposeLocationId;
        headItemTableType.value = snap.headItemTableType;
        pharmacyStockInGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
        patchPharmacyStockInGridEditConfig();
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
function flushPharmacyStockInDraftPersist(): void {
  parentSync.flushPersist({
    getLocalPersistedBusNo: () => headerBusNo.value,
    captureDraft: capturePharmacyStockInDraftSnap,
  });
}

onBeforeUnmount(() => {
  flushPharmacyStockInDraftPersist();
});

onDeactivated(() => {
  flushPharmacyStockInDraftPersist();
});

watch(
  () => [props.busNo, props.isDraftOrder, props.resetKey, props.clientDraftId] as const,
  () => {
    void syncFromParentContext();
  },
  { immediate: true },
);

/** 解析库位展示名（locationId + displayName） */
function resolvePrintLocationName(
  options: WarehouseOptionDto[] | undefined,
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

/** 明细行项目名称（选品单元格） */
function resolvePharmacyStockInPrintProductName(row: PharmacyStockInPreviewRow): string {
  const pick = row.medicinePick;
  if (pick && typeof pick === 'object' && 'name' in pick) {
    return String((pick as { name?: string }).name ?? '').trim();
  }
  return '';
}

/** 明细行当前计量单位展示文案 */
function resolvePharmacyStockInPrintUnitLabel(row: PharmacyStockInPreviewRow): string {
  const kind = String(row.selectedBandKind ?? '').trim();
  const byKind = row.unitList?.find((item) => String(item.value ?? '') === kind);
  if (byKind) {
    return String(byKind.label ?? byKind.value_dictText ?? '').trim();
  }
  return String(row.unitCode ?? '').trim();
}

/** 打印或预览药房入库单（计价流转模版） */
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

  const sourceName = resolvePrintLocationName(props.warehouseOptions, detailSourceLocationId.value);
  const destName = resolvePrintLocationName(props.pharmacyOptions, purposeLocationId.value);
  const itemTypeText = resolvePrintOptionLabel(props.itemTableTypeOptions, headItemTableType.value);
  const documentTitle = buildInvDocumentTitle(destName || sourceName, '入库单');
  const applyTimeText = '';

  // 按当前网格展示值组装 PrintData
  const lineRows = productRows.map((row) => ({
    productBusNo: resolveInvPrintProductBusNo(row as unknown as Record<string, unknown>),
    productName: resolvePharmacyStockInPrintProductName(row),
    specText: String(row.spec ?? '').trim(),
    manufacturerText: String(row.manufacturer ?? '').trim(),
    qty: row.itemQuantity,
    unitLabel: resolvePharmacyStockInPrintUnitLabel(row),
    unitPrice: row.price,
    amount: row.totalPrice,
    lotNumber: String(row.lotNumber ?? '').trim(),
    expiryText: '',
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
      linkDocText: String(originalBusNo.value ?? '').trim(),
      destLocationText: destName,
      invoiceNoText: '',
      lineRows,
      preparedByName,
      approvedByName,
    },
    bizRef: { busNo, docKind: 'pharmacy-stock-in' },
    jobName: documentTitle,
    preview: payload?.preview === true,
  });
}
</script>

<template>
  <div
    class="pharmacy-stock-in-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
    :class="{ 'pharmacy-stock-in-main-pane--flash': paneFlashActive }"
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
        @mousedown="pharmacyStockInGridEdit.onSurfaceMouseDown"
      >
        <div class="shrink-0">
          <SectionTitle title="入库单信息" />
          <p class="mt-1 text-xs" style="color: var(--el-text-color-secondary)">
            {{ INV_DOC_READONLY_SHELL_HINT.stockIn }}
          </p>
          <div class="mt-1 flex flex-wrap items-center gap-2">
            <span class="text-xs" style="color: var(--el-text-color-regular)">状态</span>
            <span class="text-sm" style="color: var(--el-text-color-primary)">{{
              headerStatusText
            }}</span>
            <span v-if="headerBusNo" class="text-xs" style="color: var(--el-text-color-secondary)">
              单号 {{ headerBusNo }}
            </span>
          </div>
          <ElForm
            ref="headFormRef"
            :model="headFormModel"
            :rules="headFormRules"
            label-width="80px"
            class="compact-head-form mt-1"
          >
            <div
              class="grid grid-cols-1 gap-x-2 gap-y-0 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-6"
            >
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem label="源药库" prop="detailSourceLocationId">
                <ElSelect
                  v-model="detailSourceLocationId"
                  placeholder="入库源药库"
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
              <ElFormItem v-if="originalBusNo" label="关联请领" class="lg:col-span-2">
                <ElInput :model-value="originalBusNo" disabled placeholder="—" />
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <div
          ref="pharmacyStockInGridKeyboardRef"
          class="pharmacy-stock-in-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col"
          @keydown.capture="pharmacyStockInGridEdit.onGridKeydown"
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
                @click="deleteItem(row as PharmacyStockInPreviewRow)"
              >
                <Trash2 class="size-4" />
                删除
              </ElButton>
            </template>
          </Grid>
        </div>
      </div>
    </InvMainShell>
  </div>
  <InvDocTraceScanBridge
    ref="traceScanBridgeRef"
    scene="inventoryOut"
    :default-location-id="detailSourceLocationId"
  />
</template>

<style scoped lang="scss">
.compact-head-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.compact-head-form :deep(.el-form-item__label) {
  padding-right: 8px;
}

.pharmacy-stock-in-main-pane__grid-wrap :deep(.vxe-grid) {
  flex: 1;
  min-height: 0;
}

@keyframes pharmacy-stock-in-pane-flash {
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

.pharmacy-stock-in-main-pane--flash {
  animation: pharmacy-stock-in-pane-flash 0.42s ease-out;
}
</style>
