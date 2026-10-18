<!--
  门诊/住院费用明细报表：汇总/明细双 Tab；只读查询与导出（AS-RPT-008 / AS-RPT-009）。
-->
<script setup lang="ts">
import type { FeeDetailTableRow } from './flatten-encounter-blocks';

import type {
  FeeDetailAmountSummaryDto,
  FeeDetailReportScope,
  FeeDetailReportSummaryFooterDto,
  FeeDetailReportSummaryRowDto,
  LoadFeeDetailReportPageInitResponse,
  PresentationModeCode,
} from '#/api/report/fee-detail';
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
  exportFeeDetailReportApi,
  LINE_MERGE_MODE_BY_DEFINITION,
  LINE_MERGE_MODE_LINE,
  loadFeeDetailReportPageInitApi,
  pageFeeDetailReportApi,
  PRESENTATION_MODE_DETAIL,
  PRESENTATION_MODE_SUMMARY,
  SETTLEMENT_STATUS_ALL,
  SETTLEMENT_STATUS_PENDING,
  SETTLEMENT_STATUS_SETTLED,
  SUMMARY_DIMENSION_FIN_TYPE,
  SUMMARY_DIMENSION_ITEM,
  summarizeFeeDetailPurchaseCornerApi,
  summarizeFeeDetailReportApi,
} from '#/api/report/fee-detail';
import { useRequest } from '#/hooks/use-request';
import { buildExcelExportColumns } from '#/utils/export/build-excel-export-columns';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import {
  formatMoneyYuan,
  formatPurchaseMoneyYuan,
  formatPurchaseUnitPriceDisplay,
} from '#/utils/format/money-scale';
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
  REPORT_PRINT_ALL_MAX_ROWS,
  reportPrintFieldsOf,
  ReportTableScroll,
  resolveReportRefundRowClassName,
  sumReportPurchaseCornerFromRows,
  useReportListPrint,
} from '#/views/report/shared';
import {
  buildReportDateTimeRangeShortcuts,
  normalizeReportDateTime,
  toReportApiDateTimeRangePrecise,
} from '#/views/report/shared/report-date-range-shortcuts';

import { FEE_DETAIL_SUMMARY_DISPLAY, SUMMARY_FOOTER_DISPLAY } from './constants';
import { flattenEncounterBlocksToTableRows } from './flatten-encounter-blocks';

defineOptions({ name: 'FeeDetailReportPage' });

/** 明细表一列：表头/打印/导出共用；优惠/实收仅已配置有效折扣时插入。 */
interface FeeDetailDynamicColumn {
  prop: string;
  /** 打印字段；缺省用 prop（来源列打印用 tradeDirectionLabel）。 */
  printField?: string;
  label: string;
  width?: number;
  minWidth?: number;
  align: 'center' | 'right';
  widthWeight: number;
  className?: string;
  cellKind: 'text' | 'quantity' | 'trade' | 'purchaseUnit' | 'purchaseMoney' | 'saleMoney';
}

/** 汇总表一列：与打印目录同序。 */
interface FeeDetailSummaryColumn {
  prop: string;
  label: string;
  width?: number;
  minWidth?: number;
  align: 'center' | 'right';
  widthWeight: number;
  className?: string;
  cellKind: 'dimension' | 'qty' | 'purchaseMoney' | 'saleMoney';
}

interface Props {
  /** 门诊或住院费用明细报表场景 */
  scope: FeeDetailReportScope;
}

interface QueryFormState {
  pageNo: number;
  pageSize: number;
  dateFrom: string;
  dateTo: string;
  tradeDirection: string;
  /** 住院结算状态：ALL / SETTLED / PENDING_SETTLEMENT。 */
  settlementStatus: string;
  requestingOrgId?: string;
  feeCategoryCodes?: string[];
  subCategoryCodes?: string[];
  patientKeyword?: string;
  productNameKeyword?: string;
  productCodeKeyword?: string;
  cashierId?: string;
  feeNatureContractNo?: string;
  presentationMode: PresentationModeCode;
  /** 汇总维度（仅汇总 Tab）。 */
  summaryDimension: string;
  /**
   * 按患者维度合并（仅住院明细）：true → lineMergeMode=BY_DEFINITION（同就诊内同产品品规净合计）。
   */
  mergeByPatientDefinition: boolean;
}

const props = defineProps<Props>();

const pageInit = ref<LoadFeeDetailReportPageInitResponse | null>(null);
/**
 * init 完成后再挂明细 ElTable。默认明细 Tab 会在 init 前先挂表，
 * ElTableColumn v-if 为 false 时 Element Plus 不会后补优惠/实收列。
 */
const pageInitReady = ref(false);
/** 已配置有效折扣（ACTIVE 配置&gt;0）；init / 明细分页均可回写。 */
const discountFeatureEnabled = ref(false);
const summaryRows = ref<FeeDetailReportSummaryRowDto[]>([]);
const summaryFooter = ref<FeeDetailReportSummaryFooterDto | undefined>();
const tableData = ref<FeeDetailTableRow[]>([]);
const globalSummary = ref<FeeDetailAmountSummaryDto | undefined>();
/** 明细分页 total（就诊块数）。 */
const detailTotal = ref(0);
/** 汇总 Tab 行数（与明细分页 total 隔离，避免切 Tab 串扰）。 */
const summaryTotal = ref(0);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const queryParams = reactive<QueryFormState>({
  pageNo: 1,
  pageSize: 10,
  dateFrom: '',
  dateTo: '',
  tradeDirection: 'ALL',
  settlementStatus: SETTLEMENT_STATUS_ALL,
  requestingOrgId: undefined,
  feeCategoryCodes: undefined,
  subCategoryCodes: undefined,
  patientKeyword: undefined,
  productNameKeyword: undefined,
  productCodeKeyword: undefined,
  cashierId: undefined,
  feeNatureContractNo: undefined,
  /** 进入页默认明细 Tab（门/住院统一）。 */
  presentationMode: PRESENTATION_MODE_DETAIL,
  summaryDimension: SUMMARY_DIMENSION_FIN_TYPE,
  mergeByPatientDefinition: false,
});

const pageTitle = computed(() => (props.scope === 'outpatient' ? '门诊费用明细' : '住院费用明细'));

/** 住院含待结：时间轴为已结结算时 / 待结发生时；门诊仍称结算时间。 */
const businessDateLabel = computed(() => (props.scope === 'inpatient' ? '业务时间' : '结算时间'));

