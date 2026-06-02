<script setup lang="ts">
import type { VbenFormSchema } from '@vben-core/form-ui';

import type { EditableTableColumn, EditableTableProps } from '../types/EditableTable.d';

import { computed, nextTick, onMounted, reactive, ref, useTemplateRef, watch } from 'vue';

import { useVbenForm } from '@vben-core/form-ui';
import { CSS_VARIABLE_LAYOUT_CONTENT_HEIGHT } from '@vben-core/shared/constants';
import { cn } from '@vben-core/shared/utils';

import { ArrowLeft, ArrowRight, Plus, Search } from '@element-plus/icons-vue';
import { useDebounceFn } from '@vueuse/core';
import { ElButton, ElIcon, ElInput, ElScrollbar, ElTree } from 'element-plus';

import EditableTable from './EditableTable.vue';

interface TreeNodeData {
  [key: string]: any;
  children?: TreeNodeData[];
  id?: string;
  label?: string;
}

defineOptions({
  name: 'EditableTableWithForm',
});

const props = withDefaults(defineProps<EditableTableWithFormProps>(), {
  addButtonText: '添加',
  autoContentHeight: false,
  contentClass: '',
  deleteButtonText: '删除',
  formCommonConfig: () => ({}),
  formInitialValues: () => ({}),
  formLayout: 'inline',
  formSchema: () => [],
  formTitle: '基础信息',
  formWrapperClass: 'flex flex-wrap gap-x-4 gap-y-2',
  footerClass: '',
  headerClass: '',
  heightOffset: 0,
  purchaseOrderListConfig: () => ({}),
  requiredFormFields: () => [],
  showAddButton: true,
  showDeleteButton: true,
  showFormTitle: false,
  showPurchaseOrderList: false,
  showSideList: false,
  sideListConfig: () => ({}),
  tagContent: undefined,
});

const emit = defineEmits<{
  cellChange: [row: Record<string, any>, column: EditableTableColumn, value: any];
  formChange: [values: Record<string, any>];
  purchaseOrderAdd: [];
  purchaseOrderClick: [order: Record<string, any>];
  purchaseOrderLoadMore: [];
  purchaseOrderSearch: [keyword: string];
  rowAdd: [row: Record<string, any>, index: number];
  rowRemove: [row: Record<string, any>, index: number];
  sideListNodeClick: [node: TreeNodeData];
  submit: [formValues: Record<string, any>, tableData: Record<string, any>[]];
  'update:modelValue': [value: Record<string, any>[]];
  'update:selectedPurchaseOrderId': [id: string];
}>();

interface PurchaseOrderListConfig {
  /** 是否还有更多数据 */
  hasMore?: boolean;
  /** 是否启用无限滚动（下拉刷新模式） */
  infiniteScroll?: boolean;
  /** 采购单列表数据 */
  list?: Array<{
    [key: string]: any;
    busNo: string;
    createTime?: string;
    id: string;
    status?: string;
    supplierName?: string;
  }>;
  /** 加载状态 */
  loading?: boolean;
  /** 选中的采购单ID */
  selectedId?: string;
  /** 标题 */
  title?: string;
  /** 侧边栏宽度 */
  width?: number;
}

interface SideListConfig {
  /** 列表数据 */
  data?: TreeNodeData[];
  /** 标题 */
  title?: string;
  /** 树节点 children 字段 */
  treeNodeChildren?: string;
  /** 树节点 key 字段 */
  treeNodeKey?: string;
  /** 树节点 label 字段 */
  treeNodeLabel?: string;
  /** 宽度 */
  width?: number;
}

