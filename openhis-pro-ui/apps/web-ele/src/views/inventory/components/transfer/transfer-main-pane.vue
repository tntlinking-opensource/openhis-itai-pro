<!--
  调拨入库/出库右侧主区：InvMainShell + InvDocActions + 单头 + 明细表格。
-->
<script setup lang="ts">
import type { VxeGridPropTypes } from '#/adapter/vxe-table';
import type { ApiOptionItem } from '#/types/api-response';

import type { FormInstance } from 'element-plus';

import { computed, nextTick, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvDocDetailGridShell from '@inventory/components/inv-doc-detail-grid-shell.vue';
import InvDocGridToolbar from '@inventory/components/inv-doc-grid-toolbar.vue';
import InvDocTraceScanBridge from '@inventory/components/inv-doc-trace-scan-bridge.vue';
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
  agreeTransferInApi,
  createTransferInApi,
  getTransferInDetailApi,
  rejectTransferInApi,
  removeTransferInApi,
  submitTransferInApprovalApi,
  updateTransferInApi,
  withdrawTransferInApprovalApi,
  WHS_TRANSFER_IN_CATEGORY_ENUM,
  type TransferInDetailLineDto,
  type TransferInLineDto,
} from '#/api/inventory/warehouse/transfer-in';
import {
  agreeTransferOutApi,
  createTransferOutApi,
  getTransferOutDetailApi,
  rejectTransferOutApi,
  removeTransferOutApi,
  submitTransferOutApprovalApi,
  updateTransferOutApi,
  withdrawTransferOutApprovalApi,
  WHS_TRANSFER_OUT_CATEGORY_ENUM,
  type TransferOutDetailLineDto,
  type TransferOutLineDto,
} from '#/api/inventory/warehouse/transfer-out';
import { getTransferInDetailApi as getDeptTransferInDetailApi } from '#/api/inventory/department/transfer-in';
import {
  agreeTransferOutApi as agreeDeptTransferOutApi,
  createTransferOutApi as createDeptTransferOutApi,
  DEPT_TRANSFER_OUT_CATEGORY_ENUM,
  getTransferOutDetailApi as getDeptTransferOutDetailApi,
  rejectTransferOutApi as rejectDeptTransferOutApi,
  removeTransferOutApi as removeDeptTransferOutApi,
  submitTransferOutApprovalApi as submitDeptTransferOutApprovalApi,
  updateTransferOutApi as updateDeptTransferOutApi,
  withdrawTransferOutApprovalApi as withdrawDeptTransferOutApprovalApi,
} from '#/api/inventory/department/transfer-out';
import {
  agreeTransferInApi as agreePharmacyTransferInApi,
  createTransferInApi as createPharmacyTransferInApi,
  getTransferInDetailApi as getPharmacyTransferInDetailApi,
  rejectTransferInApi as rejectPharmacyTransferInApi,
  removeTransferInApi as removePharmacyTransferInApi,
  submitTransferInApprovalApi as submitPharmacyTransferInApprovalApi,
  updateTransferInApi as updatePharmacyTransferInApi,
  withdrawTransferInApprovalApi as withdrawPharmacyTransferInApprovalApi,
  WHS_TRANSFER_IN_CATEGORY_ENUM as PHA_TRANSFER_IN_CATEGORY_ENUM,
} from '#/api/inventory/pharmacy/transfer-in';
import {
  agreeTransferOutApi as agreePharmacyTransferOutApi,
  createTransferOutApi as createPharmacyTransferOutApi,
  getTransferOutDetailApi as getPharmacyTransferOutDetailApi,
  rejectTransferOutApi as rejectPharmacyTransferOutApi,
  removeTransferOutApi as removePharmacyTransferOutApi,
  submitTransferOutApprovalApi as submitPharmacyTransferOutApprovalApi,
  updateTransferOutApi as updatePharmacyTransferOutApi,
  withdrawTransferOutApprovalApi as withdrawPharmacyTransferOutApprovalApi,
  WHS_TRANSFER_OUT_CATEGORY_ENUM as PHA_TRANSFER_OUT_CATEGORY_ENUM,
} from '#/api/inventory/pharmacy/transfer-out';
import SectionTitle from '#/components/layouts/section-title.vue';
import {
  formatInventoryLineTotal,
  formatMoneyDisplay,
  inventoryLineTotalNumber,
} from '#/utils/format/money-scale';
import {
  inventoryPickAvailableQtyAsStockDisplay,
  inventoryPickMedicineCell,
  inventoryPickNumberField,
  inventoryPickStringField,
} from '#/utils/inventory/inventory-product-pick-list';
import {
  canConvertDisplayQtyToSplitForBands,
  convertInboundQtyBetweenBandKinds,
  persistDisplayQtyAsSplitForBands,
  resolveInboundLinePersistUnitCodeForBands,
} from '#/utils/inventory/inbound-product-unit-bands';
import {
  applyHydratedInventoryDetailQtyToRow,
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
  isStockOutDocumentEditableStatus,
  isStockOutDocumentRemovableStatus,
  buildStockOutDocActionFlags,
  stockOutStatusLabel,
  normalizeStockOutStatusSort,
} from '#/utils/inventory/stock-out-doc-actions';
import { INV_DOC_READONLY_SHELL_HINT } from '#/utils/inventory/inventory-doc-restrictions';
import {
  buildInventoryDocPractitionerSelectOptions,
  INV_ITEM_TABLE_TYPE_DEVICE,
  INV_ITEM_TABLE_TYPE_MEDICATION,
  coercePractitionerIdForSelect,
  normalizeInventoryItemTableTypeOptions,
  resolveDefaultInventoryItemTableType,
  resolveDefaultPractitionerIdForSelect,
  resolveDocHeadProductPickLocationId,
  resolveFirstRealLocationIdFromInitOptions,
} from '#/utils/inventory/inventory-workbench-list';
import {
  buildInventoryLocationHeadFormRules,
  clearInventoryHeadFormValidation,
  validateInventoryHeadFormFields,
} from '#/utils/inventory/inventory-doc-head-form-validation';
import { buildInvDocumentTitle, printInvDocument } from '#/utils/print/print-inv-document';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';
import { useUserStore } from '@vben/stores';

