<script setup lang="ts">
import type { InpatientDoctorStationPatient } from './doctorPatientStore';

import { computed, nextTick, ref } from 'vue';

import { Fullscreen, RotateCw, Search } from '@zxplat/icons';

import { ElButton, ElInput, ElPopover } from 'element-plus';

import SidePaneCollapseHandle from '#/components/layouts/side-pane-collapse-handle.vue';
import { DOCTOR_STATION_SIDEBAR_WIDTH_PX } from '#/constants/layout/viewport-breakpoints';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { isPatientAdmittedToday } from '#/utils/inpatient/is-patient-admitted-today';

import InpatientDoctorPatientCard from './components/inpatient-doctor-patient-card.vue';

defineOptions({
  name: 'InpatientDoctorPatientList',
});

interface Props {
  patients?: InpatientDoctorStationPatient[];
  selectedPatientId?: string;
  loading?: boolean;
  /** 病历工作台不需要大屏入口 */
  showLargeScreen?: boolean;
  /** 进入时是否收起为床号+姓名 */
  defaultCollapsed?: boolean;
}

const emit = defineEmits<{
  refresh: [];
  search: [keyword: string];
  select: [patient: InpatientDoctorStationPatient];
  openLargeScreen: [];
}>();

/** 展开态宽度（与医生站侧栏常量一致） */
const EXPANDED_WIDTH = DOCTOR_STATION_SIDEBAR_WIDTH_PX.patientQueue.default;
/** 收起态宽度：仅留姓名 + 床号/住院号 */
const COLLAPSED_WIDTH = 120;

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
  selectedPatientId: '',
  loading: false,
  showLargeScreen: true,
  defaultCollapsed: false,
});

const searchKeyword = ref('');
const isRefreshing = ref(false);
const isCollapsed = ref(props.defaultCollapsed);
const listBodyRef = ref<HTMLElement | null>(null);
/** 队列入院日筛选：全部 / 仅今日入院（本地过滤，不重拉） */
const admissionFilter = ref<'all' | 'today'>('all');

const displayedPatients = computed(() => {
  if (admissionFilter.value !== 'today') {
    return props.patients;
  }
  return props.patients.filter((patient) => isPatientAdmittedToday(patient));
});

const emptyQueueText = computed(() => {
  if (props.patients.length === 0) {
    return '暂无在院患者';
  }
  if (admissionFilter.value === 'today') {
    return '暂无今日入院患者';
  }
  return '暂无在院患者';
});

/** 回车/清空后按关键字筛选在院患者 */
function handleSearch() {
  emit('search', searchKeyword.value);
}

/** 点选队列中的患者 */
function handleSelect(patient: InpatientDoctorStationPatient) {
  emit('select', patient);
}

/** 切换全部 / 今日入院（本地过滤） */
function changeAdmissionFilter(value: 'all' | 'today') {
  admissionFilter.value = value;
}

/** 刷新在院患者队列 */
function handleRefresh() {
  isRefreshing.value = true;
  emit('refresh');
  setTimeout(() => {
    isRefreshing.value = false;
  }, 600);
}

/** 切换患者队列收起/展开 */
function toggleCollapsed() {
  isCollapsed.value = !isCollapsed.value;
}

/** 展开患者一览大屏 */
function handleOpenLargeScreen() {
  emit('openLargeScreen');
}

/** 收起态副标题：床号优先，否则住院号 */
function compactSubText(patient: InpatientDoctorStationPatient) {
  const bed = (patient.bedName ?? '').trim();
  if (bed) return bed;
  return (patient.encounterBusNo ?? patient.encounterId ?? '').trim() || '-';
}

/** 收起态左边条：今日入院用翡翠绿，与展开卡片同口径 */
function isTodayAdmission(patient: InpatientDoctorStationPatient) {
  return isPatientAdmittedToday(patient);
}

function resolveQueueEncounterId(encounterId: string | undefined): string {
  return preserveApiIdForRequest(encounterId) ?? '';
}

