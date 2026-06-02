<script setup>
import { computed, getCurrentInstance, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { formatDateTime } from '@vben/utils';

import { ElCard, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { initPurchaseReturnBusNoApi, savePurchaseReturnApi } from '#/api/warehouse/purchase-return';
import TraceNoDialog from '#/components/OpenHis/TraceNoDialog/index.vue';
import WarehouseDocOrderHeadPanel from '#/components/warehouse/warehouse-doc-order-head-panel.vue';
import WarehouseOrderDetailToolbar from '#/components/warehouse/warehouse-order-detail-toolbar.vue';
import { formatPharmacyStockYmd } from '#/utils/warehouse/pharmacy-stock-order-el-format';
import { isPurchaseRequestApproved } from '#/utils/warehouse/purchase-request-order-detail';

import PurchaseReturnStockInImportDialog from './purchase-return-stock-in-import-dialog.vue';

defineOptions({ name: 'WarehousePharmacyStockOrderTablePurchaseReturn' });

// 属性
const props = defineProps({
  // 父组件 表格数据
  tableData: {
    type: Array,
    default: () => [],
  },
  // 父组件 表单数据
  form: {
    type: Object,
    default: () => ({
      busNo: undefined,
      applyTime: undefined,
      supplierId: undefined,
      applicantId: undefined,
      reason: undefined,
      status: undefined,
      approverId: undefined,
    }),
  },
  // 父组件 订单编辑还是新增
  isAddOrEditOrder: {
    type: Object,
    default: () => ({
      isAddOrder: false,
      isEditOrder: false,
    }),
  },
  // 父组件 审核表单数据
  auditForm: {
    type: Object,
    default: () => ({
      approvalTime: undefined,
      statusEnum_enumText: undefined,
      approverId_dictText: undefined,
    }),
  },
  // 查询供应商列表 supplierOption
  supplierOption: {
    type: Array,
    default: () => [],
  },
  // 查询经手人列表 practitionerOption
  practitionerOption: {
    type: Array,
    default: () => [],
  },
  // 单据分类 supplyCategoryOptions
  supplyCategoryOptions: {
    type: Array,
    default: () => [],
  },
  // 包装情况 packagingConditionOptions
  packagingConditionOptions: {
    type: Array,
    default: () => [],
  },
  // 验收结果 acceptanceResultoryOptions
  acceptanceResultoryOptions: {
    type: Array,
    default: () => [],
  },
  // 按钮显示
  buttonShow: {
    type: Object,
    default: () => ({
      isAddShow: false,
      isDeleteShow: false,
      isSaveShow: false,
      isEditShow: false,
      isOrderImport: false,
    }),
  },
  // 单据类型 supplyTypeOptions
  supplyTypeOptions: {
    type: Array,
    default: () => [],
  },
  // 审批状态 supplyStatusOptions
  supplyStatusOptions: {
    type: Array,
    default: () => [],
  },
  /** 单据头标题（如采购退货单） */
  headPanelTitle: {
    type: String,
    default: undefined,
  },
});
// 调用父组件方法
const emit = defineEmits(['update:form', 'getList', 'updateButtonState', 'clearForm']);
// 获取当前实例
const { proxy } = getCurrentInstance();
// 本地表单数据，用于接收 props.form
const localForm = ref({});

// 加载
const loading = ref(false);

// 审核表单数据
const localAuditForm = ref({
  approvalTime: undefined,
  statusEnum_enumText: undefined,
  approverId_dictText: undefined,
});
// 订单编辑还是新增
const localIsAddOrEditOrder = ref({
  isAddOrder: false,
  isEditOrder: false,
});
/** 单据头表单项校验（与 prop 一一对应；联系人随供应商回填，不单列必填） */
const headerFormRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  applicantId: [{ required: true, message: '请选择开单人', trigger: 'change' }],
  categoryEnum: [{ required: true, message: '请选择单据分类', trigger: 'change' }],
  applyTime: [{ required: true, message: '请选择开单日期', trigger: 'change' }],
  invoiceNo: [{ required: true, message: '请输入发票号', trigger: 'blur' }],
};

/** VXE 明细校验（field 与列 field 一致） */
const purchaseReturnGridEditRules = {
  medicineInfo: [{ required: true, message: '请选择药品' }],
  totalVolume: [{ required: true, message: '请输入规格', trigger: 'blur' }],
  unitCode: [{ required: true, message: '请选择单位', trigger: 'change' }],
  itemQuantity: [
    {
      validator: (_rule, value, callback) => {
        if (value === undefined || value === null || value === '') {
          callback(new Error('请输入数量'));
          return;
        }
        if (Number(value) <= 0) {
          callback(new Error('数量须大于 0'));
          return;
        }
        callback();
      },
      trigger: ['blur', 'change'],
    },
  ],
  lotNumber: [{ required: true, message: '请输入产品批号', trigger: 'blur' }],
  endTime: [{ required: true, message: '请填写失效日期', trigger: 'change' }],
  occurrenceTime: [{ required: true, message: '请选择禁用日期', trigger: 'change' }],
  manufacturerText: [{ required: true, message: '请输入厂家/产地', trigger: 'blur' }],
  approvalNumber: [{ required: true, message: '请输入批准文号', trigger: 'blur' }],
  traceNo: [{ required: true, message: '请输入药品追溯码', trigger: 'blur' }],
  traceNoUnitCode: [{ required: true, message: '请选择药品追溯码单位', trigger: 'change' }],
};
// 药品名称
const ypName = ref('');
// 药品追溯码
const rowData = ref({});
// 药品追溯码弹窗
const openTraceNoDialog = ref(false);
// 扫码目标行（VXE 行引用）
const traceTargetRow = ref(null);

// 订单组件对话框
const dialogVisible = ref(false);
/** 当前行选中单位在 unitList 中的项 */
const getCurrentUnitDataRow = (row) => {
  if (!row || !row.unitList) return null;
  return row.unitList.find((item) => item.value === row.unitCode) || null;
};

/** 进货价 / 售价 / 金额展示（依赖 unitList 掩码字段） */
const getPriceDisplayRow = (row, priceType) => {
  if (!row) return '';
  const currentUnit = getCurrentUnitDataRow(row);

  // 根据价格类型返回对应的值
  switch (priceType) {
    case 'price': {
      return currentUnit?.priceMask ? Number.parseFloat(currentUnit.priceMask).toFixed(2) : '';
    }
    case 'retailPrice': {
      return currentUnit?.retailPriceMask
        ? Number.parseFloat(currentUnit.retailPriceMask).toFixed(2)
        : '';
    }
    case 'totalPrice': {
      return row.totalPriceMask || currentUnit?.totalPriceMask
        ? Number.parseFloat(row.totalPriceMask || currentUnit?.totalPriceMask).toFixed(2)
        : '';
    }
    case 'totalRetailPrice': {
      return row.totalRetailPriceMask || currentUnit?.totalRetailPriceMask
        ? Number.parseFloat(row.totalRetailPriceMask || currentUnit?.totalRetailPriceMask).toFixed(
            2,
          )
        : '';
    }
    default: {
      return '';
    }
  }
};

/** 批次库存单元格文案 */
const formatBatchInventoryCell = (row) => {
  const currentUnit = getCurrentUnitDataRow(row);
  if (!currentUnit) return '0';
  const hasBatch =
    currentUnit.batchInventoryMax ||
    currentUnit.batchInventoryMaxUnit ||
    currentUnit.batchInventoryMin ||
    currentUnit.batchInventoryMinUnit;
  if (!hasBatch) return '0';
  return `${currentUnit.batchInventoryMax ?? ''}${currentUnit.batchInventoryMaxUnit ?? ''}${currentUnit.batchInventoryMin ?? ''}${currentUnit.batchInventoryMinUnit ?? ''}`;
};

// 监听 props.isAddOrEditOrder 的变化
watch(
  () => props.isAddOrEditOrder,
  (newVal) => {
    localIsAddOrEditOrder.value = { ...newVal };
  },
);
// 监听 props.form 的变化
watch(
  () => props.form,
  (newVal) => {
    localForm.value = { ...newVal };
  },
  { deep: true },
);
// 监听 props.auditForm 的变化
watch(
  () => props.auditForm,
  (newVal) => {
    localAuditForm.value = { ...newVal };
  },
  { deep: true },
);

const auditApproverLabelText = computed(() => {
  const list = props.practitionerOption || [];
  const id = localAuditForm.value.approverId;
  return list.find((i) => String(i.value) === String(id))?.label ?? '暂无';
});

const auditApprovalTimeDisplay = computed(() => {
  const time = localAuditForm.value.approvalTime;
  if (!time) return '暂无';
  const formatted = formatDateTime(time);
  return formatted && `${formatted}`.trim() !== '' ? formatted : '暂无';
});

const auditStatusDisplayText = computed(() => {
  const t = localAuditForm.value.statusEnum_enumText;
  return t === undefined || t === null || `${t}` === '' ? '暂无' : String(t);
});

const auditStatusTagTypeValue = computed(() => {
  const raw = localAuditForm.value.statusEnum_enumText;
  if (raw === undefined || raw === null || `${raw}` === '') {
    return 'info';
  }
  const approved = isPurchaseRequestApproved(localAuditForm.value);
  return approved ? 'success' : 'danger';
});

// 获取单据号
const getInitBusNo = async () => {
  const res = await initPurchaseReturnBusNoApi();
  return res?.busNo ?? '';
};
/** 扫码追溯码：需已填产品批号 */
const handleScanRow = (row) => {
  if (!row?.lotNumber) {
    ElMessage.warning('请先输入产品批号！');
    return;
  }
  traceTargetRow.value = row;
  rowData.value = { ...row, locationId: localForm.value.sourceLocationId, itemType: 1 };
  ypName.value = row.itemName || row.name || '';
  openTraceNoDialog.value = true;
};
// 药品追溯码提交
const submit = (value) => {
  const row = traceTargetRow.value;
  if (!row) {
    openTraceNoDialog.value = false;
    return;
  }
  row.traceNo = row.traceNo ? `${row.traceNo},${value}` : value;
  openTraceNoDialog.value = false;
  traceTargetRow.value = null;
};

// 监听单位变化
const handleUnitCodeChange = (row) => {
  const isMaxUnit = row.unitCode === row.unitList[0].value;
  if (isMaxUnit) {
    row.price = row.priceMaxUnit;
    row.retailPrice = row.retailPriceMaxUnit;
  } else {
    row.price = row.priceMinUnit;
    row.retailPrice = row.retailPriceMinUnit;
  }
  // 进价金额
  row.totalPrice = row.price * row.itemQuantity;
  row.totalPriceMask = row.price * row.itemQuantity;
  // 销售金额
  row.totalRetailPrice = row.retailPrice * row.itemQuantity;
  row.totalRetailPriceMask = row.retailPrice * row.itemQuantity;
};
// 监听数量变化
const handleItemQuantityChange = (row) => {
  // 转换为数值并保留2位小数
  const quantity = Number.parseFloat(row.itemQuantity) || 0;
  const price = Number.parseFloat(row.price) || 0;
  const retailPrice = Number.parseFloat(row.retailPrice) || 0;

  // 进价金额
  row.totalPrice = Number.parseFloat((quantity * price).toFixed(2));
  row.totalPriceMask = Number.parseFloat((quantity * price).toFixed(2));
  // 销售金额
  row.totalRetailPrice = Number.parseFloat((quantity * retailPrice).toFixed(2));
  row.totalRetailPriceMask = Number.parseFloat((quantity * retailPrice).toFixed(2));
};
// 监听进货价数量变化
const handleItemPriceChange = (row) => {
  //  进价金额
  row.totalPrice = Number.parseFloat((row.itemQuantity * row.price).toFixed(2));
  //  进价金额(假)
  row.totalPriceMask = Number.parseFloat((row.itemQuantity * row.price).toFixed(2));
};
// 监听销售价数量变化
const handleItemRetailPriceChange = (row) => {
  // 销售金额
  row.totalRetailPrice = Number.parseFloat((row.itemQuantity * row.retailPrice).toFixed(2));
  // 销售金额(假)
  row.totalRetailPriceMask = Number.parseFloat((row.itemQuantity * row.retailPrice).toFixed(2));
};
/** VXE 选中药品回填当前编辑行 */
const applyMedicineToGridRow = (row, rowValue) => {
  Object.assign(row, {
    ...rowValue,
    itemId: rowValue.id,
    itemBusNo: rowValue.busNo,
    itemName: rowValue.name,
    unitCode: rowValue.unitCode,
    unitCodeDictText: rowValue.unitCode_dictText,
    remake: rowValue.remake,
    specificationInventoryMax: rowValue.specificationInventory,
    batchInventoryMax: rowValue.batchInventory,
    packagingConditionEnum: 1,
    acceptanceResultEnum: 1,
    startTime: formatPharmacyStockYmd(rowValue.productionDate),
    endTime: formatPharmacyStockYmd(rowValue.expirationDate),
    medicineInfo: { id: rowValue.id, name: rowValue.name },
  });
  rebuildTableData(row);
};
// 重新构建价格单位 规格库存 进价 售价表格数据
const rebuildTableData = (row) => {
  // 构建单位列表
  buildUnitList(row);
  // 构建进货价和销售价
  buildPrice(row);
};
// 构建进货价和销售价
const buildPrice = (item) => {
  // 判断当前单位是最大单位还是最小单位
  const isMaxUnit = item.unitCode === item.unitList[0].value;
  if (isMaxUnit) {
    // 进货价
    item.price = item.price;
    item.priceMaxUnit = item.price;
    item.priceMinUnit = item.price / Number.parseInt(item.partPercent);

    // 销售价
    item.retailPrice = item.retailPrice;
    item.retailPriceMaxUnit = item.retailPrice;
    item.retailPriceMinUnit = (
      Number.parseFloat(item.retailPrice) / Number.parseInt(item.partPercent)
    ).toFixed(2);
  } else {
    // 进货价
    item.price = item.price;
    item.priceMaxUnit = Number.parseFloat(
      (item.price * Number.parseInt(item.partPercent)).toFixed(2),
    );
    item.priceMinUnit = item.price;

    // 销售价
    item.retailPrice = item.retailPrice;
    item.retailPriceMaxUnit = Number.parseFloat(
      (item.retailPrice * Number.parseInt(item.partPercent)).toFixed(2),
    );
    item.retailPriceMinUnit = item.retailPrice;
  }
};
// 构建单位列表
const buildUnitList = (row) => {
  // 单位列表
  (row.unitList || []).forEach((item) => {
    // 最大单位
    if (item.value === row.unitCode) {
      // 假数据 只做显示用 不参与计算 用于显示
      // 进货价（假）
      item.priceMask = row.price;
      // 销售价（假）
      item.retailPriceMask = row.retailPrice;

      // 进价金额（假）
      item.totalPriceMask = row.totalPrice;
      // 销售金额（假）
      item.totalRetailPriceMask = row.totalRetailPrice;

      // 规格库存
      item.specificationInventoryOriginal = row.specificationInventory;
      // 规格库存最大值
      item.specificationInventoryMax = Math.floor(row.specificationInventory / row.partPercent);
      // 规格库存最小智
      item.specificationInventoryMin = row.specificationInventory % row.partPercent;
      // 规格库在最大单位
      item.specificationInventoryMaxUnit = row.unitCode_dictText;
      // 规格库存最小单位
      item.specificationInventoryMinUnit = row.minUnitCode_dictText;

      // 批次库存
      item.batchInventoryOriginal = row.batchInventory;
      // 批次库存最大值
      item.batchInventoryMax = Math.floor(row.batchInventory / row.partPercent);
      // 批次库存最小值
      item.batchInventoryMin = row.batchInventory % row.partPercent;
      // 批次库存最大单位
      item.batchInventoryMaxUnit = row.unitCode_dictText;
      // 批次库存最小单位
      item.batchInventoryMinUnit = row.minUnitCode_dictText;
    } else {
      // 最小单位
      // 假数据 只做显示用 不参与计算 用于显示
      // 进货价（假）
      item.priceMask = row.price / row.partPercent;
      // 销售价（假）
      item.retailPriceMask = (
        Number.parseFloat(row.retailPrice) / Number.parseFloat(row.partPercent)
      ).toFixed(2);

      // 进价金额（假）
      item.totalPriceMask = row.totalPrice;
      // 销售金额（假）
      item.totalRetailPriceMask = row.totalRetailPrice;

      // 规格库存
      item.specificationInventoryOriginal = row.specificationInventory;
      // 规格库存最大
      item.specificationInventoryMin = row.specificationInventory;
      // 规格库存最大单位
      item.specificationInventoryMinUnit = row.minUnitCode_dictText;

      // 批次库存
      item.batchInventoryOriginal = row.batchInventory;
      // 批次库存最大
      item.batchInventoryMin = row.batchInventory;
      // 批次库存最大单位
      item.batchInventoryMinUnit = row.minUnitCode_dictText;
    }
  });
};

/** 未审批且新增/编辑时可编辑明细（对齐订货 VXE） */
const detailEditable = computed(
  () =>
    !isPurchaseRequestApproved(localAuditForm.value) &&
    (localIsAddOrEditOrder.value.isAddOrder || localIsAddOrEditOrder.value.isEditOrder),
);

const purchaseReturnMedicineQuery = async () => ({
  supplierId: localForm.value.supplierId,
  locationId: localForm.value.locationId,
});

const purchaseReturnColumns = [
  { type: 'checkbox', width: 48, fixed: 'left', align: 'center', field: 'checkbox' },
  { type: 'seq', title: '序号', width: 56, fixed: 'left', align: 'center', field: 'seq' },
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
    align: 'left',
    editRender: {
      name: 'PurchaseReturnMedicineSelect',
      props: {
        query: purchaseReturnMedicineQuery,
        onSelected: (med, row) => applyMedicineToGridRow(row, med),
      },
    },
  },
  {
    title: '规格',
    field: 'totalVolume',
    width: 120,
    align: 'left',
    showOverflow: true,
  },
  {
    title: '产品批号',
    field: 'lotNumber',
    width: 160,
    editRender: {
      name: 'ElInput',
      props: { clearable: true, placeholder: '请输入产品批号', class: '!w-full' },
    },
  },
  {
    title: '单位',
    field: 'unitCode',
    width: 120,
    align: 'center',
    editRender: {
      name: 'UnitSelect',
      props: {
        onChange: (_val, row) => handleUnitCodeChange(row),
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
        change(cellParams, n) {
          cellParams.row.itemQuantity = n;
          handleItemQuantityChange(cellParams.row);
        },
      },
    },
  },
  {
    title: '进货价',
    field: 'price',
    width: 110,
    align: 'right',
    formatter: ({ row }) => getPriceDisplayRow(row, 'price'),
  },
  {
    title: '进价金额',
    field: 'totalPrice',
    width: 110,
    align: 'right',
    formatter: ({ row }) => getPriceDisplayRow(row, 'totalPrice'),
  },
  {
    title: '当前销售价',
    field: 'retailPrice',
    width: 110,
    align: 'right',
    formatter: ({ row }) => getPriceDisplayRow(row, 'retailPrice'),
  },
  {
    title: '当前销售金额',
    field: 'totalRetailPrice',
    width: 120,
    align: 'right',
    formatter: ({ row }) => getPriceDisplayRow(row, 'totalRetailPrice'),
  },
  {
    title: '失效日期',
    field: 'endTime',
    width: 120,
    align: 'center',
    formatter: ({ row }) => formatPharmacyStockYmd(row?.endTime) || '',
  },
  {
    title: '禁用日期',
    field: 'occurrenceTime',
    width: 160,
    align: 'center',
    editRender: {
      name: 'ElDatePicker',
      props: {
        type: 'date',
        valueFormat: 'YYYY-MM-DD',
        format: 'YYYY-MM-DD',
        placeholder: '请选择禁用日期',
      },
    },
  },
  {
    title: '厂家/产地',
    field: 'manufacturerText',
    width: 180,
    align: 'left',
    showOverflow: true,
  },
  {
    title: '批次库存',
    field: 'batchInventory',
    width: 120,
    align: 'left',
    formatter: ({ row }) => formatBatchInventoryCell(row),
  },
  {
    title: '批准文号',
    field: 'approvalNumber',
    width: 140,
    align: 'left',
    showOverflow: true,
  },
  {
    title: '药品追溯码',
    field: 'traceNo',
    width: 160,
    editRender: {
      name: 'ElInput',
      props: { clearable: true, placeholder: '请输入药品追溯码', class: '!w-full' },
    },
  },
  {
    title: '药品追溯码单位',
    field: 'traceNoUnitCode',
    width: 160,
    align: 'center',
    editRender: {
      name: 'UnitSelect',
      props: {},
    },
  },
  {
    title: '备注',
    field: 'remake',
    width: 140,
    align: 'left',
    editRender: { name: 'ElInput', props: { class: '!w-full' } },
  },
  {
    title: '操作',
    field: 'scanAction',
    width: 88,
    fixed: 'right',
    align: 'center',
    cellRender: {
      name: 'PurchaseReturnScanCell',
      props: {
        onScan: (r) => handleScanRow(r),
      },
    },
  },
];

