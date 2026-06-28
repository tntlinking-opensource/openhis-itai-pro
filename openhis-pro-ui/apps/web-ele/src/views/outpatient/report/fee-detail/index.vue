<script setup lang="ts">
import type { FormItemConfig } from '@vben-core/table-layout-ui';

import type { FeeDetailTableRow } from './flatten-encounter-blocks';

import type {
  LoadOutpatientFeeDetailPageInitResponse,
  OutpatientFeeDetailAmountSummaryDto,
} from '#/api/outpatient/report/fee-detail';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import dayjs from 'dayjs';
import { ElButton, ElMessage, ElTag } from 'element-plus';

import {
  exportOutpatientFeeDetailApi,
  loadOutpatientFeeDetailPageInitApi,
  pageOutpatientFeeDetailApi,
} from '#/api/outpatient/report/fee-detail';
import { useRequest } from '#/hooks/use-request';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { FEE_DETAIL_SUMMARY_DISPLAY, FEE_DETAIL_TABLE_COLUMNS } from './constants';
import { resolveFeeDetailEncounterSpanMethod } from './encounter-column-merge';
import { flattenEncounterBlocksToTableRows } from './flatten-encounter-blocks';

defineOptions({ name: 'OutpatientReportFeeDetail' });

interface QueryFormState {
  pageNo: number;
  pageSize: number;
  dateRange: [string, string];
  tradeDirection: string;
  registrationDepartmentId?: string;
  feeCategoryCodes?: string[];
  subCategoryCodes?: string[];
  patientKeyword?: string;
  productNameKeyword?: string;
  productCodeKeyword?: string;
  cashierId?: string;
}

const FEE_CATEGORY_MEDICATION = 'MEDICATION';
const FEE_CATEGORY_ACTIVITY = 'ACTIVITY';
const FEE_CATEGORY_DEVICE = 'DEVICE';

const pageInit = ref<LoadOutpatientFeeDetailPageInitResponse | null>(null);
const tableData = ref<FeeDetailTableRow[]>([]);
const globalSummary = ref<OutpatientFeeDetailAmountSummaryDto | undefined>();
const total = ref(0);
const loading = ref(false);

const queryParams = reactive<QueryFormState>({
  pageNo: 1,
  pageSize: 10,
  dateRange: [dayjs().format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')],
  tradeDirection: 'ALL',
  registrationDepartmentId: undefined,
  feeCategoryCodes: undefined,
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

watch(pageLoading, (value) => {
  loading.value = value ?? false;
});

watch(initLoading, (value) => {
  if (value) {
    loading.value = true;
  }
});

const tradeDirectionOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.tradeDirectionOptions ?? [],
);

const feeCategoryOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.feeCategoryOptions ?? [],
);

const registrationDepartmentOptions = computed<ApiOptionItem[]>(
  () => pageInit.value?.registrationDepartmentOptions ?? [],
);

const cashierOptions = computed<ApiOptionItem[]>(() => pageInit.value?.cashierOptions ?? []);

// 细分类：按已选大类合并 fin_type 字典；未选大类时展示全部（按 value 去重）。
const subCategoryOptions = computed<ApiOptionItem[]>(() => {
  const init = pageInit.value;
  if (!init) {
    return [];
  }
  const selected = queryParams.feeCategoryCodes ?? [];
  const merged: ApiOptionItem[] = [];
  if (selected.length === 0) {
    merged.push(
      ...(init.medicationSubCategoryOptions ?? []),
      ...(init.activitySubCategoryOptions ?? []),
      ...(init.deviceSubCategoryOptions ?? []),
    );
  } else {
    if (selected.includes(FEE_CATEGORY_MEDICATION)) {
      merged.push(...(init.medicationSubCategoryOptions ?? []));
    }
    if (selected.includes(FEE_CATEGORY_ACTIVITY)) {
      merged.push(...(init.activitySubCategoryOptions ?? []));
    }
    if (selected.includes(FEE_CATEGORY_DEVICE)) {
      merged.push(...(init.deviceSubCategoryOptions ?? []));
    }
  }
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

// 输入框回车触发查询。
function handleKeywordEnter(event: KeyboardEvent) {
  if (event.key !== 'Enter') {
    return;
  }
  queryParams.pageNo = 1;
  void getList();
}

const keywordInputProps = {
  onKeyup: handleKeywordEnter,
};

// 筛选区非 input 变更：回到第一页并查询。
function handleFilterFieldChange() {
  queryParams.pageNo = 1;
  void getList();
}

// 大类变更时清空已选细分类，避免跨大类残留。
function handleFeeCategoryChange() {
  queryParams.subCategoryCodes = undefined;
  handleFilterFieldChange();
}

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '结算日期',
    prop: 'dateRange',
    type: 'daterange',
    valueFormat: 'YYYY-MM-DD',
  },
  {
    label: '收退方向',
    prop: 'tradeDirection',
    type: 'radio',
    options: tradeDirectionOptions.value,
    onChange: () => {
      handleFilterFieldChange();
    },
  },
  {
    label: '挂号科室',
    prop: 'registrationDepartmentId',
    type: 'select',
    options: registrationDepartmentOptions.value,
    multiple: false,
    clearable: true,
    filterable: true,
    onChange: () => {
      handleFilterFieldChange();
    },
  },
  {
    label: '费用大类',
    prop: 'feeCategoryCodes',
    type: 'select',
    options: feeCategoryOptions.value,
    multiple: true,
    clearable: true,
    filterable: true,
    collapseTags: true,
    onChange: () => {
      handleFeeCategoryChange();
    },
  },
  {
    label: '细分类',
    prop: 'subCategoryCodes',
    type: 'select',
    options: subCategoryOptions.value,
    multiple: true,
    clearable: true,
    filterable: true,
    collapseTags: true,
    onChange: () => {
      handleFilterFieldChange();
    },
  },
  {
    label: '患者',
    prop: 'patientKeyword',
    type: 'input',
    placeholder: '姓名 / 门诊号',
    extraprops: keywordInputProps,
  },
  {
    label: '项目名称',
    prop: 'productNameKeyword',
    type: 'input',
    placeholder: '模糊匹配',
    extraprops: keywordInputProps,
  },
  {
    label: '院内编码',
    prop: 'productCodeKeyword',
    type: 'input',
    placeholder: '与名称 AND',
    extraprops: keywordInputProps,
  },
  {
    label: '收费员',
    prop: 'cashierId',
    type: 'select',
    options: cashierOptions.value,
    multiple: false,
    clearable: true,
    filterable: true,
    onChange: () => {
      handleFilterFieldChange();
    },
  },
]);

