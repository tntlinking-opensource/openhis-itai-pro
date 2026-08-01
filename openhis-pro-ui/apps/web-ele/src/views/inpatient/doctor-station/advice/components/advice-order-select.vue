<script setup lang="ts">
import type { VxePagerEvents } from 'vxe-pc-ui';
import type { VxeGridInstance } from 'vxe-table';

import type { AdviceProductOption, ProductQueryParams } from '../services/advice.types';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { Search } from '@element-plus/icons-vue';
import { useDebounceFn } from '@vueuse/core';
import { ElButton, ElIcon, ElMessage, ElSwitch } from 'element-plus';
import { VxeInput, VxePulldown } from 'vxe-pc-ui';
import { VxeGrid } from 'vxe-table';

import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import {
  formatAdviceCatalogStockCellText,
  isAdviceCatalogZeroStock,
} from '../services/advice-list';

interface ProductColumn {
  prop: string;
  label: string;
  width?: number;
}

type AdviceCategory = 'ALL' | 'DEVICE' | 'MEDICATION' | 'SERVICE';

const ADVICE_CATEGORY_TABS: Array<{ code: AdviceCategory; label: string }> = [
  { code: 'ALL', label: '全部' },
  { code: 'MEDICATION', label: '药品' },
  { code: 'SERVICE', label: '诊疗' },
  { code: 'DEVICE', label: '耗材' },
];

const props = withDefaults(
  defineProps<{
    columns: ProductColumn[];
    /** 收起态展示：选药后下拉未再打开时内部 options 可能为空，用于稳定回显项目名称。 */
    displayLabel?: string;
    /**
     * 父级递增时自动展开下拉表格（如「新增医嘱」打开空白编辑卡）；未传或 ≤0 时不处理。
     * 收起态行请传 0，避免误展开。
     */
    expandNonce?: number;
    /**
     * 父级递增时仅聚焦检索框、不展开下拉（如保存成功后继续录药）；收起态行请传 0。
     */
    focusTriggerNonce?: number;
    /**
     * 目录范围：`MEDICATION_ONLY` 时仅药品（出院带药 Tab，BR-IP-ORD-029）。
     */
    catalogScope?: 'ALL' | 'MEDICATION_ONLY';
    /**
     * 已选项目时打开下拉是否保留输入框内名称（编辑态换药不闪空）。
     * 默认 false；仅医生站开立编辑卡开启。
     */
    keepSelectedLabelOnOpen?: boolean;
    modelValue: string;
    placeholder?: string;
    remoteMethod: (params: ProductQueryParams) => Promise<{
      content: AdviceProductOption[];
      totalElements: number;
    }>;
  }>(),
  {
    displayLabel: '',
    expandNonce: 0,
    focusTriggerNonce: 0,
    catalogScope: 'ALL',
    keepSelectedLabelOnOpen: false,
  },
);

const emit = defineEmits<{
  select: [row: AdviceProductOption];
  'update:modelValue': [value: string];
}>();

const activeCategory = ref<AdviceCategory>('ALL');
const showPopup = ref(false);
const keyword = ref('');
/** 只看库存>0；默认关=零库存与有库存均展示 */
const onlyPositiveStock = ref(false);
const loading = ref(false);
const options = ref<AdviceProductOption[]>([]);
const pageNo = ref(1);
const pageSize = ref(20);
const total = ref(0);
/** 远程拉取代数：忽略关闭后/连续打开时的过期响应 */
let loadGeneration = 0;

const gridRef = ref<VxeGridInstance<AdviceProductOption>>();
/** 触发区容器，用于自动展开后聚焦内部 input */
const triggerWrapRef = ref<HTMLElement | null>(null);
/** 弹层内键盘高亮行（与 VxeGrid 当前行同步） */
const keyboardRowIndex = ref(-1);

/** 可见的目录类目 Tab（出院带药仅药品） */
const visibleCategoryTabs = computed(() => {
  if (props.catalogScope === 'MEDICATION_ONLY') {
    return ADVICE_CATEGORY_TABS.filter((t) => t.code === 'MEDICATION');
  }
  return ADVICE_CATEGORY_TABS;
});

