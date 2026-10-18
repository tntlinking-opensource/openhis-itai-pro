<!--
  财务审批：T3 带操作查询列表（报表同构布局）。
  筛选默认仅前端：类型空（全部）+ 待审 + 审批时间当月；金额列按单据类型动态切换；
  支持导出；待审行展示通过/驳回（与交账审批同口径，角色由后端 Gate 校验）。
-->
<script setup lang="ts">
import type {
  FinanceApprovalDocumentRowDto,
  PageFinanceApprovalDocumentParams,
} from '#/api/finance/approval';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@zxplat/common-ui';
import { formatDateTime } from '@zxplat/utils';

import { Download, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTableColumn,
} from 'element-plus';

import {
  agreeFinanceApprovalApi,
  initFinanceApprovalApi,
  pageFinanceApprovalDocumentsApi,
  rejectFinanceApprovalApi,
} from '#/api/finance/approval';
import { useRequest } from '#/hooks/use-request';
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
import {
  buildReportDateRangeShortcuts,
  normalizeReportDateRangeYmd,
} from '#/views/report/shared/report-date-range-shortcuts';

import { downloadFinanceApprovalCsv } from './export-finance-approval-csv';
import {
  buildFinanceApprovalViewAmountQuery,
  FINANCE_APPROVAL_TYPE_PRICE,
  resolveFinanceApprovalAmountCell,
  resolveFinanceApprovalAmountColumns,
  shouldShowFinanceApprovalLocationSupplier,
} from './finance-approval-amount-columns';

defineOptions({
  name: 'FinanceApproval',
});

/** 供应待审状态码 */
const SUPPLY_STATUS_PENDING = 'APPROVAL';
/** 调价待审状态码 */
const PRICE_STATUS_PENDING = 'pending';
/** 文档种类 / 列表类型码：调价 */
const DOCUMENT_KIND_PRICE = FINANCE_APPROVAL_TYPE_PRICE;
const PAGE_TITLE = '财务审批';
/** 导出单次拉取页大小 */
const EXPORT_PAGE_SIZE = 200;
/** 导出上限，避免一次拉爆 */
const EXPORT_MAX_ROWS = 5000;

/** 单据类型 → 业务工作站路径 */
const DETAIL_ROUTE_BY_TYPE: Record<string, string> = {
  CABINET_INBOUND_RECEIPT: '/inventory/warehouse/purchase-inbound',
  CABINET_RETURN_TO_SUPPLIER: '/inventory/warehouse/return-to-supplier',
  CABINET_GAIN_LOSS: '/inventory/warehouse/gain-loss',
  PHARMACY_GAIN_LOSS: '/inventory/pharmacy/gain-loss',
  DEPT_GAIN_LOSS: '/inventory/department/gain-loss',
  CABINET_STOCKTAKE: '/inventory/warehouse/stocktake',
  PHARMACY_STOCKTAKE: '/inventory/pharmacy/stocktake',
  DEPT_STOCKTAKE: '/inventory/department/stocktake',
  PRICE_ADJUSTMENT: '/finance/price-adjustment',
};

interface QueryFormState {
  pageNo: number;
  pageSize: number;
  busNo?: string;
  typeEnum?: string;
  statusEnum?: string;
  locationId?: string;
  supplierId?: string;
  itemTable?: string;
  applicantId?: string;
  approverId?: string;
  approvalTimeStart?: string;
  approvalTimeEnd?: string;
}

const router = useRouter();

const tableData = ref<FinanceApprovalDocumentRowDto[]>([]);
const total = ref(0);
const exportLoading = ref(false);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

/** 当前自然月起止日 */
function createCurrentMonthRange(): [string, string] {
  const d = new Date();
  const y = d.getFullYear();
  const m = d.getMonth();
  const pad = (n: number) => String(n).padStart(2, '0');
  const lastDay = new Date(y, m + 1, 0).getDate();
  return [`${y}-${pad(m + 1)}-01`, `${y}-${pad(m + 1)}-${pad(lastDay)}`];
}

const [defaultFrom, defaultTo] = createCurrentMonthRange();

const queryParams = reactive<QueryFormState>({
  pageNo: 1,
  pageSize: 50,
  busNo: undefined,
  /** 空=全部类型（供应财务族 ∪ 调价），仅默认待审 */
  typeEnum: undefined,
  statusEnum: SUPPLY_STATUS_PENDING,
  locationId: undefined,
  supplierId: undefined,
  itemTable: undefined,
  applicantId: undefined,
  approverId: undefined,
  approvalTimeStart: defaultFrom,
  approvalTimeEnd: defaultTo,
});

