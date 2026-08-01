<!--
  追溯码流水 Drawer：按「从 → 到 + 单据 + 类别」展示可读流转。
-->
<script setup lang="ts">
import type { TraceNumberRecordRowDto } from '#/api/report/trace-number-report';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { ElTable, ElTableColumn } from 'element-plus';

import { listTraceRecordsApi } from '#/api/report/trace-number-report';
import { useRequest } from '#/hooks/use-request';
import { fmtReportNumber } from '#/views/report/shared/report-format';

import { buildTraceFlowDisplayRows } from './build-trace-flow-display-rows';

defineOptions({ name: 'TraceNumberDetailDrawer' });

const [Drawer, drawerApi] = useVbenDrawer();

const activeTraceNo = ref('');
const rawRecords = ref<TraceNumberRecordRowDto[]>([]);

const { run: runRecords, loading: recordsLoading } = useRequest(listTraceRecordsApi, {
  manual: true,
  onSuccess: (data) => {
    rawRecords.value = data?.records ?? [];
  },
});

/** 成对合并后的可读行。 */
const displayRows = computed(() => buildTraceFlowDisplayRows(rawRecords.value));

/** 格式化日期时间展示。 */
function formatDateTime(value?: string) {
  const text = String(value ?? '').trim();
  if (!text) {
    return '—';
  }
  return text.length >= 19 ? text.slice(0, 19) : text;
}

/** 展示数量：无小数；空为 —。 */
function formatQuantity(value?: number | string) {
  const text = fmtReportNumber(value, 0);
  return text || '—';
}

/** 加载流水并打开 Drawer。 */
async function open(traceNo: string) {
  const code = traceNo.trim();
  if (!code) {
    return;
  }
  activeTraceNo.value = code;
  rawRecords.value = [];
  drawerApi.open();
  await runRecords({ traceNo: code });
}

defineExpose({ open });
</script>

<template>
  <Drawer
    :title="`流转流水 · ${activeTraceNo || '—'}`"
    class="trace-number-detail-drawer w-[980px]"
    :loading="recordsLoading"
    :footer="false"
  >
    <ElTable
      v-loading="recordsLoading"
      :data="displayRows"
      border
      stripe
      size="small"
      max-height="640"
      empty-text="暂无流转记录"
      row-key="key"
    >
      <ElTableColumn
        prop="createdAt"
        label="时间"
        width="160"
        align="center"
        header-align="center"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="fromLocation"
        label="从"
        min-width="120"
        align="center"
        header-align="center"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="toLocation"
        label="到"
        min-width="120"
        align="center"
        header-align="center"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="documentText"
        label="单据"
        min-width="220"
        align="center"
        header-align="center"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="categoryText"
        label="类别"
        width="96"
        align="center"
        header-align="center"
        show-overflow-tooltip
      />
      <ElTableColumn prop="quantity" label="数量" width="72" align="right" header-align="right">
        <template #default="{ row }">
          {{ formatQuantity(row.quantity) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="unitText"
        label="单位"
        width="64"
        align="center"
        header-align="center"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="patientName"
        label="患者"
        width="88"
        align="center"
        header-align="center"
        show-overflow-tooltip
      />
    </ElTable>
  </Drawer>
</template>
