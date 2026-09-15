<!--
  现库行本库仍在库追溯码列表（只读）。
  本库子表无净量时回落同品同批全院在院码（期初/未扫流转常见）。
-->
<script setup lang="ts">
import type { InventoryItemTraceNumberRowDto } from '#/api/inventory/warehouse/inventory-detail';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { ElMessage, ElTable, ElTableColumn } from 'element-plus';

import {
  INVENTORY_DETAIL_ITEM_TABLE_MEDICATION,
  listInStockTraceNumbersByInventoryItemApi,
} from '#/api/inventory/warehouse/inventory-detail';
import {
  TRACE_NUMBER_REPORT_ROUTE_PATH,
  TRACE_QUERY_SCOPE_LOCATION,
} from '#/api/report/trace-number-report';
import AppDialog from '#/components/app-dialog.vue';
import TraceCodeTooltip from '#/components/trace/trace-code-tooltip.vue';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'TraceCodeListDialog' });

const router = useRouter();

const visible = defineModel<boolean>({ default: false });

const title = ref('本库追溯码');
const loading = ref(false);
const rows = ref<InventoryItemTraceNumberRowDto[]>([]);
/** 当前库存行库房，供跳转追溯码查询报表 deep link 使用。 */
const contextLocationId = ref('');

/** 按库存行四元组加载本库仍在库追溯码（本期仅药品）。 */
async function loadForInventoryRow(params: {
  locationId: string;
  itemId: string;
  lotNumber: string;
  productName?: string;
  /** 定义表名；缺省按药品；非药品直接拒绝，不打接口 */
  itemTable?: string;
}): Promise<void> {
  const locationId = preserveApiIdForRequest(params.locationId);
  const itemId = preserveApiIdForRequest(params.itemId);
  const lotNumber = String(params.lotNumber ?? '').trim();
  // 后端契约：itemTable 须为 med_medication_definition（禁止误传 medication 等别名）
  const itemTable = String(params.itemTable ?? '').trim() || INVENTORY_DETAIL_ITEM_TABLE_MEDICATION;
  if (!locationId || !itemId || !lotNumber) {
    ElMessage.warning('库存行信息不完整，无法查询追溯码');
    return;
  }
  if (itemTable !== INVENTORY_DETAIL_ITEM_TABLE_MEDICATION) {
    ElMessage.warning('本期仅支持药品追溯码');
    return;
  }
  title.value = params.productName ? `本库追溯码 · ${params.productName}` : '本库追溯码';
  contextLocationId.value = String(locationId ?? '');
  loading.value = true;
  rows.value = [];
  visible.value = true;
  try {
    // 本库优先；空则回落同品同批全院在院码（对齐发药库取 / BR-TRC-022）
    const res = await listInStockTraceNumbersByInventoryItemApi({
      locationId,
      itemId,
      lotNumber,
      itemTable,
      hospitalWideFallbackWhenEmpty: true,
    });
    rows.value = res.items ?? [];
  } catch {
    ElMessage.error('追溯码列表加载失败');
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

/** 跳转追溯码查询报表并打开该码详情。 */
function navigateToTraceReport(traceNo?: string) {
  const code = String(traceNo ?? '').trim();
  if (!code) {
    return;
  }
  visible.value = false;
  void router.push({
    path: TRACE_NUMBER_REPORT_ROUTE_PATH,
    query: {
      traceNo: code,
      openDetail: 'true',
      queryScope: TRACE_QUERY_SCOPE_LOCATION,
      ...(contextLocationId.value ? { locationId: contextLocationId.value } : {}),
    },
  });
}

defineExpose({ loadForInventoryRow });
</script>

<template>
  <AppDialog v-model="visible" :title="title" width="640px" destroy-on-close>
    <ElTable
      v-loading="loading"
      :data="rows"
      border
      size="small"
      max-height="360"
      empty-text="本库无在库码，且同品同批无全院在院码"
    >
      <ElTableColumn prop="traceNo" label="追溯码" min-width="180">
        <template #default="{ row }">
          <span
            class="trace-code-list-dialog__link cursor-pointer text-[var(--el-color-primary)]"
            @click="navigateToTraceReport(row.traceNo)"
          >
            <TraceCodeTooltip :value="row.traceNo" />
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="traceUnitCode" label="追溯单位" width="96" show-overflow-tooltip />
      <ElTableColumn prop="locationNetSplitQuantity" label="净量/剩余" width="96" align="right" />
      <ElTableColumn
        prop="inHospitalStatusLabel"
        label="在院状态"
        width="96"
        show-overflow-tooltip
      />
    </ElTable>
  </AppDialog>
</template>
