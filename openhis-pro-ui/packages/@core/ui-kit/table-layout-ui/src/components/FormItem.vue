<script setup lang="ts">
import type { FormItemProps } from '../types/FormItem.d';

import { computed } from 'vue';

import QuickDateRange from './QuickDateRange.vue';

defineOptions({
  name: 'FormItem',
});

const props = defineProps<FormItemProps>();

const emit = defineEmits<{
  change: [value: any];
  'update:modelValue': [value: any];
}>();

const labelWithSuffix = computed(() => {
  const suffix = props.item.labelSuffix || '';
  return `${props.item.label || ''}${suffix}`;
});

// 日期区间组件的值处理
const daterangeValue = computed<string[]>(() => {
  if (props.item.type === 'daterange') {
    if (Array.isArray(props.modelValue)) {
      return props.modelValue.map(String);
    }
    return [];
  }
  return [];
});

// 日期区间组件的样式处理
const daterangeStyle = computed(() => {
  if (props.item.type === 'daterange') {
    if (
      typeof props.item.style === 'object' &&
      props.item.style !== null &&
      !Array.isArray(props.item.style)
    ) {
      return props.item.style;
    }
    return { width: props.item.width || 'calc(316px + 7em)' };
  }
  return {};
});

const handleUpdate = (value: any) => {
  emit('update:modelValue', value);
};

const handleUpdateWithCheck = async (value: any, shouldCheck = false) => {
  if (shouldCheck && props.item.onChange && typeof props.item.onChange === 'function') {
    const result = await props.item.onChange(value);
    if (result === false) {
      return;
    }
  }
  handleUpdate(value);
};

const handleChange = (value: any) => {
  emit('change', value);
};
</script>

<template>
  <el-form-item :label="labelWithSuffix" :prop="item.prop" :required="item.required">
    <el-input
      v-if="item.type === 'input'"
      :model-value="modelValue"
      :placeholder="item.placeholder || `请输入${item.label}`"
      :clearable="item.clearable !== false"
      :style="item.style || { width: item.width || '200px' }"
      v-bind="item.extraprops || {}"
      @update:model-value="handleUpdate"
    />
    <el-select
      v-else-if="item.type === 'select'"
      :model-value="modelValue"
      :placeholder="item.placeholder || `请选择${item.label}`"
      :clearable="item.clearable !== false"
      :style="item.style || { width: item.width || '200px' }"
      :disabled="item.disabled"
      v-bind="item.extraprops || {}"
      :multiple="item.multiple !== false"
      :filterable="item.filterable !== false"
      :collapse-tags="item.collapseTags !== false"
      @change="handleChange"
      @update:model-value="(value: any) => handleUpdateWithCheck(value, item.checkBeforeChange)"
    >
      <el-option
        v-for="option in item.options || []"
        :key="option.value"
        :label="option.label"
        :value="option.value"
      />
    </el-select>
    <el-radio-group
      v-else-if="item.type === 'radio'"
      :model-value="modelValue"
      v-bind="item.extraprops || {}"
      @change="handleChange"
      @update:model-value="handleUpdate"
    >
      <el-radio v-for="option in item.options || []" :key="option.value" :label="option.value">
        {{ option.label }}
      </el-radio>
    </el-radio-group>
    <!-- 单独日期 -->
    <el-date-picker
      v-else-if="item.type === 'date'"
      :model-value="modelValue"
      type="date"
      :placeholder="item.placeholder || `请选择${item.label}`"
      :clearable="item.clearable !== false"
      :value-format="item.valueFormat || 'YYYY-MM-DD HH:mm:ss'"
      :style="item.style || { width: item.width || '200px' }"
      :disabled="item.disabled"
      v-bind="item.extraprops || {}"
      @change="handleChange"
      @update:model-value="handleUpdate"
    />
    <!-- 日期区间 -->
    <QuickDateRange
      v-else-if="item.type === 'daterange'"
      :model-value="daterangeValue"
      :start-placeholder="item.startPlaceholder || '开始日期'"
      :end-placeholder="item.endPlaceholder || '结束日期'"
      :value-format="item.valueFormat || 'YYYY-MM-DD HH:mm:ss'"
      :clearable="item.clearable !== false"
      :date-picker-style="daterangeStyle"
      :attrs="item.extraprops || {}"
      @change="handleChange"
      @update:model-value="handleUpdate"
    />
    <!-- 纯文本展示 -->
    <span v-else-if="item.type === 'text'" :style="item.style || { width: item.width || '200px' }">
      {{ item.formatter ? item.formatter(modelValue) : (modelValue ?? '') }}
    </span>
    <slot
      v-else-if="item.type === 'custom'"
      :name="item.slot || item.prop"
      :item="item"
      :model-value="modelValue"
      :update-model-value="handleUpdate"
    ></slot>
  </el-form-item>
</template>

<style scoped lang="scss">
:deep(.el-form-item) {
  display: inline-flex;
  align-items: flex-start;
  margin-right: 1rem;
  margin-bottom: 1rem;
  vertical-align: top;

  .el-form-item__label {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    width: 7em !important;
    min-width: 7em;
    padding-top: 0;
    padding-right: 0.5rem;
    line-height: 1.5;
    text-align: right;
    word-break: break-all;
    white-space: normal;
  }

  .el-form-item__content {
    display: flex;
    align-items: center;
  }
}
</style>
