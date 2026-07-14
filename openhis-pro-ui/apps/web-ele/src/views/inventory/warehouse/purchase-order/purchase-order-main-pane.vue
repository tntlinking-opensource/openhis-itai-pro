<!--
  药库订货单右侧主区：单头/明细 + 网格键盘导航；布局与操作同构药库进货单。
-->
<script setup lang="ts">
import type { VxeGridPropTypes } from '#/adapter/vxe-table';
import type {
  GetPurchaseOrderDetailResponse,
  PurchaseOrderLineDto,
} from '#/api/inventory/warehouse/purchase-order';
import type { ApiOptionItem } from '#/types/api-response';
import type { InboundProductUnitBand } from '#/utils/inventory/inbound-product-unit-bands';

import type { FormInstance } from 'element-plus';

import { computed, nextTick, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocDetailGridShell from '@inventory/components/inv-doc-detail-grid-shell.vue';
import InvDocGridToolbar from '@inventory/components/inv-doc-grid-toolbar.vue';
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

import { useCompactViewport } from '#/composables/use-compact-viewport';
import { useInvDocDetailGrid } from '#/composables/use-inv-doc-detail-grid';
import { useInvDocGridRowOps } from '#/composables/use-inv-doc-grid-row-ops';
import { useInvDocParentSyncSession } from '#/composables/use-inv-doc-parent-sync-session';
import {
  agreePurchaseOrderApi,
  createPurchaseOrderApi,
  rejectPurchaseOrderApi,
  removePurchaseOrderApi,
  submitPurchaseOrderApprovalApi,
  updatePurchaseOrderApi,
  WHS_PO_CATEGORY_STOCK,
  withdrawPurchaseOrderApprovalApi,
} from '#/api/inventory/warehouse/purchase-order';
import SectionTitle from '#/components/layouts/section-title.vue';
import {
  PURCHASE_ORDER_TABLE_COL_WIDTH,
  type PurchaseOrderTableColWidthPreset,
} from '#/constants/layout/inventory-doc-compact-presets';
import { formatInventoryLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { inboundReceiptPickMedicineCell } from '#/utils/inventory/inbound-receipt-product-pick-list';
import {
  applyInboundBandKindToRow,
  canConvertDisplayQtyToSplitForBands,
  defaultInboundUnitBandKind,
  findInboundUnitBandByKind,
  handleInventoryUnitBandChanged,
  hydrateInboundDetailQtyFromPersisted,
  inboundResolveDisplayUnitPrice,
  inboundUnitBandSelectOptions,
  normalizeInboundProductUnitBands,
  persistDisplayQtyAsSplitForBands,
  resolveInboundLinePersistUnitCodeForBands,
} from '#/utils/inventory/inbound-product-unit-bands';
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
import { resolveFirstRealLocationIdFromInitOptions } from '#/utils/inventory/inventory-workbench-list';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  inventoryRequiredSelectRule,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';

interface Props {
  busNo?: string;
  isDraftOrder?: boolean;
  orderDetail?: GetPurchaseOrderDetailResponse | null;
  itemTableTypeOptions?: ApiOptionItem[];
  resetKey?: number;
  applyTime?: string;
  supplierOptions?: ApiOptionItem[];
  warehouseOptions?: Record<string, unknown>[];
  documentEditable?: boolean;
  practitionerId?: number | string;
}

defineOptions({ name: 'PurchaseOrderMainPane' });

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  isDraftOrder: false,
  orderDetail: null,
  itemTableTypeOptions: () => [],
  resetKey: 0,
  applyTime: '',
  supplierOptions: () => [],
  warehouseOptions: () => [],
  documentEditable: true,
  practitionerId: undefined,
});

