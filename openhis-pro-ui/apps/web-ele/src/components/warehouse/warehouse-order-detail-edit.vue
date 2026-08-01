<!-- T1+T5：订货申请主单 useVbenForm + 明细 useVbenVxeGrid（公共组件，原 views/detail/order-table） -->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { ApiOptionItem } from '#/types/api-response';
import type {
  PurchaseOrderDetailGridRow,
  PurchaseOrderMedicationRecord,
} from '#/utils/warehouse/purchase-request-order-detail';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { formatDateTime } from '@vben/utils';

import { dayjs, ElCard, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  initGainLossBusNoApi,
  pageGainLossMedicationApi,
  saveGainLossApi,
} from '#/api/warehouse/gain-loss';
import {
  initPurchaseOrderBusNoApi,
  pagePurchaseOrderMedicationApi,
  savePurchaseOrderApi,
} from '#/api/warehouse/purchase-request';
import WarehouseDocOrderHeadPanel from '#/components/warehouse/warehouse-doc-order-head-panel.vue';
import WarehouseOrderDetailToolbar from '#/components/warehouse/warehouse-order-detail-toolbar.vue';
import { useFormKeyboardNavigation } from '#/hooks/use-form-keyboard-navigation';
import { isGainLossApproved } from '#/utils/warehouse/gain-loss-doc';
import {
  applyMedicineToRow,
  formatAmountCell,
  formatInventoryCell,
  formatPriceCell,
  gridRowsToSavePayload,
  isPurchaseRequestApproved,
  mapDetailRecordToGridRow,
  purchaseOrderDetailDefaultRow,
  purchaseOrderDetailValidRules,
  recalcRowAmounts,
} from '#/utils/warehouse/purchase-request-order-detail';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

defineOptions({ name: 'WarehouseOrderDetailEdit' });

const props = defineProps({
  tableData: { type: Array, default: () => [] },
  form: {
    type: Object,
    default: () => ({
      busNo: undefined,
      applyTime: undefined,
      supplierId: undefined,
      applicantId: undefined,
      reason: undefined,
    }),
  },
  isAddOrEditOrder: {
    type: Object,
    default: () => ({ isAddOrder: false, isEditOrder: false }),
  },
  auditForm: {
    type: Object,
    default: () => ({
      approvalTime: undefined,
      statusEnum_enumText: undefined,
      approverId_dictText: undefined,
    }),
  },
  supplierOption: { type: Array, default: () => [] },
  practitionerOption: { type: Array, default: () => [] },
  supplyCategoryOptions: { type: Array, default: () => [] },
  buttonShow: {
    type: Object,
    default: () => ({
      isAddShow: false,
      isDeleteShow: false,
      isSaveShow: false,
      isEditShow: false,
      isAlertShow: false,
    }),
  },
  supplyTypeOptions: { type: Array, default: () => [] },
  supplyStatusOptions: { type: Array, default: () => [] },
  /** 透传单据头标题（如退货入库单）；缺省为「单据信息」 */
  headPanelTitle: { type: String, default: undefined },
  /** purchase-request：采购申请/订货；gain-loss：损益（接口与审批口径不同） */
  documentModule: {
    type: String,
    default: 'purchase-request',
    validator: (v: string) => v === 'purchase-request' || v === 'gain-loss',
  },
});

const emit = defineEmits([
  'update:form',
  'getList',
  'updateButtonState',
  'clearForm',
  'orderImport',
]);

const formKeyboardAreaRef = ref<HTMLElement | null>(null);
const { handleFormKeydown } = useFormKeyboardNavigation(formKeyboardAreaRef, {
  selectPolicy: true,
});

const loading = ref(false);
const localAuditForm = ref<Record<string, unknown>>({});
const localIsAddOrEditOrder = ref({ isAddOrder: false, isEditOrder: false });

function isDocApproved(row?: null | Record<string, unknown>): boolean {
  if (!row) return false;
  if (props.documentModule === 'gain-loss') {
    return isGainLossApproved(row as Parameters<typeof isGainLossApproved>[0]);
  }
  return isPurchaseRequestApproved(row as Parameters<typeof isPurchaseRequestApproved>[0]);
}

/** 明细工具栏：损益页用「进货单导入」，其它页用「警戒订货」 */
function toolbarAlertFlags() {
  return props.documentModule === 'gain-loss'
    ? { isAlertShow: false, isOrderImport: true }
    : { isAlertShow: true, isOrderImport: false };
}

