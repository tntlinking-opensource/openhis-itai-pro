<!--
  追溯码详情 Drawer：概览 + 流转明细。
-->
<script setup lang="ts">
import type {
  GetTraceNumberOverviewResponse,
  TraceNumberRecordRowDto,
} from '#/api/report/trace-number-report';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import {
  ElDescriptions,
  ElDescriptionsItem,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
} from 'element-plus';

import { getTraceNumberOverviewApi, listTraceRecordsApi } from '#/api/report/trace-number-report';
import { useRequest } from '#/hooks/use-request';
import { fmtReportNumber } from '#/views/report/shared/report-format';

defineOptions({ name: 'TraceNumberDetailDrawer' });

const [Drawer, drawerApi] = useVbenDrawer();

const activeTraceNo = ref('');
const activeTab = ref<'overview' | 'records'>('overview');
const overview = ref<GetTraceNumberOverviewResponse | null>(null);
const recordRows = ref<TraceNumberRecordRowDto[]>([]);

const { run: runOverview, loading: overviewLoading } = useRequest(getTraceNumberOverviewApi, {
  manual: true,
  onSuccess: (data) => {
    overview.value = data ?? null;
  },
});

const { run: runRecords, loading: recordsLoading } = useRequest(listTraceRecordsApi, {
  manual: true,
  onSuccess: (data) => {
    recordRows.value = data?.records ?? [];
  },
});

const drawerLoading = computed(() => overviewLoading.value || recordsLoading.value);

/** 格式化日期时间展示。 */
function formatDateTime(value?: string) {
  const text = String(value ?? '').trim();
  if (!text) {
    return '—';
  }
  return text.length >= 19 ? text.slice(0, 19) : text;
}

/** 加载概览与明细并打开 Drawer。 */
async function open(traceNo: string, tab: 'overview' | 'records' = 'overview') {
  const code = traceNo.trim();
  if (!code) {
    return;
  }
  activeTraceNo.value = code;
  activeTab.value = tab;
  overview.value = null;
  recordRows.value = [];
  drawerApi.open();
  await Promise.all([runOverview({ traceNo: code }), runRecords({ traceNo: code })]);
}

/** 切换 Tab 时按需刷新（首次已并行加载）。 */
function handleTabChange(name: number | string) {
  activeTab.value = String(name) as 'overview' | 'records';
}

defineExpose({ open });
</script>

<template>
  <Drawer
    :title="`追溯码详情 · ${activeTraceNo || '—'}`"
    class="trace-number-detail-drawer w-[960px]"
    :loading="drawerLoading"
    :footer="false"
  >
    <ElTabs v-model="activeTab" @tab-change="handleTabChange">
      <ElTabPane label="概览" name="overview">
        <ElDescriptions v-loading="overviewLoading" :column="2" border size="small">
          <ElDescriptionsItem label="追溯码">
            {{ overview?.traceNo ?? activeTraceNo }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="在院状态">
            {{ overview?.inHospitalStatusLabel ?? '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="药品编码">
            {{ overview?.medicationCode ?? '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="药品名称">
            {{ overview?.medicationName ?? '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="规格">
            {{ overview?.specificationText ?? '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="批号">
            {{ overview?.lotNumber ?? '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="追溯单位">
            {{ overview?.traceUnitCode ?? '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="拆零剩余">
            {{ fmtReportNumber(overview?.remainingSplitQuantity, 2) }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </ElTabPane>

      <ElTabPane label="明细" name="records">
        <ElTable
          v-loading="recordsLoading"
          :data="recordRows"
          border
          stripe
          size="small"
          max-height="520"
          empty-text="暂无流转记录"
        >
          <ElTableColumn prop="createdAt" label="时间" width="160" show-overflow-tooltip>
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="flowDirectionLabel" label="方向" width="72" show-overflow-tooltip />
          <ElTableColumn prop="flowCategoryLabel" label="类别" width="96" show-overflow-tooltip />
          <ElTableColumn
            prop="operationTypeLabel"
            label="操作类型"
            min-width="120"
            show-overflow-tooltip
          />
          <ElTableColumn prop="locationName" label="库位" min-width="120" show-overflow-tooltip />
          <ElTableColumn prop="quantity" label="数量" width="88" align="right">
            <template #default="{ row }">
              {{ fmtReportNumber(row.quantity, 2) }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="unitCodeLabel" label="单位" width="72" show-overflow-tooltip />
          <ElTableColumn prop="busNo" label="单据号" min-width="140" show-overflow-tooltip />
          <ElTableColumn prop="patientName" label="患者" width="96" show-overflow-tooltip />
        </ElTable>
      </ElTabPane>
    </ElTabs>
  </Drawer>
</template>
