<script setup lang="ts">
import { IconifyIcon } from '@vben/icons';

interface Props {
  accentColor: string;
  cardTint: string;
  hint?: string;
  icon: string;
  iconColor: string;
  iconGradient: string;
  title: string;
  value?: number | string;
  valueColor?: string;
}

defineOptions({ name: 'WorkbenchStatCard' });

withDefaults(defineProps<Props>(), {
  hint: '',
  value: undefined,
  valueColor: '',
});

const emit = defineEmits<{
  click: [];
}>();
</script>

<template>
  <div
    role="button"
    tabindex="0"
    class="workbench-stat-card"
    :style="{
      '--card-tint': cardTint,
      '--card-accent': accentColor,
      '--value-color': valueColor || accentColor,
    }"
    @click="emit('click')"
    @keydown.enter="emit('click')"
  >
    <span class="workbench-stat-card__icon" :style="{ background: iconGradient, color: iconColor }">
      <IconifyIcon :icon="icon" class="workbench-stat-card__icon-svg" />
    </span>
    <div class="workbench-stat-card__body">
      <div class="workbench-stat-card__title">{{ title }}</div>
      <div v-if="hint" class="workbench-stat-card__hint">{{ hint }}</div>
    </div>
    <div v-if="value !== undefined && value !== ''" class="workbench-stat-card__value">
      {{ value }}
    </div>
    <span v-else class="workbench-stat-card__chevron" aria-hidden="true">›</span>
  </div>
</template>

<style scoped>
.workbench-stat-card {
  position: relative;
  display: flex;
  min-height: 64px;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  overflow: hidden;
  border-radius: 10px;
  background: linear-gradient(180deg, #fff 0%, color-mix(in srgb, var(--card-tint) 8%, #fff) 100%);
  box-shadow: 0 1px 6px rgb(15 23 42 / 5%);
  cursor: pointer;
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease;
}

.workbench-stat-card::before {
  position: absolute;
  top: 0;
  left: 0;
  width: 3px;
  height: 100%;
  background: var(--card-accent);
  content: '';
  pointer-events: none;
}

.workbench-stat-card:hover {
  box-shadow: 0 4px 14px rgb(15 23 42 / 8%);
  transform: translateY(-1px);
}

.workbench-stat-card__icon {
  position: relative;
  z-index: 1;
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
}

.workbench-stat-card__icon-svg {
  width: 18px;
  height: 18px;
  color: currentColor;
}

.workbench-stat-card__body {
  position: relative;
  z-index: 1;
  min-width: 0;
  flex: 1;
}

.workbench-stat-card__title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--el-text-color-primary);
}

.workbench-stat-card__hint {
  margin-top: 2px;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.3;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbench-stat-card__value {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  font-size: 20px;
  font-weight: 700;
  line-height: 1;
  color: var(--value-color);
}

.workbench-stat-card__chevron {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  font-size: 18px;
  font-weight: 400;
  line-height: 1;
  color: var(--el-text-color-placeholder);
}
</style>
