<script setup lang="ts">
import type { WorkbenchShortcutItem } from '../workbench-types';

import { computed, ref } from 'vue';

import { ElButton, ElDialog, ElMessage, ElOption, ElSelect } from 'element-plus';

import WorkbenchAddSlot from './workbench-add-slot.vue';
import WorkbenchShortcutCard from './workbench-shortcut-card.vue';

interface Props {
  addableCandidates: WorkbenchShortcutItem[];
  canAddMore: boolean;
  customItems: WorkbenchShortcutItem[];
  emptySlotCount: number;
}

defineOptions({
  name: 'WorkbenchCustomSection',
});

const props = defineProps<Props>();

const emit = defineEmits<{
  add: [routeName: string];
  navigate: [path: string];
  remove: [routeName: string];
}>();

const addDialogVisible = ref(false);
const selectedRouteName = ref('');

const addDialogOptions = computed(() =>
  props.addableCandidates.map((item) => ({
    label: item.title,
    value: item.routeName,
  })),
);

/** 打开添加快捷入口弹窗 */
function handleOpenAddDialog(): void {
  if (!props.canAddMore) {
    return;
  }
  selectedRouteName.value = '';
  addDialogVisible.value = true;
}

/** 确认将所选菜单加入自定义快捷入口 */
function handleConfirmAdd(): void {
  const routeName = selectedRouteName.value.trim();
  if (!routeName) {
    ElMessage.warning('请选择要添加的菜单');
    return;
  }

  emit('add', routeName);
  addDialogVisible.value = false;
  selectedRouteName.value = '';
}

/** 点击已有快捷入口跳转业务页 */
function handleNavigate(item: WorkbenchShortcutItem): void {
  if (!item.path) {
    return;
  }
  emit('navigate', item.path);
}

/** 从自定义区移除指定快捷入口 */
function handleRemove(item: WorkbenchShortcutItem): void {
  emit('remove', item.routeName);
}
</script>

<template>
  <section class="workbench-custom">
    <header class="workbench-custom__header">
      <span class="workbench-custom__bar" />
      <h3 class="workbench-custom__title">自定义快捷入口</h3>
    </header>

    <div class="workbench-custom__grid">
      <WorkbenchShortcutCard
        v-for="item in customItems"
        :key="item.routeName"
        removable
        :item="item"
        @click="handleNavigate"
        @remove="handleRemove"
      />

      <WorkbenchAddSlot
        v-for="slotIndex in emptySlotCount"
        :key="`add-slot-${slotIndex}`"
        @click="handleOpenAddDialog"
      />
    </div>

    <ElDialog
      v-model="addDialogVisible"
      append-to-body
      destroy-on-close
      title="添加快捷入口"
      width="420px"
    >
      <ElSelect
        v-model="selectedRouteName"
        class="workbench-custom__select"
        filterable
        placeholder="请选择菜单"
      >
        <ElOption
          v-for="option in addDialogOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </ElSelect>

      <template #footer>
        <ElButton @click="addDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="handleConfirmAdd">确定</ElButton>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped>
.workbench-custom__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.workbench-custom__bar {
  flex-shrink: 0;
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: #0066e6;
}

.workbench-custom__title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.workbench-custom__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}

.workbench-custom__select {
  width: 100%;
}
</style>
