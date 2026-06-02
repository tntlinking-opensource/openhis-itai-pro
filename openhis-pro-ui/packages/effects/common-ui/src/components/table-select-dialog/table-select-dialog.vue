<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import { computed, nextTick, ref, watch } from 'vue';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElDialog, ElIcon, ElInput, ElTabPane, ElTabs } from 'element-plus';

export interface TableSelectItem {
  [key: string]: any;
}

interface Props {
  modelValue?: null | number | number[] | string | string[];
  placeholder?: string;
  multiple?: boolean;
  columns: TableColumn[];
  formItems?: FormItemConfig[];
  /** 全数据模式：传入全部数据时，分页与筛选在前端完成；为 null/undefined 时走远端请求 */
  data?: null | TableSelectItem[];
  request: (params: Record<string, any>) => Promise<{
    [key: string]: any;
    content?: any[];
    list?: any[];
    records?: any[];
    total?: number;
    totalElements?: number;
  }>;
  displayField?: string;
  valueField?: string;
  dialogTitle?: string;
  dialogWidth?: string;
  inputClass?: string;
  showSideQuery?: boolean;
  siderData?: any[];
  /** 是否显示输入框 */
  showInput?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请选择',
  multiple: false,
  formItems: () => [],
  data: null,
  displayField: 'name',
  valueField: 'id',
  dialogTitle: '请选择',
  dialogWidth: '60%',
  inputClass: 'w-[220px]',
  showSideQuery: false,
  siderData: () => [],
  showInput: true,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: null | number | number[] | string | string[]): void;
  (e: 'change', item: null | TableSelectItem | TableSelectItem[]): void;
}>();

const dialogVisible = ref(false);
const activeTab = ref('list');
const tableData = ref<TableSelectItem[]>([]);
const loading = ref(false);
const total = ref(0);
const selectedRows = ref<TableSelectItem[]>([]);
const tableLayoutRef = ref<any>(null);

// 是否启用前端全数据模式（只要传入 data 不为 null 即启用，允许空数组）
const isClientMode = computed(() => props.data !== null && props.data !== undefined);

const initQueryParams = () => {
  const params: Record<string, any> & { pageNo: number; pageSize: number } = {
    pageNo: 1,
    pageSize: 20,
  };

  if (props.formItems && Array.isArray(props.formItems)) {
    props.formItems.forEach((item) => {
      if (item.prop && item.type !== 'custom') {
        params[item.prop] = '';
      }
    });
  }

  return params;
};

const queryParams = ref<Record<string, any> & { pageNo: number; pageSize: number }>(
  initQueryParams(),
);

watch(
  () => props.formItems,
  () => {
    const newParams = initQueryParams();
    Object.keys(newParams).forEach((key) => {
      if (!(key in queryParams.value)) {
        queryParams.value[key] = newParams[key];
      }
    });
  },
  { immediate: true },
);

const innerValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
});

const selectedList = computed(() => {
  if (!props.multiple) return [];
  return selectedRows.value;
});

const displayText = computed(() => {
  if (props.multiple) {
    if (selectedRows.value.length === 0) return '';
    const texts = selectedRows.value.map((item) => item[props.displayField]).filter(Boolean);
    if (texts.length === 0) return '';
    return texts.join('，');
  } else {
    if (!innerValue.value) return '';
    let item = tableData.value.find(
      (item) => String(item[props.valueField]) === String(innerValue.value),
    );
    if (!item) {
      item = selectedRows.value.find(
        (item) => String(item[props.valueField]) === String(innerValue.value),
      );
    }
    return item?.[props.displayField] || '';
  }
});

const initialized = ref(false);
watch(
  () => props.modelValue,
  async (val) => {
    if (val && !initialized.value && tableData.value.length === 0) {
      await fetchList();
      initialized.value = true;
      updateSelectedRowsFromValue();
    } else if (val) {
      updateSelectedRowsFromValue();
    } else {
      selectedRows.value = [];
    }
  },
  { immediate: true, deep: true },
);

