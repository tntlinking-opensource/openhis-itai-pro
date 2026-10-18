<script setup lang="ts">
import type { PatientInfoBarPayload } from './patient-card.types';

import { computed } from 'vue';

import { Calendar, UserRoundPen } from '@zxplat/icons';
import { getPaymentTypeTagStyles } from '@zxplat/utils';

import dayjs from 'dayjs';
import { ElTag } from 'element-plus';

defineOptions({
  name: 'PatientInfoBar',
});

interface Props {
  /** 与就诊列表/读模型字段一致 */
  patient?: PatientInfoBarPayload;
  doctorName?: string;
  /** 开始时间文案（默认「挂号时间」；住院传「入院时间」） */
  startTimeLabel?: string;
  /** 本次就诊费用合计展示文案；无就诊时不传 */
  feeTotalText?: string;
}

const props = withDefaults(defineProps<Props>(), {
  patient: () => ({
    encounterId: '',
    patientName: '',
    genderEnumText: undefined,
    genderEnum: undefined,
    ageText: '',
    phone: '',
    medicalRecordNo: '',
    idCard: '',
    address: '',
    serviceTypeName: '',
    startTime: '',
    statusEnum: undefined,
  }),
  doctorName: '',
  startTimeLabel: '挂号时间',
  feeTotalText: '',
});

const showFeeSummary = computed(() => Boolean(props.feeTotalText));

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

/** 手机号展示（无值显示 —） */
const phoneDisplay = computed(() => (props.patient.phone ?? '').trim() || '—');

/** 身份证号展示（无值显示 —） */
const idCardDisplay = computed(() => (props.patient.idCard ?? '').trim() || '—');

/** 患者地址全文（有值才展示） */
const addressDisplay = computed(() => {
  return (props.patient.address ?? '').trim();
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
    class="mb-0 ml-0 mr-2 flex flex-wrap items-start justify-between gap-x-6 gap-y-1 rounded-none bg-white px-4 py-2"
  >
    <div class="flex min-w-0 flex-1 flex-wrap items-center gap-x-4 gap-y-1">
      <div class="flex items-center gap-2">
        <UserRoundPen class="text-text-sub h-4 w-4 shrink-0" />
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

      <div class="flex items-center gap-1.5">
        <span class="text-text-sub text-sm">手机号：</span>
        <span class="text-text-main text-sm">{{ phoneDisplay }}</span>
      </div>

      <div v-if="patient.medicalRecordNo" class="flex items-center gap-1.5">
        <span class="text-text-sub text-sm">病历号：</span>
        <span class="text-text-main text-sm font-medium">{{ patient.medicalRecordNo }}</span>
      </div>

      <div class="flex items-center gap-1.5">
        <span class="text-text-sub text-sm">身份证号：</span>
        <span class="text-text-main text-sm">{{ idCardDisplay }}</span>
      </div>

      <div v-if="addressDisplay" class="flex max-w-full items-start gap-1.5">
        <span class="text-text-sub shrink-0 text-sm">地址：</span>
        <span class="text-text-main whitespace-normal break-all text-sm">{{ addressDisplay }}</span>
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

    <div class="bg-border mt-0.5 h-6 w-px shrink-0"></div>

    <div class="flex min-w-0 flex-wrap items-center justify-end gap-x-4 gap-y-1">
      <div v-if="patient.startTime" class="flex items-center gap-1.5">
        <Calendar class="text-text-sub h-3.5 w-3.5" />
        <span class="text-text-sub text-sm">{{ startTimeLabel }}：</span>
        <span class="text-text-main text-sm">{{ startTimeDisplay }}</span>
      </div>

      <div v-if="doctorName" class="flex items-center gap-1.5">
        <span class="text-text-sub text-sm">医生：</span>
        <span class="text-text-main text-sm font-medium">{{ doctorName }}</span>
      </div>

      <template v-if="showFeeSummary">
        <div class="bg-border mt-0.5 h-6 w-px shrink-0"></div>
        <div class="flex items-center gap-1 text-sm">
          <span class="text-text-sub">总费用</span>
          <span class="text-text-main font-semibold">{{ feeTotalText }}</span>
        </div>
      </template>
    </div>
  </div>
</template>
