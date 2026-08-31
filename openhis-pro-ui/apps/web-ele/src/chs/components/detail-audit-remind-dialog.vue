<script setup lang="ts">
import type { AuditAnalyzeResult, AuditViolation } from '#/chs/api/detail-audit-try';

import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElInput,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import { DETAIL_AUDIT_DSPO_WAY_OPTIONS, formatSevDeg } from '#/chs/constants/detail-audit-try';
import { gateFeedback3103Api } from '#/chs/api/detail-audit-gate';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';

defineOptions({ name: 'DetailAuditRemindDialog' });

const props = defineProps<{
  /** 提醒类型：1 事前 / 2 事中 */
  warnType?: string;
  contractBusNo?: string;
  contractId?: number | string;
}>();

const visible = defineModel<boolean>({ default: false });

const analyzeResult = ref<AuditAnalyzeResult | null>(null);
const selected = ref<AuditViolation | null>(null);
const dspoWay = ref('2');
const dspoWayRea = ref('');
const submitting = ref(false);
const runtimeContractBusNo = ref('');
const runtimeContractId = ref<number | string | undefined>();

let resolvePromise: ((continued: boolean) => void) | null = null;

const violations = computed(() => analyzeResult.value?.violations ?? []);
const details = computed(() => selected.value?.details ?? []);
const mdtrtId = computed(() => selected.value?.mdtrtId || violations.value[0]?.mdtrtId || '—');
const selectedVolaCont = computed(() => String(selected.value?.volaCont ?? '').trim());
const effectiveBusNo = computed(
  () => runtimeContractBusNo.value.trim() || props.contractBusNo?.trim() || '',
);
const effectiveContractId = computed(() => runtimeContractId.value ?? props.contractId);

watch(visible, (open) => {
  if (!open) {
    return;
  }
  selected.value = violations.value[0] ?? null;
  dspoWay.value = '2';
  dspoWayRea.value = '';
});

/**
 * 打开对话框并等待用户处置。
 * @returns true=继续执行；false=返回修改
 */
function openAndWait(
  result: AuditAnalyzeResult,
  meta?: { contractBusNo?: string; contractId?: number | string },
): Promise<boolean> {
  analyzeResult.value = result;
  selected.value = result.violations?.[0] ?? null;
  if (meta?.contractBusNo) {
    runtimeContractBusNo.value = meta.contractBusNo;
  }
  if (meta?.contractId !== undefined) {
    runtimeContractId.value = meta.contractId;
  }
  visible.value = true;
  return new Promise<boolean>((resolve) => {
    resolvePromise = resolve;
  });
}

/** 关闭对话框并 resolve 等待中的 Promise。 */
function finish(continued: boolean) {
  visible.value = false;
  const r = resolvePromise;
  resolvePromise = null;
  if (r) {
    r(continued);
  }
}

/** 按当前违规列表生成 3103 warns（优先 jrdId，无明细则回退 jrId）。 */
function buildWarns() {
  const warns: Array<{
    warnRsltId: string;
    dspoWay: string;
    dspoWayRea?: string;
    jrId?: string;
  }> = [];
  for (const v of violations.value) {
    const lines = v.details ?? [];
    if (lines.length === 0) {
      if (v.jrId) {
        warns.push({
          warnRsltId: v.jrId,
          dspoWay: dspoWay.value,
          dspoWayRea: dspoWayRea.value.trim() || undefined,
          jrId: v.jrId,
        });
      }
      continue;
    }
    for (const d of lines) {
      const id = d.jrdId || v.jrId;
      if (!id) {
        continue;
      }
      warns.push({
        warnRsltId: id,
        dspoWay: dspoWay.value,
        dspoWayRea: dspoWayRea.value.trim() || undefined,
        jrId: v.jrId,
      });
    }
  }
  return warns;
}

/**
 * 提交 3103：continued=true 强制 dspo=1 且原因必填；false 强制 dspo=2。
 * 反馈成功后 finish；失败仅 Toast，保持阻断。
 */
