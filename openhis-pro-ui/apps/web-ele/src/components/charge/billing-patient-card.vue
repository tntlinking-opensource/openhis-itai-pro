<script setup lang="ts">
import type { BillingPatientCardPatient } from './billing-patient-card.types';

import { computed } from 'vue';

import { ElTag } from 'element-plus';

defineOptions({ name: 'BillingPatientCard' });

const props = withDefaults(
  defineProps<{
    patient: BillingPatientCardPatient;
    selected?: boolean;
  }>(),
  {
    selected: false,
  },
);

const emit = defineEmits<{
  select: [patient: BillingPatientCardPatient];
}>();

const isInpatient = computed(() => props.patient.visitType === 'inpatient');

const departmentLabel = computed(() => (isInpatient.value ? '所在科室' : '挂号科室'));

const timeLabel = computed(() => (isInpatient.value ? '入院时间' : '挂号时间'));

const showChargeStatus = computed(() => Boolean(props.patient.chargeStatus));

/** 待处理态（待收费 / 待退费）用橙色系，已结算用绿色系 */
const isPendingChargeLike = computed(
  () => props.patient.chargeStatus === '待收费' || props.patient.chargeStatus === '待退费',
);

/** 左侧色条：有收费状态时待处理橙、已结算绿；否则中性灰 */
const statusBarClass = computed(() => {
  if (!showChargeStatus.value) return 'bg-slate-300';
  return isPendingChargeLike.value ? 'bg-orange-500' : 'bg-emerald-500';
});

/** 与色条一致：warning≈橙、success≈绿 */
const chargeStatusTagType = computed(() => (isPendingChargeLike.value ? 'warning' : 'success'));

/** 选中态边框/底与色条同色组 */
const selectedFrameClass = computed(() => {
  if (!showChargeStatus.value) {
    return 'border-[var(--el-color-primary)] bg-[var(--el-color-primary-light-9)] ring-1 ring-[var(--el-color-primary-light-7)]';
  }
  return isPendingChargeLike.value
    ? 'border-orange-500 bg-orange-50/45 ring-1 ring-orange-200/70'
    : 'border-emerald-500 bg-emerald-50/40 ring-1 ring-emerald-200/70';
});

const handleClick = () => {
  emit('select', props.patient);
};
</script>

<template>
  <div
    class="billing-patient-card flex cursor-pointer overflow-hidden rounded-lg border text-left shadow-sm transition-all duration-200"
    :class="
      selected
        ? selectedFrameClass
        : 'border-gray-200 bg-white hover:border-gray-300 hover:bg-slate-50/80'
    "
    role="button"
    tabindex="0"
    @click="handleClick"
    @keydown.enter.prevent="handleClick"
  >
    <div
      class="w-1 shrink-0 self-stretch sm:w-1.5"
      :class="statusBarClass"
      aria-hidden="true"
    ></div>

    <div class="flex min-h-0 min-w-0 flex-1 flex-col gap-1 px-2 py-1.5">
      <div class="flex min-w-0 items-start justify-between gap-2">
        <div class="min-w-0 flex-1 truncate leading-tight">
          <span class="text-base font-semibold text-gray-900">{{ patient.patientName }}</span>
          <span class="ml-1 text-sm font-normal text-gray-500">
            {{ patient.gender }}·{{
              patient.ageText ?? (patient.age != null ? `${patient.age}岁` : '—')
            }}
          </span>
        </div>
        <ElTag
          v-if="showChargeStatus"
          :type="chargeStatusTagType"
          size="small"
          effect="dark"
          class="shrink-0 !font-normal"
        >
          {{ patient.chargeStatus }}
        </ElTag>
      </div>

      <div class="space-y-0.5 text-xs leading-tight text-gray-600">
        <div v-if="patient.inpatientNo" class="min-w-0 truncate">
          <span class="text-gray-400">住院号</span>
          {{ patient.inpatientNo }}
        </div>
        <div class="min-w-0 truncate">
          <span class="text-gray-400">{{ timeLabel }}</span>
          {{ patient.visitTime }}
        </div>
        <div
          v-if="patient.insuranceType"
          class="flex min-w-0 items-baseline justify-between gap-2 leading-tight text-gray-600"
        >
          <div class="min-w-0 flex-1 truncate text-xs">
            <span class="text-gray-400">{{ departmentLabel }}</span>
            {{ patient.visitDepartment }}
          </div>
          <span class="shrink-0 pl-2 text-right text-sm font-medium text-gray-800">
            {{ patient.insuranceType }}
          </span>
        </div>
        <div v-else class="min-w-0 truncate text-xs leading-tight text-gray-600">
          <span class="text-gray-400">{{ departmentLabel }}</span>
          {{ patient.visitDepartment }}
        </div>
        <div
          v-if="patient.accountBalanceSummary"
          class="flex min-w-0 items-start justify-between gap-2 text-xs leading-tight"
        >
          <span class="shrink-0 text-gray-400">账户余额</span>
          <span
            class="min-w-0 text-right font-medium"
            :class="patient.lowBalanceWarning ? 'text-red-600' : 'text-gray-800'"
          >
            <span>{{ patient.accountBalanceSummary }}</span>
            <span
              v-if="patient.accountBalanceSub"
              class="mt-0.5 block text-[11px] font-normal text-gray-500"
            >
              {{ patient.accountBalanceSub }}
            </span>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
