<!--
  门诊/住院耗材使用报表：汇总/明细双 Tab；只读查询与导出（AS-RPT-005 / AS-RPT-006）。
-->
<script setup lang="ts">
import type {
  ConsumableUsageDetailRowDto,
  ConsumableUsageExportTab,
  ConsumableUsageReportScope,
  ConsumableUsageSummaryRowDto,
} from '#/api/report/consumable-usage';
import type { ApiOptionItem } from '#/types/api-response';
import type { ReportPrintColumnDef, ReportPrintSummaryItem } from '#/utils/print/print-report-list';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Download, Printer, Refresh, Search, Setting } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElFormItem,
  ElInput,
  ElLoading,
  ElMessage,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import dayjs from 'dayjs';

import {
  EXPORT_TAB_DETAIL,
  EXPORT_TAB_SUMMARY,
  exportConsumableUsageReportApi,
  LINE_MERGE_MODE_BY_DEFINITION,
  LINE_MERGE_MODE_LINE,
  loadConsumableUsageReportPageInitApi,
  pageConsumableUsageReportDetailApi,
  SOURCE_CODE_ALL,
  SOURCE_CODE_DEPT_ISSUE,
  SOURCE_CODE_PATIENT_DISPENSE,
  SUMMARY_DIMENSION_ITEM,
  SUMMARY_DIMENSION_LOCATION,
  summarizeConsumableUsageReportApi,
} from '#/api/report/consumable-usage';
import { useRequest } from '#/hooks/use-request';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { buildExcelExportColumns } from '#/utils/export/build-excel-export-columns';
import { formatMoneyDisplay, sumMoneyThenSettle } from '#/utils/format/money-scale';
import {
  getReportPrintColumnPreference,
  normalizeReportPrintScope,
} from '#/utils/print/print-report-list';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  isReportRefundBySignedFields,
  mapReportPrintRow,
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATETIMERANGE,
  REPORT_TABLE_AMOUNT_CELL,
  REPORT_TABLE_NUM_CELL,
  ReportElTable,
  ReportListPageLayout,
  ReportListToolbar,
  ReportPageFooter,
  ReportPrintFieldDialog,
  ReportQueryBar,
  collectPagedReportRows,
  reportPrintFieldsOf,
  ReportTableScroll,
  resolveReportDetailRowClassName,
  sumReportPurchaseCornerFromRows,
  useReportListPrint,
} from '#/views/report/shared';
import {
  buildReportDateTimeRangeShortcuts,
  normalizeReportDateTime,
  toReportApiDateTimeRangePrecise,
} from '#/views/report/shared/report-date-range-shortcuts';
import { fmtReportNumber, optionDtosToSelectItems } from '#/views/report/shared/report-format';

defineOptions({ name: 'ConsumableUsageReportPage' });

interface Props {
  /** 门诊或住院耗材使用报表场景 */
  scope: ConsumableUsageReportScope;
}

interface QueryState {
  pageNo: number;
  pageSize: number;
  dateFrom: string;
  dateTo: string;
  summaryDimension: string;
  sourceCode: string;
  locationId?: string;
  exportTabCode: ConsumableUsageExportTab;
  feeNatureContractNo?: string;
  recordTypeCode?: string;
  deviceCategoryCodes: string[];
  performerPractitionerId?: string;
  deviceKeyword: string;
  patientKeyword: string;
  /** 患者=开立科室；领用=领用科室 */
  organizationId?: string;
  /**
   * 按患者维度合并（仅住院明细·患者使用源）：true → BY_DEFINITION。
   */
  mergeByPatientDefinition: boolean;
}

const props = defineProps<Props>();

const summaryDimensionOptions = ref<ApiOptionItem[]>([]);
const sourceCodeOptions = ref<ApiOptionItem[]>([]);
const locationOptions = ref<ApiOptionItem[]>([]);
const feeNatureOptions = ref<ApiOptionItem[]>([]);
const organizationOptions = ref<ApiOptionItem[]>([]);
const categoryOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
const recordTypeOptions = ref<ApiOptionItem[]>([]);
const summaryRows = ref<ConsumableUsageSummaryRowDto[]>([]);
const detailRows = ref<ConsumableUsageDetailRowDto[]>([]);
const total = ref(0);
const totalNetQuantity = ref<number | string>('');
const purchaseNetTotalAmount = ref<number | string>('');
const saleNetTotalAmount = ref<number | string>('');
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const queryParams = reactive<QueryState>({
  pageNo: 1,
  pageSize: 20,
  dateFrom: '',
  dateTo: '',
  summaryDimension: SUMMARY_DIMENSION_LOCATION,
  sourceCode: SOURCE_CODE_PATIENT_DISPENSE,
  exportTabCode: EXPORT_TAB_DETAIL,
  feeNatureContractNo: undefined,
  deviceCategoryCodes: [],
  deviceKeyword: '',
  patientKeyword: '',
  organizationId: undefined,
  mergeByPatientDefinition: false,
});

