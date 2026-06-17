<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { ServiceCatalogItem } from '#/api';

import { computed } from 'vue';

import { TableSelectDialog } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { pageServiceCatalogApi } from '#/api';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

defineOptions({
  name: 'SelectServiceCatalogDialog',
});

interface Props {
  modelValue?: null | string | string[];
  placeholder?: string;
  /** 默认多选 */
  multiple?: boolean;
  dialogTitle?: string;
  dialogWidth?: string;
  inputClass?: string;
  /** 是否显示输入框 */
  showInput?: boolean;
  /** 编辑子项时排除自身父项，避免套餐自引用 */
  excludeId?: null | number | string;
}

withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请选择诊疗项目',
  multiple: false,
  dialogTitle: '选择诊疗项目',
  dialogWidth: '70%',
  inputClass: '',
  showInput: true,
  excludeId: null,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: null | string | string[]): void;
  (e: 'change', item: ServiceCatalogItem | ServiceCatalogItem[] | null): void;
}>();

const columns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
    width: 60,
  },
  {
    prop: 'busNo',
    label: '编码',
    width: 140,
    showOverflowTooltip: true,
  },
  {
    prop: 'name',
    label: '诊疗名称',
    minWidth: 160,
    showOverflowTooltip: true,
  },
  {
    prop: 'categoryCode_dictText',
    label: '分类',
    minWidth: 120,
    showOverflowTooltip: true,
  },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入名称/编码',
    clearable: true,
  },
]);

/** 分页检索诊疗目录供弹窗表格选择 */
async function requestServiceList(params: Record<string, string | number | undefined>) {
  const keyword = typeof params.keyword === 'string' ? params.keyword : '';
  const pageNo = typeof params.pageNo === 'number' ? params.pageNo : 1;
  const pageSize = typeof params.pageSize === 'number' ? params.pageSize : 20;
  try {
    const response = await pageServiceCatalogApi({
      pageNo,
      pageSize,
      keyword: keyword.trim() || undefined,
    });
    const parsed = parsePagedRowsFromApiData<ServiceCatalogItem>(response?.data);
    const excludeId =
      typeof (params as { excludeId?: string | number }).excludeId !== 'undefined'
        ? String((params as { excludeId?: string | number }).excludeId)
        : '';
    const content = excludeId
      ? parsed.content.filter((item) => String(item.id ?? '') !== excludeId)
      : parsed.content;
    return {
      content,
      total: parsed.totalCount,
      totalElements: parsed.totalCount,
    };
  } catch (error) {
    console.error('加载诊疗目录失败:', error);
    ElMessage.error('加载诊疗目录失败，请稍后重试');
    return {
      content: [],
      total: 0,
      totalElements: 0,
    };
  }
}

/** 同步弹窗选中值到 v-model */
function handleModelUpdate(value: null | string | string[]) {
  emit('update:modelValue', value);
}

/** 回传弹窗选中的诊疗项 */
function handleChange(item: ServiceCatalogItem | ServiceCatalogItem[] | null) {
  emit('change', item);
}
</script>

<template>
  <TableSelectDialog
    :model-value="modelValue"
    :placeholder="placeholder"
    :multiple="multiple"
    :columns="columns"
    :form-items="formItems"
    :request="requestServiceList"
    :data="null"
    display-field="name"
    value-field="id"
    :dialog-title="dialogTitle"
    :dialog-width="dialogWidth"
    :input-class="inputClass"
    :show-input="showInput"
    :query="{ excludeId }"
    @update:model-value="handleModelUpdate"
    @change="handleChange"
  />
</template>
