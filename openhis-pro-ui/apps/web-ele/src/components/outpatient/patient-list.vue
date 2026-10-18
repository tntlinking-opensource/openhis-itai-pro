<script setup lang="ts">
import type { OutpatientPatientCardPayload } from './patient-card.types';

import { ref, watch } from 'vue';

import { CalendarSearch, ChevronLeft, ChevronRight, RotateCw, Search } from '@zxplat/icons';

import { ElButton, ElDatePickerPanel, ElInput, ElPopover } from 'element-plus';

import PatientCard from './patient-card.vue';

defineOptions({
  name: 'PatientList',
});

withDefaults(defineProps<Props>(), {
  patients: () => [],
  selectedPatientId: '',
  loading: false,
});

const emit = defineEmits<{
  dateChange: [date: string];
  refresh: [];
  search: [keyword: string];
  select: [patient: Patient];
  view: [patient: Patient];
}>();

/** 与门诊就诊列表行一致（演示/父级可自行补全字段） */
type Patient = OutpatientPatientCardPayload;

interface Props {
  patients?: Patient[];
  selectedPatientId?: string;
  loading?: boolean;
}

const searchKeyword = ref('');
const selectedDate = ref(new Date().toISOString().split('T')[0]);

const handleSearch = () => {
  emit('search', searchKeyword.value);
};

const handleSelect = (patient: Patient) => {
  emit('select', patient);
};

const handleView = (patient: Patient) => {
  emit('view', patient);
};

const handleDateChange = (date: string | undefined) => {
  selectedDate.value = date;
  emit('dateChange', date || '');
};

const isRefreshing = ref(false);

const handleRefresh = () => {
  isRefreshing.value = true;
  emit('refresh');
  setTimeout(() => {
    isRefreshing.value = false;
  }, 1000);
};

const isCollapsed = ref(false);
const datePickerPopoverVisible = ref(false);
const defaultDate = new Date().toISOString().split('T')[0];
const getDateValue = () => selectedDate.value || defaultDate;
const tempSelectedDate = ref<string | undefined>(getDateValue());

watch(datePickerPopoverVisible, (visible) => {
  if (visible) {
    tempSelectedDate.value = getDateValue();
  }
});

const handleDatePickerConfirm = () => {
  handleDateChange(tempSelectedDate.value);
  datePickerPopoverVisible.value = false;
};

const handleDatePickerCancel = () => {
  datePickerPopoverVisible.value = false;
};
</script>

<template>
  <div class="relative flex h-full">
    <div
      class="bg-background-card flex h-full flex-col rounded-lg transition-all duration-300"
      :class="isCollapsed ? 'w-[120px]' : 'w-[260px]'"
    >
      <!-- 搜索区域 -->

      <div v-if="!isCollapsed" class="mb-2 flex items-center px-3 pr-4 pt-2">
        <ElInput
          v-model="searchKeyword"
          placeholder="请输入患者名"
          clearable
          class="flex-1"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <Search class="h-4 w-4" />
          </template>
        </ElInput>

        <ElPopover
          v-model:visible="datePickerPopoverVisible"
          placement="bottom-end"
          trigger="click"
          popper-class="date-picker-popover"
        >
          <template #reference>
            <ElButton circle type="text">
              <CalendarSearch class="h-4 w-4" />
            </ElButton>
          </template>
          <div>
            <ElDatePickerPanel
              v-model="tempSelectedDate"
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              :border="false"
            />
            <div class="flex justify-end gap-2 border-t p-2">
              <ElButton @click="handleDatePickerCancel">取消</ElButton>
              <ElButton type="primary" @click="handleDatePickerConfirm"> 确定 </ElButton>
            </div>
          </div>
        </ElPopover>
        <ElButton type="text" @click="handleRefresh" :bordered="false" class="!ml-0">
          <RotateCw :class="{ 'animate-spin': isRefreshing }" class="h-4 w-4" />
        </ElButton>
      </div>

      <!-- 患者列表 -->
      <div class="flex-1 overflow-y-auto" :class="isCollapsed ? 'px-0' : 'px-3'">
        <div v-if="loading" class="text-text-sub py-8 text-center text-sm">加载中...</div>
        <div v-else-if="patients.length === 0" class="text-text-sub py-8 text-center text-sm">
          暂无患者
        </div>
        <div v-else class="space-y-2">
          <!-- 展开状态：显示完整卡片 -->
          <template v-if="!isCollapsed">
            <PatientCard
              v-for="patient in patients"
              :key="patient.encounterId"
              :patient="patient"
              :selected="patient.encounterId === selectedPatientId"
              @select="handleSelect"
              @view="handleView"
            />
          </template>
          <!-- 收起状态：只显示名字和编号，悬浮显示详情 -->
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
                  class="cursor-pointer px-3 py-2 transition-colors"
                  :class="{
                    'border-primary bg-primary/5': patient.encounterId === selectedPatientId,
                    'hover:border-primary/30 border-gray-200 hover:bg-gray-50':
                      patient.encounterId !== selectedPatientId,
                  }"
                  @click="handleSelect(patient)"
                >
                  <div class="text-sm font-medium text-gray-800">
                    {{ patient.patientName || '-' }}
                  </div>
                  <div class="mt-1 text-xs text-gray-500">
                    {{ patient.encounterBusNo || patient.encounterId || '-' }}
                  </div>
                </div>
              </template>
              <div class="p-0">
                <PatientCard
                  :patient="patient"
                  :selected="patient.encounterId === selectedPatientId"
                  @select="handleSelect"
                  @view="handleView"
                />
              </div>
            </ElPopover>
          </template>
        </div>
      </div>
    </div>

    <!-- 分隔线和收起按钮 -->
    <div class="relative flex w-px flex-shrink-0 items-start justify-center bg-[#ebeef5]">
      <ElButton
        circle
        class="absolute left-1/2 top-[18px] z-10 h-6 w-6 -translate-x-1/2 border border-[#ebeef5] bg-white shadow-[0_2px_8px_rgba(0,0,0,0.12)] transition-colors hover:border-[#409eff] hover:bg-[#409eff] hover:text-white"
        @click="isCollapsed = !isCollapsed"
      >
        <ChevronRight v-if="isCollapsed" class="h-3 w-3" />
        <ChevronLeft v-else class="h-3 w-3" />
      </ElButton>
    </div>
  </div>
</template>

<style>
.date-picker-popover {
  @apply !w-auto !p-0;

  min-width: auto !important;
}

.date-picker-popover .el-popover__inner {
  @apply !p-0;
}
</style>