interface EditableTableWithFormProps extends Omit<EditableTableProps, 'disabled'> {
  /** Page 组件的 autoContentHeight */
  autoContentHeight?: boolean;
  /** Page 组件的 contentClass */
  contentClass?: string;
  /** Page 组件的 footerClass */
  footerClass?: string;
  /** 表单项通用配置 */
  formCommonConfig?: any;
  /** 表单初始值 */
  formInitialValues?: Record<string, any>;
  /** 表单布局 */
  formLayout?: 'horizontal' | 'inline' | 'vertical';
  /** 表单字段配置 */
  formSchema?: VbenFormSchema[];
  /** 表单标题 */
  formTitle?: string;
  /** 表单栅格布局 */
  formWrapperClass?: string;
  /** Page 组件的 headerClass */
  headerClass?: string;
  /** Page 组件的 heightOffset */
  heightOffset?: number;
  /** 采购单列表配置 */
  purchaseOrderListConfig?: PurchaseOrderListConfig;
  /** 必须填写的表单字段（用于控制是否允许添加行） */
  requiredFormFields?: string[];
  /** 是否显示表单标题 */
  showFormTitle?: boolean;
  /** 是否显示采购单列表侧边栏 */
  showPurchaseOrderList?: boolean;
  /** 是否显示左侧列表 */
  showSideList?: boolean;
  /** 左侧列表配置 */
  sideListConfig?: SideListConfig;
  /** Page 组件的 tagContent */
  tagContent?: (() => string) | string;
}

const editableTableRef = ref<InstanceType<typeof EditableTable> | null>(null);
const tableData = ref<Record<string, any>[]>(props.modelValue || []);
const sideListCollapsed = ref<boolean>(false);
const sideListSearchKeyword = ref<string>('');
const treeRef = ref<InstanceType<typeof ElTree> | null>(null);
const currentTreeNode = ref<null | TreeNodeData>(null);
const purchaseOrderListCollapsed = ref<boolean>(false);
const purchaseOrderSearchKeyword = ref<string>('');
const isPurchaseOrderSearchInitialized = ref<boolean>(false);

// 采购单列表配置的计算属性（提供默认值）
const purchaseOrderListConfig = computed(() => ({
  list: props.purchaseOrderListConfig?.list || [],
  loading: props.purchaseOrderListConfig?.loading ?? false,
  title: props.purchaseOrderListConfig?.title || '',
  width: props.purchaseOrderListConfig?.width ?? 320,
  infiniteScroll: props.purchaseOrderListConfig?.infiniteScroll ?? false,
  hasMore: props.purchaseOrderListConfig?.hasMore ?? false,
  selectedId: props.purchaseOrderListConfig?.selectedId || '',
}));

// 左侧列表配置的计算属性（提供默认值）
const sideListConfig = computed(() => ({
  data: props.sideListConfig?.data || [],
  title: props.sideListConfig?.title || '列表',
  width: props.sideListConfig?.width ?? 240,
  treeNodeChildren: props.sideListConfig?.treeNodeChildren || 'children',
  treeNodeKey: props.sideListConfig?.treeNodeKey || 'id',
  treeNodeLabel: props.sideListConfig?.treeNodeLabel || 'label',
}));

// Page 组件相关
const headerRef = useTemplateRef<HTMLDivElement>('headerRef');
const footerRef = useTemplateRef<HTMLDivElement>('footerRef');
const headerHeight = ref(0);
const footerHeight = ref(0);
const shouldAutoHeight = ref(false);

const contentStyle = computed<Record<string, string>>(() => {
  if (props.autoContentHeight) {
    return {
      height: `calc(var(${CSS_VARIABLE_LAYOUT_CONTENT_HEIGHT}) - ${headerHeight.value}px - ${footerHeight.value}px - ${typeof props.heightOffset === 'number' ? `${props.heightOffset}px` : props.heightOffset})`,
      overflowY: shouldAutoHeight.value ? 'auto' : 'unset',
    } as Record<string, string>;
  }
  return {};
});

