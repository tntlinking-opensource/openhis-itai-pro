<!--
  单据详情报表：三域共用，由 domainScope 区分药库/药房/科室。
-->
<script setup lang="ts">
import type { DocumentDetailDomainScope, DocumentDetailRowDto } from '#/api/report/document-detail';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Download, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTableColumn,
} from 'element-plus';

import {
  exportDocumentDetailsApi,
  loadDocumentDetailFormInitApi,
  pageDocumentDetailsApi,
  summarizeDocumentDetailsApi,
  type LoadDocumentDetailFormInitResponse,
} from '#/api/report/document-detail';
import { useRequest } from '#/hooks/use-request';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { decimalAdd } from '#/utils/format/decimal-util';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATETIMERANGE,
  REPORT_TABLE_AMOUNT_CELL,
  REPORT_TABLE_BUSNO,
  REPORT_TABLE_COL_GROUP_START,
  REPORT_TABLE_EMPTY_VALUE,
  REPORT_TABLE_NUM_CELL,
  ReportElTable,
  ReportListPageLayout,
  ReportListToolbar,
  ReportPageFooter,
  ReportQueryBar,
  ReportTableScroll,
} from '#/views/report/shared';
import { fmtReportNumber, optionDtosToSelectItems } from '#/views/report/shared/report-format';
import {
  buildReportDateTimeRangeShortcuts,
  normalizeReportDateTime,
  toReportApiDateTimeRangePrecise,
} from '#/views/report/shared/report-date-range-shortcuts';

defineOptions({ name: 'DocumentDetailReportPage' });

interface Props {
  domainScope: DocumentDetailDomainScope;
  pageTitle: string;
}

interface QueryState {
  pageNo: number;
  pageSize: number;
  typeEnum?: string;
  sourceLocationId?: string;
  purposeLocationId?: string;
  approvalTimeStart?: string;
  approvalTimeEnd?: string;
  productKeyword?: string;
  lotNumberKeyword?: string;
  busNo?: string;
}

/** 列表行：明细或单据小计。 */
interface DocumentDetailTableRow extends DocumentDetailRowDto {
  __rowKind?: 'detail' | 'subtotal';
}

const props = defineProps<Props>();

/** 列表列总数；仅单据号列（下标 0）按同 busNo 纵向合并。 */
const TABLE_COLUMN_COUNT = 18;
/** 仅合并「单据号」列。 */
const DOC_LEVEL_COLUMN_INDEXES = new Set([0]);

const tableData = ref<DocumentDetailTableRow[]>([]);
/** 当前页 rowspan：matrix[row][col]，0 表示被上方单元格合并。 */
const busNoSpanMatrix = ref<number[][]>([]);
const total = ref(0);
const sumTotalPrice = ref('');
const sumItemQuantity = ref('');
const sumRowCount = ref(0);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const typeEnumOptions = ref<ApiOptionItem[]>([]);
const sourceLocationOptions = ref<ApiOptionItem[]>([]);
const purposeLocationOptions = ref<ApiOptionItem[]>([]);
/** init 回包：重置/清空审批时间时回填默认当月。 */
const pageInit = ref<LoadDocumentDetailFormInitResponse | null>(null);

const queryParams = reactive<QueryState>({
  pageNo: 1,
  pageSize: 20,
});

const tableLoading = computed(
  () => initLoading.value || pageLoading.value || sumLoading.value || exportLoading.value,
);

const listToolbarTitle = computed(() => `${props.pageTitle} · 明细列表`);

const footerSummaryItems = computed(() => [
  { label: '行数', value: sumRowCount.value },
  { label: '数量合计', value: sumItemQuantity.value },
  { label: '金额合计', value: sumTotalPrice.value, primary: true, prefix: '¥' },
]);

