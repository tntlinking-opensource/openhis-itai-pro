<script setup lang="ts">
import type {
  ClearAmountSummary,
  ClearOrg3206AResult,
  ClearStatus3205ARow,
  CrossProvinceReconRow,
  LocalClearApplyRecord,
  LocalReconDailyRecord,
  LocalReconDetailRecord,
  ReconDaily3201Result,
  ReconDetail3202Result,
  ReconDetailRow,
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
  ElDescriptions,
  ElDescriptionsItem,
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
  previewReconDetailRowsApi,
  queryClearApplyStatusApi,
  queryClearOrganizationsApi,
  queryCrossProvinceReconFailuresApi,
  reconcileDailyLedgerApi,
  reconcileDetailLedgerApi,
  revokeJilinClearApi,
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
  YB_INSUTYPE_OPTIONS,
} from '#/chs/constants/reconciliation';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'ChsReconciliationPage' });

const activeTab = ref<'daily' | 'clear'>('daily');
const contractBusNo = ref<string>();
const chargeTypeOptions = ref<ChargeType[]>([]);

const dateFieldProps = {
  type: 'daterange' as const,
  valueFormat: 'YYYY-MM-DD',
  rangeSeparator: '至',
  startPlaceholder: '开始日期',
  endPlaceholder: '结束日期',
  clearable: true,
};

const monthFilter = ref<string>();

// —— 日对账 3201 ——
const dailyDateRange = ref<[string, string]>();
const dailyInsutype = ref('310');
const dailyClrType = ref('11');
const dailySetlOptins = ref('');
const dailySummary = ref<ClearAmountSummary | null>(null);
const dailyReconResult = ref<ReconDaily3201Result | null>(null);
const dailyLocalRecords = ref<LocalReconDailyRecord[]>([]);
const detailPreviewRows = ref<ReconDetailRow[]>([]);
const detailReconResult = ref<ReconDetail3202Result | null>(null);
const detailLocalRecords = ref<LocalReconDetailRecord[]>([]);

const dailyAmountForm = ref({
  medfeeSumamt: undefined as number | undefined,
  fundPaySumamt: undefined as number | undefined,
  acctPay: undefined as number | undefined,
  fixmedinsSetlCnt: undefined as number | undefined,
});

// —— 月清算 3206A→3203A→3205A→3204A ——
const clearDateRange = ref<[string, string]>();
const clearClrType = ref('11');
const clearClrOptins = ref('');
const clearOrgResult = ref<ClearOrg3206AResult | null>(null);
const clearSummary = ref<ClearAmountSummary | null>(null);
const clearStatusRows = ref<ClearStatus3205ARow[]>([]);
const clearStatusStas = ref('10');
const clearStatusYm = ref('');
const clearLocalRecords = ref<LocalClearApplyRecord[]>([]);
const crossProvinceRows = ref<CrossProvinceReconRow[]>([]);

const clearAmountForm = ref({
  medfeeSumamt: undefined as number | undefined,
  medSumfee: undefined as number | undefined,
  fundAppySum: undefined as number | undefined,
  cashPayamt: undefined as number | undefined,
  acctPay: undefined as number | undefined,
});

const clrOptinsOptions = computed(() => {
  const rows = clearOrgResult.value?.clrOptins ?? [];
  return rows
    .map((row) => {
      const code = String(
        row.clr_optins ?? row.clrOptins ?? row.optins_no ?? row.optinsNo ?? '',
      ).trim();
      const name = String(row.clr_optins_name ?? row.clrOptinsName ?? row.optins_name ?? '').trim();
      if (!code) return null;
      return { label: name ? `${name}（${code}）` : code, value: code };
    })
    .filter((item): item is { label: string; value: string } => item != null);
});

function requireContractBusNo(): string {
  const busNo = contractBusNo.value?.trim();
  if (!busNo) {
    throw new Error('请先选择医保契约');
  }
  return busNo;
}

function requireDateRange(range?: [string, string] | null, label = '日期范围'): [string, string] {
  if (!range?.[0] || !range?.[1]) {
    throw new Error(`请选择${label}`);
  }
  return range;
}

