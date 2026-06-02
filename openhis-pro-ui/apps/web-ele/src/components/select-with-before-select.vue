<script lang="ts" setup>
import { computed, ref, useAttrs, watch } from 'vue';

import { ElSelectV2 } from 'element-plus';

interface Props {
  modelValue?: any;
  beforeSelect?: (value: any, option?: any) => boolean | Promise<boolean>;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: undefined,
  beforeSelect: undefined,
});

const emit = defineEmits<{
  change: [value: any, option?: any];
  'update:modelValue': [value: any];
}>();

const attrs = useAttrs();
const innerValue = ref(props.modelValue);
const pendingValue = ref<any>(null);
const pendingOption = ref<any>(null);

// 处理 attrs，确保 options 存在（即使为空数组）以满足类型要求
const selectAttrs = computed(() => {
  const result: Record<string, any> = { ...attrs };
  // 如果 attrs 中没有 options，提供一个空数组以满足类型要求
  // 实际使用时，options 会通过 attrs 传入
  if (!('options' in result) || result.options === undefined) {
    result.options = [];
  }
  return result;
});

// 同步外部值变化到内部
watch(
  () => props.modelValue,
  (newVal) => {
    if (innerValue.value !== newVal) {
      innerValue.value = newVal;
    }
  },
);

const handleUpdate = (value: any) => {
  // 如果没有 beforeSelect，直接更新
  if (!props.beforeSelect) {
    innerValue.value = value;
    emit('update:modelValue', value);
    return;
  }

  // 有 beforeSelect 时，先临时更新值（UI 会立即响应）
  // 但先不 emit，等待 change 事件触发后再决定是否真正更新
  innerValue.value = value;
  pendingValue.value = value;
};

const handleChange = async (value: any, option?: any) => {
  // 如果没有 beforeSelect，直接触发 change 事件
  if (!props.beforeSelect) {
    emit('update:modelValue', value);
    emit('change', value, option);
    return;
  }

  // 调用 beforeSelect 钩子
  await processBeforeSelect(value, option);
};

const processBeforeSelect = async (value: any, option?: any) => {
  if (!props.beforeSelect) {
    return;
  }

  try {
    const result = await props.beforeSelect(value, option);
    // 如果返回 false，不更新值，恢复原值
    if (result === false) {
      innerValue.value = props.modelValue;
      pendingValue.value = null;
      pendingOption.value = null;
      return;
    }
    // 返回 true 或 undefined，正常更新
    innerValue.value = value;
    emit('update:modelValue', value);
    emit('change', value, option);
    pendingValue.value = null;
    pendingOption.value = null;
  } catch (error) {
    // 如果 beforeSelect 抛出异常，也不更新值
    console.warn('beforeSelect hook error:', error);
    innerValue.value = props.modelValue;
    pendingValue.value = null;
    pendingOption.value = null;
  }
};
</script>

<template>
  <ElSelectV2
    v-model="innerValue"
    v-bind="selectAttrs as any"
    @update:model-value="handleUpdate"
    @change="handleChange"
  >
    <template v-for="(_, slot) in $slots" #[slot]="scope">
      <slot :name="slot" v-bind="scope"></slot>
    </template>
  </ElSelectV2>
</template>
