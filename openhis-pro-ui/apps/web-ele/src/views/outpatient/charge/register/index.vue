<script setup lang="ts">
import type { RegisterHistoryExpose } from './services';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import History from './components/history.vue';
import RegisterLeftPanel from './components/register-left-panel.vue';

defineOptions({
  name: 'ChargeOpRegister',
});

const historyRef = ref<null | RegisterHistoryExpose>(null);

/**
 * 左侧挂号结算成功后，刷新右侧就诊记录。
 */
async function handleRegisterSuccess() {
  await historyRef.value?.refresh();
}
</script>

<template>
  <Page>
    <div class="flex h-full min-h-0 flex-1 flex-row gap-2 !overflow-hidden !p-2">
      <RegisterLeftPanel @register-success="handleRegisterSuccess" />
      <History ref="historyRef" />
    </div>
  </Page>
</template>
