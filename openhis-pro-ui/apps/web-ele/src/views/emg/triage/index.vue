<script setup lang="ts">
import { ref } from 'vue';

import { ElTabPane, ElTabs } from 'element-plus';

import KnowledgeBaseManagement from '../knowledge/index.vue';
import QueuePanel from './queue-panel.vue';
import Workbench from './workbench.vue';

defineOptions({
  name: 'EmgTriageIndex',
});

const activeTab = ref('workbench');
const queueRefreshToken = ref(0);

// 分诊台保存后刷新候诊队列，保证切换标签时数据是最新的。
function onTriageSaved() {
  queueRefreshToken.value += 1;
}
</script>

<template>
  <ElTabs v-model="activeTab" class="triage-tabs">
    <ElTabPane label="急诊分诊" name="workbench" lazy>
      <Workbench @saved="onTriageSaved" />
    </ElTabPane>
    <ElTabPane label="候诊队列" name="queue" lazy>
      <QueuePanel :refresh-token="queueRefreshToken" />
    </ElTabPane>
    <ElTabPane label="知识库管理" name="knowledge" lazy>
      <KnowledgeBaseManagement />
    </ElTabPane>
  </ElTabs>
</template>

<style scoped>
.triage-tabs {
  height: 100%;
  min-height: 0;
}

.triage-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  margin-top: 0;
  background: #fff;
  overflow: hidden;
}

.triage-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
  background: #fff;
}

.triage-tabs :deep(.el-tabs__nav-wrap) {
  justify-content: center;
}

.triage-tabs :deep(.el-tabs__nav-scroll) {
  display: flex;
  justify-content: center;
}

.triage-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.triage-tabs :deep(.el-tab-pane) {
  height: 100%;
  background: #fff;
  margin-top: 0;
  padding-top: 0;
  min-height: 0;
  overflow: auto;
}
</style>
