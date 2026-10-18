<script setup lang="ts">
import type { ComputedRef } from 'vue';

import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { InventoryItemDetail } from '#/api/core/common';
import type {
  CreateOutboundRequestItem,
  CreateOutboundRequestParams,
  DrawDetailResponse,
  DrawDetailSupplyItem,
  InitWarehouseDrawOptionsResponse,
  UpdateDrawRequisitionInfoParams,
  UpdateDrawRequisitionInfoSupplyItem,
} from '#/api/warehouse/draw';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@zxplat/common-ui';

import { dayjs, ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getWarehouseByType } from '#/api/warehouse/common';
import {
  createOutboundRequestApi,
  findRequisitionByBusNoApi,
  initDrawRequisitionApi,
  updateDrawRequisitionInfoApi,
} from '#/api/warehouse/draw';
import { useRequest } from '#/hooks/use-request';

import { calcTotalPrice, formatQuantityByUnit } from '../../util';
import { validRules as drawValidRules } from './util';

interface DrawItemRow {
  id?: string;
  name?: string;
  itemId: string;
  totalVolume: string;
  manufacturerId?: string;
  manufacturerText?: string;
  itemTable?: string;
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
    drawDetail?: DrawDetailResponse | null;
    isInDialog?: boolean;
    state?: 'edit' | 'view';
  }>(),
  { isInDialog: false, drawDetail: undefined },
);

const emit = defineEmits<{
  success: [];
}>();

const router = useRouter();
const isViewMode = ref(false);
const lastAction = ref<'save' | 'saveAndNew' | 'saveAndSubmit'>('save');
const localDetail = ref<DrawDetailResponse | null>(null);
const effectiveDrawDetail = computed<DrawDetailResponse | null>(
  () => localDetail.value ?? props.drawDetail ?? null,
);

const formOptions = reactive<{
  categoryOptions: ApiOptionItem[];
  organizationOptions: ApiOptionItem[];
  practitionerOptions: ApiOptionItem[];
  warehouseOptions: ApiOptionItem[];
  warehouseTypeOptions: ApiOptionItem[];
}>({
  practitionerOptions: [],
  categoryOptions: [],
  warehouseTypeOptions: [],
  warehouseOptions: [],
  organizationOptions: [],
});

type DrawFormValues = {
  applicantId?: string;
  categoryEnum?: string;
  locationId?: string;
  locationTypeEnum?: string;
  practitionerId?: string;
  purposeLocationId?: string;
};

