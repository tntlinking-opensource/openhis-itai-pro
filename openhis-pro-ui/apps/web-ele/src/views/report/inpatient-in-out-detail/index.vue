<!--
  住院患者入出明细：单列表 + 人次页脚 + 动态转科列；只读查询与导出。
-->
<script setup lang="ts">
import type {
  InpatientInOutDetailFooterDto,
  InpatientInOutDetailRowDto,
  MovementStatusCode,
} from '#/api/report/inpatient-in-out-detail';
import type { ApiOptionItem } from '#/types/api-response';
import type { ReportPrintColumnDef } from '#/utils/print/print-report-list';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import { Download, Printer, Refresh, Search, Setting } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElFormItem,
  ElMessage,
  ElOption,
  ElSelect,
  ElTableColumn,
} from 'element-plus';

import {
  exportInpatientInOutDetailReportApi,
  listInpatientInOutDetailWardOptionsApi,
  loadInpatientInOutDetailReportPageInitApi,
  MOVEMENT_STATUS_ALL,
  pageInpatientInOutDetailReportApi,
} from '#/api/report/inpatient-in-out-detail';
import { useRequest } from '#/hooks/use-request';
import { buildExcelExportColumns } from '#/utils/export/build-excel-export-columns';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATERANGE,
  REPORT_TABLE_AMOUNT_CELL,
  ReportElTable,
  ReportListPageLayout,
  ReportListToolbar,
  ReportPageFooter,
  ReportPrintFieldDialog,
  ReportQueryBar,
  ReportTableScroll,
  mapReportPrintRow,
  collectPagedReportRows,
  reportPrintFieldsOf,
  useReportListPrint,
} from '#/views/report/shared';
import {
  buildReportDateRangeShortcuts,
  normalizeReportDateRangeYmd,
  toReportApiDateTimeRange,
} from '#/views/report/shared/report-date-range-shortcuts';
import { optionDtosToSelectItems } from '#/views/report/shared/report-format';

defineOptions({ name: 'InpatientInOutDetailReport' });

interface QueryState {
  pageNo: number;
  pageSize: number;
  dateFrom: string;
  dateTo: string;
  movementStatus: MovementStatusCode | string;
  organizationId?: string;
  wardLocationId?: string;
}

const PAGE_TITLE = '住院患者入出明细';
/** 打印字段偏好键（本机记住）。 */
const PRINT_REPORT_CODE = 'report-inpatient-in-out-detail';

/** 入出明细可打印列目录（转往列始终可选，未展示时打印为空）。 */
const PRINT_COLUMNS: ReportPrintColumnDef[] = [
  { field: 'busNo', title: '住院号', align: 'center', widthWeight: 1.1 },
  { field: 'patientName', title: '患者姓名', align: 'center', widthWeight: 1 },
  { field: 'genderText', title: '性别', align: 'center', widthWeight: 0.6 },
  { field: 'ageText', title: '年龄', align: 'center', widthWeight: 0.7 },
  { field: 'admissionOrganizationName', title: '入院科室', align: 'center', widthWeight: 1.1 },
  { field: 'admissionWardName', title: '入院病区', align: 'center', widthWeight: 1.1 },
  { field: 'wardName', title: '病区', align: 'center', widthWeight: 1 },
  { field: 'houseName', title: '病房', align: 'center', widthWeight: 0.9 },
  { field: 'bedName', title: '床位', align: 'center', widthWeight: 0.8 },
  { field: 'startTime', title: '入院时间', align: 'center', widthWeight: 1.3 },
  { field: 'endTime', title: '出院时间', align: 'center', widthWeight: 1.3 },
  { field: 'inpatientDays', title: '住院天数', align: 'right', widthWeight: 0.9 },
  { field: 'transferTargetText', title: '转往', align: 'center', widthWeight: 1.2 },
  { field: 'feeNatureDisplay', title: '费用性质', align: 'center', widthWeight: 1 },
  { field: 'settledFeeAmount', title: '已结费用', align: 'right', widthWeight: 1.1 },
];

