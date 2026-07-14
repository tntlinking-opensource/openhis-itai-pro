<script setup lang="ts">
import type { FeeDetailTableRow } from './flatten-encounter-blocks';

import type {
  LoadOutpatientFeeDetailPageInitResponse,
  OutpatientFeeDetailAmountSummaryDto,
} from '#/api/outpatient/report/fee-detail';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { Download, Refresh, Search } from '@element-plus/icons-vue';
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
  exportOutpatientFeeDetailApi,
  loadOutpatientFeeDetailPageInitApi,
  pageOutpatientFeeDetailApi,
} from '#/api/outpatient/report/fee-detail';
import { useRequest } from '#/hooks/use-request';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { formatMoneyYuan } from '#/utils/format/money-scale';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import {
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATERANGE,
  REPORT_TABLE_AMOUNT_CELL,
  REPORT_TABLE_NUM_CELL,
  ReportElTable,
  ReportListPageLayout,
  ReportListToolbar,
  ReportPageFooter,
  ReportQueryBar,
  ReportTableScroll,
} from '#/views/report/shared';
import { buildReportDateRangeShortcuts } from '#/views/report/shared/report-date-range-shortcuts';

import { FEE_DETAIL_SUMMARY_DISPLAY } from './constants';
import { resolveFeeDetailEncounterSpanMethod } from './encounter-column-merge';
import { flattenEncounterBlocksToTableRows } from './flatten-encounter-blocks';

defineOptions({ name: 'OutpatientReportFeeDetail' });

interface QueryFormState {
  pageNo: number;
  pageSize: number;
  dateRange: [string, string];
  tradeDirection: string;
  registrationDepartmentId?: string;
  subCategoryCodes?: string[];
  patientKeyword?: string;
  productNameKeyword?: string;
  productCodeKeyword?: string;
  cashierId?: string;
}

const PAGE_TITLE = '门诊费用明细';

const dateRangeShortcuts = buildReportDateRangeShortcuts();

const userStore = useUserStore();
const pageInit = ref<LoadOutpatientFeeDetailPageInitResponse | null>(null);
const tableData = ref<FeeDetailTableRow[]>([]);
const globalSummary = ref<OutpatientFeeDetailAmountSummaryDto | undefined>();
const total = ref(0);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const queryParams = reactive<QueryFormState>({
  pageNo: 1,
  pageSize: 10,
  dateRange: [dayjs().format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')],
  tradeDirection: 'ALL',
  registrationDepartmentId: undefined,
  subCategoryCodes: undefined,
  patientKeyword: undefined,
  productNameKeyword: undefined,
  productCodeKeyword: undefined,
  cashierId: undefined,
});

const { run: runInit, loading: initLoading } = useRequest(loadOutpatientFeeDetailPageInitApi, {
  manual: true,
});

const { run: runPage, loading: pageLoading } = useRequest(pageOutpatientFeeDetailApi, {
  manual: true,
  onSuccess: (data) => {
    tableData.value = flattenEncounterBlocksToTableRows(data.content ?? []);
    total.value = data.totalCount ?? 0;
    globalSummary.value = data.summary;
  },
});

const { run: runExport, loading: exportLoading } = useRequest(exportOutpatientFeeDetailApi, {
  manual: true,
  onSuccess: (data) => {
    downloadBinaryExportFile(data, '门诊费用明细.xlsx');
    const rowCount = data.rowCount ?? data.exportedRowCount ?? 0;
    if (data.truncated) {
      ElMessage.warning('导出数据已触达行数上限，部分记录未包含在文件中');
    } else {
      ElMessage.success(`导出成功，共 ${rowCount} 行`);
    }
  },
});

const tableLoading = computed(() => initLoading.value || pageLoading.value);

const tradeDirectionOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.tradeDirectionOptions ?? [],
);

const registrationDepartmentOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.registrationDepartmentOptions ?? [],
);

const cashierOptions = computed<ApiOptionItem[]>(() => pageInit.value?.cashierOptions ?? []);

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
  return merged.filter((item) => {
    const value = String(item.value ?? '');
    if (!value || seen.has(value)) {
      return false;
    }
    seen.add(value);
    return true;
  });
});

const footerSummaryItems = computed(() => {
  const summary = globalSummary.value;
  if (!summary) {
    return [];
  }
  return FEE_DETAIL_SUMMARY_DISPLAY.map((item) => ({
    label: item.label,
    value: formatMoneyCell(summary[item.key]),
  }));
});

/** 默认收费员：当前登录医务人员，且须在下拉选项内。 */
function resolveDefaultCashierId(
  init: LoadOutpatientFeeDetailPageInitResponse | null | undefined,
): string | undefined {
  const loginId = resolveLoginPractitionerIdForRequest(userStore.userInfo);
  if (!loginId) {
    return undefined;
  }
  const matched = (init?.cashierOptions ?? []).some(
    (item) => preserveApiIdForRequest(item.value) === loginId,
  );
  return matched ? loginId : undefined;
}

