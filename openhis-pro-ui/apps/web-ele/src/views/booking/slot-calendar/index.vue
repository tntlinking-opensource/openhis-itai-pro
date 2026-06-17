<script setup lang="ts">
import type { SlotDetailDto, SlotListItemDto } from '#/api/booking/slot-manage';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import {
  blockSlotsApi,
  loadSlotDetailApi,
  pageSlotsByDateApi,
  setOverbookedApi,
  unblockSlotsApi,
} from '#/api/booking/slot-manage';
import {
  loadScheduleFormInitApi,
  previewStopClinicImpactApi,
  releaseStopClinicPreviewFreezeApi,
} from '#/api/booking/outpatient-schedule';
import type { BookingOptionDto } from '#/api/booking/booking-types';
import StopClinicWizard from '#/views/booking/components/stop-clinic-wizard.vue';
import { useBookingOrganization } from '#/views/booking/composables/use-booking-organization';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

defineOptions({ name: 'BookingSlotCalendar' });

const { organizationId } = useBookingOrganization();
const list = ref<SlotListItemDto[]>([]);
const total = ref(0);
const selectedRows = ref<SlotListItemDto[]>([]);
const practitioners = ref<BookingOptionDto[]>([]);
const detailVisible = ref(false);
const detail = ref<SlotDetailDto | null>(null);

const stopWizardVisible = ref(false);
const wizardScheduleId = ref<number>();
const wizardSlotIds = ref<number[]>([]);
const wizardCompleted = ref(false);
let wizardPromise: { resolve: () => void; reject: () => void } | null = null;

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  date: '',
  practitionerId: undefined as number | undefined,
});

const selectedSlotIds = computed(() =>
  selectedRows.value.map((row) => row.slotId).filter((id): id is number => !!id),
);

const wizardSchedule = computed(() =>
  wizardScheduleId.value ? { scheduleId: wizardScheduleId.value } : null,
);

const { run: runPage, loading } = useRequest(pageSlotsByDateApi, {
  manual: true,
  onSuccess: (res) => {
    list.value = res.content ?? [];
    total.value = Number(res.totalCount ?? 0);
  },
});

const { run: runDetail, loading: detailLoading } = useRequest(loadSlotDetailApi, {
  manual: true,
  onSuccess: (res) => {
    detail.value = res ?? null;
    detailVisible.value = true;
  },
});

function groupSelectedBySchedule(rows: SlotListItemDto[]) {
  const groups = new Map<number, SlotListItemDto[]>();
  for (const row of rows) {
    if (!row.scheduleId || !row.slotId) continue;
    const bucket = groups.get(row.scheduleId) ?? [];
    bucket.push(row);
    groups.set(row.scheduleId, bucket);
  }
  return groups;
}

function openStopWizard(scheduleId: number, slotIds: number[]) {
  return new Promise<void>((resolve, reject) => {
    wizardScheduleId.value = scheduleId;
    wizardSlotIds.value = slotIds;
    wizardCompleted.value = false;
    wizardPromise = { resolve, reject };
    stopWizardVisible.value = true;
  });
}

async function handleWizardDone() {
  wizardCompleted.value = true;
  wizardPromise?.resolve();
  wizardPromise = null;
  await loadData();
}

watch(stopWizardVisible, (visible) => {
  if (!visible && wizardPromise && !wizardCompleted.value) {
    wizardPromise.reject();
    wizardPromise = null;
  }
});

async function blockSlotsDirect(slotIds: number[], reason: string) {
  await blockSlotsApi({ slotIds, reason: reason || '停诊' });
}

async function handleBlock() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录科室');
    return;
  }
  if (!selectedSlotIds.value.length) {
    ElMessage.warning('请先选择号源');
    return;
  }

  const groups = groupSelectedBySchedule(selectedRows.value);
  if (!groups.size) {
    ElMessage.warning('所选号源缺少日程信息');
    return;
  }

  try {
    const { value } = await ElMessageBox.prompt('封锁原因', '封锁号源', {
      inputPlaceholder: '停诊',
    });
    const reason = value || '停诊';

    for (const [scheduleId, rows] of groups) {
      const slotIds = rows.map((row) => row.slotId!).filter(Boolean);
      const preview = await previewStopClinicImpactApi({
        organizationId: organizationId.value,
        scheduleId,
        slotIds,
      });
      const needWizard = (preview?.appointmentCount ?? 0) > 0;
      if (needWizard) {
        await openStopWizard(scheduleId, slotIds);
      } else {
        if (preview?.sourceSlotsFrozen) {
          await releaseStopClinicPreviewFreezeApi({
            organizationId: organizationId.value,
            scheduleId,
            slotIds,
          });
        }
        await blockSlotsDirect(slotIds, reason);
      }
    }

    ElMessage.success('封锁操作已完成');
    await loadData();
  } catch {
    // 用户取消或向导中断
  }
}

async function loadData() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录科室');
    return;
  }
  if (!query.date) {
    ElMessage.warning('请选择日期');
    return;
  }
  await runPage({
    organizationId: organizationId.value,
    date: query.date,
    practitionerId: query.practitionerId,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  });
}

