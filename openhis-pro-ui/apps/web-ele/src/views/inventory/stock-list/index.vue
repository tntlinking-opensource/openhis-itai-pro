<!--
  药库库存明细：现库 T2+T3（停供/恢复）；选备份单号时只读查静表快照。
  状态分层 — 业务：筛选（含 backupBusNo）、rows、本页合计；视图：分页。
-->
<script setup lang="ts">
import type {
  InventoryDetailListRowDto,
  OptionDto,
  SumInventoryDetailCurrentRequest,
} from '#/api/inventory/warehouse/inventory-detail';
import type { ApiOptionItem } from '#/types/api-response';
import type { ReportPrintColumnDef, ReportPrintSummaryItem } from '#/utils/print/print-report-list';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@zxplat/common-ui';
import { formatDate } from '@zxplat/utils';

import { Download, Printer, Refresh, Search, Setting } from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTableColumn,
} from 'element-plus';

import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { buildExcelExportColumns } from '#/utils/export/build-excel-export-columns';

import {
  exportInventoryDetailBackupApi,
  exportInventoryDetailCurrentApi,
  fmtPackageUnitPrice,
  INVENTORY_DETAIL_ITEM_TABLE_DEVICE,
  INVENTORY_DETAIL_ITEM_TABLE_MEDICATION,
  listInventoryDetailBackupBusNoApi,
  loadInventoryDetailFormInitApi,
  pageInventoryDetailBackupApi,
  pageInventoryDetailCurrentApi,
  resumeInventoryItemsApi,
  retireInventoryItemsApi,
  sumInventoryDetailBackupApi,
  sumInventoryDetailCurrentApi,
  INVENTORY_WARNING_MODE_EXPIRE,
  INVENTORY_WARNING_MODE_LOW_STOCK,
  type InventoryWarningMode,
} from '#/api/inventory/warehouse/inventory-detail';
import TraceCodeListDialog from '#/components/trace/trace-code-list-dialog.vue';
import { PublicationStatusCode } from '#/constants/domain/publication-status';
import { useRequest } from '#/hooks/use-request';
import { formatPurchaseMoneyDisplay, sumMoneyThenCalc } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  getReportPrintColumnPreference,
  normalizeReportPrintScope,
  printReportList,
} from '#/utils/print/print-report-list';
import {
  REPORT_QUERY_CONTROL,
  REPORT_TABLE_NUM_CELL,
  ReportElTable,
  ReportListPageLayout,
  ReportListToolbar,
  ReportPageFooter,
  ReportPrintFieldDialog,
  ReportQueryBar,
  ReportTableScroll,
} from '#/views/report/shared';

defineOptions({ name: 'InventoryWarehouseInventoryDetailPage' });

/** inventory_range 术语概念码（与后端 InventoryWarehouseDetailConstants 对齐）。 */
const INVENTORY_RANGE_ZERO_CODE = '2';
const INVENTORY_RANGE_GT_ZERO_CODE = '3';
const DEFAULT_EXPIRE_WARNING_DAYS = 180;
const PAGE_TITLE = '药库库存明细';
/** 打印字段偏好键（本机记住）。 */
const STOCK_LIST_PRINT_REPORT_CODE = 'inventory-stock-list';
/** 跨页打印每次分页条数；直调分页接口，不改列表 tableData。 */
const PRINT_ALL_PAGE_SIZE = 1000;
/** 跨页打印行数上限，与现库汇总/导出 cap 对齐。 */
const PRINT_ALL_MAX_ROWS = 30000;

/** 关键字占位：现库与备份均支持批号模糊。 */
const KEYWORD_PLACEHOLDER = '名称、编码、商品名、拼音、五笔、批号';

/** 项目类型下拉：value 为 item_table 物理表名。 */
const itemTableSelectOptions = [
  { label: '药品', value: INVENTORY_DETAIL_ITEM_TABLE_MEDICATION },
  { label: '耗材', value: INVENTORY_DETAIL_ITEM_TABLE_DEVICE },
];

/** 库存明细可打印列目录（与表格 prop 对齐；首次默认全选）。 */
const STOCK_LIST_PRINT_COLUMNS: ReportPrintColumnDef[] = [
  { field: 'productBusNo', title: '编码', align: 'center', widthWeight: 1.1 },
  { field: 'name', title: '项目名称', align: 'center', widthWeight: 1.6 },
  { field: 'merchandiseName', title: '商品名称', align: 'center', widthWeight: 1.4 },
  { field: 'totalVolume', title: '规格', align: 'center', widthWeight: 1.1 },
  { field: 'packageSplitRatioText', title: '包装拆零比例', align: 'center', widthWeight: 1.1 },
  { field: 'categoryLabel', title: '项目类别', align: 'center', widthWeight: 1 },
  { field: 'lotNumber', title: '批号', align: 'center', widthWeight: 1 },
  { field: 'packageStockText', title: '库存(包装)', align: 'right', widthWeight: 1 },
  { field: 'splitStockText', title: '库存(最小)', align: 'right', widthWeight: 1 },
  { field: 'lotCostPrice', title: '进价', align: 'right', widthWeight: 1 },
  { field: 'lotSalePrice', title: '售价', align: 'right', widthWeight: 1 },
  { field: 'lineCostTotal', title: '进价总额', align: 'right', widthWeight: 1.1 },
  { field: 'linePriceTotal', title: '售价总额', align: 'right', widthWeight: 1.1 },
  { field: 'manufacturer', title: '厂家', align: 'center', widthWeight: 1.2 },
  { field: 'expirationDate', title: '效期', align: 'center', widthWeight: 1 },
  { field: 'productionDate', title: '生产日期', align: 'center', widthWeight: 1 },
  { field: 'remainingDays', title: '剩余天数', align: 'center', widthWeight: 0.9 },
  { field: 'inventoryStatusLabel', title: '供应状态', align: 'center', widthWeight: 0.9 },
  { field: 'warehouseName', title: '库房', align: 'center', widthWeight: 1 },
  { field: 'supplierName', title: '供应商', align: 'center', widthWeight: 1.3 },
  { field: 'doseFormLabel', title: '剂型', align: 'center', widthWeight: 0.8 },
];

