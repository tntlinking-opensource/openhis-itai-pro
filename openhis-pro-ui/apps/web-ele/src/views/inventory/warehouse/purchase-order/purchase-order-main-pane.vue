<!--
  药库订货单右侧主区：单头/明细 + 网格键盘导航；布局与操作同构药库进货单。
-->
<script setup lang="ts">
import type { VxeGridPropTypes } from '#/adapter/vxe-table';
import type {
  GetPurchaseOrderDetailResponse,
  LinkableDeptPurchaseRequestListRowDto,
  PurchaseOrderLineDto,
} from '#/api/inventory/warehouse/purchase-order';
import type { ApiOptionItem } from '#/types/api-response';
import type { InboundProductUnitBand } from '#/utils/inventory/inbound-product-unit-bands';
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { FormInstance } from 'element-plus';

import { computed, nextTick, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocDetailGridShell from '@inventory/components/inv-doc-detail-grid-shell.vue';
import InvDocGridToolbar from '@inventory/components/inv-doc-grid-toolbar.vue';
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

import { useCompactViewport } from '#/composables/use-compact-viewport';
import { useInvDocDetailGrid } from '#/composables/use-inv-doc-detail-grid';
import { useInvDocGridRowOps } from '#/composables/use-inv-doc-grid-row-ops';
import { useInvDocParentSyncSession } from '#/composables/use-inv-doc-parent-sync-session';
import {
  agreePurchaseOrderApi,
  createPurchaseOrderApi,
  getLinkableDeptPurchaseRequestDetailApi,
  pageLinkableDeptPurchaseRequestsApi,
  pageProductsForPurchaseApi,
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
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { formatInventoryLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  inboundReceiptPickMedicineCell,
  mapInboundReceiptProductPickListRow,
} from '#/utils/inventory/inbound-receipt-product-pick-list';
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
import { withInvDocLinkPickerEnrichedColumns } from '#/utils/inventory/inv-doc-link-picker-display';
import {
  inventoryPickNumberField,
  inventoryPickStringField,
} from '#/utils/inventory/inventory-product-pick-list';
import {
  applyInventoryRowCategoryCode,
  isPresentItemId,
  scheduleInventoryProductPickFocus,
} from '#/utils/inventory/inventory-doc-grid-common';
import { hasNonEmptyBusNo } from '#/utils/inventory/inventory-doc-restrictions';
import {
  INV_ALL_DEPT_PHARMACIES_LABEL,
  INV_ALL_LOCATIONS_FILTER_ID,
  isSpecificInventoryLocationId,
  resolveFirstRealLocationIdFromInitOptions,
  resolveLoginPractitionerLabelFromUserInfo,
  resolveSpecificInventoryLocationId,
} from '#/utils/inventory/inventory-workbench-list';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  inventoryRequiredSelectRule,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import { buildInvDocumentTitle, printInvDocument } from '#/utils/print/print-inv-document';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';
import PurchaseOrderWarningProductsDialog from '#/views/inventory/warehouse/purchase-order/purchase-order-warning-products-dialog.vue';
interface Props {
  busNo?: string;
  isDraftOrder?: boolean;
  orderDetail?: GetPurchaseOrderDetailResponse | null;
  itemTableTypeOptions?: ApiOptionItem[];
  resetKey?: number;
  applyTime?: string;
  supplierOptions?: ApiOptionItem[];
  warehouseOptions?: Record<string, unknown>[];
  /** 关联科室采购申请弹窗：科室药房下拉（含「全部」） */
  departmentPharmacyOptions?: Record<string, unknown>[];
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
  departmentPharmacyOptions: () => [],
  documentEditable: true,
  practitionerId: undefined,
});

const emit = defineEmits<{
  createFromUpstream: [row: LinkableDeptPurchaseRequestListRowDto];
  documentChanged: [{ busNo?: string }];
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

const userStore = useUserStore();

interface HeadFormModel {
  busNo: string;
  /** 可选：来源科室采购申请单号 */
  originalBusNo: string;
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
  /** 药品分类码；中药=4 时单价三位展示 */
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
/** 预警快捷选品弹窗 */
const warningDialogVisible = ref(false);

/** 关联科室采购申请：仅新建草稿可改来源单号 */
const canEditOriginalBusNo = computed(() => props.documentEditable && props.isDraftOrder);
const hasLinkedUpstream = computed(() => hasNonEmptyBusNo(headForm.value.originalBusNo));
const linkDeptVisible = ref(false);
const linkDeptLoading = ref(false);
const linkDeptQueryParams = ref<{
  keyword: string;
  pageNo: number;
  pageSize: number;
  departmentPharmacyId: number | string;
}>({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  departmentPharmacyId: INV_ALL_LOCATIONS_FILTER_ID,
});
const linkDeptTotal = ref(0);
const linkDeptRecords = ref<LinkableDeptPurchaseRequestListRowDto[]>([]);

const linkDeptPharmacyOptions = computed(() => {
  const rows = (props.departmentPharmacyOptions ?? [])
    .filter((row) => isSpecificInventoryLocationId(row.id ?? row.value))
    .map((row) => ({
      label: String(row.name ?? row.label ?? ''),
      value: (row.id ?? row.value) as number | string,
    }));
  return [{ label: INV_ALL_DEPT_PHARMACIES_LABEL, value: INV_ALL_LOCATIONS_FILTER_ID }, ...rows];
});

/** 关联弹窗筛选用：空/0 → 全部科室药房 */
function resolveLinkDeptPurposeLocationId(): number | string {
  const loc = resolveSpecificInventoryLocationId(linkDeptQueryParams.value.departmentPharmacyId);
  if (loc) return loc;
  return INV_ALL_LOCATIONS_FILTER_ID;
}

function normalizeLinkDeptPharmacyId(raw: unknown): number | string {
  if (Array.isArray(raw)) {
    const first = raw[0];
    if (first === undefined || first === null) return INV_ALL_LOCATIONS_FILTER_ID;
    return first as number | string;
  }
  if (raw === undefined || raw === null || raw === '') return INV_ALL_LOCATIONS_FILTER_ID;
  return raw as number | string;
}

/** 关联弹窗：切换科室药房后重查 */
function handleLinkDeptPharmacyChange(value: unknown) {
  linkDeptQueryParams.value.departmentPharmacyId = normalizeLinkDeptPharmacyId(value);
  linkDeptQueryParams.value.pageNo = 1;
  void fetchLinkableDeptPurchaseRequests();
}

const linkDeptFormItems = computed<FormItemConfig[]>(() => [
  {
    label: '科室药房',
    prop: 'departmentPharmacyId',
    type: 'select',
    placeholder: INV_ALL_DEPT_PHARMACIES_LABEL,
    filterable: true,
    clearable: false,
    multiple: false,
    options: linkDeptPharmacyOptions.value,
    onChange: handleLinkDeptPharmacyChange,
  },
  {
    label: '关键字',
    prop: 'keyword',
    type: 'input',
    placeholder: '申请单号/关键字',
  },
]);

const linkDeptTableColumns = computed<TableColumn[]>(() =>
  withInvDocLinkPickerEnrichedColumns(
    [
      { type: 'index', label: '序号', width: 58, align: 'center' },
      { prop: 'busNo', label: '申请单号', minWidth: 160, showOverflowTooltip: true },
      {
        prop: 'statusEnumText',
        label: '状态',
        width: 88,
        showOverflowTooltip: true,
      },
      { prop: 'applyTime', label: '申请时间', minWidth: 160, slot: 'applyTime' },
      { label: '操作', width: 88, align: 'center', fixed: 'right', slot: 'rowActions' },
    ],
    { warehouseLabel: '申请科室' },
  ),
);

const linkDeptButtonLabel = computed(() =>
  hasLinkedUpstream.value && props.isDraftOrder ? '更换申请单' : '从申请单选择',
);

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
  detailRowVersion.value = undefined;
  clearInventoryHeadFormValidation(headFormRef.value);
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchOrderGridEditConfig();
  });
}

