<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  BookableSlotDto,
  BookingDetailDto,
  BookingListItemDto,
} from '#/api/booking/booking-register';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  cancelBookingApi,
  listBookableSlotsApi,
  loadBookingDetailApi,
  pageBookingsApi,
  rescheduleBookingApi,
} from '#/api/booking/booking-register';
import BookingRegisterPanel from '#/views/booking/components/booking-register-panel.vue';
import { useBookingOrganization } from '#/views/booking/composables/use-booking-organization';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElMessage,
  ElMessageBox,
} from 'element-plus';

defineOptions({ name: 'BookingOutpatientRegister' });

const { organizationId } = useBookingOrganization();
const list = ref<BookingListItemDto[]>([]);
const total = ref(0);
const detailVisible = ref(false);
const detail = ref<BookingDetailDto | null>(null);
const rescheduleVisible = ref(false);
const rescheduleRow = ref<BookingListItemDto | null>(null);
const rescheduleDate = ref('');
const rescheduleSlots = ref<BookableSlotDto[]>([]);
const selectedRescheduleSlotId = ref<number>();

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
});

const formItems: FormItemConfig[] = [
  {
    prop: 'keyword',
    label: '预约单号',
    type: 'input',
    placeholder: '业务单号关键字',
  },
];

const columns: TableColumn[] = [
  { prop: 'busNo', label: '预约单号', minWidth: 140 },
  { prop: 'patientName', label: '患者', minWidth: 100 },
  { prop: 'startInstant', label: '开始', minWidth: 160 },
  { prop: 'endInstant', label: '结束', minWidth: 160 },
  { prop: 'statusView', label: '状态', width: 100 },
  { prop: 'slotId', label: '号源', width: 100 },
  { prop: 'actions', label: '操作', width: 160, slot: 'actions' },
];

const { run: runRescheduleSlots, loading: rescheduleSlotsLoading } = useRequest(
  listBookableSlotsApi,
  {
    manual: true,
    onSuccess: (res) => {
      rescheduleSlots.value = res?.slots ?? [];
      selectedRescheduleSlotId.value = undefined;
    },
  },
);

const { run: runReschedule, loading: rescheduleLoading } = useRequest(rescheduleBookingApi, {
  manual: true,
  onSuccess: (res) => {
    ElMessage.success(`改约成功：${res?.busNo ?? ''}`);
    rescheduleVisible.value = false;
    void loadData();
  },
});

const { run: runPage, loading } = useRequest(pageBookingsApi, {
  manual: true,
  onSuccess: (res) => {
    list.value = res.content ?? [];
    total.value = Number(res.totalCount ?? 0);
  },
});

const { run: runDetail, loading: detailLoading } = useRequest(loadBookingDetailApi, {
  manual: true,
  onSuccess: (res) => {
    detail.value = res ?? null;
    detailVisible.value = true;
  },
});

async function loadData() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录科室');
    return;
  }
  await runPage({
    organizationId: organizationId.value,
    keyword: String(query.keyword || '').trim() || undefined,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  });
}

function handleSearch() {
  query.pageNo = 1;
  void loadData();
}

function handlePagination(pagination: { limit: number; page: number }) {
  query.pageNo = pagination.page;
  query.pageSize = pagination.limit;
  void loadData();
}

async function handleCancel(row: BookingListItemDto) {
  if (!row.appointmentId) return;
  try {
    await ElMessageBox.confirm(`确认取消预约 ${row.busNo ?? ''}？`, '取消预约');
    await cancelBookingApi({ appointmentId: row.appointmentId, reason: '窗口取消' });
    ElMessage.success('已取消');
    await loadData();
  } catch {
    // cancel
  }
}

function openReschedule(row: BookingListItemDto) {
  rescheduleRow.value = row;
  rescheduleDate.value = row.startInstant?.slice(0, 10) || new Date().toISOString().slice(0, 10);
  rescheduleVisible.value = true;
  void loadRescheduleSlots();
}

async function loadRescheduleSlots() {
  if (!organizationId.value || !rescheduleDate.value) return;
  await runRescheduleSlots({
    organizationId: organizationId.value,
    date: rescheduleDate.value,
  });
}