watch(
  () => dialogVisible.value,
  async (visible) => {
    if (visible) {
      activeTab.value = 'list';

      const resetParams = initQueryParams();
      resetParams.pageNo = 1;
      resetParams.pageSize = queryParams.value.pageSize || 20;
      queryParams.value = resetParams;

      await fetchList();
      await updateSelectedRowsOnOpen();
    }
  },
);

async function updateSelectedRowsOnOpen() {
  if (props.multiple) {
    if (Array.isArray(innerValue.value) && innerValue.value.length > 0) {
      const values = innerValue.value as any[];

      const otherPageSelected = selectedRows.value.filter(
        (row) =>
          values.includes(row[props.valueField]) &&
          !tableData.value.some((item) => item[props.valueField] === row[props.valueField]),
      );

      const currentPageSelected = tableData.value.filter((item) =>
        values.includes(item[props.valueField]),
      );

      selectedRows.value = [...otherPageSelected, ...currentPageSelected];

      nextTick(() => {
        setTableSelection();
      });
    } else {
      selectedRows.value = [];
    }
  } else {
    if (innerValue.value) {
      const item = tableData.value.find(
        (item) => String(item[props.valueField]) === String(innerValue.value),
      );
      if (item) {
        selectedRows.value = [item];
        nextTick(() => {
          setTableSelection();
        });
      } else {
        selectedRows.value = selectedRows.value.filter(
          (row) => String(row[props.valueField]) === String(innerValue.value),
        );
      }
    } else {
      selectedRows.value = [];
    }
  }
}

function setTableSelection() {
  if (!props.multiple) return;

  nextTick(() => {
    setTimeout(() => {
      try {
        const gridApi = tableLayoutRef.value?.gridApi;

        if (!gridApi) {
          setTimeout(() => setTableSelection(), 200);
          return;
        }

        if (Array.isArray(innerValue.value) && innerValue.value.length > 0) {
          const values = innerValue.value as any[];

          const rowsToSelect = tableData.value.filter((tableRow) =>
            values.includes(tableRow[props.valueField]),
          );

          if (rowsToSelect.length > 0) {
            try {
              const gridInstance = (gridApi as any).$refs?.grid || (gridApi as any).grid || gridApi;

              const methods = [
                'setCheckboxRow',
                'toggleCheckboxRow',
                'setCheckboxRowChecked',
                'setCheckboxReserve',
              ];

              let success = false;
              for (const method of methods) {
                if (typeof gridInstance[method] === 'function') {
                  rowsToSelect.forEach((row) => {
                    try {
                      if (method === 'toggleCheckboxRow') {
                        const isChecked = gridInstance.isCheckboxRowChecked?.(row);
                        if (!isChecked) {
                          gridInstance[method](row);
                        }
                      } else {
                        gridInstance[method](row, true);
                      }
                    } catch {
                      // ignore
                    }
                  });
                  success = true;
                  break;
                }
              }

              if (!success) {
                console.warn('[TableSelectDialog] 无法通过 API 设置选中状态');
              }
            } catch (error) {
              console.error('[TableSelectDialog] 设置选中状态失败:', error);
            }
          }
        }
      } catch (error) {
        console.error('[TableSelectDialog] 设置表格选中状态失败:', error);
      }
    }, 300);
  });
}

function updateSelectedRowsFromValue() {
  if (props.multiple) {
    if (Array.isArray(innerValue.value) && innerValue.value.length > 0) {
      const currentPageSelected = tableData.value.filter((item) =>
        (innerValue.value as any[]).includes(item[props.valueField]),
      );

      const otherPageSelected = selectedRows.value.filter(
        (row) =>
          (innerValue.value as any[]).includes(row[props.valueField]) &&
          !tableData.value.some((item) => item[props.valueField] === row[props.valueField]),
      );

      selectedRows.value = [...otherPageSelected, ...currentPageSelected];
    } else {
      selectedRows.value = [];
    }
  } else {
    if (innerValue.value) {
      const item = tableData.value.find(
        (item) => String(item[props.valueField]) === String(innerValue.value),
      );
      selectedRows.value = item
        ? [item]
        : selectedRows.value.filter(
            (row) => String(row[props.valueField]) === String(innerValue.value),
          );
    } else {
      selectedRows.value = [];
    }
  }
}

