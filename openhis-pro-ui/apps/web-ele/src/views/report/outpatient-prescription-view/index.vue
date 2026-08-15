<!--
  门诊处方查看：筛选 + 方级列表（一张方一行）+ 详情抽屉看药品明细；打印与药房处方一致。
  主类型：T2 纯展示查询列表；附加：详情 Drawer / 打印。
-->
<script setup lang="ts">
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
  ElOption,
  ElSelect,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  loadOutpatientPrescriptionViewPageInitApi,
  pageOutpatientPrescriptionViewApi,
} from '#/api/report/outpatient-prescription-view';
import { useRequest } from '#/hooks/use-request';
import {
  REPORT_QUERY_CONTROL,
  REPORT_QUERY_CONTROL_DATERANGE,
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
import { optionDtosToSelectItems } from '#/views/report/shared/report-format';
import { unionDiagnosisDisplayNames } from '#/utils/diagnosis/union-diagnosis-display-names';

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
}

const PAGE_TITLE = '门诊处方查看';

const tableScrollRef = ref<InstanceType<typeof ReportTableScroll> | null>(null);
const detailDrawerRef = ref<InstanceType<typeof OutpatientPrescriptionViewDetailDrawer> | null>(
  null,
);
const tableData = ref<OutpatientPrescriptionSummaryRow[]>([]);
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

const queryParams = reactive<QueryState>({
  pageNo: 1,
  pageSize: 50,
});

const tableLoading = computed(() => initLoading.value || pageLoading.value);

const footerSummaryItems = computed(() => [
  { label: '总处方量', value: prescriptionCount.value, primary: true },
  { label: '就诊人数', value: visitPatientCount.value },
]);

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

/** 开方时间展示。 */
function formatDateTime(value?: string) {
  const text = String(value ?? '').trim();
  if (!text) {
    return '—';
  }
  return text.length >= 19 ? text.slice(0, 19) : text;
}

/** 方级行键。 */
function rowKey(row: OutpatientPrescriptionSummaryRow) {
  return row.prescriptionNo || String(row.encounterId ?? '');
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
    });
    order.push(rxNo);
  }
  return order.map((key) => byRx.get(key)!);
}

/** 写入方级列表。 */
function applyTableRecords(records: OutpatientPrescriptionDrugLineDto[]) {
  tableData.value = buildPrescriptionSummaryRows(records);
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
    },
  },
);

const { run: runPage, loading: pageLoading } = useRequest(pageOutpatientPrescriptionViewApi, {
  manual: true,
  onSuccess: (data) => {
    applyTableRecords(data?.records ?? []);
    total.value = Number(data?.total ?? 0);
    // 页脚汇总由后端同条件 COUNT DISTINCT 带回，禁止二次拉全量明细
    prescriptionCount.value = Number(data?.prescriptionCount ?? 0);
    visitPatientCount.value = Number(data?.visitPatientCount ?? 0);
  },
});

/** 拉取平铺明细后聚合成方级列表。 */
async function getList() {
  if (!ensureQueryReady()) {
    return;
  }
  await runPage(buildPageRequest());
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
  queryParams.pageNo = 1;
  queryParams.pageSize = 50;
  prescriptionCount.value = 0;
  visitPatientCount.value = 0;
  await runInit();
  await getList();
}

/** 切换每页条数（后端仍按明细行分页）。 */
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
            <ElFormItem label="患者">
              <ElInput
                v-model="queryParams.patientKeyword"
                clearable
                placeholder="姓名等关键字"
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
        <ReportListToolbar :title="`${PAGE_TITLE} · 处方列表`" :total="tableData.length" />
      </template>

      <template #table>
        <ReportTableScroll ref="tableScrollRef">
          <template #default="{ tableHeight }">
            <ReportElTable
              :loading="tableLoading"
              :data="tableData"
              :height="tableHeight"
              :row-key="rowKey"
              class="op-rx-summary-table"
              @row-click="handleOpenDetail"
            >
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
                min-width="72"
                align="center"
                header-align="center"
                fixed="right"
              >
                <template #default="{ row }">
                  <ElButton link type="primary" @click.stop="handleOpenDetail(row)">
                    详情
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
  </Page>
</template>

<style scoped>
:deep(.op-rx-summary-table .el-table__body tr) {
  cursor: pointer;
}
</style>