const pageTitle = computed(() =>
  props.scope === 'outpatient' ? '门诊耗材使用明细' : '住院耗材使用明细',
);

const defaultExportFileName = computed(() =>
  props.scope === 'outpatient' ? '门诊耗材使用明细.xlsx' : '住院耗材使用明细.xlsx',
);

const isSummaryTab = computed(() => queryParams.exportTabCode === EXPORT_TAB_SUMMARY);

/** 住院明细且开启按患者维度合并（科室领用源后端按 LINE 忽略）。 */
const isPatientDefinitionMerge = computed(
  () => props.scope === 'inpatient' && !isSummaryTab.value && queryParams.mergeByPatientDefinition,
);

const encounterNoLabel = computed(() => (props.scope === 'outpatient' ? '门诊号' : '住院号'));

/** 来源=领用时筛领用科室，否则开立科室。 */
const organizationFilterLabel = computed(() =>
  queryParams.sourceCode === SOURCE_CODE_DEPT_ISSUE ? '领用科室' : '开立科室',
);

/** 明细科室列随来源切换文案。 */
const departmentColumnLabel = computed(() =>
  queryParams.sourceCode === SOURCE_CODE_DEPT_ISSUE ? '领用科室' : '开立科室',
);

const dateTimeRangeShortcuts = buildReportDateTimeRangeShortcuts();
/** 日历点选日期时默认起 00:00:00、止 23:59:59（快捷项已自带时分秒）。 */
const dateTimeDefaultTime: [Date, Date] = [
  new Date(2000, 0, 1, 0, 0, 0),
  new Date(2000, 0, 1, 23, 59, 59),
];

