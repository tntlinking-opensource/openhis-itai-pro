<script setup lang="ts">
import type { BossDocumentDetailDto, BossLineDto } from '#/api/einvoice-boss/document';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { useAccess } from '@vben/access';

import {
  ElButton,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElAlert,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  getDocumentByIdApi,
  issueNowApi,
  saveDocumentAdjustmentApi,
  validateDocumentAmountApi,
} from '#/api/einvoice-boss/document';
import EinvoiceConfirmDialog from '#/components/einvoice/EinvoiceConfirmDialog.vue';
import { useRequest } from '#/hooks/use-request';
import {
  formatMoneyDisplay,
  moneyDiffAtSettle,
  moneyEqualsAtSettle,
  sumMoneyThenCalc,
} from '#/utils/format/money-scale';

defineOptions({
  name: 'EinvoiceDocumentAmount',
});

const route = useRoute();
const router = useRouter();
const { hasAccessByCodes } = useAccess();

const documentId = computed(() => String(route.params.documentId ?? ''));
const canAdjust = computed(() => hasAccessByCodes(['einvoice-boss.document.adjust']));
const canOperate = computed(() => hasAccessByCodes(['einvoice-boss.compensation.operate']));

const detail = ref<BossDocumentDetailDto | null>(null);
const editableLines = ref<BossLineDto[]>([]);
const totalAmt = ref(0);
const reason = ref('');
const dirty = ref(false);
const validateError = ref('');
const confirmOpen = ref(false);

const { run: runLoad, loading: loadLoading } = useRequest(getDocumentByIdApi, { manual: true });
const { run: runValidate, loading: validateLoading } = useRequest(validateDocumentAmountApi, {
  manual: true,
});
const { run: runSave, loading: saveLoading } = useRequest(saveDocumentAdjustmentApi, {
  manual: true,
});
const { run: runIssueNow, loading: issueLoading } = useRequest(issueNowApi, { manual: true });

const lineSum = computed(() =>
  sumMoneyThenCalc(editableLines.value.map((line) => Number(line.amount ?? 0))),
);

const diff = computed(() => moneyDiffAtSettle(lineSum.value, totalAmt.value));
const amountMatched = computed(() => moneyEqualsAtSettle(lineSum.value, totalAmt.value));

const amountStatusTag = computed(() => {
  const status = detail.value?.amountStatus ?? '';
  if (status === 'MATCH') {
    return { label: '金额一致', type: 'success' as const };
  }
  if (status === 'ADJUSTED') {
    return { label: '已人工调整', type: 'info' as const };
  }
  return { label: '金额不一致', type: 'warning' as const };
});

const adjustable = computed(() => detail.value?.invoiceStatus === 'DRAFT');

function syncFromDetail(data: BossDocumentDetailDto) {
  detail.value = data;
  editableLines.value = (data.lines ?? []).map((line) => ({ ...line }));
  totalAmt.value = Number(data.totalAmt ?? 0);
  dirty.value = false;
  validateError.value = '';
}

async function loadDetail() {
  if (!documentId.value) {
    return;
  }
  const data = await runLoad(documentId.value);
  if (data) {
    syncFromDetail(data);
    if (data.warningMessage) {
      ElMessage.warning(data.warningMessage);
    }
    if (data.invoiceStatus !== 'DRAFT') {
      ElMessage.warning('仅草稿状态票据允许调额');
    }
  }
}

function markDirty() {
  dirty.value = true;
  validateError.value = '';
}

async function handleValidate() {
  if (!documentId.value) {
    return;
  }
  const result = await runValidate(documentId.value);
  if (result?.valid) {
    validateError.value = '';
    ElMessage.success('金额校验通过');
    return;
  }
  validateError.value = `明细合计 ${formatMoneyDisplay(result?.lineSum ?? lineSum.value)} 与开票总额 ${formatMoneyDisplay(result?.totalAmt ?? totalAmt.value)} 相差 ${formatMoneyDisplay(result?.diff ?? diff.value)} 元`;
}

async function handleSave() {
  if (!documentId.value) {
    return;
  }
  if (reason.value.trim().length < 5) {
    ElMessage.warning('调整原因至少 5 个字');
    return;
  }
  if (!amountMatched.value) {
    validateError.value = `明细合计 ${formatMoneyDisplay(lineSum.value)} 与开票总额 ${formatMoneyDisplay(totalAmt.value)} 相差 ${formatMoneyDisplay(diff.value)} 元`;
    return;
  }
  const data = await runSave({
    documentId: documentId.value,
    totalAmt: totalAmt.value,
    reason: reason.value.trim(),
    lines: editableLines.value.map((line) => ({
      lineId: line.lineId,
      chargeCode: line.chargeCode,
      chargeName: line.chargeName,
      stdAmount: line.stdAmount,
      amount: line.amount,
      newLine: !line.lineId,
    })),
  });
  if (data) {
    syncFromDetail(data);
    ElMessage.success('调整已保存');
    if (data.warningMessage) {
      ElMessage.warning(data.warningMessage);
    }
  }
}

function addBalanceLine() {
  const gap = moneyDiffAtSettle(totalAmt.value, lineSum.value);
  if (moneyEqualsAtSettle(lineSum.value, totalAmt.value)) {
    ElMessage.info('当前金额已平衡');
    return;
  }
  editableLines.value.push({
    chargeCode: 'BALANCE_ADJ',
    chargeName: '金额调平',
    stdAmount: gap,
    amount: gap,
    adjusted: true,
  });
  if (!reason.value.trim()) {
    reason.value = '尾差调平';
  }
  markDirty();
}

