<!--
  单个医生工作量明细弹窗：紧凑汇总 + 固定高度医嘱明细表格。
-->
<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type {
  PractitionerWorkloadDetailLineDto,
  PractitionerWorkloadFinTypeRowDto,
  PractitionerWorkloadQueryFilter,
  PractitionerWorkloadRegistrationRowDto,
  PractitionerWorkloadSummaryTotalsDto,
} from '#/api/report/practitioner-workload';

import { computed, reactive, ref, watch } from 'vue';

import { TableLayout } from '@vben-core/table-layout-ui';

import { Download } from '@element-plus/icons-vue';
import { ElButton, ElMessage, ElPagination } from 'element-plus';

import {
  exportPractitionerWorkloadDetailApi,
  pagePractitionerWorkloadDetailApi,
} from '#/api/report/practitioner-workload';
import AppDialog from '#/components/app-dialog.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import { useRequest } from '#/hooks/use-request';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { fmtReportNumber } from '#/views/report/shared/report-format';

defineOptions({ name: 'PractitionerWorkloadDoctorDetailDialog' });

interface Props {
  baseFilter: PractitionerWorkloadQueryFilter;
  finTypeRows: PractitionerWorkloadFinTypeRowDto[];
  practitionerId: string;
  practitionerName: string;
  registrationRows: PractitionerWorkloadRegistrationRowDto[];
  summaryTotals: PractitionerWorkloadSummaryTotalsDto;
}

interface DetailTableRow extends PractitionerWorkloadDetailLineDto {
  _rowKey: string;
}

interface MetricItem {
  highlight?: boolean;
  label: string;
  value: string;
}

const props = defineProps<Props>();
const visible = defineModel<boolean>({ default: false });

const detailRows = ref<DetailTableRow[]>([]);
const detailTotal = ref(0);
const detailPage = reactive({ pageNo: 1, pageSize: 10 });

const { run: runDetailPage, loading: detailLoading } = useRequest(
  pagePractitionerWorkloadDetailApi,
  {
    manual: true,
    onSuccess: (data) => {
      detailRows.value = (data?.content ?? []).map((row) => ({
        ...row,
        _rowKey: `${row.chargeItemId ?? ''}-${row.paymentId ?? ''}`,
      }));
      detailTotal.value = Number(data?.totalCount ?? 0);
    },
  },
);

const { run: runExport, loading: exportLoading } = useRequest(exportPractitionerWorkloadDetailApi, {
  manual: true,
});

/** 汇总区第一行：工作量指标。 */
const workloadSummaryMetrics = computed<MetricItem[]>(() => {
  const t = props.summaryTotals;
  return [
    { label: '接诊人数', value: String(t.encounterCountTotal ?? 0) },
    { label: '处方数量', value: String(t.prescriptionCountTotal ?? 0) },
    { label: '诊疗项目', value: String(t.activityItemCountTotal ?? 0) },
    {
      label: '开单总金额',
      value: fmtReportNumber(t.receivableAmountTotal),
      highlight: true,
    },
  ];
});

/** 汇总区第二行：费用指标。 */
const financialSummaryMetrics = computed<MetricItem[]>(() => {
  const t = props.summaryTotals;
  return [
    { label: '应收', value: fmtReportNumber(t.receivableAmountTotal) },
    { label: '实收', value: fmtReportNumber(t.actualReceivedAmountTotal) },
    { label: '成本', value: fmtReportNumber(t.costAmountTotal) },
    { label: '挂号净笔数', value: String(t.registrationNetCountTotal ?? 0) },
  ];
});

const hasBreakdown = computed(
  () => props.finTypeRows.length > 0 || props.registrationRows.length > 0,
);

