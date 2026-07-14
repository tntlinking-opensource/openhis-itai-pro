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
} from '#/api/report/document-detail';
import { useRequest } from '#/hooks/use-request';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATERANGE,
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
  buildReportDateRangeShortcuts,
  normalizeReportDateRangeYmd,
  toReportApiDateTimeRange,
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

const props = defineProps<Props>();

const tableData = ref<DocumentDetailRowDto[]>([]);
const total = ref(0);
const sumTotalPrice = ref('');
const sumItemQuantity = ref('');
const sumRowCount = ref(0);
const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);

const typeEnumOptions = ref<ApiOptionItem[]>([]);
const sourceLocationOptions = ref<ApiOptionItem[]>([]);
const purposeLocationOptions = ref<ApiOptionItem[]>([]);

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

const dateRangeShortcuts = buildReportDateRangeShortcuts();

/** 审批时间与 daterange 双向绑定（界面精确到天，请求时补全日起止）。 */
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

/** 下拉选项转为 ElSelect 可用结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 组装筛选请求体（不含分页）。 */
function buildFilterRequest() {
  const approvalRange = toReportApiDateTimeRange(
    queryParams.approvalTimeStart,
    queryParams.approvalTimeEnd,
  );
  return {
    domainScope: props.domainScope,
    typeEnum: queryParams.typeEnum || undefined,
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
function rowUnitText(row: DocumentDetailRowDto): string {
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
function formatQtyWithUnit(row: DocumentDetailRowDto) {
  const qty = formatQtyNatural(row.itemQuantity);
  const unit = rowUnitText(row);
  if (!qty && !unit) return '';
  if (!unit) return qty;
  if (!qty) return unit;
  return `${qty}${unit}`;
}

/** 单价展示：2 位小数 + 元，有操作单位时追加「/单位」。 */
function formatPriceWithUnit(row: DocumentDetailRowDto) {
  const price = fmtReportNumber(row.price, 2);
  const unit = rowUnitText(row);
  if (!price) return '';
  return unit ? `${price}元/${unit}` : `${price}元`;
}

/** 金额展示：2 位小数 + 元。 */
function formatAmountWithUnit(row: DocumentDetailRowDto) {
  const amount = fmtReportNumber(row.totalPrice, 2);
  if (!amount) return '';
  return `${amount}元`;
}

/** 盘点相关列：空值显示占位符。 */
function formatQtyOrDash(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  return fmtReportNumber(value, 2);
}

/** 判断盘点列是否为空。 */
function isQtyEmpty(value: unknown) {
  return value === null || value === undefined || value === '';
}

/** 审批时间压缩为 MM-DD HH:mm。 */
function formatApprovalTime(value?: string) {
  const text = String(value ?? '').trim();
  if (!text) {
    return '—';
  }
  if (text.length >= 16) {
    return text.slice(5, 16);
  }
  return text;
}

const { run: runInit, loading: initLoading } = useRequest(loadDocumentDetailFormInitApi, {
  manual: true,
  onSuccess: (data) => {
    typeEnumOptions.value = optionDtosToSelectItems(data?.typeEnumOptions);
    sourceLocationOptions.value = optionDtosToSelectItems(data?.sourceLocationOptions);
    purposeLocationOptions.value = optionDtosToSelectItems(data?.purposeLocationOptions);
    queryParams.typeEnum = data?.defaultTypeEnum ?? queryParams.typeEnum;
    queryParams.approvalTimeStart =
      normalizeReportDateRangeYmd(data?.defaultApprovalTimeStart) ?? queryParams.approvalTimeStart;
    queryParams.approvalTimeEnd =
      normalizeReportDateRangeYmd(data?.defaultApprovalTimeEnd) ?? queryParams.approvalTimeEnd;
  },
});

const { run: runPage, loading: pageLoading } = useRequest(pageDocumentDetailsApi, {
  manual: true,
  onSuccess: (data) => {
    tableData.value = data?.records ?? [];
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

/** 重置筛选并回到第一页。 */
function handleReset() {
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
  const data = await runExport(buildFilterRequest());
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
              >
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
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
                :shortcuts="dateRangeShortcuts"
                :class="[REPORT_QUERY_CONTROL, REPORT_QUERY_CONTROL_DATERANGE]"
              />
            </ElFormItem>
            <ElFormItem label="品名/编码">
              <ElInput
                v-model="queryParams.productKeyword"
                clearable
                placeholder="品名或编码"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="批号">
              <ElInput
                v-model="queryParams.lotNumberKeyword"
                clearable
                placeholder="批号"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="单据号">
              <ElInput
                v-model="queryParams.busNo"
                clearable
                placeholder="单据号"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handleQuery"
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
              row-key="lineId"
            >
              <ElTableColumn label="单据号" min-width="136" fixed="left" show-overflow-tooltip>
                <template #default="{ row }">
                  <span :class="REPORT_TABLE_BUSNO">{{ row.busNo }}</span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="typeEnumText"
                label="单据类型"
                min-width="100"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="sourceLocationName"
                label="源库位"
                min-width="100"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="purposeLocationName"
                label="目的库位"
                min-width="100"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="supplierName"
                label="供应商"
                min-width="108"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="productName"
                label="品名"
                min-width="132"
                show-overflow-tooltip
                :class-name="REPORT_TABLE_COL_GROUP_START"
                :label-class-name="REPORT_TABLE_COL_GROUP_START"
              />
              <ElTableColumn
                prop="productBusNo"
                label="编码"
                min-width="92"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="specification"
                label="规格"
                min-width="92"
                show-overflow-tooltip
              />
              <ElTableColumn prop="lotNumber" label="批号" min-width="88" show-overflow-tooltip />
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
                <template #default="{ row }">{{ formatAmountWithUnit(row) }}</template>
              </ElTableColumn>
              <ElTableColumn
                label="账面"
                min-width="76"
                align="right"
                :class-name="`${REPORT_TABLE_NUM_CELL} ${REPORT_TABLE_COL_GROUP_START}`"
                :label-class-name="REPORT_TABLE_COL_GROUP_START"
              >
                <template #default="{ row }">
                  <span :class="{ [REPORT_TABLE_EMPTY_VALUE]: isQtyEmpty(row.bookQuantity) }">
                    {{ formatQtyOrDash(row.bookQuantity) }}
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
                  <span :class="{ [REPORT_TABLE_EMPTY_VALUE]: isQtyEmpty(row.actualQuantity) }">
                    {{ formatQtyOrDash(row.actualQuantity) }}
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
                  <span :class="{ [REPORT_TABLE_EMPTY_VALUE]: isQtyEmpty(row.gainLossQuantity) }">
                    {{ formatQtyOrDash(row.gainLossQuantity) }}
                  </span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="applicantName"
                label="制单人"
                min-width="88"
                show-overflow-tooltip
                :class-name="REPORT_TABLE_COL_GROUP_START"
                :label-class-name="REPORT_TABLE_COL_GROUP_START"
              />
              <ElTableColumn
                prop="approverName"
                label="审批人"
                min-width="88"
                show-overflow-tooltip
              />
              <ElTableColumn label="审批时间" min-width="118" show-overflow-tooltip>
                <template #default="{ row }">{{ formatApprovalTime(row.approvalTime) }}</template>
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
