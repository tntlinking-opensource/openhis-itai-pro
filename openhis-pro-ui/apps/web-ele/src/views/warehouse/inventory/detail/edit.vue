<script setup lang="ts">
import type { ComputedRef } from 'vue';

import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { InventoryItemDetail } from '#/api/core/common';
import type {
  BatchCreateInventoryDetailItem,
  BatchCreateInventoryParams,
  BatchUpdateInventoryParams,
  InventoryDetailResponse,
  InventoryDetailSupplyItem,
} from '#/api/warehouse/inventory';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { dayjs, ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getWarehouseByType } from '#/api/warehouse/common';
import {
  batchCreateInventoryApi,
  batchUpdateInventoryApi,
  findInventoryByBusNoApi,
  initPharmacyWarehouseSuplyApi,
} from '#/api/warehouse/inventory';
import { useRequest } from '#/hooks/use-request';
import { decimalAdd, decimalSub, decimalToFixed } from '#/utils/decimal-util';

import { calcTotalPrice, formatQuantityByUnit } from '../../util';

defineOptions({
  name: 'WarehouseInventoryDetailEdit',
});

const props = withDefaults(
  defineProps<{
    inventoryDetail?: InventoryDetailResponse | null;
    isInDialog?: boolean;
    state?: 'edit' | 'view';
  }>(),
  { isInDialog: false, state: 'edit', inventoryDetail: undefined },
);
const emit = defineEmits<{
  success: [];
}>();

const router = useRouter();
const isViewMode = ref(false);
const localDetail = ref<InventoryDetailResponse | null>(null);
const effectiveDetail = computed<InventoryDetailResponse | null>(
  () => localDetail.value ?? props.inventoryDetail ?? null,
);
const formOptions = reactive<{
  categoryOptions: ApiOptionItem[];
  profitReasonOptions: ApiOptionItem[];
  warehouseOptions: ApiOptionItem[];
  warehouseTypeOptions: ApiOptionItem[];
}>({
  categoryOptions: [],
  warehouseTypeOptions: [],
  warehouseOptions: [],
  profitReasonOptions: [],
});

type InventoryFormValues = {
  applyTime?: string;
  categoryEnum?: string;
  purposeLocationId?: string;
  purposeTypeEnum?: string;
};

interface InventoryItemRow {
  id?: string;
  version?: string;
  name?: string;
  itemId?: string;
  totalVolume?: string;
  unitCode?: string;
  unitCode_dictText?: string;
  manufacturer?: string;
  itemTable?: string;
  itemQuantity?: number;
  price?: number;
  totalPrice?: number;
  lotNumber?: string;
  reasonCode?: string;
  reasonCode_dictText?: string;
  remark?: string;
  productInfo?: Partial<InventoryItemDetail>;
  unitList?: ApiOptionItem[];
  isUnit?: boolean;
  /** 盘前库存（展示用） */
  stockQuantity?: string;
  /** 盘前库存数量（用于计算盈亏） */
  preStockQuantity?: number;
  /** 盈亏数量（= 实盘数量减盘前库存），与 itemQuantity 联动 */
  profitLossQuantity?: number;
  /** 盈亏金额（= 实盘金额 - 盘前金额） */
  profitLossAmount?: number;
  inventoryId?: string;
}

