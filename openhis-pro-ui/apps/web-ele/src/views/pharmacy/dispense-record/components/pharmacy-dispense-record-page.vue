<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import dayjs from 'dayjs';
import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElRadioButton, ElRadioGroup, ElTag, ElTooltip } from 'element-plus';

import {
  enrichDispenseRecordRowsForDisplay,
  formatDispenseRecordPrescriptionNoDisplay,
  resolveDispenseRecordSpanMethod,
  type DispenseRecordDisplayMode,
  type DispenseRecordSourceRow,
  type DispenseRecordTableRow,
} from '../services/dispense-record-group-display';

import {
  exportInpatientPharmacyDispenseRecordsApi,
  loadInpatientPharmacyDispenseRecordPageInitApi,
  pageInpatientPharmacyDispenseRecordsApi,
} from '#/api/pharmacy/inpatient-dispense-record';
import {
  exportOutpatientPharmacyDispenseRecordsApi,
  loadOutpatientPharmacyDispenseRecordPageInitApi,
  pageOutpatientPharmacyDispenseRecordsApi,
} from '#/api/pharmacy/outpatient-dispense-record';
import { useRequest } from '#/hooks/use-request';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'PharmacyDispenseRecordPage' });

interface Props {
  /** 门诊或住院发退药记录场景 */
  scope: 'inpatient' | 'outpatient';
}

const props = withDefaults(defineProps<Props>(), {});

interface QueryFormState {
  pageNo: number;
  pageSize: number;
  dispenseDateRange: [string, string];
  recordTypeCode?: string;
  pharmacyLocationId?: string;
  medicationCategoryCodes?: string[];
  dispenserPractitionerId?: string;
  drugKeyword?: string;
  patientKeyword?: string;
}

// 默认发药日期：当月 1 日～月末（yyyy-MM-dd）。
function buildThisMonthDateRange(): [string, string] {
  return [
    dayjs().startOf('month').format('YYYY-MM-DD'),
    dayjs().endOf('month').format('YYYY-MM-DD'),
  ];
}

// 写回默认发药日期区间（进入页、重置时共用）。
function applyDefaultDispenseDateRange() {
  const range = buildThisMonthDateRange();
  defaultDispenseDateRange.value = range;
  queryParams.dispenseDateRange = [...range];
}

const pharmacyOptions = ref<ApiOptionItem[]>([]);
const medicationCategoryOptions = ref<ApiOptionItem[]>([]);
const dispenserOptions = ref<ApiOptionItem[]>([]);
const recordTypeOptions = ref<ApiOptionItem[]>([]);
// 页初始化默认发药日期（本月），重置时恢复。
const defaultDispenseDateRange = ref<[string, string]>(buildThisMonthDateRange());

const rawTableData = ref<DispenseRecordSourceRow[]>([]);
const total = ref(0);
const loading = ref(false);
// 默认按处方合并：处方号在组头完整展示，同方药品明细纵向合并公共列。
const displayMode = ref<DispenseRecordDisplayMode>('BY_PRESCRIPTION');

const tableData = computed<DispenseRecordTableRow[]>(() =>
  enrichDispenseRecordRowsForDisplay(rawTableData.value, displayMode.value),
);

const queryParams = reactive<QueryFormState>({
  pageNo: 1,
  pageSize: 20,
  dispenseDateRange: [...defaultDispenseDateRange.value],
  recordTypeCode: undefined,
  pharmacyLocationId: undefined,
  medicationCategoryCodes: undefined,
  dispenserPractitionerId: undefined,
  drugKeyword: undefined,
  patientKeyword: undefined,
});

const pageTitle = computed(() =>
  props.scope === 'outpatient' ? '门诊发退药记录' : '住院发退药记录',
);

const defaultExportFileName = computed(() =>
  props.scope === 'outpatient' ? '门诊发退药记录.xlsx' : '住院发退药记录.xlsx',
);

const { run: runInit, loading: initLoading } = useRequest(
  props.scope === 'outpatient'
    ? loadOutpatientPharmacyDispenseRecordPageInitApi
    : loadInpatientPharmacyDispenseRecordPageInitApi,
  { manual: true },
);

const { run: runPage, loading: pageLoading } = useRequest(
  props.scope === 'outpatient'
    ? pageOutpatientPharmacyDispenseRecordsApi
    : pageInpatientPharmacyDispenseRecordsApi,
  {
    manual: true,
    onSuccess: (data) => {
      rawTableData.value = (data?.content ?? []) as DispenseRecordSourceRow[];
      total.value = data?.totalCount ?? 0;
    },
  },
);

