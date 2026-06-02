<script setup lang="ts">
import type { VNode } from 'vue';

import { computed, onMounted, ref, watch } from 'vue';

import { Input } from '@vben-core/shadcn-ui';
import { cn, isFunction } from '@vben-core/shared/utils';

import { ElPopover, ElTable, ElTableColumn } from 'element-plus';

/**
 * 远程选项项结构
 */
export interface RemoteSelectItem {
  /** 主键/值字段 */
  value: number | string;
  /** 名称 */
  name: string;
  /** 编码 */
  code?: string;
  /** 备注 */
  remark?: string;
  /** 允许附加任意字段 */
  [key: string]: any;
}

export interface RemoteSelectResult {
  list: RemoteSelectItem[];
  total?: number;
  totalElements?: number;
}

export interface RemoteSelectColumn {
  /** 字段 key，对应数据中的属性名 */
  key: string;
  /** 列头显示文本 */
  label?: string;
}

interface Props {
  /** v-model 绑定的值（选中项的 value） */
  modelValue?: null | number | string;
  /** 占位提示 */
  placeholder?: string;
  /** 每页条数 */
  pageSize?: number;
  /** 远程加载函数，返回分页结果 */
  request: (params: {
    keyword: string;
    page: number;
    pageSize: number;
  }) => Promise<RemoteSelectResult>;
  /** Popover 宽度 */
  popoverWidth?: number;
  /** Popover 内容类名 */
  contentClass?: any;
  /**
   * 列配置：不传则使用默认布局：名称 + 编码 + 备注
   */
  columns?: RemoteSelectColumn[];
  /** 额外渲染在名称行右侧的自定义节点 */
  extraRender?: (item: RemoteSelectItem) => null | VNode;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请选择',
  pageSize: 10,
  popoverWidth: 800,
  contentClass: '',
  columns: () => [
    { key: 'name', label: '名称' },
    { key: 'code', label: '编码' },
    { key: 'remark', label: '备注' },
  ],
  extraRender: undefined,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: null | number | string): void;
  (e: 'change', item: null | RemoteSelectItem): void;
}>();

/**
 * 状态
 */
const visible = ref(false);
const loading = ref(false);
const loadingMore = ref(false);

// 输入框 ref，用于选择后失焦
const inputRef = ref<any>(null);

const page = ref(1);
const pageSize = computed(() => props.pageSize ?? 10);
const total = ref(0);
const tableData = ref<RemoteSelectItem[]>([]);

// 搜索关键字（传给接口）
const keyword = ref('');

// 选中值与展示文本
const innerValue = ref<null | number | string>(props.modelValue ?? null);

// 外部 v-model 变化时，同步内部值
watch(
  () => props.modelValue,
  (val) => {
    innerValue.value = val ?? null;
  },
);

const selectedItem = computed(
  () => tableData.value.find((it) => it.value === innerValue.value) ?? null,
);

const displayLabel = computed(() => {
  const item = selectedItem.value;
  if (!item) return '';
  const { name, code } = item;
  return name && code ? `${name}（${code}）` : name || String(item.value ?? '');
});

const inputText = ref('');

watch(
  () => selectedItem.value,
  () => {
    if (!visible.value) {
      inputText.value = displayLabel.value;
    }
  },
  { immediate: true },
);

const tableColumns = computed(() =>
  (props.columns ?? []).map((col) => ({
    prop: col.key,
    label: col.label || col.key,
  })),
);

/**
 * 拉取列表（滚动加载）
 */
async function fetchPage(append = false) {
  if (!isFunction(props.request)) return;
  if (append) {
    loadingMore.value = true;
  } else {
    loading.value = true;
  }
  try {
    const res = await props.request({
      keyword: keyword.value.trim(),
      page: page.value,
      pageSize: pageSize.value,
    });

    const list = Array.isArray(res.list) ? res.list : [];
    tableData.value = append ? [...tableData.value, ...list] : list;

    total.value =
      Number.isFinite(res.totalElements as number) && res.totalElements !== undefined
        ? Number(res.totalElements)
        : Number.isFinite(res.total as number) && res.total !== undefined
          ? Number(res.total)
          : 0;
  } catch (error) {
    console.error('[RemoteSelectPopover] 请求数据失败:', error);
    if (!append) {
      tableData.value = [];
      total.value = 0;
    }
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
}

const hasMore = computed(() => tableData.value.length < total.value);

/**
 * 滚动到底部加载更多
 */
function handleTableScroll({ scrollTop, scrollHeight, clientHeight }: any) {
  if (loading.value || loadingMore.value || !hasMore.value) return;
  // 接近底部 50px 时加载下一页
  if (scrollTop + clientHeight >= scrollHeight - 50) {
    page.value += 1;
    fetchPage(true);
  }
}

/**
 * 选中行
 */
function handleSelect(row: RemoteSelectItem) {
  innerValue.value = row.value;
  emit('update:modelValue', row.value);
  emit('change', row);
  visible.value = false;
  // 选择后让输入框失焦
  if (inputRef.value?.blur) {
    inputRef.value.blur();
  } else if (inputRef.value?.$el?.blur) {
    inputRef.value.$el.blur();
  }
}

/**
 * 输入框回车：默认选中当前结果的第一行；如果没有数据则不关闭 Popover
 */
function handleEnter() {
  if (!visible.value) return;
  if (tableData.value.length === 1) {
    const first = tableData.value[0];
    handleSelect(first as RemoteSelectItem);
  }
}

/**
 * 高亮当前选中行
 */
function rowClassName({ row }: { row: RemoteSelectItem }) {
  return row.value === innerValue.value ? 'remote-select-row--active' : '';
}

// 搜索防抖：直接监听输入框文本
let searchDebounceTimer: null | ReturnType<typeof setTimeout> = null;

watch(
  () => inputText.value,
  (val) => {
    if (!visible.value) return;
    if (searchDebounceTimer) {
      clearTimeout(searchDebounceTimer);
    }
    searchDebounceTimer = setTimeout(() => {
      keyword.value = val.trim();
      page.value = 1;
      fetchPage(false);
    }, 300);
  },
);

// 弹框关闭时，恢复为选中值，并清空搜索关键字
watch(
  () => visible.value,
  (val) => {
    if (!val) {
      inputText.value = displayLabel.value;
      keyword.value = '';
    }
  },
);

onMounted(() => {
  fetchPage(false);
});
</script>

<template>
  <ElPopover
    v-model:visible="visible"
    placement="bottom-start"
    :width="popoverWidth"
    :popper-class="cn('p-0', contentClass)"
    :trigger-keys="[]"
    trigger="click"
  >
    <template #reference>
      <Input
        v-model="inputText"
        :placeholder="placeholder"
        class="h-9 w-full"
        @keydown.enter.stop.prevent="handleEnter"
      />
    </template>

    <div class="flex max-h-[520px] min-h-[260px] w-full flex-col text-sm">
      <ElTable
        :data="tableData"
        size="large"
        style="width: 100%"
        @row-click="handleSelect"
        :row-class-name="rowClassName"
        :loading="loading"
        @scroll="handleTableScroll"
      >
        <ElTableColumn
          v-for="col in tableColumns"
          :key="col.prop"
          :prop="col.prop"
          :label="col.label"
          show-overflow-tooltip
        />
      </ElTable>
    </div>
  </ElPopover>
</template>

<style scoped>
.remote-select-row--active {
  background-color: #ebf8ff !important;
}
</style>
