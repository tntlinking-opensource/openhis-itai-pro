<script setup lang="ts">
import type {
  ChargeDiscountConfigItem,
  ChargeDiscountConfigPageInit,
  DiscountCatalogOption,
} from '#/api/basic/charge-discount-config';
import type {
  DeviceCatalogItem,
  MedicationCatalogItem,
  ServiceCatalogItem,
} from '#/api/core/system/catalog';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCheckbox,
  ElDialog,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { pageDeviceCatalogApi, pageMedicationCatalogApi, pageServiceCatalogApi } from '#/api';
import {
  CHARGE_DISCOUNT_STATUS,
  DISCOUNT_CATALOG_SEARCH_MODE,
  changeChargeDiscountConfigStatusApi,
  createChargeDiscountConfigApi,
  deleteChargeDiscountConfigApi,
  discountRateToZhe,
  formatDiscountRateDisplay,
  isChargeDiscountConfigActive,
  loadChargeDiscountConfigPageInitApi,
  modifyChargeDiscountConfigApi,
  pageChargeDiscountConfigsApi,
  searchDiscountCatalogOptionsApi,
  zheToDiscountRate,
} from '#/api/basic/charge-discount-config';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { decimalMul, decimalSub } from '#/utils/format/decimal-util';
import { formatMoneyYuanWithSymbol } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import SelectTable from '#/components/select-table.vue';

defineOptions({ name: 'BasicChargeDiscountConfigIndex' });

/**
 * 主类型 T5 + 附加 T3。
 * 上：药品/诊疗/耗材大类定折 + 明细查询弹窗；
 * 下：单项目折扣配置列表（仅 productId 行），支持新增/启停/删除。
 */

const PRODUCT_TABLE_MEDICATION = 'med_medication_definition';
const PRODUCT_TABLE_ACTIVITY = 'wkf_activity_definition';
const PRODUCT_TABLE_DEVICE = 'adm_device_definition';
const DEFAULT_ZHE = 8.5;

const CATALOG_DEFS = [
  {
    key: 'medication',
    productTable: PRODUCT_TABLE_MEDICATION,
    title: '药品',
    short: '药',
    theme: 'med',
  },
  {
    key: 'activity',
    productTable: PRODUCT_TABLE_ACTIVITY,
    title: '诊疗',
    short: '诊',
    theme: 'act',
  },
  {
    key: 'device',
    productTable: PRODUCT_TABLE_DEVICE,
    title: '耗材',
    short: '耗',
    theme: 'dev',
  },
] as const;

type CatalogKey = (typeof CATALOG_DEFS)[number]['key'];
type CatalogTheme = (typeof CATALOG_DEFS)[number]['theme'];

interface CategoryRuleRow {
  categoryCode: string;
  categoryName: string;
  itemCount: number;
  configId?: string;
  enabled: boolean;
  /** 折值展示字符串，避免丑步进器 */
  zheText: string;
  saving: boolean;
}

interface CatalogPanel {
  key: CatalogKey;
  productTable: string;
  title: string;
  short: string;
  theme: CatalogTheme;
  rows: CategoryRuleRow[];
  loading: boolean;
  saving: boolean;
  bulkZheText: string;
  configByCategoryCode: Map<string, ChargeDiscountConfigItem>;
  configByProductId: Map<string, ChargeDiscountConfigItem>;
}

interface ProductOverrideRow {
  /** 已落库 id；新增草稿用临时 id */
  id: string;
  isNew?: boolean;
  editing?: boolean;
  saving?: boolean;
  productTable: string;
  productTableName: string;
  productId: string;
  productName: string;
  selectedLabel?: string;
  discountRate: null | number;
  /** 行编辑用折值文案 */
  zheText: string;
  originalPrice: null | number;
  statusEnum: string;
  statusEnumText: string;
  remark?: string;
}

interface CatalogDetailRow {
  productId: string;
  name: string;
  busNo?: string;
  categoryCode?: string;
  categoryName?: string;
  originalPrice?: null | number;
  /** 命中折扣率 (0,1]；无折扣为 null */
  effectiveRate: null | number;
}

const productTableOptions = ref<ApiOptionItem[]>([]);
const panels = reactive<CatalogPanel[]>(
  CATALOG_DEFS.map((def) => ({
    key: def.key,
    productTable: def.productTable,
    title: def.title,
    short: def.short,
    theme: def.theme,
    rows: [],
    loading: false,
    saving: false,
    bulkZheText: String(DEFAULT_ZHE),
    configByCategoryCode: new Map(),
    configByProductId: new Map(),
  })),
);

const overrideKeyword = ref('');
const overrideTableFilter = ref('');
const overridePageNo = ref(1);
const overridePageSize = ref(20);
const overrideTotal = ref(0);
const overrideRows = ref<ProductOverrideRow[]>([]);
const overrideLoading = ref(false);

const detailVisible = ref(false);
const detailPanel = ref<CatalogPanel | null>(null);
const detailKeyword = ref('');
const detailPageNo = ref(1);
const detailPageSize = ref(20);
const detailTotal = ref(0);
const detailRows = ref<CatalogDetailRow[]>([]);
const detailLoading = ref(false);

const overrideTableOptions = computed(() =>
  panels.map((p) => ({ label: p.title, value: p.productTable })),
);

const productPickColumns = [
  { prop: 'name', label: '名称', minWidth: 160 },
  { prop: 'busNo', label: '编码', width: 120 },
  { prop: 'priceText', label: '原价', width: 90 },
];

/** 是否存在未保存行编辑 */
const hasEditingOverride = computed(() => overrideRows.value.some((row) => row.editing));

/** init 回填面板标题 */
function applyPanelTitlesFromInit() {
  for (const panel of panels) {
    const opt = productTableOptions.value.find((o) => String(o.value ?? '') === panel.productTable);
    if (opt?.label?.trim()) {
      panel.title = opt.label.trim();
    }
  }
}