/** 筛选 + 分页；有 backupBusNo 时走备份读，否则走现库。 */
interface InventoryDetailFormState {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  /** 项目类型：药品/耗材定义表名；undefined 表示不限 */
  itemTable?: string;
  backupBusNo?: string;
  warehouseIds?: string[];
  supplierIds?: string[];
  medicationCategoryCodes?: string[];
  deviceCategoryCodes?: string[];
  inventoryStatusEnums?: string[];
  chrgitmLvList?: string[];
  /** 重点药类型 code；undefined 表示不限 */
  keyDrugType?: string;
  medicinePropertyCode?: string;
  zeroStockFilter?: typeof INVENTORY_RANGE_ZERO_CODE | typeof INVENTORY_RANGE_GT_ZERO_CODE;
  expireWithinDays?: string;
  warningMode?: InventoryWarningMode;
}

const tableData = ref<InventoryDetailListRowDto[]>([]);
const total = ref(0);
const sumLineCostTotal = ref('');
const sumLinePriceTotal = ref('');
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);
const printFieldDialogRef = ref<InstanceType<typeof ReportPrintFieldDialog> | null>(null);
const printSubmitting = ref(false);

const chrgitmLvOptions = ref<ApiOptionItem[]>([]);
const inventoryStatusOptions = ref<ApiOptionItem[]>([]);
const medicationCategoryOptions = ref<ApiOptionItem[]>([]);
const deviceCategoryOptions = ref<ApiOptionItem[]>([]);
const inventoryRangeOptions = ref<ApiOptionItem[]>([]);
const warehouseOptions = ref<ApiOptionItem[]>([]);
const supplierOptions = ref<ApiOptionItem[]>([]);
const backupBusNoOptions = ref<ApiOptionItem[]>([]);
/** 系统设置：效期预警天数（剩余天数 ≤ 该值视为临期）。 */
const expireWarningDays = ref(DEFAULT_EXPIRE_WARNING_DAYS);
/** 系统设置：库存预警包装单位阈值。 */
const minPackageQuantityWarning = ref(30);

const queryParams = reactive<InventoryDetailFormState>({
  pageNo: 1,
  pageSize: 15,
  zeroStockFilter: INVENTORY_RANGE_GT_ZERO_CODE,
});

const route = useRoute();
const router = useRouter();

const footerSummaryItems = computed(() => [
  { label: '本页进价合计', value: sumLineCostTotal.value },
  { label: '本页售价合计', value: sumLinePriceTotal.value, primary: true },
]);

/** 工作台预警跳转后，查询区下方展示的筛选口径说明。 */
const warningFilterTip = computed(() => {
  if (queryParams.warningMode === INVENTORY_WARNING_MODE_EXPIRE) {
    const days = queryParams.expireWithinDays?.trim() || String(expireWarningDays.value);
    return `当前为效期预警：剩余效期 ≤ ${days} 天`;
  }
  if (queryParams.warningMode === INVENTORY_WARNING_MODE_LOW_STOCK) {
    return `当前为库存预警：全院汇总包装 < ${minPackageQuantityWarning.value}`;
  }
  return '';
});

/** 是否按备份单号查静表（有单号即备份读；未选则现库）。 */
const isBackupQuery = computed(() => Boolean(queryParams.backupBusNo?.trim()));

const tableLoading = computed(
  () =>
    initLoading.value ||
    busNoLoading.value ||
    pageLoading.value ||
    backupPageLoading.value ||
    exportLoading.value ||
    backupExportLoading.value ||
    retireLoading.value ||
    resumeLoading.value ||
    printSubmitting.value,
);

/** 解析路由中的预警模式参数。 */
function parseRouteWarningMode(raw: unknown): InventoryWarningMode | undefined {
  const value = Array.isArray(raw) ? raw[0] : raw;
  const normalized = String(value ?? '').trim();
  if (
    normalized === INVENTORY_WARNING_MODE_EXPIRE ||
    normalized === INVENTORY_WARNING_MODE_LOW_STOCK
  ) {
    return normalized;
  }
  return undefined;
}

/** 解析路由中的近效期天数（仅正整数字符串）。 */
function parseRouteExpireWithinDays(raw: unknown): string | undefined {
  const value = Array.isArray(raw) ? raw[0] : raw;
  const normalized = String(value ?? '').trim();
  if (normalized && /^\d+$/.test(normalized)) {
    return normalized;
  }
  return undefined;
}

/**
 * 从路由 query 写回预警相关查询条件。
 * 效期：回填「近效期(天)」；库存预警：清空近效期以免误导。
 */