function productTableNamesForCategory(cat: AdviceCategory): string[] | undefined {
  // 出院带药：强制仅药品定义表
  if (props.catalogScope === 'MEDICATION_ONLY') {
    return ['med_medication_definition'];
  }
  if (cat === 'ALL') return undefined;
  if (cat === 'MEDICATION') return ['med_medication_definition'];
  if (cat === 'SERVICE') return ['wkf_activity_definition'];
  if (cat === 'DEVICE') return ['adm_device_definition'];
  return undefined;
}

watch(
  () => props.catalogScope,
  (scope) => {
    // 切到出院带药目录时固定药品 Tab，避免仍停在诊疗
    if (scope === 'MEDICATION_ONLY') {
      activeCategory.value = 'MEDICATION';
    }
  },
  { immediate: true },
);

const selectedLabel = computed(() => {
  const id = props.modelValue;
  if (!id) return '';
  const fromParent = props.displayLabel.trim();
  if (fromParent) return fromParent;
  const row = options.value.find((item) => item.id === id);
  return row?.name || '';
});

/**
 * 本轮打开下拉后是否已改过检索词。
 * 未改过时已选药品继续显示名称，避免打开瞬间变空触发 clearable 清掉 selectedId。
 */
const searchEdited = ref(false);

/** 中药等「不保留上次选品」场景：清空检索词/分类/旧列表，避免下一味看起来像空列表 */
function resetEphemeralSearchState() {
  keyword.value = '';
  searchEdited.value = false;
  // 出院带药目录固定药品 Tab，其余回全部
  activeCategory.value = props.catalogScope === 'MEDICATION_ONLY' ? 'MEDICATION' : 'ALL';
  onlyPositiveStock.value = false;
  pageNo.value = 1;
  options.value = [];
  gridOptions.data = [];
  total.value = 0;
  gridOptions.pagerConfig.currentPage = 1;
  gridOptions.pagerConfig.total = 0;
  keyboardRowIndex.value = -1;
}

/** 打开下拉前准备检索态：西药保留药名；中药强制空白重搜 */
function prepareOpenKeyword() {
  searchEdited.value = false;
  if (props.keepSelectedLabelOnOpen) {
    if (props.modelValue) {
      const label = selectedLabel.value;
      if (label) {
        keyword.value = label;
      }
    }
    return;
  }
  resetEphemeralSearchState();
}

/** 点击输入框打开下拉 */
function openPopup() {
  prepareOpenKeyword();
  if (showPopup.value) {
    void loadOptions();
    return;
  }
  showPopup.value = true;
}

/** 后缀箭头：关闭则直接关；打开则先准备检索态再展开 */
function togglePopup() {
  if (showPopup.value) {
    showPopup.value = false;
    return;
  }
  prepareOpenKeyword();
  showPopup.value = true;
}

/** 下拉打开时绑定检索关键字；收起时回显已选名称（与 `select-table.vue` 同源，否则 `keyword` 不随输入变化、远程搜索不触发） */
const inputValue = computed({
  get() {
    if (!showPopup.value) {
      return selectedLabel.value;
    }
    // 已选且尚未手工改检索：继续显示药品名，防止打开瞬间空串触发 @clear
    if (
      props.keepSelectedLabelOnOpen &&
      props.modelValue &&
      !searchEdited.value &&
      keyword.value === ''
    ) {
      return selectedLabel.value;
    }
    return keyword.value;
  },
  set(v: string) {
    searchEdited.value = true;
    keyword.value = v;
  },
});

const displayPlaceholder = computed(() => props.placeholder || '请输入编码、项目名称或商品名称');