/** 下拉选项转为 ElSelect 可用结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 将日期规范为 yyyy-MM-dd。 */
function normalizeDate(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) {
    return '';
  }
  const datePart = trimmed.slice(0, 10);
  if (/^\d{4}-\d{2}-\d{2}$/.test(datePart)) {
    return datePart;
  }
  return trimmed;
}

/** 校验日期区间并组装查询/导出请求体。 */
function buildFilterPayload() {
  const [rawFrom, rawTo] = queryParams.dateRange;
  const dateFrom = normalizeDate(rawFrom ?? '');
  const dateTo = normalizeDate(rawTo ?? '');
  if (!dateFrom || !dateTo) {
    ElMessage.warning('请选择结算日期');
    return null;
  }
  if (dayjs(dateFrom).isAfter(dayjs(dateTo))) {
    ElMessage.warning('开始日期不能晚于结束日期');
    return null;
  }
  return {
    dateFrom,
    dateTo,
    tradeDirection: queryParams.tradeDirection || 'ALL',
    registrationDepartmentId: preserveApiIdForRequest(queryParams.registrationDepartmentId),
    subCategoryCodes: queryParams.subCategoryCodes?.length
      ? queryParams.subCategoryCodes
      : undefined,
    patientKeyword: queryParams.patientKeyword?.trim() || undefined,
    productNameKeyword: queryParams.productNameKeyword?.trim() || undefined,
    productCodeKeyword: queryParams.productCodeKeyword?.trim() || undefined,
    cashierId: preserveApiIdForRequest(queryParams.cashierId),
  };
}

