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
import {
  isSlotAvailable,
  isSlotBlocked,
  isSlotFull,
  resolveSlotStatusDisplayLabel,
  resolveSlotStatusTagType,
} from '#/api/booking/slot-status';
import { loadSlotDetailApi, pageSlotsByDateApi } from '#/api/booking/slot-manage';
import { useRequest } from '#/hooks/use-request';

import { loadRegisterFormInit } from '../services/register.service';

defineOptions({
  name: 'RegisterDailySlotPanel',
});

const props = withDefaults(
  defineProps<{
    healthcareServiceId?: BookingEntityId;
    /** 挂号表单所选门诊科室（adm_organization.id）；未选时不查号源 */
    organizationId?: BookingEntityId;
    practitionerId?: BookingEntityId;
    /** horizontal：横向滚动卡片（新挂号页布局） */
    variant?: 'default' | 'horizontal';
    /** 是否展示号源面板顶部筛选条 */
    showFilters?: boolean;
    /** 外部承载标题栏时隐藏内置顶栏 */
    hideToolbar?: boolean;
  }>(),
  {
    organizationId: undefined,
    practitionerId: undefined,
    healthcareServiceId: undefined,
    variant: 'default',
    showFilters: true,
    hideToolbar: false,
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

interface OnDutyPractitionerItem {
  practitionerId: string;
  practitionerName: string;
  slotCount: number;
  remainingCount: number;
}

/** 号源/排班按门诊科室主键查询，禁止回退登录 OU（与 adm_organization 非同一 ID 空间） */
const effectiveOrganizationId = computed(() => toBookingId(props.organizationId));
const list = ref<SlotListItemDto[]>([]);
const total = ref(0);
const practitioners = ref<OptionDto[]>([]);
const healthcareServices = ref<OptionDto[]>([]);
const selectedSlotId = ref<string>();
const onDutyPractitioners = ref<OnDutyPractitionerItem[]>([]);
const selectedPanelPractitionerId = ref<BookingEntityId | undefined>();
const practitionersLoading = ref(false);

const query = reactive({
  pageNo: 1,
  pageSize: 200,
});

const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

/** 现场挂号仅查今日号源；跨日排班请在门诊排班维护 */
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

/** 仅多医师且未指定医师筛选时展示分组标题（default 布局） */
const showGroupHeaders = computed(
  () =>
    props.variant !== 'horizontal' &&
    groupedList.value.length > 1 &&
    !toBookingId(filters.practitionerId),
);

const horizontalPanelLoading = computed(
  () => props.variant === 'horizontal' && (practitionersLoading.value || loading.value),
);

const horizontalEmptyMessage = computed(() => {
  if (props.variant !== 'horizontal') {
    return '';
  }
  if (!effectiveOrganizationId.value) {
    return '请先在挂号信息中选择科室';
  }
  if (onDutyPractitioners.value.length === 0) {
    return '当日暂无出诊医师';
  }
  if (!toBookingId(selectedPanelPractitionerId.value)) {
    return '请选择医生查看号源';
  }
  if (displayedList.value.length === 0) {
    return filters.healthcareServiceId ? '当前号源类型下暂无号源' : '该医生暂无号源';
  }
  return '';
});

const showHorizontalPlaceholder = computed(
  () =>
    props.variant === 'horizontal' &&
    !horizontalPanelLoading.value &&
    Boolean(horizontalEmptyMessage.value),
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

async function loadOnDutyPractitioners() {
  const orgId = effectiveOrganizationId.value;
  if (!orgId) {
    onDutyPractitioners.value = [];
    return;
  }
  practitionersLoading.value = true;
  try {
    const date = todayLocalDateIso();
    const res = await pageSlotsByDateApi({
      organizationId: orgId,
      date,
      pageNo: 1,
      pageSize: 200,
    });
    const groupMap = new Map<string, OnDutyPractitionerItem>();
    for (const row of res.content ?? []) {
      const practitionerId = toBookingId(row.practitionerId);
      if (!practitionerId) {
        continue;
      }
      const existing = groupMap.get(practitionerId);
      if (existing) {
        existing.slotCount += 1;
        const remaining = Number(row.remainingCount ?? 0);
        existing.remainingCount += Number.isFinite(remaining) ? remaining : 0;
        continue;
      }
      const remaining = Number(row.remainingCount ?? 0);
      groupMap.set(practitionerId, {
        practitionerId,
        practitionerName: practitionerLabel(row),
        slotCount: 1,
        remainingCount: Number.isFinite(remaining) ? remaining : 0,
      });
    }
    onDutyPractitioners.value = [...groupMap.values()].sort((left, right) =>
      left.practitionerName.localeCompare(right.practitionerName, 'zh-CN'),
    );
    await ensureDefaultDoctorSelected();
  } finally {
    practitionersLoading.value = false;
  }
}

async function loadSlotsForSelectedPractitioner() {
  const orgId = effectiveOrganizationId.value;
  const practitionerId = toBookingId(selectedPanelPractitionerId.value);
  if (!orgId || !practitionerId) {
    list.value = [];
    total.value = 0;
    return;
  }
  const date = todayLocalDateIso();
  await runPage({
    organizationId: orgId,
    date,
    practitionerId,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  });
}

async function loadData() {
  if (props.variant === 'horizontal') {
    await loadSlotsForSelectedPractitioner();
    return;
  }
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

async function handlePanelPractitionerSelect(item: OnDutyPractitionerItem) {
  if (isSameBookingId(selectedPanelPractitionerId.value, item.practitionerId)) {
    return;
  }
  selectedPanelPractitionerId.value = item.practitionerId;
  selectedSlotId.value = undefined;
  query.pageNo = 1;
  await loadSlotsForSelectedPractitioner();
}

async function ensureDefaultDoctorSelected() {
  if (props.variant !== 'horizontal') {
    return;
  }
  if (selectedPanelPractitionerId.value || toBookingId(props.practitionerId)) {
    return;
  }
  const first = onDutyPractitioners.value[0];
  if (!first) {
    return;
  }
  await handlePanelPractitionerSelect(first);
}

async function refreshPanel() {
  if (props.variant === 'horizontal') {
    await loadOnDutyPractitioners();
    if (selectedPanelPractitionerId.value) {
      await loadSlotsForSelectedPractitioner();
    }
    return;
  }
  await loadData();
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
  if (props.variant === 'horizontal') {
    selectedPanelPractitionerId.value = undefined;
    list.value = [];
    total.value = 0;
  }
}

/** 将左侧挂号表单医师/号源类型同步到筛选条 */
function syncFiltersFromForm(
  practitionerId?: BookingEntityId,
  healthcareServiceId?: BookingEntityId,
) {
  const nextPractitioner = toBookingId(practitionerId);
  const nextService = toBookingId(healthcareServiceId);
  filters.healthcareServiceId = nextService;
  selectedSlotId.value = undefined;
  if (props.variant === 'horizontal') {
    if (nextPractitioner && !isSameBookingId(selectedPanelPractitionerId.value, nextPractitioner)) {
      selectedPanelPractitionerId.value = nextPractitioner;
      query.pageNo = 1;
      void loadSlotsForSelectedPractitioner();
      return;
    }
    if (!nextPractitioner && selectedPanelPractitionerId.value) {
      selectedPanelPractitionerId.value = undefined;
      list.value = [];
      total.value = 0;
    }
    return;
  }
  const practitionerChanged = !isSameBookingId(filters.practitionerId, nextPractitioner);
  filters.practitionerId = nextPractitioner;
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
    healthcareServiceName: row.healthcareServiceName || detail?.healthcareServiceName || undefined,
    practitionerId: row.practitionerId ?? detail?.practitionerId,
    slotStart: row.startTime ?? detail?.startTime,
    slotEnd: row.endTime ?? detail?.endTime,
  };
}

function isSelectable(row: SlotListItemDto): boolean {
  if (isSlotBlocked(row)) {
    return false;
  }
  return isSlotAvailable(row) || Boolean(row.overbookedFlag);
}

function cardStatusType(row: SlotListItemDto): 'danger' | 'info' | 'success' | 'warning' {
  return resolveSlotStatusTagType(row);
}

function cardStatusTone(row: SlotListItemDto): string {
  if (isSlotBlocked(row)) {
    return 'register-daily-slot-panel__card-status--blocked';
  }
  if (isSlotFull(row) && !row.overbookedFlag) {
    return 'register-daily-slot-panel__card-status--full';
  }
  return 'register-daily-slot-panel__card-status--available';
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
  if (isSlotBlocked(row)) {
    ElMessage.warning('该号源已停诊');
    return;
  }
  if (isSlotFull(row) && !row.overbookedFlag) {
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
      onDutyPractitioners.value = [];
      resetSlotFilters();
      return;
    }
    resetSlotFilters();
    await initFilters();
    if (props.variant === 'horizontal') {
      await loadOnDutyPractitioners();
      return;
    }
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
  refresh: refreshPanel,
});
</script>

<template>
  <div
    class="register-daily-slot-panel flex min-h-0 flex-col"
    :class="[
      variant === 'horizontal'
        ? 'register-daily-slot-panel--horizontal overflow-visible'
        : 'h-full min-h-0 flex-1 overflow-hidden',
    ]"
  >
    <div
      v-if="!hideToolbar && (showFilters || variant === 'horizontal')"
      class="register-daily-slot-panel__header"
      :class="{ 'register-daily-slot-panel__header--horizontal': variant === 'horizontal' }"
    >
      <template v-if="variant === 'horizontal'">
        <span class="register-daily-slot-panel__header-title">今日出诊医生</span>
        <span
          class="register-daily-slot-panel__today text-sm font-medium text-[var(--el-text-color-primary)]"
        >
          今日 {{ todayDateLabel }}
        </span>
      </template>
      <template v-if="showFilters">
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
        <ElButton
          :icon="Refresh"
          circle
          :loading="loading"
          title="刷新今日号源"
          @click="loadData"
        />
        <span class="ml-auto text-sm text-[var(--el-text-color-secondary)]">
          汇总：容量 {{ summary.capacity }} · 已约 {{ summary.booked }} · 剩余
          {{ summary.remaining }}
        </span>
      </template>
      <ElButton
        v-else-if="variant === 'horizontal'"
        class="ml-auto"
        :icon="Refresh"
        circle
        :loading="practitionersLoading || loading"
        title="刷新今日号源"
        @click="refreshPanel"
      />
    </div>

    <div
      v-loading="variant === 'horizontal' ? practitionersLoading || loading : loading"
      class="register-daily-slot-panel__body min-h-0"
      :class="
        variant === 'horizontal'
          ? 'register-daily-slot-panel__body--horizontal'
          : 'min-h-0 flex-1 overflow-y-auto'
      "
    >
      <div
        v-if="
          variant !== 'horizontal' && !effectiveOrganizationId && !practitionersLoading && !loading
        "
        class="register-daily-slot-panel__empty"
      >
        请先在挂号信息中选择科室
      </div>

      <div v-else-if="variant === 'horizontal'" class="register-daily-slot-panel__pick-layout">
        <div v-if="showHorizontalPlaceholder" class="register-daily-slot-panel__placeholder">
          <span class="register-daily-slot-panel__placeholder-text">
            {{ horizontalEmptyMessage }}
          </span>
        </div>
        <template v-else>
          <div class="register-daily-slot-panel__doctor-strip">
            <button
              v-for="item in onDutyPractitioners"
              :key="item.practitionerId"
              type="button"
              class="register-daily-slot-panel__doctor-item"
              :class="{
                'is-active': isSameBookingId(selectedPanelPractitionerId, item.practitionerId),
              }"
              @click="handlePanelPractitionerSelect(item)"
            >
              <span class="register-daily-slot-panel__doctor-name">{{
                item.practitionerName
              }}</span>
              <span class="register-daily-slot-panel__doctor-remaining">
                余{{ item.remainingCount }}
              </span>
            </button>
          </div>

          <div class="register-daily-slot-panel__slot-area">
            <div
              v-if="displayedList.length === 0 && !loading"
              class="register-daily-slot-panel__placeholder register-daily-slot-panel__placeholder--inline"
            >
              <span class="register-daily-slot-panel__placeholder-text">
                {{ horizontalEmptyMessage || '该医生暂无号源' }}
              </span>
            </div>
            <div
              v-else
              class="register-daily-slot-panel__grid register-daily-slot-panel__grid--horizontal"
            >
              <button
                v-for="row in displayedList"
                :key="row.slotId"
                type="button"
                class="register-daily-slot-panel__card register-daily-slot-panel__card--slot"
                :class="cardClassName(row)"
                @click="handleCardClick(row)"
              >
                <div class="register-daily-slot-panel__card-head">
                  <span class="register-daily-slot-panel__card-time">
                    {{ formatTime(row.startTime) }}-{{ formatTime(row.endTime) }}
                  </span>
                  <span class="register-daily-slot-panel__card-status" :class="cardStatusTone(row)">
                    {{ resolveSlotStatusDisplayLabel(row) }}
                  </span>
                </div>
                <div class="register-daily-slot-panel__card-foot">
                  <span class="register-daily-slot-panel__card-meta">{{
                    serviceTypeLabel(row)
                  }}</span>
                  <span class="register-daily-slot-panel__card-count">
                    余 {{ row.remainingCount ?? 0 }}/{{ row.capacity ?? 0 }}
                  </span>
                </div>
              </button>
            </div>
          </div>
        </template>
      </div>

      <template v-else>
        <div v-if="groupedList.length === 0 && !loading" class="register-daily-slot-panel__empty">
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
            <span class="register-daily-slot-panel__group-meta"
              >{{ group.slots.length }} 个时段</span
            >
          </header>
          <div class="register-daily-slot-panel__grid">
            <div
              v-for="row in group.slots"
              :key="row.slotId"
              class="register-daily-slot-panel__card-wrap"
              :class="cardClassName(row)"
            >
              <button
                type="button"
                class="register-daily-slot-panel__card"
                :class="cardClassName(row)"
                @click="handleCardClick(row)"
              >
                <div class="register-daily-slot-panel__card-time">
                  {{ formatTime(row.startTime) }} - {{ formatTime(row.endTime) }}
                </div>
                <div class="register-daily-slot-panel__card-doctor">
                  {{ practitionerLabel(row) }}
                </div>
                <ElTag size="small" :type="cardStatusType(row)" effect="plain">
                  {{ resolveSlotStatusDisplayLabel(row) }}
                </ElTag>
                <div class="register-daily-slot-panel__card-meta">{{ serviceTypeLabel(row) }}</div>
                <div class="register-daily-slot-panel__card-count">
                  剩余 {{ row.remainingCount ?? 0 }} / {{ row.capacity ?? 0 }}
                </div>
              </button>
            </div>
          </div>
        </section>
      </template>
    </div>

    <div
      v-if="
        variant === 'horizontal'
          ? selectedPanelPractitionerId && total > query.pageSize
          : total > query.pageSize
      "
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
.register-daily-slot-panel--horizontal {
  flex: 0 0 auto;
  height: auto;
  min-height: 0;
}

