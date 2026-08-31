<!--
  库房单据纸张卡片：三联单 / A4，供打印弹窗与设置弹窗复用。
-->
<script setup lang="ts">
import type { InvFormPaperKind } from '../form-print-preference';

import {
  INV_FORM_PAPER_KIND,
  INV_FORM_PAPER_KIND_LABELS,
  normalizeInvFormPaperKind,
} from '../form-print-preference';

import './inv-form-paper-kind.css';

defineOptions({ name: 'InvFormPaperKindCards' });

interface Props {
  modelValue?: InvFormPaperKind;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: INV_FORM_PAPER_KIND.triple,
});

const emit = defineEmits<{
  'update:modelValue': [kind: InvFormPaperKind];
}>();

const paperCards: Array<{
  hint: string;
  kind: InvFormPaperKind;
  label: string;
  sheetClass: string;
}> = [
  {
    kind: INV_FORM_PAPER_KIND.triple,
    label: INV_FORM_PAPER_KIND_LABELS.triple,
    hint: '针式连续纸，按一折高度打印',
    sheetClass: 'is-triple',
  },
  {
    kind: INV_FORM_PAPER_KIND.a4,
    label: INV_FORM_PAPER_KIND_LABELS.a4,
    hint: 'A4 纵向，适合激光/喷墨打印机',
    sheetClass: 'is-a4',
  },
];

/** 选中一张纸并回写 v-model */
function handleSelect(kind: InvFormPaperKind): void {
  emit('update:modelValue', normalizeInvFormPaperKind(kind));
}
</script>

<template>
  <div class="inv-paper-cards">
    <button
      v-for="card in paperCards"
      :key="card.kind"
      type="button"
      class="inv-paper-card"
      :class="{ 'is-active': props.modelValue === card.kind }"
      @click="handleSelect(card.kind)"
    >
      <div class="inv-paper-card-thumb">
        <div class="inv-paper-card-sheet" :class="card.sheetClass"></div>
      </div>
      <div class="inv-paper-card-name">{{ card.label }}</div>
      <div class="inv-paper-card-hint">{{ card.hint }}</div>
    </button>
  </div>
</template>