async function calcContentHeight() {
  if (!props.autoContentHeight) {
    return;
  }
  await nextTick();
  headerHeight.value = headerRef.value?.offsetHeight || 0;
  footerHeight.value = footerRef.value?.offsetHeight || 0;
  setTimeout(() => {
    shouldAutoHeight.value = true;
  }, 30);
}

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
  emit('purchaseOrderSearch', keyword);
}, 500);

// 监听搜索关键词变化
watch(purchaseOrderSearchKeyword, (newKeyword) => {
  if (purchaseOrderListConfig.value.infiniteScroll) {
    // 避免初始化时触发搜索（初始值为空字符串时）
    if (!isPurchaseOrderSearchInitialized.value) {
      isPurchaseOrderSearchInitialized.value = true;
      return;
    }
    debouncedSearch(newKeyword || '');
  }
});

// 处理采购单点击
const handlePurchaseOrderClick = (order: Record<string, any>) => {
  emit('update:selectedPurchaseOrderId', order.id);
  emit('purchaseOrderClick', order);
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
    emit('purchaseOrderLoadMore');
  }
};

// 过滤树形数据
const filteredSideListData = computed(() => {
  const config = sideListConfig.value;
  if (!sideListSearchKeyword.value || !config.data || config.data.length === 0) {
    return config.data;
  }

  const keyword = sideListSearchKeyword.value.toLowerCase();
  const labelKey = config.treeNodeLabel;
  const childrenKey = config.treeNodeChildren;

  const filterTree = (nodes: TreeNodeData[]): TreeNodeData[] => {
    if (!nodes || nodes.length === 0) return [];

    return nodes
      .map((node: TreeNodeData) => {
        const label = String(node[labelKey] || '').toLowerCase();
        const match = label.includes(keyword);
        const children = node[childrenKey];

        let filteredChildren: null | TreeNodeData[] = null;
        if (children && Array.isArray(children) && children.length > 0) {
          filteredChildren = filterTree(children);
        }

        if (match || (filteredChildren && filteredChildren.length > 0)) {
          return {
            ...node,
            [childrenKey]: filteredChildren,
          };
        }

        return null;
      })
      .filter(Boolean) as TreeNodeData[];
  };

  return filterTree(config.data);
});

// 树节点属性配置
const treeProps = computed(() => {
  const config = sideListConfig.value;
  return {
    children: config.treeNodeChildren,
    label: config.treeNodeLabel,
  };
});

// 处理树节点点击
const handleNodeClick = (data: TreeNodeData) => {
  const config = sideListConfig.value;
  currentTreeNode.value = data;
  if (treeRef.value && data && data[config.treeNodeKey]) {
    treeRef.value.setCurrentKey(data[config.treeNodeKey]);
  }
  emit('sideListNodeClick', data);
};

watch(
  () => props.modelValue,
  (newVal) => {
    if (JSON.stringify(newVal) !== JSON.stringify(tableData.value)) {
      tableData.value = newVal || [];
    }
  },
  { deep: true, immediate: true },
);

watch(
  tableData,
  (newVal) => {
    emit('update:modelValue', newVal);
  },
  { deep: true },
);
const [Form, formApi] = useVbenForm(
  reactive({
    schema: computed(() => props.formSchema),
    layout: props.formLayout,
    wrapperClass: props.formWrapperClass,
    commonConfig: {
      componentProps: {
        style: { width: '220px' },
      },
      ...props.formCommonConfig,
    },
    showDefaultActions: false,
  }),
);

const formValues = ref<Record<string, any>>({});

onMounted(async () => {
  await nextTick();

  let retries = 0;
  while (!formApi.isMounted && retries < 50) {
    await nextTick();
    retries++;
  }

  if (formApi.isMounted && formApi.form) {
    // 如果有初始值，先设置初始值
    if (props.formInitialValues && Object.keys(props.formInitialValues).length > 0) {
      await formApi.setValues(props.formInitialValues);
    }

    formValues.value = (await formApi.getValues()) || {};
    emit('formChange', formValues.value);

    watch(
      () => formApi.form.values,
      async () => {
        formValues.value = (await formApi.getValues()) || {};
        emit('formChange', formValues.value);
      },
      { deep: true },
    );
  }

  calcContentHeight();
});