const defaultExportFileName = computed(() =>
  props.scope === 'outpatient' ? '门诊费用明细.xlsx' : '住院费用明细.xlsx',
);

const encounterNoLabel = computed(() => (props.scope === 'outpatient' ? '门诊号' : '住院号'));

const departmentFilterLabel = '开立科室';
/** 明细块头科室列：开立科室（后端 requestingOrgName，缺省回落就诊科室）。 */
const departmentColumnLabel = '开立科室';

const patientKeywordPlaceholder = computed(() =>
  props.scope === 'outpatient' ? '姓名 / 门诊号' : '姓名 / 住院号',
);

const isSummaryTab = computed(() => queryParams.presentationMode === PRESENTATION_MODE_SUMMARY);

/** 住院明细且开启「按患者维度合并」。 */
const isPatientDefinitionMerge = computed(
  () => props.scope === 'inpatient' && !isSummaryTab.value && queryParams.mergeByPatientDefinition,
);

const dateTimeRangeShortcuts = buildReportDateTimeRangeShortcuts();
/** 日历点选日期时默认起 00:00:00、止 23:59:59（快捷项已自带时分秒）。 */
const dateTimeDefaultTime: [Date, Date] = [
  new Date(2000, 0, 1, 0, 0, 0),
  new Date(2000, 0, 1, 23, 59, 59),
];

/** 结算/业务时间与 datetimerange 双向绑定（界面精确到秒）。 */
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

const tradeDirectionOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.tradeDirectionOptions ?? [],
);

/** 住院结算状态筛：优先用 init；缺省本地兜底，保证筛选可见。 */
const settlementStatusOptions = computed<ApiOptionItem[]>(() => {
  const fromInit = pageInit.value?.settlementStatusOptions ?? [];
  if (fromInit.length > 0) {
    return fromInit;
  }
  if (props.scope !== 'inpatient') {
    return [];
  }
  return [
    { label: '全部', value: SETTLEMENT_STATUS_ALL },
    { label: '已结算', value: SETTLEMENT_STATUS_SETTLED },
    { label: '待结算', value: SETTLEMENT_STATUS_PENDING },
  ];
});

const showSettlementStatusFilter = computed(
  () => props.scope === 'inpatient' && settlementStatusOptions.value.length > 0,
);

const registrationDepartmentOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.registrationDepartmentOptions ?? [],
);

const feeCategoryOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.feeCategoryOptions ?? [],
);

const cashierOptions = computed<ApiOptionItem[]>(() => pageInit.value?.cashierOptions ?? []);

const feeNatureOptions = computed<ApiOptionItem[]>(() => pageInit.value?.feeNatureOptions ?? []);

const summaryDimensionOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.summaryDimensionOptions ?? [],
);

/**
 * 已配置有效折扣（ACTIVE 配置&gt;0）时展示优惠/实收列。
 * 只认配置，不按行金额猜。
 */
const showDiscountAmountColumns = computed(() => discountFeatureEnabled.value);

/**
 * 把接口折扣显隐落到本地（true / 'true' / 1 视为已配置）。
 *
 * @param value init 或明细分页的 discountFeatureEnabled
 */
function applyDiscountFeatureEnabled(value: unknown) {
  // 已配置有效折扣：打开优惠/实收列
  if (value === true || value === 'true' || value === 'TRUE' || value === 1 || value === '1') {
    discountFeatureEnabled.value = true;
    return;
  }
  // 明确未配置：关闭列，避免上一轮 true 残留
  if (value === false || value === 'false' || value === 'FALSE' || value === 0 || value === '0') {
    discountFeatureEnabled.value = false;
  }
}

/** 项目类别：合并药品/诊疗/耗材 fin_type 字典（按 value 去重）。 */
const subCategoryOptions = computed<ApiOptionItem[]>(() => {
  const init = pageInit.value;
  if (!init) {
    return [];
  }
  const merged: ApiOptionItem[] = [
    ...(init.medicationSubCategoryOptions ?? []),
    ...(init.activitySubCategoryOptions ?? []),
    ...(init.deviceSubCategoryOptions ?? []),
  ];
  const seen = new Set<string>();
  // 按 value 去重，避免三类字典重叠导致重复选项。
  return merged.filter((item) => {
    const value = String(item.value ?? '');
    if (!value || seen.has(value)) {
      return false;
    }
    seen.add(value);
    return true;
  });
});

const showFeeCategoryFilter = computed(() => feeCategoryOptions.value.length > 0);

/** ReportQueryBar 需要 Record 形态的 model。 */
const queryBarModel = computed(() => queryParams as Record<string, unknown>);

const footerSummaryItems = computed(() => {
  if (isSummaryTab.value) {
    const footer = summaryFooter.value;
    if (!footer) {
      return SUMMARY_FOOTER_DISPLAY.map((item) => ({
        label: item.label,
        value: '—',
      }));
    }
    return SUMMARY_FOOTER_DISPLAY.map((item) => {
      const raw = footer[item.key];
      if (item.kind === 'qty') {
        return {
          label: item.label,
          value: raw === undefined || raw === null || raw === '' ? '—' : formatQuantity(raw),
        };
      }
      return {
        label: item.label,
        value:
          item.key === 'purchaseAmountTotal' ? formatPurchaseMoneyCell(raw) : formatMoneyCell(raw),
        primary: item.key === 'saleAmountTotal',
        prefix: '¥',
      };
    });
  }
  const summary = globalSummary.value;
  if (!summary) {
    return [{ label: '明细行数', value: detailTotal.value }];
  }
  return FEE_DETAIL_SUMMARY_DISPLAY.filter((item) => {
    if (item.key === 'discountAmountTotal' || item.key === 'actualReceivedAmountTotal') {
      return showDiscountAmountColumns.value;
    }
    return true;
  }).map((item) => ({
    label: item.label,
    value:
      item.key === 'purchaseAmountTotal'
        ? formatPurchaseMoneyCell(summary[item.key])
        : formatMoneyCell(summary[item.key]),
  }));
});

/** 汇总 Tab 仅「品规」维展示进/售单价。 */
const showSummaryUnitPriceColumns = computed(
  () => queryParams.summaryDimension === SUMMARY_DIMENSION_ITEM,
);

