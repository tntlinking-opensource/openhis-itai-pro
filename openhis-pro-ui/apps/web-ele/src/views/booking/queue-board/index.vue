<script setup lang="ts">
import type { CallToSeeResponse, QueueListItemDto } from '#/api/booking/queue-board';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { ElButton, ElCard, ElEmpty, ElInput, ElMessage, ElOption, ElSelect } from 'element-plus';

import { loadScheduleFormInitApi } from '#/api/booking/outpatient-schedule';
import type { BookingOptionDto } from '#/api/booking/booking-types';
import {
  callToSeeApi,
  changeOverApi,
  checkInApi,
  noShowApi,
  pageCompletedQueueApi,
  pageQueueApi,
  priorityTreatmentApi,
} from '#/api/booking/queue-board';
import { useAuthStore } from '#/store/auth';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'BookingQueueBoard' });

const router = useRouter();
const authStore = useAuthStore();
const waitingList = ref<QueueListItemDto[]>([]);
const completedList = ref<QueueListItemDto[]>([]);
const currentCall = ref<CallToSeeResponse | null>(null);
const clinicLocations = ref<BookingOptionDto[]>([]);
const practitioners = ref<BookingOptionDto[]>([]);
const destinationLocationId = ref<number>();
const practitionerId = ref<number>();
const checkInAppointmentBusNo = ref('');

const query = reactive({
  pageNo: 1,
  pageSize: 50,
});

const organizationId = computed(() => {
  const raw = authStore.getSelectedOrganization();
  const id = Number(raw);
  return Number.isFinite(id) && id > 0 ? id : undefined;
});

const { run: runInit, loading: initLoading } = useRequest(loadScheduleFormInitApi, {
  manual: true,
  onSuccess: (res) => {
    clinicLocations.value = res.clinicLocations ?? [];
    practitioners.value = res.practitioners ?? [];
    if (!destinationLocationId.value && clinicLocations.value.length > 0) {
      const first = Number(clinicLocations.value[0]?.value);
      if (Number.isFinite(first)) {
        destinationLocationId.value = first;
      }
    }
  },
});

const { run: runPage, loading: pageLoading } = useRequest(pageQueueApi, {
  manual: true,
  onSuccess: (res) => {
    waitingList.value = res.content ?? [];
  },
});

const { run: runCompleted, loading: completedLoading } = useRequest(pageCompletedQueueApi, {
  manual: true,
  onSuccess: (res) => {
    completedList.value = res.content ?? [];
  },
});

const { run: runCall, loading: callLoading } = useRequest(callToSeeApi, {
  manual: true,
  onSuccess: (res) => {
    currentCall.value = res;
    ElMessage.success(`已叫号：${res.patientName ?? res.busNo ?? ''}`);
    void refreshQueue();
  },
});

const { run: runNoShow, loading: noShowLoading } = useRequest(noShowApi, {
  manual: true,
  onSuccess: (_, params) => {
    const item = params[0];
    if (currentCall.value?.appointmentId === item.appointmentId) {
      currentCall.value = null;
    }
    ElMessage.success('已过号');
    void refreshQueue();
  },
});

const { run: runPriority, loading: priorityLoading } = useRequest(priorityTreatmentApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('已设为优先');
    void refreshQueue();
  },
});

const { run: runChangeOver, loading: changeOverLoading } = useRequest(changeOverApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('调序成功');
    void refreshQueue();
  },
});

const { run: runCheckIn, loading: checkInLoading } = useRequest(checkInApi, {
  manual: true,
  onSuccess: (res) => {
    ElMessage.success(`报到成功，排队号 ${res.displayOrder ?? ''}`);
    checkInAppointmentBusNo.value = '';
    void refreshQueue();
  },
});

async function refreshQueue() {
  if (!organizationId.value || !destinationLocationId.value) {
    return;
  }
  await Promise.all([
    runPage({
      organizationId: organizationId.value,
      destinationLocationId: destinationLocationId.value,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    }),
    runCompleted({
      organizationId: organizationId.value,
      destinationLocationId: destinationLocationId.value,
      pageNo: 1,
      pageSize: 50,
    }),
  ]);
}

