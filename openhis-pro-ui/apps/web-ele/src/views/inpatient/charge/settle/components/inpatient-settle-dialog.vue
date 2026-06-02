<script setup lang="ts">
import type { InpatientSettleChargeLineDto } from '#/api/inpatient-settle';

import { computed } from 'vue';

import { ElButton, ElRadioButton, ElRadioGroup, ElTable, ElTableColumn } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';

type PayChannelOption = { label: string; value: string };

defineOptions({
  name: 'InpatientSettleDialog',
});

const props = withDefaults(
  defineProps<{
    chargeLines?: InpatientSettleChargeLineDto[];
    loading?: boolean;
    payChannelOptions?: PayChannelOption[];
    previewText?: string;
    receivableTotal?: number | string;
  }>(),
  {
    chargeLines: () => [],
    loading: false,
    payChannelOptions: () => [],
    previewText: '',
    receivableTotal: 0,
  },
);

const emit = defineEmits<{
  confirm: [];
}>();

const visible = defineModel<boolean>({ required: true });
const payChannelCode = defineModel<string>('payChannelCode', { required: true });

/** 金额格式化为两位小数展示 */
function formatMoney(value: null | number | string | undefined) {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n.toFixed(2) : '0.00';
}

const receivableDisplay = computed(() => formatMoney(props.receivableTotal));

/** 关闭弹窗 */
function handleCancel() {
  visible.value = false;
}
</script>

<template>
  <AppDialog v-model="visible" title="确认住院结算" width="720px" destroy-on-close>
    <div class="settle-dialog-body">
      <p
        v-if="props.previewText"
        class="settle-dialog-preview text-sm text-[var(--el-text-color-regular)]"
      >
        {{ props.previewText }}
      </p>
      <div class="settle-dialog-amount">
        <span class="text-[var(--el-text-color-regular)]">本次应收</span>
        <strong class="text-[var(--el-color-primary)]">¥{{ receivableDisplay }}</strong>
      </div>
      <div class="settle-dialog-section">
        <div class="settle-dialog-section__title">支付方式</div>
        <ElRadioGroup v-model="payChannelCode" class="settle-dialog-pay-group">
          <ElRadioButton v-for="opt in payChannelOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </ElRadioButton>
        </ElRadioGroup>
      </div>
      <div class="settle-dialog-section">
        <div class="settle-dialog-section__title">结算费用（{{ chargeLines.length }} 项）</div>
        <ElTable :data="chargeLines" border size="small" max-height="280" row-key="chargeItemId">
          <ElTableColumn prop="itemName" label="项目" min-width="160" show-overflow-tooltip />
          <ElTableColumn
            prop="categoryDisplay"
            label="类别"
            min-width="100"
            show-overflow-tooltip
          />
          <ElTableColumn label="金额" width="110" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.lineAmount) }}</template>
          </ElTableColumn>
        </ElTable>
      </div>
    </div>
    <template #footer>
      <ElButton @click="handleCancel">取消</ElButton>
      <ElButton type="primary" :loading="loading" @click="emit('confirm')">确认结算</ElButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.settle-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.settle-dialog-preview {
  margin: 0;
}

.settle-dialog-amount {
  display: flex;
  align-items: baseline;
  gap: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  padding: 10px 12px;
}

.settle-dialog-amount strong {
  font-size: 24px;
  font-weight: 700;
}

.settle-dialog-section__title {
  margin-bottom: 8px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 600;
}

.settle-dialog-pay-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