async function submitFeedback(continued: boolean) {
  if (continued && dspoWay.value !== '1') {
    dspoWay.value = '1';
  }
  if (!continued && dspoWay.value !== '2') {
    dspoWay.value = '2';
  }
  if (dspoWay.value === '1' && !dspoWayRea.value.trim()) {
    ElMessage.warning('继续执行须填写处置原因');
    return;
  }
  const busNo = effectiveBusNo.value;
  if (!busNo) {
    ElMessage.warning('缺少医保契约');
    return;
  }
  const warns = buildWarns();
  if (warns.length === 0) {
    ElMessage.warning('无法生成反馈明细');
    return;
  }
  submitting.value = true;
  try {
    await ensureInsuranceSignIn(busNo, { warnOnServerFallback: false });
    await gateFeedback3103Api({
      contractBusNo: busNo,
      contractId: effectiveContractId.value,
      warnType: props.warnType || '1',
      warns,
    });
    ElMessage.success(continued ? '已反馈并继续' : '已反馈返回修改');
    finish(continued);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '3103 反馈失败');
  } finally {
    submitting.value = false;
  }
}

defineExpose({ openAndWait });
</script>

<template>
  <AppDialog
    v-model="visible"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    destroy-on-close
    title="医保事前提醒"
    width="960px"
  >
    <div class="audit-remind">
      <div class="audit-remind__summary">
        <div>
          发现 {{ violations.length }} 条规则提醒
          <span class="audit-remind__muted">请处理后再继续。严重程度见列表。</span>
        </div>
        <div class="audit-remind__mdtrt">就诊号 {{ mdtrtId }}</div>
      </div>

      <ElTable
        :data="violations"
        class="audit-remind__table"
        highlight-current-row
        max-height="220"
        stripe
        @current-change="(row: AuditViolation | null) => (selected = row)"
      >
        <ElTableColumn label="严重程度" width="110">
          <template #default="{ row }">
            {{ formatSevDeg(row.sevDeg) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="规则名称" min-width="220">
          <template #default="{ row }">
            <div class="audit-remind__wrap">{{ row.ruleName || '—' }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="违规金额" width="100" prop="volaAmt" align="right" />
      </ElTable>

      <div class="audit-remind__section-title">选中行 · 违规内容</div>
      <div class="audit-remind__content">
        {{ selectedVolaCont || '（无违规内容）' }}
      </div>

      <div class="audit-remind__section-title">选中行 · 违规明细</div>
      <ElTable :data="details" class="audit-remind__table" max-height="240" stripe>
        <ElTableColumn label="药品/项目" min-width="220">
          <template #default="{ row }">
            <div class="audit-remind__wrap">{{ row.itemName || '—' }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="处方/医嘱" min-width="160">
          <template #default="{ row }">
            <div class="audit-remind__wrap">{{ row.rxId || '—' }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="类型" width="72" prop="volaItemType" align="center" />
        <ElTableColumn label="金额" width="96" prop="volaAmt" align="right" />
      </ElTable>

      <div class="audit-remind__action">
        <div class="audit-remind__section-title audit-remind__section-title--flush">处理方式</div>
        <ElRadioGroup v-model="dspoWay">
          <ElRadio v-for="opt in DETAIL_AUDIT_DSPO_WAY_OPTIONS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </ElRadio>
        </ElRadioGroup>
        <ElInput
          v-model="dspoWayRea"
          :rows="2"
          class="audit-remind__reason"
          placeholder="继续执行时必填处置原因"
          type="textarea"
        />
      </div>
    </div>

    <template #footer>
      <ElButton :loading="submitting" @click="submitFeedback(false)"> 返回修改 </ElButton>
      <ElButton :loading="submitting" type="primary" @click="submitFeedback(true)">
        确认并继续
      </ElButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.audit-remind {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.audit-remind__summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 14px;
  line-height: 1.5;
}

.audit-remind__muted {
  margin-left: 8px;
  color: #64748b;
  font-size: 13px;
}

.audit-remind__mdtrt {
  color: #475569;
  font-size: 13px;
  line-height: 1.45;
  word-break: break-all;
}

.audit-remind__section-title {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.4;
}

.audit-remind__section-title--flush {
  margin-top: 0;
  margin-bottom: 6px;
  color: #0f172a;
  font-size: 14px;
}

.audit-remind__content {
  max-height: 160px;
  overflow: auto;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
  color: #0f172a;
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.audit-remind__wrap {
  white-space: normal;
  word-break: break-word;
  line-height: 1.45;
}

.audit-remind__table :deep(.el-table .cell) {
  line-height: 1.45;
}

.audit-remind__action {
  margin-top: 6px;
}

.audit-remind__reason {
  margin-top: 8px;
}
</style>