/** 面板目录项合计 */
function panelItemTotal(panel: CatalogPanel): number {
  let sum = 0;
  for (const row of panel.rows) {
    sum += row.itemCount || 0;
  }
  return sum;
}

/** 已启用大类数 */
function enabledCount(panel: CatalogPanel): number {
  return panel.rows.filter((row) => row.enabled).length;
}

/** 折文案 → 率 */
function requireRateFromZheText(text: string): number {
  const rate = zheToDiscountRate(text.trim());
  if (rate === null) {
    throw new Error('折扣值须在 (0,10] 折内，例如 8.5');
  }
  return rate;
}

/** 率 → 折展示 */
function toZheText(rate: null | number | string | undefined): string {
  const zhe = discountRateToZhe(rate);
  return zhe === null ? String(DEFAULT_ZHE) : String(zhe);
}

/** 加载初始化 */
async function loadInitOptions() {
  try {
    const data = (await loadChargeDiscountConfigPageInitApi()) as
      | ChargeDiscountConfigPageInit
      | undefined;
    productTableOptions.value = data?.productTableOptions ?? [];
  } catch {
    productTableOptions.value = [];
  }
  applyPanelTitlesFromInit();
}

/** 拉取大类/单项配置索引 */
async function loadCategoryConfigs(panel: CatalogPanel) {
  const res = await pageChargeDiscountConfigsApi({
    pageNo: 1,
    pageSize: 500,
    productTable: panel.productTable,
  });
  const parsed = parsePagedRowsFromApiData<ChargeDiscountConfigItem>(res);
  const byCategory = new Map<string, ChargeDiscountConfigItem>();
  const byProduct = new Map<string, ChargeDiscountConfigItem>();
  for (const raw of parsed.content) {
    const row: ChargeDiscountConfigItem = {
      ...raw,
      id: preserveApiIdForRequest(raw.id),
      productId: preserveApiIdForRequest(raw.productId) ?? null,
      orgId: preserveApiIdForRequest(raw.orgId) ?? null,
    };
    const productId = preserveApiIdForRequest(row.productId);
    if (productId) {
      byProduct.set(productId, row);
      continue;
    }
    const code = row.categoryCode?.trim();
    if (!code) {
      continue;
    }
    byCategory.set(code, row);
  }
  panel.configByCategoryCode = byCategory;
  panel.configByProductId = byProduct;
}

/** 配置 → 规则行 */
function buildCategoryRuleRow(
  categoryCode: string,
  categoryName: string,
  itemCount: number,
  config?: ChargeDiscountConfigItem,
): CategoryRuleRow {
  if (!config) {
    return {
      categoryCode,
      categoryName,
      itemCount,
      enabled: false,
      zheText: String(DEFAULT_ZHE),
      saving: false,
    };
  }
  return {
    categoryCode,
    categoryName: categoryName || config.categoryName?.trim() || categoryCode,
    itemCount,
    configId: preserveApiIdForRequest(config.id),
    enabled: isChargeDiscountConfigActive(config.statusEnum),
    zheText: toZheText(config.discountRate),
    saving: false,
  };
}

/** 加载单块大类规则 */
async function loadPanelRules(panel: CatalogPanel) {
  panel.loading = true;
  try {
    await loadCategoryConfigs(panel);
    const res = await searchDiscountCatalogOptionsApi({
      productTable: panel.productTable,
      searchMode: DISCOUNT_CATALOG_SEARCH_MODE.CATEGORY,
      pageNo: 1,
      pageSize: 500,
    });
    const parsed = parsePagedRowsFromApiData<DiscountCatalogOption>(res);
    const byConfig = panel.configByCategoryCode;
    panel.rows = parsed.content
      .filter((opt) => String(opt.value ?? '').trim())
      .map((opt) => {
        const code = String(opt.value ?? '').trim();
        return buildCategoryRuleRow(
          code,
          opt.label?.trim() || code,
          Number(opt.itemCount) || 0,
          byConfig.get(code),
        );
      });
  } catch {
    ElMessage.error(`加载${panel.title}折扣规则失败`);
    panel.rows = [];
  } finally {
    panel.loading = false;
  }
}

/** 并行加载三块 */
async function loadAllPanelRules() {
  await Promise.all(panels.map((panel) => loadPanelRules(panel)));
}

/** 勾选启停 */
function handleCategoryEnabledChange(
  panel: CatalogPanel,
  row: CategoryRuleRow,
  val: boolean | number | string,
) {
  void persistCategoryEnabled(panel, row, !!val);
}

/** 启停大类并写死折扣率 */
async function persistCategoryEnabled(panel: CatalogPanel, row: CategoryRuleRow, enabled: boolean) {
  if (row.saving || panel.saving) {
    return;
  }
  row.saving = true;
  try {
    if (!enabled) {
      const id = preserveApiIdForRequest(row.configId);
      if (id) {
        await changeChargeDiscountConfigStatusApi({
          id,
          statusEnum: CHARGE_DISCOUNT_STATUS.INACTIVE,
        });
      }
      row.enabled = false;
      ElMessage.success('已取消参与');
      await refreshPanelAfterChange(panel);
      return;
    }
    const rate = requireRateFromZheText(row.zheText);
    const id = preserveApiIdForRequest(row.configId);
    if (id) {
      await changeChargeDiscountConfigStatusApi({
        id,
        statusEnum: CHARGE_DISCOUNT_STATUS.ACTIVE,
      });
      await modifyChargeDiscountConfigApi({
        id,
        productTable: panel.productTable,
        categoryCode: row.categoryCode,
        productId: null,
        discountRate: rate,
      });
    } else {
      const created = await createChargeDiscountConfigApi({
        productTable: panel.productTable,
        categoryCode: row.categoryCode,
        productId: null,
        discountRate: rate,
      });
      row.configId = preserveApiIdForRequest(created?.id);
    }
    row.enabled = true;
    ElMessage.success('已参与折扣');
    await refreshPanelAfterChange(panel);
  } catch (error: unknown) {
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err.msg || err.message || '更新失败');
    await loadPanelRules(panel);
  } finally {
    row.saving = false;
  }
}

