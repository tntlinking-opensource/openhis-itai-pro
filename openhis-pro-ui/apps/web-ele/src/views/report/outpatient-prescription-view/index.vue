<!--
  门诊处方查看：筛选 + 方级列表（一张方一行）+ 详情抽屉看药品明细；打印与药房处方一致。
  支持已发药待点评（行内点评 / 批量点评）、已点评撤销。
  主类型：T3 带操作列表（点评 / 批量点评 / 撤销点评）；附加：详情 Drawer / 打印。
-->
<script setup lang="ts">
import type { BatchCreateReviewItem } from '#/api/pharmacy/prescription-review';
import type {
  OutpatientPrescriptionDrugLineDto,
  OutpatientPrescriptionSummaryRow,
  PageOutpatientPrescriptionViewRequest,
} from '#/api/report/outpatient-prescription-view';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Refresh, Search } from '@element-plus/icons-vue';
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
  ElTag,
} from 'element-plus';

import {
  mapActiveReviewsByPrescriptionNosApi,
  PRESCRIPTION_REVIEW_STATUS,
  revokePrescriptionReviewApi,
} from '#/api/pharmacy/prescription-review';
import {
  loadOutpatientPrescriptionViewPageInitApi,
  pageOutpatientPrescriptionViewApi,
} from '#/api/report/outpatient-prescription-view';
import { useRequest } from '#/hooks/use-request';
import { unionDiagnosisDisplayNames } from '#/utils/diagnosis/union-diagnosis-display-names';
import {
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATERANGE,
  ReportElTable,
  ReportListPageLayout,
  ReportListToolbar,
  ReportPageFooter,
  ReportQueryBar,
  ReportTableScroll,
  resolveReportRefundRowClassName,
} from '#/views/report/shared';
import {
  buildReportDateRangeShortcuts,
  normalizeReportDateRangeYmd,
} from '#/views/report/shared/report-date-range-shortcuts';
import { optionDtosToSelectItems } from '#/views/report/shared/report-format';
import PrescriptionBatchReviewDialog from '#/views/report/prescription-review/components/prescription-batch-review-dialog.vue';

import OutpatientPrescriptionViewDetailDrawer from './components/outpatient-prescription-view-detail-drawer.vue';

defineOptions({ name: 'OutpatientPrescriptionViewReportPage' });

interface QueryState {
  pageNo: number;
  pageSize: number;
  openedDateFrom?: string;
  openedDateTo?: string;
  chargeStatusEnum?: string;
  dispenseStatusEnum?: string;
  infusionFilterCode?: string;
  rxTypeCode?: string;
  medTypeCode?: string;
  contractCategoryCode?: string;
  /** 开单科室组织主键（string 保精度） */
  orderingDepartmentId?: string;
  patientKeyword?: string;
  /** 点评状态 PENDING/REVIEWED */
  reviewStatusEnum?: string;
  /**
   * 是否合理筛选值（下拉 value 为字符串化布尔，提交前转 boolean）。
   */
  reasonableValue?: string;
  /** 药品类型（药典 category_code） */
  medicationCategoryCode?: string;
}

/** 发药完成态 code（可选行 / 批量点评门槛）。 */
const DISPENSE_STATUS_COMPLETED = 'COMPLETED';

const PAGE_TITLE = '门诊处方查看';

const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);
const detailDrawerRef = ref<InstanceType<typeof OutpatientPrescriptionViewDetailDrawer> | null>(
  null,
);
const batchReviewDialogRef = ref<InstanceType<typeof PrescriptionBatchReviewDialog> | null>(null);
const tableData = ref<OutpatientPrescriptionSummaryRow[]>([]);
const selectedRows = ref<OutpatientPrescriptionSummaryRow[]>([]);
const total = ref(0);
const prescriptionCount = ref(0);
const visitPatientCount = ref(0);

const chargeStatusOptions = ref<ApiOptionItem[]>([]);
const dispenseStatusOptions = ref<ApiOptionItem[]>([]);
const rxTypeOptions = ref<ApiOptionItem[]>([]);
const medTypeOptions = ref<ApiOptionItem[]>([]);
const contractCategoryOptions = ref<ApiOptionItem[]>([]);
const infusionFilterOptions = ref<ApiOptionItem[]>([]);
const organizationOptions = ref<ApiOptionItem[]>([]);
const reviewStatusOptions = ref<ApiOptionItem[]>([]);
const reasonableOptions = ref<ApiOptionItem[]>([]);
const medicationCategoryOptions = ref<ApiOptionItem[]>([]);

