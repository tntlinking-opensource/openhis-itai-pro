<script setup lang="ts">
import type { ApiOptionItem } from '#/types/api-response';
import type { ExcelExportSourceColumn } from '#/utils/export/build-excel-export-columns';

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
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTableColumn,
  ElTag,
  ElTooltip,
} from 'element-plus';

import dayjs from 'dayjs';

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
import TraceCodeTooltip from '#/components/trace/trace-code-tooltip.vue';
import { useRequest } from '#/hooks/use-request';
import { buildExcelExportColumns } from '#/utils/export/build-excel-export-columns';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { formatMoneyDisplay, formatMoneyYuan } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATERANGE,
  REPORT_TABLE_NUM_CELL,
  ReportElTable,
  ReportListPageLayout,
  ReportListToolbar,
  ReportPageFooter,
  ReportQueryBar,
  ReportTableScroll,
} from '#/views/report/shared';

defineOptions({ name: 'PharmacyDispenseRecordPage' });

interface Props {
  /** 门诊或住院发退药记录场景 */
  scope: 'inpatient' | 'outpatient';
}

const props = withDefaults(defineProps<Props>(), {});

/** 与表格可见列对齐的导出目录（数量单独展开为 quantity+unitText）。 */
const DISPENSE_RECORD_EXPORT_COLUMNS: ExcelExportSourceColumn[] = [
  { field: 'prescriptionNo', title: '处方号' },
  { field: 'recordTypeText', title: '类型' },
  { field: 'patientName', title: '患者' },
  { field: 'encounterBusNo', title: '门诊号' },
  { field: 'inpatientNo', title: '住院号' },
  { field: 'wardName', title: '病区' },
  { field: 'bedName', title: '床号' },
  { field: 'medicationName', title: '药品名称' },
  { field: 'medicationSpec', title: '规格' },
  { field: 'quantityDisplay', title: '数量' },
  { field: 'unitPrice', title: '单价' },
  { field: 'totalAmount', title: '金额' },
  { field: 'lotNumber', title: '批号' },
  { field: 'traceNo', title: '追溯码' },
  { field: 'manufacturerText', title: '厂家' },
  { field: 'summaryNo', title: '汇总单号' },
  { field: 'orderingDepartmentName', title: '开单科室' },
  { field: 'dispenseTime', title: '发药时间' },
  { field: 'pharmacyLocationName', title: '药房' },
  { field: 'dispenserName', title: '发药人' },
];

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

/** 默认发药日期：当月 1 日～月末（yyyy-MM-dd）。 */
function buildThisMonthDateRange(): [string, string] {
  return [
    dayjs().startOf('month').format('YYYY-MM-DD'),
    dayjs().endOf('month').format('YYYY-MM-DD'),
  ];
}

/** 写回默认发药日期区间（进入页、重置时共用）。 */
function applyDefaultDispenseDateRange() {
  const range = buildThisMonthDateRange();
  defaultDispenseDateRange.value = range;
  queryParams.dispenseDateRange = [...range];
}

const pharmacyOptions = ref<ApiOptionItem[]>([]);
const medicationCategoryOptions = ref<ApiOptionItem[]>([]);
const dispenserOptions = ref<ApiOptionItem[]>([]);
const recordTypeOptions = ref<ApiOptionItem[]>([]);
const defaultDispenseDateRange = ref<[string, string]>(buildThisMonthDateRange());

const rawTableData = ref<DispenseRecordSourceRow[]>([]);
const total = ref(0);
const inpatientDisplayMode = ref<DispenseRecordDisplayMode>('BY_PRESCRIPTION');
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

/** 门诊固定按就诊+处方合并；住院保留展示模式切换。 */
const effectiveDisplayMode = computed<DispenseRecordDisplayMode>(() =>
  props.scope === 'outpatient' ? 'BY_PRESCRIPTION' : inpatientDisplayMode.value,
);

const tableData = computed<DispenseRecordTableRow[]>(() =>
  enrichDispenseRecordRowsForDisplay(rawTableData.value, effectiveDisplayMode.value),
);

const tableStripe = computed(() => effectiveDisplayMode.value === 'FLAT');

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

const tableLoading = computed(() => initLoading.value || pageLoading.value);

/** 发药日期与 daterange 双向绑定。 */
const dispenseDateRange = computed({
  get() {
    return queryParams.dispenseDateRange;
  },
  set(value: [string, string] | null) {
    if (value) {
      queryParams.dispenseDateRange = value;
    }
  },
});

/** 下拉选项转为 ElSelect 可用结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 单元格合并：按就诊或按处方号纵向合并组头列。 */
function handleDispenseRecordSpanMethod(params: {
  column: { property?: string };
  row: DispenseRecordTableRow;
}) {
  return resolveDispenseRecordSpanMethod({
    row: params.row,
    column: params.column,
    displayMode: effectiveDisplayMode.value,
  });
}

