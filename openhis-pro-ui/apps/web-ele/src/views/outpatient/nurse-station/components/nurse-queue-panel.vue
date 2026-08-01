<script setup lang="ts">
import type { NursePatient } from './types';

import type {
  OutpatientPatientCardPayload,
  PatientCardPatient,
} from '#/components/outpatient/patient-card.types';

import { RotateCw, Search } from '@vben/icons';

import { ElButton, ElDatePicker, ElInput } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import PatientCard from '#/components/outpatient/patient-card.vue';
import { useSingleDayDatePickerShortcuts } from '#/composables/use-single-day-date-picker-shortcuts';

import { toPatientCardPatient } from './nurse-patient-map';

defineOptions({
  name: 'NurseQueuePanel',
});

const props = defineProps<Props>();

const emit = defineEmits<{
  queueDateChange: [date: string | undefined];
  refresh: [];
  selectPatient: [patient: { id: string }];
}>();

interface Props {
  filteredPatients: NursePatient[];
  queueLoading?: boolean;
  selectedPatientId: string;
}

const keyword = defineModel<string>('keyword', { required: true });
const queueDate = defineModel<string>('queueDate', { required: true });

const { dateShortcuts, singleDayPopperClass } = useSingleDayDatePickerShortcuts(
  () => queueDate.value,
  (ymd) => {
    queueDate.value = ymd;
    emit('queueDateChange', ymd);
  },
);

/** 通知父级重新拉取队列（由父级控制 loading） */
const handleRefresh = () => {
  emit('refresh');
};

function onPatientCardSelect(card: PatientCardPatient) {
  if ('patientType' in card && card.patientType === 'inpatient') {
    emit('selectPatient', { id: card.id });
    return;
  }
  emit('selectPatient', { id: (card as OutpatientPatientCardPayload).encounterId });
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <div class="mb-1.5 flex flex-col gap-1.5 px-2 pt-1.5">
      <SectionTitle title="患者队列" />
      <ElInput v-model="keyword" clearable placeholder="姓名 / 排队号" class="flex-1">
        <template #prefix>
          <Search class="h-4 w-4" />
        </template>
      </ElInput>
      <div class="flex items-center gap-1.5">
        <ElDatePicker
          v-model="queueDate"
          class="w-full flex-1"
          type="date"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          placeholder="请选择日期"
          :shortcuts="dateShortcuts"
          :popper-class="singleDayPopperClass"
          @change="emit('queueDateChange', $event as string | undefined)"
        />
        <ElButton type="default" class="!px-2" @click="handleRefresh">
          <RotateCw class="h-4 w-4" :class="{ 'animate-spin': props.queueLoading }" />
        </ElButton>
      </div>
    </div>
    <div class="queue-list min-h-0 flex-1 space-y-1.5 overflow-y-auto px-2 pb-1">
      <template v-if="filteredPatients.length === 0">
        <div class="text-text-sub py-8 text-center text-sm">暂无匹配患者</div>
      </template>
      <template v-else>
        <PatientCard
          v-for="patient in filteredPatients"
          :key="patient.id"
          variant="nurse"
          :patient="toPatientCardPatient(patient)"
          :selected="patient.id === selectedPatientId"
          @select="onPatientCardSelect"
        />
      </template>
    </div>
  </div>
</template>