/** 审核人展示文案（下拉字典解析） */
const auditApproverLabelText = computed(() => {
  const list = (props.practitionerOption || []) as ApiOptionItem[];
  const id = localAuditForm.value.approverId;
  return list.find((i) => String(i.value) === String(id))?.label ?? '暂无';
});

/** 审核日期展示（空则暂无） */
const auditApprovalTimeDisplay = computed(() => {
  const time = localAuditForm.value.approvalTime;
  if (!time) return '暂无';
  const formatted = formatDateTime(time as string);
  return formatted && `${formatted}`.trim() !== '' ? formatted : '暂无';
});

/** 审核状态文案 */
const auditStatusDisplayText = computed(() => {
  const t = localAuditForm.value.statusEnum_enumText;
  return t === undefined || t === null || `${t}` === '' ? '暂无' : String(t);
});

/** 审核状态标签类型：无记录 info；已通过 success；否则 danger */
const auditStatusTagTypeValue = computed<'danger' | 'info' | 'success'>(() => {
  const raw = localAuditForm.value.statusEnum_enumText;
  if (raw === undefined || raw === null || `${raw}` === '') {
    return 'info';
  }
  const approved = isDocApproved(localAuditForm.value);
  return approved ? 'success' : 'danger';
});

watch(
  () => props.isAddOrEditOrder,
  (v) => {
    localIsAddOrEditOrder.value = {
      isAddOrder: Boolean(v?.isAddOrder),
      isEditOrder: Boolean(v?.isEditOrder),
    };
  },
  { deep: true },
);

watch(
  () => props.auditForm,
  (v) => {
    localAuditForm.value = { ...v };
  },
  { deep: true },
);

const detailEditable = computed(
  () =>
    !isDocApproved(localAuditForm.value) &&
    (localIsAddOrEditOrder.value.isAddOrder || localIsAddOrEditOrder.value.isEditOrder),
);

function mapOptions(list: unknown[]): ApiOptionItem[] {
  return (list as ApiOptionItem[]).map((i) => ({
    label: i.label,
    value: i.value,
  }));
}

const [Form, formApi] = useVbenForm(
  reactive({
    schema: computed(() => [
      {
        component: 'Input',
        fieldName: 'busNo',
        label: '单据号',
        componentProps: {
          disabled: true,
          placeholder: '',
        },
      },
      {
        component: 'DatePicker',
        fieldName: 'applyTime',
        label: '开单日期',
        rules: 'required',
        componentProps: {
          type: 'datetime',
          valueFormat: 'YYYY-MM-DD HH:mm:ss',
          format: 'YYYY-MM-DD HH:mm:ss',
          placeholder: '请选择开单日期',
        },
        defaultValue: dayjs().format('YYYY-MM-DD HH:mm:ss'),
      },
      {
        component: 'Select',
        fieldName: 'supplierId',
        label: '供应商',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: mapOptions(props.supplierOption || []),
        },
      },
      {
        component: 'Select',
        fieldName: 'applicantId',
        label: props.documentModule === 'gain-loss' ? '开单人' : '采购员',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: mapOptions(props.practitionerOption || []),
        },
      },
      {
        component: 'Select',
        fieldName: 'categoryEnum',
        label: '单据分类',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: mapOptions(props.supplyCategoryOptions || []),
        },
      },
      {
        component: 'Input',
        fieldName: 'reason',
        label: '摘要(A)',
        componentProps: {
          type: 'textarea',
          autosize: { minRows: 1, maxRows: 4 },
          clearable: true,
          placeholder: '请输入摘要',
          style: { width: '100%' },
        },
      },
    ]),
    layout: 'horizontal',
    commonConfig: {
      labelWidth: 88,
      componentProps: {
        style: { width: '100%' },
      },
    },
    wrapperClass: 'grid grid-cols-1 gap-x-3 gap-y-2 lg:grid-cols-3 xl:grid-cols-4',
    showDefaultActions: false,
  }),
);

watch(
  detailEditable,
  (v) => {
    void formApi.setState({
      commonConfig: { disabled: !v },
    });
  },
  { immediate: true },
);

watch(
  () => props.form,
  async (f) => {
    if (!f || typeof f !== 'object' || Object.keys(f).length === 0) {
      await formApi.resetForm();
      return;
    }
    await formApi.setValues({ ...(f as Record<string, unknown>) });
  },
  { deep: true },
);