const movementStatusOptions = ref<ApiOptionItem[]>([]);
const organizationOptions = ref<ApiOptionItem[]>([]);
const wardOptions = ref<ApiOptionItem[]>([]);
const rows = ref<InpatientInOutDetailRowDto[]>([]);
const footer = ref<InpatientInOutDetailFooterDto>({
  admissionPersonTimes: 0,
  dischargePersonTimes: 0,
});
const total = ref(0);
const showTransferColumn = ref(false);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const queryParams = reactive<QueryState>({
  pageNo: 1,
  pageSize: 20,
  dateFrom: '',
  dateTo: '',
  movementStatus: MOVEMENT_STATUS_ALL,
});

const dateRangeShortcuts = buildReportDateRangeShortcuts();

/** 业务日期与 daterange 双向绑定（UI 用日，请求转日界 datetime）。 */
const businessDateRange = computed({
  get() {
    const start = normalizeReportDateRangeYmd(queryParams.dateFrom);
    const end = normalizeReportDateRangeYmd(queryParams.dateTo);
    if (start && end) {
      return [start, end] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.dateFrom = value?.[0] ?? '';
    queryParams.dateTo = value?.[1] ?? '';
  },
});

const footerSummaryItems = computed(() => [
  {
    label: '入院人次',
    value: footer.value.admissionPersonTimes ?? 0,
    primary: true,
  },
  {
    label: '出院人次',
    value: footer.value.dischargePersonTimes ?? 0,
    primary: true,
  },
  { label: '明细行数', value: total.value },
]);

/** ReportQueryBar 需要 Record 形态的 model。 */
const queryBarModel = computed(() => queryParams as Record<string, unknown>);

/** 未选入院科室时禁用病区。 */
const wardSelectDisabled = computed(() => !queryParams.organizationId);

const { run: runInit, loading: initLoading } = useRequest(
  loadInpatientInOutDetailReportPageInitApi,
  {
    manual: true,
    onSuccess: (data) => {
      movementStatusOptions.value = optionDtosToSelectItems(data?.movementStatusOptions);
      organizationOptions.value = optionDtosToSelectItems(data?.organizationOptions);
      wardOptions.value = optionDtosToSelectItems(data?.wardOptions);
      queryParams.dateFrom =
        normalizeReportDateRangeYmd(data?.defaultDateFrom) || queryParams.dateFrom;
      queryParams.dateTo = normalizeReportDateRangeYmd(data?.defaultDateTo) || queryParams.dateTo;
      if (data?.defaultPageSize && data.defaultPageSize > 0) {
        queryParams.pageSize = data.defaultPageSize;
      }
      const defaultStatus = String(
        data?.defaultMovementStatus ?? movementStatusOptions.value[0]?.value ?? MOVEMENT_STATUS_ALL,
      ).trim();
      if (defaultStatus) {
        queryParams.movementStatus = defaultStatus;
      }
    },
  },
);

const { run: runListWards, loading: wardLoading } = useRequest(
  listInpatientInOutDetailWardOptionsApi,
  {
    manual: true,
    onSuccess: (data) => {
      wardOptions.value = optionDtosToSelectItems(data?.wardOptions);
    },
  },
);

const { run: runPage, loading: pageLoading } = useRequest(pageInpatientInOutDetailReportApi, {
  manual: true,
  onSuccess: (data) => {
    rows.value = data?.content ?? [];
    total.value = Number(data?.totalCount ?? 0);
    showTransferColumn.value = Boolean(data?.showTransferColumn);
    footer.value = {
      admissionPersonTimes: Number(data?.footer?.admissionPersonTimes ?? 0),
      dischargePersonTimes: Number(data?.footer?.dischargePersonTimes ?? 0),
    };
  },
});

const { run: runExport, loading: exportLoading } = useRequest(exportInpatientInOutDetailReportApi, {
  manual: true,
  onSuccess: (data) => {
    downloadBinaryExportFile(data, '住院患者入出明细.xlsx');
    const rowCount = data.rowCount ?? data.exportedRowCount ?? 0;
    if (data.truncated) {
      ElMessage.warning('导出数据已触达行数上限，部分记录未包含在文件中');
    } else {
      ElMessage.success(`导出成功，共 ${rowCount} 行`);
    }
  },
});

/** 金额单元格：空占位（禁止把未结显示成 0）。 */
function formatMoneyCell(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  return formatMoneyDisplay(value);
}

/** 普通文本空占位。 */
function formatTextCell(value: unknown): string {
  const text = String(value ?? '').trim();
  return text || '—';
}

/** 将入出明细行映射为打印单元格。 */
function mapInOutPrintRows(source: InpatientInOutDetailRowDto[]) {
  return source.map((r) =>
    mapReportPrintRow(r as unknown as Record<string, unknown>, reportPrintFieldsOf(PRINT_COLUMNS), {
      busNo: (v) => formatTextCell(v),
      patientName: (v) => formatTextCell(v),
      genderText: (v) => formatTextCell(v),
      ageText: (v) => formatTextCell(v),
      admissionOrganizationName: (v) => formatTextCell(v),
      admissionWardName: (v) => formatTextCell(v),
      wardName: (v) => formatTextCell(v),
      houseName: (v) => formatTextCell(v),
      bedName: (v) => formatTextCell(v),
      startTime: (v) => formatTextCell(v),
      endTime: (v) => formatTextCell(v),
      inpatientDays: (v) => formatTextCell(v),
      transferTargetText: (v) => formatTextCell(v),
      feeNatureDisplay: (v) => formatTextCell(v),
      settledFeeAmount: (v) => formatMoneyCell(v),
    }),
  );
}

const { printFieldDialogRef, printSubmitting, handleOpenPrintFields, handlePrintCurrentPage } =
  useReportListPrint({
    getReportCode: () => PRINT_REPORT_CODE,
    getColumnCatalog: () => PRINT_COLUMNS,
    getReportTitle: () => PAGE_TITLE,
    getPrintRows: () => mapInOutPrintRows(rows.value),
    fetchAllPrintRows: async () => {
      const filters = buildFilterPayload();
      if (!filters) {
        return { rows: [], truncated: false };
      }
      const collected = await collectPagedReportRows({
        fetchPage: async (pageNo, pageSize) => {
          const page = await pageInpatientInOutDetailReportApi({
            ...filters,
            pageNo,
            pageSize,
          });
          return { rows: page.content ?? [], total: Number(page.totalCount ?? 0) };
        },
      });
      return { rows: mapInOutPrintRows(collected.rows), truncated: collected.truncated };
    },
    hasPrintableData: () => total.value > 0 || rows.value.length > 0,
  });

const tableLoading = computed(
  () => initLoading.value || pageLoading.value || wardLoading.value || printSubmitting.value,
);

/** 下拉选项转为 ElSelect 结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 明细行稳定 key。 */
function resolveRowKey(row: InpatientInOutDetailRowDto) {
  return String(row.encounterId ?? `${row.busNo ?? ''}|${row.startTime ?? ''}`);
}

/** 校验日期并组装筛选体（Long 主键保持 string；日期转日界 datetime）。 */
function buildFilterPayload() {
  const dateFromYmd = normalizeReportDateRangeYmd(queryParams.dateFrom);
  const dateToYmd = normalizeReportDateRangeYmd(queryParams.dateTo);
  if (!dateFromYmd || !dateToYmd) {
    ElMessage.warning('请选择有效查询日期');
    return null;
  }
  const range = toReportApiDateTimeRange(dateFromYmd, dateToYmd);
  if (!range.start || !range.end) {
    ElMessage.warning('请选择有效查询日期');
    return null;
  }
  const movementStatus = String(queryParams.movementStatus ?? '').trim();
  if (!movementStatus) {
    ElMessage.warning('请选择入出状态');
    return null;
  }
  return {
    dateFrom: range.start,
    dateTo: range.end,
    movementStatus,
    organizationId: preserveApiIdForRequest(queryParams.organizationId),
    wardLocationId: preserveApiIdForRequest(queryParams.wardLocationId),
  };
}

/** 拉取明细分页。 */
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

/** 搜索：回到第一页再查。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 切换入院科室：清空病区并重载选项后查询。 */
async function handleOrganizationChange() {
  queryParams.wardLocationId = undefined;
  const organizationId = preserveApiIdForRequest(queryParams.organizationId);
  // 未选科室 → 病区空且禁用
  if (!organizationId) {
    wardOptions.value = [];
    handleQuery();
    return;
  }
  await runListWards({ organizationId });
  handleQuery();
}

/** 导出当前筛选（列清单与打印列目录一致）。 */
async function handleExport() {
  const filters = buildFilterPayload();
  if (!filters) {
    return;
  }
  const exportColumns = buildExcelExportColumns(PRINT_COLUMNS);
  await runExport({
    ...filters,
    exportColumns,
  });
}

/** 重置为初始化默认值。 */
async function handleReset() {
  queryParams.pageNo = 1;
  queryParams.organizationId = undefined;
  queryParams.wardLocationId = undefined;
  queryParams.movementStatus = MOVEMENT_STATUS_ALL;
  await runInit();
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
            <ElFormItem label="业务日期">
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
            <ElFormItem label="入出状态">
              <ElSelect
                v-model="queryParams.movementStatus"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(movementStatusOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="入院科室">
              <ElSelect
                v-model="queryParams.organizationId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleOrganizationChange"
              >
                <ElOption
                  v-for="item in toSelectOptions(organizationOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="入院病区">
              <ElSelect
                v-model="queryParams.wardLocationId"
                clearable
                filterable
                placeholder="全部"
                :disabled="wardSelectDisabled"
                :loading="wardLoading"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(wardOptions)"
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
      </template>

      <template #toolbar>
        <ReportListToolbar :title="PAGE_TITLE" :total="total">
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
              :loading="tableLoading"
              :data="rows"
              :height="tableHeight"
              :row-key="resolveRowKey"
            >
              <ElTableColumn
                type="index"
                label="序号"
                width="56"
                align="center"
                header-align="center"
              />
              <ElTableColumn
                prop="busNo"
                label="住院号"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatTextCell(row.busNo) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="patientName"
                label="患者姓名"
                min-width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatTextCell(row.patientName) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="genderText"
                label="性别"
                width="72"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  {{ formatTextCell(row.genderText) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="ageText"
                label="年龄"
                width="80"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  {{ formatTextCell(row.ageText) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="admissionOrganizationName"
                label="入院科室"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatTextCell(row.admissionOrganizationName) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="admissionWardName"
                label="入院病区"
                min-width="120"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatTextCell(row.admissionWardName) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="wardName"
                label="病区"
                min-width="110"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatTextCell(row.wardName) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="houseName"
                label="病房"
                min-width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatTextCell(row.houseName) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="bedName"
                label="床位"
                width="88"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatTextCell(row.bedName) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="startTime"
                label="入院时间"
                min-width="160"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatTextCell(row.startTime) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="endTime"
                label="出院时间"
                min-width="160"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatTextCell(row.endTime) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="inpatientDays"
                label="住院天数"
                width="96"
                align="right"
                header-align="right"
              >
                <template #default="{ row }">
                  {{ formatTextCell(row.inpatientDays) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-if="showTransferColumn"
                prop="transferTargetText"
                label="转往"
                min-width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatTextCell(row.transferTargetText) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="feeNatureDisplay"
                label="费用性质"
                min-width="110"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatTextCell(row.feeNatureDisplay) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="settledFeeAmount"
                label="已结费用"
                min-width="120"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ formatMoneyCell(row.settledFeeAmount) }}
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
          :page-sizes="[20, 50, 100, 200]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handlePageSizeChange"
          @current-change="handleCurrentPageChange"
        />
      </template>
    </ReportListPageLayout>
    <ReportPrintFieldDialog
      ref="printFieldDialogRef"
      :report-code="PRINT_REPORT_CODE"
      :column-catalog="PRINT_COLUMNS"
      show-print-scope
    />
  </Page>
</template>