const queryParams = reactive<QueryState>({
  pageNo: 1,
  pageSize: 50,
});

const tableLoading = computed(
  () => initLoading.value || pageLoading.value || mapActiveLoading.value || revokeLoading.value,
);

/** 是否已点评（逻辑用 status code）。 */
function isReviewed(row: OutpatientPrescriptionSummaryRow) {
  return String(row.reviewStatusEnum ?? '').trim() === PRESCRIPTION_REVIEW_STATUS.REVIEWED;
}

/** 是否已发药完成（可评门槛，禁止用中文文案判断）。 */
function isDispensedCompleted(row: OutpatientPrescriptionSummaryRow) {
  return String(row.dispenseStatusEnum ?? '').trim() === DISPENSE_STATUS_COMPLETED;
}

/**
 * 行是否可选 / 可评：已发药完成且未点评（= 业务「待点评」）。
 * 未发药等不可评行不算待点评。
 */
function isRowSelectable(row: OutpatientPrescriptionSummaryRow) {
  return isDispensedCompleted(row) && !isReviewed(row);
}

/** 不合理点评行：整行 danger 标红。 */
function resolveTableRowClassName({ row }: { row: OutpatientPrescriptionSummaryRow }) {
  return resolveReportRefundRowClassName(row.reasonableFlag === false);
}

/** 方级行键。 */
function rowKey(row: OutpatientPrescriptionSummaryRow) {
  return row.prescriptionNo || String(row.encounterId ?? '');
}

const footerSummaryItems = computed(() => [
  { label: '总处方量', value: prescriptionCount.value, primary: true },
  { label: '就诊人数', value: visitPatientCount.value },
]);

/** 点评弹窗入参（单条或批量勾选，打开时写入）。 */
const reviewDialogItems = ref<BatchCreateReviewItem[]>([]);

const dateRangeShortcuts = buildReportDateRangeShortcuts();

/** 开方日期与 daterange 双向绑定。 */
const openedDateRange = computed({
  get() {
    const start = normalizeReportDateRangeYmd(queryParams.openedDateFrom);
    const end = normalizeReportDateRangeYmd(queryParams.openedDateTo);
    if (start && end) {
      return [start, end] as [string, string];
    }
    return null;
  },
  set(value: [string, string] | null) {
    queryParams.openedDateFrom = value?.[0];
    queryParams.openedDateTo = value?.[1];
  },
});

/** 下拉选项转为 ElSelect 结构。 */
function toSelectOptions(items: ApiOptionItem[]) {
  return items.map((item) => ({
    label: String(item.label ?? ''),
    value: String(item.value ?? ''),
  }));
}

/** 将是否合理下拉字符串转为请求布尔；空则不传。 */
function resolveReasonableFlag(): boolean | undefined {
  const raw = String(queryParams.reasonableValue ?? '').trim();
  if (raw === 'true') {
    return true;
  }
  if (raw === 'false') {
    return false;
  }
  return undefined;
}

/** 开方时间展示。 */
function formatDateTime(value?: string) {
  const text = String(value ?? '').trim();
  if (!text) {
    return '—';
  }
  return text.length >= 19 ? text.slice(0, 19) : text;
}

/** 身份证号前端脱敏：保留前三后四。 */
function maskIdCard(value?: string) {
  const text = String(value ?? '').trim();
  if (!text) {
    return '—';
  }
  if (text.length <= 7) {
    return '***';
  }
  return `${text.slice(0, 3)}***********${text.slice(-4)}`;
}

