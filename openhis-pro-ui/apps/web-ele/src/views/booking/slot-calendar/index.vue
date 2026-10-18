<script setup lang="ts">
import type { SlotDetailDto, SlotListItemDto } from '#/api/booking/slot-manage';

import { computed, reactive, ref, watch } from 'vue';

import { Page } from '@zxplat/common-ui';

import {
  bookingOptionValue,
  isSameBookingId,
  toBookingId,
  type BookingEntityId,
} from '#/api/booking/booking-id';
import type { BookingOptionDto } from '#/api/booking/booking-types';
import {
  blockSlotsApi,
  loadSlotDetailApi,
  pageSlotsByDateApi,
  setOverbookedApi,
  unblockSlotsApi,
} from '#/api/booking/slot-manage';
import {
  isSlotBlocked,
  matchesSlotStatusFilter,
  resolveSlotStatusDisplayLabel,
  resolveSlotStatusFilterServerCode,
  resolveSlotStatusTagType,
  SLOT_STATUS_FILTER_AVAILABLE,
  SLOT_STATUS_FILTER_FULL,
  SLOT_STATUS_FILTER_BUSY_UNAVAILABLE,
  SLOT_STATUS_FILTER_OPTIONS,
  type SlotStatusFilterCode,
} from '#/api/booking/slot-status';
import {
  loadScheduleFormInitApi,
  previewStopClinicImpactApi,
  releaseStopClinicPreviewFreezeApi,
} from '#/api/booking/outpatient-schedule';
import { addDaysIso, todayLocalDateIso } from '#/api/booking/booking-constants';
import StopClinicWizard from '#/views/booking/components/stop-clinic-wizard.vue';
import { useBookingOrganization } from '#/views/booking/composables/use-booking-organization';
import { useRequest } from '#/hooks/use-request';
import { Refresh } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElEmpty,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

defineOptions({ name: 'BookingSlotCalendar' });

const { organizationId: loginOrganizationId } = useBookingOrganization();
/** 查询科室：默认同登录科室，可在页内切换（与门诊排班一致） */
const queryOrganizationId = ref<BookingEntityId | undefined>();
const list = ref<SlotListItemDto[]>([]);
const total = ref(0);
const selectedRows = ref<SlotListItemDto[]>([]);
const practitioners = ref<BookingOptionDto[]>([]);
const departments = ref<BookingOptionDto[]>([]);
const detailVisible = ref(false);
const detail = ref<SlotDetailDto | null>(null);

const stopWizardVisible = ref(false);
const wizardScheduleId = ref<BookingEntityId>();
const wizardSlotIds = ref<BookingEntityId[]>([]);
const wizardCompleted = ref(false);
let wizardPromise: { resolve: () => void; reject: () => void } | null = null;

const query = reactive({
  pageNo: 1,
  pageSize: 50,
  date: todayLocalDateIso(),
  practitionerId: undefined as BookingEntityId | undefined,
  statusFilter: '' as SlotStatusFilterCode,
});

const statusFilterLabel = computed(() => {
  const hit = SLOT_STATUS_FILTER_OPTIONS.find((item) => item.value === query.statusFilter);
  return hit?.label ?? '';
});

const selectedSlotIds = computed(() =>
  selectedRows.value.map((row) => row.slotId).filter((id): id is BookingEntityId => !!id),
);

const wizardSchedule = computed(() =>
  wizardScheduleId.value ? { scheduleId: wizardScheduleId.value } : null,
);

const currentOrgLabel = computed(() => {
  const hit = departments.value.find((item) =>
    isSameBookingId(item.value, queryOrganizationId.value),
  );
  return hit?.label ?? '';
});

const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

const dateLabel = computed(() => formatDateLabel(query.date));