const gridOptions = reactive({
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
    mode: 'row',
  },
  columns: purchaseReturnColumns,
  pagerConfig: { enabled: false },
  editRules: purchaseReturnGridEditRules,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions,
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

function syncPurchaseReturnGridFromProps(rows) {
  nextTick(() => {
    const list = Array.isArray(rows)
      ? rows.map((r) => {
          const x = { ...r };
          if (x.itemName && !x.medicineInfo) {
            x.medicineInfo = { id: x.itemId, name: x.itemName };
          }
          return x;
        })
      : [];
    gridApi?.grid?.loadData(list);
  });
}

watch(
  () => props.tableData,
  (rows) => {
    syncPurchaseReturnGridFromProps(rows);
  },
  { deep: true },
);

onMounted(() => {
  syncPurchaseReturnGridFromProps(props.tableData);
});

/** 是否存在未完成填写的明细行（数量须大于 0） */
const isIncompleteDetailRow = (row) =>
  !row.itemId ||
  !row.itemName ||
  row.itemQuantity === undefined ||
  row.itemQuantity === null ||
  Number(row.itemQuantity) <= 0;

const hasIncompleteRows = (rows) => rows.some((row) => isIncompleteDetailRow(row));

/** 合成空行默认字段（与旧 el-table 一致） */
function buildPurchaseReturnEmptyRow() {
  return {
    busNo: localForm.value.busNo,
    applyTime: localForm.value.applyTime,
    applicantId: localForm.value.applicantId,
    reason: localForm.value.reason,
    categoryEnum: localForm.value.categoryEnum,
    supplierId: localForm.value.supplierId,
    sourceLocationId: localForm.value.locationId,
    medicineInfo: undefined,
    itemBusNo: '',
    itemId: '',
    itemName: '',
    totalVolume: '',
    unitCode: '',
    itemQuantity: 0,
    price: '',
    totalPrice: '',
    retailPrice: '',
    totalRetailPrice: '',
    manufacturerText: '',
    specificationInventory: '',
    approvalNumber: '',
    acceptanceResultEnum: 1,
    packagingConditionEnum: 1,
    remake: '',
  };
}

/** 新增一行可编辑明细 */
const addEmptyRow = async () => {
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const empty = buildPurchaseReturnEmptyRow();
  await $grid.insertAt(empty, null);
  await nextTick();
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
    isOrderImport: true,
  });
};
// 添加行
const handleAddRow = async () => {
  const formValid = await validateFormRef();
  if (!formValid) {
    return;
  }
  const $grid = gridApi?.grid;
  if (!$grid) return;

  const existing = $grid.getFullData() || [];
  if (existing.length > 0 && hasIncompleteRows(existing)) {
    ElMessage.error('请填写完整信息');
    return;
  }
  await addEmptyRow();
  const rows = $grid.getFullData() || [];
  const last = rows[rows.length - 1];
  if (last) {
    await $grid.scrollToRow(last);
    await $grid.setEditCell(last, 'medicineInfo');
  }
};

