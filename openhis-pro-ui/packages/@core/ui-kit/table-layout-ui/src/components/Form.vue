<script setup lang="ts">
import type { FormProps } from '../types/Form.d';

import { computed, ref } from 'vue';

import FormItem from './FormItem.vue';

defineOptions({
  name: 'Form',
});

const props = withDefaults(defineProps<FormProps>(), {
  formItems: () => [],
  rules: () => ({}),
  labelWidth: '120px',
  inline: false,
  labelPosition: 'right',
  showLabelColon: true,
});

defineEmits<{
  validate: [callback?: (valid: boolean) => void];
}>();

const formRef = ref<InstanceType<typeof import('element-plus').ElForm> | null>(null);

const normalizedFormItems = computed(() =>
  (props.formItems || []).map((item) => ({
    ...item,
    labelSuffix: item.labelSuffix ?? (props.showLabelColon ? '：' : ''),
  })),
);

// 表单验证
const validate = (callback?: (valid: boolean) => void) => {
  if (formRef.value) {
    return formRef.value.validate(callback);
  }
};

// 验证指定字段
const validateField = (
  props: string | string[],
  callback?: (isValid: boolean, invalidFields: any) => void,
) => {
  if (formRef.value) {
    return formRef.value.validateField(props, callback);
  }
};

// 重置表单
const resetFields = () => {
  if (formRef.value) {
    formRef.value.resetFields();
  }
};

// 清除验证
const clearValidate = (props?: string | string[]) => {
  if (formRef.value) {
    formRef.value.clearValidate(props);
  }
};

// 滚动到指定字段
const scrollToField = (prop: string) => {
  if (formRef.value) {
    formRef.value.scrollToField(prop);
  }
};

defineExpose({
  formRef,
  validate,
  validateField,
  resetFields,
  clearValidate,
  scrollToField,
});
</script>

<template>
  <el-form
    ref="formRef"
    :model="model"
    :rules="rules"
    :label-width="labelWidth"
    :inline="inline"
    :label-position="labelPosition"
    class="w-full"
  >
    <template v-for="item in normalizedFormItems" :key="item.prop">
      <FormItem
        :item="item"
        :model-value="model[item.prop]"
        @update:model-value="(value) => (model[item.prop] = value)"
        @change="(value) => item.onChange && item.onChange(value)"
      >
        <template v-for="(_, slotName) in $slots" :key="slotName" #[slotName]="slotProps">
          <slot :name="slotName" v-bind="slotProps"></slot>
        </template>
      </FormItem>
    </template>
  </el-form>
</template>

<style scoped lang="scss">
:deep(.el-form:not(.el-form--inline) .el-form-item) {
  display: flex;
  margin-right: 0;
}
</style>