async function handleCheckIn() {
  if (!organizationId.value || !destinationLocationId.value) {
    ElMessage.warning('请先选择科室与诊室');
    return;
  }
  if (!checkInAppointmentBusNo.value.trim()) {
    ElMessage.warning('请输入预约单号');
    return;
  }
  await runCheckIn({
    appointmentBusNo: checkInAppointmentBusNo.value.trim(),
    destinationLocationId: destinationLocationId.value,
    organizationId: organizationId.value,
    originChannel: 'WINDOW',
  });
}

async function handleRefresh() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录科室');
    return;
  }
  if (!destinationLocationId.value) {
    ElMessage.warning('请选择诊室');
    return;
  }
  await refreshQueue();
}

async function handleCall(row: QueueListItemDto) {
  if (!row.appointmentId) {
    return;
  }
  if (!practitionerId.value) {
    ElMessage.warning('请选择叫号医师');
    return;
  }
  await runCall({
    appointmentId: row.appointmentId,
    practitionerId: practitionerId.value,
  });
}

async function handleNoShow(appointmentId?: number) {
  if (!appointmentId) {
    return;
  }
  await runNoShow({ appointmentId });
}

async function handlePriority(row: QueueListItemDto) {
  if (!row.appointmentId) return;
  await runPriority({ appointmentId: row.appointmentId });
}

async function handleMove(row: QueueListItemDto, delta: number) {
  if (!row.appointmentId || !organizationId.value || !destinationLocationId.value) return;
  const currentOrder = Number(row.displayOrder ?? 0);
  const targetOrder = Math.max(1, currentOrder + delta);
  await runChangeOver({
    organizationId: organizationId.value,
    destinationLocationId: destinationLocationId.value,
    appointmentId: row.appointmentId,
    targetDisplayOrder: targetOrder,
  });
}

async function handleCallNext() {
  const first = waitingList.value[0];
  if (!first) {
    ElMessage.info('暂无等候患者');
    return;
  }
  await handleCall(first);
}

function openDisplayWindow() {
  if (!organizationId.value || !destinationLocationId.value) {
    ElMessage.warning('请先选择诊室');
    return;
  }
  const route = router.resolve({
    path: '/booking/queue-display',
    query: {
      orgId: String(organizationId.value),
      locationId: String(destinationLocationId.value),
    },
  });
  window.open(route.href, '_blank');
}

onMounted(async () => {
  if (organizationId.value) {
    await runInit({ organizationId: organizationId.value });
    await refreshQueue();
  }
});
</script>

