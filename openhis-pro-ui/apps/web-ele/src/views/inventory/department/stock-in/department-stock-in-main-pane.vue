<!--
  科室入库单右侧主区（只读壳单台账）：由上游审批同意后自动生成，禁止改单。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import { bindInvDocCompactColumns } from '#/composables/use-inv-doc-compact-columns';
import type { StockInDetailLineDto } from '#/api/inventory/department/stock-in';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import InvDocActions from '@inventory/components/inv-doc-actions.vue';
import InvMainShell from '@inventory/components/inv-main-shell.vue';
import { ElForm, ElFormItem, ElInput, ElMessage, ElOption, ElSelect } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getStockInDetailApi } from '#/api/inventory/department/stock-in';
import SectionTitle from '#/components/layouts/section-title.vue';
import { INV_DOC_READONLY_SHELL_HINT } from '#/utils/inventory/inventory-doc-restrictions';
import { formatPurchaseMoneyDisplay } from '#/utils/format/money-scale';
import {
  createInventoryQtyUnitReadonlyColumn,
  hydrateInventoryDetailLineQtyForGrid,
  isPresentItemId,
} from '#/utils/inventory/inventory-doc-grid-common';
import {
  formatInventoryUnitPriceDecimals,
  resolveInventoryUnitPriceDisplayDp,
} from '#/utils/inventory/inventory-qty-unit-display';
import {
  normalizePharmacyStockInStatusSort,
  pharmacyStockInStatusLabel,
} from '#/utils/inventory/pharmacy-stock-in-doc-actions';
import {
  buildInvDocumentTitle,
  printInvDocument,
  resolveInvPrintProductBusNo,
} from '#/utils/print/print-inv-document';
import {
  resolveInvDocApprovedByName,
  resolveInvDocPreparedByName,
} from '#/utils/inventory/inventory-workbench-list';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

defineOptions({ name: 'InventoryDepartmentStockInMainPane' });

interface StockInPreviewRow {
  id: string;
  itemId?: number | string;
  productName: string;
  specification: string;
  manufacturer: string;
  /** 网格展示数量（已按计量带从 SPLIT 还原） */
  itemQuantity?: number;
  unitCode: string;
  unitCodeDictText?: string;
  productCatalog?: { unitBands?: unknown; unitBandsJson?: unknown };
  lotNumber: string;
  traceNo: string;
  remark: string;
  price?: number;
  /** 行总价展示（两位小数） */
  totalPriceText: string;
  totalPrice?: number;
  occurrenceTime?: string;
  startTime?: string;
  endTime?: string;
}

interface Props {
  /** 侧栏当前单据行（制单人/审批人与队列卡片同口径） */
  queueCardRow?: null | Record<string, unknown>;
  busNo?: string;
  resetKey?: number;
  warehouseOptions?: ApiOptionItem[];
  pharmacyOptions?: ApiOptionItem[];
}

const props = withDefaults(defineProps<Props>(), {
  queueCardRow: null,
  busNo: '',
  resetKey: 0,
  warehouseOptions: () => [],
  pharmacyOptions: () => [],
});

const headerBusNo = ref<string | undefined>(undefined);
const originalBusNo = ref<string | undefined>(undefined);
const sourceLocationId = ref<number | string | undefined>(undefined);
const purposeLocationId = ref<number | string | undefined>(undefined);
const detailStatusEnum = ref<number | string | undefined>(undefined);

const headerStatusText = computed(() =>
  pharmacyStockInStatusLabel(normalizePharmacyStockInStatusSort(detailStatusEnum.value)),
);

