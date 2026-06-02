<script setup lang="ts">
import type { ResponsibilityDept } from '#/api/core/common';

import { onMounted, ref, watch } from 'vue';

import { ElMessage, ElTreeSelect } from 'element-plus';

import { getTreeForResponsibilityDeptApi } from '#/api/core/common';

interface Props {
  modelValue?: null | string;
  placeholder?: string;
  disabled?: boolean;
  clearable?: boolean;
  filterable?: boolean;
  class?: string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请选择责任科室',
  disabled: false,
  clearable: true,
  filterable: true,
  class: '',
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: null | string): void;
  (e: 'change', value: null | ResponsibilityDept): void;
}>();

interface TreeOption {
  value: string;
  label: string;
  children?: TreeOption[];
  raw: ResponsibilityDept;
}

const options = ref<TreeOption[]>([]);
const innerValue = ref<null | string>(props.modelValue ?? null);

function transformToTreeOptions(list: ResponsibilityDept[]): TreeOption[] {
  return list
    .toSorted((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0))
    .map((item) => ({
      value: item.id,
      label: item.name,
      raw: item,
      children: item.children?.length ? transformToTreeOptions(item.children) : undefined,
    }));
}

async function loadOptions() {
  try {
    const data = await getTreeForResponsibilityDeptApi();
    options.value = transformToTreeOptions(data || []);
  } catch (error) {
    console.error('加载责任科室树失败:', error);
    ElMessage.error('加载责任科室失败，请稍后重试');
    options.value = [];
  }
}

function findOptionByValue(list: TreeOption[], value: null | string): null | TreeOption {
  if (!value) return null;
  for (const item of list) {
    if (item.value === value) return item;
    if (item.children?.length) {
      const found = findOptionByValue(item.children, value);
      if (found) return found;
    }
  }
  return null;
}

function handleChange(value: null | string) {
  innerValue.value = value;
  emit('update:modelValue', value);
  const option = findOptionByValue(options.value, value);
  emit('change', option ? option.raw : null);
}

watch(
  () => props.modelValue,
  (val) => {
    innerValue.value = val ?? null;
  },
);

onMounted(() => {
  loadOptions();
});
</script>

<template>
  <ElTreeSelect
    v-model="innerValue"
    :data="options"
    :props="{ label: 'label', children: 'children', value: 'value' }"
    node-key="value"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :filterable="filterable"
    check-strictly
    style="width: 220px"
    :class="props.class"
    @change="handleChange"
  />
</template>