<template>
  <Page auto-content-height>
    <div class="queue-board">
      <header class="queue-board__toolbar">
        <ElSelect
          v-model="destinationLocationId"
          clearable
          filterable
          placeholder="诊室"
          style="width: 220px"
          :loading="initLoading"
          @change="refreshQueue"
        >
          <ElOption
            v-for="item in clinicLocations"
            :key="item.value"
            :label="item.label"
            :value="Number(item.value)"
          />
        </ElSelect>
        <ElInput
          v-model="checkInAppointmentBusNo"
          clearable
          placeholder="预约单号报到"
          style="width: 200px"
        />
        <ElButton type="success" :loading="checkInLoading" @click="handleCheckIn">报到</ElButton>
        <ElSelect
          v-model="practitionerId"
          clearable
          filterable
          placeholder="叫号医师"
          style="width: 200px"
          :loading="initLoading"
        >
          <ElOption
            v-for="item in practitioners"
            :key="item.value"
            :label="item.label"
            :value="Number(item.value)"
          />
        </ElSelect>
        <ElButton type="primary" :loading="pageLoading || completedLoading" @click="handleRefresh">
          刷新
        </ElButton>
        <ElButton type="warning" :loading="callLoading" @click="handleCallNext">叫下一位</ElButton>
        <ElButton @click="openDisplayWindow">打开大屏</ElButton>
      </header>

      <div class="queue-board__columns">
        <ElCard class="queue-board__column" shadow="never">
          <template #header>
            <span>等候（{{ waitingList.length }}）</span>
          </template>
          <div v-if="waitingList.length === 0" class="queue-board__empty">
            <ElEmpty description="暂无等候患者" :image-size="64" />
          </div>
          <div v-for="row in waitingList" :key="row.appointmentId" class="queue-board__card">
            <div class="queue-board__card-title">
              {{ String(row.displayOrder ?? '').padStart(3, '0') }}
              {{ row.patientName || '未知患者' }}
              <span v-if="row.triageLevel" class="queue-board__priority-tag">优先</span>
            </div>
            <div class="queue-board__card-meta">
              {{ row.busNo }}
              <span v-if="row.substituteDisplay" class="queue-board__substitute">
                · {{ row.substituteDisplay }}
              </span>
            </div>
            <div class="queue-board__card-actions">
              <ElButton size="small" type="primary" :loading="callLoading" @click="handleCall(row)">
                叫号
              </ElButton>
              <ElButton size="small" :loading="priorityLoading" @click="handlePriority(row)">
                优先
              </ElButton>
              <ElButton size="small" :loading="changeOverLoading" @click="handleMove(row, -1)">
                前移
              </ElButton>
              <ElButton size="small" :loading="changeOverLoading" @click="handleMove(row, 1)">
                后移
              </ElButton>
              <ElButton
                size="small"
                :loading="noShowLoading"
                @click="handleNoShow(row.appointmentId)"
              >
                过号
              </ElButton>
            </div>
          </div>
        </ElCard>

        <ElCard class="queue-board__column" shadow="never">
          <template #header>
            <span>当前叫号</span>
          </template>
          <div v-if="!currentCall" class="queue-board__empty">
            <ElEmpty description="暂无叫号" :image-size="64" />
          </div>
          <div v-else class="queue-board__card queue-board__card--active">
            <div class="queue-board__card-title">
              {{ String(currentCall.displayOrder ?? '').padStart(3, '0') }}
              {{ currentCall.patientName || '未知患者' }}
            </div>
            <div class="queue-board__card-meta">
              {{ currentCall.busNo }}
              <span v-if="currentCall.substituteDisplay" class="queue-board__substitute">
                · {{ currentCall.substituteDisplay }}
              </span>
            </div>
            <div class="queue-board__card-actions">
              <ElButton size="small" type="primary" :loading="callLoading" @click="handleCallNext">
                叫下一位
              </ElButton>
              <ElButton
                size="small"
                :loading="noShowLoading"
                @click="handleNoShow(currentCall.appointmentId)"
              >
                过号
              </ElButton>
            </div>
          </div>
        </ElCard>

        <ElCard class="queue-board__column" shadow="never">
          <template #header>
            <span>已过号 / 完成（{{ completedList.length }}）</span>
          </template>
          <div v-if="completedList.length === 0" class="queue-board__empty">
            <ElEmpty description="暂无记录" :image-size="64" />
          </div>
          <div v-for="row in completedList" :key="row.appointmentId" class="queue-board__card">
            <div class="queue-board__card-title">{{ row.patientName || row.busNo }}</div>
            <div class="queue-board__card-meta">
              {{ row.statusView || row.busNo }}
              <span v-if="row.substituteDisplay" class="queue-board__substitute">
                · {{ row.substituteDisplay }}
              </span>
            </div>
          </div>
        </ElCard>
      </div>
    </div>
  </Page>
</template>

<style scoped>
.queue-board {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.queue-board__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.queue-board__columns {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  min-height: 420px;
}

.queue-board__column {
  display: flex;
  flex-direction: column;
  min-height: 360px;
}

.queue-board__column :deep(.el-card__body) {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  overflow: auto;
}

.queue-board__card {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.queue-board__card--active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.queue-board__card-title {
  font-size: 15px;
  font-weight: 600;
}

.queue-board__card-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.queue-board__substitute {
  color: var(--el-color-warning);
}

.queue-board__card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.queue-board__priority-tag {
  margin-left: 6px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 500;
  color: var(--el-color-warning);
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: 4px;
}

.queue-board__empty {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
}
</style>
