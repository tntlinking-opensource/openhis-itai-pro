<!--
  药库订货单预警快捷选品：样式对齐库存明细（ReportQueryBar + ReportElTable）；
  左侧预警列表，右侧纵向已选清单（非表格）。
-->
<script setup lang="ts">
import type { PurchaseWarningFilter } from '#/api/inventory/warehouse/purchase-order';

import { computed, reactive, ref, watch } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  pagePurchaseWarningProductsApi,
  type PurchaseWarningProductRowDto,
} from '#/api/inventory/warehouse/purchase-order';
import AppDialog from '#/components/app-dialog.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  REPORT_QUERY_CONTROL,
  REPORT_TABLE_NUM_CELL,
  ReportElTable,
  ReportListToolbar,
  ReportPageFooter,
  ReportQueryBar,
} from '#/views/report/shared';

defineOptions({ name: 'PurchaseOrderWarningProductsDialog' });

interface WarningPickRow extends PurchaseWarningProductRowDto {
  id?: string;
}

interface Props {
  visible: boolean;
  /** 单头库房（订货目的药库门禁） */
  purposeLocationId?: number | string;
  /** 单头项目类型对应物品表名 */
  productTableName?: string;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  purposeLocationId: undefined,
  productTableName: '',
});

const emit = defineEmits<{
  'update:visible': [value: boolean];
  confirm: [productDefinitionIds: string[]];
}>();

const queryParams = reactive({
  pageNo: 1,
  pageSize: 15,
  keyword: '',
  warningFilter: 'all' as PurchaseWarningFilter,
});

const tableData = ref<WarningPickRow[]>([]);
const selectedRows = ref<WarningPickRow[]>([]);
const loading = ref(false);
const total = ref(0);

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

const selectedKeySet = computed(() => {
  const keys = new Set<string>();
  for (const row of selectedRows.value) {
    const key = resolveProductKey(row);
    if (key) keys.add(key);
  }
  return keys;
});

const warningFilterOptions = [
  { label: '全部预警', value: 'all' },
  { label: '效期预警', value: 'expire' },
  { label: '库存预警', value: 'lowStock' },
];

/** 预警行唯一键（产品定义主键，保持 string） */
function resolveProductKey(row: PurchaseWarningProductRowDto): string {
  return preserveApiIdForRequest(row.productDefinitionId) ?? '';
}

/** 将接口行规范为弹窗行 */
function mapWarningRow(row: PurchaseWarningProductRowDto): WarningPickRow {
  const id = resolveProductKey(row);
  return { ...row, id };
}

/** 展示名：名称优先，空则回落编码 */
function resolveDisplayName(row: WarningPickRow): string {
  const name = String(row.productName ?? '').trim();
  if (name) return name;
  return String(row.productBusNo ?? row.id ?? '').trim() || '未命名项目';
}

/** 门禁：须有项目类型与具体库房 */
function hasQueryGate(): boolean {
  const productTableName = props.productTableName?.trim();
  const purposeLocationId = props.purposeLocationId;
  if (!productTableName) return false;
  if (
    purposeLocationId === undefined ||
    purposeLocationId === null ||
    String(purposeLocationId).trim() === '' ||
    String(purposeLocationId).trim() === '0'
  ) {
    return false;
  }
  return true;
}

/** 已选行高亮 */
function resolveRowClassName({ row }: { row: WarningPickRow }): string {
  const key = resolveProductKey(row);
  return key && selectedKeySet.value.has(key) ? 'po-warning-row--selected' : '';
}

