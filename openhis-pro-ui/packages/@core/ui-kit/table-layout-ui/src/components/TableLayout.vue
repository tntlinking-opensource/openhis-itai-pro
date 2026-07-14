<script setup lang="ts">
import type { FilterExpose } from '../types/Filter.d';
import type { TableLayoutProps, TreeNodeData } from '../types/TableLayout.d';

import { computed, nextTick, onBeforeUnmount, onMounted, ref, useSlots, watch } from 'vue';

import { ArrowLeft, ArrowRight, MoreFilled, Refresh, Search } from '@element-plus/icons-vue';
import { ElTreeV2 } from 'element-plus';

import Filter from './Filter.vue';
import Table from './Table.vue';

defineOptions({
  name: 'TableLayout',
});

const props = withDefaults(defineProps<TableLayoutProps>(), {
  tableData: () => [],
  loading: false,
  total: 0,
  queryParams: () => ({
    pageNo: 1,
    pageSize: 20,
  }),
  sideQueryParams: () => ({}),
  formItems: () => [],
  showTopQuery: true,
  showSideQuery: false,
  showAllNode: true,
  showPagination: true,
  isAllData: false,
  showDefaultButtons: true,
  sideWidth: 6,
  border: true,
  stripe: false,
  size: 'default',
  highlightCurrentRow: false,
  siderData: () => [],
  treeNodeKey: 'id',
  sideQueryLoading: false,
  tableColumns: () => [],
  tree: false,
  treeConfig: () => ({
    children: 'children',
    indent: 20,
    line: false,
    expandAll: false,
    accordion: false,
    trigger: 'default',
  }),
  showRefreshButton: false,
  expandConfig: () => ({}),
});

const emit = defineEmits<{
  pagination: [pagination: { limit: number; page: number }];
  query: [queryParams: Record<string, any>];
  refreshSideQuery: [];
  reset: [];
  resetSideQuery: [];
  rowClick: [row: Record<string, any>, column: any, event: Event];
  selectionChange: [selection: any];
  sideNodeAction: [payload: { command: string; data: TreeNodeData; node: any }];
  sideQuery: [node: TreeNodeData];
  sortChange: [sortInfo: { column: any; order: string; prop: string }];
}>();
const queryFormComponentRef = ref<FilterExpose | null>(null);
const tableRef = ref<any>(null);
const sideSearchKeyword = ref<string>('');
const treeRef = ref<InstanceType<typeof import('element-plus').ElTree> | null>(null);
const currentTreeNode = ref<null | TreeNodeData>(null);
const sideQueryCollapsed = ref<boolean>(false);
const isRefreshing = ref<boolean>(false);
const allNodeExpanded = ref<boolean>(true);

// 左侧树虚拟化：自动测量可视高度，传给 ElTreeV2 的 height（必须是 number）
const sideTreeViewportRef = ref<HTMLElement | null>(null);
const sideTreeHeight = ref<number>(360);
let sideTreeResizeObserver: null | ResizeObserver = null;

onMounted(() => {
  if (typeof ResizeObserver === 'undefined') return;
  sideTreeResizeObserver = new ResizeObserver(() => {
    const el = sideTreeViewportRef.value;
    if (!el) return;
    // 避免 0 导致 TreeV2 无法渲染
    sideTreeHeight.value = Math.max(240, Math.floor(el.clientHeight || 0));
  });
  if (sideTreeViewportRef.value) {
    sideTreeResizeObserver.observe(sideTreeViewportRef.value);
  }
});

onBeforeUnmount(() => {
  sideTreeResizeObserver?.disconnect();
  sideTreeResizeObserver = null;
});

const useVirtualSideTree = computed(() => {
  // 经验阈值：超过 200 个节点时 ElTree DOM/高亮/过滤会明显变慢
  const count = (filteredSiderData.value?.length || 0) + (props.showAllNode ? 1 : 0);
  return count >= 200;
});

