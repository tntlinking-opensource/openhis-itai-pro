<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue';

import type { EmqConfigLogItem } from '#/api';

import { pageConfigLogApi, getConfigLogDetailApi } from '#/api';
import { ElMessage } from 'element-plus';

import MonitorDialog from './monitor-dialog.vue';

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
const tableData = ref<EmqConfigLogItem[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(10);

// 监护仪详情弹窗
const monitorVisible = ref(false);
const monitorData = ref<any>(null);

async function loadData() {
  if (!props.deviceId) return;
  loading.value = true;
  try {
    const res = await pageConfigLogApi({
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
async function handleViewDetail(row: EmqConfigLogItem) {
  if (!row.id) return;
  try {
    const res = await getConfigLogDetailApi(String(row.id));
    monitorData.value = res.data;
    monitorVisible.value = true;
  } catch {
    ElMessage.error('加载日志详情失败');
  }
}

/** 格式化时间 */
function formatTime(val?: string): string {
  if (!val) return '--';
  return val;
}
</script>

<template>
  <el-dialog
    v-model="show"
    :show-close="true"
    :close-on-click-modal="true"
    :close-on-press-escape="true"
    width="800px"
    title="采集日志"
    append-to-body
    destroy-on-close
  >
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between">
        <span style="font-size: 16px; font-weight: 600">采集日志</span>
        <span v-if="deviceName" style="font-size: 13px; color: #00ccff">{{ deviceName }}</span>
      </div>
    </template>

    <el-table :data="tableData" v-loading="loading" stripe border size="small" style="width: 100%">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="observeTime" label="采集时间" width="170" align="center">
        <template #default="{ row }">
          {{ row.observeTime || '--' }}
        </template>
      </el-table-column>
      <el-table-column prop="patientId" label="患者ID" width="120" align="center">
        <template #default="{ row }">
          {{ row.patientId || '--' }}
        </template>
      </el-table-column>
      <el-table-column prop="bedNo" label="床位" width="80" align="center">
        <template #default="{ row }">
          {{ row.bedNo || '--' }}
        </template>
      </el-table-column>
      <el-table-column prop="success" label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'" size="small">
            {{ row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="记录时间" width="170" align="center">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <ElButton type="primary" link size="small" @click="handleViewDetail(row)">
            查看
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

    <MonitorDialog v-model:visible="monitorVisible" :data="monitorData" />
  </el-dialog>
</template>
