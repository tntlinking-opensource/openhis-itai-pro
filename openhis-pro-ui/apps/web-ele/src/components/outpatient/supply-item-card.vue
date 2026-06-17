<script setup lang="ts">
import { computed } from 'vue';

import { ElButton, ElCheckbox, ElTag } from 'element-plus';

export interface SupplyItem {
  id: string;
  name: string;
  spec: string;
  usage: string;
  quantity: number;
  status: '待发放' | '已配药' | '已发放';
  canCancelExecute?: boolean;
}

interface Props {
  item: SupplyItem;
  /** 可选模式：显示 checkbox、响应点击切换选中；false 时纯展示，不可交互 */
  selectable?: boolean;
  /** 当前是否已选中（仅 selectable 模式有效） */
  selected?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  selectable: false,
  selected: false,
});

const emit = defineEmits<{
  (e: 'change', id: string, checked: boolean): void;
  (e: 'cancel', id: string): void;
}>();

/** 已执行态不可再勾选随批执行 */
const isExecuted = computed(() => props.item.status === '已配药' || props.item.status === '已发放');

const cardClass = computed(() => {
  if (isExecuted.value) {
    return 'supply-item-card--dispatched';
  }
  if (props.selectable && props.selected) {
    return 'supply-item-card--selected';
  }
  return props.selectable ? 'supply-item-card--idle' : 'supply-item-card--display';
});

const statusTagType = computed(() => {
  const s = props.item.status;
  if (s === '已发放') return 'success';
  if (s === '已配药') return 'warning';
  return 'primary';
});

const handleCardClick = () => {
  if (!props.selectable || isExecuted.value) return;
  emit('change', props.item.id, !props.selected);
};

const handleCheckboxChange = (val: boolean | number | string) => {
  emit('change', props.item.id, Boolean(val));
};

const handleCancelClick = () => {
  emit('cancel', props.item.id);
};
</script>

<template>
  <div class="supply-item-card w-full" :class="cardClass" @click="handleCardClick">
    <ElCheckbox
      v-if="selectable"
      class="supply-item-card__checkbox shrink-0"
      :model-value="selected"
      :disabled="isExecuted"
      @click.stop
      @change="handleCheckboxChange"
    />
    <div class="flex min-h-[2.75rem] min-w-0 flex-1 flex-col justify-center gap-1">
      <div class="truncate text-sm font-medium text-gray-900">{{ item.name }}</div>
      <div class="truncate text-xs leading-snug text-gray-600">
        {{ item.spec }} · {{ item.usage }} · ×{{ item.quantity }}
      </div>
    </div>
    <div class="flex shrink-0 flex-col items-end gap-1 self-center">
      <ElTag size="small" :type="statusTagType">
        {{ item.status }}
      </ElTag>
      <ElButton
        v-if="item.canCancelExecute"
        type="danger"
        link
        size="small"
        @click.stop="handleCancelClick"
      >
        取消执行
      </ElButton>
    </div>
  </div>
</template>

<style scoped>
.supply-item-card {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  padding: 0.625rem 0.75rem;
  border: 1px solid var(--el-border-color);
  border-radius: 0.5rem;
  font-size: 0.875rem;
  transition:
    border-color 0.2s,
    background-color 0.2s;
  box-sizing: border-box;
}

/* 纯展示：无悬停交互 */
.supply-item-card--display {
  cursor: default;
  background-color: var(--el-bg-color);
}

/* 可选且未选中：可悬停 */
.supply-item-card--idle {
  cursor: pointer;
  background-color: var(--el-bg-color);
}

.supply-item-card--idle:hover {
  border-color: color-mix(in srgb, var(--el-color-primary) 40%, transparent);
  background-color: var(--el-fill-color-blank);
}

/* 可选且已选中 */
.supply-item-card--selected {
  cursor: pointer;
  border-color: var(--el-color-primary);
  background-color: color-mix(in srgb, var(--el-color-primary) 8%, transparent);
}

/* 已配药/已发放：置灰不可勾选 */
.supply-item-card--dispatched {
  cursor: default;
  border-color: var(--el-border-color-lighter);
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
}

.supply-item-card__checkbox :deep(.el-checkbox__input) {
  transform: scale(1.15);
}

.supply-item-card__checkbox :deep(.el-checkbox__inner) {
  width: 18px;
  height: 18px;
}
</style>