/** 医师下拉：主数据 + 当日号源回显并集 */
const practitionerFilterOptions = computed(() => {
  const map = new Map<string, BookingOptionDto>();
  for (const item of practitioners.value) {
    const id = toBookingId(item.value);
    if (!id) continue;
    map.set(id, { ...item, value: id });
  }
  for (const row of list.value) {
    const id = toBookingId(row.practitionerId);
    if (!id) continue;
    if (!map.has(id)) {
      map.set(id, { value: id, label: row.practitionerName || '医师待定' });
    }
  }
  return [...map.values()];
});

const summary = computed(() => {
  let capacity = 0;
  let booked = 0;
  let remaining = 0;
  let blocked = 0;
  for (const row of filteredList.value) {
    capacity += Number(row.capacity ?? 0);
    booked += Number(row.bookedCount ?? 0);
    remaining += Number(row.remainingCount ?? 0);
    if (isSlotBlocked(row)) {
      blocked += 1;
    }
  }
  return { capacity, booked, remaining, blocked, count: filteredList.value.length };
});

/** 停诊可走服务端 statusCode；可约/已满为展示态，仅本页前端收窄 */
function resolveServerStatusCode(): string | undefined {
  return resolveSlotStatusFilterServerCode(query.statusFilter);
}

const filteredList = computed(() => {
  if (!query.statusFilter || query.statusFilter === SLOT_STATUS_FILTER_BUSY_UNAVAILABLE) {
    return list.value;
  }
  return list.value.filter((row) => matchesSlotStatusFilter(row, query.statusFilter));
});

/** 分页总数：停诊/无筛选用服务端；可约/已满用本页筛选后条数 */
const displayTotal = computed(() => {
  if (
    query.statusFilter === SLOT_STATUS_FILTER_AVAILABLE ||
    query.statusFilter === SLOT_STATUS_FILTER_FULL
  ) {
    return filteredList.value.length;
  }
  return total.value;
});

const emptyDescription = computed(() => {
  if (!queryOrganizationId.value) {
    return '请先选择查询科室';
  }
  if (!query.date) {
    return '请选择查询日期';
  }
  return `${query.date} 暂无号源`;
});

