<!--
  药库出库单右侧主区：InvMainShell + InvDocActions + 单头表单 + useVbenVxeGrid；布局与进货主区同构。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { ChargeType } from '#/api/core/common';
import type { PharmacyLocationOptionDto } from '#/api/inventory/pharmacy/requisition';
import type { WarehouseOptionDto } from '#/api/inventory/warehouse/stock-out';
import type { ApiOptionItem } from '#/types/api-response';
import type { InboundProductUnitBand } from '#/utils/inventory/inbound-product-unit-bands';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import { Delete, Plus } from '@element-plus/icons-vue';
import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getChargeTypeListApi } from '#/api/core/common';
import { getPharmacyRequisitionDetailApi } from '#/api/inventory/pharmacy/requisition';
import {
  agreeStockOutApi,
  createStockOutApi,
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
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import SectionTitle from '#/components/layouts/section-title.vue';
import StockOutBatchAddDialog from '#/views/inventory/warehouse/stock-out/stock-out-batch-add-dialog.vue';
import {
  collectFormFocusableElements,
  EXTENDED_FORM_KEYBOARD_SELECTORS,
  focusFormControl,
  isFormSelectDropdownTarget,
  isFormSelectExpanded,
} from '#/hooks/use-form-keyboard-navigation';
import { decimalMul } from '#/utils/decimal-util';
import { resolveLoginPractitionerIdForRequest } from '#/utils/preserve-api-id';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  convertInboundQtyBetweenBandKinds,
  inboundUnitBandSelectOptions,
  normalizeInboundProductUnitBands,
  canConvertDisplayQtyToSplitForBands,
  persistDisplayQtyAsSplitForBands,
  applyWarehouseNonInboundBandKindToRow,
  hydrateWarehouseDetailQtyFromPersisted,
  warehouseNonInboundDefaultBandKind,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  buildStockOutDocActionFlags,
  normalizeStockOutStatusSort,
  stockOutStatusLabel,
} from '#/utils/inventory/stock-out-doc-actions';
import {
  inventoryPickAvailableQtyAsStockDisplay,
  inventoryPickMedicineCell,
  inventoryPickNumberField,
  inventoryPickStringField,
} from '#/utils/inventory/inventory-product-pick-list';

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
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

const userStore = useUserStore();

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
  if (v === undefined || v === null || v === '') return undefined;
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

const detailStatusEnum = ref<number | undefined>(undefined);

/** 出库选品 {@code productTableName}，与进货单「项目类型」一致 */
const headItemTableType = ref(firstItemTableTypeValue());

const saving = ref(false);

const insuranceContractBusNo = ref<string>();
const insuranceChargeTypeOptions = ref<ChargeType[]>([]);

const linkQlVisible = ref(false);
const linkQlKeyword = ref('');
const linkQlLoading = ref(false);
const linkQlPage = ref({ pageNo: 1, pageSize: 10 });
const linkQlTotal = ref(0);
const linkQlRecords = ref<StockOutListRowDto[]>([]);

const isNewDraft = computed(() => !headerBusNo.value);

const documentEditable = computed(() => isNewDraft.value || detailStatusEnum.value === 1);

const headerStatusText = computed(() => {
  if (isNewDraft.value) return '编辑中';
  return stockOutStatusLabel(detailStatusEnum.value);
});

const docActionFlags = computed(() => {
  const base = buildStockOutDocActionFlags({
    hasPersistedBusNo: Boolean(headerBusNo.value),
    isDraft: isNewDraft.value,
    status: detailStatusEnum.value,
  });
  const busy = saving.value;
  return {
    ...base,
    saveDisabled: base.saveDisabled || busy,
    saveAndSubmitDisabled: base.saveAndSubmitDisabled || busy,
  };
});

