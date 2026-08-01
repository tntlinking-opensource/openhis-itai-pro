<script setup lang="ts">
import type {
  ClearAmountSummary,
  ClearStatus3205ARow,
  LocalClearApplyRecord,
  LocalReconDailyRecord,
  LocalReconDetailRecord,
} from '#/chs/api';
import type { ChargeType } from '#/api/core/common';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElCol,
  ElDatePicker,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRow,
  ElSelect,
  ElTabPane,
  ElTabs,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  applyJilinClearApi,
  listLocalClearAppliesApi,
  listLocalReconDailyRecordsApi,
  listLocalReconDetailRecordsApi,
  queryClearApplyStatusApi,
  revokeJilinClearApi,
  startWorkInjuryReconApi,
  mergeClearAmountSummaries,
  summarizeClearAmountsApi,
} from '#/chs/api';
import {
  formatClrStas,
  formatClrType,
  formatInsutype,
  formatStmtRslt,
  isStmtBalanced,
  YB_CLR_STAS_OPTIONS,
  YB_CLR_TYPE_OPTIONS,
} from '#/chs/constants/reconciliation';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';
import { useRequest } from '#/hooks/use-request';
import { continueWorkInjurySession } from '#/chs/work-injury/run-work-injury-phase';
import { httpWorkInjuryModuleCapability } from '#/chs/work-injury/work-injury.http';
import { isWorkInjuryPluginOnline } from '#/chs/work-injury/work-injury-plugin';
import { isWorkInjuryContractCategory } from '#/views/outpatient/charge/register/services/charge-type-category';
import AppDialog from '#/components/app-dialog.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import DailyLedgerList from '#/chs/reconciliation/components/daily-ledger-list.vue';
import {
  formatClearOrgCode,
  formatClearOrgOptionLabel,
} from '#/chs/reconciliation/format-clear-org';

defineOptions({ name: 'ChsReconciliationPage' });

const activeTab = ref<'clear' | 'daily' | 'workInjury'>('daily');
const contractBusNo = ref<string>();
const chargeTypeOptions = ref<ChargeType[]>([]);

const dateFieldProps = {
  type: 'daterange' as const,
  valueFormat: 'YYYY-MM-DD',
  rangeSeparator: '至',
  startPlaceholder: '开始',
  endPlaceholder: '结束',
  clearable: true,
};

function formatMoney(value?: number | string | null): string {
  if (value == null || value === '') return '—';
  const n = Number(value);
  return Number.isFinite(n) ? n.toFixed(2) : '—';
}

/** 取记录对账日期（开始日优先） */
function recordReconDate(row: LocalReconDailyRecord): string {
  return (row.stmtBegndate || row.stmtEnddate || '').slice(0, 10);
}

/** 记录是否总账不平（按 stmtRslt code） */
function isRecordUnbalanced(row: LocalReconDailyRecord): boolean {
  return !isStmtBalanced(row.stmtRslt);
}

const detailDrawerVisible = ref(false);
const detailDrawerTitle = ref('明细核对');
const detailDrawerMeta = ref('');
const detailDrawerRecords = ref<LocalReconDetailRecord[]>([]);

/** 从本地日账打开关联的明细对账流水（真实 listLocal） */
function openDetailDrawer(row: LocalReconDailyRecord) {
  const dateText = recordReconDate(row) || '—';
  detailDrawerMeta.value = [
    formatInsutype(row.insutype),
    formatClrType(row.clrType),
    dateText,
    row.stmtRsltDscr || '',
  ]
    .filter(Boolean)
    .join(' · ');
  detailDrawerTitle.value = isRecordUnbalanced(row) ? '明细核对（有差异）' : '明细核对';
  // 按对账日 + 清算类别匹配已落库的 3202 流水
  detailDrawerRecords.value = detailLocalRecords.value.filter((item) => {
    const begin = (item.stmtBegndate || '').slice(0, 10);
    const end = (item.stmtEnddate || begin).slice(0, 10);
    if (!begin || dateText === '—') return false;
    if (begin > dateText || end < dateText) return false;
    if (row.clrType && item.clrType && item.clrType !== row.clrType) return false;
    return true;
  });
  detailDrawerVisible.value = true;
}

