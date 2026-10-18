<!--
  合管配备：检验项目批量选品弹窗。
  左侧分页选品（排除已绑定），点击行加入右侧已选；确认后回写父表。
-->
<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';
import type { ServiceCatalogItem } from '#/api';

import { computed, ref, watch } from 'vue';

import { TableLayout } from '@zxplat-core/table-layout-ui';
import { ElButton, ElMessage } from 'element-plus';

import { pageServiceCatalogApi } from '#/api';
import { LAB_ACTIVITY_CATEGORY_CODE } from '#/api/basic/specimen-tube-merge';
import AppDialog from '#/components/app-dialog.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import { PublicationStatusCode } from '#/constants/domain/publication-status';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

defineOptions({ name: 'SpecimenTubeMergeBatchAddActivityDialog' });

export interface BatchActivityPickRow {
  id: string;
  busNo?: string;
  name?: string;
  categoryCode_dictText?: string;
  packageUnitCode_dictText?: string;
  retailPrice?: string;
  specification?: string;
}

interface Props {
  visible: boolean;
  /** 当前设备已维护（含未保存草稿行）的检验项目 id，不进左侧候选 */
  excludeActivityIds?: string[];
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  excludeActivityIds: () => [],
});

const emit = defineEmits<{
  'update:visible': [value: boolean];
  confirm: [items: BatchActivityPickRow[]];
}>();

const queryParams = ref({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
});

const tableData = ref<BatchActivityPickRow[]>([]);
const selectedRows = ref<BatchActivityPickRow[]>([]);
const loading = ref(false);
const total = ref(0);

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

/** 已绑定 id 集合（稳定 string 比较） */
const excludeIdSet = computed(() => {
  const set = new Set<string>();
  for (const id of props.excludeActivityIds ?? []) {
    const key = String(id ?? '').trim();
    if (key) {
      set.add(key);
    }
  }
  return set;
});

/** 右侧已选 id 集合，供左侧行点击去重与提示 */
const selectedIdSet = computed(() => {
  const set = new Set<string>();
  for (const row of selectedRows.value) {
    const key = String(row.id ?? '').trim();
    if (key) {
      set.add(key);
    }
  }
  return set;
});

const formItems: FormItemConfig[] = [
  {
    label: '关键字',
    prop: 'keyword',
    type: 'input',
    placeholder: '项目编码 / 名称',
  },
];

const pickTableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, align: 'center' },
  { prop: 'busNo', label: '编码', width: 140, showOverflowTooltip: true },
  { prop: 'name', label: '项目名称', minWidth: 180, showOverflowTooltip: true },
  { prop: 'categoryCode_dictText', label: '分类', width: 110, showOverflowTooltip: true },
  { prop: 'packageUnitCode_dictText', label: '单位', width: 88, showOverflowTooltip: true },
  { prop: 'retailPrice', label: '售价', width: 96, align: 'right' },
  { prop: 'specification', label: '规格', width: 120, showOverflowTooltip: true },
];

const selectedTableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, align: 'center' },
  { prop: 'busNo', label: '编码', width: 120, showOverflowTooltip: true },
  { prop: 'name', label: '项目名称', minWidth: 160, showOverflowTooltip: true },
  { prop: 'categoryCode_dictText', label: '分类', width: 100, showOverflowTooltip: true },
  {
    label: '操作',
    width: 72,
    align: 'center',
    fixed: 'right',
    slot: 'rowActions',
  },
];

/** 诊疗目录是否启用态（与合管页单行选品一致） */
function isServicePublicationActive(row: ServiceCatalogItem): boolean {
  const status = row.statusEnum;
  if (
    status === PublicationStatusCode.INACTIVE ||
    status === PublicationStatusCode.DISCARDED ||
    status === 3 ||
    status === '3'
  ) {
    return false;
  }
  return (
    status === PublicationStatusCode.ACTIVE ||
    status === 1 ||
    status === '1' ||
    status === 2 ||
    status === '2'
  );
}

/** 格式化选品列表售价 */
function formatPickRetailPrice(price: unknown): string {
  if (price === null || price === undefined || price === '') {
    return '';
  }
  const num = Number(price);
  return Number.isFinite(num) ? formatMoneyDisplay(num) : String(price);
}

