<!--
  住院用药明细：选患者 → 计划发药日期 → 明细/汇总查询（只读）。
-->
<script setup lang="ts">
import type {
  InpatientMedicationDetailLineDto,
  InpatientMedicationPatientRowDto,
  InpatientMedicationPresentationMode,
  InpatientMedicationSummaryLineDto,
} from '#/api/report/inpatient-medication-detail';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Refresh } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  loadInpatientMedicationDetailPageInitApi,
  pageInpatientMedicationPatientsApi,
  PRESENTATION_MODE_DETAIL,
  PRESENTATION_MODE_SUMMARY,
  queryInpatientMedicationDetailApi,
} from '#/api/report/inpatient-medication-detail';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
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
import {
  buildReportDateRangeShortcuts,
  normalizeReportDateRangeYmd,
} from '#/views/report/shared/report-date-range-shortcuts';
import { fmtReportNumber } from '#/views/report/shared/report-format';

defineOptions({ name: 'InpatientMedicationDetailReportPage' });

interface QueryState {
  patientKeyword?: string;
  encounterId?: string;
  patientId?: string;
  dateFrom?: string;
  dateTo?: string;
  presentationMode: InpatientMedicationPresentationMode;
}

const PAGE_TITLE = '住院用药明细';

const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);
const patientOptions = ref<InpatientMedicationPatientRowDto[]>([]);
const presentationOptions = ref<ApiOptionItem[]>([]);
const detailLines = ref<InpatientMedicationDetailLineDto[]>([]);
const summaryLines = ref<InpatientMedicationSummaryLineDto[]>([]);
const grandTotal = ref('');
const lineCount = ref(0);

const queryParams = reactive<QueryState>({
  presentationMode: PRESENTATION_MODE_DETAIL,
});

/** 患者候选一次拉取上限（仅用于下拉选人，主表无分页）。 */
const PATIENT_CANDIDATE_PAGE_SIZE = 50;

const isDetailMode = computed(() => queryParams.presentationMode === PRESENTATION_MODE_DETAIL);

const tableLoading = computed(
  () => initLoading.value || patientLoading.value || queryLoading.value,
);

const selectedPatientLabel = computed(() => {
  const selected = patientOptions.value.find(
    (row) => String(row.encounterId ?? '') === String(queryParams.encounterId ?? ''),
  );
  if (!selected) {
    return '';
  }
  const parts = [
    selected.patientName,
    selected.inpatientNo,
    selected.wardName,
    selected.bedName ? `${selected.bedName}床` : '',
  ].filter((part) => String(part ?? '').trim());
  return parts.join(' / ');
});

const footerSummaryItems = computed(() => [
  { label: '行数', value: lineCount.value },
  {
    label: '净金额合计',
    value: grandTotal.value || '0.00',
    primary: true,
    prefix: '¥',
  },
]);

const dateRangeShortcuts = buildReportDateRangeShortcuts();

/** 计划发药日与 daterange 双向绑定。 */
const plannedDateRange = computed({
  get() {
    const start = normalizeReportDateRangeYmd(queryParams.dateFrom);
    const end = normalizeReportDateRangeYmd(queryParams.dateTo);
    if (start && end) {
      return [start, end] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.dateFrom = value?.[0];
    queryParams.dateTo = value?.[1];
  },
});

/** 展示模式下拉选项。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 患者下拉展示文案。 */
function formatPatientOptionLabel(row: InpatientMedicationPatientRowDto) {
  const parts = [
    row.patientName,
    row.inpatientNo,
    row.wardName,
    row.bedName ? `${row.bedName}床` : '',
  ].filter((part) => String(part ?? '').trim());
  return parts.join(' / ') || String(row.encounterId ?? '');
}

/** 数量自然展示（可不补零）。 */
function formatQtyNatural(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  const n = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(n)) {
    return String(value);
  }
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 4,
  });
}

