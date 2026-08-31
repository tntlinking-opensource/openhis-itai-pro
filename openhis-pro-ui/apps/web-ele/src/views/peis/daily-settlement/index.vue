<!--
  体检日结：按收费日查询已结体检清单明细与人次/金额汇总。
-->
<script setup lang="ts">
import type {
  ListPhysicalExamDailySettlementResponse,
  PhysicalExamDailySettlementLineDto,
} from '#/api/peis/daily-settlement';
import type { ReportPrintColumnDef } from '#/utils/print/print-report-list';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Printer, Refresh, Search } from '@element-plus/icons-vue';
import { ElButton, ElDatePicker, ElFormItem, ElMessage, ElTableColumn } from 'element-plus';

import dayjs from 'dayjs';

import { listPhysicalExamDailySettlementApi } from '#/api/peis/daily-settlement';
import { useRequest } from '#/hooks/use-request';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import {
  mapReportPrintRow,
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATERANGE,
  REPORT_TABLE_AMOUNT_CELL,
  ReportElTable,
  ReportListPageLayout,
  ReportListToolbar,
  ReportPrintFieldDialog,
  ReportQueryBar,
  ReportTableScroll,
  reportPrintFieldsOf,
  useReportListPrint,
} from '#/views/report/shared';
import { buildReportDateRangeShortcuts } from '#/views/report/shared/report-date-range-shortcuts';

defineOptions({ name: 'PhysicalExamDailySettlement' });

interface QueryState {
  dateFrom: string;
  dateTo: string;
}

interface SpanMethodParams {
  column: { property?: string };
  rowIndex: number;
}

const PAGE_TITLE = '体检日结';
const SHEET_TITLE = '个人体检费用清单';
const today = dayjs().format('YYYY-MM-DD');

const queryParams = reactive<QueryState>({
  dateFrom: today,
  dateTo: today,
});
const lines = ref<PhysicalExamDailySettlementLineDto[]>([]);
const queryTimeText = ref(`${today}至${today}`);
const visitCountText = ref('0人');
const totalAmount = ref<number | string>(0);
const queried = ref(false);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);
const dateRangeShortcuts = buildReportDateRangeShortcuts();

const queryBarModel = computed(() => queryParams as Record<string, unknown>);

