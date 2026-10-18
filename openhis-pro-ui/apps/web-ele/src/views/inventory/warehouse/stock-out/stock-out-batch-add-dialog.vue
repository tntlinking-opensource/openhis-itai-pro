<!--
  药库出库单批量选品弹窗：TableLayout 展示选品列表，点击行加入已选区域。
-->
<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import { computed, ref, watch } from 'vue';

import { TableLayout } from '@zxplat-core/table-layout-ui';
import { ElButton, ElMessage, ElSwitch } from 'element-plus';

import { pageProductsForStockOutApi } from '#/api/inventory/warehouse/stock-out';
import AppDialog from '#/components/app-dialog.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { mapInventoryProductPickListRow } from '#/utils/inventory/inventory-product-pick-list';
import { STOCK_OUT_PRODUCT_PICK_TABLE_COLUMNS } from '#/utils/inventory/stock-out-product-pick-list';

defineOptions({ name: 'StockOutBatchAddDialog' });

interface ProductPickRow extends Record<string, unknown> {
  id?: string;
  inventoryItemId?: number | string;
  productDefinitionId?: number | string;
  productBusNo?: string;
  productName?: string;
  manufacturer?: string;
  specification?: string;
  lotNumber?: string;
}

interface Props {
  visible: boolean;
  /** 源药库（与单行选品 query.sourceLocationId 一致） */
  sourceLocationId?: number | string;
  /** 出库项目类型（与单行选品 query.productTableName 一致） */
  productTableName?: string;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  sourceLocationId: undefined,
  productTableName: '',
});

const emit = defineEmits<{
  'update:visible': [value: boolean];
  confirm: [products: ProductPickRow[]];
}>();

const queryParams = ref({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
});

/** 出库批量选品默认仅查账面量>0，用户可取消勾选 */
const positiveQuantityOnly = ref(true);

const tableData = ref<ProductPickRow[]>([]);
const selectedRows = ref<ProductPickRow[]>([]);
const loading = ref(false);
const total = ref(0);

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

const formItems: FormItemConfig[] = [
  {
    label: '关键字',
    prop: 'keyword',
    type: 'input',
    placeholder: '项目编码 / 名称 / 厂家 / 批号',
  },
];

const pickTableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, align: 'center' },
  ...STOCK_OUT_PRODUCT_PICK_TABLE_COLUMNS.map((col) => ({
    prop: col.prop,
    label: col.label,
    width: col.width,
    minWidth: col.width,
    showOverflowTooltip: true,
  })),
];

const selectedTableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, align: 'center' },
  {
    prop: 'productBusNo',
    label: '项目编码',
    width: 120,
    showOverflowTooltip: true,
  },
  {
    prop: 'productName',
    label: '项目名称',
    minWidth: 160,
    showOverflowTooltip: true,
  },
  {
    prop: 'manufacturer',
    label: '厂家/产地',
    minWidth: 120,
    showOverflowTooltip: true,
  },
  {
    prop: 'specification',
    label: '规格',
    width: 100,
    showOverflowTooltip: true,
  },
  {
    prop: 'lotNumber',
    label: '批号',
    width: 100,
    showOverflowTooltip: true,
  },
  {
    label: '操作',
    width: 72,
    align: 'center',
    fixed: 'right',
    slot: 'rowActions',
  },
];

/** 选品行唯一键（库存行 inventoryItemId / id） */
function resolveProductKey(row: Record<string, unknown>): string {
  return String(row.inventoryItemId ?? row.id ?? '').trim();
}

/** 加载可选药品列表 */
async function getList() {
  const sourceLocationId = props.sourceLocationId;
  const productTableName = String(props.productTableName ?? '').trim();
  if (
    sourceLocationId === undefined ||
    sourceLocationId === null ||
    sourceLocationId === '' ||
    !productTableName
  ) {
    tableData.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const res = await pageProductsForStockOutApi({
      sourceLocationId,
      productTableName,
      keyword: String(queryParams.value.keyword ?? '').trim() || undefined,
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      activeFlag: true,
      positiveQuantityOnly: positiveQuantityOnly.value,
    });
    const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res.data);
    tableData.value = parsed.content.map(
      (r) => mapInventoryProductPickListRow(r) as ProductPickRow,
    );
    total.value = parsed.totalCount;
  } catch {
    tableData.value = [];
    total.value = 0;
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
  positiveQuantityOnly.value = true;
  void getList();
}

/** 「库存大于0」筛选变更后重新拉列表 */
function handlePositiveQuantityOnlyChange() {
  queryParams.value.pageNo = 1;
  void getList();
}

/** 分页切换 */
function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  void getList();
}

