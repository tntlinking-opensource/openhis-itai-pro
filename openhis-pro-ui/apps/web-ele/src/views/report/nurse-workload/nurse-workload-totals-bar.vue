<script setup lang="ts">
import { computed } from 'vue';

import { IconifyIcon } from '@zxplat/icons';

import { fmtReportNumber, fmtReportPurchaseMoney } from '#/views/report/shared/report-format';

defineOptions({ name: 'NurseWorkloadTotalsBar' });

interface Props {
  consumableTotalDiscountAmount?: number | string;
  consumableTotalPurchaseAmount?: number | string | null;
  consumableTotalQuantity?: number | string;
  consumableTotalSaleAmount?: number | string;
  discountFeatureEnabled?: boolean;
  executionTotalCount?: number;
  executionTotalDiscountAmount?: number | string;
  executionTotalPurchaseAmount?: number | string | null;
  executionTotalSaleAmount?: number | string;
  nurseCount?: number;
}

const props = withDefaults(defineProps<Props>(), {
  consumableTotalDiscountAmount: 0,
  consumableTotalPurchaseAmount: null,
  consumableTotalQuantity: 0,
  consumableTotalSaleAmount: 0,
  discountFeatureEnabled: false,
  executionTotalCount: 0,
  executionTotalDiscountAmount: 0,
  executionTotalPurchaseAmount: null,
  executionTotalSaleAmount: 0,
  nurseCount: 0,
});

const saleTotal = computed(
  () => Number(props.executionTotalSaleAmount ?? 0) + Number(props.consumableTotalSaleAmount ?? 0),
);

const discountTotal = computed(
  () =>
    Number(props.executionTotalDiscountAmount ?? 0) +
    Number(props.consumableTotalDiscountAmount ?? 0),
);

/** 已配置有效折扣时展示优惠/实收。 */
const showDiscount = computed(() => Boolean(props.discountFeatureEnabled));

const purchaseTotal = computed(() => {
  const executionPurchase = props.executionTotalPurchaseAmount;
  const consumablePurchase = props.consumableTotalPurchaseAmount;
  if (executionPurchase == null && consumablePurchase == null) {
    return null;
  }
  return Number(executionPurchase ?? 0) + Number(consumablePurchase ?? 0);
});
</script>

<template>
  <div class="nurse-workload-stats">
    <div class="nurse-workload-stats__row">
      <div class="nurse-workload-stats__card nurse-workload-stats__card--nurse">
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:users" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">护士人数</span>
          <span class="nurse-workload-stats__value">{{ nurseCount }}</span>
        </div>
      </div>

      <div class="nurse-workload-stats__card nurse-workload-stats__card--execution">
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:activity" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">处置总次数</span>
          <span class="nurse-workload-stats__value">{{ executionTotalCount }}</span>
        </div>
      </div>

      <div class="nurse-workload-stats__card nurse-workload-stats__card--consumable">
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:package" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">耗材总数量</span>
          <span class="nurse-workload-stats__value">
            {{ fmtReportNumber(consumableTotalQuantity, 2) }}
          </span>
        </div>
      </div>
    </div>

    <div class="nurse-workload-stats__row nurse-workload-stats__row--amount">
      <div class="nurse-workload-stats__card nurse-workload-stats__card--amount">
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:banknote" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">处置售价</span>
          <span class="nurse-workload-stats__value nurse-workload-stats__value--amount">
            {{ fmtReportNumber(executionTotalSaleAmount, 2) }}
          </span>
        </div>
      </div>

      <div class="nurse-workload-stats__card nurse-workload-stats__card--amount">
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:wallet" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">耗材售价</span>
          <span class="nurse-workload-stats__value nurse-workload-stats__value--amount">
            {{ fmtReportNumber(consumableTotalSaleAmount, 2) }}
          </span>
        </div>
      </div>

      <div class="nurse-workload-stats__card nurse-workload-stats__card--amount-total">
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:circle-dollar-sign" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">售价合计</span>
          <span class="nurse-workload-stats__value nurse-workload-stats__value--amount-total">
            {{ fmtReportNumber(saleTotal, 2) }}
          </span>
        </div>
      </div>

      <div
        v-if="showDiscount"
        class="nurse-workload-stats__card nurse-workload-stats__card--amount-total"
      >
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:percent" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">优惠合计</span>
          <span class="nurse-workload-stats__value nurse-workload-stats__value--amount-total">
            {{ fmtReportNumber(discountTotal, 2) }}
          </span>
        </div>
      </div>
    </div>

    <div class="nurse-workload-stats__row nurse-workload-stats__row--amount">
      <div class="nurse-workload-stats__card nurse-workload-stats__card--amount">
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:receipt" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">耗材进价</span>
          <span class="nurse-workload-stats__value nurse-workload-stats__value--amount">
            {{ fmtReportPurchaseMoney(consumableTotalPurchaseAmount) }}
          </span>
        </div>
      </div>

      <div class="nurse-workload-stats__card nurse-workload-stats__card--amount-total">
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:coins" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">进价合计</span>
          <span class="nurse-workload-stats__value nurse-workload-stats__value--amount-total">
            {{ fmtReportPurchaseMoney(purchaseTotal) }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nurse-workload-stats {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.nurse-workload-stats__row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.nurse-workload-stats__row--amount:last-child {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.nurse-workload-stats__card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: #fff;
}

.nurse-workload-stats__icon {
  display: flex;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 18px;
}

.nurse-workload-stats__card--nurse .nurse-workload-stats__icon {
  background: linear-gradient(135deg, #a7f3d0 0%, #6ee7b7 100%);
  color: #047857;
}

.nurse-workload-stats__card--execution .nurse-workload-stats__icon {
  background: linear-gradient(135deg, #bfdbfe 0%, #93c5fd 100%);
  color: #1d4ed8;
}

.nurse-workload-stats__card--consumable .nurse-workload-stats__icon {
  background: linear-gradient(135deg, #fde68a 0%, #fcd34d 100%);
  color: #b45309;
}

.nurse-workload-stats__card--amount .nurse-workload-stats__icon,
.nurse-workload-stats__card--amount-total .nurse-workload-stats__icon {
  background: linear-gradient(135deg, #e9d5ff 0%, #d8b4fe 100%);
  color: #7e22ce;
}

.nurse-workload-stats__card--amount-total {
  border-color: var(--el-color-primary-light-7);
  background: var(--el-color-primary-light-9);
}

.nurse-workload-stats__content {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.nurse-workload-stats__label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.nurse-workload-stats__value {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

.nurse-workload-stats__value--amount,
.nurse-workload-stats__value--amount-total {
  font-size: 18px;
}

.nurse-workload-stats__value--amount-total {
  color: var(--el-color-primary);
}
</style>
