<script setup lang="ts">
import type { NursePatient } from './types';

import type {
  OutpatientPatientCardPayload,
  PatientCardPatient,
} from '#/components/outpatient/patient-card.types';
import type { OptionDto } from '#/api/outpatient-registration';

import { RotateCw, Search } from '@vben/icons';

import { ElButton, ElDatePicker, ElInput, ElSelect, ElOption } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import PatientCard from '#/components/outpatient/patient-card.vue';
import { useSingleDayDatePickerShortcuts } from '#/composables/use-single-day-date-picker-shortcuts';

import { toPatientCardPatient } from './nurse-patient-map';

defineOptions({
  name: 'NurseQueuePanel',
});

const props = withDefaults(defineProps<Props>(), {
  queueLoading: false,
  orgOptions: () => [],
});

const emit = defineEmits<{
  orgChange: [orgId: string];
  queueDateChange: [date: string | undefined];
  refresh: [];
  selectPatient: [patient: { id: string }];
}>();

interface Props {
  filteredPatients: NursePatient[];
  /** 开立科室下拉（空 value 表示「全部」由父级用空串表达） */
  orgOptions?: OptionDto[];
  queueLoading?: boolean;
  selectedPatientId: string;
}

const keyword = defineModel<string>('keyword', { required: true });
const queueDate = defineModel<string>('queueDate', { required: true });
/** 开立科室：空串=全部 */
const orgId = defineModel<string>('orgId', { required: true });

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

/** 开立科室变更：通知父级立即重查（默认全部） */
function handleOrgChange(value: string | number | boolean | undefined) {
  const next = value === undefined || value === null ? '' : String(value);
  orgId.value = next;
  emit('orgChange', next);
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
      <SectionTitle title="患者队列" />
      <ElSelect
        :model-value="orgId || undefined"
        clearable
        filterable
        placeholder="开立科室（全部）"
        class="w-full"
        @change="handleOrgChange"
        @clear="handleOrgChange(undefined)"
      >
        <ElOption
          v-for="opt in props.orgOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </ElSelect>
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
