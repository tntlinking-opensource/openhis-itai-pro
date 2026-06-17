<script setup lang="ts">
defineOptions({
  name: 'InpatientDispenseModeSwitch',
});

type DispenseMode = 'patient' | 'summary';

const dispenseMode = defineModel<DispenseMode>('dispenseMode', { required: true });

const modeTabs: Array<{ label: string; value: DispenseMode }> = [
  { label: '按患者发药', value: 'patient' },
  { label: '按汇总单发药', value: 'summary' },
];
</script>

<template>
  <div
    class="mode-switch-wrap border-b border-solid border-[var(--el-border-color-lighter)] px-2 pb-2 pt-1.5"
  >
    <ElRadioGroup v-model="dispenseMode" class="dispense-mode-radio w-full">
      <ElRadioButton v-for="tab in modeTabs" :key="tab.value" :label="tab.value" class="flex-1">
        {{ tab.label }}
      </ElRadioButton>
    </ElRadioGroup>
  </div>
</template>

<style scoped lang="scss">
.dispense-mode-radio {
  display: flex;
  width: 100%;
}

.dispense-mode-radio :deep(.el-radio-button) {
  flex: 1;
}

.dispense-mode-radio :deep(.el-radio-button__inner) {
  width: 100%;
  padding-left: 8px;
  padding-right: 8px;
}
</style>
