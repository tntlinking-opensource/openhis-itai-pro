<script setup lang="ts">
defineOptions({ name: 'RibbonBtn' });

withDefaults(
  defineProps<{
    title?: string;
    active?: boolean;
    disabled?: boolean;
    wide?: boolean;
    inline?: boolean;
    caption?: string;
  }>(),
  {
    title: '',
    active: false,
    disabled: false,
    wide: false,
    inline: false,
    caption: '',
  },
);
</script>

<template>
  <button
    type="button"
    class="rib-btn"
    :class="{ 'is-on': active, 'is-wide': wide, 'is-inline': inline }"
    :title="title"
    :disabled="disabled"
  >
    <span class="rib-btn__icon">
      <slot />
    </span>
    <span v-if="caption" class="rib-btn__caption">{{ caption }}</span>
  </button>
</template>
