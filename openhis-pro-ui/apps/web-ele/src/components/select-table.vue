<script lang="ts" setup>
import type { VxePagerEvents } from 'vxe-pc-ui';
import type { VxeGridInstance, VxeGridProps, VxeGridPropTypes, VxeTableEvents } from 'vxe-table';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import { VxeInput, VxePulldown } from 'vxe-pc-ui';
import { VxeGrid } from 'vxe-table';

import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

interface OptionItem {
  [key: string]: any;
}

interface TableColumn {
  prop: string;
  label: string;
  width?: number | string;
  minWidth?: number | string;
  align?: 'center' | 'left' | 'right';
}

interface RemoteMethodParams extends Record<string, any> {
  pageNo: number;
  pageSize: number;
}

interface RemoteMethodResponse {
  content: OptionItem[];
  totalElements?: number;
  totalCount?: number;
}

interface Props {
  modelValue?: string | string[];
  remoteMethod: (params: RemoteMethodParams) => Promise<RemoteMethodResponse>;
  columns: TableColumn[];
  valueKey?: string;
  labelKey?: string;
  placeholder?: string;
  clearable?: boolean;
  filterable?: boolean;
  pageSize?: number;
  query?: Record<string, any>;
  disabled?: boolean;
  beforeClose?: (item: OptionItem, value: string) => boolean | Promise<boolean>;
  selectedLabel?: string;
  inputHeight?: number | string;
  /** 下拉表格面板宽度，默认 800px */
  panelWidth?: number | string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: undefined,
  valueKey: 'value',
  labelKey: 'label',
  placeholder: '请选择',
  clearable: true,
  filterable: true,
  pageSize: 20,
  query: () => ({}),
  disabled: false,
  beforeClose: undefined,
  selectedLabel: undefined,
  inputHeight: undefined,
  panelWidth: 800,
});

const emit = defineEmits<{
  blur: [keyword: string];
  change: [value: string | string[]];
  'update:modelValue': [value: string | string[]];
}>();

const showPopup = ref(false);
const loading = ref(false);
const isBeforeClosePending = ref(false);
/** 仅用户鼠标点选输入框时为 true，防止程序 focus / 方向键切格误弹下拉 */
const openDropdownOnFocus = ref(false);
const keyword = ref('');
const gridRef = ref<VxeGridInstance<OptionItem>>();
/** 下拉打开时，与表格当前行同步的键盘高亮索引（当前页 data 下标） */
const keyboardRowIndex = ref(-1);

const isSelected = computed(() => {
  const v = props.modelValue;
  return v !== '' && v !== undefined && v !== null;
});

const gridOptions = reactive<VxeGridProps & { pagerConfig: VxeGridPropTypes.PagerConfig }>({
  autoResize: true,
  height: '100%',
  loading: false,
  rowConfig: {
    isHover: true,
    isCurrent: true,
    keyField: props.valueKey,
  },
  pagerConfig: {
    enabled: true,
    total: 0,
    currentPage: 1,
    pageSize: props.pageSize,
  },
  columns: props.columns.map((c: TableColumn) => ({
    field: c.prop,
    title: c.label,
    width: c.width,
    minWidth: c.minWidth,
    align: c.align,
    showOverflow: true,
  })),
  border: true,
  data: [],
});

const selectedLabelText = computed(() => {
  if (props.selectedLabel !== undefined) return props.selectedLabel;
  if (!isSelected.value) return '';
  const v = props.modelValue as string | number;
  const row = (gridOptions.data as OptionItem[]).find((it) => {
    const key = it?.[props.valueKey];
    return key === v || (key != null && v !== '' && v !== undefined && String(key) === String(v));
  });
  return row ? String(row?.[props.labelKey] ?? '') : String(v);
});

const inputValue = computed({
  get() {
    if (showPopup.value) {
      return keyword.value;
    }
    if (isSelected.value) {
      return selectedLabelText.value;
    }
    // 未选中档案时保留检索框内手工输入，避免下拉关闭后文案被清空
    return keyword.value;
  },
  set(v: string) {
    keyword.value = v;
  },
});