/** 固定列宽之和小于下拉宽度时，由「项目名称」列（无 name 列时最后一列）用 minWidth 吃满剩余宽度 */
const gridColumns = computed(() => {
  const cols = props.columns;
  const hasName = cols.some((c) => c.prop === 'name');
  return cols.map((column, index) => {
    const base = { field: column.prop, title: column.label, showOverflow: true as const };
    const flex = hasName ? column.prop === 'name' : index === cols.length - 1;
    if (column.prop === 'availableQuantity') {
      return {
        ...base,
        width: column.width,
        slots: { default: 'availableQuantity' },
      };
    }
    if (column.prop === 'merchandiseName') {
      return {
        ...base,
        width: column.width,
        slots: { default: 'merchandiseName' },
      };
    }
    if (flex) {
      return { ...base, minWidth: column.width ?? 160 };
    }
    return { ...base, width: column.width };
  });
});

const gridOptions = reactive({
  autoResize: true,
  border: true,
  height: 420,
  rowConfig: { keyField: 'id', isHover: true, isCurrent: true },
  pagerConfig: {
    enabled: true,
    currentPage: pageNo.value,
    pageSize: pageSize.value,
    total: total.value,
  },
  data: [] as AdviceProductOption[],
});

const syncGridCurrentRow = async () => {
  await nextTick();
  const grid = gridRef.value;
  if (!grid || !showPopup.value) return;
  const rows = gridOptions.data;
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

/** 当前页结果按「只看库存>0」过滤；无库存字段的行（如诊疗）仍保留。 */
function applyPositiveStockFilter(list: AdviceProductOption[]): AdviceProductOption[] {
  if (!onlyPositiveStock.value) {
    return list;
  }
  return list.filter((row) => !isAdviceCatalogZeroStock(row));
}

/** 把 options 同步到表格（含库存开关过滤）并重置键盘高亮。 */
function syncGridDataFromOptions() {
  const filtered = applyPositiveStockFilter(options.value);
  gridOptions.data = filtered;
  keyboardRowIndex.value = filtered.length > 0 ? 0 : -1;
}

const loadOptions = async () => {
  const generation = ++loadGeneration;
  loading.value = true;
  try {
    const result = await props.remoteMethod({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim(),
      productTableNames: productTableNamesForCategory(activeCategory.value),
    });
    if (generation !== loadGeneration) {
      return;
    }
    const parsed = parsePagedRowsFromApiData<AdviceProductOption>(result as unknown);
    const list = parsed.content.filter(
      (row) =>
        String(row.productTableName ?? '')
          .trim()
          .toLowerCase() !== 'adm_device_definition',
    );
    options.value = list;
    syncGridDataFromOptions();
    total.value = Math.max(parsed.totalCount, list.length);
    gridOptions.pagerConfig.currentPage = pageNo.value;
    gridOptions.pagerConfig.pageSize = pageSize.value;
    gridOptions.pagerConfig.total = total.value;
  } finally {
    if (generation === loadGeneration) {
      loading.value = false;
    }
  }
  if (generation === loadGeneration) {
    await syncGridCurrentRow();
  }
};

const debouncedLoad = useDebounceFn(loadOptions, 300);

function runFocusTriggerInput() {
  const tryFocus = () => {
    const input = triggerWrapRef.value?.querySelector<HTMLInputElement>('input');
    if (input) {
      input.focus({ preventScroll: true });
      return true;
    }
    return false;
  };
  requestAnimationFrame(() => {
    if (tryFocus()) return;
    window.setTimeout(() => {
      void tryFocus();
    }, 80);
  });
}

/** 聚焦选药检索框（供自动展开后键盘直接操作） */
async function focusTriggerInput() {
  await nextTick();
  runFocusTriggerInput();
}

/** 与上一次已响应的 expandNonce 对齐，避免重复展开或 0 占位触发 */
const lastHandledExpandNonce = ref(0);

watch(
  () => props.expandNonce ?? 0,
  async (n) => {
    if (n <= 0) return;
    if (n === lastHandledExpandNonce.value) return;
    lastHandledExpandNonce.value = n;
    const alreadyOpen = showPopup.value;
    prepareOpenKeyword();
    showPopup.value = true;
    if (alreadyOpen) {
      void loadOptions();
    }
    await focusTriggerInput();
  },
);

/** 与上一次已响应的 focusTriggerNonce 对齐 */
const lastHandledFocusTriggerNonce = ref(0);

watch(
  () => props.focusTriggerNonce ?? 0,
  async (n) => {
    if (n <= 0) return;
    if (n === lastHandledFocusTriggerNonce.value) return;
    lastHandledFocusTriggerNonce.value = n;
    showPopup.value = false;
    await nextTick();
    runFocusTriggerInput();
  },
);

watch(
  () => showPopup.value,
  (visible) => {
    if (visible) {
      pageNo.value = 1;
      if (props.keepSelectedLabelOnOpen) {
        if (props.modelValue && !searchEdited.value && !keyword.value) {
          const label = selectedLabel.value;
          if (label) {
            keyword.value = label;
          }
        }
      } else {
        const defaultCategory = props.catalogScope === 'MEDICATION_ONLY' ? 'MEDICATION' : 'ALL';
        if (keyword.value || options.value.length > 0 || activeCategory.value !== defaultCategory) {
          resetEphemeralSearchState();
        }
      }
      void loadOptions();
      return;
    }
    loadGeneration += 1;
    keyword.value = '';
    searchEdited.value = false;
    onlyPositiveStock.value = false;
    keyboardRowIndex.value = -1;
    if (!props.keepSelectedLabelOnOpen) {
      options.value = [];
      gridOptions.data = [];
      total.value = 0;
      gridOptions.pagerConfig.total = 0;
      activeCategory.value = props.catalogScope === 'MEDICATION_ONLY' ? 'MEDICATION' : 'ALL';
    }
    void nextTick(() => gridRef.value?.clearCurrentRow());
  },
);

watch(
  () => activeCategory.value,
  () => {
    pageNo.value = 1;
    if (showPopup.value) loadOptions();
  },
);

watch(keyword, () => {
  pageNo.value = 1;
  if (showPopup.value) debouncedLoad();
});

watch(onlyPositiveStock, () => {
  if (!showPopup.value) return;
  syncGridDataFromOptions();
  void syncGridCurrentRow();
});

/** 选品：零库存拦截并提示，不关闭下拉、不回写选中项 */
const handleSelect = ({ row }: { row: AdviceProductOption }) => {
  if (isAdviceCatalogZeroStock(row)) {
    ElMessage.warning('库存不足');
    return;
  }
  emit('update:modelValue', row.id);
  emit('select', row);
  showPopup.value = false;
};

const handleInputKeydown = (params: { $event: KeyboardEvent }) => {
  const e = params.$event;
  if (e.isComposing) return;
  if (e.key === 'Enter' && !showPopup.value) {
    e.preventDefault();
    openPopup();
    return;
  }
  if (!showPopup.value) return;
  const len = gridOptions.data.length;
  if (!len) return;

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
    if (idx < 0 || idx >= len) idx = 0;
    const row = gridOptions.data[idx];
    if (row) handleSelect({ row });
  }
};