/** 页签切人后把队列滚到对应卡片；今日入院筛掉时先切回全部 */
async function locatePatient(encounterId: string) {
  const key = resolveQueueEncounterId(encounterId);
  if (!key) return;
  const visible = displayedPatients.value.some(
    (row) => resolveQueueEncounterId(row.encounterId) === key,
  );
  if (!visible && admissionFilter.value === 'today') {
    admissionFilter.value = 'all';
    await nextTick();
  }
  await nextTick();
  const root = listBodyRef.value;
  if (!root) return;
  const target = root.querySelector(`[data-encounter-id="${CSS.escape(key)}"]`);
  if (target instanceof HTMLElement) {
    target.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }
}

defineExpose({
  locatePatient,
});
</script>

<template>
  <div
    class="ip-patient-queue relative flex h-full flex-shrink-0"
    :class="{ 'is-collapsed': isCollapsed }"
  >
    <div
      class="bg-background-card flex h-full flex-col rounded-none border-r border-[var(--el-border-color-lighter)] transition-all duration-300"
      :style="{ width: `${isCollapsed ? COLLAPSED_WIDTH : EXPANDED_WIDTH}px` }"
    >
      <div v-if="!isCollapsed" class="mb-2 flex flex-col gap-2 px-3 pt-2">
        <div class="flex items-center justify-between gap-2">
          <div class="text-text-main text-base font-semibold">在院患者</div>
          <ElButton
            v-if="showLargeScreen"
            type="primary"
            link
            class="!shrink-0"
            title="展开患者一览大屏"
            @click="handleOpenLargeScreen"
          >
            <Fullscreen class="mr-1 h-4 w-4" />
            患者一览
          </ElButton>
        </div>
        <div class="flex items-center gap-2">
          <ElInput
            v-model="searchKeyword"
            placeholder="住院号 / 姓名 / 床号"
            clearable
            class="min-w-0 flex-1"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #prefix>
              <Search class="h-4 w-4" />
            </template>
          </ElInput>
          <ElButton
            type="default"
            class="!shrink-0 !px-2"
            title="刷新在院患者"
            @click="handleRefresh"
          >
            <RotateCw class="h-4 w-4" :class="{ 'animate-spin': isRefreshing }" />
          </ElButton>
        </div>
        <div class="ip-admission-seg" role="group" aria-label="入院日筛选">
          <button
            type="button"
            class="ip-admission-seg__item"
            :class="{ 'is-active': admissionFilter === 'all' }"
            @click="changeAdmissionFilter('all')"
          >
            全部
          </button>
          <button
            type="button"
            class="ip-admission-seg__item is-today"
            :class="{ 'is-active': admissionFilter === 'today' }"
            title="仅看今日入院患者"
            @click="changeAdmissionFilter('today')"
          >
            今日入院
          </button>
        </div>
      </div>

      <div
        v-if="isCollapsed && showLargeScreen"
        class="flex items-center justify-center px-1 pt-2"
      >
        <button
          type="button"
          class="ip-patient-queue__board-btn"
          title="展开患者一览大屏"
          @click="handleOpenLargeScreen"
        >
          <Fullscreen class="h-4 w-4" />
        </button>
      </div>

      <div
        ref="listBodyRef"
        class="flex-1 overflow-y-auto pb-3"
        :class="isCollapsed ? 'px-0 pt-2' : 'px-3'"
      >
        <div v-if="loading" class="text-text-sub py-8 text-center text-sm">加载中...</div>
        <div
          v-else-if="displayedPatients.length === 0"
          class="text-text-sub py-8 text-center text-sm"
        >
          {{ emptyQueueText }}
        </div>
        <div v-else class="space-y-2">
          <template v-if="!isCollapsed">
            <div
              v-for="patient in displayedPatients"
              :key="patient.encounterId"
              class="patient-card-dblclick-wrap"
              :data-encounter-id="patient.encounterId"
            >
              <InpatientDoctorPatientCard
                :patient="patient"
                :selected="patient.encounterId === selectedPatientId"
                @select="handleSelect"
              />
            </div>
          </template>
          <template v-else>
            <ElPopover
              v-for="patient in displayedPatients"
              :key="patient.encounterId"
              placement="right"
              trigger="hover"
              width="300"
              :popper-options="{ strategy: 'fixed' }"
            >
              <template #reference>
                <div
                  class="cursor-pointer border-l-2 px-2 py-2 transition-colors"
                  :data-encounter-id="patient.encounterId"
                  :class="{
                    'border-primary bg-primary/5': patient.encounterId === selectedPatientId,
                    'is-today-admission':
                      isTodayAdmission(patient) && patient.encounterId !== selectedPatientId,
                    'hover:border-primary/30 border-transparent hover:bg-[var(--el-fill-color-light)]':
                      patient.encounterId !== selectedPatientId && !isTodayAdmission(patient),
                  }"
                  @click="handleSelect(patient)"
                >
                  <div
                    class="flex min-w-0 items-center gap-1 text-sm font-medium text-[var(--el-text-color-primary)]"
                  >
                    <span class="truncate">{{ patient.patientName || '-' }}</span>
                    <span
                      v-if="isTodayAdmission(patient)"
                      class="ip-patient-queue__today-badge"
                      title="今日入院"
                    >
                      今日
                    </span>
                  </div>
                  <div class="mt-0.5 truncate text-xs text-[var(--el-text-color-secondary)]">
                    {{ compactSubText(patient) }}
                  </div>
                </div>
              </template>
              <div class="p-0">
                <InpatientDoctorPatientCard
                  :patient="patient"
                  :selected="patient.encounterId === selectedPatientId"
                  @select="handleSelect"
                />
              </div>
            </ElPopover>
          </template>
        </div>
      </div>
    </div>

    <!-- 右缘热区：hover 显示灰色透明梯形把手，垂直居中 -->
    <div class="ip-patient-queue__edge">
      <SidePaneCollapseHandle :collapsed="isCollapsed" @toggle="toggleCollapsed" />
    </div>
  </div>
