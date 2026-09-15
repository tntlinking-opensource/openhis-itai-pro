<script setup lang="ts">
/**
 * 门诊护士站耗材明细卡片：勾选待发放 / 取消已执行，展示规格、批号与单价。
 */
import type { SupplyDispenseStatusCode } from '#/utils/nurse-station/nurse-execute-status';

import { computed } from 'vue';

import { ElButton, ElCheckbox, ElTag } from 'element-plus';

defineOptions({
  name: 'SupplyItemCard',
});

export interface SupplyItem {
  id: string;
  name: string;
  spec: string;
  usage: string;
  quantity: number;
  /** 签发批号；空则不展示 */
  lotNumber?: string;
  /** 已格式化单价文案；空则不展示 */
  unitPriceText?: string;
  status: '已发放' | '已配药' | '待发放' | '待退费' | '已退费';
  statusCode: SupplyDispenseStatusCode;
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

/** 已执行 / 退费态不可再勾选随批执行 */
const isNonSelectable = computed(() => {
  const code = props.item.statusCode;
  return (
    code === 'PREPARED' ||
    code === 'COMPLETED' ||
    code === 'PART_COMPLETED' ||
    code === 'REFUNDING' ||
    code === 'REFUNDED'
  );
});

const cardClass = computed(() => {
  if (isNonSelectable.value) {
    return 'supply-item-card--dispatched';
  }
  if (props.selectable && props.selected) {
    return 'supply-item-card--selected';
  }
  return props.selectable ? 'supply-item-card--idle' : 'supply-item-card--display';
});

const statusTagType = computed(() => {
  const code = props.item.statusCode;
  if (code === 'REFUNDED') return 'danger';
  if (code === 'REFUNDING') return 'warning';
  if (code === 'COMPLETED' || code === 'PART_COMPLETED') return 'success';
  if (code === 'PREPARED') return 'warning';
  return 'primary';
});

/** 规格·类目·数量摘要行 */
const metaLine = computed(() => {
  return `${props.item.spec} · ${props.item.usage} · ×${props.item.quantity}`;
});

/** 批号与单价次行；均无则整行隐藏 */
const detailLine = computed(() => {
  const parts: string[] = [];
  const lot = String(props.item.lotNumber ?? '').trim();
  if (lot) {
    parts.push(`批号 ${lot}`);
  }
  const price = String(props.item.unitPriceText ?? '').trim();
  if (price) {
    parts.push(`单价 ${price}`);
  }
  return parts.join(' · ');
});

/** 点击卡片切换勾选（已执行/退费忽略） */
const handleCardClick = () => {
  if (!props.selectable || isNonSelectable.value) return;
  emit('change', props.item.id, !props.selected);
};

/** checkbox 变更同步选中态 */
const handleCheckboxChange = (val: boolean | number | string) => {
  emit('change', props.item.id, Boolean(val));
};

/** 取消执行（仅已配药/已发放） */
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
      :disabled="isNonSelectable"
      @click.stop
      @change="handleCheckboxChange"
    />
    <div class="flex min-h-[2.75rem] min-w-0 flex-1 flex-col justify-center gap-0.5">
      <div class="truncate text-sm font-medium text-[var(--el-text-color-primary)]">
        {{ item.name }}
      </div>
      <div class="truncate text-xs leading-snug text-[var(--el-text-color-regular)]">
        {{ metaLine }}
      </div>
      <div
        v-if="detailLine"
        class="truncate text-xs leading-snug text-[var(--el-text-color-regular)]"
      >
        {{ detailLine }}
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
