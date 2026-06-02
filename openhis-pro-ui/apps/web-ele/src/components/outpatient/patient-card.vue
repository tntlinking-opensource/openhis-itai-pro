<script setup lang="ts">
import type {
  InpatientPatientCardPayload,
  OutpatientPatientCardPayload,
  PatientCardPatient,
  PatientCardVariant,
} from './patient-card.types';

import { computed } from 'vue';

import {
  formatDateTime,
  getInpatientStatusTagStyles,
  getOutpatientStatusTagStyles,
  getPaymentTypeTagStyles,
} from '@vben/utils';

import { encounterQueueStatusDisplayText } from '#/utils/encounter-queue-status-text';

defineOptions({
  name: 'PatientCard',
});

const props = withDefaults(
  defineProps<{
    patient: PatientCardPatient;
    selected?: boolean;
    variant?: PatientCardVariant;
  }>(),
  {
    variant: 'default',
  },
);

const emit = defineEmits<{
  select: [patient: PatientCardPatient];
  view: [patient: PatientCardPatient];
}>();

function isInpatientPayload(p: PatientCardPatient): p is InpatientPatientCardPayload {
  return (p as InpatientPatientCardPayload).patientType === 'inpatient';
}

function genderEnumToLabel(v: null | number | undefined): string {
  if (v === 1) return '男';
  if (v === 2) return '女';
  return '-';
}

function formatOutpatientVisitTime(o: OutpatientPatientCardPayload): string {
  const r = o.receptionTime ? formatDateTime(o.receptionTime) : '';
  if (r && r !== '-') return r;
  const s = o.startTime ? formatDateTime(o.startTime) : '';
  if (s && s !== '-') return s;
  if (o.receptionTime) return o.receptionTime;
  if (o.startTime) return o.startTime;
  return '-';
}

function ageLineOutpatient(ageText: null | string | undefined): string {
  const t = (ageText ?? '').trim();
  if (!t) return '-';
  if (t.includes('岁')) return t;
  return `${t}岁`;
}

/** 顶部标签第三段：就诊类别名称（如普通门诊），与底部费用标签解耦 */
function thirdMetaOutpatient(o: OutpatientPatientCardPayload): string {
  const name = (o.serviceTypeName ?? '').trim();
  return name || '-';
}

const variantResolved = computed(() => props.variant ?? 'default');

const display = computed(() => {
  const v = variantResolved.value;
  return {
    showPaymentTag: v !== 'dispense',
    /** 护士站不展示队列/在诊状态胶囊，避免与收费标签抢视觉 */
    showStatusTag: v !== 'dispense' && v !== 'nurse',
    showNurseAddon: v === 'nurse',
  };
});

const inpatient = computed(() => (isInpatientPayload(props.patient) ? props.patient : null));

const outpatient = computed(() =>
  isInpatientPayload(props.patient) ? null : (props.patient as OutpatientPatientCardPayload),
);

const isInpatient = computed(() => !!inpatient.value);

const statusConfig = computed(() => {
  const ip = inpatient.value;
  if (ip) {
    const status = ip.status;
    if (!status) return getInpatientStatusTagStyles('待入科');
    return getInpatientStatusTagStyles(status);
  }
  const o = outpatient.value;
  if (!o) return getOutpatientStatusTagStyles('待诊');
  const status = encounterQueueStatusDisplayText(o.statusEnum, o.statusEnumText);
  if (!status) return getOutpatientStatusTagStyles('待诊');
  return getOutpatientStatusTagStyles(status);
});

const feeChipLabel = computed(() => {
  const ip = inpatient.value;
  if (ip) {
    const label = (ip.paymentType ?? '').trim();
    if (variantResolved.value === 'doctor') return label || '—';
    return ip.paymentType || '自费';
  }
  const o = outpatient.value;
  if (!o) return '';
  // TODO(后端): 费用性质接口字段对齐后改为读模型值；当前写死展示
  return '自费';
});

const paymentTypeConfig = computed(() => getPaymentTypeTagStyles(feeChipLabel.value || '自费'));

const defaultStatus = computed(() => (isInpatient.value ? '待入科' : '待诊'));

const showPaymentChip = computed(() => {
  if (!display.value.showPaymentTag) return false;
  if (inpatient.value) {
    // 住院医生站与门诊医生站一致：底栏始终展示费用性质胶囊
    if (variantResolved.value === 'doctor') return true;
    return !!inpatient.value.paymentType;
  }
  const o = outpatient.value;
  if (!o) return false;
  // 门诊底部费用标签固定「自费」，在需展示费用区时始终出现
  return true;
});

const showStatusChip = computed(() => display.value.showStatusTag);

const statusChipText = computed(() => {
  const ip = inpatient.value;
  if (ip) return ip.status || defaultStatus.value;
  const o = outpatient.value;
  if (!o) return defaultStatus.value;
  return encounterQueueStatusDisplayText(o.statusEnum, o.statusEnumText) || defaultStatus.value;
});

const bottomLeftNo = computed(() => {
  const ip = inpatient.value;
  if (ip) return ip.visitNo ?? '-';
  const o = outpatient.value;
  if (!o) return '-';
  return o.encounterBusNo ?? o.encounterId ?? '-';
});

/** 门诊底栏左侧：护士站仅展示业务流水号，无流水则不占位（避免露出 encounterId 短数字） */
const outpatientFooterLeftText = computed(() => {
  const o = outpatient.value;
  if (!o) return '';
  if (variantResolved.value === 'nurse') {
    return (o.encounterBusNo ?? '').trim();
  }
  const bus = (o.encounterBusNo ?? '').trim();
  if (bus) return bus;
  return (o.encounterId ?? '').trim() || '-';
});

