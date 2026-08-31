<!--
  调价单明细行展开：展示该项目下受影响批号（子表 details）及调前价/盈亏。
-->
<script setup lang="ts">
import type { PriceAdjustmentDetailRow, PriceAdjustmentLineRow } from '../types';

import { computed } from 'vue';

import { formatMoneyDisplay, formatPurchaseMoneyDisplay } from '#/utils/format/money-scale';

import { isLogisticsItemTable } from '../utils/price-adjustment-item-table';

defineOptions({ name: 'PriceAdjustmentLineLotExpand' });

interface Props {
  row?: PriceAdjustmentLineRow;
}

const props = withDefaults(defineProps<Props>(), {
  row: () => ({}),
});

/** 是否物流口径（展示进价列） */
const isLogistics = computed(() => {
  const style = props.row?.adjustmentStyle;
  if (style === 'logistics') return true;
  if (style === 'clinical') return false;
  return isLogisticsItemTable(props.row?.itemTable);
});

const detailRows = computed<PriceAdjustmentDetailRow[]>(() => props.row?.details ?? []);

/** 金额展示：空值占位（售价/售价盈亏两位） */
function formatAmount(value: unknown): string {
  if (value === undefined || value === null || value === '') return '—';
  const n = Number(value);
  if (!Number.isFinite(n)) return '—';
  return formatMoneyDisplay(n);
}

/** 进价单价/进价盈亏展示：最多三位去尾零 */
function formatPurchaseAmount(value: unknown): string {
  if (value === undefined || value === null || value === '') return '—';
  const n = Number(value);
  if (!Number.isFinite(n)) return '—';
  return formatPurchaseMoneyDisplay(n) || '—';
}

/** 数量展示 */
function formatQuantity(value: unknown): string {
  if (value === undefined || value === null || value === '') return '—';
  const n = Number(value);
  if (!Number.isFinite(n)) return '—';
  return String(n);
}
</script>

<template>
  <div class="lot-expand">
    <div v-if="detailRows.length === 0" class="lot-expand__empty">暂无批号明细</div>
    <table v-else class="lot-expand__table">
      <thead>
        <tr>
          <th class="col-lot">批号</th>
          <th class="col-qty">数量</th>
          <th v-if="isLogistics" class="col-amount">调前进价</th>
          <th class="col-amount">调前售价</th>
          <th v-if="isLogistics" class="col-impact">进价盈亏</th>
          <th class="col-impact">售价盈亏</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(detail, index) in detailRows"
          :key="`${detail.lotNumber ?? ''}-${detail.id ?? index}`"
        >
          <td class="col-lot">{{ detail.lotNumber || '—' }}</td>
          <td class="col-qty tabular-nums">{{ formatQuantity(detail.quantity) }}</td>
          <td v-if="isLogistics" class="col-amount tabular-nums">
            {{ formatPurchaseAmount(detail.oldPurchaseAmount) }}
          </td>
          <td class="col-amount tabular-nums">{{ formatAmount(detail.oldSellingAmount) }}</td>
          <td v-if="isLogistics" class="col-impact tabular-nums">
            {{ formatPurchaseAmount(detail.purchaseImpact) }}
          </td>
          <td class="col-impact tabular-nums">{{ formatAmount(detail.sellingImpact) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped lang="scss">
.lot-expand {
  padding: 8px 16px 10px 72px;
  background: var(--el-fill-color-blank);
  border-top: 1px solid var(--el-border-color-lighter);
}

.lot-expand__empty {
  font-size: 13px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
}

.lot-expand__table {
  width: 100%;
  font-size: 13px;
  line-height: 20px;
  border-collapse: collapse;
  table-layout: fixed;

  th,
  td {
    padding: 6px 10px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  th {
    font-weight: 500;
    color: var(--el-text-color-regular);
    text-align: center;
    background: var(--el-fill-color-light);
  }

  tbody tr:last-child td {
    border-bottom: none;
  }
}

.col-lot {
  width: 20%;
  text-align: center;
}

.col-qty {
  width: 12%;
  text-align: center;
}

.col-amount,
.col-impact {
  width: 17%;
  text-align: right;
}
</style>
