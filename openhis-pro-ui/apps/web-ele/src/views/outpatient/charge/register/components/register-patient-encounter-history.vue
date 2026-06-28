<script setup lang="ts">
import type { RegisterHistoryTableRow } from '../services';

import { computed, ref, watch } from 'vue';

import { ElEmpty, ElPagination, ElTable, ElTableColumn, ElTag } from 'element-plus';

import { getDateRangeQuick } from '#/utils/format/date-range-quick';
import { tagTypeByCode } from '#/utils/format/status-tag';

import { ENCOUNTER_STATUS_EL_TAG_MAP, pageRegisterHistoryForTable } from '../services';

defineOptions({
  name: 'RegisterPatientEncounterHistory',
});

const props = defineProps<{
  patientId?: string;
  /** 无档案时可用证件号检索近一年挂号记录 */
  patientKeyword?: string;
}>();

const loading = ref(false);
const rows = ref<RegisterHistoryTableRow[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 10;

const hasPatientContext = computed(() =>
  Boolean(props.patientId?.trim() || props.patientKeyword?.trim()),
);

const showEmptyHistory = computed(
  () => hasPatientContext.value && !loading.value && rows.value.length === 0,
);

function formatAmount(value: unknown): string {
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return '—';
  }
  return n.toFixed(2);
}

async function loadHistory() {
  const patientId = props.patientId?.trim();
  const keyword = patientId ? '' : (props.patientKeyword?.trim() ?? '');
  if (!patientId && !keyword) {
    rows.value = [];
    total.value = 0;
    return;
  }
  const [dateFrom, dateTo] = getDateRangeQuick({
    strategy: 'rolling-days',
    spanDays: 365,
  });
  loading.value = true;
  try {
    const result = await pageRegisterHistoryForTable({
      dateFrom,
      dateTo,
      keyword,
      pageNo: pageNo.value,
      pageSize,
      patientId,
    });
    rows.value = result.list;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

watch(
  () => [props.patientId, props.patientKeyword] as const,
  () => {
    pageNo.value = 1;
    void loadHistory();
  },
  { immediate: true },
);

function onPageChange(next: number) {
  pageNo.value = next;
  void loadHistory();
}
</script>

<template>
  <div v-loading="loading" class="patient-encounter-history min-h-[120px]">
    <ElEmpty v-if="!hasPatientContext" :image-size="64" description="请先选择患者或读卡" />
    <ElEmpty v-else-if="showEmptyHistory" :image-size="64" description="近一年无门诊挂号记录" />
    <template v-else-if="rows.length > 0">
      <ElTable :data="rows" size="small" stripe max-height="220">
        <ElTableColumn prop="startTime" label="挂号时间" width="140" />
        <ElTableColumn prop="organizationName" label="科室" min-width="88" show-overflow-tooltip />
        <ElTableColumn prop="doctorName" label="医生" width="72" show-overflow-tooltip />
        <ElTableColumn prop="serviceTypeName" label="号别" width="72" show-overflow-tooltip />
        <ElTableColumn label="挂号费" width="72" align="right">
          <template #default="{ row }">
            {{ formatAmount(row.amount) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="76" align="center">
          <template #default="{ row }">
            <ElTag size="small" :type="tagTypeByCode(row.statusEnum, ENCOUNTER_STATUS_EL_TAG_MAP)">
              {{ row.statusText || row.statusEnum || '—' }}
            </ElTag>
          </template>
        </ElTableColumn>
      </ElTable>
      <div v-if="total > pageSize" class="mt-2 flex justify-end">
        <ElPagination
          small
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="pageNo"
          @current-change="onPageChange"
        />
      </div>
    </template>
  </div>
</template>
