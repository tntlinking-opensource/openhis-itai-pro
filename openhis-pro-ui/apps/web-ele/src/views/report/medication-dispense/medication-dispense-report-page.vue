<!--
  门诊/住院药品使用报表：汇总/明细双 Tab；来源仅患者使用 / 科室领用（禁止 ALL 并集）。
-->
<script setup lang="ts">
import type {
  MedicationDispenseDetailRowDto,
  MedicationDispensePresentationMode,
  MedicationDispenseReportScope,
  MedicationDispenseSummaryRowDto,
} from '#/api/report/medication-dispense';
import type { ApiOptionItem } from '#/types/api-response';
import type { ReportPrintColumnDef } from '#/utils/print/print-report-list';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import { Download, Printer, Refresh, Search, Setting } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import dayjs from 'dayjs';

import {
  exportMedicationDispenseReportApi,
  loadMedicationDispenseReportPageInitApi,
  pageMedicationDispenseReportDetailApi,
  PRESENTATION_MODE_DETAIL,
  PRESENTATION_MODE_SUMMARY,
  SOURCE_CODE_ALL,
  SOURCE_CODE_DEPT_ISSUE,
  SOURCE_CODE_PATIENT_DISPENSE,
  SUMMARY_DIMENSION_ITEM,
  SUMMARY_DIMENSION_PHARMACY,
  summarizeMedicationDispenseReportApi,
} from '#/api/report/medication-dispense';
import TraceCodeTooltip from '#/components/trace/trace-code-tooltip.vue';
import { useRequest } from '#/hooks/use-request';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { buildExcelExportColumns } from '#/utils/export/build-excel-export-columns';
import { formatMoneyDisplay, formatPurchaseMoneyDisplay } from '#/utils/format/money-scale';
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
import { optionDtosToSelectItems } from '#/views/report/shared/report-format';

defineOptions({ name: 'MedicationDispenseReportPage' });

interface Props {
  /** 门诊或住院发药报表场景 */
  scope: MedicationDispenseReportScope;
}

interface QueryState {
  pageNo: number;
  pageSize: number;
  dateFrom: string;
  dateTo: string;
  recordTypeCode?: string;
  pharmacyLocationId?: string;
  medicationCategoryCodes: string[];
  dispenserPractitionerId?: string;
  drugKeyword: string;
  patientKeyword: string;
  summaryDimension: string;
  sourceCode: string;
  presentationMode: MedicationDispensePresentationMode;
  feeNatureContractNo?: string;
  /** 患者=开立科室；领用=领用科室 */
  organizationId?: string;
  /** 药品性质 KeyDrugType；空=不限 */
  keyDrugType?: string;
}

const props = defineProps<Props>();

const pharmacyOptions = ref<ApiOptionItem[]>([]);
const categoryOptions = ref<ApiOptionItem[]>([]);
const medicinePropertyOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
const recordTypeOptions = ref<ApiOptionItem[]>([]);
const summaryDimensionOptions = ref<ApiOptionItem[]>([]);
const sourceOptions = ref<ApiOptionItem[]>([]);
const organizationOptions = ref<ApiOptionItem[]>([]);
const feeNatureOptions = ref<ApiOptionItem[]>([]);
const summaryRows = ref<MedicationDispenseSummaryRowDto[]>([]);
const detailRows = ref<MedicationDispenseDetailRowDto[]>([]);
const total = ref(0);
const totalQuantity = ref<number | string>('');
const purchaseTotalAmount = ref<number | string>('');
const saleTotalAmount = ref<number | string>('');
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const queryParams = reactive<QueryState>({
  pageNo: 1,
  pageSize: 20,
  dateFrom: '',
  dateTo: '',
  medicationCategoryCodes: [],
  drugKeyword: '',
  patientKeyword: '',
  summaryDimension: SUMMARY_DIMENSION_PHARMACY,
  sourceCode: SOURCE_CODE_PATIENT_DISPENSE,
  presentationMode: PRESENTATION_MODE_DETAIL,
  feeNatureContractNo: undefined,
  organizationId: undefined,
});

