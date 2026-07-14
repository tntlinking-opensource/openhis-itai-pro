<script setup lang="ts">
import type { RegisterSlotSelectionPayload } from '../services';

import { computed } from 'vue';

defineOptions({
  name: 'RegisterAsideSummary',
});

const props = withDefaults(
  defineProps<{
    departmentLabel?: string;
    doctorLabel?: string;
    selectedSlot?: null | RegisterSlotSelectionPayload;
    serviceTypeLabel?: string;
    /** footer：底栏内联摘要；card：独立卡片（旧） */
    variant?: 'card' | 'footer';
  }>(),
  {
    departmentLabel: undefined,
    doctorLabel: undefined,
    selectedSlot: null,
    serviceTypeLabel: undefined,
    variant: 'footer',
  },
);

const hasSelectedSlot = computed(() => Boolean(props.selectedSlot?.slotId));

const slotTimeRange = computed(() => {
  const start = props.selectedSlot?.slotStart;
  const end = props.selectedSlot?.slotEnd;
  const formatTime = (value?: string) => {
    if (!value) {
      return '';
    }
    return String(value).replace('T', ' ').slice(11, 16);
  };
  const from = formatTime(start);
  const to = formatTime(end);
  if (!from && !to) {
    return '—';
  }
  return `${from || '—'}-${to || '—'}`;
});

const selectedSummaryText = computed(() => {
  return [props.departmentLabel, props.doctorLabel, props.serviceTypeLabel, slotTimeRange.value]
    .filter((item) => item && item !== '—')
    .join(' · ');
});
</script>

<template>
  <div
    class="register-aside-summary"
    :class="{
      'register-aside-summary--footer': variant === 'footer',
      'register-aside-summary--card': variant === 'card',
    }"
  >
    <template v-if="variant === 'footer'">
      <span class="register-aside-summary__footer-label">已选</span>
      <span
        class="register-aside-summary__footer-text"
        :class="{ 'is-empty': !hasSelectedSlot || !selectedSummaryText }"
      >
        {{ hasSelectedSlot && selectedSummaryText ? selectedSummaryText : '未选号源' }}
      </span>
    </template>

    <div v-else-if="hasSelectedSlot" class="register-aside-summary__selected">
      <span class="register-aside-summary__selected-label">已选号源</span>
      <div class="register-aside-summary__selected-items">
        {{ selectedSummaryText }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-aside-summary {
  min-width: 0;
}

.register-aside-summary--footer {
  display: flex;
  flex: 1 1 auto;
  gap: 4px;
  align-items: center;
  min-width: 0;
}

.register-aside-summary__footer-label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.register-aside-summary__footer-text {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.register-aside-summary__footer-text.is-empty {
  color: var(--el-text-color-placeholder);
}

.register-aside-summary__selected {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  padding: 8px 10px;
  background: #fff;
  border: 1px solid #dce3eb;
  border-radius: 6px;
}

.register-aside-summary__selected-label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  color: var(--el-color-primary);
}

.register-aside-summary__selected-items {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 13px;
  line-height: 1.4;
  color: var(--el-text-color-primary);
}
</style>
