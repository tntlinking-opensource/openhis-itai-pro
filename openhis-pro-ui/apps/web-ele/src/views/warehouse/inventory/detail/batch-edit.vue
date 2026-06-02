<script setup lang="ts">
import type { ComputedRef } from 'vue';
import type { FormItemConfig } from '@vben-core/table-layout-ui';

import type { VxeGridProps } from '#/adapter/vxe-table';
import type { InventoryItemDetail } from '#/api/core/common';
import type {
  BatchCreateInventoryDetailItem,
  BatchCreateInventoryParams,
  BatchUpdateInventoryParams,
  GetBatchInventoryParams,
  InventoryDetailResponse,
  InventoryDetailSupplyItem,
} from '#/api/warehouse/inventory';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Filter } from '@vben-core/table-layout-ui';

import { dayjs, ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getWarehouseByType, submitForReview } from '#/api/warehouse/common';
import {
  batchCreateInventoryApi,
  batchUpdateInventoryApi,
  getBatchInventoryApi,
  initPharmacyWarehouseSuplyApi,
} from '#/api/warehouse/inventory';
import { useRequest } from '#/hooks/use-request';
import { decimalAdd, decimalSub, decimalToFixed } from '#/utils/decimal-util';

import { calcTotalPrice, formatQuantityByUnit } from '../../util';

defineOptions({
  name: 'WarehouseInventoryBatchEdit',
});

const props = withDefaults(
  defineProps<{
    inventoryDetail?: InventoryDetailResponse | null;
    isInDialog?: boolean;
    state?: 'edit' | 'view';
  }>(),
  {
    inventoryDetail: undefined,
    isInDialog: false,
    state: 'edit',
  },
);

type InventoryBatchQuery = Partial<GetBatchInventoryParams> & {
  purposeLocationId?: string;
  purposeLocationId_dictText?: string;
  purposeTypeEnum?: string;
  purposeTypeEnum_dictText?: string;
};

const batchState = reactive<{
  categoryOptions: ApiOptionItem[];
  items: InventoryDetailSupplyItem[];
  pageNo: number;
  pageSize: number;
  profitReasonOptions: ApiOptionItem[];
  query: InventoryBatchQuery;
  total: number;
}>({
  items: [],
  pageNo: 1,
  pageSize: 50,
  categoryOptions: [],
  profitReasonOptions: [],
  query: {},
  total: 0,
});

const router = useRouter();

const isDialogMode = computed(() => !!props.inventoryDetail);
const isViewMode = computed(() => props.state === 'view');

type BatchRow = {
  id?: string;
  inventoryId?: string;
  isUnit?: boolean;
  itemId?: string;
  itemQuantity?: number;
  itemTable?: string;
  lotNumber?: string;
  manufacturer?: string;
  name?: string;
  /** 盘前库存数量（用于计算盈亏） */
  preStockQuantity?: number;
  price?: number;
  productInfo?: Partial<InventoryItemDetail>;
  /** 盈亏金额（= 实盘金额 - 盘前金额） */
  /** 盈亏数量（= 实盘数量减盘前库存），与 itemQuantity 联动 */
  profitLossQuantity?: number;
  /** 盈亏金额（= 实盘金额 - 盘前金额） */
  profitLossAmount?: number;
  reasonCode?: string;
  reasonCode_dictText?: string;
  remark?: string;
  stockQuantity?: string;
  totalPrice?: number;
  totalQuantity?: number;
  totalVolume?: string;
  unitCode?: string;
  unitCode_dictText?: string;
  unitList?: ApiOptionItem[];
  version?: string;
};

const { run: runGetBatchInventory, loading } = useRequest(getBatchInventoryApi, {
  manual: true,
});

const { run: runBatchCreate, loading: createLoading } = useRequest(batchCreateInventoryApi, {
  manual: true,
  onSuccess: (res: { busNo?: string; data?: { busNo?: string } }) => {
    const busNo = res?.data?.busNo ?? res?.busNo ?? '';
    if (busNo) supplyBusNo.value = busNo;
    ElMessage.success(busNo ? `创建成功：${busNo}` : '创建成功');
    changedRowMap.clear();
  },
});

