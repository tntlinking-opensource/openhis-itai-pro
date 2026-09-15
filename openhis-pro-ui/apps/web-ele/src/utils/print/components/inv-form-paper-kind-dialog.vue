<!--
  库房单据打印前选择纸张：三联单（针式连续纸，含整张/二等分/三等分）或 A4。
-->
<script setup lang="ts">
import type { InvFormPaperKind, InvFormTripleSpec } from '../form-print-preference';

import { ref, watch } from 'vue';

import { ElButton, ElDialog } from 'element-plus';

import {
  INV_FORM_PAPER_KIND,
  INV_FORM_TRIPLE_SPEC,
  normalizeInvFormPaperKind,
  normalizeInvFormTripleSpec,
} from '../form-print-preference';
import InvFormPaperKindCards from './inv-form-paper-kind-cards.vue';

defineOptions({ name: 'InvFormPaperKindDialog' });

interface Props {
  confirmText?: string;
  initialPaperKind?: InvFormPaperKind;
  initialTripleSpec?: InvFormTripleSpec;
  title?: string;
  visible?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  confirmText: '确定',
  initialPaperKind: INV_FORM_PAPER_KIND.triple,
  initialTripleSpec: INV_FORM_TRIPLE_SPEC.third,
  title: '选择纸张',
  visible: true,
});

const emit = defineEmits<{
  cancel: [];
  confirm: [payload: { paperKind: InvFormPaperKind; tripleSpec: InvFormTripleSpec }];
}>();

const dialogVisible = ref(props.visible);
const paperKind = ref<InvFormPaperKind>(normalizeInvFormPaperKind(props.initialPaperKind));
const tripleSpec = ref<InvFormTripleSpec>(normalizeInvFormTripleSpec(props.initialTripleSpec));

/** 把外部初始纸张同步到表单 */
function syncFormFromProps(): void {
  paperKind.value = normalizeInvFormPaperKind(props.initialPaperKind);
  tripleSpec.value = normalizeInvFormTripleSpec(props.initialTripleSpec);
}

/** 关闭并取消 */
function handleCancel(): void {
  dialogVisible.value = false;
  emit('cancel');
}

/** 提交所选纸张 */
function handleConfirm(): void {
  dialogVisible.value = false;
  emit('confirm', { paperKind: paperKind.value, tripleSpec: tripleSpec.value });
}

watch(
  () => props.visible,
  (value) => {
    dialogVisible.value = value;
    if (value) {
      syncFormFromProps();
    }
  },
);
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="title"
    width="520px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @close="handleCancel"
  >
    <InvFormPaperKindCards v-model="paperKind" v-model:triple-spec="tripleSpec" />
    <template #footer>
      <ElButton @click="handleCancel">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">{{ confirmText }}</ElButton>
    </template>
  </ElDialog>
</template>
