<script setup lang="ts">
import type { NurseStationModulePatient } from './nurse-station-module-patient.types';

import { computed } from 'vue';

import {
  formatNurseStationAccountBalance,
  isNurseStationBedUnassigned,
} from './nurse-station-module-patient.types';

defineOptions({
  name: 'NurseStationPatientOrderCollapseTitle',
});

interface Props {
  patient: NurseStationModulePatient;
  /** 该患者当前列表医嘱条数；未传则不展示 */
  orderCount?: number;
  /** 医嘱数量标签前缀，如「待执行」→「待执行医嘱」 */
  orderCountLabel?: string;
  /** 该患者计划时间点合计；未传则不展示 */
  planSlotCount?: number;
}

const props = withDefaults(defineProps<Props>(), {
  orderCount: undefined,
  orderCountLabel: '待执行',
  planSlotCount: undefined,
});

/** 床位展示：与左侧患者卡片一致，直接展示床号。 */
const bedDisplayText = computed(() => {
  const trimmed = props.patient.bedNo?.trim();
  return trimmed || '未分床';
});

const bedUnassigned = computed(() => isNurseStationBedUnassigned(props.patient.bedNo));

const prepayBalanceText = computed(() => formatNurseStationAccountBalance(props.patient));

/** 是否展示医嘱/时点统计（执行页传入） */
const showOrderStats = computed(
  () => props.orderCount !== undefined || props.planSlotCount !== undefined,
);

const orderStatLabel = computed(() => `${props.orderCountLabel || '待执行'}医嘱`);
</script>

<template>
  <header class="card-head">
    <div class="patient-meta">
      <span class="bed-tag" :class="{ 'is-unassigned': bedUnassigned }">{{ bedDisplayText }}</span>
      <span class="name-text">{{ patient.name }}</span>
      <span class="age-text">{{ patient.age }}岁</span>
      <span class="diag-text truncate" :title="patient.diagnosis">{{
        patient.diagnosis || '—'
      }}</span>
    </div>
    <div class="balance-meta">
      <span v-if="showOrderStats" class="order-stats">
        <span v-if="orderCount !== undefined" class="order-stats__item">
          {{ orderStatLabel }}
          <span class="order-stats__value">{{ orderCount }}</span>
        </span>
        <span v-if="planSlotCount !== undefined" class="order-stats__item">
          计划时间点
          <span class="order-stats__value">{{ planSlotCount }}</span>
        </span>
      </span>
      <span class="money-item"
        >预交余额 <span class="money-amount">{{ prepayBalanceText }}</span></span
      >
    </div>
  </header>
</template>

<style scoped lang="scss">
@use './nurse-station-patient-order-list.scss';

.bed-tag.is-unassigned {
  color: var(--el-color-warning);
}

.name-text,
.age-text {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.name-text {
  font-weight: 700;
}

.age-text {
  color: var(--el-text-color-regular);
}

.diag-text {
  min-width: 0;
  max-width: 40%;
}

.order-stats {
  align-items: baseline;
  display: inline-flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-right: 12px;
}

.order-stats__item {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.order-stats__value {
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 700;
  margin-left: 2px;
}
</style>
