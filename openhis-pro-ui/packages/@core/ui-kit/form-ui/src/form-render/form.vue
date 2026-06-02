<script setup lang="ts">
import type { GenericObject } from 'vee-validate';
import type { ZodTypeAny } from 'zod';

import type { FormCommonConfig, FormRenderProps, FormSchema, FormShape } from '../types';

import { computed } from 'vue';

import { Form } from '@vben-core/shadcn-ui';
import { cn, isFunction, isString, mergeWithArrayOverride } from '@vben-core/shared/utils';

import { provideFormRenderProps } from './context';
import { useExpandable } from './expandable';
import FormField from './form-field.vue';
import { getBaseRules, getDefaultValueInZodStack } from './helper';

interface Props extends FormRenderProps {}

const props = withDefaults(defineProps<Props & { globalCommonConfig?: FormCommonConfig }>(), {
  collapsedRows: 1,
  commonConfig: () => ({}),
  globalCommonConfig: () => ({}),
  showCollapseButton: false,
  wrapperClass: 'grid-cols-1 sm:grid-cols-2 md:grid-cols-3',
});

const emits = defineEmits<{
  submit: [event: any];
}>();

const wrapperClass = computed(() => {
  const cls = ['flex'];
  if (props.layout === 'inline') {
    cls.push('flex-wrap gap-x-2');
  } else {
    cls.push(props.compact ? 'gap-x-2' : 'gap-x-4', 'flex-col grid');
  }
  return cn(...cls, props.wrapperClass);
});

provideFormRenderProps(props);

const { isCalculated, keepFormItemIndex, wrapperRef } = useExpandable(props);

const shapes = computed(() => {
  const resultShapes: FormShape[] = [];
  props.schema?.forEach((schema) => {
    const { fieldName } = schema;
    const rules = schema.rules as ZodTypeAny;

    let typeName = '';
    if (rules && !isString(rules)) {
      typeName = rules._def.typeName;
    }

    const baseRules = getBaseRules(rules) as ZodTypeAny;

    resultShapes.push({
      default: getDefaultValueInZodStack(rules),
      fieldName,
      required: !['ZodNullable', 'ZodOptional'].includes(typeName),
      rules: baseRules,
    });
  });
  return resultShapes;
});

const formComponent = computed(() => (props.form ? 'form' : Form));

const formComponentProps = computed(() => {
  return props.form
    ? {
        onSubmit: props.form.handleSubmit((val) => emits('submit', val)),
      }
    : {
        onSubmit: (val: GenericObject) => emits('submit', val),
      };
});

const formCollapsed = computed(() => {
  return props.collapsed && isCalculated.value;
});

const computedSchema = computed(
  (): (Omit<FormSchema, 'formFieldProps'> & {
    commonComponentProps: Record<string, any>;
    formFieldProps: Record<string, any>;
  })[] => {
    const {
      colon = false,
      componentProps = {},
      controlClass = '',
      disabled,
      disabledOnChangeListener = true,
      disabledOnInputListener = true,
      emptyStateValue = undefined,
      formFieldProps = {},
      formItemClass = '',
      hideLabel = false,
      hideRequiredMark = false,
      labelClass = '',
      labelWidth = 100,
      modelPropName = '',
      wrapperClass = '',
    } = mergeWithArrayOverride(props.commonConfig, props.globalCommonConfig);
    return (props.schema || []).map((schema, index) => {
      const keepIndex = keepFormItemIndex.value;

      const hidden =
        // 折叠状态 & 显示折叠按钮 & 当前索引大于保留索引
        props.showCollapseButton && !!formCollapsed.value && keepIndex ? keepIndex <= index : false;

      // 处理函数形式的formItemClass
      let resolvedSchemaFormItemClass = schema.formItemClass;
      if (isFunction(schema.formItemClass)) {
        try {
          resolvedSchemaFormItemClass = schema.formItemClass();
        } catch (error) {
          console.error('Error calling formItemClass function:', error);
          resolvedSchemaFormItemClass = '';
        }
      }

      // 自动为 textarea 类型的 Input 添加 col-span-2
      const componentPropsValue = isFunction(schema.componentProps)
        ? undefined
        : schema.componentProps;
      const isTextarea =
        schema.component === 'Input' &&
        componentPropsValue &&
        typeof componentPropsValue === 'object' &&
        'type' in componentPropsValue &&
        componentPropsValue.type === 'textarea';

      const textareaRows =
        isTextarea &&
        componentPropsValue &&
        typeof componentPropsValue === 'object' &&
        'rows' in componentPropsValue
          ? Number(componentPropsValue.rows) || 0
          : 0;
      const hasRows2OrMore = isTextarea && textareaRows >= 2;

      const resolvedFormItemClassStr = isString(resolvedSchemaFormItemClass)
        ? resolvedSchemaFormItemClass
        : '';
      const textareaClass =
        isTextarea && !resolvedFormItemClassStr.includes('col-span') ? 'col-span-3' : '';

      // 如果是 textarea 且 rows >= 2，标签相对于第一行居中
      const textareaLabelClass = hasRows2OrMore ? 'textarea-label-top-center' : '';

      return {
        colon,
        disabled,
        disabledOnChangeListener,
        disabledOnInputListener,
        emptyStateValue,
        hideLabel,
        hideRequiredMark,
        labelWidth,
        modelPropName,
        wrapperClass,
        ...schema,
        commonComponentProps: componentProps,
        componentProps: schema.componentProps,
        controlClass: cn(controlClass, schema.controlClass),
        formFieldProps: {
          ...formFieldProps,
          ...schema.formFieldProps,
        },
        formItemClass: cn(
          'flex-shrink-0',
          { hidden },
          formItemClass,
          textareaClass,
          resolvedSchemaFormItemClass,
        ),
        labelClass: cn(labelClass, schema.labelClass, textareaLabelClass),
      };
    });
  },
);
</script>

<template>
  <component :is="formComponent" v-bind="formComponentProps">
    <div ref="wrapperRef" :class="wrapperClass">
      <template v-for="cSchema in computedSchema" :key="cSchema.fieldName">
        <!-- <div v-if="$slots[cSchema.fieldName]" :class="cSchema.formItemClass">
          <slot :definition="cSchema" :name="cSchema.fieldName"> </slot>
        </div> -->
        <FormField v-bind="cSchema" :class="cSchema.formItemClass" :rules="cSchema.rules">
          <template #default="slotProps">
            <slot v-bind="slotProps" :name="cSchema.fieldName"> </slot>
          </template>
        </FormField>
      </template>
      <slot :shapes="shapes"></slot>
    </div>
  </component>
</template>
