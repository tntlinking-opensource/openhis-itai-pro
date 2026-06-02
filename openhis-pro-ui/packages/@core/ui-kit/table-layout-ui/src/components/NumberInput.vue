<script setup lang="ts">
import { computed } from 'vue';

interface NumberInputProps {
  clearable?: boolean;
  disabled?: boolean;
  max?: number;
  min?: number;
  modelValue?: number | string;
  placeholder?: string;
  precision?: number;
  suffix?: string;
}

const props = withDefaults(defineProps<NumberInputProps>(), {
  modelValue: undefined,
  placeholder: '',
  disabled: false,
  clearable: true,
  suffix: '',
  precision: undefined,
  min: undefined,
  max: undefined,
});

const emit = defineEmits<{
  blur: [event: Event];
  change: [value: string];
  'update:modelValue': [value: string];
}>();

const displayValue = computed(() => {
  if (props.modelValue === null || props.modelValue === undefined || props.modelValue === '') {
    return '';
  }
  return String(props.modelValue);
});

const handleInput = (value: string) => {
  // 只允许数字、小数点和负号
  let newValue = value.replaceAll(/[^\d.-]/g, '');

  // 只允许一个小数点
  const parts = newValue.split('.');
  if (parts.length > 2) {
    newValue = `${parts[0]}.${parts.slice(1).join('')}`;
  }

  // 只允许一个负号，且必须在开头
  if (newValue.indexOf('-') > 0) {
    newValue = newValue.replaceAll('-', '');
  }
  if (newValue.startsWith('-') && newValue.split('-').length > 2) {
    newValue = `-${newValue.replaceAll('-', '')}`;
  }

  // 如果为空，直接返回空字符串
  if (newValue === '' || newValue === '-') {
    emit('update:modelValue', '');
    return;
  }

  // 转换为数字
  const numValue = Number.parseFloat(newValue);
  if (Number.isNaN(numValue)) {
    emit('update:modelValue', '');
    return;
  }

  // 限制最小值
  if (props.min !== undefined && numValue < props.min) {
    newValue = String(props.min);
  }

  // 限制最大值
  if (props.max !== undefined && numValue > props.max) {
    newValue = String(props.max);
  }

  // 处理精度
  if (props.precision !== undefined && newValue.includes('.')) {
    const parts = newValue.split('.');
    if (parts[1] && parts[1].length > props.precision) {
      parts[1] = parts[1].slice(0, Math.max(0, props.precision));
      newValue = parts.join('.');
    }
  }

  emit('update:modelValue', newValue);
};

const handleBlur = (event: Event) => {
  const target = event.target as HTMLInputElement;
  if (!target) return;
  const value = target.value;
  if (value === '' || value === '-') {
    emit('update:modelValue', '');
    emit('blur', event);
    return;
  }

  const numValue = Number.parseFloat(value);
  if (Number.isNaN(numValue)) {
    emit('update:modelValue', '');
    emit('blur', event);
    return;
  }

  // 应用精度
  let finalValue = numValue;
  if (props.precision !== undefined) {
    finalValue = Number.parseFloat(numValue.toFixed(props.precision));
  }

  // 限制范围
  if (props.min !== undefined && finalValue < props.min) {
    finalValue = props.min;
  }
  if (props.max !== undefined && finalValue > props.max) {
    finalValue = props.max;
  }

  emit('update:modelValue', String(finalValue));
  emit('blur', event);
};

const handleChange = (value: string) => {
  emit('change', value);
};
</script>

<template>
  <el-input
    :model-value="displayValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    @input="handleInput"
    @blur="handleBlur"
    @change="handleChange"
  >
    <template v-if="suffix" #suffix>{{ suffix }}</template>
  </el-input>
</template>

<style scoped lang="scss">
:deep(.el-input__inner) {
  text-align: left;
}
</style>