// 验证表单
const validateFormRef = () => {
  return new Promise((resolve) => {
    proxy.$refs.formRef.validate((valid) => {
      resolve(valid);
    });
  });
};

// 供应商选择变化
const handleSupplierChange = async () => {
  gridApi?.grid?.loadData([]);
  localForm.value.phone = props.supplierOption.find(
    (item) => item.value === localForm.value.supplierId,
  )?.phone;
};

// 保存行
const handleSave = async () => {
  // 验证表单头部
  const formValid = await validateFormRef();
  if (!formValid) {
    return;
  }

  const $grid = gridApi?.grid;
  if (!$grid) return;

  const gridErr = await $grid.validate(true);
  if (gridErr) {
    return;
  }

  const fullRows = $grid.getFullData() || [];
  if (fullRows.length === 0) {
    ElMessage.warning('请先添加明细');
    return;
  }

  const currentTableData = JSON.parse(JSON.stringify(fullRows));
  const currentFormData = JSON.parse(JSON.stringify(localForm.value));

  // 是否确认保存数据
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
  const editData = await buildEditForm(currentTableData, currentFormData);

  try {
    await savePurchaseReturnApi(editData);
    ElMessage.success(localIsAddOrEditOrder.value.isAddOrder ? '新增成功' : '保存成功');
    emit('getList');
    resetAllData();
  } catch {
    ElMessage.error('保存失败');
  } finally {
    loading.value = false;
  }
};
// 重构数据
const buildEditForm = async (data, formData = null) => {
  const formDataCopy = [...data];
  // 获取单据号
  let busNo = '';
  busNo = localIsAddOrEditOrder.value.isAddOrder ? await getInitBusNo() : data[0].busNo;
  // 订单数据
  const orderData = data[0];
  // 使用传入的表单数据，如果没有则使用当前的localForm
  const currentForm = formData || localForm.value;
  // 表格数据
  formDataCopy.forEach((item) => {
    // 单据号
    item.busNo = busNo;
    // 开单日期
    item.applyTime = currentForm.applyTime;
    // 供应商
    item.supplierId = currentForm.supplierId;
    // 开单人
    item.applicantId = currentForm.applicantId;
    // 单据分类
    item.categoryEnum = currentForm.categoryEnum;
    // 摘要
    item.reason = currentForm.reason;
    // 仓库
    item.sourceLocationId = orderData.sourceLocationId
      ? orderData.sourceLocationId
      : currentForm.locationId;
    // 供应商联系人
    item.phone = currentForm.phone;
    // 开单人
    item.practitionerId = currentForm.practitionerId;
    // 发票号
    item.invoiceNo = currentForm.invoiceNo;
    // 原始单据号
    item.originalBusNo = currentForm.originalBusNo;
  });
  // 重构数据
  return formDataCopy;
};
// 清空表单
const resetAllData = () => {
  // 清空表单数据
  localForm.value = {};

  // 清空审核表单数据
  localAuditForm.value = {};

  gridApi?.grid?.loadData([]);

  // 重置订单状态
  localIsAddOrEditOrder.value = {
    isAddOrder: false,
    isEditOrder: false,
  };

  // 重置按钮状态为初始状态
  emit('updateButtonState', {
    // 审核 按钮禁用
    isAuditDisabled: true,
    // 取消 按钮禁用
    isCancelDisabled: true,
    // 删除 按钮禁用
    isDeleteDisabled: true,
    // 添加 按钮禁用
    isAddDisabled: false,
    // 编辑 按钮禁用
    isEditDisabled: true,
    // 添加行 按钮显示
    isAddShow: false,
    // 删除行 按钮显示
    isDeleteShow: false,
    // 保存行 按钮显示
    isSaveShow: false,
    // 编辑行 按钮显示
    isEditShow: false,
  });
};
// 删除行
const handleDeleteRow = async () => {
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const rows = $grid.getCheckboxRecords() || [];
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

  const rest = $grid.getFullData() || [];
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
      isOrderImport: true,
    });
  }
};

