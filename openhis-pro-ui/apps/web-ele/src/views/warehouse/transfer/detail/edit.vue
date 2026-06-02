<script setup lang="ts">
import type { ComputedRef } from 'vue';

import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { InventoryItemDetail } from '#/api/core/common';
import type {
  CreateTransferRequestParams,
  InitWarehouseTransferOptionsResponse,
  TransferDetailResponse,
  TransferDetailSupplyItem,
  UpdateTransferRequestParams,
  UpdateTransferSupplyItem,
} from '#/api/warehouse/transfer';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getWarehouseByType } from '#/api/warehouse/common';
import {
  createTransferRequest,
  findTransferByBusNo,
  initTransferRequestOptions,
  updateTransferRequest,
} from '#/api/warehouse/transfer';
import { useRequest } from '#/hooks/use-request';

import { calcTotalPrice, formatQuantityByUnit } from '../../util';
import { validRules as transferValidRules } from './util';

interface TransferItemRow {
  id?: string;
  name?: string;
  supplierId?: string;
  itemId: string;
  totalVolume: string;
  manufacturerId?: string;
  manufacturerText?: string;
  itemTable?: string;
  sourceInventoryItemId?: string;
  productInfo?: Partial<InventoryItemDetail>;
  unitCode: string;
  isUnit?: boolean;
  itemQuantity?: number;
  price: number;
  totalPrice: number;
  lotNumber: string;
  traceNo: string;
  startTime: string;
  endTime: string;
  remark: string;
  stockQuantity?: string;
  unitList?: ApiOptionItem[];
}

const props = withDefaults(
  defineProps<{
    isInDialog?: boolean;
    state?: 'edit' | 'view';
    transferDetail?: null | TransferDetailResponse;
  }>(),
  { transferDetail: undefined, isInDialog: false },
);

const emit = defineEmits<{
  success: [];
}>();

const isViewMode = ref(false);
const localTransferDetail = ref<null | TransferDetailResponse>(null);
const effectiveTransferDetail = computed<null | TransferDetailResponse>(
  () => props.transferDetail ?? localTransferDetail.value,
);

const formOptions = reactive<{
  categoryOptions: ApiOptionItem[];
  practitionerOptions: ApiOptionItem[];
  purposeWarehouseOptions: ApiOptionItem[];
  sourceWarehouseOptions: ApiOptionItem[];
  warehouseTypeOptions: ApiOptionItem[];
}>({
  practitionerOptions: [],
  categoryOptions: [],
  warehouseTypeOptions: [],
  sourceWarehouseOptions: [],
  purposeWarehouseOptions: [],
});

type TransferFormValues = {
  applicantId?: string;
  categoryEnum?: string;
  purposeLocationId?: string;
  purposeTypeEnum?: string;
  sourceLocationId?: string;
  sourceTypeEnum?: string;
};

