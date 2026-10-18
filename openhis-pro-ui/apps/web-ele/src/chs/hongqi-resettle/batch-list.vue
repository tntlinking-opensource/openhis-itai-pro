<script lang="ts" setup>
import type { HongqiResettleBatch } from '#/api/chs/hongqi-resettle';

import { ElButton, ElEmpty, ElTable, ElTableColumn, ElTag } from 'element-plus';

defineProps<{
  batches: HongqiResettleBatch[];
  emptyText: string;
  busy?: boolean;
  kind: 'registration' | 'clinic';
}>();

const emit = defineEmits<{
  revokeSettle: [batch: HongqiResettleBatch];
  revokeRegistration: [batch: HongqiResettleBatch];
  registerGuoji: [batch: HongqiResettleBatch];
  chargeGuoji: [batch: HongqiResettleBatch];
}>();

function money(value: number | string | undefined) {
  const amount = Number(value ?? 0);
  if (!Number.isFinite(amount)) {
    return '0.00';
  }
  return amount.toFixed(2);
}

function qty(value: number | string | undefined) {
  const amount = Number(value ?? 0);
  if (!Number.isFinite(amount)) {
    return '';
  }
  return String(amount);
}

function hasConsultFee(batch: HongqiResettleBatch) {
  return (batch.lines || []).some((line) => (line.itemName || '').includes('诊查费'));
}
</script>

<template>
  <ElEmpty v-if="batches.length === 0" :description="emptyText" />
  <article v-for="batch in batches" :key="batch.paymentId" class="batch">
    <div class="batch-head">
      <div>
        <strong>{{ batch.visitTime || batch.billTime }}</strong>
        <span>{{ batch.department }}</span>
        <ElTag size="small">{{ batch.contractName }} {{ batch.contractBusNo }}</ElTag>
        <ElTag size="small" type="info">{{ batch.insutypeName }}</ElTag>
        <ElTag size="small" :type="batch.setlStatusName === '已结算' ? 'success' : 'warning'">
          {{ batch.setlStatusName }}
        </ElTag>
      </div>
      <div class="batch-actions">
        <div class="batch-money">本笔项目 {{ money(batch.lineTotal) }} 元</div>
        <ElButton
          v-if="batch.setlStatusName === '已结算'"
          size="small"
          type="danger"
          plain
          :disabled="busy"
          @click="emit('revokeSettle', batch)"
        >
          取消中医院收费
        </ElButton>
        <ElButton
          v-if="hasConsultFee(batch)"
          size="small"
          type="warning"
          plain
          :disabled="busy || !batch.encounterId"
          @click="emit('revokeRegistration', batch)"
        >
          取消中医院挂号
        </ElButton>
        <ElButton
          v-if="hasConsultFee(batch)"
          size="small"
          type="primary"
          plain
          :disabled="busy || !batch.encounterId"
          @click="emit('registerGuoji', batch)"
        >
          挂国基号
        </ElButton>
        <ElButton
          v-if="kind === 'clinic' || hasConsultFee(batch)"
          size="small"
          type="primary"
          :disabled="busy || !batch.encounterId"
          @click="emit('chargeGuoji', batch)"
        >
          {{ kind === 'registration' ? '收取国基挂号费' : '收取国基收费' }}
        </ElButton>
      </div>
    </div>
    <div class="batch-sub">
      结算号 {{ batch.setlId || '—' }} · 收费时间 {{ batch.billTime || '—' }} · 就诊号
      {{ batch.encounterNo || '—' }}
    </div>
    <div class="batch-sub">
      医疗费 {{ money(batch.medfee) }} · 统筹 {{ money(batch.fundPay) }} · 个账
      {{ money(batch.acctPay) }} · 现金 {{ money(batch.cashPay) }}
    </div>
    <ElTable :data="batch.lines || []" size="small" border>
      <ElTableColumn prop="itemName" label="项目" min-width="220" />
      <ElTableColumn label="数量" width="120">
        <template #default="{ row }">{{ qty(row.quantity) }} {{ row.unit || '' }}</template>
      </ElTableColumn>
      <ElTableColumn label="单价" width="110" align="right">
        <template #default="{ row }">{{ money(row.unitPrice) }}</template>
      </ElTableColumn>
      <ElTableColumn label="金额" width="110" align="right">
        <template #default="{ row }">{{ money(row.totalPrice) }}</template>
      </ElTableColumn>
    </ElTable>
  </article>
</template>

<style scoped>
.batch {
  margin-bottom: 12px;
  padding: 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.batch-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.batch-head span {
  margin-left: 8px;
}

.batch-actions {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.batch-money {
  font-weight: 600;
}

.batch-sub {
  margin-bottom: 8px;
  color: #6b7280;
  font-size: 12px;
}
</style>
