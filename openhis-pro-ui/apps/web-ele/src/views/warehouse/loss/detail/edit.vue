<script setup lang="ts">
import type { ComputedRef } from 'vue';

import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { InventoryItemDetail } from '#/api/core/common';
import type {
  CreateLossRequestParams,
  InitLossReportResponse,
  LossDetailItem,
  LossDetailResponse,
  LossRequestItem,
  UpdateLossRequestItem,
  UpdateLossRequestParams,
} from '#/api/warehouse/loss';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@zxplat/common-ui';

import { dayjs, ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getWarehouseByType } from '#/api/warehouse/common';
import {
  createLossRequestApi,
  findLossByBusNoApi,
  initLossReportApi,
  updateLossRequestApi,
} from '#/api/warehouse/loss';
import { useRequest } from '#/hooks/use-request';

import { calcTotalPrice, formatQuantityByUnit } from '../../util';
import { validRules as lossValidRules } from './util';

interface LossItemRow {
  id?: string;
  name?: string;
  itemId: string;
  itemTable?: string;
  supplierId?: string;
  supplierName?: string;
  productInfo?: Partial<InventoryItemDetail> & Record<string, any>;
  totalVolume: string;
  unitCode: string;
  isUnit?: boolean;
  unitList?: ApiOptionItem[];
  stockQuantity?: string;
  itemQuantity?: number;
  price: number;
  totalPrice: number;
  lotNumber: string;
  startTime: string;
  endTime: string;
  reason: string;
  traceNo: string;
  remark: string;
}

defineOptions({
  name: 'WarehouseLossDetail',
});

const props = withDefaults(
  defineProps<{
    isInDialog?: boolean;
    lossDetail?: LossDetailResponse;
    state?: 'edit' | 'view';
  }>(),
  { isInDialog: false, lossDetail: undefined },
);

const emit = defineEmits<{
  success: [];
}>();

const isViewMode = ref(false);
const localLossDetail = ref<LossDetailResponse | null>(null);
const effectiveLossDetail = computed<LossDetailResponse | null>(
  () => props.lossDetail ?? localLossDetail.value,
);

const formOptions = reactive<{
  categoryOptions: ApiOptionItem[];
  practitionerOptions: ApiOptionItem[];
  supplierOptions: ApiOptionItem[];
  warehouseOptions: ApiOptionItem[];
  warehouseTypeOptions: ApiOptionItem[];
}>({
  practitionerOptions: [],
  categoryOptions: [],
  supplierOptions: [],
  warehouseTypeOptions: [],
  warehouseOptions: [],
});