/** 加载效期/库存预警品种列表 */
async function getList() {
  if (!hasQueryGate()) {
    tableData.value = [];
    total.value = 0;
    return;
  }
  const locationId = preserveApiIdForRequest(props.purposeLocationId);
  const productTableName = props.productTableName?.trim();
  if (!locationId || !productTableName) {
    tableData.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const res = await pagePurchaseWarningProductsApi({
      purposeLocationId: locationId,
      productTableName,
      warningFilter: queryParams.warningFilter,
      keyword: String(queryParams.keyword ?? '').trim() || undefined,
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
    });
    const parsed = parsePagedRowsFromApiData<PurchaseWarningProductRowDto>(res.data);
    tableData.value = parsed.content.map((r) => mapWarningRow(r));
    total.value = parsed.totalCount;
  } catch {
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

/** 搜索 */
function handleQuery() {
  const nextFilter = String(queryParams.warningFilter ?? 'all').trim() as PurchaseWarningFilter;
  queryParams.warningFilter =
    nextFilter === 'expire' || nextFilter === 'lowStock' || nextFilter === 'all'
      ? nextFilter
      : 'all';
  queryParams.pageNo = 1;
  void getList();
}

/** 重置（默认全部预警） */
function handleReset() {
  queryParams.pageNo = 1;
  queryParams.pageSize = 15;
  queryParams.keyword = '';
  queryParams.warningFilter = 'all';
  void getList();
}

/** 分页条数变更 */
function handlePageSizeChange(size: number) {
  queryParams.pageSize = size;
  queryParams.pageNo = 1;
  void getList();
}

/** 分页页码变更 */
function handleCurrentPageChange(page: number) {
  queryParams.pageNo = page;
  void getList();
}

/** 批量加入已选（按产品定义 id 去重） */
function appendPickRows(rows: WarningPickRow[]): number {
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

/** 点击行：已选则移除，未选则加入 */
function handleRowClick(row: WarningPickRow) {
  const key = resolveProductKey(row);
  if (!key) return;
  if (selectedKeySet.value.has(key)) {
    selectedRows.value = selectedRows.value.filter((item) => resolveProductKey(item) !== key);
    return;
  }
  appendPickRows([row]);
}

/** 全选：当前筛选条件下全部预警品种加入已选 */
async function selectAllPickRows() {
  if (!hasQueryGate()) return;
  if (total.value === 0) {
    ElMessage.warning('暂无预警项目');
    return;
  }
  const locationId = preserveApiIdForRequest(props.purposeLocationId);
  const productTableName = props.productTableName?.trim();
  if (!locationId || !productTableName) return;
  loading.value = true;
  try {
    const pageSize = Math.min(Math.max(total.value, 1), 500);
    const res = await pagePurchaseWarningProductsApi({
      purposeLocationId: locationId,
      productTableName,
      warningFilter: queryParams.warningFilter,
      keyword: String(queryParams.keyword ?? '').trim() || undefined,
      pageNo: 1,
      pageSize,
    });
    const parsed = parsePagedRowsFromApiData<PurchaseWarningProductRowDto>(res.data);
    const allRows = parsed.content.map((r) => mapWarningRow(r));
    const added = appendPickRows(allRows);
    if (added === 0) {
      ElMessage.info('当前筛选结果均已加入已选');
      return;
    }
    ElMessage.success(`已加入 ${added} 条项目`);
  } finally {
    loading.value = false;
  }
}

/** 从已选移除 */
function removeSelected(row: WarningPickRow) {
  const key = resolveProductKey(row);
  selectedRows.value = selectedRows.value.filter((item) => resolveProductKey(item) !== key);
}

/** 清空已选 */
function clearSelected() {
  selectedRows.value = [];
}

/** 关闭并重置 */
function handleClose() {
  dialogVisible.value = false;
  queryParams.pageNo = 1;
  queryParams.pageSize = 15;
  queryParams.keyword = '';
  queryParams.warningFilter = 'all';
  tableData.value = [];
  selectedRows.value = [];
  total.value = 0;
}

/** 确认返回已选产品定义 id（string，避免 Long 精度丢失） */
function handleConfirm() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请至少选择一个项目');
    return;
  }
  const ids: string[] = [];
  const seen = new Set<string>();
  for (const row of selectedRows.value) {
    const id = resolveProductKey(row);
    if (!id || seen.has(id)) continue;
    seen.add(id);
    ids.push(id);
  }
  if (ids.length === 0) {
    ElMessage.warning('请至少选择一个项目');
    return;
  }
  emit('confirm', ids);
  handleClose();
}

watch(
  () => props.visible,
  (open) => {
    if (open) {
      queryParams.pageNo = 1;
      queryParams.pageSize = 15;
      queryParams.keyword = '';
      queryParams.warningFilter = 'all';
      selectedRows.value = [];
      void getList();
    }
  },
);
</script>

<template>
  <AppDialog
    v-model="dialogVisible"
    title="预警药品"
    width="1480px"
    destroy-on-close
    @close="handleClose"
  >
    <div class="po-warning-dialog report-list-page">
      <ReportQueryBar :model="queryParams as Record<string, unknown>" label-width="72px">
        <template #fields>
          <ElFormItem label="预警类型">
            <ElSelect
              v-model="queryParams.warningFilter"
              :class="REPORT_QUERY_CONTROL"
              @change="handleQuery"
            >
              <ElOption
                v-for="item in warningFilterOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="关键字">
            <ElInput
              v-model="queryParams.keyword"
              clearable
              placeholder="名称、编码、拼音"
              :class="REPORT_QUERY_CONTROL"
              @keyup.enter="handleQuery"
              @clear="handleQuery"
            />
          </ElFormItem>
        </template>
        <template #actions>
          <ElButton type="primary" :icon="Search" :loading="loading" @click="handleQuery">
            搜索
          </ElButton>
          <ElButton :icon="Refresh" :loading="loading" @click="handleReset">重置</ElButton>
        </template>
      </ReportQueryBar>

      <div class="po-warning-dialog__body">
        <section class="po-warning-dialog__main">
          <ReportListToolbar title="预警项目" :total="total">
            <template #actions>
              <ElButton
                type="primary"
                plain
                :disabled="!total || loading"
                @click="selectAllPickRows"
              >
                全选当前筛选
              </ElButton>
            </template>
          </ReportListToolbar>

          <ReportElTable
            class="po-warning-dialog__table"
            :loading="loading"
            :data="tableData"
            height="520"
            row-key="id"
            :row-class-name="resolveRowClassName"
            highlight-current-row
            @row-click="handleRowClick"
          >
            <ElTableColumn type="index" label="序号" width="56" align="center" fixed="left" />
            <ElTableColumn
              prop="productBusNo"
              label="编码"
              width="110"
              align="center"
              fixed="left"
              show-overflow-tooltip
            />
            <ElTableColumn
              prop="productName"
              label="项目名称"
              min-width="168"
              align="center"
              fixed="left"
              show-overflow-tooltip
            />
            <ElTableColumn
              prop="specification"
              label="规格"
              min-width="120"
              align="center"
              show-overflow-tooltip
            />
            <ElTableColumn label="预警" width="120" align="center">
              <template #default="{ row }">
                <div class="flex flex-wrap items-center justify-center gap-1">
                  <ElTag v-if="(row as WarningPickRow).hitExpire" type="warning" size="small">
                    效期
                  </ElTag>
                  <ElTag v-if="(row as WarningPickRow).hitLowStock" type="danger" size="small">
                    低储
                  </ElTag>
                  <span
                    v-if="
                      !(row as WarningPickRow).hitExpire && !(row as WarningPickRow).hitLowStock
                    "
                    style="color: var(--el-text-color-regular)"
                  >
                    —
                  </span>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="latestExpirationDate"
              label="最晚效期"
              width="110"
              align="center"
            />
            <ElTableColumn
              prop="remainingDays"
              label="剩余天数"
              width="96"
              align="right"
              :class-name="REPORT_TABLE_NUM_CELL"
            />
            <ElTableColumn
              prop="hospitalWidePackageQty"
              label="全院包装量"
              width="110"
              align="right"
              :class-name="REPORT_TABLE_NUM_CELL"
            />
          </ReportElTable>

          <ReportPageFooter
            :current-page="queryParams.pageNo"
            :page-size="queryParams.pageSize"
            :page-sizes="[15, 20, 50, 100]"
            :total="total"
            @size-change="handlePageSizeChange"
            @current-change="handleCurrentPageChange"
          />
        </section>

        <aside class="po-warning-dialog__aside">
          <div class="po-warning-dialog__aside-head">
            <span class="po-warning-dialog__aside-title">
              已选
              <span class="po-warning-dialog__aside-count">{{ selectedRows.length }}</span>
            </span>
            <ElButton
              link
              type="danger"
              :disabled="selectedRows.length === 0"
              @click="clearSelected"
            >
              清空
            </ElButton>
          </div>
          <div class="po-warning-dialog__aside-list">
            <div
              v-for="row in selectedRows"
              :key="resolveProductKey(row)"
              class="po-warning-dialog__aside-item"
            >
              <div class="po-warning-dialog__aside-item-main">
                <div
                  class="po-warning-dialog__aside-item-name"
                  style="color: var(--el-text-color-primary)"
                >
                  {{ resolveDisplayName(row) }}
                </div>
                <div
                  class="po-warning-dialog__aside-item-meta"
                  style="color: var(--el-text-color-regular)"
                >
                  {{ row.productBusNo || '—' }}
                  <template v-if="row.specification"> · {{ row.specification }}</template>
                </div>
              </div>
              <ElButton link type="danger" @click.stop="removeSelected(row)">移除</ElButton>
            </div>
            <div
              v-if="selectedRows.length === 0"
              class="po-warning-dialog__aside-empty"
              style="color: var(--el-text-color-regular)"
            >
              点击左侧行加入已选
            </div>
          </div>
        </aside>
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
.po-warning-dialog {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
}

.po-warning-dialog__body {
  display: flex;
  gap: 12px;
  min-height: 0;
  align-items: stretch;
}

.po-warning-dialog__main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}