const [Form, formApi] = useVbenForm(
  reactive({
    schema: computed(() => [
      {
        component: 'Select',
        fieldName: 'applicantId',
        label: '制单人',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.practitionerOptions,
        },
      },
      {
        component: 'Select',
        fieldName: 'categoryEnum',
        label: '调拨类型',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.categoryOptions,
        },
      },
      {
        component: 'Select',
        fieldName: 'sourceTypeEnum',
        label: '调出仓库类型',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.warehouseTypeOptions,
          async onChange(value: string) {
            try {
              if (!value) {
                formOptions.sourceWarehouseOptions = [];
                await formApi.setFieldValue('sourceLocationId', undefined);
                return;
              }
              const res = await getWarehouseByType(value);
              formOptions.sourceWarehouseOptions = res?.data || [];
              await formApi.setFieldValue('sourceLocationId', undefined);
            } catch {
              formOptions.sourceWarehouseOptions = [];
              await formApi.setFieldValue('sourceLocationId', undefined);
            }
          },
        },
      },
      {
        component: 'Select',
        fieldName: 'sourceLocationId',
        label: '调出仓库',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.sourceWarehouseOptions,
        },
      },
      {
        component: 'Select',
        fieldName: 'purposeTypeEnum',
        label: '调入仓库类型',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.warehouseTypeOptions,
          async onChange(value: string) {
            try {
              if (!value) {
                formOptions.purposeWarehouseOptions = [];
                await formApi.setFieldValue('purposeLocationId', undefined);
                return;
              }
              const res = await getWarehouseByType(value);
              formOptions.purposeWarehouseOptions = res?.data || [];
              await formApi.setFieldValue('purposeLocationId', undefined);
            } catch {
              formOptions.purposeWarehouseOptions = [];
              await formApi.setFieldValue('purposeLocationId', undefined);
            }
          },
        },
      },
      {
        component: 'Select',
        fieldName: 'purposeLocationId',
        label: '调入仓库',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.purposeWarehouseOptions,
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
    type: 'checkbox',
    width: 50,
    fixed: 'left',
    field: 'checkbox',
  },
  {
    type: 'seq',
    title: '序号',
    width: 70,
    fixed: 'left',
    field: 'seq',
  },
  {
    title: '项目名称',
    field: 'productInfo',
    width: 220,
    editRender: {
      name: 'WarehouseProductSelectTable',
      props: {
        query: async () => {
          const values = await formApi.getValues();
          return {
            drugType: values.categoryEnum,
            locationId: values.sourceLocationId,
          };
        },
        onSelected: (product: InventoryItemDetail, row: any) => {
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
            manufacturerId: product.manufacturer,
            unitCode: product.unitCode,
            price: product.price,
            lotNumber: product.lotNumber,
            startTime: product.productionDate,
            endTime: product.expirationDate,
            itemQuantity: 0,
            stockQuantity: formatQuantityByUnit(
              product.totalQuantity || 0,
              product.partPercent ?? 1,
              product.unitCode_dictText ?? '',
              product.minUnitCode_dictText ?? '',
              true,
            ),
            unitList,
            isUnit: true,
          });
        },
      },
    },
  },
  {
    title: '规格',
    field: 'totalVolume',
    width: 160,
  },
  {
    title: '计量单位',
    field: 'unitCode',
    width: 120,
    editRender: {
      name: 'UnitSelect',
      props: {
        onChange: (val: any, row: any) => {
          row.isUnit = val === row.productInfo.unitCode;
          const p = row.productInfo as InventoryItemDetail | undefined;
          if (p && p.itemQuantity !== null) {
            row.stockQuantity = formatQuantityByUnit(
              p.itemQuantity,
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
  {
    title: '库存数量',
    field: 'stockQuantity',
    width: 180,
  },
  {
    title: '调拨数量',
    field: 'itemQuantity',
    width: 280,
    editRender: {
      name: 'UnitQuantityInput',
      props: {
        onChange: (val: number, row: TransferItemRow) => {
          if (!row) return;
          const q = val || 0;
          row.itemQuantity = q;
          if (row.price !== null) {
            row.totalPrice = calcTotalPrice(q, row.price);
          }
        },
      },
    },
  },
  {
    title: '单价',
    field: 'price',
    width: 200,
  },
  {
    title: '金额',
    field: 'totalPrice',
    width: 200,
  },
  {
    title: '产品批号',
    field: 'lotNumber',
    width: 160,
  },
  {
    title: '生产日期',
    field: 'startTime',
    width: 200,
  },
  {
    title: '有效期至',
    field: 'endTime',
    width: 200,
  },
  {
    title: '追溯码',
    field: 'traceNo',
    width: 160,
  },
  {
    title: '备注',
    field: 'remark',
    width: 180,
    editRender: { name: 'ElInput' },
  },
  {
    title: '操作',
    field: 'action',
    width: 100,
    align: 'center',
    fixed: 'right',
    visible: props.state === 'edit',
    slots: { default: 'action' },
  },
];

const gridOptions: VxeGridProps<TransferItemRow> = reactive({
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
  editRules: transferValidRules,
  footerMethod: ({ data }) => {
    const totalPrice = (data || []).reduce(
      (sum, row) => sum + (Number((row as any).totalPrice) || 0),
      0,
    );
    return [['合计', `${totalPrice} 元`]];
  },
});

const [Grid, gridApi] = useVbenVxeGrid<TransferItemRow>({ gridOptions });

function mapDetailListToRows(list: TransferDetailSupplyItem[]): TransferItemRow[] {
  if (!Array.isArray(list)) return [];
  return list.map((item: TransferDetailSupplyItem): TransferItemRow => {
    const unitList: ApiOptionItem[] = [
      { label: item.unitCode_dictText || '', value: item.unitCode || '' },
      ...(item.minUnitCode_dictText && item.minUnitCode
        ? [{ label: item.minUnitCode_dictText, value: item.minUnitCode }]
        : []),
    ];
    return {
      ...item,
      name: item.name,
      itemQuantity: item.itemQuantity,
      manufacturerId: item.manufacturerText,
      startTime: item.startTime ?? '',
      endTime: item.endTime ?? '',
      productInfo: item,
      unitList,
      isUnit: item.unitCode === item.defUnitCode,
      stockQuantity: formatQuantityByUnit(
        item.totalQuantity,
        item.partPercent ?? 1,
        item.unitCode_dictText ?? '',
        item.minUnitCode_dictText ?? '',
        true,
      ),
    };
  });
}

/** 将详情赋到表单和表格（含拉取仓库选项） */
async function applyDetailToFormAndGrid(detail: TransferDetailResponse) {
  formApi.setValues({
    applicantId: detail.applicantId,
    categoryEnum: detail.categoryEnum,
    sourceTypeEnum: detail.sourceTypeEnum,
    sourceLocationId: detail.sourceLocationId,
    purposeTypeEnum: detail.purposeTypeEnum,
    purposeLocationId: detail.purposeLocationId,
  });
  if (detail.sourceTypeEnum) {
    try {
      const res = await getWarehouseByType(String(detail.sourceTypeEnum));
      formOptions.sourceWarehouseOptions = res?.data || [];
    } catch {
      formOptions.sourceWarehouseOptions = [];
    }
  }
  if (detail.purposeTypeEnum) {
    try {
      const res = await getWarehouseByType(String(detail.purposeTypeEnum));
      formOptions.purposeWarehouseOptions = res?.data || [];
    } catch {
      formOptions.purposeWarehouseOptions = [];
    }
  }
  const rows = mapDetailListToRows(detail.supplyList ?? []);
  gridApi.grid.loadData(rows);
}

/** 保存后调 find 拉取最新详情并赋到表单/表格（item 会带上 id） */
async function refreshDetailAfterSave(busNo: string) {
  const res = await findTransferByBusNo({ busNo });
  const detail = (res as any)?.data ?? res;
  if (detail?.supplyBusNo) {
    localTransferDetail.value = detail;
    await applyDetailToFormAndGrid(detail);
  }
}

watch([() => props.transferDetail, () => props.state], async ([detail]) => {
  const disabled = props.state === 'view';
  isViewMode.value = disabled;
  formApi.setState({ commonConfig: { disabled } });
  gridApi.setGridOptions({
    ...gridOptions,
    columns: columns.map((col) =>
      col.field === 'action' ? { ...col, visible: props.state === 'edit' } : col,
    ),
    editConfig: {
      ...gridOptions.editConfig,
      enabled: !disabled,
    },
  });
  localTransferDetail.value = null;
  if (!detail) {
    await formApi.resetForm();
    gridApi.grid.loadData([]);
    return;
  }
  await applyDetailToFormAndGrid(detail);
});

const addItem = async () => {
  const formValid = await formApi.validate();
  if (!formValid.valid) {
    return;
  }
  const $grid = gridApi.grid;
  if ($grid) {
    const record = {
      name: '',
      itemId: '',
      totalVolume: '',
      manufacturerId: '',
      unitCode: '',
      quantity: 0,
      itemQuantity: 0,
      price: 0,
      totalPrice: 0,
      lotNumber: '',
      traceNo: '',
      startTime: '',
      endTime: '',
      remark: '',
    } as TransferItemRow;
    const { row } = await $grid.insertAt(record, null);
    await $grid.$nextTick();
    await $grid.scrollToRow(row);
    await $grid.setEditCell(row, 'productInfo');
  }
};

const deleteItem = async (row: 'select' | TransferItemRow) => {
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

const lastAction = ref<'save' | 'saveAndNew' | 'saveAndSubmit'>('save');

const onSaveSuccess = async () => {
  if (lastAction.value === 'saveAndNew') {
    ElMessage.success('保存成功，已准备新增下一条');
    await formApi.resetForm();
    gridApi.grid.loadData([]);
    localTransferDetail.value = null;
    return;
  }
  if (lastAction.value === 'saveAndSubmit') {
    ElMessage.success('保存并提交审核成功');
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

function extractBusNo(res: any): string {
  const first = Array.isArray(res) ? res[0] : res;
  const busNo = first?.busNo ?? first?.supplyBusNo ?? first?.value;
  return typeof busNo === 'string' ? busNo : '';
}

const { run: runCreateTransfer, loading: createLoading } = useRequest(createTransferRequest, {
  manual: true,
  onSuccess: async (res: any) => {
    const busNo = extractBusNo(res);
    if (busNo) await refreshDetailAfterSave(busNo);
    await onSaveSuccess();
  },
});

const { run: runUpdateTransfer, loading: updateLoading } = useRequest(updateTransferRequest, {
  manual: true,
  onSuccess: async () => {
    const busNo = effectiveTransferDetail.value?.supplyBusNo;
    if (busNo) await refreshDetailAfterSave(busNo);
    await onSaveSuccess();
  },
});

function buildUpdateSupplyList(
  fullData: TransferItemRow[],
  originalList: TransferDetailSupplyItem[],
): UpdateTransferSupplyItem[] {
  return fullData.map((row, i) => {
    const orig = originalList[i] || ({} as TransferDetailSupplyItem);
    return {
      id: row.id ?? orig.id ?? '',
      itemId: row.itemId ?? orig.itemId ?? '',
      itemQuantity: row.itemQuantity ?? orig.itemQuantity ?? 0,
      totalPrice: row.totalPrice ?? orig.totalPrice ?? 0,
      traceNo: row.traceNo ?? orig.traceNo ?? '',
      itemTable: row.itemTable ?? orig.itemTable ?? '',
      remark: row.remark ?? orig.remark ?? '',
      lotNumber: row.lotNumber ?? orig.lotNumber ?? '',
      startTime: row.startTime ?? orig.startTime ?? '',
      price: row.price ?? orig.price ?? 0,
      unitCode: row.unitCode ?? orig.unitCode ?? '',
      name: row.name ?? orig.name ?? '',
      endTime: row.endTime ?? orig.endTime ?? '',
    };
  });
}

const doSave = async (isSubmit = false) => {
  const { valid } = await formApi.validate();
  if (!valid) return;

  const $grid = gridApi.grid;
  const validGrid = await $grid.validate(true);
  if (validGrid) {
    return;
  }

  const fullData = $grid.getFullData() as TransferItemRow[];
  if (fullData.length === 0) {
    ElMessage.warning('请先添加调拨明细');
    return;
  }

  const formValues = (await formApi.getValues()) as TransferFormValues;
  const detailForSave = effectiveTransferDetail.value;
  const isEdit = !!detailForSave?.supplyBusNo;

  if (isEdit) {
    const detail = detailForSave!;
    const originalList: TransferDetailSupplyItem[] = Array.isArray(detail?.supplyList)
      ? detail?.supplyList
      : [];
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
    const params: UpdateTransferRequestParams = {
      supplyBusNo: detail?.supplyBusNo ?? '',
      categoryEnum: String(formValues.categoryEnum ?? ''),
      sourceLocationType: String(formValues.sourceTypeEnum ?? ''),
      purposeLocationId: String(formValues.purposeLocationId ?? ''),
      sourceLocationId: String(formValues.sourceLocationId ?? ''),
      purposeTypeEnum: String(formValues.purposeTypeEnum ?? ''),
      applyTime: detail?.applyTime ?? '',
      deleteIds,
      supplyList: buildUpdateSupplyList(fullData, originalList),
      isSubmit,
    };
    await runUpdateTransfer(params);
  } else {
    const params: CreateTransferRequestParams = {
      categoryEnum: String(formValues.categoryEnum || ''),
      sourceTypeEnum: String(formValues.sourceTypeEnum || ''),
      purposeLocationId: String(formValues.purposeLocationId || ''),
      applicantId: String(formValues.applicantId || ''),
      sourceLocationId: String(formValues.sourceLocationId || ''),
      purposeTypeEnum: String(formValues.purposeTypeEnum || ''),
      isSubmit,
      items: fullData.map((item) => ({
        totalVolume: item.totalVolume || '',
        itemQuantity: item.itemQuantity ?? 0,
        totalPrice: item.totalPrice || 0,
        traceNo: item.traceNo || '',
        itemTable: item.itemTable || '',
        manufacturerId: item.manufacturerId || '',
        remark: item.remark || '',
        lotNumber: item.lotNumber || '',
        sourceInventoryItemId: item.sourceInventoryItemId || '',
        name: item.name || '',
        itemId: item.itemId || '',
        price: item.price || 0,
        unitCode: item.unitCode || '',
        startTime: item.startTime || '',
        endTime: item.endTime || '',
        supplierId: item.supplierId || '',
      })),
    };
    await runCreateTransfer(params);
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

defineExpose<{
  save: () => Promise<void>;
  saveAndNew: () => Promise<void>;
  saveAndSubmit: () => Promise<void>;
  saveLoading: ComputedRef<boolean>;
}>({
  save: handleSave,
  saveAndNew: handleSaveAndNew,
  saveAndSubmit: handleSaveAndSubmit,
  saveLoading: computed(() => !!(createLoading.value || updateLoading.value)),
});

onMounted(async () => {
  try {
    const res = (await initTransferRequestOptions()) as InitWarehouseTransferOptionsResponse;
    formOptions.practitionerOptions = res.practitionerOptions || [];
    formOptions.categoryOptions = res.categoryOptions || [];
    formOptions.warehouseTypeOptions = res.warehouseTypeOptions || [];
  } catch {
    formOptions.practitionerOptions = [];
    formOptions.categoryOptions = [];
    formOptions.warehouseTypeOptions = [];
  }
  isViewMode.value = props.state === 'view';
});
</script>

<template>
  <Page :class="!!state ? '!mt-0' : 'mt-2'">
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
    <template #footer v-if="props.state !== 'view' && !isInDialog">
      <ElButton type="primary" :loading="createLoading || updateLoading" @click="handleSave">
        保存
      </ElButton>
      <ElButton
        v-if="!props.transferDetail?.supplyBusNo"
        type="primary"
        :loading="createLoading || updateLoading"
        @click="handleSaveAndNew"
      >
        保存并新增
      </ElButton>
      <ElButton :loading="createLoading || updateLoading" @click="handleSaveAndSubmit">
        保存并提交审核
      </ElButton>
    </template>
  </Page>
</template>
