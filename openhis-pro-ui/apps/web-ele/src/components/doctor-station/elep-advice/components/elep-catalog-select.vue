<script setup lang="ts">
import type {
  ElepCatalogItemDto,
  ElepCatalogPickerKind,
} from '#/api/doctor-station/elep-catalog.types';

import { computed } from 'vue';

import {
  httpSearchElepDiagnosisCatalog,
  httpSearchElepDrugCatalog,
} from '#/api/doctor-station/elep-catalog.http';
import SelectTable from '#/components/select-table.vue';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'ElepCatalogSelect' });

interface Props {
  disabled?: boolean;
  kind: ElepCatalogPickerKind;
  modelValue?: string;
  panelWidth?: number | string;
  placeholder?: string;
  selectedLabel?: string;
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  modelValue: '',
  panelWidth: undefined,
  placeholder: '',
  selectedLabel: '',
});

const emit = defineEmits<{
  change: [value: string];
  select: [item: ElepCatalogItemDto];
  'update:modelValue': [value: string];
}>();

const { run: runSearchDrug } = useRequest(httpSearchElepDrugCatalog, {
  showErrorMessage: true,
});

const { run: runSearchDiagnosis } = useRequest(httpSearchElepDiagnosisCatalog, {
  showErrorMessage: true,
});

const columns = computed(() => {
  if (props.kind === 'drug') {
    return [
      { prop: 'hilistName', label: '名称', minWidth: 160 },
      { prop: 'spec', label: '规格', width: 90 },
      { prop: 'dosform', label: '剂型', width: 72 },
      { prop: 'prodentpName', label: '厂家', minWidth: 140 },
    ];
  }
  return [{ prop: 'hilistName', label: '名称', minWidth: 220 }];
});

const resolvedPlaceholder = computed(() => {
  if (props.placeholder.trim()) return props.placeholder;
  return props.kind === 'drug' ? '输入药名 / 厂家 / 首字母后搜索' : '诊断名称 / 拼音';
});

const resolvedPanelWidth = computed(() => {
  if (props.panelWidth !== undefined) return props.panelWidth;
  return props.kind === 'drug' ? 680 : 420;
});

/**
 * 远程检索医保国家目录。按租户共享，不带挂号契约，避免市医保挂号把省医保灌入的目录滤空。
 * 药品必须先输入关键字：空搜会对 28 万摘要做去重全扫，请求超时后下拉是空的。
 *
 * @param params SelectTable 分页检索参数
 */
async function handleRemoteMethod(params: { keyword?: string; pageNo: number; pageSize: number }) {
  const keyword = String(params.keyword ?? '').trim();
  if (props.kind === 'drug' && !keyword) {
    return { content: [], totalCount: 0 };
  }
  const request = {
    keyword: keyword || undefined,
    page: Math.max((params.pageNo ?? 1) - 1, 0),
    pageSize: params.pageSize,
  };
  const res =
    props.kind === 'drug' ? await runSearchDrug(request) : await runSearchDiagnosis(request);
  const items = res?.items ?? [];
  return {
    content: items,
    totalCount: Number(res?.total ?? 0),
  };
}

/**
 * 点选目录行：把完整项交给父级再关下拉。
 *
 * @param item 目录行
 */
async function handleBeforeClose(item: ElepCatalogItemDto) {
  emit('select', item);
  return true;
}

/**
 * 同步 SelectTable 的值；空串表示清空。
 *
 * @param value 医保目录编码
 */
function handleChange(value: string | string[]) {
  const next = Array.isArray(value) ? (value[0] ?? '') : (value ?? '');
  emit('update:modelValue', next);
  emit('change', next);
}
</script>

<template>
  <SelectTable
    :model-value="modelValue"
    :columns="columns"
    :remote-method="handleRemoteMethod"
    :disabled="disabled"
    :placeholder="resolvedPlaceholder"
    :selected-label="selectedLabel"
    :panel-width="resolvedPanelWidth"
    value-key="hilistCode"
    label-key="hilistName"
    :before-close="handleBeforeClose"
    @change="handleChange"
  />
</template>
