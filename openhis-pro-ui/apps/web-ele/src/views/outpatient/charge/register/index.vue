<script setup lang="ts">
import type { RegisterRightPanelExpose } from './services';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { getDateRangeQuick } from '#/utils/format/date-range-quick';

import RegisterLeftPanel from './components/register-left-panel.vue';
import RegisterRightPanel from './components/register-right-panel.vue';

defineOptions({
  name: 'ChargeOpRegister',
});

const historyRef = ref<null | RegisterRightPanelExpose>(null);
const historyDateRange = ref(getDateRangeQuick({ strategy: 'calendar-day' }) as [string, string]);
const historyCollapsed = ref(false);

/** 挂号结算成功或取消核对后，刷新底部历史与统计 */
async function handleRegisterSuccess() {
  await historyRef.value?.refresh();
}

/** 顶栏日期范围变更：同步底部历史查询 */
function handleDateRangeChange(range: [string, string]) {
  historyDateRange.value = range;
}

function handleHistoryCollapsedChange(collapsed: boolean) {
  historyCollapsed.value = collapsed;
}
</script>

<template>
  <Page content-class="register-page-content">
    <div class="register-page" :class="{ 'register-page--history-collapsed': historyCollapsed }">
      <RegisterLeftPanel
        class="register-page__workspace"
        @register-success="handleRegisterSuccess"
        @register-cancelled="handleRegisterSuccess"
      />
      <RegisterRightPanel
        ref="historyRef"
        class="register-page__history"
        :date-range="historyDateRange"
        @date-range-change="handleDateRangeChange"
        @collapsed-change="handleHistoryCollapsedChange"
      />
    </div>
  </Page>
</template>

<style scoped>
.register-page {
  display: grid;
  grid-template-rows: 55fr 45fr;
  gap: 8px;
  height: 100%;
  min-height: 0;
  padding: 8px;
  overflow: hidden;
}

.register-page--history-collapsed {
  grid-template-rows: 1fr auto;
}

.register-page__workspace,
.register-page__history {
  min-height: 0;
  overflow: hidden;
}

.register-page--history-collapsed .register-page__history {
  overflow: visible;
}

/* 窄屏（<1680，含 1600）：压缩上方、给历史列表更多高度 */
@media (max-width: 1679px) {
  .register-page {
    grid-template-rows: auto minmax(320px, 1fr);
    gap: 6px;
    padding: 4px;
  }

  .register-page--history-collapsed {
    grid-template-rows: 1fr auto;
  }

  .register-page__workspace {
    height: auto;
    overflow: visible;
  }

  .register-page__workspace :deep(.reg-bill-form-area),
  .register-page__workspace :deep(.register-workspace-layout) {
    height: auto;
  }
}
</style>

<style>
@media (max-width: 1679px) {
  .register-page-content {
    padding: 4px !important;
    padding-bottom: 0 !important;
  }
}
</style>
