<!--
  药房退库单左侧：历史退库单检索 + 卡片队列（从入库单新建入口在右侧操作区）。
-->
<script setup lang="ts">
import { ref } from 'vue';

import { RotateCw, Search } from '@vben/icons';

import InvAsideShell from '@inventory/components/inv-aside-shell.vue';
import InvQueueCards from '@inventory/components/inv-queue-cards.vue';
import dayjs from 'dayjs';
import isoWeek from 'dayjs/plugin/isoWeek';
import { ElButton, ElDatePicker, ElInput, ElOption, ElSelect } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';

defineOptions({ name: 'InventoryPharmacyReturnToWarehouseAsidePane' });

defineProps<{
  cabinetListOptions: Record<string, unknown>[];
  orders: Record<string, unknown>[];
  selectedOrder?: null | Record<string, unknown>;
}>();

const emit = defineEmits<{
  search: [];
  select: [row: Record<string, unknown>];
}>();

const sourceLocationId = defineModel<number | string | undefined>('sourceLocationId');
const keyword = defineModel<string>('keyword', { default: '' });
const dateRange = defineModel<[string, string] | null>('dateRange', { default: null });

dayjs.extend(isoWeek);

const rangeShortcuts = [
  {
    text: '今天',
    value: () => {
      const d = dayjs();
      return [d.toDate(), d.toDate()];
    },
  },
  {
    text: '昨天',
    value: () => {
      const d = dayjs().subtract(1, 'day');
      return [d.toDate(), d.toDate()];
    },
  },
  {
    text: '本周',
    value: () => [dayjs().startOf('isoWeek').toDate(), dayjs().endOf('isoWeek').toDate()],
  },
  {
    text: '本月',
    value: () => [dayjs().startOf('month').toDate(), dayjs().endOf('month').toDate()],
  },
  {
    text: '近三月',
    value: () => [
      dayjs().subtract(2, 'month').startOf('month').toDate(),
      dayjs().endOf('day').toDate(),
    ],
  },
];

const isRefreshing = ref(false);

function cabinetOptionValue(item: Record<string, unknown>): number | string {
  const v = item.id ?? item.value;
  if (typeof v === 'number' || typeof v === 'string') return v;
  return String(v ?? '');
}

function handleLocationFiltersChange() {
  emit('search');
}

function handleSearchEmit() {
  emit('search');
}

function handleDateRangeChange() {
  emit('search');
}

function handleRefreshClick() {
  isRefreshing.value = true;
  emit('search');
  setTimeout(() => {
    isRefreshing.value = false;
  }, 500);
}

function handleQueueSelect(row: Record<string, unknown>) {
  emit('select', row);
}
</script>

<template>
  <InvAsideShell class="return-to-warehouse-aside-pane">
    <template #header>
      <div class="flex flex-col gap-1.5">
        <SectionTitle title="历史退库单" />
        <ElInput
          v-model="keyword"
          placeholder="单据号检索"
          clearable
          class="w-full"
          @keyup.enter="handleSearchEmit"
          @clear="handleSearchEmit"
        >
          <template #prefix>
            <Search class="h-4 w-4 text-[var(--el-text-color-secondary)]" />
          </template>
        </ElInput>
        <div class="return-to-warehouse-daterange-wrap w-full min-w-0">
          <ElDatePicker
            v-model="dateRange"
            class="w-full"
            type="daterange"
            unlink-panels
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            :shortcuts="rangeShortcuts"
            @change="handleDateRangeChange"
          />
        </div>
        <div class="flex min-w-0 items-center gap-2">
          <ElSelect
            v-model="sourceLocationId"
            class="min-w-0 flex-1"
            placeholder="请选择药房"
            clearable
            filterable
            @change="handleLocationFiltersChange"
          >
            <ElOption
              v-for="item in cabinetListOptions"
              :key="String(item.id ?? item.value)"
              :label="String(item.name ?? item.label ?? '')"
              :value="cabinetOptionValue(item)"
            />
          </ElSelect>
          <ElButton type="default" class="shrink-0 !px-2" @click="handleRefreshClick">
            <RotateCw class="h-4 w-4" :class="{ 'animate-spin': isRefreshing }" />
          </ElButton>
        </div>
      </div>
    </template>

    <template #queue>
      <InvQueueCards
        :cabinet-list-options="cabinetListOptions"
        :location-context-id="sourceLocationId"
        :orders="orders"
        :selected-order="selectedOrder"
        empty-description="暂无退库单"
        :show-empty-add="false"
        @select="handleQueueSelect"
      />
    </template>
  </InvAsideShell>
</template>

<style scoped lang="scss">
.return-to-warehouse-daterange-wrap :deep(.el-date-editor.el-date-editor--daterange) {
  width: 100%;
  max-width: 100%;
  --el-date-editor-daterange-width: 100%;
}
</style>
