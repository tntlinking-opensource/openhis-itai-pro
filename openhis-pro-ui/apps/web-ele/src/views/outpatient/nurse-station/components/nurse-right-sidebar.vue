<script setup lang="ts">
import type { ActionRecord, SupplyItem, SupplyRoomOption } from './types';

import { ElButton, ElEmpty, ElOption, ElSelect, ElTag } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import SupplyItemCard from '#/components/outpatient/supply-item-card.vue';

defineOptions({
  name: 'NurseRightSidebar',
});

const props = withDefaults(defineProps<Props>(), {
  showSupplySection: true,
});

const emit = defineEmits<{
  cancelSupply: [id: string];
  showAllRecords: [];
  supplyChange: [id: string, checked: boolean];
}>();

interface Props {
  supplyItems: SupplyItem[];
  selectedSupplyIds: string[];
  supplyRoomOptions: SupplyRoomOption[];
  recentActionRecords: ActionRecord[];
  /** 皮试 Tab 下隐藏耗材区 */
  showSupplySection?: boolean;
}

const selectedSupplyRoomId = defineModel<string | undefined>('supplyRoomId');

const onSupplyChange = (id: string, checked: boolean) => {
  emit('supplyChange', id, checked);
};
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <template v-if="props.showSupplySection">
      <div class="bg-background px-2 py-1.5">
        <SectionTitle title="耗材明细" />
        <p class="mt-0.5 text-[11px] leading-snug text-gray-500">
          勾选「待发放」随批执行；已配药/已发放可点「取消执行」回退。
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
        <div
          v-if="supplyItems.length === 0"
          class="flex h-full min-h-0 items-center justify-center"
        >
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
            @cancel="(id) => emit('cancelSupply', id)"
          />
        </div>
      </div>
    </template>

    <div v-else class="bg-background px-2 py-1.5">
      <SectionTitle title="皮试说明" />
      <p class="mt-0.5 text-[11px] leading-snug text-gray-500">
        列表仅展示已收费皮试检查；登记须填写临床结果与双签护士，保存不写费用。
      </p>
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
          <div v-if="record.operator && record.operator !== '—'" class="truncate text-gray-500">
            {{ record.operatorLabel ?? '执行人' }}：{{ record.operator }}
          </div>
          <div class="truncate text-gray-500">{{ record.target }}</div>
        </div>
        <div v-if="recentActionRecords.length === 0" class="py-6 text-center text-xs text-gray-400">
          暂无记录
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped></style>