const detailColumns: TableColumn[] = [
  { prop: 'catalogTypeDisplay', label: '类型', minWidth: 72 },
  { prop: 'productName', label: '项目名称', minWidth: 160 },
  { prop: 'requestingOrgName', label: '科室', minWidth: 100 },
  { prop: 'enteredDate', label: '开立时间', minWidth: 158 },
  {
    prop: 'receivableAmount',
    label: '应收',
    minWidth: 88,
    align: 'right',
    formatter: (row) =>
      fmtReportNumber((row as PractitionerWorkloadDetailLineDto).receivableAmount),
  },
  {
    prop: 'actualReceivedAmount',
    label: '实收',
    minWidth: 88,
    align: 'right',
    formatter: (row) =>
      fmtReportNumber((row as PractitionerWorkloadDetailLineDto).actualReceivedAmount),
  },
];

/** 组装带医生 id 的明细查询条件。 */
function buildDoctorDetailFilter() {
  return {
    ...props.baseFilter,
    practitionerId: preserveApiIdForRequest(props.practitionerId),
  };
}

/** 拉取当前医生医嘱明细分页。 */
async function loadDetailPage() {
  await runDetailPage({
    ...buildDoctorDetailFilter(),
    pageNo: detailPage.pageNo,
    pageSize: detailPage.pageSize,
  });
}

/** 导出当前医生明细 Excel。 */
async function handleExportDetail() {
  const data = await runExport(buildDoctorDetailFilter());
  if (!data?.body?.trim()) {
    ElMessage.warning('当前条件下无明细可导出');
    return;
  }
  downloadBinaryExportFile(data, `${props.practitionerName}-工作量明细.xlsx`);
  if (data.truncated) {
    ElMessage.warning('导出数据已触达行数上限');
  } else {
    ElMessage.success(`导出成功，共 ${data.rowCount ?? 0} 行`);
  }
}

/** 明细翻页。 */
function handleCurrentPageChange(page: number) {
  detailPage.pageNo = page;
  void loadDetailPage();
}

/** 明细每页条数变更。 */
function handlePageSizeChange(size: number) {
  detailPage.pageSize = size;
  detailPage.pageNo = 1;
  void loadDetailPage();
}

watch(
  () => visible.value,
  (open) => {
    if (!open) {
      detailRows.value = [];
      detailTotal.value = 0;
      detailPage.pageNo = 1;
      return;
    }
    void loadDetailPage();
  },
);
</script>

