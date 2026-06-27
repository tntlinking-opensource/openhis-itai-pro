<!--
  药库退货单右侧主区：InvMainShell + InvDocActions + 单头 + 明细表格。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { ChargeType } from '#/api/core/common';
import type {
  ReturnToSupplierDetailLineDto,
  ReturnToSupplierLineDto,
  ReturnToSupplierListRowDto,
  WarehouseOptionDto,
} from '#/api/inventory/warehouse/return-to-supplier';
import type { ApiOptionItem } from '#/types/api-response';

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
import {
  agreeReturnToSupplierApi,
  createReturnToSupplierApi,
  getReturnToSupplierDetailApi,
  pageLinkableGoodsReceiptsApi,
  rejectReturnToSupplierApi,
  removeReturnToSupplierApi,
  submitReturnToSupplierApprovalApi,
  updateReturnToSupplierApi,
  withdrawReturnToSupplierApprovalApi,
  WHS_RT_CATEGORY_STOCK,
  WHS_RT_LOCATION_TYPE_CABINET,
} from '#/api/inventory/warehouse/return-to-supplier';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
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

defineOptions({ name: 'InventoryWarehouseReturnToSupplierMainPane' });

interface MedicinePickCell {
  id?: number | string;
  name?: string;
}

interface ReturnPreviewRow {
  id: string;
  medicinePick?: MedicinePickCell | Record<string, unknown>;
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
  sidebarSourceLocationId?: number | string;
  warehouseOptions: WarehouseOptionDto[];
  supplierOptions?: ApiOptionItem[];
  applyTime?: string;
}

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  isDraftOrder: false,
  resetKey: 0,
  sidebarSourceLocationId: undefined,
  warehouseOptions: () => [],
  supplierOptions: () => [],
  applyTime: undefined,
});

const emit = defineEmits<{
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

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
const supplierId = ref<number | string | undefined>(undefined);
const detailStatusEnum = ref<number | undefined>(undefined);
const headApplyTime = ref(formatDateTime(new Date()));
const saving = ref(false);

const insuranceContractBusNo = ref<string>();
const insuranceChargeTypeOptions = ref<ChargeType[]>([]);

const linkInboundVisible = ref(false);
const linkInboundKeyword = ref('');
const linkInboundLoading = ref(false);
const linkInboundPage = ref({ pageNo: 1, pageSize: 10 });
const linkInboundTotal = ref(0);
const linkInboundRecords = ref<ReturnToSupplierListRowDto[]>([]);

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

function mapDetailLineToGrid(ln: ReturnToSupplierDetailLineDto, rowIdx: number): ReturnPreviewRow {
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
        listSource: 'return-to-supplier',
        query: async () => ({
          productTableName: HEAD_ITEM_TABLE_TYPE,
          sourceLocationId: sourceLocationId.value ?? props.sidebarSourceLocationId,
          supplierId: supplierId.value,
        }),
        onSelected: (product: Record<string, unknown>, row: ReturnPreviewRow) => {
          syncRowFromProductPick(product, row);
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
    editRender: { name: 'ElInputNumber', props: { min: 0, controlsPosition: 'right' } },
  },
  {
    field: 'unitCode',
    title: '单位',
    width: 88,
    showOverflow: true,
  },
  {
    field: 'lotNumber',
    title: '批号',
    width: 120,
    editRender: { name: 'ElInput', props: { maxlength: 64, clearable: true } },
    showOverflow: true,
  },
  {
    field: 'traceNo',
    title: '追溯码',
    minWidth: 140,
    editRender: { name: 'ElInput', props: { maxlength: 512, clearable: true } },
    showOverflow: true,
  },
  {
    field: 'reason',
    title: '退货原因',
    minWidth: 120,
    editRender: { name: 'ElInput', props: { maxlength: 200, clearable: true } },
    showOverflow: true,
  },
  {
    field: 'remake',
    title: '备注',
    minWidth: 120,
    editRender: { name: 'ElInput', props: { maxlength: 200, clearable: true } },
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

const [Grid, gridApi] = useVbenVxeGrid<ReturnPreviewRow>({
  gridOptions,
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
});

function patchGridEditConfig() {
  gridOptions.editConfig = {
    ...gridOptions.editConfig,
    enabled: documentEditable.value,
    beforeEditMethod: () => documentEditable.value,
  };
}

watch(
  () => documentEditable.value,
  () => {
    patchGridEditConfig();
  },
);

function collectGridRows(): ReturnPreviewRow[] {
  const g = gridApi?.grid as null | { getFullData?: () => ReturnPreviewRow[] };
  return g?.getFullData?.() ?? [];
}

function resetDetailForCreate() {
  headerBusNo.value = undefined;
  originalBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  supplierId.value = undefined;
  const sid = normalizeLocationId(props.sidebarSourceLocationId);
  sourceLocationId.value = sid ?? props.warehouseOptions[0]?.locationId;
  purposeLocationId.value = sourceLocationId.value;
  initHeadApplyTimeFromContext();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchGridEditConfig();
  });
}

async function loadDetail(busNo: string) {
  try {
    const res = await getReturnToSupplierDetailApi({ busNo });
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到退货单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    originalBusNo.value = data.originalBusNo ?? undefined;
    detailStatusEnum.value = normalizeStockOutStatusSort(data.statusEnum);
    sourceLocationId.value = data.sourceLocationId ?? undefined;
    purposeLocationId.value = data.purposeLocationId ?? data.sourceLocationId ?? undefined;
    supplierId.value = data.supplierId ?? undefined;

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
      patchGridEditConfig();
    });
  } catch {
    void nextTick(() => {
      gridApi?.grid?.loadData([]);
      patchGridEditConfig();
    });
  }
}

