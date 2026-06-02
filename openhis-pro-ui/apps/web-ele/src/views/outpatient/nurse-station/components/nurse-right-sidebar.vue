<script setup lang="ts">
import type { ActionRecord, SupplyItem, SupplyRoomOption } from './types';

import { ElButton, ElEmpty, ElOption, ElSelect, ElTag } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import SupplyItemCard from '#/components/outpatient/supply-item-card.vue';

defineOptions({
  name: 'NurseRightSidebar',
});

defineProps<Props>();

const emit = defineEmits<{
  print: [label: string];
  showAllRecords: [];
  supplyChange: [id: string, checked: boolean];
}>();

interface Props {
  supplyItems: SupplyItem[];
  selectedSupplyIds: string[];
  supplyRoomOptions: SupplyRoomOption[];
  recentActionRecords: ActionRecord[];
}

const selectedSupplyRoomId = defineModel<string | undefined>('supplyRoomId');

const onSupplyChange = (id: string, checked: boolean) => {
  emit('supplyChange', id, checked);
};

const onPrint = (label: string) => {
  emit('print', label);
};
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <div class="bg-background px-2 py-1.5">
      <SectionTitle title="耗材明细" />
      <p class="mt-0.5 text-[11px] leading-snug text-gray-500">
        勾选后随「执行并记录」一并发放；已发放不可再选。
      </p>
      <div class="mt-1.5 flex min-w-0 items-center gap-2">
        <span class="shrink-0 text-sm text-gray-600">耗材房（可选）</span>
        <div class="min-w-0 max-w-[11rem] flex-1 sm:max-w-[13rem]">
          <ElSelect
            v-model="selectedSupplyRoomId"
            class="supply-room-select w-full"
            placeholder="不指定则按默认库位出库"
            clearable
            filterable
          >
            <ElOption
              v-for="opt in supplyRoomOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </div>
      </div>
    </div>
    <div class="supply-list-wrap bg-background h-[240px] shrink-0 overflow-hidden px-2 py-1.5">
      <div v-if="supplyItems.length === 0" class="flex h-full min-h-0 items-center justify-center">
        <ElEmpty description="暂无耗材明细" :image-size="72" />
      </div>
      <div
        v-else
        class="flex h-full min-h-0 flex-col gap-2 overflow-y-auto overflow-x-hidden pr-0.5"
      >
        <SupplyItemCard
          v-for="item in supplyItems"
          :key="item.id"
          :item="item"
          selectable
          :selected="selectedSupplyIds.includes(item.id)"
          @change="onSupplyChange"
        />
      </div>
    </div>

    <div class="bg-background px-2 py-1.5">
      <SectionTitle title="打印" />
    </div>
    <div class="print-action-grid bg-background min-w-0 px-2 py-1.5">
      <ElButton type="primary" size="small" @click="onPrint('打印瓶贴')">打印瓶贴</ElButton>
      <ElButton size="small" @click="onPrint('打印处置单')">打印处置单</ElButton>
      <ElButton size="small" @click="onPrint('打印采血码')">打印采血码</ElButton>
      <ElButton size="small" @click="onPrint('打印处方单')">打印处方单</ElButton>
    </div>

    <div class="bg-background flex min-h-0 flex-1 flex-col px-2 py-1.5">
      <div class="mb-1.5 flex shrink-0 items-center justify-between gap-2">
        <SectionTitle title="最近执行记录" class="min-w-0 flex-1" />
        <ElButton link type="primary" size="small" @click="emit('showAllRecords')">
          查看全部
        </ElButton>
      </div>
      <div class="record-mini min-h-0 flex-1 space-y-1 overflow-y-auto pr-0.5">
        <div
          v-for="record in recentActionRecords"
          :key="record.id"
          class="rounded-md bg-[var(--el-fill-color-light)] px-2 py-1.5 text-[11px] text-gray-700"
        >
          <div class="flex items-center justify-between gap-1 text-gray-500">
            <span class="truncate">{{ record.time }}</span>
            <ElTag size="small" :type="record.type === '执行' ? 'warning' : 'info'">
              {{ record.type }}
            </ElTag>
          </div>
          <div class="mt-0.5 truncate font-medium">{{ record.action }}</div>
          <div class="truncate text-gray-500">{{ record.target }}</div>
        </div>
        <div v-if="recentActionRecords.length === 0" class="py-6 text-center text-xs text-gray-400">
          暂无记录
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.print-action-grid {
  display: grid;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(2, auto);
  align-items: stretch;
  gap: 0.375rem;
}

.print-action-grid :deep(.el-button) {
  box-sizing: border-box;
  width: 100%;
  min-width: 0 !important;
  max-width: 100%;
  margin: 0 !important;
  height: 100%;
  min-height: 2rem;
  justify-content: center;
  padding: 4px 6px;
  font-size: 12px;
  line-height: 1.25;
  white-space: normal;
  overflow-wrap: break-word;
}
</style>
