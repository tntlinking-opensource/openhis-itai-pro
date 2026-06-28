<script setup lang="ts">
import type { RegisterSlotSelectionPayload } from '../services';

import type { BookingEntityId } from '#/api/booking/booking-id';
import type { SlotDetailDto, SlotListItemDto } from '#/api/booking/slot-manage';
import type { OptionDto } from '#/api/outpatient-registration';

import { computed, reactive, ref, watch } from 'vue';

import { Refresh } from '@element-plus/icons-vue';
import { ElButton, ElMessage, ElOption, ElSelect, ElTag } from 'element-plus';

import { todayLocalDateIso } from '#/api/booking/booking-constants';
import { bookingOptionValue, isSameBookingId, toBookingId } from '#/api/booking/booking-id';
import { loadSlotDetailApi, pageSlotsByDateApi } from '#/api/booking/slot-manage';
import { useRequest } from '#/hooks/use-request';
import { useBookingOrganization } from '#/views/booking/composables/use-booking-organization';

import { loadRegisterFormInit } from '../services/register.service';

defineOptions({
  name: 'RegisterDailySlotPanel',
});

const props = withDefaults(
  defineProps<{
    /** 左侧挂号表单当前号源类型，同步右侧筛选 */
    healthcareServiceId?: BookingEntityId;
    /** 左侧挂号表单科室，优先于顶部登录科室 */
    organizationId?: BookingEntityId;
    /** 左侧挂号表单当前医师，同步右侧筛选 */
    practitionerId?: BookingEntityId;
  }>(),
  {
    organizationId: undefined,
    practitionerId: undefined,
    healthcareServiceId: undefined,
  },
);

const emit = defineEmits<{
  slotSelected: [payload: RegisterSlotSelectionPayload];
}>();

interface PractitionerSlotGroup {
  key: string;
  practitionerId?: string;
  practitionerName: string;
  slots: SlotListItemDto[];
}

const { organizationId: loginOrganizationId } = useBookingOrganization();
const effectiveOrganizationId = computed(
  () => toBookingId(props.organizationId) ?? loginOrganizationId.value,
);
const list = ref<SlotListItemDto[]>([]);
const total = ref(0);
const practitioners = ref<OptionDto[]>([]);
const healthcareServices = ref<OptionDto[]>([]);
const selectedSlotId = ref<string>();

const query = reactive({
  pageNo: 1,
  pageSize: 200,
});

const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

/** 现场挂号仅查今日号源；跨日请走门诊预约 */
const todayDateLabel = computed(() => {
  const date = todayLocalDateIso();
  const parsed = new Date(`${date}T12:00:00`);
  const weekday = Number.isNaN(parsed.getTime()) ? '' : WEEKDAY_LABELS[parsed.getDay()];
  return weekday ? `${date} ${weekday}` : date;
});

const filters = reactive({
  practitionerId: undefined as BookingEntityId | undefined,
  healthcareServiceId: undefined as BookingEntityId | undefined,
});

/** 号源类型在前端收窄（接口仅支持医师服务端筛选） */
const displayedList = computed(() => {
  const serviceId = toBookingId(filters.healthcareServiceId);
  if (!serviceId) {
    return list.value;
  }
  return list.value.filter((row) => isSameBookingId(row.healthcareServiceId, serviceId));
});

const summary = computed(() => {
  let capacity = 0;
  let booked = 0;
  let remaining = 0;
  for (const row of displayedList.value) {
    capacity += Number(row.capacity ?? 0);
    booked += Number(row.bookedCount ?? 0);
    remaining += Number(row.remainingCount ?? 0);
  }
  return { capacity, booked, remaining };
});

const groupedList = computed<PractitionerSlotGroup[]>(() => {
  const groupMap = new Map<string, PractitionerSlotGroup>();
  for (const row of displayedList.value) {
    const practitionerId = toBookingId(row.practitionerId);
    const key = practitionerId ?? '__unknown__';
    const practitionerName = practitionerLabel(row);
    const existing = groupMap.get(key);
    if (existing) {
      existing.slots.push(row);
      continue;
    }
    groupMap.set(key, {
      key,
      practitionerId,
      practitionerName,
      slots: [row],
    });
  }

  const groups = [...groupMap.values()];
  for (const group of groups) {
    group.slots.sort((left, right) =>
      String(left.startTime ?? '').localeCompare(String(right.startTime ?? '')),
    );
  }
  groups.sort((left, right) =>
    left.practitionerName.localeCompare(right.practitionerName, 'zh-CN'),
  );
  return groups;
});