const [Form, formApi] = useVbenForm(
  reactive({
    schema: computed(() => [
      {
        component: 'Select',
        fieldName: 'categoryEnum',
        label: '盘点类型',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.categoryOptions,
        },
      },
      {
        component: 'Select',
        fieldName: 'purposeTypeEnum',
        label: '仓库类型',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.warehouseTypeOptions,
          async onChange(value: string) {
            try {
              if (!value) {
                formOptions.warehouseOptions = [];
                await formApi.setFieldValue('purposeLocationId', undefined);
                return;
              }
              const res = await getWarehouseByType(value);
              formOptions.warehouseOptions = res?.data || [];
              await formApi.setFieldValue('purposeLocationId', undefined);
            } catch {
              formOptions.warehouseOptions = [];
              await formApi.setFieldValue('purposeLocationId', undefined);
            }
          },
        },
      },
      {
        component: 'Select',
        fieldName: 'purposeLocationId',
        label: '仓库',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.warehouseOptions,
        },
      },
      {
        component: 'DatePicker',
        fieldName: 'applyTime',
        label: '制单时间',
        hide: true,
        rules: 'required',
        componentProps: {
          valueFormat: 'YYYY-MM-DD HH:mm:ss',
          format: 'YYYY-MM-DD HH:mm:ss',
          placeholder: '请选择制单时间',
        },
        defaultValue: dayjs().format('YYYY-MM-DD HH:mm:ss'),
      },
    ]),
    commonConfig: {
      componentProps: {
        style: { width: '220px' },
      },
      disabled: computed(() => props.state === 'view'),
    },
    wrapperClass: 'flex flex-row flex-wrap',
    showDefaultActions: false,
  }),
);

const validRules = {
  productInfo: [{ required: true, message: '必须填写' }],
  unitCode: [{ required: true, message: '必须填写' }],
  itemQuantity: [{ required: true, message: '必须填写' }],
};

function calcProfitLossAmount(row: InventoryItemRow): number {
  const realAmount = row.totalPrice ?? 0;
  const preQ = row.preStockQuantity ?? 0;
  const price = row.price ?? 0;
  const preAmount = calcTotalPrice(preQ, price);
  return decimalSub(realAmount, preAmount, 2);
}