async function handleConfirmReschedule() {
  if (!rescheduleRow.value?.appointmentId || !selectedRescheduleSlotId.value) {
    ElMessage.warning('请选择新时段');
    return;
  }
  await runReschedule({
    appointmentId: rescheduleRow.value.appointmentId,
    newSlotId: selectedRescheduleSlotId.value,
    reason: '窗口改约',
  });
}

onMounted(loadData);
</script>

<template>
  <Page auto-content-height>
    <div class="booking-register-page">
      <div class="booking-register-page__left">
        <BookingRegisterPanel @booked="loadData" />
      </div>
      <div class="booking-register-page__right">
        <TableLayout
          v-model:query-params="query"
          :form-items="formItems"
          :table-columns="columns"
          :table-data="list"
          :total="total"
          :loading="loading"
          @search="handleSearch"
          @pagination="handlePagination"
        >
          <template #actions="{ row }">
            <ElButton
              link
              type="primary"
              @click="row.appointmentId && runDetail({ appointmentId: row.appointmentId })"
            >
              详情
            </ElButton>
            <ElButton link type="primary" @click="openReschedule(row)">改约</ElButton>
            <ElButton link type="danger" @click="handleCancel(row)">取消</ElButton>
          </template>
        </TableLayout>
      </div>

      <ElDrawer v-model="detailVisible" title="预约详情" size="420px">
        <ElDescriptions v-if="detail" v-loading="detailLoading" :column="1" border>
          <ElDescriptionsItem label="预约单号">{{ detail.busNo }}</ElDescriptionsItem>
          <ElDescriptionsItem label="患者">{{ detail.patientName }}</ElDescriptionsItem>
          <ElDescriptionsItem label="状态">{{ detail.statusView }}</ElDescriptionsItem>
          <ElDescriptionsItem label="开始">{{ detail.startInstant }}</ElDescriptionsItem>
          <ElDescriptionsItem label="结束">{{ detail.endInstant }}</ElDescriptionsItem>
          <ElDescriptionsItem label="号源">{{ detail.slotId }}</ElDescriptionsItem>
        </ElDescriptions>
      </ElDrawer>

      <ElDialog v-model="rescheduleVisible" title="改约" width="520px">
        <div class="booking-register-page__reschedule">
          <div class="booking-register-page__reschedule-meta">
            预约单 {{ rescheduleRow?.busNo }} · {{ rescheduleRow?.patientName }}
          </div>
          <ElDatePicker
            v-model="rescheduleDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="新预约日期"
            style="width: 160px; margin-bottom: 12px"
            @change="loadRescheduleSlots"
          />
          <div v-loading="rescheduleSlotsLoading" class="booking-register-page__reschedule-slots">
            <button
              v-for="slot in rescheduleSlots"
              :key="slot.slotId"
              type="button"
              class="booking-register-page__slot-btn"
              :class="{
                'is-selected': selectedRescheduleSlotId === slot.slotId,
                'is-full': slot.fullFlag,
              }"
              :disabled="slot.fullFlag"
              @click="selectedRescheduleSlotId = slot.slotId"
            >
              {{ slot.startTime?.slice(11, 16) }} - {{ slot.endTime?.slice(11, 16) }} （余
              {{ slot.remainingCount ?? 0 }}）
            </button>
          </div>
        </div>
        <template #footer>
          <ElButton @click="rescheduleVisible = false">取消</ElButton>
          <ElButton type="primary" :loading="rescheduleLoading" @click="handleConfirmReschedule">
            确认改约
          </ElButton>
        </template>
      </ElDialog>
    </div>
  </Page>
</template>

<style scoped>
.booking-register-page {
  display: grid;
  grid-template-columns: minmax(360px, 40%) minmax(0, 1fr);
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.booking-register-page__left,
.booking-register-page__right {
  min-height: 0;
  overflow: auto;
}

.booking-register-page__reschedule-meta {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.booking-register-page__reschedule-slots {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 72px;
}

.booking-register-page__slot-btn {
  padding: 8px 12px;
  cursor: pointer;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.booking-register-page__slot-btn.is-selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.booking-register-page__slot-btn.is-full {
  cursor: not-allowed;
  opacity: 0.55;
}
</style>
