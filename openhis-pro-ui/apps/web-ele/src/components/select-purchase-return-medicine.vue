<script setup lang="ts">
import { pagePurchaseReturnMedicationApi } from '#/api/warehouse/purchase-return';
import SelectTable from '#/components/select-table.vue';

type PurchaseReturnMedicationPickRow = Record<string, unknown> & {
  busNo?: string;
  id?: string;
  name?: string;
};

interface Props {
  modelValue?: null | PurchaseReturnMedicationPickRow;
  placeholder?: string;
  disabled?: boolean;
  clearable?: boolean;
  pageSize?: number;
  query?: () => Promise<Record<string, unknown>>;
  inputHeight?: number | string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: undefined,
  placeholder: '请输入关键字检索药品',
  disabled: false,
  clearable: true,
  pageSize: 20,
  query: () => Promise.resolve({}),
  inputHeight: undefined,
});

const emit = defineEmits<{
  change: [value: PurchaseReturnMedicationPickRow | undefined];
  'update:modelValue': [value: PurchaseReturnMedicationPickRow | undefined];
}>();

const tableColumns = [
  { prop: 'busNo', label: '项目编码', width: 140 },
  { prop: 'name', label: '项目名称', width: 160 },
  { prop: 'price', label: '进货价', width: 90 },
  { prop: 'retailPrice', label: '零售价', width: 90 },
  { prop: 'unitCode_dictText', label: '包装单位', width: 90 },
  { prop: 'minUnitCode_dictText', label: '最小单位', width: 90 },
  { prop: 'totalVolume', label: '规格', width: 120 },
  { prop: 'specificationInventory', label: '规格库存', width: 100 },
  { prop: 'manufacturerText', label: '生产厂家', width: 160 },
  { prop: 'approvalNumber', label: '批准文号', width: 140 },
];

async function handleRemoteMethod(params: { pageNo: number; pageSize: number; keyword?: string }) {
  const q = await props.query();
  const data = await pagePurchaseReturnMedicationApi({
    supplierId: q.supplierId as string | undefined,
    locationId: q.locationId as string | undefined,
    pageNo: params.pageNo,
    pageSize: params.pageSize,
    keyword: params.keyword,
  });
  const records = data.records ?? [];
  return {
    content: records,
    totalElements: data.total ?? records.length,
  };
}

async function handleBeforeClose(item: Record<string, unknown>) {
  emit('update:modelValue', item as PurchaseReturnMedicationPickRow);
  emit('change', item as PurchaseReturnMedicationPickRow);
  return true;
}

function handleChange(value: null | string | string[] | undefined) {
  if (value === null || value === undefined || value === '') {
    emit('update:modelValue', undefined);
    emit('change', undefined);
  }
}
</script>

<template>
  <SelectTable
    :model-value="modelValue?.id"
    :remote-method="handleRemoteMethod"
    :columns="tableColumns"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :page-size="pageSize"
    value-key="id"
    label-key="name"
    :selected-label="modelValue?.name"
    :before-close="handleBeforeClose"
    :input-height="inputHeight"
    @update:model-value="handleChange"
    @change="handleChange"
  />
</template>