/** 去掉遗留 SOURCE_ALL，默认患者使用。 */
function normalizeSourceOptions(items: ApiOptionItem[]): ApiOptionItem[] {
  const filtered = items.filter((item) => String(item.value) !== SOURCE_CODE_ALL);
  if (filtered.length > 0) {
    return filtered;
  }
  return [
    { value: SOURCE_CODE_PATIENT_DISPENSE, label: '患者发药' },
    { value: SOURCE_CODE_DEPT_ISSUE, label: '科室领用' },
  ];
}

const pageTitle = computed(() =>
  props.scope === 'outpatient' ? '门诊药品使用明细' : '住院药品使用明细',
);

const defaultExportFileName = computed(() =>
  props.scope === 'outpatient' ? '门诊药品使用明细.xlsx' : '住院药品使用明细.xlsx',
);

const isSummaryTab = computed(() => queryParams.presentationMode === PRESENTATION_MODE_SUMMARY);

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

/** 查询条 model（避免模板内 as 断言触发 vue parser）。 */
const queryBarModel = computed(() => queryParams as Record<string, unknown>);

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
        value: formatQtyCell(totalQuantity.value) || '—',
      },
      {
        label: '进价合计',
        value: formatPurchaseMoneyCell(purchaseTotalAmount.value),
        prefix: '¥',
      },
      {
        label: '售价合计',
        value: formatMoneyCell(saleTotalAmount.value),
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

/** 汇总 Tab 仅「品规」维展示药品编码、最小数量。 */
const showSummaryItemExtraColumns = computed(
  () => queryParams.summaryDimension === SUMMARY_DIMENSION_ITEM,
);

/** 汇总 Tab 可打印列（进/售单价、编码、最小数量仅品规维）。 */
const summaryPrintColumns = computed<ReportPrintColumnDef[]>(() => {
  const cols: ReportPrintColumnDef[] = [
    { field: 'dimensionName', title: '维度', align: 'center', widthWeight: 1.4 },
  ];
  if (showSummaryItemExtraColumns.value) {
    cols.push({ field: 'medicationCode', title: '药品编码', align: 'center', widthWeight: 1 });
  }
  cols.push({ field: 'netQuantity', title: '数量', align: 'right', widthWeight: 1 });
  if (showSummaryItemExtraColumns.value) {
    cols.push({ field: 'minUnitQuantity', title: '最小数量', align: 'right', widthWeight: 1 });
  }
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
    { field: 'sourceText', title: '来源', align: 'center', widthWeight: 0.9 },
    { field: 'recordTypeText', title: '类型', align: 'center', widthWeight: 0.8 },
    { field: 'businessTime', title: '业务时间', align: 'center', widthWeight: 1.3 },
    { field: 'pharmacyLocationName', title: '药房', align: 'center', widthWeight: 1 },
    { field: 'patientName', title: '患者', align: 'center', widthWeight: 0.9 },
    { field: 'encounterNo', title: '就诊号', align: 'center', widthWeight: 1.1 },
    { field: 'feeNatureDisplay', title: '费用性质', align: 'center', widthWeight: 0.9 },
    { field: 'medTypeDisplay', title: '医疗类别', align: 'center', widthWeight: 0.9 },
  ];
  // 住院才有病区/床号列
  if (props.scope === 'inpatient') {
    cols.push(
      { field: 'wardName', title: '病区', align: 'center', widthWeight: 0.9 },
      { field: 'bedNo', title: '床号', align: 'center', widthWeight: 0.7 },
    );
  }
  cols.push(
    { field: 'medicationCode', title: '药品编码', align: 'center', widthWeight: 1 },
    { field: 'medicationName', title: '药品名称', align: 'center', widthWeight: 1.4 },
    { field: 'medicationSpec', title: '规格', align: 'center', widthWeight: 1 },
    { field: 'quantityDisplay', title: '数量', align: 'right', widthWeight: 1 },
    { field: 'purchaseUnitPrice', title: '进价单价', align: 'right', widthWeight: 0.9 },
    { field: 'purchaseAmount', title: '进价总价', align: 'right', widthWeight: 1 },
    { field: 'saleUnitPrice', title: '售价单价', align: 'right', widthWeight: 0.9 },
    { field: 'saleAmount', title: '售价总价', align: 'right', widthWeight: 1 },
    { field: 'lotNumber', title: '批号', align: 'center', widthWeight: 1 },
    { field: 'traceNo', title: '追溯码', align: 'center', widthWeight: 1 },
    {
      field: 'departmentName',
      title: departmentColumnLabel.value,
      align: 'center',
      widthWeight: 1,
    },
    { field: 'dispenserName', title: '发药人', align: 'center', widthWeight: 0.8 },
    { field: 'busNo', title: '业务单号', align: 'center', widthWeight: 1.2 },
    { field: 'prescriptionNo', title: '处方号', align: 'center', widthWeight: 1.1 },
  );
  return cols;
});