const emit = defineEmits<{
  documentChanged: [{ busNo?: string }];
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

interface HeadFormModel {
  busNo: string;
  supplierId: string;
  applyTime: string;
  warehouseId: number | string;
  itemTableType: string;
}

interface OrderPreviewRow {
  id: string;
  medicinePick?: Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  spec: string;
  manufacturer: string;
  orderQty?: number;
  /** 提交用计量单位编码（与当前选中 band 一致） */
  purchaseUnit: string;
  unitBands?: InboundProductUnitBand[];
  selectedBandKind?: string;
  unitList?: { label?: string; value: string; value_dictText?: string }[];
  __prevBandKind?: string;
  unitPrice: number;
  totalAmount: string;
  remark: string;
}

function defaultHeadForm(): HeadFormModel {
  return {
    busNo: '',
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

function warehouseOptionValue(row: Record<string, unknown>): number | string {
  const v = row.id ?? row.value;
  if (typeof v === 'number' || typeof v === 'string') return v;
  return String(v ?? '');
}

/** 选品接口药库门禁：与单头库房无关，取 init 返回的首个真实药库位满足后端 purposeLocationId 必填。 */
function resolvePurchaseOrderPickGateLocationId(): number | string | undefined {
  return resolveFirstRealLocationIdFromInitOptions(
    (props.warehouseOptions ?? []).map((w) => ({
      value: warehouseOptionValue(w),
    })),
  );
}

function currentPersistedBusNo(): string {
  return String(props.busNo ?? headForm.value.busNo ?? '').trim();
}

const headForm = ref<HeadFormModel>(defaultHeadForm());
const headFormRef = ref<FormInstance>();
const detailRowVersion = ref<number | string | undefined>(undefined);

const headFormRules = computed(() => ({
  ...buildInventoryLocationHeadFormRules({
    warehouseId: '库房',
  }),
  supplierId: [inventoryRequiredSelectRule('供应商')],
}));

/** 保存/保存并提交进行中：置灰防双击并发建单或软删重插出重复明细 */
const saving = ref(false);

const docActionFlags = computed(() =>
  buildInboundReceiptDocActionFlags({
    documentEditable: props.documentEditable,
    hasPersistedBusNo: Boolean(currentPersistedBusNo()),
    isDraft: props.isDraftOrder,
    statusRaw: props.orderDetail?.statusEnum,
    busy: saving.value,
  }),
);

const orderHeaderStatusText = computed(() => {
  if (props.isDraftOrder) return '编辑中';
  return inboundReceiptStatusLabel(props.orderDetail?.statusEnum);
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

/** 制单时间 → 明细 occurrenceTime */
function resolveOrderApplyOccurrenceTime(): string {
  const fromHead = String(headForm.value.applyTime ?? '').trim();
  const fromParent = String(props.applyTime ?? '').trim();
  const t = fromHead || fromParent;
  if (t === '') return nowIsoLocal();
  return t.includes('T') ? t : t.replace(' ', 'T');
}

function applyEmptyState() {
  headForm.value = defaultHeadForm();
  headForm.value.applyTime = formatDateTime(new Date());
  headForm.value.warehouseId = '';
  headForm.value.itemTableType = firstItemTableTypeValue();
  detailRowVersion.value = undefined;
  clearInventoryHeadFormValidation(headFormRef.value);
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchOrderGridEditConfig();
  });
}

function syncHeadFromDetail(detail: GetPurchaseOrderDetailResponse) {
  headForm.value.busNo = String(detail.busNo ?? props.busNo ?? '');
  headForm.value.supplierId =
    detail.supplierId !== undefined && detail.supplierId !== null
      ? (preserveApiIdForRequest(detail.supplierId) ?? '')
      : '';
  const firstLine = detail.lines?.[0];
  headForm.value.applyTime = firstLine?.occurrenceTime
    ? formatDateTime(String(firstLine.occurrenceTime))
    : formatDateTime(new Date());
  headForm.value.warehouseId =
    detail.purposeLocationId !== undefined && detail.purposeLocationId !== null
      ? (detail.purposeLocationId as number | string)
      : '';
  const firstItemTable = detail.lines?.find((ln) => ln.itemTable)?.itemTable;
  headForm.value.itemTableType =
    firstItemTable !== undefined && firstItemTable !== null && String(firstItemTable) !== ''
      ? String(firstItemTable)
      : firstItemTableTypeValue();
  detailRowVersion.value = detail.rowVersion;
}

function resolveOrderDetailUnitCodeDictText(ln: Record<string, unknown>): string {
  return String(ln.unitCodeDictText ?? ln.unitCode_dictText ?? '').trim();
}

function applyOrderDetailUnitListDictText(
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

function mapDetailLineToGrid(
  ln: NonNullable<GetPurchaseOrderDetailResponse['lines']>[number],
  idx: number,
): OrderPreviewRow {
  const itemIdRaw = ln.itemId;
  const idStr = ln.id !== undefined ? `ln-${String(ln.id)}` : `ln-${idx}`;
  const name = String(ln.productName ?? '').trim();
  const total = ln.totalPrice;
  const raw = ln as Record<string, unknown>;
  const catalog = ln.productCatalog as Record<string, unknown> | undefined;
  const unitBands = normalizeInboundProductUnitBands(
    raw.unitBands ?? catalog?.unitBands,
    raw.unitBandsJson ?? catalog?.unitBandsJson,
  );
  const unitCodeStr = String(ln.unitCode ?? '');
  const unitCodeDictText = resolveOrderDetailUnitCodeDictText(raw);
  const hasBands = unitBands.length > 0;
  const rawQty = ln.itemQuantity === undefined ? 0 : Number(ln.itemQuantity);
  let orderQty = rawQty;
  let purchaseUnit = unitCodeStr;
  let selectedBandKind = 'LEGACY';
  if (hasBands) {
    const hydrated = hydrateInboundDetailQtyFromPersisted({
      bands: unitBands,
      persistedItemQuantity: rawQty,
      persistedUnitCode: unitCodeStr,
    });
    orderQty = hydrated.displayQty;
    purchaseUnit = hydrated.unitCode;
    selectedBandKind = hydrated.bandKind;
  }
  const unitList = applyOrderDetailUnitListDictText(
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
  return {
    id: idStr,
    medicinePick: name
      ? { id: itemIdRaw === undefined ? undefined : String(itemIdRaw), name }
      : undefined,
    itemTable: ln.itemTable,
    itemId: itemIdRaw,
    spec: String(ln.specification ?? ''),
    manufacturer: String(ln.manufacturer ?? ''),
    orderQty,
    purchaseUnit,
    unitBands: hasBands ? unitBands : undefined,
    selectedBandKind,
    unitList,
    __prevBandKind: selectedBandKind,
    unitPrice: ln.price === undefined ? 0 : Number(ln.price),
    totalAmount:
      total === undefined || total === null
        ? ''
        : typeof total === 'number'
          ? formatMoneyDisplay(total)
          : String(total),
    remark: String(ln.remake ?? ''),
  };
}

function recalcOrderLineTotal(row: OrderPreviewRow) {
  const qty = Number(row.orderQty);
  const unit = Number(row.unitPrice);
  row.totalAmount =
    Number.isFinite(qty) && Number.isFinite(unit) ? formatInventoryLineTotal(qty, unit) : '';
}

function handleOrderUnitBandChanged(row: OrderPreviewRow) {
  handleInventoryUnitBandChanged(row, {
    getQty: () => Number(row.orderQty ?? 0),
    setQty: (qty) => {
      row.orderQty = qty;
    },
    onAfterChanged: () => recalcOrderLineTotal(row),
  });
}

function syncRowFromProductPick(pick: Record<string, unknown>, row: OrderPreviewRow) {
  row.medicinePick =
    inboundReceiptPickMedicineCell(pick) ?? (pick as OrderPreviewRow['medicinePick']);
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
    const kind = defaultInboundUnitBandKind(bands);
    row.selectedBandKind = kind;
    row.unitList = inboundUnitBandSelectOptions(bands);
    row.__prevBandKind = kind;
    applyInboundBandKindToRow(row, kind);
    const resolved = inboundResolveDisplayUnitPrice({
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
  recalcOrderLineTotal(row);
}

function validatePickItemTable(pick: Record<string, unknown>): boolean {
  const docType = String(headForm.value.itemTableType ?? '').trim();
  if (!docType) {
    ElMessage.warning('请先在单头选择项目类型');
    return false;
  }
  const pickTable = inventoryPickStringField(pick, 'productTableName');
  if (pickTable && pickTable !== docType) {
    ElMessage.warning('所选项目类型与单头不一致');
    return false;
  }
  const existing = collectGridRows().filter((r) => isPresentItemId(r.itemId));
  if (existing.length > 0) {
    const locked = String(existing[0]?.itemTable ?? '').trim();
    if (locked && pickTable && locked !== pickTable) {
      ElMessage.warning('一张订货单只能包含同一项目类型（药品或耗材）');
      return false;
    }
  }
  return true;
}

function isOrderRowComplete(row: OrderPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.orderQty);
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
  return !!unitCode;
}

function handleOrderProductPick(row: OrderPreviewRow) {
  scheduleInventoryProductPickFocus(orderGridEdit, row);
}

const { pickPreset, isCompactViewport } = useCompactViewport();
/** 明细列宽：≥1680 用 default，窄屏用 compact */
const orderTableColWidth = pickPreset(PURCHASE_ORDER_TABLE_COL_WIDTH);
/** 单头 label 宽：窄屏略收，腾给控件 */
const headFormLabelWidth = computed(() => (isCompactViewport.value ? '72px' : '80px'));
/** 单头栅格：窄屏缩小列间距，仍保持 xl 五列 */
const headFormGridClass = computed(() =>
  isCompactViewport.value
    ? 'grid grid-cols-1 gap-x-2 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5'
    : 'grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5',
);

/** 按视口预设组装订货单明细列（结构不变，仅宽/minWidth 切换） */
function buildPurchaseOrderColumns(w: PurchaseOrderTableColWidthPreset): VxeGridPropTypes.Column[] {
  return [
    { type: 'checkbox', width: w.checkbox, fixed: 'left', align: 'center' },
    { type: 'seq', title: '序号', width: w.seq, fixed: 'left', align: 'center' },
    {
      field: 'medicinePick',
      title: '项目名称',
      minWidth: w.medicinePickMin,
      fixed: 'left',
      editRender: {
        name: 'PurchaseOrderMedicineSelect',
        props: {
          listSource: 'purchase-order',
          placeholder: '请先选择项目类型后检索',
          query: async () => ({
            productTableName: headForm.value.itemTableType || undefined,
            purposeLocationId: resolvePurchaseOrderPickGateLocationId(),
          }),
          onSelected: (product: Record<string, unknown>, row: OrderPreviewRow) => {
            if (!validatePickItemTable(product)) return;
            syncRowFromProductPick(product, row);
            handleOrderProductPick(row);
          },
        },
      },
      showOverflow: true,
    },
    { field: 'spec', title: '规格', width: w.spec, showOverflow: true },
    {
      field: 'manufacturer',
      title: '厂家/产地',
      minWidth: w.manufacturerMin,
      showOverflow: true,
    },
    {
      field: '_qtyUnit',
      title: '数量',
      width: w.qtyUnit,
      align: 'center',
      headerAlign: 'center',
      editRender: {
        name: 'InventoryQtyUnitCell',
        props: {
          qtyField: 'orderQty',
          unitField: 'selectedBandKind',
          onUnitChange: (row: OrderPreviewRow) => {
            handleOrderUnitBandChanged(row);
          },
        },
      },
    },
    {
      field: 'unitPrice',
      title: '单价',
      width: w.unitPrice,
      align: 'right',
      headerAlign: 'center',
      editRender: {
        name: 'InventoryUnitPriceCell',
        props: { priceField: 'unitPrice', unitField: 'selectedBandKind' },
      },
    },
    {
      field: 'totalAmount',
      title: '总金额',
      width: w.totalAmount,
      align: 'right',
      headerAlign: 'center',
      cellRender: {
        name: 'InventoryTotalAmountCell',
        props: { amountField: 'totalAmount' },
      },
    },
    {
      field: 'remark',
      title: '备注',
      minWidth: w.remarkMin,
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
      width: w.action,
    },
  ];
}

const ORDER_EDITABLE_COLUMN_FIELDS = new Set(['_qtyUnit', 'medicinePick', 'remark', 'unitPrice']);
const ORDER_EDIT_FIELD_ORDER = ['medicinePick', '_qtyUnit', 'unitPrice', 'remark'] as const;
type OrderEditField = (typeof ORDER_EDIT_FIELD_ORDER)[number];
const ORDER_VERTICAL_NAV_FIELDS = new Set<OrderEditField>(['_qtyUnit', 'unitPrice', 'remark']);

const {
  Grid,
  gridApi,
  gridOptions,
  bindGridWrap,
  gridEdit: orderGridEdit,
  collectGridRows,
  patchGridEditConfig: patchOrderGridEditConfig,
} = useInvDocDetailGrid<OrderPreviewRow>({
  columns: buildPurchaseOrderColumns(orderTableColWidth.value),
  documentEditable: () => props.documentEditable,
  beforeEditMethod: () => props.documentEditable,
  onEditClosed: (row) => {
    recalcOrderLineTotal(row);
  },
  config: {
    rowKey: (row) => row.id,
    productField: 'medicinePick',
    editFieldOrder: ORDER_EDIT_FIELD_ORDER,
    editableFields: ORDER_EDITABLE_COLUMN_FIELDS,
    verticalNavFields: ORDER_VERTICAL_NAV_FIELDS,
    firstFillFieldAfterPick: '_qtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isOrderRowComplete,
  },
});

/** 视口切换时同步明细列宽到 vxe（不改列结构） */
watch(
  orderTableColWidth,
  (w) => {
    const next = buildPurchaseOrderColumns(w);
    gridOptions.columns = next;
    const grid = gridApi?.grid as { loadColumn?: (cols: VxeGridPropTypes.Column[]) => void } | null;
    grid?.loadColumn?.(next);
  },
  { deep: true },
);

const { addItem, deleteItem, removeEmptyRows } = useInvDocGridRowOps<OrderPreviewRow>({
  documentEditable: () => props.documentEditable,
  getGridApi: () => gridApi ?? null,
  gridEdit: orderGridEdit,
  createEmptyRow: () => ({
    id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    medicinePick: undefined,
    itemTable: undefined,
    itemId: undefined,
    spec: '',
    manufacturer: '',
    orderQty: undefined,
    purchaseUnit: '',
    selectedBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: '单位' }],
    __prevBandKind: 'LEGACY',
    unitPrice: 0,
    totalAmount: '',
    remark: '',
  }),
  hasProduct: (row) => isPresentItemId(row.itemId),
});

watch(
  () => props.itemTableTypeOptions,
  (opts) => {
    if (opts?.length && !headForm.value.itemTableType) {
      headForm.value.itemTableType = String(opts[0]?.value ?? '');
    }
  },
  { deep: true },
);

interface OrderDraftSnap {
  head: HeadFormModel;
  rows: OrderPreviewRow[];
}

const parentSync = useInvDocParentSyncSession<OrderDraftSnap>();

/** 将父级详情写入主区（含 loadGen 防异步串扰） */
function applyPersistedDetailFromParent(loadGen?: number) {
  if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
  const detail = props.orderDetail;
  if (detail?.busNo) {
    syncHeadFromDetail(detail);
    const gridRows = (detail.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData(gridRows);
      patchOrderGridEditConfig();
    });
    triggerPaneFlash();
    return;
  }
  if (!String(props.busNo ?? '').trim()) {
    applyEmptyState();
  }
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
        orderGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
        patchOrderGridEditConfig();
      },
      resetForCreate: () => {
        orderGridEdit.resetFormMode();
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
    orderGridEdit.resetFormMode();
    applyEmptyState();
    triggerPaneFlash();
  },
);

watch(
  () => props.documentEditable,
  (editable) => {
    patchOrderGridEditConfig();
    void nextTick(() => {
      if (!editable) {
        orderGridEdit.resetFormMode();
        orderGridEdit.clearGridEdit();
      }
    });
  },
  { immediate: true },
);

watch(
  () => [props.orderDetail, props.isDraftOrder, props.busNo] as const,
  () => {
    void syncFromParentContext();
  },
  { immediate: true, deep: true },
);

function buildLinesPayload(): PurchaseOrderLineDto[] {
  const occurrenceTime = resolveOrderApplyOccurrenceTime();
  return collectGridRows().map((r) => {
    const qty = Number(r.orderQty);
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
      }) ?? String(r.purchaseUnit ?? '');
    return {
      itemTable: String(r.itemTable ?? ''),
      itemId: r.itemId as number | string,
      itemQuantity: persisted.itemQuantity,
      unitCode,
      price: r.unitPrice,
      totalPrice: r.totalAmount === '' ? undefined : Number(r.totalAmount),
      occurrenceTime,
      remake: r.remark,
    };
  });
}

