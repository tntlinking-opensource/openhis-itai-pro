<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { CreatePurchaseParams, ReturnPurchaseParams } from '#/api/warehouse/purchase';
import type { ApiOptionItem } from '#/types/api-response';
import type { ProductDetail } from '#/types/common';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getWarehouseByType } from '#/api/warehouse/common';
import { initPharmacyWarehouseSuplyApi, returnPurchase } from '#/api/warehouse/purchase';
import { useRequest } from '#/hooks/use-request';

import { calcAllTotalPrice, calcTotalPrice } from './util';

type PurchaseDetailForReturn = CreatePurchaseParams & Record<string, any>;

const props = defineProps<{
  /** 原始采购单据号 */
  originalBusNo: string;
  purchaseDetail?: PurchaseDetailForReturn;
  /** 仅用于展示标记，当前组件只处理退库 */
  state?: 'return' | 'view';
}>();

const emit = defineEmits<{
  success: [];
}>();
const isViewMode = ref(false);

const formOptions = reactive<{
  practitionerListOptions: ApiOptionItem[];
  purchaseTypeOptions: ApiOptionItem[];
  supplierListOptions: ApiOptionItem[];
  warehouseOptions: ApiOptionItem[];
  warehousTypeOptions: ApiOptionItem[];
}>({
  practitionerListOptions: [],
  supplierListOptions: [],
  purchaseTypeOptions: [],
  warehousTypeOptions: [],
  warehouseOptions: [],
});