/** 拉取当前页就诊块并平铺为列表行。 */
async function getList() {
  const filters = buildFilterPayload();
  if (!filters) {
    return;
  }
  await runPage({
    ...filters,
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
  });
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 搜索按钮：回到第一页再查。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 重置筛选项为初始化默认值。 */
function handleReset() {
  const init = pageInit.value;
  const dateFrom = init?.defaultDateFrom ?? dayjs().format('YYYY-MM-DD');
  const dateTo = init?.defaultDateTo ?? dayjs().format('YYYY-MM-DD');
  queryParams.dateRange = [dateFrom, dateTo];
  queryParams.tradeDirection = 'ALL';
  queryParams.registrationDepartmentId = undefined;
  queryParams.subCategoryCodes = undefined;
  queryParams.patientKeyword = undefined;
  queryParams.productNameKeyword = undefined;
  queryParams.productCodeKeyword = undefined;
  queryParams.cashierId = resolveDefaultCashierId(init);
  queryParams.pageNo = 1;
  queryParams.pageSize = init?.defaultPageSize ?? 10;
  void getList();
}

/** 导出当前筛选条件下的明细。 */
async function handleExport() {
  const filters = buildFilterPayload();
  if (!filters) {
    return;
  }
  await runExport(filters);
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

/** 金额展示：2 位小数 + 元（BR-MON-001）。 */
function formatMoneyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return formatMoneyYuan(value);
}

/** 判断金额是否为负数（退费行）。 */
function isNegativeAmount(value: unknown): boolean {
  if (value === undefined || value === null || value === '') {
    return false;
  }
  const num = Number(value);
  return Number.isFinite(num) && num < 0;
}

/** 金额列样式：负数标红。 */
function resolveAmountClass(value: unknown): string {
  return isNegativeAmount(value) ? 'text-[var(--el-color-danger)]' : '';
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

/** 行样式：按就诊块统一底色（避免合并列与斑马纹错位），小计行单独强调。 */
function resolveRowClassName({ row }: { row: FeeDetailTableRow }): string {
  const blockIndex = row.encounterBlockIndex ?? 0;
  const toneClass = blockIndex % 2 === 0 ? 'fee-detail-block--even' : 'fee-detail-block--odd';
  if (row.rowType === 'subtotal') {
    return `${toneClass} fee-detail-subtotal-row`;
  }
  return toneClass;
}

/** 同一门诊号下合并就诊头列（门诊号/患者/科室/日期）。 */
function handleEncounterSpanMethod(params: {
  column: { property?: string };
  row: FeeDetailTableRow;
}) {
  return resolveFeeDetailEncounterSpanMethod({
    row: params.row,
    column: params.column,
  });
}

/** 加载页初始化下拉与默认日期。 */
async function loadPageInit() {
  const data = await runInit();
  pageInit.value = data;
  const dateFrom = data.defaultDateFrom ?? dayjs().format('YYYY-MM-DD');
  const dateTo = data.defaultDateTo ?? dayjs().format('YYYY-MM-DD');
  queryParams.dateRange = [dateFrom, dateTo];
  queryParams.pageSize = data.defaultPageSize ?? 10;
  if (data.tradeDirectionOptions?.length) {
    const hasAll = data.tradeDirectionOptions.some((item) => item.value === 'ALL');
    queryParams.tradeDirection = hasAll ? 'ALL' : (data.tradeDirectionOptions[0]?.value ?? 'ALL');
  }
  queryParams.cashierId = resolveDefaultCashierId(data);
}

onMounted(async () => {
  try {
    await loadPageInit();
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
            <ElFormItem label="收退方向">
              <ElRadioGroup v-model="queryParams.tradeDirection" :class="REPORT_QUERY_CONTROL">
                <ElRadioButton
                  v-for="item in toSelectOptions(tradeDirectionOptions)"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </ElRadioButton>
              </ElRadioGroup>
            </ElFormItem>
            <ElFormItem label="挂号科室">
              <ElSelect
                v-model="queryParams.registrationDepartmentId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
              >
                <ElOption
                  v-for="item in toSelectOptions(registrationDepartmentOptions)"
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
                placeholder="姓名 / 门诊号"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="项目名称">
              <ElInput
                v-model="queryParams.productNameKeyword"
                clearable
                placeholder="项目名称"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="项目编码">
              <ElInput
                v-model="queryParams.productCodeKeyword"
                clearable
                placeholder="项目编码"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="收费员">
              <ElSelect
                v-model="queryParams.cashierId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
              >
                <ElOption
                  v-for="item in toSelectOptions(cashierOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="结算日期">
              <ElDatePicker
                v-model="queryParams.dateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
                :shortcuts="dateRangeShortcuts"
                :class="[REPORT_QUERY_CONTROL, REPORT_QUERY_CONTROL_DATERANGE]"
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
        <ReportListToolbar :title="`${PAGE_TITLE} · 明细列表`" :total="total">
          <template #actions>
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
              class="fee-detail-report-table"
              :stripe="false"
              :loading="tableLoading"
              :data="tableData"
              :height="tableHeight"
              row-key="rowKey"
              :row-class-name="resolveRowClassName"
              :span-method="handleEncounterSpanMethod"
            >
              <ElTableColumn
                prop="busNo"
                label="门诊号"
                width="120"
                align="center"
                fixed="left"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="patientName"
                label="患者"
                width="90"
                align="center"
                fixed="left"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="registrationDepartmentName"
                label="挂号科室"
                width="100"
                align="center"
                fixed="left"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="encounterDate"
                label="就诊日期"
                width="110"
                align="center"
                fixed="left"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="subCategoryDisplay"
                label="项目类别"
                width="88"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="productName"
                label="项目名称"
                min-width="120"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="productCode"
                label="项目编码"
                width="100"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn label="数量" width="88" align="center" header-align="center">
                <template #default="{ row }">
                  {{ formatQuantityWithUnit(row.quantityValue, row.quantityUnitDisplay) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="orderingDoctorName"
                label="开单医生"
                width="88"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="cashierName"
                label="收费人"
                width="88"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="billDate"
                label="结算时间"
                width="160"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn prop="tradeDirection" label="来源" width="72" align="center">
                <template #default="{ row }">
                  <template v-if="row.rowType === 'subtotal'">—</template>
                  <ElTag
                    v-else
                    :type="resolveTradeDirectionTagType(row.tradeDirection as string)"
                    effect="light"
                  >
                    {{ resolveTradeDirectionLabel(row.tradeDirection as string) }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="insuranceLevelDisplay"
                label="医保等级"
                width="88"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="insuranceTypeDisplay"
                label="医保类型"
                width="88"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                label="原价"
                prop="baseAmount"
                width="96"
                align="right"
                header-align="right"
                :class-name="`${REPORT_TABLE_NUM_CELL} ${REPORT_TABLE_AMOUNT_CELL}`"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.baseAmount)">
                    {{ formatMoneyCell(row.baseAmount) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="优惠金额"
                prop="discountAmount"
                width="96"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.discountAmount)">
                    {{ formatMoneyCell(row.discountAmount) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="应收金额"
                prop="receivableAmount"
                width="96"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.receivableAmount)">
                    {{ formatMoneyCell(row.receivableAmount) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="折后金额"
                prop="discountedAmount"
                width="96"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.discountedAmount)">
                    {{ formatMoneyCell(row.discountedAmount) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="单价"
                prop="unitPrice"
                width="96"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.unitPrice)">
                    {{ formatMoneyCell(row.unitPrice) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="实收金额"
                prop="actualReceivedAmount"
                width="96"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.actualReceivedAmount)">
                    {{ formatMoneyCell(row.actualReceivedAmount) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="医保金额"
                prop="insuranceAmount"
                width="96"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.insuranceAmount)">
                    {{ formatMoneyCell(row.insuranceAmount) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="自费金额"
                prop="selfPayAmount"
                width="96"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="resolveAmountClass(row.selfPayAmount)">
                    {{ formatMoneyCell(row.selfPayAmount) }}
                  </span>
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