async function validateBeforeSave(): Promise<boolean> {
  if (!(await validateInventoryHeadFormFields(headFormRef.value, ['warehouseId', 'supplierId']))) {
    return false;
  }
  if (!String(headForm.value.itemTableType ?? '').trim()) {
    ElMessage.warning('请选择项目类型');
    return false;
  }
  const pid = props.practitionerId;
  if (pid === undefined || pid === null || String(pid).trim() === '') {
    ElMessage.warning('无法解析经手人，请确认登录账号已绑定医务人员');
    return false;
  }
  const lines = buildLinesPayload();
  if (lines.length === 0) {
    ElMessage.warning('请至少添加一行订货明细');
    return false;
  }
  for (const ln of lines) {
    if (!ln.itemTable || ln.itemId === undefined) {
      ElMessage.warning('明细中存在未选择项目的行');
      return false;
    }
    if (!String(ln.unitCode ?? '').trim()) {
      ElMessage.warning('明细中存在未选择单位的行');
      return false;
    }
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

async function persistPurchaseOrder(submitApproval: boolean): Promise<string | undefined> {
  // 重入护栏：双击会并发 create/修订，明细可能翻倍
  if (saving.value) return undefined;
  if (!props.documentEditable) return undefined;
  if (!(await validateBeforeSave())) return undefined;

  let practitionerId: number | string;
  try {
    practitionerId = coercePractitionerId(props.practitionerId, '经手人');
  } catch {
    ElMessage.warning('无法解析经手人，请确认登录账号已绑定医务人员');
    return undefined;
  }

  const common = {
    purposeLocationId: headForm.value.warehouseId,
    categoryEnum: WHS_PO_CATEGORY_STOCK,
    supplierId: headForm.value.supplierId || undefined,
    practitionerId,
    lines: buildLinesPayload(),
  };

  saving.value = true;
  try {
    let busNo = currentPersistedBusNo();
    // 已有单号一律修订，避免草稿标记未清时二次 create
    if (busNo) {
      await updatePurchaseOrderApi({
        busNo,
        ...common,
        rowVersion: detailRowVersion.value,
      });
    } else {
      const res = await createPurchaseOrderApi(common);
      busNo = res.data?.busNo?.trim() ?? '';
      if (busNo) {
        headForm.value.busNo = busNo;
      }
    }
    if (!busNo) {
      ElMessage.warning('保存成功但未返回单号');
      return undefined;
    }
    if (submitApproval) {
      await submitPurchaseOrderApprovalApi({ busNo, applyClock: nowIsoLocal() });
      ElMessage.success('保存并提交审批成功');
      emit('documentChanged', { busNo });
    } else {
      ElMessage.success('保存成功');
      emit('documentSaved', { busNo });
    }
    return busNo;
  } catch {
    /* 拦截器已提示 */
    return undefined;
  } finally {
    saving.value = false;
  }
}

async function handleSaveDoc() {
  await persistPurchaseOrder(false);
}

async function handleSaveAndSubmitDoc() {
  await persistPurchaseOrder(true);
}

async function handleSubmitApproval() {
  if (props.isDraftOrder) return;
  const busNo = currentPersistedBusNo();
  if (!busNo) return;
  try {
    await submitPurchaseOrderApprovalApi({ busNo, applyClock: nowIsoLocal() });
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
    await withdrawPurchaseOrderApprovalApi({ busNo });
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
      await ElMessageBox.confirm('确认放弃当前订货草稿？', '提示', { type: 'warning' });
    } catch {
      return;
    }
    emit('documentRemoved');
    return;
  }
  if (!props.documentEditable || !busNo) return;
  try {
    await ElMessageBox.confirm('确认删除整张订货单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await removePurchaseOrderApi({ busNo });
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
    await agreePurchaseOrderApi({ busNo, approvalClock: nowIsoLocal() });
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
  } catch {
    return;
  }
  try {
    await rejectPurchaseOrderApi({ busNo, rejectReason, approvalClock: nowIsoLocal() });
    ElMessage.success('已驳回');
    emit('documentChanged', { busNo });
  } catch {
    /* 拦截器已提示 */
  }
}

async function handleAddItem() {
  if (!String(headForm.value.itemTableType ?? '').trim()) {
    ElMessage.warning('请先在单头选择项目类型');
    return;
  }
  if (!(await validateInventoryHeadFormFields(headFormRef.value, 'warehouseId'))) {
    return;
  }
  await removeEmptyRows();
  await addItem();
}
</script>

<template>
  <div
    class="purchase-order-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
    :class="{ 'purchase-order-main-pane--flash': paneFlashActive }"
  >
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
        @mousedown="orderGridEdit.onSurfaceMouseDown"
      >
        <div class="shrink-0">
          <SectionTitle title="订货单信息" />
          <div class="mt-1 flex flex-wrap items-center gap-2">
            <span class="text-xs" style="color: var(--el-text-color-regular)">状态</span>
            <span class="text-sm" style="color: var(--el-text-color-primary)">{{
              orderHeaderStatusText
            }}</span>
          </div>
          <ElForm
            ref="headFormRef"
            :model="headForm"
            :rules="headFormRules"
            :label-width="headFormLabelWidth"
            class="compact-head-form mt-1"
          >
            <div :class="headFormGridClass">
              <ElFormItem label="单据号">
                <ElInput v-model="headForm.busNo" disabled placeholder="未保存" />
              </ElFormItem>
              <ElFormItem label="供应商" prop="supplierId" required>
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
            </div>
          </ElForm>
        </div>

        <InvDocDetailGridShell
          :bind-grid-wrap="bindGridWrap"
          :on-grid-keydown="orderGridEdit.onGridKeydown"
        >
          <Grid>
            <template #toolbar-actions>
              <InvDocGridToolbar
                :disabled="!documentEditable"
                @add="handleAddItem"
                @delete-selected="deleteItem('select')"
              />
            </template>
            <template #action="{ row }">
              <ElButton
                type="danger"
                text
                :disabled="!documentEditable"
                @click="deleteItem(row as OrderPreviewRow)"
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

@keyframes purchase-order-pane-flash {
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

.purchase-order-main-pane--flash {
  animation: purchase-order-pane-flash 0.42s ease-out;
}
</style>