async function initFilters() {
  if (!organizationId.value) return;
  const res = await loadScheduleFormInitApi({ organizationId: organizationId.value });
  practitioners.value = res?.practitioners ?? [];
}

function handleSelectionChange(rows: SlotListItemDto[]) {
  selectedRows.value = rows;
}

async function handleUnblock() {
  if (!selectedSlotIds.value.length) {
    ElMessage.warning('请先选择号源');
    return;
  }
  await unblockSlotsApi({ slotIds: selectedSlotIds.value });
  ElMessage.success('已解封');
  await loadData();
}

async function handleToggleOverbook(row: SlotListItemDto, overbooked: boolean) {
  if (!row.slotId) return;
  await setOverbookedApi({ slotId: row.slotId, overbookedFlag: overbooked });
  ElMessage.success(overbooked ? '已标记超售' : '已取消超售');
  await loadData();
}

function handlePageChange(page: number) {
  query.pageNo = page;
  void loadData();
}

onMounted(async () => {
  query.date = new Date().toISOString().slice(0, 10);
  await initFilters();
  await loadData();
});
</script>

<template>
  <Page auto-content-height>
    <div class="slot-calendar-page">
      <header class="slot-calendar-page__toolbar">
        <ElDatePicker
          v-model="query.date"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="日期"
          style="width: 160px"
        />
        <ElSelect
          v-model="query.practitionerId"
          clearable
          filterable
          placeholder="医师"
          style="width: 180px"
        >
          <ElOption
            v-for="item in practitioners"
            :key="item.value"
            :label="item.label"
            :value="Number(item.value)"
          />
        </ElSelect>
        <ElButton type="primary" :loading="loading" @click="loadData">查询</ElButton>
        <ElButton :disabled="!selectedSlotIds.length" @click="handleBlock">封锁选中</ElButton>
        <ElButton :disabled="!selectedSlotIds.length" @click="handleUnblock">解封选中</ElButton>
      </header>

      <ElTable
        v-loading="loading"
        :data="list"
        border
        stripe
        @selection-change="handleSelectionChange"
      >
        <ElTableColumn type="selection" width="48" />
        <ElTableColumn prop="startTime" label="开始" min-width="160" />
        <ElTableColumn prop="endTime" label="结束" min-width="160" />
        <ElTableColumn prop="displayStatus" label="状态" width="100" />
        <ElTableColumn prop="capacity" label="容量" width="80" />
        <ElTableColumn prop="bookedCount" label="已约" width="80" />
        <ElTableColumn prop="remainingCount" label="剩余" width="80" />
        <ElTableColumn label="超售" width="80">
          <template #default="{ row }">
            {{ row.overbookedFlag ? '是' : '否' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="row.slotId && runDetail({ slotId: row.slotId })">
              详情
            </ElButton>
            <ElButton
              v-if="row.slotId"
              link
              type="warning"
              @click="handleToggleOverbook(row, !row.overbookedFlag)"
            >
              {{ row.overbookedFlag ? '取消超售' : '标记超售' }}
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <div class="slot-calendar-page__pager">
        <span>共 {{ total }} 条</span>
        <ElButton :disabled="query.pageNo <= 1" @click="handlePageChange(query.pageNo - 1)">
          上一页
        </ElButton>
        <span>第 {{ query.pageNo }} 页</span>
        <ElButton
          :disabled="query.pageNo * query.pageSize >= total"
          @click="handlePageChange(query.pageNo + 1)"
        >
          下一页
        </ElButton>
      </div>

      <ElDrawer v-model="detailVisible" title="号源详情" size="420px">
        <ElDescriptions v-if="detail" v-loading="detailLoading" :column="1" border>
          <ElDescriptionsItem label="号源 ID">{{ detail.slotId }}</ElDescriptionsItem>
          <ElDescriptionsItem label="日程 ID">{{ detail.scheduleId }}</ElDescriptionsItem>
          <ElDescriptionsItem label="状态">{{
            detail.displayStatus || detail.statusCode
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="开始">{{ detail.startTime }}</ElDescriptionsItem>
          <ElDescriptionsItem label="结束">{{ detail.endTime }}</ElDescriptionsItem>
          <ElDescriptionsItem label="容量">{{ detail.capacity }}</ElDescriptionsItem>
          <ElDescriptionsItem label="已约">{{ detail.bookedCount }}</ElDescriptionsItem>
          <ElDescriptionsItem label="剩余">{{ detail.remainingCount }}</ElDescriptionsItem>
          <ElDescriptionsItem label="超售">{{
            detail.overbookedFlag ? '是' : '否'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="备注">{{ detail.commentText || '-' }}</ElDescriptionsItem>
        </ElDescriptions>
      </ElDrawer>

      <StopClinicWizard
        v-model:visible="stopWizardVisible"
        :organization-id="organizationId"
        :schedule="wizardSchedule"
        :slot-ids="wizardSlotIds"
        :practitioners="practitioners"
        @done="handleWizardDone"
      />
    </div>
  </Page>
</template>

<style scoped>
.slot-calendar-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.slot-calendar-page__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.slot-calendar-page__pager {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}
</style>
