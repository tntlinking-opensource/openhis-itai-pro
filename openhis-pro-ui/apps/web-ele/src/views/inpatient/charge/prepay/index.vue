<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { PrepayPaymentRecordDto, PrepayQueueItemDto } from '#/api/inpatient-prepay';
import type { BillingPatientCardPatient } from '#/components/charge/billing-patient-card.types';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { useUserStore } from '@vben/stores';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElTag,
} from 'element-plus';

import { useDepositReceiptPrint } from '#/composables/use-deposit-receipt-print';
import {
  httpCollectPrepayAdvanceDeposit,
  httpListPrepayPaymentRecords,
  httpPagePrepayPatientQueue,
  httpRefundPrepayAdvanceDeposit,
} from '#/api/inpatient-prepay';
import AppDialog from '#/components/app-dialog.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/preserve-api-id';

import InpatientQueuePanel from './components/inpatient-queue-panel.vue';
import {
  DEFAULT_PAY_CHANNEL_CODE,
  PAY_CHANNEL_OPTIONS,
  PREPAY_ALLOWED_ENCOUNTER_STATUSES,
} from './inpatient-prepay.constants';

defineOptions({
  name: 'ChargeIpPrepay',
});

const userStore = useUserStore();

const queueFilter = reactive({
  admissionDate: '',
  keyword: '',
});

const queuePager = reactive({
  pageNo: 1,
  pageSize: 50,
});

const prepayForm = reactive({
  amount: '',
  payChannelCode: DEFAULT_PAY_CHANNEL_CODE,
  remark: '',
});

const refundForm = reactive({
  amount: '',
  payChannelCode: DEFAULT_PAY_CHANNEL_CODE,
  remark: '',
});

const recordTableQueryParams = reactive({
  pageNo: 1,
  pageSize: 20,
});

const queueData = ref<PrepayQueueItemDto[]>([]);
const prepayRecordData = ref<PrepayPaymentRecordDto[]>([]);
const selectedEncounterId = ref('');
const queueLoading = ref(false);
const tableLoading = ref(false);
const collectSubmitting = ref(false);
const refundSubmitting = ref(false);
const refundDialogVisible = ref(false);

const { isPrinting, previewLoading, printDepositReceipt } = useDepositReceiptPrint();

const quickAmounts = [100, 200, 500, 1000, 2000];

const recordTableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 60, align: 'center', headerAlign: 'center' },
  { prop: 'tradeTime', label: '收费时间', minWidth: 170, align: 'center', headerAlign: 'center' },
  { prop: 'bizTypeText', label: '业务类型', minWidth: 110, align: 'center', headerAlign: 'center' },
  {
    prop: 'amount',
    label: '金额(元)',
    minWidth: 110,
    slot: 'amount',
    align: 'right',
    headerAlign: 'center',
  },
  {
    prop: 'payMethodText',
    label: '支付方式',
    minWidth: 110,
    align: 'center',
    headerAlign: 'center',
  },
  { prop: 'operatorName', label: '操作员', minWidth: 100, align: 'center', headerAlign: 'center' },
  { prop: 'ticketNo', label: '票据号', minWidth: 150, align: 'center', headerAlign: 'center' },
  {
    prop: 'statusEnumText',
    label: '状态',
    minWidth: 90,
    slot: 'status',
    align: 'center',
    headerAlign: 'center',
  },
  {
    label: '操作',
    minWidth: 90,
    slot: 'actions',
    align: 'center',
    headerAlign: 'center',
    fixed: 'right',
  },
];

const currentPatient = computed(
  () => queueData.value.find((item) => item.encounterId === selectedEncounterId.value) ?? null,
);

