<script setup lang="ts">
import { ElButton } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';

defineOptions({
  name: 'NurseFooterBar',
});

defineProps<Props>();

const emit = defineEmits<{
  clear: [];
  execute: [];
}>();

interface Props {
  selectedExecuteCount: number;
  selectedSupplyCount: number;
  totalPendingCount: number;
  totalSelectedCount: number;
  canExecuteSelected: boolean;
}
</script>

<template>
  <div class="flex w-full min-w-0 flex-wrap items-center justify-between gap-2">
    <div class="min-w-0 flex-1 text-gray-600">
      <SectionTitle title="已选项汇总" />
      <div class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm">
        <span>
          处置 <strong class="text-primary">{{ selectedExecuteCount }}</strong> 项
        </span>
        <span class="text-gray-300">|</span>
        <span>
          耗材 <strong class="text-primary">{{ selectedSupplyCount }}</strong> 项
        </span>
        <span class="text-gray-300">|</span>
        <span class="text-xs text-gray-500">当前待处理共 {{ totalPendingCount }} 项</span>
      </div>
    </div>
    <div class="flex flex-wrap items-center gap-1.5">
      <ElButton :disabled="!canExecuteSelected" @click="emit('clear')">一键清空</ElButton>
      <ElButton
        type="primary"
        size="default"
        :disabled="!canExecuteSelected"
        @click="emit('execute')"
      >
        批量执行（{{ totalSelectedCount }}）
      </ElButton>
    </div>
  </div>
</template>