const handlePageChange: VxePagerEvents.PageChange = ({ currentPage, pageSize: size }) => {
  pageNo.value = currentPage;
  pageSize.value = size;
  loadOptions();
};

/**
 * 清空按钮：开立编辑卡已选药品时只清检索词，保留 selectedId 与下方明细表单；
 * 其它场景仍清空选中项。
 */
function handleInputClear() {
  if (props.keepSelectedLabelOnOpen && props.modelValue) {
    searchEdited.value = true;
    keyword.value = '';
    pageNo.value = 1;
    showPopup.value = true;
    loadOptions();
    return;
  }
  keyword.value = '';
  showPopup.value = false;
  emit('update:modelValue', '');
}
</script>

<template>
  <!-- 触发区宽度固定 400px（scoped 规则），避免与父级合并的 w-full 在 Tailwind 顺序上覆盖 w-[400px] -->
  <div class="advice-order-select-trigger-host min-w-0">
    <!-- Vue 传给 VxePulldown 的 class 未落到根结点；宽度靠下方 :deep + !important 覆盖 vxe 默认 180px / inline-block -->
    <VxePulldown v-model="showPopup" transfer :z-index="6000">
      <template #default>
        <div ref="triggerWrapRef" class="advice-order-select-input-wrap relative w-full">
          <span
            class="pointer-events-none absolute left-2.5 top-1/2 z-[1] -translate-y-1/2 text-neutral-500"
            aria-hidden="true"
          >
            <ElIcon :size="16">
              <Search />
            </ElIcon>
          </span>
          <VxeInput
            v-model="inputValue"
            clearable
            class="advice-order-select-vxe-input w-full"
            :placeholder="displayPlaceholder"
            @click="openPopup"
            @clear="handleInputClear"
            @suffix-click="togglePopup"
            @keydown="handleInputKeydown"
          />
        </div>
      </template>
      <template #dropdown>
        <div
          class="advice-order-select-dropdown w-[min(100vw-24px,1080px)] min-w-[960px] border border-[#dcdfe6] bg-white p-2 shadow-[0_6px_16px_rgb(0_0_0_/_12%)]"
        >
          <div
            class="mb-2 flex items-center gap-1 rounded border border-[#d7e4f2] bg-[#f6fbff] p-1"
          >
            <ElButton
              v-for="tab in visibleCategoryTabs"
              :key="tab.code"
              size="small"
              :type="activeCategory === tab.code ? 'primary' : 'default'"
              @click="activeCategory = tab.code"
            >
              {{ tab.label }}
            </ElButton>
            <div
              class="ml-auto flex shrink-0 items-center gap-2 px-2 text-sm text-[var(--el-text-color-regular)]"
            >
              <span class="whitespace-nowrap">只看库存&gt;0</span>
              <ElSwitch v-model="onlyPositiveStock" size="small" />
            </div>
          </div>
          <VxeGrid
            ref="gridRef"
            v-bind="gridOptions"
            :columns="gridColumns"
            :loading="loading"
            @cell-click="handleSelect"
            @page-change="handlePageChange"
          >
            <template #availableQuantity="{ row }">
              <span
                :class="{
                  'advice-order-select-stock-zero': isAdviceCatalogZeroStock(row),
                }"
              >
                {{ formatAdviceCatalogStockCellText(row) }}
              </span>
            </template>
            <template #merchandiseName="{ row }">
              <span>{{ String(row.merchandiseName ?? '').trim() || '—' }}</span>
            </template>
          </VxeGrid>
        </div>
      </template>
    </VxePulldown>
  </div>
