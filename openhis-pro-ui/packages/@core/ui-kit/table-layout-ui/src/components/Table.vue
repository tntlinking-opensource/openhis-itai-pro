<script setup lang="ts">
import type { VxeGridInstance, VxeGridListeners } from 'vxe-table';

import type { TableProps } from '../types/Table.d';
import type { TableColumn } from '../types/TableLayout.d';

import { computed, ref, useSlots, useTemplateRef, watch } from 'vue';

import { VxeGrid } from 'vxe-table';

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
const tableHeight = ref<null | number>(null);

watch(
  () => props.tableData,
  () => {
    if (props.isAllData && internalPageNo.value !== 1 && props.tableData.length > 0) {
      internalPageNo.value = 1;
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
    options.height = `${tableHeight.value}px`;
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

const gridRef = useTemplateRef<VxeGridInstance>('gridRef');

const gridApi = computed(() => gridRef.value || null);

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
});
</script>

<template>
  <div ref="tableContainerRef" class="flex min-h-0 flex-col overflow-hidden">
    <div class="relative min-h-0 flex-1 overflow-hidden">
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