/** 汇总表列（品规维才出单价；不含优惠/实收）。 */
const summaryTableColumns = computed<FeeDetailSummaryColumn[]>(() => {
  const cols: FeeDetailSummaryColumn[] = [
    {
      prop: 'dimensionName',
      label: '维度',
      minWidth: 160,
      align: 'center',
      widthWeight: 1.4,
      cellKind: 'dimension',
    },
    {
      prop: 'quantityTotal',
      label: '数量',
      minWidth: 100,
      align: 'right',
      widthWeight: 0.9,
      className: REPORT_TABLE_NUM_CELL,
      cellKind: 'qty',
    },
  ];
  // 品规维才展示进/售单价，其它维只有合计
  if (showSummaryUnitPriceColumns.value) {
    cols.push(
      {
        prop: 'purchaseUnitPrice',
        label: '进价单价',
        width: 110,
        align: 'right',
        widthWeight: 1,
        className: `${REPORT_TABLE_NUM_CELL} ${REPORT_TABLE_AMOUNT_CELL}`,
        cellKind: 'purchaseMoney',
      },
      {
        prop: 'saleUnitPrice',
        label: '售价单价',
        width: 110,
        align: 'right',
        widthWeight: 1,
        className: REPORT_TABLE_NUM_CELL,
        cellKind: 'saleMoney',
      },
    );
  }
  cols.push(
    {
      prop: 'purchaseAmountTotal',
      label: '进价合计',
      width: 120,
      align: 'right',
      widthWeight: 1,
      className: `${REPORT_TABLE_NUM_CELL} ${REPORT_TABLE_AMOUNT_CELL}`,
      cellKind: 'purchaseMoney',
    },
    {
      prop: 'saleAmountTotal',
      label: '售价合计',
      width: 120,
      align: 'right',
      widthWeight: 1,
      className: REPORT_TABLE_NUM_CELL,
      cellKind: 'saleMoney',
    },
  );
  return cols;
});

/** 汇总 Tab 可打印列（与汇总表同序）。 */
const summaryPrintColumns = computed<ReportPrintColumnDef[]>(() =>
  summaryTableColumns.value.map((col) => ({
    field: col.prop,
    title: col.label,
    align: col.align,
    widthWeight: col.widthWeight,
  })),
);

/**
 * 明细表列：售价/优惠/实收紧挨着往前，进价放最后。
 * 有有效折扣配置才插入优惠、实收，不写死在模板。
 */
const detailTableColumns = computed<FeeDetailDynamicColumn[]>(() => {
  const cols: FeeDetailDynamicColumn[] = [
    {
      prop: 'busNo',
      label: encounterNoLabel.value,
      width: 120,
      align: 'center',
      widthWeight: 1.1,
      cellKind: 'text',
    },
    {
      prop: 'patientName',
      label: '患者',
      width: 90,
      align: 'center',
      widthWeight: 0.9,
      cellKind: 'text',
    },
    {
      prop: 'registrationDepartmentName',
      label: departmentColumnLabel,
      width: 100,
      align: 'center',
      widthWeight: 1,
      cellKind: 'text',
    },
    {
      prop: 'encounterDate',
      label: '就诊日期',
      width: 110,
      align: 'center',
      widthWeight: 1,
      cellKind: 'text',
    },
    {
      prop: 'feeNatureDisplay',
      label: '费用性质',
      width: 100,
      align: 'center',
      widthWeight: 0.9,
      cellKind: 'text',
    },
    {
      prop: 'medTypeDisplay',
      label: '医疗类别',
      width: 100,
      align: 'center',
      widthWeight: 0.9,
      cellKind: 'text',
    },
    {
      prop: 'subCategoryDisplay',
      label: '项目类别',
      width: 88,
      align: 'center',
      widthWeight: 0.9,
      cellKind: 'text',
    },
    {
      prop: 'productName',
      label: '项目名称',
      minWidth: 120,
      align: 'center',
      widthWeight: 1.3,
      cellKind: 'text',
    },
    {
      prop: 'productCode',
      label: '项目编码',
      width: 100,
      align: 'center',
      widthWeight: 1,
      cellKind: 'text',
    },
    {
      prop: 'quantityDisplay',
      label: '数量',
      width: 88,
      align: 'right',
      widthWeight: 0.9,
      className: REPORT_TABLE_NUM_CELL,
      cellKind: 'quantity',
    },
  ];
  // 合并态隐藏开单/执行/收费人与业务时间（逐条差异列）
  if (!isPatientDefinitionMerge.value) {
    cols.push(
      {
        prop: 'orderingDoctorName',
        label: '开单医生',
        width: 88,
        align: 'center',
        widthWeight: 0.9,
        cellKind: 'text',
      },
      {
        prop: 'performerName',
        label: '执行人',
        width: 88,
        align: 'center',
        widthWeight: 0.8,
        cellKind: 'text',
      },
      {
        prop: 'cashierName',
        label: '收费人',
        width: 88,
        align: 'center',
        widthWeight: 0.8,
        cellKind: 'text',
      },
      {
        prop: 'billDate',
        label: businessDateLabel.value,
        width: 160,
        align: 'center',
        widthWeight: 1.2,
        cellKind: 'text',
      },
    );
  }
  cols.push(
    {
      prop: 'tradeDirection',
      printField: 'tradeDirectionLabel',
      label: '来源',
      width: 72,
      align: 'center',
      widthWeight: 0.7,
      cellKind: 'trade',
    },
    {
      prop: 'insuranceLevelDisplay',
      label: '医保等级',
      width: 88,
      align: 'center',
      widthWeight: 0.8,
      cellKind: 'text',
    },
    {
      prop: 'saleUnitPrice',
      label: '售价单价',
      width: 96,
      align: 'right',
      widthWeight: 0.9,
      className: REPORT_TABLE_NUM_CELL,
      cellKind: 'saleMoney',
    },
    {
      prop: 'saleAmount',
      label: '售价总价',
      width: 96,
      align: 'right',
      widthWeight: 0.9,
      className: REPORT_TABLE_NUM_CELL,
      cellKind: 'saleMoney',
    },
  );
  // 有有效折扣配置：优惠/实收紧跟售价，避免挤在进价后面要横滚才看见
  if (showDiscountAmountColumns.value) {
    cols.push(
      {
        prop: 'discountAmount',
        label: '优惠金额',
        width: 96,
        align: 'right',
        widthWeight: 0.9,
        className: REPORT_TABLE_NUM_CELL,
        cellKind: 'saleMoney',
      },
      {
        prop: 'actualReceivedAmount',
        label: '实收金额',
        width: 96,
        align: 'right',
        widthWeight: 0.9,
        className: REPORT_TABLE_NUM_CELL,
        cellKind: 'saleMoney',
      },
    );
  }
  cols.push(
    {
      prop: 'purchaseUnitPrice',
      label: '进价单价',
      width: 96,
      align: 'right',
      widthWeight: 0.9,
      className: REPORT_TABLE_NUM_CELL,
      cellKind: 'purchaseUnit',
    },
    {
      prop: 'purchaseAmount',
      label: '进价总价',
      width: 96,
      align: 'right',
      widthWeight: 0.9,
      className: `${REPORT_TABLE_NUM_CELL} ${REPORT_TABLE_AMOUNT_CELL}`,
      cellKind: 'purchaseMoney',
    },
  );
  return cols;
});