const { run: runExport, loading: exportLoading } = useRequest(
  props.scope === 'outpatient'
    ? exportOutpatientPharmacyDispenseRecordsApi
    : exportInpatientPharmacyDispenseRecordsApi,
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

watch(pageLoading, (value) => {
  loading.value = value ?? false;
});

// 筛选区非 input 变更：回到第一页并查询。
function handleFilterFieldChange() {
  queryParams.pageNo = 1;
  void getList();
}

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

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '发药日期',
    prop: 'dispenseDateRange',
    type: 'daterange',
    valueFormat: 'YYYY-MM-DD',
    onChange: () => {
      handleFilterFieldChange();
    },
  },
  {
    label: '记录类型',
    prop: 'recordTypeCode',
    type: 'select',
    options: recordTypeOptions.value,
    multiple: false,
    clearable: true,
    onChange: () => {
      handleFilterFieldChange();
    },
  },
  {
    label: '药房',
    prop: 'pharmacyLocationId',
    type: 'select',
    options: pharmacyOptions.value,
    multiple: false,
    clearable: true,
    onChange: () => {
      handleFilterFieldChange();
    },
  },
  {
    label: '项目类型',
    prop: 'medicationCategoryCodes',
    type: 'select',
    options: medicationCategoryOptions.value,
    multiple: true,
    clearable: true,
    onChange: () => {
      handleFilterFieldChange();
    },
  },
  {
    label: '发药人',
    prop: 'dispenserPractitionerId',
    type: 'select',
    options: dispenserOptions.value,
    multiple: false,
    clearable: true,
    onChange: () => {
      handleFilterFieldChange();
    },
  },
  {
    label: '药品',
    prop: 'drugKeyword',
    type: 'input',
    placeholder: '名称/编码/拼音',
    extraprops: keywordInputProps,
  },
  {
    label: '患者',
    prop: 'patientKeyword',
    type: 'input',
    placeholder: '姓名等',
    extraprops: keywordInputProps,
  },
]);

const tableColumns = computed<TableColumn[]>(() => {
  const baseColumns: TableColumn[] = [
    { type: 'index', label: '序号', width: 56, align: 'center' },
    {
      prop: 'prescriptionNo',
      label: '处方号',
      minWidth: 200,
      showOverflowTooltip: true,
      slot: 'prescriptionNo',
    },
    {
      prop: 'recordTypeText',
      label: '类型',
      width: 72,
      align: 'center',
      slot: 'recordType',
    },
    { prop: 'patientName', label: '患者', width: 90, showOverflowTooltip: true },
  ];
  if (props.scope === 'outpatient') {
    baseColumns.push({
      prop: 'encounterBusNo',
      label: '门诊号',
      width: 130,
      showOverflowTooltip: true,
    });
  } else {
    baseColumns.push(
      { prop: 'inpatientNo', label: '住院号', width: 130, showOverflowTooltip: true },
      { prop: 'wardName', label: '病区', width: 100, showOverflowTooltip: true },
      { prop: 'bedName', label: '床号', width: 72, align: 'center' },
    );
  }
  baseColumns.push(
    {
      prop: 'orderingDepartmentName',
      label: '开单科室',
      width: 110,
      showOverflowTooltip: true,
    },
    { prop: 'dispenseTime', label: '发药时间', width: 168, showOverflowTooltip: true },
    { prop: 'pharmacyLocationName', label: '药房', width: 120, showOverflowTooltip: true },
    { prop: 'dispenserName', label: '发药人', width: 90, showOverflowTooltip: true },
    { prop: 'medicationName', label: '药品名称', minWidth: 140, showOverflowTooltip: true },
    { prop: 'medicationSpec', label: '规格', width: 100, showOverflowTooltip: true },
    { prop: 'quantity', label: '数量', width: 72, align: 'right', slot: 'quantity' },
    { prop: 'unitText', label: '单位', width: 56, align: 'center' },
    { prop: 'unitPrice', label: '单价', width: 88, align: 'right', slot: 'unitPrice' },
    { prop: 'totalAmount', label: '金额', width: 88, align: 'right', slot: 'totalAmount' },
    { prop: 'lotNumber', label: '批号', width: 100, showOverflowTooltip: true },
    { prop: 'traceNo', label: '追溯码', width: 120, showOverflowTooltip: true },
    { prop: 'usageText', label: '用法用量', width: 120, showOverflowTooltip: true },
    { prop: 'summaryNo', label: '汇总单号', width: 130, showOverflowTooltip: true },
  );
  return baseColumns;
});

// 单元格合并：按就诊或按处方号纵向合并组头列。
function handleDispenseRecordSpanMethod(params: {
  column: Record<string, unknown>;
  row: Record<string, unknown>;
}) {
  return resolveDispenseRecordSpanMethod({
    row: params.row as DispenseRecordTableRow,
    column: params.column as { field?: string },
    displayMode: displayMode.value,
  });
}

// 处方号列展示（退药行去 T 前缀，Tooltip 保留存库号）。
function resolvePrescriptionNoDisplay(row: DispenseRecordTableRow) {
  return formatDispenseRecordPrescriptionNoDisplay(row);
}

// 记录类型标签样式。
function resolveRecordTypeTagType(recordTypeCode?: string): 'danger' | 'info' | 'success' {
  if (recordTypeCode === 'REFUND') {
    return 'danger';
  }
  if (recordTypeCode === 'DISPENSE') {
    return 'success';
  }
  return 'info';
}

