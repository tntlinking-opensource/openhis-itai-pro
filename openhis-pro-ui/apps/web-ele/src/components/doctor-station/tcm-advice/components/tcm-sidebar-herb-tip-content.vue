<script setup lang="ts">
import type { TcmHerbTooltipItem } from '#/components/doctor-station/tcm-advice/services/tcm-prescription.service';

defineOptions({ name: 'TcmSidebarHerbTipContent' });

interface Props {
  /** 空态文案（加载中 / 暂无药品） */
  emptyText?: string;
  /** 结构化药材行 */
  items?: TcmHerbTooltipItem[];
  /** 标题：历史方药材 / 组套药品 */
  title?: string;
}

const props = withDefaults(defineProps<Props>(), {
  emptyText: '暂无药品',
  items: () => [],
  title: '药材明细',
});
</script>

<template>
  <div class="tcm-herb-tip">
    <div class="tcm-herb-tip__head">
      <span class="tcm-herb-tip__title">{{ props.title }}</span>
      <span v-if="props.items.length > 0" class="tcm-herb-tip__count">
        {{ props.items.length }}味
      </span>
    </div>
    <ul v-if="props.items.length > 0" class="tcm-herb-tip__list">
      <li
        v-for="(herb, herbIndex) in props.items"
        :key="`${herb.name}-${herbIndex}`"
        class="tcm-herb-tip__row"
      >
        <span class="tcm-herb-tip__name">{{ herb.name }}</span>
        <span v-if="herb.doseText" class="tcm-herb-tip__dose">{{ herb.doseText }}</span>
        <span v-else class="tcm-herb-tip__dose tcm-herb-tip__dose--empty">—</span>
      </li>
    </ul>
    <div v-else class="tcm-herb-tip__empty">{{ props.emptyText }}</div>
  </div>
</template>
