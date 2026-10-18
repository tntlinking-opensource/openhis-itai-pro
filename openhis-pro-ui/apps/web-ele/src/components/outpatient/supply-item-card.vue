<script setup lang="ts">
/**
 * 门诊护士站 / 医技站耗材明细卡片：勾选待发放 / 取消已执行；
 * 展示编号、规格、数量、批号、单价与总价。
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
  /** 目录编号 / 业务号；空则不展示编号行 */
  itemCode?: string;
  /** 来源诊疗名（待计费预览）；空则不展示 */
  sourceLabel?: string;
  /** 签发批号；空则不展示 */
  lotNumber?: string;
  /** 已格式化单价文案；空则不展示 */
  unitPriceText?: string;
  /** 已格式化总价文案；空则不展示 */
  totalPriceText?: string;
  status:
    | '已发放'
    | '已配药'
    | '待发放'
    | '待退费'
    | '已退费'
    | '开立带出'
    | '待计费'
    | '已计费';
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
  // 待计费：蓝色（含历史「开立带出」文案）
  if (props.item.status === '待计费' || props.item.status === '开立带出') {
    return 'primary';
  }
  const code = props.item.statusCode;
  if (code === 'REFUNDED') return 'danger';
  if (code === 'REFUNDING') return 'warning';
  if (code === 'COMPLETED' || code === 'PART_COMPLETED') return 'success';
  if (code === 'PREPARED') return 'warning';
  return 'primary';
});

/** 编号文案 */
const itemCodeText = computed(() => String(props.item.itemCode ?? '').trim());

/** 规格文案 */
const specText = computed(() => {
  const raw = String(props.item.spec ?? '').trim();
  return raw && raw !== '—' ? raw : '';
});

/** 单位·数量行 */
const qtyLine = computed(() => {
  const unit = String(props.item.usage ?? '').trim() || '—';
  return `单位 ${unit} · 数量 ×${props.item.quantity}`;
});

/** 来源诊疗 */
const sourceText = computed(() => String(props.item.sourceLabel ?? '').trim());

/** 批号 */
const lotText = computed(() => String(props.item.lotNumber ?? '').trim());

/** 单价 */
const unitPriceText = computed(() => String(props.item.unitPriceText ?? '').trim());

/** 总价 */
const totalPriceText = computed(() => String(props.item.totalPriceText ?? '').trim());

/** 价格行：单价 / 总价 */
const priceLine = computed(() => {
  const parts: string[] = [];
  if (unitPriceText.value) {
    parts.push(`单价 ${unitPriceText.value}`);
  }
  if (totalPriceText.value) {
    parts.push(`总价 ${totalPriceText.value}`);
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
    <div class="supply-item-card__body min-w-0 flex-1">
      <div class="supply-item-card__name">
        {{ item.name }}
      </div>
      <div v-if="itemCodeText" class="supply-item-card__meta">
        编号 {{ itemCodeText }}
      </div>
      <div v-if="specText" class="supply-item-card__meta">
        规格 {{ specText }}
      </div>
      <div class="supply-item-card__meta">
        {{ qtyLine }}
      </div>
      <div v-if="sourceText" class="supply-item-card__meta">
        来自 {{ sourceText }}
      </div>
      <div v-if="lotText" class="supply-item-card__meta">
        批号 {{ lotText }}
      </div>
      <div v-if="priceLine" class="supply-item-card__price">
        {{ priceLine }}
      </div>
    </div>
    <div class="flex shrink-0 flex-col items-end gap-1 self-start pt-0.5">
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
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.875rem 1rem;
  border: 1px solid var(--el-border-color);
  border-radius: 0.5rem;
  font-size: 0.875rem;
  transition:
    border-color 0.2s,
    background-color 0.2s;
  box-sizing: border-box;
}

.supply-item-card__body {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-height: 3.25rem;
  justify-content: center;
}

.supply-item-card__name {
  font-size: 0.9375rem;
  font-weight: 600;
  line-height: 1.35;
  color: var(--el-text-color-primary);
  word-break: break-word;
}

.supply-item-card__meta {
  font-size: 0.8125rem;
  line-height: 1.4;
  color: var(--el-text-color-regular);
  word-break: break-word;
}

.supply-item-card__price {
  margin-top: 0.15rem;
  font-size: 0.8125rem;
  font-weight: 600;
  line-height: 1.4;
  color: var(--el-text-color-primary);
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

.supply-item-card__checkbox {
  margin-top: 0.15rem;
}

.supply-item-card__checkbox :deep(.el-checkbox__input) {
  transform: scale(1.15);
}

.supply-item-card__checkbox :deep(.el-checkbox__inner) {
  width: 18px;
  height: 18px;
}
</style>