/** 打印偏好键：按 scope + 汇总/明细隔离。 */
const printReportCode = computed(
  () => `report-medication-dispense-${props.scope}-${isSummaryTab.value ? 'summary' : 'detail'}`,
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
      medicationCode: (value) => String(value ?? '').trim(),
      netQuantity: (value, row) => formatQtyWithUnit(value, String(row?.packageUnitName ?? '')),
      minUnitQuantity: (value, row) => formatQtyWithUnit(value, String(row?.minUnitName ?? '')),
      purchaseUnitPrice: (value, row) => formatPurchaseMoneyCell(value, row?.categoryCode),
      saleUnitPrice: (value) => formatMoneyCell(value),
      purchaseNetAmount: (value) => formatPurchaseMoneyCell(value),
      saleNetAmount: (value) => formatMoneyCell(value),
    }),
  );
}

/** 将当前页明细行映射为打印单元格。 */
function mapDetailPrintRows(source = detailRows.value): Record<string, string>[] {
  const catalog = printColumnCatalog.value;
  const fields = reportPrintFieldsOf(catalog);
  return source.map((row) =>
    mapReportPrintRow(
      {
        ...row,
        encounterNo: resolveEncounterNo(row),
      } as Record<string, unknown>,
      fields,
      {
        sourceText: (value, r) => String(value ?? r.sourceCode ?? '').trim(),
        encounterNo: (value) => {
          const text = String(value ?? '').trim();
          return text === '—' ? '' : text;
        },
        quantityDisplay: (_value, r) =>
          formatQtyWithUnit(r.quantity, r.unitText as string | undefined),
        purchaseUnitPrice: (value, row) => formatPurchaseMoneyCell(value, row?.categoryCode),
        purchaseAmount: (value, row) => formatPurchaseMoneyCell(value, row?.categoryCode),
        saleUnitPrice: (value) => formatMoneyCell(value),
        saleAmount: (value) => formatMoneyCell(value),
        traceNo: (value) => (value === undefined || value === null ? '' : String(value)),
      },
    ),
  );
}

const { printFieldDialogRef, printSubmitting, handleOpenPrintFields, handlePrintCurrentPage } =
  useReportListPrint({
    getReportCode: () => printReportCode.value,
    getColumnCatalog: () => printColumnCatalog.value,
    getReportTitle: () => printReportTitle.value,
    getPrintRows: () => (isSummaryTab.value ? mapSummaryPrintRows() : mapDetailPrintRows()),
    fetchAllPrintRows: fetchAllDispensePrintRows,
    hasPrintableData: () =>
      isSummaryTab.value
        ? summaryRows.value.length > 0
        : total.value > 0 || detailRows.value.length > 0,
  });

/** 跨页打印：汇总已一次返回；明细按当前筛选分页拉全量。 */
async function fetchAllDispensePrintRows() {
  if (isSummaryTab.value) {
    return { rows: mapSummaryPrintRows(), truncated: false };
  }
  const filters = buildFilterPayload();
  if (!filters) {
    return { rows: [], truncated: false };
  }
  const collected = await collectPagedReportRows({
    fetchPage: async (pageNo, pageSize) => {
      const page = await pageMedicationDispenseReportDetailApi(props.scope, {
        ...filters,
        pageNo,
        pageSize,
      });
      return { rows: page.content ?? [], total: Number(page.totalCount ?? 0) };
    },
  });
  return { rows: mapDetailPrintRows(collected.rows), truncated: collected.truncated };
}

