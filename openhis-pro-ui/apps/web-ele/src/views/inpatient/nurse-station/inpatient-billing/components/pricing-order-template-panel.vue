<script setup lang="ts">
import type {
  OrderTemplateBindingScope,
  OrderTemplateTreeNode,
} from '#/views/outpatient/doctor-station/advice/services/order-template.service';

import type { OrderTemplateHeaderItem } from '#/api/basic/order-template';

import { computed, ref, watch } from 'vue';

import { ElInput, ElScrollbar, ElTooltip, ElTree } from 'element-plus';

import { buildOrderTemplateTreeFromGroups } from '#/views/outpatient/doctor-station/advice/services/order-template.service';

import {
  fetchNurseStationPricingOrderTemplateGroups,
  findPricingOrderTemplateHeaderInGroups,
} from '../services/pricing-order-template-apply.service';

defineOptions({ name: 'PricingOrderTemplatePanel' });

/** 划价组套不展示协定处方 */
const PRICING_EXCLUDED_BINDING_SCOPES: OrderTemplateBindingScope[] = ['AGREEMENT'];

interface Props {
  /** 划价弹窗打开时为 true，触发加载组套列表 */
  active?: boolean;
  applying?: boolean;
  disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  active: false,
  applying: false,
  disabled: false,
});

const emit = defineEmits<{
  select: [template: OrderTemplateHeaderItem];
}>();

const searchKeyword = ref('');
const selectedNodeId = ref('');
const listLoading = ref(false);
const templateGroups =
  ref<Awaited<ReturnType<typeof fetchNurseStationPricingOrderTemplateGroups>>>();

/** 划价组套树：个人 / 科室 / 全院（不含协定处方） */
const templateTreeData = computed<OrderTemplateTreeNode[]>(() =>
  buildOrderTemplateTreeFromGroups(templateGroups.value, {
    excludeBindingScopes: PRICING_EXCLUDED_BINDING_SCOPES,
  }),
);

let searchTimer: ReturnType<typeof setTimeout> | undefined;

/** 拉取划价组套列表 */
async function reloadTemplateList() {
  listLoading.value = true;
  try {
    templateGroups.value = await fetchNurseStationPricingOrderTemplateGroups(
      searchKeyword.value.trim() || undefined,
    );
  } catch {
    templateGroups.value = undefined;
  } finally {
    listLoading.value = false;
  }
}

/** 搜索关键字变更：防抖刷新 */
function scheduleReloadBySearch() {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    void reloadTemplateList();
  }, 300);
}

/** 点击树节点：选中组套并通知父级 */
function handleTemplateTreeNodeClick(data: OrderTemplateTreeNode) {
  if (props.disabled || props.applying) {
    return;
  }
  if (data.nodeType !== 'template' || !data.templateId) {
    return;
  }
  const header =
    data.header ?? findPricingOrderTemplateHeaderInGroups(templateGroups.value, data.templateId);
  if (!header) {
    return;
  }
  selectedNodeId.value = data.id;
  emit('select', header);
}

/** 取消树高亮（套用完成后由父级可选调用） */
function clearSelection() {
  selectedNodeId.value = '';
}

watch(
  () => props.active,
  (active) => {
    if (!active) {
      selectedNodeId.value = '';
      searchKeyword.value = '';
      templateGroups.value = undefined;
      return;
    }
    searchKeyword.value = '';
    selectedNodeId.value = '';
    void reloadTemplateList();
  },
  { immediate: true },
);

watch(searchKeyword, () => {
  if (!props.active) {
    return;
  }
  scheduleReloadBySearch();
});

defineExpose({ clearSelection, reloadTemplateList });
</script>

<template>
  <section v-loading="listLoading || applying" class="pricing-order-template-panel">
    <header class="pricing-order-template-panel__header">划价组套</header>
    <div class="pricing-order-template-panel__search">
      <ElInput v-model="searchKeyword" placeholder="搜索组套名称" clearable :disabled="disabled" />
    </div>
    <ElScrollbar class="pricing-order-template-panel__tree-wrap">
      <ElTree
        :data="templateTreeData"
        node-key="id"
        default-expand-all
        :expand-on-click-node="false"
        :current-node-key="selectedNodeId"
        class="pricing-order-template-tree"
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
  </section>
</template>

<style scoped>
.pricing-order-template-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.pricing-order-template-panel__header {
  align-items: center;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-light);
  color: var(--el-text-color-primary);
  display: flex;
  flex: 0 0 auto;
  font-size: 14px;
  font-weight: 600;
  justify-content: space-between;
  min-height: 42px;
  padding: 0 10px;
}

.pricing-order-template-panel__search {
  flex: 0 0 auto;
  padding: 8px 10px;
}

.pricing-order-template-panel__tree-wrap {
  flex: 1;
  min-height: 0;
  padding: 0 6px 8px;
}

.pricing-order-template-tree {
  background: transparent;
}

.pricing-order-template-tree :deep(.el-tree-node__content) {
  height: 32px;
}
</style>
