<!--
  药库退货入库单右侧主区（只读壳单台账）：由药房/科室退库 agree 自动生成，禁止改单。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type {
  LocationOptionDto,
  ReturnStockInDetailLineDto,
} from '#/api/inventory/warehouse/return-stock-in';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { formatDateTime } from '@vben/utils';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
import {
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElOptionGroup,
  ElSelect,
  ElTag,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getReturnStockInDetailApi } from '#/api/inventory/warehouse/return-stock-in';
import { bindInvDocCompactColumns } from '#/composables/use-inv-doc-compact-columns';
import SectionTitle from '#/components/layouts/section-title.vue';
import {
  applyHydratedInventoryDetailQtyToRow,
  createInventoryQtyUnitReadonlyColumn,
  hydrateInventoryDetailLineQtyForGrid,
  isPresentItemId,
} from '#/utils/inventory/inventory-doc-grid-common';
import { INV_DOC_READONLY_SHELL_HINT } from '#/utils/inventory/inventory-doc-restrictions';
import {
  resolveInvDocApprovedByName,
  resolveInvDocPreparedByName,
  resolveInvDocPrintApplyTimeText,
} from '#/utils/inventory/inventory-workbench-list';
import {
  normalizeStockOutStatusSort,
  stockOutStatusLabel,
} from '#/utils/inventory/stock-out-doc-actions';
import {
  buildInvDocumentTitle,
  printInvDocument,
  resolveInvPrintProductBusNo,
} from '#/utils/print/print-inv-document';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

defineOptions({ name: 'InventoryWarehouseReturnStockInMainPane' });

interface ReturnPreviewRow {
  id: string;
  itemId?: number | string;
  productName: string;
  itemQuantity?: number;
  unitCode: string;
  unitCodeDictText?: string;
  lotNumber: string;
  traceNo: string;
  reason: string;
  remake: string;
  /** 打印取编码用 */
  medicinePick?: Record<string, unknown>;
}

interface Props {
  /** 侧栏当前单据行（制单人/审批人与队列卡片同口径） */
  queueCardRow?: null | Record<string, unknown>;
  busNo?: string;
  isDraftOrder?: boolean;
  /** 侧栏草稿稳定 id（壳单无草稿，保留 prop 兼容父页） */
  clientDraftId?: string;
  resetKey?: number;
  sidebarPurposeLocationId?: number | string;
  warehouseOptions: LocationOptionDto[];
  departmentOptions: LocationOptionDto[];
  pharmacyOptions: LocationOptionDto[];
  applyTime?: string;
}

const props = withDefaults(defineProps<Props>(), {
  queueCardRow: null,
  busNo: '',
  isDraftOrder: false,
  clientDraftId: '',
  resetKey: 0,
  sidebarPurposeLocationId: undefined,
  warehouseOptions: () => [],
  departmentOptions: () => [],
  pharmacyOptions: () => [],
  applyTime: undefined,
});

const headerBusNo = ref<string | undefined>(undefined);
const originalBusNo = ref<string | undefined>(undefined);
const sourceLocationId = ref<number | string | undefined>(undefined);
const purposeLocationId = ref<number | string | undefined>(undefined);
const detailStatusEnum = ref<number | undefined>(undefined);
const headApplyTime = ref(formatDateTime(new Date()));

const headerStatusText = computed(() => {
  if (!headerBusNo.value) return '—';
  return stockOutStatusLabel(detailStatusEnum.value);
});

/** 只读壳：全部制单/审驳禁用，仅开放打印 */
const docActionFlags = computed(() => {
  const hasPersistedBusNo = Boolean(headerBusNo.value);
  return {
    saveDisabled: true,
    saveAndSubmitDisabled: true,
    removeDisabled: true,
    submitApprovalDisabled: true,
    withdrawDisabled: true,
    approveDisabled: true,
    rejectDisabled: true,
    printDisabled: !hasPersistedBusNo,
  };
});