/** 业务日期与 daterange 双向绑定。 */
const businessDateRange = computed({
  get() {
    if (queryParams.dateFrom && queryParams.dateTo) {
      return [queryParams.dateFrom, queryParams.dateTo] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.dateFrom = value?.[0] ?? '';
    queryParams.dateTo = value?.[1] ?? '';
  },
});

/** 科室列连续相同行的 rowspan（0 表示被合并）。 */
const departmentSpans = computed(() =>
  buildConsecutiveSpans(lines.value, (row) => row.departmentName ?? ''),
);

/** 开单人列按科室+开单人连续相同行的 rowspan。 */
const ordererSpans = computed(() =>
  buildConsecutiveSpans(
    lines.value,
    (row) => `${row.departmentName ?? ''}|${row.ordererName ?? ''}`,
  ),
);

const printColumnCatalog: ReportPrintColumnDef[] = [
  { field: 'departmentName', title: '科室', align: 'center', widthWeight: 1.2 },
  { field: 'ordererName', title: '开单人', align: 'center', widthWeight: 1 },
  { field: 'patientName', title: '病人', align: 'center', widthWeight: 1 },
  { field: 'itemName', title: '项目名称', align: 'center', widthWeight: 1.6 },
  { field: 'amount', title: '金额', align: 'right', widthWeight: 1 },
];

const { printFieldDialogRef, printSubmitting, handleOpenPrintFields, handlePrintCurrentPage } =
  useReportListPrint({
    getReportCode: () => 'peis-daily-settlement',
    getColumnCatalog: () => printColumnCatalog,
    getReportTitle: () => SHEET_TITLE,
    getPrintRows: () => {
      const fields = reportPrintFieldsOf(printColumnCatalog);
      const printRows = lines.value.map((row) =>
        mapReportPrintRow(
          {
            departmentName: row.departmentName ?? '',
            ordererName: row.ordererName ?? '',
            patientName: row.patientName ?? '',
            itemName: row.itemName ?? '',
            amount: formatAmount(row.amount),
          },
          fields,
        ),
      );
      if (printRows.length > 0) {
        printRows.push(
          mapReportPrintRow(
            {
              departmentName: '合计：',
              ordererName: '',
              patientName: '',
              itemName: '',
              amount: formatAmount(totalAmount.value),
            },
            fields,
          ),
        );
      }
      return printRows;
    },
  });

const { run: runQuery, loading } = useRequest(listPhysicalExamDailySettlementApi, {
  manual: true,
  onSuccess: (data) => {
    applyQueryResult(data);
  },
});

/**
 * 将接口结果写入页面展示态。
 *
 * @param data 一日清单响应
 */
function applyQueryResult(data?: ListPhysicalExamDailySettlementResponse | null) {
  lines.value = data?.lines ?? [];
  queryTimeText.value = data?.queryTimeText || `${queryParams.dateFrom}至${queryParams.dateTo}`;
  visitCountText.value = data?.visitCountText || `${Number(data?.visitCount ?? 0)}人`;
  totalAmount.value = data?.totalAmount ?? 0;
  queried.value = true;
}

/**
 * 按连续相同 key 计算 rowspan：组首行为组长度，其余为 0。
 *
 * @param rows 明细
 * @param keyOf 分组键
 */
function buildConsecutiveSpans(
  rows: PhysicalExamDailySettlementLineDto[],
  keyOf: (row: PhysicalExamDailySettlementLineDto) => string,
): number[] {
  const spans = rows.map(() => 0);
  let index = 0;
  while (index < rows.length) {
    const key = keyOf(rows[index] as PhysicalExamDailySettlementLineDto);
    let end = index + 1;
    while (end < rows.length) {
      const next = rows[end] as PhysicalExamDailySettlementLineDto;
      if (keyOf(next) !== key) {
        break;
      }
      end += 1;
    }
    spans[index] = end - index;
    index = end;
  }
  return spans;
}

/** 金额展示为两位小数。 */
function formatAmount(value?: null | number | string): string {
  if (value == null || value === '') {
    return '0.00';
  }
  return formatMoneyDisplay(value);
}

/**
 * 科室/开单人列纵向合并。
 *
 * @param params 表格 span 参数
 */
function handleSpanMethod(
  params: SpanMethodParams,
): { colspan: number; rowspan: number } | undefined {
  const property = params.column.property;
  if (property === 'departmentName') {
    return toSpan(departmentSpans.value[params.rowIndex]);
  }
  if (property === 'ordererName') {
    return toSpan(ordererSpans.value[params.rowIndex]);
  }
  return undefined;
}

/**
 * 将预计算 rowspan 转为 ElTable span。
 *
 * @param rowspan 组首 >0，被合并格为 0
 */
function toSpan(rowspan: number | undefined): { colspan: number; rowspan: number } {
  if (rowspan && rowspan > 0) {
    return { rowspan, colspan: 1 };
  }
  return { rowspan: 0, colspan: 0 };
}

/**
 * 表尾合计：首列「合计：」，金额列展示汇总。
 *
 * @param param 表格列
 */
function getSummaries(param: { columns: Array<{ property?: string }> }): string[] {
  return param.columns.map((column, index) => {
    if (index === 0) {
      return '合计：';
    }
    if (column.property === 'amount') {
      return formatAmount(totalAmount.value);
    }
    return '';
  });
}

/** 校验日期后查询一日清单。 */
async function handleQuery() {
  if (!queryParams.dateFrom || !queryParams.dateTo) {
    ElMessage.warning('请选择查询日期');
    return;
  }
  await runQuery({
    dateFrom: queryParams.dateFrom,
    dateTo: queryParams.dateTo,
  });
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 重置为当天并立即查询。 */
function handleReset() {
  const current = dayjs().format('YYYY-MM-DD');
  queryParams.dateFrom = current;
  queryParams.dateTo = current;
  handleQuery();
}

onMounted(() => {
  handleQuery();
});
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryBarModel">
          <template #fields>
            <ElFormItem label="查询时间">
              <ElDatePicker
                v-model="businessDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
                :clearable="false"
                :shortcuts="dateRangeShortcuts"
                :class="[REPORT_QUERY_CONTROL, REPORT_QUERY_CONTROL_DATERANGE]"
                @change="handleQuery"
              />
            </ElFormItem>
          </template>
          <template #actions>
            <ElButton type="primary" :icon="Search" :loading="loading" @click="handleQuery">
              搜索
            </ElButton>
            <ElButton :icon="Refresh" :loading="loading" @click="handleReset">重置</ElButton>
          </template>
        </ReportQueryBar>
      </template>

      <template #toolbar>
        <ReportListToolbar :title="PAGE_TITLE" :total="lines.length">
          <template #meta>
            <span v-if="queried" class="daily-settlement-meta">
              查询时间 {{ queryTimeText }} · 门诊人次 {{ visitCountText }}
            </span>
          </template>
          <template #actions>
            <ElButton
              plain
              :disabled="printSubmitting || lines.length === 0"
              @click="handleOpenPrintFields"
            >
              打印设置
            </ElButton>
            <ElButton
              type="primary"
              plain
              :icon="Printer"
              :loading="printSubmitting"
              :disabled="lines.length === 0"
              @click="handlePrintCurrentPage"
            >
              打印
            </ElButton>
          </template>
        </ReportListToolbar>
      </template>

      <template #table>
        <ReportTableScroll ref="tableScrollRef">
          <template #default="{ tableHeight }">
            <div class="daily-settlement-sheet" :style="{ height: `${tableHeight}px` }">
              <div class="daily-settlement-sheet__title">{{ SHEET_TITLE }}</div>
              <div class="daily-settlement-sheet__meta">
                <div>查询时间：{{ queryTimeText }}</div>
                <div>门诊人次：{{ visitCountText }}</div>
              </div>
              <div class="daily-settlement-sheet__table">
                <ReportElTable
                  :data="lines"
                  :loading="loading"
                  :span-method="handleSpanMethod"
                  :summary-method="getSummaries"
                  show-summary
                  height="100%"
                >
                  <ElTableColumn
                    prop="departmentName"
                    label="科室"
                    min-width="140"
                    align="center"
                    header-align="center"
                    show-overflow-tooltip
                  />
                  <ElTableColumn
                    prop="ordererName"
                    label="开单人"
                    min-width="100"
                    align="center"
                    header-align="center"
                    show-overflow-tooltip
                  />
                  <ElTableColumn
                    prop="patientName"
                    label="病人"
                    min-width="100"
                    align="center"
                    header-align="center"
                    show-overflow-tooltip
                  />
                  <ElTableColumn
                    prop="itemName"
                    label="项目名称"
                    min-width="180"
                    align="center"
                    header-align="center"
                    show-overflow-tooltip
                  />
                  <ElTableColumn
                    prop="amount"
                    label="金额"
                    min-width="100"
                    align="right"
                    header-align="right"
                    :class-name="REPORT_TABLE_AMOUNT_CELL"
                  >
                    <template #default="{ row }">
                      {{ formatAmount(row.amount) }}
                    </template>
                  </ElTableColumn>
                </ReportElTable>
              </div>
            </div>
          </template>
        </ReportTableScroll>
      </template>
    </ReportListPageLayout>
    <ReportPrintFieldDialog
      ref="printFieldDialogRef"
      report-code="peis-daily-settlement"
      :column-catalog="printColumnCatalog"
    />
  </Page>
</template>

<style scoped>
.daily-settlement-meta {
  margin-left: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.daily-settlement-sheet {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 8px 12px 0;
}

.daily-settlement-sheet__table {
  flex: 1;
  min-height: 0;
}

.daily-settlement-sheet__title {
  margin-bottom: 8px;
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  text-align: center;
}

.daily-settlement-sheet__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}
</style>