// —— 日对账本地记录 ——
const dailyLocalRecords = ref<LocalReconDailyRecord[]>([]);
const detailLocalRecords = ref<LocalReconDetailRecord[]>([]);

function pad2(n: number) {
  return String(n).padStart(2, '0');
}

function formatYmd(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

/** 默认清算区间：上个月 1 日～月末 */
function defaultLastMonthRange(): [string, string] {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const end = new Date(now.getFullYear(), now.getMonth(), 0);
  return [formatYmd(start), formatYmd(end)];
}

/** 默认清算月：上个月 yyyyMM（状态查询用） */
function defaultLastMonthYm(): string {
  const now = new Date();
  const d = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  return `${d.getFullYear()}${pad2(d.getMonth() + 1)}`;
}

// —— 月清算 ——
const clearDateRange = ref<[string, string]>(defaultLastMonthRange());
const clearClrType = ref('11');
const clearSummary = ref<ClearAmountSummary | null>(null);
const clearStatusRows = ref<ClearStatus3205ARow[]>([]);
const clearStatusStas = ref('10');
const clearStatusYm = ref(defaultLastMonthYm());
const clearLocalRecords = ref<LocalClearApplyRecord[]>([]);
const clearAmountForm = ref({
  medfeeSumamt: undefined as number | undefined,
  medSumfee: undefined as number | undefined,
  fundAppySum: undefined as number | undefined,
  cashPayamt: undefined as number | undefined,
  acctPay: undefined as number | undefined,
});

/** 快捷切回上个月区间 */
function setClearDateLastMonth() {
  clearDateRange.value = defaultLastMonthRange();
  clearStatusYm.value = defaultLastMonthYm();
}

/** 从清算机构（契约 busNo）解析医保清算经办机构编码 */
function requireClrOptins(): string {
  const code = formatClearOrgCode(contractBusNo.value);
  if (!code) {
    throw new Error('请选择清算机构');
  }
  return code;
}

/** 清算状态 Tag 类型 */
function clearStasTagType(stas?: string): 'info' | 'success' | 'warning' {
  if (stas === '10') return 'warning';
  if (stas === '20') return 'success';
  return 'info';
}

interface WiChargeTypeOption {
  contractId: string;
  label: string;
  busNo: string;
}

const wiModuleEnabled = ref(false);
const wiContractOptions = ref<WiChargeTypeOption[]>([]);
const wiContractId = ref<string>();
const wiDateRange = ref<[string, string]>();
const wiSocialPoolCode = ref('220100');
const wiMonth = ref('');
const wiRunning = ref(false);
const wiLastSummary = ref<string>();

function filterWorkInjuryChargeTypes(
  items: Array<{ value?: string; label?: string; busNo?: string; categoryEnum?: string }>,
): WiChargeTypeOption[] {
  return items
    .filter(
      (item) =>
        Boolean(item.value?.trim()) &&
        Boolean(item.busNo?.trim()) &&
        (isWorkInjuryContractCategory(item.categoryEnum) || item.busNo!.trim().endsWith('-GS')),
    )
    .map((item) => ({
      contractId: String(item.value).trim(),
      label: (item.label ?? item.busNo ?? item.value ?? '').trim(),
      busNo: (item.busNo ?? '').trim(),
    }));
}

async function loadWorkInjuryReconInit() {
  try {
    const cap = await httpWorkInjuryModuleCapability();
    wiModuleEnabled.value = cap?.enabled === true;
  } catch {
    wiModuleEnabled.value = false;
  }
  if (!wiModuleEnabled.value) {
    wiContractOptions.value = [];
    if (activeTab.value === 'workInjury') {
      activeTab.value = 'daily';
    }
    return;
  }
  const list = await getChargeTypeListApi();
  wiContractOptions.value = filterWorkInjuryChargeTypes(list);
}

async function runWorkInjuryRecon() {
  const cid = wiContractId.value?.trim();
  if (!cid) {
    ElMessage.warning('请选择工伤费用性质');
    return;
  }
  if (!(await isWorkInjuryPluginOnline())) {
    ElMessage.warning('请启动 OpenHIS.Win 并启用工伤模块');
    return;
  }
  const formContext: Record<string, unknown> = {
    socialPoolCode: wiSocialPoolCode.value.trim() || '220100',
    month: wiMonth.value.trim(),
  };
  if (wiDateRange.value?.length === 2) {
    formContext.stmtBegindate = wiDateRange.value[0]!.replaceAll('-', '');
    formContext.stmtEnddate = wiDateRange.value[1]!.replaceAll('-', '');
  }
  wiRunning.value = true;
  wiLastSummary.value = undefined;
  try {
    const prep = await startWorkInjuryReconApi({ contractId: cid, formContext });
    const result = await continueWorkInjurySession(prep, 'RECON', {
      contractId: cid,
      context: formContext,
    });
    wiLastSummary.value = result.summary ?? '对账 invoke 链已完成（1320→1321→1322）';
    ElMessage.success(wiLastSummary.value);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '工伤对账失败');
  } finally {
    wiRunning.value = false;
  }
}

