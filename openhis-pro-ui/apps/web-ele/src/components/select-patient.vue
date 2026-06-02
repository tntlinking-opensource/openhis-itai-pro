<script setup lang="ts">
import type { ProductDetail } from '../types/common';

import { ElMessage } from 'element-plus';

import { getProductDetailApi, getProductListApi } from '../api/core/common';
import SelectTable from './select-table.vue';

interface Props {
  modelValue?: ProductDetail;
  placeholder?: string;
  disabled?: boolean;
  clearable?: boolean;
  pageSize?: number;
  query?: () => Promise<Record<string, any>>;
  inputHeight?: number | string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: undefined,
  placeholder: '姓名 ｜ 拼音 ｜ 证件号 ｜ 联系电话',
  disabled: false,
  clearable: true,
  pageSize: 20,
  query: () => Promise.resolve({}),
  inputHeight: undefined,
});

const emit = defineEmits<{
  change: [value: ProductDetail | undefined];
  'update:modelValue': [value: ProductDetail | undefined];
}>();

const tableColumns = [
  { prop: 'productBusNo', label: '姓名', width: 200 },
  { prop: 'productName', label: '性别', width: 200 },
  { prop: 'unitCodeDictText', label: '年龄', width: 100 },
  { prop: 'manufacturer', label: '证件号', width: 200 },
  { prop: 'price', label: '联系电话', width: 100 },
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
  const res = await getProductListApi(requestParams);

  return res;
};

const handleBeforeClose = async (_: any, value: string) => {
  const res = await getProductDetailApi({ id: value });
  if (res) {
    emit('update:modelValue', res);
    emit('change', res);
    return true;
  }
  ElMessage.warning('查询失败，请稍后重试！');
  return false;
};

const handleChange = (value: null | string | string[] | undefined) => {
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
    label-key="productName"
    :selected-label="modelValue?.productName"
    @update:model-value="handleChange"
    @change="handleChange"
    :before-close="handleBeforeClose"
    :input-height="inputHeight"
  />
</template>
