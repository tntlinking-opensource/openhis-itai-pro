<!--
  药库退货入库单左侧：历史退货入库单检索 + 卡片队列（只读壳单，无新增）。
-->
<script setup lang="ts">
import { ref } from 'vue';

import { RotateCw, Search } from '@zxplat/icons';

import InvAsideShell from '@inventory/components/inv-aside-shell.vue';
import InvQueueCards from '@inventory/components/inv-queue-cards.vue';
import dayjs from 'dayjs';
import isoWeek from 'dayjs/plugin/isoWeek';
import { ElButton, ElDatePicker, ElInput, ElOption, ElSelect } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';

defineOptions({ name: 'InventoryWarehouseReturnStockInAsidePane' });

defineProps<{
  cabinetListOptions: Record<string, unknown>[];
  orders: Record<string, unknown>[];
  selectedOrder?: null | Record<string, unknown>;
}>();

const emit = defineEmits<{
  search: [];
  select: [row: Record<string, unknown>];
}>();

const purposeLocationId = defineModel<number | string | undefined>('purposeLocationId');
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

/** 下拉 option value */
function cabinetOptionValue(item: Record<string, unknown>): number | string {
  const v = item.id ?? item.value;
  if (typeof v === 'number' || typeof v === 'string') return v;
  return String(v ?? '');
}

/** 药库筛选变更 → 刷新列表 */
function handleLocationFiltersChange() {
  emit('search');
}

/** 关键字回车/清空 → 检索 */
function handleSearchEmit() {
  emit('search');
}

/** 日期范围变更 → 检索 */
function handleDateRangeChange() {
  emit('search');
}

/** 刷新按钮 */
function handleRefreshClick() {
  isRefreshing.value = true;
  emit('search');
  setTimeout(() => {
    isRefreshing.value = false;
  }, 500);
}

/** 选中队列卡片 */
function handleQueueSelect(row: Record<string, unknown>) {
  emit('select', row);
}
</script>

<template>
  <InvAsideShell class="return-stock-in-aside-pane">
    <template #header>
      <div class="flex flex-col gap-1.5">
        <SectionTitle title="历史退货入库单" />
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
        <div class="return-stock-in-daterange-wrap w-full min-w-0">
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
            v-model="purposeLocationId"
            class="min-w-0 flex-1"
            placeholder="全部库房"
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
        :location-context-id="purposeLocationId"
        :orders="orders"
        :selected-order="selectedOrder"
        :show-empty-add="false"
        :show-supplier="false"
        @select="handleQueueSelect"
      />
    </template>
  </InvAsideShell>
</template>

<style scoped lang="scss">
.return-stock-in-daterange-wrap :deep(.el-date-editor.el-date-editor--daterange) {
  width: 100%;
  max-width: 100%;
  --el-date-editor-daterange-width: 100%;
}
</style>
