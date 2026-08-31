<!--
  进货等编辑态追溯码：输入框 + 常显个数（随输入实时更新，避免逗号串截断误判成 1 个）。
-->
<script setup lang="ts">
import { computed } from 'vue';

import { ElInput } from 'element-plus';

import { splitTraceNoCommaString } from '#/utils/trace';

defineOptions({ name: 'InventoryTraceNoEditCell' });

const props = withDefaults(
  defineProps<{
    modelValue?: string;
    maxlength?: number;
    clearable?: boolean;
  }>(),
  {
    modelValue: '',
    maxlength: undefined,
    clearable: false,
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();

const codeCount = computed(() => splitTraceNoCommaString(props.modelValue).length);
</script>

<template>
  <div class="inventory-trace-no-edit flex w-full min-w-0 items-center gap-1">
    <ElInput
      class="!w-full min-w-0"
      :model-value="props.modelValue"
      :maxlength="props.maxlength"
      :clearable="props.clearable"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <span
      v-if="codeCount > 0"
      class="inventory-trace-no-edit__count shrink-0 whitespace-nowrap text-xs tabular-nums"
      :title="`共 ${codeCount} 个追溯码（英文逗号分隔）`"
    >
      {{ codeCount }}个
    </span>
  </div>
</template>

<style scoped>
.inventory-trace-no-edit__count {
  font-weight: 600;
  color: var(--el-color-primary);
}
</style>