function requireDateRange(range?: [string, string] | null, label = '日期范围'): [string, string] {
  if (!range?.[0] || !range?.[1]) {
    throw new Error(`请选择${label}`);
  }
  return range;
}

function applySummaryToClearForm(summary: ClearAmountSummary) {
  clearAmountForm.value = {
    medfeeSumamt: Number(summary.medfeeSumamt ?? 0),
    medSumfee: Number(summary.medSumfee ?? summary.medfeeSumamt ?? 0),
    fundAppySum: Number(summary.fundAppySum ?? 0),
    cashPayamt: Number(summary.cashPayamt ?? 0),
    acctPay: Number(summary.acctPay ?? 0),
  };
}

function requireContractBusNo(): string {
  const busNo = contractBusNo.value?.trim();
  if (!busNo) {
    throw new Error('请先选择清算机构');
  }
  return busNo;
}

async function withSignIn<T>(runner: () => Promise<T>): Promise<T> {
  await ensureInsuranceSignIn(requireContractBusNo());
  return runner();
}

const { run: loadChargeTypes, loading: loadingChargeTypes } = useRequest(async () => {
  const list = await getChargeTypeListApi();
  const eligible = filterInsuranceChargeTypes(list);
  chargeTypeOptions.value = eligible;
  contractBusNo.value = resolveDefaultInsuranceBusNo(eligible, contractBusNo.value);
});

const { run: loadDailyLocalRecords, loading: loadingDailyRecords } = useRequest(async () => {
  dailyLocalRecords.value = await listLocalReconDailyRecordsApi({
    contractBusNo: requireContractBusNo(),
  });
});

const { run: loadDetailLocalRecords } = useRequest(async () => {
  detailLocalRecords.value = await listLocalReconDetailRecordsApi({
    contractBusNo: requireContractBusNo(),
  });
});

const { run: loadClearLocalRecords, loading: loadingClearRecords } = useRequest(async () => {
  clearLocalRecords.value = await listLocalClearAppliesApi({
    contractBusNo: requireContractBusNo(),
  });
});

const { run: summarizeClearAmounts, loading: summarizingClear } = useRequest(async () => {
  const [begndate, enddate] = requireDateRange(clearDateRange.value, '清算月份');
  const rows = await withSignIn(() =>
    summarizeClearAmountsApi({ contractBusNo: requireContractBusNo(), begndate, enddate }),
  );
  const summary = mergeClearAmountSummaries(rows);
  clearSummary.value = summary;
  applySummaryToClearForm(summary);
  ElMessage.success(
    `已汇总本地结算 ${summary.setlCount ?? 0} 笔（${rows.length} 个险种/业务类型）`,
  );
});