/** 详情行展示名：行上 productName 为空或非字符串时取 catalog.merchandiseName */
function resolveReturnStockInLineProductName(ln: ReturnStockInDetailLineDto): string {
  const raw = ln.productName as unknown;
  if (typeof raw === 'string') {
    const top = raw.trim();
    if (top) return top;
  }
  const catalog = ln.productCatalog;
  const fromMerchandise = catalog?.merchandiseName;
  if (typeof fromMerchandise === 'string' && fromMerchandise.trim()) {
    return fromMerchandise.trim();
  }
  const fromCatalogName = catalog?.productName;
  if (typeof fromCatalogName === 'string' && fromCatalogName.trim()) {
    return fromCatalogName.trim();
  }
  return '';
}

/** 详情行 → 网格只读行（含 SPLIT→展示数量） */
function mapDetailLineToGrid(ln: ReturnStockInDetailLineDto, rowIdx: number): ReturnPreviewRow {
  const itemIdRaw = ln.itemId;
  const idStr =
    ln.id !== undefined && ln.id !== null
      ? `ln-${String(ln.id)}`
      : itemIdRaw !== undefined && itemIdRaw !== null
        ? `ln-item-${String(itemIdRaw)}-${rowIdx}`
        : `ln-x-${rowIdx}`;
  const name = resolveReturnStockInLineProductName(ln);
  const productBusNo = String(ln.productCatalog?.productBusNo ?? '').trim();
  const row: ReturnPreviewRow = {
    id: idStr,
    medicinePick: name
      ? {
          id: itemIdRaw as number | string | undefined,
          name,
          ...(productBusNo ? { busNo: productBusNo } : {}),
        }
      : undefined,
    itemId: itemIdRaw,
    productName: name,
    itemQuantity: ln.itemQuantity === undefined ? undefined : Number(ln.itemQuantity),
    unitCode: String(ln.unitCode ?? ''),
    unitCodeDictText: String(ln.unitCodeDictText ?? '').trim() || undefined,
    lotNumber: String(ln.lotNumber ?? ''),
    traceNo: String(ln.traceNo ?? ''),
    reason: String(ln.reason ?? ''),
    remake: String(ln.remake ?? ''),
  };
  const hydrated = hydrateInventoryDetailLineQtyForGrid({
    itemQuantity: row.itemQuantity,
    unitCode: row.unitCode,
    unitCodeDictText: row.unitCodeDictText,
    productCatalog: ln.productCatalog,
  });
  applyHydratedInventoryDetailQtyToRow(row, hydrated);
  return row;
}

const columns: VxeGridPropTypes.Column[] = [
  { type: 'seq', title: '序号', width: 52, fixed: 'left', align: 'center' },
  { field: 'productName', title: '项目名称', minWidth: 200, fixed: 'left', showOverflow: true },
  createInventoryQtyUnitReadonlyColumn({
    hydrateFromSplit: false,
    unitLabelField: 'unitCodeDictText',
  }),
  { field: 'lotNumber', title: '批号', width: 120, showOverflow: true },
  { field: 'traceNo', title: '追溯码', minWidth: 140, showOverflow: true },
  { field: 'reason', title: '退货原因', minWidth: 120, showOverflow: true },
  { field: 'remake', title: '备注', minWidth: 120, showOverflow: true },
];

const gridOptions: VxeGridProps<ReturnPreviewRow> = reactive({
  autoResize: true,
  height: '100%',
  border: true,
  stripe: true,
  showOverflow: true,
  size: 'medium',
  columnConfig: { resizable: true },
  data: [],
  columns,
  pagerConfig: { enabled: false },
});

const [Grid, gridApi] = useVbenVxeGrid<ReturnPreviewRow>({
  gridOptions,
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
});
bindInvDocCompactColumns({
  baseColumns: columns,
  gridOptions,
  getGrid: () =>
    gridApi?.grid as { loadColumn?: (cols: VxeGridPropTypes.Column[]) => void } | null | undefined,
});

/** 清空主区表头与明细 */
function resetDetail() {
  headerBusNo.value = undefined;
  originalBusNo.value = undefined;
  detailStatusEnum.value = undefined;
  sourceLocationId.value = undefined;
  purposeLocationId.value = undefined;
  headApplyTime.value = formatDateTime(new Date());
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
  });
}