const handleSelect = () => {
  emit('select', props.patient);
};
</script>

<template>
  <div
    class="group relative cursor-pointer overflow-hidden rounded-xl border bg-white transition-all duration-200"
    :class="{
      'border-primary bg-primary/5 shadow-sm': selected,
      'hover:border-primary/30 border-gray-200 hover:bg-gray-50/50': !selected,
    }"
    @click="handleSelect"
  >
    <div class="relative p-3">
      <!-- 顶部：姓名 + 右上性别・年龄・第三段（门诊类别 / 住院床位） -->
      <div class="mb-3 flex items-center justify-between gap-2">
        <template
          v-if="inpatient && (variantResolved === 'doctor' || variantResolved === 'dispense')"
        >
          <span class="min-w-0 flex-1 truncate text-base font-medium text-gray-800">
            {{
              inpatient.isRegistered && (inpatient.name ?? '').trim() ? inpatient.name : '无档登记'
            }}
          </span>
        </template>
        <template v-else-if="inpatient">
          <span class="shrink-0 text-base font-medium text-gray-700">{{
            inpatient.queueNumber ?? '-'
          }}</span>
        </template>
        <template v-else-if="outpatient">
          <span class="min-w-0 flex-1 truncate text-base font-medium text-gray-800">
            {{
              outpatient.patientName && String(outpatient.patientName).trim()
                ? outpatient.patientName
                : '无档登记'
            }}
          </span>
        </template>
        <span class="shrink-0 text-right text-xs leading-snug text-gray-600">
          <template v-if="inpatient">
            {{ inpatient.gender }}・{{ inpatient.age }}岁・{{ inpatient.priority || '普通' }}
          </template>
          <template v-else-if="outpatient">
            {{
              (outpatient.genderEnumText ?? '').trim() || genderEnumToLabel(outpatient.genderEnum)
            }}・{{ ageLineOutpatient(outpatient.ageText) }}・{{ thirdMetaOutpatient(outpatient) }}
          </template>
        </span>
      </div>

      <div class="mb-3 space-y-1.5 text-xs text-gray-600">
        <template
          v-if="inpatient && (variantResolved === 'doctor' || variantResolved === 'dispense')"
        >
          <div class="flex items-center">
            <span class="text-gray-600">入院时间:</span>
            <span class="ml-1.5 font-medium text-gray-800">{{ inpatient.admissionTime }}</span>
          </div>
        </template>
        <template v-else-if="inpatient">
          <div class="flex items-center">
            <span class="text-gray-600">姓名:</span>
            <span class="ml-1.5 font-medium text-gray-800">{{
              inpatient.isRegistered ? inpatient.name || '-' : '无档登记'
            }}</span>
          </div>
          <div class="flex items-center">
            <span class="text-gray-600">住院号:</span>
            <span class="ml-1.5 font-medium text-gray-800">{{
              inpatient.hospitalizationNo || '-'
            }}</span>
          </div>
          <div class="flex items-center">
            <span class="text-gray-600">入院时间:</span>
            <span class="ml-1.5 font-medium text-gray-800">{{ inpatient.admissionTime }}</span>
          </div>
          <div class="flex items-center">
            <span class="text-gray-600">入科时间:</span>
            <span class="ml-1.5 font-medium text-gray-800">{{
              inpatient.departmentAdmissionTime || '-'
            }}</span>
          </div>
        </template>
        <template v-else-if="outpatient">
          <div class="flex items-center">
            <span class="text-gray-600">就诊时间:</span>
            <span class="ml-1.5 font-medium text-gray-800">{{
              formatOutpatientVisitTime(outpatient)
            }}</span>
          </div>
        </template>

        <template v-if="display.showNurseAddon && outpatient">
          <div
            v-if="outpatient.riskHint"
            class="rounded-md bg-amber-50 px-2 py-1 text-[11px] leading-snug text-amber-900"
          >
            {{ outpatient.riskHint }}
          </div>
        </template>
        <template v-if="display.showNurseAddon && inpatient">
          <div
            v-if="inpatient.riskHint"
            class="rounded-md bg-amber-50 px-2 py-1 text-[11px] leading-snug text-amber-900"
          >
            {{ inpatient.riskHint }}
          </div>
        </template>
      </div>

      <div class="mb-3 border-t border-dashed border-gray-300"></div>

      <div class="flex items-center justify-between gap-2">
        <span v-if="isInpatient" class="min-w-0 flex-1 truncate text-xs font-medium text-gray-800">
          {{ bottomLeftNo }}
        </span>
        <span
          v-else-if="outpatientFooterLeftText"
          class="min-w-0 flex-1 truncate text-xs font-medium text-gray-800"
        >
          {{ outpatientFooterLeftText }}
        </span>
        <span v-else class="min-w-0 flex-1" />
        <div class="flex min-w-0 shrink-0 flex-wrap items-center justify-end gap-2">
          <span
            v-if="showPaymentChip"
            class="shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium"
            :style="{
              backgroundColor: paymentTypeConfig.bgColor,
              color: paymentTypeConfig.textColor,
            }"
          >
            {{ feeChipLabel }}
          </span>
          <span
            v-if="showStatusChip"
            class="shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium"
            :style="{
              backgroundColor: statusConfig.bgColor,
              color: statusConfig.textColor,
            }"
          >
            {{ statusChipText }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