function updateSelectedRows() {
  if (props.multiple) {
    if (Array.isArray(innerValue.value) && innerValue.value.length > 0) {
      const currentPageSelected = tableData.value.filter((item) =>
        (innerValue.value as any[]).includes(item[props.valueField]),
      );

      const otherPageSelected = selectedRows.value.filter(
        (row) => !tableData.value.some((item) => item[props.valueField] === row[props.valueField]),
      );

      selectedRows.value = [...otherPageSelected, ...currentPageSelected];
    } else {
      selectedRows.value = selectedRows.value.filter(
        (row) => !tableData.value.some((item) => item[props.valueField] === row[props.valueField]),
      );
    }
  } else {
    if (innerValue.value) {
      const item = tableData.value.find(
        (item) => String(item[props.valueField]) === String(innerValue.value),
      );
      selectedRows.value = item
        ? [item]
        : selectedRows.value.filter(
            (row) => String(row[props.valueField]) === String(innerValue.value),
          );
    } else {
      selectedRows.value = [];
    }
  }
}

async function fetchList() {
  loading.value = true;
  try {
    if (isClientMode.value) {
      // 前端筛选与分页
      const source = Array.isArray(props.data) ? (props.data as TableSelectItem[]) : [];
      const qp = queryParams.value;
      const filtered = source.filter((row) => {
        // 对除分页字段外的查询项做包含匹配（字符串忽略大小写）
        return Object.keys(qp).every((key) => {
          if (key === 'pageNo' || key === 'pageSize') return true;
          const val = qp[key];
          if (val === '' || val === null || val === undefined) return true;
          const cell = (row as any)[key];
          if (cell === null || cell === undefined) return false;
          return String(cell).toLowerCase().includes(String(val).toLowerCase());
        });
      });
      total.value = filtered.length;
      const start = Math.max(0, (qp.pageNo - 1) * qp.pageSize);
      const end = start + qp.pageSize;
      tableData.value = filtered.slice(start, end);
    } else {
      const res = await props.request({
        ...queryParams.value,
      });
      const data = res.content || res.records || res.list || [];
      tableData.value = Array.isArray(data) ? data : [];
      total.value = Number(res.totalElements || res.total || 0);
    }

    if (innerValue.value) {
      updateSelectedRowsFromValue();
    } else {
      updateSelectedRows();
    }

    nextTick(() => {
      setTableSelection();
    });
  } catch (error) {
    console.error('[TableSelectDialog] 请求数据失败:', error);
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function handleQuery(params: Record<string, any>) {
  queryParams.value = {
    ...queryParams.value,
    ...params,
    pageNo: 1,
  };
  fetchList();
}

function handleReset() {
  const resetParams = initQueryParams();
  resetParams.pageSize = queryParams.value.pageSize || 20;
  queryParams.value = resetParams;
  fetchList();
}

function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  fetchList();
}

function handleSelectionChange(selection: Record<string, any>[]) {
  const typedSelection = selection as TableSelectItem[];
  if (props.multiple) {
    selectedRows.value = selectedRows.value.filter(
      (row) => !tableData.value.some((item) => item[props.valueField] === row[props.valueField]),
    );

    selectedRows.value = [...selectedRows.value, ...typedSelection];

    const values = selectedRows.value.map((item) => item[props.valueField]);
    innerValue.value = values.length > 0 ? values : null;
  } else {
    if (typedSelection.length > 0) {
      const item = typedSelection[0];
      if (item) {
        innerValue.value = item[props.valueField];
        selectedRows.value = [item];
        emit('change', item);
        dialogVisible.value = false;
      }
    }
  }
}

function toggleRowCheckbox(row: Record<string, any>, checked: boolean) {
  if (!props.multiple) return;

  nextTick(() => {
    try {
      const gridApi = tableLayoutRef.value?.gridApi;
      if (!gridApi) return;

      const gridInstance = (gridApi as any).$refs?.grid || (gridApi as any).grid || gridApi;

      const methods = ['setCheckboxRow', 'toggleCheckboxRow', 'setCheckboxRowChecked'];
      for (const method of methods) {
        if (typeof gridInstance[method] === 'function') {
          try {
            if (method === 'toggleCheckboxRow') {
              const isChecked = gridInstance.isCheckboxRowChecked?.(row);
              if (isChecked !== checked) {
                gridInstance[method](row);
              }
            } else {
              gridInstance[method](row, checked);
            }
            break;
          } catch {
            // ignore
          }
        }
      }
    } catch {
      // ignore
    }
  });
}

function handleRowClick(row: Record<string, any>) {
  const item = row as TableSelectItem;

  if (props.multiple) {
    const isSelected = selectedRows.value.some(
      (selected) => String(selected[props.valueField]) === String(item[props.valueField]),
    );

    const newSelected = !isSelected;

    selectedRows.value = newSelected
      ? [...selectedRows.value, item]
      : selectedRows.value.filter(
          (selected) => String(selected[props.valueField]) !== String(item[props.valueField]),
        );

    const values = selectedRows.value.map((selected) => selected[props.valueField]);
    innerValue.value = values.length > 0 ? values : null;

    // 同步更新表格的 checkbox 状态
    toggleRowCheckbox(row, newSelected);

    // 多选模式下不立即触发 change 事件，等确认后再触发
  } else {
    innerValue.value = item[props.valueField];
    selectedRows.value = [item];
    emit('change', item);
    dialogVisible.value = false;
  }
}

function handleRemoveSelected(item: TableSelectItem) {
  selectedRows.value = selectedRows.value.filter(
    (row) => String(row[props.valueField]) !== String(item[props.valueField]),
  );
  const values = selectedRows.value.map((row) => row[props.valueField]);
  innerValue.value = values.length > 0 ? values : null;
  // 多选模式下不立即触发 change 事件，等确认后再触发
}

function handleClearSelected() {
  selectedRows.value = [];
  innerValue.value = null;
  // 多选模式下不立即触发 change 事件，等确认后再触发
}

function handleConfirm() {
  const values = selectedRows.value.map((item) => item[props.valueField]);
  innerValue.value = values.length > 0 ? values : null;
  emit('change', selectedRows.value.length > 0 ? selectedRows.value : null);
  dialogVisible.value = false;
}

const tableColumnsWithSelection = computed(() => {
  const columns: TableColumn[] = [...props.columns];
  const hasSelection = columns.some((col) => col.type === 'selection');
  if (!hasSelection && props.multiple) {
    columns.unshift({
      type: 'selection',
      fixed: 'left',
      width: 55,
    });
  }
  return columns;
});

const selectedTableColumns = computed(() => {
  const columns: TableColumn[] = [
    ...props.columns,
    {
      prop: 'action',
      label: '操作',
      width: 80,
      fixed: 'right',
      slot: 'action',
    },
  ];
  return columns;
});

const openDialog = () => {
  dialogVisible.value = true;
};

defineExpose({
  openDialog,
  dialogVisible,
});
</script>

<template>
  <div class="w-full">
    <ElInput
      v-if="showInput"
      :model-value="displayText"
      :placeholder="placeholder"
      readonly
      class="!w-[220px] cursor-pointer"
      :class="inputClass"
      :title="displayText"
      @click="dialogVisible = true"
    >
      <template #suffix>
        <ElIcon class="text-muted-foreground cursor-pointer">
          <span class="i-heroicons-magnifying-glass"></span>
        </ElIcon>
      </template>
    </ElInput>

    <ElDialog
      append-to-body
      v-model="dialogVisible"
      :title="dialogTitle"
      :width="dialogWidth"
      :close-on-click-modal="false"
      destroy-on-close
      body-class="!h-[60vh] h-full"
    >
      <ElTabs v-model="activeTab" v-if="multiple" class="h-full">
        <ElTabPane label="选择列表" name="list" class="h-full">
          <TableLayout
            ref="tableLayoutRef"
            :table-data="tableData"
            :loading="loading"
            :total="total"
            :query-params="queryParams"
            :form-items="formItems"
            :table-columns="tableColumnsWithSelection"
            :show-top-query="true"
            :show-side-query="showSideQuery"
            :show-pagination="true"
            :sider-data="siderData"
            row-key="id"
            border
            size="default"
            @query="handleQuery"
            @reset="handleReset"
            @pagination="handlePagination"
            @selection-change="handleSelectionChange"
            @row-click="handleRowClick"
            :toolbar-config="{
              zoom: false,
              custom: false,
            }"
          />
        </ElTabPane>
        <ElTabPane :label="`已选择(${selectedList.length})`" name="selected" class="h-full">
          <div class="flex h-full flex-col">
            <div class="mb-3 flex items-center justify-between">
              <span class="text-sm">已选择 {{ selectedList.length }} 项</span>
              <ElButton type="danger" link @click="handleClearSelected"> 清空全部 </ElButton>
            </div>
            <TableLayout
              :table-data="selectedList"
              :loading="false"
              :total="selectedList.length"
              :query-params="{
                pageNo: 1,
                pageSize: selectedList.length || 10,
              }"
              :form-items="[]"
              :table-columns="selectedTableColumns"
              :show-top-query="false"
              :show-pagination="false"
              :row-key="valueField"
              border
              size="default"
              :toolbar-config="{
                zoom: false,
                custom: false,
              }"
              :pagination="false"
            >
              <template #action="{ row }">
                <ElButton type="danger" link @click="handleRemoveSelected(row)"> 删除 </ElButton>
              </template>
            </TableLayout>
          </div>
        </ElTabPane>
      </ElTabs>
      <TableLayout
        v-else
        :table-data="tableData"
        :loading="loading"
        :total="total"
        :query-params="queryParams"
        :form-items="formItems"
        :table-columns="tableColumnsWithSelection"
        :show-top-query="true"
        :show-side-query="showSideQuery"
        :show-pagination="true"
        :sider-data="siderData"
        row-key="id"
        border
        size="default"
        @query="handleQuery"
        @reset="handleReset"
        @pagination="handlePagination"
        @selection-change="handleSelectionChange"
        @row-click="handleRowClick"
        :toolbar-config="{
          zoom: false,
          custom: false,
        }"
      />
      <template #footer>
        <div class="flex justify-end">
          <ElButton @click="dialogVisible = false">取消</ElButton>
          <ElButton v-if="multiple" type="primary" @click="handleConfirm">确认选择</ElButton>
        </div>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
:deep(.vxe-table--body .vxe-body--row:hover) {
  cursor: pointer;
  background-color: #f5f7fa !important;
}

:deep(.vxe-table--body .vxe-body--row.row--hover) {
  background-color: #f5f7fa !important;
}

:deep(.vxe-table--body .vxe-body--row.row--checked) {
  background-color: #ecf5ff !important;
}

:deep(.vxe-table--body .vxe-body--row.row--checked:hover) {
  background-color: #d4e8ff !important;
}

:deep(.vxe-table--body .vxe-body--column:hover) {
  background-color: #f5f7fa !important;
}

:deep(.vxe-table--body .vxe-body--row:hover .vxe-body--column) {
  background-color: transparent !important;
}

:deep(.el-input__inner) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
