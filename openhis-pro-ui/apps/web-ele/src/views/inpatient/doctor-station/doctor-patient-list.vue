<script setup lang="ts">
import type { InpatientDoctorStationPatient } from './doctorPatientStore';

import { ref } from 'vue';

import { RotateCw, Search } from '@vben/icons';

import { ElButton, ElInput, ElPopover } from 'element-plus';

import SidePaneCollapseHandle from '#/components/layouts/side-pane-collapse-handle.vue';
import { DOCTOR_STATION_SIDEBAR_WIDTH_PX } from '#/constants/layout/viewport-breakpoints';

import InpatientDoctorPatientCard from './components/inpatient-doctor-patient-card.vue';

defineOptions({
  name: 'InpatientDoctorPatientList',
});

interface Props {
  patients?: InpatientDoctorStationPatient[];
  selectedPatientId?: string;
  loading?: boolean;
}

withDefaults(defineProps<Props>(), {
  patients: () => [],
  selectedPatientId: '',
  loading: false,
});

const emit = defineEmits<{
  refresh: [];
  search: [keyword: string];
  select: [patient: InpatientDoctorStationPatient];
}>();

/** 展开态宽度（与医生站侧栏常量一致） */
const EXPANDED_WIDTH = DOCTOR_STATION_SIDEBAR_WIDTH_PX.patientQueue.default;
/** 收起态宽度：仅留姓名 + 床号/住院号 */
const COLLAPSED_WIDTH = 120;

const searchKeyword = ref('');
const isRefreshing = ref(false);
const isCollapsed = ref(false);

/** 回车/清空后按关键字筛选在院患者 */
function handleSearch() {
  emit('search', searchKeyword.value);
}

/** 点选队列中的患者 */
function handleSelect(patient: InpatientDoctorStationPatient) {
  emit('select', patient);
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

/** 收起态副标题：床号优先，否则住院号 */
function compactSubText(patient: InpatientDoctorStationPatient) {
  const bed = (patient.bedName ?? '').trim();
  if (bed) return bed;
  return (patient.encounterBusNo ?? patient.encounterId ?? '').trim() || '-';
}
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
        <div class="text-text-main text-base font-semibold">在院患者</div>
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
      </div>

      <div class="flex-1 overflow-y-auto pb-3" :class="isCollapsed ? 'px-0 pt-2' : 'px-3'">
        <div v-if="loading" class="text-text-sub py-8 text-center text-sm">加载中...</div>
        <div v-else-if="patients.length === 0" class="text-text-sub py-8 text-center text-sm">
          暂无在院患者
        </div>
        <div v-else class="space-y-2">
          <template v-if="!isCollapsed">
            <div
              v-for="patient in patients"
              :key="patient.encounterId"
              class="patient-card-dblclick-wrap"
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
              v-for="patient in patients"
              :key="patient.encounterId"
              placement="right"
              trigger="hover"
              width="300"
              :popper-options="{ strategy: 'fixed' }"
            >
              <template #reference>
                <div
                  class="cursor-pointer border-l-2 px-2 py-2 transition-colors"
                  :class="{
                    'border-primary bg-primary/5': patient.encounterId === selectedPatientId,
                    'hover:border-primary/30 border-transparent hover:bg-[var(--el-fill-color-light)]':
                      patient.encounterId !== selectedPatientId,
                  }"
                  @click="handleSelect(patient)"
                >
                  <div class="truncate text-sm font-medium text-[var(--el-text-color-primary)]">
                    {{ patient.patientName || '-' }}
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
