<!--
  科室采购申请单右侧主区：单头/明细 + 网格键盘导航；同构药库订货单（无供应商）。
-->
<script setup lang="ts">
import type { VxeGridPropTypes } from '#/adapter/vxe-table';
import type {
  DeptPurchaseRequestLineDto,
  GetDeptPurchaseRequestDetailResponse,
} from '#/api/inventory/department/purchase-request';
import type { ApiOptionItem } from '#/types/api-response';
import type { InboundProductUnitBand } from '#/utils/inventory/inbound-product-unit-bands';

import type { FormInstance } from 'element-plus';

import { computed, nextTick, ref, watch } from 'vue';

import { Trash2 } from '@zxplat/icons';
import { useUserStore } from '@zxplat/stores';
import { formatDateTime } from '@zxplat/utils';

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
  agreeDeptPurchaseRequestApi,
  createDeptPurchaseRequestApi,
  DEPT_CPR_CATEGORY_STOCK,
  rejectDeptPurchaseRequestApi,
  removeDeptPurchaseRequestApi,
  submitDeptPurchaseRequestApprovalApi,
  updateDeptPurchaseRequestApi,
  withdrawDeptPurchaseRequestApprovalApi,
} from '#/api/inventory/department/purchase-request';
import SectionTitle from '#/components/layouts/section-title.vue';
import {
  PURCHASE_ORDER_TABLE_COL_WIDTH,
  type PurchaseOrderTableColWidthPreset,
} from '#/constants/layout/inventory-doc-compact-presets';
import { formatInventoryLineTotal, formatPurchaseMoneyDisplay } from '#/utils/format/money-scale';
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
  applyInventoryRowCategoryCode,
  isPresentItemId,
  scheduleInventoryProductPickFocus,
} from '#/utils/inventory/inventory-doc-grid-common';
import {
  buildInventoryDocPractitionerSelectOptions,
  coercePractitionerIdForSelect,
  INV_ITEM_TABLE_TYPE_DEVICE,
  resolveDefaultPractitionerIdForSelect,
  resolveFirstRealLocationIdFromInitOptions,
  resolveInvDocPreparedByName,
  resolveInvDocApprovedByName,
} from '#/utils/inventory/inventory-workbench-list';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import {
  buildInvDocumentTitle,
  printInvDocument,
  resolveInvPrintProductBusNo,
} from '#/utils/print/print-inv-document';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

interface Props {
  /** 侧栏当前单据行（制单人/审批人与队列卡片同口径） */
  queueCardRow?: null | Record<string, unknown>;
  busNo?: string;
  isDraftOrder?: boolean;
  orderDetail?: GetDeptPurchaseRequestDetailResponse | null;
  itemTableTypeOptions?: ApiOptionItem[];
  resetKey?: number;
  applyTime?: string;
  pharmacyOptions?: Record<string, unknown>[];
  documentEditable?: boolean;
  practitionerOptions?: ApiOptionItem[];
}

defineOptions({ name: 'InventoryDepartmentPurchaseRequestMainPane' });

const props = withDefaults(defineProps<Props>(), {
  queueCardRow: null,
  busNo: '',
  isDraftOrder: false,
  orderDetail: null,
  itemTableTypeOptions: () => [],
  resetKey: 0,
  applyTime: '',
  pharmacyOptions: () => [],
  documentEditable: true,
  practitionerOptions: () => [],
});

const emit = defineEmits<{
  documentChanged: [{ busNo?: string }];
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

const practitionerId = ref<number | string | undefined>(undefined);

interface HeadFormModel {
  busNo: string;
  applyTime: string;
  warehouseId: number | string;
  itemTableType: string;
}

interface OrderPreviewRow {
  id: string;
  medicinePick?: Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  /** 药品分类码；中药=4 时进价三位展示 */
  categoryCode?: string;
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
    applyTime: '',
    warehouseId: '',
    itemTableType: '',
  };
}

