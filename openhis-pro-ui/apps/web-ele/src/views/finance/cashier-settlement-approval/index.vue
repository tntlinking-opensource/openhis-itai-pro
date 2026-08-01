<!--
  交账审批（T3）：菜单由后端驱动时可注册路径 /finance/cashier-settlement-approval
-->
<script setup lang="ts">
import type {
  CashierSettlementApprovalListItemDto,
  LoadCashierSettlementApprovalDetailResponse,
  PageCashierSettlementApprovalsParams,
} from '#/api/finance/cashier-settlement-approval';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElFormItem,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  approveCashierSettlementApi,
  loadCashierSettlementApprovalDetailApi,
  loadCashierSettlementApprovalPageInitApi,
  pageCashierSettlementApprovalsApi,
  rejectCashierSettlementApi,
} from '#/api/finance/cashier-settlement-approval';
import DailySettlementSummaryPanel from '#/components/charge/daily-settlement-summary-panel.vue';
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

defineOptions({
  name: 'CashierSettlementApproval',
});

const SETTLEMENT_STATUS_SUBMITTED = 'SUBMITTED';
const CATEGORY_PHARMACY = 'PHARMACY';
const PAGE_TITLE = '交账审批';

interface QueryFormState {
  pageNo: number;
  pageSize: number;
  categoryEnum?: string;
  statusEnum?: string;
  locationId?: string;
  submittedDateStart?: string;
  submittedDateEnd?: string;
}

const tableData = ref<CashierSettlementApprovalListItemDto[]>([]);
const total = ref(0);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);
const detailData = ref<LoadCashierSettlementApprovalDetailResponse | null>(null);

const categoryOptions = ref<ApiOptionItem[]>([]);
const statusOptions = ref<ApiOptionItem[]>([]);
const pharmacyOptions = ref<ApiOptionItem[]>([]);
const dateRangeShortcuts = buildReportDateRangeShortcuts();

const queryParams = reactive<QueryFormState>({
  pageNo: 1,
  pageSize: 50,
  categoryEnum: undefined,
  statusEnum: SETTLEMENT_STATUS_SUBMITTED,
  locationId: undefined,
  submittedDateStart: undefined,
  submittedDateEnd: undefined,
});

const [Drawer, drawerApi] = useVbenDrawer();

const { run: runInit, loading: initLoading } = useRequest(
  loadCashierSettlementApprovalPageInitApi,
  { manual: true },
);
const { run: runPage, loading: pageLoading } = useRequest(pageCashierSettlementApprovalsApi, {
  manual: true,
});
const { run: runDetail, loading: detailLoading } = useRequest(
  loadCashierSettlementApprovalDetailApi,
  { manual: true },
);
const { run: runApprove, loading: approveLoading } = useRequest(approveCashierSettlementApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('审批通过');
  },
});
const { run: runReject, loading: rejectLoading } = useRequest(rejectCashierSettlementApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('已驳回');
  },
});

const tableLoading = computed(
  () => initLoading.value || pageLoading.value || approveLoading.value || rejectLoading.value,
);

/** 当前是否按药房日结大类筛选（展示药房条件）。 */
const isPharmacyCategory = computed(() => queryParams.categoryEnum === CATEGORY_PHARMACY);

/** 交账时间与 daterange 双向绑定。 */
const submittedDateRange = computed({
  get() {
    const start = normalizeReportDateRangeYmd(queryParams.submittedDateStart);
    const end = normalizeReportDateRangeYmd(queryParams.submittedDateEnd);
    if (start && end) {
      return [start, end] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.submittedDateStart = value?.[0];
    queryParams.submittedDateEnd = value?.[1];
  },
});

/** YYYY-MM-DD → 后端 LocalDateTime 边界。 */
function toDateTimeBoundary(ymd: string | undefined, endOfDay: boolean): string | undefined {
  const day = normalizeReportDateRangeYmd(ymd);
  if (!day) {
    return undefined;
  }
  return endOfDay ? `${day} 23:59:59` : `${day} 00:00:00`;
}

/** 组装分页查询请求。 */
function buildPageRequest(): PageCashierSettlementApprovalsParams {
  const locationId = preserveApiIdForRequest(queryParams.locationId);
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    categoryEnum: queryParams.categoryEnum || undefined,
    statusEnum: queryParams.statusEnum || undefined,
    submittedFrom: toDateTimeBoundary(queryParams.submittedDateStart, false),
    submittedTo: toDateTimeBoundary(queryParams.submittedDateEnd, true),
    locationId: isPharmacyCategory.value && locationId ? locationId : undefined,
  };
}

