<!--
  调拨入库/出库右侧主区：InvMainShell + InvDocActions + 单头 + 明细表格。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { ChargeType } from '#/api/core/common';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

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
  ElTag,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { useInventoryDocGridEdit } from '#/composables/use-inventory-doc-grid-edit';
import { getChargeTypeListApi } from '#/api/core/common';
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
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import SectionTitle from '#/components/layouts/section-title.vue';
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
import { useUserStore } from '@vben/stores';

defineOptions({ name: 'InventoryTransferMainPane' });

type TransferVariant = 'in' | 'out';

interface MedicinePickCell {
  id?: number | string;
  name?: string;
}

interface TransferPreviewRow {
  id: string;
  medicinePick?: MedicinePickCell | Record<string, unknown>;
  itemTable?: string;
  itemId?: number | string;
  productName: string;
  itemQuantity?: number;
  unitCode: string;
  lotNumber: string;
  traceNo: string;
  remake: string;
}

interface Props {
  variant: TransferVariant;
  busNo?: string;
  isDraftOrder?: boolean;
  resetKey?: number;
  sidebarFilterLocationId?: number | string;
  warehouseOptions: ApiOptionItem[];
  pharmacyOptions: ApiOptionItem[];
  practitionerOptions?: ApiOptionItem[];
  headSectionTitle?: string;
}

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  isDraftOrder: false,
  resetKey: 0,
  sidebarFilterLocationId: undefined,
  warehouseOptions: () => [],
  pharmacyOptions: () => [],
  practitionerOptions: () => [],
  headSectionTitle: '调拨单信息',
});

const emit = defineEmits<{
  documentRemoved: [];
  documentSaved: [{ busNo?: string }];
}>();

const userStore = useUserStore();
const HEAD_ITEM_TABLE_TYPE = 'med_medication_definition';
const SOURCE_TYPE_ENUM = 'CABINET';
const PURPOSE_TYPE_ENUM = 'PHARMACY';

const listSource = computed(() =>
  props.variant === 'in' ? 'warehouse-transfer-in' : 'warehouse-transfer-out',
);

function normalizeLocationId(v?: number | string | null): number | string | undefined {
  if (v === undefined || v === null || v === '') return undefined;
  if (typeof v === 'number') return Number.isFinite(v) ? v : undefined;
  const s = String(v).trim();
  if (s === '') return undefined;
  if (!/^\d+$/.test(s)) return s;
  const n = Number(s);
  return Number.isSafeInteger(n) ? n : s;
}

const headerBusNo = ref<string | undefined>(undefined);
const originalBusNo = ref<string | undefined>(undefined);
const sourceLocationId = ref<number | string | undefined>(undefined);
const purposeLocationId = ref<number | string | undefined>(undefined);
const practitionerId = ref<number | string | undefined>(undefined);
const detailStatusEnum = ref<number | undefined>(undefined);
const headApplyTime = ref(formatDateTime(new Date()));
const saving = ref(false);

const insuranceContractBusNo = ref<string>();
const insuranceChargeTypeOptions = ref<ChargeType[]>([]);

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

function resolveOccurrenceTimeForApi(): string {
  const t = String(headApplyTime.value ?? '').trim();
  if (t === '') return formatDateTime(new Date());
  return t.includes('T') ? t.replace('T', ' ') : t;
}

function syncRowFromProductPick(pick: Record<string, unknown>, row: TransferPreviewRow) {
  row.medicinePick =
    inventoryPickMedicineCell(pick) ?? (pick as TransferPreviewRow['medicinePick']);
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

function createEmptyRow(): TransferPreviewRow {
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
    remake: '',
  };
}

function mapDetailLineToGrid(
  ln: TransferInDetailLineDto | TransferOutDetailLineDto,
  rowIdx: number,
): TransferPreviewRow {
  const itemIdRaw = ln.itemId;
  const idStr = ln.id !== undefined && ln.id !== null ? `ln-${String(ln.id)}` : `ln-x-${rowIdx}`;
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
    remake: String(ln.remake ?? ''),
  };
}

function isPresentItemId(v: unknown): boolean {
  if (v === undefined || v === null) return false;
  if (typeof v === 'string') return v.trim() !== '';
  if (typeof v === 'number') return Number.isFinite(v);
  if (typeof v === 'bigint') return true;
  return String(v).trim() !== '';
}

function isTransferRowComplete(row: TransferPreviewRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.itemQuantity);
  if (!Number.isFinite(qty) || qty <= 0) return false;
  return !!String(row.unitCode ?? '').trim();
}