/** 业务时间与 datetimerange 双向绑定（界面精确到秒）。 */
const businessDateRange = computed({
  get() {
    const start = normalizeReportDateTime(queryParams.dateFrom, 'start');
    const end = normalizeReportDateTime(queryParams.dateTo, 'end');
    if (start && end) {
      return [start, end] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.dateFrom = value?.[0] ? normalizeReportDateTime(value[0], 'start') || '' : '';
    queryParams.dateTo = value?.[1] ? normalizeReportDateTime(value[1], 'end') || '' : '';
  },
});

const footerSummaryItems = computed(() => {
  if (isSummaryTab.value) {
    return [
      {
        label: '数量合计',
        value: fmtReportNumber(totalNetQuantity.value, 2) || '—',
      },
      {
        label: '进价合计',
        value: formatMoneyCell(purchaseNetTotalAmount.value),
        prefix: '¥',
      },
      {
        label: '售价合计',
        value: formatMoneyCell(saleNetTotalAmount.value),
        primary: true,
        prefix: '¥',
      },
    ];
  }
  return [{ label: '明细行数', value: total.value }];
});

/** 汇总 Tab 仅「品规」维展示进/售单价。 */
const showSummaryUnitPriceColumns = computed(
  () => queryParams.summaryDimension === SUMMARY_DIMENSION_ITEM,
);

/** 汇总 Tab 可打印列（进/售单价仅品规维）。 */
const summaryPrintColumns = computed<ReportPrintColumnDef[]>(() => {
  const cols: ReportPrintColumnDef[] = [
    { field: 'dimensionName', title: '维度', align: 'center', widthWeight: 1.4 },
    { field: 'netQuantity', title: '数量', align: 'right', widthWeight: 1 },
  ];
  if (showSummaryUnitPriceColumns.value) {
    cols.push(
      { field: 'purchaseUnitPrice', title: '进价单价', align: 'right', widthWeight: 1 },
      { field: 'saleUnitPrice', title: '售价单价', align: 'right', widthWeight: 1 },
    );
  }
  cols.push(
    { field: 'purchaseNetAmount', title: '进价合计', align: 'right', widthWeight: 1 },
    { field: 'saleNetAmount', title: '售价合计', align: 'right', widthWeight: 1 },
  );
  return cols;
});

/** 明细 Tab 可打印列（病区/床号仅住院）。 */
const printColumnCatalog = computed<ReportPrintColumnDef[]>(() => {
  if (isSummaryTab.value) {
    return summaryPrintColumns.value;
  }
  const cols: ReportPrintColumnDef[] = [
    { field: 'sourceCode_dictText', title: '来源', align: 'center', widthWeight: 0.9 },
  ];
  // 合并态隐藏业务时间（逐条差异）
  if (!isPatientDefinitionMerge.value) {
    cols.push({ field: 'businessTime', title: '业务时间', align: 'center', widthWeight: 1.3 });
  }
  cols.push(
    { field: 'deviceName', title: '耗材名称', align: 'center', widthWeight: 2 },
    { field: 'specText', title: '规格', align: 'center', widthWeight: 1.2 },
    { field: 'patientName', title: '患者', align: 'center', widthWeight: 0.9 },
    { field: 'encounterBusNo', title: '就诊号', align: 'center', widthWeight: 1.5 },
    { field: 'feeNatureDisplay', title: '费用性质', align: 'center', widthWeight: 0.9 },
    { field: 'medTypeDisplay', title: '医疗类别', align: 'center', widthWeight: 0.9 },
  );
  // 住院才有病区/床号列
  if (props.scope === 'inpatient') {
    cols.push(
      { field: 'wardName', title: '病区', align: 'center', widthWeight: 1 },
      { field: 'bedName', title: '床号', align: 'center', widthWeight: 0.7 },
    );
  }
  cols.push(
    {
      field: 'organizationName',
      title: departmentColumnLabel.value,
      align: 'center',
      widthWeight: 1.2,
    },
    { field: 'locationName', title: '耗材房', align: 'center', widthWeight: 1.4 },
  );
  if (!isPatientDefinitionMerge.value) {
    cols.push({
      field: 'statusOrTypeEnum_enumText',
      title: '状态/类型',
      align: 'center',
      widthWeight: 0.9,
    });
  }
  cols.push(
    { field: 'quantityDisplay', title: '数量', align: 'right', widthWeight: 1 },
    { field: 'purchaseUnitPrice', title: '进价单价', align: 'right', widthWeight: 0.9 },
    { field: 'purchaseAmount', title: '进价总价', align: 'right', widthWeight: 1 },
    { field: 'saleUnitPrice', title: '售价单价', align: 'right', widthWeight: 0.9 },
    { field: 'saleAmount', title: '售价总价', align: 'right', widthWeight: 1 },
  );
  if (!isPatientDefinitionMerge.value) {
    cols.push(
      { field: 'lotNumber', title: '批号', align: 'center', widthWeight: 1 },
      { field: 'performerName', title: '执行人', align: 'center', widthWeight: 0.8 },
      { field: 'busNo', title: '业务单号', align: 'center', widthWeight: 1.4 },
    );
  }
  return cols;
});

/** 打印偏好键：按 scope + 汇总/明细隔离。 */
const printReportCode = computed(
  () => `report-consumable-usage-${props.scope}-${isSummaryTab.value ? 'summary' : 'detail'}`,
);

/** 打印单据标题（含当前 Tab）。 */
const printReportTitle = computed(
  () => `${pageTitle.value} · ${isSummaryTab.value ? '汇总' : '明细'}`,
);

/** 将当前页汇总行映射为打印单元格。 */
function mapSummaryPrintRows(): Record<string, string>[] {
  const fields = reportPrintFieldsOf(summaryPrintColumns.value);
  // 只打当前汇总视图行
  return summaryRows.value.map((row) =>
    mapReportPrintRow(row as Record<string, unknown>, fields, {
      netQuantity: (value) => formatQtyCell(value),
      purchaseUnitPrice: (value) => formatMoneyCell(value),
      saleUnitPrice: (value) => formatMoneyCell(value),
      purchaseNetAmount: (value) => formatMoneyCell(value),
      saleNetAmount: (value) => formatMoneyCell(value),
    }),
  );
}

/** 将当前页明细行映射为打印单元格。 */
function mapDetailPrintRows(source = detailRows.value): Record<string, string>[] {
  const catalog = printColumnCatalog.value;
  const fields = reportPrintFieldsOf(catalog);
  return source.map((row) =>
    mapReportPrintRow(row as Record<string, unknown>, fields, {
      sourceCode_dictText: (value, r) => String(value ?? r.sourceCode ?? '').trim(),
      quantityDisplay: (_value, r) =>
        formatQtyWithUnit(r.quantity, r.unitCode_dictText as string | undefined),
      purchaseUnitPrice: (value) => formatMoneyCell(value),
      purchaseAmount: (value) => formatMoneyCell(value),
      saleUnitPrice: (value) => formatMoneyCell(value),
      saleAmount: (value) => formatMoneyCell(value),
    }),
  );
}

/** 打印合计金额：空不印。 */
function formatPrintMoney(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '';
  }
  return formatMoneyDisplay(value);
}

/** 进价合计、售价合计有展示值才进打印。 */
function toPrintAmountSummaryItems(
  purchase: number | string,
  sale: number | string,
): ReportPrintSummaryItem[] {
  const items: ReportPrintSummaryItem[] = [];
  const purchaseText = formatPrintMoney(purchase);
  if (purchaseText) {
    items.push({ label: '进价合计', value: purchaseText });
  }
  const saleText = formatPrintMoney(sale);
  if (saleText) {
    items.push({ label: '售价合计', value: saleText });
  }
  return items;
}