const { run: applyClear, loading: applyingClear } = useRequest(async () => {
  const [begndate, enddate] = requireDateRange(clearDateRange.value, '清算月份');
  const clrOptins = requireClrOptins();
  if (clearAmountForm.value.medfeeSumamt == null) {
    await summarizeClearAmounts();
  }
  const result = await withSignIn(() =>
    applyJilinClearApi({
      contractBusNo: requireContractBusNo(),
      clrType: clearClrType.value,
      clrOptins,
      begndate,
      enddate,
      ...clearAmountForm.value,
    }),
  );
  await ElMessageBox.alert(`清算申请已提交，事件 ID：${result.clrAppyEvtId ?? '—'}`, '清算申请');
  await loadClearLocalRecords();
});

const { run: queryClearStatus, loading: queryingStatus } = useRequest(async () => {
  const clrOptins = requireClrOptins();
  if (['20', '50'].includes(clearStatusStas.value) && !clearStatusYm.value.trim()) {
    throw new Error('查询已受理/已清算状态需填写清算月份（yyyyMM）');
  }
  clearStatusRows.value = await withSignIn(() =>
    queryClearApplyStatusApi({
      contractBusNo: requireContractBusNo(),
      clrOptins,
      clrStas: clearStatusStas.value,
      clrYm: clearStatusYm.value.trim() || undefined,
    }),
  );
  ElMessage.success(`查询到 ${clearStatusRows.value.length} 条清算状态`);
});

async function handleRevokeClear(row: ClearStatus3205ARow | LocalClearApplyRecord) {
  const evtId = row.clrAppyEvtId?.trim();
  const optins = (row.clrOptins ?? formatClearOrgCode(contractBusNo.value))?.trim();
  if (!evtId || !optins) {
    ElMessage.warning('缺少清算事件 ID 或清算机构');
    return;
  }
  await ElMessageBox.confirm(`确认撤销清算申请 ${evtId}？`, '撤销清算', { type: 'warning' });
  await withSignIn(() =>
    revokeJilinClearApi({
      contractBusNo: requireContractBusNo(),
      clrAppyEvtId: evtId,
      clrOptins: optins,
    }),
  );
  ElMessage.success('撤销成功');
  await loadClearLocalRecords();
}

/** 日对账契约变更后刷新本地对账记录 */
function onDailyContractChange() {
  if (!contractBusNo.value) return;
  return Promise.all([loadDailyLocalRecords(), loadDetailLocalRecords()]);
}

/** 月清算机构变更后刷新本地清算记录 */
function onClearContractChange() {
  if (!contractBusNo.value) return;
  return loadClearLocalRecords();
}

onMounted(async () => {
  await loadChargeTypes();
  await loadWorkInjuryReconInit();
  if (contractBusNo.value) {
    await Promise.all([loadDailyLocalRecords(), loadDetailLocalRecords(), loadClearLocalRecords()]);
  }
});
</script>