/** 新建默认项目类型：耗材（adm_device_definition）；选项中无耗材时回落首项。 */
function firstItemTableTypeValue(): string {
  const opts = props.itemTableTypeOptions ?? [];
  const deviceHit = opts.find((o) => {
    const value = String(o.value ?? '').trim();
    const label = String(o.label ?? '').trim();
    return value === 'adm_device_definition' || label === '耗材';
  });
  if (deviceHit?.value !== undefined && deviceHit?.value !== null) {
    return String(deviceHit.value);
  }
  const o = opts[0];
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

/** 选品接口科室药房门禁：与单头库位解耦，取 init 首个真实科室药房满足后端 purposeLocationId 必填。 */
function resolvePurchaseRequestPickGateLocationId(): number | string | undefined {
  return resolveFirstRealLocationIdFromInitOptions(
    (props.pharmacyOptions ?? []).map((w) => ({
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

const headFormRules = computed(() =>
  buildInventoryLocationHeadFormRules({
    warehouseId: '科室药房',
  }),
);

/** 保存/保存并提交进行中：置灰防双击并发建单或软删重插出重复明细 */
const saving = ref(false);

const docActionFlags = computed(() => {
  const hasPersistedBusNo = Boolean(currentPersistedBusNo());
  const base = buildInboundReceiptDocActionFlags({
    documentEditable: props.documentEditable,
    hasPersistedBusNo,
    isDraft: props.isDraftOrder,
    statusRaw: props.orderDetail?.statusEnum,
    busy: saving.value,
  });
  return {
    ...base,
    // 已落库单据才可打印
    printDisabled: saving.value || !hasPersistedBusNo,
  };
});

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
  pinnedPractitionerOptions.value = [];
  practitionerId.value = resolveDefaultPractitionerIdForSelect(
    effectivePractitionerOptions.value,
    userStore.userInfo as null | Record<string, unknown>,
  );
  detailRowVersion.value = undefined;
  clearInventoryHeadFormValidation(headFormRef.value);
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchOrderGridEditConfig();
  });
}

function syncHeadFromDetail(detail: GetDeptPurchaseRequestDetailResponse) {
  headForm.value.busNo = String(detail.busNo ?? props.busNo ?? '');
  headForm.value.applyTime = detail.occurrenceTime
    ? formatDateTime(String(detail.occurrenceTime))
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
  pinnedPractitionerOptions.value = [];
  pinPractitionerOption(detail.practitionerId);
  practitionerId.value = coercePractitionerIdForSelect(
    detail.practitionerId,
    effectivePractitionerOptions.value,
  );
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
  ln: NonNullable<GetDeptPurchaseRequestDetailResponse['lines']>[number],
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
  const productBusNo = String(catalog?.productBusNo ?? '').trim();
  const categoryCode =
    String(catalog?.categoryCode ?? (ln as { categoryCode?: string }).categoryCode ?? '').trim() ||
    undefined;
  return {
    id: idStr,
    medicinePick: name
      ? {
          id: itemIdRaw === undefined ? undefined : String(itemIdRaw),
          name,
          ...(productBusNo ? { busNo: productBusNo } : {}),
        }
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
          ? formatPurchaseMoneyDisplay(total, categoryCode)
          : String(total),
    remark: String(ln.remake ?? ''),
    categoryCode,
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
  applyInventoryRowCategoryCode(row as unknown as Record<string, unknown>, pick);
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
      ElMessage.warning('一张采购申请单只能包含同一项目类型（药品或耗材）');
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
    ? 'grid grid-cols-1 gap-x-2 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4'
    : 'grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4',
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
          listSource: 'department-purchase-request',
          placeholder: '请先选择项目类型后检索',
          query: async () => ({
            productTableName: headForm.value.itemTableType || undefined,
            purposeLocationId: resolvePurchaseRequestPickGateLocationId(),
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
  /** 本页已 pickPreset 列宽，避免与 hook 内缩放叠加 */
  compactColumns: false,
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
      headForm.value.itemTableType = firstItemTableTypeValue();
    }
  },
  { deep: true },
);

interface OrderDraftSnap {
  head: HeadFormModel;
  practitionerId?: number | string;
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
        practitionerId: practitionerId.value,
        rows: collectGridRows().map((r) => ({ ...r })),
      }),
      restoreDraft: async (snap) => {
        headForm.value = { ...snap.head, busNo: '' };
        practitionerId.value = snap.practitionerId;
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

function buildLinesPayload(): DeptPurchaseRequestLineDto[] {
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
      remake: r.remark,
    };
  });
}

async function validateBeforeSave(): Promise<boolean> {
  if (!(await validateInventoryHeadFormFields(headFormRef.value, ['warehouseId']))) {
    return false;
  }
  if (!String(headForm.value.itemTableType ?? '').trim()) {
    ElMessage.warning('请选择项目类型');
    return false;
  }
  if (!practitionerId.value) {
    ElMessage.warning('请选择经手人');
    return false;
  }
  const lines = buildLinesPayload();
  if (lines.length === 0) {
    ElMessage.warning('请至少添加一行申请明细');
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

async function persistPurchaseRequest(submitApproval: boolean): Promise<string | undefined> {
  // 重入护栏：双击会并发 create/修订，明细可能翻倍
  if (saving.value) return undefined;
  if (!props.documentEditable) return undefined;
  if (!(await validateBeforeSave())) return undefined;

  let handlerId: number | string;
  try {
    handlerId = coercePractitionerId(practitionerId.value, '经手人');
  } catch {
    ElMessage.warning('请选择经手人');
    return undefined;
  }

  const common = {
    purposeLocationId: headForm.value.warehouseId,
    occurrenceTime: resolveOrderApplyOccurrenceTime(),
    categoryEnum: DEPT_CPR_CATEGORY_STOCK,
    practitionerId: handlerId,
    lines: buildLinesPayload(),
  };

  saving.value = true;
  try {
    let busNo = currentPersistedBusNo();
    // 已有单号一律修订，避免草稿标记未清时二次 create
    if (busNo) {
      await updateDeptPurchaseRequestApi({
        busNo,
        ...common,
        rowVersion: detailRowVersion.value,
      });
    } else {
      const res = await createDeptPurchaseRequestApi(common);
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
      await submitDeptPurchaseRequestApprovalApi({ busNo, applyClock: nowIsoLocal() });
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
  await persistPurchaseRequest(false);
}

async function handleSaveAndSubmitDoc() {
  await persistPurchaseRequest(true);
}

async function handleSubmitApproval() {
  if (props.isDraftOrder) return;
  const busNo = currentPersistedBusNo();
  if (!busNo) return;
  try {
    await submitDeptPurchaseRequestApprovalApi({ busNo, applyClock: nowIsoLocal() });
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
    await withdrawDeptPurchaseRequestApprovalApi({ busNo });
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
      await ElMessageBox.confirm('确认放弃当前采购申请草稿？', '提示', { type: 'warning' });
    } catch {
      return;
    }
    emit('documentRemoved');
    return;
  }
  if (!props.documentEditable || !busNo) return;
  try {
    await ElMessageBox.confirm('确认删除整张采购申请单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await removeDeptPurchaseRequestApi({ busNo });
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
    await agreeDeptPurchaseRequestApi({ busNo });
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
    await rejectDeptPurchaseRequestApi({ busNo, rejectReason });
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

/** 解析科室药房展示名 */
function resolvePrintPharmacyName(value: unknown): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = (props.pharmacyOptions ?? []).find(
    (row) => String(warehouseOptionValue(row)) === key,
  );
  return String(hit?.name ?? hit?.label ?? '').trim();
}

/** 明细行项目名称（选品单元格） */
function resolveRequestPrintProductName(row: OrderPreviewRow): string {
  const pick = row.medicinePick;
  if (pick && typeof pick === 'object' && 'name' in pick) {
    return String((pick as { name?: string }).name ?? '').trim();
  }
  return '';
}

/** 明细行当前计量单位展示文案 */
function resolveRequestPrintUnitLabel(row: OrderPreviewRow): string {
  const kind = String(row.selectedBandKind ?? '').trim();
  const byKind = row.unitList?.find((item) => String(item.value ?? '') === kind);
  if (byKind) {
    return String(byKind.label ?? byKind.value_dictText ?? '').trim();
  }
  const code = String(row.purchaseUnit ?? '').trim();
  const byCode = row.unitList?.find((item) => String(item.value ?? '') === code);
  return String(byCode?.label ?? byCode?.value_dictText ?? code).trim();
}

/** 打印或预览采购申请单（计价流转模版） */
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

  const pharmacyName = resolvePrintPharmacyName(headForm.value.warehouseId);
  const itemTypeText = resolvePrintOptionLabel(
    props.itemTableTypeOptions,
    headForm.value.itemTableType,
  );
  const isDevicePurchaseRequest =
    String(headForm.value.itemTableType ?? '').trim() === INV_ITEM_TABLE_TYPE_DEVICE;
  const documentTitle = buildInvDocumentTitle(pharmacyName, '采购申请单');
  const applyTimeText = String(headForm.value.applyTime ?? props.applyTime ?? '')
    .trim()
    .replace('T', ' ');

  // 按当前网格展示值组装 PrintData
  const lineRows = productRows.map((row) => ({
    productBusNo: resolveInvPrintProductBusNo(row as unknown as Record<string, unknown>),
    productName: resolveRequestPrintProductName(row),
    specText: String(row.spec ?? '').trim(),
    manufacturerText: String(row.manufacturer ?? '').trim(),
    qty: row.orderQty,
    unitLabel: resolveRequestPrintUnitLabel(row),
    unitPrice: row.unitPrice,
    amount: row.totalAmount,
    lotNumber: '',
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
      sourceLocationText: pharmacyName,
      supplierText: '',
      linkDocText: '',
      destLocationText: '',
      invoiceNoText: '',
      lineRows,
      preparedByName,
      approvedByName,
      // 仅采购申请单：底部印「采购人」，并展示经办人/科室责任人/业务主管院长/院长
      purchaseRequestSignOff: true,
      // 仅耗材申请：底部去掉制单人/审核，审核放到单头制单人后
      devicePurchaseRequestSignOff: isDevicePurchaseRequest,
    },
    bizRef: { busNo, docKind: 'purchase-request' },
    jobName: documentTitle,
    preview: payload?.preview === true,
  });
}
</script>

<template>
  <div
    class="purchase-request-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
    :class="{ 'purchase-request-main-pane--flash': paneFlashActive }"
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
        class="purchase-request-main-pane__body flex min-h-0 flex-1 flex-col overflow-hidden px-2"
        :class="isCompactViewport ? 'gap-1.5 pt-1.5' : 'gap-2 pt-2'"
        @mousedown="orderGridEdit.onSurfaceMouseDown"
      >
        <div class="shrink-0">
          <div class="mb-1 flex min-w-0 items-center justify-between gap-2">
            <SectionTitle title="采购申请单信息" class="min-w-0" />
            <div class="flex shrink-0 flex-wrap items-center gap-1.5">
              <span class="text-xs" style="color: var(--el-text-color-regular)">状态</span>
              <span
                :class="isCompactViewport ? 'text-xs' : 'text-sm'"
                style="color: var(--el-text-color-primary)"
                >{{ orderHeaderStatusText }}</span
              >
            </div>
          </div>
          <ElForm
            ref="headFormRef"
            :model="headForm"
            :rules="headFormRules"
            :label-width="headFormLabelWidth"
            class="compact-head-form"
            :class="isCompactViewport ? 'mt-0.5' : 'mt-1'"
          >
            <div :class="headFormGridClass">
              <ElFormItem label="单据号">
                <ElInput v-model="headForm.busNo" disabled placeholder="未保存" />
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
              <ElFormItem label="科室药房" prop="warehouseId">
                <ElSelect
                  v-model="headForm.warehouseId"
                  placeholder="请选择"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="w in pharmacyOptions"
                    :key="String(w.id ?? w.value)"
                    :label="String(w.name ?? w.label ?? '')"
                    :value="warehouseOptionValue(w)"
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

@keyframes purchase-request-pane-flash {
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

.purchase-request-main-pane--flash {
  animation: purchase-request-pane-flash 0.42s ease-out;
}
</style>
