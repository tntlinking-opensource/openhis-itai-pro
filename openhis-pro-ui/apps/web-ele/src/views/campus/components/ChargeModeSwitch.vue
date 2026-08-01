<script setup lang="ts">
import type { GfChargeMode } from '#/api/campus/types';

import { computed } from 'vue';

import { GF_CHARGE_MODE_OPTIONS } from '#/api/campus/types';

import { ElSegmented } from 'element-plus';

/** 支持空字符串表示「全部」 */
const mode = defineModel<GfChargeMode | ''>({ required: true });

const emit = defineEmits<{ change: [GfChargeMode | ''] }>();

const props = withDefaults(
  defineProps<{
    /** 是否显示「全部」选项（审批列表等） */
    allowAll?: boolean;
  }>(),
  { allowAll: false },
);

const options = computed(() => [
  ...(props.allowAll ? [{ label: '全部', value: '' as const }] : []),
  ...GF_CHARGE_MODE_OPTIONS.map((o) => ({ label: o.label, value: o.value })),
]);

function onChange(value: GfChargeMode | '') {
  emit('change', value);
}
</script>

<template>
  <ElSegmented v-model="mode" :options="options" @change="onChange" />
</template>
