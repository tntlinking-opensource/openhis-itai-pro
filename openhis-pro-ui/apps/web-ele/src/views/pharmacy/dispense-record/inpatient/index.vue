<script setup lang="ts">
/**
 * 兼容后端菜单历史 component 路径（views/pharmacy/dispense-record/inpatient）。
 * 业务已演进为报表中心「住院发药报表」。
 */
import MedicationDispenseReportPage from '#/views/report/medication-dispense/medication-dispense-report-page.vue';

defineOptions({ name: 'PharmacyInpatientDispenseRecord' });
</script>

<template>
  <MedicationDispenseReportPage scope="inpatient" />
</template>