const { run: runBatchUpdate, loading: updateLoading } = useRequest(batchUpdateInventoryApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('保存成功');
    changedRowMap.clear();
  },
});

const formOptions = reactive<{
  warehouseOptions: ApiOptionItem[];
  warehouseTypeOptions: ApiOptionItem[];
}>({
  warehouseOptions: [],
  warehouseTypeOptions: [],
});

const changedRowMap = reactive(new Map<string, BatchRow>());
const supplyBusNo = ref('');

function getRowKey(row: BatchRow): string {
  return `${row.itemTable ?? ''}::${row.itemId ?? row.id ?? ''}`;
}

function markRowChanged(row: BatchRow) {
  const key = getRowKey(row);
  if (!key || key.endsWith('::')) return;
  changedRowMap.set(key, { ...row });
}

const formModel = reactive<{
  dateRange: string[];
  purposeLocationId: string;
  purposeTypeEnum: string;
  keyword: string;
}>({
  purposeTypeEnum: '',
  purposeLocationId: '',
  dateRange: [],
  keyword: '',
});

function syncQueryFromFormModel() {
  const q = batchState.query;
  q.purposeTypeEnum = formModel.purposeTypeEnum || undefined;
  q.purposeLocationId = formModel.purposeLocationId || undefined;
  q.keyword = formModel.keyword || undefined;
  if (formModel.dateRange?.length === 2) {
    const start = formModel.dateRange[0];
    const end = formModel.dateRange[1];
    q.dateRange =
      start !== undefined && end !== undefined ? { startTime: start, endTime: end } : undefined;
  } else {
    q.dateRange = undefined;
  }
  const warehouseTypeOpt = formOptions.warehouseTypeOptions.find(
    (o) => o.value === formModel.purposeTypeEnum,
  );
  const warehouseOpt = formOptions.warehouseOptions.find(
    (o) => o.value === formModel.purposeLocationId,
  );
  q.purposeTypeEnum_dictText = getOptionLabel(warehouseTypeOpt) || undefined;
  q.purposeLocationId_dictText = getOptionLabel(warehouseOpt) || undefined;
}

async function handleLocationTypeChange(value: string) {
  try {
    if (!value) {
      formOptions.warehouseOptions = [];
      formModel.purposeLocationId = '';
      return;
    }
    const res = await getWarehouseByType(value);
    formOptions.warehouseOptions = res?.data ?? res ?? [];
    formModel.purposeLocationId = '';
  } catch {
    formOptions.warehouseOptions = [];
    formModel.purposeLocationId = '';
  }
}

function toFormOptions(opts: ApiOptionItem[]) {
  return opts.map((o) => ({ label: getOptionLabel(o), value: o.value }));
}

const formItems = computed<FormItemConfig[]>(() => {
  const disabled = isDialogMode.value || isViewMode.value;
  return [
    {
      label: '关键字',
      prop: 'keyword',
      type: 'input',
      placeholder: '名称 ｜ 编码',
      clearable: true,
      disabled,
      extraprops: disabled ? { disabled: true, readonly: true } : {},
    },
    {
      label: '仓库类型',
      prop: 'purposeTypeEnum',
      type: 'select',
      multiple: false,
      required: true,
      options: toFormOptions(formOptions.warehouseTypeOptions),
      clearable: true,
      filterable: true,
      disabled,
      checkBeforeChange: true,
      onChange: async (value) => {
        if (disabled) return false;
        await handleLocationTypeChange(value as string);
        return true;
      },
    },
    {
      label: '仓库',
      prop: 'purposeLocationId',
      type: 'select',
      multiple: false,
      required: true,
      options: toFormOptions(formOptions.warehouseOptions),
      clearable: true,
      filterable: true,
      disabled,
    },
    {
      label: '日期范围',
      prop: 'dateRange',
      type: 'daterange',
      valueFormat: 'YYYY-MM-DD HH:mm:ss',
      startPlaceholder: '开始日期',
      endPlaceholder: '结束日期',
      disabled,
      extraprops: disabled ? { disabled: true } : {},
    },
  ];
});