const dateTimeRangeShortcuts = buildReportDateTimeRangeShortcuts();
/** 日历点选日期时默认起 00:00:00、止 23:59:59（快捷项已自带时分秒）。 */
const approvalDateTimeDefaultTime: [Date, Date] = [
  new Date(2000, 0, 1, 0, 0, 0),
  new Date(2000, 0, 1, 23, 59, 59),
];

/** 审批时间与 datetimerange 双向绑定（界面精确到秒，请求原样传 LocalDateTime）。 */
const approvalDateRange = computed({
  get() {
    const start = normalizeReportDateTime(queryParams.approvalTimeStart, 'start');
    const end = normalizeReportDateTime(queryParams.approvalTimeEnd, 'end');
    if (start && end) {
      return [start, end] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.approvalTimeStart = value?.[0]
      ? normalizeReportDateTime(value[0], 'start')
      : undefined;
    queryParams.approvalTimeEnd = value?.[1] ? normalizeReportDateTime(value[1], 'end') : undefined;
  },
});

/** 下拉选项转为 ElSelect 可用结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 回填 init 默认审批时间窗（当前自然月 00:00:00～23:59:59）。 */
function applyDefaultApprovalDates() {
  const start = normalizeReportDateTime(pageInit.value?.defaultApprovalTimeStart, 'start');
  const end = normalizeReportDateTime(pageInit.value?.defaultApprovalTimeEnd, 'end');
  if (start && end) {
    queryParams.approvalTimeStart = start;
    queryParams.approvalTimeEnd = end;
  }
}

/** 审批时间必填展示：清空时立刻回填默认当月，避免 UI 空而后端仍按当月筛。 */
function ensureApprovalDatesVisible(): boolean {
  const start = normalizeReportDateTime(queryParams.approvalTimeStart, 'start');
  const end = normalizeReportDateTime(queryParams.approvalTimeEnd, 'end');
  if (start && end) {
    queryParams.approvalTimeStart = start;
    queryParams.approvalTimeEnd = end;
    return true;
  }
  applyDefaultApprovalDates();
  if (
    !normalizeReportDateTime(queryParams.approvalTimeStart, 'start') ||
    !normalizeReportDateTime(queryParams.approvalTimeEnd, 'end')
  ) {
    ElMessage.warning('请选择审批时间');
    return false;
  }
  return true;
}

/** 是否单据小计行。 */
function isSubtotalRow(row: DocumentDetailTableRow) {
  return row.__rowKind === 'subtotal';
}

/** 同单据连续明细后插入小计行（合计金额）。 */
function buildRowsWithSubtotals(rows: DocumentDetailRowDto[]): DocumentDetailTableRow[] {
  const result: DocumentDetailTableRow[] = [];
  let rowIndex = 0;
  while (rowIndex < rows.length) {
    const busNo = String(rows[rowIndex]?.busNo ?? '').trim();
    let span = 1;
    // 按连续相同单据号成组（空单据号只占 1 行）
    if (busNo) {
      while (
        rowIndex + span < rows.length &&
        String(rows[rowIndex + span]?.busNo ?? '').trim() === busNo
      ) {
        span += 1;
      }
    }
    let amountSum = 0;
    for (let offset = 0; offset < span; offset += 1) {
      const detail = rows[rowIndex + offset]!;
      result.push({ ...detail, __rowKind: 'detail' });
      amountSum = decimalAdd(amountSum, detail.totalPrice, 2);
    }
    // 有单据号才插小计；空单号不造假合计行
    if (busNo) {
      result.push({
        lineId: `subtotal-${busNo}-${rowIndex}`,
        busNo: '',
        __rowKind: 'subtotal',
        productName: '小计',
        totalPrice: amountSum,
      });
    }
    rowIndex += span;
  }
  return result;
}

/** 写入列表并重算单据号合并（小计行不参与合并）。 */
function applyTableRecords(records: DocumentDetailRowDto[]) {
  const rows = buildRowsWithSubtotals(records);
  tableData.value = rows;
  rebuildBusNoSpanMatrix(rows);
}

/** 按当前页连续相同 busNo（仅明细行）重建「单据号」列 rowspan 矩阵。 */
function rebuildBusNoSpanMatrix(rows: DocumentDetailTableRow[]) {
  // 每行每列默认占 1；仅单据号列对连续同单明细合并
  const matrix: number[][] = rows.map(() => Array.from({ length: TABLE_COLUMN_COUNT }, () => 1));
  let rowIndex = 0;
  while (rowIndex < rows.length) {
    const row = rows[rowIndex]!;
    if (isSubtotalRow(row)) {
      rowIndex += 1;
      continue;
    }
    const busNo = String(row.busNo ?? '').trim();
    let span = 1;
    if (busNo) {
      while (
        rowIndex + span < rows.length &&
        !isSubtotalRow(rows[rowIndex + span]!) &&
        String(rows[rowIndex + span]?.busNo ?? '').trim() === busNo
      ) {
        span += 1;
      }
    }
    for (const columnIndex of DOC_LEVEL_COLUMN_INDEXES) {
      matrix[rowIndex]![columnIndex] = span;
      for (let offset = 1; offset < span; offset += 1) {
        matrix[rowIndex + offset]![columnIndex] = 0;
      }
    }
    rowIndex += span;
  }
  busNoSpanMatrix.value = matrix;
}

/** ElTable 单元格合并：同单据号仅纵向合并「单据号」列。 */
function tableSpanMethod(args: { columnIndex: number; rowIndex: number }) {
  const rowspan = busNoSpanMatrix.value[args.rowIndex]?.[args.columnIndex] ?? 1;
  if (rowspan === 0) {
    return { rowspan: 0, colspan: 0 };
  }
  return { rowspan, colspan: 1 };
}

/** 组装筛选请求体（不含分页）。 */
function buildFilterRequest() {
  if (!ensureApprovalDatesVisible()) {
    return null;
  }
  const approvalRange = toReportApiDateTimeRangePrecise(
    queryParams.approvalTimeStart,
    queryParams.approvalTimeEnd,
  );
  if (!approvalRange.start || !approvalRange.end) {
    ElMessage.warning('请选择审批时间');
    return null;
  }
  return {
    domainScope: props.domainScope,
    // 空串/清空 = 本域全部类型（BR-INV-DDC-002）
    typeEnum: queryParams.typeEnum?.trim() || undefined,
    sourceLocationId: queryParams.sourceLocationId
      ? preserveApiIdForRequest(queryParams.sourceLocationId)
      : undefined,
    purposeLocationId: queryParams.purposeLocationId
      ? preserveApiIdForRequest(queryParams.purposeLocationId)
      : undefined,
    approvalTimeStart: approvalRange.start,
    approvalTimeEnd: approvalRange.end,
    productKeyword: queryParams.productKeyword?.trim() || undefined,
    lotNumberKeyword: queryParams.lotNumberKeyword?.trim() || undefined,
    busNo: queryParams.busNo?.trim() || undefined,
  };
}

/** 取行操作单位展示名。 */
function rowUnitText(row: DocumentDetailTableRow): string {
  return String(row.unitCodeDictText ?? '').trim();
}

/** 数量展示：整数量不补零，有小数则保留。 */
function formatQtyNatural(value: unknown) {
  if (value === null || value === undefined || value === '') return '';
  const n = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(n)) return String(value);
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 4,
  });
}