/** 已启用大类改折 */
async function handleCategoryZheBlur(panel: CatalogPanel, row: CategoryRuleRow) {
  if (!row.enabled || row.saving) {
    return;
  }
  const id = preserveApiIdForRequest(row.configId);
  if (!id) {
    return;
  }
  row.saving = true;
  try {
    const rate = requireRateFromZheText(row.zheText);
    row.zheText = toZheText(rate);
    await modifyChargeDiscountConfigApi({
      id,
      productTable: panel.productTable,
      categoryCode: row.categoryCode,
      productId: null,
      discountRate: rate,
    });
    ElMessage.success('折扣已更新');
    await refreshPanelAfterChange(panel);
  } catch (error: unknown) {
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err.msg || err.message || '更新折扣失败');
    await loadPanelRules(panel);
  } finally {
    row.saving = false;
  }
}

/** 统一应用到已启用 */
async function handleApplyBulk(panel: CatalogPanel) {
  const targets = panel.rows.filter((row) => row.enabled);
  if (!targets.length) {
    ElMessage.warning('请先勾选需要参与折扣的大类');
    return;
  }
  let rate: number;
  try {
    rate = requireRateFromZheText(panel.bulkZheText);
  } catch (error: unknown) {
    const err = error as { message?: string };
    ElMessage.error(err.message || '统一折扣值不合法');
    return;
  }
  const zheText = toZheText(rate);
  panel.bulkZheText = zheText;
  panel.saving = true;
  try {
    for (const row of targets) {
      const id = preserveApiIdForRequest(row.configId);
      if (!id) {
        continue;
      }
      await modifyChargeDiscountConfigApi({
        id,
        productTable: panel.productTable,
        categoryCode: row.categoryCode,
        productId: null,
        discountRate: rate,
      });
      row.zheText = zheText;
    }
    ElMessage.success(`已应用到 ${targets.length} 个已启用大类`);
    await refreshPanelAfterChange(panel);
  } catch (error: unknown) {
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err.msg || err.message || '统一设置失败');
    await loadPanelRules(panel);
  } finally {
    panel.saving = false;
  }
}

/** 规则变更后刷新本块 */
async function refreshPanelAfterChange(panel: CatalogPanel) {
  await loadCategoryConfigs(panel);
  const byConfig = panel.configByCategoryCode;
  panel.rows = panel.rows.map((row) =>
    buildCategoryRuleRow(
      row.categoryCode,
      row.categoryName,
      row.itemCount,
      byConfig.get(row.categoryCode),
    ),
  );
}

/** 解析目录原价 */
function resolveCatalogPrice(row: Record<string, unknown>): null | number {
  const raw = row.retailPrice ?? row.salePrice ?? row.unitPrice ?? row.price;
  if (raw === null || raw === undefined || raw === '') {
    return null;
  }
  const n = Number(raw);
  return Number.isFinite(n) ? n : null;
}

/** 单项 > 大类 命中有效折扣率 */
function resolveDetailEffectiveRate(
  panel: CatalogPanel,
  productId: string,
  categoryCode?: string,
): null | number {
  const productConfig = panel.configByProductId.get(productId);
  if (productConfig && isChargeDiscountConfigActive(productConfig.statusEnum)) {
    const zhe = discountRateToZhe(productConfig.discountRate);
    return zhe === null ? null : zheToDiscountRate(zhe);
  }
  if (!categoryCode) {
    return null;
  }
  const categoryConfig = panel.configByCategoryCode.get(categoryCode);
  if (categoryConfig && isChargeDiscountConfigActive(categoryConfig.statusEnum)) {
    const zhe = discountRateToZhe(categoryConfig.discountRate);
    return zhe === null ? null : zheToDiscountRate(zhe);
  }
  // 面板 UI 已勾选但尚未刷索引时，用行上折值兜底
  const rule = panel.rows.find((r) => r.categoryCode === categoryCode);
  if (rule?.enabled) {
    return zheToDiscountRate(rule.zheText);
  }
  return null;
}

/** 明细折扣展示 */
function formatDetailDiscount(row: CatalogDetailRow): string {
  if (row.effectiveRate === null) {
    return '—';
  }
  return formatDiscountRateDisplay(row.effectiveRate);
}

/** 明细折后价 */
function formatDetailDiscountedPrice(row: CatalogDetailRow): string {
  if (row.effectiveRate === null || row.originalPrice === null || row.originalPrice === undefined) {
    return '—';
  }
  return formatMoneyYuanWithSymbol(decimalMul(row.originalPrice, row.effectiveRate, 2));
}

/** 分页拉目录（明细查询弹窗） */
async function fetchCatalogPage(
  productTable: string,
  pageNo: number,
  pageSize: number,
  keyword?: string,
): Promise<{ content: Record<string, unknown>[]; totalCount: number }> {
  if (productTable === PRODUCT_TABLE_ACTIVITY) {
    const response = await pageServiceCatalogApi({
      keyword,
      pageNo,
      pageSize,
    });
    const parsed = parsePagedRowsFromApiData<ServiceCatalogItem>(response?.data);
    return {
      content: parsed.content as Record<string, unknown>[],
      totalCount: parsed.totalCount,
    };
  }
  if (productTable === PRODUCT_TABLE_MEDICATION) {
    const response = await pageMedicationCatalogApi({
      keyword,
      pageNo,
      pageSize,
    });
    const parsed = parsePagedRowsFromApiData<MedicationCatalogItem>(response?.data);
    return {
      content: parsed.content as Record<string, unknown>[],
      totalCount: parsed.totalCount,
    };
  }
  if (productTable === PRODUCT_TABLE_DEVICE) {
    const response = await pageDeviceCatalogApi({
      keyword,
      pageNo,
      pageSize,
    });
    const parsed = parsePagedRowsFromApiData<DeviceCatalogItem>(response?.data);
    return {
      content: parsed.content as Record<string, unknown>[],
      totalCount: parsed.totalCount,
    };
  }
  const res = await searchDiscountCatalogOptionsApi({
    productTable,
    searchMode: DISCOUNT_CATALOG_SEARCH_MODE.ITEM,
    keyword,
    pageNo,
    pageSize,
  });
  const parsed = parsePagedRowsFromApiData<DiscountCatalogOption>(res);
  return {
    content: parsed.content.map((opt) => ({
      id: preserveApiIdForRequest(opt.id ?? opt.value),
      name: opt.label,
      busNo: opt.busNo,
    })),
    totalCount: parsed.totalCount,
  };
}

