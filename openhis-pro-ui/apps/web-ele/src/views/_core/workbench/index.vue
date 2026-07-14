<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Search } from '@element-plus/icons-vue';
import { ElEmpty, ElInput, ElMessage } from 'element-plus';

import WorkbenchCustomSection from './components/workbench-custom-section.vue';
import WorkbenchGroupSection from './components/workbench-group-section.vue';
import WorkbenchInventoryWarningSection from './components/workbench-inventory-warning-section.vue';
import WorkbenchWorkloadReportSection from './components/workbench-workload-report-section.vue';
import { useWorkbenchCustomShortcuts } from './use-workbench-custom-shortcuts';
import { useWorkbenchShortcuts } from './use-workbench-shortcuts';

defineOptions({
  name: 'WorkbenchHome',
});

/** 自定义快捷入口（暂隐藏，改 true 可恢复） */
const showCustomShortcuts = false;

const router = useRouter();

const searchKeyword = ref('');

const searchKeywordComputed = computed(() => searchKeyword.value);
const { filteredGroups, shortcutCandidates, totalShortcutCount } =
  useWorkbenchShortcuts(searchKeywordComputed);

const {
  addableCandidates,
  addCustomShortcut,
  canAddMore,
  customItems,
  emptySlotCount,
  removeCustomShortcut,
} = useWorkbenchCustomShortcuts(shortcutCandidates);

/** 点击快捷入口跳转对应业务页面 */
function handleNavigate(path: string) {
  void router.push(path);
}

/** 将所选菜单写入本地自定义快捷入口 */
function handleAddCustomShortcut(routeName: string) {
  const added = addCustomShortcut(routeName);
  if (added) {
    ElMessage.success('已添加快捷入口');
    return;
  }
  ElMessage.warning('无法添加该菜单');
}

/** 从本地自定义快捷入口移除菜单 */
function handleRemoveCustomShortcut(routeName: string) {
  removeCustomShortcut(routeName);
  ElMessage.success('已移除快捷入口');
}
</script>

<template>
  <Page auto-content-height content-class="workbench-home__content" header-class="!hidden">
    <div class="workbench-home">
      <div class="workbench-home__toolbar">
        <ElInput
          v-model="searchKeyword"
          class="workbench-home__search"
          clearable
          placeholder="搜索菜单"
          :prefix-icon="Search"
        />
      </div>

      <div class="workbench-home__stats">
        <WorkbenchInventoryWarningSection />
        <WorkbenchWorkloadReportSection />
      </div>

      <WorkbenchCustomSection
        v-if="showCustomShortcuts"
        class="workbench-home__custom"
        :addable-candidates="addableCandidates"
        :can-add-more="canAddMore"
        :custom-items="customItems"
        :empty-slot-count="emptySlotCount"
        @add="handleAddCustomShortcut"
        @navigate="handleNavigate"
        @remove="handleRemoveCustomShortcut"
      />

      <div v-if="filteredGroups.length > 0" class="workbench-home__groups">
        <WorkbenchGroupSection
          v-for="group in filteredGroups"
          :key="group.config.id"
          :group="group"
          @navigate="handleNavigate"
        />
      </div>

      <ElEmpty
        v-else
        class="workbench-home__empty"
        :description="totalShortcutCount > 0 ? '未找到匹配的菜单' : '暂无可用快捷入口'"
      />
    </div>
  </Page>
</template>

<style scoped>
.workbench-home {
  display: flex;
  height: 100%;
  flex-direction: column;
  padding: 20px 24px;
  background: #f5f7fa;
  overflow-y: auto;
}

.workbench-home__toolbar {
  flex-shrink: 0;
  margin-bottom: 16px;
}

.workbench-home__stats {
  display: grid;
  flex-shrink: 0;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
  margin-bottom: 18px;
}

.workbench-home__custom {
  flex-shrink: 0;
  margin-bottom: 22px;
}

.workbench-home__search {
  width: min(360px, 100%);
}

.workbench-home__search :deep(.el-input__wrapper) {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: none;
}

.workbench-home__groups {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.workbench-home__empty {
  flex: 1;
  padding: 48px 0;
}
</style>

<style>
/* 首页贴边：去掉 Page 卡片底/顶距，沿用布局 content 区背景 */
.bg-background-card:has(.workbench-home__content) {
  margin-top: 0 !important;
  background: transparent !important;
}

.workbench-home__content {
  height: 100%;
  padding: 0 !important;
  overflow: hidden !important;
  background: transparent !important;
}
</style>
