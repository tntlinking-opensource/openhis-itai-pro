<script setup lang="ts">
import type { InpatientDispenseQueuePatient } from './types';

import { ref } from 'vue';

import { RotateCw, Search } from '@zxplat/icons';

import {
  ElButton,
  ElDatePicker,
  ElInput,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import type { PatientCardPatient } from '#/components/outpatient/patient-card.types';

import SectionTitle from '#/components/layouts/section-title.vue';
import PatientCard from '#/components/outpatient/patient-card.vue';

import { toInpatientDispensePatientCardModel } from './inpatient-dispense-patient-card-map';

defineOptions({
  name: 'InpatientDispenseQueuePanel',
});

const props = defineProps<Props>();

const emit = defineEmits<{
  queueDateRangeChange: [range: [string, string] | null];
  refresh: [];
  selectPatient: [patient: { id: string }];
}>();

interface Props {
  filteredPatients: InpatientDispenseQueuePatient[];
  loadingQueue?: boolean;
  selectedPatientId: null | string;
  wardOptions: { label: string; value: string }[];
}

const keyword = defineModel<string>('keyword', { required: true });
const queueDateRange = defineModel<[string, string] | null>('queueDateRange', { required: true });
const queueScope = defineModel<'completed' | 'pending'>('queueScope', { required: true });
const selectedWardId = defineModel<string | undefined>('selectedWardId', { required: true });

const isQueueListRefreshing = ref(false);

/** 触发展示旋转动画并通知父级重新拉队列 */
function handleRefresh() {
  isQueueListRefreshing.value = true;
  emit('refresh');
  window.setTimeout(() => {
    isQueueListRefreshing.value = false;
  }, 400);
}

/** 选中住院患者卡片 */
function onPatientCardSelect(card: PatientCardPatient) {
  if ('patientType' in card && card.patientType === 'inpatient') {
    emit('selectPatient', { id: card.id });
    return;
  }
  emit('selectPatient', { id: String((card as { encounterId?: string }).encounterId ?? '') });
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <div class="mb-1.5 flex flex-col gap-1.5 px-2 pt-1.5">
      <SectionTitle title="待发药患者" />
      <ElRadioGroup
        v-model="queueScope"
        class="dispense-queue-scope flex flex-wrap items-center gap-x-4 gap-y-1"
      >
        <ElRadio value="pending">待发药</ElRadio>
        <ElRadio value="completed">已发药</ElRadio>
      </ElRadioGroup>
      <ElSelect v-model="selectedWardId" clearable filterable placeholder="全部病区" class="w-full">
        <ElOption
          v-for="opt in props.wardOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </ElSelect>
      <div class="inpatient-dispense-daterange-row w-full min-w-0">
        <ElDatePicker
          v-model="queueDateRange"
          class="inpatient-dispense-daterange w-full"
          type="daterange"
          unlink-panels
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          @change="emit('queueDateRangeChange', $event as [string, string] | null)"
        />
      </div>
      <div class="flex min-w-0 items-center gap-1.5">
        <ElInput
          v-model="keyword"
          clearable
          placeholder="姓名 / 住院号 / 床号"
          class="min-w-0 flex-1"
        >
          <template #prefix>
            <Search class="h-4 w-4" />
          </template>
        </ElInput>
        <ElButton
          type="default"
          class="shrink-0 !px-2"
          :loading="props.loadingQueue"
          @click="handleRefresh"
        >
          <RotateCw class="h-4 w-4" :class="{ 'animate-spin': isQueueListRefreshing }" />
        </ElButton>
      </div>
    </div>
    <div class="queue-list min-h-0 flex-1 space-y-1.5 overflow-y-auto px-2 pb-1">
      <template v-if="filteredPatients.length === 0">
        <div class="text-text-sub py-8 text-center text-sm">暂无匹配患者</div>
      </template>
      <template v-else>
        <PatientCard
          v-for="p in filteredPatients"
          :key="p.id"
          variant="dispense"
          :patient="toInpatientDispensePatientCardModel(p)"
          :selected="selectedPatientId === p.id"
          @select="onPatientCardSelect"
        />
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.inpatient-dispense-daterange-row :deep(.el-date-editor.el-date-editor--daterange) {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  --el-date-editor-daterange-width: 100%;
}
</style>
