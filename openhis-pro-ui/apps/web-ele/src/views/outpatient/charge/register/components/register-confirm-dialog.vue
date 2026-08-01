<script setup lang="ts">
import type { PreRegisterSettlementResult, RegisterConfirmDetail } from '../services';

import { computed, nextTick, ref, watch } from 'vue';

import { ElAlert, ElButton, ElInput, ElMessage, ElRadioButton, ElRadioGroup } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import { isScanPayChannel } from '#/composables/payment/useUnionPayScanPay';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { FALLBACK_DEFAULT_PAY_CHANNEL } from '#/utils/payment/payment-channel-options';
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

import WorkInjuryFilingDetailAction from '#/chs/work-injury/work-injury-filing-detail-action.vue';

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
const scanCodeInputRef = ref<{ focus?: () => void } | null>(null);

const {
  scanPayEnabled,
  hasScanPayChannel,
  effectivePayChannelOptions,
  refreshPayChannelsForDialog,
  refreshUnionPayCapabilities,
} = useRegisterPayChannels();

function focusScanCodeInput() {
  void nextTick(() => {
    requestAnimationFrame(() => {
      scanCodeInputRef.value?.focus?.();
    });
  });
}

function onConfirmDialogOpened() {
  if (showScanPaySection.value) {
    focusScanCodeInput();
  }
}

watch(payChannelCode, (code) => {
  if (visible.value && isScanPayChannel(code)) {
    focusScanCodeInput();
  }
});

