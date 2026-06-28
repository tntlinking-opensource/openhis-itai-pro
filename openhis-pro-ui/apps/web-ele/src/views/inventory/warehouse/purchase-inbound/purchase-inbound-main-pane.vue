<!--
  药库进货单右侧主区：单头/明细 + useVbenVxeGrid；单据操作吸底。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type {
  CreateInboundReceiptResponseDto,
  InboundReceiptLineDto,
} from '#/api/inventory/warehouse/inbound-receipt';
import type { ApiOptionItem } from '#/types/api-response';
import type { InboundProductUnitBand } from '#/utils/inventory/inbound-product-unit-bands';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import type { ChargeType } from '#/api/core/common';
import { getChargeTypeListApi } from '#/api/core/common';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';

import { Trash2 } from '@vben/icons';
import { formatDateTime } from '@vben/utils';

import { Delete, Plus } from '@element-plus/icons-vue';
import InvDocActions from '@inventory/components/inv-doc-actions.vue';
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

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { useFinanceApprovalAccess } from '#/composables/use-finance-approval-access';
import { useInventoryDocGridEdit } from '#/composables/use-inventory-doc-grid-edit';
import {
  agreeInboundReceiptApi,
  createInboundReceiptApi,
  rejectInboundReceiptApi,
  removeInboundReceiptApi,
  submitInboundReceiptApprovalApi,
  updateInboundReceiptApi,
  withdrawInboundReceiptApprovalApi,
} from '#/api/inventory/warehouse/inbound-receipt';
import SectionTitle from '#/components/layouts/section-title.vue';
import PurchaseInboundBatchAddDialog from '#/views/inventory/warehouse/purchase-inbound/purchase-inbound-batch-add-dialog.vue';
import { decimalMul } from '#/utils/format/decimal-util';
import {
  canConvertDisplayQtyToSplitForBands,
  convertInboundQtyBetweenBandKinds,
  convertInboundUnitPriceBetweenBandKinds,
  defaultInboundUnitBandKind,
  findInboundUnitBandByKind,
  hydrateInboundDetailQtyFromPersisted,
  inboundResolveDisplayUnitPrice,
  inboundUnitBandSelectOptions,
  normalizeInboundProductUnitBands,
  persistDisplayQtyAsSplitForBands,
  resolveInboundLinePersistUnitCodeForBands,
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

interface Props {
  /** 当前选中单据号（来自左侧列表） */
  busNo?: string;
  /** 左侧列表所选源药库 locationId（IF-WHS-IN-04/05 sourceLocationId） */
  sourceLocationId?: number | string;
  /** 当前为新增草稿（无后端单号） */
  isDraftOrder?: boolean;
  /** 详情拉取后明细行（后端字段，与 index tableData 同源） */
  inboundDetailRows?: Record<string, unknown>[];
  /** IF-WHS-IN-01 入库项目类型选项 */
  itemTableTypeOptions?: ApiOptionItem[];
  /** 目的药库 locationId（与侧栏一致，选品 IF-WHS-IN-11 必填） */
  purposeLocationId?: number | string;
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
}

defineOptions({ name: 'PurchaseInboundMainPane' });

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  sourceLocationId: undefined,
  isDraftOrder: false,
  inboundDetailRows: () => [],
  itemTableTypeOptions: () => [],
  purposeLocationId: undefined,
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

const { canFinanceApprove } = useFinanceApprovalAccess();

interface HeadFormModel {
  busNo: string;
  supplierId: string;
  applyTime: string;
  warehouseId: number | string;
  itemTableType: string;
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

const headForm = ref<HeadFormModel>(defaultHeadForm());

const insuranceContractBusNo = ref<string>();
const insuranceChargeTypeOptions = ref<ChargeType[]>([]);

async function loadInsuranceContractBusNo() {
  try {
    const chargeTypes = await getChargeTypeListApi();
    const eligible = filterInsuranceChargeTypes(chargeTypes);
    insuranceChargeTypeOptions.value = eligible;
    insuranceContractBusNo.value = resolveDefaultInsuranceBusNo(
      eligible,
      insuranceContractBusNo.value,
    );
  } catch {
    insuranceContractBusNo.value = undefined;
    insuranceChargeTypeOptions.value = [];
  }
}

const docActionFlags = computed(() =>
  buildInboundReceiptDocActionFlags({
    documentEditable: props.documentEditable,
    hasPersistedBusNo: Boolean(String(props.busNo ?? headForm.value.busNo ?? '').trim()),
    isDraft: props.isDraftOrder,
    statusRaw: (props.inboundDetailRows?.[0] as Record<string, unknown> | undefined)?.statusEnum,
  }),
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
  headForm.value.warehouseId =
    props.purposeLocationId === undefined || props.purposeLocationId === null
      ? ''
      : props.purposeLocationId;
  headForm.value.itemTableType = firstItemTableTypeValue();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchInboundGridEditConfig();
  });
}

