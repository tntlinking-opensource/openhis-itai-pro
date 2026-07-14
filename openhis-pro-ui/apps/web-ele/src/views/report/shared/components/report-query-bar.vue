<script setup lang="ts">
import { ElForm } from 'element-plus';

defineOptions({ name: 'ReportQueryBar', inheritAttrs: false });

interface Props {
  /** ElForm model，用于查询区双向绑定。 */
  model: Record<string, unknown>;
  labelWidth?: string;
}

withDefaults(defineProps<Props>(), {
  labelWidth: '88px',
});
</script>

<template>
  <div class="report-list-page__query-bar" v-bind="$attrs">
    <ElForm :model="model" :label-width="labelWidth" class="report-list-page__query-form">
      <div class="report-list-page__query-fields">
        <slot name="fields" />
      </div>
      <div class="report-list-page__query-actions">
        <slot name="actions" />
      </div>
    </ElForm>
  </div>
</template>

<style lang="scss">
@use '../report-list-page.scss';
</style>
