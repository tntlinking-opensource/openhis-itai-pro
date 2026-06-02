<script setup lang="ts">
import type { FormLayoutExpose } from '../types/FormLayout.d';

import { ref } from 'vue';

import FormLayout from './FormLayout.vue';

defineOptions({
  name: 'FormSectionLayout',
  inheritAttrs: false,
});

const formLayoutRef = ref<FormLayoutExpose | null>(null);

defineExpose({
  get formRef() {
    return formLayoutRef.value?.formRef;
  },
  validate: (...args: Parameters<FormLayoutExpose['validate']>) =>
    formLayoutRef.value?.validate(...args),
  validateField: (...args: Parameters<FormLayoutExpose['validateField']>) =>
    formLayoutRef.value?.validateField(...args),
  resetFields: (...args: Parameters<FormLayoutExpose['resetFields']>) =>
    formLayoutRef.value?.resetFields(...args),
  clearValidate: (...args: Parameters<FormLayoutExpose['clearValidate']>) =>
    formLayoutRef.value?.clearValidate(...args),
  scrollToField: (...args: Parameters<FormLayoutExpose['scrollToField']>) =>
    formLayoutRef.value?.scrollToField(...args),
});
</script>

<template>
  <div class="mb-4 flex-shrink-0">
    <FormLayout ref="formLayoutRef" v-bind="$attrs as any">
      <template v-for="(_, slotName) in $slots" :key="slotName" #[slotName]="slotProps">
        <slot :name="slotName" v-bind="slotProps"></slot>
      </template>
    </FormLayout>
  </div>
</template>
