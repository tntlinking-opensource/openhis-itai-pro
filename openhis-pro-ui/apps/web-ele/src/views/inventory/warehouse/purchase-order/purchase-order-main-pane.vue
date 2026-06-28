<!--
  订货单右侧主区：对接药库订货应用服务；单头 + 可编辑明细 + 吸底操作。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type {
  PurchaseOrderLineDto,
  SupplierOptionDto,
  WarehouseOptionDto,
} from '#/api/inventory/warehouse/purchase-order';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { Trash2 } from '@vben/icons';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import { Delete, Plus } from '@element-plus/icons-vue';
import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
import {
  ElButton,
  ElCheckbox,
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
  agreePurchaseOrderApi,
  createPurchaseOrderApi,
  getPurchaseOrderDetailApi,
  rejectPurchaseOrderApi,
  removePurchaseOrderApi,
  submitPurchaseOrderApprovalApi,
  updatePurchaseOrderApi,
  WHS_PO_CATEGORY_STOCK,
  WHS_PO_LOCATION_TYPE_CABINET,
  WHS_PO_TYPE_ENUM,
  withdrawPurchaseOrderApprovalApi,
} from '#/api/inventory/warehouse/purchase-order';
import SectionTitle from '#/components/layouts/section-title.vue';
import { decimalMul } from '#/utils/format/decimal-util';
import { resolveLoginPractitionerIdForRequest } from '#/utils/format/preserve-api-id';

interface Props {
  busNo?: string;
  resetKey?: number;
  /** 左侧选中为本地新增草稿时，右侧进入空单编辑态 */
  isNewDraft?: boolean;
  /** 新增草稿时默认目的药库（来自左侧筛选或草稿行） */
  initialPurposeLocationId?: number;
  supplierOptions?: SupplierOptionDto[];
  warehouseOptions?: WarehouseOptionDto[];
  /** 左侧列表筛选 purposeLocationId，0 表示全部药库 */
  listPurposeLocationId?: number;
}

defineOptions({ name: 'PurchaseOrderMainPane' });

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
  resetKey: 0,
  isNewDraft: false,
  initialPurposeLocationId: undefined,
  supplierOptions: () => [],
  warehouseOptions: () => [],
  listPurposeLocationId: undefined,
});

const emit = defineEmits<{
  docChanged: [payload?: { busNo?: string }];
}>();

const { canFinanceApprove } = useFinanceApprovalAccess();

type MedicinePick = Record<string, unknown> & {
  id?: string;
  name?: string;
};

interface OrderLineRow {
  id: string;
  medicinePick?: MedicinePick;
  itemTable?: string;
  itemId?: number;
  spec: string;
  manufacturer: string;
  orderQty: number;
  orderUnit: string;
  unitPrice: number;
  totalAmount: string;
  remark: string;
}

/** SupplyStatus.sortOrder → 卡片/头状态展示 */
const STATUS_LABEL: Record<number, string> = {
  1: '待审核',
  2: '审核中',
  3: '同意',
  4: '驳回',
  9: '已撤回',
  10: '待复审',
  11: '已失效',
  12: '已发送',
};

const userStore = useUserStore();

function statusLabel(sort?: number): string {
  if (sort === undefined || sort === null) return '';
  return STATUS_LABEL[sort] ?? String(sort);
}