defineOptions({ name: 'InventoryTransferMainPane' });

type TransferVariant = 'in' | 'out';
type TransferDomain = 'department' | 'pharmacy' | 'warehouse';

interface MedicinePickCell {
  id?: number | string;
  name?: string;
}

interface TransferPreviewRow {
  id: string;
  medicinePick?: MedicinePickCell | Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  inventoryItemId?: number | string;
  productName: string;
  /** 药品分类码；中药=4 时单价三位展示 */
  categoryCode?: string;
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
  sourceStockQuantity: number;
  lotNumber: string;
  traceNo: string;
  remake: string;
}

interface Props {
  variant: TransferVariant;
  domain?: TransferDomain;
  busNo?: string;
  isDraftOrder?: boolean;
  resetKey?: number;
  warehouseOptions: ApiOptionItem[];
  pharmacyOptions: ApiOptionItem[];
  practitionerOptions?: ApiOptionItem[];
  itemTableTypeOptions?: ApiOptionItem[];
  headSectionTitle?: string;
}

const props = withDefaults(defineProps<Props>(), {
  domain: 'warehouse',
  busNo: '',
  isDraftOrder: false,
  resetKey: 0,
  warehouseOptions: () => [],
  pharmacyOptions: () => [],
  practitionerOptions: () => [],
  itemTableTypeOptions: () => [],
  headSectionTitle: '调拨单信息',
});