const canEditOriginalBusNo = computed(() => documentEditable.value && isNewDraft.value);

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
    Number.isFinite(qty) && Number.isFinite(unit) ? String(decimalMul(qty, unit, 2)) : '';
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
  return typeof total === 'number' ? total.toFixed(2) : String(total);
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
  const bands = normalizeInboundProductUnitBands(catalog?.unitBands);
  const rawQty = qty === undefined || qty === null ? 0 : Number(qty);

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
    remark: String(ln.remake ?? '').trim(),
    _occurrenceTime: ln.occurrenceTime ?? undefined,
    _reason: ln.reason,
  };

  if (bands.length > 0) {
    base.unitBands = bands;
    // 回显：itemQuantity 为 SPLIT；unitCode 为保存时单位；price/totalPrice 原样
    const hydrated = hydrateWarehouseDetailQtyFromPersisted({
      bands,
      persistedItemQuantity: rawQty,
      persistedUnitCode: unitCodeStr,
    });
    base.itemQuantity = hydrated.displayQty;
    base.unitCode = hydrated.unitCode;
    base.selectedBandKind = hydrated.bandKind;
    base.unitList = inboundUnitBandSelectOptions(bands);
    base.__prevBandKind = hydrated.bandKind;
  } else if (ln.unitCodeDictText) {
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
          sourceLocationId: detailSourceLocationId.value ?? props.sidebarSourceLocationId,
        }),
        onSelected: (product: Record<string, unknown>, row: StockOutboundPreviewRow) => {
          syncRowFromStockOutboundMedicinePick(product, row);
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
    field: 'remark',
    title: '备注',
    minWidth: 120,
    showOverflow: true,
    editRender: {
      name: 'ElInput',
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

const gridOptions: VxeGridProps<StockOutboundPreviewRow> = reactive({
  autoResize: true,
  height: '100%',
  border: true,
  stripe: true,
  showOverflow: true,
  showFooter: false,
  size: 'medium',
  columnConfig: { resizable: true },
  data: [],
  editConfig: {
    enabled: true,
    trigger: 'click',
    mode: 'cell',
    autoClear: true,
    beforeEditMethod: () => documentEditable.value,
  },
  columns,
  pagerConfig: { enabled: false },
});

const STOCK_OUT_EDITABLE_COLUMN_FIELDS = new Set(['_qtyUnit', 'medicinePick', 'remark']);

const STOCK_OUT_EDIT_FIELD_ORDER = ['medicinePick', '_qtyUnit', 'remark'] as const;

type StockOutEditField = (typeof STOCK_OUT_EDIT_FIELD_ORDER)[number];

/** 支持方向键上下换行的列（不含项目名称，避免误聚焦选品下拉） */
const STOCK_OUT_VERTICAL_NAV_FIELDS = new Set<StockOutEditField>(['_qtyUnit', 'remark']);

const stockOutGridKeyboardRef = ref<HTMLElement | null>(null);
const stockOutLastEditField = ref<StockOutEditField | undefined>(undefined);

type StockOutGridVm = {
  clearEdit?: () => void | Promise<void>;
  getEditRecord?: () => { column?: { field?: string }; row?: StockOutboundPreviewRow } | null;
  getFullData?: () => StockOutboundPreviewRow[];
  getRowNode?: (tr: Element) => { row?: StockOutboundPreviewRow } | null;
  scrollToRow?: (row: StockOutboundPreviewRow) => void | Promise<void>;
  setEditCell?: (row: StockOutboundPreviewRow, field: string) => void | Promise<void>;
};

function stockOutGridVm(): StockOutGridVm | undefined {
  return gridApi?.grid as unknown as StockOutGridVm | undefined;
}

function clearStockOutGridEdit() {
  void stockOutGridVm()?.clearEdit?.();
}

function isStockOutEditableColumn(column?: { field?: string; type?: string }): boolean {
  if (!column || column.type === 'checkbox' || column.type === 'seq') return false;
  const field = column.field;
  return field ? STOCK_OUT_EDITABLE_COLUMN_FIELDS.has(field) : false;
}

/** 解析当前编辑列（vxe 行编辑模式下 getEditRecord.column 不可靠，辅以最近点击列） */
function resolveStockOutActiveEditField(
  editRec?: { column?: { field?: string } } | null,
): StockOutEditField | undefined {
  const fromRec = editRec?.column?.field;
  if (fromRec && STOCK_OUT_EDITABLE_COLUMN_FIELDS.has(fromRec)) {
    return fromRec as StockOutEditField;
  }
  return stockOutLastEditField.value;
}

/** 点击可编辑列：同行切换列 / 跨行切换至点击列 */
function handleStockOutGridCellClick(params: {
  column?: { field?: string; type?: string };
  row: StockOutboundPreviewRow;
}) {
  if (!documentEditable.value) return;
  const g = stockOutGridVm();
  if (!g?.setEditCell) return;
  if (!isStockOutEditableColumn(params.column)) return;
  const field = params.column?.field as StockOutEditField | undefined;
  if (!field) return;

  const editingRow = g.getEditRecord?.()?.row;
  if (editingRow && editingRow !== params.row) {
    void g.clearEdit?.();
  }
  stockOutLastEditField.value = field;
  void nextTick(() => {
    void g.setEditCell?.(params.row, field);
  });
}

function handleStockOutSurfaceMouseDown(ev: MouseEvent) {
  if (!documentEditable.value) return;
  const target = ev.target as HTMLElement;
  if (target.closest('.el-popper, .el-select__popper, .el-picker-panel, .el-dialog, .el-overlay')) {
    return;
  }
  const g = stockOutGridVm();
  if (!g) return;
  const editingRow = g.getEditRecord?.()?.row;
  if (!editingRow) return;
  const rowEl = target.closest('tr.vxe-body--row');
  if (rowEl) {
    const node = g.getRowNode?.(rowEl);
    if (node?.row === editingRow) return;
    return;
  }
  clearStockOutGridEdit();
}

/** 输入框内仍有可移动光标时不抢方向键 */
function shouldKeepStockOutCaretNavigation(event: KeyboardEvent, target: HTMLElement): boolean {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
    return false;
  }
  if (!(target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement)) {
    return false;
  }
  if (target.readOnly || target.disabled || target.closest('.el-select, .el-tree-select')) {
    return false;
  }
  const { selectionEnd, selectionStart, value } = target;
  if (selectionStart === null || selectionEnd === null || selectionStart !== selectionEnd) {
    return selectionStart !== selectionEnd;
  }
  if (event.key === 'ArrowLeft') {
    return selectionStart > 0;
  }
  return selectionEnd < value.length;
}

/** 在表格 DOM 中定位指定数据行对应的 tr */
function findStockOutGridRowElement(row: StockOutboundPreviewRow): HTMLElement | null {
  const wrap = stockOutGridKeyboardRef.value;
  const g = stockOutGridVm();
  if (!wrap || !g?.getRowNode) return null;
  for (const tr of wrap.querySelectorAll('tr.vxe-body--row')) {
    const node = g.getRowNode(tr);
    if (node?.row === row) {
      return tr as HTMLElement;
    }
  }
  return null;
}

/** 编辑格激活后聚焦数量等输入控件 */
async function focusStockOutEditCell(field?: StockOutEditField, row?: StockOutboundPreviewRow) {
  await nextTick();
  await nextTick();
  const wrap = stockOutGridKeyboardRef.value;
  if (!wrap) return;
  const rowEl = row ? findStockOutGridRowElement(row) : null;
  const activeCell = (rowEl?.querySelector(
    '.vxe-body--column.col--active, .vxe-body--column.col--edit',
  ) ?? wrap.querySelector('.vxe-body--column.col--active')) as HTMLElement | null;
  const scope = activeCell ?? wrap;
  const focusables = collectFormFocusableElements(scope, EXTENDED_FORM_KEYBOARD_SELECTORS, true);
  if (!focusables.length) return;
  let target = focusables[0];
  if (field === '_qtyUnit') {
    target =
      focusables.find((el) => !!el.closest('.el-input-number')) ??
      focusables.find((el) => el.tagName === 'INPUT') ??
      focusables[0];
  }
  focusFormControl(target);
  if (target instanceof HTMLInputElement && target.type !== 'hidden') {
    target.select();
  }
}

/** 关闭行内选品下拉（避免 autofocus 抢焦点） */
function dismissStockOutMedicinePickDropdown() {
  for (const pulldown of document.querySelectorAll('.vxe-pulldown.is--active')) {
    const input = pulldown.querySelector('.vxe-input--inner') as HTMLElement | null;
    input?.blur();
  }
}

/** 定位指定行数量列内的数字输入框 */
function findStockOutQtyInput(row: StockOutboundPreviewRow): HTMLInputElement | null {
  const rowEl = findStockOutGridRowElement(row);
  if (!rowEl) return null;
  const qtyCell = rowEl.querySelector('td[colid="_qtyUnit"]') as HTMLElement | null;
  const scope = qtyCell ?? rowEl;
  return scope.querySelector('.el-input-number input:not([disabled])') as HTMLInputElement | null;
}

/** 强制聚焦数量输入（覆盖 vxe 默认 autofocus 到项目名称） */
function forceFocusStockOutQtyInput(row: StockOutboundPreviewRow) {
  dismissStockOutMedicinePickDropdown();
  const input = findStockOutQtyInput(row);
  if (!input) return;
  input.focus({ preventScroll: true });
  input.select();
}

/** 批量回填后：定位到指定行数量列并聚焦 */
async function focusStockOutRowQtyCell(row: StockOutboundPreviewRow) {
  const g = stockOutGridVm();
  if (!g?.setEditCell) return;
  clearStockOutGridEdit();
  await nextTick();
  stockOutLastEditField.value = '_qtyUnit';
  await g.setEditCell(row, '_qtyUnit');
  await nextTick();
  forceFocusStockOutQtyInput(row);
  requestAnimationFrame(() => {
    forceFocusStockOutQtyInput(row);
    setTimeout(() => forceFocusStockOutQtyInput(row), 40);
  });
}

/** 切换到指定行/列并聚焦（项目名称列不主动抢焦点，保留选品下拉回显） */
async function activateStockOutEditCell(row: StockOutboundPreviewRow, field: StockOutEditField) {
  const g = stockOutGridVm();
  if (!g?.setEditCell) return;
  stockOutLastEditField.value = field;
  await g.setEditCell(row, field);
  if (field !== 'medicinePick') {
    await focusStockOutEditCell(field);
  }
}

/** 查找同列上/下一行（跳过未选品空行） */
function findStockOutNeighborRow(
  rows: StockOutboundPreviewRow[],
  rowIndex: number,
  delta: -1 | 1,
): StockOutboundPreviewRow | undefined {
  for (let i = rowIndex + delta; i >= 0 && i < rows.length; i += delta) {
    const row = rows[i];
    if (row && isPresentItemId(row.itemId)) {
      return row;
    }
  }
  return undefined;
}

/** 明细表方向键：同列上下行、同行左右列 */
async function handleStockOutGridKeydown(event: KeyboardEvent) {
  if (!documentEditable.value) return;
  const key = event.key;
  if (!['ArrowDown', 'ArrowLeft', 'ArrowRight', 'ArrowUp'].includes(key)) {
    return;
  }
  const target = event.target as HTMLElement;
  if (
    isFormSelectDropdownTarget(target) ||
    isFormSelectExpanded(target) ||
    target.closest('.el-picker-panel, .el-date-picker, .vxe-pulldown, .vxe-pulldown--panel')
  ) {
    return;
  }
  const g = stockOutGridVm();
  const editRec = g?.getEditRecord?.();
  const currentRow = editRec?.row;
  const currentField = resolveStockOutActiveEditField(editRec);
  if (!g?.setEditCell || !currentRow || !currentField) {
    return;
  }
  if (!STOCK_OUT_EDITABLE_COLUMN_FIELDS.has(currentField)) {
    return;
  }
  if (currentField === 'medicinePick') {
    return;
  }

  const rows = g.getFullData?.() ?? collectGridRows();
  const rowIndex = rows.findIndex((row) => row === currentRow);
  if (rowIndex < 0) {
    return;
  }

  if (key === 'ArrowUp' || key === 'ArrowDown') {
    if (!STOCK_OUT_VERTICAL_NAV_FIELDS.has(currentField)) {
      return;
    }
    const neighbor = findStockOutNeighborRow(rows, rowIndex, key === 'ArrowUp' ? -1 : 1);
    if (!neighbor) return;
    event.preventDefault();
    event.stopPropagation();
    await activateStockOutEditCell(neighbor, currentField);
    return;
  }

  if (shouldKeepStockOutCaretNavigation(event, target)) {
    return;
  }

  const activeCell = target.closest('.vxe-body--column') as HTMLElement | null;
  if (activeCell) {
    const focusables = collectFormFocusableElements(
      activeCell,
      EXTENDED_FORM_KEYBOARD_SELECTORS,
      true,
    );
    const currentIndex = focusables.findIndex((el) => el === target || el.contains(target));
    if (currentIndex >= 0) {
      const delta = key === 'ArrowRight' ? 1 : -1;
      const nextIndex = currentIndex + delta;
      if (nextIndex >= 0 && nextIndex < focusables.length) {
        event.preventDefault();
        event.stopPropagation();
        focusFormControl(focusables[nextIndex]);
        return;
      }
    }
  }

  const fieldIndex = STOCK_OUT_EDIT_FIELD_ORDER.indexOf(currentField);
  if (fieldIndex < 0) {
    return;
  }
  const nextFieldIndex = key === 'ArrowRight' ? fieldIndex + 1 : fieldIndex - 1;
  if (nextFieldIndex < 0 || nextFieldIndex >= STOCK_OUT_EDIT_FIELD_ORDER.length) {
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  await activateStockOutEditCell(currentRow, STOCK_OUT_EDIT_FIELD_ORDER[nextFieldIndex]);
}

const [Grid, gridApi] = useVbenVxeGrid<StockOutboundPreviewRow>({
  gridOptions,
  gridEvents: {
    cellClick: handleStockOutGridCellClick,
    editClosed(params: { row: StockOutboundPreviewRow }) {
      recalcStockOutboundLineTotal(params.row);
    },
  },
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
});

function patchStockOutGridEditConfig() {
  gridOptions.editConfig = {
    ...gridOptions.editConfig,
    enabled: documentEditable.value,
    trigger: 'click',
    mode: 'cell',
    autoClear: true,
    beforeEditMethod: () => documentEditable.value,
  };
}

watch(
  () => documentEditable.value,
  () => {
    patchStockOutGridEditConfig();
    void nextTick(() => {
      const g = gridApi?.grid as undefined | { clearEdit?: () => void };
      if (!documentEditable.value) g?.clearEdit?.();
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

function collectGridRows(): StockOutboundPreviewRow[] {
  const g = gridApi?.grid as null | { getFullData?: () => StockOutboundPreviewRow[] };
  return g?.getFullData?.() ?? [];
}

/** 清空右侧编辑区，准备新建草稿。 */
function resetDetailForCreate() {
  headerBusNo.value = undefined;
  originalBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  const sid = normalizeLocationId(props.sidebarSourceLocationId);
  detailSourceLocationId.value = sid ?? props.warehouseOptions[0]?.locationId;
  const pid = normalizeLocationId(props.sidebarPurposeLocationId);
  purposeLocationId.value = pid ?? props.pharmacyOptions[0]?.locationId;
  initHeadApplyTimeFromContext();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchStockOutGridEditConfig();
  });
}

/** 按单号加载出库单详情并填充表单与明细。 */
async function loadDetail(busNo: string) {
  try {
    const res = await getStockOutDetailApi({ busNo });
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
      gridApi?.grid?.loadData(gridRows);
      patchStockOutGridEditConfig();
    });
  } catch {
    void nextTick(() => {
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
          ? Number(decimalMul(qty, unit, 2))
          : undefined;
    const { itemQuantity: persistedQty, unitCode: persistedUnit } =
      persistDisplayQtyAsSplitForBands({
        bands: r.unitBands ?? [],
        displayQty: r.itemQuantity,
        legacyUnitCode: r.unitCode,
        selectedBandKind: r.selectedBandKind,
      });
    return {
      itemTable: r.itemTable,
      itemId: r.itemId,
      itemQuantity: persistedQty,
      unitCode: persistedUnit,
      lotNumber: String(r.lotNumber ?? '').trim() || undefined,
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
  if (!documentEditable.value) {
    ElMessage.warning('当前状态不允许修改');
    return undefined;
  }
  const rows = collectGridRows();
  if (!validateBeforeSave(rows)) return undefined;

  saving.value = true;
  try {
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
  const no = await persistStockOut();
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
  if (!no) return;
  if (detailStatusEnum.value !== 1) {
    ElMessage.warning('仅待审核单据可删除');
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
  if (!insuranceContractBusNo.value?.trim()) {
    ElMessage.warning('请选择医保契约');
    return;
  }
  const approverId = resolveLoginPractitionerIdForRequest(
    userStore.userInfo as null | Record<string, unknown>,
  );
  try {
    await agreeStockOutApi({
      busNo: no,
      approverId,
      approvalClock: nowIsoLocal(),
      contractBusNo: insuranceContractBusNo.value,
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

async function addItem() {
  if (!documentEditable.value) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const record = createEmptyStockOutboundRow();
  const { row } = await $grid.insertAt(record, null);
  await $grid.$nextTick();
  if (row) {
    await $grid.scrollToRow(row);
    await $grid.setEditCell(row, 'medicinePick');
  }
}

async function deleteItem(row: 'select' | StockOutboundPreviewRow) {
  if (!documentEditable.value) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const checked = $grid.getCheckboxRecords() as StockOutboundPreviewRow[];
  if (row === 'select' && checked.length === 0) return;
  await $grid.remove(row === 'select' ? checked : row);
}

/** 未选品的空行（批量回填前清理，避免多出一行） */
function isStockOutRowWithoutProduct(row: StockOutboundPreviewRow): boolean {
  return !isPresentItemId(row.itemId);
}

/** 移除明细表中未选品的空行 */
async function removeEmptyStockOutRows() {
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const rows = ($grid.getFullData?.() ?? []) as StockOutboundPreviewRow[];
  const emptyRows = rows.filter(isStockOutRowWithoutProduct);
  if (!emptyRows.length) return;
  await $grid.remove(emptyRows);
}

const batchDialogVisible = ref(false);

/** 打开批量选品弹窗（与单行选品共用 sourceLocationId / productTableName） */
function openBatchDialog() {
  if (!documentEditable.value) return;
  const src = detailSourceLocationId.value;
  if (src === undefined || src === null || String(src).trim() === '') {
    ElMessage.warning('请先选择源药库');
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
  clearStockOutGridEdit();
  await removeEmptyStockOutRows();
  for (const product of products) {
    const record = createEmptyStockOutboundRow();
    syncRowFromStockOutboundMedicinePick(product, record);
    await $grid.insertAt(record, null);
  }
  await $grid.$nextTick();
  await nextTick();
  const allRows = ($grid.getFullData?.() ?? collectGridRows()) as StockOutboundPreviewRow[];
  const firstInserted = allRows[Math.max(0, allRows.length - products.length)];
  if (firstInserted && isPresentItemId(firstInserted.itemId)) {
    await $grid.scrollToRow(firstInserted);
    await focusStockOutRowQtyCell(firstInserted);
  }
  ElMessage.success(`已添加 ${products.length} 条药品，请补充数量等信息`);
}

async function openLinkQlDialog() {
  if (!canEditOriginalBusNo.value) {
    ElMessage.warning('仅新建单据时可关联请领单，或请先点「新增」');
    return;
  }
  const src = detailSourceLocationId.value ?? normalizeLocationId(props.sidebarSourceLocationId);
  if (src === undefined || src === null) {
    ElMessage.warning('请先选择源药库');
    return;
  }
  linkQlVisible.value = true;
  linkQlKeyword.value = '';
  linkQlPage.value.pageNo = 1;
  await fetchLinkablePharmacyRequisitions();
}

async function fetchLinkablePharmacyRequisitions() {
  const src = detailSourceLocationId.value ?? normalizeLocationId(props.sidebarSourceLocationId);
  if (src === undefined || src === null) {
    linkQlRecords.value = [];
    linkQlTotal.value = 0;
    return;
  }
  linkQlLoading.value = true;
  try {
    const res = await pageLinkablePharmacyRequisitionsApi({
      sourceLocationId: src,
      keyword: linkQlKeyword.value.trim() || undefined,
      pageNo: linkQlPage.value.pageNo,
      pageSize: linkQlPage.value.pageSize,
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
    const res = await getPharmacyRequisitionDetailApi({ busNo: qlNo });
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到请领单详情');
      return;
    }
    originalBusNo.value = qlNo;
    if (data.sourceLocationId !== undefined && data.sourceLocationId !== null) {
      detailSourceLocationId.value = data.sourceLocationId;
    }
    if (data.purposeLocationId !== undefined && data.purposeLocationId !== null) {
      purposeLocationId.value = data.purposeLocationId;
    }
    const gridRows = (data.lines ?? []).map((ln, i) =>
      mapStockOutDetailLineToGrid(ln as StockOutDetailLineDto, i),
    );
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchStockOutGridEditConfig();
    });
    linkQlVisible.value = false;
    ElMessage.success('已带入请领明细，请核对数量后保存');
  } catch {
    /* handled */
  }
}

async function syncFromParentContext() {
  if (props.isDraftOrder) {
    resetDetailForCreate();
    return;
  }
  const bn = String(props.busNo ?? '').trim();
  if (bn) {
    await loadDetail(bn);
  } else {
    resetDetailForCreate();
  }
}

watch(
  () => [props.busNo, props.isDraftOrder, props.resetKey] as const,
  () => {
    void syncFromParentContext();
  },
  { immediate: true },
);

watch(
  () => props.sidebarSourceLocationId,
  () => {
    if (props.isDraftOrder || !headerBusNo.value) {
      const sid = normalizeLocationId(props.sidebarSourceLocationId);
      if (sid !== undefined) {
        detailSourceLocationId.value = sid;
      }
    }
  },
);

watch(
  () => props.sidebarPurposeLocationId,
  () => {
    if (props.isDraftOrder || !headerBusNo.value) {
      const pid = normalizeLocationId(props.sidebarPurposeLocationId);
      if (pid !== undefined) {
        purposeLocationId.value = pid;
      }
    }
  },
);

watch(
  () => props.pharmacyOptions,
  (list) => {
    if (list.length === 0) return;
    if (!purposeLocationId.value && (isNewDraft.value || !headerBusNo.value)) {
      const pid = normalizeLocationId(props.sidebarPurposeLocationId);
      purposeLocationId.value = pid ?? list[0]?.locationId;
    }
  },
  { deep: true },
);

watch(
  () => props.warehouseOptions,
  (list) => {
    if (list.length === 0) return;
    if (!detailSourceLocationId.value && (isNewDraft.value || !headerBusNo.value)) {
      const sid = normalizeLocationId(props.sidebarSourceLocationId);
      detailSourceLocationId.value = sid ?? list[0]?.locationId;
    }
  },
  { deep: true },
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

onMounted(() => {
  void loadInsuranceContractBusNo();
});
</script>

<template>
  <div
    class="stock-out-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
    :class="{ 'stock-out-main-pane--flash': paneFlashActive }"
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
        @mousedown="handleStockOutSurfaceMouseDown"
      >
        <div class="stock-out-head-panel shrink-0">
          <div
            class="stock-out-head-panel__bar flex flex-wrap items-center justify-between gap-x-4 gap-y-2"
          >
            <SectionTitle title="出库单信息" />
            <div class="flex flex-wrap items-center gap-2">
              <span class="text-xs text-[var(--el-text-color-secondary)]">状态</span>
              <ElTag size="small" type="info" effect="plain">{{ headerStatusText || '—' }}</ElTag>
            </div>
          </div>
          <ElForm label-width="88px" class="compact-head-form stock-out-head-form">
            <div
              class="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5"
            >
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem label="源药库">
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
              <ElFormItem label="目的药房">
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

        <div
          ref="stockOutGridKeyboardRef"
          class="stock-out-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col"
          @keydown.capture="handleStockOutGridKeydown"
        >
          <Grid class="min-h-0 flex-1">
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
                @click="deleteItem(row as StockOutboundPreviewRow)"
              >
                <Trash2 class="size-4" />
                删除
              </ElButton>
            </template>
          </Grid>
        </div>
      </div>
    </InvMainShell>

    <StockOutBatchAddDialog
      v-model:visible="batchDialogVisible"
      :source-location-id="detailSourceLocationId"
      :product-table-name="headItemTableType"
      @confirm="handleBatchConfirm"
    />

    <ElDialog
      v-model="linkQlVisible"
      destroy-on-close
      title="选择已同意的药房请领单"
      width="720px"
      @opened="fetchLinkablePharmacyRequisitions"
    >
      <div class="mb-2 flex gap-2">
        <ElInput
          v-model="linkQlKeyword"
          clearable
          placeholder="请领单号/关键字"
          size="small"
          style="max-width: 260px"
          @keyup.enter="fetchLinkablePharmacyRequisitions"
        />
        <ElButton size="small" type="primary" @click="fetchLinkablePharmacyRequisitions">
          查询
        </ElButton>
      </div>
      <ElTable
        v-loading="linkQlLoading"
        :data="linkQlRecords"
        border
        height="320"
        size="small"
        stripe
        @row-dblclick="(r: unknown) => applyLinkablePharmacyRequisition(r as StockOutListRowDto)"
      >
        <ElTableColumn label="请领单号" min-width="140" prop="busNo" show-overflow-tooltip />
        <ElTableColumn label="状态" width="72">
          <template #default="{ row }">
            {{ stockOutStatusLabel((row as StockOutListRowDto).statusEnum) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="申请时间" min-width="132" show-overflow-tooltip>
          <template #default="{ row }">
            {{
              (row as StockOutListRowDto).applyTime
                ? formatDateTime((row as StockOutListRowDto).applyTime as string)
                : ''
            }}
          </template>
        </ElTableColumn>
        <ElTableColumn fixed="right" label="操作" width="88">
          <template #default="{ row }">
            <ElButton
              link
              size="small"
              type="primary"
              @click="applyLinkablePharmacyRequisition(row as StockOutListRowDto)"
            >
              选用
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <ElPagination
        v-model:current-page="linkQlPage.pageNo"
        v-model:page-size="linkQlPage.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="linkQlTotal"
        class="mt-2"
        background
        layout="total, sizes, prev, pager, next"
        small
        @current-change="fetchLinkablePharmacyRequisitions"
        @size-change="fetchLinkablePharmacyRequisitions"
      />
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.stock-out-head-panel {
  padding: 12px 14px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.stock-out-head-form {
  margin-top: 10px;
}

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

.stock-out-main-pane__grid-wrap :deep(.vxe-grid) {
  flex: 1;
  min-height: 0;
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