type LossFormValues = {
  applyTime?: string;
  categoryEnum?: string;
  locationId?: string;
  locationTypeEnum?: string;
  practitionerId?: string;
  supplierId?: string;
  supplyBusNo?: string;
};

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
          options: formOptions.practitionerOptions,
        },
      },
      {
        component: 'Select',
        fieldName: 'categoryEnum',
        label: '报损类型',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.categoryOptions,
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
          options: formOptions.warehouseTypeOptions,
          async onChange(value: string) {
            try {
              if (!value) {
                formOptions.warehouseOptions = [];
                await formApi.setFieldValue('locationId', undefined);
                return;
              }
              const res = await getWarehouseByType(value);
              formOptions.warehouseOptions = res?.data || [];
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
          options: formOptions.warehouseOptions,
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
          options: formOptions.supplierOptions,
        },
      },
      {
        component: 'DatePicker',
        fieldName: 'applyTime',
        label: '制单日期',
        rules: 'required',
        componentProps: {
          type: 'datetime',
          clearable: true,
        },
      },
    ]),
    commonConfig: {
      componentProps: {
        style: { width: '220px' },
      },
    },
    wrapperClass: 'flex flex-row flex-wrap',
    showDefaultActions: false,
  }),
);

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
          const values = (await formApi.getValues()) as LossFormValues;
          return {
            drugType: values.categoryEnum,
            locationId: values.locationId,
          };
        },
        onSelected: (product: InventoryItemDetail & Record<string, any>, row: any) => {
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
            itemTable: product.itemTable ?? product.itemTableName ?? '',
            totalVolume: product.volume,
            unitCode: product.unitCode,
            price: product.price,
            lotNumber: product.lotNumber,
            startTime: product.productionDate,
            endTime: product.expirationDate,
            supplierId: product.supplierId,
            supplierName: product.supplierName,
            itemQuantity: 0,
            reason: '',
            totalPrice: 0,
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
  { title: '规格', field: 'totalVolume', width: 160 },
  {
    title: '计量单位',
    field: 'unitCode',
    width: 120,
    editRender: {
      name: 'UnitSelect',
      props: {
        onChange: (val: any, row: any) => {
          const isUnit = val === row?.productInfo?.unitCode;
          if (row) {
            row.isUnit = isUnit ?? false;
            const p = row.productInfo as (InventoryItemDetail | undefined) & Record<string, any>;
            row.stockQuantity = formatQuantityByUnit(
              p.itemQuantity,
              p.partPercent ?? 1,
              p.unitCode_dictText ?? '',
              p.minUnitCode_dictText ?? '',
              isUnit,
            );
          }
        },
      },
    },
  },
  { title: '库存数量', field: 'stockQuantity', width: 180 },
  {
    title: '报损数量',
    field: 'itemQuantity',
    width: 280,
    editRender: {
      name: 'UnitQuantityInput',
      props: {
        onChange: (val: number, row: LossItemRow) => {
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
  { title: '单价', field: 'price', width: 200 },
  { title: '金额', field: 'totalPrice', width: 200 },
  { title: '产品批号', field: 'lotNumber', width: 160 },
  { title: '生产日期', field: 'productionDate', width: 200 },
  { title: '有效期至', field: 'expirationDate', width: 200 },
  { title: '报损原因', field: 'reason', width: 220, editRender: { name: 'ElInput' } },
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

const gridOptions: VxeGridProps<LossItemRow> = reactive({
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
  editRules: lossValidRules,
  footerMethod: ({ data }) => {
    const totalPrice = (data || []).reduce(
      (sum, row) => sum + (Number((row as any).totalPrice) || 0),
      0,
    );
    return [['合计', `${totalPrice} 元`]];
  },
});

const [Grid, gridApi] = useVbenVxeGrid<LossItemRow>({ gridOptions });

function mapDetailListToRows(list: any[]): LossItemRow[] {
  if (!Array.isArray(list)) return [];
  return list.map((item: any): LossItemRow => {
    const unitLabel = item.unitCode_dictText ?? item.unitCode ?? '';
    const unitList: ApiOptionItem[] = [
      { label: unitLabel, value: item.unitCode ?? '' },
      ...(item.minUnitCode_dictText && item.minUnitCode
        ? [{ label: item.minUnitCode_dictText, value: item.minUnitCode }]
        : []),
    ];

    const totalQuantity = item.itemQuantity;
    return {
      ...item,
      productInfo: item,
      name: item.name,
      itemId: item.itemId,
      itemTable: item.itemTable ?? item.itemTableName ?? '',
      supplierId: item.supplierId,
      supplierName: item.supplierName,
      totalVolume: item.totalVolume ?? item.volume ?? '',
      unitCode: item.defUnitCode,
      price: item.price ?? 0,
      itemQuantity: item.itemQuantity ?? 0,
      totalPrice: item.totalPrice ?? 0,
      lotNumber: item.lotNumber ?? '',
      productionDate: item.productionDate ?? '',
      expirationDate: item.expirationDate ?? '',
      reason: item.reason ?? '',
      unitList,
      isUnit: item.defUnitCode === item.unitCode,
      stockQuantity: formatQuantityByUnit(
        totalQuantity,
        item.partPercent ?? 1,
        item.unitCode_dictText ?? '',
        item.minUnitCode_dictText ?? '',
        item.defUnitCode === item.unitCode,
      ),
    };
  });
}

/** 将详情赋到表单和表格（含拉取仓库选项） */
async function applyDetailToFormAndGrid(detail: LossDetailResponse) {
  await formApi.setValues({
    practitionerId: detail.practitionerId,
    categoryEnum: detail.categoryEnum,
    supplierId: detail.supplierId,
    locationTypeEnum: detail.locationTypeEnum,
    locationId: detail.locationId,
    applyTime: detail.applyTime,
    supplyBusNo: detail.supplyBusNo,
  });
  if (detail.locationTypeEnum) {
    try {
      const res = await getWarehouseByType(String(detail.locationTypeEnum));
      formOptions.warehouseOptions = res?.data || [];
    } catch {
      formOptions.warehouseOptions = [];
    }
  }
  const list = detail.supplyList || [];
  gridApi.grid.loadData(mapDetailListToRows(list));
}

/** 保存后调 find 拉取最新详情并赋到表单/表格（item 会带上 id） */
async function refreshDetailAfterSave(busNo: string) {
  const res = await findLossByBusNoApi({ busNo });
  const detail = (res as any)?.data ?? res;
  if (detail?.supplyBusNo) {
    localLossDetail.value = detail;
    await applyDetailToFormAndGrid(detail);
  }
}

watch([() => props.lossDetail, () => props.state], async ([detail]) => {
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
  localLossDetail.value = null;
  if (!detail) {
    await formApi.resetForm();
    gridApi.grid.loadData([]);
    return;
  }
  await applyDetailToFormAndGrid(detail);
});

const addItem = async () => {
  const formValid = await formApi.validate();
  if (!formValid.valid) return;

  const $grid = gridApi.grid;
  const record = {
    name: '',
    itemId: '',
    itemTable: '',
    supplierId: '',
    supplierName: '',
    totalVolume: '',
    unitCode: '',
    itemQuantity: 0,
    price: 0,
    totalPrice: 0,
    lotNumber: '',
    startTime: '',
    endTime: '',
    reason: '',
  } as LossItemRow;
  const { row } = await $grid.insertAt(record, null);
  await $grid.$nextTick();
  await $grid.scrollToRow(row);
  await $grid.setEditCell(row, 'productInfo');
};

const deleteItem = async (row: 'select' | LossItemRow) => {
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
    await formApi.setFieldValue('applyTime', new Date());
    gridApi.grid.loadData([]);
    localLossDetail.value = null;
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

const { run: runCreateLoss, loading: createLoading } = useRequest(createLossRequestApi, {
  manual: true,
  onSuccess: async (res: any) => {
    const busNo = extractBusNo(res);
    if (busNo) await refreshDetailAfterSave(busNo);
    await onSaveSuccess();
  },
});

const { run: runUpdateLoss, loading: updateLoading } = useRequest(updateLossRequestApi, {
  manual: true,
  onSuccess: async () => {
    const busNo = effectiveLossDetail.value?.supplyBusNo;
    if (busNo) await refreshDetailAfterSave(busNo);
    await onSaveSuccess();
  },
});

function buildLossItems(fullData: LossItemRow[]): LossRequestItem[] {
  return fullData.map((row) => ({
    itemId: row.itemId ?? '',
    reason: row.reason ?? '',
    itemQuantity: row.itemQuantity ?? 0,
    supplierId: row.supplierId ?? '',
    startTime: row.startTime ?? '',
    totalPrice: row.totalPrice ?? 0,
    price: row.price ?? 0,
    unitCode: row.unitCode ?? '',
    itemTable: row.itemTable ?? '',
    name: row.name ?? '',
    lotNumber: row.lotNumber ?? '',
    endTime: row.endTime ?? '',
    traceNo: row.traceNo ?? '',
    remark: row.remark ?? '',
  }));
}

function buildUpdateLossList(fullData: LossItemRow[], originalList: LossDetailItem[]) {
  return fullData.map((row, i) => {
    const orig = originalList[i] || ({} as LossDetailItem);
    return {
      id: orig.id ?? '',
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
  const fullData = $grid.getFullData() as LossItemRow[];
  if (fullData.length === 0) {
    ElMessage.warning('请先添加报损明细');
    return;
  }

  const formValues = (await formApi.getValues()) as LossFormValues;
  const supplierId = String(formValues.supplierId || fullData[0]?.supplierId || '');
  if (!supplierId) {
    ElMessage.warning('请选择供应商');
    return;
  }

  const detailForSave = effectiveLossDetail.value;
  const isEdit = !!detailForSave?.supplyBusNo;

  if (isEdit) {
    const detail = detailForSave!;
    const originalList: LossDetailItem[] = Array.isArray(detail?.supplyList)
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

    const updateParams: UpdateLossRequestParams = {
      supplyBusNo: String(detailForSave?.supplyBusNo || ''),
      supplierId,
      locationTypeEnum: String(formValues.locationTypeEnum || ''),
      locationId: String(formValues.locationId || ''),
      deleteIds,
      supplyList: buildUpdateLossList(fullData, originalList) as UpdateLossRequestItem[],
      isSubmit,
      applyTime: dayjs(formValues.applyTime || '').format('YYYY-MM-DD HH:mm:ss'),
      practitionerId: String(formValues.practitionerId || ''),
      categoryEnum: String(formValues.categoryEnum || ''),
    };
    await runUpdateLoss(updateParams);
  } else {
    const createParams: CreateLossRequestParams = {
      supplierId,
      locationTypeEnum: String(formValues.locationTypeEnum || ''),
      locationId: String(formValues.locationId || ''),
      supplyBusNo: String(formValues.supplyBusNo || ''),
      lossRequestItems: buildLossItems(fullData),
      isSubmit,
      applyTime: dayjs(formValues.applyTime || '').format('YYYY-MM-DD HH:mm:ss'),
      practitionerId: String(formValues.practitionerId || ''),
      categoryEnum: String(formValues.categoryEnum || ''),
    };
    await runCreateLoss(createParams);
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
    const res: InitLossReportResponse = await initLossReportApi();
    formOptions.practitionerOptions = res.practitionerOptions || [];
    formOptions.categoryOptions = res.categoryOptions || res.typeOptions || [];
    formOptions.supplierOptions = res.supplierOptions || [];
    formOptions.warehouseTypeOptions = res.warehouseTypeOptions || [];
  } catch {
    formOptions.practitionerOptions = [];
    formOptions.categoryOptions = [];
    formOptions.supplierOptions = [];
    formOptions.warehouseTypeOptions = [];
  }
  if (!props.lossDetail?.supplyBusNo) {
    await formApi.setFieldValue('applyTime', new Date());
  }
});
</script>

<template>
  <Page class="mt-2">
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
    <template #footer v-if="props.state !== 'view' && !props.isInDialog">
      <ElButton type="primary" :loading="createLoading" @click="handleSave">保存</ElButton>
      <ElButton type="primary" :loading="createLoading" @click="handleSaveAndNew">
        保存并新增
      </ElButton>
      <ElButton :loading="createLoading" @click="handleSaveAndSubmit">保存并提交审核</ElButton>
    </template>
  </Page>
</template>