function buildLinesPayload(rows: ReturnPreviewRow[]): ReturnToSupplierLineDto[] {
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
    ElMessage.warning('请选择药库');
    return false;
  }
  const ob = originalBusNo.value?.trim();
  if (!headerBusNo.value && !ob) {
    ElMessage.warning('请选择已同意的进货单（原单号）');
    return false;
  }
  if (rows.length === 0) {
    ElMessage.warning('请至少添加一行退货明细');
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

function resolvePurposeLocationIdForApi(): number | string {
  return (purposeLocationId.value ?? sourceLocationId.value) as number | string;
}

async function persistReturnToSupplier(): Promise<string | undefined> {
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
      purposeLocationId: resolvePurposeLocationIdForApi(),
      purposeTypeEnum: WHS_RT_LOCATION_TYPE_CABINET,
      categoryEnum: WHS_RT_CATEGORY_STOCK,
      supplierId:
        supplierId.value === undefined ||
        supplierId.value === null ||
        String(supplierId.value) === ''
          ? undefined
          : (supplierId.value as number | string),
      lines,
    };

    if (headerBusNo.value) {
      await updateReturnToSupplierApi({
        busNo: headerBusNo.value,
        ...common,
      });
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }

    const res = await createReturnToSupplierApi({
      originalBusNo: originalBusNo.value!.trim(),
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
  await persistReturnToSupplier();
}

async function handleSaveAndSubmitDoc() {
  const no = await persistReturnToSupplier();
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
    await ElMessageBox.confirm('确认删除整张退货单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await removeReturnToSupplierApi({ busNo: no });
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
    const res = await submitReturnToSupplierApprovalApi({
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
    const res = await withdrawReturnToSupplierApprovalApi({ busNo: no });
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

function resolveAgreeMessage(res: unknown, fallback: string): string {
  const msg = (res as { message?: string } | undefined)?.message?.trim();
  return msg || fallback;
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
    const res = await agreeReturnToSupplierApi({
      busNo: no,
      approverId,
      approvalClock: nowIsoLocal(),
      contractBusNo: insuranceContractBusNo.value,
    });
    const message = resolveAgreeMessage(res, '审批同意');
    if (message.includes('EX-OPP-207')) {
      ElMessage.warning(message);
    } else {
      ElMessage.success(message);
    }
    await loadDetail(no);
    emit('documentSaved', { busNo: no });
  } catch (error: unknown) {
    const msg = String((error as { message?: string })?.message ?? '');
    if (msg.includes('EX-OPP-207')) {
      ElMessage.warning(msg);
      await loadDetail(no);
      emit('documentSaved', { busNo: no });
    }
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
    await rejectReturnToSupplierApi({
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
    await $grid.setEditCell(row, 'medicinePick');
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

async function openLinkInboundDialog() {
  if (!canEditOriginalBusNo.value) {
    ElMessage.warning('仅新建单据时可关联进货单，或请先点「新增」');
    return;
  }
  const src = sourceLocationId.value ?? normalizeLocationId(props.sidebarSourceLocationId);
  if (src === undefined || src === null) {
    ElMessage.warning('请先选择药库');
    return;
  }
  linkInboundVisible.value = true;
  linkInboundKeyword.value = '';
  linkInboundPage.value.pageNo = 1;
  await fetchLinkableGoodsReceipts();
}

async function fetchLinkableGoodsReceipts() {
  const src = sourceLocationId.value ?? normalizeLocationId(props.sidebarSourceLocationId);
  if (src === undefined || src === null) {
    linkInboundRecords.value = [];
    linkInboundTotal.value = 0;
    return;
  }
  linkInboundLoading.value = true;
  try {
    const res = await pageLinkableGoodsReceiptsApi({
      sourceLocationId: Number(src),
      keyword: linkInboundKeyword.value.trim() || undefined,
      pageNo: linkInboundPage.value.pageNo,
      pageSize: linkInboundPage.value.pageSize,
    });
    const { content, totalCount } = parsePagedRowsFromApiData<ReturnToSupplierListRowDto>(res.data);
    linkInboundRecords.value = content;
    linkInboundTotal.value = totalCount;
  } catch {
    linkInboundRecords.value = [];
    linkInboundTotal.value = 0;
  } finally {
    linkInboundLoading.value = false;
  }
}

function handleSelectLinkableInbound(row: ReturnToSupplierListRowDto) {
  const no = String(row.busNo ?? '').trim();
  if (!no) return;
  originalBusNo.value = no;
  if (row.supplierId !== undefined && row.supplierId !== null) {
    supplierId.value = row.supplierId;
  }
  linkInboundVisible.value = false;
  ElMessage.success(`已关联进货单 ${no}`);
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
        sourceLocationId.value = sid;
        purposeLocationId.value = sid;
      }
    }
  },
);

watch(
  () => props.warehouseOptions,
  (list) => {
    if (list.length === 0) return;
    if (!sourceLocationId.value && (isNewDraft.value || !headerBusNo.value)) {
      const sid = normalizeLocationId(props.sidebarSourceLocationId);
      sourceLocationId.value = sid ?? list[0]?.locationId;
      purposeLocationId.value = sourceLocationId.value;
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
  <div class="return-to-supplier-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
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

      <div class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-2 pt-2">
        <div class="return-to-supplier-head-panel shrink-0">
          <div
            class="return-to-supplier-head-panel__bar flex flex-wrap items-center justify-between gap-x-4 gap-y-2"
          >
            <SectionTitle title="退货单信息" />
            <div class="flex flex-wrap items-center gap-2">
              <span class="text-xs text-[var(--el-text-color-secondary)]">状态</span>
              <ElTag size="small" type="info" effect="plain">{{ headerStatusText || '—' }}</ElTag>
            </div>
          </div>
          <ElForm label-width="96px" class="compact-head-form return-to-supplier-head-form">
            <div
              class="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
            >
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="保存后生成" />
              </ElFormItem>
              <ElFormItem label="药库">
                <ElSelect
                  v-model="sourceLocationId"
                  placeholder="退货源药库"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                  @change="purposeLocationId = sourceLocationId"
                >
                  <ElOption
                    v-for="w in warehouseOptions"
                    :key="String(w.locationId)"
                    :label="w.displayName ?? String(w.locationId)"
                    :value="w.locationId"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="供应商">
                <ElSelect
                  v-model="supplierId"
                  placeholder="请选择供应商"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="s in supplierOptions"
                    :key="String(s.value)"
                    :label="s.label"
                    :value="s.value"
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
              <ElFormItem
                label="原进货单"
                class="return-to-supplier-head-form__inbound sm:col-span-2 lg:col-span-2"
              >
                <div class="flex min-w-0 items-center gap-2">
                  <ElInput
                    v-model="originalBusNo"
                    class="min-w-0 flex-1"
                    clearable
                    maxlength="64"
                    placeholder="必填，已同意的药库进货单号"
                    :disabled="!canEditOriginalBusNo"
                  />
                  <ElButton
                    class="shrink-0"
                    :disabled="!canEditOriginalBusNo"
                    @click="openLinkInboundDialog"
                  >
                    从进货单选择
                  </ElButton>
                </div>
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <div class="return-to-supplier-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col">
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
      v-model="linkInboundVisible"
      destroy-on-close
      title="选择已同意的药库进货单"
      width="720px"
      @opened="fetchLinkableGoodsReceipts"
    >
      <div class="mb-2 flex gap-2">
        <ElInput
          v-model="linkInboundKeyword"
          clearable
          placeholder="进货单号/关键字"
          size="small"
          style="max-width: 260px"
          @keyup.enter="fetchLinkableGoodsReceipts"
        />
        <ElButton size="small" type="primary" @click="fetchLinkableGoodsReceipts">检索</ElButton>
      </div>
      <ElTable
        v-loading="linkInboundLoading"
        :data="linkInboundRecords"
        size="small"
        highlight-current-row
        @row-dblclick="handleSelectLinkableInbound"
      >
        <ElTableColumn prop="busNo" label="进货单号" min-width="140" show-overflow-tooltip />
        <ElTableColumn prop="supplierId" label="供应商ID" width="100" />
        <ElTableColumn prop="applyTime" label="制单时间" min-width="140" show-overflow-tooltip />
        <ElTableColumn label="操作" width="88" align="center">
          <template #default="{ row }">
            <ElButton link type="primary" size="small" @click="handleSelectLinkableInbound(row)">
              选择
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <div class="mt-3 flex justify-end">
        <ElPagination
          v-model:current-page="linkInboundPage.pageNo"
          v-model:page-size="linkInboundPage.pageSize"
          small
          layout="total, prev, pager, next"
          :total="linkInboundTotal"
          @current-change="fetchLinkableGoodsReceipts"
          @size-change="fetchLinkableGoodsReceipts"
        />
      </div>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.return-to-supplier-head-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 12px 4px;
  background: var(--el-bg-color);
}

.return-to-supplier-main-pane__grid-wrap :deep(.vxe-grid) {
  min-height: 200px;
}
</style>
