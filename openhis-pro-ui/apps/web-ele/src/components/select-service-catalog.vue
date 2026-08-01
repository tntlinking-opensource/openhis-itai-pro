<script setup lang="ts">
import type { ServiceCatalogItem } from '#/api';

import { ElMessage } from 'element-plus';

import { pageServiceCatalogApi } from '#/api';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import SelectTable from './select-table.vue';

defineOptions({
  name: 'SelectServiceCatalog',
});

interface Props {
  modelValue?: null | string | string[];
  placeholder?: string;
  disabled?: boolean;
  clearable?: boolean;
  filterable?: boolean;
  multiple?: boolean;
  class?: string;
  /** 编辑子项时排除自身父项，避免套餐自引用 */
  excludeId?: null | number | string;
  /** 仅 id 回填时用于输入框展示名称 */
  selectedLabel?: string;
  pageSize?: number;
  /** 兼容历史调用，无业务语义 */
  isSelectable?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请选择诊疗项目',
  disabled: false,
  clearable: true,
  filterable: true,
  multiple: false,
  class: '',
  excludeId: null,
  selectedLabel: undefined,
  pageSize: 20,
  isSelectable: true,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: null | string | string[]): void;
  (e: 'change', value: ServiceCatalogItem[]): void;
}>();

const tableColumns = [
  { prop: 'busNo', label: '编码', width: 140 },
  { prop: 'name', label: '诊疗名称', minWidth: 200 },
  { prop: 'categoryCode_dictText', label: '分类', minWidth: 120 },
];

/** 分页检索诊疗目录，并按 excludeId 过滤当前父项 */
async function handleRemoteMethod(params: { keyword?: string; pageNo: number; pageSize: number }) {
  const { pageNo, pageSize, keyword } = params;
  try {
    const response = await pageServiceCatalogApi({
      pageNo,
      pageSize,
      keyword: keyword?.trim() || undefined,
      // 维护父项时挑选子项，须能检索明细行
      includeReferencedPackageChildren: true,
    });
    const parsed = parsePagedRowsFromApiData<ServiceCatalogItem>(response?.data);
    const excludeId = props.excludeId != null ? String(props.excludeId) : '';
    const content = excludeId
      ? parsed.content.filter((item) => String(item.id ?? '') !== excludeId)
      : parsed.content;
    return {
      content,
      totalCount: parsed.totalCount,
    };
  } catch (error) {
    console.error('加载诊疗目录失败:', error);
    ElMessage.error('加载诊疗目录失败，请稍后重试');
    return { content: [], totalCount: 0 };
  }
}

/** 选中行后回传完整诊疗项供子项名称回填 */
async function handleBeforeClose(item: Record<string, unknown>, _value: string) {
  emit('change', [item as ServiceCatalogItem]);
  return true;
}

/** 清空选择时同步子项展示字段 */
function handleChange(value: null | string | string[] | undefined) {
  if (value === null || value === undefined || value === '') {
    emit('update:modelValue', null);
    emit('change', []);
    return;
  }
  emit('update:modelValue', value);
}
</script>

<template>
  <SelectTable
    :model-value="modelValue ?? undefined"
    :remote-method="handleRemoteMethod"
    :columns="tableColumns"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :filterable="filterable"
    :page-size="pageSize"
    value-key="id"
    label-key="name"
    :selected-label="selectedLabel"
    :before-close="handleBeforeClose"
    :class="props.class"
    class="w-full"
    @update:model-value="handleChange"
    @change="handleChange"
  />
</template>
