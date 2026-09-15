<!--
  三联单规格分段开关：整张 / 二等分 / 三等分。
-->
<script setup lang="ts">
import type { InvFormTripleSpec } from '../form-print-preference';

import {
  INV_FORM_TRIPLE_SPEC,
  INV_FORM_TRIPLE_SPEC_LABELS,
  normalizeInvFormTripleSpec,
} from '../form-print-preference';

import './inv-form-paper-kind.css';

defineOptions({ name: 'InvFormTripleSpecSwitch' });

interface Props {
  modelValue?: InvFormTripleSpec;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: INV_FORM_TRIPLE_SPEC.third,
});

const emit = defineEmits<{
  'update:modelValue': [kind: InvFormTripleSpec];
}>();

const options: Array<{ kind: InvFormTripleSpec; label: string }> = [
  {
    kind: INV_FORM_TRIPLE_SPEC.full,
    label: INV_FORM_TRIPLE_SPEC_LABELS[INV_FORM_TRIPLE_SPEC.full],
  },
  {
    kind: INV_FORM_TRIPLE_SPEC.half,
    label: INV_FORM_TRIPLE_SPEC_LABELS[INV_FORM_TRIPLE_SPEC.half],
  },
  {
    kind: INV_FORM_TRIPLE_SPEC.third,
    label: INV_FORM_TRIPLE_SPEC_LABELS[INV_FORM_TRIPLE_SPEC.third],
  },
];

/** 点选规格并回写 v-model */
function handleSelect(kind: InvFormTripleSpec): void {
  const next = normalizeInvFormTripleSpec(kind);
  if (next === props.modelValue) {
    return;
  }
  emit('update:modelValue', next);
}
</script>

<template>
  <div class="inv-paper-switch" role="radiogroup" aria-label="三联单规格">
    <button
      v-for="item in options"
      :key="item.kind"
      type="button"
      class="inv-paper-switch-item"
      :class="{ 'is-active': modelValue === item.kind }"
      role="radio"
      :aria-checked="modelValue === item.kind"
      @click="handleSelect(item.kind)"
    >
      {{ item.label }}
    </button>
  </div>
</template>
