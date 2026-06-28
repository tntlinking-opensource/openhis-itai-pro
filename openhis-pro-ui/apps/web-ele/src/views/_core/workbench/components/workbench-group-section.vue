<script setup lang="ts">
import type { WorkbenchGroupView } from '../workbench-types';

import WorkbenchShortcutCard from './workbench-shortcut-card.vue';

interface Props {
  group: WorkbenchGroupView;
}

defineOptions({
  name: 'WorkbenchGroupSection',
});

defineProps<Props>();

const emit = defineEmits<{
  navigate: [path: string];
}>();
</script>

<template>
  <section class="workbench-group">
    <header class="workbench-group__header">
      <span class="workbench-group__bar" />
      <h3 class="workbench-group__title">{{ group.config.title }}</h3>
    </header>

    <div class="workbench-group__grid">
      <WorkbenchShortcutCard
        v-for="item in group.items"
        :key="item.routeName"
        :item="item"
        @click="emit('navigate', item.path)"
      />
    </div>
  </section>
</template>

<style scoped>
.workbench-group__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.workbench-group__bar {
  flex-shrink: 0;
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: #0066e6;
}

.workbench-group__title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.workbench-group__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}
</style>