/** 数量 + 单位合并，如「10盒」。 */
function formatQtyWithUnit(row: DocumentDetailTableRow) {
  if (isSubtotalRow(row)) {
    return '';
  }
  const qty = formatQtyNatural(row.itemQuantity);
  const unit = rowUnitText(row);
  if (!qty && !unit) return '';
  if (!unit) return qty;
  if (!qty) return unit;
  return `${qty}${unit}`;
}

/** 单价展示：2 位小数 + 元，有操作单位时追加「/单位」。 */
function formatPriceWithUnit(row: DocumentDetailTableRow) {
  if (isSubtotalRow(row)) {
    return '';
  }
  const price = fmtReportNumber(row.price, 2);
  const unit = rowUnitText(row);
  if (!price) return '';
  return unit ? `${price}元/${unit}` : `${price}元`;
}

/** 金额展示：2 位小数 + 元。 */
function formatAmountWithUnit(row: DocumentDetailTableRow) {
  const amount = fmtReportNumber(row.totalPrice, 2);
  if (!amount) return '';
  return `${amount}元`;
}

/** 盘点相关列：空值显示占位符。 */
function formatQtyOrDash(value: unknown, row?: DocumentDetailTableRow) {
  if (row && isSubtotalRow(row)) {
    return '';
  }
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  return fmtReportNumber(value, 2);
}