/** 打开明细查询 */
function handleOpenDetail(panel: CatalogPanel) {
  detailPanel.value = panel;
  detailKeyword.value = '';
  detailPageNo.value = 1;
  detailVisible.value = true;
  void loadDetailRows();
}

/** 加载明细查询行 */
async function loadDetailRows() {
  const panel = detailPanel.value;
  if (!panel) {
    detailRows.value = [];
    detailTotal.value = 0;
    return;
  }
  detailLoading.value = true;
  try {
    // 打开弹窗时刷新配置索引，保证折扣与折后价最新
    await loadCategoryConfigs(panel);
    const page = await fetchCatalogPage(
      panel.productTable,
      detailPageNo.value,
      detailPageSize.value,
      detailKeyword.value.trim() || undefined,
    );
    detailRows.value = page.content
      .map((raw) => {
        const productId = preserveApiIdForRequest(raw.id ?? raw.medicationDefId);
        if (!productId) {
          return null;
        }
        const categoryCode =
          typeof raw.categoryCode === 'string' ? raw.categoryCode.trim() : undefined;
        const name =
          (typeof raw.name === 'string' && raw.name.trim()) ||
          (typeof raw.merchandiseName === 'string' && raw.merchandiseName.trim()) ||
          productId;
        return {
          productId,
          name,
          busNo: typeof raw.busNo === 'string' ? raw.busNo : undefined,
          categoryCode,
          categoryName: categoryCode
            ? panel.rows.find((r) => r.categoryCode === categoryCode)?.categoryName || categoryCode
            : undefined,
          originalPrice: resolveCatalogPrice(raw),
          effectiveRate: resolveDetailEffectiveRate(panel, productId, categoryCode),
        } satisfies CatalogDetailRow;
      })
      .filter((row): row is CatalogDetailRow => !!row);
    detailTotal.value = page.totalCount;
  } catch {
    ElMessage.error('加载明细失败');
    detailRows.value = [];
    detailTotal.value = 0;
  } finally {
    detailLoading.value = false;
  }
}

/** 明细查询 */
function handleDetailSearch() {
  detailPageNo.value = 1;
  void loadDetailRows();
}

/** 明细分页 */
function handleDetailPageChange(page: number) {
  detailPageNo.value = page;
  void loadDetailRows();
}

/** 明细页大小 */
function handleDetailPageSizeChange(size: number) {
  detailPageSize.value = size;
  detailPageNo.value = 1;
  void loadDetailRows();
}

/** 加载全部单项目配置 */
async function loadProductOverrides() {
  overrideLoading.value = true;
  try {
    const tables = overrideTableFilter.value
      ? [overrideTableFilter.value]
      : panels.map((p) => p.productTable);
    const all: ProductOverrideRow[] = [];
    for (const productTable of tables) {
      const res = await pageChargeDiscountConfigsApi({
        pageNo: 1,
        pageSize: 500,
        productTable,
        keyword: overrideKeyword.value.trim() || undefined,
      });
      const parsed = parsePagedRowsFromApiData<ChargeDiscountConfigItem>(res);
      const tableName = panels.find((p) => p.productTable === productTable)?.title || productTable;
      for (const raw of parsed.content) {
        const productId = preserveApiIdForRequest(raw.productId);
        const id = preserveApiIdForRequest(raw.id);
        if (!productId || !id) {
          continue;
        }
        const rate =
          raw.discountRate === null || raw.discountRate === undefined
            ? null
            : Number(raw.discountRate);
        all.push({
          id,
          productTable,
          productTableName: raw.productTableName?.trim() || tableName,
          productId,
          productName: raw.productName?.trim() || productId,
          selectedLabel: raw.productName?.trim() || productId,
          discountRate: rate,
          zheText: toZheText(rate),
          originalPrice: null,
          statusEnum: String(raw.statusEnum ?? ''),
          statusEnumText: raw.statusEnumText?.trim() || '',
          remark: raw.remark?.trim() || undefined,
        });
      }
    }
    overrideTotal.value = all.length;
    const start = (overridePageNo.value - 1) * overridePageSize.value;
    overrideRows.value = all.slice(start, start + overridePageSize.value);
    await enrichOverrideOriginalPrices(overrideRows.value);
  } catch {
    ElMessage.error('加载单项目折扣配置失败');
    overrideRows.value = [];
    overrideTotal.value = 0;
  } finally {
    overrideLoading.value = false;
  }
}

/** 为本页单项补目录原价（按名称检索命中 id） */
async function enrichOverrideOriginalPrices(rows: ProductOverrideRow[]) {
  await Promise.all(
    rows.map(async (row) => {
      if (row.originalPrice !== null && row.originalPrice !== undefined) {
        return;
      }
      try {
        const page = await fetchCatalogPage(row.productTable, 1, 10, row.productName);
        const hit = page.content.find(
          (raw) => preserveApiIdForRequest(raw.id ?? raw.medicationDefId) === row.productId,
        );
        if (hit) {
          row.originalPrice = resolveCatalogPrice(hit);
        }
      } catch {
        // 原价补全失败不影响列表主流程
      }
    }),
  );
}

/** 单项目查询 */
function handleOverrideSearch() {
  if (hasEditingOverride.value) {
    ElMessage.warning('请先保存或取消当前行编辑');
    return;
  }
  overridePageNo.value = 1;
  void loadProductOverrides();
}

