<script setup lang="ts">
import type { BossIssueStatusDto } from '#/api/einvoice-boss/cashier';

import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';

import { ElButton, ElMessage } from 'element-plus';

import { getCashierQrApi, getIssueStatusApi } from '#/api/einvoice-boss/cashier';
import { isEinvoiceUiEnabled } from '#/utils/project-optional-modules';

import EinvoiceQrPanel from './EinvoiceQrPanel.vue';

const props = defineProps<{
  paymentId?: string;
}>();

const POLL_INTERVAL_MS = 3000;
const POLL_TIMEOUT_MS = 120_000;

const { hasAccessByCodes } = useAccess();
const router = useRouter();
const einvoiceUiEnabled = isEinvoiceUiEnabled();

const canView = computed(() => einvoiceUiEnabled && hasAccessByCodes(['einvoice-boss.cashier.qr']));
const status = ref<BossIssueStatusDto | null>(null);
const polling = ref(false);
const pollTimedOut = ref(false);
let pollTimer: ReturnType<typeof setInterval> | null = null;
let pollStartedAt = 0;

const invoiceStatus = computed(() => status.value?.invoiceStatus ?? '');
const taskStatus = computed(() => status.value?.taskStatus ?? '');
const isIssued = computed(() => invoiceStatus.value === 'ISSUED');
const isFailed = computed(() => taskStatus.value === 'FAILED');
const isPending = computed(() => !isIssued.value && !isFailed.value && Boolean(props.paymentId));

const statusText = computed(() => {
  if (isIssued.value) {
    return '已开具';
  }
  if (isFailed.value) {
    return '开票失败';
  }
  if (pollTimedOut.value) {
    return '开票较慢，请稍后扫码或联系收费处';
  }
  return '开票中…';
});

const statusDetail = computed(() => {
  if (isIssued.value && status.value?.billNo) {
    return `票号 ${status.value.billNo}`;
  }
  if (isFailed.value) {
    return '请联系财务处理';
  }
  return '';
});

const qrData = ref<Awaited<ReturnType<typeof getCashierQrApi>> | null>(null);

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  polling.value = false;
}

async function refreshStatus() {
  if (!props.paymentId || !canView.value) {
    return;
  }
  try {
    const result = await getIssueStatusApi(props.paymentId);
    status.value = result ?? null;
    if (result?.invoiceStatus === 'ISSUED') {
      qrData.value = await getCashierQrApi(props.paymentId);
      stopPolling();
      pollTimedOut.value = false;
      return;
    }
    if (result?.taskStatus === 'FAILED') {
      stopPolling();
    }
  } catch {
    // 轮询失败不打断收费主流程
  }
}

function startPolling() {
  stopPolling();
  pollTimedOut.value = false;
  pollStartedAt = Date.now();
  polling.value = true;
  void refreshStatus();
  pollTimer = setInterval(() => {
    if (Date.now() - pollStartedAt >= POLL_TIMEOUT_MS) {
      pollTimedOut.value = true;
      stopPolling();
      return;
    }
    void refreshStatus();
  }, POLL_INTERVAL_MS);
}

function goCompensation() {
  if (!hasAccessByCodes(['einvoice-boss.compensation.view'])) {
    ElMessage.info('暂无待补偿查看权限');
    return;
  }
  router.push('/finance/einvoice/compensation');
}

watch(
  () => props.paymentId,
  (paymentId) => {
    status.value = null;
    qrData.value = null;
    pollTimedOut.value = false;
    if (paymentId && canView.value) {
      startPolling();
    } else {
      stopPolling();
    }
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  stopPolling();
});
</script>

<template>
  <div
    v-if="paymentId && canView"
    class="mt-3 rounded border border-solid border-[var(--el-border-color-lighter)] px-3 py-2"
  >
    <div class="mb-1 text-sm font-medium text-[var(--el-text-color-primary)]">电子发票</div>
    <div class="flex flex-wrap items-center gap-2 text-sm">
      <span
        :class="[
          isIssued
            ? 'text-[var(--el-color-success)]'
            : isFailed
              ? 'text-[var(--el-color-warning)]'
              : 'text-[var(--el-text-color-regular)]',
        ]"
      >
        {{ isPending && polling ? '○' : isIssued ? '✓' : isFailed ? '⚠' : '○' }}
        {{ statusText }}
      </span>
      <span v-if="statusDetail" class="text-[var(--el-text-color-secondary)]">
        {{ statusDetail }}
      </span>
      <ElButton
        v-if="pollTimedOut || isFailed"
        link
        type="primary"
        size="small"
        @click="startPolling"
      >
        刷新
      </ElButton>
      <ElButton v-if="isFailed" link type="primary" size="small" @click="goCompensation">
        查看原因
      </ElButton>
    </div>
    <EinvoiceQrPanel v-if="isIssued" :issued="true" :qr="qrData" />
    <EinvoiceQrPanel v-else-if="isPending && !pollTimedOut" :issued="false" />
  </div>
</template>