</template>

<style scoped>
.patient-card-dblclick-wrap {
  display: block;
}

.ip-admission-seg {
  display: flex;
  flex-shrink: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}

.ip-admission-seg__item {
  flex: 1;
  height: 28px;
  margin: 0;
  padding: 0 8px;
  font-size: 12px;
  line-height: 1;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: #fff;
  border: 0;
  border-right: 1px solid var(--el-border-color);
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}

.ip-admission-seg__item:last-child {
  border-right: 0;
}

.ip-admission-seg__item:hover:not(.is-active) {
  color: var(--el-color-primary);
  background: #f0f2f5;
}

.ip-admission-seg__item.is-active {
  font-weight: 600;
  color: #fff;
  background: var(--el-color-primary);
}

.ip-admission-seg__item.is-today:hover:not(.is-active) {
  color: #047857;
  background: #ecfdf5;
}

.ip-admission-seg__item.is-today.is-active {
  color: #fff;
  background: #059669;
}

.is-today-admission {
  background: #ecfdf5;
  border-left-color: #10b981;
}

.ip-patient-queue__board-btn {
  align-items: center;
  background: transparent;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  color: var(--el-color-primary);
  cursor: pointer;
  display: inline-flex;
  height: 28px;
  justify-content: center;
  width: 28px;
}

.ip-patient-queue__board-btn:hover {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.ip-patient-queue__today-badge {
  background: #d1fae5;
  border-radius: 4px;
  color: #047857;
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  padding: 2px 4px;
}

.ip-patient-queue__edge {
  position: absolute;
  top: 0;
  right: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  width: 28px;
  height: 100%;
}

.ip-patient-queue:hover .ip-patient-queue__edge :deep(.side-pane-collapse-handle),
.ip-patient-queue.is-collapsed .ip-patient-queue__edge :deep(.side-pane-collapse-handle),
.ip-patient-queue__edge:hover :deep(.side-pane-collapse-handle),
.ip-patient-queue__edge :deep(.side-pane-collapse-handle:focus-visible) {
  opacity: 1;
  pointer-events: auto;
}
</style>
