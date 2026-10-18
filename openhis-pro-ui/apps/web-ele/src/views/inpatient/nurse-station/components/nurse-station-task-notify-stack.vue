<script setup lang="ts">
import type { NurseStationTaskNotifyCard } from '../nurse-station-task-notify.types';

import { Close } from '@element-plus/icons-vue';

defineOptions({
  name: 'NurseStationTaskNotifyStack',
});

defineProps<{
  items: NurseStationTaskNotifyCard[];
}>();

const emit = defineEmits<{
  open: [item: NurseStationTaskNotifyCard];
  dismiss: [id: string];
}>();
</script>

<template>
  <!-- 容器不拦截点击，仅卡片可点，避免挡住右侧操作 -->
  <div class="nurse-task-notify" aria-live="polite">
    <button
      v-for="item in items"
      :key="item.id"
      type="button"
      class="nurse-task-notify__card"
      @click="emit('open', item)"
    >
      <div class="nurse-task-notify__body">
        <div class="nurse-task-notify__title">医嘱提醒</div>
        <div class="nurse-task-notify__msg">{{ item.message }}</div>
        <div class="nurse-task-notify__action">点击查看</div>
      </div>
      <span
        class="nurse-task-notify__close"
        title="关闭"
        @click.stop="emit('dismiss', item.id)"
      >
        <ElIcon :size="14"><Close /></ElIcon>
      </span>
    </button>
  </div>
</template>

<style scoped>
.nurse-task-notify {
  bottom: 24px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: min(360px, calc(100% - 32px));
  pointer-events: none;
  position: absolute;
  right: 20px;
  z-index: 40;
}

.nurse-task-notify__card {
  align-items: flex-start;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  box-shadow: 0 8px 24px rgb(15 23 42 / 14%);
  cursor: pointer;
  display: flex;
  gap: 8px;
  padding: 12px 12px 12px 14px;
  pointer-events: auto;
  text-align: left;
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease;
  width: 100%;
}

.nurse-task-notify__card:hover {
  box-shadow: 0 10px 28px rgb(15 23 42 / 18%);
  transform: translateY(-1px);
}

.nurse-task-notify__body {
  flex: 1;
  min-width: 0;
}

.nurse-task-notify__title {
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  margin-bottom: 4px;
}

.nurse-task-notify__msg {
  color: #303133;
  font-size: 14px;
  line-height: 1.45;
  word-break: break-all;
}

.nurse-task-notify__action {
  color: var(--el-color-primary);
  font-size: 12px;
  margin-top: 6px;
}

.nurse-task-notify__close {
  align-items: center;
  color: #909399;
  display: inline-flex;
  flex: 0 0 auto;
  height: 22px;
  justify-content: center;
  width: 22px;
}

.nurse-task-notify__close:hover {
  color: #606266;
}
</style>
