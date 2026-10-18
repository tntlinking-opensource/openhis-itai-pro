<script setup lang="ts">
import type { OrderTemplateTreeNode } from '../services/order-template.service';

import type { OrderTemplateHeaderItem } from '#/api/basic/order-template';

import { computed, onActivated, onDeactivated, ref, watch } from 'vue';

import {
  ElButton,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElScrollbar,
  ElTooltip,
  ElTree,
} from 'element-plus';

import {
  changeOrderTemplateLifecycleApi,
  ORDER_TEMPLATE_KIND_GENERAL,
  ORDER_TEMPLATE_LIFECYCLE_DELETE,
  ORDER_TEMPLATE_SOURCE_FROM_ADVICE,
} from '#/api/basic/order-template';
import SidePaneCollapseHandle from '#/components/layouts/side-pane-collapse-handle.vue';
import { DOCTOR_STATION_SIDEBAR_WIDTH_PX } from '#/constants/layout/viewport-breakpoints';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  buildOrderTemplateTreeFromGroups,
  DOCTOR_STATION_EXCLUDED_BINDING_SCOPES,
  fetchMergedDoctorStationOrderTemplateGroups,
  findOrderTemplateHeaderInGroups,
} from '../services/order-template.service';
import EditOrderTemplateDialog from './edit-order-template-dialog.vue';

defineOptions({ name: 'OrderTemplateSidePanel' });

interface Props {
  /**
   * 为 true 时，个人组套节点展示删除（住院医生站开启）。
   * 门诊另开 allowAdviceTemplateManage 时，个人/科室「另存模板」也可删。
   */
  allowPersonalDelete?: boolean;
  /**
   * 为 true 时，个人/科室「另存模板」（FROM_ADVICE）展示编辑、删除（门诊医生站开启）。
   */
  allowAdviceTemplateManage?: boolean;
  encounterId?: string;
  patientId?: string;
  templateKindEnum?: string;
}

const props = withDefaults(defineProps<Props>(), {
  allowPersonalDelete: false,
  allowAdviceTemplateManage: false,
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
/** 正在删除的个人组套 id，避免重复提交 */
const deletingTemplateId = ref('');
/** 编辑弹窗 */
const editDialogOpen = ref(false);
const editingTemplate = ref<null | OrderTemplateHeaderItem>(null);
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

/** 是否医生站另存模板（FROM_ADVICE），维护端组套不可在侧栏改删 */
function isAdviceSavedTemplate(data: OrderTemplateTreeNode) {
  const source = String(data.header?.templateSourceEnum ?? '')
    .trim()
    .toUpperCase();
  return source === ORDER_TEMPLATE_SOURCE_FROM_ADVICE;
}

/** 个人/科室另存模板是否展示编辑 */
function showTemplateEdit(data: OrderTemplateTreeNode) {
  return (
    props.allowAdviceTemplateManage &&
    data.nodeType === 'template' &&
    (data.bindingScope === 'PERSONAL' || data.bindingScope === 'DEPARTMENT') &&
    isAdviceSavedTemplate(data)
  );
}

/** 个人组套删除，或门诊另存模板（个人/科室）删除 */
function showTemplateDelete(data: OrderTemplateTreeNode) {
  if (data.nodeType !== 'template') {
    return false;
  }
  if (props.allowPersonalDelete && data.bindingScope === 'PERSONAL') {
    return true;
  }
  return (
    props.allowAdviceTemplateManage &&
    (data.bindingScope === 'PERSONAL' || data.bindingScope === 'DEPARTMENT') &&
    isAdviceSavedTemplate(data)
  );
}

/** 打开组套编辑弹窗（阻止冒泡以免触发套用） */
function handleEditTemplate(data: OrderTemplateTreeNode) {
  const header =
    data.header ?? findOrderTemplateHeaderInGroups(templateGroups.value, data.templateId ?? '');
  if (!header) {
    ElMessage.warning('未找到组套信息');
    return;
  }
  editingTemplate.value = header;
  editDialogOpen.value = true;
}

/** 编辑保存成功后刷新侧栏 */
function handleEditOrderTemplateSaved() {
  editingTemplate.value = null;
  void reloadTemplateList();
}

/** 删除组套，成功后刷新侧栏 */
async function handleDeleteTemplate(data: OrderTemplateTreeNode) {
  const orderTemplateId = preserveApiIdForRequest(data.templateId ?? data.header?.orderTemplateId);
  if (!orderTemplateId || deletingTemplateId.value) {
    return;
  }
  const name = String(data.header?.name ?? '').trim() || '该组套';
  const scopeLabel = data.bindingScope === 'DEPARTMENT' ? '科室组套' : '个人组套';
  try {
    await ElMessageBox.confirm(`确定删除${scopeLabel}「${name}」吗？`, '确认操作', {
      type: 'warning',
    });
  } catch {
    return;
  }
  deletingTemplateId.value = orderTemplateId;
  try {
    await changeOrderTemplateLifecycleApi({
      orderTemplateId,
      lifecycleActionCode: ORDER_TEMPLATE_LIFECYCLE_DELETE,
    });
    ElMessage.success('删除成功');
    if (selectedNodeId.value === data.id) {
      selectedNodeId.value = '';
      emit('select', null);
    }
    await reloadTemplateList();
  } catch {
    // 请求失败由拦截器提示
  } finally {
    deletingTemplateId.value = '';
  }
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
            <div class="order-template-tree-node flex min-w-0 items-center gap-1 py-0.5">
              <div class="min-w-0 flex-1">
                <ElTooltip
                  v-if="data.nodeType === 'template'"
                  :content="data.tooltip || '暂无说明'"
                  placement="right"
                  effect="dark"
                  :show-after="400"
                  :hide-after="100"
                >
                  <span class="block truncate text-sm text-[var(--el-text-color-regular)]">
                    {{ data.label }}
                  </span>
                </ElTooltip>
                <span
                  v-else
                  class="block truncate text-sm font-medium text-[var(--el-text-color-primary)]"
                >
                  {{ data.label }}
                </span>
              </div>
              <span
                v-if="showTemplateEdit(data) || showTemplateDelete(data)"
                class="order-template-tree-node__actions shrink-0"
                @mousedown.stop
                @click.stop
              >
                <ElButton
                  v-if="showTemplateEdit(data)"
                  link
                  type="primary"
                  class="order-template-tree-node__action-btn"
                  @click="handleEditTemplate(data)"
                >
                  编辑
                </ElButton>
                <ElButton
                  v-if="showTemplateDelete(data)"
                  link
                  type="danger"
                  class="order-template-tree-node__action-btn"
                  :loading="deletingTemplateId === data.templateId"
                  @click="handleDeleteTemplate(data)"
                >
                  删除
                </ElButton>
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

    <EditOrderTemplateDialog
      v-model:open="editDialogOpen"
      :template="editingTemplate"
      @saved="handleEditOrderTemplateSaved"
    />
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

.order-template-tree-node {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.order-template-tree-node__actions {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0;
}

.order-template-tree-node__action-btn {
  height: 24px;
  padding: 0 4px;
  margin-left: 0;
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