/** 目录行 → 批量选品行（仅保留展示列） */
function mapCatalogToPickRow(row: ServiceCatalogItem): BatchActivityPickRow | null {
  const id = String(row.id ?? '').trim();
  if (!id) {
    return null;
  }
  return {
    id,
    busNo: row.busNo != null ? String(row.busNo) : undefined,
    name: row.name != null ? String(row.name) : undefined,
    categoryCode_dictText:
      row.categoryCode_dictText != null ? String(row.categoryCode_dictText) : undefined,
    packageUnitCode_dictText:
      row.packageUnitCode_dictText != null ? String(row.packageUnitCode_dictText) : undefined,
    retailPrice: formatPickRetailPrice(row.retailPrice),
    specification: row.specification != null ? String(row.specification) : undefined,
  };
}

/** 加载左侧可选检验项目（排除表内已绑定；本弹窗已选仍保留并高亮） */
async function getList() {
  loading.value = true;
  try {
    const response = await pageServiceCatalogApi({
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      keyword: String(queryParams.value.keyword ?? '').trim() || undefined,
      categoryCode: LAB_ACTIVITY_CATEGORY_CODE,
      includeReferencedPackageChildren: true,
    });
    const parsed = parsePagedRowsFromApiData<ServiceCatalogItem>(response?.data);
    // 启用态 + 排除表内已维护；本弹窗右侧已选仍留在左侧并靠 rowClass 回显
    const content: BatchActivityPickRow[] = [];
    for (const row of parsed.content) {
      if (!isServicePublicationActive(row)) {
        continue;
      }
      const mapped = mapCatalogToPickRow(row);
      if (!mapped) {
        continue;
      }
      if (excludeIdSet.value.has(mapped.id)) {
        continue;
      }
      content.push(mapped);
    }
    tableData.value = content;
    // 服务端 total 含已排除项，仅作分页参考；左侧展示条数可能少于 pageSize
    total.value = parsed.totalCount;
  } catch {
    tableData.value = [];
    total.value = 0;
    ElMessage.error('加载检验项目失败，请稍后重试');
  } finally {
    loading.value = false;
  }
}

/** 顶部查询 */
function handleQuery(params: Record<string, unknown>) {
  Object.assign(queryParams.value, params);
  queryParams.value.pageNo = 1;
  void getList();
}

/** 重置查询 */
function handleReset() {
  queryParams.value = { pageNo: 1, pageSize: 20, keyword: '' };
  void getList();
}

/** 分页切换 */
function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  void getList();
}

/** 左侧候选行：已在右侧待添加时高亮，便于翻页后回显已选 */
function pickRowClassName(params: { row: Record<string, unknown> }): string {
  const key = String(params.row.id ?? '').trim();
  if (key && selectedIdSet.value.has(key)) {
    return 'stm-batch-pick-row--selected';
  }
  return '';
}

/** 将行加入右侧已选（按 id 去重） */
function appendPickRows(rows: BatchActivityPickRow[]): number {
  const seen = new Set(selectedRows.value.map((item) => String(item.id ?? '').trim()));
  let added = 0;
  for (const row of rows) {
    const key = String(row.id ?? '').trim();
    if (!key || seen.has(key) || excludeIdSet.value.has(key)) {
      continue;
    }
    seen.add(key);
    selectedRows.value.push(row);
    added++;
  }
  return added;
}

/** 点击左侧行：加入已选；已在右侧则提示（回显已选语义） */
function handlePickRowClick(row: Record<string, unknown>) {
  const key = String(row.id ?? '').trim();
  if (!key) {
    return;
  }
  if (excludeIdSet.value.has(key)) {
    ElMessage.warning('该检验项目已在合管绑定列表中');
    return;
  }
  if (selectedIdSet.value.has(key)) {
    ElMessage.warning('该检验项目已在待添加列表中');
    return;
  }
  appendPickRows([row as unknown as BatchActivityPickRow]);
}