/** 明细数量 + 单位。 */
function formatDetailQty(row: InpatientMedicationDetailLineDto) {
  const qty = formatQtyNatural(row.quantityValue);
  const unit = String(row.quantityUnitDisplay ?? '').trim();
  if (!qty && !unit) {
    return '';
  }
  if (!unit) {
    return qty;
  }
  if (!qty) {
    return unit;
  }
  return `${qty}${unit}`;
}

/** 汇总净用量展示：满包 + 拆零。 */
function formatSummaryQty(row: InpatientMedicationSummaryLineDto) {
  const packageQty = formatQtyNatural(row.packageQuantity);
  const packageUnit = String(row.packageUnitDisplay ?? '').trim();
  const splitQty = formatQtyNatural(row.splitUnitQuantity);
  const splitUnit = String(row.splitUnitDisplay ?? '').trim();
  const parts: string[] = [];
  if (packageQty) {
    parts.push(packageUnit ? `${packageQty}${packageUnit}` : packageQty);
  }
  if (splitQty) {
    parts.push(splitUnit ? `${splitQty}${splitUnit}` : splitQty);
  }
  return parts.join(' + ');
}

/** 明细行客户可见执行时间：优先 recorded，回落 planned。 */
function resolveUsageDisplayTime(row: {
  recordedTime?: string;
  plannedDispenseTime?: string;
}): string {
  const recorded = String(row.recordedTime ?? '').trim();
  if (recorded) {
    return recorded;
  }
  return String(row.plannedDispenseTime ?? '').trim();
}

/** 执行/计划时间展示。 */
function formatDateTime(value?: string) {
  const text = String(value ?? '').trim();
  if (!text) {
    return '—';
  }
  return text.length >= 19 ? text.slice(0, 19) : text;
}

const { run: runInit, loading: initLoading } = useRequest(
  loadInpatientMedicationDetailPageInitApi,
  {
    manual: true,
    onSuccess: (data) => {
      queryParams.dateFrom = normalizeReportDateRangeYmd(data?.dateFrom) || queryParams.dateFrom;
      queryParams.dateTo = normalizeReportDateRangeYmd(data?.dateTo) || queryParams.dateTo;
      presentationOptions.value = (data?.presentationOptions ?? []).map((item) => ({
        label: String(item.display ?? item.code ?? ''),
        value: String(item.code ?? ''),
      }));
      // 仅在尚未选定展示模式时采用 init 首项，避免重置后覆盖用户/页面默认 DETAIL
      const currentMode = queryParams.presentationMode;
      if (currentMode !== PRESENTATION_MODE_DETAIL && currentMode !== PRESENTATION_MODE_SUMMARY) {
        const firstMode = presentationOptions.value[0]?.value;
        if (firstMode === PRESENTATION_MODE_DETAIL || firstMode === PRESENTATION_MODE_SUMMARY) {
          queryParams.presentationMode = firstMode;
        }
      }
    },
  },
);

const { run: runPagePatients, loading: patientLoading } = useRequest(
  pageInpatientMedicationPatientsApi,
  {
    manual: true,
    onSuccess: (data) => {
      patientOptions.value = data?.records ?? [];
      // 仅一条候选时自动选中，减少点选
      if (patientOptions.value.length === 1) {
        const only = patientOptions.value[0]!;
        queryParams.encounterId = String(only.encounterId ?? '');
        queryParams.patientId = String(only.patientId ?? '');
      }
    },
  },
);

const { run: runQuery, loading: queryLoading } = useRequest(queryInpatientMedicationDetailApi, {
  manual: true,
  onSuccess: (data) => {
    detailLines.value = data?.detailLines ?? [];
    summaryLines.value = data?.summaryLines ?? [];
    lineCount.value = Number(data?.totals?.lineCount ?? 0);
    grandTotal.value = fmtReportNumber(data?.totals?.grandTotal, 2);
  },
});

