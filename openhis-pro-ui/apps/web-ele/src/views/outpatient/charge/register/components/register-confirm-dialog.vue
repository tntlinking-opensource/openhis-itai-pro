<script setup lang="ts">
import type {
  ConfirmDetailSection,
  PreRegisterSettlementResult,
  RegisterConfirmDetail,
} from '../services';

import { computed, ref } from 'vue';

import { ElAlert, ElButton, ElInput, ElMessage, ElRadioButton, ElRadioGroup } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import { isScanPayChannel } from '#/composables/payment/useUnionPayScanPay';
import { settleWithScanRecovery } from '#/utils/payment/scan-settle-recovery';
import { submitScanPayment } from '#/utils/payment/submit-scan-payment';
import { schedulePrintRegistrationReceipt } from '#/utils/print/print-registration-receipt';

import { useRegisterPayChannels } from '../composables/use-register-pay-channels';

import {
  formatInsuranceCredentialExpiredHint,
  isEcVoucherMediumType,
  isEcVoucherStale,
  isInsuranceCredentialExpiredError,
} from '../services/register-insurance-credential';
import { DEFAULT_REGISTER_PAY_CHANNEL_CODE, payRegisterFee } from '../services/register.service';

defineOptions({
  name: 'RegisterConfirmDialog',
});

const emit = defineEmits<{
  cancel: [];
  success: [];
}>();

type RescanRefreshHandler = (
  currentPre: PreRegisterSettlementResult,
) => Promise<null | PreRegisterSettlementResult>;

const visible = ref(false);
const payLoading = ref(false);
const refreshLoading = ref(false);
const credentialExpiredHint = ref(false);
const payChannelCode = ref(DEFAULT_REGISTER_PAY_CHANNEL_CODE);
const scanCode = ref('');
const scanStatusText = ref('');

const { scanPayEnabled, effectivePayChannelOptions, ensurePayChannelsLoaded } =
  useRegisterPayChannels();

const showScanPaySection = computed(
  () => scanPayEnabled.value && isScanPayChannel(payChannelCode.value),
);
const pendingPreSettlement = ref<null | PreRegisterSettlementResult>(null);
const detail = ref<null | RegisterConfirmDetail>(null);
const insuranceMediumType = ref('');
const ybCredentialReadAt = ref<null | number>(null);
const rescanAndRefreshHandler = ref<null | RescanRefreshHandler>(null);

const insurancePreSettled = computed(() =>
  Boolean(
    (pendingPreSettlement.value as null | { insurancePreSettled?: boolean })?.insurancePreSettled,
  ),
);
const insuranceFundPay = computed(() =>
  Number(
    (pendingPreSettlement.value as null | { ybFundPaySumamt?: unknown })?.ybFundPaySumamt ?? 0,
  ),
);
const insuranceAcctPay = computed(() =>
  Number((pendingPreSettlement.value as null | { ybAcctPay?: unknown })?.ybAcctPay ?? 0),
);
const payableCashAmount = computed(() => {
  const raw = pendingPreSettlement.value as null | {
    totalAmount?: unknown;
    ybPsnCashPay?: unknown;
  };
  if (insurancePreSettled.value) {
    const cash = Number(raw?.ybPsnCashPay ?? 0);
    return Number.isFinite(cash) ? Math.max(0, cash) : 0;
  }
  const total = Number(raw?.totalAmount ?? detail.value?.totalAmount ?? 0);
  return Number.isFinite(total) ? Math.max(0, total) : 0;
});
const showCashPaySection = computed(() => payableCashAmount.value > 0.001);
const ecVoucherStale = computed(
  () =>
    insurancePreSettled.value &&
    isEcVoucherMediumType(insuranceMediumType.value) &&
    isEcVoucherStale(ybCredentialReadAt.value),
);
const showRescanSection = computed(
  () => insurancePreSettled.value && isEcVoucherMediumType(insuranceMediumType.value),
);

const registerIntro = '请核对以下信息，确认后将按预结算金额发起收费。';