watch(
  () => props.valueKey,
  (keyField) => {
    gridOptions.rowConfig = {
      ...gridOptions.rowConfig,
      isHover: true,
      isCurrent: true,
      keyField,
    };
  },
  { immediate: true },
);

const syncGridCurrentRow = async () => {
  await nextTick();
  const grid = gridRef.value;
  if (!grid || !showPopup.value) {
    return;
  }
  const rows = gridOptions.data as OptionItem[];
  const idx = keyboardRowIndex.value;
  if (idx >= 0 && idx < rows.length) {
    const row = rows[idx];
    if (row) {
      await grid.setCurrentRow(row);
      await grid.scrollToRow(row);
    }
  } else {
    await grid.clearCurrentRow();
  }
};

const loadData = async () => {
  loading.value = true;
  gridOptions.loading = true;
  try {
    const { currentPage = 1, pageSize = props.pageSize } = gridOptions.pagerConfig;
    const result = await props.remoteMethod({
      ...props.query,
      keyword: keyword.value || '',
      pageNo: currentPage,
      pageSize,
    });
    const parsed = parsePagedRowsFromApiData<OptionItem>(result as unknown);
    gridOptions.data = parsed.content;
    gridOptions.pagerConfig.total = parsed.totalCount;
  } catch (error) {
    console.error('加载数据失败:', error);
    gridOptions.data = [];
    gridOptions.pagerConfig.total = 0;
  } finally {
    loading.value = false;
    gridOptions.loading = false;
  }
  const rows = (gridOptions.data as OptionItem[]) || [];
  if (isSelected.value && props.modelValue !== undefined && props.modelValue !== '') {
    const selectedIdx = rows.findIndex((it) => {
      const key = it?.[props.valueKey];
      return key != null && String(key) === String(props.modelValue);
    });
    keyboardRowIndex.value = selectedIdx >= 0 ? selectedIdx : rows.length > 0 ? 0 : -1;
  } else {
    keyboardRowIndex.value = rows.length > 0 ? 0 : -1;
  }
  await nextTick();
  await syncGridCurrentRow();
};

const debouncedSearch = useDebounceFn(() => {
  gridOptions.pagerConfig.currentPage = 1;
  loadData();
}, 1000);

watch(
  () => showPopup.value,
  async (visible: boolean) => {
    if (visible) {
      if (isSelected.value) {
        const label =
          props.selectedLabel !== undefined && String(props.selectedLabel).trim() !== ''
            ? String(props.selectedLabel).trim()
            : selectedLabelText.value;
        if (label) {
          keyword.value = label;
        }
      }
      await loadData();
    } else {
      if (isSelected.value) {
        keyword.value = '';
      }
      keyboardRowIndex.value = -1;
      await nextTick(() => gridRef.value?.clearCurrentRow());
    }
  },
);

watch(keyword, () => {
  if (showPopup.value) debouncedSearch();
});

function openDropdown() {
  if (props.disabled) {
    return;
  }
  showPopup.value = true;
}

/** 用户鼠标按下输入框：允许随后 focus 时打开下拉 */
function handleInputMouseDown() {
  openDropdownOnFocus.value = true;
}

/** 仅用户点选触发的 focus 才打开下拉；程序 focus / Tab / 方向键切格不打开 */
function handleInputFocus() {
  if (!openDropdownOnFocus.value) {
    return;
  }
  openDropdownOnFocus.value = false;
  openDropdown();
}

const suffixClick = () => {
  if (props.disabled) return;
  showPopup.value = !showPopup.value;
};

const clearEvent = () => {
  if (props.disabled) return;
  keyword.value = '';
  showPopup.value = false;
  emit('update:modelValue', '');
  emit('change', '');
};