/** 仅多医师且未指定医师筛选时展示分组标题 */
const showGroupHeaders = computed(
  () => groupedList.value.length > 1 && !toBookingId(filters.practitionerId),
);

/** 医师下拉：主数据 + 当日号源回显并集，避免 actorId 与下拉口径不一致 */
const practitionerFilterOptions = computed(() => {
  const map = new Map<string, OptionDto>();
  for (const item of practitioners.value) {
    const id = toBookingId(item.value);
    if (!id) continue;
    map.set(id, { value: id, label: item.label });
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

/** 号源类型下拉：主数据 + 当日号源回显并集 */
const healthcareServiceFilterOptions = computed(() => {
  const map = new Map<string, OptionDto>();
  for (const item of healthcareServices.value) {
    const id = toBookingId(item.value);
    if (!id) continue;
    map.set(id, { value: id, label: item.label });
  }
  for (const row of list.value) {
    const id = toBookingId(row.healthcareServiceId);
    if (!id) continue;
    if (!map.has(id)) {
      map.set(id, { value: id, label: row.healthcareServiceName || '号源类型待定' });
    }
  }
  return [...map.values()];
});

const { run: runPage, loading } = useRequest(pageSlotsByDateApi, {
  manual: true,
  onSuccess: (res) => {
    list.value = res.content ?? [];
    total.value = Number(res.totalCount ?? 0);
  },
});

async function loadData() {
  const orgId = effectiveOrganizationId.value;
  if (!orgId) {
    list.value = [];
    total.value = 0;
    return;
  }
  const date = todayLocalDateIso();
  await runPage({
    organizationId: orgId,
    date,
    practitionerId: filters.practitionerId,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  });
}

/** 医师筛选变更：回第一页并重新拉取 */
function handlePractitionerFilterChange() {
  selectedSlotId.value = undefined;
  handleSearch();
}

/** 号源类型筛选变更：仅前端收窄列表 */
function handleServiceTypeFilterChange() {
  selectedSlotId.value = undefined;
}

/** 重置医师/号源类型筛选 */
function resetSlotFilters() {
  filters.practitionerId = undefined;
  filters.healthcareServiceId = undefined;
  selectedSlotId.value = undefined;
}

/** 将左侧挂号表单医师/号源类型同步到筛选条 */
function syncFiltersFromForm(
  practitionerId?: BookingEntityId,
  healthcareServiceId?: BookingEntityId,
) {
  const nextPractitioner = toBookingId(practitionerId);
  const nextService = toBookingId(healthcareServiceId);
  const practitionerChanged = !isSameBookingId(filters.practitionerId, nextPractitioner);
  filters.practitionerId = nextPractitioner;
  filters.healthcareServiceId = nextService;
  selectedSlotId.value = undefined;
  if (practitionerChanged && effectiveOrganizationId.value) {
    query.pageNo = 1;
    void loadData();
  }
}

async function initFilters() {
  const orgId = effectiveOrganizationId.value;
  if (!orgId) {
    practitioners.value = [];
    healthcareServices.value = [];
    return;
  }
  const res = await loadRegisterFormInit({
    organizationId: orgId,
    registrationDate: todayLocalDateIso(),
  });
  practitioners.value = res?.practitionerOptions ?? [];
  healthcareServices.value = res?.serviceTypeOptions ?? [];
}

function handleSearch() {
  query.pageNo = 1;
  void loadData();
}

function handlePageChange(page: number) {
  query.pageNo = page;
  void loadData();
}

function formatTime(value?: string): string {
  if (!value) {
    return '—';
  }
  return String(value).replace('T', ' ').slice(11, 16);
}

function resolveOptionLabel(options: OptionDto[], id?: unknown): string {
  const idText = toBookingId(id);
  if (!idText) {
    return '';
  }
  const hit = options.find((item) => isSameBookingId(item.value, idText));
  return hit?.label ?? '';
}

function practitionerLabel(row: SlotListItemDto): string {
  return (
    row.practitionerName ||
    resolveOptionLabel(practitioners.value, row.practitionerId) ||
    '医师待定'
  );
}

function serviceTypeLabel(row: SlotListItemDto): string {
  return (
    row.healthcareServiceName ||
    resolveOptionLabel(healthcareServices.value, row.healthcareServiceId) ||
    '号源类型待定'
  );
}

function toSelectionPayload(
  row: SlotListItemDto,
  detail?: SlotDetailDto,
): null | RegisterSlotSelectionPayload {
  const slotId = row.slotId ?? detail?.slotId;
  const orgId = row.organizationId ?? detail?.organizationId ?? effectiveOrganizationId.value;
  const serviceId = row.healthcareServiceId ?? detail?.healthcareServiceId;
  if (!slotId || !orgId || !serviceId) {
    return null;
  }
  return {
    slotId,
    organizationId: orgId,
    healthcareServiceId: serviceId,
    practitionerId: row.practitionerId ?? detail?.practitionerId,
    slotStart: row.startTime ?? detail?.startTime,
    slotEnd: row.endTime ?? detail?.endTime,
  };
}

function isSelectable(row: SlotListItemDto): boolean {
  if (row.displayStatus === '停诊') {
    return false;
  }
  const remaining = Number(row.remainingCount ?? 0);
  return remaining > 0 || Boolean(row.overbookedFlag);
}

function cardStatusType(row: SlotListItemDto): 'danger' | 'info' | 'success' | 'warning' {
  if (row.displayStatus === '停诊') {
    return 'danger';
  }
  if (row.displayStatus === '已满') {
    return 'info';
  }
  return 'success';
}

function cardClassName(row: SlotListItemDto): Record<string, boolean> {
  return {
    'register-daily-slot-panel__card--selected':
      row.slotId !== null &&
      row.slotId !== undefined &&
      isSameBookingId(selectedSlotId.value, row.slotId),
    'register-daily-slot-panel__card--disabled': !isSelectable(row),
  };
}

async function handleCardClick(row: SlotListItemDto) {
  if (!row.slotId) {
    return;
  }
  if (row.displayStatus === '停诊') {
    ElMessage.warning('该号源已停诊');
    return;
  }
  const remaining = Number(row.remainingCount ?? 0);
  if (remaining <= 0 && !row.overbookedFlag) {
    ElMessage.warning('该号源已满');
    return;
  }

  let payload = toSelectionPayload(row);
  if (!payload) {
    try {
      const detail = await loadSlotDetailApi({ slotId: row.slotId });
      payload = toSelectionPayload(row, detail ?? {});
    } catch {
      ElMessage.error('加载号源详情失败');
      return;
    }
  }
  if (!payload) {
    ElMessage.warning('号源缺少科室/医师/号源类型信息，无法带入');
    return;
  }
  selectedSlotId.value = toBookingId(row.slotId);
  emit('slotSelected', payload);
}

watch(
  effectiveOrganizationId,
  async (orgId) => {
    if (!orgId) {
      list.value = [];
      total.value = 0;
      practitioners.value = [];
      healthcareServices.value = [];
      resetSlotFilters();
      return;
    }
    resetSlotFilters();
    await initFilters();
    await loadData();
  },
  { immediate: true },
);

watch(
  () => [props.practitionerId, props.healthcareServiceId] as const,
  ([practitionerId, healthcareServiceId]) => {
    syncFiltersFromForm(practitionerId, healthcareServiceId);
  },
  { immediate: true },
);

defineExpose({
  refresh: loadData,
});
</script>

<template>
  <div class="register-daily-slot-panel flex h-full min-h-0 flex-1 flex-col overflow-hidden">
    <div class="mb-2 flex flex-wrap items-center gap-2">
      <span
        class="register-daily-slot-panel__today text-sm font-medium text-[var(--el-text-color-primary)]"
      >
        今日 {{ todayDateLabel }}
      </span>
      <ElSelect
        v-model="filters.practitionerId"
        clearable
        filterable
        placeholder="医师"
        style="width: 140px"
        @change="handlePractitionerFilterChange"
      >
        <ElOption
          v-for="item in practitionerFilterOptions"
          :key="item.value"
          :label="item.label"
          :value="bookingOptionValue(item.value)"
        />
      </ElSelect>
      <ElSelect
        v-model="filters.healthcareServiceId"
        clearable
        filterable
        placeholder="号源类型"
        style="width: 140px"
        @change="handleServiceTypeFilterChange"
      >
        <ElOption
          v-for="item in healthcareServiceFilterOptions"
          :key="item.value"
          :label="item.label"
          :value="bookingOptionValue(item.value)"
        />
      </ElSelect>
      <ElButton :icon="Refresh" circle :loading="loading" title="刷新今日号源" @click="loadData" />
      <span class="ml-auto text-sm text-[var(--el-text-color-secondary)]">
        汇总：容量 {{ summary.capacity }} · 已约 {{ summary.booked }} · 剩余 {{ summary.remaining }}
      </span>
    </div>

    <div v-loading="loading" class="register-daily-slot-panel__body min-h-0 flex-1 overflow-y-auto">
      <div v-if="!effectiveOrganizationId && !loading" class="register-daily-slot-panel__empty">
        请先在左侧挂号信息中选择科室，或于顶部选择登录科室
      </div>
      <div
        v-else-if="groupedList.length === 0 && !loading"
        class="register-daily-slot-panel__empty"
      >
        {{
          filters.practitionerId || filters.healthcareServiceId
            ? '当前筛选条件下暂无号源'
            : '当日暂无号源'
        }}
      </div>
      <section
        v-for="group in groupedList"
        :key="group.key"
        class="register-daily-slot-panel__group"
      >
        <header v-if="showGroupHeaders" class="register-daily-slot-panel__group-header">
          <span class="register-daily-slot-panel__group-title">{{ group.practitionerName }}</span>
          <span class="register-daily-slot-panel__group-meta">{{ group.slots.length }} 个时段</span>
        </header>
        <div class="register-daily-slot-panel__grid">
          <button
            v-for="row in group.slots"
            :key="row.slotId"
            type="button"
            class="register-daily-slot-panel__card"
            :class="cardClassName(row)"
            @click="handleCardClick(row)"
          >
            <div class="register-daily-slot-panel__card-time">
              {{ formatTime(row.startTime) }} - {{ formatTime(row.endTime) }}
            </div>
            <ElTag size="small" :type="cardStatusType(row)" effect="plain">
              {{ row.displayStatus || '可约' }}
            </ElTag>
            <div class="register-daily-slot-panel__card-meta">{{ serviceTypeLabel(row) }}</div>
            <div class="register-daily-slot-panel__card-count">
              剩余 {{ row.remainingCount ?? 0 }} / {{ row.capacity ?? 0 }}
            </div>
          </button>
        </div>
      </section>
    </div>

    <div
      v-if="total > query.pageSize"
      class="mt-2 flex items-center justify-end gap-2 text-sm text-[var(--el-text-color-secondary)]"
    >
      <span>共 {{ total }} 条</span>
      <ElButton
        link
        type="primary"
        :disabled="query.pageNo <= 1"
        @click="handlePageChange(query.pageNo - 1)"
      >
        上一页
      </ElButton>
      <span>{{ query.pageNo }}</span>
      <ElButton
        link
        type="primary"
        :disabled="query.pageNo * query.pageSize >= total"
        @click="handlePageChange(query.pageNo + 1)"
      >
        下一页
      </ElButton>
    </div>
  </div>
</template>

<style scoped>
.register-daily-slot-panel__body {
  padding: 2px;
}

.register-daily-slot-panel__today {
  flex-shrink: 0;
}

.register-daily-slot-panel__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 120px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.register-daily-slot-panel__group + .register-daily-slot-panel__group {
  margin-top: 14px;
}

.register-daily-slot-panel__group-header {
  display: flex;
  gap: 8px;
  align-items: baseline;
  margin-bottom: 8px;
}

.register-daily-slot-panel__group-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.register-daily-slot-panel__group-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.register-daily-slot-panel__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(148px, 1fr));
  gap: 10px;
}

.register-daily-slot-panel__card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    background-color 0.15s ease;
}

.register-daily-slot-panel__card:hover:not(.register-daily-slot-panel__card--disabled) {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 2px 8px rgb(0 0 0 / 6%);
}

.register-daily-slot-panel__card--selected {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.register-daily-slot-panel__card--disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.register-daily-slot-panel__card-time {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--el-text-color-primary);
}

.register-daily-slot-panel__card-meta {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  line-height: 1.3;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.register-daily-slot-panel__card-count {
  margin-top: auto;
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}
</style>
