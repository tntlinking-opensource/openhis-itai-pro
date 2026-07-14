<script setup lang="ts">
import { ElButton } from 'element-plus';

defineOptions({ name: 'InpatientAdviceQuickActionBar' });

export type InpatientAdviceQuickAction =
  | 'allergy'
  | 'exam'
  | 'lab'
  | 'pathology'
  | 'report'
  | 'surgery'
  | 'transfusion';

const QUICK_ACTION_ITEMS: Array<{ action: InpatientAdviceQuickAction; label: string }> = [
  { action: 'exam', label: '检查申请' },
  { action: 'lab', label: '检验申请' },
  { action: 'transfusion', label: '输血申请' },
  { action: 'pathology', label: '病理申请' },
  { action: 'surgery', label: '手术申请' },
  { action: 'report', label: '报告查询' },
  { action: 'allergy', label: '过敏史' },
];

const props = withDefaults(
  defineProps<{
    disabled?: boolean;
  }>(),
  { disabled: false },
);

const emit = defineEmits<{
  action: [action: InpatientAdviceQuickAction];
}>();

/** 底部快捷入口点击 */
function handleClick(action: InpatientAdviceQuickAction) {
  emit('action', action);
}

/** 全局禁写时仍允许查看类入口（报告查询、过敏史） */
function isQuickActionDisabled(action: InpatientAdviceQuickAction): boolean {
  if (!props.disabled) return false;
  return action !== 'allergy' && action !== 'report';
}
</script>

<template>
  <footer class="ip-advice-quick-bar">
    <div class="ip-advice-quick-bar__actions">
      <ElButton
        v-for="item in QUICK_ACTION_ITEMS"
        :key="item.action"
        plain
        class="ip-advice-quick-bar__btn"
        :disabled="isQuickActionDisabled(item.action)"
        @click="handleClick(item.action)"
      >
        {{ item.label }}
      </ElButton>
    </div>
  </footer>
</template>

<style scoped>
.ip-advice-quick-bar {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  border-top: 1px solid var(--el-border-color-lighter);
  background: linear-gradient(
    180deg,
    var(--el-fill-color-blank) 0%,
    var(--el-fill-color-light) 100%
  );
  padding: 6px 12px 8px;
}

.ip-advice-quick-bar__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 6px 10px;
  width: 100%;
  max-width: 960px;
}

.ip-advice-quick-bar__actions :deep(.ip-advice-quick-bar__btn) {
  margin: 0;
  min-width: 108px;
  height: 34px;
  padding: 0 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  border-color: var(--el-border-color);
  background-color: var(--el-fill-color-blank);
  color: var(--el-text-color-primary);
  box-shadow: 0 1px 2px rgb(15 23 42 / 6%);
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

.ip-advice-quick-bar__actions :deep(.ip-advice-quick-bar__btn:hover:not(.is-disabled)) {
  border-color: var(--el-color-primary-light-5);
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  box-shadow: 0 2px 6px rgb(64 158 255 / 12%);
}

.ip-advice-quick-bar__actions :deep(.ip-advice-quick-bar__btn:active:not(.is-disabled)) {
  background-color: var(--el-color-primary-light-8);
}

.ip-advice-quick-bar__actions :deep(.ip-advice-quick-bar__btn.is-disabled) {
  box-shadow: none;
}
</style>
