<!--
  单个护士工作量明细弹窗：汇总指标 + 诊疗执行/耗材使用明细表。
-->
<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type {
  NurseConsumableDetailRowDto,
  NurseConsumableSummaryRowDto,
  NurseExecutionDetailRowDto,
  NurseExecutionSummaryRowDto,
  NurseMainTabCode,
  NurseWorkloadQueryFilter,
} from '#/api/report/nurse-workload';

import { computed, reactive, ref, watch } from 'vue';

import { TableLayout } from '@vben-core/table-layout-ui';

import { Download } from '@element-plus/icons-vue';
import { ElButton, ElMessage, ElPagination, ElTabPane, ElTabs } from 'element-plus';

import {
  exportNurseWorkloadDetailApi,
  NURSE_MAIN_TAB_CONSUMABLE,
  NURSE_MAIN_TAB_EXECUTION,
  pageNurseConsumableWorkloadDetailApi,
  pageNurseExecutionWorkloadDetailApi,
} from '#/api/report/nurse-workload';
import AppDialog from '#/components/app-dialog.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import { useRequest } from '#/hooks/use-request';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { fmtReportNumber } from '#/views/report/shared/report-format';

import { formatChipQuantity, type NurseConsumableItemChip } from './nurse-workload-card-data';

defineOptions({ name: 'NurseWorkloadNurseDetailDialog' });

interface Props {
  baseFilter: NurseWorkloadQueryFilter;
  consumableItemChips?: NurseConsumableItemChip[];
  consumableItemCount?: number;
  consumableSummaryRows: NurseConsumableSummaryRowDto[];
  consumableTotalAmount?: number | string;
  consumableTotalQuantity?: number | string;
  executionSummaryRows: NurseExecutionSummaryRowDto[];
  executionTotalAmount?: number | string;
  executionTotalCount?: number;
  practitionerId: string;
  practitionerName: string;
}

const props = withDefaults(defineProps<Props>(), {
  consumableItemChips: () => [],
  consumableItemCount: 0,
  consumableTotalAmount: 0,
  consumableTotalQuantity: 0,
  executionTotalAmount: 0,
  executionTotalCount: 0,
});
const visible = defineModel<boolean>({ default: false });

const mainTab = ref<NurseMainTabCode>(NURSE_MAIN_TAB_EXECUTION);
const executionDetailRows = ref<NurseExecutionDetailRowDto[]>([]);
const executionDetailTotal = ref(0);
const consumableDetailRows = ref<NurseConsumableDetailRowDto[]>([]);
const consumableDetailTotal = ref(0);
const detailPage = reactive({ pageNo: 1, pageSize: 10 });

const workloadMetrics = computed(() => [
  { label: '处置次数', value: String(props.executionTotalCount ?? 0) },
  { label: '处置金额', value: fmtReportNumber(props.executionTotalAmount, 2) },
  { label: '耗材项数', value: String(props.consumableItemCount ?? 0) },
  {
    label: '耗材总数量',
    value: fmtReportNumber(props.consumableTotalQuantity, 2),
  },
  {
    label: '耗材金额',
    value: fmtReportNumber(props.consumableTotalAmount, 2),
    highlight: true,
  },
  {
    label: '金额合计',
    value: fmtReportNumber(
      Number(props.executionTotalAmount ?? 0) + Number(props.consumableTotalAmount ?? 0),
      2,
    ),
    highlight: true,
  },
]);

const hasExecutionBreakdown = computed(() => props.executionSummaryRows.length > 0);
const hasConsumableBreakdown = computed(
  () => (props.consumableItemChips?.length ?? 0) > 0 || props.consumableSummaryRows.length > 0,
);

const { run: runExecutionDetail, loading: executionDetailLoading } = useRequest(
  pageNurseExecutionWorkloadDetailApi,
  {
    manual: true,
    onSuccess: (data) => {
      executionDetailRows.value = data?.rows ?? [];
      executionDetailTotal.value = Number(data?.total ?? 0);
    },
  },
);

const { run: runConsumableDetail, loading: consumableDetailLoading } = useRequest(
  pageNurseConsumableWorkloadDetailApi,
  {
    manual: true,
    onSuccess: (data) => {
      consumableDetailRows.value = data?.rows ?? [];
      consumableDetailTotal.value = Number(data?.total ?? 0);
    },
  },
);

const { run: runExport, loading: exportLoading } = useRequest(exportNurseWorkloadDetailApi, {
  manual: true,
});