.po-warning-dialog__table {
  width: 100%;

  :deep(.po-warning-row--selected) {
    --el-table-tr-bg-color: var(--el-color-primary-light-9);

    td.el-table__cell {
      background: var(--el-color-primary-light-9) !important;
    }

    &.el-table__row--striped td.el-table__cell,
    td.el-table-fixed-column--left,
    td.el-table-fixed-column--right {
      background: var(--el-color-primary-light-9) !important;
    }
  }
}

.po-warning-dialog__aside {
  display: flex;
  width: 280px;
  flex-shrink: 0;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-blank);
  overflow: hidden;
}

.po-warning-dialog__aside-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}

.po-warning-dialog__aside-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.po-warning-dialog__aside-count {
  margin-left: 4px;
  color: var(--el-color-primary);
}

.po-warning-dialog__aside-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-height: 560px;
  max-height: 640px;
  padding: 8px;
  overflow: auto;
}

.po-warning-dialog__aside-item {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
}

.po-warning-dialog__aside-item-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.po-warning-dialog__aside-item-name {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.3;
  word-break: break-all;
}

.po-warning-dialog__aside-item-meta {
  font-size: 12px;
  line-height: 1.3;
  word-break: break-all;
}

.po-warning-dialog__aside-empty {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: 24px 12px;
  font-size: 13px;
  text-align: center;
}
</style>
