<script setup lang="ts">
defineOptions({
  name: 'WorkbenchAddSlot',
});

const emit = defineEmits<{
  click: [];
}>();
</script>

<template>
  <button type="button" class="workbench-add-slot" @click="emit('click')">
    <span class="workbench-add-slot__icon" aria-hidden="true">+</span>
    <span class="workbench-add-slot__label">添加快捷入口</span>
  </button>
</template>

<style scoped>
.workbench-add-slot {
  display: flex;
  min-height: 76px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 18px;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  background: rgb(255 255 255 / 72%);
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    transform 0.2s ease;
}

.workbench-add-slot:hover {
  border-color: #94a3b8;
  background: #fff;
  transform: translateY(-1px);
}

.workbench-add-slot__icon {
  display: flex;
  width: 44px;
  height: 44px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: #f8fafc;
  font-size: 28px;
  font-weight: 300;
  line-height: 1;
  color: #64748b;
}

.workbench-add-slot__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