const executionDetailColumns: TableColumn[] = [
  { prop: 'itemName', label: '诊疗项目', minWidth: 160 },
  { prop: 'categoryDisplay', label: '类目', minWidth: 100 },
  { prop: 'generateSourceDisplay', label: '来源', minWidth: 90 },
  {
    prop: 'amount',
    label: '金额',
    minWidth: 100,
    formatter: (row) => fmtReportNumber((row as NurseExecutionDetailRowDto).amount, 2),
  },
  { prop: 'recordedTime', label: '执行时间', minWidth: 160 },
  { prop: 'encounterBusNo', label: '就诊号', minWidth: 120 },
  { prop: 'patientName', label: '患者', minWidth: 100 },
];

const consumableDetailColumns: TableColumn[] = [
  { prop: 'deviceName', label: '耗材名称', minWidth: 160 },
  { prop: 'specText', label: '规格', minWidth: 100 },
  { prop: 'hvcmDisplay', label: '高/低值', minWidth: 90 },
  { prop: 'generateSourceDisplay', label: '来源', minWidth: 90 },
  { prop: 'businessTime', label: '业务时间', minWidth: 160 },
  {
    prop: 'quantity',
    label: '数量',
    minWidth: 90,
    formatter: (row) => fmtReportNumber((row as NurseConsumableDetailRowDto).quantity, 2),
  },
  {
    prop: 'amount',
    label: '金额',
    minWidth: 100,
    formatter: (row) => fmtReportNumber((row as NurseConsumableDetailRowDto).amount, 2),
  },
  { prop: 'unitCodeLabel', label: '单位', minWidth: 80 },
  { prop: 'encounterBusNo', label: '就诊号', minWidth: 120 },
  { prop: 'patientName', label: '患者', minWidth: 100 },
];

/** 组装带护士 id 的明细查询条件。 */
function buildNurseDetailFilter() {
  return {
    ...props.baseFilter,
    practitionerId: preserveApiIdForRequest(props.practitionerId),
  };
}

/** 拉取当前主 Tab 明细分页。 */
async function loadDetailPage() {
  const filter = buildNurseDetailFilter();
  if (mainTab.value === NURSE_MAIN_TAB_EXECUTION) {
    await runExecutionDetail({
      ...filter,
      pageNo: detailPage.pageNo,
      pageSize: detailPage.pageSize,
    });
    return;
  }
  await runConsumableDetail({
    ...filter,
    pageNo: detailPage.pageNo,
    pageSize: detailPage.pageSize,
  });
}

/** 导出当前主 Tab 明细 Excel。 */
async function handleExportDetail() {
  const data = await runExport({
    ...buildNurseDetailFilter(),
    mainTabCode: mainTab.value,
  });
  if (!data?.fileBytes?.trim()) {
    ElMessage.warning('当前条件下无明细可导出');
    return;
  }
  const tabLabel = mainTab.value === NURSE_MAIN_TAB_EXECUTION ? '诊疗执行' : '耗材使用';
  downloadBinaryExportFile(
    data,
    data.fileName ?? `${props.practitionerName}_护士工作量_${tabLabel}.xlsx`,
  );
  if (data.truncated) {
    ElMessage.warning('导出数据已触达行数上限');
  } else {
    ElMessage.success(`导出成功，共 ${data.exportedRowCount ?? 0} 行`);
  }
}