/** 明细表 :key：列清单变化时整表重挂，避免 ElTable 不刷新表头。 */
const detailTableColumnKey = computed(() =>
  detailTableColumns.value.map((col) => col.prop).join('|'),
);

/** 明细 Tab 可打印列（与当前表明细列对齐）。 */
const DETAIL_PRINT_COLUMNS = computed<ReportPrintColumnDef[]>(() =>
  detailTableColumns.value.map((col) => ({
    field: col.printField ?? col.prop,
    title: col.label,
    align: col.align,
    widthWeight: col.widthWeight,
  })),
);

/** 当前 Tab 打印列目录。 */
const printColumnCatalog = computed<ReportPrintColumnDef[]>(() =>
  isSummaryTab.value ? summaryPrintColumns.value : DETAIL_PRINT_COLUMNS.value,
);

/** 打印偏好键：按 scope + 汇总/明细隔离。 */
const printReportCode = computed(
  () => `report-fee-detail-${props.scope}-${isSummaryTab.value ? 'summary' : 'detail'}`,
);

/** 打印单据标题（含当前 Tab）。 */
const printReportTitle = computed(
  () => `${pageTitle.value} · ${isSummaryTab.value ? '汇总' : '明细'}`,
);

/** 将当前汇总行映射为打印单元格。 */
function mapSummaryPrintRows(): Record<string, string>[] {
  const fields = reportPrintFieldsOf(summaryPrintColumns.value);
  // 只打当前汇总视图行
  return summaryRows.value.map((row) =>
    mapReportPrintRow(
      {
        ...row,
        dimensionName: row.dimensionName || row.finTypeDisplay || '',
      } as Record<string, unknown>,
      fields,
      {
        quantityTotal: (value) => formatQuantity(value),
        purchaseUnitPrice: (value) => formatPurchaseMoneyCell(value),
        saleUnitPrice: (value) => formatMoneyCell(value),
        purchaseAmountTotal: (value) => formatPurchaseMoneyCell(value),
        saleAmountTotal: (value) => formatMoneyCell(value),
      },
    ),
  );
}

/** 将当前页明细行映射为打印单元格。 */
function mapDetailPrintRows(source = tableData.value): Record<string, string>[] {
  const fields = reportPrintFieldsOf(DETAIL_PRINT_COLUMNS.value);
  return source.map((row) =>
    mapReportPrintRow(row as Record<string, unknown>, fields, {
      quantityDisplay: (_value, r) =>
        formatQuantityWithUnit(r.quantityValue, r.quantityUnitDisplay),
      tradeDirectionLabel: (_value, r) => {
        if (r.rowType === 'subtotal') {
          return '—';
        }
        return resolveTradeDirectionLabel(r.tradeDirection as string | undefined);
      },
      purchaseUnitPrice: (value) => formatPurchaseUnitPriceCell(value),
      purchaseAmount: (value) => formatPurchaseMoneyCell(value),
      saleUnitPrice: (value) => formatMoneyCell(value),
      saleAmount: (value) => formatMoneyCell(value),
      discountAmount: (value) => formatMoneyCell(value),
      actualReceivedAmount: (value) => formatMoneyCell(value),
    }),
  );
}

const { printFieldDialogRef, printSubmitting, handleOpenPrintFields, handlePrintCurrentPage } =
  useReportListPrint({
    getReportCode: () => printReportCode.value,
    getColumnCatalog: () => printColumnCatalog.value,
    getReportTitle: () => printReportTitle.value,
    getPrintRows: () => (isSummaryTab.value ? mapSummaryPrintRows() : mapDetailPrintRows()),
    fetchAllPrintRows: fetchAllFeePrintRows,
    hasPrintableData: () =>
      isSummaryTab.value
        ? summaryRows.value.length > 0
        : detailTotal.value > 0 || tableData.value.length > 0,
  });

/** 跨页打印：汇总已一次返回；明细按就诊块分页拉全量后再展平。 */
async function fetchAllFeePrintRows() {
  if (isSummaryTab.value) {
    return { rows: mapSummaryPrintRows(), truncated: false };
  }
  const filters = buildFilterPayload();
  if (!filters) {
    return { rows: [], truncated: false };
  }
  const collected = await collectPagedReportRows({
    fetchPage: async (pageNo, pageSize) => {
      const page = await pageFeeDetailReportApi(props.scope, {
        ...filters,
        pageNo,
        pageSize,
      });
      return { rows: page.content ?? [], total: Number(page.totalCount ?? 0) };
    },
  });
  const flattened = flattenEncounterBlocksToTableRows(collected.rows);
  const capped =
    flattened.length > REPORT_PRINT_ALL_MAX_ROWS
      ? flattened.slice(0, REPORT_PRINT_ALL_MAX_ROWS)
      : flattened;
  return {
    rows: mapDetailPrintRows(capped),
    truncated: collected.truncated || flattened.length > capped.length,
  };
}

const { run: runInit, loading: initLoading } = useRequest(
  () => loadFeeDetailReportPageInitApi(props.scope),
  { manual: true },
);

const { run: runSummarize, loading: summarizeLoading } = useRequest(
  (body) => summarizeFeeDetailReportApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      summaryRows.value = data?.rows ?? [];
      // Type B：页脚进价=当次汇总行加总，与表内进价同筛选同管线
      const footer = data?.footer ? { ...data.footer } : undefined;
      if (footer) {
        footer.purchaseAmountTotal = sumReportPurchaseCornerFromRows(
          summaryRows.value as Array<Record<string, unknown>>,
          'purchaseAmountTotal',
        );
      }
      summaryFooter.value = footer;
      // 汇总 Tab 独立 total，避免与明细分页 total 串扰
      summaryTotal.value = summaryRows.value.length;
    },
  },
);