/** 按处方号聚合本页平铺行为方级摘要（一张方一行）。 */
function buildPrescriptionSummaryRows(
  records: OutpatientPrescriptionDrugLineDto[],
): OutpatientPrescriptionSummaryRow[] {
  const order: string[] = [];
  const byRx = new Map<string, OutpatientPrescriptionSummaryRow>();
  // 同方连续聚合：取首行方头字段，诊断取并集，累计明细行数
  for (const line of records) {
    const rxNo = String(line.prescriptionNo ?? '').trim();
    if (!rxNo) {
      continue;
    }
    const existing = byRx.get(rxNo);
    if (existing) {
      existing.lineCount += 1;
      // 方头诊断：合并本行诊断名到并集（后端通常已写并集，此处防漏）
      const mergedDiagnosis = unionDiagnosisDisplayNames(
        existing.diagnosisName,
        line.diagnosisName,
      );
      if (mergedDiagnosis) {
        existing.diagnosisName = mergedDiagnosis;
      }
      continue;
    }
    byRx.set(rxNo, {
      prescriptionNo: rxNo,
      encounterId: line.encounterId,
      encounterBusNo: line.encounterBusNo,
      patientId: line.patientId,
      patientName: line.patientName,
      idCard: line.idCard,
      patientPhone: line.patientPhone,
      diagnosisName: line.diagnosisName,
      chargeStatusEnum: line.chargeStatusEnum,
      chargeStatusText: line.chargeStatusText,
      dispenseStatusEnum: line.dispenseStatusEnum,
      dispenseStatusText: line.dispenseStatusText,
      orderingPractitionerName: line.orderingPractitionerName,
      orderingDepartmentId: line.orderingDepartmentId,
      orderingDepartmentName: line.orderingDepartmentName,
      reqOpenedTime: line.reqOpenedTime,
      rxTypeCode: line.rxTypeCode,
      rxTypeText: line.rxTypeText,
      medTypeCode: line.medTypeCode,
      medTypeText: line.medTypeText,
      contractCategoryText: line.contractCategoryText,
      infusionPrescriptionFlag: line.infusionPrescriptionFlag,
      lineCount: 1,
      // 点评态由 enrichment 按「已发药待点评 / 已点评 / 不可评」回填，此处不默认待点评
    });
    order.push(rxNo);
  }
  return order.map((key) => byRx.get(key)!);
}

/** 用有效点评映射回填本页方级行；无点评时仅已发药标待点评。 */
async function enrichRowsWithActiveReviews(rows: OutpatientPrescriptionSummaryRow[]) {
  // 本页去重处方号
  const prescriptionNos = [
    ...new Set(
      rows.map((row) => String(row.prescriptionNo ?? '').trim()).filter((no) => no.length > 0),
    ),
  ];
  if (prescriptionNos.length === 0) {
    return;
  }
  const data = await runMapActive({ prescriptionNos });
  const map = data?.reviewsByPrescriptionNo ?? {};
  // 按处方号合并点评字段；无映射则仅已发药写待点评，其余不可评不标待点评
  for (const row of rows) {
    const active = map[row.prescriptionNo];
    if (active) {
      const reviewId = active.id != null ? String(active.id) : undefined;
      row.reviewId = reviewId;
      row.reviewStatusEnum =
        String(active.statusEnum ?? '').trim() || PRESCRIPTION_REVIEW_STATUS.REVIEWED;
      row.reviewStatusText = active.statusText || '已点评';
      row.reasonableFlag = active.reasonableFlag;
      row.reasonableText = active.reasonableText;
      row.reviewReason = active.reason;
      row.reviewRemark = active.remark;
      row.reviewerName = active.reviewerName;
      row.reviewedAt = active.reviewedAt;
      continue;
    }
    // 已发药且无有效点评 → 待点评；未发药等不可评 → 留空（展示 —）
    if (isDispensedCompleted(row)) {
      row.reviewStatusEnum = PRESCRIPTION_REVIEW_STATUS.PENDING;
      row.reviewStatusText = '待点评';
    } else {
      row.reviewStatusEnum = undefined;
      row.reviewStatusText = undefined;
    }
  }
  tableData.value = [...rows];
}

/** 写入方级列表并 enrichment 点评态。 */
async function applyTableRecords(records: OutpatientPrescriptionDrugLineDto[]) {
  const rows = buildPrescriptionSummaryRows(records);
  tableData.value = rows;
  selectedRows.value = [];
  // 筛「待点评」时本页均为已发药待点评，跳过二次 enrichment RTT
  const reviewFilter = String(queryParams.reviewStatusEnum ?? '').trim();
  if (reviewFilter === PRESCRIPTION_REVIEW_STATUS.PENDING) {
    for (const row of rows) {
      row.reviewStatusEnum = PRESCRIPTION_REVIEW_STATUS.PENDING;
      row.reviewStatusText = '待点评';
    }
    tableData.value = [...rows];
    return;
  }
  await enrichRowsWithActiveReviews(rows);
}