function formatGenderForConfirm(code: string): string {
  const c = String(code ?? '').trim();
  if (c === 'MALE' || c === '1') {
    return '男性';
  }
  if (c === 'FEMALE' || c === '2') {
    return '女性';
  }
  if (c === 'UNKNOWN') {
    return '未知';
  }
  return c || '—';
}

function displayDash(value: null | string | undefined): string {
  const t = String(value ?? '').trim();
  return t || '—';
}

const confirmSections = computed<ConfirmDetailSection[]>(() => {
  const d = detail.value;
  if (!d) {
    return [];
  }
  return [
    {
      title: '患者信息',
      rows: [
        { label: '病历号', value: displayDash(d.medicalRecordNo), breakAll: true },
        { label: '姓名', value: displayDash(d.patientName), breakAll: true },
        { label: '性别', value: formatGenderForConfirm(d.patientGender) },
        { label: '出生日期', value: displayDash(d.patientBirthday) },
        { label: '年龄', value: displayDash(d.patientAge) },
        { label: '身份证号', value: displayDash(d.patientIdCard), breakAll: true },
        { label: '联系电话', value: displayDash(d.patientPhone), breakAll: true },
      ],
    },
    {
      title: '挂号信息',
      rows: [
        { label: '挂号科室', value: displayDash(d.departmentLabel), breakAll: true },
        { label: '接诊医生', value: displayDash(d.doctorLabel), breakAll: true },
        { label: '费用性质', value: displayDash(d.contractLabel), breakAll: true },
        { label: '挂号类型', value: displayDash(d.serviceTypeLabel), breakAll: true },
      ],
    },
    {
      title: '费用明细',
      rows: [
        {
          label: '号源类型',
          value: displayDash(d.serviceTypeLabel),
          breakAll: true,
          medium: true,
        },
      ],
    },
  ];
});

function applyPreSettlement(pre: PreRegisterSettlementResult) {
  pendingPreSettlement.value = pre;
  let cash = Number(pre.totalAmount ?? 0);
  if (pre.insurancePreSettled) {
    const psnCash = Number((pre as { ybPsnCashPay?: unknown }).ybPsnCashPay ?? 0);
    cash = Number.isFinite(psnCash) ? psnCash : cash;
  }
  if (detail.value) {
    detail.value = { ...detail.value, totalAmount: Number.isFinite(cash) ? cash : 0 };
  }
  credentialExpiredHint.value = false;
}

function open(
  nextDetail: RegisterConfirmDetail,
  pre: PreRegisterSettlementResult,
  options?: {
    insuranceMediumType?: string;
    rescanAndRefresh?: RescanRefreshHandler;
    ybCredentialReadAt?: null | number;
  },
) {
  detail.value = nextDetail;
  pendingPreSettlement.value = pre;
  insuranceMediumType.value = options?.insuranceMediumType ?? '';
  ybCredentialReadAt.value = options?.ybCredentialReadAt ?? null;
  rescanAndRefreshHandler.value = options?.rescanAndRefresh ?? null;
  credentialExpiredHint.value = false;
  scanCode.value = '';
  scanStatusText.value = '';
  payChannelCode.value = DEFAULT_REGISTER_PAY_CHANNEL_CODE;
  visible.value = true;
  void ensurePayChannelsLoaded();
}

function resetState() {
  visible.value = false;
  detail.value = null;
  pendingPreSettlement.value = null;
  rescanAndRefreshHandler.value = null;
  credentialExpiredHint.value = false;
}

function handleCancelDialog() {
  visible.value = false;
  emit('cancel');
}

function onDialogClosed() {
  detail.value = null;
  pendingPreSettlement.value = null;
  rescanAndRefreshHandler.value = null;
  payLoading.value = false;
  refreshLoading.value = false;
  scanCode.value = '';
  scanStatusText.value = '';
  credentialExpiredHint.value = false;
}