/** 拉取退货入库壳单详情并回显只读明细 */
async function loadDetail(busNo: string) {
  try {
    const res = await getReturnStockInDetailApi({ busNo });
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到退货入库单详情');
      resetDetail();
      return;
    }
    headerBusNo.value = data.busNo;
    originalBusNo.value = data.originalBusNo ?? undefined;
    detailStatusEnum.value = normalizeStockOutStatusSort(data.statusEnum);
    sourceLocationId.value = data.sourceLocationId ?? undefined;
    purposeLocationId.value = data.purposeLocationId ?? undefined;

    // 制单时间取明细首条 occurrenceTime，缺省用当前时刻占位
    let headOt = '';
    for (const ln of data.lines ?? []) {
      const o = ln.occurrenceTime;
      if (o !== undefined && o !== null && String(o).trim() !== '') {
        headOt = String(o).trim();
        break;
      }
    }
    headApplyTime.value = headOt
      ? headOt.includes('T')
        ? headOt.replace('T', ' ')
        : headOt
      : formatDateTime(new Date());

    const gridRows = (data.lines ?? []).map((ln, i) => mapDetailLineToGrid(ln, i));
    void nextTick(() => {
      gridApi?.grid?.loadData(gridRows);
    });
  } catch {
    resetDetail();
  }
}

watch(
  () => [props.busNo, props.resetKey] as const,
  ([no]) => {
    const bn = String(no ?? '').trim();
    if (bn) {
      void loadDetail(bn);
    } else {
      resetDetail();
    }
  },
  { immediate: true },
);

/** 读取当前网格明细行 */
function collectGridRows(): ReturnPreviewRow[] {
  const g = gridApi?.grid as null | { getFullData?: () => ReturnPreviewRow[] };
  return g?.getFullData?.() ?? [];
}

/** 解析库位展示名 */
function resolvePrintLocationName(
  options: { locationId?: number | string; displayName?: string }[] | undefined,
  value: unknown,
): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = (options ?? []).find((row) => String(row.locationId ?? '') === key);
  return String(hit?.displayName ?? hit?.locationId ?? '').trim();
}

/** 打印或预览退货入库单 */
async function handlePrint(payload?: { preview?: boolean }) {
  const busNo = String(headerBusNo.value ?? '').trim();
  if (!busNo) {
    ElMessage.warning('请先选择单据后再打印');
    return;
  }
  const productRows = collectGridRows().filter((row) => isPresentItemId(row.itemId));
  if (productRows.length === 0) {
    ElMessage.warning('没有可打印的明细');
    return;
  }

  const sourceName = resolvePrintLocationName(
    [...(props.pharmacyOptions ?? []), ...(props.departmentOptions ?? [])],
    sourceLocationId.value,
  );
  const warehouseName = resolvePrintLocationName(props.warehouseOptions, purposeLocationId.value);
  const documentTitle = buildInvDocumentTitle(warehouseName || sourceName, '退货入库单');
  // 打印制单时间取侧栏单据实际审批时间，禁止用单头缺省「当前时刻」冒充
  const applyTimeText = resolveInvDocPrintApplyTimeText({
    queueCardRow: props.queueCardRow as null | Record<string, unknown>,
  });

  // 网格行已在 loadDetail 时完成 SPLIT→展示换算，打印直接取展示值
  const lineRows = productRows.map((row) => ({
    productBusNo: resolveInvPrintProductBusNo(row as unknown as Record<string, unknown>),
    productName: String(row.productName ?? '').trim(),
    specText: String((row as { specification?: string }).specification ?? '').trim(),
    manufacturerText: String((row as { manufacturer?: string }).manufacturer ?? '').trim(),
    qty: row.itemQuantity,
    unitLabel: String(row.unitCodeDictText ?? row.unitCode ?? '').trim(),
    lotNumber: String(row.lotNumber ?? '').trim(),
    reasonText: String(row.reason ?? '').trim(),
  }));

  const preparedByName = resolveInvDocPreparedByName(
    props.queueCardRow as null | Record<string, unknown>,
  );
  const approvedByName = resolveInvDocApprovedByName(
    props.queueCardRow as null | Record<string, unknown>,
  );
  await printInvDocument({
    family: 'return',
    source: {
      hospitalName: resolvePrintHospitalName(),
      documentTitle,
      docNo: busNo,
      applyTimeText,
      itemTypeText: '',
      sourceLocationText: sourceName,
      supplierText: '',
      linkDocText: String(originalBusNo.value ?? '').trim(),
      destLocationText: warehouseName,
      invoiceNoText: '',
      lineRows,
      preparedByName,
      approvedByName,
    },
    bizRef: { busNo, docKind: 'return-stock-in' },
    jobName: documentTitle,
    preview: payload?.preview === true,
  });
}
</script>

