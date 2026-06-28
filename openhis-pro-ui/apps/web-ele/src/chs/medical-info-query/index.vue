<script setup lang="ts">
import type { ChargeType } from '#/api/core/common';
import type { Setl5203Result } from '#/chs/api';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTabs,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  queryCum5206Api,
  queryDise5202Api,
  queryFee5204Api,
  queryMdtrt5201Api,
  queryOpspDrug5205Api,
  querySetl5203Api,
} from '#/chs/api';
import { httpGetInsurancePersonInfo } from '#/chs/api/insurance-person';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import {
  formatInhospStas,
  formatMedType,
  rowField,
  YB_MED_TYPE_OPTIONS,
} from '#/chs/constants/medical-info-query';
import { formatInsutype } from '#/chs/constants/reconciliation';
import { formatChrgitmLv } from '#/chs/constants/catalog-list-type';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';
import {
  DEFAULT_CARD_READER_HOST,
  DEFAULT_CARD_READER_PORT,
  invokeSocialCardRead,
  invokeYbPlugin,
  resolveSocialCard1101Credential,
  YB_PLUGIN_FUNCTION_ID,
} from '#/chs/utils/yb-plugin';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'ChsMedicalInfoQueryPage' });

type MainTab = 'visit' | 'opsp' | 'cum';
type DetailTab = 'dise' | 'setl' | 'fee';

const activeTab = ref<MainTab>('visit');
const contractBusNo = ref<string>();
const contractId = ref<string>();
const chargeTypeOptions = ref<ChargeType[]>([]);

const psnNo = ref('');
const psnName = ref('');

const visitMedType = ref('11');
const visitDateRange = ref<[string, string]>();
const visitRows = ref<Array<Record<string, unknown>>>([]);

const opspDateRange = ref<[string, string]>();
const opspRows = ref<Array<Record<string, unknown>>>([]);

const cumYm = ref('');
const cumRows = ref<Array<Record<string, unknown>>>([]);

const drawerVisible = ref(false);
const detailTab = ref<DetailTab>('dise');
const selectedVisit = ref<Record<string, unknown> | null>(null);
const manualSetlId = ref('');

const diseRows = ref<Array<Record<string, unknown>>>([]);
const setlResult = ref<Setl5203Result | null>(null);
const feeRows = ref<Array<Record<string, unknown>>>([]);

const dateFieldProps = {
  type: 'daterange' as const,
  valueFormat: 'YYYY-MM-DD',
  rangeSeparator: '至',
  startPlaceholder: '开始日期',
  endPlaceholder: '结束日期',
  clearable: true,
};

const selectedContract = computed(() =>
  chargeTypeOptions.value.find((item) => item.busNo === contractBusNo.value),
);

function requireContractBusNo(): string {
  const busNo = contractBusNo.value?.trim();
  if (!busNo) {
    throw new Error('请先选择医保契约');
  }
  return busNo;
}

function requirePsnNo(): string {
  const no = psnNo.value.trim();
  if (!no) {
    throw new Error('请先读卡或填写人员编号');
  }
  return no;
}

function requireDateRange(range?: [string, string] | null, label = '日期范围'): [string, string] {
  if (!range?.[0] || !range?.[1]) {
    throw new Error(`请选择${label}`);
  }
  return range;
}

function toDateTimeRange(range: [string, string]) {
  return {
    begntime: `${range[0]} 00:00:00`,
    endtime: `${range[1]} 23:59:59`,
  };
}

function defaultDateRange(days: number): [string, string] {
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - days);
  const fmt = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  return [fmt(start), fmt(end)];
}

async function withSignIn<T>(runner: () => Promise<T>): Promise<T> {
  await ensureInsuranceSignIn(requireContractBusNo());
  return runner();
}

function syncContractId() {
  const selected = selectedContract.value;
  contractId.value = selected?.value ? String(selected.value) : '';
}

function baseRequest() {
  return {
    contractBusNo: requireContractBusNo(),
    contractId: contractId.value || undefined,
    psnNo: requirePsnNo(),
  };
}

const { run: loadChargeTypes, loading: loadingChargeTypes } = useRequest(async () => {
  const list = await getChargeTypeListApi();
  const eligible = filterInsuranceChargeTypes(list);
  chargeTypeOptions.value = eligible;
  contractBusNo.value = resolveDefaultInsuranceBusNo(eligible, contractBusNo.value);
  syncContractId();
});

