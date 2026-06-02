<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { ArrowLeft, ArrowRight, Plus, Search } from '@element-plus/icons-vue';
import { useDebounceFn } from '@vueuse/core';
import { ElButton, ElIcon, ElInput, ElScrollbar } from 'element-plus';

interface PurchaseOrderItem {
  [key: string]: any;
  busNo: string;
  createTime?: string;
  id: string;
  status?: string;
  supplierName?: string;
}

interface PurchaseOrderListConfig {
  /** 是否还有更多数据 */
  hasMore?: boolean;
  /** 是否启用无限滚动（下拉刷新模式） */
  infiniteScroll?: boolean;
  /** 采购单列表数据 */
  list?: PurchaseOrderItem[];
  /** 加载状态 */
  loading?: boolean;
  /** 选中的采购单ID */
  selectedId?: string;
  /** 标题 */
  title?: string;
  /** 侧边栏宽度 */
  width?: number;
}

interface Props {
  /** 配置 */
  config?: PurchaseOrderListConfig;
  /** 是否已初始化搜索 */
  isSearchInitialized?: boolean;
  /** 是否显示 */
  visible?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  config: () => ({}),
  isSearchInitialized: false,
});

const emit = defineEmits<{
  add: [];
  click: [order: PurchaseOrderItem];
  loadMore: [];
  search: [keyword: string];
  'update:isSearchInitialized': [value: boolean];
  'update:selectedId': [id: string];
}>();

const purchaseOrderListCollapsed = ref<boolean>(false);
const purchaseOrderSearchKeyword = ref<string>('');

// 采购单列表配置的计算属性（提供默认值）
const purchaseOrderListConfig = computed(() => ({
  list: props.config?.list || [],
  loading: props.config?.loading ?? false,
  title: props.config?.title || '',
  width: props.config?.width ?? 320,
  infiniteScroll: props.config?.infiniteScroll ?? false,
  hasMore: props.config?.hasMore ?? false,
  selectedId: props.config?.selectedId || '',
}));

// 过滤后的采购单列表（如果启用无限滚动，则不过滤，直接显示全部，由接口过滤）
const filteredPurchaseOrderList = computed(() => {
  const config = purchaseOrderListConfig.value;
  // 如果启用无限滚动，说明搜索由接口处理，直接返回全部数据
  if (config.infiniteScroll) {
    return config.list;
  }
  // 否则使用本地过滤
  if (!purchaseOrderSearchKeyword.value) {
    return config.list;
  }
  const keyword = purchaseOrderSearchKeyword.value.toLowerCase();
  return config.list.filter(
    (order) =>
      order.busNo?.toLowerCase().includes(keyword) ||
      order.supplierName?.toLowerCase().includes(keyword),
  );
});

// 防抖搜索函数
const debouncedSearch = useDebounceFn((keyword: string) => {
  emit('search', keyword);
}, 500);

// 监听搜索关键词变化
watch(
  () => purchaseOrderSearchKeyword.value,
  (newKeyword) => {
    if (purchaseOrderListConfig.value.infiniteScroll) {
      // 避免初始化时触发搜索（初始值为空字符串时）
      if (!props.isSearchInitialized) {
        emit('update:isSearchInitialized', true);
        return;
      }
      debouncedSearch(newKeyword || '');
    }
  },
);

// 处理采购单点击
const handlePurchaseOrderClick = (order: PurchaseOrderItem) => {
  emit('update:selectedId', order.id);
  emit('click', order);
};

// 处理采购单列表滚动
const handlePurchaseOrderScroll = (e: Event) => {
  const config = purchaseOrderListConfig.value;
  if (!config.infiniteScroll || !config.hasMore) {
    return;
  }

  const target = e.target as HTMLElement;
  if (!target) return;

  // 计算是否滚动到底部（距离底部 50px 内触发）
  const scrollTop = target.scrollTop;
  const scrollHeight = target.scrollHeight;
  const clientHeight = target.clientHeight;

  if (scrollHeight - scrollTop - clientHeight < 50) {
    emit('loadMore');
  }
};
</script>