// 将日期规范为 yyyy-MM-dd。
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

// 校验日期区间并组装查询/导出请求体。
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
  };
}

// 拉取当前页就诊块并平铺为列表行。
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
}

// 搜索按钮：回到第一页再查。
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

// 重置筛选项为初始化默认值。
function handleReset() {
  const init = pageInit.value;
  const dateFrom = init?.defaultDateFrom ?? dayjs().format('YYYY-MM-DD');
  const dateTo = init?.defaultDateTo ?? dayjs().format('YYYY-MM-DD');
  queryParams.dateRange = [dateFrom, dateTo];
  queryParams.tradeDirection = 'ALL';
  queryParams.registrationDepartmentId = undefined;
  queryParams.feeCategoryCodes = undefined;
  queryParams.subCategoryCodes = undefined;
  queryParams.patientKeyword = undefined;
  queryParams.productNameKeyword = undefined;
  queryParams.productCodeKeyword = undefined;
  queryParams.cashierId = undefined;
  queryParams.pageNo = 1;
  queryParams.pageSize = init?.defaultPageSize ?? 10;
  void getList();
}

// 导出当前筛选条件下的明细。
async function handleExport() {
  const filters = buildFilterPayload();
  if (!filters) {
    return;
  }
  await runExport(filters);
}

// 分页变更后重新查询。
function handlePagination({ page, limit }: { limit: number; page: number }) {
  queryParams.pageNo = page;
  queryParams.pageSize = limit;
  void getList();
}

// 金额展示：空值占位。
function formatAmount(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return String(value);
}

// 判断金额是否为负数（退费行）。
function isNegativeAmount(value: unknown): boolean {
  if (value === undefined || value === null || value === '') {
    return false;
  }
  const num = Number(value);
  return Number.isFinite(num) && num < 0;
}

// 金额列样式：负数标红。
function resolveAmountClass(value: unknown): string {
  return isNegativeAmount(value) ? 'text-[var(--el-color-danger)]' : '';
}

// 收退方向标签类型。
function resolveTradeDirectionTagType(tradeDirection?: string): 'danger' | 'info' | 'success' {
  if (tradeDirection === 'REFUND') {
    return 'danger';
  }
  if (tradeDirection === 'CHARGE') {
    return 'success';
  }
  return 'info';
}

// 收退方向文案。
function resolveTradeDirectionLabel(tradeDirection?: string): string {
  if (tradeDirection === 'REFUND') {
    return '退费';
  }
  if (tradeDirection === 'CHARGE') {
    return '收费';
  }
  return tradeDirection?.trim() || '—';
}

