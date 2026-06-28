<script setup lang="ts">
import { computed } from 'vue';

import { ElButton, ElDialog } from 'element-plus';

export type EinvoiceConfirmAction = 'issueNow' | 'recoverByBusNo' | 'requeue';

const props = defineProps<{
  action: EinvoiceConfirmAction | null;
  busNo?: string;
  open: boolean;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
  confirm: [];
}>();

const title = computed(() => {
  switch (props.action) {
    case 'issueNow':
      return '确认立即开具';
    case 'recoverByBusNo':
      return '确认流水号查票';
    case 'requeue':
      return '确认重新入队';
    default:
      return '确认操作';
  }
});

const bodyLines = computed(() => {
  switch (props.action) {
    case 'issueNow':
      return [
        '将向博思平台发起电子票据开具。',
        '· 请确认金额已在「电子发票金额」页核对无误。',
        '· 若博思侧已存在同流水号票据，重复操作可能导致异常。',
      ];
    case 'recoverByBusNo':
      return [
        `将使用业务流水号 ${props.busNo || '-'} 向博思查询开票结果。`,
        '· 若博思已开票，系统将直接标记为已开具并回写票号。',
        '· 若未开票，请改用「重新入队」或「立即开具」。',
      ];
    case 'requeue':
      return ['将重新加入后台开票队列，不会立即调用博思。'];
    default:
      return [];
  }
});

function close() {
  emit('update:open', false);
}

function confirm() {
  emit('confirm');
}
</script>

<template>
  <ElDialog :model-value="open" :title="title" width="480px" @close="close">
    <div class="space-y-2 text-sm leading-6 text-[var(--el-text-color-regular)]">
      <p v-for="(line, index) in bodyLines" :key="index">{{ line }}</p>
    </div>
    <template #footer>
      <ElButton @click="close">取消</ElButton>
      <ElButton type="primary" @click="confirm">确认</ElButton>
    </template>
  </ElDialog>
</template>
