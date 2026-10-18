<script setup lang="ts">
import { computed } from 'vue';

import { ElTag } from 'element-plus';

import { PublicationStatusCode } from '#/constants/domain/publication-status';

defineOptions({
  name: 'StatusEnumTag',
});

const props = withDefaults(defineProps<Props>(), {
  statusEnum: undefined,
  statusText: undefined,
  size: 'small',
  effect: 'dark',
});

interface Props {
  /** 发布状态：ACTIVE/2 启用，INACTIVE/3 停用，DRAFT/1 草稿，DISCARDED 作废 */
  statusEnum?: number | string;
  /** 状态文本，如果不提供则根据 statusEnum 自动生成 */
  statusText?: string;
  /** 标签大小 */
  size?: 'default' | 'large' | 'small';
  /** 标签效果 */
  effect?: 'dark' | 'light' | 'plain';
}

/** 与药品目录一致：启用绿、停用橙、草稿灰、作废红 */
const statusConfig = computed(() => {
  const raw = props.statusEnum;
  if (raw === PublicationStatusCode.ACTIVE || raw === 2 || raw === '2') {
    return { label: '启用', type: 'success' as const };
  }
  if (raw === PublicationStatusCode.INACTIVE || raw === 3 || raw === '3') {
    return { label: '停用', type: 'warning' as const };
  }
  if (raw === PublicationStatusCode.DISCARDED) {
    return { label: '作废', type: 'danger' as const };
  }
  if (raw === PublicationStatusCode.DRAFT || raw === 1 || raw === '1') {
    return { label: '草稿', type: 'info' as const };
  }
  return { label: '未知', type: 'info' as const };
});

// 显示文本
const displayText = computed(() => {
  if (props.statusText) {
    return props.statusText;
  }
  return statusConfig.value.label;
});

// 标签类型
const tagType = computed(() => {
  return statusConfig.value.type;
});

/** 作废用描边，启用和停用用实心 */
const tagEffect = computed(() => {
  const raw = props.statusEnum;
  if (raw === PublicationStatusCode.DISCARDED || raw === 4 || raw === '4') {
    return 'plain';
  }
  return props.effect;
});
</script>

<template>
  <ElTag :type="tagType" :size="size" :effect="tagEffect">
    {{ displayText }}
  </ElTag>
</template>