/** 判断盘点列是否为空。 */
function isQtyEmpty(value: unknown, row?: DocumentDetailTableRow) {
  if (row && isSubtotalRow(row)) {
    return false;
  }
  return value === null || value === undefined || value === '';
}

/** 审批时间压缩为 MM-DD HH:mm。 */
function formatApprovalTime(value?: string, row?: DocumentDetailTableRow) {
  if (row && isSubtotalRow(row)) {
    return '';
  }
  const text = String(value ?? '').trim();
  if (!text) {
    return '—';
  }
  if (text.length >= 16) {
    return text.slice(5, 16);
  }
  return text;
}

/** 小计行底色 class。 */
function tableRowClassName({ row }: { row: DocumentDetailTableRow }) {
  return isSubtotalRow(row) ? 'document-detail-subtotal-row' : '';
}

const { run: runInit, loading: initLoading } = useRequest(loadDocumentDetailFormInitApi, {
  manual: true,
  onSuccess: (data) => {
    pageInit.value = data ?? null;
    typeEnumOptions.value = optionDtosToSelectItems(data?.typeEnumOptions);
    sourceLocationOptions.value = optionDtosToSelectItems(data?.sourceLocationOptions);
    purposeLocationOptions.value = optionDtosToSelectItems(data?.purposeLocationOptions);
    // 单据类型默认空=本域全部；忽略后端 defaultTypeEnum（仅前端口径）
    applyDefaultApprovalDates();
  },
});

const { run: runPage, loading: pageLoading } = useRequest(pageDocumentDetailsApi, {
  manual: true,
  onSuccess: (data) => {
    applyTableRecords(data?.records ?? []);
    total.value = Number(data?.total ?? 0);
  },
});

const { run: runSum, loading: sumLoading } = useRequest(summarizeDocumentDetailsApi, {
  manual: true,
  onSuccess: (data) => {
    sumTotalPrice.value = fmtReportNumber(data?.totalPriceSum, 2);
    sumItemQuantity.value = formatQtyNatural(data?.itemQuantitySum);
    sumRowCount.value = Number(data?.rowCount ?? 0);
  },
});

const { run: runExport, loading: exportLoading } = useRequest(exportDocumentDetailsApi, {
  manual: true,
});