const emit = defineEmits<{
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

const userStore = useUserStore();

const isPharmacyDomain = computed(() => props.domain === 'pharmacy');
const isDepartmentDomain = computed(() => props.domain === 'department');
const isReadOnlyShell = computed(() => props.variant === 'in');

/** 选品源库位选项池（调出侧）。 */
const productPickSourceLocationOptions = computed((): ApiOptionItem[] => {
  if (isDepartmentDomain.value || isPharmacyDomain.value) return props.pharmacyOptions;
  return props.warehouseOptions;
});

const transferCategoryEnum = computed(() => {
  if (isDepartmentDomain.value) {
    return DEPT_TRANSFER_OUT_CATEGORY_ENUM;
  }
  if (props.variant === 'in') {
    return isPharmacyDomain.value ? PHA_TRANSFER_IN_CATEGORY_ENUM : WHS_TRANSFER_IN_CATEGORY_ENUM;
  }
  return isPharmacyDomain.value ? PHA_TRANSFER_OUT_CATEGORY_ENUM : WHS_TRANSFER_OUT_CATEGORY_ENUM;
});

const sourceTypeEnum = computed(() => {
  if (isDepartmentDomain.value || isPharmacyDomain.value) return 'PHARMACY';
  return 'CABINET';
});

/** 科室/药房/药库均为平级调拨：源与目的同级，禁止跨药房↔药库。 */
const purposeTypeEnum = computed(() => {
  if (isDepartmentDomain.value || isPharmacyDomain.value) return 'PHARMACY';
  return 'CABINET';
});

const listSource = computed(() => {
  if (isDepartmentDomain.value) {
    return props.variant === 'in' ? 'dept-transfer-in' : 'dept-transfer-out';
  }
  const prefix = isPharmacyDomain.value ? 'pharmacy' : 'warehouse';
  return props.variant === 'in' ? `${prefix}-transfer-in` : `${prefix}-transfer-out`;
});

const normalizedItemTableTypeOptions = computed(() =>
  normalizeInventoryItemTableTypeOptions(props.itemTableTypeOptions),
);

/** 科室默认耗材，药库/药房默认药品。 */
function preferredItemTableType(): string {
  return isDepartmentDomain.value ? INV_ITEM_TABLE_TYPE_DEVICE : INV_ITEM_TABLE_TYPE_MEDICATION;
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

/** 详情回显时把经手人 id pin 进下拉（无姓名时仍 pin，避免 ElSelect 空白）。 */
function pinPractitionerOption(id: unknown, label?: string) {
  const v = String(id ?? '').trim();
  if (v === '') return;
  if (effectivePractitionerOptions.value.some((o) => String(o.value ?? '').trim() === v)) return;
  const lbl = String(label ?? '').trim();
  pinnedPractitionerOptions.value = [{ label: lbl !== '' && lbl !== v ? lbl : v, value: v }];
}

const sourceLocationLabel = computed(() => {
  if (isDepartmentDomain.value) return '调出科室药房';
  if (isPharmacyDomain.value) return '调出药房';
  return '调出药库';
});

const purposeLocationLabel = computed(() => {
  if (isDepartmentDomain.value) return '调入科室药房';
  if (isPharmacyDomain.value) return '调入药房';
  return '调入药库';
});

/** 科室「调出/调入科室药房」字数更长，加宽 label，避免折行叠字。 */
const headFormLabelWidth = computed(() => (isDepartmentDomain.value ? '120px' : '96px'));

/** 单头调出库位：科室/药房用药房选项，药库用库房选项（平级调拨）。 */
const sourceLocationOptions = computed(() => {
  const base =
    isDepartmentDomain.value || isPharmacyDomain.value
      ? props.pharmacyOptions
      : props.warehouseOptions;
  return filterTransferLocationOptionsExcluding(base, purposeLocationId.value);
});

/** 单头调入库位：与调出同级；排除已选调出库位。 */
const purposeLocationOptions = computed(() => {
  const base =
    isDepartmentDomain.value || isPharmacyDomain.value
      ? props.pharmacyOptions
      : props.warehouseOptions;
  return filterTransferLocationOptionsExcluding(base, sourceLocationId.value);
});

const transferSameLocationWarning = computed(() => {
  if (isDepartmentDomain.value) return '调出科室药房与调入科室药房不能相同';
  if (isPharmacyDomain.value) return '调出药房与调入药房不能相同';
  return '调出药库与调入药库不能相同';
});

function isSameTransferLocationId(a?: null | number | string, b?: null | number | string): boolean {
  if (a === undefined || a === null || b === undefined || b === null) return false;
  const sa = String(a).trim();
  const sb = String(b).trim();
  return sa !== '' && sa === sb;
}

function filterTransferLocationOptionsExcluding(
  options: ApiOptionItem[],
  excludeId?: null | number | string,
): ApiOptionItem[] {
  if (excludeId === undefined || excludeId === null || String(excludeId).trim() === '') {
    return options;
  }
  return options.filter((o) => !isSameTransferLocationId(o.value, excludeId));
}

function resolveDistinctLocationIdFromOptions(
  options: ApiOptionItem[],
  excludeId?: null | number | string,
): number | string | undefined {
  const filtered = filterTransferLocationOptionsExcluding(options, excludeId);
  const id = resolveFirstRealLocationIdFromInitOptions(filtered);
  return id === undefined ? undefined : id;
}

function transferLocationPoolOptions(): ApiOptionItem[] {
  if (isDepartmentDomain.value || isPharmacyDomain.value) {
    return props.pharmacyOptions;
  }
  return props.warehouseOptions;
}

const headerBusNo = ref<string | undefined>(undefined);
const originalBusNo = ref<string | undefined>(undefined);
const sourceLocationId = ref<number | string | undefined>(undefined);
const purposeLocationId = ref<number | string | undefined>(undefined);
const headFormRef = ref<FormInstance>();

const headFormModel = computed(() => ({
  sourceLocationId: sourceLocationId.value ?? '',
  purposeLocationId: purposeLocationId.value ?? '',
}));

const headFormRules = computed(() =>
  buildInventoryLocationHeadFormRules({
    sourceLocationId: sourceLocationLabel.value,
    purposeLocationId: purposeLocationLabel.value,
  }),
);

/** 选品门禁：跟单头「调出库位」，不用 init 首项（与出库/盘点同构）。 */
const productPickGateLocationId = computed(() =>
  resolveDocHeadProductPickLocationId(
    sourceLocationId.value,
    productPickSourceLocationOptions.value,
  ),
);

const practitionerId = ref<number | string | undefined>(undefined);
const headItemTableType = ref(firstItemTableTypeValue());
const detailStatusEnum = ref<number | undefined>(undefined);
const headApplyTime = ref(formatDateTime(new Date()));
const saving = ref(false);

const isNewDraft = computed(() => !headerBusNo.value);
const documentEditable = computed(() => {
  if (isReadOnlyShell.value) return false;
  return isNewDraft.value || isStockOutDocumentEditableStatus(detailStatusEnum.value);
});

const headerStatusText = computed(() => {
  if (isNewDraft.value) return '编辑中';
  return stockOutStatusLabel(detailStatusEnum.value);
});

const docActionFlags = computed(() => {
  const hasPersistedBusNo = Boolean(headerBusNo.value);
  const base = buildStockOutDocActionFlags({
    hasPersistedBusNo,
    isDraft: isNewDraft.value,
    status: detailStatusEnum.value,
  });
  const busy = saving.value;
  // 调拨入库只读壳：仅开放打印，禁掉制单/审驳
  if (isReadOnlyShell.value) {
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
  }
  return {
    ...base,
    saveDisabled: base.saveDisabled || busy,
    saveAndSubmitDisabled: base.saveAndSubmitDisabled || busy,
    printDisabled: busy || !hasPersistedBusNo,
  };
});

function resolveOccurrenceTimeForApi(): string {
  const t = String(headApplyTime.value ?? '').trim();
  if (t === '') return formatDateTime(new Date());
  return t.includes('T') ? t.replace('T', ' ') : t;
}

function formatTransferDetailTotalAmount(total: unknown): string {
  if (total === undefined || total === null || total === '') return '';
  const n = Number(total);
  if (!Number.isFinite(n)) return String(total).trim();
  return formatMoneyDisplay(n);
}

/** 编辑态：按数量 × 单价刷新金额（详情 load 不重算 totalPrice）。 */
function recalcTransferLineTotal(row: TransferPreviewRow) {
  const qty = Number(row.itemQuantity);
  const unit = Number(row.price);
  row.totalPrice =
    Number.isFinite(qty) && Number.isFinite(unit) ? formatInventoryLineTotal(qty, unit) : '';
}

function transferDetailLineProductName(
  ln: TransferInDetailLineDto | TransferOutDetailLineDto,
  catalog?: { merchandiseName?: string } | null,
): string {
  const top = String(ln.productName ?? '').trim();
  if (top) return top;
  return String(catalog?.merchandiseName ?? '').trim();
}

function transferDetailLineSpecification(
  ln: TransferInDetailLineDto | TransferOutDetailLineDto,
  catalog?: { totalVolume?: string } | null,
): string {
  const top = String(ln.specification ?? '').trim();
  if (top) return top;
  return String(catalog?.totalVolume ?? '').trim();
}

/** 选品回填源库存：与下拉「库存数量」列同源。 */
function resolveTransferSourceStockQtyFromPick(pick: Record<string, unknown>): number {
  if (
    Object.prototype.hasOwnProperty.call(pick, 'stockAvailableQty') &&
    pick.stockAvailableQty !== undefined &&
    pick.stockAvailableQty !== null
  ) {
    return inventoryPickNumberField(pick, 'stockAvailableQty');
  }
  return inventoryPickAvailableQtyAsStockDisplay(pick);
}

function syncRowFromProductPick(pick: Record<string, unknown>, row: TransferPreviewRow) {
  row.medicinePick =
    inventoryPickMedicineCell(pick) ?? (pick as TransferPreviewRow['medicinePick']);
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
  applyWarehouseNonInboundProductPickUnitsToRow(pick, row);
  row.sourceStockQuantity = resolveTransferSourceStockQtyFromPick(pick);
  recalcTransferLineTotal(row);
}

function handleTransferUnitBandChanged(row: TransferPreviewRow) {
  const newK = String(row.selectedBandKind ?? '')
    .trim()
    .toUpperCase();
  const oldK = String(row.__prevBandKind ?? '')
    .trim()
    .toUpperCase();
  const bands = row.unitBands ?? [];
  if (oldK && newK && oldK !== newK && oldK !== 'LEGACY' && newK !== 'LEGACY' && bands.length > 0) {
    const nSource = convertInboundQtyBetweenBandKinds(row.sourceStockQuantity, oldK, newK, bands);
    if (nSource !== undefined) {
      row.sourceStockQuantity = nSource;
    }
  }
  handleWarehouseNonInboundUnitBandChanged(row, {
    getQty: () => Number(row.itemQuantity),
    setQty: (qty) => {
      row.itemQuantity = qty;
    },
    onAfterChanged: () => recalcTransferLineTotal(row),
  });
}

function createEmptyRow(): TransferPreviewRow {
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
    selectedBandKind: 'LEGACY',
    __prevBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: '单位' }],
    price: 0,
    totalPrice: '',
    sourceStockQuantity: 0,
    lotNumber: '',
    traceNo: '',
    remake: '',
  };
}