async function withSignIn<T>(runner: () => Promise<T>): Promise<T> {
  await ensureInsuranceSignIn(requireContractBusNo());
  return runner();
}

function applySummaryToDailyForm(summary: ClearAmountSummary) {
  dailyAmountForm.value = {
    medfeeSumamt: Number(summary.medfeeSumamt ?? 0),
    fundPaySumamt: Number(summary.fundAppySum ?? 0),
    acctPay: Number(summary.acctPay ?? 0),
    fixmedinsSetlCnt: Number(summary.setlCount ?? 0),
  };
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

const { run: loadChargeTypes, loading: loadingChargeTypes } = useRequest(async () => {
  const list = await getChargeTypeListApi();
  const eligible = filterInsuranceChargeTypes(list);
  chargeTypeOptions.value = eligible;
  contractBusNo.value = resolveDefaultInsuranceBusNo(eligible, contractBusNo.value);
});

const { run: loadDailyLocalRecords, loading: loadingDailyRecords } = useRequest(async () => {
  dailyLocalRecords.value = await listLocalReconDailyRecordsApi({
    contractBusNo: requireContractBusNo(),
    monthFilter: monthFilter.value || undefined,
  });
});

const { run: loadDetailLocalRecords, loading: loadingDetailRecords } = useRequest(async () => {
  detailLocalRecords.value = await listLocalReconDetailRecordsApi({
    contractBusNo: requireContractBusNo(),
    monthFilter: monthFilter.value || undefined,
  });
});

const { run: loadClearLocalRecords, loading: loadingClearRecords } = useRequest(async () => {
  clearLocalRecords.value = await listLocalClearAppliesApi({
    contractBusNo: requireContractBusNo(),
    monthFilter: monthFilter.value || undefined,
  });
});

const { run: summarizeDailyAmounts, loading: summarizingDaily } = useRequest(async () => {
  const [begndate, enddate] = requireDateRange(dailyDateRange.value, '对账日期');
  const summary = await withSignIn(() =>
    summarizeClearAmountsApi({ contractBusNo: requireContractBusNo(), begndate, enddate }),
  );
  dailySummary.value = summary;
  applySummaryToDailyForm(summary);
  ElMessage.success(`已汇总本地结算 ${summary.setlCount ?? 0} 笔（未按险种/清算类别拆分）`);
});

const { run: runDailyRecon, loading: reconcilingDaily } = useRequest(async () => {
  const [stmtBegndate, stmtEnddate] = requireDateRange(dailyDateRange.value, '对账日期');
  if (!dailySetlOptins.value.trim()) {
    throw new Error('请填写结算经办机构');
  }
  const result = await withSignIn(() =>
    reconcileDailyLedgerApi({
      contractBusNo: requireContractBusNo(),
      insutype: dailyInsutype.value,
      clrType: dailyClrType.value,
      setlOptins: dailySetlOptins.value.trim(),
      stmtBegndate,
      stmtEnddate,
      medfeeSumamt: dailyAmountForm.value.medfeeSumamt,
      fundPaySumamt: dailyAmountForm.value.fundPaySumamt,
      acctPay: dailyAmountForm.value.acctPay,
      fixmedinsSetlCnt: dailyAmountForm.value.fixmedinsSetlCnt,
    }),
  );
  dailyReconResult.value = result;
  if (isStmtBalanced(result.stmtRslt)) {
    ElMessage.success('对总账平账');
  } else {
    ElMessage.warning(result.stmtRsltDscr || '对总账不平，请核对金额或进行明细对账');
  }
  await loadDailyLocalRecords();
});

const { run: previewDetailRows, loading: previewingDetail } = useRequest(async () => {
  const [begndate, enddate] = requireDateRange(dailyDateRange.value, '对账日期');
  detailPreviewRows.value = await withSignIn(() =>
    previewReconDetailRowsApi({ contractBusNo: requireContractBusNo(), begndate, enddate }),
  );
  ElMessage.success(`已加载 ${detailPreviewRows.value.length} 条本地结算明细`);
});

const { run: runDetailRecon, loading: reconcilingDetail } = useRequest(async () => {
  const [stmtBegndate, stmtEnddate] = requireDateRange(dailyDateRange.value, '对账日期');
  if (!dailySetlOptins.value.trim()) {
    throw new Error('请填写结算经办机构');
  }
  const result = await withSignIn(() =>
    reconcileDetailLedgerApi({
      contractBusNo: requireContractBusNo(),
      setlOptins: dailySetlOptins.value.trim(),
      stmtBegndate,
      stmtEnddate,
      clrType: dailyClrType.value,
      refdSetlFlag: '0',
      medfeeSumamt: dailyAmountForm.value.medfeeSumamt,
      fundPaySumamt: dailyAmountForm.value.fundPaySumamt,
      cashPayamt: dailySummary.value?.cashPayamt,
      fixmedinsSetlCnt: dailyAmountForm.value.fixmedinsSetlCnt,
    }),
  );
  detailReconResult.value = result;
  if ((result.mismatchCount ?? 0) === 0) {
    ElMessage.success('明细对账全部平账');
  } else {
    ElMessage.warning(`明细对账存在 ${result.mismatchCount} 条不平记录`);
  }
  await loadDetailLocalRecords();
});

const { run: queryClearOrgs, loading: queryingOrgs } = useRequest(async () => {
  const result = await withSignIn(() =>
    queryClearOrganizationsApi({
      contractBusNo: requireContractBusNo(),
      pageNum: 1,
      pageSize: 50,
    }),
  );
  clearOrgResult.value = result;
  const first = clrOptinsOptions.value[0]?.value;
  if (first && !clearClrOptins.value) {
    clearClrOptins.value = first;
    dailySetlOptins.value = first;
  }
  ElMessage.success(`已查询清算机构 ${result.clrOptins?.length ?? 0} 条`);
});

const { run: summarizeClearAmounts, loading: summarizingClear } = useRequest(async () => {
  const [begndate, enddate] = requireDateRange(clearDateRange.value, '清算月份');
  const summary = await withSignIn(() =>
    summarizeClearAmountsApi({ contractBusNo: requireContractBusNo(), begndate, enddate }),
  );
  clearSummary.value = summary;
  applySummaryToClearForm(summary);
  ElMessage.success(`已汇总本地结算 ${summary.setlCount ?? 0} 笔`);
});

const { run: applyClear, loading: applyingClear } = useRequest(async () => {
  const [begndate, enddate] = requireDateRange(clearDateRange.value, '清算月份');
  if (!clearClrOptins.value.trim()) {
    throw new Error('请选择清算机构（先执行 3206A 查询）');
  }
  const result = await withSignIn(() =>
    applyJilinClearApi({
      contractBusNo: requireContractBusNo(),
      clrType: clearClrType.value,
      clrOptins: clearClrOptins.value.trim(),
      begndate,
      enddate,
      ...clearAmountForm.value,
    }),
  );
  await ElMessageBox.alert(
    `清算申请已提交，事件 ID：${result.clrAppyEvtId ?? '—'}`,
    '3203A 清算申请',
  );
  await loadClearLocalRecords();
});

const { run: queryClearStatus, loading: queryingStatus } = useRequest(async () => {
  if (!clearClrOptins.value.trim()) {
    throw new Error('请选择清算机构');
  }
  if (['20', '50'].includes(clearStatusStas.value) && !clearStatusYm.value.trim()) {
    throw new Error('查询已受理/已清算状态需填写清算月份（yyyyMM）');
  }
  clearStatusRows.value = await withSignIn(() =>
    queryClearApplyStatusApi({
      contractBusNo: requireContractBusNo(),
      clrOptins: clearClrOptins.value.trim(),
      clrStas: clearStatusStas.value,
      clrYm: clearStatusYm.value.trim() || undefined,
    }),
  );
  ElMessage.success(`查询到 ${clearStatusRows.value.length} 条清算状态`);
});

const { run: queryCrossProvinceRecon, loading: queryingCrossProvince } = useRequest(async () => {
  const [begndate, enddate] = requireDateRange(clearDateRange.value, '查询日期');
  if (!clearClrOptins.value.trim()) {
    throw new Error('请选择清算机构（先执行 3206A 查询）');
  }
  crossProvinceRows.value = await withSignIn(() =>
    queryCrossProvinceReconFailuresApi({
      contractBusNo: requireContractBusNo(),
      clrType: clearClrType.value,
      clrOptins: clearClrOptins.value.trim(),
      begndate,
      enddate,
    }),
  );
  ElMessage.success(`查询到 ${crossProvinceRows.value.length} 条跨省三方未成功数据`);
});

async function handleRevokeClear(row: ClearStatus3205ARow | LocalClearApplyRecord) {
  const evtId = row.clrAppyEvtId?.trim();
  const optins = (row.clrOptins ?? clearClrOptins.value)?.trim();
  if (!evtId || !optins) {
    ElMessage.warning('缺少清算事件 ID 或清算机构');
    return;
  }
  await ElMessageBox.confirm(`确认撤销清算申请 ${evtId}？`, '3204A 撤销', { type: 'warning' });
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

function refreshLocalRecords() {
  if (activeTab.value === 'daily') {
    return Promise.all([loadDailyLocalRecords(), loadDetailLocalRecords()]);
  }
  return loadClearLocalRecords();
}

onMounted(async () => {
  await loadChargeTypes();
  if (contractBusNo.value) {
    await Promise.all([loadDailyLocalRecords(), loadDetailLocalRecords(), loadClearLocalRecords()]);
  }
});
</script>

<template>
  <Page auto-content-height>
    <ElAlert
      class="mb-4"
      type="info"
      :closable="false"
      title="对账清算（吉林）"
      description="日对账：3201 对总账；总账不平时用 3202 明细对账（9101 上传 → 3202 → 9102 下载）。月清算按 3206A→3203A→3205A→3204A；跨省异常用 3209A 查询。"
    />

    <ElCard shadow="never" class="mb-4">
      <ElForm inline label-width="88px">
        <ElFormItem label="医保契约" required>
          <ElSelect
            v-model="contractBusNo"
            class="!w-72"
            placeholder="请选择医保契约"
            :loading="loadingChargeTypes"
            @change="refreshLocalRecords"
          >
            <ElOption
              v-for="item in chargeTypeOptions"
              :key="item.busNo"
              :label="item.busNo ? `${item.label}（${item.busNo}）` : item.label"
              :value="item.busNo"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="月份筛选">
          <ElInput
            v-model="monthFilter"
            class="!w-36"
            placeholder="yyyyMM"
            clearable
            @change="refreshLocalRecords"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton @click="refreshLocalRecords">刷新本地记录</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElTabs v-model="activeTab">
      <ElTabPane label="日对账（3201）" name="daily">
        <ElRow :gutter="16">
          <ElCol :span="14">
            <ElCard shadow="never" header="对总账">
              <ElForm label-width="120px">
                <ElFormItem label="对账日期" required>
                  <ElDatePicker v-model="dailyDateRange" v-bind="dateFieldProps" class="!w-full" />
                </ElFormItem>
                <ElFormItem label="险种" required>
                  <ElSelect v-model="dailyInsutype" class="!w-full">
                    <ElOption
                      v-for="item in YB_INSUTYPE_OPTIONS"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="清算类别" required>
                  <ElSelect v-model="dailyClrType" class="!w-full">
                    <ElOption
                      v-for="item in YB_CLR_TYPE_OPTIONS"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="结算经办机构" required>
                  <ElInput v-model="dailySetlOptins" placeholder="6 位编码，可从 3206A 获取" />
                </ElFormItem>
                <ElFormItem label="医疗费总额">
                  <ElInputNumber
                    v-model="dailyAmountForm.medfeeSumamt"
                    :min="0"
                    :precision="2"
                    class="!w-full"
                  />
                </ElFormItem>
                <ElFormItem label="基金支付总额">
                  <ElInputNumber
                    v-model="dailyAmountForm.fundPaySumamt"
                    :min="0"
                    :precision="2"
                    class="!w-full"
                  />
                </ElFormItem>
                <ElFormItem label="个账支付">
                  <ElInputNumber
                    v-model="dailyAmountForm.acctPay"
                    :min="0"
                    :precision="2"
                    class="!w-full"
                  />
                </ElFormItem>
                <ElFormItem label="结算笔数">
                  <ElInputNumber
                    v-model="dailyAmountForm.fixmedinsSetlCnt"
                    :min="0"
                    :precision="0"
                    class="!w-full"
                  />
                </ElFormItem>
                <ElFormItem>
                  <ElButton :loading="summarizingDaily" @click="summarizeDailyAmounts">
                    汇总本地金额
                  </ElButton>
                  <ElButton type="primary" :loading="reconcilingDaily" @click="runDailyRecon">
                    3201 对总账
                  </ElButton>
                </ElFormItem>
              </ElForm>
              <ElDescriptions v-if="dailyReconResult" :column="2" border class="mt-4">
                <ElDescriptionsItem label="对账结果">
                  <ElTag :type="isStmtBalanced(dailyReconResult.stmtRslt) ? 'success' : 'danger'">
                    {{ formatStmtRslt(dailyReconResult.stmtRslt) }}
                  </ElTag>
                </ElDescriptionsItem>
                <ElDescriptionsItem label="说明">
                  {{ dailyReconResult.stmtRsltDscr || '—' }}
                </ElDescriptionsItem>
                <ElDescriptionsItem label="经办机构">
                  {{ dailyReconResult.setlOptins || '—' }}
                </ElDescriptionsItem>
                <ElDescriptionsItem label="本地记录 ID">
                  {{ dailyReconResult.localRecordId ?? '—' }}
                </ElDescriptionsItem>
              </ElDescriptions>
            </ElCard>

            <ElCard shadow="never" header="明细对账（3202）" class="mt-4">
              <ElAlert
                class="mb-3"
                type="warning"
                :closable="false"
                show-icon
                title="3201 不平账时使用"
                description="自动从本地 yb_settlement_record / yb_inpatient_pre_settle 生成 TAB 明细文件，经 9101 上传后调用 3202，并 9102 下载结果。"
              />
              <ElFormItem>
                <ElButton :loading="previewingDetail" @click="previewDetailRows"
                  >预览本地明细</ElButton
                >
                <ElButton type="warning" :loading="reconcilingDetail" @click="runDetailRecon">
                  3202 明细对账
                </ElButton>
              </ElFormItem>
              <ElTable
                v-if="detailPreviewRows.length"
                :data="detailPreviewRows"
                max-height="220"
                border
                stripe
                class="mb-3"
              >
                <ElTableColumn
                  prop="setlId"
                  label="结算 ID"
                  min-width="120"
                  show-overflow-tooltip
                />
                <ElTableColumn
                  prop="mdtrtId"
                  label="就诊 ID"
                  min-width="120"
                  show-overflow-tooltip
                />
                <ElTableColumn prop="source" label="来源" width="56" />
                <ElTableColumn prop="medfeeSumamt" label="医疗费" width="88" />
                <ElTableColumn prop="fundPaySumamt" label="基金" width="88" />
              </ElTable>
              <ElDescriptions v-if="detailReconResult" :column="2" border>
                <ElDescriptionsItem label="上传文件号">
                  {{ detailReconResult.uploadFileQuryNo || '—' }}
                </ElDescriptionsItem>
                <ElDescriptionsItem label="结果文件号">
                  {{ detailReconResult.resultFileQuryNo || '—' }}
                </ElDescriptionsItem>
                <ElDescriptionsItem label="明细笔数">
                  {{ detailReconResult.rowCount ?? '—' }}
                </ElDescriptionsItem>
                <ElDescriptionsItem label="不平笔数">
                  <ElTag
                    :type="(detailReconResult.mismatchCount ?? 0) === 0 ? 'success' : 'danger'"
                  >
                    {{ detailReconResult.mismatchCount ?? 0 }}
                  </ElTag>
                </ElDescriptionsItem>
              </ElDescriptions>
              <ElTable
                v-if="detailReconResult?.resultRows?.length"
                :data="detailReconResult.resultRows"
                max-height="240"
                border
                stripe
                class="mt-3"
              >
                <ElTableColumn
                  prop="setlId"
                  label="结算 ID"
                  min-width="110"
                  show-overflow-tooltip
                />
                <ElTableColumn label="结果" width="72">
                  <template #default="{ row }">
                    <ElTag :type="row.stmtRslt === '0' ? 'success' : 'danger'" size="small">
                      {{ formatStmtRslt(row.stmtRslt) }}
                    </ElTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn prop="memo" label="备注" min-width="120" show-overflow-tooltip />
              </ElTable>
            </ElCard>
          </ElCol>
          <ElCol :span="10">
            <ElCard shadow="never" header="本地日对账记录">
              <ElTable
                :data="dailyLocalRecords"
                v-loading="loadingDailyRecords"
                max-height="520"
                border
                stripe
              >
                <ElTableColumn prop="stmtBegndate" label="开始" width="100" />
                <ElTableColumn prop="stmtEnddate" label="结束" width="100" />
                <ElTableColumn label="险种" width="90">
                  <template #default="{ row }">{{ formatInsutype(row.insutype) }}</template>
                </ElTableColumn>
                <ElTableColumn label="结果" width="72">
                  <template #default="{ row }">
                    <ElTag :type="isStmtBalanced(row.stmtRslt) ? 'success' : 'danger'" size="small">
                      {{ formatStmtRslt(row.stmtRslt) }}
                    </ElTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn prop="medfeeSumamt" label="医疗费" min-width="88" />
                <ElTableColumn
                  prop="createdAt"
                  label="时间"
                  min-width="140"
                  show-overflow-tooltip
                />
              </ElTable>
            </ElCard>
            <ElCard shadow="never" header="本地明细对账记录" class="mt-4">
              <ElTable
                :data="detailLocalRecords"
                v-loading="loadingDetailRecords"
                max-height="360"
                border
                stripe
              >
                <ElTableColumn prop="stmtBegndate" label="开始" width="100" />
                <ElTableColumn prop="stmtEnddate" label="结束" width="100" />
                <ElTableColumn prop="rowCount" label="笔数" width="64" />
                <ElTableColumn label="不平" width="64">
                  <template #default="{ row }">
                    <ElTag
                      :type="(row.mismatchCount ?? 0) === 0 ? 'success' : 'danger'"
                      size="small"
                    >
                      {{ row.mismatchCount ?? 0 }}
                    </ElTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn
                  prop="createdAt"
                  label="时间"
                  min-width="130"
                  show-overflow-tooltip
                />
              </ElTable>
            </ElCard>
          </ElCol>
        </ElRow>
      </ElTabPane>

      <ElTabPane label="月清算（3206A→3203A）" name="clear">
        <ElRow :gutter="16">
          <ElCol :span="14">
            <ElCard shadow="never" header="清算申请">
              <ElForm label-width="120px">
                <ElFormItem>
                  <ElButton :loading="queryingOrgs" @click="queryClearOrgs"
                    >3206A 查询清算机构</ElButton
                  >
                </ElFormItem>
                <ElFormItem label="清算机构" required>
                  <ElSelect
                    v-model="clearClrOptins"
                    class="!w-full"
                    filterable
                    placeholder="先执行 3206A"
                  >
                    <ElOption
                      v-for="item in clrOptinsOptions"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="清算类别" required>
                  <ElSelect v-model="clearClrType" class="!w-full">
                    <ElOption
                      v-for="item in YB_CLR_TYPE_OPTIONS"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="清算月份" required>
                  <ElDatePicker v-model="clearDateRange" v-bind="dateFieldProps" class="!w-full" />
                </ElFormItem>
                <ElFormItem label="医疗费总额">
                  <ElInputNumber
                    v-model="clearAmountForm.medfeeSumamt"
                    :min="0"
                    :precision="2"
                    class="!w-full"
                  />
                </ElFormItem>
                <ElFormItem label="认可费用总额">
                  <ElInputNumber
                    v-model="clearAmountForm.medSumfee"
                    :min="0"
                    :precision="2"
                    class="!w-full"
                  />
                </ElFormItem>
                <ElFormItem label="基金申报总额">
                  <ElInputNumber
                    v-model="clearAmountForm.fundAppySum"
                    :min="0"
                    :precision="2"
                    class="!w-full"
                  />
                </ElFormItem>
                <ElFormItem label="现金支付">
                  <ElInputNumber
                    v-model="clearAmountForm.cashPayamt"
                    :min="0"
                    :precision="2"
                    class="!w-full"
                  />
                </ElFormItem>
                <ElFormItem label="个账支出">
                  <ElInputNumber
                    v-model="clearAmountForm.acctPay"
                    :min="0"
                    :precision="2"
                    class="!w-full"
                  />
                </ElFormItem>
                <ElFormItem>
                  <ElButton :loading="summarizingClear" @click="summarizeClearAmounts">
                    汇总本地金额
                  </ElButton>
                  <ElButton type="primary" :loading="applyingClear" @click="applyClear">
                    3203A 清算申请
                  </ElButton>
                </ElFormItem>
              </ElForm>
            </ElCard>

            <ElCard shadow="never" header="清算状态 / 撤销" class="mt-4">
              <ElForm inline label-width="88px">
                <ElFormItem label="状态">
                  <ElSelect v-model="clearStatusStas" class="!w-36">
                    <ElOption
                      v-for="item in YB_CLR_STAS_OPTIONS"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="清算月份">
                  <ElInput v-model="clearStatusYm" class="!w-32" placeholder="yyyyMM" />
                </ElFormItem>
                <ElFormItem>
                  <ElButton :loading="queryingStatus" @click="queryClearStatus"
                    >3205A 查询状态</ElButton
                  >
                </ElFormItem>
              </ElForm>
              <ElTable :data="clearStatusRows" border stripe max-height="280">
                <ElTableColumn
                  prop="clrAppyEvtId"
                  label="事件 ID"
                  min-width="140"
                  show-overflow-tooltip
                />
                <ElTableColumn label="状态" width="88">
                  <template #default="{ row }">{{ formatClrStas(row.clrStas) }}</template>
                </ElTableColumn>
                <ElTableColumn prop="begndate" label="开始" width="100" />
                <ElTableColumn prop="enddate" label="结束" width="100" />
                <ElTableColumn prop="medfeeSumamt" label="医疗费" width="96" />
                <ElTableColumn label="操作" width="88" fixed="right">
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
            </ElCard>

            <ElCard shadow="never" header="跨省三方对账（3209A）" class="mt-4">
              <ElAlert
                class="mb-3"
                type="info"
                :closable="false"
                show-icon
                title="查询跨省三方对账未成功数据"
                description="与月清算共用清算机构、清算类别与日期范围；返回结算 ID 与参保地区划，供人工核对。"
              />
              <ElFormItem>
                <ElButton :loading="queryingCrossProvince" @click="queryCrossProvinceRecon">
                  3209A 查询未成功数据
                </ElButton>
              </ElFormItem>
              <ElTable :data="crossProvinceRows" border stripe max-height="240">
                <ElTableColumn
                  prop="setlId"
                  label="结算 ID"
                  min-width="160"
                  show-overflow-tooltip
                />
                <ElTableColumn
                  prop="insuAdmdvs"
                  label="参保地区划"
                  min-width="120"
                  show-overflow-tooltip
                />
              </ElTable>
            </ElCard>
          </ElCol>

          <ElCol :span="10">
            <ElCard shadow="never" header="本地清算申请记录">
              <ElTable
                :data="clearLocalRecords"
                v-loading="loadingClearRecords"
                max-height="640"
                border
                stripe
              >
                <ElTableColumn prop="begndate" label="开始" width="100" />
                <ElTableColumn prop="enddate" label="结束" width="100" />
                <ElTableColumn label="类别" width="72">
                  <template #default="{ row }">{{ formatClrType(row.clrType) }}</template>
                </ElTableColumn>
                <ElTableColumn label="状态" width="88">
                  <template #default="{ row }">{{ formatClrStas(row.clrStas) }}</template>
                </ElTableColumn>
                <ElTableColumn
                  prop="clrAppyEvtId"
                  label="事件 ID"
                  min-width="120"
                  show-overflow-tooltip
                />
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
            </ElCard>
          </ElCol>
        </ElRow>
      </ElTabPane>
    </ElTabs>
  </Page>
</template>