function applyRouteWarningMode() {
  const mode = parseRouteWarningMode(route.query.warningMode);
  queryParams.warningMode = mode;

  if (mode === INVENTORY_WARNING_MODE_EXPIRE) {
    const fromRoute = parseRouteExpireWithinDays(route.query.expireWithinDays);
    if (fromRoute) {
      queryParams.expireWithinDays = fromRoute;
    } else if (expireWarningDays.value > 0) {
      // 仅有 warningMode 时，用系统效期预警天数回填查询区
      queryParams.expireWithinDays = String(expireWarningDays.value);
    }
    return;
  }

  if (mode === INVENTORY_WARNING_MODE_LOW_STOCK) {
    queryParams.expireWithinDays = undefined;
  }
}

/** OptionDto → 表格筛选用 ApiOptionItem（value 一律 string）。 */
function optionDtosToSelectItems(list?: OptionDto[]): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}

/** 下拉选项转为 ElSelect 可用结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 单价 + 单位名（元/单位）。 */
function fmtUnitPriceCell(price: unknown, unitName?: string): string {
  return fmtPackageUnitPrice(price as string | number | null | undefined, unitName);
}

/** 售价总额等金额展示：空则空白，否则本地化数字（两位起）。 */
function fmtAmt(v: unknown): string {
  if (v === null || v === undefined || v === '') return '';
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) return '';
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
}

/** 进价总额展示：中药可至三位（无分类码时最多三位去尾零）。 */
function fmtPurchaseAmt(v: unknown): string {
  if (v === null || v === undefined || v === '') return '';
  return formatPurchaseMoneyDisplay(v);
}

/** 用当前页行金额加总页脚合计（不再调全量汇总 IF-03/05）。 */
function applyPageFooterSum(rows: InventoryDetailListRowDto[]) {
  // 收集本页行进价/售价总额，空值按 0
  const costValues: Array<string | number | null | undefined> = [];
  const priceValues: Array<string | number | null | undefined> = [];
  for (const row of rows) {
    costValues.push(row.lineCostTotal);
    priceValues.push(row.linePriceTotal);
  }
  sumLineCostTotal.value = fmtPurchaseAmt(sumMoneyThenCalc(costValues));
  sumLinePriceTotal.value = fmtAmt(sumMoneyThenCalc(priceValues));
}

/** 日期展示：去掉时分秒，仅保留 YYYY-MM-DD。 */
function fmtDateOnly(v: unknown): string {
  if (v === null || v === undefined || v === '') return '';
  const formatted = formatDate(v as Date | number | string, 'YYYY-MM-DD');
  return formatted.length > 10 ? formatted.slice(0, 10) : formatted;
}

type InventoryDetailExpiryStatus = 'expired' | 'nearExpiry' | '';

type InventoryDetailExpiryRowClass =
  | 'inventory-detail-row--danger'
  | 'inventory-detail-row--warning'
  | '';

/** 解析行效期状态：过期 / 临期 / 正常。 */
function resolveInventoryDetailExpiryStatus(
  row: InventoryDetailListRowDto,
): InventoryDetailExpiryStatus {
  const remaining = row.remainingDays;
  if (remaining === null || remaining === undefined) {
    return '';
  }
  if (remaining < 0) {
    return 'expired';
  }
  if (remaining <= expireWarningDays.value) {
    return 'nearExpiry';
  }
  return '';
}

/** 项目名称后展示的效期标签文案。 */
function resolveInventoryDetailExpiryLabel(row: InventoryDetailListRowDto): string {
  const status = resolveInventoryDetailExpiryStatus(row);
  if (status === 'expired') {
    return '过期';
  }
  if (status === 'nearExpiry') {
    return '临期';
  }
  return '';
}

/** 按剩余效期天数着色：过期 danger，临期（≤预警天数）warning。 */
function resolveInventoryDetailRowClassName({
  row,
}: {
  row: InventoryDetailListRowDto;
}): InventoryDetailExpiryRowClass {
  const status = resolveInventoryDetailExpiryStatus(row);
  if (status === 'expired') {
    return 'inventory-detail-row--danger';
  }
  if (status === 'nearExpiry') {
    return 'inventory-detail-row--warning';
  }
  return '';
}

/** 非空数组才下发，避免传空数组与后端默认不一致。 */
function emptyToUndef(arr?: string[]): string[] | undefined {
  return arr && arr.length > 0 ? arr : undefined;
}

/** 多选业务 Long ID 规范化后下发（禁止 Number 精度丢失）。 */
function preserveApiIdListForRequest(arr?: string[]): string[] | undefined {
  const raw = emptyToUndef(arr);
  if (!raw) {
    return undefined;
  }
  const mapped = raw
    .map((id) => preserveApiIdForRequest(id))
    .filter((id): id is string => Boolean(id));
  return mapped.length > 0 ? mapped : undefined;
}

/** 医保等级多选 string → List<Integer>。 */
function toChrgitmLvInts(codes?: string[]): number[] | undefined {
  if (!codes || codes.length === 0) return undefined;
  const nums = codes.map((c) => Number.parseInt(String(c), 10)).filter((n) => !Number.isNaN(n));
  return nums.length > 0 ? nums : undefined;
}

/** inventory_range 术语码归一化。 */
function normalizeInventoryRangeCode(code?: string | string[]): string | undefined {
  const raw = Array.isArray(code) ? code[0] : code;
  const normalized = String(raw ?? '').trim();
  return normalized || undefined;
}

/** inventory_range 术语码 → zeroStockOnly。 */
function mapInventoryRangeToZeroStockOnly(code?: string | string[]): boolean {
  const normalized = normalizeInventoryRangeCode(code);
  if (normalized === INVENTORY_RANGE_ZERO_CODE) return true;
  return false;
}