/** 校验是否已选患者与日期。 */
function ensureQueryReady() {
  if (!queryParams.encounterId || !queryParams.patientId) {
    ElMessage.warning('请先选择住院患者');
    return false;
  }
  if (!queryParams.dateFrom || !queryParams.dateTo) {
    ElMessage.warning('请选择计划发药日期区间');
    return false;
  }
  return true;
}

/** 清空结果表与合计。 */
function clearResultTable() {
  detailLines.value = [];
  summaryLines.value = [];
  lineCount.value = 0;
  grandTotal.value = '';
}

/** 按关键字拉取住院患者候选；仅一条时自动选中。 */
async function searchPatients() {
  await runPagePatients({
    keyword: queryParams.patientKeyword?.trim() || undefined,
    pageNo: 1,
    pageSize: PATIENT_CANDIDATE_PAGE_SIZE,
  });
}

/** 拉取患者后：已自动选中则继续查明细。 */
async function searchPatientsAndQueryIfSelected() {
  await searchPatients();
  if (queryParams.encounterId && queryParams.patientId) {
    await getList();
  }
}

/** 查询用药明细/汇总。 */
async function getList() {
  if (!ensureQueryReady()) {
    return;
  }
  await runQuery({
    encounterId: preserveApiIdForRequest(queryParams.encounterId ?? ''),
    patientId: preserveApiIdForRequest(queryParams.patientId ?? ''),
    dateFrom: String(queryParams.dateFrom),
    dateTo: String(queryParams.dateTo),
    presentationMode: queryParams.presentationMode,
  });
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 患者关键字回车：按关键字刷新候选，再按需查询。 */
async function handlePatientKeywordSearch() {
  queryParams.encounterId = undefined;
  queryParams.patientId = undefined;
  clearResultTable();
  await searchPatientsAndQueryIfSelected();
}

/** 清空患者关键字：恢复候选全量并清空选中/表格。 */
async function handlePatientKeywordClear() {
  queryParams.patientKeyword = undefined;
  queryParams.encounterId = undefined;
  queryParams.patientId = undefined;
  clearResultTable();
  await searchPatientsAndQueryIfSelected();
}

/** 选择患者后写入 patientId 并查询。 */
function handlePatientChange(encounterId?: string) {
  const selected = patientOptions.value.find(
    (row) => String(row.encounterId ?? '') === String(encounterId ?? ''),
  );
  queryParams.encounterId = selected ? String(selected.encounterId ?? '') : undefined;
  queryParams.patientId = selected ? String(selected.patientId ?? '') : undefined;
  if (queryParams.encounterId && queryParams.patientId) {
    void getList();
    return;
  }
  clearResultTable();
}

/** 查询入口：未选患者时静默跳过（筛选 change 不弹警告）。 */
function handleQuery() {
  if (!queryParams.encounterId || !queryParams.patientId) {
    return;
  }
  void getList();
}

/** 工具栏「查询」：未选患者时给出提示。 */
function handleQueryClick() {
  void getList();
}

/** 重置筛选与结果，并自动重拉患者。 */
async function handleReset() {
  queryParams.patientKeyword = undefined;
  queryParams.encounterId = undefined;
  queryParams.patientId = undefined;
  queryParams.presentationMode = PRESENTATION_MODE_DETAIL;
  patientOptions.value = [];
  clearResultTable();
  await runInit();
  await searchPatientsAndQueryIfSelected();
}

onMounted(async () => {
  await runInit();
  await searchPatientsAndQueryIfSelected();
});
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryParams as Record<string, unknown>" label-width="96px">
          <template #fields>
            <ElFormItem label="患者关键字">
              <ElInput
                v-model="queryParams.patientKeyword"
                clearable
                placeholder="姓名/住院号/床号，回车筛选"
                :class="REPORT_QUERY_CONTROL"
                @keyup.enter="handlePatientKeywordSearch"
                @clear="handlePatientKeywordClear"
              />
            </ElFormItem>
            <ElFormItem label="住院患者">
              <ElSelect
                v-model="queryParams.encounterId"
                clearable
                filterable
                placeholder="请选择住院患者"
                :class="REPORT_QUERY_CONTROL"
                @change="handlePatientChange"
              >
                <ElOption
                  v-for="row in patientOptions"
                  :key="String(row.encounterId ?? '')"
                  :label="formatPatientOptionLabel(row)"
                  :value="String(row.encounterId ?? '')"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="计划发药日">
              <ElDatePicker
                v-model="plannedDateRange"
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
            <ElFormItem label="展示模式">
              <ElSelect
                v-model="queryParams.presentationMode"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(presentationOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
          </template>
          <template #actions>
            <ElButton type="primary" :loading="tableLoading" @click="handleQueryClick">
              查询
            </ElButton>
            <ElButton :icon="Refresh" :loading="tableLoading" @click="handleReset"> 重置 </ElButton>
          </template>
        </ReportQueryBar>
      </template>

      <template #toolbar>
        <ReportListToolbar
          :title="`${PAGE_TITLE} · ${isDetailMode ? '明细' : '汇总'}`"
          :total="lineCount"
        >
          <template #meta>
            <ElTag v-if="selectedPatientLabel" type="success" effect="plain">
              {{ selectedPatientLabel }}
            </ElTag>
          </template>
        </ReportListToolbar>
      </template>

      <template #table>
        <ReportTableScroll ref="tableScrollRef">
          <template #default="{ tableHeight }">
            <ReportElTable
              v-if="isDetailMode"
              :loading="tableLoading"
              :data="detailLines"
              :height="tableHeight"
              row-key="dispenseId"
            >
              <ElTableColumn
                type="index"
                label="序号"
                width="52"
                align="center"
                header-align="center"
              />
              <ElTableColumn
                prop="itemName"
                label="药品名称"
                min-width="160"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="itemSpec"
                label="规格"
                width="140"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                label="数量"
                width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ formatDetailQty(row) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="单价"
                width="90"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ fmtReportNumber(row.unitPrice, 2) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="金额"
                width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ fmtReportNumber(row.totalPrice, 2) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="usageStatusDisplay"
                label="发药状态"
                width="96"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  <ElTag v-if="row.usageStatusDisplay" effect="light">
                    {{ row.usageStatusDisplay }}
                  </ElTag>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="recordedTime"
                label="执行时间"
                width="168"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatDateTime(resolveUsageDisplayTime(row)) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="lotNumber"
                label="批号"
                width="110"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="summaryNo"
                label="汇总单号"
                width="150"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
            </ReportElTable>

            <ReportElTable
              v-else
              :loading="tableLoading"
              :data="summaryLines"
              :height="tableHeight"
              row-key="medicationId"
            >
              <ElTableColumn
                type="index"
                label="序号"
                width="52"
                align="center"
                header-align="center"
              />
              <ElTableColumn
                prop="itemName"
                label="药品名称"
                min-width="200"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="itemSpec"
                label="规格"
                width="160"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                label="净用量"
                width="160"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_NUM_CELL"
              >
                <template #default="{ row }">
                  {{ formatSummaryQty(row) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="单价"
                width="100"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ fmtReportNumber(row.unitPrice, 2) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="净金额"
                width="120"
                align="right"
                header-align="right"
                :class-name="REPORT_TABLE_AMOUNT_CELL"
              >
                <template #default="{ row }">
                  {{ fmtReportNumber(row.lineTotalAmount, 2) }}
                </template>
              </ElTableColumn>
            </ReportElTable>
          </template>
        </ReportTableScroll>
      </template>

      <template #footer>
        <ReportPageFooter
          :summary-items="footerSummaryItems"
          :current-page="1"
          :page-size="lineCount || 1"
          :total="lineCount"
          layout="total"
        />
      </template>
    </ReportListPageLayout>
  </Page>
</template>
