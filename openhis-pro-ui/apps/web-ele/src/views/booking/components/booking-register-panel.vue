<script setup lang="ts">
import type { BookableSlotDto, BookingPatientItem } from '#/api/booking/booking-register';
import type { BookingEntityId } from '#/api/booking/booking-id';
import { bookingOptionValue, toBookingId } from '#/api/booking/booking-id';
import type { BookingOptionDto } from '#/api/booking/booking-types';

import { computed, reactive, ref } from 'vue';

import {
  createOutpatientBookingApi,
  joinWaitlistApi,
  listBookableSlotsApi,
  pagePatientsForSelectApi,
} from '#/api/booking/booking-register';
import { loadScheduleFormInitApi } from '#/api/booking/outpatient-schedule';
import SelectTable from '#/components/select-table.vue';
import { useBookingOrganization } from '#/views/booking/composables/use-booking-organization';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { ElButton, ElCard, ElDatePicker, ElMessage, ElOption, ElSelect } from 'element-plus';

const emit = defineEmits<{
  booked: [];
}>();

const { organizationId } = useBookingOrganization();
const practitioners = ref<BookingOptionDto[]>([]);
const healthcareServices = ref<BookingOptionDto[]>([]);
const bookableSlots = ref<BookableSlotDto[]>([]);
const selectedPatientId = ref('');
const selectedSlotKey = ref('');

const form = reactive({
  date: '',
  practitionerId: undefined as BookingEntityId | undefined,
  healthcareServiceId: undefined as BookingEntityId | undefined,
});

const patientColumns = [
  { prop: 'name', label: '姓名', minWidth: 100 },
  { prop: 'patientBusNo', label: '患者号', minWidth: 120 },
  { prop: 'idCardNoMasked', label: '证件号', minWidth: 140 },
  { prop: 'phoneMasked', label: '电话', minWidth: 120 },
];

const selectedSlot = computed(() =>
  bookableSlots.value.find((item) => slotKey(item) === selectedSlotKey.value),
);

const isWaitlistMode = computed(
  () =>
    !!selectedSlot.value?.waitlistEligible &&
    !!selectedSlot.value?.fullFlag &&
    !!selectedSlot.value?.slotId,
);

const canSubmit = computed(
  () => !!organizationId.value && !!selectedPatientId.value && !!selectedSlotKey.value,
);

const submitLabel = computed(() => (isWaitlistMode.value ? '加入候补' : '确认预约'));

function slotKey(slot: BookableSlotDto) {
  if (slot.slotId) return `slot-${slot.slotId}`;
  if (slot.virtualSlotKey) return `virtual-${slot.virtualSlotKey}`;
  return '';
}

const { run: runSlots, loading: slotsLoading } = useRequest(listBookableSlotsApi, {
  manual: true,
  onSuccess: (res) => {
    bookableSlots.value = res?.slots ?? [];
    selectedSlotKey.value = '';
  },
});

const { run: runCreate, loading: createLoading } = useRequest(createOutpatientBookingApi, {
  manual: true,
  refreshOnStale: async () => {
    emit('booked');
    await loadBookableSlots();
  },
  onSuccess: (res) => {
    ElMessage.success(`预约成功：${res?.busNo ?? ''}`);
    emit('booked');
    void loadBookableSlots();
  },
});

const { run: runJoinWaitlist, loading: waitlistLoading } = useRequest(joinWaitlistApi, {
  manual: true,
  refreshOnStale: async () => {
    emit('booked');
    await loadBookableSlots();
  },
  onSuccess: (res) => {
    ElMessage.success(`已加入候补，排队序号 ${res?.queuePosition ?? '-'}`);
    emit('booked');
    void loadBookableSlots();
  },
});

async function initOptions() {
  if (!organizationId.value) return;
  const res = await loadScheduleFormInitApi({ organizationId: organizationId.value });
  practitioners.value = res?.practitioners ?? [];
  healthcareServices.value = res?.healthcareServices ?? [];
}

async function loadBookableSlots() {
  if (!organizationId.value || !form.date) return;
  await runSlots({
    organizationId: organizationId.value,
    date: form.date,
    practitionerId: form.practitionerId,
    healthcareServiceId: form.healthcareServiceId,
  });
}

async function queryPatients({
  keyword = '',
  pageNo = 1,
  pageSize = 20,
}: {
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
}) {
  const data = await pagePatientsForSelectApi({ keyword, pageNo, pageSize });
  const { content, totalCount } = parsePagedRowsFromApiData<BookingPatientItem>(data);
  return {
    content: content.map((row) => ({
      ...row,
      value: String(row.patientId ?? ''),
      label: row.name,
    })),
    totalElements: totalCount,
  };
}

