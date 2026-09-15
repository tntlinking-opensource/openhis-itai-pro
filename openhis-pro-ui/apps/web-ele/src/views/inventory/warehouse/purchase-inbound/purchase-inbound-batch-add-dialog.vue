<!--
  药库进货单批量选品弹窗：TableLayout 展示选品列表，点击行加入已选区域。
-->
<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import { computed, ref, watch } from 'vue';

import { TableLayout } from '@vben-core/table-layout-ui';
import { ElButton, ElMessage } from 'element-plus';

import { pageProductsForInboundReceiptApi } from '#/api/inventory/warehouse/inbound-receipt';
import AppDialog from '#/components/app-dialog.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import {
  INBOUND_RECEIPT_PRODUCT_PICK_TABLE_COLUMNS,
  mapInboundReceiptProductPickListRow,
} from '#/utils/inventory/inbound-receipt-product-pick-list';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

defineOptions({ name: 'PurchaseInboundBatchAddDialog' });

interface ProductPickRow extends Record<string, unknown> {
  id?: string;
  productDefinitionId?: number | string;
  productBusNo?: string;
  productName?: string;
  manufacturer?: string;
  totalVolume?: string;
}

interface Props {
  visible: boolean;
  /** 选品接口药库门禁（与单头库房无关，由父级传入 init 首个药库位） */
  pickGateLocationId?: number | string;
  /** 入库项目类型（与单行选品 query.productTableName 一致） */
  productTableName?: string;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  pickGateLocationId: undefined,
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
    placeholder: '项目编码 / 名称 / 厂家',
  },
];

const pickTableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, align: 'center' },
  ...INBOUND_RECEIPT_PRODUCT_PICK_TABLE_COLUMNS.map((col) => ({
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
    minWidth: 140,
    showOverflowTooltip: true,
  },
  {
    prop: 'totalVolume',
    label: '规格',
    width: 120,
    showOverflowTooltip: true,
  },
  {
    prop: 'purchaseSplitRatioText',
    label: '采购最小单位换算比',
    width: 140,
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

/** 选品行唯一键（productDefinitionId / id） */
function resolveProductKey(row: Record<string, unknown>): string {
  return String(row.id ?? row.productDefinitionId ?? '').trim();
}

/** 加载可选项目列表（仅按项目类型筛选，与单头库房无关） */
async function getList() {
  const productTableName = props.productTableName?.trim();
  if (!productTableName) {
    tableData.value = [];
    total.value = 0;
    return;
  }
  const pickGateLocationId = props.pickGateLocationId;
  if (
    pickGateLocationId === undefined ||
    pickGateLocationId === null ||
    String(pickGateLocationId).trim() === ''
  ) {
    tableData.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const res = await pageProductsForInboundReceiptApi({
      purposeLocationId: pickGateLocationId,
      productTableName,
      keyword: String(queryParams.value.keyword ?? '').trim() || undefined,
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      activeFlag: true,
    });
    const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res.data);
    tableData.value = parsed.content.map(
      (r) => mapInboundReceiptProductPickListRow(r) as ProductPickRow,
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
  void getList();
}

/** 分页切换 */
function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  void getList();
}

/** 批量加入已选区域（按 id 去重） */
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
    ElMessage.warning('该项目已在待添加列表中');
    return;
  }
  appendPickRows([row as ProductPickRow]);
}

/** 全选：当前筛选条件下全部可选项目加入已选 */
async function selectAllPickRows() {
  const productTableName = props.productTableName?.trim();
  if (!productTableName) {
    return;
  }
  const pickGateLocationId = props.pickGateLocationId;
  if (
    pickGateLocationId === undefined ||
    pickGateLocationId === null ||
    String(pickGateLocationId).trim() === ''
  ) {
    return;
  }
  if (total.value === 0) {
    ElMessage.warning('暂无可选项目');
    return;
  }
  loading.value = true;
  try {
    const pageSize = Math.min(Math.max(total.value, 1), 500);
    const res = await pageProductsForInboundReceiptApi({
      purposeLocationId: pickGateLocationId,
      productTableName,
      keyword: String(queryParams.value.keyword ?? '').trim() || undefined,
      pageNo: 1,
      pageSize,
      activeFlag: true,
    });
    const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res.data);
    const allRows = parsed.content.map(
      (r) => mapInboundReceiptProductPickListRow(r) as ProductPickRow,
    );
    const added = appendPickRows(allRows);
    if (added === 0) {
      ElMessage.info('当前筛选结果均已加入待添加列表');
      return;
    }
    ElMessage.success(`已加入 ${added} 条项目`);
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
  tableData.value = [];
  selectedRows.value = [];
  total.value = 0;
}

/** 确认批量添加 */
function handleConfirm() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请至少选择一个项目');
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
    title="批量添加项目"
    width="1360px"
    destroy-on-close
    @close="handleClose"
  >
    <div class="purchase-inbound-batch-dialog">
      <p
        class="purchase-inbound-batch-dialog__hint text-sm"
        style="color: var(--el-text-color-regular)"
      >
        检索项目后，点击左侧列表行即可加入右侧「已选项目」。
      </p>

      <div class="purchase-inbound-batch-dialog__body">
        <section
          class="purchase-inbound-batch-dialog__pane purchase-inbound-batch-dialog__pane--pick"
        >
          <SectionTitle title="可选项目" />
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

        <section
          class="purchase-inbound-batch-dialog__pane purchase-inbound-batch-dialog__pane--selected"
        >
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
.purchase-inbound-batch-dialog__hint {
  margin: 0 0 12px;
}

.purchase-inbound-batch-dialog__body {
  display: flex;
  gap: 12px;
  min-height: 460px;
}

.purchase-inbound-batch-dialog__pane {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}

.purchase-inbound-batch-dialog__pane--pick {
  flex: 1.4;
}

.purchase-inbound-batch-dialog__pane--selected {
  flex: 1;
  min-width: 360px;
}
</style>