/** 行样式：合并模式下按处方块统一底色，避免合并列与斑马纹错位。 */
function resolveRowClassName({ row }: { row: DispenseRecordTableRow }): string {
  if (effectiveDisplayMode.value === 'FLAT') {
    return '';
  }
  const blockIndex = row.displayGroupBlockIndex ?? 0;
  return blockIndex % 2 === 0 ? 'dispense-record-block--even' : 'dispense-record-block--odd';
}

/** 处方号列展示（退药行去 T 前缀，Tooltip 保留存库号）。 */
function resolvePrescriptionNoDisplay(row: DispenseRecordTableRow) {
  return formatDispenseRecordPrescriptionNoDisplay(row);
}

/** 记录类型标签样式。 */
function resolveRecordTypeTagType(recordTypeCode?: string): 'danger' | 'info' | 'success' {
  if (recordTypeCode === 'REFUND') {
    return 'danger';
  }
  if (recordTypeCode === 'DISPENSE') {
    return 'success';
  }
  return 'info';
}

/** 数量展示：空值显示占位符。 */
function formatQuantity(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return String(value);
}

/** 数量 + 单位合并，如「1盒」。 */
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

/** 数量 + 单位合并，如「1盒」；退药行展示为负数。 */
function formatDispenseQuantityWithUnit(row: DispenseRecordTableRow): string {
  const isRefund = row.recordTypeCode === 'REFUND';
  let quantity: unknown = row.quantity;
  if (isRefund && quantity !== undefined && quantity !== null && quantity !== '') {
    const num = Number(quantity);
    if (Number.isFinite(num)) {
      quantity = -Math.abs(num);
    }
  }
  return formatQuantityWithUnit(quantity, row.unitText);
}

/** 退药数量标红（与金额列一致）。 */
function resolveQuantityClass(row: DispenseRecordTableRow): string {
  if (row.recordTypeCode === 'REFUND') {
    return 'text-[var(--el-color-danger)]';
  }
  const num = Number(row.quantity);
  return Number.isFinite(num) && num < 0 ? 'text-[var(--el-color-danger)]' : '';
}

/** 金额展示：6 位 API 值 format 为 2 位（BR-MON-001）。 */
function formatMoneyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return formatMoneyDisplay(value);
}

/** 金额展示：2 位小数 + 元。 */
function formatMoneyYuanCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return formatMoneyYuan(value);
}

/** 将日期规范为 yyyy-MM-dd（后端 LocalDate 不接受时间部分）。 */
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

/** 校验发药日期区间并组装查询/导出请求体。 */
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

/** 拉取分页列表（搜索按钮 / 回车 / 筛选项 change 共用）。 */
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

/** 导出当前筛选条件下的全部记录（列清单与表格可见列一致）。 */
async function handleExport() {
  const filters = buildFilterPayload();
  if (!filters) {
    return;
  }
  // 门诊不导住院专属列；数量展开为可求和 + 单位
  const catalog =
    props.scope === 'outpatient'
      ? DISPENSE_RECORD_EXPORT_COLUMNS.filter(
          (col) =>
            col.field !== 'inpatientNo' && col.field !== 'wardName' && col.field !== 'bedName',
        )
      : DISPENSE_RECORD_EXPORT_COLUMNS;
  const exportColumns = buildExcelExportColumns(catalog, {
    fieldExpand: {
      quantityDisplay: [
        { field: 'quantity', title: '数量' },
        { field: 'unitText', title: '单位' },
      ],
    },
  });
  await runExport({
    ...filters,
    exportColumns,
  });
}