const queueCardList = computed<BillingPatientCardPatient[]>(() =>
  queueData.value.map((row) => ({
    ageText: row.ageText,
    doctorName: '',
    gender: row.genderEnumText ?? '',
    id: row.encounterId,
    inpatientNo: row.encounterBusNo,
    insuranceType: row.contractDisplay ?? '',
    medicalRecordNo: row.encounterBusNo,
    patientName: row.patientName,
    visitDepartment: [row.organizationName, row.bedName ? `${row.bedName}床` : '']
      .filter(Boolean)
      .join(' / '),
    visitTime: row.startTime ?? '',
    visitType: 'inpatient',
  })),
);

const canOperatePrepay = computed(() => {
  const status = currentPatient.value?.statusEnum ?? '';
  return (PREPAY_ALLOWED_ENCOUNTER_STATUSES as readonly string[]).includes(status);
});

const currentPatientStatusTagType = computed(() => {
  if (!currentPatient.value) return 'info';
  if (currentPatient.value.arrearsAlert) return 'danger';
  return 'success';
});

/** 将余额格式化为展示文案 */
function formatBalanceDisplay(value: number | string | undefined): string {
  if (value === undefined || value === null || value === '') return '-';
  const num = Number(value);
  if (!Number.isFinite(num)) return String(value);
  return `¥${num.toFixed(2)}`;
}

/** 预交金流水金额：退预交金（UN_PAY）前加负号 */
function formatPrepayRecordAmount(row: PrepayPaymentRecordDto): string {
  const num = Math.abs(Number(row.amount));
  if (!Number.isFinite(num)) return '¥-';
  const isRefund = row.paymentEnum === 'UN_PAY' || row.bizTypeText === '退预交金';
  return isRefund ? `¥-${num.toFixed(2)}` : `¥${num.toFixed(2)}`;
}

/** 解析金额输入为正数，非法时返回 null */
function parsePositiveAmount(raw: string): null | number {
  const num = Number(String(raw ?? '').trim());
  if (!Number.isFinite(num) || !(num > 0)) return null;
  return num;
}

/** 将选中患者余额写回队列行（收退成功后本地即时刷新） */
function patchQueueAccountBalance(encounterId: string, accountBalance: number | string) {
  const row = queueData.value.find((item) => item.encounterId === encounterId);
  if (row) {
    row.accountBalance = accountBalance;
  }
}

/** 根据左侧筛选条件刷新在院患者队列 */
async function queryQueueData() {
  queueLoading.value = true;
  try {
    const result = await httpPagePrepayPatientQueue({
      keyword: queueFilter.keyword.trim() || undefined,
      admissionDate: queueFilter.admissionDate.trim() || undefined,
      pageNo: queuePager.pageNo,
      pageSize: queuePager.pageSize,
    });
    queueData.value = result.content ?? [];
    if (!selectedEncounterId.value && queueData.value.length > 0) {
      selectedEncounterId.value = queueData.value[0]?.encounterId ?? '';
    }
    if (!queueData.value.some((item) => item.encounterId === selectedEncounterId.value)) {
      selectedEncounterId.value = queueData.value[0]?.encounterId ?? '';
    }
  } finally {
    queueLoading.value = false;
  }
}

/** 解析当前选中就诊对应的患者 ID（队列卡片优先） */
function resolveSelectedPatientId(): string | undefined {
  const row = queueData.value.find((item) => item.encounterId === selectedEncounterId.value);
  return preserveApiIdForRequest(row?.patientId ?? currentPatient.value?.patientId);
}

/** 按当前就诊刷新预交金流水 */
async function queryPrepayRecords() {
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  if (!encounterId) {
    prepayRecordData.value = [];
    return;
  }
  const patientId = resolveSelectedPatientId();
  tableLoading.value = true;
  try {
    const result = await httpListPrepayPaymentRecords({
      encounterId,
      patientId,
    });
    prepayRecordData.value = result.records ?? [];
  } finally {
    tableLoading.value = false;
  }
}

/** 左侧切换患者并触发右侧数据联动 */
function handleChoosePatient(patient: BillingPatientCardPatient) {
  selectedEncounterId.value = patient.id;
}

