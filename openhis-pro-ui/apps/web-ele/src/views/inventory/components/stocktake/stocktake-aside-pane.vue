<!--
  盘库/盘点单左侧：历史单据检索 + 卡片队列 + 新增（与进货单侧栏同构）。
-->
<script setup lang="ts">
import { ref } from 'vue';

import { RotateCw, Search } from '@zxplat/icons';

import InvAsideShell from '@inventory/components/inv-aside-shell.vue';
import InvPrimaryBtn from '@inventory/components/inv-primary-btn.vue';
import InvQueueCards from '@inventory/components/inv-queue-cards.vue';
import dayjs from 'dayjs';
import isoWeek from 'dayjs/plugin/isoWeek';
import { ElButton, ElDatePicker, ElInput, ElOption, ElSelect } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';

defineOptions({ name: 'InventoryStocktakeAsidePane' });

const props = withDefaults(
  defineProps<{
    addDisabled?: boolean;
    cabinetListOptions: Record<string, unknown>[];
    locationLabel?: string;
    orders: Record<string, unknown>[];
    selectedOrder?: null | Record<string, unknown>;
  }>(),
  {
    addDisabled: false,
    locationLabel: '盘点库房',
  },
);

const emit = defineEmits<{
  add: [];
  locationChange: [locationId: number | string | undefined];
  search: [];
  select: [row: Record<string, unknown>];
}>();

const locationId = defineModel<number | string | undefined>('locationId');
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

function handleLocationSelectChange(val: number | string | undefined) {
  emit('locationChange', val);
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

function handleAdd() {
  emit('add');
}

function resolveQueueWarehouseLine(row: Record<string, unknown>): string | undefined {
  if (!row._draft) {
    return row.purposeLocationName
      ? String(row.purposeLocationName)
      : row.purposeLocationId_dictText
        ? String(row.purposeLocationId_dictText)
        : undefined;
  }
  const lid = row.purposeLocationId ?? locationId.value;
  if (lid === undefined || lid === null || lid === '') return undefined;
  const idStr = String(lid);
  for (const o of props.cabinetListOptions) {
    const v = o.id ?? o.value;
    if (String(v) === idStr) return String(o.name ?? o.label ?? '').trim() || undefined;
  }
  return undefined;
}
</script>

<template>
  <InvAsideShell class="stocktake-aside-pane">
    <template #header>
      <div class="flex flex-col gap-1.5">
        <SectionTitle title="历史盘点单" />
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
        <div class="stocktake-daterange-wrap w-full min-w-0">
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
            v-model="locationId"
            class="min-w-0 flex-1"
            :placeholder="locationLabel"
            clearable
            filterable
            @change="handleLocationSelectChange"
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
        :location-context-id="locationId"
        :orders="orders"
        :resolve-warehouse-line="resolveQueueWarehouseLine"
        :selected-order="selectedOrder"
        @add="handleAdd"
        @select="handleQueueSelect"
      />
    </template>

    <template #footer>
      <InvPrimaryBtn label="新增盘点单" :disabled="addDisabled" @click="handleAdd" />
    </template>
  </InvAsideShell>
</template>

<style scoped lang="scss">
.stocktake-daterange-wrap :deep(.el-date-editor.el-date-editor--daterange) {
  width: 100%;
  max-width: 100%;
  --el-date-editor-daterange-width: 100%;
}
</style>
