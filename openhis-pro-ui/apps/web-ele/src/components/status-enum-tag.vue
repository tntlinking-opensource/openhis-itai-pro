<script setup lang="ts">
import { computed } from 'vue';

import { ElTag } from 'element-plus';

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
  /** 状态枚举值：1-草稿, 2-启用, 3-停用, 4-未知 */
  statusEnum?: number | string;
  /** 状态文本，如果不提供则根据 statusEnum 自动生成 */
  statusText?: string;
  /** 标签大小 */
  size?: 'default' | 'large' | 'small';
  /** 标签效果 */
  effect?: 'dark' | 'light' | 'plain';
}

// 状态映射
const STATUS_MAP = {
  1: { label: '草稿', type: 'info' as const },
  2: { label: '启用', type: 'success' as const },
  3: { label: '停用', type: 'warning' as const },
  4: { label: '未知', type: 'info' as const },
} as const;

// 获取状态配置
const statusConfig = computed(() => {
  const status =
    typeof props.statusEnum === 'string' ? Number.parseInt(props.statusEnum, 10) : props.statusEnum;

  return STATUS_MAP[status as keyof typeof STATUS_MAP] || STATUS_MAP[4];
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

// 标签效果：未知状态使用 plain，其他使用 dark
const tagEffect = computed(() => {
  if (props.statusEnum === 4) {
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
