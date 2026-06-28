<script setup lang="ts">
import PharmacyDispenseRecordPage from '../components/pharmacy-dispense-record-page.vue';

defineOptions({ name: 'PharmacyInpatientDispenseRecord' });
</script>

<template>
  <PharmacyDispenseRecordPage scope="inpatient" />
</template>