/** 清空筛选 */
function handleClearOverrideFilters() {
  if (hasEditingOverride.value) {
    ElMessage.warning('请先保存或取消当前行编辑');
    return;
  }
  overrideKeyword.value = '';
  overrideTableFilter.value = '';
  overridePageNo.value = 1;
  void loadProductOverrides();
}

/** 单项目分页 */
function handleOverridePageChange(page: number) {
  if (hasEditingOverride.value) {
    ElMessage.warning('请先保存或取消当前行编辑');
    return;
  }
  overridePageNo.value = page;
  void loadProductOverrides();
}

/** 单项目页大小 */
function handleOverridePageSizeChange(size: number) {
  if (hasEditingOverride.value) {
    ElMessage.warning('请先保存或取消当前行编辑');
    return;
  }
  overridePageSize.value = size;
  overridePageNo.value = 1;
  void loadProductOverrides();
}

/** 选品远程检索（含原价） */
async function searchProductPick(
  productTable: string,
  params: { pageNo: number; pageSize: number; keyword?: string },
) {
  const page = await fetchCatalogPage(
    productTable,
    params.pageNo,
    params.pageSize,
    params.keyword?.trim() || undefined,
  );
  return {
    content: page.content.map((raw) => {
      const id = preserveApiIdForRequest(raw.id ?? raw.medicationDefId) || '';
      const name =
        (typeof raw.name === 'string' && raw.name.trim()) ||
        (typeof raw.merchandiseName === 'string' && raw.merchandiseName.trim()) ||
        id;
      const price = resolveCatalogPrice(raw);
      return {
        id,
        name,
        busNo: typeof raw.busNo === 'string' ? raw.busNo : '',
        originalPrice: price,
        priceText: price === null || price === undefined ? '—' : formatMoneyYuanWithSymbol(price),
      };
    }),
    totalCount: page.totalCount,
  };
}

/** 选品关闭前写入行上原价与名称 */
function handleProductPickClose(row: ProductOverrideRow, item: Record<string, unknown>) {
  const productId = preserveApiIdForRequest(item.id);
  if (!productId) {
    return false;
  }
  row.productId = productId;
  row.productName = (typeof item.name === 'string' && item.name.trim()) || productId;
  row.selectedLabel = row.productName;
  const price = item.originalPrice;
  row.originalPrice = typeof price === 'number' && Number.isFinite(price) ? price : null;
  return true;
}

/** 选品变更 */
function handleProductIdChange(row: ProductOverrideRow, value: string | string[]) {
  const id = Array.isArray(value) ? value[0] : value;
  if (!id) {
    row.productId = '';
    row.productName = '';
    row.selectedLabel = '';
    row.originalPrice = null;
  }
}

/** 切换目录时清空已选项目 */
function handleOverrideTableChange(row: ProductOverrideRow) {
  const panel = panels.find((p) => p.productTable === row.productTable);
  row.productTableName = panel?.title || row.productTable;
  row.productId = '';
  row.productName = '';
  row.selectedLabel = '';
  row.originalPrice = null;
}

/** 新增一行（行编辑） */
function handleAddProductOverride() {
  if (hasEditingOverride.value) {
    ElMessage.warning('请先保存或取消当前行编辑');
    return;
  }
  const defaultTable = overrideTableFilter.value || panels[0]?.productTable || '';
  const panel = panels.find((p) => p.productTable === defaultTable);
  overrideRows.value.unshift({
    id: `new-${Date.now()}`,
    isNew: true,
    editing: true,
    productTable: defaultTable,
    productTableName: panel?.title || defaultTable,
    productId: '',
    productName: '',
    selectedLabel: '',
    discountRate: zheToDiscountRate(DEFAULT_ZHE),
    zheText: String(DEFAULT_ZHE),
    originalPrice: null,
    statusEnum: CHARGE_DISCOUNT_STATUS.ACTIVE,
    statusEnumText: '启用',
  });
}

/** 进入编辑 */
function handleEditOverride(row: ProductOverrideRow) {
  if (hasEditingOverride.value) {
    ElMessage.warning('请先保存或取消当前行编辑');
    return;
  }
  row.editing = true;
  row.zheText = toZheText(row.discountRate);
  row.selectedLabel = row.productName;
}

/** 取消行编辑 */
function handleCancelOverrideEdit(row: ProductOverrideRow) {
  if (row.isNew) {
    overrideRows.value = overrideRows.value.filter((r) => r.id !== row.id);
    return;
  }
  row.editing = false;
  row.zheText = toZheText(row.discountRate);
  row.selectedLabel = row.productName;
}

/** 保存行编辑 */
async function handleSaveOverrideRow(row: ProductOverrideRow) {
  if (row.saving) {
    return;
  }
  if (!row.productTable) {
    ElMessage.warning('请选择目录');
    return;
  }
  if (!row.productId) {
    ElMessage.warning('请选择项目');
    return;
  }
  let rate: number;
  try {
    rate = requireRateFromZheText(row.zheText);
  } catch (error: unknown) {
    const err = error as { message?: string };
    ElMessage.error(err.message || '折扣值不合法');
    return;
  }

  row.saving = true;
  try {
    if (row.isNew) {
      const created = await createChargeDiscountConfigApi({
        productTable: row.productTable,
        categoryCode: null,
        productId: row.productId,
        discountRate: rate,
      });
      const newId = preserveApiIdForRequest(created?.id);
      if (!newId) {
        throw new Error('创建成功但未返回 id');
      }
      row.id = newId;
      row.isNew = false;
    } else {
      await modifyChargeDiscountConfigApi({
        id: row.id,
        productTable: row.productTable,
        categoryCode: null,
        productId: row.productId,
        discountRate: rate,
      });
    }
    row.discountRate = rate;
    row.zheText = toZheText(rate);
    row.editing = false;
    row.statusEnum = CHARGE_DISCOUNT_STATUS.ACTIVE;
    ElMessage.success('已保存');
    await loadAllPanelRules();
  } catch (error: unknown) {
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err.msg || err.message || '保存失败');
  } finally {
    row.saving = false;
  }
}