const { run: readCard, loading: readingCard } = useRequest(async () => {
  syncContractId();
  if (!contractId.value) {
    throw new Error('当前契约无效，请重新选择');
  }
  await ensureInsuranceSignIn(requireContractBusNo());
  const card = await invokeSocialCardRead({
    FunctionId: YB_PLUGIN_FUNCTION_ID.SOCIAL_CARD,
    IP: DEFAULT_CARD_READER_HOST,
    PORT: DEFAULT_CARD_READER_PORT,
    TIMEOUT: 60,
    LOG_PATH: 'C:/neu_log/',
    SFZ_DRIVER_TYPE: 0,
  });
  const credential = resolveSocialCard1101Credential(card);
  const person = await httpGetInsurancePersonInfo({
    cardSn: credential.cardSn,
    certno: credential.certno,
    contractId: contractId.value,
    mdtrtCertType: credential.mdtrtCertType,
    mdtrtCertNo: credential.mdtrtCertNo,
    psnCertType: credential.psnCertType,
  });
  psnNo.value = person.psnNo;
  psnName.value = person.psnName ?? '';
  ElMessage.success(`读卡成功，人员编号：${person.psnNo}`);
});

const { run: searchVisit, loading: loadingVisit } = useRequest(async () => {
  const range = requireDateRange(visitDateRange.value, '就诊时间');
  const dt = toDateTimeRange(range);
  const result = await withSignIn(() =>
    queryMdtrt5201Api({
      ...baseRequest(),
      ...dt,
      medType: visitMedType.value,
    }),
  );
  visitRows.value = result.rows ?? [];
  if (visitRows.value.length === 0) {
    ElMessage.info('未查询到就诊记录');
  }
});

const { run: searchOpsp, loading: loadingOpsp } = useRequest(async () => {
  const range = requireDateRange(opspDateRange.value, '查询时间');
  const dt = toDateTimeRange(range);
  const result = await withSignIn(() =>
    queryOpspDrug5205Api({
      ...baseRequest(),
      begntime: dt.begntime,
      endtime: dt.endtime,
    }),
  );
  opspRows.value = result.rows ?? [];
  if (opspRows.value.length === 0) {
    ElMessage.info('未查询到慢特病用药记录');
  }
});

const { run: searchCum, loading: loadingCum } = useRequest(async () => {
  const result = await withSignIn(() =>
    queryCum5206Api({
      ...baseRequest(),
      cumYm: cumYm.value.trim() || undefined,
    }),
  );
  cumRows.value = result.rows ?? [];
  if (cumRows.value.length === 0) {
    ElMessage.info('未查询到累计信息');
  }
});

const { run: loadDise, loading: loadingDise } = useRequest(async () => {
  const mdtrtId = rowField(selectedVisit.value ?? {}, 'mdtrt_id', 'mdtrtId');
  if (!mdtrtId) {
    throw new Error('缺少就诊ID');
  }
  const result = await withSignIn(() =>
    queryDise5202Api({
      ...baseRequest(),
      mdtrtId,
    }),
  );
  diseRows.value = result.rows ?? [];
});

const { run: loadSetl, loading: loadingSetl } = useRequest(async () => {
  const mdtrtId = rowField(selectedVisit.value ?? {}, 'mdtrt_id', 'mdtrtId');
  const setlId =
    manualSetlId.value.trim() || rowField(selectedVisit.value ?? {}, 'setl_id', 'setlId');
  if (!mdtrtId) {
    throw new Error('缺少就诊ID');
  }
  if (!setlId) {
    throw new Error('请填写结算ID（5201 行未返回时可手工输入）');
  }
  setlResult.value = await withSignIn(() =>
    querySetl5203Api({
      ...baseRequest(),
      mdtrtId,
      setlId,
    }),
  );
});

const { run: loadFee, loading: loadingFee } = useRequest(async () => {
  const mdtrtId = rowField(selectedVisit.value ?? {}, 'mdtrt_id', 'mdtrtId');
  if (!mdtrtId) {
    throw new Error('缺少就诊ID');
  }
  const setlId =
    manualSetlId.value.trim() ||
    rowField(selectedVisit.value ?? {}, 'setl_id', 'setlId') ||
    undefined;
  const result = await withSignIn(() =>
    queryFee5204Api({
      ...baseRequest(),
      mdtrtId,
      setlId,
    }),
  );
  feeRows.value = result.rows ?? [];
});