<template>
  <div class="mt-2 flex h-full gap-2">
    <!-- 左侧采购单列表侧边栏（独立 card，完全独立于 Page） -->
    <transition
      enter-active-class="transition-all duration-300 ease-in-out"
      leave-active-class="transition-all duration-300 ease-in-out"
      enter-from-class="w-0 opacity-0 -translate-x-5"
      enter-to-class="opacity-100 translate-x-0"
      leave-from-class="opacity-100 translate-x-0"
      leave-to-class="w-0 opacity-0 -translate-x-5"
    >
      <div
        v-if="visible"
        class="bg-background-card flex flex-shrink-0 flex-col overflow-hidden rounded-md transition-all duration-300 ease-in-out"
        :class="[purchaseOrderListCollapsed ? 'w-0 p-0 opacity-0' : 'opacity-100']"
        :style="{ width: purchaseOrderListCollapsed ? '0' : `${purchaseOrderListConfig.width}px` }"
      >
        <div v-if="!purchaseOrderListCollapsed" class="flex h-full flex-col overflow-hidden p-2">
          <!-- 标题和搜索 -->
          <div class="border-border mb-2 flex flex-shrink-0 items-center gap-4 border-b pb-2">
            <h3 class="text-base font-semibold" v-if="purchaseOrderListConfig.title">
              {{ purchaseOrderListConfig.title }}
            </h3>
            <ElInput
              v-model="purchaseOrderSearchKeyword"
              placeholder="搜索单据号"
              clearable
              class="flex-1"
            >
              <template #prefix>
                <ElIcon>
                  <Search />
                </ElIcon>
              </template>
            </ElInput>
          </div>
          <!-- 新增按钮 -->
          <div class="flex-shrink-0 px-2">
            <ElButton type="primary" class="w-full" @click="emit('add')">
              <ElIcon class="mr-1"><Plus /></ElIcon>
              新增
            </ElButton>
          </div>
          <!-- 采购单列表 -->
          <ElScrollbar class="flex-1">
            <div
              v-loading="purchaseOrderListConfig.loading"
              class="p-2"
              @scroll="handlePurchaseOrderScroll"
            >
              <div
                v-for="order in filteredPurchaseOrderList"
                :key="order.id"
                class="border-border bg-card hover:border-primary hover:bg-accent mb-2 cursor-pointer rounded-md border p-3 transition-colors"
                :class="{
                  'border-primary bg-accent': purchaseOrderListConfig.selectedId === order.id,
                }"
                @click="handlePurchaseOrderClick(order)"
              >
                <div class="mb-1 text-sm font-medium">{{ order.busNo }}</div>
                <div v-if="order.supplierName" class="text-muted-foreground mb-1 text-xs">
                  供应商：{{ order.supplierName }}
                </div>
                <div v-if="order.createTime" class="text-muted-foreground mb-1 text-xs">
                  时间：{{ order.createTime }}
                </div>
                <div v-if="order.status" class="text-xs">
                  <span
                    class="inline-block rounded px-2 py-0.5"
                    :class="{
                      'bg-yellow-100 text-yellow-800': order.status === '待审核',
                      'bg-green-100 text-green-800': order.status === '已审核',
                      'bg-red-100 text-red-800': order.status === '已驳回',
                    }"
                  >
                    {{ order.status }}
                  </span>
                </div>
              </div>
              <div
                v-if="!purchaseOrderListConfig.loading && filteredPurchaseOrderList.length === 0"
                class="text-muted-foreground flex h-32 items-center justify-center text-sm"
              >
                暂无数据
              </div>
              <!-- 加载更多提示 -->
              <div
                v-if="purchaseOrderListConfig.infiniteScroll && purchaseOrderListConfig.hasMore"
                class="text-muted-foreground flex items-center justify-center py-2 text-xs"
              >
                滚动加载更多...
              </div>
              <div
                v-if="
                  purchaseOrderListConfig.infiniteScroll &&
                  !purchaseOrderListConfig.hasMore &&
                  filteredPurchaseOrderList.length > 0
                "
                class="text-muted-foreground flex items-center justify-center py-2 text-xs"
              >
                没有更多数据了
              </div>
            </div>
          </ElScrollbar>
        </div>
      </div>
    </transition>

    <!-- 分隔线和收起按钮（采购单列表） -->
    <transition
      enter-active-class="transition-opacity duration-300 ease-in-out"
      leave-active-class="transition-opacity duration-300 ease-in-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="visible"
        class="bg-border relative flex w-px flex-shrink-0 items-start justify-center transition-opacity duration-300 ease-in-out"
      >
        <ElButton
          circle
          class="border-border bg-background hover:border-primary hover:bg-primary hover:text-primary-foreground z-100 absolute left-1/2 top-[18px] h-6 w-6 -translate-x-1/2 border shadow-sm transition-colors"
          @click="purchaseOrderListCollapsed = !purchaseOrderListCollapsed"
        >
          <ElIcon>
            <ArrowRight v-if="purchaseOrderListCollapsed" />
            <ArrowLeft v-else />
          </ElIcon>
        </ElButton>
      </div>
    </transition>

    <!-- 主内容区域插槽 -->
    <div class="flex min-h-0 w-full min-w-0 flex-1">
      <slot></slot>
    </div>
  </div>
</template>