/** 组装打印表尾金额汇总（对齐库存明细：最后一页右下角）。 */
function buildPrintSummaryItems(): ReportPrintSummaryItem[] {
  if (isSummaryTab.value) {
    return toPrintAmountSummaryItems(purchaseNetTotalAmount.value, saleNetTotalAmount.value);
  }
  if (
    printAllDetailSummary.value &&
    normalizeReportPrintScope(getReportPrintColumnPreference(printReportCode.value)?.printScope) ===
      'all'
  ) {
    return printAllDetailSummary.value;
  }
  const purchaseSum = sumMoneyThenSettle(detailRows.value.map((row) => row.purchaseAmount));
  const saleSum = sumMoneyThenSettle(detailRows.value.map((row) => row.saleAmount));
  return toPrintAmountSummaryItems(purchaseSum, saleSum);
}

/** 跨页打印明细时暂存全量进价/售价合计，避免表尾仍加总本页。 */
const printAllDetailSummary = ref<ReportPrintSummaryItem[] | undefined>();

/** 跨页打印：汇总已一次返回；明细按当前筛选分页拉全量。 */
async function fetchAllConsumablePrintRows() {
  printAllDetailSummary.value = undefined;
  if (isSummaryTab.value) {
    return { rows: mapSummaryPrintRows(), truncated: false };
  }
  const filters = buildFilterPayload();
  if (!filters) {
    return { rows: [], truncated: false };
  }
  const collected = await collectPagedReportRows({
    fetchPage: async (pageNo, pageSize) => {
      const page = await pageConsumableUsageReportDetailApi(props.scope, {
        ...filters,
        pageNo,
        pageSize,
      });
      return { rows: page.records ?? [], total: Number(page.total ?? 0) };
    },
  });
  const purchaseSum = sumMoneyThenSettle(collected.rows.map((row) => row.purchaseAmount));
  const saleSum = sumMoneyThenSettle(collected.rows.map((row) => row.saleAmount));
  printAllDetailSummary.value = toPrintAmountSummaryItems(purchaseSum, saleSum);
  return { rows: mapDetailPrintRows(collected.rows), truncated: collected.truncated };
}

const { printFieldDialogRef, printSubmitting, handleOpenPrintFields, handlePrintCurrentPage } =
  useReportListPrint({
    getReportCode: () => printReportCode.value,
    getColumnCatalog: () => printColumnCatalog.value,
    getReportTitle: () => printReportTitle.value,
    getPrintRows: () => (isSummaryTab.value ? mapSummaryPrintRows() : mapDetailPrintRows()),
    getSummaryItems: () => buildPrintSummaryItems(),
    fetchAllPrintRows: fetchAllConsumablePrintRows,
    hasPrintableData: () =>
      isSummaryTab.value
        ? summaryRows.value.length > 0
        : total.value > 0 || detailRows.value.length > 0,
  });

const { run: runInit, loading: initLoading } = useRequest(
  () => loadConsumableUsageReportPageInitApi(props.scope),
  {
    manual: true,
    onSuccess: (data) => {
      summaryDimensionOptions.value = optionDtosToSelectItems(data?.summaryDimensionOptions);
      // 去掉遗留 ALL，默认患者使用
      sourceCodeOptions.value = optionDtosToSelectItems(data?.sourceCodeOptions).filter(
        (item) => String(item.value) !== SOURCE_CODE_ALL,
      );
      locationOptions.value = optionDtosToSelectItems(data?.locationOptions);
      feeNatureOptions.value = optionDtosToSelectItems(data?.feeNatureOptions);
      organizationOptions.value = optionDtosToSelectItems(data?.organizationOptions);
      categoryOptions.value = optionDtosToSelectItems(data?.categoryOptions);
      practitionerOptions.value = optionDtosToSelectItems(data?.practitionerOptions);
      recordTypeOptions.value = optionDtosToSelectItems(data?.recordTypeOptions);
      queryParams.dateFrom =
        normalizeReportDateTime(data?.defaultDateFrom, 'start') ||
        dayjs().startOf('day').format('YYYY-MM-DD HH:mm:ss');
      queryParams.dateTo =
        normalizeReportDateTime(data?.defaultDateTo, 'end') ||
        dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss');
      const firstDim = summaryDimensionOptions.value[0]?.value;
      if (firstDim) {
        queryParams.summaryDimension = String(firstDim);
      }
      const firstSource = sourceCodeOptions.value[0]?.value;
      if (firstSource) {
        queryParams.sourceCode = String(firstSource);
      } else {
        queryParams.sourceCode = SOURCE_CODE_PATIENT_DISPENSE;
      }
      const firstRecordType = recordTypeOptions.value[0]?.value;
      if (firstRecordType) {
        queryParams.recordTypeCode = String(firstRecordType);
      }
    },
  },
);

const { run: runSummarize, loading: summarizeLoading } = useRequest(
  (body) => summarizeConsumableUsageReportApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      summaryRows.value = data?.rows ?? [];
      totalNetQuantity.value = data?.totalNetQuantity ?? '';
      // Type B：进价列角=当次汇总行净进价加总
      purchaseNetTotalAmount.value = sumReportPurchaseCornerFromRows(
        summaryRows.value as Array<Record<string, unknown>>,
        'purchaseNetAmount',
      );
      saleNetTotalAmount.value = data?.saleNetTotalAmount ?? '';
      total.value = summaryRows.value.length;
    },
  },
);

