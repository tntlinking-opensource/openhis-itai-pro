<script setup lang="ts">
import type { VxePagerEvents } from 'vxe-pc-ui';
import type { VxeGridInstance } from 'vxe-table';

import type { AdviceProductOption, ProductQueryParams } from '../services/advice.types';

import { computed, nextTick, onBeforeUnmount, reactive, ref, useSlots, watch } from 'vue';

import { Search, Star, StarFilled } from '@element-plus/icons-vue';
import { useDebounceFn } from '@vueuse/core';
import { ElButton, ElIcon, ElMessage, ElSwitch } from 'element-plus';
import { VxeInput, VxePulldown } from 'vxe-pc-ui';
import { VxeGrid } from 'vxe-table';

import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  type AnesthFavoriteKind,
  type AnesthFavoriteMedSnapshot,
  adoptAnesthFavoriteLive,
  anesthFavoriteBucket,
  anesthFavoriteRowMatches,
  favoriteMatchesKeyword,
  isAnesthFavoriteMed,
  readAnesthFavoriteLive,
  shiftAnesthFavorite,
  rememberAnesthFavoriteLive,
  removeAnesthFavoriteMed,
  resolveAnesthFavoriteOwner,
  toggleAnesthFavoriteMed,
} from '#/views/inpatient/doctor-station/surgery-anesth/anesth-favorite-meds';

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
     * 锁定类目并隐藏 Tab（手术/麻醉选品：仅药品或仅诊疗，对齐临嘱下拉但收窄范围）。
     */
    lockCategory?: 'MEDICATION' | 'SERVICE';
    /** 诊疗类目过滤（麻醉 25 / 手术 24）；仅 lockCategory=SERVICE 时有意义 */
    categoryEnum?: string;
    /**
     * 已选项目时，首次展开是否回填当前药名（编辑态换药不闪空）。
     * 用户一旦改过检索，再展开不再强制回填旧名，直到换选新品；默认 false。
     */
    keepSelectedLabelOnOpen?: boolean;
    /** 只读态禁止展开选品 */
    disabled?: boolean;
    /**
     * 麻醉申请常用：本机标星，下拉顶部按顺序展示，可拖拽。
     * 麻醉项目传 service，麻醉药品传 medication；其它选品不传。
     */
    favoriteKind?: AnesthFavoriteKind;
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
    lockCategory: undefined,
    categoryEnum: '',
    keepSelectedLabelOnOpen: false,
    disabled: false,
    favoriteKind: undefined,
  },
);

const emit = defineEmits<{
  select: [row: AdviceProductOption];
  'update:modelValue': [value: string];
}>();

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

/** 常用药回查代数：关闭下拉或连续打开时丢掉过期结果 */
let favoriteGeneration = 0;
/** 本次打开已回查到的目录行；missing 表示编码已查过但不在目录 */
const favoriteLive = ref<Record<string, AdviceProductOption | 'missing'>>({});
/** 刚上移/下移的那一行，用来带一点过渡高亮 */
const movingFavoriteId = ref('');
let movingFavoriteTimer = 0;

/** 当前登录人的常用药；未开常用或未登录时为空 */
const favoriteItems = computed(() => {
  if (!props.favoriteKind) {
    return [] as AnesthFavoriteMedSnapshot[];
  }
  const owner = resolveAnesthFavoriteOwner();
  return anesthFavoriteBucket(owner.userId, owner.organizationId, props.favoriteKind);
});

/** 常用块：关键字在名称/编码/商品名/规格里；「只看库存>0」时藏掉已确认的零库存 */
const visibleFavorites = computed(() => {
  const keywordText = keyword.value.trim();
  const matched = favoriteItems.value.filter((item) => favoriteMatchesKeyword(item, keywordText));
  if (!onlyPositiveStock.value) {
    return matched;
  }
  return matched.filter((item) => {
    const live = favoriteLive.value[item.id];
    if (!live || live === 'missing') {
      return true;
    }
    return !isAdviceCatalogZeroStock(live);
  });
});