const columns: VxeGridPropTypes.Column[] = [
  {
    type: 'checkbox',
    width: 48,
    fixed: 'left',
    align: 'center',
    field: 'checkbox',
  },
  {
    type: 'seq',
    title: '序号',
    width: 56,
    fixed: 'left',
    align: 'center',
    field: 'seq',
  },
  {
    title: '编号',
    field: 'itemBusNo',
    width: 140,
    align: 'left',
    showOverflow: true,
  },
  {
    title: '品名',
    field: 'medicineInfo',
    minWidth: 200,
    width: 240,
    align: 'left',
    editRender: {
      name: 'PurchaseOrderMedicineSelect',
      props: {
        listSource: props.documentModule === 'gain-loss' ? 'gain-loss' : 'purchase-request',
        query: async () => {
          const values = await formApi.getValues();
          return {
            supplierId: values.supplierId,
            locationId: values.locationId,
            typeEnum: props.documentModule === 'gain-loss' ? 21 : 13,
          };
        },
        onSelected: (med: PurchaseOrderMedicationRecord, row: PurchaseOrderDetailGridRow) => {
          applyMedicineToRow(row, med);
        },
      },
    },
  },
  {
    title: '规格',
    field: 'totalVolume',
    width: 140,
    align: 'left',
    showOverflow: true,
  },
  {
    title: '单位',
    field: 'unitCode',
    width: 120,
    align: 'center',
    editRender: {
      name: 'UnitSelect',
      props: {
        onChange: (_val: string, row: PurchaseOrderDetailGridRow) => {
          recalcRowAmounts(row);
        },
      },
    },
  },
  {
    title: '数量',
    field: 'itemQuantity',
    width: 130,
    align: 'right',
    editRender: {
      name: 'ElInputNumber',
      props: {
        controlsPosition: 'right',
        min: 0,
        class: '!w-full',
      },
      events: {
        change(cellParams: { row: PurchaseOrderDetailGridRow }, n: number) {
          cellParams.row.itemQuantity = n;
          recalcRowAmounts(cellParams.row);
        },
      },
    },
  },
  {
    title: '进货价',
    field: 'price',
    width: 100,
    align: 'right',
    formatter: ({ row }) => formatPriceCell(row as PurchaseOrderDetailGridRow, 'price'),
  },
  {
    title: '进货金额',
    field: 'totalPrice',
    width: 110,
    align: 'right',
    showOverflow: true,
    formatter: ({ row }) => formatAmountCell(row as PurchaseOrderDetailGridRow, 'totalPrice'),
  },
  {
    title: '销售价',
    field: 'retailPrice',
    width: 100,
    align: 'right',
    formatter: ({ row }) => formatPriceCell(row as PurchaseOrderDetailGridRow, 'retailPrice'),
  },
  {
    title: '销售金额',
    field: 'totalRetailPrice',
    width: 110,
    align: 'right',
    showOverflow: true,
    formatter: ({ row }) => formatAmountCell(row as PurchaseOrderDetailGridRow, 'totalRetailPrice'),
  },
  {
    title: '厂家/产地',
    field: 'manufacturerText',
    width: 180,
    align: 'left',
    showOverflow: true,
  },
  {
    title: '规格库存',
    field: 'specificationInventory',
    width: 140,
    align: 'right',
    formatter: ({ row }) => formatInventoryCell(row as PurchaseOrderDetailGridRow),
  },
  {
    title: '批准文号',
    field: 'approvalNumber',
    width: 140,
    align: 'left',
    showOverflow: true,
  },
  {
    title: '备注',
    field: 'remake',
    width: 140,
    align: 'left',
    editRender: { name: 'ElInput', props: { class: '!w-full' } },
  },
];

const gridOptions: VxeGridProps<PurchaseOrderDetailGridRow> = reactive({
  autoResize: true,
  height: '100%',
  border: true,
  stripe: true,
  showOverflow: true,
  showFooter: false,
  size: 'medium',
  columnConfig: {
    resizable: true,
  },
  data: [],
  editConfig: {
    enabled: true,
    trigger: 'click',
    mode: 'row',
  },
  columns,
  pagerConfig: { enabled: false },
  editRules: purchaseOrderDetailValidRules,
});

const [Grid, gridApi] = useVbenVxeGrid<PurchaseOrderDetailGridRow>({
  gridOptions,
  /** 去掉 use-vxe-grid 默认 p-2 / 外层卡片圆角，避免订货明细区双层留白 */
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
});

watch(
  detailEditable,
  (v) => {
    gridApi?.setGridOptions?.({
      editConfig: {
        enabled: v,
        trigger: 'click',
        mode: 'row',
      },
    });
  },
  { immediate: true },
);

function syncTableDataToGrid(rows: unknown) {
  nextTick(() => {
    const list = Array.isArray(rows)
      ? rows.map((r) => mapDetailRecordToGridRow({ ...(r as Record<string, unknown>) }))
      : [];
    gridApi?.grid?.loadData(list);
  });
}

