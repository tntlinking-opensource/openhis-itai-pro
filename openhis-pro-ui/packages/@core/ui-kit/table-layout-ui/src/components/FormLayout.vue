<script setup lang="ts">
import type { FormLayoutProps } from '../types/FormLayout.d';

import { computed, ref } from 'vue';

import FormItem from './FormItem.vue';

defineOptions({
  name: 'FormLayout',
});

const props = withDefaults(defineProps<FormLayoutProps>(), {
  formItems: () => [],
  rules: () => ({}),
  labelWidth: '140px',
  labelPosition: 'right',
  showLabelColon: true,
  columns: 0,
});

const formRef = ref<InstanceType<typeof import('element-plus').ElForm> | null>(null);

const normalizedFormItems = computed(() =>
  (props.formItems || []).map((item) => ({
    ...item,
    labelSuffix: item.labelSuffix ?? (props.showLabelColon ? '：' : ''),
  })),
);

const validate = (callback?: (valid: boolean) => void) => {
  if (formRef.value) {
    return formRef.value.validate(callback);
  }
};

const validateField = (
  props: string | string[],
  callback?: (isValid: boolean, invalidFields: any) => void,
) => {
  if (formRef.value) {
    return formRef.value.validateField(props, callback);
  }
};

const resetFields = () => {
  if (formRef.value) {
    formRef.value.resetFields();
  }
};

const clearValidate = (props?: string | string[]) => {
  if (formRef.value) {
    formRef.value.clearValidate(props);
  }
};

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
    :label-position="labelPosition"
    class="w-full"
  >
    <div class="flex flex-wrap gap-4" :class="columns > 0 ? `form-layout-${columns}col` : ''">
      <template v-for="(item, index) in normalizedFormItems" :key="item.prop">
        <FormItem
          :item="item"
          :model-value="model[item.prop]"
          @update:model-value="
            async (value) => {
              if (item.onChange && typeof item.onChange === 'function') {
                const result = await item.onChange(value);
                if (result === false) {
                  return;
                }
              }
              model[item.prop] = value;
            }
          "
        >
          <template v-for="(_, slotName) in $slots" :key="slotName" #[slotName]="slotProps">
            <slot :name="slotName" v-bind="slotProps"></slot>
          </template>
        </FormItem>
        <span
          v-if="
            columns > 0 &&
            index > 0 &&
            (index + 1) % columns === 0 &&
            index < normalizedFormItems.length - 1
          "
          class="m-0 h-0 w-0 basis-full border-0 p-0"
        ></span>
      </template>
    </div>
  </el-form>
</template>

<style scoped lang="scss">
:deep(.el-form-item) {
  flex: 0 0 auto;
  justify-content: flex-start;
  margin-bottom: 0;

  .el-form-item__content {
    justify-content: flex-start;
    text-align: left;
  }
}
</style>