/** 启停单项目 */
async function handleToggleOverride(row: ProductOverrideRow) {
  if (row.editing) {
    return;
  }
  const active = isChargeDiscountConfigActive(row.statusEnum);
  try {
    await changeChargeDiscountConfigStatusApi({
      id: row.id,
      statusEnum: active ? CHARGE_DISCOUNT_STATUS.INACTIVE : CHARGE_DISCOUNT_STATUS.ACTIVE,
    });
    ElMessage.success(active ? '已停用' : '已启用');
    await loadProductOverrides();
  } catch (error: unknown) {
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err.msg || err.message || '状态更新失败');
  }
}

/** 删除单项目 */
async function handleDeleteOverride(row: ProductOverrideRow) {
  if (row.isNew) {
    handleCancelOverrideEdit(row);
    return;
  }
  try {
    await ElMessageBox.confirm(`确认删除「${row.productName}」的单项折扣？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await deleteChargeDiscountConfigApi({ id: row.id });
    ElMessage.success('已删除');
    await loadProductOverrides();
    await loadAllPanelRules();
  } catch (error: unknown) {
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err.msg || err.message || '删除失败');
  }
}

/** 行折扣展示 */
function formatOverrideDiscount(row: ProductOverrideRow): string {
  const rate = row.editing ? zheToDiscountRate(row.zheText) : row.discountRate;
  if (rate === null) {
    return '—';
  }
  return formatDiscountRateDisplay(rate);
}

/** 行折后价 */
function formatOverrideDiscountedPrice(row: ProductOverrideRow): string {
  const rate = row.editing ? zheToDiscountRate(row.zheText) : row.discountRate;
  if (rate === null || row.originalPrice === null || row.originalPrice === undefined) {
    return '—';
  }
  return formatMoneyYuanWithSymbol(decimalMul(row.originalPrice, rate, 2));
}

/** 行患者节省 */
function formatOverrideSaving(row: ProductOverrideRow): string {
  const rate = row.editing ? zheToDiscountRate(row.zheText) : row.discountRate;
  if (rate === null || row.originalPrice === null || row.originalPrice === undefined) {
    return '—';
  }
  const discounted = decimalMul(row.originalPrice, rate, 2);
  const saving = decimalSub(row.originalPrice, discounted, 2);
  return saving > 0 ? formatMoneyYuanWithSymbol(saving) : '—';
}

/** 重置 */
async function handleReset() {
  await loadInitOptions();
  await loadAllPanelRules();
  overrideKeyword.value = '';
  overrideTableFilter.value = '';
  overridePageNo.value = 1;
  await loadProductOverrides();
  ElMessage.success('已重置为最新配置');
}

/** 目录主题 */
function resolveTableTheme(productTable: string): CatalogTheme {
  return panels.find((p) => p.productTable === productTable)?.theme ?? 'med';
}

/** 目录短字 */
function resolveTableShort(productTable: string): string {
  return panels.find((p) => p.productTable === productTable)?.short ?? '项';
}

onMounted(async () => {
  await loadInitOptions();
  await loadAllPanelRules();
  await loadProductOverrides();
});
</script>

<template>
  <Page auto-content-height>
    <div class="cdc">
      <header class="cdc-hero">
        <div class="cdc-hero__left">
          <h1 class="cdc-hero__title">收费项目折扣配置</h1>
          <p class="cdc-hero__crumb">基础配置 / 收费管理 / 折扣配置</p>
        </div>
        <div class="cdc-hero__actions">
          <ElButton @click="handleReset">重置</ElButton>
        </div>
      </header>

      <!-- 三卡大类 -->
      <div class="cdc-cards">
        <section
          v-for="panel in panels"
          :key="panel.key"
          v-loading="panel.loading || panel.saving"
          class="cdc-card"
          :class="`is-${panel.theme}`"
        >
          <header class="cdc-card__hd">
            <div class="cdc-card__hd-left">
              <span class="cdc-card__icon">{{ panel.short }}</span>
              <div>
                <div class="cdc-card__title">{{ panel.title }}</div>
                <div class="cdc-card__sub">{{ panelItemTotal(panel) }} 项</div>
              </div>
            </div>
            <div class="cdc-card__hd-right">
              <span class="cdc-card__badge">
                {{ enabledCount(panel) }}/{{ panel.rows.length }} 已启用
              </span>
              <ElButton link type="primary" @click="handleOpenDetail(panel)"> 明细查询 </ElButton>
            </div>
          </header>

          <div class="cdc-card__list">
            <div
              v-for="row in panel.rows"
              :key="row.categoryCode"
              class="cdc-rule"
              :class="{ 'is-off': !row.enabled }"
            >
              <ElCheckbox
                :model-value="row.enabled"
                :disabled="row.saving"
                @change="(val) => handleCategoryEnabledChange(panel, row, val)"
              />
              <span class="cdc-rule__name" :title="row.categoryName">
                {{ row.categoryName }}
              </span>
              <div class="cdc-rule__ops">
                <ElInput
                  v-model="row.zheText"
                  :disabled="!row.enabled || row.saving"
                  class="cdc-zhe-input"
                  @blur="() => handleCategoryZheBlur(panel, row)"
                  @keyup.enter="($event.target as HTMLInputElement)?.blur()"
                />
                <span class="cdc-rule__unit">折</span>
                <span class="cdc-rule__cnt">{{ row.itemCount }}项</span>
              </div>
            </div>
            <div v-if="!panel.rows.length && !panel.loading" class="cdc-empty">暂无分类</div>
          </div>

          <footer class="cdc-card__ft">
            <span class="cdc-card__ft-label">统一设置</span>
            <ElInput v-model="panel.bulkZheText" :disabled="panel.saving" class="cdc-zhe-input" />
            <span class="cdc-rule__unit">折</span>
            <ElButton
              type="primary"
              :loading="panel.saving"
              :disabled="!enabledCount(panel)"
              @click="handleApplyBulk(panel)"
            >
              应用
            </ElButton>
          </footer>
        </section>
      </div>

      <!-- 单项目折扣配置 -->
      <section class="cdc-detail">
        <header class="cdc-detail__hd">
          <div>
            <h3 class="cdc-detail__title">单项目折扣配置</h3>
            <p class="cdc-detail__desc">
              仅展示单独指定到项目的折扣；优先于大类规则。支持新增、启停与删除。
            </p>
          </div>
        </header>

        <div class="cdc-filter">
          <ElButton type="primary" @click="handleAddProductOverride"> 新增单项 </ElButton>
          <ElSelect
            v-model="overrideTableFilter"
            clearable
            placeholder="全部目录"
            class="cdc-filter__sel"
            @change="handleOverrideSearch"
          >
            <ElOption
              v-for="opt in overrideTableOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
          <ElInput
            v-model="overrideKeyword"
            clearable
            placeholder="搜索项目名称"
            class="cdc-filter__kw"
            @keyup.enter="handleOverrideSearch"
            @clear="handleOverrideSearch"
          />
          <ElButton type="primary" @click="handleOverrideSearch">查询</ElButton>
          <ElButton @click="handleClearOverrideFilters">清空筛选</ElButton>
          <span class="cdc-filter__sum">共 {{ overrideTotal }} 条</span>
        </div>

        <div class="cdc-detail__body">
          <ElTable
            v-loading="overrideLoading"
            :data="overrideRows"
            row-key="id"
            height="100%"
            class="cdc-grid"
          >
            <ElTableColumn label="目录" width="100" align="center">
              <template #default="{ row }">
                <ElSelect
                  v-if="row.editing"
                  v-model="row.productTable"
                  placeholder="目录"
                  class="cdc-cell-sel"
                  @change="() => handleOverrideTableChange(row)"
                >
                  <ElOption
                    v-for="opt in overrideTableOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </ElSelect>
                <span v-else class="cdc-cat" :class="`is-${resolveTableTheme(row.productTable)}`">
                  <i class="cdc-cat__mark">
                    {{ resolveTableShort(row.productTable) }}
                  </i>
                  {{ row.productTableName }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="项目" width="220" show-overflow-tooltip>
              <template #default="{ row }">
                <SelectTable
                  v-if="row.editing"
                  :model-value="row.productId"
                  :remote-method="(params) => searchProductPick(row.productTable, params)"
                  :columns="productPickColumns"
                  value-key="id"
                  label-key="name"
                  :selected-label="row.selectedLabel"
                  placeholder="请选择项目"
                  filterable
                  clearable
                  panel-width="720"
                  class="w-full"
                  :before-close="(item) => handleProductPickClose(row, item)"
                  @update:model-value="(v) => handleProductIdChange(row, v)"
                />
                <span v-else class="cdc-name">{{ row.productName }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="项目原价" width="110" align="right">
              <template #default="{ row }">
                <template v-if="row.originalPrice !== null && row.originalPrice !== undefined">
                  {{ formatMoneyYuanWithSymbol(row.originalPrice) }}
                </template>
                <span v-else class="cdc-dash">—</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="折扣" width="120" align="center">
              <template #default="{ row }">
                <div v-if="row.editing" class="cdc-inline-zhe">
                  <ElInput v-model="row.zheText" class="cdc-zhe-input" />
                  <span class="cdc-rule__unit">折</span>
                </div>
                <span v-else class="cdc-hi">{{ formatOverrideDiscount(row) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="折后价" width="110" align="right">
              <template #default="{ row }">
                <span :class="formatOverrideDiscountedPrice(row) === '—' ? 'cdc-dash' : 'cdc-hi'">
                  {{ formatOverrideDiscountedPrice(row) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="患者节省" width="110" align="right">
              <template #default="{ row }">
                <span :class="formatOverrideSaving(row) === '—' ? 'cdc-dash' : 'cdc-ok'">
                  {{ formatOverrideSaving(row) }}
                </span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="状态" width="88" align="center">
              <template #default="{ row }">
                <ElTag
                  v-if="!row.isNew"
                  size="small"
                  effect="plain"
                  :type="isChargeDiscountConfigActive(row.statusEnum) ? 'success' : 'info'"
                >
                  {{ isChargeDiscountConfigActive(row.statusEnum) ? '启用' : '停用' }}
                </ElTag>
                <span v-else class="cdc-dash">—</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" min-width="180" align="center">
              <template #default="{ row }">
                <template v-if="row.editing">
                  <ElButton
                    link
                    type="primary"
                    :loading="row.saving"
                    @click="handleSaveOverrideRow(row)"
                  >
                    保存
                  </ElButton>
                  <ElButton link @click="handleCancelOverrideEdit(row)"> 取消 </ElButton>
                </template>
                <template v-else>
                  <ElButton link type="primary" @click="handleEditOverride(row)"> 编辑 </ElButton>
                  <ElButton link type="primary" @click="handleToggleOverride(row)">
                    {{ isChargeDiscountConfigActive(row.statusEnum) ? '停用' : '启用' }}
                  </ElButton>
                  <ElButton link type="danger" @click="handleDeleteOverride(row)"> 删除 </ElButton>
                </template>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>

        <div class="cdc-detail__pager">
          <ElPagination
            v-model:current-page="overridePageNo"
            v-model:page-size="overridePageSize"
            :total="overrideTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            background
            @current-change="handleOverridePageChange"
            @size-change="handleOverridePageSizeChange"
          />
        </div>
      </section>
    </div>

    <ElDialog
      v-model="detailVisible"
      :title="`${detailPanel?.title || ''}明细查询`"
      width="960px"
      destroy-on-close
      append-to-body
    >
      <div class="cdc-dlg-filter">
        <ElInput
          v-model="detailKeyword"
          clearable
          placeholder="搜索项目名称 / 编码"
          class="cdc-filter__kw"
          @keyup.enter="handleDetailSearch"
          @clear="handleDetailSearch"
        />
        <ElButton type="primary" @click="handleDetailSearch">查询</ElButton>
      </div>
      <ElTable
        v-loading="detailLoading"
        :data="detailRows"
        row-key="productId"
        max-height="420"
        class="cdc-grid"
      >
        <ElTableColumn label="分类" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.categoryName || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="项目名称" min-width="180">
          <template #default="{ row }">
            <div class="cdc-name">{{ row.name }}</div>
            <div v-if="row.busNo" class="cdc-name__sub">{{ row.busNo }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="原价" width="100" align="right">
          <template #default="{ row }">
            <template v-if="row.originalPrice !== null && row.originalPrice !== undefined">
              {{ formatMoneyYuanWithSymbol(row.originalPrice) }}
            </template>
            <span v-else class="cdc-dash">—</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="折扣" width="88" align="center">
          <template #default="{ row }">
            <span :class="formatDetailDiscount(row) === '—' ? 'cdc-dash' : 'cdc-hi'">
              {{ formatDetailDiscount(row) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="折后价" width="100" align="right">
          <template #default="{ row }">
            <span :class="formatDetailDiscountedPrice(row) === '—' ? 'cdc-dash' : 'cdc-hi'">
              {{ formatDetailDiscountedPrice(row) }}
            </span>
          </template>
        </ElTableColumn>
      </ElTable>
      <div class="cdc-detail__pager">
        <ElPagination
          v-model:current-page="detailPageNo"
          v-model:page-size="detailPageSize"
          :total="detailTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="handleDetailPageChange"
          @size-change="handleDetailPageSizeChange"
        />
      </div>
    </ElDialog>
  </Page>
</template>

<style scoped>
.cdc {
  --cdc-med: #3b82f6;
  --cdc-act: #f59e0b;
  --cdc-dev: #10b981;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  padding-bottom: 4px;
}

.cdc-hero {
  display: flex;
  flex-shrink: 0;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 2px;
}

.cdc-hero__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--el-text-color-primary);
}

.cdc-hero__crumb {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.cdc-hero__actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.cdc-cards {
  display: grid;
  flex-shrink: 0;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.cdc-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  border-left-width: 3px;
  border-left-color: var(--cdc-accent);
}

.cdc-card.is-med {
  --cdc-accent: var(--cdc-med);
}

.cdc-card.is-act {
  --cdc-accent: var(--cdc-act);
}

.cdc-card.is-dev {
  --cdc-accent: var(--cdc-dev);
}

.cdc-card__hd {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.cdc-card__hd-left {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.cdc-card__hd-right {
  display: flex;
  flex-shrink: 0;
  gap: 10px;
  align-items: center;
}

.cdc-card__icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  background: var(--cdc-accent);
  border-radius: 6px;
}

.cdc-card__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.cdc-card__sub {
  margin-top: 2px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.cdc-card__badge {
  padding: 2px 8px;
  font-size: 12px;
  line-height: 20px;
  color: var(--cdc-accent);
  background: color-mix(in srgb, var(--cdc-accent) 12%, #fff);
  border-radius: 999px;
}

.cdc-card__list {
  max-height: 220px;
  padding: 0;
  overflow: auto;
}

.cdc-rule {
  display: flex;
  gap: 10px;
  align-items: center;
  min-height: 40px;
  padding: 6px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.cdc-rule:first-child {
  border-top: 1px solid var(--el-border-color-lighter);
}

.cdc-rule:hover {
  background: var(--el-fill-color-lighter);
}

.cdc-rule.is-off .cdc-rule__name,
.cdc-rule.is-off .cdc-rule__unit,
.cdc-rule.is-off .cdc-rule__cnt {
  color: var(--el-text-color-placeholder);
}

.cdc-rule__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  white-space: nowrap;
  color: var(--el-text-color-primary);
}

.cdc-rule__ops {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
}

.cdc-rule__unit {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.cdc-rule__cnt {
  min-width: 48px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.cdc-zhe-input {
  width: 72px;
}

.cdc-inline-zhe {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  justify-content: center;
}

.cdc-cell-sel {
  width: 100%;
}

.cdc-ok {
  font-weight: 600;
  color: var(--el-color-success);
}

.cdc-empty {
  padding: 20px 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.cdc-card__ft {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  margin-top: auto;
  background: var(--el-fill-color-light);
  border-top: 1px solid var(--el-border-color);
}

.cdc-card__ft-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.cdc-detail {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.cdc-detail__hd {
  flex-shrink: 0;
  padding: 12px 16px 0;
}

.cdc-detail__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.cdc-detail__desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.cdc-filter {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 12px 16px;
}

.cdc-filter__kw {
  width: 200px;
}

.cdc-filter__sel {
  width: 140px;
}

.cdc-filter__sum {
  margin-left: auto;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.cdc-detail__body {
  flex: 1;
  min-height: 140px;
  padding: 0 8px;
}

.cdc-detail__pager {
  display: flex;
  flex-shrink: 0;
  justify-content: flex-end;
  padding: 8px 12px 10px;
}

.cdc-dlg-filter {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.cdc-cat {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.cdc-cat__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 11px;
  font-style: normal;
  font-weight: 600;
  color: #fff;
  background: var(--cdc-med);
  border-radius: 3px;
}

.cdc-cat.is-act .cdc-cat__mark {
  background: var(--cdc-act);
}

.cdc-cat.is-dev .cdc-cat__mark {
  background: var(--cdc-dev);
}

.cdc-name {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.cdc-name__sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.cdc-hi {
  font-weight: 600;
  color: var(--el-color-primary);
}

.cdc-dash {
  color: var(--el-text-color-placeholder);
}

:deep(.cdc-grid .el-table__header th) {
  font-weight: 500;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light) !important;
}

:deep(.cdc-grid::before),
:deep(.cdc-grid .el-table__inner-wrapper::before) {
  display: none;
}

@media (max-width: 1200px) {
  .cdc-cards {
    grid-template-columns: 1fr;
  }
}
</style>