<template>
  <div class="return-stock-in-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
    <InvMainShell>
      <template #actionBar>
        <div class="flex w-full flex-wrap items-center justify-end gap-2">
          <InvDocActions v-bind="docActionFlags" @print="handlePrint" />
        </div>
      </template>

      <div class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-2 pt-2">
        <div class="return-stock-in-head-panel shrink-0">
          <div
            class="return-stock-in-head-panel__bar flex flex-wrap items-center justify-between gap-x-4 gap-y-2"
          >
            <div>
              <SectionTitle title="退货入库单信息" />
              <p class="mt-1 text-xs" style="color: var(--el-text-color-secondary)">
                {{ INV_DOC_READONLY_SHELL_HINT.returnStockIn }}
              </p>
            </div>
            <div class="flex flex-wrap items-center gap-2">
              <span class="text-xs text-[var(--el-text-color-secondary)]">状态</span>
              <ElTag size="small" type="info" effect="plain">{{ headerStatusText || '—' }}</ElTag>
              <span
                v-if="headerBusNo"
                class="text-xs"
                style="color: var(--el-text-color-secondary)"
              >
                单号 {{ headerBusNo }}
              </span>
            </div>
          </div>
          <ElForm label-width="96px" class="compact-head-form">
            <div
              class="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
            >
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="—" />
              </ElFormItem>
              <ElFormItem label="退货来源">
                <ElSelect
                  v-model="sourceLocationId"
                  placeholder="—"
                  class="w-full"
                  filterable
                  disabled
                >
                  <ElOptionGroup v-if="departmentOptions.length > 0" label="科室">
                    <ElOption
                      v-for="d in departmentOptions"
                      :key="`dept-${d.locationId}`"
                      :label="d.displayName ?? String(d.locationId)"
                      :value="d.locationId"
                    />
                  </ElOptionGroup>
                  <ElOptionGroup v-if="pharmacyOptions.length > 0" label="药房">
                    <ElOption
                      v-for="p in pharmacyOptions"
                      :key="`pha-${p.locationId}`"
                      :label="p.displayName ?? String(p.locationId)"
                      :value="p.locationId"
                    />
                  </ElOptionGroup>
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="接收药库">
                <ElSelect
                  v-model="purposeLocationId"
                  placeholder="—"
                  class="w-full"
                  filterable
                  disabled
                >
                  <ElOption
                    v-for="w in warehouseOptions"
                    :key="String(w.locationId)"
                    :label="w.displayName ?? String(w.locationId)"
                    :value="w.locationId"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="制单时间">
                <ElDatePicker
                  v-model="headApplyTime"
                  class="w-full"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  format="YYYY-MM-DD HH:mm:ss"
                  placeholder="—"
                  disabled
                />
              </ElFormItem>
              <ElFormItem label="关联退库单" class="sm:col-span-2 lg:col-span-2">
                <ElInput :model-value="originalBusNo ?? ''" disabled placeholder="—" />
              </ElFormItem>
            </div>
          </ElForm>
        </div>

        <div class="return-stock-in-main-pane__grid-wrap flex min-h-0 min-w-0 flex-1 flex-col">
          <Grid class="min-h-0 flex-1" />
        </div>
      </div>
    </InvMainShell>
  </div>
</template>

<style scoped lang="scss">
.return-stock-in-head-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 12px 4px;
  background: var(--el-bg-color);
}

.return-stock-in-main-pane__grid-wrap :deep(.vxe-grid) {
  min-height: 200px;
}
</style>
