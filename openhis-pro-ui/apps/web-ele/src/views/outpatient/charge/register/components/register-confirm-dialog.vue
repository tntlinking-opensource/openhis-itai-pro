<script setup lang="ts">
import type {
  ConfirmDetailSection,
  PreRegisterSettlementResult,
  RegisterConfirmDetail,
} from '../services';

import { computed, ref } from 'vue';

import { ElButton, ElMessage } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';

import { DEFAULT_REGISTER_PAY_CHANNEL_CODE, payRegisterFee } from '../services/register.service';

defineOptions({
  name: 'RegisterConfirmDialog',
});

const emit = defineEmits<{
  /** 正式结算成功且弹窗已关闭后，由父级重置表单并刷新就诊记录 */
  success: [];
}>();

const visible = ref(false);
const payLoading = ref(false);
const detail = ref<null | RegisterConfirmDetail>(null);
const pendingPreSettlement = ref<null | PreRegisterSettlementResult>(null);

const registerIntro = '请核对以下信息，确认后将按预结算金额发起收费。';

/**
 * 将性别编码转为弹窗展示文案。
 */
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

/**
 * 空串统一为「—」。
 */
function displayDash(value: null | string | undefined): string {
  const t = String(value ?? '').trim();
  return t || '—';
}

/**
 * 将当前核对明细映射为共用「分区块 + 栅格」结构。
 */
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

/**
 * 由父组件在预结算成功后打开：写入展示明细与草稿上下文。
 */
function open(nextDetail: RegisterConfirmDetail, pre: PreRegisterSettlementResult) {
  detail.value = nextDetail;
  pendingPreSettlement.value = pre;
  visible.value = true;
}

/**
 * 关闭弹窗并清空暂存的预结算上下文（取消或成功收尾）。
 */
function resetState() {
  visible.value = false;
  detail.value = null;
  pendingPreSettlement.value = null;
}

/**
 * 弹窗关闭动画结束后释放引用，避免误用过期预结算。
 */
function onDialogClosed() {
  detail.value = null;
  pendingPreSettlement.value = null;
  payLoading.value = false;
}

/**
 * 弹窗内确认缴费：调用正式结算后通知父级做表单与列表收尾。
 */
async function executeRegisterPayment() {
  const pre = pendingPreSettlement.value;
  const d = detail.value;
  if (!pre?.paymentDraftId || !d) {
    return;
  }
  payLoading.value = true;
  try {
    await payRegisterFee({
      appointmentId: pre.appointmentId != null ? String(pre.appointmentId) : undefined,
      paymentDetails: [
        {
          payAmount: d.totalAmount,
          payChannelCode: DEFAULT_REGISTER_PAY_CHANNEL_CODE,
        },
      ],
      paymentDraftId: pre.paymentDraftId,
    });
    resetState();
    ElMessage.success('挂号成功！');
    emit('success');
  } finally {
    payLoading.value = false;
  }
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
          :class="[
            'mb-1 border-0 border-b border-solid border-[var(--el-border-color-lighter)] pb-1 text-[14px] font-semibold text-[var(--el-text-color-primary)]',
            sIdx > 0 ? 'mt-4' : '',
          ]"
        >
          {{ section.title }}
        </div>
        <div class="grid grid-cols-[96px_1fr] gap-x-2 gap-y-1 py-0.5">
          <template v-for="row in section.rows" :key="row.label">
            <span class="text-[var(--el-text-color-regular)]">{{ row.label }}</span>
            <span
              :class="[
                'text-[var(--el-text-color-primary)]',
                row.breakAll ? 'break-all' : '',
                row.medium ? 'font-medium' : '',
              ]"
            >
              {{ row.value }}
            </span>
          </template>
        </div>
      </template>

      <div
        class="mt-4 rounded bg-[var(--el-fill-color-light)] px-2 py-2 text-[14px] font-semibold text-[var(--el-color-primary)]"
      >
        挂号金额（预结算）：¥{{ detail.totalAmount.toFixed(2) }}
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <ElButton :disabled="payLoading" @click="visible = false"> 取消 </ElButton>
        <ElButton type="primary" :loading="payLoading" @click="executeRegisterPayment">
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