const columns: VxeGridProps<BatchRow>['columns'] = [
  { type: 'checkbox', width: 50, fixed: 'left', field: 'checkbox' },
  { type: 'seq', title: '序号', width: 70, fixed: 'left', field: 'seq' },
  {
    title: '项目名称',
    field: 'name',
    width: 220,
  },
  { title: '规格', field: 'totalVolume', width: 160 },
  {
    title: '盘点单位',
    field: 'unitCode',
    width: 120,
    editRender: {
      name: 'UnitSelect',
      props: {
        onChange: (val: string, row: BatchRow) => {
          row.isUnit = val === row.productInfo?.unitCode;
          const p = row.productInfo;
          if (p && (p as any).totalQuantity !== null && (p as any).totalQuantity !== undefined) {
            row.stockQuantity = formatQuantityByUnit(
              (p as any).totalQuantity,
              (p as any).partPercent ?? 1,
              (p as any).unitCode_dictText ?? '',
              (p as any).minUnitCode_dictText ?? '',
              row.isUnit === true,
            );
          }
          markRowChanged(row);
        },
      },
    },
  },
  { title: '盘前库存', field: 'stockQuantity', width: 120 },
  {
    title: '实盘数量',
    field: 'itemQuantity',
    width: 280,
    editRender: {
      name: 'UnitQuantityInput',
      props: {
        onChange: (val: number, row: BatchRow) => {
          if (!row) return;
          const q = val || 0;
          row.itemQuantity = q;
          row.profitLossQuantity = decimalSub(q, row.preStockQuantity ?? 0);
          if (row.price !== null && row.price !== undefined) {
            row.totalPrice = calcTotalPrice(q, row.price);
          }
          row.profitLossAmount = calcProfitLossAmount(row);
          markRowChanged(row);
        },
      },
    },
  },
  { title: '单价', field: 'price', width: 120 },
  { title: '实盘金额', field: 'totalPrice', width: 120 },
  {
    title: '盈亏数量',
    field: 'profitLossQuantity',
    width: 280,
    editRender: {
      name: 'UnitQuantityInput',
      props: {
        onChange: (val: number, row: BatchRow) => {
          if (!row) return;
          const diff = val ?? 0;
          row.profitLossQuantity = diff;
          const pre = row.preStockQuantity ?? 0;
          row.itemQuantity = decimalAdd(pre, diff);
          if (row.price !== null && row.price !== undefined) {
            row.totalPrice = calcTotalPrice(row.itemQuantity, row.price);
          }
          row.profitLossAmount = calcProfitLossAmount(row);
          markRowChanged(row);
        },
      },
    },
  },
  {
    title: '盈亏金额',
    field: 'profitLossAmount',
    width: 120,
    formatter: ({ row }: { row: BatchRow }) => {
      const diff = row.profitLossAmount ?? calcProfitLossAmount(row);
      const formatted = decimalToFixed(diff, 2);
      return diff === 0 ? '0' : diff > 0 ? `+${formatted}` : String(formatted);
    },
  },
  {
    title: '产品批号',
    field: 'lotNumber',
    width: 140,
    editRender: {
      name: 'ElInput',
      props: {
        onChange: (_val: string, row: BatchRow) => markRowChanged(row),
      },
    },
  },
  {
    title: '盈亏类型',
    field: 'reasonCode',
    width: 140,
    editRender: {
      name: 'ElSelect',
      props: {
        clearable: true,
        filterable: true,
        options: batchState.profitReasonOptions,
        valueKey: 'value',
        labelKey: 'label',
        onChange: (val: string, row: BatchRow) => {
          const options = batchState.profitReasonOptions || [];
          const opt = options.find((o) => o.value === val);
          row.reasonCode = val;
          row.reasonCode_dictText = opt?.label ?? '';
          markRowChanged(row);
        },
      },
    },
  },
  {
    title: '备注',
    field: 'remark',
    width: 160,
    editRender: {
      name: 'ElInput',
      props: {
        onChange: (_val: string, row: BatchRow) => markRowChanged(row),
      },
    },
  },
];

