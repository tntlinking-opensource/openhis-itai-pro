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

defineOptions({ name: 'AdviceOrderSelect' });

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
     * 已选项目时，首次展开是否回填当前药名（编辑态换药不闪空）。
     * 用户一旦改过检索，再展开不再强制回填旧名，直到换选新品；默认 false。
     */
    keepSelectedLabelOnOpen?: boolean;
    modelValue: string;
    placeholder?: string;
    remoteMethod: (params: ProductQueryParams) => Promise<{
      content: AdviceProductOption[];
      totalElements: number;
    }>;
    /**
     * 下拉面板左侧最少留白（px，相对中药处方面板左缘）。
     * 用于露出下方药名/剂量列；0 表示不调整。
     */
    dropdownLeftClearance?: number;
    /**
     * 中药处方：下拉左缘贴剂量列右侧，右缘贴处方面板行尾。
     */
    dropdownFillToHostRight?: boolean;
    /**
     * 中药处方：下拉左缘与检索输入框（表单）左缘对齐。
     */
    dropdownAlignTriggerLeft?: boolean;
  }>(),
  {
    displayLabel: '',
    expandNonce: 0,
    focusTriggerNonce: 0,
    keepSelectedLabelOnOpen: false,
    dropdownLeftClearance: 0,
    dropdownFillToHostRight: false,
    dropdownAlignTriggerLeft: false,
  },
);

const emit = defineEmits<{
  select: [row: AdviceProductOption];
  'update:modelValue': [value: string];
}>();

const ADVICE_CATEGORY_TABS: Array<{ code: AdviceCategory; label: string }> = [
  { code: 'ALL', label: '全部' },
  { code: 'MEDICATION', label: '药品' },
  { code: 'SERVICE', label: '诊疗' },
  { code: 'DEVICE', label: '耗材' },
];

const activeCategory = ref<AdviceCategory>('ALL');
const showPopup = ref(false);
const keyword = ref('');
/** 只看库存>0；默认开=后端分页过滤，关则含零库存 */
const onlyPositiveStock = ref(true);
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

function productTableNamesForCategory(cat: AdviceCategory): string[] | undefined {
  if (cat === 'ALL') return undefined;
  if (cat === 'MEDICATION') return ['med_medication_definition'];
  if (cat === 'SERVICE') return ['wkf_activity_definition'];
  if (cat === 'DEVICE') return ['adm_device_definition'];
  return undefined;
}

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
/**
 * 当前 selectedId 下用户已改过检索：再展开不再强制回填旧药名，直到换选新品。
 */
const skipSelectedLabelSeed = ref(false);

/** 选中项变化：允许下一次展开再回填新药名 */
watch(
  () => props.modelValue,
  () => {
    skipSelectedLabelSeed.value = false;
  },
);

/** 中药等「不保留上次选品」场景：清空检索词/分类/旧列表，避免下一味看起来像空列表 */
function resetEphemeralSearchState() {
  keyword.value = '';
  searchEdited.value = false;
  activeCategory.value = 'ALL';
  onlyPositiveStock.value = true;
  pageNo.value = 1;
  options.value = [];
  gridOptions.data = [];
  total.value = 0;
  gridOptions.pagerConfig.currentPage = 1;
  gridOptions.pagerConfig.total = 0;
  keyboardRowIndex.value = -1;
}

/** 标记用户正在换药检索，后续展开勿再灌回旧药名 */
function markSearchingToReplaceSelection() {
  searchEdited.value = true;
  if (props.keepSelectedLabelOnOpen) {
    skipSelectedLabelSeed.value = true;
  }
}

/** 打开下拉前准备检索态：西药首次保留药名；已改检索则空白；中药强制空白重搜 */
function prepareOpenKeyword() {
  searchEdited.value = false;
  if (props.keepSelectedLabelOnOpen) {
    // 用户已改过检索（想换药）：空白重搜，勿把旧药名盖回去
    if (skipSelectedLabelSeed.value) {
      keyword.value = '';
      return;
    }
    if (props.modelValue) {
      const label = selectedLabel.value;
      if (label) {
        keyword.value = label;
      }
    }
    return;
  }
  // 中药/连续加药：不沿用上一味关键字与旧 options（否则 exclude 后常表现为空表）
  resetEphemeralSearchState();
}

/**
 * 展开后短时忽略 Enter 选中，避免：
 * - 剂量框短时间双击回车展开后连带第三次回车误选首行
 * - 检索框回车刚展开时连按误选
 */
const ENTER_SELECT_GUARD_MS = 450;
let enterSelectGuardUntil = 0;

