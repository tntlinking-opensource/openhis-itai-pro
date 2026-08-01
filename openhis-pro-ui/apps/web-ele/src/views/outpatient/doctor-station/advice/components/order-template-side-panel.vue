<script setup lang="ts">
import type { OrderTemplateTreeNode } from '../services/order-template.service';

import type { OrderTemplateHeaderItem } from '#/api/basic/order-template';

import { computed, onActivated, onDeactivated, ref, watch } from 'vue';

import { ElInput, ElScrollbar, ElTooltip, ElTree } from 'element-plus';

import { ORDER_TEMPLATE_KIND_GENERAL } from '#/api/basic/order-template';
import SidePaneCollapseHandle from '#/components/layouts/side-pane-collapse-handle.vue';
import { DOCTOR_STATION_SIDEBAR_WIDTH_PX } from '#/constants/layout/viewport-breakpoints';

import {
  buildOrderTemplateTreeFromGroups,
  DOCTOR_STATION_EXCLUDED_BINDING_SCOPES,
  fetchMergedDoctorStationOrderTemplateGroups,
  findOrderTemplateHeaderInGroups,
} from '../services/order-template.service';

defineOptions({ name: 'OrderTemplateSidePanel' });

interface Props {
  encounterId?: string;
  patientId?: string;
  templateKindEnum?: string;
}

const props = withDefaults(defineProps<Props>(), {
  encounterId: '',
  patientId: '',
  templateKindEnum: ORDER_TEMPLATE_KIND_GENERAL,
});

const emit = defineEmits<{
  select: [template: null | OrderTemplateHeaderItem];
}>();

/** 展开态宽度（与医生站侧栏常量一致） */
const EXPANDED_WIDTH = DOCTOR_STATION_SIDEBAR_WIDTH_PX.orderTemplate.default;
/** 收起后仅留把手宽度，避免被父级 overflow 裁切 */
const COLLAPSED_WIDTH = 28;

const searchKeyword = ref('');
const selectedNodeId = ref('');
const listLoading = ref(false);
/** 小屏可收起组套树，把横向空间让给医嘱表格 */
const isCollapsed = ref(false);
const templateGroups =
  ref<Awaited<ReturnType<typeof fetchMergedDoctorStationOrderTemplateGroups>>>();

/** 切换组套侧栏收起/展开 */
function toggleCollapsed() {
  isCollapsed.value = !isCollapsed.value;
}

/** 左侧树：协定 / 个人 / 科室 / 全院（含维护组套 + 另存模板） */
const templateTreeData = computed<OrderTemplateTreeNode[]>(() =>
  buildOrderTemplateTreeFromGroups(templateGroups.value, {
    excludeBindingScopes: DOCTOR_STATION_EXCLUDED_BINDING_SCOPES,
  }),
);

const canLoadList = computed(() => !!props.encounterId?.trim() && !!props.patientId?.trim());

/** 并行拉取维护组套与另存模板并合并 */
async function reloadTemplateList() {
  if (!canLoadList.value) {
    templateGroups.value = undefined;
    return;
  }
  listLoading.value = true;
  try {
    templateGroups.value = await fetchMergedDoctorStationOrderTemplateGroups({
      encounterId: props.encounterId ?? '',
      patientId: props.patientId ?? '',
      templateKindEnum: props.templateKindEnum,
      searchKey: searchKeyword.value.trim() || undefined,
    });
  } catch {
    templateGroups.value = undefined;
  } finally {
    listLoading.value = false;
  }
}

let searchTimer: ReturnType<typeof setTimeout> | undefined;

/** 父级 KeepAlive 激活时才拉组套，避免非当前 Tab 换患者重复请求 */
const isPanelActive = ref(false);

onActivated(() => {
  isPanelActive.value = true;
  void reloadTemplateList();
});

onDeactivated(() => {
  isPanelActive.value = false;
});

/** 搜索关键字变更：防抖刷新列表 */
function scheduleReloadBySearch() {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    void reloadTemplateList();
  }, 300);
}