/** 禁止 deep：mapDetailRecordToGridRow 会就地改写 unitList 等嵌套字段，与父级 tableData 共享引用时会触发深度 watch 死循环 */
watch(
  () => props.tableData,
  (rows) => {
    syncTableDataToGrid(rows);
  },
);

onMounted(() => {
  syncTableDataToGrid(props.tableData);
});

async function getInitBusNo() {
  if (props.documentModule === 'gain-loss') {
    const data = await initGainLossBusNoApi();
    return data.busNo;
  }
  const data = await initPurchaseOrderBusNoApi();
  return data.busNo;
}

function hasIncompleteRow(rows: PurchaseOrderDetailGridRow[]): boolean {
  return rows.some((r) => !r.itemId || !r.itemName);
}

async function handleAddRow() {
  const { valid } = await formApi.validate();
  if (!valid) return;

  const $grid = gridApi?.grid;
  if (!$grid) return;

  const head = await formApi.getValues();

  const existing = $grid.getFullData() as PurchaseOrderDetailGridRow[];
  if (existing.length > 0 && hasIncompleteRow(existing)) {
    ElMessage.error('请填写完整信息');
    return;
  }

  const empty: PurchaseOrderDetailGridRow = {
    ...purchaseOrderDetailDefaultRow(),
    busNo: head.busNo as string | undefined,
    applyTime: head.applyTime as string | undefined,
    supplierId: head.supplierId as string | undefined,
    applicantId: head.applicantId as string | undefined,
    categoryEnum: head.categoryEnum as string | undefined,
    reason: head.reason as string | undefined,
    purposeLocationId: head.locationId as string | undefined,
    locationId: head.locationId as string | undefined,
  };

  const { row } = await $grid.insertAt(empty, null);
  await $grid.$nextTick();
  await $grid.scrollToRow(row);
  await $grid.setEditCell(row, 'medicineInfo');

  emit('updateButtonState', {
    isAuditDisabled: true,
    isCancelDisabled: false,
    isDeleteDisabled: true,
    isAddDisabled: true,
    isEditDisabled: true,
    isAddShow: true,
    isDeleteShow: true,
    isSaveShow: true,
    isEditShow: true,
    ...toolbarAlertFlags(),
  });
}

async function handleDeleteRow() {
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const rows = $grid.getCheckboxRecords() as PurchaseOrderDetailGridRow[];
  if (rows.length === 0) {
    ElMessage.warning('请选择要删除的行');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除选中的行吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return;
  }
  await $grid.remove(rows);
  ElMessage.success('删除成功');

  const rest = $grid.getFullData() as PurchaseOrderDetailGridRow[];
  if (rest.length === 0) {
    emit('updateButtonState', {
      isAuditDisabled: true,
      isCancelDisabled: true,
      isDeleteDisabled: true,
      isAddDisabled: false,
      isEditDisabled: true,
      isAddShow: true,
      isDeleteShow: true,
      isSaveShow: true,
      isEditShow: false,
      ...toolbarAlertFlags(),
    });
  }
}

async function getMedicineListData(): Promise<PurchaseOrderMedicationRecord[]> {
  const head = await formApi.getValues();
  if (!head.supplierId || !head.locationId) {
    ElMessage.warning('请选择供应商和仓库');
    return [];
  }
  const medParams = {
    supplierId: head.supplierId as string,
    locationId: head.locationId as string,
    pageNo: 1,
    pageSize: 1000,
    typeEnum: props.documentModule === 'gain-loss' ? 21 : 13,
  };
  const data =
    props.documentModule === 'gain-loss'
      ? await pageGainLossMedicationApi(medParams)
      : await pagePurchaseOrderMedicationApi(medParams);
  const { content: records } = parsePagedRowsFromApiData(data);
  if (records.length === 0) {
    ElMessage.warning('本仓库和供应商没有找到药品信息');
  }
  return records as PurchaseOrderMedicationRecord[];
}