/** 批量加入已选区域（按库存行 id 去重） */
function appendPickRows(rows: ProductPickRow[]): number {
  const seen = new Set(selectedRows.value.map((item) => resolveProductKey(item)));
  let added = 0;
  for (const row of rows) {
    const key = resolveProductKey(row);
    if (!key || seen.has(key)) continue;
    seen.add(key);
    selectedRows.value.push(row);
    added++;
  }
  return added;
}

/** 点击选品行：加入已选区域 */
function handlePickRowClick(row: Record<string, unknown>) {
  const key = resolveProductKey(row);
  if (!key) return;
  if (selectedRows.value.some((item) => resolveProductKey(item) === key)) {
    ElMessage.warning('该药品已在待添加列表中');
    return;
  }
  appendPickRows([row as ProductPickRow]);
}

/** 全选：当前筛选条件下全部可选药品加入已选 */
async function selectAllPickRows() {
  const sourceLocationId = props.sourceLocationId;
  const productTableName = String(props.productTableName ?? '').trim();
  if (
    sourceLocationId === undefined ||
    sourceLocationId === null ||
    sourceLocationId === '' ||
    !productTableName
  ) {
    return;
  }
  if (total.value === 0) {
    ElMessage.warning('暂无可选药品');
    return;
  }
  loading.value = true;
  try {
    const pageSize = Math.min(Math.max(total.value, 1), 500);
    const res = await pageProductsForStockOutApi({
      sourceLocationId,
      productTableName,
      keyword: String(queryParams.value.keyword ?? '').trim() || undefined,
      pageNo: 1,
      pageSize,
      activeFlag: true,
      positiveQuantityOnly: positiveQuantityOnly.value,
    });
    const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res.data);
    const allRows = parsed.content.map((r) => mapInventoryProductPickListRow(r) as ProductPickRow);
    const added = appendPickRows(allRows);
    if (added === 0) {
      ElMessage.info('当前筛选结果均已加入待添加列表');
      return;
    }
    ElMessage.success(`已加入 ${added} 条药品`);
  } finally {
    loading.value = false;
  }
}

/** 从已选区域移除 */
function removeSelected(row: ProductPickRow) {
  const key = resolveProductKey(row);
  selectedRows.value = selectedRows.value.filter((item) => resolveProductKey(item) !== key);
}

/** 关闭并重置 */
function handleClose() {
  dialogVisible.value = false;
  queryParams.value = { pageNo: 1, pageSize: 20, keyword: '' };
  positiveQuantityOnly.value = true;
  tableData.value = [];
  selectedRows.value = [];
  total.value = 0;
}

/** 确认批量添加 */
function handleConfirm() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请至少选择一个药品');
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
      positiveQuantityOnly.value = true;
      selectedRows.value = [];
      void getList();
    }
  },
);
</script>

<template>
  <AppDialog
    v-model="dialogVisible"
    title="批量添加药品"
    width="1360px"
    destroy-on-close
    @close="handleClose"
  >
    <div class="stock-out-batch-dialog">
      <p class="stock-out-batch-dialog__hint text-sm" style="color: var(--el-text-color-regular)">
        检索药品后，点击左侧列表行即可加入右侧「已选药品」。
      </p>

      <div class="stock-out-batch-dialog__body">
        <section class="stock-out-batch-dialog__pane stock-out-batch-dialog__pane--pick">
          <SectionTitle title="可选药品" />
          <TableLayout
            :table-data="tableData"
            :loading="loading"
            :total="total"
            :query-params="queryParams"
            :form-items="formItems"
            :table-columns="pickTableColumns"
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
              <div class="stock-out-batch-dialog__qty-filter mr-3">
                <span class="stock-out-batch-dialog__qty-filter-label">只看库存大于0</span>
                <ElSwitch
                  v-model="positiveQuantityOnly"
                  size="small"
                  @change="handlePositiveQuantityOnlyChange"
                />
              </div>
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

        <section class="stock-out-batch-dialog__pane stock-out-batch-dialog__pane--selected">
          <SectionTitle :title="`已选药品（${selectedRows.length}）`" />
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
              <ElButton link type="danger" @click.stop="removeSelected(row as ProductPickRow)">
                移除
              </ElButton>
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
.stock-out-batch-dialog__hint {
  margin: 0 0 12px;
}

.stock-out-batch-dialog__body {
  display: flex;
  gap: 12px;
  min-height: 460px;
}

.stock-out-batch-dialog__pane {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}

.stock-out-batch-dialog__pane--pick {
  flex: 1.4;
}

.stock-out-batch-dialog__pane--selected {
  flex: 1;
  min-width: 360px;
}

.stock-out-batch-dialog__qty-filter {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 4px 8px;
  border: 1px solid #d7e4f2;
  border-radius: 4px;
  background: #f6fbff;
  cursor: default;
  user-select: none;
}

.stock-out-batch-dialog__qty-filter-label {
  font-size: 12px;
  line-height: 1;
  color: var(--el-text-color-regular);
}
</style>