const typeOptions = ref<ApiOptionItem[]>([]);
const statusOptions = ref<ApiOptionItem[]>([]);
const locationOptions = ref<ApiOptionItem[]>([]);
const supplierOptions = ref<ApiOptionItem[]>([]);
const itemTableOptions = ref<ApiOptionItem[]>([]);
const applicantOptions = ref<ApiOptionItem[]>([]);
const approverOptions = ref<ApiOptionItem[]>([]);

const dateRangeShortcuts = buildReportDateRangeShortcuts();

const { run: runInit, loading: initLoading } = useRequest(initFinanceApprovalApi, {
  manual: true,
});

const { run: runPage, loading: pageLoading } = useRequest(pageFinanceApprovalDocumentsApi, {
  manual: true,
});

const { run: runAgree, loading: agreeLoading } = useRequest(agreeFinanceApprovalApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('审批通过');
  },
});

const { run: runReject, loading: rejectLoading } = useRequest(rejectFinanceApprovalApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('已驳回');
  },
});

const tableLoading = computed(
  () =>
    initLoading.value ||
    pageLoading.value ||
    agreeLoading.value ||
    rejectLoading.value ||
    exportLoading.value,
);

/** 审批时间与 daterange 双向绑定（精确到天） */
const approvalDateRange = computed({
  get() {
    const start = normalizeReportDateRangeYmd(queryParams.approvalTimeStart);
    const end = normalizeReportDateRangeYmd(queryParams.approvalTimeEnd);
    if (start && end) {
      return [start, end] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.approvalTimeStart = value?.[0];
    queryParams.approvalTimeEnd = value?.[1];
  },
});

/** 下拉选项规范化 */
function toSelectOptions(items?: ApiOptionItem[]): ApiOptionItem[] {
  return (items ?? []).map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** YYYY-MM-DD → 后端 LocalDateTime 边界 */
function toDateTimeBoundary(ymd: string | undefined, endOfDay: boolean): string | undefined {
  const day = normalizeReportDateRangeYmd(ymd);
  if (!day) return undefined;
  return endOfDay ? `${day} 23:59:59` : `${day} 00:00:00`;
}

/** 组装筛选条件（不含分页；待审不传审批时间窗） */
function buildFilterRequest(): Omit<PageFinanceApprovalDocumentParams, 'pageNo' | 'pageSize'> {
  const pendingOnly = queryParams.statusEnum === SUPPLY_STATUS_PENDING;
  return {
    busNo: queryParams.busNo?.trim() || undefined,
    typeEnum: queryParams.typeEnum || undefined,
    statusEnum: queryParams.statusEnum || undefined,
    locationId: preserveApiIdForRequest(queryParams.locationId),
    supplierId: preserveApiIdForRequest(queryParams.supplierId),
    itemTable: queryParams.itemTable || undefined,
    applicantId: preserveApiIdForRequest(queryParams.applicantId),
    approverId: preserveApiIdForRequest(queryParams.approverId),
    approvalTimeFrom: pendingOnly
      ? undefined
      : toDateTimeBoundary(queryParams.approvalTimeStart, false),
    approvalTimeTo: pendingOnly ? undefined : toDateTimeBoundary(queryParams.approvalTimeEnd, true),
  };
}

/** 组装分页查询请求 */
function buildPageRequest(): PageFinanceApprovalDocumentParams {
  return {
    ...buildFilterRequest(),
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
  };
}

/** 加载筛选选项 */
async function initPage() {
  try {
    const res = await runInit();
    typeOptions.value = toSelectOptions(res?.typeOptions);
    statusOptions.value = toSelectOptions(res?.statusOptions);
    locationOptions.value = toSelectOptions(res?.locationOptions);
    supplierOptions.value = toSelectOptions(res?.supplierOptions);
    itemTableOptions.value = toSelectOptions(res?.itemTableOptions);
    applicantOptions.value = toSelectOptions(res?.applicantOptions);
    approverOptions.value = toSelectOptions(res?.approverOptions);
  } catch {
    typeOptions.value = [];
    statusOptions.value = [];
    locationOptions.value = [];
    supplierOptions.value = [];
    itemTableOptions.value = [];
    applicantOptions.value = [];
    approverOptions.value = [];
  }
}

/** 查询列表 */
async function getList() {
  const responseData = await runPage(buildPageRequest());
  tableData.value = responseData?.content ?? [];
  total.value = responseData?.totalCount ?? 0;
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 查询回到第一页 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 重置为前端默认（类型空=全部 + 待审 + 当月） */
function handleReset() {
  const [from, to] = createCurrentMonthRange();
  queryParams.pageNo = 1;
  queryParams.pageSize = 50;
  queryParams.busNo = undefined;
  queryParams.typeEnum = undefined;
  queryParams.statusEnum = SUPPLY_STATUS_PENDING;
  queryParams.locationId = undefined;
  queryParams.supplierId = undefined;
  queryParams.itemTable = undefined;
  queryParams.applicantId = undefined;
  queryParams.approverId = undefined;
  queryParams.approvalTimeStart = from;
  queryParams.approvalTimeEnd = to;
  void getList();
}

/** 切换每页条数 */
function handlePageSizeChange(size: number) {
  queryParams.pageSize = size;
  queryParams.pageNo = 1;
  void getList();
}

/** 切换页码 */
function handleCurrentPageChange(page: number) {
  queryParams.pageNo = page;
  void getList();
}

/** 展示时间 */
function parseTime(time?: string): string {
  if (!time) return '';
  return formatDateTime(time);
}

/** 行审批时间 */
function resolveApprovalTime(row: FinanceApprovalDocumentRowDto): string {
  return parseTime(row.approvalTime || row.approveTime);
}

/** 行制单时间 */
function resolveOccurrenceTime(row: FinanceApprovalDocumentRowDto): string {
  return parseTime(row.occurrenceTime || row.applyTime || row.submitTime);
}

/** 当前筛选下的金额列定义（随单据类型切换表头） */
const amountColumns = computed(() => resolveFinanceApprovalAmountColumns(queryParams.typeEnum));

/** 金额列集合变化时强制表格重挂，避免 ElTable 动态列错位 */
const amountColumnsTableKey = computed(() => amountColumns.value.map((col) => col.key).join('|'));

/** 调价筛选时库房/供应商无业务含义，隐藏列 */
const showLocationSupplierColumns = computed(() =>
  shouldShowFinanceApprovalLocationSupplier(queryParams.typeEnum),
);

/** 是否待审（按 status code） */
function isPendingApproval(row: FinanceApprovalDocumentRowDto): boolean {
  if (row.documentKind === DOCUMENT_KIND_PRICE) {
    return row.statusEnum === PRICE_STATUS_PENDING;
  }
  return row.statusEnum === SUPPLY_STATUS_PENDING;
}

/** 行唯一键 */
function resolveRowKey(row: FinanceApprovalDocumentRowDto): string {
  return `${row.documentKind ?? ''}:${row.busNo ?? ''}`;
}

/** 打开业务工作站只读详情 */
function handleView(row: FinanceApprovalDocumentRowDto) {
  const busNo = String(row.busNo ?? '').trim();
  if (!busNo) {
    ElMessage.warning('单据号为空，无法查看');
    return;
  }
  const typeCode =
    row.documentKind === DOCUMENT_KIND_PRICE
      ? DOCUMENT_KIND_PRICE
      : String(row.typeEnum ?? '').trim();
  const path = DETAIL_ROUTE_BY_TYPE[typeCode];
  if (!path) {
    ElMessage.warning('暂不支持查看该单据类型详情');
    return;
  }
  // 金额随深链带入详情页脚，与列表列口径一致（退货详情无行金额时尤需）
  void router.push({
    path,
    query: {
      busNo,
      fromFinanceApproval: '1',
      ...buildFinanceApprovalViewAmountQuery(row, typeCode),
    },
  });
}

/** 审批通过 */
async function handleAgree(row: FinanceApprovalDocumentRowDto) {
  const busNo = String(row.busNo ?? '').trim();
  const documentKind = String(row.documentKind ?? '').trim();
  if (!busNo || !documentKind) {
    ElMessage.warning('单据信息不完整');
    return;
  }
  try {
    await ElMessageBox.confirm(`确认通过单据 ${busNo}？`, '财务审批通过', {
      type: 'warning',
      confirmButtonText: '通过',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  await runAgree({
    busNo,
    documentKind,
    typeEnum: documentKind === DOCUMENT_KIND_PRICE ? undefined : row.typeEnum,
  });
  await getList();
}

/** 审批驳回 */
async function handleReject(row: FinanceApprovalDocumentRowDto) {
  const busNo = String(row.busNo ?? '').trim();
  const documentKind = String(row.documentKind ?? '').trim();
  if (!busNo || !documentKind) {
    ElMessage.warning('单据信息不完整');
    return;
  }
  let rejectReason = '';
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', `驳回 · ${busNo}`, {
      type: 'warning',
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputPlaceholder: '驳回原因必填',
      inputValidator: (val) => {
        if (!val?.trim()) return '驳回原因不能为空';
        return true;
      },
    });
    rejectReason = String(value ?? '').trim();
  } catch {
    return;
  }
  await runReject({
    busNo,
    documentKind,
    typeEnum: documentKind === DOCUMENT_KIND_PRICE ? undefined : row.typeEnum,
    rejectReason,
  });
  await getList();
}

/** 导出当前筛选条件下的列表（前端分页拉取后生成 CSV，Excel 可打开） */
async function handleExportExcel() {
  exportLoading.value = true;
  try {
    const rows: FinanceApprovalDocumentRowDto[] = [];
    let pageNo = 1;
    const filter = buildFilterRequest();
    while (rows.length < EXPORT_MAX_ROWS) {
      const res = await pageFinanceApprovalDocumentsApi({
        ...filter,
        pageNo,
        pageSize: EXPORT_PAGE_SIZE,
      });
      const chunk = res?.content ?? [];
      rows.push(...chunk);
      const totalCount = Number(res?.totalCount ?? 0);
      if (chunk.length === 0 || rows.length >= totalCount) {
        break;
      }
      pageNo += 1;
    }
    if (rows.length === 0) {
      ElMessage.warning('没有可导出的数据');
      return;
    }
    if (rows.length >= EXPORT_MAX_ROWS) {
      ElMessage.warning(`导出已截断为前 ${EXPORT_MAX_ROWS} 条，请缩小筛选范围`);
    }
    downloadFinanceApprovalCsv(rows, undefined, queryParams.typeEnum);
    ElMessage.success(`已导出 ${rows.length} 条`);
  } catch {
    ElMessage.error('导出失败');
  } finally {
    exportLoading.value = false;
  }
}

onMounted(async () => {
  await initPage();
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
                <ElOption
                  v-for="item in typeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="审批状态">
              <ElSelect
                v-model="queryParams.statusEnum"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in statusOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="库房">
              <ElSelect
                v-model="queryParams.locationId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in locationOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="审批时间">
              <ElDatePicker
                v-model="approvalDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
                :shortcuts="dateRangeShortcuts"
                :class="[REPORT_QUERY_CONTROL, REPORT_QUERY_CONTROL_DATERANGE]"
                @change="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="供应商">
              <ElSelect
                v-model="queryParams.supplierId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in supplierOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="品类">
              <ElSelect
                v-model="queryParams.itemTable"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in itemTableOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="制单人">
              <ElSelect
                v-model="queryParams.applicantId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in applicantOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="审批人">
              <ElSelect
                v-model="queryParams.approverId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in approverOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="单据号">
              <ElInput
                v-model="queryParams.busNo"
                clearable
                placeholder="单据号"
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
        <ReportListToolbar :title="`${PAGE_TITLE} · 单据列表`" :total="total">
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
              :key="amountColumnsTableKey"
              :loading="tableLoading"
              :data="tableData"
              :height="tableHeight"
              :row-key="resolveRowKey"
            >
              <ElTableColumn type="index" label="序号" width="60" align="center" />
              <ElTableColumn
                prop="busNo"
                label="单据号"
                min-width="150"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="typeEnumText"
                label="单据类型"
                width="120"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn prop="statusEnumText" label="审批状态" width="100" align="center" />
              <ElTableColumn
                v-if="showLocationSupplierColumns"
                prop="locationName"
                label="库房"
                min-width="120"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                v-if="showLocationSupplierColumns"
                prop="supplierName"
                label="供应商"
                min-width="140"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn prop="itemTableText" label="品类" width="80" align="center" />
              <ElTableColumn
                prop="applicantName"
                label="制单人"
                width="100"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn label="制单时间" width="170" align="center">
                <template #default="{ row }">
                  {{ resolveOccurrenceTime(row) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                v-for="col in amountColumns"
                :key="col.key"
                :label="col.label"
                width="120"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ resolveFinanceApprovalAmountCell(row, col.key) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="approverName"
                label="审批人"
                width="100"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn label="审批时间" width="170" align="center">
                <template #default="{ row }">
                  {{ resolveApprovalTime(row) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" width="180" fixed="right" align="center">
                <template #default="{ row }">
                  <ElButton link type="primary" @click="handleView(row)">查看</ElButton>
                  <ElButton
                    v-if="isPendingApproval(row)"
                    link
                    type="primary"
                    @click="handleAgree(row)"
                  >
                    通过
                  </ElButton>
                  <ElButton
                    v-if="isPendingApproval(row)"
                    link
                    type="danger"
                    @click="handleReject(row)"
                  >
                    驳回
                  </ElButton>
                </template>
              </ElTableColumn>
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