/** 全选：按当前关键字拉取一页窗口内可选项目加入已选 */
async function selectAllPickRows() {
  if (total.value === 0) {
    ElMessage.warning('暂无可选项目');
    return;
  }
  loading.value = true;
  try {
    const pageSize = Math.min(Math.max(total.value, 1), 500);
    const response = await pageServiceCatalogApi({
      pageNo: 1,
      pageSize,
      keyword: String(queryParams.value.keyword ?? '').trim() || undefined,
      categoryCode: LAB_ACTIVITY_CATEGORY_CODE,
      includeReferencedPackageChildren: true,
    });
    const parsed = parsePagedRowsFromApiData<ServiceCatalogItem>(response?.data);
    const candidates: BatchActivityPickRow[] = [];
    for (const row of parsed.content) {
      if (!isServicePublicationActive(row)) {
        continue;
      }
      const mapped = mapCatalogToPickRow(row);
      if (!mapped) {
        continue;
      }
      if (excludeIdSet.value.has(mapped.id)) {
        continue;
      }
      candidates.push(mapped);
    }
    const added = appendPickRows(candidates);
    if (added === 0) {
      ElMessage.info('当前筛选结果均已加入待添加列表或已在绑定中');
      return;
    }
    ElMessage.success(`已加入 ${added} 条项目`);
  } finally {
    loading.value = false;
  }
}

/** 从已选区域移除 */
function removeSelected(row: BatchActivityPickRow | Record<string, unknown>) {
  const key = String(row.id ?? '').trim();
  selectedRows.value = selectedRows.value.filter((item) => String(item.id ?? '').trim() !== key);
}

/** 关闭并重置 */
function handleClose() {
  dialogVisible.value = false;
  queryParams.value = { pageNo: 1, pageSize: 20, keyword: '' };
  tableData.value = [];
  selectedRows.value = [];
  total.value = 0;
}

/** 确认批量添加 */
function handleConfirm() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请至少选择一个检验项目');
    return;
  }
  emit('confirm', [...selectedRows.value]);
  handleClose();
}

watch(
  () => props.visible,
  (open) => {
    if (open) {
      queryParams.value = { pageNo: 1, pageSize: 20, keyword: '' };
      selectedRows.value = [];
      void getList();
    }
  },
);
</script>

<template>
  <AppDialog
    v-model="dialogVisible"
    title="批量添加检验项目"
    width="1200px"
    destroy-on-close
    @close="handleClose"
  >
    <div class="stm-batch-dialog">
      <p class="stm-batch-dialog__hint text-sm text-[var(--el-text-color-regular)]">
        检索后点击左侧列表行加入右侧「已选项目」；已在合管绑定中的项目不会出现在候选列表。
      </p>

      <div class="stm-batch-dialog__body">
        <section class="stm-batch-dialog__pane stm-batch-dialog__pane--pick">
          <SectionTitle title="可选项目" />
          <TableLayout
            :table-data="tableData"
            :loading="loading"
            :total="total"
            :query-params="queryParams"
            :form-items="formItems"
            :table-columns="pickTableColumns"
            :row-class-name="pickRowClassName"
            row-key="id"
            border
            stripe
            highlight-current-row
            :max-height="420"
            @query="handleQuery"
            @reset="handleReset"
            @pagination="handlePagination"
            @row-click="handlePickRowClick"
          >
            <template #toolbar-actions>
              <ElButton
                type="primary"
                plain
                :disabled="!total || loading"
                @click="selectAllPickRows"
              >
                全选
              </ElButton>
            </template>
          </TableLayout>
        </section>

        <section class="stm-batch-dialog__pane stm-batch-dialog__pane--selected">
          <SectionTitle :title="`已选项目（${selectedRows.length}）`" />
          <TableLayout
            :table-data="selectedRows"
            :loading="false"
            :total="selectedRows.length"
            :query-params="{ pageNo: 1, pageSize: 20 }"
            :table-columns="selectedTableColumns"
            :show-top-query="false"
            :show-pagination="false"
            :show-default-buttons="false"
            row-key="id"
            border
            stripe
            :max-height="420"
          >
            <template #rowActions="{ row }">
              <ElButton link type="danger" @click.stop="removeSelected(row)"> 移除 </ElButton>
            </template>
          </TableLayout>
        </section>
      </div>
    </div>

    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton type="primary" :disabled="selectedRows.length === 0" @click="handleConfirm">
        确定添加{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}
      </ElButton>
    </template>
  </AppDialog>
</template>

<style scoped lang="scss">
.stm-batch-dialog__hint {
  margin: 0 0 12px;
}

.stm-batch-dialog__body {
  display: flex;
  gap: 12px;
  min-height: 460px;
}

.stm-batch-dialog__pane {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}

.stm-batch-dialog__pane--pick {
  flex: 1.4;
}

.stm-batch-dialog__pane--selected {
  flex: 1;
  min-width: 320px;
}

:deep(.stm-batch-pick-row--selected) {
  background-color: var(--el-color-primary-light-9);
}
</style>
