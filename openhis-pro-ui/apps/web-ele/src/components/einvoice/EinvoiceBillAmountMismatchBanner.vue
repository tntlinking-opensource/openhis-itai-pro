<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@zxplat/access';

import { ElAlert, ElButton, ElMessage } from 'element-plus';

import { getDocumentBriefByPaymentIdApi } from '#/api/einvoice-boss/document';
import { formatMoneyDisplay, sumMoneyThenSettle } from '#/utils/format/money-scale';
import { isEinvoiceUiEnabled } from '#/utils/project-optional-modules';

const props = defineProps<{
  lineAmounts?: Array<number | string | null | undefined>;
  paymentAmount?: number | string | null;
  paymentId?: string;
}>();

const emit = defineEmits<{
  focusAdjust: [];
}>();

const router = useRouter();
const { hasAccessByCodes } = useAccess();
const einvoiceUiEnabled = isEinvoiceUiEnabled();

const canViewEinvoice = computed(
  () =>
    einvoiceUiEnabled &&
    hasAccessByCodes(['einvoice-boss.document.view', 'einvoice-boss.document.adjust']),
);

const itemSum = computed(() => sumMoneyThenSettle(props.lineAmounts ?? []));

const payAmt = computed(() => Number(props.paymentAmount ?? 0));

const visible = computed(() => {
  if (!canViewEinvoice.value) {
    return false;
  }
  return Math.abs(itemSum.value - payAmt.value) >= 0.005;
});

const documentId = ref('');

async function loadDocumentBrief() {
  documentId.value = '';
  if (!props.paymentId) {
    return;
  }
  try {
    const brief = await getDocumentBriefByPaymentIdApi(props.paymentId);
    documentId.value = brief?.documentId ? String(brief.documentId) : '';
  } catch {
    documentId.value = '';
  }
}

watch(
  () => props.paymentId,
  () => {
    void loadDocumentBrief();
  },
  { immediate: true },
);

function handleFocusAdjust() {
  emit('focusAdjust');
}

function handleViewEinvoice() {
  if (!documentId.value) {
    ElMessage.warning('电子发票尚未生成');
    return;
  }
  router.push(`/finance/einvoice/document/${documentId.value}/amount`);
}
</script>

<template>
  <ElAlert
    v-if="visible"
    class="mb-3"
    type="warning"
    show-icon
    :closable="false"
    :title="`账单明细合计 ${formatMoneyDisplay(itemSum)} 元，与支付金额 ${formatMoneyDisplay(payAmt)} 元不一致，请微调后再开票。`"
  >
    <div class="mt-2 flex flex-wrap gap-2">
      <ElButton size="small" @click="handleFocusAdjust">去微调账单</ElButton>
      <ElButton size="small" type="primary" plain @click="handleViewEinvoice">
        查看电子发票
      </ElButton>
    </div>
  </ElAlert>
</template>
