<script setup lang="ts">
import type { QuickDateRangeProps } from '../types/QuickDateRange.d';

import { computed, ref, watch } from 'vue';

defineOptions({
  name: 'QuickDateRange',
});

const props = withDefaults(defineProps<QuickDateRangeProps>(), {
  modelValue: () => [],
  startPlaceholder: '',
  endPlaceholder: '',
  valueFormat: 'YYYY-MM-DD HH:mm:ss',
  clearable: true,
  datePickerStyle: () => ({}),
  attrs: () => ({}),
});

const emit = defineEmits<{
  change: [value: string[]];
  'update:modelValue': [value: string[]];
}>();

const innerValue = ref<string[]>(
  props.modelValue && props.modelValue.length > 0 ? [...props.modelValue] : [],
);
const quickType = ref<string>('custom');

watch(
  () => props.modelValue,
  (val) => {
    if (!val || val.length === 0) {
      innerValue.value = [];
      quickType.value = 'custom';
    } else {
      innerValue.value = [...val];
    }
  },
  { deep: true },
);

const datePickerStyle = computed(() => {
  return Object.assign({ width: '300px' }, props.datePickerStyle || {});
});

function formatDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  const h = String(date.getHours()).padStart(2, '0');
  const i = String(date.getMinutes()).padStart(2, '0');
  const s = String(date.getSeconds()).padStart(2, '0');
  return `${y}-${m}-${d} ${h}:${i}:${s}`;
}

function getToday() {
  const today = new Date();
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 0, 0, 0);
  const end = today;
  return [formatDate(start), formatDate(end)];
}

function getYesterday() {
  const today = new Date();
  const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
  const s = formatDate(d);
  return [s, s];
}

function getThisWeek() {
  const today = new Date();
  const day = today.getDay() || 7; // 周日返回 7
  const monday = new Date(today);
  monday.setDate(today.getDate() - day + 1);
  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);
  return [formatDate(monday), formatDate(sunday)];
}

function getLastWeek() {
  const today = new Date();
  const day = today.getDay() || 7;
  const lastMonday = new Date(today);
  lastMonday.setDate(today.getDate() - day - 6);
  const lastSunday = new Date(lastMonday);
  lastSunday.setDate(lastMonday.getDate() + 6);
  return [formatDate(lastMonday), formatDate(lastSunday)];
}

function getLast30Days() {
  const today = new Date();
  const end = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const start = new Date(end);
  start.setDate(end.getDate() - 29);
  return [formatDate(start), formatDate(end)];
}

function getThisMonth() {
  const today = new Date();
  const start = new Date(today.getFullYear(), today.getMonth(), 1);
  const end = new Date(today.getFullYear(), today.getMonth() + 1, 0);
  return [formatDate(start), formatDate(end)];
}

function getLastMonth() {
  const today = new Date();
  const start = new Date(today.getFullYear(), today.getMonth() - 1, 1);
  const end = new Date(today.getFullYear(), today.getMonth(), 0);
  return [formatDate(start), formatDate(end)];
}

function handleQuickChange(val: string) {
  if (val === 'custom') {
    // 自定义时间段，清空日期值
    innerValue.value = [];
    emit('update:modelValue', []);
    emit('change', []);
    return;
  }
  let range: string[] = [];
  switch (val) {
    case 'last30Days': {
      range = getLast30Days();
      break;
    }
    case 'lastMonth': {
      range = getLastMonth();
      break;
    }
    case 'lastWeek': {
      range = getLastWeek();
      break;
    }
    case 'thisMonth': {
      range = getThisMonth();
      break;
    }
    case 'thisWeek': {
      range = getThisWeek();
      break;
    }
    case 'today': {
      range = getToday();
      break;
    }
    case 'yesterday': {
      range = getYesterday();
      break;
    }
    default: {
      range = [];
    }
  }
  innerValue.value = range;
  emit('update:modelValue', range);
  emit('change', range);
}

function handleDateChange(val: null | string[]) {
  // 用户手动选择时间段时，将预设切换为自定义
  quickType.value = 'custom';
  innerValue.value = val || [];
  emit('update:modelValue', innerValue.value);
  emit('change', innerValue.value);
}
</script>

<template>
  <div class="inline-flex items-center gap-2">
    <el-select
      v-model="quickType"
      class="!w-[130px]"
      :disabled="(attrs as any)?.disabled"
      @change="handleQuickChange"
    >
      <el-option label="自定义时间段" value="custom" />
      <el-option label="今天" value="today" />
      <el-option label="昨天" value="yesterday" />
      <el-option label="本周" value="thisWeek" />
      <el-option label="上周" value="lastWeek" />
      <el-option label="本月" value="thisMonth" />
      <el-option label="上月" value="lastMonth" />
      <el-option label="最近30日" value="last30Days" />
    </el-select>
    <el-date-picker
      v-model="innerValue"
      type="daterange"
      range-separator="-"
      :start-placeholder="startPlaceholder || '开始日期'"
      :end-placeholder="endPlaceholder || '结束日期'"
      :value-format="valueFormat"
      :clearable="clearable"
      :style="datePickerStyle"
      v-bind="attrs"
      @change="handleDateChange"
    />
  </div>
</template>