/** 开启 Enter 选中防抖窗口 */
function armEnterSelectGuard() {
  enterSelectGuardUntil = Date.now() + ENTER_SELECT_GUARD_MS;
}

/** 当前是否处于 Enter 选中防抖期内 */
function isEnterSelectGuarded() {
  return Date.now() < enterSelectGuardUntil;
}

/** 查找当前实例已 transfer 到 body 的选品下拉面板 */
function findTransferredDropdownPanel(): HTMLElement | null {
  const nodes = document.querySelectorAll<HTMLElement>('.vxe-pulldown--panel');
  for (const node of nodes) {
    if (!node.querySelector('.advice-order-select-dropdown')) continue;
    const style = getComputedStyle(node);
    if (style.display === 'none' || style.visibility === 'hidden') continue;
    return node;
  }
  return null;
}

/** 给元素写入宽高定位（!important，避免被 vxe / Tailwind 盖掉） */
function setImportantStyle(el: HTMLElement, prop: string, value: string) {
  el.style.setProperty(prop, value, 'important');
}

/** 中药选品列表固定宽度（小于原 820，避免超宽后被 vxe 左移再二次校正） */
const TCM_DROPDOWN_WIDTH_PX = 680;

function needsDropdownHostLayout() {
  return (
    Boolean(props.dropdownAlignTriggerLeft) ||
    Boolean(props.dropdownFillToHostRight) ||
    Number(props.dropdownLeftClearance ?? 0) > 0
  );
}

/**
 * 校正下拉定位（成功返回 true）：
 * - alignTriggerLeft：左缘贴检索框，宽度固定 TCM_DROPDOWN_WIDTH_PX
 * - fillToHostRight：左缘相对剂量列右缘，右缘撑满药表行尾
 * - 否则仅按 dropdownLeftClearance 推左缘
 */
function applyDropdownHostLayout(): boolean {
  const alignTriggerLeft = Boolean(props.dropdownAlignTriggerLeft);
  const fillToHostRight = Boolean(props.dropdownFillToHostRight);
  const clearance = Number(props.dropdownLeftClearance ?? 0);
  if (!needsDropdownHostLayout()) return false;

  const panel = findTransferredDropdownPanel();
  if (!panel) return false;
  const trigger = triggerWrapRef.value;
  const host = trigger?.closest('.tcm-rx-panel') ?? trigger?.closest('.tcm-herb-workspace') ?? null;

  // 优先：左缘贴齐检索表单 + 固定较小宽度（首帧完成，避免二次跳动）
  if (alignTriggerLeft && trigger) {
    const leftPx = `${Math.round(trigger.getBoundingClientRect().left)}px`;
    const widthPx = `${TCM_DROPDOWN_WIDTH_PX}px`;
    setImportantStyle(panel, 'left', leftPx);
    setImportantStyle(panel, 'right', 'auto');
    setImportantStyle(panel, 'width', widthPx);
    setImportantStyle(panel, 'max-width', widthPx);
    setImportantStyle(panel, 'min-width', '0');
    setImportantStyle(panel, 'box-sizing', 'border-box');
    const sized = panel.querySelectorAll<HTMLElement>(
      '.advice-order-select-dropdown, .vxe-pulldown--wrapper, .vxe-pulldown--panel-wrapper',
    );
    sized.forEach((el) => {
      setImportantStyle(el, 'width', '100%');
      setImportantStyle(el, 'max-width', 'none');
      setImportantStyle(el, 'min-width', '0');
      setImportantStyle(el, 'box-sizing', 'border-box');
    });
    return true;
  }

  if (fillToHostRight && host) {
    const hostRect = host.getBoundingClientRect();
    const doseEl = host.querySelector('.tcm-herb-workspace__col-dose');
    const stripsEl = host.querySelector('.tcm-herb-workspace__strips');
    // 剂量右缘 -8（相对上一版再右移 20px）；右缘贴药表 strips 行尾
    const left = doseEl
      ? doseEl.getBoundingClientRect().right - 8
      : hostRect.left + (clearance > 0 ? clearance : 220);
    const right = (stripsEl ?? host).getBoundingClientRect().right;
    const width = Math.max(360, Math.round(right - left));
    const leftPx = `${Math.round(left)}px`;
    const widthPx = `${width}px`;
    setImportantStyle(panel, 'left', leftPx);
    setImportantStyle(panel, 'right', 'auto');
    setImportantStyle(panel, 'width', widthPx);
    setImportantStyle(panel, 'max-width', widthPx);
    setImportantStyle(panel, 'min-width', '0');
    setImportantStyle(panel, 'box-sizing', 'border-box');
    const sized = panel.querySelectorAll<HTMLElement>(
      '.advice-order-select-dropdown, .vxe-pulldown--wrapper, .vxe-pulldown--panel-wrapper',
    );
    sized.forEach((el) => {
      setImportantStyle(el, 'width', '100%');
      setImportantStyle(el, 'max-width', 'none');
      setImportantStyle(el, 'min-width', '0');
      setImportantStyle(el, 'box-sizing', 'border-box');
    });
    return true;
  }

  if (!host || !(clearance > 0)) return false;
  const baseLeft = host.getBoundingClientRect().left;
  const minLeft = baseLeft + clearance;
  const currentLeft = panel.getBoundingClientRect().left;
  if (currentLeft >= minLeft - 0.5) return true;
  const delta = minLeft - currentLeft;
  const styleLeft = Number.parseFloat(panel.style.left || '');
  if (Number.isFinite(styleLeft)) {
    panel.style.left = `${styleLeft + delta}px`;
    return true;
  }
  panel.style.left = `${minLeft}px`;
  return true;
}