const { run: runInit, loading: initLoading } = useRequest(
  () => loadMedicationDispenseReportPageInitApi(props.scope),
  {
    manual: true,
    onSuccess: (data) => {
      pharmacyOptions.value = optionDtosToSelectItems(data?.pharmacyOptions);
      categoryOptions.value = optionDtosToSelectItems(data?.categoryOptions);
      medicinePropertyOptions.value = optionDtosToSelectItems(data?.medicinePropertyOptions);
      practitionerOptions.value = optionDtosToSelectItems(data?.practitionerOptions);
      recordTypeOptions.value = optionDtosToSelectItems(data?.recordTypeOptions);
      summaryDimensionOptions.value = optionDtosToSelectItems(data?.summaryDimensionOptions);
      sourceOptions.value = normalizeSourceOptions(optionDtosToSelectItems(data?.sourceOptions));
      organizationOptions.value = optionDtosToSelectItems(data?.organizationOptions);
      feeNatureOptions.value = optionDtosToSelectItems(data?.feeNatureOptions);
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
      const firstSource = sourceOptions.value[0]?.value;
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
  (body) => summarizeMedicationDispenseReportApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      summaryRows.value = data?.rows ?? [];
      totalQuantity.value = data?.totalQuantity ?? '';
      // Type B：进价列角=当次汇总行净进价加总（与查询同筛选、与表内列一致）
      purchaseTotalAmount.value = sumReportPurchaseCornerFromRows(
        summaryRows.value as Array<Record<string, unknown>>,
        'purchaseNetAmount',
      );
      saleTotalAmount.value = data?.saleTotalAmount ?? '';
      total.value = summaryRows.value.length;
    },
  },
);

const { run: runPageDetail, loading: detailLoading } = useRequest(
  (body) => pageMedicationDispenseReportDetailApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      detailRows.value = data?.content ?? [];
      total.value = Number(data?.totalCount ?? 0);
      // 明细分页不返回合计；页脚金额/数量合计置空避免误导
      totalQuantity.value = '';
      purchaseTotalAmount.value = '';
      saleTotalAmount.value = '';
    },
  },
);

const { run: runExport, loading: exportLoading } = useRequest(
  (body) => exportMedicationDispenseReportApi(props.scope, body),
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

const tableLoading = computed(
  () => initLoading.value || summarizeLoading.value || detailLoading.value || printSubmitting.value,
);

/** 下拉选项转为 ElSelect 结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 金额单元格：空占位，其余按展示刻度（售价/结算两位）。 */
function formatMoneyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return formatMoneyDisplay(value);
}

/** 进价侧金额：中草药三位（有分类码）或最多三位去尾零。 */
function formatPurchaseMoneyCell(value: unknown, categoryCode?: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return formatPurchaseMoneyDisplay(value, categoryCode) || '—';
}

/** 数量单元格：整数不补小数位（如 1,530）。 */
function formatQtyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  const n = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(n)) {
    return String(value);
  }
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  });
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