const handlePageChange = async ({ currentPage, pageSize }: any) => {
  const query = batchState.query;
  const params: Partial<GetBatchInventoryParams> = {
    ...query,
    pageNo: Number(currentPage) || 1,
    pageSize: Number(pageSize) || 100,
  };

  Object.keys(params).forEach((key) => {
    if (params[key as keyof GetBatchInventoryParams] === undefined) {
      delete params[key as keyof GetBatchInventoryParams];
    }
  });

  const res = await runGetBatchInventory(params);
  const items = (res?.content || []) as InventoryDetailSupplyItem[];
  const total = res?.totalElements ?? res?.totalCount ?? 0;

  batchState.items = items;
  batchState.total = Number(total) || 0;
  batchState.pageNo = Number(currentPage) || 1;
  batchState.pageSize = Number(pageSize) || 100;
};

const gridOptions: VxeGridProps<BatchRow> = reactive({
  autoResize: true,
  height: '100%',
  border: true,
  showOverflow: true,
  showFooter: false,
  size: 'medium',
  data: [],
  columns,
  editConfig: {
    enabled: true,
    trigger: 'click',
    mode: 'row',
  },
  pagerConfig: {
    enabled: true,
    total: batchState.total,
    currentPage: batchState.pageNo,
    pageSize: batchState.pageSize,
    pageSizeOptions: [50, 100, 200],
  },
  //   toolbarConfig: {
  //     zoom: true,
  //   },
});

const [Grid, gridApi] = useVbenVxeGrid<BatchRow>({
  gridOptions,
  gridEvents: {
    pageChange: handlePageChange,
  },
});

function mapItemsToRows(list?: InventoryDetailSupplyItem[] | null): BatchRow[] {
  if (!Array.isArray(list)) return [];
  return list.map((item) => {
    const isUnit = item.defUnitCode === item.unitCode;
    const unitList: ApiOptionItem[] = [
      { label: item.unitCode_dictText || '', value: item.unitCode || '' },
      ...(item.minUnitCode_dictText && item.minUnitCode
        ? [{ label: item.minUnitCode_dictText, value: item.minUnitCode }]
        : []),
    ];
    const totalQ = item.totalQuantity ?? 0;
    const realAmount = item.totalPrice ?? calcTotalPrice(item.itemQuantity ?? 0, item.price ?? 0);
    const preAmount = calcTotalPrice(totalQ, item.price ?? 0);
    return {
      ...item,
      version: item.version,
      id: item.id,
      name: item.name ?? '',
      itemQuantity: item.itemQuantity ?? 0,
      totalQuantity: item.totalQuantity ?? 0,
      totalPrice: item.totalPrice ?? 0,
      manufacturer: item.manufacturer ?? item.manufacturerText,
      productInfo: item as Partial<InventoryItemDetail>,
      unitList,
      stockQuantity: formatQuantityByUnit(
        totalQ,
        item.partPercent ?? 1,
        item.unitCode_dictText ?? '',
        item.minUnitCode_dictText ?? '',
        true,
      ),
      preStockQuantity: totalQ,
      profitLossQuantity: decimalSub(item.itemQuantity ?? 0, totalQ),
      profitLossAmount: decimalSub(realAmount, preAmount, 2),
      isUnit,
    };
  });
}

function buildDetail(row: BatchRow): BatchCreateInventoryDetailItem {
  return {
    name: row.name ?? '',
    itemId: row.itemId ?? '',
    volume: row.totalVolume ?? '',
    unitCode: row.unitCode ?? '',
    unitCode_dictText: row.unitCode_dictText ?? row.unitCode ?? '',
    itemQuantity: row.itemQuantity ?? 0,
    totalQuantity: row.itemQuantity ?? 0,
    price: row.price ?? 0,
    totalPrice: row.totalPrice ?? 0,
    lotNumber: row.lotNumber ?? '',
    reasonCode: row.reasonCode ?? '',
    reasonCode_dictText: row.reasonCode_dictText ?? row.reasonCode ?? '',
    remark: row.remark ?? '',
    manufacturer: row.manufacturer ?? '',
    itemTable: row.itemTable ?? '',
    version: row.version,
    inventoryId: row.inventoryId ?? '',
  };
}