const { run: runPage, loading } = useRequest(pageSlotsByDateApi, {
  manual: true,
  onSuccess: (res) => {
    list.value = res.content ?? [];
    total.value = Number(res.totalCount ?? 0);
    selectedRows.value = [];
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
  const groups = new Map<string, SlotListItemDto[]>();
  for (const row of rows) {
    const scheduleId = toBookingId(row.scheduleId);
    if (!scheduleId || !row.slotId) continue;
    const bucket = groups.get(scheduleId) ?? [];
    bucket.push(row);
    groups.set(scheduleId, bucket);
  }
  return groups;
}

function openStopWizard(scheduleId: BookingEntityId, slotIds: BookingEntityId[]) {
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

async function blockSlotsDirect(slotIds: BookingEntityId[], reason: string) {
  await blockSlotsApi({ slotIds, reason: reason || '停诊' });
}

async function handleBlock() {
  if (!queryOrganizationId.value) {
    ElMessage.warning('请先选择查询科室');
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
      const slotIds = rows.map((row) => toBookingId(row.slotId)!).filter(Boolean);
      const preview = await previewStopClinicImpactApi({
        organizationId: queryOrganizationId.value,
        scheduleId,
        slotIds,
      });
      const needWizard = (preview?.appointmentCount ?? 0) > 0;
      if (needWizard) {
        await openStopWizard(scheduleId, slotIds);
      } else {
        if (preview?.sourceSlotsFrozen) {
          await releaseStopClinicPreviewFreezeApi({
            organizationId: queryOrganizationId.value,
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
  if (!queryOrganizationId.value) {
    list.value = [];
    total.value = 0;
    return;
  }
  if (!query.date) {
    ElMessage.warning('请选择日期');
    return;
  }
  await runPage({
    organizationId: queryOrganizationId.value,
    date: query.date,
    practitionerId: query.practitionerId,
    statusCode: resolveServerStatusCode(),
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  });
}

/** 从科室下拉解析默认查询科室（优先登录科室，否则取列表首项） */
function pickQueryOrganizationId(
  departmentOptions: BookingOptionDto[] | undefined,
  preferred?: BookingEntityId,
): BookingEntityId | undefined {
  const pref = toBookingId(preferred);
  if (pref && departmentOptions?.some((item) => isSameBookingId(item.value, pref))) {
    return pref;
  }
  return toBookingId(departmentOptions?.[0]?.value) ?? pref;
}

async function initFilters(preferredOrganizationId?: BookingEntityId) {
  const candidate =
    preferredOrganizationId ?? queryOrganizationId.value ?? loginOrganizationId.value;
  if (!candidate) {
    practitioners.value = [];
    departments.value = [];
    queryOrganizationId.value = undefined;
    return;
  }
  let res = await loadScheduleFormInitApi({ organizationId: candidate });
  const resolved = pickQueryOrganizationId(res?.departments, candidate);
  if (resolved !== undefined && resolved !== candidate) {
    res = await loadScheduleFormInitApi({ organizationId: resolved });
  }
  queryOrganizationId.value = resolved;
  practitioners.value = res?.practitioners ?? [];
  departments.value = res?.departments ?? [];
}

/** 切换查询科室：重置医师筛选并重新拉取 */
async function handleOrganizationChange() {
  query.practitionerId = undefined;
  query.pageNo = 1;
  selectedRows.value = [];
  await initFilters(queryOrganizationId.value);
  await loadData();
}

function handleSearch() {
  query.pageNo = 1;
  selectedRows.value = [];
  void loadData();
}

/** 可约/已满仅前端筛：变更时清空勾选，避免与可见行不一致 */
function handleStatusFilterChange() {
  selectedRows.value = [];
  if (query.statusFilter === SLOT_STATUS_FILTER_BUSY_UNAVAILABLE) {
    handleSearch();
  }
}

function openDetail(row: SlotListItemDto) {
  if (!row.slotId) return;
  void runDetail({ slotId: row.slotId });
}

function handleRowDblClick(row: SlotListItemDto) {
  openDetail(row);
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
  const slotId = row.slotId;
  if (!slotId) return;
  await setOverbookedApi({ slotId, overbookedFlag: overbooked });
  ElMessage.success(overbooked ? '已标记超售' : '已取消超售');
  await loadData();
}

function handlePageChange(page: number) {
  query.pageNo = page;
  void loadData();
}

function handlePageSizeChange(size: number) {
  query.pageSize = size;
  query.pageNo = 1;
  void loadData();
}

function setToday() {
  query.date = todayLocalDateIso();
  handleSearch();
}

function shiftDate(days: number) {
  query.date = addDaysIso(query.date || todayLocalDateIso(), days);
  handleSearch();
}

function formatTime(value?: string) {
  if (!value) return '—';
  return String(value).replace('T', ' ').slice(11, 16);
}

function formatDateLabel(date: string) {
  if (!date) return '';
  const parsed = new Date(`${date}T12:00:00`);
  if (Number.isNaN(parsed.getTime())) return date;
  return `${date} ${WEEKDAY_LABELS[parsed.getDay()]}`;
}

function resolveOptionLabel(options: BookingOptionDto[], id?: unknown) {
  const idText = toBookingId(id);
  if (!idText) return '';
  return options.find((item) => isSameBookingId(item.value, idText))?.label ?? '';
}

function organizationLabel(row: SlotListItemDto | SlotDetailDto) {
  return (
    row.organizationName ||
    resolveOptionLabel(departments.value, row.organizationId) ||
    currentOrgLabel.value ||
    '—'
  );
}

function practitionerLabel(row: SlotListItemDto | SlotDetailDto) {
  return (
    row.practitionerName ||
    resolveOptionLabel(practitioners.value, row.practitionerId) ||
    '医师待定'
  );
}

function serviceTypeLabel(row: SlotListItemDto | SlotDetailDto) {
  return row.healthcareServiceName || '—';
}

function rowClassName({ row }: { row: SlotListItemDto }) {
  return isSlotBlocked(row) ? 'slot-calendar-page__row--blocked' : '';
}

watch(loginOrganizationId, (organizationId) => {
  const id = toBookingId(organizationId);
  if (!id) {
    return;
  }
  const departmentOptions = departments.value;
  if (
    departmentOptions.length &&
    !departmentOptions.some((item) => isSameBookingId(item.value, id))
  ) {
    return;
  }
  if (isSameBookingId(queryOrganizationId.value, id)) {
    return;
  }
  queryOrganizationId.value = id;
  void handleOrganizationChange();
});

watch(
  () => query.date,
  (nextDate, prevDate) => {
    if (!nextDate || !prevDate || nextDate === prevDate) {
      return;
    }
    handleSearch();
  },
);

async function bootstrapPage() {
  await initFilters();
  await loadData();
}

void bootstrapPage();
</script>

<template>
  <Page auto-content-height>
    <div class="slot-calendar-page">
      <header class="slot-calendar-page__toolbar">
        <ElSelect
          v-model="queryOrganizationId"
          filterable
          placeholder="查询科室"
          style="width: 180px"
          @change="handleOrganizationChange"
        >
          <ElOption
            v-for="item in departments"
            :key="item.value"
            :label="item.label"
            :value="bookingOptionValue(item.value)"
          />
        </ElSelect>
        <ElDatePicker
          v-model="query.date"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="日期"
          style="width: 160px"
        />
        <ElButton @click="setToday">今天</ElButton>
        <ElButton @click="shiftDate(1)">明天</ElButton>
        <ElButton @click="shiftDate(-1)">昨天</ElButton>
        <ElSelect
          v-model="query.practitionerId"
          clearable
          filterable
          placeholder="医师"
          style="width: 180px"
          @change="handleSearch"
        >
          <ElOption
            v-for="item in practitionerFilterOptions"
            :key="item.value"
            :label="item.label"
            :value="bookingOptionValue(item.value)"
          />
        </ElSelect>
        <ElSelect
          v-model="query.statusFilter"
          clearable
          placeholder="状态"
          style="width: 120px"
          @change="handleStatusFilterChange"
        >
          <ElOption
            v-for="item in SLOT_STATUS_FILTER_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
        <ElButton type="primary" :loading="loading" @click="handleSearch">查询</ElButton>
        <ElButton :icon="Refresh" circle :loading="loading" @click="loadData" />
        <ElButton :disabled="!selectedSlotIds.length" @click="handleBlock">封锁选中</ElButton>
        <ElButton :disabled="!selectedSlotIds.length" @click="handleUnblock">解封选中</ElButton>
        <span v-if="selectedSlotIds.length" class="slot-calendar-page__selection-hint">
          已选 {{ selectedSlotIds.length }} 条
        </span>
        <span v-if="currentOrgLabel" class="slot-calendar-page__org-hint">
          当前科室：{{ currentOrgLabel }}
        </span>
      </header>

      <div class="slot-calendar-page__summary">
        <span v-if="dateLabel" class="slot-calendar-page__date-label">{{ dateLabel }}</span>
        <span>本页 {{ summary.count }} 个时段</span>
        <span v-if="query.statusFilter" class="slot-calendar-page__filter-hint">
          状态「{{ statusFilterLabel }}」（本页筛选）
        </span>
        <span>容量 {{ summary.capacity }}</span>
        <span>已约 {{ summary.booked }}</span>
        <span>剩余 {{ summary.remaining }}</span>
        <span v-if="summary.blocked">停诊 {{ summary.blocked }}</span>
        <span class="slot-calendar-page__summary-total">共 {{ displayTotal }} 条（分页）</span>
      </div>

      <ElTable
        v-loading="loading"
        class="slot-calendar-page__table"
        :data="filteredList"
        border
        stripe
        :row-class-name="rowClassName"
        @selection-change="handleSelectionChange"
        @row-dblclick="handleRowDblClick"
      >
        <ElTableColumn type="selection" width="48" fixed="left" />
        <ElTableColumn label="科室" min-width="120" fixed="left" show-overflow-tooltip>
          <template #default="{ row }">{{ organizationLabel(row) }}</template>
        </ElTableColumn>
        <ElTableColumn label="医师" min-width="100" fixed="left" show-overflow-tooltip>
          <template #default="{ row }">{{ practitionerLabel(row) }}</template>
        </ElTableColumn>
        <ElTableColumn label="号源类型" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ serviceTypeLabel(row) }}</template>
        </ElTableColumn>
        <ElTableColumn label="时段" min-width="120">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }} — {{ formatTime(row.endTime) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="88" align="center">
          <template #default="{ row }">
            <ElTag :type="resolveSlotStatusTagType(row)" size="small" effect="light">
              {{ resolveSlotStatusDisplayLabel(row) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="capacity" label="容量" width="72" align="center" />
        <ElTableColumn prop="bookedCount" label="已约" width="72" align="center" />
        <ElTableColumn label="剩余" width="72" align="center">
          <template #default="{ row }">
            <span
              :class="{
                'slot-calendar-page__remaining--zero': Number(row.remainingCount ?? 0) <= 0,
              }"
            >
              {{ row.remainingCount ?? '—' }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="超售" width="72" align="center">
          <template #default="{ row }">
            <ElTag v-if="row.overbookedFlag" type="warning" size="small">是</ElTag>
            <span v-else>—</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="168" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openDetail(row)"> 详情 </ElButton>
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
        <template #empty>
          <ElEmpty :description="emptyDescription" :image-size="80" />
        </template>
      </ElTable>

      <div class="slot-calendar-page__pager">
        <ElPagination
          :current-page="query.pageNo"
          :page-size="query.pageSize"
          :total="total"
          :page-sizes="[20, 50, 100, 200]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>

      <ElDrawer v-model="detailVisible" title="号源详情" size="440px">
        <ElDescriptions v-if="detail" v-loading="detailLoading" :column="1" border>
          <ElDescriptionsItem label="科室">{{ organizationLabel(detail) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="医师">{{ practitionerLabel(detail) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="号源类型">{{ serviceTypeLabel(detail) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            <ElTag :type="resolveSlotStatusTagType(detail)" size="small">
              {{ resolveSlotStatusDisplayLabel(detail) }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="时段">
            {{ formatTime(detail.startTime) }} — {{ formatTime(detail.endTime) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="容量">{{ detail.capacity }}</ElDescriptionsItem>
          <ElDescriptionsItem label="已约">{{ detail.bookedCount }}</ElDescriptionsItem>
          <ElDescriptionsItem label="剩余">{{ detail.remainingCount }}</ElDescriptionsItem>
          <ElDescriptionsItem label="超售">{{
            detail.overbookedFlag ? '是' : '否'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="备注">{{ detail.commentText || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="号源 ID">{{ detail.slotId }}</ElDescriptionsItem>
          <ElDescriptionsItem label="日程 ID">{{ detail.scheduleId }}</ElDescriptionsItem>
        </ElDescriptions>
      </ElDrawer>

      <StopClinicWizard
        v-model:visible="stopWizardVisible"
        :organization-id="queryOrganizationId"
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
  min-height: 0;
}

.slot-calendar-page__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.slot-calendar-page__org-hint {
  margin-left: auto;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.slot-calendar-page__selection-hint {
  font-size: 13px;
  color: var(--el-color-primary);
}

.slot-calendar-page__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.slot-calendar-page__date-label {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.slot-calendar-page__filter-hint {
  color: var(--el-color-warning);
}

.slot-calendar-page__summary-total {
  margin-left: auto;
  color: var(--el-text-color-secondary);
}

.slot-calendar-page__table {
  flex: 1;
  min-height: 0;
}

.slot-calendar-page__pager {
  display: flex;
  justify-content: flex-end;
}

.slot-calendar-page__remaining--zero {
  color: var(--el-text-color-placeholder);
}

:deep(.slot-calendar-page__row--blocked) {
  opacity: 0.72;
}
</style>
