<script setup lang="ts">
import type { QueueDisplaySnapshotDto } from '#/api/booking/queue-board';
import { toBookingId } from '#/api/booking/booking-id';
import type { BookingEntityId } from '#/api/booking/booking-id';

import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { getQueueDisplaySnapshotApi } from '#/api/booking/queue-board';

defineOptions({ name: 'BookingQueueDisplay' });

const route = useRoute();
const snapshot = ref<QueueDisplaySnapshotDto | null>(null);
const loading = ref(false);
const errorText = ref('');
const pollTimer = ref<number>();

const organizationId = computed((): BookingEntityId | undefined => {
  const raw = route.query.orgId ?? route.query.organizationId;
  return toBookingId(raw);
});

const destinationLocationId = computed((): BookingEntityId | undefined => {
  const raw = route.query.locationId ?? route.query.destinationLocationId;
  return toBookingId(raw);
});

const titleText = computed(() => {
  const org = snapshot.value?.organizationName || '候诊叫号';
  const loc = snapshot.value?.locationName || '';
  return loc ? `${org} · ${loc}` : org;
});

const currentCallText = computed(() => {
  const call = snapshot.value?.currentCall;
  if (!call) return '暂无叫号';
  const order = String(call.displayOrder ?? '').padStart(3, '0');
  const name = call.patientName || '未知患者';
  return `请 ${order} 号 ${name} 到诊室就诊`;
});

async function pollSnapshot() {
  if (!organizationId.value || !destinationLocationId.value) {
    errorText.value = '缺少 orgId 或 locationId 参数';
    return;
  }
  loading.value = true;
  try {
    snapshot.value = await getQueueDisplaySnapshotApi({
      organizationId: organizationId.value,
      destinationLocationId: destinationLocationId.value,
    });
    errorText.value = '';
  } catch {
    errorText.value = '连接中断，重试中…';
  } finally {
    loading.value = false;
  }
}

function startPolling() {
  stopPolling();
  void pollSnapshot();
  pollTimer.value = window.setInterval(pollSnapshot, 4000);
}

function stopPolling() {
  if (pollTimer.value) {
    window.clearInterval(pollTimer.value);
    pollTimer.value = undefined;
  }
}

onMounted(startPolling);
onUnmounted(stopPolling);
</script>

<template>
  <div class="queue-display" :class="{ 'is-loading': loading }">
    <header class="queue-display__header">{{ titleText }} · 叫号大屏</header>

    <div v-if="errorText" class="queue-display__error">{{ errorText }}</div>

    <section class="queue-display__current">
      {{ currentCallText }}
    </section>

    <section class="queue-display__waiting">
      <div class="queue-display__waiting-title">等候</div>
      <div class="queue-display__waiting-list">
        <span
          v-for="item in snapshot?.waitingList ?? []"
          :key="item.appointmentId"
          class="queue-display__waiting-item"
        >
          {{ String(item.displayOrder ?? '').padStart(3, '0') }} {{ item.patientName }}
        </span>
        <span v-if="!snapshot?.waitingList?.length" class="queue-display__waiting-empty"
          >暂无等候</span
        >
      </div>
    </section>
  </div>
</template>

<style scoped>
.queue-display {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-height: 100vh;
  padding: 32px 48px;
  color: #fff;
  background: linear-gradient(180deg, #0b3d91 0%, #051a3a 100%);
}

.queue-display__header {
  font-size: 28px;
  font-weight: 600;
  text-align: center;
}

.queue-display__error {
  font-size: 18px;
  color: #ffd666;
  text-align: center;
}

.queue-display__current {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  font-size: clamp(36px, 6vw, 72px);
  font-weight: 700;
  line-height: 1.3;
  text-align: center;
}

.queue-display__waiting-title {
  margin-bottom: 12px;
  font-size: 22px;
  opacity: 0.85;
}

.queue-display__waiting-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 24px;
  font-size: 24px;
}

.queue-display__waiting-item {
  padding: 8px 16px;
  background: rgb(255 255 255 / 12%);
  border-radius: 8px;
}

.queue-display__waiting-empty {
  opacity: 0.6;
}
</style>
