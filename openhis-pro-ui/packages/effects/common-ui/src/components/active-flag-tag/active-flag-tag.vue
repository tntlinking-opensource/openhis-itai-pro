<script lang="ts" setup>
import { computed } from 'vue';

import { ElTag } from 'element-plus';

interface Props {
  activeFlag?: number | string;
  enumText?: string;
}

const props = withDefaults(defineProps<Props>(), {
  activeFlag: undefined,
  enumText: '',
});

const isEnabled = computed(() => {
  return props.activeFlag === 1 || props.activeFlag === 2;
});

const displayText = computed(() => {
  return props.enumText || (isEnabled.value ? '是' : '否');
});

const tagType = computed(() => {
  return isEnabled.value ? 'success' : 'danger';
});
</script>

<template>
  <ElTag :type="tagType">
    {{ displayText }}
  </ElTag>
</template>