<template>
  <Page auto-content-height content-class="recon-page">
    <div class="recon-page__body flex flex-col gap-3">
      <ElTabs v-model="activeTab" class="recon-tabs min-h-0 flex-1">
        <!-- ========== 日对账 ========== -->
        <ElTabPane label="日对账" name="daily" lazy>
          <ElRow :gutter="12">
            <ElCol :xs="24" :lg="15">
              <div class="mb-3">
                <SectionTitle title="对总账" class="recon-section-title mb-2" />
                <div class="recon-panel">
                  <DailyLedgerList
                    v-model:contract-bus-no="contractBusNo"
                    :charge-type-options="chargeTypeOptions"
                    :loading-charge-types="loadingChargeTypes"
                    @contract-change="onDailyContractChange"
                    @reconciled="onDailyContractChange"
                  />
                </div>
              </div>
            </ElCol>

            <ElCol :xs="24" :lg="9">
              <div class="mb-3">
                <SectionTitle title="本地记录" class="recon-section-title mb-2" />
                <div v-loading="loadingDailyRecords" class="recon-panel recon-local-records">
                  <ElEmpty
                    v-if="dailyLocalRecords.length === 0"
                    description="暂无对账记录"
                    :image-size="64"
                  />
                  <div v-else class="flex flex-col gap-2">
                    <div
                      v-for="row in dailyLocalRecords"
                      :key="String(row.id ?? `${row.stmtBegndate}-${row.insutype}`)"
                      class="recon-local-record-item"
                    >
                      <div class="flex items-start justify-between gap-2">
                        <div class="min-w-0 flex-1">
                          <div
                            class="text-sm font-medium"
                            style="color: var(--el-text-color-primary)"
                          >
                            {{ row.stmtBegndate || '—' }}
                            <span v-if="row.stmtEnddate && row.stmtEnddate !== row.stmtBegndate">
                              至 {{ row.stmtEnddate }}
                            </span>
                          </div>
                          <div class="mt-0.5 text-sm" style="color: var(--el-text-color-regular)">
                            {{ formatInsutype(row.insutype) }}
                            <span style="color: var(--el-text-color-secondary)">
                              · {{ formatClrType(row.clrType) }}
                            </span>
                          </div>
                        </div>
                        <ElTag
                          :type="isRecordUnbalanced(row) ? 'danger' : 'success'"
                          size="small"
                          effect="light"
                        >
                          {{ formatStmtRslt(row.stmtRslt) }}
                        </ElTag>
                      </div>
                      <div class="mt-1 text-xs" style="color: var(--el-text-color-secondary)">
                        医疗费 {{ formatMoney(row.medfeeSumamt) }}
                      </div>
                      <div
                        v-if="row.stmtRsltDscr"
                        class="mt-1.5 text-xs leading-relaxed"
                        style="color: var(--el-color-danger)"
                      >
                        {{ row.stmtRsltDscr }}
                      </div>
                      <div v-if="isRecordUnbalanced(row)" class="mt-1.5 flex justify-end">
                        <ElButton link type="primary" @click="openDetailDrawer(row)">
                          查看明细流水
                        </ElButton>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </ElCol>
          </ElRow>
        </ElTabPane>

        <!-- ========== 月清算 ========== -->
        <ElTabPane label="月清算" name="clear" lazy>
          <ElRow :gutter="12">
            <ElCol :xs="24" :lg="15">
              <div class="mb-3">
                <SectionTitle title="清算申请" class="recon-section-title mb-2" />
                <div class="recon-panel">
                  <ElForm inline label-width="80px" class="recon-form recon-clear-filters mb-3">
                    <ElFormItem label="清算机构" required>
                      <ElSelect
                        v-model="contractBusNo"
                        class="recon-filter-control recon-filter-control--org"
                        filterable
                        placeholder="请选择清算机构"
                        :loading="loadingChargeTypes"
                        @change="onClearContractChange"
                      >
                        <ElOption
                          v-for="item in chargeTypeOptions"
                          :key="item.busNo"
                          :label="formatClearOrgOptionLabel(item.label, item.busNo)"
                          :value="item.busNo"
                        />
                      </ElSelect>
                    </ElFormItem>
                    <ElFormItem label="业务类型" required>
                      <ElSelect
                        v-model="clearClrType"
                        class="recon-filter-control recon-filter-control--type"
                      >
                        <ElOption
                          v-for="item in YB_CLR_TYPE_OPTIONS"
                          :key="item.value"
                          :label="item.label"
                          :value="item.value"
                        />
                      </ElSelect>
                    </ElFormItem>
                    <ElFormItem label="清算区间" required>
                      <div class="flex items-center gap-2">
                        <ElDatePicker
                          v-model="clearDateRange"
                          v-bind="dateFieldProps"
                          class="recon-filter-control recon-filter-control--range"
                        />
                        <ElButton @click="setClearDateLastMonth">上月</ElButton>
                      </div>
                    </ElFormItem>
                  </ElForm>

                  <ElForm label-width="108px" class="recon-form">
                    <div class="recon-amount-panel mb-3">
                      <div
                        class="mb-2 text-sm font-medium"
                        style="color: var(--el-text-color-primary)"
                      >
                        清算金额
                      </div>
                      <ElRow :gutter="12">
                        <ElCol :xs="24" :sm="12">
                          <ElFormItem label="医疗费总额" class="!mb-2">
                            <ElInputNumber
                              v-model="clearAmountForm.medfeeSumamt"
                              :min="0"
                              :precision="2"
                              controls-position="right"
                              class="!w-full"
                            />
                          </ElFormItem>
                        </ElCol>
                        <ElCol :xs="24" :sm="12">
                          <ElFormItem label="认可费用" class="!mb-2">
                            <ElInputNumber
                              v-model="clearAmountForm.medSumfee"
                              :min="0"
                              :precision="2"
                              controls-position="right"
                              class="!w-full"
                            />
                          </ElFormItem>
                        </ElCol>
                        <ElCol :xs="24" :sm="12">
                          <ElFormItem label="基金申报" class="!mb-2">
                            <ElInputNumber
                              v-model="clearAmountForm.fundAppySum"
                              :min="0"
                              :precision="2"
                              controls-position="right"
                              class="!w-full"
                            />
                          </ElFormItem>
                        </ElCol>
                        <ElCol :xs="24" :sm="12">
                          <ElFormItem label="现金支付" class="!mb-2">
                            <ElInputNumber
                              v-model="clearAmountForm.cashPayamt"
                              :min="0"
                              :precision="2"
                              controls-position="right"
                              class="!w-full"
                            />
                          </ElFormItem>
                        </ElCol>
                        <ElCol :xs="24" :sm="12">
                          <ElFormItem label="个账支出" class="!mb-2">
                            <ElInputNumber
                              v-model="clearAmountForm.acctPay"
                              :min="0"
                              :precision="2"
                              controls-position="right"
                              class="!w-full"
                            />
                          </ElFormItem>
                        </ElCol>
                      </ElRow>
                    </div>

                    <ElFormItem label=" ">
                      <div class="flex flex-wrap gap-2">
                        <ElButton :loading="summarizingClear" @click="summarizeClearAmounts">
                          汇总本地金额
                        </ElButton>
                        <ElButton
                          type="primary"
                          :loading="applyingClear || summarizingClear"
                          @click="applyClear"
                        >
                          发起清算
                        </ElButton>
                      </div>
                    </ElFormItem>
                  </ElForm>
                </div>
              </div>

              <div class="mb-3">
                <SectionTitle title="清算撤销" class="recon-section-title mb-2" />
                <div class="recon-panel">
                  <ElForm inline label-width="72px" class="mb-2">
                    <ElFormItem label="状态">
                      <ElSelect v-model="clearStatusStas" class="!w-32">
                        <ElOption
                          v-for="item in YB_CLR_STAS_OPTIONS"
                          :key="item.value"
                          :label="item.label"
                          :value="item.value"
                        />
                      </ElSelect>
                    </ElFormItem>
                    <ElFormItem label="清算月">
                      <ElDatePicker
                        v-model="clearStatusYm"
                        type="month"
                        value-format="YYYYMM"
                        placeholder="选择月份"
                        class="!w-36"
                        clearable
                      />
                    </ElFormItem>
                    <ElFormItem>
                      <ElButton :loading="queryingStatus" @click="queryClearStatus">
                        查询
                      </ElButton>
                    </ElFormItem>
                  </ElForm>
                  <ElTable
                    :data="clearStatusRows"
                    border
                    stripe
                    size="small"
                    max-height="260"
                    empty-text="暂无查询结果"
                  >
                    <ElTableColumn
                      prop="clrAppyEvtId"
                      label="事件号"
                      min-width="140"
                      show-overflow-tooltip
                    />
                    <ElTableColumn label="状态" width="88">
                      <template #default="{ row }">
                        {{ formatClrStas(row.clrStas) }}
                      </template>
                    </ElTableColumn>
                    <ElTableColumn prop="begndate" label="开始" width="96" />
                    <ElTableColumn prop="enddate" label="结束" width="96" />
                    <ElTableColumn label="医疗费" width="96" align="right">
                      <template #default="{ row }">
                        {{ formatMoney(row.medfeeSumamt) }}
                      </template>
                    </ElTableColumn>
                    <ElTableColumn label="操作" width="72" fixed="right">
                      <template #default="{ row }">
                        <ElButton
                          v-if="row.clrStas === '10'"
                          link
                          type="danger"
                          @click="handleRevokeClear(row)"
                        >
                          撤销
                        </ElButton>
                      </template>
                    </ElTableColumn>
                  </ElTable>
                </div>
              </div>
            </ElCol>

            <ElCol :xs="24" :lg="9">
              <div class="mb-3">
                <SectionTitle title="清算记录" class="recon-section-title mb-2" />
                <div v-loading="loadingClearRecords" class="recon-panel recon-local-records">
                  <ElEmpty
                    v-if="clearLocalRecords.length === 0"
                    description="暂无清算申请记录"
                    :image-size="64"
                  />
                  <div v-else class="flex flex-col gap-2">
                    <div
                      v-for="row in clearLocalRecords"
                      :key="String(row.id ?? row.clrAppyEvtId ?? `${row.begndate}-${row.clrType}`)"
                      class="recon-local-record-item"
                    >
                      <div class="flex items-start justify-between gap-2">
                        <div class="min-w-0 flex-1">
                          <div
                            class="text-sm font-medium"
                            style="color: var(--el-text-color-primary)"
                          >
                            {{ row.begndate || '—' }}
                            <span v-if="row.enddate && row.enddate !== row.begndate">
                              至 {{ row.enddate }}
                            </span>
                          </div>
                          <div class="mt-0.5 text-sm" style="color: var(--el-text-color-regular)">
                            {{ formatClrType(row.clrType) }}
                          </div>
                        </div>
                        <ElTag :type="clearStasTagType(row.clrStas)" size="small" effect="light">
                          {{ formatClrStas(row.clrStas) }}
                        </ElTag>
                      </div>
                      <div class="mt-1 text-xs" style="color: var(--el-text-color-secondary)">
                        医疗费 {{ formatMoney(row.medfeeSumamt) }}
                      </div>
                      <div v-if="row.clrStas === '10'" class="mt-1.5 flex justify-end">
                        <ElButton link type="danger" @click="handleRevokeClear(row)">
                          撤销
                        </ElButton>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </ElCol>
          </ElRow>
        </ElTabPane>

        <!-- ========== 工伤 ========== -->
        <ElTabPane v-if="wiModuleEnabled" label="工伤对账" name="workInjury" lazy>
          <ElCard shadow="never" class="max-w-2xl">
            <template #header>
              <span class="font-medium">工伤对账（1320→1322）</span>
            </template>
            <ElForm label-width="120px">
              <ElFormItem label="工伤费用性质" required>
                <ElSelect v-model="wiContractId" class="!w-full" filterable placeholder="请选择">
                  <ElOption
                    v-for="item in wiContractOptions"
                    :key="item.contractId"
                    :label="`${item.label}（${item.busNo}）`"
                    :value="item.contractId"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="对账日期">
                <ElDatePicker v-model="wiDateRange" v-bind="dateFieldProps" class="!w-full" />
              </ElFormItem>
              <ElFormItem label="统筹区编码">
                <ElInput v-model="wiSocialPoolCode" placeholder="220100" />
              </ElFormItem>
              <ElFormItem label="对账月份">
                <ElDatePicker
                  v-model="wiMonth"
                  type="month"
                  value-format="YYYYMM"
                  placeholder="可选"
                  clearable
                  class="!w-full"
                />
              </ElFormItem>
              <ElFormItem>
                <ElButton type="primary" :loading="wiRunning" @click="runWorkInjuryRecon">
                  启动工伤对账
                </ElButton>
              </ElFormItem>
              <ElAlert
                v-if="wiLastSummary"
                type="success"
                :closable="false"
                show-icon
                :title="wiLastSummary"
              />
              <ElEmpty v-else description="尚未执行" :image-size="64" />
            </ElForm>
          </ElCard>
        </ElTabPane>
      </ElTabs>
    </div>

    <AppDialog v-model="detailDrawerVisible" :title="detailDrawerTitle" width="720px">
      <template #default>
        <p v-if="detailDrawerMeta" class="mb-3 text-sm" style="color: var(--el-text-color-regular)">
          {{ detailDrawerMeta }}
        </p>
        <ElTable
          v-if="detailDrawerRecords.length"
          :data="detailDrawerRecords"
          border
          stripe
          size="small"
        >
          <ElTableColumn prop="stmtBegndate" label="开始日" width="110" />
          <ElTableColumn prop="stmtEnddate" label="结束日" width="110" />
          <ElTableColumn label="业务类型" width="88" align="center">
            <template #default="{ row }">
              {{ formatClrType(row.clrType) }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="rowCount" label="明细笔数" width="88" align="right" />
          <ElTableColumn prop="mismatchCount" label="差异笔数" width="88" align="right" />
          <ElTableColumn
            prop="resultFileQuryNo"
            label="结果文件号"
            min-width="140"
            show-overflow-tooltip
          />
        </ElTable>
        <ElEmpty
          v-else
          description="暂无关联的明细对账流水，请在左侧发起「对明细账」"
          :image-size="72"
          class="!py-4"
        />
      </template>
      <template #footer>
        <ElButton type="primary" @click="detailDrawerVisible = false"> 关闭 </ElButton>
      </template>
    </AppDialog>
  </Page>
</template>

<style scoped>
.recon-section-title {
  --section-title-font-size: 14px;
  --section-title-bar-height: 12px;
}

.recon-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
  padding: 12px;
}