const pendingPreSettlement = ref<null | PreRegisterSettlementResult>(null);
const detail = ref<null | RegisterConfirmDetail>(null);
const insuranceMediumType = ref('');
const ybCredentialReadAt = ref<null | number>(null);
const rescanAndRefreshHandler = ref<null | RescanRefreshHandler>(null);
const workInjuryRegister = ref(false);
const workInjuryContractId = ref<string | number | undefined>(undefined);

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
const workInjuryEncounterId = computed(
  () => (pendingPreSettlement.value as null | { encounterId?: string | number })?.encounterId,
);
const showWorkInjuryFiling = computed(
  () =>
    workInjuryRegister.value &&
    workInjuryEncounterId.value != null &&
    workInjuryEncounterId.value !== '' &&
    workInjuryContractId.value != null &&
    workInjuryContractId.value !== '',
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
const showScanPaySection = computed(
  () =>
    showCashPaySection.value && hasScanPayChannel.value && isScanPayChannel(payChannelCode.value),
);
const zeroPayHint = computed(() =>
  insurancePreSettled.value ? '医保已全额覆盖，无需额外现金支付' : '本单金额为 0，无需支付',
);
const ecVoucherStale = computed(
  () =>
    insurancePreSettled.value &&
    isEcVoucherMediumType(insuranceMediumType.value) &&
    isEcVoucherStale(ybCredentialReadAt.value),
);
const showRescanSection = computed(
  () => insurancePreSettled.value && isEcVoucherMediumType(insuranceMediumType.value),
);

function formatGenderShort(code: string): string {
  const c = String(code ?? '').trim();
  if (c === 'MALE' || c === '1') {
    return '男';
  }
  if (c === 'FEMALE' || c === '2') {
    return '女';
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

/** 收款员一眼核对：姓名 · 性别 · 年龄 */
const patientSummary = computed(() => {
  const d = detail.value;
  if (!d) {
    return '—';
  }
  const parts = [
    displayDash(d.patientName),
    formatGenderShort(d.patientGender),
    displayDash(d.patientAge),
  ].filter((p) => p && p !== '—');
  return parts.length > 0 ? parts.join(' · ') : '—';
});

const deptDoctorSummary = computed(() => {
  const d = detail.value;
  if (!d) {
    return '—';
  }
  const dept = displayDash(d.departmentLabel);
  const doctor = displayDash(d.doctorLabel);
  if (dept === '—' && doctor === '—') {
    return '—';
  }
  if (doctor === '—') {
    return dept;
  }
  if (dept === '—') {
    return doctor;
  }
  return `${dept} / ${doctor}`;
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
  payChannelCode.value =
    Number.isFinite(cash) && cash > 0.001
      ? DEFAULT_REGISTER_PAY_CHANNEL_CODE
      : FALLBACK_DEFAULT_PAY_CHANNEL;
  credentialExpiredHint.value = false;
}

async function open(
  nextDetail: RegisterConfirmDetail,
  pre: PreRegisterSettlementResult,
  options?: {
    insuranceMediumType?: string;
    rescanAndRefresh?: RescanRefreshHandler;
    ybCredentialReadAt?: null | number;
    workInjuryRegister?: boolean;
    workInjuryContractId?: string | number;
  },
) {
  detail.value = nextDetail;
  pendingPreSettlement.value = pre;
  insuranceMediumType.value = options?.insuranceMediumType ?? '';
  ybCredentialReadAt.value = options?.ybCredentialReadAt ?? null;
  rescanAndRefreshHandler.value = options?.rescanAndRefresh ?? null;
  workInjuryRegister.value = Boolean(options?.workInjuryRegister);
  workInjuryContractId.value = options?.workInjuryContractId;
  credentialExpiredHint.value = false;
  scanCode.value = '';
  scanStatusText.value = '';
  applyPreSettlement(pre);
  await refreshPayChannelsForDialog();
  visible.value = true;
}

function resetState() {
  visible.value = false;
  detail.value = null;
  pendingPreSettlement.value = null;
  rescanAndRefreshHandler.value = null;
  credentialExpiredHint.value = false;
  workInjuryRegister.value = false;
  workInjuryContractId.value = undefined;
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
  const needScanPay = showCashPaySection.value && isScanPayChannel(payChannelCode.value);
  if (needScanPay && !scanCode.value.trim()) {
    ElMessage.warning('请输入付款码');
    return;
  }
  payLoading.value = true;
  scanStatusText.value = '';
  try {
    let clientScanResult;
    if (needScanPay) {
      await refreshUnionPayCapabilities();
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
    width="480px"
    top="8vh"
    append-to-body
    destroy-on-close
    @opened="onConfirmDialogOpened"
    @closed="onDialogClosed"
  >
    <div v-if="detail" class="register-confirm-dialog__body">
      <div class="register-confirm-dialog__amount">
        <span class="register-confirm-dialog__amount-label">应收金额</span>
        <span class="register-confirm-dialog__amount-value">
          ¥{{ formatMoneyDisplay(payableCashAmount) }}
        </span>
      </div>

      <div class="register-confirm-dialog__meta">
        <div class="register-confirm-dialog__meta-row">
          <span>患者</span>
          <span class="break-all font-medium">{{ patientSummary }}</span>
        </div>
        <div class="register-confirm-dialog__meta-row">
          <span>科室 / 医生</span>
          <span class="break-all">{{ deptDoctorSummary }}</span>
        </div>
        <div class="register-confirm-dialog__meta-row">
          <span>费用性质</span>
          <span class="break-all">{{ displayDash(detail.contractLabel) }}</span>
        </div>
        <div class="register-confirm-dialog__meta-row">
          <span>挂号类型</span>
          <span class="break-all">{{ displayDash(detail.serviceTypeLabel) }}</span>
        </div>
        <template v-if="insurancePreSettled">
          <div class="register-confirm-dialog__meta-row">
            <span>基金支付</span>
            <span>¥{{ formatMoneyDisplay(insuranceFundPay) }}</span>
          </div>
          <div class="register-confirm-dialog__meta-row">
            <span>个账支付</span>
            <span>¥{{ formatMoneyDisplay(insuranceAcctPay) }}</span>
          </div>
        </template>
      </div>

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

      <div class="register-confirm-dialog__pay">
        <div class="register-confirm-dialog__section-title">支付方式</div>
        <ElRadioGroup
          v-if="showCashPaySection"
          v-model="payChannelCode"
          class="register-confirm-dialog__channels"
        >
          <ElRadioButton
            v-for="opt in effectivePayChannelOptions"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </ElRadioButton>
        </ElRadioGroup>
        <p v-else class="register-confirm-dialog__zero-hint">{{ zeroPayHint }}</p>
      </div>

      <div v-if="showScanPaySection" class="register-confirm-dialog__scan">
        <ElInput
          ref="scanCodeInputRef"
          v-model="scanCode"
          size="large"
          clearable
          placeholder="请扫码或输入付款码，回车确认缴费"
          @keyup.enter="executeRegisterPayment"
        />
        <p v-if="scanStatusText" class="register-confirm-dialog__scan-status">
          {{ scanStatusText }}
        </p>
      </div>
    </div>

    <template #footer>
      <div class="flex w-full items-center justify-between gap-2">
        <WorkInjuryFilingDetailAction
          v-if="showWorkInjuryFiling"
          link
          :encounter-id="workInjuryEncounterId"
          :contract-id="workInjuryContractId"
        />
        <div v-else class="flex-1" />
        <div class="flex shrink-0 justify-end gap-2">
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
      </div>
    </template>
  </AppDialog>
</template>

<style scoped>
.register-confirm-dialog__body {
  text-align: left;
  font-size: 14px;
  line-height: 1.5;
}

.register-confirm-dialog__amount {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  margin-bottom: 12px;
  background: var(--el-color-primary-light-9);
  border-radius: 8px;
}

.register-confirm-dialog__amount-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.register-confirm-dialog__amount-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--el-color-primary);
}

.register-confirm-dialog__meta {
  display: grid;
  gap: 6px;
}

.register-confirm-dialog__meta-row {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 8px;
  font-size: 13px;
}

.register-confirm-dialog__meta-row > span:first-child {
  color: var(--el-text-color-secondary);
}

.register-confirm-dialog__meta-row > span:last-child {
  color: var(--el-text-color-primary);
}

.register-confirm-dialog__pay {
  margin-top: 14px;
}

.register-confirm-dialog__section-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.register-confirm-dialog__channels {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.register-confirm-dialog__zero-hint {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.register-confirm-dialog__scan {
  margin-top: 10px;
}

.register-confirm-dialog__scan-status {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--el-color-primary);
}
</style>
