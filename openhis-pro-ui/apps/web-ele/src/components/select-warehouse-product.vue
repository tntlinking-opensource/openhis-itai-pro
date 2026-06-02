<script setup lang="ts">
import type { InventoryItemDetail } from '../api/core/common';

import { ElMessage } from 'element-plus';

import { getInventoryItemDetailApi, getWarehouseProductListApi } from '../api/core/common';
import SelectTable from './select-table.vue';

interface Props {
  modelValue?: InventoryItemDetail;
  placeholder?: string;
  disabled?: boolean;
  clearable?: boolean;
  pageSize?: number;
  query?: () => Promise<Record<string, any>>;
  inputHeight?: number | string;
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '请输入产品名称搜索',
  disabled: false,
  clearable: true,
  pageSize: 20,
  query: () => Promise.resolve({}),
});

const emit = defineEmits<{
  change: [value: InventoryItemDetail | undefined];
  'update:modelValue': [value: InventoryItemDetail | undefined];
}>();

const tableColumns = [
  { prop: 'busNo', label: '编码', width: 200 },
  { prop: 'categoryCode_dictText', label: '类型', width: 100 },
  { prop: 'name', label: '产品名称', width: 200 },
  { prop: 'volume', label: '规格', width: 200 },
  { prop: 'unitCode_dictText', label: '包装单位', width: 100 },
  { prop: 'quantity', label: '库存数量', width: 100 },
  { prop: 'manufacturer', label: '生产厂家', width: 200 },
  { prop: 'supplierId_dictText', label: '供应商', width: 200 },
  { prop: 'price', label: '价格', width: 100 },
  { prop: 'lotNumber', label: '批次号', width: 100 },
];

const handleRemoteMethod = async (params: {
  [key: string]: any;
  pageNo: number;
  pageSize: number;
  keyword?: string;
}) => {
  const { pageNo, pageSize, keyword, ...restParams } = params;
  const requestParams: any = {
    pageNo,
    pageSize,
    ...(await props.query()),
    ...restParams,
  };
  if (keyword) {
    requestParams.keyword = keyword;
  }
  const res = await getWarehouseProductListApi(requestParams);

  return res;
};

const handleBeforeClose = async (_: any, value: string) => {
  const res = await getInventoryItemDetailApi({ id: value });
  if (res) {
    emit('update:modelValue', res);
    emit('change', res);
    return true;
  }
  ElMessage.warning('查询失败，请稍后重试！');
  return false;
};

const handleChange = (value: (number | string)[] | null | string | undefined) => {
  // 这里只处理清空的场景，其余场景交给 beforeClose 统一更新详情
  if (value === null || value === undefined || value === '') {
    emit('update:modelValue', undefined);
    emit('change', undefined);
  }
};
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
    :selected-label="modelValue?.name"
    @update:model-value="handleChange"
    @change="handleChange"
    :before-close="handleBeforeClose"
    :input-height="inputHeight"
  />
</template>
