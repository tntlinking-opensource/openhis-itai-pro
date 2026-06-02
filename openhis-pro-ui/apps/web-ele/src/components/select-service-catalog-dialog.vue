<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { RegistrationType } from '#/api/core/system/registration';

import { computed, onMounted, ref } from 'vue';

import { TableSelectDialog } from '@vben/common-ui';

import { getRegistrationTypeListApi } from '#/api/core/system/registration';

interface Props {
  modelValue?: null | string | string[];
  placeholder?: string;
  /** 默认多选 */
  multiple?: boolean;
  dialogTitle?: string;
  dialogWidth?: string;
  inputClass?: string;
  /** 是否显示输入框 */
  showInput?: boolean;
}

withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请选择出诊类型',
  multiple: true,
  dialogTitle: '选择出诊类型',
  dialogWidth: '70%',
  inputClass: '',
  showInput: true,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: null | string | string[]): void;
  (e: 'change', item: RegistrationType | RegistrationType[] | null): void;
}>();

const allData = ref<RegistrationType[]>([]);

const columns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
    width: 60,
  },
  {
    prop: 'name',
    label: '名称',
    minWidth: 120,
    showOverflowTooltip: true,
  },
  {
    prop: 'ybNo',
    label: '医保编码',
    width: 120,
  },
  {
    prop: 'busNo',
    label: '业务编码',
    width: 120,
    showOverflowTooltip: true,
  },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入名称/编码',
    clearable: true,
  },
]);

async function loadAll() {
  const resp = await getRegistrationTypeListApi();
  allData.value = Array.isArray(resp) ? resp : [];
}

async function requestServiceList(params: Record<string, any>) {
  const { keyword = '', pageNo = 1, pageSize = 20 } = params;
  let filtered = [...allData.value];

  if (keyword && String(keyword).trim()) {
    const keyword = String(keyword).toLowerCase().trim();
    filtered = filtered.filter((item) => {
      return (
        item.name?.toLowerCase().includes(keyword) ||
        item.ybNo?.toLowerCase().includes(keyword) ||
        item.busNo?.toLowerCase().includes(keyword)
      );
    });
  }

  const total = filtered.length;
  const start = Math.max(0, (pageNo - 1) * pageSize);
  const end = start + pageSize;
  const content = filtered.slice(start, end);

  return {
    content,
    total,
    totalElements: total,
  };
}

onMounted(() => {
  loadAll();
});
</script>

<template>
  <TableSelectDialog
    :model-value="modelValue"
    :placeholder="placeholder"
    :multiple="multiple"
    :columns="columns"
    :form-items="formItems"
    :request="requestServiceList"
    :data="null"
    display-field="name"
    value-field="id"
    :dialog-title="dialogTitle"
    :dialog-width="dialogWidth"
    :input-class="inputClass"
    :show-input="showInput"
    @update:model-value="emit('update:modelValue', $event as any)"
    @change="emit('change', $event as any)"
  />
</template>