.recon-local-records {
  max-height: 560px;
  overflow: auto;
}

.recon-local-record-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  padding: 10px 12px;
  background: var(--el-fill-color-blank);
}

.recon-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.recon-amount-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-light);
  padding: 12px 12px 4px;
}

.recon-form :deep(.el-form-item__label) {
  color: var(--el-text-color-regular);
  white-space: nowrap;
  line-height: 32px;
}

.recon-clear-filters {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  width: 100%;
  overflow: visible;
}

.recon-clear-filters :deep(.el-form-item) {
  flex-shrink: 0;
  margin-right: 12px;
  margin-bottom: 8px;
}

.recon-clear-filters :deep(.el-form-item__label) {
  width: 80px !important;
  min-width: 80px;
  height: 32px;
  line-height: 32px;
  white-space: nowrap !important;
  word-break: keep-all;
}

/* inline 并排布局，勿再 margin-left 叠宽 */
.recon-clear-filters :deep(.el-form-item__content) {
  margin-left: 0 !important;
}

.recon-filter-control--org {
  width: 220px;
}

.recon-filter-control--type {
  width: 120px;
}

.recon-filter-control--range {
  width: 280px;
}

.recon-amount-panel :deep(.el-form-item__label) {
  color: var(--el-text-color-regular);
  white-space: nowrap;
}
</style>