const customFormItems = computed(() => {
  return props.formItems
    .filter((item) => item.type === 'custom')
    .map((item) => ({
      ...item,
      slotName: item.slot || item.prop,
    }));
});

const defaultProps = {
  children: 'children',
  label: 'label',
};

const filteredSiderData = computed(() => {
  if (!sideSearchKeyword.value || !props.siderData || props.siderData.length === 0) {
    return props.siderData;
  }

  const keyword = sideSearchKeyword.value.toLowerCase();

  const filterTree = (nodes: TreeNodeData[]): TreeNodeData[] => {
    if (!nodes || nodes.length === 0) return [];

    return nodes
      .map((node: TreeNodeData) => {
        const label = (node[defaultProps.label] || '').toLowerCase();
        const match = label.includes(keyword);
        const children = node[defaultProps.children];

        let filteredChildren: null | TreeNodeData[] = null;
        if (children && children.length > 0) {
          filteredChildren = filterTree(children);
        }

        if (match || (filteredChildren && filteredChildren.length > 0)) {
          return {
            ...node,
            [defaultProps.children]: filteredChildren,
          };
        }

        return null;
      })
      .filter(Boolean) as TreeNodeData[];
  };

  return filterTree(props.siderData);
});

const allNode = computed(() => {
  return {
    [props.treeNodeKey]: '__ALL__',
    [defaultProps.label]: '全部',
  };
});

const treeDataWithAll = computed(() => {
  return filteredSiderData.value || [];
});

const handleQuery = (filterParams?: Record<string, any>) => {
  const queryParams = { ...(filterParams ?? props.queryParams), pageNo: 1 };
  emit('query', queryParams);
  // 移除 sideQuery 的触发，避免重复调用接口
  // sideQuery 应该只在侧边树节点点击时触发，不应该在搜索时触发
};

const toggleAllNode = () => {
  allNodeExpanded.value = !allNodeExpanded.value;
};

const handleAllNodeClick = () => {
  const allNodeValue = allNode.value;
  currentTreeNode.value = allNodeValue;
  if (treeRef.value) {
    treeRef.value.setCurrentKey(null);
  }
  emit('sideQuery', allNodeValue);
};

const handleNodeClick = (data: TreeNodeData) => {
  currentTreeNode.value = data;
  if (treeRef.value && data && data[props.treeNodeKey]) {
    treeRef.value.setCurrentKey(data[props.treeNodeKey]);
  }
  // 只触发 sideQuery 事件，让父组件统一处理查询，避免重复调用
  emit('sideQuery', data);
};

const handleCurrentChange = (data: TreeNodeData) => {
  currentTreeNode.value = data;
};

const handleRefreshSideQuery = () => {
  sideSearchKeyword.value = '';
  currentTreeNode.value = null;
  isRefreshing.value = true;
  emit('refreshSideQuery');
};

// 记录是否已经初始化过默认选中
let hasInitializedDefaultSelection = false;