function openVisitDetail(row: Record<string, unknown>) {
  selectedVisit.value = row;
  manualSetlId.value = rowField(row, 'setl_id', 'setlId');
  diseRows.value = [];
  setlResult.value = null;
  feeRows.value = [];
  detailTab.value = 'dise';
  drawerVisible.value = true;
  void loadDise();
}

function onDetailTabChange(name: string | number) {
  if (name === 'dise' && diseRows.value.length === 0) {
    void loadDise();
  }
  if (name === 'setl' && !setlResult.value) {
    void loadSetl();
  }
  if (name === 'fee' && feeRows.value.length === 0) {
    void loadFee();
  }
}

onMounted(() => {
  visitDateRange.value = defaultDateRange(30);
  opspDateRange.value = defaultDateRange(90);
  void loadChargeTypes();
});
</script>

<template>
  <Page auto-content-height>
    <ElCard shadow="never" class="mb-4">
      <ElForm inline label-width="88px">
        <ElFormItem label="医保契约">
          <ElSelect
            v-model="contractBusNo"
            placeholder="选择契约"
            filterable
            style="width: 220px"
            :loading="loadingChargeTypes"
            @change="syncContractId"
          >
            <ElOption
              v-for="item in chargeTypeOptions"
              :key="item.busNo"
              :label="item.label || item.busNo"
              :value="item.busNo!"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="人员编号">
          <ElInput v-model="psnNo" placeholder="读卡或手工输入" style="width: 200px" clearable />
        </ElFormItem>
        <ElFormItem label="姓名">
          <ElInput v-model="psnName" placeholder="读卡回填" style="width: 140px" disabled />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" :loading="readingCard" @click="readCard">读卡 1101</ElButton>
        </ElFormItem>
      </ElForm>
      <ElAlert
        type="info"
        :closable="false"
        show-icon
        title="5201 查就诊 → 5202/5203/5204 查诊断/结算/明细；5205 慢特病用药；5206 人员累计。查询前自动 9001 签到。"
      />
    </ElCard>

    <ElCard shadow="never">
      <ElTabs v-model="activeTab">
        <ElTabPane label="就诊查询 (5201)" name="visit">
          <ElForm inline class="mb-3">
            <ElFormItem label="医疗类别">
              <ElSelect v-model="visitMedType" style="width: 160px">
                <ElOption
                  v-for="item in YB_MED_TYPE_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="就诊时间">
              <ElDatePicker v-model="visitDateRange" v-bind="dateFieldProps" />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :loading="loadingVisit" @click="searchVisit">查询</ElButton>
            </ElFormItem>
          </ElForm>
          <ElTable :data="visitRows" stripe border max-height="520" empty-text="暂无数据">
            <ElTableColumn label="就诊ID" min-width="160">
              <template #default="{ row }">{{ rowField(row, 'mdtrt_id', 'mdtrtId') }}</template>
            </ElTableColumn>
            <ElTableColumn label="姓名" width="100">
              <template #default="{ row }">{{ rowField(row, 'psn_name', 'psnName') }}</template>
            </ElTableColumn>
            <ElTableColumn label="医疗类别" width="110">
              <template #default="{ row }">
                {{ formatMedType(rowField(row, 'med_type', 'medType')) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="险种" width="100">
              <template #default="{ row }">
                {{ formatInsutype(rowField(row, 'insutype')) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="开始时间" min-width="160">
              <template #default="{ row }">{{ rowField(row, 'begntime') }}</template>
            </ElTableColumn>
            <ElTableColumn label="结束时间" min-width="160">
              <template #default="{ row }">{{ rowField(row, 'endtime') }}</template>
            </ElTableColumn>
            <ElTableColumn label="在院状态" width="90">
              <template #default="{ row }">
                {{ formatInhospStas(rowField(row, 'inhosp_stas', 'inhospStas')) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="主诊断" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{
                  rowField(
                    row,
                    'dscg_maindiag_name',
                    'dscgMaindiagName',
                    'otp_diag_info',
                    'otpDiagInfo',
                  )
                }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <ElButton link type="primary" @click="openVisitDetail(row)">详情</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <ElTabPane label="慢特病用药 (5205)" name="opsp">
          <ElForm inline class="mb-3">
            <ElFormItem label="时间范围">
              <ElDatePicker v-model="opspDateRange" v-bind="dateFieldProps" />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :loading="loadingOpsp" @click="searchOpsp">查询</ElButton>
            </ElFormItem>
          </ElForm>
          <ElTable :data="opspRows" stripe border max-height="520" empty-text="暂无数据">
            <ElTableColumn label="费用时间" min-width="160">
              <template #default="{ row }">{{
                rowField(row, 'fee_ocur_time', 'feeOcurTime')
              }}</template>
            </ElTableColumn>
            <ElTableColumn label="目录编码" min-width="140">
              <template #default="{ row }">{{
                rowField(row, 'hilist_code', 'hilistCode')
              }}</template>
            </ElTableColumn>
            <ElTableColumn label="目录名称" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{
                rowField(row, 'hilist_name', 'hilistName')
              }}</template>
            </ElTableColumn>
            <ElTableColumn label="数量" width="90">
              <template #default="{ row }">{{ rowField(row, 'cnt') }}</template>
            </ElTableColumn>
            <ElTableColumn label="单价" width="100">
              <template #default="{ row }">{{ rowField(row, 'pric') }}</template>
            </ElTableColumn>
            <ElTableColumn label="等级" width="80">
              <template #default="{ row }">
                {{ formatChrgitmLv(rowField(row, 'chrgitm_lv', 'chrgitmLv')) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="机构" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{
                rowField(row, 'fixmedins_name', 'fixmedinsName')
              }}</template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <ElTabPane label="人员累计 (5206)" name="cum">
          <ElForm inline class="mb-3">
            <ElFormItem label="累计年月">
              <ElDatePicker
                v-model="cumYm"
                type="month"
                value-format="YYYYMM"
                placeholder="可选，默认平台范围"
                clearable
              />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :loading="loadingCum" @click="searchCum">查询</ElButton>
            </ElFormItem>
          </ElForm>
          <ElTable :data="cumRows" stripe border max-height="520" empty-text="暂无数据">
            <ElTableColumn label="险种" width="120">
              <template #default="{ row }">{{
                formatInsutype(rowField(row, 'insutype'))
              }}</template>
            </ElTableColumn>
            <ElTableColumn label="年度" width="80">
              <template #default="{ row }">{{ rowField(row, 'year') }}</template>
            </ElTableColumn>
            <ElTableColumn label="累计年月" width="100">
              <template #default="{ row }">{{ rowField(row, 'cum_ym', 'cumYm') }}</template>
            </ElTableColumn>
            <ElTableColumn label="累计类别" min-width="140">
              <template #default="{ row }">{{
                rowField(row, 'cum_type_code', 'cumTypeCode')
              }}</template>
            </ElTableColumn>
            <ElTableColumn label="累计值" width="120">
              <template #default="{ row }">{{ rowField(row, 'cum') }}</template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>
      </ElTabs>
    </ElCard>

    <ElDrawer v-model="drawerVisible" title="就诊详情" size="72%" destroy-on-close>
      <ElDescriptions v-if="selectedVisit" :column="2" border class="mb-4" size="small">
        <ElDescriptionsItem label="就诊ID">
          {{ rowField(selectedVisit, 'mdtrt_id', 'mdtrtId') }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="医疗类别">
          {{ formatMedType(rowField(selectedVisit, 'med_type', 'medType')) }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="开始时间">
          {{ rowField(selectedVisit, 'begntime') }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="结束时间">
          {{ rowField(selectedVisit, 'endtime') }}
        </ElDescriptionsItem>
      </ElDescriptions>

      <ElForm inline class="mb-3">
        <ElFormItem label="结算ID">
          <ElInput
            v-model="manualSetlId"
            placeholder="5203 必填，可手工输入"
            style="width: 220px"
            clearable
          />
        </ElFormItem>
      </ElForm>

      <ElTabs v-model="detailTab" @tab-change="onDetailTabChange">
        <ElTabPane label="诊断 (5202)" name="dise">
          <div class="mb-2">
            <ElButton size="small" :loading="loadingDise" @click="loadDise">刷新</ElButton>
          </div>
          <ElTable :data="diseRows" stripe border max-height="420" empty-text="暂无数据">
            <ElTableColumn label="主诊断" width="80">
              <template #default="{ row }">{{
                rowField(row, 'maindiag_flag', 'maindiagFlag')
              }}</template>
            </ElTableColumn>
            <ElTableColumn label="诊断代码" min-width="120">
              <template #default="{ row }">{{ rowField(row, 'diag_code', 'diagCode') }}</template>
            </ElTableColumn>
            <ElTableColumn label="诊断名称" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ rowField(row, 'diag_name', 'diagName') }}</template>
            </ElTableColumn>
            <ElTableColumn label="诊断时间" min-width="160">
              <template #default="{ row }">{{ rowField(row, 'diag_time', 'diagTime') }}</template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <ElTabPane label="结算 (5203)" name="setl">
          <div class="mb-2">
            <ElButton size="small" :loading="loadingSetl" @click="loadSetl">查询结算</ElButton>
          </div>
          <ElDescriptions v-if="setlResult?.header" :column="2" border class="mb-3" size="small">
            <ElDescriptionsItem label="医疗费总额">
              {{ rowField(setlResult.header, 'medfee_sumamt', 'medfeeSumamt') }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="基金支付总额">
              {{ rowField(setlResult.header, 'fund_pay_sumamt', 'fundPaySumamt') }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="个人账户">
              {{ rowField(setlResult.header, 'acct_pay', 'acctPay') }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="现金支付">
              {{ rowField(setlResult.header, 'cash_payamt', 'cashPayamt') }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="结算时间">
              {{ rowField(setlResult.header, 'setl_time', 'setlTime') }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="退费标志">
              {{ rowField(setlResult.header, 'refd_setl_flag', 'refdSetlFlag') }}
            </ElDescriptionsItem>
          </ElDescriptions>
          <ElTable
            :data="setlResult?.setlDetails ?? []"
            stripe
            border
            max-height="320"
            empty-text="暂无基金分项"
          >
            <ElTableColumn label="基金类型" min-width="140">
              <template #default="{ row }">
                {{
                  rowField(
                    row,
                    'fund_pay_type_name',
                    'fundPayTypeName',
                    'fund_pay_type',
                    'fundPayType',
                  )
                }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="基金支付" width="120">
              <template #default="{ row }">{{
                rowField(row, 'fund_payamt', 'fundPayamt')
              }}</template>
            </ElTableColumn>
            <ElTableColumn label="符合范围金额" width="130">
              <template #default="{ row }">{{
                rowField(row, 'inscp_scp_amt', 'inscpScpAmt')
              }}</template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <ElTabPane label="费用明细 (5204)" name="fee">
          <div class="mb-2">
            <ElButton size="small" :loading="loadingFee" @click="loadFee">查询明细</ElButton>
          </div>
          <ElTable :data="feeRows" stripe border max-height="420" empty-text="暂无数据">
            <ElTableColumn label="目录编码" min-width="130">
              <template #default="{ row }">{{
                rowField(row, 'hilist_code', 'hilistCode')
              }}</template>
            </ElTableColumn>
            <ElTableColumn label="目录名称" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{
                rowField(row, 'hilist_name', 'hilistName')
              }}</template>
            </ElTableColumn>
            <ElTableColumn label="金额" width="100">
              <template #default="{ row }">
                {{ rowField(row, 'det_item_fee_sumamt', 'detItemFeeSumamt') }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="数量" width="80">
              <template #default="{ row }">{{ rowField(row, 'cnt') }}</template>
            </ElTableColumn>
            <ElTableColumn label="单价" width="90">
              <template #default="{ row }">{{ rowField(row, 'pric') }}</template>
            </ElTableColumn>
            <ElTableColumn label="等级" width="80">
              <template #default="{ row }">
                {{ formatChrgitmLv(rowField(row, 'chrgitm_lv', 'chrgitmLv')) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="费用时间" min-width="160">
              <template #default="{ row }">{{
                rowField(row, 'fee_ocur_time', 'feeOcurTime')
              }}</template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>
      </ElTabs>
    </ElDrawer>
  </Page>
</template>
