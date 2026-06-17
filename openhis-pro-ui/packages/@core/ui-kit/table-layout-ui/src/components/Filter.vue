<script setup lang="ts">
import type { FilterProps } from '../types/Filter.d';

import { computed, ref } from 'vue';

import { IconifyIcon } from '@vben-core/icons';

import FormItem from './FormItem.vue';

defineOptions({
  name: 'Filter',
});

const props = withDefaults(defineProps<FilterProps>(), {
  formItems: () => [],
  show: true,
  showDefaultButtons: true,
  labelWidth: '120px',
  showLabelColon: true,
});

const emit = defineEmits<{
  query: [queryParams: Record<string, any>];
  reset: [];
}>();

const queryFormRef = ref<InstanceType<typeof import('element-plus').ElForm> | null>(null);
const isExpanded = ref(true);

const itemsPerRow = 4;

const normalizedFormItems = computed(() =>
  (props.formItems || []).map((item) => ({
    ...item,
    labelSuffix: item.labelSuffix ?? (props.showLabelColon ? '：' : ''),
  })),
);

const needCollapse = computed(() => {
  if (!normalizedFormItems.value || normalizedFormItems.value.length === 0) return false;

  let totalWidth = 0;
  normalizedFormItems.value.forEach((item) => {
    totalWidth += item.type === 'custom' || item.type === 'daterange' ? 2 : 1;
  });

  return totalWidth > itemsPerRow * 2;
});

const displayedFormItems = computed(() => {
  if (!needCollapse.value || isExpanded.value) {
    return normalizedFormItems.value;
  }

  const maxItems = itemsPerRow * 2;
  let count = 0;
  const result: any[] = [];

  for (const item of normalizedFormItems.value) {
    const itemWidth = item.type === 'custom' || item.type === 'daterange' ? 2 : 1;

    if (count + itemWidth > maxItems) {
      break;
    }

    result.push(item);
    count += itemWidth;
  }

  return result;
});

const toggleExpand = () => {
  isExpanded.value = !isExpanded.value;
};

const handleQuery = () => {
  emit('query', props.queryParams);
};

const resetQuery = () => {
  if (queryFormRef.value) {
    queryFormRef.value.resetFields();
  }
  // Note: pageNum reset should be handled by parent component
  emit('reset');
};

defineExpose({
  queryFormRef,
  handleQuery,
  resetQuery,
});
</script>

<template>
  <div v-if="show" class="w-full flex-shrink-0">
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="w-full"
      :label-width="labelWidth"
      @submit.prevent="handleQuery"
    >
      <template v-for="item in displayedFormItems" :key="item.prop">
        <FormItem
          :item="item"
          :model-value="queryParams[item.prop]"
          @update:model-value="(value) => (queryParams[item.prop] = value)"
          @change="(value) => item.onChange && item.onChange(value)"
        >
          <template v-for="(_, slotName) in $slots" :key="slotName" #[slotName]="slotProps">
            <slot :name="slotName" v-bind="slotProps"></slot>
          </template>
        </FormItem>
      </template>
      <el-form-item v-if="showDefaultButtons" class="ml-5">
        <el-button type="primary" @click="handleQuery">
          <el-icon><IconifyIcon icon="mdi:magnify" /></el-icon>
          <span>搜索</span>
        </el-button>
        <el-button @click="resetQuery">
          <el-icon><IconifyIcon icon="mdi:refresh" /></el-icon>
          <span>重置</span>
        </el-button>
        <el-button v-if="needCollapse" type="text" @click="toggleExpand" class="ml-4">
          {{ isExpanded ? '收起' : '展开' }}
          <el-icon class="el-icon--right">
            <IconifyIcon
              :icon="isExpanded ? 'mdi:chevron-up' : 'mdi:chevron-down'"
              class="transition-transform duration-200 ease-in-out"
            />
          </el-icon>
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>
