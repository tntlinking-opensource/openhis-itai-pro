<script setup lang="ts">
/**
 * 门诊挂号列表皮肤封装：对齐 `outpatient/charge/register/components/history.vue` 的 TableLayout 视觉。
 * 收费域列表统一使用本组件，避免 ElTable / 默认 TableLayout 边框斑马纹风格漂移。
 */
import { TableLayout } from '@zxplat-core/table-layout-ui';

import { computed, ref, useAttrs, useSlots } from 'vue';

import './register-style-table-layout.css';

defineOptions({
  name: 'RegisterStyleTableLayout',
  inheritAttrs: false,
});

const props = withDefaults(
  defineProps<{
    /** 默认无外边框，对齐挂号历史列表 */
    border?: boolean;
    /**
     * 是否隐藏 vxe 工具条。
     * 默认：未传 toolbar-actions / toolbar-tools 时自动隐藏，避免空 toolbar 占位成「空行」。
     */
    hideToolbar?: boolean;
    size?: 'default' | 'mini' | 'small';
    /** 默认无斑马纹，对齐挂号历史列表 */
    stripe?: boolean;
  }>(),
  {
    border: false,
    size: 'small',
    stripe: false,
  },
);

const attrs = useAttrs();
const slots = useSlots();
const innerRef = ref<InstanceType<typeof TableLayout> | null>(null);

/** 无自定义工具条时隐藏空 toolbar */
const shouldHideToolbar = computed(() => {
  if (props.hideToolbar !== undefined) {
    return props.hideToolbar;
  }
  return !slots['toolbar-actions'] && !slots['toolbar-tools'];
});

/** 透传 class，保留挂号皮肤根类名 */
const rootClass = computed(() => {
  const extra = attrs.class;
  const base = shouldHideToolbar.value
    ? ['register-style-table', 'register-style-table--hide-toolbar']
    : ['register-style-table'];
  return extra ? [...base, extra] : base;
});

/** 去掉 class，避免与根类重复绑定 */
const passThroughAttrs = computed(() => {
  const { class: _class, ...rest } = attrs as Record<string, unknown>;
  return rest;
});

defineExpose({
  clearSelection: () => innerRef.value?.clearSelection?.(),
  gridApi: computed(() => innerRef.value?.gridApi),
  handleQuery: (...args: unknown[]) =>
    (innerRef.value as null | { handleQuery?: (...a: unknown[]) => unknown })?.handleQuery?.(
      ...args,
    ),
  queryFormRef: computed(() => innerRef.value?.queryFormRef),
  resetQuery: (...args: unknown[]) =>
    (innerRef.value as null | { resetQuery?: (...a: unknown[]) => unknown })?.resetQuery?.(...args),
});
</script>

<template>
  <TableLayout
    ref="innerRef"
    :class="rootClass"
    v-bind="passThroughAttrs"
    :border="props.border"
    :size="props.size"
    :stripe="props.stripe"
  >
    <template v-for="(_, name) in slots" :key="name" #[name]="slotData">
      <slot :name="name" v-bind="slotData || {}" />
    </template>
  </TableLayout>
</template>