const pageChangeEvent: VxePagerEvents.PageChange = ({ currentPage, pageSize }: any) => {
  gridOptions.pagerConfig.currentPage = currentPage;
  gridOptions.pagerConfig.pageSize = pageSize;
  void loadData();
};

async function commitSelection(item: OptionItem) {
  if (isBeforeClosePending.value) return;
  const newValue = item?.[props.valueKey];
  if (newValue === undefined || newValue === null) return;

  if (props.beforeClose) {
    try {
      isBeforeClosePending.value = true;
      const shouldClose = await props.beforeClose(item, newValue);
      isBeforeClosePending.value = false;
      if (!shouldClose) {
        showPopup.value = true;
        return;
      }
    } catch {
      isBeforeClosePending.value = false;
      showPopup.value = true;
      return;
    }
  }

  emit('update:modelValue', newValue);
  emit('change', newValue);
  showPopup.value = false;
}

const selectEvent: VxeTableEvents.CellClick = async (params: any) => {
  await commitSelection(params.row as OptionItem);
};

const handleInputKeydown = (params: { $event: KeyboardEvent }) => {
  const e = params.$event;
  if (props.disabled) {
    return;
  }
  if (e.isComposing) {
    return;
  }

  if (!showPopup.value) {
    if (e.key === 'Enter' || e.key === 'ArrowDown') {
      e.preventDefault();
      openDropdown();
    }
    return;
  }

  const rows = gridOptions.data as OptionItem[];
  const len = rows.length;
  if (!len) {
    return;
  }

  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    e.preventDefault();
    let next = keyboardRowIndex.value;
    if (next < 0 || next >= len) {
      next = e.key === 'ArrowDown' ? 0 : len - 1;
    } else if (e.key === 'ArrowDown') {
      next = Math.min(len - 1, next + 1);
    } else {
      next = Math.max(0, next - 1);
    }
    keyboardRowIndex.value = next;
    void syncGridCurrentRow();
    return;
  }

  if (e.key === 'Enter') {
    e.preventDefault();
    let idx = keyboardRowIndex.value;
    if (idx < 0 || idx >= len) {
      idx = 0;
    }
    const row = rows[idx];
    if (row) {
      void commitSelection(row);
    }
  }
};

/** 输入框失焦：回传当前检索关键字（未选中行时供外层按手工姓名处理） */
function handleInputBlur() {
  emit('blur', getKeywordDraft());
}

/** 当前输入框可见文案（含下拉打开时的检索词） */
function getKeywordDraft(): string {
  return String(keyword.value || inputValue.value || '').trim();
}

defineExpose({ getKeywordDraft });
</script>

<template>
  <VxePulldown v-model="showPopup" class="w-full" :z-index="6000" transfer>
    <template #default>
      <VxeInput
        v-model="inputValue"
        class="!w-full"
        :placeholder="isSelected ? '' : placeholder"
        :disabled="disabled"
        :clearable="clearable"
        @click="openDropdown"
        @clear="clearEvent"
        @mousedown="handleInputMouseDown"
        @focus="handleInputFocus"
        @blur="handleInputBlur"
        @suffix-click="suffixClick"
        @keydown="handleInputKeydown"
      />
    </template>

    <template #dropdown>
      <div
        class="h-[300px] w-full border border-[#dcdfe6] bg-white shadow-[0_0_6px_2px_rgb(0_0_0_/_10%)]"
        :style="{
          width: typeof panelWidth === 'number' ? `${panelWidth}px` : String(panelWidth || '800px'),
        }"
      >
        <VxeGrid
          ref="gridRef"
          class="select-table-dropdown-grid"
          v-bind="gridOptions"
          :loading="loading || isBeforeClosePending"
          @cell-click="selectEvent"
          @page-change="pageChangeEvent"
        />
      </div>
    </template>
  </VxePulldown>
</template>

<style scoped>
.select-table-dropdown-grid {
  width: 100%;
}

.select-table-dropdown-grid :deep(.vxe-table) {
  width: 100% !important;
}
</style>
