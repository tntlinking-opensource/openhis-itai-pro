<script setup lang="ts">
import type { VxeTableInstance } from 'vxe-table';

import type {
  EditableTableColumn,
  EditableTableExpose,
  EditableTableProps,
  RowVO,
} from '../types/EditableTable.d';

import { computed, h, nextTick, onMounted, onUnmounted, ref, useTemplateRef, watch } from 'vue';

import { Plus } from '@vben-core/icons';
import { VbenButton } from '@vben-core/shadcn-ui';

import { ElMessageBox } from 'element-plus';
import { Trash2 } from 'lucide-vue-next';
import { VxeColumn, VxeTable, VxeUI } from 'vxe-table';

/** 供 CustomEditRenderer 按 field 解析列配置（跨组件实例共享） */
const customEditColumnResolver = ref<((field: string) => EditableTableColumn | undefined) | null>(
  null,
);

let customEditRendererRegistered = false;

function ensureCustomEditRenderer() {
  if (customEditRendererRegistered) {
    return;
  }
  customEditRendererRegistered = true;
  VxeUI.renderer.add('CustomEditRenderer', {
    renderTableEdit(_renderOpts, params) {
      const { row, column } = params;
      const field = column.field;
      if (!field || !customEditColumnResolver.value) {
        return null;
      }
      const colConfig = customEditColumnResolver.value(field);
      if (!colConfig?.editRender) {
        return null;
      }
      const component = colConfig.editRender(row, colConfig);
      if (typeof component === 'string') {
        return h('span', component);
      }
      return h(component);
    },
  });
}

defineOptions({
  name: 'EditableTable',
});

const props = withDefaults(defineProps<EditableTableProps>(), {
  modelValue: () => [],
  columns: () => [],
  stripe: false,
  showAddButton: true,
  showCheckboxColumn: true,
  showDeleteButton: true,
  showSeqColumn: true,
  addButtonText: '添加',
  deleteButtonText: '删除',
  disabled: false,
  rowKey: 'id',
  maxRows: undefined,
  minRows: 0,
  defaultRowData: () => ({}),
  autoFocus: true,
  height: undefined,
  maxHeight: undefined,
  validationErrors: () => ({}),
  validRules: () => ({}),
});

const emit = defineEmits<{
  cellChange: [row: Record<string, any>, column: EditableTableColumn, value: any];
  rowAdd: [row: Record<string, any>, index: number];
  rowRemove: [row: Record<string, any>, index: number];
  selectAllChangeEvent: [selected: boolean];
  selectChangeEvent: [selected: boolean, row: Record<string, any>];
  'update:modelValue': [value: Record<string, any>[]];
}>();

const tableData = ref<Record<string, any>[]>([]);
const tableRef = useTemplateRef<VxeTableInstance>('tableRef');
const containerRef = ref<HTMLDivElement | null>(null);
const calculatedHeight = ref<number | undefined>(undefined);
// 存储字段名到列配置的映射
const columnConfigMap = ref<Map<string, EditableTableColumn>>(new Map());

// 计算表格高度
const calculateHeight = () => {
  if (!containerRef.value) return;

  setTimeout(() => {
    if (!containerRef.value) return;

    nextTick(() => {
      if (!containerRef.value) return;

      const containerRect = containerRef.value.getBoundingClientRect();
      if (containerRect.height <= 0) {
        setTimeout(() => calculateHeight(), 100);
        return;
      }

      let height = containerRect.height;

      const addButtonElement = containerRef.value.querySelector('.mb-2');
      if (addButtonElement) {
        height -= addButtonElement.getBoundingClientRect().height + 8;
      }

      calculatedHeight.value = height > 0 ? Math.max(height, 220) : 220;
    });
  }, 50);
};

const tableHeight = computed(() => {
  return props.height ?? calculatedHeight.value;
});

let resizeObserver: null | ResizeObserver = null;

onMounted(() => {
  customEditColumnResolver.value = (field) => columnConfigMap.value.get(field);
  ensureCustomEditRenderer();

  setTimeout(() => {
    calculateHeight();

    if (containerRef.value && window.ResizeObserver) {
      resizeObserver = new ResizeObserver(() => {
        calculateHeight();
      });
      resizeObserver.observe(containerRef.value);
    }
  }, 100);
});

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect();
  }
});