const columns: VxeGridPropTypes.Column[] = [
  { type: 'checkbox', width: 50, fixed: 'left', field: 'checkbox' },
  { type: 'seq', title: '序号', width: 70, fixed: 'left', field: 'seq' },
  {
    title: '项目名称',
    field: 'productInfo',
    width: 220,
    editRender: {
      name: 'WarehouseProductSelectTable',
      props: {
        query: async () => {
          const values = (await formApi.getValues()) as InventoryFormValues;
          return {
            drugType: values.categoryEnum,
            purposeLocationId: values.purposeLocationId,
          };
        },
        onSelected: (product: InventoryItemDetail, row: InventoryItemRow) => {
          const unitList: ApiOptionItem[] = [
            { label: product.unitCode_dictText || '', value: product.unitCode || '' },
            ...(product.minUnitCode_dictText && product.minUnitCode
              ? [{ label: product.minUnitCode_dictText, value: product.minUnitCode }]
              : []),
          ];
          Object.assign(row, {
            ...product,
            productInfo: product,
            name: product.name,
            itemId: product.itemId,
            totalVolume: product.volume,
            manufacturer: product.manufacturer,
            unitCode: product.unitCode,
            unitCode_dictText: product.unitCode_dictText,
            price: product.price,
            lotNumber: product.lotNumber ?? '',
            itemQuantity: 0,
            totalPrice: 0,
            itemTable: product.itemTable ?? '',
            stockQuantity: formatQuantityByUnit(
              product.totalQuantity || 0,
              product.partPercent ?? 1,
              product.unitCode_dictText ?? '',
              product.minUnitCode_dictText ?? '',
              true,
            ),
            preStockQuantity: product.totalQuantity ?? 0,
            profitLossQuantity: decimalSub(0, product.totalQuantity ?? 0),
            unitList,
            isUnit: true,
          });
        },
      },
    },
  },
  { title: '规格', field: 'totalVolume', width: 160 },
  {
    title: '盘点单位',
    field: 'unitCode',
    width: 120,
    editRender: {
      name: 'UnitSelect',
      props: {
        onChange: (val: string, row: InventoryItemRow) => {
          row.isUnit = val === row.productInfo?.unitCode;
          const p = row.productInfo;
          if (p && p.totalQuantity !== null && p.totalQuantity !== undefined) {
            row.stockQuantity = formatQuantityByUnit(
              p.totalQuantity,
              p.partPercent ?? 1,
              p.unitCode_dictText ?? '',
              p.minUnitCode_dictText ?? '',
              row.isUnit === true,
            );
          }
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
        onChange: (val: number, row: InventoryItemRow) => {
          if (!row) return;
          const q = val || 0;
          row.itemQuantity = q;
          row.profitLossQuantity = decimalSub(q, row.preStockQuantity ?? 0);
          if (row.price !== null && row.price !== undefined) {
            row.totalPrice = calcTotalPrice(q, row.price);
          }
          row.profitLossAmount = calcProfitLossAmount(row);
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
        onChange: (val: number, row: InventoryItemRow) => {
          if (!row) return;
          const diff = val ?? 0;
          row.profitLossQuantity = diff;
          const pre = row.preStockQuantity ?? 0;
          row.itemQuantity = decimalAdd(pre, diff);
          if (row.price !== null && row.price !== undefined) {
            row.totalPrice = calcTotalPrice(row.itemQuantity, row.price);
          }
          row.profitLossAmount = calcProfitLossAmount(row);
        },
      },
    },
  },
  {
    title: '盈亏金额',
    field: 'profitLossAmount',
    width: 120,
    formatter: ({ row }: { row: InventoryItemRow }) => {
      const diff = row.profitLossAmount ?? calcProfitLossAmount(row);
      const formatted = decimalToFixed(diff, 2);
      return diff === 0 ? '0' : diff > 0 ? `+${formatted}` : String(formatted);
    },
  },
  { title: '产品批号', field: 'lotNumber', width: 140, editRender: { name: 'ElInput' } },
  {
    title: '盈亏类型',
    field: 'reasonCode',
    width: 140,
    editRender: {
      name: 'ElSelect',
      props: {
        clearable: true,
        filterable: true,
        options: formOptions.profitReasonOptions,
        valueKey: 'value',
        labelKey: 'label',
        onChange: (val: string, row: InventoryItemRow) => {
          const opt = formOptions.profitReasonOptions.find((o) => o.value === val);
          row.reasonCode = val;
          row.reasonCode_dictText = opt?.label ?? '';
        },
      },
    },
  },
  { title: '备注', field: 'remark', width: 160, editRender: { name: 'ElInput' } },
  {
    title: '操作',
    field: 'action',
    width: 100,
    align: 'center',
    fixed: 'right',
    visible: props.state !== 'view',
    slots: { default: 'action' },
  },
];

const gridOptions: VxeGridProps<InventoryItemRow> = reactive({
  autoResize: true,
  height: '100%',
  border: true,
  showOverflow: true,
  showFooter: true,
  size: 'medium',
  data: [],
  editConfig: {
    enabled: true,
    trigger: 'click',
    mode: 'row',
  },
  columns,
  pagerConfig: { enabled: false },
  editRules: validRules,
  footerMethod: ({ data }) => {
    const totalPrice = (data || []).reduce((sum, row) => sum + (Number(row.totalPrice) || 0), 0);
    return [['合计', `${totalPrice} 元`]];
  },
});

const [Grid, gridApi] = useVbenVxeGrid<InventoryItemRow>({ gridOptions });

function mapDetailListToRows(list: InventoryDetailSupplyItem[]): InventoryItemRow[] {
  if (!Array.isArray(list)) return [];
  return list.map((item) => {
    const unitList: ApiOptionItem[] = [
      { label: item.unitCode_dictText || '', value: item.unitCode || '' },
      ...(item.minUnitCode_dictText && item.minUnitCode
        ? [{ label: item.minUnitCode_dictText, value: item.minUnitCode }]
        : []),
    ];
    const totalQ = item.totalQuantity ?? item.itemQuantity ?? 0;
    const realAmount = item.totalPrice ?? calcTotalPrice(item.itemQuantity ?? 0, item.price ?? 0);
    const preAmount = calcTotalPrice(totalQ, item.price ?? 0);
    return {
      ...item,
      id: item.id,
      name: item.name ?? '',
      itemQuantity: item.itemQuantity ?? 0,
      totalPrice: item.totalPrice ?? 0,
      manufacturer: item.manufacturer ?? item.manufacturerText,
      productInfo: item as Partial<InventoryItemDetail>,
      unitList,
      isUnit: true,
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
    };
  });
}

async function applyDetailToFormAndGrid(detail: InventoryDetailResponse) {
  formApi.setValues({
    categoryEnum: detail.categoryEnum,
    purposeTypeEnum: detail.purposeTypeEnum,
    purposeLocationId: detail.purposeLocationId,
    applyTime: detail.applyTime ?? dayjs().format('YYYY-MM-DD HH:mm:ss'),
  });
  if (detail.purposeTypeEnum) {
    try {
      const res = await getWarehouseByType(String(detail.purposeTypeEnum));
      formOptions.warehouseOptions = res?.data || [];
    } catch {
      formOptions.warehouseOptions = [];
    }
  }
  const rows = mapDetailListToRows(detail.supplyList ?? []);
  await nextTick();
  gridApi.grid?.loadData(rows);
}

async function refreshDetailAfterSave(busNo: string) {
  const res = await findInventoryByBusNoApi({ busNo });
  const detail = (res as { data?: InventoryDetailResponse })?.data ?? res;
  if (detail?.supplyBusNo) {
    localDetail.value = detail;
    await applyDetailToFormAndGrid(detail);
  }
}

watch(
  [() => props.inventoryDetail, () => props.state],
  async ([detail]) => {
    const disabled = props.state === 'view';
    isViewMode.value = disabled;
    formApi.setState({ commonConfig: { disabled } });
    gridApi.setGridOptions({
      ...gridOptions,
      columns: columns.map((col) =>
        col.field === 'action' ? { ...col, visible: props.state !== 'view' } : col,
      ),
      editConfig: {
        ...gridOptions.editConfig,
        enabled: !disabled,
      },
    });
    localDetail.value = null;
    if (!detail) {
      await formApi.resetForm();
      gridApi.grid?.loadData([]);
      return;
    }
    await nextTick();
    await applyDetailToFormAndGrid(detail);
  },
  { immediate: true },
);

function buildDetails(fullData: InventoryItemRow[]): BatchCreateInventoryDetailItem[] {
  return fullData.map((row) => ({
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
  }));
}

function buildUpdateSupplyList(
  fullData: InventoryItemRow[],
  originalList: InventoryDetailSupplyItem[],
): BatchUpdateInventoryParams['details'] {
  return fullData.map((row, i) => {
    const orig = originalList[i] || ({} as InventoryDetailSupplyItem);
    return {
      id: row.id ?? orig.id ?? '',
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
  });
}

const { run: runBatchCreate, loading: createLoading } = useRequest(batchCreateInventoryApi, {
  manual: true,
  onSuccess: async (res: { busNo?: string; data?: { busNo?: string } }) => {
    const busNo = res?.data?.busNo ?? res?.busNo ?? '';
    if (busNo) await refreshDetailAfterSave(busNo);
    ElMessage.success('创建成功');
    if (props.isInDialog) emit('success');
    else router.push('/warehouse/inventory');
  },
});

const { run: runBatchUpdate, loading: updateLoading } = useRequest(batchUpdateInventoryApi, {
  manual: true,
  onSuccess: async () => {
    const busNo = effectiveDetail.value?.supplyBusNo;
    if (busNo) await refreshDetailAfterSave(busNo);
    ElMessage.success('保存成功');
    if (props.isInDialog) emit('success');
  },
});

const addItem = async () => {
  const formValid = await formApi.validate();
  if (!formValid.valid) return;
  const $grid = gridApi.grid;
  if ($grid) {
    const record: InventoryItemRow = {
      name: '',
      itemId: '',
      totalVolume: '',
      unitCode: '',
      itemQuantity: 0,
      profitLossQuantity: 0,
      price: 0,
      totalPrice: 0,
      lotNumber: '',
      reasonCode: '',
      remark: '',
    };
    const { row } = await $grid.insertAt(record, null);
    await $grid.$nextTick();
    await $grid.scrollToRow(row);
    await $grid.setEditCell(row, 'productInfo');
  }
};

const deleteItem = async (row: 'select' | InventoryItemRow) => {
  const $grid = gridApi.grid;
  const rows = $grid.getCheckboxRecords();
  if (row === 'select' && rows.length === 0) {
    ElMessage.warning('请选择要删除的项目');
    return;
  }
  if (row !== 'select' && !row?.name) {
    await $grid.remove(row);
    return;
  }
  ElMessageBox.confirm('确定删除所选项目吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    await $grid.remove(row === 'select' ? rows : row);
  });
};

async function doSave() {
  const { valid } = await formApi.validate();
  if (!valid) return;
  const $grid = gridApi.grid;
  const validGrid = await $grid.validate(true);
  if (validGrid) return;
  const fullData = $grid.getFullData() as InventoryItemRow[];
  if (fullData.length === 0) {
    ElMessage.warning('请先添加盘点明细');
    return;
  }
  const formValues = (await formApi.getValues()) as InventoryFormValues;
  const detail = effectiveDetail.value;
  const isEdit = !!detail?.supplyBusNo;

  if (isEdit) {
    const originalList = detail?.supplyList ?? [];
    const remainingIds = new Set(
      fullData
        .map((r) => r.id)
        .filter((id): id is string => id !== undefined && id !== null && id !== ''),
    );
    const deleteIds = originalList
      .map((item) => item.id)
      .filter(
        (id): id is string => id !== undefined && id !== null && id !== '' && !remainingIds.has(id),
      );
    const params: BatchUpdateInventoryParams = {
      supplyBusNo: detail.supplyBusNo ?? '',
      categoryEnum: String(formValues.categoryEnum ?? ''),
      purposeTypeEnum: String(formValues.purposeTypeEnum ?? ''),
      applyTime: formValues.applyTime ?? detail.applyTime ?? dayjs().format('YYYY-MM-DD HH:mm:ss'),
      deleteIds,
      details: buildUpdateSupplyList(fullData, originalList),
      isBatches: false,
    };
    await runBatchUpdate(params);
  } else {
    const params: BatchCreateInventoryParams = {
      categoryEnum: String(formValues.categoryEnum ?? ''),
      purposeTypeEnum: String(formValues.purposeTypeEnum ?? ''),
      purposeLocationId: String(formValues.purposeLocationId ?? ''),
      applyTime: formValues.applyTime ?? dayjs().format('YYYY-MM-DD HH:mm:ss'),
      details: buildDetails(fullData),
      isBatches: false,
    };
    await runBatchCreate(params);
  }
}

const handleSave = () => doSave();
const handleSaveAndSubmit = () => doSave();

defineExpose<{
  save: () => Promise<void>;
  saveAndSubmit: () => Promise<void>;
  saveLoading: ComputedRef<boolean>;
}>({
  save: handleSave,
  saveAndSubmit: handleSaveAndSubmit,
  saveLoading: computed(() => !!(createLoading.value || updateLoading.value)),
});

onMounted(async () => {
  try {
    const res = await initPharmacyWarehouseSuplyApi();
    formOptions.categoryOptions = res.categoryOptions || [];
    formOptions.warehouseTypeOptions = res.warehouseTypeOptions || [];
    const nextProfitReasons = res.profitReasonOptions;
    formOptions.profitReasonOptions.splice(
      0,
      formOptions.profitReasonOptions.length,
      ...nextProfitReasons,
    );
  } catch {
    formOptions.categoryOptions = [];
    formOptions.warehouseTypeOptions = [];
    formOptions.profitReasonOptions = [];
  }
  isViewMode.value = props.state === 'view';
});
</script>

<template>
  <Page :class="isInDialog ? '!mt-0' : 'mt-2'">
    <div class="flex h-full flex-col">
      <Form />
      <div class="min-h-0 flex-1">
        <Grid>
          <template #toolbar-actions v-if="!isViewMode">
            <ElButton class="mr-2" type="primary" @click="addItem"> 添加项目 </ElButton>
            <ElButton type="danger" @click="deleteItem('select')"> 删除项目 </ElButton>
          </template>
          <template #action="{ row }">
            <ElButton type="danger" @click="deleteItem(row)" text> 删除 </ElButton>
          </template>
        </Grid>
      </div>
    </div>
    <template v-if="props.state !== 'view' && !isInDialog" #footer>
      <ElButton type="primary" :loading="createLoading || updateLoading" @click="handleSave">
        保存
      </ElButton>
    </template>
  </Page>
</template>
