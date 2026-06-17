<script setup lang="ts">
import type { OutpatientBookingSnapshotDto } from '#/api/outpatient-registration';

import { computed, onMounted, ref } from 'vue';

import { ElButton, ElInput, ElMessage } from 'element-plus';

import { getRegistrationModeApi } from '#/api/booking/booking-policy';
import { httpLoadAppointmentForRegister } from '#/api/outpatient-registration';
import { useAuthStore } from '#/store/auth';

defineOptions({
  name: 'AppointmentLoadBar',
});

const emit = defineEmits<{
  cleared: [];
  loaded: [snapshot: OutpatientBookingSnapshotDto];
}>();

const props = defineProps<{
  patientId?: string;
}>();

const appointmentBusNo = ref('');
const loading = ref(false);
const snapshot = ref<null | OutpatientBookingSnapshotDto>(null);
const bookingEnabled = ref(false);
const modeLoading = ref(true);

const authStore = useAuthStore();

const summaryText = computed(() => {
  const s = snapshot.value;
  if (!s) {
    return '';
  }
  const org = s.organizationName || '—';
  const doctor = s.practitionerName || '—';
  const service = s.serviceTypeName || '—';
  const slotStart = s.slotStart ? String(s.slotStart).replace('T', ' ').slice(0, 16) : '—';
  return `${s.appointmentBusNo} · ${org} · ${doctor} · ${service} · ${slotStart}`;
});

/**
 * 按预约单号或当前选中患者加载当日有效预约。
 */
async function handleLoad() {
  const busNo = appointmentBusNo.value.trim();
  const patientId = props.patientId?.trim();
  if (!busNo && !patientId) {
    ElMessage.warning('请先选择患者或输入预约单号');
    return;
  }
  loading.value = true;
  try {
    const result = await httpLoadAppointmentForRegister({
      appointmentBusNo: busNo || undefined,
      patientId: patientId || undefined,
    });
    snapshot.value = result;
    emit('loaded', result);
    ElMessage.success('预约加载成功');
  } finally {
    loading.value = false;
  }
}

/**
 * 清空预约带入上下文。
 */
function handleClear() {
  appointmentBusNo.value = '';
  snapshot.value = null;
  emit('cleared');
}

defineExpose({
  clear: handleClear,
  getSnapshot: () => snapshot.value,
});

onMounted(async () => {
  modeLoading.value = true;
  try {
    const orgRaw = authStore.getSelectedOrganization();
    const organizationId = Number(orgRaw);
    const res = await getRegistrationModeApi(
      Number.isFinite(organizationId) && organizationId > 0 ? { organizationId } : {},
    );
    bookingEnabled.value = !!res?.bookingEnabled;
  } catch {
    bookingEnabled.value = false;
  } finally {
    modeLoading.value = false;
  }
});
</script>

<template>
  <div
    v-if="!modeLoading && bookingEnabled"
    class="appointment-load-bar mb-2 space-y-1 rounded border border-dashed border-gray-200 px-2 py-1.5"
  >
    <div class="flex items-center gap-2">
      <ElInput
        v-model="appointmentBusNo"
        class="flex-1"
        clearable
        placeholder="预约单号（可选，不填则按患者查当日预约）"
        @keyup.enter="handleLoad"
      />
      <ElButton :loading="loading" type="primary" @click="handleLoad">加载预约</ElButton>
      <ElButton v-if="snapshot" @click="handleClear">清除预约</ElButton>
    </div>
    <div v-if="snapshot" class="text-xs text-gray-600">已加载：{{ summaryText }}</div>
  </div>
</template>