function nowIsoLocal(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

const headerBusNo = ref<string | undefined>(undefined);
const detailPurposeLocationId = ref<number | undefined>(undefined);
const detailStatusEnum = ref<number | undefined>(undefined);
const detailRowVersion = ref<number | undefined>(undefined);
const supplierId = ref<number | undefined>(undefined);
const purposeSameAsSource = ref(true);
const purposeLocationId = ref<number | undefined>(undefined);

const headApplicant = ref('');
const headApplyTime = ref('');

const saving = ref(false);
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

/** 仅待审核或本地新单可改明细 */
const canEditLines = computed(
  () => detailStatusEnum.value === 1 || (!headerBusNo.value && props.isNewDraft),
);

const canSubmitApproval = computed(() => {
  const s = detailStatusEnum.value;
  return s === 1 || s === 9;
});

/** 左侧为「全部药库」筛选时，新建须手选目的药库 */
function isAllWarehousesListFilter(): boolean {
  const id = props.listPurposeLocationId;
  return id === 0;
}

/** 与药库相同勾选时，目的位取左侧筛选的具体药库 */
function resolveActivePurposeWarehouseId(): number | undefined {
  const sel = props.listPurposeLocationId;
  if (sel !== undefined && sel !== null && sel !== 0) {
    return sel;
  }
  if (headerBusNo.value) {
    return detailPurposeLocationId.value;
  }
  return undefined;
}

function effectivePurposeLocationId(): number | undefined {
  if (purposeSameAsSource.value) {
    return resolveActivePurposeWarehouseId();
  }
  return purposeLocationId.value;
}

/** 将选品行写入明细行（含保存用 itemTable/itemId） */
function syncRowFromMedicinePick(pick: Record<string, unknown>, row: OrderLineRow) {
  row.medicinePick = pick as MedicinePick;
  row.itemTable = pick.itemTable === undefined ? String(pick.busNo ?? '') : String(pick.itemTable);
  row.itemId =
    pick.itemId !== undefined && pick.itemId !== null ? Number(pick.itemId) : Number(pick.id);
  row.spec = String(pick.totalVolume ?? '');
  row.manufacturer = String(pick.manufacturerText ?? pick.manufacturer ?? '');
  row.orderUnit = String(pick.unitCode_dictText ?? pick.unitCode ?? '');
  const priceNum = Number(pick.price);
  row.unitPrice = Number.isFinite(priceNum) ? priceNum : 0;
  recalcOrderLineTotal(row);
}

/** 详情回显：持久化 total_price 原样格式化。 */
function formatPurchaseOrderDetailTotalAmount(total: unknown): string {
  if (total === undefined || total === null || total === '') return '';
  return typeof total === 'number' ? total.toFixed(2) : String(total);
}

/** 编辑态：按数量 × 单价刷新合计（详情 load 不调用）。 */
function recalcOrderLineTotal(row: OrderLineRow) {
  const qty = Number(row.orderQty);
  const unit = Number(row.unitPrice);
  row.totalAmount =
    Number.isFinite(qty) && Number.isFinite(unit) ? String(decimalMul(qty, unit, 2)) : '';
}

function buildLinesPayload(): PurchaseOrderLineDto[] {
  const $grid = gridApi?.grid;
  const rows = ($grid?.getFullData?.() as OrderLineRow[] | undefined) ?? [];
  return rows.map((r) => ({
    itemTable: r.itemTable,
    itemId: r.itemId,
    itemQuantity: r.orderQty,
    unitCode: r.orderUnit,
    price: r.unitPrice,
    totalPrice: r.totalAmount === '' ? undefined : Number(r.totalAmount),
    remake: r.remark,
  }));
}

function createEmptyOrderRow(): OrderLineRow {
  return {
    id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    medicinePick: undefined,
    itemTable: undefined,
    itemId: undefined,
    spec: '',
    manufacturer: '',
    orderQty: 1,
    orderUnit: '',
    unitPrice: 0,
    totalAmount: '',
    remark: '',
  };
}

function resetPaneForNewDraftHeader() {
  headerBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  detailRowVersion.value = undefined;
  detailPurposeLocationId.value = undefined;
  supplierId.value = undefined;
  headApplicant.value = '';
  headApplyTime.value = formatDateTime(new Date());
  purposeSameAsSource.value = !isAllWarehousesListFilter();
  purposeLocationId.value =
    props.initialPurposeLocationId ??
    (isAllWarehousesListFilter()
      ? props.warehouseOptions[0]?.locationId
      : resolveActivePurposeWarehouseId());
}

async function loadDetail(busNo: string) {
  orderGridEdit.resetFormMode();
  orderGridEdit.clearGridEdit();
  try {
    const res = await getPurchaseOrderDetailApi({ busNo });
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到订货单详情');
      return;
    }
    headerBusNo.value = data.busNo;
    detailPurposeLocationId.value = data.purposeLocationId ?? undefined;
    detailStatusEnum.value = data.statusEnum;
    detailRowVersion.value = data.rowVersion;
    supplierId.value = data.supplierId ?? undefined;
    purposeSameAsSource.value =
      data.sourceLocationId === null ||
      data.sourceLocationId === undefined ||
      data.purposeLocationId === undefined ||
      data.purposeLocationId === data.sourceLocationId;
    purposeLocationId.value = data.purposeLocationId ?? data.sourceLocationId;

    headApplyTime.value = data.lines?.[0]?.occurrenceTime
      ? formatDateTime(String(data.lines[0]?.occurrenceTime))
      : formatDateTime(new Date());

    const lines = data.lines ?? [];
    const gridRows: OrderLineRow[] = lines.map((ln, idx) => ({
      id: `ln-${ln.id ?? idx}`,
      medicinePick: {
        id: ln.itemId === undefined ? undefined : String(ln.itemId),
        name: ln.productName,
      },
      itemTable: ln.itemTable,
      itemId: ln.itemId,
      spec: '',
      manufacturer: '',
      orderQty: ln.itemQuantity === undefined ? 1 : Number(ln.itemQuantity),
      orderUnit: String(ln.unitCode ?? ''),
      unitPrice: ln.price === undefined ? 0 : Number(ln.price),
      totalAmount: formatPurchaseOrderDetailTotalAmount(ln.totalPrice),
      remark: String(ln.remake ?? ''),
    }));
    // 详情回显：单价/总价以后端 SupplyRequest 落库为准，不在 load 时重算
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
      patchOrderGridEditConfig();
    });
  } catch {
    void nextTick(() => {
      gridApi?.grid?.loadData([]);
      patchOrderGridEditConfig();
    });
  }
}