.register-daily-slot-panel__header {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.register-daily-slot-panel__header--horizontal {
  flex-wrap: nowrap;
  flex-shrink: 0;
  min-height: 30px;
  padding-bottom: 8px;
  margin-bottom: 0;
}

.register-daily-slot-panel__header-title {
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 30px;
  color: var(--el-text-color-primary);
}

.register-daily-slot-panel--horizontal .register-daily-slot-panel__header .el-button.is-circle {
  width: 30px;
  height: 30px;
  padding: 0;
}

.register-daily-slot-panel__body {
  padding: 0;
}

.register-daily-slot-panel--horizontal .register-daily-slot-panel__body {
  flex: 0 0 auto;
  min-height: 0;
  overflow: visible;
}

.register-daily-slot-panel__placeholder {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  padding: 24px 16px;
  background: #fff;
  border: 1px dashed #dce3eb;
  border-radius: 8px;
}

.register-daily-slot-panel__placeholder--inline {
  min-height: 56px;
}

.register-daily-slot-panel__placeholder-text {
  max-width: 260px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.register-daily-slot-panel__today {
  flex-shrink: 0;
  line-height: 30px;
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

.register-daily-slot-panel__grid--horizontal {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-content: flex-start;
  width: 100%;
}

.register-daily-slot-panel__body--horizontal {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.register-daily-slot-panel__pick-layout {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
}

.register-daily-slot-panel__doctor-strip {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  padding: 2px;
  margin: 0;
  overflow: visible;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

.register-daily-slot-panel__doctor-empty {
  width: 100%;
  padding: 8px 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.register-daily-slot-panel__doctor-item {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 4px;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 4px;
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}

.register-daily-slot-panel__doctor-item:hover:not(.is-active) {
  background: rgb(255 255 255 / 70%);
}

.register-daily-slot-panel__doctor-item.is-active {
  background: #fff;
  box-shadow: 0 1px 2px rgb(15 23 42 / 8%);
}

.register-daily-slot-panel__doctor-name {
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  color: var(--el-text-color-regular);
}

.register-daily-slot-panel__doctor-item.is-active .register-daily-slot-panel__doctor-name {
  font-weight: 600;
  color: var(--el-color-primary);
}

.register-daily-slot-panel__doctor-remaining {
  font-size: 11px;
  line-height: 1;
  color: var(--el-text-color-secondary);
}

.register-daily-slot-panel__doctor-item.is-active .register-daily-slot-panel__doctor-remaining {
  color: var(--el-color-primary-light-3);
}

.register-daily-slot-panel__slot-area {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  min-height: 0;
  overflow: visible;
}

.register-daily-slot-panel__slot-area .register-daily-slot-panel__grid--horizontal {
  flex: 0 0 auto;
  min-height: 0;
  overflow-y: visible;
}

.register-daily-slot-panel__body--horizontal .register-daily-slot-panel__group {
  min-width: 0;
}

.register-daily-slot-panel__card-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: stretch;
  min-width: 0;
}

.register-daily-slot-panel__card-doctor {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.3;
  color: var(--el-text-color-primary);
  white-space: nowrap;
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
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    background-color 0.15s ease;
}

.register-daily-slot-panel--horizontal .register-daily-slot-panel__card--slot {
  flex: 0 0 auto;
  gap: 0;
  width: 148px;
  padding: 0;
  overflow: hidden;
  text-align: left;
  border-radius: 6px;
}

.register-daily-slot-panel__card-head,
.register-daily-slot-panel__card-foot {
  display: flex;
  gap: 4px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.register-daily-slot-panel--horizontal
  .register-daily-slot-panel__card--slot
  .register-daily-slot-panel__card-head {
  padding: 6px 8px 4px;
}

.register-daily-slot-panel__card-foot {
  margin-top: 0;
  padding: 4px 8px 6px;
  border-top: 1px solid var(--el-border-color-extra-light);
}

.register-daily-slot-panel__card-status {
  flex-shrink: 0;
  padding: 0 4px;
  font-size: 10px;
  font-weight: 500;
  line-height: 16px;
  border-radius: 3px;
}

.register-daily-slot-panel__card-status--available {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.register-daily-slot-panel__card-status--full {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
}

.register-daily-slot-panel__card-status--blocked {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.register-daily-slot-panel__card:hover:not(.register-daily-slot-panel__card--disabled) {
  border-color: var(--el-color-primary-light-5);
}

.register-daily-slot-panel--horizontal
  .register-daily-slot-panel__card--slot:hover:not(.register-daily-slot-panel__card--disabled) {
  box-shadow: 0 2px 6px rgb(15 23 42 / 6%);
}

.register-daily-slot-panel__card--selected {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.register-daily-slot-panel--horizontal
  .register-daily-slot-panel__card--slot.register-daily-slot-panel__card--selected {
  background: var(--el-color-primary-light-9);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
}

.register-daily-slot-panel__card--disabled {
  cursor: not-allowed;
  background: var(--el-fill-color-lighter);
  opacity: 0.72;
}

.register-daily-slot-panel__card-time {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.register-daily-slot-panel__card-meta {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  line-height: 1.2;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.register-daily-slot-panel__card-count {
  flex-shrink: 0;
  font-size: 11px;
  line-height: 1.2;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.register-daily-slot-panel--horizontal
  .register-daily-slot-panel__card--slot
  .register-daily-slot-panel__card-count {
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.register-daily-slot-panel--horizontal
  .register-daily-slot-panel__card--slot.register-daily-slot-panel__card--disabled
  .register-daily-slot-panel__card-count {
  font-weight: 400;
  color: var(--el-text-color-placeholder);
}
</style>
