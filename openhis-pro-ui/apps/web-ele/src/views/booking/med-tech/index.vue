<script setup lang="ts">
import type { BookableSlotDto } from '#/api/booking/booking-register';
import type { EncounterContextDto, ServiceRequestItemDto } from '#/api/booking/med-tech-booking';

import { computed, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import {
  createDiagnosticBookingApi,
  listBookableServiceRequestsApi,
  listDiagnosticBookableSlotsApi,
  searchEncounterApi,
} from '#/api/booking/med-tech-booking';
import { useBookingOrganization } from '#/views/booking/composables/use-booking-organization';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElInput,
  ElMessage,
  ElTable,
  ElTableColumn,
} from 'element-plus';

defineOptions({ name: 'BookingMedTech' });

const { organizationId } = useBookingOrganization();
const keyword = ref('');
const encounter = ref<EncounterContextDto | null>(null);
const serviceRequests = ref<ServiceRequestItemDto[]>([]);
const selectedRequestIds = ref<number[]>([]);
const bookableSlots = ref<BookableSlotDto[]>([]);
const selectedSlotId = ref<number>();
const slotDate = ref('');

const canBook = computed(
  () =>
    !!organizationId.value &&
    !!encounter.value?.patientId &&
    selectedRequestIds.value.length === 1 &&
    !!selectedSlotId.value,
);

const { run: runSearch, loading: searchLoading } = useRequest(searchEncounterApi, {
  manual: true,
  onSuccess: async (res) => {
    encounter.value = res ?? null;
    selectedRequestIds.value = [];
    bookableSlots.value = [];
    selectedSlotId.value = undefined;
    if (res?.encounterId && organizationId.value) {
      await runRequests({
        organizationId: organizationId.value,
        encounterId: res.encounterId,
      });
    } else {
      serviceRequests.value = [];
    }
  },
});

const { run: runRequests, loading: requestsLoading } = useRequest(listBookableServiceRequestsApi, {
  manual: true,
  onSuccess: (res) => {
    serviceRequests.value = res?.items ?? [];
  },
});

const { run: runSlots, loading: slotsLoading } = useRequest(listDiagnosticBookableSlotsApi, {
  manual: true,
  onSuccess: (res) => {
    bookableSlots.value = res?.slots ?? [];
    selectedSlotId.value = undefined;
  },
});

const { run: runCreate, loading: createLoading } = useRequest(createDiagnosticBookingApi, {
  manual: true,
  onSuccess: (res) => {
    ElMessage.success(`医技预约成功：${res?.busNo ?? ''}`);
    if (encounter.value?.encounterId && organizationId.value) {
      void runRequests({
        organizationId: organizationId.value,
        encounterId: encounter.value.encounterId,
      });
    }
    void loadSlots();
  },
});

async function handleSearch() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录科室');
    return;
  }
  if (!keyword.value.trim()) {
    ElMessage.warning('请输入就诊号或患者关键字');
    return;
  }
  await runSearch({
    organizationId: organizationId.value,
    keyword: keyword.value.trim(),
  });
}

function handleSelectionChange(rows: ServiceRequestItemDto[]) {
  selectedRequestIds.value = rows
    .map((row) => row.serviceRequestId)
    .filter((id): id is number => !!id);
}

async function loadSlots() {
  const requestId = selectedRequestIds.value[0];
  if (!organizationId.value || !requestId || !slotDate.value) return;
  await runSlots({
    organizationId: organizationId.value,
    serviceRequestId: requestId,
    date: slotDate.value,
  });
}

async function handleBook() {
  const requestId = selectedRequestIds.value[0];
  if (!canBook.value || !organizationId.value || !encounter.value?.patientId || !requestId) {
    return;
  }
  if (!selectedSlotId.value) return;
  await runCreate({
    organizationId: organizationId.value,
    patientId: encounter.value.patientId,
    serviceRequestId: requestId,
    slotId: selectedSlotId.value,
    originChannel: 'WINDOW',
  });
}

slotDate.value = new Date().toISOString().slice(0, 10);
</script>

<template>
  <Page auto-content-height>
    <div class="med-tech-page">
      <header class="med-tech-page__toolbar">
        <ElInput
          v-model="keyword"
          clearable
          placeholder="就诊号 / 患者姓名 / 病历号"
          style="width: 320px"
          @keyup.enter="handleSearch"
        />
        <ElButton type="primary" :loading="searchLoading" @click="handleSearch">查询就诊</ElButton>
      </header>

      <ElCard v-if="encounter" shadow="never" class="med-tech-page__summary">
        <div>
          患者：{{ encounter.patientName || '—' }} · 就诊 {{ encounter.encounterBusNo || '—' }} ·
          {{ encounter.organizationName || '—' }}
        </div>
      </ElCard>

      <ElCard shadow="never">
        <template #header>待预约医嘱</template>
        <ElTable
          v-loading="requestsLoading"
          :data="serviceRequests"
          border
          stripe
          @selection-change="handleSelectionChange"
        >
          <ElTableColumn type="selection" width="48" :selectable="(row) => !row.bookedFlag" />
          <ElTableColumn prop="activityName" label="项目" min-width="160" />
          <ElTableColumn prop="serviceRequestBusNo" label="医嘱号" min-width="120" />
          <ElTableColumn prop="statusView" label="状态" width="100" />
          <ElTableColumn prop="bookingStatusView" label="预约状态" width="100">
            <template #default="{ row }">
              {{ row.bookedFlag ? '已预约' : '待预约' }}
            </template>
          </ElTableColumn>
        </ElTable>
      </ElCard>

      <ElCard shadow="never">
        <template #header>可约时段</template>
        <div class="med-tech-page__slot-toolbar">
          <ElDatePicker
            v-model="slotDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="预约日期"
            style="width: 160px"
            @change="loadSlots"
          />
          <ElButton
            :disabled="selectedRequestIds.length !== 1"
            :loading="slotsLoading"
            @click="loadSlots"
          >
            刷新可约
          </ElButton>
          <ElButton
            type="primary"
            :disabled="!canBook"
            :loading="createLoading"
            @click="handleBook"
          >
            预约选中项
          </ElButton>
        </div>
        <div v-loading="slotsLoading" class="med-tech-page__slots">
          <button
            v-for="slot in bookableSlots"
            :key="slot.slotId"
            type="button"
            class="med-tech-page__slot"
            :class="{
              'is-selected': selectedSlotId === slot.slotId,
              'is-full': slot.fullFlag,
            }"
            :disabled="slot.fullFlag"
            @click="selectedSlotId = slot.slotId"
          >
            <span>{{ slot.startTime?.slice(11, 16) }} - {{ slot.endTime?.slice(11, 16) }}</span>
            <span>余 {{ slot.remainingCount ?? 0 }}</span>
          </button>
          <div v-if="!bookableSlots.length" class="med-tech-page__empty">
            请选择医嘱并刷新可约时段
          </div>
        </div>
      </ElCard>
    </div>
  </Page>
</template>

<style scoped>
.med-tech-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.med-tech-page__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.med-tech-page__summary {
  font-size: 14px;
}

.med-tech-page__slot-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.med-tech-page__slots {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 72px;
}

.med-tech-page__slot {
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

.med-tech-page__slot.is-selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.med-tech-page__slot.is-full {
  cursor: not-allowed;
  opacity: 0.55;
}

.med-tech-page__empty {
  color: var(--el-text-color-placeholder);
}
</style>
