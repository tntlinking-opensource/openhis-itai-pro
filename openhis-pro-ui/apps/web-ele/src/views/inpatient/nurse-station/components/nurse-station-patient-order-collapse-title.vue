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
}

const props = defineProps<Props>();

/** 床位展示：与左侧患者卡片一致，直接展示床号。 */
const bedDisplayText = computed(() => {
  const trimmed = props.patient.bedNo?.trim();
  return trimmed || '未分床';
});

const bedUnassigned = computed(() => isNurseStationBedUnassigned(props.patient.bedNo));

const prepayBalanceText = computed(() =>
  formatNurseStationAccountBalance(props.patient.accountBalance),
);
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

.age-text {
  color: var(--el-text-color-regular);
}

.diag-text {
  min-width: 0;
  max-width: 40%;
}
</style>
