<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { CreatePurchaseParams, UpdatePurchaseParams } from '#/api/warehouse/purchase';
import type { ApiOptionItem } from '#/types/api-response';
import type { ProductDetail } from '#/types/common';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { useTabs } from '@vben/hooks';
import { Trash2 } from '@vben/icons';

import { dayjs, ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { createPurchase, initPharmacyWarehouseSuplyApi, updatePurchase } from '#/api';
import { getWarehouseByType } from '#/api/warehouse/common';
import { getPurchaseById } from '#/api/warehouse/purchase';
import { useRequest } from '#/hooks/use-request';

import {
  calcAllTotalPrice,
  calcPurchasePrice,
  calcTotalPrice,
  defaultValues,
  validRules,
} from './util';

type PurchaseDetailForEdit = (CreatePurchaseParams | Record<string, any>) & {
  supplyBusNo?: string;
  supplyList?: any[];
};

const props = withDefaults(
  defineProps<{
    isInDialog?: boolean;
    purchaseDetail?: PurchaseDetailForEdit;
    state?: 'edit' | 'view';
  }>(),
  {
    purchaseDetail: undefined,
    state: 'edit',
    isInDialog: false,
  },
);

const emit = defineEmits<{
  success: [];
}>();

const isViewMode = ref(false);
const isEditMode = ref(false);

const router = useRouter();
const { closeCurrentTab } = useTabs();

const localPurchaseDetail = ref<PurchaseDetailForEdit | undefined>(undefined);
const effectivePurchaseDetail = computed<PurchaseDetailForEdit | undefined>(
  () => props.purchaseDetail ?? localPurchaseDetail.value,
);

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
          options: formOptions.supplierListOptions.map((item) => ({
            label: item.label,
            value: item.value,
          })),
        },
      },
      {
        component: 'DatePicker',
        fieldName: 'applyTime',
        label: '制单时间',
        componentProps: {
          valueFormat: 'YYYY-MM-DD HH:mm:ss',
          format: 'YYYY-MM-DD HH:mm:ss',
          placeholder: '请选择制单时间',
        },
        defaultValue: dayjs().format('YYYY-MM-DD HH:mm:ss'),
      },
      {
        component: 'Select',
        fieldName: 'categoryEnum',
        label: '药品类型',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.purchaseTypeOptions.map((item) => ({
            label: item.label,
            value: item.value,
          })),
          beforeSelect: async () => {
            const $grid = gridApi.grid;
            if ($grid) {
              const fullData = $grid.getFullData();
              if (fullData.length === 0) {
                return true;
              }
            }
            const result = await ElMessageBox.confirm(
              '切换药品类型会清空当前添加的药品，是否继续？',
              '提示',
              {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning',
              },
            );
            if (result) {
              gridApi.grid.loadData([]);
            }
            return result;
          },
        },
      },
      {
        component: 'Select',
        fieldName: 'locationTypeEnum',
        label: '仓库类型',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.warehousTypeOptions.map((item) => ({
            label: item.label,
            value: item.value,
          })),
          async onChange(value: string) {
            try {
              if (!value) {
                formOptions.warehouseOptions = [];
                await formApi.setFieldValue('locationId', undefined);
                return;
              }
              const response = await getWarehouseByType(value);
              formOptions.warehouseOptions = response?.data;
              await formApi.setFieldValue('locationId', undefined);
            } catch {
              formOptions.warehouseOptions = [];
              await formApi.setFieldValue('locationId', undefined);
            }
          },
        },
      },
      {
        component: 'Select',
        fieldName: 'locationId',
        label: '仓库',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.warehouseOptions.map((item) => ({
            label: item.label,
            value: item.value,
          })),
        },
      },
    ]),
    commonConfig: {
      componentProps: {
        style: { width: '220px' },
      },
      disabled: props.state === 'view',
    },
    wrapperClass: 'flex flex-row flex-wrap',
    showDefaultActions: false,
  }),
);