/** 清理 transfer 面板上的一次性定位标记，避免下次展开残留 */
function resetTransferredDropdownLayoutMarks() {
  const nodes = document.querySelectorAll<HTMLElement>('.vxe-pulldown--panel');
  nodes.forEach((node) => {
    if (!node.querySelector('.advice-order-select-dropdown')) return;
    delete node.dataset.layoutReady;
    node.style.removeProperty('opacity');
  });
}

let dropdownLayoutObserver: MutationObserver | null = null;

/** 面板插入后先透明再一次性定位，避免先错位再跳回 */
function scheduleDropdownHostLayout() {
  if (!needsDropdownHostLayout()) return;

  const reveal = (panel: HTMLElement) => {
    if (panel.dataset.layoutReady === '1') return true;
    setImportantStyle(panel, 'opacity', '0');
    if (!applyDropdownHostLayout()) return false;
    panel.dataset.layoutReady = '1';
    setImportantStyle(panel, 'opacity', '1');
    return true;
  };

  const tryApply = () => {
    const panel = findTransferredDropdownPanel();
    if (!panel) return false;
    return reveal(panel);
  };

  if (tryApply()) return;

  dropdownLayoutObserver?.disconnect();
  dropdownLayoutObserver = new MutationObserver(() => {
    if (tryApply()) {
      dropdownLayoutObserver?.disconnect();
      dropdownLayoutObserver = null;
    }
  });
  dropdownLayoutObserver.observe(document.body, { childList: true, subtree: true });

  void nextTick(() => {
    if (tryApply()) {
      dropdownLayoutObserver?.disconnect();
      dropdownLayoutObserver = null;
    }
  });

  window.setTimeout(() => {
    tryApply();
    dropdownLayoutObserver?.disconnect();
    dropdownLayoutObserver = null;
  }, 120);
}

/** 中药对齐模式用更窄固定宽；撑满行尾时交给定位脚本 */
const dropdownPanelClass = computed(() => {
  const base =
    'advice-order-select-dropdown border border-[#dcdfe6] bg-white p-2 shadow-[0_6px_16px_rgb(0_0_0_/_12%)]';
  if (props.dropdownAlignTriggerLeft) {
    return `${base} w-[680px] min-w-0 max-w-[min(100vw-48px,680px)]`;
  }
  if (props.dropdownFillToHostRight) {
    return `${base} w-full min-w-0 max-w-none`;
  }
  return `${base} w-[min(100vw-48px,920px)] min-w-[820px]`;
});

/** 点击输入框打开下拉 */
function openPopup() {
  // 已展开再点：只重拉，勿 prepareOpenKeyword（否则会把正在输入的词打回旧药名）
  if (showPopup.value) {
    void loadOptions();
    return;
  }
  prepareOpenKeyword();
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
      !skipSelectedLabelSeed.value &&
      keyword.value === ''
    ) {
      return selectedLabel.value;
    }
    return keyword.value;
  },
  set(v: string) {
    markSearchingToReplaceSelection();
    keyword.value = v;
  },
});

const displayPlaceholder = computed(() => props.placeholder || '请输入编码、项目名称或商品名称');