/** 主 Tab 切换后重置分页并重新拉取。 */
function handleMainTabChange() {
  detailPage.pageNo = 1;
  void loadDetailPage();
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
      executionDetailRows.value = [];
      executionDetailTotal.value = 0;
      consumableDetailRows.value = [];
      consumableDetailTotal.value = 0;
      detailPage.pageNo = 1;
      mainTab.value = NURSE_MAIN_TAB_EXECUTION;
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
    class="nurse-workload-nurse-detail-dialog"
  >
    <div class="nurse-workload-nurse-detail">
      <div class="nurse-workload-nurse-detail__summary-card">
        <div class="nurse-workload-nurse-detail__metrics">
          <div
            v-for="item in workloadMetrics"
            :key="item.label"
            class="nurse-workload-nurse-detail__metric"
          >
            <span class="nurse-workload-nurse-detail__metric-label">{{ item.label }}</span>
            <span
              class="nurse-workload-nurse-detail__metric-value"
              :class="{ 'nurse-workload-nurse-detail__metric-value--highlight': item.highlight }"
            >
              {{ item.value }}
            </span>
          </div>
        </div>
      </div>

      <div
        v-if="hasExecutionBreakdown || hasConsumableBreakdown"
        class="nurse-workload-nurse-detail__breakdown"
      >
        <div v-if="hasExecutionBreakdown" class="nurse-workload-nurse-detail__breakdown-row">
          <span class="nurse-workload-nurse-detail__breakdown-label">诊疗类目</span>
          <template v-for="(row, index) in executionSummaryRows" :key="row.categoryEnum">
            <span v-if="index > 0" class="nurse-workload-nurse-detail__breakdown-sep">·</span>
            <span>{{ row.categoryDisplay }} {{ row.executionCount ?? 0 }} 次</span>
          </template>
        </div>
        <div v-if="hasConsumableBreakdown" class="nurse-workload-nurse-detail__breakdown-row">
          <span class="nurse-workload-nurse-detail__breakdown-label">耗材明细</span>
          <template v-if="(consumableItemChips?.length ?? 0) > 0">
            <template v-for="(chip, index) in consumableItemChips" :key="chip.key">
              <span v-if="index > 0" class="nurse-workload-nurse-detail__breakdown-sep">·</span>
              <span>{{ chip.label }} {{ formatChipQuantity(chip.quantity) }}</span>
            </template>
          </template>
          <template v-else>
            <template v-for="(row, index) in consumableSummaryRows" :key="row.hvcmDisplay">
              <span v-if="index > 0" class="nurse-workload-nurse-detail__breakdown-sep">·</span>
              <span>{{ row.hvcmDisplay }} {{ fmtReportNumber(row.quantityTotal, 2) }}</span>
            </template>
          </template>
        </div>
      </div>

      <div class="nurse-workload-nurse-detail__detail-section">
        <div class="nurse-workload-nurse-detail__detail-head">
          <SectionTitle title="明细" />
          <ElButton :icon="Download" :loading="exportLoading" @click="handleExportDetail">
            导出明细
          </ElButton>
        </div>

        <div class="nurse-workload-nurse-detail__table-wrap">
          <ElTabs v-model="mainTab" @tab-change="handleMainTabChange">
            <ElTabPane label="诊疗执行" :name="NURSE_MAIN_TAB_EXECUTION">
              <TableLayout
                :table-data="executionDetailRows"
                :loading="executionDetailLoading"
                :total="executionDetailTotal"
                :query-params="{}"
                :form-items="[]"
                :table-columns="executionDetailColumns"
                :show-top-query="false"
                :show-pagination="false"
                row-key="procedureId"
                border
                stripe
              />
            </ElTabPane>
            <ElTabPane label="耗材使用" :name="NURSE_MAIN_TAB_CONSUMABLE">
              <TableLayout
                :table-data="consumableDetailRows"
                :loading="consumableDetailLoading"
                :total="consumableDetailTotal"
                :query-params="{}"
                :form-items="[]"
                :table-columns="consumableDetailColumns"
                :show-top-query="false"
                :show-pagination="false"
                row-key="dispenseId"
                border
                stripe
              />
            </ElTabPane>
          </ElTabs>
        </div>

        <div
          v-if="
            (mainTab === NURSE_MAIN_TAB_EXECUTION && executionDetailTotal > 0) ||
            (mainTab === NURSE_MAIN_TAB_CONSUMABLE && consumableDetailTotal > 0)
          "
          class="nurse-workload-nurse-detail__pager"
        >
          <ElPagination
            :current-page="detailPage.pageNo"
            :page-size="detailPage.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="
              mainTab === NURSE_MAIN_TAB_EXECUTION ? executionDetailTotal : consumableDetailTotal
            "
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
.nurse-workload-nurse-detail {
  display: flex;
  max-height: calc(100vh - 108px);
  flex-direction: column;
  gap: 16px;
}

.nurse-workload-nurse-detail__summary-card {
  flex-shrink: 0;
  padding: 14px 16px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 1px 3px rgb(15 23 42 / 5%);
}

.nurse-workload-nurse-detail__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 16px;
}

.nurse-workload-nurse-detail__metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nurse-workload-nurse-detail__metric-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.nurse-workload-nurse-detail__metric-value {
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

.nurse-workload-nurse-detail__metric-value--highlight {
  color: var(--el-color-primary);
}

.nurse-workload-nurse-detail__breakdown {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
}

.nurse-workload-nurse-detail__breakdown-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.nurse-workload-nurse-detail__breakdown-label {
  margin-right: 4px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.nurse-workload-nurse-detail__breakdown-sep {
  color: var(--el-text-color-placeholder);
}

.nurse-workload-nurse-detail__detail-section {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 10px;
}

.nurse-workload-nurse-detail__detail-head {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.nurse-workload-nurse-detail__table-wrap {
  height: min(480px, calc(100vh - 300px));
  min-height: 360px;
  overflow: auto;
}

.nurse-workload-nurse-detail__pager {
  display: flex;
  flex-shrink: 0;
  justify-content: flex-end;
}
</style>

<style>
.nurse-workload-nurse-detail-dialog.el-dialog {
  display: flex;
  width: 1000px !important;
  max-width: 92vw;
  max-height: calc(100vh - 48px);
  margin-bottom: 0;
  flex-direction: column;
  overflow: hidden;
}

.nurse-workload-nurse-detail-dialog .el-dialog__body {
  display: flex;
  min-height: calc(100vh - 120px);
  flex: 1;
  flex-direction: column;
  padding-top: 8px;
  overflow: hidden;
}
</style>
