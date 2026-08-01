<script setup lang="ts">
import type { VxeGridInstance, VxeGridListeners } from 'vxe-table';

import type { TableProps } from '../types/Table.d';
import type { TableColumn } from '../types/TableLayout.d';

import {
  computed,
  inject,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  useSlots,
  useTemplateRef,
  watch,
} from 'vue';

import { VxeGrid } from 'vxe-table';

import {
  findConstrainedOverlayMaxHeight,
  isInsideOverlayContainer,
  TABLE_LAYOUT_OVERLAY_HOST,
} from '../utils/overlay-container';

defineOptions({
  name: 'Table',
});

const props = withDefaults(defineProps<TableProps>(), {
  tableData: () => [],
  loading: false,
  border: true,
  stripe: false,
  size: 'default',
  highlightCurrentRow: false,
  tableColumns: () => [],
  showPagination: false,
  total: 0,
  pageNo: 1,
  pageSize: 20,
  isAllData: false,
  paginationLeftText: '',
  paginationProps: () => ({}),
  toolbarConfig: () => ({
    custom: false,
    export: false,
    refresh: false,
    zoom: true,
  }),
  tree: false,
  treeConfig: () => ({
    children: 'children',
    indent: 20,
    line: false,
    expandAll: false,
    accordion: false,
    trigger: 'default',
  }),
  expandConfig: () => ({}),
});

const emit = defineEmits<{
  pagination: [pagination: { limit: number; page: number }];
  rowClick: [row: Record<string, any>, column: any, event: Event];
  selectionChange: [selection: Record<string, any>[]];
  sortChange: [sortInfo: { column: any; order: string; prop: string }];
}>();

const slots = useSlots();

const convertColumns = (columns: TableColumn[]) => {
  return columns.map((col) => {
    const vxeCol: any = {
      field: col.prop,
      title: col.label,
      width: col.width,
      minWidth: col.minWidth,
      align: col.align || 'left',
      headerAlign: col.headerAlign || col.align || 'left',
      fixed: col.fixed === true ? 'left' : col.fixed || false,
      showOverflow: col.showOverflowTooltip === false ? false : 'tooltip',
    };

    if (col.sortable) {
      vxeCol.sortable = col.sortable;
    }

    switch (col.type) {
      case 'expand': {
        vxeCol.type = 'expand';
        vxeCol.width = col.width || 40;
        if (col.slot) {
          vxeCol.slots = { content: col.slot };
        }

        break;
      }
      case 'index': {
        vxeCol.type = 'seq';
        vxeCol.width = col.width || 50;
        vxeCol.title = col.label || '序号';

        break;
      }
      case 'selection': {
        vxeCol.type = 'checkbox';
        vxeCol.width = col.width || 50;
        break;
      }
      // No default
    }

    if (col.prop === 'activeFlag_enumText' && col.slot === 'activeFlag' && !slots.activeFlag) {
      vxeCol.cellRender = { name: 'CellActiveFlag' };
    } else if (col.slot && col.type !== 'expand') {
      vxeCol.slots = { default: col.slot };
    }

    if (col.formatter) {
      vxeCol.formatter = ({ row, column: vxeColumn, cellValue, rowIndex }: any) => {
        return col.formatter!(row, vxeColumn, cellValue, rowIndex);
      };
    }

    // 如果启用树形表格且该列设置了 treeNode，添加 treeNode 属性
    if (props.tree && col.treeNode) {
      vxeCol.treeNode = true;
    }

    return vxeCol;
  });
};

const vxeColumns = computed(() => convertColumns(props.tableColumns));

/** VxeGrid 需在 `checkboxConfig.checkMethod` 上控制行是否可勾选；列上的 checkMethod 在部分版本不生效 */
const checkboxConfigFromSelection = computed(() => {
  const selCol = props.tableColumns.find(
    (c) => c.type === 'selection' && typeof c.selectable === 'function',
  );
  if (!selCol?.selectable) {
    return undefined;
  }
  const selectableFn = selCol.selectable;
  return {
    checkMethod: (params: Record<string, any>) => {
      const row = params?.row;
      if (!row || typeof row !== 'object') {
        return false;
      }
      const idx =
        typeof params.rowIndex === 'number'
          ? params.rowIndex
          : typeof params.$rowIndex === 'number'
            ? params.$rowIndex
            : 0;
      return selectableFn(row, idx);
    },
  };
});

const internalPageNo = ref(props.pageNo);
const internalPageSize = ref(props.pageSize);

watch(
  () => [props.pageNo, props.pageSize],
  ([newPageNo, newPageSize]) => {
    if (!props.isAllData) {
      internalPageNo.value = newPageNo ?? 1;
      internalPageSize.value = newPageSize ?? 20;
    }
  },
);

watch(
  () => props.isAllData,
  (isAllData) => {
    if (isAllData) {
      internalPageNo.value = props.pageNo;
      internalPageSize.value = props.pageSize;
    }
  },
);

const computedPageNo = computed(() => {
  return props.isAllData ? internalPageNo.value : props.pageNo;
});