const [Form, formApi] = useVbenForm(
  reactive({
    schema: computed(() => [
      {
        component: 'Select',
        fieldName: 'categoryEnum',
        label: '领用类型',
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
        label: '领用仓库',
        rules: 'required',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.warehouseOptions,
        },
      },
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
        fieldName: 'purposeLocationId',
        label: '科室',
        componentProps: {
          clearable: true,
          filterable: true,
          options: formOptions.organizationOptions,
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
          const values = await formApi.getValues();
          return {
            drugType: values.categoryEnum,
            locationId: values.locationId,
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
  { title: '规格', field: 'totalVolume', width: 160 },
  {
    title: '计量单位',
    field: 'unitCode',
    width: 120,
    editRender: {
      name: 'UnitSelect',
      props: {
        onChange: (val: any, row: any) => {
          row.isUnit = val === row.productInfo?.unitCode;
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
  { title: '库存数量', field: 'stockQuantity', width: 180 },
  {
    title: '领用数量',
    field: 'itemQuantity',
    width: 280,
    editRender: {
      name: 'UnitQuantityInput',
      props: {
        onChange: (val: number, row: DrawItemRow) => {
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
  { title: '生产日期', field: 'startTime', width: 200 },
  { title: '有效期至', field: 'endTime', width: 200 },
  { title: '追溯码', field: 'traceNo', width: 160 },
  { title: '备注', field: 'remark', width: 180, editRender: { name: 'ElInput' } },
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

const gridOptions: VxeGridProps<DrawItemRow> = reactive({
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
  editRules: drawValidRules,
  footerMethod: ({ data }) => {
    const totalPrice = (data || []).reduce(
      (sum, row) => sum + (Number((row as any).totalPrice) || 0),
      0,
    );
    return [['合计', `${totalPrice} 元`]];
  },
});

const [Grid, gridApi] = useVbenVxeGrid<DrawItemRow>({ gridOptions });

function mapDetailListToRows(list: DrawDetailSupplyItem[]): DrawItemRow[] {
  if (!Array.isArray(list)) return [];
  return list.map((item) => {
    const unitList: ApiOptionItem[] = [
      { label: item.unitCode_dictText || '', value: item.unitCode || '' },
      ...(item.minUnitCode_dictText && item.minUnitCode
        ? [{ label: item.minUnitCode_dictText, value: item.minUnitCode }]
        : []),
    ];
    return {
      ...item,
      name: item.name ?? '',
      itemQuantity: item.itemQuantity ?? 0,
      manufacturerId: item.manufacturerText,
      startTime: item.startTime ?? '',
      endTime: item.endTime ?? '',
      productInfo: item,
      unitList,
      isUnit: item.unitCode === item.defUnitCode,
      stockQuantity: formatQuantityByUnit(
        item.totalQuantity ?? 0,
        item.partPercent ?? 1,
        item.unitCode_dictText ?? '',
        item.minUnitCode_dictText ?? '',
        true,
      ),
    };
  });
}

/** 将详情赋到表单和表格（含拉取仓库选项） */
async function applyDetailToFormAndGrid(detail: DrawDetailResponse) {
  formApi.setValues({
    categoryEnum: detail.categoryEnum,
    locationTypeEnum: detail.locationTypeEnum,
    locationId: detail.locationId,
    practitionerId: detail.practitionerId,
    purposeLocationId: detail.purposeLocationId,
  });
  if (detail.locationTypeEnum) {
    try {
      const res = await getWarehouseByType(String(detail.locationTypeEnum));
      formOptions.warehouseOptions = res?.data || [];
    } catch {
      formOptions.warehouseOptions = [];
    }
  }
  const rows = mapDetailListToRows(detail.supplyList ?? []);
  gridApi.grid.loadData(rows);
}

/** 保存后调 find 拉取最新详情并赋到表单/表格（item 会带上 id） */
async function refreshDetailAfterSave(busNo: string) {
  const res = await findRequisitionByBusNoApi({ busNo });
  const detail = (res as any)?.data ?? res;
  if (detail?.supplyBusNo) {
    localDetail.value = detail;
    await applyDetailToFormAndGrid(detail);
  }
}

watch([() => props.drawDetail, () => props.state], async ([detail]) => {
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
    gridApi.grid.loadData([]);
    return;
  }
  await applyDetailToFormAndGrid(detail);
});

const addItem = async () => {
  const formValid = await formApi.validate();
  if (!formValid.valid) return;
  const $grid = gridApi.grid;
  if ($grid) {
    const record: DrawItemRow = {
      name: '',
      itemId: '',
      totalVolume: '',
      manufacturerId: '',
      unitCode: '',
      itemQuantity: 0,
      price: 0,
      totalPrice: 0,
      lotNumber: '',
      traceNo: '',
      startTime: '',
      endTime: '',
      remark: '',
    };
    const { row } = await $grid.insertAt(record, null);
    await $grid.$nextTick();
    await $grid.scrollToRow(row);
    await $grid.setEditCell(row, 'productInfo');
  }
};

const deleteItem = async (row: 'select' | DrawItemRow) => {
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

const onSaveSuccess = async () => {
  if (lastAction.value === 'saveAndNew') {
    ElMessage.success('保存成功，已准备新增下一条');
    await formApi.resetForm();
    gridApi.grid.loadData([]);
    return;
  }
  if (lastAction.value === 'saveAndSubmit') {
    ElMessage.success('保存并提交审核成功');
    if (props.isInDialog) {
      emit('success');
    } else {
      router.push('/warehouse/draw');
    }
    return;
  }
  ElMessage.success('保存成功');
  if (props.isInDialog) emit('success');
};

const { run: runCreate, loading: createLoading } = useRequest(createOutboundRequestApi, {
  manual: true,
  onSuccess: async (res: any) => {
    const busNo = res?.data?.busNo ?? res?.busNo ?? '';
    if (busNo) await refreshDetailAfterSave(busNo);
    onSaveSuccess();
  },
});

const { run: runUpdate, loading: updateLoading } = useRequest(updateDrawRequisitionInfoApi, {
  manual: true,
  onSuccess: async () => {
    const busNo = effectiveDrawDetail.value?.supplyBusNo;
    if (busNo) await refreshDetailAfterSave(busNo);
    onSaveSuccess();
  },
});

function buildUpdateSupplyList(
  fullData: DrawItemRow[],
  originalList: DrawDetailSupplyItem[],
): UpdateDrawRequisitionInfoSupplyItem[] {
  return fullData.map((row, i) => {
    const orig = originalList[i] || ({} as DrawDetailSupplyItem);
    return {
      id: row.id ?? orig.id ?? '',
      itemId: row.itemId ?? orig.itemId ?? '',
      itemQuantity: row.itemQuantity ?? orig.itemQuantity ?? 0,
      totalPrice: row.totalPrice ?? orig.totalPrice ?? 0,
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

async function doSave(isSubmit: boolean) {
  const { valid } = await formApi.validate();
  if (!valid) return;
  const $grid = gridApi.grid;
  const validGrid = await $grid.validate(true);
  if (validGrid) return;
  const fullData = $grid.getFullData() as DrawItemRow[];
  if (fullData.length === 0) {
    ElMessage.warning('请先添加领用明细');
    return;
  }
  const formValues = (await formApi.getValues()) as DrawFormValues;
  const detail = effectiveDrawDetail.value;
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
    const params: UpdateDrawRequisitionInfoParams = {
      supplyBusNo: detail.supplyBusNo ?? '',
      categoryEnum: String(formValues.categoryEnum ?? ''),
      locationTypeEnum: String(formValues.locationTypeEnum ?? ''),
      locationId: String(formValues.locationId ?? ''),
      purposeLocationId: String(formValues.purposeLocationId ?? ''),
      applyTime: detail.applyTime ?? dayjs().format('YYYY-MM-DD HH:mm:ss'),
      practitionerId: String(formValues.practitionerId ?? ''),
      deleteIds,
      supplyList: buildUpdateSupplyList(fullData, originalList),
      isSubmit,
    };
    await runUpdate(params);
  } else {
    const locationId = String(formValues.locationId ?? '');
    const items: CreateOutboundRequestItem[] = fullData.map((row) => ({
      name: row.name ?? '',
      itemId: row.itemId ?? '',
      unitCode: row.unitCode ?? '',
      price: row.price ?? 0,
      totalPrice: row.totalPrice ?? 0,
      totalVolume: row.totalVolume ?? '',
      quantity: row.itemQuantity ?? 0,
      totalQuantity: row.itemQuantity ?? 0,
      salePrice: row.price ?? 0,
      itemTable: row.itemTable ?? '',
      manufacturerId: row.manufacturerId ?? '',
      remark: row.remark ?? '',
      lotNumber: row.lotNumber ?? '',
      sourceLocationId: locationId,
      ybNo: row.traceNo ?? '',
    }));
    const applyTime = dayjs().format('YYYY-MM-DD HH:mm:ss');
    const params: CreateOutboundRequestParams = {
      categoryEnum: String(formValues.categoryEnum ?? ''),
      locationTypeEnum: String(formValues.locationTypeEnum ?? ''),
      locationId,
      supplyBusNo: '',
      isSubmit,
      applicantId: String(formValues.applicantId ?? ''),
      applyTime,
      practitionerId: String(formValues.practitionerId ?? ''),
      purposeLocationId: String(formValues.purposeLocationId ?? ''),
      items,
    };
    await runCreate(params);
  }
}

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
    const res = (await initDrawRequisitionApi()) as InitWarehouseDrawOptionsResponse;
    formOptions.practitionerOptions = (res.practitionerOptions || []) as ApiOptionItem[];
    formOptions.categoryOptions = (res.categoryOptions || []) as ApiOptionItem[];
    formOptions.warehouseTypeOptions = (res.warehouseTypeOptions || []) as ApiOptionItem[];
    formOptions.organizationOptions = (res.organizationOptions || []) as ApiOptionItem[];
  } catch {
    formOptions.practitionerOptions = [];
    formOptions.categoryOptions = [];
    formOptions.warehouseTypeOptions = [];
    formOptions.organizationOptions = [];
  }
  isViewMode.value = props.state === 'view';
});
</script>

<template>
  <Page class="mt-2">
    <div class="flex h-full flex-col">
      <Form />
      <div class="min-h-0 flex-1">
        <Grid>
          <template #toolbar-actions v-if="!isViewMode">
            <ElButton class="mr-2" type="primary" @click="addItem">添加项目</ElButton>
            <ElButton type="danger" @click="deleteItem('select')">删除项目</ElButton>
          </template>
          <template #action="{ row }">
            <ElButton type="danger" text @click="deleteItem(row)">删除</ElButton>
          </template>
        </Grid>
      </div>
    </div>
    <template #footer v-if="!isInDialog">
      <ElButton type="primary" :loading="createLoading || updateLoading" @click="handleSave">
        保存
      </ElButton>
      <ElButton
        v-if="!effectiveDrawDetail?.supplyBusNo"
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