const [Form, formApi] = useVbenForm(
  reactive({
    schema: computed(() => [
      {
        component: 'Select',
        fieldName: 'practitionerId',
        label: '经手人',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.practitionerListOptions.map((item) => ({
            label: item.label,
            value: item.value,
          })),
        },
      },
      {
        component: 'Select',
        fieldName: 'supplierId',
        label: '供应商',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          disabled: true,
          options: formOptions.supplierListOptions.map((item) => ({
            label: item.label,
            value: item.value,
          })),
        },
      },
      {
        component: 'Input',
        fieldName: 'applyTime',
        label: '制单时间',
        componentProps: {
          disabled: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'categoryEnum',
        label: '药品类型',
        componentProps: {
          clearable: true,
          filterable: true,
          disabled: true,
          options: formOptions.purchaseTypeOptions.map((item) => ({
            label: item.label,
            value: item.value,
          })),
        },
      },
      {
        component: 'Select',
        fieldName: 'locationTypeEnum',
        label: '仓库类型',
        componentProps: {
          clearable: true,
          filterable: true,
          disabled: true,
          options: formOptions.warehousTypeOptions.map((item) => ({
            label: item.label,
            value: item.value,
          })),
        },
      },
      {
        component: 'Select',
        fieldName: 'locationId',
        label: '仓库',
        componentProps: {
          clearable: true,
          filterable: true,
          disabled: true,
          options: formOptions.warehouseOptions.map((item) => ({
            label: item.label,
            value: item.value,
          })),
        },
      },
      {
        component: 'Input',
        fieldName: 'returnReason',
        label: '退库原因',
        componentProps: {
          type: 'textarea',
          rows: 2,
        },
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

const columns: VxeGridPropTypes.Column[] = [
  {
    type: 'seq',
    title: '序号',
    width: 70,
    fixed: 'left',
    field: 'seq',
  },
  {
    title: '项目',
    field: 'name',
    width: 220,
  },
  {
    title: '原采购数量',
    field: 'itemQuantity',
    width: 140,
  },
  {
    title: '退库数量',
    field: 'returnQuantity',
    width: 200,
    editRender: {
      name: 'UnitQuantityInput',
      props: {
        onChange: (val: number, row: any) => {
          const max = Number(row.itemQuantity) || 0;
          let next = Number(val) || 0;
          if (next > max) {
            next = max;
            ElMessage.warning('退库数量不能大于采购数量');
          }
          if (next < 0) next = 0;
          row.returnQuantity = next;
          if (row.price) {
            row.totalSalePrice = calcTotalPrice(next, row.price);
          }
        },
      },
    },
  },
  {
    title: '退库原因',
    field: 'returnReason',
    width: 220,
    editRender: {
      name: 'ElInput',
      props: {
        placeholder: '请输入退库原因',
      },
    },
  },
  {
    title: '单价',
    field: 'price',
    width: 120,
    formatter: (params: { cellValue: number }) => `${params.cellValue || 0} 元`,
  },
  {
    title: '退库金额',
    field: 'totalSalePrice',
    width: 160,
    formatter: (params: { cellValue: number }) => `${params.cellValue || 0} 元`,
  },
  {
    title: '计量单位',
    field: 'unitCode',
    width: 140,
    formatter: ({ row }: { row: any }) => {
      const list = (row.unitList || []) as ApiOptionItem[];
      const found = list.find((item) => item.value === row.unitCode);
      return found?.label || row.unitCode || '';
    },
  },
  {
    title: '产品批号',
    field: 'lotNumber',
    width: 160,
  },
  {
    title: '备注',
    field: 'remake',
    width: 160,
    editRender: {
      name: 'ElInput',
      props: {
        placeholder: '请输入备注',
      },
    },
  },
];

const gridOptions: VxeGridProps<ProductDetail> = {
  autoResize: true,
  height: '100%',
  border: true,
  showOverflow: true,
  showFooter: true,
  size: 'medium',
  data: [],
  editConfig: {
    enabled: props.state !== 'view',
    trigger: 'click',
    mode: 'cell',
  },
  columns,
  pagerConfig: {
    enabled: false,
  },
  editRules: {
    returnQuantity: [{ required: true, message: '请填写退库数量' }],
  },
  footerMethod: ({ data }) => {
    const totalPrice = calcAllTotalPrice((data || []) as any[]);
    return [['合计', `${totalPrice} 元`]];
  },
};

const [Grid, gridApi] = useVbenVxeGrid<ProductDetail>({ gridOptions });

const { run: runReturnPurchase } = useRequest(returnPurchase, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('退库申请已提交');
    emit('success');
  },
});

const buildParams = async (): Promise<ReturnPurchaseParams> => {
  const $grid = gridApi.grid;
  if (!$grid) {
    throw new Error('表格未初始化');
  }
  const fullData = $grid.getFullData() as any[];

  const formValues = await formApi.getValues();

  const items = fullData
    .filter((item) => Number(item.returnQuantity) > 0)
    .map((item) => ({
      reason: item.returnReason || '',
      inventoryItemId: item.inventoryItemId || '',
      itemId: String(item.itemId || item.productInfo?.id || ''),
      note: item.remake || '',
      itemName: item.name || item.productInfo?.productName || '',
      quantity: String(item.returnQuantity || 0),
      salePrice: String(item.salePrice ?? item.price ?? 0),
      price: String(item.price ?? 0),
      itemTable: item.itemTable || '',
      unitCode: item.unitCode || '',
      sourceLocationStoreId: item.sourceLocationStoreId || '',
      lotNumber: item.lotNumber || '',
      sourceLocationId: item.sourceLocationId || props.purchaseDetail?.locationId || '',
    }));

  return {
    supplierId: String(props.purchaseDetail?.supplierId || ''),
    originalBusNo: props.originalBusNo,
    remark: String((formValues as any).returnReason || ''),
    busNo: undefined,
    reasonCode: '',
    applicantId: String((formValues as any).practitionerId || ''),
    items,
  };
};

const save = async () => {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }
  const params = await buildParams();
  if (params.items.length === 0) {
    ElMessage.warning('请先填写退库数量');
    return;
  }
  await runReturnPurchase(params);
};

const saveAndSubmit = async () => {
  await save();
};

watch(
  () => props.state,
  (val) => {
    isViewMode.value = val === 'view';
    if (gridApi.grid) {
      gridApi.grid.editConfig = {
        ...(gridOptions.editConfig as any),
        enabled: !isViewMode.value,
      } as any;
    }
  },
  { immediate: true },
);

watch(
  () => props.purchaseDetail,
  async (val) => {
    if (val) {
      await formApi.setValues({
        practitionerId: undefined,
        ...val,
        returnReason: '',
      });
      if (val.locationTypeEnum) {
        try {
          const response = await getWarehouseByType(String(val.locationTypeEnum));
          const data = (response?.data || []) as ApiOptionItem[];
          formOptions.warehouseOptions = data;
        } catch {
          formOptions.warehouseOptions = [];
        }
      }
    }

    const $grid = gridApi.grid;
    if (val && $grid) {
      $grid.loadData((val.supplyList || []) as any[]);
      if (props.state === 'return') {
        await nextTick();
        const tableData = $grid.getTableData?.();
        const firstRow = tableData?.fullData?.[0];
        if (firstRow) {
          await $grid.setEditCell(firstRow, 'returnQuantity');
        }
      }
    }
  },
  { immediate: true, deep: true },
);

defineExpose<{
  save: () => Promise<void>;
  saveAndSubmit: () => Promise<void>;
}>({
  save,
  saveAndSubmit,
});

onMounted(async () => {
  try {
    const res = await initPharmacyWarehouseSuplyApi();
    const data = res || ({} as any);
    formOptions.practitionerListOptions = data.practitionerOptions || [];
    formOptions.supplierListOptions = data.supplierOptions || [];
    formOptions.purchaseTypeOptions = data.categoryOptions || [];
    formOptions.warehousTypeOptions = data.warehouseTypeOptions || [];
    formOptions.warehouseOptions = data.warehouseOptions || [];
  } catch {
    formOptions.practitionerListOptions = [];
    formOptions.supplierListOptions = [];
    formOptions.purchaseTypeOptions = [];
    formOptions.warehousTypeOptions = [];
    formOptions.warehouseOptions = [];
  }

  if (props.purchaseDetail && gridApi.grid) {
    gridApi.grid.loadData((props.purchaseDetail.supplyList || []) as any[]);
  }
});
</script>

<template>
  <Page class="!mt-0">
    <div class="flex h-full flex-col">
      <Form />
      <div class="min-h-0 flex-1">
        <Grid />
      </div>
    </div>
  </Page>
</template>