function resolvePayError(error: unknown): { code?: string; message: string } {
  if (error && typeof error === 'object') {
    const row = error as {
      code?: string;
      message?: string;
      response?: { data?: { code?: string; message?: string } };
    };
    const data = row.response?.data;
    return {
      code: data?.code ?? row.code,
      message: String(data?.message ?? row.message ?? ''),
    };
  }
  return { message: String(error ?? '') };
}

async function handleRescanAndRefresh() {
  const handler = rescanAndRefreshHandler.value;
  const pre = pendingPreSettlement.value;
  if (!handler || !pre) {
    ElMessage.warning('当前无法刷新预结算，请关闭弹窗后重新确认挂号');
    return;
  }
  refreshLoading.value = true;
  try {
    const nextPre = await handler(pre);
    if (!nextPre) {
      return;
    }
    applyPreSettlement(nextPre);
    ybCredentialReadAt.value = Date.now();
    ElMessage.success('已刷新医保预结算');
  } finally {
    refreshLoading.value = false;
  }
}

function ensureCredentialFreshBeforePay(): boolean {
  if (ecVoucherStale.value) {
    credentialExpiredHint.value = true;
    ElMessage.warning(formatInsuranceCredentialExpiredHint());
    return false;
  }
  return true;
}

async function executeRegisterPayment() {
  const pre = pendingPreSettlement.value;
  const d = detail.value;
  if (!pre?.paymentDraftId || !d) {
    return;
  }
  if (!ensureCredentialFreshBeforePay()) {
    return;
  }
  if (isScanPayChannel(payChannelCode.value) && !scanCode.value.trim()) {
    ElMessage.warning('请输入付款码');
    return;
  }
  payLoading.value = true;
  scanStatusText.value = '';
  try {
    let clientScanResult;
    if (isScanPayChannel(payChannelCode.value)) {
      if (!scanPayEnabled.value) {
        ElMessage.error('请启动本地客户端（OpenHIS.Win）并确认银联组件已安装');
        return;
      }
      scanStatusText.value = '等待支付结果…';
      const scanOutcome = await submitScanPayment({
        paymentDraftId: pre.paymentDraftId,
        amount: payableCashAmount.value,
        scanCode: scanCode.value,
      });
      clientScanResult = scanOutcome.clientScanResult;
    }

    const result = clientScanResult
      ? await settleWithScanRecovery(clientScanResult, () =>
          payRegisterFee({
            appointmentId:
              pre.appointmentId === null || pre.appointmentId === undefined
                ? undefined
                : String(pre.appointmentId),
            paymentDetails: showCashPaySection.value
              ? [
                  {
                    payAmount: payableCashAmount.value,
                    payChannelCode: payChannelCode.value,
                  },
                ]
              : [],
            paymentDraftId: pre.paymentDraftId,
            clientScanResult,
          }),
        )
      : await payRegisterFee({
          appointmentId:
            pre.appointmentId === null || pre.appointmentId === undefined
              ? undefined
              : String(pre.appointmentId),
          paymentDetails: showCashPaySection.value
            ? [
                {
                  payAmount: payableCashAmount.value,
                  payChannelCode: payChannelCode.value,
                },
              ]
            : [],
          paymentDraftId: pre.paymentDraftId,
        });
    ElMessage.success('挂号成功！');
    finishAfterPay();
    schedulePrintRegistrationReceipt({
      encounterId: result?.encounterId ?? pre.encounterId,
      paymentId: result?.paymentId,
      jobName: '挂号小票',
    });
  } catch (error) {
    const { code, message } = resolvePayError(error);
    if (isInsuranceCredentialExpiredError(code, message)) {
      credentialExpiredHint.value = true;
      ElMessage.warning(formatInsuranceCredentialExpiredHint());
    }
  } finally {
    payLoading.value = false;
  }
}

function finishAfterPay() {
  resetState();
  emit('success');
}

defineExpose({
  open,
});
</script>

