<!--
  科室入库单右侧主区（只读壳单台账）：由上游审批同意后自动生成，禁止改单。
-->
<script setup lang="ts">
import type { VxeGridProps, VxeGridPropTypes } from '#/adapter/vxe-table';
import type { StockInDetailLineDto } from '#/api/inventory/department/stock-in';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import InvMainShell from '@inventory/components/inv-main-shell.vue';
import { ElForm, ElFormItem, ElInput, ElMessage, ElOption, ElSelect } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getStockInDetailApi } from '#/api/inventory/department/stock-in';
import SectionTitle from '#/components/layouts/section-title.vue';
import { INV_DOC_READONLY_SHELL_HINT } from '#/utils/inventory/inventory-doc-restrictions';
import { createInventoryQtyUnitReadonlyColumn } from '#/utils/inventory/inventory-doc-grid-common';
import {
  normalizePharmacyStockInStatusSort,
  pharmacyStockInStatusLabel,
} from '#/utils/inventory/pharmacy-stock-in-doc-actions';

defineOptions({ name: 'InventoryDepartmentStockInMainPane' });

interface StockInPreviewRow {
  id: string;
  productName: string;
  specification: string;
  manufacturer: string;
  itemQuantity?: number;
  unitCode: string;
  unitCodeDictText?: string;
  productCatalog?: { unitBands?: unknown; unitBandsJson?: unknown };
  lotNumber: string;
  traceNo: string;
  remark: string;
}

interface Props {
  busNo?: string;
  resetKey?: number;
  warehouseOptions?: ApiOptionItem[];
  pharmacyOptions?: ApiOptionItem[];
}

const props = withDefaults(defineProps<Props>(), {
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

function mapDetailLineToGrid(ln: StockInDetailLineDto, rowIdx: number): StockInPreviewRow {
  const catalog = (ln as { productCatalog?: { unitBands?: unknown; unitBandsJson?: unknown } })
    .productCatalog;
  return {
    id: `ln-${ln.id ?? rowIdx}`,
    productName: String(ln.productName ?? '').trim(),
    specification: String(ln.specification ?? ''),
    manufacturer: String(ln.manufacturer ?? ''),
    itemQuantity: ln.itemQuantity === undefined ? undefined : Number(ln.itemQuantity),
    unitCode: String(ln.unitCode ?? ''),
    unitCodeDictText: String(ln.unitCodeDictText ?? ln.unitCode ?? ''),
    productCatalog: catalog,
    lotNumber: String(ln.lotNumber ?? ''),
    traceNo: String(ln.traceNo ?? ''),
    remark: String(ln.remake ?? ''),
  };
}

const columns: VxeGridPropTypes.Column[] = [
  { type: 'seq', title: '序号', width: 52, fixed: 'left', align: 'center' },
  { field: 'productName', title: '项目名称', minWidth: 200, showOverflow: true },
  { field: 'specification', title: '规格', width: 120, showOverflow: true },
  { field: 'manufacturer', title: '厂家/产地', minWidth: 120, showOverflow: true },
  createInventoryQtyUnitReadonlyColumn({
    hydrateFromSplit: true,
    unitLabelField: 'unitCodeDictText',
  }),
  { field: 'lotNumber', title: '批号', width: 120, showOverflow: true },
  { field: 'traceNo', title: '追溯码', width: 140, cellRender: { name: 'TraceCodeTooltipCell' } },
  { field: 'remark', title: '备注', minWidth: 120, showOverflow: true },
];

const gridOptions: VxeGridProps<StockInPreviewRow> = reactive({
  autoResize: true,
  height: '100%',
  border: true,
  stripe: true,
  showOverflow: true,
  size: 'medium',
  data: [],
  columns,
  pagerConfig: { enabled: false },
});

const [Grid, gridApi] = useVbenVxeGrid<StockInPreviewRow>({
  gridOptions,
  class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
  gridClass: '!p-0',
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
</script>

<template>
  <div class="department-stock-in-main-pane flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
    <InvMainShell>
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