function mapDetailLineToGrid(
  ln: TransferInDetailLineDto | TransferOutDetailLineDto,
  rowIdx: number,
): TransferPreviewRow {
  const itemIdRaw = ln.itemId;
  const catalog = ln.productCatalog ?? undefined;
  const idStr = ln.id !== undefined && ln.id !== null ? `ln-${String(ln.id)}` : `ln-x-${rowIdx}`;
  const name = transferDetailLineProductName(ln, catalog);
  const unitCodeStr = String(ln.unitCode ?? '');
  const rawQty = ln.itemQuantity === undefined ? undefined : Number(ln.itemQuantity);
  const unitCodeDictText = String(ln.unitCodeDictText ?? ln.unitCode ?? '');

  const row: TransferPreviewRow = {
    id: idStr,
    medicinePick: name ? { id: itemIdRaw as number | string | undefined, name } : undefined,
    itemTable: ln.itemTable,
    itemId: itemIdRaw,
    productName: name,
    spec: transferDetailLineSpecification(ln, catalog),
    manufacturer: String(ln.manufacturer ?? '').trim(),
    itemQuantity: rawQty,
    unitCode: unitCodeStr,
    unitCodeDictText,
    selectedBandKind: 'LEGACY',
    unitList: [{ value: 'LEGACY', label: unitCodeStr.trim() === '' ? '单位' : unitCodeStr }],
    __prevBandKind: 'LEGACY',
    price: ln.price === undefined || ln.price === null ? 0 : Number(ln.price),
    totalPrice: formatTransferDetailTotalAmount(ln.totalPrice),
    sourceStockQuantity: 0,
    lotNumber: String(ln.lotNumber ?? ''),
    traceNo: String(ln.traceNo ?? ''),
    remake: String(ln.remake ?? ''),
  };

  const hydrated = hydrateInventoryDetailLineQtyForGrid({
    itemQuantity: rawQty,
    unitCode: unitCodeStr,
    unitCodeDictText,
    productCatalog: catalog,
  });
  applyHydratedInventoryDetailQtyToRow(row, hydrated);
  if (!row.unitBands?.length && ln.unitCodeDictText) {
    row.unitList = [
      {
        value: 'LEGACY',
        label: String(ln.unitCodeDictText).trim() || unitCodeStr || '单位',
      },
    ];
  }
  // 源库存：详情 availableQuantity 为拆零，换算到当前展示计量带（与选品/切单位口径一致）
  const availRaw = (ln as { availableQuantity?: number | string | null }).availableQuantity;
  if (availRaw !== undefined && availRaw !== null) {
    const splitAvail = Number(availRaw);
    if (Number.isFinite(splitAvail)) {
      const bands = row.unitBands ?? [];
      const bandKind = String(row.selectedBandKind ?? '')
        .trim()
        .toUpperCase();
      if (bands.length > 0 && bandKind && bandKind !== 'LEGACY' && bandKind !== 'SPLIT') {
        const displayAvail = convertInboundQtyBetweenBandKinds(
          splitAvail,
          'SPLIT',
          bandKind,
          bands,
        );
        row.sourceStockQuantity =
          displayAvail !== undefined && Number.isFinite(displayAvail) ? displayAvail : splitAvail;
      } else {
        row.sourceStockQuantity = splitAvail;
      }
    }
  }
  return row;
}

