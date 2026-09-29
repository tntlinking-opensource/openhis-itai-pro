<script setup lang="ts">
import { ElButton } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';

defineOptions({
  name: 'MedicalTechFooterBar',
});

interface Props {
  cancelLoading?: boolean;
  canCancelSelected?: boolean;
  canExecuteSelected?: boolean;
  /** 住院医技执行不展示耗材计数 */
  hideDeviceCount?: boolean;
  performLoading?: boolean;
  selectedDeviceCount?: number;
  selectedServiceCount?: number;
  totalSelectedCount?: number;
}

withDefaults(defineProps<Props>(), {
  cancelLoading: false,
  canCancelSelected: false,
  canExecuteSelected: false,
  hideDeviceCount: false,
  performLoading: false,
  selectedDeviceCount: 0,
  selectedServiceCount: 0,
  totalSelectedCount: 0,
});

const emit = defineEmits<{
  cancel: [];
  clear: [];
  execute: [];
}>();
</script>

<template>
  <div class="flex w-full min-w-0 flex-wrap items-center justify-between gap-2">
    <div class="text-text-sub min-w-0 flex-1">
      <SectionTitle title="已选项汇总" />
      <div class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm">
        <span>
          诊疗
          <strong class="text-primary">{{ selectedServiceCount }}</strong>
          项
        </span>
        <template v-if="!hideDeviceCount">
          <span class="text-[var(--el-border-color)]">|</span>
          <span>
            耗材
            <strong class="text-primary">{{ selectedDeviceCount }}</strong>
            项
          </span>
        </template>
      </div>
    </div>
    <div class="flex flex-wrap items-center gap-1.5">
      <ElButton :disabled="!canExecuteSelected && !canCancelSelected" @click="emit('clear')">
        一键清空
      </ElButton>
      <ElButton
        type="primary"
        size="default"
        :loading="performLoading"
        :disabled="!canExecuteSelected"
        @click="emit('execute')"
      >
        批量执行（{{ totalSelectedCount }}）
      </ElButton>
      <ElButton
        type="danger"
        plain
        :loading="cancelLoading"
        :disabled="!canCancelSelected"
        @click="emit('cancel')"
      >
        取消执行
      </ElButton>
    </div>
  </div>
</template>