/** 有常用记录，但开了库存过滤后一条都不显示 */
const favoritesHiddenByStock = computed(() => {
  if (!props.favoriteKind || !onlyPositiveStock.value) {
    return false;
  }
  const keywordText = keyword.value.trim();
  const matched = favoriteItems.value.filter((item) => favoriteMatchesKeyword(item, keywordText));
  if (matched.length === 0 || visibleFavorites.value.length > 0) {
    return false;
  }
  return matched.every((item) => {
    const live = favoriteLive.value[item.id];
    return Boolean(live) && live !== 'missing' && isAdviceCatalogZeroStock(live);
  });
});

const showFavoriteBlock = computed(
  () => Boolean(props.favoriteKind) && (visibleFavorites.value.length > 0 || favoritesHiddenByStock.value),
);

/** 可见的目录类目 Tab（出院带药仅药品；锁定类目时隐藏 Tab） */
const visibleCategoryTabs = computed(() => {
  if (props.lockCategory) {
    return [];
  }
  if (props.catalogScope === 'MEDICATION_ONLY') {
    return ADVICE_CATEGORY_TABS.filter((t) => t.code === 'MEDICATION');
  }
  return ADVICE_CATEGORY_TABS;
});

/** 锁定类目时不展示「全部/药品/诊疗」切页 */
const showCategoryTabs = computed(() => visibleCategoryTabs.value.length > 0);

/** 诊疗锁定时无库存过滤；药品锁定/普通选品保留「只看库存>0」 */
const showStockFilter = computed(() => props.lockCategory !== 'SERVICE');

const slots = useSlots();
/** 父级注入工具条左侧（如麻醉项目/麻醉药品切换） */
const hasToolbarLeading = computed(() => Boolean(slots['toolbar-leading']));

/** 工具条：有 Tab、库存开关或自定义左侧槽才显示 */
const showCatalogToolbar = computed(
  () => showCategoryTabs.value || showStockFilter.value || hasToolbarLeading.value,
);