/** 组装现库分页请求体。 */
function buildPageRequest() {
  const z = mapInventoryRangeToZeroStockOnly(queryParams.zeroStockFilter);
  const ed = queryParams.expireWithinDays?.trim();
  const expireWithinDays = ed && /^\d+$/.test(ed) ? Number.parseInt(ed, 10) : undefined;
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    keyword: queryParams.keyword?.trim() || undefined,
    itemTable: queryParams.itemTable?.trim() || undefined,
    warehouseIds: preserveApiIdListForRequest(queryParams.warehouseIds),
    supplierIds: preserveApiIdListForRequest(queryParams.supplierIds),
    medicationCategoryCodes: emptyToUndef(queryParams.medicationCategoryCodes),
    deviceCategoryCodes: emptyToUndef(queryParams.deviceCategoryCodes),
    inventoryStatusEnums: emptyToUndef(queryParams.inventoryStatusEnums),
    chrgitmLvList: toChrgitmLvInts(queryParams.chrgitmLvList),
    keyDrugType: queryParams.keyDrugType,
    medicinePropertyCode: queryParams.medicinePropertyCode,
    zeroStockOnly: z,
    expireWithinDays,
    warningMode: queryParams.warningMode,
  };
}

/** 组装现库汇总/导出同筛选请求体（无分页键）。 */
function buildSumExportFilter(): SumInventoryDetailCurrentRequest {
  const p = buildPageRequest();
  return {
    keyword: p.keyword,
    itemTable: p.itemTable,
    warehouseIds: p.warehouseIds,
    supplierIds: p.supplierIds,
    medicationCategoryCodes: p.medicationCategoryCodes,
    deviceCategoryCodes: p.deviceCategoryCodes,
    chrgitmLvList: p.chrgitmLvList,
    keyDrugType: p.keyDrugType,
    medicinePropertyCode: p.medicinePropertyCode,
    zeroStockOnly: p.zeroStockOnly,
    expireWithinDays: p.expireWithinDays,
    inventoryStatusEnums: p.inventoryStatusEnums,
    warningMode: p.warningMode,
  };
}

/** 组装备份分页请求体。 */
function buildBackupPageRequest() {
  const base = buildPageRequest();
  const busNo = queryParams.backupBusNo?.trim();
  return {
    ...base,
    backupBusNo: busNo || undefined,
  };
}

/** 组装备份汇总/导出同筛选请求体。 */
function buildBackupSumExportFilter() {
  const base = buildSumExportFilter();
  const busNo = queryParams.backupBusNo?.trim();
  return {
    ...base,
    backupBusNo: busNo || undefined,
  };
}

const { run: runInit, loading: initLoading } = useRequest(loadInventoryDetailFormInitApi, {
  manual: true,
  onSuccess: (data) => {
    const d = data ?? {};
    chrgitmLvOptions.value = optionDtosToSelectItems(d.chrgitmLvOptions);
    inventoryStatusOptions.value = optionDtosToSelectItems(d.inventoryStatusOptions);
    medicationCategoryOptions.value = optionDtosToSelectItems(d.medicationCategoryOptions);
    deviceCategoryOptions.value = optionDtosToSelectItems(d.deviceCategoryOptions);
    inventoryRangeOptions.value = optionDtosToSelectItems(d.inventoryRangeOptions);
    warehouseOptions.value = optionDtosToSelectItems(d.warehouseOptions);
    supplierOptions.value = optionDtosToSelectItems(d.supplierOptions);
    const warningDays = Number(d.expireWarningDays);
    expireWarningDays.value =
      Number.isFinite(warningDays) && warningDays > 0 ? warningDays : DEFAULT_EXPIRE_WARNING_DAYS;
    const minPackageQty = Number(d.minPackageQuantityWarning);
    minPackageQuantityWarning.value =
      Number.isFinite(minPackageQty) && minPackageQty > 0 ? minPackageQty : 30;
    // init 完成后：效期预警且查询区尚未回填近效期时，用系统天数补齐
    if (
      queryParams.warningMode === INVENTORY_WARNING_MODE_EXPIRE &&
      !queryParams.expireWithinDays?.trim()
    ) {
      queryParams.expireWithinDays = String(expireWarningDays.value);
    }
  },
});

const { run: runListBackupBusNo, loading: busNoLoading } = useRequest(
  listInventoryDetailBackupBusNoApi,
  {
    manual: true,
    onSuccess: (data) => {
      backupBusNoOptions.value = (data?.backupBusNoList ?? []).map((busNo) => ({
        label: busNo,
        value: busNo,
      }));
    },
  },
);

const { run: runPage, loading: pageLoading } = useRequest(pageInventoryDetailCurrentApi, {
  manual: true,
  onSuccess: (data) => {
    const rows = data?.rows ?? [];
    tableData.value = rows;
    total.value = Number(data?.totalElements ?? 0);
    applyPageFooterSum(rows);
  },
});

const { run: runBackupPage, loading: backupPageLoading } = useRequest(
  pageInventoryDetailBackupApi,
  {
    manual: true,
    onSuccess: (data) => {
      const rows = data?.rows ?? [];
      tableData.value = rows;
      total.value = Number(data?.totalElements ?? 0);
      applyPageFooterSum(rows);
    },
  },
);

const { run: runExport, loading: exportLoading } = useRequest(exportInventoryDetailCurrentApi, {
  manual: true,
  showErrorMessage: true,
});

const { run: runBackupExport, loading: backupExportLoading } = useRequest(
  exportInventoryDetailBackupApi,
  {
    manual: true,
    showErrorMessage: true,
  },
);

