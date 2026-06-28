<script setup lang="ts">
import type { AdjustableItemRowDto } from '#/api/finance/price-adjustment';

import { computed } from 'vue';

import { searchAdjustableItemsApi } from '#/api/finance/price-adjustment';
import SelectTable from '#/components/select-table.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

type PriceAdjustmentItemPickRow = AdjustableItemRowDto & {
  id?: number | string;
  name?: string;
};

interface Props {
  modelValue?: null | PriceAdjustmentItemPickRow;
  placeholder?: string;
  disabled?: boolean;
  clearable?: boolean;
  pageSize?: number;
  query?: () => Promise<Record<string, unknown>>;
  inputHeight?: number | string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: undefined,
  placeholder: '请输入关键字检索项目',
  disabled: false,
  clearable: true,
  pageSize: 20,
  query: () => Promise.resolve({}),
  inputHeight: undefined,
});

const emit = defineEmits<{
  change: [value: PriceAdjustmentItemPickRow | undefined];
  'update:modelValue': [value: PriceAdjustmentItemPickRow | undefined];
}>();

const tableColumns = computed(() => [
  { prop: 'itemCode', label: '项目编码', width: 120 },
  { prop: 'itemName', label: '项目名称', minWidth: 160 },
  { prop: 'specification', label: '规格', width: 140 },
  { prop: 'unitName', label: '单位', width: 72, align: 'center' as const },
  { prop: 'categoryCodeDictText', label: '种类', width: 88, align: 'center' as const },
]);

/** 远程检索可选调价项目 */
async function handleRemoteMethod(params: { keyword?: string; pageNo: number; pageSize: number }) {
  const q = await props.query();
  const itemTable = String(q.itemTable ?? '').trim();
  if (!itemTable) {
    return { content: [], totalElements: 0 };
  }
  const res = await searchAdjustableItemsApi({
    itemTable,
    orgId: preserveApiIdForRequest(q.orgId as number | string | undefined),
    keyword: params.keyword?.trim() || undefined,
    pageNo: params.pageNo,
    pageSize: params.pageSize,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<AdjustableItemRowDto>(res.data);
  return { content, totalElements: totalCount };
}

/** 选中列表行后直接作为 modelValue */
async function handleBeforeClose(item: Record<string, unknown>) {
  const row = item as AdjustableItemRowDto;
  const pick: PriceAdjustmentItemPickRow = {
    ...row,
    id: preserveApiIdForRequest(row.itemId),
    name: row.itemName,
  };
  emit('update:modelValue', pick);
  emit('change', pick);
  return true;
}

function handleChange(value: null | string | string[] | undefined) {
  if (value === null || value === undefined || value === '') {
    emit('update:modelValue', undefined);
    emit('change', undefined);
  }
}

/** 编辑格回显：优先 itemName，其次 name */
function selectedItemName(): string {
  const v = props.modelValue;
  if (!v) return '';
  const fromItemName = v.itemName;
  if (fromItemName !== undefined && fromItemName !== null) {
    const s = String(fromItemName).trim();
    if (s) return s;
  }
  const fromName = v.name;
  if (fromName !== undefined && fromName !== null) {
    const s = String(fromName).trim();
    if (s) return s;
  }
  return '';
}

function resolvedModelValue(): string | undefined {
  const id = preserveApiIdForRequest(props.modelValue?.itemId ?? props.modelValue?.id);
  return id || undefined;
}
</script>

<template>
  <SelectTable
    :model-value="resolvedModelValue()"
    :remote-method="handleRemoteMethod"
    :columns="tableColumns"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :page-size="pageSize"
    value-key="itemId"
    label-key="itemName"
    :selected-label="selectedItemName()"
    :before-close="handleBeforeClose"
    :input-height="inputHeight"
    :panel-width="900"
    @update:model-value="handleChange"
    @change="handleChange"
  />
</template>