watch(
  () => [props.encounterId, props.patientId, props.templateKindEnum],
  () => {
    if (!isPanelActive.value) return;
    selectedNodeId.value = '';
    emit('select', null);
    void reloadTemplateList();
  },
);

watch(searchKeyword, () => {
  if (!isPanelActive.value) return;
  scheduleReloadBySearch();
});

/** 点击树节点：选中组套并通知父级 */
function handleTemplateTreeNodeClick(data: OrderTemplateTreeNode) {
  if (data.nodeType !== 'template' || !data.templateId) {
    return;
  }
  const header =
    data.header ?? findOrderTemplateHeaderInGroups(templateGroups.value, data.templateId);
  if (!header) {
    return;
  }
  selectedNodeId.value = data.id;
  emit('select', header);
}

/** 供父级取消选中时同步树高亮 */
function clearSelection() {
  selectedNodeId.value = '';
}

defineExpose({ clearSelection, reloadTemplateList });
</script>

<template>
  <div
    class="order-template-side-panel-root"
    :class="{ 'is-collapsed': isCollapsed }"
    :style="{ width: `${isCollapsed ? COLLAPSED_WIDTH : EXPANDED_WIDTH}px` }"
  >
    <aside v-show="!isCollapsed" v-loading="listLoading" class="order-template-side-panel">
      <ElInput
        v-model="searchKeyword"
        placeholder="搜索组套/模板"
        clearable
        :disabled="!canLoadList"
        class="order-template-side-panel__search"
      />

      <ElScrollbar class="order-template-side-panel__tree-wrap">
        <div
          v-if="!canLoadList"
          class="px-2 py-4 text-center text-xs text-[var(--el-text-color-secondary)]"
        >
          请先选择患者与就诊
        </div>
        <ElTree
          v-else
          :data="templateTreeData"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          :current-node-key="selectedNodeId"
          class="order-template-tree"
          @node-click="handleTemplateTreeNodeClick"
        >
          <template #default="{ data }">
            <div class="flex w-full items-center justify-between py-0.5">
              <ElTooltip
                v-if="data.nodeType === 'template'"
                :content="data.tooltip || '暂无说明'"
                placement="right"
                effect="dark"
                :show-after="400"
                :hide-after="100"
              >
                <span class="truncate text-sm text-[var(--el-text-color-regular)]">
                  {{ data.label }}
                </span>
              </ElTooltip>
              <span v-else class="truncate text-sm font-medium text-[var(--el-text-color-primary)]">
                {{ data.label }}
              </span>
            </div>
          </template>
        </ElTree>
      </ElScrollbar>
    </aside>

    <!-- 右缘热区：hover 显示透明竖向把手，垂直居中 -->
    <div class="order-template-side-panel__edge">
      <SidePaneCollapseHandle :collapsed="isCollapsed" @toggle="toggleCollapsed" />
    </div>
  </div>
</template>

<style scoped>
.order-template-side-panel-root {
  position: relative;
  display: flex;
  flex-shrink: 0;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: visible;
  border-right: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-blank);
  box-sizing: border-box;
  transition: width 0.2s ease;
}

.order-template-side-panel-root.is-collapsed {
  border-right-color: transparent;
  background: transparent;
}

.order-template-side-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  min-height: 0;
  padding: 6px;
  box-sizing: border-box;
}

.order-template-side-panel__search {
  flex-shrink: 0;
  margin-bottom: 6px;
}

.order-template-side-panel__tree-wrap {
  flex: 1;
  min-height: 0;
}

.order-template-tree :deep(.el-tree-node__content) {
  height: 32px;
}

.order-template-side-panel__edge {
  position: absolute;
  top: 0;
  right: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  width: 28px;
  height: 100%;
}

.order-template-side-panel-root:hover
  .order-template-side-panel__edge
  :deep(.side-pane-collapse-handle),
.order-template-side-panel-root.is-collapsed
  .order-template-side-panel__edge
  :deep(.side-pane-collapse-handle),
.order-template-side-panel__edge:hover :deep(.side-pane-collapse-handle),
.order-template-side-panel__edge :deep(.side-pane-collapse-handle:focus-visible) {
  opacity: 1;
  pointer-events: auto;
}
</style>
