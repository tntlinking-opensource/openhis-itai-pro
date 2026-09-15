<!--
  库房单据纸张卡片：三联单 / A4；选三联单时再选整张 / 二等分 / 三等分。
-->
<script setup lang="ts">
import type { InvFormPaperKind, InvFormTripleSpec } from '../form-print-preference';

import { computed } from 'vue';

import {
  INV_FORM_PAPER_KIND,
  INV_FORM_PAPER_KIND_LABELS,
  INV_FORM_TRIPLE_SPEC,
  isInvFormTriplePaper,
  normalizeInvFormPaperKind,
  normalizeInvFormTripleSpec,
} from '../form-print-preference';
import InvFormTripleSpecSwitch from './inv-form-triple-spec-switch.vue';

import './inv-form-paper-kind.css';

defineOptions({ name: 'InvFormPaperKindCards' });

interface Props {
  modelValue?: InvFormPaperKind;
  tripleSpec?: InvFormTripleSpec;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: INV_FORM_PAPER_KIND.triple,
  tripleSpec: INV_FORM_TRIPLE_SPEC.third,
});

const emit = defineEmits<{
  'update:modelValue': [kind: InvFormPaperKind];
  'update:tripleSpec': [kind: InvFormTripleSpec];
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
    hint: '针式连续纸，可选整张、二等分或三等分',
    sheetClass: 'is-triple',
  },
  {
    kind: INV_FORM_PAPER_KIND.a4,
    label: INV_FORM_PAPER_KIND_LABELS.a4,
    hint: 'A4 纵向，适合激光/喷墨打印机',
    sheetClass: 'is-a4',
  },
];

const showSpecSwitch = computed(() => isInvFormTriplePaper(props.modelValue));

/** 选中一张纸并回写 v-model */
function handleSelect(kind: InvFormPaperKind): void {
  emit('update:modelValue', normalizeInvFormPaperKind(kind));
}

/** 选中三联单规格并回写 */
function handleSpecSelect(kind: InvFormTripleSpec): void {
  emit('update:tripleSpec', normalizeInvFormTripleSpec(kind));
}
</script>

<template>
  <div class="inv-paper-kind-block">
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
    <div v-if="showSpecSwitch" class="inv-paper-spec-block">
      <div class="inv-paper-spec-label">规格</div>
      <InvFormTripleSpecSwitch :model-value="tripleSpec" @update:model-value="handleSpecSelect" />
    </div>
  </div>
</template>