function isPresentItemId(v: unknown): boolean {
  if (v === undefined || v === null) return false;
  if (typeof v === 'string') return v.trim() !== '';
  if (typeof v === 'number') return Number.isFinite(v);
  if (typeof v === 'bigint') return true;
  return String(v).trim() !== '';
}

/** 明细行必填项是否已齐（与 buildLinesPayload 保存校验对齐） */
function isOrderRowComplete(row: OrderLineRow): boolean {
  if (!String(row.itemTable ?? '').trim() || !isPresentItemId(row.itemId)) return false;
  const qty = Number(row.orderQty);
  if (!Number.isFinite(qty) || qty <= 0) return false;
  return !!String(row.orderUnit ?? '').trim();
}

let orderGridEdit!: ReturnType<typeof useInventoryDocGridEdit<OrderLineRow>>;

function handleOrderProductPick(row: OrderLineRow) {
  void nextTick(async () => {
    await nextTick();
    await orderGridEdit.onProductSelected(row);
  });
}

function collectGridRows(): OrderLineRow[] {
  const g = gridApi?.grid as null | { getFullData?: () => OrderLineRow[] };
  return g?.getFullData?.() ?? [];
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
        listSource: 'inventory-products',
        query: async () => ({ supplierId: supplierId.value }),
        onSelected: (product: Record<string, unknown>, row: OrderLineRow) => {
          syncRowFromMedicinePick(product, row);
          handleOrderProductPick(row);
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
        qtyField: 'orderQty',
        unitField: 'orderUnit',
        useBandSelect: false,
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
        unitField: 'orderUnit',
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

const gridOptions: VxeGridProps<OrderLineRow> = reactive({
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
    beforeEditMethod: () => canEditLines.value,
  },
  columns,
  pagerConfig: { enabled: false },
});

const ORDER_EDITABLE_COLUMN_FIELDS = new Set(['_qtyUnit', 'medicinePick', 'remark', 'unitPrice']);

const ORDER_EDIT_FIELD_ORDER = ['medicinePick', '_qtyUnit', 'unitPrice', 'remark'] as const;

type OrderEditField = (typeof ORDER_EDIT_FIELD_ORDER)[number];

const ORDER_VERTICAL_NAV_FIELDS = new Set<OrderEditField>(['_qtyUnit', 'unitPrice', 'remark']);

const orderGridKeyboardRef = ref<HTMLElement | null>(null);

const [Grid, gridApi] = useVbenVxeGrid<OrderLineRow>({
  gridOptions,
  gridEvents: {
    cellClick: (params: { column?: { field?: string; type?: string }; row: OrderLineRow }) =>
      orderGridEdit.onCellClick(params),
    editClosed(params: { row: OrderLineRow }) {
      recalcOrderLineTotal(params.row);
      orderGridEdit.syncRowFormMode(params.row);
    },
  },
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
});

orderGridEdit = useInventoryDocGridEdit<OrderLineRow>({
  getGridApi: () => gridApi ?? null,
  gridWrapRef: orderGridKeyboardRef,
  documentEditable: () => canEditLines.value,
  collectRows: collectGridRows,
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

function patchOrderGridEditConfig() {
  const patched = orderGridEdit.patchGridOptions({
    ...(gridOptions as Record<string, unknown>),
    editConfig: {
      ...(gridOptions.editConfig as Record<string, unknown>),
      enabled: canEditLines.value,
    },
  });
  Object.assign(gridOptions, patched);
}

function resetPaneForNewDraft() {
  resetPaneForNewDraftHeader();
  orderGridEdit.resetFormMode();
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
    patchOrderGridEditConfig();
  });
}

watch(
  () => canEditLines.value,
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
  () => props.resetKey,
  () => {
    if (props.isNewDraft) {
      resetPaneForNewDraft();
    } else if (props.busNo) {
      void loadDetail(props.busNo);
    }
    triggerPaneFlash();
  },
);

watch(
  () => [props.busNo, props.isNewDraft] as const,
  ([no, draft]) => {
    if (draft) {
      resetPaneForNewDraft();
      triggerPaneFlash();
      return;
    }
    if (no) {
      void loadDetail(no);
    } else {
      headerBusNo.value = undefined;
      detailStatusEnum.value = undefined;
      detailRowVersion.value = undefined;
      orderGridEdit.resetFormMode();
      void nextTick(() => {
        gridApi?.grid?.loadData([]);
        patchOrderGridEditConfig();
      });
    }
  },
  { immediate: true },
);

watch(
  () => props.listPurposeLocationId,
  () => {
    if (purposeSameAsSource.value) {
      purposeLocationId.value = resolveActivePurposeWarehouseId();
    }
  },
);

watch(purposeSameAsSource, (v) => {
  if (v) {
    purposeLocationId.value = resolveActivePurposeWarehouseId();
  }
});

async function addItem() {
  if (!canEditLines.value) {
    ElMessage.warning('当前状态不可编辑明细');
    return;
  }
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const record = createEmptyOrderRow();
  const { row } = await $grid.insertAt(record, null);
  await $grid.$nextTick();
  if (row) {
    await $grid.scrollToRow(row);
    await orderGridEdit.enterRowFormMode(row as OrderLineRow, 'medicinePick');
  }
}

async function deleteItem(row: 'select' | OrderLineRow) {
  if (!canEditLines.value) return;
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const checked = $grid.getCheckboxRecords() as OrderLineRow[];
  if (row === 'select' && checked.length === 0) return;
  await $grid.remove(row === 'select' ? checked : row);
}

/** 校验并组装订货单行后落库 */
async function handleSave() {
  const purpose = effectivePurposeLocationId();
  if (purpose === undefined || purpose === null) {
    ElMessage.warning('请选择入库目的药库');
    return;
  }
  const linesPayload = buildLinesPayload();
  if (linesPayload.length === 0) {
    ElMessage.warning('请至少添加一行订货明细');
    return;
  }
  for (const ln of linesPayload) {
    if (!ln.itemTable || ln.itemId === undefined) {
      ElMessage.warning('明细中存在未选择商品的行');
      return;
    }
  }

  saving.value = true;
  try {
    const common = {
      purposeLocationId: purpose,
      purposeTypeEnum: WHS_PO_LOCATION_TYPE_CABINET,
      categoryEnum: WHS_PO_CATEGORY_STOCK,
      supplierId: supplierId.value,
      lines: linesPayload,
    };

    if (headerBusNo.value) {
      await updatePurchaseOrderApi({
        busNo: headerBusNo.value,
        ...common,
        rowVersion: detailRowVersion.value,
      });
      ElMessage.success('保存成功');
      await loadDetail(headerBusNo.value);
      emit('docChanged', { busNo: headerBusNo.value });
    } else {
      const res = await createPurchaseOrderApi({
        ...common,
        typeEnum: WHS_PO_TYPE_ENUM,
      });
      const no = res.data?.busNo;
      ElMessage.success('新建成功');
      if (no) {
        detailStatusEnum.value = res.data?.statusEnum ?? 1;
        await loadDetail(no);
      }
      emit('docChanged', { busNo: no ?? headerBusNo.value });
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false;
  }
}

/** 保存成功后立即提交审批 */
async function handleSaveAndSubmit() {
  await handleSave();
  if (!headerBusNo.value) return;
  if (!canSubmitApproval.value) return;
  await handleSubmitApproval();
}

async function handleRemoveDoc() {
  const no = headerBusNo.value;
  if (!no) return;
  try {
    await ElMessageBox.confirm('确认删除整张订货单？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await removePurchaseOrderApi({ busNo: no });
    ElMessage.success('已删除');
    headerBusNo.value = undefined;
    detailStatusEnum.value = undefined;
    void nextTick(() => {
      gridApi?.grid?.loadData([]);
    });
    emit('docChanged', {});
  } catch {
    /* handled */
  }
}

async function handleSubmitApproval() {
  const no = headerBusNo.value;
  if (!no) return;
  try {
    const res = await submitPurchaseOrderApprovalApi({
      busNo: no,
      applyClock: nowIsoLocal(),
    });
    if (res.data?.statusEnum !== undefined && res.data.statusEnum !== null) {
      detailStatusEnum.value = res.data.statusEnum;
    }
    ElMessage.success('已提交审批');
    await loadDetail(no);
    emit('docChanged', { busNo: no });
  } catch {
    /* handled */
  }
}

async function handleWithdraw() {
  const no = headerBusNo.value;
  if (!no) return;
  try {
    const res = await withdrawPurchaseOrderApprovalApi({ busNo: no });
    if (res.data?.statusEnum !== undefined && res.data.statusEnum !== null) {
      detailStatusEnum.value = res.data.statusEnum;
    }
    ElMessage.success('已撤回');
    await loadDetail(no);
    emit('docChanged', { busNo: no });
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
    await agreePurchaseOrderApi({
      busNo: no,
      approverId,
      approvalClock: nowIsoLocal(),
    });
    ElMessage.success('审批同意');
    await loadDetail(no);
    emit('docChanged', { busNo: no });
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
    await rejectPurchaseOrderApi({
      busNo: no,
      rejectReason: value,
      approverId: resolveLoginPractitionerIdForRequest(
        userStore.userInfo as null | Record<string, unknown>,
      ),
      approvalClock: nowIsoLocal(),
    });
    ElMessage.success('已驳回');
    await loadDetail(no);
    emit('docChanged', { busNo: no });
  } catch (error) {
    if (error !== 'cancel') {
      /* 拦截器已提示其它错误 */
    }
  }
}
</script>

<template>
  <div
    class="purchase-order-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
    :class="{ 'purchase-order-main-pane--flash': paneFlashActive }"
  >
    <InvMainShell>
      <template #actionBar>
        <InvDocActions
          :show-approval-actions="canFinanceApprove"
          @save="handleSave"
          @save-and-submit="handleSaveAndSubmit"
          @remove-doc="handleRemoveDoc"
          @submit-approval="handleSubmitApproval"
          @withdraw="handleWithdraw"
          @approve="handleApprove"
          @reject="handleReject"
        />
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
              statusLabel(detailStatusEnum) || (props.isNewDraft ? '编辑中' : '—')
            }}</span>
          </div>
          <ElForm class="compact-head-form mt-1" label-width="80px" size="small">
            <div
              class="grid grid-cols-1 gap-x-2 gap-y-0 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5"
            >
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="未保存" />
              </ElFormItem>
              <ElFormItem label="制单人">
                <ElInput v-model="headApplicant" disabled placeholder="—" />
              </ElFormItem>
              <ElFormItem label="供应商">
                <ElSelect
                  v-model="supplierId"
                  class="w-full"
                  clearable
                  filterable
                  placeholder="可选"
                  :disabled="!canEditLines"
                >
                  <ElOption
                    v-for="s in supplierOptions"
                    :key="String(s.supplierId)"
                    :label="s.supplierName ?? ''"
                    :value="s.supplierId as number"
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
                  placeholder="—"
                  disabled
                />
              </ElFormItem>
              <ElFormItem label="目的药库">
                <div class="flex flex-col gap-1">
                  <ElCheckbox v-model="purposeSameAsSource" :disabled="!canEditLines">
                    与左侧筛选药库相同
                  </ElCheckbox>
                  <ElSelect
                    v-if="!purposeSameAsSource"
                    v-model="purposeLocationId"
                    class="w-full"
                    clearable
                    filterable
                    placeholder="请选择目的库房"
                    :disabled="!canEditLines"
                  >
                    <ElOption
                      v-for="w in warehouseOptions"
                      :key="String(w.locationId)"
                      :label="w.displayName ?? String(w.locationId)"
                      :value="w.locationId as number"
                    />
                  </ElSelect>
                </div>
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <div
          ref="orderGridKeyboardRef"
          class="purchase-order-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col"
          @keydown.capture="orderGridEdit.onGridKeydown"
        >
          <Grid>
            <template #toolbar-actions>
              <ElButton class="mr-2" type="primary" :icon="Plus" @click="addItem">添加行</ElButton>
              <ElButton type="danger" :icon="Delete" @click="deleteItem('select')">删除行</ElButton>
            </template>
            <template #action="{ row }">
              <ElButton type="danger" text @click="deleteItem(row as OrderLineRow)">
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
.compact-head-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.compact-head-form :deep(.el-form-item__label) {
  padding-right: 8px;
}

.purchase-order-main-pane__grid-wrap :deep(.vxe-grid) {
  flex: 1;
  min-height: 0;
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
