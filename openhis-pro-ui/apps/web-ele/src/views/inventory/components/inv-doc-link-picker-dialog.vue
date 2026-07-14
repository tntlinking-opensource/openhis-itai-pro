<!--
  库房关联单据选择弹窗：AppDialog + TableLayout，与系统 T2/T3 列表样式一致。
-->
<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, useSlots, watch } from 'vue';

import { TableLayout } from '@vben-core/table-layout-ui';

import AppDialog from '#/components/app-dialog.vue';
import {
  resolveInvDocLinkPickerAmount,
  resolveInvDocLinkPickerCreator,
  resolveInvDocLinkPickerWarehouse,
} from '#/utils/inventory/inv-doc-link-picker-display';

defineOptions({ name: 'InvDocLinkPickerDialog' });

interface Props {
  title: string;
  width?: string;
  tableData: Record<string, unknown>[];
  loading?: boolean;
  total?: number;
  queryParams: {
    pageNo: number;
    pageSize: number;
    keyword?: string;
    [key: string]: unknown;
  };
  tableColumns: TableColumn[];
  /** 覆盖默认「关键字」单项；传入时由调用方自行配置查询表单项 */
  formItems?: FormItemConfig[];
  /** 库位选项（用于列表「库房/药房」列 id→名称解析） */
  locationOptions?: Record<string, unknown>[];
  /** 制单人选项（applicantId/practitionerId 回落解析） */
  practitionerOptions?: ApiOptionItem[];
  keywordPlaceholder?: string;
  maxHeight?: number | string;
}

const props = withDefaults(defineProps<Props>(), {
  width: '1280px',
  loading: false,
  total: 0,
  formItems: undefined,
  locationOptions: () => [],
  practitionerOptions: () => [],
  keywordPlaceholder: '单号/关键字',
  maxHeight: 480,
});

const visible = defineModel<boolean>({ default: false });

const slots = useSlots();

const forwardedSlotNames = computed(() =>
  Object.keys(slots).filter(
    (name) =>
      name !== 'linkPickerCreator' && name !== 'linkPickerAmount' && name !== 'linkPickerWarehouse',
  ),
);

const emit = defineEmits<{
  opened: [];
  query: [params: Record<string, unknown>];
  reset: [];
  pagination: [pagination: { limit: number; page: number }];
  select: [row: Record<string, unknown>];
}>();

const formItems = computed<FormItemConfig[]>(() => {
  if (props.formItems?.length) {
    return props.formItems;
  }
  return [
    {
      label: '关键字',
      prop: 'keyword',
      type: 'input',
      placeholder: props.keywordPlaceholder,
    },
  ];
});

/** 顶部查询 */
function handleQuery(params: Record<string, unknown>) {
  emit('query', params);
}

/** 重置查询 */
function handleReset() {
  emit('reset');
}

/** 分页切换 */
function handlePagination(pagination: { limit: number; page: number }) {
  emit('pagination', pagination);
}

/** 点击行选中 */
function handleRowClick(row: Record<string, unknown>) {
  emit('select', row);
}

function formatLinkPickerCreator(row: unknown) {
  return resolveInvDocLinkPickerCreator(row as Record<string, unknown>, props.practitionerOptions);
}

function formatLinkPickerAmount(row: unknown) {
  return resolveInvDocLinkPickerAmount(row as Record<string, unknown>);
}

function formatLinkPickerWarehouse(row: unknown) {
  return resolveInvDocLinkPickerWarehouse(row as Record<string, unknown>, props.locationOptions);
}

watch(
  () => visible.value,
  (open) => {
    if (open) {
      emit('opened');
    }
  },
);
</script>

<template>
  <AppDialog v-model="visible" :title="title" :width="width" destroy-on-close top="5vh">
    <div class="inv-doc-link-picker-dialog">
      <TableLayout
        :table-data="tableData"
        :loading="loading"
        :total="total"
        :query-params="queryParams"
        :form-items="formItems"
        :table-columns="tableColumns"
        row-key="id"
        border
        stripe
        highlight-current-row
        :max-height="maxHeight"
        @query="handleQuery"
        @reset="handleReset"
        @pagination="handlePagination"
        @row-click="handleRowClick"
      >
        <template #linkPickerCreator="{ row }">
          <slot v-if="$slots.linkPickerCreator" name="linkPickerCreator" :row="row" />
          <span v-else>{{ formatLinkPickerCreator(row) }}</span>
        </template>
        <template #linkPickerAmount="{ row }">
          <slot v-if="$slots.linkPickerAmount" name="linkPickerAmount" :row="row" />
          <span v-else>{{ formatLinkPickerAmount(row) }}</span>
        </template>
        <template #linkPickerWarehouse="{ row }">
          <slot v-if="$slots.linkPickerWarehouse" name="linkPickerWarehouse" :row="row" />
          <span v-else>{{ formatLinkPickerWarehouse(row) }}</span>
        </template>
        <template v-for="name in forwardedSlotNames" :key="name" #[name]="slotData">
          <slot :name="name" v-bind="slotData ?? {}" />
        </template>
      </TableLayout>
    </div>
  </AppDialog>
</template>

<style scoped lang="scss">
.inv-doc-link-picker-dialog {
  min-height: 520px;
}
</style>
