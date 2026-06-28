<!--
  添加调价项目：按项目表 Tab 检索可选项目（IF-PRC-04）。
-->
<script setup lang="ts">
import type { ApiOptionItem } from '#/types/api-response';

import type { AdjustableItemRowDto } from '#/api/finance/price-adjustment';

import { computed, ref, watch } from 'vue';

import { Search } from '@vben/icons';

import { searchAdjustableItemsApi } from '#/api/finance/price-adjustment';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  ElButton,
  ElDialog,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
} from 'element-plus';

defineOptions({ name: 'PriceAdjustmentAddItemDialog' });

interface Props {
  itemTableTabOptions?: ApiOptionItem[];
  orgId?: string;
  visible: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  itemTableTabOptions: () => [],
  orgId: '',
});

const emit = defineEmits<{
  confirm: [items: AdjustableItemRowDto[]];
  'update:visible': [value: boolean];
}>();

const keyword = ref('');
const activeItemTable = ref('');
const loading = ref(false);
const tableRows = ref<AdjustableItemRowDto[]>([]);
const selectedRows = ref<AdjustableItemRowDto[]>([]);

/** 当前 Tab 对应项目表 */
const resolvedItemTable = computed(() => {
  if (activeItemTable.value) return activeItemTable.value;
  return String(props.itemTableTabOptions[0]?.value ?? '');
});

/** 检索可选调价项目 */
async function fetchItems() {
  const itemTable = resolvedItemTable.value.trim();
  if (!itemTable) {
    tableRows.value = [];
    return;
  }
  loading.value = true;
  try {
    const res = await searchAdjustableItemsApi({
      itemTable,
      orgId: preserveApiIdForRequest(props.orgId),
      keyword: keyword.value.trim() || undefined,
      pageNo: 1,
      pageSize: 50,
    });
    const { content } = parsePagedRowsFromApiData<AdjustableItemRowDto>(res.data);
    tableRows.value = content;
  } catch {
    tableRows.value = [];
  } finally {
    loading.value = false;
  }
}

/** 弹窗打开时重置并拉取首 Tab 数据 */
watch(
  () => props.visible,
  (open) => {
    if (!open) return;
    keyword.value = '';
    selectedRows.value = [];
    activeItemTable.value = String(props.itemTableTabOptions[0]?.value ?? '');
    void fetchItems();
  },
);

/** Tab 切换后重新检索 */
function handleTabChange(name: string | number) {
  activeItemTable.value = String(name);
  void fetchItems();
}

/** 关闭弹窗 */
function handleClose() {
  emit('update:visible', false);
}

/** 将选中项目带回明细表 */
function handleConfirm() {
  if (selectedRows.value.length === 0) return;
  emit('confirm', [...selectedRows.value]);
  emit('update:visible', false);
}

/** 表格多选变更 */
function handleSelectionChange(rows: AdjustableItemRowDto[]) {
  selectedRows.value = rows;
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    title="添加调价项目"
    width="960px"
    destroy-on-close
    @close="handleClose"
    @update:model-value="emit('update:visible', $event)"
  >
    <ElTabs v-if="itemTableTabOptions.length > 0" @tab-change="handleTabChange">
      <ElTabPane
        v-for="tab in itemTableTabOptions"
        :key="String(tab.value)"
        :label="String(tab.label ?? '')"
        :name="String(tab.value ?? '')"
      />
    </ElTabs>
    <ElSelect
      v-else
      v-model="activeItemTable"
      class="mb-3 w-full"
      placeholder="项目表"
      @change="fetchItems"
    >
      <ElOption label="—" value="" disabled />
    </ElSelect>

    <ElInput
      v-model="keyword"
      placeholder="编码 / 名称检索"
      clearable
      class="mb-3"
      @keyup.enter="fetchItems"
      @clear="fetchItems"
    >
      <template #prefix>
        <Search class="h-4 w-4 text-[var(--el-text-color-secondary)]" />
      </template>
      <template #append>
        <ElButton :loading="loading" @click="fetchItems">检索</ElButton>
      </template>
    </ElInput>

    <ElTable
      v-loading="loading"
      :data="tableRows"
      height="360"
      border
      stripe
      @selection-change="handleSelectionChange"
    >
      <ElTableColumn type="selection" width="48" />
      <ElTableColumn prop="itemCode" label="项目编码" width="120" />
      <ElTableColumn prop="itemName" label="项目名称" min-width="160" show-overflow-tooltip />
      <ElTableColumn prop="specification" label="规格" width="140" show-overflow-tooltip />
      <ElTableColumn prop="unitName" label="单位" width="72" align="center" />
      <ElTableColumn prop="adjustmentStyle" label="口径" width="88" align="center" />
    </ElTable>

    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton type="primary" :disabled="selectedRows.length === 0" @click="handleConfirm">
        确定（{{ selectedRows.length }}）
      </ElButton>
    </template>
  </ElDialog>
</template>