const columns: VxeGridPropTypes.Column[] = [
  {
    type: 'checkbox',
    width: 50,
    fixed: 'left',
    field: 'checkbox',
  },
  {
    title: '序号',
    type: 'seq',
    width: 70,
    fixed: 'left',
    field: 'quantity',
  },
  {
    title: '项目',
    field: 'productInfo',
    width: 220,
    editRender: {
      name: 'ProductSelectTable',
      props: {
        query: async () => {
          const values = await formApi.getValues();
          return {
            categoryEnum: values.categoryEnum,
          };
        },
        onSelected: (product: ProductDetail, row: ProductDetail) => {
          row.totalVolume = product.totalVolume;
          row.manufacturerText = product.manufacturer;
          row.unitCode = product.unitCode;
          row.price = calcPurchasePrice(product.partPercent ?? 1, product.price);
          row.startTime = product.startTime;
          row.endTime = product.endTime;
          row.totalSalePrice = 0;
          const unitList: ApiOptionItem[] = [
            {
              label: product.unitCodeDictText || '',
              value: product.unitCode || '',
            },
          ];
          if (product.minUnitCodeDictText && product.minUnitCode) {
            unitList.push({
              label: product.minUnitCodeDictText,
              value: product.minUnitCode,
            });
          }
          row.unitList = unitList;
          row.partPercent = product.partPercent ?? 1;
          row.isUnit = true; // 是否为大单位
        },
      },
    },
    fixed: 'left',
  },
  {
    title: '规格',
    field: 'totalVolume',
    width: 160,
    editRender: { name: 'ElInput' },
  },
  {
    title: '厂家/产地',
    field: 'manufacturerText',
    width: 180,
    editRender: { name: 'ElInput' },
  },
  {
    title: '计量单位',
    field: 'unitCode',
    width: 140,
    editRender: {
      name: 'UnitSelect',
      props: {
        onChange: (val: string, row: ProductDetail) => {
          const product = row.productInfo as ProductDetail;
          if (product) {
            row.isUnit = val === product.unitCode;
          }
        },
      },
    },
  },
  {
    title: '采购数量',
    field: 'itemQuantity',
    width: 280,
    editRender: {
      name: 'UnitQuantityInput',
      props: {
        width: '100%',
        onChange: (val: number, row: ProductDetail) => {
          if (row?.price) {
            row.totalSalePrice = calcTotalPrice(val, row.price);
            gridApi.grid?.updateFooter();
          }
        },
      },
    },
  },

  {
    title: '采购单价（包装单位）',
    field: 'price',
    width: 200,
    editRender: {
      name: 'ElInputNumber',
      props: {
        controlsPosition: 'right',
        width: '100%',
      },
      events: {
        change(cellParams, eventParams) {
          const { row } = cellParams;
          row.totalSalePrice = calcTotalPrice(eventParams, row.itemQuantity);
          gridApi.grid?.updateFooter();
        },
      },
    },
    formatter: (params: { cellValue: number }) => `${params.cellValue} 元`,
  },
  {
    title: '合计金额',
    field: 'totalSalePrice',
    width: 160,
    formatter: (params: { cellValue: number }) => `${params.cellValue} 元`,
  },
  {
    title: '产品批号',
    field: 'lotNumber',
    width: 160,
    editRender: { name: 'ElInput' },
  },
  {
    title: '生产日期',
    field: 'startTime',
    width: 220,
    editRender: { name: 'ElDatePicker', props: { class: '!w-[200px]' } },
  },
  {
    title: '有效期至',
    field: 'endTime',
    width: 220,
    editRender: { name: 'ElDatePicker', props: { class: '!w-[200px]' } },
  },
  {
    title: '发票号',
    field: 'invoiceNo',
    width: 160,
    editRender: { name: 'ElInput' },
  },
  {
    title: '追溯码',
    field: 'traceNo',
    width: 160,
    editRender: { name: 'ElInput' },
  },
  {
    title: '备注',
    field: 'remake',
    width: 160,
    editRender: { name: 'ElInput' },
  },
  {
    slots: { default: 'action' },
    title: '操作',
    align: 'center',
    fixed: 'right',
    field: 'action',
    minWidth: 100,
    visible: props.state === 'edit',
  },
];

const gridOptions: VxeGridProps<ProductDetail> = reactive({
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
  pagerConfig: {
    enabled: false,
  },
  editRules: validRules,
  mergeFooterItems: [
    { row: 0, col: 0, rowspan: 1, colspan: 2 },
    { row: 0, col: 3, rowspan: 1, colspan: 14 },
  ],
  footerMethod: ({ data }) => {
    const totalPrice = calcAllTotalPrice(data || []);
    return [['合计', `${totalPrice} 元`]];
  },
});

