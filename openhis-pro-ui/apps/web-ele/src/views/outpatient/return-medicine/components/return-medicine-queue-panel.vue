<script setup lang="ts">
import type { ReturnMedicineQueuePatient } from './types';

import type {
  OutpatientPatientCardPayload,
  PatientCardPatient,
} from '#/components/outpatient/patient-card.types';

import { ref } from 'vue';

import { RotateCw, Search } from '@zxplat/icons';

import { ElButton, ElDatePicker, ElInput, ElRadio, ElRadioGroup } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import PatientCard from '#/components/outpatient/patient-card.vue';
import { useSingleDayDatePickerShortcuts } from '#/composables/use-single-day-date-picker-shortcuts';

import { toReturnPatientCardModel } from './return-patient-card-map';

defineOptions({
  name: 'ReturnMedicineQueuePanel',
});

const props = withDefaults(
  defineProps<{
    filteredPatients: ReturnMedicineQueuePatient[];
    loadingQueue?: boolean;
    selectedPatientId: null | string;
  }>(),
  {
    loadingQueue: false,
  },
);

const emit = defineEmits<{
  queueDateChange: [date: string | undefined];
  refresh: [];
  selectPatient: [patient: { id: string }];
}>();

const keyword = defineModel<string>('keyword', { required: true });
const queueDate = defineModel<string>('queueDate', { required: true });
/** 与门诊发药 pending/completed 同构，父级映射为 PENDING_REFUND / REFUNDED */
const queueScope = defineModel<'completed' | 'pending'>('queueScope', { required: true });

const { dateShortcuts, singleDayPopperClass } = useSingleDayDatePickerShortcuts(
  () => queueDate.value,
  (ymd) => {
    queueDate.value = ymd;
    emit('queueDateChange', ymd);
  },
);

const isQueueListRefreshing = ref(false);

/** 触发展示旋转动画并通知父级重新拉队列 */
function handleRefresh() {
  isQueueListRefreshing.value = true;
  emit('refresh');
  window.setTimeout(() => {
    isQueueListRefreshing.value = false;
  }, 400);
}

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
      <SectionTitle title="退药患者" />
      <ElRadioGroup
        v-model="queueScope"
        class="return-queue-scope flex flex-wrap items-center gap-x-4 gap-y-1"
      >
        <ElRadio value="pending">待退药</ElRadio>
        <ElRadio value="completed">已退药</ElRadio>
      </ElRadioGroup>
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
        <ElButton type="default" class="!px-2" :loading="props.loadingQueue" @click="handleRefresh">
          <RotateCw class="h-4 w-4" :class="{ 'animate-spin': isQueueListRefreshing }" />
        </ElButton>
      </div>
    </div>
    <div class="queue-list min-h-0 flex-1 space-y-1.5 overflow-y-auto px-2 pb-1">
      <template v-if="filteredPatients.length === 0">
        <div class="text-text-sub py-8 text-center text-sm">暂无匹配患者</div>
      </template>
      <template v-else>
        <PatientCard
          v-for="p in filteredPatients"
          :key="p.id"
          variant="dispense"
          :patient="toReturnPatientCardModel(p)"
          :selected="selectedPatientId === p.id"
          @select="onPatientCardSelect"
        />
      </template>
    </div>
  </div>
</template>