</template>

<style scoped>
/** 固定检索输入占位宽度；max-width 防止窄视口溢出 */
.advice-order-select-trigger-host {
  width: 400px;
  max-width: 100%;
  box-sizing: border-box;
}

/**
 * vxe-pc-ui 全局：.vxe-pulldown { display:inline-block }、.vxe-input { width:180px }
 * 不写 !important 时，外层 flex 分配的宽度完全被这两个默认值吃掉，表现成「检索框永远不宽」。
 */
.advice-order-select-trigger-host :deep(.vxe-pulldown) {
  display: block !important;
  width: 100% !important;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.advice-order-select-trigger-host :deep(.vxe-pulldown--content) {
  display: block;
  width: 100%;
}

.advice-order-select-input-wrap :deep(.vxe-input) {
  width: 100% !important;
  max-width: 100%;
  min-width: 0 !important;
  box-sizing: border-box;
}

/* 为左侧搜索图标留出空间（VxeInput 内部为原生 input） */
.advice-order-select-input-wrap :deep(.vxe-input--inner),
.advice-order-select-input-wrap :deep(input) {
  padding-left: 30px;
}

/* 下拉内表格横向占满面板，避免列宽总和小于容器时出现右侧留白 */
.advice-order-select-dropdown :deep(.vxe-grid),
.advice-order-select-dropdown :deep(.vxe-table) {
  width: 100%;
}

.advice-order-select-stock-zero {
  color: var(--el-color-danger);
  font-weight: 500;
}
</style>