async function handleAlertOrder() {
  const $grid = gridApi?.grid;
  if (!$grid) return;
  await $grid.loadData([]);
  const head = await formApi.getValues();
  if (!head.supplierId || !head.locationId) {
    ElMessage.warning('请选择供应商和仓库');
    return;
  }
  const list = await getMedicineListData();
  const mapped = list.map((m) => {
    const row: PurchaseOrderDetailGridRow = {
      ...purchaseOrderDetailDefaultRow(),
      busNo: head.busNo as string | undefined,
      applyTime: head.applyTime as string | undefined,
      supplierId: head.supplierId as string | undefined,
      applicantId: head.applicantId as string | undefined,
      categoryEnum: head.categoryEnum as string | undefined,
      reason: head.reason as string | undefined,
      purposeLocationId: head.locationId as string | undefined,
      locationId: head.locationId as string | undefined,
    };
    applyMedicineToRow(row, m);
    return row;
  });
  await $grid.loadData(mapped);
  localIsAddOrEditOrder.value = { isAddOrder: true, isEditOrder: false };

  emit('updateButtonState', {
    isAuditDisabled: true,
    isCancelDisabled: true,
    isDeleteDisabled: true,
    isAddDisabled: true,
    isEditDisabled: true,
    isAddShow: true,
    isDeleteShow: true,
    isSaveShow: true,
    isEditShow: true,
    ...toolbarAlertFlags(),
  });
}

function handleOrderImportClick() {
  emit('orderImport');
}

async function buildSavePayload(rows: PurchaseOrderDetailGridRow[]) {
  const head = await formApi.getValues();
  const busNo = localIsAddOrEditOrder.value.isAddOrder
    ? await getInitBusNo()
    : String(rows[0]?.busNo ?? '');
  const merged = gridRowsToSavePayload(rows, { ...head, busNo });
  merged.forEach((line) => {
    line.busNo = busNo;
    line.applyTime = head.applyTime;
    line.supplierId = head.supplierId;
    line.applicantId = head.applicantId;
    line.categoryEnum = head.categoryEnum;
    line.reason = head.reason;
    const loc = head.locationId ?? rows[0]?.purposeLocationId;
    line.purposeLocationId = loc;
  });
  return merged;
}

async function handleSave() {
  const { valid } = await formApi.validate();
  if (!valid) return;

  const $grid = gridApi?.grid;
  if (!$grid) return;

  const gridErr = await $grid.validate(true);
  if (gridErr) {
    return;
  }

  const fullData = $grid.getFullData() as PurchaseOrderDetailGridRow[];
  if (fullData.length === 0) {
    ElMessage.warning('请先添加明细');
    return;
  }

  try {
    await ElMessageBox.confirm('确认保存数据吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return;
  }

  loading.value = true;
  try {
    const payload = await buildSavePayload(fullData);
    if (props.documentModule === 'gain-loss') {
      await saveGainLossApi(payload as Record<string, unknown>[]);
    } else {
      await savePurchaseOrderApi(payload as Record<string, unknown>[]);
    }
    ElMessage.success(localIsAddOrEditOrder.value.isAddOrder ? '新增成功' : '保存成功');
    emit('getList');
    emit('clearForm');
  } catch {
    /* 拦截器提示 */
  } finally {
    loading.value = false;
  }
}

async function resetAllData() {
  await formApi.resetForm();
  gridApi?.grid?.loadData([]);
  localAuditForm.value = {};
  localIsAddOrEditOrder.value = { isAddOrder: false, isEditOrder: false };
  emit('updateButtonState', {
    isAuditDisabled: true,
    isCancelDisabled: true,
    isDeleteDisabled: true,
    isAddDisabled: false,
    isEditDisabled: true,
    isAddShow: false,
    isDeleteShow: false,
    isSaveShow: false,
    isEditShow: false,
    isAlertShow: false,
    isOrderImport: false,
  });
}

defineExpose({
  resetAllData,
});
</script>

<template>
  <div
    ref="formKeyboardAreaRef"
    class="purchase-request-order-detail flex h-full min-h-0 flex-1 flex-col gap-1.5"
    tabindex="-1"
    @keydown.capture="handleFormKeydown"
  >
    <WarehouseDocOrderHeadPanel
      :title="headPanelTitle ?? '单据信息'"
      :approver-label="auditApproverLabelText"
      :approval-time-text="auditApprovalTimeDisplay"
      :status-text="auditStatusDisplayText"
      :status-tag-type="auditStatusTagTypeValue"
    >
      <Form />
    </WarehouseDocOrderHeadPanel>

    <ElCard
      shadow="never"
      header-class="purchase-request-card-header"
      body-class="flex min-h-0 min-w-0 flex-1 flex-col !px-2 !pb-1 !pt-0"
      v-loading="loading"
    >
      <div>
        <Grid>
          <template #toolbar-actions>
            <WarehouseOrderDetailToolbar
              :button-show="buttonShow"
              @add-row="handleAddRow"
              @delete-row="handleDeleteRow"
              @save="handleSave"
              @alert-order="handleAlertOrder"
              @order-import="handleOrderImportClick"
            />
          </template>
        </Grid>
      </div>
    </ElCard>
  </div>
</template>