/** 组装筛选请求体（不含分页窗口）。 */
function buildFilterBody(): Omit<PageOutpatientPrescriptionViewRequest, 'pageNo' | 'pageSize'> {
  return {
    openedDateFrom: String(queryParams.openedDateFrom ?? ''),
    openedDateTo: String(queryParams.openedDateTo ?? ''),
    chargeStatusEnum: queryParams.chargeStatusEnum || undefined,
    dispenseStatusEnum: queryParams.dispenseStatusEnum || undefined,
    infusionFilterCode: queryParams.infusionFilterCode || undefined,
    rxTypeCode: queryParams.rxTypeCode || undefined,
    medTypeCode: queryParams.medTypeCode || undefined,
    contractCategoryCode: queryParams.contractCategoryCode || undefined,
    orderingDepartmentId: queryParams.orderingDepartmentId || undefined,
    patientKeyword: queryParams.patientKeyword?.trim() || undefined,
    reviewStatusEnum: queryParams.reviewStatusEnum || undefined,
    reasonableFlag: resolveReasonableFlag(),
    medicationCategoryCode: queryParams.medicationCategoryCode || undefined,
  };
}

/** 组装分页请求体。 */
function buildPageRequest(): PageOutpatientPrescriptionViewRequest {
  return {
    ...buildFilterBody(),
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
  };
}

/** 校验必填开方日期。 */
function ensureQueryReady() {
  if (!queryParams.openedDateFrom || !queryParams.openedDateTo) {
    ElMessage.warning('请选择开方日期区间');
    return false;
  }
  return true;
}

const { run: runInit, loading: initLoading } = useRequest(
  loadOutpatientPrescriptionViewPageInitApi,
  {
    manual: true,
    onSuccess: (data) => {
      queryParams.openedDateFrom =
        normalizeReportDateRangeYmd(data?.defaultOpenedDateFrom) || queryParams.openedDateFrom;
      queryParams.openedDateTo =
        normalizeReportDateRangeYmd(data?.defaultOpenedDateTo) || queryParams.openedDateTo;
      chargeStatusOptions.value = optionDtosToSelectItems(data?.chargeStatusOptions);
      dispenseStatusOptions.value = optionDtosToSelectItems(data?.dispenseStatusOptions);
      rxTypeOptions.value = optionDtosToSelectItems(data?.rxTypeOptions);
      medTypeOptions.value = optionDtosToSelectItems(data?.medTypeOptions);
      contractCategoryOptions.value = optionDtosToSelectItems(data?.contractCategoryOptions);
      infusionFilterOptions.value = optionDtosToSelectItems(data?.infusionFilterOptions);
      organizationOptions.value = optionDtosToSelectItems(data?.organizationOptions);
      reviewStatusOptions.value = optionDtosToSelectItems(data?.reviewStatusOptions);
      reasonableOptions.value = optionDtosToSelectItems(data?.reasonableOptions);
      medicationCategoryOptions.value = optionDtosToSelectItems(data?.medicationCategoryOptions);
    },
  },
);

const { run: runPage, loading: pageLoading } = useRequest(pageOutpatientPrescriptionViewApi, {
  manual: true,
});

const { run: runMapActive, loading: mapActiveLoading } = useRequest(
  mapActiveReviewsByPrescriptionNosApi,
  { manual: true },
);

const { run: runRevoke, loading: revokeLoading } = useRequest(revokePrescriptionReviewApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('已撤销点评');
    void getList();
  },
});

/** 拉取平铺明细后聚合成方级列表并 enrichment。 */
async function getList() {
  if (!ensureQueryReady()) {
    return;
  }
  const data = await runPage(buildPageRequest());
  await applyTableRecords(data?.records ?? []);
  // 分页 total = 明细行条数（与列表聚合后的「方」条数可不同）
  total.value = Number(data?.total ?? 0);
  // 页脚总处方量 = COUNT DISTINCT prescription_no，禁止与分页 total 混用
  prescriptionCount.value = Number(data?.prescriptionCount ?? 0);
  visitPatientCount.value = Number(data?.visitPatientCount ?? 0);
  await nextTick();
  tableScrollRef.value?.updateTableHeight();
}

/** 查询回到第一页。 */
function handleQuery() {
  queryParams.pageNo = 1;
  void getList();
}