const [Grid, gridApi] = useVbenVxeGrid({ gridOptions });

const addItem = async () => {
  const formValid = await formApi.validate();
  if (!formValid.valid) {
    return;
  }
  const $grid = gridApi.grid;
  if ($grid) {
    const record = { ...defaultValues };
    const { row } = await $grid.insertAt(record, null);
    await $grid.$nextTick();
    await $grid.scrollToRow(row);
    await $grid.setEditCell(row, 'productInfo');
  }
};

const deleteItem = async (row: 'select' | ProductDetail) => {
  const $grid = gridApi.grid;
  const rows = $grid.getCheckboxRecords();
  if (row === 'select' && rows.length === 0) {
    ElMessage.warning('请选择要删除的药品');
    return;
  }
  if (row !== 'select' && !row?.productInfo) {
    await $grid.remove(row);
    return;
  }
  ElMessageBox.confirm('确定删除所选药品吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    await $grid.remove(row === 'select' ? rows : row);
  });
};

async function getPurchaseinventoryTypeList() {
  try {
    const response = await initPharmacyWarehouseSuplyApi();
    const data = response || {};
    formOptions.supplierListOptions = data.supplierOptions || [];
    formOptions.practitionerListOptions = data.practitionerOptions || [];
    formOptions.purchaseTypeOptions = data.categoryOptions || [];
    formOptions.warehousTypeOptions = data.warehouseTypeOptions || [];
    formOptions.warehouseOptions = data.warehouseOptions || [];
  } catch {
    formOptions.supplierListOptions = [];
    formOptions.practitionerListOptions = [];
    formOptions.purchaseTypeOptions = [];
    formOptions.warehousTypeOptions = [];
    formOptions.warehouseOptions = [];
  }
}

const lastAction = ref<'save' | 'saveAndNew' | 'saveAndSubmit'>('save');

const onSaveSuccess = async () => {
  if (lastAction.value === 'saveAndNew') {
    ElMessage.success('保存成功，已准备新增下一条');
    await formApi.resetForm();
    gridApi.grid.loadData([]);
    localPurchaseDetail.value = undefined;
    return;
  }
  if (lastAction.value === 'saveAndSubmit') {
    ElMessage.success('保存并提交审核成功');
    if (!props.state) {
      await closeCurrentTab();
      await router.push('/warehouse/purchase');
      await nextTick();
    }
    if (props.isInDialog) {
      emit('success');
    }
    return;
  }
  ElMessage.success('保存成功');
  if (props.isInDialog) {
    emit('success');
  }
};

/** 将详情赋到表单和表格（含拉取仓库选项） */
function mapSupplyListToRows(list: any[] | null | undefined): any[] {
  if (!Array.isArray(list)) return [];
  return list.map((item) => {
    const row: any = {
      ...item,
    };

    row.productInfo = row.productInfo || {
      productDefinitionId: item.itemId,
      id: item.itemId,
      productName: item.name,
      unitCode: item.unitCode,
      unitCodeDictText: item.unitCodeDictText || item.unitCode_dictText,
      totalVolume: item.totalVolume,
      manufacturer: item.manufacturerText || item.manufacturer,
      partPercent: item.partPercent,
      price: item.price,
      startTime: item.startTime,
      endTime: item.endTime,
      minUnitCode: item.minUnitCode,
      minUnitCodeDictText: item.minUnitCodeDictText,
    };

    row.name = row.name ?? row.productInfo.productName ?? '';
    row.totalVolume = row.totalVolume ?? '';
    row.manufacturerText = row.manufacturerText || item.manufacturer;

    if (!row.unitList) {
      const unitList: ApiOptionItem[] = [];
      if (row.productInfo.unitCodeDictText || row.productInfo.unitCode) {
        unitList.push({
          label: row.productInfo.unitCodeDictText || '',
          value: row.productInfo.unitCode || '',
        });
      }
      if (row.productInfo.minUnitCodeDictText && row.productInfo.minUnitCode) {
        unitList.push({
          label: row.productInfo.minUnitCodeDictText,
          value: row.productInfo.minUnitCode,
        });
      }
      row.unitList = unitList;
    }

    const quantity = Number(row.itemQuantity ?? 0);
    const price = Number(row.price ?? 0);
    const totalSalePrice =
      row.totalSalePrice !== undefined && row.totalSalePrice !== null
        ? Number(row.totalSalePrice)
        : calcTotalPrice(quantity, price);

    row.itemQuantity = quantity;
    row.price = price;
    row.totalSalePrice = totalSalePrice;

    return row;
  });
}

