<script setup lang="ts">
import type { IcdItemDto } from '#/api/mrms/types';

import SelectTable from '#/components/select-table.vue';
import { searchIcd10Api } from '#/api/mrms';

defineOptions({ name: 'MrmsDiagnosisPicker' });

interface Props {
  modelValue?: string;
  selectedLabel?: string;
  disabled?: boolean;
  placeholder?: string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: undefined,
  selectedLabel: undefined,
  disabled: false,
  placeholder: '检索 ICD-10 诊断',
});

const emit = defineEmits<{
  'update:modelValue': [code: string];
  picked: [item: IcdItemDto];
}>();

const icdColumns = [
  { prop: 'code', label: '编码', width: 120 },
  { prop: 'name', label: '名称', width: 280 },
  { prop: 'pyStr', label: '拼音', width: 100 },
];

/** 将 ICD-10 数组包装为 SelectTable 分页结构 */
async function remoteSearchIcd10(params: Record<string, unknown>) {
  const keyword = String(params.keyword ?? '').trim();
  const limit = Number(params.pageSize ?? 20) || 20;
  const list = await searchIcd10Api({ keyword, limit });
  const content = (list ?? []).map((item) => ({
    ...item,
    value: String(item.code ?? ''),
    label: String(item.name ?? ''),
  }));
  return { content, totalCount: content.length };
}

/** 选中行后回写编码并抛出完整项 */
function onCodeChange(code: string | string[]) {
  const v = Array.isArray(code) ? (code[0] ?? '') : code;
  emit('update:modelValue', v);
}

/** SelectTable 关闭下拉时若已选行则通知父组件更新名称 */
function onBeforeClose(item: Record<string, unknown>) {
  const picked: IcdItemDto = {
    id: item.id as string | undefined,
    code: String(item.code ?? item.value ?? ''),
    name: String(item.name ?? item.label ?? ''),
    pyStr: item.pyStr as string | undefined,
    wbStr: item.wbStr as string | undefined,
  };
  emit('picked', picked);
  return true;
}
</script>

<template>
  <SelectTable
    :model-value="props.modelValue"
    :selected-label="props.selectedLabel"
    :disabled="props.disabled"
    :placeholder="props.placeholder"
    :remote-method="remoteSearchIcd10"
    :columns="icdColumns"
    value-key="code"
    label-key="name"
    :panel-width="640"
    :before-close="onBeforeClose"
    @update:model-value="onCodeChange"
  />
</template>