/** 加载筛选项。 */
async function initPage() {
  try {
    const res = await runInit();
    categoryOptions.value = res?.categoryOptions ?? [];
    statusOptions.value = res?.statusOptions ?? [];
    pharmacyOptions.value = res?.pharmacyOptions ?? [];
  } catch {
    categoryOptions.value = [];
    statusOptions.value = [];
    pharmacyOptions.value = [];
  }
}

/** 业务大类变更：切离药房日结时清空药房筛选。 */
function handleCategoryChange() {
  if (!isPharmacyCategory.value) {
    queryParams.locationId = undefined;
  }
  handleQuery();
}

/** 查询列表。 */
async function getList() {
  const responseData = await runPage(buildPageRequest());
  tableData.value = responseData?.content ?? [];
  total.value = responseData?.totalCount ?? 0;
}

/** 回到第一页查询。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 重置筛选。 */
function handleReset() {
  queryParams.pageNo = 1;
  queryParams.pageSize = 50;
  queryParams.categoryEnum = undefined;
  queryParams.statusEnum = SETTLEMENT_STATUS_SUBMITTED;
  queryParams.locationId = undefined;
  queryParams.submittedDateStart = undefined;
  queryParams.submittedDateEnd = undefined;
  void getList();
}

/** 格式化时间展示。 */
function parseTime(time?: string): string {
  if (!time) {
    return '—';
  }
  return formatDateTime(time);
}

/** 列表范围列：药房优先，否则科室。 */
function formatScopeLabel(row: CashierSettlementApprovalListItemDto): string {
  const locationName = row.locationName?.trim();
  if (locationName) {
    return locationName;
  }
  const orgName = row.orgName?.trim();
  if (orgName) {
    return orgName;
  }
  return '—';
}

/** 金额两位小数展示。 */
function formatAmount(value?: string | number): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  return num.toFixed(2);
}

/** 是否待审（按 status code）。 */
function isPendingApproval(row: CashierSettlementApprovalListItemDto): boolean {
  return row.statusEnum === SETTLEMENT_STATUS_SUBMITTED;
}

/** 打开详情抽屉。 */
async function handleViewDetail(row: CashierSettlementApprovalListItemDto) {
  const settlementId = preserveApiIdForRequest(row.settlementId);
  if (!settlementId) {
    return;
  }
  detailData.value = null;
  drawerApi.open();
  const data = await runDetail(settlementId);
  detailData.value = data ?? null;
}

