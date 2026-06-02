<script lang="ts" setup>
import type { ImecConfigLogItem, ImecMonitorData } from '#/api';

import { ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import { getImecConfigLogDetailApi, pageImecConfigLogApi } from '#/api';

import ImecMonitorDialog from './imec-monitor-dialog.vue';

interface Props {
  visible?: boolean;
  deviceId?: string;
  deviceName?: string;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  deviceId: '',
  deviceName: '',
});

const emit = defineEmits<{
  'update:visible': [value: boolean];
}>();

const show = ref(false);
watch(
  () => props.visible,
  (val) => {
    show.value = val;
    if (val && props.deviceId) {
      loadData();
    }
  },
);
watch(show, (val) => emit('update:visible', val));

const loading = ref(false);
const tableData = ref<ImecConfigLogItem[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);

// 详情弹窗
const detailVisible = ref(false);
const detailData = ref<ImecMonitorData | null>(null);

async function loadData() {
  if (!props.deviceId) return;
  loading.value = true;
  try {
    const res = await pageImecConfigLogApi({
      deviceId: props.deviceId,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    });
    const data = res.data;
    tableData.value = data?.content || [];
    total.value = data?.totalElements || 0;
  } catch {
    ElMessage.error('加载采集日志失败');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function handlePageChange(page: number) {
  pageNo.value = page;
  loadData();
}

function handleSizeChange(size: number) {
  pageSize.value = size;
  pageNo.value = 1;
  loadData();
}

/** 查看日志详情 */
async function handleViewDetail(row: ImecConfigLogItem) {
  if (!row.id) return;
  try {
    const res = await getImecConfigLogDetailApi(String(row.id));
    detailData.value = res.data as unknown as ImecMonitorData;
    detailVisible.value = true;
  } catch {
    ElMessage.error('加载日志详情失败');
  }
}

/** 格式化时间 */
function formatTime(val?: string): string {
  return val || '--';
}

/** 报警数量标签 */
function alarmCountTag(count?: number) {
  if (!count || count === 0) return { text: '无', type: 'info' as const };
  if (count <= 3) return { text: `${count}条`, type: 'warning' as const };
  return { text: `${count}条`, type: 'danger' as const };
}
</script>

<template>
  <el-dialog
    v-model="show"
    :show-close="true"
    :close-on-click-modal="true"
    :close-on-press-escape="true"
    width="860px"
    class="imec-history-dialog"
    append-to-body
    destroy-on-close
  >
    <template #header>
      <div class="imec-history-header">
        <span class="imec-history-title">采集日志</span>
        <span class="imec-brand-tag">Mindray</span>
        <span v-if="deviceName" class="imec-device-tag">{{ deviceName }}</span>
      </div>
    </template>

    <el-table :data="tableData" v-loading="loading" stripe border size="small" style="width: 100%">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="startTime" label="开始时间" width="160" align="center">
        <template #default="{ row }">
          {{ formatTime(row.startTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="endTime" label="结束时间" width="160" align="center">
        <template #default="{ row }">
          {{ formatTime(row.endTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="patientName" label="患者" width="80" align="center">
        <template #default="{ row }">
          {{ row.patientName || '--' }}
        </template>
      </el-table-column>
      <el-table-column prop="bedNo" label="床位" width="70" align="center">
        <template #default="{ row }">
          {{ row.bedNo || '--' }}
        </template>
      </el-table-column>
      <el-table-column prop="alarmCount" label="报警" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="alarmCountTag(row.alarmCount).type" size="small">
            {{ alarmCountTag(row.alarmCount).text }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="observeTime" label="最后采集" width="160" align="center">
        <template #default="{ row }">
          {{ formatTime(row.observeTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center" fixed="right">
        <template #default="{ row }">
          <ElButton type="primary" link size="small" @click="handleViewDetail(row)">
            详情
          </ElButton>
        </template>
      </el-table-column>
    </el-table>

    <div style="display: flex; justify-content: flex-end; margin-top: 12px">
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        small
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <ImecMonitorDialog v-model:visible="detailVisible" :device-id="deviceId" :data="detailData" />
  </el-dialog>
</template>

<style>
.imec-history-dialog .imec-history-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.imec-history-title {
  font-size: 16px;
  font-weight: 600;
}

.imec-brand-tag {
  font-size: 11px;
  font-weight: 700;
  color: #00ccff;
  background: rgba(0, 204, 255, 0.12);
  padding: 2px 10px;
  border-radius: 10px;
  border: 1px solid rgba(0, 204, 255, 0.35);
  letter-spacing: 1px;
}

.imec-device-tag {
  font-size: 12px;
  color: #7ab8d8;
  background: rgba(122, 184, 216, 0.1);
  padding: 2px 10px;
  border-radius: 10px;
  border: 1px solid rgba(122, 184, 216, 0.25);
}
</style>