function syncHeadFromDetail(detail: GetPurchaseOrderDetailResponse) {
  headForm.value.busNo = String(detail.busNo ?? props.busNo ?? '');
  headForm.value.originalBusNo = String(detail.originalBusNo ?? '').trim();
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
    categoryCode:
      String(
        catalog?.categoryCode ?? (ln as { categoryCode?: string }).categoryCode ?? '',
      ).trim() || undefined,
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

/** 新建空白订货明细行（数量不预填，避免误判行已录完） */
function createEmptyOrderRow(): OrderPreviewRow {
  return {
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
  };
}

const { addItem, deleteItem, removeEmptyRows } = useInvDocGridRowOps<OrderPreviewRow>({
  documentEditable: () => props.documentEditable,
  getGridApi: () => gridApi ?? null,
  gridEdit: orderGridEdit,
  createEmptyRow: createEmptyOrderRow,
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

  const originalRaw = String(headForm.value.originalBusNo ?? '').trim();
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
      // 新建才传 originalBusNo；修订禁止切换来源（由后端保留存量）
      const res = await createPurchaseOrderApi({
        ...common,
        ...(originalRaw ? { originalBusNo: originalRaw } : {}),
      });
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

/** 打开可关联科室采购申请弹窗（无需已在草稿；未在草稿时选用会 emit createFromUpstream） */
function openUpstreamPicker() {
  linkDeptQueryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    departmentPharmacyId: INV_ALL_LOCATIONS_FILTER_ID,
  };
  linkDeptRecords.value = [];
  linkDeptTotal.value = 0;
  linkDeptVisible.value = true;
}

/** 单头「从申请单选择/更换」：仅新建草稿可改关联 */
function openLinkDeptDialog() {
  if (!canEditOriginalBusNo.value) {
    ElMessage.warning('仅新建单据时可关联科室采购申请单');
    return;
  }
  openUpstreamPicker();
}

/** 关联申请弹窗：查询 */
function handleLinkDeptQuery(params: Record<string, unknown>) {
  Object.assign(linkDeptQueryParams.value, params);
  linkDeptQueryParams.value.departmentPharmacyId = normalizeLinkDeptPharmacyId(
    linkDeptQueryParams.value.departmentPharmacyId,
  );
  linkDeptQueryParams.value.pageNo = 1;
  void fetchLinkableDeptPurchaseRequests();
}

/** 关联申请弹窗：重置 */
function handleLinkDeptReset() {
  linkDeptQueryParams.value = {
    pageNo: 1,
    pageSize: 20,
    keyword: '',
    departmentPharmacyId: INV_ALL_LOCATIONS_FILTER_ID,
  };
  void fetchLinkableDeptPurchaseRequests();
}

/** 关联申请弹窗：分页 */
function handleLinkDeptPagination(pagination: { limit: number; page: number }) {
  linkDeptQueryParams.value.pageNo = pagination.page;
  linkDeptQueryParams.value.pageSize = pagination.limit;
  void fetchLinkableDeptPurchaseRequests();
}

/** 拉取可关联科室采购申请（已同意且未被订货占用） */
async function fetchLinkableDeptPurchaseRequests() {
  linkDeptQueryParams.value.departmentPharmacyId = normalizeLinkDeptPharmacyId(
    linkDeptQueryParams.value.departmentPharmacyId,
  );
  linkDeptLoading.value = true;
  try {
    const res = await pageLinkableDeptPurchaseRequestsApi({
      purposeLocationId: resolveLinkDeptPurposeLocationId(),
      keyword: String(linkDeptQueryParams.value.keyword ?? '').trim() || undefined,
      pageNo: linkDeptQueryParams.value.pageNo,
      pageSize: linkDeptQueryParams.value.pageSize,
    });
    const { content, totalCount } =
      parsePagedRowsFromApiData<LinkableDeptPurchaseRequestListRowDto>(res.data);
    linkDeptRecords.value = content;
    linkDeptTotal.value = totalCount;
  } catch {
    linkDeptRecords.value = [];
    linkDeptTotal.value = 0;
  } finally {
    linkDeptLoading.value = false;
  }
}

/**
 * 选用科室采购申请：非草稿时先建草稿；草稿则直接带入。
 */
async function applyLinkableDeptPurchaseRequest(row: LinkableDeptPurchaseRequestListRowDto) {
  if (!props.isDraftOrder) {
    emit('createFromUpstream', row);
    return;
  }
  await applyLinkableDeptPurchaseRequestToDraft(row);
}

/**
 * 将科室采购申请明细带入当前草稿。
 * 申请目的位是科室药房，不得写入订货单头药库；供应商仍由用户选择。
 */
async function applyLinkableDeptPurchaseRequestToDraft(row: LinkableDeptPurchaseRequestListRowDto) {
  const applyNo = String(row.busNo ?? '').trim();
  if (!applyNo) return;
  try {
    const res = await getLinkableDeptPurchaseRequestDetailApi({
      originalBusNo: applyNo,
    });
    const data = res.data;
    if (!data?.lines?.length) {
      ElMessage.warning('未获取到科室采购申请明细');
      return;
    }
    headForm.value.originalBusNo = String(
      data.originalBusNo ?? data.deptPurchaseRequestBusNo ?? applyNo,
    ).trim();
    const firstTable = data.lines[0]?.itemTable;
    if (firstTable) {
      headForm.value.itemTableType = String(firstTable);
    }
    const gridRows = (data.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    orderGridEdit.resetFormMode();
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchOrderGridEditConfig();
    });
    linkDeptVisible.value = false;
    const locHint = String(data.purposeLocationName ?? '').trim();
    ElMessage.success(
      locHint
        ? `已带入「${locHint}」申请明细，请选择药库/供应商后保存`
        : '已带入科室申请明细，请选择药库/供应商后保存',
    );
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

/** 明细行项目名称（选品单元格） */
function resolveOrderPrintProductName(row: OrderPreviewRow): string {
  const pick = row.medicinePick;
  if (pick && typeof pick === 'object' && 'name' in pick) {
    return String((pick as { name?: string }).name ?? '').trim();
  }
  return '';
}

/** 明细行当前计量单位展示文案 */
function resolveOrderPrintUnitLabel(row: OrderPreviewRow): string {
  const kind = String(row.selectedBandKind ?? '').trim();
  const byKind = row.unitList?.find((item) => String(item.value ?? '') === kind);
  if (byKind) {
    return String(byKind.label ?? byKind.value_dictText ?? '').trim();
  }
  const code = String(row.purchaseUnit ?? '').trim();
  const byCode = row.unitList?.find((item) => String(item.value ?? '') === code);
  return String(byCode?.label ?? byCode?.value_dictText ?? code).trim();
}

/** 打印订货单（计价流转模版） */
async function handlePrint() {
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
  const practitionerName = resolveLoginPractitionerLabelFromUserInfo(
    userStore.userInfo as null | Record<string, unknown>,
  );
  const documentTitle = buildInvDocumentTitle(warehouseName, '订货单');
  const applyTimeText = String(headForm.value.applyTime ?? props.applyTime ?? '')
    .trim()
    .replace('T', ' ');

  // 按当前网格展示值组装 PrintData
  const lineRows = productRows.map((row) => ({
    productName: resolveOrderPrintProductName(row),
    specText: String(row.spec ?? '').trim(),
    manufacturerText: String(row.manufacturer ?? '').trim(),
    qty: row.orderQty,
    unitLabel: resolveOrderPrintUnitLabel(row),
    unitPrice: row.unitPrice,
    amount: row.totalAmount,
    lotNumber: '',
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
      sourceLocationText: warehouseName,
      supplierText,
      linkDocText: String(headForm.value.originalBusNo ?? '').trim(),
      destLocationText: '',
      invoiceNoText: '',
      practitionerName,
      lineRows,
      preparedByName: practitionerName,
      approvedByName: '',
    },
    bizRef: { busNo, docKind: 'purchase-order' },
    jobName: documentTitle,
  });
}

/** 打开预警药品弹窗（须已选单头项目类型与库房） */
async function openWarningProductsDialog() {
  if (!props.documentEditable) return;
  if (!String(headForm.value.itemTableType ?? '').trim()) {
    ElMessage.warning('请先在单头选择项目类型');
    return;
  }
  if (!(await validateInventoryHeadFormFields(headFormRef.value, 'warehouseId'))) {
    return;
  }
  warningDialogVisible.value = true;
}

/**
 * 预警勾选确认：按 id 调采购选品收窄结果，写入明细（不自动填数量）。
 * 已存在同一产品定义的行跳过，避免重复。
 */
async function handleWarningProductsConfirm(productDefinitionIds: string[]) {
  if (productDefinitionIds.length === 0) return;
  const docType = String(headForm.value.itemTableType ?? '').trim();
  if (!docType) {
    ElMessage.warning('请先在单头选择项目类型');
    return;
  }
  const purposeLocationId = preserveApiIdForRequest(headForm.value.warehouseId);
  if (!purposeLocationId) {
    ElMessage.warning('请先选择库房');
    return;
  }
  // 明细已有产品定义 id，用于去重
  const existingIdSet = new Set(
    collectGridRows()
      .filter((r) => isPresentItemId(r.itemId))
      .map((r) => preserveApiIdForRequest(r.itemId))
      .filter((id): id is string => !!id),
  );
  // 仅保留尚未在明细中的 id
  const idsToAdd = productDefinitionIds.filter((id) => !existingIdSet.has(id));
  if (idsToAdd.length === 0) {
    ElMessage.info('所选项目均已在明细中');
    return;
  }
  const skipped = productDefinitionIds.length - idsToAdd.length;
  try {
    const res = await pageProductsForPurchaseApi({
      purposeLocationId,
      productTableName: docType,
      productDefinitionIds: idsToAdd,
      pageNo: 1,
      pageSize: Math.min(Math.max(idsToAdd.length, 1), 500),
      activeFlag: true,
    });
    const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res.data);
    const picks = parsed.content.map((r) => mapInboundReceiptProductPickListRow(r));
    if (picks.length === 0) {
      ElMessage.warning('未查询到可回填的目录项目');
      return;
    }
    for (const pick of picks) {
      if (!validatePickItemTable(pick)) return;
    }
    const $grid = gridApi?.grid;
    if (!$grid) return;
    orderGridEdit.clearGridEdit();
    await removeEmptyRows();
    const insertedRows: OrderPreviewRow[] = [];
    for (const pick of picks) {
      const record = createEmptyOrderRow();
      syncRowFromProductPick(pick, record);
      const { row } = await $grid.insertAt(record, null);
      if (row) {
        insertedRows.push(row as OrderPreviewRow);
        orderGridEdit.markRowFormMode(row as OrderPreviewRow);
      }
    }
    await $grid.$nextTick();
    await nextTick();
    const firstInserted = insertedRows[0];
    if (firstInserted && isPresentItemId(firstInserted.itemId)) {
      await $grid.scrollToRow(firstInserted);
      await orderGridEdit.enterRowFormMode(firstInserted, '_qtyUnit');
    }
    const skipHint = skipped > 0 ? `，已跳过 ${skipped} 条重复` : '';
    ElMessage.success(`已添加 ${insertedRows.length} 条预警项目，请补充数量${skipHint}`);
  } catch {
    /* 全局错误提示 */
  }
}

defineExpose({
  applyLinkableDeptPurchaseRequestToDraft,
  openUpstreamPicker,
});
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
        class="purchase-order-main-pane__body flex min-h-0 flex-1 flex-col overflow-hidden px-2"
        :class="isCompactViewport ? 'gap-1.5 pt-1.5' : 'gap-2 pt-2'"
        @mousedown="orderGridEdit.onSurfaceMouseDown"
      >
        <div class="shrink-0">
          <div class="mb-1 flex min-w-0 items-center justify-between gap-2">
            <SectionTitle title="订货单信息" class="min-w-0" />
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
              <ElFormItem label="关联申请" class="sm:col-span-2 lg:col-span-2 xl:col-span-2">
                <div class="flex min-w-0 items-center gap-2">
                  <ElInput
                    :model-value="headForm.originalBusNo"
                    class="min-w-0 flex-1"
                    readonly
                    disabled
                    placeholder="可选；须为已同意的科室采购申请单"
                  />
                  <ElButton
                    class="shrink-0"
                    :disabled="!canEditOriginalBusNo"
                    @click="openLinkDeptDialog"
                  >
                    {{ linkDeptButtonLabel }}
                  </ElButton>
                </div>
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
                show-warning-products
                @add="handleAddItem"
                @warning-products="openWarningProductsDialog"
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

    <PurchaseOrderWarningProductsDialog
      v-model:visible="warningDialogVisible"
      :purpose-location-id="headForm.warehouseId"
      :product-table-name="headForm.itemTableType"
      @confirm="handleWarningProductsConfirm"
    />

    <InvDocLinkPickerDialog
      v-model="linkDeptVisible"
      title="选择已同意的科室采购申请单"
      :table-data="linkDeptRecords"
      :loading="linkDeptLoading"
      :total="linkDeptTotal"
      :query-params="linkDeptQueryParams"
      :form-items="linkDeptFormItems"
      :table-columns="linkDeptTableColumns"
      :location-options="departmentPharmacyOptions"
      keyword-placeholder="申请单号/关键字"
      @opened="fetchLinkableDeptPurchaseRequests"
      @query="handleLinkDeptQuery"
      @reset="handleLinkDeptReset"
      @pagination="handleLinkDeptPagination"
      @select="
        (row) => applyLinkableDeptPurchaseRequest(row as LinkableDeptPurchaseRequestListRowDto)
      "
    >
      <template #applyTime="{ row }">
        {{
          (row as LinkableDeptPurchaseRequestListRowDto).applyTime
            ? formatDateTime((row as LinkableDeptPurchaseRequestListRowDto).applyTime as string)
            : ''
        }}
      </template>
      <template #rowActions="{ row }">
        <ElButton
          link
          type="primary"
          @click.stop="
            applyLinkableDeptPurchaseRequest(row as LinkableDeptPurchaseRequestListRowDto)
          "
        >
          选用
        </ElButton>
      </template>
    </InvDocLinkPickerDialog>
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
