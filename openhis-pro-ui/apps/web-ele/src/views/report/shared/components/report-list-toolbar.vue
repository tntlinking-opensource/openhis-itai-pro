<script setup lang="ts">
import { ElTag } from 'element-plus';

defineOptions({ name: 'ReportListToolbar' });

interface Props {
  title: string;
  total?: number;
  showTotal?: boolean;
}

withDefaults(defineProps<Props>(), {
  total: 0,
  showTotal: true,
});
</script>

<template>
  <div class="report-list-page__list-toolbar">
    <div class="report-list-page__list-toolbar-left">
      <span class="report-list-page__list-title">{{ title }}</span>
      <ElTag v-if="showTotal" type="info" effect="plain">共 {{ total }} 条</ElTag>
      <slot name="meta" />
    </div>
    <div class="report-list-page__list-toolbar-actions">
      <slot name="actions" />
    </div>
  </div>
</template>