<template>
  <AppDialog
    v-model="visible"
    class="register-confirm-dialog"
    title="确认挂号"
    width="560px"
    top="5vh"
    append-to-body
    destroy-on-close
    @closed="onDialogClosed"
  >
    <div v-if="detail" class="register-confirm-dialog__body text-left text-[14px] leading-relaxed">
      <p class="mb-3 text-[var(--el-text-color-regular)]">{{ registerIntro }}</p>

      <template v-for="(section, sIdx) in confirmSections" :key="`${section.title}-${sIdx}`">
        <div
          class="mb-1 border-0 border-b border-solid border-[var(--el-border-color-lighter)] pb-1 text-[14px] font-semibold text-[var(--el-text-color-primary)]"
          :class="[sIdx > 0 ? 'mt-4' : '']"
        >
          {{ section.title }}
        </div>
        <div class="grid grid-cols-[96px_1fr] gap-x-2 gap-y-1 py-0.5">
          <template v-for="row in section.rows" :key="row.label">
            <span class="text-[var(--el-text-color-regular)]">{{ row.label }}</span>
            <span
              class="text-[var(--el-text-color-primary)]"
              :class="[row.breakAll ? 'break-all' : '', row.medium ? 'font-medium' : '']"
            >
              {{ row.value }}
            </span>
          </template>
        </div>
      </template>

      <div
        class="mt-4 rounded bg-[var(--el-fill-color-light)] px-2 py-2 text-[14px] font-semibold text-[var(--el-color-primary)]"
      >
        挂号金额（预结算）：¥{{ payableCashAmount.toFixed(2) }}
      </div>

      <template v-if="insurancePreSettled">
        <div class="mt-3 space-y-1 text-[13px] text-[var(--el-text-color-regular)]">
          <div>基金支付：¥{{ insuranceFundPay.toFixed(2) }}</div>
          <div>个账支付：¥{{ insuranceAcctPay.toFixed(2) }}</div>
          <div class="font-medium text-[var(--el-color-primary)]">
            个人现金应付：¥{{ payableCashAmount.toFixed(2) }}
          </div>
        </div>
      </template>

      <ElAlert
        v-if="showRescanSection && (ecVoucherStale || credentialExpiredHint)"
        class="mt-3"
        type="warning"
        :closable="false"
        show-icon
        :title="formatInsuranceCredentialExpiredHint()"
      />

      <div v-if="showRescanSection" class="mt-3">
        <ElButton
          type="warning"
          plain
          :loading="refreshLoading"
          :disabled="payLoading"
          @click="handleRescanAndRefresh"
        >
          重新扫医保码并刷新预结算
        </ElButton>
      </div>

      <div v-if="showCashPaySection" class="mt-4">
        <div class="mb-2 text-[14px] font-semibold text-[var(--el-text-color-primary)]">
          支付方式
        </div>
        <ElRadioGroup v-model="payChannelCode" class="flex flex-wrap gap-2">
          <ElRadioButton
            v-for="opt in effectivePayChannelOptions"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </ElRadioButton>
        </ElRadioGroup>
      </div>

      <div v-if="showScanPaySection" class="mt-3">
        <div class="mb-2 text-[14px] font-semibold text-[var(--el-text-color-primary)]">
          扫码支付
        </div>
        <ElInput
          v-model="scanCode"
          placeholder="请扫码或输入付款码"
          clearable
          @keyup.enter="executeRegisterPayment"
        />
        <p v-if="scanStatusText" class="mt-2 text-[13px] text-blue-600">{{ scanStatusText }}</p>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <ElButton :disabled="payLoading || refreshLoading" @click="handleCancelDialog">
          取消
        </ElButton>
        <ElButton
          type="primary"
          :loading="payLoading"
          :disabled="refreshLoading"
          @click="executeRegisterPayment"
        >
          确认缴费
        </ElButton>
      </div>
    </template>
  </AppDialog>
</template>

<style scoped>
:deep(.register-confirm-dialog .el-dialog__body) {
  max-height: none;
  overflow: visible;
  padding-bottom: 16px;
}
</style>
