<script setup lang="ts">
import type { InpatientDoctorStationPatient } from './doctorPatientStore';

import { ref } from 'vue';

import { RotateCw, Search } from '@vben/icons';

import { ElButton, ElInput } from 'element-plus';

import InpatientDoctorPatientCard from './components/inpatient-doctor-patient-card.vue';

defineOptions({
  name: 'InpatientDoctorPatientList',
});

interface Props {
  patients?: InpatientDoctorStationPatient[];
  selectedPatientId?: string;
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
  selectedPatientId: '',
  loading: false,
});

const emit = defineEmits<{
  refresh: [];
  search: [keyword: string];
  select: [patient: InpatientDoctorStationPatient];
}>();

const searchKeyword = ref('');
const isRefreshing = ref(false);

function handleSearch() {
  emit('search', searchKeyword.value);
}

function handleSelect(patient: InpatientDoctorStationPatient) {
  emit('select', patient);
}

function handleRefresh() {
  isRefreshing.value = true;
  emit('refresh');
  setTimeout(() => {
    isRefreshing.value = false;
  }, 600);
}
</script>

<template>
  <div
    class="bg-background-card flex h-full w-[260px] flex-shrink-0 flex-col rounded-none transition-all duration-300"
  >
    <div class="mb-2 flex flex-col gap-2 px-3 pt-2">
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

    <div class="flex-1 overflow-y-auto px-3 pb-3">
      <div v-if="loading" class="text-text-sub py-8 text-center text-sm">加载中...</div>
      <div v-else-if="patients.length === 0" class="text-text-sub py-8 text-center text-sm">
        暂无在院患者
      </div>
      <div v-else class="space-y-2">
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
      </div>
    </div>
  </div>
</template>

<style scoped>
.patient-card-dblclick-wrap {
  display: block;
}
</style>
