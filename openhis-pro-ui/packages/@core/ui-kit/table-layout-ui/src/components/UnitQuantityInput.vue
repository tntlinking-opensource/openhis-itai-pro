<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { ElInput } from 'element-plus';

const props = withDefaults(
  defineProps<{
    /** 是否禁用 */
    disabled?: boolean;
    /** 是否使用大单位 */
    isUnit?: boolean;
    /** 小单位标签 */
    minUnitLabel?: string;
    /** 总数量（小单位） */
    modelValue?: number | string;
    /** 拆零比 */
    partPercent?: number;
    /** 大单位标签 */
    unitLabel?: string;
  }>(),
  {
    modelValue: 0,
    disabled: false,
    partPercent: 1,
    unitLabel: '',
    minUnitLabel: '',
    isUnit: false,
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: number];
}>();

// 计算大单位和小单位数量
const calculateUnitQuantities = (
  totalQuantity: number | string | undefined,
  partPercent: number,
) => {
  const qty = Number(totalQuantity) || 0;
  const total = Math.abs(qty);
  const integerPart = Math.floor(total / partPercent);
  const decimalPart = total % partPercent;
  return {
    unit: qty < 0 ? -integerPart : integerPart,
    minUnit: qty < 0 ? -decimalPart : decimalPart,
  };
};

const quantities = computed(() => {
  const value = props.modelValue ?? 0;
  if (props.isUnit) {
    return calculateUnitQuantities(value, props.partPercent);
  }
  return {
    unit: 0,
    minUnit: Number(value) || 0,
  };
});

// 使用本地状态来存储输入值，避免计算属性导致的输入问题
const unitInputValue = ref<string>('');
const minUnitInputValue = ref<string>('');

// 同步外部值到本地输入值
watch(
  () => quantities.value.unit,
  (val) => {
    if (unitInputValue.value !== String(val)) {
      unitInputValue.value = String(val);
    }
  },
  { immediate: true },
);

watch(
  () => quantities.value.minUnit,
  (val) => {
    if (minUnitInputValue.value !== String(val)) {
      minUnitInputValue.value = String(val);
    }
  },
  { immediate: true },
);

// 处理大单位输入变化
const handleUnitInput = (value: string) => {
  unitInputValue.value = value;
  const unitQty = Number(value) || 0;
  const minUnitQty = Number(minUnitInputValue.value) || 0;
  const newTotalQuantity = unitQty * props.partPercent + minUnitQty;
  emit('update:modelValue', newTotalQuantity);
};

// 处理小单位输入变化
const handleMinUnitInput = (value: string) => {
  minUnitInputValue.value = value;
  if (props.isUnit) {
    let minUnitQty = Number(value) || 0;
    let unitQty = Number(unitInputValue.value) || 0;
    if (minUnitQty >= props.partPercent) {
      const extraUnits = Math.floor(minUnitQty / props.partPercent);
      unitQty = unitQty + extraUnits;
      minUnitQty = minUnitQty % props.partPercent;
      // 更新显示值
      unitInputValue.value = String(unitQty);
      minUnitInputValue.value = String(minUnitQty);
    }
    const newTotalQuantity = unitQty * props.partPercent + minUnitQty;
    emit('update:modelValue', newTotalQuantity);
  } else {
    const newTotalQuantity = Number(value) || 0;
    emit('update:modelValue', newTotalQuantity);
  }
};
</script>

<template>
  <div v-if="isUnit" class="flex items-center gap-2">
    <ElInput
      :model-value="unitInputValue"
      :disabled="disabled"
      class="w-[100px]"
      @input="(val: string) => handleUnitInput(val)"
    >
      <template #suffix>{{ unitLabel }}</template>
    </ElInput>
    <ElInput
      :model-value="minUnitInputValue"
      :disabled="disabled"
      class="w-[100px]"
      @input="(val: string) => handleMinUnitInput(val)"
    >
      <template #suffix>{{ minUnitLabel }}</template>
    </ElInput>
  </div>
  <ElInput
    v-else
    :model-value="minUnitInputValue"
    :disabled="disabled"
    class="w-[208px]"
    @input="(val: string) => handleMinUnitInput(val)"
  >
    <template #suffix>{{ minUnitLabel }}</template>
  </ElInput>
</template>