/** 审批通过。 */
async function handleApprove(row: CashierSettlementApprovalListItemDto) {
  const settlementId = preserveApiIdForRequest(row.settlementId);
  if (!settlementId) {
    return;
  }
  try {
    await ElMessageBox.confirm(`确认通过交账单 ${row.busNo ?? settlementId}？`, '交账审批通过', {
      type: 'warning',
      confirmButtonText: '通过',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  await runApprove({ settlementId });
  drawerApi.close();
  await getList();
}

/** 审批驳回。 */
async function handleReject(row: CashierSettlementApprovalListItemDto) {
  const settlementId = preserveApiIdForRequest(row.settlementId);
  if (!settlementId) {
    return;
  }
  let reason = '';
  try {
    const { value } = await ElMessageBox.prompt(
      '请填写驳回原因（可选）',
      `驳回 · ${row.busNo ?? ''}`,
      {
        type: 'warning',
        confirmButtonText: '驳回',
        cancelButtonText: '取消',
        inputPlaceholder: '驳回原因',
      },
    );
    reason = String(value ?? '').trim();
  } catch {
    return;
  }
  await runReject({ settlementId, reason: reason || undefined });
  drawerApi.close();
  await getList();
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
  await initPage();
  await getList();
});
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryParams">
          <template #fields>
            <ElFormItem label="业务大类">
              <ElSelect
                v-model="queryParams.categoryEnum"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleCategoryChange"
              >
                <ElOption
                  v-for="item in categoryOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem v-if="isPharmacyCategory" label="药房">
              <ElSelect
                v-model="queryParams.locationId"
                clearable
                filterable
                placeholder="全部药房"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in pharmacyOptions"
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
            <ElFormItem label="交账时间">
              <ElDatePicker
                v-model="submittedDateRange"
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
        <ReportListToolbar :title="`${PAGE_TITLE} · 交账列表`" :total="total" />
      </template>

      <template #table>
        <ReportTableScroll ref="tableScrollRef">
          <template #default="{ tableHeight }">
            <ReportElTable
              :loading="tableLoading"
              :data="tableData"
              :height="tableHeight"
              row-key="settlementId"
            >
              <ElTableColumn type="index" label="序号" width="60" align="center" />
              <ElTableColumn
                prop="busNo"
                label="交账单号"
                min-width="140"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="typeEnumText"
                label="交账类型"
                width="120"
                align="center"
                show-overflow-tooltip
              />
              <ElTableColumn label="药房/科室" min-width="120" align="center" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ formatScopeLabel(row) }}
                </template>
              </ElTableColumn>
              <ElTableColumn prop="statusEnumText" label="状态" width="90" align="center" />
              <ElTableColumn label="业务窗" min-width="200" align="center">
                <template #default="{ row }">
                  {{ parseTime(row.dateFrom) }} ~ {{ parseTime(row.dateTo) }}
                </template>
              </ElTableColumn>
              <ElTableColumn prop="submitterName" label="交账人" width="100" align="center" />
              <ElTableColumn label="交账时间" width="170" align="center">
                <template #default="{ row }">
                  {{ parseTime(row.submittedAt) }}
                </template>
              </ElTableColumn>
              <ElTableColumn prop="approverName" label="审批人" width="100" align="center">
                <template #default="{ row }">
                  {{ row.approverName?.trim() || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="审批时间" width="170" align="center">
                <template #default="{ row }">
                  {{ parseTime(row.approvedAt) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="paymentCount"
                label="笔数"
                width="70"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              />
              <ElTableColumn
                prop="totalIncome"
                label="金额"
                width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              />
              <ElTableColumn label="操作" width="200" fixed="right" align="center">
                <template #default="{ row }">
                  <ElButton link type="primary" @click="handleViewDetail(row)">详情</ElButton>
                  <ElButton
                    v-if="isPendingApproval(row)"
                    link
                    type="primary"
                    @click="handleApprove(row)"
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

    <Drawer title="交账详情" class="w-[960px]" :loading="detailLoading" :footer="false">
      <template v-if="detailData?.header">
        <ElDescriptions :column="2" border size="small" class="mb-4">
          <ElDescriptionsItem label="交账单号">{{
            detailData.header.busNo ?? '—'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="类型">{{
            detailData.header.typeEnumText ?? '—'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="状态">{{
            detailData.header.statusEnumText ?? '—'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="药房/科室">{{
            formatScopeLabel(detailData.header)
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="挂载笔数">{{
            detailData.mountedSourceCount ?? detailData.header.paymentCount ?? '—'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="金额">{{
            detailData.header.totalIncome ?? '—'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="业务窗起">{{
            parseTime(detailData.header.dateFrom)
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="业务窗止">{{
            parseTime(detailData.header.dateTo)
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="交账人">{{
            detailData.header.submitterName?.trim() || '—'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="交账时间">{{
            parseTime(detailData.header.submittedAt)
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="审批人">{{
            detailData.header.approverName?.trim() || '—'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="审批时间">{{
            parseTime(detailData.header.approvedAt)
          }}</ElDescriptionsItem>
        </ElDescriptions>
        <p
          v-if="detailData.detailBodyRecomputeNote"
          class="mb-3 text-sm text-[var(--el-text-color-regular)]"
        >
          {{ detailData.detailBodyRecomputeNote }}
        </p>

        <!-- 收款日结体现算 -->
        <template v-if="detailData.paymentRows?.length">
          <h4 class="mb-2 text-sm font-medium">费用科目</h4>
          <ElTable :data="detailData.paymentRows" border size="small" class="mb-3" max-height="240">
            <ElTableColumn prop="category" label="科目" min-width="140" align="center" />
            <ElTableColumn prop="amount" label="金额" width="120" align="right" />
            <ElTableColumn prop="unit" label="单位" width="80" align="center" />
          </ElTable>
        </template>
        <template v-if="detailData.paymentSummary">
          <h4 class="mb-2 text-sm font-medium">收入汇总</h4>
          <DailySettlementSummaryPanel :summary="detailData.paymentSummary" />
        </template>

        <!-- 药房日结体现算 -->
        <template v-if="detailData.pharmacyCategoryRows?.length">
          <h4 class="mb-2 text-sm font-medium">大类汇总</h4>
          <ElTable
            :data="detailData.pharmacyCategoryRows"
            border
            size="small"
            class="mb-3"
            max-height="220"
          >
            <ElTableColumn prop="categoryLabel" label="大类" min-width="100" align="center" />
            <ElTableColumn label="进价总额" width="100" align="right" header-align="right">
              <template #default="{ row }">
                {{ formatAmount(row.purchaseAmount) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="售价总额" width="100" align="right" header-align="right">
              <template #default="{ row }">
                {{ formatAmount(row.saleAmount) }}
              </template>
            </ElTableColumn>
          </ElTable>
        </template>
        <template v-if="detailData.pharmacyProductRows?.length">
          <h4 class="mb-2 text-sm font-medium">品规汇总</h4>
          <ElTable
            :data="detailData.pharmacyProductRows"
            border
            size="small"
            class="mb-3"
            max-height="280"
          >
            <ElTableColumn
              prop="medicationName"
              label="药品"
              min-width="140"
              show-overflow-tooltip
            />
            <ElTableColumn prop="busNo" label="编码" width="110" align="center" />
            <ElTableColumn prop="specification" label="规格" width="100" align="center" />
            <ElTableColumn prop="manufacturer" label="厂家" min-width="120" show-overflow-tooltip />
            <ElTableColumn label="净数量" width="90" align="right" header-align="right">
              <template #default="{ row }">
                {{ formatAmount(row.netQuantity) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="进价单价" width="100" align="right" header-align="right">
              <template #default="{ row }">
                {{ formatAmount(row.purchaseUnitPrice) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="售价单价" width="100" align="right" header-align="right">
              <template #default="{ row }">
                {{ formatAmount(row.saleUnitPrice) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="进价总额" width="100" align="right" header-align="right">
              <template #default="{ row }">
                {{ formatAmount(row.purchaseAmount) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="售价总额" width="100" align="right" header-align="right">
              <template #default="{ row }">
                {{ formatAmount(row.saleAmount) }}
              </template>
            </ElTableColumn>
          </ElTable>
        </template>

        <div
          v-if="detailData.header && isPendingApproval(detailData.header)"
          class="mt-4 flex gap-2"
        >
          <ElButton
            type="primary"
            :loading="approveLoading"
            @click="handleApprove(detailData.header)"
          >
            通过
          </ElButton>
          <ElButton
            type="danger"
            plain
            :loading="rejectLoading"
            @click="handleReject(detailData.header)"
          >
            驳回
          </ElButton>
        </div>
      </template>
    </Drawer>
  </Page>
</template>