/** 明细分页世代号：翻页/重查时丢弃过期进价列角回包。 */
const detailPageGeneration = ref(0);

const { run: runPurchaseCorner } = useRequest(
  (body) => summarizeFeeDetailPurchaseCornerApi(props.scope, body),
  { manual: true },
);

/** 本页明细行硬上限：按就诊块分页时长时段可展出数千行，超限截断防卡死。 */
const DETAIL_TABLE_ROW_HARD_LIMIT = 500;

/** 本页明细行软提示阈值：超过则提示缩窗或开启按患者维度合并。 */
const DETAIL_TABLE_ROW_SOFT_LIMIT = 300;

const { run: runPage, loading: pageLoading } = useRequest(
  (body) => pageFeeDetailReportApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      const flattened = flattenEncounterBlocksToTableRows(data.content ?? []);
      // 超硬限截断 DOM，避免浏览器假死；提示缩窗或开「按患者维度合并」
      if (flattened.length > DETAIL_TABLE_ROW_HARD_LIMIT) {
        tableData.value = flattened.slice(0, DETAIL_TABLE_ROW_HARD_LIMIT);
        ElMessage.warning(
          `本页明细约 ${flattened.length} 行，已截断展示前 ${DETAIL_TABLE_ROW_HARD_LIMIT} 行。请缩小业务时间或开启「按患者维度合并」。`,
        );
      } else {
        tableData.value = flattened;
        if (flattened.length > DETAIL_TABLE_ROW_SOFT_LIMIT) {
          ElMessage.warning(
            `本页明细约 ${flattened.length} 行，表格较重。建议缩小时间范围或开启「按患者维度合并」。`,
          );
        }
      }
      // total 为就诊块数（非明细行数）；须 Number 化，避免字符串导致分页失效
      detailTotal.value = Number(data.totalCount ?? 0) || 0;
      // 后端钳制越界页后回写实际 pageNo，前端同步避免空页
      if (data.pageNo != null && Number(data.pageNo) >= 1) {
        queryParams.pageNo = Number(data.pageNo);
      }
      // 先展示售价列角；进价合计另 IF 异步回填（null 时页脚显示 —）
      globalSummary.value = data.summary;
      // 分页回包再带折扣显隐，避免仅 init 丢失时明细列一直不挂
      applyDiscountFeatureEnabled(data.discountFeatureEnabled);
    },
  },
);