/** 明细/汇总行是否退药冲减（数量或金额为负）。 */
function isMedicationDispenseRefundRow(
  row: MedicationDispenseDetailRowDto | MedicationDispenseSummaryRowDto,
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
 * 退药冲减整行标红；账批≠实发批加 warning 底色（金额口径不变）。
 *
 * @param row 明细或汇总行
 */
function resolveDispenseRowClassName({
  row,
}: {
  row: MedicationDispenseDetailRowDto | MedicationDispenseSummaryRowDto;
}): string {
  const detail = row as MedicationDispenseDetailRowDto;
  return resolveReportDetailRowClassName({
    isRefund: isMedicationDispenseRefundRow(row),
    lotMismatch: detail.lotMismatch === true,
  });
}

/**
 * 批号列展示：不一致时附收费批号提示（质检；不改正额）。
 *
 * @param row 明细分页行
 */
function formatLotNumberCell(row: MedicationDispenseDetailRowDto): string {
  const dispenseLot = row.lotNumber?.trim() || '—';
  // 账批≠实发批：主列仍是实发批，括号展示账批便于排查
  if (row.lotMismatch === true && row.chargeLotNumber?.trim()) {
    return `${dispenseLot}（账批 ${row.chargeLotNumber.trim()}）`;
  }
  return dispenseLot;
}

/** 来源 Tag 样式（按 code，不用展示文案分支）。 */
function resolveSourceTagType(sourceCode?: string): 'info' | 'success' | 'warning' {
  if (sourceCode === SOURCE_CODE_DEPT_ISSUE) {
    return 'warning';
  }
  if (sourceCode === SOURCE_CODE_PATIENT_DISPENSE) {
    return 'success';
  }
  return 'info';
}

/** 明细行稳定 key（两路并集）。 */
function resolveDetailRowKey(row: MedicationDispenseDetailRowDto) {
  return [
    row.sourceCode ?? '',
    row.dispenseId ?? '',
    row.supplyRequestId ?? '',
    row.medicationId ?? '',
    row.businessTime ?? '',
  ].join('|');
}

/** 汇总行 key。 */
function resolveSummaryRowKey(row: MedicationDispenseSummaryRowDto) {
  if (row.dimensionKey) {
    return row.dimensionKey;
  }
  return [
    row.summaryDimension ?? '',
    row.locationId ?? '',
    row.medicationId ?? '',
    row.categoryCode ?? '',
    row.organizationId ?? '',
  ].join('|');
}

/** 就诊号：门诊用 encounterBusNo，住院用 inpatientNo。 */
function resolveEncounterNo(row: MedicationDispenseDetailRowDto) {
  if (props.scope === 'inpatient') {
    return row.inpatientNo || '—';
  }
  return row.encounterBusNo || '—';
}

/** 校验业务时间并组装筛选体。 */
function buildFilterPayload() {
  const dateRange = toReportApiDateTimeRangePrecise(queryParams.dateFrom, queryParams.dateTo);
  const dispenseDateFrom = dateRange.start;
  const dispenseDateTo = dateRange.end;
  if (!dispenseDateFrom || !dispenseDateTo) {
    ElMessage.warning('请选择业务时间');
    return null;
  }
  if (dayjs(dispenseDateFrom).isAfter(dayjs(dispenseDateTo))) {
    ElMessage.warning('开始时间不能晚于结束时间');
    return null;
  }
  return {
    dispenseDateFrom,
    dispenseDateTo,
    recordTypeCode:
      queryParams.recordTypeCode ||
      (recordTypeOptions.value[0]?.value ? String(recordTypeOptions.value[0].value) : undefined),
    pharmacyLocationId: preserveApiIdForRequest(queryParams.pharmacyLocationId),
    medicationCategoryCodes:
      queryParams.medicationCategoryCodes.length > 0
        ? [...queryParams.medicationCategoryCodes]
        : undefined,
    dispenserPractitionerId: preserveApiIdForRequest(queryParams.dispenserPractitionerId),
    drugKeyword: queryParams.drugKeyword.trim() || undefined,
    patientKeyword: queryParams.patientKeyword.trim() || undefined,
    sourceCode: queryParams.sourceCode || SOURCE_CODE_PATIENT_DISPENSE,
    feeNatureContractNo: queryParams.feeNatureContractNo || undefined,
    organizationId: preserveApiIdForRequest(queryParams.organizationId),
    keyDrugType: queryParams.keyDrugType || undefined,
  };
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
      summaryDimension: queryParams.summaryDimension || SUMMARY_DIMENSION_PHARMACY,
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
  // 页面有什么列就导出什么；quantityDisplay/netQuantity 由 util 展开为单位分列
  const exportColumns = buildExcelExportColumns(printColumnCatalog.value);
  await runExport({
    ...filters,
    presentationMode: queryParams.presentationMode,
    summaryDimension: isSummaryTab.value
      ? queryParams.summaryDimension || SUMMARY_DIMENSION_PHARMACY
      : undefined,
    exportColumns,
  });
}

/** 重置为初始化默认值。 */
async function handleReset() {
  queryParams.pageNo = 1;
  queryParams.pageSize = 20;
  queryParams.recordTypeCode = undefined;
  queryParams.pharmacyLocationId = undefined;
  queryParams.medicationCategoryCodes = [];
  queryParams.keyDrugType = undefined;
  queryParams.dispenserPractitionerId = undefined;
  queryParams.drugKeyword = '';
  queryParams.patientKeyword = '';
  queryParams.summaryDimension = String(
    summaryDimensionOptions.value[0]?.value ?? SUMMARY_DIMENSION_PHARMACY,
  );
  queryParams.sourceCode = String(sourceOptions.value[0]?.value ?? SOURCE_CODE_PATIENT_DISPENSE);
  queryParams.feeNatureContractNo = undefined;
  queryParams.organizationId = undefined;
  queryParams.presentationMode = PRESENTATION_MODE_DETAIL;
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
        <ReportQueryBar :model="queryBarModel">
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
                  v-for="item in toSelectOptions(sourceOptions)"
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
            <ElFormItem label="药房">
              <ElSelect
                v-model="queryParams.pharmacyLocationId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(pharmacyOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="药品类型">
              <ElSelect
                v-model="queryParams.medicationCategoryCodes"
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
            <ElFormItem label="药品性质">
              <ElSelect
                v-model="queryParams.keyDrugType"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(medicinePropertyOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="发药人">
              <ElSelect
                v-model="queryParams.dispenserPractitionerId"
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
            <ElFormItem label="药品">
              <ElInput
                v-model="queryParams.drugKeyword"
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
                :placeholder="scope === 'outpatient' ? '姓名/门诊号' : '姓名/住院号'"
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
            <ElFormItem v-if="isSummaryTab" label="汇总维度">
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
          </template>
          <template #actions>
            <ElButton type="primary" :icon="Search" :loading="tableLoading" @click="handleQuery">
              搜索
            </ElButton>
            <ElButton :icon="Refresh" :loading="tableLoading" @click="handleReset"> 重置 </ElButton>
          </template>
        </ReportQueryBar>
      </template>

      <template #toolbar>
        <ReportListToolbar
          :title="`${pageTitle} · ${isSummaryTab ? '汇总' : '明细'}`"
          :total="isSummaryTab ? summaryRows.length : total"
        >
          <template #actions>
            <ElRadioGroup v-model="queryParams.presentationMode" @change="handleTabChange">
              <ElRadioButton :value="PRESENTATION_MODE_SUMMARY">汇总</ElRadioButton>
              <ElRadioButton :value="PRESENTATION_MODE_DETAIL">明细</ElRadioButton>
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
              :row-class-name="resolveDispenseRowClassName"
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
                v-if="showSummaryItemExtraColumns"
                prop="medicationCode"
                label="药品编码"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                label="数量"
                width="140"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ formatQtyWithUnit(row.netQuantity, row.packageUnitName) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="showSummaryItemExtraColumns"
                label="最小数量"
                width="140"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ formatQtyWithUnit(row.minUnitQuantity, row.minUnitName) }}
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
                  {{ formatPurchaseMoneyCell(row.purchaseUnitPrice, row.categoryCode) }}
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
                  {{ formatPurchaseMoneyCell(row.purchaseNetAmount) }}
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
              :row-class-name="resolveDispenseRowClassName"
            >
              <ElTableColumn label="来源" width="100" align="center" header-align="center">
                <template #default="{ row }">
                  <ElTag :type="resolveSourceTagType(row.sourceCode)" effect="light">
                    {{ row.sourceText || row.sourceCode || '—' }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="recordTypeText"
                label="类型"
                width="88"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="businessTime"
                label="业务时间"
                width="170"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="pharmacyLocationName"
                label="药房"
                width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="patientName"
                label="患者"
                width="90"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                :label="encounterNoLabel"
                width="130"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ resolveEncounterNo(row) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="feeNatureDisplay"
                label="费用性质"
                width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="medTypeDisplay"
                label="医疗类别"
                width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <template v-if="scope === 'inpatient'">
                <ElTableColumn
                  prop="wardName"
                  label="病区"
                  width="100"
                  align="center"
                  header-align="center"
                  show-overflow-tooltip
                />
                <ElTableColumn
                  prop="bedNo"
                  label="床号"
                  width="72"
                  align="center"
                  header-align="center"
                  show-overflow-tooltip
                />
              </template>
              <ElTableColumn
                prop="medicationCode"
                label="药品编码"
                width="110"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="medicationName"
                label="药品名称"
                min-width="150"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="medicationSpec"
                label="规格"
                width="110"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                label="数量"
                width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ formatQtyWithUnit(row.quantity, row.unitText) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="进价单价"
                width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatPurchaseMoneyCell(row.purchaseUnitPrice, row.categoryCode) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="进价总价"
                width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatPurchaseMoneyCell(row.purchaseAmount, row.categoryCode) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="售价单价"
                width="100"
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
                width="110"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatMoneyCell(row.saleAmount) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="批号"
                width="140"
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
              <ElTableColumn label="追溯码" width="120" align="center" header-align="center">
                <template #default="{ row }">
                  <TraceCodeTooltip :value="row.traceNo" />
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="departmentName"
                :label="departmentColumnLabel"
                width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="dispenserName"
                label="发药人"
                width="90"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="busNo"
                label="业务单号"
                width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="prescriptionNo"
                label="处方号"
                width="130"
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