const { run: runPageDetail, loading: detailLoading } = useRequest(
  (body) => pageConsumableUsageReportDetailApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      detailRows.value = data?.records ?? [];
      total.value = Number(data?.total ?? 0);
      // 明细分页不返回合计；页脚数量用 total，金额/数量合计置空避免误导
      totalNetQuantity.value = '';
      purchaseNetTotalAmount.value = '';
      saleNetTotalAmount.value = '';
    },
  },
);

const { run: runExport, loading: exportLoading } = useRequest(
  (body) => exportConsumableUsageReportApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      downloadBinaryExportFile(data, defaultExportFileName.value);
      if (data.truncated) {
        ElMessage.warning('导出数据已触达行数上限，部分记录未包含在文件中');
      } else {
        ElMessage.success(`导出成功，共 ${data.exportedRowCount ?? 0} 行`);
      }
    },
  },
);

const tableLoading = computed(() => {
  // 开合并时用全屏 ElLoading，表格 loading 关掉，避免双圈
  if (mergeFullscreenBusy.value) {
    return false;
  }
  return (
    initLoading.value ||
    summarizeLoading.value ||
    detailLoading.value ||
    printSubmitting.value ||
    mergeQueryBusy.value
  );
});

/** 按钮/开关忙态（含全屏合并查询）。 */
const queryActionBusy = computed(
  () => mergeFullscreenBusy.value || mergeQueryBusy.value || tableLoading.value,
);

/** 切换合并时先占住 loading，避免后端慢查询时界面像卡死。 */
const mergeQueryBusy = ref(false);
/** 开合并全屏 loading 中（与表格 loading 互斥）。 */
const mergeFullscreenBusy = ref(false);

/** 下拉选项转为 ElSelect 结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 金额单元格：空占位，其余按展示刻度。 */
function formatMoneyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return formatMoneyDisplay(value);
}

/** 数量单元格。 */
function formatQtyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return fmtReportNumber(value, 2) || String(value);
}

/** 数量 + 单位。 */
function formatQtyWithUnit(quantity: unknown, unit?: string): string {
  const qty = formatQtyCell(quantity);
  const unitText = String(unit ?? '').trim();
  if (qty === '—') {
    return unitText || '—';
  }
  return unitText ? `${qty}${unitText}` : qty;
}

/** 明细/汇总行是否退耗冲减（数量或金额为负）。 */
function isConsumableUsageRefundRow(
  row: ConsumableUsageDetailRowDto | ConsumableUsageSummaryRowDto,
): boolean {
  return isReportRefundBySignedFields(row as Record<string, unknown>, [
    'quantity',
    'netQuantity',
    'purchaseAmount',
    'saleAmount',
    'purchaseNetAmount',
    'saleNetAmount',
  ]);
}

/**
 * 退耗冲减整行标红；账批≠实发批加 warning 底色（金额口径不变）。
 *
 * @param row 明细或汇总行
 */
function resolveUsageRowClassName({
  row,
}: {
  row: ConsumableUsageDetailRowDto | ConsumableUsageSummaryRowDto;
}): string {
  const detail = row as ConsumableUsageDetailRowDto;
  return resolveReportDetailRowClassName({
    isRefund: isConsumableUsageRefundRow(row),
    lotMismatch: detail.lotMismatch === true,
  });
}

/**
 * 批号列展示：不一致时附收费批号提示（质检；不改正额）。
 *
 * @param row 明细分页行
 */
function formatLotNumberCell(row: ConsumableUsageDetailRowDto): string {
  const dispenseLot = row.lotNumber?.trim() || '—';
  // 账批≠实发批：主列仍是实发批，括号展示账批便于排查
  if (row.lotMismatch === true && row.chargeLotNumber?.trim()) {
    return `${dispenseLot}（账批 ${row.chargeLotNumber.trim()}）`;
  }
  return dispenseLot;
}

/** 来源 Tag 样式。 */
function resolveSourceTagType(sourceCode?: string): 'info' | 'success' | 'warning' {
  if (sourceCode === 'DEPT_ISSUE') {
    return 'warning';
  }
  if (sourceCode === 'PATIENT_DISPENSE') {
    return 'success';
  }
  return 'info';
}

/** 明细行稳定 key（两路并集）。 */
function resolveDetailRowKey(row: ConsumableUsageDetailRowDto) {
  return [
    row.sourceCode ?? '',
    row.dispenseId ?? '',
    row.supplyRequestId ?? '',
    row.deviceDefId ?? '',
    row.businessTime ?? '',
  ].join('|');
}

/** 汇总行 key。 */
function resolveSummaryRowKey(row: ConsumableUsageSummaryRowDto) {
  if (row.dimensionKey) {
    return row.dimensionKey;
  }
  return [
    row.summaryDimension ?? '',
    row.patientId ?? '',
    row.organizationId ?? '',
    row.deviceDefId ?? '',
  ].join('|');
}