// 监听siderData变化，刷新后自动选中"全部"节点，或首次加载时自动选中
// 注意：这里不应 deep watch siderData。
// siderData 往往是大数组/树，deep watch 会触发大量递归遍历，导致左侧树渲染明显卡顿。
watch(
  () => props.siderData,
  (newData) => {
    // 如果是刷新操作
    if (isRefreshing.value) {
      nextTick(() => {
        if (props.showAllNode) {
          const allNodeValue = allNode.value;
          currentTreeNode.value = allNodeValue;
          if (treeRef.value) {
            treeRef.value.setCurrentKey(null);
          }
          emit('sideQuery', allNodeValue);
        } else if (newData && newData.length > 0) {
          const firstNode = newData[0];
          if (firstNode) {
            currentTreeNode.value = firstNode;
            if (treeRef.value && firstNode[props.treeNodeKey]) {
              treeRef.value.setCurrentKey(firstNode[props.treeNodeKey]);
            }
            emit('sideQuery', firstNode);
          }
        }
        isRefreshing.value = false;
      });
    }
    // 如果是首次加载且有数据，且还未初始化过
    else if (
      newData &&
      newData.length > 0 &&
      !hasInitializedDefaultSelection &&
      !currentTreeNode.value
    ) {
      hasInitializedDefaultSelection = true;
      nextTick(() => {
        // 如果显示"全部"节点，自动选中"全部"；否则选中第一个节点
        if (props.showAllNode) {
          const allNodeValue = allNode.value;
          currentTreeNode.value = allNodeValue;
          if (treeRef.value) {
            treeRef.value.setCurrentKey(null);
          }
          emit('sideQuery', allNodeValue);
        } else {
          const firstNode = newData[0];
          if (firstNode) {
            currentTreeNode.value = firstNode;
            if (treeRef.value && firstNode[props.treeNodeKey]) {
              treeRef.value.setCurrentKey(firstNode[props.treeNodeKey]);
            }
            emit('sideQuery', firstNode);
          }
        }
      });
    }
  },
  { deep: false },
);

const resetQuery = () => {
  if (queryFormComponentRef.value?.queryFormRef) {
    queryFormComponentRef.value.queryFormRef.resetFields();
  }
  const resetParams = { ...props.queryParams };
  if (resetParams) {
    Object.keys(resetParams).forEach((key) => {
      if (key !== 'pageNo' && key !== 'pageSize') {
        if (Array.isArray(resetParams[key])) {
          resetParams[key] = [];
        } else if (typeof resetParams[key] === 'object' && resetParams[key] !== null) {
          resetParams[key] = null;
        } else {
          resetParams[key] = '';
        }
      }
    });
    if (Object.prototype.hasOwnProperty.call(resetParams, 'pageNo')) {
      resetParams.pageNo = 1;
    }
  }
  emit('reset');
  // 不在这里触发 query 事件，让父组件的 handleReset 统一处理查询，避免重复调用
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  emit('pagination', pagination);
  // 不在这里触发 query 事件，让父组件的 handlePagination 统一处理查询，避免重复调用
};

const handleRowClick = (row: Record<string, any>, column: any, event: Event) => {
  emit('rowClick', row, column, event);
};

const handleSelectionChange = (selection: Record<string, any>[]) => {
  emit('selectionChange', selection);
};

const handleSortChange = ({
  column,
  prop,
  order,
}: {
  column: any;
  order: string;
  prop: string;
}) => {
  emit('sortChange', { column, prop, order });
};

const slots = useSlots();

const filteredSlotNames = computed(() => {
  return Object.keys(slots).filter(
    (name) =>
      name !== 'toolbar-actions' &&
      name !== 'toolbar-tools' &&
      name !== 'paginationLeft' &&
      name !== 'operations',
  );
});

const hasSideNodeMenuSlot = computed(() => !!slots['side-node-menu']);
const resolvedToolbarConfig = computed(() => ({
  ...props.toolbarConfig,
  zoom: false,
}));

// 侧边栏宽度（px）
// 历史上 props.sideWidth 默认值为 6，但模板中写死了 w-60(=240px)；
// 这里按 40px/单位换算，使默认 6 → 240px，并允许业务页传入更大的 sideWidth（如 15 → 600px）。
const sidePanelWidthPx = computed(() => {
  const unit = 40;
  const v = Number(props.sideWidth);
  if (!Number.isFinite(v) || v <= 0) return 240;
  return Math.round(v * unit);
});

defineExpose({
  queryFormRef: computed(() => queryFormComponentRef.value?.queryFormRef),
  handleQuery,
  resetQuery,
  gridApi: computed(() => tableRef.value?.gridApi),
});
</script>

