<script setup lang="ts">
defineOptions({ name: 'TcmHerbAddCard' });

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
});

const emit = defineEmits<{
  add: [];
}>();

interface Props {
  disabled?: boolean;
}

/** 点击加号：追加空白编辑卡并聚焦选药下拉。 */
function handleClick() {
  if (props.disabled) return;
  emit('add');
}
</script>

<template>
  <div
    class="tcm-herb-add-card"
    :class="{ 'tcm-herb-add-card--disabled': disabled }"
    @click="handleClick"
  >
    <span class="tcm-herb-add-card__icon-wrap">
      <span class="tcm-herb-add-card__icon">+</span>
    </span>
    <span class="tcm-herb-add-card__label">添加药材</span>
  </div>
</template>

<style scoped>
.tcm-herb-add-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  min-height: 108px;
  padding: 12px 14px;
  border: 1px dashed var(--el-border-color);
  border-radius: 12px;
  background: var(--el-fill-color-lighter);
  cursor: pointer;
  box-sizing: border-box;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    box-shadow 0.2s ease;
}

.tcm-herb-add-card:hover:not(.tcm-herb-add-card--disabled) {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
  box-shadow: 0 2px 8px rgb(15 23 42 / 6%);
}

.tcm-herb-add-card--disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.tcm-herb-add-card__icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 999px;
  background: var(--el-fill-color-blank);
  box-shadow: 0 1px 2px rgb(15 23 42 / 6%);
}

.tcm-herb-add-card__icon {
  color: var(--el-color-primary);
  font-size: 20px;
  font-weight: 400;
  line-height: 1;
}

.tcm-herb-add-card__label {
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.2;
}
</style>
