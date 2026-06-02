<script setup lang="ts">
import type { RegistrationType } from '#/api/core/system/registration';

import { onMounted, ref, watch } from 'vue';

import { ElMessage, ElOption, ElSelect } from 'element-plus';

import { getRegistrationTypeListApi } from '#/api/core/system/registration';

interface Props {
  modelValue?: null | string | string[];
  placeholder?: string;
  disabled?: boolean;
  clearable?: boolean;
  filterable?: boolean;
  multiple?: boolean;
  class?: string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请选择出诊类型',
  disabled: false,
  clearable: true,
  filterable: true,
  multiple: true,
  class: '',
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: null | string | string[]): void;
  (e: 'change', value: RegistrationType[]): void;
}>();

interface SelectOption {
  value: string;
  label: string;
  raw: RegistrationType;
}

const options = ref<SelectOption[]>([]);
const innerValue = ref<null | string | string[]>(
  props.modelValue === null || props.modelValue === undefined
    ? null
    : props.multiple
      ? Array.isArray(props.modelValue)
        ? props.modelValue
        : [props.modelValue]
      : Array.isArray(props.modelValue)
        ? (props.modelValue[0] ?? null)
        : props.modelValue,
);

async function loadOptions() {
  try {
    const data = await getRegistrationTypeListApi();
    options.value = (data || []).map((item) => ({
      value: item.id,
      label: item.name,
      raw: item,
    }));
  } catch (error) {
    console.error('加载出诊类型失败:', error);
    ElMessage.error('加载出诊类型失败，请稍后重试');
    options.value = [];
  }
}

function findOptionsByValues(values: null | string | string[]): RegistrationType[] {
  if (!values) return [];
  const valueArray = Array.isArray(values) ? values : [values];
  return valueArray
    .map((val) => {
      const option = options.value.find((opt) => opt.value === val);
      return option?.raw;
    })
    .filter(Boolean) as RegistrationType[];
}

function handleChange(value: null | string | string[]) {
  innerValue.value = value;
  emit('update:modelValue', value);

  const items = findOptionsByValues(value);
  emit('change', items);
}

watch(
  () => props.modelValue,
  (val) => {
    if (val === null || val === undefined) {
      innerValue.value = null;
    } else if (props.multiple) {
      innerValue.value = Array.isArray(val) ? val : [val];
    } else {
      innerValue.value = Array.isArray(val) ? (val[0] ?? null) : val;
    }
  },
  { immediate: true },
);

onMounted(() => {
  loadOptions();
});
</script>

<template>
  <ElSelect
    v-model="innerValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :filterable="filterable"
    :multiple="multiple"
    style="width: 220px"
    :class="props.class"
    @change="handleChange"
  >
    <ElOption
      v-for="option in options"
      :key="option.value"
      :label="option.label"
      :value="option.value"
    />
  </ElSelect>
</template>