const computedPageSize = computed(() => {
  return props.isAllData ? internalPageSize.value : props.pageSize;
});

const computedTotal = computed(() => {
  return props.isAllData ? props.tableData.length : props.total;
});

const computedTableData = computed(() => {
  if (!props.isAllData) {
    return props.tableData;
  }
  const start = (computedPageNo.value - 1) * computedPageSize.value;
  const end = start + computedPageSize.value;
  return props.tableData.slice(start, end);
});

const tableContainerRef = ref<HTMLDivElement | null>(null);
const tableBodyRef = ref<HTMLDivElement | null>(null);
/** 自动测得的高度上限（一律按 maxHeight 使用，避免强制 height 把表体撑高） */
const tableHeight = ref<null | number>(null);
/** 弹层内：默认内容高度；仅存在限高滚动容器时才设 maxHeight */
const insideOverlay = ref(false);
const overlayHostInjected = inject(TABLE_LAYOUT_OVERLAY_HOST, false);

function resolveInsideOverlay() {
  return overlayHostInjected === true || isInsideOverlayContainer(tableContainerRef.value);
}

function updateAutoTableHeight() {
  if (props.tableHeight || props.maxHeight) {
    return;
  }
  const el = tableBodyRef.value;
  if (!el) {
    return;
  }
  if (insideOverlay.value) {
    // 弹层：有固定滚动容器则封顶，否则不锁高度（确认收费等场景）
    tableHeight.value = findConstrainedOverlayMaxHeight(el);
    return;
  }
  const height = el.clientHeight;
  if (height > 0) {
    tableHeight.value = height;
  }
}

function bindAutoHeightObserver() {
  tableResizeObserver?.disconnect();
  tableResizeObserver = null;
  if (props.tableHeight || props.maxHeight) {
    tableHeight.value = null;
    return;
  }
  updateAutoTableHeight();
  if (typeof ResizeObserver === 'undefined' || !tableBodyRef.value) {
    return;
  }
  // 弹层且无限高容器：不监听，避免父级被内容撑开后反复加高
  if (insideOverlay.value && tableHeight.value == null) {
    return;
  }
  tableResizeObserver = new ResizeObserver(() => {
    updateAutoTableHeight();
  });
  tableResizeObserver.observe(tableBodyRef.value);
}

let tableResizeObserver: null | ResizeObserver = null;

onMounted(() => {
  insideOverlay.value = resolveInsideOverlay();
  // teleport 挂载后再测一次，避免首帧尚未挂到 dialog 根上
  nextTick(() => {
    insideOverlay.value = resolveInsideOverlay();
    setTimeout(() => {
      bindAutoHeightObserver();
    }, 50);
  });
});

onBeforeUnmount(() => {
  tableResizeObserver?.disconnect();
  tableResizeObserver = null;
});

watch(
  () => [props.tableHeight, props.maxHeight],
  () => {
    if (!props.tableHeight && !props.maxHeight) {
      bindAutoHeightObserver();
      return;
    }
    tableHeight.value = null;
    tableResizeObserver?.disconnect();
    tableResizeObserver = null;
  },
);

const gridRef = useTemplateRef<VxeGridInstance>('gridRef');

const gridApi = computed(() => gridRef.value || null);

/** 清空表格勾选（Vxe 内部态 + 通知业务侧 selectionChange([])） */
async function clearSelection() {
  await nextTick();
  const grid = gridRef.value as
    | null
    | (VxeGridInstance & {
        clearCheckboxReserve?: () => void;
        clearCheckboxRow?: () => void;
      });
  if (grid) {
    if (typeof grid.clearCheckboxRow === 'function') {
      grid.clearCheckboxRow();
    }
    if (typeof grid.clearCheckboxReserve === 'function') {
      grid.clearCheckboxReserve();
    }
  }
  emit('selectionChange', []);
}

watch(
  () => props.tableData,
  async () => {
    if (props.isAllData && internalPageNo.value !== 1 && props.tableData.length > 0) {
      internalPageNo.value = 1;
    }
    // 列表数据刷新后同步清空勾选，避免业务侧 selectedRows 已空但 UI 仍勾选
    await clearSelection();
  },
  { flush: 'post' },
);

watch(
  () => props.loading,
  (loading) => {
    if (!loading && !props.tableHeight && !props.maxHeight) {
      nextTick(() => updateAutoTableHeight());
    }
  },
);

const hasToolbarActions = computed(() => !!slots['toolbar-actions']);
const hasToolbarTools = computed(() => !!slots['toolbar-tools']);