/** 同步拉分页与合计。 */
async function getList() {
  const filter = buildFilterRequest();
  if (!filter) {
    return;
  }
  await Promise.all([
    runPage({ ...filter, pageNo: queryParams.pageNo, pageSize: queryParams.pageSize }),
    runSum(filter),
  ]);
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 查询回到第一页。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 审批时间变更：清空则回填默认当月后再查。 */
function handleApprovalDateChange() {
  ensureApprovalDatesVisible();
  handleQuery();
}

/** 重置筛选：类型空（全部）+ 当月审批时间窗。 */
function handleReset() {
  queryParams.typeEnum = undefined;
  queryParams.productKeyword = undefined;
  queryParams.lotNumberKeyword = undefined;
  queryParams.busNo = undefined;
  queryParams.sourceLocationId = undefined;
  queryParams.purposeLocationId = undefined;
  queryParams.pageNo = 1;
  void runInit({ domainScope: props.domainScope }).then(() => getList());
}

/** 导出 Excel。 */
async function handleExportExcel() {
  const filter = buildFilterRequest();
  if (!filter) {
    return;
  }
  const data = await runExport(filter);
  if (!data?.fileBytes?.trim()) {
    ElMessage.warning('导出内容为空');
    return;
  }
  downloadBinaryExportFile(data, data.fileName ?? `${props.pageTitle}.xlsx`);
  ElMessage.success('导出成功');
}

/** 切换每页条数。 */
function handlePageSizeChange(size: number) {
  queryParams.pageSize = size;
  queryParams.pageNo = 1;
  void getList();
}

/** 切换页码。 */
function handleCurrentPageChange(page: number) {
  queryParams.pageNo = page;
  void getList();
}

onMounted(async () => {
  await runInit({ domainScope: props.domainScope });
  await getList();
});
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryParams as Record<string, unknown>">
          <template #fields>
            <ElFormItem label="单据类型">
              <ElSelect
                v-model="queryParams.typeEnum"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption label="全部" value="" />
                <ElOption
                  v-for="item in toSelectOptions(typeEnumOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="源库位">
              <ElSelect
                v-model="queryParams.sourceLocationId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(sourceLocationOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="目的库位">
              <ElSelect
                v-model="queryParams.purposeLocationId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(purposeLocationOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="审批时间">
              <ElDatePicker
                v-model="approvalDateRange"
                type="datetimerange"
                value-format="YYYY-MM-DD HH:mm:ss"
                format="YYYY-MM-DD HH:mm:ss"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                :clearable="false"
                :default-time="approvalDateTimeDefaultTime"
                :shortcuts="dateTimeRangeShortcuts"
                :class="[REPORT_QUERY_CONTROL, REPORT_QUERY_CONTROL_DATETIMERANGE]"
                @change="handleApprovalDateChange"
              />
            </ElFormItem>
            <ElFormItem label="品名/编码">
              <ElInput
                v-model="queryParams.productKeyword"
                clearable
                placeholder="品名或编码"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="批号">
              <ElInput
                v-model="queryParams.lotNumberKeyword"
                clearable
                placeholder="批号模糊"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="单据号">
              <ElInput
                v-model="queryParams.busNo"
                clearable
                placeholder="单据号模糊"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
          </template>
          <template #actions>
            <ElButton type="primary" :icon="Search" :loading="tableLoading" @click="handleQuery">
              搜索
            </ElButton>
            <ElButton :icon="Refresh" :loading="tableLoading" @click="handleReset">重置</ElButton>
          </template>
        </ReportQueryBar>
      </template>

      <template #toolbar>
        <ReportListToolbar :title="listToolbarTitle" :total="total">
          <template #actions>
            <ElButton
              type="primary"
              plain
              :icon="Download"
              :loading="exportLoading"
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
              :loading="tableLoading"
              :data="tableData"
              :height="tableHeight"
              :stripe="false"
              row-key="lineId"
              :span-method="tableSpanMethod"
              :row-class-name="tableRowClassName"
            >
              <ElTableColumn label="单据号" min-width="136" fixed="left" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="!isSubtotalRow(row)" :class="REPORT_TABLE_BUSNO">{{
                    row.busNo
                  }}</span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="单据类型" min-width="100" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ isSubtotalRow(row) ? '' : row.typeEnumText }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="源库位" min-width="100" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ isSubtotalRow(row) ? '' : row.sourceLocationName }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="目的库位" min-width="100" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ isSubtotalRow(row) ? '' : row.purposeLocationName }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="领用科室" min-width="100" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ isSubtotalRow(row) ? '' : row.consumingOrganizationName }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="供应商" min-width="108" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ isSubtotalRow(row) ? '' : row.supplierName }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="品名"
                min-width="132"
                show-overflow-tooltip
                :class-name="REPORT_TABLE_COL_GROUP_START"
                :label-class-name="REPORT_TABLE_COL_GROUP_START"
              >
                <template #default="{ row }">
                  <span :class="{ 'document-detail-subtotal-label': isSubtotalRow(row) }">
                    {{ row.productName }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="编码" min-width="92" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ isSubtotalRow(row) ? '' : row.productBusNo }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="规格" min-width="92" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ isSubtotalRow(row) ? '' : row.specification }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="批号" min-width="88" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ isSubtotalRow(row) ? '' : row.lotNumber }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="数量"
                min-width="96"
                align="right"
                :class-name="`${REPORT_TABLE_NUM_CELL} ${REPORT_TABLE_COL_GROUP_START}`"
                :label-class-name="REPORT_TABLE_COL_GROUP_START"
              >
                <template #default="{ row }">{{ formatQtyWithUnit(row) }}</template>
              </ElTableColumn>
              <ElTableColumn
                label="单价"
                min-width="96"
                align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">{{ formatPriceWithUnit(row) }}</template>
              </ElTableColumn>
              <ElTableColumn
                label="金额"
                min-width="108"
                align="right"
                :class-name="`${REPORT_TABLE_NUM_CELL} ${REPORT_TABLE_AMOUNT_CELL}`"
              >
                <template #default="{ row }">
                  <span :class="{ 'document-detail-subtotal-amount': isSubtotalRow(row) }">
                    {{ formatAmountWithUnit(row) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="账面"
                min-width="76"
                align="right"
                :class-name="`${REPORT_TABLE_NUM_CELL} ${REPORT_TABLE_COL_GROUP_START}`"
                :label-class-name="REPORT_TABLE_COL_GROUP_START"
              >
                <template #default="{ row }">
                  <span :class="{ [REPORT_TABLE_EMPTY_VALUE]: isQtyEmpty(row.bookQuantity, row) }">
                    {{ formatQtyOrDash(row.bookQuantity, row) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="实盘"
                min-width="76"
                align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span
                    :class="{ [REPORT_TABLE_EMPTY_VALUE]: isQtyEmpty(row.actualQuantity, row) }"
                  >
                    {{ formatQtyOrDash(row.actualQuantity, row) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="盈亏量"
                min-width="76"
                align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span
                    :class="{ [REPORT_TABLE_EMPTY_VALUE]: isQtyEmpty(row.gainLossQuantity, row) }"
                  >
                    {{ formatQtyOrDash(row.gainLossQuantity, row) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="制单人"
                min-width="88"
                show-overflow-tooltip
                :class-name="REPORT_TABLE_COL_GROUP_START"
                :label-class-name="REPORT_TABLE_COL_GROUP_START"
              >
                <template #default="{ row }">
                  {{ isSubtotalRow(row) ? '' : row.applicantName }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="审批人" min-width="88" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ isSubtotalRow(row) ? '' : row.approverName }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="审批时间" min-width="118" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ formatApprovalTime(row.approvalTime, row) }}
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
          :total="total"
          @size-change="handlePageSizeChange"
          @current-change="handleCurrentPageChange"
        />
      </template>
    </ReportListPageLayout>
  </Page>
</template>

<style scoped>
/* 小计行与明细同行高（ReportElTable td 35px） */
:deep(.document-detail-subtotal-row > td.el-table__cell) {
  height: 35px;
  padding: 0;
  font-weight: 600;
  background: var(--el-fill-color) !important;
}

:deep(.document-detail-subtotal-row > td.el-table__cell .cell) {
  line-height: 1.35;
}

:deep(.el-table__body tr.document-detail-subtotal-row:hover > td.el-table__cell),
:deep(.el-table__body tr.document-detail-subtotal-row:hover > td.el-table-fixed-column--left),
:deep(.el-table__body tr.document-detail-subtotal-row:hover > td.el-table-fixed-column--right) {
  background: var(--el-fill-color) !important;
}

:deep(.document-detail-subtotal-label),
:deep(.document-detail-subtotal-amount) {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>