watch(tableHeight, (newHeight) => {
  if (newHeight && tableRef.value) {
    nextTick(() => {
      tableRef.value?.recalculate();
    });
  }
});

watch(
  () => props.columns,
  (columns) => {
    columnConfigMap.value.clear();
    columns.forEach((col) => {
      if (col.prop) {
        columnConfigMap.value.set(col.prop, col);
      }
    });
  },
  { immediate: true },
);

// 防止循环更新的标志
let isInternalUpdate = false;

// 同步外部数据
watch(
  () => props.modelValue,
  (newVal) => {
    if (isInternalUpdate) {
      return;
    }
    const currentData = tableData.value.map(({ _rowIndex, ...row }) => row);
    // 深度比较，避免不必要的更新
    if (JSON.stringify(newVal) !== JSON.stringify(currentData)) {
      isInternalUpdate = true;
      tableData.value = newVal.map((row, index) => ({
        ...row,
        _rowIndex: index,
      }));
      isInternalUpdate = false;
    }
  },
  { immediate: true, deep: true },
);

// 监听内部数据变化，同步到外部
watch(
  tableData,
  (newVal) => {
    if (isInternalUpdate) {
      return;
    }
    isInternalUpdate = true;
    nextTick(() => {
      const data = newVal.map(({ _rowIndex, ...row }) => row);
      emit('update:modelValue', data);
      nextTick(() => {
        isInternalUpdate = false;
      });
    });
  },
  { deep: true },
);

// 添加新行
const addRow = async () => {
  const $table = tableRef.value;
  if ($table) {
    const record = props.defaultRowData?.();
    const newRow = await $table.createRow(record);
    tableData.value.unshift(newRow);
    await nextTick();
    $table.setEditRow(newRow, props.columns[0]!.prop);
  }
};

// 删除行
const removeRow = (row: RowVO) => {
  ElMessageBox.confirm('确定删除该行吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    tableData.value = tableData.value.filter((item) => item.id !== row.id);
  });
};

// 处理行点击事件
const handleRowClick = ({ row }: { row: Record<string, any> }) => {
  if (props.disabled || !tableRef.value) return;
  const isEdit = tableRef.value.isEditByRow(row);
  if (!isEdit) {
    tableRef.value.setEditRow(row);
  }
};

const handleSelectAllChange = (params: { checked: boolean }) => {
  emit('selectAllChangeEvent', params.checked);
};

const handleSelectChange = (params: { checked: boolean; row: Record<string, any> }) => {
  emit('selectChangeEvent', params.checked, params.row);
};

type VxeEditRendererConfig =
  | undefined
  | {
      name: string;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      render?: (params: any) => any;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      props?: Record<string, any>;
    };

/** 列是否参与行编辑（默认 true，显式 editable: false 除外） */
function isColumnEditable(column: EditableTableColumn): boolean {
  return column.editable !== false;
}

const getEditRender = (column: EditableTableColumn): VxeEditRendererConfig => {
  if (column.editable === false) {
    return undefined;
  }

  if (column.editType === 'custom' && column.editRender) {
    return {
      name: 'CustomEditRenderer',
    };
  }

  if (column.editType === 'date') {
    return {
      name: 'ElDatePicker',
      props: {
        type: 'date',
        placeholder: column.editPlaceholder || `请选择${column.label}`,
        disabled: props.disabled,
        clearable: true,
        class: '!w-full',
        ...column.editProps,
      },
    };
  }

  if (column.editType === 'select') {
    const selectOptions =
      typeof column.editOptions === 'function'
        ? column.editOptions({}, column)
        : column.editOptions;
    return {
      name: 'ElSelect',
      props: {
        disabled: props.disabled,
        options: selectOptions,
        optionProps: {
          label: 'label',
          value: 'value',
        },
        placeholder: column.editPlaceholder,
        clearable: true,
        class: 'w-full',
        ...column.editProps,
      },
    };
  }
  if (column.editType === 'number') {
    return {
      name: 'ElInputNumber',
      props: {
        placeholder: column.editPlaceholder || `请输入${column.label}`,
        disabled: props.disabled,
        class: 'editable-table-input border',
        controlsPosition: 'right',
      },
    };
  }
  if (column.editType === 'textarea') {
    return {
      name: 'ElInput',
      props: {
        type: 'textarea',
        placeholder: column.editPlaceholder || `请输入${column.label}`,
        disabled: props.disabled,
        class: 'editable-table-textarea border',
      },
    };
  }

  return {
    name: 'ElInput',
    props: {
      placeholder: column.editPlaceholder || `请输入${column.label}`,
      disabled: props.disabled,
      class: 'editable-table-input border',
    },
  };
};