const gridOptions = computed(() => {
  const toolbarConfig: any = {
    ...props.toolbarConfig,
  };

  if (hasToolbarActions.value || hasToolbarTools.value) {
    toolbarConfig.slots = {
      ...(hasToolbarActions.value ? { buttons: 'toolbar-actions' } : {}),
      ...(hasToolbarTools.value ? { tools: 'toolbar-tools' } : {}),
    };
  }

  const options: any = {
    autoResize: true,
    data: computedTableData.value,
    columns: vxeColumns.value,
    border: props.border,
    stripe: props.stripe,
    size: props.size === 'default' ? 'medium' : props.size,
    rowConfig: {
      keyField: typeof props.rowKey === 'string' ? props.rowKey : undefined,
      isHover: props.highlightCurrentRow,
      ...(props.rowClassName ? { className: props.rowClassName } : {}),
    },
    columnConfig: {
      resizable: true,
    },
    pagerConfig: props.showPagination
      ? {
          enabled: true,
          currentPage: computedPageNo.value,
          pageSize: computedPageSize.value,
          total: computedTotal.value,
          pageSizes: props.paginationProps?.pageSizes || [10, 20, 50, 100],
          layouts: [
            'PrevJump',
            'PrevPage',
            'Number',
            'NextPage',
            'NextJump',
            'Sizes',
            'FullJump',
            'Total',
          ],
        }
      : {
          enabled: false,
        },
    sortConfig: {
      multiple: true,
    },
    ...(props.spanMethod ? { spanMethod: props.spanMethod } : {}),
    loading: props.loading,
    toolbarConfig,
    ...(checkboxConfigFromSelection.value
      ? { checkboxConfig: checkboxConfigFromSelection.value }
      : {}),
  };

  // 如果有展开配置，添加到选项中
  if (props.expandConfig && Object.keys(props.expandConfig).length > 0) {
    options.expandConfig = props.expandConfig;
  }

  // 如果启用树形表格，添加 treeConfig
  if (props.tree) {
    options.treeConfig = {
      children: props.treeConfig?.children || 'children',
      indent: props.treeConfig?.indent || 20,
      showLine: props.treeConfig?.line === undefined ? false : props.treeConfig.line,
      expandAll: props.treeConfig?.expandAll || false,
      accordion: props.treeConfig?.accordion || false,
      trigger: props.treeConfig?.trigger || 'default',
    };
  }

  if (props.tableHeight) {
    options.height =
      typeof props.tableHeight === 'string' ? props.tableHeight : `${props.tableHeight}px`;
  } else if (props.maxHeight) {
    options.maxHeight =
      typeof props.maxHeight === 'string' ? props.maxHeight : `${props.maxHeight}px`;
  } else if (tableHeight.value) {
    // 用 maxHeight 而非 height：少行按内容，多行在容器内滚动，避免弹窗打开后表体被撑高
    options.maxHeight = `${tableHeight.value}px`;
  }

  return options;
});

const gridEvents = computed(() => ({
  cellClick: ({ row, column, $event }: any) => {
    emit('rowClick', row, column, $event);
  },
  checkboxChange: ({ records }: any) => {
    emit('selectionChange', records);
  },
  checkboxAll: ({ records }: any) => {
    emit('selectionChange', records);
  },
  sortChange: ({ column, property, order }: any) => {
    emit('sortChange', {
      column,
      prop: property || '',
      order: order || '',
    });
  },
  pageChange: ({ currentPage, pageSize }: any) => {
    if (props.isAllData) {
      internalPageNo.value = currentPage;
      internalPageSize.value = pageSize;
    } else {
      emit('pagination', {
        page: currentPage,
        limit: pageSize,
      });
    }
  },
}));

const columnSlots = computed(() => {
  const resultSlots: string[] = [];
  for (const key of Object.keys(slots)) {
    if (
      key !== 'toolbar-actions' &&
      key !== 'toolbar-tools' &&
      key !== 'paginationLeft' &&
      key !== 'operations'
    ) {
      resultSlots.push(key);
    }
  }
  return resultSlots;
});

defineExpose({
  tableWrapperRef: tableContainerRef,
  gridApi,
  gridRef,
  clearSelection,
});
</script>

<template>
  <div
    ref="tableContainerRef"
    class="flex min-h-0 w-full min-w-0 flex-col overflow-hidden"
    :class="insideOverlay ? 'h-auto' : 'h-full flex-1'"
  >
    <div
      ref="tableBodyRef"
      class="relative min-h-0 w-full min-w-0 overflow-hidden"
      :class="insideOverlay ? '' : 'flex-1'"
    >
      <VxeGrid ref="gridRef" v-bind="gridOptions" v-on="gridEvents as VxeGridListeners">
        <template #toolbar-actions>
          <slot name="toolbar-actions"></slot>
        </template>
        <template #toolbar-tools>
          <slot name="toolbar-tools"></slot>
        </template>
        <template v-for="slotName in columnSlots" :key="slotName" #[slotName]="slotProps">
          <slot :name="slotName" v-bind="slotProps"></slot>
        </template>
      </VxeGrid>
    </div>
    <div
      v-if="showPagination && paginationLeftText"
      class="flex-shrink-0 py-2 text-sm text-[var(--el-text-color-regular)]"
    >
      <slot name="paginationLeft">
        {{ paginationLeftText }}
      </slot>
    </div>
  </div>
</template>

<style scoped lang="scss">
:deep(.vxe-grid) {
  min-height: 0;

  .vxe-table--body-wrapper {
    max-height: none;
    overflow: auto;
  }
}
</style>