/** 重置筛选项为初始化默认值。 */
function handleReset() {
  applyDefaultDispenseDateRange();
  queryParams.recordTypeCode = undefined;
  queryParams.pharmacyLocationId = undefined;
  queryParams.medicationCategoryCodes = undefined;
  queryParams.dispenserPractitionerId = undefined;
  queryParams.drugKeyword = undefined;
  queryParams.patientKeyword = undefined;
  queryParams.pageNo = 1;
  void getList();
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

/** 加载页初始化下拉与默认日期。 */
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
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryParams as Record<string, unknown>">
          <template #fields>
            <ElFormItem label="药品名称">
              <ElInput
                v-model="queryParams.drugKeyword"
                clearable
                placeholder="名称/编码/拼音"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="记录类型">
              <ElSelect
                v-model="queryParams.recordTypeCode"
                clearable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
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
              >
                <ElOption
                  v-for="item in toSelectOptions(pharmacyOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="项目类型">
              <ElSelect
                v-model="queryParams.medicationCategoryCodes"
                clearable
                filterable
                multiple
                collapse-tags
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
              >
                <ElOption
                  v-for="item in toSelectOptions(medicationCategoryOptions)"
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
              >
                <ElOption
                  v-for="item in toSelectOptions(dispenserOptions)"
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
                placeholder="姓名等"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="发药日期">
              <ElDatePicker
                v-model="dispenseDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
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
        <ReportListToolbar :title="`${pageTitle} · 明细列表`" :total="total">
          <template #actions>
            <ElRadioGroup v-if="scope === 'inpatient'" v-model="inpatientDisplayMode">
              <ElRadioButton value="BY_PRESCRIPTION">按处方</ElRadioButton>
              <ElRadioButton value="BY_ENCOUNTER">按就诊</ElRadioButton>
              <ElRadioButton value="FLAT">平铺</ElRadioButton>
            </ElRadioGroup>
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
              class="dispense-record-table"
              :stripe="tableStripe"
              :loading="tableLoading"
              :data="tableData"
              :height="tableHeight"
              row-key="rowKey"
              :row-class-name="resolveRowClassName"
              :span-method="handleDispenseRecordSpanMethod"
            >
              <ElTableColumn
                prop="prescriptionNo"
                label="处方号"
                width="150"
                align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
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
              </ElTableColumn>
              <ElTableColumn prop="recordTypeText" label="类型" width="72" align="center">
                <template #default="{ row }">
                  <ElTag
                    :type="resolveRecordTypeTagType(row.recordTypeCode as string)"
                    effect="light"
                  >
                    {{ row.recordTypeText || '—' }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="patientName"
                label="患者"
                width="90"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                v-if="scope === 'outpatient'"
                prop="encounterBusNo"
                label="门诊号"
                width="130"
                align="center"
                show-overflow-tooltip
              />
              <template v-else>
                <ElTableColumn
                  prop="inpatientNo"
                  label="住院号"
                  width="130"
                  align="center"
                  show-overflow-tooltip
                />
                <ElTableColumn
                  prop="wardName"
                  label="病区"
                  width="100"
                  align="center"
                  show-overflow-tooltip
                />
                <ElTableColumn prop="bedName" label="床号" width="72" align="center" />
              </template>
              <ElTableColumn
                prop="medicationName"
                label="药品名称"
                min-width="140"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="medicationSpec"
                label="规格"
                width="100"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn label="数量" width="88" align="center" header-align="center">
                <template #default="{ row }">
                  <span :class="resolveQuantityClass(row)">
                    {{ formatDispenseQuantityWithUnit(row) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="单价"
                prop="unitPrice"
                width="88"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">{{ formatMoneyCell(row.unitPrice) }}</template>
              </ElTableColumn>
              <ElTableColumn
                label="金额"
                prop="totalAmount"
                width="88"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  <span :class="Number(row.totalAmount) < 0 ? 'text-[var(--el-color-danger)]' : ''">
                    {{ formatMoneyYuanCell(row.totalAmount) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="lotNumber"
                label="批号"
                width="100"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn prop="traceNo" label="追溯码" width="120" align="center">
                <template #default="{ row }">
                  <TraceCodeTooltip :value="row.traceNo" />
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="manufacturerText"
                label="厂家"
                width="120"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                v-if="scope === 'inpatient'"
                prop="summaryNo"
                label="汇总单号"
                width="130"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="orderingDepartmentName"
                label="开单科室"
                width="110"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="dispenseTime"
                label="发药时间"
                width="168"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="pharmacyLocationName"
                label="药房"
                width="120"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="dispenserName"
                label="发药人"
                width="90"
                align="center"
                show-overflow-tooltip
              />
            </ReportElTable>
          </template>
        </ReportTableScroll>
      </template>

      <template #footer>
        <ReportPageFooter
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
.dispense-record-rx-no {
  display: inline-block;
  max-width: 100%;
  line-height: 1.4;
  word-break: break-all;
}

.dispense-record-table {
  :deep(.el-table__body tr.dispense-record-block--even > td.el-table__cell),
  :deep(.el-table__body tr.dispense-record-block--even > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.dispense-record-block--even > td.el-table-fixed-column--right) {
    background-color: var(--el-bg-color);
  }

  :deep(.el-table__body tr.dispense-record-block--odd > td.el-table__cell),
  :deep(.el-table__body tr.dispense-record-block--odd > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.dispense-record-block--odd > td.el-table-fixed-column--right) {
    background-color: var(--el-fill-color-lighter);
  }

  :deep(.el-table__body tr.dispense-record-block--even:hover > td.el-table__cell),
  :deep(.el-table__body tr.dispense-record-block--even:hover > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.dispense-record-block--even:hover > td.el-table-fixed-column--right) {
    background-color: var(--el-bg-color) !important;
  }

  :deep(.el-table__body tr.dispense-record-block--odd:hover > td.el-table__cell),
  :deep(.el-table__body tr.dispense-record-block--odd:hover > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.dispense-record-block--odd:hover > td.el-table-fixed-column--right) {
    background-color: var(--el-fill-color-lighter) !important;
  }
}
</style>