// 更新表格数据（订货导入）
const updateTableData = (updateValue) => {
  const $grid = gridApi?.grid;
  if (!$grid) return;
  const cur = $grid.getFullData() || [];
  $grid.loadData([...cur, ...(updateValue.currentOrderList || [])]);
  // 更新表单数据 - 合并数据而不是替换
  localForm.value = { ...localForm.value, ...updateValue.currentOrderForm };

  // 更新按钮状态
  emit('updateButtonState', {
    // 审核 按钮禁用
    isAuditDisabled: true,
    // 取消 按钮禁用
    isCancelDisabled: true,
    // 删除 按钮禁用
    isDeleteDisabled: true,
    // 添加 按钮禁用
    isAddDisabled: false,
    // 编辑 按钮禁用
    isEditDisabled: true,
    // 添加行 按钮显示
    isAddShow: true,
    // 删除行 按钮显示
    isDeleteShow: true,
    // 保存行 按钮显示
    isSaveShow: true,
    // 编辑行 按钮显示
    isEditShow: false,
    // 警戒订货 按钮显示
    isOrderImport: true,
  });
};

// 对话框提交
const dialogSubmit = (value) => {
  // 对话框关闭
  dialogVisible.value = value.dialogVisible;
};
// 对话框取消
const dialogCancel = (value) => {
  // 对话框关闭
  dialogVisible.value = value.dialogVisible;
};
// 订货单导入
const handleOrderImport = async () => {
  // 对话框显示
  dialogVisible.value = true;
  // 获取订单列表
  await proxy.$refs.orderDialogRef.getOrderList(localForm.value.locationId);
};