function isTransferRowComplete(row: TransferPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.itemQuantity);
  if (!Number.isFinite(qty) || qty <= 0) return false;
  return !!String(row.unitCode ?? '').trim();
}

/** 选品后进入整行表单并聚焦数量 */
function handleTransferProductPick(row: TransferPreviewRow) {
  scheduleInventoryProductPickFocus(transferGridEdit, row);
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
          sourceLocationId: productPickGateLocationId.value,
        }),
        onSelected: (product: Record<string, unknown>, row: TransferPreviewRow) => {
          syncRowFromProductPick(product, row);
          handleTransferProductPick(row);
        },
      },
    },
    showOverflow: true,
  },
  { field: 'spec', title: '规格', width: 110, showOverflow: true },
  { field: 'manufacturer', title: '厂家', minWidth: 100, showOverflow: true },
  {
    field: '_sourceStockQtyUnit',
    title: '源库存数量',
    width: 120,
    align: 'center',
    headerAlign: 'center',
    cellRender: {
      name: 'InventoryQtyUnitReadonlyCell',
      props: {
        qtyField: 'sourceStockQuantity',
        unitField: 'selectedBandKind',
      },
    },
  },
  createInventoryQtyUnitColumn({
    title: '调拨数量',
    onUnitChange: (row) => handleTransferUnitBandChanged(row as TransferPreviewRow),
  }),
  {
    field: 'price',
    title: '单价',
    width: 110,
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
    title: '金额',
    width: 102,
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
    editRender: { name: 'InventoryPersistElInput', props: { maxlength: 64, clearable: true } },
    showOverflow: true,
  },
  {
    field: 'traceNo',
    title: '追溯码',
    minWidth: 140,
    editRender: { name: 'InventoryTraceNoCell', props: { maxlength: 512, clearable: true } },
  },
  {
    field: 'remake',
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

const TRANSFER_EDITABLE_COLUMN_FIELDS = new Set([
  '_qtyUnit',
  'lotNumber',
  'medicinePick',
  'remake',
  'traceNo',
]);

const TRANSFER_EDIT_FIELD_ORDER = [
  'medicinePick',
  '_qtyUnit',
  'lotNumber',
  'traceNo',
  'remake',
] as const;

type TransferEditField = (typeof TRANSFER_EDIT_FIELD_ORDER)[number];

const TRANSFER_VERTICAL_NAV_FIELDS = new Set<TransferEditField>([
  '_qtyUnit',
  'lotNumber',
  'remake',
  'traceNo',
]);

const {
  Grid,
  gridApi,
  bindGridWrap,
  gridEdit: transferGridEdit,
  collectGridRows,
  patchGridEditConfig: patchTransferGridEditConfig,
} = useInvDocDetailGrid<TransferPreviewRow>({
  columns,
  documentEditable: () => documentEditable.value,
  beforeEditMethod: () => documentEditable.value,
  onEditClosed: (row) => recalcTransferLineTotal(row),
  config: {
    rowKey: (row) => row.id,
    productField: 'medicinePick',
    editFieldOrder: TRANSFER_EDIT_FIELD_ORDER,
    editableFields: TRANSFER_EDITABLE_COLUMN_FIELDS,
    verticalNavFields: TRANSFER_VERTICAL_NAV_FIELDS,
    firstFillFieldAfterPick: '_qtyUnit',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isTransferRowComplete,
  },
});

const { addItem: addTransferRow, deleteItem } = useInvDocGridRowOps({
  documentEditable: () => documentEditable.value,
  getGridApi: () => gridApi ?? null,
  gridEdit: transferGridEdit,
  createEmptyRow: createEmptyRow,
  hasProduct: (row) => isPresentItemId(row.itemId),
});

const traceScanBridgeRef = ref<InstanceType<typeof InvDocTraceScanBridge> | null>(null);

/** 打开批量追溯码扫码弹窗 */
function handleOpenTraceScan(): void {
  traceScanBridgeRef.value?.open(collectGridRows());
}

function resetDetailForCreate() {
  headerBusNo.value = undefined;
  originalBusNo.value = undefined;
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
  transferGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchTransferGridEditConfig();
  });
}

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
  await addTransferRow();
}