/** 只读壳：全部制单/审驳禁用，仅开放打印 */
const docActionFlags = computed(() => {
  const hasPersistedBusNo = Boolean(String(headerBusNo.value ?? '').trim());
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

/** 详情行 → 网格：先 SPLIT→展示数量，再拼只读列（勿在列 formatter 里再 hydrate，易把后续列打没） */
function mapDetailLineToGrid(ln: StockInDetailLineDto, rowIdx: number): StockInPreviewRow {
  const catalog = (
    ln as {
      productCatalog?: { unitBands?: unknown; unitBandsJson?: unknown; categoryCode?: string };
      categoryCode?: string;
    }
  ).productCatalog;
  const rawQty =
    ln.itemQuantity === undefined || ln.itemQuantity === null ? undefined : Number(ln.itemQuantity);
  const hydrated = hydrateInventoryDetailLineQtyForGrid({
    itemQuantity: rawQty,
    unitCode: String(ln.unitCode ?? ''),
    unitCodeDictText: String(ln.unitCodeDictText ?? ''),
    productCatalog: catalog,
  });
  const totalRaw =
    ln.totalPrice === undefined || ln.totalPrice === null ? undefined : Number(ln.totalPrice);
  const categoryCode =
    String(catalog?.categoryCode ?? (ln as { categoryCode?: string }).categoryCode ?? '').trim() ||
    undefined;
  return {
    id: `ln-${ln.id ?? rowIdx}`,
    itemId: ln.itemId,
    productName: String(ln.productName ?? '').trim(),
    specification: String(ln.specification ?? ''),
    manufacturer: String(ln.manufacturer ?? ''),
    itemQuantity: hydrated.displayQty,
    unitCode: hydrated.unitCode,
    unitCodeDictText: hydrated.unitCodeDictText || hydrated.unitCode,
    productCatalog: catalog,
    lotNumber: String(ln.lotNumber ?? '').trim(),
    traceNo: String(ln.traceNo ?? '').trim(),
    remark: String(ln.remake ?? ''),
    price: ln.price === undefined || ln.price === null ? undefined : Number(ln.price),
    totalPrice: totalRaw,
    totalPriceText:
      totalRaw === undefined || !Number.isFinite(totalRaw)
        ? ''
        : formatPurchaseMoneyDisplay(totalRaw, categoryCode),
    occurrenceTime: String(ln.occurrenceTime ?? '').trim(),
    startTime: String((ln as { startTime?: string }).startTime ?? '').trim(),
    endTime: String((ln as { endTime?: string }).endTime ?? '').trim(),
  };
}

const columns: VxeGridPropTypes.Column[] = [
  { type: 'seq', title: '序号', width: 52, fixed: 'left', align: 'center' },
  { field: 'productName', title: '项目名称', minWidth: 160, showOverflow: true },
  { field: 'specification', title: '规格', width: 110, showOverflow: true },
  { field: 'manufacturer', title: '厂家/产地', minWidth: 110, showOverflow: true },
  // 数量已在 map 时 hydrate，列内只做只读拼接，避免 formatter 再换算
  createInventoryQtyUnitReadonlyColumn({
    hydrateFromSplit: false,
    unitLabelField: 'unitCodeDictText',
  }),
  {
    field: 'price',
    title: '单价',
    width: 96,
    align: 'right',
    headerAlign: 'center',
    formatter: ({ row, cellValue }) => {
      const n = Number(cellValue);
      if (!Number.isFinite(n)) return '';
      return formatInventoryUnitPriceDecimals(
        n,
        resolveInventoryUnitPriceDisplayDp(row as Record<string, unknown>),
      );
    },
  },
  {
    field: 'totalPriceText',
    title: '总金额',
    width: 100,
    align: 'right',
    headerAlign: 'center',
  },
  { field: 'lotNumber', title: '批号', width: 110, showOverflow: true },
  { field: 'traceNo', title: '追溯码', width: 130, cellRender: { name: 'TraceCodeTooltipCell' } },
  { field: 'remark', title: '备注', minWidth: 100, showOverflow: true },
];

const gridOptions: VxeGridProps<StockInPreviewRow> = reactive({
  autoResize: true,
  height: '100%',
  border: true,
  stripe: true,
  showOverflow: true,
  size: 'medium',
  columnConfig: { resizable: true },
  scrollX: { enabled: true },
  data: [],
  columns,
  pagerConfig: { enabled: false },
});

const [Grid, gridApi] = useVbenVxeGrid<StockInPreviewRow>({
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

function resetDetail() {
  headerBusNo.value = undefined;
  originalBusNo.value = undefined;
  sourceLocationId.value = undefined;
  purposeLocationId.value = undefined;
  detailStatusEnum.value = undefined;
  void nextTick(() => {
    gridApi?.grid?.loadData([]);
  });
}

/** 拉取科室入库壳单详情并回显只读明细 */
async function loadDetail(busNo: string) {
  try {
    const res = await getStockInDetailApi({ busNo });
    const data = res.data;
    if (!data?.busNo) {
      ElMessage.warning('未获取到入库单详情');
      resetDetail();
      return;
    }
    headerBusNo.value = data.busNo;
    originalBusNo.value = data.originalBusNo ?? undefined;
    detailStatusEnum.value = data.statusEnum;
    sourceLocationId.value = data.sourceLocationId ?? undefined;
    purposeLocationId.value = data.purposeLocationId ?? undefined;
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
function collectGridRows(): StockInPreviewRow[] {
  const full = gridApi?.grid?.getTableData?.()?.fullData;
  return Array.isArray(full) ? (full as StockInPreviewRow[]) : [];
}

/** 按下拉 value 解析展示名 */
function resolvePrintOptionLabel(options: ApiOptionItem[] | undefined, value: unknown): string {
  const key = String(value ?? '').trim();
  if (!key) return '';
  const hit = options?.find((item) => String(item.value ?? '') === key);
  return String(hit?.label ?? '').trim();
}

/** 打印或预览科室入库单（计价流转模版） */
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

  const sourceName = resolvePrintOptionLabel(props.warehouseOptions, sourceLocationId.value);
  const destName = resolvePrintOptionLabel(props.pharmacyOptions, purposeLocationId.value);
  const documentTitle = buildInvDocumentTitle(destName || sourceName, '入库单');
  const applyTimeText = String(productRows[0]?.occurrenceTime ?? '')
    .trim()
    .replace('T', ' ');

  // 网格行已在 loadDetail 时完成 SPLIT→展示换算，打印直接取展示值
  const lineRows = productRows.map((row) => ({
    productBusNo: resolveInvPrintProductBusNo(row as unknown as Record<string, unknown>),
    productName: String(row.productName ?? '').trim(),
    specText: String(row.specification ?? '').trim(),
    manufacturerText: String(row.manufacturer ?? '').trim(),
    qty: row.itemQuantity,
    unitLabel: String(row.unitCodeDictText || row.unitCode || '').trim(),
    unitPrice: row.price,
    amount: row.totalPrice,
    lotNumber: String(row.lotNumber ?? '').trim(),
    expiryDate: String(row.endTime ?? '').trim(),
  }));

  const preparedByName = resolveInvDocPreparedByName(
    props.queueCardRow as null | Record<string, unknown>,
  );
  const approvedByName = resolveInvDocApprovedByName(
    props.queueCardRow as null | Record<string, unknown>,
  );
  await printInvDocument({
    family: 'movement',
    source: {
      hospitalName: resolvePrintHospitalName(),
      documentTitle,
      docNo: busNo,
      applyTimeText,
      itemTypeText: '',
      sourceLocationText: sourceName,
      supplierText: '',
      linkDocText: String(originalBusNo.value ?? '').trim(),
      destLocationText: destName,
      invoiceNoText: '',
      lineRows,
      preparedByName,
      approvedByName,
    },
    bizRef: { busNo, docKind: 'department-stock-in' },
    jobName: documentTitle,
    preview: payload?.preview === true,
  });
}
</script>

<template>
  <div class="department-stock-in-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
    <InvMainShell>
      <template #actionBar>
        <div class="flex w-full flex-wrap items-center justify-end gap-2">
          <InvDocActions v-bind="docActionFlags" @print="handlePrint" />
        </div>
      </template>
      <div class="flex min-h-0 flex-1 flex-col gap-2 overflow-hidden px-2 pt-2">
        <div class="shrink-0">
          <SectionTitle title="科室入库单信息" />
          <div class="mt-1 flex flex-wrap items-center gap-2">
            <span class="text-xs" style="color: var(--el-text-color-regular)">状态</span>
            <span class="text-sm" style="color: var(--el-text-color-primary)">{{
              headerStatusText || '—'
            }}</span>
          </div>
          <p class="mt-1 text-xs" style="color: var(--el-text-color-secondary)">
            {{ INV_DOC_READONLY_SHELL_HINT.stockIn }}
          </p>
          <ElForm label-width="96px" class="compact-head-form mt-1">
            <div
              class="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
            >
              <ElFormItem label="单据号">
                <ElInput :model-value="headerBusNo ?? ''" disabled placeholder="—" />
              </ElFormItem>
              <ElFormItem label="源药库">
                <ElSelect
                  v-model="sourceLocationId"
                  class="w-full"
                  filterable
                  disabled
                  placeholder="—"
                >
                  <ElOption
                    v-for="w in warehouseOptions"
                    :key="String(w.value)"
                    :label="w.label"
                    :value="w.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="科室药房">
                <ElSelect
                  v-model="purposeLocationId"
                  class="w-full"
                  filterable
                  disabled
                  placeholder="—"
                >
                  <ElOption
                    v-for="p in pharmacyOptions"
                    :key="String(p.value)"
                    :label="p.label"
                    :value="p.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="关联单号">
                <ElInput :model-value="originalBusNo ?? ''" disabled placeholder="—" />
              </ElFormItem>
            </div>
          </ElForm>
        </div>
        <Grid class="min-h-0 flex-1" />
      </div>
    </InvMainShell>
  </div>
</template>

<style scoped lang="scss">
.compact-head-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.compact-head-form :deep(.el-form-item__label) {
  padding-right: 8px;
}
</style>