<template>
  <div class="box-border flex h-full min-h-0 flex-col overflow-hidden p-2">
    <div class="relative flex min-h-0 flex-1 gap-0">
      <transition
        enter-active-class="transition-all duration-300 ease-in-out"
        leave-active-class="transition-all duration-300 ease-in-out"
        enter-from-class="opacity-0 -translate-x-5"
        enter-to-class="opacity-100 translate-x-0"
        leave-from-class="opacity-100 translate-x-0"
        leave-to-class="opacity-0 -translate-x-5"
      >
        <div
          v-if="showSideQuery"
          class="flex flex-shrink-0 flex-col overflow-hidden"
          :class="[sideQueryCollapsed ? 'border-0 p-0 opacity-0' : 'opacity-100']"
          :style="{ width: sideQueryCollapsed ? '0px' : sidePanelWidthPx + 'px' }"
        >
          <div v-if="!sideQueryCollapsed" class="mb-3 flex flex-shrink-0 flex-col gap-2">
            <div class="flex items-center">
              <el-input v-model="sideSearchKeyword" placeholder="搜索" clearable class="flex-1">
                <template #prefix>
                  <el-icon>
                    <Search />
                  </el-icon>
                </template>
              </el-input>
              <el-button
                :icon="Refresh"
                circle
                :loading="props.sideQueryLoading"
                @click="handleRefreshSideQuery"
                v-if="props.showRefreshButton"
              />
            </div>
            <slot name="side-header"></slot>
          </div>
          <div v-if="!sideQueryCollapsed" class="flex min-h-0 flex-1 flex-col overflow-hidden">
            <!-- 固定的"全部"节点 -->
            <div
              v-if="showAllNode"
              class="flex flex-shrink-0 cursor-pointer items-center border-b border-[#ebeef5] py-[5px] pl-2 transition-colors duration-200"
              :class="{
                'bg-[#ecf5ff] font-medium text-[#409eff]':
                  currentTreeNode?.[props.treeNodeKey] === '__ALL__',
                'hover:bg-[#f5f7fa]': currentTreeNode?.[props.treeNodeKey] !== '__ALL__',
              }"
            >
              <span
                class="mr-1 inline-flex h-3 w-3 cursor-pointer items-center justify-center transition-transform duration-200 hover:text-[#409eff]"
                @click.stop="toggleAllNode"
              >
                <el-icon
                  class="!text-[14px] transition-transform duration-200"
                  :class="{ 'rotate-90': allNodeExpanded }"
                >
                  <ArrowRight />
                </el-icon>
              </span>
              <span class="flex-1 text-sm" @click="handleAllNodeClick">
                {{ allNode[defaultProps.label] || '全部' }}
              </span>
            </div>
            <!-- 可滚动的树形组件 -->
            <div
              v-show="showAllNode ? allNodeExpanded : true"
              ref="sideTreeViewportRef"
              class="min-h-0 flex-1"
            >
              <!-- 节点多时启用虚拟化（ElTreeV2） -->
              <ElTreeV2
                v-if="useVirtualSideTree"
                :data="showAllNode ? treeDataWithAll : filteredSiderData"
                :props="defaultProps"
                :node-key="treeNodeKey"
                :height="sideTreeHeight"
                :item-size="32"
                highlight-current
                :expand-on-click-node="false"
                :current-node-key="currentTreeNode?.[props.treeNodeKey]"
                @node-click="handleNodeClick"
              />

              <!-- 节点少时用 ElTree（功能更全：菜单、插槽等） -->
              <el-tree
                v-else
                ref="treeRef"
                :data="showAllNode ? treeDataWithAll : filteredSiderData"
                :props="defaultProps"
                :node-key="treeNodeKey"
                :expand-on-click-node="false"
                default-expand-all
                highlight-current
                @node-click="handleNodeClick"
                @current-change="handleCurrentChange"
              >
                <template #default="{ node, data }">
                  <span class="group flex flex-1 items-center justify-between" @click.stop>
                    <span
                      class="min-w-0 flex-1 overflow-hidden text-ellipsis whitespace-nowrap"
                      @click.stop="handleNodeClick(data)"
                      >{{ node.label }}</span
                    >
                    <slot name="side-node-actions" :node="node" :data="data">
                      <el-dropdown
                        v-if="hasSideNodeMenuSlot"
                        trigger="hover"
                        @command="
                          (command: string) => emit('sideNodeAction', { command, node, data })
                        "
                        @click.stop
                      >
                        <el-icon
                          class="mr-4 cursor-pointer px-1 text-gray-400 hover:text-gray-600"
                          style="font-size: 20px"
                          @click.stop
                        >
                          <MoreFilled />
                        </el-icon>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <slot name="side-node-menu" :node="node" :data="data"></slot>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </slot>
                  </span>
                </template>
              </el-tree>
            </div>
          </div>
        </div>
      </transition>

      <transition
        enter-active-class="transition-opacity duration-300 ease-in-out"
        leave-active-class="transition-opacity duration-300 ease-in-out"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="showSideQuery"
          class="relative mx-3 flex w-px flex-shrink-0 items-start justify-center bg-[#ebeef5]"
        >
          <el-button
            circle
            class="absolute left-1/2 top-[18px] z-10 h-6 w-6 -translate-x-1/2 border border-[#ebeef5] bg-white shadow-[0_2px_8px_rgba(0,0,0,0.12)] transition-colors hover:border-[#409eff] hover:bg-[#409eff] hover:text-white"
            @click="sideQueryCollapsed = !sideQueryCollapsed"
          >
            <el-icon>
              <ArrowRight v-if="sideQueryCollapsed" />
              <ArrowLeft v-else />
            </el-icon>
          </el-button>
        </div>
      </transition>

      <!-- 主内容区域 -->
      <div
        class="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden"
        :class="{ 'with-side-query': showSideQuery && !sideQueryCollapsed }"
      >
        <Filter
          v-if="showTopQuery"
          ref="queryFormComponentRef"
          :query-params="queryParams"
          :form-items="formItems"
          :show-default-buttons="showDefaultButtons"
          @query="handleQuery"
          @reset="resetQuery"
        >
          <template v-for="item in customFormItems" :key="item.prop" #[item.slotName]="slotProps">
            <slot
              :name="item.slotName"
              :item="slotProps.item"
              :query-params="props.queryParams"
            ></slot>
          </template>
          <template #default>
            <slot
              name="topQuery"
              :query-params="props.queryParams"
              :handle-query="handleQuery"
              :reset-query="resetQuery"
            ></slot>
          </template>
        </Filter>
        <!-- 表格区域 -->
        <Table
          ref="tableRef"
          class="min-h-0 flex-1"
          :table-data="tableData"
          :loading="loading"
          :border="border"
          :stripe="stripe"
          :size="size"
          :table-height="tableHeight"
          :max-height="maxHeight"
          :row-key="rowKey"
          :row-class-name="rowClassName"
          :span-method="spanMethod"
          :highlight-current-row="highlightCurrentRow"
          :table-columns="tableColumns"
          :show-pagination="showPagination"
          :total="total"
          :page-no="props.queryParams.pageNo"
          :page-size="props.queryParams.pageSize"
          :is-all-data="isAllData"
          :toolbar-config="resolvedToolbarConfig"
          :tree="tree"
          :tree-config="treeConfig"
          :expand-config="props.expandConfig"
          @row-click="handleRowClick"
          @selection-change="handleSelectionChange"
          @sort-change="handleSortChange"
          @pagination="handlePagination"
        >
          <template #toolbar-actions>
            <slot name="toolbar-actions"></slot>
          </template>
          <template #toolbar-tools>
            <slot name="toolbar-tools"></slot>
          </template>
          <template v-for="slotName in filteredSlotNames" :key="slotName" #[slotName]="slotProps">
            <slot :name="slotName" v-bind="slotProps"></slot>
          </template>
        </Table>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
:deep(.el-tree--highlight-current) {
  background-color: #fff !important;
}
</style>
