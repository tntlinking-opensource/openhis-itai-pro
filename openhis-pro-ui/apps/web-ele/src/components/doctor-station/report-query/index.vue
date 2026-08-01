<script setup lang="ts">
import type { LisPacsReportItem } from '#/api/basic/lis-pacs-report';

import { computed, ref, watch } from 'vue';

import { ElButton, ElEmpty, ElMessage, ElSpace, ElTable, ElTableColumn, ElTag } from 'element-plus';

import { listLisPacsReportByEncounterApi } from '#/api/basic/lis-pacs-report';

defineOptions({ name: 'DoctorStationReportQuery' });

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    patientId?: string;
    /** 门诊默认；住院医生站传 inpatient（预留联调差异） */
    stationScope?: 'inpatient' | 'outpatient';
  }>(),
  {
    encounterId: '',
    patientId: '',
    stationScope: 'outpatient',
  },
);

const loading = ref(false);
const rows = ref<LisPacsReportItem[]>([]);

const contextReady = computed(
  () => Boolean(props.encounterId?.trim()) && Boolean(props.patientId?.trim()),
);

function dispatchLabel(status?: string) {
  if (status === 'sent') {
    return { text: '已外发', type: 'success' as const };
  }
  if (status === 'failed') {
    return { text: '外发失败', type: 'danger' as const };
  }
  return { text: '未外发', type: 'info' as const };
}

function openReport(row: LisPacsReportItem) {
  const url = row.reportPdfUrl?.trim();
  if (!url) {
    ElMessage.warning('未配置报告地址');
    return;
  }
  window.open(url, '_blank', 'noopener,noreferrer');
}

async function reload() {
  if (!contextReady.value) {
    rows.value = [];
    return;
  }
  loading.value = true;
  try {
    const res = await listLisPacsReportByEncounterApi(props.encounterId);
    const list = Array.isArray(res) ? res : ((res as { data?: LisPacsReportItem[] })?.data ?? []);
    rows.value = (list ?? []).map((row) => ({
      ...row,
      serviceRequestId:
        row.serviceRequestId !== undefined && row.serviceRequestId !== null
          ? String(row.serviceRequestId)
          : undefined,
    }));
  } catch (error) {
    rows.value = [];
    const message = error instanceof Error && error.message ? error.message : '查询报告失败';
    ElMessage.error(message);
  } finally {
    loading.value = false;
  }
}

defineExpose({ reload });

watch(
  () => [props.encounterId, props.patientId, props.stationScope] as const,
  () => {
    void reload();
  },
  { immediate: true },
);
</script>

<template>
  <div class="doctor-report-query flex h-full min-h-0 flex-col p-2">
    <ElEmpty v-if="!contextReady" description="请先选择患者以查询报告。" />
    <template v-else>
      <div class="mb-2 flex items-center justify-between">
        <span class="text-sm text-[var(--el-text-color-secondary)]">
          检验/检查报告（固定地址 + 申请单号打开 PDF）
        </span>
        <ElButton size="small" :loading="loading" @click="reload">刷新</ElButton>
      </div>
      <ElTable v-loading="loading" :data="rows" stripe height="100%" empty-text="暂无检验/检查申请">
        <ElTableColumn prop="systemLabel" label="类型" width="80" align="center" />
        <ElTableColumn prop="activityBusNo" label="项目编码" width="140" show-overflow-tooltip />
        <ElTableColumn prop="activityName" label="项目名称" min-width="160" show-overflow-tooltip />
        <ElTableColumn prop="applyNo" label="申请单号" width="180" show-overflow-tooltip />
        <ElTableColumn label="外发状态" width="100" align="center">
          <template #default="{ row }">
            <ElTag :type="dispatchLabel(row.dispatchStatus).type" size="small" effect="plain">
              {{ dispatchLabel(row.dispatchStatus).text }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="authoredTime" label="开立时间" width="170" show-overflow-tooltip />
        <ElTableColumn label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <ElSpace>
              <ElButton type="primary" link :disabled="!row.reportPdfUrl" @click="openReport(row)">
                打开报告
              </ElButton>
            </ElSpace>
          </template>
        </ElTableColumn>
      </ElTable>
    </template>
  </div>
</template>