function syncHeadFromDetailRows(rows: Record<string, unknown>[]) {
  const h = rows[0];
  if (!h) return;
  headForm.value.busNo = String(h.busNo ?? props.busNo ?? '');
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
    pl !== undefined && pl !== null && pl !== ''
      ? (pl as number | string)
      : props.purposeLocationId === undefined || props.purposeLocationId === null
        ? ''
        : props.purposeLocationId;
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
          ? total.toFixed(2)
          : String(total),
    batchNo: String(r.lotNumber ?? ''),
    prodExpiry: mapStartEndToProdExpiry(r.startTime, r.endTime),
    remark: String(r.remake ?? ''),
  };
}

/** 编辑态：按数量 × 单价刷新合计（详情回显不调用，总价以后端为准） */
function recalcInboundLineTotal(row: InboundPreviewRow) {
  const qty = Number(row.purchaseQty);
  const unit = Number(row.unitPrice);
  row.totalAmount =
    Number.isFinite(qty) && Number.isFinite(unit) ? String(decimalMul(qty, unit, 2)) : '';
}

/** 按当前计量带写入 unitCode；切换单位时按带换算单价，否则取目录/带单价 */
function applyInboundBandKindToRow(
  row: InboundPreviewRow,
  kind: string,
  opts?: { convertFromCurrentPrice?: boolean; fromBandKind?: string },
) {
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
    row.purchaseUnit = code;
  }
  if (opts?.convertFromCurrentPrice) {
    const fromK = String(opts.fromBandKind ?? '')
      .trim()
      .toUpperCase();
    if (fromK && fromK !== k && fromK !== 'LEGACY') {
      const converted = convertInboundUnitPriceBetweenBandKinds(
        row.unitPrice,
        fromK,
        k,
        row.unitBands,
      );
      if (converted !== undefined && Number.isFinite(converted)) {
        row.unitPrice = converted;
        return;
      }
    }
  }
  const resolved = inboundResolveDisplayUnitPrice({
    bands: row.unitBands,
    displayBandKind: k,
    legacyUnitPrice: row.unitPrice,
  });
  if (resolved !== undefined) {
    row.unitPrice = resolved;
  }
}

/** 切换采购单位带：数量按 ratioToSplit 换算到目标带 */
function handleInboundUnitBandChanged(row: InboundPreviewRow) {
  const newK = String(row.selectedBandKind ?? '')
    .trim()
    .toUpperCase();
  const oldK = String(row.__prevBandKind ?? '')
    .trim()
    .toUpperCase();
  const bands = row.unitBands ?? [];
  if (oldK && newK && oldK !== newK && oldK !== 'LEGACY' && newK !== 'LEGACY' && bands.length > 0) {
    const nq = convertInboundQtyBetweenBandKinds(Number(row.purchaseQty ?? 0), oldK, newK, bands);
    if (nq !== undefined) {
      row.purchaseQty = nq;
    }
  }
  applyInboundBandKindToRow(row, newK, {
    convertFromCurrentPrice: true,
    fromBandKind: oldK,
  });
  row.__prevBandKind = newK;
  recalcInboundLineTotal(row);
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
  prodExpiry?: [string, string] | null;
  remark: string;
}

