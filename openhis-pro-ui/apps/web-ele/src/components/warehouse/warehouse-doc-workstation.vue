<!--
  库房单据工作站壳：顶栏 + 左检索/单据队列 + 右主内容。
  业务页通过 toolbar / aside-filter / aside-queue / 默认插槽填入具体内容。
-->
<script setup lang="ts">
import { computed } from 'vue';

import { Page } from '@zxplat/common-ui';

import { ElCard } from 'element-plus';

import { useCompactViewport } from '#/composables/use-compact-viewport';
import { INVENTORY_DOC_ASIDE_WIDTH_PX } from '#/constants/layout/viewport-breakpoints';

const props = withDefaults(
  defineProps<{
    /** 左侧栏宽度；不传则随视口 280/248 */
    asideWidth?: string;
    /** 是否展示左侧栏（队列）；财务审批只读入口可关 */
    showAside?: boolean;
    /** 是否展示顶栏工具区（toolbar 插槽） */
    showToolbar?: boolean;
    /** 是否展示左侧检索区（aside-filter 插槽） */
    showAsideFilter?: boolean;
    /** 队列区域上方标题；传空字符串则不展示分割条 */
    queueTitle?: string;
    /** 左侧紧凑模式：减少页边与侧栏内边距、队列区无额外水平 padding */
    asideDense?: boolean;
    /** 透传到 Page 根节点（如 !mt-0） */
    pageClass?: string;
    /** 透传到 Page 内容区 class */
    pageContentClass?: string;
  }>(),
  {
    showAside: true,
    showToolbar: true,
    showAsideFilter: true,
    queueTitle: '单据队列',
    asideDense: false,
    pageContentClass: 'flex min-h-0 flex-col !overflow-hidden !p-0',
  },
);

const { pickPreset } = useCompactViewport();
/** 未显式传 asideWidth 时：≥1680 用 280，窄屏 248 */
const compactAsideWidthPx = pickPreset(INVENTORY_DOC_ASIDE_WIDTH_PX);

const asideStyle = computed(() => ({
  width: props.asideWidth ?? `${compactAsideWidthPx.value}px`,
}));

const workstationRootClass = computed(() =>
  props.asideDense
    ? 'warehouse-doc-workstation warehouse-doc-workstation--dense flex h-full min-h-0 flex-1 flex-col gap-1 !overflow-hidden px-0 pb-0'
    : 'warehouse-doc-workstation flex h-full min-h-0 flex-1 flex-col gap-2 !overflow-hidden px-2 pb-2',
);

const mainRowClass = computed(() =>
  props.asideDense
    ? 'flex min-h-0 flex-1 flex-row gap-1 overflow-hidden'
    : 'flex min-h-0 flex-1 flex-row gap-2 overflow-hidden',
);

const asideShellClass = computed(() =>
  props.asideDense
    ? 'flex h-full min-h-0 shrink-0 flex-col overflow-hidden border-r border-[var(--el-border-color-lighter)] pr-0'
    : 'flex h-full min-h-0 shrink-0 flex-col overflow-hidden border-r border-[var(--el-border-color-lighter)] pr-2',
);

const asideCardBodyClass = computed(() =>
  props.asideDense
    ? 'flex min-h-0 flex-1 flex-col !gap-0 overflow-hidden !p-0'
    : 'flex min-h-0 flex-1 flex-col gap-2 overflow-hidden !p-0 !pt-0',
);

const queueScrollClass = computed(() =>
  props.asideDense
    ? 'warehouse-doc-queue-scroll warehouse-doc-queue-scroll--dense min-h-0 flex-1 overflow-y-auto overscroll-contain'
    : 'warehouse-doc-queue-scroll min-h-0 flex-1 overflow-y-auto overscroll-contain px-1 pr-2',
);

const mainPaneClass = computed(() =>
  props.asideDense
    ? 'flex h-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden pl-1'
    : 'flex h-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden pl-2',
);
</script>

<template>
  <Page :class="pageClass" :content-class="pageContentClass">
    <div :class="workstationRootClass">
      <div
        v-if="showToolbar"
        class="warehouse-doc-toolbar mt-2 flex shrink-0 flex-wrap items-center gap-2"
      >
        <slot name="toolbar" />
      </div>

      <div :class="mainRowClass">
        <aside v-if="showAside" :class="asideShellClass" :style="asideStyle">
          <ElCard
            shadow="never"
            class="flex h-full min-h-0 flex-1 flex-col overflow-hidden !border-0 bg-transparent shadow-none"
            :body-class="asideCardBodyClass"
          >
            <div
              v-if="showAsideFilter"
              class="warehouse-doc-aside-filter shrink-0 rounded-md bg-[var(--el-fill-color-blank)] px-2 py-1.5"
            >
              <slot name="aside-filter" />
            </div>

            <div
              v-if="queueTitle"
              class="warehouse-doc-queue-split text-xs font-medium leading-none text-[var(--el-text-color-secondary)]"
            >
              {{ queueTitle }}
            </div>

            <div :class="queueScrollClass">
              <slot name="aside-queue" />
            </div>
          </ElCard>
        </aside>

        <main :class="mainPaneClass">
          <slot />
        </main>
      </div>
    </div>
  </Page>
</template>

<style scoped lang="scss">
.warehouse-doc-aside-filter {
  border: 1px solid var(--el-border-color-extra-light);

  :deep(.el-form-item) {
    margin-bottom: 6px;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }

  /* append 搜索按钮与 small 输入对齐；表单项请加 class="warehouse-doc-aside-search-item" */
  :deep(.warehouse-doc-aside-search-item .el-input-group__append) {
    padding: 0;
  }

  :deep(.warehouse-doc-aside-search-item .el-input-group__append .el-button) {
    margin: 0;
    border-radius: 0 var(--el-border-radius-base) var(--el-border-radius-base) 0;
  }
}

.warehouse-doc-queue-split {
  flex-shrink: 0;
  padding: 6px 4px 4px;
  margin-top: 2px;
  border-top: 1px solid var(--el-border-color-extra-light);
}

.warehouse-doc-queue-scroll--dense {
  padding: 0;
}
</style>
