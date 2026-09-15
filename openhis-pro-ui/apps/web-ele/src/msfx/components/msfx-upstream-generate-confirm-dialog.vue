<!-- Opensource stub -->
<script setup lang="ts">
defineOptions({ name: 'MsfxUpstreamGenerateConfirmDialog' });
defineProps<{ [key: string]: unknown }>();
defineEmits<{ confirm: [payload: unknown]; cancel: [] }>();
defineExpose({ open: (_payload?: unknown) => {} });
</script>
<template></template>
