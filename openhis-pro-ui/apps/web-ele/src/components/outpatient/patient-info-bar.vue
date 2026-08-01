<script setup lang="ts">
import type { PatientInfoBarPayload } from './patient-card.types';

import { computed } from 'vue';

import { Calendar, UserRoundPen } from '@vben/icons';
import { getPaymentTypeTagStyles } from '@vben/utils';

import dayjs from 'dayjs';
import { ElTag } from 'element-plus';

defineOptions({
  name: 'PatientInfoBar',
});

const props = withDefaults(defineProps<Props>(), {
  patient: () => ({
    encounterId: '',
    patientName: '',
    genderEnumText: undefined,
    genderEnum: undefined,
    ageText: '',
    phone: '',
    medicalRecordNo: '',
    serviceTypeName: '',
    startTime: '',
    statusEnum: undefined,
  }),
  doctorName: '',
});

interface Props {
  /** 与就诊列表/读模型字段一致 */
  patient?: PatientInfoBarPayload;
  doctorName?: string;
}

const paymentTypeTagConfig = computed(() => {
  const label = (props.patient.serviceTypeName ?? '').trim();
  if (!label) return null;
  return getPaymentTypeTagStyles(label);
});

const patientNameDisplay = computed(() => {
  const n = (props.patient.patientName ?? '').trim();
  return n || '—';
});

const ageDisplay = computed(() => {
  const t = (props.patient.ageText ?? '').trim();
  return t || '';
});

/** 挂号/就诊开始时间：月-日 时:分 */
const startTimeDisplay = computed(() => {
  const raw = props.patient.startTime;
  if (!raw || raw === '') return '';
  const parsed = dayjs(raw);
  if (!parsed.isValid()) return raw;
  return parsed.format('MM-DD HH:mm');
});
</script>

<template>
  <div
    class="mb-0 ml-0 mr-2 flex items-center justify-between gap-6 rounded-none bg-white px-4 py-2"
  >
    <div class="flex items-center gap-4">
      <div class="flex items-center gap-2">
        <UserRoundPen class="text-text-sub h-4 w-4" />
        <span class="text-text-sub text-sm font-medium">患者：</span>
        <span
          class="text-sm font-semibold"
          :class="(patient.patientName ?? '').trim() ? 'text-text-main' : 'text-text-sub'"
        >
          {{ patientNameDisplay }}
        </span>
      </div>

      <div class="flex items-center gap-2">
        <span class="text-text-main text-sm font-semibold">
          {{ (patient.genderEnumText ?? '').trim() || '—' }}
        </span>
        <span v-if="ageDisplay" class="text-text-main text-sm">{{ ageDisplay }}</span>
      </div>

      <div v-if="patient.phone" class="flex items-center gap-1.5">
        <span class="text-text-sub text-sm">电话：</span>
        <span class="text-text-main text-sm">{{ patient.phone }}</span>
      </div>

      <div v-if="patient.medicalRecordNo" class="flex items-center gap-1.5">
        <span class="text-text-sub text-sm">病历号：</span>
        <span class="text-text-main text-sm font-medium">{{ patient.medicalRecordNo }}</span>
      </div>

      <ElTag
        v-if="paymentTypeTagConfig"
        :style="{
          backgroundColor: paymentTypeTagConfig.bgColor,
          color: paymentTypeTagConfig.textColor,
          borderColor: paymentTypeTagConfig.bgColor,
        }"
      >
        {{ patient.serviceTypeName }}
      </ElTag>
    </div>

    <div class="bg-border h-6 w-px"></div>

    <div class="flex items-center gap-4">
      <div v-if="patient.startTime" class="flex items-center gap-1.5">
        <Calendar class="text-text-sub h-3.5 w-3.5" />
        <span class="text-text-sub text-sm">挂号时间：</span>
        <span class="text-text-main text-sm">{{ startTimeDisplay }}</span>
      </div>

      <div v-if="doctorName" class="flex items-center gap-1.5">
        <span class="text-text-sub text-sm">医生：</span>
        <span class="text-text-main text-sm font-medium">{{ doctorName }}</span>
      </div>
    </div>
  </div>
</template>
