<!--
  药库退库单（下游退回）右侧主区。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type {
  LocationOptionDto,
  ReturnFromDownstreamDetailLineDto,
  ReturnFromDownstreamLineDto,
  WarehouseDocumentDetailRowDto,
} from '#/api/inventory/warehouse/return-from-downstream';

import { computed, nextTick, reactive, ref, watch } from 'vue';

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
  ElOptionGroup,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { useInventoryDocGridEdit } from '#/composables/use-inventory-doc-grid-edit';
import { useFinanceApprovalAccess } from '#/composables/use-finance-approval-access';
import {
  agreeReturnFromDownstreamApi,
  createReturnFromDownstreamApi,
  getReturnFromDownstreamDetailApi,
  pageLinkableDeliveriesApi,
  rejectReturnFromDownstreamApi,
  removeReturnFromDownstreamApi,
  submitReturnFromDownstreamApprovalApi,
  updateReturnFromDownstreamApi,
  withdrawReturnFromDownstreamApprovalApi,
  WHS_RFD_CATEGORY_STOCK,
  WHS_RFD_LOCATION_TYPE_CABINET,
  WHS_RFD_LOCATION_TYPE_DEPARTMENT,
  WHS_RFD_LOCATION_TYPE_PHARMACY,
} from '#/api/inventory/warehouse/return-from-downstream';
import SectionTitle from '#/components/layouts/section-title.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  inventoryPickMedicineCell,
  inventoryPickStringField,
} from '#/utils/inventory/inventory-product-pick-list';
import {
  buildStockOutDocActionFlags,
  normalizeStockOutStatusSort,
  stockOutStatusLabel,
} from '#/utils/inventory/stock-out-doc-actions';
import { resolveLoginPractitionerIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'InventoryWarehouseReturnFromDownstreamMainPane' });

interface ReturnPreviewRow {
  id: string;
  medicinePick?: Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  productName: string;
  itemQuantity?: number;
  unitCode: string;
  lotNumber: string;
  traceNo: string;
  reason: string;
  remake: string;
}

interface Props {
  busNo?: string;
  isDraftOrder?: boolean;
  resetKey?: number;
  sidebarPurposeLocationId?: number | string;
  warehouseOptions: LocationOptionDto[];
  departmentOptions: LocationOptionDto[];
  pharmacyOptions: LocationOptionDto[];
  applyTime?: string;
}

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  isDraftOrder: false,
  resetKey: 0,
  sidebarPurposeLocationId: undefined,
  warehouseOptions: () => [],
  departmentOptions: () => [],
  pharmacyOptions: () => [],
  applyTime: undefined,
});