async function handleSubmit() {
  const slot = selectedSlot.value;
  if (!canSubmit.value || !organizationId.value || !slot) return;
  if (isWaitlistMode.value && slot.slotId) {
    await runJoinWaitlist({
      organizationId: organizationId.value,
      patientId: toBookingId(selectedPatientId.value)!,
      slotId: toBookingId(slot.slotId)!,
    });
    return;
  }
  if (slot.fullFlag) {
    ElMessage.warning('该号源已满，请选择候补或更换时段');
    return;
  }
  await runCreate({
    organizationId: organizationId.value,
    patientId: toBookingId(selectedPatientId.value)!,
    slotId: toBookingId(slot.slotId),
    virtualSlotKey: slot.virtualSlotKey,
    originChannel: 'WINDOW',
  });
}

function bootstrap() {
  form.date = new Date().toISOString().slice(0, 10);
  void initOptions();
  void loadBookableSlots();
}

bootstrap();
</script>

<template>
  <ElCard shadow="never" class="booking-register-panel">
    <template #header>创建预约</template>

    <div class="booking-register-panel__section">
      <div class="booking-register-panel__label">选择患者</div>
      <SelectTable
        v-model="selectedPatientId"
        :columns="patientColumns"
        value-key="value"
        label-key="label"
        placeholder="搜索患者姓名/证件/电话"
        :remote-method="queryPatients"
      />
    </div>

    <div class="booking-register-panel__filters">
      <ElDatePicker
        v-model="form.date"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="预约日期"
        style="width: 160px"
        @change="loadBookableSlots"
      />
      <ElSelect
        v-model="form.practitionerId"
        clearable
        filterable
        placeholder="医师"
        style="width: 160px"
        @change="loadBookableSlots"
      >
        <ElOption
          v-for="item in practitioners"
          :key="item.value"
          :label="item.label"
          :value="bookingOptionValue(item.value)"
        />
      </ElSelect>
      <ElSelect
        v-model="form.healthcareServiceId"
        clearable
        filterable
        placeholder="号源类型"
        style="width: 160px"
        @change="loadBookableSlots"
      >
        <ElOption
          v-for="item in healthcareServices"
          :key="item.value"
          :label="item.label"
          :value="bookingOptionValue(item.value)"
        />
      </ElSelect>
      <ElButton :loading="slotsLoading" @click="loadBookableSlots">刷新可约</ElButton>
    </div>

    <div v-loading="slotsLoading" class="booking-register-panel__slots">
      <button
        v-for="slot in bookableSlots"
        :key="slotKey(slot)"
        type="button"
        class="booking-register-panel__slot"
        :class="{
          'is-selected': selectedSlotKey === slotKey(slot),
          'is-full': slot.fullFlag,
          'is-waitlist': slot.waitlistEligible,
          'is-template': slot.fromTemplate,
        }"
        @click="selectedSlotKey = slotKey(slot)"
      >
        <span>{{ slot.startTime?.slice(11, 16) }} - {{ slot.endTime?.slice(11, 16) }}</span>
        <span>
          <template v-if="slot.fullFlag && slot.waitlistEligible">
            已满 · 可候补
            <template v-if="slot.waitlistCount">（{{ slot.waitlistCount }}人排队）</template>
          </template>
          <template v-else>余 {{ slot.remainingCount ?? 0 }}</template>
          <template v-if="slot.fromTemplate"> · 占位预览</template>
        </span>
      </button>
      <div v-if="!bookableSlots.length" class="booking-register-panel__empty">暂无可约号源</div>
    </div>

    <ElButton
      type="primary"
      :disabled="!canSubmit"
      :loading="createLoading || waitlistLoading"
      @click="handleSubmit"
    >
      {{ submitLabel }}
    </ElButton>
  </ElCard>
</template>

<style scoped>
.booking-register-panel {
  height: 100%;
}

.booking-register-panel__section {
  margin-bottom: 12px;
}

.booking-register-panel__label {
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.booking-register-panel__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.booking-register-panel__slots {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 88px;
  margin-bottom: 12px;
}

.booking-register-panel__slot {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 108px;
  padding: 10px 12px;
  cursor: pointer;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.booking-register-panel__slot.is-selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.booking-register-panel__slot.is-full:not(.is-waitlist) {
  cursor: not-allowed;
  opacity: 0.55;
}

.booking-register-panel__slot.is-waitlist {
  border-color: var(--el-color-warning-light-5);
}

.booking-register-panel__slot.is-waitlist.is-selected {
  background: var(--el-color-warning-light-9);
}

.booking-register-panel__slot.is-template {
  border-style: dashed;
}

.booking-register-panel__empty {
  color: var(--el-text-color-placeholder);
}
</style>