const { run: runSum } = useRequest(sumInventoryDetailCurrentApi, {
  manual: true,
  showErrorMessage: true,
});

const { run: runBackupSum } = useRequest(sumInventoryDetailBackupApi, {
  manual: true,
  showErrorMessage: true,
});

const { run: runRetire, loading: retireLoading } = useRequest(retireInventoryItemsApi, {
  manual: true,
  onSuccess: (data) => {
    ElMessage.success(`停供成功，更新 ${data?.affectedRows ?? 0} 条`);
    void getList();
  },
});

const { run: runResume, loading: resumeLoading } = useRequest(resumeInventoryItemsApi, {
  manual: true,
  onSuccess: (data) => {
    ElMessage.success(`已恢复供应，更新 ${data?.affectedRows ?? 0} 条`);
    void getList();
  },
});

const traceCodeListDialogRef = ref<InstanceType<typeof TraceCodeListDialog> | null>(null);

/** 仅拉分页；页脚合计由本页行金额加总（有备份单号走静表，否则现库）。 */
async function getList() {
  if (isBackupQuery.value) {
    await runBackupPage(buildBackupPageRequest());
  } else {
    await runPage(buildPageRequest());
  }
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 顶部查询：回到第一页。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 退出预警筛选，恢复普通列表（保留其它手工筛选项）。 */
function clearWarningFilter() {
  queryParams.warningMode = undefined;
  queryParams.expireWithinDays = undefined;
  queryParams.pageNo = 1;
  void router.replace({ path: route.path, query: {} });
  void getList();
}

/** 清空筛选并回到第一页。 */
function handleReset() {
  queryParams.keyword = undefined;
  queryParams.itemTable = undefined;
  queryParams.backupBusNo = undefined;
  queryParams.warehouseIds = undefined;
  queryParams.supplierIds = undefined;
  queryParams.expireWithinDays = undefined;
  queryParams.medicationCategoryCodes = undefined;
  queryParams.deviceCategoryCodes = undefined;
  queryParams.inventoryStatusEnums = undefined;
  queryParams.chrgitmLvList = undefined;
  queryParams.keyDrugType = undefined;
  queryParams.medicinePropertyCode = undefined;
  queryParams.zeroStockFilter = INVENTORY_RANGE_GT_ZERO_CODE;
  queryParams.warningMode = undefined;
  queryParams.pageNo = 1;
  void router.replace({ path: route.path, query: {} });
  void getList();
}

/** 按当前筛选导出 Excel（有备份单号则导备份；列与打印目录一致）。 */
async function handleExportExcel() {
  const defaultFileName = isBackupQuery.value ? '库存明细备份.xlsx' : '库存明细.xlsx';
  // 包装/最小库存展示列展开为可求和数量 + 单位
  const exportColumns = buildExcelExportColumns(STOCK_LIST_PRINT_COLUMNS, {
    fieldExpand: {
      packageStockText: [
        { field: 'packageQuantity', title: '库存数量（包装）' },
        { field: 'packageUnitName', title: '包装单位' },
      ],
      splitStockText: [
        { field: 'minUnitQuantity', title: '库存数量（最小）' },
        { field: 'minUnitName', title: '最小单位' },
      ],
    },
    fieldAliases: {
      packageSplitRatioText: 'packageUnitRatio',
    },
  });
  const data = isBackupQuery.value
    ? await runBackupExport({
        ...buildBackupSumExportFilter(),
        exportColumns,
      })
    : await runExport({
        ...buildSumExportFilter(),
        exportColumns,
      });
  if (!data?.body?.trim()) {
    ElMessage.warning('导出内容为空');
    return;
  }
  downloadBinaryExportFile(data, defaultFileName);
  if (data.truncated) {
    ElMessage.warning('导出数据已触达行数上限，部分记录未包含在文件中');
  } else {
    ElMessage.success(`导出成功，共 ${data.exportedRowCount ?? 0} 行`);
  }
}

/** 打开打印字段勾选弹窗。 */
function handleOpenPrintFields() {
  printFieldDialogRef.value?.open();
}

/** 将一行库存明细格式化为打印单元格文案。 */
function mapStockListPrintRow(row: InventoryDetailListRowDto): Record<string, string> {
  return {
    productBusNo: row.productBusNo ?? '',
    name: row.name ?? '',
    merchandiseName: row.merchandiseName ?? '',
    totalVolume: row.totalVolume ?? '',
    packageSplitRatioText: row.packageSplitRatioText ?? '',
    categoryLabel: row.categoryLabel ?? '',
    lotNumber: row.lotNumber ?? '',
    packageStockText: row.packageStockText ?? '',
    splitStockText: row.splitStockText ?? '',
    lotCostPrice: fmtUnitPriceCell(row.lotCostPrice, row.packageUnitName),
    lotSalePrice: fmtUnitPriceCell(row.lotSalePrice, row.packageUnitName),
    lineCostTotal: fmtPurchaseAmt(row.lineCostTotal),
    linePriceTotal: fmtAmt(row.linePriceTotal),
    manufacturer: row.manufacturer ?? '',
    expirationDate: fmtDateOnly(row.expirationDate),
    productionDate: fmtDateOnly(row.productionDate),
    remainingDays:
      row.remainingDays === null || row.remainingDays === undefined
        ? ''
        : String(row.remainingDays),
    inventoryStatusLabel: row.inventoryStatusLabel ?? '',
    warehouseName: row.warehouseName ?? '',
    supplierName: row.supplierName ?? '',
    doseFormLabel: row.doseFormLabel ?? '',
  };
}

/** 拉当前筛选下全量进价/售价合计（有值才进打印，空项不印）。 */
async function loadStockListPrintGrandTotals(): Promise<ReportPrintSummaryItem[]> {
  const data = isBackupQuery.value
    ? await runBackupSum(buildBackupSumExportFilter())
    : await runSum(buildSumExportFilter());
  const items: ReportPrintSummaryItem[] = [];
  const costText = fmtPurchaseAmt(data?.sumLineCostTotal);
  if (costText) {
    items.push({ label: '进价合计', value: costText });
  }
  const priceText = fmtAmt(data?.sumLinePriceTotal);
  if (priceText) {
    items.push({ label: '售价合计', value: priceText });
  }
  return items;
}

/** 跨页打印：按当前筛选分页拉全量行，不改列表 tableData / 页脚。 */
async function fetchStockListPrintRowsAll(): Promise<{
  rows: InventoryDetailListRowDto[];
  truncated: boolean;
}> {
  const collected: InventoryDetailListRowDto[] = [];
  let pageNo = 1;
  let truncated = false;
  const maxPages = Math.ceil(PRINT_ALL_MAX_ROWS / PRINT_ALL_PAGE_SIZE);
  while (pageNo <= maxPages && collected.length < PRINT_ALL_MAX_ROWS) {
    const remaining = PRINT_ALL_MAX_ROWS - collected.length;
    const pageSize = Math.min(PRINT_ALL_PAGE_SIZE, remaining);
    // 直调分页 API，避免 runPage 覆盖当前列表
    const page = isBackupQuery.value
      ? await pageInventoryDetailBackupApi({
          ...buildBackupPageRequest(),
          pageNo,
          pageSize,
        })
      : await pageInventoryDetailCurrentApi({
          ...buildPageRequest(),
          pageNo,
          pageSize,
        });
    const batch = page.rows ?? [];
    if (batch.length === 0) {
      break;
    }
    collected.push(...batch);
    const totalElements = Number(page.totalElements ?? 0);
    if (batch.length < pageSize || collected.length >= totalElements) {
      truncated = totalElements > collected.length;
      break;
    }
    if (collected.length >= PRINT_ALL_MAX_ROWS) {
      truncated = totalElements > PRINT_ALL_MAX_ROWS;
      break;
    }
    pageNo += 1;
  }
  return { rows: collected, truncated };
}

/** 按打印设置范围打库存明细（表尾打全量合计，非本页加总）。 */
async function handlePrintCurrentPage() {
  const printScope = normalizeReportPrintScope(
    getReportPrintColumnPreference(STOCK_LIST_PRINT_REPORT_CODE)?.printScope,
  );
  const printAll = printScope === 'all';
  if (printAll) {
    if (total.value <= 0 && tableData.value.length === 0) {
      ElMessage.warning('当前无可打印数据');
      return;
    }
  } else if (tableData.value.length === 0) {
    ElMessage.warning('当前无可打印数据');
    return;
  }
  printSubmitting.value = true;
  try {
    const fetched = printAll
      ? await fetchStockListPrintRowsAll()
      : { rows: tableData.value, truncated: false };
    if (fetched.rows.length === 0) {
      ElMessage.warning('当前无可打印数据');
      return;
    }
    const summaryItems = await loadStockListPrintGrandTotals();
    const result = await printReportList({
      reportCode: STOCK_LIST_PRINT_REPORT_CODE,
      columnCatalog: STOCK_LIST_PRINT_COLUMNS,
      reportTitle: PAGE_TITLE,
      rows: fetched.rows.map((row) => mapStockListPrintRow(row)),
      jobName: PAGE_TITLE,
      summaryItems,
    });
    if (result.ok) {
      if (fetched.truncated) {
        ElMessage.warning(`打印数据已触达 ${PRINT_ALL_MAX_ROWS} 行上限，部分记录未包含`);
      }
      const scopeText = printAll
        ? `共 ${fetched.rows.length} 行`
        : `本页 ${fetched.rows.length} 行`;
      ElMessage.success(`已提交打印（${scopeText}）`);
    }
  } catch {
    // 全量合计或分页拉取失败时 useRequest/拦截器已提示，不再提交打印
  } finally {
    printSubmitting.value = false;
  }
}

/** 切换每页条数。 */
function handlePageSizeChange(limit: number) {
  queryParams.pageSize = limit;
  queryParams.pageNo = 1;
  void getList();
}

/** 切换页码。 */
function handleCurrentPageChange(page: number) {
  queryParams.pageNo = page;
  void getList();
}

/** 是否可停供。 */
function canRetireInventoryRow(row: InventoryDetailListRowDto): boolean {
  return row.inventoryStatusEnum === PublicationStatusCode.ACTIVE;
}

/** 是否可恢复供应。 */
function canResumeInventoryRow(row: InventoryDetailListRowDto): boolean {
  return row.inventoryStatusEnum === PublicationStatusCode.INACTIVE;
}

/** 本期仅药品行可查本库追溯码。 */
function canOpenTrace(row: InventoryDetailListRowDto): boolean {
  return row.itemTable === INVENTORY_DETAIL_ITEM_TABLE_MEDICATION;
}

/** 打开本库追溯码列表弹窗。 */
function handleOpenTrace(row: InventoryDetailListRowDto) {
  if (!canOpenTrace(row)) {
    ElMessage.warning('本期仅支持药品追溯码');
    return;
  }
  void traceCodeListDialogRef.value?.loadForInventoryRow({
    locationId: row.warehouseId ?? '',
    itemId: row.itemId ?? '',
    lotNumber: row.lotNumber ?? '',
    productName: row.name,
    itemTable: row.itemTable,
  });
}

/** 单行停供。 */
async function handleRetireInventoryRow(row: InventoryDetailListRowDto) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确认对「${row.name ?? row.id}」停供？`, '停供', {
      type: 'warning',
      confirmButtonText: '确认停供',
      cancelButtonText: '取消',
    });
    await runRetire({ inventoryItemIds: [row.id] });
  } catch {
    // 用户取消确认框时不提示
  }
}

/** 单行恢复供应。 */
async function handleResumeInventoryRow(row: InventoryDetailListRowDto) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确认恢复「${row.name ?? row.id}」供应？`, '取消停供', {
      type: 'info',
      confirmButtonText: '确认恢复',
      cancelButtonText: '取消',
    });
    await runResume({ inventoryItemIds: [row.id] });
  } catch {
    // 用户取消确认框时不提示
  }
}