let transferGridEdit!: ReturnType<typeof useInventoryDocGridEdit<TransferPreviewRow>>;

function handleTransferProductPick(row: TransferPreviewRow) {
  void nextTick(async () => {
    await nextTick();
    await transferGridEdit.onProductSelected(row);
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
        listSource: listSource,
        query: async () => ({
          productTableName: HEAD_ITEM_TABLE_TYPE,
          sourceLocationId: sourceLocationId.value,
        }),
        onSelected: (product: Record<string, unknown>, row: TransferPreviewRow) => {
          syncRowFromProductPick(product, row);
          handleTransferProductPick(row);
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

const gridOptions: VxeGridProps<TransferPreviewRow> = reactive({
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

const TRANSFER_EDITABLE_COLUMN_FIELDS = new Set([
  'itemQuantity',
  'lotNumber',
  'medicinePick',
  'remake',
  'traceNo',
]);

const TRANSFER_EDIT_FIELD_ORDER = [
  'medicinePick',
  'itemQuantity',
  'lotNumber',
  'traceNo',
  'remake',
] as const;

type TransferEditField = (typeof TRANSFER_EDIT_FIELD_ORDER)[number];

const TRANSFER_VERTICAL_NAV_FIELDS = new Set<TransferEditField>([
  'itemQuantity',
  'lotNumber',
  'remake',
  'traceNo',
]);

const transferGridKeyboardRef = ref<HTMLElement | null>(null);

const [Grid, gridApi] = useVbenVxeGrid<TransferPreviewRow>({
  gridOptions,
  gridEvents: {
    cellClick: (params: { column?: { field?: string; type?: string }; row: TransferPreviewRow }) =>
      transferGridEdit.onCellClick(params),
    editClosed(params: { row: TransferPreviewRow }) {
      transferGridEdit.syncRowFormMode(params.row);
    },
  },
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
});

transferGridEdit = useInventoryDocGridEdit<TransferPreviewRow>({
  getGridApi: () => gridApi ?? null,
  gridWrapRef: transferGridKeyboardRef,
  documentEditable: () => documentEditable.value,
  collectRows: collectGridRows,
  config: {
    rowKey: (row) => row.id,
    productField: 'medicinePick',
    editFieldOrder: TRANSFER_EDIT_FIELD_ORDER,
    editableFields: TRANSFER_EDITABLE_COLUMN_FIELDS,
    verticalNavFields: TRANSFER_VERTICAL_NAV_FIELDS,
    firstFillFieldAfterPick: 'itemQuantity',
    hasProduct: (row) => isPresentItemId(row.itemId),
    isRowComplete: isTransferRowComplete,
  },
});

function patchTransferGridEditConfig() {
  const patched = transferGridEdit.patchGridOptions({
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
    patchTransferGridEditConfig();
    void nextTick(() => {
      if (!editable) {
        transferGridEdit.resetFormMode();
        transferGridEdit.clearGridEdit();
      }
    });
  },
  { immediate: true },
);

function collectGridRows(): TransferPreviewRow[] {
  const g = gridApi?.grid as null | { getFullData?: () => TransferPreviewRow[] };
  return g?.getFullData?.() ?? [];
}

function resetDetailForCreate() {
  headerBusNo.value = undefined;
  originalBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  const filterLoc = normalizeLocationId(props.sidebarFilterLocationId);
  if (props.variant === 'in') {
    purposeLocationId.value = filterLoc ?? props.pharmacyOptions[0]?.value;
    sourceLocationId.value = props.warehouseOptions[0]?.value;
  } else {
    sourceLocationId.value = filterLoc ?? props.warehouseOptions[0]?.value;
    purposeLocationId.value = props.pharmacyOptions[0]?.value;
  }
  practitionerId.value =
    resolveLoginPractitionerIdForRequest(userStore.userInfo as null | Record<string, unknown>) ??
    props.practitionerOptions[0]?.value;
  headApplyTime.value = formatDateTime(new Date());
  transferGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchTransferGridEditConfig();
  });
}

async function loadDetail(busNo: string) {
  transferGridEdit.resetFormMode();
  transferGridEdit.clearGridEdit();
  try {
    const res =
      props.variant === 'in'
        ? await getTransferInDetailApi({ busNo })
        : await getTransferOutDetailApi({ busNo });
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
    practitionerId.value = data.practitionerId ?? undefined;
    headApplyTime.value = formatDateTime(new Date());
    const gridRows = (data.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchTransferGridEditConfig();
    });
  } catch {
    void nextTick(() => {
      gridApi?.grid?.loadData([]);
      patchTransferGridEditConfig();
    });
  }
}

function buildLinesPayload(rows: TransferPreviewRow[]): TransferInLineDto[] | TransferOutLineDto[] {
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
    remake: String(r.remake ?? '').trim() || undefined,
    occurrenceTime: resolveOccurrenceTimeForApi(),
  }));
}

function validateBeforeSave(rows: TransferPreviewRow[]): boolean {
  if (!sourceLocationId.value || !purposeLocationId.value) {
    ElMessage.warning('请选择调出药库与调入药房');
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
      sourceTypeEnum: SOURCE_TYPE_ENUM,
      purposeTypeEnum: PURPOSE_TYPE_ENUM,
      categoryEnum:
        props.variant === 'in' ? WHS_TRANSFER_IN_CATEGORY_ENUM : WHS_TRANSFER_OUT_CATEGORY_ENUM,
      practitionerId: practitionerId.value as number | string,
      originalBusNo: originalBusNo.value?.trim() || undefined,
      lines,
    };

    if (headerBusNo.value) {
      if (props.variant === 'in') {
        await updateTransferInApi({ busNo: headerBusNo.value, ...common });
      } else {
        await updateTransferOutApi({ busNo: headerBusNo.value, ...common });
      }
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('documentSaved', { busNo: headerBusNo.value });
      return headerBusNo.value;
    }

    const res =
      props.variant === 'in'
        ? await createTransferInApi(common)
        : await createTransferOutApi(common);
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
  if (!no || detailStatusEnum.value !== 1) {
    ElMessage.warning('仅待审核单据可删除');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除整张调拨单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    if (props.variant === 'in') {
      await removeTransferInApi({ busNo: no });
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
    const res =
      props.variant === 'in'
        ? await submitTransferInApprovalApi({ busNo: no })
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
    const res =
      props.variant === 'in'
        ? await withdrawTransferInApprovalApi({ busNo: no })
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
  if (!insuranceContractBusNo.value?.trim()) {
    ElMessage.warning('请选择医保契约');
    return;
  }
  try {
    const body = { busNo: no, contractBusNo: insuranceContractBusNo.value };
    if (props.variant === 'in') {
      await agreeTransferInApi(body);
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
    if (props.variant === 'in') {
      await rejectTransferInApi(body);
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

async function addItem() {
  if (!documentEditable.value) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const record = createEmptyRow();
  const { row } = await $grid.insertAt(record, null);
  await $grid.$nextTick();
  if (row) {
    await $grid.scrollToRow(row);
    await transferGridEdit.enterRowFormMode(row as TransferPreviewRow, 'medicinePick');
  }
}

async function deleteItem(row: 'select' | TransferPreviewRow) {
  if (!documentEditable.value) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const checked = $grid.getCheckboxRecords() as TransferPreviewRow[];
  if (row === 'select' && checked.length === 0) return;
  await $grid.remove(row === 'select' ? checked : row);
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

onMounted(() => {
  void loadInsuranceContractBusNo();
});
</script>

<template>
  <div class="transfer-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
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
        @mousedown="transferGridEdit.onSurfaceMouseDown"
      >
        <div class="transfer-head-panel shrink-0">
          <div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
            <SectionTitle :title="headSectionTitle" />
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
              <ElFormItem label="调出药库">
                <ElSelect
                  v-model="sourceLocationId"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="w in warehouseOptions"
                    :key="String(w.value)"
                    :label="w.label"
                    :value="w.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="调入药房">
                <ElSelect
                  v-model="purposeLocationId"
                  class="w-full"
                  filterable
                  clearable
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="p in pharmacyOptions"
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
                  :disabled="!documentEditable"
                >
                  <ElOption
                    v-for="p in practitionerOptions"
                    :key="String(p.value)"
                    :label="p.label"
                    :value="p.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="关联单号">
                <ElInput
                  v-model="originalBusNo"
                  clearable
                  maxlength="64"
                  placeholder="可选，请领单号等"
                  :disabled="!documentEditable"
                />
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

        <div
          ref="transferGridKeyboardRef"
          class="transfer-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col"
          @keydown.capture="transferGridEdit.onGridKeydown"
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
                @click="deleteItem(row as TransferPreviewRow)"
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
</template>

<style scoped lang="scss">
.transfer-head-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 12px 4px;
  background: var(--el-bg-color);
}

.transfer-main-pane__grid-wrap :deep(.vxe-grid) {
  min-height: 200px;
}
</style>