const emit = defineEmits<{
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

const { canFinanceApprove } = useFinanceApprovalAccess();

const userStore = useUserStore();
const HEAD_ITEM_TABLE_TYPE = 'med_medication_definition';

function nowIsoLocal(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function normalizeLocationId(v?: number | string | null): number | string | undefined {
  if (v === undefined || v === null || v === '') return undefined;
  if (typeof v === 'number') return Number.isFinite(v) ? v : undefined;
  const s = String(v).trim();
  if (s === '') return undefined;
  if (!/^\d+$/.test(s)) return s;
  const n = Number(s);
  return Number.isSafeInteger(n) ? n : s;
}

function isPresentItemId(v: unknown): boolean {
  if (v === undefined || v === null) return false;
  return String(v).trim() !== '';
}

const headerBusNo = ref<string | undefined>(undefined);
const originalBusNo = ref<string | undefined>(undefined);
const sourceLocationId = ref<number | string | undefined>(undefined);
const purposeLocationId = ref<number | string | undefined>(undefined);
const detailStatusEnum = ref<number | undefined>(undefined);
const headApplyTime = ref(formatDateTime(new Date()));
const saving = ref(false);

const linkDeliveryVisible = ref(false);
const linkDeliveryKeyword = ref('');
const linkDeliveryLoading = ref(false);
const linkDeliveryPage = ref({ pageNo: 1, pageSize: 10 });
const linkDeliveryTotal = ref(0);
const linkDeliveryRecords = ref<WarehouseDocumentDetailRowDto[]>([]);

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

function resolveSourceTypeEnum(): number {
  const sid = sourceLocationId.value;
  if (sid === undefined || sid === null) return WHS_RFD_LOCATION_TYPE_DEPARTMENT;
  const idNum = Number(sid);
  if (props.pharmacyOptions.some((p) => p.locationId === idNum)) {
    return WHS_RFD_LOCATION_TYPE_PHARMACY;
  }
  return WHS_RFD_LOCATION_TYPE_DEPARTMENT;
}

function initHeadApplyTimeFromContext() {
  const p = String(props.applyTime ?? '').trim();
  headApplyTime.value = p
    ? p.includes('T')
      ? p.replace('T', ' ')
      : p
    : formatDateTime(new Date());
}

function resolveOccurrenceTimeForApi(): string {
  const fromHead = String(headApplyTime.value ?? '').trim();
  const fromParent = String(props.applyTime ?? '').trim();
  const t = fromHead || fromParent;
  if (t === '') return formatDateTime(new Date());
  return t.includes('T') ? t.replace('T', ' ') : t;
}

function resolveApplyClockForSubmit(): string {
  const raw = resolveOccurrenceTimeForApi();
  return raw.includes('T') ? raw : raw.replace(' ', 'T');
}

function syncRowFromProductPick(pick: Record<string, unknown>, row: ReturnPreviewRow) {
  row.medicinePick = inventoryPickMedicineCell(pick) ?? (pick as ReturnPreviewRow['medicinePick']);
  row.itemTable = inventoryPickStringField(pick, 'productTableName');
  const defId = pick.productDefinitionId;
  row.itemId =
    defId === undefined || defId === null || String(defId).trim() === ''
      ? undefined
      : (defId as number | string);
  row.productName =
    inventoryPickStringField(pick, 'merchandiseName') ||
    inventoryPickStringField(pick, 'productName');
  row.unitCode =
    inventoryPickStringField(pick, 'minUnitCode') || inventoryPickStringField(pick, 'unitCode');
  row.lotNumber = inventoryPickStringField(pick, 'lotNumber');
}

function createEmptyRow(): ReturnPreviewRow {
  return {
    id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    medicinePick: undefined,
    itemTable: undefined,
    itemId: undefined,
    productName: '',
    itemQuantity: undefined,
    unitCode: '',
    lotNumber: '',
    traceNo: '',
    reason: '',
    remake: '',
  };
}

function mapDetailLineToGrid(
  ln: ReturnFromDownstreamDetailLineDto,
  rowIdx: number,
): ReturnPreviewRow {
  const itemIdRaw = ln.itemId;
  const idStr =
    ln.id !== undefined && ln.id !== null
      ? `ln-${String(ln.id)}`
      : itemIdRaw !== undefined && itemIdRaw !== null
        ? `ln-item-${String(itemIdRaw)}-${rowIdx}`
        : `ln-x-${rowIdx}`;
  const name = String(ln.productName ?? '').trim();
  return {
    id: idStr,
    medicinePick: name ? { id: itemIdRaw as number | string | undefined, name } : undefined,
    itemTable: ln.itemTable,
    itemId: itemIdRaw,
    productName: name,
    itemQuantity: ln.itemQuantity === undefined ? undefined : Number(ln.itemQuantity),
    unitCode: String(ln.unitCode ?? ''),
    lotNumber: String(ln.lotNumber ?? ''),
    traceNo: String(ln.traceNo ?? ''),
    reason: String(ln.reason ?? ''),
    remake: String(ln.remake ?? ''),
  };
}

function isReturnFromDownstreamRowComplete(row: ReturnPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.itemQuantity);
  if (!Number.isFinite(qty) || qty <= 0) return false;
  return !!String(row.unitCode ?? '').trim();
}

let returnFromDownstreamGridEdit!: ReturnType<typeof useInventoryDocGridEdit<ReturnPreviewRow>>;

function handleReturnFromDownstreamProductPick(row: ReturnPreviewRow) {
  void nextTick(async () => {
    await nextTick();
    await returnFromDownstreamGridEdit.onProductSelected(row);
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
        listSource: 'return-from-downstream',
        query: async () => ({
          productTableName: HEAD_ITEM_TABLE_TYPE,
          sourceLocationId: sourceLocationId.value,
        }),
        onSelected: (product: Record<string, unknown>, row: ReturnPreviewRow) => {
          syncRowFromProductPick(product, row);
          handleReturnFromDownstreamProductPick(row);
        },
      },
    },
    showOverflow: true,
  },
  {
    field: 'itemQuantity',
    title: '数量',
    width: 120,
    align: 'center',
    editRender: {
      name: 'InventoryPersistElInputNumber',
      props: { min: 0, controlsPosition: 'right' },
    },
  },
  { field: 'unitCode', title: '单位', width: 88, showOverflow: true },
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
    editRender: { name: 'InventoryPersistElInput', props: { maxlength: 512, clearable: true } },
    showOverflow: true,
  },
  {
    field: 'reason',
    title: '退库原因',
    minWidth: 120,
    editRender: { name: 'InventoryPersistElInput', props: { maxlength: 200, clearable: true } },
    showOverflow: true,
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

const gridOptions: VxeGridProps<ReturnPreviewRow> = reactive({
  autoResize: true,
  height: '100%',
  border: true,
  stripe: true,
  showOverflow: true,
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

const RETURN_FROM_DOWNSTREAM_EDITABLE_COLUMN_FIELDS = new Set([
  'itemQuantity',
  'lotNumber',
  'medicinePick',
  'reason',
  'remake',
  'traceNo',
]);

const RETURN_FROM_DOWNSTREAM_EDIT_FIELD_ORDER = [
  'medicinePick',
  'itemQuantity',
  'lotNumber',
  'traceNo',
  'reason',
  'remake',
] as const;

type ReturnFromDownstreamEditField = (typeof RETURN_FROM_DOWNSTREAM_EDIT_FIELD_ORDER)[number];

const RETURN_FROM_DOWNSTREAM_VERTICAL_NAV_FIELDS = new Set<ReturnFromDownstreamEditField>([
  'itemQuantity',
  'lotNumber',
  'reason',
  'remake',
  'traceNo',
]);

const returnFromDownstreamGridKeyboardRef = ref<HTMLElement | null>(null);

const [Grid, gridApi] = useVbenVxeGrid<ReturnPreviewRow>({
  gridOptions,
  gridEvents: {
    cellClick: (params: { column?: { field?: string; type?: string }; row: ReturnPreviewRow }) =>
      returnFromDownstreamGridEdit.onCellClick(params),
    editClosed(params: { row: ReturnPreviewRow }) {
      returnFromDownstreamGridEdit.syncRowFormMode(params.row);
    },
  },
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
});

returnFromDownstreamGridEdit = useInventoryDocGridEdit<ReturnPreviewRow>({
  getGridApi: () => gridApi ?? null,
  gridWrapRef: returnFromDownstreamGridKeyboardRef,
  documentEditable: () => documentEditable.value,
  collectRows: collectGridRows,
  config: {
    rowKey: (row) => row.id,
    productField: 'medicinePick',
    editFieldOrder: RETURN_FROM_DOWNSTREAM_EDIT_FIELD_ORDER,
    editableFields: RETURN_FROM_DOWNSTREAM_EDITABLE_COLUMN_FIELDS,
    verticalNavFields: RETURN_FROM_DOWNSTREAM_VERTICAL_NAV_FIELDS,
    firstFillFieldAfterPick: 'itemQuantity',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isReturnFromDownstreamRowComplete,
  },
});

function patchReturnFromDownstreamGridEditConfig() {
  const patched = returnFromDownstreamGridEdit.patchGridOptions({
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
    patchReturnFromDownstreamGridEditConfig();
    void nextTick(() => {
      if (!editable) {
        returnFromDownstreamGridEdit.resetFormMode();
        returnFromDownstreamGridEdit.clearGridEdit();
      }
    });
  },
  { immediate: true },
);

function collectGridRows(): ReturnPreviewRow[] {
  const g = gridApi?.grid as null | { getFullData?: () => ReturnPreviewRow[] };
  return g?.getFullData?.() ?? [];
}

function resetDetailForCreate() {
  headerBusNo.value = undefined;
  originalBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  const pid = normalizeLocationId(props.sidebarPurposeLocationId);
  purposeLocationId.value = pid ?? props.warehouseOptions[0]?.locationId;
  sourceLocationId.value =
    props.departmentOptions[0]?.locationId ?? props.pharmacyOptions[0]?.locationId;
  initHeadApplyTimeFromContext();
  returnFromDownstreamGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchReturnFromDownstreamGridEditConfig();
  });
}

async function loadDetail(busNo: string) {
  returnFromDownstreamGridEdit.resetFormMode();
  returnFromDownstreamGridEdit.clearGridEdit();
  try {
    const res = await getReturnFromDownstreamDetailApi({ busNo });
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到退库单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    originalBusNo.value = data.originalBusNo ?? undefined;
    detailStatusEnum.value = normalizeStockOutStatusSort(data.statusEnum);
    sourceLocationId.value = data.sourceLocationId ?? undefined;
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

    const gridRows = (data.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchReturnFromDownstreamGridEditConfig();
    });
  } catch {
    void nextTick(() => {
      gridApi?.grid?.loadData([]);
      patchReturnFromDownstreamGridEditConfig();
    });
  }
}

function buildLinesPayload(rows: ReturnPreviewRow[]): ReturnFromDownstreamLineDto[] {
  return rows.map((r) => ({
    itemTable: r.itemTable,
    itemId:
      typeof r.itemId === 'string' && /^\d+$/.test(r.itemId)
        ? Number(r.itemId)
        : (r.itemId as number | undefined),
    itemQuantity: r.itemQuantity,
    unitCode: String(r.unitCode ?? '').trim() || undefined,
    lotNumber: String(r.lotNumber ?? '').trim() || undefined,
    traceNo: String(r.traceNo ?? '').trim() || undefined,
    occurrenceTime: resolveOccurrenceTimeForApi(),
    reason: String(r.reason ?? '').trim() || undefined,
    remake: String(r.remake ?? '').trim() || undefined,
  }));
}

function validateBeforeSave(rows: ReturnPreviewRow[]): boolean {
  const src = sourceLocationId.value;
  if (src === undefined || src === null || String(src).trim() === '') {
    ElMessage.warning('请选择退库来源（科室/药房）');
    return false;
  }
  const purpose = purposeLocationId.value;
  if (purpose === undefined || purpose === null || String(purpose).trim() === '') {
    ElMessage.warning('请选择接收药库');
    return false;
  }
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一行退库明细');
    return false;
  }
  for (const [i, r] of rows.entries()) {
    if (!String(r.itemTable ?? '').trim() || !isPresentItemId(r.itemId)) {
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
  }
  return true;
}

async function persistDocument(): Promise<string | undefined> {
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
      sourceLocationId: Number(sourceLocationId.value),
      purposeLocationId: Number(purposeLocationId.value),
      sourceTypeEnum: resolveSourceTypeEnum(),
      purposeTypeEnum: WHS_RFD_LOCATION_TYPE_CABINET,
      categoryEnum: WHS_RFD_CATEGORY_STOCK,
      lines,
    };
    const original = originalBusNo.value?.trim();

    if (headerBusNo.value) {
      await updateReturnFromDownstreamApi({ busNo: headerBusNo.value, ...common });
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }

    const res = await createReturnFromDownstreamApi({
      ...(original ? { originalBusNo: original } : {}),
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
  await persistDocument();
}

async function handleSaveAndSubmitDoc() {
  const no = await persistDocument();
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
    await ElMessageBox.confirm('确认删除整张退库单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await removeReturnFromDownstreamApi({ busNo: no });
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
    const res = await submitReturnFromDownstreamApprovalApi({
      busNo: no,
      applyClock: resolveApplyClockForSubmit(),
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
    const res = await withdrawReturnFromDownstreamApprovalApi({ busNo: no });
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
    await agreeReturnFromDownstreamApi({
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
    await rejectReturnFromDownstreamApi({
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
  const record = createEmptyRow();
  const { row } = await $grid.insertAt(record, null);
  await $grid.$nextTick();
  if (row) {
    await $grid.scrollToRow(row);
    await returnFromDownstreamGridEdit.enterRowFormMode(row as ReturnPreviewRow, 'medicinePick');
  }
}

async function deleteItem(row: 'select' | ReturnPreviewRow) {
  if (!documentEditable.value) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const checked = $grid.getCheckboxRecords() as ReturnPreviewRow[];
  if (row === 'select' && checked.length === 0) return;
  await $grid.remove(row === 'select' ? checked : row);
}

async function openLinkDeliveryDialog() {
  if (!canEditOriginalBusNo.value) {
    ElMessage.warning('仅新建单据时可关联发放单');
    return;
  }
  const src = sourceLocationId.value;
  if (src === undefined || src === null) {
    ElMessage.warning('请先选择退库来源');
    return;
  }
  linkDeliveryVisible.value = true;
  linkDeliveryKeyword.value = '';
  linkDeliveryPage.value.pageNo = 1;
  await fetchLinkableDeliveries();
}

async function fetchLinkableDeliveries() {
  const src = sourceLocationId.value;
  if (src === undefined || src === null) {
    linkDeliveryRecords.value = [];
    linkDeliveryTotal.value = 0;
    return;
  }
  linkDeliveryLoading.value = true;
  try {
    const res = await pageLinkableDeliveriesApi({
      sourceLocationId: Number(src),
      keyword: linkDeliveryKeyword.value.trim() || undefined,
      pageNo: linkDeliveryPage.value.pageNo,
      pageSize: linkDeliveryPage.value.pageSize,
    });
    const { content, totalCount } = parsePagedRowsFromApiData<WarehouseDocumentDetailRowDto>(
      res.data,
    );
    linkDeliveryRecords.value = content;
    linkDeliveryTotal.value = totalCount;
  } catch {
    linkDeliveryRecords.value = [];
    linkDeliveryTotal.value = 0;
  } finally {
    linkDeliveryLoading.value = false;
  }
}

function handleSelectLinkableDelivery(row: WarehouseDocumentDetailRowDto) {
  const no = String(row.busNo ?? '').trim();
  if (!no) return;
  originalBusNo.value = no;
  linkDeliveryVisible.value = false;
  ElMessage.success(`已关联发放单 ${no}`);
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
  () => props.sidebarPurposeLocationId,
  () => {
    if (props.isDraftOrder || !headerBusNo.value) {
      const pid = normalizeLocationId(props.sidebarPurposeLocationId);
      if (pid !== undefined) purposeLocationId.value = pid;
    }
  },
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
    class="return-from-downstream-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
  >
    <InvMainShell>
      <template #actionBar>
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
      </template>

      <div
        class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-2 pt-2"
        @mousedown="returnFromDownstreamGridEdit.onSurfaceMouseDown"
      >
        <div class="return-from-downstream-head-panel shrink-0">
          <div
            class="return-from-downstream-head-panel__bar flex flex-wrap items-center justify-between gap-x-4 gap-y-2"
          >
            <SectionTitle title="退库单信息" />
            <div class="flex flex-wrap items-center gap-2">
              <span class="text-xs text-[var(--el-text-color-secondary)]">状态</span>
              <ElTag size="small" type="info" effect="plain">{{ headerStatusText || '—' }}</ElTag>
            </div>
          </div>
          <ElForm label-width="96px" class="compact-head-form">
            <div
              class="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
            >
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem label="退库来源">
                <ElSelect
                  v-model="sourceLocationId"
                  placeholder="科室或药房"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOptionGroup v-if="departmentOptions.length > 0" label="科室">
                    <ElOption
                      v-for="d in departmentOptions"
                      :key="`dept-${d.locationId}`"
                      :label="d.displayName ?? String(d.locationId)"
                      :value="d.locationId!"
                    />
                  </ElOptionGroup>
                  <ElOptionGroup v-if="pharmacyOptions.length > 0" label="药房">
                    <ElOption
                      v-for="p in pharmacyOptions"
                      :key="`pha-${p.locationId}`"
                      :label="p.displayName ?? String(p.locationId)"
                      :value="p.locationId!"
                    />
                  </ElOptionGroup>
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="接收药库">
                <ElSelect
                  v-model="purposeLocationId"
                  placeholder="退库目的药库"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="w in warehouseOptions"
                    :key="String(w.locationId)"
                    :label="w.displayName ?? String(w.locationId)"
                    :value="w.locationId!"
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
              <ElFormItem label="关联发放单" class="sm:col-span-2 lg:col-span-2">
                <div class="flex min-w-0 items-center gap-2">
                  <ElInput
                    v-model="originalBusNo"
                    class="min-w-0 flex-1"
                    clearable
                    maxlength="64"
                    placeholder="可选，关联下游发放单号"
                    :disabled="!canEditOriginalBusNo"
                  />
                  <ElButton
                    class="shrink-0"
                    :disabled="!canEditOriginalBusNo"
                    @click="openLinkDeliveryDialog"
                  >
                    从发放单选择
                  </ElButton>
                </div>
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <div
          ref="returnFromDownstreamGridKeyboardRef"
          class="return-from-downstream-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col"
          @keydown.capture="returnFromDownstreamGridEdit.onGridKeydown"
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
                @click="deleteItem(row as ReturnPreviewRow)"
              >
                <Trash2 class="size-4" />
                删除
              </ElButton>
            </template>
          </Grid>
        </div>
      </div>
    </InvMainShell>

    <ElDialog
      v-model="linkDeliveryVisible"
      destroy-on-close
      title="选择可关联的发放单"
      width="720px"
      @opened="fetchLinkableDeliveries"
    >
      <div class="mb-2 flex gap-2">
        <ElInput
          v-model="linkDeliveryKeyword"
          clearable
          placeholder="发放单号/关键字"
          size="small"
          style="max-width: 260px"
          @keyup.enter="fetchLinkableDeliveries"
        />
        <ElButton size="small" type="primary" @click="fetchLinkableDeliveries">检索</ElButton>
      </div>
      <ElTable
        v-loading="linkDeliveryLoading"
        :data="linkDeliveryRecords"
        size="small"
        highlight-current-row
        @row-dblclick="handleSelectLinkableDelivery"
      >
        <ElTableColumn prop="busNo" label="发放单号" min-width="140" show-overflow-tooltip />
        <ElTableColumn prop="applyTime" label="制单时间" min-width="140" show-overflow-tooltip />
        <ElTableColumn label="操作" width="88" align="center">
          <template #default="{ row }">
            <ElButton link type="primary" size="small" @click="handleSelectLinkableDelivery(row)">
              选择
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <div class="mt-3 flex justify-end">
        <ElPagination
          v-model:current-page="linkDeliveryPage.pageNo"
          v-model:page-size="linkDeliveryPage.pageSize"
          small
          layout="total, prev, pager, next"
          :total="linkDeliveryTotal"
          @current-change="fetchLinkableDeliveries"
          @size-change="fetchLinkableDeliveries"
        />
      </div>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.return-from-downstream-head-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 12px 4px;
  background: var(--el-bg-color);
}

.return-from-downstream-main-pane__grid-wrap :deep(.vxe-grid) {
  min-height: 200px;
}
</style>