function buildUpdateSupplyItem(row: BatchRow): BatchUpdateInventoryParams['details'][number] {
  return {
    id: row.id ?? '',
    name: row.name ?? '',
    itemId: row.itemId ?? '',
    volume: row.totalVolume ?? '',
    unitCode: row.unitCode ?? '',
    unitCode_dictText: row.unitCode_dictText ?? row.unitCode ?? '',
    itemQuantity: row.itemQuantity ?? 0,
    totalQuantity: row.itemQuantity ?? 0,
    price: row.price ?? 0,
    totalPrice: row.totalPrice ?? 0,
    lotNumber: row.lotNumber ?? '',
    reasonCode: row.reasonCode ?? '',
    reasonCode_dictText: row.reasonCode_dictText ?? row.reasonCode ?? '',
    remark: row.remark ?? '',
    manufacturer: row.manufacturer ?? '',
    itemTable: row.itemTable ?? '',
    version: row.version,
    inventoryId: row.inventoryId ?? '',
  };
}

function calcProfitLossAmount(row: BatchRow): number {
  const realAmount = row.totalPrice ?? 0;
  const preQ = row.preStockQuantity ?? 0;
  const price = row.price ?? 0;
  const preAmount = calcTotalPrice(preQ, price);
  return decimalSub(realAmount, preAmount, 2);
}

async function doSearch() {
  syncQueryFromFormModel();
  const pageSize = gridOptions.pagerConfig?.pageSize || batchState.pageSize || 50;
  await handlePageChange({ currentPage: 1, pageSize });
}