/** 刷新左侧患者队列 */
async function handleRefreshQueue() {
  await queryQueueData();
  ElMessage.success('在院患者列表已刷新');
}

/** 快捷金额用于快速写入预交金金额 */
function handleApplyQuickAmount(amount: number) {
  prepayForm.amount = String(amount);
}

/** 打开退预交金弹窗 */
function handleOpenRefundDialog() {
  if (!selectedEncounterId.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (!canOperatePrepay.value) {
    ElMessage.warning('当前就诊状态不可办理预交金退费');
    return;
  }
  refundForm.amount = '';
  refundForm.payChannelCode = DEFAULT_PAY_CHANNEL_CODE;
  refundForm.remark = '';
  refundDialogVisible.value = true;
}

/** 提交收取预交金并刷新余额与流水 */
async function handleCollectPrepay() {
  const amount = parsePositiveAmount(prepayForm.amount);
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  const patientId = preserveApiIdForRequest(currentPatient.value?.patientId);
  if (!encounterId || !patientId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (!canOperatePrepay.value) {
    ElMessage.warning('当前就诊状态不可收取预交金');
    return;
  }
  if (amount === null) {
    ElMessage.warning('请输入正确的收费金额');
    return;
  }
  collectSubmitting.value = true;
  try {
    const result = await httpCollectPrepayAdvanceDeposit({
      encounterId,
      patientId,
      amount,
      paymentDetails: [
        {
          payChannelCode: prepayForm.payChannelCode,
          amount,
        },
      ],
      operatorId: resolveLoginPractitionerIdForRequest(userStore.userInfo),
      remark: prepayForm.remark.trim() || undefined,
    });
    patchQueueAccountBalance(encounterId, result.accountBalance);
    prepayForm.amount = '';
    prepayForm.remark = '';
    await queryPrepayRecords();
    ElMessage.success('预交金收取成功');
  } finally {
    collectSubmitting.value = false;
  }
}

/** 提交退预交金并刷新余额与流水 */
async function handleConfirmRefund() {
  const refundAmount = parsePositiveAmount(refundForm.amount);
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  const patientId = preserveApiIdForRequest(currentPatient.value?.patientId);
  if (!encounterId || !patientId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (refundAmount === null) {
    ElMessage.warning('请输入正确的退费金额');
    return;
  }
  const balance = Number(currentPatient.value?.accountBalance ?? 0);
  if (Number.isFinite(balance) && refundAmount > balance) {
    ElMessage.warning('退费金额不能大于账户余额');
    return;
  }
  refundSubmitting.value = true;
  try {
    const result = await httpRefundPrepayAdvanceDeposit({
      encounterId,
      patientId,
      refundAmount,
      paymentDetails: [
        {
          payChannelCode: refundForm.payChannelCode,
          amount: refundAmount,
        },
      ],
      operatorId: resolveLoginPractitionerIdForRequest(userStore.userInfo),
      remark: refundForm.remark.trim() || undefined,
    });
    patchQueueAccountBalance(encounterId, result.accountBalance);
    refundDialogVisible.value = false;
    await queryPrepayRecords();
    ElMessage.success('预交金退费成功');
  } finally {
    refundSubmitting.value = false;
  }
}

/** 打印指定流水收据 */
function handlePrintRecord(row: PrepayPaymentRecordDto) {
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (row.statusEnum !== 'SUCCESS') {
    ElMessage.warning('仅成功流水可打印收据');
    return;
  }
  void printDepositReceipt({
    paymentId: row.paymentId,
    encounterId,
    jobName: `预交金-${row.ticketNo || row.paymentId}`,
  });
}

/** 打印最近一条成功流水 */
function handlePrintTicket() {
  const encounterId = preserveApiIdForRequest(selectedEncounterId.value);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const latest = prepayRecordData.value.find((row) => row.statusEnum === 'SUCCESS');
  if (!latest) {
    ElMessage.warning('暂无可打印的预交金流水');
    return;
  }
  handlePrintRecord(latest);
}

watch(
  () => [selectedEncounterId.value, queueData.value] as const,
  () => {
    void queryPrepayRecords();
  },
);

watch(
  () => [queueFilter.keyword, queueFilter.admissionDate],
  () => {
    queuePager.pageNo = 1;
    queryQueueData();
  },
);

onMounted(async () => {
  await queryQueueData();
  await queryPrepayRecords();
});
</script>

<template>
  <div class="flex h-full min-h-0 flex-col overflow-hidden">
    <StationTriPaneLayout
      :show-right="false"
      :show-footer="false"
      class="charge-inpatient-prepay-workbench min-h-0 flex-1"
      left-track="minmax(300px, 0.34fr)"
      center-track="minmax(0, 1fr)"
    >
      <template #left>
        <InpatientQueuePanel
          v-model:keyword="queueFilter.keyword"
          v-model:date="queueFilter.admissionDate"
          :list="queueCardList"
          :selected-id="selectedEncounterId"
          :loading="queueLoading"
          @select="handleChoosePatient"
          @refresh="handleRefreshQueue"
        />
      </template>

      <template #center>
        <ElCard
          shadow="never"
          class="flex h-full min-h-0 flex-col overflow-hidden rounded-lg"
          body-class="flex-1 min-h-0 overflow-hidden !p-2"
        >
          <div class="flex h-full min-h-0 flex-col gap-3 overflow-hidden">
            <SectionTitle title="患者信息" />
            <div class="patient-info-bar">
              <div class="patient-info-grid">
                <div class="patient-info-item">
                  <span class="patient-info-label">住院号</span>
                  <span class="patient-info-value">{{
                    currentPatient?.encounterBusNo || '-'
                  }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">姓名</span>
                  <span class="patient-info-value">{{ currentPatient?.patientName || '-' }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">科室</span>
                  <span class="patient-info-value">{{
                    currentPatient?.organizationName || '-'
                  }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">病区</span>
                  <span class="patient-info-value">{{ currentPatient?.wardName || '-' }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">床号</span>
                  <span class="patient-info-value">{{ currentPatient?.bedName || '-' }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">账户余额</span>
                  <span class="patient-info-value text-blue-600">
                    {{ formatBalanceDisplay(currentPatient?.accountBalance) }}
                  </span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">就诊状态</span>
                  <ElTag :type="currentPatientStatusTagType" size="small">
                    {{ currentPatient?.statusEnumText || '未选择' }}
                  </ElTag>
                  <ElTag
                    v-if="currentPatient?.arrearsAlert"
                    type="danger"
                    size="small"
                    class="ml-1"
                  >
                    欠费预警
                  </ElTag>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">费用性质</span>
                  <span class="patient-info-value">{{
                    currentPatient?.contractDisplay || '-'
                  }}</span>
                </div>
              </div>
            </div>

            <div class="prepay-toolbar">
              <div class="flex items-center gap-2">
                <SectionTitle title="预交金办理" />
                <div class="text-xs text-[var(--el-text-color-regular)]">快捷金额</div>
                <ElButton
                  v-for="item in quickAmounts"
                  :key="item"
                  size="small"
                  plain
                  :disabled="!canOperatePrepay"
                  @click="handleApplyQuickAmount(item)"
                >
                  {{ item }}
                </ElButton>
              </div>
              <div class="flex items-center gap-2">
                <ElButton
                  type="primary"
                  :loading="collectSubmitting"
                  :disabled="!canOperatePrepay"
                  @click="handleCollectPrepay"
                >
                  收取预交金
                </ElButton>
                <ElButton plain :disabled="!canOperatePrepay" @click="handleOpenRefundDialog">
                  退预交金
                </ElButton>
                <ElButton
                  plain
                  :loading="isPrinting || previewLoading"
                  :disabled="!canOperatePrepay"
                  @click="handlePrintTicket"
                >
                  打印凭证
                </ElButton>
              </div>
            </div>

            <ElForm :model="prepayForm" inline class="prepay-form">
              <ElFormItem label="收费金额" required>
                <ElInput
                  v-model="prepayForm.amount"
                  placeholder="请输入金额"
                  class="w-52"
                  clearable
                  :disabled="!canOperatePrepay"
                />
              </ElFormItem>
              <ElFormItem label="支付方式" required>
                <ElRadioGroup v-model="prepayForm.payChannelCode" :disabled="!canOperatePrepay">
                  <ElRadio
                    v-for="item in PAY_CHANNEL_OPTIONS"
                    :key="item.value"
                    :label="item.value"
                  >
                    {{ item.label }}
                  </ElRadio>
                </ElRadioGroup>
              </ElFormItem>
              <ElFormItem label="备注">
                <ElInput
                  v-model="prepayForm.remark"
                  placeholder="可选填写"
                  class="w-80"
                  clearable
                  :disabled="!canOperatePrepay"
                />
              </ElFormItem>
            </ElForm>

            <div class="min-h-0 flex-1 overflow-hidden">
              <SectionTitle class="mb-2" title="预交金流水" />
              <TableLayout
                :table-data="prepayRecordData"
                :loading="tableLoading"
                :total="prepayRecordData.length"
                :query-params="recordTableQueryParams"
                :table-columns="recordTableColumns"
                :show-top-query="false"
                :show-pagination="false"
                border
                stripe
              >
                <template #amount="{ row }">
                  <span :class="row.paymentEnum === 'UN_PAY' ? 'text-orange-600' : ''">
                    {{ formatPrepayRecordAmount(row) }}
                  </span>
                </template>
                <template #status="{ row }">
                  <ElTag :type="row.statusEnum === 'SUCCESS' ? 'success' : 'info'" effect="light">
                    {{ row.statusEnumText }}
                  </ElTag>
                </template>
                <template #actions="{ row }">
                  <ElButton
                    v-if="row.statusEnum === 'SUCCESS'"
                    link
                    type="primary"
                    :loading="isPrinting || previewLoading"
                    @click="handlePrintRecord(row)"
                  >
                    打印
                  </ElButton>
                </template>
              </TableLayout>
            </div>
          </div>
        </ElCard>
      </template>
    </StationTriPaneLayout>

    <AppDialog v-model="refundDialogVisible" title="退预交金" width="520px">
      <ElForm :model="refundForm" label-width="96px">
        <ElFormItem label="退费金额" required>
          <ElInput v-model="refundForm.amount" placeholder="请输入退费金额" clearable />
        </ElFormItem>
        <ElFormItem label="退现渠道" required>
          <ElRadioGroup v-model="refundForm.payChannelCode">
            <ElRadio v-for="item in PAY_CHANNEL_OPTIONS" :key="item.value" :label="item.value">
              {{ item.label }}
            </ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="refundForm.remark" placeholder="可选填写" clearable />
        </ElFormItem>
        <ElFormItem label="账户余额">
          <span class="text-[var(--el-text-color-primary)]">
            {{ formatBalanceDisplay(currentPatient?.accountBalance) }}
          </span>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="refundDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="refundSubmitting" @click="handleConfirmRefund">
          确认退费
        </ElButton>
      </template>
    </AppDialog>
  </div>
</template>

<style scoped>
.patient-info-bar {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-light);
  padding: 10px 12px;
}

.patient-info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 16px;
}

.patient-info-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.patient-info-label {
  color: var(--el-text-color-regular);
  font-size: 14px;
  white-space: nowrap;
}

.patient-info-value {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 500;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prepay-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.prepay-form {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 0;
}
</style>
