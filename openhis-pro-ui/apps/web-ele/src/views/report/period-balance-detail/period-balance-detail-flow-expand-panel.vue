<script setup lang="ts">
import type {
  PeriodBalanceDetailColumnDto,
  PeriodBalanceDetailListRowDto,
} from '#/api/report/period-balance-detail';

import { computed } from 'vue';

import {
  buildSettlementFlowRows,
  DAILY_SETTLEMENT_MAIN_INFO_WIDTH_PX,
} from './settlement-flow-rows';

defineOptions({ name: 'PeriodBalanceDetailFlowExpandPanel' });

interface Props {
  row: PeriodBalanceDetailListRowDto;
  columns?: PeriodBalanceDetailColumnDto[];
  /** 与查询区「仅显示有业务」联动 */
  hideZeroRows?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  columns: () => [],
  hideZeroRows: true,
});

/** 当前品种期间流水，全零行过滤随查询区联动。 */
const flowRows = computed(() =>
  buildSettlementFlowRows(props.row, props.columns, { hideZeroRows: props.hideZeroRows }),
);

const flowBodyStyle = computed(() => ({
  paddingLeft: `${DAILY_SETTLEMENT_MAIN_INFO_WIDTH_PX}px`,
}));
</script>

<template>
  <div class="period-balance-detail-flow-expand">
    <div class="period-balance-detail-flow-expand__body" :style="flowBodyStyle">
      <table v-if="flowRows.length" class="period-balance-detail-flow-expand__table">
        <colgroup>
          <col
            class="period-balance-detail-flow-expand__col period-balance-detail-flow-expand__col--label"
          />
          <col
            class="period-balance-detail-flow-expand__col period-balance-detail-flow-expand__col--num"
          />
          <col
            class="period-balance-detail-flow-expand__col period-balance-detail-flow-expand__col--num"
          />
          <col
            class="period-balance-detail-flow-expand__col period-balance-detail-flow-expand__col--num"
          />
        </colgroup>
        <thead>
          <tr>
            <th
              class="period-balance-detail-flow-expand__cell period-balance-detail-flow-expand__cell--label"
            >
              业务
            </th>
            <th
              class="period-balance-detail-flow-expand__cell period-balance-detail-flow-expand__cell--num"
            >
              数量
            </th>
            <th
              class="period-balance-detail-flow-expand__cell period-balance-detail-flow-expand__cell--num"
            >
              包装进价
            </th>
            <th
              class="period-balance-detail-flow-expand__cell period-balance-detail-flow-expand__cell--num"
            >
              进价额
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="flow in flowRows" :key="flow.key">
            <td
              class="period-balance-detail-flow-expand__cell period-balance-detail-flow-expand__cell--label"
            >
              {{ flow.label }}
            </td>
            <td
              class="period-balance-detail-flow-expand__cell period-balance-detail-flow-expand__cell--num"
            >
              {{ flow.quantityText }}
            </td>
            <td
              class="period-balance-detail-flow-expand__cell period-balance-detail-flow-expand__cell--num"
            >
              {{ flow.costPackageUnitPriceText }}
            </td>
            <td
              class="period-balance-detail-flow-expand__cell period-balance-detail-flow-expand__cell--num"
            >
              {{ flow.costAmountText }}
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="period-balance-detail-flow-expand__empty">暂无期间流水</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.period-balance-detail-flow-expand {
  padding: 2px 0 4px;
  background: var(--el-fill-color-lighter);
}

.period-balance-detail-flow-expand__body {
  box-sizing: border-box;
  padding-right: 12px;
}

.period-balance-detail-flow-expand__table {
  width: 100%;
  min-width: 480px;
  font-size: 13px;
  table-layout: fixed;
  border-collapse: collapse;
}

.period-balance-detail-flow-expand__col--label {
  width: 16%;
}

.period-balance-detail-flow-expand__col--num {
  width: 28%;
}

.period-balance-detail-flow-expand__cell {
  height: 28px;
  padding: 0 12px;
  line-height: 28px;
  color: var(--el-text-color-regular);
  border: 1px solid var(--el-border-color-lighter);
}

.period-balance-detail-flow-expand__cell--label {
  text-align: left;
}

.period-balance-detail-flow-expand__cell--num {
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.period-balance-detail-flow-expand__table thead .period-balance-detail-flow-expand__cell {
  height: 30px;
  font-size: 13px;
  font-weight: 600;
  line-height: 30px;
  color: var(--el-text-color-primary);
  background: var(--el-color-primary-light-9);
  border-bottom: 1px solid var(--el-color-primary-light-5);
}

.period-balance-detail-flow-expand__table
  tbody
  tr:nth-child(even)
  .period-balance-detail-flow-expand__cell {
  background: var(--el-fill-color-light);
}

.period-balance-detail-flow-expand__empty {
  padding: 4px 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