/** 校验业务时间并组装筛选体。 */
function buildFilterPayload() {
  const dateRange = toReportApiDateTimeRangePrecise(queryParams.dateFrom, queryParams.dateTo);
  const dateFrom = dateRange.start;
  const dateTo = dateRange.end;
  if (!dateFrom || !dateTo) {
    ElMessage.warning('请选择业务时间');
    return null;
  }
  if (dayjs(dateFrom).isAfter(dayjs(dateTo))) {
    ElMessage.warning('开始时间不能晚于结束时间');
    return null;
  }
  return {
    dateFrom,
    dateTo,
    sourceCode: queryParams.sourceCode || SOURCE_CODE_PATIENT_DISPENSE,
    locationId: preserveApiIdForRequest(queryParams.locationId),
    feeNatureContractNo: queryParams.feeNatureContractNo || undefined,
    recordTypeCode:
      queryParams.recordTypeCode ||
      (recordTypeOptions.value[0]?.value ? String(recordTypeOptions.value[0].value) : undefined),
    deviceCategoryCodes:
      queryParams.deviceCategoryCodes.length > 0 ? [...queryParams.deviceCategoryCodes] : undefined,
    performerPractitionerId: preserveApiIdForRequest(queryParams.performerPractitionerId),
    deviceKeyword: queryParams.deviceKeyword.trim() || undefined,
    patientKeyword: queryParams.patientKeyword.trim() || undefined,
    organizationId: preserveApiIdForRequest(queryParams.organizationId),
    lineMergeMode: resolveLineMergeModeForRequest(),
  };
}

/** 住院明细组装 lineMergeMode；门诊/汇总不传。 */
function resolveLineMergeModeForRequest(): string | undefined {
  if (props.scope !== 'inpatient' || isSummaryTab.value) {
    return undefined;
  }
  if (queryParams.mergeByPatientDefinition) {
    return LINE_MERGE_MODE_BY_DEFINITION;
  }
  return LINE_MERGE_MODE_LINE;
}