async function loadDetail(busNo: string, loadGen?: number) {
  transferGridEdit.resetFormMode();
  transferGridEdit.clearGridEdit();
  try {
    let res;
    if (isDepartmentDomain.value) {
      res =
        props.variant === 'in'
          ? await getDeptTransferInDetailApi({ busNo })
          : await getDeptTransferOutDetailApi({ busNo });
    } else if (props.variant === 'in') {
      res = isPharmacyDomain.value
        ? await getPharmacyTransferInDetailApi({ busNo })
        : await getTransferInDetailApi({ busNo });
    } else {
      res = isPharmacyDomain.value
        ? await getPharmacyTransferOutDetailApi({ busNo })
        : await getTransferOutDetailApi({ busNo });
    }
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到调拨单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    originalBusNo.value = data.originalBusNo ?? undefined;
    detailStatusEnum.value = normalizeStockOutStatusSort(data.statusEnum);
    sourceLocationId.value = data.sourceLocationId ?? undefined;
    purposeLocationId.value = data.purposeLocationId ?? undefined;
    pinnedPractitionerOptions.value = [];
    pinPractitionerOption(
      data.practitionerId,
      (data as { practitionerName?: string }).practitionerName,
    );
    practitionerId.value = coercePractitionerIdForSelect(
      data.practitionerId,
      effectivePractitionerOptions.value,
    );
    const firstLineTable = String(data.lines?.[0]?.itemTable ?? '').trim();
    headItemTableType.value = firstLineTable !== '' ? firstLineTable : firstItemTableTypeValue();
    headApplyTime.value = formatDateTime(new Date());
    const gridRows = (data.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData(gridRows);
      patchTransferGridEditConfig();
    });
  } catch {
    if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
    void nextTick(() => {
      if (loadGen !== undefined && parentSync.isLoadStale(loadGen)) return;
      gridApi?.grid?.loadData([]);
      patchTransferGridEditConfig();
    });
  }
}

function buildLinesPayload(rows: TransferPreviewRow[]): TransferInLineDto[] | TransferOutLineDto[] {
  return rows.map((r) => {
    recalcTransferLineTotal(r);
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
      itemId: isPresentItemId(r.itemId) ? String(r.itemId) : undefined,
      inventoryItemId: isPresentItemId(r.inventoryItemId) ? String(r.inventoryItemId) : undefined,
      itemQuantity: persisted.itemQuantity,
      unitCode: unitCode || undefined,
      lotNumber: String(r.lotNumber ?? '').trim() || undefined,
      traceNo: String(r.traceNo ?? '').trim() || undefined,
      price: Number.isFinite(unit) ? unit : undefined,
      totalPrice,
      remake: String(r.remake ?? '').trim() || undefined,
      occurrenceTime: resolveOccurrenceTimeForApi(),
    };
  });
}

