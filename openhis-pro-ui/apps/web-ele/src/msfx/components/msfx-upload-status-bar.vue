<!-- Opensource stub: optional module off -->
<script setup lang="ts">
defineOptions({ name: 'MsfxUploadStatusBar' });
defineProps<{
  busNo?: string | null;
  hisSceneEnum?: string;
  supplierEntId?: string | null;
  supplierEntName?: string | null;
  billTime?: string | null;
  compact?: boolean;
  needSupplierEntId?: boolean;
}>();
defineEmits<{
  'update:supplierEntId': [value: string];
  inboundCodesExpanded: [payload: unknown];
}>();
defineExpose({ reload: async () => {} });
</script>
<template></template>