/** 拉取当前 Tab 数据。 */
async function getList() {
  const filters = buildFilterPayload();
  if (!filters) {
    return;
  }
  if (isSummaryTab.value) {
    await runSummarize({
      ...filters,
      summaryDimension: queryParams.summaryDimension || SUMMARY_DIMENSION_LOCATION,
    });
  } else {
    await runPageDetail({
      ...filters,
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
    });
  }
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 记录类型变更：清空时回落到「全部」，避免 UI 空白而后端仍按 ALL。 */
function handleRecordTypeChange(value: string | null | undefined) {
  if (!value) {
    const allCode = recordTypeOptions.value[0]?.value;
    queryParams.recordTypeCode = allCode ? String(allCode) : undefined;
  }
  handleQuery();
}

/** 来源切换：清空科室筛，避免开立/领用串用。 */
function handleSourceChange() {
  queryParams.organizationId = undefined;
  handleQuery();
}

/** 搜索：回到第一页再查。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/**
 * 切换「按患者维度合并」后立即查询。
 * 开合并时额外全屏 loading，避免慢查询时像卡死。
 */
async function handleMergeByPatientChange(enabled: string | number | boolean) {
  queryParams.pageNo = 1;
  mergeQueryBusy.value = true;
  const mergeOn = enabled === true;
  let fullscreenLoading: ReturnType<typeof ElLoading.service> | undefined;
  if (mergeOn) {
    mergeFullscreenBusy.value = true;
    fullscreenLoading = ElLoading.service({
      lock: true,
      text: '按患者维度合并查询中，请稍候…',
      background: 'rgba(0, 0, 0, 0.25)',
    });
  }
  // 先让 loading 画出一帧，再发请求
  await nextTick();
  try {
    await getList();
  } finally {
    mergeQueryBusy.value = false;
    mergeFullscreenBusy.value = false;
    fullscreenLoading?.close();
  }
}

/** 切换汇总/明细 Tab 后立即查询。 */
function handleTabChange() {
  queryParams.pageNo = 1;
  void getList();
}

/** 导出当前 Tab（列清单与页面可见列一致）。 */
async function handleExport() {
  const filters = buildFilterPayload();
  if (!filters) {
    return;
  }
  // 耗材汇总无包装单位列；明细 quantityDisplay → quantity+unitText
  const exportColumns = buildExcelExportColumns(printColumnCatalog.value, {
    fieldExpand: {
      netQuantity: [{ field: 'netQuantity' }],
      quantityDisplay: [
        { field: 'quantity', title: '数量' },
        { field: 'unitText', title: '单位' },
      ],
    },
    fieldAliases: {
      sourceCode_dictText: 'sourceText',
      statusOrTypeEnum_enumText: 'statusOrTypeText',
      businessTime: 'businessTimeText',
    },
  });
  await runExport({
    ...filters,
    exportTabCode: queryParams.exportTabCode,
    summaryDimension: isSummaryTab.value
      ? queryParams.summaryDimension || SUMMARY_DIMENSION_LOCATION
      : undefined,
    exportColumns,
  });
}

/** 重置为初始化默认值。 */
async function handleReset() {
  queryParams.pageNo = 1;
  queryParams.pageSize = 20;
  queryParams.summaryDimension = String(
    summaryDimensionOptions.value[0]?.value ?? SUMMARY_DIMENSION_LOCATION,
  );
  queryParams.sourceCode = String(
    sourceCodeOptions.value[0]?.value ?? SOURCE_CODE_PATIENT_DISPENSE,
  );
  queryParams.locationId = undefined;
  queryParams.feeNatureContractNo = undefined;
  queryParams.recordTypeCode = undefined;
  queryParams.deviceCategoryCodes = [];
  queryParams.performerPractitionerId = undefined;
  queryParams.deviceKeyword = '';
  queryParams.patientKeyword = '';
  queryParams.organizationId = undefined;
  queryParams.mergeByPatientDefinition = false;
  queryParams.exportTabCode = EXPORT_TAB_DETAIL;
  await runInit();
  void getList();
}

/** 切换每页条数（仅明细）。 */
function handlePageSizeChange(size: number) {
  queryParams.pageSize = size;
  queryParams.pageNo = 1;
  void getList();
}

/** 切换页码（仅明细）。 */
function handleCurrentPageChange(page: number) {
  queryParams.pageNo = page;
  void getList();
}

onMounted(async () => {
  try {
    await runInit();
    await getList();
  } catch {
    // init 失败时不自动查询
  }
});
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryParams as Record<string, unknown>">
          <template #fields>
            <ElFormItem label="业务时间">
              <ElDatePicker
                v-model="businessDateRange"
                type="datetimerange"
                value-format="YYYY-MM-DD HH:mm:ss"
                format="YYYY-MM-DD HH:mm:ss"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                :clearable="false"
                :default-time="dateTimeDefaultTime"
                :shortcuts="dateTimeRangeShortcuts"
                :class="[REPORT_QUERY_CONTROL, REPORT_QUERY_CONTROL_DATETIMERANGE]"
                @change="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="来源">
              <ElSelect
                v-model="queryParams.sourceCode"
                :class="REPORT_QUERY_CONTROL"
                @change="handleSourceChange"
              >
                <ElOption
                  v-for="item in toSelectOptions(sourceCodeOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem :label="organizationFilterLabel">
              <ElSelect
                v-model="queryParams.organizationId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(organizationOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="记录类型">
              <ElSelect
                v-model="queryParams.recordTypeCode"
                clearable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleRecordTypeChange"
              >
                <ElOption
                  v-for="item in toSelectOptions(recordTypeOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="耗材房">
              <ElSelect
                v-model="queryParams.locationId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(locationOptions)"
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
                collapse-tags-tooltip
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(categoryOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="发耗材人">
              <ElSelect
                v-model="queryParams.performerPractitionerId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(practitionerOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="耗材">
              <ElInput
                v-model="queryParams.deviceKeyword"
                clearable
                placeholder="名称/拼音/编码"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="患者">
              <ElInput
                v-model="queryParams.patientKeyword"
                clearable
                :placeholder="props.scope === 'outpatient' ? '姓名/门诊号等' : '姓名/住院号等'"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="费用性质">
              <ElSelect
                v-model="queryParams.feeNatureContractNo"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(feeNatureOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem v-if="isSummaryTab" label="汇总维">
              <ElSelect
                v-model="queryParams.summaryDimension"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(summaryDimensionOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <!-- 占查询区栅格空位；长文案放 content，避免 88px label 竖排 -->
            <ElFormItem
              v-if="scope === 'inpatient' && !isSummaryTab"
              label-width="0"
              class="report-list-page__query-switch-item"
            >
              <div class="report-list-page__query-switch">
                <span>按患者维度合并</span>
                <ElSwitch
                  v-model="queryParams.mergeByPatientDefinition"
                  :disabled="queryActionBusy"
                  @change="handleMergeByPatientChange"
                />
              </div>
            </ElFormItem>
          </template>
          <template #actions>
            <ElButton type="primary" :icon="Search" :loading="queryActionBusy" @click="handleQuery">
              搜索
            </ElButton>
            <ElButton :icon="Refresh" :loading="queryActionBusy" @click="handleReset"
              >重置</ElButton
            >
          </template>
        </ReportQueryBar>
      </template>

      <template #toolbar>
        <ReportListToolbar
          :title="`${pageTitle} · ${isSummaryTab ? '汇总' : '明细'}`"
          :total="isSummaryTab ? summaryRows.length : total"
        >
          <template #actions>
            <ElRadioGroup v-model="queryParams.exportTabCode" @change="handleTabChange">
              <ElRadioButton :value="EXPORT_TAB_SUMMARY">汇总</ElRadioButton>
              <ElRadioButton :value="EXPORT_TAB_DETAIL">明细</ElRadioButton>
            </ElRadioGroup>
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
            <ElButton plain :icon="Download" :loading="exportLoading" @click="handleExport">
              导出
            </ElButton>
          </template>
        </ReportListToolbar>
      </template>

      <template #table>
        <ReportTableScroll ref="tableScrollRef">
          <template #default="{ tableHeight }">
            <ReportElTable
              v-if="isSummaryTab"
              :loading="tableLoading"
              :data="summaryRows"
              :height="tableHeight"
              :row-key="resolveSummaryRowKey"
              :row-class-name="resolveUsageRowClassName"
            >
              <ElTableColumn
                type="index"
                label="序号"
                width="56"
                align="center"
                header-align="center"
              />
              <ElTableColumn
                prop="dimensionName"
                label="维度"
                min-width="160"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                label="数量"
                width="120"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ formatQtyCell(row.netQuantity) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="showSummaryUnitPriceColumns"
                label="进价单价"
                width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatMoneyCell(row.purchaseUnitPrice) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="showSummaryUnitPriceColumns"
                label="售价单价"
                width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatMoneyCell(row.saleUnitPrice) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="进价合计"
                width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatMoneyCell(row.purchaseNetAmount) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="售价合计"
                width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatMoneyCell(row.saleNetAmount) }}
                </template>
              </ElTableColumn>
            </ReportElTable>

            <ReportElTable
              v-else
              :loading="tableLoading"
              :data="detailRows"
              :height="tableHeight"
              :row-key="resolveDetailRowKey"
              :row-class-name="resolveUsageRowClassName"
            >
              <ElTableColumn label="来源" width="100" align="center" header-align="center">
                <template #default="{ row }">
                  <ElTag :type="resolveSourceTagType(row.sourceCode)" effect="light">
                    {{ row.sourceCode_dictText || row.sourceCode || '—' }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="!isPatientDefinitionMerge"
                prop="businessTime"
                label="业务时间"
                min-width="170"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="deviceName"
                label="耗材名称"
                min-width="240"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="specText"
                label="规格"
                min-width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="patientName"
                label="患者"
                min-width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="encounterBusNo"
                :label="encounterNoLabel"
                min-width="180"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="feeNatureDisplay"
                label="费用性质"
                min-width="110"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="medTypeDisplay"
                label="医疗类别"
                min-width="110"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <template v-if="scope === 'inpatient'">
                <ElTableColumn
                  prop="wardName"
                  label="病区"
                  min-width="120"
                  align="center"
                  header-align="center"
                  show-overflow-tooltip
                />
                <ElTableColumn
                  prop="bedName"
                  label="床号"
                  width="72"
                  align="center"
                  header-align="center"
                  show-overflow-tooltip
                />
              </template>
              <ElTableColumn
                prop="organizationName"
                :label="departmentColumnLabel"
                min-width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="locationName"
                label="耗材房"
                min-width="160"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                v-if="!isPatientDefinitionMerge"
                prop="statusOrTypeEnum_enumText"
                label="状态/类型"
                min-width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                label="数量"
                min-width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ formatQtyWithUnit(row.quantity, row.unitCode_dictText) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="进价单价"
                min-width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatMoneyCell(row.purchaseUnitPrice) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="进价总价"
                min-width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatMoneyCell(row.purchaseAmount) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="售价单价"
                min-width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatMoneyCell(row.saleUnitPrice) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="售价总价"
                min-width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatMoneyCell(row.saleAmount) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="!isPatientDefinitionMerge"
                label="批号"
                min-width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  <span :class="{ 'text-[var(--el-color-danger)]': row.lotMismatch === true }">
                    {{ formatLotNumberCell(row) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="!isPatientDefinitionMerge"
                prop="performerName"
                label="执行人"
                min-width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                v-if="!isPatientDefinitionMerge"
                prop="busNo"
                label="业务单号"
                min-width="170"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
            </ReportElTable>
          </template>
        </ReportTableScroll>
      </template>

      <template #footer>
        <ReportPageFooter
          :summary-items="footerSummaryItems"
          :current-page="queryParams.pageNo"
          :page-size="isSummaryTab ? Math.max(summaryRows.length, 1) : queryParams.pageSize"
          :total="isSummaryTab ? summaryRows.length : total"
          :layout="isSummaryTab ? 'total' : 'sizes, prev, pager, next, jumper'"
          @size-change="handlePageSizeChange"
          @current-change="handleCurrentPageChange"
        />
      </template>
    </ReportListPageLayout>
    <ReportPrintFieldDialog
      ref="printFieldDialogRef"
      :report-code="printReportCode"
      :column-catalog="printColumnCatalog"
      show-print-scope
    />
  </Page>
</template>