const canAddRow = computed(() => {
  if (props.requiredFormFields.length === 0) {
    return true;
  }

  return props.requiredFormFields.every((field) => {
    const value = formValues.value[field];
    return value !== undefined && value !== null && value !== '';
  });
});

const handleRowAdd = (row: Record<string, any>, index: number) => {
  const newRow = {
    ...formValues.value,
    ...row,
  };
  const currentData = editableTableRef.value?.getData() || [];
  currentData[index] = newRow;
  editableTableRef.value?.setData(currentData);
  emit('rowAdd', newRow, index);
};

const handleSubmit = async () => {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  const finalFormValues = await formApi.getValues();
  const tableData = editableTableRef.value?.getData() || [];

  emit('submit', finalFormValues, tableData);
};

defineExpose({
  getFormApi: () => formApi,
  getTableRef: () => editableTableRef.value,
  getFormValues: async () => {
    return await formApi.getValues();
  },
  getTableData: () => {
    return editableTableRef.value?.getData() || [];
  },
  validateForm: async () => {
    return await formApi.validate();
  },
  validateTable: async () => {
    return (await editableTableRef.value?.validate()) || true;
  },
  resetForm: async () => {
    await formApi.resetForm();
  },
  clearTable: () => {
    editableTableRef.value?.clear();
  },
  handleSubmit,
});
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
        v-if="showPurchaseOrderList"
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
            <ElButton type="primary" class="w-full" @click="emit('purchaseOrderAdd')">
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
        v-if="showPurchaseOrderList"
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

    <!-- Page 组件（主内容区域） -->
    <div class="flex min-h-0 w-full min-w-0 flex-1">
      <div class="bg-background-card relative flex h-full w-full flex-col">
        <div
          v-if="tagContent || $slots.tag"
          class="absolute left-0 top-0 z-10 flex gap-2 rounded-br-xl border-b border-r bg-amber-50 px-2 py-1"
        >
          <slot name="tag">
            <div class="flex items-center">
              <span class="text-sm font-semibold text-gray-800">
                {{ typeof tagContent === 'function' ? tagContent() : tagContent }}
              </span>
            </div>
          </slot>
        </div>
        <div
          v-if="$slots.title || $slots.description || $slots.extra"
          ref="headerRef"
          :class="
            cn('bg-card border-border relative flex items-end border-b px-6 py-4', headerClass)
          "
        >
          <div class="flex-auto">
            <slot name="title"></slot>
            <slot name="description"></slot>
          </div>
          <div v-if="$slots.extra">
            <slot name="extra"></slot>
          </div>
        </div>

        <div
          :class="
            cn(
              'flex min-h-0 flex-1 flex-col overflow-hidden p-4',
              contentClass,
              tagContent || $slots.tag ? 'pt-12' : '',
            )
          "
          :style="contentStyle"
        >
          <div
            v-if="(formSchema?.length ?? 0) > 0"
            class="form-wrapper flex-shrink-0 rounded-md p-2"
          >
            <Form />
          </div>
          <div v-if="showAddButton" class="add-button-wrapper flex-shrink-0"></div>
          <div class="relative flex min-h-0 w-full flex-1 gap-0 overflow-hidden">
            <!-- 左侧列表 -->
            <transition
              enter-active-class="transition-all duration-300 ease-in-out"
              leave-active-class="transition-all duration-300 ease-in-out"
              enter-from-class="w-0 opacity-0 -translate-x-5"
              enter-to-class="opacity-100 translate-x-0"
              leave-from-class="opacity-100 translate-x-0"
              leave-to-class="w-0 opacity-0 -translate-x-5"
            >
              <div
                v-if="showSideList"
                class="border-border bg-background flex flex-shrink-0 flex-col overflow-hidden border-r transition-all duration-300 ease-in-out"
                :class="[sideListCollapsed ? 'w-0 border-0 p-0 opacity-0' : 'opacity-100']"
                :style="{ width: sideListCollapsed ? '0' : `${sideListConfig.width}px` }"
              >
                <div v-if="!sideListCollapsed" class="flex flex-shrink-0 flex-col overflow-hidden">
                  <!-- 标题和搜索 -->
                  <div
                    class="border-border mb-2 flex flex-shrink-0 items-center gap-2 border-b p-2"
                  >
                    <h3 class="flex-1 text-sm font-medium">{{ sideListConfig.title }}</h3>
                    <ElInput
                      v-model="sideListSearchKeyword"
                      placeholder="搜索"
                      clearable
                      class="flex-1"
                    />
                  </div>
                  <!-- 树形列表 -->
                  <div class="min-h-0 flex-1 overflow-y-auto p-2">
                    <ElTree
                      v-if="filteredSideListData.length > 0"
                      ref="treeRef"
                      :data="filteredSideListData"
                      :props="treeProps"
                      :node-key="sideListConfig.treeNodeKey"
                      :expand-on-click-node="false"
                      default-expand-all
                      highlight-current
                      @node-click="handleNodeClick"
                    />
                    <div
                      v-else
                      class="text-muted-foreground flex h-full items-center justify-center text-sm"
                    >
                      暂无数据
                    </div>
                  </div>
                </div>
              </div>
            </transition>

            <!-- 分隔线和收起按钮 -->
            <transition
              enter-active-class="transition-opacity duration-300 ease-in-out"
              leave-active-class="transition-opacity duration-300 ease-in-out"
              enter-from-class="opacity-0"
              enter-to-class="opacity-100"
              leave-from-class="opacity-100"
              leave-to-class="opacity-0"
            >
              <div
                v-if="showSideList"
                class="bg-border relative mx-3 flex w-px flex-shrink-0 items-start justify-center"
              >
                <ElButton
                  circle
                  class="border-border bg-background hover:border-primary hover:bg-primary hover:text-primary-foreground absolute left-1/2 top-[18px] z-10 h-6 w-6 -translate-x-1/2 border shadow-sm transition-colors"
                  @click="sideListCollapsed = !sideListCollapsed"
                >
                  <ElIcon>
                    <ArrowRight v-if="sideListCollapsed" />
                    <ArrowLeft v-else />
                  </ElIcon>
                </ElButton>
              </div>
            </transition>

            <!-- 主内容区域 -->
            <div class="relative min-h-0 min-w-0 flex-1 overflow-hidden">
              <div class="h-full w-full overflow-auto">
                <EditableTable
                  ref="editableTableRef"
                  :model-value="tableData"
                  :columns="columns"
                  :border="border"
                  :stripe="stripe"
                  :size="size"
                  :show-add-button="showAddButton"
                  :show-delete-button="showDeleteButton"
                  :add-button-text="addButtonText"
                  :delete-button-text="deleteButtonText"
                  :disabled="!canAddRow"
                  :row-key="rowKey"
                  :max-rows="maxRows"
                  :min-rows="minRows"
                  :default-row-data="defaultRowData"
                  :auto-focus="autoFocus"
                  :validation-errors="props.validationErrors"
                  @update:model-value="(val) => (tableData = val)"
                  @row-add="handleRowAdd"
                  @row-remove="(row, index) => emit('rowRemove', row, index)"
                  @cell-change="(row, column, value) => emit('cellChange', row, column, value)"
                />
              </div>
            </div>
          </div>
        </div>
        <div
          v-if="$slots.footer"
          ref="footerRef"
          :class="cn('bg-card align-center flex px-6 py-4', footerClass)"
        >
          <slot name="footer"></slot>
        </div>
      </div>
    </div>
  </div>
</template>