// 就诊小计行高亮。
function resolveRowClassName({ row }: { row: FeeDetailTableRow }): string {
  return row.rowType === 'subtotal' ? 'fee-detail-subtotal-row' : '';
}

// 同一门诊号下合并就诊头列（门诊号/患者/科室/日期）。
function handleEncounterSpanMethod(params: {
  column: Record<string, unknown>;
  row: Record<string, unknown>;
}) {
  return resolveFeeDetailEncounterSpanMethod({
    row: params.row as FeeDetailTableRow,
    column: params.column as { field?: string },
  });
}

// 全量合计摘要文案（toolbar 下方展示）。
const globalSummaryText = computed(() => {
  const summary = globalSummary.value;
  if (!summary) {
    return '';
  }
  return FEE_DETAIL_SUMMARY_DISPLAY.map((item) => {
    const value = formatAmount(summary[item.key]);
    return `${item.label} ${value}`;
  }).join('　');
});

// 加载页初始化下拉与默认日期。
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
  <Page auto-content-height>
    <TableLayout
      :table-data="tableData"
      :loading="loading || initLoading"
      :total="total"
      :query-params="queryParams as Record<string, unknown>"
      :form-items="formItems"
      :table-columns="FEE_DETAIL_TABLE_COLUMNS"
      row-key="rowKey"
      :row-class-name="resolveRowClassName"
      :span-method="handleEncounterSpanMethod"
      :show-top-query="true"
      :show-pagination="true"
      border
      stripe
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #toolbar-actions>
        <div class="flex flex-col gap-1">
          <span class="text-sm font-medium text-[var(--el-text-color-primary)]">门诊费用明细</span>
          <span v-if="globalSummaryText" class="text-xs text-[var(--el-text-color-regular)]">
            全量合计：{{ globalSummaryText }}
          </span>
        </div>
      </template>
      <template #toolbar-tools>
        <ElButton plain :loading="exportLoading" @click="handleExport">导出</ElButton>
      </template>

      <template #tradeDirection="{ row }">
        <template v-if="row.rowType === 'subtotal'">—</template>
        <ElTag
          v-else
          :type="resolveTradeDirectionTagType(row.tradeDirection as string)"
          size="small"
          effect="light"
        >
          {{ resolveTradeDirectionLabel(row.tradeDirection as string) }}
        </ElTag>
      </template>
      <template #quantityValue="{ row }">
        <span>{{ formatAmount(row.quantityValue) }}</span>
      </template>
      <template #baseAmount="{ row }">
        <span :class="resolveAmountClass(row.baseAmount)">{{ formatAmount(row.baseAmount) }}</span>
      </template>
      <template #discountAmount="{ row }">
        <span :class="resolveAmountClass(row.discountAmount)">
          {{ formatAmount(row.discountAmount) }}
        </span>
      </template>
      <template #receivableAmount="{ row }">
        <span :class="resolveAmountClass(row.receivableAmount)">
          {{ formatAmount(row.receivableAmount) }}
        </span>
      </template>
      <template #discountedAmount="{ row }">
        <span :class="resolveAmountClass(row.discountedAmount)">
          {{ formatAmount(row.discountedAmount) }}
        </span>
      </template>
      <template #unitPrice="{ row }">
        <span>{{ formatAmount(row.unitPrice) }}</span>
      </template>
      <template #lineAmount="{ row }">
        <span :class="resolveAmountClass(row.lineAmount)">{{ formatAmount(row.lineAmount) }}</span>
      </template>
      <template #actualReceivedAmount="{ row }">
        <span :class="resolveAmountClass(row.actualReceivedAmount)">
          {{ formatAmount(row.actualReceivedAmount) }}
        </span>
      </template>
      <template #insuranceAmount="{ row }">
        <span :class="resolveAmountClass(row.insuranceAmount)">
          {{ formatAmount(row.insuranceAmount) }}
        </span>
      </template>
      <template #selfPayAmount="{ row }">
        <span :class="resolveAmountClass(row.selfPayAmount)">
          {{ formatAmount(row.selfPayAmount) }}
        </span>
      </template>
    </TableLayout>
  </Page>
</template>

<style scoped>
:deep(.fee-detail-subtotal-row) {
  background-color: var(--el-fill-color-light) !important;
  font-weight: 500;
}

:deep(.fee-detail-subtotal-row .vxe-body--column) {
  color: var(--el-text-color-primary);
}

:deep(.vxe-body--column.col--ellipsis) {
  vertical-align: middle;
}
</style>