<template>
  <AppDialog
    v-model="visible"
    :title="`${practitionerName} · 工作量明细`"
    width="1000px"
    top="2vh"
    destroy-on-close
    class="practitioner-workload-doctor-detail-dialog"
  >
    <div
      v-loading="detailLoading"
      element-loading-text="加载明细中..."
      class="practitioner-workload-doctor-detail"
    >
      <div class="practitioner-workload-doctor-detail__summary">
        <div class="practitioner-workload-doctor-detail__metrics-grid">
          <div
            v-for="item in workloadSummaryMetrics"
            :key="item.label"
            class="practitioner-workload-doctor-detail__metric"
          >
            <span class="practitioner-workload-doctor-detail__metric-label">{{ item.label }}</span>
            <span
              class="practitioner-workload-doctor-detail__metric-value"
              :class="{
                'practitioner-workload-doctor-detail__metric-value--highlight': item.highlight,
              }"
            >
              {{ item.value }}
            </span>
          </div>
          <div class="practitioner-workload-doctor-detail__metrics-divider" />
          <div
            v-for="item in financialSummaryMetrics"
            :key="item.label"
            class="practitioner-workload-doctor-detail__metric"
          >
            <span class="practitioner-workload-doctor-detail__metric-label">{{ item.label }}</span>
            <span
              class="practitioner-workload-doctor-detail__metric-value"
              :class="{
                'practitioner-workload-doctor-detail__metric-value--highlight': item.highlight,
              }"
            >
              {{ item.value }}
            </span>
          </div>
        </div>

        <div v-if="hasBreakdown" class="practitioner-workload-doctor-detail__breakdown">
          <div
            v-if="finTypeRows.length > 0"
            class="practitioner-workload-doctor-detail__breakdown-row"
          >
            <span class="practitioner-workload-doctor-detail__breakdown-label">费用构成</span>
            <template
              v-for="(row, index) in finTypeRows"
              :key="row.finTypeCode ?? row.finTypeDisplay"
            >
              <span v-if="index > 0" class="practitioner-workload-doctor-detail__breakdown-sep"
                >·</span
              >
              <span>{{ row.finTypeDisplay }} {{ fmtReportNumber(row.receivableAmount) }}</span>
            </template>
          </div>
          <div
            v-if="registrationRows.length > 0"
            class="practitioner-workload-doctor-detail__breakdown-row"
          >
            <span class="practitioner-workload-doctor-detail__breakdown-label">挂号明细</span>
            <template
              v-for="(row, index) in registrationRows"
              :key="row.serviceTypeId ?? row.serviceTypeName"
            >
              <span v-if="index > 0" class="practitioner-workload-doctor-detail__breakdown-sep"
                >·</span
              >
              <span>{{ row.serviceTypeName }} {{ row.netChargeCount ?? 0 }} 笔</span>
            </template>
          </div>
        </div>
      </div>

      <div class="practitioner-workload-doctor-detail__detail-section">
        <div class="practitioner-workload-doctor-detail__detail-head">
          <SectionTitle title="医嘱明细" />
          <ElButton :icon="Download" :loading="exportLoading" @click="handleExportDetail">
            导出明细
          </ElButton>
        </div>

        <div class="practitioner-workload-doctor-detail__table-wrap">
          <TableLayout
            :table-data="detailRows"
            :loading="detailLoading"
            :total="detailTotal"
            :query-params="{}"
            :form-items="[]"
            :table-columns="detailColumns"
            :show-top-query="false"
            :show-pagination="false"
            row-key="_rowKey"
            border
            stripe
          />
        </div>

        <div class="practitioner-workload-doctor-detail__pager">
          <ElPagination
            :current-page="detailPage.pageNo"
            :page-size="detailPage.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="detailTotal"
            background
            layout="total, sizes, prev, pager, next"
            @size-change="handlePageSizeChange"
            @current-change="handleCurrentPageChange"
          />
        </div>
      </div>
    </div>
  </AppDialog>
</template>

<style scoped>
.practitioner-workload-doctor-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
}

.practitioner-workload-doctor-detail__summary {
  flex-shrink: 0;
  padding: 14px 16px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 1px 3px rgb(15 23 42 / 5%);
}

.practitioner-workload-doctor-detail__metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 16px;
}

.practitioner-workload-doctor-detail__metrics-divider {
  grid-column: 1 / -1;
  height: 0;
  margin: 2px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.practitioner-workload-doctor-detail__metric {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.practitioner-workload-doctor-detail__metric-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.practitioner-workload-doctor-detail__metric-value {
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

.practitioner-workload-doctor-detail__metric-value--highlight {
  color: var(--el-color-primary);
}

.practitioner-workload-doctor-detail__breakdown {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
}

.practitioner-workload-doctor-detail__breakdown-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.practitioner-workload-doctor-detail__breakdown-label {
  flex-shrink: 0;
  margin-right: 2px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.practitioner-workload-doctor-detail__breakdown-sep {
  color: var(--el-text-color-placeholder);
}

.practitioner-workload-doctor-detail__detail-section {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 10px;
}

.practitioner-workload-doctor-detail__detail-head {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.practitioner-workload-doctor-detail__table-wrap {
  flex: 1;
  min-height: 240px;
  overflow: auto;
}

.practitioner-workload-doctor-detail__pager {
  display: flex;
  flex-shrink: 0;
  justify-content: flex-end;
}
</style>

<style>
.practitioner-workload-doctor-detail-dialog.el-dialog {
  display: flex;
  width: 1000px !important;
  max-width: 92vw;
  max-height: calc(100vh - 48px);
  margin-bottom: 0;
  flex-direction: column;
  overflow: hidden;
}

.practitioner-workload-doctor-detail-dialog .el-dialog__body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding-top: 8px;
  overflow: hidden;
}
</style>