defineExpose<EditableTableExpose>({
  addRow,
  removeRow,
  getData: () => tableData.value.map(({ _rowIndex, ...row }) => row),
  setData: (data: Record<string, any>[]) => {
    tableData.value = data.map((row, index) => ({
      ...row,
      _rowIndex: index,
    }));
  },
  validate: async () => {
    if (tableRef.value) {
      const errMap = await tableRef.value.validate();
      return !errMap || Object.keys(errMap).length === 0;
    }
    return true;
  },
  clear: () => {
    tableData.value = [];
  },
});
</script>

<template>
  <div ref="containerRef" class="h-full w-full">
    <div
      v-if="showAddButton || $slots['toolbar-tools']"
      class="mb-2 flex items-center justify-start"
    >
      <slot name="toolbar-tools">
        <VbenButton
          v-if="showAddButton"
          variant="default"
          type="button"
          :disabled="disabled || (maxRows ? tableData.length >= maxRows : false)"
          @click.prevent="addRow()"
        >
          <Plus :size="16" class="mr-1" />
          {{ addButtonText }}
        </VbenButton>
      </slot>
    </div>
    <VxeTable
      ref="tableRef"
      :data="tableData"
      :height="tableHeight"
      :max-height="maxHeight"
      border
      :stripe="stripe"
      size="medium"
      :row-config="{ keyField: rowKey }"
      show-overflow
      :edit-rules="validRules"
      :edit-config="{
        mode: 'row',
        trigger: 'click',
        enabled: !disabled,
        autoClear: false,
        showStatus: true,
      }"
      :scrollbar-config="{
        x: {
          visible: 'visible',
          position: 'bottom',
        },
      }"
      @row-click="handleRowClick"
      @checkbox-all="handleSelectAllChange"
      @checkbox-change="handleSelectChange"
    >
      <!-- 序号列 -->
      <VxeColumn v-if="showCheckboxColumn" type="checkbox" width="60" fixed="left" align="center" />
      <VxeColumn
        v-if="showSeqColumn"
        type="seq"
        title="序号"
        width="60"
        align="center"
        fixed="left"
      />
      <!-- 数据列 -->
      <VxeColumn
        v-for="column in props.columns"
        :key="column.prop"
        :field="column.prop"
        :title="column.label"
        :width="column.width"
        :min-width="column.minWidth"
        :align="column.align || 'left'"
        :fixed="
          typeof column.fixed === 'boolean' ? (column.fixed ? 'left' : undefined) : column.fixed
        "
        :edit-render="getEditRender(column)"
      >
        <template #default="{ row }" v-if="column.formatterRender">
          <component :is="column.formatterRender!(row, column)" />
        </template>
        <template v-else #default="{ row, $rowIndex, $table }">
          <span v-if="!isColumnEditable(column) || !$table.isEditByRow(row)">
            {{
              column.formatter
                ? column.formatter(row, column, row[column.prop!], $rowIndex)
                : (row[column.prop!] ?? '')
            }}
          </span>
        </template>
      </VxeColumn>
      <!-- 操作列 -->
      <VxeColumn v-if="showDeleteButton" title="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <VbenButton
            variant="link"
            type="button"
            class="text-destructive hover:text-destructive/80"
            :disabled="disabled || (minRows ? tableData.length <= minRows : false)"
            @click.prevent="removeRow(row)"
          >
            <Trash2 :size="14" class="mr-1" />
            {{ deleteButtonText }}
          </VbenButton>
        </template>
      </VxeColumn>
    </VxeTable>
  </div>
</template>
