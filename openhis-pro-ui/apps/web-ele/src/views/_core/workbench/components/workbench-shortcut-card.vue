<script setup lang="ts">
import type { WorkbenchShortcutItem } from '../workbench-types';

import { IconifyIcon } from '@vben/icons';

interface Props {
  item: WorkbenchShortcutItem;
  removable?: boolean;
}

defineOptions({
  name: 'WorkbenchShortcutCard',
});

withDefaults(defineProps<Props>(), {
  removable: false,
});

const emit = defineEmits<{
  click: [item: WorkbenchShortcutItem];
  remove: [item: WorkbenchShortcutItem];
}>();
</script>

<template>
  <div
    role="button"
    tabindex="0"
    class="workbench-shortcut-card"
    :class="{ 'workbench-shortcut-card--removable': removable }"
    :style="{ '--card-tint': item.cardTint, '--card-accent': item.accentColor }"
    @click="emit('click', item)"
    @keydown.enter="emit('click', item)"
  >
    <button
      v-if="removable"
      type="button"
      class="workbench-shortcut-card__remove"
      aria-label="移除快捷入口"
      @click.stop="emit('remove', item)"
    >
      ×
    </button>
    <span
      class="workbench-shortcut-card__icon"
      :style="{
        background: item.iconGradient,
        color: item.iconColor,
      }"
    >
      <IconifyIcon
        :icon="item.icon || 'lucide:layout-grid'"
        class="workbench-shortcut-card__icon-svg"
      />
    </span>
    <span class="workbench-shortcut-card__title">{{ item.title }}</span>
  </div>
</template>

<style scoped>
.workbench-shortcut-card {
  position: relative;
  display: flex;
  min-height: 76px;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  overflow: hidden;
  border: none;
  border-radius: 12px;
  background: linear-gradient(180deg, #fff 0%, color-mix(in srgb, var(--card-tint) 9%, #fff) 100%);
  box-shadow: 0 2px 10px rgb(15 23 42 / 4%);
  cursor: pointer;
  text-align: left;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.workbench-shortcut-card::before {
  position: absolute;
  top: 0;
  left: 0;
  width: 3px;
  height: 100%;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--card-accent) 65%, #fff) 0%,
    color-mix(in srgb, var(--card-tint) 50%, var(--card-accent)) 100%
  );
  content: '';
  pointer-events: none;
}

.workbench-shortcut-card::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 55%;
  background: linear-gradient(
    180deg,
    transparent 0%,
    color-mix(in srgb, var(--card-tint) 22%, transparent) 55%,
    color-mix(in srgb, var(--card-accent) 5%, transparent) 100%
  );
  content: '';
  pointer-events: none;
}

.workbench-shortcut-card:hover {
  box-shadow: 0 6px 20px rgb(15 23 42 / 8%);
  transform: translateY(-2px);
}

.workbench-shortcut-card:active {
  transform: translateY(0);
}

.workbench-shortcut-card--removable:hover .workbench-shortcut-card__remove {
  opacity: 1;
}

.workbench-shortcut-card__remove {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  display: flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: rgb(15 23 42 / 8%);
  font-size: 16px;
  line-height: 1;
  color: var(--el-text-color-regular);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.workbench-shortcut-card__remove:hover {
  background: rgb(15 23 42 / 12%);
  color: var(--el-text-color-primary);
}

.workbench-shortcut-card__icon {
  position: relative;
  z-index: 1;
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
}

.workbench-shortcut-card__icon-svg {
  width: 22px;
  height: 22px;
  color: currentColor;
}

.workbench-shortcut-card__title {
  position: relative;
  z-index: 1;
  overflow: hidden;
  min-width: 0;
  flex: 1;
  font-size: 16px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