async function applyPurchaseDetailToFormAndGrid(detail: PurchaseDetailForEdit) {
  formApi.setValues(detail);
  if (detail.locationTypeEnum) {
    try {
      const response = await getWarehouseByType(String(detail.locationTypeEnum));
      const data = (response?.data || []) as ApiOptionItem[];
      formOptions.warehouseOptions = data;
    } catch {
      formOptions.warehouseOptions = [];
    }
  }
  const rows = mapSupplyListToRows(detail.supplyList ?? []);
  gridApi.grid.loadData(rows);
}

const { run: runGetPurchaseDetail, loading: getPurchaseDetailLoading } = useRequest(
  getPurchaseById,
  {
    manual: true,
    onSuccess: async (res: any) => {
      const detail = (res as any)?.data ?? res;
      if (detail?.supplyBusNo) {
        localPurchaseDetail.value = detail as PurchaseDetailForEdit;
        await applyPurchaseDetailToFormAndGrid(detail);
      }
    },
  },
);

async function refreshDetailAfterSave(busNo: string) {
  if (!busNo) return;
  await runGetPurchaseDetail({ busNo });
}

const { run: runCreatePurchase, loading: createPurchaseLoading } = useRequest(createPurchase, {
  manual: true,
  onSuccess: async (res) => {
    const busNo = res?.busNo ?? '';
    if (busNo) await refreshDetailAfterSave(busNo);
    await onSaveSuccess();
  },
});

const { run: runUpdatePurchase, loading: updatePurchaseLoading } = useRequest(updatePurchase, {
  manual: true,
  onSuccess: async () => {
    const busNo = effectivePurchaseDetail.value?.supplyBusNo;
    if (busNo) await refreshDetailAfterSave(busNo);
    await onSaveSuccess();
  },
});

function buildUpdateSupplyList(
  fullData: any[],
  originalList: any[],
): UpdatePurchaseParams['supplyList'] {
  return fullData.map((row, i) => {
    const orig = originalList[i] || {};
    const productId =
      row.productInfo?.productDefinitionId ?? row.productInfo?.id ?? row.itemId ?? orig.itemId;
    return {
      ...orig,
      id: orig.id ?? '',
      busNo: orig.busNo ?? '',
      itemId: String(productId),
      name: row.name ?? row.productInfo?.productName ?? orig.name ?? '',
      unitCode: row.unitCode ?? orig.unitCode ?? '',
      itemQuantity: row.itemQuantity ?? orig.itemQuantity ?? 0,
      price: row.price ?? orig.price ?? 0,
      salePrice: row.price ?? orig.salePrice ?? orig.price ?? 0,
      totalSalePrice: row.totalSalePrice ?? orig.totalSalePrice ?? 0,
      totalPrice: row.totalPrice ?? orig.totalPrice ?? 0,
      partPrice: orig.partPrice ?? row.price ?? 0,
      partSalePrice: orig.partSalePrice ?? row.price ?? 0,
      lotNumber: row.lotNumber ?? orig.lotNumber ?? '',
      traceNo: row.traceNo ?? orig.traceNo ?? '',
      invoiceNo: row.invoiceNo ?? orig.invoiceNo ?? '',
      startTime: row.startTime ?? orig.startTime ?? '',
      endTime: row.endTime ?? orig.endTime ?? '',
      remake: row.remake ?? orig.remake ?? '',
      itemTable: row.itemTable ?? orig.itemTable ?? '',
      locationId: orig.locationId ?? '',
      purposeTypeEnum: orig.purposeTypeEnum ?? '',
      packagingConditionEnum: orig.packagingConditionEnum ?? '',
      statusEnum: orig.statusEnum ?? '',
      approverId: orig.approverId ?? '',
      approvalTime: orig.approvalTime ?? '',
    };
  });
}