/** 重置筛选并重新初始化默认日期后查询。 */
async function handleReset() {
  queryParams.chargeStatusEnum = undefined;
  queryParams.dispenseStatusEnum = undefined;
  queryParams.infusionFilterCode = undefined;
  queryParams.rxTypeCode = undefined;
  queryParams.medTypeCode = undefined;
  queryParams.contractCategoryCode = undefined;
  queryParams.orderingDepartmentId = undefined;
  queryParams.patientKeyword = undefined;
  queryParams.reviewStatusEnum = undefined;
  queryParams.reasonableValue = undefined;
  queryParams.medicationCategoryCode = undefined;
  queryParams.pageNo = 1;
  queryParams.pageSize = 50;
  prescriptionCount.value = 0;
  visitPatientCount.value = 0;
  selectedRows.value = [];
  await runInit();
  await getList();
}

/** 切换每页条数（按明细行条数分页）。 */
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

/** 表格多选变更。 */
function handleSelectionChange(rows: OutpatientPrescriptionSummaryRow[]) {
  selectedRows.value = rows;
}

/** 将可点评行转为弹窗入参。 */
function toReviewDialogItem(row: OutpatientPrescriptionSummaryRow): BatchCreateReviewItem {
  return {
    prescriptionNo: row.prescriptionNo,
    busNo: row.encounterBusNo || undefined,
  };
}

/** 打开批量点评弹窗（勾选行）。 */
function handleOpenBatchReview() {
  // 仅保留已发药且待点评的勾选行
  const items = selectedRows.value
    .filter((row) => isRowSelectable(row))
    .map((row) => toReviewDialogItem(row));
  if (items.length === 0) {
    ElMessage.warning('请先勾选已发药且待点评的处方');
    return;
  }
  reviewDialogItems.value = items;
  batchReviewDialogRef.value?.open();
}

/** 行内单条点评：复用批量弹窗，入参仅当前行。 */
function handleOpenSingleReview(row: OutpatientPrescriptionSummaryRow) {
  if (!isRowSelectable(row)) {
    ElMessage.warning('仅已发药且待点评的处方可点评');
    return;
  }
  reviewDialogItems.value = [toReviewDialogItem(row)];
  batchReviewDialogRef.value?.open();
}

/** 点评成功后刷新列表。 */
function handleBatchReviewSuccess() {
  selectedRows.value = [];
  reviewDialogItems.value = [];
  void getList();
}

/** 点击行打开详情（整方明细 + 打印）。 */
function handleOpenDetail(row: OutpatientPrescriptionSummaryRow) {
  const from = String(queryParams.openedDateFrom ?? '');
  const to = String(queryParams.openedDateTo ?? '');
  if (!from || !to) {
    ElMessage.warning('请选择开方日期区间');
    return;
  }
  void detailDrawerRef.value?.open({
    summary: row,
    openedDateFrom: from,
    openedDateTo: to,
  });
}