function validateBeforeSave(rows: TransferPreviewRow[]): boolean {
  if (!sourceLocationId.value || !purposeLocationId.value) {
    ElMessage.warning(
      isDepartmentDomain.value
        ? '请选择调出科室药房与调入科室药房'
        : isPharmacyDomain.value
          ? '请选择调出药房与调入药房'
          : '请选择调出药库与调入药库',
    );
    return false;
  }
  if (isSameTransferLocationId(sourceLocationId.value, purposeLocationId.value)) {
    ElMessage.warning(transferSameLocationWarning.value);
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
    recalcTransferLineTotal(r);
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

async function persistTransfer(): Promise<string | undefined> {
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
      sourceLocationId: sourceLocationId.value as number | string,
      purposeLocationId: purposeLocationId.value as number | string,
      sourceTypeEnum: sourceTypeEnum.value,
      purposeTypeEnum: purposeTypeEnum.value,
      categoryEnum: transferCategoryEnum.value,
      practitionerId: practitionerId.value as number | string,
      originalBusNo: originalBusNo.value?.trim() || undefined,
      lines,
    };

    if (headerBusNo.value) {
      if (isDepartmentDomain.value) {
        await updateDeptTransferOutApi({ busNo: headerBusNo.value, ...common });
      } else if (props.variant === 'in') {
        if (isPharmacyDomain.value) {
          await updatePharmacyTransferInApi({ busNo: headerBusNo.value, ...common });
        } else {
          await updateTransferInApi({ busNo: headerBusNo.value, ...common });
        }
      } else if (isPharmacyDomain.value) {
        await updatePharmacyTransferOutApi({ busNo: headerBusNo.value, ...common });
      } else {
        await updateTransferOutApi({ busNo: headerBusNo.value, ...common });
      }
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }

    let res;
    if (isDepartmentDomain.value) {
      res = await createDeptTransferOutApi(common);
    } else if (props.variant === 'in') {
      res = isPharmacyDomain.value
        ? await createPharmacyTransferInApi(common)
        : await createTransferInApi(common);
    } else {
      res = isPharmacyDomain.value
        ? await createPharmacyTransferOutApi(common)
        : await createTransferOutApi(common);
    }
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
  await persistTransfer();
}

async function handleSaveAndSubmitDoc() {
  const no = await persistTransfer();
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
    await ElMessageBox.confirm('确认删除整张调拨单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    if (isDepartmentDomain.value) {
      await removeDeptTransferOutApi({ busNo: no });
    } else if (props.variant === 'in') {
      if (isPharmacyDomain.value) {
        await removePharmacyTransferInApi({ busNo: no });
      } else {
        await removeTransferInApi({ busNo: no });
      }
    } else if (isPharmacyDomain.value) {
      await removePharmacyTransferOutApi({ busNo: no });
    } else {
      await removeTransferOutApi({ busNo: no });
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
    const res = isDepartmentDomain.value
      ? await submitDeptTransferOutApprovalApi({ busNo: no })
      : props.variant === 'in'
        ? isPharmacyDomain.value
          ? await submitPharmacyTransferInApprovalApi({ busNo: no })
          : await submitTransferInApprovalApi({ busNo: no })
        : isPharmacyDomain.value
          ? await submitPharmacyTransferOutApprovalApi({ busNo: no })
          : await submitTransferOutApprovalApi({ busNo: no });
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
    const res = isDepartmentDomain.value
      ? await withdrawDeptTransferOutApprovalApi({ busNo: no })
      : props.variant === 'in'
        ? isPharmacyDomain.value
          ? await withdrawPharmacyTransferInApprovalApi({ busNo: no })
          : await withdrawTransferInApprovalApi({ busNo: no })
        : isPharmacyDomain.value
          ? await withdrawPharmacyTransferOutApprovalApi({ busNo: no })
          : await withdrawTransferOutApprovalApi({ busNo: no });
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
  try {
    const body = { busNo: no };
    if (isDepartmentDomain.value) {
      if (props.variant === 'in') {
        ElMessage.warning('调入壳单为只读台账，不可审批');
        return;
      }
      await agreeDeptTransferOutApi(body);
    } else if (props.variant === 'in') {
      if (isPharmacyDomain.value) {
        await agreePharmacyTransferInApi(body);
      } else {
        await agreeTransferInApi(body);
      }
    } else if (isPharmacyDomain.value) {
      await agreePharmacyTransferOutApi(body);
    } else {
      await agreeTransferOutApi(body);
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
    const body = { busNo: no, rejectReason: value };
    if (isDepartmentDomain.value) {
      await rejectDeptTransferOutApi(body);
    } else if (props.variant === 'in') {
      if (isPharmacyDomain.value) {
        await rejectPharmacyTransferInApi(body);
      } else {
        await rejectTransferInApi(body);
      }
    } else if (isPharmacyDomain.value) {
      await rejectPharmacyTransferOutApi(body);
    } else {
      await rejectTransferOutApi(body);
    }
    ElMessage.success('已驳回');
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch (e) {
    if (e === 'cancel') return;
  }
}

interface TransferDraftSnap {
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  practitionerId?: number | string;
  headItemTableType: string;
  originalBusNo?: string;
  headApplyTime: string;
  rows: TransferPreviewRow[];
}

const parentSync = useInvDocParentSyncSession<TransferDraftSnap>();

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
        originalBusNo: originalBusNo.value,
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
        originalBusNo.value = snap.originalBusNo;
        headApplyTime.value = snap.headApplyTime;
        transferGridEdit.resetFormMode();
        await nextTick();
        gridApi?.grid?.loadData(snap.rows);
        patchTransferGridEditConfig();
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

watch(sourceLocationId, (src) => {
  if (!documentEditable.value) return;
  if (!isSameTransferLocationId(src, purposeLocationId.value)) return;
  const next = resolveDistinctLocationIdFromOptions(transferLocationPoolOptions(), src);
  purposeLocationId.value = next;
  ElMessage.warning(transferSameLocationWarning.value);
});

watch(purposeLocationId, (purpose, oldPurpose) => {
  if (!documentEditable.value) return;
  if (!isSameTransferLocationId(purpose, sourceLocationId.value)) return;
  purposeLocationId.value = oldPurpose;
  ElMessage.warning(transferSameLocationWarning.value);
});

/** 解析下拉选项展示名 */
function resolvePrintOptionLabel(options: ApiOptionItem[] | undefined, value: unknown): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = options?.find((item) => String(item.value ?? '') === key);
  return String(hit?.label ?? '').trim();
}

/** 明细行当前计量单位展示文案 */
function resolveTransferPrintUnitLabel(row: TransferPreviewRow): string {
  const kind = String(row.selectedBandKind ?? '').trim();
  const byKind = row.unitList?.find((item) => String(item.value ?? '') === kind);
  if (byKind) {
    return String(byKind.label ?? byKind.value_dictText ?? '').trim();
  }
  return String(row.unitCodeDictText ?? row.unitCode ?? '').trim();
}

/** 打印调拨单（计价流转模版；入库/出库按 variant 定标题） */
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

  const sourceName = resolvePrintOptionLabel(transferLocationPoolOptions(), sourceLocationId.value);
  const destName = resolvePrintOptionLabel(transferLocationPoolOptions(), purposeLocationId.value);
  const itemTypeText = resolvePrintOptionLabel(
    normalizedItemTableTypeOptions.value,
    headItemTableType.value,
  );
  const practitionerName = resolvePrintOptionLabel(
    effectivePractitionerOptions.value,
    practitionerId.value,
  );
  const docKindLabel = props.variant === 'in' ? '调拨入库单' : '调拨出库单';
  const titleLocation =
    sourceName && destName ? `${sourceName}→${destName}` : sourceName || destName;
  const documentTitle = buildInvDocumentTitle(titleLocation, docKindLabel);
  const applyTimeText = String(headApplyTime.value ?? '')
    .trim()
    .replace('T', ' ');

  // 按当前网格展示值组装 PrintData
  const lineRows = productRows.map((row) => ({
    productName: String(row.productName ?? '').trim(),
    specText: String(row.spec ?? '').trim(),
    manufacturerText: String(row.manufacturer ?? '').trim(),
    qty: row.itemQuantity,
    unitLabel: resolveTransferPrintUnitLabel(row),
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
      sourceLocationText: sourceName,
      supplierText: '',
      linkDocText: String(originalBusNo.value ?? '').trim(),
      destLocationText: destName,
      invoiceNoText: '',
      practitionerName,
      lineRows,
      preparedByName: practitionerName,
      approvedByName: '',
    },
    bizRef: {
      busNo,
      docKind: props.variant === 'in' ? 'transfer-in' : 'transfer-out',
      domain: props.domain,
    },
    jobName: documentTitle,
  });
}
</script>

<template>
  <div class="transfer-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
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
        @mousedown="transferGridEdit.onSurfaceMouseDown"
      >
        <div :class="INV_DOC_HEAD_SECTION_CLASS">
          <SectionTitle :title="headSectionTitle" />
          <div class="mt-1 flex flex-wrap items-center gap-2">
            <span class="text-xs" style="color: var(--el-text-color-regular)">状态</span>
            <span class="text-sm" style="color: var(--el-text-color-primary)">{{
              headerStatusText || '—'
            }}</span>
          </div>
          <p
            v-if="isReadOnlyShell"
            class="mt-1 text-xs"
            style="color: var(--el-text-color-secondary)"
          >
            {{ INV_DOC_READONLY_SHELL_HINT.transferIn }}
          </p>
          <ElForm
            ref="headFormRef"
            :model="headFormModel"
            :rules="headFormRules"
            :label-width="headFormLabelWidth"
            class="compact-head-form mt-1"
          >
            <div :class="INV_DOC_HEAD_FORM_GRID_CLASS">
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem :label="sourceLocationLabel" prop="sourceLocationId">
                <ElSelect
                  v-model="sourceLocationId"
                  class="w-full"
                  filterable
                  clearable
                  placeholder="请选择"
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="w in sourceLocationOptions"
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
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="p in purposeLocationOptions"
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
              <ElFormItem v-if="!isReadOnlyShell" label="项目类型">
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
          :on-grid-keydown="transferGridEdit.onGridKeydown"
        >
          <Grid class="min-h-0 flex-1">
            <template #toolbar-actions>
              <InvDocGridToolbar
                v-if="!isReadOnlyShell"
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
                @click="deleteItem(row as TransferPreviewRow)"
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
  <InvDocTraceScanBridge
    ref="traceScanBridgeRef"
    scene="inventoryOut"
    :default-location-id="sourceLocationId"
  />
</template>

<style scoped lang="scss">
.compact-head-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.compact-head-form :deep(.el-form-item__label) {
  padding-right: 8px;
  white-space: nowrap;
}

.inv-doc-head-section {
  border-bottom: 1px solid var(--el-border-color-lighter);
}
</style>