const handleSearch = async () => {
  if (!formModel.purposeTypeEnum?.trim()) {
    ElMessage.warning('请选择仓库类型');
    return;
  }
  if (!formModel.purposeLocationId?.trim()) {
    ElMessage.warning('请选择仓库');
    return;
  }
  if (batchState.items.length > 0) {
    try {
      await ElMessageBox.confirm('当前有盘点数据，重新搜索将清空当前数据，是否继续？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      });
    } catch {
      return;
    }
    changedRowMap.clear();
  }
  await doSearch();
};

function doReset() {
  formModel.purposeTypeEnum = '';
  formModel.purposeLocationId = '';
  formModel.dateRange = [];
  formModel.keyword = '';
  syncQueryFromFormModel();
  batchState.items = [];
  batchState.total = 0;
  batchState.pageNo = 1;
}

const handleReset = async () => {
  if (batchState.items.length > 0) {
    try {
      await ElMessageBox.confirm('当前有盘点数据，重置将清空当前数据，是否继续？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      });
    } catch {
      return;
    }
    changedRowMap.clear();
  }
  doReset();
};

const handleSave = async () => {
  if (!formModel.purposeTypeEnum?.trim()) {
    ElMessage.warning('请选择仓库类型');
    return;
  }
  if (!formModel.purposeLocationId?.trim()) {
    ElMessage.warning('请选择仓库');
    return;
  }
  if (changedRowMap.size === 0) {
    ElMessage.warning('暂无修改内容，无需保存');
    return;
  }
  if (!batchState.categoryOptions?.length || !batchState.categoryOptions[0]?.value) {
    ElMessage.warning('盘点类型缺失，无法保存');
    return;
  }

  const dateRange =
    formModel.dateRange?.length === 2
      ? { startTime: formModel.dateRange[0], endTime: formModel.dateRange[1] }
      : undefined;

  if (!supplyBusNo.value) {
    const details = [...changedRowMap.values()].map((item) => buildDetail(item));
    const params: BatchCreateInventoryParams = {
      categoryEnum: String(batchState.categoryOptions[0].value ?? ''),
      purposeTypeEnum: String(formModel.purposeTypeEnum ?? ''),
      purposeLocationId: String(formModel.purposeLocationId ?? ''),
      applyTime: dayjs().format('YYYY-MM-DD HH:mm:ss'),
      details,
      isBatches: true,
      dateRange,
    };
    await runBatchCreate(params);
    return;
  }

  const details = [...changedRowMap.values()].map((item) => buildUpdateSupplyItem(item));
  const params: BatchUpdateInventoryParams = {
    supplyBusNo: supplyBusNo.value,
    categoryEnum: String(batchState.categoryOptions[0].value ?? ''),
    purposeTypeEnum: String(formModel.purposeTypeEnum ?? ''),
    applyTime: dayjs().format('YYYY-MM-DD HH:mm:ss'),
    deleteIds: [],
    details,
    isBatches: true,
  };
  await runBatchUpdate(params);
};

const handleSaveAndSubmit = async () => {
  await handleSave();
  if (!supplyBusNo.value) return;
  await submitForReview('inventory', { supplyBusNo: supplyBusNo.value, type: 'submit' });
  ElMessage.success('提交审核成功');
};

const saveLoading = computed(() => !!(createLoading.value || updateLoading.value));

const handleCancel = () => {
  if (props.isInDialog) {
    return;
  }
  router.back();
};

function getOptionLabel(
  opt: null | undefined | { dictText?: string; label?: string; text?: string },
): string {
  return opt?.label ?? opt?.dictText ?? opt?.text ?? '';
}

onMounted(async () => {
  try {
    const res = await initPharmacyWarehouseSuplyApi();
    batchState.categoryOptions.splice(
      0,
      batchState.categoryOptions.length,
      ...(res.categoryOptions || []),
    );
    batchState.profitReasonOptions.splice(
      0,
      batchState.profitReasonOptions.length,
      ...(res.profitReasonOptions || []),
    );
    formOptions.warehouseTypeOptions = res.warehouseTypeOptions || [];
  } catch {
    batchState.categoryOptions.splice(0);
    batchState.profitReasonOptions.splice(0);
  }
});

watch(
  () => [batchState.items, batchState.pageNo, batchState.pageSize, batchState.total],
  ([items, pageNo, pageSize, total]) => {
    gridApi.setGridOptions({
      pagerConfig: {
        ...gridOptions.pagerConfig,
        enabled: true,
        total: (total as number) ?? 0,
        currentPage: (pageNo as number) ?? 1,
        pageSize: (pageSize as number) ?? 50,
      } as any,
      data: mapItemsToRows(items as InventoryDetailSupplyItem[] | null),
    });
  },
  { immediate: true },
);

watch(
  () => props.state,
  (state) => {
    const disabled = state === 'view';
    gridApi.setGridOptions({
      ...gridOptions,
      editConfig: {
        ...gridOptions.editConfig,
        enabled: !disabled,
      },
    });
  },
  { immediate: true },
);

watch(
  () => props.inventoryDetail,
  (detail) => {
    if (!detail) return;
    batchState.query.purposeTypeEnum = detail.purposeTypeEnum || undefined;
    batchState.query.purposeLocationId = detail.purposeLocationId || undefined;
    formModel.purposeTypeEnum = detail.purposeTypeEnum || '';
    formModel.purposeLocationId = detail.purposeLocationId || '';

    const items = (detail.supplyList || []) as InventoryDetailSupplyItem[];
    batchState.items = items;
    batchState.total = items.length;
    batchState.pageNo = 1;
    batchState.pageSize = items.length;
  },
  { immediate: true },
);

defineExpose<{
  save: () => Promise<void>;
  saveAndSubmit: () => Promise<void>;
  saveLoading: ComputedRef<boolean>;
}>({
  save: handleSave,
  saveAndSubmit: handleSaveAndSubmit,
  saveLoading,
});
</script>

<template>
  <Page :class="props.isInDialog ? '!mt-0' : 'mt-2'">
    <div class="flex h-full min-h-0 flex-col overflow-hidden !p-0">
      <Filter
        :form-items="formItems"
        :query-params="formModel as Record<string, unknown>"
        :show-default-buttons="!props.inventoryDetail"
        @query="handleSearch"
        @reset="handleReset"
      />
      <div class="min-h-0 flex-1">
        <Grid :loading="loading" @page-change="handlePageChange" />
      </div>
    </div>
    <template v-if="!props.isInDialog" #footer>
      <ElButton type="primary" :loading="saveLoading" @click="handleSave">保存</ElButton>
      <ElButton type="primary" :loading="saveLoading" @click="handleSaveAndSubmit">
        保存并提交审核
      </ElButton>
      <ElButton @click="handleCancel">取消</ElButton>
    </template>
  </Page>
</template>