function isPresentItemId(v: unknown): boolean {
  if (v === undefined || v === null) return false;
  if (typeof v === 'string') return v.trim() !== '';
  if (typeof v === 'number') return Number.isFinite(v);
  if (typeof v === 'bigint') return true;
  return String(v).trim() !== '';
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

let inboundGridEdit!: ReturnType<typeof useInventoryDocGridEdit<InboundPreviewRow>>;

/** 选品后进入整行表单并聚焦数量 */
function handleInboundProductPick(row: InboundPreviewRow) {
  void nextTick(async () => {
    await nextTick();
    await inboundGridEdit.onProductSelected(row);
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
        listSource: 'inbound-receipt',
        query: async () => ({
          purposeLocationId: props.purposeLocationId,
          productTableName: headForm.value.itemTableType || undefined,
        }),
        onSelected: (product: Record<string, unknown>, row: InboundPreviewRow) => {
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

const gridOptions: VxeGridProps<InboundPreviewRow> = reactive({
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
    beforeEditMethod: () => props.documentEditable,
  },
  columns,
  pagerConfig: { enabled: false },
});

const INBOUND_EDITABLE_COLUMN_FIELDS = new Set([
  '_qtyUnit',
  'batchNo',
  'medicinePick',
  'prodExpiry',
  'remark',
  'unitPrice',
]);

const INBOUND_EDIT_FIELD_ORDER = [
  'medicinePick',
  '_qtyUnit',
  'unitPrice',
  'batchNo',
  'prodExpiry',
  'remark',
] as const;

type InboundEditField = (typeof INBOUND_EDIT_FIELD_ORDER)[number];

/** 支持方向键上下换行的列（不含项目名称，避免误聚焦选品下拉） */
const INBOUND_VERTICAL_NAV_FIELDS = new Set<InboundEditField>([
  '_qtyUnit',
  'batchNo',
  'prodExpiry',
  'remark',
  'unitPrice',
]);

const inboundGridKeyboardRef = ref<HTMLElement | null>(null);

const [Grid, gridApi] = useVbenVxeGrid<InboundPreviewRow>({
  gridOptions,
  gridEvents: {
    cellClick: (params: { column?: { field?: string; type?: string }; row: InboundPreviewRow }) =>
      inboundGridEdit.onCellClick(params),
    editClosed(params: { row: InboundPreviewRow }) {
      recalcInboundLineTotal(params.row);
      inboundGridEdit.syncRowFormMode(params.row);
    },
  },
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
});

inboundGridEdit = useInventoryDocGridEdit<InboundPreviewRow>({
  getGridApi: () => gridApi ?? null,
  gridWrapRef: inboundGridKeyboardRef,
  documentEditable: () => props.documentEditable,
  collectRows: collectGridRows,
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

function patchInboundGridEditConfig() {
  const patched = inboundGridEdit.patchGridOptions({
    ...(gridOptions as Record<string, unknown>),
    editConfig: {
      ...(gridOptions.editConfig as Record<string, unknown>),
      enabled: props.documentEditable,
    },
  });
  Object.assign(gridOptions, patched);
}

watch(
  () => props.documentEditable,
  (editable) => {
    patchInboundGridEditConfig();
    void nextTick(() => {
      if (!editable) {
        inboundGridEdit.resetFormMode();
        inboundGridEdit.clearGridEdit();
      }
    });
  },
  { immediate: true },
);

watch(
  () => props.purposeLocationId,
  (v) => {
    if (v !== undefined && v !== null && v !== '') {
      headForm.value.warehouseId = v;
    }
  },
);

watch(
  () => props.itemTableTypeOptions,
  (opts) => {
    if (opts?.length && !headForm.value.itemTableType) {
      headForm.value.itemTableType = String(opts[0]?.value ?? '');
    }
  },
  { deep: true },
);

watch(
  () => props.resetKey,
  () => {
    inboundGridEdit.resetFormMode();
    applyEmptyState();
    triggerPaneFlash();
  },
);

watch(
  () => [props.busNo, props.inboundDetailRows, props.isDraftOrder] as const,
  () => {
    if (props.isDraftOrder) {
      return;
    }
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
      headForm.value.warehouseId =
        props.purposeLocationId === undefined || props.purposeLocationId === null
          ? ''
          : props.purposeLocationId;
      headForm.value.itemTableType = firstItemTableTypeValue();
      void nextTick(() => {
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
      gridApi?.grid?.loadData(gridRows);
      patchInboundGridEditConfig();
    });
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

function collectGridRows(): InboundPreviewRow[] {
  const g = gridApi?.grid as null | { getFullData?: () => InboundPreviewRow[] };
  return g?.getFullData?.() ?? [];
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
  const sourceLocationId = coerceLocationId(props.sourceLocationId, '源药库');
  const purposeLocationId = coerceLocationId(headForm.value.warehouseId, '入库库房');
  const head = (props.inboundDetailRows ?? [])[0] as Record<string, unknown> | undefined;
  const sourceTypeEnum = String(head?.sourceTypeEnum ?? 'CABINET').trim() || 'CABINET';
  const purposeTypeEnum = String(head?.purposeTypeEnum ?? 'CABINET').trim() || 'CABINET';
  const categoryEnum = String(head?.categoryEnum ?? 'STOCK_SUPPLY').trim() || 'STOCK_SUPPLY';
  const originalRaw = head?.originalBusNo;
  const originalBusNo =
    originalRaw !== undefined && originalRaw !== null && String(originalRaw).trim() !== ''
      ? String(originalRaw).trim()
      : undefined;
  return {
    originalBusNo,
    sourceLocationId,
    purposeLocationId,
    sourceTypeEnum,
    purposeTypeEnum,
    categoryEnum,
    supplierId: optionalSupplierId(headForm.value.supplierId),
    practitionerId: coercePractitionerId(props.practitionerId, '经手人'),
    lines,
  };
}

async function persistInbound(submitApproval: boolean): Promise<string | undefined> {
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
  try {
    let busNo: string | undefined;
    if (props.isDraftOrder) {
      const res = (await createInboundReceiptApi(common)) as CreateInboundReceiptResponseDto & {
        data?: CreateInboundReceiptResponseDto;
      };
      busNo = (res.busNo ?? res.data?.busNo)?.trim();
    } else {
      const bn = String(props.busNo ?? headForm.value.busNo ?? '').trim();
      if (!bn) {
        ElMessage.error('缺少单据号');
        return undefined;
      }
      const head = (props.inboundDetailRows ?? [])[0] as Record<string, unknown> | undefined;
      const rv = head?.rowVersion;
      await updateInboundReceiptApi({
        busNo: bn,
        ...common,
        rowVersion: rv !== undefined && rv !== null && String(rv) !== '' ? Number(rv) : undefined,
      });
      busNo = bn;
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
  if (docActionFlags.value.submitApprovalDisabled) return;
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
  if (docActionFlags.value.withdrawDisabled) return;
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
  if (docActionFlags.value.approveDisabled) return;
  const busNo = currentPersistedBusNo();
  if (!busNo) return;
  if (!insuranceContractBusNo.value?.trim()) {
    ElMessage.warning('请选择医保契约');
    return;
  }
  try {
    await agreeInboundReceiptApi({
      busNo,
      approvalClock: nowIsoLocal(),
      contractBusNo: insuranceContractBusNo.value,
    });
    ElMessage.success('审批同意');
    emit('documentChanged', { busNo });
  } catch {
    /* 拦截器已提示 */
  }
}

async function handleReject() {
  if (props.isDraftOrder) return;
  if (docActionFlags.value.rejectDisabled) return;
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
    prodExpiry: null,
    remark: '',
  };
}

async function addItem() {
  if (!props.documentEditable) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const record = createEmptyInboundRow();
  const { row } = await $grid.insertAt(record, null);
  await $grid.$nextTick();
  if (row) {
    await $grid.scrollToRow(row);
    await inboundGridEdit.enterRowFormMode(row as InboundPreviewRow, 'medicinePick');
  }
}

async function deleteItem(row: 'select' | InboundPreviewRow) {
  if (!props.documentEditable) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const checked = $grid.getCheckboxRecords() as InboundPreviewRow[];
  if (row === 'select' && checked.length === 0) return;
  await $grid.remove(row === 'select' ? checked : row);
}

/** 未选品的空行（批量回填前清理，避免多出一行） */
function isInboundRowWithoutProduct(row: InboundPreviewRow): boolean {
  return !isPresentItemId(row.itemId);
}

/** 移除明细表中未选品的空行 */
async function removeEmptyInboundRows() {
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const rows = ($grid.getFullData?.() ?? []) as InboundPreviewRow[];
  const emptyRows = rows.filter((r) => isInboundRowWithoutProduct(r));
  if (emptyRows.length === 0) return;
  await $grid.remove(emptyRows);
}

const batchDialogVisible = ref(false);

/** 打开批量选品弹窗（与单行选品共用 purposeLocationId） */
function openBatchDialog() {
  if (!props.documentEditable) return;
  const purpose = props.purposeLocationId;
  if (purpose === undefined || purpose === null || String(purpose).trim() === '') {
    ElMessage.warning('请先在左侧选择目的药库');
    return;
  }
  batchDialogVisible.value = true;
}

/** 批量选品确认：逐条新建行并写入选品信息（等同多次单行选择） */
async function handleBatchConfirm(products: Record<string, unknown>[]) {
  if (products.length === 0) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  inboundGridEdit.clearGridEdit();
  await removeEmptyInboundRows();
  // 每条选品独立一行，仅回填药品信息，数量/批号等由用户后续填写
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
  ElMessage.success(`已添加 ${products.length} 条药品，请补充数量、批号等信息`);
}

onMounted(() => {
  void loadInsuranceContractBusNo();
});
</script>

<template>
  <div
    class="purchase-inbound-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
    :class="{ 'purchase-inbound-main-pane--flash': paneFlashActive }"
  >
    <InvMainShell>
      <template #actionBar>
        <div class="flex w-full flex-col gap-2">
          <div
            v-if="insuranceChargeTypeOptions.length > 0"
            class="flex flex-wrap items-center gap-2"
          >
            <span class="text-xs text-[var(--el-text-color-secondary)]">医保契约</span>
            <ElSelect
              v-model="insuranceContractBusNo"
              class="!w-72"
              placeholder="请选择医保契约"
              filterable
              clearable
              :disabled="docActionFlags.approveDisabled"
            >
              <ElOption
                v-for="item in insuranceChargeTypeOptions"
                :key="item.busNo"
                :label="item.busNo ? `${item.label}（${item.busNo}）` : item.label"
                :value="item.busNo"
              />
            </ElSelect>
          </div>
          <div class="flex w-full flex-wrap items-center justify-end gap-2">
            <InvDocActions
              v-bind="docActionFlags"
              :show-approval-actions="canFinanceApprove"
              @approve="handleApprove"
              @reject="handleReject"
              @remove-doc="handleRemoveDoc"
              @save="handleSaveDoc"
              @save-and-submit="handleSaveAndSubmitDoc"
              @submit-approval="handleSubmitApproval"
              @withdraw="handleWithdraw"
            />
          </div>
        </div>
      </template>

      <div
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
          <ElForm :model="headForm" label-width="80px" class="compact-head-form mt-1">
            <div
              class="grid grid-cols-1 gap-x-2 gap-y-0 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5"
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
              <ElFormItem label="库房">
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

        <div
          ref="inboundGridKeyboardRef"
          class="purchase-inbound-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col"
          @keydown.capture="inboundGridEdit.onGridKeydown"
        >
          <Grid>
            <template #toolbar-actions>
              <ElButton
                class="mr-2"
                type="primary"
                :icon="Plus"
                :disabled="!documentEditable"
                @click="addItem"
              >
                添加行
              </ElButton>
              <ElButton class="mr-2" :disabled="!documentEditable" @click="openBatchDialog">
                批量添加
              </ElButton>
              <ElButton
                type="danger"
                :icon="Delete"
                :disabled="!documentEditable"
                @click="deleteItem('select')"
              >
                删除行
              </ElButton>
            </template>
            <template #action="{ row }">
              <ElButton
                type="danger"
                text
                :disabled="!documentEditable"
                @click="deleteItem(row as InboundPreviewRow)"
              >
                <Trash2 class="size-4" />
                删除
              </ElButton>
            </template>
          </Grid>
        </div>
      </div>
    </InvMainShell>

    <PurchaseInboundBatchAddDialog
      v-model:visible="batchDialogVisible"
      :purpose-location-id="purposeLocationId"
      :product-table-name="headForm.itemTableType"
      @confirm="handleBatchConfirm"
    />
  </div>
</template>

<style scoped lang="scss">
.compact-head-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.compact-head-form :deep(.el-form-item__label) {
  padding-right: 8px;
}

.purchase-inbound-main-pane__grid-wrap :deep(.vxe-grid) {
  flex: 1;
  min-height: 0;
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