function handleIssueNowClick() {
  if (dirty.value) {
    ElMessage.warning('请先保存调整');
    return;
  }
  if (!amountMatched.value) {
    ElMessage.warning('请先校验并通过金额核对');
    return;
  }
  confirmOpen.value = true;
}

async function handleIssueConfirm() {
  const invoiceId = detail.value?.invoiceId;
  if (!invoiceId) {
    confirmOpen.value = false;
    return;
  }
  await runIssueNow(invoiceId);
  ElMessage.success('操作已提交');
  confirmOpen.value = false;
  await loadDetail();
}

function goBack() {
  router.back();
}

onMounted(() => {
  void loadDetail();
});
</script>

<template>
  <Page auto-content-height>
    <div class="mb-4 flex items-center justify-between">
      <div class="flex items-center gap-3">
        <ElButton link type="primary" @click="goBack">← 返回</ElButton>
        <h2 class="text-lg font-medium">电子发票金额调整</h2>
        <ElTag :type="amountStatusTag.type" size="small">{{ amountStatusTag.label }}</ElTag>
      </div>
    </div>

    <div
      v-loading="loadLoading"
      class="rounded border border-solid border-[var(--el-border-color-lighter)] p-4"
    >
      <div v-if="detail?.warningMessage" class="mb-4">
        <ElAlert :title="detail.warningMessage" type="warning" show-icon :closable="false" />
      </div>
      <div v-if="detail && !adjustable" class="mb-4 text-sm text-[var(--el-color-warning)]">
        当前票据状态为「{{
          detail.invoiceStatus === 'ISSUED'
            ? '已开具'
            : detail.invoiceStatus === 'CANCELLED'
              ? '已作废'
              : detail.invoiceStatus
        }}」，不允许调额。
      </div>
      <div v-if="detail" class="mb-4 grid gap-2 text-sm md:grid-cols-2">
        <div>患者：{{ detail.patientName }}（{{ detail.genderDisplay }}，{{ detail.age }}）</div>
        <div>就诊号：{{ detail.encounterBusNo }}</div>
        <div>业务线：{{ detail.issueKindLabel }}</div>
        <div>业务流水号：{{ detail.busNo }}</div>
        <div>支付金额（只读）：{{ formatMoneyDisplay(detail.paymentAmt ?? 0) }}</div>
        <div class="flex items-center gap-2">
          开票总额
          <ElInputNumber
            v-model="totalAmt"
            :disabled="!canAdjust || !adjustable"
            :precision="2"
            :step="0.01"
            controls-position="right"
            @change="markDirty"
          />
          <span
            :class="
              amountMatched ? 'text-[var(--el-color-success)]' : 'text-[var(--el-color-danger)]'
            "
          >
            差额：{{ formatMoneyDisplay(diff) }} {{ amountMatched ? '✓' : '' }}
          </span>
        </div>
      </div>

      <div class="mb-2 flex items-center justify-between">
        <span class="font-medium">明细</span>
        <div class="flex gap-2">
          <ElButton :loading="validateLoading" @click="handleValidate">校验金额</ElButton>
          <ElButton v-if="canAdjust && adjustable" @click="addBalanceLine">+ 调平行</ElButton>
        </div>
      </div>

      <ElTable :data="editableLines" border stripe>
        <ElTableColumn prop="lineNo" label="行" width="60" />
        <ElTableColumn prop="chargeName" label="项目名称" min-width="140" />
        <ElTableColumn label="标准金额" width="110">
          <template #default="{ row }">
            <span class="text-[var(--el-text-color-secondary)]">
              {{ formatMoneyDisplay(row.stdAmount ?? 0) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="出站金额" width="140">
          <template #default="{ row }">
            <ElInputNumber
              v-model="row.amount"
              :disabled="!canAdjust || !adjustable"
              :precision="2"
              :step="0.01"
              controls-position="right"
              class="!w-full"
              @change="markDirty"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn label="备注" min-width="100">
          <template #default="{ row }">
            <span v-if="row.adjusted" class="text-[var(--el-color-warning)]">已调整</span>
          </template>
        </ElTableColumn>
      </ElTable>

      <div
        class="mt-2 text-right text-sm"
        :class="amountMatched ? '' : 'text-[var(--el-color-danger)]'"
      >
        合计 {{ formatMoneyDisplay(lineSum) }}
        <span v-if="!amountMatched">（与开票总额不等）</span>
      </div>
      <p v-if="validateError" class="mt-2 text-sm text-[var(--el-color-danger)]">
        {{ validateError }}
      </p>

      <div class="mt-4">
        <div class="mb-2 text-sm">调整原因（必填）</div>
        <ElInput
          v-model="reason"
          :disabled="!canAdjust || !adjustable"
          type="textarea"
          :rows="2"
          placeholder="至少 5 个字"
        />
      </div>

      <div class="mt-4 flex justify-end gap-2">
        <ElButton @click="goBack">取消</ElButton>
        <ElButton
          v-if="canAdjust && adjustable"
          type="primary"
          :loading="saveLoading"
          @click="handleSave"
        >
          保存调整
        </ElButton>
        <ElButton
          v-if="canOperate && adjustable"
          type="warning"
          :loading="issueLoading"
          @click="handleIssueNowClick"
        >
          重新开具
        </ElButton>
      </div>
    </div>

    <EinvoiceConfirmDialog
      v-model:open="confirmOpen"
      action="issueNow"
      :bus-no="detail?.busNo"
      @confirm="handleIssueConfirm"
    />
  </Page>
</template>
