<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { ElCheckbox } from 'element-plus';

interface CheckboxField {
  fieldName: string;
  label: string;
}

interface Props {
  fields?: CheckboxField[];
  modelValue?: Record<string, boolean>;
}

const props = withDefaults(defineProps<Props>(), {
  fields: () => [],
  modelValue: () => ({}),
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, boolean>): void;
  (e: 'change', value: Record<string, boolean>): void;
}>();

const checkboxFields = computed(() => props.fields || []);

const fieldValues = ref<Record<string, boolean>>({ ...props.modelValue });

watch(
  () => props.modelValue,
  (val) => {
    fieldValues.value = { ...val };
  },
  { deep: true },
);

const handleChange = (fieldName: string, checked: boolean | number | string) => {
  const boolValue = checked === true || checked === 1 || checked === '1' || checked === 'true';

  fieldValues.value = {
    ...fieldValues.value,
    [fieldName]: boolValue,
  };

  const newValue: Record<string, boolean> = {
    ...props.modelValue,
    [fieldName]: boolValue,
  };

  emit('update:modelValue', newValue);
  emit('change', newValue);
};
</script>

<template>
  <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
    <ElCheckbox
      v-for="field in checkboxFields"
      :key="field.fieldName"
      v-model="fieldValues[field.fieldName]"
      @change="(val: any) => handleChange(field.fieldName, val)"
    >
      {{ field.label }}
    </ElCheckbox>
  </div>
</template>