// 金额/数量展示：空值显示占位符。
function formatAmount(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return String(value);
}

// 将日期规范为 yyyy-MM-dd（后端 LocalDate 不接受时间部分）。
function normalizeDispenseDate(value: string): string {
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

// 校验发药日期区间并组装查询/导出请求体。
function buildFilterPayload() {
  const [rawFrom, rawTo] = queryParams.dispenseDateRange;
  const dispenseDateFrom = normalizeDispenseDate(rawFrom ?? '');
  const dispenseDateTo = normalizeDispenseDate(rawTo ?? '');
  if (!dispenseDateFrom || !dispenseDateTo) {
    ElMessage.warning('请选择发药日期');
    return null;
  }
  return {
    dispenseDateFrom,
    dispenseDateTo,
    recordTypeCode: queryParams.recordTypeCode || undefined,
    pharmacyLocationId: preserveApiIdForRequest(queryParams.pharmacyLocationId),
    medicationCategoryCodes: queryParams.medicationCategoryCodes?.length
      ? queryParams.medicationCategoryCodes
      : undefined,
    dispenserPractitionerId: preserveApiIdForRequest(queryParams.dispenserPractitionerId),
    drugKeyword: queryParams.drugKeyword?.trim() || undefined,
    patientKeyword: queryParams.patientKeyword?.trim() || undefined,
  };
}

// 拉取分页列表（搜索按钮 / 回车 / 筛选项 change 共用）。
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

// 导出当前筛选条件下的全部记录。
async function handleExport() {
  const filters = buildFilterPayload();
  if (!filters) {
    return;
  }
  await runExport(filters);
}

// 重置筛选项为初始化默认值。
function handleReset() {
  applyDefaultDispenseDateRange();
  queryParams.recordTypeCode = undefined;
  queryParams.pharmacyLocationId = undefined;
  queryParams.medicationCategoryCodes = undefined;
  queryParams.dispenserPractitionerId = undefined;
  queryParams.drugKeyword = undefined;
  queryParams.patientKeyword = undefined;
  queryParams.pageNo = 1;
  getList();
}

// 分页变更后重新查询。
function handlePagination({ page, limit }: { page: number; limit: number }) {
  queryParams.pageNo = page;
  queryParams.pageSize = limit;
  getList();
}

// 加载页初始化下拉与默认日期。
async function loadPageInit() {
  const data = await runInit();
  pharmacyOptions.value = data.pharmacyOptions ?? [];
  medicationCategoryOptions.value = data.medicationCategoryOptions ?? [];
  dispenserOptions.value = data.dispenserOptions ?? [];
  recordTypeOptions.value = data.recordTypeOptions ?? [];
  applyDefaultDispenseDateRange();
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
      :table-columns="tableColumns"
      row-key="rowKey"
      :span-method="handleDispenseRecordSpanMethod"
      :show-top-query="true"
      :show-pagination="true"
      border
      stripe
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #toolbar-actions>
        <span class="text-sm font-medium text-[var(--el-text-color-primary)]">{{ pageTitle }}</span>
      </template>
      <template #toolbar-tools>
        <div class="flex flex-wrap items-center gap-3">
          <ElRadioGroup v-model="displayMode" size="small">
            <ElRadioButton value="BY_PRESCRIPTION">按处方</ElRadioButton>
            <ElRadioButton value="BY_ENCOUNTER">按就诊</ElRadioButton>
            <ElRadioButton value="FLAT">平铺</ElRadioButton>
          </ElRadioGroup>
          <ElButton plain :loading="exportLoading" @click="handleExport">导出</ElButton>
        </div>
      </template>

      <template #prescriptionNo="{ row }">
        <ElTooltip
          v-if="resolvePrescriptionNoDisplay(row).tooltip"
          :content="resolvePrescriptionNoDisplay(row).tooltip"
          placement="top"
        >
          <span class="dispense-record-rx-no">
            {{ resolvePrescriptionNoDisplay(row).text }}
          </span>
        </ElTooltip>
        <span v-else class="dispense-record-rx-no">
          {{ resolvePrescriptionNoDisplay(row).text }}
        </span>
      </template>
      <template #recordType="{ row }">
        <ElTag
          :type="resolveRecordTypeTagType(row.recordTypeCode as string)"
          size="small"
          effect="light"
        >
          {{ row.recordTypeText || '—' }}
        </ElTag>
      </template>
      <template #quantity="{ row }">
        <span>{{ formatAmount(row.quantity) }}</span>
      </template>
      <template #unitPrice="{ row }">
        <span>{{ formatAmount(row.unitPrice) }}</span>
      </template>
      <template #totalAmount="{ row }">
        <span :class="Number(row.totalAmount) < 0 ? 'text-[var(--el-color-danger)]' : ''">
          {{ formatAmount(row.totalAmount) }}
        </span>
      </template>
    </TableLayout>
  </Page>
</template>

<style scoped>
.dispense-record-rx-no {
  display: inline-block;
  max-width: 100%;
  line-height: 1.4;
  word-break: break-all;
}
</style>