const doSave = async (review?: boolean) => {
  const formValid = await formApi.validate();
  if (!formValid.valid) {
    return;
  }
  const $grid = gridApi.grid;
  const valid = await $grid.validate(true);
  if (valid) {
    return;
  }
  const fullData = $grid.getFullData();
  const fromData = (await formApi.getValues()) as Record<string, any>;

  const detail = effectivePurchaseDetail.value;
  const isEdit = !!detail?.supplyBusNo;
  if (isEdit) {
    const originalSupplyList: any[] = Array.isArray(detail?.supplyList) ? detail?.supplyList : [];
    const remainingIds = new Set(
      fullData.map((row: any) => row.id).filter((id: any) => id !== null && id !== ''),
    );
    const deleteIds = originalSupplyList
      .map((item: any) => item.id)
      .filter((id: any) => id !== null && id !== '' && !remainingIds.has(id));
    const params: UpdatePurchaseParams = {
      supplyBusNo: detail?.supplyBusNo ?? '',
      supplierId: fromData.supplierId ?? '',
      practitionerId: fromData.practitionerId ?? '',
      applyTime: fromData.applyTime ?? '',
      locationTypeEnum: String(fromData.locationTypeEnum ?? ''),
      locationId: String(fromData.locationId ?? ''),
      categoryEnum: String(fromData.categoryEnum ?? ''),
      deleteIds,
      isSubmit: review,
      supplyList: buildUpdateSupplyList(fullData, originalSupplyList),
    };
    await runUpdatePurchase(params);
  } else {
    const params: CreatePurchaseParams = {
      ...fromData,
      isSubmit: review,
      supplyList: fullData.map((item: any) => ({
        ...item,
        itemId: String(
          item.productInfo?.productDefinitionId ?? item.productInfo?.id ?? item.itemId ?? '',
        ),
      })),
    };
    await runCreatePurchase(params);
  }
};

const handleSave = async () => {
  lastAction.value = 'save';
  await doSave(false);
};

const handleSaveAndNew = async () => {
  lastAction.value = 'saveAndNew';
  await doSave(false);
};

const handleSaveAndSubmit = async () => {
  lastAction.value = 'saveAndSubmit';
  await doSave(true);
};

watch(props, async (newVal) => {
  localPurchaseDetail.value = undefined;
  if (!props.state || !newVal.purchaseDetail) return;
  const disabled = props.state === 'view';
  isViewMode.value = props.state === 'view';
  isEditMode.value = props.state === 'edit';

  gridApi.setGridOptions({
    ...(gridOptions as any),
    columns: columns.map((col) =>
      col.field === 'action' ? { ...col, visible: props.state === 'edit' } : col,
    ),
    editConfig: {
      ...gridOptions.editConfig,
      enabled: !disabled,
    },
  });
  formApi.setState({ commonConfig: { disabled } });
  await applyPurchaseDetailToFormAndGrid(newVal.purchaseDetail);
});

defineExpose<{
  save: () => Promise<void>;
  saveAndSubmit: () => Promise<void>;
}>({
  save: handleSave,
  saveAndSubmit: handleSaveAndSubmit,
});
onMounted(() => {
  getPurchaseinventoryTypeList();
});
</script>
<template>
  <Page :class="isInDialog ? '!mt-0' : 'mt-2'" :loading="getPurchaseDetailLoading">
    <div class="flex h-full flex-col">
      <Form />
      <div class="min-h-0 flex-1">
        <Grid>
          <template #toolbar-actions v-if="!isViewMode">
            <ElButton class="mr-2" type="primary" @click="addItem"> 添加药品 </ElButton>
            <ElButton type="danger" @click="deleteItem('select')"> 删除药品 </ElButton>
          </template>
          <template #action="{ row }" v-if="!isViewMode">
            <ElButton type="danger" @click="deleteItem(row)" text>
              <Trash2 class="size-4" /> 删除
            </ElButton>
          </template>
        </Grid>
      </div>
    </div>
    <template #footer v-if="state === 'edit' && !isInDialog">
      <ElButton
        type="primary"
        :loading="createPurchaseLoading || updatePurchaseLoading"
        @click="handleSave"
      >
        保存
      </ElButton>
      <ElButton
        type="primary"
        :loading="createPurchaseLoading || updatePurchaseLoading"
        @click="handleSaveAndNew"
      >
        保存并新增
      </ElButton>
      <ElButton
        :loading="createPurchaseLoading || updatePurchaseLoading"
        @click="handleSaveAndSubmit"
      >
        保存并提交审核
      </ElButton>
    </template>
  </Page>
</template>
