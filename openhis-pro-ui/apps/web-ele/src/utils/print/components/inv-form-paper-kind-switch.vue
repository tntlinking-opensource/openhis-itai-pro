<!--
  库房单据纸张分段开关（预览顶栏）。
-->
<script setup lang="ts">
import type { InvFormPaperKind } from '../form-print-preference';

import {
  INV_FORM_PAPER_KIND,
  INV_FORM_PAPER_KIND_LABELS,
  normalizeInvFormPaperKind,
} from '../form-print-preference';

import './inv-form-paper-kind.css';

defineOptions({ name: 'InvFormPaperKindSwitch' });

interface Props {
  modelValue?: InvFormPaperKind;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: INV_FORM_PAPER_KIND.triple,
});

const emit = defineEmits<{
  'update:modelValue': [kind: InvFormPaperKind];
}>();

const options: Array<{ kind: InvFormPaperKind; label: string }> = [
  { kind: INV_FORM_PAPER_KIND.triple, label: INV_FORM_PAPER_KIND_LABELS.triple },
  { kind: INV_FORM_PAPER_KIND.a4, label: INV_FORM_PAPER_KIND_LABELS.a4 },
];

/** 点选纸张并回写 v-model */
function handleSelect(kind: InvFormPaperKind): void {
  const next = normalizeInvFormPaperKind(kind);
  if (next === props.modelValue) {
    return;
  }
  emit('update:modelValue', next);
}
</script>

<template>
  <div class="inv-paper-switch" role="radiogroup" aria-label="纸张">
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