/** 列宽：有厂家列时全部固定宽（避免名称列 minWidth 吃光空档、挤扁厂家） */
const gridColumns = computed(() => {
  const cols = props.columns;
  const hasName = cols.some((c) => c.prop === 'name');
  // 中药选品列较多：禁止名称列弹性撑开，否则厂家被压成省略号
  const lockAllWidths = cols.some((c) => c.prop === 'manufacturer');
  return cols.map((column, index) => {
    const base = { field: column.prop, title: column.label, showOverflow: true as const };
    const flex = !lockAllWidths && (hasName ? column.prop === 'name' : index === cols.length - 1);
    if (column.prop === 'availableQuantity') {
      return {
        ...base,
        width: column.width,
        slots: { default: 'availableQuantity' },
      };
    }
    if (column.prop === 'manufacturer') {
      return {
        ...base,
        width: column.width,
        minWidth: column.width,
        slots: { default: 'manufacturer' },
      };
    }
    if (column.prop === 'merchandiseName') {
      return {
        ...base,
        width: column.width,
        minWidth: column.width,
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

/** 把 options 同步到表格并重置键盘高亮（库存过滤已在后端分页完成）。 */
function syncGridDataFromOptions() {
  gridOptions.data = options.value;
  keyboardRowIndex.value = options.value.length > 0 ? 0 : -1;
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
      positiveQuantityOnly: onlyPositiveStock.value,
    });
    // 关闭下拉或连续重开后丢弃过期响应，避免旧关键字结果盖住空白重搜
    if (generation !== loadGeneration) {
      return;
    }
    const parsed = parsePagedRowsFromApiData<AdviceProductOption>(result as unknown);
    const list = parsed.content;
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
    // 仅撑满行尾模式需要在数据回填后重算宽度；对齐模式禁止二次校正（会跳一下）
    if (props.dropdownFillToHostRight) {
      scheduleDropdownHostLayout();
    }
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
    // 剂量双击回车展开：短时吞掉紧随的 Enter，防止误选首行药品
    armEnterSelectGuard();
    prepareOpenKeyword();
    showPopup.value = true;
    // 已展开时 showPopup watch 不触发，需显式重拉（中药连续加药常见）
    if (alreadyOpen) {
      void loadOptions();
    }
    await focusTriggerInput();
    scheduleDropdownHostLayout();
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
      // Enter 等未走 prepareOpenKeyword 的入口：按模式补齐检索态
      if (props.keepSelectedLabelOnOpen) {
        if (
          props.modelValue &&
          !searchEdited.value &&
          !keyword.value &&
          !skipSelectedLabelSeed.value
        ) {
          const label = selectedLabel.value;
          if (label) {
            keyword.value = label;
          }
        }
      } else if (keyword.value || options.value.length > 0 || activeCategory.value !== 'ALL') {
        // 非 prepareOpenKeyword 入口打开时，中药仍强制空白重搜
        resetEphemeralSearchState();
      }
      void loadOptions();
      scheduleDropdownHostLayout();
      return;
    }
    // 关闭：作废在途请求；中药额外清掉旧列表，避免下次先闪空/旧结果
    loadGeneration += 1;
    dropdownLayoutObserver?.disconnect();
    dropdownLayoutObserver = null;
    resetTransferredDropdownLayoutMarks();
    keyword.value = '';
    searchEdited.value = false;
    onlyPositiveStock.value = true;
    keyboardRowIndex.value = -1;
    if (!props.keepSelectedLabelOnOpen) {
      options.value = [];
      gridOptions.data = [];
      total.value = 0;
      gridOptions.pagerConfig.total = 0;
      activeCategory.value = 'ALL';
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
  // 开关变更须重新请求后端分页，禁止只筛当前页
  pageNo.value = 1;
  void loadOptions();
});

/** 选品：零库存拦截并提示，不关闭下拉、不回写选中项 */
const handleSelect = ({ row }: { row: AdviceProductOption }) => {
  if (isAdviceCatalogZeroStock(row)) {
    ElMessage.warning(`${String(row.name ?? '该药品')}库存不足，无法开立`);
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
    // 回车刚展开时同样防抖，避免连按直接选中首行
    armEnterSelectGuard();
    openPopup();
    return;
  }
  if (!showPopup.value) return;
  const len = gridOptions.data.length;
  if (!len) return;

  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    e.preventDefault();
    // 用户已主动上下选择，解除误选防抖
    enterSelectGuardUntil = 0;
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
    if (isEnterSelectGuarded()) return;
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
    markSearchingToReplaceSelection();
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
        <div :class="dropdownPanelClass">
          <div
            class="mb-2 flex items-center gap-1 rounded border border-[#d7e4f2] bg-[#f6fbff] p-1"
          >
            <ElButton
              v-for="tab in ADVICE_CATEGORY_TABS"
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
            <template #manufacturer="{ row }">
              <span>{{ String(row.manufacturer ?? '').trim() || '—' }}</span>
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