// 暴露方法给父组件
defineExpose({
  resetAllData,
});
</script>

<template>
  <div class="purchase-request-order-detail flex h-full min-h-0 flex-1 flex-col gap-1.5">
    <WarehouseDocOrderHeadPanel
      :title="headPanelTitle ?? '单据信息'"
      :approver-label="auditApproverLabelText"
      :approval-time-text="auditApprovalTimeDisplay"
      :status-text="auditStatusDisplayText"
      :status-tag-type="auditStatusTagTypeValue"
    >
      <el-form ref="formRef" :model="localForm" label-width="140px" inline :rules="headerFormRules">
        <el-row :gutter="10">
          <el-form-item label="单据号" prop="busNo">
            <el-input v-model="localForm.busNo" placeholder="" style="width: 220px" disabled />
          </el-form-item>
          <el-form-item label="开单日期" prop="applyTime">
            <el-date-picker
              v-model="localForm.applyTime"
              placeholder="请选择开单日期"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              :disabled="!localIsAddOrEditOrder.isAddOrder && !localIsAddOrEditOrder.isEditOrder"
              clearable
            />
          </el-form-item>

          <el-form-item label="供应商" prop="supplierId">
            <el-select
              v-model="localForm.supplierId"
              placeholder="请选择供应商"
              style="width: 180px"
              :disabled="!localIsAddOrEditOrder.isAddOrder && !localIsAddOrEditOrder.isEditOrder"
              clearable
              @change="handleSupplierChange"
            >
              <el-option
                v-for="item in props.supplierOption || []"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="供应商联系人" prop="phone">
            <el-input
              v-model="localForm.phone"
              placeholder="请输入供应商联系人"
              style="width: 220px"
              disabled
            />
          </el-form-item>
          <el-form-item label="开单人" prop="applicantId">
            <el-select
              v-model="localForm.applicantId"
              placeholder="请选择开单人"
              style="width: 140px"
              :disabled="!localIsAddOrEditOrder.isAddOrder && !localIsAddOrEditOrder.isEditOrder"
              clearable
            >
              <el-option
                v-for="item in props.practitionerOption || []"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="单据分类" prop="categoryEnum">
            <el-select
              v-model="localForm.categoryEnum"
              placeholder="请选择单据分类"
              style="width: 180px"
              :disabled="!localIsAddOrEditOrder.isAddOrder && !localIsAddOrEditOrder.isEditOrder"
              clearable
            >
              <el-option
                v-for="item in props.supplyCategoryOptions || []"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="发票号" prop="invoiceNo">
            <el-input
              v-model="localForm.invoiceNo"
              placeholder="请输入发票号"
              clearable
              style="width: 180px"
              :disabled="!localIsAddOrEditOrder.isAddOrder && !localIsAddOrEditOrder.isEditOrder"
            />
          </el-form-item>
          <el-form-item label="摘要(A)" prop="reason">
            <el-input
              type="textarea"
              v-model="localForm.reason"
              placeholder="请输入摘要"
              style="width: 300px"
              :rows="1"
              :disabled="!localIsAddOrEditOrder.isAddOrder && !localIsAddOrEditOrder.isEditOrder"
              clearable
            />
          </el-form-item>
        </el-row>
      </el-form>
    </WarehouseDocOrderHeadPanel>

    <ElCard
      shadow="never"
      header-class="purchase-request-card-header"
      body-class="flex min-h-0 min-w-0 flex-1 flex-col !px-2 !pb-1 !pt-0"
      v-loading="loading"
    >
      <div>
        <Grid class="min-h-0 flex-1">
          <template #toolbar-actions>
            <WarehouseOrderDetailToolbar
              :button-show="buttonShow"
              @add-row="handleAddRow"
              @delete-row="handleDeleteRow"
              @save="handleSave"
              @order-import="handleOrderImport"
            />
          </template>
        </Grid>
      </div>
    </ElCard>

    <TraceNoDialog
      :yp-name="ypName"
      :row-data="rowData"
      :open-dialog="openTraceNoDialog"
      @submit="submit"
      @cancel="openTraceNoDialog = false"
    />

    <PurchaseReturnStockInImportDialog
      ref="orderDialogRef"
      :dialog-visible="dialogVisible"
      @dialog-submit="dialogSubmit"
      @dialog-cancel="dialogCancel"
      @update-table-data="updateTableData"
    />
  </div>
</template>