const { run: runExport, loading: exportLoading } = useRequest(
  (body) => exportFeeDetailReportApi(props.scope, body),
  {
    manual: true,
    onSuccess: (data) => {
      downloadBinaryExportFile(data, defaultExportFileName.value);
      const rowCount = data.rowCount ?? data.exportedRowCount ?? 0;
      if (data.truncated) {
        ElMessage.warning('导出数据已触达行数上限，部分记录未包含在文件中');
      } else {
        ElMessage.success(`导出成功，共 ${rowCount} 行`);
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
    pageLoading.value ||
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

/** 解析默认 Tab：门/住院费用明细均固定明细（忽略后端 defaultActiveTab）。 */
function resolveDefaultPresentationMode(
  _init: LoadFeeDetailReportPageInitResponse | null | undefined,
): PresentationModeCode {
  return PRESENTATION_MODE_DETAIL;
}

/** 下拉选项转为 ElSelect 可用结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 校验时间区间并组装查询/导出请求体。 */
function buildFilterPayload() {
  const dateRange = toReportApiDateTimeRangePrecise(queryParams.dateFrom, queryParams.dateTo);
  const dateFrom = dateRange.start;
  const dateTo = dateRange.end;
  if (!dateFrom || !dateTo) {
    ElMessage.warning(props.scope === 'inpatient' ? '请选择业务时间' : '请选择结算时间');
    return null;
  }
  if (dayjs(dateFrom).isAfter(dayjs(dateTo))) {
    ElMessage.warning('开始时间不能晚于结束时间');
    return null;
  }
  return {
    dateFrom,
    dateTo,
    // 汇总 Tab 不传收退方向，由后端强制净视角。
    tradeDirection: isSummaryTab.value ? undefined : queryParams.tradeDirection || 'ALL',
    requestingOrgId: preserveApiIdForRequest(queryParams.requestingOrgId),
    feeCategoryCodes: queryParams.feeCategoryCodes?.length
      ? queryParams.feeCategoryCodes
      : undefined,
    subCategoryCodes: queryParams.subCategoryCodes?.length
      ? queryParams.subCategoryCodes
      : undefined,
    patientKeyword: queryParams.patientKeyword?.trim() || undefined,
    productNameKeyword: queryParams.productNameKeyword?.trim() || undefined,
    productCodeKeyword: queryParams.productCodeKeyword?.trim() || undefined,
    cashierId: preserveApiIdForRequest(queryParams.cashierId),
    feeNatureContractNo: queryParams.feeNatureContractNo || undefined,
    // 仅住院传结算状态；门诊忽略。
    settlementStatus:
      props.scope === 'inpatient'
        ? queryParams.settlementStatus || SETTLEMENT_STATUS_ALL
        : undefined,
    summaryDimension: isSummaryTab.value
      ? queryParams.summaryDimension || SUMMARY_DIMENSION_FIN_TYPE
      : undefined,
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
    await runSummarize(filters);
  } else {
    // 提升世代号：丢弃上一轮未完成的进价列角
    detailPageGeneration.value += 1;
    const generation = detailPageGeneration.value;
    await runPage({
      ...filters,
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
    });
    // 列表已出后再刷进价列角，不阻塞表格 loading
    void refreshPurchaseCorner(filters, generation);
  }
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/**
 * 异步回填明细页脚进价合计（与当前筛选同口径）。
 *
 * @param filters 当前查询筛选
 * @param generation 发起时的明细分页世代号
 */
async function refreshPurchaseCorner(
  filters: NonNullable<ReturnType<typeof buildFilterPayload>>,
  generation: number,
) {
  try {
    const corner = await runPurchaseCorner(filters);
    // 已换筛选/翻页则丢弃过期回包
    if (generation !== detailPageGeneration.value) {
      return;
    }
    if (!globalSummary.value) {
      globalSummary.value = {};
    }
    globalSummary.value = {
      ...globalSummary.value,
      purchaseAmountTotal: corner?.purchaseAmountTotal,
    };
  } catch {
    // 列角失败不影响列表；页脚进价保持 —
  }
}

/** 搜索按钮：回到第一页再查。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/**
 * 切换「按患者维度合并」后立即查询（仅住院明细）。
 * 费用明细表行数多：开关会立刻改列显隐，若仍挂着当月数据，ElTable 同步重绘会卡主线程。
 * 先清空再出 loading，再请求。
 */
async function handleMergeByPatientChange(enabled: string | number | boolean) {
  // 先卸掉当前页大表，避免「改列 + 几百行」同帧重绘卡死
  tableData.value = [];
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
  // 等空表 + loading 画出后再发请求
  await nextTick();
  await new Promise<void>((resolve) => {
    requestAnimationFrame(() => resolve());
  });
  try {
    await getList();
    // 回包灌表也重，loading 多盖一帧，减少「查完又卡一下」的体感
    await nextTick();
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => resolve());
    });
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

/** 重置筛选项为初始化默认值。 */
function handleReset() {
  const init = pageInit.value;
  queryParams.dateFrom =
    normalizeReportDateTime(init?.defaultDateFrom, 'start') ||
    dayjs().startOf('day').format('YYYY-MM-DD HH:mm:ss');
  queryParams.dateTo =
    normalizeReportDateTime(init?.defaultDateTo, 'end') ||
    dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss');
  queryParams.tradeDirection = 'ALL';
  if (init?.tradeDirectionOptions?.length) {
    const hasAll = init.tradeDirectionOptions.some((item) => item.value === 'ALL');
    queryParams.tradeDirection = hasAll
      ? 'ALL'
      : String(init.tradeDirectionOptions[0]?.value ?? 'ALL');
  }
  queryParams.settlementStatus = SETTLEMENT_STATUS_ALL;
  if (init?.settlementStatusOptions?.length) {
    const hasAll = init.settlementStatusOptions.some(
      (item) => item.value === SETTLEMENT_STATUS_ALL,
    );
    queryParams.settlementStatus = hasAll
      ? SETTLEMENT_STATUS_ALL
      : String(init.settlementStatusOptions[0]?.value ?? SETTLEMENT_STATUS_ALL);
  }
  queryParams.requestingOrgId = undefined;
  queryParams.feeCategoryCodes = undefined;
  queryParams.subCategoryCodes = undefined;
  queryParams.patientKeyword = undefined;
  queryParams.productNameKeyword = undefined;
  queryParams.productCodeKeyword = undefined;
  queryParams.cashierId = undefined;
  queryParams.feeNatureContractNo = undefined;
  queryParams.presentationMode = resolveDefaultPresentationMode(init);
  queryParams.summaryDimension = String(
    summaryDimensionOptions.value[0]?.value ?? SUMMARY_DIMENSION_FIN_TYPE,
  );
  queryParams.mergeByPatientDefinition = false;
  queryParams.pageNo = 1;
  queryParams.pageSize = init?.defaultPageSize ?? 10;
  void getList();
}

/** 导出当前 Tab（列清单与页面可见列一致）。 */
async function handleExport() {
  const filters = buildFilterPayload();
  if (!filters) {
    return;
  }
  // 明细 quantityDisplay → 可求和数量 + 单位；其余字段与导出行 DTO 同名
  const exportColumns = buildExcelExportColumns(printColumnCatalog.value, {
    fieldExpand: {
      quantityDisplay: [
        { field: 'quantityValue', title: '数量' },
        { field: 'quantityUnitDisplay', title: '单位' },
      ],
    },
  });
  await runExport({
    ...filters,
    presentationMode: queryParams.presentationMode,
    exportColumns,
  });
}

/** 切换每页条数（仅明细）。 */
function handlePageSizeChange(size: number) {
  queryParams.pageSize = size;
  queryParams.pageNo = 1;
  void getList();
}

/** 切换页码（仅明细）；同页重复触发不重复请求。 */
function handleCurrentPageChange(page: number) {
  if (!page || page < 1 || page === queryParams.pageNo) {
    return;
  }
  queryParams.pageNo = page;
  void getList();
}

/** 数量展示：空值占位。 */
function formatQuantity(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return String(value);
}

/** 数量 + 单位合并，如「3盒」。 */
function formatQuantityWithUnit(quantity: unknown, unit: unknown): string {
  const qty = formatQuantity(quantity);
  const unitText = unit === undefined || unit === null ? '' : String(unit).trim();
  if (qty === '—' && !unitText) {
    return '—';
  }
  if (qty === '—') {
    return unitText || '—';
  }
  return unitText ? `${qty}${unitText}` : qty;
}

/** 金额展示：2 位小数 + 元（BR-MON-001；售价/结算）。 */
function formatMoneyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return formatMoneyYuan(value);
}

/** 进价侧金额：中药可至三位 + 元（无分类码时最多三位去尾零）。 */
function formatPurchaseMoneyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  const text = formatPurchaseMoneyYuan(value);
  return text || '—';
}

/** 进价单价展示：中药可至三位（无分类码时最多三位去尾零）。 */
function formatPurchaseUnitPriceCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  const text = formatPurchaseUnitPriceDisplay(value);
  return text ? `${text}元` : '—';
}

/**
 * 汇总表单元格：维度名回落 finTypeDisplay；金额走进/售价刻度。
 *
 * @param row 汇总行
 * @param col 当前列
 */
function formatSummaryCell(
  row: FeeDetailReportSummaryRowDto,
  col: FeeDetailSummaryColumn,
): string {
  // 维度列优先 dimensionName，兼容旧 finTypeDisplay
  if (col.cellKind === 'dimension') {
    return String(row.dimensionName || row.finTypeDisplay || '—');
  }
  if (col.cellKind === 'qty') {
    return formatQuantity(row.quantityTotal);
  }
  const raw = (row as Record<string, unknown>)[col.prop];
  if (col.cellKind === 'purchaseMoney') {
    return formatPurchaseMoneyCell(raw);
  }
  return formatMoneyCell(raw);
}

/**
 * 明细表单元格：数量带单位；来源列由模板画 Tag；金额分进/售价刻度。
 *
 * @param row 平铺行
 * @param col 当前列
 */
function formatDetailCell(row: FeeDetailTableRow, col: FeeDetailDynamicColumn): string {
  // 数量 = 数值 + 单位，如「1次」
  if (col.cellKind === 'quantity') {
    return formatQuantityWithUnit(row.quantityValue, row.quantityUnitDisplay);
  }
  // 进价单价走中药三位刻度
  if (col.cellKind === 'purchaseUnit') {
    return formatPurchaseUnitPriceCell(row.purchaseUnitPrice);
  }
  // 进价总价
  if (col.cellKind === 'purchaseMoney') {
    return formatPurchaseMoneyCell(row.purchaseAmount);
  }
  // 售价/优惠/实收同一结算刻度
  if (col.cellKind === 'saleMoney') {
    return formatMoneyCell((row as Record<string, unknown>)[col.prop]);
  }
  const raw = (row as Record<string, unknown>)[col.prop];
  // 空单元格占位
  if (raw === undefined || raw === null || raw === '') {
    return '—';
  }
  return String(raw);
}

/** 收退方向标签类型。 */
function resolveTradeDirectionTagType(tradeDirection?: string): 'danger' | 'info' | 'success' {
  if (tradeDirection === 'REFUND') {
    return 'danger';
  }
  if (tradeDirection === 'CHARGE') {
    return 'success';
  }
  return 'info';
}

/** 收退方向文案。 */
function resolveTradeDirectionLabel(tradeDirection?: string): string {
  if (tradeDirection === 'REFUND') {
    return '退费';
  }
  if (tradeDirection === 'CHARGE') {
    return '收费';
  }
  return tradeDirection?.trim() || '—';
}

/** 明细行是否退费（方向或数量/金额为负）。 */
function isFeeDetailRefundRow(row: FeeDetailTableRow): boolean {
  if (row.tradeDirection === 'REFUND') {
    return true;
  }
  return isReportRefundBySignedFields(row as Record<string, unknown>, [
    'quantityValue',
    'purchaseAmount',
    'saleAmount',
    'discountAmount',
    'actualReceivedAmount',
  ]);
}

/** 汇总行是否净退（合计金额为负）。 */
function isFeeDetailSummaryRefundRow(row: FeeDetailReportSummaryRowDto): boolean {
  return isReportRefundBySignedFields(row as Record<string, unknown>, [
    'purchaseAmountTotal',
    'saleAmountTotal',
  ]);
}

/** 明细行样式：就诊块底色 + 退费整行标红。 */
function resolveRowClassName({ row }: { row: FeeDetailTableRow }): string {
  const blockIndex = row.encounterBlockIndex ?? 0;
  const toneClass = blockIndex % 2 === 0 ? 'fee-detail-block--even' : 'fee-detail-block--odd';
  const baseClass = row.rowType === 'subtotal' ? `${toneClass} fee-detail-subtotal-row` : toneClass;
  return resolveReportRefundRowClassName(isFeeDetailRefundRow(row), baseClass);
}

/** 汇总行样式：净退整行标红。 */
function resolveSummaryRowClassName({ row }: { row: FeeDetailReportSummaryRowDto }): string {
  return resolveReportRefundRowClassName(isFeeDetailSummaryRefundRow(row));
}

/**
 * 汇总行稳定 key（优先 dimensionKey，兼容旧 finType*）。
 *
 * @param row 汇总行
 */
function resolveSummaryRowKey(row: FeeDetailReportSummaryRowDto) {
  // 多维契约：优先用分桶键
  if (row.dimensionKey) {
    return String(row.dimensionKey);
  }
  // 兼容 FIN_TYPE 旧字段或仅有展示名
  return String(row.finTypeCode ?? row.finTypeDisplay ?? row.dimensionName ?? '');
}

/** 加载页初始化下拉与默认时间窗。 */
async function loadPageInit() {
  const data = await runInit();
  pageInit.value = data;
  applyDiscountFeatureEnabled(data?.discountFeatureEnabled);
  queryParams.dateFrom =
    normalizeReportDateTime(data.defaultDateFrom, 'start') ||
    dayjs().startOf('day').format('YYYY-MM-DD HH:mm:ss');
  queryParams.dateTo =
    normalizeReportDateTime(data.defaultDateTo, 'end') ||
    dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss');
  queryParams.pageSize = data.defaultPageSize ?? 10;
  queryParams.presentationMode = resolveDefaultPresentationMode(data);
  if (data.tradeDirectionOptions?.length) {
    const hasAll = data.tradeDirectionOptions.some((item) => item.value === 'ALL');
    queryParams.tradeDirection = hasAll
      ? 'ALL'
      : String(data.tradeDirectionOptions[0]?.value ?? 'ALL');
  }
  queryParams.settlementStatus = SETTLEMENT_STATUS_ALL;
  if (data.settlementStatusOptions?.length) {
    const hasAll = data.settlementStatusOptions.some(
      (item) => item.value === SETTLEMENT_STATUS_ALL,
    );
    queryParams.settlementStatus = hasAll
      ? SETTLEMENT_STATUS_ALL
      : String(data.settlementStatusOptions[0]?.value ?? SETTLEMENT_STATUS_ALL);
  }
  const firstDim = data.summaryDimensionOptions?.[0]?.value;
  // init 首项为 FIN_TYPE；无选项时本地默认
  queryParams.summaryDimension = String(firstDim ?? SUMMARY_DIMENSION_FIN_TYPE);
  queryParams.cashierId = undefined;
}

onMounted(async () => {
  try {
    await loadPageInit();
    await getList();
  } catch {
    // init 失败时不自动查询
  } finally {
    // 无论 init 成败都允许挂表，避免一直空白
    pageInitReady.value = true;
  }
});
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryBarModel">
          <template #fields>
            <ElFormItem :label="businessDateLabel">
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
            <ElFormItem v-if="!isSummaryTab" label="收退方向">
              <ElRadioGroup v-model="queryParams.tradeDirection" @change="handleQuery">
                <ElRadioButton
                  v-for="item in toSelectOptions(tradeDirectionOptions)"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </ElRadioButton>
              </ElRadioGroup>
            </ElFormItem>
            <ElFormItem v-if="showSettlementStatusFilter" label="结算状态">
              <ElSelect
                v-model="queryParams.settlementStatus"
                :clearable="false"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(settlementStatusOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem :label="departmentFilterLabel">
              <ElSelect
                v-model="queryParams.requestingOrgId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(registrationDepartmentOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem v-if="showFeeCategoryFilter" label="费用大类">
              <ElSelect
                v-model="queryParams.feeCategoryCodes"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(feeCategoryOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="项目类别">
              <ElSelect
                v-model="queryParams.subCategoryCodes"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(subCategoryOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="患者">
              <ElInput
                v-model="queryParams.patientKeyword"
                clearable
                :placeholder="patientKeywordPlaceholder"
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
            <ElFormItem label="项目名称">
              <ElInput
                v-model="queryParams.productNameKeyword"
                clearable
                placeholder="项目名称"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="项目编码">
              <ElInput
                v-model="queryParams.productCodeKeyword"
                clearable
                placeholder="项目编码"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="收费员">
              <ElSelect
                v-model="queryParams.cashierId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(cashierOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <!-- 占满查询区第 4 列空位；长文案放 content，避免 88px label 竖排 -->
            <ElFormItem
              v-if="scope === 'inpatient' && !isSummaryTab"
              label-width="0"
              class="report-list-page__query-switch-item"
            >
              <div class="report-list-page__query-switch">
                <!-- 开启后同住院就诊内相同项目编码（产品）数量/金额净合计；主项与加收项仍分行 -->
                <span title="开启后：同一住院就诊内相同项目合并为一行，数量与金额相加；关闭后：按收费行原样展示（数量为开立/执行落库值，不一定为 1）">按患者维度合并</span>
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
          :total="isSummaryTab ? summaryTotal : detailTotal"
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
              :key="`fee-detail-summary-${queryParams.summaryDimension}`"
              :loading="tableLoading"
              :data="summaryRows"
              :height="tableHeight"
              :row-key="resolveSummaryRowKey"
              :row-class-name="resolveSummaryRowClassName"
            >
              <ElTableColumn
                v-for="col in summaryTableColumns"
                :key="col.prop"
                :prop="col.prop"
                :label="col.label"
                :width="col.width"
                :min-width="col.minWidth"
                :align="col.align"
                :header-align="col.align"
                :class-name="col.className"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatSummaryCell(row, col) }}
                </template>
              </ElTableColumn>
            </ReportElTable>

            <ReportElTable
              v-else-if="pageInitReady"
              :key="`fee-detail-detail-${detailTableColumnKey}`"
              class="fee-detail-report-table"
              :stripe="false"
              :loading="tableLoading"
              :data="tableData"
              :height="tableHeight"
              row-key="rowKey"
              :row-class-name="resolveRowClassName"
            >
              <ElTableColumn
                v-for="col in detailTableColumns"
                :key="col.prop"
                :prop="col.prop"
                :label="col.label"
                :width="col.width"
                :min-width="col.minWidth"
                :align="col.align"
                :header-align="col.align"
                :class-name="col.className"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  <template v-if="col.cellKind === 'trade'">
                    <template v-if="row.rowType === 'subtotal'">—</template>
                    <ElTag
                      v-else
                      :type="resolveTradeDirectionTagType(row.tradeDirection as string)"
                      effect="light"
                    >
                      {{ resolveTradeDirectionLabel(row.tradeDirection as string) }}
                    </ElTag>
                  </template>
                  <template v-else>
                    {{ formatDetailCell(row, col) }}
                  </template>
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
          :page-size="isSummaryTab ? Math.max(summaryRows.length, 1) : queryParams.pageSize"
          :total="isSummaryTab ? summaryTotal : detailTotal"
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

<style scoped lang="scss">
.fee-detail-report-table {
  /* 关闭默认斑马纹，改按就诊块着色，合并列与明细列底色一致。 */
  :deep(.el-table__body tr.fee-detail-block--even > td.el-table__cell),
  :deep(.el-table__body tr.fee-detail-block--even > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.fee-detail-block--even > td.el-table-fixed-column--right) {
    background-color: var(--el-bg-color);
  }

  :deep(.el-table__body tr.fee-detail-block--odd > td.el-table__cell),
  :deep(.el-table__body tr.fee-detail-block--odd > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.fee-detail-block--odd > td.el-table-fixed-column--right) {
    background-color: var(--el-fill-color-lighter);
  }

  :deep(.el-table__body tr.fee-detail-subtotal-row > td.el-table__cell),
  :deep(.el-table__body tr.fee-detail-subtotal-row > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.fee-detail-subtotal-row > td.el-table-fixed-column--right) {
    font-weight: 500;
    color: var(--el-text-color-primary);
    background-color: var(--el-fill-color-light) !important;
    border-top: 1px solid var(--el-border-color);
  }

  /* 小计且退费：保持整行 danger 字色。 */
  :deep(
    .el-table__body tr.fee-detail-subtotal-row.report-list-page__refund-row > td.el-table__cell
  ),
  :deep(
    .el-table__body
      tr.fee-detail-subtotal-row.report-list-page__refund-row
      > td.el-table-fixed-column--left
  ),
  :deep(
    .el-table__body
      tr.fee-detail-subtotal-row.report-list-page__refund-row
      > td.el-table-fixed-column--right
  ),
  :deep(
    .el-table__body
      tr.fee-detail-subtotal-row.report-list-page__refund-row
      > td.el-table__cell
      .cell
  ) {
    color: var(--el-color-danger);
  }

  /* 禁用 hover 变色，保持就诊块底色。 */
  :deep(.el-table__body tr.fee-detail-block--even:hover > td.el-table__cell),
  :deep(.el-table__body tr.fee-detail-block--even:hover > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.fee-detail-block--even:hover > td.el-table-fixed-column--right) {
    background-color: var(--el-bg-color) !important;
  }

  :deep(.el-table__body tr.fee-detail-block--odd:hover > td.el-table__cell),
  :deep(.el-table__body tr.fee-detail-block--odd:hover > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.fee-detail-block--odd:hover > td.el-table-fixed-column--right) {
    background-color: var(--el-fill-color-lighter) !important;
  }

  :deep(.el-table__body tr.fee-detail-subtotal-row:hover > td.el-table__cell),
  :deep(.el-table__body tr.fee-detail-subtotal-row:hover > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.fee-detail-subtotal-row:hover > td.el-table-fixed-column--right) {
    background-color: var(--el-fill-color-light) !important;
  }
}
</style>