/** 撤销当前有效点评。 */
async function handleRevoke(row: OutpatientPrescriptionSummaryRow) {
  if (!isReviewed(row)) {
    return;
  }
  const id = String(row.reviewId ?? '').trim();
  const rxNo = String(row.prescriptionNo ?? '').trim();
  if (!id && !rxNo) {
    ElMessage.warning('缺少点评标识');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认撤销处方 ${rxNo || id} 的点评？撤销后可重新点评。`,
      '撤销点评',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await runRevoke(id ? { id } : { prescriptionNo: rxNo });
}

onMounted(async () => {
  await runInit();
  await getList();
});
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <ReportListPageLayout>
      <template #query>
        <ReportQueryBar :model="queryParams as Record<string, unknown>">
          <template #fields>
            <ElFormItem label="收费状态">
              <ElSelect
                v-model="queryParams.chargeStatusEnum"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(chargeStatusOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="发药状态">
              <ElSelect
                v-model="queryParams.dispenseStatusEnum"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(dispenseStatusOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="含输液">
              <ElSelect
                v-model="queryParams.infusionFilterCode"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(infusionFilterOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="开方日期">
              <ElDatePicker
                v-model="openedDateRange"
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
            <ElFormItem label="处方类别">
              <ElSelect
                v-model="queryParams.rxTypeCode"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(rxTypeOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="医保类别">
              <ElSelect
                v-model="queryParams.medTypeCode"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(medTypeOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="费用性质">
              <ElSelect
                v-model="queryParams.contractCategoryCode"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(contractCategoryOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="科室">
              <ElSelect
                v-model="queryParams.orderingDepartmentId"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(organizationOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="点评状态">
              <ElSelect
                v-model="queryParams.reviewStatusEnum"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(reviewStatusOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="是否合理">
              <ElSelect
                v-model="queryParams.reasonableValue"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(reasonableOptions)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="药品类型">
              <ElSelect
                v-model="queryParams.medicationCategoryCode"
                clearable
                filterable
                placeholder="全部"
                :class="REPORT_QUERY_CONTROL"
                @change="handleQuery"
              >
                <ElOption
                  v-for="item in toSelectOptions(medicationCategoryOptions)"
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
                placeholder="姓名/身份证/门诊号"
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
        <ReportListToolbar :title="`${PAGE_TITLE} · 处方列表`" :total="total">
          <template #actions>
            <ElButton type="primary" :loading="tableLoading" @click="handleOpenBatchReview">
              批量点评
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
              :row-key="rowKey"
              :row-class-name="resolveTableRowClassName"
              class="op-rx-summary-table"
              @selection-change="handleSelectionChange"
              @row-click="handleOpenDetail"
            >
              <ElTableColumn
                type="selection"
                width="48"
                align="center"
                header-align="center"
                :selectable="isRowSelectable"
              />
              <ElTableColumn
                prop="prescriptionNo"
                label="处方号"
                min-width="132"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="encounterBusNo"
                label="门诊号"
                min-width="132"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="patientName"
                label="患者"
                min-width="72"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="idCard"
                label="身份证号"
                min-width="168"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ maskIdCard(row.idCard) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="patientPhone"
                label="电话号"
                min-width="110"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.patientPhone || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="diagnosisName"
                label="诊断"
                min-width="200"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="chargeStatusText"
                label="收费状态"
                min-width="88"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  <ElTag v-if="row.chargeStatusText" effect="light">
                    {{ row.chargeStatusText }}
                  </ElTag>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="dispenseStatusText"
                label="发药状态"
                min-width="88"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  <ElTag v-if="row.dispenseStatusText" type="info" effect="light">
                    {{ row.dispenseStatusText }}
                  </ElTag>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="reviewStatusText"
                label="点评状态"
                min-width="88"
                align="center"
                header-align="center"
              >
                <template #default="{ row }">
                  <ElTag
                    v-if="row.reviewStatusText"
                    :type="isReviewed(row) ? 'success' : 'info'"
                    effect="light"
                  >
                    {{ row.reviewStatusText }}
                  </ElTag>
                  <span v-else>—</span>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="reasonableText"
                label="点评结果"
                min-width="88"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.reasonableText || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="orderingDepartmentName"
                label="开单科室"
                min-width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.orderingDepartmentName || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="orderingPractitionerName"
                label="开单医生"
                min-width="88"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="reqOpenedTime"
                label="开方时间"
                min-width="154"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ formatDateTime(row.reqOpenedTime) }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="rxTypeText"
                label="处方类别"
                min-width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              />
              <ElTableColumn
                prop="medTypeText"
                label="医保类别"
                min-width="100"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.medTypeText || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="contractCategoryText"
                label="费用性质"
                min-width="88"
                align="center"
                header-align="center"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.contractCategoryText || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="操作"
                min-width="168"
                align="center"
                header-align="center"
                fixed="right"
              >
                <template #default="{ row }">
                  <ElButton link type="primary" @click.stop="handleOpenDetail(row)">
                    详情
                  </ElButton>
                  <ElButton
                    v-if="isRowSelectable(row)"
                    link
                    type="primary"
                    @click.stop="handleOpenSingleReview(row)"
                  >
                    点评
                  </ElButton>
                  <ElButton
                    v-if="isReviewed(row)"
                    link
                    type="danger"
                    @click.stop="handleRevoke(row)"
                  >
                    撤销点评
                  </ElButton>
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
          @size-change="handlePageSizeChange"
          @current-change="handleCurrentPageChange"
        />
      </template>
    </ReportListPageLayout>

    <OutpatientPrescriptionViewDetailDrawer ref="detailDrawerRef" />
    <PrescriptionBatchReviewDialog
      ref="batchReviewDialogRef"
      :items="reviewDialogItems"
      @success="handleBatchReviewSuccess"
    />
  </Page>
</template>

<style scoped>
:deep(.op-rx-summary-table .el-table__body tr) {
  cursor: pointer;
}
</style>
