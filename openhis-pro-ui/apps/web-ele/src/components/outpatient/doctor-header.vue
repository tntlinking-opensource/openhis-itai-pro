<script setup lang="ts">
import type { PatientInfoBarPayload } from './patient-card.types';

defineOptions({
  name: 'DoctorHeader',
});

withDefaults(defineProps<Props>(), {
  patient: () => ({
    patientName: '',
    genderEnum: undefined,
    ageText: '',
    phone: '',
    medicalRecordNo: '',
    serviceTypeName: '',
    startTime: '',
  }),
  doctorName: '',
  activeTab: 'outpatient',
  tabs: () => ['门诊病历', '诊断', '医嘱', '中医', '电子处方', '报告查询'],
});

const emit = defineEmits<{
  action: [action: string];
  tabChange: [tab: string];
}>();

interface Props {
  /** 与 `PatientInfoBar` 同构，供后续在表头区扩展展示 */
  patient?: PatientInfoBarPayload;
  doctorName?: string;
  activeTab?: string;
  tabs?: string[];
}
</script>

<template>
  <div
    class="doctor-header__tab-strip flex items-end justify-between gap-2 rounded-none bg-[var(--el-fill-color-light)] pb-0 pl-0 pr-1 pt-1"
  >
    <div class="flex flex-wrap items-end gap-1">
      <button
        v-for="tab in tabs"
        :key="tab"
        type="button"
        class="doctor-header__tab h-7 min-w-0 rounded-t-xl border border-b-0 px-3 text-[13px] font-medium leading-none transition-colors"
        :class="
          activeTab === tab
            ? 'border-[var(--el-color-primary)] bg-[var(--el-color-primary)] text-white shadow-sm'
            : 'text-text-sub hover:border-primary/40 hover:text-text-main border-[var(--el-border-color)] bg-white'
        "
        @click="emit('tabChange', tab)"
      >
        {{ tab }}
      </button>
    </div>
    <div class="flex flex-shrink-0 items-center gap-2 pb-1">
      <slot name="actions"></slot>
    </div>
  </div>
</template>