onMounted(async () => {
  applyRouteWarningMode();
  await runInit();
  void runListBackupBusNo({});
  void getList();
});

watch(
  () => [route.query.warningMode, route.query.expireWithinDays] as const,
  () => {
    applyRouteWarningMode();
    queryParams.pageNo = 1;
    void getList();
  },
);
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryParams as Record<string, unknown>">
          <template #fields>
            <ElFormItem label="备份单号">
              <ElSelect
                v-model="queryParams.backupBusNo"
                clearable
                filterable
                placeholder="不选则查现库存"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(backupBusNoOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="关键字">
              <ElInput
                v-model="queryParams.keyword"
                clearable
                :placeholder="KEYWORD_PLACEHOLDER"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="库房">
              <ElSelect
                v-model="queryParams.warehouseIds"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="可多选"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(warehouseOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="供应商">
              <ElSelect
                v-model="queryParams.supplierIds"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="可多选"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(supplierOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="近效期(天)">
              <ElInput
                v-model="queryParams.expireWithinDays"
                clearable
                placeholder="剩余天数 ≤"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="药品类型">
              <ElSelect
                v-model="queryParams.medicationCategoryCodes"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="可多选"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(medicationCategoryOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="耗材类型">
              <ElSelect
                v-model="queryParams.deviceCategoryCodes"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="可多选"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(deviceCategoryOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="供应状态">
              <ElSelect
                v-model="queryParams.inventoryStatusEnums"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="可多选"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(inventoryStatusOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="医保等级">
              <ElSelect
                v-model="queryParams.chrgitmLvList"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="可多选"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(chrgitmLvOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="重点药类型">
              <ElSelect
                v-model="queryParams.keyDrugType"
                clearable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption label="基药" value="BASIC" />
                <ElOption label="国家重点监控药品" value="NATIONAL_MONITOR" />
                <ElOption label="集采药品" value="CENTRAL_PURCHASE" />
                <ElOption label="抗生素" value="ANTIBIOTIC" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="药品性质">
              <ElSelect
                v-model="queryParams.medicinePropertyCode"
                clearable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption label="麻醉药品" value="NARCOTIC" />
                <ElOption label="一类精神药品" value="PSYCH_ONE" />
                <ElOption label="二类精神药品" value="PSYCH_TWO" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="库存范围">
              <ElSelect
                v-model="queryParams.zeroStockFilter"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(inventoryRangeOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="项目类型">
              <ElSelect
                v-model="queryParams.itemTable"
                clearable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in itemTableSelectOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
          </template>
          <template #actions>
            <ElButton type="primary" :icon="Search" :loading="tableLoading" @click="handleQuery">
              搜索
            </ElButton>
            <ElButton :icon="Refresh" :loading="tableLoading" @click="handleReset">重置</ElButton>
          </template>
        </ReportQueryBar>
        <ElAlert
          v-if="warningFilterTip"
          class="mt-2"
          type="warning"
          closable
          show-icon
          @close="clearWarningFilter"
        >
          <div class="flex flex-wrap items-center gap-3">
            <span>{{ warningFilterTip }}</span>
            <ElButton size="small" @click="clearWarningFilter">退出预警</ElButton>
          </div>
        </ElAlert>
      </template>

      <template #toolbar>
        <ReportListToolbar :title="`${PAGE_TITLE} · 明细列表`" :total="total">
          <template #actions>
            <ElButton
              plain
              :icon="Setting"
              :disabled="printSubmitting"
              @click="handleOpenPrintFields"
            >
              打印设置
            </ElButton>
            <ElButton
              type="primary"
              plain
              :icon="Printer"
              :loading="printSubmitting"
              @click="handlePrintCurrentPage"
            >
              打印
            </ElButton>
            <ElButton
              type="primary"
              plain
              :icon="Download"
              :loading="exportLoading || backupExportLoading"
              @click="handleExportExcel"
            >
              导出 Excel
            </ElButton>
          </template>
        </ReportListToolbar>
      </template>

      <template #table>
        <ReportTableScroll ref="tableScrollRef">
          <template #default="{ tableHeight }">
            <ReportElTable
              class="inventory-detail-table"
              :loading="tableLoading"
              :data="tableData"
              :height="tableHeight"
              row-key="id"
              :row-class-name="resolveInventoryDetailRowClassName"
            >
              <ElTableColumn
                type="index"
                label="序号"
                width="56"
                align="center"
                header-align="center"
                fixed="left"
              />
              <ElTableColumn
                prop="productBusNo"
                label="编码"
                width="110"
                align="center"
                header-align="center"
                fixed="left"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="name"
                label="项目名称"
                min-width="168"
                align="center"
                header-align="center"
                fixed="left"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  <span class="inventory-detail-name-cell">
                    <span>{{ row.name }}</span>
                    <span
                      v-if="resolveInventoryDetailExpiryLabel(row)"
                      class="inventory-detail-expiry-label"
                      :class="
                        resolveInventoryDetailExpiryStatus(row) === 'expired'
                          ? 'inventory-detail-expiry-label--danger'
                          : 'inventory-detail-expiry-label--warning'
                      "
                    >
                      {{ resolveInventoryDetailExpiryLabel(row) }}
                    </span>
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="merchandiseName"
                label="商品名称"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="totalVolume"
                label="规格"
                min-width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="packageSplitRatioText"
                label="包装拆零比例"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="categoryLabel"
                label="项目类别"
                width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="lotNumber"
                label="批号"
                min-width="110"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="packageStockText"
                label="库存(包装)"
                min-width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              />
              <ElTableColumn
                prop="splitStockText"
                label="库存(最小)"
                min-width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              />
              <ElTableColumn
                label="进价"
                prop="lotCostPrice"
                width="130"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ fmtUnitPriceCell(row.lotCostPrice, row.packageUnitName) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="售价"
                prop="lotSalePrice"
                width="130"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ fmtUnitPriceCell(row.lotSalePrice, row.packageUnitName) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="进价总额"
                prop="lineCostTotal"
                width="120"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">{{ fmtPurchaseAmt(row.lineCostTotal) }}</template>
              </ElTableColumn>
              <ElTableColumn
                label="售价总额"
                prop="linePriceTotal"
                width="120"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">{{ fmtAmt(row.linePriceTotal) }}</template>
              </ElTableColumn>
              <ElTableColumn
                prop="manufacturer"
                label="厂家"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="expirationDate"
                label="效期"
                width="120"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">{{ fmtDateOnly(row.expirationDate) }}</template>
              </ElTableColumn>
              <ElTableColumn
                prop="productionDate"
                label="生产日期"
                width="120"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">{{ fmtDateOnly(row.productionDate) }}</template>
              </ElTableColumn>
              <ElTableColumn
                prop="remainingDays"
                label="剩余天数"
                width="90"
                align="center"
                header-align="center"
              />
              <ElTableColumn
                prop="inventoryStatusLabel"
                label="供应状态"
                width="100"
                align="center"
                header-align="center"
              />
              <ElTableColumn
                prop="warehouseName"
                label="库房"
                min-width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="supplierName"
                label="供应商"
                min-width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="doseFormLabel"
                label="剂型"
                width="90"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                v-if="!isBackupQuery"
                label="操作"
                width="160"
                align="center"
                header-align="center"
                fixed="right"
              >
                <template #default="{ row }">
                  <ElButton
                    v-if="canOpenTrace(row)"
                    link
                    type="primary"
                    @click="handleOpenTrace(row)"
                  >
                    追溯码
                  </ElButton>
                  <ElButton
                    v-if="canRetireInventoryRow(row)"
                    link
                    type="danger"
                    @click="handleRetireInventoryRow(row)"
                  >
                    停供
                  </ElButton>
                  <ElButton
                    v-if="canResumeInventoryRow(row)"
                    link
                    type="primary"
                    @click="handleResumeInventoryRow(row)"
                  >
                    取消停供
                  </ElButton>
                </template>
              </ElTableColumn>
            </ReportElTable>
          </template>
        </ReportTableScroll>
      </template>

      <template #footer>
        <ReportPageFooter
          :summary-items="footerSummaryItems"
          :current-page="queryParams.pageNo"
          :page-size="queryParams.pageSize"
          :page-sizes="[15, 20, 50, 100, 200, 500, 1000]"
          :total="total"
          @size-change="handlePageSizeChange"
          @current-change="handleCurrentPageChange"
        />
      </template>
    </ReportListPageLayout>

    <TraceCodeListDialog ref="traceCodeListDialogRef" />
    <ReportPrintFieldDialog
      ref="printFieldDialogRef"
      :report-code="STOCK_LIST_PRINT_REPORT_CODE"
      :column-catalog="STOCK_LIST_PRINT_COLUMNS"
      :show-print-scope="true"
    />
  </Page>
</template>

<style scoped lang="scss">
.inventory-detail-table {
  :deep(.inventory-detail-row--warning) {
    --el-table-tr-bg-color: var(--el-color-warning-light-7);

    td.el-table__cell {
      background: var(--el-color-warning-light-7) !important;
    }

    &.el-table__row--striped td.el-table__cell,
    td.el-table-fixed-column--left,
    td.el-table-fixed-column--right {
      background: var(--el-color-warning-light-7) !important;
    }
  }

  :deep(.inventory-detail-row--danger) {
    --el-table-tr-bg-color: var(--el-color-danger-light-7);

    td.el-table__cell {
      background: var(--el-color-danger-light-7) !important;
    }

    &.el-table__row--striped td.el-table__cell,
    td.el-table-fixed-column--left,
    td.el-table-fixed-column--right {
      background: var(--el-color-danger-light-7) !important;
    }
  }
}

.inventory-detail-name-cell {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  max-width: 100%;
}

.inventory-detail-expiry-label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.inventory-detail-expiry-label--warning {
  color: var(--el-color-warning);
}

.inventory-detail-expiry-label--danger {
  color: var(--el-color-danger);
}
</style>
