<script setup lang="ts">
import type { PatientCardPatient } from '#/components/outpatient/patient-card.types';

import type { InpatientDoctorStationPatient } from '../doctorPatientStore';

import { computed } from 'vue';

import PatientCard from '#/components/outpatient/patient-card.vue';

import { mapInpatientDoctorStationPatientToCardPayload } from '../services/map-inpatient-doctor-patient-card';

defineOptions({ name: 'InpatientDoctorPatientCard' });

const props = withDefaults(
  defineProps<{
    patient: InpatientDoctorStationPatient;
    selected?: boolean;
  }>(),
  { selected: false },
);

const emit = defineEmits<{
  select: [patient: InpatientDoctorStationPatient];
}>();

const cardPatient = computed(() => mapInpatientDoctorStationPatientToCardPayload(props.patient));

function handleSelect(_card: PatientCardPatient) {
  emit('select', props.patient);
}
</script>

<template>
  <PatientCard
    variant="doctor"
    :patient="cardPatient"
    :selected="selected"
    @select="handleSelect"
  />
</template>