function productTableNamesForCategory(cat: AdviceCategory): string[] | undefined {
  if (props.lockCategory === 'MEDICATION') {
    return ['med_medication_definition'];
  }
  if (props.lockCategory === 'SERVICE') {
    return ['wkf_activity_definition'];
  }
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

/** 当前实际目录类目（锁定/出院带药优先于 Tab） */
function resolveActiveCategory(): AdviceCategory {
  if (props.lockCategory === 'MEDICATION') {
    return 'MEDICATION';
  }
  if (props.lockCategory === 'SERVICE') {
    return 'SERVICE';
  }
  if (props.catalogScope === 'MEDICATION_ONLY') {
    return 'MEDICATION';
  }
  return activeCategory.value;
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
  // 锁定类目 / 出院带药固定 Tab；其余回全部
  activeCategory.value = resolveActiveCategoryForReset();
  onlyPositiveStock.value = props.lockCategory !== 'SERVICE';
  pageNo.value = 1;
  options.value = [];
  gridOptions.data = [];
  total.value = 0;
  gridOptions.pagerConfig.currentPage = 1;
  gridOptions.pagerConfig.total = 0;
  keyboardRowIndex.value = -1;
}

/** 收起/换锁时重置 Tab：锁定类目固定，否则回全部/药品 */
function resolveActiveCategoryForReset(): AdviceCategory {
  if (props.lockCategory === 'MEDICATION') {
    return 'MEDICATION';
  }
  if (props.lockCategory === 'SERVICE') {
    return 'SERVICE';
  }
  if (props.catalogScope === 'MEDICATION_ONLY') {
    return 'MEDICATION';
  }
  return 'ALL';
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
  resetEphemeralSearchState();
}

/** 点击输入框打开下拉 */
function openPopup() {
  if (props.disabled) {
    return;
  }
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
  if (props.disabled) {
    return;
  }
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

/** 固定列宽之和小于下拉宽度时，由「项目名称」列（无 name 列时最后一列）用 minWidth 吃满剩余宽度 */
const gridColumns = computed(() => {
  const cols = props.columns;
  const hasName = cols.some((c) => c.prop === 'name');
  const mapped = cols.map((column, index) => {
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
  if (!props.favoriteKind) {
    return mapped;
  }
  mapped.push({
    field: '__favorite',
    title: '常用',
    width: 64,
    align: 'center',
    slots: { default: 'favoriteStar' },
  });
  return mapped;
});

const DEFAULT_GRID_HEIGHT = 420;
const MIN_GRID_HEIGHT = 180;
/** 面板内边距 + 工具条大约占用（表格含分页） */
const DROPDOWN_CHROME_PX = 64;
const VIEWPORT_GAP_PX = 12;

/** 优先向下；下方不够再向上，并按剩余视口收缩表格高度 */
const panelPlacement = ref<'bottom' | 'top'>('bottom');

const gridOptions = reactive({
  autoResize: true,
  border: true,
  height: DEFAULT_GRID_HEIGHT,
  rowConfig: { keyField: 'id', isHover: true, isCurrent: true },
  pagerConfig: {
    enabled: true,
    currentPage: pageNo.value,
    pageSize: pageSize.value,
    total: total.value,
  },
  data: [] as AdviceProductOption[],
});

/**
 * 按触发器相对视口位置决定弹出方向与表格高度，避免上下被裁切。
 */
function applyDropdownViewportLayout() {
  const el = triggerWrapRef.value;
  if (!el) {
    panelPlacement.value = 'bottom';
    gridOptions.height = DEFAULT_GRID_HEIGHT;
    return;
  }
  const rect = el.getBoundingClientRect();
  const favoriteRows = visibleFavorites.value.length + (favoritesHiddenByStock.value ? 1 : 0);
  const favoriteChrome = showFavoriteBlock.value ? Math.min(216, 50 + favoriteRows * 30) : 0;
  const chrome =
    (showCatalogToolbar.value ? DROPDOWN_CHROME_PX + 36 : DROPDOWN_CHROME_PX) + favoriteChrome;
  const spaceBelow = window.innerHeight - rect.bottom - VIEWPORT_GAP_PX;
  const spaceAbove = rect.top - VIEWPORT_GAP_PX;
  const preferBottom = spaceBelow >= MIN_GRID_HEIGHT + chrome || spaceBelow >= spaceAbove;
  const space = preferBottom ? spaceBelow : spaceAbove;
  panelPlacement.value = preferBottom ? 'bottom' : 'top';
  gridOptions.height = Math.max(
    MIN_GRID_HEIGHT,
    Math.min(DEFAULT_GRID_HEIGHT, Math.floor(space - chrome)),
  );
}

function onWindowResizeWhileOpen() {
  if (!showPopup.value) {
    return;
  }
  applyDropdownViewportLayout();
}

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

function favoriteOwnerReady(): { organizationId: string; userId: string } | null {
  const owner = resolveAnesthFavoriteOwner();
  if (!owner.userId) {
    return null;
  }
  return owner;
}

function sameFavoriteRow(row: AdviceProductOption, item: AnesthFavoriteMedSnapshot): boolean {
  return anesthFavoriteRowMatches(item, row);
}

/** 按编码回查常用药的当前库存和开立字段；不改目录分页 */
async function refreshFavoriteLives() {
  if (!props.favoriteKind) {
    favoriteLive.value = {};
    return;
  }
  const owner = favoriteOwnerReady();
  const items = favoriteItems.value;
  const generation = ++favoriteGeneration;
  if (!owner || items.length === 0) {
    favoriteLive.value = {};
    return;
  }
  const next: Record<string, AdviceProductOption | 'missing'> = {};
  const pending: AnesthFavoriteMedSnapshot[] = [];
  for (const item of items) {
    const cached = readAnesthFavoriteLive(owner.organizationId, item.id);
    if (cached) {
      next[item.id] = cached;
    } else {
      pending.push(item);
    }
  }
  favoriteLive.value = next;
  if (pending.length === 0) {
    return;
  }
  const fetched = await Promise.all(
    pending.map(async (item) => {
      const text = item.code.trim() || item.name.trim();
      if (!text) {
        return { id: item.id, row: 'missing' as const };
      }
      try {
        const cat = props.favoriteKind === 'service' ? 'SERVICE' : 'MEDICATION';
        const result = await props.remoteMethod({
          pageNo: 1,
          pageSize: 20,
          keyword: text,
          productTableNames: productTableNamesForCategory(cat),
          positiveQuantityOnly: false,
          categoryEnum: props.categoryEnum?.trim() || undefined,
        });
        const parsed = parsePagedRowsFromApiData<AdviceProductOption>(result as unknown);
        const hit = parsed.content.find((row) => sameFavoriteRow(row, item));
        return { id: item.id, row: hit ?? ('missing' as const) };
      } catch {
        return { id: item.id, row: 'missing' as const };
      }
    }),
  );
  if (generation !== favoriteGeneration) {
    return;
  }
  const merged = { ...favoriteLive.value };
  const kind = props.favoriteKind;
  for (const entry of fetched) {
    let liveId = entry.id;
    if (kind && entry.row !== 'missing') {
      liveId = adoptAnesthFavoriteLive(
        owner.userId,
        owner.organizationId,
        kind,
        entry.id,
        entry.row,
      );
    }
    merged[liveId] = entry.row;
    rememberAnesthFavoriteLive(owner.organizationId, liveId, entry.row);
  }
  favoriteLive.value = merged;
}

function favoriteIsZeroStock(item: AnesthFavoriteMedSnapshot): boolean {
  const live = favoriteLive.value[item.id];
  if (!live || live === 'missing') {
    return false;
  }
  return isAdviceCatalogZeroStock(live);
}

function favoriteStockLabel(item: AnesthFavoriteMedSnapshot): string {
  const live = favoriteLive.value[item.id];
  if (!live) {
    return '刷新中';
  }
  if (live === 'missing') {
    return '已不在目录';
  }
  return formatAdviceCatalogStockCellText(live);
}

function favoriteClickDisabled(item: AnesthFavoriteMedSnapshot): boolean {
  const live = favoriteLive.value[item.id];
  return !live || live === 'missing';
}

function onFavoriteClick(item: AnesthFavoriteMedSnapshot) {
  const live = favoriteLive.value[item.id];
  if (!live) {
    return;
  }
  if (live === 'missing') {
    ElMessage.warning('该项已不在目录，可取消常用');
    return;
  }
  handleSelect({ row: live });
}

function onToggleFavorite(row: AdviceProductOption, event: Event) {
  event.stopPropagation();
  const owner = favoriteOwnerReady();
  const kind = props.favoriteKind;
  if (!owner || !kind) {
    ElMessage.warning('未获取到登录信息，暂时不能保存常用');
    return;
  }
  const result = toggleAnesthFavoriteMed(owner.userId, owner.organizationId, kind, row);
  if (result === 'limit') {
    ElMessage.warning('常用最多 20 条');
    return;
  }
  if (result === 'added') {
    favoriteLive.value = { ...favoriteLive.value, [String(row.id)]: row };
    rememberAnesthFavoriteLive(owner.organizationId, String(row.id), row);
    ElMessage.success('已加入常用');
    void nextTick(() => applyDropdownViewportLayout());
    return;
  }
  const next = { ...favoriteLive.value };
  delete next[String(row.id)];
  favoriteLive.value = next;
  ElMessage.success('已取消常用');
  void nextTick(() => applyDropdownViewportLayout());
}

function onUnpinFavorite(item: AnesthFavoriteMedSnapshot, event: Event) {
  event.stopPropagation();
  const owner = favoriteOwnerReady();
  const kind = props.favoriteKind;
  if (!owner || !kind) {
    return;
  }
  removeAnesthFavoriteMed(owner.userId, owner.organizationId, kind, item.id);
  const next = { ...favoriteLive.value };
  delete next[item.id];
  favoriteLive.value = next;
  void nextTick(() => applyDropdownViewportLayout());
}

function rowIsFavorite(row: AdviceProductOption): boolean {
  const owner = favoriteOwnerReady();
  const kind = props.favoriteKind;
  if (!owner || !kind) {
    return false;
  }
  return isAnesthFavoriteMed(owner.userId, owner.organizationId, kind, row);
}

function favoriteCode(item: AnesthFavoriteMedSnapshot): string {
  const live = favoriteLive.value[item.id];
  if (live && live !== 'missing') {
    const code = String(live.code ?? '').trim();
    if (code) {
      return code;
    }
  }
  return item.code.trim() || '—';
}

function favoriteName(item: AnesthFavoriteMedSnapshot): string {
  const live = favoriteLive.value[item.id];
  if (live && live !== 'missing') {
    const name = String(live.name ?? '').trim();
    if (name) {
      return name;
    }
  }
  return item.name.trim() || '—';
}

function favoriteSpec(item: AnesthFavoriteMedSnapshot): string {
  const live = favoriteLive.value[item.id];
  if (live && live !== 'missing') {
    const spec = String(live.spec ?? '').trim();
    if (spec) {
      return spec;
    }
  }
  return item.spec.trim() || '—';
}

function favoriteSortNo(item: AnesthFavoriteMedSnapshot): number {
  const index = favoriteItems.value.findIndex((row) => row.id === item.id);
  return index >= 0 ? index + 1 : 0;
}

function canShiftFavorite(item: AnesthFavoriteMedSnapshot, delta: -1 | 1): boolean {
  const index = favoriteItems.value.findIndex((row) => row.id === item.id);
  if (index < 0) {
    return false;
  }
  const next = index + delta;
  return next >= 0 && next < favoriteItems.value.length;
}

function markMovingFavorite(id: string) {
  movingFavoriteId.value = id;
  window.clearTimeout(movingFavoriteTimer);
  movingFavoriteTimer = window.setTimeout(() => {
    if (movingFavoriteId.value === id) {
      movingFavoriteId.value = '';
    }
  }, 420);
}

function onShiftFavorite(item: AnesthFavoriteMedSnapshot, delta: -1 | 1, event: Event) {
  event.stopPropagation();
  const owner = favoriteOwnerReady();
  const kind = props.favoriteKind;
  if (!owner || !kind || !canShiftFavorite(item, delta)) {
    return;
  }
  shiftAnesthFavorite(owner.userId, owner.organizationId, kind, item.id, delta);
  markMovingFavorite(item.id);
}

const loadOptions = async () => {
  const generation = ++loadGeneration;
  loading.value = true;
  const favoriteTask = refreshFavoriteLives();
  try {
    const cat = resolveActiveCategory();
    const result = await props.remoteMethod({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim(),
      productTableNames: productTableNamesForCategory(cat),
      positiveQuantityOnly: cat === 'SERVICE' ? false : onlyPositiveStock.value,
      categoryEnum: props.categoryEnum?.trim() || undefined,
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
    await favoriteTask;
    await syncGridCurrentRow();
    applyDropdownViewportLayout();
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
  async (visible) => {
    if (visible) {
      pageNo.value = 1;
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
      } else {
        const defaultCategory = resolveActiveCategoryForReset();
        if (keyword.value || options.value.length > 0 || activeCategory.value !== defaultCategory) {
          resetEphemeralSearchState();
        }
      }
      await nextTick();
      applyDropdownViewportLayout();
      window.addEventListener('resize', onWindowResizeWhileOpen);
      void loadOptions();
      // 布局后再算一次：工具条渲染后 chrome 更准
      await nextTick();
      applyDropdownViewportLayout();
      return;
    }
    window.removeEventListener('resize', onWindowResizeWhileOpen);
    loadGeneration += 1;
    favoriteGeneration += 1;
    keyword.value = '';
    searchEdited.value = false;
    onlyPositiveStock.value = props.lockCategory !== 'SERVICE';
    keyboardRowIndex.value = -1;
    panelPlacement.value = 'bottom';
    gridOptions.height = DEFAULT_GRID_HEIGHT;
    if (!props.keepSelectedLabelOnOpen) {
      options.value = [];
      gridOptions.data = [];
      total.value = 0;
      gridOptions.pagerConfig.total = 0;
      activeCategory.value = resolveActiveCategoryForReset();
    }
    void nextTick(() => gridRef.value?.clearCurrentRow());
  },
);

onBeforeUnmount(() => {
  window.removeEventListener('resize', onWindowResizeWhileOpen);
  window.clearTimeout(movingFavoriteTimer);
});

watch(
  () => [props.lockCategory, props.categoryEnum] as const,
  async () => {
    activeCategory.value = resolveActiveCategoryForReset();
    onlyPositiveStock.value = props.lockCategory !== 'SERVICE';
    pageNo.value = 1;
    if (showPopup.value) {
      await nextTick();
      applyDropdownViewportLayout();
      void loadOptions();
    }
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

/** 点常用列只标星，不把该行加入申请明细 */
const onGridCellClick = (params: { column?: { field?: string }; row: AdviceProductOption }) => {
  if (params.column?.field === '__favorite') {
    return;
  }
  handleSelect({ row: params.row });
};

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
    <VxePulldown
      v-model="showPopup"
      transfer
      :placement="panelPlacement"
      :z-index="6000"
    >
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
            :disabled="disabled"
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
            v-if="showCatalogToolbar"
            class="mb-2 flex items-center gap-1 rounded border border-[#d7e4f2] bg-[#f6fbff] p-1"
          >
            <slot name="toolbar-leading" />
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
              v-if="showStockFilter"
              class="ml-auto flex shrink-0 items-center gap-2 px-2 text-sm text-[var(--el-text-color-regular)]"
            >
              <span class="whitespace-nowrap">只看库存&gt;0</span>
              <ElSwitch v-model="onlyPositiveStock" size="small" />
            </div>
          </div>
          <div v-if="showFavoriteBlock" class="advice-order-select-fav">
            <div class="advice-order-select-fav__title">常用</div>
            <div class="advice-order-select-fav__head">
              <span>序号</span>
              <span>编码</span>
              <span>项目名称</span>
              <span>规格</span>
              <span>库存</span>
              <span>操作</span>
            </div>
            <div v-if="favoritesHiddenByStock" class="advice-order-select-fav__empty">
              常用项当前无库存，已隐藏
            </div>
            <TransitionGroup name="fav-list" tag="div" class="advice-order-select-fav__list">
            <div
              v-for="item in visibleFavorites"
              :key="item.id"
              class="advice-order-select-fav__row"
              :class="{
                'is-disabled': favoriteClickDisabled(item),
                'is-moving': movingFavoriteId === item.id,
              }"
              role="button"
              tabindex="0"
              @click="onFavoriteClick(item)"
              @keydown.enter.prevent="onFavoriteClick(item)"
            >
              <span class="advice-order-select-fav__no">{{ favoriteSortNo(item) }}</span>
              <span class="advice-order-select-fav__code" :title="favoriteCode(item)">
                {{ favoriteCode(item) }}
              </span>
              <span class="advice-order-select-fav__name" :title="favoriteName(item)">
                {{ favoriteName(item) }}
              </span>
              <span class="advice-order-select-fav__spec" :title="favoriteSpec(item)">
                {{ favoriteSpec(item) }}
              </span>
              <span
                class="advice-order-select-fav__stock"
                :class="{
                  'is-missing': favoriteLive[item.id] === 'missing',
                  'is-zero': favoriteIsZeroStock(item),
                }"
              >
                {{ favoriteStockLabel(item) }}
              </span>
              <span class="advice-order-select-fav__ops" @click.stop>
                <button
                  type="button"
                  class="advice-order-select-fav__link"
                  :disabled="!canShiftFavorite(item, -1)"
                  @click="onShiftFavorite(item, -1, $event)"
                >
                  上移
                </button>
                <button
                  type="button"
                  class="advice-order-select-fav__link"
                  :disabled="!canShiftFavorite(item, 1)"
                  @click="onShiftFavorite(item, 1, $event)"
                >
                  下移
                </button>
                <button
                  type="button"
                  class="advice-order-select-fav__star"
                  title="取消常用"
                  @click="onUnpinFavorite(item, $event)"
                >
                  <ElIcon :size="16">
                    <StarFilled />
                  </ElIcon>
                </button>
              </span>
            </div>
            </TransitionGroup>
          </div>
          <VxeGrid
            ref="gridRef"
            v-bind="gridOptions"
            :columns="gridColumns"
            :loading="loading"
            @cell-click="onGridCellClick"
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
            <template #favoriteStar="{ row }">
              <button
                type="button"
                class="advice-order-select-star"
                :class="{ 'is-on': rowIsFavorite(row) }"
                :title="rowIsFavorite(row) ? '取消常用' : '设为常用'"
                @click="onToggleFavorite(row, $event)"
              >
                <ElIcon :size="16">
                  <StarFilled v-if="rowIsFavorite(row)" />
                  <Star v-else />
                </ElIcon>
              </button>
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

.advice-order-select-fav {
  max-height: 216px;
  margin-bottom: 8px;
  overflow: auto;
  border: 1px solid #d7e4f2;
  border-radius: 4px;
  background: #f8fbff;
}

.advice-order-select-fav__title {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 4px 8px;
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  color: var(--el-color-primary);
  background: #f8fbff;
}

.advice-order-select-fav__empty {
  padding: 4px 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.advice-order-select-fav__head,
.advice-order-select-fav__row {
  display: grid;
  grid-template-columns: 44px 136px minmax(0, 1.8fr) minmax(0, 0.8fr) 96px 124px;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 4px 8px;
}

.advice-order-select-fav__head {
  position: sticky;
  top: 28px;
  z-index: 1;
  border-top: 1px solid #e6eef6;
  background: #eef6fd;
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  color: var(--el-text-color-regular);
}

.advice-order-select-fav__list {
  position: relative;
}

.advice-order-select-fav__row {
  border: 0;
  border-top: 1px solid #e6eef6;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.35s ease;
}

.advice-order-select-fav__row.is-moving,
.advice-order-select-fav__row.is-moving:hover {
  background: var(--el-color-primary-light-8);
}

.fav-list-move {
  transition: transform 0.32s cubic-bezier(0.22, 1, 0.36, 1);
}

.fav-list-enter-active,
.fav-list-leave-active {
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;
}

.fav-list-enter-from {
  opacity: 0;
  transform: translateY(-8px);
}

.fav-list-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

.fav-list-leave-active {
  position: absolute;
  width: 100%;
}

.advice-order-select-fav__no {
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-regular);
  text-align: center;
}

.advice-order-select-fav__ops {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.advice-order-select-fav__link {
  padding: 0;
  border: 0;
  background: transparent;
  font-size: 13px;
  line-height: 20px;
  color: var(--el-color-primary);
  cursor: pointer;
}

.advice-order-select-fav__link:hover:not(:disabled) {
  color: var(--el-color-primary-light-3);
}

.advice-order-select-fav__link:disabled {
  color: var(--el-text-color-placeholder);
  cursor: not-allowed;
}

.advice-order-select-fav__row.is-disabled {
  cursor: default;
}

.advice-order-select-fav__row:hover {
  background: var(--el-fill-color-light);
}

.advice-order-select-fav__code,
.advice-order-select-fav__name,
.advice-order-select-fav__spec {
  overflow: hidden;
  font-size: 13px;
  line-height: 21px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.advice-order-select-fav__code,
.advice-order-select-fav__name {
  font-weight: 500;
}

.advice-order-select-fav__spec {
  color: var(--el-text-color-regular);
}

.advice-order-select-fav__stock {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.advice-order-select-fav__stock.is-zero,
.advice-order-select-fav__stock.is-missing {
  color: var(--el-color-danger);
}

.advice-order-select-fav__star,
.advice-order-select-star {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-text-color-placeholder);
  cursor: pointer;
}

.advice-order-select-fav__star,
.advice-order-select-star.is-on {
  color: var(--el-color-warning);
}
</style>
